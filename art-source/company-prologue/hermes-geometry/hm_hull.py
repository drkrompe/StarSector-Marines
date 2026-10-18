"""Hermes pressure hull: the spine (cockpit and troop bay) with its cream
fairing, the forward gun sponsons and the aft drive block.

Planform is measured off the vanilla sprite (graphics/ships/hermes/
hermes_base.png, 56x82, .ship centre at sprite pixel 28,39 from the
bottom-left), so sprite pixel (c, r), counted from the top-left, covers hull
X 42-r..43-r and Y 27-c..28-c. Read in the sprite:

- the spine, +-11 from the nose (X 42) back to the aft turret (X -24): the
  pressure hull proper, with the cockpit forward and the troop bay amidships;
- the gun sponsons, the grey octagonal wing corners over X 6.5..22 carrying
  WS 001/002 at X 12 on flat decks over raked walls;
- the spine fairing: aft of X 10 the spine's cream top flares out as one
  crowned deck rolling down to the wing edge (+-26) over a raked flank, back
  to X -17.5. It is part of the spine's plate, not a separate block, and the
  boarding doors are recessed into its flanks;
- the aft block, +-21 over X -37..-16.5: the drive block behind the troop
  bay, with three drive units on its stern face.

The boat sits flat: every block shares the spine's keel (owner, pass 3).
Heights are art interpretation: the sprite supplies only the plan.
"""
import math
import os
import bmesh
import bpy
from hm_kit import (prism, section, boolean, facet, plane_cut, crisp, mirror, material, bx, blocks,
                    ccw, pivot, parent_to)

KEEL = -10.0          # spine keel (troop-bay floor structure below it)
SPINE_DECK = 10.0     # spine roof
# Pass 3 (owner): the boat sits flat, so every block shares the spine's
# keel; the wing pods' walls rake inward and their tops slope down outboard.
WING_KEEL, WING_DECK = -10.0, 5.0
AFT_KEEL, AFT_DECK = -10.0, 8.0
# Forward pods are the gun sponsons: the sprite's octagonal wing corners, a
# short vertical skirt, a raked wall and a flat deck the turret ring stands on.
POD_SECTION = [(1.0, WING_KEEL), (0.0, WING_KEEL + 1.0), (0.0, -5.0), (2.2, 3.8), (3.0, WING_DECK + .2)]
# Behind the side guns the spine flares (owner, pass 3, read off the
# sprite): its cream top runs out from the roof edge as one broad, crowned
# deck rolling down outboard, over a raked flank down to a short skirt at the
# wing edge. The fairing is swept along X from this half-section, raked into
# the gun sponsons at its forward edge, and is part of the spine's plate.
FAIRING_X = (11.0, -17.5)
FAIRING_HALF = [(8.0, SPINE_DECK), (12.0, 9.7), (16.0, 8.8), (19.5, 7.5), (22.5, 5.8), (25.9, -7.0), (25.9, -9.0),
                (24.9, KEEL)]   # skirt a hair inside the sponson's, never coplanar
FAIRING_LEAD = ((10.5, 11.0), (3.0, 24.5))     # forward edge in plan, port
FAIRING_VENT, FAIRING_VENT_FLOOR = (-3.2, 1.6), 8.0   # the sprite's grey-blue vents
SPINE_AFT = -24.0

# Port half-plans, bow to stern, closed with their starboard mirrors.
SPINE_PLAN = mirror([(42, 0), (42, 5), (41, 6), (40, 7), (39, 8), (38, 9), (35, 9.5), (33.5, 11),
                     (SPINE_AFT, 11), (SPINE_AFT, 0)])
# Collar round the neck at X 26..28.5 (the sprite's +-13 lip).
COLLAR_PLAN = mirror([(28.5, 0), (28.5, 12), (27.5, 13), (26, 13), (25.5, 11.5), (25.5, 0)])
WING_PLAN = mirror([(22, 0), (22, 19), (20.5, 21), (18, 23), (16, 25), (14, 26), (5.5, 26),
                    (4.5, 24.5), (3, 24), (2, 23), (-2, 23), (-3, 24.5), (-5, 26),
                    (-15, 26), (-16, 25), (-17.5, 23.5), (-17.5, 0)])
