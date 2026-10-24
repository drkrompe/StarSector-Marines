"""Package arrival-cinematic shots into the mod (runtime-contract.md).

python art-source/company-prologue/second-wind-cinematic/cinematic_export.py [--shot c1 ...] [--crf 25] [--check] [--timeline-only] [--from-drive]

For each shot with rendered frames this encodes the shipped video and writes the shot's
timeline (captions, overlays, audio cues) beside it:

  mod/cinematic/jangala-arrival/<shot>.mp4          checked in (its frames are not); the player decodes it (JCodec)
  mod/data/cinematic/jangala-arrival/<shot>.json    timeline; small, checked in

The video is H.264 High, CABAC, closed one-second GOPs, no B-frames, BT.709 limited range:
the profile the player's pure-Java decoder is measured on (runtime-contract.md). Do not change
it without re-measuring. Shots with no frames are skipped, so they can land one at a time.

Nothing in a timeline is English. A caption names a strings.json key (hail lines are keyed from their
catalog ids, see caption_key), and speaker labels and the lock's title and detail runs are keys too,
so the player resolves them in the active language and pages the text itself (CaptionPager).
`--timeline-only` rewrites just the JSON, taking the frame count from the timeline already on disk;
it needs no frames and no encode and leaves the checked-in mp4 untouched.

The sound is never in the video. Every cue's level, and the music, come from
arrival-audio/arrival-mix.json (arrival_mix.py), applied to every timeline written here, so a mix
change is `--timeline-only --preview`: rewrite the timelines and hear them on the shipped picture
in build/cinematic/preview/arrival.mp4 (`--shot c5` for one shot).

To add a shot, add a row to SHOTS: the folder holding its composer (or, for a Blender scene, a
plain-Python contract module beside it), the module name, and where its frames are read from
(build/cinematic/<output>/frames), and a row to FINALS once its final frames are on Drive. The module provides captions() (as
(t_in, t_out, speaker_id, catalog_line_id) over the whole take), overlays(), audio_cues() and
duration() (or DURATION). Requires ffmpeg on PATH.
"""
import argparse
import importlib
import json
import re
import shutil
import subprocess
import sys
from collections import namedtuple
from pathlib import Path

import arrival_mix

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
MOD = ROOT / "mod"
VIDEO_DIR = MOD / "cinematic" / "jangala-arrival"
TIMELINE_DIR = MOD / "data" / "cinematic" / "jangala-arrival"
SOUNDS = MOD / "data" / "config" / "sounds.json"
FPS = 24
GOP = 24

Shot = namedtuple("Shot", "folder module output crf", defaults=(None,))
# In play order; the player reads the same order from sequence.json.
SHOTS = {
    "c1": Shot("corvus-approach", "compose_c1", "c1-corvus"),
    "c2": Shot("patrol-abeam", "compose_c2", "c2-patrol"),
    "entry": Shot("patrol-abeam", "compose_entry", "entry"),
    # C3 is film-grain heavy: crf 25 costs 1.1 MB/s there against 0.4 for C1, so it takes 28 (8 MB).
    "c3": Shot("first-open-sky", "compose_c3", "c3-first-open-sky", 28),
    # C4 and C5 are Blender scenes (c4x_scene.py, c5_scene.py import bpy); their contract data is in
    # the plain-Python c4x_contract / c5_contract beside them.
    "c4": Shot("industrial-exterior", "c4x_contract", "c4-landing"),
    "c5": Shot("industrial-exterior", "c5_contract", "c5-yard-title"),
}
CINEMATIC_BUILD = ROOT / "build" / "cinematic"

