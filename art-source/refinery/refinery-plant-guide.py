"""Geometry guide for the refinery's plant props (`RefineryGround`, the refining Raid site).

Six square pieces on a 1920x1080 canvas, three to a row in two rows, each drawn in a 320 px cell inside a 384 px
frame, seen from straight above with the light from the upper left, so a tall piece throws its shadow to the lower
right inside its own cell:

  1 distillation column: its head and platform ring in the upper left, its long shadow to the lower right
  2 stripper column: a smaller column, a shorter shadow
  3 flare stack: the lattice derrick, the burning flame at its tip and the heat glow round it
  4 floating-roof storage tank: filling its cell
  5 volatiles sphere: a ball tank on legs, its shadow to the lower right
  6 spill: a pool of crude and fuel on the ground

    python art-source/refinery/refinery-plant-guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

CELL, FRAME, COLS = 320, 384, 3
LEFT = (1920 - COLS * FRAME) // 2
TOP = (1080 - 2 * FRAME) // 2
img = Image.new("RGBA", (1920, 1080), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

STEEL = (150, 150, 146, 255)
LIGHT = (196, 194, 186, 255)
DARK = (70, 72, 74, 255)
SHADOW = (0, 0, 0, 110)
FLAME = (240, 140, 40, 255)
GLOW = (250, 190, 90, 90)
OIL = (24, 22, 20, 230)


def origin(i):
    return LEFT + (i % COLS) * FRAME + (FRAME - CELL) // 2, TOP + (i // COLS) * FRAME + (FRAME - CELL) // 2


def p(i, x, y):
    ox, oy = origin(i)
    return ox + x * CELL, oy + y * CELL


def circle(i, cx, cy, r, color, width=0):
    x, y = p(i, cx, cy)
    box = [x - r * CELL, y - r * CELL, x + r * CELL, y + r * CELL]
    if width:
        d.ellipse(box, outline=color, width=width)
    else:
        d.ellipse(box, fill=color)


def shadow(i, cx, cy, r, tx, ty):
    """A tall piece's cast shadow: its head's disc swept to (tx, ty)."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    steps = 40
    for k in range(steps + 1):
        t = k / steps
        x, y = p(i, cx + (tx - cx) * t, cy + (ty - cy) * t)
        rr = r * CELL * (1 - .15 * t)
        ld.ellipse([x - rr, y - rr, x + rr, y + rr], fill=SHADOW)
    img.alpha_composite(layer)


# 1 distillation column
shadow(0, .36, .36, .20, .86, .86)
circle(0, .36, .36, .26, LIGHT)            # platform ring
circle(0, .36, .36, .20, STEEL)            # column head
circle(0, .36, .36, .07, DARK)             # top nozzle and davit base
x0, y0 = p(0, .54, .33)
x1, y1 = p(0, 1.0, .39)
d.rectangle([x0, y0, x1, y1], fill=DARK)    # overhead vapour line leaving east
x0, y0 = p(0, .14, .30)
x1, y1 = p(0, .20, .42)
d.rectangle([x0, y0, x1, y1], fill=DARK)    # caged ladder on the ring

# 2 stripper column
shadow(1, .40, .40, .15, .74, .74)
circle(1, .40, .40, .20, LIGHT)
circle(1, .40, .40, .15, STEEL)
circle(1, .40, .40, .05, DARK)
x0, y0 = p(1, .55, .37)
x1, y1 = p(1, 1.0, .43)
d.rectangle([x0, y0, x1, y1], fill=DARK)

# 3 flare stack
circle(2, .42, .42, .40, GLOW)             # heat glow on the ground
shadow(2, .42, .42, .08, .80, .80)
x0, y0 = p(2, .29, .29)
x1, y1 = p(2, .55, .55)
d.rectangle([x0, y0, x1, y1], outline=DARK, width=10)   # lattice derrick
d.line([x0, y0, x1, y1], fill=DARK, width=6)
d.line([x0, y1, x1, y0], fill=DARK, width=6)
circle(2, .42, .42, .07, STEEL)            # stack tip
circle(2, .40, .34, .12, FLAME)            # the flame, leaning with the wind

# 4 floating-roof tank
circle(3, .5, .5, .47, DARK)               # shell rim
circle(3, .5, .5, .43, STEEL)              # floating roof
circle(3, .5, .5, .36, LIGHT, width=8)     # pontoon ring
x0, y0 = p(3, .48, .5)
x1, y1 = p(3, .52, .93)
d.rectangle([x0, y0, x1, y1], fill=DARK)   # rolling ladder

# 5 volatiles sphere
shadow(4, .44, .44, .30, .62, .62)
for lx, ly in ((.16, .44), (.72, .44), (.44, .16), (.44, .72)):
    circle(4, lx, ly, .04, DARK)           # legs
circle(4, .44, .44, .30, STEEL)
circle(4, .36, .36, .10, LIGHT)            # highlight
circle(4, .44, .44, .30, DARK, width=5)    # equator stair and girder

# 6 spill
x, y = p(5, .5, .5)
d.polygon([p(5, .15, .45), p(5, .30, .20), p(5, .55, .25), p(5, .80, .18), p(5, .88, .45),
           p(5, .75, .70), p(5, .82, .85), p(5, .55, .82), p(5, .35, .88), p(5, .20, .70)], fill=OIL)

img.save(Path(__file__).with_name("refinery-plant-guide.png"))
