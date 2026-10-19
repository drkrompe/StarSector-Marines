"""Colossus Mk.III hull: the forward cargo body and its longerons (the waist and aft body are c3_aft).

Forked from the accepted Colossus (colossus-geometry/cl_hull.py) and refitted to
colossus3_pirate.png: the side cargo modules are cut down to the sprite's narrower
walls (rel y 62.5, not 88; the guns' sponsons carry the outline beyond them), and
the forward half of the hatch cover is gone, replaced by the converted launch bay
(WS 017, the converted_bay hullmod) open to the deck and through the bow face.
The container-stack bow and aft faces stay, one cell per module end.

The hull is broken into named modules along the joints the sprite draws, so a
later condition state can tear them apart cleanly: the central cargo hold, two
pale longerons, four side cargo modules (fore and aft, port and starboard, split
at the seam the sprite shows at X 33), the hold's hatch cover. c3_aft holds the keel spine through the waist, the aft machinery body
under the bridge, and the open lattice frames.

Planform is read off colossus.png; heights are proposed art. The sprite sits
1.5 units to port of the .ship centre (its engines are at +49..64 and
-46..-61), so every part is symmetric about Y = YC.
"""
import math
import random
import bpy
import c3_kit
from c3_kit import prism, section, boolean
from mathutils import Vector

