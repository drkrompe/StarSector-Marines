"""Geometry guide for the deck-motion sheet: the moving parts of shipboard props, cut from their static bases.

64 px per cell on a 16x16-cell canvas. Every slot is a whole-cell placement frame with at least one empty cell of
gutter, so the exporter cuts each frame mechanically and every part keeps its registration against its base:

    slot                 frame (col,row,w,h)  role
    crane-runway         0,0,5,7              static overhead runway: two girders over the bay's frame columns
    rack-lift-rail       6,0,2,6              static guide rail down the aisle face of a picking rack
    rower-base           9,0,2,3              the shipped rower with its seat taken off the rail
    fan-rotor            12,0,2,2             spinning rotor, pivot at frame centre, 1.25-cell disc
    rower-seat           14,0,1,1             sliding seat, centred in its frame
    rack-lift-carriage   12,3,1,2             sliding carriage on the guide rail
    treadmill-belt       14,3,1,1             seamless belt tile, period runs top to bottom
    conveyor-belt        14,5,1,1             seamless belt tile, period runs left to right
    crane-bridge         0,8,5,1              sliding overhead bridge with its hoist trolley

Belt tiles carry plain rubber at the two edges the period wraps across, so tiles laid end to end join without a seam.
The rower base is the shipped rower (ship-gym.png) with the seat cleared and the rail continued under it.
Superseded: the real-size gym sheet carries its own seatless rower and seat, so the rower-base and rower-seat
slots are excluded from export, and this crop no longer reads a rower out of today's ship-gym.png.
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw

C = 64
ROOT = Path(__file__).resolve().parents[2]
out = Image.new('RGBA', (16 * C, 16 * C))
d = ImageDraw.Draw(out)

STEEL = '#8d968c'
STEEL_DARK = '#4b5552'
STEEL_LIGHT = '#b4b9aa'
RUBBER = '#2b2f30'
RIB = '#3e4445'
HAZARD = '#a99354'


def px(col, row, x, y):
    return int(col * C + x * C), int(row * C + y * C)


def box(col, row, x0, y0, x1, y1, **kw):
    d.rectangle((*px(col, row, x0, y0), *px(col, row, x1, y1)), **kw)


def rbox(col, row, x0, y0, x1, y1, r, **kw):
    d.rounded_rectangle((*px(col, row, x0, y0), *px(col, row, x1, y1)), r, **kw)


# crane-runway: two I-beam girders centred on columns 0 and 4, end stops at both ends.
for cx in (0.5, 4.5):
    box(0, 0, cx - 0.22, 0.06, cx + 0.22, 6.94, fill=STEEL, outline=STEEL_DARK, width=3)
    box(0, 0, cx - 0.06, 0.06, cx + 0.06, 6.94, fill=STEEL_DARK)
    for y in (0.06, 6.68):
        box(0, 0, cx - 0.3, y, cx + 0.3, y + 0.26, fill=HAZARD, outline=STEEL_DARK, width=2)
    for y in range(1, 7):
        box(0, 0, cx - 0.22, y - 0.03, cx + 0.22, y + 0.03, fill=STEEL_LIGHT)

# crane-bridge: box girder across, end trucks on both runways, hoist trolley with hook block.
box(0, 8, 0.25, 0.3, 4.75, 0.7, fill=STEEL, outline=STEEL_DARK, width=3)
for x in range(1, 5):
    d.line((*px(0, 8, x - 0.4, 0.32), *px(0, 8, x + 0.4, 0.68)), fill=STEEL_DARK, width=3)
for x0 in (0.08, 4.42):
    rbox(0, 8, x0, 0.08, x0 + 0.5, 0.92, 4, fill=HAZARD, outline=STEEL_DARK, width=3)
rbox(0, 8, 2.05, 0.12, 2.95, 0.88, 6, fill='#6f7a72', outline=STEEL_DARK, width=3)
d.ellipse((*px(0, 8, 2.32, 0.32), *px(0, 8, 2.68, 0.68)), fill='#2f3536', outline=STEEL_LIGHT, width=3)

# rack-lift-rail: a guide rail down the aisle (left) face of a 2x6 rack, buffers at each end; the rest stays clear.
box(6, 0, 0.08, 0.1, 0.3, 5.9, fill=STEEL, outline=STEEL_DARK, width=3)
for y in (0.1, 5.68):
    box(6, 0, 0.04, y, 0.42, y + 0.22, fill=HAZARD, outline=STEEL_DARK, width=2)

# rack-lift-carriage: a narrow platform riding the rail, with forks reaching into the rack.
rbox(12, 3, 0.06, 0.2, 0.5, 1.8, 6, fill='#6f7a72', outline=STEEL_DARK, width=3)
for y in (0.45, 1.35):
    box(12, 3, 0.5, y, 0.94, y + 0.2, fill=STEEL_LIGHT, outline=STEEL_DARK, width=2)
box(12, 3, 0.14, 0.9, 0.42, 1.1, fill=HAZARD)

# rower-base: the shipped rower, seat cleared, rail continued beneath it.
rower = Image.open(ROOT / 'mod/graphics/tilesets/ship-gym.png').convert('RGBA').crop((128, 0, 256, 192))
seat = (24, 116, 104, 158)
clear = Image.new('RGBA', (seat[2] - seat[0], seat[3] - seat[1]))
rower.paste(clear, seat[:2])
rail = rower.getpixel((63, 108))
ImageDraw.Draw(rower).rectangle((53, seat[1], 72, seat[3]), fill=rail)
out.alpha_composite(rower, (9 * C, 0))

# rower-seat: a padded seat on a small carriage, centred.
rbox(14, 0, 0.18, 0.28, 0.82, 0.72, 8, fill='#3a3f40', outline=STEEL_DARK, width=3)
box(14, 0, 0.4, 0.2, 0.6, 0.8, outline=STEEL, width=2)

# fan-rotor: dark well, five swept blades and a hub, inside a thin bezel; 1.25-cell disc at frame centre.
cx, cy, r = 13 * C, 1 * C, int(0.625 * C)
d.ellipse((cx - r, cy - r, cx + r, cy + r), fill='#22282a', outline=STEEL_LIGHT, width=4)
for k in range(5):

    a = math.radians(k * 72)
    tip = (cx + math.cos(a) * r * 0.86, cy + math.sin(a) * r * 0.86)
    lead = (cx + math.cos(a + 0.55) * r * 0.62, cy + math.sin(a + 0.55) * r * 0.62)
    root = (cx + math.cos(a + 0.9) * r * 0.2, cy + math.sin(a + 0.9) * r * 0.2)
    d.polygon((root, lead, tip, (cx + math.cos(a - 0.2) * r * 0.25, cy + math.sin(a - 0.2) * r * 0.25)), fill=STEEL)
d.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), fill=STEEL_LIGHT, outline=STEEL_DARK, width=2)

# treadmill-belt: one period of ribbed rubber, ribs across the belt, plain rubber at top and bottom edges.
box(14, 3, 0, 0, 1, 1, fill=RUBBER)
for y in (0.25, 0.5, 0.75):
    box(14, 3, 0, y - 0.035, 1, y + 0.035, fill=RIB)

# conveyor-belt: one period of cleated belt running left to right, cleats across, plain belt at both wrap edges.
box(14, 5, 0, 0, 1, 1, fill='#33393a')
for x in (0.3, 0.7):
    box(14, 5, x - 0.05, 0.06, x + 0.05, 0.94, fill='#56605d')
box(14, 5, 0, 0, 1, 0.06, fill=STEEL_DARK)
box(14, 5, 0, 0.94, 1, 1, fill=STEEL_DARK)

out.save(Path(__file__).with_suffix('.png'))
