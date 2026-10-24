"""Geometry guides for the battle-map roofs (`battle-render-nouns.md`, Roofs; `RoofTiles`): one 1536x1024 sheet per roof
style, painted in one ImageGen pass so a style's field, edge and damage read as one material.

  swatch    x 0-767, y 128-895: two by two cells of 384 px, the roof's field, its grain (courses, ribs, plate rows)
            running across the sheet. Exported as a seamless swatch, cut into the four FIELD frames.
  strip     x 800-1503, y 40-103: the roof's eave edge seen from above, its outer side up. The exporter lays it along
            every eave, outside corner and inside corner (TRIM_* frames).
  holed     x 800-1151, y 136-487: one cell of field punched through by a shell, a ragged hole open to the room below.
  collapsed x 1168-1519, y 136-487: one cell fallen in: a remnant hanging from its north edge, the rest open.
  burnt     x 800-1151, y 520-871: one cell burnt through: charred rafters across the grain over the open room.

Open, void parts are painted a flat near-black; `guides/<style>-mask.png` holds the alpha the exporter keeps, white
for roof and black for void, so the silhouette is the code's and never the painter's. Light is from the north-west.

Usage: python art-source/roofs/make_guide.py   (writes art-source/roofs/guides/<style>-guide.png and -mask.png)
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
GUIDES = HERE / 'guides'

W, H = 1536, 1024
CELL = 384
SWATCH = (0, 128)
STRIP = (800, 40, 704, 64)
DAMAGE = 352
HOLED = (800, 136)
COLLAPSED = (1168, 136)
BURNT = (800, 520)

VOID = (20, 18, 17)
STYLES = ('clay', 'slab', 'corrugated', 'plate')

BASE = {
    'clay': (176, 92, 62),
    'slab': (150, 146, 136),
    'corrugated': (128, 134, 126),
    'plate': (104, 112, 116),
}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def field(draw, x0, y0, w, h, style, cell):
    """The style's field over the rectangle, its pattern laid on the cell grid so neighbouring cells join."""
    tile = Image.new('RGB', (int(w), int(h)))
    paint_field(ImageDraw.Draw(tile), 0, 0, int(w), int(h), style, cell)
    draw._image.paste(tile, (int(x0), int(y0)))


def paint_field(draw, x0, y0, w, h, style, cell):
    base = BASE[style]
    draw.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=base)
    if style == 'clay':
        # Four courses a cell, eight pantiles a course, each lit on its north-west and shaded under its lap.
        course = cell / 4
        tile = cell / 8
        rows = int(math.ceil(h / course))
        for r in range(rows):
            top = y0 + r * course
            off = (tile / 2) if r % 2 else 0
            for c in range(-1, int(math.ceil(w / tile)) + 1):
                left = x0 + c * tile + off
                draw.rectangle([left, top, left + tile * 0.55, top + course - 1], fill=shade(base, 1.12))
                draw.rectangle([left + tile * 0.55, top, left + tile - 1, top + course - 1], fill=shade(base, 0.9))
            draw.rectangle([x0, top + course - course * 0.16, x0 + w - 1, top + course - 1], fill=shade(base, 0.62))
    elif style == 'slab':
        # One poured panel a cell: tarred seams on the cell lines, gravel, a drain and a vent box.
        rnd = random.Random(7)
        for _ in range(int(w * h / 90)):
            px, py = x0 + rnd.random() * w, y0 + rnd.random() * h
            draw.point((px, py), fill=shade(base, 0.75 + rnd.random() * 0.5))
        seam = max(3, int(cell * 0.022))
        for k in range(0, int(w / cell) + 1):
            draw.rectangle([x0 + k * cell - seam, y0, x0 + k * cell + seam, y0 + h - 1], fill=(52, 50, 48))
        for k in range(0, int(h / cell) + 1):
            draw.rectangle([x0, y0 + k * cell - seam, x0 + w - 1, y0 + k * cell + seam], fill=(52, 50, 48))
        if w >= cell * 2:
            vx, vy = x0 + cell * 1.3, y0 + cell * 0.3
            draw.rectangle([vx, vy, vx + cell * 0.3, vy + cell * 0.22], fill=shade(base, 1.2), outline=(70, 68, 64))
            draw.ellipse([x0 + cell * 0.45, y0 + cell * 1.45, x0 + cell * 0.6, y0 + cell * 1.6], fill=(60, 58, 55))
    elif style == 'corrugated':
        # Ribs across the grain, twelve a cell, sheets lapped on every half cell with a rivet line on the purlin.
        rib = cell / 12
        for c in range(int(math.ceil(w / rib))):
            left = x0 + c * rib
            draw.rectangle([left, y0, left + rib * 0.45, y0 + h - 1], fill=shade(base, 1.18))
            draw.rectangle([left + rib * 0.45, y0, left + rib - 1, y0 + h - 1], fill=shade(base, 0.8))
        for k in range(0, int(h / (cell / 2)) + 1):
            yy = y0 + k * cell / 2
            draw.rectangle([x0, yy - 3, x0 + w - 1, yy + 3], fill=shade(base, 0.55))
            for c in range(int(math.ceil(w / rib))):
                cx = x0 + c * rib + rib * 0.25
                draw.ellipse([cx - 3, yy + 8, cx + 3, yy + 14], fill=shade(base, 1.35))
    elif style == 'plate':
        # Two plates a cell along the grain, riveted round, a hatch in one cell of the swatch.
        pw, ph = cell / 2, cell / 2
        for r in range(int(math.ceil(h / ph))):
            for c in range(int(math.ceil(w / pw))):
                left, top = x0 + c * pw, y0 + r * ph
                draw.rectangle([left, top, left + pw - 1, top + ph - 1], fill=base, outline=shade(base, 0.55), width=4)
                draw.line([left + 4, top + 4, left + pw - 5, top + 4], fill=shade(base, 1.3), width=3)
                for t in range(1, 6):
                    for (rx, ry) in ((left + pw * t / 6, top + 12), (left + pw * t / 6, top + ph - 12)):
                        draw.ellipse([rx - 4, ry - 4, rx + 4, ry + 4], fill=shade(base, 1.4))
        if w >= cell * 2:
            hx, hy = x0 + cell * 1.25, y0 + cell * 1.25
            draw.rectangle([hx, hy, hx + cell * 0.5, hy + cell * 0.5], fill=shade(base, 0.85),
                           outline=shade(base, 0.45), width=6)
            draw.ellipse([hx + cell * 0.18, hy + cell * 0.18, hx + cell * 0.32, hy + cell * 0.32], fill=shade(base, 1.3))


