"""Phaeton bow: the bridge on the starboard bow shoulder, the forward
observation housing beside it, and the hose-handling crane on the port bow.

Read off tanker_medium.png (bow right, port up; model X = 140 - row,
Y = 60 - col):
- Bridge. The tan cab covers X 101..119.5, Y -10.5..-30: square to the
  bow and to starboard, its aft-port corner cut on a long chamfer from
  (110, -10.5) to (101, -22). Inside it a darker block X 105..117,
  Y -14..-26.5 is the upper tier (the wheelhouse); the two amber points at
  X 113.5 are lit panes of its windscreen, so the screen is raked back far
  enough to be seen from above. Outboard of the cab a light grey plate at
  X 108.5..119.5, Y -30..-35.5 with three lit lamps along its edge is the
  bridge wing. Below and aft of it, dark grey at X 87..116, Y -30..-37.5 is
  a lower gangway deck, and the bright rails at Y -38 and -42 between
  X 93 and 106 are an escape pod slung in davits over the side.
- The grey bulb forward of the cab (X 119..128, Y -12..-23, a highlight
  at its forward-starboard quarter) is a domed forward observation and
  sensor housing on the bow shoulder, joined to the cab by a short trunk.
- Crane. Not a single post: two parallel lattice legs at X 94.5 and
  X 105.5 run outboard from the deck (Y ~23) to Y 44 (gaps between them,
  one diagonal from (95, 34) to (104, 43)); a lattice arm across their
  heads at Y 43..46 runs forward from X 93 to the knob at X 118; a narrow
  fly jib runs on out to the knob at (100, 55); a brace drops from the
  forward arm at (107, 43) to the bow rim at (119, 29), and an aft brace
  from (93, 44) to the rim at (92, 31). Read as a twin-leg luffing hose
  crane on a slewing ring, its heels on the deck, its head over the side.
Nothing here stands on the nose: WS 004 (126, 13) keeps its arc.
"""
import math
import bpy
from mathutils import Vector
from ph_kit import material, prism, section, sphere

DECK = 6.0  # ph_hull's deck line; parts on the hull are seated by ray-cast

# Bridge, read off the sprite (see docstring).
BRIDGE = {'x0': 101.0, 'x1': 119.5, 'y0': -30.0, 'y1': -10.5}
CAB_PLAN = [(103.0, -30.0), (119.5, -30.0), (119.5, -12.0), (118.0, -10.5), (110.0, -10.5), (101.0, -22.0),
            (101.0, -27.5)]
CAB_TOP = DECK+8.0            # one deck high: a 6.5-unit door with headroom
WHEEL = {'x0': 105.0, 'x1': 117.0, 'y0': -26.5, 'y1': -14.0, 'top': DECK+15.5, 'rake': 4.0}
WING = {'x0': 108.5, 'x1': 119.5, 'y0': -35.8, 'y1': -28.8}
GANGWAY_PLAN = [(86.5, -39.8), (92.5, -39.8), (93.5, -37.6), (110.5, -37.6), (116.0, -36.6), (116.0, -33.0),
                (110.0, -30.2), (86.5, -30.2)]
POD = {'x0': 92.5, 'x1': 106.5, 'y': -41.3, 'r': 2.6}
HOUSING = (123.6, -17.6, 4.6)  # forward observation housing centre and radius
MAST = (107.6, -20.2)

# Crane, read off the sprite (see docstring).
RING = (100.0, 21.5, 6.0)      # slewing ring centre and radius
HEELS = ((95.5, 24.0), (104.5, 24.0))
HEADS = ((94.5, 43.2), (105.5, 43.2))
HEAD_Z = DECK+16.0
ARM_Y, ARM_X0, ARM_X1 = 45.0, 93.0, 118.0
FLY_TIP = (100.5, 54.5)
REEL = (89.0, 22.0)

DOOR_W, DOOR_H = 2.2, 6.5      # 0.7 x 2.1 m at 0.32 m per unit
RAIL_H = 3.3


