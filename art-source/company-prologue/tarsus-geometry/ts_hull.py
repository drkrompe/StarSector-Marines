"""Tarsus hull: the twin forward cargo arms, the central body, the aft
sponsons with their turret drums, and the bow drums. The drive block's
constants live here; ts_engines builds it.

Plans are read off tarsus_base.png's alpha (sprite col c, row r covers model
X 123-r..124-r, Y 52-c..53-c). The sprite is symmetric about Y +1 (YC); the
port half outline is given in rel y and mirrored. Heights are proposed art.
"""
import math
from ts_kit import prism, section, loft_x, Parts

YC = 1.0                  # the sprite's own centreline, in hull Y
SIDES = (('port', 1), ('starboard', -1))

# Twin forward arms (the sprite's two prongs), joined to the body aft of the crotch.
ARM_IN = 9.5              # inboard wall of each arm, rel y
ARM_NOSE = 110.0
ARM_AFT = 30.0            # buried in the central body
# Owner, pass 3 round 1: "too low poly", and "the whole ship is the same thickness on the
# same plane". The modules now stand at their own heights and depths: low faceted arms, a
# deeper central body over a ventral keel, a raised aft castle between turret-drum towers,
# and a low drive block the drive cans stand proud of.
ARM_KEEL, ARM_DECK = -8.5, 8.5       # pod keel and pod deck at full section
ARM_STRIP_DECK = 5.0                 # the lower inboard strip's deck
# Port arm cross-section (rel y, z), full size: a canted keel facet rising into the outboard
# pod, a pod wall that bulges and cants back in, the raised pod deck, an angled step down to
# the lower inboard strip, and a chamfered inboard wall.
ARM_SECTION = [(ARM_IN, -2.5), (12.0, -5.0), (24.0, -5.0), (28.5, -8.5), (37.0, -8.5), (42.0, -5.0),
               (43.5, -1.0), (43.0, 3.0), (39.5, 7.5), (30.0, 8.5), (26.0, ARM_STRIP_DECK+1.0),
               (13.0, ARM_STRIP_DECK), (ARM_IN, 2.5)]
ARM_POD_IN = 26.0                    # section points outboard of this follow the plan's width
# Arm stations fore to aft: (x, outboard rel y from the sprite, height scale for the bow rake).
ARM_STATIONS = [(ARM_NOSE, 27.0, .42), (107.0, 31.0, .6), (103.0, 33.5, .76), (97.0, 36.0, .92),
                (91.0, 38.0, 1.0), (88.0, 41.0, 1.0), (84.0, 43.0, 1.0), (50.0, 43.0, 1.0),
                (46.0, 44.0, 1.0), (41.0, 43.5, 1.0), (38.0, 41.0, 1.0), (ARM_AFT, 39.5, 1.0)]
# Central body between the crotch and the sponsons: deeper than the arms, with a steep keel.
BODY_FRONT, BODY_AFT = 41.0, -24.0
BODY_KEEL, BODY_DECK = -13.0, 10.0
# Ventral keel: a faceted belly hold under the body and aft body.
KEEL_FRONT, KEEL_AFT = 30.0, -50.0
KEEL_HALF, KEEL_BOTTOM = 18.0, -20.0
# Aft body: the sponsons that carry the aft turrets on round drums, with a raised castle.
AFT_FRONT, AFT_BACK = -20.0, -56.5
AFT_KEEL, AFT_DECK = -13.0, 10.5
CASTLE_FRONT, CASTLE_BACK, CASTLE_HALF, CASTLE_DECK = -35.5, -55.0, 20.0, 16.5
DRUM_X, DRUM_REL, DRUM_R = -41.0, 36.5, 14.5
# Bow drums: the turret sponsons that close each arm's nose.
BOW_DRUM_X, BOW_DRUM_REL, BOW_DRUM_R = 100.0, 21.0, 10.5
# Owner, pass 3 round 2: the round drums read as cylinders bolted on, stood the turrets far
# too high and did not belong to the hull. They are now turret sponsons in the hull's own
# section language: an octagon in plan (flats fore, aft and outboard), one keel chamfer, a
# short wall and a broad sloped shoulder up to a small turret deck just above the hull
# beside them. The plan's circumradius keeps the sprite's round outline's area.
DRUM_N = 8
DRUM_CIRC, BOW_DRUM_CIRC = DRUM_R*1.04, BOW_DRUM_R*1.04
DRUM_KEEL, DRUM_DECK = -13.0, 13.0         # aft body deck 10.5; the castle stands at 16.5
BOW_DRUM_KEEL, BOW_DRUM_DECK = -6.5, 8.0   # level with the raked arm pod deck beside it
DRUM_KEEL_C, DRUM_SHOULDER_C = 5.0, 6.0
BOW_DRUM_KEEL_C, BOW_DRUM_SHOULDER_C = 3.0, 4.0
# Drive block: the stern machinery the drive units are sunk into, lower than the aft body.
DRIVE_FRONT, DRIVE_BACK = -52.0, -73.5
DRIVE_KEEL, DRIVE_DECK = -10.0, 6.5


