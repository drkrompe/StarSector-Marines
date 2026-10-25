"""Geometry guide for the lobster trade's props (`swarm-lore-guide.md`, the lobster thread): set dressing for a
market's fish stall, a licensed kitchen in a shop's back room, and a freight yard. Winks only.

One 1536x1024 RGBA sheet, one world cell = CELL px, frames laid out explicitly with transparent gutters; north is
the top of a frame. FRAMES gives each frame's (x, y, w, h) in pixels.

- lobster-tank (1x1): a round pressure tank with a porthole lid, a lobster behind the glass.
- lobster-tank-burst (1x1): the same tank torn open at the lid, empty, sea water spilled round its foot.
- lobster-cooker (2x1): a licensed kitchen's range with a clamped pressure cooker; the back (wall side) is north.
- lobster-crate (1x1): a pressurised shipping canister in a square frame, a pressure hatch on top.

Usage: python art-source/templates/lobster-trade-guide.py <out-dir>
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

W, H, CELL, G = 1536, 1024, 300, 40

FRAMES = {
    'lobster-tank': (G, G, CELL, CELL),
    'lobster-tank-burst': (G + CELL + G, G, CELL, CELL),
    'lobster-crate': (G + 2 * (CELL + G), G, CELL, CELL),
    'lobster-cooker': (G, G + CELL + G, 2 * CELL, CELL),
}


def tank(d, x, y, burst):
    cx, cy = x + CELL // 2, y + CELL // 2
    if burst:
        # Water spilled round the foot, inside the frame.
        d.ellipse((cx - 140, cy - 120, cx + 135, cy + 140), fill='#3d5f63')
    d.ellipse((cx - 96, cy - 96, cx + 96, cy + 96), fill='#6d7574')       # vessel rim
    for k in range(12):                                                     # bolts round the lid
        a = k * math.pi / 6
        bx, by = cx + 82 * math.cos(a), cy + 82 * math.sin(a)
        d.ellipse((bx - 6, by - 6, bx + 6, by + 6), fill='#3a3f3f')
    if burst:
        d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill='#1d2526')    # open, empty
        for a0 in range(0, 360, 60):                                        # torn lid petals
            d.pieslice((cx - 110, cy - 110, cx + 110, cy + 110), a0, a0 + 22, fill='#8a9190')
    else:
        d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill='#8a9190')     # lid
        d.ellipse((cx - 48, cy - 48, cx + 48, cy + 48), fill='#2f6b72')     # porthole water
        d.ellipse((cx - 14, cy - 34, cx + 14, cy + 30), fill='#b5452c')     # the lobster
        d.polygon([(cx - 14, cy - 26), (cx - 34, cy - 44), (cx - 10, cy - 36)], fill='#b5452c')
        d.polygon([(cx + 14, cy - 26), (cx + 34, cy - 44), (cx + 10, cy - 36)], fill='#b5452c')
    d.ellipse((cx + 70, cy - 108, cx + 106, cy - 72), fill='#d8d2b8')       # gauge
    d.ellipse((cx - 112, cy + 70, cx - 72, cy + 110), fill='#a8362c')       # valve wheel


def crate(d, x, y):
    cx, cy = x + CELL // 2, y + CELL // 2
    d.rectangle((cx - 120, cy - 120, cx + 120, cy + 120), fill='#4f5a5c')   # frame
    d.rectangle((cx - 104, cy - 104, cx + 104, cy + 104), fill='#c9cbc4')   # insulated body
    d.ellipse((cx - 56, cy - 56, cx + 56, cy + 56), fill='#7d8584')         # pressure hatch
    d.ellipse((cx - 36, cy - 36, cx + 36, cy + 36), fill='#5d6564')
    d.rectangle((cx + 62, cy + 62, cx + 98, cy + 98), fill='#b5452c')       # red pictogram corner


def cooker(d, x, y):
    w = 2 * CELL
    d.rectangle((x + 12, y + 20, x + w - 13, y + CELL - 40), fill='#8f9493')  # range top
    d.rectangle((x + 12, y + 20, x + w - 13, y + 46), fill='#4b5050')         # backsplash (north)
    d.ellipse((x + 60, y + 70, x + 270, y + 240), fill='#3a3f3f')            # burner ring
    d.ellipse((x + 75, y + 85, x + 255, y + 225), fill='#b7bcbb')            # pressure cooker lid
    for bx in (x + 75, x + 235):                                              # lid clamps
        d.rectangle((bx, y + 140, bx + 20, y + 170), fill='#3a3f3f')
    d.ellipse((x + 150, y + 140, x + 180, y + 170), fill='#a8362c')          # steam valve
    d.ellipse((x + 340, y + 90, x + 460, y + 210), fill='#3a3f3f')           # second burner
    d.rectangle((x + 490, y + 70, x + 570, y + 130), fill='#d6b04a')         # licence plaque


def build():
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    tank(d, *FRAMES['lobster-tank'][:2], False)
    tank(d, *FRAMES['lobster-tank-burst'][:2], True)
    crate(d, *FRAMES['lobster-crate'][:2])
    cooker(d, *FRAMES['lobster-cooker'][:2])
    return img


if __name__ == '__main__':
    build().save(Path(sys.argv[1]) / 'lobster-trade-guide.png')
