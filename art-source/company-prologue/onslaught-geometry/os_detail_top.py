"""Surface detail on the topside of the prow, glacis, citadel and bridge (os_hull, os_armour, os_bridge): deck hatches, plate seams, spine conduits, ventilators, machinery in the waist valleys.

Owner, after round 11: "I'm liking the silhouette of this ship. The next passes
need to focus on that detail on the hull, exposed machinery, piping, hatches,
and armor." The silhouette is locked: detail stands at most a little proud of
the surface it sits on, never changes the outline, and keeps clear of the
turret rings (os_turrets.SLOTS, RING) and the running lights. Every object
built here is named 'Detail <area> ...'.

Read off onslaught_hegemony.png at 7x (model X = 244 - row, Y = 144 - col):
  prow        a pale strip down the nose aft to WS 007 with a long hatch in it;
              a ribbed shell (louvred grille) between WS 007 and WS 008/009;
              dark vent slots near the orange shoulders' outer edge (X ~188);
              the shoulders and the glacis legs as bolted plate courses.
  glacis      the dark grey A-legs in blocks with small inspection hatches, the
              orange band outboard in long plates.
  waists      the dark valley between glacis and arm: exposed machinery, read
              here as a grated floor on frames carrying two pipe runs from the
              amber-lit bay frame forward (os_armour) aft to deck trunks, with
              pumps and valve chests along them.
  core        the gutter between the spine and the grey leg carries conduits
              from the gun gallery aft; a ring of conduits on the citadel's
              terrace links the turret wells; vent cupolas and louvres round
              the citadel (the sprite's dark slots and two pale discs at X ~9).
  citadel     blast-shutter leaves at the glazing's ends, ladders up the cheek
              slabs to the brow walkway, comm boxes and whips on the armour
              tier, a handrail round the crown hatch.

Every part is seated by ray casts against the Hull, Armour and Bridge solids
(the topmost surface under it), oriented to the surface normal, sunk a little
so nothing floats; a part whose footprint spans a step is dropped rather than
bridged. Parts are kept clear of every turret ring (1.3 r) and of each turret's
trainable envelope (barrel reach over its arc, where the barrels are lower than
the part's top). Repeated parts are one mesh per family (per side where sided).
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
import os_turrets
from os_kit import material

SEAT_FAMILIES = ('Hull', 'Armour', 'Bridge')
KEEP_RING = 1.3          # turret keep-out, over the ring radius
DEBUG = False

# The command citadel as os_bridge builds it (final heights): twelve flats about
# CIT_C, the drum foot's apothem CIT_A.
CIT_C = (24.0, 0.0)
CIT_A = 21.7
CIT_SIDES = 12


# --- seating ----------------------------------------------------------------------

class _Seat:
    """Topmost Hull/Armour/Bridge surface under a point, by BVH ray casts."""

    def __init__(self):
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        self.trees = []
        for ob in bpy.context.scene.objects:
            if ob.type != 'MESH' or ob.name.split(' ')[0] not in SEAT_FAMILIES:
                continue
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            pts = [ev.matrix_world @ v.co for v in me.vertices]
            if pts:
                lo = (min(p.x for p in pts), min(p.y for p in pts))
                hi = (max(p.x for p in pts), max(p.y for p in pts))
                self.trees.append((ob.name, BVHTree.FromPolygons(pts, [tuple(p.vertices) for p in me.polygons]),
                                   lo, hi))
            ev.to_mesh_clear()

    def top(self, x, y, z=160.0):
        """(point, upward normal, object name) of the first surface below (x, y, z)."""
        o, d = Vector((x, y, z)), Vector((0, 0, -1))
        best = None
        for name, t, lo, hi in self.trees:
            if not (lo[0]-.01 <= x <= hi[0]+.01 and lo[1]-.01 <= y <= hi[1]+.01):
                continue
            p, n, _, dist = t.ray_cast(o, d)
            if p is not None and (best is None or dist < best[0]):
                best = (dist, p, n if n.z >= 0 else -n, name)
        return None if best is None else best[1:]


class _Keep:
    """Turret keep-out: each ring and its well (KEEP_RING r), and the ground each turret's
    trainable parts sweep over across their arc. The sweep is a plan grid of the lowest
    trained part over each cell, from the parts' edges rotated about the pivot's own axis
    through the arc in 3 deg steps (the mounts lean with the hull, so a barrel's height
    changes as it trains)."""
    CELL = .7

    def __init__(self):
        import numpy as np
        self.rings = []
        self.low = {}
        dg = bpy.context.evaluated_depsgraph_get()
        for sid, (x, y), angle, arc, size, _, _ in os_turrets.SLOTS:
            r = os_turrets.RING[size]
            self.rings.append((x, y, KEEP_RING*r+.4))
            piv = bpy.data.objects.get(f'Weapon {sid} pivot')
            if piv is None:
                continue
            inv = piv.matrix_world.inverted()
            local = []
            for ch in piv.children_recursive:
                if ch.type != 'MESH':
                    continue
                ev = ch.evaluated_get(dg)
                me = ev.to_mesh()
                w = [inv @ (ev.matrix_world @ v.co) for v in me.vertices]
                for e in me.edges:
                    a, b = w[e.vertices[0]], w[e.vertices[1]]
                    k = max(1, int((b-a).length/.35))
                    local += [tuple(a+(b-a)*(i/k)) for i in range(k+1)]
                ev.to_mesh_clear()
            if not local:
                continue
            L = np.array(local)
            M = np.array(piv.matrix_world)
            keep2 = (KEEP_RING*r+.4)**2
            for d in np.arange(-arc/2, arc/2+.01, 3.0):
                t = math.radians(d)
                c, sn = math.cos(t), math.sin(t)
                R = np.array([[c, -sn, 0], [sn, c, 0], [0, 0, 1]])
                P = (L@R.T)@M[:3, :3].T+M[:3, 3]
                far = (P[:, 0]-x)**2+(P[:, 1]-y)**2 > keep2
                for px, py, pz in P[far]:
                    key = (int(math.floor(px/self.CELL)), int(math.floor(py/self.CELL)))
                    if pz < self.low.get(key, 1e9):
                        self.low[key] = pz

    def ok(self, pts, ztop):
        for x, y in pts:
            for cx, cy, rr in self.rings:
                if (x-cx)**2+(y-cy)**2 < rr*rr:
                    return False
            i, j = int(math.floor(x/self.CELL)), int(math.floor(y/self.CELL))
            for di in (-2, -1, 0, 1, 2):
                for dj in (-2, -1, 0, 1, 2):
                    if ztop > self.low.get((i+di, j+dj), 1e9)-.35:
                        return False
        return True


class _Batch:
    """Oriented boxes and round bars gathered into one mesh."""

    def __init__(self):
        self.v, self.f = [], []

    def box(self, c, u, v, w, hu, hv, hw):
        c, u, v, w = Vector(c), Vector(u)*hu, Vector(v)*hv, Vector(w)*hw
        b = len(self.v)
        for su in (-1, 1):
            for sv in (-1, 1):
                for sw in (-1, 1):
                    self.v.append(tuple(c+u*su+v*sv+w*sw))
        q = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
        self.f += [tuple(b+i for i in f) for f in q]

    def bar(self, a, b, r, n=8):
        """A closed n-sided round bar from a to b."""
        a, b = Vector(a), Vector(b)
        ax = (b-a).normalized()
        ref = Vector((0, 0, 1)) if abs(ax.z) < .9 else Vector((1, 0, 0))
        u = ax.cross(ref).normalized()
        v = ax.cross(u)
        base = len(self.v)
        for p in (a, b):
            for k in range(n):
                t = 2*math.pi*(k+.5)/n
                self.v.append(tuple(p+(u*math.cos(t)+v*math.sin(t))*r))
        self.f.append(tuple(base+k for k in reversed(range(n))))
        self.f.append(tuple(base+n+k for k in range(n)))
        for k in range(n):
            k1 = (k+1) % n
            self.f.append((base+k, base+k1, base+n+k1, base+n+k))

    def build(self, kit, name, mat):
        if self.v:
            return kit.mesh(name, self.v, self.f, mat)


class _Detail:
    def __init__(self, kit):
        self.kit = kit
        self.seat = _Seat()
        self.keep = _Keep()
        self.batches = {}
        self.dropped = []

    def batch(self, name, mat):
        if name not in self.batches:
            self.batches[name] = (_Batch(), mat)
        return self.batches[name][0]

    def frame(self, x, y, heading):
        """Seat frame at (x, y): (point, u along heading in the surface, v across, normal)."""
        hit = self.seat.top(x, y)
        if hit is None:
            return None
        p, n, _ = hit
        u = Vector((math.cos(heading), math.sin(heading), 0))
        u = (u-n*u.dot(n)).normalized()
        return p, u, n.cross(u), n

    def plate(self, batch, x, y, heading, length, width, height, max_rise=.8, sink=.06, label='plate'):
        """A box seated on the surface under (x, y): its base follows the lowest of its
        sampled footprint points (so no corner floats), its top stands `height` over the
        highest. Returns (top centre, u, v, n), or None when the footprint spans a step,
        leaves the hull or meets a turret."""
        f = self.frame(x, y, heading)
        if f is None:
            self.dropped.append((label, x, y, 'no surface'))
            return None
        p, u, v, n = f
        ds, foot, hz = [], [], []
        for su in (-1, 0, 1):
            for sv in (-1, 0, 1):
                q = p+u*su*length/2+v*sv*width/2
                foot.append((q.x, q.y))
                h = self.seat.top(q.x, q.y, z=q.z+6.0)
                if h is None:
                    self.dropped.append((label, x, y, 'edge'))
                    return None
                ds.append((h[0]-p).dot(n))
                hz.append(h[0].z)
        nu, nv = max(2, int(math.ceil(length/.8))), max(2, int(math.ceil(width/.8)))
        foot += [tuple((p+u*(i/nu-.5)*length+v*(j/nv-.5)*width)[:2]) for i in range(nu+1) for j in range(nv+1)]
        lo, hi = min(ds), max(ds)
        if hi-lo > max_rise:
            self.dropped.append((label, x, y, f'rise {hi-lo:.2f}'))
            return None
        base, top = lo-sink, hi+height
        if not self.keep.ok(foot, max(hz)+height+(hi-lo)):
            self.dropped.append((label, x, y, 'turret'))
            return None
        batch.box(p+n*((base+top)/2), u, v, n, length/2, width/2, (top-base)/2)
        return p+n*top, u, v, n

    def bolts(self, batch, pts, size=.42, height=.28):
        """A row of bolt heads, each seated on its own cast."""
        for x, y in pts:
            hit = self.seat.top(x, y)
            if hit is None:
                continue
            p, n, _ = hit
            if not self.keep.ok([(x, y)], p.z+height):
                continue
            u = Vector((1, 0, 0))
            u = (u-n*u.dot(n)).normalized()
            batch.box(p+n*(height/2-.05), u, n.cross(u), n, size/2, size/2, height/2+.05)

    def run(self, pipe, saddle, pts, r, lift=.2, step=2.0, saddle_every=4, flange=None):
        """A pipe laid along a plan polyline: sampled every `step`, each sample seated
        r + lift over the surface, straight lengths between samples, a saddle under
        every `saddle_every`-th sample and a flange collar at each bend. Returns the
        pipe's centreline, or [] when a sample leaves the hull or meets a turret."""
        samples = []
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            L = math.hypot(bx-ax, by-ay)
            k = max(1, int(math.ceil(L/step)))
            samples += [(ax+(bx-ax)*j/k, ay+(by-ay)*j/k) for j in range(k)]
        samples.append(pts[-1])
        line = []
        for x, y in samples:
            hit = self.seat.top(x, y)
            if hit is None:
                self.dropped.append(('pipe', x, y, 'no surface'))
                return []
            p, n, _ = hit
            line.append(Vector((x, y, p.z+(r+lift)/max(n.z, .5))))
        # Lay the pipe straight over small undulations: each sample takes the highest
        # seat within two samples either side (so it never dips into the deck), then
        # runs of nearly collinear samples merge into one straight length.
        zs = [c.z for c in line]
        for i, c in enumerate(line):
            c.z = max(zs[max(0, i-2):i+3])
        for c in line:
            if not self.keep.ok([(c.x, c.y)], c.z+r):
                self.dropped.append(('pipe', c.x, c.y, 'turret'))
                return []
        keep = [0]
        for i in range(1, len(line)-1):
            d0 = (line[i]-line[keep[-1]]).normalized()
            d1 = (line[i+1]-line[i]).normalized()
            if d0.dot(d1) < .996:
                keep.append(i)
        keep.append(len(line)-1)
        knots = [line[i] for i in keep]
        for a, b in zip(knots, knots[1:]):
            ext = (b-a).normalized()*r*.3
            pipe.bar(a-ext, b+ext, r, n=10)
        flange = flange or saddle
        for i in range(1, len(knots)-1):
            d0 = (knots[i]-knots[i-1]).normalized()
            d1 = (knots[i+1]-knots[i]).normalized()
            if d0.dot(d1) < .985:
                flange.bar(knots[i]-d0*.25, knots[i]+d0*.25, r*1.3, n=10)
        # Saddles (pipe clamps) every `saddle_every` samples, down to the deck.
        for i in range(1, len(line)-1, saddle_every):
            c = line[i]
            d = line[i+1]-line[i-1]
            along = Vector((d.x, d.y, 0)).normalized()
            across = Vector((-along.y, along.x, 0))
            zb = self.seat.top(c.x, c.y)[0].z-.08
            ztop = c.z-r*.3
            saddle.box(Vector((c.x, c.y, (ztop+zb)/2)), along, across, Vector((0, 0, 1)), .25, r*1.15, (ztop-zb)/2)
            flange.bar(c-along*.18, c+along*.18, r*1.18, n=10)
        return line

    def finish(self):
        for name, (b, mat) in sorted(self.batches.items()):
            b.build(self.kit, name, mat)
        if DEBUG:
            for d in self.dropped:
                print('DETAIL TOP DROPPED', d)
        print(f'DETAIL TOP: {len(self.batches)} meshes, {len(self.dropped)} parts dropped')


