"""Geometry guides for the gun plate: the armoured plate that turns with a fitted weapon on a fixed hull's mount ring.

The runtime draws the plate centred on the mount ring, gun slot to the top (north), at VehicleGun.plateCells, and the
weapon's own drawing over it pivoting on the plate's centre (VehicleGun.RING_FROM_REAR: 40% of the gun behind the
centre, 60% ahead). The guides keep the gun's lane, a strip down the middle, clear and flat, so whatever the generation
puts there is hidden under the gun rather than fighting it.

  guide-shield.png  candidate A: a flat turntable with a curved gunner's shield across the front, slot at the top
  guide-cupola.png  candidate B: a low bevelled armoured cupola with a squared mantlet block at the front

Run from anywhere: python art-source/vehicles/gun-plate/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
SIZE = 1024
C = SIZE / 2
R = SIZE * .46            # the plate's radius
LANE = SIZE * .07         # half-width of the gun's lane down the middle


def disc(draw, radius, fill, outline=None, width=0, cx=C, cy=C):
    draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=fill, outline=outline, width=width)


def bolts(draw, radius, count, start_deg=0, size=9):
    import math
    for i in range(count):
        a = math.radians(start_deg + 360 * i / count)
        x, y = C + radius * math.sin(a), C - radius * math.cos(a)
        disc(draw, size, (60, 60, 60, 255), cx=x, cy=y)


def shield() -> Image.Image:
    im = Image.new("RGBA", (SIZE, SIZE))
    d = ImageDraw.Draw(im)
    disc(d, R, (128, 128, 128, 255), (50, 50, 50, 255), 10)        # the turntable deck
    disc(d, R * .9, None, (90, 90, 90, 255), 5)                     # inner seam
    bolts(d, R * .95, 24, 7.5)
    # The gunner's shield: a thick curved plate standing across the front 140 degrees.
    d.pieslice((C - R, C - R, C + R, C + R), 200, 340, fill=(190, 190, 190, 255), outline=(40, 40, 40, 255), width=8)
    disc(d, R * .62, (128, 128, 128, 255))                          # cut the shield into an arc band
    d.arc((C - R * .62, C - R * .62, C + R * .62, C + R * .62), 200, 340, fill=(40, 40, 40, 255), width=8)
    # The slot the barrel passes through, at the top.
    d.rectangle((C - LANE, C - R - 4, C + LANE, C - R * .55), fill=(20, 20, 20, 255))
    # The gun's lane: a flat cradle rail down the middle, kept plain.
    d.rectangle((C - LANE, C - R * .55, C + LANE, C + R * .8), fill=(105, 105, 105, 255), outline=(60, 60, 60, 255), width=4)
    # The belt box on the left of the gun, a hatch plate on the right.
    d.rectangle((C - R * .55, C - R * .1, C - LANE - 24, C + R * .35), fill=(150, 150, 150, 255), outline=(45, 45, 45, 255), width=6)
    disc(d, R * .2, (115, 115, 115, 255), (45, 45, 45, 255), 6, cx=C + R * .4, cy=C + R * .4)
    return im


def cupola() -> Image.Image:
    im = Image.new("RGBA", (SIZE, SIZE))
    d = ImageDraw.Draw(im)
    disc(d, R, (95, 95, 95, 255), (40, 40, 40, 255), 10)            # outer bevel
    disc(d, R * .8, (150, 150, 150, 255), (55, 55, 55, 255), 6)     # the raised armoured top
    bolts(d, R * .9, 20, 9)
    # The squared mantlet block at the front the gun passes through.
    d.rectangle((C - R * .3, C - R * .98, C + R * .3, C - R * .5), fill=(175, 175, 175, 255), outline=(40, 40, 40, 255), width=8)
    d.rectangle((C - LANE, C - R - 4, C + LANE, C - R * .5), fill=(20, 20, 20, 255))
    d.rectangle((C - LANE, C - R * .5, C + LANE, C + R * .8), fill=(125, 125, 125, 255), outline=(60, 60, 60, 255), width=4)
    # Two vision blocks on the front shoulders, a round hatch rear right.
    for side in (-1, 1):
        x = C + side * R * .5
        d.rectangle((x - 30, C - R * .5 - 22, x + 30, C - R * .5 + 22), fill=(40, 40, 40, 255))
    disc(d, R * .24, (135, 135, 135, 255), (45, 45, 45, 255), 6, cx=C + R * .42, cy=C + R * .38)
    return im


if __name__ == "__main__":
    shield().save(HERE / "guide-shield.png")
    cupola().save(HERE / "guide-cupola.png")
    print("wrote guides in", HERE)
