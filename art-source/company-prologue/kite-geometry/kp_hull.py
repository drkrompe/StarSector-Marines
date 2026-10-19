"""Kite (P) pressure hull: the fuselage (cockpit and boarding cabin), the
lifting-body wing it sits in, the dorsal cabin spine, the aft hump that
carries the turret, and the two missile sponsons.

Planform is measured off the vanilla sprite (graphics/ships/aeroshuttle/
aeroshuttle_pirates.png, 82x66, kite.ship centre at sprite pixel 41,28 from
the bottom-left), so sprite pixel (c, r), counted from the top-left, covers
hull X 37-r..38-r and Y 40-c..41-c. Read in the sprite (bow up):

- the fuselage: a rounded nose (X 37.5) widening to +-13 by X 26 and running
  back as a +-13 neck to X 18.5, then flaring through the shoulders to +-20
  at X 14 and the full body behind it;
- the wing: a lifting-body delta whose leading edge runs from the shoulders
  (X 14, +-20) out to +-28 at X 11, +-30 back to X -1, then sweeps out to the
  +-40 tips at X -8; the tips run square aft to X -15.5;
- the missile sponsons: two armoured boxes at +-23..28 over X 4.5..14, with
  two forward prongs (X 11..14) and a gap between them and the fuselage that
  the forward-firing hardpoints (WS 001/002 at X 6, +-22) shoot through;
- the aft: the wing's trailing edge steps in to +-33 (X -16..-21) round the
  outer drive nacelles, the tail to +-23 / +-18 and the two main drive pods
  run out to X -27 at +-9..17, either side of a +-7 tail cone.

Heights are art interpretation: the sprite supplies only the plan. The
fuselage is a deep flat-bottomed lifting body; the wing is a thick plate
with a broad chamfer falling to a blunt edge.
"""
import bpy
from kp_kit import prism, section, boolean, facet, plane_cut, crisp, mirror, bx

KEEL = -6.0           # fuselage keel
FUSE_DECK = 6.0       # fuselage roof
SPINE_DECK = 9.5      # dorsal cabin spine roof (its side walls carry the cabin windows)
HUMP_DECK = 7.5       # aft hump round the turret
WING_KEEL, WING_DECK = -3.6, 0.8

FUSE_PLAN = mirror([(37.6, 0), (37.4, 1.6), (36.2, 3.6), (34.5, 5.6), (32.5, 8.0), (30.0, 10.0), (27.5, 12.0),
                    (26.0, 13.0), (19.0, 13.0), (17.5, 15.0), (16.0, 17.0), (14.0, 19.5),
                    (-20.5, 19.5), (-22.5, 16.0), (-24.5, 7.5), (-25.0, 0)])
WING_PLAN = mirror([(16.0, 0), (16.0, 17.5), (14.0, 20.0), (11.0, 28.0), (4.5, 29.0), (3.5, 30.0),
                    (-1.5, 30.5), (-3.0, 31.8), (-4.0, 33.5), (-5.0, 36.0), (-6.0, 38.0), (-7.0, 39.4),
                    (-8.0, 40.0), (-15.8, 40.0), (-16.2, 33.0), (-20.8, 33.0),
                    (-21.4, 23.0), (-22.0, 19.0), (-22.0, 0)])
# A plate a metre thick at the edge: a short keel chamfer, the edge wall and a
# broad shoulder chamfer rising to the deck (the tips are under 8 units deep,
# so the shoulder inset stays under half that).
WING_SECTION = [(1.2, WING_KEEL), (0.0, WING_KEEL+1.2), (0.0, WING_KEEL+2.4), (2.4, WING_DECK)]
SPINE_PLAN = mirror([(32.0, 0), (31.5, 2.5), (30.0, 4.6), (27.0, 5.6), (0.0, 5.6), (-2.0, 4.0), (-2.0, 0)])
HUMP_PLAN = mirror([(3.0, 0), (3.0, 7.5), (0.0, 10.5), (-17.0, 10.5), (-21.0, 8.0), (-22.5, 4.5), (-22.5, 0)])
SPONSON_X = (4.5, 14.0)
SPONSON_Y = (23.0, 28.5)
SPONSON_Z = (-3.0, 4.5)
PRONG_X = 11.0        # prongs run forward of here; the gap between them is open

