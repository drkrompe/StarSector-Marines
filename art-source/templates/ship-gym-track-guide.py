"""Geometry guide for the ship-gym-track sheet: the cruise-ship gymnasium's running track and poolside lounger.

128 px per cell on a 10x10-cell canvas (a cell is about 0.75 m against a crewman's 0.6-cell shoulders). Whole-cell
frames with gutters, cut mechanically:

    slot      frame (col,row,w,h)  role
    lounger   1,1,3,1              a reclining pool lounger, 0.7 x 2.0 m, headrest at the left end
    track     1,4,3,3              seamless running-track rubber; the interior is cut as one 1x1 material sample
"""
from pathlib import Path
from PIL import Image, ImageDraw

C = 128
out = Image.new('RGBA', (10 * C, 10 * C))
d = ImageDraw.Draw(out)


def px(col, row, x, y):
    return int(col * C + x * C), int(row * C + y * C)


def box(col, row, x0, y0, x1, y1, **kw):
    d.rectangle((*px(col, row, x0, y0), *px(col, row, x1, y1)), **kw)


def rbox(col, row, x0, y0, x1, y1, r, **kw):
    d.rounded_rectangle((*px(col, row, x0, y0), *px(col, row, x1, y1)), r, **kw)


# Lounger: slatted frame the length of the frame, a raised back at the left, a thin cushion over the slats.
rbox(1, 1, 0.12, 0.1, 2.9, 0.9, 10, fill='#8d968c', outline='#4b5552', width=4)
for x in [0.35 + 0.16 * i for i in range(16)]:
    box(1, 1, x, 0.16, x + 0.07, 0.84, fill='#6f7a72')
rbox(1, 1, 0.16, 0.16, 0.85, 0.84, 8, fill='#b4b9aa', outline='#4b5552', width=3)
rbox(1, 1, 0.9, 0.2, 2.82, 0.8, 8, fill='#a7ae9c', outline='#4b5552', width=3)

# Track: one muted terracotta rubber field.
box(1, 4, 0, 0, 3, 3, fill='#7a4a3e')

out.save(Path(__file__).with_suffix('.png'))
