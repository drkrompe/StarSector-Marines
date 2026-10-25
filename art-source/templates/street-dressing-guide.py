"""Geometry guides for the district street dressing (`set-piece-districts.md`), two sheets.

Both sheets are 1536x1024 RGBA (a canvas ImageGen returns), one world cell = CELL px, frames laid out
explicitly with transparent gutters. North is the top of a frame. Frames (x, y, w, h) in pixels are FRAMES
below; the authoring documents cut the raw at these boxes scaled to the raw's own size.

`street-dressing` (main street):
- three shop awnings, 3x1 cells each: the shop wall is the frame's north edge, the canvas runs out over the
  pavement to a valance on the south edge. Striped canvas, plain canvas, corrugated steel canopy. Drawn
  overhead (roof pass), they never block.
- two street lamps, 1x1: post foot low in the cell, arm reaching north to the head. North is the carriageway.

`industrial-street-kit` (industrial zone and cordoned facility):
- a 3x1 strip of overhead pipe rack, cut per cell: a support beam crossing the pipes, and two straight cells.
  Flange collars sit on every cell boundary so straight cells repeat without a visible seam.
- the gantry column (1x1) that stands under a beam cell at each kerb.
- a 2x1 concrete jersey barrier, a 1x1 steel bollard, the boom gate's 1x1 post and its 3x1 arm.

Usage: python art-source/templates/street-dressing-guide.py <out-dir>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H, CELL, G = 1536, 1024, 300, 40

FRAMES = {
    'street-dressing': {
        'awning-striped': (G, G, 3 * CELL, CELL),
        'awning-canvas': (G, G + (CELL + G), 3 * CELL, CELL),
        'awning-steel': (G, G + 2 * (CELL + G), 3 * CELL, CELL),
        'lamp-colonial': (G + 3 * CELL + 2 * G, G, CELL, CELL),
        'lamp-industrial': (G + 3 * CELL + 2 * G, G + (CELL + G), CELL, CELL),
    },
    'industrial-street-kit': {
        'pipe-beam': (G, G, CELL, CELL),
        'pipe-straight': (G + CELL, G, CELL, CELL),
        'pipe-straight-b': (G + 2 * CELL, G, CELL, CELL),
        'gantry-column': (G + 3 * CELL + 2 * G, G, CELL, CELL),
        'jersey-barrier': (G, G + (CELL + G), 2 * CELL, CELL),
        'bollard': (G + 2 * CELL + 2 * G, G + (CELL + G), CELL, CELL),
        'boom-post': (G + 3 * CELL + 4 * G, G + (CELL + G), CELL, CELL),
        'boom-arm': (G, G + 2 * (CELL + G), 3 * CELL, CELL),
    },
}


def awning(d, x, y, kind):
    w, depth = 3 * CELL, int(CELL * 0.86)
    # Wall rail along the north edge, then the canvas falling away south to a valance.
    d.rectangle((x, y, x + w - 1, y + 16), fill='#3b3a37')
    body = (x + 6, y + 16, x + w - 7, y + depth - 34)
    if kind == 'striped':
        stripes = 12
        for i in range(stripes):
            x0 = body[0] + (body[2] - body[0]) * i // stripes
            x1 = body[0] + (body[2] - body[0]) * (i + 1) // stripes - 1
            d.rectangle((x0, body[1], x1, body[3]), fill='#a8362c' if i % 2 == 0 else '#e2d6b8')
        valance = ['#a8362c', '#e2d6b8']
    elif kind == 'canvas':
        d.rectangle(body, fill='#3f6a52')
        valance = ['#2f5240', '#2f5240']
    else:
        d.rectangle(body, fill='#8d9192')
        for i in range(1, 30):
            xx = body[0] + (body[2] - body[0]) * i // 30
            d.line((xx, body[1], xx, body[3]), fill='#6c7172', width=3)
        valance = ['#5d6162', '#5d6162']
    # Scalloped valance: a row of half-discs hanging off the front edge (steel: a plain fascia).
    vy = body[3]
    if kind == 'steel':
        d.rectangle((body[0], vy, body[2], vy + 22), fill=valance[0])
        return
    n = 12
    for i in range(n):
        x0 = body[0] + (body[2] - body[0]) * i // n
        x1 = body[0] + (body[2] - body[0]) * (i + 1) // n - 1
        d.rectangle((x0, vy, x1, vy + 14), fill=valance[i % 2])
        d.pieslice((x0, vy, x1, vy + 2 * 26), 0, 180, fill=valance[i % 2])


def lamp(d, x, y, kind):
    cx = x + CELL // 2
    foot_y = y + int(CELL * 0.74)
    head_y = y + int(CELL * 0.22)
    d.ellipse((cx - 30, foot_y - 30, cx + 30, foot_y + 30), fill='#4a4c4b')    # base plate
    d.ellipse((cx - 18, foot_y - 18, cx + 18, foot_y + 18), fill='#2c2e2e')    # post top
    d.rectangle((cx - 7, head_y, cx + 7, foot_y), fill='#2c2e2e')              # arm
    if kind == 'colonial':
        d.ellipse((cx - 44, head_y - 44, cx + 44, head_y + 44), fill='#2c2e2e')
        d.ellipse((cx - 30, head_y - 30, cx + 30, head_y + 30), fill='#e8d79a')
    else:
        d.rounded_rectangle((cx - 34, head_y - 58, cx + 34, head_y + 30), 18, fill='#5b6061')
        d.rounded_rectangle((cx - 22, head_y - 46, cx + 22, head_y + 16), 10, fill='#e8e2c4')


def pipes(d, x, y, w, beam):
    # Three pipes east-west across the middle of the cell, collars on every cell boundary.
    if beam:
        d.rectangle((x + CELL // 2 - 26, y, x + CELL // 2 + 26, y + CELL - 1), fill='#6b5a3a')
        d.rectangle((x + CELL // 2 - 10, y, x + CELL // 2 + 10, y + CELL - 1), fill='#4d412b')
    for py, r, col in ((0.30, 26, '#8a8f8c'), (0.50, 34, '#9a7b54'), (0.70, 22, '#6f7a7e')):
        yy = y + int(CELL * py)
        d.rectangle((x, yy - r, x + w - 1, yy + r), fill=col)
        d.rectangle((x, yy - r // 3, x + w - 1, yy - r // 3 + 6), fill='#c4c8c4')  # highlight line
        for b in range(x, x + w + 1, CELL):
            d.rectangle((b - 9, yy - r - 6, b + 8, yy + r + 6), fill='#4b4f4e')


def column(d, x, y):
    cx, cy = x + CELL // 2, y + CELL // 2
    d.rectangle((cx - 66, cy - 66, cx + 66, cy + 66), fill='#9a978f')            # footing
    d.rectangle((cx - 44, cy - 44, cx + 44, cy + 44), fill='#5d625f')            # base plate
    for bx, by in ((-32, -32), (32, -32), (-32, 32), (32, 32)):
        d.ellipse((cx + bx - 6, cy + by - 6, cx + bx + 6, cy + by + 6), fill='#2f3231')
    d.rectangle((cx - 30, cy - 30, cx + 30, cy - 18), fill='#6b5a3a')            # H flanges
    d.rectangle((cx - 30, cy + 18, cx + 30, cy + 30), fill='#6b5a3a')
    d.rectangle((cx - 6, cy - 18, cx + 6, cy + 18), fill='#6b5a3a')              # web


def jersey(d, x, y):
    w = 2 * CELL
    d.rounded_rectangle((x + 8, y + CELL // 2 - 48, x + w - 9, y + CELL // 2 + 48), 10, fill='#a19d93')
    d.rounded_rectangle((x + 14, y + CELL // 2 - 18, x + w - 15, y + CELL // 2 + 18), 6, fill='#bdb9ae')
    for x0 in (x + 8, x + w - 49):
        for k in range(4):
            d.rectangle((x0 + k * 10, y + CELL // 2 - 48, x0 + k * 10 + 4, y + CELL // 2 + 48),
                        fill='#d2a52c')


def bollard(d, x, y):
    cx, cy = x + CELL // 2, y + CELL // 2
    d.ellipse((cx - 34, cy - 34, cx + 34, cy + 34), fill='#d2a52c')
    d.ellipse((cx - 22, cy - 22, cx + 22, cy + 22), fill='#3b3e3d')


def boom_post(d, x, y):
    cx, cy = x + CELL // 2, y + CELL // 2
    d.rounded_rectangle((cx - 60, cy - 46, cx + 60, cy + 46), 8, fill='#c9c4b5')
    d.rectangle((cx - 60, cy - 46, cx + 60, cy - 30), fill='#a8362c')
    d.ellipse((cx + 24, cy - 14, cx + 52, cy + 14), fill='#3b3e3d')             # pivot hub, east side


def boom_arm(d, x, y):
    w, cy = 3 * CELL, y + CELL // 2
    d.rectangle((x, cy - 16, x + w - 12, cy + 16), fill='#e8e2d4')
    for k in range(9):
        x0 = x + 30 + k * (w - 60) // 9
        if k % 2 == 0:
            d.rectangle((x0, cy - 16, x0 + (w - 60) // 18, cy + 16), fill='#a8362c')
    d.rectangle((x + w - 30, cy - 22, x + w - 12, cy + 22), fill='#3b3e3d')     # tip


def build(sheet):
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    f = FRAMES[sheet]
    if sheet == 'street-dressing':
        for k in ('striped', 'canvas', 'steel'):
            awning(d, *f['awning-' + k][:2], k)
        lamp(d, *f['lamp-colonial'][:2], 'colonial')
        lamp(d, *f['lamp-industrial'][:2], 'industrial')
    else:
        x, y = f['pipe-beam'][:2]
        pipes(d, x, y, CELL, True)          # the beam, under the pipes resting on it
        pipes(d, x, y, 3 * CELL, False)
        column(d, *f['gantry-column'][:2])
        jersey(d, *f['jersey-barrier'][:2])
        bollard(d, *f['bollard'][:2])
        boom_post(d, *f['boom-post'][:2])
        boom_arm(d, *f['boom-arm'][:2])
    return img


if __name__ == '__main__':
    out = Path(sys.argv[1])
    for name in FRAMES:
        build(name).save(out / f'{name}-guide.png')
