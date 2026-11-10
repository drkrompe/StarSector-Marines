"""Derives the basalt hill finish (`TerrainPlan.Finish.BASALT`, materials `terrain.basalt.*`) from ImageGen masters.

Nine swatches in the ice terrain's reading order, a 3x3 grid of 384 px cells: summit, talus, then seven cliff-face
values from dark to light. The faces are the lava rift's columnar basalt wall (`art-source/voids/lava/basalt-wall.png`),
the summit the cooled flow ground (`volcanic-ground/basalt-flow.png`), the talus the wall's vesicular scoria broken
small. Each swatch is shifted onto a target luminance, keeping its own texture and hue. Run from the repository root:

    python art-source/tilesets/basalt-terrain/derive.py
"""
import numpy as np
from PIL import Image

WALL = 'art-source/voids/lava/basalt-wall.png'
FLOW = 'art-source/tilesets/volcanic-ground/basalt-flow.png'
OUT = 'art-source/tilesets/basalt-terrain.raw.png'
CELL = 384
# Summit, talus, faces dark to light: mean luminance of each swatch.
TARGETS = [60, 70, 26, 34, 44, 55, 67, 80, 94]
WARM = np.array([1.06, 1.0, .96], np.float32)


def load(path):
    return np.asarray(Image.open(path).convert('RGB')).astype(np.float32)


def crop(img, x, y, side):
    tile = Image.fromarray(img[y:y + side, x:x + side].astype(np.uint8)).resize((CELL, CELL), Image.LANCZOS)
    return np.asarray(tile).astype(np.float32)


def onto(tile, target):
    lum = tile @ np.array([.299, .587, .114], np.float32)
    # Scale (not shift) so the darkest crevices stay dark; a little contrast is kept per value.
    out = tile * (target / max(lum.mean(), 1)) * WARM
    return np.clip(out, 0, 255)


wall, flow = load(WALL), load(FLOW)
swatches = [onto(crop(flow, 300, 300, 620), TARGETS[0]),
            # Talus: a wide crop of the wall shrunk hard, so columns break into rubble-sized grain.
            onto(crop(wall, 0, 0, 1254), TARGETS[1])]
for i, t in enumerate(TARGETS[2:]):
    swatches.append(onto(crop(wall, 120 + 60 * i, 200 + 40 * i, 640), t))
sheet = np.zeros((CELL * 3, CELL * 3, 3), np.float32)
for i, s in enumerate(swatches):
    sheet[(i // 3) * CELL:(i // 3 + 1) * CELL, (i % 3) * CELL:(i % 3 + 1) * CELL] = s
Image.fromarray(sheet.astype(np.uint8)).save(OUT, optimize=True)
print('wrote', OUT)
