"""Package arrival-cinematic shots into the mod (runtime-contract.md).

python art-source/company-prologue/second-wind-cinematic/cinematic_export.py [--shot c1 ...] [--crf 25] [--check]

For each shot with rendered frames this encodes the shipped video and writes the shot's
timeline (captions, overlays, audio cues) beside it:

  mod/cinematic/jangala-arrival/<shot>.mp4          gitignored; the player decodes it (JCodec)
  mod/data/cinematic/jangala-arrival/<shot>.json    timeline; small, checked in

The video is H.264 High, CABAC, closed one-second GOPs, no B-frames, BT.709 limited range:
the profile the player's pure-Java decoder is measured on (runtime-contract.md). Do not change
it without re-measuring. Shots with no frames are skipped, so they can land one at a time.

To add a shot, add a row to SHOTS: the folder holding its composer, the module name, and where
the composer writes frames. The module provides captions(), overlays(), audio_cues() and
duration() (or DURATION). Requires ffmpeg on PATH.
"""
import argparse
import importlib
import json
import re
import subprocess
import sys
from collections import namedtuple
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
MOD = ROOT / "mod"
VIDEO_DIR = MOD / "cinematic" / "jangala-arrival"
TIMELINE_DIR = MOD / "data" / "cinematic" / "jangala-arrival"
SOUNDS = MOD / "data" / "config" / "sounds.json"
FPS = 24
GOP = 24

Shot = namedtuple("Shot", "folder module frames")
# In play order; the player reads the same order from sequence.json.
SHOTS = {
    "c1": Shot("corvus-approach", "compose_c1", ROOT / "build" / "cinematic" / "c1-corvus" / "frames"),
}

SPEAKERS = {
    "approach": {"label": "JANGALA APPROACH", "color": "hegemony"},
    "castle": {"label": "CASTLE  /  SECOND WIND", "color": "player"},
}


def frames_of(directory):
    return sorted(directory.glob("*.png")) if directory.is_dir() else []


def known_sounds():
    text = SOUNDS.read_text(encoding="utf-8")
    return set(re.findall(r'^\s*"([^"]+)"\s*:\s*\[', text, re.M))


def collect(shot_id, shot, frames):
    sys.path.insert(0, str(HERE / shot.folder))
    module = importlib.import_module(shot.module)
    duration = module.duration() if hasattr(module, "duration") else module.DURATION
    captions = [{"t_in": a, "t_out": b, "speaker": s, "text": t} for a, b, s, t in module.captions()]
    overlays = list(module.overlays())
    cues = list(module.audio_cues())
    clock = len(frames) / FPS
    problems = []
    if abs(clock - duration) > 1.5 / FPS:
        problems.append("%d frames play %.3fs but the shot says %.3fs" % (len(frames), clock, duration))
    sounds = known_sounds()
    for c in cues:
        if c["sound"] not in sounds:
            problems.append("cue %r is not in sounds.json" % c["sound"])
        if c["t"] < 0:
            problems.append("cue %r starts before the shot" % c["sound"])
    for c in captions:
        if c["speaker"] not in SPEAKERS:
            problems.append("caption speaker %r unknown" % c["speaker"])
        if not (0 <= c["t_in"] < c["t_out"] <= duration + 1e-3):
            problems.append("caption %r outside the shot" % c["text"][:30])
    if problems:
        raise SystemExit("%s: %s" % (shot_id, "; ".join(problems)))
    return {"id": shot_id, "video": "cinematic/jangala-arrival/%s.mp4" % shot_id, "fps": FPS, "width": 1920,
            "height": 1080, "frames": len(frames), "duration": round(clock, 4),
            "captions": captions, "overlays": overlays, "cues": cues}


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
    parser.add_argument("--crf", type=int, default=25)
    parser.add_argument("--check", action="store_true", help="validate the timeline; write nothing")
    args = parser.parse_args()
    total = 0
    for shot_id, shot in SHOTS.items():
        if args.shot and shot_id not in args.shot:
            continue
        frames = frames_of(shot.frames)
        if not frames:
            print("%s: no frames in %s, skipped" % (shot_id, shot.frames))
            continue
        timeline = collect(shot_id, shot, frames)
        timeline["speakers"] = SPEAKERS
        if args.check:
            print("%s: ok, %d frames, %d captions, %d cues" % (shot_id, len(frames), len(timeline["captions"]), len(timeline["cues"])))
            continue
        video = VIDEO_DIR / (shot_id + ".mp4")
        encode(shot.frames, video, args.crf)
        TIMELINE_DIR.mkdir(parents=True, exist_ok=True)
        (TIMELINE_DIR / (shot_id + ".json")).write_text(json.dumps(timeline, indent=1) + "\n", encoding="utf-8")
        size = video.stat().st_size
        total += size
        print("%s: %.1fs, %d frames, %.1f MB" % (shot_id, timeline["duration"], len(frames), size / 1e6))
    shipped = sum(p.stat().st_size for p in VIDEO_DIR.glob("*.mp4")) if VIDEO_DIR.is_dir() else 0
    print("arrival video on disk: %.1f MB" % (shipped / 1e6))


if __name__ == "__main__":
    main()
