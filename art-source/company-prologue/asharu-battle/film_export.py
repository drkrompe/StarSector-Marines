"""Package the Asharu wreckfall film into the mod, as the Jangala arrival is (runtime-contract.md).

  python art-source/company-prologue/asharu-battle/film_export.py [--shot s6 ...] [--timeline-only] [--preview]

For each shot this encodes the shipped video and writes its timeline beside it:

  mod/cinematic/asharu-battle/<shot>.mp4          checked in; the player decodes it (JCodec)
  mod/data/cinematic/asharu-battle/<shot>.json    captions, speakers and sound cues
  mod/data/cinematic/asharu-battle/sequence.json  the shot order, and "captions": "translated"

The picture is the owner's approved finals (FINALS: the render each one came from, as the Battle
Cut page lists it), read off Drive, and the terminal plates S0 and S13 rendered by
`recording_plate.py s0|s13 <dir> --words` from the locked radio log (PLATES). The words on the
terminal are drawn into the plate, so they are English only and the date is the plate's own.
The video profile is cinematic_export.py's, which the player's decoder is measured on.

The sound is battle-audio/film-cues.json (film_audio.py: the locked mix, one clip per sound), each
cue on the shot its start falls in. The captions are the locked mix's playing lines, keyed by
radio_log (asharuRadio<Shot>_<n>), with each speaker's registry callsign and hull role as keys too;
the player draws them only when the game is not in English (owner, 2026-10-30), since the voices
are English. `--timeline-only` rewrites just the JSON and strings; `--preview` mixes every cue onto
the shipped video at build/asharu-film/preview.mp4, as the player would play it.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

import radio_log

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
MOD = ROOT / "mod"
FILM = "asharu-battle"
VIDEO_DIR = MOD / "cinematic" / FILM
TIMELINE_DIR = MOD / "data" / "cinematic" / FILM
STRINGS = MOD / "data" / "strings" / "strings.json"
SOUNDS = MOD / "data" / "config" / "sounds.json"
FINAL = HERE / "battle-audio" / "final-mix.json"
CUES = HERE / "battle-audio" / "film-cues.json"
CLIPS = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio"
GAME_SOUNDS = Path(r"C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core")
DRIVE = Path(r"G:\My Drive\starsector\builds\asharu-frames")
PLATES = ROOT / "build" / "asharu-film" / "plates"
PREVIEW = ROOT / "build" / "asharu-film" / "preview.mp4"
FPS, GOP, CRF = 24, 24, 25
SR = 48000

# (shot id, sequence start, end) in play order: asharu_preview.segments, S8-S9 as one shot
SHOTS = [("s0", -21.0, 10.0), ("s1a", 10.0, 16.0), ("s1", 16.0, 24.0), ("s2", 24.0, 30.0), ("s3", 30.0, 38.0),
         ("s4", 38.0, 44.0), ("s5", 44.0, 50.0), ("s6", 50.0, 58.0), ("s6b", 58.0, 66.0), ("s6c", 66.0, 74.0),
         ("s6d", 74.0, 80.0), ("s7", 80.0, 88.0), ("s8-s9", 88.0, 102.0), ("s9b", 102.0, 110.0),
         ("s10", 110.0, 118.0), ("s11", 118.0, 126.0), ("s12", 126.0, 138.0), ("s13", 138.0, 162.5)]
# the approved final of each shot, as the Battle Cut page plays it (render commit / master video)
FINALS = {"s1a": "c6c31b0cf/s1a-k0.mp4", "s1": "77829d94c/s1-k1.mp4", "s2": "27175ec89/s2-m2.mp4",
          "s3": "735d44ffd/s3-k1b.mp4", "s4": "735d44ffd/s4-k2.mp4", "s5": "d44024e85/s5-m5.mp4",
          "s6": "735d44ffd/s6-k3.mp4", "s6b": "a5c5be9ad/s6b-k3c.mp4", "s6c": "a5c5be9ad/s6c-k3d.mp4",
          "s6d": "a5c5be9ad/s6d-k3e.mp4", "s7": "a5c5be9ad/s7-k3b.mp4", "s8-s9": "7b1fde1ec/s89-k4.mp4",
          "s9b": "dff7ff26e/s9b-k4b.mp4", "s10": "a5c5be9ad/s10-k5.mp4", "s11": "a5c5be9ad/s11-m11.mp4",
          "s12": "c6c31b0cf/s12-k6.mp4"}
PLATE_FRAMES = {"s0": ("rec0_f%05d.png", 0), "s13": ("rec13_f%05d.png", 3312)}    # (pattern, first frame)


def camel(text):
    return "".join(w.capitalize() for w in re.split(r"[^A-Za-z0-9]+", text) if w)


def speaker_id(callsign):
    return re.sub(r"[^a-z0-9]+", "_", callsign.lower()).strip("_")


def shot_of(t):
    for sid, start, end in SHOTS:
        if start <= t < end:
            return sid, start
    return SHOTS[-1][0], SHOTS[-1][1]


def encode(source, target, first=None):
    """cinematic_export.encode's profile, from a master video or a numbered plate from frame `first`."""
    target.parent.mkdir(parents=True, exist_ok=True)
    src = ["-i", str(source)] if first is None else \
        ["-framerate", str(FPS), "-start_number", str(first), "-i", str(source)]
    subprocess.run([
        "ffmpeg", "-hide_banner", "-v", "error", "-y", *src, "-an",
        "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
        "-c:v", "libx264", "-preset", "slow", "-crf", str(CRF), "-profile:v", "high",
        "-bf", "0", "-refs", "1", "-g", str(GOP),
        "-x264-params", "keyint=%d:min-keyint=%d:scenecut=0:open-gop=0:cabac=1" % (GOP, GOP),
        "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
        "-movflags", "+faststart", str(target)], check=True)


