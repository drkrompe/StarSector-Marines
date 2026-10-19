"""Colossus hull: the forward cargo body and its longerons (the waist and aft body are cl_aft).

The hull is broken into named modules along the joints the sprite draws, so a
later condition state can tear them apart cleanly: the central cargo hold, two
pale longerons, four side cargo modules (fore and aft, port and starboard, split
at the seam the sprite shows at X 33), the hold's hatch cover. cl_aft holds the keel spine through the waist, the aft machinery body
under the bridge, and the open lattice frames.

Planform is read off colossus.png; heights are proposed art. The sprite sits
1.5 units to port of the .ship centre (its engines are at +49..64 and
-46..-61), so every part is symmetric about Y = YC.
"""
import math
import random
import bpy
import cl_kit
from cl_kit import prism, section, boolean
from mathutils import Vector

YC = 1.5            # the sprite's own centreline, in hull Y
FRONT = 90.5        # bow face of the cargo body
HOLD_AFT = -19.0    # aft face of the cargo body
HOLD_Y = 36.0       # central hold half-width
LONGERON_Y = (36.0, 46.0)
SIDE_Y = (46.0, 88.0)
SIDE_SPLIT = 33.0   # seam between fore and aft side modules
# Owner, pass 3 round 1: a thick, massive cargo front that the drive pods push like tugs.
# The block is deepened below the pass-2 deck, so every deck fitting keeps its seat.
HOLD_KEEL, HOLD_DECK = -50.0, 18.0
SIDE_KEEL, SIDE_DECK = -46.0, 14.0
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
                 section(HOLD_KEEL, HOLD_DECK, 8, 3), kit.red)
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
                     section(SIDE_KEEL, SIDE_DECK, 7, 3), kit.maroon)
        aft = prism(kit, f'Cargo {side} aft module',
                    plan([(SIDE_SPLIT-.6, a), (SIDE_SPLIT-.6, b), (-12, b), (-20, b-9), (-20, a)], s),
                    section(SIDE_KEEL, SIDE_DECK, 7, 3), kit.maroon)
        kit.hull += [fore, aft]
    detail(kit)


# --------------------------------------------------------------------------
# Pass 2: plausible detail on the cargo body, read off colossus.png.
#
# Nothing here changes the planform: plates and fittings stand on the tops,
# and everything on the side walls and the bow face sits in recesses cut
# into them, so the top silhouette is the blockout's. Grooves are shallow
# boolean cuts (dark), so the scale solids stay closed. The turret rings and
# flare launchers of cl_mounts seat on clear patches of the module tops.

WALL = SIDE_Y[1]                    # outboard wall of the side modules (rel y)
HATCH_TOP = HOLD_DECK+2.5           # top of the hatch cover
LAMP_X = (61.5, 54.5, 47.5, 39.0, 32.0, 24.5, 15.5)   # the sprite's hatch-edge lamps
LAMP_REL = 34.2
CRANE_X, CRANE_REL = 64.5, 24.5     # the sprite's crane pedestals
LEAF_SEAMS = (24.5, 35.5, 46.0)     # transverse panel seams on the two leaves
PLATFORM_AFT = 57.0                 # fixed crane platform forward of the leaves
FORE_SEAMS, AFT_SEAMS = (47.0, 60.0, 79.0), (-8.0, 14.0, 26.0)
WALL_SEAMS = {'fore': (36.6, 60.0, 66.0, 79.0), 'aft': (-8.0, 1.5, 26.0, 29.5)}
# (x0, x1) of the cargo door and of the personnel hatch on each module's outboard wall
DOORS = {'fore': ((40.5, 55.5), (71.4, 74.4)), 'aft': ((7.0, 22.0), (-6.4, -3.4))}


class Parts:
    """Many closed shells gathered into one mesh object."""

    def __init__(self):
        self.v, self.f, self.shells = [], [], []

    def _add(self, verts, faces):
        o = len(self.v)
        self.shells.append((o, len(verts), len(self.f), len(faces)))
        self.v += [tuple(p) for p in verts]
        self.f += [tuple(i+o for i in fc) for fc in faces]
        return self

    def box(self, x0, x1, y0, y1, z0, z1):
        x0, x1 = sorted((x0, x1))
        y0, y1 = sorted((y0, y1))
        z0, z1 = sorted((z0, z1))
        verts = [(x, yy, z) for x in (x0, x1) for yy in (y0, y1) for z in (z0, z1)]
        return self._add(verts, [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1),
                                 (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)])

    def rbox(self, x0, x1, r0, r1, z0, z1, s):
        """Box given in rel y on one side."""
        return self.box(x0, x1, y(r0, s), y(r1, s), z0, z1)

    def slab(self, poly, ext):
        """A planar polygon swept along ext."""
        n = len(poly)
        top = [(p[0]+ext[0], p[1]+ext[1], p[2]+ext[2]) for p in poly]
        faces = [tuple(range(n)), tuple(range(n, 2*n))]
        faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
        return self._add(list(poly)+top, faces)

    def cyl(self, c, r, h, axis='Z', n=12):
        """Cylinder from c along +axis by h."""
        pts = []
        for i in range(n):
            a = 2*math.pi*(i+.5)/n
            u, w = r*math.cos(a), r*math.sin(a)
            pts.append({'X': (c[0], c[1]+u, c[2]+w), 'Y': (c[0]+u, c[1], c[2]+w),
                        'Z': (c[0]+u, c[1]+w, c[2])}[axis])
        ext = {'X': (h, 0, 0), 'Y': (0, h, 0), 'Z': (0, 0, h)}[axis]
        return self.slab(pts, ext)

    def beam(self, p0, p1, w, h):
        """Rectangular beam w wide and h deep from p0 to p1."""
        p0, p1 = Vector(p0), Vector(p1)
        d = p1-p0
        side = d.cross(Vector((0, 0, 1)))
        if side.length < 1e-6:
            side = Vector((1, 0, 0))
        side = side.normalized()*w/2
        up = side.cross(d).normalized()*h/2
        if up.z < 0:
            up = -up
        quad = [p0-side-up, p0+side-up, p0+side+up, p0-side+up]
        return self.slab(quad, d)

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.v, self.f, mat, bevel) if self.v else None