class Parts:
    """One mesh object assembled from bars, boxes, slabs and tubes."""

    def __init__(self):
        self.v, self.f = [], []

    def _add(self, verts, faces):
        o = len(self.v)
        self.v += [tuple(p) for p in verts]
        self.f += [tuple(i+o for i in face) for face in faces]

    def bar(self, a, b, r):
        a, b = Vector(a), Vector(b)
        d = (b-a).normalized()
        s = d.cross(Vector((0, 0, 1)))
        if s.length < 1e-3:
            s = d.cross(Vector((1, 0, 0)))
        s.normalize()
        u = s.cross(d).normalized()
        ring = [s*r+u*r, -s*r+u*r, -s*r-u*r, s*r-u*r]
        self._add([a+c for c in ring]+[b+c for c in ring],
                  [(0, 1, 2, 3), (7, 6, 5, 4)]+[(i, (i+1) % 4, 4+(i+1) % 4, 4+i) for i in range(4)])

    def box(self, c, dims):
        cx, cy, cz = c
        hx, hy, hz = (d/2 for d in dims)
        self._add([(cx+sx*hx, cy+sy*hy, cz+sz*hz) for sz in (-1, 1) for sy in (-1, 1) for sx in (-1, 1)],
                  [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)])

    def slab(self, corners, normal, t, off=0.0):
        """A plate on four coplanar corners, standing `off` proud and `t` thick along `normal`."""
        n = Vector(normal).normalized()
        c = [Vector(p) for p in corners]
        self._add([p+n*off for p in c]+[p+n*(off+t) for p in c],
                  [(0, 1, 2, 3), (7, 6, 5, 4)]+[(i, (i+1) % 4, 4+(i+1) % 4, 4+i) for i in range(4)])

    def tube(self, pts, r, n=8, caps=True):
        pts = [Vector(p) for p in pts]
        tang = [(pts[min(i+1, len(pts)-1)]-pts[max(i-1, 0)]).normalized() for i in range(len(pts))]
        ref = tang[0].orthogonal().normalized()
        verts = []
        for p, t in zip(pts, tang):
            ref = (ref-t*ref.dot(t)).normalized()  # carry the frame along the path
            side = t.cross(ref)
            verts += [p+(ref*math.cos(2*math.pi*k/n)+side*math.sin(2*math.pi*k/n))*r for k in range(n)]
        faces = []
        for i in range(len(pts)-1):
            faces += [(i*n+k, i*n+(k+1) % n, (i+1)*n+(k+1) % n, (i+1)*n+k) for k in range(n)]
        if caps:
            faces += [tuple(range(n)), tuple(range((len(pts)-1)*n, len(pts)*n))]
        self._add(verts, faces)

    def girder(self, a, b, w, h, bays, rc=.28, rl=.13, w1=None, h1=None):
        """Open box-lattice girder on the centreline a-b: four chords, a batten
        ring at every panel point and one diagonal per face per bay. `w` is
        the horizontal width, `h` the depth; w1/h1 taper the far end."""
        a, b = Vector(a), Vector(b)
        d = (b-a).normalized()
        s = d.cross(Vector((0, 0, 1))).normalized()
        u = s.cross(d).normalized()
        w1 = w if w1 is None else w1
        h1 = h if h1 is None else h1

        def corner(t, k):
            ww, hh = (w+(w1-w)*t)/2, (h+(h1-h)*t)/2
            sx, sz = ((1, 1), (-1, 1), (-1, -1), (1, -1))[k]
            return a+(b-a)*t+s*ww*sx+u*hh*sz
        for k in range(4):
            self.bar(corner(0, k), corner(1, k), rc)
        for i in range(bays+1):
            t = i/bays
            for k in range(4):
                self.bar(corner(t, k), corner(t, (k+1) % 4), rl)
        for i in range(bays):
            t0, t1 = i/bays, (i+1)/bays
            flip = i % 2
            for k in range(4):
                k2 = (k+1) % 4
                p, q = (corner(t0, k), corner(t1, k2)) if flip else (corner(t0, k2), corner(t1, k))
                self.bar(p, q, rl)

    def rail(self, pts, z0, posts=2.2, r=.11, closed=False):
        """Two-course guard rail along a deck edge at height z0."""
        pts = [Vector((x, y, z0)) for x, y in pts]
        if closed:
            pts.append(pts[0])
        for zz in (RAIL_H, RAIL_H/2):
            for p, q in zip(pts, pts[1:]):
                self.bar(p+Vector((0, 0, zz)), q+Vector((0, 0, zz)), r)
        for p, q in zip(pts, pts[1:]):
            n = max(1, round((q-p).length/posts))
            for i in range(n+(0 if closed else (1 if q is pts[-1] else 0))):
                c = p+(q-p)*(i/n)
                self.bar(c, c+Vector((0, 0, RAIL_H)), r)

    def ladder(self, a, b, width=1.4, pitch=.95, r=.1, across=(0, 1, 0)):
        a, b = Vector(a), Vector(b)
        s = Vector(across).normalized()*width/2
        self.bar(a+s, b+s, r)
        self.bar(a-s, b-s, r)
        n = max(1, int((b-a).length/pitch))
        for i in range(1, n+1):
            c = a+(b-a)*(i/(n+1))
            self.bar(c-s, c+s, r*.8)

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.v, self.f, mat, bevel)


def strut(kit, name, a, b, r, mat, vertices=8):
    a, b = Vector(a), Vector(b)
    d = b-a
    ob = kit.cylinder(name, (a+b)/2, r, d.length, mat, 'Z', vertices, 0)
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    return ob


def ground(kit, x, y, fallback=DECK):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


def smooth(pts, k=5):
    """Catmull-Rom resample through the control points."""
    p = [Vector(q) for q in pts]
    p = [p[0]]+p+[p[-1]]
    out = []
    for i in range(1, len(p)-2):
        p0, p1, p2, p3 = p[i-1], p[i], p[i+1], p[i+2]
        for j in range(k):
            t = j/k
            out.append(.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t**3))
    out.append(p[-2])
    return out


def build(kit):
    green = material('Navigation lamp green', (.02, .2, .05), .1, .3, emission=(.2, 1.0, .35), strength=5.0)
    red = material('Navigation lamp red', (.25, .02, .02), .1, .3, emission=(1.0, .12, .08), strength=5.0)
    white = material('Masthead lamp white', (.3, .3, .28), .1, .3, emission=(1.0, .95, .85), strength=5.0)
    hazard = material('Crane hook hazard yellow', (.55, .40, .05), .3, .5)
    rubber = material('Cargo hose rubber black', (.03, .03, .03), 0, .8)
    build_bridge(kit, green, white)
    build_housing(kit)
    build_crane(kit, hazard, rubber)
    build_bow_fittings(kit, red)


# --- Bridge -------------------------------------------------------------------

