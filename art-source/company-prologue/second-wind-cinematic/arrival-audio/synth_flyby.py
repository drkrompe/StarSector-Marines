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
import subprocess
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
C2_V0, C2_V1, C2_ABEAM_T, C2_ABEAM_Y = 140.0, 22.0, 8.0, 340.0
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



# C4-C5 joined clock: C5 second = joined - C4_SECONDS. The C5 camera (c5_scene.camera_at) holds
# over the ramp at 220 m after its push-in, then rises 18-26 s to 1800 m (x1.04 by the end) and
# drifts west; the departing Colossus flies port_fleet.DEPART_KEYS. Positions are camera-relative.
C4_SECONDS = 21.4
# port_fleet's 'Colossus at the heavy berth' (C5 seconds, world metres), as port-fleet keys it: parked
# in the walled bay until it lifts on its jets at 15, then the mains take it up and away west.
# Interpolated smoothly (it accelerates), never linearly. Paste a new track here when it changes.
COLOSSUS_KEYS = ((0.0, -3.8, 665.8, 26.6), (16.0, -3.8, 665.8, 27.0), (18.0, -3.8, 661.6, 29.5),
                 (20.0, -3.8, 653.9, 34.4), (22.0, -6.4, 649.0, 43.0), (24.0, -14.3, 645.1, 57.1),
                 (26.0, -27.4, 637.4, 76.9), (28.0, -44.9, 625.1, 102.2), (30.0, -65.7, 607.4, 133.1),
                 (32.0, -88.8, 583.7, 169.7), (34.0, -113.5, 553.8, 211.8), (36.0, -139.9, 517.7, 259.6),
                 (38.0, -168.0, 475.5, 312.9), (40.0, -197.7, 427.1, 371.8))   # port_fleet 7acb34ea7
LIFT_SPOOL, LIFT_OFF, MAIN_ON, MAIN_FULL = 12.0, 15.0, 19.5, 21.0


def smooth_track(keys, t):
    """Catmull-Rom through (t, x, y, z) keys, held at the ends."""
    k = np.array(keys, float)
    kt, kp = k[:, 0], k[:, 1:]
    t = np.clip(t, kt[0], kt[-1])
    i = np.clip(np.searchsorted(kt, t, side="right") - 1, 0, len(kt) - 2)
    p0, p1 = kp[np.maximum(i - 1, 0)], kp[i]
    p2, p3 = kp[i + 1], kp[np.minimum(i + 2, len(kt) - 1)]
    t0, t1 = kt[i], kt[i + 1]
    u = ((t - t0) / (t1 - t0))[:, None]
    dt = (t1 - t0)[:, None]
    m1 = (p2 - p0) / (kt[i + 1] - kt[np.maximum(i - 1, 0)])[:, None] * dt
    m2 = (p3 - p1) / (kt[np.minimum(i + 2, len(kt) - 1)] - kt[i])[:, None] * dt
    h00, h10, h01, h11 = 2 * u**3 - 3 * u**2 + 1, u**3 - 2 * u**2 + u, -2 * u**3 + 3 * u**2, u**3 - u**2
    return h00 * p1 + h10 * m1 + h01 * p2 + h11 * m2


def smoothstep(a, b, x):
    k = np.clip((x - a) / (b - a), 0.0, 1.0)
    return k * k * (3 - 2 * k)


def c5_camera(t5):
    r = smoothstep(18.0, 26.0, t5)
    x = 362.0 + (150.0 - 362.0) * r
    y = 436.0 + (430.0 - 436.0) * r
    z = 220.0 * (1800.0 / 220.0) ** r * (1 + 0.07 * smoothstep(24.0, 40.0, t5))
    return np.stack([x, y, z], axis=1)


