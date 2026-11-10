"""Geometry guide for the raised ore conveyor (`voids.md`, the open pit; `OreLine`).

Five one-cell pieces in a row on a 1920x1080 canvas, each drawn at 320 px a cell inside a 384 px frame, its belt
running east (+x): the belt with ore on it (seamless east to west), the girder frame over it (seamless east to west,
its middle open for the belt), the corner (the belt enters from the west and leaves north), the trestle bent standing
under it, and the drive housing where a run ends at a building.

    python art-source/tilesets/ore-conveyor-guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

CELL, FRAME = 320, 384
TOP = (1080 - FRAME) // 2
img = Image.new("RGBA", (1920, 1080), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

BELT = "#2b2622"
ORE = "#7a5638"
GIRDER = "#6d6a60"
WALK = "#8d887a"
RAIL = "#c9a23a"
DARK = "#2a2d2e"
RUST = "#8a5a36"


def cell(i):
    """The top-left of piece i's cell on the canvas."""
    return i * FRAME + (FRAME - CELL) // 2, TOP + (FRAME - CELL) // 2


def r(i, x0, y0, x1, y1, color):
    """A rectangle in piece i's cell, in shares of the cell."""
    ox, oy = cell(i)
    d.rectangle([ox + x0 * CELL, oy + y0 * CELL, ox + x1 * CELL - 1, oy + y1 * CELL - 1], fill=color)


BELT_Y0, BELT_Y1 = .34, .66

# 0 belt: the moving rubber belt, ore heaped down its middle; full width, seamless along it.
r(0, 0, BELT_Y0, 1, BELT_Y1, BELT)
r(0, 0, .40, 1, .60, ORE)

# 1 frame: girders either side of the belt and a walkway on the south side; the belt's stripe left open.
r(1, 0, .24, 1, BELT_Y0, GIRDER)
r(1, 0, BELT_Y1, 1, .76, GIRDER)
r(1, 0, .76, 1, .92, WALK)
r(1, 0, .92, 1, .95, RAIL)
for t in range(4):
    r(1, .05 + t * .25, .24, .11 + t * .25, .76, DARK)   # cross ties under the belt, seen at its edges

# 2 corner: the belt in from the west, a transfer chute, out to the north.
r(2, 0, BELT_Y0, .66, BELT_Y1, BELT)
r(2, BELT_Y0, 0, .66, .66, BELT)
r(2, 0, .40, .60, .60, ORE)
r(2, .40, 0, .60, .60, ORE)
r(2, 0, .24, .24, BELT_Y0, GIRDER)
r(2, .24, 0, BELT_Y0, .34, GIRDER)
r(2, 0, BELT_Y1, .76, .76, GIRDER)
r(2, BELT_Y1, 0, .76, .76, GIRDER)
r(2, .30, .30, .70, .70, RUST)                          # the chute housing at the turn

# 3 bent: the trestle leg under the belt, its feet out beyond the belt on both sides.
r(3, .38, .04, .62, .96, GIRDER)
r(3, .30, 0, .70, .14, DARK)                            # footing, north
r(3, .30, .86, .70, 1, DARK)                            # footing, south
r(3, .44, .14, .56, .86, RUST)                          # bracing

# 4 drive: the head drive where a run ends at a building, the belt in from the west.
r(4, 0, BELT_Y0, .3, BELT_Y1, BELT)
r(4, 0, .40, .3, .60, ORE)
r(4, .2, .12, .95, .88, GIRDER)
r(4, .3, .22, .85, .78, DARK)
r(4, .45, .30, .7, .70, RUST)                           # drum and motor
r(4, .2, .12, .95, .18, RAIL)

img.save(Path(__file__).with_name("ore-conveyor-guide.png"))
