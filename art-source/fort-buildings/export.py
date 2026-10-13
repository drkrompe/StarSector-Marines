"""Mechanical export of the fort building sheets: crop each frame at the guide's own coordinates and resize it to
64 px per cell. Nothing is alpha-trimmed or recentred, so intact, damaged and destroyed share one registration.

    python art-source/fort-buildings/export.py            # all sheets that have a raw
    python art-source/fort-buildings/export.py bunker     # one

Frame geometry is the contract in art-source/templates/fort-buildings-guide.py (read its header).
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

BUILDINGS = {'bunker': (7, 5, 92), 'magazine': (8, 6, 77), 'power': (11, 5, 65)}
SLOTS = ['roof', 'damaged', 'wreck', 'lights']


def panel_box(quadrant, cw, ch, s):
    pw, ph = cw * s, ch * s
    x0 = (quadrant % 2) * QW + (QW - pw) // 2
    y0 = (quadrant // 2) * QH + (QH - ph) // 2
    return x0, y0, x0 + pw, y0 + ph


def crop(im, box, size):
    k = im.width / 1536.0
    scaled = tuple(round(v * k) for v in box)
    return im.crop(scaled).resize(size, Image.Resampling.LANCZOS)


def load(name):
    im = Image.open(HERE / 'raw' / f'{name}.raw.png').convert('RGBA')
    if im.getextrema()[3][0] != 0:
        raise ValueError(f'{name}: master has no real transparency')
    return im


def building(name):
    cw, ch, s = BUILDINGS[name]
    im = load(name)
    for q, slot in enumerate(SLOTS):
        crop(im, panel_box(q, cw, ch, s), (cw * PX, ch * PX)).save(OUT / f'{name}-{slot}.png')


def parts():
    im = load('parts')
    names = ['rotor-radar', 'rotor-turbine', 'rotor-vent']
    for slot, name in enumerate(names):
        x, y = (slot % 3) * 512, (slot // 3) * 512
        crop(im, (x, y, x + 512, y + 512), (256, 256)).save(OUT / f'{name}.png')
    for slot, name in ((3, 'core-console'), (4, 'core-ordnance'), (5, 'core-reactor')):
        cx, cy = (slot % 3) * 512 + 256, (slot // 3) * 512 + 256
        crop(im, (cx - 200, cy - 200, cx + 200, cy + 200), (3 * PX, 3 * PX)).save(OUT / f'{name}.png')


def gatehouse():
    im = load('gatehouse')
    for q, name in ((0, 'gate-tower'), (1, 'gate-leaves'), (2, 'gate-tower-damaged')):
        crop(im, panel_box(q, 2, 3, 150), (2 * PX, 3 * PX)).save(OUT / f'{name}.png')


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    wanted = sys.argv[1:] or ['bunker', 'magazine', 'power', 'parts', 'gatehouse']
    for w in wanted:
        if not (HERE / 'raw' / f'{w}.raw.png').exists():
            print('skip (no raw):', w)
        elif w in BUILDINGS:
            building(w)
        elif w == 'parts':
            parts()
        elif w == 'gatehouse':
            gatehouse()
