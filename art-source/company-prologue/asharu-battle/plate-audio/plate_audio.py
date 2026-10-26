"""The recording plates' sound (S0, S13): the terminal's own sounds laid on the plate's timeline,
the sampled sounds of the set and its room (plate_sfx.py) under them, and the relay's channel.

  python art-source/company-prologue/asharu-battle/plate-audio/plate_audio.py s0|s13 [--video MP4]

Writes build/asharu-plate-audio/<shot>.wav (48 kHz stereo, the shot's own length, sample 0 at the
shot's first frame) and, with --video, <shot>.mp4: that clip with the mix laid on it.

Everything is keyed to recording_plate's constants, so a retimed plate carries its sound with it:
the keys under every character the terminal types, the answer tone of each query by its kind, the
raster write-in of each view, the zoom boxes, the sweep, the relay pings on Corvus, the flag, the
stamps (NOT ESCALATED, ARCHIVED), the camera's push into the optic and its pull back out, and the set
switching on and off. The terminal's sounds are synthesised here (deterministic, no takes); the set,
its hum, the room and the static are takes (plate_sfx.CUES).

The radio traffic is not voiced yet: each line opens and closes the squelch and lifts the static
under its caption, so the channel's rhythm can be judged. The voices are a separate pass.
"""
import argparse
import json
import math
import random
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import radio_log  # noqa: E402
import recording_plate as rp  # noqa: E402

ROOT = HERE.parents[3]
OUT = ROOT / "build" / "asharu-plate-audio"
SR = 48000
SQUELCH = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio" / "jangala-arrival"
# the take of each sampled cue the mix uses, until `plate_sfx.py pick` records another
DEFAULT_TAKES = {"crt_on": 2, "crt_off": 1, "crt_hum": 2, "room_bed": 1, "relay_static": 1}

# The sound families the owner judges option by option (sound_review.py's page): each option's key,
# what it is, and its source (a take's stem, or a synthesised style). CHOICE is what the mix uses.
OPTIONS = {
    "crt_on": {"A": ("TV switch-on, first pass", "take:crt_on_t02"),
               "B": ("Computer monitor, take 1", "take:crt_on_monitor_t01"),
               "C": ("Computer monitor, take 2", "take:crt_on_monitor_t02"),
               "D": ("Heavy old display, take 1", "take:crt_on_heavy_t01"),
               "E": ("Heavy old display, take 2", "take:crt_on_heavy_t02"),
               "F": ("Synthesised: click, degauss, whine", "synth")},
    "crt_off": {"A": ("TV switch-off, first pass", "take:crt_off_t01"),
                "B": ("Computer monitor, take 1", "take:crt_off_monitor_t01"),
                "C": ("Computer monitor, take 2", "take:crt_off_monitor_t02"),
                "D": ("Heavy old display, take 1", "take:crt_off_heavy_t01"),
                "E": ("Heavy old display, take 2", "take:crt_off_heavy_t02"),
                "F": ("Synthesised: snap and falling tone", "synth")},
    "text": {"A": ("Key ticks, first pass (typewriter)", "keys"),
             "B": ("Data pips: a short high blip per character", "pips"),
             "C": ("Soft digital ticks", "ticks"),
             "D": ("Data stream: a tone per character, pitched by it", "stream"),
             "E": ("Silent: no sound while typing", "none")},
    "transition": {"A": ("Noise sweeps and data patter, first pass", "noise"),
                   "B": ("Digital: blip runs and tone glides", "digital"),
                   "C": ("Interface sweep, take 1", "take:ui_transition_t01"),
                   "D": ("Interface sweep, take 2", "take:ui_transition_t02"),
                   "E": ("Minimal: one soft tone, the hum swell only", "minimal")},
}
CHOICE = {"crt_on": "B", "crt_off": "B", "text": "B", "transition": "B"}

# every level, in dB, in one place: the mix is judged and turned here
# (beds and synthesised sounds by RMS; the set's switching sounds, all transient, by peak)
MASTER = 12
LEVEL = dict(
    room=-30, hum=-31, static_bed=-38, static_line=-29, squelch=-20, crt_on=-9, crt_off=-12, snow=-24,
    key=-27, boot_key=-24, answer=-22, flag=-17, chatter=-31, zoom=-25, sweep=-24, ping=-33,
    stamp=-15, push=-17, route=-30, crest=-24)


# ------------------------------------------------------------------------------------- sources

