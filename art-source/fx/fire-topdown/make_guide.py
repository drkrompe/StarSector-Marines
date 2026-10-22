"""Geometry guide for the top-down fire sheet (`fire-nouns.md`, Look).

Code owns the shape; ImageGen reskins it (see `art-source/geometry-first-imagegen.md`).
A 4x4 sheet of 256 px frames, seen from straight above:

  rows 0-1  BED   eight frames of a patch of burning ground: an irregular blob with
                  no single centre, several hot cores where the noise peaks, a flame
                  body round them and many short ragged licks at the rim.
  rows 2-3  TUFT  eight frames of a grass fire: two to four small flame islands
                  scattered over the cell, more ground than flame.

A first guide drew a core with a ring of long tongues; ImageGen read it as a
starburst, which from above looks like a blast, not a fire. Fire seen from above
is a patch: its outline is busy at a small scale and quiet at a large one, and
neighbouring patches run together into one carpet. So the outline here is a soft
radial falloff broken by noise, and the frame fills most of its cell.

The eight frames are one loop: the noise is a sum of plane waves whose phase
speeds are whole numbers of turns over the loop, so frame 7 runs into frame 0.
The renderer turns each instance to its own angle and starts it at its own frame,
so the loop never lines up.

Guide colours are flat zones for the reskin to read: white-yellow core, orange
body, red licks, transparent ground. Run: python make_guide.py
"""
import math
import os

import numpy as np
from PIL import Image

FRAME = 256
COLS = ROWS = 4
FRAMES = 8
HERE = os.path.dirname(os.path.abspath(__file__))

CORE = (255, 244, 170)
BODY = (255, 150, 30)
TONGUE = (200, 40, 10)
SHAPE_FLOOR = 0.06


def noise(xs, ys, phase, seed, scales):
    """Looping plane-wave noise in about -1..1: each wave drifts a whole number of turns round the loop."""
    rng = np.random.default_rng(seed)
    out = np.zeros_like(xs, dtype=float)
    for scale, weight, count in scales:
        for _ in range(count):
            a = rng.uniform(0, 2 * math.pi)
            k = 2 * math.pi / scale
            speed = rng.integers(1, 3) * (1 if rng.random() < 0.5 else -1)
            out += weight * np.cos(k * (xs * math.cos(a) + ys * math.sin(a)) + rng.uniform(0, 2 * math.pi) + speed * phase)
    # Each wave's variance is half its weight squared: this leaves the sum at unit deviation.
    return out / math.sqrt(sum(w * w * c for _, w, c in scales) / 2)


def draw_patch(phase, seed, radius, rough, centres, core_at, body_at, lick_at=0.2):
    ys, xs = np.mgrid[0:FRAME, 0:FRAME].astype(float)
    shape = np.zeros_like(xs)
    for cx, cy, r in centres:
        d = np.hypot(xs - cx, ys - cy) / (radius * r)
        shape = np.maximum(shape, 1 - d)
    # Big shapes slowly, small licks quickly: a quiet outline at large scale, a busy one at small.
    n = noise(xs, ys, phase, seed, [(90, 1.0, 7), (34, 0.6, 10), (14, 0.22, 14)])
    v = shape + rough * n
    # Kept clear of the cell's edge, so a turned instance never shows a cut.
    edge = np.minimum(np.minimum(xs, FRAME - 1 - xs), np.minimum(ys, FRAME - 1 - ys))
    v = np.minimum(v, (edge - 10) / 24)
    # Nothing far outside the patch: a lick out there would reskin as a stray speck, not flame.
    v = np.where(shape > SHAPE_FLOOR, v, -1.0)
    img = np.zeros((FRAME, FRAME, 4), np.uint8)
    img[v > lick_at] = TONGUE + (255,)
    img[v > body_at] = BODY + (255,)
    img[v > core_at] = CORE + (255,)
    return img


def check_margin(img, margin=6):
    a = img[:, :, 3]
    assert not a[:margin].any() and not a[-margin:].any() and not a[:, :margin].any() and not a[:, -margin:].any(), \
        "a frame reaches its cell's edge"


def main():
    sheet = np.zeros((FRAME * ROWS, FRAME * COLS, 4), np.uint8)
    c = FRAME / 2
    rng = np.random.default_rng(8)
    islands = [(c + rng.uniform(-62, 62), c + rng.uniform(-62, 62), rng.uniform(0.5, 0.85)) for _ in range(4)]
    for f in range(FRAMES):
        phase = 2 * math.pi * f / FRAMES
        bed = draw_patch(phase, 11, radius=112, rough=0.2, centres=[(c, c, 1.0)], core_at=0.72, body_at=0.46)
        tuft = draw_patch(phase, 23, radius=58, rough=0.2, centres=islands, core_at=0.8, body_at=0.5)
        for i, img in ((f, bed), (FRAMES + f, tuft)):
            check_margin(img)
            col, row = i % COLS, i // COLS
            sheet[row * FRAME:(row + 1) * FRAME, col * FRAME:(col + 1) * FRAME] = img
    out = os.path.join(HERE, "fire-topdown-guide.png")
    Image.fromarray(sheet, "RGBA").save(out)
    print(out)


if __name__ == "__main__":
    main()
