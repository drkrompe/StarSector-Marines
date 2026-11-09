"""Placeholder art for the derelict security walker (derelict-security-network.md, slice 9).

Deliberately plain and machine-drawn, so it never reads as finished art or as any company mech: a hexagonal torso
plate in Domain grey with hazard chevrons and a sensor eye, a leg bone, and a three-toed foot. Head-up (forward is
the top of the image). The owner picks the real look; this keeps the walker its own shape meanwhile.

Run: python art-source/derelict-walker/placeholder.py  (writes mod/graphics/battle/derelict-walker/)
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'mod', 'graphics', 'battle', 'derelict-walker')
GREY = (118, 122, 126, 255)
DARK = (58, 61, 64, 255)
EDGE = (30, 31, 33, 255)
HAZARD = (214, 172, 38, 255)
EYE = (255, 92, 60, 255)


def torso(size=128):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    r = size * 0.46
    hexagon = [(c + r * math.cos(math.radians(a)), c + r * math.sin(math.radians(a))) for a in range(30, 390, 60)]
    d.polygon(hexagon, fill=GREY, outline=EDGE, width=3)
    inner = [(c + r * 0.62 * math.cos(math.radians(a)), c + r * 0.62 * math.sin(math.radians(a)))
             for a in range(30, 390, 60)]
    d.polygon(inner, fill=DARK, outline=EDGE, width=2)
    # Hazard chevrons on the rear plate.
    for k in range(3):
        y = c + r * 0.70 + k * 0.0
        x0 = c - r * 0.45 + k * r * 0.32
        d.polygon([(x0, y - 6), (x0 + 10, y - 6), (x0 + 18, y + 4), (x0 + 8, y + 4)], fill=HAZARD)
    # Gun ports either side, racks on the back.
    d.rectangle([c - r * 0.95, c - r * 0.35, c - r * 0.70, c + r * 0.05], fill=DARK, outline=EDGE)
    d.rectangle([c + r * 0.70, c - r * 0.35, c + r * 0.95, c + r * 0.05], fill=DARK, outline=EDGE)
    # Sensor eye at the front.
    d.ellipse([c - 9, c - r * 0.80 - 9, c + 9, c - r * 0.80 + 9], fill=EYE, outline=EDGE, width=2)
    return img


def leg(width=24, length=128):
    img = Image.new('RGBA', (width, length), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([3, 0, width - 4, length - 1], radius=6, fill=GREY, outline=EDGE, width=2)
    d.rectangle([width / 2 - 2, 8, width / 2 + 2, length - 8], fill=DARK)
    d.ellipse([width / 2 - 7, length / 2 - 7, width / 2 + 7, length / 2 + 7], fill=DARK, outline=EDGE, width=2)
    return img


def foot(size=40):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    for a in (-35, 0, 35):
        x = c + math.sin(math.radians(a)) * size * 0.42
        y = c - math.cos(math.radians(a)) * size * 0.42
        d.line([(c, c), (x, y)], fill=EDGE, width=6)
        d.line([(c, c), (x, y)], fill=GREY, width=3)
    d.ellipse([c - 7, c - 7, c + 7, c + 7], fill=DARK, outline=EDGE, width=2)
    return img


def main():
    os.makedirs(ROOT, exist_ok=True)
    torso().save(os.path.join(ROOT, 'torso.png'))
    leg().save(os.path.join(ROOT, 'leg.png'))
    foot().save(os.path.join(ROOT, 'foot.png'))
    print('wrote', os.path.abspath(ROOT))


if __name__ == '__main__':
    main()
