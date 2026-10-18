"""Surface detail on the two gun arms (os_arms): armour plate seams and bolt rows, access hatches, flank piping, exposed machinery between plates, sponson and vent detail.

Owner, after round 11: "I'm liking the silhouette of this ship. The next passes
need to focus on that detail on the hull, exposed machinery, piping, hatches,
and armor." The silhouette is locked: detail stands at most a little proud of
the surface it sits on, never changes the outline, and keeps clear of the
turret rings (os_turrets.SLOTS, RING) and the running lights. Every object
built here is named 'Detail <area> ...'.

Everything is placed in the WORLD after os_arms has posed the arms (rolled 16 deg outboard,
pitched 6 deg nose down) and os_turrets has let the mounts into the deck: each element is seated
by ray casts against the built arm parts and laid along the surface normal it lands on, so it
follows the rolled deck, the rail's chine and the sponson's bulge without knowing their formulas.
Read off onslaught_hegemony.png at 8x (model X = 244 - row, Y = 144 - col), the port arm carries:

  rail        the orange rail is a run of plates: butt seams across it with a bolt row either
              side, between the white edge-line lights (os_fittings.EDGE_X), never on them.
  deck        the brown gun deck is plated in panels: transverse seams between the turret rings
              and a longitudinal seam each side of the gun line, as thin dark lines.
  hatches     personnel hatches on the inboard deck between the rings (behind every mount's arc),
              the sprite's round hatch between WS 001 and WS 002 (X 142, Y 83), its round trunk
              hatch aft of the Devastator (X 42, Y 99) and the round hatch on the broadside fin
              (X 5, Y 93) with the three small vents beside it.
  conduits    a pair of feed conduits on the inboard deck from the Devastator's drum forward to
              WS 001, with a branch into each mount it passes (WS 005, WS 002), ending in junction
              boxes on the mount plates; clamped down every few units.
  channels    the deck channels between the rings (the sprite's dark lines) hold exposed
              machinery: two feed lines along the channel floor between transverse frames, and a
              valve block midway; all of it below the deck.
  vents       the sprite's dark louvre panels: two on the outboard terrace (X 101-112) and four on
              the rail aft of the sponson (X 73-88).
  sponson     louvres in its six vent slots, a frame round the vent row, plate seams, a bolt row
              under the rail's edge, a domed ventilator and lifting eyes on its top.
  flank       a power trunk on the arm's outboard lower face: from the after collar forward into
              the sponson's after end, and out of its forward end to the TPC casemate.
  nose        a bolted surround where the TPC casemate leaves the arm's nose face.
  rails       grab rails only beside the hatches crew use.
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

def _build_trees():
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _TREES.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.startswith(('Light ', 'Temporary', 'Detail ')):
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        pts = [ev.matrix_world @ v.co for v in me.vertices]
        if pts:
            lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
            hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
            _TREES[ob.name] = (BVHTree.FromPolygons(pts, [tuple(p.vertices) for p in me.polygons]), lo, hi)
        ev.to_mesh_clear()


def _arm(side):
    """The arm's own bodies: what detail may be seated on."""
    pre = (f'Hull {side} arm', f'Armour {side} arm', f'Armour {side} nose cheek', f'Armour {side} broadside fin')
    return lambda n: n.startswith(pre)


def _cast(origin, direction, only=None, far=400.0):
    """Nearest hit: (point, normal facing back along the ray, object name) or None."""
    o, d = Vector(origin), Vector(direction).normalized()
    e = o+d*far
    lo = Vector(map(min, o, e))
    hi = Vector(map(max, o, e))
    best = None
    for name, (t, blo, bhi) in _TREES.items():
        if only is not None and not only(name):
            continue
        if blo.x > hi.x or bhi.x < lo.x or blo.y > hi.y or bhi.y < lo.y or blo.z > hi.z or bhi.z < lo.z:
            continue
        p, n, _, dist = t.ray_cast(o, d, far)
        if p is not None and (best is None or dist < best[0]):
            best = (dist, p, -n if n.dot(d) > 0 else n, name)
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

    def finish(self, kit, name, mat, bevel=0):
        if not self.f:
            return None
        return kit.mesh(name, self.v, self.f, mat, bevel)


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

