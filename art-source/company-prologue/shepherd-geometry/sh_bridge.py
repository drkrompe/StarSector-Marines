"""Shepherd bow: the bridge cab, the crew habitat shell behind it, the forward
sensor boom off the spine and the starboard survey sensor head.

Read off drone_tender.png: the tan body ends forward in a rounded cab
(X 26..33) with a small dark rectangle on its roof (X ~29, Y -1..-3: the
roof hatch) and a dark band across its aft edge (X ~26.5: aft-facing
windows over the drone deck); behind it the pale blister on the starboard
half of the body (X 5..25, Y -8..-1) is the habitat shell the description's
"internal volume given to crew comfort" asks for; a thin grey boom runs
forward off the spine to X 44 (Y ~9.5); a grey-white sensor cluster stands
at X 31..39, Y -6..-11, its round pale mass aft (a radome over the
pedestal) and a smaller grey block forward (the scanner).

Pass 2 detail: the cab's forward faces are raked back and glazed in framed
panes under a brow visor, with side and aft windows, a roof hatch, roof
rails, an air handler, an antenna mast and navigation lamps; the habitat is
ribbed into segments with viewport rows down both flanks and an
observation strip along its crown; the boom is an open lattice girder on a
root clamp and knee brace with a sensor tip and nav lamp; the survey head
is a chassis on a column with a radome, a lidar drum on a yoke, a dish and
forward apertures.

Names: 'Bridge ...' and 'Mast ...'. Only the cab and the habitat shell join
kit.hull (the ray-cast seat); every detail part stays out of it.
"""
import math
from mathutils import Vector
from sh_kit import prism, section, pod, plane_cut, facet_normal, inset, revolve_x, material, ngon

CAB_PLAN = [(25, -6), (31.5, -6), (35, -3), (35, 3), (31.5, 6), (25, 6)]
HAB_STATIONS = [(4, 1.0, 1.0), (7, 3.6, 4.6), (14, 4.4, 6.0), (21, 4.0, 5.4), (25.5, 2.2, 3.0)]
CAB_H, CAB_SHOULDER = 6.0, 2.0   # cab rises deck-1..deck+6 (ENVELOPE.md)
HAB_CY = -4.5
RAKE = 62            # windscreen face angle below horizontal, degrees
RUN = 1/math.tan(math.radians(RAKE))  # horizontal setback per unit of rise


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


def _hab(x):
    """Habitat shell (ry, rz) at X, by the same linear loft the pod uses."""
    s = HAB_STATIONS
    for (x0, a0, b0), (x1, a1, b1) in zip(s, s[1:]):
        if x0 <= x <= x1:
            t = (x-x0)/(x1-x0)
            return a0+(a1-a0)*t, b0+(b1-b0)*t
    raise ValueError(x)


def _hab_face(x, cz, a0, a1, side):
    """Midpoint and outward normal of the habitat's 12-gon face between angles a0..a1 (deg)."""
    ry, rz = _hab(x)
    p0 = Vector((ry*math.cos(math.radians(a0)), rz*math.sin(math.radians(a0))))
    p1 = Vector((ry*math.cos(math.radians(a1)), rz*math.sin(math.radians(a1))))
    m = (p0+p1)/2
    e = p1-p0
    n = Vector((e.y, -e.x)).normalized()
    return Vector((x, HAB_CY+side*m.x, cz+m.y)), Vector((0, side*n.x, n.y))