YC = 1.5            # the sprite's own centreline, in hull Y
FRONT = 90.5        # bow face of the cargo body
HOLD_AFT = -19.0    # aft face of the cargo body
HOLD_Y = 36.0       # central hold half-width
LONGERON_Y = (36.0, 46.0)
SIDE_Y = (46.0, 62.5)   # Mk.III: the sprite's walls at Y 63.5 / -61.5
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
    lid = prism(kit, 'Hull hold hatch cover', plan([(LID_FORE, 31), (LID_FORE-2, 33), (15, 33), (13, 31)]),
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
                     plan([(FRONT, a), (FRONT, b-2), (FRONT-2, b), (SIDE_SPLIT+.6, b), (SIDE_SPLIT+.6, a)], s),
                     section(SIDE_KEEL, SIDE_DECK, 7, 3), kit.maroon)
        aft = prism(kit, f'Cargo {side} aft module',
                    plan([(SIDE_SPLIT-.6, a), (SIDE_SPLIT-.6, b), (-17, b), (-20, b-3), (-20, a)], s),
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
# flare launchers of c3_mounts seat on clear patches of the module tops.

WALL = SIDE_Y[1]                    # outboard wall of the side modules (rel y)
HATCH_TOP = HOLD_DECK+2.5           # top of the hatch cover
LAMP_X = (39.0, 32.0, 24.5, 15.5)   # the hatch-edge lamps left aft of the launch bay
LAMP_REL = 34.2
LEAF_SEAMS = (24.5, 35.5, 46.0)     # transverse panel seams on the two leaves
LID_FORE = 43.0                     # fore edge of the surviving hatch cover
# The converted launch bay (WS 017 at X 52..69, Y -6..9): the sprite's dark red well from the
# bow face back to X 45.5, Y +-17 about the centreline; its floor 30 units (15 m) under the deck.
BAY_AFT, BAY_HALF, BAY_FLOOR = 46.0, 17.0, -12.0
FORE_SEAMS, AFT_SEAMS = (47.0, 60.0, 79.0), (-8.0, 14.0, 26.0)
WALL_SEAMS = {'fore': (36.6, 54.8, 67.2, 79.0), 'aft': (-8.0, 1.5, 25.0, 31.0)}
# (x0, x1) of the cargo door and of the personnel hatch on each module's outboard wall, between
# the gun sponsons (c3_mounts); the fore module's hatch went under the corner sponson.
DOORS = {'fore': ((56.0, 66.0), None), 'aft': ((9.0, 23.0), (26.5, 29.5))}


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

    def tube(self, cx, cy, z0, z1, r0, r1, wall, n=40):
        """Annular shell about a vertical axis: radius r0 at z0 to r1 at z1, `wall` thick."""
        vs = []
        for r, z in ((r0, z0), (r1, z1), (r1-wall, z1), (r0-wall, z0)):
            vs += [(cx+r*math.cos(2*math.pi*i/n), cy+r*math.sin(2*math.pi*i/n), z) for i in range(n)]
        fs = []
        for a in range(4):
            b = (a+1) % 4
            fs += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
        return self._add(vs, fs)

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


def hatch(kit):
    """Two leaves hinged at their outboard edges: the aft part of the Colossus's cover, cut off
    ahead of the second leaf seam where the launch bay begins (the cranes went with it)."""
    lid = obj('Hull hold hatch cover')
    t = HATCH_TOP
    across = Parts()
    for x in LEAF_SEAMS[:2]:
        across.box(x-.25, x+.25, y(34.5, 1), y(34.5, -1), t-.55, t+1)
    _cut(kit, lid, across, kit.dark)
    along = Parts()
    along.box(12, LID_FORE+1, YC-.3, YC+.3, t-.6, t+1)            # the two leaves meet here
    for s in (1, -1):
        along.rbox(12, LID_FORE+1, 16.8, 17.25, t-.45, t+1, s)
    _cut(kit, lid, along, kit.dark)

    pins, knuckles = Parts(), Parts()
    for side, s in SIDES:
        pins.cyl((14.0, y(30.6, s), t+.15), .32, LID_FORE-15.5, 'X', 10)
        for x in (16.0, 21.0, 28.0, 32.5, 39.0):
            knuckles.cyl((x-1.1, y(30.6, s), t+.15), .62, 2.2, 'X', 10)
    pins.build(kit, 'Hull hold hatch hinge pins', kit.steel)
    knuckles.build(kit, 'Hull hold hatch hinge knuckles', kit.steel, .06)
    # Dogs: clamps along the centre seam, over the leaves' aft edge and across the platform seam.
    dogs = Parts()
    for x in (16.5, 21.5, 29.5, 38.0):
        dogs.box(x-.55, x+.55, YC-1.0, YC+1.0, t, t+.5)
    for s in (1, -1):
        for r in (4.0, 17.5, 26.0):
            dogs.rbox(12.0, 14.8, r-.6, r+.6, HOLD_DECK, t+.45, s)
            dogs.rbox(LID_FORE-1.8, LID_FORE+1.0, r-.6, r+.6, HOLD_DECK, t+.45, s)
    dogs.build(kit, 'Hull hold hatch dogs', kit.steel, .06)
    eyes = Parts()
    for s in (1, -1):
        for x in (19.0, 30.0, 39.5):
            eyes.rbox(x-.6, x+.6, 8.2, 9.2, t, t+.2, s)
            eyes.slab([(x-.5, y(8.6, s), t+.2), (x+.5, y(8.6, s), t+.2),
                       (x+.25, y(8.6, s), t+.9), (x-.25, y(8.6, s), t+.9)], (0, .22, 0))
    eyes.build(kit, 'Hull hold hatch lifting eyes', kit.steel)

    # Side coamings the cover seats on, carrying the sprite's rows of edge lamps.
    for side, s in SIDES:
        Parts().rbox(13.0, LID_FORE+.6, 33.05, 35.3, 15.2, 19.5, s).build(
            kit, f'Hull {side} hatch side coaming', kit.steel, .1)
        lamps, housings = Parts(), Parts()
        for x in LAMP_X:
            housings.rbox(x-.8, x+.8, LAMP_REL-.75, LAMP_REL+.75, 19.5, 19.8, s)
            lamps.rbox(x-.5, x+.5, LAMP_REL-.45, LAMP_REL+.45, 19.8, 20.15, s)
        housings.build(kit, f'Hull {side} hatch lamp housings', kit.dark)
        lamps.build(kit, f'Hull {side} hatch edge lamps', kit.lamp)


BAY_POINTS = []


def launch_bay(kit):
    """The converted launch bay (WS 017, converted_bay): a deck-open well from X 46 to the bow,
    Y +-17, cut 30 units down into the hold and out through the bow face so fighters launch
    forward. The sprite's dark well with the red lamp rows down both rims and the launch
    rails along its floor: deck plating, two catapult tracks with shuttles, centreline and
    edge markings, ribbed walls with a gallery each side, a blast deflector and the hangar
    lift door in the aft wall, a heavy coaming round the rim and a hazard-framed bow mouth."""
    hold = obj('Hull central cargo hold')
    x0, x1, h, z0 = BAY_AFT, FRONT, BAY_HALF, BAY_FLOOR
    _cut(kit, hold, Parts().box(x0, x1+3, YC-h, YC+h, z0, HOLD_DECK+6), kit.steel)
    Parts().box(x0+.05, x1-.4, YC-h+.05, YC+h-.05, z0, z0+.35).build(kit, 'Launch bay deck plating', kit.steel, .05)
    fz = z0+.35
    seams = Parts()
    for x in range(int(x0)+6, int(x1), 6):
        seams.box(x-.06, x+.06, YC-h+.4, YC+h-.4, fz, fz+.03)
    seams.build(kit, 'Launch bay deck seams', kit.dark)
    tracks, shuttles, marks = Parts(), Parts(), Parts()
    for r in (-7.5, 7.5):
        tracks.box(x0+5, x1-1.0, YC+r-.55, YC+r+.55, fz, fz+.12)
        tracks.box(x0+5, x1-1.0, YC+r-.15, YC+r+.15, fz+.12, fz+.16)
        shuttles.box(x0+8.0, x0+10.4, YC+r-.9, YC+r+.9, fz+.12, fz+.75)
        shuttles.box(x0+10.4, x0+11.2, YC+r-.35, YC+r+.35, fz+.5, fz+1.3)      # tow bar
    tracks.build(kit, 'Launch bay catapult tracks', kit.dark)
    shuttles.build(kit, 'Launch bay catapult shuttles', kit.pale, .06)
    x = x0+14
    while x < x1-3:                                                            # dashed centreline
        marks.box(x, x+2.6, YC-.25, YC+.25, fz, fz+.04)
        x += 4.6
    for r in (-h+1.6, h-1.6):                                                  # deck edge lines
        marks.box(x0+3, x1-.6, YC+r-.18, YC+r+.18, fz, fz+.04)
    marks.build(kit, 'Launch bay deck markings', kit.pale)
    # Lining: the well's walls are plated in bare steel inside the hull's paint.
    lining = Parts()
    for s in (1, -1):
        lining.rbox(x0+.15, x1+.6, h-.2, h-.02, z0+.35, HOLD_DECK-.15, s)
    lining.box(x0+.02, x0+.2, YC-h+.2, YC+h-.2, z0+.35, HOLD_DECK-.15)
    lining.build(kit, 'Launch bay wall lining', kit.steel)
    # Walls: frames every 4 units, a gallery with a rail each side at z 2, lamp rows.
    ribs, gallery, rails, lamps = Parts(), Parts(), Parts(), Parts()
    for s in (1, -1):
        w = h-.05
        x = x0+2
        while x < x1-1:
            ribs.rbox(x-.35, x+.35, w-.6, w, z0+.35, HOLD_DECK-.4, s)
            x += 4.0
        gallery.rbox(x0+1, x1-1.5, w-2.4, w-.6, 1.6, 2.0, s)
        for x in range(int(x0)+3, int(x1)-1, 4):
            gallery.rbox(x-.2, x+.2, w-2.0, w-.6, .2, 1.6, s)                  # brackets
            rails.rbox(x-.08, x+.08, w-2.35, w-2.2, 2.0, 3.1, s)               # stanchions
        rails.rbox(x0+1, x1-1.5, w-2.35, w-2.2, 3.0, 3.15, s)
        rails.rbox(x0+1, x1-1.5, w-2.35, w-2.2, 2.5, 2.6, s)
        for x in range(int(x0)+3, int(x1)-1, 4):                               # rim lamps: the sprite's red dots
            lamps.rbox(x-.45, x+.45, w-.35, w, HOLD_DECK-1.6, HOLD_DECK-.9, s)
            lamps.rbox(x+1.6, x+2.4, w-.9, w-.6, z0+.4, z0+.75, s)            # floor edge lights
    ribs.build(kit, 'Launch bay wall frames', kit.steel, .05)
    gallery.build(kit, 'Launch bay galleries', kit.steel, .04)
    rails.build(kit, 'Launch bay gallery rails', kit.pale)
    lamps.build(kit, 'Launch bay red lamps', kit.bay_lamp)
    # Aft wall: the hangar lift door into the hold, hazard-framed, and a raised blast deflector ahead of it.
    Parts().box(x0+.2, x0+.5, YC-11, YC+11, z0+.35, 10.0).build(kit, 'Launch bay hangar lift door', kit.maroon, .05)
    ribs = Parts()
    for zz in (-8.0, -4.0, 0.0, 4.0, 8.0):
        ribs.box(x0+.5, x0+.7, YC-10.4, YC+10.4, zz-.25, zz+.25)
    ribs.box(x0+.5, x0+.72, YC-.2, YC+.2, z0+.8, 9.6)
    ribs.build(kit, 'Launch bay hangar lift door stiffeners', kit.steel)
    frame, stripes = Parts(), Parts()
    frame.box(x0+.2, x0+.42, YC-12, YC+12, 10.0, 11.0).box(x0+.2, x0+.42, YC-12, YC-11, z0+.35, 10)
    frame.box(x0+.2, x0+.42, YC+11, YC+12, z0+.35, 10)
    frame.build(kit, 'Launch bay hangar lift door frame', kit.orange)
    u = YC-12
    while u < YC+11.4:
        stripes.slab([(x0+.42, u, 10.0), (x0+.42, u+.55, 10.0), (x0+.42, u+1.55, 11.0), (x0+.42, u+1.0, 11.0)], (.08, 0, 0))
        u += 1.6
    stripes.build(kit, 'Launch bay hangar lift door hazard stripes', kit.dark)
    d = Parts()
    for r0, r1 in ((-11.0, -.3), (.3, 11.0)):
        d.slab([(x0+3.0, YC+r0, fz), (x0+3.0, YC+r1, fz), (x0+5.6, YC+r1, fz+2.9), (x0+5.6, YC+r0, fz+2.9)], (-.35, 0, 0))
    d.build(kit, 'Launch bay blast deflector', kit.steel, .05)
    rams = Parts()
    for r in (-8.0, -3.0, 3.0, 8.0):
        rams.beam((x0+2.2, YC+r, fz+.05), (x0+4.4, YC+r, fz+2.0), .35, .35)
    rams.build(kit, 'Launch bay blast deflector rams', kit.dark)
    # Rim coaming round the three deck edges, and the bow mouth's heavy frame.
    c = Parts()
    for s in (1, -1):
        c.rbox(x0-1.2, x1-1.0, h, h+1.2, HOLD_DECK-.4, HOLD_DECK+1.4, s)
    c.box(x0-1.2, x0, YC-h-1.2, YC+h+1.2, HOLD_DECK-.4, HOLD_DECK+1.4)
    c.build(kit, 'Launch bay rim coaming', kit.steel, .12)
    f, band = FRONT, 1.2
    mouth = Parts()
    for s in (1, -1):
        mouth.rbox(f-1.0, f+.6, h, h+band, z0-band, HOLD_DECK+1.4, s)
    mouth.box(f-1.0, f+.6, YC-h-band, YC+h+band, z0-band, z0)
    mouth.build(kit, 'Launch bay bow mouth frame', kit.orange)
    stripes = Parts()
    for s in (1, -1):
        z = z0
        while z < HOLD_DECK:
            stripes.slab([(f+.6, y(h, s), z), (f+.6, y(h, s), z+.55), (f+.6, y(h+band, s), z+1.55),
                          (f+.6, y(h+band, s), z+1.0)], (.08, 0, 0))
            z += 1.6
    u = YC-h-band
    while u < YC+h:
        stripes.slab([(f+.6, u, z0-band), (f+.6, u+.55, z0-band), (f+.6, u+1.55, z0), (f+.6, u+1.0, z0)], (.08, 0, 0))
        u += 1.6
    stripes.build(kit, 'Launch bay bow mouth hazard stripes', kit.dark)
    # Launch points: WS 017's four locations at the catapult shuttles' height, +X out of the mouth.
    BAY_POINTS.clear()
    for i, (px, py) in enumerate(((69, -5), (69, 9), (52, -6), (52, 9)), 1):
        loc = (px, py, fz+3.0)
        kit.empty(f'Launch bay WS 017 launch point {i}', loc, 0)
        BAY_POINTS.append(loc)


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
    """The hold's flat bow face: a two-leaf forward cargo door in a recess, framed by armour plates.

    Mk.III: the launch bay's mouth takes the top of the face, so the door is the lower
    part of the Colossus's (41 x 19 units) under it."""
    hold = obj('Hull central cargo hold')
    f = FRONT
    door_r, dz0, dz1, band = 20.5, -36.0, -17.0, 1.0
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
    for z in (-31.0, -26.5, -22.0):
        for a, b in ((YC-door_r+.8, YC-1.0), (YC+1.0, YC+door_r-.8)):
            ribs.box(fx+.3, fx+.45, a, b, z-.35, z+.35)
    for a in (YC-1.0, YC+.6):
        ribs.box(fx+.3, fx+.48, a, a+.4, dz0+1, dz1-1)    # locking bars either side of the split
    ribs.build(kit, 'Hull bow cargo door stiffeners', kit.steel)
    plates = Parts()
    for s in (1, -1):
        plates.rbox(f, f+.5, 22.2, 33.0, 12.0, 14.6, s)
        for z0, z1 in ((-40.0, -26.3), (-25.7, -16.6), (-16.0, -2.3), (-1.7, 11.4)):
            plates.rbox(f, f+.5, 22.2, 33.0, z0, z1, s)
    plates.box(f, f+.5, YC-21.6, YC+21.6, -15.6, -13.8)               # sill between door and bay mouth
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
BAY_COLS = (47.6, 59.6)                  # Mk.III: one cell per cut-down module end (the Colossus had three)
BAY_ROWS = (-38.0, -14.0, 10.0)          # cell edges, z (its two slot rows)
# The aft faces (X -20, rel 46..79) are narrower: three cells on a 10.8 pitch with lighter
# posts and the boxes 0.2 apart; the drive pod's thrust yoke bears on the second post.
AFT_BAY_COLS = (47.4, 58.2)
BAY_DEPTH = 7.0                          # recess behind the bow face
BOX_W, BOX_H, BOX_GAP = 4.9, 5.2, .3     # container end, units
LIVERIES = (('rust orange', (.42, .15, .05)), ('faded blue', (.11, .21, .34)), ('green', (.11, .23, .13)),
            ('grey', (.30, .30, .31)), ('cream', (.55, .49, .38)), ('oxblood', (.28, .06, .05)),
            ('teal', (.09, .26, .26)))
_LIVERY_MATS = {}


def _livery(name, rgb):
    if name not in _LIVERY_MATS:
        _LIVERY_MATS[name] = c3_kit.material(f'Container livery {name}', rgb, .35, .7)
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
    (dx0, dx1), hatch_x = DOORS[part]
    band = 1.0
    pz0, pz1 = -7.0, -2.2

    # The Colossus's blue and orange deck bands are gone under the pirates' paint (the sprite
    # draws none). Bolted service covers and tie-downs, clear of the gun sponsons and tubs.
    covers, bolts, eyes = Parts(), Parts(), Parts()
    for cx, cr in (((61.0, 55.5),) if fore else ()):
        covers.rbox(cx-2.5, cx+2.5, cr-2.0, cr+2.0, t, t+.3, s)
        for bx in (-2.0, 0.0, 2.0):
            for br in (-1.5, 1.5):
                bolts.cyl((cx+bx, y(cr+br, s), t+.3), .22, .15, 'Z', 6)
    for ex in ((57.0, 65.0) if fore else (8.5,)):
        eyes.rbox(ex-.5, ex+.5, 59.5, 60.5, t, t+.3, s)
        eyes.slab([(ex-.45, y(60.0, s), t+.3), (ex+.45, y(60.0, s), t+.3),
                   (ex+.25, y(60.0, s), t+1.0), (ex-.25, y(60.0, s), t+1.0)], (0, .25, 0))
    covers.build(kit, f'{n} service covers', kit.steel, .1)
    bolts.build(kit, f'{n} cover bolts', kit.pale)
    eyes.build(kit, f'{n} tie-downs', kit.steel)

    # Darker end caps: the sprite's dark trapezoids at the bow and stern ends.
    cap, vents = Parts(), Parts()
    if fore:
        cap.rbox(84.0, FRONT-.4, 48.2, 60.5, t, t+.45, s)
        container_bays(kit, n, s, FRONT, 1, BAY_COLS, f'{n} bow armour')
    else:
        cap.rbox(-17.0, -11.0, 48.2, 60.0, t, t+.45, s)
        for x in (-15.6, -14.0, -12.4):
            vents.rbox(x-.35, x+.35, 50.0, 59.0, t+.45, t+.55, s)
        container_bays(kit, n, s, HOLD_AFT-1, -1, AFT_BAY_COLS, f'{n} aft end cap', post=.4, gap=.2,
                       thrust_post=AFT_BAY_COLS[1])
    cap.build(kit, f'{n} {"bow" if fore else "aft"} cap plate', kit.steel, .12)
    if not fore:
        vents.build(kit, f'{n} cap vents', kit.dark, .05)

    # Doors on the outboard wall. Their contents sit inside recesses, never proud of the wall.
    w = WALL
    for dz0, dz1, low in ((-8.0, 6.0, ''), (-28.0, -14.0, ' lower')):
        _door(kit, n, s, dx0, dx1, dz0, dz1, band, w, low)
    if hatch_x:
        px0, px1 = hatch_x
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
    _cut(kit, mod, Parts().rbox(tx0-1.5, tx1, 54.25, 54.75, t-.55, t+1, s), kit.dark)
    g = Parts()
    for x in WALL_SEAMS[part]:
        g.rbox(x-.27, x+.27, WALL-.55, WALL+2, SIDE_KEEL-2, t+1, s)
    for dz0, dz1 in ((-8.0, 6.0), (-28.0, -14.0)):
        g.rbox(dx0-band, dx1+band, WALL-.5, WALL+2, dz0-band, dz1+band, s)
    if hatch_x:
        g.rbox(hatch_x[0], hatch_x[1], WALL-.5, WALL+2, pz0-.4, pz1+.4, s)
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
    for side, s in SIDES:
        for part, seams in (('fore', FORE_SEAMS), ('aft', AFT_SEAMS)):
            g = Parts()
            for x in seams:
                g.rbox(x-.3, x+.3, SIDE_Y[0]+7, SIDE_Y[1]-7, SIDE_KEEL-1, SIDE_KEEL+.6, s)
            _cut(kit, obj(f'Cargo {side} {part} module'), g, kit.dark)
            g = Parts()
            g.rbox(SIDE_SPLIT+8 if part == 'fore' else HOLD_AFT+8, FRONT-8 if part == 'fore' else SIDE_SPLIT-8,
                   53.7, 54.3, SIDE_KEEL-1, SIDE_KEEL+.6, s)
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
    # Hinge knuckles along each leaf's outboard edge, and stiffening ribs under the leaves.
    knuckles, ribs = Parts(), Parts()
    for x0, x1 in hatches:
        for edge in (YC-10.6, YC+10.6):
            u = x0+.6
            while u < x1-1.5:
                knuckles.cyl((u, edge, z+.95), .38, 1.6, 'X', 16)
                u += 4.1
        for u in [x0+2.25+3*k for k in range(6)]:
            for a, b in ((YC-10.4, YC-.8), (YC+.8, YC+10.4)):
                ribs.box(u-.3, u+.3, a, b, z+.4, z+.62)
    knuckles.build(kit, 'Hull ventral cargo hatch hinges', kit.steel, .05)
    ribs.build(kit, 'Hull ventral cargo hatch leaf ribs', kit.maroon, .08)
    # Docking clamp hardpoints on the side-module keels: a round bolted flange on a hazard ring,
    # a turned clamp body with a collar, a head plate, and two hooked jaws on pivot pins.
    base, head, bolts, jaws, pins, rings = Parts(), Parts(), Parts(), Parts(), Parts(), Parts()
    zk = SIDE_KEEL
    for side, s in SIDES:
        for x in (-2.0, 70.0):
            c = y(CLAMP_REL, s)
            rings.cyl((x, c, zk+.02), 4.1, -.1, 'Z', 40)
            base.cyl((x, c, zk+.2), 3.4, -.8, 'Z', 40)
            head.cyl((x, c, zk-.6), 2.2, -2.0, 'Z', 32)
            head.cyl((x, c, zk-1.1), 2.55, -.45, 'Z', 32)                # collar
            head.cyl((x, c, zk-2.6), 1.7, -.5, 'Z', 32)                 # head plate
            for k in range(10):
                a = 2*math.pi*(k+.5)/10
                bolts.cyl((x+2.85*math.cos(a), c+2.85*math.sin(a), zk-.6), .26, -.28, 'Z', 6)
            for sx in (-1, 1):
                hook = [(1.25, -1.6), (2.05, -1.6), (2.05, -4.0), (1.85, -4.5), (.75, -4.5), (.75, -3.9), (1.25, -3.9)]
                poly = [(x+sx*dx, c-.8, zk+dz) for dx, dz in hook]
                jaws.slab(poly if sx > 0 else poly[::-1], (0, 1.6, 0))
                pins.cyl((x+sx*1.3, c-1.2, zk-3.5), .32, 2.4, 'Y', 16)
    rings.build(kit, 'Hull docking clamp hazard rings', kit.orange)
    base.build(kit, 'Hull docking clamp bases', kit.steel, .12)
    head.build(kit, 'Hull docking clamp heads', kit.dark, .08)
    jaws.build(kit, 'Hull docking clamp jaws', kit.steel, .1)
    pins.build(kit, 'Hull docking clamp pivot pins', kit.pale, .04)
    bolts.build(kit, 'Hull docking clamp bolts', kit.pale)


# Ventral lift thrusters (owner, pass 3 round 6: "some kind of bottom thrust for lifting off
# the ground"): (cx, cy, well radius, keel z, keel object). Two large ones on the hold's
# centreline between the cargo hatches, and a pair across each side module's keel, clear of
# its seams and docking clamp.
# Mk.III: the cut-down modules have room for one per module, on the keel's middle line.
CLAMP_REL = 54.25
LIFT = [(24.5, YC, 7.0, HOLD_KEEL, 'Hull central cargo hold'),
        (46.5, YC, 7.0, HOLD_KEEL, 'Hull central cargo hold')]
for _side, _s in SIDES:
    for _part, _x in (('fore', 53.5), ('aft', 5.0)):
        LIFT.append((_x, y(CLAMP_REL, _s), 3.6, SIDE_KEEL, f'Cargo {_side} {_part} module'))
LIFT_ANCHORS = {}


def lift(kit):
    """Each thruster: a well cut up into the keel, a down-facing bell with a lip and cooling
    hoops and a dark throat, vectoring vanes across the well mouth, a bolted heat-shield
    collar proud of the keel and a hazard ring round it. 'Lift thruster <i> exhaust anchor'
    sits on the bell mouth with +X pointing down, the main drives' convention."""
    LIFT_ANCHORS.clear()
    bells, lips, throats, vanes, collars, rings, bolts = (Parts() for _ in range(7))
    for i, (cx, cy, R, k, host) in enumerate(LIFT, 1):
        _cut(kit, obj(host), Parts().cyl((cx, cy, k-1), R, 5.0, 'Z', 48), kit.dark)
        mouth, throat = k+1.4, k+3.6
        bells.tube(cx, cy, throat, mouth, .45*R, .86*R, .18*R/3.6, 48)
        lips.tube(cx, cy, mouth, mouth-.3, .9*R, .9*R, .2, 48)
        for t in (.35, .7):
            zz = throat+(mouth-throat)*t
            rr = .45*R+(.86-.45)*R*t+.08
            lips.tube(cx, cy, zz+.15, zz-.15, rr+.18, rr+.18, .2, 48)
        throats.cyl((cx, cy, throat-.05), .46*R, .45, 'Z', 32)
        pitch = R/3.0
        u = -R+pitch/2
        while u < R:
            half = math.sqrt(max(R*R-u*u, 0))-.15
            vanes.box(cx-half, cx+half, cy+u-.12, cy+u+.12, k+.15, k+1.05)
            u += pitch
        w = 2.2*R/7.0
        collars.tube(cx, cy, k+.02, k-.3, R+w, R+w, w, 48)
        collars.tube(cx, cy, k+.6, k-.45, R+.02, R+.02, .3, 48)       # lip lining the well edge
        rings.tube(cx, cy, k+.02, k-.06, R+w+.45, R+w+.45, .45, 48)
        for j in range(16 if R > 5 else 10):
            a = 2*math.pi*(j+.5)/(16 if R > 5 else 10)
            bolts.cyl((cx+(R+w/2)*math.cos(a), cy+(R+w/2)*math.sin(a), k-.3), .22, -.25, 'Z', 6)
        loc = (cx, cy, mouth-.3)
        ob = kit.empty(f'Lift thruster {i} exhaust anchor', loc, 0)
        ob.rotation_euler = (0, math.pi/2, 0)                           # +X points down
        LIFT_ANCHORS[i] = (loc, (0.0, 0.0, -1.0))
    bells.build(kit, 'Lift thruster bells', kit.steel, .05)
    lips.build(kit, 'Lift thruster lips and hoops', kit.pale, .04)
    throats.build(kit, 'Lift thruster dark throats', kit.dark)
    vanes.build(kit, 'Lift thruster vectoring vanes', kit.steel, .06)
    collars.build(kit, 'Lift thruster heat-shield collars', kit.steel, .08)
    rings.build(kit, 'Lift thruster hazard rings', kit.orange)
    bolts.build(kit, 'Lift thruster collar bolts', kit.pale)


def heat_tiles(kit):
    """Ceramic heat-shield tile fields round the lift thrusters, where their exhaust washes the
    keel on lift-off and touchdown: square tiles proud of the plating, on a 2.6-unit pitch,
    stopping at the collars, seams and docking clamps."""
    tiles = Parts()
    fields = [(13.6, 56.4, YC-17.2, YC+17.2, HOLD_KEEL)]          # between the hatches, inside the skids
    for side, s in SIDES:
        for x0, x1 in ((40.6, 65.4), (-9.4, 19.4)):
            a, b = sorted((y(47.4, s), y(61.2, s)))
            fields.append((x0, x1, a, b, SIDE_KEEL))
    keep_clear = [(cx, cy, R+2.2*R/7+.45) for cx, cy, R, k, h in LIFT]        # the hazard ring
    keep_clear += [(x, y(CLAMP_REL, s), 4.1) for _, s in SIDES for x in (-2.0, 70.0)]
    for x0, x1, a, b, k in fields:
        nx, ny = int((x1-x0)/2.6), int((b-a)/2.6)
        ox, oy = x0+(x1-x0-nx*2.6)/2, a+(b-a-ny*2.6)/2
        for i in range(nx):
            for j in range(ny):
                cx, cy = ox+2.6*(i+.5), oy+2.6*(j+.5)
                near = lambda px, py: math.hypot(max(abs(cx-px)-1.15, 0), max(abs(cy-py)-1.15, 0))
                if any(near(px, py) < pr+.25 for px, py, pr in keep_clear):
                    continue
                tiles.box(cx-1.15, cx+1.15, cy-1.15, cy+1.15, k-.2, k+.05)
    tiles.build(kit, 'Lift thruster heat-shield tiles', kit.tile, .12)


def skids(kit):
    """Landing runners along the hold keel, outboard of the hatches and lift thrusters: what
    the hull rests on when the lift thrusters set it down. Broad and flat-soled so the weight
    spreads along their whole length rather than onto a line (owner, pass 3 round 7: legs
    were rejected as more concentrated still): 8 units (4 m) wide, raked at both ends, a
    replaceable wear shoe, side stiffeners and bolted mounting blocks."""
    z = HOLD_KEEL
    beams, shoes, blocks, ribs = Parts(), Parts(), Parts(), Parts()
    for r in (-22.0, 22.0):
        c = YC+r
        x0, x1 = -12.0, 84.0
        prof = [(x0, z), (x0+4, z-2.4), (x1-4, z-2.4), (x1, z)]
        beams.slab([(px, c-4.0, pz) for px, pz in prof], (0, 8.0, 0))
        shoes.box(x0+4.4, x1-4.4, c-3.6, c+3.6, z-2.85, z-2.35)
        for k in range(1, 12):                                  # shoe segment joints
            bx = x0+4.4+k*(x1-x0-8.8)/12
            shoes.box(bx-.08, bx+.08, c-3.65, c+3.65, z-2.9, z-2.8)
        for bx in range(int(x0)+6, int(x1)-3, 8):
            blocks.box(bx-1.0, bx+1.0, c-4.8, c+4.8, z-1.3, z+.05)
            for sy in (-1, 1):
                ribs.box(bx+3.6, bx+4.4, c+sy*4.0, c+sy*4.35, z-2.2, z)
    beams.build(kit, 'Hull keel landing skids', kit.steel, .25)
    shoes.build(kit, 'Hull keel landing skid wear shoes', kit.dark, .1)
    blocks.build(kit, 'Hull keel landing skid mounting blocks', kit.steel, .1)
    ribs.build(kit, 'Hull keel landing skid stiffeners', kit.steel, .06)


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
    hatch(kit)
    launch_bay(kit)
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
    lift(kit)
    heat_tiles(kit)
    skids(kit)
