"""Builds the works halls' floor coverings (``ruins-theme-halls``) as the ``ruins-ground`` tileset.

Six ImageGen masters (``*-master.png``, briefs beside them) are each cut into eight crops, wrapped into 56 px tiles by
``normalize_ground_variants.py`` (one family per material, aligned to its own mean) into
``atlas-material-source/ruins-<material>-variants/``, then laid one row per material in ``ruins-ground.raw.png`` with
its authoring document. The raised floor and stage floor are panelled: their masters are plain material, and the
panel seams are drawn here on the cell grid (the raised floor in half-cell panels, the stage in whole cells), so every
seam falls on the grid whichever variant a cell picks. Run from the repository root, then export:

    python art-source/tilesets/ruins-ground/build.py
    echo {"name":"ruins-ground"} | tools/authoring.cmd tileset_export -
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

HERE = "art-source/tilesets/ruins-ground/"
SRC = "art-source/tilesets/atlas-material-source/"
CELL = 56
# Material, block suffix, normaliser flags. Structured surfaces keep their middle (``--band``); grain is blended.
MATERIALS = [
    ("ice-sheet", "ice-sheet", ["--scale", "3", "--band", "13"], "a thin sheet of frozen coolant over the deck"),
    ("rime", "rime", ["--scale", "3"], "hoar frost and rime grown over the deck"),
    ("biofilm", "biofilm", ["--scale", "3", "--band", "13"], "a wet culture mat grown over the deck"),
    ("wet-floor", "wet-floor", ["--scale", "3", "--band", "13"], "the concrete deck standing wet"),
    ("raised-floor", "raised-floor", ["--scale", "4"], "a clean-room raised access floor, half-cell panels"),
    ("stage-floor", "stage-floor", ["--scale", "4"], "a holographic stage's dark polished deck, cell panels"),
]
# The masters came back very flat (the contract's fine, low-contrast grain), so at play scale ice and rime read as one
# colour: each tile's texture is stretched about its own mean by this gain, which keeps the colour and the wrap.
GAIN = {"ice-sheet": 2.4, "rime": 2.2, "biofilm": 1.8, "wet-floor": 2.0, "raised-floor": 1.6, "stage-floor": 1.8}
# Panel seams: (period in px, dark joint, light lip) drawn on the cell grid.
PANELS = {"raised-floor": (28, (88, 90, 92), (176, 178, 180)), "stage-floor": (56, (30, 32, 38), (84, 90, 100))}


def crops(master, out_dir, side):
    """Eight crops of ``side`` px spread over the master, each a source the normaliser centre-cuts."""
    im = Image.open(master).convert("RGB")
    xs = np.linspace(0, im.width - side, 4).astype(int)
    ys = np.linspace(0, im.height - side, 2).astype(int)
    k = 0
    for y in ys:
        for x in xs:
            k += 1
            im.crop((x, y, x + side, y + side)).save(os.path.join(out_dir, f"crop-{k}.png"))
            os.utime(os.path.join(out_dir, f"crop-{k}.png"), (k, k))


def panel(tile, spec):
    period, dark, light = spec
    a = np.asarray(tile).copy()
    for p in range(0, CELL, period):
        a[p, :] = dark
        a[:, p] = dark
        if p + 1 < CELL:
            a[p + 1, :] = (a[p + 1, :] * 0.5 + np.array(light) * 0.5).astype(np.uint8)
            a[:, p + 1] = (a[:, p + 1] * 0.5 + np.array(light) * 0.5).astype(np.uint8)
    return Image.fromarray(a)


def main():
    plate = Image.new("RGB", (CELL * 8, CELL * len(MATERIALS)))
    entries = []
    for row, (mat, block, flags, _) in enumerate(MATERIALS):
        scale = int(flags[flags.index("--scale") + 1])
        out = SRC + f"ruins-{mat}-variants"
        with tempfile.TemporaryDirectory() as tmp:
            crops(HERE + f"{mat}-master.png", tmp, CELL * 2 * scale)
            subprocess.run([sys.executable, "art-source/tilesets/normalize_ground_variants.py", tmp, out, f"{mat}var",
                            *flags], check=True, stdout=subprocess.DEVNULL)
        for v in range(1, 9):
            path = f"{out}/{mat}var-{v}.png"
            tile = Image.open(path).convert("RGB")
            t = np.asarray(tile).astype(np.float32)
            mean = t.reshape(-1, 3).mean(0)
            tile = Image.fromarray(np.clip(mean + (t - mean) * GAIN[mat], 0, 255).astype(np.uint8))
            tile.save(path)
            if mat in PANELS:
                tile = panel(tile, PANELS[mat])
                tile.save(path)
            plate.paste(tile, ((v - 1) * CELL, row * CELL))
            entries.append({"block": f"ruins-ground.{block}", "cover": "none", "footprintCells": [1, 1],
                            "id": f"doodad.ruins-ground.c{v - 1}r{row}", "included": True, "material": path,
                            "note": f"{mat}var-{v}", "rect": [(v - 1) * CELL, row * CELL, CELL, CELL],
                            "slot": f"v{v}"})
    plate.save("art-source/tilesets/ruins-ground.raw.png")
    doc = {
        "alphaMin": 40,
        "blocks": [{"id": f"ruins-ground.{b}", "layout": "variants"} for _, b, _, _ in MATERIALS],
        "cellPx": CELL, "entries": entries, "gridCols": 8, "gridRows": len(MATERIALS),
        "idPrefix": "doodad.ruins-ground",
        "note": "The works halls' floor coverings (ruins-theme-halls), laid as skins by cell: "
                + "; ".join(f"ruins-ground.{b} is {d}" for _, b, _, d in MATERIALS)
                + ". A contact sheet of the tiles under atlas-material-source/ruins-*-variants, rebuilt by "
                  "art-source/tilesets/ruins-ground/build.py from the masters beside it.",
        "sheet": "art-source/tilesets/ruins-ground.raw.png", "sheetName": "ruins-ground"}
    with open("art-source/tilesets/ruins-ground.tileset-authoring.json", "w") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")
    print("plate", plate.size, len(entries), "tiles")


if __name__ == "__main__":
    main()
