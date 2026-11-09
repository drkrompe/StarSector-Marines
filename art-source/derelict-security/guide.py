"""Deterministic geometry guides for derelict security's look candidates (`derelict-security-network.md`, slice 9).

Writes two 1024x1024 guides beside this script, each painted once per lineage livery (Domain security, contingency
security) so a design reads the same in both:

post-guide.png: three rows of three 341-pixel panels, one row per candidate gun post, one cell 200 pixels:
column one the base laid in the paving (one cell), column two the post folded (powered down, retracted), column
three the post unfolded (its head, twin barrels UP, muzzles 0.57 cells forward as the Vulcan mount's). The folded
and unfolded drawings sit in a 1.25-cell square (the mount's drawn size) on the panel's centre, which is the pivot.

  row 1  popup      a round head that rises out of an iris-hatched well
  row 2  clamshell  a gun box between two armoured leaves that close over it
  row 3  cupola     a low dome that turns its gun slit down into its ring

node-guide.png: four 512x512 panels, each one candidate control node, an armoured unarmed relay structure, unrotated.

  top-left     mast     a square armoured cabinet under a relay mast and dish
  top-right    pylon    an octagonal armoured pillar with a sensor dome
  bottom-left  cabinet  a bank of armoured relay cabinets with heat fins
  bottom-right bunker   a low armoured dome with antenna stubs and a cable trunk

Flat colours are placeholders that say what each region is, not material. pack.py reads the layouts.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
SHEET = 1024
PLATE = "#c8ccd0"
SEAM = "#7d858f"
DECK = "#9aa2ab"
DARK = "#2b2e33"
BAY = "#3a4048"
ACCENT = "#e0b830"
LENS = "#c23a2a"

# Post: design -> row of 341 panels; columns base, folded, unfolded. One cell is CELL pixels.
POST_ROWS = {"popup": 0, "clamshell": 1, "cupola": 2}
POST_PARTS = ("base", "folded", "unfolded")
POST_PANEL = SHEET / 3
CELL = 200
# Node: design -> (column, row) of a 512 panel.
NODE_PANELS = {"mast": (0, 0), "pylon": (1, 0), "cabinet": (0, 1), "bunker": (1, 1)}


def cells(cx, cy, x, y):
    """Cell coordinates (x right, y forward) to image pixels about a post panel's centre."""
    return cx + x * CELL, cy - y * CELL


def crect(d, cx, cy, x0, y0, x1, y1, fill):
    a = cells(cx, cy, x0, y1)
    b = cells(cx, cy, x1, y0)
    d.rectangle((a[0], a[1], b[0], b[1]), fill=fill)


def cdisc(d, cx, cy, x, y, r, fill=None, outline=None, width=1):
    px, py = cells(cx, cy, x, y)
    d.ellipse((px - r * CELL, py - r * CELL, px + r * CELL, py + r * CELL), fill=fill, outline=outline, width=width)


def barrels(d, cx, cy, half_width=0.035):
    for x in (-0.055, 0.07):
        crect(d, cx, cy, x - half_width, 0.15, x + half_width, 0.57, DARK)


def base_plate(d, cx, cy):
    crect(d, cx, cy, -0.48, -0.48, 0.48, 0.48, SEAM)
    crect(d, cx, cy, -0.44, -0.44, 0.44, 0.44, PLATE)


def popup(d, cx, cy, part):
    if part == "base":
        base_plate(d, cx, cy)
        cdisc(d, cx, cy, 0, 0, 0.40, fill=ACCENT)
        cdisc(d, cx, cy, 0, 0, 0.34, fill=BAY)
    elif part == "folded":
        cdisc(d, cx, cy, 0, 0, 0.36, fill=PLATE)
        for k in range(6):
            a = 2 * math.pi * k / 6
            x1, y1 = cells(cx, cy, 0.34 * math.cos(a), 0.34 * math.sin(a))
            d.line((cx, cy, x1, y1), fill=SEAM, width=4)
        cdisc(d, cx, cy, 0, 0, 0.06, fill=SEAM)
    else:
        barrels(d, cx, cy)
        cdisc(d, cx, cy, 0, 0, 0.30, fill=PLATE)
        crect(d, cx, cy, -0.14, 0.10, 0.16, 0.24, DECK)
        cdisc(d, cx, cy, -0.16, 0.10, 0.04, fill=LENS)


def clamshell(d, cx, cy, part):
    if part == "base":
        base_plate(d, cx, cy)
        crect(d, cx, cy, -0.34, -0.30, 0.34, 0.30, BAY)
        for x in (-0.44, 0.38):
            crect(d, cx, cy, x, -0.44, x + 0.06, 0.44, ACCENT)
    elif part == "folded":
        crect(d, cx, cy, -0.36, -0.32, 0.36, 0.32, PLATE)
        crect(d, cx, cy, -0.015, -0.32, 0.015, 0.32, DARK)
        for s in (-1, 1):
            crect(d, cx, cy, s * 0.20 - 0.02, -0.28, s * 0.20 + 0.02, 0.28, SEAM)
    else:
        for s in (-1, 1):
            crect(d, cx, cy, s * 0.40 - 0.05, -0.30, s * 0.40 + 0.05, 0.30, SEAM)
        barrels(d, cx, cy)
        crect(d, cx, cy, -0.24, -0.26, 0.24, 0.20, PLATE)
        crect(d, cx, cy, -0.20, 0.12, 0.20, 0.20, DECK)
        cdisc(d, cx, cy, 0.16, -0.18, 0.035, fill=LENS)


