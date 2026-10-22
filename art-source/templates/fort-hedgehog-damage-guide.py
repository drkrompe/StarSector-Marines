"""Geometry guide for the fort hedgehog's damage states (fort-hedgehog-damage-guide.png).

Two 512px full-cell frames side by side, each one game cell, drawn at the intact hedgehog's own proportions
(fort-earthworks.raw.png frame [384,576,192,192]: three crossed girders about 22/192 of the cell wide, a
bolted hub at the centre, arms reaching to the frame edge).

  frame 0  damaged:   the hedgehog still standing on its hub; the vertical girder intact, one diagonal bent
                      at a kink below the hub, the other knocked loose from the hub and lying askew beside it.
  frame 1  destroyed: toppled flat; three girders lying low and splayed on the ground, no hub crossing,
                      the hub plate and two bolts separate. Low wreck, no standing cover.

Flat grey fills with darker outlines; transparent everywhere else. Run: python fort-hedgehog-damage-guide.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

CELL = 512
S = CELL / 192.0
W = 22 * S
FILL, EDGE, HUB = (150, 150, 150, 255), (70, 70, 70, 255), (115, 115, 115, 255)


def girder(d, ox, cx, cy, angle_deg, length, width=W):
    a = math.radians(angle_deg)
    ux, uy = math.cos(a) * length / 2, math.sin(a) * length / 2
    px, py = -math.sin(a) * width / 2, math.cos(a) * width / 2
    pts = [(ox + cx - ux + px, cy - uy + py), (ox + cx + ux + px, cy + uy + py),
           (ox + cx + ux - px, cy + uy - py), (ox + cx - ux - px, cy - uy - py)]
    d.polygon(pts, fill=FILL, outline=EDGE, width=4)


def segment(d, ox, x0, y0, x1, y1, width=W):
    length = math.hypot(x1 - x0, y1 - y0)
    girder(d, ox, (x0 + x1) / 2, (y0 + y1) / 2, math.degrees(math.atan2(y1 - y0, x1 - x0)), length, width)


def hub(d, ox, cx, cy, r):
    pts = [(ox + cx + r * math.cos(math.radians(30 + 60 * i)), cy + r * math.sin(math.radians(30 + 60 * i)))
           for i in range(6)]
    d.polygon(pts, fill=HUB, outline=EDGE, width=4)


def main():
    img = Image.new("RGBA", (CELL * 2, CELL), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = CELL / 2
    # Damaged: vertical girder intact; upper-left-to-lower-right diagonal bent at a kink; the other diagonal
    # torn off the hub and lying askew, shifted down-left, its upper end clear of the hub.
    girder(d, 0, c, c, 90, 180 * S)
    segment(d, 0, c - 62 * S, c - 62 * S, c, c)
    segment(d, 0, c, c, c + 30 * S, c + 46 * S)
    segment(d, 0, c + 30 * S, c + 46 * S, c + 44 * S, c + 82 * S)
    girder(d, 0, c - 36 * S, c + 60 * S, -24, 96 * S)
    hub(d, 0, c, c, 16 * S)
    # Destroyed: three girders down flat and splayed, one bent; hub plate and bolts loose.
    ox = CELL
    girder(d, ox, c - 6 * S, c - 38 * S, 8, 150 * S)
    segment(d, ox, c - 66 * S, c + 6 * S, c + 2 * S, c + 18 * S)
    segment(d, ox, c + 2 * S, c + 18 * S, c + 70 * S, c - 2 * S)
    girder(d, ox, c - 4 * S, c + 52 * S, -6, 140 * S)
    hub(d, ox, c + 48 * S, c + 30 * S, 13 * S)
    for bx, by in ((c - 58 * S, c + 34 * S), (c + 28 * S, c - 64 * S)):
        d.ellipse((ox + bx - 4 * S, by - 4 * S, ox + bx + 4 * S, by + 4 * S), fill=HUB, outline=EDGE, width=3)
    img.save(Path(__file__).with_suffix(".png").with_name("fort-hedgehog-damage-guide.png"))


if __name__ == "__main__":
    main()