def build_bridge(kit, green, white):
    deck = ground(kit, 108, -20)
    cab = prism(kit, 'Bridge cab', CAB_PLAN, section(deck-6, CAB_TOP, 1, 1.2), kit.tan)
    kit.hull.append(cab)
    z0, z1, rake = CAB_TOP, WHEEL['top'], WHEEL['rake']
    wx0, wx1, wy0, wy1 = WHEEL['x0'], WHEEL['x1'], WHEEL['y0'], WHEEL['y1']

    # Upper tier: the wheelhouse, its forward face raked back so the panes
    # catch the light from above as the sprite's lit slits do.
    bottom = [(wx0, wy0), (wx1, wy0), (wx1, wy1), (wx0, wy1)]
    top = [(wx0, wy0), (wx1-rake, wy0), (wx1-rake, wy1), (wx0, wy1)]
    verts = [(x, y, z0) for x, y in bottom]+[(x, y, z1) for x, y in top]
    faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    kit.hull.append(kit.mesh('Bridge cab wheelhouse', verts, faces, kit.tan, .12))
    kit.box('Bridge roof', ((wx0+wx1-rake)/2-.1, (wy0+wy1)/2, z1+.25), (wx1-rake-wx0+.6, wy1-wy0+.6, .5), kit.tan, .1)

    def screen(z):  # x of the raked face at height z
        return wx1-rake*(z-z0)/(z1-z0)
    n_screen = Vector((z1-z0, 0, rake)).normalized()

    # Windscreen: five framed panes across the raked face.
    glass, frame = Parts(), Parts()
    za, zb = z0+2.4, z1-.7
    panes = 5
    span = (wy1-wy0-1.0)/panes
    for i in range(panes):
        ya, yb = wy0+.5+i*span+.14, wy0+.5+(i+1)*span-.14
        glass.slab([(screen(za), ya, za), (screen(za), yb, za), (screen(zb), yb, zb), (screen(zb), ya, zb)],
                   n_screen, .08, .02)
    for i in range(panes+1):
        y = wy0+.5+i*span
        frame.bar(Vector((screen(za-.1), y, za-.1))+n_screen*.12, Vector((screen(zb+.1), y, zb+.1))+n_screen*.12, .14)
    for z in (za-.1, zb+.1):
        frame.bar(Vector((screen(z), wy0+.4, z))+n_screen*.12, Vector((screen(z), wy1-.4, z))+n_screen*.12, .16)
    # A sun visor lip over the screen, kept short so the panes still read from above.
    frame.box((screen(z1)+.35, (wy0+wy1)/2, z1-.05), (.9, wy1-wy0+.2, .22))
    glass.build(kit, 'Bridge forward glazing', kit.glass)

    # Wing windows on both sides of the wheelhouse, two on the aft face.
    stb, port = Parts(), Parts()
    for xa in (109.0, 111.3):
        stb.slab([(xa, wy0, z0+3.0), (xa+1.9, wy0, z0+3.0), (xa+1.9, wy0, z1-1.0), (xa, wy0, z1-1.0)], (0, -1, 0), .08, .02)
    for xa in (106.0, 108.3, 110.6):
        port.slab([(xa, wy1, z0+3.0), (xa+1.9, wy1, z0+3.0), (xa+1.9, wy1, z1-1.0), (xa, wy1, z1-1.0)], (0, 1, 0), .08, .02)
    for ya in (-24.0, -18.6):
        port.slab([(wx0, ya, z0+3.6), (wx0, ya+2.4, z0+3.6), (wx0, ya+2.4, z1-1.2), (wx0, ya, z1-1.2)], (-1, 0, 0), .08, .02)
    stb.build(kit, 'Bridge starboard glazing', kit.glass)
    port.build(kit, 'Bridge port and aft glazing', kit.glass)

    # Lower tier: lit ports along the forward face (starboard of the
    # observation trunk) and the starboard face.
    ports = Parts()
    zp = deck+4.6
    for ya in (-28.6, -26.2):
        ports.slab([(BRIDGE['x1'], ya, zp), (BRIDGE['x1'], ya+1.6, zp), (BRIDGE['x1'], ya+1.6, zp+1.1),
                    (BRIDGE['x1'], ya, zp+1.1)], (1, 0, 0), .08, .02)
    for xa in (104.5, 108.0, 111.5, 115.0):
        ports.slab([(xa, BRIDGE['y0'], zp), (xa+1.8, BRIDGE['y0'], zp), (xa+1.8, BRIDGE['y0'], zp+1.1),
                    (xa, BRIDGE['y0'], zp+1.1)], (0, -1, 0), .08, .02)
    ports.build(kit, 'Bridge lower ports', kit.glass)

    # Window frames round the side and aft glazing, and the plating seams:
    # a dark welt at each plate course and every few frames along the walls.
    seams = Parts()
    for xa in (109.0, 111.3):
        seams.slab([(xa-.15, wy0, z0+2.85), (xa+2.05, wy0, z0+2.85), (xa+2.05, wy0, z0+3.0), (xa-.15, wy0, z0+3.0)],
                   (0, -1, 0), .1, .0)
    for z in (deck+2.4, deck+6.6):
        seams.slab([(BRIDGE['x0']+2.2, BRIDGE['y0'], z), (BRIDGE['x1']-.4, BRIDGE['y0'], z),
                    (BRIDGE['x1']-.4, BRIDGE['y0'], z+.14), (BRIDGE['x0']+2.2, BRIDGE['y0'], z+.14)], (0, -1, 0), .06)
        seams.slab([(BRIDGE['x1'], BRIDGE['y0']+.4, z), (BRIDGE['x1'], -12.4, z), (BRIDGE['x1'], -12.4, z+.14),
                    (BRIDGE['x1'], BRIDGE['y0']+.4, z+.14)], (1, 0, 0), .06)
    for x in (106.8, 110.3, 113.8, 117.3):
        seams.slab([(x, BRIDGE['y0'], deck-4.5), (x+.14, BRIDGE['y0'], deck-4.5), (x+.14, BRIDGE['y0'], CAB_TOP-1.3),
                    (x, BRIDGE['y0'], CAB_TOP-1.3)], (0, -1, 0), .06)
    for y in (-27.0, -22.5, -18.0, -14.0):
        seams.slab([(BRIDGE['x1'], y, deck-4.5), (BRIDGE['x1'], y+.14, deck-4.5), (BRIDGE['x1'], y+.14, CAB_TOP-1.3),
                    (BRIDGE['x1'], y, CAB_TOP-1.3)], (1, 0, 0), .06)
    for y in (wy0, wy1):
        sgn = -1 if y == wy0 else 1
        seams.slab([(wx0+.3, y, z0+2.2), (wx1-.6, y, z0+2.2), (wx1-.6, y, z0+2.34), (wx0+.3, y, z0+2.34)], (0, sgn, 0), .06)
    seams.build(kit, 'Bridge plating seams', kit.dark)
    frame.build(kit, 'Bridge windscreen frame', kit.steel)

    # Deck door in the aft face, at person scale, with frame, step and lamp.
    door_y = -24.75
    dz = ground(kit, BRIDGE['x0']-1.2, door_y)
    door = Parts()
    door.slab([(BRIDGE['x0'], door_y-DOOR_W/2, dz), (BRIDGE['x0'], door_y+DOOR_W/2, dz),
               (BRIDGE['x0'], door_y+DOOR_W/2, dz+DOOR_H), (BRIDGE['x0'], door_y-DOOR_W/2, dz+DOOR_H)], (-1, 0, 0), .12, .04)
    door.build(kit, 'Bridge deck door', kit.grey)
    jamb = Parts()
    for y in (door_y-DOOR_W/2-.15, door_y+DOOR_W/2+.15):
        jamb.bar((BRIDGE['x0']-.12, y, dz), (BRIDGE['x0']-.12, y, dz+DOOR_H+.2), .15)
    jamb.bar((BRIDGE['x0']-.12, door_y-DOOR_W/2-.3, dz+DOOR_H+.2), (BRIDGE['x0']-.12, door_y+DOOR_W/2+.3, dz+DOOR_H+.2), .15)
    jamb.box((BRIDGE['x0']-.32, door_y+.65, dz+3.1), (.25, .2, .7))  # dog handle
    jamb.box((BRIDGE['x0']-.6, door_y, dz+.15), (1.2, DOOR_W+.8, .3))  # step plate
    jamb.build(kit, 'Bridge deck door frame', kit.steel)
    kit.box('Bridge deck door lamp', (BRIDGE['x0']-.3, door_y, dz+DOOR_H+.75), (.6, .9, .45), kit.lamp, 0)

    # Wing door from the wheelhouse onto the starboard walkway.
    wd = Parts()
    wd.slab([(106.0, wy0, z0), (106.0+DOOR_W, wy0, z0), (106.0+DOOR_W, wy0, z0+DOOR_H), (106.0, wy0, z0+DOOR_H)],
            (0, -1, 0), .12, .04)
    wd.build(kit, 'Bridge wing door', kit.grey)

    # Hatches: a dorsal access hatch on the lower roof aft of the wheelhouse,
    # an escape scuttle on the wheelhouse roof.
    for name, (x, y, z, s) in {'Bridge dorsal hatch': (103.0, -25.2, CAB_TOP, 2.2),
                               'Bridge roof scuttle': (106.6, -16.2, z1+.5, 1.5)}.items():
        kit.box(name+' coaming', (x, y, z+.25), (s+.5, s+.5, .5), kit.steel, .06)
        kit.box(name, (x, y, z+.6), (s, s, .22), kit.grey, .05)
        kit.box(name+' hinge', (x-s/2-.05, y, z+.6), (.3, s*.8, .3), kit.dark, 0)

    # Walkway from the wing door along the lower roof, out to the wing.
    walk = Parts()
    walk.rail([(102.5, -29.0), (108.5, -29.0)], CAB_TOP)
    walk.ladder((wx0-.25, -17.5, z0), (wx0-.25, -17.5, z1+1.5), across=(0, 1, 0))  # roof ladder on the aft face
    walk.build(kit, 'Bridge walkway rail and roof ladder', kit.grey)

    # Wing: a grey deck plate on brackets, railed round, three lamps and a
    # green starboard sidelight at its outer forward corner.
    w = WING
    kit.box('Bridge wing', ((w['x0']+w['x1'])/2, (w['y0']+w['y1'])/2, z0-.3), (w['x1']-w['x0'], w['y1']-w['y0'], .6),
            kit.grey, .1)
    wing = Parts()
    wing.rail([(w['x0']+.2, w['y0']+.2), (w['x1']-.2, w['y0']+.2), (w['x1']-.2, -30.2)], z0)
    wing.rail([(w['x0']+.2, -30.2), (w['x0']+.2, -33.4)], z0)  # open aft of that for the stair
    for x in (w['x0']+1.0, (w['x0']+w['x1'])/2, w['x1']-1.0):
        wing.bar((x, w['y0']+.6, z0-.6), (x, BRIDGE['y0']-.1, deck+2.5), .22)  # knee bracket to the cab wall
        wing.bar((x, w['y0']+.6, z0-.6), (x, BRIDGE['y0']-.1, z0-.6), .2)
    wing.box((117.6, -34.2, z0+1.3), (1.0, 1.2, 2.6))  # wing console
    wing.build(kit, 'Bridge wing rail and brackets', kit.steel)
    lamps = Parts()
    for x in (111.0, 114.0, 117.0):
        lamps.box((x, w['y0']+.2, z0+RAIL_H+.25), (.6, .6, .5))
    lamps.build(kit, 'Bridge wing lamps', kit.lamp)
    kit.box('Bridge starboard sidelight', (w['x1']-.25, w['y0']+.5, z0+RAIL_H+.4), (.6, .5, .8), green, 0)

    # Mast on the wheelhouse roof: pole, yard with signal lamps, masthead
    # lamp, radar scanner, a small dish and two whip aerials.
    mx, my = MAST
    zr = z1+.5
    kit.cylinder('Bridge mast', (mx, my, zr+5.5), .38, 11.0, kit.grey, 'Z', 8, 0)
    mast = Parts()
    for dy in (-1.3, 1.3):  # tripod stays to the roof
        mast.bar((mx-1.6, my+dy, zr), (mx, my, zr+6.0), .16)
    mast.bar((mx, my-3.8, zr+7.0), (mx, my+3.8, zr+7.0), .18)  # yard
    mast.box((mx+.6, my, zr+8.6), (1.4, 1.4, .25))             # radar platform
    mast.build(kit, 'Bridge mast yard and stays', kit.grey)
    sig = Parts()
    for dy in (-3.6, 3.6):
        sig.box((mx, my+dy, zr+6.6), (.45, .45, .55))
    sig.build(kit, 'Bridge mast signal lamps', kit.lamp)
    kit.box('Bridge masthead lamp', (mx, my, zr+11.25), (.5, .5, .5), white, 0)
    kit.cylinder('Bridge radar pedestal', (mx+.6, my, zr+9.15), .4, .9, kit.steel, 'Z', 8, 0)
    scanner = kit.box('Bridge radar scanner', (mx+.6, my, zr+9.8), (.55, 7.0, .45), kit.dark, .05)
    scanner.rotation_euler[2] = math.radians(28)
    dish = kit.cylinder('Bridge sensor dish', (106.2, -24.6, zr+1.4), 1.1, .35, kit.cream, 'Z', 16, .05)
    dish.rotation_euler = (math.radians(-35), math.radians(-20), 0)
    aer = Parts()
    aer.bar((106.0, -24.6, zr), (106.2, -24.6, zr+1.2), .14)  # dish post
    for x, y in ((112.3, -14.6), (112.3, -25.9)):
        aer.bar((x, y, zr-.2), (x-.4, y, zr+7.5), .07)
    aer.build(kit, 'Bridge aerials', kit.steel)

    build_gangway(kit, deck)