def obj(name):
    return bpy.data.objects[name]


def _cut(kit, target, parts, mat):
    """Subtract gathered cutter shells from target; the new faces take mat.

    Shells that overlap (crossing seams) would make one self-intersecting cutter,
    which the MANIFOLD solver turns into torn, wrongly-coloured keel faces, so
    they are split into passes whose shells are pairwise disjoint."""
    passes = []                                   # [(Parts, [aabb...])]
    for o, nv, fo, nf in parts.shells:
        vs = parts.v[o:o+nv]
        lo = [min(p[i] for p in vs) for i in range(3)]
        hi = [max(p[i] for p in vs) for i in range(3)]
        for g, boxes in passes:
            if not any(all(lo[i] < b1[i] and b0[i] < hi[i] for i in range(3)) for b0, b1 in boxes):
                break
        else:
            g, boxes = Parts(), []
            passes.append((g, boxes))
        boxes.append((lo, hi))
        g._add(vs, [tuple(i-o for i in fc) for fc in parts.f[fo:fo+nf]])
    for g, _ in passes:
        boolean(target, g.build(kit, 'Temporary cargo body cutter', mat))


def _hazard(p, u0, u1, z0, z1, band, face, depth, s, along='X'):
    """Diagonal hazard stripes (gathered into p) over the border band of an opening.

    The opening is u0..u1 by z0..z1 on a face whose outward normal is +rel y
    (along='X': the outboard wall at rel `face`) or +X (along='Y': the bow face
    at X `face`, u then in model Y). Stripes stand `depth` proud of `face`."""
    pitch, w = 1.6, .55

    def put(u, z):
        return (u, y(face, s), z) if along == 'X' else (face, u, z)
    ext = (0, s*depth, 0) if along == 'X' else (depth, 0, 0)
    ou0, ou1, oz0, oz1 = u0-band, u1+band, z0-band, z1+band
    for za, zb in ((oz0, z0), (z1, oz1)):          # top and bottom bands
        u = ou0
        while u+w+band <= ou1:
            p.slab([put(u, za), put(u+w, za), put(u+w+band, zb), put(u+band, zb)], ext)
            u += pitch
    for ua, ub in ((ou0, u0), (u1, ou1)):          # the two side bands
        z = z0
        while z+w+band <= z1:
            p.slab([put(ua, z), put(ub, z+band), put(ub, z+band+w), put(ua, z+w)], ext)
            z += pitch


def crane(kit, side, s):
    """A deck crane at the hatch platform's forward corner, its L-shaped jib folded aft into a rest cradle."""
    yc = y(CRANE_REL, s)
    z0 = kit.surface(CRANE_X, yc)[0].z
    n = f'Hull {side} deck crane'
    Parts().cyl((CRANE_X, yc, z0), 3.0, .5, 'Z', 18).cyl((CRANE_X, yc, z0), 2.2, 3.6, 'Z', 16).build(
        kit, f'{n} pedestal', kit.steel, .06)
    Parts().cyl((CRANE_X, yc, z0+3.6), 2.6, .3, 'Z', 18).build(kit, f'{n} slewing ring', kit.dark)
    zh = z0+3.9
    Parts().rbox(61.8, 67.6, CRANE_REL-2.3, CRANE_REL+2.3, zh, zh+3.5, s).build(
        kit, f'{n} slewing house', kit.pale, .3)
    Parts().rbox(67.5, 69.1, CRANE_REL-1.6, CRANE_REL+1.6, zh+.2, zh+1.6, s).build(
        kit, f'{n} counterweight', kit.steel, .15)
    Parts().rbox(67.6, 67.8, CRANE_REL-1.4, CRANE_REL+1.4, zh+2.0, zh+3.0, s).build(
        kit, f'{n} cab window', kit.dark)
    # L-shaped jib: inner boom aft along the hatch, a knuckle, the outer boom angled outboard.
    zj = zh+3.9
    knee = (52.0, yc, zj)
    tip = (47.2, y(CRANE_REL+4.0, s), zj-.2)
    jib = Parts()
    jib.box(61.2, 63.4, yc-.8, yc+.8, zh+3.5, zj+.4)          # heel on the house roof
    jib.beam((63.0, yc, zj), knee, 1.3, 1.5)
    jib.beam(knee, tip, 1.0, 1.2)
    jib.build(kit, f'{n} jib', kit.orange, .12)
    Parts().cyl((knee[0], knee[1], zj-1.0), .95, 2.0, 'Z', 12).cyl((tip[0], tip[1], tip[2]-.7), .6, 1.4, 'Z', 10).build(
        kit, f'{n} jib knuckle', kit.steel)
    hook = Parts()
    hook.beam((tip[0], tip[1], tip[2]-.6), (tip[0], tip[1]+.001, z0+5.0), .12, .12)   # the fall
    hook.box(tip[0]-.5, tip[0]+.5, tip[1]-.5, tip[1]+.5, z0+3.7, z0+5.0)
    hook.box(tip[0]-.15, tip[0]+.15, tip[1]-.3, tip[1]+.3, z0+2.9, z0+3.7)
    hook.build(kit, f'{n} hook block', kit.steel)
    # Rest cradle on the leaf under the outer jib.
    cr = CRANE_REL+4.0*(52.0-49.5)/(52.0-47.2)
    cradle = Parts()
    for r in (cr-1.25, cr+1.25):
        cradle.rbox(49.25, 49.75, r-.25, r+.25, z0, zj-.8, s)
    cradle.rbox(48.9, 50.1, cr-1.5, cr+1.5, zj-1.1, zj-.6, s)
    cradle.rbox(48.6, 50.4, cr-1.8, cr+1.8, z0, z0+.3, s)
    cradle.build(kit, f'{n} rest cradle', kit.steel, .05)
    # The winch the sprite draws inboard of the pedestal.
    w = Parts().rbox(61.6, 64.4, 16.6, 20.4, z0, z0+.8, s)
    w.cyl((63.0, y(17.0, s) if s > 0 else y(20.0, s), z0+2.0), 1.1, 3.0, 'Y', 14)
    w.build(kit, f'{n} winch', kit.steel, .05)


