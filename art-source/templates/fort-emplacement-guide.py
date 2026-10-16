"""Geometry only: a snappable concrete gun-emplacement set, nine frames of one 3x3 patch plus three platform variants.

Canvas 1536x1024, 8 columns x 4 rows of 192px full-cell frames, overhead and orthographic. The patch occupies columns
0-2 of rows 0-2 and is read in the same order as every wall block: row 0 NW N NE, row 1 W C E, row 2 SW S SE, each named
for the side the *exterior* is on. Columns 4-6 of row 0 are three centre (platform) variants. Everything else is empty.

Each edge frame runs a raised parapet along the exterior edge: outer batter (the sloped face down to the ground, 11% of
the cell), top face (24%), inner batter (8%), then the flat gun platform. Corners join two of them with a square mitre.
The profile is identical on every edge, so frames join edge to edge in any arrangement of 3x3 and larger. The
platform tone is the same on every frame so interior cells meet seamlessly. Light comes from the north-west.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
S = 192
OUTER = round(S * 0.11)
TOP = round(S * 0.24)
INNER = round(S * 0.08)
DEPTH = OUTER + TOP + INNER

PLATFORM = (150, 150, 146, 255)
JOINT = (122, 122, 118, 255)
TOPFACE = (186, 186, 180, 255)
# Batter tones, by the side the face looks toward (light from the north-west).
OUTER_TONE = {'n': (128, 128, 125, 255), 'w': (126, 126, 123, 255), 's': (78, 79, 78, 255), 'e': (82, 83, 82, 255)}
INNER_TONE = {'n': (112, 112, 109, 255), 'w': (116, 116, 112, 255), 's': (170, 170, 166, 255), 'e': (166, 166, 162, 255)}


def band(d, side, a, b, fill):
    """Fill the strip a..b (cells from the exterior edge) along one side, full length."""
    r = {'n': (0, a, S - 1, b - 1), 's': (0, S - b, S - 1, S - 1 - a),
         'w': (a, 0, b - 1, S - 1), 'e': (S - b, 0, S - 1 - a, S - 1)}[side]
    d.rectangle(r, fill=fill)


def platform(d):
    d.rectangle((0, 0, S - 1, S - 1), fill=PLATFORM)


def frame(sides):
    cell = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(cell)
    platform(d)
    for side in sides:
        band(d, side, 0, OUTER, OUTER_TONE[side])
        band(d, side, OUTER, OUTER + TOP, TOPFACE)
        band(d, side, OUTER + TOP, DEPTH, INNER_TONE[side])
    return cell


def centre(variant):
    cell = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(cell)
    platform(d)
    if variant == 1:  # a pair of tie-down plates and a drain
        for x, y in ((40, 40), (S - 40, 40), (40, S - 40), (S - 40, S - 40)):
            d.rectangle((x - 9, y - 9, x + 9, y + 9), fill=JOINT)
        d.ellipse((S // 2 - 10, S // 2 - 10, S // 2 + 10, S // 2 + 10), fill=JOINT)
    if variant == 2:  # a centred worn recess where a mount bears
        d.rectangle((S // 2 - 34, S // 2 - 34, S // 2 + 34, S // 2 + 34), outline=JOINT, width=5)
    return cell


guide = Image.new('RGBA', (1536, 1024))
patch = {(0, 0): 'nw', (1, 0): 'n', (2, 0): 'ne', (0, 1): 'w', (1, 1): '', (2, 1): 'e',
         (0, 2): 'sw', (1, 2): 's', (2, 2): 'se'}
for (c, r), key in patch.items():
    guide.alpha_composite(frame(key), (c * S, r * S))
for i in range(3):
    guide.alpha_composite(centre(i), ((4 + i) * S, 0))
guide.save(ROOT / 'art-source/templates/fort-emplacement-guide.png')
