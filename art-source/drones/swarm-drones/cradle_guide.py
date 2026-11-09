"""Deterministic geometry guide for the flock cradle's look candidates (`air-nouns.md`, Flocks).

Writes cradle-guide.png beside this script: a 1024x1024 sheet of four 512x512 panels, each one candidate design of the
launch housing that keeps a flock of fourteen swarm drones docked, in strict overhead orthographic, unrotated (a
structure is drawn as it stands). Painted once per lineage livery, like the drone guide.

  top-left     rack      an armoured rectangular rack, two rows of seven docking cups either side of a spine
  top-right    carousel  a round drum, fourteen launch hatches round the rim, a mast at the hub
  bottom-left  hangar    a low armoured box whose two roof doors stand half open on a dark bay
  bottom-right silo      a square block of launch tubes, four by four less two, a few hatches open

Flat colours are placeholders that say what each region is, not material. pack.py reads PANELS.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
PANEL = 512
PLATE = "#c8ccd0"
SEAM = "#7d858f"
DARK = "#2b2e33"
BAY = "#3a4048"
HATCH = "#9aa2ab"
ACCENT = "#e0b830"
LENS = "#c23a2a"

PANELS = {"rack": (0, 0), "carousel": (1, 0), "hangar": (0, 1), "silo": (1, 1)}


def cup(d, cx, cy, r):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=HATCH)
    d.ellipse((cx - r * .7, cy - r * .7, cx + r * .7, cy + r * .7), fill=BAY)


def rack(d, cx, cy):
    d.rectangle((cx - 200, cy - 110, cx + 200, cy + 110), fill=PLATE)
    d.rectangle((cx - 200, cy - 14, cx + 200, cy + 14), fill=SEAM)
    for row in (-1, 1):
        for k in range(7):
            cup(d, cx - 168 + k * 56, cy + row * 60, 22)
    for x in (cx - 200, cx + 188):
        for y0 in range(cy - 110, cy + 110, 24):
            d.rectangle((x, y0, x + 12, y0 + 12), fill=ACCENT)
    d.ellipse((cx - 8, cy - 8, cx + 8, cy + 8), fill=LENS)


def carousel(d, cx, cy):
    d.ellipse((cx - 200, cy - 200, cx + 200, cy + 200), fill=PLATE)
    d.ellipse((cx - 196, cy - 196, cx + 196, cy + 196), outline=ACCENT, width=10)
    for k in range(14):
        a = 2 * math.pi * k / 14
        cup(d, cx + 140 * math.cos(a), cy + 140 * math.sin(a), 26)
    d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill=SEAM)
    d.ellipse((cx - 30, cy - 30, cx + 30, cy + 30), fill=DARK)
    d.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), fill=LENS)


def hangar(d, cx, cy):
    d.rectangle((cx - 210, cy - 150, cx + 210, cy + 150), fill=PLATE)
    d.rectangle((cx - 170, cy - 110, cx + 170, cy + 110), fill=BAY)
    for k in range(7):
        for row in (-1, 1):
            x, y = cx - 132 + k * 44, cy + row * 46
            d.ellipse((x - 14, y - 14, x + 14, y + 14), fill=HATCH)
    d.rectangle((cx - 210, cy - 150, cx - 60, cy + 150), fill=SEAM)
    d.rectangle((cx + 60, cy - 150, cx + 210, cy + 150), fill=SEAM)
    for x0 in (cx - 210, cx + 196):
        d.rectangle((x0, cy - 150, x0 + 14, cy + 150), fill=ACCENT)
    d.ellipse((cx - 190, cy - 136, cx - 172, cy - 118), fill=LENS)


def silo(d, cx, cy):
    d.rectangle((cx - 190, cy - 190, cx + 190, cy + 190), fill=PLATE)
    open_tubes = {(0, 1), (2, 3), (3, 0)}
    k = 0
    for i in range(4):
        for j in range(4):
            if (i, j) in ((0, 0), (3, 3)):
                continue
            x, y = cx - 135 + i * 90, cy - 135 + j * 90
            d.rectangle((x - 36, y - 36, x + 36, y + 36), fill=SEAM)
            if (i, j) in open_tubes:
                d.ellipse((x - 28, y - 28, x + 28, y + 28), fill=DARK)
            else:
                d.ellipse((x - 28, y - 28, x + 28, y + 28), fill=HATCH)
            k += 1
    for (i, j) in ((0, 0), (3, 3)):
        x, y = cx - 135 + i * 90, cy - 135 + j * 90
        d.rectangle((x - 36, y - 36, x + 36, y + 36), fill=ACCENT)
        d.ellipse((x - 10, y - 10, x + 10, y + 10), fill=LENS)


DRAW = {"rack": rack, "carousel": carousel, "hangar": hangar, "silo": silo}


def main():
    img = Image.new("RGBA", (PANEL * 2, PANEL * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for name, (col, row) in PANELS.items():
        DRAW[name](d, col * PANEL + PANEL // 2, row * PANEL + PANEL // 2)
    img.save(HERE / "cradle-guide.png")


if __name__ == "__main__":
    main()
