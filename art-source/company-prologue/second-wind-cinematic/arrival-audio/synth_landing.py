"""Code-built sound for Second Wind's landing on Kilo Nine at the end of C4.

  python art-source/company-prologue/second-wind-cinematic/arrival-audio/synth_landing.py [--out DIR]

The owner asked for this one synthesised rather than generated (2026-10-19). It follows the
picture: timing is read straight out of `industrial-exterior/c4x_scene.py` (ARRIVE, T_DOWN,
HOVER_Z, REST_Z, DURATION and the lift jets' throttle, `lift_throttle`), so a retimed C4 only
needs this re-run. Five stems on C4's clock, each starting at its own `start`, plus their sum:

Everything is noise, shaped: no oscillators and no resonant filters anywhere, since anything
pitched read as wind chimes or an electronic engine (owner, 2026-10-19: "Burning gas thrusters -
heavy lift").

- `landing_roar`: the belly jets burning. Ragged broadband combustion with crackle, a chest-deep
  low end, turbulence, and pressure surges as the throttle trims the descent; a whump as they
  light. Near the pad it buffets and hardens with the roar thrown back off the slab.
- `landing_grit`: dust and grit stripped off the slab: a grainy hiss and gravel rattling.
- `landing_thump`: touchdown: a deep shove, the legs' crunch, the second pair a moment later.
- `landing_spool`: the burners shutting down: sputtering coughs, pops, and gas hissing off.
- `landing_settle`: the dust coming back down: a thinning wash and the odd bit of grit.

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


def lp2(x, cutoff):
    """Two plain one-pole low-passes: dull, never resonant."""
    return fb.one_pole(fb.one_pole(x, cutoff), cutoff)


def hp(x, cutoff):
    return x - fb.one_pole(x, cutoff)


def flutter(n, rng, rate):
    """A slow random wobble, about +-1, changing at roughly `rate` times a second."""
    return norm(lp2(rng.standard_normal(n), rate))


def grains(t, rng, density, length=(0.002, 0.006), lo=600.0, hi=7000.0):
    """Gravel and grit: very short bursts of filtered noise (no pitch), density(t) per second."""
    n = len(t)
    out = np.zeros(n)
    for i in np.flatnonzero(rng.random(n) < density(t) / SR):
        k = int(rng.uniform(*length) * SR)
        g = rng.standard_normal(k) * np.exp(-np.arange(k) / (0.3 * k)) * rng.pareto(2.5)
        j = min(n, i + k)
        out[i:j] += g[: j - i]
    return band(out, lo, hi)


def crackle(t, rng, density):
    """Combustion crackle: sharp broadband clicks of both signs with heavy-tailed sizes."""
    n = len(t)
    hits = (rng.random(n) < density / SR) * rng.choice((-1.0, 1.0), n) * rng.pareto(2.0, n)
    kernel = np.exp(-np.arange(int(0.003 * SR)) / (0.0006 * SR))
    return hp(np.convolve(hits, kernel)[:n], 300.0)


def bump(t, at, width):
    return np.exp(-0.5 * ((t - at) / width) ** 2)


def cutoff_gate(t):
    """The burners cut cleanly a moment after the legs touch; the shutdown stem carries the rest."""
    return 1 - smoothstep(T_DOWN + 0.12, T_DOWN + 0.2, t)


def roar(t, rng):
    """Heavy-lift gas burners: ragged broadband combustion, no pitch anywhere. Felt as pressure
    low down, torn and crackling higher up; it buffets and hardens as the pad throws it back."""
    n = len(t)
    g, thr = ground_effect(t), throttle(t)
    # Throttle trims on the way down: pressure surges as the pilot holds the descent.
    trims = np.ones(n)
    for k, at in enumerate(np.linspace(ARRIVE + 0.6, T_DOWN - 0.5, 5)):
        trims += rng.uniform(0.18, 0.4) * bump(t, at + rng.uniform(-0.3, 0.3), rng.uniform(0.15, 0.3))
    lit = smoothstep(ARRIVE - LIFT_ON, ARRIVE - LIFT_ON + 0.25, t)
    ignition = bump(t, ARRIVE - LIFT_ON + 0.15, 0.12)                    # the whump as they light
    level = np.maximum(thr, 0.6 * lit * (t < ARRIVE)) * trims * cutoff_gate(t)

    turb = 1 + 0.35 * flutter(n, rng, 22.0) + 0.25 * flutter(n, rng, 7.0)
    buffet = 1 + 0.8 * g * flutter(n, rng, 4.5)                         # the ground pushing back
    torn = norm(lp2(fb.coloured(n, rng, 1.0) + 0.5 * rng.standard_normal(n), 1400.0 + 4200.0 * g))
    chest = norm(lp2(fb.coloured(n, rng, 2.0), 95.0)) * (1 + 0.5 * flutter(n, rng, 6.0))
    floor = norm(lp2(fb.coloured(n, rng, 2.0), 38.0)) * g               # deep ground rumble near the pad
    blast = norm(hp(rng.standard_normal(n), 1800.0)) * g ** 2            # the pad's reflected roar
    crack = norm(crackle(t, rng, 120.0 + 520.0 * g)) * (0.5 + 0.5 * g)
    body = 0.9 * torn * turb + 1.1 * chest + 0.9 * floor + 0.35 * blast + 0.45 * crack
    whump = norm(lp2(fb.coloured(n, rng, 2.0), 120.0)) * ignition * 2.0
    return body * buffet * level + whump


def grit(t, rng):
    """Dust and grit stripped off the slab: a hiss with grain, and gravel rattling on metal."""
    n = len(t)
    g = ground_effect(t) * (1 - smoothstep(T_DOWN + 0.12, T_DOWN + 0.6, t))
    hiss = band(rng.standard_normal(n), 900.0, 6500.0)
    grain = 0.35 + norm(np.abs(lp2(rng.standard_normal(n), 90.0)))
    rattle = grains(t, rng, lambda _: 940.0 * g ** 1.4)
    return norm(hiss * grain) * g ** 1.2 + 0.6 * norm(rattle)


def thump(t, rng):
    """Touchdown, all noise: a deep shove into the slab, the legs' crunch, the second legs after."""
    n = len(t)
    out = np.zeros(n)
    deep = norm(lp2(fb.coloured(n, rng, 2.0), 90.0))
    crunch = norm(lp2(rng.standard_normal(n), 1600.0))
    clunk = norm(band(rng.standard_normal(n), 120.0, 500.0))
    for when, gain in ((T_DOWN, 1.0), (T_DOWN + 0.17, 0.6)):
        k = np.clip(t - when, 0, None)
        on = (t >= when) * smoothstep(0.0, 0.006, k)
        out += gain * on * (1.3 * deep * np.exp(-k / 0.35) + 0.5 * crunch * np.exp(-k / 0.05)
                            + 0.5 * clunk * np.exp(-k / 0.12))
    return out


