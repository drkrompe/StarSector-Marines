"""Rough mixes of the arrival's background sound against the picture sketches (sfx-cues.md).

  python art-source/company-prologue/second-wind-cinematic/arrival-audio/sfx_mix.py [--shot ID ...]
         [--picture ID=PATH ...] [--castle DIR] [--hail DIR] [--out DIR]

For each shot: the chosen take of every cue (sfx-manifest.json) placed at its cue-sheet time
and level, Castle's and Approach's lines as the shipped and review takes, and the picture
sketch, muxed into <out>/<shot>.mp4, with the sound alone as <shot>.mp3 and the effects without
voices as <shot>-sfx-only.mp3. Nothing is generated or paid for here.

Levels are dB against the dialogue, which sits at DIALOGUE_LUFS; each take is gain-matched by
its measured integrated loudness first. A layer's `env` is a list of (shot seconds, dB) points
on top of its level, interpolated in dB. Requires ffmpeg/ffprobe on PATH.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path

import numpy as np

import synth_flyby

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
MANIFEST = HERE / "sfx-manifest.json"
TAKES = HERE / "sfx-takes"
OUT = ROOT / "build" / "audio-previews" / "cinematic-sfx"
SR = 48000
DIALOGUE_LUFS = -19.0

# The shipped hail and squelch, encoded by :asset-pipeline:processAudio (ignored output, so a
# fresh worktree may need --hail pointing at a checkout that has built it).
HAIL = ROOT / "mod/sounds/story/jangala-arrival"
# Castle's review takes (record_castle_lines.py treat): the owner's "a" takes from the review set.
CASTLE = {"entry": "entry_t06", "c4_reels": "c4_reels_t06", "c4_greywater": "c4_greywater_t01",
          "c5_announce": "c5v3_announce_t01", "c5_last": "c5v3_last_t01"}
LOW = "lowpass=f=220:p=2"   # cues felt through the hull stay under the voice band

# Picture sketches: the newest review video of each shot, as of the cue sheet's date.
DRIVE = Path("G:/My Drive/starsector/review")
PICTURES = {
    "c1-hound-b": DRIVE / "cinematic-c1/2026-10-18_picket-inbound-fighters_windows/01-c1v2-1fps-with-hail.mp4",
    "c1": DRIVE / "cinematic-c1/2026-10-18_picket-inbound-fighters_windows/01-c1v2-1fps-with-hail.mp4",
    "c2": DRIVE / "cinematic-c2/2026-10-18_bridge-fighters_windows/01-c2-bridge-sketch.mp4",
    "entry": ROOT / "mod/cinematic/jangala-arrival/entry.mp4",
    "c3": ROOT / "mod/cinematic/jangala-arrival/c3.mp4",
    # C4 and C5 are one shot now; until it is rendered as one, the two sketches play back to back.
    "c4c5": [ROOT / "build/c4x/c4x-sketch-1fps.mp4",
             DRIVE / "cinematic-c5/2026-10-19_rise-title_windows/05-sketch-1fps.mp4"],
}


def sfx(cue, t, level, **kw):
    return dict(kind="sfx", cue=cue, t=t, level=level, **kw)


def synth(name, level, **kw):
    """A code-built pass from synth_flyby.py; its clip already sits on the shot clock."""
    return dict(kind="sfx", cue=None, synth=name, t=synth_flyby.PASSES[name]["start"], level=level, **kw)


def voice(src, t, **kw):
    return dict(kind="voice", src=src, t=t, level=0.0, **kw)


def hail(n, t):
    return voice(("hail", "jangala_arrival_hail_%02d" % n), t)


def squelch(kind, t):
    return dict(kind="voice", src=("hail", "jangala_squelch_" + kind), t=t, level=0.0, match=False)


# Each layer: start t (shot seconds), level (dB against dialogue), and optional
# until (loop or cut to this shot time), offset (seconds into the take), env, fade_in, fade_out, af.
def shifted(layers, by):
    """The same layers later on a joined shot's clock."""
    out = []
    for layer in layers:
        moved = dict(layer)
        for key in ("t", "until", "ends"):
            if moved.get(key) is not None:
                moved[key] += by
        if moved.get("env"):
            moved["env"] = [(t + by, db) for t, db in moved["env"]]
        out.append(moved)
    return out


C4_LENGTH = 16.0   # C4 and C5 play as one shot (owner, 2026-10-19); C5's beats keep their own clock + 16

