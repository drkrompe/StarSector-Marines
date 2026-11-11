"""Geometry guide for the refinery's works props redrawn in the works tileset's flat style (`RefineryGround`).

Six square frames, two rows of three on a 1920x1080 canvas, FRAME px each at PITCH px apart:

  row 0: fired heater (new), cooling tower, pump skid
  row 1: radio mast, loading island, gas rack

The heater is drawn here as flat plates: a firebox the full frame wide over rows .125 to .875 (the 8 by 6 cells it
stands on, drawn in an 8-cell frame a row above its box), its roof panels, a row of burner ports along its south
side, and its stack's ring at (.80, .50) with smoke drifting east. The other five are the frames of
refinery-utilities.png as shipped before this restyle (commit ddd842e9e6; the sheet is now cut from this guide's
master by refinery-utilities-export.py), set in place as the silhouettes to repaint.

    python art-source/refinery/refinery-works-guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OLD = ROOT / "mod/graphics/doodads/refinery-utilities.png"
FRAME, PITCH, SHEET_FRAME = 384, 420, 480
LEFT = (1920 - 2 * PITCH - FRAME) // 2
TOP = (1080 - PITCH - FRAME) // 2
img = Image.new("RGBA", (1920, 1080), (0, 0, 0, 0))

STEEL = (130, 130, 128, 255)
LIGHT = (165, 163, 158, 255)
DARK = (70, 72, 74, 255)
SHADOW = (0, 0, 0, 90)
SMOKE = (90, 90, 90, 90)


def origin(col, row):
    return LEFT + col * PITCH, TOP + row * PITCH


# The five kept pieces: (col, row) on the old sheet -> (col, row) here.
old = Image.open(OLD).convert("RGBA")
for (oc, orow), (c, r) in {(1, 0): (1, 0), (2, 0): (2, 0), (0, 1): (0, 1), (1, 1): (1, 1), (2, 1): (2, 1)}.items():
    frame = old.crop((oc * SHEET_FRAME, orow * SHEET_FRAME, (oc + 1) * SHEET_FRAME, (orow + 1) * SHEET_FRAME))
    img.alpha_composite(frame.resize((FRAME, FRAME), Image.LANCZOS), origin(c, r))

# The fired heater.
ox, oy = origin(0, 0)
layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
d = ImageDraw.Draw(layer)


def p(x, y):
    return ox + x * FRAME, oy + y * FRAME


d.rectangle([*p(.04, .16), *p(1.0, .91)], fill=SHADOW)                      # short shadow, lower right
img.alpha_composite(layer)
d = ImageDraw.Draw(img)
d.rectangle([*p(.0, .125), *p(.97, .875)], fill=STEEL)                       # firebox
for k in range(4):                                                           # roof panels
    x0 = .04 + k * .155
    d.rectangle([*p(x0, .19), *p(x0 + .13, .62)], fill=LIGHT)
for k in range(5):                                                           # burner ports, south side
    cx = .08 + k * .12
    d.ellipse([*p(cx - .03, .71), *p(cx + .03, .79)], fill=DARK)
d.ellipse([*p(.80 - .13, .50 - .13), *p(.80 + .13, .50 + .13)], fill=LIGHT)  # stack platform ring
d.ellipse([*p(.80 - .09, .50 - .09), *p(.80 + .09, .50 + .09)], fill=STEEL)  # chimney
d.ellipse([*p(.80 - .055, .50 - .055), *p(.80 + .055, .50 + .055)], fill=DARK)  # flue
smoke = Image.new("RGBA", img.size, (0, 0, 0, 0))
sd = ImageDraw.Draw(smoke)
for k in range(4):
    r = .035 + .012 * k
    cx, cy = .88 + .03 * k, .47 - .03 * k
    sd.ellipse([*p(cx - r, cy - r), *p(cx + r, cy + r)], fill=SMOKE)
img.alpha_composite(smoke)

img.save(Path(__file__).with_name("refinery-works-guide.png"))
