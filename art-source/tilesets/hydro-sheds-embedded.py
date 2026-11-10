"""Derives the embedded hydroponic sheds (`story-map-pools.md`, Planet skin) from the lit shed sheet: each shed sunk
into the ground with only a low lit roof ridge above grade and earth banked up all four sides (owner: "I feel like we
should 'embed them' into the earth").

The bank is painted as translucent slope shading only (light on the faces turned north-west, shadow on those turned
south-east, a little clod grain), never as a colour: the doodad draws over the planet's own ground, so the bank is snow
on a frozen world, crust on a toxic one and scorched soil on an irradiated one without a sheet per world. The roof is
the amber shed's roof, narrowed to the ridge between the banks.

Reads `hydro-sheds.amber.png`, writes `hydro-sheds-embedded.png` at the same frames, which
`hydro-sheds.tileset-authoring.json` exports. The pump house is cut unchanged: plant stands above grade.

Usage: python art-source/tilesets/hydro-sheds-embedded.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
SCALE = 1448 / 1536
CELL = 160 * SCALE
# Guide frames (hydroponics-guide.py) at the sheet's scale: x, y, cells along, cells across.
SHEDS = [(40, 40, 6, 3), (40, 40 + 3 * 160 + 40, 3, 3)]
PUMP = (40 + 3 * 160 + 40, 40 + 3 * 160 + 40, 2, 3)
# The bank's width on each side, in cells, and how much of the roof's own width stays above grade.
BANK, RIDGE = 0.7, 0.62
LIGHT = np.array([-0.6, -0.8])          # light from the north-west, as the look preset's shading
SEED = 7


def noise(h, w, rng):
    """Clod grain: three octaves of blurred noise, zero mean."""
    out = np.zeros((h, w), np.float32)
    for blur, gain in ((7, 1.0), (3, 0.5), (1.2, 0.3)):
        layer = Image.fromarray((rng.random((h, w)) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(blur))
        a = np.asarray(layer).astype(np.float32) / 255
        out += (a - a.mean()) / max(a.std(), 1e-6) * gain
    return out / 1.8


def bank(w, h, inner):
    """RGBA of an earth bank from the frame's edge up to the roof: a height field shaded from the north-west, only
    light and shadow with no colour of its own, so it takes the ground it is drawn over."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    x0, y0, x1, y1 = inner
    dx = np.where(xs < x0, x0 - xs, np.where(xs > x1, xs - x1, 0))
    dy = np.where(ys < y0, y0 - ys, np.where(ys > y1, ys - y1, 0))
    # Rounded corners: the bank is a mound round a box, so corners fall off with the Euclidean distance.
    dist = np.hypot(dx, dy)
    reach = float(min(x0, y0))
    t = np.clip(dist / reach, 0, 1)                         # 0 at the crest, 1 at the toe
    height = (np.cos(t * np.pi) + 1) / 2 * reach * 0.55     # smooth mound, crest at the roof
    rng = np.random.default_rng(SEED)
    height += noise(h, w, rng) * 1.6 * (1 - t) ** 0.5 * (dist > 0)
    gy, gx = np.gradient(height)
    nz = 1.0
    norm = np.sqrt(gx * gx + gy * gy + nz * nz)
    lx, ly, lz = LIGHT[0] * 0.75, LIGHT[1] * 0.75, 0.66
    flat = lz
    shade = (-gx * lx - gy * ly + nz * lz) / norm - flat    # >0 brighter than flat ground, <0 darker
    # Occlusion where the roof sits into the bank, and a faint one at the toe.
    occl = np.exp(-(dist / 5.0) ** 2) * 0.55 * (dist > 0)
    shade = np.where(shade > 0, shade * 1.6, shade * 1.0) - occl
    on = (dist > 0) & (t < 1)
    fade = np.clip((1 - t) * 4, 0, 1)                       # the last quarter of the toe melts into the ground
    alpha = np.clip(np.abs(shade), 0, 0.85) * fade * on
    lum = (shade > 0).astype(np.float32)
    rgba = np.zeros((h, w, 4), np.float32)
    rgba[..., :3] = lum[..., None] * np.array([1.0, 0.98, 0.94]) + (1 - lum[..., None]) * np.array([0.04, 0.035, 0.03])
    rgba[..., 3] = alpha
    return Image.fromarray((rgba * 255).round().astype(np.uint8))


def embedded(sheet, frame):
    gx, gy, along, across = frame
    x, y = round(gx * SCALE), round(gy * SCALE)
    w, h = round(along * CELL), round(across * CELL)
    shed = sheet.crop((x, y, x + w, y + h))
    b = round(BANK * CELL)
    roof_h = h - 2 * b
    # The roof: the shed's own art, its length kept, squeezed across to the ridge between the banks.
    core = shed.crop((0, round(h * (1 - RIDGE) / 2), w, round(h * (1 + RIDGE) / 2))).resize((w - 2 * b, roof_h),
                                                                                             Image.LANCZOS)
    out = bank(w, h, (b, b, w - 1 - b, h - 1 - b))
    out.alpha_composite(core, (b, b))
    return out, (x, y)


def main():
    sheet = Image.open(HERE / 'hydro-sheds.amber.png').convert('RGBA')
    out = Image.new('RGBA', sheet.size)
    for frame in SHEDS:
        piece, at = embedded(sheet, frame)
        out.alpha_composite(piece, at)
    gx, gy, along, across = PUMP
    x, y = round(gx * SCALE), round(gy * SCALE)
    out.alpha_composite(sheet.crop((x, y, x + round(along * CELL), y + round(across * CELL))), (x, y))
    out.save(HERE / 'hydro-sheds-embedded.png')


if __name__ == '__main__':
    main()