# --- helpers ------------------------------------------------------------------------

def _edges(dt, x, names, s, y0=0.0, y1=100.0, step=.5):
    """(|Y| lo, |Y| hi) at X where the topmost seat object's name contains one of `names`."""
    ys = []
    y = y0
    while y <= y1:
        hit = dt.seat.top(x, s*y)
        if hit and any(k in hit[2] for k in names):
            ys.append(y)
        y += step
    return (ys[0], ys[-1]) if ys else None


def _smooth(vals, k=2):
    out = []
    for i in range(len(vals)):
        w = vals[max(0, i-k):i+k+1]
        out.append(sum(w)/len(w))
    return out


def _cit(apo, bearing, z):
    """A point on the citadel's flats at apothem `apo`, bearing deg from the bow, height z."""
    step = 360.0/CIT_SIDES
    d = (bearing+step/2) % step-step/2
    r = apo/math.cos(math.radians(d))
    b = math.radians(bearing)
    return Vector((CIT_C[0]+r*math.cos(b), CIT_C[1]+r*math.sin(b), z))


def _polar(rad, bearing):
    b = math.radians(bearing)
    return (CIT_C[0]+rad*math.cos(b), CIT_C[1]+rad*math.sin(b))


# --- areas ----------------------------------------------------------------------------