def lighter_six(t):
    """port_life 'Lighter 6 return': a Hermes letting down onto pad (95, 412) from 200 m between
    C5 10 and 22, about 27 m/s at first and slowing smoothly to touchdown."""
    s5 = np.clip((t - C4_SECONDS - 10.0) / 12.0, 0.0, 1.0)
    z = 200.0 * (1.0 - s5) ** 1.6
    ship = np.stack([np.full_like(t, 95.0), np.full_like(t, 412.0), z], axis=1)
    return ship - c5_camera(t - C4_SECONDS)


def colossus_heavy(t):
    t5 = t - C4_SECONDS
    return smooth_track(COLOSSUS_KEYS, t5) - c5_camera(t5)


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
# Thruster sizes (owner, 2026-10-19, from audition_engines.py): SMALL is a small thruster close
# (audition 01); MEDIUM_RANGE a medium drive muffled by distance or hull (03); HEAVY_RANGE a heavy
# drive at range (02); the generated c4_launch_burn takes are a medium thruster close (t01) and a
# single heavy thruster (t02).
SMALL = dict(muffle_near=900.0, bass=2.6, crackle=0.9, rush_gain=0.1)
MEDIUM_RANGE = dict(muffle_far=70.0, muffle_near=320.0, bass=2.2, crackle=0.4, rush_gain=0.15)
HEAVY_RANGE = dict(muffle_far=120.0, muffle_near=180.0, bass=2.4, crackle=0.45, rush_gain=0.0)
FIGHTER = dict(HOUND, start=0.0, end=7.0, sound_speed=330.0, muffle_far=150.0, muffle_near=2600.0,
               bass=0.8, crackle=0.6, rush_gain=0.7, rush_low=250.0, rush_high=5000.0,
               distance_power=2.0, pan="c2", pan_width=1.0)
FIGHTER = dict(FIGHTER, **SMALL)                       # Broadswords: small thrusters
LEGION = dict(HOUND, start=0.0, end=15.0, sound_speed=340.0, muffle_far=60.0, muffle_near=380.0,
              bass=3.0, crackle=0.25, rush_gain=0.05, distance_power=1.4, pan="c2", pan_width=0.5)
# The two ships trim to look at each other (c2_bridge_blockout): the Legion's attitude thrusters
# fire in bursts along its hull while it closes and as it slows to the beam.
LEGION_RCS = ((1.6, 0.5, 0.8), (3.4, 0.35, 0.6), (5.1, 0.8, 1.0), (6.3, 0.4, 0.7), (7.2, 1.1, 1.0),
              (8.9, 0.45, 0.8), (10.4, 0.7, 0.9), (12.0, 0.5, 0.7))
