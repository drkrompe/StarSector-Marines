"""Bake the water-shore sheet the organic shoreline draws its bands from.

Derived art, the only writer of its outputs:

    mod/graphics/tilesets/water-shore.png
    mod/data/tilesets/water-shore.tileset.json

Two blocks, three variants each, on the water sheet's own 16px grid so the
shore samples at the same texel density as the open water beside it:

* ``shore.shallow`` -- the water tiles lifted toward the sandy bed: the ring
  of shallows between the waterline and the deeper water.
* ``shore.wet`` -- a translucent dark film laid over whatever land meets the
  water, so the bank reads as wet without replacing the ground under it.

Re-run after changing ``Water_tiles.png``:

    python art-source/terrain/bake_water_shore.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WATER = ROOT / "mod/graphics/tilesets/Water_tiles.png"
SHEET = "graphics/tilesets/water-shore.png"
CELL = 16
VARIANTS = 3

# The bed the shallows show through, and how much of it they show.
BED = (178, 160, 112)
SHALLOW_MIX = 0.30
SHALLOW_LIFT = 1.04
# The wet film: a dark umber at a modest opacity, mottled so it is not a flat stroke.
WET = (38, 30, 20)
WET_ALPHA = 0.30
WET_MOTTLE = 0.10


def shallow(source: Image.Image, variant: int) -> Image.Image:
    tile = source.crop((variant * CELL, 0, (variant + 1) * CELL, CELL)).convert("RGBA")
    out = Image.new("RGBA", (CELL, CELL))
    for y in range(CELL):
        for x in range(CELL):
            r, g, b, _ = tile.getpixel((x, y))
            px = [min(255, round((c * (1 - SHALLOW_MIX) + bed * SHALLOW_MIX) * SHALLOW_LIFT))
                  for c, bed in zip((r, g, b), BED)]
            out.putpixel((x, y), (*px, 255))
    return out


def wet(variant: int) -> Image.Image:
    rng = random.Random(0x5EA5 + variant)
    out = Image.new("RGBA", (CELL, CELL))
    for y in range(CELL):
        for x in range(CELL):
            a = WET_ALPHA + rng.uniform(-WET_MOTTLE, WET_MOTTLE)
            out.putpixel((x, y), (*WET, round(255 * max(0.0, min(1.0, a)))))
    return out


def main() -> None:
    source = Image.open(WATER)
    sheet = Image.new("RGBA", (CELL * VARIANTS, CELL * 2), (0, 0, 0, 0))
    for v in range(VARIANTS):
        sheet.paste(shallow(source, v), (v * CELL, 0))
        sheet.paste(wet(v), (v * CELL, CELL))
    out = ROOT / "mod" / SHEET
    sheet.save(out)
    cells, blocks = [], []
    for row, (block, what) in enumerate((("shore.shallow", "shallows over a sandy bed"),
                                         ("shore.wet", "translucent wet film over the bank"))):
        ids = []
        for v in range(VARIANTS):
            cells.append({"col": v, "row": row, "name": f"{block} v{v + 1}",
                          "description": f"{what}, variant {v + 1}"})
            ids.append([v, row])
        blocks.append({"id": block, "cells": ids})
    catalog = {"sheet": SHEET, "cellPx": CELL, "blocks": blocks, "cells": cells, "doodads": []}
    (ROOT / "mod/data/tilesets/water-shore.tileset.json").write_text(
        json.dumps(catalog, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
