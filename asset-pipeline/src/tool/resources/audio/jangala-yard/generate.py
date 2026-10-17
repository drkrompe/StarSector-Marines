"""Deterministic, original yard sound design for the arrival's C5 NESSA'S YARD.

    python generate.py

Writes retained stereo PCM masters (44.1 kHz, 16-bit) beside this script. These are designed
effects built from noise and oscillators with numpy, not recordings and not generated voice.
`gradlew.bat :asset-pipeline:processAudio` encodes them into mod/sounds/story/jangala-arrival/.

Every clip is placed by compose_c5.audio_cues(), which owns the timing; the lengths here are
what that schedule assumes (see each function).
"""
import wave
from pathlib import Path

import numpy as np

RATE = 44100
ROOT = Path(__file__).resolve().parent


def t_axis(seconds):
    return np.arange(int(round(seconds * RATE))) / RATE


def noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n)


def onepole(x, cutoff):
    """One-pole low-pass, cutoff in Hz (scalar or per-sample array)."""
    a = np.exp(-2 * np.pi * np.broadcast_to(cutoff, x.shape) / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def band(x, lo, hi):
    """Crude band-pass by FFT mask with soft shoulders."""
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    m = np.clip((f - lo * 0.7) / (lo * 0.3 + 1e-9), 0, 1) * np.clip((hi * 1.3 - f) / (hi * 0.3), 0, 1)
    return np.fft.irfft(np.fft.rfft(x) * m, len(x))


def resonator(x, freq, decay_s):
    """Two-pole resonator (a struck-metal mode)."""
    r = np.exp(-1 / (decay_s * RATE))
    c = 2 * r * np.cos(2 * np.pi * freq / RATE)
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    for i in range(len(x)):
        y0 = x[i] + c * y1 - r * r * y2
        y[i] = y0
        y2, y1 = y1, y0
    return y


def fade(x, a=0.02, b=0.05):
    n = len(x)
    e = np.ones(n)
    na, nb = int(a * RATE), int(b * RATE)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nb:
        e[-nb:] = np.minimum(e[-nb:], np.linspace(1, 0, nb))
    return x * (e[:, None] if x.ndim == 2 else e)


def room(x, seed, wet=0.25, length=0.9):
    """Short outdoor slap and diffuse tail: the yard's hard walls a few dozen metres off."""
    n = int(length * RATE)
    ir = noise(n, seed) * np.exp(-np.arange(n) / (0.18 * RATE))
    ir[: int(0.035 * RATE)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    m = len(x) + n
    tail = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)[: len(x)]
    return x + wet * tail


def stereo(left, right=None, width=0.0, seed=0):
    if right is None:
        d = int(0.0007 * RATE)
        right = np.concatenate([np.zeros(d), left[:-d]]) if width else left
        if width:
            right = (1 - width) * left + width * right
    return np.stack([left, right], 1)


def write(name, st, peak=0.7):
    st = st / (np.max(np.abs(st)) + 1e-9) * peak
    pcm = (np.clip(st, -1, 1) * 32767).astype("<i2")
    with wave.open(str(ROOT / (name + ".wav")), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    print("wrote", name, "%.2fs" % (len(st) / RATE))


# ------------------------------------------------------------------ clips

def steam_vent():
    """9 s: the cradle's cooling vents. A broad hiss that breathes on the 2.9 s steam-sheet
    cycle (compose_c5.STEAM_PERIOD) with a pressure surge at the start of each breath."""
    t = t_axis(9.0)
    out = []
    for ch, seed in ((0, 11), (1, 12)):
        n = noise(len(t), seed)
        hiss = band(n, 1800, 9000) + 0.5 * band(n, 400, 1800)
        phase = (t / 2.9) % 1.0
        breathe = 0.55 + 0.45 * np.exp(-phase * 3.0) + 0.06 * np.sin(2 * np.pi * t * 0.37 + ch)
        flutter = 1 + 0.08 * band(noise(len(t), seed + 5), 6, 30) / 0.02
        out.append(hiss * breathe * np.clip(flutter, 0.6, 1.4))
    return fade(np.stack(out, 1) * 1.0, 0.4, 0.6)


def hull_ticks():
    """9 s: hot plate cooling. Sparse metallic ticks and pings, scattered left to right
    across the hull, a few with a longer ring."""
    t = t_axis(9.0)
    rng = np.random.default_rng(23)
    st = np.zeros((len(t), 2))
    for _ in range(26):
        at = int(rng.uniform(0.2, 8.6) * RATE)
        x = np.zeros(int(0.6 * RATE))
        x[0] = 1.0
        x[1:20] = rng.standard_normal(19) * 0.3
        f = rng.choice([rng.uniform(1700, 2600), rng.uniform(3000, 5200), rng.uniform(900, 1300)])
        decay = rng.uniform(0.015, 0.06) if rng.random() < 0.8 else rng.uniform(0.15, 0.3)
        y = resonator(x, f, decay) + 0.4 * resonator(x, f * 2.71, decay * 0.5)
        y *= rng.uniform(0.3, 1.0)
        pan = rng.uniform(0.15, 0.85)
        end = min(len(t), at + len(y))
        st[at:end, 0] += y[: end - at] * (1 - pan)
        st[at:end, 1] += y[: end - at] * pan
    st[:, 0] = room(st[:, 0], 31, 0.35)
    st[:, 1] = room(st[:, 1], 32, 0.35)
    return st


def crane_horn():
    """2.4 s: a distant yard crane's two-tone warning horn, bounced off the halls."""
    t = t_axis(2.4)
    env = np.clip(t / 0.06, 0, 1) * np.clip((1.25 - t) / 0.12, 0, 1)
    f = np.where(t < 0.62, 311.0, 262.0)
    ph = 2 * np.pi * np.cumsum(f) / RATE
    tone = sum(np.sin(ph * k) / k ** 0.9 for k in range(1, 9))
    tone = band(tone, 200, 2600) * env
    left = room(tone, 41, 0.9, 1.2)
    right = room(tone, 42, 0.9, 1.2)
    return np.stack([left * 0.8, right], 1)


def winch():
    """4 s: the gantry jib's electric winch paying out and hauling back (compose_c5.HOIST_PERIOD)."""
    t = t_axis(4.0)
    speed = np.abs(np.sin(np.pi * t / 2.0))
    f = 90 + 70 * speed
    ph = 2 * np.pi * np.cumsum(f) / RATE
    motor = np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.25 * np.sin(5 * ph) + 0.15 * np.sin(11 * ph)
    whine = 0.12 * np.sin(2 * np.pi * np.cumsum(f * 9.5) / RATE)
    gear = band(noise(len(t), 51), 2000, 6000) * 0.25 * (0.5 + 0.5 * np.sin(ph * 0.5) ** 2)
    clank = np.zeros(len(t))
    for at in (0.02, 1.98, 3.96):
        i = int(at * RATE)
        x = np.zeros(int(0.3 * RATE))
        x[0] = 1
        y = resonator(x, 640, 0.05) + resonator(x, 1510, 0.03)
        clank[i:i + len(y)] += 0.5 * y[: len(clank) - i]
    m = (motor + whine) * (0.25 + 0.75 * speed) + gear * speed + clank
    m = room(m, 52, 0.3)
    return stereo(m, width=0.6)


def radio_chatter():
    """2.6 s: faint gantry radio. Squelch open, a clipped unintelligible burst, squelch close.
    No words: band-limited, syllable-gated noise and buzz, so nothing needs subtitling."""
    t = t_axis(2.6)
    rng = np.random.default_rng(61)
    g = np.zeros(len(t))
    pos = 0.25
    while pos < 2.2:
        dur = rng.uniform(0.07, 0.2)
        a, b = int(pos * RATE), int((pos + dur) * RATE)
        g[a:b] = rng.uniform(0.5, 1.0) * np.hanning(b - a)
        pos += dur + rng.uniform(0.01, 0.09)
    f0 = 130 + 30 * np.sin(2 * np.pi * t * 1.3)
    buzz = np.sign(np.sin(2 * np.pi * np.cumsum(f0) / RATE)) * 0.6 + noise(len(t), 62) * 0.5
    voice = band(buzz * onepole(g, 40), 500, 2600)
    voice = np.tanh(voice * 4) * 0.5
    sq = np.zeros(len(t))
    for at, ln in ((0.02, 0.16), (2.3, 0.12)):
        a, b = int(at * RATE), int((at + ln) * RATE)
        sq[a:b] = band(noise(b - a, 63), 1500, 7000) * np.hanning(b - a)
    m = room(voice + 0.6 * sq + 0.03 * band(noise(len(t), 64), 800, 3000), 65, 0.2)
    return stereo(m * 0.9, m * 0.6)


def boots():
    """4.5 s: five pairs of boots on dry concrete, unhurried, slightly out of step."""
    t = t_axis(4.5)
    rng = np.random.default_rng(71)
    st = np.zeros((len(t), 2))
    for w in range(5):
        pos = rng.uniform(0.0, 0.5)
        period = rng.uniform(0.52, 0.6)
        pan = 0.55 + 0.08 * w
        gain = rng.uniform(0.5, 1.0)
        while pos < 4.3:
            i = int(pos * RATE)
            x = noise(int(0.12 * RATE), int(pos * 1000) + w)
            heel = band(x, 120, 900) * np.exp(-np.arange(len(x)) / (0.012 * RATE))
            grit = band(x, 2500, 8000) * 0.25 * np.exp(-np.arange(len(x)) / (0.03 * RATE))
            y = (heel + grit) * gain * rng.uniform(0.8, 1.1)
            e = min(len(t), i + len(y))
            st[i:e, 0] += y[: e - i] * (1 - pan)
            st[i:e, 1] += y[: e - i] * pan
            pos += period * rng.uniform(0.97, 1.03)
    st[:, 0] = room(st[:, 0], 72, 0.4)
    st[:, 1] = room(st[:, 1], 73, 0.4)
    return fade(st, 0.3, 0.6)


def jungle_bed():
    """10 s: the open air. Distant cicada-like pulsing buzz from the tree line, a few chirping
    insects nearer, and a low wash of wind and far machinery under it."""
    t = t_axis(10.0)
    out = []
    for ch in range(2):
        rng = np.random.default_rng(81 + ch)
        cic = np.zeros(len(t))
        for k in range(4):
            fc = rng.uniform(4200, 6800)
            car = np.sin(2 * np.pi * fc * t + rng.uniform(0, 6)) * (0.6 + 0.4 * np.sin(2 * np.pi * rng.uniform(140, 220) * t))
            swell = 0.5 + 0.5 * np.sin(2 * np.pi * t / rng.uniform(3, 7) + rng.uniform(0, 6))
            cic += car * swell ** 2 * rng.uniform(0.3, 0.6)
        chirp = np.zeros(len(t))
        pos = rng.uniform(0, 0.4)
        while pos < 9.6:
            a = int(pos * RATE)
            n = int(0.045 * RATE)
            fc = rng.uniform(2800, 3600)
            chirp[a:a + n] += np.sin(2 * np.pi * fc * np.arange(n) / RATE) * np.hanning(n) * 0.5
            pos += rng.choice([0.09, 0.09, 0.09, 0.6, 1.1])
        wash = band(noise(len(t), 90 + ch), 40, 400) * 1.2
        out.append(0.18 * cic + 0.12 * chirp + wash)
    return fade(np.stack(out, 1), 0.8, 1.0)


def spool_down():
    """2.8 s: the bridge from C4. Second Wind's starboard drive coil and auxiliaries winding down
    on the ground, heard from outside: a falling whine over a fading rumble."""
    t = t_axis(2.8)
    f = 900 * np.exp(-t / 0.9) + 110
    ph = 2 * np.pi * np.cumsum(f) / RATE
    whine = (np.sin(ph) + 0.3 * np.sin(2.01 * ph)) * np.exp(-t / 1.1)
    rumble = band(noise(len(t), 95), 30, 220) * 3.0 * np.exp(-t / 0.7)
    m = room(whine * 0.5 + rumble, 96, 0.5, 1.2)
    return fade(stereo(m, width=0.5), 0.0, 0.4)


CLIPS = {
    "c5_yard_steam_vent": steam_vent, "c5_yard_hull_ticks": hull_ticks, "c5_yard_crane_horn": crane_horn,
    "c5_yard_winch": winch, "c5_yard_radio_chatter": radio_chatter, "c5_yard_boots": boots,
    "c5_yard_jungle_bed": jungle_bed, "c5_yard_spool_down": spool_down,
}

if __name__ == "__main__":
    for name, fn in CLIPS.items():
        write(name, fn())