PROW_SEAMS = (191.0, 175.0, 157.0, 140.0, 125.0)


def _prow(dt, kit, pipe_steel):
    """Nose strip and hatch, the ribbed grille ahead of WS 008/009, deck hatches and
    straps on the gun decks, vent slots and bolt rows on the orange shoulders."""
    steel = dt.batch('Detail prow fittings', kit.steel)
    dark = dt.batch('Detail prow vents', kit.dark)
    plate = dt.batch('Detail prow deck straps', kit.plate)
    handles = dt.batch('Detail prow hatch handles', pipe_steel)
    # The nose strip: a raised plate down the centreline from WS 007's deck toward
    # the stem (stopped short of where the stem light lands, X ~212), a long hatch in it.
    top = dt.plate(plate, 205.4, 0, 0, 6.2, 3.2, .45, max_rise=2.5, label='nose strip')
    if top:
        p, u, v, n = top
        dt.batch('Detail prow nose hatch', kit.dark).box(p+n*.02, u, v, n, 2.2, .85, .08)
    # The ribbed grille between WS 007 and WS 008/009 (the sprite's pale shell): a
    # dark intake plate on the deck with seven bone louvre ribs along it.
    top = dt.plate(dark, 165.0, 0, 0, 8.0, 9.0, .25, max_rise=.6, label='prow grille')
    if top:
        p, u, v, n = top
        ribs = dt.batch('Detail prow grille ribs', kit.bone)
        for k in range(-3, 4):
            ribs.box(p+v*(k*1.25)+n*.3, u, v, n, 3.6, .3, .32)
        steel.box(p+u*4.2+n*.25, u, v, n, .3, 4.6, .3)     # its forward coaming
    for s in (1, -1):
        # Deck hatches on the WS 008/009 deck, a ready-use locker aft of them.
        h = dt.plate(steel, 165.5, s*10.5, 0, 2.6, 2.2, .3, label='prow deck hatch')
        if h:
            p, u, v, n = h
            handles.bar(p+n*.25-v*.6, p+n*.25+v*.6, .14, n=6)
        dt.plate(steel, 136.0, s*16.0, 0, 3.0, 2.0, .9, label='prow locker')
        # Vent slots on the shoulders' outer course (the sprite's dark notches at X ~188).
        for x in (189.0, 183.5):
            dt.plate(dark, x, s*19.5, 0, 3.6, 1.3, .3, label='shoulder vent')
    # Butt straps across the gun decks (plate seams), clear of the wells.
    for x, y0, y1 in ((197.0, -6.0, 6.0), (172.5, -6.0, 6.0), (145.0, -4.0, 4.0)):
        y = y0
        while y < y1-1e-6:
            dt.plate(plate, x, y+1.0, math.pi/2, 2.0, .6, .18, max_rise=.5, label='deck strap')
            y += 2.0
    # Bolt rows: both sides of each shoulder seam, and along the shoulders' inner
    # edge where they meet the gallery's foot.
    for side, s in (('port', 1), ('starboard', -1)):
        bolts = dt.batch(f'Detail {side} prow bolt rows', kit.orange)
        for xs in PROW_SEAMS:
            for dx in (-1.1, 1.1):
                x = xs+dx
                span = _edges(dt, x, ('prow shoulder',), s, 4.0, 45.0)
                if not span:
                    continue
                y0, y1 = span
                k = int((y1-y0-1.0)/1.7)
                dt.bolts(bolts, [(x, s*(y0+.9+1.7*i)) for i in range(k)])
        pts = []
        for x in [205-2.0*i for i in range(48)]:
            span = _edges(dt, x, ('prow shoulder',), s, 4.0, 45.0)
            if span and not any(abs(x-xs) < 2.2 for xs in PROW_SEAMS):
                pts.append((x, s*(span[0]+1.1)))
        dt.bolts(bolts, pts)


