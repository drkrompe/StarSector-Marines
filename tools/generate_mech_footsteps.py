"""Author mech footfalls with ElevenLabs Sound Effects v2 (never run by Gradle).

Dry run: python tools/generate_mech_footsteps.py
Generate missing takes: add --generate; optionally --clip ID (repeatable).
Changed takes require --replace. No automatic retries of paid requests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request

from generate_wayline_narration import credential

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "asset-pipeline/src/tool/resources/audio/mech-footsteps"
CATALOG = SOURCES / "generation.json"
OUTPUT_FORMAT = "mp3_44100_128"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--replace", action="store_true")
    parser.add_argument("--clip", action="append")
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    if args.clip and set(args.clip) - {clip["id"] for clip in catalog["clips"]}:
        raise SystemExit("Unknown clip ID; no request sent.")
    if args.generate:
        subprocess.run(["ffprobe", "-version"], check=True, capture_output=True)
    key = credential() if args.generate else None
    for clip in catalog["clips"]:
        if args.clip and clip["id"] not in args.clip:
            continue
        if not re.fullmatch(r"[a-z0-9_]+", clip["id"]):
            raise SystemExit("Invalid sound ID; no request sent.")
        payload = dict(catalog["settings"], text=clip["prompt"])
        body = json.dumps(payload, sort_keys=True, ensure_ascii=False).encode("utf-8")
        digest = hashlib.sha256(OUTPUT_FORMAT.encode() + b"\0" + body).hexdigest()
        audio = SOURCES / (clip["id"] + ".mp3")
        receipt = audio.with_suffix(".json")
        if audio.exists() and receipt.exists():
            saved = json.loads(receipt.read_text(encoding="utf-8"))
            if (saved["inputSha256"] == digest
                    and saved["audioSha256"] == hashlib.sha256(audio.read_bytes()).hexdigest()):
                print("Unchanged:", clip["id"], flush=True)
                continue
        if audio.exists() and not args.replace:
            raise SystemExit("Existing take differs: " + clip["id"] + "; use --replace explicitly.")
        print("Generating:" if args.generate else "Would generate:", clip["id"], flush=True)
        if not args.generate:
            continue
        request = urllib.request.Request(
            "https://api.elevenlabs.io/v1/sound-generation?output_format=" + OUTPUT_FORMAT,
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
        temporary = audio.with_suffix(".tmp.mp3")
        temporary.write_bytes(data)
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                "-of", "default=nw=1:nk=1", str(temporary)],
                               check=True, capture_output=True, text=True)
        duration = round(float(probe.stdout.strip()), 3)
        temporary.replace(audio)
        metadata.update(inputSha256=digest, audioSha256=hashlib.sha256(data).hexdigest(),
                        modelId=payload["model_id"], durationSeconds=duration,
                        outputFormat=OUTPUT_FORMAT)
        receipt.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        print("Saved:", clip["id"], str(duration) + "s", flush=True)


if __name__ == "__main__":
    main()