def build(kit):
    glass = material('Bridge tinted glass', (.012, .02, .03), 0, .3, emission=(.9, .72, .42), strength=.04)
    bsdf = glass.node_tree.nodes['Principled BSDF']
    if 'Specular IOR Level' in bsdf.inputs:
        bsdf.inputs['Specular IOR Level'].default_value = .2  # dark tint, not a sky mirror
    red = material('Running lamp red', (.3, .02, .02), 0, .3, emission=(1, .08, .05), strength=8)
    green = material('Running lamp green', (.02, .3, .08), 0, .3, emission=(.1, 1, .3), strength=8)
    white = material('Running lamp white', (.6, .6, .55), 0, .3, emission=(1, .95, .85), strength=8)

    deck = kit.surface(28, 0)[0].z
    z_rake = deck+.8      # foot of the raked windscreen
    cab = prism(kit, 'Bridge cab', CAB_PLAN, section(deck-1.0, deck+CAB_H, .6, CAB_SHOULDER), kit.tan)
    # The forward faces rake back from just above the deck: one flat facet each.
    faces = []  # (A, B, outward plan normal) of each forward face at z_rake
    for a, b in zip(CAB_PLAN[1:4], CAB_PLAN[2:5]):
        d = Vector((b[0]-a[0], b[1]-a[1]))
        n = Vector((d.y, -d.x)).normalized()
        faces.append((Vector(a), Vector(b), n))
        mid = (Vector(a)+Vector(b))/2
        plane_cut(cab, 'cab rake', (mid.x, mid.y, z_rake), facet_normal(n, RAKE))
    kit.hull.append(cab)
    roof = deck+CAB_H
    sh0 = roof-CAB_SHOULDER   # foot of the aft shoulder chamfer

    def on_face(face, t, z, out=0.0):
        a, b, n = face
        p = a+(b-a)*t-n*(z-z_rake)*RUN
        nn = Vector(facet_normal(n, RAKE))
        return Vector((p.x, p.y, z))+nn*out

    # Windscreen: framed panes on the three raked faces (3 forward, 2 per quarter).
    zg0, zg1 = deck+1.7, deck+4.1
    panes, frame = _Acc(), _Acc()
    splits = [(0, .5, 1), (0, 1/3, 2/3, 1), (0, .5, 1)]
    for face, ts in zip(faces, splits):
        a, b, n = face
        span = (b-a).length
        m = .45/span  # keep clear of the corner creases
        ts = [m+(1-2*m)*t for t in ts]
        nn = Vector(facet_normal(n, RAKE))
        for t0, t1 in zip(ts, ts[1:]):
            q = [on_face(face, t0, zg0, .05), on_face(face, t1, zg0, .05), on_face(face, t1, zg1, .05), on_face(face, t0, zg1, .05)]
            panes.add(q+[p-nn*.12 for p in q], [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)])
        for t in ts:  # mullions, the end ones are the jambs
            frame.bar(on_face(face, t, zg0-.15, .1), on_face(face, t, zg1+.15, .1), .26, .2, up=nn)
        for z in (zg0-.08, zg1+.08):  # sill and head
            frame.bar(on_face(face, ts[0]-.02, z, .1), on_face(face, ts[-1]+.02, z, .1), .22, .22, up=nn)

    # Aft windows on the cab's aft shoulder: the dark band the sprite shows at X ~26.5.
    zc0, zc1 = sh0+.35, sh0+1.6
    for y0, y1 in ((-3.9, -1.65), (-1.05, 1.05), (1.65, 3.9)):
        q = [(25.0+(z-sh0), y, z) for z, y in ((zc0, y0), (zc0, y1), (zc1, y1), (zc1, y0))]
        q = [Vector(p)+Vector((-.7, 0, .7))*.06 for p in q]
        panes.add(q+[p+Vector((.7, 0, -.7))*.16 for p in q], [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)])
        for y in (y0-.15, y1+.15):
            frame.bar((25.0+(zc0-.15-sh0)-.05, y, zc0-.15), (25.0+(zc1+.15-sh0)-.05, y, zc1+.15), .22, .2)
    # Starboard side windows (the port wall is hidden behind the spine).
    for x in (26.3, 27.9, 29.5):
        panes.box((x, -6.07, deck+2.3), (1.1, .16, 1.4))
        frame.box((x, -6.03, deck+2.3), (1.45, .14, 1.75))
    panes.build(kit, 'Bridge glazing band', glass, 0)
    frame.build(kit, 'Bridge glazing frame', kit.grey, .03)

    # Brow visor over the windscreen: a sloped plate standing proud of the raked faces.
    outer = inset(CAB_PLAN, -.15)[1:5]
    inner = inset(CAB_PLAN, (deck+4.55-z_rake)*RUN+.1)[1:5]
    zo, zi = deck+4.2, deck+4.55
    vv = [(x, y, zo) for x, y in outer]+[(x, y, zi) for x, y in inner]
    vv += [(x, y, z-.28) for x, y, z in vv]
    vf = []
    for i in range(3):
        vf.append((i, i+1, 4+i+1, 4+i))
        vf.append((8+i, 12+i, 12+i+1, 8+i+1))
        vf.append((i, 8+i, 8+i+1, i+1))
        vf.append((4+i, 4+i+1, 12+i+1, 12+i))
    vf += [(0, 4, 12, 8), (3, 11, 15, 7)]
    kit.mesh('Bridge brow visor', vv, vf, kit.tan, .06)
    # Visor drip strip and the window washer rail under its lip.
    lip = _Acc()
    for (x0, y0), (x1, y1) in zip(outer, outer[1:]):
        lip.bar((x0, y0, zo-.32), (x1, y1, zo-.32), .14, .14)
    lip.build(kit, 'Bridge brow visor lip', kit.steel, 0)

    # Roof: access hatch (sprite's dark rectangle), air handler, rails, mast.
    hatch = _Acc()
    hatch.box((28.9, -1.2, roof+.15), (2.0, 2.0, .3))
    hatch.build(kit, 'Bridge roof hatch coaming', kit.steel, .05)
    lid = _Acc()
    lid.box((28.9, -1.2, roof+.38), (1.7, 1.7, .16))
    lid.cyl((27.95, -1.2, roof+.38), (0, 1, 0), .13, .8, 8)          # hinge
    lid.bar((29.5, -1.7, roof+.55), (29.5, -.7, roof+.55), .1)      # grab handle
    for y in (-1.7, -.7):
        lid.bar((29.5, y, roof+.46), (29.6, y, roof+.6), .1)
    for x in (28.2, 29.6):
        for y in (-1.9, -.5):
            lid.box((x, y, roof+.48), (.14, .14, .06))                 # dog bolts
    lid.build(kit, 'Bridge roof hatch lid', kit.cream, .02)

    hvac = _Acc()
    hvac.box((29.6, 1.4, roof+.35), (1.6, 1.8, .7))
    hvac.build(kit, 'Bridge roof air handler', kit.grey, .06)
    grille = _Acc()
    for i in range(5):
        grille.box((29.0+i*.3, 1.4, roof+.72), (.12, 1.6, .08))
    grille.build(kit, 'Bridge roof air handler grille', kit.dark, 0)

    # Roof rails: stanchions and two rails round the flat of the roof, aft gap
    # left open over the hatch's approach from the habitat.
    x0, x1, yr = 27.3, 31.6, 3.75
    hr = 1.2
    rails = _Acc()
    ring = [(x0, -yr), (x1, -1.9), (x1, 1.9), (x0, yr)]
    for (ax, ay), (bx, by) in zip(ring, ring[1:]):
        n = max(2, round(math.hypot(bx-ax, by-ay)/1.5))
        for i in range(0 if (ax, ay) == ring[0] else 1, n+1):  # corner posts once
            t = i/n
            px, py = ax+(bx-ax)*t, ay+(by-ay)*t
            rails.bar((px, py, roof), (px, py, roof+hr), .1, up=(1, 0, 0))
        for h in (hr*.5, hr):
            rails.bar((ax, ay, roof+h), (bx, by, roof+h), .09)
    rails.build(kit, 'Bridge roof rails', kit.grey, 0)

    # Antenna mast at the roof's aft port corner: tripod foot, pole, yard, whips.
    mx, my = 27.9, 2.6
    mast = _Acc()
    for ang in (0, 120, 240):
        a = math.radians(ang)
        mast.bar((mx+.75*math.cos(a), my+.75*math.sin(a), roof), (mx, my, roof+1.6), .14)
    mast.cyl((mx, my, roof+2.5), (0, 0, 1), .14, 2.5, 8)
    mast.bar((mx, my-1.1, roof+3.8), (mx, my+1.1, roof+3.8), .12)
    for y in (my-1.0, my+1.0):
        mast.bar((mx, y, roof+3.8), (mx, y, roof+5.2), .06)
    mast.bar((mx, my, roof+5.0), (mx, my, roof+6.6), .05)
    mast.build(kit, 'Mast bridge antenna mast', kit.steel, 0)
    lamp = _Acc()
    lamp.cyl((mx, my, roof+5.1), (0, 0, 1), .2, .12, 8)
    lamp.build(kit, 'Mast bridge masthead lamp', white, 0)

    # Navigation lamps: green on the port end of the visor (the port wall is
    # behind the spine), red low on the starboard wall under the sensor chassis.
    for side, s, mat, (x, y, z) in (('port green', 1, green, (outer[3][0], outer[3][1], zo+.25)),
                                    ('starboard red', -1, red, (31.4, -6.0, deck+2.3))):
        house = _Acc()
        house.box((x-.45, y+s*.15, z), (.9, .45, .55))
        house.build(kit, f'Bridge running lamp {side} housing', kit.steel, .03)
        lens = _Acc()
        lens.box((x-.3, y+s*.42, z), (.55, .12, .35))
        lens.build(kit, f'Bridge running lamp {side}', mat, 0)

    _habitat(kit, glass)
    _boom(kit, white)
    _sensor_head(kit, glass)


