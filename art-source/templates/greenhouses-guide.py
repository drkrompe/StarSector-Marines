"""Geometry guide for a colony's covered farm (`story-map-pools.md`, Planet skin): what stands on a worked field on a
world that cannot grow food in the open (frozen, airless, volcanic, irradiated, toxic). Placed in rows on the cleared
plot with walkable lanes between; passable and coverless, so the map's layout never moves.

One 1536x1152 RGBA sheet, one world cell = CELL px, frames laid out explicitly with transparent gutters; north is the
top of a frame. FRAMES gives each frame's (x, y, w, h) in pixels.

- greenhouse-tunnel (6x2): a long arched polytunnel, ribbed translucent film over crop beds, a door at each short end.
- greenhouse-tunnel-short (3x2): the same tunnel, half as long.
- grow-dome (3x3): a sealed geodesic dome over a round hydroponic bed, an airlock porch on its south side.
- nutrient-skid (2x1): a pair of water/nutrient tanks and a pump on a low skid.

Usage: python art-source/templates/greenhouses-guide.py <out-dir>
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H, CELL, G = 1536, 1152, 180, 40

FRAMES = {
    'greenhouse-tunnel': (G, G, 6 * CELL, 2 * CELL),
    'nutrient-skid': (G + 6 * CELL + G, G, 2 * CELL, CELL),
    'greenhouse-tunnel-short': (G, G + 2 * CELL + G, 3 * CELL, 2 * CELL),
    'grow-dome': (G + 3 * CELL + G, G + 2 * CELL + G, 3 * CELL, 3 * CELL),
}

FILM, RIB, BED, PATH, FRAME = '#c9d6d2', '#8c9895', '#4f7a3e', '#7f7a6c', '#5d6564'


def tunnel(d, x, y, cells):
    w, h = cells * CELL, 2 * CELL
    inset = 14
    d.rounded_rectangle((x + inset, y + inset, x + w - inset, y + h - inset), radius=40, fill=FILM)
    # Crop beds seen through the film: two long beds either side of a central path.
    d.rectangle((x + 50, y + 62, x + w - 50, y + h // 2 - 22), fill=BED)
    d.rectangle((x + 50, y + h // 2 + 22, x + w - 50, y + h - 62), fill=BED)
    d.rectangle((x + 50, y + h // 2 - 14, x + w - 50, y + h // 2 + 14), fill=PATH)
    # Hoops across the tunnel every half cell.
    for k in range(1, cells * 2):
        rx = x + k * CELL // 2
        d.rectangle((rx - 4, y + inset, rx + 4, y + h - inset), fill=RIB)
    # Ridge line and the doors in each short end.
    d.rectangle((x + inset, y + h // 2 - 3, x + w - inset, y + h // 2 + 3), fill=RIB)
    for dx in (x + inset, x + w - inset - 26):
        d.rectangle((dx, y + h // 2 - 40, dx + 26, y + h // 2 + 40), fill=FRAME)


def dome(d, x, y):
    s = 3 * CELL
    cx, cy, r = x + s // 2, y + s // 2 - 20, s // 2 - 40
    d.rectangle((cx - 50, cy + r - 20, cx + 50, y + s - 14), fill=FRAME)          # airlock porch, south
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=FILM)
    d.ellipse((cx - r + 50, cy - r + 50, cx + r - 50, cy + r - 50), fill=BED)       # round hydroponic bed
    d.ellipse((cx - 40, cy - 40, cx + 40, cy + 40), fill=PATH)                      # central walkway hub
    for k in range(10):                                                             # geodesic struts
        a = k * math.pi / 5
        d.line((cx, cy, cx + r * math.cos(a), cy + r * math.sin(a)), fill=RIB, width=7)
    for rr in (r * 0.45, r * 0.78):
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=RIB, width=7)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=FRAME, width=10)


def skid(d, x, y):
    w, h = 2 * CELL, CELL
    d.rectangle((x + 10, y + 18, x + w - 10, y + h - 18), fill=FRAME)               # skid
    for tx in (x + 30, x + 150):                                                    # two round tanks
        d.ellipse((tx, y + 28, tx + 110, y + 138), fill='#9aa3a0')
        d.ellipse((tx + 35, y + 63, tx + 75, y + 103), fill='#6f7876')
    d.rectangle((x + 280, y + 50, x + 340, y + 110), fill='#3e6f7a')                # pump
    d.rectangle((x + 140, y + 78, x + 290, y + 88), fill='#3a3f3f')                 # pipe


def build():
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    tunnel(d, *FRAMES['greenhouse-tunnel'][:2], 6)
    tunnel(d, *FRAMES['greenhouse-tunnel-short'][:2], 3)
    dome(d, *FRAMES['grow-dome'][:2])
    skid(d, *FRAMES['nutrient-skid'][:2])
    return img


if __name__ == '__main__':
    build().save(Path(sys.argv[1]) / 'greenhouses-guide.png')
