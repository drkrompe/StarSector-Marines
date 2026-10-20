"""Kilo Nine's ground crew from above: where each vehicle starts, the journey it drives, where it waits
and where it parks, the shipped keys against the K9_ORIGINS proposal (`c4x_scene.K9_LEGS`). A plan for review, drawn
from the scene's layout numbers; writes a 1920x1080 PNG (default build/c4x/k9-vehicle-plan.png).

    python yard_vehicle_plan.py [out.png]
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[4] / 'build' / 'c4x' / 'k9-vehicle-plan.png'
X0, X1, Y0, Y1 = 262.0, 500.0, 364.0, 560.0
K = 1060 / (Y1 - Y0)  # metres to pixels
W, H = 1920, 1080
img = Image.new('RGB', (W, H), (245, 244, 240))
d = ImageDraw.Draw(img)
def P(x, y):
    return (10 + (x - X0) * K, 10 + (Y1 - y) * K)
try:
    F = ImageFont.truetype('arial.ttf', 22); FB = ImageFont.truetype('arialbd.ttf', 26); FS = ImageFont.truetype('arial.ttf', 18)
except OSError:
    F = FB = FS = ImageFont.load_default()
kh = math.atan2(935, 620)
def at(u, v):
    return (400 + u * math.cos(kh) - v * math.sin(kh), 465 + u * math.sin(kh) + v * math.cos(kh))

GREY, DARK = (150, 150, 150), (70, 70, 70)
def rect(x0, x1, y0, y1, fill=None, outline=DARK, label=None, lw=2, off=(6, 4)):
    a, b = P(x0, y1), P(x1, y0)
    d.rectangle([a[0], a[1], b[0], b[1]], fill=fill, outline=outline, width=lw)
    if label: d.text((a[0] + off[0], a[1] + off[1]), label, fill=DARK, font=FS)
rect(290, 495, 384, 560, fill=(232, 224, 205), outline=(110, 110, 110), lw=3)            # the yard
rect(298, 312, 384, 560, fill=(200, 196, 186), outline=None)                             # haul road
rect(312, 352, 424, 436, fill=(200, 196, 186), outline=None)                             # spur
d.text(P(299, 470), 'haul road', fill=DARK, font=FS)
rect(262, 300, 366, 376, fill=(200, 196, 186), outline=None); d.text(P(264, 375), 'port road', fill=DARK, font=FS)
rect(262, 288, 376, 426, fill=(200, 196, 186), outline=None)
d.text(P(263, 425), 'arrivals', fill=DARK, font=FS)
for yy in (403, 412, 421):
    rect(266, 280, yy - 2, yy + 2, fill=None, outline=GREY)
d.text(P(263, 398), 'bus bays', fill=DARK, font=FS)
rect(296, 318, 375, 384, fill=(200, 196, 186), outline=None); d.text(P(296, 382), 'gate', fill=DARK, font=FS)
rect(320, 348, 391, 409, fill=(205, 205, 210), label='workshop')
rect(351, 363, 390, 400, fill=(205, 205, 210), label='garage', off=(2, 2))
rect(328, 340, 409.5, 416.5, fill=(240, 170, 110))
rect(366, 387, 395, 401, fill=None, outline=GREY, label='washing', off=(0, 0))
rect(313, 347, 519, 561, fill=(190, 140, 110), label='hangar')
rect(304, 336, 456, 489, fill=(215, 205, 185), outline=GREY, label='stores')
rect(467, 485, 418, 432, fill=(170, 200, 140), label='garden', off=(0, 0))
rect(411, 463, 509, 553, fill=None, outline=GREY, label='salvage')
for yy in (395, 406):
    a = P(451, yy + 4); b = P(459, yy - 4); d.ellipse([a[0], a[1], b[0], b[1]], fill=(225, 225, 225), outline=DARK, width=2)
d.text(P(452, 390), 'tanks', fill=DARK, font=FS)
for r, col, w in ((48, (120, 120, 120), 2), (50, (220, 180, 40), 4)):
    a = P(400 - r, 465 + r); b = P(400 + r, 465 - r); d.ellipse([a[0], a[1], b[0], b[1]], outline=col, width=w)
d.text(P(372, 523), 'Kilo Nine pad (ship sets down here)', fill=DARK, font=FS)
hull = [at(u, v) for u, v in ((39 - 5.9, 22), (-27 - 5.9, 22), (-27 - 5.9, -22), (39 - 5.9, -22))]
d.polygon([P(*p) for p in hull], fill=(215, 140, 90), outline=(120, 60, 20))
d.text(P(*at(-5, -6)), 'Second Wind', fill=(60, 20, 0), font=F)
for c in (at(0, 58), at(0, -58)):
    pts = [P(c[0] + u * math.cos(kh) - v * math.sin(kh), c[1] + u * math.sin(kh) + v * math.cos(kh)) for u, v in ((14, 3), (-14, 3), (-14, -3), (14, -3))]
    d.polygon(pts, outline=GREY, width=2)
d.text(P(*at(-6, 66)), 'scaffold', fill=DARK, font=FS); d.text(P(*at(-12, -50)), 'scaffold', fill=DARK, font=FS)
def path(pts, col, w=5, dash=False):
    q = [P(*p) for p in pts]
    if dash:
        for a, b in zip(q, q[1:]):
            n = max(1, int(math.dist(a, b) / 14))
            for k in range(0, n, 2):
                t0, t1 = k / n, min(1, (k + 1) / n)
                d.line([(a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0), (a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1)], fill=col, width=w)
    else:
        d.line(q, fill=col, width=w, joint='curve')
    a, b = q[-2], q[-1]
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    d.polygon([b, (b[0] - 18 * math.cos(ang - 0.45), b[1] - 18 * math.sin(ang - 0.45)), (b[0] - 18 * math.cos(ang + 0.45), b[1] - 18 * math.sin(ang + 0.45))], fill=col)
def box(x, y, L, Wd, h, col, label=None, off=(8, -14)):
    c, s = math.cos(h), math.sin(h)
    pts = [P(x + u * c - v * s, y + u * s + v * c) for u, v in ((L/2, Wd/2), (-L/2, Wd/2), (-L/2, -Wd/2), (L/2, -Wd/2))]
    d.polygon(pts, outline=col, width=3)
    if label:
        px, py = P(x, y); d.text((px + off[0], py + off[1]), label, fill=col, font=FS)
NEW, OLD, CUS, PARK = (20, 140, 60), (200, 40, 40), (30, 90, 200), (0, 0, 0)
ends = {'tug': at(-46, 22), 'carrier': at(-50, 4), 'bowser': at(0, -40)}
# shipped: start -> stand-off -> hull (straight lines, as the shipped C4 keys them)
path([(342, 528), (342, 507), ends['tug']], OLD, 4, True)
path([(322, 430), (336, 430), ends['carrier']], OLD, 4, True)
path([(470, 405), (462, 418), ends['bowser']], OLD, 4, True)
# round 2 (`c4x_scene.K9_LEGS`)
path([(273, 421), (285, 421), (286, 386), (288, 372), (300, 371), (305, 380), (305, 426), (314, 433.5),
      (330, 433.5), (352, 432.5), (362, 427.2), ends['carrier']], NEW, 6)
path([(328, 513), (300, 513), (300, 452), (300, 438), (318, 433.5), (338, 433.5), (345, 434), ends['tug']], (0, 110, 120), 6)
path([(434, 400), (434, 414), (433.6, 437), ends['bowser']], (120, 60, 160), 6)
path([(305, 386), (305, 424), (305, 430), (355, 428)], CUS, 4, True)
box(273, 421, 10, 3, 0.0, NEW, 'carrier: spare bus bay', (-10, 18))
box(328, 513, 8, 4, math.pi, (0, 110, 120), 'tug: hangar apron', (-40, -40))
box(434, 400, 12.5, 3, math.pi / 2, (120, 60, 160), 'bowser: fuel point', (14, 10))
d.rectangle([P(438.3, 402)[0], P(438.3, 402)[1], P(439.7, 401)[0], P(439.7, 401)[1]], fill=NEW)
for n, (x, y) in ends.items():
    px, py = P(x, y); d.ellipse([px - 9, py - 9, px + 9, py + 9], outline=PARK, width=3)
for (x, y), lab, col, off in (
        ((330, 433.5), 'A', NEW, (-6, -34)), ((300, 452), 'B', (0, 110, 120), (-34, -12)),
        ((338, 433.5), 'C', (0, 110, 120), (-6, 12)), ((434, 414), 'D', (120, 60, 160), (12, -12)),
        ((433.6, 437), 'E', (120, 60, 160), (12, -30))):
    px, py = P(x, y); d.ellipse([px - 7, py - 7, px + 7, py + 7], fill=col); d.text((px + off[0], py + off[1]), lab, fill=col, font=FB)
for (x, y), lab in (((342, 528), 'old tug start (in the hangar wall)'), ((470, 405), 'old bowser start'),
                    ((322, 430), 'old carrier start')):
    px, py = P(x, y); d.ellipse([px - 6, py - 6, px + 6, py + 6], fill=OLD); d.text((px + 10, py - 12), lab, fill=OLD, font=FS)
for (x, y) in ((341.4, 487.0), (346.4, 483.7), (356.9, 510.4), (361.9, 507.1), (438.1, 422.9), (443.1, 419.6), (453.6, 446.3), (458.6, 443.0)):
    px, py = P(x, y); d.rectangle([px - 5, py - 5, px + 5, py + 5], fill=(120, 60, 0))
for x in range(300, 500, 50):
    d.text(P(x, Y0 + 2), str(x), fill=(90, 90, 90), font=FS)
for y in range(400, 560, 50):
    d.text(P(X0 + 1, y), str(y), fill=(90, 90, 90), font=FS)
L = 10 + (X1 - X0) * K + 40
TEAL, PURP = (0, 110, 120), (120, 60, 160)
lines = [('Kilo Nine ground crew, round 2', FB, PARK), ('(K9_ORIGINS=1, proposal)', F, PARK), ('', FS, PARK),
         ('Crew carrier', FB, NEW),
         ('C4 0-15.5  out of the spare bus bay, the', F, NEW), ('  port road, in at the gate ahead of customs', F, NEW),
         ('A  holds on the spur through the dust', F, NEW), ('C4 20.6-C5 4.6  up beside the ramp', F, NEW),
         ('', FS, PARK), ('Tug', FB, TEAL),
         ('C4 3.0-19.0  off the hangar apron, down the haul road', F, TEAL),
         ('B  gives way to customs at the spur', F, TEAL),
         ('C4 21.2-C5 7.0  along the spur behind it', F, TEAL),
         ('C  waits while an officer walks across', F, TEAL),
         ('C5 11.4-14.6  runs past its spot', F, TEAL), ('C5 15.4-16.6  backs into it, squares up', F, TEAL),
         ('', FS, PARK), ('Fuel bowser', FB, PURP),
         ('C4 12.0-17.0  creeps off the fuel point', F, PURP), ('D  holding line during the touchdown', F, PURP),
         ('C4 18.6-C5 1.8  in towards the hull', F, PURP),
         ('E  stops short', F, PURP), ('C5 2.4-4.8  eases onto the hookup;', F, PURP),
         ('  a hand walks the hose to the hull', F, PURP), ('', FS, PARK),
         ('Customs (dashed blue): unchanged, parks C5 5.0', F, CUS),
         ('Shipped (dashed red): one cue, short hops', F, OLD),
         ('Circles: parked at the hull (unchanged)', F, PARK),
         ('C5 opens at C4 21.4 s. Grid: metres.', F, (90, 90, 90))]
y = 30
for t, f, c in lines:
    d.text((L, y), t, fill=c, font=f); y += 34 if f is FB else (14 if f is FS else 30)
out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out)
print('saved', out)