# The pods are swept from the plan with the notch filled (a deep inset would
# fold over its re-entrant corners); the boarding-door bay recuts it.
POD_PLAN = mirror([(22, 0), (22, 19), (20.5, 21), (18, 23), (16, 25), (14, 26),
                   (6.5, 26), (6.5, 0)])
AFT_PLAN = mirror([(-16.5, 0), (-16.5, 22), (-19, 22), (-21, 20.5), (-23.5, 20.5),
                   (-25, 21), (-27, 21), (-28.5, 20), (-30, 21), (-34, 21), (-35, 20),
                   (-36, 18), (-37, 17), (-37, 0)])

# Plateaus aft to fore for the manifest: (x0, x1, top z).
DECKS = [(-37, -16.5, AFT_DECK), (-17.5, 10.5, SPINE_DECK), (6.5, 22, WING_DECK),
         (SPINE_AFT, 34, SPINE_DECK)]


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


def sweep_x(kit, name, x0, x1, half, mat):
    """A port half-section (y, z) closed with its mirror and swept along X."""
    ring = list(half) + [(-y, z) for y, z in reversed(half)]
    n = len(ring)
    verts = [(x, y, z) for x in (x0, x1) for y, z in ring]
    faces = [tuple(range(n)), tuple(reversed(range(n, 2*n)))]
    faces += [(i, n+i, n+(i+1) % n, (i+1) % n) for i in range(n)]
    ob = kit.mesh(name, verts, faces, mat)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def build_wing_body(kit):
    wing = prism(kit, 'Wing body', POD_PLAN, POD_SECTION, kit.panel)
    crisp(wing, .2)
    fairing = sweep_x(kit, 'Temporary spine fairing', FAIRING_X[1], FAIRING_X[0], FAIRING_HALF, kit.cream)
    (ax, ay), (bx_, by) = FAIRING_LEAD
    for s in (1, -1):
        # Forward edge: a 45 deg ramp down onto the sponson deck.
        plane_cut(fairing, *facet('Fairing lead', ((ax+bx_)/2, s*(ay+by)/2, WING_DECK + .2),
                                  (by - ay, -s*(bx_ - ax)), 45))
        plane_cut(fairing, *facet('Fairing aft corner', (-16.25, s*24.75, KEEL), (-1, s), 75))
    # Aft edge: eased down onto the drive block's deck.
    plane_cut(fairing, *facet('Fairing aft fall', (-16.5, 0, AFT_DECK), (-1, 0), 50))
    boolean(kit.hull, fairing, 'UNION')
    aft = prism(kit, 'Aft drive block', AFT_PLAN, section(AFT_KEEL, 4, AFT_DECK, 1.5, 1.5), kit.panel)
    crisp(aft, .2)
    collar = prism(kit, 'Nose collar', COLLAR_PLAN, section(-7, 4, 8, .6, .6), kit.steel)
    crisp(collar, .15)
    return wing, aft, collar



# ---------------------------------------------------------------------------
# Pass 2 (refit to the pass-3 shape): plate modules, seams, louvres, the
# horseshoe arms, the fairing's vents.
#
# Everything below is added on top of the pass-1 solids and stays inside
# their plan, so the silhouette is unchanged. Walls get shallow boolean
# grooves (seams, strakes, cover recesses) with dark cores in them; tops get
# separate plate modules over a dark seam underlay; chamfers get thin dark
# seam bands. Nothing is placed inside r 6.5 of a turret slot (the turret
# module's zone), below z -4 (ventral), forward of X 30 on the spine
# (cockpit) or aft of X -35.5 (engines).
# ---------------------------------------------------------------------------
AFT_TURRET = (-18.0, 0.0)
SPINE_TOP_AFT = SPINE_AFT + 3.0     # the spine's flat top ends where its aft chamfer starts
PLATE = SPINE_DECK + .28            # top of the spine plate modules
LANE_C = 2.55                       # half width of the dorsal ridge lane
LANE_IN, LANE_OUT = 2.85, 7.8       # side plate lanes
# Spine plate courses, aft to fore: (x0, x1, side lanes are louvre banks).
SPINE_COURSES = [(0.3, 3.8, False), (4.1, 8.2, False), (8.5, 12.6, True), (12.9, 17.1, False),
                 (17.4, 22.0, True), (22.3, 25.9, True), (26.2, 29.7, False)]
