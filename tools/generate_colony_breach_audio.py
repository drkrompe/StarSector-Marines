"""Colony Breach audio. Dry-run by default; never invoked by Gradle.

python tools/generate_colony_breach_audio.py [--kind speech|sfx] [--id ID] --generate [--replace]
python tools/generate_colony_breach_audio.py --register     (audio.json + sounds.json entries, offline)

Speech comes from mod/data/marines/colony_breach_narration.json (text to speech, one
voice per line); effects from colony-breach/sfx.json (sound generation, one request
per variant). A sound's "cut" lists takes the owner rejected: never generated, registered
or shipped. A sound with "trimmedFrom" was cut by hand from another sound's takes; it is
registered but never generated. A sound with "reuses" ships no audio of its own: it is registered
as other sounds' kept takes, a pool of {"id", "pitch"} sources each played at its pitch (which also sets
the tempo) and at the sound's own "volume", the owner's rule for the Rav Bloom (re-use takes between
strains rather than ship near-copies). Sources and receipts land under asset-pipeline/src/tool/resources/audio/colony-breach/.
A changed take needs --replace. No retries after ambiguous API failures. Needs ffprobe on
PATH when generating. Credentials come only from the local environment.
"""
import argparse
import hashlib
import json
import re
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

from generate_wayline_narration import credential

ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "asset-pipeline/src/tool/resources/audio"
BREACH = AUDIO / "colony-breach"
SPEECH_CATALOG = ROOT / "mod/data/marines/colony_breach_narration.json"
SFX_CATALOG = BREACH / "sfx.json"
SOUNDS = ROOT / "mod/data/config/sounds.json"
SPEECH_OUT = "story/colony-breach"
FORMAT = "mp3_44100_128"


def speech_plan(catalog):
    result = []
    # Variants are name-bearing alternates of a line (see "replaces"); recorded and registered
    # alongside it, but only "lines" are looked up by the narration queue.
    for line in catalog["lines"] + catalog.get("variants", []):
        if not re.fullmatch(r"[a-z0-9_]+", line["id"]):
            raise SystemExit("Invalid sound ID; no request sent.")
        if not line.get("voiceId") or not line.get("synthesisText"):
            raise SystemExit("No voice or synthesis text for " + line["id"] + "; no request sent.")
        payload = {"text": line["synthesisText"], "model_id": catalog["modelId"]}
        if catalog["voiceSettings"]:
            payload["voice_settings"] = catalog["voiceSettings"]
        url = "https://api.elevenlabs.io/v1/text-to-speech/" + line["voiceId"] + "?output_format=" + FORMAT
        result.append(dict(id=line["id"], dir=BREACH / "speech", url=url, payload=payload,
                           voice=line["voiceId"], model=catalog["modelId"], entry=line))
    return result


def takes(sound):
    """The take numbers that ship: 1..variants less the owner's cuts."""
    return [v for v in range(1, sound["variants"] + 1) if v not in sound.get("cut", [])]


def sfx_plan(catalog):
    result = []
    for sound in catalog["sounds"]:
        if "reuses" in sound:
            continue  # its clips are the reused sound's, already planned under that sound
        for variant in takes(sound):
            sound_id = "%s%s_%d" % (catalog["prefix"], sound["id"], variant)
            if not re.fullmatch(r"[a-z0-9_]+", sound_id):
                raise SystemExit("Invalid sound ID; no request sent.")
            # The variant index is part of the input so each take differs from its siblings.
            prompt = sound["prompt"] + (" (variation %d)" % variant if sound["variants"] > 1 else "")
            payload = {"text": prompt, "duration_seconds": sound["durationSeconds"],
                       "prompt_influence": catalog["promptInfluence"], "model_id": catalog["modelId"]}
            if sound["loop"]:
                payload["loop"] = True
            url = "https://api.elevenlabs.io/v1/sound-generation?output_format=" + FORMAT
            result.append(dict(id=sound_id, dir=BREACH / "sfx", url=url, payload=payload,
                               voice="", model=catalog["modelId"], entry=sound,
                               generated="trimmedFrom" not in sound))
    return result


