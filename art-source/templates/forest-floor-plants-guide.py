"""Guide for the six open-ground forest-floor plants (nature.shrub-1..3, nature.tuft-1..3).

One plant per 512 px frame on a 3x2 sheet (1536x1024), one world cell = 480 px, plant centred in its frame.
Slot order is the tileset order: shrub-1, shrub-2, shrub-3 / tuft-1, tuft-2, tuft-3. Each plant stays inside a
disc of its stated diameter so it reads as a one-cell prop that never claims its neighbours. They are open ground:
nothing here closes a cell.

Usage: python art-source/templates/forest-floor-plants-guide.py <out.png>
"""
import math
import random
import sys

from PIL import Image, ImageDraw

PITCH, CELL = 512, 480
# (diameter in cells, kind, base colour)
PLANTS = [
    (0.92, "fern", (84, 118, 58)),      # shrub-1 broad low fern rosette
    (0.70, "bush", (78, 102, 56)),      # shrub-2 small leafy scrub
    (0.82, "flower", (92, 112, 60)),    # shrub-3 low leafy bush with small pale flowers
    (0.62, "grass", (104, 126, 62)),    # tuft-1 upright grass tuft
    (0.80, "grass", (110, 128, 66)),    # tuft-2 wider grass tuft
    (0.88, "seed", (122, 128, 70)),     # tuft-3 tall wild grass with seed heads
]


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c) + (255,)


def main(out):
    img = Image.new("RGBA", (PITCH * 3, PITCH * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for slot, (diam, kind, col) in enumerate(PLANTS):
        rnd = random.Random(slot)
        cx, cy = slot % 3 * PITCH + PITCH // 2, slot // 3 * PITCH + PITCH // 2
        r = diam * CELL / 2
        if kind in ("fern",):
            for i in range(9):
                a = i * 2 * math.pi / 9 + rnd.uniform(-.15, .15)
                ex, ey = cx + math.cos(a) * r, cy + math.sin(a) * r
                d.line((cx, cy, ex, ey), fill=shade(col, .7), width=6)
                for t in range(2, 10):
                    px, py = cx + (ex - cx) * t / 10, cy + (ey - cy) * t / 10
                    w = r * .16 * (1 - t / 12)
                    for s in (-1, 1):
                        d.line((px, py, px - math.sin(a) * w * s, py + math.cos(a) * w * s), fill=shade(col, 1.1), width=7)
        elif kind in ("bush", "flower"):
            for _ in range(46):
                a, rr = rnd.uniform(0, 2 * math.pi), r * math.sqrt(rnd.random()) * .82
                x, y, s = cx + math.cos(a) * rr, cy + math.sin(a) * rr, r * rnd.uniform(.14, .22)
                d.ellipse((x - s, y - s, x + s, y + s), fill=shade(col, rnd.choice((.75, 1, 1.2))))
            if kind == "flower":
                for _ in range(14):
                    a, rr = rnd.uniform(0, 2 * math.pi), r * rnd.uniform(.2, .8)
                    x, y = cx + math.cos(a) * rr, cy + math.sin(a) * rr
                    d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=(226, 222, 196, 255))
        else:
            for _ in range(60):
                a = rnd.uniform(0, 2 * math.pi)
                length = r * rnd.uniform(.55, 1.0)
                ex, ey = cx + math.cos(a) * length, cy + math.sin(a) * length
                d.line((cx, cy, ex, ey), fill=shade(col, rnd.choice((.7, .9, 1.15))), width=5)
                if kind == "seed" and rnd.random() < .3:
                    d.ellipse((ex - 8, ey - 8, ex + 8, ey + 8), fill=(176, 160, 104, 255))
    img.save(out)


if __name__ == "__main__":
    main(sys.argv[1])
