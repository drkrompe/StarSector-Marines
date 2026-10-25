"""Geometry guide for the Rav wear sheet (combat-durability-nouns.md, Worn plate; colony-breach-nouns.md, Matriarch).

Four columns by three rows of 384 px cells on a transparent canvas. Each row is one stage of organic wear on a Rav's
chitin, and each column is one variant: row 0 scored and chipped chitin, row 1 bruising and cracked plate, row 2
weeping wounds. Each mark's footprint is drawn in flat mid grey, centred in its cell and inside the middle 70%, so a
turned mark never reaches a neighbour. ImageGen fills each footprint with the mark's material; the grey says where
and how big, never what it looks like.

    python art-source/rav-wear/make_guide.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

CELL = 384
COLS, ROWS = 4, 3
GREY = (128, 128, 128, 255)
HERE = Path(__file__).resolve().parent


def streaks(d, cx, cy, rng, count, length, width, angle):
    for i in range(count):
        off = (i - (count - 1) / 2) * width * 2.4
        a = math.radians(angle + rng.uniform(-8, 8))
        ln = length * rng.uniform(0.6, 1.0)
        nx, ny = -math.sin(a) * off, math.cos(a) * off
        x0, y0 = cx + nx - math.cos(a) * ln / 2, cy + ny - math.sin(a) * ln / 2
        x1, y1 = cx + nx + math.cos(a) * ln / 2, cy + ny + math.sin(a) * ln / 2
        d.line([(x0, y0), (x1, y1)], fill=GREY, width=max(2, int(width * rng.uniform(0.5, 1.0))))


def chips(d, cx, cy, rng, count, spread, size):
    for _ in range(count):
        x, y = cx + rng.uniform(-spread, spread), cy + rng.uniform(-spread, spread)
        r = size * rng.uniform(0.6, 1.2)
        pts = [(x + math.cos(t) * r * rng.uniform(0.7, 1.2), y + math.sin(t) * r * rng.uniform(0.7, 1.2))
               for t in [2 * math.pi * i / 7 for i in range(7)]]
        d.polygon(pts, fill=GREY)


def blob(d, cx, cy, rng, rx, ry, lobes):
    pts = []
    for i in range(48):
        t = 2 * math.pi * i / 48
        k = 1 + 0.2 * math.sin(lobes * t + rng.uniform(0, 0.4)) + rng.uniform(-0.06, 0.06)
        pts.append((cx + math.cos(t) * rx * k, cy + math.sin(t) * ry * k))
    d.polygon(pts, fill=GREY)


def gash(d, cx, cy, rng, length, width, angle):
    a = math.radians(angle)
    top, bot = [], []
    for i in range(13):
        u = i / 12 - 0.5
        w = width * math.sin(math.pi * (u + 0.5)) * rng.uniform(0.7, 1.15)
        x, y = cx + math.cos(a) * u * length, cy + math.sin(a) * u * length
        top.append((x - math.sin(a) * w, y + math.cos(a) * w))
        bot.append((x + math.sin(a) * w * rng.uniform(0.6, 1.0), y - math.cos(a) * w * rng.uniform(0.6, 1.0)))
    d.polygon(top + bot[::-1], fill=GREY)


def run(d, x, y, length, width):
    """A drip of ichor running down from a wound: a tapering tail ending in a bead."""
    d.polygon([(x - width, y), (x + width, y), (x + width * 0.35, y + length), (x - width * 0.35, y + length)],
              fill=GREY)
    r = width * 0.8
    d.ellipse([x - r, y + length - r, x + r, y + length + r], fill=GREY)


def main():
    rng = random.Random(31)
    img = Image.new("RGBA", (CELL * COLS, CELL * ROWS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = lambda col, row: (col * CELL + CELL / 2, row * CELL + CELL / 2)

    # Row 0: scored and chipped chitin -- the first rounds the plate turns.
    x, y = c(0, 0); streaks(d, x, y, rng, 3, 230, 16, -30)
    x, y = c(1, 0); chips(d, x, y, rng, 5, 80, 24)
    x, y = c(2, 0); streaks(d, x, y, rng, 1, 250, 22, 20); chips(d, x + 70, y - 70, rng, 2, 20, 22)
    x, y = c(3, 0); chips(d, x - 50, y + 40, rng, 3, 40, 22); streaks(d, x + 40, y - 40, rng, 2, 160, 14, -55)

    # Row 1: bruising and cracked plate -- broad soft patches.
    x, y = c(0, 1); blob(d, x, y, rng, 115, 95, 3)
    x, y = c(1, 1); blob(d, x, y, rng, 130, 78, 5)
    x, y = c(2, 1); blob(d, x - 30, y, rng, 85, 85, 4); blob(d, x + 60, y + 40, rng, 55, 50, 3)
    x, y = c(3, 1); blob(d, x, y, rng, 105, 110, 6)

    # Row 2: weeping wounds -- plate torn through to the sinew, ichor running from them.
    x, y = c(0, 2); gash(d, x, y - 20, rng, 230, 36, -20); run(d, x - 10, y, 90, 14)
    x, y = c(1, 2); blob(d, x, y - 25, rng, 80, 70, 7); run(d, x + 10, y + 30, 80, 16)
    x, y = c(2, 2); gash(d, x - 10, y - 30, rng, 180, 26, 30); blob(d, x + 50, y + 40, rng, 45, 40, 5)
    x, y = c(3, 2); gash(d, x, y - 10, rng, 220, 30, 50); run(d, x + 30, y + 20, 70, 12); run(d, x - 40, y - 10, 60, 10)

    # Bruises are soft-edged; the rest keep hard edges.
    soft = img.crop((0, CELL, CELL * COLS, CELL * 2)).filter(ImageFilter.GaussianBlur(10))
    img.paste(soft, (0, CELL))
    img.save(HERE / "guide.png")
    print("wrote", HERE / "guide.png", img.size)


if __name__ == "__main__":
    main()
