"""Derives the volcanic formations sheet from the planet formations' accepted ImageGen sheet.

Columns 0-1 of `planet-formations.raw.png` (boulders, spires; rows intact, damaged, destroyed) are regraded from grey
rock to dark vesicular basalt with a faint warm cast; the alpha is untouched, so every cut and collision mask of the
rock formations holds for the basalt ones (`MineralFormation.BASALT_BOULDERS`, `BASALT_SPIRES`). Cracks on the damaged
and destroyed rows (the darkest pixels of a broken body) get a dull ember glow, as fresh basalt broken open shows.
Run from the repository root:

    python art-source/tilesets/volcanic-formations/derive.py
"""
import numpy as np
from PIL import Image

SRC = 'art-source/tilesets/planet-formations.raw.png'
OUT = 'art-source/tilesets/volcanic-formations.raw.png'
# Columns 0-1 of the source sheet end before the ice blocks' frame at x=763.
WIDTH = 724
# Rows in the source: intact from 0, damaged from 362, destroyed from 724.
DAMAGED_FROM = 362

img = np.asarray(Image.open(SRC).convert('RGBA')).astype(np.float32)[:, :WIDTH]
rgb, a = img[..., :3], img[..., 3:]
lum = (rgb @ np.array([.299, .587, .114], np.float32))[..., None]
# Grey rock spans roughly 40-200 in luminance; basalt sits low, keeping the facets' shading as relief.
t = np.clip(lum / 255.0, 0, 1)
basalt = np.array([30, 27, 27], np.float32) + t * np.array([78, 70, 66], np.float32)
out = basalt
# Ember in the cracks of a broken body: the darkest opaque pixels below the intact row.
h = out.shape[0]
broken = (np.arange(h) >= DAMAGED_FROM)[:, None, None]
crack = np.clip((0.22 - t) / 0.12, 0, 1) * broken * (a > 128)
ember = np.array([196, 74, 26], np.float32)
out = out * (1 - crack * .8) + ember * crack * .8
res = np.concatenate([np.clip(out, 0, 255), a], axis=2).astype(np.uint8)
Image.fromarray(res, 'RGBA').save(OUT, optimize=True)
print('wrote', OUT, res.shape)
