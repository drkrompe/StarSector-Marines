"""The public port's roads from above: what is there now, a proposed network with a hierarchy, which
traffic uses which road, and which shots see the change. A plan for review (`port-roads.md`), drawn
from the scene's layout numbers over an occupancy map of everything standing; nothing here touches
the scene. Writes four 1920x1080 PNGs.

    python port_roads_plan.py OUT_DIR [--occ occ.npz]

`--occ` is an occupancy grid of the built scene (`stat`, `mov`, `lo`, `cell`: 1 m cells over x -720 to
560, y 330 to 790; what static geometry crosses 0.8, 2.0 or 3.5 m, and where moving vehicles sweep).
The 2026-10-20 plan read one taken off a saved C4 build. Without it the plan is drawn on the layout
rectangles alone.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

X0, X1, Y0, Y1 = -720.0, 560.0, 300.0, 790.0
K = 1.5                                   # pixels per metre
MAP_H = round((Y1 - Y0) * K)
W, H = 1920, 1080
try:
    F = ImageFont.truetype('arial.ttf', 19)
    FB = ImageFont.truetype('arialbd.ttf', 24)
    FS = ImageFont.truetype('arial.ttf', 15)
    FN = ImageFont.truetype('arialbd.ttf', 17)
except OSError:
    F = FB = FS = FN = ImageFont.load_default()


def P(x, y):
    return ((x - X0) * K, (Y1 - y) * K)


# ------------------------------------------------------------------ the layout (c4x_scene, port_detail)
ROW_H = math.radians(98.0)
BERTH_Y, BERTH_R = 530.0, 34.0
BERTH_DY = {-400.0: 12.0, -310.0: -8.0, -220.0: 0.0, -130.0: 15.0, -40.0: -10.0, 140.0: 0.0}
LIGHTERS = (-445.0, -355.0, -265.0, -175.0, -85.0, 5.0, 95.0)
LIGHTER_Y, LIGHTER_R = 412.0, 13.0
K9, K9_R = (400.0, 465.0), 48.0
HEAVY = (-60.0, 70.0, 615.0, 715.0)


def stern(x):
    """Where a freighter's cargo lane meets its stern."""
    cy = BERTH_Y + BERTH_DY[x]
    return x + 38.0 * -math.cos(ROW_H), cy - 38.0 * math.sin(ROW_H)


BUILDINGS = [('arrivals hall', (220, 260, 398, 432)), ('terminal shed', (180, 260, 645, 695))]
LOTS = [('west truck lot', (-700, -566, 404, 540)), ('spares yard', (-700, -596, 560, 636)),
        ('west hangars', (-700, -530, 650, 713)), ('depot', (-524, -440, 590, 680)),
        ('fuel farm', (-405, -285, 645, 705)), ('tanker rack', (-400, -290, 714, 754)),
        ('truck park', (-270, -76, 628, 718)), ('heavy berth', HEAVY),
        ('heavy staging', (95, 160, 596, 630)), ('container overflow', (80, 176, 700, 752)),
        ('terminal lorry park', (182, 284, 702, 752)), ('arrivals car park', (208, 268, 455, 526)),
        ('customs pound', (208, 268, 534, 610)), ('ferry side', (66, 184, 436, 484)),
        ('Kilo Nine', (290, 495, 384, 755)), ('container yard', (-600, 600, 300, 335))]
# Road surfaces as they are: (name, kind, rect)
CURRENT = [('port road', 'road', (-700, 322, 366, 378)),
           ('loading strip', 'yard', (-600, 560, 336, 358)),
           ('heavy berth road', 'road', (43, 57, 378, 615)),
           ('fuel farm road', 'bad', (-300, -285, 560, 645)),
           ('terminal road', 'bad', (57, 180, 680, 692)),
           ('K9 gate apron', 'k9', (296, 318, 366, 392)), ('K9 haul road', 'k9', (298, 312, 384, 747)),
           ('K9 spur', 'k9', (312, 352, 424, 436)), ('K9 works road', 'k9', (312, 400, 588, 600)),
           ('K9 turning ground', 'k9', (312, 400, 718, 750))]
for cx in (-300.0, 0.0, 300.0):
    CURRENT.append(('cross street %d' % cx, 'road', (cx - 7, cx + 7, 300, 360)))
