"""The spore colonies' shots, drawn in code: what a burst looks like in flight.

A shot sprite is drawn untinted, nose up, and stretched to the weapon's ``projectileVisualCells`` along its line of
flight, so the colour is in the pixels. Three bodies, one per grade, named after the weapons:

- ``spore-clump``: the fissure colony's spore burst. A lumpy clot of dusky membrane with a hot amber core, lobbed.
- ``spore-cluster``: the bore colony's spore flak. Three clots stuck together, bursting into three puffs.
- ``spore-lance``: the maw colony's spore lance. A long, fast, white-amber streak in a violet sheath.

The amber is the swarm's own node amber; the membrane is a dusky violet that sits with the settled Rav Bloom
lilac and crimson. The weapons' ``fx`` blocks carry the rest of the look (muzzle spit, trail, airburst,
drifting haze), in the same colours.

Run from the repository root: ``python art-source/swarm-bores/spore_shots.py``.
Writes ``mod/graphics/fx/spore-clump.png``, ``spore-cluster.png`` and ``spore-lance.png``.
"""

import math
from pathlib import Path

import numpy as np
from PIL import Image

SUPER = 4
MEMBRANE = np.array((92, 70, 104), np.float32)
MEMBRANE_DARK = np.array((44, 30, 52), np.float32)
AMBER = np.array((255, 170, 60), np.float32)
HOT = np.array((255, 236, 190), np.float32)
CREAM = np.array((226, 206, 160), np.float32)


def grid(w, h):
    ys, xs = np.mgrid[0:h * SUPER, 0:w * SUPER].astype(np.float32)
    return (xs + 0.5) / SUPER, (ys + 0.5) / SUPER


def finish(rgb, alpha, w, h):
    out = np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 1) * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA").resize((w, h), Image.Resampling.LANCZOS)


def clot(x, y, cx, cy, r, seed):
    """A lumpy disc: 0 outside, 1 at the centre, and the distance ratio for shading."""
    rng = np.random.default_rng(seed)
    a = np.arctan2(y - cy, x - cx)
    edge = r * (1 + 0.12 * np.sin(5 * a + rng.uniform(0, 6)) + 0.07 * np.sin(3 * a + rng.uniform(0, 6)))
    return np.hypot(x - cx, y - cy) / edge


def shade_clot(rgb, alpha, d, x, y, cx, cy, r, core=0.75):
    inside = d < 1
    # Lit from the upper left like everything else; darker towards the rim.
    lx, ly = (x - cx) / r, (y - cy) / r
    light = np.clip(0.75 - 0.35 * (lx + ly) * 0.7, 0.35, 1.1)
    body = MEMBRANE * light[..., None] * (1 - 0.45 * np.clip(d, 0, 1)[..., None] ** 3) \
        + MEMBRANE_DARK * 0.45 * np.clip(d, 0, 1)[..., None] ** 3
    glow = np.clip(1 - d / core, 0, 1)[..., None] ** 0.7
    colour = body * (1 - glow) + (AMBER * (1 - glow) + HOT * glow) * glow
    rgb[inside] = colour[inside]
    alpha[inside] = 1.0
    # A soft halo of spore dust outside the membrane.
    halo = np.clip(1 - (d - 1) / 0.35, 0, 1) * (d >= 1) * 0.35
    rgb[~inside & (halo > alpha)] = AMBER * 0.85
    alpha[:] = np.maximum(alpha, halo)


def spore_clump():
    w, h = 24, 28
    x, y = grid(w, h)
    rgb = np.zeros(x.shape + (3,), np.float32)
    alpha = np.zeros(x.shape, np.float32)
    d = clot(x, y, 12, 15, 7.5, 3)
    shade_clot(rgb, alpha, d, x, y, 12, 15, 7.5)
    # A tail of loose spores behind it (nose is up).
    for i, (sx, sy, sr) in enumerate(((10.5, 24.5, 1.4), (13.5, 26.0, 1.0), (12.0, 22.6, 1.6))):
        s = np.hypot(x - sx, y - sy) / sr
        m = (s < 1) & (alpha < 0.9)
        rgb[m] = CREAM
        alpha[m] = np.maximum(alpha[m], 0.85 * (1 - s[m] ** 2))
    return finish(rgb, alpha, w, h)


def spore_cluster():
    w, h = 32, 36
    x, y = grid(w, h)
    rgb = np.zeros(x.shape + (3,), np.float32)
    alpha = np.zeros(x.shape, np.float32)
    for k, (cx, cy, r) in enumerate(((16.5, 12.5, 7.2), (10.5, 21.5, 6.2), (21.5, 22.0, 6.0))):
        d = clot(x, y, cx, cy, r, 10 + k)
        layer_rgb = np.zeros_like(rgb)
        layer_a = np.zeros_like(alpha)
        shade_clot(layer_rgb, layer_a, d, x, y, cx, cy, r, core=0.65)
        take = layer_a >= alpha
        rgb[take] = layer_rgb[take]
        alpha[:] = np.maximum(alpha, layer_a)
    return finish(rgb, alpha, w, h)


def spore_lance():
    w, h = 20, 80
    x, y = grid(w, h)
    cx = w / 2
    t = np.clip((y - 4) / (h - 8), 0, 1)        # 0 at the nose, 1 at the tail
    half = 1.2 + 6.0 * np.clip(np.sin(np.pi * np.clip(t * 1.05, 0, 1)), 0, 1) ** 0.6 * (1 - t) ** 0.5
    lateral = np.abs(x - cx) / np.maximum(half, 0.5)
    inside = (lateral < 1) & (y > 3) & (y < h - 2)
    core = np.clip(1 - lateral / 0.45, 0, 1) * (1 - 0.6 * t)
    sheath = MEMBRANE * (1 - 0.5 * t)[..., None] + MEMBRANE_DARK * (0.5 * t)[..., None]
    colour = sheath * (1 - core[..., None]) + (AMBER * (1 - core[..., None] ** 2) + HOT * core[..., None] ** 2) * core[..., None]
    rgb = np.where(inside[..., None], colour, AMBER * 0.85)
    fade = np.clip(1 - t * 0.85, 0, 1)
    alpha = np.where(inside, fade, np.clip(1 - (lateral - 1) / 0.6, 0, 1) * 0.35 * fade)
    nose = np.hypot(x - cx, y - 4.5) / 2.6
    hot = nose < 1
    rgb[hot] = HOT
    alpha[hot] = 1.0
    return finish(rgb, alpha, w, h)


def main():
    out = Path(__file__).resolve().parent.parent.parent / "mod" / "graphics" / "fx"
    out.mkdir(parents=True, exist_ok=True)
    for name, make in (("spore-clump", spore_clump), ("spore-cluster", spore_cluster), ("spore-lance", spore_lance)):
        image = make()
        image.save(out / f"{name}.png", optimize=True)
        print(out / f"{name}.png", image.size)


if __name__ == "__main__":
    main()