def spool(t, rng):
    """The burners shutting down: the flame sputters out in shorter, weaker coughs, a few pops,
    and the last gas hissing off the nozzles."""
    n = len(t)
    flame = norm(lp2(fb.coloured(n, rng, 1.0) + 0.3 * rng.standard_normal(n), 2400.0)) \
        + 0.9 * norm(lp2(fb.coloured(n, rng, 2.0), 110.0))
    gate = np.zeros(n)
    at, gap, length, gain = T_DOWN + 0.2, 0.06, 0.13, 0.85
    while gain > 0.08:
        gate += gain * smoothstep(at, at + 0.012, t) * (1 - smoothstep(at + length, at + length + 0.03, t))
        at += length + gap * rng.uniform(0.7, 1.4)
        gap, length, gain = gap * 1.5, length * 0.8, gain * 0.68
    out = norm(flame) * gate
    pops = np.zeros(n)
    for when in sorted(rng.uniform(T_DOWN + 0.25, T_DOWN + 1.8, 7)):
        k = np.clip(t - when, 0, None)
        on = (t >= when) * smoothstep(0.0, 0.002, k)
        pops += rng.uniform(0.4, 1.0) * on * np.exp(-k / 0.025)
    pop_noise = lp2(rng.standard_normal(n), 2800.0) + 0.8 * norm(lp2(rng.standard_normal(n), 220.0))
    out += 0.9 * norm(pop_noise) * pops
    k = np.clip(t - T_DOWN - 0.2, 0, None)
    on = t >= T_DOWN + 0.2
    gas = lp2(hp(rng.standard_normal(n), 1500.0), 1500.0 + 6500.0 * np.exp(-k / 0.8))
    out += 0.45 * norm(gas) * on * smoothstep(0.0, 0.15, k) * np.exp(-k / 1.0)
    return out


def settle(t, rng):
    """Dust coming back down: a soft, thinning wash and the odd bit of grit landing."""
    n = len(t)
    k = np.clip(t - T_DOWN - 0.5, 0, None)
    on = t >= T_DOWN + 0.5
    wash = band(fb.coloured(n, rng, 1.2), 250.0, 1800.0)
    shape = on * smoothstep(0.0, 0.8, k) * (1 - smoothstep(0.0, DURATION - T_DOWN - 1.0, k))
    last = grains(t, rng, lambda t: 25.0 * np.exp(-np.clip(t - T_DOWN - 1.0, 0, None) / 1.2) * (t > T_DOWN + 0.6),
                  (0.002, 0.004), 500.0, 4000.0)
    return norm(wash) * shape * 0.7 + norm(last) * 0.3


STEMS = {"landing_roar": (roar, 11), "landing_grit": (grit, 12), "landing_thump": (thump, 13),
         "landing_spool": (spool, 14), "landing_settle": (settle, 15)}
# How loud each stem sits in the sum, relative to the roar.
BALANCE = {"landing_roar": 1.0, "landing_grit": 0.55, "landing_thump": 1.0, "landing_spool": 0.45,
           "landing_settle": 0.3}
# Stereo: the roar and thump fill the middle; grit and settling dust are spread wide.
WIDTH = {"landing_roar": 0.15, "landing_grit": 0.7, "landing_thump": 0.0, "landing_spool": 0.3,
         "landing_settle": 0.8}


def render():
    t = START + np.arange(int(round((END - START) * SR))) / SR
    edge = np.minimum(1, np.minimum(t - t[0], t[-1] - t) / 0.05)[:, None]
    stems = {}
    for name, (fn, seed) in STEMS.items():
        # Width from two independent renders of the same stem, one per side: a delay-based widener
        # comb-filters noise into a metallic tone.
        a = norm(fn(t, np.random.default_rng(seed)))
        b = norm(fn(t, np.random.default_rng(seed + 100))) if WIDTH[name] > 0 else a
        w = WIDTH[name] / 2
        stems[name] = np.stack([(1 - w) * a + w * b, w * a + (1 - w) * b], axis=1) * edge
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
