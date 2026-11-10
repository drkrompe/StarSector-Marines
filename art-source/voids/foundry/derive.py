"""Derives the foundry drop's shipped textures from their ImageGen masters (``ruins-forge-area``, ``FOUNDRY``).

Each master is shrunk to its shipped size and its seams healed as the lava rift's are (``art-source/voids/lava``):
a band along each repeating edge is blended with the master rolled by half its size. The masters were drawn over
guides that repeat exactly (``make_guide.py``), so the rolled copy's bricks and joints fall where the master's do and
the blend meets brick to brick. Run from the repository root:

    python art-source/voids/foundry/derive.py

- ``firebrick-wall.png``: 512 square, repeats both ways; one copy is the drop's ``wallTileMeters``.
- ``foundry-lip.png``: 128x64, one cell along the edge by half a cell across, repeats left to right only; the bottom
  row is the drop's edge. The master is cut to its guide's drawn rows first (the guide leaves a transparent margin
  under the nosing), so the nosing reaches the bottom row, and the strip ships opaque.
"""
import numpy as np
from PIL import Image

SRC = "art-source/voids/foundry/"
OUT = "mod/graphics/battle/voids/"
# The width of the blended band along each healed edge, as a share of the side.
BAND = .07


def ramp(n):
    t = np.arange(n, dtype=np.float32) / (n - 1)
    e = np.clip(np.minimum(t, 1 - t) / BAND, 0, 1)
    return e * e * (3 - 2 * e)


def heal(a, both):
    h, w, _ = a.shape
    rolled = np.roll(a, w // 2, axis=1)
    keep = np.tile(ramp(w)[None, :], (h, 1))
    if both:
        rolled = np.roll(rolled, h // 2, axis=0)
        keep = np.minimum(keep, ramp(h)[:, None])
    keep = keep[..., None]
    return a * keep + rolled * (1 - keep)


def derive(name, size, both, mode, rows=1.0):
    img = Image.open(SRC + name + ".png").convert(mode)
    img = img.crop((0, 0, img.width, round(img.height * rows))).resize(size, Image.LANCZOS)
    a = heal(np.asarray(img).astype(np.float32), both)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), mode).save(OUT + name + ".png", optimize=True)
    print(name, size, "seam x %.1f" % np.abs(a[:, 0] - a[:, -1]).mean(),
          "inner %.1f" % np.abs(a[:, 1:] - a[:, :-1]).mean())


if __name__ == "__main__":
    derive("firebrick-wall", (512, 512), True, "RGB")
    # The lip guide draws rows 0-500 of 512 (``make_guide.lip``).
    derive("foundry-lip", (128, 64), False, "RGB", rows=501 / 512)