def hatch(kit):
    """Two leaves hinged at their outboard edges, a fixed crane platform ahead of them."""
    lid = obj('Hull hold hatch cover')
    t = HATCH_TOP
    across = Parts()
    for x in (PLATFORM_AFT,)+LEAF_SEAMS:
        across.box(x-.25, x+.25, y(34.5, 1), y(34.5, -1), t-.55, t+1)
    across.box(70-.22, 70+.22, y(13, 1), y(13, -1), t-.45, t+1)    # platform plate seam
    _cut(kit, lid, across, kit.dark)
    along = Parts()
    along.box(12, PLATFORM_AFT-.3, YC-.3, YC+.3, t-.6, t+1)       # the two leaves meet here
    for s in (1, -1):
        along.rbox(12, PLATFORM_AFT-.3, 16.8, 17.25, t-.45, t+1, s)
    _cut(kit, lid, along, kit.dark)

    pins, knuckles = Parts(), Parts()
    for side, s in SIDES:
        pins.cyl((14.0, y(30.6, s), t+.15), .32, 42.5, 'X', 10)
        for x in (16.0, 21.0, 28.0, 32.5, 39.0, 43.0, 49.5, 54.0):
            knuckles.cyl((x-1.1, y(30.6, s), t+.15), .62, 2.2, 'X', 10)
    pins.build(kit, 'Hull hold hatch hinge pins', kit.steel)
    knuckles.build(kit, 'Hull hold hatch hinge knuckles', kit.steel, .06)
    # Dogs: clamps along the centre seam, over the leaves' aft edge and across the platform seam.
    dogs = Parts()
    for x in (16.5, 21.5, 29.5, 41.0, 51.0, 55.0):
        dogs.box(x-.55, x+.55, YC-1.0, YC+1.0, t, t+.5)
    for s in (1, -1):
        for r in (4.0, 17.5, 26.0):
            dogs.rbox(12.0, 14.8, r-.6, r+.6, HOLD_DECK, t+.45, s)
        for r in (8.0, 26.0):
            dogs.rbox(PLATFORM_AFT-.9, PLATFORM_AFT+.9, r-.5, r+.5, t, t+.45, s)
    dogs.build(kit, 'Hull hold hatch dogs', kit.steel, .06)
    eyes = Parts()
    for s in (1, -1):
        for x in (19.0, 30.0, 41.0, 51.5):
            eyes.rbox(x-.6, x+.6, 8.2, 9.2, t, t+.2, s)
            eyes.slab([(x-.5, y(8.6, s), t+.2), (x+.5, y(8.6, s), t+.2),
                       (x+.25, y(8.6, s), t+.9), (x-.25, y(8.6, s), t+.9)], (0, .22, 0))
    eyes.build(kit, 'Hull hold hatch lifting eyes', kit.steel)

    # Side coamings the cover seats on, carrying the sprite's rows of edge lamps.
    for side, s in SIDES:
        Parts().rbox(13.0, 79.6, 33.05, 35.3, 15.2, 19.5, s).build(
            kit, f'Hull {side} hatch side coaming', kit.steel, .1)
        lamps, housings = Parts(), Parts()
        for x in LAMP_X:
            housings.rbox(x-.8, x+.8, LAMP_REL-.75, LAMP_REL+.75, 19.5, 19.8, s)
            lamps.rbox(x-.5, x+.5, LAMP_REL-.45, LAMP_REL+.45, 19.8, 20.15, s)
        housings.build(kit, f'Hull {side} hatch lamp housings', kit.dark)
        lamps.build(kit, f'Hull {side} hatch edge lamps', kit.lamp)


def coaming_band(kit):
    """The forward coaming band across the hold ahead of the hatch, with vents and two deck hatches."""
    Parts().box(79.4, 88.0, y(32.4, 1), y(32.4, -1), HOLD_DECK-.6, HOLD_DECK+1.8).build(
        kit, 'Hull forward coaming band', kit.maroon, .2)
    top = HOLD_DECK+1.8
    vents, louvres = Parts(), Parts()
    for r in (-13.5, -6.0, 6.0, 13.5):
        vents.box(84.0, 87.4, YC+r-1.7, YC+r+1.7, top, top+1.2)
        for x in (84.6, 85.5, 86.4):
            louvres.box(x, x+.45, YC+r-1.3, YC+r+1.3, top+1.2, top+1.28)
    vents.build(kit, 'Hull forward coaming vents', kit.pale, .12)
    louvres.build(kit, 'Hull forward coaming vent louvres', kit.dark)
    rims, lids = Parts(), Parts()
    for r in (-3.0, 3.0):
        rims.cyl((81.7, YC+r, top), 1.05, .35, 'Z', 14)
        lids.cyl((81.7, YC+r, top+.35), .78, .18, 'Z', 14)
    rims.build(kit, 'Hull forward deck hatch rims', kit.steel)
    lids.build(kit, 'Hull forward deck hatch lids', kit.pale)


