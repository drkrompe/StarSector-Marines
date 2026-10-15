"""Geometry only for the Palisade interceptor pack (special.point-defence-pack).

Writes two guides under art-source/templates/. Code owns every silhouette; ImageGen reskins material
and detail. Sprites point UP (the actor's forward), empty space is transparent.

The pack is a miniature vertical launcher: its interceptors leave their tubes straight up, then light a
second motor and turn onto the incoming round. Seen from above that is a block of round tube hatches,
one per interceptor in the magazine (six), so the count on the marine's back is the count in the sim.

  pd-pack-pack-guide.png  800x880: the pack as worn, seen from straight above on the back, drawn at the
                          carrier layer's 0.54 x 0.59 shoulder aspect. The top edge lies against the
                          shoulders, so the strap roots are there. A rigid frame carries the launch
                          block -- three by two round hatches, each ringed in cyan -- with a small
                          sensor dome between the straps and exhaust vents down both sides.
  pd-pack-icon-guide.png  1024x1024: the same pack standing upright as an inventory icon, seen from the
                          back and a little above: the frame with its strap tops, the launch block's
                          six hatches on its top face, one hatch open with an interceptor's nose
                          standing out of it, and the sensor dome on a short mast.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
CYAN = '#5fe3ff'     # the Palisade's interceptor accent, the same cyan as the pod and its tracer
FRAME = '#8a9399'
DARK = '#1d2220'


def hatch(d, cx, cy, r, lid='#9aa3a9'):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=CYAN)                          # cyan rim
    d.ellipse((cx - r + 9, cy - r + 9, cx + r - 9, cy + r - 9), fill='#3a4146')     # tube mouth
    d.ellipse((cx - r + 18, cy - r + 18, cx + r - 18, cy + r - 18), fill=lid)       # hatch lid


def pack():
    W, H = 800, 880
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((70, 60, 730, 850), 70, fill=FRAME)                         # frame
    d.rectangle((150, 60, 250, 150), fill=DARK)                                     # strap roots against the shoulders
    d.rectangle((550, 60, 650, 150), fill=DARK)
    d.ellipse((345, 95, 455, 205), fill='#5a6268')                                  # sensor dome
    d.ellipse((380, 130, 420, 170), fill=CYAN)
    d.rounded_rectangle((150, 250, 650, 790), 50, fill='#6c757b')                   # launch block
    for cy in (390, 640):
        for cx in (250, 400, 550):
            hatch(d, cx, cy, 70)
    for x in (95, 675):                                                             # exhaust vents
        for y in range(300, 760, 70):
            d.rounded_rectangle((x, y, x + 30, y + 40), 8, fill='#3a4146')
    im.save(OUT / 'pd-pack-pack-guide.png')


def icon():
    S = 1024
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((250, 330, 770, 960), 50, fill='#4b5a46')                   # frame face
    d.rectangle((320, 300, 410, 400), fill='#2a2f2c')                               # strap tops
    d.rectangle((610, 300, 700, 400), fill='#2a2f2c')
    d.polygon([(250, 330), (330, 200), (850, 200), (770, 330)], fill='#5c6b56')     # launch block top face
    for i, cx in enumerate((380, 520, 660)):                                        # back row of hatches
        d.ellipse((cx - 45, 215, cx + 75, 255), fill=CYAN)
        d.ellipse((cx - 35, 220, cx + 65, 250), fill='#3a4146')
    for i, cx in enumerate((330, 470, 610)):                                        # front row of hatches
        d.ellipse((cx - 50, 270, cx + 80, 315), fill=CYAN)
        d.ellipse((cx - 40, 276, cx + 70, 309), fill='#3a4146')
    d.polygon([(495, 290), (525, 120), (555, 290)], fill='#c9d1d6')                 # one interceptor nose out of its tube
    d.polygon([(510, 160), (525, 120), (540, 160)], fill=CYAN)
    d.rectangle((790, 120, 810, 210), fill='#3a4146')                               # sensor mast
    d.ellipse((760, 70, 840, 150), fill='#5a6268')                                  # sensor dome
    d.ellipse((785, 95, 815, 125), fill=CYAN)
    for y in range(440, 900, 80):                                                   # exhaust vents down the side
        d.rounded_rectangle((270, y, 300, y + 50), 8, fill='#2a2f2c')
        d.rounded_rectangle((720, y, 750, y + 50), 8, fill='#2a2f2c')
    d.rounded_rectangle((400, 560, 620, 840), 30, fill='#3d4a3a')                   # magazine access panel
    d.rounded_rectangle((430, 590, 460, 810), 10, fill=CYAN)                        # status strip
    im.save(OUT / 'pd-pack-icon-guide.png')


if __name__ == '__main__':
    pack()
    icon()
    print('wrote pd-pack-{pack,icon}-guide.png')
