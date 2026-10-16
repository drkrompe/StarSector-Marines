"""Lay part guides out as one 2x2 sheet for a single Codex request, and cut the
generated sheet back into tiles.

  python parts_sheet.py compose            -> guide/parts-sheet-guide.png
  python parts_sheet.py split <generated>  -> build/parts/<part>.png (tile size)

Tiles sit on the guide background with gutters, so no part touches another and
each tile is segmented on its own. The generated sheet is cut at the same
normalised rectangles and resized to the tile size, so measure_drift.py
compares like with like; anything Codex moved across a gutter is lost and
shows up as drift.
"""
from pathlib import Path
import sys
from PIL import Image

HERE = Path(__file__).resolve().parent
PARTS = ['part-deck', 'part-turret', 'part-pod', 'part-bow']  # reading order
SHEET, TILE, GX, GY = (1920, 1080), (912, 513), 32, 18
BACKGROUND = (214, 214, 214)


def box(k):
    x = GX + (k % 2) * (TILE[0] + GX)
    y = GY + (k // 2) * (TILE[1] + GY)
    return x, y, x + TILE[0], y + TILE[1]


if sys.argv[1] == 'compose':
    sheet = Image.new('RGB', SHEET, BACKGROUND)
    for k, p in enumerate(PARTS):
        sheet.paste(Image.open(HERE / 'guide' / f'{p}-guide.png').convert('RGB'), box(k)[:2])
    sheet.save(HERE / 'guide' / 'parts-sheet-guide.png')
else:
    gen = Image.open(sys.argv[2]).convert('RGB')
    sx, sy = gen.width / SHEET[0], gen.height / SHEET[1]
    out = HERE / 'build' / 'parts'
    out.mkdir(parents=True, exist_ok=True)
    for k, p in enumerate(PARTS):
        x0, y0, x1, y1 = box(k)
        gen.crop((round(x0 * sx), round(y0 * sy), round(x1 * sx), round(y1 * sy))).resize(TILE, Image.LANCZOS).save(out / f'{p}.png')