def strip(draw, x0, y0, w, h, style):
    """The eave edge, its outer side at the top: a lighter lip, the edge band, and its shaded inner foot."""
    band = Image.new('RGB', (w, h))
    paint_strip(ImageDraw.Draw(band), 0, 0, w, h, style)
    draw._image.paste(band, (x0, y0))


def paint_strip(draw, x0, y0, w, h, style):
    base = BASE[style]
    draw.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=shade(base, 0.95))
    draw.rectangle([x0, y0, x0 + w - 1, y0 + h * 0.22], fill=shade(base, 1.3))
    draw.rectangle([x0, y0 + h * 0.78, x0 + w - 1, y0 + h - 1], fill=shade(base, 0.6))
    if style == 'clay':
        step = h * 0.9
        for k in range(int(w / step) + 1):
            cx = x0 + k * step
            draw.ellipse([cx, y0 + h * 0.15, cx + step * 0.95, y0 + h * 0.8], fill=shade(base, 1.08),
                         outline=shade(base, 0.6), width=2)
    elif style == 'plate':
        for k in range(int(w / 32) + 1):
            draw.ellipse([x0 + k * 32 + 10, y0 + h * 0.45, x0 + k * 32 + 20, y0 + h * 0.45 + 10], fill=shade(base, 1.4))
    elif style == 'corrugated':
        for k in range(int(w / 32) + 1):
            draw.rectangle([x0 + k * 32, y0 + h * 0.22, x0 + k * 32 + 14, y0 + h * 0.78], fill=shade(base, 1.15))


def ragged(cx, cy, r, points, seed, wobble=0.35):
    rnd = random.Random(seed)
    out = []
    for i in range(points):
        a = 2 * math.pi * i / points
        rr = r * (1 - wobble / 2 + rnd.random() * wobble)
        out.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return out


def holed(img, mask, x0, y0, style):
    d = ImageDraw.Draw(img)
    m = ImageDraw.Draw(mask)
    field(d, x0, y0, DAMAGE, DAMAGE, style, DAMAGE)
    cx, cy = x0 + DAMAGE * 0.52, y0 + DAMAGE * 0.5
    rim = ragged(cx, cy, DAMAGE * 0.36, 22, 3, 0.4)
    hole = ragged(cx, cy, DAMAGE * 0.28, 22, 3, 0.45)
    d.polygon(rim, fill=shade(BASE[style], 0.55))
    rnd = random.Random(11)
    for i in range(9):
        a = rnd.random() * 2 * math.pi
        r0, r1 = DAMAGE * 0.3, DAMAGE * (0.38 + rnd.random() * 0.12)
        d.line([cx + math.cos(a) * r0, cy + math.sin(a) * r0, cx + math.cos(a) * r1, cy + math.sin(a) * r1],
               fill=shade(BASE[style], 0.45), width=4)
    d.polygon(hole, fill=VOID)
    m.polygon(hole, fill=0)


