"""Geometry only for the forward-observer comms radio (special.comms-radio).

Writes three guides under art-source/templates/ and one finished code-drawn texture. Code owns every
silhouette; ImageGen reskins material and detail. Sprites point UP (the actor's forward), empty space
is transparent.

  comms-radio-pack-guide.png     800x840: the pack as worn, seen from straight above on the back. The
                                 top edge lies against the torso, so the strap roots are there; the
                                 buckled flap and lower pocket face the camera; the rear edge is the
                                 bottom. An empty handset cradle and a cord socket on the left side
                                 (the handset itself is a separate layer, raised only during a call)
                                 and one short whip antenna trailing back from the rear right corner.
                                 Drawn at the carrier layer's 0.40 x 0.42 shoulder aspect.
  comms-radio-handset-guide.png  320x800: the hand set -- a flared earpiece on top, a narrow grip with
                                 a press-to-talk bar, a flared mouthpiece below and the cord stub at
                                 the bottom end. Drawn at 0.16 x 0.40 shoulder aspect.
  comms-radio-icon-guide.png     1024x1024: the same set as an inventory icon -- the pack standing
                                 upright, its face with a dial, two knobs and a speaker grille, the
                                 handset hanging from a coiled cord and the antenna whip.
  comms-radio-cord.png           (in mod/graphics/battle/fx, written by this script directly) a coiled
                                 cord laid along +y, stretched between the two cord ports at draw time.
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
    W, H = 800, 840
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((70, 40, 730, 720), 70, fill='#3d4a3a')                       # pack body
    d.rectangle((150, 40, 250, 130), fill='#1d2220')                                  # strap roots against the torso
    d.rectangle((550, 40, 650, 130), fill='#1d2220')
    d.rounded_rectangle((110, 140, 690, 430), 40, fill='#4f5e49')                     # buckled top flap
    for x in (270, 530):
        d.rectangle((x - 24, 400, x + 24, 470), fill='#6b5a3a')                       # flap straps
        d.rounded_rectangle((x - 34, 450, x + 34, 494), 10, fill='#2a2f2c')           # buckles
    d.rounded_rectangle((130, 510, 670, 690), 36, fill='#46543f')                     # lower pocket
    d.rounded_rectangle((10, 190, 80, 430), 26, fill='#2f3530')                       # empty handset cradle on the left
    d.ellipse((26, 280, 86, 340), fill='#1d2220')                                     # cord socket
    d.ellipse((560, 640, 660, 740), fill='#23282a')                                   # antenna socket
    whip(d, (620, 700), (760, 824), 22, 9)
    im.save(OUT / 'comms-radio-pack-guide.png')


def handset():
    W, H = 320, 800
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((120, 190, 200, 590), 30, fill='#2f3530')                     # grip
    d.rounded_rectangle((30, 20, 290, 250), 80, fill='#2f3530')                       # earpiece cup
    d.rounded_rectangle((40, 540, 280, 730), 70, fill='#2f3530')                      # mouthpiece
    d.rounded_rectangle((100, 330, 130, 470), 12, fill='#c9962a')                     # press-to-talk bar
    d.rounded_rectangle((140, 725, 180, 790), 16, fill='#1d2220')                     # cord stub
    im.save(OUT / 'comms-radio-handset-guide.png')


def cord():
    """A coiled cord, drawn finished: it is a line, so there is nothing for a reskin to improve."""
    W, H, S = 64, 512, 4
    im = Image.new('RGBA', (W * S, H * S))
    d = ImageDraw.Draw(im)
    coils = 24
    pts = []
    for i in range(H * S):
        t = i / (H * S - 1)
        pts.append((W * S / 2 + math.sin(t * coils * math.tau) * W * S * 0.26, i))
    d.line(pts, fill='#0d0f0e', width=int(W * S * 0.30), joint='curve')
    d.line(pts, fill='#1f2522', width=int(W * S * 0.18), joint='curve')
    d.line([(x - 4, y) for x, y in pts], fill='#3b4640', width=int(W * S * 0.05), joint='curve')
    im = im.resize((W, H), Image.LANCZOS)
    out = ROOT / 'mod/graphics/battle/fx/comms-radio-cord.png'
    im.save(out)


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
    handset()
    cord()
    icon()
    print('wrote comms-radio-{pack,handset,icon}-guide.png and comms-radio-cord.png')