WELL_COURSES = [(-10.15, -5.9), (-5.6, 0.0)]
# Horseshoe arm centreline (port), aft along the spine shoulder to the stern corner.
ARM_PATH = [(1.0, 8.6), (-6.0, 9.2), (-12.0, 10.4), (-18.0, 12.4), (-24.0, 14.4), (-30.0, 16.0), (-35.3, 16.6)]
ARM_SEGMENTS = [(1.0, -7.8), (-8.2, -16.0), (-16.4, -25.3), (-25.7, -35.3)]
ARM_HALF, ARM_BOTTOM = 1.5, 5.0
SIDES = ((1, 'port'), (-1, 'starboard'))


def fix_normals(ob):
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()
    return ob


def extrude_many(kit, name, items, mat, bevel=0):
    """One object of YZ polygons extruded along X: [(poly_yz, x0, x1), ...]."""
    verts, faces = [], []
    for poly, x0, x1 in items:
        poly = ccw(poly)
        n, b = len(poly), len(verts)
        verts += [(x0, y, z) for y, z in poly] + [(x1, y, z) for y, z in poly]
        faces += [tuple(b+i for i in reversed(range(n))), tuple(b+n+i for i in range(n))]
        faces += [(b+i, b+(i+1) % n, b+n+(i+1) % n, b+n+i) for i in range(n)]
    return fix_normals(kit.mesh(name, verts, faces, mat, bevel))


def band(pts, a, b):
    """A thin YZ polygon along a polyline, offset a..b along its left normal
    (outward for a port polyline listed from the top inboard edge downward)."""
    normals = []
    for i in range(len(pts)):
        segs = [(pts[j+1][0]-pts[j][0], pts[j+1][1]-pts[j][1]) for j in (i-1, i) if 0 <= j < len(pts)-1]
        ny = sum(-dz/math.hypot(dy, dz) for dy, dz in segs)
        nz = sum(dy/math.hypot(dy, dz) for dy, dz in segs)
        L = math.hypot(ny, nz)
        normals.append((ny/L, nz/L))
    outer = [(p[0]+n[0]*b, p[1]+n[1]*b) for p, n in zip(pts, normals)]
    inner = [(p[0]+n[0]*a, p[1]+n[1]*a) for p, n in zip(pts, normals)]
    return outer + list(reversed(inner))


def both(poly_yz):
    """A port YZ polygon and its starboard mirror."""
    return [poly_yz, [(-y, z) for y, z in poly_yz]]


def bolt(x, y, z, s=.24, h=.1):
    return (x-s/2, x+s/2, y-s/2, y+s/2, z, z+h)


def ybolt(x, y, z, s, h):
    """A bolt head standing proud along +-Y (h signed) from (x, y, z)."""
    return (x-s/2, x+s/2, min(y, y+h), max(y, y+h), z-s/2, z+s/2)


def cut(kit, solid, boxes):
    """Shallow boolean grooves/recesses into a hull solid, all in one pass.
    The solid's plate-edge chamfer is applied first so the groove edges stay
    sharp: chamfering a groove narrower than two chamfer widths folds it."""
    if boxes:
        bevel = solid.modifiers.get('Plate edge chamfer')
        if bevel:
            bpy.context.view_layer.objects.active = solid
            bpy.ops.object.modifier_apply(modifier=bevel.name)
        # One cutter per box: crossing grooves in one cutter mesh would make it
        # self-intersecting, which the manifold solver mis-resolves.
        for b in boxes:
            boolean(solid, blocks(kit, 'Temporary hull groove cutter', [b], kit.dark), 'DIFFERENCE')


