"""Geometry only: a bolder concertina wire for the fort belt, two 192px full-cell frames.

Canvas 1536x1024, the same registration as fort-earthworks (frame 0 at (0, 576) horizontal run, frame 1 at (192, 576)
vertical run), so the result is composited over those two frames of the earthworks sheet by
art-source/tilesets/composite-fort-wire.py and exported through the ordinary tileset pipeline.

The v1 coil was 60px of a 192px cell, a grey hairline on tan earth. This one fills ~62% of the cell width (120px
coils), carries a dark contact-shadow band so the obstacle reads against bare earth, bright galvanised strands and
sharp barbs, and edge-to-edge runs so neighbours join.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
S = 192


def wire(horizontal):
    cell = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(cell)
    # contact shadow band: dark disturbed earth under the whole run
    band = (0, S // 2 - 62, S, S // 2 + 62) if horizontal else (S // 2 - 62, 0, S // 2 + 62, S)
    d.rectangle(band, fill=(40, 30, 22, 150))
    for i in range(0, S, 24):
        box = (i + 2, S // 2 - 56, i + 50, S // 2 + 56) if horizontal else (S // 2 - 56, i + 2, S // 2 + 56, i + 50)
        d.ellipse(box, outline='#c9ced0', width=8)
        # barbs at the top and bottom of every coil
        for sign in (-1, 1):
            if horizontal:
                x, y = i + 26, S // 2 + sign * 56
                d.polygon([(x - 6, y), (x + 6, y), (x, y + sign * 16)], fill='#e6eaeb')
            else:
                x, y = S // 2 + sign * 56, i + 26
                d.polygon([(x, y - 6), (x, y + 6), (x + sign * 16, y)], fill='#e6eaeb')
    d.line((0, S // 2, S, S // 2) if horizontal else (S // 2, 0, S // 2, S), fill='#3a3f41', width=6)
    return cell


guide = Image.new('RGBA', (1536, 1024))
guide.alpha_composite(wire(True), (0, 3 * S))
guide.alpha_composite(wire(False), (S, 3 * S))
guide.save(ROOT / 'art-source/templates/fort-wire-guide.png')