def hold_aft(kit):
    """The coaming knees aft of the hatch (the sprite's pale blocks) and the hold's aft deck seams."""
    knees = Parts()
    for s in (1, -1):
        z = kit.surface(6, y(12, s))[0].z
        for r0, r1 in ((9.0, 14.5), (21.5, 28.0)):
            knees.rbox(1.0, 12.0, r0, r1, z, z+2.4, s)
    knees.build(kit, 'Hull hold aft coaming knees', kit.pale, .4)
    seams = Parts()
    for x in (-4.0, -12.0):
        seams.box(x-.25, x+.25, y(33.5, 1), y(33.5, -1), HOLD_DECK-.5, HOLD_DECK+1)
    _cut(kit, obj('Hull central cargo hold'), seams, kit.dark)


def bow_face(kit):
    """The hold's flat bow face: a two-leaf forward cargo door in a recess, framed by armour plates."""
    hold = obj('Hull central cargo hold')
    f = FRONT
    door_r, dz0, dz1, band = 20.5, -36.0, 10.5, 1.0
    _cut(kit, hold, Parts().box(f-.5, f+3, YC-door_r-band, YC+door_r+band, dz0-band, dz1+band), kit.steel)
    fx = f-.5
    frame = Parts()
    frame.box(fx, fx+.35, YC-door_r-band, YC+door_r+band, dz1, dz1+band)
    frame.box(fx, fx+.35, YC-door_r-band, YC+door_r+band, dz0-band, dz0)
    frame.box(fx, fx+.35, YC-door_r-band, YC-door_r, dz0, dz1)
    frame.box(fx, fx+.35, YC+door_r, YC+door_r+band, dz0, dz1)
    frame.build(kit, 'Hull bow cargo door hazard frame', kit.orange)
    stripes = Parts()
    _hazard(stripes, YC-door_r, YC+door_r, dz0, dz1, band, fx+.35, .1, 1, along='Y')
    stripes.build(kit, 'Hull bow cargo door hazard stripes', kit.dark)
    leaves = Parts()
    for a, b in ((YC-door_r, YC-.25), (YC+.25, YC+door_r)):
        leaves.box(fx, fx+.3, a, b, dz0, dz1)
    leaves.build(kit, 'Hull bow cargo door leaves', kit.red, .1)
    ribs = Parts()
    for z in (-30.0, -23.0, -16.0, -9.0, -2.0, 5.0):
        for a, b in ((YC-door_r+.8, YC-1.0), (YC+1.0, YC+door_r-.8)):
            ribs.box(fx+.3, fx+.45, a, b, z-.35, z+.35)
    for a in (YC-1.0, YC+.6):
        ribs.box(fx+.3, fx+.48, a, a+.4, dz0+1, dz1-1)    # locking bars either side of the split
    ribs.build(kit, 'Hull bow cargo door stiffeners', kit.steel)
    plates = Parts()
    for a, b in ((-33.0, -11.3), (-10.7, 10.7), (11.3, 33.0)):
        plates.box(f, f+.5, YC+a, YC+b, 12.0, 14.6)
    for s in (1, -1):
        for z0, z1 in ((-40.0, -26.3), (-25.7, -16.6), (-16.0, -2.3), (-1.7, 11.4)):
            plates.rbox(f, f+.5, 22.2, 33.0, z0, z1, s)
    plates.box(f, f+.5, YC-21.6, YC+21.6, -39.8, -37.7)
    plates.build(kit, 'Hull bow armour plates', kit.red, .25)


def longerons(kit):
    """Box girders: a wide top flange, web stiffeners, lightening holes, splices and end plates."""
    a, b = LONGERON_Y
    top = HOLD_DECK+4
    for side, s in SIDES:
        n = f'Hull {side} longeron'
        Parts().rbox(HOLD_AFT-.6, FRONT-2.4, a-.7, b+.7, top, top+.8, s).build(kit, f'{n} top flange', kit.pale, .15)
        stiff = Parts()
        x = HOLD_AFT+4
        while x < FRONT-4:
            if abs(x-SIDE_SPLIT) > 1.5:
                stiff.rbox(x-.45, x+.45, b-.1, b+.6, 11.0, 20.2, s)
                stiff.rbox(x-.45, x+.45, a-.6, a+.1, 15.6, 20.2, s)
            x += 7.0
        stiff.build(kit, f'{n} web stiffeners', kit.pale, .08)
        holes = Parts()
        x = HOLD_AFT+7.5
        while x < FRONT-6:
            if abs(x-SIDE_SPLIT) > 3:
                holes.cyl((x, y(b-1.2, s) if s > 0 else y(b+1.8, s), 16.2), 1.5, 3.0, 'Y', 14)
            x += 7.0
        _cut(kit, obj(n), holes, kit.dark)
        splice, bolts = Parts(), Parts()
        for x in (5.0, 33.0, 61.0):
            splice.rbox(x-1.3, x+1.3, a-.9, b+.9, top+.8, top+1.1, s)
            for dx in (-.75, .75):
                for r in (a+.5, a+3.5, a+6.5, b-.5):
                    bolts.cyl((x+dx, y(r, s), top+1.1), .22, .15, 'Z', 6)
        splice.build(kit, f'{n} splice plates', kit.steel, .05)
        bolts.build(kit, f'{n} splice bolts', kit.steel)
        Parts().rbox(FRONT-2.6, FRONT+.5, a-.8, b+.8, HOLD_KEEL+2.5, top+1.0, s).build(
            kit, f'{n} bow end plate', kit.steel, .3)
        knee = [(HOLD_AFT+2, a+1), (HOLD_AFT+2, b-1), (-36, 30), (-36, 22)]
        Parts().slab([(x, y(r, s), 9.0) for x, r in knee], (0, 0, .6)).build(
            kit, f'{n} aft knee flange', kit.pale, .08)
        fx = 68.0 if s > 0 else 82.0
        Parts().rbox(fx-1, fx+1, 40, 42, top+.8, top+1.6, s).build(kit, f'{n} deck fitting', kit.steel, .1)