def build_gangway(kit, deck):
    """Lower gangway deck outboard of the cab, its stair to the wing and the
    escape pod in davits over the side."""
    top = deck+.6
    kit.hull.append(prism(kit, 'Bridge gangway deck', GANGWAY_PLAN, section(top-1.2, top, .2, .2), kit.grey))
    g = Parts()
    g.rail([(87.0, -39.4), (92.2, -39.4), (93.2, -37.2), (101.0, -37.2)], top)
    g.rail([(87.0, -30.6), (87.0, -39.4)], top)
    # Stair from the gangway up to the wing, rising forward along the side.
    a, b = Vector((99.0, -34.6, top)), Vector((108.3, -34.6, CAB_TOP))
    for dy in (-.8, .8):
        g.bar(a+Vector((0, dy, 0)), b+Vector((0, dy, 0)), .2)
        g.bar(a+Vector((0, dy, RAIL_H)), b+Vector((0, dy, RAIL_H)), .1)
    steps = 9
    for i in range(1, steps):
        c = a+(b-a)*(i/steps)
        g.box((c.x, c.y, c.z), (.9, 1.6, .22))
    for i in range(0, steps+1, 3):  # stanchions under the stair
        c = a+(b-a)*(i/steps)
        g.bar((c.x, c.y-.8, top), (c.x, c.y-.8, c.z), .12)
    # Brackets carrying the gangway off the hull side.
    for x in (89.0, 96.0, 103.0, 110.0):
        g.bar((x, -36.8, top-1.2), (x, -30.5, top-5.0), .25)
    g.build(kit, 'Bridge gangway rail and stair', kit.steel)

    # Escape pod in two davits, its hull lofted along X.
    p = POD
    zc = top+.6
    stations = [(p['x0'], .5), (p['x0']+.8, .82), (p['x0']+2.2, 1), (p['x1']-2.2, 1), (p['x1']-.8, .82), (p['x1'], .5)]
    verts, faces, n = [], [], 12
    for x, f in stations:
        verts += [(x, p['y']+p['r']*f*math.cos(2*math.pi*k/n), zc+p['r']*f*.9*math.sin(2*math.pi*k/n)) for k in range(n)]
    for i in range(len(stations)-1):
        faces += [(i*n+k, i*n+(k+1) % n, (i+1)*n+(k+1) % n, (i+1)*n+k) for k in range(n)]
    faces += [tuple(range(n)), tuple(range((len(stations)-1)*n, len(stations)*n))]
    kit.mesh('Bridge escape pod', verts, faces, kit.cream, .06)
    band = Parts()
    for x in (p['x0']+2.6, p['x1']-2.6):
        band.tube([(x, p['y']+p['r']*1.02*math.cos(a), zc+p['r']*.92*math.sin(a)) for a in
                   [2*math.pi*k/16 for k in range(17)]], .14, 6, caps=False)
    band.box(((p['x0']+p['x1'])/2, p['y'], zc+p['r']*.9), (2.6, 1.2, .2))  # hatch on the pod's crown
    band.build(kit, 'Bridge escape pod hatch and bands', kit.steel)
    dav = Parts()
    for x in (p['x0']+2.6, p['x1']-2.6):
        foot = (x, -36.6, top)
        knee = (x, -37.4, top+5.2)
        head = (x, p['y']-.6, top+5.6)
        dav.bar(foot, knee, .28)
        dav.bar(knee, head, .26)
        dav.bar(head, (x, p['y'], zc+p['r']*.9+.1), .07)  # fall to the pod
    dav.build(kit, 'Bridge escape pod davits', kit.grey)