for bx in (-400.0, -310.0, -220.0, -130.0, -40.0):
    sx, sy = stern(bx)
    CURRENT.append(('cargo lane %d' % bx, 'lane', (sx - 8, sx + 8, 342, sy)))
# Traffic loops as they run now (port_life.ROUTES)
LOOPS = {'belt-east-outer': [(303, 203), (303, 374), (-3, 374), (-3, 203)],
         'belt-east-inner': [(3, 209), (3, 368), (297, 368), (297, 209)],
         'port-west': [(-590, 354), (-590, 368), (-326, 368), (-326, 354)],
         'port-mid': [(-284, 354), (-284, 368), (-150, 368), (-150, 354)],
         'terminal': [(278, 445), (278, 622), (198, 622), (198, 445)],
         'heavy': [(46, 392), (46, 598), (54, 598), (54, 392)]}

# ------------------------------------------------------------------ the proposal
# Polylines with a width; kinds: main (haul road), dist (service roads), spur, ring (pad service).
PROPOSED = [
    ('port road', 'main', 12, [(-700, 372), (318, 372)]),
    ('heavy berth road', 'main', 14, [(50, 378), (50, 612)]),
    ('cross street -300', 'main', 14, [(-300, 300), (-300, 366)]),
    ('cross street 0', 'main', 14, [(0, 300), (0, 366)]),
    ('cross street 300', 'main', 14, [(300, 300), (300, 366)]),
    ('west connector', 'dist', 12, [(-545, 378), (-545, 650)]),
    ('back road', 'dist', 10, [(-545, 584), (-450, 584), (-436, 610), (-90, 610), (-90, 628)]),   # south of the depot
    ('arrivals link', 'dist', 8, [(285, 378), (285, 445)]),
    ('terminal road', 'dist', 8, [(278, 445), (278, 702)]),
    ('fuel spur', 'spur', 8, [(-422, 610), (-422, 734), (-400, 734)]),
    ('west lot spur', 'spur', 8, [(-545, 470), (-566, 470)]),
    ('spares spur', 'spur', 8, [(-545, 600), (-596, 600)]),
]
RING_R = 24.0                             # a lighter pad's service ring: 8 m outside its marker ring
REMOVED = [('fuel farm road', (-300, -285, 560, 645)), ('terminal road', (57, 180, 680, 692))]

# Problems with the roads as they are, numbered on the first plan.
PROBLEMS = [
    ((-292, 600), 'Fuel farm road: starts 4 m behind berth -310\'s pad, joins nothing; a tanker can only leave over the pad.'),
    ((118, 686), 'Terminal road: starts inside the heavy berth\'s walled deck, through its blast wall, ends on the shed wall.'),
    ((-170, 690), 'Truck park, lorry park, overflow, depot, fuel farm, tanker rack, spares, hangars: no road out at all.'),
    ((238, 470), 'Terminal apron (bus, baggage trains) is an island: the arrivals hall stands between it and the port road.'),
    ((150, 371), 'Port road is a racetrack: the belt loops run 300 m along it and turn back at Kilo Nine\'s gate.'),
    ((-440, 361), 'Port-west and port-mid loops do the same on the west half, crossing the berth lanes twice a lap.'),
    ((-620, 470), 'West truck lot and the west end reach the port road only across open apron.'),
]

# Flows on the proposed network: (label, colour, polylines)
FLOWS = [
    ('Freight haulers: freight road - cross streets - container yard; berth carriers stay on their lanes',
     (220, 120, 20), [[(-300, 300), (-300, 345), (-150, 345)], [(0, 300), (0, 345), (150, 345)],
                      [(-395, 470), (-395, 350)], [(-35, 470), (-35, 350)]]),
    ("Fuel: tanker rack - fuel spur - back road - west connector - port road - round pad 5's ring into berth -40's gap",
     (200, 30, 30), [[(-400, 734), (-422, 734), (-422, 612), (-450, 586), (-545, 586), (-545, 380),
                      (-26, 380), (-26, 440), (-1, 462), (-1, 500)]]),
    ('Crew carriers and buses: arrivals bays - arrivals link - port road - Kilo Nine gate; port bus hall-ferry',
     (30, 140, 60), [[(273, 421), (285, 421), (285, 382), (305, 382), (305, 392)],
                     [(240, 445), (198, 470), (198, 530)]]),
    ('Customs (MuniSec): the hall\'s post - arrivals link - port road - Kilo Nine gate, or west to the berths',
     (40, 90, 210), [[(250, 438), (282, 438), (282, 375), (300, 375), (308, 392)], [(282, 375), (-200, 375)]]),
    ('Lighter tugs and dollies: heavy staging - heavy road - port road - pad service rings',
     (130, 60, 170), [[(100, 605), (50, 600), (50, 382), (5, 382), (5, 388)], [(50, 382), (95, 382), (95, 388)]]),
    ('Trucks to the back lots: cross street - port road - west connector - back road - truck park',
     (110, 110, 110), [[(-300, 360), (-300, 376), (-543, 376), (-543, 608), (-90, 608), (-90, 628)]]),
]

