"""Offline operation-radio auditions; paid generation requires --generate.

Default: validate catalog and show missing takes. --render DIR makes comms
previews offline. --line ID restricts generation, not the complete preview reel.
Keep an old take by changing the catalog's take number before a new generation.
No automatic retries and no connection to Gradle or runtime playback.
"""
import argparse
import array
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
import wave

from generate_wayline_narration import credential

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "asset-pipeline/src/tool/resources/audio/operation-radio"
COMMS = ("highpass=f=280:p=2,lowpass=f=3600:p=2,"
         "acompressor=threshold=0.125:ratio=3:attack=5:release=90:makeup=1.5,"
         "asoftclip=type=tanh:threshold=0.65:oversample=2,volume=2.5,"
         "alimiter=limit=0.89:level=false:latency=true")
RATE = 44100


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def source_path(line):
    relative = line.get("retainedSource", f"takes/{line['id']}-take{line['take']}.mp3")
    path = (SOURCE / relative).resolve()
    if not path.is_relative_to(SOURCE.resolve()):
        raise SystemExit("Source path leaves authoring directory.")
    return path


def synthesis(catalog, line):
    voice = catalog["cast"][line["speaker"]]["voiceId"]
    payload = {"text": line["synthesisText"], "model_id": catalog["modelId"]}
    if catalog["voiceSettings"]:
        payload["voice_settings"] = catalog["voiceSettings"]
    body = json.dumps(payload, ensure_ascii=False).encode()
    return voice, body, digest(voice.encode() + b"\0" + body)


def retained(line, catalog):
    path = source_path(line)
    if "retainedSource" in line:
        expected = catalog["retainedHashes"][line["retainedSource"]]
        if not path.exists() or digest(path.read_bytes()) != expected:
            raise SystemExit("Missing or changed approved source: " + line["id"])
        return True
    receipt = path.with_suffix(".json")
    if not path.exists() and not receipt.exists():
        return False
    if not path.exists() or not receipt.exists():
        raise SystemExit("Incomplete take; inspect before another paid request: " + line["id"])
    saved = json.loads(receipt.read_text(encoding="utf-8"))
    if (saved["inputSha256"] != synthesis(catalog, line)[2]
            or saved["audioSha256"] != digest(path.read_bytes())):
        raise SystemExit("Changed take; retain it and select a new take number: " + line["id"])
    return True


def run(args):
    return subprocess.run(args, check=True, capture_output=True).stdout


def generate(catalog, lines):
    # Preflight every selected take before spending credits.
    missing = [line for line in lines if not retained(line, catalog)]
    if not missing:
        print("All selected takes retained; no API calls.")
        return
    for line in missing:
        if source_path(line).with_suffix(".pending.json").exists():
            raise SystemExit("Unresolved previous request: " + line["id"] + "; inspect before retrying.")
    run(["ffprobe", "-version"])
    key = credential()
    for line in missing:
        voice, body, input_hash = synthesis(catalog, line)
        path = source_path(line)
        path.parent.mkdir(parents=True, exist_ok=True)
        # A marker survives an ambiguous failure: rerunning never spends twice silently.
        pending = path.with_suffix(".pending.json")
        if pending.exists():
            raise SystemExit("Unresolved previous request: " + line["id"] + "; inspect before retrying.")
        write_json(pending, {"voiceId": voice, "payload": json.loads(body), "inputSha256": input_hash})
        req = urllib.request.Request(
            "https://api.elevenlabs.io/v1/text-to-speech/" + voice + "?output_format=mp3_44100_128",
            data=body, headers={"xi-api-key": key, "Content-Type": "application/json"})
        print("Generating:", line["id"], flush=True)
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                data = response.read()
                receipt = {"requestId": response.headers.get("request-id"),
                           "characterCost": response.headers.get("character-cost")}
        except urllib.error.HTTPError as error:
            raise SystemExit(f"HTTP {error.code}; no retry. Inspect pending request.") from None
        except (OSError, urllib.error.URLError):
            raise SystemExit("Connection failed; no retry. Inspect pending request.") from None
        path.write_bytes(data)
        duration = float(run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                              "-of", "default=nw=1:nk=1", str(path)]).decode().strip())
        receipt.update(voiceId=voice, modelId=catalog["modelId"], take=line["take"],
                       inputSha256=input_hash, audioSha256=digest(data),
                       durationSeconds=round(duration, 3), payload=json.loads(body),
                       outputFormat="mp3_44100_128")
        write_json(path.with_suffix(".json"), receipt)
        pending.unlink()
        print("Saved:", line["id"], round(duration, 3), "seconds", flush=True)


def pcm(path, filters=None):
    args = ["ffmpeg", "-v", "error", "-i", str(path)]
    if filters:
        args += ["-af", filters]
    data = run(args + ["-ar", str(RATE), "-ac", "1", "-f", "s16le", "pipe:1"])
    result = array.array("h")
    result.frombytes(data)
    if not result:
        raise SystemExit("Empty audio: " + str(path))
    return result


def export(path, data, channels=1):
    # WAV masters make the audition renders inspectable without another decode.
    with wave.open(str(path.with_suffix(".wav")), "wb") as target:
        target.setnchannels(channels)
        target.setsampwidth(2)
        target.setframerate(RATE)
        target.writeframes(data.tobytes())
    run(["ffmpeg", "-v", "error", "-y", "-i", str(path.with_suffix(".wav")),
         "-c:a", "libmp3lame", "-b:a", "192k", str(path.with_suffix(".mp3"))])