def _door(kit, n, s, dx0, dx1, dz0, dz1, band, w, low):
    """A two-leaf cargo door in its wall recess, with hazard frame and stiffeners."""
    frame = Parts()
    frame.rbox(dx0-band, dx1+band, w-.5, w-.15, dz1, dz1+band, s)
    frame.rbox(dx0-band, dx1+band, w-.5, w-.15, dz0-band, dz0, s)
    frame.rbox(dx0-band, dx0, w-.5, w-.15, dz0, dz1, s)
    frame.rbox(dx1, dx1+band, w-.5, w-.15, dz0, dz1, s)
    frame.build(kit, f'{n}{low} cargo door hazard frame', kit.orange)
    stripes = Parts()
    _hazard(stripes, dx0, dx1, dz0, dz1, band, w-.15, .08, s)
    stripes.build(kit, f'{n}{low} cargo door hazard stripes', kit.dark)
    mid = (dx0+dx1)/2
    leaves, ribs = Parts(), Parts()
    for a, b in ((dx0, mid-.2), (mid+.2, dx1)):
        leaves.rbox(a, b, w-.5, w-.25, dz0, dz1, s)
        for z in (dz0+3.5, dz1-3.5):
            ribs.rbox(a+.6, b-.6, w-.25, w-.12, z-.3, z+.3, s)
    ribs.rbox(mid-.7, mid+.7, w-.25, w-.1, (dz0+dz1)/2-.8, (dz0+dz1)/2+.8, s)   # lock box
    leaves.build(kit, f'{n}{low} cargo door leaves', kit.maroon, .08)
    ribs.build(kit, f'{n}{low} cargo door stiffeners', kit.steel)


# Owner, pass 3 round 2: the hull is a thin wrapper round stacked cargo containers.
# The sprite's six grey slots on each fore module's bow face are open container
# bays: a thin hull lip round a recess, a steel cell-guide grid on the slot lines,
# and in each cell a stack of standard 40 ft boxes end-on (2.44 x 2.59 m = 4.9 x
# 5.2 units at 0.50 m/u), two across and four high, in mixed liveries.
BAY_COLS = (47.0, 58.3, 69.7, 81.0)      # cell edges, rel y (the sprite's three slot columns)
BAY_ROWS = (-38.0, -14.0, 10.0)          # cell edges, z (its two slot rows)
# The aft faces (X -20, rel 46..79) are narrower: three cells on a 10.8 pitch with lighter
# posts and the boxes 0.2 apart; the drive pod's thrust yoke bears on the second post.
AFT_BAY_COLS = (46.3, 57.1, 67.9, 78.7)
BAY_DEPTH = 7.0                          # recess behind the bow face
BOX_W, BOX_H, BOX_GAP = 4.9, 5.2, .3     # container end, units
LIVERIES = (('rust orange', (.42, .15, .05)), ('faded blue', (.11, .21, .34)), ('green', (.11, .23, .13)),
            ('grey', (.30, .30, .31)), ('cream', (.55, .49, .38)), ('oxblood', (.28, .06, .05)),
            ('teal', (.09, .26, .26)))
_LIVERY_MATS = {}


def _livery(name, rgb):
    if name not in _LIVERY_MATS:
        _LIVERY_MATS[name] = cl_kit.material(f'Container livery {name}', rgb, .35, .7)
    return _LIVERY_MATS[name]