# Shot footprints on the ground, (x0, x1, y0, y1), from the cameras' lenses and heights.
SHOTS = [('C4 linger, 13.4-21.4 s (and C5 0-4 s)', (-80, 880, 195, 785), (230, 140, 20)),
         ('C5 close view, 4-18 s', (283, 441, 391, 481), (200, 30, 30)),
         ('C5 top, 26-40 s', (-543, 843, 40, 820), (40, 90, 210))]


# ------------------------------------------------------------------ drawing

def base(occ):
    img = Image.new('RGB', (W, H), (250, 249, 246))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, MAP_H], fill=(244, 243, 239))
    if occ is not None:
        s, lo, cell = occ['stat'], occ['lo'], float(occ['cell'])
        ny, nx = s.shape
        ys, xs = np.nonzero(s)
        wx, wy = lo[0] + (xs + 0.5) * cell, lo[1] + (ys + 0.5) * cell
        keep = (wx > X0) & (wx < X1) & (wy > Y0) & (wy < Y1)
        px = ((wx[keep] - X0) * K).astype(int)
        py = ((Y1 - wy[keep]) * K).astype(int)
        a = np.array(img)
        for dx in (0, 1):
            for dy in (0, 1):
                a[np.clip(py + dy, 0, MAP_H - 1), np.clip(px + dx, 0, W - 1)] = (150, 150, 150)
        img = Image.fromarray(a)
        d = ImageDraw.Draw(img)
    for name, (x0, x1, y0, y1) in LOTS:
        a, b = P(x0, y1), P(x1, y0)
        d.rectangle([a[0], a[1], b[0], b[1]], outline=(185, 180, 165), width=1)
        d.text((a[0] + 3, a[1] + 2), name, fill=(130, 125, 110), font=FS)
    for name, (x0, x1, y0, y1) in BUILDINGS:
        a, b = P(x0, y1), P(x1, y0)
        d.rectangle([a[0], a[1], b[0], b[1]], fill=(205, 205, 210), outline=(110, 110, 120))
    hx0, hx1, hy0, hy1 = HEAVY
    for seg in (((hx0, hy0), (hx0, hy1)), ((hx0, hy1), (hx1, hy1)), ((hx1, hy1), (hx1, hy0))):
        d.line([P(*seg[0]), P(*seg[1])], fill=(90, 90, 90), width=4)          # blast walls
    pads = [(bx, BERTH_Y + dy, BERTH_R) for bx, dy in BERTH_DY.items()] + [(x, LIGHTER_Y, LIGHTER_R) for x in LIGHTERS]
    for cx, cy, r in pads + [(K9[0], K9[1], K9_R)]:
        keep = r + (10 if r > 20 else 8)
        a, b = P(cx - keep, cy + keep), P(cx + keep, cy - keep)
        d.ellipse([a[0], a[1], b[0], b[1]], fill=(250, 228, 225))             # blast keep-out
        a, b = P(cx - r, cy + r), P(cx + r, cy - r)
        d.ellipse([a[0], a[1], b[0], b[1]], fill=(232, 226, 205), outline=(170, 140, 60), width=2)
    for x in range(-700, 561, 100):
        d.text(P(x, Y0 + 9), str(x), fill=(120, 120, 120), font=FS)
    for y in range(400, 790, 100):
        d.text(P(X0 + 3, y), str(y), fill=(120, 120, 120), font=FS)
    d.line([0, MAP_H, W, MAP_H], fill=(120, 120, 120), width=2)
    return img, d


def rect(d, r, fill, outline=None, width=1):
    x0, x1, y0, y1 = r
    a, b = P(x0, y1), P(x1, y0)
    d.rectangle([a[0], a[1], b[0], b[1]], fill=fill, outline=outline, width=width)