# --- Forward observation housing ---------------------------------------------

def build_housing(kit):
    x, y, r = HOUSING
    z = ground(kit, x, y)
    kit.cylinder('Bow observation housing base', (x, y, z+.4), r, 4.4, kit.grey, 'Z', 20, .12)
    sphere(kit, 'Bow observation housing dome', (x, y, z+2.6), r-.25, kit.grey, 24, 12)
    ports = Parts()
    for k in range(10):  # a band of viewports round the dome's shoulder
        a = math.radians(-150+k*24)
        c, s = math.cos(a), math.sin(a)
        rr, zz = (r-.25)*.93, z+2.6+(r-.25)*.36
        ports.slab([(x+c*rr-s*.5, y+s*rr+c*.5, zz-.35), (x+c*rr+s*.5, y+s*rr-c*.5, zz-.35),
                    (x+c*rr+s*.5, y+s*rr-c*.5, zz+.35), (x+c*rr-s*.5, y+s*rr+c*.5, zz+.35)], (c, s, 0), .1, .05)
    ports.build(kit, 'Bow observation housing ports', kit.glass)
    kit.cylinder('Bow observation housing ring', (x, y, z+2.6), r+.15, .55, kit.steel, 'Z', 20, .05)
    kit.cylinder('Bow sensor head', (x+.6, y-.6, z+2.6+r-.3), .9, .9, kit.steel, 'Z', 10, .05)
    kit.box('Bow sensor head lamp', (x+.6, y-.6, z+2.6+r+.35), (.4, .4, .35), kit.lamp, 0)
    # Covered trunk back to the cab's forward face.
    kit.box('Bow observation trunk', (BRIDGE['x1']+.9, y, z+1.2), (2.4, 4.2, 4.2), kit.grey, .15)