def wall_groove(s, x0, x1, wall, z0, z1, depth=.4):
    """Cutter box and dark core box for a groove into a +-Y wall at |Y| = wall."""
    y0, y1 = sorted((s*(wall-depth), s*(wall+.6)))
    yc = s*(wall-depth)
    core = (x0+.02, x1-.02, min(yc, yc+s*.1), max(yc, yc+s*.1), z0+.02, z1-.02)
    return (x0, x1, y0, y1, z0, z1), core


def clear_turret(kit, ob, r=7.0, centre=AFT_TURRET):
    c = kit.cylinder('Temporary turret clearance', (*centre, 9), r, 30, kit.dark, 'Z', 48, 0)
    boolean(ob, c, 'DIFFERENCE')
    return ob


def louvre(kit, name, x0, x1, y0, y1, z, slats, frame=.28):
    """A louvre bank: dark well, steel frame, transverse slats (along Y)."""
    bx(kit, name + ' well', x0+frame-.05, x1-frame+.05, y0+frame-.05, y1-frame+.05, z-.5, z+.12, kit.dark, 0)
    f = [(x0, x1, y0, y0+frame), (x0, x1, y1-frame, y1), (x0, x0+frame, y0+frame, y1-frame), (x1-frame, x1, y0+frame, y1-frame)]
    blocks(kit, name + ' frame', [(a, b, c, d, z-.1, z+.32) for a, b, c, d in f], kit.steel, .04)
    pitch = (x1-x0-2*frame)/slats
    xs = [x0+frame+pitch*(i+.5) for i in range(slats)]
    blocks(kit, name + ' slats', [(x-.11, x+.11, y0+frame, y1-frame, z-.25, z+.2) for x in xs], kit.steel)


def sweep(kit, name, path, profile, mat):
    """Solid swept along a plan polyline [(x, y), ...]; profile(i) gives the
    section [(lateral, z), ...] at station i."""
    rings = []
    n = len(path)
    for i, (x, y) in enumerate(path):
        a, b = path[max(i-1, 0)], path[min(i+1, n-1)]
        tx, ty = b[0]-a[0], b[1]-a[1]
        L = math.hypot(tx, ty)
        lx, ly = -ty/L, tx/L
        rings.append([(x+lx*l, y+ly*l, z) for l, z in profile(i)])
    m = len(rings[0])
    verts = [v for r in rings for v in r]
    faces = [tuple(range(m)), tuple(range((n-1)*m, n*m))]
    for s in range(n-1):
        faces += [(s*m+k, s*m+(k+1) % m, (s+1)*m+(k+1) % m, (s+1)*m+k) for k in range(m)]
    return fix_normals(kit.mesh(name, verts, faces, mat))


def arm_y(x):
    for (xa, ya), (xb, yb) in zip(ARM_PATH, ARM_PATH[1:]):
        if xb <= x <= xa:
            return ya + (yb-ya)*(xa-x)/(xa-xb)
    return ARM_PATH[0][1] if x > ARM_PATH[0][0] else ARM_PATH[-1][1]


def arm_top(x):
    """Arm crest: ramps up off the spine plates at its root, level along the
    spine, then falls toward the stern corner."""
    if x > -3.0:
        return PLATE + .15 + (SPINE_DECK + 1.0 - PLATE - .15)*(1.0 - x)/4.0
    return SPINE_DECK + 1.0 if x >= -21 else SPINE_DECK + 1.0 - (-21-x)/(35.3-21)*1.3


# --- spine -----------------------------------------------------------------

