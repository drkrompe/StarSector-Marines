"""Regrades a covered farm sheet's grow-light from ImageGen's magenta to warm sodium amber (owner, 2026-11-10: "I'm not
a fan of the pink/purple"; picked "Warm amber"). Reads `<look>.raw.png`, writes `<look>.amber.png` beside it, which the
look's tileset-authoring document exports. Geometry and alpha are untouched; only pink-to-violet pixels move.

Strong glow (saturated) takes amber at its own brightness; weak spill (a faint pink or salmon bloom on a roof) is
neutralised to a warm grey instead, so it reads as lit surface rather than orange speckle.

Usage: python art-source/tilesets/grow-light-amber.py hydro-sheds hydro-domes glasshouses
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent


def regrade(src):
    im = np.asarray(Image.open(src).convert('RGBA')).astype(np.float32) / 255
    rgb, a = im[..., :3], im[..., 3:]
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx, mn = rgb.max(-1), rgb.min(-1)
    d = np.maximum(mx - mn, 1e-6)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    hue = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60
    # Pink to violet: 265-360 degrees; and the pale salmon bloom the light spills on a roof (0-20 degrees, faint
    # only, so rust and copper of the same hue keep their colour).
    spill = np.clip((22 - hue) / 6, 0, 1) * (hue < 22) * np.clip((0.32 - sat) / 0.08, 0, 1)
    in_hue = np.clip(np.clip((hue - 265) / 15, 0, 1) + spill, 0, 1)
    weight = in_hue * np.clip((sat - 0.012) / 0.05, 0, 1)
    glow = np.clip((sat - 0.18) / 0.25, 0, 1)[..., None]
    lum = (0.3 * r + 0.5 * g + 0.2 * b + mx) / 2
    amber = np.stack([mx, mx * 0.70, mx * 0.30], -1)
    grey = np.stack([lum * 1.02, lum, lum * 0.94], -1)
    target = np.clip(amber * glow + grey * (1 - glow), 0, 1)
    out = rgb * (1 - weight[..., None]) + target * weight[..., None]
    return Image.fromarray((np.concatenate([out, a], -1) * 255).round().astype(np.uint8))


if __name__ == '__main__':
    for look in sys.argv[1:]:
        regrade(HERE / f'{look}.raw.png').save(HERE / f'{look}.amber.png')
