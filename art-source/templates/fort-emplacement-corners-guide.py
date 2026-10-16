"""Geometry only: the four inner-corner pieces of the tight emplacement collar, in three states.

Canvas 1536x1024, 192px frames, a contiguous 4x3 block at the top-left (x 0-767, y 0-575): columns are the corner of the
cell the parapet notch sits in (nw, ne, sw, se), rows are intact, damaged, destroyed. A tight collar hugs its turret
body, so at a diagonal ring cell the edge pieces on either side meet at the body's corner; this piece is that meeting:
a small square of parapet in the one cell corner nearest the body (14% outer batter, 29% top face, 11% inner batter, 54% in all, matching the measured depth of the cut edge pieces,
as in fort-emplacement-guide.py, mitred on the diagonal), everything else flat platform concrete. The notch's two
open sides run on into the neighbouring edge pieces' parapets at the same profile and tone. Light is from the
north-west. Damaged marks chips and cracks on the notch and a little scorch on the platform; destroyed breaks the notch
into stumps with rubble and rebar and scorches the platform.
"""
import random
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
DARK = (58, 58, 56, 255)
SCORCH = (70, 66, 60, 255)
RUBBLE = (120, 118, 112, 255)
REBAR = (120, 78, 52, 255)


def band_pixel(side, dist):
    """Tone of the parapet profile at `dist` pixels from the exterior edge of a band along `side`, or None for platform."""
    if dist < OUTER:
        return OUTER_TONE[side]
    if dist < OUTER + TOP:
        return TOPFACE
    if dist < DEPTH:
        return INNER_TONE[side]
    return None


def notch(corner):
    """corner: 'nw','ne','sw','se' - where the notch sits. The bands run along the edges nearest the body, i.e. the
    horizontal band along that corner's N/S edge and the vertical band along its E/W edge."""
    img = Image.new('RGBA', (S, S), PLATFORM)
    vy = corner[0]  # 'n' or 's'
    hx = corner[1]  # 'w' or 'e'
    for y in range(S):
        for x in range(S):
            px = x if hx == 'w' else S - 1 - x
            py = y if vy == 'n' else S - 1 - y
            if px >= DEPTH or py >= DEPTH:
                continue
            # The exterior of this collar is the side away from the body, so a band along the body-facing edge of
            # the cell has its outer batter at that cell edge. Choose the band profile by the mitre diagonal.
            if py > px:
                tone = band_pixel(vy, py)
            else:
                tone = band_pixel(hx, px)
            if tone is not None:
                img.putpixel((x, y), tone)
    return img


def mark(img, state, rng):
    d = ImageDraw.Draw(img)
    if state == 'damaged':
        for _ in range(14):
            x, y = rng.randrange(S), rng.randrange(S)
            if img.getpixel((x, y)) != PLATFORM:
                w = rng.randrange(5, 11)
                d.polygon([(x, y), (x + w, y + 3), (x + w // 2, y + w)], fill=(92, 92, 88, 255))
        for _ in range(4):
            x, y = rng.randrange(S), rng.randrange(S)
            pts = [(x, y)] + [(x + rng.randrange(-20, 21), y + rng.randrange(-20, 21)) for _ in range(4)]
            d.line(pts, fill=DARK, width=2)
        x, y = rng.randrange(40, S - 40), rng.randrange(40, S - 40)
        d.ellipse((x - 18, y - 12, x + 18, y + 12), fill=SCORCH)
    elif state == 'destroyed':
        for _ in range(5):
            x, y = rng.randrange(S), rng.randrange(S)
            if img.getpixel((x, y)) != PLATFORM:
                d.rectangle((x - 5, y - 5, x + rng.randrange(10, 22), y + rng.randrange(10, 22)), fill=PLATFORM)
        for _ in range(16):
            x, y = rng.randrange(S), rng.randrange(S)
            w = rng.randrange(6, 16)
            d.polygon([(x, y), (x + w, y + 3), (x + w - 3, y + w), (x - 2, y + w - 4)], fill=RUBBLE)
        for _ in range(3):
            x, y = rng.randrange(S), rng.randrange(S)
            d.line([(x, y), (x + rng.randrange(-26, 27), y + rng.randrange(-26, 27))], fill=REBAR, width=2)
        x, y = rng.randrange(40, S - 40), rng.randrange(40, S - 40)
        d.ellipse((x - 24, y - 16, x + 24, y + 16), fill=SCORCH)
    return img


guide = Image.new('RGBA', (1536, 1024))
rng = random.Random(5)
for row, state in enumerate(('intact', 'damaged', 'destroyed')):
    for col, corner in enumerate(('nw', 'ne', 'sw', 'se')):
        guide.alpha_composite(mark(notch(corner), state, rng), (col * S, row * S))
guide.save(ROOT / 'art-source/templates/fort-emplacement-corners-guide.png')
