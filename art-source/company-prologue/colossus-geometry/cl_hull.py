"""Colossus hull: the forward cargo body, its longerons, the waist and the aft machinery body.

The hull is broken into named modules along the joints the sprite draws, so a
later condition state can tear them apart cleanly: the central cargo hold, two
pale longerons, four side cargo modules (fore and aft, port and starboard, split
at the seam the sprite shows at X 33), the hold's hatch cover, the keel spine
through the waist, the aft machinery body under the bridge, and the open
lattice frames that tie the side modules and drive pods to the spine.

Planform is read off colossus.png; heights are proposed art. The sprite sits
1.5 units to port of the .ship centre (its engines are at +49..64 and
-46..-61), so every part is symmetric about Y = YC.
"""
import cl_kit
from cl_kit import prism, section

YC = 1.5            # the sprite's own centreline, in hull Y
FRONT = 90.5        # bow face of the cargo body
HOLD_AFT = -19.0    # aft face of the cargo body
HOLD_Y = 36.0       # central hold half-width
LONGERON_Y = (36.0, 46.0)
SIDE_Y = (46.0, 88.0)
SIDE_SPLIT = 33.0   # seam between fore and aft side modules
HOLD_KEEL, HOLD_DECK = -22.0, 18.0
SIDE_KEEL, SIDE_DECK = -17.0, 14.0
AFT_KEEL, AFT_DECK = -16.0, 13.0
SIDES = (('port', 1), ('starboard', -1))


def y(rel, sign):
    return YC+sign*rel


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def plan(half, sign=None):
    """Hull plan from a port half outline [(x, rel y), ...] fore to aft, mirrored about YC.

    With a sign, the outline is a closed one-sided shape instead: [(x, rel y)] placed on that side."""
    if sign is not None:
        return ccw([(x, y(r, sign)) for x, r in half])
    return ccw([(x, y(r, 1)) for x, r in half]+[(x, y(r, -1)) for x, r in reversed(half)])


def build(kit):
    # Central hold: the long box the cargo rides in, full length of the cargo body.
    hold = prism(kit, 'Hull central cargo hold',
                 plan([(FRONT, HOLD_Y-2), (FRONT-2, HOLD_Y), (HOLD_AFT, HOLD_Y)]),
                 section(HOLD_KEEL, HOLD_DECK, 5, 3), kit.red)
    kit.hull.append(hold)
    # Hatch cover over the hold: the sprite's big pale square.
    lid = prism(kit, 'Hull hold hatch cover', plan([(79, 31), (77, 33), (15, 33), (13, 31)]),
                section(HOLD_DECK-.5, HOLD_DECK+2.5, .5, 1.2), kit.red)
    kit.hull.append(lid)
    for side, s in SIDES:
        a, b = LONGERON_Y
        lon = prism(kit, f'Hull {side} longeron',
                    plan([(FRONT+.5, a), (FRONT+.5, b), (HOLD_AFT-1, b), (HOLD_AFT-1, a)], s),
                    section(HOLD_KEEL+3, HOLD_DECK+4, 3, 2), kit.pale)
        kit.hull.append(lon)
        # The longeron's aft knee, angled inboard onto the keel spine.
        prism(kit, f'Hull {side} longeron aft knee',
              plan([(HOLD_AFT+2, a+1), (HOLD_AFT+2, b-1), (-36, 30), (-36, 22)], s),
              section(-8, 9, 2, 2), kit.pale)
        a, b = SIDE_Y
        fore = prism(kit, f'Cargo {side} fore module',
                     plan([(FRONT, a), (FRONT, b-6), (FRONT-5, b), (SIDE_SPLIT+.6, b), (SIDE_SPLIT+.6, a)], s),
                     section(SIDE_KEEL, SIDE_DECK, 4, 3), kit.maroon)
        aft = prism(kit, f'Cargo {side} aft module',
                    plan([(SIDE_SPLIT-.6, a), (SIDE_SPLIT-.6, b), (-12, b), (-20, b-9), (-20, a)], s),
                    section(SIDE_KEEL, SIDE_DECK, 4, 3), kit.maroon)
        kit.hull += [fore, aft]
        # Waist truss: open lattice tying the aft side module to the spine and pod.
        kit.box(f'Frame {side} waist truss', (-27, y(34, s), 0), (16, 28, 10), kit.dark, bevel=.3)
        kit.box(f'Frame {side} pod truss', (-52, y(40, s), 0), (40, 10, 14), kit.dark, bevel=.3)
        kit.box(f'Frame {side} pod collar truss', (-24, y(56, s), -1), (10, 12, 8), kit.dark, bevel=.3)
    spine = prism(kit, 'Hull keel spine', plan([(-12, 20), (-46, 20)]), section(-15, 12, 4, 3), kit.red)
    aft = prism(kit, 'Hull aft machinery body',
                plan([(-30, 34), (-80, 35), (-84, 27), (-87, 16), (-90, 9), (-92, 5)]),
                section(AFT_KEEL, AFT_DECK, 5, 4), kit.red)
    kit.hull += [spine, aft]
