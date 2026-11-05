"""Repaint the Hegemony vehicle line's sheets for other factions (hegemony-armor-line.md, liveries).

The Hegemony sheets are the masters: tan plate, olive faces and chipped orange hazard panels. A livery recolours
them per pixel and never moves one: alpha, outlines, lighting and painted wear come from the master, so every frame
keeps its registration. The hazard paint is found by colour, the master's one strongly saturated paint (orange, at
saturation above about 0.6); the plate by its milder tan.

  independent  hazard panels a sea blue, plate a little cooler and greyer.
  pirates      hazard panels a dried-blood red, plate darker, browner and dirtier, with soot and chipped paint
               from a fixed-seed noise so the same hull is dirty the same way every build.

Writes <sheet>-<livery>.png beside each master under mod/graphics/battle/vehicles/.
Run: python art-source/vehicles/liveries/paint.py [--preview DIR]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
VEHICLES = REPO / "mod" / "graphics" / "battle" / "vehicles"

SHEETS = (
    "army-apc", "mortar-carrier", "lrm-carrier", "heg-medium-tank", "heg-heavy-repulsor",
    "turret-head-hegemony", "turret-head-hegemony-wreck", "turret-barbette-hegemony", "turret-barbette-hegemony-wreck",
)
LIVERIES = ("independent", "pirates")


def smoothstep(x, lo, hi):
    t = np.clip((x - lo) / (hi - lo), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def rgb_to_hsv(rgb):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx = rgb.max(-1)
    mn = rgb.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-6
    rm = nz & (mx == r)
    gm = nz & (mx == g) & ~rm
    bm = nz & ~rm & ~gm
    h[rm] = ((g - b)[rm] / d[rm]) % 6
    h[gm] = (b - r)[gm] / d[gm] + 2
    h[bm] = (r - g)[bm] / d[bm] + 4
    h = h / 6.0
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0)
    return np.stack([h, s, mx], -1)


def hsv_to_rgb(hsv):
    h, s, v = hsv[..., 0] % 1.0, hsv[..., 1], hsv[..., 2]
    i = np.floor(h * 6).astype(int) % 6
    f = h * 6 - np.floor(h * 6)
    p = v * (1 - s)
    q = v * (1 - s * f)
    t = v * (1 - s * (1 - f))
    out = np.zeros(h.shape + (3,))
    for k, (a, b, c) in enumerate(((v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q))):
        m = i == k
        out[..., 0][m], out[..., 1][m], out[..., 2][m] = a[m], b[m], c[m]
    return out


def noise(shape, cell, seed):
    """Smooth value noise in [0, 1]: a seeded coarse grid scaled up bilinearly."""
    rng = np.random.default_rng(seed)
    gh, gw = shape[0] // cell + 2, shape[1] // cell + 2
    grid = Image.fromarray((rng.random((gh, gw)) * 255).astype(np.uint8))
    big = grid.resize((gw * cell, gh * cell), Image.Resampling.BICUBIC)
    return np.asarray(big, dtype=float)[: shape[0], : shape[1]] / 255.0


def paint(image: Image.Image, livery: str, seed: int) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA"), dtype=float) / 255.0
    rgb, alpha = rgba[..., :3], rgba[..., 3:]
    hsv = rgb_to_hsv(rgb)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    warm = smoothstep(h, 0.0, 0.02) * (1 - smoothstep(h, 0.11, 0.14))
    hazard = warm * smoothstep(s, 0.56, 0.70) * smoothstep(v, 0.28, 0.42)
    plate = warm * smoothstep(s, 0.18, 0.30) * (1 - hazard)
    out = hsv.copy()
    if livery == "independent":
        out[..., 0] = np.where(hazard > 0, h + (0.585 - h) * hazard, out[..., 0])
        out[..., 1] = s * (1 - 0.20 * hazard)
        out[..., 2] = v * (1 - 0.18 * hazard)
        # The plate keeps half its tan: a grey, faded civilian finish that reads apart from the Hegemony's sand.
        out[..., 1] = out[..., 1] * (1 - 0.50 * plate)
        out[..., 2] = out[..., 2] * (1 - 0.04 * plate)
        result = hsv_to_rgb(out)
    elif livery == "pirates":
        out[..., 0] = np.where(hazard > 0, (h + (-0.008 - h) * hazard), out[..., 0])
        out[..., 1] = np.clip(s * (1 + 0.10 * hazard), 0, 1)
        out[..., 2] = v * (1 - 0.32 * hazard)
        # The plate darker and browner, as if never washed.
        out[..., 0] = out[..., 0] - 0.012 * plate
        out[..., 1] = out[..., 1] * (1 + 0.10 * plate)
        out[..., 2] = out[..., 2] * (1 - 0.22 * plate)
        result = hsv_to_rgb(out)
        shape = rgb.shape[:2]
        painted = np.clip(hazard + plate, 0, 1)[..., None]
        # Soot and grease: broad, soft, dark, over everything painted.
        soot = smoothstep(noise(shape, 40, seed), 0.50, 0.90)[..., None]
        result = result * (1 - 0.40 * soot * painted)
        # Chipped paint: small, sharp flecks of bare steel.
        chips = smoothstep(noise(shape, 3, seed + 1) * noise(shape, 20, seed + 2), 0.50, 0.58)[..., None]
        bare = np.array([0.36, 0.35, 0.33]) * (0.5 + 0.7 * v[..., None])
        result = result * (1 - 0.75 * chips * painted) + bare * 0.75 * chips * painted
        # Rust weeping from a few seams.
        rust = smoothstep(noise(shape, 10, seed + 3) * noise(shape, 36, seed + 4), 0.40, 0.55)[..., None] * painted
        result = result * (1 - 0.55 * rust) + np.array([0.30, 0.14, 0.06]) * 0.55 * rust
    else:
        raise ValueError(livery)
    return Image.fromarray((np.clip(np.concatenate([result, alpha], -1), 0, 1) * 255).round().astype(np.uint8))


def main() -> None:
    preview = Path(sys.argv[sys.argv.index("--preview") + 1]) if "--preview" in sys.argv else None
    for i, name in enumerate(SHEETS):
        master = Image.open(VEHICLES / f"{name}.png")
        for livery in LIVERIES:
            out = paint(master, livery, seed=1009 + i)
            target = (preview or VEHICLES) / f"{name}-{livery}.png"
            out.save(target)
            print("wrote", target.name)


if __name__ == "__main__":
    main()