def spine_top(kit):
    """Segmented cream plates, the dorsal ridge, louvre banks, and the grey
    well round the aft turret, over one dark seam underlay."""
    bx(kit, 'Hull spine seam underlay', WELL_COURSES[0][0], 29.9, -LANE_OUT-.05, LANE_OUT+.05,
       SPINE_DECK-.03, SPINE_DECK+.12, kit.dark, 0)
    bolts = []
    for k, (x0, x1, vents) in enumerate(SPINE_COURSES, 1):
        bx(kit, f'Hull dorsal ridge plate {k}', x0, x1, -LANE_C, LANE_C, SPINE_DECK, PLATE+.17, kit.cream, .08)
        bolts += [bolt(x, y, PLATE+.17, .2, .08) for x in (x0+.3, x1-.3) for y in (-1.6, 1.6)]
        for s, side in SIDES:
            y0, y1 = sorted((s*LANE_IN, s*LANE_OUT))
            if vents:
                louvre(kit, f'Hull spine louvre {k} {side}', x0, x1, y0, y1, SPINE_DECK + .1, int((x1-x0)/.75))
            else:
                bx(kit, f'Hull spine plate {k} {side}', x0, x1, y0, y1, SPINE_DECK, PLATE, kit.cream, .06)
                bolts += [bolt(x, s*y, PLATE) for x in (x0+.3, x1-.3) for y in (3.4, 5.3, 7.25)]
    for k, (x0, x1) in enumerate(WELL_COURSES, 1):
        bx(kit, f'Hull well plate {k} centre', x0, x1, -LANE_C, LANE_C, SPINE_DECK, PLATE, kit.panel, .06)
        for s, side in SIDES:
            y0, y1 = sorted((s*LANE_IN, s*LANE_OUT))
            bx(kit, f'Hull well plate {k} {side}', x0, x1, y0, y1, SPINE_DECK, PLATE, kit.panel, .06)
            bolts += [bolt(x, s*y, PLATE) for x in (x0+.3, x1-.3) for y in (3.4, 7.25)]
    blocks(kit, 'Hull spine plate bolts', bolts, kit.steel)
    # Grey deck of the turret well, port and starboard halves, cut round the ring.
    x0, x1 = SPINE_TOP_AFT + .1, WELL_COURSES[0][0] - .3
    for s, side in SIDES:
        y0, y1 = sorted((s*.15, s*LANE_OUT))
        clear_turret(kit, bx(kit, f'Hull aft turret well deck {side}', x0, x1, y0, y1, SPINE_DECK, PLATE, kit.panel, .06))
    # The sprite's dark ring round the aft turret, outside the turret's own zone.
    ring = kit.cylinder('Hull aft turret well ring', (*AFT_TURRET, SPINE_DECK+.03), 7.0, .14, kit.dark, 'Z', 48, 0)
    clear_turret(kit, ring, 6.2)
    trim = bx(kit, 'Temporary well ring trim', SPINE_AFT-10, SPINE_TOP_AFT+.1, -10, 10, 0, 20, kit.dark, 0)
    boolean(ring, trim, 'DIFFERENCE')


def spine_sides(kit):
    """Seam bands down the spine shoulder, slots on the neck, the collar's bolts."""
    items = []
    for x in [c[0]-.15 for c in SPINE_COURSES[1:]] + [SPINE_COURSES[-1][1]+.15]:
        pts = [(LANE_OUT, SPINE_DECK), (8.0, SPINE_DECK), (11.0, 4.0)] + ([(11.0, -3.8)] if x > 22 else [])
        items += [(p, x-.15, x+.15) for p in both(band(pts, -.06, .05))]
    extrude_many(kit, 'Hull spine shoulder seams', items, kit.dark)
    # Transverse slots on the neck between the wing's leading edge and the collar.
    slots, lips = [], []
    for x in (22.75, 23.55, 24.35, 25.1):
        pts = [(8.45, 9.1), (10.8, 4.4), (11.0, 4.0), (11.0, -2.0)]
        slots += [(p, x-.2, x+.2) for p in both(band(pts, -.06, .06))]
        lips += [(p, x+.2, x+.32) for p in both(band(pts, -.06, .16))]
    extrude_many(kit, 'Hull neck slots', slots, kit.dark)
    extrude_many(kit, 'Hull neck slot lips', lips, kit.steel)
    # Collar: bolt heads on its top flats and a weld bead where it meets the spine wall.
    zc = 8.0
    blocks(kit, 'Hull collar bolts',
           [bolt(x, s*y, zc, .26, .12) for s, _ in SIDES for x, y in ((26.2, 12.1), (27.0, 12.1), (27.8, 11.6))], kit.steel)
    blocks(kit, 'Hull collar weld beads',
           [(25.5, 28.5, s*11.0-.12, s*11.0+.12, zc-.02, zc+.14) for s, _ in SIDES], kit.steel)
    # Fillet weld where the spine wall meets the wing deck.
    blocks(kit, 'Hull spine wing weld beads',
           [(-16.3, 21.8, min(s*11.0, s*11.25), max(s*11.0, s*11.25), WING_DECK-.02, WING_DECK+.16) for s, _ in SIDES], kit.steel)


