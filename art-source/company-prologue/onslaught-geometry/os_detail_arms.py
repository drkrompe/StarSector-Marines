"""Surface detail on the two gun arms (os_arms), in two layers: a big layer of armour plates, machinery bays and radiator banks that reads at the 1.5-3 km formation range, and a close-up layer of hatches, bolts, louvres, trunking and fittings on top of it.

Owner, after round 11: "the next passes need to focus on that detail on the hull, exposed
machinery, piping, hatches, and armor"; after detail pass 1: "push the next detail pass bolder:
bigger, denser machinery and plating breakup so it reads at 1.5 km too." The silhouette is locked:
detail stands at most a few units proud, never changes the outline, keeps clear of the turret rings
(os_turrets.SLOTS, RING), the barrels' sweep and the running lights. Every object is 'Detail <area> ...'.

At 1.5 km the hull is about 0.8 px per unit, so the big layer works in plates of 8-25 units and in
value steps. It is laid on lattices of casts against the bare, posed arm (Grid), so every plate
follows the rolled deck, the rail's chine and the sponson's slope, and stops at the true edge of its
field. Read off onslaught_hegemony.png (model X = 244 - row, Y = 144 - col):

  deck        the brown gun deck in three courses of plates (dark brown, deck brown, light
              bone-brown with a raised doubler), staggered, with near-black seams between them.
  trench      the inboard deck carries a built-up machinery trench: coaming walls round a black
              floor, two big feed mains, straps over them and two pump housings.
  bays        the sprite's two dark deck channels between the rings are framed machinery bays with
              feed lines, cross frames and a valve housing, all below their rims.
  terrace     a three-module radiator bank where the sprite has dark louvre panels (X 100-115).
  rail        the orange rail cut into segments by dark bolted straps, alternate segments plated in
              weathered orange, a louvre bank on its top aft of the sponson's forward end.
  drop/flank  the deck's outboard drop to the rail and the arm's outboard flank in alternating
              dark and bone plates (the brown band seen abeam).
  sponson     plate courses of grey-blue steel against the dark grey, the six-slot vent row left
              clear, and a framed radiator bank of fins under it.
  close-up    hatches (seated on the plates), louvres in the vent slots, a three-main power trunk
              under the rail, the casemate's bolted surround, underside seams, the ventilator.
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

import os_kit
import os_turrets

SIDES = (('port', 1), ('starboard', -1))
EDGE_X = (180, 140, 100, 60, 20, -6)        # os_fittings.EDGE_X: the white edge-line lights (kept clear)
LIGHT_CLEAR = 3.5
KEEP = 1.3          # keep this many ring radii clear around every slot

_TREES = {}


# --- casting ---------------------------------------------------------------

def _build_trees(include=()):
    """BVH per mesh over the renderer's triangulation (a raw polygon tree can sit up to 0.5 off a
    twisted loft quad), with each triangle's material so a cast can tell a recess floor from deck.
    Detail objects are left out unless their name starts with one of `include`."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _TREES.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.startswith(('Light ', 'Temporary')):
            continue
        if ob.name.startswith('Detail ') and not (include and ob.name.startswith(tuple(include))):
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        pts = [ev.matrix_world @ v.co for v in me.vertices]
        if pts:
            mats = [m.name if m else None for m in me.materials]
            tm = [mats[t.material_index] if t.material_index < len(mats) else None for t in me.loop_triangles]
            lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
            hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
            _TREES[ob.name] = (BVHTree.FromPolygons(pts, [tuple(t.vertices) for t in me.loop_triangles]), lo, hi, tm)
        ev.to_mesh_clear()


def _arm(side):
    """The arm's own bodies: what detail may be seated on."""
    pre = (f'Hull {side} arm', f'Armour {side} arm', f'Armour {side} nose cheek', f'Armour {side} broadside fin',
           f'Detail {side} arm ')
    return lambda n: n.startswith(pre)


def _cast(origin, direction, only=None, far=400.0):
    """Nearest hit: (point, normal facing back along the ray, object name, material) or None."""
    o, d = Vector(origin), Vector(direction).normalized()
    e = o+d*far
    lo = Vector(map(min, o, e))
    hi = Vector(map(max, o, e))
    best = None
    for name, (t, blo, bhi, tm) in _TREES.items():
        if only is not None and not only(name):
            continue
        if blo.x > hi.x or bhi.x < lo.x or blo.y > hi.y or bhi.y < lo.y or blo.z > hi.z or bhi.z < lo.z:
            continue
        p, n, i, dist = t.ray_cast(o, d, far)
        if p is not None and (best is None or dist < best[0]):
            best = (dist, p, -n if n.dot(d) > 0 else n, name, tm[i] if i is not None and i < len(tm) else None)
    return None if best is None else best[1:]


def _drop(x, y, side, top=120.0):
    """The surface at (x, y) seen from above, if the topmost thing there is the arm itself."""
    h = _cast((x, y, top), (0, 0, -1))
    if h is None or not _arm(side)(h[2]):
        return None
    return h


def _keep_clear(x, y, s, extra=0.0):
    """True when (x, y) lies outside every slot's kept ring (and its turret well)."""
    for sid, slot, ang, arc, size, kind, wpn in os_turrets.SLOTS:
        r = os_turrets.RING[size]
        if math.hypot(x-slot[0], y-slot[1]) < KEEP*r+extra:
            return False
    return True


def _light_clear(x):
    return all(abs(x-e) >= LIGHT_CLEAR for e in EDGE_X)


# --- mesh families ---------------------------------------------------------

