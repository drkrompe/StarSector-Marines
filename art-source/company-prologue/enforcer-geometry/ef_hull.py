"""The Enforcer's structure: carapace, wells, neck, prow, aft spine, bridge,
engine blocks, tank bays and the missile pods.

Read off enforcer_base.png (136 x 136, .ship centre (68, 67)): sprite column
c, row r (from the top) covers X 68-r..69-r, Y 67-c..68-c.

- Carapace: the broad armoured body, nearly square in plan, that every turret
  stands on. In the sprite it is a ring of raised armour round three sunk
  areas: the two diamond wells either side of the waist (dark, X -13..3), the
  centre well round the waist spine ahead of the bridge, and the two tank bays
  aft (pale tanks lying fore and aft). Its stern is notched on the
  centreline (the .ship bounds' (-29, 0)).
- Neck and prow: a narrow gunmetal neck runs forward from the carapace to the
  anvil prow, a broad armoured ram the width of the inner missile pods.
- Aft spine: the gunmetal spine on the centreline between the tank bays,
  carrying the bridge: the octagonal deckhouse the sprite draws as a pale
  ring at (-15, 0).
- Engine blocks: one either side of the stern notch, each holding a big drive
  between two small ones (ef_engines).
- Missile pods: four gunmetal blocks standing proud of the carapace's forward
  edge, each holding a small missile hardpoint (ef_mounts).
"""
from ef_kit import (boolean, facet_normal, mirror_y, plane_cut, prism, section, sym)

DECK = 6.0       # carapace deck
WELL = 1.0       # floor of the sunk areas
KEEL = -9.0      # carapace keel

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
NECK = [(48, 0), (48, 14), (37, 14), (35, 9), (30, 9), (30, 0)]
PROW = [(68.5, 0), (68.5, 9), (66.5, 15), (66, 18), (64, 20.5), (48, 20.5), (46, 18), (46, 0)]
SPINE = [(-3, 0), (-3, 10.5), (-42, 10.5), (-36, 5.5), (-32, 0)]
VENTRAL = [(31, 0), (31, 20), (22, 33), (-2, 40), (-32, 40), (-42, 30), (-42, 12), (-36, 5), (-33, 0)]
ENGINE_BLOCK = [(-45, 47), (-52, 48.5), (-58, 46.5), (-62, 43.5), (-64, 40), (-64, 17.5), (-60, 15.5),
                (-52, 13.5), (-46, 13)]
POD_OUTER = [(32, 41), (36, 44), (36.5, 54), (34.5, 58), (31, 60), (18, 60), (17, 41)]          # WS 006 (29, 49)
POD_INNER = [(42.5, 22.5), (46, 26), (46, 35), (42.5, 38.5), (28, 38.5), (27, 22.5)]            # WS 007 (38, 30)
TANKS = [  # (name, y, x aft, x fore, radius): the pale tanks in each bay
    ('outer', 30.0, -41.0, -22.5, 4.2),
    ('inner', 20.0, -41.0, -16.0, 4.0),
]
BRIDGE = (-15.0, 0.0, 6.6)   # deckhouse centre x, y, outer radius


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

    neck = prism(kit, 'Hull neck', sym(NECK), section(-5.0, 3.0, 1.5, 1.5), kit.steel, .15)
    kit.hull.append(neck)
    prow = prism(kit, 'Hull prow', sym(PROW), section(-6.5, 4.5, 2.5, 2.0), kit.armour, .2)
    plane_cut(prow, 'prow brow', (63, 0, 3.0), facet_normal((1, 0), 55))
    plane_cut(prow, 'prow chin', (63, 0, -4.5), (.6, 0, -.8))
    kit.hull.append(prow)

    spine = prism(kit, 'Hull aft spine', sym(SPINE), section(-6.0, DECK+1.5, 2.0, 1.5), kit.steel, .15)
    kit.hull.append(spine)
    x, y, r = BRIDGE
    import math
    octo = [(x+r*math.cos(math.pi/8+k*math.pi/4), y+r*math.sin(math.pi/8+k*math.pi/4)) for k in range(8)]
    house = prism(kit, 'Bridge deckhouse', octo, [(0, DECK+1), (0, DECK+4.6), (1.2, DECK+5.6)], kit.armour, .12)
    kit.hull.append(house)
    band = [(x+(r+.08)*math.cos(math.pi/8+k*math.pi/4), y+(r+.08)*math.sin(math.pi/8+k*math.pi/4)) for k in range(8)]
    prism(kit, 'Bridge glazing band', band, [(0, DECK+3.0), (0, DECK+4.0)], kit.glass, 0)

    for side, f in (('port', 1), ('starboard', -1)):
        def m(plan):
            return plan if f > 0 else mirror_y(plan)
        eng = prism(kit, f'Hull {side} engine block', m(ENGINE_BLOCK), section(-8.5, 4.5, 2.5, 2.0), kit.steel, .2)
        kit.hull.append(eng)
        for name, ty, xa, xf, rr in TANKS:
            t = kit.cylinder(f'Hull {side} {name} tank', ((xa+xf)/2, f*ty, WELL-1.5+rr+.3), rr, xf-xa,
                             kit.tank, 'X', 16, .25)
            kit.hull.append(t)
        for name, plan in (('outer', POD_OUTER), ('inner', POD_INNER)):
            pod = prism(kit, f'Pod {side} {name} missile', m(plan), section(-4.5, 4.5, 1.5, 1.5), kit.steel, .15)
            kit.hull.append(pod)