def cheek_fairings(kit):
    """The sprite's forward cream sweeps (X 4..10, |Y| 9..14): wedge
    fairings filling the corner between the spine shoulder and the wing deck,
    the mirror of the horseshoe arms aft. Their inner edge sits on the spine
    chamfer; they stop short of the sponson ring zone."""
    for s, side in SIDES:
        top = [(9.4, 9.2, 8.0), (3.8, 9.2, 8.0), (3.8, 11.6, WING_DECK+.3), (9.4, 13.8, WING_DECK+.3)]
        bottom = [(x, y, 5.0) for x, y, _ in top]
        verts = [(x, s*y, z) for x, y, z in top + bottom]
        faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
        crisp(fix_normals(kit.mesh(f'Hull {side} cheek fairing', verts, faces, kit.cream)), .1)
        blocks(kit, f'Hull {side} cheek fairing bolts',
               [bolt(x, s*(9.6 + (y - 9.6)), 8.0 - (y - 9.2)/(y_out - 9.2)*(8.0 - WING_DECK - .3) - .03, .22, .1)
                for x, y_out in ((4.4, 11.6 + (13.8 - 11.6)*(4.4 - 3.8)/5.6), (8.8, 11.6 + (13.8 - 11.6)*(8.8 - 3.8)/5.6))
                for y in (9.6, 9.6 + (y_out - 9.6)*.5)], kit.steel)


def horseshoe_arms(kit):
    """The sprite's two raised cream arms: from the spine shoulder at X 0
    round the aft turret's well to the stern corners, in four plate modules
    each with a seam gap between them."""
    w, c = ARM_HALF, .6
    for s, side in SIDES:
        bolts = []
        for k, (xa, xb) in enumerate(ARM_SEGMENTS, 1):
            n = max(2, int(abs(xa-xb)/1.0)+1)
            xs = [xa+(xb-xa)*i/(n-1) for i in range(n)]
            path = [(x, s*arm_y(x)) for x in xs]
            prof = lambda i, xs=xs: [(-w, ARM_BOTTOM), (w, ARM_BOTTOM), (w, arm_top(xs[i])-c), (w-c, arm_top(xs[i])),
                                     (-w+c, arm_top(xs[i])), (-w, arm_top(xs[i])-c)]
            crisp(sweep(kit, f'Hull aft arm {side} segment {k}', path, prof, kit.cream), .1)
            for x in (xa-.45, xb+.45):
                bolts += [bolt(x, s*arm_y(x)+d, arm_top(x)-.02, .24, .12) for d in (-.5, .5)]
        blocks(kit, f'Hull aft arm {side} bolts', bolts, kit.steel)


