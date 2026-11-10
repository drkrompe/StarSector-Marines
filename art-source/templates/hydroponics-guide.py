"""Geometry guides for a colony's covered farm (`story-map-pools.md`, Planet skin): what stands on a worked field on a
world that cannot grow food in the open (frozen, airless, volcanic, irradiated, toxic). Three looks, each one sheet of
three pieces, so the owner can pick the one that reads as enclosed growing (owner: the polytunnels "don't read like
hydroponics"). Every piece fills its footprint, carries lit grow-light (magenta in the guide) and its own piping; all
are walk-in and coverless, so the map's layout never moves.

Each sheet is 1536x1152 RGBA, one world cell = the look's CELL px, frames laid out explicitly with transparent
gutters; north is the top of a frame. A look's FRAMES give each piece's (x, y, w, h) in pixels.

- hydro-sheds: pressurised hydroponic sheds, rigid ribbed roofs split by two long lit grow-light skylights, roof fans,
  an airlock at the west end and a pipe run down the south eave. A 6x3 shed, a 3x3 shed and a 2x3 pump house.
- hydro-domes: sealed geodesic domes lit violet inside over ring beds. A 9x4 pair joined by a pressurised tube, a 4x4
  single dome and a 2x4 tank station.
- glasshouses: rigid glass halls, a sawtooth of narrow glazed ridges over crop rows under lit grow-light bars, a
  header pipe on the north edge. An 8x4 hall, a 4x4 hall and a 2x4 boiler house.

Usage: python art-source/templates/hydroponics-guide.py <out-dir>
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H, G = 1536, 1152, 40

ROOF, RIB, FRAME, PIPE, CROP, GLOW, HALO = '#b9bec0', '#8e9597', '#4e5557', '#2f6670', '#4f8a3e', '#e04fd0', '#f0a6e8'
GLASS, TANK, HATCH = '#a9c3c8', '#c9cecc', '#6f7876'

LOOKS = {
    'hydro-sheds': (160, {
        'hydro-shed': (G, G, 6, 3),
        'hydro-shed-short': (G, G + 3 * 160 + G, 3, 3),
        'hydro-pumphouse': (G + 3 * 160 + G, G + 3 * 160 + G, 2, 3),
    }),
    'hydro-domes': (125, {
        'hydro-dome-pair': (G, G, 9, 4),
        'hydro-dome': (G, G + 4 * 125 + G, 4, 4),
        'hydro-dome-tanks': (G + 4 * 125 + G, G + 4 * 125 + G, 2, 4),
    }),
    'glasshouses': (125, {
        'glasshouse': (G, G, 8, 4),
        'glasshouse-short': (G, G + 4 * 125 + G, 4, 4),
        'glasshouse-boiler': (G + 4 * 125 + G, G + 4 * 125 + G, 2, 4),
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


def dome_at(d, cx, cy, r):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=GLASS)
    d.ellipse((cx - r + 40, cy - r + 40, cx + r - 40, cy + r - 40), fill=HALO)      # violet light inside
    for rr, col in ((r - 60, CROP), (r - 105, GLOW), (r - 140, CROP)):              # ring beds under the light
        if rr > 20:
            d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=col, width=26)
    d.ellipse((cx - 26, cy - 26, cx + 26, cy + 26), fill=FRAME)                     # hub
    for k in range(12):                                                             # geodesic struts
        a = k * math.pi / 6
        d.line((cx + 26 * math.cos(a), cy + 26 * math.sin(a), cx + r * math.cos(a), cy + r * math.sin(a)),
               fill=RIB, width=5)
    for rr in (r * 0.5, r * 0.8):
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=RIB, width=5)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=FRAME, width=14)


def dome_pair(d, x, y, w, h, cell):
    r = 2 * cell - 10
    d.rectangle((x + 2 * cell, y + h // 2 - 34, x + w - 2 * cell, y + h // 2 + 34), fill=FRAME)   # pressurised tube
    d.rectangle((x + 2 * cell, y + h // 2 - 18, x + w - 2 * cell, y + h // 2 + 18), fill=RIB)
    d.rectangle((x + 2 * cell, y + h - 30, x + w - 2 * cell, y + h - 18), fill=PIPE)              # pipe beneath
    dome_at(d, x + 2 * cell, y + h // 2, r)
    dome_at(d, x + w - 2 * cell, y + h // 2, r)


def dome_single(d, x, y, w, h, cell):
    dome_at(d, x + w // 2, y + h // 2, 2 * cell - 10)
    d.rectangle((x + w // 2 - 40, y + h - 46, x + w // 2 + 40, y + h - 4), fill=FRAME)            # airlock, south


def tanks(d, x, y, w, h):
    d.rectangle((x + 8, y + 8, x + w - 8, y + h - 8), fill=FRAME)
    s = min(w - 50, (h - 70) // 2)
    for k in range(2):
        ty = y + 25 + k * (s + 20)
        d.ellipse((x + 25, ty, x + 25 + s, ty + s), fill=TANK)
        d.ellipse((x + 25 + s // 2 - 18, ty + s // 2 - 18, x + 25 + s // 2 + 18, ty + s // 2 + 18), fill=HATCH)
    d.rectangle((x + w - 30, y + 20, x + w - 16, y + h - 20), fill=PIPE)


def glasshouse(d, x, y, w, h):
    d.rectangle((x + 4, y + 4, x + w - 4, y + h - 4), fill=GLASS, outline=FRAME, width=10)
    for k, ry in enumerate(range(y + 50, y + h - 30, 48)):                          # crop rows under the glass
        d.rectangle((x + 30, ry, x + w - 30, ry + 16), fill=CROP)
    for ry in range(y + 70, y + h - 40, 96):                                        # lit grow-light bars along
        d.rectangle((x + 30, ry - 2, x + w - 30, ry + 8), fill=GLOW)
    for rx in range(x + 40, x + w - 20, 60):                                        # sawtooth glazed ridges across
        d.line((rx, y + 10, rx, y + h - 10), fill=RIB, width=4)
        d.line((rx + 20, y + 10, rx + 20, y + h - 10), fill='#d7e4e6', width=2)
    d.rectangle((x + 10, y + 12, x + w - 10, y + 30), fill=PIPE)                    # header pipe, north
    d.rectangle((x + w - 40, y + h // 2 - 36, x + w - 6, y + h // 2 + 36), fill=FRAME)   # door, east end


def boiler(d, x, y, w, h):
    d.rectangle((x + 8, y + 8, x + w - 8, y + h - 8), fill=ROOF, outline=FRAME, width=8)
    d.ellipse((x + 50, y + 40, x + w - 50, y + 40 + w - 100), fill=FRAME)          # flue
    d.ellipse((x + 70, y + 60, x + w - 70, y + 20 + w - 80), fill='#3a3f3f')
    s = w - 60
    d.ellipse((x + 30, y + h - s - 40, x + 30 + s, y + h - 40), fill=TANK)          # CO2 tank
    d.ellipse((x + 30 + s // 2 - 16, y + h - s // 2 - 56, x + 30 + s // 2 + 16, y + h - s // 2 - 24), fill=HATCH)
    d.rectangle((x + 8, y + 12, x + w - 8, y + 30), fill=PIPE)                      # header out to the halls


def build(look):
    cell, _ = LOOKS[look]
    f = frames(look)
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    if look == 'hydro-sheds':
        shed(d, *f['hydro-shed'])
        shed(d, *f['hydro-shed-short'])
        pumphouse(d, *f['hydro-pumphouse'])
    elif look == 'hydro-domes':
        dome_pair(d, *f['hydro-dome-pair'], cell)
        dome_single(d, *f['hydro-dome'], cell)
        tanks(d, *f['hydro-dome-tanks'])
    else:
        glasshouse(d, *f['glasshouse'])
        glasshouse(d, *f['glasshouse-short'])
        boiler(d, *f['glasshouse-boiler'])
    return img


if __name__ == '__main__':
    for look in LOOKS:
        build(look).save(Path(sys.argv[1]) / f'{look}-guide.png')
