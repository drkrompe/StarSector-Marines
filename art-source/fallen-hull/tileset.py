"""The fallen hull's map tileset: its walls, its trench floor and its debris, cut from the accepted paintings.

  python art-source/fallen-hull/tileset.py

Writes mod/graphics/tilesets/fallen-hull.png (64 px cells, 12 x 6) and mod/data/tilesets/fallen-hull.tileset.json.
Hand-made, not exported by the authoring tools: re-run this script instead. No new painting goes in:

  cols 0-3, rows 0-3   `installation.dark-concrete-wall@fallen-hull`, a 'wall-faces-4x4' block (slot = mask % 4,
                       mask / 4; N=1 E=2 S=4 W=8, as `concrete-walls`) derived from the hull roof style's raw
                       (`art-source/roofs/raw/hull.raw.png`): each cell its plate field, the hull's armour rim (the roof
                       sheet's eave strip, lit lip outward) along every exposed face, so walls and roof are one
                       material. Its doors, windows and rubble are the dark concrete's; its id keeps the installation
                       wall's kind (`FreestandingWalls`) and roofs it in hull plating (`RoofStyle.ofWallSurface`).
  cols 4-5, rows 0-1   `fallen-hull.trench`, the trench floor laid by position as a 2 x 2 pattern (`patternCols`), the
                       seamless swatch of raw/trench.png.
  the rest             `fallen-hull.debris-<name>` doodads from raw/debris.png at their guide footprints.
"""
import importlib.util
import json
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ROOFS = ROOT / "art-source" / "roofs"


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


G = _load("roof_guide", ROOFS / "make_guide.py")
E = _load("roof_export", ROOFS / "export.py")
X = _load("hull_export", HERE / "export.py")

F = 64
COLS, ROWS = 12, 6
RIM = 7
WALL = "installation.dark-concrete-wall@fallen-hull"
TRENCH = "fallen-hull.trench"
SHEET = "graphics/tilesets/fallen-hull.png"
SIDES = ("north", "east", "south", "west")
# Debris doodads: name -> (col, row, cover, ballistic half height). Footprints are the guide's (guides/layout.json).
DEBRIS = {
    "pod": (6, 0, "med", 0.5),
    "bell": (9, 0, "med", 0.6),
    "plate-shard-b": (4, 2, "light", 0.2),
    "plate-shard-a": (6, 3, "light", 0.2),
    "scrap-heap": (9, 3, "med", 0.4),
    "girder": (0, 5, "light", 0.25),
}


def walls(sheet, raw):
    sx, sy = G.SWATCH
    swatch = np.asarray(raw.crop((sx, sy, sx + G.CELL * 2, sy + G.CELL * 2)))
    field = Image.fromarray(E.seamless(swatch)).resize((2 * F, 2 * F), Image.LANCZOS).convert("RGBA")
    x, y, w, h = G.STRIP
    band = raw.crop((x, y, x + w, y + h)).resize((round(w * RIM / h), RIM), Image.LANCZOS).crop((0, 0, F, RIM))
    band = band.convert("RGBA")
    cells = []
    for mask in range(16):
        col, row = mask % 4, mask // 4
        cell = field.crop(((col & 1) * F, (row & 1) * F, (col & 1) * F + F, (row & 1) * F + F))
        if mask & 1:
            cell.alpha_composite(band, (0, 0))
        if mask & 2:
            cell.alpha_composite(band.rotate(-90, expand=True), (F - RIM, 0))
        if mask & 4:
            cell.alpha_composite(band.rotate(180, expand=True), (0, F - RIM))
        if mask & 8:
            cell.alpha_composite(band.rotate(90, expand=True), (0, 0))
        sheet.paste(cell, (col * F, row * F))
        exposed = [SIDES[i] for i in range(4) if mask & (1 << i)]
        cells.append({"col": col, "row": row, "name": f"{WALL} faces-{mask}",
                      "description": "solid cap, no exposed faces" if not exposed
                      else "wall cap exposed on " + " and ".join(exposed)})
    return cells


def main():
    sheet = Image.new("RGBA", (COLS * F, ROWS * F))
    raw = Image.open(ROOFS / "raw" / "hull.raw.png").convert("RGB")
    if raw.size != (G.W, G.H):
        raw = raw.resize((G.W, G.H), Image.LANCZOS)
    cells = walls(sheet, raw)

    trench = X.sheet("trench")
    tx, ty, tw, th = X.LAYOUT["trench"]["swatch"]
    floor = Image.fromarray(X.seamless(np.asarray(trench.crop((tx, ty, tx + tw, ty + th)).convert("RGB"))))
    sheet.paste(floor.resize((2 * F, 2 * F), Image.LANCZOS).convert("RGBA"), (4 * F, 0))

    debris = X.sheet("debris")
    frames = X.LAYOUT["debris"]["frames"]
    cell_px = X.LAYOUT["debris"]["cellPx"]
    doodads = []
    for name, (col, row, cover, half) in DEBRIS.items():
        x0, y0, x1, y1 = frames[name]
        piece = X.cut(debris, (x0, y0, x1, y1), cell_px, F)
        sheet.alpha_composite(piece, (col * F, row * F))
        doodads.append({"id": f"fallen-hull.debris-{name}", "col": col, "row": row, "cover": cover,
                        "ballisticHalfHeight": half,
                        "footprintCells": [round((x1 - x0) / cell_px), round((y1 - y0) / cell_px)]})
    sheet.save(ROOT / "mod" / SHEET)
    doc = {
        "blocks": [
            {"doorBlock": "installation.dark-concrete-door", "id": WALL, "layout": "wall-faces-4x4",
             "origin": [0, 0], "rubbleBlock": "installation.dark-concrete-rubble",
             "windowBlock": "installation.dark-concrete-window"},
            {"cells": [[4, 0], [5, 0], [4, 1], [5, 1]], "id": TRENCH, "patternCols": 2},
        ],
        "cellPx": F,
        "cells": cells,
        "doodads": doodads,
        "sheet": SHEET,
    }
    (ROOT / "mod" / "data" / "tilesets" / "fallen-hull.tileset.json").write_text(json.dumps(doc, indent=2) + "\n",
                                                                                 encoding="utf-8")
    print("wrote", ROOT / "mod" / SHEET)


if __name__ == "__main__":
    main()