def frames_of(video):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-count_frames", "-show_entries",
                          "stream=nb_read_frames", "-of", "csv=p=0", str(video)], capture_output=True, text=True)
    return int(out.stdout.strip())


def lines():
    """The locked mix's playing lines with their radio_log keys: (key, callsign, t0, t1, text)."""
    final = json.loads(FINAL.read_text(encoding="utf-8"))
    keyed = {(ln[1], round(ln[2], 2)): ln for ln in radio_log.LINES}
    out = []
    for d in final["lines"].values():
        if d.get("off") or d.get("placeholder"):
            continue
        ln = keyed[(d["speaker"], round(d["t0"], 2))]
        out.append((ln[0], d["speaker"], d["t0"], d["t1"], d["text"]))
    return sorted(out, key=lambda ln: ln[2])


def timelines():
    cues = json.loads(CUES.read_text(encoding="utf-8"))["cues"]
    spoken = lines()
    strings = {}
    for key, who, *_rest, text in spoken:
        strings[key] = text
        label, role = radio_log.caption(who)
        strings["asharuRadioSpeaker" + camel(who)] = label
        if role:
            strings["asharuRadioRole" + camel(who)] = role
    out = {}
    for sid, start, end in SHOTS:
        shot = {"captions": [], "cues": [], "speakers": {}}
        for key, who, t0, t1, _text in spoken:
            if shot_of(t0)[0] != sid:
                continue
            shot["captions"].append({"t_in": round(t0 - start, 4), "t_out": round(t1 - start, 4),
                                     "speaker": speaker_id(who), "key": key})
            sp = {"label_key": "asharuRadioSpeaker" + camel(who), "color": "player"}
            if "asharuRadioRole" + camel(who) in strings:
                sp["role_key"] = "asharuRadioRole" + camel(who)
            shot["speakers"][speaker_id(who)] = sp
        for c in cues:
            if shot_of(c["t"])[0] != sid:
                continue
            cue = dict(c, t=round(c["t"] - start, 4))
            if "until" in cue:
                cue["until"] = round(cue["until"] - start, 4)
            shot["cues"].append(cue)
        out[sid] = shot
    return out, strings


def write_strings(strings):
    text = STRINGS.read_text(encoding="utf-8")
    kept = [ln for ln in text.split("\n") if '"asharuRadio' not in ln]
    end = max(i for i, ln in enumerate(kept) if ln.strip() == "}")       # closes marineOps
    end = max(i for i in range(end) if kept[i].strip() == "}")
    last = max(i for i in range(end) if kept[i].strip().startswith('"'))
    kept[last] = kept[last].rstrip().rstrip(",") + ","
    indent = kept[last][:len(kept[last]) - len(kept[last].lstrip())]
    new = [f"{indent}{json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}," for k, v in sorted(strings.items())]
    new[-1] = new[-1].rstrip(",")
    STRINGS.write_text("\n".join(kept[:last + 1] + new + kept[last + 1:]), encoding="utf-8", newline="\n")