def container_bays(kit, n, s, f, d, cols, guide_name, post=.6, gap=BOX_GAP, thrust_post=None):
    """Open a module end face into container bays and fill them (see BAY_COLS).

    f is the face's X and d its outward direction (+1 the bow, -1 aft); cols are the cell
    edges in rel y. thrust_post (rel y) names the post the drive pod's thrust yoke bears
    on, carried flush with the face."""
    back = f-d*BAY_DEPTH
    lip = f-d*.3
    xs = lambda u0, u1: (min(u0, u1), max(u0, u1))
    lo, hi = cols[0]-post, cols[-1]+post
    _cut(kit, obj(n), Parts().rbox(*xs(back, f+d*2), lo, hi, BAY_ROWS[0]-.6, BAY_ROWS[-1]+.6, s), kit.dark)
    Parts().rbox(*xs(back-d*.3, back+d*.2), lo, hi, BAY_ROWS[0]-.6, BAY_ROWS[-1]+.6, s).build(
        kit, f'{n} container bay back wall', kit.dark)
    # Cell guides: posts on the slot lines and shelves between the rows.
    guides = Parts()
    for r in cols:
        heavy = r == thrust_post                    # carried flush to the face for the yoke's shoe
        guides.rbox(*xs(back, f if heavy else lip), r-post, r+post, BAY_ROWS[0]-.6, BAY_ROWS[-1]+.6, s)
    for z in BAY_ROWS:
        guides.rbox(*xs(back, lip), lo, hi, z-.6, z+.6, s)
    guides.build(kit, guide_name, kit.steel, .1)
    rng = random.Random(f'{n} containers')
    bodies, seams, bars, corners = {}, Parts(), Parts(), Parts()
    face = f-d*2.4                                  # door plane, set back inside the lip
    door = xs(face, face+d*.04)
    for c0, c1 in zip(cols, cols[1:]):
        w0 = (c0+c1)/2-(2*BOX_W+gap)/2
        for z0, z1 in zip(BAY_ROWS, BAY_ROWS[1:]):
            h0 = (z0+z1)/2-(4*BOX_H+3*BOX_GAP)/2
            for i in range(2):
                for k in range(4):
                    a = w0+i*(BOX_W+gap)
                    zb = h0+k*(BOX_H+BOX_GAP)
                    name, rgb = rng.choice(LIVERIES)
                    bodies.setdefault(name, (rgb, Parts()))[1].rbox(*xs(back+d*.2, face), a, a+BOX_W, zb, zb+BOX_H, s)
                    m = a+BOX_W/2
                    seams.rbox(*door, m-.06, m+.06, zb+.5, zb+BOX_H-.5, s)                      # leaf split
                    seams.rbox(*door, a+.3, a+BOX_W-.3, zb+BOX_H-.55, zb+BOX_H-.45, s)          # header
                    for u in (.18, .38, .62, .82):                                              # lock bars
                        r = a+BOX_W*u
                        bars.rbox(*xs(face, face+d*.14), r-.07, r+.07, zb+.35, zb+BOX_H-.35, s)
                        for zz in (zb+1.6, zb+BOX_H-1.6):                                     # handles
                            bars.rbox(*xs(face, face+d*.2), r-.07, r+.32 if u < .5 else r-.32, zz-.06, zz+.06, s)
                    for ca in (a, a+BOX_W-.45):                                                 # corner castings
                        for cz in (zb, zb+BOX_H-.45):
                            corners.rbox(*xs(face-d*.05, face+d*.08), ca, ca+.45, cz, cz+.45, s)
    for name, (rgb, parts) in sorted(bodies.items()):
        parts.build(kit, f'{n} containers {name}', _livery(name, rgb), .06)
    seams.build(kit, f'{n} container door seams', kit.dark)
    bars.build(kit, f'{n} container lock bars', kit.pale)
    corners.build(kit, f'{n} container corner castings', kit.steel)


def module(kit, side, s, part):
    """One side cargo module: panel bands, covers, tie-downs, end caps, wall doors and seams."""
    n = f'Cargo {side} {part} module'
    t = SIDE_DECK
    fore = part == 'fore'
    wall0, wall1 = (SIDE_SPLIT+.6, FRONT-5) if fore else (-12.0, SIDE_SPLIT-.6)
    tx0, tx1 = (SIDE_SPLIT+3.6, 84.0) if fore else (-17.0, SIDE_SPLIT-3.6)
    seams = FORE_SEAMS if fore else AFT_SEAMS
    (dx0, dx1), (px0, px1) = DOORS[part]
    band = 1.0
    pz0, pz1 = -7.0, -2.2

    # The sprite's panel bands (blue inboard, orange outboard of it), broken at the seams.
    blue, orange = Parts(), Parts()
    edges = [tx0]+[v for x in seams for v in (x-.6, x+.6)]+[tx1]
    for a, b in zip(edges[::2], edges[1::2]):
        blue.rbox(a, b, 50.0, 55.0, t, t+.3, s)
        orange.rbox(a, b, 55.8, 62.5, t, t+.3, s)
    blue.build(kit, f'{n} blue panel band', kit.blue, .08)
    orange.build(kit, f'{n} orange panel band', kit.orange, .08)

    # Bolted service covers and tie-downs, clear of the turret and launcher seats.
    covers, bolts, eyes = Parts(), Parts(), Parts()
    for cx, cr in (((40.0, 77.0), (52.0, 77.0)) if fore else ((19.5, 77.0),)):
        covers.rbox(cx-2.5, cx+2.5, cr-2.0, cr+2.0, t, t+.3, s)
        for bx in (-2.0, 0.0, 2.0):
            for br in (-1.5, 1.5):
                bolts.cyl((cx+bx, y(cr+br, s), t+.3), .22, .15, 'Z', 6)
    for ex in ((45.0, 58.0, 80.0) if fore else (-6.0, 12.0, 28.0)):
        eyes.rbox(ex-.5, ex+.5, 83.5, 84.5, t, t+.3, s)
        eyes.slab([(ex-.45, y(84.0, s), t+.3), (ex+.45, y(84.0, s), t+.3),
                   (ex+.25, y(84.0, s), t+1.0), (ex-.25, y(84.0, s), t+1.0)], (0, .25, 0))
    covers.build(kit, f'{n} service covers', kit.steel, .1)
    bolts.build(kit, f'{n} cover bolts', kit.pale)
    eyes.build(kit, f'{n} tie-downs', kit.steel)

    # Darker end caps: the sprite's dark trapezoids at the bow and stern ends.
    cap, vents = Parts(), Parts()
    if fore:
        cap.rbox(84.0, FRONT-.4, 48.2, 77.0, t, t+.45, s)
        for r in (52.0, 58.0, 64.0, 70.0):
            vents.rbox(85.5, 88.0, r-1.4, r+1.4, t+.45, t+.7, s)
        container_bays(kit, n, s, FRONT, 1, BAY_COLS, f'{n} bow armour')
    else:
        cap.rbox(-17.0, -11.0, 48.2, 76.0, t, t+.45, s)
        for x in (-15.6, -14.0, -12.4):
            vents.rbox(x-.35, x+.35, 50.0, 74.0, t+.45, t+.55, s)
        container_bays(kit, n, s, HOLD_AFT-1, -1, AFT_BAY_COLS, f'{n} aft end cap', post=.4, gap=.2,
                       thrust_post=AFT_BAY_COLS[1])
    cap.build(kit, f'{n} {"bow" if fore else "aft"} cap plate', kit.steel, .12)
    vents.build(kit, f'{n} cap vents', kit.pale if fore else kit.dark, .05)

    # Doors on the outboard wall. Their contents sit inside recesses, never proud of the wall.
    w = WALL
    for dz0, dz1, low in ((-8.0, 6.0, ''), (-28.0, -14.0, ' lower')):
        _door(kit, n, s, dx0, dx1, dz0, dz1, band, w, low)
    hframe = Parts()
    hframe.rbox(px0, px1, w-.5, w-.15, pz1, pz1+.4, s).rbox(px0, px1, w-.5, w-.15, pz0-.4, pz0, s)
    hframe.rbox(px0, px0+.4, w-.5, w-.15, pz0, pz1, s).rbox(px1-.4, px1, w-.5, w-.15, pz0, pz1, s)
    hframe.build(kit, f'{n} personnel hatch frame', kit.orange)
    Parts().rbox(px0+.4, px1-.4, w-.5, w-.3, pz0, pz1, s).build(kit, f'{n} personnel hatch', kit.pale, .05)
    Parts().rbox(px0+.9, px1-.9, w-.3, w-.2, pz1-1.5, pz1-.8, s).build(kit, f'{n} personnel hatch port', kit.dark)

    # Grooves: transverse seams over the top, the longitudinal seam, wall seams with the
    # door recesses, then the wall strata.
    mod = obj(n)
    g = Parts()
    for x in seams:
        g.rbox(x-.27, x+.27, 46.2, WALL-.55, t-.55, t+1, s)
    _cut(kit, mod, g, kit.dark)
    _cut(kit, mod, Parts().rbox(tx0-1.5, tx1, 65.75, 66.25, t-.55, t+1, s), kit.dark)
    g = Parts()
    for x in WALL_SEAMS[part]:
        g.rbox(x-.27, x+.27, WALL-.55, WALL+2, SIDE_KEEL-2, t+1, s)
    for dz0, dz1 in ((-8.0, 6.0), (-28.0, -14.0)):
        g.rbox(dx0-band, dx1+band, WALL-.5, WALL+2, dz0-band, dz1+band, s)
    g.rbox(px0, px1, WALL-.5, WALL+2, pz0-.4, pz1+.4, s)
    _cut(kit, mod, g, kit.steel)
    g = Parts()
    for z in (8.9, -10.9, -30.9, -39.5):
        g.rbox(wall0+.5, wall1-.5, WALL-.5, WALL+2, z-.25, z+.25, s)
    _cut(kit, mod, g, kit.dark)