def poly(d, pts, col, w, dash=False):
    q = [P(*p) for p in pts]
    if not dash:
        d.line(q, fill=col, width=max(1, round(w)), joint='curve')
        return
    for a, b in zip(q, q[1:]):
        n = max(1, int(math.dist(a, b) / 10))
        for k in range(0, n, 2):
            t0, t1 = k / n, min(1, (k + 1) / n)
            d.line([(a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0),
                    (a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1)], fill=col, width=max(1, round(w)))


def arrow(d, pts, col, w=4):
    poly(d, pts, col, w)
    q = [P(*p) for p in pts]
    a, b = q[-2], q[-1]
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    d.polygon([b, (b[0] - 14 * math.cos(ang - 0.45), b[1] - 14 * math.sin(ang - 0.45)),
               (b[0] - 14 * math.cos(ang + 0.45), b[1] - 14 * math.sin(ang + 0.45))], fill=col)


def legend(d, title, lines, top=MAP_H + 14):
    d.rectangle([0, MAP_H + 1, W, H], fill=(250, 249, 246))
    d.text((24, top), title, fill=(20, 20, 20), font=FB)
    y = top + 38
    col_x = 24
    for item in lines:
        swatch, text = item
        if swatch is not None:
            d.rectangle([col_x, y + 4, col_x + 26, y + 16], fill=swatch)
        d.text((col_x + 36, y), text, fill=(30, 30, 30), font=F)
        y += 27
        if y > H - 28:
            y, col_x = top + 38, col_x + 960


KIND = {'road': (95, 95, 100), 'yard': (175, 170, 160), 'lane': (190, 150, 70), 'bad': (210, 40, 40),
        'k9': (150, 120, 90)}
HIER = {'main': (200, 80, 20), 'dist': (30, 110, 190), 'spur': (120, 70, 170), 'ring': (40, 150, 80)}


def plan_current(occ, out):
    img, d = base(occ)
    for name, kind, r in CURRENT:
        rect(d, r, KIND[kind])
    for name, pts in LOOPS.items():
        poly(d, pts + [pts[0]], (60, 130, 220), 2, dash=True)
    for k, ((x, y), _) in enumerate(PROBLEMS, 1):
        px, py = P(x, y)
        d.ellipse([px - 13, py - 13, px + 13, py + 13], fill=(210, 40, 40))
        d.text((px - 5, py - 10), str(k), fill=(255, 255, 255), font=FN)
    legend(d, 'Port roads as they are (asphalt by kind; dashed blue: where port traffic loops)', [
        (KIND['road'], 'roads'), (KIND['lane'], 'berth cargo lanes'), (KIND['bad'], 'roads that make no sense (1, 2)'),
        (KIND['k9'], 'Kilo Nine (unchanged)'), ((250, 228, 225), 'blast keep-out round every pad')] +
        [(None, '%d. %s' % (k, t)) for k, (_, t) in enumerate(PROBLEMS, 1)])
    img.save(out)


def proposed_layers(d, label_roads=True):
    for name, r in REMOVED:
        rect(d, r, None, outline=(210, 40, 40), width=2)
        x0, x1, y0, y1 = r
        poly(d, [(x0, y0), (x1, y1)], (210, 40, 40), 2)
        poly(d, [(x0, y1), (x1, y0)], (210, 40, 40), 2)
    for x in LIGHTERS:
        a, b = P(x - RING_R, LIGHTER_Y + RING_R), P(x + RING_R, LIGHTER_Y - RING_R)
        d.ellipse([a[0], a[1], b[0], b[1]], outline=HIER['ring'], width=round(6 * K))
        poly(d, [(x - 8, 378), (x - 8, LIGHTER_Y - RING_R)], HIER['ring'], 6 * K)
        poly(d, [(x + 8, 378), (x + 8, LIGHTER_Y - RING_R)], HIER['ring'], 6 * K)
    for bx in (-400.0, -310.0, -220.0, -130.0, -40.0):
        sx, sy = stern(bx)
        rect(d, (sx - 8, sx + 8, 342, sy), KIND['lane'])
    for name, kind, w, pts in PROPOSED:
        poly(d, pts, HIER[kind], w * K)
    for name, r in (('K9', (296, 318, 366, 392)), ('K9', (298, 312, 384, 747)), ('K9', (312, 352, 424, 436))):
        rect(d, r, KIND['k9'])
    if label_roads:
        for text, (x, y) in (('port road (main haul road)', (-260, 381)), ('heavy berth road', (56, 520)),
                             ('west connector', (-540, 520)), ('back road', (-330, 615)),
                             ('arrivals link', (214, 392)), ('terminal road', (283, 680)),
                             ('fuel spur', (-417, 700)), ('pad service rings', (-480, 446))):
            d.text(P(x, y), text, fill=(20, 20, 20), font=FN)