def y(rel, sign):
    return YC+sign*rel


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def plan(half, sign=None):
    """Plan from a port half outline [(x, rel y), ...] fore to aft, mirrored about YC.

    With a sign, the outline is a closed one-sided shape placed on that side."""
    if sign is not None:
        return ccw([(x, y(r, sign)) for x, r in half])
    return ccw([(x, y(r, 1)) for x, r in half]+[(x, y(r, -1)) for x, r in reversed(half)])


def sponson_plan(cx, cy, circ, n=DRUM_N):
    """CCW n-gon about (cx, cy), flats facing fore, aft and athwartships."""
    return [(cx+circ*math.cos(2*math.pi*(i+.5)/n), cy+circ*math.sin(2*math.pi*(i+.5)/n)) for i in range(n)]


def sponson_apothem(circ, n=DRUM_N):
    return circ*math.cos(math.pi/n)


def arm_section(r_out, zs, s):
    """The arm section at one station: the pod squeezed to r_out, heights scaled by zs."""
    full = max(r for r, _ in ARM_SECTION)
    out = []
    for r, z in ARM_SECTION:
        if r > ARM_POD_IN:
            r = ARM_POD_IN+(r-ARM_POD_IN)*(r_out-ARM_POD_IN)/(full-ARM_POD_IN)
        out.append((y(r, s), z*zs))
    return out


def build(kit):
    for side, s in SIDES:
        arm = loft_x(kit, f'Hull {side} arm',
                     [(x, arm_section(r, zs, s)) for x, r, zs in reversed(ARM_STATIONS)], kit.ochre)
        kit.hull.append(arm)
        kit.hull.append(prism(kit, f'Hull {side} bow drum', sponson_plan(BOW_DRUM_X, y(BOW_DRUM_REL, s), BOW_DRUM_CIRC),
                              section(BOW_DRUM_KEEL, BOW_DRUM_DECK, BOW_DRUM_KEEL_C, BOW_DRUM_SHOULDER_C), kit.ochre))
    body = prism(kit, 'Hull central body',
                 plan([(BODY_FRONT, 42.5), (37.0, 41.0), (33.0, 39.0), (12.0, 39.0), (9.0, 42.5),
                       (BODY_AFT, 42.5)]),
                 section(BODY_KEEL, BODY_DECK, 7, 3), kit.cream)
    kit.hull.append(body)
    keel = prism(kit, 'Hull ventral keel',
                 plan([(KEEL_FRONT, KEEL_HALF-6), (KEEL_FRONT-8, KEEL_HALF), (KEEL_AFT+6, KEEL_HALF),
                       (KEEL_AFT, KEEL_HALF-5)]),
                 section(KEEL_BOTTOM, BODY_KEEL+3, 5, 0), kit.grey)
    kit.hull.append(keel)
    aft = prism(kit, 'Hull aft body',
                plan([(AFT_FRONT, 43.0), (-27.0, 44.5), (-54.0, 44.5), (AFT_BACK, 40.0)]),
                section(AFT_KEEL, AFT_DECK, 6, 3), kit.ochre)
    kit.hull.append(aft)
    castle = prism(kit, 'Hull aft castle',
                   plan([(CASTLE_FRONT, CASTLE_HALF-5), (CASTLE_FRONT-4, CASTLE_HALF), (CASTLE_BACK, CASTLE_HALF)]),
                   section(AFT_DECK-2, CASTLE_DECK, 0, 3.5), kit.ochre)
    kit.hull.append(castle)
    for side, s in SIDES:
        kit.hull.append(prism(kit, f'Hull {side} aft drum', sponson_plan(DRUM_X, y(DRUM_REL, s), DRUM_CIRC),
                              section(DRUM_KEEL, DRUM_DECK, DRUM_KEEL_C, DRUM_SHOULDER_C), kit.ochre))
