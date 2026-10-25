"""Damage guides for the seven field-stone groups, built from the accepted stones themselves.

Reads art-source/tilesets/field-stones/field-stones.png (4x2 frames) and writes two sheets the same size, so every
state registers with the intact group it replaces:
  field-stones-damaged-guide.png    each stone's own silhouette, greyed, with branching cracks and chipped edges
  field-stones-destroyed-guide.png  each silhouette broken into chunks, every chunk drawn in toward its own centre,
                                    so a low scatter of rubble lies where the stone stood, with gravel between

Usage: python art-source/templates/field-stones-damage-guide.py
"""
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art-source/tilesets/field-stones/field-stones.png"
OUT = ROOT / "art-source/templates"
ALPHA = 40


def damaged(mask, rnd):
    """The silhouette in flat stone with dark cracks and a few bites out of its rim."""
    h, w = mask.shape
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    body = Image.fromarray((mask * 255).astype(np.uint8))
    img.paste((120, 117, 110, 255), mask=body)
    d = ImageDraw.Draw(img)
    ys, xs = np.nonzero(mask)
    for _ in range(max(2, len(xs) // 9000)):
        i = rnd.randrange(len(xs))
        x, y, a = xs[i], ys[i], rnd.uniform(0, 2 * math.pi)
        pts = [(x, y)]
        for _ in range(6):
            a += rnd.uniform(-.6, .6)
            x, y = x + math.cos(a) * 14, y + math.sin(a) * 14
            pts.append((x, y))
        d.line(pts, fill=(46, 44, 41, 255), width=5)
    for _ in range(4):
        i = rnd.randrange(len(xs))
        r = rnd.randint(8, 16)
        d.ellipse((xs[i] - r, ys[i] - r, xs[i] + r, ys[i] + r), fill=(0, 0, 0, 0))
    # Cracks and chips never leave the stone.
    out = np.asarray(img).copy()
    out[~mask] = 0
    return Image.fromarray(out, "RGBA")


def destroyed(mask, rnd):
    """Chunks of the silhouette, each shrunk toward its own centre, with gravel in the gaps."""
    h, w = mask.shape
    ys, xs = np.nonzero(mask)
    seeds = [(xs[i], ys[i]) for i in (rnd.randrange(len(xs)) for _ in range(max(3, len(xs) // 7000)))]
    yy, xx = np.mgrid[0:h, 0:w]
    owner = np.argmin(np.stack([(xx - sx) ** 2 + (yy - sy) ** 2 for sx, sy in seeds]), axis=0)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for k, (sx, sy) in enumerate(seeds):
        cell = mask & (owner == k)
        if cell.sum() < 30:
            continue
        cy, cx = np.argwhere(cell).mean(axis=0)
        s = rnd.uniform(.45, .62)
        piece = Image.fromarray((cell * 255).astype(np.uint8))
        small = piece.resize((max(1, int(w * s)), max(1, int(h * s))), Image.NEAREST)
        tint = rnd.choice(((132, 128, 120, 255), (104, 101, 95, 255)))
        tile = Image.new("RGBA", small.size, tint)
        img.paste(tile, (int(cx - cx * s), int(cy - cy * s)), small)
    d = ImageDraw.Draw(img)
    for _ in range(len(seeds) * 2):
        i = rnd.randrange(len(xs))
        r = rnd.randint(3, 6)
        d.ellipse((xs[i] - r, ys[i] - r, xs[i] + r, ys[i] + r), fill=(140, 136, 128, 255))
    return img


def main():
    src = Image.open(SOURCE).convert("RGBA")
    pitch = src.width / 4
    alpha = np.asarray(src)[..., 3] >= ALPHA
    sheets = {"damaged": Image.new("RGBA", src.size), "destroyed": Image.new("RGBA", src.size)}
    for slot in range(7):
        x0, y0 = round(slot % 4 * pitch), round(slot // 4 * pitch)
        x1, y1 = round(x0 + pitch), round(y0 + pitch)
        mask = alpha[y0:y1, x0:x1]
        rnd = random.Random(slot)
        sheets["damaged"].alpha_composite(damaged(mask, rnd), (x0, y0))
        sheets["destroyed"].alpha_composite(destroyed(mask, rnd), (x0, y0))
    for state, img in sheets.items():
        img.save(OUT / f"field-stones-{state}-guide.png")


if __name__ == "__main__":
    main()
