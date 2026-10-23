"""Bake the ford sheet: what a brook's ford shows through its shallows.

Derived art, the only writer of its outputs:

    mod/graphics/tilesets/water-ford.png
    mod/data/tilesets/water-ford.tileset.json

Two blocks, eight variants each, drawn per cell over the shallows band
(``WaterShore.bedPatch``); ``-Dbattle.render.fordLook`` picks which are drawn:

* ``shore.ford-pebbles`` -- a stony bed of pebbles, half-seen through the water,
  on every cell of the ford.
* ``shore.ford-stones``  -- one big flat stepping stone, dry-topped and
  wet-rimmed with a ripple of foam, on the line across the ford's middle
  (``WaterShore.isSteppingStone``).

Every stone keeps clear of its cell's edge, so cells never cut one in half.
Drawn at four times size and box-filtered down, so edges are smooth.

    python art-source/terrain/bake_water_ford.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SHEET = "graphics/tilesets/water-ford.png"
CELL = 64
SS = 4
VARIANTS = 8
MARGIN = 4  # px at CELL size kept clear at the edge, past the drawer's inset

PEBBLE_TONES = [(120, 112, 98), (100, 94, 84), (138, 126, 102), (88, 84, 78), (112, 100, 80)]
STONE_TOP = (176, 172, 160)
STONE_BODY = (104, 100, 92)
STONE_WET = (40, 38, 36)
FOAM = (230, 242, 248)


def blob(rng, cx, cy, r, points=9, rough=0.18):
    """An irregular rounded polygon about (cx, cy)."""
    phase = rng.uniform(0, math.tau)
    squash = rng.uniform(0.72, 1.0)
    turn = rng.uniform(0, math.pi)
    out = []
    for i in range(points):
        a = phase + i * math.tau / points
        rr = r * (1 + rng.uniform(-rough, rough))
        x, y = math.cos(a) * rr, math.sin(a) * rr * squash
        out.append((cx + x * math.cos(turn) - y * math.sin(turn), cy + x * math.sin(turn) + y * math.cos(turn)))
    return out


def scaled(poly):
    return [(x * SS, y * SS) for x, y in poly]


def pebbles(rng, layer):
    d = ImageDraw.Draw(layer)
    for _ in range(rng.randint(10, 20)):
        r = rng.uniform(2.0, 4.5)
        cx = rng.uniform(MARGIN + r, CELL - MARGIN - r)
        cy = rng.uniform(MARGIN + r, CELL - MARGIN - r)
        tone = rng.choice(PEBBLE_TONES)
        alpha = rng.randint(110, 160)
        d.polygon(scaled(blob(rng, cx, cy, r, 7, 0.2)), fill=(*tone, alpha))
        # A glint on the upper left, where the water lets the light through.
        d.polygon(scaled(blob(rng, cx - r * 0.3, cy - r * 0.3, r * 0.4, 6, 0.1)),
                  fill=(min(255, tone[0] + 40), min(255, tone[1] + 40), min(255, tone[2] + 36), alpha // 2))


def stone(rng, layer):
    r = rng.uniform(19, 22)
    jitter = max(0.0, CELL / 2 - MARGIN - r * 1.22)
    cx = CELL / 2 + rng.uniform(-jitter, jitter)
    cy = CELL / 2 + rng.uniform(-jitter, jitter)
    shape = blob(rng, cx, cy, r, 12, 0.12)
    # A ripple of foam where the current meets the stone.
    foam = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    ImageDraw.Draw(foam).polygon(scaled(blob(rng, cx, cy, r * 1.2, 13, 0.08)), fill=(*FOAM, 140))
    layer.alpha_composite(foam.filter(ImageFilter.GaussianBlur(SS * 1.4)))
    d = ImageDraw.Draw(layer)
    d.polygon(scaled([(x + 1.6, y + 2.0) for x, y in shape]), fill=(*STONE_WET, 255))
    d.polygon(scaled(shape), fill=(*STONE_BODY, 255))
    top = [(cx + (x - cx) * 0.8 - r * 0.07, cy + (y - cy) * 0.76 - r * 0.09) for x, y in shape]
    d.polygon(scaled(top), fill=(*STONE_TOP, 255))
    for _ in range(9):
        sx, sy = cx + rng.uniform(-r * 0.55, r * 0.55), cy + rng.uniform(-r * 0.5, r * 0.4)
        d.ellipse([(sx - 0.7) * SS, (sy - 0.7) * SS, (sx + 0.7) * SS, (sy + 0.7) * SS], fill=(*STONE_BODY, 210))


def tile(look, variant):
    rng = random.Random(f"{look}-{variant}")
    layer = Image.new("RGBA", (CELL * SS, CELL * SS), (0, 0, 0, 0))
    (pebbles if look == "pebbles" else stone)(rng, layer)
    return layer.resize((CELL, CELL), Image.Resampling.BOX)


def main():
    looks = ["pebbles", "stones"]
    sheet = Image.new("RGBA", (CELL * VARIANTS, CELL * len(looks)), (0, 0, 0, 0))
    cells, blocks = [], []
    for row, look in enumerate(looks):
        ids = []
        for v in range(VARIANTS):
            sheet.paste(tile(look, v), (v * CELL, row * CELL))
            cells.append({"col": v, "row": row, "name": f"shore.ford-{look} v{v + 1}",
                          "description": f"ford, {look}, variant {v + 1}"})
            ids.append([v, row])
        blocks.append({"id": f"shore.ford-{look}", "cells": ids})
    sheet.save(ROOT / "mod" / SHEET)
    catalog = {"sheet": SHEET, "cellPx": CELL, "blocks": blocks, "cells": cells, "doodads": []}
    (ROOT / "mod/data/tilesets/water-ford.tileset.json").write_text(
        json.dumps(catalog, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