def decode(path):
    """A sound file as mono float32 at SR."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def norm(x, db):
    """x at an RMS of db dBFS (over its louder half, so a quiet tail does not drag a spot down)."""
    a = np.abs(x)
    loud = x[a >= np.median(a)] if len(x) else x
    rms = math.sqrt(float(np.mean(loud ** 2))) or 1.0
    return x * (10 ** (db / 20) / rms)


def peak(x, db):
    """x at a peak of db dBFS."""
    return x * (10 ** (db / 20) / (float(np.max(np.abs(x))) or 1.0))


def take_file(stem):
    return decode(HERE / "sfx-takes" / (stem + ".mp3"))


def take(cue):
    manifest = json.loads((HERE / "sfx-manifest.json").read_text(encoding="utf-8"))
    chosen = manifest["cues"].get(cue, {}).get("chosen") or "%s_t%02d" % (cue, DEFAULT_TAKES[cue])
    return decode(HERE / "sfx-takes" / (chosen + ".mp3"))


# ---------------------------------------------------------------------------------- synthesis

def env(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(1.0, t / max(attack, 1e-4)) * np.exp(-t / decay)


def onepole(x, cutoff):
    """A one-pole low-pass, its cutoff (Hz) fixed or one per sample."""
    cut = np.broadcast_to(np.asarray(cutoff, np.float64), x.shape)
    k = 1 - np.exp(-2 * math.pi * cut / SR)
    y, acc = np.empty_like(x), 0.0
    for i in range(len(x)):
        acc += k[i] * (x[i] - acc)
        y[i] = acc
    return y


def key(seed):
    """One key of a terminal keyboard: a short dry tick, a little different each time."""
    r = np.random.default_rng(seed)
    n = int(.035 * SR)
    noise = r.normal(0, 1, n)
    tick = onepole(noise - onepole(noise, 1800 + 900 * r.random()), 5500)      # a soft membrane key, not a crack
    tick = tick * env(n, .0005, .004 + .002 * r.random()) * 2.2
    body = np.sin(2 * math.pi * (180 + 60 * r.random()) * np.arange(n) / SR) * env(n, .0005, .008) * .35
    return tick + body


def tone(freq, dur, shape="square", decay=None):
    """A terminal tone: a soft-edged square (or sine), the phosphor's own beep."""
    n = int(dur * SR)
    ph = 2 * math.pi * freq * np.arange(n) / SR
    w = np.sign(np.sin(ph)) * .6 + np.sin(ph) * .4 if shape == "square" else np.sin(ph)
    w = onepole(w, 3500)
    e = np.minimum(1, np.arange(n) / (.004 * SR)) * np.minimum(1, (n - np.arange(n)) / (.012 * SR))
    if decay:
        e = e * np.exp(-np.arange(n) / SR / decay)
    return w * e


def seq(*parts, gap=.03):
    """Tones one after another, a short gap between."""
    out = []
    for p in parts:
        out += [p, np.zeros(int(gap * SR))]
    return np.concatenate(out)


ANSWERS = {   # a query's answer tone, by its kind
    "ok": lambda: seq(tone(1320, .05), tone(1760, .07)),
    "note": lambda: tone(1180, .07),
    "flag": lambda: seq(tone(880, .06), tone(660, .09)),
    "down": lambda: seq(tone(990, .06), tone(740, .06), tone(560, .1)),
}


