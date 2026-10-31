"""The arrival's sound mix as data: every level in one file, applied to the timelines, and heard
without the game.

`arrival-audio/arrival-mix.json` holds every cue's level for every shot plus the music cues. A
shot's cues are its composer's (the voices and squelches) and the cue sheet's effects, which
`arrival-audio/build_arrival_stems.py` renders one clip per layer and lists in arrival-stems.json;
each clip's starting gain_db there is the sheet's level, and it plays as a one-shot.
`cinematic_export.py` applies it whenever it writes a timeline, so a level change is an edit there
and `cinematic_export.py --timeline-only` (no frames, no encode), and `--preview` mixes the shipped
timelines the way the player plays them onto the shipped video to hear it.

  shots.<shot>.<key>   one cue the shot's composer places. <key> is its sound id, with "#2", "#3"
                       for the second and third cue of the same sound in that shot, in time order.
                       gain_db      its level: the timeline volume is 10^(gain_db/20)
                       fade_in, fade_out   seconds; only a looped cue (one with `until`) ramps
                       duck_db      optional; dB the cue drops by while anyone speaks
  music[]              a music cue the mix places itself:
                       shot, sound, t      where it starts, on that shot's clock
                       until_end_of        the shot it plays to the end of (it may cross shots)
                       level_db     on the Arrival Score Desk's scale: 0 is the take loudness-matched
                                    to dialogue + reference.score_level_db, as the desk plays it
                       source_lufs  the encoded take's integrated loudness, which that scale is
                                    measured against (checked against the ogg when it is built)
                       fade_in, fade_out, duck_db   as above

The game plays sounds at volume 0..1, so a level that works out above 1 is refused rather than
clipped. Times stay with the composers; the mix only moves levels and the music.
"""
import json
import math
import re
import subprocess
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
MIX = HERE / "arrival-audio" / "arrival-mix.json"
STEMS = HERE / "arrival-audio" / "arrival-stems.json"   # the cue sheet's effects (build_arrival_stems.py)
MOD = ROOT / "mod"
SOUNDS = MOD / "data" / "config" / "sounds.json"
TIMELINE_DIR = MOD / "data" / "cinematic" / "jangala-arrival"
VIDEO_DIR = MOD / "cinematic" / "jangala-arrival"
PREVIEW_DIR = ROOT / "build" / "cinematic" / "preview"
SR = 44100
DUCK_EASE = 0.4      # keep equal to CinematicSequence.DUCK_EASE


def load():
    return json.loads(MIX.read_text(encoding="utf-8"))


def keyed(cues):
    """[(key, cue)] in time order: the sound id, with #n for its n-th cue in the shot (n >= 2)."""
    seen, out = {}, []
    for cue in sorted(cues, key=lambda c: (c["t"], c["sound"])):
        n = seen[cue["sound"]] = seen.get(cue["sound"], 0) + 1
        out.append((cue["sound"] if n == 1 else "%s#%d" % (cue["sound"], n), cue))
    return out


def db_volume(db):
    return round(10 ** (db / 20), 4)


def _levels(cue, entry):
    if "gain_db" in entry:
        cue["volume"] = db_volume(entry["gain_db"])
    for k in ("fade_in", "fade_out"):
        if k in entry:
            cue[k] = entry[k]
    if "duck_db" in entry:
        cue["duck"] = db_volume(entry["duck_db"])


def stems(shot_id):
    """The cue sheet's effects in this shot: one-shot clips at their sheet times and levels."""
    rows = json.loads(STEMS.read_text(encoding="utf-8"))["stems"]
    return [{"t": r["t"], "sound": r["sound"], "volume": db_volume(r["gain_db"])} for r in rows if r["shot"] == shot_id]


def apply(shot_id, cues, shot_ids, duration_of, mix=None):
    """The shot's cues (the composer's voices and the cue sheet's effects) with the mix's levels on
    them, plus the music cues that start in it. Returns (cues, problems)."""
    mix = mix or load()
    cues = list(cues) + stems(shot_id)
    levels = mix.get("shots", {}).get(shot_id, {})
    problems, out = [], []
    keys = set()
    for key, cue in keyed(cues):
        keys.add(key)
        cue = dict(cue)
        if key in levels:
            _levels(cue, levels[key])
        else:
            problems.append("cue %r has no level in %s" % (key, MIX.name))
        out.append(cue)
    for key in levels:
        if key not in keys:
            problems.append("%s sets %r, which the shot does not play" % (MIX.name, key))
    ref = mix["reference"]
    for m in mix.get("music", []):
        if m["shot"] != shot_id:
            continue
        last = shot_ids.index(m["until_end_of"])
        until = sum(duration_of(s) for s in shot_ids[shot_ids.index(shot_id):last + 1])
        db = ref["dialogue_lufs"] + ref["score_level_db"] + m["level_db"] - m["source_lufs"]
        cue = {"t": m["t"], "sound": m["sound"], "volume": db_volume(db), "until": round(until, 4),
               "fade_in": m.get("fade_in", 0.0), "fade_out": m.get("fade_out", 0.0)}
        if "duck_db" in m:
            cue["duck"] = db_volume(m["duck_db"])
        if cue["volume"] > 1:
            problems.append("music %r at level_db %+.1f needs volume %.2f, above the game's 1.0"
                            % (m["sound"], m["level_db"], cue["volume"]))
        out.append(cue)
    for cue in out:
        if cue["volume"] > 1:
            problems.append("cue %r volume %.2f is above the game's 1.0" % (cue["sound"], cue["volume"]))
    return sorted(out, key=lambda c: (c["t"], c["sound"])), problems


