"""Geometry guide for a colony's covered farm (`story-map-pools.md`, Planet skin): what stands on a worked field on a
world that cannot grow food in the open (frozen, airless, volcanic, irradiated, toxic). Pressurised hydroponic sheds
(owner's pick of planet-skin-farm over geodesic domes and glass halls; polytunnels "don't read like hydroponics"):
rigid ribbed roofs split by two long lit grow-light skylights (magenta in the guide; the shipped sheet is regraded to
amber by `tilesets/grow-light-amber.py`), roof fans, an airlock at the west end and a pipe run down the south eave. A
6x3 shed, a 3x3 shed and a 2x3 pump house; walk-in and coverless, so the map's layout never moves.

One 1536x1152 RGBA sheet, one world cell = CELL px, frames laid out explicitly with transparent gutters; north is the
top of a frame.

Usage: python art-source/templates/hydroponics-guide.py <out-dir>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H, G = 1536, 1152, 40

ROOF, RIB, FRAME, PIPE, CROP, GLOW, HALO = '#b9bec0', '#8e9597', '#4e5557', '#2f6670', '#4f8a3e', '#e04fd0', '#f0a6e8'
TANK, HATCH = '#c9cecc', '#6f7876'

LOOKS = {
    'hydro-sheds': (160, {
        'hydro-shed': (G, G, 6, 3),
        'hydro-shed-short': (G, G + 3 * 160 + G, 3, 3),
        'hydro-pumphouse': (G + 3 * 160 + G, G + 3 * 160 + G, 2, 3),
    }),
}


def frames(look):
    cell, pieces = LOOKS[look]
    return {k: (x, y, cw * cell, ch * cell) for k, (x, y, cw, ch) in pieces.items()}


def lit_strip(d, box):
    """A grow-light skylight: a pink halo, a magenta core, crops showing through."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=HALO)
    d.rectangle((x0 + 6, y0 + 6, x1 - 6, y1 - 6), fill=GLOW)
    for cx in range(x0 + 18, x1 - 10, 26):
        d.ellipse((cx - 7, (y0 + y1) // 2 - 7, cx + 7, (y0 + y1) // 2 + 7), fill=CROP)


def shed(d, x, y, w, h):
    i = 6
    d.rectangle((x + i, y + i, x + w - i, y + h - i), fill=ROOF, outline=FRAME, width=8)
    for rx in range(x + 40, x + w - 30, 22):                                         # roof ribs across
        d.line((rx, y + i, rx, y + h - i), fill=RIB, width=3)
    band = h // 6
    lit_strip(d, (x + 56, y + band, x + w - 30, y + 2 * band))                       # two skylights along
    lit_strip(d, (x + 56, y + h - 2 * band - 34, x + w - 30, y + h - band - 34))
    d.rectangle((x + i, y + h // 2 - 40, x + 44, y + h // 2 + 40), fill=FRAME)      # airlock, west end
    for fx in range(x + 130, x + w - 60, 300):                                      # roof fans on the ridge
        d.ellipse((fx - 22, y + h // 2 - 22, fx + 22, y + h // 2 + 22), fill=FRAME)
        d.ellipse((fx - 12, y + h // 2 - 12, fx + 12, y + h // 2 + 12), fill=RIB)
    d.rectangle((x + 50, y + h - 34, x + w - 12, y + h - 24), fill=PIPE)            # pipe run, south eave
    d.rectangle((x + 50, y + h - 20, x + w - 12, y + h - 12), fill=PIPE)


def pumphouse(d, x, y, w, h):
    d.rectangle((x + 8, y + 8, x + w - 8, y + h - 8), fill=FRAME)                   # skid
    t = 170
    for ty in (y + 24, y + 24 + t + 16):                                            # two round tanks
        d.ellipse((x + (w - t) // 2, ty, x + (w + t) // 2, ty + t), fill=TANK)
        d.ellipse((x + w // 2 - 20, ty + t // 2 - 20, x + w // 2 + 20, ty + t // 2 + 20), fill=HATCH)
    d.rectangle((x + 60, y + h - 110, x + w - 60, y + h - 40), fill='#3e6f7a')      # pump
    d.rectangle((x + w // 2 - 6, y + 20, x + w // 2 + 6, y + h - 40), fill=PIPE)    # riser
    d.rectangle((x + 8, y + h - 34, x + w - 8, y + h - 12), fill=PIPE)              # header out to the sheds








def build(look):
    cell, _ = LOOKS[look]
    f = frames(look)
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    shed(d, *f['hydro-shed'])
    shed(d, *f['hydro-shed-short'])
    pumphouse(d, *f['hydro-pumphouse'])
    return img


if __name__ == '__main__':
    for look in LOOKS:
        build(look).save(Path(sys.argv[1]) / f'{look}-guide.png')
