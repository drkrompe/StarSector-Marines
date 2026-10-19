"""Tarsus fittings: the port pressure tanks and their blue pipe run, the
starboard cylinder, the four dorsal grapple receivers the Colossus Mk.III
clamps to, and the two flank boarding collars the Kites (P) dock on.

The tanks are the sprite's two big white capsules on the port side of the
central body (X -1..29 at Y 14..30, X -28..4 at Y 22..40) and the grey
cylinder at X -13..-3, Y -30..-40; the blue pipe runs diagonally from the fore
tank's aft end toward the spine. Receivers and collars are not drawn on the
sprite: they are the towing and boarding hardpoints a hazard-duty freighter
carries, placed on clear deck and flank (agreed with the Mk.III session)."""
from ts_kit import revolve_x, Parts
import ts_hull as H

TANKS = [  # name, x aft, x fore, y, radius, sink below the deck
    ('Tank port fore', -1.0, 29.0, 22.0, 8.0, 5.0),
    ('Tank port aft', -28.0, 4.0, 31.0, 8.5, 5.0),
]
# Dorsal grapple receivers, (x, rel y, side): two on the arms, two on the aft body's deck
# inboard of the drums and aft of the port tanks.
RECEIVERS = [(70.0, 26.0, 1), (70.0, 26.0, -1), (-30.0, 14.0, 1), (-30.0, 14.0, -1)]
# Flank boarding collars: (side, x, outward sign) on the central body's walls.
COLLARS = [('starboard', 0.0, -1), ('port', -12.0, 1)]
ANCHORS = {}


def _capsule(x0, x1, r, n=10):
    import math
    prof = [(x0, .01)]
    for k in range(1, n):
        a = math.pi/2*k/n
        prof.append((x0+r*.55*(1-math.cos(a)), r*math.sin(a)))
    prof += [(x1-r*.55*(1-math.cos(math.pi/2*k/n)), r*math.sin(math.pi/2*k/n)) for k in range(n-1, 0, -1)]
    prof.append((x1, .01))
    return prof


def build(kit):
    for name, x0, x1, yy, r, sink in TANKS:
        revolve_x(kit, name, _capsule(x0, x1, r), yy, H.BODY_DECK+r-sink, kit.white, n=32)
    t = Parts().cyl((-8.0, -30.5, H.BODY_DECK+2.0), 5.0, -10.5, 'Y', 24)
    t.build(kit, 'Tank starboard cylinder', kit.grey, .1)
    pipe = Parts().beam((-1.0, 15.5, H.BODY_DECK+1.2), (-15.0, 3.0, H.BODY_DECK+1.2), 2.6, 2.2)
    pipe.build(kit, 'Pipe blue transfer run', kit.blue, .1)
    rings, plates = Parts(), Parts()
    for i, (x, rel, s) in enumerate(RECEIVERS, 1):
        yy = H.y(rel, s)
        p, _ = kit.surface(x, yy)
        rings.cyl((x, yy, p.z), 3.4, .25, 'Z', 32)
        plates.cyl((x, yy, p.z), 2.6, .55, 'Z', 32)
        kit.empty(f'Clamp receiver {i} anchor', (x, yy, p.z+.55), 90).rotation_euler = (0, -1.5707963, 0)
        ANCHORS[f'Clamp receiver {i}'] = ((x, yy, round(p.z+.55, 2)), (0.0, 0.0, 1.0))
    rings.build(kit, 'Clamp receiver dorsal hazard rings', kit.orange)
    plates.build(kit, 'Clamp receiver dorsal plates', kit.steel, .06)
    frame, door = Parts(), Parts()
    for side, x, s in COLLARS:
        wall = H.YC+s*42.5
        frame.cyl((x, wall, -1.0), 3.6, s*.6, 'Y', 32)
        door.cyl((x, wall+s*.6, -1.0), 2.6, s*.2, 'Y', 32)
        kit.empty(f'Kite collar {side} anchor', (x, wall+s*.8, -1.0), 90 if s > 0 else -90)
        ANCHORS[f'Kite collar {side}'] = ((x, round(wall+s*.8, 2), -1.0), (0.0, float(s), 0.0))
    frame.build(kit, 'Kite collar flank frames', kit.orange)
    door.build(kit, 'Kite collar flank hatches', kit.steel, .05)
