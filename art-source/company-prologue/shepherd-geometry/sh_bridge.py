"""Shepherd bow: the bridge hull, the forward sensor boom off the spine and
the starboard survey sensor head.

Read off drone_tender.png (pass 3, owner: "it was a longer curved bridge
hull in the sprite"): the centre of the ship carries one long rounded hull,
X 4..34 and Y -10..+5, running aft from the bow over the crew body. Its
port half is sand-tan and its starboard crown cream, the way a curved
surface lit from starboard reads; it rounds off over the bow at X 33..34
and runs down into the deck aft of X 5. The dark band across it at
X 27..28.5 is the bridge glazing, and the dark mark at X 30..31 a roof hatch.
There is no separate cab and no habitat pod: pass 2 had invented both.

So 'Bridge hull' is a superellipse lozenge (plan and section, BRIDGE_*):
blunter aft, rounder forward, its flat base buried in the crew body. On it:
the pale starboard crown plating, plating seams, the wraparound glazing band
with mullions under a brow lip, flank viewports (crew comfort), the roof
hatch and an air handler on the nose crown, an antenna mast, running lamps
either side of the nose and a tail vent. A thin grey boom runs forward off
the spine to X 44 (Y ~9.5); a grey-white sensor cluster stands at
X 31..39, Y -6..-11, a radome over its pedestal and a scanner forward.

Names: 'Bridge ...' and 'Mast ...'. Only the bridge hull joins kit.hull
(the ray-cast seat); every detail part stays out of it.
"""
import math
from mathutils import Vector
from sh_kit import prism, section, revolve_x, material

BRIDGE_X = (3.8, 35.6)        # aft and forward ends
BRIDGE_CY = -2.5              # plan centreline (Y -10..+5)
BRIDGE_HALF_W = 8.0
BRIDGE_H = 15.5               # crown height above BASE_Z (crown about +17.5, deck +6.5)
BASE_Z = 2.0                  # flat base, buried in the crew body
BOW_P, TAIL_P = 2.2, 3.0      # plan superellipse exponents forward / aft of mid-length
HEIGHT_K = 1.0                # crown falls off with the plan width
SECTION_N = 2.4               # section superellipse exponent (2 = ellipse)
GLAZE_X = (26.8, 28.6)        # the sprite's dark band


# --- Private geometry accumulators: many small parts, one object each kind. ---

