"""Geometry only for the forward-observer comms radio (special.comms-radio).

Writes two guides under art-source/templates/. Code owns every silhouette; ImageGen reskins material
and detail. Sprites point UP, empty space is transparent.

  comms-radio-pack-guide.png   768x1000: the set as worn, seen from above on the back -- a squat
                               rectangular pack, a buckled top flap, a coiled-cord handset clipped
                               to its side and one whip antenna rising from the upper right corner.
                               Drawn at the carrier layer's 0.40 x 0.52 shoulder aspect.
  comms-radio-icon-guide.png   1024x1024: the same set as an inventory icon -- the pack standing
                               upright, its face with a dial, two knobs and a speaker grille, the
                               handset hanging from a coiled cord and the antenna whip.
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'


def whip(d, base, tip, w0, w1):
    dx, dy = tip[0] - base[0], tip[1] - base[1]
    n = math.hypot(dx, dy)
    px, py = -dy / n, dx / n
    d.polygon([(base[0] + px * w0, base[1] + py * w0), (base[0] - px * w0, base[1] - py * w0),
               (tip[0] - px * w1, tip[1] - py * w1), (tip[0] + px * w1, tip[1] + py * w1)], fill='#2a2f2c')
    d.ellipse((tip[0] - w1 * 2, tip[1] - w1 * 2, tip[0] + w1 * 2, tip[1] + w1 * 2), fill='#c9962a')


def pack():
    W, H = 768, 1000
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((100, 300, 580, 970), 60, fill='#3d4a3a')                     # pack body
    d.rounded_rectangle((130, 330, 550, 520), 36, fill='#4f5e49')                     # top flap
    for x in (230, 450):
        d.rectangle((x - 22, 500, x + 22, 560), fill='#6b5a3a')                       # flap straps
        d.rounded_rectangle((x - 30, 540, x + 30, 580), 10, fill='#2a2f2c')           # buckles
    d.rounded_rectangle((150, 620, 530, 930), 40, fill='#46543f')                     # lower pocket
    d.rounded_rectangle((590, 520, 660, 840), 30, fill='#2f3530')                     # handset clipped on the side
    d.ellipse((600, 540, 650, 600), fill='#1d2220')
    d.rectangle((100, 800, 160, 940), fill='#1d2220')                                 # strap roots
    d.rectangle((520, 800, 580, 940), fill='#1d2220')
    d.ellipse((440, 270, 540, 370), fill='#23282a')                                   # antenna socket
    whip(d, (490, 320), (690, 40), 26, 11)
    im.save(OUT / 'comms-radio-pack-guide.png')


def icon():
    S = 1024
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((220, 330, 700, 930), 54, fill='#3d4a3a')                    # case
    d.rounded_rectangle((250, 360, 670, 520), 30, fill='#2a2f2c')                    # display bezel
    d.rounded_rectangle((276, 386, 470, 494), 14, fill='#8fa07a')                    # display
    d.rectangle((500, 392, 640, 420), fill='#c9962a')
    d.rectangle((500, 440, 640, 468), fill='#8fa07a')
    for cx in (330, 460):                                                            # knobs
        d.ellipse((cx - 56, 590, cx + 56, 702), fill='#23282a')
        d.ellipse((cx - 34, 612, cx + 34, 680), fill='#5a6358')
    for i in range(5):                                                               # speaker grille
        d.rounded_rectangle((250, 760 + i * 30, 670, 774 + i * 30), 7, fill='#1d2220')
    d.rounded_rectangle((570, 570, 650, 710), 22, fill='#6b5a3a')                    # carry strap tab
    d.ellipse((560, 292, 660, 372), fill='#23282a')                                  # antenna socket
    whip(d, (610, 330), (840, 40), 16, 6)
    d.rounded_rectangle((740, 560, 830, 900), 36, fill='#2f3530')                    # handset on its cord
    d.rounded_rectangle((756, 590, 814, 640), 16, fill='#1d2220')
    d.rounded_rectangle((756, 800, 814, 860), 16, fill='#1d2220')
    for i in range(6):                                                               # coiled cord
        y = 700 + i * 28
        d.arc((690, y - 70, 760, y + 10), 20, 340, fill='#1d2220', width=10)
    im.save(OUT / 'comms-radio-icon-guide.png')


if __name__ == '__main__':
    pack()
    icon()
    print('wrote comms-radio-{pack,icon}-guide.png')
