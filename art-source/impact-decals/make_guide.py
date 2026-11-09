"""Geometry guides for the impact decal sheets (ImpactDecals, DecalKind).

Three sheets of 4 x 4 cells of 384 px on a transparent canvas: ballistic, explosive, and energy-and-fire. Each row
is one mark family and each column one variant of it. Every footprint is flat mid grey, centred on its cell, so the
renderer can turn a mark about its centre. A directional mark (gouge, beam streak, flame sweep) lies along +x, the
way the round travelled. Soft-edged marks (scorch, halos) are blurred grey. ImageGen fills each footprint with the
mark's material; the grey says where and how big, never what it looks like.

    python art-source/impact-decals/make_guide.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

CELL = 384
COLS = ROWS = 4
GREY = (128, 128, 128, 255)
GUTTER = 8
HERE = Path(__file__).resolve().parent


def disc(d, x, y, r):
    d.ellipse([x - r, y - r, x + r, y + r], fill=GREY)


def blob(d, cx, cy, rng, rx, ry, lobes, jag=0.18):
    """An irregular patch: its radius is a sum of a few random harmonics, so no outline reads as a regular star."""
    harmonics = [(k, rng.uniform(0, 2 * math.pi), jag * rng.uniform(0.3, 1.0) / (1 + abs(k - lobes) * 0.6))
                 for k in range(2, lobes + 4)]
    pts = []
    for i in range(96):
        t = 2 * math.pi * i / 96
        k = 1 + sum(a * math.sin(n * t + ph) for n, ph, a in harmonics) * 0.7 + rng.uniform(-0.03, 0.03)
        pts.append((cx + math.cos(t) * rx * k, cy + math.sin(t) * ry * k))
    d.polygon(pts, fill=GREY)


def pepper(d, cx, cy, rng, count, inner, outer, rmin, rmax):
    for _ in range(count):
        a = rng.uniform(0, 2 * math.pi)
        dist = rng.uniform(inner, outer)
        disc(d, cx + math.cos(a) * dist, cy + math.sin(a) * dist, rng.uniform(rmin, rmax))


def rays(d, cx, cy, rng, count, inner, outer, width):
    for i in range(count):
        a = 2 * math.pi * i / count + rng.uniform(-0.25, 0.25)
        ln = rng.uniform(inner, outer)
        d.line([(cx, cy), (cx + math.cos(a) * ln, cy + math.sin(a) * ln)], fill=GREY,
               width=max(3, int(width * rng.uniform(0.5, 1.1))))


def gash(d, cx, cy, rng, length, width, taper_back=0.35):
    """A gouge along +x: narrow where the round touched down (left), widest near the far end where it dug in."""
    top, bot = [], []
    for i in range(17):
        u = i / 16
        w = width * (taper_back + (1 - taper_back) * math.sin(math.pi * min(1.0, u * 1.15) / 2) ** 1.5)
        if u > 0.87:
            w *= max(0.15, (1 - u) / 0.13)
        x = cx - length / 2 + u * length
        top.append((x, cy - w * rng.uniform(0.8, 1.1)))
        bot.append((x, cy + w * rng.uniform(0.8, 1.1)))
    d.polygon(top + bot[::-1], fill=GREY)


def streak(d, cx, cy, rng, length, width, wobble):
    pts = []
    n = 24
    for i in range(n + 1):
        u = i / n
        pts.append((cx - length / 2 + u * length, cy + math.sin(u * 7 + rng.uniform(0, 0.3)) * wobble))
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        u = (x0 - (cx - length / 2)) / length
        w = width * (0.55 + 0.45 * math.sin(math.pi * u))
        d.line([(x0, y0), (x1, y1)], fill=GREY, width=max(3, int(w)))


def fan(d, cx, cy, rng, length, spread_deg):
    """A swept burn widening along +x from its left end: overlapping lobes that grow as the flame spread."""
    half = math.tan(math.radians(spread_deg / 2))
    for i in range(9):
        u = (i + 0.5) / 9
        r = min(120.0, max(14.0, length * u * half * rng.uniform(0.75, 1.05)))
        blob(d, cx - length / 2 + u * length - r * 0.3, cy + rng.uniform(-0.25, 0.25) * r, rng, r, r * 0.9, 4, 0.25)


def soft(img, box, radius):
    """Blurs each cell of a row band on its own, so no cell's grey bleeds into its neighbour."""
    x0, y0, x1, y1 = box
    for cx in range(x0, x1, CELL):
        cell = (cx, y0, cx + CELL, y1)
        img.paste(img.crop(cell).filter(ImageFilter.GaussianBlur(radius)), cell[:2])


