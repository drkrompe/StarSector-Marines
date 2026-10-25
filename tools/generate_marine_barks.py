"""Marine battle barks. Dry-run by default; never invoked by Gradle.

python tools/generate_marine_barks.py [--id ID] --generate [--replace]
python tools/generate_marine_barks.py --register     (levels, audio.json + sounds.json entries, offline)

Barks are the background voice of the people in a fight with the Rav (colony-breach-nouns.md, Sound):
shouts, cries and grunts heard in the mix, never hero lines. Each cast (the marines, the colony militia,
the colonists) has its own voices and lines; every voice of a cast speaks every line of each of its
kinds, one take each, so a take's ID is <prefix><cast prefix><kind>_<voice>_<line>, the grid
MarineBarks plays from. Effort sounds (the scuffle) are sound generation, several variants under one ID.

--register measures each take's integrated loudness (ffmpeg ebur128) and writes the sounds.json
volume that lands it at its kind's "lufs", so every take of a kind is heard at one level whatever
level it was generated at; the measurements go to levels.json beside the catalog. Sources and
receipts land under asset-pipeline/src/tool/resources/audio/marine-barks/. A changed take needs
--replace. No retries after ambiguous API failures. Needs ffmpeg and ffprobe on PATH.
Credentials come only from the local environment.
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
BARKS = AUDIO / "marine-barks"
CATALOG = BARKS / "barks.json"
LEVELS = BARKS / "levels.json"
SOUNDS = ROOT / "mod/data/config/sounds.json"
HEADER = "Marine barks (tools/generate_marine_barks.py)"


def plan(catalog):
    result = []
    for cast in catalog["casts"]:
        for kind in cast["kinds"]:
            for voice, voice_id in cast["voices"].items():
                for n, line in enumerate(kind["lines"], start=1):
                    sound_id = "%s%s%s_%s_%d" % (catalog["prefix"], cast["prefix"], kind["id"], voice, n)
                    if not re.fullmatch(r"[a-z0-9_]+", sound_id):
                        raise SystemExit("Invalid sound ID; no request sent.")
                    url = "https://api.elevenlabs.io/v1/text-to-speech/" + voice_id + "?output_format=" + FORMAT
                    result.append(dict(id=sound_id, sound=sound_id, dir=BARKS / "speech", url=url, voice=voice_id,
                                       payload={"text": line, "model_id": catalog["speechModelId"]},
                                       model=catalog["speechModelId"], lufs=kind["lufs"]))
    for sfx in catalog["sfx"]:
        for variant in range(1, sfx["variants"] + 1):
            sound = catalog["prefix"] + sfx["id"]
            # The variant index is part of the input so each take differs from its siblings.
            payload = {"text": sfx["prompt"] + " (variation %d)" % variant,
                       "duration_seconds": sfx["durationSeconds"],
                       "prompt_influence": catalog["promptInfluence"], "model_id": catalog["sfxModelId"]}
            url = "https://api.elevenlabs.io/v1/sound-generation?output_format=" + FORMAT
            result.append(dict(id="%s_%d" % (sound, variant), sound=sound, dir=BARKS / "sfx", url=url, voice="",
                               payload=payload, model=catalog["sfxModelId"], lufs=sfx["lufs"]))
    return result


def loudness(path):
    """Integrated loudness in LUFS. Padded to a second so a grunt shorter than one 400 ms block still measures."""
    run = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
                          "-af", "apad=whole_dur=1,ebur128", "-f", "null", "-"],
                         capture_output=True, text=True, check=True)
    found = re.findall(r"I:\s+(-?[\d.]+) LUFS", run.stderr)
    if not found:
        raise SystemExit("No loudness measured for " + str(path))
    return float(found[-1])


def duration(path):
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                            "-of", "default=nw=1:nk=1", str(path)], check=True, capture_output=True, text=True)
    return round(float(probe.stdout.strip()), 3)


def register(catalog, items):
    """Measures every recorded take and writes its level; idempotent text insertion as the breach script does."""
    levels, entries, clips = {}, {}, []
    for item in items:
        audio = item["dir"] / (item["id"] + ".mp3")
        if not audio.exists():
            continue
        lufs = loudness(audio)
        volume = 10 ** ((item["lufs"] - lufs) / 20)
        if volume > 1:
            print("Warning: %s measures %.1f LUFS, under its target %d; played at full volume."
                  % (item["id"], lufs, item["lufs"]))
            volume = 1.0
        levels[item["id"]] = {"lufs": round(lufs, 1), "seconds": duration(audio), "volume": round(volume, 3)}
        clips.append((item["id"], '{"id": "%s", "source": "marine-barks/%s/%s.mp3", "kind": "positional", "out": "%s"}'
                      % (item["id"], item["dir"].name, item["id"], catalog["out"])))
        entries.setdefault(item["sound"], []).append(
            '{"file": "sounds/%s/%s.ogg", "pitch": 1.0, "volume": %s}' % (catalog["out"], item["id"], round(volume, 3)))
    LEVELS.write_text(json.dumps(levels, indent=2) + "\n", encoding="utf-8")
    manifest = AUDIO / "audio.json"
    text = manifest.read_text(encoding="utf-8")
    fresh = [c for c in clips if '"id": "%s"' % c[0] not in text]
    if fresh:
        end = text.rindex("]")
        text = text[:end].rstrip().rstrip(",") + ",\n    // " + HEADER + "\n    " \
            + ",\n    ".join(c[1] for c in fresh) + "\n  " + text[end:]
        manifest.write_text(text, encoding="utf-8", newline="\n")
    print("audio.json: added %d of %d clips." % (len(fresh), len(clips)))
    sounds = SOUNDS.read_text(encoding="utf-8")
    lines = ['"%s": [%s],' % (sound, ", ".join(files)) for sound, files in entries.items()]
    rewritten = 0
    for sound, entry in zip(entries, lines):
        pattern = re.compile(r'^\t"%s": \[.*\],$' % re.escape(sound), re.M)
        found = pattern.search(sounds)
        if found and found.group(0) != "\t" + entry:
            sounds = pattern.sub(lambda ignored: "\t" + entry, sounds)
            rewritten += 1
    new = [entry for sound, entry in zip(entries, lines) if '"%s":' % sound not in sounds]
    if new:
        end = sounds.rindex("}")
        sounds = sounds[:end].rstrip() + "\n\t# ----- " + HEADER + " -----\n\t" + "\n\t".join(new) + "\n" + sounds[end:]
    if new or rewritten:
        SOUNDS.write_text(sounds, encoding="utf-8", newline="\n")
    print("sounds.json: added %d of %d ids, rewrote %d." % (len(new), len(lines), rewritten))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--replace", action="store_true")
    parser.add_argument("--register", action="store_true")
    parser.add_argument("--id", action="append", help="Only this take ID (repeatable)")
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    items = plan(catalog)
    if args.register:
        register(catalog, items)
        return
    if args.id:
        unknown = set(args.id) - {item["id"] for item in items}
        if unknown:
            raise SystemExit("Unknown ID " + ", ".join(sorted(unknown)) + "; no request sent.")
        items = [item for item in items if item["id"] in args.id]
    if args.generate:
        subprocess.run(["ffprobe", "-version"], check=True, capture_output=True)
    key = credential() if args.generate else None
    for item in items:
        generate(item, key, args.replace)


if __name__ == "__main__":
    main()
