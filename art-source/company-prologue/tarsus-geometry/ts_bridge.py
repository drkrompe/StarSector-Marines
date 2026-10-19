"""Tarsus command module: the dark block that stands forward of the central
body between the two arms (sprite rows 75-81, X 43-50, two orange lamps on
its forward edge), read as the bridge looking out down the gap between the
prongs.

Pass 2 (about 0.51 m per unit, a 1.8 m person is 3.5 units): an armoured cab.
The raked face over the nose carries a recessed, mullioned five-pane
windscreen with a sill, under a horizontal armoured brow on gussets; the two
brow ends carry the sprite's two orange lamps. Each side wall has three
recessed ports under eyebrows and a bolted service cover; the walls, nose and
roof are plated, with skin-deep seams and a strata groove (kit.cut, so
'Bridge command module' stays a closed solid). The narrow roof carries a
railed walkway with a dorsal access hatch, a tapered sensor mast with a yard,
whips and the masthead lamp, and a dish on a pedestal facing forward.

New parts are named 'Bridge ...'."""
import math
from mathutils import Vector
from ts_kit import prism, section, plane_cut, facet_normal, cut, Parts
import ts_hull as H

FRONT, AFT = 51.5, 33.0
HALF = 4.6
KEEL, DECK = -4.0, 13.0
RAKE = 50.0                      # windscreen face, degrees below horizontal
RAKE_TOP = FRONT-3.5             # where the raked face meets the roof

_A = math.radians(RAKE)
N = Vector((math.sin(_A), 0, math.cos(_A)))      # outward normal of the raked face
T = Vector((-math.cos(_A), 0, math.sin(_A)))     # up the raked face
Z_FOOT = DECK-(FRONT-RAKE_TOP)*math.tan(_A)      # where the raked face meets the nose
SCREEN = (.8, 3.3)                               # windscreen foot / head, distance up the face
SCREEN_HALF = 2.9


def _fp(s, rel, off):
    """A point on the raked face: s up the slope, rel across, off along its normal."""
    return Vector((FRONT, H.YC+rel, Z_FOOT))+T*s+N*off


def _face_box(parts, s0, s1, r0, r1, o0, o1):
    """A box aligned to the raked face."""
    verts = [tuple(_fp(s, r, o)) for s in (s0, s1) for r in (r0, r1) for o in (o0, o1)]
    return parts._add(verts, [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)])


def _frame(axis):
    axis = Vector(axis).normalized()
    s = Vector((0, 0, 1)) if abs(axis.z) < .9 else Vector((1, 0, 0))
    u = (s-axis*s.dot(axis)).normalized()
    return axis, u, axis.cross(u)


def rev(parts, c, axis, loop, n=16):
    """Closed (d, r) outline revolved about the line through c along axis."""
    a, u, w = _frame(axis)
    c = Vector(c)
    verts = []
    for d, r in loop:
        verts += [tuple(c+a*d+u*(r*math.cos(2*math.pi*i/n))+w*(r*math.sin(2*math.pi*i/n))) for i in range(n)]
    m = len(loop)
    faces = []
    for k in range(m):
        j = (k+1) % m
        faces += [(k*n+i, k*n+(i+1) % n, j*n+(i+1) % n, j*n+i) for i in range(n)]
    return parts._add(verts, faces)


def _rail(parts, pts, z0, height=2.1, pitch=2.3):
    """Stanchions along a polyline [(x, y), ...] on z0, with a top and a knee rail."""
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        a, b = Vector((ax, ay, z0)), Vector((bx, by, z0))
        k = max(1, math.ceil((b-a).length/pitch))
        for i in range(k+1):
            p = a+(b-a)*(i/k)
            parts.beam(p, p+Vector((0, 0, height)), .18, .18)
        for h in (height, height*.5):
            parts.beam(a+Vector((0, 0, h)), b+Vector((0, 0, h)), .16, .16)