def aft_well(kit):
    """The recessed well between the arms aft of the spine: a gunmetal deck,
    two louvre banks and the sprite's blue-grey brackets."""
    z = AFT_DECK
    edge = lambda x: arm_y(x) - ARM_HALF - .25
    xf = SPINE_AFT - .25
    plan = [(xf, -edge(xf)), (-35.2, -edge(-35.2)), (-35.2, edge(-35.2)), (xf, edge(xf))]
    prism(kit, 'Hull aft well deck', plan, [(0, z-.03), (0, z+.1)], kit.steel)
    for s, side in SIDES:
        y0, y1 = sorted((s*2.4, s*8.6))
        louvre(kit, f'Hull aft well louvre {side}', -33.4, -26.6, y0, y1, z+.1, 9)
        b0, b1 = sorted((s*9.3, s*11.0))
        f0, f1 = sorted((s*1.2, s*11.0))
        blocks(kit, f'Hull aft well bracket {side}',
               [(-33.8, -25.0, b0, b1, z, z+.85), (-34.9, -33.8, f0, f1, z, z+.85)], kit.blue, .06)
        blocks(kit, f'Hull aft well bracket {side} bolts',
               [bolt(x, s*10.15, z+.85, .22, .1) for x in (-25.6, -27.6, -29.6, -31.6, -33.4)] +
               [bolt(-34.35, s*y, z+.85, .22, .1) for y in (2.0, 4.5, 7.0)], kit.steel)


# --- wing body and aft block ----------------------------------------------

def wing_body(kit, wing):
    """Pass 3 refit: the gun sponsons keep plate seams, a wall service cover
    and a deck cover; the spine fairing behind them takes the sprite's
    grey-blue vents (X -3..2) in recessed pockets and two plate seams."""
    skirt = (-8.6, -5.4)                         # the sponson wall's vertical band
    grooves, cores = [], []
    for s, side in SIDES:
        for x0, x1, z0, z1 in [(9.43, 9.77, *skirt), (16.6, 20.4, -6.6, -6.25)]:
            g, c = wall_groove(s, x0, x1, 26.0, z0, z1)
            grooves.append(g)
            cores.append(c)
        # Leading-edge face strake.
        y0, y1 = sorted((s*11.3, s*18.6))
        grooves.append((21.55, 22.6, y0, y1, -6.6, -6.25))
        cores.append((21.55, 21.65, y0, y1, -6.58, -6.27))
        grooves.append(wall_groove(s, 11.38, 14.62, 26.0, -8.42, -5.48, .3)[0])
    cut(kit, wing, grooves)
    blocks(kit, 'Hull wing seam cores', cores, kit.dark)
    deck = WING_DECK + .2
    for s, side in SIDES:
        y0, y1 = sorted((s*25.7, s*25.92))
        bx(kit, f'Hull wing service cover {side}', 11.5, 14.5, y0, y1, -8.3, -5.6, kit.steel, .05)
        blocks(kit, f'Hull wing service cover {side} bolts',
               [ybolt(x, s*25.92, z, .22, s*.1) for x in (11.8, 14.2) for z in (-8.0, -5.9)], kit.steel)
        # Bolted octagonal service cover forward of the sponson ring.
        x, y = 18.3, s*14.0
        kit.cylinder(f'Hull wing deck service cover {side}', (x, y, deck+.08), 1.2, .16, kit.steel, 'Z', 8, .04)
        blocks(kit, f'Hull wing deck service cover {side} bolts',
               [bolt(x+.9*math.cos(a), y+.9*math.sin(a), deck+.16, .16, .08)
                for a in (j*math.tau/8+math.pi/8 for j in range(8))], kit.steel)
    # Spine fairing: a flat-floored vent pocket each side of the crown, and
    # transverse plate seams down its deck and flank.
    pockets = []
    for s, side in SIDES:
        y0, y1 = sorted((s*12.2, s*16.8))
        pockets.append((FAIRING_VENT[0], FAIRING_VENT[1], y0, y1, FAIRING_VENT_FLOOR, SPINE_DECK + 2))
        for xs in (-8.5, -14.4):
            pockets.append((xs - .1, xs + .1, *sorted((s*11.6, s*27.0)), -3.0, SPINE_DECK + 2))
    cut(kit, kit.hull, pockets)
    for s, side in SIDES:
        y0, y1 = sorted((s*12.2, s*16.8))
        louvre(kit, f'Hull fairing vent {side}', *FAIRING_VENT, y0, y1, FAIRING_VENT_FLOOR, 6)


