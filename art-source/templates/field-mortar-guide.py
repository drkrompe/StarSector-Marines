"""Geometry only for the field mortar: the crew-served infantry tube the Anvil section sets down.

Writes three 1024x1024 guides under art-source/templates/. Code owns every silhouette;
ImageGen reskins material and detail. Sprites point UP (the Starsector sprite convention),
pivot at the canvas centre, empty space is transparent.

  field-mortar-base-guide.png     static ground plate: square baseplate with a rim, ribs and two carry handles
  field-mortar-turret-guide.png   rotating bipod collar: the clamp ring and the two splayed legs (barrel not included)
  field-mortar-tube-guide.png     recoil layer: the smoothbore tube alone, breech cap below the pivot, muzzle up
  field-mortar-packed-guide.png   the carried kit lying diagonally: tube, folded bipod and baseplate in straps
                                   (armory icon and the carried layer on the gunner)

The three deployed layers share one frame and one pivot so they register without arithmetic.
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
S = 1024
C = S // 2


def base():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    # baseplate: a thick square plate with chamfered corners, a raised rim and a recessed socket
    r = 330
    d.rounded_rectangle((C - r, C - r, C + r, C + r), 60, fill='#454b4e')
    d.rounded_rectangle((C - r + 26, C - r + 26, C + r - 26, C + r - 26), 44, fill='#5b6266')
    d.rounded_rectangle((C - r + 70, C - r + 70, C + r - 70, C + r - 70), 30, fill='#3b4144')
    # radial ribs under the socket
    for ang in range(0, 360, 45):
        v = Image.new('RGBA', (S, S))
        vd = ImageDraw.Draw(v)
        vd.rectangle((C - 14, C - 270, C + 14, C - 120), fill='#2b3033')
        im.alpha_composite(v.rotate(ang, center=(C, C)))
    d.ellipse((C - 120, C - 120, C + 120, C + 120), fill='#23282a')   # ball socket
    d.ellipse((C - 80, C - 80, C + 80, C + 80), fill='#14181a')
    # two carry handles on the rear corners (rear = bottom, the tube points up)
    for sx in (-1, 1):
        x = C + sx * 300
        d.rounded_rectangle((x - 46, C + 250, x + 46, C + 410), 26, fill='#33393c')
        d.rounded_rectangle((x - 22, C + 280, x + 22, C + 380), 14, fill=(0, 0, 0, 0))
    # four spade teeth on the front edge
    for i in range(4):
        x = C - 210 + i * 140
        d.polygon([(x - 34, C - r), (x + 34, C - r), (x, C - r - 70)], fill='#2f3538')
    im.save(OUT / 'field-mortar-base-guide.png')


def turret():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    # two splayed bipod legs running back (down) and out, with spiked feet
    for sx in (-1, 1):
        d.polygon([(C + sx * 70, C + 30), (C + sx * 118, C + 30),
                   (C + sx * 330, C + 330), (C + sx * 282, C + 346)], fill='#575f63')
        d.ellipse((C + sx * 330 - 46, C + 330 - 30, C + sx * 330 + 46, C + 330 + 56), fill='#2b3033')
    # traverse bar joining the legs
    d.rounded_rectangle((C - 260, C + 190, C + 260, C + 236), 20, fill='#444b4f')
    # clamp collar around the tube
    d.ellipse((C - 150, C - 150, C + 150, C + 150), fill='#6a7277')
    d.ellipse((C - 108, C - 108, C + 108, C + 108), fill=(0, 0, 0, 0))
    # elevation screw
    d.rounded_rectangle((C + 120, C - 40, C + 250, C + 40), 16, fill='#4a5256')
    d.ellipse((C + 224, C - 34, C + 292, C + 34), fill='#2e3437')
    im.save(OUT / 'field-mortar-turret-guide.png')


def tube():
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    # smoothbore tube: muzzle at the top, breech cap just below the pivot
    d.rounded_rectangle((C - 62, C - 440, C + 62, C + 190), 24, fill='#323839')
    d.rounded_rectangle((C - 80, C - 450, C + 80, C - 380), 16, fill='#23282a')       # muzzle reinforce
    d.ellipse((C - 52, C - 436, C + 52, C - 392), fill='#0d1011')                      # bore
    for i in range(5):                                                                 # cooling rings
        y = C - 250 + i * 70
        d.rectangle((C - 74, y, C + 74, y + 18), fill='#40474a')
    d.ellipse((C - 96, C + 130, C + 96, C + 250), fill='#2a3033')                      # breech cap
    d.ellipse((C - 44, C + 160, C + 44, C + 224), fill='#575f63')
    im.save(OUT / 'field-mortar-tube-guide.png')


def packed():
    im = Image.new('RGBA', (S, S))
    kit = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(kit)
    # drawn upright, then turned: a long tube, a baseplate plate behind it, a folded bipod beside it
    d.rounded_rectangle((C - 270, C - 250, C + 130, C + 250), 40, fill='#454b4e')     # baseplate, edge on
    d.rounded_rectangle((C - 244, C - 224, C + 104, C + 224), 28, fill='#5b6266')
    d.rounded_rectangle((C + 40, C - 440, C + 160, C + 400), 24, fill='#323839')       # tube
    d.rectangle((C + 28, C - 452, C + 172, C - 380), fill='#23282a')
    for i in range(4):
        y = C - 250 + i * 150
        d.rectangle((C + 32, y, C + 168, y + 18), fill='#40474a')
    for sx in (190, 230):                                                              # folded bipod legs
        d.rounded_rectangle((C + sx, C - 300, C + sx + 38, C + 320), 14, fill='#575f63')
    for y in (-210, 60, 250):                                                          # webbing straps
        d.rectangle((C - 290, C + y, C + 270, C + y + 34), fill='#6b5a3a')
    kit = kit.rotate(-38, center=(C, C), resample=Image.BICUBIC)
    im.alpha_composite(kit)
    im.save(OUT / 'field-mortar-packed-guide.png')


if __name__ == '__main__':
    base()
    turret()
    tube()
    packed()
    print('wrote field-mortar-{base,turret,tube,packed}-guide.png')
