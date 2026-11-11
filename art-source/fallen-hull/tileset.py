"""The fallen hull's map tileset: its walls, its trench floor and its debris, cut from the accepted paintings.

  python art-source/fallen-hull/tileset.py

Writes mod/graphics/tilesets/fallen-hull.png (64 px cells, 16 x 22) and mod/data/tilesets/fallen-hull.tileset.json.
Hand-made, not exported by the authoring tools: re-run this script instead. No new painting goes in; every piece is
cut mechanically at its guide frame (`make_guide.py`) out of an accepted raw:

  cols 0-3, rows 0-3   `installation.dark-concrete-wall@fallen-hull`, the hull's outer armour wall, a 'wall-faces-4x4'
                       block (slot = mask % 4, mask / 4; N=1 E=2 S=4 W=8, as `concrete-walls`) from raw/armour.png:
                       an armour cap with a lip on each exposed face and a spine along each joined one. Its id keeps
                       the installation wall's kind (`FreestandingWalls`) and roofs it in hull plating
                       (`RoofStyle.ofWallSurface`).
  cols 12-15, rows 0-3 `fallen-hull.armour-breach`, its rubble block: the sixteen masks blown open into torn roots.
  cols 12-13, rows 4-5 `fallen-hull.armour-hatch`, its door, and cols 14-15 `fallen-hull.armour-port`, its window
                       ('cardinal-2x2', N E / S W).
  cols 4-5, rows 0-1   `fallen-hull.trench`, the trench floor laid by position as a 2 x 2 pattern (`patternCols`), the
                       seamless swatch of raw/trench.png.
  cols 0-11, rows 0-5  `fallen-hull.debris-<name>` doodads from raw/debris.png at their guide footprints.
  rows 6-12            the hull entered (FITTINGS): its interior dressing from raw/fittings.png. A rib or conduit run
                       ships its painting's middle cell, its ends cross-faded so it joins end to end; a `-v` piece is
                       its `-h` turned a quarter.
  cols 4-7, rows 9-12  `fallen-hull.sheared-wall`, the armour cut through at a section's broken end (raw/sheared.png),
                       and beside it `fallen-hull.breach-edge` and `fallen-hull.shear-edge`, the middle cells of the
                       breach lip and sheared-end spill runs, the hull at the image's top.
  rows 13-21           the debris shower and the interior's wreckage (SHOWER) from raw/shower.png.
  aliases              the ground's names for pieces already cut (ALIASES): `fallen-hull.armour-wall` is the armour
                       wall's block again, `fallen-hull.breach-spill` and the `fallen-hull.interior-*` pieces are
                       fittings and wreckage the ground scatters walkable, so their cover is none.
"""
import importlib.util
import json
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


X = _load("hull_export", HERE / "export.py")

F = 64
COLS, ROWS = 16, 22
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


