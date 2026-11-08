"""Placement guides only; ImageGen supplies the finished sky port deck
(material: art-source/voids/sky-port/industrial/terrace-roof.png).

Two 1536x1536 opaque guides, each one seamless tile of the deck laid as an
8x8-cell pattern (192 px a cell here):
  sky-port-deck-guide.png  4x4 heavy deck plates, two cells square, seams
                           centred on the plate lines so half a seam falls on
                           each canvas edge and the tile repeats on a full
                           seam; a few plates carry a drainage grille or a
                           hatch cover.
  sky-port-pad-guide.png   the landing pad: two wide heat plates, faint weld
                           lines only.
"""
from pathlib import Path
from PIL import Image, ImageDraw

S, P = 1536, 384
here = Path(__file__).parent


def grey(v):
    return (v, v, v, 255)


def deck():
    image = Image.new('RGBA', (S, S), grey(104))
    d = ImageDraw.Draw(image)
    for k in range(0, S + 1, P):
        d.rectangle((k - 6, 0, k + 5, S), fill=grey(52))
        d.rectangle((0, k - 6, S, k + 5), fill=grey(52))
    for px in range(4):
        for py in range(4):
            x, y = px * P, py * P
            for bx in (30, P // 2, P - 30):
                for by in (30, P - 30):
                    for cx, cy in ((x + bx, y + by), (x + by, y + bx)):
                        d.ellipse((cx - 10, cy - 10, cx + 10, cy + 10), fill=grey(150))
    # Drainage grilles and hatch covers on a few plates, never on a seam.
    for px, py in ((1, 0), (3, 2), (0, 3)):
        x, y = px * P + 112, py * P + 112
        d.rectangle((x, y, x + 160, y + 160), fill=grey(30), outline=grey(130), width=10)
        for k in range(x + 22, x + 150, 22):
            d.line((k, y + 10, k, y + 150), fill=grey(96), width=7)
    for px, py in ((2, 1), (0, 1)):
        x, y = px * P + 92, py * P + 92
        d.rectangle((x, y, x + 200, y + 200), fill=grey(88), outline=grey(60), width=8)
        d.line((x + 100, y + 16, x + 100, y + 184), fill=grey(60), width=6)
    image.save(here / 'sky-port-deck-guide.png')


def pad():
    image = Image.new('RGBA', (S, S), grey(138))
    d = ImageDraw.Draw(image)
    for k in (0, S // 2, S):
        d.rectangle((0, k - 3, S, k + 2), fill=grey(112))
    image.save(here / 'sky-port-pad-guide.png')


deck()
pad()
