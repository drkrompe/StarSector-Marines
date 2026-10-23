"""Exports the vehicle scuff sheet (combat-durability-nouns.md, Worn plate) from its accepted master.

The master is the guide's 4 x 3 grid as ImageGen returned it, at whatever size it came back. Each cell is cut on the
grid mechanically, kept whole (no trim or recentre: the renderer turns a mark about its cell centre) and resampled to
CELL px, so the shipped sheet is 4 x 3 cells of CELL px. Row is the stage (scrape/pock, scorch, gouge/dent), column
the variant; `ConvoyRenderSystem.SCUFF_SHEET` addresses it that way.

    python art-source/vehicles/scuffs/export.py
"""
from pathlib import Path

from PIL import Image

COLS, ROWS = 4, 3
CELL = 128
HERE = Path(__file__).resolve().parent
OUT = HERE.parents[2] / "mod" / "graphics" / "battle" / "vehicles" / "scuffs.png"


def main():
    master = Image.open(HERE / "master.png").convert("RGBA")
    w, h = master.size
    sheet = Image.new("RGBA", (COLS * CELL, ROWS * CELL), (0, 0, 0, 0))
    for row in range(ROWS):
        for col in range(COLS):
            box = (round(col * w / COLS), round(row * h / ROWS), round((col + 1) * w / COLS), round((row + 1) * h / ROWS))
            cell = master.crop(box).resize((CELL, CELL), Image.LANCZOS)
            sheet.paste(cell, (col * CELL, row * CELL))
    sheet.save(OUT, optimize=True)
    print("wrote", OUT, sheet.size)


if __name__ == "__main__":
    main()
