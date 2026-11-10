"""Cut the refinery utilities master into the shipped prop sheet and its tileset.

    python art-source/refinery/refinery-utilities-export.py

Reads refinery-utilities.raw.png (the ImageGen master over refinery-utilities-guide.png) and writes
mod/graphics/doodads/refinery-utilities.png (3x2 frames of FRAME px) and
mod/data/tilesets/refinery-utilities.tileset.json.

Each piece is cropped to a square around a measured centre, so RefineryGround can lay a frame over the ground its
fixture takes (its *_SPAN constants):
  stack   chimney ring radius 0.125 of the frame, centre at (0.375, 0.375): drawn 4x the chimney, at -chimney
  tower   rim fills the frame
  skid    the skid a band across the middle, rows 0.32 to 0.68
  mast    base half-width 0.10, centre (0.5, 0.5): drawn 5x the base, at -2x
  island  the concrete island down the middle, arms folded to the right; the frame is turned to lie along a bay
  gas     the rack and cabinet a band across the middle, rows 0.24 to 0.77
FRAME is 480 so every drawn size the code uses (4, 6, 8, 10, 16 cells) divides it.
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "art-source/refinery/refinery-utilities.raw.png"
SHEET = ROOT / "mod/graphics/doodads/refinery-utilities.png"
TILESET = ROOT / "mod/data/tilesets/refinery-utilities.tileset.json"
FRAME = 480

# id, col, row, centre measured on the master (px), crop side (px), where the centre falls across the crop
PIECES = [
    ("refinery.stack", 0, 0, (372, 236), 360, 0.375),
    ("refinery.cooling-tower", 1, 0, (828, 265), 310, 0.5),
    ("refinery.pump-skid", 2, 0, (1237, 266), 372, 0.5),
    ("refinery.mast", 0, 1, (407, 680), 400, 0.5),
    ("refinery.loading-island", 1, 1, (855, 678), 372, 0.5),
    ("refinery.gas-rack", 2, 1, (1233, 647), 280, 0.5),
]


def main():
    src = Image.open(SRC).convert("RGBA")
    sheet = Image.new("RGBA", (3 * FRAME, 2 * FRAME), (0, 0, 0, 0))
    cells, doodads = [], []
    for pid, col, row, (cx, cy), side, at in PIECES:
        x0, y0 = cx - at * side, cy - at * side
        box = tuple(int(round(v)) for v in (x0, y0, x0 + side, y0 + side))
        img = src.crop(box).resize((FRAME, FRAME), Image.LANCZOS)
        sheet.alpha_composite(img, (col * FRAME, row * FRAME))
        cells.append({"col": col, "row": row, "name": pid})
        doodads.append({"id": pid, "col": col, "row": row, "cover": "none", "ballisticHalfHeight": 0,
                        "footprintCells": [1, 1]})
    sheet.save(SHEET, optimize=True)
    TILESET.write_text(json.dumps({"sheet": "graphics/doodads/refinery-utilities.png", "cellPx": FRAME,
                                   "cells": cells, "doodads": doodads}, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {SHEET.relative_to(ROOT)} {sheet.size} and {TILESET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