# ------------------------------------------------------------------ hearing it

def sound_files():
    """sound id -> (file under mod/, sounds.json volume) for every sound the player can cue."""
    lines = SOUNDS.read_text(encoding="utf-8").splitlines()
    text = "\n".join(l for l in lines if not l.lstrip().startswith("#"))
    out = {}
    for sid, body in re.findall(r'"([^"]+)"\s*:\s*\[(\{[^\]]*\})\]', text):
        m = re.search(r'"file"\s*:\s*"([^"]+)"', body)
        v = re.search(r'"volume"\s*:\s*([\d.]+)', body)
        if m:
            out[sid] = (MOD / m.group(1), float(v.group(1)) if v else 1.0)
    return out


def loudness(path):
    err = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path), "-af", "ebur128", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    return float(re.findall(r"I:\s+(-?[\d.]+) LUFS", err)[-1])


def check_music(mix=None):
    """Problems where a music take's recorded source_lufs no longer matches its encoded ogg."""
    files, problems = sound_files(), []
    for m in (mix or load()).get("music", []):
        path = files.get(m["sound"], (None,))[0]
        if path is None:
            problems.append("music %r is not in sounds.json" % m["sound"])
        elif path.is_file():
            measured = loudness(path)
            if abs(measured - m["source_lufs"]) > 0.5:
                problems.append("music %r measures %.1f LUFS, the mix says %.1f: update source_lufs"
                                % (m["sound"], measured, m["source_lufs"]))
    return problems


def _decode(path, cache):
    if path not in cache:
        raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "2", "-ar", str(SR), "-f", "s16le", "-"],
                             capture_output=True, check=True).stdout
        cache[path] = np.frombuffer(raw, "<i2").reshape(-1, 2).astype(np.float32) / 32768
    return cache[path]


def sequence_mix(shot_ids):
    """The shipped timelines mixed as the player plays them: (float stereo at SR from the first
    shot's start, unclipped; each shot's start in seconds; the timelines).

    One-shots play once from their start; a cue with `until` loops to it with its fades, and its
    duck follows every caption on the sequence clock (CinematicSequence.volumeAt)."""
    timelines = [json.loads((TIMELINE_DIR / (s + ".json")).read_text(encoding="utf-8")) for s in shot_ids]
    offsets, at = [], 0.0
    for tl in timelines:
        offsets.append(at)
        at += tl["duration"]
    n = int(math.ceil(at * SR))
    mix = np.zeros((n, 2), np.float32)
    t = np.arange(n) / SR
    speaking = np.zeros(n, np.float32)
    for off, tl in zip(offsets, timelines):
        for c in tl["captions"]:
            a, b = off + c["t_in"], off + c["t_out"]
            ramp = np.where(t < a, 1 - (a - t) / DUCK_EASE, np.where(t > b, 1 - (t - b) / DUCK_EASE, 1.0))
            speaking = np.maximum(speaking, np.clip(ramp, 0, 1))
    files, cache = sound_files(), {}
    for off, tl in zip(offsets, timelines):
        for c in tl["cues"]:
            path, base = files[c["sound"]]
            x = _decode(path, cache)
            start = int(round((off + c["t"]) * SR))
            if start >= n or not len(x):
                continue
            if "until" not in c:
                e = min(n, start + len(x))
                mix[start:e] += x[: e - start] * c["volume"] * base
                continue
            end = min(n, int(round((off + c["until"]) * SR)))
            if end <= start:
                continue
            span = end - start
            body = np.tile(x, (span // len(x) + 1, 1))[:span]
            tt = np.arange(span) / SR
            g = np.ones(span, np.float32) * c["volume"] * base
            if c.get("fade_in", 0) > 0:
                g *= np.minimum(1, tt / c["fade_in"])
            if c.get("fade_out", 0) > 0:
                g *= np.minimum(1, (span / SR - tt) / c["fade_out"])
            if c.get("duck", 1.0) < 1:
                g *= 1 - (1 - c["duck"]) * speaking[start:end]
            mix[start:end] += body * g[:, None]
    return mix, offsets, timelines


def preview(shot_ids, only=None, out=None):
    """Mix the shipped timelines as the player plays them (sequence_mix) and mux the sound onto the
    shipped video. `only` keeps the picture to those shots, while cues from earlier shots that carry
    into them are still heard. Writes build/cinematic/preview/<name>.mp4 and returns its path."""
    mix, offsets, timelines = sequence_mix(shot_ids)
    picked =[i for i, s in enumerate(shot_ids) if not only or s in only]
    a = int(round(offsets[picked[0]] * SR))
    b = int(round((offsets[picked[-1]] + timelines[picked[-1]]["duration"]) * SR))
    peak = float(np.abs(mix[a:b]).max()) if b > a else 0.0
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    name = "arrival" if not only else "-".join(shot_ids[i] for i in picked)
    wav = PREVIEW_DIR / (name + ".wav")
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(mix[a:b], -1, 1) * 32767).astype("<i2").tobytes())
    listing = PREVIEW_DIR / (name + ".txt")
    listing.write_text("".join("file '%s'\n" % (VIDEO_DIR / (shot_ids[i] + ".mp4")).as_posix() for i in picked),
                       encoding="utf-8")
    target = Path(out) if out else PREVIEW_DIR / (name + ".mp4")
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(listing),
                    "-i", str(wav), "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                    "-shortest", "-movflags", "+faststart", str(target)], check=True)
    print("preview %s: %.1fs, peak %.2f%s" % (target, (b - a) / SR, peak, " (above 1.0: this preview clips there)" if peak > 1 else ""))
    return target
