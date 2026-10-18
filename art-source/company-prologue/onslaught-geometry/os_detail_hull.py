"""Surface detail on the hull's flanks and underside (os_hull belly, prow and core sides): plating seams, hatches, keel conduits, belly access panels.

Owner, after round 11: "I'm liking the silhouette of this ship. The next passes
need to focus on that detail on the hull, exposed machinery, piping, hatches,
and armor." The silhouette is locked: detail stands at most a little proud of
the surface it sits on, never changes the outline, and keeps clear of the
turret rings (os_turrets.SLOTS, RING) and the running lights. Every object
built here is named 'Detail <area> ...'.

Detail pass 1 (hull flanks and underside). The sprite only shows the top, so the
flanks take their rhythm from the prow's orange band edge: dark seam notches at
about X 172, 148 and 120 (os_hull.FORE_W's plate breaks), dark vent cut-outs at
the band's edge near X 138 and 116. The underside follows the hull's own planes
(round 11: keel band, bilge planes, chines):

  prow sides     seam straps on the plate breaks with bolt rows, a horizontal
                 seam strap, one personnel airlock a side (X 160, under the WS
                 008/009 gun deck), a service hatch, a vent louvre and vent ports
                 where the sprite cuts its band, a grapple hardpoint;
  forefoot       on the deadrise forward of the belly: an anchor/grapple
                 hardpoint and vent ports;
  bilge planes   transverse seam straps on the belly's stations and one
                 longitudinal strap, cutting each plane into a course of plates;
                 each plate carries one thing (access hatch, heat-sink louvre,
                 drain ports, grapple hardpoint); a main conduit and a smaller
                 line run the bilge chine from a junction box under the forefoot
                 to one at the aft knuckle, clamped every few metres;
  keel shoe      the docking collar amidships, keel access hatches and drain
                 ports on the centreline;
  waist bellies  radiator louvre banks under each waist valley.

Every part is seated by ray casts on the built hull and armour (one BVH per
solid, as os_fittings does): a footprint is cast at its centre, its corners and
edge midpoints, sunk below the deepest of them and standing its stated height
proud of the highest, and refused where they disagree (a step or a chine under
it), so nothing floats or straddles a knuckle.
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

from os_hull import WAIST_OUT, _table, fore
from os_armour import _belly_at
from os_kit import material
from os_turrets import SLOTS, RING

_TREES = []
REFUSED = []
X = Vector((1, 0, 0))
Z = Vector((0, 0, 1))


def _trees():
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _TREES.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.split(' ')[0] not in ('Hull', 'Armour', 'Bridge', 'Machinery', 'Engine'):
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        # the renderer's own triangulation: the lofts' quads are twisted, and a BVH that
        # splits them on the other diagonal is up to half a unit off the drawn surface
        me.calc_loop_triangles()
        _TREES.append((ob.name, BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                                     [tuple(t.vertices) for t in me.loop_triangles])))
        ev.to_mesh_clear()


def _cast(origin, direction):
    o, d = Vector(origin), Vector(direction).normalized()
    best = None
    for name, t in _TREES:
        p, n, _, dist = t.ray_cast(o, d)
        if p is not None and (best is None or dist < best[0]):
            best = (dist, p, -n if n.dot(d) > 0 else n, name)
    return best


def _clear_of_turrets(p):
    for sid, (sx, sy), angle, arc, size, typ, weapon in SLOTS:
        if (p.x-sx)**2+(p.y-sy)**2 < (1.3*RING[size]+2.0)**2 and p.z > -6.0:
            return False
    return True


class Frame:
    """A seated footprint: origin on the surface, u along, v across, n outward;
    lo/hi the deepest and highest surface under its footprint (along n)."""

    def __init__(self, p, u, v, n, lo=0.0, hi=0.0):
        self.p, self.u, self.v, self.n, self.lo, self.hi = p, u, v, n, lo, hi

    def at(self, a, b, c):
        return self.p+self.u*a+self.v*b+self.n*c

    def turned(self, a, du=0.0, dv=0.0):
        """The same seat, moved (du, dv) and turned by angle a about n."""
        u = self.u*math.cos(a)+self.v*math.sin(a)
        return Frame(self.at(du, dv, 0), u, self.n.cross(u), self.n, self.lo, self.hi)


def seat(origin, inward, along, hu, hv, spread=1.0, tag='', up=False):
    """Cast from `origin` along `inward`; frame the hit with u along `along` projected on the
    surface; check the footprint (+-hu, +-hv) by casting at its corners and edge midpoints.
    `up` turns v to point upward (doors)."""
    hit = _cast(origin, inward)
    if hit is None:
        REFUSED.append((tag, 'no hit'))
        return None
    _, p, n, _ = hit
    n = n.normalized()
    a = Vector(along)
    u = (a-n*a.dot(n)).normalized()
    v = n.cross(u).normalized()
    if up and v.z < 0:
        v = -v
    offs = []
    for cu, cv in ((-1, -1), (1, -1), (1, 1), (-1, 1), (0, -1), (0, 1), (-1, 0), (1, 0), (0, 0)):
        q = p+u*cu*hu+v*cv*hv
        h = _cast(q+n*6.0, -n)
        if h is None or h[0] > 12.0:
            REFUSED.append((tag, 'corner miss'))
            return None
        offs.append(6.0-h[0])
    lo, hi = min(offs), max(offs)
    if hi-lo > spread:
        REFUSED.append((tag, f'spread {hi-lo:.2f}'))
        return None
    if not _clear_of_turrets(p):
        REFUSED.append((tag, 'turret'))
        return None
    return Frame(p, u, v, n, lo, hi)


class Family:
    """One mesh per family per side: boxes and prisms accumulated, built once."""

    def __init__(self, name, mat, bevel=0.0):
        self.name, self.mat, self.bevel = name, mat, bevel
        self.verts, self.faces = [], []

    def box(self, c, axes, half):
        """A box centred at c with orthonormal axes (a, b, n) and half sizes."""
        a, b, n = axes
        i = len(self.verts)
        for sa, sb, sn in ((-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                           (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)):
            self.verts.append(tuple(c+a*sa*half[0]+b*sb*half[1]+n*sn*half[2]))
        self.faces += [(i, i+3, i+2, i+1), (i+4, i+5, i+6, i+7), (i, i+1, i+5, i+4),
                       (i+1, i+2, i+6, i+5), (i+2, i+3, i+7, i+6), (i+3, i, i+4, i+7)]

    def fbox(self, f, u0, u1, v0, v1, h0, h1, tilt=0.0, flat=False):
        """A box in a seated frame: u/v ranges, from h0 under the deepest point to h1 over
        the highest (heights along n); `flat` measures both from the highest (a part that
        stands on others, never reaching the hull). `tilt` rolls it about u (louvre slats)."""
        lo, hi = (f.hi if flat else f.lo)+h0, f.hi+h1
        v, n = f.v, f.n
        c = f.at((u0+u1)/2, (v0+v1)/2, (lo+hi)/2)
        if tilt:
            ca, sa = math.cos(tilt), math.sin(tilt)
            v, n = v*ca+n*sa, -v*sa+n*ca
        self.box(c, (f.u, v, n), ((u1-u0)/2, (v1-v0)/2, (hi-lo)/2))

    def cyl(self, a, b, r, sides=10):
        a, b = Vector(a), Vector(b)
        ax = (b-a).normalized()
        ref = Vector((0, 0, 1)) if abs(ax.z) < .9 else Vector((1, 0, 0))
        e1 = ax.cross(ref).normalized()
        e2 = ax.cross(e1)
        i = len(self.verts)
        for end in (a, b):
            for k in range(sides):
                t = 2*math.pi*(k+.5)/sides
                self.verts.append(tuple(end+(e1*math.cos(t)+e2*math.sin(t))*r))
        self.faces.append(tuple(range(i+sides-1, i-1, -1)))
        self.faces.append(tuple(range(i+sides, i+2*sides)))
        for k in range(sides):
            k1 = (k+1) % sides
            self.faces.append((i+k, i+k1, i+sides+k1, i+sides+k))

    def fcyl(self, f, cu, cv, r, h0, h1, sides=10):
        """A cylinder standing on a seated frame along n."""
        base = f.p+f.u*cu+f.v*cv
        self.cyl(base+f.n*(f.lo+h0), base+f.n*(f.hi+h1), r, sides)

    def build(self, kit):
        if not self.faces:
            return None
        return kit.mesh(self.name, self.verts, self.faces, self.mat, self.bevel)


def conformal(fam, P, nu, nv, h, sink=.35, jump=2.0, tag=""):
    """A plate that follows the surface exactly: P(a, b) (a, b in 0..1) gives a point near the
    surface and its outward direction; each grid point is cast onto the hull, the top stands
    h proud of it and the bottom is sunk `sink` into it. Refused where a cast misses or jumps
    (a step under the plate)."""
    grid = []
    for i in range(nu+1):
        row = []
        for j in range(nv+1):
            p, o = P(i/nu, j/nv)
            hit = _cast(p+o*8.0, -o)
            if hit is None or hit[0] > 14.0:
                REFUSED.append((tag, 'patch miss'))
                return False
            row.append((hit[0], hit[1], o))
        grid.append(row)
    # a step under the plate shows as a kink in the cast distances (their second difference);
    # a plane merely sloping against the cast direction is a steady trend and passes
    for i in range(nu+1):
        for j in range(nv+1):
            for di, dj in ((1, 0), (0, 1)):
                if 0 <= i-di and i+di <= nu and 0 <= j-dj and j+dj <= nv and \
                        abs(grid[i-di][j-dj][0]-2*grid[i][j][0]+grid[i+di][j+dj][0]) > jump:
                    REFUSED.append((tag, 'patch jump'))
                    return False
    base = len(fam.verts)
    m = (nu+1)*(nv+1)
    fam.verts += [tuple(q+o*h) for row in grid for _, q, o in row]
    fam.verts += [tuple(q-o*sink) for row in grid for _, q, o in row]
    idx = lambda i, j: base+i*(nv+1)+j
    quads = []
    for i in range(nu):
        for j in range(nv):
            quads.append((idx(i, j), idx(i+1, j), idx(i+1, j+1), idx(i, j+1)))
    fam.faces += quads+[tuple(reversed([v+m for v in q])) for q in quads]
    ring = [(i, 0) for i in range(nu)]+[(nu, j) for j in range(nv)]+[(i, nv) for i in range(nu, 0, -1)] + \
           [(0, j) for j in range(nv, 0, -1)]
    for k in range(len(ring)):
        a, b = idx(*ring[k]), idx(*ring[(k+1) % len(ring)])
        fam.faces.append((b, a, a+m, b+m))
    return True


# --- composite parts ------------------------------------------------------------

def hatch(F, f, hu, hv):
    """A bolted access hatch: a frame plate, a raised leaf, two hinge knuckles and a grab bar."""
    F['frame'].fbox(f, -hu, hu, -hv, hv, -.3, .3)
    F['leaf'].fbox(f, -hu+.35, hu-.35, -hv+.35, hv-.35, -.3, .5)
    for s in (-.55, .55):
        F['steel'].fbox(f, -hu-.15, -hu+.45, s*hv-.35, s*hv+.35, -.2, .7)
    F['steel'].fbox(f, hu-.8, hu-.5, -hv*.4, hv*.4, .3, .85)


def airlock(F, f, w=1.4, h=2.5):
    """A personnel airlock: a heavy frame with a hood over it, a dark door with a viewport
    strip, a lamp over it and a grab rail beside it. u along the hull, v up the door."""
    fw = .55
    F['frame'].fbox(f, -w/2-fw, w/2+fw, -h/2-fw, -h/2, -.3, .85)       # sill
    F['frame'].fbox(f, -w/2-fw-.2, w/2+fw+.2, h/2, h/2+fw+.15, -.3, 1.05)  # hood
    F['frame'].fbox(f, -w/2-fw, -w/2, -h/2, h/2, -.3, .85)              # jambs
    F['frame'].fbox(f, w/2, w/2+fw, -h/2, h/2, -.3, .85)
    F['door'].fbox(f, -w/2, w/2, -h/2, h/2, -.3, .25)
    F['steel'].fbox(f, -w/2+.3, w/2-.3, h*.18, h*.18+.3, .1, .4)        # viewport strip
    F['steel'].fbox(f, -w/2+.15, w/2-.15, -.12, .12, .1, .38)           # door seam bar
    F['lamp'].fbox(f, -.3, .3, h/2+fw+.3, h/2+fw+.65, -.2, .35)
    F['steel'].fbox(f, w/2+fw+.45, w/2+fw+.75, -h*.35, h*.35, .55, .85)  # grab rail
    for sv in (-1, 1):
        F['steel'].fbox(f, w/2+fw+.4, w/2+fw+.8, sv*h*.35-.15, sv*h*.35+.15, -.2, .7)


def louvre(F, f, hu, hv, n_slats, across=False):
    """A heat-sink louvre bank: a heavy frame and angled slats (rolled 35 deg) in it.
    Slats run along u, stacked across v; `across` turns them to run across v."""
    t = .55
    F['frame'].fbox(f, -hu, hu, -hv, -hv+t, -.3, .7)
    F['frame'].fbox(f, -hu, hu, hv-t, hv, -.3, .7)
    F['frame'].fbox(f, -hu, -hu+t, -hv+t, hv-t, -.3, .7)
    F['frame'].fbox(f, hu-t, hu, -hv+t, hv-t, -.3, .7)
    F['dark'].fbox(f, -hu+t, hu-t, -hv+t, hv-t, -.3, .05)
    if across:
        g, a, b = f.turned(math.pi/2), hv, hu
    else:
        g, a, b = f, hu, hv
    pitch = (2*b-2*t)/n_slats
    for k in range(n_slats):
        c = -b+t+pitch*(k+.5)
        F['slat'].fbox(g, -a+t, a-t, c-pitch*.45, c+pitch*.45, .1, .32, tilt=math.radians(35), flat=True)


def port(F, f, r=.75):
    """A drain or vent port: a raised steel rim round a dark throat with a cross bar."""
    F['steel'].fcyl(f, 0, 0, r+.35, -.3, .45, 12)
    F['dark'].fcyl(f, 0, 0, r, -.3, .5, 12)
    F['steel'].fbox(f, -r, r, -.13, .13, .3, .6)


def hardpoint(F, f):
    """A docking/grapple hardpoint: a bolted base plate, a boss, and a lifting eye on it."""
    F['frame'].fbox(f, -1.7, 1.7, -1.7, 1.7, -.3, .45)
    for cu in (-1.25, 1.25):
        for cv in (-1.25, 1.25):
            F['steel'].fbox(f, cu-.22, cu+.22, cv-.22, cv+.22, .2, .62)
    F['steel'].fcyl(f, 0, 0, .9, .2, 1.0, 10)
    F['steel'].fbox(f, -.75, -.45, -.16, .16, .9, 1.55)
    F['steel'].fbox(f, .45, .75, -.16, .16, .9, 1.55)
    F['steel'].fbox(f, -.75, .75, -.16, .16, 1.28, 1.55)


def strap(F, f, hu, hv, h=.3):
    F['seam'].fbox(f, -hu, hu, -hv, hv, -.3, h)


def bolts(F, f, hu, off, pitch=1.8):
    """Two rows of bolt heads either side of a strap running along u."""
    k = max(1, int(2*hu/pitch))
    for i in range(k+1):
        cu = -hu+2*hu*i/k
        for s in (-1, 1):
            F['steel'].fbox(f, cu-.17, cu+.17, s*off-.17, s*off+.17, -.2, .4)


# --- surface parameterisations ------------------------------------------------

def side_point(x, h, s):
    """Prow/core side face: h 0 at the lower chine, 1 at the upper; (point, outward guess)."""
    fo = fore(x)
    y = fo['w']-fo['fl']*(1-h)
    z = fo['zl']+(fo['zk']-fo['zl'])*h
    n = Vector((0, fo['zk']-fo['zl'], -fo['fl'])).normalized()
    return Vector((x, s*y, z)), Vector((0, s*n.y, n.z))


def bilge_point(x, t, s):
    """Belly bilge plane: t 0 at the keel chine, 1 at the bilge chine."""
    sec = _belly_at(x)
    m = len(sec)//2
    _, c3, kp = sec[m:]
    y, z = kp[0]+(c3[0]-kp[0])*t, kp[1]+(c3[1]-kp[1])*t
    n = Vector((0, c3[1]-kp[1], -(c3[0]-kp[0]))).normalized()
    return Vector((x, s*y, z)), Vector((0, s*n.y, n.z))


def deadrise_point(x, t, s):
    """Forebody deadrise (forward of the belly): t 0 at the keel flat, 1 at the lower chine."""
    fo = fore(x)
    a, b = (fo['kw'], fo['keel']), (fo['w']-fo['fl'], fo['zl'])
    y, z = a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t
    n = Vector((0, b[1]-a[1], -(b[0]-a[0]))).normalized()
    return Vector((x, s*y, z)), Vector((0, s*n.y, n.z))


def on(pt, out, along, hu, hv, spread=1.0, tag='', up=False, reach=10.0):
    return seat(pt+out*reach, -out, along, hu, hv, spread, tag, up)


def pipe_run(F, pts, r, standoff, clamp_every=8.0, sides=10):
    """A conduit through seated surface points [(p, n)]: the pipe's axis stands `standoff` off
    the surface, with a flange at every inner node and a saddle clamp every few units."""
    axis = [p+n*standoff for p, n in pts]
    for a, b in zip(axis, axis[1:]):
        F['pipe'].cyl(a, b, r, sides)
    for i in range(1, len(axis)-1):
        d = (axis[i+1]-axis[i-1]).normalized()
        F['steel'].cyl(axis[i]-d*.35, axis[i]+d*.35, r*1.4, sides)
    for (pa, na), (pb, nb), a, b in zip(pts, pts[1:], axis, axis[1:]):
        k = max(1, int((b-a).length/clamp_every))
        d = (b-a).normalized()
        for j in range(1, k+1):
            t = j/(k+1)
            c = a+(b-a)*t
            hit = _cast(c, -(na*(1-t)+nb*t).normalized())
            if hit is None or hit[0] > standoff+2.0:
                continue
            p, n = hit[1], hit[2].normalized()
            side = n.cross(d).normalized()
            dd = side.cross(n).normalized()
            depth = (c-p).dot(n)+r+.25
            F['steel'].box(p+n*(depth-.3)/2, (dd, side, n), (.3, r+.3, (depth+.3)/2))


def _side_len(x):
    fo = fore(x)
    return math.hypot(fo['zk']-fo['zl'], fo['fl'])


def _bilge_len(x):
    sec = _belly_at(x)
    m = len(sec)//2
    _, c3, kp = sec[m:]
    return math.hypot(c3[0]-kp[0], c3[1]-kp[1])


def build(kit):
    _trees()
    REFUSED.clear()
    seam_mat = material('Hegemony seam strap', (.075, .05, .045), .4, .6)
    leaf_mat = material('Hegemony hatch plate', (.20, .13, .105), .35, .55)
    doubler_mat = material('Hegemony doubler plate', (.115, .073, .062), .35, .6)
    pipe_mat = material('Hull conduit steel', (.16, .15, .15), .8, .4)
    made = []

    def finish(F):
        for fam in F.values():
            ob = fam.build(kit)
            if ob:
                made.append(ob)

    for side, s in (('port', 1), ('starboard', -1)):
        F = {'seam': Family(f'Detail hull {side} seam straps', seam_mat, 0),
             'doubler': Family(f'Detail hull {side} doubler plates', doubler_mat, 0),
             'frame': Family(f'Detail hull {side} hatch frames', kit.plate, .08),
             'leaf': Family(f'Detail hull {side} hatch leaves', leaf_mat, .08),
             'door': Family(f'Detail hull {side} airlock door', kit.dark, .05),
             'steel': Family(f'Detail hull {side} fittings', kit.steel, 0),
             'dark': Family(f'Detail hull {side} vent throats', kit.dark, 0),
             'slat': Family(f'Detail hull {side} louvre slats', kit.steel, .04),
             'lamp': Family(f'Detail hull {side} airlock lamp', kit.lamp, 0),
             'pipe': Family(f'Detail hull {side} keel conduits', pipe_mat, 0)}

        def side_patch(xa, xb, ha, hb):
            return lambda a, b: side_point(xa+(xb-xa)*a, ha+(hb-ha)*b, s)

        def bilge_patch(xa, xb, ta, tb):
            return lambda a, b: bilge_point(xa+(xb-xa)*a, ta+(tb-ta)*b, s)

        def item(kind, f, *a):
            if f is None:
                return
            if kind == 'hatch':
                hatch(F, f, *a)
            elif kind == 'louvre':
                louvre(F, f, *a)
            elif kind == 'port':
                port(F, f, *a)
            elif kind == 'hardpoint':
                hardpoint(F, f)

        # --- prow sides: plates broken on FORE_W's stations (the sprite's band notches) ---
        for x in (172.0, 148.0, 120.0):
            if not conformal(F['seam'], side_patch(x+.38, x-.38, .06, .9), 2, 8, .35, tag=f'prow seam {x}'):
                continue
            for k in range(7):
                h = .12+.75*k/6
                for dx in (1.0, -1.0):
                    p, o = side_point(x+dx, h, s)
                    f = on(p, o, X, .18, .18, .6, 'bolt')
                    if f:
                        F['steel'].fbox(f, -.18, .18, -.18, .18, -.2, .38)
        for xa, xb in ((198.0, 172.4), (171.6, 148.4), (147.6, 120.4), (119.6, 106.0)):
            dh = .35/_side_len((xa+xb)/2)
            conformal(F['seam'], side_patch(xa, xb, .62-dh, .62+dh), 6, 1, .3, tag=f'prow hseam {xa}')
        # a darker doubler on the forward lower plate, the airlock and its ports in the
        # next, a hatch and a grapple point under WS 019's deck, a louvre at the root
        conformal(F['doubler'], side_patch(195.0, 175.0, .12, .55), 6, 3, .3, tag='prow doubler')
        # the airlock stands in an armoured surround plate, so it reads past a few hundred metres
        if conformal(F['leaf'], side_patch(162.6, 157.4, .1, .62), 3, 4, .28, tag='airlock surround'):
            p, o = side_point(160.0, .3, s)
            f = on(p, o, X, 1.7, 2.1, 1.2, 'airlock', up=True)
            if f:
                f.hi += .28     # stand on the surround (and sink through it)
                airlock(F, f)
        for x in (166.0, 154.0):
            p, o = side_point(x, .8, s)
            item('port', on(p, o, X, .9, .9, 1.0, f'prow port {x}'), .5)
        p, o = side_point(140.0, .32, s)
        item('hatch', on(p, o, X, 1.6, 1.3, 1.0, 'prow hatch', up=True), 1.6, 1.3)
        p, o = side_point(128.0, .3, s)
        item('hardpoint', on(p, o, X, 1.7, 1.7, 1.0, 'prow hardpoint'))
        p, o = side_point(134.0, .8, s)
        item('port', on(p, o, X, .9, .9, 1.0, 'prow port 134'), .5)
        p, o = side_point(113.0, .32, s)
        item('louvre', on(p, o, X, 3.6, 2.2, 1.0, 'prow louvre', up=True), 3.6, 2.2, 5)
        p, o = side_point(113.0, .8, s)
        item('port', on(p, o, X, .9, .9, 1.0, 'prow port 113'), .5)

        # --- the forefoot's deadrise, forward of the belly ---
        def dead_patch(xa, xb, ta, tb):
            return lambda a, b: deadrise_point(xa+(xb-xa)*a, ta+(tb-ta)*b, s)
        for x in (196.0, 172.0):
            conformal(F['seam'], dead_patch(x+.38, x-.38, .15, .9), 1, 8, .35, tag=f'forefoot seam {x}')
        conformal(F['doubler'], dead_patch(194.5, 189.5, .28, .8), 3, 5, .3, tag='forefoot doubler')
        conformal(F['doubler'], dead_patch(182.0, 177.5, .2, .85), 3, 5, .3, tag='forefoot doubler aft')
        p, o = deadrise_point(186.0, .55, s)
        item('hardpoint', on(p, o, X, 1.7, 1.7, 1.0, 'forefoot hardpoint'))
        for x in (199.0, 175.0):
            p, o = deadrise_point(x, .5, s)
            item('port', on(p, o, X, .9, .9, 1.0, f'forefoot port {x}'), .6)

        # --- bilge planes: a course of plates on the belly's stations ---
        seams = (150.0, 128.0, 104.0, 74.0, 40.0, 8.0)
        for x in seams+(-20.0,):
            # aft, the aft body comes down over the plane's upper part: stop the strap short
            for t1 in (.86, .7, .55, .42):
                if conformal(F['seam'], bilge_patch(x+.4, x-.4, .2, t1), 2, 10, .38, tag=f'bilge seam {x}'):
                    break
            else:
                continue
            for k in range(int((t1-.2)/.07)+1):
                t = .24+(t1-.28)*k/max(1, int((t1-.2)/.07))
                for dx in (1.05, -1.05):
                    p, o = bilge_point(x+dx, t, s)
                    f = on(p, o, X, .18, .18, .6, 'bolt')
                    if f:
                        F['steel'].fbox(f, -.18, .18, -.18, .18, -.2, .4)
        for xa, xb in zip(seams, seams[1:]):
            dt = .35/_bilge_len((xa+xb)/2)
            conformal(F['seam'], bilge_patch(xa-.4, xb+.4, .5-dt, .5+dt), 8, 1, .32, tag=f'bilge lseam {xa}')
        conformal(F['doubler'], bilge_patch(126.5, 105.5, .55, .82), 6, 3, .3, tag='doubler 128')
        conformal(F['doubler'], bilge_patch(72.5, 41.5, .23, .46), 8, 3, .3, tag='doubler 74')
        conformal(F['doubler'], bilge_patch(148.5, 129.5, .55, .82), 5, 3, .3, tag='doubler 150')

        def put(kind, x, t, *a, n=1):
            hu, hv = {'port': (1.1, 1.1), 'hardpoint': (1.7, 1.7)}.get(kind, a[:2])
            for k in range(n):
                xk = x+(k-(n-1)/2)*(2*hu+.4)
                p, o = bilge_point(xk, t, s)
                item(kind, on(p, o, X, hu, hv, 1.0, f'bilge {kind} {xk}'), *a)
        put('port', 143.0, .3, .75)
        put('port', 135.0, .3, .75)
        put('hatch', 116.0, .34, 2.2, 1.6)
        put('louvre', 89.0, .32, 2.6, 2.6, 5, n=4)
        put('hatch', 95.0, .7, 1.6, 1.2)
        put('port', 84.0, .68, .75)
        put('port', 79.0, .68, .75)
        put('hatch', 64.0, .68, 3.0, 2.2)
        put('hatch', 49.0, .68, 1.6, 1.2)
        put('port', 30.0, .3, .75)
        put('port', 24.0, .3, .75)
        put('hatch', 15.0, .32, 1.8, 1.4)
        put('louvre', 24.0, .68, 2.6, 2.8, 5, n=4)
        put('hardpoint', -2.0, .3)
        put('hatch', -14.0, .26, 1.6, 1.2)

        # --- the conduits along the bilge chine, junction box to junction box ---
        for t, r, so, tag in ((.93, .55, .85, 'main'), (.865, .35, .6, 'branch')):
            pts = []
            for x in (158.0, 150.0, 128.0, 104.0, 88.0, 74.0, 60.0, 40.0, 20.0, 0.0, -18.0, -28.0):
                p, o = bilge_point(x, t, s)
                hit = _cast(p+o*8.0, -o)
                if hit is None or hit[0] > 14.0:
                    REFUSED.append((f'conduit {tag} {x}', 'miss'))
                    continue
                pts.append((hit[1], hit[2].normalized()))
            pipe_run(F, pts, r, so)
        for x in (160.5, -30.5):
            p, o = bilge_point(x, .9, s)
            f = on(p, o, X, 2.0, 1.6, 2.2, f'junction {x}')
            if f:
                F['frame'].fbox(f, -2.0, 2.0, -1.6, 1.6, -.3, 1.5)
                F['steel'].fbox(f, -1.6, 1.6, -1.2, 1.2, 1.2, 1.75, flat=True)

        # --- waist bellies: radiator banks under the valley ---
        for x in (84.0, 56.0, 28.0):
            fo = fore(x)
            yc, yo = fo['w']-fo['fl'], _table(WAIST_OUT, x)
            hv = min(3.4, (yo-yc)/2-2.5)
            for k in (-1, 1):
                f = seat((x+k*3.2, s*(yc+yo)/2, -45.0), (0, 0, 1), X, 3.0, hv, 1.0, f'waist louvre {x}')
                if f:
                    louvre(F, f, 3.0, hv, 6, True)
        finish(F)

    # --- keel shoe, on the centreline ---
    F = {'frame': Family('Detail hull keel frames', kit.grey, .08),
         'leaf': Family('Detail hull keel hatch leaves', leaf_mat, .08),
         'steel': Family('Detail hull keel fittings', kit.steel, 0),
         'dark': Family('Detail hull keel throats', kit.dark, 0),
         'pipe': Family('Detail hull keel docking collar', pipe_mat, .05)}

    def keel_at(x, hu, hv, tag):
        return seat((x, 0.0, -80.0), (0, 0, 1), X, hu, hv, .8, tag)
    f = keel_at(40.0, 3.6, 3.6, 'docking collar')
    if f:
        # a ring of eight segments round a dark mating face, and four latch blocks
        F['dark'].fcyl(f, 0, 0, 2.3, -.3, .5, 16)
        for k in range(8):
            F['pipe'].fbox(f.turned(2*math.pi*k/8), 2.3, 3.1, -1.05, 1.05, -.3, .95)
        for k in range(4):
            F['steel'].fbox(f.turned(2*math.pi*(k+.5)/4), 3.0, 3.6, -.45, .45, -.3, .7)
    for x in (122.0, 2.0):
        f = keel_at(x, 2.4, 1.8, f'keel hatch {x}')
        if f:
            hatch(F, f, 2.4, 1.8)
    for x in (142.0, 98.0, 70.0, 16.0, -24.0):
        f = keel_at(x, 1.1, 1.1, f'keel port {x}')
        if f:
            port(F, f, .7)
    finish(F)
    print(f'DETAIL HULL: {len(made)} objects; refused {len(REFUSED)}')
    for tag, why in REFUSED:
        print('  refused', tag, why)
    return made