def chatter(dur, seed):
    """The raster writing a view in: a fast patter of ticks, the data arriving."""
    r = random.Random(seed)
    out = np.zeros(int(dur * SR) + SR // 10)
    t = 0.0
    while t < dur:
        k = key(seed * 1000 + int(t * 1000)) * r.uniform(.3, 1.0)
        i = int(t * SR)
        out[i:i + len(k)] += k[:len(out) - i]
        t += r.uniform(.012, .03)
    return out * np.linspace(1, .6, len(out))


def whoosh(dur, rising=True, seed=1):
    """A zoom: filtered noise sweeping up (in) or down (out)."""
    n = int(dur * SR)
    u = np.arange(n) / n
    sweep = (u if rising else 1 - u) ** 1.5
    x = onepole(np.random.default_rng(seed).normal(0, 1, n), 300 + 5000 * sweep)
    return x * np.sin(math.pi * u) ** .8


def ping(freq=740):
    """A relay's ping: a soft sine with a long tail, faintly echoed."""
    n = int(1.4 * SR)
    p = np.sin(2 * math.pi * freq * np.arange(n) / SR) * env(n, .003, .22)
    for d, g in ((.11, .35), (.23, .18)):
        i = int(d * SR)
        p[i:] += p[:n - i] * g
    return p


def thunk(f0=110, dur=.5):
    """A stamp: a low drop in pitch with a click on its front."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    body = np.sin(2 * math.pi * np.cumsum(f0 * np.exp(-t * 4)) / SR) * env(n, .002, .14)
    click = np.random.default_rng(int(f0)).normal(0, 1, n) * env(n, .0003, .006) * .5
    return body + click


def push(dur, rising=True):
    """The camera on the tube's face: a swell of hum and air, pitched up going in, down coming out."""
    n = int(dur * SR)
    u = np.arange(n) / n
    s = u if rising else 1 - u
    f = 55 + 65 * s
    hum = np.sin(2 * math.pi * np.cumsum(f) / SR) + .4 * np.sin(4 * math.pi * np.cumsum(f) / SR)
    air = onepole(np.random.default_rng(9).normal(0, 1, n), 200 + 4000 * s ** 2)
    e = (s ** 2 if rising else (1 - u) ** .6 * np.minimum(1, u * 20))
    return (hum * .6 + air) * e


def snow(dur):
    """The roll-lock's snow: hiss thinning out as the picture holds."""
    n = int(dur * SR)
    x = np.random.default_rng(3).normal(0, 1, n)
    return (x - onepole(x, 1500)) * (1 - np.arange(n) / n) ** 1.5


def crt_on_synth():
    """A set switched on: the switch, the degauss coil's wobbling buzz, the tube's whine rising."""
    n = int(2.6 * SR)
    t = np.arange(n) / SR
    click = np.random.default_rng(11).normal(0, 1, n) * env(n, .0002, .004)
    wob = 1 + .5 * np.sin(2 * math.pi * 9 * t) * np.exp(-t * 2)
    degauss = (np.sin(2 * math.pi * 60 * t) + .5 * np.sign(np.sin(2 * math.pi * 120 * t))) * wob * np.exp(-t * 2.2)
    thump = np.sin(2 * math.pi * np.cumsum(70 * np.exp(-t * 6)) / SR) * env(n, .002, .12)
    whine = np.sin(2 * math.pi * 9800 * t) * np.minimum(1, t / .6) * .06
    crackle = (np.random.default_rng(12).random(n) > .9985) * np.random.default_rng(13).normal(0, 1, n) * np.exp(-t)
    return click * .8 + onepole(degauss, 900) * .5 + thump * .7 + whine + crackle * .4


def crt_off_synth():
    """A set switched off: a snap, the picture collapsing in a falling tone, a fizz dying."""
    n = int(1.6 * SR)
    t = np.arange(n) / SR
    snap = np.random.default_rng(21).normal(0, 1, n) * env(n, .0002, .006)
    fall = np.sin(2 * math.pi * np.cumsum(1400 * np.exp(-t * 7) + 40) / SR) * env(n, .003, .25)
    fizz = onepole(np.random.default_rng(22).normal(0, 1, n), 4000) * env(n, .01, .3) * .3
    return snap + fall * .6 + fizz


def text_sound(style, seed, ch=" "):
    """One character the terminal types, in a style: keys, pips, ticks or a stream tone."""
    r = np.random.default_rng(seed)
    if style == "keys":
        return key(seed)
    if style == "pips":
        f = (2093, 2349, 2637, 3136)[r.integers(4)]
        n = int(.016 * SR)
        return np.sin(2 * math.pi * f * np.arange(n) / SR) * env(n, .001, .005)
    if style == "ticks":
        n = int(.01 * SR)
        sq = np.sign(np.sin(2 * math.pi * 1250 * np.arange(n) / SR))
        return onepole(sq, 3000) * env(n, .0005, .0025)
    if style == "stream":
        f = 1300 + (ord(ch) % 12) * 85
        n = int(.034 * SR)
        return onepole(np.sign(np.sin(2 * math.pi * f * np.arange(n) / SR)), 4000) * env(n, .002, .02) * .7
    return np.zeros(1)


def transition(style, kind, dur, rising=True, seed=1):
    """A view change in a style: the raster writing a view in ('write'), a zoom box ('zoom'), or the
    camera pushing into the optic or pulling out ('push')."""
    if style == "noise":
        return {"write": lambda: chatter(dur, seed), "zoom": lambda: whoosh(dur, rising, seed),
                "push": lambda: push(dur, rising)}[kind]()
    if style == "digital":
        if kind == "write":
            out = np.zeros(int((dur + .1) * SR))
            for k in range(10):
                f = 900 * 2 ** (k / 6)
                b = tone(f, .025, shape="sine", decay=.01)
                i = int(k / 10 * dur * SR)
                out[i:i + len(b)] += b[:len(out) - i]
            return out
        if kind == "zoom":
            n = int(dur * SR)
            u = np.arange(n) / n
            f = (500 + 900 * (u if rising else 1 - u))
            return np.sin(2 * math.pi * np.cumsum(f) / SR) * np.sin(math.pi * u) * .6
        n = int(dur * SR)
        u = np.arange(n) / n
        s = u if rising else 1 - u
        g = np.sin(2 * math.pi * np.cumsum(110 + 330 * s ** 2) / SR) * .4
        return g * (s ** 1.5 if rising else (1 - u) ** .6 * np.minimum(1, u * 20)) + push(dur, rising) * .35
    if style.startswith("take:"):
        if kind == "push":
            return push(dur, rising) * .6
        x = take_file(style[5:])
        x = peak(x, -6)[:int((dur + .6) * SR)]
        return x * np.minimum(1, (len(x) - np.arange(len(x))) / (.15 * SR))
    if style == "minimal":
        if kind == "write":
            return tone(880, .06, shape="sine", decay=.04)
        if kind == "zoom":
            return tone(660 if rising else 520, .05, shape="sine", decay=.03)
        return push(dur, rising) * .5
    return np.zeros(1)


# ---------------------------------------------------------------------------------------- mix

class Mix:
    def __init__(self, t0, t1):
        self.t0, self.t1 = t0, t1
        self.buf = np.zeros(int(round((t1 - t0) * SR)) + 1, np.float64)
        self.style = "keys"
        self.fam, self.only = None, None             # the family being placed; the one to keep, if soloing
        self.cues = []                                  # (family, label, t): where each judged sound plays

    def mark(self, family, label, t):
        if self.t0 <= t < self.t1:
            self.cues.append((family, label, round(t, 3)))

    def at(self, t, x, db, norm_db=None):
        """x placed at sequence time t, at db (x normalised to norm_db first if given)."""
        if self.only and self.fam != self.only:
            return
        if norm_db is not None:
            x = norm(x, norm_db)
        x = np.asarray(x, np.float64) * 10 ** (db / 20)
        i = int(round((t - self.t0) * SR))
        a, b = max(0, i), min(len(self.buf), i + len(x))
        if b > a:
            self.buf[a:b] += x[a - i:b - i]

    def bed(self, x, t_in, t_out, db, fade=.5, level=None):
        """A looped bed from t_in to t_out; level(t) shapes it (in dB) if given."""
        x = norm(x, -20)
        t_in, t_out = max(t_in, self.t0), min(t_out, self.t1)
        n = int((t_out - t_in) * SR)
        if n <= 0:
            return
        loop = np.tile(x, n // len(x) + 1)[:n]
        e = np.minimum(1, np.minimum(np.arange(n), n - np.arange(n)) / (fade * SR))
        if level is not None:
            ts = t_in + np.arange(0, n, 480) / SR
            e = e * np.repeat(10 ** (np.array([level(t) for t in ts]) / 20), 480)[:n]
        self.at(t_in, loop * e, db)

    def typed(self, t, text, cps, db, seed):
        """The sound under each character of a line the terminal types."""
        for k, ch in enumerate(text):
            if ch != " ":
                self.at(t + k / cps, text_sound(self.style, seed + k, ch), db)


def source(family, choice):
    return OPTIONS[family][choice[family]][1]


def build(shot, choice=None, only=None):
    """The shot's mix, as a stereo float array, and the cues it placed: [(family, label, t)]. With
    only, the mix holds that family's sounds alone (the review page's solo)."""
    choice = dict(CHOICE, **(choice or {}))
    _, t0, t1, mode = rp.SHOTS[shot]
    m = Mix(t0, t1)
    m.only = only
    m.style = source("text", choice)
    tr = source("transition", choice)
    open_ = mode == "open"
    L = LEVEL
    lines = radio_log.OPENING if open_ else radio_log.CLOSING

    # the set and its room
    m.fam = "beds"
    m.bed(take("room_bed"), t0, t1, L["room"], fade=1.0)
    m.mark("beds", "The room and the set's hum", t0 + (1.5 if open_ else 15.5))
    on, off = (rp.POWER_ON[0], t1) if open_ else (t0, rp.TUNE_OUT[0] + .15)
    m.bed(take("crt_hum"), on + (.3 if open_ else 0), off, L["hum"], fade=.4)
    if open_:
        m.fam = "crt_on"
        src = source("crt_on", choice)
        m.at(rp.POWER_ON[0] - .05, peak(crt_on_synth() if src == "synth" else take_file(src[5:]), 0), L["crt_on"])
        m.at(rp.POWER_ON[1], snow(rp.ROLL_LOCK_S), L["snow"], -20)
        m.mark("crt_on", "The set switches on", rp.POWER_ON[0])
    else:
        m.fam = "crt_off"
        src = source("crt_off", choice)
        m.at(rp.TUNE_OUT[0] - .05, peak(crt_off_synth() if src == "synth" else take_file(src[5:]), 0), L["crt_off"])
        m.mark("crt_off", "The set switches off", rp.TUNE_OUT[0])

    # the boot slate: the name, the directorate, the status typed out; the crest settling up
    if open_:
        m.fam = "text"
        for k, (name, text) in enumerate((("name", "HEGEMONY NAVY"), ("sub", "SECURITY DIRECTORATE  //  TRAFFIC ANALYSIS"),
                                          ("status", "ESTABLISHING SECTOR FEED"))):
            m.typed(rp.BOOT_TYPE[name], text, rp.BOOT_CPS, L["boot_key"], 100 * (k + 1))
        m.mark("text", "The boot slate types out", rp.BOOT_TYPE["name"])
        m.fam = "crest"
        m.at(rp.CREST_TO_HEADER[0], push(rp.CREST_TO_HEADER[1] - rp.CREST_TO_HEADER[0] + .2), L["crest"], -20)
        m.mark("crest", "The crest settles into the header", rp.CREST_TO_HEADER[0])
    else:
        m.fam = "crest"
        m.at(rp.CREST_BACK[0], push(rp.CREST_BACK[1] - rp.CREST_BACK[0] + .3, rising=False), L["crest"], -20)
        m.mark("crest", "The crest comes back to the slate", rp.CREST_BACK[0])
        m.fam = "text"
        m.typed(rp.SLATE_OUT_TYPE, "FEED IDLE // TRACKING CONTINUES", rp.BOOT_CPS, L["boot_key"], 900)
        m.mark("text", "The slate types its sign-off", rp.SLATE_OUT_TYPE)

    # the views writing in, the zoom boxes between them
    m.fam = "transition"
    views = rp.S0_VIEWS if open_ else rp.S13_VIEWS
    for k, (name, a, _) in enumerate(views):
        if t0 < a < t1:
            m.at(a, transition(tr, "write", rp.REVEAL_S, seed=50 + k), L["chatter"], -20)
            m.mark("transition", "The %s view writes in" % name, a)
    for k, (za, zb, src_, dst) in enumerate(rp.ZOOMS_S0 if open_ else rp.ZOOMS_S13):
        m.at(za, transition(tr, "zoom", zb - za + .15, rising=open_, seed=20 + k), L["zoom"], -20)
        m.mark("transition", "Zoom box: %s to %s" % (src_.replace("_", " "), dst.replace("_", " ")), za)

    # the sector's sweep, Corvus's relay pings, the contact, the flag
    m.fam = "sweep"
    for k, (t, _) in enumerate(rp.SWEEP if open_ else rp.SWEEP_OUT):
        m.at(t, seq(tone(1480, .04), tone(1480, .04), gap=.05), L["sweep"], -20)
        if k == 0:
            m.mark("sweep", "The sector sweep", t)
    m.fam = "ping"
    corvus = next(v for v in views if v[0] == "corvus")
    for key_, period, off_ in (("relay", 3.0, 0.0), ("array", 2.2, .9)):
        t = corvus[1] + (-(corvus[1] + off_)) % period
        while t < corvus[2]:
            m.at(t, ping(740 if key_ == "relay" else 990), L["ping"], -20)
            t += period
    m.mark("ping", "Corvus's relay pings", corvus[1] + .2)
    if open_:
        m.fam = "flag"
        m.at(rp.UNKNOWN_SEEN, tone(560, .12, decay=.08), L["flag"] - 4, -20)
        m.at(rp.FLAG_S, seq(tone(620, .09), tone(620, .09), tone(620, .09), gap=.07), L["flag"], -20)
        m.mark("flag", "The contact, then the flag", rp.UNKNOWN_SEEN)
    else:
        m.fam = "route"
        m.at(rp.ADVISED_S, chatter(1.2, 77) * np.linspace(1, .3, int(1.3 * SR) + SR // 10)[:len(chatter(1.2, 77))],
             L["route"], -20)
        m.mark("route", "The patrol route redrawn", rp.ADVISED_S)

    # the query log: the keys under each call, the answer's tone
    for k, q in enumerate(rp.QUERIES):
        if t0 <= q[0] < t1:
            m.fam = "text"
            m.typed(q[0], "> " + q[1], rp.TYPE_CPS, L["key"], 1000 + 50 * k)
            m.fam = "answer"
            m.at(rp.answered(q), ANSWERS[q[3]](), L["answer"], -20)
            m.mark("text", "Query typed: " + q[1], q[0])
            m.mark("answer", "Answer (%s): %s" % (q[3], q[2]), rp.answered(q))

    # the stamps
    m.fam = "stamp"
    if not open_:
        m.at(rp.NOT_ESCALATED_S, seq(thunk(120), tone(520, .14, decay=.1), gap=.0), L["stamp"], -20)
        m.at(rp.ARCHIVE_S, seq(thunk(90, .6), tone(390, .2, decay=.14), gap=.0), L["stamp"], -20)
        m.mark("stamp", "NOT ESCALATED", rp.NOT_ESCALATED_S)
        m.mark("stamp", "ARCHIVED", rp.ARCHIVE_S)

    # the camera: into the optic for the cut to picture, back out of it
    m.fam = "transition"
    if open_:
        m.at(rp.CAM_IN[0], transition(tr, "push", rp.CAM_IN[1] - rp.CAM_IN[0]), L["push"], -20)
        m.mark("transition", "The camera pushes into the optic", rp.CAM_IN[0])
    else:
        m.at(t0, transition(tr, "push", rp.CAM_OUT[1] - t0, rising=False), L["push"], -20)
        m.mark("transition", "The camera pulls back out of the optic", t0)

    # the relay channel: static under the tap, lifted with the squelch under each line
    m.fam = "radio"
    tap_on = rp.R[1] if open_ else t0
    tap_off = t1 if open_ else rp.TAP_CLOSE_S

    def static_level(t):
        live = any(a - .05 <= t <= b + .05 for _, _, a, b, _ in lines)
        return (L["static_line"] if live else L["static_bed"]) - L["static_bed"]
    m.bed(take("relay_static"), tap_on, tap_off, L["static_bed"], fade=.2, level=static_level)
    m.mark("radio", "The relay channel: squelch and static under each line", lines[0][2] - .3)
    sq_open, sq_close = decode(SQUELCH / "squelch_open.wav"), decode(SQUELCH / "squelch_close.wav")
    for _, _, a, b, _ in lines:
        m.at(a - .06, sq_open, L["squelch"], -20)
        m.at(b, sq_close, L["squelch"], -20)
    if not open_:
        m.at(rp.TAP_CLOSE_S, sq_close, L["squelch"] + 2, -20)

    # hard cut at S0's end (the picture takes over); the set's own fade at S13's
    x = m.buf
    if open_:
        x[-int(.01 * SR):] *= np.linspace(1, 0, int(.01 * SR))
    x = x * 10 ** (MASTER / 20)
    over = np.abs(x) > .7                               # a soft limit: only the loudest moments bend
    x[over] = np.sign(x[over]) * (.7 + .2 * np.tanh((np.abs(x[over]) - .7) / .2))
    return np.stack([x, x], -1).astype(np.float32), m.cues


def encode(x, wav):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    "-af", "alimiter=limit=0.7:level=false:latency=true", "-c:a", "pcm_s16le", str(wav)],
                   input=x.tobytes(), check=True)


def write(shot, video=None):
    OUT.mkdir(parents=True, exist_ok=True)
    wav = OUT / (shot + ".wav")
    x, _ = build(shot)
    encode(x, wav)
    print("wrote", wav)
    if video:
        mp4 = OUT / (shot + ".mp4")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(video), "-i", str(wav), "-map", "0:v", "-map", "1:a",
                        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", str(mp4)], check=True)
        print("wrote", mp4)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("shot", choices=["s0", "s13"])
    ap.add_argument("--video")
    a = ap.parse_args()
    write(a.shot, a.video)