def _habitat(kit, glass):
    hz = kit.surface(15, -4.5)[0].z
    kit.hull.append(pod(kit, 'Bridge habitat shell', HAB_STATIONS, HAB_CY, hz, kit.pale, 12))
    # Pressure-hull ribs: the shell reads as five frames and the gores between.
    ribs = _Acc()
    rib_x = (7.5, 11.0, 14.5, 18.0, 21.5)
    for x in rib_x:
        ry, rz = _hab(x)
        pts = []
        for xx in (x-.3, x+.3):
            pts += [(xx, y, z) for y, z in ngon(HAB_CY, hz, ry+.22, rz+.22, 12)]
        n = 12
        faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
        faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
        ribs.add(pts, faces)
    # The tail and nose caps are frames too.
    ribs.build(kit, 'Bridge habitat ribs', kit.steel, .05)

    # Viewport rows down both flanks, two per gore, on the upper flank face.
    ports, rims = _Acc(), _Acc()
    for x0, x1 in zip(rib_x, rib_x[1:]):
        for x in (x0+(x1-x0)*.3, x0+(x1-x0)*.7):
            for side in (1, -1):
                for a0, a1, r in ((15, 45, .6), (45, 75, .45)):
                    pass
                    c, n = _hab_face(x, hz, a0, a1, side)
                    rims.cyl(c+n*.02, n, r+.16, .1, 12)
                    ports.cyl(c+n*.1, n, r, .06, 12)
    rims.build(kit, 'Bridge habitat viewport rims', kit.steel, 0)
    ports.build(kit, 'Bridge habitat viewports', glass, 0)

    # Observation strip along the crown: glazed gores between the ribs.
    obs, sash = _Acc(), _Acc()
    for x0, x1 in zip(rib_x, rib_x[1:]):
        xa, xb = x0+.35, x1-.35
        za, zb = (hz+_hab(x)[1]*math.sin(math.radians(75)) for x in (xa, xb))
        hw = .75
        q = [(xa, HAB_CY-hw, za+.05), (xb, HAB_CY-hw, zb+.05), (xb, HAB_CY+hw, zb+.05), (xa, HAB_CY+hw, za+.05)]
        obs.add(q+[(x, y, z-.3) for x, y, z in q], [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)])
        for y in (HAB_CY-hw-.1, HAB_CY+hw+.1):
            sash.bar((xa, y, za+.07), (xb, y, zb+.07), .16, .16)
        xm = (xa+xb)/2
        sash.bar((xm, HAB_CY-hw, (za+zb)/2+.1), (xm, HAB_CY+hw, (za+zb)/2+.1), .12, .12)
    obs.build(kit, 'Bridge habitat observation strip', glass, 0)
    sash.build(kit, 'Bridge habitat observation sash', kit.grey, 0)

    # Tail end: a bolted service bulkhead and a vent where the shell tapers.
    tail = _Acc()
    tail.cyl((5.2, HAB_CY, hz+.9), (1, 0, 0), .55, .5, 8)
    tail.build(kit, 'Bridge habitat tail vent', kit.dark, 0)


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
