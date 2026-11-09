"""Derives the sky port's light masks from its shipped industrial textures.

Each mask is the lights already painted in its texture (windows, lamps, beacons), lifted out as an RGBA image:
the light's own colour, brightened, in RGB, and how much it glows in alpha, with a soft halo round each light.
The renderer lays a mask over its texture after the depth haze, so the lights punch through the haze that greys
everything else. Run from the repository root:

    python art-source/voids/sky-port/industrial/derive_glow.py
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

OUT = 'mod/graphics/battle/voids/'
SOURCES = ['industrial-facade', 'industrial-terrace-roof', 'industrial-city-floor']


# The share of each texture's pixels that are lights: the brightest warm or red pixels, after paint is ruled out.
LIT_SHARE = {'industrial-facade': .02, 'industrial-terrace-roof': .006, 'industrial-city-floor': .03}


# Masks drawn as a soft field of light only.
SOFT = {'industrial-city-floor'}


def mask(rgb, share):
    r, g, b = (rgb[..., k].astype(np.float32) for k in range(3))
    lum = .3 * r + .59 * g + .11 * b
    # Warm lamps and windows: red over green over blue, and blue enough that it is light, not yellow paint.
    warm = (r >= g) & (g >= b) & (b > .30 * r) & (r - b > 30)
    # Red beacons: a saturated red.
    red = (r > 150) & (r - g > 90) & (r - b > 90)
    score = np.where(warm | red, lum + np.where(red, 60, 0), 0)
    cut = np.quantile(score, 1 - share)
    return np.clip((score - cut) / 40 + .5, 0, 1) * (score > cut)


def derive(name):
    src = Image.open(OUT + name + '.png').convert('RGB')
    rgb = np.asarray(src)
    core = mask(rgb, LIT_SHARE[name])
    # Light colour: the pixel's own, pushed to full brightness.
    col = rgb.astype(np.float32)
    peak = np.maximum(col.max(axis=2, keepdims=True), 1)
    col = np.clip(col / peak * 255 * 1.0, 0, 255)
    # A halo: the core blurred wide and faint, coloured by the blurred light colour, wrapped so the tile stays
    # seamless.
    a_core = Image.fromarray((core * 255).astype(np.uint8))
    w, h = src.size
    tiled = Image.new('L', (w * 3, h * 3))
    for i in range(3):
        for j in range(3):
            tiled.paste(a_core, (i * w, j * h))
    halo = np.asarray(tiled.filter(ImageFilter.GaussianBlur(w / 80)).crop((w, h, 2 * w, 2 * h))).astype(np.float32) / 255
    halo = np.clip(halo * 4.5, 0, 1) * .8
    # The city floor is seen from far off and magnified up close: its lights are a soft field, no hard cores.
    alpha = np.maximum(core * .6, halo) if name in SOFT else np.maximum(core, halo)
    # Colour the halo by the nearest light: blur the premultiplied colour the same way.
    pre = (col * core[..., None]).astype(np.float32)
    chans = []
    for k in range(3):
        c = Image.fromarray(np.clip(pre[..., k], 0, 255).astype(np.uint8))
        t = Image.new('L', (w * 3, h * 3))
        for i in range(3):
            for j in range(3):
                t.paste(c, (i * w, j * h))
        chans.append(np.asarray(t.filter(ImageFilter.GaussianBlur(w / 80)).crop((w, h, 2 * w, 2 * h))).astype(np.float32))
    blurred = np.stack(chans, axis=2)
    wsum = np.asarray(tiled.filter(ImageFilter.GaussianBlur(w / 80)).crop((w, h, 2 * w, 2 * h))).astype(np.float32)
    halo_col = blurred / np.maximum(wsum[..., None], 1) * 255
    rgb_out = np.where((core[..., None] > 0) & (name not in SOFT), col, np.clip(halo_col, 0, 255))
    out = np.dstack([rgb_out, alpha * 255]).astype(np.uint8)
    Image.fromarray(out, 'RGBA').save(OUT + name + '-glow.png', optimize=True)
    print(name, 'lit fraction', float((core > 0).mean()))


if __name__ == '__main__':
    for n in (sys.argv[1:] or SOURCES):
        derive(n)