def render(catalog, output):
    output = output.resolve()
    if output == SOURCE.resolve() or output.is_relative_to(SOURCE.resolve()):
        raise SystemExit("Render outside the source directory.")
    missing = [line["id"] for line in catalog["lines"] if not retained(line, catalog)]
    if missing:
        raise SystemExit("Missing sources: " + ", ".join(missing))
    output.mkdir(parents=True, exist_ok=True)
    clips = {line["id"]: pcm(source_path(line), COMMS) for line in catalog["lines"]}
    for line_id, samples in clips.items():
        export(output / line_id, samples)
    gap = array.array("h", [0] * round(catalog["mix"]["gapSeconds"] * RATE))

    def sequence(ids):
        samples = array.array("h")
        turns = []
        for line_id in ids:
            if samples:
                samples.extend(gap)
            start = len(samples) / RATE
            samples.extend(clips[line_id])
            turns.append({"line": line_id, "startSeconds": round(start, 3),
                          "endSeconds": round(len(samples) / RATE, 3)})
        return samples, turns

    report = {"commsFilter": COMMS, "exchanges": [], "reel": []}
    for exchange in catalog["exchanges"]:
        samples, turns = sequence(exchange["turns"])
        export(output / exchange["id"], samples)
        report["exchanges"].append({"id": exchange["id"], "turns": turns})

    # A compressed role/mix audition, not a proposed in-game cadence.
    exchanges = {e["id"]: e for e in catalog["exchanges"]}
    montage_order = ["01-objective-assignment", "03-covered-crossing",
                     "02-contested-status", "04-reinforcement-drop", "05-objective-secured"]
    foreground = array.array("h")
    bed_start = None
    for exchange_id in montage_order:
        if foreground:
            foreground.extend([0] * round(catalog["mix"]["exchangeGapSeconds"] * RATE))
        start = len(foreground)
        ids = exchanges[exchange_id]["turns"]
        if exchange_id == "04-reinforcement-drop":
            ids = ids[1:]  # Pilot's opening report already arrived over the secondary net.
        samples, turns = sequence(ids)
        if exchange_id == "02-contested-status":
            bed_start = start + round(1.2 * RATE)
        foreground.extend(samples)
        for turn in turns:
            report["reel"].append({**turn, "channel": "foreground",
                                  "startSeconds": round(start / RATE + turn["startSeconds"], 3),
                                  "endSeconds": round(start / RATE + turn["endSeconds"], 3)})
    bed = clips["rook_drop_complete"]
    gain = 10 ** (catalog["mix"]["backgroundGainDb"] / 20)
    pan = catalog["mix"]["backgroundPan"]
    stereo = array.array("h")
    peak = 0
    for i, sample in enumerate(foreground):
        j = i - bed_start
        secondary = bed[j] * gain if 0 <= j < len(bed) else 0
        # Fixed 0.8 headroom; foreground centered, secondary net right-biased.
        left = round(0.8 * (sample + secondary * (1-pan)))
        right = round(0.8 * (sample + secondary * (1+pan)))
        peak = max(peak, abs(left), abs(right))
        if peak >= 32767:
            raise SystemExit("Mix would clip; reduce mix gain.")
        stereo.extend((left, right))
    report["reel"].append({"line": "rook_drop_complete", "channel": "secondary",
                           "startSeconds": round(bed_start / RATE, 3),
                           "endSeconds": round((bed_start + len(bed)) / RATE, 3),
                           "gainDb": catalog["mix"]["backgroundGainDb"], "pan": pan})
    report.update(durationSeconds=round(len(foreground)/RATE, 3),
                  peakDbFS=round(20*math.log10(peak/32768), 2),
                  note=catalog["mix"]["note"])
    export(output / "operation-radio-reel", stereo, 2)
    write_json(output / "render.json", report)
    print("Rendered:", output, report["durationSeconds"], "seconds; peak", report["peakDbFS"], "dBFS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--line", action="append")
    parser.add_argument("--render", type=Path)
    args = parser.parse_args()
    catalog = json.loads((SOURCE / "catalog.json").read_text(encoding="utf-8"))
    ids = [line["id"] for line in catalog["lines"]]
    if len(ids) != len(set(ids)) or any(not re.fullmatch(r"[a-z0-9_]+", i) for i in ids):
        raise SystemExit("Invalid or duplicate line IDs.")
    if args.line and set(args.line) - set(ids):
        raise SystemExit("Unknown line ID; no request sent.")
    for line in catalog["lines"]:
        if line["speaker"] not in catalog["cast"] or line["recipient"] not in catalog["cast"]:
            raise SystemExit("Unknown speaker or recipient.")
    for exchange in catalog["exchanges"]:
        if set(exchange["turns"]) - set(ids):
            raise SystemExit("Unknown exchange turn.")
    selected = [line for line in catalog["lines"] if not args.line or line["id"] in args.line]
    if args.generate:
        generate(catalog, selected)
    elif not args.render:
        for line in selected:
            print("Retained:" if retained(line, catalog) else "Would generate:", line["id"])
    if args.render:
        render(catalog, args.render)


if __name__ == "__main__":
    main()
