"""The Enforcer's carapace and ventral keel: the armoured body every turret
stands on, its sunk wells and tank bays, and the holes the sprite shows
through it.

Read off enforcer_base.png (136 x 136, .ship centre (68, 67)): sprite column
c, row r (from the top) covers X 68-r..69-r, Y 67-c..68-c.

The carapace is nearly square in plan. In the sprite it is a ring of raised
armour round three sunk areas: the two diamond wells either side of the waist
(dark, X -13..3), the centre well round the waist ahead of the bridge, and
the two tank bays aft (the tanks themselves are ef_spine's). The sprite is
see-through in the middle of each diamond well and in a slot beside the
centre well: those are open through the whole hull. Its stern is notched on
the centreline (the .ship bounds' (-29, 0)). Neck, prow and missile pods are
ef_bow's; aft spine, bridge and tanks ef_spine's; engine blocks ef_engines'.
"""
from ef_kit import (DECK, KEEL, WELL, boolean, facet_normal, mirror_y, plane_cut, prism, section, sym)

# Port halves, bow to stern (sym() mirrors them to starboard).
CARAPACE = [(35, 0), (36.5, 10), (36.5, 17.5), (34, 21), (29.5, 25), (27.5, 38), (19.5, 41), (15, 55),
            (10, 60), (-2, 62.5), (-4, 64.5), (-12, 66), (-22, 66), (-26, 62), (-32, 58), (-36, 54), (-42, 50),
            (-45, 47.5), (-49, 44), (-49, 15), (-46, 12), (-44, 8), (-36, 5.5), (-32, 0)]
DIAMOND_WELL = [(3.5, 28), (-0.5, 35), (-8, 36), (-13.5, 28.5), (-9.5, 20.5), (-1.5, 20.5)]
# The sprite is see-through in the middle of each diamond well and in a slot
# beside the centre well: open to space through the whole hull.
THROUGH = [[(-1, 28), (-2.5, 32), (-7, 32), (-8.5, 28), (-6.5, 24.5), (-2, 24.5)],
           [(6, 13), (6, 17.5), (2.5, 17.5), (2.5, 13)]]
TANK_BAY = [(-12.5, 21), (-14, 35.5), (-44, 37), (-45.5, 34), (-45.5, 16.5), (-42, 14.5), (-17, 14.5)]
CENTRE_WELL = [(13, 0), (11, 6), (7, 16.5), (1, 19), (-3, 14), (-4.5, 0)]
VENTRAL = [(31, 0), (31, 20), (22, 33), (-2, 40), (-32, 40), (-42, 30), (-42, 12), (-36, 5), (-33, 0)]


def well(kit, target, name, plan, floor):
    cutter = prism(kit, 'Temporary '+name+' cutter', plan, [(0, floor), (0, 30)], kit.dark, 0)
    boolean(target, cutter)


def build(kit):
    # Carapace: one keel chamfer, a wall, one shoulder chamfer.
    car = prism(kit, 'Hull carapace', sym(CARAPACE), section(KEEL, DECK, 3.0, 2.2), kit.armour, .25)
    # Raked forward faces: the sprite's bow shoulders fall to the pods.
    plane_cut(car, 'carapace port bow shoulder', (33, 22, DECK-1.5), facet_normal((.8, .6), 50))
    plane_cut(car, 'carapace starboard bow shoulder', (33, -22, DECK-1.5), facet_normal((.8, -.6), 50))
    for side, f in (('port', 1), ('starboard', -1)):
        def m(plan):
            return plan if f > 0 else mirror_y(plan)
        well(kit, car, f'{side} diamond well', m(DIAMOND_WELL), WELL)
        well(kit, car, f'{side} tank bay', m(TANK_BAY), WELL-1.5)
    well(kit, car, 'centre well', sym(CENTRE_WELL), WELL)
    vent = prism(kit, 'Hull ventral keel', sym(VENTRAL), section(-16.0, KEEL+1, 3.5, .5), kit.steel, .2)
    for k, hole in enumerate(THROUGH, 1):
        for side, plan in (('port', hole), ('starboard', mirror_y(hole))):
            for host in (car, vent):
                cutter = prism(kit, f'Temporary {side} through hole {k} cutter', plan, [(0, -30), (0, 30)], kit.dark, 0)
                boolean(host, cutter)
    kit.hull.append(car)
    kit.hull.append(vent)
