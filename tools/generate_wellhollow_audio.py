"""Khettara Bloom briefing voices. Dry-run by default; never invoked by Gradle.

python tools/generate_wellhollow_audio.py [--id ID] --generate [--replace]
python tools/generate_wellhollow_audio.py --register     (audio.json + sounds.json entries, offline)

Speech comes from mod/data/marines/wellhollow_narration.json: one direction tag plus the line's
current string, one voice per line. A line whose string no longer matches its synthesis text is
refused before any request; refresh its synthesisText first. The replay line is shared by every
chapter and recorded once. Sources and receipts land under
asset-pipeline/src/tool/resources/audio/wellhollow/. A changed take needs --replace. No retries after
ambiguous API failures. Needs ffprobe on PATH when generating. Credentials come only from the local
environment.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path

from generate_colony_breach_audio import FORMAT, generate
from generate_wayline_narration import credential

ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "asset-pipeline/src/tool/resources/audio"
SPEECH = AUDIO / "wellhollow/speech"
CATALOG = ROOT / "mod/data/marines/wellhollow_narration.json"
STRINGS = ROOT / "mod/data/strings/strings.json"
SOUNDS = ROOT / "mod/data/config/sounds.json"
OUT = "story/wellhollow"
TAG = re.compile(r"^\[[^\]]*\]\s*")


def strings():
    text = STRINGS.read_text(encoding="utf-8")
    return {k: json.loads(v) for k, v in re.findall(r'"(wellhollow\w+)"\s*:\s*("(?:[^"\\]|\\.)*")', text)}


def plan(catalog):
    """One request per sound ID, validated as a whole before credentials or any paid request."""
    current = strings()
    result, seen = [], {}
    for line in catalog["lines"]:
        if not re.fullmatch(r"[a-z0-9_]+", line["id"]):
            raise SystemExit("Invalid sound ID; no request sent.")
        if TAG.sub("", line["synthesisText"]) != current.get(line["textKey"]):
            raise SystemExit("Stale synthesis text for " + line["id"] + "; no request sent.")
        payload = {"text": line["synthesisText"], "model_id": catalog["modelId"]}
        if catalog["voiceSettings"]:
            payload["voice_settings"] = catalog["voiceSettings"]
        if line["id"] in seen:
            if seen[line["id"]] != (line["voiceId"], payload):
                raise SystemExit("Shared sound " + line["id"] + " differs between chapters; no request sent.")
            continue
        seen[line["id"]] = (line["voiceId"], payload)
        url = "https://api.elevenlabs.io/v1/text-to-speech/" + line["voiceId"] + "?output_format=" + FORMAT
        result.append(dict(id=line["id"], dir=SPEECH, url=url, payload=payload,
                           voice=line["voiceId"], model=catalog["modelId"]))
    return result


def register(items):
    """Idempotent text insertion; audio.json carries comments so it is never re-serialised."""
    manifest = AUDIO / "audio.json"
    text = manifest.read_text(encoding="utf-8")
    fresh = ['{"id": "%s", "source": "wellhollow/speech/%s.mp3", "kind": "ui", "out": "%s", '
             '"processing": "dialogue", "mix": "story_voice"}' % (i["id"], i["id"], OUT)
             for i in items if '"id": "%s"' % i["id"] not in text]
    if fresh:
        end = text.rindex("]")
        text = text[:end].rstrip().rstrip(",") + ",\n    // Khettara Bloom (tools/generate_wellhollow_audio.py)\n    " \
            + ",\n    ".join(fresh) + "\n  " + text[end:]
        manifest.write_text(text, encoding="utf-8", newline="\n")
    print("audio.json: added %d of %d clips." % (len(fresh), len(items)))
    sounds = SOUNDS.read_text(encoding="utf-8")
    entries = ['"%s": [{"file": "sounds/%s/%s.ogg", "pitch": 1, "volume": 1.0}],' % (i["id"], OUT, i["id"])
               for i in items if '"%s":' % i["id"] not in sounds]
    if entries:
        end = sounds.rindex("}")
        sounds = sounds[:end].rstrip() + "\n\t# ----- Khettara Bloom (tools/generate_wellhollow_audio.py) -----\n\t" \
            + "\n\t".join(entries) + "\n" + sounds[end:]
        SOUNDS.write_text(sounds, encoding="utf-8", newline="\n")
    print("sounds.json: added %d of %d ids." % (len(entries), len(items)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--replace", action="store_true")
    parser.add_argument("--register", action="store_true")
    parser.add_argument("--id", action="append", help="Only this sound ID (repeatable)")
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    items = plan(catalog)
    if args.register:
        register(items)
        return
    if args.id:
        unknown = set(args.id) - {i["id"] for i in items}
        if unknown:
            raise SystemExit("Unknown ID " + ", ".join(sorted(unknown)) + "; no request sent.")
        items = [i for i in items if i["id"] in args.id]
    if args.generate:
        subprocess.run(["ffprobe", "-version"], check=True, capture_output=True)
    key = credential() if args.generate else None
    durations = {}
    for item in items:
        duration = generate(item, key, args.replace)
        if duration is not None:
            durations[item["id"]] = duration
    if durations:
        for line in catalog["lines"]:
            if line["id"] in durations:
                line["durationSeconds"] = durations[line["id"]]
        CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