def _glacis(dt, kit, hatch_orange):
    """Bolt rows at the grey legs' and orange band's seams and inner edges, an
    inspection hatch on each grey block, a bolted access panel on each orange plate."""
    hatch = dt.batch('Detail glacis hatches', kit.steel)
    handles = dt.batch('Detail glacis hatch handles', kit.steel)
    panel = dt.batch('Detail glacis access panels', hatch_orange)
    for side, s in (('port', 1), ('starboard', -1)):
        grey = dt.batch(f'Detail {side} glacis grey bolt rows', kit.grey)
        orange = dt.batch(f'Detail {side} glacis bolt rows', kit.orange)
        # Grey leg: blocks between seams 108 | 92 | 76 | 60 | 44 | 24.
        for x0, x1 in ((106.0, 92.0), (92.0, 76.0), (76.0, 60.0), (60.0, 44.0), (44.0, 26.0)):
            xm = (x0+x1)/2
            span = _edges(dt, xm, ('glacis grey',), s, 5.0, 60.0)
            if not span:
                continue
            ym = (span[0]+span[1])/2
            h = dt.plate(hatch, xm, s*ym, 0, 3.0, 2.2, .3, max_rise=1.2, label='grey hatch')
            if h:
                p, u, v, n = h
                for k in (-1, 1):
                    handles.bar(p+n*.22+u*k*.9-v*.6, p+n*.22+u*k*.9+v*.6, .15, n=6)
            for xs in (x0, x1):
                if xs in (106.0, 26.0):
                    continue
                for dx in (-1.0, 1.0):
                    sp = _edges(dt, xs+dx, ('glacis grey',), s, 5.0, 60.0)
                    if sp:
                        k = int((sp[1]-sp[0]-1.0)/1.7)
                        dt.bolts(grey, [(xs+dx, s*(sp[0]+.9+1.7*i)) for i in range(k)])
        # Orange band: plates between seams 104 | 80 | 56 | 30 | 4.
        for x0, x1 in ((102.0, 80.0), (80.0, 56.0), (56.0, 30.0), (30.0, 6.0)):
            xm = (x0+x1)/2
            span = _edges(dt, xm, ('glacis band',), s, 10.0, 90.0)
            if span:
                ym = span[0]+.4*(span[1]-span[0])
                dt.plate(panel, xm, s*ym, 0, 4.4, 2.6, .22, max_rise=1.2, label='orange panel')
            if x0 != 102.0:
                for dx in (-1.0, 1.0):
                    sp = _edges(dt, x0+dx, ('glacis band',), s, 10.0, 90.0)
                    if sp:
                        k = int((sp[1]-sp[0]-1.4)/1.7)
                        dt.bolts(orange, [(x0+dx, s*(sp[0]+.9+1.7*i)) for i in range(k)])
        # The orange band's inner edge, where it steps down from the grey leg.
        pts = []
        for x in [100-2.0*i for i in range(48)]:
            sp = _edges(dt, x, ('glacis band',), s, 10.0, 90.0)
            if sp and not any(abs(x-xs) < 2.0 for xs in (80.0, 56.0, 30.0)):
                pts.append((x, s*(sp[0]+1.0)))
        dt.bolts(orange, pts)


