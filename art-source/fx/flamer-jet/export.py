"""Exports the flamer jet's head sprite: the brightest flame puff of the shared particle sheet.

The flamer's charge flies as this sprite, wrapped in its authored fire trail. It is a crop, not new art, so the
jet reads as the same fire the ground burns with. Run from the repository root:

    python art-source/fx/flamer-jet/export.py
"""
from pathlib import Path

from PIL import Image

SHEET = Path("mod/graphics/particle/smokeAndFire.png")
OUT = Path("mod/graphics/battle/fx/flamer-jet-head.png")
FRAME = 16
# Row 2 of the sheet is fire; column 0 is its fullest, brightest puff.
COLUMN, ROW = 0, 2


def main():
    sheet = Image.open(SHEET).convert("RGBA")
    box = (COLUMN * FRAME, ROW * FRAME, (COLUMN + 1) * FRAME, (ROW + 1) * FRAME)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.crop(box).save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