ARMOUR_DOOR = "fallen-hull.armour-hatch"
ARMOUR_WINDOW = "fallen-hull.armour-port"
ARMOUR_RUBBLE = "fallen-hull.armour-breach"
# The armour's cells on the sheet, and how far inside each painted cell the cut stays, guide px: ImageGen outlines
# every cell, and the outline must not come between two joined wall cells.
WALL_AT, BREACH_AT, HATCH_AT, PORT_AT = (0, 0), (12, 0), (12, 4), (14, 4)
INSET = 7
# Fittings: id -> (frame name, col, row, turn, cover, ballistic half height). A run's frame is three cells; the
# middle ships. turn: quarter turns clockwise of the painted piece.
FITTINGS = {
    "rib-h": ("rib-run", 0, 6, 0, "none", 0.0),
    "rib-v": ("rib-run", 1, 6, 1, "none", 0.0),
    "conduit-h": ("conduit-run", 2, 6, 0, "none", 0.1),
    "conduit-v": ("conduit-run", 3, 6, 1, "none", 0.1),
    "conduit-junction": ("conduit-junction", 4, 6, 0, "light", 0.3),
    "survey-console": ("survey-console", 5, 6, 0, "light", 0.4),
    "breach-scatter": ("breach-scatter", 6, 6, 0, "none", 0.0),
    "survey-rack-h": ("survey-rack", 7, 6, 0, "med", 0.6),
    "forge-hopper": ("forge-hopper", 9, 6, 0, "med", 0.5),
    "forge-cables": ("forge-cables", 11, 6, 0, "none", 0.1),
    "crushed-bulkhead-a": ("crushed-bulkhead-a", 13, 6, 0, "med", 0.5),
    "survey-rack-v": ("survey-rack", 0, 7, 1, "med", 0.6),
    "crushed-bulkhead-b": ("crushed-bulkhead-b", 1, 7, 0, "med", 0.5),
    "sensor-drum": ("sensor-drum", 4, 7, 0, "med", 0.6),
    "forge-coolant": ("forge-coolant", 6, 7, 0, "heavy", 0.7),
    "breach-plating": ("breach-plating", 8, 7, 0, "light", 0.3),
    "hangar-cradle": ("hangar-cradle", 10, 7, 0, "light", 0.3),
    "hangar-cradle-v": ("hangar-cradle", 0, 9, 1, "light", 0.3),
}
RUNS = ("rib-run", "conduit-run")
SHEARED = "fallen-hull.sheared-wall"
SHEARED_AT = (4, 9)
EDGES_AT = {"breach-edge": (8, 9), "shear-edge": (9, 9)}
# Shower: name -> (col, row, cover, ballistic half height); footprints from the guide.
SHOWER = {
    "shower-shard-a": ("shard-a", 0, 13, "none", 0.0), "shower-shard-b": ("shard-b", 1, 13, "none", 0.0),
    "shower-shard-c": ("shard-c", 2, 13, "none", 0.0), "shower-plate-a": ("plate-a", 3, 13, "light", 0.2),
    "interior-cable-fall": ("cable-fall", 5, 13, "none", 0.0),
    "interior-collapsed-panel-wall": ("collapsed-panel", 6, 13, "none", 0.0),
    "crater-small": ("crater-small", 8, 13, "none", 0.0), "shower-plate-b": ("plate-b", 10, 13, "light", 0.2),
    "shower-pod": ("pod", 12, 13, "med", 0.5), "interior-buckled-deck": ("buckled-deck", 14, 13, "none", 0.0),
    "shower-slab": ("slab", 0, 15, "med", 0.6), "shower-girders": ("girders", 4, 15, "med", 0.4),
    "shower-bell": ("bell", 7, 15, "heavy", 0.8), "scorch-splash": ("scorch-splash", 10, 15, "none", 0.0),
    "interior-scorch": ("scorch-splash", 13, 15, "none", 0.0),
    "shower-mast": ("mast", 0, 18, "med", 0.4), "scorch-streak": ("scorch-streak", 5, 18, "none", 0.0),
    "crater-large": ("crater-large", 9, 18, "none", 0.0),
}
# interior-scorch is the scorch splash drawn two cells across, for a deck.
RESIZED = {"interior-scorch": (2, 2)}
# The ground's names (`HullSite` layout code) for pieces already cut: alias -> (piece, cover, ballistic half height).
ALIASES = {
    "breach-spill": ("breach-plating", "none", 0.0),
    "interior-rib-h": ("rib-h", "none", 0.0), "interior-rib-v": ("rib-v", "none", 0.0),
    "interior-conduit-h": ("conduit-h", "none", 0.0), "interior-conduit-v": ("conduit-v", "none", 0.0),
    "interior-cables": ("forge-cables", "none", 0.0), "interior-scatter": ("breach-scatter", "none", 0.0),
    "interior-console-wall": ("survey-console", "none", 0.0),
    "interior-junction-wall": ("conduit-junction", "none", 0.0),
    "interior-rack-wall": ("survey-rack-h", "none", 0.0),
    "interior-crushed-bulkhead-wall": ("crushed-bulkhead-a", "none", 0.0),
}


def sheared(sheet):
    """The sheared wall's sixteen cells, and the breach and shear edges: each run's middle cell, tiling along x."""
    raw = X.sheet("sheared")
    lay = X.LAYOUT["sheared"]
    c, pitch = lay["cellPx"], lay["pitch"]
    cells = []
    for mask in range(16):
        col, row = mask % 4, mask // 4
        x, y = lay["walls"][0] + col * pitch, lay["walls"][1] + row * pitch
        piece = raw.crop((x + INSET, y + INSET, x + c - INSET, y + c - INSET)).resize((F, F), Image.LANCZOS)
        sheet.paste(piece, ((SHEARED_AT[0] + col) * F, (SHEARED_AT[1] + row) * F))
        exposed = [SIDES[i] for i in range(4) if mask & (1 << i)]
        cells.append({"col": SHEARED_AT[0] + col, "row": SHEARED_AT[1] + row, "name": f"{SHEARED} faces-{mask}",
                      "description": "armour cap" if not exposed
                      else "armour sheared through on " + " and ".join(exposed)})
    doodads = []
    for name, (col, row) in EDGES_AT.items():
        run = name.split("-")[0]
        piece = run_cell(X.cut(raw, lay["runs"][run], c, F))
        sheet.alpha_composite(piece, (col * F, row * F))
        doodads.append({"id": f"fallen-hull.{name}", "col": col, "row": row, "cover": "none",
                        "ballisticHalfHeight": 0.0, "footprintCells": [1, 1]})
    return cells, doodads


def shower(sheet):
    raw = X.sheet("shower")
    lay = X.LAYOUT["shower"]
    doodads = []
    for name, (frame, col, row, cover, half) in SHOWER.items():
        piece = X.cut(raw, lay["frames"][frame], lay["cellPx"], F)
        if name in RESIZED:
            w, h = RESIZED[name]
            piece = piece.resize((w * F, h * F), Image.LANCZOS)
        sheet.alpha_composite(piece, (col * F, row * F))
        doodads.append({"id": f"fallen-hull.{name}", "col": col, "row": row, "cover": cover,
                        "ballisticHalfHeight": half,
                        "footprintCells": [round(piece.width / F), round(piece.height / F)]})
    return doodads


