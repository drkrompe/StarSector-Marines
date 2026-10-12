"""Retained Voice Design auditions. Offline validation by default; --generate is paid.

Each role is one design request returning alternative voices. --save-selected
saves approved candidates to the provider account, without runtime registration. An unresolved request
blocks retries; inspect its pending marker and raw response before proceeding.
"""
import argparse
import base64
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import urllib.error
import urllib.request

from generate_wayline_narration import credential
from operation_radio_audition import COMMS, RATE, pcm

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "asset-pipeline/src/tool/resources/audio/operation-radio/personality-auditions"
ENDPOINT = "https://api.elevenlabs.io/v1/text-to-voice/design"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def probe(path):
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=nw=1:nk=1", str(path)], check=True, capture_output=True, text=True)
    return round(float(result.stdout.strip()), 3)


def retained(role, output_format):
    receipt = SOURCE / (role["id"] + ".receipt.json")
    pending = SOURCE / (role["id"] + ".pending.json")
    if pending.exists():
        raise SystemExit("Unresolved request: " + role["id"] + "; inspect before retrying.")
    if not receipt.exists():
        if role.get("status") == "selected":
            raise SystemExit("Missing approved receipt: " + role["id"])
        if list(SOURCE.glob(role["id"] + "-*.mp3")):
            raise SystemExit("Incomplete receipt: " + role["id"])
        return False
    saved = json.loads(receipt.read_text(encoding="utf-8"))
    if saved["request"] != role["request"] or saved["outputFormat"] != output_format:
        raise SystemExit("Changed inputs; preserve these candidates under a new audition ID.")
    for candidate in saved["candidates"]:
        if candidate.get("status") == "rejected":
            continue
        audio = SOURCE / candidate["file"]
        if not audio.exists() or sha(audio.read_bytes()) != candidate["audioSha256"]:
            raise SystemExit("Missing or changed candidate: " + str(audio))
    return True


