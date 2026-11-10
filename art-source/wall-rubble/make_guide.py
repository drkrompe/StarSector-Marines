"""Geometry guide for the wall rubble sheet (FacePainter's debris, drawn by WallRubble).

One sheet of 4 x 4 cells of 384 px on a transparent canvas. Each row is one kind of debris and each column a variant:

1. Heaps: a long, low pile a fallen wall leaves along its foot. The wall stood along the top of the cell, so the pile's
   top edge runs nearly straight across and it thins out toward the bottom.
2. Slabs: one or two big broken pieces of wall.
3. Clusters: a handful of mid-sized chunks lying together.
4. Scatter: small fragments thrown out across the ground, with a soft dust feather (blurred grey).

Every footprint is flat mid grey. ImageGen fills it with debris; the grey says where and how big, never what it
looks like. The renderer tints each piece by its wall's material, so the art is neutral.

    python art-source/wall-rubble/make_guide.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

CELL = 384
COLS = ROWS = 4
GREY = (128, 128, 128, 255)
HERE = Path(__file__).resolve().parent


def chunk(d, cx, cy, rng, r, sides=None):
    """An angular broken piece: a convex-ish polygon with uneven sides, never a regular shape."""
    n = sides or rng.randint(4, 7)
    base = rng.uniform(0, 2 * math.pi)
    angles = sorted(base + 2 * math.pi * (i + rng.uniform(-0.3, 0.3)) / n for i in range(n))
    stretch = rng.uniform(0.6, 1.0)
    turn = rng.uniform(0, math.pi)
    pts = []
    for a in angles:
        k = r * rng.uniform(0.7, 1.1)
        x, y = math.cos(a) * k, math.sin(a) * k * stretch
        pts.append((cx + x * math.cos(turn) - y * math.sin(turn), cy + x * math.sin(turn) + y * math.cos(turn)))
    d.polygon(pts, fill=GREY)


def heap(d, ox, oy, rng):
    """A pile along the cell's top edge: a lumpy band, deepest in its middle, chunks breaking its lower edge."""
    left, right = ox + CELL * rng.uniform(0.04, 0.1), ox + CELL * rng.uniform(0.9, 0.96)
    top = oy + CELL * rng.uniform(0.12, 0.18)
    depth = CELL * rng.uniform(0.36, 0.5)
    phase = rng.uniform(0, 2 * math.pi)
    pts = [(left, top + rng.uniform(-6, 6))]
    for i in range(1, 24):
        u = i / 24
        pts.append((left + (right - left) * u, top + rng.uniform(-8, 4)))
    pts.append((right, top + rng.uniform(-6, 6)))
    for i in range(24, -1, -1):
        u = i / 24
        bulge = math.sin(math.pi * u) ** 0.3 * (0.8 + 0.2 * math.sin(u * 9 + phase))
        pts.append((left + (right - left) * u, top + depth * bulge * rng.uniform(0.85, 1.05) + 6))
    d.polygon(pts, fill=GREY)
    for _ in range(rng.randint(5, 8)):
        u = rng.uniform(0.15, 0.85)
        x = left + (right - left) * u
        y = top + depth * math.sin(math.pi * u) ** 0.3 * 0.85 + rng.uniform(4, 30)
        chunk(d, x, y, rng, rng.uniform(10, 24))


def slabs(d, ox, oy, rng):
    cx, cy = ox + CELL / 2, oy + CELL / 2
    if rng.random() < 0.5:
        chunk(d, cx + rng.uniform(-15, 15), cy + rng.uniform(-15, 15), rng, CELL * rng.uniform(0.3, 0.36), 5)
    else:
        chunk(d, cx - CELL * 0.14, cy - CELL * 0.08, rng, CELL * 0.24, 5)
        chunk(d, cx + CELL * 0.18, cy + CELL * 0.14, rng, CELL * 0.17, 4)
    for _ in range(3):
        chunk(d, cx + rng.uniform(-130, 130), cy + rng.uniform(-130, 130), rng, rng.uniform(8, 16))


def cluster(d, ox, oy, rng):
    cx, cy = ox + CELL / 2, oy + CELL / 2
    for _ in range(rng.randint(4, 6)):
        a, dist = rng.uniform(0, 2 * math.pi), rng.uniform(0, CELL * 0.26)
        chunk(d, cx + math.cos(a) * dist, cy + math.sin(a) * dist, rng, rng.uniform(26, 50))


def scatter(img, d, ox, oy, rng):
    cx, cy = ox + CELL / 2, oy + CELL / 2
    dust = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dd = ImageDraw.Draw(dust)
    dd.ellipse([cx - 140, cy - 110, cx + 140, cy + 110], fill=(128, 128, 128, 110))
    dust = dust.filter(ImageFilter.GaussianBlur(28))
    img.alpha_composite(dust)
    for _ in range(rng.randint(14, 22)):
        a, dist = rng.uniform(0, 2 * math.pi), rng.uniform(0, CELL * 0.38)
        chunk(d, cx + math.cos(a) * dist, cy + math.sin(a) * dist * 0.8, rng, rng.uniform(6, 16))


def main():
    rng = random.Random(1110)
    img = Image.new("RGBA", (CELL * COLS, CELL * ROWS), (0, 0, 0, 0))
    for row, kind in enumerate((heap, slabs, cluster, scatter)):
        for col in range(COLS):
            ox, oy = col * CELL, row * CELL
            d = ImageDraw.Draw(img)
            if kind is scatter:
                scatter(img, d, ox, oy, rng)
            else:
                kind(d, ox, oy, rng)
    img.save(HERE / "guide.png")


if __name__ == "__main__":
    main()