def _waist(dt, kit, pipe_steel):
    """The waist valley's exposed machinery: a grated floor on frames, two pipe runs
    from the bay frame (X ~98, os_armour) aft to deck trunks, pumps and valve chests."""
    for side, s in (('port', 1), ('starboard', -1)):
        grate = dt.batch(f'Detail {side} waist grating', kit.dark)
        frames = dt.batch(f'Detail {side} waist frames', kit.steel)
        pipes = dt.batch(f'Detail {side} waist pipes', pipe_steel)
        fittings = dt.batch(f'Detail {side} waist pipe fittings', kit.steel)
        mach = dt.batch(f'Detail {side} waist machinery', kit.steel)
        wheels = dt.batch(f'Detail {side} waist valve wheels', kit.orange)
        good = []
        for i in range(46):
            x = 6.0+2.0*i
            sp = _edges(dt, x, ('waist',), s, 30.0, 100.0)
            if sp:
                good.append((x, sp))
        X = [x for x, _ in good]
        lo = _smooth([sp[0] for _, sp in good])
        hi = _smooth([sp[1] for _, sp in good])

        def at(x, f):
            """|Y| at fraction f across the valley at X x (held at its ends)."""
            x = min(max(x, X[0]), X[-1])
            for i in range(len(X)-1):
                if X[i] <= x <= X[i+1]:
                    t = (x-X[i])/(X[i+1]-X[i])
                    a = lo[i]+(lo[i+1]-lo[i])*t
                    b = hi[i]+(hi[i+1]-hi[i])*t
                    return a+(b-a)*f
            return None
        # Grated floor panels and the frames between them.
        x = 8.0
        while x < 92.0:
            a, b = at(x+3.0, 0.0), at(x+3.0, 1.0)
            if a is not None and b-a > 3.0:
                ang = s*math.atan2(at(x+6.0, 0.0)-at(x, 0.0), 6.0)
                dt.plate(grate, x+3.0, s*(a+(b-a)*.45), ang, 5.6, b-a-2.2, .12, max_rise=.3, label='grating')
                fa, fb = at(x, 0.0), at(x, 1.0)
                # A frame square to the valley's axis, carrying the pipes.
                dt.plate(frames, x, s*(fa+(fb-fa)*.45), ang+math.pi/2, (fb-fa-1.6)*math.cos(ang), .7, .5,
                         max_rise=.3, label='frame')
            x += 6.0
        # Two pipe runs along the valley's inboard side, out of the bay frame's after face;
        # each turns down at its after end into a trunk collar on the deck.
        for off, r, x_end in ((1.6, .5, 12.0), (2.9, .36, 16.0)):
            line = [(x, s*(at(x, 0.0)+off)) for x in (96.6, 90, 80, 70, 60, 50, 40, 30, 20, x_end)]
            c = dt.run(pipes, fittings, line, r, lift=.5, step=2.0, saddle_every=3)
            if c:
                end = c[-1]
                d = (c[-1]-c[-2]).normalized()
                z0 = dt.seat.top(end.x, end.y)[0].z
                elbow = end+d*r
                pipes.bar(end, elbow+d*.1, r)
                pipes.bar(elbow+Vector((0, 0, r*.2)), Vector((elbow.x, elbow.y, z0-.1)), r)
                fittings.bar(Vector((elbow.x, elbow.y, z0-.1)), Vector((elbow.x, elbow.y, z0+.45)), r*1.6)
        # Pumps and valve chests outboard of the runs, each branched into the main run.
        for k, x in enumerate((88.0, 79.0, 70.0, 61.0, 52.0, 43.0, 34.0, 25.0)):
            a, b = at(x, 0.0), at(x, 1.0)
            y = a+max(4.6, .62*(b-a))
            if y+1.2 > b:
                continue
            main_y = s*(a+1.6)
            if k % 2 == 0:
                h = dt.plate(mach, x, s*y, 0, 3.6, 2.0, .35, max_rise=.3, label='pump bed')
                if h:
                    p, u, v, n = h
                    pipes.bar(p+n*.75-u*1.5, p+n*.75+u*1.2, .7, n=10)          # motor drum
                    fittings.bar(p+n*.75+u*1.2, p+n*.75+u*1.5, .5, n=10)       # pump head
                    pipes.bar(p+n*.75-v*s*.6, Vector((x, main_y, p.z+.75)), .25, n=6)
            else:
                h = dt.plate(mach, x, s*y, 0, 2.2, 1.8, 1.1, max_rise=.3, label='valve chest')
                if h:
                    p, u, v, n = h
                    wheels.bar(p+n*.05, p+n*.32, .65, n=10)                    # handwheel
                    pipes.bar(p-n*.5-v*s*.9, Vector((x, main_y, p.z-.5)), .25, n=6)