# --- Crane --------------------------------------------------------------------

def build_crane(kit, hazard, rubber):
    cx, cy, rr = RING
    base = ground(kit, cx, cy)
    kit.cylinder('Crane pedestal', (cx, cy, base+.9), 3.4, 2.4, kit.grey, 'Z', 16, .15)
    kit.cylinder('Crane slewing gear', (cx, cy, base+2.3), rr-.6, .5, kit.dark, 'Z', 32, 0)
    kit.cylinder('Crane slewing ring', (cx, cy, base+2.85), rr, .7, kit.grey, 'Z', 32, .12)
    zt = base+3.2
    heel_z, head_z = zt+1.0, HEAD_Z

    # King post with the luffing pendants, operator cab and a guard rail
    # round the inboard half of the ring.
    px, py = cx, cy-5.0
    kit.box('Crane post', (px, py, zt+7.0), (1.6, 1.6, 14.0), kit.grey, .1)
    kit.box('Crane post cap', (px, py, zt+14.3), (2.4, 2.0, .7), kit.steel, .05)
    cab_c = (cx, cy-2.2, zt+1.8)
    kit.box('Crane operator cab', cab_c, (2.9, 2.6, 3.6), kit.grey, .12)
    cg = Parts()
    cg.slab([(cx-1.2, cab_c[1]+1.3, zt+1.9), (cx+1.2, cab_c[1]+1.3, zt+1.9), (cx+1.2, cab_c[1]+1.3, zt+3.2),
             (cx-1.2, cab_c[1]+1.3, zt+3.2)], (0, 1, 0), .08, .02)
    cg.slab([(cx+1.45, cab_c[1]-.9, zt+1.9), (cx+1.45, cab_c[1]+.9, zt+1.9), (cx+1.45, cab_c[1]+.9, zt+3.2),
             (cx+1.45, cab_c[1]-.9, zt+3.2)], (1, 0, 0), .08, .02)
    cg.build(kit, 'Crane operator cab glazing', kit.glass)
    rail = Parts()
    rail.rail([(cx+math.cos(a)*(rr-.3), cy+math.sin(a)*(rr-.3)) for a in
               [math.radians(d) for d in range(200, 345, 18)]], zt)
    rail.build(kit, 'Crane platform rail', kit.steel)

    # Main boom: two lattice legs from heel pins on the ring out to the head.
    legs = Parts()
    for (hx, hy), (ex, ey) in zip(HEELS, HEADS):
        legs.girder((hx, hy, heel_z), (ex, ey, head_z-1.0), 1.9, 1.6, 6, w1=1.6, h1=1.3)
    for t in (0.0, .5):  # cross ties between the legs
        (ax, ay), (bx, by) = HEELS
        (ex, ey), (fx, fy) = HEADS
        p = Vector((ax+(ex-ax)*t, ay+(ey-ay)*t, heel_z+(head_z-1-heel_z)*t))
        q = Vector((bx+(fx-bx)*t, by+(fy-by)*t, heel_z+(head_z-1-heel_z)*t))
        legs.bar(p+Vector((0, 0, .8)), q+Vector((0, 0, .8)), .2)
        legs.bar(p-Vector((0, 0, .8)), q-Vector((0, 0, .8)), .2)
    legs.build(kit, 'Crane boom legs', kit.grey)
    lugs = Parts()
    for hx, hy in HEELS:
        lugs.box((hx, hy, zt+.6), (2.4, 1.6, 1.2))
        lugs.bar((hx-1.4, hy, heel_z), (hx+1.4, hy, heel_z), .35)
    lugs.build(kit, 'Crane boom heel lugs', kit.steel)
    pend = Parts()
    for ex, ey in HEADS:
        pend.bar((px, py, zt+14.3), (ex, ey-.4, head_z+.3), .1)
    pend.build(kit, 'Crane luffing pendants', kit.dark)

    # Forward arm across the boom head, the sprite's lattice to X 118.
    arm = Parts()
    arm.girder((ARM_X0, ARM_Y, head_z), (ARM_X1, ARM_Y, head_z), 3.2, 1.8, 10, w1=2.6, h1=1.4)
    arm.build(kit, 'Crane forward jib', kit.grey)
    # Trolley runway slung under the arm, and the trolley at its outer end.
    run = Parts()
    run.box(((ARM_X0+4+ARM_X1)/2, ARM_Y, head_z-1.35), (ARM_X1-ARM_X0-4, .5, .5))
    for x in range(int(ARM_X0)+5, int(ARM_X1), 4):
        run.bar((x, ARM_Y, head_z-.8), (x, ARM_Y, head_z-1.2), .1)
    run.box((ARM_X1-3.5, ARM_Y, head_z-1.9), (1.6, 1.0, .7))
    run.build(kit, 'Crane forward jib inner chord', kit.steel)

    # Hose handlers' platform over the boom head (the sprite's block at
    # X 96..107, Y 46..51): open grating on bearers, railed outboard.
    plat_z = head_z+.9
    grate = Parts()
    for x in (96.4, 106.0):
        grate.bar((x, ARM_Y+1.2, plat_z-.25), (x, 50.6, plat_z-.25), .2)
    for i in range(14):
        x = 96.4+i*(106.0-96.4)/13
        if abs(x-100.2) < 1.1:
            continue  # the fly jib passes up through here
        grate.bar((x, ARM_Y+1.4, plat_z), (x, 50.4, plat_z), .09)
    grate.bar((96.4, 50.6, plat_z), (106.0, 50.6, plat_z), .18)
    grate.rail([(96.4, ARM_Y+1.6), (96.4, 50.4), (99.0, 50.4)], plat_z)
    grate.rail([(102.0, 50.4), (106.0, 50.4), (106.0, ARM_Y+1.6)], plat_z)
    grate.build(kit, 'Crane head platform', kit.steel)

    # Fly jib out to the sprite's knob at Y 55, trussed underneath.
    fx, fy = FLY_TIP
    fz = head_z+1.6
    fly = Parts()
    fly.girder((100.0, ARM_Y+1.0, head_z+.2), (fx, fy, fz), 1.7, 1.4, 4, w1=1.0, h1=.9)
    fly.build(kit, 'Crane outboard jib', kit.grey)
    strut(kit, 'Crane outboard jib king strut', (100.0, ARM_Y+1.6, head_z-.6), (100.0, ARM_Y+1.6, head_z-3.2), .22, kit.steel, 6)
    strut(kit, 'Crane outboard jib lower chord', (100.0, ARM_Y+1.6, head_z-3.2), (fx, fy-.6, fz-.4), .16, kit.steel, 6)
    strut(kit, 'Crane outboard jib outer chord', (px, py, zt+14.0), (fx, fy-.5, fz+.5), .09, kit.dark, 6)  # topping stay
    kit.box('Crane outboard jib head', (fx, fy+.3, fz), (1.8, 1.6, 1.6), kit.steel, .1)
    kit.cylinder('Crane outboard jib sheave', (fx, fy+1.1, fz-.2), .7, .4, kit.dark, 'X', 12, 0)

    # Braces: forward to the bow rim, aft to the rim by the gallery, and
    # knee braces from the arm to the fly jib.
    lat = Parts()
    z_fwd = ground(kit, 119.0, 28.0, DECK-1)
    lat.girder((107.0, ARM_Y-1.0, head_z-.6), (119.0, 28.8, z_fwd+.4), 1.3, 1.0, 6, rc=.22, rl=.11)
    lat.build(kit, 'Crane jib brace', kit.grey)
    aft = Parts()
    z_aft = ground(kit, 92.0, 30.5, DECK-1)
    aft.girder((93.6, ARM_Y-1.0, head_z-.6), (92.0, 30.6, z_aft+.4), 1.2, 1.0, 5, rc=.22, rl=.11)
    aft.build(kit, 'Crane jib brace aft', kit.grey)
    for name, foot in ((('Crane jib brace foot', (119.0, 28.8, z_fwd)), ('Crane jib brace aft foot', (92.0, 30.6, z_aft)))):
        kit.box(name, (foot[0], foot[1], foot[2]+.3), (2.0, 2.0, 1.0), kit.steel, .08)
    diag = [((HEADS[0][0]+.3, 33.5, heel_z+(head_z-1-heel_z)*.45+.8), (HEADS[1][0]-.3, 42.6, head_z-.2)),
            ((96.4, ARM_Y+1.0, head_z-.8), (96.4, 50.4, head_z+.5)),   # knee braces under the
            ((106.0, ARM_Y+1.0, head_z-.8), (106.0, 50.4, head_z+.5))]  # head platform
    for i, (a, b) in enumerate(diag, 1):
        strut(kit, f'Crane jib diagonal {i}', a, b, .26, kit.grey, 6)

    # Forward head: sheave block, hoist wires, block and hook.
    hx = ARM_X1+.4
    kit.box('Crane jib head', (hx-.2, ARM_Y, head_z+.1), (2.2, 2.8, 2.2), kit.steel, .1)
    kit.cylinder('Crane jib head sheave', (hx+.9, ARM_Y+.2, head_z-.1), 1.0, .5, kit.dark, 'Y', 14, 0)
    bz = head_z-7.0
    wires = Parts()
    for dy in (-.25, .25):
        wires.bar((hx+1.6, ARM_Y+dy, head_z-.4), (hx+1.6, ARM_Y+dy, bz+.9), .06)
    wires.build(kit, 'Crane hoist wires', kit.dark)
    kit.box('Crane hoist block', (hx+1.6, ARM_Y, bz), (1.0, 1.2, 1.8), hazard, .1)
    hook = Parts()
    hz = bz-1.0
    pts = [(hx+1.6, ARM_Y, hz+.2), (hx+1.6, ARM_Y, hz-.6)]
    pts += [(hx+1.6+.65*math.sin(a), ARM_Y, hz-1.25+.65*math.cos(a)) for a in
            [math.radians(d) for d in range(0, 280, 35)]]
    hook.tube(pts, .2, 6)
    hook.build(kit, 'Crane hook', hazard)

    # The cargo hose: off the reel, up the aft leg, along the arm, over the
    # head sheave and down in a bight to its coupling, slung from the hook.
    rx, ry = REEL
    rz = ground(kit, rx, ry)+2.9
    kit.cylinder('Crane hose reel', (rx, ry, rz), 2.3, 3.0, rubber, 'X', 18, .05)
    reel = Parts()
    for dx in (-1.7, 1.7):
        reel.tube([(rx+dx, ry+2.9*math.cos(a), rz+2.9*math.sin(a)) for a in [2*math.pi*k/18 for k in range(19)]],
                  .22, 6, caps=False)
        reel.bar((rx+dx, ry-2.6, rz-2.9), (rx+dx, ry, rz), .25)  # A-frame stand
        reel.bar((rx+dx, ry+2.6, rz-2.9), (rx+dx, ry, rz), .25)
    reel.bar((rx-2.2, ry, rz), (rx+2.2, ry, rz), .35)  # axle
    reel.build(kit, 'Crane hose reel frame', kit.steel)
    sx = ARM_X1+.4
    path = [(rx, ry+.5, rz+2.35), (91.5, 24.5, zt+2.4), (94.0, 30.0, heel_z+3.0), (94.6, 40.0, head_z+.4),
            (96.5, ARM_Y+.2, head_z+1.25), (108.0, ARM_Y+.2, head_z+1.25), (sx, ARM_Y+.2, head_z+1.3),
            (sx+1.0, ARM_Y+.3, head_z+.9), (sx+1.15, ARM_Y+.4, head_z-3.0), (sx+1.0, ARM_Y+.5, bz-3.5),
            (sx+1.6, ARM_Y+.5, bz-5.0), (sx+2.3, ARM_Y+.4, bz-3.8), (sx+2.4, ARM_Y+.3, hz-1.9)]
    hose = Parts()
    hose.tube(smooth(path, 5), .42, 8)
    hose.build(kit, 'Crane cargo hose', rubber)
    kit.cylinder('Crane hose coupling', (sx+2.4, ARM_Y+.3, hz-1.75), .62, .5, kit.steel, 'Z', 12, .05)
    sling = Parts()
    sling.bar((sx+2.4, ARM_Y+.3, hz-1.5), (hx+1.6, ARM_Y, hz-1.85), .07)
    sling.build(kit, 'Crane hose sling', kit.dark)
    saddles = Parts()
    for x in (99.0, 104.0, 109.0, 114.0):
        saddles.box((x, ARM_Y+.2, head_z+.85), (.5, 1.3, .25))
    saddles.build(kit, 'Crane hose saddles', kit.steel)


