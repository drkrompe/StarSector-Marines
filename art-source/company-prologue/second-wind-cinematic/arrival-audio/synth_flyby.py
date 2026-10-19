"""Code-built fly-bys for the arrival: a hull passing the camera, synthesised from its path.

  python art-source/company-prologue/second-wind-cinematic/arrival-audio/synth_flyby.py [--out DIR]

The owner asked for passes we can control exactly (2026-10-19, after the generated Hound swish
missed). Each pass is a straight path past the camera at the origin, the same numbers the
shot's own scene flies (`corvus-approach/c1v2_scene.py`), and the sound is computed from it:

- an engine voice (a low harmonic drone and a thin turbine whine) heard at its retarded time,
  so the Doppler drop falls out of the geometry; `sound_speed` is a cinematic stand-in, since
  there is no air in space, and sets how far the pitch drops;
- an air rush (pink noise through a band-pass) that swells and brightens with closeness;
- loudness from distance, dulling with distance, and a pan from the bearing.

Every knob is in PASSES. Output is regenerable, so it goes under build/; `sfx_mix.py` places it.
Seeded and deterministic. Requires numpy.
"""
import argparse
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OUT = ROOT / "build" / "audio-previews" / "cinematic-sfx" / "synth"
SR = 48000

# A pass: the clip covers [start, end] on the shot clock; the hull flies p0 at t0 to p1 at t1
# (metres, camera at the origin looking down +Y at the end of C1, +X to the right of frame).
HOUND = dict(start=21.5, end=34.5, t0=23.0, t1=32.0, p0=(-300.0, 220.0, -70.0), p1=(260.0, 270.0, -30.0),
             sound_speed=300.0,       # lower = stronger Doppler drop
             drone_hz=46.0, drone_harmonics=7, drone_gain=1.0,
             whine_hz=780.0, whine_gain=0.18,
             rush_gain=0.9, rush_low=180.0, rush_high=3200.0,
             distance_power=1.6,      # loudness ~ (closest / r) ** power
             pan_width=0.9, seed=11)
PASSES = {
    "hound_flyby_a": HOUND,
    # A sharper reading: more Doppler, more rush, a brighter whine, a tighter swell.
    "hound_flyby_b": dict(HOUND, sound_speed=210.0, rush_gain=1.3, rush_high=5000.0, whine_gain=0.28,
                          distance_power=2.2, seed=12),
}


def path(spec, t):
    k = (t - spec["t0"]) / (spec["t1"] - spec["t0"])
    p0, p1 = np.array(spec["p0"]), np.array(spec["p1"])
    return p0[None, :] + k[:, None] * (p1 - p0)[None, :]


def emission_times(spec, t):
    """When the sound heard at t left the hull: solve te = t - r(te) / c by iteration."""
    te = t.copy()
    for _ in range(8):
        te = t - np.linalg.norm(path(spec, te), axis=1) / spec["sound_speed"]
    return te


def one_pole(x, cutoff):
    a = np.exp(-2 * np.pi * np.asarray(cutoff) / SR) * np.ones_like(x)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def pink(n, rng):
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f))) / np.sqrt(np.maximum(f, 20.0))
    x = np.fft.irfft(spec, n)
    return x / np.abs(x).max()


def render(spec):
    rng = np.random.default_rng(spec["seed"])
    t = spec["start"] + np.arange(int((spec["end"] - spec["start"]) * SR)) / SR
    te = emission_times(spec, t)
    pos = path(spec, te)
    r = np.linalg.norm(pos, axis=1)
    near = (r.min() / r) ** spec["distance_power"]

    # Engine voice at the emission clock, so its pitch carries the Doppler shift.
    drone = sum(np.sin(2 * np.pi * spec["drone_hz"] * h * te + rng.uniform(0, 6.28)) / h ** 1.2
                for h in range(1, spec["drone_harmonics"] + 1))
    drone *= 1 + 0.15 * np.sin(2 * np.pi * 7.3 * te)                      # drive flutter
    whine = np.sin(2 * np.pi * spec["whine_hz"] * te + 0.8 * np.sin(2 * np.pi * 3.1 * te))
    engine = spec["drone_gain"] * drone / spec["drone_harmonics"] ** 0.5 + spec["whine_gain"] * whine
    engine = one_pole(engine, 400 + 7000 * near)                            # duller far off

    # Air rush: a band that opens and brightens as the hull closes.
    noise = pink(len(t), rng)
    low = one_pole(noise, spec["rush_low"])
    band = one_pole(noise - low, spec["rush_low"] + (spec["rush_high"] - spec["rush_low"]) * near)
    rush = spec["rush_gain"] * band / max(1e-9, np.abs(band).max()) * near ** 0.5

    mono = (engine / max(1e-9, np.abs(engine).max()) + rush) * near
    # Pan from the bearing across the final view (+Y ahead, +X right).
    bearing = np.arctan2(pos[:, 0], pos[:, 1])
    pan = np.clip(np.sin(bearing) * spec["pan_width"], -1, 1)
    left, right = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
    edge = np.minimum(1, np.minimum(t - t[0], t[-1] - t) / 0.4)             # soft clip ends
    out = np.stack([mono * left, mono * right], axis=1) * edge[:, None]
    return out / np.abs(out).max() * 0.89


def write(samples, path_out):
    path_out.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path_out), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(samples, -1, 1) * 32767).astype(np.int16).tobytes())


def build(out=OUT):
    for name, spec in PASSES.items():
        write(render(spec), out / (name + ".wav"))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(OUT))
    out = build(Path(parser.parse_args().out))
    for name in PASSES:
        print("Wrote", out / (name + ".wav"))


if __name__ == "__main__":
    main()