def _core(dt, kit, pipe_steel):
    """Conduits in the gutter between the spine and the grey leg, from the gun
    gallery aft; the conduit ring round the citadel linking the turret wells; vent
    cupolas, mushroom ventilators, louvres and personnel hatches on the terrace."""
    steel = dt.batch('Detail core fittings', kit.steel)
    dark = dt.batch('Detail core vents', kit.dark)
    lids = dt.batch('Detail core hatch lids', kit.plate)
    for side, s in (('port', 1), ('starboard', -1)):
        conduits = dt.batch(f'Detail {side} core conduits', pipe_steel)
        clamps = dt.batch(f'Detail {side} core conduit clamps', kit.steel)
        # Gutter conduits: out of the gallery's after face (X ~90), aft along the
        # gutter beside the spine to a junction box at its after end (X ~58).
        for dy, r in ((13.0, .55), (14.6, .4)):
            dt.run(conduits, clamps, [(89.0, s*dy), (63.8, s*dy)], r, lift=.15, step=2.5, saddle_every=3)
        dt.plate(steel, 62.5, s*14.0, 0, 2.4, 4.0, 1.1, max_rise=.6, label='gutter junction box')
        # The citadel conduit ring: a pair round the drum from a junction box aft of
        # WS 012/013 to one forward of WS 014/015, passing inboard of WS 022/023.
        for rad, r in ((27.6, .5), (28.8, .38)):
            pts = [_polar(rad, s*b) for b in range(66, 141, 6)]
            dt.run(conduits, clamps, pts, r, lift=.15, step=2.0, saddle_every=4)
        for b, rad, nm in ((61.0, 28.2, 'ring junction fwd'), (137.0, 28.4, 'ring junction aft')):
            x, y = _polar(rad, s*b)
            dt.plate(steel, x, y, math.radians(s*b)+math.pi/2, 2.8, 2.6, 1.2, max_rise=.6, label=nm)
        # A vent cupola (the sprite's pale discs at X ~9): round trunk, dark louvred cap.
        x, y = 5.0, s*24.5
        hit = dt.seat.top(x, y)
        if hit and dt.keep.ok([(x+dx, y+dy) for dx in (-2, 2) for dy in (-2, 2)], hit[0].z+1.4):
            z = hit[0].z
            steel.bar(Vector((x, y, z-.2)), Vector((x, y, z+.9)), 2.0, n=12)
            dark.bar(Vector((x, y, z+.9)), Vector((x, y, z+1.2)), 1.6, n=12)
            for k in (-1, 0, 1):
                steel.box(Vector((x, y+k*.9, z+1.25)), Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1)),
                          1.4, .14, .12)
        # Mushroom ventilators on the terrace forward of WS 022/023.
        for x, y in ((38.0, 31.0), (35.0, 33.5), (41.0, 33.5)):
            hit = dt.seat.top(x, s*y)
            if hit and dt.keep.ok([(x, s*y)], hit[0].z+1.4) and hit[1].z > .97:
                z = hit[0].z
                steel.bar(Vector((x, s*y, z-.1)), Vector((x, s*y, z+1.0)), .45, n=10)
                steel.bar(Vector((x, s*y, z+.95)), Vector((x, s*y, z+1.35)), .85, n=10)
        # Louvred vents round the citadel's foot (the sprite's ring of dark slots).
        for b, rad in ((128.0, 31.5),):
            x, y = _polar(rad, s*b)
            h = dt.plate(dark, x, y, math.radians(s*b), 1.8, 3.2, .3, max_rise=.4, label='terrace louvre')
            if h:
                p, u, v, n = h
                for k in (-1, 0, 1):
                    steel.box(p+v*k*.9+n*.08, u, v, n, .8, .14, .14)
        # Personnel hatches on the terrace (a 1.3 x 2.4 m door in its coaming).
        for x, y in ((31.0, 37.0),):
            h = dt.plate(steel, x, s*y, 0, 2.9, 1.9, .35, max_rise=.4, label='personnel hatch')
            if h:
                p, u, v, n = h
                lids.box(p+n*.1, u, v, n, 1.25, .78, .14)
                clamps.bar(p+n*.42-u*.5, p+n*.42+u*.5, .14, n=6)
        # Ready-use lockers on the terrace between the citadel's turret pairs.
        for x, y in ((26.5, 39.0), (22.5, 39.0)):
            h = dt.plate(steel, x, s*y, 0, 3.2, 2.2, 1.1, max_rise=.5, label='terrace locker')
            if h:
                p, u, v, n = h
                clamps.bar(p+n*.05-u*1.0+v*s*1.15, p+n*.05+u*1.0+v*s*1.15, .14, n=6)


