"""Exports the Rav wear sheet (combat-durability-nouns.md, Worn plate; RavWear) from its accepted master.

The master is the guide's 4 x 3 grid as ImageGen returned it, at whatever size it came back. Each cell is cut on the
grid mechanically, kept whole (no trim or recentre: the renderer turns a mark about its cell centre) and resampled to
CELL px, so the shipped sheet is 4 x 3 cells of CELL px. Row is the stage (score/chip, bruise, wound), column
the variant; `RavWear.SHEET` addresses it that way.

    python art-source/rav-wear/export.py [master.png]

The accepted master is `master-v2.png` (bold, high-contrast marks that read at play zoom); `master.png` is the first,
subtler generation, kept for comparison.
"""
import sys
from pathlib import Path

from PIL import Image

COLS, ROWS = 4, 3
CELL = 128
HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "mod" / "graphics" / "battle" / "swarm" / "rav-wear.png"


def main():
    master = Image.open(HERE / (sys.argv[1] if len(sys.argv) > 1 else "master-v2.png")).convert("RGBA")
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
