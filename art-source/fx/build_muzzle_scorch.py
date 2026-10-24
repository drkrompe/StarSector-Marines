"""Derive the muzzle-scorch frame of the shipped decal sheet.

A heavy gun that fires near the ground leaves soot fanned forward from its
muzzle (`TurretFx`, `render.scorch`). The decal sheet is one auto-sliced strip
(`SpriteSheetSlicer`), so the scorch is frame 13, in the free slot at the
strip's right end; this script clears that slot and paints the frame there.

The fan points along +x with the muzzle at the frame's left edge, symmetric
about its axis, so `ImpactDecals.spawnMuzzleScorch` can put the decal's centre
half a length ahead of the muzzle at the gun's bearing. It is soot with a low
alpha: a single round is a faint mark, and the overlapping stamps of a long
engagement darken it (the spawn caps how many stack in one place).

Variants (`--variant`), kept so the look can be re-argued from pictures:
  tight   a narrow dark fan with streaks raked forward (the shipped default)
  broad   a wide faint blast, blotchy and soft
  ringed  a blast ring just ahead of the muzzle with a shorter fan beyond it

Run: python art-source/fx/build_muzzle_scorch.py [--variant tight]
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SHEET = ROOT / "mod" / "graphics" / "decals" / "decals.png"

"""The free slot after the last authored frame (which ends at column 1903).
Six clear columns either side keep the slicer's four-column gap."""
SLOT_X0 = 1910
SLOT_X1 = 2042
SLOT_W = SLOT_X1 - SLOT_X0
"""Soot, not black: a warm near-black so it sits on sand and grass alike."""
SOOT = (24, 20, 17)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def value_noise(shape, cells, rng):
    """Smooth noise in [0, 1]: a coarse random grid bilinearly upsampled."""
    h, w = shape
    grid = rng.random((cells[0] + 1, cells[1] + 1))
    ys = np.linspace(0, cells[0], h)
    xs = np.linspace(0, cells[1], w)
    y0 = np.floor(ys).astype(int).clip(0, cells[0] - 1)
    x0 = np.floor(xs).astype(int).clip(0, cells[1] - 1)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    a = grid[y0][:, x0]
    b = grid[y0][:, x0 + 1]
    c = grid[y0 + 1][:, x0]
    d = grid[y0 + 1][:, x0 + 1]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def polar(width, height):
    """Normalised radius (1 at the frame's far end) and bearing off the axis, about the left-edge muzzle."""
    ys, xs = np.mgrid[0:height, 0:width].astype(float)
    dx = xs + 0.5
    dy = ys + 0.5 - height / 2.0
    r = np.hypot(dx, dy) / width
    theta = np.arctan2(dy, dx)
    return r, theta


def streaks(theta, rng, count, strength):
    """Rays raked out from the muzzle: a random-phase sum of sines in bearing."""
    s = np.zeros_like(theta)
    for _ in range(count):
        s += np.sin(theta * rng.uniform(18, 44) + rng.uniform(0, 6.3))
    s = (s / count) * 0.5 + 0.5
    return 1.0 - strength + strength * s


def tight(rng):
    w, h = SLOT_W, 104
    r, theta = polar(w, h)
    angular = np.exp(-(theta / 0.36) ** 2)
    radial = smoothstep(0.0, 0.05, r) * (1.0 - smoothstep(0.35, 0.98, r))
    core = 0.75 + 0.25 * np.exp(-(theta / 0.14) ** 2) * (1.0 - smoothstep(0.1, 0.7, r))
    rays = streaks(theta, rng, 7, 0.45)
    blot = 0.75 + 0.25 * value_noise((h, w), (4, 8), rng)
    return 0.52 * angular * radial * core * rays * blot


def broad(rng):
    w, h = SLOT_W, 150
    r, theta = polar(w, h)
    angular = np.exp(-(theta / 0.78) ** 2)
    radial = smoothstep(0.0, 0.06, r) * (1.0 - smoothstep(0.15, 0.95, r))
    blot = 0.45 + 0.55 * value_noise((h, w), (5, 5), rng)
    rays = streaks(theta, rng, 5, 0.2)
    return 0.32 * angular * radial * blot * rays