class _Acc:
    def __init__(self):
        self.v, self.f = [], []

    def add(self, verts, faces):
        o = len(self.v)
        self.v += [tuple(p) for p in verts]
        self.f += [tuple(i+o for i in face) for face in faces]

    def obox(self, c, u, v, w, a, b, d):
        """Box centred on c with half sizes a, b, d along unit axes u, v, w."""
        c, u, v, w = Vector(c), Vector(u).normalized(), Vector(v).normalized(), Vector(w).normalized()
        pts = [c+u*a*i+v*b*j+w*d*k for k in (-1, 1) for j in (-1, 1) for i in (-1, 1)]
        self.add(pts, [(0, 2, 3, 1), (4, 5, 7, 6), (0, 1, 5, 4), (2, 6, 7, 3), (0, 4, 6, 2), (1, 3, 7, 5)])

    def box(self, c, dims):
        self.obox(c, (1, 0, 0), (0, 1, 0), (0, 0, 1), dims[0]/2, dims[1]/2, dims[2]/2)

    def bar(self, p0, p1, w, d=None, up=(0, 0, 1)):
        """Square-ish bar from p0 to p1, w wide, d deep (side axis taken against `up`)."""
        p0, p1 = Vector(p0), Vector(p1)
        ax = p1-p0
        up = Vector(up)
        side = ax.cross(up)
        if side.length < 1e-6:
            side = ax.cross(Vector((1, 0, 0)))
        side.normalize()
        nrm = side.cross(ax).normalized()
        self.obox((p0+p1)/2, ax, side, nrm, ax.length/2, w/2, (d or w)/2)

    def cyl(self, c, axis, r, half, n=10):
        c, w = Vector(c), Vector(axis).normalized()
        u = w.orthogonal().normalized()
        v = w.cross(u)
        ring = [u*math.cos(2*math.pi*i/n)+v*math.sin(2*math.pi*i/n) for i in range(n)]
        pts = [c-w*half+q*r for q in ring]+[c+w*half+q*r for q in ring]
        faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
        faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
        self.add(pts, faces)

    def dome(self, c, r, h, n=12, rings=3):
        """Faceted dome on a flat base at c (z up)."""
        c = Vector(c)
        pts, faces = [], []
        for k in range(rings):
            a = math.pi/2*k/rings
            pts += [c+Vector((r*math.cos(a)*math.cos(2*math.pi*i/n), r*math.cos(a)*math.sin(2*math.pi*i/n), h*math.sin(a)))
                    for i in range(n)]
        pts.append(c+Vector((0, 0, h)))
        top = len(pts)-1
        faces.append(tuple(reversed(range(n))))
        for k in range(rings-1):
            faces += [(k*n+i, k*n+(i+1) % n, (k+1)*n+(i+1) % n, (k+1)*n+i) for i in range(n)]
        faces += [((rings-1)*n+i, (rings-1)*n+(i+1) % n, top) for i in range(n)]
        self.add(pts, faces)

    def dish(self, c, axis, r, depth, t=.1, n=14):
        """Shallow parabolic-ish dish facing `axis`, its rim centred at c."""
        c, w = Vector(c), Vector(axis).normalized()
        u = w.orthogonal().normalized()
        v = w.cross(u)
        rim = [u*math.cos(2*math.pi*i/n)+v*math.sin(2*math.pi*i/n) for i in range(n)]
        mid = [c+q*r*.55-w*depth*.7 for q in rim]
        pts = [c+q*r for q in rim]+mid+[c-w*depth]
        pts += [p-w*t for p in pts]
        m = 2*n+1
        faces = []
        for s in (0, m):
            flip = s == m
            ring = [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
            ring += [(n+i, n+(i+1) % n, 2*n) for i in range(n)]
            faces += [tuple(reversed([j+s for j in f])) if flip else tuple(j+s for j in f) for f in ring]
        faces += [(i, m+i, m+(i+1) % n, (i+1) % n) for i in range(n)]
        self.add(pts, faces)

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.v, self.f, mat, bevel)


def _profile(x):
    """Bridge hull half-width and height above BASE_Z at x (0 outside its length)."""
    x0, x1 = BRIDGE_X
    xc, half = (x0+x1)/2, (x1-x0)/2
    u = (x-xc)/half
    if abs(u) >= 1:
        return 0.0, 0.0
    p = BOW_P if u > 0 else TAIL_P
    f = (1-abs(u)**p)**(1/p)
    return BRIDGE_HALF_W*f, BRIDGE_H*f**HEIGHT_K


def _pt(x, t, d=0.0):
    """Point on the bridge hull at station x, section angle t (rad, 0 = port, pi/2 = crown,
    pi = starboard), pushed d outward."""
    a, h = _profile(x)
    c, s = math.cos(t), math.sin(t)
    e = 2/SECTION_N
    y = BRIDGE_CY+(a+d)*math.copysign(abs(c)**e, c)
    z = BASE_Z+(h+d)*abs(s)**e
    return Vector((x, y, z))


def _strip(acc, xs, ts, d0, d1):
    """A thin solid on the hull surface over stations xs and angles ts, d0..d1 off the skin."""
    rows = [[_pt(x, t, d0) for t in ts] for x in xs]+[[_pt(x, t, d1) for t in ts] for x in xs]
    nx, nt = len(xs), len(ts)
    verts = [p for row in rows for p in row]

    def idx(layer, i, j):
        return (layer*nx+i)*nt+j
    faces = []
    for i in range(nx-1):
        for j in range(nt-1):
            faces.append((idx(0, i, j), idx(0, i, j+1), idx(0, i+1, j+1), idx(0, i+1, j)))
            faces.append((idx(1, i, j), idx(1, i+1, j), idx(1, i+1, j+1), idx(1, i, j+1)))
    for i in range(nx-1):
        for j in (0, nt-1):
            faces.append((idx(0, i, j), idx(0, i+1, j), idx(1, i+1, j), idx(1, i, j)))
    for j in range(nt-1):
        for i in (0, nx-1):
            faces.append((idx(0, i, j), idx(0, i, j+1), idx(1, i, j+1), idx(1, i, j)))
    acc.add(verts, faces)


