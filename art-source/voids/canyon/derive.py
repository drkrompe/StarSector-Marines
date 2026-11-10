"""Derives the arid canyon's shipped wall from its ImageGen masters (`voids.md`, canyon).

Each master is shrunk to 1024 px and its seams healed, so it repeats: a band along every edge is blended with
the master rolled by half its size, which runs on unbroken across that edge. Run from the repository root:

    python art-source/voids/canyon/derive.py
"""
import numpy as np
from PIL import Image

SRC = 'art-source/voids/canyon/'
OUT = 'mod/graphics/battle/voids/'
SIZE = 1024
# The width of the blended band along each edge, as a share of the side.
BAND = .07


def heal(rgb):
    h, w, _ = rgb.shape
    rolled = np.roll(np.roll(rgb, h // 2, axis=0), w // 2, axis=1)

    def ramp(n):
        t = np.arange(n, dtype=np.float32) / (n - 1)
        edge = np.minimum(t, 1 - t) / BAND
        e = np.clip(edge, 0, 1)
        return e * e * (3 - 2 * e)

    keep = np.minimum.outer(ramp(h), ramp(w))[..., None]
    return rgb * keep + rolled * (1 - keep)


def derive(name):
    img = Image.open(SRC + name + '.png').convert('RGB').resize((SIZE, SIZE), Image.LANCZOS)
    rgb = heal(np.asarray(img).astype(np.float32))
    Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).save(OUT + name + '.png', optimize=True)
    a = rgb
    inner = np.abs(a[:, 1:] - a[:, :-1]).mean()
    print(name, 'inner step %.1f, seam x %.1f, seam y %.1f'
          % (inner, np.abs(a[:, 0] - a[:, -1]).mean(), np.abs(a[0] - a[-1]).mean()))


if __name__ == '__main__':
    for n in ('canyon-wall',):
        derive(n)
