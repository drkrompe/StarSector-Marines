"""Surface detail on the hull's flanks and underside (os_hull belly, prow and stem sides, forefoot): plate courses, heat-sink banks, machinery bays, conduit bundles, hatches.

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

Detail pass 2 (owner: "bolder, bigger, denser ... so it reads at 1.5 km"). At formation
range a unit is under a pixel, so a big layer goes down first and reads by value, at the
scale of plates and bays (big_layer): brick-bonded courses of plates 10-25 long in
alternating tones (dark brown, bone-brown, blue-grey steel, weathered orange, the base
brown) standing 1.2 proud over a near-black backing, so every gap is a wide dark seam,
with stepped doublers on alternate plates; on the prow and stem flanks two courses either
side of a conduit trench (three conduits, shared brackets, junction boxes at the stem
knuckle and the root) and two heat-sink banks of big fins; on the forefoot two courses;
on each bilge plane three courses, four fin banks, two machinery bays with twin tanks and
one with pump housings (a 3.0 coaming round a dark floor, contents below its rim), a
four-conduit bundle along the bilge chine; broad low keel plates on the keel flat. The
pass-1 parts (bolts, hatches, ports, the airlock, grapple points) are the close-up layer,
re-placed on plate centres and seated on the plates. Aft of X 19 the aft body breaks
through the bilge plane's middle, so the middle course stops there.

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


def _trees(own=False):
    """BVHs of the hull's solids; `own` adds this module's big layer (plates, banks, bays) so
    the small layer seats on top of it."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _TREES.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or (ob.name.split(' ')[0] not in ('Hull', 'Armour', 'Bridge', 'Machinery', 'Engine')
                                 and not (own and ob.name.startswith('Detail hull'))):
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


# --- detail pass 2: the big layer, read at 1.5 km ----------------------------------
# At formation range one unit is under a pixel, so the flanks and the belly are broken up
# at the scale of PLATES and BAYS: courses of armour plates 10-25 long in alternating
# tones, standing 1.2 proud over a near-black backing so every gap between them is a wide
# dark seam; every other plate carries a stepped doubler; heat-sink banks of big fins in
# raised frames; machinery bays (a tall coaming round a dark floor holding tanks and
# pump housings below its rim); conduit bundles in a trench along the prow's side and the
# belly's bilge chine. The pass-1 parts (hatches, ports, bolts, the airlock) are the
# close-up layer and seat on top of the plates afterwards.

def _patch(fn, s):
    """A conformal patch over a surface parameterisation fn(x, t, s): x from xa to xb, t from ta to tb."""
    def make(xa, xb, ta, tb):
        return lambda a, b: fn(xa+(xb-xa)*a, ta+(tb-ta)*b, s)
    return make


def _dead_len(x):
    fo = fore(x)
    return math.hypot(fo['w']-fo['fl']-fo['kw'], fo['zl']-fo['keel'])


