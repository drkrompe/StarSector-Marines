"""Geometry guide for the open pit's megaproject machines (`voids.md`, the open pit; `PitMachines`).

Eight frames on a 1920x1080 canvas, 4 columns x 2 rows of 480x540, each piece centred in its frame with its front
(or, for the gallery bay, its run) to the east (+x). Each frame has its own scale, `PitMachines.Kind.pxPerMeter`, so
every piece fills its frame and ImageGen draws it large. Frame order is `PitMachines.Kind`'s: cutter, crusher, loaded
ore crawler, empty ore crawler, drill gantry, transfer tower, gallery bay, bench hopper.

    python art-source/voids/open-pit/megaworks-guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

FW, FH = 480, 540
img = Image.new("RGBA", (1920, 1080), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

HULL = "#8d8676"
PLATE = "#6f6a5e"
DARK = "#2a2d2e"
TREAD = "#17191a"
GROOVE = "#3a3d3e"
RUST = "#8a5a36"
HAZARD = "#c9a23a"
GLOW = "#e08a3a"
ORE = "#6b4a33"
PIT = "#120f0d"


class Frame:
    def __init__(self, i, px):
        self.ox, self.oy, self.px = (i % 4) * FW + FW / 2, (i // 4) * FH + FH / 2, px

    def box(self, x, y, w, h, color):
        p = self.px
        d.rectangle([self.ox + x * p, self.oy + y * p, self.ox + (x + w) * p - 1, self.oy + (y + h) * p - 1], fill=color)

    def disc(self, x, y, r, color):
        p = self.px
        d.ellipse([self.ox + (x - r) * p, self.oy + (y - r) * p, self.ox + (x + r) * p, self.oy + (y + r) * p], fill=color)

    def track(self, x, y, w, h):
        self.box(x, y, w, h, TREAD)
        t = x + .3
        while t < x + w - .3:
            self.box(t, y + .15, .25, h - .3, GROOVE)
            t += 1.0


# 0 cutter: a crawling cutting-drum excavator, 44 x 30 m.
f = Frame(0, 9)
for tx, ty in ((-18, -15), (-18, 9), (-2, -15), (-2, 9)):
    f.track(tx, ty, 12, 6)
f.box(-17, -9, 26, 18, HULL)                 # main hull
f.box(-15, -7, 10, 14, PLATE)                # power house
f.box(-4, -5, 8, 10, DARK)                   # slewing ring housing
f.box(-23, -2, 8, 4, PLATE)                  # discharge boom, back
f.box(9, -2.5, 12, 5, HULL)                  # cutting boom
f.box(19, -11, 5, 22, RUST)                  # cutting drum across the front
for t in range(9):
    f.box(23.5, -10.5 + t * 2.5, 1, 1, DARK)  # picks
f.box(1, -8, 4, 3, GLOW)                     # cab glazing

# 1 crusher: an in-pit crushing station, 24 x 20 m; the hopper at the front, the spine leaves from the back.
f = Frame(1, 18)
f.track(-12, -10, 22, 3)
f.track(-12, 7, 22, 3)
f.box(-11, -7, 20, 14, HULL)
f.box(1, -6, 10, 12, DARK)                   # hopper rim
f.box(2.5, -4.5, 7, 9, PIT)                  # hopper mouth
f.disc(6, 0, 2.5, ORE)                       # ore in it
f.box(-9, -5, 9, 10, PLATE)                  # crusher housing
f.box(-12, -1.5, 4, 3, RUST)                 # discharge to the spine
f.box(-6, -7, 3, 2, GLOW)

# 2, 3 ore crawlers: tracked haulers, 18 x 9 m, loaded then empty.
for i, loaded in ((2, True), (3, False)):
    f = Frame(i, 24)
    f.track(-9, -4.5, 17, 2)
    f.track(-9, 2.5, 17, 2)
    f.box(-8.5, -3, 17, 6, PLATE)
    f.box(5, -3, 4, 6, HULL)                 # drive and sensor block at the front
    f.box(7, -1, 1.5, 2, GLOW)
    f.box(-8, -3.5, 12, 7, HULL)             # ore bin
    f.box(-7.2, -2.7, 10.4, 5.4, ORE if loaded else DARK)
    if loaded:
        f.disc(-2, 0, 2.2, "#7d5a3e")

# 4 drill gantry: straddles a bench, 14 m along it (east), 12 m across, a row of drill masts.
f = Frame(4, 30)
f.box(-7, -6, 2.5, 12, PLATE)                # leg bogie, west
f.box(4.5, -6, 2.5, 12, PLATE)               # leg bogie, east
f.track(-7, -6, 2.5, 1.6)
f.track(4.5, -6, 2.5, 1.6)
f.track(-7, 4.4, 2.5, 1.6)
f.track(4.5, 4.4, 2.5, 1.6)
f.box(-7, -1.6, 14, 3.2, HULL)               # the beam
for t in range(5):
    f.disc(-4.4 + t * 2.2, 0, .7, DARK)      # drill masts
    f.disc(-4.4 + t * 2.2, 0, .35, RUST)
f.box(-1, -5.5, 2, 3.5, HAZARD)              # power cabinet

# 5 transfer tower: the spine's head at the rim, 14 x 14 m; the gallery enters from the west and leaves east.
f = Frame(5, 28)
f.box(-7, -7, 14, 14, HULL)
f.box(-5.5, -5.5, 11, 11, PLATE)             # roof
f.box(-7, -2, 3, 4, DARK)                    # gallery in
f.box(4, -2, 3, 4, DARK)                     # gallery out
f.box(-2, -2, 4, 4, RUST)                    # head pulley housing
f.box(-6.5, -6.5, 2, 2, HAZARD)
f.box(4.5, 4.5, 2, 2, HAZARD)

# 6 gallery bay: one 12 m module of the covered conveyor, 5 m wide, cut flush at both ends.
f = Frame(6, 36)
f.box(-6, -2.5, 12, 5, HULL)
f.box(-6, -2.5, 12, .6, DARK)                # walkway rail
f.box(-6, 1.9, 12, .6, DARK)
for t in range(6):
    f.box(-5.8 + t * 2, -1.9, 1.6, 3.8, PLATE)  # roof panels
f.box(-6, -2.5, .3, 5, RUST)                 # joint flange

# 7 bench hopper: where the crawlers tip ore onto the spine, 10 x 10 m.
f = Frame(7, 36)
f.box(-5, -5, 10, 10, HULL)
f.box(-3.5, -3.5, 7, 7, DARK)
f.box(-2.7, -2.7, 5.4, 5.4, PIT)
f.disc(0, 0, 1.8, ORE)
f.box(-5, -5, 10, .8, HAZARD)

img.save(Path(__file__).with_name("megaworks-guide.png"))
