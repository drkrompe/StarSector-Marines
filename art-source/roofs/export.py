"""Export the roof sheet `mod/graphics/tilesets/roofs.png` from the painted roof sheets (`generate.py`).

A row per roof style, in `RoofStyle` order, and ten 128 px frames per row, in `RoofTiles` order:

  0-3  FIELD      the style's swatch made seamless and cut two by two: column i + 2j holds cell (i, j), j counted north
  4    TRIM_EAVE  the eave edge along the line EAVE_DEPTH up from the frame's south edge, outer side north, with the
                  shadow the edge throws on the wall beyond it; transparent elsewhere
  5    TRIM_CORNER the edge round the south-west EAVE_DEPTH square (open north and east), mitred at its corner
  6    TRIM_INNER the edge along an inside corner, round the north-east square the outside touches
  7-9  HOLED, COLLAPSED, BURNT  the damage cells, their alpha the guide's mask (the code owns every silhouette)

Every cut is mechanical: the raw is scaled back to the guide's canvas and cut where the guide put each piece. A style
with no raw yet is cut from its guide, so the runtime has a sheet to draw before the paint is in.

Usage: python art-source/roofs/export.py
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import make_guide as G  # noqa: E402

OUT = ROOT / 'mod/graphics/tilesets/roofs.png'
STYLES = ('clay', 'slab', 'corrugated', 'plate')
F = 128
COLUMNS = 10
# RoofCover.EAVE_DEPTH: an eave's open edge lies this far up the frame from its south edge.
EAVE_DEPTH = 0.7
EDGE = round(F * (1 - EAVE_DEPTH))   # image row of the eave's open edge
CORNER = round(F * EAVE_DEPTH)       # image column of an outside corner's open edge
TRIM = 12                            # the edge band's depth
SHADOW = 9                           # how far the edge's shadow reaches over the wall
SHADOW_ALPHA = 0.45


def seamless(rgb):
    """Blends the swatch with itself rolled half way, so it wraps: the roll owns the edges, the original the middle."""
    a = rgb.astype(np.float32)
    h, w = a.shape[:2]
    rolled = np.roll(a, (h // 2, w // 2), (0, 1))
    fx = np.minimum(np.arange(w), w - 1 - np.arange(w)) / (w * 0.18)
    fy = np.minimum(np.arange(h), h - 1 - np.arange(h)) / (h * 0.18)
    wgt = np.clip(np.minimum(fy[:, None], fx[None, :]), 0, 1)
    wgt = wgt * wgt * (3 - 2 * wgt)
    return (a * wgt[..., None] + rolled * (1 - wgt[..., None])).astype(np.uint8)


def edge_band(sheet):
    x, y, w, h = G.STRIP
    band = sheet.crop((x, y, x + w, y + h))
    width = round(w * TRIM / h)
    return band.resize((width, TRIM), Image.LANCZOS).crop((0, 0, F, TRIM)).convert('RGBA')


def shadow(frame, box):
    """Darkens {x0, y0, x1, y1} with the edge's shadow, fading away from the edge named by the box's 'from' side."""
    arr = np.array(frame)
    x0, y0, x1, y1, side = box
    for yy in range(y0, y1):
        for xx in range(x0, x1):
            d = {'down': y1 - 1 - yy, 'left': xx - x0}[side]
            a = SHADOW_ALPHA * (1 - d / SHADOW)
            if a <= 0 or arr[yy, xx, 3] > 0:
                continue
            arr[yy, xx] = (0, 0, 0, int(a * 255))
    return Image.fromarray(arr)


def trims(band):
    """The three trim frames, north-facing, from one cell of the edge band (its outer side up)."""
    east = band.rotate(-90, expand=True)  # outer side facing east

    eave = Image.new('RGBA', (F, F))
    eave.paste(band, (0, EDGE))
    eave = shadow(eave, (0, EDGE - SHADOW, F, EDGE, 'down'))

    corner = Image.new('RGBA', (F, F))
    corner.paste(east.crop((0, 0, TRIM, F - EDGE)), (CORNER - TRIM, EDGE))
    top = band.crop((0, 0, CORNER, TRIM))
    mitre = Image.new('L', top.size, 255)
    for yy in range(TRIM):
        for xx in range(CORNER - TRIM, CORNER):
            if (xx - (CORNER - TRIM)) > yy:
                mitre.putpixel((xx, yy), 0)
    corner.paste(top, (0, EDGE), mitre)
    corner = shadow(corner, (0, EDGE - SHADOW, CORNER + SHADOW, EDGE, 'down'))
    corner = shadow(corner, (CORNER, EDGE - SHADOW, CORNER + SHADOW, F, 'left'))

    inner = Image.new('RGBA', (F, F))
    inner.paste(east.crop((0, 0, TRIM, EDGE + TRIM)), (CORNER - TRIM, 0))
    run = band.crop((0, 0, F - CORNER + TRIM, TRIM))
    mitre = Image.new('L', run.size, 255)
    for yy in range(TRIM):
        for xx in range(TRIM):
            if xx < TRIM - 1 - yy:
                mitre.putpixel((xx, yy), 0)
    inner.paste(run, (CORNER - TRIM, EDGE), mitre)
    inner = shadow(inner, (CORNER, EDGE - SHADOW, F, EDGE, 'down'))
    inner = shadow(inner, (CORNER, 0, CORNER + SHADOW, EDGE, 'left'))
    return eave, corner, inner


def row(style):
    raw = HERE / 'raw' / f'{style}.raw.png'
    source = raw if raw.exists() else HERE / 'guides' / f'{style}-guide.png'
    if not raw.exists():
        print(f'{style}: no raw yet, cut from the guide')
    sheet = Image.open(source).convert('RGB')
    if sheet.size != (G.W, G.H):
        sheet = sheet.resize((G.W, G.H), Image.LANCZOS)
    mask = Image.open(HERE / 'guides' / f'{style}-mask.png').convert('L')
    frames = []

    sx, sy = G.SWATCH
    swatch = np.array(sheet.crop((sx, sy, sx + G.CELL * 2, sy + G.CELL * 2)))
    swatch = Image.fromarray(seamless(swatch)).resize((F * 2, F * 2), Image.LANCZOS).convert('RGBA')
    for col in range(4):
        i, j = col & 1, col >> 1
        frames.append(swatch.crop((i * F, (1 - j) * F, (i + 1) * F, (2 - j) * F)))

    frames.extend(trims(edge_band(sheet)))

    for (x, y) in (G.HOLED, G.COLLAPSED, G.BURNT):
        box = (x, y, x + G.DAMAGE, y + G.DAMAGE)
        cell = sheet.crop(box).resize((F, F), Image.LANCZOS).convert('RGBA')
        cell.putalpha(mask.crop(box).resize((F, F), Image.BILINEAR))
        frames.append(cell)
    return frames


def main():
    out = Image.new('RGBA', (F * COLUMNS, F * len(STYLES)))
    for r, style in enumerate(STYLES):
        for c, frame in enumerate(row(style)):
            out.paste(frame, (c * F, r * F))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT)
    print(f'wrote {OUT.relative_to(ROOT)} {out.size}')


if __name__ == '__main__':
    main()
