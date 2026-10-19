"""Code-built sound for Second Wind's landing on Kilo Nine at the end of C4.

  python art-source/company-prologue/second-wind-cinematic/arrival-audio/synth_landing.py [--out DIR]

The owner asked for this one synthesised rather than generated (2026-10-19). It follows the
picture: timing is read straight out of `industrial-exterior/c4x_scene.py` (ARRIVE, T_DOWN,
HOVER_Z, REST_Z, DURATION and the lift jets' throttle, `lift_throttle`), so a retimed C4 only
needs this re-run. Five stems on C4's clock, each starting at its own `start`, plus their sum:

- `landing_roar`: the belly jets. A rocket roar (`synth_flyby.rocket`) lit two seconds before
  the hover and held through the descent. As the hull sinks the jets hit the pad: the roar
  opens up and roughens, and a sub-bass ground rumble rises under it.
- `landing_grit`: dust and grit blasting off the slab: a sand-blast hiss whose grain follows
  the downwash, and pebbles pinging off metal (the yard's vehicles at their stand-off).
- `landing_thump`: touchdown. A deep hit, the legs' second contact a moment later, and the
  struts taking the weight with a groan.
- `landing_spool`: the jets cutting out: the roar's pitch and brightness falling away, a vent
  hiss, and a turbine whine winding down.
- `landing_settle`: what the dust does after: a soft, thinning wash and the last grit landing.

Output is regenerable, so it goes under build/; `sfx_mix.py` places it. Seeded and deterministic.
Requires numpy.
"""
import argparse
import ast
from pathlib import Path

import numpy as np

import synth_flyby as fb

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SCENE = HERE.parent / "industrial-exterior" / "c4x_scene.py"
OUT = ROOT / "build" / "audio-previews" / "cinematic-sfx" / "synth"
SR = fb.SR
LIFT_ON, LIFT_OFF = 2.0, 1.0          # as c4x_scene.LIFT_ON / LIFT_OFF


def scene_constants(path=SCENE):
    """The top-level numeric constants of the C4 scene, evaluated in order (no Blender needed)."""
    names = {}
    for node in ast.parse(path.read_text(encoding="utf-8")).body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
            try:
                value = eval(compile(ast.Expression(node.value), str(path), "eval"), {"__builtins__": {}}, dict(names))
            except Exception:
                continue
            if isinstance(value, (int, float)):
                names[node.targets[0].id] = float(value)
    return names


C = scene_constants()
ARRIVE, T_DOWN, DURATION = C["ARRIVE"], C["T_DOWN"], C["DURATION"]
HOVER_Z, REST_Z = C["HOVER_Z"], C["REST_Z"]
LIFT_ON, LIFT_OFF = C.get("LIFT_ON", LIFT_ON), C.get("LIFT_OFF", LIFT_OFF)
START = ARRIVE - LIFT_ON - 0.5         # every stem starts here, on C4's clock
END = DURATION


def smoothstep(a, b, x):
    x = np.clip((x - a) / (b - a), 0.0, 1.0)
    return x * x * (3 - 2 * x)


def throttle(t):
    return smoothstep(ARRIVE - LIFT_ON, ARRIVE, t) * (1 - smoothstep(T_DOWN, T_DOWN + LIFT_OFF, t))


def height(t):
    """The ship's root above the slab (c4x_scene.ship_path once it hovers)."""
    return np.where(t < ARRIVE, HOVER_Z, HOVER_Z + (REST_Z - HOVER_Z) * smoothstep(ARRIVE, T_DOWN, t))


def ground_effect(t):
    """How hard the jets are hitting the pad: nothing at sixty metres up, everything on the slab."""
    return throttle(t) * (1 - smoothstep(10.0, 70.0, height(t) - 8.0))


def band(x, lo, hi):
    """A soft band-pass: two poles under hi, one-pole high-pass at lo."""
    y = x - fb.one_pole(x, lo)
    return fb.one_pole(fb.one_pole(y, hi), hi)


def norm(x, peak=1.0):
    return x / max(1e-9, np.abs(x).max()) * peak


def pings(t, rng, rate, lo=1800.0, hi=5200.0, decay=(0.008, 0.03)):
    """Small hard things hitting metal: short inharmonic rings, density rate(t) per second."""
    n = len(t)
    out = np.zeros(n)
    density = rate(t)
    for i in np.flatnonzero(rng.random(n) < density / SR):
        f0, tau = rng.uniform(lo, hi), rng.uniform(*decay)
        k = np.arange(int(tau * 6 * SR)) / SR
        ring = np.sin(2 * np.pi * f0 * k) + 0.5 * np.sin(2 * np.pi * f0 * 2.76 * k + rng.uniform(0, 6))
        ring *= np.exp(-k / tau) * rng.uniform(0.2, 1.0)
        j = min(n, i + len(ring))
        out[i:j] += ring[: j - i]
    return out


def roar(t, rng):
    g, thr = ground_effect(t), throttle(t)
    # After cut-off the jets' voice slows: read the source on a clock that runs down with the throttle.
    rate = 0.55 + 0.45 * np.clip(thr, 0, 1) ** 0.5
    clock = np.cumsum(rate) / SR
    src = fb.rocket(int((clock[-1] + 0.5) * SR), rng, 0.55)
    voice = np.interp(clock * SR, np.arange(len(src)), src)
    corner = 260.0 + 1500.0 * g + 300.0 * thr
    body = fb.one_pole(fb.one_pole(voice, corner), corner)
    sub = fb.one_pole(fb.one_pole(voice, 70.0), 70.0)
    rumble = fb.one_pole(fb.one_pole(fb.coloured(len(t), rng, 2.0), 45.0), 45.0)
    mono = norm(body) * (0.55 + 0.45 * g) + 0.9 * norm(sub) + 1.1 * norm(rumble) * g
    return mono * thr ** 1.3


