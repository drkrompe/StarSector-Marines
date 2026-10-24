"""Put the corrected Jangala into finished C1 frames without re-rendering the shot.

    python planet_fix.py --frames <shipped frames> --before <layer> --after <layer> --out <dir>

The 2026-10-22 C1 final rendered the planet's air shell unscaled, so it came out as an opaque
grey ball (`render_planet.air_material`). `c1v2_scene.py --final --planet-layer` renders the
planet alone, every other object held out and every volume hidden; rendered once with
--air-legacy (before) and once without (after), the two differ only in the planet. Each frame
here is the shipped frame plus (after - before) over the before layer's alpha: a ship in front
of the planet is a holdout there and keeps its pixels, a drive plume over the planet keeps its
glow, which a plain alpha-over would have painted out, and the after layer keeps the sky, so
the stars the wider legacy shell hid around the rim come back. Frames with no layer are copied as
they are. Display-referred arithmetic, which is close enough for a planet behind thin glows.

The before layer's anti-aliased rim does not match the shipped frame's (16 samples against the
final's, a different footprint per pixel), so the difference alone leaves the legacy ball's edge
as a faint grey ring outside the new limb. Within RIM px of the footprint the ball is therefore
taken as fully covering, at the colour of its nearest rim, except near a held-out craft.
"""
import argparse
import shutil
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image


CHANGED_ONLY = False
RIM = 2


def _disc(r):
    return [(dy, dx) for dy in range(-r, r + 1) for dx in range(-r, r + 1) if dy * dy + dx * dx <= r * r + r]


def _shift(img, dy, dx):
    out = np.zeros_like(img)
    h, w = img.shape[:2]
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] =         img[max(-dy, 0):h + min(-dy, 0), max(-dx, 0):w + min(-dx, 0)]
    return out


def dilate(img, r):
    return np.maximum.reduce([_shift(img, dy, dx) for dy, dx in _disc(r)])


def erode(img, r):
    return np.minimum.reduce([_shift(img, dy, dx) for dy, dx in _disc(r)])


def rgba(path):
    a = np.asarray(Image.open(path), dtype=np.float32)
    return a / (65535.0 if a.max() > 255 else 255.0)


def fix(job):
    src, before, after, out = job
    if not (before.exists() and after.exists()):
        if not CHANGED_ONLY:
            shutil.copyfile(src, out)
        return 0
    old = np.asarray(Image.open(src).convert('RGB'), dtype=np.float32) / 255.0
    b, a = rgba(before), rgba(after)
    cover = b[..., 3:4]                      # the legacy ball's footprint; 0 on held-out craft
    # The after layer is opaque over its crop but for held-out craft, so where it stays opaque a
    # few px around, the rim band is open sky and the legacy ball's edge can be removed whole.
    clear = erode(a[..., 3:4], RIM + 1) > 0.999
    rim = np.where(clear & (cover < 1.0), dilate(cover, RIM), cover)
    shell = np.where(rim > cover, dilate(b[..., :3] * (cover > 0.5), RIM), b[..., :3])
    b = np.concatenate([shell, b[..., 3:4]], axis=-1)
    cover = rim
    # What the shipped frame holds over the legacy ball: plumes and glows in front of the planet,
    # craft it hides. All of it adds light, so only the positive part is kept; the negative part is
    # the legacy ball's own grain where the before layer's noise differs from the shipped frame's.
    over = np.maximum(old - cover * b[..., :3], 0.0)
    new = np.clip(cover * a[..., :3] + over, 0.0, 1.0)
    Image.fromarray((new * 255.0 + 0.5).astype(np.uint8)).save(out, compress_level=3)
    return 1


def _init(changed_only):
    global CHANGED_ONLY
    CHANGED_ONLY = changed_only


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--frames', required=True, type=Path)
    ap.add_argument('--before', required=True, type=Path)
    ap.add_argument('--after', required=True, type=Path)
    ap.add_argument('--out', required=True, type=Path)
    ap.add_argument('--only', help='a:b, frames to write (default all)')
    ap.add_argument('--changed-only', action='store_true',
                    help='write only the frames that get the corrected planet (a later Drive folder in '
                         'cinematic_export.FINALS replaces just those)')
    o = ap.parse_args()
    global CHANGED_ONLY
    CHANGED_ONLY = o.changed_only
    o.out.mkdir(parents=True, exist_ok=True)
    names = sorted(p.name for p in o.frames.glob('*.png'))
    if o.only:
        a, b = (int(x) for x in o.only.split(':'))
        names = [n for n in names if a <= int(n[:-4]) <= b]
    jobs = [(o.frames / n, o.before / n, o.after / n, o.out / n) for n in names]
    with ProcessPoolExecutor(initializer=_init, initargs=(o.changed_only,)) as pool:
        fixed = sum(pool.map(fix, jobs, chunksize=8))
    print('PLANET_FIX %d frames, %d with the corrected planet' % (len(jobs), fixed), flush=True)
    if not jobs:
        sys.exit('no frames in %s' % o.frames)


if __name__ == '__main__':
    main()
