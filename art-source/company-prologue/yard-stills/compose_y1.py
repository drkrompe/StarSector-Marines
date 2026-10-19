"""Y1 as layers: the yard background with Nessa's cut-out composited over it.

    python compose_y1.py [--scale 0.8] [--centre-x 1100] [--out PATH]

The yard pictures are layered (owner, 2026-10-19): `yard-hull-bg-v3.png` has nobody in the
foreground, and `nessa-y1-cutout-v1.png` is Nessa alone on transparency, so she can be changed or
left out without repainting the yard. The cut-out came back larger than its placement guide and
cropped at the hips, so it is scaled about its bottom edge (which stays on the frame's bottom edge,
where the picture crops her) and slid sideways until her centre sits at --centre-x. A soft shade
under her lower edge settles her onto the gravel. Writes a 1536x1024 PNG (default
build/yard-stills/y1-composite.png); pack.ps1 packs whichever master story-stills holds.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
BACKGROUND = HERE / 'yard-hull-bg-v3.png'
CUTOUT = HERE / 'nessa-y1-cutout-v1.png'
GRADE = (0.95, 0.98, 1.05)       # per-channel gain on her cut-out, toward the yard's daylight


def compose(scale, centre_x):
    bg = Image.open(BACKGROUND).convert('RGBA')
    cut = Image.open(CUTOUT).convert('RGBA')
    w, h = bg.size
    a = np.array(cut)[..., 3]
    cols = np.where(a.max(axis=0) > 128)[0]
    fig_centre = (cols[0] + cols[-1]) / 2
    # Her light was painted warmer than the yard's hazy daylight: cool and soften her a touch.
    px = np.array(cut).astype(float)
    grey = px[..., :3].mean(axis=2, keepdims=True)
    px[..., :3] = (px[..., :3] * 0.88 + grey * 0.12) * np.array(GRADE)
    cut = Image.fromarray(px.clip(0, 255).astype(np.uint8))
    small = cut.resize((round(cut.width * scale), round(cut.height * scale)), Image.LANCZOS)
    ox = round(centre_x - fig_centre * scale)
    oy = h - small.height
    layer = Image.new('RGBA', bg.size, (0, 0, 0, 0))
    layer.alpha_composite(small, (max(ox, 0), oy), (max(-ox, 0), 0))
    # Ambient shade: a soft darkening round her lower body where she blocks the sky's light.
    shade = np.array(layer)[..., 3].astype(float) / 255.0
    shade = np.array(Image.fromarray((shade * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(28))) / 255.0
    rows = np.linspace(0, 1, h)[:, None] ** 3
    out = np.array(bg).astype(float)
    out[..., :3] *= 1.0 - 0.28 * shade[..., None] * rows[..., None]
    base = Image.fromarray(out.clip(0, 255).astype(np.uint8))
    base.alpha_composite(layer)
    return base.convert('RGB')


def main():
    argv = sys.argv[1:]
    arg = lambda k, d: argv[argv.index(k) + 1] if k in argv else d   # noqa: E731
    out = Path(arg('--out', str(REPO / 'build' / 'yard-stills' / 'y1-composite.png')))
    out.parent.mkdir(parents=True, exist_ok=True)
    compose(float(arg('--scale', '0.8')), float(arg('--centre-x', '1100'))).save(out)
    print('Y1_COMPOSITE', out)


if __name__ == '__main__':
    main()
