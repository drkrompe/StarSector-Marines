"""Geometry guide for the refinery's unit heaters in the tileset's flat style (`RefineryGround`).

Three square pieces in one row on a 1920x1080 canvas, each in a 320 px cell inside a 384 px frame, seen from straight
above as flat plates (the works tileset's way), each with only a short soft shadow to the lower right:

  1 distillation column: head plate centred at (.40, .40), platform ring radius .20, vapour line east
  2 stripper column: the same, smaller head, the same ring and centre
  3 furnace stack: chimney ring radius .125 centred at (.375, .375), a short plume of smoke east

    python art-source/refinery/refinery-heaters-guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

CELL, FRAME, COLS = 320, 384, 3
LEFT = (1920 - COLS * FRAME) // 2
TOP = (1080 - FRAME) // 2
img = Image.new("RGBA", (1920, 1080), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

STEEL = (130, 130, 128, 255)
LIGHT = (165, 163, 158, 255)
DARK = (70, 72, 74, 255)
SHADOW = (0, 0, 0, 90)
SMOKE = (90, 90, 90, 90)


def p(i, x, y):
    return LEFT + i * FRAME + (FRAME - CELL) // 2 + x * CELL, TOP + (FRAME - CELL) // 2 + y * CELL


def circle(i, cx, cy, r, color, width=0):
    x, y = p(i, cx, cy)
    box = [x - r * CELL, y - r * CELL, x + r * CELL, y + r * CELL]
    if width:
        d.ellipse(box, outline=color, width=width)
    else:
        d.ellipse(box, fill=color)


def rect(i, x0, y0, x1, y1, color):
    a, b = p(i, x0, y0)
    c, e = p(i, x1, y1)
    d.rectangle([a, b, c, e], fill=color)


def soft(i, cx, cy, r, color):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([*p(i, cx - r, cy - r), *p(i, cx + r, cy + r)], fill=color)
    img.alpha_composite(layer)


for i, head in ((0, .15), (1, .11)):
    soft(i, .43, .43, .20, SHADOW)           # a short shadow, offset a little
    circle(i, .40, .40, .20, LIGHT)          # platform ring
    circle(i, .40, .40, head, STEEL)         # head plate
    circle(i, .40, .40, .04, DARK)           # top nozzle
    rect(i, .58, .37, 1.0, .43, DARK)        # vapour line east

soft(2, .40, .40, .13, SHADOW)
circle(2, .375, .375, .125, LIGHT)           # platform ring
circle(2, .375, .375, .09, STEEL)            # chimney
circle(2, .375, .375, .06, DARK)             # flue
for k in range(6):
    soft(2, .47 + .06 * k, .40 + .02 * k, .05 + .015 * k, SMOKE)

img.save(Path(__file__).with_name("refinery-heaters-guide.png"))
