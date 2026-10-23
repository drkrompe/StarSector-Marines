"""Put the corrected Jangala into finished C1 frames without re-rendering the shot.

    python planet_fix.py --frames <shipped frames> --before <layer> --after <layer> --out <dir>

The 2026-10-22 C1 final rendered the planet's air shell unscaled, so it came out as an opaque
grey ball (`render_planet.air_material`). `c1v2_scene.py --final --planet-layer` renders the
planet alone, every other object held out and every volume hidden; rendered once with
--air-legacy (before) and once without (after), the two differ only in the planet. Each frame
here is the shipped frame plus (after - before), both premultiplied: a ship in front of the
planet is a holdout in both layers and keeps its pixels, and a drive plume over the planet keeps
its glow, which a plain alpha-over would have painted out. Frames with no layer are copied as
they are. Display-referred arithmetic, which is close enough for a planet behind thin glows.
"""
import argparse
import shutil
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image


def premultiplied(path):
    a = np.asarray(Image.open(path), dtype=np.float32)
    scale = 65535.0 if a.max() > 255 else 255.0
    a = a / scale
    return a[..., :3] * a[..., 3:4]


def fix(job):
    src, before, after, out = job
    if not (before.exists() and after.exists()):
        shutil.copyfile(src, out)
        return 0
    old = np.asarray(Image.open(src).convert('RGB'), dtype=np.float32) / 255.0
    new = np.clip(old + premultiplied(after) - premultiplied(before), 0.0, 1.0)
    Image.fromarray((new * 255.0 + 0.5).astype(np.uint8)).save(out, compress_level=3)
    return 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--frames', required=True, type=Path)
    ap.add_argument('--before', required=True, type=Path)
    ap.add_argument('--after', required=True, type=Path)
    ap.add_argument('--out', required=True, type=Path)
    ap.add_argument('--only', help='a:b, frames to write (default all)')
    o = ap.parse_args()
    o.out.mkdir(parents=True, exist_ok=True)
    names = sorted(p.name for p in o.frames.glob('*.png'))
    if o.only:
        a, b = (int(x) for x in o.only.split(':'))
        names = [n for n in names if a <= int(n[:-4]) <= b]
    jobs = [(o.frames / n, o.before / n, o.after / n, o.out / n) for n in names]
    with ProcessPoolExecutor() as pool:
        fixed = sum(pool.map(fix, jobs, chunksize=8))
    print('PLANET_FIX %d frames, %d with the corrected planet' % (len(jobs), fixed), flush=True)
    if not jobs:
        sys.exit('no frames in %s' % o.frames)


if __name__ == '__main__':
    main()
