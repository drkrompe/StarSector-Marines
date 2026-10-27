"""Sparse-crown guide for the dry-woodland canopies (v2 edit target).

python art-source/dry-woodland/make_sparse_guide.py

Starts from the shipped `templates/forest-canopies.png` (TreeCanopy lobes, unchanged outline),
then (1) punches seeded transparent gaps inside each crown, kept clear of its outer edge so the
silhouette and tactical footprint read as before, and (2) draws a forked branch skeleton from
the crown centre in bark brown, across the gaps, so the generation owns where sand shows
through and where the limbs are. Output: guides/forest-canopies-sparse.png (1536x1024).
"""
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
SRC = HERE.parent / "templates" / "forest-canopies.png"
OUT = HERE / "guides" / "forest-canopies-sparse.png"
BARK = (88, 66, 46, 255)
GAP_SHARE = 0.24
EDGE_KEEP = 34  # px of solid foliage kept inside the outline


def main():
    im = Image.open(SRC).convert("RGBA")
    alpha = np.array(im)[:, :, 3] > 40
    out = np.array(im).copy()
    rng = random.Random(4711)
    holes = Image.new("L", im.size, 0)
    hd = ImageDraw.Draw(holes)
    skel = Image.new("RGBA", im.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(skel)
    for r in range(2):
        for c in range(3):
            x0, y0 = c * 512, r * 512
            cell = alpha[y0:y0 + 512, x0:x0 + 512]
            ys, xs = np.nonzero(cell)
            cx, cy = x0 + (xs.min() + xs.max()) / 2, y0 + (ys.min() + ys.max()) / 2
            interior = Image.fromarray((cell * 255).astype(np.uint8)).filter(
                ImageFilter.MinFilter(2 * EDGE_KEEP + 1))
            inner = np.array(interior) > 0
            area = cell.sum()
            removed = 0
            tries = 0
            while removed < GAP_SHARE * area and tries < 4000:
                tries += 1
                px, py = rng.randrange(512), rng.randrange(512)
                if not inner[py, px] or math.hypot(x0 + px - cx, y0 + py - cy) < 40:
                    continue
                rad = rng.randint(14, 30)
                hd.ellipse((x0 + px - rad, y0 + py - rad * 0.8, x0 + px + rad, y0 + py + rad * 0.8), fill=255)
                removed += math.pi * rad * rad * 0.8 * 0.85
            # Branch skeleton: 5-7 main limbs, each forking once or twice, ending inside the edge band.
            reach = min(xs.max() - xs.min(), ys.max() - ys.min()) / 2 - EDGE_KEEP * 0.6
            n = rng.randint(5, 7)
            base = rng.random() * math.tau
            for k in range(n):
                ang = base + k * math.tau / n + rng.uniform(-0.25, 0.25)
                limb(sd, cx, cy, ang, reach * rng.uniform(0.8, 1.0), 13, rng, 0)
    hole = np.array(holes) > 0
    out[hole & alpha, 3] = 0
    res = Image.fromarray(out)
    res.alpha_composite(skel)
    # Branches never leave the shipped outline.
    a = np.array(res)
    a[~alpha, 3] = 0
    OUT.parent.mkdir(exist_ok=True)
    Image.fromarray(a).save(OUT)
    print("wrote", OUT)


def limb(d, x, y, ang, length, width, rng, depth):
    seg = 4
    for i in range(seg):
        ang += rng.uniform(-0.2, 0.2)
        nx, ny = x + math.cos(ang) * length / seg, y + math.sin(ang) * length / seg
        w = max(3, int(width * (1 - i / (seg + 1))))
        d.line((x, y, nx, ny), fill=BARK, width=w)
        d.ellipse((nx - w / 2, ny - w / 2, nx + w / 2, ny + w / 2), fill=BARK)
        if depth < 2 and i in (1, 2) and rng.random() < 0.7:
            limb(d, nx, ny, ang + rng.choice((-1, 1)) * rng.uniform(0.4, 0.8),
                 length * (seg - i) / seg * 0.7, max(3, w - 3), rng, depth + 1)
        x, y = nx, ny


if __name__ == "__main__":
    main()
