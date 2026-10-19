"""Code-built fly-bys for the arrival: a hull passing the camera, synthesised from its path.

  python art-source/company-prologue/second-wind-cinematic/arrival-audio/synth_flyby.py [--out DIR]

The owner asked for passes we can control exactly (2026-10-19, after the generated Hound swish
missed), and for engines that sound like muffled heavy rockets. Each pass follows the path the
shot's own scene flies (`corvus-approach/c1v2_scene.py`), with the camera at the origin, and
the sound is computed from it:

- a rocket voice: a deep roar (brown noise with crackle) heard muffled through a low-pass,
  with a heavy low end. It is read at the retarded time, so the pitch drop of the pass falls
  out of the geometry. `sound_speed` is a cinematic stand-in, since space has no air, and
  sets how far the pitch drops;
- an air rush (pink noise through a band-pass) that swells and opens as the hull closes;
- loudness from distance, a muffle that opens as the hull closes, and a pan from the bearing.

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


def straight(t0, t1, p0, p1):
    """Constant velocity through p0 at t0 and p1 at t1 (c1v2_scene.fly)."""
    p0, p1 = np.array(p0), np.array(p1)
    return lambda t: p0[None, :] + ((t - t0) / (t1 - t0))[:, None] * (p1 - p0)[None, :]


def second_wind_lane(t):
    """Second Wind's lane in c1v2_scene (LANE_X/Z, lane_s): braking hard to pass the camera
    slowly at 10 s, then opening its drive toward Jangala."""
    pass_t, speed, start_s, end_s, duration = 10.0, 28.0, -1400.0, 2600.0, 37.7083
    a = (-start_s - speed * pass_t) / pass_t ** 2
    c = (end_s - speed * (duration - pass_t)) / (duration - pass_t) ** 2
    u, w = pass_t - t, t - pass_t
    s = np.where(t <= pass_t, -(a * u * u + speed * u), c * w * w + speed * w)
    return np.stack([np.full_like(t, 110.0), s, np.full_like(t, -45.0)], axis=1)


# C2 is seen from Second Wind's bridge (patrol-abeam/c2_bridge_blockout.py). Its frame: +X ahead
# along our bow, +Y out to port, +Z up, metres from the eye. The ear is turned toward port with
# the watcher's head, so these passes pan by bearing from the bow (port = left).
C2_V0, C2_V1, C2_ABEAM_T, C2_ABEAM_Y = 140.0, 22.0, 8.0, 265.0
C2_DROP_T, C2_DROP_ACCEL = 9.6, 9.0


def legion_along(t):
    """The Legion's midpoint distance ahead (m): closing from 140 m/s to 22 m/s by abeam."""
    d = C2_ABEAM_T - t
    ahead = C2_V1 * d + (C2_V0 - C2_V1) * d * d / (2 * C2_ABEAM_T)
    return np.where(t <= C2_ABEAM_T, ahead, -C2_V1 * (t - C2_ABEAM_T))


def legion_at(offset_m, out_m=0.0, up_m=0.0):
    """A point on the Legion offset_m forward of its midpoint (its bow leads toward us), rising
    relative to us once Second Wind starts down."""
    def path(t):
        x = legion_along(t) - offset_m
        z = 20.0 + up_m + 0.5 * C2_DROP_ACCEL * np.maximum(0.0, t - C2_DROP_T) ** 2
        return np.stack([x, np.full_like(t, C2_ABEAM_Y + out_m), z], axis=1)
    return path


def fighter(t_pass, out_m, up_m):
    """A Broadsword streaking past the port slits at 160 m/s, abeam our eye at t_pass."""
    def path(t):
        return np.stack([160.0 * (t_pass - t), np.full_like(t, out_m), np.full_like(t, up_m)], axis=1)
    return path


def fixed(x, y, z):
    return lambda t: np.tile(np.array([[x, y, z]], float), (len(t), 1))