def generate(item, key, replace):
    body = json.dumps(item["payload"], ensure_ascii=False).encode("utf-8")
    digest = hashlib.sha256(item["voice"].encode() + b"\0" + item["url"].encode() + b"\0" + body).hexdigest()
    audio = item["dir"] / (item["id"] + ".mp3")
    receipt = audio.with_suffix(".json")
    take = 1
    if audio.exists() and receipt.exists():
        saved = json.loads(receipt.read_text(encoding="utf-8"))
        take = saved.get("take", 1) + 1
        if saved["inputSha256"] == digest:
            print("Unchanged:", item["id"], flush=True)
            return None
    if audio.exists() and not replace:
        raise SystemExit("Existing take differs: " + item["id"] + "; use --replace explicitly.")
    print(("Generating:" if key else "Would generate:"), item["id"], flush=True)
    if not key:
        return None
    request = urllib.request.Request(item["url"], data=body,
                                     headers={"xi-api-key": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            data = response.read()
            metadata = {"requestId": response.headers.get("request-id"),
                        "characterCost": response.headers.get("character-cost")}
    except urllib.error.HTTPError as error:
        raise SystemExit("Generation failed: HTTP " + str(error.code) + "; no retry made.") from None
    except (OSError, urllib.error.URLError):
        raise SystemExit("Generation connection failed; no retry made.") from None
    item["dir"].mkdir(parents=True, exist_ok=True)
    temporary = audio.with_suffix(".tmp.mp3")
    temporary.write_bytes(data)
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                            "-of", "default=nw=1:nk=1", str(temporary)],
                           check=True, capture_output=True, text=True)
    duration = round(float(probe.stdout.strip()), 3)
    temporary.replace(audio)
    metadata.update(inputSha256=digest, audioSha256=hashlib.sha256(data).hexdigest(),
                    voiceId=item["voice"], modelId=item["model"], durationSeconds=duration,
                    outputFormat=FORMAT, take=take)
    receipt.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print("Saved:", item["id"], str(duration) + "s", flush=True)
    return duration


