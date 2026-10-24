"""Geometry guide for one emplacement style's fortification works (`fort-works.md`): every piece a lot's walls,
parapets and barricades are drawn from, on ONE 1536x1024 sheet, so a style's whole kit is painted in one pass and
reads as one material.

  left   the tight parapet collar: the 5x5 plate of `templates/fort-emplacement-guide.py` at 192 px a cell, at (0, 32).
         Its eight pieces are the looks of `doodad.fort-emplacement-<shape>`.
  right  the barricade ring: a 3x3 ring of 176 px cells at (992, 32), a gutter clear of the plate, a low band along the ring's outer border and
         nothing inside. The top-middle cell is the run whose band is on its north edge ('n'), the top-left the corner
         on its north and west edges ('corner-nw'), and so on: the looks of `doodad.fort-sandbag-<shape>` and of the
         post ring's `doodad.sandbag-<shape>`.
  below  the perimeter wall: the sixteen exposed-face masks (N=1 E=2 S=4 W=8, slot = mask % 4, mask / 4) at a 96 px
         pitch, an 84 px cap with a rim on each exposed face, at (1008, 636). The looks of the 'wall-faces-4x4'
         block `installation.dark-concrete-wall`.

Light is from the north-west, as the battle's sun. Everything outside the pieces is transparent.

Usage: python art-source/fortworks/make_guide.py   (writes art-source/fortworks/guides/works-guide.png)
"""
import importlib.util
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

W, H = 1536, 1024
S = 192
PLATE_AT = (0, 32)
RING_AT = (992, 32)
RING_CELL = 176
WALL_AT = (1008, 636)
WALL_PITCH = 96
WALL_CELL = 84
WALL_RIM = 12

# The barricade band's profile across its depth, from the ring's outside in: outer batter, top, inner batter.
BAND_OUTER = 13
BAND_TOP = 48
BAND_INNER = 11
BAND = BAND_OUTER + BAND_TOP + BAND_INNER
BAND_TOPFACE = (176, 172, 160, 255)
BAND_OUTER_TONE = {'n': (132, 128, 118, 255), 'w': (128, 124, 114, 255), 's': (72, 70, 64, 255), 'e': (78, 76, 70, 255)}
BAND_INNER_TONE = {'n': (104, 100, 92, 255), 'w': (108, 104, 96, 255), 's': (150, 146, 136, 255), 'e': (146, 142, 132, 255)}

WALL_CAP = (112, 112, 110, 255)
WALL_RIM_TONE = {'n': (150, 150, 146, 255), 'w': (146, 146, 142, 255), 's': (66, 66, 64, 255), 'e': (72, 72, 70, 255)}


def emplacement_plate():
    spec = importlib.util.spec_from_file_location('fort_emplacement_guide',
                                                  ROOT / 'art-source/templates/fort-emplacement-guide.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.plate()


def band_tone(side, dist):
    if dist < BAND_OUTER:
        return BAND_OUTER_TONE[side]
    if dist < BAND_OUTER + BAND_TOP:
        return BAND_TOPFACE
    if dist < BAND:
        return BAND_INNER_TONE[side]
    return None


def ring():
    """The 3x3 barricade ring: a band of BAND px inside the outer border, mitred at the four outer corners."""
    size = 3 * RING_CELL
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            dist = {'n': y, 's': size - 1 - y, 'w': x, 'e': size - 1 - x}
            side = min(dist, key=dist.get)
            tone = band_tone(side, dist[side])
            if tone is not None:
                px[x, y] = tone
    return img


def wall_cell(mask):
    img = Image.new('RGBA', (WALL_CELL, WALL_CELL), WALL_CAP)
    d = ImageDraw.Draw(img)
    c = WALL_CELL - 1
    if mask & 1:
        d.rectangle([0, 0, c, WALL_RIM - 1], fill=WALL_RIM_TONE['n'])
    if mask & 8:
        d.rectangle([0, 0, WALL_RIM - 1, c], fill=WALL_RIM_TONE['w'])
    if mask & 4:
        d.rectangle([0, c - WALL_RIM + 1, c, c], fill=WALL_RIM_TONE['s'])
    if mask & 2:
        d.rectangle([c - WALL_RIM + 1, 0, c, c], fill=WALL_RIM_TONE['e'])
    return img


def wall_rect(mask):
    """Where the cap of `mask` sits on the guide, as [x, y, w, h]."""
    pad = (WALL_PITCH - WALL_CELL) // 2
    return [WALL_AT[0] + (mask % 4) * WALL_PITCH + pad, WALL_AT[1] + (mask // 4) * WALL_PITCH + pad,
            WALL_CELL, WALL_CELL]


def guide():
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    out.alpha_composite(emplacement_plate(), PLATE_AT)
    out.alpha_composite(ring(), RING_AT)
    for mask in range(16):
        x, y, _, _ = wall_rect(mask)
        out.alpha_composite(wall_cell(mask), (x, y))
    return out


if __name__ == '__main__':
    (HERE / 'guides').mkdir(exist_ok=True)
    guide().save(HERE / 'guides/works-guide.png')
    print('wrote', HERE / 'guides/works-guide.png')
