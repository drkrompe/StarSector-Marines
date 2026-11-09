"""Geometry guides for derelict security's second look round (`derelict-security-network.md`, Looks).

The first round's sheets (guide.py) came back photo-real and text-stamped and the owner rejected the node. This round
paints each sheet once for both lineages, Domain security on the top row and contingency security on the bottom, so
the two read as one family. The style target is Starsector's own derelict ship sprites (the owner's pick; the v2b
sheets): kitbashed, greebled sage-green and khaki plates, yellow hazard diagonals, bone-white and brass machinery.
Each guide is 1536x1024: two rows of three 512-pixel panels, flat placeholder colours with a dark line round every
silhouette.

node-guide-v2.png: three control node designs, an armoured, unarmed relay and controller structure 1.6 cells long
(NODE_CELL pixels a cell), unrotated, one per column:

  column 1  cabinet   an armoured relay cabinet: louvred door, a lamp panel, a hazard band, whip antennas laid flat
  column 2  array     a flat phased-array uplink: a tiled antenna face between two radiator fin banks
  column 3  junction  a cable junction: three capped relay canisters on a skid, joined by cable bundles

post-guide-v2.png: the picked pop-up post (guide.py's popup, same geometry) at POST_CELL pixels a cell, columns base,
folded, unfolded, about each panel's centre (the pivot): the base the one-cell square, folded and unfolded the
1.25-cell square, the unfolded head's twin barrels UP, muzzles 0.57 cells forward.

pack-v2.py reads the layouts.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
WIDTH, HEIGHT = 1536, 1024
PANEL = 512
LINEAGES = ("domain", "contingency")
PLATE = "#c8ccd0"
SEAM = "#7d858f"
DECK = "#9aa2ab"
DARK = "#2b2e33"
OUTLINE = "#15171a"
ACCENT = "#e0b830"
LENS = "#c23a2a"

NODES = ("cabinet", "array", "junction")
NODE_CELL = 275
NODE_LONG_CELLS = 1.6
POST_PARTS = ("base", "folded", "unfolded")
POST_CELL = 380


def panel_centre(col, row):
    return col * PANEL + PANEL // 2, row * PANEL + PANEL // 2


# --- nodes: pixel offsets about the panel centre; the long side is 1.6 * NODE_CELL = 440 pixels.

def box(d, cx, cy, x0, y0, x1, y1, fill, outline=None, width=0, radius=0):
    rect = (cx + x0, cy + y0, cx + x1, cy + y1)
    if radius:
        d.rounded_rectangle(rect, radius=radius, fill=fill, outline=outline, width=width)
    else:
        d.rectangle(rect, fill=fill, outline=outline, width=width)


def dot(d, cx, cy, x, y, r, fill, outline=None, width=0):
    d.ellipse((cx + x - r, cy + y - r, cx + x + r, cy + y + r), fill=fill, outline=outline, width=width)


def cabinet(d, cx, cy):
    box(d, cx, cy, -220, -140, 220, 140, PLATE, OUTLINE, 7, radius=22)
    box(d, cx, cy, -220, -140, 220, -112, ACCENT, OUTLINE, 5, radius=10)
    # Left door: louvres.
    box(d, cx, cy, -196, -94, -20, 116, DECK, OUTLINE, 5, radius=8)
    for j in range(7):
        box(d, cx, cy, -178, -74 + j * 26, -38, -62 + j * 26, DARK)
    # Right door: a lamp panel of indicator sockets.
    box(d, cx, cy, 20, -94, 196, 116, DECK, OUTLINE, 5, radius=8)
    box(d, cx, cy, 40, -74, 176, 20, SEAM, OUTLINE, 4)
    for i in range(3):
        for j in range(2):
            fill = LENS if (i, j) == (0, 0) else (ACCENT if (i, j) == (2, 1) else DARK)
            dot(d, cx, cy, 64 + i * 44, -46 + j * 40, 12, fill, OUTLINE, 3)
    box(d, cx, cy, 40, 44, 176, 96, SEAM, OUTLINE, 4)
    # Spine between the doors, two whip antennas folded flat along it.
    box(d, cx, cy, -12, -100, 12, 124, SEAM, OUTLINE, 4)
    for x in (-6, 6):
        d.line((cx + x, cy - 96, cx + x, cy + 118), fill=DARK, width=4)
    for x, y in ((-206, -98), (206, -98), (-206, 126), (206, 126)):
        dot(d, cx, cy, x, y, 7, SEAM, OUTLINE, 2)


def array(d, cx, cy):
    pts = [(-220, -100), (-180, -140), (180, -140), (220, -100), (220, 100), (180, 140), (-180, 140), (-220, 100)]
    d.polygon([(cx + x, cy + y) for x, y in pts], fill=PLATE, outline=OUTLINE, width=7)
    # Radiator fin banks either side.
    for s in (-1, 1):
        x0, x1 = (-206, -126) if s < 0 else (126, 206)
        box(d, cx, cy, x0, -110, x1, 110, SEAM, OUTLINE, 5)
        for j in range(9):
            box(d, cx, cy, x0 + 8, -98 + j * 24, x1 - 8, -88 + j * 24, DARK)
    # The flat antenna face: a 4x4 tile grid.
    box(d, cx, cy, -112, -120, 112, 120, DARK, OUTLINE, 6)
    for i in range(4):
        for j in range(4):
            x = -104 + i * 53
            y = -112 + j * 58
            box(d, cx, cy, x, y, x + 47, y + 52, DECK)
    # Hazard strips on the short ends, lamps at the corners.
    for s in (-1, 1):
        box(d, cx, cy, s * 214 - 6, -60, s * 214 + 6, 60, ACCENT)
    dot(d, cx, cy, -178, -124, 9, LENS, OUTLINE, 3)
    dot(d, cx, cy, 178, 124, 9, ACCENT, OUTLINE, 3)


def junction(d, cx, cy):
    # The skid.
    box(d, cx, cy, -220, -110, 220, 110, SEAM, OUTLINE, 7, radius=16)
    for s in (-1, 1):
        box(d, cx, cy, -210, s * 98 - 6, 210, s * 98 + 6, ACCENT)
    # Cable bundles between the canisters (drawn under them).
    for x0, x1 in ((-140, 0), (0, 140)):
        for dy in (-22, 22):
            d.line((cx + x0, cy + dy, cx + x1, cy + dy), fill=DARK, width=16)
    # Three capped relay canisters.
    for k, x in enumerate((-140, 0, 140)):
        dot(d, cx, cy, x, 0, 66, PLATE, OUTLINE, 7)
        dot(d, cx, cy, x, 0, 44, DECK, OUTLINE, 4)
        for a in range(4):
            ang = math.pi / 4 + a * math.pi / 2
            dot(d, cx, cy, x + 55 * math.cos(ang), 55 * math.sin(ang), 5, SEAM)
        dot(d, cx, cy, x, 0, 11, LENS if k == 1 else ACCENT, OUTLINE, 3)
    # A short whip antenna laid flat on the skid's end.
    d.line((cx + 196, cy - 80, cx + 196, cy + 80), fill=DARK, width=6)


NODE_DRAW = {"cabinet": cabinet, "array": array, "junction": junction}


# --- post: the popup design of guide.py, at POST_CELL pixels a cell (x right, y forward).

def cells(cx, cy, x, y):
    return cx + x * POST_CELL, cy - y * POST_CELL


def crect(d, cx, cy, x0, y0, x1, y1, fill, outline=None, width=0):
    a = cells(cx, cy, x0, y1)
    b = cells(cx, cy, x1, y0)
    d.rectangle((a[0], a[1], b[0], b[1]), fill=fill, outline=outline, width=width)


def cdisc(d, cx, cy, x, y, r, fill=None, outline=None, width=1):
    px, py = cells(cx, cy, x, y)
    d.ellipse((px - r * POST_CELL, py - r * POST_CELL, px + r * POST_CELL, py + r * POST_CELL), fill=fill,
              outline=outline, width=width)


def popup(d, cx, cy, part):
    if part == "base":
        crect(d, cx, cy, -0.48, -0.48, 0.48, 0.48, SEAM, OUTLINE, 7)
        crect(d, cx, cy, -0.44, -0.44, 0.44, 0.44, PLATE)
        cdisc(d, cx, cy, 0, 0, 0.40, fill=ACCENT, outline=OUTLINE, width=6)
        cdisc(d, cx, cy, 0, 0, 0.34, fill=DARK, outline=OUTLINE, width=5)
    elif part == "folded":
        cdisc(d, cx, cy, 0, 0, 0.36, fill=PLATE, outline=OUTLINE, width=7)
        for k in range(6):
            a = 2 * math.pi * k / 6
            x1, y1 = cells(cx, cy, 0.34 * math.cos(a), 0.34 * math.sin(a))
            d.line((cx, cy, x1, y1), fill=SEAM, width=6)
        cdisc(d, cx, cy, 0, 0, 0.06, fill=SEAM, outline=OUTLINE, width=4)
    else:
        for x in (-0.055, 0.07):
            crect(d, cx, cy, x - 0.035, 0.15, x + 0.035, 0.57, DARK, OUTLINE, 4)
        cdisc(d, cx, cy, 0, 0, 0.30, fill=PLATE, outline=OUTLINE, width=7)
        crect(d, cx, cy, -0.14, 0.10, 0.16, 0.24, DECK, OUTLINE, 4)
        cdisc(d, cx, cy, -0.16, 0.10, 0.04, fill=LENS, outline=OUTLINE, width=3)


def sheet():
    return Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))


def node_guide():
    img = sheet()
    d = ImageDraw.Draw(img)
    for row in range(len(LINEAGES)):
        for col, name in enumerate(NODES):
            NODE_DRAW[name](d, *panel_centre(col, row))
    return img


def post_guide():
    img = sheet()
    d = ImageDraw.Draw(img)
    for row in range(len(LINEAGES)):
        for col, part in enumerate(POST_PARTS):
            popup(d, *panel_centre(col, row), part)
    return img


def main():
    node_guide().save(HERE / "node-guide-v2.png")
    post_guide().save(HERE / "post-guide-v2.png")


if __name__ == "__main__":
    main()
