"""Geometry guide for the refinery's utility props (`RefineryGround`): the pieces that say what each house does.

Six square pieces on a 1920x1080 canvas, three to a row in two rows, each drawn in a 320 px cell inside a 384 px
frame, seen from straight above with the light from the upper left:

  1 furnace stack: a fired heater's chimney, smoke drifting to the lower right, its long shadow
  2 cooling tower: the utilities' round tower, its rim and the wet fill inside, steam rising
  3 pump skid: two pumps and their motors on a steel skid, pipe stubs either end
  4 comms mast: the control room's lattice mast with its dish, its shadow to the lower right
  5 loading arm island: a tanker bay's concrete island with its folded loading arms under a small canopy
  6 gas cylinder rack: the lab's caged sample-gas cylinders and an analyser cabinet

    python art-source/refinery/refinery-utilities-guide.py
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
CONCRETE = (172, 168, 158, 255)
SHADOW = (0, 0, 0, 110)
SMOKE = (90, 90, 90, 120)
STEAM = (235, 235, 235, 110)


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


def rect(i, x0, y0, x1, y1, color, width=0):
    a, b = p(i, x0, y0)
    c, e = p(i, x1, y1)
    if width:
        d.rectangle([a, b, c, e], outline=color, width=width)
    else:
        d.rectangle([a, b, c, e], fill=color)


def sweep(i, cx, cy, r, tx, ty, color, grow=-.15):
    """A disc swept to (tx, ty): a tall piece's shadow, or a plume."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    steps = 40
    for k in range(steps + 1):
        t = k / steps
        x, y = p(i, cx + (tx - cx) * t, cy + (ty - cy) * t)
        rr = r * CELL * (1 + grow * t)
        ld.ellipse([x - rr, y - rr, x + rr, y + rr], fill=color)
    img.alpha_composite(layer)


# 1 furnace stack: centre (.40, .40), chimney radius .10, its shadow and smoke to the lower right
sweep(0, .40, .40, .10, .85, .85, SHADOW)
circle(0, .40, .40, .13, LIGHT)            # platform ring
circle(0, .40, .40, .10, STEEL)            # chimney
circle(0, .40, .40, .06, DARK)             # flue
sweep(0, .44, .38, .06, .80, .60, SMOKE, grow=1.2)

# 2 cooling tower: centre (.5, .5), rim radius .40
circle(1, .5, .5, .42, CONCRETE)
circle(1, .5, .5, .33, DARK)               # wet fill
circle(1, .46, .46, .22, STEAM)

# 3 pump skid: a band across the middle
rect(2, .10, .32, .90, .68, DARK)          # skid
rect(2, .18, .38, .42, .62, STEEL)         # pump and motor
rect(2, .58, .38, .82, .62, STEEL)
rect(2, .0, .46, .10, .54, DARK)           # pipe stubs
rect(2, .90, .46, 1.0, .54, DARK)

# 4 comms mast: base centre (.5, .5), half-width .09, dish to the upper left
sweep(3, .5, .5, .05, .85, .85, SHADOW, grow=0)
rect(3, .41, .41, .59, .59, DARK, width=8)
d.line([p(3, .41, .41), p(3, .59, .59)], fill=DARK, width=5)
d.line([p(3, .41, .59), p(3, .59, .41)], fill=DARK, width=5)
circle(3, .38, .36, .12, LIGHT)            # dish
circle(3, .38, .36, .03, DARK)

# 5 loading arm island: a long concrete island down the middle, a canopy over it
rect(4, .36, .05, .64, .95, CONCRETE)
rect(4, .28, .25, .72, .75, STEEL, width=8)  # canopy edge
for y in (.35, .65):
    d.line([p(4, .50, y), p(4, .80, y - .06), p(4, .84, y + .04)], fill=DARK, width=10)  # arms, folded

# 6 gas cylinder rack: a caged rack of cylinders and the analyser cabinet
rect(5, .14, .24, .62, .62, DARK, width=8)
for cx in (.22, .32, .42, .52):
    for cy in (.34, .52):
        circle(5, cx, cy, .045, LIGHT)
rect(5, .68, .28, .88, .58, STEEL)

img.save(Path(__file__).with_name("refinery-utilities-guide.png"))