def belly(kit):
    """Keel seams and armour strakes under the deep cargo block (owner, pass 3 round 1)."""
    hold = obj('Hull central cargo hold')
    z = HOLD_KEEL
    g = Parts()
    for x in (-4.0, 13.0, 35.5, 57.0, 79.0):
        g.box(x-.3, x+.3, y(HOLD_Y-8, 1), y(HOLD_Y-8, -1), z-1, z+.6)
    _cut(kit, hold, g, kit.dark)          # crossing seams in one cutter overlap and break the boolean
    g = Parts()
    for r in (-17.0, 0.0, 17.0):
        g.box(HOLD_AFT+8, FRONT-8, YC+r-.3, YC+r+.3, z-1, z+.6)
    _cut(kit, hold, g, kit.dark)
    strakes = Parts()
    for r0, r1 in ((-15.0, -2.0), (2.0, 15.0)):
        for x0, x1 in ((13.6, 34.9), (36.1, 56.4)):        # the end bays take the ventral hatches
            strakes.box(x0, x1, YC+r0, YC+r1, z-.5, z+.2)
    strakes.build(kit, 'Hull keel armour strakes', kit.steel, .2)
    for side, s in SIDES:
        for part, seams in (('fore', FORE_SEAMS), ('aft', AFT_SEAMS)):
            g = Parts()
            for x in seams:
                g.rbox(x-.3, x+.3, SIDE_Y[0]+7, SIDE_Y[1]-7, SIDE_KEEL-1, SIDE_KEEL+.6, s)
            _cut(kit, obj(f'Cargo {side} {part} module'), g, kit.dark)
            g = Parts()
            g.rbox(SIDE_SPLIT+8 if part == 'fore' else HOLD_AFT+8, FRONT-8 if part == 'fore' else SIDE_SPLIT-8,
                   66.7, 67.3, SIDE_KEEL-1, SIDE_KEEL+.6, s)
            _cut(kit, obj(f'Cargo {side} {part} module'), g, kit.dark)


