"""Cuts the volcanic ground's additive overlays from their ImageGen masters (VolcanicGroundRenderSystem).

`lava-fissures.png` is a 4x2 sheet on black (four cracks, two vents, two ember scatters) and `ash-drifts.png` a 2x2
sheet of pale ash on black. Each sprite is cut from its grid cell, trimmed to what is lit with a margin, squared
about its centre (the renderer rotates them) and shrunk; black stays black, which adds nothing when drawn additively.
Run from the repository root:

    python art-source/tilesets/volcanic-ground/derive_overlays.py
"""
import os
import numpy as np
from PIL import Image

SRC = 'art-source/tilesets/volcanic-ground/'
OUT = 'mod/graphics/battle/volcanic/'
# Anything dimmer than this is the background's noise, not light: cleared to black.
FLOOR = 10


def cut(master, cols, rows, names, side):
    img = np.asarray(Image.open(SRC + master).convert('RGB')).astype(np.float32)
    h, w, _ = img.shape
    cw, ch = w // cols, h // rows
    for k, name in enumerate(names):
        cx, cy = k % cols, k // cols
        cell = img[cy * ch:(cy + 1) * ch, cx * cw:(cx + 1) * cw].copy()
        lum = cell.max(axis=2)
        cell[lum < FLOOR] = 0
        ys, xs = np.nonzero(lum >= FLOOR)
        my, mx = (ys.min() + ys.max()) / 2, (xs.min() + xs.max()) / 2
        half = max(ys.max() - ys.min(), xs.max() - xs.min()) / 2 + 12
        pad = int(half) + 1
        big = np.pad(cell, ((pad, pad), (pad, pad), (0, 0)))
        y0, x0 = int(my - half) + pad, int(mx - half) + pad
        sq = big[y0:y0 + int(2 * half), x0:x0 + int(2 * half)]
        # Fade the outermost band to black, so no cut edge ever shows.
        n = sq.shape[0]
        t = np.minimum(np.arange(n), np.arange(n)[::-1]) / 10.0
        fade = np.clip(np.minimum.outer(t, t), 0, 1)[..., None]
        out = Image.fromarray(np.clip(sq * fade, 0, 255).astype(np.uint8)).resize((side, side), Image.LANCZOS)
        out.save(OUT + name + '.png', optimize=True)
        print(name, sq.shape[:2], '->', side)


os.makedirs(OUT, exist_ok=True)
cut('lava-fissures.png', 4, 2, ['fissure-1', 'fissure-2', 'fissure-3', 'fissure-4', 'vent-1', 'vent-2',
                                 'embers-1', 'embers-2'], 256)
cut('ash-drifts.png', 2, 2, ['ash-drift-1', 'ash-drift-2', 'ash-drift-3', 'ash-drift-4'], 256)
