"""Bake the ford sheet: what a brook's ford shows through its shallows.

Derived art, the only writer of its outputs:

    mod/graphics/tilesets/water-ford.png
    mod/data/tilesets/water-ford.tileset.json

One block, ``shore.ford-pebbles``, eight variants: a stony bed of pebbles,
half-seen through the water, drawn on every cell of a ford over the shallows
band (``WaterShore.bedPatch``).

Every pebble keeps clear of its cell's edge, so cells never cut one in half.
Drawn at four times size and box-filtered down, so edges are smooth.

    python art-source/terrain/bake_water_ford.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SHEET = "graphics/tilesets/water-ford.png"
CELL = 64
SS = 4
VARIANTS = 8
MARGIN = 4  # px at CELL size kept clear at the edge, past the drawer's inset

PEBBLE_TONES = [(120, 112, 98), (100, 94, 84), (138, 126, 102), (88, 84, 78), (112, 100, 80)]


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


def tile(variant):
    rng = random.Random(f"pebbles-{variant}")
    layer = Image.new("RGBA", (CELL * SS, CELL * SS), (0, 0, 0, 0))
    pebbles(rng, layer)
    return layer.resize((CELL, CELL), Image.Resampling.BOX)


def main():
    sheet = Image.new("RGBA", (CELL * VARIANTS, CELL), (0, 0, 0, 0))
    cells, ids = [], []
    for v in range(VARIANTS):
        sheet.paste(tile(v), (v * CELL, 0))
        cells.append({"col": v, "row": 0, "name": f"shore.ford-pebbles v{v + 1}",
                      "description": f"ford, pebbles, variant {v + 1}"})
        ids.append([v, 0])
    sheet.save(ROOT / "mod" / SHEET)
    blocks = [{"id": "shore.ford-pebbles", "cells": ids}]
    catalog = {"sheet": SHEET, "cellPx": CELL, "blocks": blocks, "cells": cells, "doodads": []}
    (ROOT / "mod/data/tilesets/water-ford.tileset.json").write_text(
        json.dumps(catalog, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
