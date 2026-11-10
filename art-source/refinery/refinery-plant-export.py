"""Cut the refinery plant master into the shipped prop sheet and its tileset.

    python art-source/refinery/refinery-plant-export.py

Reads refinery-plant.raw.png (the ImageGen master over refinery-plant-guide.png) and writes
mod/graphics/doodads/refinery-plant.png (3x2 frames of FRAME px) and
mod/data/tilesets/refinery-plant.tileset.json.

Each piece is cropped so its footprint disc sits at a fixed place in its frame, which RefineryGround
relies on to draw a frame over the disc it stamps (see its *_SPAN constants):
  column, stripper  ring radius 0.20 of the frame, centre at (0.40, 0.40): drawn 2.5x the disc, at -disc/2
  tank              rim fills the frame:                                 drawn at the disc
  sphere            radius 0.25, centre (0.375, 0.375):                  drawn 2x the disc, at -disc/4
  flare             derrick half-width 0.125, centre (0.5, 0.5):         drawn 4x the derrick, at -1.5x
  spill             the pool's bounding square
FRAME is 480 so every drawn size the code uses (6, 10, 15, 16, 20, 32 cells) divides it.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "art-source/refinery/refinery-plant.raw.png"
SHEET = ROOT / "mod/graphics/doodads/refinery-plant.png"
TILESET = ROOT / "mod/data/tilesets/refinery-plant.tileset.json"
FRAME = 480

# Measured on the master (px): centre and the radius that is the footprint disc.
PIECES = [
    # id,                  col, row, centre,        radius, radius as frame fraction, centre fraction
    ("refinery.column",    0, 0, (447, 263), 92, 0.20, 0.40),
    ("refinery.stripper",  1, 0, (797, 280), 62, 0.20, 0.40),
    ("refinery.flare",     2, 0, (1142, 280), 48, 0.125, 0.50),
    ("refinery.tank",      0, 1, (497, 642), 155, 0.50, 0.50),
    ("refinery.sphere",    1, 1, (817, 626), 98, 0.25, 0.375),
    ("refinery.spill",     2, 1, (1177, 650), 112, 0.50, 0.50),
]


def crop(src, centre, radius, fraction, at):
    side = radius / fraction
    x0, y0 = centre[0] - at * side, centre[1] - at * side
    box = tuple(int(round(v)) for v in (x0, y0, x0 + side, y0 + side))
    return src.crop(box).resize((FRAME, FRAME), Image.LANCZOS)


def circle_mask(img, cx, cy, r0, r1):
    """Fades alpha from r0 to r1 (frame fractions) round (cx, cy): drops a neighbour's overspill."""
    a = np.array(img).astype(np.float32)
    yy, xx = np.mgrid[0:FRAME, 0:FRAME] / FRAME
    d = np.hypot(xx - cx, yy - cy)
    a[..., 3] *= np.clip((r1 - d) / (r1 - r0), 0, 1)
    return Image.fromarray(a.astype(np.uint8))


def soften_glow(img):
    """The flare's ground glow: the hard red rim the generator left goes, the rest fades out softly."""
    a = np.array(img).astype(np.float32)
    r, g, b, al = a[..., 0], a[..., 1], a[..., 2], a[..., 3]
    rim = (r > 150) & (g < 0.45 * r) & (b < 0.45 * r)
    al[rim] = 0
    yy, xx = np.mgrid[0:FRAME, 0:FRAME] / FRAME
    d = np.hypot(xx - 0.5, yy - 0.5)
    # Inside the derrick and flame keep everything; beyond it the glow thins to nothing at 0.46.
    fade = np.clip((0.46 - d) / (0.46 - 0.16), 0, 1)
    glow = al < 235
    al[glow] *= 0.55 * fade[glow]
    a[..., 3] = al
    return Image.fromarray(a.astype(np.uint8))


def main():
    src = Image.open(SRC).convert("RGBA")
    sheet = Image.new("RGBA", (3 * FRAME, 2 * FRAME), (0, 0, 0, 0))
    doodads, cells = [], []
    for pid, col, row, centre, radius, fraction, at in PIECES:
        img = crop(src, centre, radius, fraction, at)
        if pid == "refinery.flare":
            img = soften_glow(circle_mask(img, 0.5, 0.5, 0.40, 0.44))
        elif pid == "refinery.stripper":
            img = circle_mask(img, 0.40, 0.40, 0.62, 0.70)
        elif pid == "refinery.column":
            img = circle_mask(img, at, at, 0.47, 0.51)
        elif pid == "refinery.sphere":
            img = circle_mask(img, at, at, 0.58, 0.62)
        sheet.alpha_composite(img, (col * FRAME, row * FRAME))
        cells.append({"col": col, "row": row, "name": pid})
        doodads.append({"id": pid, "col": col, "row": row, "cover": "none", "ballisticHalfHeight": 0,
                        "footprintCells": [1, 1]})
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(SHEET, optimize=True)
    TILESET.write_text(json.dumps({"sheet": "graphics/doodads/refinery-plant.png", "cellPx": FRAME,
                                   "cells": cells, "doodads": doodads}, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {SHEET.relative_to(ROOT)} {sheet.size} and {TILESET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