def aft_bulkhead(kit):
    """The hold's aft face between the drive pods, below the waist: a plated thrust bulkhead."""
    hold = obj('Hull central cargo hold')
    f = HOLD_AFT
    g = Parts()
    for r in (-24.0, -12.0, 0.0, 12.0, 24.0):                          # vertical plate seams
        g.box(f-1, f+.6, YC+r-.25, YC+r+.25, HOLD_KEEL+8, -16.5)
    _cut(kit, hold, g, kit.dark)
    g = Parts()
    for z in (-24.0, -33.0):                                             # horizontal strata
        g.box(f-1, f+.6, YC-HOLD_Y+.5, YC+HOLD_Y-.5, z-.25, z+.25)
    _cut(kit, hold, g, kit.dark)
    g = Parts().box(f-1, f+1.1, YC-5.5, YC+5.5, -39.0, -27.5)             # service hatch recess, deeper than the seams
    _cut(kit, hold, g, kit.dark)
    Parts().box(f+.3, f+.7, YC-5.0, YC+5.0, -38.5, -28.0).build(kit, 'Hull aft bulkhead service hatch', kit.steel, .1)
    dogs = Parts()
    for u in (-3.5, 0.0, 3.5):
        for z in (-38.0, -28.5):
            dogs.box(f, f+.3, YC+u-.4, YC+u+.4, z-.2, z+.2)
    for z in (-36.5, -30.0):
        dogs.box(f, f+.3, YC-4.6, YC-4.2, z-.4, z+.4).box(f, f+.3, YC+4.2, YC+4.6, z-.4, z+.4)
    dogs.build(kit, 'Hull aft bulkhead hatch dogs', kit.pale)
    # Two thrust frames: the heavy vertical ribs that take the waist's loads into the bulkhead.
    ribs = Parts()
    for r in (-17.0, 17.0):
        ribs.box(f-1.4, f+.2, YC+r-1.2, YC+r+1.2, HOLD_KEEL+9, -16.0)
    ribs.build(kit, 'Hull aft bulkhead thrust frames', kit.steel, .15)


def docking(kit):
    """Under the keel: two ventral cargo hatches and four docking clamp hardpoints, for loading
    at a station or a freight lighter (owner, pass 3 round 4)."""
    hold = obj('Hull central cargo hold')
    z = HOLD_KEEL
    hatches = ((-9.0, 9.0), (59.0, 77.0))                               # between the strakes' seams
    g = Parts()
    for x0, x1 in hatches:
        g.box(x0-.6, x1+.6, YC-11.6, YC+11.6, z-1, z+1.1)     # deeper than the keel seams
    _cut(kit, hold, g, kit.dark)
    leaves, frame, stripes = Parts(), Parts(), Parts()
    for x0, x1 in hatches:
        for a, b in ((YC-11.0, YC-.15), (YC+.15, YC+11.0)):
            leaves.box(x0, x1, a, b, z+.6, z+.9)
        for zz in (-.05,):
            frame.box(x0-1.4, x1+1.4, YC-12.4, YC-11.6, z+zz-.25, z+zz)
            frame.box(x0-1.4, x1+1.4, YC+11.6, YC+12.4, z+zz-.25, z+zz)
            frame.box(x0-1.4, x0-.6, YC-11.6, YC+11.6, z+zz-.25, z+zz)
            frame.box(x1+.6, x1+1.4, YC-11.6, YC+11.6, z+zz-.25, z+zz)
        u = x0-1.4
        while u < x1+1.0:
            for r in (-12.4, 11.6):
                stripes.box(u, u+.5, YC+r, YC+r+.8, z-.32, z-.27)
            u += 1.6
    leaves.build(kit, 'Hull ventral cargo hatch leaves', kit.maroon, .08)
    frame.build(kit, 'Hull ventral cargo hatch frames', kit.orange)
    stripes.build(kit, 'Hull ventral cargo hatch hazard stripes', kit.dark)
    # Docking clamp hardpoints: a bolted base, a clamp head and two jaws, on the side-module keels.
    base, head, bolts = Parts(), Parts(), Parts()
    zk = SIDE_KEEL
    for side, s in SIDES:
        for x in (-2.0, 70.0):
            c = y(67.0, s)
            base.box(x-3.0, x+3.0, c-3.0, c+3.0, zk-.6, zk+.2)
            head.box(x-1.6, x+1.6, c-1.6, c+1.6, zk-2.4, zk-.6)
            for dx in (-1.9, 1.9):
                head.box(x+dx-.35, x+dx+.35, c-1.2, c+1.2, zk-3.4, zk-1.6)
            for bx in (-2.4, 2.4):
                for by in (-2.4, 2.4):
                    bolts.box(x+bx-.25, x+bx+.25, c+by-.25, c+by+.25, zk-.85, zk-.6)
    base.build(kit, 'Hull docking clamp bases', kit.steel, .15)
    head.build(kit, 'Hull docking clamp heads', kit.dark, .1)
    bolts.build(kit, 'Hull docking clamp bolts', kit.pale)


def fenders(kit):
    """Fender rails along the outboard walls: the berthing strakes a freighter lies against a
    station with, carried on stand-off brackets over the thin wall skin (owner, pass 3 round 4)."""
    rails, brackets = Parts(), Parts()
    w = SIDE_Y[1]
    for side, s in SIDES:
        for z in (-11.0, -36.0):                  # between the two door rows, and below them
            for x0, x1 in ((SIDE_SPLIT+1.2, FRONT-6.5), (-11.0, SIDE_SPLIT-1.2)):
                rails.rbox(x0, x1, w+.25, w+.85, z-.6, z+.6, s)
                x = x0+2.0
                while x < x1-1.0:
                    brackets.rbox(x-.4, x+.4, w-.1, w+.3, z-.9, z+.9, s)
                    x += 6.0
    rails.build(kit, 'Cargo fender rails', kit.dark, .2)
    brackets.build(kit, 'Cargo fender rail brackets', kit.steel)


def detail(kit):
    # Everything seated by ray-cast is placed before any groove is cut.
    for side, s in SIDES:
        crane(kit, side, s)
    hatch(kit)
    coaming_band(kit)
    hold_aft(kit)
    bow_face(kit)
    longerons(kit)
    for side, s in SIDES:
        for part in ('fore', 'aft'):
            module(kit, side, s, part)
    belly(kit)
    aft_bulkhead(kit)
    docking(kit)
    fenders(kit)