def cupola(d, cx, cy, part):
    if part == "base":
        base_plate(d, cx, cy)
        pts = [cells(cx, cy, 0.42 * math.cos(math.pi / 8 + k * math.pi / 4),
                     0.42 * math.sin(math.pi / 8 + k * math.pi / 4)) for k in range(8)]
        d.polygon(pts, fill=SEAM)
        cdisc(d, cx, cy, 0, 0, 0.34, fill=BAY)
    elif part == "folded":
        cdisc(d, cx, cy, 0, 0, 0.36, fill=PLATE)
        cdisc(d, cx, cy, 0, 0, 0.36, outline=SEAM, width=5)
        crect(d, cx, cy, -0.12, -0.30, 0.12, -0.24, DARK)
    else:
        barrels(d, cx, cy)
        cdisc(d, cx, cy, 0, 0, 0.34, fill=PLATE)
        crect(d, cx, cy, -0.16, 0.18, 0.18, 0.30, DARK)
        cdisc(d, cx, cy, 0, 0, 0.34, outline=SEAM, width=5)
        cdisc(d, cx, cy, -0.12, 0.08, 0.04, fill=LENS)


POST_DRAW = {"popup": popup, "clamshell": clamshell, "cupola": cupola}


def mast(d, cx, cy):
    d.rectangle((cx - 150, cy - 150, cx + 150, cy + 150), fill=PLATE)
    for sx in (-1, 1):
        for sy in (-1, 1):
            d.rectangle((cx + sx * 150 - (30 if sx > 0 else 0), cy + sy * 150 - (30 if sy > 0 else 0),
                         cx + sx * 150 + (30 if sx < 0 else 0), cy + sy * 150 + (30 if sy < 0 else 0)), fill=ACCENT)
    d.rectangle((cx - 110, cy + 60, cx + 110, cy + 120), fill=DECK)
    d.ellipse((cx - 70, cy - 120, cx + 70, cy + 20), fill=SEAM)
    d.ellipse((cx - 50, cy - 100, cx + 50, cy), fill=DECK)
    d.line((cx, cy - 50, cx + 90, cy - 130), fill=DARK, width=8)
    d.ellipse((cx - 10, cy - 60, cx + 10, cy - 40), fill=DARK)
    d.ellipse((cx + 100, cy + 80, cx + 120, cy + 100), fill=LENS)


def pylon(d, cx, cy):
    pts = [(cx + 180 * math.cos(math.pi / 8 + k * math.pi / 4), cy + 180 * math.sin(math.pi / 8 + k * math.pi / 4))
           for k in range(8)]
    d.polygon(pts, fill=PLATE)
    pts = [(cx + 120 * math.cos(math.pi / 8 + k * math.pi / 4), cy + 120 * math.sin(math.pi / 8 + k * math.pi / 4))
           for k in range(8)]
    d.polygon(pts, outline=SEAM, width=8)
    d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill=DARK)
    d.ellipse((cx - 50, cy - 50, cx + 50, cy + 50), fill=DECK)
    for k in range(8):
        a = k * math.pi / 4
        x, y = cx + 150 * math.cos(a), cy + 150 * math.sin(a)
        d.ellipse((x - 10, y - 10, x + 10, y + 10), fill=LENS if k % 2 == 0 else ACCENT)


def cabinet(d, cx, cy):
    d.rectangle((cx - 200, cy - 120, cx + 200, cy + 120), fill=PLATE)
    for k in range(3):
        x0 = cx - 180 + k * 124
        d.rectangle((x0, cy - 100, x0 + 108, cy + 100), fill=DECK)
        for j in range(6):
            d.rectangle((x0 + 14, cy - 80 + j * 28, x0 + 94, cy - 70 + j * 28), fill=SEAM)
        d.ellipse((x0 + 44, cy + 74, x0 + 64, cy + 94), fill=LENS)
    for s in (-1, 1):
        for j in range(7):
            y = cy - 105 + j * 32
            d.rectangle((cx + s * 200 - (0 if s > 0 else 30), y, cx + s * 200 + (30 if s > 0 else 0), y + 14),
                        fill=SEAM)
    d.rectangle((cx - 200, cy - 120, cx + 200, cy - 106), fill=ACCENT)


def bunker(d, cx, cy):
    d.rounded_rectangle((cx - 180, cy - 140, cx + 180, cy + 140), radius=110, fill=PLATE)
    d.rounded_rectangle((cx - 120, cy - 90, cx + 120, cy + 90), radius=70, outline=SEAM, width=8)
    d.rectangle((cx - 40, cy + 140, cx + 40, cy + 220), fill=SEAM)
    for x, y in ((cx - 140, cy - 60), (cx + 140, cy + 60)):
        d.ellipse((x - 18, y - 18, x + 18, y + 18), fill=DARK)
    d.ellipse((cx - 50, cy - 50, cx + 50, cy + 50), fill=DECK)
    d.rectangle((cx - 170, cy - 8, cx - 130, cy + 8), fill=ACCENT)
    d.rectangle((cx + 130, cy - 8, cx + 170, cy + 8), fill=ACCENT)
    d.ellipse((cx + 60, cy - 80, cx + 80, cy - 60), fill=LENS)


NODE_DRAW = {"mast": mast, "pylon": pylon, "cabinet": cabinet, "bunker": bunker}


def post_guide():
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for name, row in POST_ROWS.items():
        for col, part in enumerate(POST_PARTS):
            POST_DRAW[name](d, round((col + .5) * POST_PANEL), round((row + .5) * POST_PANEL), part)
    return img


def node_guide():
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for name, (col, row) in NODE_PANELS.items():
        NODE_DRAW[name](d, col * 512 + 256, row * 512 + 256)
    return img


def main():
    post_guide().save(HERE / "post-guide.png")
    node_guide().save(HERE / "node-guide.png")


if __name__ == "__main__":
    main()
