"""Geometry guide for street lamps seen straight down (`set-piece-districts.md`): from the zenith a lamp is its
head, the post hidden under it, so no pole is drawn lying across the ground.

One 1536x1024 RGBA sheet, one world cell = CELL px, frames laid out explicitly with transparent gutters; north is
the top of a frame. FRAMES gives each frame's (x, y, w, h) in pixels.

- lamp-cap-colonial (1x1): a cast-iron lantern's roof from above, a fluted hexagonal cap and finial over a warm
  glass rim, centred on the post.
- lamp-cap-industrial (1x1): a cobra-head luminaire's housing from above, an elongated rounded shell over its
  post, a short arm stub at its south end.
- lamp-bracket-colonial (1x1): the colonial lantern on a short scroll bracket off a wall along the frame's north
  edge; drawn overhead, a figure walks under it.
- lamp-bracket-industrial (1x1): a flood lamp on a short bracket off the same wall.

Usage: python art-source/templates/street-lamps-guide.py <out-dir>
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H, CELL, G = 1536, 1024, 300, 40

FRAMES = {
    'lamp-cap-colonial': (G, G, CELL, CELL),
    'lamp-cap-industrial': (G + CELL + G, G, CELL, CELL),
    'lamp-bracket-colonial': (G, G + CELL + G, CELL, CELL),
    'lamp-bracket-industrial': (G + CELL + G, G + CELL + G, CELL, CELL),
}


def hexagon(cx, cy, r):
    return [(cx + r * math.cos(math.pi / 6 + k * math.pi / 3), cy + r * math.sin(math.pi / 6 + k * math.pi / 3))
            for k in range(6)]


def lantern(d, cx, cy, r):
    d.ellipse((cx - r - 14, cy - r - 14, cx + r + 14, cy + r + 14), fill='#efe0a8')   # warm glass rim
    d.polygon(hexagon(cx, cy, r), fill='#2c2e2e')                                       # fluted roof
    for k in range(6):                                                                  # roof ribs
        a = math.pi / 6 + k * math.pi / 3
        d.line((cx, cy, cx + r * math.cos(a), cy + r * math.sin(a)), fill='#4a4c4b', width=5)
    d.ellipse((cx - 16, cy - 16, cx + 16, cy + 16), fill='#4a4c4b')                     # finial


def cobra(d, cx, cy):
    d.ellipse((cx - 50, cy - 80, cx + 50, cy + 50), fill='#efe0a8')        # lens glow at the rim
    d.ellipse((cx - 42, cy - 72, cx + 42, cy + 42), fill='#5b6061')        # housing shell
    d.ellipse((cx - 24, cy - 50, cx + 24, cy + 18), fill='#6f7576')        # shell crown
    d.rectangle((cx - 9, cy + 40, cx + 9, cy + 68), fill='#3a3d3d')        # arm stub


def bracket(d, x, y, cx, reach):
    d.rectangle((cx - 40, y, cx + 40, y + 22), fill='#3a3d3d')             # wall plate on the north edge
    d.rectangle((cx - 8, y + 22, cx + 8, y + reach), fill='#2c2e2e')       # bracket


def build():
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    x, y = FRAMES['lamp-cap-colonial'][:2]
    lantern(d, x + CELL // 2, y + CELL // 2, 62)
    x, y = FRAMES['lamp-cap-industrial'][:2]
    cobra(d, x + CELL // 2, y + CELL // 2 + 10)
    x, y = FRAMES['lamp-bracket-colonial'][:2]
    bracket(d, x, y, x + CELL // 2, 120)
    lantern(d, x + CELL // 2, y + 170, 56)
    x, y = FRAMES['lamp-bracket-industrial'][:2]
    bracket(d, x, y, x + CELL // 2, 110)
    d.rounded_rectangle((x + CELL // 2 - 70, y + 110, x + CELL // 2 + 70, y + 200), 16, fill='#efe0a8')
    d.rounded_rectangle((x + CELL // 2 - 62, y + 104, x + CELL // 2 + 62, y + 192), 14, fill='#5b6061')
    return img


if __name__ == '__main__':
    build().save(Path(sys.argv[1]) / 'street-lamps-guide.png')