def ringed(rng):
    w, h = SLOT_W, 112
    r, theta = polar(w, h)
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    ring_cx, ring_r = 0.24 * w, 0.24 * w
    wobble = 0.12 * (value_noise((h, w), (6, 6), rng) - 0.5)
    d = np.hypot(xs + 0.5 - ring_cx, ys + 0.5 - h / 2.0) / ring_r + wobble
    disc = 0.45 * (1.0 - smoothstep(0.55, 1.0, d))
    rim = np.exp(-((d - 0.86) / 0.16) ** 2) * (0.45 + 0.55 * value_noise((h, w), (7, 7), rng))
    blast = np.maximum(disc, rim)
    fan = np.exp(-(theta / 0.4) ** 2) * smoothstep(0.25, 0.4, r) * (1.0 - smoothstep(0.45, 0.95, r))
    rays = streaks(theta, rng, 6, 0.4)
    blot = 0.7 + 0.3 * value_noise((h, w), (5, 7), rng)
    return 0.46 * np.maximum(blast, 0.8 * fan * rays) * blot


def main_run(content):
    """The columns of the widest run the slicer would read as one frame (gaps under four columns bridged)."""
    columns = content.any(axis=0)
    runs, start, gap = [], None, 0
    for x, filled in enumerate(columns):
        if filled:
            start = x if start is None else start
            gap = 0
        elif start is not None:
            gap += 1
            if gap >= 4:
                runs.append((start, x - gap))
                start, gap = None, 0
    if start is not None:
        runs.append((start, len(columns) - 1 - gap))
    keep = np.zeros(len(columns), dtype=bool)
    first, last = max(runs, key=lambda run: run[1] - run[0])
    keep[first:last + 1] = True
    return keep


VARIANTS = {"tight": tight, "broad": broad, "ringed": ringed}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--variant", choices=sorted(VARIANTS), default="tight")
    args = parser.parse_args()

    alpha = VARIANTS[args.variant](np.random.default_rng(13))
    h, w = alpha.shape
    sheet = Image.open(SHEET).convert("RGBA")
    pixels = np.array(sheet)
    pixels[:, SLOT_X0 - 6:, :] = 0
    y0 = (pixels.shape[0] - h) // 2
    frame = pixels[y0:y0 + h, SLOT_X0:SLOT_X0 + w]
    frame[..., 0], frame[..., 1], frame[..., 2] = SOOT
    # The slicer bounds a frame by its pixels at alpha 16 or more, and soot fades out well below that, so a
    # plain falloff would be cropped inside itself and stamp as a hard rectangle. The faint tail is dithered
    # instead: below 16 a pixel is either 0 or 16, with the odds that keep its average. The frame then spans the
    # whole fade (muzzle at the left edge, axis on the centre line), and filtering averages the dither away.
    # Below three levels the tail is dropped, and so is any speck the dither leaves out past the fade on its own,
    # which the slicer would otherwise read as a frame of its own.
    value = alpha * 255.0
    faint = value < 16.0
    value[faint] = np.where(np.random.default_rng(29).random(value.shape)[faint] < value[faint] / 16.0, 16.0, 0.0)
    value[alpha * 255.0 < 3.0] = 0.0
    value[:, ~main_run(value >= 16.0)] = 0.0
    frame[..., 3] = np.clip(value + 0.5, 0, 255).astype(np.uint8)
    rows = np.flatnonzero((value >= 16.0).any(axis=1))
    cols = np.flatnonzero((value >= 16.0).any(axis=0))
    border = max(alpha[0].max(), alpha[-1].max(), alpha[:, -1].max())
    Image.fromarray(pixels, "RGBA").save(SHEET, optimize=True)
    print(f"{SHEET.relative_to(ROOT)}: muzzle scorch '{args.variant}' at x {SLOT_X0}..{SLOT_X0 + w - 1}, "
          f"{w}x{h}, peak alpha {alpha.max():.2f}, alpha at the border {border:.3f}; sliced frame columns "
          f"{cols[0]}..{cols[-1]}, rows {rows[0]}..{rows[-1]}")


if __name__ == "__main__":
    main()
