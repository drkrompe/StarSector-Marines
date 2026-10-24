"""Geometry only for the flamer (special.flamer): a futuristic take on a WW2 backpack flamethrower.

Writes three guides under art-source/templates/ and one finished code-drawn texture. Code owns every
silhouette; ImageGen reskins material and detail. Sprites point UP (the actor's forward), empty space
is transparent.

  flamer-pack-guide.png   1120x800: the fuel pack as worn, seen from straight above on the back. The top
                          edge lies against the torso, so the harness yoke and its strap roots are there.
                          Two upright fuel tanks side by side show as round armoured caps with a filler
                          plug each; a smaller pressure bottle sits between and behind them; the hose
                          leaves a valve elbow on the right tank's flank (the wand is carried at the
                          right hip) and a pilot lamp sits on the yoke. Drawn at the carrier layer's
                          0.70 x 0.50 shoulder aspect.
  flamer-wand-guide.png   320x1240: the wand -- nozzle with its igniter block at the top, a long barrel
                          inside a perforated heat shroud, the fore grip, the valve and trigger housing,
                          the rear grip and the hose fitting at the bottom end. Drawn at the handset
                          layer's 0.16 x 0.62 shoulder aspect.
  flamer-icon-guide.png   1024x1024: the same set as an inventory icon, upright in profile: the twin tanks
                          and pressure bottle on their frame, the hose looping out to the wand lying
                          across the front, nozzle up to the right.
  flamer-hose.png         (in mod/graphics/battle/fx, written by this script directly) a ribbed armoured
                          hose laid along +y, stretched between the wand and the pack at draw time.
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
HAZARD = '#e0662a'   # hazard orange: the fuel's warning colour, reads against every armour family
STEEL = '#59606a'    # tank steel
DARK = '#1f2326'


def tank(d, cx, cy, r):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=STEEL)                          # tank wall seen end-on
    d.ellipse((cx - r * 0.86, cy - r * 0.86, cx + r * 0.86, cy + r * 0.86), fill=HAZARD)  # domed cap band
    d.ellipse((cx - r * 0.66, cy - r * 0.66, cx + r * 0.66, cy + r * 0.66), fill='#8a929b')  # cap dome
    d.ellipse((cx - r * 0.2, cy - r * 0.2, cx + r * 0.2, cy + r * 0.2), fill=DARK)   # filler plug
    for k in range(6):                                                               # armour ribs on the dome
        a = k * math.tau / 6 + math.pi / 6
        x0, y0 = cx + math.cos(a) * r * 0.28, cy + math.sin(a) * r * 0.28
        x1, y1 = cx + math.cos(a) * r * 0.62, cy + math.sin(a) * r * 0.62
        d.line((x0, y0, x1, y1), fill='#6c737c', width=int(r * 0.08))


def pack():
    W, H = 1120, 800
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((90, 30, 1030, 190), 50, fill='#3a4046')                     # harness yoke against the torso
    d.rectangle((230, 0, 340, 80), fill=DARK)                                        # strap roots
    d.rectangle((780, 0, 890, 80), fill=DARK)
    d.rounded_rectangle((520, 420, 600, 760), 30, fill='#3a4046')                    # frame spine behind
    d.ellipse((440, 470, 680, 710), fill='#8a929b')                                  # pressure bottle, set back
    d.ellipse((490, 520, 630, 660), fill='#b7bdc3')
    d.ellipse((535, 565, 585, 615), fill=DARK)                                       # its regulator
    tank(d, 290, 410, 240)
    tank(d, 830, 410, 240)
    d.rounded_rectangle((1010, 370, 1080, 450), 18, fill='#2c3136')                  # valve elbow on the right tank's flank
    d.ellipse((1040, 366, 1118, 454), fill=DARK)                                     # hose coupling, right side
    d.ellipse((540, 90, 580, 130), fill='#ffb347')                                   # pilot lamp on the yoke
    im.save(OUT / 'flamer-pack-guide.png')


def wand():
    W, H = 320, 1240
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((128, 60, 192, 780), 26, fill='#4a5058')                     # barrel
    d.rounded_rectangle((96, 170, 224, 600), 40, fill='#6c737c')                     # perforated heat shroud
    for y in range(200, 580, 42):
        d.ellipse((140, y, 180, y + 22), fill=DARK)                                  # shroud vents
    d.rounded_rectangle((104, 10, 216, 90), 30, fill='#3a4046')                      # nozzle
    d.ellipse((136, 18, 184, 66), fill='#1a1a1a')                                    # nozzle mouth
    d.rounded_rectangle((200, 60, 268, 150), 18, fill=HAZARD)                        # igniter block, right of nozzle
    d.ellipse((216, 80, 252, 116), fill='#ffb347')                                   # igniter glow
    d.rounded_rectangle((84, 620, 236, 740), 30, fill='#2c3136')                     # fore grip
    d.rounded_rectangle((70, 760, 250, 960), 40, fill=HAZARD)                        # valve and trigger housing
    d.rounded_rectangle((110, 790, 210, 930), 24, fill='#8a929b')                    # valve body
    d.rounded_rectangle((112, 960, 208, 1130), 30, fill='#2c3136')                   # rear grip
    d.rounded_rectangle((126, 1120, 194, 1232), 20, fill='#4a5058')                  # hose fitting
    im.save(OUT / 'flamer-wand-guide.png')


def hose():
    """An armoured hose, drawn finished: it is a line, so there is nothing for a reskin to improve."""
    W, H, S = 64, 512, 4
    im = Image.new('RGBA', (W * S, H * S))
    d = ImageDraw.Draw(im)
    x0, x1 = W * S * 0.16, W * S * 0.84
    d.rectangle((x0, 0, x1, H * S), fill='#24282c')                                  # rubber body
    d.rectangle((x0 + 10, 0, x0 + 34, H * S), fill='#3b4147')                        # rim light
    ribs = 40
    for i in range(ribs):                                                            # steel braid bands
        y = (i + 0.5) * H * S / ribs
        d.rectangle((x0 - 6, y - 10, x1 + 6, y + 10), fill='#5d646c')
        d.rectangle((x0 - 6, y - 10, x0 + 40, y - 2), fill='#9aa2aa')
    im = im.resize((W, H), Image.LANCZOS)
    out = ROOT / 'mod/graphics/battle/fx/flamer-hose.png'
    im.save(out)


def icon():
    S = 1024
    im = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((170, 170, 230, 900), 24, fill='#3a4046')                    # pack frame
    for x in (210, 440):                                                             # twin fuel tanks
        d.rounded_rectangle((x, 160, x + 200, 860), 96, fill=STEEL)
        d.rounded_rectangle((x + 10, 230, x + 190, 290), 20, fill=HAZARD)            # hazard bands
        d.rounded_rectangle((x + 10, 720, x + 190, 780), 20, fill=HAZARD)
        d.rounded_rectangle((x + 70, 120, x + 130, 170), 14, fill=DARK)              # filler plugs
    d.rounded_rectangle((380, 330, 470, 700), 44, fill='#8a929b')                    # pressure bottle between
    d.rounded_rectangle((300, 860, 600, 920), 24, fill='#2c3136')                    # manifold
    d.line([(520, 900), (540, 970), (640, 975), (660, 930)], fill='#24282c', width=44, joint='curve')  # hose
    # The wand, lying across the front with the nozzle up to the right.
    a = math.radians(-50)
    def p(t, s):
        bx, by = 760 + t * math.cos(a), 760 + t * math.sin(a)
        return bx - s * math.sin(a), by + s * math.cos(a)
    def bar(t0, t1, w, fill):
        d.polygon([p(t0, -w), p(t1, -w), p(t1, w), p(t0, w)], fill=fill)
    bar(-60, 400, 22, '#4a5058')                                                     # barrel
    bar(80, 300, 44, '#6c737c')                                                      # heat shroud
    bar(380, 430, 40, '#3a4046')                                                     # nozzle
    bar(-120, -30, 54, HAZARD)                                                       # valve housing
    bar(-200, -120, 34, '#2c3136')                                                   # rear grip
    d.polygon([p(-110, 40), p(-80, 40), p(-60, 150), p(-100, 150)], fill='#2c3136')  # pistol grip
    im.save(OUT / 'flamer-icon-guide.png')


if __name__ == '__main__':
    pack()
    wand()
    hose()
    icon()
    print('wrote flamer-{pack,wand,icon}-guide.png and flamer-hose.png')
