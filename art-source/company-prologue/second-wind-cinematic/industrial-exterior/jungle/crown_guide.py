"""The silhouette guide for the jungle crown sheet: a 4 x 4 grid of tree crowns seen from above.

    python crown_guide.py        -> guides/crowns.png (regenerated, never committed)

Rows, top to bottom: rounded broadleaf crowns, wide multi-lobed emergents, palms, tree ferns and
understory rosettes. Each crown sits centred in its 512 px cell so `c4_jungle` can cut the sheet
on that grid. ImageGen paints leaves inside these outlines (`crowns.txt`).
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
CELL, N = 512, 4


def lobed(d, cx, cy, r, lobes, rng, fill):
    """A cauliflower outline: a disc ringed by smaller overlapping discs."""
    d.ellipse((cx - r * 0.72, cy - r * 0.72, cx + r * 0.72, cy + r * 0.72), fill=fill)
    for k in range(lobes):
        a = 2 * math.pi * (k + rng.uniform(-0.3, 0.3)) / lobes
        rr = r * rng.uniform(0.28, 0.42)
        dist = r - rr
        x, y = cx + dist * math.cos(a), cy + dist * math.sin(a)
        d.ellipse((x - rr, y - rr, x + rr, y + rr), fill=fill)


def star(d, cx, cy, r, fronds, rng, fill, width):
    """Fronds radiating from a centre: tapered leaves, each slightly curved."""
    for k in range(fronds):
        a = 2 * math.pi * (k + rng.uniform(-0.25, 0.25)) / fronds
        length = r * rng.uniform(0.8, 1.0)
        bend = rng.uniform(-0.25, 0.25)
        pts_l, pts_r = [], []
        for i in range(13):
            t = i / 12
            ang = a + bend * t * t
            x, y = cx + length * t * math.cos(ang), cy + length * t * math.sin(ang)
            w = width * math.sin(math.pi * min(1.0, t * 1.15)) * (1 - 0.6 * t) + 2
            nx, ny = -math.sin(ang), math.cos(ang)
            pts_l.append((x + nx * w, y + ny * w))
            pts_r.append((x - nx * w, y - ny * w))
        d.polygon(pts_l + pts_r[::-1], fill=fill)
    d.ellipse((cx - r * 0.1, cy - r * 0.1, cx + r * 0.1, cy + r * 0.1), fill=fill)


def main():
    rng = random.Random(5)
    im = Image.new('RGB', (CELL * N, CELL * N), (255, 255, 255))
    d = ImageDraw.Draw(im)
    grey = (128, 128, 128)
    for j in range(N):
        for i in range(N):
            cx, cy = CELL * i + CELL / 2, CELL * j + CELL / 2
            if j == 0:
                lobed(d, cx, cy, CELL * 0.40, rng.randrange(9, 14), rng, grey)
            elif j == 1:
                for _ in range(rng.randrange(4, 7)):            # several crowns of one spreading tree
                    a, dist = rng.uniform(0, 2 * math.pi), rng.uniform(CELL * 0.08, CELL * 0.2)
                    lobed(d, cx + dist * math.cos(a), cy + dist * math.sin(a), CELL * rng.uniform(0.2, 0.25),
                          rng.randrange(7, 11), rng, grey)
            elif j == 2:
                star(d, cx, cy, CELL * 0.43, rng.randrange(9, 14), rng, grey, CELL * 0.045)
            else:
                star(d, cx, cy, CELL * 0.40, rng.randrange(14, 20), rng, grey, CELL * 0.03)
    im = im.filter(ImageFilter.GaussianBlur(1.2))
    out = HERE / 'guides' / 'crowns.png'
    out.parent.mkdir(exist_ok=True)
    im.save(out)
    print(out)


if __name__ == '__main__':
    main()