def ballistic(rng):
    """Row 0 small-arms strikes on the ground, row 1 on a wall, row 2 heavy-round ground gouges, row 3 heavy wall holes."""
    img = Image.new("RGBA", (CELL * COLS, CELL * ROWS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = lambda col, row: (col * CELL + CELL / 2, row * CELL + CELL / 2)
    # Row 0: a pock with its spat dirt; two; three; a short burst walking along +x.
    x, y = c(0, 0); disc(d, x, y, 46); pepper(d, x, y, rng, 9, 60, 130, 6, 13)
    x, y = c(1, 0); disc(d, x - 50, y + 20, 38); disc(d, x + 55, y - 30, 34); pepper(d, x, y, rng, 12, 70, 140, 5, 11)
    x, y = c(2, 0)
    for dx, dy in ((-60, -40), (55, -20), (-10, 60)):
        disc(d, x + dx, y + dy, rng.uniform(28, 36))
    pepper(d, x, y, rng, 12, 90, 150, 5, 10)
    x, y = c(3, 0)
    for i in range(4):
        disc(d, x - 120 + i * 80, y + rng.uniform(-22, 22), rng.uniform(26, 34))
    pepper(d, x, y, rng, 8, 60, 150, 5, 9)
    # Row 1: wall pocks -- a hole with a chipped spall ring, then clusters.
    x, y = c(0, 1); blob(d, x, y, rng, 75, 70, 7, 0.22)
    x, y = c(1, 1); blob(d, x - 55, y - 35, rng, 55, 52, 6, 0.25); blob(d, x + 60, y + 45, rng, 50, 48, 7, 0.25)
    x, y = c(2, 1)
    for dx, dy in ((-80, -60), (60, -70), (-50, 70), (75, 50)):
        blob(d, x + dx, y + dy, rng, 42, 40, 6, 0.25)
    x, y = c(3, 1)
    for _ in range(6):
        blob(d, x + rng.uniform(-120, 120), y + rng.uniform(-120, 120), rng, 30, 28, 5, 0.25)
    # Row 2: heavy-round gouges along +x with clods thrown ahead of them.
    for col, (ln, w) in enumerate(((290, 34), (260, 44), (290, 28), (230, 52))):
        x, y = c(col, 2)
        gash(d, x, y + rng.uniform(-10, 10), rng, ln, w)
        pepper(d, x + ln / 2 - 30, y, rng, 7, 15, 40, 6, 12)
    # Row 3: heavy wall holes with radial cracks.
    for col, r in enumerate((80, 95, 70, 88)):
        x, y = c(col, 3)
        blob(d, x, y, rng, r, r * 0.95, 8, 0.2)
        rays(d, x, y, rng, 6 + col, r + 30, r + 85, 7)
    return img


def explosive(rng):
    """Row 0 fragmentation pepper, row 1 rocket/grenade blast stars, row 2 shell craters, row 3 thrown debris."""
    img = Image.new("RGBA", (CELL * COLS, CELL * ROWS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = lambda col, row: (col * CELL + CELL / 2, row * CELL + CELL / 2)
    for col in range(4):
        x, y = c(col, 0)
        blob(d, x, y, rng, 40 + col * 6, 38 + col * 6, 5, 0.25)
        pepper(d, x, y, rng, 30 + col * 6, 60, 170, 4, 10)
    for col in range(4):
        x, y = c(col, 1)
        r = 70 + col * 8
        rays(d, x, y, rng, 9 + col, r + 40, 160, 22)
        blob(d, x, y, rng, r, r * 0.92, 6, 0.22)
    for col in range(4):
        x, y = c(col, 2)
        r = 105 + col * 8
        rays(d, x, y, rng, 12, r + 15, 165, 16)
        blob(d, x, y, rng, r, r * 0.95, 9, 0.12)
    for col in range(4):
        x, y = c(col, 3)
        for _ in range(9 + col * 2):
            a = rng.uniform(0, 2 * math.pi)
            dist = rng.uniform(0, 130)
            blob(d, x + math.cos(a) * dist, y + math.sin(a) * dist, rng, rng.uniform(14, 34), rng.uniform(12, 28),
                 rng.choice((3, 4, 5)), 0.3)
    # The fragmentation core and the blast stars' soot are soft at their edge.
    soft(img, (0, CELL, CELL * COLS, CELL * 2), 6)
    return img


def energy_fire(rng):
    """Row 0 pulse scorches, row 1 beam burn streaks along +x, row 2 heavy-energy melt craters, row 3 fire scorch."""
    img = Image.new("RGBA", (CELL * COLS, CELL * ROWS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = lambda col, row: (col * CELL + CELL / 2, row * CELL + CELL / 2)
    x, y = c(0, 0); disc(d, x, y, 95)
    x, y = c(1, 0); blob(d, x, y, rng, 130, 75, 4, 0.1)
    x, y = c(2, 0); disc(d, x - 55, y - 40, 72); disc(d, x + 60, y + 50, 64)
    x, y = c(3, 0); disc(d, x, y, 80); pepper(d, x, y, rng, 10, 95, 135, 7, 12)
    for col, (ln, w, wob) in enumerate(((330, 34, 8), (300, 26, 14), (340, 44, 6), (260, 30, 18))):
        x, y = c(col, 1)
        streak(d, x, y, rng, ln, w, wob)
    for col in range(4):
        x, y = c(col, 2)
        r = 95 + col * 10
        blob(d, x, y, rng, r, r * 0.95, 7, 0.1)
        pepper(d, x, y, rng, 8 + col * 2, r + 18, r + 45, 7, 14)
    x, y = c(0, 3); blob(d, x, y, rng, 125, 115, 4, 0.2)
    x, y = c(1, 3); blob(d, x - 30, y - 15, rng, 95, 88, 5, 0.2); blob(d, x + 60, y + 50, rng, 70, 62, 4, 0.2)
    x, y = c(2, 3); fan(d, x - 25, y, rng, 250, 40)
    x, y = c(3, 3); fan(d, x - 20, y, rng, 240, 50)
    # Pulse scorches and fire are soft-edged; beam streaks and melt craters keep a crisper rim.
    soft(img, (0, 0, CELL * COLS, CELL), 14)
    soft(img, (0, CELL * 3, CELL * COLS, CELL * 4), 16)
    return img


def clip_gutters(img):
    """Clears a thin gutter round every cell, the margin the exporter's cuts rely on."""
    d = ImageDraw.Draw(img)
    for k in range(COLS + 1):
        d.rectangle([k * CELL - GUTTER, 0, k * CELL + GUTTER - 1, img.height], fill=(0, 0, 0, 0))
    for k in range(ROWS + 1):
        d.rectangle([0, k * CELL - GUTTER, img.width, k * CELL + GUTTER - 1], fill=(0, 0, 0, 0))


SHEETS = {"ballistic": ballistic, "explosive": explosive, "energy-fire": energy_fire}


def main():
    for i, (name, make) in enumerate(SHEETS.items()):
        img = make(random.Random(31 + i))
        clip_gutters(img)
        out = HERE / f"guide-{name}.png"
        img.save(out)
        print("wrote", out, img.size)


if __name__ == "__main__":
    main()
