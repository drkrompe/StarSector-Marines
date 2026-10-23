#!/usr/bin/env python3
"""Fold texture-usage recordings into the checked-in usage record.

usage: texture_usage.py merge <record.json> <recording-or-dir> [...]

A recording is what a run with texture usage switched on writes: the headless evidence
(-PtextureUsage, under build/texture-usage/) or a dev-mode game
(<starsector>/saves/common/marines_texture_usage.data). Each holds, per image path and per
source label, the largest size the whole image was drawn at, scaled to the reference conditions
(1920x1080, closest battle zoom, largest UI scale). Merging keeps the larger figure per path and
label, so the record only grows; delete an entry (or a label under it) to measure it afresh.

The record is the input to the release build's texture fit (tools/optimize_pngs.py); see
roadmap/release/design/texture-fit.md.
"""
import json
import os
import sys


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def recordings(args):
    for arg in args:
        if os.path.isdir(arg):
            for name in sorted(os.listdir(arg)):
                if name.endswith(".json"):
                    yield os.path.join(arg, name)
        elif os.path.isfile(arg):
            yield arg
        else:
            print("texture_usage: no recording at %s (skipped)" % arg)


def merge_into(record, recording):
    textures = record.setdefault("textures", {})
    changed = 0
    for path, entry in recording.get("textures", {}).items():
        target = textures.setdefault(path, {"sliced": False, "sources": {}})
        if entry.get("sliced") and not target.get("sliced"):
            target["sliced"] = True
            changed += 1
        for label, usage in entry.get("sources", {}).items():
            old = target["sources"].get(label)
            if old is None:
                target["sources"][label] = dict(usage)
                changed += 1
                continue
            if usage.get("displayWidth", 0) > old.get("displayWidth", 0):
                for key in ("displayWidth", "domain", "mapWidth", "mapHeight"):
                    if key in usage:
                        old[key] = usage[key]
                    else:
                        old.pop(key, None)
                changed += 1
            if usage.get("displayHeight", 0) > old.get("displayHeight", 0):
                old["displayHeight"] = usage["displayHeight"]
                changed += 1
    return changed


def write(record, path):
    record["textures"] = {k: record["textures"][k] for k in sorted(record["textures"])}
    for entry in record["textures"].values():
        entry["sources"] = {k: entry["sources"][k] for k in sorted(entry["sources"])}
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    tmp = path + ".part"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(record, f, indent=1, sort_keys=False)
        f.write("\n")
    os.replace(tmp, path)


def main(argv):
    if len(argv) < 3 or argv[0] != "merge":
        print(__doc__)
        return 2
    target = argv[1]
    record = load(target) if os.path.exists(target) else {}
    record["reference"] = {"screen": "1920x1080", "battleZoom": 16.0, "uiScale": 2.0}
    total = 0
    files = 0
    for path in recordings(argv[2:]):
        try:
            recording = load(path)
        except (OSError, ValueError) as e:
            print("texture_usage: unreadable %s (%s)" % (path, e))
            continue
        files += 1
        total += merge_into(record, recording)
    write(record, target)
    print("texture_usage: merged %d recording(s), %d change(s); %d textures in %s"
          % (files, total, len(record.get("textures", {})), target))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
