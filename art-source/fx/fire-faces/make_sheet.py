"""Builds the upright flame sheet a burning building's faces are drawn with (`fire-nouns.md`, Look; `FaceFire`).

Seen from the side, as a stood-up wall is: flame rising up a face and out of its openings. Procedural, so every row is
a seamless eight-frame loop: the flame's body is fractal noise tiling in height, scrolled up the frame by exactly one
period over the loop, with a second octave scrolled twice as fast and a sway that turns once a loop.

Grid: 8 columns x 4 rows of 32 x 64 px (256 x 256, the battle FX limit; `RawArtStaysOutOfModTest`).
  row 0, 1  a curtain: fire across the whole frame's width, licking up from its foot (two variants)
  row 2, 3  a plume: fire pouring out of an opening at the frame's lower third, up and over its head (two variants)
Rendered at 4x and resampled down. Run: python make_sheet.py  (writes mod/graphics/fx/fire_face_tongues.png)
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "..", "mod", "graphics", "fx", "fire_face_tongues.png"))
COLS, ROWS, FW, FH, SS = 8, 4, 32, 64, 4


def lattice(seed, nx, ny):
    return np.random.default_rng(seed).random((ny, nx))


def value_noise(grid, u, v):
    """Bilinear-smoothstep value noise over a lattice that wraps both ways; u, v in lattice units."""
    ny, nx = grid.shape
    x0 = np.floor(u).astype(int)
    y0 = np.floor(v).astype(int)
    fx, fy = u - x0, v - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    x0 %= nx
    y0 %= ny
    x1, y1 = (x0 + 1) % nx, (y0 + 1) % ny
    a = grid[y0, x0] * (1 - fx) + grid[y0, x1] * fx
    b = grid[y1, x0] * (1 - fx) + grid[y1, x1] * fx
    return a * (1 - fy) + b * fy


def fbm(seed, u, v, octaves=4):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        f = 2 ** o
        g = lattice(seed + o * 101, 8 * f, 8 * f)
        total = total + amp * value_noise(g, u * f, v * f)
        norm += amp
        amp *= 0.5
    return total / norm


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


RAMP = [  # density -> colour: deep ember red at the fringe to a white-yellow core
    (0.00, (0.45, 0.04, 0.01)),
    (0.18, (0.80, 0.16, 0.02)),
    (0.38, (1.00, 0.42, 0.05)),
    (0.60, (1.00, 0.70, 0.18)),
    (0.82, (1.00, 0.90, 0.55)),
    (1.00, (1.00, 0.98, 0.86)),
]


def colour(d):
    out = np.zeros(d.shape + (3,))
    for (d0, c0), (d1, c1) in zip(RAMP, RAMP[1:]):
        t = np.clip((d - d0) / (d1 - d0), 0, 1)[..., None]
        inside = ((d >= d0) & (d <= d1))[..., None]
        out = np.where(inside, np.array(c0) * (1 - t) + np.array(c1) * t, out)
    return np.where((d > 1)[..., None], np.array(RAMP[-1][1]), out)


def frame(kind, variant, k):
    w, h = FW * SS, FH * SS
    x = (np.arange(w) + 0.5) / w
    y = (np.arange(h) + 0.5) / h
    u, v = np.meshgrid(x, 1 - y)  # v up from the frame's foot
    phase = k / COLS
    seed = 1000 * (kind == "plume") + 50 * variant
    sway = 0.06 * np.sin(2 * np.pi * (phase + v * 0.8 + variant * 0.37))
    # Height-tiling noise scrolled up exactly one lattice period (8 units over the frame's 2 units of height, so the
    # frame shows a quarter period) per loop: v * 2 keeps tongues taller than wide.
    n1 = fbm(seed + 1, (u + sway) * 2.0 + variant * 3.1, v * 2.0 - phase * 8.0)
    n2 = fbm(seed + 7, (u - sway) * 4.0 + 1.7, v * 4.0 - phase * 16.0, octaves=3)
    turb = 0.62 * n1 + 0.38 * n2
    if kind == "curtain":
        # Tongues across the width; the fire fills the foot and thins to licks toward the top.
        tongues = 0.5 + 0.5 * np.cos(2 * np.pi * (u * 2.0 + variant * 0.5 + 0.22 * (n1 - 0.5)))
        body = 0.95 - v * 2.05 + 0.95 * tongues * v
        edge = smoothstep(0.0, 0.18, u) * smoothstep(0.0, 0.18, 1 - u)
        d = (body + (turb - 0.5) * 1.3 * (1 - 0.35 * v)) * (0.6 + 0.4 * edge) - 0.2 * (1 - edge)
    else:
        # Out of an opening whose head is a third up: a narrow root that spills out and rolls up over the head,
        # widening and breaking into licks as it climbs.
        root = 0.30
        width = 0.24 + 0.22 * smoothstep(0.15, 0.75, v)
        lean = 0.08 * np.sin(2 * np.pi * (phase + variant * 0.5)) * v
        across = np.abs(u - 0.5 - lean) / width
        rise = np.maximum(0, v - root)
        licks = np.cos(2 * np.pi * (u * 1.6 + variant * 0.4 + 0.3 * (n1 - 0.5)))
        body = 1.05 - across ** 2 * 1.1 - rise * 1.55 - np.maximum(0, 0.04 - v) * 14
        body = body + 0.35 * licks * smoothstep(root, 0.8, v)
        d = body + (turb - 0.5) * 1.5
    # Nothing reaches the frame's top: the licks die out above three quarters of it.
    d = d - 0.9 * smoothstep(0.72, 0.98, v)
    d = np.clip(d, -1, 2)
    a = smoothstep(0.0, 0.14, d)
    rgb = colour(np.clip(d * 0.82, 0, 1))
    img = np.concatenate([rgb, a[..., None]], axis=-1)
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA")
    return im.resize((FW, FH), Image.LANCZOS)


def main():
    sheet = Image.new("RGBA", (COLS * FW, ROWS * FH), (0, 0, 0, 0))
    rows = [("curtain", 0), ("curtain", 1), ("plume", 0), ("plume", 1)]
    for r, (kind, variant) in enumerate(rows):
        for k in range(COLS):
            sheet.paste(frame(kind, variant, k), (k * FW, r * FH))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT, optimize=True)
    print(OUT)


if __name__ == "__main__":
    main()
