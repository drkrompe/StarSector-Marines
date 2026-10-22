"""Turns the ImageGen detail masters (textures/generated/<name>_v<N>.png) into the kit's tiles
(build/asharu-wrecks/tiles/<name>_tile.png, ignored: regenerate, never commit): seamless, 1024 px, and centred on 0.5 so the materials' linear-light
blend adds the detail without shifting the wreck's colour.

  python make_tiles.py [--plating textures/generated/plating_v1.png] [--structure ...]

Seamless by the half-offset cross-fade: the master is blended with itself rolled by half its size,
weighted so the edges come wholly from the rolled copy (whose edges are the master's middle, and
continuous across the wrap). Chroma is kept at a quarter, so the tile is mostly a luminance signal."""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
SIZE = 1024
CHROMA = .25
CONTRAST = 1.0


def tile(src, out):
    im = np.asarray(Image.open(src).convert('RGB').resize((SIZE, SIZE), Image.LANCZOS), dtype=np.float64) / 255.0
    h, w, _ = im.shape
    y = np.sin(np.pi * (np.arange(h) + .5) / h)[:, None]
    x = np.sin(np.pi * (np.arange(w) + .5) / w)[None, :]
    wgt = (y * x) ** .6
    rolled = np.roll(np.roll(im, h // 2, 0), w // 2, 1)
    t = im * wgt[..., None] + rolled * (1 - wgt[..., None])
    lum = t.mean(axis=2, keepdims=True)
    t = lum + (t - lum) * CHROMA
    t = .5 + (t - t.mean(axis=(0, 1))) * CONTRAST
    t = np.clip(t, 0, 1)
    Image.fromarray((t * 255 + .5).astype(np.uint8)).save(out)
    print('TILE', out, 'mean', t.mean(axis=(0, 1)).round(3), 'std', t.std().round(3))


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    for name in ('plating', 'structure'):
        ap.add_argument('--' + name, default=str(HERE / 'textures' / 'generated' / ('%s_v1.png' % name)))
    a = ap.parse_args()
    for name in ('plating', 'structure'):
        out = HERE.parents[2] / 'build' / 'asharu-wrecks' / 'tiles'
        out.mkdir(parents=True, exist_ok=True)
        tile(getattr(a, name), out / ('%s_tile.png' % name))
