"""Geometry guide for the open pit's machines (`voids.md`, the open pit; `PitMachines`).

Six frames on a 1920x1080 canvas, 3 columns x 2 rows of 640x540, each machine centred in its frame with its
front to the east (+x), drawn overhead at one scale (30 px a metre) so the sheet keeps the machines' real sizes
against each other. Frame order is `PitMachines.Kind`'s: loaded haul truck, empty haul truck, face shovel,
blasthole drill, crawler dozer, wheel loader.

    python art-source/voids/open-pit/machines-guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

PX = 30  # pixels a metre
FW, FH = 640, 540
img = Image.new("RGBA", (1920, 1080), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

PAINT = "#c9a23a"
DARK = "#2a2f31"
TREAD = "#171e22"
GROOVE = "#3c4245"
STEEL = "#6d7476"
GLASS = "#263e49"
ORE = "#7a5a3c"


def box(ox, oy, x, y, w, h, color):
    """A rectangle of w x h metres whose top-left is (x, y) metres from the frame's centre."""
    cx, cy = ox + FW / 2, oy + FH / 2
    d.rectangle([cx + x * PX, cy + y * PX, cx + (x + w) * PX - 1, cy + (y + h) * PX - 1], fill=color)


def tread(ox, oy, x, y, w, h, along=True):
    """A tyre or crawler track seen from above: dark, with grooves across the direction of travel."""
    box(ox, oy, x, y, w, h, TREAD)
    step = .55
    t = x + .2
    while t < x + w - .2:
        box(ox, oy, t, y + .12, .1, h - .24, GROOVE)
        t += step


def haul_truck(ox, oy, loaded):
    # 15 m long, 9.6 m wide; dump body over the rear and its canopy over the cab.
    for tx, w in ((4.0, 2.4), (-6.6, 2.6), (-3.8, 2.6)):
        tread(ox, oy, tx, -4.8, w, 1.5)
        tread(ox, oy, tx, 3.3, w, 1.5)
    box(ox, oy, -7.5, -3.4, 15.0, 6.8, DARK)
    box(ox, oy, -7.4, -4.4, 12.6, 8.8, PAINT)           # dump body
    box(ox, oy, -6.9, -3.9, 11.2, 7.8, "#a8862f")       # its floor
    for r in range(5):
        box(ox, oy, -6.9 + r * 2.4, -3.9, .25, 7.8, "#8b6f27")  # ribs
    box(ox, oy, 5.2, -4.4, 2.2, 8.8, PAINT)             # canopy over the cab
    box(ox, oy, 5.4, -4.2, 1.8, 2.2, GLASS)             # cab, the driver's side
    box(ox, oy, 7.1, -3.0, .35, 6.0, STEEL)             # front deck rail
    if loaded:
        d.ellipse([ox + FW / 2 - 5.8 * PX, oy + FH / 2 - 3.2 * PX, ox + FW / 2 + 4.0 * PX, oy + FH / 2 + 3.2 * PX],
                  fill=ORE)


def shovel(ox, oy):
    # Crawler tracks 8.5 m, house 7 x 6 m, boom and stick reaching 6 m ahead to a 3.6 m wide bucket.
    tread(ox, oy, -8.0, -3.9, 8.5, 1.6)
    tread(ox, oy, -8.0, 2.3, 8.5, 1.6)
    box(ox, oy, -7.4, -2.3, 7.3, 4.6, DARK)             # carbody
    box(ox, oy, -9.0, -3.2, 7.4, 6.4, PAINT)            # house
    box(ox, oy, -9.0, -3.2, 1.6, 6.4, "#8b7a52")        # counterweight
    box(ox, oy, -2.4, -3.2, 1.8, 1.8, GLASS)            # cab
    box(ox, oy, -1.6, -.8, 6.4, 1.6, PAINT)             # boom
    box(ox, oy, 4.2, -.6, 2.2, 1.2, STEEL)              # stick
    box(ox, oy, 6.2, -1.8, 2.6, 3.6, "#5d6467")         # bucket
    for t in range(4):
        box(ox, oy, 8.8, -1.6 + t * .95, .45, .45, STEEL)  # teeth


def drill(ox, oy):
    # Crawler tracks 9 m; deck 10 x 5 m; the mast laid along it for tramming, a lattice 13 m long.
    tread(ox, oy, -5.0, -3.0, 9.0, 1.3)
    tread(ox, oy, -5.0, 1.7, 9.0, 1.3)
    box(ox, oy, -6.0, -2.3, 10.0, 4.6, "#7d8a8c")       # deck
    box(ox, oy, -6.0, -2.3, 3.0, 4.6, PAINT)            # power pack
    box(ox, oy, 1.6, -2.3, 2.0, 1.6, GLASS)             # cab
    box(ox, oy, -4.0, -.6, 13.0, 1.2, STEEL)            # mast
    for t in range(13):
        box(ox, oy, -4.0 + t, -.6, .18, 1.2, "#41484a")  # lattice
    box(ox, oy, 8.6, -.9, .8, 1.8, PAINT)               # mast head


def dozer(ox, oy):
    # Tracks 5 m, body 6 x 3.6 m, blade 6 m wide ahead, ripper behind.
    tread(ox, oy, -3.2, -2.9, 5.6, 1.1)
    tread(ox, oy, -3.2, 1.8, 5.6, 1.1)
    box(ox, oy, -3.4, -1.8, 6.0, 3.6, PAINT)
    box(ox, oy, -1.2, -1.2, 1.8, 2.4, GLASS)            # cab
    box(ox, oy, 2.6, -1.8, 1.4, 3.6, DARK)              # push arms
    box(ox, oy, 4.0, -3.2, .8, 6.4, PAINT)              # blade
    box(ox, oy, -4.6, -.3, 1.2, .6, STEEL)              # ripper


def loader(ox, oy):
    # Articulated: rear frame and engine, front frame, a 5 m wide bucket ahead.
    for tx in (-4.6, 1.2):
        tread(ox, oy, tx, -2.9, 2.2, 1.1)
        tread(ox, oy, tx, 1.8, 2.2, 1.1)
    box(ox, oy, -5.2, -1.8, 5.4, 3.6, PAINT)            # rear frame, engine
    box(ox, oy, -1.4, -1.4, 1.6, 2.8, GLASS)            # cab
    box(ox, oy, .2, -1.2, 3.8, 2.4, DARK)               # front frame, lift arms
    box(ox, oy, 4.0, -2.6, 1.6, 5.2, "#5d6467")         # bucket


frames = [lambda o, p: haul_truck(o, p, True), lambda o, p: haul_truck(o, p, False), shovel, drill, dozer, loader]
for i, draw in enumerate(frames):
    draw((i % 3) * FW, (i // 3) * FH)
img.save(Path(__file__).with_name("machines-guide.png"))
