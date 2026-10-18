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
X 31..39, Y -6..-11, a radome over its chassis on a plinth seated on the
skin, and a scanner forward.

Names: 'Bridge ...' and 'Mast ...'. Only the bridge hull joins kit.hull
(the ray-cast seat); every detail part stays out of it.
"""
import math
import bpy
from mathutils import Vector
from sh_kit import prism, section, revolve_x, material

BRIDGE_X = (3.8, 34.8)        # aft and forward ends (the chin lands on the bow here)
BRIDGE_CY = -2.5              # plan centreline (Y -10..+5)
BRIDGE_HALF_W = 8.0
BRIDGE_H = 15.0               # crown height above BASE_Z (crown +17, deck +6)
BASE_Z = 2.0                  # flat base, buried in the crew body
SECTION_N = 3.0               # section superellipse exponent: a flat crown and full flanks
# Long profile (pass 3 round 2, owner: the front windows and the bridge's shape
# need work): a flat crown, a short aft taper, then at the front a steep raked
# windscreen face from the brow (RAKE_X[0]) down to the sill (RAKE_X[1]), and a
# rounded chin below the sill running down into the bow.
TAIL_LEN = 7.0                # aft taper length
PLAN_FRONT = 24.0             # plan width starts rounding off here
RAKE_X = (27.4, 30.0)         # brow and sill stations: the sprite's dark band at X 27..28.5 and on
SILL_H = 10.6                 # sill height above BASE_Z (deck +1.6)
# The chin (pass 3 round 3, owner: the bridge's bottom lip does not cleanly meet
# the hull): forward of the sill the hull is drawn down onto the bow's own skin,
# tangent where it lands at BRIDGE_X[1], so the bridge flows into the bow rather
# than overhanging it with an exposed flat underside. _BOW holds the hull parts
# the chin lands on, captured before the bridge hull itself joins kit.hull.
_BOW = {'objs': None, 'cache': {}}


def _bow_z(x, y):
    """Top of the bow's skin at (x, y) (None off it), by a downward ray."""
    key = (round(x, 3), round(y, 3))
    if key in _BOW['cache']:
        return _BOW['cache'][key]
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    best = None
    for ob in _BOW['objs']:
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        hit, p, _, _ = ev.ray_cast(inv @ Vector((x, y, 60)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
        if hit:
            z = (ev.matrix_world @ p).z
            best = z if best is None or z > best else best
    _BOW['cache'][key] = best
    return best


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
    if not x0 < x < x1:
        return 0.0, 0.0
    # Plan: full width, rounding off aft over TAIL_LEN and forward from PLAN_FRONT.
    if x < x0+TAIL_LEN:
        fw = (1-((x0+TAIL_LEN-x)/TAIL_LEN)**2.6)**(1/2.6)
    elif x > PLAN_FRONT:
        fw = (1-((x-PLAN_FRONT)/(x1-PLAN_FRONT))**2.4)**(1/2.4)
    else:
        fw = 1.0
    # Height: aft taper, flat crown, the raked windscreen face, the chin.
    xb, xs = RAKE_X
    if x < x0+TAIL_LEN:
        h = BRIDGE_H*(1-((x0+TAIL_LEN-x)/TAIL_LEN)**2.2)**(1/2.2)
    elif x <= xb-.6:
        h = BRIDGE_H
    elif x <= xb:                                   # the brow rounds over into the rake
        t = (x-(xb-.6))/.6
        h = BRIDGE_H-(BRIDGE_H-SILL_H)*.06*t*t
    elif x <= xs:
        h0 = BRIDGE_H-(BRIDGE_H-SILL_H)*.06
        h = h0+(SILL_H-h0)*(x-xb)/(xs-xb)
    else:
        h = SILL_H*(1-((x-xs)/(x1-xs))**2.0)**.5
    return BRIDGE_HALF_W*fw, h


def _pt(x, t, d=0.0):
    """Point on the bridge hull at station x, section angle t (rad, 0 = port, pi/2 = crown,
    pi = starboard), pushed d outward."""
    a, h = _profile(x)
    c, s = math.cos(t), math.sin(t)
    e = 2/SECTION_N
    y = BRIDGE_CY+(a+d)*math.copysign(abs(c)**e, c)
    z0 = BASE_Z+h*abs(s)**e
    z = z0+d*abs(s)**e
    xs, x1 = RAKE_X[1], BRIDGE_X[1]
    if x > xs and _BOW['objs']:
        zb = _bow_z(x, BRIDGE_CY+a*math.copysign(abs(c)**e, c))
        if zb is not None:
            # The flanks land first and the crown last, so the chin meets the bow
            # on a tangent all round rather than folding at its feet.
            xe = xs+(x1-xs)*(.4+.6*abs(s))
            w = (1-min(1.0, (x-xs)/(xe-xs)))**2
            z = zb-.06*(1-w)+(z0-zb)*w+(z-z0)
    return Vector((x, y, z))


def _ty(x, y):
    """Section angle at station x whose skin point lies at plan offset y."""
    a = _profile(x)[0]
    q = max(-1.0, min(1.0, (y-BRIDGE_CY)/a))
    return math.acos(math.copysign(abs(q)**(SECTION_N/2), q))


def _yz(z, y, d=0.0):
    """Point on the windscreen face at height z and plan offset y, pushed d outward: the
    station is found by bisection down the brow, rake and upper chin, where the skin's
    height falls monotonically with x at fixed Y."""
    lo, hi = RAKE_X[0]-.6, RAKE_X[1]+1.5
    for _ in range(40):
        x = (lo+hi)/2
        if _pt(x, _ty(x, y)).z > z:
            lo = x
        else:
            hi = x
    x = (lo+hi)/2
    return _pt(x, _ty(x, y), d)


def _strip(acc, xs, ts, d0, d1, by_y=False, pt=None):
    """A thin solid on the hull surface over stations xs and angles ts, d0..d1 off the skin
    (with by_y, ts are plan offsets: the solid's edges then run at constant Y; with pt,
    pt(x, t, d) places each point instead)."""
    if pt is None:
        def pt(x, t, d):
            return _pt(x, _ty(x, t) if by_y else t, d)
    rows = [[pt(x, t, d0) for t in ts] for x in xs]+[[pt(x, t, d1) for t in ts] for x in xs]
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
        for j, t in enumerate(ts):
            p = _pt(x, t)
            if j in (0, m) and x > RAKE_X[1]:          # the flank feet stay under the bow's skin
                zb = _bow_z(x, p.y)
                if zb is not None:
                    p.z = min(p.z, zb-.8)
            verts.append(tuple(p))
    w = len(ts)
    for i in range(len(xs)-1):
        for j in range(w-1):
            faces.append((i*w+j, (i+1)*w+j, (i+1)*w+j+1, i*w+j+1))
        faces.append((i*w, i*w+w-1, (i+1)*w+w-1, (i+1)*w))       # flat base under the deck
    faces.append(tuple(range(w)))
    faces.append(tuple(range((len(xs)-1)*w, len(xs)*w)))
    return kit.mesh('Bridge hull', verts, faces, kit.tan, 0)


def build(kit):
    # Tinted glazing: near-black blue, a soft (not mirror) sheen, and two cool
    # diagonal highlight streaks across each window - the read of tinted glass.
    # It keeps this shader through surfacing (owner: a swatch read as steel; a
    # mirror finish only picked up the tan hull and read as taupe).
    glass = material('Bridge tinted glass', (.004, .008, .014), 0, .18)
    nt = glass.node_tree
    bsdf = nt.nodes['Principled BSDF']
    if 'Specular IOR Level' in bsdf.inputs:
        bsdf.inputs['Specular IOR Level'].default_value = .35
    tc, sep = nt.nodes.new('ShaderNodeTexCoord'), nt.nodes.new('ShaderNodeSeparateXYZ')
    diag = nt.nodes.new('ShaderNodeMath')
    diag.operation = 'MULTIPLY_ADD'                          # across the window, rising
    diag.inputs[1].default_value = .55
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    els = ramp.color_ramp.elements
    els[0].position, els[0].color = 0.0, (.012, .016, .022, 1)
    els[1].position, els[1].color = 1.0, (.012, .016, .022, 1)
    for pos, c in ((.38, (.012, .016, .022)), (.45, (.30, .38, .46)), (.5, (.012, .016, .022)),
                   (.56, (.012, .016, .022)), (.585, (.16, .20, .25)), (.61, (.012, .016, .022))):
        e = els.new(pos)
        e.color = (*c, 1)
    nt.links.new(tc.outputs['Generated'], sep.inputs[0])
    nt.links.new(sep.outputs['Y'], diag.inputs[0])
    nt.links.new(sep.outputs['Z'], diag.inputs[2])
    nt.links.new(diag.outputs[0], ramp.inputs['Fac'])
    nt.links.new(ramp.outputs['Color'], bsdf.inputs['Emission Color'])
    bsdf.inputs['Emission Strength'].default_value = 1.0
    red = material('Running lamp red', (.3, .02, .02), 0, .3, emission=(1, .08, .05), strength=8)
    green = material('Running lamp green', (.02, .3, .08), 0, .3, emission=(.1, 1, .3), strength=8)
    white = material('Running lamp white', (.6, .6, .55), 0, .3, emission=(1, .95, .85), strength=8)

    _BOW['objs'], _BOW['cache'] = list(kit.hull), {}
    hull = _hull_mesh(kit)
    kit.hull.append(hull)
    deg = math.radians

    # Pale plating over the starboard crown: the sprite's cream half of the hull.
    pale = _Acc()
    xs = [8.0+k*(23.6-8.0)/16 for k in range(17)]
    _strip(pale, xs, [deg(a) for a in range(96, 171, 5)], -.02, .08)
    pale.build(kit, 'Bridge hull pale crown plating', kit.pale, 0)

    # Plating seams: dark rings over the upper hull, and a crown seam fore and aft.
    seams = _Acc()
    for x in (9.0, 13.5, 18.0, 22.5):
        _strip(seams, [x-.07, x+.07], [deg(a) for a in range(20, 161, 10)], .06, .14)
    _strip(seams, [x/2 for x in range(16, 50)], [deg(88.8), deg(91.2)], .06, .14)
    seams.build(kit, 'Bridge hull plating seams', kit.dark, 0)

    # Bridge windscreen: six flat panes across the raked forward face - the dark
    # band the sprite shows at X 27..28.5 and forward. Pass 6 (owner, twice: the
    # windows looked wonky with the ribbon angling; they must be horizontal seen
    # from ahead): the glazing is laid out in height and plan Y, not in station
    # and section angle. Head, sill, brow and sill rail are level lines (constant
    # Z), mullions are vertical (constant Y), and every point is found on the
    # rake by _yz, so on a rounded nose the head simply sits a little further aft
    # at the shoulders instead of dropping into an arc.
    half = 4.0
    zh, zs = BASE_Z+13.0, BASE_Z+10.7                       # head and sill heights
    bounds = [BRIDGE_CY+half-2*half*k/6 for k in range(7)]   # port to starboard
    mw = .13                                                 # mullion half-width
    rows = [zh-(zh-zs)*k/2 for k in range(3)]                # top down
    panes, frame = _Acc(), _Acc()
    for y0, y1 in zip(bounds, bounds[1:]):
        _strip(panes, rows, [y0-mw, y1+mw], .04, .11, pt=_yz)
    for y in bounds:                                         # mullions, the end ones the jambs
        w = mw*(1.6 if y in (bounds[0], bounds[-1]) else 1)
        _strip(frame, [zh+.26, (zh+zs)/2, zs-.26], [y+w, y-w], 0, .18, pt=_yz)
    rail = [bounds[0]+mw*1.6-(2*half+mw*3.2)*k/14 for k in range(15)]
    for z in (zh+.12, zs-.12):                               # head and sill rails
        _strip(frame, [z+.14, z-.14], rail, 0, .2, pt=_yz)
    panes.build(kit, 'Bridge glazing band', glass, 0)
    frame.build(kit, 'Bridge glazing frame', kit.grey, .02)
    # Brow visor: an eyebrow of hull plating standing out over the head of the
    # windscreen, its lip carried a little forward to shade the glass; level,
    # and a little past the jambs.
    brow = _Acc()
    by = [BRIDGE_CY+half+.6-(2*half+1.2)*k/16 for k in range(17)]
    _strip(brow, [zh+.95, zh+.55], by, 0, .22, pt=_yz)
    _strip(brow, [zh+.55, zh+.3], by[1:-1], .1, .32, pt=_yz)
    brow.build(kit, 'Bridge brow visor', kit.tan, .03)
    # Wiper park and washer rail along the sill, level beneath it.
    sill = _Acc()
    _strip(sill, [zs-.3, zs-.6], rail, 0, .14, pt=_yz)
    sill.build(kit, 'Bridge windscreen sill rail', kit.steel, 0)
    # Bridge wing windows on each flank just aft of the windscreen.
    side, sframe = _Acc(), _Acc()
    for t0, t1 in ((22, 34), (146, 158)):
        _strip(side, [24.6+k*.6 for k in range(5)], [deg(t0+(t1-t0)*k/3) for k in range(4)], -.02, .06)
        for x in (24.45, 25.6, 27.15):
            _strip(sframe, [x-.12, x+.12], [deg(t0-1.2+(t1-t0+2.4)*k/3) for k in range(4)], 0, .16)
        for t in (t0-1.2, t1+1.2):
            _strip(sframe, [24.35+k*.7 for k in range(5)], [deg(t-1.0), deg(t+1.0)], 0, .16)
    side.build(kit, 'Bridge wing windows', glass, 0)
    sframe.build(kit, 'Bridge wing window frames', kit.grey, .02)

    # Side viewports down the hull flanks: the crew spaces the description asks for.
    ports, rims = _Acc(), _Acc()
    for x in (10.5, 13.0, 15.5, 18.0, 20.5):
        for t in (deg(50), deg(130)):
            c = _pt(x, t, 0)
            nrm = (_pt(x, t, 1)-c).normalized()
            rims.cyl(c+nrm*.04, nrm, .55, .08, 10)
            ports.cyl(c+nrm*.12, nrm, .4, .05, 10)
    rims.build(kit, 'Bridge hull viewport rims', kit.steel, 0)
    ports.build(kit, 'Bridge hull viewports', glass, 0)

    # Nose crown: the roof hatch (the sprite's dark mark at X 30..31) and an air handler.
    hx = 25.8
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
    ax = 22.4
    atop = _pt(ax, deg(90)).z
    hvac = _Acc()
    hvac.box((ax, BRIDGE_CY-1.6, atop+.25), (1.8, 1.6, .7))
    hvac.build(kit, 'Bridge roof air handler', kit.grey, .06)
    grille = _Acc()
    for i in range(5):
        grille.box((ax-.6+i*.3, BRIDGE_CY-1.6, atop+.62), (.12, 1.4, .08))
    grille.build(kit, 'Bridge roof air handler grille', kit.dark, 0)

    # Antenna mast on the crown aft of the glazing: tripod foot, pole, yard, whips.
    mx, my = 20.6, BRIDGE_CY+1.8
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
        c = _pt(RAKE_X[1]+.75, t, 0)
        nrm = (_pt(RAKE_X[1]+.75, t, 1)-c).normalized()
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
    # Plinth (pass 3 round 3, owner: the machinery is not cleanly attached): one
    # chamfered block under the chassis, its foot sunk into the bridge flank and
    # the bow shoulder it stands across, with a welded base flange laid on that
    # skin by ray-cast - not a round column on a flat flange tilted on a curve.
    PX, PY = (31.5, 33.0), (-8.9, -6.9)
    ch = .45
    plan = [(PX[0]+ch, PY[0]), (PX[1]-ch, PY[0]), (PX[1], PY[0]+ch), (PX[1], PY[1]-ch),
            (PX[1]-ch, PY[1]), (PX[0]+ch, PY[1]), (PX[0], PY[1]-ch), (PX[0], PY[0]+ch)]
    ring = []
    for (ax, ay), (bx, by) in zip(plan, plan[1:]+plan[:1]):
        n = max(1, round(math.hypot(bx-ax, by-ay)/.3))
        ring += [(ax+(bx-ax)*k/n, ay+(by-ay)*k/n) for k in range(n)]
    # Walls straight down from the collar to the skin, the foot following the skin
    # point by point, so no flat underside is left standing off a curved hull.
    m = len(ring)
    skin = [kit.surface(x, y)[0].z for x, y in ring]
    pv = [(x, y, z-.25) for (x, y), z in zip(ring, skin)]+[(x, y, zb+.15) for x, y in ring]
    pf = [tuple(reversed(range(m))), tuple(range(m, 2*m))]+[(i, (i+1) % m, m+(i+1) % m, m+i) for i in range(m)]
    kit.mesh('Mast starboard sensor plinth', pv, pf, kit.grey, .03)
    # Base flange: a band round the plinth's foot, following the skin.
    cx, cy = sum(PX)/2, sum(PY)/2
    fv, ff = [], []
    m = len(ring)
    for x, y in ring:
        dx, dy = x-cx, y-cy
        L = math.hypot(dx, dy)
        ox, oy = x+dx/L*.3, y+dy/L*.3
        zi = kit.surface(x, y)[0].z
        zo = (kit.surface(ox, oy) or (Vector((0, 0, zi-.3)),))[0].z
        fv += [(x, y, zi-.15), (ox, oy, zo-.15), (ox, oy, zo+.12), (x, y, zi+.3)]
    for i in range(m):
        a0, a1 = 4*i, 4*((i+1) % m)
        ff += [(a0+q, a0+(q+1) % 4, a1+(q+1) % 4, a1+q) for q in range(4)]
    kit.mesh('Mast starboard sensor plinth base flange', fv, ff, kit.steel, 0)
    fb = _Acc()
    for x, y in ring[::3]:
        dx, dy = x-cx, y-cy
        L = math.hypot(dx, dy)
        bx, by = x+dx/L*.22, y+dy/L*.22
        fb.cyl((bx, by, kit.surface(bx, by)[0].z+.2), (0, 0, 1), .09, .07, 6)
    fb.build(kit, 'Mast starboard sensor plinth flange bolts', kit.dark, 0)
    # Plinth access panel and a cable gland on its outboard face, a collar under the chassis.
    pan = _Acc()
    pan.box((32.25, PY[0]-.05, (pz+zb)/2+.3), (1.1, .14, 1.5))
    for x in (31.85, 32.65):
        for z in ((pz+zb)/2-.3, (pz+zb)/2+.9):
            pan.cyl((x, PY[0]-.12, z), (0, 1, 0), .07, .05, 6)
    pan.cyl((31.85, PY[0]-.15, pz+.4), (0, 1, 0), .25, .15, 8)
    pan.build(kit, 'Mast starboard sensor plinth access panel', kit.steel, 0)
    collar = _Acc()
    collar.box((cx, cy, zb-.12), (PX[1]-PX[0]+.3, PY[1]-PY[0]+.3, .24))
    collar.build(kit, 'Mast starboard sensor plinth collar', kit.steel, .03)

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

    # Gusset: a web from the plinth's forward face out under the cantilevered chassis.
    gv = []
    for y in (-8.75, -8.45):
        gv += [(PX[1]-.1, y, zb-2.6), (PX[1]-.1, y, zb+.05), (37.6, y, zb+.05)]
    kit.mesh('Mast starboard sensor head gusset', gv, [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)],
             kit.steel, 0)

    # Radome over the plinth.
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