def rail_seams(kit, side, s, seams, bolts):
    """Butt seams across the orange rail with a bolt row either side; the sprite's rail is a run of
    plates, not one extrusion."""
    rail = f'Armour {side} arm belt rail'
    only = lambda n: n == rail
    for x in (30.0, 42.0, 50.0, 72.0, 86.0, 110.0, 122.0, 132.0, 150.0, 160.0):
        if not _light_clear(x):
            continue
        pts, nrms = [], []
        c = math.sqrt(.5)
        d = Vector((0, -s*c, -c))
        e = Vector((0, s*c, -c))
        for k in range(0, 400):
            o = Vector((x, 0, 0))+e*(k*.3)-d*200.0
            h = _cast(o, d)
            if h is None or h[2] != rail:
                continue
            pts.append(h[0])
            nrms.append(h[1])
        if len(pts) < 3:
            continue
        seams.ribbon(pts, nrms, .32, .14, .3)
        for dx in (-.6, .6):
            for p, n in list(zip(pts, nrms))[2:-2:4]:
                q = Vector(p)+Vector((dx, 0, 0))
                h = _cast(q+Vector(n)*3.0, -Vector(n), only=only)
                if h:
                    bolts.slab(h[0], h[1], Vector((1, 0, 0)), .2, .2, .22, .2)


def deck_seams(kit, side, s, seams):
    """Plate seams on the brown gun deck: across it between the rings and along it either side of
    the gun line, as thin dark lines (never through a kept ring)."""
    t, o = _axis(s)

    def clear(h):
        return (h[2] in (f'Hull {side} arm gallery', f'Hull {side} arm') and h[1].z > .7
                and _keep_clear(h[0].x, h[0].y, s, .6) and all(abs(h[0].x-e) > 1.4 for e in EDGE_X))
    for x in (96.0, 104.0, 124.0, 146.0, 170.0):
        c = Vector((x, s*_gun_line(x), 0))
        for run in _drape(side, c-o*26.0, c+o*30.0, .5, clear):
            seams.ribbon(run[0], run[1], .3, .12, .3)
    for off in (-9.5, 10.5):
        pts = [Vector((x, s*_gun_line(x), 0))+o*off for x in (90.0, 104.0, 124.0, 146.0, 176.0)]
        for a, b in zip(pts, pts[1:]):
            for run in _drape(side, a, b, .5, clear):
                seams.ribbon(run[0], run[1], .3, .12, .3)


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


def _vent(panel, slats, frame, h, t, L, W, n_slats):
    """A louvre panel: a dark recess plate, slats across it, a frame round it."""
    p, n = Vector(h[0]), Vector(h[1])
    tt = (t-n*t.dot(n)).normalized()
    b = n.cross(tt).normalized()
    frame.slab(p, n, tt, L/2+.35, W/2+.35, .3, .3)
    panel.slab(p, n, tt, L/2, W/2, .36, .3)
    for k in range(n_slats):
        f = (k+.5)/n_slats-.5
        slats.box(p+tt*(f*L)+n*.52, tt, b, n, .14, W/2-.1, .14)


def vents(kit, side, s, panel, slats, frame):
    """The sprite's dark louvre panels on the outboard terrace (X 101-112) and on the rail aft of
    the sponson (X 73-88)."""
    t, o = _axis(s)
    for xc, yc, L, W in ((103.2, 100.0, 3.6, 3.2), (109.2, 99.0, 4.6, 3.2)):
        h = _drop(xc, s*yc, side)
        if h and h[1].z > .6:
            _vent(panel, slats, frame, h, t, L, W, 5 if L < 4 else 6)
    rail = f'Armour {side} arm belt rail'
    for xc in (74.0, 78.0, 82.0, 86.0):
        for dy in (0.0, .5, 1.0, 1.5, 2.0, 2.5, 3.0):
            h = _drop(xc, s*(107.5+dy), side)
            if h and h[1].z > .8 and h[2] == rail:
                _vent(panel, slats, frame, h, t, 2.4, 1.6, 3)
                break


def _pipe_run(side, s, plan, r, pipes, clamps, with_clamps):
    """A pipe laid on the deck along a plan polyline: its centre a standoff over the surface, with
    saddle clamps."""
    pts, nrms = [], []
    for a, b in zip(plan, plan[1:]):
        for run in _drape(side, a, b, .8):
            pts += [Vector(p) for p in run[0]]
            nrms += [Vector(n) for n in run[1]]
    if len(pts) < 2:
        return
    cen = [p+n*(r+.02) for p, n in zip(pts, nrms)]
    for i in range(len(cen)):
        for j in (i-1, i+1):
            if 0 <= j < len(cen):
                need = pts[j].z+r+.02
                if cen[i].z < need-.05:
                    cen[i].z = need
    keep = cen[::2]+([cen[-1]] if len(cen) % 2 == 0 else [])
    pipes.tube(keep, r, ref=Vector((0, 0, 1)), seg=8)
    if with_clamps:
        for i in range(3, len(pts)-2, 6):
            p, n = pts[i], nrms[i]
            tt = (pts[i+1]-pts[i-1]).normalized()
            clamps.slab(p, n, tt, .5, r+.75, .18, .2)              # foot plate
            clamps.slab(p, n, tt, .2, r+.18, 2*r+.32, .1)          # strap over the pipe