def _hull_mesh(kit):
    x0, x1 = BRIDGE_X
    n = 34
    xs = [x0+.05+(x1-x0-.1)*(k/n) for k in range(n+1)]
    m = 18
    ts = [math.pi*j/m for j in range(m+1)]
    verts, faces = [], []
    for x in xs:
        verts += [tuple(_pt(x, t)) for t in ts]
    w = len(ts)
    for i in range(len(xs)-1):
        for j in range(w-1):
            faces.append((i*w+j, (i+1)*w+j, (i+1)*w+j+1, i*w+j+1))
        faces.append((i*w, i*w+w-1, (i+1)*w+w-1, (i+1)*w))       # flat base under the deck
    faces.append(tuple(range(w)))
    faces.append(tuple(range((len(xs)-1)*w, len(xs)*w)))
    return kit.mesh('Bridge hull', verts, faces, kit.tan, 0)


def build(kit):
    glass = material('Bridge tinted glass', (.012, .02, .03), 0, .3, emission=(.9, .72, .42), strength=.04)
    bsdf = glass.node_tree.nodes['Principled BSDF']
    if 'Specular IOR Level' in bsdf.inputs:
        bsdf.inputs['Specular IOR Level'].default_value = .2  # dark tint, not a sky mirror
    red = material('Running lamp red', (.3, .02, .02), 0, .3, emission=(1, .08, .05), strength=8)
    green = material('Running lamp green', (.02, .3, .08), 0, .3, emission=(.1, 1, .3), strength=8)
    white = material('Running lamp white', (.6, .6, .55), 0, .3, emission=(1, .95, .85), strength=8)

    hull = _hull_mesh(kit)
    kit.hull.append(hull)
    deg = math.radians

    # Pale plating over the starboard crown: the sprite's cream half of the hull.
    pale = _Acc()
    xs = [8.0+k*(26.0-8.0)/16 for k in range(17)]
    _strip(pale, xs, [deg(a) for a in range(96, 166, 5)], -.02, .08)
    pale.build(kit, 'Bridge hull pale crown plating', kit.pale, 0)

    # Plating seams: dark rings over the upper hull, and a crown seam fore and aft.
    seams = _Acc()
    for x in (9.0, 13.5, 18.0, 22.5):
        _strip(seams, [x-.07, x+.07], [deg(a) for a in range(20, 161, 10)], .06, .14)
    _strip(seams, [x/2 for x in range(14, 53)], [deg(88.8), deg(91.2)], .06, .14)
    seams.build(kit, 'Bridge hull plating seams', kit.dark, 0)

    # Bridge glazing: the dark band the sprite shows across the hull at X 27..28.5,
    # a wraparound strip of panes over the crown with mullions between them.
    gx = [GLAZE_X[0]+(GLAZE_X[1]-GLAZE_X[0])*k/4 for k in range(5)]
    pane_a = [(48, 62), (66, 80), (84, 96), (100, 114), (118, 132)]
    panes, frame = _Acc(), _Acc()
    for a0, a1 in pane_a:
        _strip(panes, gx, [deg(a0+(a1-a0)*k/4) for k in range(5)], -.02, .1)
    for a in [46, 64, 82, 98, 116, 134]:
        _strip(frame, [GLAZE_X[0]-.15+k*(GLAZE_X[1]-GLAZE_X[0]+.3)/4 for k in range(5)],
               [deg(a-1.2), deg(a+1.2)], 0, .2)
    for x in (GLAZE_X[0]-.1, GLAZE_X[1]+.1):
        _strip(frame, [x-.12, x+.12], [deg(44+k*(136-44)/12) for k in range(13)], 0, .2)
    panes.build(kit, 'Bridge glazing band', glass, 0)
    frame.build(kit, 'Bridge glazing frame', kit.grey, .02)
    # Brow: a raised lip of hull plating over the band's forward edge.
    brow = _Acc()
    _strip(brow, [GLAZE_X[1]+.15, GLAZE_X[1]+.55], [deg(42+k*(138-42)/12) for k in range(13)], 0, .32)
    brow.build(kit, 'Bridge brow visor', kit.tan, .03)

    # Side viewports down the hull flanks: the crew spaces the description asks for.
    ports, rims = _Acc(), _Acc()
    for x in (10.5, 13.0, 15.5, 20.0, 22.5):
        for t in (deg(50), deg(130)):
            c = _pt(x, t, 0)
            nrm = (_pt(x, t, 1)-c).normalized()
            rims.cyl(c+nrm*.04, nrm, .55, .08, 10)
            ports.cyl(c+nrm*.12, nrm, .4, .05, 10)
    rims.build(kit, 'Bridge hull viewport rims', kit.steel, 0)
    ports.build(kit, 'Bridge hull viewports', glass, 0)

    # Nose crown: the roof hatch (the sprite's dark mark at X 30..31) and an air handler.
    hx = 30.5
    top = _pt(hx, deg(90)).z
    hatch = _Acc()
    hatch.box((hx, BRIDGE_CY+.9, top-.05), (1.8, 1.8, .4))
    hatch.build(kit, 'Bridge roof hatch coaming', kit.steel, .05)
    lid = _Acc()
    lid.box((hx, BRIDGE_CY+.9, top+.2), (1.5, 1.5, .16))
    lid.cyl((hx-.85, BRIDGE_CY+.9, top+.2), (0, 1, 0), .12, .7, 8)
    for x in (hx-.5, hx+.5):
        for dy in (-.5, .5):
            lid.box((x, BRIDGE_CY+.9+dy, top+.3), (.14, .14, .06))
    lid.build(kit, 'Bridge roof hatch lid', kit.cream, .02)
    ax = 24.6
    atop = _pt(ax, deg(90)).z
    hvac = _Acc()
    hvac.box((ax, BRIDGE_CY-1.6, atop+.25), (1.8, 1.6, .7))
    hvac.build(kit, 'Bridge roof air handler', kit.grey, .06)
    grille = _Acc()
    for i in range(5):
        grille.box((ax-.6+i*.3, BRIDGE_CY-1.6, atop+.62), (.12, 1.4, .08))
    grille.build(kit, 'Bridge roof air handler grille', kit.dark, 0)

    # Antenna mast on the crown aft of the glazing: tripod foot, pole, yard, whips.
    mx, my = 24.6, BRIDGE_CY+1.8
    roof = _pt(mx, deg(80)).z
    mast = _Acc()
    for ang in (0, 120, 240):
        a = math.radians(ang)
        mast.bar((mx+.75*math.cos(a), my+.75*math.sin(a), roof-.1), (mx, my, roof+1.6), .14)
    mast.cyl((mx, my, roof+2.5), (0, 0, 1), .14, 2.5, 8)
    mast.bar((mx, my-1.1, roof+3.8), (mx, my+1.1, roof+3.8), .12)
    for y in (my-1.0, my+1.0):
        mast.bar((mx, y, roof+3.8), (mx, y, roof+5.2), .06)
    mast.bar((mx, my, roof+5.0), (mx, my, roof+6.6), .05)
    mast.build(kit, 'Mast bridge antenna mast', kit.steel, 0)
    lamp = _Acc()
    lamp.cyl((mx, my, roof+5.1), (0, 0, 1), .2, .12, 8)
    lamp.build(kit, 'Mast bridge masthead lamp', white, 0)

    # Navigation lamps either side of the nose, under the brow.
    for side, t, mat in (('port green', deg(52), green), ('starboard red', deg(128), red)):
        c = _pt(GLAZE_X[1]+1.2, t, 0)
        nrm = (_pt(GLAZE_X[1]+1.2, t, 1)-c).normalized()
        house = _Acc()
        house.cyl(c+nrm*.15, nrm, .38, .2, 8)
        house.build(kit, f'Bridge running lamp {side} housing', kit.steel, .02)
        lens = _Acc()
        lens.cyl(c+nrm*.4, nrm, .26, .08, 8)
        lens.build(kit, f'Bridge running lamp {side}', mat, 0)

    # Tail: where the hull runs down into the deck, a bolted service bulkhead and a vent.
    tail = _Acc()
    tx = BRIDGE_X[0]+2.2
    tc = _pt(tx, deg(90), 0)
    tail.cyl((tx-.3, BRIDGE_CY, tc.z-1.0), (1, 0, 0), .55, .4, 8)
    tail.build(kit, 'Bridge hull tail vent', kit.dark, 0)

    _boom(kit, white)
    _sensor_head(kit, glass)


