"""Cut the refinery works props master into the shipped utilities sheet and its tileset.

    python art-source/refinery/refinery-utilities-export.py

Reads refinery-works.raw.png (the ImageGen master over refinery-works-guide.png, in the works tileset's flat style)
and writes mod/graphics/doodads/refinery-utilities.png (3x2 frames of FRAME px) and
mod/data/tilesets/refinery-utilities.tileset.json. The guide set each piece in a square frame on a fixed grid, so
each frame is cut on that grid scaled to the master's size, and keeps the placing RefineryGround draws it by:
  heater  the firebox the full frame wide over rows 0.125 to 0.875: drawn 8 cells from a row above its box
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
SRC = ROOT / "art-source/refinery/refinery-works.raw.png"
SHEET = ROOT / "mod/graphics/doodads/refinery-utilities.png"
TILESET = ROOT / "mod/data/tilesets/refinery-utilities.tileset.json"
FRAME = 480
# The guide's grid (refinery-works-guide.py): a 1920x1080 canvas, GUIDE_FRAME px frames PITCH px apart.
GUIDE_W, GUIDE_FRAME, PITCH = 1920, 384, 420
LEFT, TOP = (1920 - 2 * PITCH - GUIDE_FRAME) // 2, (1080 - PITCH - GUIDE_FRAME) // 2

PIECES = [
    ("refinery.heater", 0, 0),
    ("refinery.cooling-tower", 1, 0),
    ("refinery.pump-skid", 2, 0),
    ("refinery.mast", 0, 1),
    ("refinery.loading-island", 1, 1),
    ("refinery.gas-rack", 2, 1),
]


def main():
    src = Image.open(SRC).convert("RGBA")
    s = src.width / GUIDE_W
    sheet = Image.new("RGBA", (3 * FRAME, 2 * FRAME), (0, 0, 0, 0))
    cells, doodads = [], []
    for pid, col, row in PIECES:
        x0, y0 = (LEFT + col * PITCH) * s, (TOP + row * PITCH) * s
        box = tuple(int(round(v)) for v in (x0, y0, x0 + GUIDE_FRAME * s, y0 + GUIDE_FRAME * s))
        sheet.alpha_composite(src.crop(box).resize((FRAME, FRAME), Image.LANCZOS), (col * FRAME, row * FRAME))
        cells.append({"col": col, "row": row, "name": pid})
        doodads.append({"id": pid, "col": col, "row": row, "cover": "none", "ballisticHalfHeight": 0,
                        "footprintCells": [1, 1]})
    sheet.save(SHEET, optimize=True)
    TILESET.write_text(json.dumps({"sheet": "graphics/doodads/refinery-utilities.png", "cellPx": FRAME,
                                   "cells": cells, "doodads": doodads}, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {SHEET.relative_to(ROOT)} {sheet.size} and {TILESET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
