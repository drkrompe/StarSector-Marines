"""Geometry only for the Hegemony Arrestor interceptor pack (special.point-defence-pack).

Writes two guides under art-source/templates/. Code owns every silhouette; ImageGen reskins material
and detail. Sprites point UP (the actor's forward), empty space is transparent.

The pack is a miniature vertical launcher: its interceptors leave their tubes straight up, then light a
second motor and turn onto the incoming round. Seen from above that is a block of round tube hatches,
one per interceptor in the magazine (six), so the count on the marine's back is the count in the sim.
It is Hegemony issue: khaki plate, gunmetal launch block, amber ring lights; the cyan glow of the
Palisade gun pod belongs to the other kit.

  pd-pack-pack-guide.png  800x880: the pack as worn, seen from straight above on the back, drawn at the
                          carrier layer's 0.54 x 0.59 shoulder aspect. The top edge lies against the
                          shoulders, so the strap roots are there. A rigid frame carries the launch
                          block -- three by two round hatches, each ringed in amber -- with a small
                          sensor dome between the straps and exhaust vents down both sides.
  pd-pack-icon-guide.png  1024x1024: the same pack face as an inventory icon, stood upright and turned a
                          little so its top edge and side plate show: strap roots, sensor dome, hazard
                          bands, the three-by-two hatch block and side vents exactly as worn, with one
                          hatch lid swung open on an interceptor's red-banded nose.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
CYAN = '#ffb02e'     # Hegemony amber: hatch rings and status lights, the same amber as the interceptor's motor
FRAME = '#a39a7c'    # Hegemony khaki
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
    d.rounded_rectangle((150, 250, 650, 790), 50, fill='#5b6266')                   # launch block
    for cy in (390, 640):
        for cx in (250, 400, 550):
            hatch(d, cx, cy, 70)
    for y0 in (212, 800):                                                           # hazard bands across the frame
        for k, x in enumerate(range(110, 700, 40)):
            d.polygon([(x, y0), (x + 20, y0), (x + 40, y0 + 30), (x + 20, y0 + 30)],
                      fill='#e8b020' if k % 2 == 0 else '#1d1d1b')
    for x in (95, 675):                                                             # exhaust vents
        for y in range(300, 760, 70):
            d.rounded_rectangle((x, y, x + 30, y + 40), 8, fill='#3a4146')
    im.save(OUT / 'pd-pack-pack-guide.png')


def icon():
    """The icon is the worn pack's own face, stood upright and turned a little so its depth shows:
    the same strap roots, sensor dome, hazard bands, three-by-two hatch block and side vents as the
    sprite on the marine's back, one hatch lid swung open on an interceptor's red-banded nose."""
    S = 1024
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    ox, oy = 150, 100                                                               # face origin
    d.polygon([(ox + 600, oy + 70), (ox + 690, oy + 20), (ox + 690, oy + 760), (ox + 600, oy + 830)],
              fill='#6f6852')                                                       # side plate, in shade
    d.polygon([(ox + 70, oy + 20), (ox + 130, oy - 20), (ox + 690, oy - 20), (ox + 640, oy + 20)],
              fill='#b3aa8a')                                                       # top edge, lit
    d.rounded_rectangle((ox, oy, ox + 660, oy + 840), 70, fill=FRAME)               # frame face
    d.rectangle((ox + 80, oy - 30, ox + 180, oy + 90), fill=DARK)                   # strap roots
    d.rectangle((ox + 480, oy - 30, ox + 580, oy + 90), fill=DARK)
    d.ellipse((ox + 275, oy + 35, ox + 385, oy + 145), fill='#5a6268')              # sensor dome
    d.ellipse((ox + 310, oy + 70, ox + 350, oy + 110), fill=CYAN)
    d.rounded_rectangle((ox + 80, oy + 190, ox + 580, oy + 730), 50, fill='#5b6266')    # launch block
    for row, cy in enumerate((oy + 330, oy + 580)):
        for col, cx in enumerate((ox + 180, ox + 330, ox + 480)):
            if row == 0 and col == 1:
                hatch(d, cx, cy, 70, lid='#3a4146')                                 # open tube
                d.ellipse((cx - 34, cy - 34, cx + 34, cy + 34), fill='#c9d1d6')     # interceptor nose
                d.ellipse((cx - 14, cy - 14, cx + 14, cy + 14), fill='#c8402a')     # red band at the tip
                d.polygon([(cx - 60, cy - 70), (cx + 60, cy - 70), (cx + 50, cy - 150), (cx - 50, cy - 150)],
                          fill='#9aa3a9')                                           # lid swung up on its hinge
            else:
                hatch(d, cx, cy, 70)
    for y0 in (oy + 152, oy + 750):                                                 # hazard bands across the frame
        for k, x in enumerate(range(ox + 40, ox + 630, 40)):
            d.polygon([(x, y0), (x + 20, y0), (x + 40, y0 + 30), (x + 20, y0 + 30)],
                      fill='#e8b020' if k % 2 == 0 else '#1d1d1b')
    for x in (ox + 25, ox + 605):                                                   # exhaust vents
        for y in range(oy + 240, oy + 700, 70):
            d.rounded_rectangle((x, y, x + 30, y + 40), 8, fill='#3a4146')
    im.save(OUT / 'pd-pack-icon-guide.png')


if __name__ == '__main__':
    pack()
    icon()
    print('wrote pd-pack-{pack,icon}-guide.png')