def grit(t, rng):
    g = ground_effect(t)
    after = np.exp(-np.clip(t - T_DOWN - LIFT_OFF, 0, None) / 0.5)     # the blast stops with the jets
    blast = g * after
    hiss = band(fb.coloured(len(t), rng, 0.6), 1500.0, 7000.0)
    grain = fb.one_pole(np.abs(rng.standard_normal(len(t))), 35.0)      # the hiss is grainy, not smooth
    hiss = norm(hiss) * (0.5 + 1.2 * norm(grain))
    patter = pings(t, rng, lambda t: 260.0 * blast ** 1.5)
    return norm(hiss) * blast ** 1.2 * 0.8 + norm(patter) * 0.5


def thump(t, rng):
    out = np.zeros(len(t))
    for when, gain in ((T_DOWN, 1.0), (T_DOWN + 0.17, 0.55)):
        k = np.clip(t - when, 0, None)
        on = t >= when
        sweep = 2 * np.pi * (34.0 * k + 30.0 * 0.12 * (1 - np.exp(-k / 0.12)))   # 64 Hz falling to 34
        hit = np.sin(sweep) * np.exp(-k / 0.42)
        crunch = fb.one_pole(rng.standard_normal(len(t)), 900.0) * np.exp(-k / 0.06)
        out += gain * on * (hit + 0.35 * norm(crunch))
    # The struts taking the weight: a slow, inharmonic metal groan.
    k = np.clip(t - T_DOWN - 0.08, 0, None)
    on = t >= T_DOWN + 0.08
    groan = sum(a * np.sin(2 * np.pi * f * k * (1 - 0.06 * np.minimum(k, 1.2)))
                for f, a in ((97.0, 1.0), (151.0, 0.6), (233.0, 0.35), (412.0, 0.15)))
    out += 0.22 * on * groan * smoothstep(0.0, 0.15, k) * np.exp(-k / 0.7)
    return out


def spool(t, rng):
    k = np.clip(t - T_DOWN, 0, None)
    on = t >= T_DOWN
    vent = band(fb.coloured(len(t), rng, 0.8), 2500.0, 9000.0)
    vent = norm(vent) * on * smoothstep(0.0, 0.25, k) * np.exp(-k / 1.1)
    f = 180.0 + 720.0 * np.exp(-k / 0.9)                                  # the turbine winding down
    phase = 2 * np.pi * np.cumsum(f) / SR
    whine = (np.sin(phase) + 0.3 * np.sin(2.01 * phase)) * on * np.exp(-k / 1.6) * (1 - np.exp(-k / 0.05))
    return 0.8 * vent + 0.12 * whine


def settle(t, rng):
    k = np.clip(t - T_DOWN - 0.5, 0, None)
    on = t >= T_DOWN + 0.5
    wash = band(fb.coloured(len(t), rng, 1.2), 300.0, 2400.0)
    shape = on * smoothstep(0.0, 0.8, k) * (1 - smoothstep(0.0, DURATION - T_DOWN - 1.0, k))
    last = pings(t, rng, lambda t: 30.0 * np.exp(-np.clip(t - T_DOWN - 1.0, 0, None) / 1.2) * (t > T_DOWN + 0.6),
                 900.0, 3000.0, (0.01, 0.04))
    return norm(wash) * shape * 0.7 + norm(last) * 0.35


STEMS = {"landing_roar": (roar, 11), "landing_grit": (grit, 12), "landing_thump": (thump, 13),
         "landing_spool": (spool, 14), "landing_settle": (settle, 15)}
# How loud each stem sits in the sum, relative to the roar.
BALANCE = {"landing_roar": 1.0, "landing_grit": 0.55, "landing_thump": 1.0, "landing_spool": 0.45,
           "landing_settle": 0.3}
# Stereo: the roar and thump fill the middle; grit and settling dust are spread wide.
WIDTH = {"landing_roar": 0.15, "landing_grit": 0.7, "landing_thump": 0.0, "landing_spool": 0.3,
         "landing_settle": 0.8}


def stereo(mono, width, rng):
    """Decorrelated sides: a few ms of slowly wandering delay on one channel."""
    if width <= 0:
        return np.stack([mono, mono], axis=1)
    lag = (0.004 + 0.003 * np.sin(2 * np.pi * 0.21 * np.arange(len(mono)) / SR + rng.uniform(0, 6))) * SR
    other = np.interp(np.arange(len(mono)) - lag, np.arange(len(mono)), mono)
    mid, side = (mono + other) / 2, (mono - other) / 2 * width * 2
    return np.stack([mid + side, mid - side], axis=1)


def render():
    t = START + np.arange(int(round((END - START) * SR))) / SR
    edge = np.minimum(1, np.minimum(t - t[0], t[-1] - t) / 0.05)[:, None]
    stems = {}
    for name, (fn, seed) in STEMS.items():
        rng = np.random.default_rng(seed)
        stems[name] = stereo(norm(fn(t, rng)), WIDTH[name], rng) * edge
    total = sum(BALANCE[n] * s / max(1e-9, np.abs(s).max()) for n, s in stems.items())
    stems["landing_synth"] = total
    return {n: norm(s, 0.89) for n, s in stems.items()}


def build(out=OUT):
    for name, samples in render().items():
        fb.write(samples, out / (name + ".wav"))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(OUT))
    out = build(Path(parser.parse_args().out))
    print("C4 clock: start %.2f s, hover %.2f, touchdown %.2f, end %.2f" % (START, ARRIVE, T_DOWN, END))
    for name in list(STEMS) + ["landing_synth"]:
        print("Wrote", out / (name + ".wav"))


if __name__ == "__main__":
    main()
