"""Geometry guide for the open pit's mass-driver works (`voids.md`, the open pit; `PitMachines`).

Eight frames on a 1920x1080 canvas, 4 columns x 2 rows of 480x540, each piece centred in its frame with its front
(or, for the rail bay and the capsule, its run) to the east (+x). Each frame has its own scale,
`PitMachines.Kind.pxPerMeter`, so every piece fills its frame and ImageGen draws it large. Frame order is
`PitMachines.Kind`'s: cutter, loader, loaded hauler, empty hauler, drill gantry, catch station, rail bay, capsule.

    python art-source/voids/open-pit/massdriver-guide.py
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
COIL = "#b06a2e"
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

    def ring(self, x, y, r, width, color):
        p = self.px
        d.ellipse([self.ox + (x - r) * p, self.oy + (y - r) * p, self.ox + (x + r) * p, self.oy + (y + r) * p],
                  outline=color, width=max(1, int(width * p)))

    def track(self, x, y, w, h):
        self.box(x, y, w, h, TREAD)
        t = x + .3
        while t < x + w - .3:
            self.box(t, y + .15, .25, h - .3, GROOVE)
            t += 1.0


# 0 cutter: a crawling cutting-drum excavator, 44 x 30 m (as the crawler-and-spine sheet).
f = Frame(0, 9)
for tx, ty in ((-18, -15), (-18, 9), (-2, -15), (-2, 9)):
    f.track(tx, ty, 12, 6)
f.box(-17, -9, 26, 18, HULL)
f.box(-15, -7, 10, 14, PLATE)
f.box(-4, -5, 8, 10, DARK)
f.box(-23, -2, 8, 4, PLATE)
f.box(9, -2.5, 12, 5, HULL)
f.box(19, -11, 5, 22, RUST)
for t in range(9):
    f.box(23.5, -10.5 + t * 2.5, 1, 1, DARK)
f.box(1, -8, 4, 3, GLOW)

# 1 loader: crusher and capsule loader, 24 x 20 m; the hopper at the front, the driver rail leaves from the breech
# at the back.
f = Frame(1, 18)
f.track(-12, -10, 22, 3)
f.track(-12, 7, 22, 3)
f.box(-11, -7, 20, 14, HULL)
f.box(3, -6, 8, 12, DARK)                    # hopper rim
f.box(4.5, -4.5, 5, 9, PIT)                  # hopper mouth
f.disc(7, 0, 2, ORE)
f.box(-3, -5.5, 6, 11, PLATE)                # crusher housing
f.disc(0, 0, 2.6, DARK)                      # crusher drum
f.box(-10, -4, 7, 8, PLATE)                  # capsule magazine
for t in range(3):
    f.box(-9.5 + t * 2.2, -3.4, 1.6, 6.8, RUST)  # capsules racked
f.box(-13, -1.3, 3, 2.6, GLOW)               # breech: the rail starts here
f.box(-6, -7, 3, 2, GLOW)

# 2, 3 hover haulers: 16 x 11 m, four lift-fan rings, an ore bed; loaded then empty.
for i, loaded in ((2, True), (3, False)):
    f = Frame(i, 21)
    for fx, fy in ((-6, -4.2), (-6, 4.2), (5, -4.2), (5, 4.2)):
        f.disc(fx, fy, 2.4, PLATE)           # fan nacelle
        f.disc(fx, fy, 1.8, DARK)            # fan disc
        f.disc(fx, fy, .5, GLOW)             # hub
    f.box(-8, -2.6, 16, 5.2, HULL)           # spine
    f.box(4.5, -1.8, 3.5, 3.6, PLATE)        # drive and sensor block at the front
    f.box(7, -.6, 1, 1.2, GLOW)
    f.box(-6.5, -2.2, 10, 4.4, DARK)         # ore bed
    if loaded:
        f.box(-6, -1.8, 9, 3.6, ORE)
        f.disc(-1.5, 0, 1.6, "#7d5a3e")

# 4 drill gantry (as the crawler-and-spine sheet).
f = Frame(4, 30)
f.box(-7, -6, 2.5, 12, PLATE)
f.box(4.5, -6, 2.5, 12, PLATE)
f.track(-7, -6, 2.5, 1.6)
f.track(4.5, -6, 2.5, 1.6)
f.track(-7, 4.4, 2.5, 1.6)
f.track(4.5, 4.4, 2.5, 1.6)
f.box(-7, -1.6, 14, 3.2, HULL)
for t in range(5):
    f.disc(-4.4 + t * 2.2, 0, .7, DARK)
    f.disc(-4.4 + t * 2.2, 0, .35, RUST)
f.box(-1, -5.5, 2, 3.5, HAZARD)

# 5 catch station: the driver's head on the rim, 14 x 14 m; the rail enters from the west through a decelerator coil
# bank, capsules are opened in the hall, and the ore line leaves east.
f = Frame(5, 28)
f.box(-7, -6, 14, 12, HULL)
f.box(-7, -1.2, 6, 2.4, DARK)                # rail in
for t in range(4):
    f.box(-6.6 + t * 1.4, -1.9, .6, 3.8, COIL)   # decelerator coils
f.box(-1, -4.8, 6.5, 9.6, PLATE)             # unloading hall roof
f.box(0, -2, 4, 4, RUST)                     # capsule tipper
f.box(5, -1, 2, 2, DARK)                     # conveyor out
f.box(-6.5, -5.5, 2, 2, HAZARD)
f.box(4.5, 3.5, 2, 2, HAZARD)

# 6 rail bay: one 12 m module of the mass-driver rail, 5 m wide, cut flush at both ends, a coil ring round it.
f = Frame(6, 36)
f.box(-6, -1.6, 12, 3.2, DARK)               # the launch trough
f.box(-6, -2.2, 12, .6, PLATE)               # guide rails
f.box(-6, 1.6, 12, .6, PLATE)
for t in range(12):
    f.box(-5.8 + t * 1, -1.2, .35, 2.4, GROOVE)  # ribbing
f.box(-1, -2.5, 2, 5, COIL)                  # coil ring
f.box(-.6, -2.5, 1.2, 5, RUST)
f.box(-6, -2.2, .25, 4.4, RUST)              # joint flange

# 7 capsule: a sealed ore capsule, 7 x 2.4 m, its nose glowing hot from the coils.
f = Frame(7, 48)
f.box(-3.5, -1.2, 6.2, 2.4, HULL)
f.disc(2.7, 0, 1.2, GLOW)                    # hot nose
f.box(-3.5, -1.2, .5, 2.4, DARK)             # tail seal
for t in range(3):
    f.box(-2.4 + t * 1.6, -1.2, .3, 2.4, PLATE)  # bands

img.save(Path(__file__).with_name("massdriver-guide.png"))
