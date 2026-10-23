"""Geometry guide for the vehicle scuff sheet (combat-durability-nouns.md, Worn plate).

Four columns by three rows of 384 px cells on a transparent canvas. Each row is one stage of worn plate, and each
column is one variant: row 0 scrapes and bullet pocks, row 1 scorch, row 2 gouges and dents. Each mark's footprint is
drawn in flat mid grey, centred in its cell and inside the middle 70%, so a rotated mark never reaches a neighbour.
ImageGen fills each footprint with the mark's material; the grey says where and how big, never what it looks like.

    python art-source/vehicles/scuffs/make_guide.py
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
        off = (i - (count - 1) / 2) * width * 2.2
        a = math.radians(angle + rng.uniform(-6, 6))
        ln = length * rng.uniform(0.6, 1.0)
        nx, ny = -math.sin(a) * off, math.cos(a) * off
        x0, y0 = cx + nx - math.cos(a) * ln / 2, cy + ny - math.sin(a) * ln / 2
        x1, y1 = cx + nx + math.cos(a) * ln / 2, cy + ny + math.sin(a) * ln / 2
        d.line([(x0, y0), (x1, y1)], fill=GREY, width=max(2, int(width * rng.uniform(0.5, 1.0))))


def pocks(d, cx, cy, rng, count, spread, size):
    for _ in range(count):
        x, y = cx + rng.uniform(-spread, spread), cy + rng.uniform(-spread, spread)
        r = size * rng.uniform(0.6, 1.2)
        d.ellipse([x - r, y - r, x + r, y + r], fill=GREY)


def blob(d, cx, cy, rng, rx, ry, lobes):
    pts = []
    for i in range(48):
        t = 2 * math.pi * i / 48
        k = 1 + 0.18 * math.sin(lobes * t + rng.uniform(0, 0.4)) + rng.uniform(-0.06, 0.06)
        pts.append((cx + math.cos(t) * rx * k, cy + math.sin(t) * ry * k))
    d.polygon(pts, fill=GREY)


def gash(d, cx, cy, rng, length, width, angle):
    a = math.radians(angle)
    pts_top, pts_bot = [], []
    for i in range(13):
        u = i / 12 - 0.5
        w = width * math.sin(math.pi * (u + 0.5)) * rng.uniform(0.7, 1.15)
        x, y = cx + math.cos(a) * u * length, cy + math.sin(a) * u * length
        pts_top.append((x - math.sin(a) * w, y + math.cos(a) * w))
        pts_bot.append((x + math.sin(a) * w * rng.uniform(0.6, 1.0), y - math.cos(a) * w * rng.uniform(0.6, 1.0)))
    d.polygon(pts_top + pts_bot[::-1], fill=GREY)


def main():
    rng = random.Random(23)
    img = Image.new("RGBA", (CELL * COLS, CELL * ROWS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = lambda col, row: (col * CELL + CELL / 2, row * CELL + CELL / 2)

    # Row 0: scrapes and pocks -- the first hits a plate takes.
    x, y = c(0, 0); streaks(d, x, y, rng, 3, 240, 20, -30)
    x, y = c(1, 0); pocks(d, x, y, rng, 5, 80, 26)
    x, y = c(2, 0); streaks(d, x, y, rng, 1, 260, 28, 20); pocks(d, x + 70, y - 70, rng, 2, 22, 22)
    x, y = c(3, 0); pocks(d, x - 50, y + 40, rng, 3, 40, 24); streaks(d, x + 40, y - 40, rng, 2, 170, 18, -55)

    # Row 1: scorch -- broad soft patches.
    x, y = c(0, 1); blob(d, x, y, rng, 120, 100, 3)
    x, y = c(1, 1); blob(d, x, y, rng, 135, 80, 5)
    x, y = c(2, 1); blob(d, x - 30, y, rng, 85, 85, 4); blob(d, x + 60, y + 40, rng, 55, 50, 3)
    x, y = c(3, 1); blob(d, x, y, rng, 110, 115, 6)

    # Row 2: gouges and dents -- the deepest marks, a plate nearly stripped.
    x, y = c(0, 2); gash(d, x, y, rng, 250, 34, -25)
    x, y = c(1, 2); d.ellipse([x - 95, y - 85, x + 95, y + 85], fill=GREY)
    x, y = c(2, 2); blob(d, x, y, rng, 80, 75, 7); gash(d, x + 40, y + 60, rng, 140, 18, 35)
    x, y = c(3, 2); gash(d, x, y, rng, 230, 28, 40); d.ellipse([x - 70, y - 90, x - 10, y - 30], fill=GREY)

    # Scorch is soft-edged; the rest keep hard edges.
    soft = img.crop((0, CELL, CELL * COLS, CELL * 2)).filter(ImageFilter.GaussianBlur(10))
    img.paste(soft, (0, CELL))
    img.save(HERE / "guide.png")
    print("wrote", HERE / "guide.png", img.size)


if __name__ == "__main__":
    main()
