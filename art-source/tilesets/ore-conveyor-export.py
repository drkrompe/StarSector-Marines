"""Cut the raised ore conveyor's ImageGen master into its shipped sheet (`OreLine`).

The master was filled in over `ore-conveyor-guide.png` (1920x1080: five 384 px frames in a row, each holding one
320 px cell). Each piece is cut mechanically at its guide cell, scaled to the master's own size, and laid into a
one-row sheet at 256 px a cell, in the guide's order: belt, frame, corner, leg, drive.

    python art-source/tilesets/ore-conveyor-export.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
ROOT = HERE.parent.parent
GUIDE_W, GUIDE_H, CELL, FRAME = 1920, 1080, 320, 384
TOP = (GUIDE_H - FRAME) // 2
OUT_CELL = 256

master = Image.open(HERE / "ore-conveyor.raw.png").convert("RGBA")
sx, sy = master.width / GUIDE_W, master.height / GUIDE_H
sheet = Image.new("RGBA", (OUT_CELL * 5, OUT_CELL), (0, 0, 0, 0))
for i in range(5):
    x0 = i * FRAME + (FRAME - CELL) // 2
    y0 = TOP + (FRAME - CELL) // 2
    box = (round(x0 * sx), round(y0 * sy), round((x0 + CELL) * sx), round((y0 + CELL) * sy))
    sheet.paste(master.crop(box).resize((OUT_CELL, OUT_CELL), Image.LANCZOS), (i * OUT_CELL, 0))
sheet.save(ROOT / "mod" / "graphics" / "doodads" / "ore-conveyor.png")
