"""Derives the built floors' step-up trims from their ImageGen masters (``make_guide.py``).

Each master is shrunk to 128x64 (one cell along the edge by half a cell across, the bottom row the step's edge) and its
left and right edges healed as the foundry's kerb is (``art-source/voids/foundry/derive.py``), so it repeats left to
right. Unlike the kerb it keeps its alpha: the top rows are transparent and the panel's own surface shows through
them; ImageGen's faint haze there (alpha under 8) is cleared. Run from the repository root:

    python art-source/floors/trims/derive.py
"""
import importlib.util
import os

import numpy as np
from PIL import Image

# The foundry's derivation, loaded by path: it is also named ``derive``.
_spec = importlib.util.spec_from_file_location("foundry_derive", "art-source/voids/foundry/derive.py")
foundry = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(foundry)

SRC = "art-source/floors/trims/"
OUT = "mod/graphics/battle/floors/"


def derive(name, size=(128, 64)):
    img = Image.open(SRC + name + ".png").convert("RGBA").resize(size, Image.LANCZOS)
    a = np.asarray(img).astype(np.float32)
    a[..., :3] *= a[..., 3:] / 255                     # heal premultiplied, so clear pixels bleed no colour
    a = foundry.heal(a, False)
    alpha = a[..., 3:]
    a[..., :3] = np.where(alpha > 0, a[..., :3] * 255 / np.maximum(alpha, 1), 0)
    a[a[..., 3] < 8] = 0
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(OUT + name + ".png", optimize=True)
    print(name, size, "seam x %.1f" % np.abs(a[:, 0] - a[:, -1]).mean())


if __name__ == "__main__":
    derive("raised-floor-trim")
    derive("stage-floor-trim")