def conduits(kit, side, s, pipes, clamps, boxes):
    """Feed conduits on the inboard deck, behind every mount's arc: from the Devastator's drum
    forward to WS 001, branching into WS 005 and WS 002, ending in junction boxes."""
    t, o = _axis(s)
    route = [(86.0, 74.0), (100.0, 70.6), (124.0, 67.4), (149.5, 63.8)]
    branches = [((110.0, 69.4), (112.3, 72.6)), ((131.0, 66.5), (132.6, 68.6))]
    for k, (dy, r) in enumerate(((0.0, .55), (-1.5, .4))):
        plan = [Vector((x, s*y, 0))+o*dy for x, y in route]
        _pipe_run(side, s, plan, r, pipes, clamps, True)
    for a, b in branches:
        plan = [Vector((a[0], s*a[1], 0)), Vector((b[0], s*b[1], 0))]
        _pipe_run(side, s, plan, .45, pipes, clamps, False)
        h = _drop(b[0], s*b[1], side)
        if h:
            boxes.slab(h[0], h[1], Vector((b[0]-a[0], s*(b[1]-a[1]), 0)), .8, .9, 1.3, .3, ch=.15)
    for x, y in (route[0], route[-1]):
        h = _drop(x, s*y, side)
        if h:
            boxes.slab(Vector(h[0])-o*.6, h[1], t, 1.2, 1.6, 1.5, .3, ch=.2)