class Fam:
    """One mesh per family per side: boxes, ribbons and tubes appended to one vertex list."""

    def __init__(self):
        self.v, self.f = [], []

    def _quad_loop(self, rings, caps=True):
        """Join rings of equal size (each a closed loop of points) into a solid."""
        m = len(rings[0])
        o = len(self.v)
        for r in rings:
            self.v += [tuple(p) for p in r]
        n = len(rings)
        for j in range(n-1):
            a, b = o+j*m, o+(j+1)*m
            self.f += [(a+i, a+(i+1) % m, b+(i+1) % m, b+i) for i in range(m)]
        if caps:
            self.f.append(tuple(reversed(range(o, o+m))))
            self.f.append(tuple(range(o+(n-1)*m, o+n*m)))

    def box(self, c, ax, ay, az, hx, hy, hz):
        """An oriented box: centre c, unit axes, half sizes."""
        c = Vector(c)
        ring = lambda sx: [c+ax*sx+ay*sy+az*sz for sy, sz in ((-hy, -hz), (hy, -hz), (hy, hz), (-hy, hz))]
        self._quad_loop([ring(-hx), ring(hx)])

    def slab(self, c, n, t, hx, hy, h, sink=.25, ch=0.0):
        """A plate lying on a surface: centre c on the surface, normal n, long axis t (in plane),
        half sizes hx along t, hy across, standing h proud and sunk `sink`; `ch` chamfers its top."""
        n = Vector(n).normalized()
        t = (Vector(t)-n*Vector(t).dot(n)).normalized()
        b = n.cross(t).normalized()
        c = Vector(c)
        if ch <= 0:
            self.box(c+n*(h-sink)/2, t, b, n, hx, hy, (h+sink)/2)
            return

        def ring(z, d):
            return [c+t*x+b*y+n*z for x, y in ((-hx+d, -hy), (hx-d, -hy), (hx, -hy+d), (hx, hy-d), (hx-d, hy),
                                                (-hx+d, hy), (-hx, hy-d), (-hx, -hy+d))]
        self._quad_loop([ring(-sink, ch), ring(h-ch, ch), ring(h, 2*ch)])

    def disc(self, c, n, r, h, sink=.25, seg=16, r_top=None):
        n = Vector(n).normalized()
        t = n.cross(Vector((0, 0, 1)) if abs(n.z) < .9 else Vector((1, 0, 0))).normalized()
        b = n.cross(t)
        c = Vector(c)
        rt = r if r_top is None else r_top
        ring = lambda z, rr: [c+n*z+t*(rr*math.cos(2*math.pi*k/seg))+b*(rr*math.sin(2*math.pi*k/seg))
                              for k in range(seg)]
        self._quad_loop([ring(-sink, r), ring(h-.12, r), ring(h, rt)])

    def annulus(self, c, n, r0, r1, h, sink=.25, seg=20):
        """A raised ring (a hatch coaming)."""
        n = Vector(n).normalized()
        t = n.cross(Vector((0, 0, 1)) if abs(n.z) < .9 else Vector((1, 0, 0))).normalized()
        b = n.cross(t)
        c = Vector(c)
        o = len(self.v)
        for k in range(seg):
            a = 2*math.pi*k/seg
            u = t*math.cos(a)+b*math.sin(a)
            self.v += [tuple(c+u*r0-n*sink), tuple(c+u*r1-n*sink), tuple(c+u*r1+n*h), tuple(c+u*r0+n*h)]
        for k in range(seg):
            a, b2 = o+4*k, o+4*((k+1) % seg)
            for i in range(4):
                self.f.append((a+i, a+(i+1) % 4, b2+(i+1) % 4, b2+i))

    def ribbon(self, pts, nrms, w, h, sink=.25):
        """A strip lying along a surface path: width w, h proud, sunk `sink`."""
        if len(pts) < 2:
            return
        rings = []
        for i, (p, n) in enumerate(zip(pts, nrms)):
            a, b = pts[max(0, i-1)], pts[min(len(pts)-1, i+1)]
            t = (Vector(b)-Vector(a)).normalized()
            n = Vector(n).normalized()
            s = n.cross(t).normalized()
            p = Vector(p)
            rings.append([p-s*w/2-n*sink, p+s*w/2-n*sink, p+s*w/2+n*h, p-s*w/2+n*h])
        self._quad_loop(rings)

    def tube(self, pts, r, ref=None, seg=8, caps=True):
        """A pipe through centre points; rings square to the run, oriented by `ref` (an up vector)."""
        if len(pts) < 2:
            return
        pts = [Vector(p) for p in pts]
        rings = []
        for i, p in enumerate(pts):
            if 0 < i < len(pts)-1:
                t = ((p-pts[i-1]).normalized()+(pts[i+1]-p).normalized()).normalized()
            else:
                t = (pts[min(i+1, len(pts)-1)]-pts[max(i-1, 0)]).normalized()
            up = Vector(ref) if ref is not None else Vector((0, 0, 1))
            if abs(up.dot(t)) > .95:
                up = Vector((1, 0, 0))
            a = t.cross(up).normalized()
            b = a.cross(t).normalized()
            rings.append([p+a*(r*math.cos(2*math.pi*(k+.5)/seg))+b*(r*math.sin(2*math.pi*(k+.5)/seg))
                          for k in range(seg)])
        self._quad_loop(rings, caps=caps)

    def finish(self, kit, name, mat, bevel=0, smooth=0.0):
        """`smooth` (degrees): shade smooth with edges sharper than this kept hard, so a plate
        draped over a twisted loft reads as one plate, not a field of facets."""
        if not self.f:
            return None
        ob = kit.mesh(name, self.v, self.f, mat, bevel)
        if smooth:
            import bmesh
            bm = bmesh.new()
            bm.from_mesh(ob.data)
            lim = math.radians(smooth)
            for f in bm.faces:
                f.smooth = True
            for e in bm.edges:
                e.smooth = e.is_manifold and e.calc_face_angle(math.pi) < lim
            bm.to_mesh(ob.data)
            bm.free()
        return ob


# --- surface paths ---------------------------------------------------------

def _drape(side, a, b, step=.6, ok=None):
    """Points and normals of the arm's top surface under the plan segment a-b, split where it
    leaves the arm (or `ok(hit)` refuses a sample): a list of runs [(points, normals)]."""
    a, b = Vector((a[0], a[1], 0)), Vector((b[0], b[1], 0))
    L = (b-a).length
    n = max(1, int(L/step))
    runs, cur = [], ([], [])
    for i in range(n+1):
        q = a+(b-a)*(i/n)
        h = _drop(q.x, q.y, side)
        if h is not None and (ok is None or ok(h)):
            cur[0].append(h[0])
            cur[1].append(h[1])
        else:
            if len(cur[0]) > 1:
                runs.append(cur)
            cur = ([], [])
    if len(cur[0]) > 1:
        runs.append(cur)
    return runs


def _axis(s):
    """The arm's long axis in plan (bow-ward), and the outboard direction square to it."""
    t = Vector((222.0, -36.0*s, 0)).normalized()
    o = Vector((-t.y, t.x, 0))*s
    return t, o


def _gun_line(x):
    """|y| of the turret line at x (through the arm slots' plan positions)."""
    pts = [(77.0, 88.0), (114.0, 78.0), (134.0, 74.0), (158.0, 71.0), (196.0, 70.5)]
    if x <= pts[0][0]:
        return pts[0][1]
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        if ax <= x <= bx:
            return ay+(by-ay)*(x-ax)/(bx-ax)
    return pts[-1][1]


def _ring(x, y):
    for sid, slot, ang, arc, size, kind, wpn in os_turrets.SLOTS:
        if (slot[0], abs(slot[1])) == (x, y):
            return os_turrets.RING[size]
    return 5.0


# --- the families ----------------------------------------------------------

def under_seams(kit, side, s, seams):
    """Plate seams on the arm's bare underside (the lower glacis and the inboard bottom), seen from
    below and on the low quarters: frames across it and one strake line along it."""
    hull = f'Hull {side} arm'

    def up(x, y):
        h = _cast((x, s*y, -150.0), (0, 0, 1))
        return h if h and h[2] == hull and h[1].z < -.3 else None
    for x in (36.0, 58.0, 80.0, 102.0, 124.0, 146.0, 168.0):
        pts, nrms = [], []
        for k in range(160):
            h = up(x, 55.0+k*.5)
            if h:
                pts.append(h[0])
                nrms.append(h[1])
            elif len(pts) > 2:
                seams.ribbon(pts, nrms, .32, .12, .3)
                pts, nrms = [], []
        if len(pts) > 2:
            seams.ribbon(pts, nrms, .32, .12, .3)


def _hatch(lid, rim, fit, h, t, L, W):
    """A rectangular hatch at hit h: a raised coaming, a lid, two hinges and a handle."""
    p, n = Vector(h[0]), Vector(h[1])
    rim.slab(p, n, t, L/2+.45, W/2+.45, .25, .3, ch=.12)
    lid.slab(p, n, t, L/2-.05, W/2-.05, .42, .3, ch=.12)
    tt = (t-n*t.dot(n)).normalized()
    b = n.cross(tt).normalized()
    for k in (-1, 1):
        fit.slab(p-tt*(L/2-.1)+b*(k*W*.28), n, tt, .3, .28, .55, .2)
    fit.slab(p+tt*(L/2-.55), n, b, .45, .14, .62, .2)


def _round_hatch(lid, rim, fit, h, r, bolts=10):
    p, n = Vector(h[0]), Vector(h[1])
    rim.annulus(p, n, r-.05, r+.45, .32, .35)
    lid.disc(p, n, r, .4, .35, seg=20, r_top=r-.25)
    fit.disc(p+n*.4, n, .45, .25, .1, seg=10)
    tt = n.cross(Vector((0, 0, 1)) if abs(n.z) < .9 else Vector((1, 0, 0))).normalized()
    bb = n.cross(tt)
    for k in range(bolts):
        a = 2*math.pi*(k+.5)/bolts
        q = p+(tt*math.cos(a)+bb*math.sin(a))*(r+.2)
        fit.slab(q, n, tt*math.cos(a)+bb*math.sin(a), .16, .16, .48, .2)


