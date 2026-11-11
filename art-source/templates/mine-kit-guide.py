"""Geometry guides for the mine kit (the Long Survey's Stakes chapter, Garnir's mine country on a cold airless mining
moon): spoil heaps and tailings mounds, an ore car upright and tipped, an ore-train engine, a headframe standing and
ruined, and a pump house. Top-down, north the top of a frame, CELL px a world cell, transparent gutters.

Two 1536x1024 RGBA sheets:

- mine-heaps-guide.png: mounded piles lit from the north-west, their rims frosted pale. spoil-small (2x2),
  spoil-large (3x3), tailings-small (3x2), tailings-large (4x3). Spoil is coarse broken rock; tailings are fine,
  flat-topped and terraced.
- mine-works-guide.png: headframe (3x3, a braced steel tower over the shaft, its sheave wheels on top),
  headframe-ruined (3x3, the same tower fallen, its legs splayed, the shaft open), pump-house (3x2, a squat
  pressurised shed with a pipe run out its east side), ore-car (1x2, an open steel tub on four wheels, long axis
  north-south, ore heaped in it), ore-car-tipped (2x2, the car on its side, its ore spilled east), ore-engine (1x3, a
  low boxy electric rail engine, cab at the north end).

FRAMES gives each frame's (x, y, w, h) in pixels per sheet.

Usage: python art-source/templates/mine-kit-guide.py <out-dir>
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

W, H, CELL, G = 1536, 1024, 150, 40

HEAPS = {
    'spoil-large': (G, G, 3 * CELL, 3 * CELL),
    'spoil-small': (G + 3 * CELL + G, G, 2 * CELL, 2 * CELL),
    'tailings-small': (G + 5 * CELL + 2 * G, G, 3 * CELL, 2 * CELL),
    'tailings-large': (G, G + 3 * CELL + G, 4 * CELL, 3 * CELL),
}
WORKS = {
    'headframe': (G, G, 3 * CELL, 3 * CELL),
    'headframe-ruined': (G + 3 * CELL + G, G, 3 * CELL, 3 * CELL),
    'pump-house': (G + 6 * CELL + 2 * G, G, 3 * CELL, 2 * CELL),
    'ore-car': (G, G + 3 * CELL + G, CELL, 2 * CELL),
    'ore-car-tipped': (G + CELL + G, G + 3 * CELL + G, 2 * CELL, 2 * CELL),
    'ore-engine': (G + 3 * CELL + 2 * G, G + 3 * CELL + G, CELL, 3 * CELL),
}


def heap(img, frame, seed, terraced):
    """A mound filling its frame less a margin: a lumpy outline, shaded by its height, its rim frosted."""
    x, y, w, h = frame
    rnd = random.Random(seed)
    ph = [rnd.uniform(0, 6.3) for _ in range(4)]
    cx, cy = x + w / 2, y + h / 2
    rx, ry = w / 2 - 14, h / 2 - 14
    px = img.load()
    for j in range(y, y + h):
        for i in range(x, x + w):
            dx, dy = (i - cx) / rx, (j - cy) / ry
            a = math.atan2(dy, dx)
            lump = 1 + 0.07 * math.sin(3 * a + ph[0]) + 0.05 * math.sin(5 * a + ph[1]) + 0.03 * math.sin(9 * a + ph[2])
            r = math.hypot(dx, dy) / lump
            if r >= 1:
                continue
            height = (1 - r * r) if not terraced else min(1.0, 1.6 * (1 - r)) ** 0.7
            if terraced:
                height = math.floor(height * 4) / 4 + 0.08 * (height * 4 % 1)
            # Lit from the north-west: brighter on the slope facing up-left.
            light = 0.55 + 0.45 * max(-1.0, min(1.0, -(dx + dy) * 0.9 * (1 - height) + height * 0.4))
            base = (122, 118, 112) if not terraced else (150, 140, 120)
            shade = [int(c * (0.45 + 0.55 * light)) for c in base]
            frost = max(0.0, min(1.0, (r - 0.78) / 0.18))
            col = [int(c * (1 - frost) + f * frost) for c, f in zip(shade, (214, 224, 232))]
            px[i, j] = (*col, 255)
    d = ImageDraw.Draw(img)
    if not terraced:
        # Coarse boulders on the spoil's flanks.
        for _ in range(int(w * h / 1800)):
            a, rr = rnd.uniform(0, 2 * math.pi), rnd.uniform(0.2, 0.8)
            bx, by = cx + math.cos(a) * rr * rx, cy + math.sin(a) * rr * ry
            s = rnd.uniform(6, 14)
            d.ellipse((bx - s, by - s * 0.8, bx + s, by + s * 0.8), fill=(92, 88, 84, 255))
            d.ellipse((bx - s * 0.7, by - s * 0.7, bx + s * 0.2, by), fill=(150, 146, 140, 255))


def headframe(d, frame, ruined):
    x, y, w, h = frame
    cx, cy = x + w / 2, y + h / 2
    steel, dark, wheel = (110, 104, 96, 255), (40, 40, 42, 255), (150, 146, 138, 255)
    if ruined:
        d.rectangle((cx - 40, cy - 40, cx + 40, cy + 40), fill=dark)                     # the open shaft
        for a in (20, 110, 200, 290):                                                     # splayed legs
            r = math.radians(a)
            d.line((cx + 50 * math.cos(r), cy + 50 * math.sin(r), cx + 200 * math.cos(r + 0.3),
                    cy + 200 * math.sin(r + 0.3)), fill=steel, width=16)
        d.line((cx + 40, cy - 30, x + w - 30, y + 40), fill=steel, width=22)              # the fallen tower top
        d.ellipse((x + w - 110, y + 20, x + w - 30, y + 100), outline=wheel, width=12)   # a sheave, flat
        d.line((cx - 120, cy + 60, cx - 30, cy + 150), fill=steel, width=10)
    else:
        d.rectangle((cx - 120, cy - 120, cx + 120, cy + 120), outline=steel, width=18)   # the tower's frame
        d.line((cx - 120, cy - 120, cx + 120, cy + 120), fill=steel, width=10)            # bracing
        d.line((cx + 120, cy - 120, cx - 120, cy + 120), fill=steel, width=10)
        d.rectangle((cx - 45, cy - 45, cx + 45, cy + 45), fill=dark)                     # the shaft below
        d.rectangle((cx - 70, cy - 26, cx + 70, cy + 26), fill=steel)                     # the sheave deck
        for sx in (-40, 40):
            d.ellipse((cx + sx - 34, cy - 34, cx + sx + 34, cy + 34), outline=wheel, width=10)
        d.line((cx, cy, cx, y + h - 10), fill=(60, 58, 54, 255), width=6)                 # hoist cable south


def pump_house(d, frame):
    x, y, w, h = frame
    d.rectangle((x + 20, y + 30, x + w - 110, y + h - 30), fill=(126, 120, 108, 255))      # the shed
    d.rectangle((x + 20, y + 30, x + w - 110, y + 60), fill=(90, 86, 80, 255))             # roof ridge band
    for k in range(3):                                                                    # roof vents
        d.ellipse((x + 60 + 90 * k, y + 110, x + 110 + 90 * k, y + 160), fill=(60, 60, 62, 255))
    d.rectangle((x + w - 110, y + h / 2 - 18, x + w - 10, y + h / 2 + 18), fill=(150, 120, 70, 255))  # pipe east
    d.ellipse((x + w - 130, y + h / 2 - 30, x + w - 70, y + h / 2 + 30), fill=(104, 100, 94, 255))   # flange
    d.rectangle((x + 30, y + h - 70, x + 90, y + h - 30), fill=(80, 120, 150, 255))        # airlock door


def ore_car(d, frame):
    x, y, w, h = frame
    d.rectangle((x + 30, y + 30, x + w - 30, y + h - 30), fill=(96, 92, 86, 255))          # the tub
    d.rectangle((x + 42, y + 44, x + w - 42, y + h - 44), fill=(70, 66, 62, 255))
    for k in range(9):                                                                    # ore heaped in it
        r = random.Random(k)
        bx, by = x + w / 2 + r.uniform(-28, 28), y + h / 2 + r.uniform(-90, 90)
        d.ellipse((bx - 12, by - 10, bx + 12, by + 10), fill=(138, 128, 112, 255))
    for wy in (y + 50, y + h - 70):                                                       # wheels, either side
        d.rectangle((x + 14, wy, x + 30, wy + 26), fill=(40, 40, 42, 255))
        d.rectangle((x + w - 30, wy, x + w - 14, wy + 26), fill=(40, 40, 42, 255))


def ore_car_tipped(d, frame):
    x, y, w, h = frame
    d.rectangle((x + 30, y + 60, x + 170, y + h - 60), fill=(96, 92, 86, 255))             # the tub on its side
    d.rectangle((x + 30, y + 60, x + 60, y + h - 60), fill=(60, 58, 54, 255))              # its underside, wheels
    for wy in (y + 80, y + h - 110):
        d.ellipse((x + 18, wy, x + 48, wy + 30), fill=(40, 40, 42, 255))
    for k in range(26):                                                                   # ore spilled east
        r = random.Random(100 + k)
        bx, by = x + 190 + r.uniform(0, 80), y + h / 2 + r.uniform(-80, 80)
        s = r.uniform(7, 14)
        d.ellipse((bx - s, by - s * 0.8, bx + s, by + s * 0.8), fill=(138, 128, 112, 255))


def ore_engine(d, frame):
    x, y, w, h = frame
    d.rectangle((x + 26, y + 20, x + w - 26, y + h - 20), fill=(118, 108, 80, 255))        # the body
    d.rectangle((x + 32, y + 30, x + w - 32, y + 120), fill=(84, 80, 72, 255))             # the cab, north
    d.rectangle((x + 46, y + 44, x + w - 46, y + 74), fill=(96, 140, 160, 255))            # cab window
    for k in range(4):                                                                    # battery hatches
        d.rectangle((x + 40, y + 150 + 70 * k, x + w - 40, y + 200 + 70 * k), fill=(98, 92, 70, 255))
    d.rectangle((x + 30, y + h - 40, x + w - 30, y + h - 26), fill=(180, 150, 50, 255))    # coupling, hazard


def build(out):
    heaps = Image.new('RGBA', (W, H))
    for k, (name, frame) in enumerate(HEAPS.items()):
        heap(heaps, frame, 7 + k, name.startswith('tailings'))
    heaps = heaps.filter(ImageFilter.SMOOTH)
    heaps.save(out / 'mine-heaps-guide.png')
    works = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(works)
    headframe(d, WORKS['headframe'], False)
    headframe(d, WORKS['headframe-ruined'], True)
    pump_house(d, WORKS['pump-house'])
    ore_car(d, WORKS['ore-car'])
    ore_car_tipped(d, WORKS['ore-car-tipped'])
    ore_engine(d, WORKS['ore-engine'])
    works.save(out / 'mine-works-guide.png')
    print('wrote', out / 'mine-heaps-guide.png', out / 'mine-works-guide.png')


if __name__ == '__main__':
    build(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent)