def save_selected(role, key):
    path = SOURCE / (role["id"] + ".receipt.json")
    receipt = json.loads(path.read_text(encoding="utf-8"))
    choice = role.get("selectedCandidate")
    if not choice or choice != receipt.get("selectedCandidate"):
        raise SystemExit("Selection is absent or inconsistent: " + role["id"])
    candidate = next(c for c in receipt["candidates"] if c["candidate"] == choice)
    if candidate.get("status") != "selected":
        raise SystemExit("Candidate is not approved: " + role["id"])
    pending = SOURCE / (role["id"] + ".save-pending.json")
    if pending.exists():
        raise SystemExit("Unresolved voice save; inspect account before retrying: " + role["id"])
    if not receipt.get("savedVoiceId"):
        if receipt.get("saveFailure"):
            raise SystemExit("Resolve recorded voice-save failure before retrying: " + role["id"])
        payload = {"voice_name": role["label"] + " - Infantry",
                   "voice_description": role["description"],
                   "generated_voice_id": candidate["generatedVoiceId"]}
        write_json(pending, payload)
        request = urllib.request.Request(
            "https://api.elevenlabs.io/v1/text-to-voice",
            data=json.dumps(payload).encode("utf-8"),
            headers={"xi-api-key": key, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                result = json.loads(response.read())
                request_id = response.headers.get("request-id")
        except urllib.error.HTTPError as error:
            raise SystemExit(f"Voice save HTTP {error.code}; no retry: {role['id']}") from None
        except (OSError, urllib.error.URLError):
            raise SystemExit("Voice save connection failed; inspect account before retrying.") from None
        receipt.update(savedVoiceId=result["voice_id"], savedVoiceName=result.get("name"),
                       saveRequest=payload, saveRequestId=request_id)
        write_json(path, receipt)
        pending.unlink()
    cast_path = SOURCE.parent / "ensemble/cast.json"
    cast = json.loads(cast_path.read_text(encoding="utf-8"))
    cast[role["id"]] = {"voiceId": receipt["savedVoiceId"], "name": role["label"],
                        "generatedVoiceId": candidate["generatedVoiceId"], "candidate": choice,
                        "status": "selected by user; production-model audition pending",
                        "description": role["description"],
                        "source": "personality-auditions/" + candidate["file"]}
    write_json(cast_path, cast)
    print("Saved selected voice:", role["label"], choice, receipt["savedVoiceId"], flush=True)


def generate(role, output_format, key):
    ident = role["id"]
    pending = SOURCE / (ident + ".pending.json")
    response_path = SOURCE / (ident + ".response.json")
    body = json.dumps(role["request"], ensure_ascii=False).encode("utf-8")
    receipt = {"endpoint": ENDPOINT, "request": role["request"],
               "outputFormat": output_format, "inputSha256": sha(body)}
    write_json(pending, receipt)
    request = urllib.request.Request(
        ENDPOINT + "?output_format=" + output_format, data=body,
        headers={"xi-api-key": key, "Content-Type": "application/json"})
    print("Designing:", ident, flush=True)
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            raw = response.read()
            receipt.update(requestId=response.headers.get("request-id"),
                           characterCost=response.headers.get("character-cost"))
    except urllib.error.HTTPError as error:
        raise SystemExit(f"HTTP {error.code}; no retry. Inspect {pending.name}.") from None
    except (OSError, urllib.error.URLError):
        raise SystemExit("Connection failed; no retry. Inspect " + pending.name) from None
    # Retain recoverable provider output before decoding or probing can fail.
    response_path.write_bytes(raw)
    result = json.loads(raw)
    if not result.get("previews"):
        raise SystemExit("No previews returned; inspect " + response_path.name)
    candidates = []
    for index, preview in enumerate(result["previews"]):
        label = chr(ord("A") + index)
        audio = SOURCE / (ident + "-" + label + ".mp3")
        data = base64.b64decode(preview["audio_base_64"], validate=True)
        audio.write_bytes(data)
        candidates.append({"candidate": label, "file": audio.name,
                           "generatedVoiceId": preview["generated_voice_id"],
                           "mediaType": preview["media_type"],
                           "durationSeconds": probe(audio), "audioSha256": sha(data)})
    receipt.update(previewText=result["text"], candidates=candidates,
                   selectedCandidate=None, savedVoiceId=None)
    write_json(SOURCE / (ident + ".receipt.json"), receipt)
    response_path.unlink()
    pending.unlink()
    print("Retained:", ident, len(candidates), "candidates", flush=True)


def render(catalog, output):
    import array

    output = output.resolve()
    if output.is_relative_to(SOURCE.resolve()):
        raise SystemExit("Render outside the retained source directory.")
    roles = [role for role in catalog["roles"] if role.get("status") != "rejected"]
    for role in roles:
        if not retained(role, catalog["outputFormat"]):
            raise SystemExit("Missing candidates: " + role["id"])
    output.mkdir(parents=True, exist_ok=True)

    def encode(path, samples):
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(RATE),
                        "-ac", "1", "-i", "pipe:0", "-c:a", "libmp3lame", "-b:a", "192k", str(path)],
                       input=samples.tobytes(), check=True, capture_output=True)

    def levels(samples):
        peak = max(abs(value) for value in samples)
        rms = math.sqrt(sum(value * value for value in samples) / len(samples))
        if peak == 0 or rms == 0:
            raise SystemExit("Silent audition")
        return {"peakDbFS": round(20 * math.log10(peak / 32768), 2),
                "rmsDbFS": round(20 * math.log10(rms / 32768), 2),
                "clippedSamples": sum(abs(value) >= 32767 for value in samples)}

    report = {"radioFilter": COMMS + ",volume=0.8", "gapSeconds": 2,
              "review": "Decode, duration, hash and level checks only; voice casting awaits listening.",
              "roles": []}
    for role in roles:
        receipt = json.loads((SOURCE / (role["id"] + ".receipt.json")).read_text(encoding="utf-8"))
        clean_reel, radio_reel = array.array("h"), array.array("h")
        row = {"id": role["id"], "label": role["label"], "candidates": []}
        for candidate in receipt["candidates"]:
            if candidate.get("status") == "rejected":
                continue
            path = SOURCE / candidate["file"]
            clean, radio = pcm(path), pcm(path, report["radioFilter"])
            prefix = role["id"] + "-" + candidate["candidate"]
            shutil.copyfile(path, output / (prefix + "-clean.mp3"))
            encode(output / (prefix + "-radio.mp3"), radio)
            if clean_reel:
                clean_reel.extend([0] * (2 * RATE))
                radio_reel.extend([0] * (2 * RATE))
            row["candidates"].append({"candidate": candidate["candidate"],
                                      "cleanStartSeconds": round(len(clean_reel) / RATE, 3),
                                      "radioStartSeconds": round(len(radio_reel) / RATE, 3),
                                      "durationSeconds": round(len(clean) / RATE, 3),
                                      "clean": levels(clean), "radio": levels(radio)})
            clean_reel.extend(clean)
            radio_reel.extend(radio)
        encode(output / (role["id"] + "-clean.mp3"), clean_reel)
        encode(output / (role["id"] + "-radio.mp3"), radio_reel)
        report["roles"].append(row)
        print("Rendered:", role["id"], round(len(radio_reel) / RATE, 1), "seconds", flush=True)
    write_json(output / "review.json", report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--save-selected", action="store_true", help="Save explicitly selected candidates as provider voices")
    parser.add_argument("--role", action="append")
    parser.add_argument("--render", type=Path, help="Offline clean/radio candidates and A/B/C reels")
    args = parser.parse_args()
    catalog = json.loads((SOURCE / "catalog.json").read_text(encoding="utf-8"))
    roles = catalog["roles"]
    ids = [role["id"] for role in roles]
    if len(set(ids)) != len(ids) or any(not ident.isalpha() for ident in ids):
        raise SystemExit("Invalid or duplicate role IDs")
    if args.role and set(args.role) - set(ids):
        raise SystemExit("Unknown role; no request sent.")
    roles = [role for role in roles if not args.role or role["id"] in args.role]
    missing = []
    for role in roles:
        if role.get("status") == "rejected":
            print("Rejected:", role["id"])
            continue
        request = role["request"]
        if not 20 <= len(request["voice_description"]) <= 1000 or not 100 <= len(request["text"]) <= 1000:
            raise SystemExit("Invalid prompt length: " + role["id"])
        if retained(role, catalog["outputFormat"]):
            print("Retained:", role["id"])
        else:
            missing.append(role)
            print("Would design:", role["id"], len(request["text"]), "preview characters")
    if args.generate and missing:
        subprocess.run(["ffprobe", "-version"], check=True, capture_output=True)
        key = credential()
        for role in missing:
            generate(role, catalog["outputFormat"], key)
    if args.save_selected:
        chosen = [role for role in roles if role.get("status") == "selected"]
        if missing:
            raise SystemExit("Validate all approved sources before saving voices.")
        key = credential() if chosen else None
        for role in chosen:
            save_selected(role, key)
    if args.render:
        render(catalog, args.render)


if __name__ == "__main__":
    main()