SHOTS = {
    "c1": (37.7, [
        sfx("c1_cabin_bed", 0.0, -16, until=37.7, env=[(0, -2), (2.6, 6), (3.1, 0)], fade_in=1.0),
        # Second Wind's drive as bass: far at first, loudest as it passes and its engines face us.
        sfx("c1_sw_engine_loop", 0.0, -14, until=37.7, af=LOW,
            env=[(0, -12), (8, -2), (11, 2), (16, 0), (24, -4), (37.7, -9)], fade_in=2.0),
        sfx("c1_sw_passby_swish", 9.25, -10),
        sfx("c1_hull_creaks", 0.5, -12, offset=0.5, until=3.0),
        sfx("c1_hull_creaks", 32.2, -12, offset=0.5, until=34.5),
        sfx("c1_rcs_puffs", 5.0, -14, until=7.0),
        sfx("c1_sparks", 6.0, -22, until=10.0, env=[(6, -6), (8, 0), (10, -10)]),
        # The customs Hound: synthesised from its flight path (owner, 2026-10-19).
        synth("hound_flyby_a", -8),
        squelch("open", 3.0), hail(1, 3.16), squelch("close", 11.8), hail(2, 12.62),
        squelch("open", 23.7), hail(3, 23.86), squelch("close", 31.7), hail(4, 32.52),
    ]),
    "c2": (15.0, [
        sfx("c1_cabin_bed", 0.0, -16, offset=37.7 % 12.0, until=1.0, fade_out=1.0),
        sfx("c2_bridge_bed", 0.0, -16, until=15.0, fade_in=1.0),
        sfx("c2_console_beeps", 0.0, -14, offset=2.0, until=0.9),
        sfx("c2_console_beeps", 2.0, -14, offset=3.4, until=3.4),
        # Two vics of three: a swish per fighter, a beat apart, peaking at 3.0 and 4.2.
        sfx("c2_fighter_swish", 2.5, -8), sfx("c2_fighter_swish", 2.62, -12), sfx("c2_fighter_swish", 2.74, -14),
        sfx("c2_fighter_swish", 3.7, -10), sfx("c2_fighter_swish", 3.82, -13), sfx("c2_fighter_swish", 3.94, -15),
        # The Legion: two layers of engine hum and the swell, all loudest at the beam (8.0).
        sfx("c2_legion_engines_a", 0.0, -12, until=15.0, af=LOW,
            env=[(0, -16), (8, 0), (12, -6), (15, -12)], fade_in=1.0),
        sfx("c2_legion_engines_b", 0.0, -13, until=15.0, af=LOW,
            env=[(0, -18), (8.5, 0), (13, -6), (15, -12)], fade_in=1.0),
        sfx("c2_legion_presence", 5.0, -14, af=LOW),
        sfx("c2_switch_clicks", 9.45, -12, until=9.8),
        sfx("c2_switch_clicks", 14.35, -12, offset=3.0, until=14.8),
        sfx("c2_deorbit_burn", 8.1, -12, until=15.0, env=[(8.1, 0), (12, 2), (15, 6)]),
        sfx("c1_hull_creaks", 10.8, -14, offset=5.0, until=12.3),
        squelch("open", 0.3), hail(7, 0.46), squelch("close", 8.94), hail(8, 9.76),
    ]),
    "entry": (3.0, [
        sfx("c2_deorbit_burn", 0.0, -8, offset=6.9, until=1.0, fade_out=0.9),
        sfx("entry_reentry_roar", 0.0, -10, env=[(0, -4), (2.6, 0), (3.0, 8)]),
        sfx("entry_buffet", 1.3, -10, until=3.0, fade_in=0.4),
        voice(("castle", CASTLE["entry"]), 0.16),
    ]),
    "c3": (12.0, [
        sfx("entry_reentry_roar", 0.0, -2, offset=3.0, until=1.5, fade_out=1.4),
        sfx("entry_buffet", 0.0, -10, offset=1.7, until=2.0, env=[(0, 0), (2.0, -18)]),
        sfx("c3_wind_easing", 0.0, -6, until=12.0, env=[(0, 0), (0.9, -4), (2.7, -8), (5, -10), (12, -12)]),
        sfx("c3_frame_settle", 1.0, -14),
        sfx("c3_cabin_flight_bed", 0.0, -16, until=12.0, fade_in=0.8),
        # The world below, through the glass: muffled, and in once the buffeting has gone.
        sfx("c3_nature_muffled", 1.5, -16, until=12.0, af="lowpass=f=1800:p=2", fade_in=2.0),
        sfx("c3_river_muffled", 1.5, -18, until=12.0, af="lowpass=f=1200:p=2", fade_in=2.5),
        voice(("castle", CASTLE["c4_reels"]), 4.0),
    ]),
    # C4 and C5 joined: the sky pan, the landing and the yard, one camera.
    "c4c5": (C4_LENGTH + 33.5, [
        sfx("c3_cabin_flight_bed", 0.0, -16, until=1.0, fade_out=1.0),
        sfx("c4_high_wind_bed", 0.0, -14, until=15.0, env=[(0, 0), (8, 0), (11, -6), (15, -14)], fade_in=0.6),
        sfx("c4_ground_distant", 0.0, -18, until=6.0, fade_out=2.0),
        sfx("c4_freeway", 1.0, -12, until=48.5, fade_in=1.0,
            env=[(1, -6), (4, 0), (8, -4), (12, -10), (34, -10), (42, -2), (48.5, -30)]),
        sfx("c4_port_heavy", 3.5, -12, until=48.5, fade_in=1.5,
            env=[(3.5, -10), (7, 0), (16, -2), (20, -6), (30, -6), (34, -5), (42, 2), (48.5, -30)]),
        sfx("c4_port_ambience", 5.0, -18, until=34.0, fade_in=2.5, fade_out=2.0),
        # The landing: the drive as bass, the rocket burn under the hover, then the thump.
        sfx("c1_sw_engine_loop", 3.0, -10, until=12.5, af=LOW,
            env=[(3, -8), (6.6, 0), (10.5, 3), (12.5, -6)], fade_in=1.5, fade_out=1.0),
        sfx("c4_landing_rocket", 4.0, -6),
        sfx("c4_ship_hover", 6.0, -14, until=11.3, loop=True, fade_in=1.0, fade_out=0.3),
        sfx("c4_touchdown", 11.0, -2),
        sfx("c4_dust_hiss", 11.0, -10),
        sfx("c4_spool_down", 11.2, -8),
        sfx("c5_vehicles_arrive", 14.2, -12, fade_in=0.5),
        sfx("c5_yard_bed", 12.0, -14, until=49.5,
            env=[(12, 0), (34, 0), (42, -8), (47, -30)], fade_in=2.0),
        voice(("castle", CASTLE["c4_greywater"]), 1.0),
    ] + shifted([
        sfx("c5_hull_ticks", 0.5, -14, until=14.0, loop=True, fade_out=1.5),
        sfx("c5_car_doors", 3.5, -12),
        sfx("c5_steam_vent", 4.0, -14, until=6.5),
        sfx("c5_steam_vent", 6.4, -16, until=8.6),
        sfx("c5_pa_click", 4.75, -10, until=5.2),
        sfx("c5_pa_click", 13.8, -10, until=14.25),
        sfx("c5_ramp_drop", 5.6, -8),
        sfx("c5_footsteps_crowd", 9.0, -14, until=27.0, env=[(9, -6), (11, 0), (18, 0), (26, -10)],
            fade_out=1.5),
        sfx("c5_crowd_murmur", 10.0, -20, until=25.0, fade_in=1.5, fade_out=2.0),
        sfx("c5_port_wide", 17.0, -5, offset=5.0, until=33.5,
            env=[(17, -12), (26, 0), (28, 0), (32.5, -40)], fade_in=1.0),
        voice(("castle", CASTLE["c5_announce"]), 5.0),
        voice(("castle", CASTLE["c5_last"]), None, ends=24.0),
    ], C4_LENGTH)),
}