def _citadel(dt, kit, pipe_steel):
    """Blast-shutter leaves at the glazing's ends, ladders up the cheek slabs to the
    brow walkway, comm boxes and whips on the armour tier, the crown hatch rail."""
    shut = dt.batch('Detail citadel blast shutters', kit.bone)
    hinge = dt.batch('Detail citadel shutter hinges', kit.steel)
    rails = dt.batch('Detail citadel ladders and rails', pipe_steel)
    boxes = dt.batch('Detail citadel comm boxes', kit.steel)
    riser = CIT_A-2.8
    h0, h1 = 50.0, 52.6
    for s in (1, -1):
        # A blast-shutter leaf at each end of the glazing, swung open on its hinge at the
        # end mullion to stand against the cheek slab's forward end (bearing 80): the
        # glazing's armoured deadlight, 2 x 2.9, a hand thick, ready to be swung shut.
        bd = s*78.2
        b = math.radians(bd)
        out = Vector((math.cos(b), math.sin(b), 0))
        tang = Vector((-math.sin(b), math.cos(b), 0))
        c = _cit(riser+1.15, bd, (h0+h1)/2)
        shut.box(c, out, tang, Vector((0, 0, 1)), 1.05, .3, (h1-h0)/2+.2)
        hp = _cit(riser+.1, s*76.6, 0)
        for z in (h0+.25, h1-.25):
            hinge.bar(Vector((hp.x, hp.y, z-.3)), Vector((hp.x, hp.y, z+.3)), .26, n=8)
        # Cheek ladder at bearing 110: up the cheek's foot wall and its battered face to
        # the brow walkway (cheek: apothem +3.6 at 35.5..40.4, rolling in to -1.0 at 53.3).
        bb = s*110.0
        bdir = math.radians(bb)
        side = Vector((-math.sin(bdir), math.cos(bdir), 0))
        o = Vector((math.cos(bdir), math.sin(bdir), 0))
        path = [(CIT_A+3.6, 36.0), (CIT_A+3.6, 40.4), (CIT_A-1.0, 53.3)]
        top = None
        for (a0, z0), (a1, z1) in zip(path, path[1:]):
            p0 = _cit(a0, bb, z0)+o*.45
            p1 = _cit(a1, bb, z1)+o*.45
            for k in (-1, 1):
                rails.bar(p0+side*k*.6, p1+side*k*.6, .14, n=6)
            n = max(1, int((p1-p0).length/1.0))
            for i in range(1, n):
                q = p0+(p1-p0)*(i/n)
                rails.bar(q-side*.6, q+side*.6, .12, n=6)
            top = p1
        for k in (-1, 1):
            rails.bar(top+side*k*.6, top+side*k*.6+Vector((0, 0, 1.2))-o*.3, .14, n=6)
        # Comm boxes on the first armour tier's step (z 57), aft quarters, each with a whip.
        for bd in (128.0, 152.0):
            p = _cit(CIT_A-5.1, s*bd, 57.0)
            bdir = math.radians(s*bd)
            o2 = Vector((math.cos(bdir), math.sin(bdir), 0))
            t2 = Vector((-math.sin(bdir), math.cos(bdir), 0))
            boxes.box(p+Vector((0, 0, .55)), t2, o2, Vector((0, 0, 1)), 1.0, .55, .65)
            rails.bar(p+Vector((0, 0, 1.1))+t2*.6, p+Vector((0, 0, 3.9))+t2*.6, .16, n=6)
    # The crown hatch rail: stanchions and a top rail round the hatch's after side.
    hit = dt.seat.top(27.5, 0.0)
    if hit:
        z = hit[0].z
        tops = []
        for a in range(60, 301, 40):
            x, y = 27.5+2.7*math.cos(math.radians(a)), 2.7*math.sin(math.radians(a))
            h = dt.seat.top(x, y)
            if h is None:
                continue
            tops.append(Vector((x, y, z+1.35)))
            rails.bar(Vector((x, y, h[0].z-.1)), Vector((x, y, z+1.4)), .14, n=6)
        for a, b in zip(tops, tops[1:]):
            rails.bar(a, b, .13, n=6)


def build(kit):
    hatch_orange = material('Detail hatch orange', (.42, .10, .028), .25, .6)
    pipe_steel = material('Detail pipe steel', (.17, .16, .155), .75, .4)
    dt = _Detail(kit)
    _prow(dt, kit, pipe_steel)
    _glacis(dt, kit, hatch_orange)
    _waist(dt, kit, pipe_steel)
    _core(dt, kit, pipe_steel)
    _citadel(dt, kit, pipe_steel)
    dt.finish()
