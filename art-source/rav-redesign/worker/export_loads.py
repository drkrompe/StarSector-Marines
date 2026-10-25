"""The skitterer's two carried loads: feed (biomass glob) and pulp (chewed creep-killed wood).

    python export_loads.py --guide            # draws guides/loads-guide.png (ImageGen geometry authority)
    python export_loads.py [--preview <png>]  # cuts raw/loads/loads-v1.png into the two shipped sprites

The raw is one sheet with the feed on the left half and the pulp on the right, painted on a flat chroma-green
ground. Each half is keyed, cropped to its opaque box, padded square so the object fills ~80%, and downsized to
64x64 into mod/graphics/battle/rav-worker/load-feed.png and load-pulp.png.
"""
import argparse
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).parent
RAW = HERE / "raw" / "loads" / "loads-v1.png"
GUIDE = HERE / "guides" / "loads-guide.png"
OUT = HERE.parent.parent.parent / "mod" / "graphics" / "battle" / "rav-worker"
SIZE, FILL = 64, 0.80
GREEN = (0, 177, 64)


def blob(cx, cy, r, wobble, seed, n=48):
    rnd = random.Random(seed)
    ph = [rnd.uniform(0, 6.3) for _ in range(3)]
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        k = 1 + wobble * (0.5 * math.sin(3 * t + ph[0]) + 0.3 * math.sin(5 * t + ph[1]) + 0.2 * math.sin(7 * t + ph[2]))
        pts.append((cx + r * k * math.cos(t), cy + r * 0.9 * k * math.sin(t)))
    return pts


def draw_guide():
    img = Image.new("RGB", (1536, 1024), GREEN)
    d = ImageDraw.Draw(img)
    # Feed: smooth round glob, lilac skin rim, amber core lit upper left, crimson strands.
    d.polygon(blob(384, 512, 270, 0.06, 1), fill=(196, 160, 214))
    d.polygon(blob(384, 512, 240, 0.06, 1), fill=(214, 112, 30))
    d.ellipse((250, 360, 420, 500), fill=(255, 190, 90))
    for a in (0.4, 1.6, 2.7, 3.9, 5.1):
        d.line([(384 + 40 * math.cos(a), 512 + 40 * math.sin(a)), (384 + 220 * math.cos(a + 0.3), 512 + 200 * math.sin(a + 0.3))],
               fill=(150, 20, 40), width=14)
    # Pulp: ragged wad, brown bark, splinters poking out, grey-brown leaves, pale lilac veins.
    d.polygon(blob(1152, 512, 250, 0.22, 7, 60), fill=(104, 78, 58))
    for a, l in ((0.3, 330), (1.3, 300), (2.4, 320), (3.6, 290), (4.7, 330), (5.6, 300)):
        d.line([(1152 + 120 * math.cos(a), 512 + 120 * math.sin(a)), (1152 + l * math.cos(a), 512 + 0.9 * l * math.sin(a))],
               fill=(150, 120, 88), width=22)
    for cx, cy, a in ((1030, 400, 0.5), (1280, 620, 2.2), (1220, 380, 1.1)):
        d.polygon([(cx + 80 * math.cos(a), cy + 80 * math.sin(a)), (cx + 30 * math.cos(a + 1.6), cy + 30 * math.sin(a + 1.6)),
                   (cx - 80 * math.cos(a), cy - 80 * math.sin(a)), (cx + 30 * math.cos(a - 1.6), cy + 30 * math.sin(a - 1.6))],
                  fill=(128, 118, 100))
    for a in (0.9, 2.5, 4.2, 5.4):
        d.line([(1152 - 200 * math.cos(a), 512 - 180 * math.sin(a)), (1152 + 200 * math.cos(a + 0.6), 512 + 180 * math.sin(a + 0.6))],
               fill=(206, 186, 222), width=9)
    GUIDE.parent.mkdir(exist_ok=True)
    img.save(GUIDE)
    print(GUIDE)


def key(rgb):
    a = rgb.astype(float)
    corners = np.concatenate([a[:16, :16].reshape(-1, 3), a[:16, -16:].reshape(-1, 3),
                              a[-16:, :16].reshape(-1, 3), a[-16:, -16:].reshape(-1, 3)])
    bg = np.median(corners, axis=0)
    dist = np.sqrt(((a - bg) ** 2).sum(2))
    alpha = np.clip((dist - 40) / 50, 0, 1)
    # Despill: green may not exceed the larger of red and blue.
    out = a.copy()
    out[..., 1] = np.minimum(a[..., 1], np.maximum(a[..., 0], a[..., 2]) + 10)
    return np.dstack([np.clip(out, 0, 255), alpha * 255]).astype(np.uint8)


def cut(half):
    rgba = key(np.asarray(half.convert("RGB")))
    # Ignore stray speckles: the box spans only rows/columns with a run of solid pixels.
    solid = rgba[..., 3] > 128
    rows = np.where(solid.sum(1) >= 6)[0]
    cols = np.where(solid.sum(0) >= 6)[0]
    m = 8
    keep = np.zeros_like(solid)
    keep[max(rows[0] - m, 0):rows[-1] + m + 1, max(cols[0] - m, 0):cols[-1] + m + 1] = True
    rgba[..., 3] = rgba[..., 3] * keep
    img = Image.fromarray(rgba, "RGBA")
    x0, y0, x1, y1 = img.getbbox()
    side = int(max(x1 - x0, y1 - y0) / FILL)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.alpha_composite(img.crop((x0, y0, x1, y1)), ((side - (x1 - x0)) // 2, (side - (y1 - y0)) // 2))
    # Premultiplied downsize so transparent pixels don't bleed colour into the rim.
    p = np.asarray(sq).astype(float)
    p[..., :3] *= p[..., 3:] / 255
    small = np.asarray(Image.fromarray(p.astype(np.uint8), "RGBA").resize((SIZE, SIZE), Image.LANCZOS)).astype(float)
    al = small[..., 3:]
    small[..., :3] = np.where(al > 0, small[..., :3] * 255 / np.maximum(al, 1), 0)
    return Image.fromarray(np.clip(small, 0, 255).astype(np.uint8), "RGBA")


def preview(sprites, path):
    pad = 24
    w = pad + sum(SIZE + pad + SIZE * 4 + pad for _ in sprites)
    can = Image.new("RGBA", (w, SIZE * 4 + 2 * pad), (34, 22, 48, 255))
    x = pad
    for s in sprites:
        can.alpha_composite(s, (x, pad + (SIZE * 4 - SIZE) // 2))
        x += SIZE + pad
        can.alpha_composite(s.resize((SIZE * 4, SIZE * 4), Image.NEAREST), (x, pad))
        x += SIZE * 4 + pad
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    can.save(path)
    print(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--guide", action="store_true")
    ap.add_argument("--raw", default=str(RAW))
    ap.add_argument("--preview")
    args = ap.parse_args()
    if args.guide:
        draw_guide()
        return
    raw = Image.open(args.raw)
    w, h = raw.size
    sprites = []
    for name, box in (("load-feed", (0, 0, w // 2, h)), ("load-pulp", (w // 2, 0, w, h))):
        s = cut(raw.crop(box))
        s.save(OUT / f"{name}.png")
        sprites.append(s)
        print(OUT / f"{name}.png")
    if args.preview:
        preview(sprites, args.preview)


if __name__ == "__main__":
    main()