def hatches(kit, side, s, lid, rim, fit, rails):
    """Deck hatches: rectangular personnel hatches on the inboard deck behind every mount's arc,
    and the sprite's round hatches."""
    t, o = _axis(s)
    for x, off in ((99.0, -13.5), (124.0, -12.5), (146.0, -10.5), (176.0, -6.0)):
        c = Vector((x, s*_gun_line(x), 0))+o*off
        A = Vector((DECK_A[0], s*DECK_A[1], 0.0))
        if _in((c-A).dot(t), (c-A).dot(o), [(a-3, b+3, cc-3, d+3) for a, b, cc, d in (TRENCH,)+BAYS+(TERRACE_BANK,)]):
            continue
        h = _drop(c.x, c.y, side)
        if h is None or not _keep_clear(c.x, c.y, s, 2.0) or h[1].z < .85:
            continue
        if not all(_drop(*(c+t*dx+o*dy).xy, side) for dx in (-1.8, 1.8) for dy in (-1.5, 1.5)):
            continue
        _hatch(lid, rim, fit, h, t, 3.0, 2.3)
        # a grab rail on the hatch's inboard side (crew climb out here)
        n = Vector(h[1])
        p = Vector(h[0])-o*2.0
        ends = [_drop(*(p+t*dx).xy, side) for dx in (-1.0, 1.0)]
        if all(ends):
            for e in ends:
                rails.box(Vector(e[0])+Vector(e[1])*.45, t, o, Vector(e[1]), .14, .14, .6)
            a, b = Vector(ends[0][0])+Vector(ends[0][1])*1.0, Vector(ends[1][0])+Vector(ends[1][1])*1.0
            rails.tube([a, b], .14, ref=n, seg=6)
    for x, y, r in ((143.5, 81.3, 1.9), (42.5, 99.0, 3.0), (5.0, 92.6, 2.0)):
        h = _drop(x, s*y, side)
        if h is None or not _keep_clear(x, s*y, s, r+1.0):
            continue
        _round_hatch(lid, rim, fit, h, r, bolts=12 if r > 2.5 else 8)
        if r > 2.5:          # the trunk hatch is a hoisted plate: two lifting eyes on its rim
            for dx in (-1, 1):
                e = _drop(x+dx*(r+1.3), s*y, side)
                if e:
                    _eye(fit, Vector(e[0]), e[1], Vector((0, 1, 0)))
    for x in (12.5, 15.5, 18.5):
        h = _drop(x, s*89.0, side)
        if h:
            rim.slab(h[0], h[1], Vector((0, 1, 0)), .9, .55, .3, .3)


def _profile(x, s, z0=12.0, z1=-52.0, dz=.25):
    """The arm's outboard profile at station x: horizontal casts inboard at falling heights,
    [(z, point, normal, name)] for every sample that hits something."""
    out = []
    z = z0
    while z >= z1:
        h = _cast((x, s*220.0, z), (0, -s, 0))
        if h is not None:
            out.append((z, Vector(h[0]), Vector(h[1]), h[2]))
        z -= dz
    return out


def sponson(kit, side, s, louvres, frame, seams, bolts, fit):
    """The dark sponson: louvres in its six vent slots, a frame round the vent row, plate seams
    down its face, a bolt row under the rail's edge, a domed ventilator and lifting eyes."""
    name = f'Armour {side} arm side sponson'
    on = lambda n: n == name
    slots = (84.0, 91.0, 98.0, 105.0, 112.0, 119.0)
    rows = []
    for xc in slots:
        inside = _profile(xc, s, 0.0, -45.0, .2)
        outside = {round(z, 2): (p, n, nm) for z, p, n, nm in _profile(xc+2.2, s, 0.0, -45.0, .2)}
        zs = [z for z, p, n, nm in inside if nm == name and round(z, 2) in outside
              and outside[round(z, 2)][2] == name and (outside[round(z, 2)][0]-p).dot(outside[round(z, 2)][1]) > .5]
        if len(zs) < 4:
            continue
        za, zb = min(zs), max(zs)
        rows.append((xc, za, zb))
        for k in range(4):
            z = za+(zb-za)*(k+.5)/4
            p, n, nm = outside[min(outside, key=lambda q: abs(q-z))]
            c = Vector((xc, p.y, p.z))-n*.45
            b = n.cross(Vector((1, 0, 0))).normalized()
            louvres.box(c, Vector((1, 0, 0)), b, n, 1.25, .13, .32)
    if rows:
        za = min(r[1] for r in rows)-.7
        zb = max(r[2] for r in rows)+.7
        xa, xb = rows[0][0]-2.0, rows[-1][0]+2.0
        edges = [[(x, za) for x in _frange(xa, xb, .6)], [(x, zb) for x in _frange(xa, xb, .6)],
                 [(xa, z) for z in _frange(za, zb, .4)], [(xb, z) for z in _frange(za, zb, .4)]]
        for e in edges:
            pts, nrms = [], []
            for x, z in e:
                h = _cast((x, s*220.0, z), (0, -s, 0), only=on)
                if h:
                    pts.append(h[0])
                    nrms.append(h[1])
            frame.ribbon(pts, nrms, .5, .28, .3)


