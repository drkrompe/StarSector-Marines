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
import synth_landing

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
          "c4_kilo_nine": "c4_kilo_nine_t01",
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
    "c4c5": DRIVE / "cinematic-c4c5/2026-10-19_slower-approach_windows/01-c4c5-slower-approach-1fps.mp4",
}


def sfx(cue, t, level, **kw):
    return dict(kind="sfx", cue=cue, t=t, level=level, **kw)


def synth(name, level, **kw):
    """A code-built pass from synth_flyby.py; its clip already sits on the shot clock."""
    return dict(kind="sfx", cue=None, synth=name, t=synth_flyby.PASSES[name]["start"], level=level, **kw)


def landing(stem, level, **kw):
    """A synth_landing.py stem; its clip starts at synth_landing.START on C4's clock."""
    return dict(kind="sfx", cue=None, synth=stem, t=synth_landing.START, level=level, **kw)


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


C4_LENGTH = 21.4   # C4 and C5 play as one shot (owner, 2026-10-19); C5's beats keep their own clock + 21.4

SHOTS = {
    "c1": (37.7, [
        sfx("c1_cabin_bed", 0.0, -16, until=37.7, env=[(0, -2), (2.6, 6), (3.1, 0)], fade_in=1.0),
        # Second Wind's drive as bass: far at first, loudest as it passes and its engines face us.
        # Second Wind's drive as a muffled heavy rocket, synthesised from its lane (owner, 2026-10-19).
        synth("second_wind_pass", -10, fade_in=2.0),
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
        synth("fighter_00", -6), synth("fighter_01", -9), synth("fighter_02", -9),
        synth("fighter_10", -8), synth("fighter_11", -11), synth("fighter_12", -11),
        # The Legion: two layers of engine hum and the swell, all loudest at the beam (8.0).
        # The Legion's engines: two synthesised rocket clusters along the hull, a beat apart.
        synth("legion_drives", -7, fade_in=1.0),
        synth("legion_forward", -9, fade_in=1.0),
        sfx("c2_switch_clicks", 9.45, -12, until=9.8),
        sfx("c2_switch_clicks", 14.35, -12, offset=3.0, until=14.8),
        # Second Wind's own drive heard from its bridge, lighting for the de-orbit burn at 9.6 s.
        synth("second_wind_interior", -8, until=15.0),
        sfx("c1_hull_creaks", 10.8, -14, offset=5.0, until=12.3),
        squelch("open", 0.3), hail(7, 0.46), squelch("close", 8.94), hail(8, 9.76),
    ]),
    # The entry, 7 s (c2_core HEAT/SMOKE/CLOUD_IN/WHITE/BLOOM, 2026-10-19): clear space to 1.2,
    # heat from the bottom right filling the glass by 2.4, smoke 1.6-5.6, cloud 4.1-5.4, white
    # 5.4-6.0, bloom to the cut. The shake (c3_core.shake_envelope) is under a pixel to 0.6 s,
    # peaks at about 9 px from 4.0 to 5.2 s and decays; the buffet follows it in dB.
    "entry": (7.0, [
        synth("second_wind_interior", -8, offset=15.0, until=7.0, env=[(0, 0), (5.4, 0), (7.0, -4)]),
        sfx("entry_reentry_roar", 1.2, -3, env=[(1.2, -10), (3.0, -4), (4.0, 0), (5.2, 0), (7.0, -2)]),
        sfx("entry_buffet", 0.6, -4, until=7.0, loop=True, fade_in=0.3,
            env=[(0.6, -24), (2.4, -10), (4.0, 0), (5.2, 0), (6.0, -5), (7.0, -9.5)]),
        sfx("c1_hull_creaks", 3.6, -10, offset=5.0, until=5.1),
        voice(("castle", CASTLE["entry"]), 0.16),
    ]),
    # C3, 16 s (c3_core): still shaking at the start (about 3 px, 1 px by 2 s, gone by 5 s), the
    # horizon levelling over 0.4-5.6 s with a settle at 5-7 s; Castle's travel-reels line at 7.0.
    "c3": (16.0, [
        synth("second_wind_interior", -10, offset=22.0, until=2.0, fade_out=2.0),
        sfx("entry_reentry_roar", 0.0, -6, offset=5.8, until=1.5, fade_out=1.0),
        sfx("entry_buffet", 0.0, -8, until=5.5, loop=True, env=[(0, -9.5), (2.0, -19), (5.0, -36)]),
        sfx("c3_wind_easing", 0.0, -6, until=16.0, loop=True,
            env=[(0, 0), (2.0, -3), (5.5, -9), (8, -11), (16, -12)]),
        sfx("c3_frame_settle", 5.6, -14),
        sfx("c3_cabin_flight_bed", 0.0, -16, until=16.0, fade_in=0.8),
        # The world below, through the glass: muffled, and in once the shaking has gone.
        sfx("c3_nature_muffled", 4.0, -16, until=16.0, af="lowpass=f=1800:p=2", fade_in=2.5),
        sfx("c3_river_muffled", 4.0, -18, until=16.0, af="lowpass=f=1200:p=2", fade_in=3.0),
        voice(("castle", CASTLE["c4_reels"]), 7.0),
    ]),
    # C4 and C5 joined: the sky pan, the landing and the yard, one camera.
    "c4c5": (C4_LENGTH + 33.5, [
        sfx("c3_cabin_flight_bed", 0.0, -16, until=1.0, fade_out=1.0),
        sfx("c4_high_wind_bed", 0.0, -14, until=20.4, loop=True,
            env=[(0, 0), (13.4, 0), (16.4, -6), (20.4, -14)], fade_in=0.6),
        sfx("c4_ground_distant", 0.0, -18, until=11.0, loop=True, fade_out=2.0),
        sfx("c4_freeway", 1.0, -12, until=53.9, fade_in=1.0,
            env=[(1, -6), (4, 0), (13.4, -4), (17.4, -10), (39.4, -10), (47.4, -2), (53.9, -30)]),
        sfx("c4_port_heavy", 3.5, -12, until=53.9, fade_in=1.5,
            env=[(3.5, -10), (7, 0), (21.4, -2), (25.4, -6), (35.4, -6), (39.4, -5), (47.4, 2), (53.9, -30)]),
        sfx("c4_port_ambience", 5.0, -18, until=39.4, fade_in=2.5, fade_out=2.0),
        # The landing (c4x_scene, 2026-10-19): the approach runs under both of Castle's lines,
        # the hover starts at 12.0 once Kilo Nine (4.6-10.9) is done, touchdown at 16.4 and the
        # drive spools down to 17.9. The drive is bass the whole way and opens up for the burn.
        # Owner, 2026-10-19: burning-gas thrusters, heavy lift; no hum, no chimes. Second Wind's
        # main drive is synthesised in the C1-C2 rocket voice (the generated bass loop was a 76 Hz
        # hum); the landing itself is synth_landing.py's broadband rebuild: the lift jets' roar with
        # its ignition whump at 10.0, grit, the touchdown, the burner shutdown and the settling dust.
        # The roar carries its own low end, so the drive drops 4 dB under it from 12.0.
        synth("second_wind_c4_drive", -12, env=[(0, -4), (10.9, -4), (12.0, -4), (16.4, -4)]),
        landing("landing_roar", -5, env=[(9.5, -6), (10.9, -6), (11.6, 0)]),
        landing("landing_grit", -11),
        landing("landing_thump", -2),
        landing("landing_spool", -8),
        landing("landing_settle", -14),
        sfx("c5_yard_bed", 17.4, -14, until=54.9,
            env=[(17.4, 0), (39.4, 0), (47.4, -8), (52.4, -30)], fade_in=2.0),
        # A second Colossus leaving low past Kilo Nine (port_fleet 'Colossus outbound'), closest at
        # C5 18-19.5, then climbing away under the title; kept under the music and the title.
        synth("colossus_outbound", -10, env=[(C4_LENGTH + 24.0, 0), (C4_LENGTH + 26.0, -4)]),
        voice(("castle", CASTLE["c4_greywater"]), 0.5),
        voice(("castle", CASTLE["c4_kilo_nine"]), 4.6),
    ] + shifted([
        sfx("c5_hull_ticks", 0.5, -14, until=14.0, loop=True, fade_out=1.5),
        # The yard's vehicles roll in from C4 and stop one by one (c5_scene, 2026-10-19): the tug at
        # 2.5, the crew carrier at 3.5, the bowser at 4.5, the customs car at 5.0. The take's brake
        # hiss sits 4 s in, so each starts 4 s before its stop; a door 0.6 s after each stop.
        sfx("c5_vehicles_arrive", -1.5, -14, fade_in=0.8),
        sfx("c5_vehicles_arrive", -0.5, -15, offset=0.3, fade_in=0.8),
        sfx("c5_vehicles_arrive", 0.5, -16, offset=0.6, fade_in=0.8),
        sfx("c5_vehicles_arrive", 1.0, -18, offset=0.9, fade_in=0.8),
        sfx("c5_car_doors", 3.1, -16, until=3.7),
        sfx("c5_car_doors", 4.1, -16, until=4.7),
        sfx("c5_car_doors", 5.1, -16, until=5.7),
        sfx("c5_car_doors", 5.6, -14),
        sfx("c5_steam_vent", 4.0, -14, until=6.5),
        sfx("c5_steam_vent", 6.4, -16, until=8.6),
        sfx("c5_pa_click", 4.75, -10, until=5.2),
        sfx("c5_pa_click", 13.8, -10, until=14.25),
        sfx("c5_ramp_drop", 5.6, -8),
        # From 220 m up the families are a far murmur, not footsteps (owner, 2026-10-19).
        sfx("c5_crowd_murmur", 8.5, -24, until=25.0, af="highpass=f=150,lowpass=f=900:p=2",
            fade_in=2.0, fade_out=3.0),
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
    if layer.get("path"):
        return layer["path"]
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


def music(path, peak_at):
    """A closing music candidate (make_music.py) on the joined C4-C5 clock, its loudest second laid
    at peak_at and ducked under Castle's last line (C5 21.7-24.0)."""
    end = C4_LENGTH + 24.0
    return dict(kind="music", path=Path(path), t=peak_at - loudest(Path(path)), level=0.0,
                until=SHOTS["c4c5"][0], fade_out=2.0,
                env=[(end - 2.6, 0), (end - 2.3, -8), (end, -8), (end + 0.6, 0)])


def loudest(path):
    """Seconds into a take of its loudest second."""
    clip = decode(path).mean(axis=1)
    per = [float(np.sqrt(np.mean(clip[i:i + SR] ** 2))) for i in range(0, len(clip) - SR + 1, SR // 4)]
    return per.index(max(per)) * 0.25 + 0.5


def mix(shot, picture, dirs, chosen, out, extra=(), tag=None):
    length, layers = SHOTS[shot]
    layers = layers + list(extra)
    name = shot + ("-" + tag if tag else "")
    full = np.zeros((int(round(length * SR)), 2))
    effects = full.copy()
    for layer in layers:
        placed = place(layer, source(layer, dirs, chosen), length)
        full += placed
        if layer["kind"] == "sfx":
            effects += placed
    write(full, out / (name + ".mp3"), [*LIMIT, "-c:a", "libmp3lame", "-q:a", "2"])
    write(effects, out / (name + "-sfx-only.mp3"), [*LIMIT, "-c:a", "libmp3lame", "-q:a", "2"])
    video = out / (name + ".mp4")
    parts = picture if isinstance(picture, list) else [picture] if picture else []
    if parts and all(part.exists() for part in parts):
        inputs = [arg for part in parts for arg in ("-i", str(part))]
        graph = "".join("[%d:v]fps=24,scale=1920:1080,setsar=1[v%d];" % (i, i) for i in range(len(parts)))
        graph += "".join("[v%d]" % i for i in range(len(parts))) + "concat=n=%d:v=1:a=0[v]" % len(parts)
        subprocess.run(["ffmpeg", "-v", "error", "-y", *inputs, "-i", str(out / (name + ".mp3")),
                        "-filter_complex", graph, "-map", "[v]", "-map", "%d:a" % len(parts), "-c:v", "libx264",
                        "-crf", "20", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
                        "-t", "%.3f" % length, str(video)], check=True)
    peak = 20 * np.log10(np.abs(full).max() + 1e-12)
    print("%-6s %5.1fs  mix peak %5.1f dBFS before limiter  %s" % (
        name, length, peak, video.name if video.exists() else "(no picture)"), flush=True)


# The whole arrival in order, for a score laid across it (make_music.py score).
ARRIVAL = ["c1", "c2", "entry", "c3", "c4c5"]
# A shot whose newest sketch is missing plays the shipped v1 movie instead.
FALLBACK_PICTURES = {shot: ROOT / "mod/cinematic/jangala-arrival" / (shot + ".mp4") for shot in ("c1", "c2", "c3")}
SCORE_LEVEL = -4.0     # dB against dialogue, before ducking
DUCK_DB = -8.0         # under any voice


def moving_sum(x, width):
    """Sum of x over a centred window of `width` samples (cumulative sums: long windows stay cheap)."""
    c = np.concatenate([[0.0], np.cumsum(x)])
    lo = np.clip(np.arange(len(x)) - width // 2, 0, len(x))
    return c[np.clip(lo + width, 0, len(x))] - c[lo]


def duck(voices):
    """Gain that takes the music DUCK_DB down wherever a voice is speaking, easing in and out."""
    mono = np.abs(voices).max(axis=1)
    win = int(0.05 * SR)
    power = moving_sum(mono ** 2, win) / win
    active = (power > 10 ** (-45 / 10)).astype(float)
    active = (moving_sum(active, int(0.5 * SR)) > 0).astype(float)
    ease = int(0.4 * SR)
    active = moving_sum(active, ease) / ease
    return (10 ** (DUCK_DB * active / 20))[:, None]


def player_kit(scores, pictures, dirs, chosen, out, width=960):
    """Media for the music player page: the whole arrival's picture once (silent, small), its sound
    without music, each score loudness-matched to SCORE_LEVEL, and where the voices are, so music
    can be swapped, layered and ducked in the browser without rendering anything again."""
    out.mkdir(parents=True, exist_ok=True)
    pieces, voiced, shots, start = [], [], [], 0.0
    for shot in ARRIVAL:
        length, layers = SHOTS[shot]
        full = np.zeros((int(round(length * SR)), 2))
        voices = full.copy()
        for layer in layers:
            placed = place(layer, source(layer, dirs, chosen), length)
            full += placed
            if layer["kind"] == "voice":
                voices += placed
        pieces.append(full)
        voiced.append(voices)
        shots.append({"id": shot, "start": round(start, 3), "length": length})
        start += length
    whole = np.concatenate(pieces)
    write(whole, out / "bed.mp3", [*LIMIT, "-c:a", "libmp3lame", "-q:a", "2"])
    # The duck curve: music gain at 20 Hz, DUCK_DB down while anyone speaks.
    gain = duck(np.concatenate(voiced))[:, 0]
    step = SR // 20
    curve = [round(float(g), 3) for g in gain[::step]]
    tracks = []
    for item in scores:
        # PATH or PATH@SECONDS: a take that starts later in the arrival (an ending, a section) is
        # padded with silence so every track on the page shares one clock.
        path, _, at = item.partition("@")
        score = Path(path)
        music = decode(score) * 10 ** ((DIALOGUE_LUFS + SCORE_LEVEL - loudness(score)) / 20)
        music = np.concatenate([np.zeros((int(round(float(at or 0) * SR)), 2)), music])
        write(music, out / (score.stem + ".mp3"), ["-c:a", "libmp3lame", "-q:a", "2"])
        tracks.append({"id": score.stem, "file": score.stem + ".mp3", "start": float(at or 0)})
    (out / "kit.json").write_text(json.dumps({"length": round(len(whole) / SR, 3), "shots": shots,
                                              "duckRate": 20, "duck": curve, "tracks": tracks}) + "\n",
                                  encoding="utf-8")
    parts = []
    for shot in ARRIVAL:
        picture = pictures.get(shot)
        picture = picture[0] if isinstance(picture, list) else picture
        if not (picture and picture.exists()):
            picture = FALLBACK_PICTURES.get(shot)
        parts.append((picture, SHOTS[shot][0]))
    inputs = [arg for picture, _ in parts for arg in ("-i", str(picture))]
    graph = "".join("[%d:v]fps=12,scale=%d:%d,setsar=1,tpad=stop_mode=clone:stop_duration=120,"
                    "trim=duration=%.3f,setpts=PTS-STARTPTS[v%d];" % (i, width, width * 9 // 16, length, i)
                    for i, (_, length) in enumerate(parts))
    graph += "".join("[v%d]" % i for i in range(len(parts))) + "concat=n=%d:v=1:a=0[v]" % len(parts)
    subprocess.run(["ffmpeg", "-v", "error", "-y", *inputs, "-filter_complex", graph, "-map", "[v]",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "30", "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart", "-an", "-t", "%.3f" % (len(whole) / SR), str(out / "picture.mp4")],
                   check=True)
    print("player kit %5.1fs  %d tracks  pictures: %s" % (
        len(whole) / SR, len(tracks), ", ".join(str(p) for p, _ in parts)), flush=True)


BEDS = set()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--shot", action="append", choices=list(SHOTS))
    parser.add_argument("--picture", action="append", default=[], help="ID=PATH, overriding PICTURES")
    parser.add_argument("--castle", default=str(ROOT / "build/audio-previews/castle-lines"),
                        help="folder holding Castle's *_treated.wav review takes")
    parser.add_argument("--hail", default=str(HAIL), help="folder holding the encoded hail .ogg files")
    parser.add_argument("--out", default=str(OUT))
    parser.add_argument("--music", action="append", default=[],
                        help="a make_music.py take to lay into c4c5, peaking at the top of the rise; "
                             "writes c4c5-<take>.mp4 per take")
    parser.add_argument("--player", action="append", default=[],
                        help="a make_music.py score take for the music player page; writes the page's "
                             "media under <out>/player/ (picture, music-free mix, tracks, duck curve)")
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
    synth_landing.build(out / "synth")
    dirs = {"castle": Path(args.castle), "hail": Path(args.hail), "synth": out / "synth"}
    for take in args.music:
        mix("c4c5", pictures.get("c4c5"), dirs, chosen, out, [music(take, C4_LENGTH + 25.0)], Path(take).stem)
    if args.player:
        player_kit(args.player, pictures, dirs, chosen, out / "player")
    for shot in args.shot or ([] if args.music or args.player else SHOTS):
        mix(shot, pictures.get(shot), dirs, chosen, out)


if __name__ == "__main__":
    main()
