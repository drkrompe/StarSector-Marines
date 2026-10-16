"""Broadsword engines: two whole drive nacelles, each ending in a single
aft-facing bell, the way the sprite paints two engine mounds at the tail.

broadsword.ship: two LOW_TECH slots at (-11, 6) and (-11, -5), width 12,
angle 180. As with the guns, the integer slots straddle a centre line
between pixel columns, so the nacelles are symmetric at Y = +-5.5. The
sprite fills Y 2..8 for X -12..-8 on each side; each nacelle is the whole
mound, not a nozzle in the hull.

Names follow the Second Wind: Engine {i} aft-facing bell / mouth lip /
dark throat, plus Engine {i} {side} exhaust anchor (+X points aft).
"""
import math
import bw_kit as K
from bw_kit import section_loft, strip, corner_s

ENGINES = [(-11, 6, 12), (-11, -5, 12)]  # exactly as in broadsword.ship (x, y, width)
CY = 5.5
NACELLE = [  # (x, half width, keel z, deck z, top chamfer, keel chamfer)
    (-.6, 2.4, -2.3, 1.0, .9, .9),
    (-3.0, 3.0, -3.2, 1.8, 1.25, 1.25),
    (-9.8, 3.0, -3.2, 1.8, 1.25, 1.25),
    (-10.6, 2.95, -2.95, 1.55, 1.15, 1.1),
]
AXIS_Z = -.7
FACE_X = -10.6
LIP_R, BELL_R = 2.2, 1.8
LIP_X0, LIP_X1 = -10.4, -11.9
ANCHOR_X = LIP_X1


def bell(kit, name, x0, x1, r0, r1, cy, z, mat, n=24):
    """Open-ended cone shell along -X from x0 (radius r0) to x1 (radius r1), wall .12."""
    verts, faces = [], []
    for x, r in ((x0, r0), (x1, r1), (x1, r1+.12), (x0, r0+.12)):
        verts += [(x, cy+r*math.cos(2*math.pi*k/n), z+r*math.sin(2*math.pi*k/n)) for k in range(n)]
    for ring in range(4):
        a, b = ring*n, ((ring+1) % 4)*n
        faces += [(a+k, a+(k+1) % n, b+(k+1) % n, b+k) for k in range(n)]
    return kit.mesh(name, verts, faces, mat, 0)


def build(kit):
    for i, (ex, ey, w) in enumerate(ENGINES, 1):
        s = 1 if ey > 0 else -1
        n = 'port' if s > 0 else 'starboard'
        cy = s*CY
        nac = section_loft(kit, f'Engine {i} {n} nacelle', NACELLE, kit.steel, cy=cy, bevel=.08)
        kit.hull.append(nac)
        K.boolean(nac, kit.cylinder('Temporary nacelle bell well', (-10.6, cy, AXIS_Z), BELL_R+.1, 2.6, kit.dark, 'X', 24, 0))
        # Collar the bell seats in, then the bright lip ring, the bell and its throat.
        kit.cylinder(f'Engine {i} exhaust collar', ((FACE_X+LIP_X0)/2+.05, cy, AXIS_Z), LIP_R+.15, .5, kit.steel, 'X', 24, .05)
        lip = kit.cylinder(f'Engine {i} mouth lip', ((LIP_X0+LIP_X1)/2, cy, AXIS_Z), LIP_R, LIP_X0-LIP_X1, kit.bright, 'X', 24, .05)
        cutter = kit.cylinder('Temporary bell well', (LIP_X1, cy, AXIS_Z), BELL_R+.1, 3.0, kit.dark, 'X', 24, 0)
        K.boolean(lip, cutter)
        bell(kit, f'Engine {i} aft-facing bell', -9.4, LIP_X1+.05, BELL_R*.62, BELL_R, cy, AXIS_Z, kit.dark)
        kit.cylinder(f'Engine {i} dark throat', (-9.5, cy, AXIS_Z), BELL_R*.66, .1, kit.dark, 'X', 24, 0)
        kit.empty(f'Engine {i} {n} exhaust anchor', (ANCHOR_X, cy, AXIS_Z), 180)
        if kit.detail:
            detail(kit, i, n, s, cy)


def detail(kit, i, n, s, cy):
    # Khaki armour on the nacelle's inboard top (the master's inner stripe).
    strip(kit, f'Engine {i} inboard armour plate', NACELLE, -10.2, -4.6, .0,
          lambda w, ct: corner_s(w, ct, 2)+.6, kit.khaki, cy=cy, side=-s, stations=3)
    # Ribbed collar where the nacelle leaves the fuselage.
    for k, x in enumerate((-2.9, -3.55, -4.2), 1):
        K.section_loft(kit, f'Engine {i} collar rib {k}', NACELLE, kit.dark, cy=cy, bevel=.03, grow=.08,
                       xs=[x+.2, x-.2])
    # Intake grille on the outboard top.
    for k in range(5):
        x = -5.4-k*.55
        z = kit.seat_z(x, cy+s*1.0)
        kit.box(f'Engine {i} top grille slat {k+1}', (x, cy+s*1.0, z+.04), (.18, 1.5, .1), kit.dark, .02)
    for k, x in enumerate((-5.6, -9.4), 1):
        kit.bolt(f'Engine {i} bolt {k}', x, cy-s*1.0)