def write_timelines(shots, only):
    TIMELINE_DIR.mkdir(parents=True, exist_ok=True)
    for sid, start, end in SHOTS:
        if only and sid not in only:
            continue
        video = VIDEO_DIR / f"{sid}.mp4"
        frames = frames_of(video) if video.exists() else round((end - start) * FPS)
        body = {"id": sid, "video": f"cinematic/{FILM}/{sid}.mp4", "fps": FPS, "width": 1920, "height": 1080,
                "frames": frames, "duration": round(frames / FPS, 4), **shots[sid]}
        (TIMELINE_DIR / f"{sid}.json").write_text(json.dumps(body, indent=1, ensure_ascii=False) + "\n",
                                                  encoding="utf-8", newline="\n")
        print("%-6s %4d frames  %2d captions  %2d cues" % (sid, frames, len(body["captions"]), len(body["cues"])))
    (TIMELINE_DIR / "sequence.json").write_text(json.dumps(
        {"id": FILM, "captions": "translated", "shots": [s for s, _a, _b in SHOTS]}, indent=1) + "\n",
        encoding="utf-8", newline="\n")


def sound_file(sound):
    """The file a cue's sound id plays, and its sounds.json volume."""
    text = SOUNDS.read_text(encoding="utf-8")
    m = re.search(r'"%s": \[\{"file": "([^"]+)", "pitch": [0-9.]+, "volume": ([0-9.]+)\}\]' % re.escape(sound), text)
    rel, volume = m.group(1), float(m.group(2))
    if rel.startswith("sounds/story/"):
        return CLIPS / FILM / (Path(rel).stem + ".flac"), volume
    return GAME_SOUNDS / rel, volume


def film_mix():
    """Every cue mixed on the sequence clock as the player schedules them: float stereo at SR, from
    the film's first frame, unclipped."""
    total = SHOTS[-1][2] - SHOTS[0][1]
    mix = np.zeros((int(total * SR) + SR * 30, 2))
    for c in json.loads(CUES.read_text(encoding="utf-8"))["cues"]:
        path, volume = sound_file(c["sound"])
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "2", "-ar", str(SR), "-f", "f32le", "-"],
                             check=True, capture_output=True).stdout
        x = np.frombuffer(raw, np.float32).astype(np.float64).reshape(-1, 2) * volume * c["volume"]
        if "until" in c:
            n = int((c["until"] - c["t"]) * SR)
            x = x[:n]
            fo = int(c.get("fade_out", 0) * SR)
            if fo and len(x) >= n:
                x[-fo:] *= np.linspace(1, 0, fo)[:, None]
        i = int(round((c["t"] - SHOTS[0][1]) * SR))
        mix[i:i + len(x)] += x
    return mix[:int(total * SR)]


def preview():
    """Every cue mixed onto the shipped video, on the sequence clock, as the player schedules them."""
    mix = film_mix()
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    wav = PREVIEW.with_suffix(".wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-", str(wav)],
                   input=mix.astype("<f4").tobytes(), check=True)
    listing = PREVIEW.with_suffix(".txt")
    listing.write_text("".join(f"file '{(VIDEO_DIR / (s + '.mp4')).as_posix()}'\n" for s, _a, _b in SHOTS))
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(listing), "-i", str(wav),
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", str(PREVIEW)], check=True)
    print("preview", PREVIEW, "peak %.1f dB" % (20 * np.log10(max(1e-9, np.abs(mix).max()))))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--shot", action="append")
    ap.add_argument("--timeline-only", action="store_true")
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    if not args.timeline_only:
        for sid, start, end in SHOTS:
            if args.shot and sid not in args.shot:
                continue
            if sid in PLATE_FRAMES:
                pattern, first = PLATE_FRAMES[sid]
                encode(PLATES / sid / pattern, VIDEO_DIR / f"{sid}.mp4", first)
            else:
                encode(DRIVE / FINALS[sid], VIDEO_DIR / f"{sid}.mp4")
            got, want = frames_of(VIDEO_DIR / f"{sid}.mp4"), round((end - start) * FPS)
            print("%-6s encoded %d frames (%d expected), %.1f MB" % (
                sid, got, want, (VIDEO_DIR / f"{sid}.mp4").stat().st_size / 1e6))
            if got != want:
                raise SystemExit(f"{sid}: {got} frames, the shot is {want}")
    shots, strings = timelines()
    write_timelines(shots, args.shot)
    write_strings(strings)
    if args.preview:
        preview()


if __name__ == "__main__":
    sys.exit(main())
