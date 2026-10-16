"""Lay part guides out as 2x2 sheets for one Codex request each, and cut the
generated sheets back into tiles.

  python sheets.py compose                 -> guide/sheet-<name>-guide.png (+ -class.png)
  python sheets.py split <name> <png>      -> build/tiles/<part>-gen.png (tile size)

Tiles sit on the guide background with gutters, so no part touches another and
each tile is segmented on its own. The generated sheet is cut at the same
normalised rectangles and resized to the tile size; anything Codex moved
across a gutter is lost and shows up as drift in register.py.
"""
from pathlib import Path
import sys
from PIL import Image
import parts as P

HERE = Path(__file__).resolve().parent


def compose():
    for name, members in P.SHEETS.items():
        for kind, bg in (('guide', P.BACKGROUND), ('class', (0, 0, 0))):
            sheet = Image.new('RGB', P.SHEET, bg)
            for k, part in enumerate(members):
                sheet.paste(Image.open(HERE / 'guide' / f'{part}-{kind}.png').convert('RGB'), P.tile_box(k)[:2])
            sheet.save(HERE / 'guide' / f'sheet-{name}-{kind}.png')
        print('composed', name)


def split(name, path):
    gen = Image.open(path).convert('RGB')
    sx, sy = gen.width / P.SHEET[0], gen.height / P.SHEET[1]
    out = HERE / 'build' / 'tiles'
    out.mkdir(parents=True, exist_ok=True)
    for k, part in enumerate(P.SHEETS[name]):
        x0, y0, x1, y1 = P.tile_box(k)
        gen.crop((round(x0 * sx), round(y0 * sy), round(x1 * sx), round(y1 * sy))).resize(P.TILE, Image.LANCZOS).save(out / f'{part}-gen.png')


if __name__ == '__main__':
    if sys.argv[1] == 'compose':
        compose()
    else:
        split(sys.argv[2], sys.argv[3])
