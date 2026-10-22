"""Guides for the Asharu backdrop plates (see README.md): the planet through a key frame's camera.

    python make_plate_guides.py            # build/asharu-plates/guides/<kf>.png for every plate key frame

Each pixel's ray from the key frame camera (battle_blockout.KEYFRAMES, 36 mm sensor, Blender's
'-Z' / 'Y' track) meets the 5,000 km sphere; the ground there is read from the surface detail
stack (desert01, the ImageGen tiles and their settlements) and lit by the scene's sun. No ships,
no air, no stars: those are other layers. The guide owns framing, geography and settlement
placement; the ImageGen plate repaints it as a finished orbital view.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent / 'surface'))
import battle_blockout as bb  # noqa: E402
import roster  # noqa: E402
import surface_detail as sd  # noqa: E402

REPO = HERE.parents[3]
OUT = REPO / 'build' / 'asharu-plates'
PLATES = {'k0': 'S1a', 'k6': 'S12'}


def camera(kf):
    c = bb.KEYFRAMES[kf]['camera']
    loc = np.array(c['loc'], dtype=float)
    f = (np.array(c['target']) - loc) if c.get('target') else np.array(c['look'], dtype=float)
    f /= np.linalg.norm(f)
    r = np.array([f[1], -f[0], 0.0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return loc, f, r, u, c['lens'] / 36.0 * bb.W


def ground(kf, w=bb.W, h=bb.H, finished=None, base=None):
    """(colour, hit mask) of the lit ground seen through a key frame's camera at w x h."""
    loc, f, r, u, fpx = camera(kf)
    fpx *= w / bb.W
    px = (np.arange(w) + .5 - w / 2)[None, :, None]
    py = (np.arange(h) + .5 - h / 2)[:, None, None]
    d = f[None, None, :] + (px * r - py * u) / fpx
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    o = loc - np.array([0.0, 0.0, -(bb.R + bb.ALT)])
    b = d @ o
    disc = b * b - (o @ o - bb.R ** 2)
    hit = disc > 0
    t = -b - np.sqrt(np.where(hit, disc, 0))
    p = o + t[..., None] * d
    col = sd.stack_at(base, finished, p[..., 0], p[..., 1])
    n = p / bb.R
    el, az = math.radians(bb.SUN_EL), math.radians(bb.SUN_AZ)
    sun = np.array([math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)])
    lam = np.clip(n @ sun, 0, 1)[..., None]
    lit = col * (.18 + .82 * lam / max(sun[2], .2))        # sun-facing ground reads at its map colour
    return np.where(hit[..., None], np.clip(lit, 0, 1), 0.0), hit


def load(path):
    return np.asarray(Image.open(path).convert('RGB'), dtype=np.float64) / 255.0


def main():
    base = load(roster.GAME_CORE / 'graphics' / 'planets' / 'desert01.jpg')
    raw = HERE.parent / 'surface' / 'raw'
    finished = sd.finish_all(base, lambda n: load(raw / f'{n}.png') if (raw / f'{n}.png').exists() else None)
    for kf in PLATES:
        img, _ = ground(kf, finished=finished, base=base)
        out = OUT / 'guides' / f'{kf}.png'
        out.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray((img * 255 + .5).astype(np.uint8)).save(out)
        print(out)


if __name__ == '__main__':
    main()