def _boom(kit, white):
    sz = kit.surface(35, 8)[0].z            # spine top
    cy, cz, h = 9.5, sz+.6, .55               # boom axis and half section
    x0, x1 = 37.4, 43.7
    # Root clamp on the spine's nose, and a knee brace from the spine's face.
    root = _Acc()
    root.box((35.9, cy, sz+.65), (3.2, 1.9, 1.3))
    root.box((37.25, cy, cz), (.5, 1.5, 1.5))
    root.bar((37.0, cy, sz-3.0), (40.6, cy, cz-h), .4, .5)
    root.box((37.15, cy, sz-3.1), (.4, 1.4, 1.2))   # brace foot plate on the spine face
    root.build(kit, 'Mast forward sensor boom root clamp', kit.steel, .06)
    bolts = _Acc()
    for x in (34.8, 35.9, 37.0):
        for y in (cy-.75, cy+.75):
            bolts.cyl((x, y, sz+1.32), (0, 0, 1), .11, .05, 6)
    bolts.build(kit, 'Mast forward sensor boom root bolts', kit.cream, 0)

    # Lattice girder: four longerons, zig-zag lacing on every face, a cable duct inside.
    g = _Acc()
    corners = [(cy+s*h, cz+t*h) for s, t in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    for y, z in corners:
        g.bar((x0, y, z), (x1, y, z), .17)
    bays = 6
    step = (x1-x0)/bays
    for k in range(4):
        (ya, za), (yb, zb) = corners[k], corners[(k+1) % 4]
        for i in range(bays):
            xa, xb = x0+i*step, x0+(i+1)*step
            if i % 2:
                g.bar((xa, ya, za), (xb, yb, zb), .1)
            else:
                g.bar((xa, yb, zb), (xb, ya, za), .1)
    for i in range(bays+1):  # battens
        x = x0+i*step
        for k in range(4):
            (ya, za), (yb, zb) = corners[k], corners[(k+1) % 4]
            g.bar((x, ya, za), (x, yb, zb), .1)
    g.box(((x0+x1)/2, cy, cz), (x1-x0, .45, .45))
    g.build(kit, 'Mast forward sensor boom', kit.steel, 0)

    # Sensor tip: a turned housing with a pointed fairing, and a nav lamp on top.
    revolve_x(kit, 'Mast forward sensor boom tip',
              [(43.6, .05), (43.6, .62), (44.4, .62), (44.6, .5), (45.05, .22), (45.2, .05)], cy, cz, kit.cream, 12)
    lamp = _Acc()
    lamp.cyl((44.0, cy, cz+.75), (0, 0, 1), .16, .13, 8)
    lamp.build(kit, 'Mast forward sensor boom nav lamp', white, 0)
    stem = _Acc()
    stem.cyl((44.0, cy, cz+.55), (0, 0, 1), .1, .1, 6)
    stem.bar((44.0, cy-.62, cz), (44.0, cy-1.0, cz), .08)
    stem.build(kit, 'Mast forward sensor boom tip fittings', kit.steel, 0)


def _sensor_head(kit, glass):
    # The surveying_equipment: a column on a flange, a chassis cantilevered
    # forward, a radome over the column (the sprite's round pale mass), a lidar
    # drum on a yoke forward, a dish, and forward camera apertures.
    pz = kit.surface(30.5, -9)[0].z
    zb, zt = pz+3.6, pz+5.4                  # chassis underside and top
    col = _Acc()
    col.cyl((30.6, -8.6, pz+1.85), (0, 0, 1), 1.0, 1.85, 8)
    col.build(kit, 'Mast starboard sensor pedestal', kit.steel, .05)
    base = _Acc()
    base.cyl((30.6, -8.6, pz+.15), (0, 0, 1), 1.55, .15, 12)
    for i in range(8):
        a = 2*math.pi*(i+.5)/8
        base.cyl((30.6+1.3*math.cos(a), -8.6+1.3*math.sin(a), pz+.33), (0, 0, 1), .1, .05, 6)
    for a in (0, 90, 180, 270):  # gussets
        r = math.radians(a)
        base.bar((30.6+1.45*math.cos(r), -8.6+1.45*math.sin(r), pz+.3), (30.6+.9*math.cos(r), -8.6+.9*math.sin(r), pz+1.5), .12, .5)
    base.build(kit, 'Mast starboard sensor pedestal flange', kit.grey, 0)

    # Chassis: a chamfered prism over the column, running forward past the bow.
    plan = [(29.2, -10.6), (38.2, -10.2), (39.0, -9.4), (39.0, -7.2), (38.2, -6.5), (29.2, -6.6)]
    prism(kit, 'Mast starboard sensor head', plan, section(zb, zt, .3, .4), kit.grey)
    seams = _Acc()
    for x in (32.6, 35.6):
        seams.box((x, -8.55, zt-.05), (.12, 3.6, .14))
        seams.box((x, -10.53, (zb+zt)/2), (.12, .14, zt-zb-.4))
    seams.build(kit, 'Mast starboard sensor head seams', kit.dark, 0)
    covers = _Acc()
    covers.box((34.1, -10.5, (zb+zt)/2), (2.2, .14, 1.0))
    for x in (33.15, 35.05):
        for z in (zb+.65, zt-.65):
            covers.cyl((x, -10.6, z), (0, 1, 0), .08, .05, 6)
    covers.build(kit, 'Mast starboard sensor head service cover', kit.steel, 0)

    # Under-strut from the body's bow to the chassis's forward end.
    strut = _Acc()
    strut.bar((32.8, -8.6, pz-3.0), (37.4, -8.6, zb+.1), .45, .6, up=(0, 1, 0))
    strut.build(kit, 'Mast starboard sensor head strut', kit.steel, .04)

    # Radome over the column.
    dome = _Acc()
    dome.cyl((31.2, -8.6, zt+.2), (0, 0, 1), 1.75, .2, 14)
    dome.build(kit, 'Mast starboard sensor radome ring', kit.steel, 0)
    rad = _Acc()
    rad.dome((31.2, -8.6, zt+.4), 1.6, 1.7, 14, 4)
    rad.build(kit, 'Mast starboard sensor radome', kit.cream, 0)

    # Lidar / survey scanner: a drum with a dark scan window between yoke cheeks.
    lx, ly = 36.4, -8.4
    yoke = _Acc()
    yoke.box((lx, ly, zt+.15), (2.0, 2.4, .3))
    for y in (ly-1.05, ly+1.05):
        yoke.box((lx, y, zt+1.0), (1.2, .25, 1.5))
    yoke.cyl((lx, ly, zt+1.25), (0, 1, 0), .18, 1.25, 8)
    yoke.build(kit, 'Mast starboard sensor lidar yoke', kit.steel, .03)
    drum = _Acc()
    drum.cyl((lx, ly, zt+1.25), (0, 1, 0), .8, .8, 12)
    drum.build(kit, 'Mast starboard sensor lidar drum', kit.grey, .03)
    win = _Acc()
    win.box((lx+.72, ly, zt+1.25), (.2, 1.3, .55))
    win.build(kit, 'Mast starboard sensor lidar window', glass, 0)

    # Survey dish on a short post at the chassis's inboard edge, looking forward and up.
    dish = _Acc()
    dish.cyl((34.0, -7.3, zt+.6), (0, 0, 1), .12, .6, 6)
    dish.dish((34.25, -7.3, zt+1.45), (1, 0, .9), .9, .3)
    dish.bar((34.25, -7.3, zt+1.45), (34.95, -7.3, zt+2.05), .06)  # feed horn
    dish.build(kit, 'Mast starboard sensor dish', kit.cream, 0)

    # Forward apertures: two camera barrels and a downward-looking sounder.
    ap = _Acc()
    for y in (-9.6, -7.7):
        ap.cyl((39.15, y, (zb+zt)/2), (1, 0, 0), .4, .2, 10)
    ap.cyl((37.0, -9.6, zb-.1), (0, 0, 1), .45, .12, 10)
    ap.build(kit, 'Mast starboard sensor apertures', kit.steel, 0)
    lens = _Acc()
    for y in (-9.6, -7.7):
        lens.cyl((39.38, y, (zb+zt)/2), (1, 0, 0), .28, .05, 10)
    lens.cyl((37.0, -9.6, zb-.24), (0, 0, 1), .32, .04, 10)
    lens.build(kit, 'Mast starboard sensor aperture lenses', glass, 0)