def aft_block(kit, aft):
    grooves, cores = [], []
    for s, _ in SIDES:
        for x0, x1, wall, z0, z1 in [(-22.42, -22.08, 20.5, -3.8, 3.6), (-26.17, -25.83, 21.0, -3.8, 3.6),
                                     (-23.3, -21.2, 20.5, 1.0, 1.35), (-26.8, -25.2, 21.0, 1.0, 1.35),
                                     (-33.8, -30.2, 21.0, 1.0, 1.35)]:
            g, c = wall_groove(s, x0, x1, wall, z0, z1)
            grooves.append(g)
            cores.append(c)
        grooves.append(wall_groove(s, -33.72, -30.38, 21.0, -3.52, .52, .3)[0])
    cut(kit, aft, grooves)
    blocks(kit, 'Hull aft block seam cores', cores, kit.dark)
    items = []
    for x, e in ((-22.25, 20.5), (-26.0, 21.0), (-32.0, 21.0)):
        items += [(p, x-.15, x+.15) for p in both(band([(e-1.7, AFT_DECK), (e-1.5, AFT_DECK), (e, 4.0)], -.06, .05))]
    extrude_many(kit, 'Hull aft block shoulder seams', items, kit.dark)
    for s, side in SIDES:
        y0, y1 = sorted((s*20.7, s*20.92))
        bx(kit, f'Hull aft block service cover {side}', -33.6, -30.5, y0, y1, -3.4, .4, kit.steel, .05)
        blocks(kit, f'Hull aft block service cover {side} bolts',
               [ybolt(x, s*20.92, z, .22, s*.1) for x in (-33.3, -30.8) for z in (-3.1, .1)], kit.steel)
        # Octagonal deck cover outboard of the arm.
        x, y = -21.5, s*17.0
        kit.cylinder(f'Hull aft deck service cover {side}', (x, y, AFT_DECK+.08), 1.1, .16, kit.steel, 'Z', 8, .04)
        blocks(kit, f'Hull aft deck service cover {side} bolts',
               [bolt(x+.82*math.cos(a), y+.82*math.sin(a), AFT_DECK+.16, .16, .08)
                for a in (j*math.tau/8+math.pi/8 for j in range(8))], kit.steel)


def tie_downs(kit):
    """Padeyes for cargo straps and handling lines."""
    pads, verts, faces = [], [], []
    spots = [(19.0, 12.6, WING_DECK + .2), (-18.6, 18.4, AFT_DECK), (-29.5, 18.7, AFT_DECK)]
    for s, _ in SIDES:
        for x, y, z in spots:
            pads.append((x-.45, x+.45, s*y-.45, s*y+.45, z-.02, z+.12))
            # A ring standing in the XZ plane: 12 x 6 torus.
            R, r, M, N = .3, .08, 12, 6
            b = len(verts)
            for i in range(M):
                a = math.tau*i/M
                for j in range(N):
                    t = math.tau*j/N
                    d = R + r*math.cos(t)
                    verts.append((x + d*math.cos(a), s*y + r*math.sin(t), z + .45 + d*math.sin(a)))
            faces += [(b+i*N+j, b+i*N+(j+1) % N, b+((i+1) % M)*N+(j+1) % N, b+((i+1) % M)*N+j)
                      for i in range(M) for j in range(N)]
    blocks(kit, 'Hull tie-down pads', pads, kit.steel)
    fix_normals(kit.mesh('Hull tie-down rings', verts, faces, kit.steel))


def build_details(kit):
    kit.blue = material('Hull grey-blue side block', (.36, .40, .47), .35, .5)
    spine_top(kit)
    spine_sides(kit)
    cheek_fairings(kit)
    horseshoe_arms(kit)
    aft_well(kit)
    wing_body(kit, bpy.data.objects['Wing body'])
    aft_block(kit, bpy.data.objects['Aft drive block'])
    tie_downs(kit)


def build(kit):
    build_shell(kit)
    build_wing_body(kit)
    if os.environ.get('HERMES_HULL_DETAILS', '1') == '0':   # shape-only review renders
        return
    build_details(kit)
