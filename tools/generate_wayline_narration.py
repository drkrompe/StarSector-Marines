"""Explicit authoring command. Dry-run by default; never invoked by Gradle.

python tools/generate_wayline_narration.py --generate [--line SOUND_ID]
Changed recordings require --replace. No retries after ambiguous API failures.
Requires ffprobe on PATH. Credentials are read only from the local environment.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "mod/data/marines/wayline_narration.json"
SOURCES = ROOT / "asset-pipeline/src/tool/resources/audio/wayline"


def credential():
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key and sys.platform == "win32":
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as env:
                key = winreg.QueryValueEx(env, "ELEVENLABS_API_KEY")[0]
        except OSError:
            pass
    if not key:
        raise SystemExit("ELEVENLABS_API_KEY is unavailable; no request sent.")
    return key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--replace", action="store_true")
    parser.add_argument("--cast", choices=["wayline", "company-officer", "operation-radio"], default="wayline")
    parser.add_argument("--line", action="append", help="Generate only this sound ID (repeatable)")
    args = parser.parse_args()
    catalog_path = CATALOG if args.cast == "wayline" else ROOT / "mod/data/marines/company_officer_narration.json"
    sources = SOURCES if args.cast == "wayline" else SOURCES.parent / "company-officer"
    if args.cast == "operation-radio":
        catalog_path = ROOT / "mod/data/marines/operation_radio.json"
        sources = SOURCES.parent / "operation-radio/live"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    if args.line and set(args.line) - {line["id"] for line in catalog["lines"]}:
        raise SystemExit("Unknown line ID; no request sent.")
    if args.generate:
        subprocess.run(["ffprobe", "-version"], check=True, capture_output=True)
    key = credential() if args.generate else None
    for line in catalog["lines"]:
        if args.line and line["id"] not in args.line:
            continue
        if not re.fullmatch(r"[a-z0-9_]+", line["id"]):
            raise SystemExit("Invalid sound ID")
        voice_id = (catalog["voices"][line["speaker"]]["voiceId"]
                    if args.cast == "operation-radio" else line.get("voiceId", catalog["voiceId"]))
        if line.get("retained"):
            audio = sources / (line["id"] + ".mp3")
            receipt = audio.with_suffix(".json")
            if not audio.exists() or not receipt.exists():
                raise SystemExit("Missing retained source: " + line["id"])
            saved = json.loads(receipt.read_text(encoding="utf-8"))
            if saved["audioSha256"] != hashlib.sha256(audio.read_bytes()).hexdigest():
                raise SystemExit("Changed retained source: " + line["id"])
            print("Retained:", line["id"], flush=True)
            continue
        payload = {"text": line["synthesisText"], "model_id": catalog["modelId"]}
        if catalog["voiceSettings"]:
            payload["voice_settings"] = catalog["voiceSettings"]
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        digest = hashlib.sha256(voice_id.encode() + b"\0" + body).hexdigest()
        audio = sources / (line["id"] + ".mp3")
        receipt = audio.with_suffix(".json")
        take = 1
        if audio.exists() and receipt.exists():
            saved = json.loads(receipt.read_text(encoding="utf-8"))
            take = saved.get("take", 1) + 1
            if saved["inputSha256"] == digest:
                print("Unchanged:", line["id"], flush=True)
                continue
        if audio.exists() and not args.replace:
            raise SystemExit("Existing take differs: " + line["id"] + "; use --replace explicitly.")
        print(("Generating:" if args.generate else "Would generate:"), line["id"], flush=True)
        if not args.generate:
            continue
        request = urllib.request.Request(
            "https://api.elevenlabs.io/v1/text-to-speech/" + voice_id
            + "?output_format=mp3_44100_128",
            data=body, headers={"xi-api-key": key, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
                metadata = {"requestId": response.headers.get("request-id"),
                            "characterCost": response.headers.get("character-cost")}
        except urllib.error.HTTPError as error:
            raise SystemExit("Generation failed: HTTP " + str(error.code) + "; no retry made.") from None
        except (OSError, urllib.error.URLError):
            raise SystemExit("Generation connection failed; no retry made.") from None
        sources.mkdir(parents=True, exist_ok=True)
        temporary = audio.with_suffix(".tmp.mp3")
        temporary.write_bytes(data)
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                "-of", "default=nw=1:nk=1", str(temporary)],
                               check=True, capture_output=True, text=True)
        duration = round(float(probe.stdout.strip()), 3)
        temporary.replace(audio)
        line["durationSeconds"] = duration
        metadata.update(inputSha256=digest, audioSha256=hashlib.sha256(data).hexdigest(),
                        voiceId=voice_id, modelId=catalog["modelId"],
                        durationSeconds=duration, outputFormat="mp3_44100_128", take=take)
        receipt.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        catalog_path.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("Saved:", line["id"], str(duration) + "s", flush=True)


if __name__ == "__main__":
    main()