def aliases(doodads):
    by_id = {d["id"]: d for d in doodads}
    out = []
    for name, (piece, cover, half) in ALIASES.items():
        d = dict(by_id[f"fallen-hull.{piece}"])
        d.update({"id": f"fallen-hull.{name}", "cover": cover, "ballisticHalfHeight": half})
        out.append(d)
    return out


def armour(sheet):
    """The armour wall, its breach roots and its openings, each cell cut inside its painted outline."""
    raw = X.sheet("armour")
    lay = X.LAYOUT["armour"]
    c, pitch = lay["cellPx"], lay["pitch"]

    def cell(origin, col, row, inset):
        x, y = origin[0] + col * pitch, origin[1] + row * pitch
        return raw.crop((x + inset, y + inset, x + c - inset, y + c - inset)).resize((F, F), Image.LANCZOS)

    cells = []
    for mask in range(16):
        col, row = mask % 4, mask // 4
        piece = cell(lay["walls"], col, row, INSET)
        sheet.paste(piece, ((WALL_AT[0] + col) * F, (WALL_AT[1] + row) * F))
        sheet.paste(cell(lay["breach"], col, row, INSET), ((BREACH_AT[0] + col) * F, (BREACH_AT[1] + row) * F))
        exposed = [SIDES[i] for i in range(4) if mask & (1 << i)]
        faces = "no exposed faces" if not exposed else "exposed on " + " and ".join(exposed)
        cells.append({"col": WALL_AT[0] + col, "row": WALL_AT[1] + row, "name": f"{WALL} faces-{mask}",
                      "description": "armour cap, " + faces})
        cells.append({"col": BREACH_AT[0] + col, "row": BREACH_AT[1] + row, "name": f"{ARMOUR_RUBBLE} faces-{mask}",
                      "description": "armour torn open, " + faces})
    for i, facing in enumerate(SIDES):
        col, row = i % 2, i // 2
        for origin, at, block in ((lay["hatch"], HATCH_AT, ARMOUR_DOOR), (lay["port"], PORT_AT, ARMOUR_WINDOW)):
            sheet.paste(cell(origin, col, row, 0), ((at[0] + col) * F, (at[1] + row) * F))
            cells.append({"col": at[0] + col, "row": at[1] + row, "name": f"{block} {facing}",
                          "description": block.split(".")[1].replace("-", " ") + " facing " + facing})
    return cells


def run_cell(piece):
    """The middle cell of a three-cell run, its last columns cross-faded into the cell before so it tiles along x."""
    a = np.asarray(piece, dtype=np.float32)
    w = a.shape[1] // 3
    mid, before = a[:, w:2 * w], a[:, :w]
    k = max(2, w // 6)
    t = np.clip((np.arange(w) - (w - k)) / k, 0, 1)[None, :, None]
    return Image.fromarray(np.clip(mid * (1 - t) + before * t, 0, 255).astype(np.uint8))


def fittings(sheet):
    raw = X.sheet("fittings")
    lay = X.LAYOUT["fittings"]
    cell_px = lay["cellPx"]
    doodads = []
    for name, (frame, col, row, turn, cover, half) in FITTINGS.items():
        x0, y0, x1, y1 = lay["frames"][frame]
        piece = X.cut(raw, (x0, y0, x1, y1), cell_px, F)
        if frame in RUNS:
            piece = run_cell(piece)
        if turn:
            piece = piece.rotate(-90 * turn, expand=True)
        sheet.alpha_composite(piece, (col * F, row * F))
        doodads.append({"id": f"fallen-hull.{name}", "col": col, "row": row, "cover": cover,
                        "ballisticHalfHeight": half,
                        "footprintCells": [round(piece.width / F), round(piece.height / F)]})
    return doodads


def main():
    sheet = Image.new("RGBA", (COLS * F, ROWS * F))
    cells = armour(sheet)

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
    doodads += fittings(sheet)
    shear_cells, shear_doodads = sheared(sheet)
    cells += shear_cells
    doodads += shear_doodads
    doodads += shower(sheet)
    doodads += aliases(doodads)
    sheet.save(ROOT / "mod" / SHEET)
    doc = {
        "blocks": [
            {"doorBlock": ARMOUR_DOOR, "id": WALL, "layout": "wall-faces-4x4", "origin": list(WALL_AT),
             "rubbleBlock": ARMOUR_RUBBLE, "windowBlock": ARMOUR_WINDOW},
            {"doorBlock": ARMOUR_DOOR, "id": "fallen-hull.armour-wall", "layout": "wall-faces-4x4",
             "origin": list(WALL_AT), "rubbleBlock": ARMOUR_RUBBLE, "windowBlock": ARMOUR_WINDOW},
            {"id": SHEARED, "layout": "wall-faces-4x4", "origin": list(SHEARED_AT), "rubbleBlock": ARMOUR_RUBBLE},
            {"id": ARMOUR_RUBBLE, "layout": "wall-faces-4x4", "origin": list(BREACH_AT)},
            {"id": ARMOUR_DOOR, "layout": "cardinal-2x2", "origin": list(HATCH_AT)},
            {"id": ARMOUR_WINDOW, "layout": "cardinal-2x2", "origin": list(PORT_AT)},
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