# The owner-approved final frames of each shot, under the shared Drive's builds/ folder (README.md,
# "Final renders"). `--from-drive` copies them into <frames-root>/<output>/frames before encoding:
# Drive is storage, so nothing reads frames off it in place (CLAUDE.md, "Large files").
# Each shot lists one or more frame folders; a later folder replaces the earlier ones' frames where
# it has them (C4's plume pass re-rendered only frames 0-359). A folder is either flat (frames in
# name order from 0) or split into render-queue chunks named "<first>-<last>/".
DRIVE_BUILDS = Path("G:/My Drive/starsector/builds")
FINALS = {
    # C1's planet over the plumes-trim frames, its air shell no longer an opaque grey ball (planet_fix.py)
    "c1": ["c1-final-dc62710dc-e0c15dc08-plumes-trim/frames", "c1-final-dc62710dc-e0c15dc08-planet-rim/frames"],
    "c2": ["c2-final-dc62710dc-71c4b051b/frames"],
    "entry": ["c3-entry-final-2a0bd10ef/entry/frames"],
    "c3": ["c3-entry-final-2a0bd10ef/c3/frames"],
    "c4": ["c4-final-dc62710dc-f2323ac8f/frames", "c4-final-dc62710dc-f2323ac8f-plumes-trim/frames"],
    "c5": ["c5-final-dc62710dc-f2323ac8f/titled"],   # the frames with the title laid on (c5_title.py)
}


def final_frames(folder):
    """{shot frame index: png} for one Drive frame folder, flat or chunked."""
    chunks = [d for d in folder.iterdir() if d.is_dir() and re.fullmatch(r"\d+-\d+", d.name)]
    if not chunks:
        return dict(enumerate(sorted(folder.glob("*.png"))))
    out = {}
    for chunk in chunks:
        first, last = (int(n) for n in chunk.name.split("-"))
        pngs = sorted(chunk.glob("*.png"))
        if len(pngs) != last - first + 1:
            raise SystemExit("%s holds %d frames, its name says %d" % (chunk, len(pngs), last - first + 1))
        out.update((first + i, png) for i, png in enumerate(pngs))
    return out


def copy_final(shot_id, target):
    """Copy a shot's final frames off Drive into target as 00000.png on, skipping files already
    there at the same size and removing any left over from a longer take."""
    frames = {}
    for folder in FINALS[shot_id]:
        frames.update(final_frames(DRIVE_BUILDS / folder))
    if sorted(frames) != list(range(len(frames))):
        raise SystemExit("%s: the final frames are not one unbroken run from 0" % shot_id)
    target.mkdir(parents=True, exist_ok=True)
    keep = {"%05d.png" % i for i in frames}
    for old in target.glob("*.png"):
        if old.name not in keep:
            old.unlink()
    for i, frame in frames.items():
        out = target / ("%05d.png" % i)
        if not out.is_file() or out.stat().st_size != frame.stat().st_size:
            shutil.copyfile(frame, out)
    print("%s: %d frames from %s" % (shot_id, len(frames), " + ".join(FINALS[shot_id])))

# Labels are strings.json keys; the player resolves them.
SPEAKERS = {
    "approach": {"label_key": "jangalaArrivalSpeakerApproach", "color": "hegemony"},
    "castle": {"label_key": "jangalaArrivalSpeakerCastle", "color": "player"},
}
STRINGS = MOD / "data" / "strings" / "strings.json"
HAIL_CATALOG = MOD / "data" / "marines" / "jangala_arrival_hail.json"
# Catalog lines whose words already live under another strings.json key (none since Castle's lines of
# 2026-10-19 replaced the Nessa memory; each catalog line, hail or Castle's, has its own key).
KEY_OVERRIDES = {}


def caption_key(line_id):
    """The strings.json key of a voiced catalog line: jangala_arrival_hail_01 -> jangalaArrivalHail01."""
    if line_id in KEY_OVERRIDES:
        return KEY_OVERRIDES[line_id]
    head, *rest = line_id.split("_")
    return head + "".join(part.capitalize() for part in rest)


def string_keys():
    """Every marineOps key in strings.json (the file carries # comment lines)."""
    lines = STRINGS.read_text(encoding="utf-8").splitlines()
    text = "\n".join(l for l in lines if not l.lstrip().startswith("#"))
    return json.loads(text)["marineOps"]