# --- Bow fittings -------------------------------------------------------------

def build_bow_fittings(kit, red):
    # Mooring bitts and a fairlead on the starboard foredeck, a tow padeye on
    # the stem shoulder, and the red port sidelight on the port bow rim; all
    # kept low and off the nose so WS 004 (126, 13) keeps its forward arc.
    z = ground(kit, 121.0, -5.5)
    for i, y in enumerate((-4.2, -6.8), 1):
        kit.cylinder(f'Bow mooring bitt {i}', (121.0, y, z+.8), .6, 1.6, kit.steel, 'Z', 10, .05)
    kit.box('Bow mooring bitt base', (121.0, -5.5, z+.1), (1.8, 4.2, .3), kit.steel, .05)
    zf = ground(kit, 124.5, -9.0)
    kit.box('Bow fairlead', (124.5, -9.0, zf+.4), (1.2, 2.2, .8), kit.dark, .1)
    zp = ground(kit, 127.0, -2.0)
    kit.box('Bow tow padeye', (127.0, -2.0, zp+.2), (1.6, .5, 1.2), kit.steel, .05)
    zl = ground(kit, 118.0, 30.0)
    kit.box('Bow port sidelight', (117.0, 30.6, zl+1.0), (.6, .5, .8), red, 0)
    kit.box('Bow port sidelight screen', (117.0, 30.2, zl+1.0), (1.2, .15, 1.1), kit.dark, 0)