def build(kit):
    cab = prism(kit, 'Bridge command module',
                H.plan([(FRONT, HALF-1.2), (FRONT-1.5, HALF), (AFT, HALF)]),
                section(KEEL, DECK, 2, 2.5), kit.steel)
    # A raked windscreen face over the nose, bounded to the cab's front.
    plane_cut(cab, 'command module windscreen rake', (RAKE_TOP, H.YC, DECK), facet_normal((1, 0), RAKE))

    # --- Skin-deep grooves: plate seams, a strata groove, window and port recesses. ---
    g = Parts()
    for s in (1, -1):
        wall = H.YC+s*HALF
        for x in (36.5, 40.4, 49.0):                               # vertical plate seams
            g.box(x-.16, x+.16, wall-s*.3, wall+s*.5, KEEL+1.5, DECK-2.7)
        g.box(AFT-1, FRONT+1, wall-s*.3, wall+s*.5, 6.6, 6.95)      # strata groove
        for x in (42.6, 44.8, 47.0):                               # side ports
            g.box(x-.75, x+.75, wall-s*.32, wall+s*.5, 8.0, 9.4)
    g.box(FRONT-.3, FRONT+1, H.YC-5, H.YC+5, 6.6, 6.95)             # the strata round the nose
    g.box(38.4, 38.7, H.YC-5, H.YC+5, DECK-.3, DECK+1)             # roof plate seam
    g.box(38.9, RAKE_TOP+.2, H.YC-.15, H.YC+.15, DECK-.3, DECK+1)  # the sprite's dark spine line
    _face_box(g, SCREEN[0], SCREEN[1], -SCREEN_HALF, SCREEN_HALF, -.42, .8)   # windscreen recess
    cut(kit, cab, g, kit.dark)
    kit.hull.append(cab)

    # --- Windscreen: glazing at the floor of the recess, mullions and sill flush with the face. ---
    glass = Parts()
    _face_box(glass, SCREEN[0], SCREEN[1], -SCREEN_HALF, SCREEN_HALF, -.44, -.32)
    for s in (1, -1):
        wall = H.YC+s*HALF
        for x in (42.6, 44.8, 47.0):
            glass.box(x-.75, x+.75, wall-s*.34, wall-s*.22, 8.0, 9.4)
    glass.build(kit, 'Bridge glazing', kit.glass)
    frame = Parts()
    for k in range(1, 5):
        r = -SCREEN_HALF+2*SCREEN_HALF*k/5
        _face_box(frame, SCREEN[0], SCREEN[1], r-.12, r+.12, -.4, .02)
    _face_box(frame, SCREEN[0]-.3, SCREEN[0]+.05, -SCREEN_HALF-.3, SCREEN_HALF+.3, -.4, .35)   # sill ledge
    for e in (-1, 1):                                                                    # side jambs
        _face_box(frame, SCREEN[0]-.3, SCREEN[1]+.1, e*SCREEN_HALF-.15, e*(SCREEN_HALF+.35), -.1, .2)
    frame.build(kit, 'Bridge windscreen frame', kit.grey)

    # --- Armoured brow over the windscreen, on gussets; the sprite's two lamps at its ends. ---
    def face_x(z):
        return RAKE_TOP+(DECK-z)/math.tan(_A)
    zb0, zb1 = Z_FOOT+math.sin(_A)*SCREEN[1]+.08, Z_FOOT+math.sin(_A)*SCREEN[1]+.5
    brow = Parts()
    brow.box(face_x(zb1)-.6, face_x(zb0)+1.25, H.YC-3.75, H.YC+3.75, zb0, zb1)
    for e in (-1, 1):
        yy = H.YC+e*3.45
        tri = [(face_x(zb0)-.05, yy-.13, zb0), (face_x(zb0)+1.1, yy-.13, zb0), (face_x(zb0-.95)+.05, yy-.13, zb0-.95)]
        brow.slab(tri, (0, .26, 0))
    brow.build(kit, 'Bridge windscreen brow', kit.steel, .06)
    lamps = Parts()
    for e in (-1, 1):
        lamps.cyl((face_x(zb0)+1.2, H.YC+e*3.25, (zb0+zb1)/2), .2, .25, 'X', 10)
    lamps.build(kit, 'Bridge brow lamps', kit.lamp)

    # --- Side ports' eyebrows, side service covers and the nose cover, with bolts. ---
    plate, bolts = Parts(), Parts()
    for s in (1, -1):
        wall = H.YC+s*HALF
        for x in (42.6, 44.8, 47.0):
            plate.box(x-.95, x+.95, wall-s*.05, wall+s*.32, 9.45, 9.72)
        plate.box(43.2, 47.4, wall-s*.05, wall+s*.18, 1.0, 4.6)
        for bx in (43.5, 45.3, 47.1):
            for bz in (1.3, 4.3):
                bolts.cyl((bx, wall+s*.18, bz), .13, s*.12, 'Y', 6)
        # A conduit run down the wall from the strata to the keel chamfer.
        plate.box(41.6, 41.95, wall-s*.05, wall+s*.4, KEEL+2, 6.5)
    plate.box(FRONT-.05, FRONT+.2, H.YC-1.7, H.YC+1.7, 1.0, 4.8)
    for by in (-1.4, 0, 1.4):
        for bz in (1.3, 4.5):
            bolts.cyl((FRONT+.2, H.YC+by, bz), .13, .12, 'X', 6)
    plate.build(kit, 'Bridge service covers', kit.grey, .04)
    bolts.build(kit, 'Bridge cover bolts', kit.dark)

    # --- Roof: walkway rails, access hatch, mast, dish. ---
    roof = kit.surface(40.0, H.YC+1.0)[0].z
    rails = Parts()
    for s in (1, -1):
        _rail(rails, [(AFT+1.0, H.YC+s*1.85), (RAKE_TOP-.4, H.YC+s*1.85)], roof)
    rails.build(kit, 'Bridge roof rails', kit.grey)
    hatch = Parts()
    hatch.box(34.4, 36.8, H.YC-1.2, H.YC+1.2, roof-.1, roof+.35)               # coaming
    hatch.box(34.55, 36.65, H.YC-1.05, H.YC+1.05, roof+.35, roof+.55)          # lid
    hatch.cyl((34.45, H.YC-.9, roof+.5), .16, 1.8, 'Y', 8)                     # hinge
    hatch.beam((36.3, H.YC-.5, roof+.6), (36.3, H.YC+.5, roof+.6), .14, .14)   # grab handle
    for e in (-1, 1):
        hatch.box(35.4, 35.8, H.YC+e*1.05, H.YC+e*1.3, roof+.3, roof+.5)       # dogs
    hatch.build(kit, 'Bridge dorsal access hatch', kit.grey, .03)

    mx = 42.0
    mast = Parts()
    mast.box(mx-.75, mx+.75, H.YC-.75, H.YC+.75, roof-.1, roof+.6)
    rev(mast, (mx, H.YC, roof+.6), (0, 0, 1), [(0, .01), (0, .4), (6.6, .2), (6.6, .01)], 10)
    mast.beam((mx, H.YC-1.6, roof+5.4), (mx, H.YC+1.6, roof+5.4), .18, .18)          # yard
    for e in (-1, 1):
        mast.beam((mx, H.YC+e*1.5, roof+5.4), (mx, H.YC+e*1.5, roof+7.6), .07, .07)  # whips
        mast.beam((mx, H.YC+e*.65, roof+.5), (mx, H.YC+e*.12, roof+3.6), .12, .12)   # stays
    mast.box(mx-.35, mx+.35, H.YC-.35, H.YC+.35, roof+3.4, roof+4.1)                 # radar housing
    mast.cyl((mx, H.YC, roof+7.55), .2, .2, 'Z', 8)                                   # lamp cap
    mast.build(kit, 'Bridge sensor mast', kit.grey, .02)
    head = Parts().cyl((mx, H.YC, roof+7.2), .2, .35, 'Z', 10)
    head.build(kit, 'Bridge masthead lamp', kit.lamp)

    dx, dy = 45.6, H.YC
    dish = Parts()
    dish.cyl((dx, dy, roof-.1), .5, .4, 'Z', 12)
    dish.cyl((dx, dy, roof+.3), .22, 1.0, 'Z', 8)
    dish.box(dx-.35, dx+.35, dy-.3, dy+.3, roof+1.15, roof+1.65)                     # elevation yoke
    axis = Vector((math.cos(math.radians(25)), 0, math.sin(math.radians(25))))
    c = Vector((dx+.2, dy, roof+1.9))
    rev(dish, c, axis, [(0, .01), (.12, .6), (.38, 1.15), (.28, 1.2), (-.02, .6), (-.28, .25), (-.28, .01)], 20)
    _, u, w = _frame(axis)
    horn = c+axis*1.05
    for k in range(3):
        a = 2*math.pi*k/3+.5
        rim = c+axis*.35+(u*math.cos(a)+w*math.sin(a))*1.05
        dish.beam(rim, horn, .06, .06)
    rev(dish, horn, axis, [(-.15, .01), (-.15, .14), (.15, .14), (.15, .01)], 8)
    dish.build(kit, 'Bridge sensor dish', kit.grey)