def collapsed(img, mask, x0, y0, style):
    d = ImageDraw.Draw(img)
    m = ImageDraw.Draw(mask)
    d.rectangle([x0, y0, x0 + DAMAGE - 1, y0 + DAMAGE - 1], fill=VOID)
    m.rectangle([x0, y0, x0 + DAMAGE - 1, y0 + DAMAGE - 1], fill=0)
    # The remnant: field along the north edge with a torn lower boundary, and a slab sagging off it.
    rnd = random.Random(5)
    edge = [(x0, y0)]
    for i in range(13):
        t = i / 12
        edge.append((x0 + t * DAMAGE, y0 + DAMAGE * (0.22 + rnd.random() * 0.16)))
    edge.append((x0 + DAMAGE, y0))
    remnant = Image.new('RGB', (DAMAGE, DAMAGE))
    field(ImageDraw.Draw(remnant), 0, 0, DAMAGE, DAMAGE, style, DAMAGE)
    keep = Image.new('L', (DAMAGE, DAMAGE), 0)
    ImageDraw.Draw(keep).polygon([(px - x0, py - y0) for px, py in edge], fill=255)
    img.paste(remnant, (x0, y0), keep)
    m.polygon(edge, fill=255)
    sag = [(x0 + DAMAGE * 0.18, y0 + DAMAGE * 0.36), (x0 + DAMAGE * 0.62, y0 + DAMAGE * 0.33),
           (x0 + DAMAGE * 0.56, y0 + DAMAGE * 0.62), (x0 + DAMAGE * 0.24, y0 + DAMAGE * 0.68)]
    d.polygon(sag, fill=shade(BASE[style], 0.62))
    m.polygon(sag, fill=255)
    # Two broken joists hanging into the room.
    for (ax, bx, by) in ((0.72, 0.8, 0.78), (0.86, 0.9, 0.6)):
        beam = [(x0 + DAMAGE * ax, y0 + DAMAGE * 0.3), (x0 + DAMAGE * (ax + 0.05), y0 + DAMAGE * 0.3),
                (x0 + DAMAGE * (bx + 0.05), y0 + DAMAGE * by), (x0 + DAMAGE * bx, y0 + DAMAGE * by)]
        d.polygon(beam, fill=(88, 64, 44) if style == 'clay' else (78, 80, 82))
        m.polygon(beam, fill=255)


def burnt(img, mask, x0, y0, style):
    d = ImageDraw.Draw(img)
    m = ImageDraw.Draw(mask)
    d.rectangle([x0, y0, x0 + DAMAGE - 1, y0 + DAMAGE - 1], fill=VOID)
    m.rectangle([x0, y0, x0 + DAMAGE - 1, y0 + DAMAGE - 1], fill=0)
    beam = (58, 40, 30) if style == 'clay' else (64, 60, 58)
    width = DAMAGE * 0.075
    # Rafters across the grain at a third of a cell, symmetric, so they run on into the next burnt cell.
    for fx in (1 / 6, 1 / 2, 5 / 6):
        cx = x0 + DAMAGE * fx
        d.rectangle([cx - width / 2, y0, cx + width / 2, y0 + DAMAGE - 1], fill=beam)
        m.rectangle([cx - width / 2, y0, cx + width / 2, y0 + DAMAGE - 1], fill=255)
    # A purlin along the grain, broken, and charred scraps of the roof left on the rafters.
    d.rectangle([x0, y0 + DAMAGE * 0.47, x0 + DAMAGE * 0.62, y0 + DAMAGE * 0.53], fill=shade(beam, 0.8))
    m.rectangle([x0, y0 + DAMAGE * 0.47, x0 + DAMAGE * 0.62, y0 + DAMAGE * 0.53], fill=255)
    for (sx, sy, sr, seed) in ((0.12, 0.15, 0.13, 1), (0.84, 0.82, 0.14, 2), (0.5, 0.9, 0.09, 4)):
        scrap = ragged(x0 + DAMAGE * sx, y0 + DAMAGE * sy, DAMAGE * sr, 9, seed, 0.6)
        d.polygon(scrap, fill=shade(BASE[style], 0.38))
        m.polygon(scrap, fill=255)


def guide(style):
    img = Image.new('RGB', (W, H), (255, 255, 255))
    mask = Image.new('L', (W, H), 0)
    d = ImageDraw.Draw(img)
    m = ImageDraw.Draw(mask)
    sx, sy = SWATCH
    field(d, sx, sy, CELL * 2, CELL * 2, style, CELL)
    m.rectangle([sx, sy, sx + CELL * 2 - 1, sy + CELL * 2 - 1], fill=255)
    x, y, w, h = STRIP
    strip(d, x, y, w, h, style)
    m.rectangle([x, y, x + w - 1, y + h - 1], fill=255)
    for (at, fn) in ((HOLED, holed), (COLLAPSED, collapsed), (BURNT, burnt)):
        m.rectangle([at[0], at[1], at[0] + DAMAGE - 1, at[1] + DAMAGE - 1], fill=255)
        fn(img, mask, at[0], at[1], style)
    GUIDES.mkdir(parents=True, exist_ok=True)
    img.save(GUIDES / f'{style}-guide.png')
    mask.save(GUIDES / f'{style}-mask.png')


if __name__ == '__main__':
    for s in STYLES:
        guide(s)
