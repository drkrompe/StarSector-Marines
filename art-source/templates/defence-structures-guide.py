"""Geometry only for the fortress battery and the planetary shield generator.

Writes five 1024x1024 guides under art-source/templates/. Code owns every
silhouette; ImageGen reskins material and detail. Sprites point UP (the
Starsector sprite convention), pivot at the canvas centre.

  fortress-battery-base-guide.png     static 3x3 footprint plinth, clipped corners
  fortress-battery-turret-guide.png   rotating gun house + three barrels (split by the exporter)
  shield-generator-base-guide.png     static 3x3 footprint plinth, recessed emitter well
  shield-generator-ring-guide.png     spinning emitter ring, six coil blocks, open centre
  shield-generator-core-guide.png     pulsing core housing and orb, tinted at runtime
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
S = 1024
C = S // 2


def octagon(inset, cut):
    a, b = inset, S - inset
    return [(a + cut, a), (b - cut, a), (b, a + cut), (b, b - cut),
            (b - cut, b), (a + cut, b), (a, b - cut), (a, a + cut)]


def battery_base():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.polygon(octagon(8, 120), fill='#464b4e')
    d.polygon(octagon(52, 100), fill='#5a6063')
    # armour glacis ring around the turret well
    d.ellipse((C - 350, C - 350, C + 350, C + 350), fill='#3d4245')
    d.ellipse((C - 310, C - 310, C + 310, C + 310), fill='#1c1f21')
    d.ellipse((C - 250, C - 250, C + 250, C + 250), fill='#2a2e30')
    # four blast hatches in the corners
    for sx in (-1, 1):
        for sy in (-1, 1):
            x, y = C + sx * 335, C + sy * 335
            d.rounded_rectangle((x - 70, y - 70, x + 70, y + 70), 14, fill='#33383a')
            d.rectangle((x - 46, y - 46, x + 46, y + 46), fill='#4b5154')
    # hazard bands and vent banks on the four mid-edges
    for ang in (0, 90, 180, 270):
        v = Image.new('RGBA', (S, S))
        vd = ImageDraw.Draw(v)
        vd.rectangle((C - 150, 70, C + 150, 96), fill='#b8892f')
        for i in range(5):
            vd.rectangle((C - 120 + i * 50, 122, C - 96 + i * 50, 190), fill='#222628')
        im.alpha_composite(v.rotate(ang))
    im.save(OUT / 'fortress-battery-base-guide.png')


def battery_turret():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    # three barrels, drawn first so the house overlaps their roots
    d.rectangle((C - 38, 20, C + 38, C), fill='#2f3436')              # centre heavy gun
    d.rectangle((C - 58, 20, C + 58, 66), fill='#23282a')             # its muzzle brake
    for i in range(6):
        d.rectangle((C - 50, 96 + i * 36, C + 50, 108 + i * 36), fill='#3d4346')  # coil rings
    for sx in (-1, 1):
        x = C + sx * 150
        d.rectangle((x - 26, 110, x + 26, C), fill='#2f3436')         # flank guns
        d.rectangle((x - 38, 110, x + 38, 148), fill='#23282a')
    house = [(C - 150, 330), (C + 150, 330), (C + 225, 400), (C + 225, 640), (C + 160, 720),
             (C - 160, 720), (C - 225, 640), (C - 225, 400)]
    d.polygon(house, fill='#6b7378')
    d.polygon([(C - 115, 372), (C + 115, 372), (C + 180, 430), (C + 180, 620), (C + 120, 680),
               (C - 120, 680), (C - 180, 620), (C - 180, 430)], fill='#596064')
    d.ellipse((C - 70, C - 50, C + 70, C + 90), fill='#44494c')       # cupola
    d.rectangle((C - 100, 676, C + 100, 704), fill='#3a3f41')         # rear vent
    im.save(OUT / 'fortress-battery-turret-guide.png')


def generator_base():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.polygon(octagon(8, 150), fill='#3f4649')
    d.polygon(octagon(56, 125), fill='#566066')
    # recessed emitter well in the middle
    d.ellipse((C - 330, C - 330, C + 330, C + 330), fill='#2c3438')
    d.ellipse((C - 290, C - 290, C + 290, C + 290), fill='#161c1f')
    # four capacitor pylons on the diagonals
    for sx in (-1, 1):
        for sy in (-1, 1):
            x, y = C + sx * 360, C + sy * 360
            d.ellipse((x - 78, y - 78, x + 78, y + 78), fill='#2e3538')
            d.ellipse((x - 52, y - 52, x + 52, y + 52), fill='#6c7a80')
            d.ellipse((x - 20, y - 20, x + 20, y + 20), fill='#8fd4e4')
    # conduit runs from each pylon toward the well
    for ang in (0, 90, 180, 270):
        v = Image.new('RGBA', (S, S))
        vd = ImageDraw.Draw(v)
        vd.rectangle((C - 24, 86, C + 24, 262), fill='#252b2e')
        vd.rectangle((C - 8, 86, C + 8, 262), fill='#79c6d8')
        im.alpha_composite(v.rotate(ang))
    im.save(OUT / 'shield-generator-base-guide.png')


def generator_ring():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.ellipse((C - 470, C - 470, C + 470, C + 470), fill='#4c555a')
    d.ellipse((C - 300, C - 300, C + 300, C + 300), fill=(0, 0, 0, 0))
    # transparent centre: punch properly
    hole = Image.new('L', (S, S), 255)
    ImageDraw.Draw(hole).ellipse((C - 300, C - 300, C + 300, C + 300), fill=0)
    r, g, b, a = im.split()
    from PIL import ImageChops
    a = ImageChops.multiply(a, hole)
    im = Image.merge('RGBA', (r, g, b, a))
    d = ImageDraw.Draw(im)
    for i in range(6):
        block = Image.new('RGBA', (S, S))
        bd = ImageDraw.Draw(block)
        bd.rounded_rectangle((C - 95, 40, C + 95, 250), 26, fill='#6a757b')
        bd.rectangle((C - 40, 70, C + 40, 220), fill='#8fd4e4')
        im.alpha_composite(block.rotate(-60 * i, center=(C, C)))
    im.save(OUT / 'shield-generator-ring-guide.png')


def generator_core():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.ellipse((C - 470, C - 470, C + 470, C + 470), fill='#3b4448')
    d.ellipse((C - 400, C - 400, C + 400, C + 400), fill='#566066')
    d.ellipse((C - 330, C - 330, C + 330, C + 330), fill='#1b2225')
    d.ellipse((C - 280, C - 280, C + 280, C + 280), fill='#9fe4f2')
    d.ellipse((C - 150, C - 150, C + 150, C + 150), fill='#f4fdff')
    for i in range(4):
        spoke = Image.new('RGBA', (S, S))
        ImageDraw.Draw(spoke).rectangle((C - 24, 36, C + 24, 130), fill='#2e373b')
        im.alpha_composite(spoke.rotate(90 * i))
    im.save(OUT / 'shield-generator-core-guide.png')


if __name__ == '__main__':
    battery_base()
    battery_turret()
    generator_base()
    generator_ring()
    generator_core()
