"""Geometry only: the whole tight collar of a 3x3-bodied emplacement as ONE plate, so every piece is painted in one pass.

Canvas 1536x1024. The plate is the real 5x5 collar a 3x3 body wears, in 192px cells at the top-left (x 0-959, y 0-959):
the ring's four corner cells are the inner-corner pieces, the three cells along each side are that side's swapped edge
piece (the cell north of the body wears the piece whose parapet is on its south edge, 's'; west wears 'e'; east 'w';
south 'n'), and the 3x3 inside is flat platform under the body. Parapet profile on every piece: outer batter 14%, top
face 29%, inner batter 11% of the cell (54% in all), the batter toward the body. Corner cells hold the mitred notch in
the corner nearest the body (NW ring cell wears inner-se, and so on). Because the pieces are drawn where they are used,
the bands run on from cell to cell and the model paints one continuous material; the exporter then cuts one cell of
each kind with measured rects. Light is from the north-west. Everything outside the plate is empty.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
S = 192
OUTER = round(S * 0.14)
TOP = round(S * 0.29)
INNER = round(S * 0.11)
DEPTH = OUTER + TOP + INNER

PLATFORM = (150, 150, 146, 255)
TOPFACE = (186, 186, 180, 255)
OUTER_TONE = {'n': (128, 128, 125, 255), 'w': (126, 126, 123, 255), 's': (78, 79, 78, 255), 'e': (82, 83, 82, 255)}
INNER_TONE = {'n': (112, 112, 109, 255), 'w': (116, 116, 112, 255), 's': (170, 170, 166, 255), 'e': (166, 166, 162, 255)}


def band_pixel(side, dist):
    """Tone of the parapet profile `dist` pixels from the exterior edge of a band along `side`, or None for platform."""
    if dist < OUTER:
        return OUTER_TONE[side]
    if dist < OUTER + TOP:
        return TOPFACE
    if dist < DEPTH:
        return INNER_TONE[side]
    return None


def edge(side):
    img = Image.new('RGBA', (S, S), PLATFORM)
    for y in range(S):
        for x in range(S):
            dist = {'n': y, 's': S - 1 - y, 'w': x, 'e': S - 1 - x}[side]
            tone = band_pixel(side, dist)
            if tone is not None:
                img.putpixel((x, y), tone)
    return img


def notch(corner):
    """Square of parapet in the cell corner `corner` ('nw','ne','sw','se'), mitred on the diagonal."""
    img = Image.new('RGBA', (S, S), PLATFORM)
    vy, hx = corner[0], corner[1]
    for y in range(S):
        for x in range(S):
            px = x if hx == 'w' else S - 1 - x
            py = y if vy == 'n' else S - 1 - y
            if px >= DEPTH or py >= DEPTH:
                continue
            tone = band_pixel(vy, py) if py > px else band_pixel(hx, px)
            if tone is not None:
                img.putpixel((x, y), tone)
    return img


def plate():
    out = Image.new('RGBA', (5 * S, 5 * S), PLATFORM)
    for i in (1, 2, 3):
        out.alpha_composite(edge('s'), (i * S, 0))
        out.alpha_composite(edge('n'), (i * S, 4 * S))
        out.alpha_composite(edge('e'), (0, i * S))
        out.alpha_composite(edge('w'), (4 * S, i * S))
    out.alpha_composite(notch('se'), (0, 0))
    out.alpha_composite(notch('sw'), (4 * S, 0))
    out.alpha_composite(notch('ne'), (0, 4 * S))
    out.alpha_composite(notch('nw'), (4 * S, 4 * S))
    return out


if __name__ == '__main__':
    guide = Image.new('RGBA', (1536, 1024))
    guide.alpha_composite(plate(), (0, 0))
    guide.save(ROOT / 'art-source/templates/fort-emplacement-guide.png')
