"""Geometry only: sixteen connected trench sections, eight thick sandbag pieces, wire and a hedgehog.

Canvas 1536x1024, 8 columns x 4 rows of 192px full-cell frames; everything is overhead, orthographic and sits on
transparent ground. Slot order is the contract the runtime reads.

Rows 0-1: trench sections addressed by their open arms, slot = mask = N(1) + E(2) + S(4) + W(8), north at the top of the
frame, column = mask % 8, row = mask // 8. An arm runs from the cell centre to the middle of that edge; the dark channel
is 40% of the cell wide and meets the edge at 30%..70%, the sandbag lip either side is 12% wide, and the outer 18% is
transparent so that neighbouring sections join seamlessly and the ground shows beyond the parapet. Mask 0 is a
foxhole: a closed lip with a channel pit.
Row 2: thick sandbag parapet. Columns 0-3 are straight runs with the bags on the N, E, S, W edge; columns 4-7 are
corners NE, SE, SW, NW. A run fills the cell edge to edge and is 40% of the cell deep, so adjacent pieces join.
Row 3: concertina wire running horizontally, the same vertically, and a steel tank-trap hedgehog.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
S = 192
LIP = '#b59b66'
SEAM = '#8a7348'
CHANNEL = '#3a2f26'
BAG = '#c2a56a'


def trench(mask):
    cell = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(cell)
    a, b = int(S * 0.18), int(S * 0.82)
    c0, c1 = int(S * 0.30), int(S * 0.70)
    arms = {1: (0, 0), 2: (1, 0), 4: (0, 1), 8: (-1, 0)}
    d.rectangle((a, a, b - 1, b - 1), fill=LIP)
    for bit, (dx, dy) in arms.items():
        if not mask & bit:
            continue
        if dy < 0 or bit == 1:
            d.rectangle((a, 0, b - 1, a), fill=LIP)
        if bit == 4:
            d.rectangle((a, b - 1, b - 1, S - 1), fill=LIP)
        if bit == 2:
            d.rectangle((b - 1, a, S - 1, b - 1), fill=LIP)
        if bit == 8:
            d.rectangle((0, a, a, b - 1), fill=LIP)
    d.rectangle((c0, c0, c1 - 1, c1 - 1), fill=CHANNEL)
    if mask & 1:
        d.rectangle((c0, 0, c1 - 1, c0), fill=CHANNEL)
    if mask & 4:
        d.rectangle((c0, c1 - 1, c1 - 1, S - 1), fill=CHANNEL)
    if mask & 2:
        d.rectangle((c1 - 1, c0, S - 1, c1 - 1), fill=CHANNEL)
    if mask & 8:
        d.rectangle((0, c0, c0, c1 - 1), fill=CHANNEL)
    # Bag courses as seams on the lip, kept off the channel.
    for i in range(0, S, 24):
        for (x0, y0, x1, y1) in ((i, a + 4, i + 2, c0 - 5), (i, c1 + 5, i + 2, b - 5),
                                 (a + 4, i, c0 - 5, i + 2), (c1 + 5, i, b - 5, i + 2)):
            if cell.getpixel((min(S - 1, x0), min(S - 1, y0)))[3] and cell.getpixel((min(S - 1, x0), min(S - 1, y0)))[:3] == (0xb5, 0x9b, 0x66):
                d.rectangle((x0, y0, x1, y1), fill=SEAM)
    return cell


def bags(sides):
    cell = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(cell)
    depth = int(S * 0.40)
    rects = {'n': (0, 0, S - 1, depth), 's': (0, S - 1 - depth, S - 1, S - 1),
             'e': (S - 1 - depth, 0, S - 1, S - 1), 'w': (0, 0, depth, S - 1)}
    for side in sides:
        d.rounded_rectangle(rects[side], 14, fill=BAG)
        x0, y0, x1, y1 = rects[side]
        if side in 'ns':
            for x in range(x0 + 24, x1, 32):
                d.line((x, y0 + 6, x, y1 - 6), fill=SEAM, width=3)
            d.line((x0 + 8, (y0 + y1) // 2, x1 - 8, (y0 + y1) // 2), fill=SEAM, width=3)
        else:
            for y in range(y0 + 24, y1, 32):
                d.line((x0 + 6, y, x1 - 6, y), fill=SEAM, width=3)
            d.line(((x0 + x1) // 2, y0 + 8, (x0 + x1) // 2, y1 - 8), fill=SEAM, width=3)
    return cell


def wire(horizontal):
    cell = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(cell)
    for i in range(0, S, 24):
        box = (i + 2, S // 2 - 30, i + 46, S // 2 + 30) if horizontal else (S // 2 - 30, i + 2, S // 2 + 30, i + 46)
        d.ellipse(box, outline='#767a78', width=5)
    d.line((0, S // 2, S, S // 2) if horizontal else (S // 2, 0, S // 2, S), fill='#4b4f4d', width=4)
    return cell


def hedgehog():
    cell = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(cell)
    for p, q in (((40, 40), (152, 152)), ((152, 40), (40, 152)), ((96, 28), (96, 164))):
        d.line((p, q), fill='#59605f', width=16)
    d.ellipse((80, 80, 112, 112), fill='#3f4443')
    return cell


guide = Image.new('RGBA', (1536, 1024))
for mask in range(16):
    guide.alpha_composite(trench(mask), ((mask % 8) * S, (mask // 8) * S))
for i, sides in enumerate(('n', 'e', 's', 'w', 'ne', 'es', 'sw', 'wn')):
    guide.alpha_composite(bags(sides), (i * S, 2 * S))
guide.alpha_composite(wire(True), (0, 3 * S))
guide.alpha_composite(wire(False), (S, 3 * S))
guide.alpha_composite(hedgehog(), (2 * S, 3 * S))
guide.save(ROOT / 'art-source/templates/fort-earthworks-guide.png')