PASSES = {
    "hound_flyby_a": dict(HOUND, **SMALL),                # the customs Hound: a small thruster
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
    # Its main drives: the generated single heavy thruster (c4_launch_burn_t02) flown along the hull.
    "legion_drives": dict(LEGION, path=legion_at(-130.0), seed=21, source="c4_launch_burn_t02"),
    "legion_forward": dict(LEGION, path=legion_at(90.0, up_m=-10.0), seed=22, rcs=LEGION_RCS, **HEAVY_RANGE),
    # The fighters: two vics of three, a hard thruster roar each, abeam at 3.0 and 4.2 s.
    **{"fighter_%d%d" % (flight, n): dict(FIGHTER, path=fighter(t_pass + back / 160.0, out + dy, up + dz),
                                          seed=30 + 3 * flight + n)
       for flight, (t_pass, out, up) in enumerate(((3.0, 55.0, 7.0), (4.2, 80.0, 9.0)))
       for n, (back, dy, dz) in enumerate(((0.0, 0.0, 0.0), (22.0, -14.0, -2.0), (22.0, 14.0, 1.5)))},
    # Second Wind's own drive from its bridge: idling under the room hum, then the de-orbit burn
    # lights at 9.6 s and the same engine opens up, on through the entry and into C3's first
    # seconds (C2 0-15, entry 15-22, C3 22-24).
    # C5: the Colossus at the heavy berth lifting out about 480 m from the camera (port_fleet): its
    # ten lift jets spool up from 12.0 (40%), full at 15.0 as it leaves the deck, and its six mains
    # light at 19.5 (30%), full by 21.0, as it turns south-west and climbs away. Both are heavy thrusters: the
    # generated heavy burn (audition 05), muffled by distance, and the dark code-built drive.
    "colossus_lift": dict(HOUND, start=C4_SECONDS + 11.5, end=C4_SECONDS + 40.0, path=colossus_heavy,
                          source="c4_launch_burn_t02", sound_speed=340.0, muffle_far=90.0, muffle_near=300.0,
                          bass=3.0, crackle=0.0, rush_gain=0.0, distance_power=1.2, pan="c1", pan_width=0.6, seed=51,
                          thrust=[(C4_SECONDS + LIFT_SPOOL - 0.1, 0.0), (C4_SECONDS + LIFT_SPOOL, 0.4),
                                  (C4_SECONDS + LIFT_OFF, 1.0), (C4_SECONDS + 40.0, 1.0)]),
    "colossus_mains": dict(HOUND, start=C4_SECONDS + MAIN_ON - 0.5, end=C4_SECONDS + 40.0, path=colossus_heavy,
                           sound_speed=340.0, distance_power=1.2, pan="c1", pan_width=0.6, seed=53, **HEAVY_RANGE,
                           thrust=[(C4_SECONDS + MAIN_ON - 0.1, 0.0), (C4_SECONDS + MAIN_ON, 0.3),
                                   (C4_SECONDS + MAIN_FULL, 1.0), (C4_SECONDS + 40.0, 1.0)],
                           thumps=(C4_SECONDS + MAIN_ON,)),
    # C4 (joined C4-C5 clock): Second Wind seen from the sky camera, 450-800 m above it. Its main drive
    # burns full until touchdown at 16.4 and is out by 17.9; the belly lift jets light at 10.0, are full
    # 12.0-16.4 and out by 17.4 (thrust_shots.mark_c4). Burning-gas rockets, broadband: no hum.
    "second_wind_c4_drive": dict(HOUND, start=0.0, end=19.4, path=fixed(0.0, 60.0, -600.0), sound_speed=340.0,
                                 muffle_far=320.0, muffle_near=320.0, bass=2.2, crackle=0.4, rush_gain=0.0,
                                 distance_power=1.0, pan_width=0.0, seed=61,         # medium, at range
                                 thrust=[(0.0, 0.0), (1.0, 0.8), (16.4, 0.8), (17.9, 0.0)]),
    "second_wind_c4_lift": dict(HOUND, start=9.5, end=18.4, path=fixed(0.0, 60.0, -500.0), sound_speed=340.0,
                                # Owner, 2026-10-19: the heavy take (audition 05) "muted a bit": twice
                                # low-passed at 2.2 kHz, lit at 10.0, flat, cut just after touchdown.
                                source="c4_launch_burn_t02",
                                muffle_far=2200.0, muffle_near=2200.0, bass=1.6, crackle=0.0, rush_gain=0.0,
                                distance_power=1.0, pan_width=0.1, seed=62,
                                thrust=[(9.5, 0.0), (10.0, 0.0), (10.3, 1.0), (16.52, 1.0), (16.65, 0.0)]),
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
    """The engine as a launch sounds (owner, 2026-10-19: "bass with burning rocket fuel like a shuttle
    launch", not compressed air): a deep turbulent rumble that surges and breathes, and the crackle
    of burning exhaust as separate shock pops. Returns (rumble, crackle) so distance can dull the
    rumble and the crackle differently."""
    # A brown rumble with a pink roar on top: the roar is the 100-800 Hz body a launch has.
    rumble = coloured(n, rng, 2.0) + 0.7 * coloured(n, rng, 1.0)
    # Nothing below about 28 Hz: it is felt in a theatre at best, and here it only ate the headroom.
    for _ in range(3):
        rumble = rumble - one_pole(rumble, 28.0)
    # Turbulence: the rumble surges and breathes a few times a second instead of holding steady.
    surge = coloured(n, rng, 2.0)
    surge = surge - surge.mean()
    surge = one_pole(surge, 4.0)
    surge /= max(1e-9, np.abs(surge).max())
    rumble = rumble * np.clip(1.0 + 0.45 * surge, 0.25, None)
    # Saturation: a launch overdrives the air and every microphone near it.
    rumble = np.tanh(3.0 * rumble / max(1e-9, np.abs(rumble).max()))
    # Crackle: dense shock waves, each a hard positive spike with a slower negative recovery, with
    # heavy-tailed sizes so a few pops cut through the roar.
    hits = (rng.random(n) < 500.0 / SR) * (rng.pareto(2.0, n) + 0.3)
    k = np.arange(int(0.006 * SR))
    shock = np.exp(-k / (0.00035 * SR)) - 0.55 * np.exp(-k / (0.0018 * SR))
    pops = np.convolve(hits, shock)[:n]
    pops /= max(1e-9, np.abs(pops).max())
    return rumble, crackle * pops


TAKES = Path(__file__).resolve().parent / "sfx-takes"


def take_loop(name, n):
    """A generated engine take (sfx-takes/<name>.mp3) as an engine source: mono, looped with
    one-second crossfades to n samples, so a recorded-sounding burn can still fly a path."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(TAKES / (name + ".mp3")), "-ac", "1", "-ar", str(SR),
                          "-f", "f32le", "-"], check=True, capture_output=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)
    x = x[int(0.3 * SR):len(x) - int(0.3 * SR)]           # skip the take's own fade in and out
    fade = min(SR, len(x) // 4)
    ramp = np.linspace(0, 1, fade)
    out = x.copy()
    while len(out) < n:
        out = np.concatenate([out[:-fade], out[-fade:] * ramp[::-1] + x[:fade] * ramp, x[fade:]])
    out = out[:n]
    return out / max(1e-9, np.abs(out).max())


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
    rumble, pops = rocket(int((te.max() - origin + 0.2) * SR), rng, spec["crackle"])
    if spec.get("source"):
        rumble, pops = take_loop(spec["source"], len(rumble)), np.zeros(len(rumble))
    at = (te - origin) * SR
    grid = np.arange(len(rumble))
    voice, crack = np.interp(at, grid, rumble), np.interp(at, grid, pops)
    corner = spec["muffle_far"] + (spec["muffle_near"] - spec["muffle_far"]) * near
    muffled = one_pole(one_pole(voice, corner), corner)                     # two poles: properly dull
    sub = one_pole(one_pole(voice, 70.0), 70.0)
    for _ in range(2):
        sub = sub - one_pole(sub, 28.0)
    # The crackle carries further than the rumble's top end: dulled by distance, never removed.
    crack_corner = np.minimum(4000.0, 3.0 * corner + 300.0)
    crack = one_pole(one_pole(crack, crack_corner), crack_corner)
    crack *= spec["crackle"] / max(1e-9, np.abs(crack).max())
    engine = muffled / max(1e-9, np.abs(muffled).max()) + 0.45 * spec["bass"] * sub / max(1e-9, np.abs(sub).max())
    engine = engine / max(1e-9, np.abs(engine).max()) + 1.4 * crack * np.sqrt(near)
    engine = np.tanh(1.6 * engine / max(1e-9, np.abs(engine).max()))

    noise = coloured(len(t), rng, 1.0)
    low = one_pole(noise, spec["rush_low"])
    band = one_pole(noise - low, spec["rush_low"] + (spec["rush_high"] - spec["rush_low"]) * near)
    # Kept low: a strong air rush is what made the old engines read as compressed air.
    rush = 0.4 * spec["rush_gain"] * band / max(1e-9, np.abs(band).max()) * near

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
