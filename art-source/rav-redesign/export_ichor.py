"""The Rav ichor a Runner bursts into: ground splats and an in-flight droplet.

  python export_ichor.py

Writes mod/graphics/battle/rav-runner/ichor-splat-{0..3}.png and ichor-drop.png. Drawn, not
generated: a seeded cluster of soft blobs thresholded into a wet shape, coloured blood red to
match the Marines' pools and the Matriarch's ichor, darker at the rim than the core, with a
glossy highlight from the upper left. Seeded, so a re-run writes the same pixels.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

OUT = Path(__file__).parent.parent.parent / "mod" / "graphics" / "battle" / "rav-runner"
SIZE = 160
CORE = np.array([0x86, 0x0C, 0x16], float)     # wet blood red, a shade brighter than the Marines' pools
RIM = np.array([0x22, 0x02, 0x06], float)      # near black where it soaks in, as the Marines' blood
GLOSS = np.array([0xD8, 0x6A, 0x66], float)


def field(rng, size, blobs, spray):
    """A splat's density: a lobed centre mass plus satellite drops flung outward."""
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    c = size / 2
    f = np.zeros((size, size))
    for _ in range(blobs):
        a = rng.uniform(0, 2 * np.pi)
        r = rng.uniform(0, size * 0.13)
        s = rng.uniform(size * 0.035, size * 0.085)
        bx, by = c + r * np.cos(a), c + r * np.sin(a)
        f += np.exp(-((xx - bx) ** 2 + (yy - by) ** 2) / (2 * s * s))
    for _ in range(spray):
        a = rng.uniform(0, 2 * np.pi)
        r = rng.uniform(size * 0.17, size * 0.46)
        s = rng.uniform(size * 0.008, size * 0.026)
        bx, by = c + r * np.cos(a), c + r * np.sin(a)
        f += 1.6 * np.exp(-((xx - bx) ** 2 + (yy - by) ** 2) / (2 * s * s))
        # a streak back toward the centre, as a flung drop leaves
        for k in range(1, 5):
            t = k / 6
            sx, sy = bx + (c - bx) * t * 0.5, by + (c - by) * t * 0.5
            ss = s * (1 - t * 0.85)
            f += 0.9 * np.exp(-((xx - sx) ** 2 + (yy - sy) ** 2) / (2 * ss * ss))
    # a ragged edge: fine noise where the shape crosses the threshold
    noise = rng.normal(0, 1, (size // 4, size // 4))
    noise = np.asarray(Image.fromarray(((noise + 3) * 40).clip(0, 255).astype(np.uint8))
                       .resize((size, size), Image.BICUBIC), float) / 120 - 1
    return f + 0.12 * noise


def paint(f, threshold=0.5):
    size = f.shape[0]
    alpha = np.clip((f - threshold) / 0.12, 0, 1)
    # depth: how far inside the shape, for a dark rim and a lighter core
    inside = Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(size * 0.03))
    depth = np.asarray(inside, float) / 255
    k = np.clip((depth - 0.35) / 0.65, 0, 1)[..., None]
    rgb = RIM + (CORE - RIM) * k
    # gloss: the inside edge facing the light (upper left) catches it
    gx = np.gradient(depth, axis=1)
    gy = np.gradient(depth, axis=0)
    lit = np.clip((gx + gy) * size * 0.08, 0, 1)[..., None] * (depth[..., None] > 0.5)
    rgb = rgb + (GLOSS - rgb) * lit * 0.45
    a = alpha * (0.82 + 0.18 * depth)
    out = np.dstack([np.clip(rgb, 0, 255), a * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for i in range(4):
        rng = np.random.default_rng(41 + i)
        paint(field(rng, SIZE, blobs=6 + i, spray=22 + 4 * i)).save(OUT / f"ichor-splat-{i}.png")
    size = 32
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    drop = np.exp(-((xx - 16) ** 2 + (yy - 16) ** 2) / (2 * 6.0 ** 2)) * 1.4
    paint(drop, threshold=0.4).save(OUT / "ichor-drop.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