# A pass: the clip covers [start, end] on the shot clock. In C1 +Y is ahead in the final view and
# +X right (pan="c1"); in C2 +X is our bow and +Y port (pan="c2").
HOUND = dict(start=21.5, end=34.5, path=straight(23.0, 32.0, (-300.0, 220.0, -70.0), (260.0, 270.0, -30.0)),
             sound_speed=300.0,       # lower = stronger pitch drop
             muffle_far=80.0, muffle_near=420.0,   # low-pass corner (Hz) far off and closest
             bass=1.4,                # extra weight below 90 Hz
             crackle=0.35,            # rocket crackle in the roar
             rush_gain=0.25, rush_low=120.0, rush_high=900.0,
             distance_power=1.6,      # loudness ~ (closest / r) ** power
             pan="c1", pan_width=0.9, seed=11,
             thrust=None,             # optional [(t, gain)] throttle curve
             thumps=(),               # ignition thumps, shot seconds
             rcs=())                  # thruster bursts: (t, seconds, gain)
FIGHTER = dict(HOUND, start=0.0, end=7.0, sound_speed=330.0, muffle_far=150.0, muffle_near=2600.0,
               bass=0.8, crackle=0.6, rush_gain=0.7, rush_low=250.0, rush_high=5000.0,
               distance_power=2.0, pan="c2", pan_width=1.0)
LEGION = dict(HOUND, start=0.0, end=15.0, sound_speed=340.0, muffle_far=60.0, muffle_near=380.0,
              bass=3.0, crackle=0.25, rush_gain=0.05, distance_power=1.4, pan="c2", pan_width=0.5)
# The two ships trim to look at each other (c2_bridge_blockout): the Legion's attitude thrusters
# fire in bursts along its hull while it closes and as it slows to the beam.
LEGION_RCS = ((1.6, 0.5, 0.8), (3.4, 0.35, 0.6), (5.1, 0.8, 1.0), (6.3, 0.4, 0.7), (7.2, 1.1, 1.0),
              (8.9, 0.45, 0.8), (10.4, 0.7, 0.9), (12.0, 0.5, 0.7))
PASSES = {
    "hound_flyby_a": HOUND,
    # Heavier and more muffled: more bass, a darker roar, a slower swell.
    "hound_flyby_b": dict(HOUND, muffle_near=280.0, bass=2.2, crackle=0.25, rush_gain=0.2,
                          distance_power=1.2, seed=12),
    # Second Wind's own drive: braking past the camera at 10 s, then its engines face us and it
    # opens up toward Jangala. The camera follows it, so it stays near the centre.
    "second_wind_pass": dict(HOUND, start=0.0, end=37.7, path=second_wind_lane, sound_speed=340.0,
                             muffle_far=70.0, muffle_near=320.0, bass=2.2, crackle=0.4,
                             rush_gain=0.15, distance_power=0.7, pan_width=0.15, seed=13),
    # C2. The Legion: main drives at its stern (it comes bow first, so they arrive last and
    # loudest just past the beam), a forward cluster, and attitude thrusters along the hull.
    "legion_drives": dict(LEGION, path=legion_at(-130.0), seed=21),
    "legion_forward": dict(LEGION, path=legion_at(90.0, up_m=-10.0), muffle_near=300.0, bass=2.4, seed=22,
                           rcs=LEGION_RCS),
    # The fighters: two vics of three, a hard thruster roar each, abeam at 3.0 and 4.2 s.
    **{"fighter_%d%d" % (flight, n): dict(FIGHTER, path=fighter(t_pass + back / 160.0, out + dy, up + dz),
                                          seed=30 + 3 * flight + n)
       for flight, (t_pass, out, up) in enumerate(((3.0, 55.0, 7.0), (4.2, 80.0, 9.0)))
       for n, (back, dy, dz) in enumerate(((0.0, 0.0, 0.0), (22.0, -14.0, -2.0), (22.0, 14.0, 1.5)))},
    # Second Wind's own drive from its bridge: idling under the room hum, then the de-orbit burn
    # lights at 9.6 s and the same engine opens up, on through the entry and into C3's first
    # seconds (C2 0-15, entry 15-22, C3 22-24).
    "second_wind_interior": dict(HOUND, start=0.0, end=24.0, path=fixed(-40.0, 0.0, -6.0), sound_speed=340.0,
                                 muffle_far=140.0, muffle_near=140.0, bass=2.6, crackle=0.3, rush_gain=0.0,
                                 distance_power=1.0, pan_width=0.0, seed=41,
                                 thrust=[(0.0, 0.22), (9.55, 0.22), (9.8, 0.75), (11.5, 1.0), (24.0, 1.0)],
                                 thumps=(9.6,)),
}