def frames_of(directory):
    return sorted(directory.glob("*.png")) if directory.is_dir() else []


def known_sounds():
    text = SOUNDS.read_text(encoding="utf-8")
    return set(re.findall(r'^\s*"([^"]+)"\s*:\s*\[', text, re.M))


SPEAKER_ALIASES = {"jangala approach": "approach", "castle": "castle", "approach": "approach"}


def speaker_id(name):
    """The hail's display names ("Castle", "Jangala Approach") or ids both resolve to an id."""
    return SPEAKER_ALIASES.get(str(name).strip().lower(), name)


def cue_dict(cue):
    """A cue as the contract's tuple (t, sound, volume[, until[, fade_in[, fade_out]]]) or a dict."""
    if isinstance(cue, dict):
        return dict(cue)
    out = {"t": cue[0], "sound": cue[1], "volume": cue[2] if len(cue) > 2 else 1.0}
    for key, i in (("until", 3), ("fade_in", 4), ("fade_out", 5)):
        if len(cue) > i and cue[i] is not None:
            out[key] = cue[i]
    return out


def collect(shot_id, shot, frame_count, overlays=None):
    """The shot's timeline. `overlays` keeps ones already worked out (the shipped timeline's, under
    --timeline-only: the picture has not moved, and C1's lock needs Blender to recompute)."""
    sys.path.insert(0, str(HERE / shot.folder))
    module = importlib.import_module(shot.module)
    duration = module.duration() if hasattr(module, "duration") else module.DURATION
    captions = [{"t_in": a, "t_out": b, "speaker": speaker_id(s), "key": caption_key(line)}
                for a, b, s, line in module.captions()]
    overlays = list(module.overlays()) if overlays is None else overlays
    cues, mix_problems = arrival_mix.apply(shot_id, [cue_dict(c) for c in module.audio_cues()],
                                           list(SHOTS), shot_duration)
    clock = frame_count / FPS
    problems = list(mix_problems)
    if abs(clock - duration) > 1.5 / FPS:
        problems.append("%d frames play %.3fs but the shot says %.3fs" % (frame_count, clock, duration))
    sounds = known_sounds()
    for c in cues:
        if c["sound"] not in sounds:
            problems.append("cue %r is not in sounds.json" % c["sound"])
        if c["t"] < 0:
            problems.append("cue %r starts before the shot" % c["sound"])
    strings = string_keys()
    keys = [c["key"] for c in captions]
    for o in overlays:
        if o.get("kind") == "lock":
            keys += [o["title_key"]] + [d["key"] for d in o["detail"]]
    keys += [v["label_key"] for v in SPEAKERS.values()]
    for key in keys:
        if key not in strings:
            problems.append("string key %r is not in strings.json" % key)
    for c in captions:
        if c["speaker"] not in SPEAKERS:
            problems.append("caption speaker %r unknown" % c["speaker"])
        if not (0 <= c["t_in"] < c["t_out"] <= duration + 1e-3):
            problems.append("caption %r outside the shot" % c["key"])
    if problems:
        raise SystemExit("%s: %s" % (shot_id, "; ".join(problems)))
    return {"id": shot_id, "video": "cinematic/jangala-arrival/%s.mp4" % shot_id, "fps": FPS, "width": 1920,
            "height": 1080, "frames": frame_count, "duration": round(clock, 4),
            "captions": captions, "overlays": overlays, "cues": cues}


def shot_duration(shot_id):
    """A shot's length for cues that run across shots: its shipped timeline's, else its module's."""
    existing = TIMELINE_DIR / (shot_id + ".json")
    if existing.is_file():
        return json.loads(existing.read_text(encoding="utf-8"))["duration"]
    shot = SHOTS[shot_id]
    sys.path.insert(0, str(HERE / shot.folder))
    module = importlib.import_module(shot.module)
    return module.duration() if hasattr(module, "duration") else module.DURATION