SHOTS["c1-hound-b"] = (SHOTS["c1"][0], [dict(layer, synth="hound_flyby_b") if layer.get("synth") else layer
                                         for layer in SHOTS["c1"][1]])

def decode(path, af=None):
    cmd = ["ffmpeg", "-v", "error", "-i", str(path)]
    if af:
        cmd += ["-af", af]
    raw = subprocess.run(cmd + ["-ac", "2", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 2).astype(np.float64)


def loudness(path):
    err = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path), "-af", "ebur128",
                          "-f", "null", "-"], capture_output=True, text=True).stderr
    return float(re.findall(r"I:\s+(-?[\d.]+) LUFS", err)[-1])


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "default=nw=1:nk=1", str(path)], check=True, capture_output=True, text=True)
    return float(out.stdout.strip())


def source(layer, dirs, chosen):
    if layer.get("synth"):
        return dirs["synth"] / (layer["synth"] + ".wav")
    if layer["kind"] == "sfx":
        return TAKES / (chosen[layer["cue"]] + ".mp3")
    family, name = layer["src"]
    if family == "hail":
        return dirs["hail"] / (name + ".ogg")
    return dirs["castle"] / (name + "_treated.wav")


def place(layer, path, length):
    """The layer's samples on the shot timeline, gain and envelope applied."""
    clip = decode(path, layer.get("af"))
    if layer.get("match", True):
        clip = clip * 10 ** ((DIALOGUE_LUFS + layer["level"] - loudness(path)) / 20)
    start = layer["t"] if layer["t"] is not None else layer["ends"] - len(clip) / SR
    clip = clip[int(layer.get("offset", 0.0) * SR):]
    until = layer.get("until")
    if until is not None:
        want = int(round((until - start) * SR))
        if want > len(clip) and (layer.get("loop") or layer["kind"] == "sfx" and layer["cue"] in BEDS):
            clip = np.concatenate([clip] * (want // len(clip) + 1))
        clip = clip[:want]
    n = len(clip)
    times = start + np.arange(n) / SR
    gain = np.ones(n)
    for edge, seconds in (("in", layer.get("fade_in", 0.02)), ("out", layer.get("fade_out", 0.05))):
        k = min(n, int(seconds * SR))
        if k:
            ramp = np.linspace(0, 1, k)
            gain[:k] *= ramp if edge == "in" else 1
            gain[n - k:] *= ramp[::-1] if edge == "out" else 1
    if layer.get("env"):
        pts = np.array(layer["env"], float)
        gain *= 10 ** (np.interp(times, pts[:, 0], pts[:, 1]) / 20)
    clip = clip * gain[:, None]
    out = np.zeros((int(round(length * SR)), 2))
    a = int(round(start * SR))
    lo, hi = max(a, 0), min(a + n, len(out))
    if hi > lo:
        out[lo:hi] = clip[lo - a:hi - a]
    return out


def write(samples, path, extra=()):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    *extra, str(path)], input=samples.astype(np.float32).tobytes(), check=True)