def register(speech, sfx):
    """Idempotent text insertion; audio.json carries comments so it is never re-serialised."""
    sfx_catalog = json.loads(SFX_CATALOG.read_text(encoding="utf-8"))
    manifest = AUDIO / "audio.json"
    text = manifest.read_text(encoding="utf-8")
    clips = []
    for item in speech:
        clips.append((item["id"], '{"id": "%s", "source": "colony-breach/speech/%s.mp3", "kind": "ui", '
                      '"out": "%s", "processing": "comms", "mix": "story_voice"}' % (item["id"], item["id"], SPEECH_OUT)))
    for item in sfx:
        clips.append((item["id"], '{"id": "%s", "source": "colony-breach/sfx/%s.mp3", "kind": "positional", '
                      '"out": "%s"}' % (item["id"], item["id"], sfx_catalog["out"])))
    # Effect clips no longer in the plan (cut takes) are dropped so they are never converted.
    planned = {item["id"] for item in sfx}
    clip = re.compile(r'"id": "(%s[a-z0-9_]+)", "source": "colony-breach/sfx/' % sfx_catalog["prefix"])
    lines = text.split("\n")
    kept = [line for line in lines if not (m := clip.search(line)) or m.group(1) in planned]
    removed = len(lines) - len(kept)
    text = "\n".join(kept)
    fresh = [c for c in clips if '"id": "%s"' % c[0] not in text]
    if removed and not fresh:
        manifest.write_text(text, encoding="utf-8", newline="\n")
    if fresh:
        end = text.rindex("]")
        text = text[:end].rstrip().rstrip(",") + ",\n    // Colony Breach (tools/generate_colony_breach_audio.py)\n    " \
            + ",\n    ".join(c[1] for c in fresh) + "\n  " + text[end:]
        manifest.write_text(text, encoding="utf-8", newline="\n")
    print("audio.json: added %d of %d clips, removed %d." % (len(fresh), len(clips), removed))
    sounds = SOUNDS.read_text(encoding="utf-8")
    entries = []
    for item in speech:
        entries.append((item["id"], '"%s": [{"file": "sounds/%s/%s.ogg", "pitch": 1, "volume": 1.0}],'
                        % (item["id"], SPEECH_OUT, item["id"])))
    by_id = {sound["id"]: sound for sound in sfx_catalog["sounds"]}
    for sound in sfx_catalog["sounds"]:
        base = sfx_catalog["prefix"] + sound["id"]
        pool = [(by_id[s["id"]], float(s["pitch"])) for s in sound["reuses"]] if "reuses" in sound             else [(sound, 1.0)]
        if any("reuses" in source for source, _ in pool):
            raise SystemExit(sound["id"] + " reuses a sound that itself reuses; no files written.")
        files = ", ".join('{"file": "sounds/%s/%s_%d.ogg", "pitch": %s, "volume": %s}'
                          % (sfx_catalog["out"], sfx_catalog["prefix"] + source["id"], v, pitch, sound["volume"])
                          for source, pitch in pool for v in takes(source))
        entries.append((base, '"%s": [%s],' % (base, files)))
    # An effect's entry is rewritten in place when its takes change; speech entries never are.
    rewritten = 0
    for base, entry in entries[len(speech):]:
        line = re.compile(r'^\t"%s": \[.*\],$' % re.escape(base), re.M)
        found = line.search(sounds)
        if found and found.group(0) != "\t" + entry:
            sounds = line.sub(lambda ignored: "\t" + entry, sounds)
            rewritten += 1
    fresh = [e for e in entries if '"%s":' % e[0] not in sounds]
    if rewritten and not fresh:
        SOUNDS.write_text(sounds, encoding="utf-8", newline="\n")
    if fresh:
        end = sounds.rindex("}")
        sounds = sounds[:end].rstrip() + "\n\t# ----- Colony Breach (tools/generate_colony_breach_audio.py) -----\n\t" \
            + "\n\t".join(e[1] for e in fresh) + "\n" + sounds[end:]
        SOUNDS.write_text(sounds, encoding="utf-8", newline="\n")
    print("sounds.json: added %d of %d ids, rewrote %d." % (len(fresh), len(entries), rewritten))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--replace", action="store_true")
    parser.add_argument("--register", action="store_true")
    parser.add_argument("--kind", choices=["speech", "sfx"], action="append")
    parser.add_argument("--id", action="append", help="Only this sound ID (repeatable)")
    args = parser.parse_args()
    speech_catalog = json.loads(SPEECH_CATALOG.read_text(encoding="utf-8"))
    sfx_catalog = json.loads(SFX_CATALOG.read_text(encoding="utf-8"))
    speech, sfx = speech_plan(speech_catalog), sfx_plan(sfx_catalog)
    if args.register:
        register(speech, sfx)
        return
    kinds = args.kind or ["speech", "sfx"]
    plan = (speech if "speech" in kinds else []) + (sfx if "sfx" in kinds else [])
    if args.id:
        unknown = set(args.id) - {item["id"] for item in plan}
        if unknown:
            raise SystemExit("Unknown ID " + ", ".join(sorted(unknown)) + "; no request sent.")
        plan = [item for item in plan if item["id"] in args.id]
    if args.generate:
        subprocess.run(["ffprobe", "-version"], check=True, capture_output=True)
    key = credential() if args.generate else None
    changed = False
    plan = [item for item in plan if item.get("generated", True)]
    for item in plan:
        duration = generate(item, key, args.replace)
        if duration is not None and item["dir"].name == "speech":
            item["entry"]["durationSeconds"] = duration
            changed = True
    if changed:
        SPEECH_CATALOG.write_text(json.dumps(speech_catalog, indent=2, ensure_ascii=False) + "\n",
                                  encoding="utf-8")


if __name__ == "__main__":
    main()
