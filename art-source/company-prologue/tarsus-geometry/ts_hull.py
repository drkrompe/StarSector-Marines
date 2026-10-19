"""Tarsus hull: the twin forward cargo arms, the central body, the aft
sponsons with their turret drums, and the bow drums. The drive block's
constants live here; ts_engines builds it.

Plans are read off tarsus_base.png's alpha (sprite col c, row r covers model
X 123-r..124-r, Y 52-c..53-c). The sprite is symmetric about Y +1 (YC); the
port half outline is given in rel y and mirrored. Heights are proposed art.
"""
import math
from ts_kit import prism, section, plane_cut, facet_normal, Parts

YC = 1.0                  # the sprite's own centreline, in hull Y
SIDES = (('port', 1), ('starboard', -1))

# Twin forward arms (the sprite's two prongs), joined to the body aft of the crotch.
ARM_IN = 9.5              # inboard wall of each arm, rel y
ARM_NOSE = 110.0
ARM_AFT = 30.0            # buried in the central body
ARM_KEEL, ARM_DECK = -9.0, 9.0
# Central body between the crotch and the sponsons.
BODY_FRONT, BODY_AFT = 41.0, -24.0
BODY_KEEL, BODY_DECK = -12.0, 10.0
# Aft body: the sponsons that carry the aft turrets on round drums.
AFT_FRONT, AFT_BACK = -20.0, -56.5
AFT_KEEL, AFT_DECK = -11.0, 10.5
DRUM_X, DRUM_REL, DRUM_R = -41.0, 36.5, 14.5
# Bow drums: the round castings the forward turrets stand on, closing each arm's nose.
BOW_DRUM_X, BOW_DRUM_REL, BOW_DRUM_R = 100.0, 21.0, 10.5
DRUM_KEEL, DRUM_DECK = -12.0, 12.0
# Drive block: the stern machinery the two drive units and four auxiliaries sit in.
DRIVE_FRONT, DRIVE_BACK = -52.0, -73.5
DRIVE_KEEL, DRIVE_DECK = -10.0, 9.0


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


# Port arm outline, rel y, closed: outboard edge fore to aft, then the inboard wall.
ARM_PLAN = [(ARM_NOSE, ARM_IN+.5), (ARM_NOSE, 25.5), (108.0, 29.5), (104.5, 33.0), (99.0, 35.5),
            (92.0, 37.0), (89.0, 40.0), (86.0, 42.0), (50.0, 43.0), (46.0, 44.0), (41.0, 44.0), (38.0, 41.0), (ARM_AFT, 39.5),
            (ARM_AFT, ARM_IN), (ARM_NOSE-1.5, ARM_IN)]


def build(kit):
    for side, s in SIDES:
        arm = prism(kit, f'Hull {side} arm', plan(ARM_PLAN, s), section(ARM_KEEL, ARM_DECK, 4, 3), kit.ochre)
        # The arm's bow: a raked upper face and a deeper raked forefoot, bounded to the nose.
        plane_cut(arm, f'{side} arm bow rake', (ARM_NOSE-5, 0, ARM_DECK), facet_normal((1, 0), 45))
        plane_cut(arm, f'{side} arm forefoot', (ARM_NOSE-6, 0, ARM_KEEL), (math.sin(math.radians(50)), 0,
                                                                             -math.cos(math.radians(50))))
        kit.hull.append(arm)
        drum = Parts().cyl((BOW_DRUM_X, y(BOW_DRUM_REL, s), ARM_KEEL+2.5), BOW_DRUM_R, ARM_DECK+1.5-ARM_KEEL-2.5, 'Z', 32)
        kit.hull.append(drum.build(kit, f'Hull {side} bow drum', kit.rust, .2))
    body = prism(kit, 'Hull central body',
                 plan([(BODY_FRONT, 42.5), (37.0, 41.0), (33.0, 39.0), (12.0, 39.0), (9.0, 42.5),
                       (BODY_AFT, 42.5)]),
                 section(BODY_KEEL, BODY_DECK, 5, 3), kit.cream)
    kit.hull.append(body)
    aft = prism(kit, 'Hull aft body',
                plan([(AFT_FRONT, 43.0), (-27.0, 44.5), (-54.0, 44.5), (AFT_BACK, 40.0)]),
                section(AFT_KEEL, AFT_DECK, 4, 3), kit.ochre)
    kit.hull.append(aft)
    for side, s in SIDES:
        drum = Parts().cyl((DRUM_X, y(DRUM_REL, s), DRUM_KEEL), DRUM_R, DRUM_DECK-DRUM_KEEL, 'Z', 32)
        d = drum.build(kit, f'Hull {side} aft drum', kit.rust, .2)
        kit.hull.append(d)