def plan_proposed(occ, out):
    img, d = base(occ)
    proposed_layers(d)
    legend(d, 'Proposed network (a plan for the owner; nothing built)', [
        (HIER['main'], 'Main haul road: freight road - cross streets -300 / 0 / 300 - port road; heavy berth road north to the staging'),
        (HIER['dist'], 'Service roads: west connector + back road behind the berths; arrivals link + terminal road on the east'),
        (HIER['spur'], 'Spurs into lots: fuel farm / tanker rack, west truck lot, spares yard (others open off the roads directly)'),
        (HIER['ring'], 'Pad service rings: one-way, 8 m outside each lighter pad\'s marker ring, two links to the port road'),
        (KIND['lane'], 'Berth cargo lanes stay: stern to the container yard, service bays only'),
        ((210, 40, 40), 'Removed: the fuel farm stub behind berth -310 and the terminal road through the heavy berth wall'),
        (KIND['k9'], 'Kilo Nine\'s gate and haul road outside it stay where they are'),
        (None, 'Nothing crosses a pad or a keep-out ring, runs through a building or ends on a wall.')])
    img.save(out)


def plan_flows(occ, out):
    img, d = base(occ)
    for name, kind, w, pts in PROPOSED:
        poly(d, pts, (200, 200, 200), w * K)
    for x in LIGHTERS:
        a, b = P(x - RING_R, LIGHTER_Y + RING_R), P(x + RING_R, LIGHTER_Y - RING_R)
        d.ellipse([a[0], a[1], b[0], b[1]], outline=(200, 200, 200), width=round(6 * K))
    for label, col, lines in FLOWS:
        for pts in lines:
            arrow(d, pts, col, 5)
    legend(d, 'Who uses which road (proposed). Loops that only circled the port road are taken off it.',
           [(col, label) for label, col, _ in FLOWS] +
           [(None, 'Belt and port-west / port-mid loops turn on the loading strip instead of the port road.')])
    img.save(out)


def plan_shots(occ, out):
    img, d = base(occ)
    proposed_layers(d, label_roads=False)
    for label, (x0, x1, y0, y1), col in SHOTS:
        a, b = P(max(x0, X0), min(y1, Y1)), P(min(x1, X1), max(y0, Y0))
        d.rectangle([a[0], a[1], b[0], b[1]], outline=col, width=4)
        d.text((a[0] + 8, a[1] + 6), label, fill=col, font=FN)
    legend(d, 'What each shot sees of it', [
        (None, 'C3 (the window): forest only, no port. Unchanged.'),
        ((230, 140, 20), 'C4 pan, 0-13.4 s: the whole port, small (from 820 m down to 480 m). All of it shows.'),
        ((230, 140, 20), 'C4 linger 13.4-21.4 s and C5 0-4 s: heavy road, arrivals link, terminal road, pad rings 5 and 95, east port road.'),
        ((200, 30, 30), 'C5 4-18 s (close view on the ramp): only the arrivals link, along its left edge. Kilo Nine\'s ground unchanged.'),
        ((40, 90, 210), 'C5 rise 18-26 s and title to 40 s: everything east of about x -540 (the west hangars drop out).'),
        (None, 'Approved C4 and C5 finals would need re-rendering; the Colossus stays the only thing in the air.')])
    img.save(out)


def main():
    argv = sys.argv[1:]
    out = Path(argv[0])
    occ = np.load(argv[argv.index('--occ') + 1]) if '--occ' in argv else None
    out.mkdir(parents=True, exist_ok=True)
    plan_current(occ, out / '01-roads-now.png')
    plan_proposed(occ, out / '02-roads-proposed.png')
    plan_flows(occ, out / '03-who-uses-which-road.png')
    plan_shots(occ, out / '04-shots.png')
    print('saved', out)


if __name__ == '__main__':
    main()