def channel_machinery(kit, side, s, pipes, frames, blocks):
    """Exposed machinery in the deck channels between the rings: two feed lines along each
    channel's floor, transverse frames over them and a valve block midway, all below the deck."""
    rings = [(158, 71, 8.0), (134, 74, 5.4), (114, 78, 5.4), (77, 88, 15.0)]
    floor = lambda n: n == f'Hull {side} arm gallery'
    for (ax, ay, ar), (bx, by, br) in zip(rings, rings[1:]):
        A, B = Vector((ax, s*ay, 0)), Vector((bx, s*by, 0))
        u = (B-A).normalized()
        w = Vector((-u.y, u.x, 0))
        sa = max(ar, KEEP*_ring(ax, ay))+.5
        sb = max(br, KEEP*_ring(bx, by))+.5
        p0, p1 = A+u*sa, B-u*sb
        L = (p1-p0).length
        if L < 3:
            continue
        cen = []
        n = max(2, int(L/.8))
        for i in range(n+1):
            q = p0+(p1-p0)*(i/n)
            cen.append(_cast((q.x, q.y, 120.0), (0, 0, -1), only=floor))
        if any(h is None for h in cen):
            continue
        nz = Vector(cen[len(cen)//2][1])
        for off, r in ((-.75, .32), (.75, .32)):
            pts = [Vector(h[0])+w*off+Vector(h[1])*(r+.08) for h in cen]
            pipes.tube(pts[::2]+[pts[-1]], r, ref=nz, seg=8)
        for i in range(2, len(cen)-1, 4):
            h = cen[i]
            frames.slab(h[0], h[1], w, 1.55, .18, .75, .1)
        mid = cen[len(cen)//2]
        blocks.slab(mid[0], mid[1], u, .7, 1.1, .85, .1, ch=.12)


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
    # plate seams down the face (outside the vent row), with bolts either side
    for x in (75.0, 129.0, 139.0):
        prof = [(z, p, n) for z, p, n, nm in _profile(x, s, 0.0, -50.0, .4) if nm == name]
        if len(prof) < 4:
            continue
        seams.ribbon([p for z, p, n in prof], [n for z, p, n in prof], .3, .12, .3)
        for dx in (-.7, .7):
            for z, p, n in prof[1:-1:3]:
                h = _cast(p+n*3.0+Vector((dx, 0, 0)), -n, only=on)
                if h:
                    bolts.slab(h[0], h[1], Vector((1, 0, 0)), .2, .2, .22, .2)
    # a bolt row along the sponson's top, just under the rail's lower edge
    for x in _frange(71.0, 145.0, 1.6):
        prof = [(z, p, n) for z, p, n, nm in _profile(x, s, 0.0, -40.0, .25) if nm == name]
        if prof:
            z, p, n = prof[0]
            h = _cast(p+n*3.0+Vector((0, 0, -.9)), -n, only=on)
            if h:
                bolts.slab(h[0], h[1], Vector((1, 0, 0)), .2, .2, .22, .2)
    # a domed ventilator on its face between the forward seams (the sprite's round fitting)
    for x in (134.0,):
        prof = [(z, p, n) for z, p, n, nm in _profile(x, s, 0.0, -45.0, .25) if nm == name]
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
    run1, run2, nrm = [], [], []
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
        p1, n1, _ = at(z_r-.9)
        p2, n2, _ = at(z_r-2.4)
        run1.append(p1+n1*.55)
        run2.append(p2+n2*.4)
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
    nrm = [n for n, g in zip(nrm, good) if g]
    for run, r in ((run1, .6), (run2, .45)):
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
        c = (run1[i]+run2[i])/2
        clamps.box(c-n*.15, t, b, n, .22, ((run1[i]-run2[i]).length+1.6)/2, .55)
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


def build(kit):
    _build_trees()
    hatch_mat = os_kit.material('Hegemony hatch plate', (.24, .19, .165), .35, .5)
    seam_mat = os_kit.material('Plate seam shadow', (.045, .03, .028), .3, .7)
    pipe_mat = os_kit.material('Conduit steel', (.16, .15, .145), .75, .42)
    for side, s in SIDES:
        seams, bolts = Fam(), Fam()
        rail_seams(kit, side, s, seams, bolts)
        deck_seams(kit, side, s, seams)
        under_seams(kit, side, s, seams)
        lid, rim, fit, rails = Fam(), Fam(), Fam(), Fam()
        hatches(kit, side, s, lid, rim, fit, rails)
        panel, slats, frame = Fam(), Fam(), Fam()
        vents(kit, side, s, panel, slats, frame)
        pipes, clamps, boxes = Fam(), Fam(), Fam()
        conduits(kit, side, s, pipes, clamps, boxes)
        cpipes, cframes, cblocks = Fam(), Fam(), Fam()
        channel_machinery(kit, side, s, cpipes, cframes, cblocks)
        seams.finish(kit, f'Detail {side} arm plate seams', seam_mat)
        bolts.finish(kit, f'Detail {side} arm bolt rows', kit.steel)
        lid.finish(kit, f'Detail {side} arm hatch lids', hatch_mat, .06)
        rim.finish(kit, f'Detail {side} arm hatch coamings', kit.grey, .05)
        fit.finish(kit, f'Detail {side} arm hatch fittings', kit.steel)
        rails.finish(kit, f'Detail {side} arm grab rails', kit.steel)
        panel.finish(kit, f'Detail {side} arm vent recesses', kit.dark)
        slats.finish(kit, f'Detail {side} arm vent louvres', kit.steel)
        frame.finish(kit, f'Detail {side} arm vent frames', kit.grey, .05)
        pipes.finish(kit, f'Detail {side} arm deck conduits', pipe_mat)
        clamps.finish(kit, f'Detail {side} arm conduit clamps', kit.grey)
        boxes.finish(kit, f'Detail {side} arm junction boxes', kit.grey, .08)
        cpipes.finish(kit, f'Detail {side} arm channel feed lines', pipe_mat)
        cframes.finish(kit, f'Detail {side} arm channel frames', kit.steel)
        cblocks.finish(kit, f'Detail {side} arm channel valve blocks', kit.grey, .06)
        louv, sframe, sseams, sbolts, sfit = Fam(), Fam(), Fam(), Fam(), Fam()
        sponson(kit, side, s, louv, sframe, sseams, sbolts, sfit)
        louv.finish(kit, f'Detail {side} arm sponson louvres', kit.steel)
        sframe.finish(kit, f'Detail {side} arm sponson grille frame', kit.steel, .05)
        sseams.finish(kit, f'Detail {side} arm sponson seams', seam_mat)
        sbolts.finish(kit, f'Detail {side} arm sponson bolt rows', kit.steel)
        sfit.finish(kit, f'Detail {side} arm sponson fittings', kit.steel, .04)
        tp, tc, tb = Fam(), Fam(), Fam()
        flank_trunk(kit, side, s, tp, tc, tb)
        tp.finish(kit, f'Detail {side} arm flank trunk', pipe_mat)
        tc.finish(kit, f'Detail {side} arm flank trunk brackets', kit.grey)
        tb.finish(kit, f'Detail {side} arm flank trunk glands', kit.grey, .06)
        nf, nb = Fam(), Fam()
        nose_surround(kit, side, s, nf, nb)
        nf.finish(kit, f'Detail {side} arm casemate surround', kit.grey, .08)
        nb.finish(kit, f'Detail {side} arm casemate surround bolts', kit.steel)