LIMIT = ["-af", "alimiter=limit=0.89:level=false"]


def mix(shot, picture, dirs, chosen, out):
    length, layers = SHOTS[shot]
    full = np.zeros((int(round(length * SR)), 2))
    effects = full.copy()
    for layer in layers:
        placed = place(layer, source(layer, dirs, chosen), length)
        full += placed
        if layer["kind"] == "sfx":
            effects += placed
    write(full, out / (shot + ".mp3"), [*LIMIT, "-c:a", "libmp3lame", "-q:a", "2"])
    write(effects, out / (shot + "-sfx-only.mp3"), [*LIMIT, "-c:a", "libmp3lame", "-q:a", "2"])
    video = out / (shot + ".mp4")
    parts = picture if isinstance(picture, list) else [picture] if picture else []
    if parts and all(part.exists() for part in parts):
        inputs = [arg for part in parts for arg in ("-i", str(part))]
        graph = "".join("[%d:v]fps=24,scale=1920:1080,setsar=1[v%d];" % (i, i) for i in range(len(parts)))
        graph += "".join("[v%d]" % i for i in range(len(parts))) + "concat=n=%d:v=1:a=0[v]" % len(parts)
        subprocess.run(["ffmpeg", "-v", "error", "-y", *inputs, "-i", str(out / (shot + ".mp3")),
                        "-filter_complex", graph, "-map", "[v]", "-map", "%d:a" % len(parts), "-c:v", "libx264",
                        "-crf", "20", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
                        "-t", "%.3f" % length, str(video)], check=True)
    peak = 20 * np.log10(np.abs(full).max() + 1e-12)
    print("%-6s %5.1fs  mix peak %5.1f dBFS before limiter  %s" % (
        shot, length, peak, video.name if video.exists() else "(no picture)"), flush=True)


BEDS = set()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--shot", action="append", choices=list(SHOTS))
    parser.add_argument("--picture", action="append", default=[], help="ID=PATH, overriding PICTURES")
    parser.add_argument("--castle", default=str(ROOT / "build/audio-previews/castle-lines"),
                        help="folder holding Castle's *_treated.wav review takes")
    parser.add_argument("--hail", default=str(HAIL), help="folder holding the encoded hail .ogg files")
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    chosen = {cue: row["chosen"] for cue, row in manifest["cues"].items() if row["chosen"]}
    BEDS.update(cue for cue, row in manifest["cues"].items() if row["loop"])
    pictures = dict(PICTURES)
    for item in args.picture:
        shot, path = item.split("=", 1)
        pictures[shot] = [Path(part) for part in path.split(",")] if "," in path else Path(path)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    synth_flyby.build(out / "synth")
    for shot in args.shot or SHOTS:
        mix(shot, pictures.get(shot), {"castle": Path(args.castle), "hail": Path(args.hail), "synth": out / "synth"}, chosen, out)


if __name__ == "__main__":
    main()
