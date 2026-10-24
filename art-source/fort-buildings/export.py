"""Mechanical export of the fort part sheets: crop each frame at the guide's own coordinates and resize it to 64 px
per cell. Nothing is alpha-trimmed or recentred, so intact and damaged share one registration.

Fort buildings are kit buildings drawn by the works (`fort-works.md`); their old roof slabs and rotors are retired and
no longer exported. What still ships from here: the core pieces, the blast door and the base gate leaves.

    python art-source/fort-buildings/export.py            # all sheets that have a raw
    python art-source/fort-buildings/export.py blastdoor  # one

Frame geometry is the contract in art-source/templates/fort-buildings-guide.py (read its header).

The sheets are drawn on the local frame, which runs y DOWN (row 0 is the north wall), but the battle draws cell +y up the
screen; so every blast-door frame is written flipped top to bottom, which is what makes a sprite drawn upright land on
the cells of the plan it was authored for. Banner and door coordinates in the catalog stay in the authored (y-down)
frame. The gate leaves and the core pieces are symmetric across that axis and are written as cropped.
Outputs: mod/graphics/fort/<name>.png
"""
from pathlib import Path
import sys
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / 'mod/graphics/fort'
PX = 64  # runtime pixels per cell
QW, QH = 768, 512

DOOR_PX = 200  # blastdoor guide px per cell (a 3 x 1 piece, intact then damaged)


def panel_box(quadrant, cw, ch, s):
    pw, ph = cw * s, ch * s
    x0 = (quadrant % 2) * QW + (QW - pw) // 2
    y0 = (quadrant // 2) * QH + (QH - ph) // 2
    return x0, y0, x0 + pw, y0 + ph


def crop(im, box, size):
    k = im.width / 1536.0
    scaled = tuple(round(v * k) for v in box)
    return im.crop(scaled).resize(size, Image.Resampling.LANCZOS)


def flip_v(im):
    return im.transpose(Image.Transpose.FLIP_TOP_BOTTOM)


def load(name):
    im = Image.open(HERE / 'raw' / f'{name}.raw.png').convert('RGBA')
    if im.getextrema()[3][0] != 0:
        raise ValueError(f'{name}: master has no real transparency')
    return im


def parts():
    im = load('parts')
    for slot, name in ((3, 'core-console'), (4, 'core-ordnance'), (5, 'core-reactor')):
        cx, cy = (slot % 3) * 512 + 256, (slot // 3) * 512 + 256
        crop(im, (cx - 200, cy - 200, cx + 200, cy + 200), (3 * PX, 3 * PX)).save(OUT / f'{name}.png')


def blastdoor():
    im = load('blastdoor')
    for q, name in ((0, 'blast-door'), (1, 'blast-door-damaged')):
        flip_v(crop(im, panel_box(q, 3, 1, DOOR_PX), (3 * PX, PX))).save(OUT / f'{name}.png')


def gatehouse():
    im = load('gatehouse')
    crop(im, panel_box(1, 2, 3, 150), (2 * PX, 3 * PX)).save(OUT / 'gate-leaves.png')


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    wanted = sys.argv[1:] or ['parts', 'gatehouse', 'blastdoor']
    for w in wanted:
        if not (HERE / 'raw' / f'{w}.raw.png').exists():
            print('skip (no raw):', w)
        elif w == 'parts':
            parts()
        elif w == 'gatehouse':
            gatehouse()
        elif w == 'blastdoor':
            blastdoor()
