"""Hermes pressure hull: the spine (cockpit and troop bay), the wing body
with the forward gun sponsons, and the aft drive block.

Planform is measured off the vanilla sprite (graphics/ships/hermes/
hermes_base.png, 56x82, .ship centre at sprite pixel 28,39 from the
bottom-left), so sprite pixel (c, r), counted from the top-left, covers hull
X 42-r..43-r and Y 27-c..28-c. Three stacked strata read in the sprite:

- the spine, +-11 from the nose (X 42) back to the aft turret (X -24): the
  pressure hull proper, with the cockpit forward and the troop bay amidships;
- the wing body, +-26 over X -17.5..22: thinner shoulder blocks carrying the
  forward gun sponsons (WS 001/002 at X 12) and, aft of the notch at X 4,
  the main landing gear;
- the aft block, +-21 over X -37..-17.5: the drive block behind the troop
  bay, with three drive units on its stern face.

Sections are one keel chamfer, one vertical wall and one shoulder chamfer.
Heights are art interpretation: the sprite supplies only the plan.
"""
from hm_kit import prism, section, boolean, facet, plane_cut, crisp, mirror

KEEL = -10.0          # spine keel (troop-bay floor structure below it)
SPINE_DECK = 10.0     # spine roof
WING_KEEL, WING_DECK = -5.5, 5.5
AFT_KEEL, AFT_DECK = -8.0, 8.0
SPINE_AFT = -24.0

# Port half-plans, bow to stern, closed with their starboard mirrors.
SPINE_PLAN = mirror([(42, 0), (42, 5), (41, 6), (40, 7), (39, 8), (38, 9), (35, 9.5), (33.5, 11),
                     (SPINE_AFT, 11), (SPINE_AFT, 0)])
# Collar round the neck at X 26..28.5 (the sprite's +-13 lip).
COLLAR_PLAN = mirror([(28.5, 0), (28.5, 12), (27.5, 13), (26, 13), (25.5, 11.5), (25.5, 0)])
WING_PLAN = mirror([(22, 0), (22, 19), (20.5, 21), (18, 23), (16, 25), (14, 26), (5.5, 26),
                    (4.5, 24.5), (3, 24), (2, 23), (-2, 23), (-3, 24.5), (-5, 26),
                    (-15, 26), (-16, 25), (-17.5, 23.5), (-17.5, 0)])
AFT_PLAN = mirror([(-16.5, 0), (-16.5, 22), (-19, 22), (-21, 20.5), (-23.5, 20.5),
                   (-25, 21), (-27, 21), (-28.5, 20), (-30, 21), (-34, 21), (-35, 20),
                   (-36, 18), (-37, 17), (-37, 0)])

# Plateaus aft to fore for the manifest: (x0, x1, top z).
DECKS = [(-37, -16.5, AFT_DECK), (-17.5, 22, WING_DECK), (SPINE_AFT, 34, SPINE_DECK)]


def build_shell(kit):
    hull = prism(kit, 'Main hull - spine', SPINE_PLAN, section(KEEL, 4, SPINE_DECK, 3, 3), kit.cream)
    kit.hull = hull
    # Nose: a raked glazing glacis over the cockpit, a chin under it, and two
    # cheek facets so the sprite's rounded nose reads as faceted plate.
    for name, point, down, angle in [('Bow glacis', (30, 0, SPINE_DECK), (1, 0), 34),
                                     ('Chin rake', (34, 0, KEEL), (1, 0), 140),
                                     ('Port nose cheek', (37, 8, 3), (1, 1.4), 50),
                                     ('Starboard nose cheek', (37, -8, 3), (1, -1.4), 50)]:
        plane_cut(hull, *facet(name, point, down, angle))
    crisp(hull, .25)
    return hull


def build_wing_body(kit):
    wing = prism(kit, 'Wing body', WING_PLAN, section(WING_KEEL, 2.5, WING_DECK, 2, 2), kit.panel)
    crisp(wing, .2)
    aft = prism(kit, 'Aft drive block', AFT_PLAN, section(AFT_KEEL, 4, AFT_DECK, 1.5, 1.5), kit.panel)
    crisp(aft, .2)
    collar = prism(kit, 'Nose collar', COLLAR_PLAN, section(-7, 4, 8, .6, .6), kit.steel)
    crisp(collar, .15)
    return wing, aft, collar


def build(kit):
    build_shell(kit)
    build_wing_body(kit)