def emission_times(spec, t):
    """When the sound heard at t left the hull: solve te = t - r(te) / c by iteration."""
    te = t.copy()
    for _ in range(8):
        te = t - np.linalg.norm(spec["path"](te), axis=1) / spec["sound_speed"]
    return te


def one_pole(x, cutoff):
    a = np.exp(-2 * np.pi * np.asarray(cutoff, float) / SR) * np.ones_like(x)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def coloured(n, rng, slope):
    """Noise with a 1/f**slope power spectrum (1 = pink, 2 = brown)."""
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f))) / np.maximum(f, 15.0) ** (slope / 2)
    x = np.fft.irfft(spec, n)
    return x / np.abs(x).max()


def rocket(n, rng, crackle):
    """The engine as it would sound in the open: a brown roar with sparse crackle."""
    roar = coloured(n, rng, 2.0) + 0.35 * coloured(n, rng, 1.0)
    pops = (rng.random(n) < 45.0 / SR) * rng.uniform(-1, 1, n)
    kernel = np.exp(-np.arange(int(0.004 * SR)) / (0.0008 * SR))
    pops = np.convolve(pops, kernel)[:n]
    return roar / np.abs(roar).max() + crackle * pops / max(1e-9, np.abs(pops).max())


def render(spec):
    rng = np.random.default_rng(spec["seed"])
    t = spec["start"] + np.arange(int(round((spec["end"] - spec["start"]) * SR))) / SR
    te = emission_times(spec, t)
    pos = spec["path"](te)
    r = np.linalg.norm(pos, axis=1)
    near = (r.min() / r) ** spec["distance_power"]

    # The source runs on its own clock and is read at the emission time: that read carries the
    # pitch drop. Pad it so the earliest emission time still has signal.
    origin = te.min() - 0.1
    src = rocket(int((te.max() - origin + 0.2) * SR), rng, spec["crackle"])
    voice = np.interp((te - origin) * SR, np.arange(len(src)), src)
    corner = spec["muffle_far"] + (spec["muffle_near"] - spec["muffle_far"]) * near
    muffled = one_pole(one_pole(voice, corner), corner)                     # two poles: properly dull
    sub = one_pole(one_pole(voice, 90.0), 90.0)
    engine = muffled + spec["bass"] * sub
    engine /= max(1e-9, np.abs(engine).max())

    noise = coloured(len(t), rng, 1.0)
    low = one_pole(noise, spec["rush_low"])
    band = one_pole(noise - low, spec["rush_low"] + (spec["rush_high"] - spec["rush_low"]) * near)
    rush = spec["rush_gain"] * band / max(1e-9, np.abs(band).max()) * near

    mono = (engine + rush) * near
    if spec["thrust"]:
        points = np.array(spec["thrust"], float)
        mono = mono * np.interp(t, points[:, 0], points[:, 1])
    for when in spec["thumps"]:
        # Ignition: a deep, short hit under the engine as it lights.
        k = np.clip(t - when, 0, None)
        hit = np.sin(2 * np.pi * 38.0 * k) * np.exp(-k / 0.35) * (t >= when)
        mono = mono + 1.2 * hit * np.abs(mono).max()
    if spec["rcs"]:
        hiss = one_pole(noise - one_pole(noise, 300.0), 600.0 + 1600.0 * near)
        hiss /= max(1e-9, np.abs(hiss).max())
    for when, seconds, gain in spec["rcs"]:
        # A thruster burst: a hiss of gas with a fast start and a short tail, dulled by distance.
        k = t - when
        shape = np.clip(k / 0.03, 0, 1) * np.clip((seconds - k) / 0.15 + 1, 0, 1) * (k >= 0)
        mono = mono + gain * 0.9 * shape * near * hiss
    if spec["pan"] == "c2":
        bearing = np.arctan2(pos[:, 1], pos[:, 0])      # from the bow, + to port
        pan = np.clip(-np.sin(bearing) * spec["pan_width"], -1, 1)
    else:
        bearing = np.arctan2(pos[:, 0], pos[:, 1])
        pan = np.clip(np.sin(bearing) * spec["pan_width"], -1, 1)
    left, right = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
    edge = np.minimum(1, np.minimum(t - t[0], t[-1] - t) / 0.4)
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
