"""Exports the shipped decal sheet, mod/graphics/decals/decals.png (DecalKind), from its sources.

The sheet is a grid of CELL px cells, COLUMNS wide; a frame's index is its cell, counted along rows. The game cuts
each cell to its content, symmetric about the cell's centre (SpriteSheetSlicer.sliceGrid), so a mark turns about the
centre it was drawn round and its drawn size is its content's width.

- Cells 0-5 are the single frames other producers draw, each centred in its cell: the shell casings, the muzzle
  scorch (art-source/fx/build_muzzle_scorch.py) and the Spitter's three acid marks
  (art-source/rav-redesign/spitter/export_spitter_rig.py). They live under singles/.
- From cell 8, the three impact sheets (master-ballistic, master-explosive, master-energy-fire), each the 4 x 4 grid
  of its guide as ImageGen returned it, cut mechanically, each cell resampled to CELL px and laid in row order.

    python art-source/impact-decals/export.py
"""
from pathlib import Path

from PIL import Image

CELL = 160
COLUMNS = 8
HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "mod" / "graphics" / "decals" / "decals.png"

SINGLES = ["shell-casing", "shell-casing-alt", "muzzle-scorch", "acid-splash", "acid-splash-long", "acid-pool"]
"""Impact sheets in frame order; each fills 16 cells from its first (DecalKind's indices)."""
SHEETS = [("ballistic", 8), ("explosive", 24), ("energy-fire", 40)]
GRID = 4


def cell_origin(index):
    return (index % COLUMNS) * CELL, (index // COLUMNS) * CELL


def place_centred(sheet, image, index):
    """Lays a single frame with its content's box centred on its cell, so the symmetric cut is that box."""
    box = image.getchannel("A").point(lambda v: 255 if v >= 16 else 0).getbbox()
    if box is None:
        raise SystemExit(f"frame {index} is empty")
    content = image.crop(box)
    if content.width > CELL or content.height > CELL:
        raise SystemExit(f"frame {index} is {content.size}, larger than a {CELL} px cell")
    x0, y0 = cell_origin(index)
    sheet.alpha_composite(content, (x0 + (CELL - content.width) // 2, y0 + (CELL - content.height) // 2))


def main():
    rows = (SHEETS[-1][1] + GRID * GRID + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGBA", (COLUMNS * CELL, rows * CELL), (0, 0, 0, 0))
    for index, name in enumerate(SINGLES):
        place_centred(sheet, Image.open(HERE / "singles" / f"{name}.png").convert("RGBA"), index)
    for name, first in SHEETS:
        master = Image.open(HERE / f"master-{name}.png").convert("RGBA")
        w, h = master.size
        for row in range(GRID):
            for col in range(GRID):
                box = (round(col * w / GRID), round(row * h / GRID),
                       round((col + 1) * w / GRID), round((row + 1) * h / GRID))
                cell = master.crop(box).resize((CELL, CELL), Image.LANCZOS)
                sheet.paste(cell, cell_origin(first + row * GRID + col))
    sheet.save(OUT, optimize=True)
    print("wrote", OUT, sheet.size)


if __name__ == "__main__":
    main()