def ventilator(kit, side, s, fit):
    """The domed ventilator on the sponson's face between the forward courses (the sprite's round
    fitting), seated on whatever plate is there."""
    name = f'Armour {side} arm side sponson'
    for x in (134.0,):
        prof = [(z, p, n) for z, p, n, nm in _profile(x, s, 0.0, -45.0, .25)
                if nm == name or nm.startswith(f'Detail {side} arm sponson plates')]
        if len(prof) > 8:
            z, p, n = prof[len(prof)//3]
            fit.disc(p, n, 1.5, .45, .3, seg=16)
            fit.disc(p+n*.45, n, 1.15, .5, .1, seg=16, r_top=.6)


def _eye(fam, p, n, t):
    """A lifting eye: a base pad and a hoop standing on it."""
    n = Vector(n).normalized()
    t = (t-n*t.dot(n)).normalized()
    b = n.cross(t)
    fam.slab(p, n, t, .45, .3, .12, .2)
    for k in (-1, 1):
        fam.box(p+t*(k*.3)+n*.4, t, b, n, .1, .12, .35)
    fam.box(p+n*.8, t, b, n, .4, .12, .1)


def _frange(a, b, step):
    n = max(1, int(round((b-a)/step)))
    return [a+(b-a)*i/n for i in range(n+1)]


def flank_trunk(kit, side, s, pipes, clamps, boxes):
    """A power trunk tucked under the orange rail's lower edge along the arm's outboard flank:
    from a junction box at the after collar forward, over the sponson's top, to a gland in the nose
    plate (belt 1) that carries it on to the TPC casemate."""
    rail = f'Armour {side} arm belt rail'
    arm = _arm(side)
    run1, run2, run3, nrm = [], [], [], []
    for x in _frange(26.0, 163.0, 1.0):
        prof = _profile(x, s, 5.0, -50.0, .2)
        edge = None
        for i in range(len(prof)-1):
            if prof[i][3] == rail and prof[i+1][3] != rail and arm(prof[i+1][3]):
                edge = i
                break
        if edge is None:
            continue
        z_r = prof[edge][0]
        below = {round(z, 1): (p, n, nm) for z, p, n, nm in prof[edge+1:]}

        def at(z):
            k = min(below, key=lambda q: abs(q-z))
            return below[k]
        p1, n1, _ = at(z_r-1.0)
        p2, n2, _ = at(z_r-2.8)
        p3, n3, _ = at(z_r-4.4)
        run1.append(p1+n1*.82)
        run2.append(p2+n2*.67)
        run3.append(p3+n3*.57)
        nrm.append(n1)
    if len(run1) < 4:
        return
    # a station whose edge was misread (a seam, a plate's corner) leaves the run: drop any station
    # off the running median by more than a pipe's width
    def med(v):
        v = sorted(v)
        return v[len(v)//2]
    good = []
    for i in range(len(run1)):
        w = run1[max(0, i-4):i+5]
        m = Vector((med([p.x for p in w]), med([p.y for p in w]), med([p.z for p in w])))
        good.append((run1[i]-m).length < 1.5)
    run1 = [p for p, g in zip(run1, good) if g]
    run2 = [p for p, g in zip(run2, good) if g]
    run3 = [p for p, g in zip(run3, good) if g]
    nrm = [n for n, g in zip(nrm, good) if g]
    for run, r in ((run1, .8), (run2, .65), (run3, .55)):
        sm = []
        for i in range(len(run)):
            w = run[max(0, i-2):i+3]
            sm.append(sum(w, Vector())/len(w))
        seg = [sm[0]]
        for a, b in zip(sm, sm[1:]):
            if (b-a).length > 2.5:
                if len(seg) > 3:
                    pipes.tube(seg, r, ref=Vector((0, 0, 1)))
                seg = []
            seg.append(b)
        if len(seg) > 3:
            pipes.tube(seg, r, ref=Vector((0, 0, 1)))
    for i in range(2, len(run1)-2, 6):
        n = nrm[i]
        t = (run1[i+1]-run1[i-1]).normalized()
        b = n.cross(t).normalized()
        c = (run1[i]+run3[i])/2
        clamps.box(c-n*.15, t, b, n, .28, ((run1[i]-run3[i]).length+2.0)/2, .75)
    for p, n in ((run1[0], nrm[0]), (run1[-1], nrm[-1])):
        boxes.box(p-n*.2+Vector((0, 0, -.8)), Vector((1, 0, 0)), n.cross(Vector((1, 0, 0))).normalized(), n,
                  1.2, 1.7, .9)


def _tpc_frame(s):
    """The TPC casemate's frame as os_turrets._hardpoint builds it: (origin, across, up)."""
    import os_arms
    up = os_arms.pose(s).to_3x3() @ Vector((0, 0, 1))
    up.x = 0.0
    up.normalize()
    side = up.cross(Vector((1, 0, 0))).normalized()
    return Vector((0.0, s*70.0, os_arms.TPC_AXIS_Z)), side, up


def nose_surround(kit, side, s, frame_f, bolts):
    """A bolted frame on the arm's raked nose face round the TPC casemate, where the casemate leaves
    the hull: traced by casting aft along the line of fire round the casemate's section, kept only
    where the cast lands on the nose face itself (the cheeks stand ahead of it at the sides)."""
    import os_arms
    p0, sd, up = _tpc_frame(s)
    hw, roof, chin = os_arms.TPC_HALF_W, 8.0, -9.0
    face = (f'Hull {side} arm', f'Hull {side} arm gallery')
    only = _arm(side)
    for grow, w_rib in ((1.0, 1.4),):
        a, b, c = hw+grow, roof+grow, chin-grow
        k = 1.8
        loop = [(-a+k, c), (a-k, c), (a, c+k), (a, b-k), (a-k, b), (-a+k, b), (-a, b-k), (-a, c+k), (-a+k, c)]
        samples = []
        for (w0, h0), (w1, h1) in zip(loop, loop[1:]):
            n = max(1, int(math.hypot(w1-w0, h1-h0)/.5))
            samples += [(w0+(w1-w0)*i/n, h0+(h1-h0)*i/n) for i in range(n)]
        runs, cur = [], ([], [])
        for w, h in samples:
            o = p0+Vector((240.0, 0, 0))+sd*w+up*h
            hit = _cast(o, (-1, 0, 0), only=only)
            if hit and hit[2] in face and hit[0].x > 185.0 and h > 0.0:
                cur[0].append(hit[0])
                cur[1].append(hit[1])
            else:
                if len(cur[0]) > 2:
                    runs.append(cur)
                cur = ([], [])
        if len(cur[0]) > 2:
            runs.append(cur)
        for pts, nrms in runs:
            frame_f.ribbon(pts, nrms, w_rib, .5, .9)
            for p, n in list(zip(pts, nrms))[1:-1:3]:
                bolts.slab(Vector(p)+Vector(n)*.5, n, Vector((0, 1, 0)), .2, .2, .2, .05)
    # the cheeks' inner edges, where they bear on the casemate's sides: a bolt row down each brow
    cheek = (f'Armour {side} nose cheek 1', f'Armour {side} nose cheek 2')
    for w in (-(hw+.9), hw+.9):
        for t in _frange(186.0, 207.0, 1.5):
            o = p0+Vector((t, 0, 0))+sd*w+up*40.0
            hit = _cast(o, -up, only=only)
            if hit and hit[2] in cheek:
                bolts.slab(hit[0], hit[1], Vector((1, 0, 0)), .22, .22, .25, .2)


# --- the big layer (detail pass 2): plates and bays that read at 1.5 km --------------------------
#
# At 1.5 km the hull is about 0.8 px per unit, so detail reads there only at the scale of plates and
# bays and through value steps. Every arm field is broken into armour panels of 8-25 units that
# alternate in tone and stand proud with a hard step, with wide near-black seams between them; the
# deck channels become framed machinery trenches; the rail is cut into segments by dark straps; the
# sponson carries plate courses and a radiator bank. All of it is laid on a lattice of casts against
# the bare arm (a Grid), so panels follow the rolled deck and stop at the true edge of every field.

LIGHTS = ((213.1, 77.1), (180.0, 77.8), (140.1, 84.9), (100.0, 104.4), (60.0, 103.5), (20.0, 110.9),
          (-6.0, 112.0))       # where the running lights land (port; they build after this module)
LIGHTS3 = ((213.1, 77.1, -25.8), (180.0, 77.8, -9.4), (140.1, 84.9, -5.5), (100.0, 104.4, -7.2),
           (60.0, 103.5, 2.9), (20.0, 110.9, 9.2), (-6.0, 112.0, 10.6))
RECESS = 'Recess and machinery'
DECK_A = (77.0, 88.0)          # the deck frame's origin: the Devastator's slot (u along the arm, w outboard)


class Grid:
    """Casts along D over a (u, w) lattice in the plane through O spanned by U and W. A sample
    counts when valid(u, w, hit) passes; a failed sample beside a good one is pulled back to just
    inside the field's edge (found by bisection, less `margin`), so panel outlines follow the true
    boundary of a field instead of stepping round it cell by cell."""

    def __init__(self, O, U, W, D, ur, wr, step, valid, only=None, margin=.5, reach=60.0):
        self.O, self.U, self.W, self.D = Vector(O), Vector(U), Vector(W), Vector(D).normalized()
        self.u0, self.w0, self.step = ur[0], wr[0], step
        self.nu, self.nw = int(round((ur[1]-ur[0])/step)), int(round((wr[1]-wr[0])/step))
        self.valid, self.only, self.reach = valid, only, reach
        raw = {}
        for i in range(self.nu+1):
            for j in range(self.nw+1):
                h = self.sample(*self.uw(i, j))
                if h:
                    raw[i, j] = h
        self.P = dict(raw)
        for i in range(self.nu+1):
            for j in range(self.nw+1):
                if (i, j) in raw:
                    continue
                best = None
                for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if (i+a, j+b) not in raw:
                        continue
                    ua, wa = self.uw(i+a, j+b)
                    ub, wb = self.uw(i, j)
                    lo, hi = 0.0, 1.0
                    for _ in range(5):
                        m = (lo+hi)/2
                        if self.sample(ua+(ub-ua)*m, wa+(wb-wa)*m):
                            lo = m
                        else:
                            hi = m
                    f = lo*step-margin
                    if f < .12*step:            # the edge lies close to the good sample: hug it
                        f = lo*step*.6
                    if f > .04*step and (best is None or f > best[0]):
                        best = (f, ua, wa, ub, wb)
                if best:
                    f, ua, wa, ub, wb = best
                    m = f/step
                    h = self.sample(ua+(ub-ua)*m, wa+(wb-wa)*m)
                    if h:
                        self.P[i, j] = h
        self._n = {}

    def uw(self, i, j):
        return self.u0+i*self.step, self.w0+j*self.step

    def ij(self, u, w):
        return int(round((u-self.u0)/self.step)), int(round((w-self.w0)/self.step))

    def at(self, u, w):
        return self.O+self.U*u+self.W*w

    def sample(self, u, w):
        q = self.at(u, w)
        h = _cast(q-self.D*self.reach, self.D, only=self.only, far=2*self.reach)
        return h if h is not None and self.valid(u, w, h) else None

    def normal(self, k):
        """The hit normal smoothed over the four neighbours (a panel rides over a crease)."""
        if k not in self._n:
            i, j = k
            ns = [self.P[q][1] for q in ((i, j), (i+1, j), (i-1, j), (i, j+1), (i, j-1)) if q in self.P]
            self._n[k] = sum(ns, Vector()).normalized()
        return self._n[k]

    def cell_ok(self, i, j):
        ks = ((i, j), (i+1, j), (i+1, j+1), (i, j+1))
        if not all(k in self.P for k in ks):
            return False
        a, b, c, d = (self.P[k][0] for k in ks)
        if max((b-a).length, (c-b).length, (d-c).length, (a-d).length) > 1.9*self.step:
            return False
        return ((c-a)-((b-a)+(d-a))).length < 1.5     # a step or a slot inside the cell refuses it


def _clip(poly, n, c):
    """Clip a convex (u, w) polygon to the half-plane n.x <= c."""
    out = []
    for k in range(len(poly)):
        p, q = poly[k], poly[(k+1) % len(poly)]
        dp, dq = n[0]*p[0]+n[1]*p[1]-c, n[0]*q[0]+n[1]*q[1]-c
        if dp <= 0:
            out.append(p)
        if dp*dq < 0:
            f = dp/(dp-dq)
            out.append((p[0]+(q[0]-p[0])*f, p[1]+(q[1]-p[1])*f))
    return out


def _area(poly):
    return abs(sum(poly[k][0]*poly[(k+1) % len(poly)][1]-poly[(k+1) % len(poly)][0]*poly[k][1]
                   for k in range(len(poly))))/2 if len(poly) > 2 else 0.0


STATS = {}
CUT_DIRS = [(math.cos(math.radians(a)), math.sin(math.radians(a))) for a in range(0, 360, 5)]


def plate_outline(g, u0, u1, w0, w1, keep=None, margin=.6, res=.5, min_keep=.35, min_area=10.0):
    """A clean outline for a plate on the rectangle [u0, u1] x [w0, w1]: every patch of the
    rectangle that is off the field (another body, a turret well, a field edge, a refused region)
    is cut away with ONE straight line fitted to it, set `margin` clear, never along the cast
    lattice. Returns the convex (u, w) polygon and its half-planes, or None when a clean cut
    would leave too little of the plate (the plate is dropped)."""
    nu, nw = max(1, int(round((u1-u0)/res))), max(1, int(round((w1-w0)/res)))
    good, bad = [], {}
    for a in range(nu+1):
        for b in range(nw+1):
            u, w = u0+(u1-u0)*a/nu, w0+(w1-w0)*b/nw
            h = g.sample(u, w)
            if h is not None and (keep is None or keep(u, w, h)):
                good.append((u, w))
            else:
                bad[a, b] = (u, w)
    if len(good) < 4:
        return None
    planes = [((-1.0, 0.0), -u0), ((1.0, 0.0), u1), ((0.0, -1.0), -w0), ((0.0, 1.0), w1)]
    # the refused patches, each connected cluster cut away by its own line
    seen, clusters = set(), []
    for k in bad:
        if k in seen:
            continue
        stack, cl = [k], []
        seen.add(k)
        while stack:
            a, b = stack.pop()
            cl.append(bad[a, b])
            for da in (-1, 0, 1):
                for db in (-1, 0, 1):
                    q = (a+da, b+db)
                    if q in bad and q not in seen:
                        seen.add(q)
                        stack.append(q)
        clusters.append(cl)
    clusters.sort(key=len, reverse=True)
    kept = good
    for cl in clusters:
        live = [(n, c) for n, c in planes]
        if all(any(n[0]*x+n[1]*y > c+1e-6 for n, c in live) for x, y in cl):
            continue                       # already cut away by an earlier line
        best = None
        for n in CUT_DIRS:
            c = min(n[0]*x+n[1]*y for x, y in cl)-margin
            cnt = sum(1 for x, y in kept if n[0]*x+n[1]*y <= c)
            if best is None or cnt > best[0]:
                best = (cnt, n, c)
        cnt, n, c = best
        planes.append((n, c))
        kept = [(x, y) for x, y in kept if n[0]*x+n[1]*y <= c]
    if len(kept) < min_keep*len(good):
        return None
    poly = [(u0, w0), (u1, w0), (u1, w1), (u0, w1)]
    for n, c in planes[4:]:
        poly = _clip(poly, n, c)
    if _area(poly) < min_area:
        return None
    per = sum(math.hypot(poly[k][0]-poly[k-1][0], poly[k][1]-poly[k-1][1]) for k in range(len(poly)))
    if min(u1-u0, w1-w0) >= 4.0 and 2*_area(poly)/per < 1.2:
        return None                        # a sliver left between two cuts is not a plate
    return poly, planes


def _inside(poly, u, w, pad=0.0):
    """True when (u, w) lies inside the convex outline, at least `pad` from its edges."""
    if not poly:
        return False
    sgn = 0
    for k in range(len(poly)):
        (x0, y0), (x1, y1) = poly[k], poly[(k+1) % len(poly)]
        L = math.hypot(x1-x0, y1-y0)
        if L < 1e-9:
            continue
        cr = ((x1-x0)*(w-y0)-(y1-y0)*(u-x0))/L
        if sgn == 0:
            sgn = 1 if _area_signed(poly) > 0 else -1
        if cr*sgn < pad:
            return False
    return True


def _area_signed(poly):
    return sum(poly[k][0]*poly[(k+1) % len(poly)][1]-poly[(k+1) % len(poly)][0]*poly[k][1]
               for k in range(len(poly)))/2


def panel(fam, g, i0, i1, j0, j1, h, keep=None, sink=.3, min_area=None, margin=.6):
    """Plates over the lattice rectangle [i0..i1] x [j0..j1], each with a clean outline
    (plate_outline). Where one clean cut would waste much of the rectangle (a well in its middle,
    a curving field edge) the rectangle is halved across its long side with a seam between the
    halves, and each half is tried again; what still cannot be cut cleanly is dropped. A plate is
    a top sheet h proud draped over the surface inside its outline, with a skirt down `sink` under
    the surface along it. `keep(u, w, hit)` refuses ground. Returns the largest outline built."""
    u0, w0 = g.uw(i0, j0)
    u1, w1 = g.uw(i1, j1)
    polys = []

    def attempt(u0, u1, w0, w1, depth):
        if u1-u0 < .9*g.step or w1-w0 < .9*g.step:
            return
        ma = min(10.0, .4*(u1-u0)*(w1-w0)) if min_area is None else min_area
        last = depth >= 3 or max(u1-u0, w1-w0) < 8.0
        out = plate_outline(g, u0, u1, w0, w1, keep, margin=margin, min_area=ma, min_keep=.35 if last else .7)
        st_ = STATS.setdefault(id(fam), [0, 0, 0.0, 0.0])
        if out is not None:
            st_[1] += 1
            st_[3] += _area(out[0])
            _drape_plate(fam, g, out, h, sink)
            polys.append(out[0])
            return
        if last:
            st_[0] += 1
            return
        gap = .5*g.step
        if u1-u0 >= w1-w0:
            m = (u0+u1)/2
            attempt(u0, m-gap, w0, w1, depth+1)
            attempt(m+gap, u1, w0, w1, depth+1)
        else:
            m = (w0+w1)/2
            attempt(u0, u1, w0, m-gap, depth+1)
            attempt(u0, u1, m+gap, w1, depth+1)
    attempt(u0, u1, w0, w1, 0)
    return max(polys, key=_area) if polys else None


def _drape_plate(fam, g, out, h, sink):
    poly, planes = out
    idx, faces = {}, []

    def vid(u, w):
        key = (round(u, 4), round(w, 4))
        if key not in idx:
            q = g.at(u, w)
            hit = _cast(q-g.D*g.reach, g.D, only=g.only, far=2*g.reach)
            if hit is None:
                idx[key] = None
            else:
                p, n = Vector(hit[0]), Vector(hit[1])
                if n.dot(-g.D) < 0:
                    n = -n
                idx[key] = len(fam.v)
                fam.v += [tuple(p+n*h), tuple(p-n*sink)]
        return idx[key]
    st = g.step
    ua, ub = min(x for x, y in poly), max(x for x, y in poly)
    wa, wb = min(y for x, y in poly), max(y for x, y in poly)
    k0, k1 = int(math.floor((ua-g.u0)/st)), int(math.ceil((ub-g.u0)/st))
    l0, l1 = int(math.floor((wa-g.w0)/st)), int(math.ceil((wb-g.w0)/st))
    for k in range(k0, k1):
        for l in range(l0, l1):
            cu, cw = g.u0+k*st, g.w0+l*st
            cell = [(cu, cw), (cu+st, cw), (cu+st, cw+st), (cu, cw+st)]
            for n, c in planes:
                cell = _clip(cell, n, c)
                if len(cell) < 3:
                    break
            if len(cell) < 3 or _area(cell) < 1e-4:
                continue
            ids = [vid(*v) for v in cell]
            if None in ids:
                continue
            dedup = [x for m, x in enumerate(ids) if x != ids[m-1]]
            if len(dedup) >= 3:
                faces.append(dedup)
    if not faces:
        return
    use = {}
    for f in faces:
        for m in range(len(f)):
            e = (f[m], f[(m+1) % len(f)])
            use[tuple(sorted(e))] = use.get(tuple(sorted(e)), 0)+1
    for f in faces:
        fam.f.append(tuple(f))
        for m in range(len(f)):
            a, b = f[m], f[(m+1) % len(f)]
            if use[tuple(sorted((a, b)))] == 1:
                fam.f.append((b, a, a+1, b+1))


def _top(g, k, h):
    """The top of a plate h proud at lattice vertex k: (point, normal)."""
    n = g.normal(k)
    return Vector(g.P[k][0])+n*h, n


REACH = {'SMALL': 9.0, 'MEDIUM': 16.0, 'LARGE': 29.5}


def _in_sweep(p, pad=1.5):
    """True where a barrel can pass overhead in its arc: nothing taller than a plate goes there."""
    for sid, slot, ang, arc, size, kind, wpn in os_turrets.SLOTS:
        d = math.hypot(p.x-slot[0], p.y-slot[1])
        if d > REACH[size]+pad:
            continue
        a = math.degrees(math.atan2(p.y-slot[1], p.x-slot[0]))
        if abs((a-ang+180) % 360-180) <= arc/2+15:
            return True
    return False


def _in(u, w, rects):
    return any(a <= u <= b and c <= w <= d for a, b, c, d in rects)


def _deck_grid(side, s):
    t, o = _axis(s)
    A = Vector((DECK_A[0], s*DECK_A[1], 0.0))
    names = (f'Hull {side} arm gallery', f'Hull {side} arm')
    lights = [(x, s*y) for x, y in LIGHTS]

    def ok(u, w, h):
        p, n, name, mat = h
        if name not in names or n.z < .8 or mat == RECESS:
            return False
        if not _keep_clear(p.x, p.y, s, .8):
            return False
        return all(math.hypot(p.x-lx, p.y-ly) > 2.8 for lx, ly in lights)
    return Grid(A, t, o, (0, 0, -1), (-92.0, 128.0), (-28.0, 32.0), 1.0, ok, only=_arm(side), reach=150.0)


# trench, bays and banks on the deck, in the deck frame (u, w)
TRENCH = (12.0, 58.0, -21.0, -15.0)
BAYS = ((14.0, 33.0, -6.0, 0.0), (42.0, 54.0, -8.0, -1.0))
TERRACE_BANK = (24.0, 42.0, 11.0, 18.0)


def deck_plates(g, F, s):
    """The brown gun deck in three courses of big plates in alternating tones (dark brown, the
    deck's own brown, light bone-brown), staggered so no two neighbours match, with near-black seam
    strips between plates and between courses; the light plates carry a raised doubler."""
    ex = [(TRENCH[0]-1, TRENCH[1]+1, TRENCH[2]-1, TRENCH[3]+1),
          (TERRACE_BANK[0]-1, TERRACE_BANK[1]+1, TERRACE_BANK[2]-1, TERRACE_BANK[3]+1)]
    ex += [(a-1, b+1, c-1, d+1) for a, b, c, d in BAYS]
    keep = lambda u, w, h: not _in(u, w, ex)
    tones = ('dark', 'light', 'base')
    courses = (  # (w band, u boundaries, tone offset)
        ((-28, -13), (-92, -64, -36, -8, 12), 2),
        ((-28, -13), (58, 80, 104, 128), 0),
        ((-12, 6), (-92, -70, -50, -30, -12, 12, 38, 58, 83, 104, 128), 0),
        ((7, 32), (-92, -76, -60, -45, -30, -16, -2, 12, 24, 42, 56, 70, 86, 104, 128), 1),
    )
    for (w0, w1), cuts, off in courses:
        j0, j1 = g.ij(0, w0)[1], g.ij(0, w1)[1]
        for k, (a, b) in enumerate(zip(cuts, cuts[1:])):
            i0, i1 = g.ij(a, 0)[0], g.ij(b, 0)[0]-1
            tone = tones[(k+off) % 3]
            cells = panel(F[tone], g, i0, i1, j0, j1, .55, keep)
            if tone == 'light' and cells:
                panel(F['bone'], g, i0+2, i1-2, j0+2, j1-2, 1.0,
                      lambda u, w, h: keep(u, w, h) and not _in_sweep(h[0]))
            if b != cuts[-1]:
                panel(F['seam'], g, i1, i1+1, j0, j1, .2, keep)
    cuts = (-92, -70, -50, -30, -12, 12, 38, 58, 83, 104, 128)
    for w0, w1 in ((-13, -12), (6, 7)):          # the seams between courses, in plate lengths
        for a, b in zip(cuts, cuts[1:]):
            panel(F['seam'], g, g.ij(a, 0)[0], g.ij(b, 0)[0], g.ij(0, w0)[1], g.ij(0, w1)[1], .2, keep)


def deck_trench(g, F, side, s):
    """The inboard machinery trench: built up rather than cut, a near-black floor between two
    coaming walls, two big feed mains along it below the rim, straps over them and two pump
    housings standing a little above the rim."""
    u0, u1, w0, w1 = TRENCH
    i0, i1 = g.ij(u0, 0)[0], g.ij(u1, 0)[0]
    j0, j1 = g.ij(0, w0)[1], g.ij(0, w1)[1]
    panel(F['floor'], g, i0, i1, j0, j1, .12)
    for ja, jb in ((j0, j0+1), (j1-1, j1)):
        panel(F['wall'], g, i0, i1, ja, jb, 2.2)
    for ia, ib in ((i0, i0+1), (i1-1, i1)):
        panel(F['wall'], g, ia, ib, j0, j1, 2.2)
    for u in range(int(u0)+5, int(u1)-2, 6):
        ia = g.ij(u, 0)[0]
        panel(F['frame'], g, ia, ia+1, j0, j1, 2.0)
    t, o = _axis(s)
    A = Vector((DECK_A[0], s*DECK_A[1], 0.0))
    deck = lambda n: n in (f'Hull {side} arm gallery', f'Hull {side} arm')
    for w, r in ((w0+1.95, .85), (w1-1.95, .85)):
        pts = []
        for k in range(int((u1-u0-2)/1.5)+1):
            q = A+t*(u0+1.0+k*1.5)+o*w
            h = _cast(q+Vector((0, 0, 100)), (0, 0, -1), only=deck)
            if h:
                pts.append(Vector(h[0])+Vector(h[1])*(.14+r))
        F['mains'].tube(pts, r, ref=Vector((0, 0, 1)), seg=10)
    for u in (u0+9.0, u0+31.0):
        ia, ib = g.ij(u-2.5, 0)[0], g.ij(u+2.5, 0)[0]
        if panel(F['housing'], g, ia, ib, j0, j1, 2.7):
            panel(F['cap'], g, ia+1, ib-1, j0+1, j1-1, 3.1)


def deck_bays(g, F, side, s):
    """The two deck channels between the rings as framed machinery bays: a coaming round each slot,
    two feed lines along its floor, cross frames and a valve housing midway, all below the rim."""
    t, o = _axis(s)
    A = Vector((DECK_A[0], s*DECK_A[1], 0.0))
    gal = f'Hull {side} arm gallery'
    for u0, u1, w0, w1 in BAYS:
        i0, i1 = g.ij(u0, 0)[0], g.ij(u1, 0)[0]
        j0, j1 = g.ij(0, w0)[1], g.ij(0, w1)[1]
        for ia, ib, ja, jb in ((i0, i1, j0, j0+1), (i0, i1, j1-1, j1), (i0, i0+1, j0, j1), (i1-1, i1, j0, j1)):
            panel(F['wall'], g, ia, ib, ja, jb, 1.3)
        floor = lambda n: n == gal

        def at(u, w):
            q = A+t*u+o*w
            h = _cast(q+Vector((0, 0, 100)), (0, 0, -1), only=floor)
            return h if h and h[3] == RECESS and h[1].z > .8 else None
        wm = (w0+w1)/2
        for dw, r in ((-1.0, .62), (1.0, .62)):
            pts = []
            for k in range(int((u1-u0-1.6)/1.2)+1):
                h = at(u0+.8+k*1.2, wm+dw)
                if h:
                    pts.append(Vector(h[0])+Vector(h[1])*(r+.03))
            runs, cur = [], []
            for p in pts:
                if cur and (p-cur[-1]).length > 2.6:
                    runs.append(cur)
                    cur = []
                cur.append(p)
            runs.append(cur)
            for run in runs:
                if len(run) > 1:
                    F['lines'].tube(run, r, ref=Vector((0, 0, 1)), seg=8)
        for u in [u0+2.5+k*4.0 for k in range(int((u1-u0-4)/4.0)+1)]:
            h = at(u, wm)
            if h:
                F['frame'].slab(h[0], h[1], o, 1.9, .22, 1.6, .1)
        h = at((u0+u1)/2, wm)
        if h:
            F['housing'].slab(h[0], h[1], t, 1.6, 1.7, 1.9, .1, ch=.25)
            F['cap'].disc(Vector(h[0])+Vector(h[1])*1.9, h[1], .8, .35, .1, seg=12)


def terrace_bank(g, F, s):
    """A radiator bank on the outboard terrace where the sprite has its dark louvre panels: a
    coaming, a near-black floor and three modules of big fins across it."""
    u0, u1, w0, w1 = TERRACE_BANK
    i0, i1 = g.ij(u0, 0)[0], g.ij(u1, 0)[0]
    j0, j1 = g.ij(0, w0)[1], g.ij(0, w1)[1]
    poly = panel(F['floor'], g, i0, i1, j0, j1, .15)
    if not poly:
        return
    for ia, ib, ja, jb in ((i0, i1, j0, j0+1), (i0, i1, j1-1, j1), (i0, i0+1, j0, j1), (i1-1, i1, j0, j1)):
        panel(F['wall'], g, ia, ib, ja, jb, 1.2)
    third = (i1-i0)/3
    for k in (1, 2):
        ia = i0+int(round(k*third))
        panel(F['wall'], g, ia, ia+1, j0, j1, 1.2)
    for i in range(i0+1, i1):
        if any(i in (i0+int(round(k*third)), i0+int(round(k*third))+1) for k in (1, 2)):
            continue
        pts, nrms = [], []
        for j in range(j0+1, j1):
            if (i, j) in g.P and _inside(poly, *g.uw(i, j), .9):
                p, n = _top(g, (i, j), .15)
                pts.append(p)
                nrms.append(n)
        if len(pts) > 1:
            F['fins'].ribbon(pts, nrms, .32, .85, .05)


def _rail_grid(side, s, names, extra=None):
    """A lattice seen from above and outboard at 45 degrees: the rail's top and its chine face,
    the sponson's slope and the arm's outboard flank all face that way."""
    D = Vector((0, -s, -1)).normalized()
    W = Vector((0, s, -1)).normalized()

    def ok(u, w, h):
        p, n, name, mat = h
        if any((p-Vector((lx, s*ly, p.z))).length < 3.0 and abs(p.z-lz) < 4.0 for lx, ly, lz in LIGHTS3):
            return False
        return name in names and n.dot(-D) > .35 and mat != RECESS and (extra is None or extra(p, n, name))
    return Grid((0, s*110.0, -15.0), (1, 0, 0), W, D, (-10.0, 202.0), (-34.0, 34.0), 1.0, ok, only=_arm(side))


RAIL_STRAPS = (46.0, 94.0, 124.0, 152.0, 174.0)


def rail_plates(g, F, side, s):
    """The orange rail as a run of plates: dark grey straps across it at intervals, weathered
    darker orange plates on alternate segments, a dark louvre bank on its top aft of the sponson's
    forward end, and bolt rows down every strap."""
    rail = (f'Armour {side} arm belt rail', f'Armour {side} arm belt 1')
    name = lambda k: g.P[k][2]
    on = lambda i, j: name((i, j)) in rail
    onh = lambda u, w, h: h[2] in rail
    bank = (73.0, 90.0)
    for x in RAIL_STRAPS:
        i0, i1 = g.ij(x-1.5, 0)[0], g.ij(x+1.5, 0)[0]
        poly = panel(F['strap'], g, i0, i1, 0, g.nw, .9, onh, min_area=6.0)
        if poly:
            for i in (i0, i1):
                for j in range(0, g.nw+1, 2):
                    k = (i+(1 if i == i0 else -1), j)
                    if k in g.P and on(*k) and _inside(poly, *g.uw(*k), .45):
                        p, n = _top(g, k, .9)
                        F['bolts'].slab(p-g.U*(.0), n, g.U, .22, .22, .25, .1)
    cuts = (16.0,)+RAIL_STRAPS+(202.0,)
    for k, (a, b) in enumerate(zip(cuts, cuts[1:])):
        if k % 2:
            continue
        i0, i1 = g.ij(a+1.5+1.0, 0)[0], g.ij(b-1.5-1.0, 0)[0]
        keep = lambda u, w, h: onh(u, w, h) and not (bank[0]-1 <= u <= bank[1]+1 and h[1].z > .78)
        panel(F['weathered'], g, i0, i1, 0, g.nw, .5, keep)
    # the louvre bank on the rail's flat top
    top = lambda u, w, h: onh(u, w, h) and h[1].z > .78
    topl = lambda i, j: on(i, j) and g.P[i, j][1].z > .78
    i0, i1 = g.ij(bank[0], 0)[0], g.ij(bank[1], 0)[0]
    poly = panel(F['floor'], g, i0, i1, 0, g.nw, .25, top)
    if poly:
        for i in range(i0+1, i1):
            pts, nrms = [], []
            for j in range(g.nw+1):
                if (i, j) in g.P and topl(i, j) and _inside(poly, *g.uw(i, j), .3):
                    p, n = _top(g, (i, j), .25)
                    pts.append(p)
                    nrms.append(n)
            if len(pts) > 1:
                F['fins'].ribbon(pts, nrms, .3, .55, .05)


def flank_plates(g, F, side, s):
    """The arm's outboard flank forward and aft of the sponson in big plates of alternating tone."""
    hull = f'Hull {side} arm'
    on = lambda u, w, h: h[2] == hull
    for k, (a, b) in enumerate(((16, 30), (31, 46), (47, 66), (148, 162), (163, 178), (179, 202))):
        i0, i1 = g.ij(a, 0)[0], g.ij(b, 0)[0]-1
        panel(F[('dark', 'light')[k % 2]], g, i0, i1, 0, g.nw, .45, on)
    # the deck's outboard drop down to the rail: the brown band seen abeam above the orange
    gal = f'Hull {side} arm gallery'
    drop = lambda u, w, h: h[2] == gal
    cuts = (-10, 8, 24, 40, 58, 76, 92, 108, 126, 144, 162, 180, 200)
    for k, (a, b) in enumerate(zip(cuts, cuts[1:])):
        i0, i1 = g.ij(a, 0)[0], g.ij(b, 0)[0]-1
        panel(F[('light', 'dark')[k % 2]], g, max(0, i0), i1, 0, g.nw, .45, drop)


def _sponson_frame(side, s):
    """The sponson's slope frame from its vent slots: O at the aftmost slot, U along the row, D
    into the face, W down the face."""
    ob = bpy.data.objects.get(f'Armour {side} arm side sponson')
    if ob is None:
        return None
    mats = [m.name if m else None for m in ob.data.materials]
    rows = []
    for p in ob.data.polygons:
        if p.material_index < len(mats) and mats[p.material_index] == RECESS and p.area > 15:
            n = (ob.matrix_world.to_3x3() @ p.normal).normalized()
            if n.y*s > .3:
                rows.append((ob.matrix_world @ p.center, n))
    if len(rows) < 3:
        return None
    rows.sort(key=lambda r: r[0].x)
    D = -sum((n for c, n in rows), Vector()).normalized()
    U = rows[-1][0]-rows[0][0]
    U = (U-D*U.dot(D)).normalized()
    W = D.cross(U).normalized()
    if W.z > 0:
        W = -W
    return rows[0][0], U, W, D, (rows[-1][0]-rows[0][0]).dot(U)


def sponson_plates(F, side, s):
    """The sponson's slope in plate courses (grey-blue steel against the dark grey), the vent row
    left clear, and a framed radiator bank of big fins under the vent row."""
    fr = _sponson_frame(side, s)
    if fr is None:
        return
    O, U, W, D, L = fr
    name = f'Armour {side} arm side sponson'

    def ok(u, w, h):
        p, n, nm, mat = h
        return nm == name and n.dot(-D) > .3 and mat != RECESS
    g = Grid(O, U, W, D, (-34.0, L+38.0), (-26.0, 32.0), 1.0, ok, only=_arm(side), reach=40.0)
    # the trunk runs under the rail along the slope's top: plates start below it
    top = {}
    for (i, j) in g.P:
        top[i] = min(top.get(i, j), j)
    topw = lambda u: g.w0+g.step*top.get(g.ij(u, 0)[0], 10**6)
    row = (-3.5, L+3.5, -5.5, 5.5)
    rad = (-2.0, L+2.0, 6.5, 14.5)
    keep = lambda u, w, h: w >= topw(u)+6 and not _in(u, w, (
        (row[0]-1, row[1]+1, row[2], row[3]), (rad[0]-1, rad[1]+1, rad[2]-1, rad[3]+1)))
    tones = ('steel', 'base')
    span = L+72.0
    courses = (((-26, -7), 0), ((-6, 5), 1), ((6, 15), 0), ((16, 32), 1))
    for (w0, w1), off in courses:
        j0, j1 = g.ij(0, w0)[1], g.ij(0, w1)[1]
        n = 5 if off == 0 else 4
        cuts = [-34.0+span*k/n for k in range(n+1)]
        for k, (a, b) in enumerate(zip(cuts, cuts[1:])):
            i0, i1 = g.ij(a, 0)[0], g.ij(b, 0)[0]-1
            panel(F[tones[(k+off) % 2]], g, i0, i1, j0, j1, .4, keep)
    # radiator bank under the vent row
    u0, u1, w0, w1 = rad
    i0, i1 = g.ij(u0, 0)[0], g.ij(u1, 0)[0]
    j0, j1 = g.ij(0, w0)[1], g.ij(0, w1)[1]
    poly = panel(F['floor'], g, i0, i1, j0, j1, .12)
    if poly:
        for ia, ib, ja, jb in ((i0, i1, j0, j0+1), (i0, i1, j1-1, j1), (i0, i0+1, j0, j1), (i1-1, i1, j0, j1)):
            panel(F['wall'], g, ia, ib, ja, jb, .95)
        third = (i1-i0)/3
        divs = {i0+int(round(k*third)) for k in (1, 2)}
        for ia in divs:
            panel(F['wall'], g, ia, ia+1, j0, j1, .95)
        for i in range(i0+1, i1):
            if i in divs or i-1 in divs:
                continue
            pts, nrms = [], []
            for j in range(j0+1, j1):
                if (i, j) in g.P and _inside(poly, *g.uw(i, j), .9):
                    p, n = _top(g, (i, j), .12)
                    pts.append(p)
                    nrms.append(n)
            if len(pts) > 1:
                F['fins'].ribbon(pts, nrms, .3, .75, .05)


def big_layer(kit, side, s, mats):
    """Build every big-layer object for one arm."""
    g = _deck_grid(side, s)
    F = {k: Fam() for k in ('dark', 'light', 'bone', 'base', 'seam', 'floor', 'wall', 'frame', 'mains', 'housing',
                            'cap', 'lines', 'fins')}
    deck_plates(g, F, s)
    deck_trench(g, F, side, s)
    deck_bays(g, F, side, s)
    terrace_bank(g, F, s)
    R = {k: Fam() for k in ('strap', 'weathered', 'bolts', 'floor', 'fins', 'dark', 'light')}
    gal = f'Hull {side} arm gallery'

    def extra(p, n, nm):
        if nm == f'Hull {side} arm':
            return n.y*s > .3 and p.z < -10.0
        if nm == gal:
            return n.z < .75 and n.y*s > .2
        return n.z < .97
    rg = _rail_grid(side, s, (f'Armour {side} arm belt rail', f'Armour {side} arm belt 1', f'Hull {side} arm', gal),
                    extra)
    rail_plates(rg, R, side, s)
    flank_plates(rg, R, side, s)
    S = {k: Fam() for k in ('steel', 'base', 'floor', 'wall', 'fins')}
    sponson_plates(S, side, s)
    a = f'Detail {side} arm '
    out = [
        (F['dark'], 'deck plates dark', mats['dark']), (F['light'], 'deck plates light', mats['light']),
        (F['base'], 'deck plates', kit.plate), (F['bone'], 'deck doublers', mats['bone']), (F['seam'], 'deck plate seams', mats['seam']),
        (F['floor'], 'machinery bay floors', mats['seam']), (F['wall'], 'machinery bay coamings', kit.grey),
        (F['frame'], 'machinery bay frames', kit.steel), (F['mains'], 'trench mains', mats['pipe']),
        (F['housing'], 'trench pump housings', kit.grey), (F['cap'], 'trench pump caps', mats['steel']),
        (F['lines'], 'bay feed lines', mats['pipe']), (F['fins'], 'terrace radiator fins', mats['steel']),
        (R['strap'], 'rail straps', kit.grey), (R['weathered'], 'rail plates weathered', mats['orange']),
        (R['bolts'], 'rail strap bolts', kit.steel), (R['floor'], 'rail louvre bank', mats['seam']),
        (R['fins'], 'rail louvres', mats['steel']), (R['dark'], 'flank plates dark', mats['dark']),
        (R['light'], 'flank plates light', mats['light']),
        (S['steel'], 'sponson plates steel', mats['steel']), (S['base'], 'sponson plates', kit.grey),
        (S['floor'], 'sponson radiator floor', mats['seam']), (S['wall'], 'sponson radiator frame', kit.grey),
        (S['fins'], 'sponson radiator fins', mats['steel']),
    ]
    for fam, label, mat in out:
        fam.finish(kit, a+label, mat, smooth=0.0 if label.endswith(('mains', 'feed lines')) else 32.0)


def _mat(name, color, metallic, rough):
    m = bpy.data.materials.get(name)
    return m if m is not None else os_kit.material(name, color, metallic, rough)


def build(kit):
    _build_trees()
    hatch_mat = os_kit.material('Hegemony hatch plate', (.24, .19, .165), .35, .5)
    pipe_mat = os_kit.material('Conduit steel', (.16, .15, .145), .75, .42)
    mats = {
        'dark': _mat('Hegemony plate dark brown', (.045, .03, .026), .3, .65),
        'light': _mat('Hegemony plate bone-brown', (.32, .24, .19), .2, .6),
        'bone': _mat('Hegemony plate bone-brown doubler', (.40, .315, .255), .2, .6),
        'steel': _mat('Hegemony plate grey-blue steel', (.10, .112, .135), .45, .5),
        'orange': _mat('Hegemony orange weathered', (.34, .08, .024), .25, .62),
        'seam': _mat('Plate seam black', (.012, .010, .010), .3, .8),
        'pipe': pipe_mat,
    }
    seam_mat = os_kit.material('Plate seam shadow', (.045, .03, .028), .3, .7)
    # the big layer, seated on the bare arm
    for side, s in SIDES:
        big_layer(kit, side, s, mats)
    # the close-up layer that sits on bare surfaces the big layer leaves alone
    for side, s in SIDES:
        seams = Fam()
        under_seams(kit, side, s, seams)
        seams.finish(kit, f'Detail {side} arm plate seams', seam_mat)
        louv, sframe = Fam(), Fam()
        sponson(kit, side, s, louv, sframe, Fam(), Fam(), Fam())
        louv.finish(kit, f'Detail {side} arm sponson louvres', kit.steel)
        sframe.finish(kit, f'Detail {side} arm sponson grille frame', kit.steel, .05)
        tp, tc, tb = Fam(), Fam(), Fam()
        flank_trunk(kit, side, s, tp, tc, tb)
        tp.finish(kit, f'Detail {side} arm flank trunk', pipe_mat)
        tc.finish(kit, f'Detail {side} arm flank trunk brackets', kit.grey)
        tb.finish(kit, f'Detail {side} arm flank trunk glands', kit.grey, .06)
        nf, nb = Fam(), Fam()
        nose_surround(kit, side, s, nf, nb)
        nf.finish(kit, f'Detail {side} arm casemate surround', kit.grey, .08)
        nb.finish(kit, f'Detail {side} arm casemate surround bolts', kit.steel)
    # ... and the close-up layer that stands on the plates
    _build_trees(include=('Detail port arm ', 'Detail starboard arm '))
    for side, s in SIDES:
        lid, rim, fit, rails = Fam(), Fam(), Fam(), Fam()
        hatches(kit, side, s, lid, rim, fit, rails)
        lid.finish(kit, f'Detail {side} arm hatch lids', hatch_mat, .06)
        rim.finish(kit, f'Detail {side} arm hatch coamings', kit.grey, .05)
        fit.finish(kit, f'Detail {side} arm hatch fittings', kit.steel)
        rails.finish(kit, f'Detail {side} arm grab rails', kit.steel)
        sfit = Fam()
        ventilator(kit, side, s, sfit)
        sfit.finish(kit, f'Detail {side} arm sponson fittings', kit.steel, .04)