def plate_course(G, patch, L, stations, ta, tb, tones, tag, h=1.2, gap=1.0, skip=(), step=2, back=True):
    """A course of plates between `stations` (descending X) across ta..tb: a near-black backing
    over the whole course and a plate inset half a gap from each station; every `step`th plate
    carries a doubler stepped another .8 proud, inset 1.8 all round."""
    for i, (xa, xb) in enumerate(zip(stations, stations[1:])):
        ln = L((xa+xb)/2)
        nu = max(2, int(round((xa-xb)/3.0)))
        nv = max(2, int(round((tb-ta)*ln/3.0)))
        if back:
            conformal(G['back'], patch(xa, xb, ta, tb), nu, nv, .4, tag=f'{tag} back {xa}')
        if (xa, xb) in skip:
            continue
        dt = gap/2/ln
        tone = tones[i % len(tones)]
        ok = conformal(G[tone], patch(xa-gap/2, xb+gap/2, ta+dt, tb-dt), nu, nv, h, tag=f'{tag} plate {xa}')
        if not ok and xa-xb > 8.0:
            # a step under the plate (another mass breaking through): two half plates instead
            xm = (xa+xb)/2
            for x0, x1 in ((xa, xm), (xm, xb)):
                if back:
                    conformal(G['back'], patch(x0, x1, ta, tb), max(2, nu//2), nv, .4, tag=f'{tag} back half {x0}')
                conformal(G[tone], patch(x0-gap/2, x1+gap/2, ta+dt, tb-dt), max(2, nu//2), nv, h,
                          tag=f'{tag} half plate {x0}')
            continue
        if ok and step and i % step == 0 and xa-xb > 7.0 and (tb-ta)*ln > 6.0:
            d = 1.8
            conformal(G[tone], patch(xa-gap/2-d, xb+gap/2+d, ta+dt+d/ln, tb-dt-d/ln), nu, nv, h+.8,
                      tag=f'{tag} doubler {xa}')


def coaming(G, patch, ln, xa, xb, ta, tb, rim, h, fam='coam', tag=''):
    """A raised frame round xa..xb x ta..tb: four strips `rim` wide standing h proud."""
    dt = rim/ln
    nu = max(2, int(round((xa-xb)/3.0)))
    nv = max(2, int(round((tb-ta)*ln/3.0)))
    conformal(G[fam], patch(xa, xb, ta, ta+dt), nu, 1, h, tag=f'{tag} rim')
    conformal(G[fam], patch(xa, xb, tb-dt, tb), nu, 1, h, tag=f'{tag} rim')
    conformal(G[fam], patch(xa, xa-rim, ta+dt, tb-dt), 1, nv, h, tag=f'{tag} rim')
    conformal(G[fam], patch(xb+rim, xb, ta+dt, tb-dt), 1, nv, h, tag=f'{tag} rim')


def radiator_bank(G, patch, ln, xa, xb, ta, tb, tag, pitch=1.4, rim=1.1, h=2.2):
    """A heat-sink bank: a heavy frame and big transverse fins, a header bar down its middle."""
    coaming(G, patch, ln, xa, xb, ta, tb, rim, h, tag=tag)
    dt = rim/ln
    nv = max(2, int(round((tb-ta)*ln/3.0)))
    n = int((xa-xb-2*rim)/pitch)
    span = (xa-xb-2*rim)/n
    for k in range(n):
        xc = xa-rim-span*(k+.5)
        conformal(G['fin'], patch(xc+.2, xc-.2, ta+dt, tb-dt), 1, nv, h-.35, tag=f'{tag} fin')
    tm = (ta+tb)/2
    for tt in (tm-.18*(tb-ta), tm+.18*(tb-ta)):
        conformal(G['coam'], patch(xa-rim, xb+rim, tt-.45/ln, tt+.45/ln), max(2, int((xa-xb)/3)), 1, h-.15,
                  tag=f'{tag} header')


def _hit(pt, o):
    h = _cast(pt+o*8.0, -o)
    if h is None or h[0] > 14.0:
        return None
    return h[1], h[2].normalized()


def tank(G, fn, s, x0, x1, t, r, standoff, tag):
    """A storage tank lying along X on the surface fn(x, t, s): a cylinder with stepped end
    caps and saddles under it."""
    a, b = _hit(*fn(x0, t, s)), _hit(*fn(x1, t, s))
    if a is None or b is None:
        REFUSED.append((tag, 'tank miss'))
        return
    pa, pb = a[0]+a[1]*standoff, b[0]+b[1]*standoff
    d = (pb-pa).normalized()
    G['tank'].cyl(pa+d*.6, pb-d*.6, r, 16)
    G['tank'].cyl(pa, pa+d*.6, r*.72, 16)
    G['tank'].cyl(pb-d*.6, pb, r*.72, 16)
    for k in (1, 3):
        G['band'].cyl(pa+(pb-pa)*(k/4)-d*.3, pa+(pb-pa)*(k/4)+d*.3, r+.12, 16)
    for k in (1, 3):
        x = x0+(x1-x0)*k/4
        h = _hit(*fn(x, t, s))
        if h:
            p, n = h
            side = n.cross(d).normalized()
            G['band'].box(p+n*(standoff-r*.4)/2, (d, side, n), (.35, r*.8, (standoff-r*.4)/2+.3))


def pump(G, f, tag):
    """A pump housing: a block with a motor drum on it and a valve chest, under 2.6 proud."""
    if f is None:
        return
    G['tank'].fbox(f, -1.9, 1.9, -1.5, 1.5, -.3, 1.4)
    G['band'].fbox(f, -2.2, 2.2, -1.8, 1.8, -.3, .35)
    G['band'].fcyl(f, -.6, 0, 1.05, 1.0, 2.5, 14)
    G['tank'].fbox(f, .8, 1.7, -.9, .9, 1.2, 2.3)


def bundle(G, pts_k, r, standoff, tag, bracket_every=7.0):
    """A bundle of conduits: pts_k is a list (one per pipe) of seated points [(p, n)]; pipes stand
    `standoff` off the surface, flanged at nodes, the bundle held by shared brackets."""
    axes = [[p+n*standoff for p, n in pts] for pts in pts_k]
    for ax in axes:
        for a, b in zip(ax, ax[1:]):
            G['bundle'].cyl(a, b, r, 10)
        for i in range(1, len(ax)-1):
            d = (ax[i+1]-ax[i-1]).normalized()
            G['bracket'].cyl(ax[i]-d*.4, ax[i]+d*.4, r*1.35, 10)
    first, last = pts_k[0], pts_k[-1]
    for i in range(len(first)-1):
        (pa, na), (pb, nb) = first[i], first[i+1]
        (qa, _), (qb, _) = last[i], last[i+1]
        seg = (pb-pa).length
        k = max(1, int(seg/bracket_every))
        for j in range(k):
            t = (j+.5)/k
            c0, c1 = pa+(pb-pa)*t, qa+(qb-qa)*t
            n = (na*(1-t)+nb*t).normalized()
            mid = (c0+c1)/2
            h = _cast(mid+n*6.0, -n)
            if h is None or h[0] > 9.0:
                continue
            p = h[1]
            d = (pb-pa).normalized()
            across = (c1-c0)
            half = across.length/2+r+.45
            v = (across-n*across.dot(n)).normalized()
            u = v.cross(n).normalized()
            hh = standoff+r+.3
            G['bracket'].box(p+n*(hh-.4)/2, (u, v, n), (.45, half, (hh+.4)/2))


def junction(G, f, hu, hv, h):
    if f is None:
        return
    G['coam'].fbox(f, -hu, hu, -hv, hv, -.3, h)
    G['band'].fbox(f, -hu+.5, hu-.5, -hv+.5, hv-.5, h-.2, h+.45, flat=True)


def big_layer(kit, G, s):
    side_p, bilge_p, dead_p = _patch(side_point, s), _patch(bilge_point, s), _patch(deadrise_point, s)

    # --- prow sides: two courses either side of a conduit trench, brick-bonded; the upper
    # course in the sprite's dirty orange edge band against dark brown, the lower in bone,
    # steel and brown; a heat-sink bank forward and aft in the lower course ---
    up = [205.0, 196.0, 184.0, 172.0, 160.0, 148.0, 136.0, 120.0, 106.0]
    lo = [205.0, 191.0, 179.0, 167.0, 153.0, 134.0, 121.0, 106.0]
    # the stem's flat flank (X 225..205), a broad plane at the bow every view sees: two
    # courses, no trench (the bundle ends in a junction box at the stem's knuckle)
    plate_course(G, side_p, _side_len, [224.0, 214.0, 205.4], .55, .97, ['orange', 'steel'], 'stem upper', step=0)
    plate_course(G, side_p, _side_len, [224.0, 217.0, 205.4], .03, .55, ['steel', 'bone'], 'stem lower', step=1)
    plate_course(G, side_p, _side_len, up, .72, .97, ['orange', 'dkbrown', 'orange', 'bone'], 'prow upper', step=0)
    plate_course(G, side_p, _side_len, lo, .03, .43, ['bone', 'steel', 'dkbrown', 'bone', 'steel', 'dkbrown', 'bone'],
                 'prow lower', skip={(191.0, 179.0), (121.0, 106.0)})
    for xa, xb in ((191.0, 179.0), (121.0, 106.0)):
        radiator_bank(G, side_p, _side_len((xa+xb)/2), xa-.5, xb+.5, .05, .41, f'prow bank {xa}')
    # the trench: a dark floor, a lip each side, three conduits running bow to root
    for xa, xb in zip(up, up[1:]):
        conformal(G['back'], side_p(xa, xb, .43, .72), max(2, int((xa-xb)/3)), 2, .4, tag=f'trench {xa}')
        conformal(G['coam'], side_p(xa, xb, .43, .43+.8/_side_len(xa)), max(2, int((xa-xb)/3)), 1, 1.6,
                  tag=f'trench lip {xa}')
    xs = [201.5, 196.0, 186.0, 172.0, 160.0, 148.0, 134.0, 120.0, 108.0]
    pts_k = []
    for k in range(3):
        pts = []
        for x in xs:
            ln = _side_len(x)
            hh = .575+(k-1)*1.85/ln
            h = _hit(*side_point(x, hh, s))
            if h:
                pts.append(h)
        pts_k.append(pts)
    if all(len(p) == len(xs) for p in pts_k):
        bundle(G, pts_k, .72, 1.05, 'prow bundle')
    for x in (108.5, 202.5):
        p, o = side_point(x, .575, s)
        junction(G, on(p, o, X, 2.4, 3.4, 1.5, f'prow junction {x}'), 2.4, 3.4, 2.4)

    # --- forefoot deadrise: two courses ---
    plate_course(G, dead_p, _dead_len, [203.0, 192.0, 180.0, 168.0], .06, .47, ['steel', 'dkbrown', 'bone'], 'forefoot a')
    plate_course(G, dead_p, _dead_len, [203.0, 197.0, 186.0, 174.0, 168.0], .53, .94, ['dkbrown', 'bone', 'steel', 'dkbrown'],
                 'forefoot b')

    # --- belly bilge planes: three courses, brick-bonded, with bays and banks ---
    A = [160.0, 140.0, 118.0, 96.0, 74.0, 52.0, 30.0, 8.0, -14.0, -36.0]
    # aft of X 19 the aft body's underside breaks through the plane's middle: the middle
    # course stops there and the pump bay takes the inner course
    B = [160.0, 150.0, 129.0, 107.0, 85.0, 63.0, 41.0, 19.0]
    C = A
    banks_c = {(140.0, 118.0), (74.0, 52.0), (30.0, 8.0)}
    banks_a = {(96.0, 74.0)}
    bays = {(129.0, 107.0): ('tanks', .36, .62), (63.0, 41.0): ('tanks', .36, .62), (8.0, -14.0): ('pumps', .05, .29)}
    plate_course(G, bilge_p, _bilge_len, A, .04, .3, ['dkbrown', 'base', 'dkbrown', 'steel', 'base', 'dkbrown', 'bone'], 'belly a',
                 skip=banks_a | {(8.0, -14.0)})
    plate_course(G, bilge_p, _bilge_len, B, .35, .63, ['base', 'dkbrown', 'base', 'bone', 'dkbrown', 'base', 'steel'], 'belly b', skip=set(bays))
    plate_course(G, bilge_p, _bilge_len, C, .65, .86, ['steel', 'dkbrown', 'steel', 'base', 'steel', 'dkbrown'], 'belly c', skip=banks_c)
    for xa, xb in banks_c:
        radiator_bank(G, bilge_p, _bilge_len((xa+xb)/2), xa-.5, xb+.5, .66, .85, f'belly bank {xa}')
    for xa, xb in banks_a:
        radiator_bank(G, bilge_p, _bilge_len((xa+xb)/2), xa-.5, xb+.5, .05, .32, f'belly bank {xa}')
    for (xa, xb), (kind, ta, tb) in bays.items():
        ln = _bilge_len((xa+xb)/2)
        coaming(G, bilge_p, ln, xa-.5, xb+.5, ta, tb, 1.5, 3.0, tag=f'bay {xa}')
        # a lower outer step round the coaming, so it reads as a heavy frame from any side
        coaming(G, bilge_p, ln, xa+.3, xb-.3, ta-1.2/ln, tb+1.2/ln, 1.2, 1.7, fam='band', tag=f'bay step {xa}')
        tm = (ta+tb)/2
        if kind == 'tanks':
            dt = 1.55/ln
            for tc in (tm-dt, tm+dt):
                tank(G, bilge_point, s, xa-2.6, xb+2.6, tc, 1.35, 1.6, f'bay tank {xa}')
        else:
            for xc in (xa-5.5, xb+5.5):
                p, o = bilge_point(xc, tm, s)
                pump(G, on(p, o, X, 2.2, 1.8, 2.0, f'bay pump {xc}'), 'pump')
            # a header pipe between the pumps
            pts_k = [[h for h in (_hit(*bilge_point(x, tc, s)) for x in (xa-3.0, (xa+xb)/2, xb+3.0)) if h]
                     for tc in (tm-.08, tm+.085)]
            if all(len(p) == 3 for p in pts_k):
                bundle(G, pts_k, .8, 1.2, 'bay header', bracket_every=4.0)
    # the bilge chine bundle: four conduits, junction boxes at both ends
    xs = [158.0, 150.0, 128.0, 104.0, 88.0, 74.0, 60.0, 40.0, 20.0, 0.0, -18.0, -28.0]
    pts_k = []
    for k in range(4):
        pts = []
        for x in xs:
            ln = _bilge_len(x)
            h = _hit(*bilge_point(x, 1.0-(1.5+1.75*k)/ln, s))
            if h:
                pts.append(h)
        pts_k.append(pts)
    n = min(len(p) for p in pts_k)
    if n >= 2:
        bundle(G, [p[:n] for p in pts_k], .72, 1.05, 'bilge bundle')
    for x in (159.0, xs[n-1]-1.5):
        ln = _bilge_len(x)
        p, o = bilge_point(x, 1.0-4.1/ln, s)
        junction(G, on(p, o, X, 2.4, 4.0, 2.5, f'bilge junction {x}'), 2.4, 4.0, 2.6)
    # the trench floor under the bundle
    for xa, xb in zip(A, A[1:]):
        for ta in (.985, .9):
            if conformal(G['back'], bilge_p(xa, xb, .86, ta), max(2, int((xa-xb)/3)), 2, .4, tag=f'chine floor {xa}'):
                break


def keel_plates(G):
    """Broad keel plates along the keel flat, alternating tone, low (0.35) so the keel line
    keeps its shape, with the gaps between them dark."""
    st = [160.0, 146.0, 128.0, 110.0, 92.0, 74.0, 56.0, 30.0, 10.0, -8.0, -26.0, -40.0]
    tones = ['steel', 'dkbrown']

    def P(xa, xb, ya, yb):
        def f(a, b):
            x = xa+(xb-xa)*a
            h = _cast(Vector((x, 0.0, -95.0)), Z)
            z = h[1].z if h else -45.0
            return Vector((x, ya+(yb-ya)*b, z)), Vector((0, 0, -1))
        return f
    for i, (xa, xb) in enumerate(zip(st, st[1:])):
        nu = max(2, int((xa-xb)/3))
        conformal(G[tones[i % 2]], P(xa-.5, xb+.5, -6.0, 6.0), nu, 4, .35, tag=f'keel plate {xa}')


def build(kit):
    _trees()
    REFUSED.clear()
    seam_mat = material('Hegemony seam strap', (.075, .05, .045), .4, .6)
    leaf_mat = material('Hegemony hatch plate', (.20, .13, .105), .35, .55)
    doubler_mat = material('Hegemony doubler plate', (.115, .073, .062), .35, .6)
    pipe_mat = material('Hull conduit steel', (.16, .15, .15), .8, .4)
    # pass 2 plate tones, kept in the Hegemony palette
    tone = {'dkbrown': material('Hegemony dark brown plate', (.062, .039, .034), .35, .62),
            'bone': material('Hegemony bone-brown plate', (.25, .175, .135), .3, .58),
            'steel': material('Hegemony blue-grey steel plate', (.050, .056, .066), .55, .5),
            'orange': material('Hegemony weathered orange plate', (.36, .10, .035), .3, .6),
            'base': kit.plate}
    seam_black = material('Hull seam black', (.012, .011, .012), .3, .8)
    tank_mat = material('Hull tank steel', (.22, .21, .20), .7, .38)
    made = []

    def finish(F):
        for fam in F.values():
            ob = fam.build(kit)
            if ob:
                made.append(ob)

    # --- the big layer first ---
    for side, s in (('port', 1), ('starboard', -1)):
        G = {k: Family(f'Detail hull {side} {k} plates', m, .3) for k, m in tone.items()}
        G['back'] = Family(f'Detail hull {side} seam backing', seam_black, 0)
        G['coam'] = Family(f'Detail hull {side} bay coamings', kit.grey, .2)
        G['fin'] = Family(f'Detail hull {side} radiator fins', kit.steel, 0)
        G['tank'] = Family(f'Detail hull {side} bay tanks', tank_mat, .1)
        G['band'] = Family(f'Detail hull {side} bay fittings', kit.steel, 0)
        G['bundle'] = Family(f'Detail hull {side} conduit bundle', tank_mat, 0)
        G['bracket'] = Family(f'Detail hull {side} bundle brackets', kit.steel, 0)
        big_layer(kit, G, s)
        finish(G)
    G = {'steel': Family('Detail hull keel steel plates', tone['steel'], .2),
         'dkbrown': Family('Detail hull keel brown plates', tone['dkbrown'], .2)}
    keel_plates(G)
    finish(G)
    big_refused = len(REFUSED)

    # --- the close-up layer, seated on the plates ---
    _trees(own=True)
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

        def bolt_rows(pt, stations, ta, tb, n, off=1.15):
            for x in stations[1:-1]:
                for k in range(n):
                    t = ta+(tb-ta)*(k+.5)/n
                    for dx in (off, -off):
                        p, o = pt(x+dx, t, s)
                        f = on(p, o, X, .18, .18, .5, 'bolt')
                        if f:
                            F['steel'].fbox(f, -.18, .18, -.18, .18, -.2, .38)

        # prow sides: bolts either side of each plate break, the airlock, a hatch, a grapple
        # point, vent ports in the upper course
        bolt_rows(side_point, [203.0, 196.0, 184.0, 172.0, 160.0, 148.0, 136.0, 120.0, 106.0], .75, .95, 3)
        bolt_rows(side_point, [203.0, 191.0, 179.0, 167.0, 153.0, 134.0, 121.0, 106.0], .06, .41, 5)
        p, o = side_point(160.0, .23, s)
        f = on(p, o, X, 1.7, 2.1, 1.2, 'airlock', up=True)
        if f:
            airlock(F, f)
        p, o = side_point(143.0, .23, s)
        item('hatch', on(p, o, X, 1.6, 1.3, 1.0, 'prow hatch', up=True), 1.6, 1.3)
        p, o = side_point(127.5, .23, s)
        item('hardpoint', on(p, o, X, 1.7, 1.7, 1.0, 'prow hardpoint'))
        for x in (166.0, 154.0, 130.0, 113.0):
            p, o = side_point(x, .85, s)
            item('port', on(p, o, X, .9, .9, 1.0, f'prow port {x}'), .5)

        # forefoot: a grapple point and vent ports on the plates
        p, o = deadrise_point(186.0, .27, s)
        item('hardpoint', on(p, o, X, 1.7, 1.7, 1.0, 'forefoot hardpoint'))
        for x in (191.5, 180.0):
            p, o = deadrise_point(x, .73, s)
            item('port', on(p, o, X, .9, .9, 1.0, f'forefoot port {x}'), .6)

        # belly: bolts on the plate breaks, hatches, ports and a grapple point on plate centres
        bolt_rows(bilge_point, [160.0, 140.0, 118.0, 96.0, 74.0, 52.0, 30.0, 8.0, -14.0, -36.0], .08, .3, 4)

        def put(kind, x, t, *a, n=1):
            hu, hv = {'port': (1.1, 1.1), 'hardpoint': (1.7, 1.7)}.get(kind, a[:2])
            for k in range(n):
                xk = x+(k-(n-1)/2)*(2*hu+.4)
                p, o = bilge_point(xk, t, s)
                item(kind, on(p, o, X, hu, hv, 1.0, f'bilge {kind} {xk}'), *a)
        put('port', 150.0, .19, .75, n=2)
        put('hatch', 125.0, .19, 2.2, 1.6)
        put('hatch', 107.0, .755, 1.6, 1.2)
        put('port', 85.0, .755, .75, n=2)
        put('hatch', 63.0, .19, 3.0, 2.2)
        put('hardpoint', 41.0, .19)
        put('hatch', 41.0, .755, 1.6, 1.2)
        put('port', 19.0, .19, .75, n=2)
        put('hatch', 30.0, .49, 1.8, 1.4)
        put('port', 96.0, .49, .75, n=2)
        put('hardpoint', -25.0, .17)

        # waist bellies: radiator banks under the valley
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
    f = keel_at(42.0, 3.6, 3.6, 'docking collar')
    if f:
        F['dark'].fcyl(f, 0, 0, 2.3, -.3, .5, 16)
        for k in range(8):
            F['pipe'].fbox(f.turned(2*math.pi*k/8), 2.3, 3.1, -1.05, 1.05, -.3, .95)
        for k in range(4):
            F['steel'].fbox(f.turned(2*math.pi*(k+.5)/4), 3.0, 3.6, -.45, .45, -.3, .7)
    for x in (119.0, 1.0):
        f = keel_at(x, 2.4, 1.8, f'keel hatch {x}')
        if f:
            hatch(F, f, 2.4, 1.8)
    for x in (138.0, 101.0, 65.0, 20.0, -17.0):
        f = keel_at(x, 1.1, 1.1, f'keel port {x}')
        if f:
            port(F, f, .7)
    finish(F)
    bpy.context.view_layer.update()
    faces = sum(len(ob.evaluated_get(bpy.context.evaluated_depsgraph_get()).data.polygons) for ob in made)
    print(f'DETAIL HULL: {len(made)} objects, {faces} faces; refused {len(REFUSED)} ({big_refused} big layer)')
    for tag, why in REFUSED:
        print('  refused', tag, why)
    return made
