"""Geometry only for the Palisade interceptor pack (special.point-defence-pack).

Writes two guides under art-source/templates/. Code owns every silhouette; ImageGen reskins material
and detail. Sprites point UP (the actor's forward), empty space is transparent.

  pd-pack-pack-guide.png  800x880: the pack as worn, seen from straight above on the back, drawn at the
                          carrier layer's 0.42 x 0.46 shoulder aspect. The top edge lies against the
                          shoulders, so the strap roots are there. A frame plate carries a drum magazine
                          lying across its lower half, a feed chute running up the right side, and the
                          cluster gun on a short mast over the right shoulder, its two visible barrels
                          pointing forward past the shoulder line. One cyan sensor eye on the gun and a
                          cyan status strip down the left of the frame.
  pd-pack-icon-guide.png  1024x1024: the same pack standing upright as an inventory icon, seen from the
                          back: the frame with its shoulder-strap tops, the drum end-on as a round
                          magazine, the chute, and the gun on its folding arm above the right corner,
                          barrels angled up and out.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
CYAN = '#5fe3ff'     # the Palisade's interceptor accent, the same cyan as the pod and its tracer
FRAME = '#8a9399'
DARK = '#1d2220'


def pack():
    W, H = 800, 880
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((90, 120, 690, 840), 60, fill=FRAME)                        # frame plate
    d.rectangle((170, 120, 270, 200), fill=DARK)                                    # strap roots against the shoulders
    d.rectangle((420, 120, 520, 200), fill=DARK)
    d.rounded_rectangle((120, 240, 150, 420), 12, fill=CYAN)                        # status strip
    d.rounded_rectangle((150, 520, 620, 800), 60, fill='#6c757b')                   # drum magazine lying across
    for x in range(210, 600, 70):
        d.rectangle((x, 530, x + 14, 790), fill='#545c61')                          # drum ribs
    d.rounded_rectangle((560, 330, 650, 560), 24, fill='#5a6268')                   # feed chute up the right side
    d.ellipse((520, 180, 760, 420), fill='#3a4146')                                 # mast ring
    d.rounded_rectangle((545, 150, 735, 380), 50, fill='#9aa3a9')                   # gun housing
    for x in (575, 655):
        d.rounded_rectangle((x, 10, x + 50, 200), 18, fill='#4a5257')               # two barrels, forward
        d.ellipse((x + 8, 14, x + 42, 48), fill=DARK)                               # muzzle bores
    d.ellipse((610, 290, 670, 350), fill=CYAN)                                      # sensor eye
    im.save(OUT / 'pd-pack-pack-guide.png')


def icon():
    S = 1024
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((240, 330, 680, 960), 50, fill='#4b5a46')                   # frame
    d.rectangle((300, 300, 380, 400), fill='#2a2f2c')                               # strap tops
    d.rectangle((540, 300, 620, 400), fill='#2a2f2c')
    d.rounded_rectangle((270, 440, 300, 640), 12, fill=CYAN)                        # status strip
    d.ellipse((310, 560, 610, 860), fill='#3d4a3a')                                 # drum end-on
    d.ellipse((380, 630, 540, 790), fill='#2a2f2c')
    d.ellipse((440, 690, 480, 730), fill=CYAN)
    d.rounded_rectangle((600, 420, 680, 640), 22, fill='#3a4146')                   # feed chute
    d.polygon([(620, 430), (680, 400), (760, 300), (720, 270), (640, 360)], fill='#3a4146')   # folding arm
    d.rounded_rectangle((640, 150, 860, 330), 40, fill='#4b5a46')                   # gun housing
    for i, (x0, y0) in enumerate(((700, 40), (770, 60))):                           # barrels up and out
        d.polygon([(x0, y0), (x0 + 50, y0 - 10), (x0 + 90, y0 + 150), (x0 + 40, y0 + 160)], fill='#2f3530')
    d.ellipse((665, 245, 715, 295), fill=CYAN)                                      # sensor eye
    im.save(OUT / 'pd-pack-icon-guide.png')


if __name__ == '__main__':
    pack()
    icon()
    print('wrote pd-pack-{pack,icon}-guide.png')
