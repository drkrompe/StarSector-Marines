"""Promote checked Shepherd takes into the runtime catalog and sound bank. Explicit, never a build step.

  python art-source/audio/shepherd/promote-takes.py [--except ID ...]

A take is promoted only when its receipt was generated from the line's current voice, model
and synthesis text, so a script edit after recording can never ship mismatched audio. The line
becomes voiced with its measured duration, and its clip is registered in audio.json and
sounds.json. Lines that fail the receipt check are listed and left text-only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]
CATALOG = ROOT / "mod/data/marines/wayline_narration.json"
TAKES = ROOT / "asset-pipeline/src/tool/resources/audio/wayline"
MANIFEST = ROOT / "asset-pipeline/src/tool/resources/audio/audio.json"
SOUNDS = ROOT / "mod/data/config/sounds.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--except", dest="skip", action="append", default=[])
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    promoted, refused = [], []
    for line in catalog["lines"]:
        if not line["missionId"].startswith("story_shepherd") or line.get("voiced") or line["id"] in args.skip:
            continue
        audio, receipt_path = TAKES / (line["id"] + ".mp3"), TAKES / (line["id"] + ".json")
        if not audio.exists() or not receipt_path.exists():
            refused.append((line["id"], "no take"))
            continue
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        payload = {"text": line["synthesisText"], "model_id": catalog["modelId"]}
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        digest = hashlib.sha256(line["voiceId"].encode() + b"\0" + body).hexdigest()
        if receipt.get("inputSha256") != digest:
            refused.append((line["id"], "take was recorded from different text or voice"))
            continue
        if receipt.get("audioSha256") != hashlib.sha256(audio.read_bytes()).hexdigest():
            refused.append((line["id"], "audio does not match its receipt"))
            continue
        line["voiced"] = True
        line["durationSeconds"] = receipt["durationSeconds"]
        line.pop("durationSource", None)
        promoted.append(line["id"])
    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    manifest = MANIFEST.read_text(encoding="utf-8").split("\n")
    sounds = SOUNDS.read_text(encoding="utf-8").split("\n")
    have_clip = {m.group(1) for m in (re.search(r'"id": "([a-z0-9_]+)"', row) for row in manifest) if m}
    have_sound = {m.group(1) for m in (re.match(r'\s*"([a-z0-9_]+)":', row) for row in sounds) if m}
    clip_at = max(i for i, row in enumerate(manifest) if '"source": "wayline/basin_' in row) + 1
    sound_at = max(i for i, row in enumerate(sounds) if re.match(r'\s*"basin_', row)) + 1
    clips = ['    {"id": "%s", "source": "wayline/%s.mp3", "kind": "ui", "out": "story/wayline", "processing": "dialogue"},'
             % (i, i) for i in promoted if i not in have_clip]
    rows = ['  "%s": [{"file": "sounds/story/wayline/%s.ogg"}],' % (i, i) for i in promoted if i not in have_sound]
    manifest[clip_at:clip_at] = clips
    sounds[sound_at:sound_at] = rows
    MANIFEST.write_text("\n".join(manifest), encoding="utf-8", newline="\n")
    SOUNDS.write_text("\n".join(sounds), encoding="utf-8", newline="\n")
    print("promoted %d, registered %d clips" % (len(promoted), len(clips)))
    for line_id, reason in refused:
        print("NOT PROMOTED", line_id, "-", reason)


if __name__ == "__main__":
    main()
