"""Cut the refinery heaters master into the shipped prop sheet and its tileset.

    python art-source/refinery/refinery-heaters-export.py

Reads refinery-heaters.raw.png (the ImageGen master over refinery-heaters-guide.png, in the works tileset's flat
style) and writes mod/graphics/doodads/refinery-heaters.png (one row of three FRAME px frames) and
mod/data/tilesets/refinery-heaters.tileset.json. The frames keep the placing RefineryGround draws them by:
  column, stripper  platform ring radius 0.20 of the frame, centre at (0.40, 0.40)
  stack             chimney ring radius 0.125, centre at (0.375, 0.375)
A neighbour reaching into a frame's edge is cut away.
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "art-source/refinery/refinery-heaters.raw.png"
SHEET = ROOT / "mod/graphics/doodads/refinery-heaters.png"
TILESET = ROOT / "mod/data/tilesets/refinery-heaters.tileset.json"
FRAME = 480

# id, col, ring centre measured on the master (px), ring radius (px), radius as frame fraction, centre fraction,
# shares of the frame's left and right edges to clear
PIECES = [
    ("refinery.column", 0, (466, 445), 88, 0.20, 0.40, (0.0, 0.06)),
    ("refinery.stripper", 1, (806, 443), 86, 0.20, 0.40, (0.05, 0.0)),
    ("refinery.stack", 2, (1132, 443), 58, 0.125, 0.375, (0.06, 0.0)),
]


def main():
    src = Image.open(SRC).convert("RGBA")
    sheet = Image.new("RGBA", (3 * FRAME, FRAME), (0, 0, 0, 0))
    cells, doodads = [], []
    for pid, col, (cx, cy), radius, fraction, at, cut in PIECES:
        side = radius / fraction
        x0, y0 = cx - at * side, cy - at * side
        box = tuple(int(round(v)) for v in (x0, y0, x0 + side, y0 + side))
        img = src.crop(box).resize((FRAME, FRAME), Image.LANCZOS)
        left, right = cut
        if left or right:
            a = np.array(img)
            a[:, :int(left * FRAME), 3] = 0
            if right:
                a[:, FRAME - int(right * FRAME):, 3] = 0
            img = Image.fromarray(a)
        sheet.alpha_composite(img, (col * FRAME, 0))
        cells.append({"col": col, "row": 0, "name": pid})
        doodads.append({"id": pid, "col": col, "row": 0, "cover": "none", "ballisticHalfHeight": 0,
                        "footprintCells": [1, 1]})
    sheet.save(SHEET, optimize=True)
    TILESET.write_text(json.dumps({"sheet": "graphics/doodads/refinery-heaters.png", "cellPx": FRAME,
                                   "cells": cells, "doodads": doodads}, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {SHEET.relative_to(ROOT)} {sheet.size} and {TILESET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