# Plateaus aft to fore for the manifest: (x0, x1, top z).
DECKS = [(-22.5, 3.0, HUMP_DECK), (-25.0, 37.6, FUSE_DECK), (-2.0, 32.0, SPINE_DECK), (-22.0, 16.0, WING_DECK)]


def build_fuselage(kit):
    hull = prism(kit, 'Hull fuselage', FUSE_PLAN, section(KEEL, 2.0, FUSE_DECK, 2.5, 3.0), kit.panel)
    kit.hull = hull
    # Nose: a raked glacis over the cockpit, a chin under it, two cheek facets.
    for name, point, down, angle in [('Bow glacis', (29, 0, FUSE_DECK), (1, 0), 22),
                                     ('Chin rake', (31, 0, KEEL), (1, 0), 150),
                                     ('Port nose cheek', (33, 6.5, 2), (1, 1.5), 48),
                                     ('Starboard nose cheek', (33, -6.5, 2), (1, -1.5), 48)]:
        plane_cut(hull, *facet(name, point, down, angle))
    # Shoulders: aft of the neck the body's top rolls off outboard of +-13 into
    # the wing roots, so the fuselage reads as a lifting body, not a box.
    for s in (1, -1):
        plane_cut(hull, *facet('Shoulder roll', (0, s*13.0, FUSE_DECK), (0, s), 38))
    # Tail: the stern falls to the drive pods.
    plane_cut(hull, *facet('Tail fall', (-21.0, 0, FUSE_DECK), (-1, 0), 55))
    crisp(hull, .2)
    return hull


def build_wing(kit):
    wing = prism(kit, 'Hull wing body', WING_PLAN, WING_SECTION, kit.panel)
    crisp(wing, .2)
    return wing


def build_spine(kit):
    spine = prism(kit, 'Hull dorsal spine', SPINE_PLAN, section(FUSE_DECK-2.5, SPINE_DECK-1.5, SPINE_DECK, .5, 1.8),
                  kit.cream)
    plane_cut(spine, *facet('Spine nose fall', (29.5, 0, SPINE_DECK), (1, 0), 38))
    plane_cut(spine, *facet('Spine aft fall', (-.5, 0, SPINE_DECK), (-1, 0), 45))
    crisp(spine, .15)
    hump = prism(kit, 'Hull aft hump', HUMP_PLAN, section(FUSE_DECK-1.0, HUMP_DECK-1.6, HUMP_DECK, .5, 3.0), kit.cream)
    plane_cut(hump, *facet('Hump fore fall', (2.0, 0, HUMP_DECK), (1, 0), 40))
    crisp(hump, .15)
    return spine, hump


def build_sponsons(kit):
    x0, x1 = SPONSON_X
    y0, y1 = SPONSON_Y
    z0, z1 = SPONSON_Z
    out = []
    for s, side in ((1, 'port'), (-1, 'starboard')):
        body = bx(kit, f'Hull {side} missile sponson', x0, PRONG_X, *sorted((s*y0, s*y1)), z0, z1, kit.steel, .2)
        # Two forward prongs (the sprite's claws), the open slot between them.
        for k, (a, b) in enumerate(((y0, y0+2.0), (y1-2.2, y1))):
            p = bx(kit, f'Temporary prong {k}', PRONG_X-.5, x1, *sorted((s*a, s*b)), z0+.8, z1-.6, kit.steel, 0)
            boolean(body, p, 'UNION')
        plane_cut(body, *facet('Sponson brow', (x1-1.0, s*(y0+y1)/2, z1-.6), (1, 0), 40))
        plane_cut(body, *facet('Sponson outboard rake', (x0, s*y1, z1-.5), (0, s), 35))
        crisp(body, .15)
        out.append(body)
    return out


def build(kit):
    build_fuselage(kit)
    build_wing(kit)
    build_spine(kit)
    build_sponsons(kit)