def encode(frames_dir, target, crf):
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        "ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS),
        "-i", str(frames_dir / "%05d.png"), "-an",
        "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
        "-c:v", "libx264", "-preset", "slow", "-crf", str(crf), "-profile:v", "high",
        "-bf", "0", "-refs", "1", "-g", str(GOP),
        "-x264-params", "keyint=%d:min-keyint=%d:scenecut=0:open-gop=0:cabac=1" % (GOP, GOP),
        "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
        "-movflags", "+faststart", str(target)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--shot", action="append", help="only these shots (default: every shot with frames)")
    parser.add_argument("--crf", type=int, default=None, help="override every shot's crf (default 25, or the shot's own)")
    parser.add_argument("--frames-root", type=Path, default=CINEMATIC_BUILD,
                        help="folder holding <output>/frames for each shot (default build/cinematic)")
    parser.add_argument("--from-drive", action="store_true",
                        help="first copy each shot's approved final frames (FINALS) off Drive into the frames root")
    parser.add_argument("--timeline-only", action="store_true",
                        help="rewrite only the timeline JSON (frame count from the one on disk); no frames, no encode")
    parser.add_argument("--check", action="store_true", help="validate the timeline; write nothing")
    parser.add_argument("--preview", action="store_true",
                        help="then mix the shipped timelines onto the shipped video, as the player would, to "
                             "build/cinematic/preview/ (the --shot ones, or the whole arrival)")
    args = parser.parse_args()
    if args.check:
        problems = arrival_mix.check_music()
        if problems:
            raise SystemExit("mix: " + "; ".join(problems))
    total = 0
    for shot_id, shot in SHOTS.items():
        if args.shot and shot_id not in args.shot:
            continue
        frames_dir = args.frames_root / shot.output / "frames"
        if args.from_drive and not args.timeline_only:
            copy_final(shot_id, frames_dir)
        frames = frames_of(frames_dir)
        if args.timeline_only:
            existing = TIMELINE_DIR / (shot_id + ".json")
            if not existing.is_file():
                print("%s: no timeline on disk to take a frame count from, skipped" % shot_id)
                continue
            shipped = json.loads(existing.read_text(encoding="utf-8"))
            frame_count = shipped["frames"]
        else:
            frame_count = len(frames)
        if not frame_count:
            print("%s: no frames in %s, skipped" % (shot_id, frames_dir))
            continue
        timeline = collect(shot_id, shot, frame_count, shipped["overlays"] if args.timeline_only else None)
        timeline["speakers"] = SPEAKERS
        if args.check:
            print("%s: ok, %d frames, %d captions, %d cues" % (shot_id, frame_count, len(timeline["captions"]), len(timeline["cues"])))
            continue
        if args.timeline_only:
            TIMELINE_DIR.mkdir(parents=True, exist_ok=True)
            (TIMELINE_DIR / (shot_id + ".json")).write_text(json.dumps(timeline, indent=1) + "\n", encoding="utf-8")
            print("%s: timeline rewritten, %d captions" % (shot_id, len(timeline["captions"])))
            continue
        video = VIDEO_DIR / (shot_id + ".mp4")
        encode(frames_dir, video, args.crf or shot.crf or 25)
        TIMELINE_DIR.mkdir(parents=True, exist_ok=True)
        (TIMELINE_DIR / (shot_id + ".json")).write_text(json.dumps(timeline, indent=1) + "\n", encoding="utf-8")
        size = video.stat().st_size
        total += size
        print("%s: %.1fs, %d frames, %.1f MB" % (shot_id, timeline["duration"], frame_count, size / 1e6))
    shipped = sum(p.stat().st_size for p in VIDEO_DIR.glob("*.mp4")) if VIDEO_DIR.is_dir() else 0
    print("arrival video on disk: %.1f MB" % (shipped / 1e6))
    if args.preview and not args.check:
        arrival_mix.preview(list(SHOTS), set(args.shot) if args.shot else None)


if __name__ == "__main__":
    main()
