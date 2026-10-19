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

Detail pass 2 (owner: "bigger, denser machinery and plating breakup so it reads at
1.5 km") adds a big layer first, built to read at about 0.8 px a unit: armour plates
of 8-25 units in alternating values split by near-black seams, stepped by doublers
and tilted so neighbours catch the sun differently (glacis grey blocks, segmented
orange band and shoulders, ringed turret aprons, rectangle-filled gun decks, core
sector rings and shoulder walls, the stem chamfer, dome and drum plates); open
machinery trenches in the waist valleys (dark floor, light coamings, tank pairs,
heat exchangers, pump sets and drums, a pipe bundle); and a pipe bundle and
radiator banks in the spine gutters. The pass-1 parts are then seated on top.

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
import os as _os
DEBUG = bool(_os.environ.get("OS_TOP_DEBUG"))

# The command citadel as os_bridge builds it (final heights): twelve flats about
# CIT_C, the drum foot's apothem CIT_A.
CIT_C = (24.0, 0.0)
CIT_A = 21.7
CIT_SIDES = 12


# --- seating ----------------------------------------------------------------------

class _Seat:
    """Topmost Hull/Armour/Bridge surface under a point (plus any `extra` objects by
    name, for the close-up layer seated on the big layer), by BVH ray casts against the
    renderer's own triangulation (the lofts' quads are twisted; a BVH of the raw quads
    is up to half a unit off the drawn surface)."""

    def __init__(self, extra=()):
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        self.trees = []
        extra = set(extra)
        for ob in bpy.context.scene.objects:
            if ob.type != 'MESH' or (ob.name.split(' ')[0] not in SEAT_FAMILIES and ob.name not in extra):
                continue
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            me.calc_loop_triangles()
            pts = [ev.matrix_world @ v.co for v in me.vertices]
            if pts:
                lo = (min(p.x for p in pts), min(p.y for p in pts))
                hi = (max(p.x for p in pts), max(p.y for p in pts))
                self.trees.append((ob.name, BVHTree.FromPolygons(pts, [tuple(t.vertices) for t in me.loop_triangles]),
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

    def cast(self, o, d):
        """(distance, point, normal facing the ray, name) of the first surface along o + t d."""
        if abs(d.z) > .999:
            h = self.top(o.x, o.y, o.z)
            return None if h is None else (o.z-h[0].z, h[0], h[1], h[2])
        best = None
        for name, t, lo, hi in self.trees:
            p, n, _, dist = t.ray_cast(o, d)
            if p is not None and (best is None or dist < best[0]):
                best = (dist, p, -n if n.dot(d) > 0 else n, name)
        return best


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

    def ceiling(self, x, y):
        """Lowest trained part over the 5x5 cells round (x, y), or None inside a ring."""
        for cx, cy, rr in self.rings:
            if (x-cx)**2+(y-cy)**2 < rr*rr:
                return None
        i, j = int(math.floor(x/self.CELL)), int(math.floor(y/self.CELL))
        return min(self.low.get((i+di, j+dj), 1e9) for di in (-2, -1, 0, 1, 2) for dj in (-2, -1, 0, 1, 2))

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
            # big-layer plates take a chamfer on their edges, so they catch the sun there
            bevel = .22 if (' panels ' in name and 'doubler' not in name) else 0
            return kit.mesh(name, self.v, self.f, mat, bevel)


class _Detail:
    def __init__(self, kit):
        self.kit = kit
        self.seat = _Seat()
        self.keep = _Keep()
        self.batches = {}
        self.dropped = []
        self.faces = 0
        self.mats = {}
        self.occ = set()
        self.jump = 1.2

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

    # --- the big layer: plates that follow the surface ---------------------------

    def sheet(self, P, nu, nv, ht, hb, keys, label, jump=None):
        """A plate that follows the surface: P(a, b) (a, b in 0..1) gives a cast origin and
        direction; each grid point is cast onto the seat, and the plate's top and bottom
        stand ht(a, b) and hb(a, b) out of the surface against the cast. Refused (None)
        where a cast misses or meets an object whose name has none of `keys`, where the
        cast distances kink (a step under the plate) or where it meets a turret.
        Returns (verts, faces) of a closed solid."""
        jump = self.jump if jump is None else jump
        grid = []
        for i in range(nu+1):
            row = []
            for j in range(nv+1):
                a, b = i/nu, j/nv
                o, d = P(a, b)
                hit = self.seat.cast(o, d)
                if hit is None or (keys and not any(k in hit[3] for k in keys)):
                    self.dropped.append((label, tuple(round(c, 1) for c in o[:2]), 'miss '+(hit[3] if hit else 'none')))
                    return None
                row.append((hit[0], hit[1], d, a, b))
            grid.append(row)
        for i in range(nu+1):
            for j in range(nv+1):
                for di, dj in ((1, 0), (0, 1)):
                    if 0 <= i-di and i+di <= nu and 0 <= j-dj and j+dj <= nv and \
                            abs(grid[i-di][j-dj][0]-2*grid[i][j][0]+grid[i+di][j+dj][0]) > jump:
                        self.dropped.append((label, tuple(round(c, 1) for c in grid[i][j][1][:2]), 'step'))
                        return None
        tops = [[q-d*ht(a, b) for _, q, d, a, b in row] for row in grid]
        bots = [[q-d*hb(a, b) for _, q, d, a, b in row] for row in grid]
        if not self.keep.ok([(q.x, q.y) for row in tops for q in row], max(q.z for row in tops for q in row)):
            self.dropped.append((label, tuple(round(c, 1) for c in grid[0][0][1][:2]), 'turret'))
            return None
        m = (nu+1)*(nv+1)
        verts = [tuple(q) for row in tops for q in row]+[tuple(q) for row in bots for q in row]
        idx = lambda i, j: i*(nv+1)+j
        quads = [(idx(i, j), idx(i+1, j), idx(i+1, j+1), idx(i, j+1)) for i in range(nu) for j in range(nv)]
        faces = quads+[tuple(reversed([v+m for v in q])) for q in quads]
        ring = [(i, 0) for i in range(nu)]+[(nu, j) for j in range(nv)]+[(i, nv) for i in range(nu, 0, -1)] + \
               [(0, j) for j in range(nv, 0, -1)]
        for k in range(len(ring)):
            a, b = idx(*ring[k]), idx(*ring[(k+1) % len(ring)])
            faces.append((b, a, a+m, b+m))
        return verts, faces

    @staticmethod
    def emit(batch, data):
        verts, faces = data
        b = len(batch.v)
        batch.v += verts
        batch.f += [tuple(b+i for i in f) for f in faces]

    SHRINKS = ((0, 1, 0, 1), (0, 1, .2, 1), (0, 1, 0, .8), (.2, 1, 0, 1), (0, .8, 0, 1), (.12, .88, .12, .88),
               (0, 1, .4, 1), (0, 1, 0, .6), (.4, 1, 0, 1), (0, .6, 0, 1), (.2, .8, .25, .75))

    def panel(self, prefix, P, La, Lb, h, tone, keys, label, gap=.9, tilt=(0.0, 0.0), doubler=None,
              depth=0, seam=True, plan=True, grid=2.6, min_a=4.0, min_b=3.4):
        """One armour panel of the big layer on the plan cell P(a, b) -> (x, y) (La x Lb
        units): a near-black seam plate over the whole cell, the panel itself inset gap/2
        all round and standing h proud (tilted by `tilt` across a and b, so neighbouring
        panels catch the sun differently), and optionally a doubler plate (inset, dh,
        tone) on it. A cell that will not seat (an edge, a step, a turret) is shrunk from
        each side in turn, then halved across its longer side, so plates stay big."""
        cast = (lambda a, b: (Vector((*P(a, b), 160.0)), Vector((0, 0, -1)))) if plan else P
        n0 = len(self.dropped)

        def sub(a0, a1, b0, b1):
            return lambda a, b: cast(a0+(a1-a0)*a, b0+(b1-b0)*b)

        def H(A, B):
            return h          # parallel to the surface under it (tilt is no longer used)
        for a0, a1, b0, b1 in self.SHRINKS:
            la, lb = La*(a1-a0), Lb*(b1-b0)
            if la < min_a or lb < min_b:
                continue
            if self._panel_try(prefix, sub(a0, a1, b0, b1), la, lb, H, (a0, a1, b0, b1), tone, keys, label, gap,
                               doubler, seam, grid):
                del self.dropped[n0:]
                return True
        last = self.dropped[-1] if len(self.dropped) > n0 else (label, (), '?')
        del self.dropped[n0:]
        if depth < 2 and max(La, Lb) > 2*min(min_a, min_b)+1.0:
            if La >= Lb:
                halves = (sub(0, .5, 0, 1), La/2, Lb), (sub(.5, 1, 0, 1), La/2, Lb)
            else:
                halves = (sub(0, 1, 0, .5), La, Lb/2), (sub(0, 1, .5, 1), La, Lb/2)
            for Q, la, lb in halves:
                self.panel(prefix, Q, la, lb, h, tone, keys, label, gap, tilt, doubler if depth == 0 else None,
                           depth+1, seam, False, grid, min_a, min_b)
        elif h > .6 and 'turret' in last[2]:
            # under a turret's barrels: try once more as a low plate
            return self.panel(prefix, P, La, Lb, .5, tone, keys, label, gap, (0.0, 0.0), None, depth, seam, plan,
                              grid, min_a, min_b)
        else:
            self.dropped.append((label, last[1], 'panel '+last[2]))
        return False

    def _panel_try(self, prefix, cast, La, Lb, H0, rng, tone, keys, label, gap, doubler, seam, grid):
        A0, A1, B0, B1 = rng

        def H(a, b):
            return H0(A0+(A1-A0)*a, B0+(B1-B0)*b)

        def sub(a0, a1, b0, b1):
            return lambda a, b: cast(a0+(a1-a0)*a, b0+(b1-b0)*b)
        nu, nv = max(1, int(math.ceil(La/grid))), max(1, int(math.ceil(Lb/grid)))
        da, db = min(.3, gap/2/max(La, .1)), min(.3, gap/2/max(Lb, .1))
        # Occupancy: a cell may not overlap a plate already laid (it may touch one).
        ka, kb = max(2, int(La/.4)), max(2, int(Lb/.4))
        ea, eb = min(.45, .3/max(La, .1)), min(.45, .3/max(Lb, .1))
        vertical = abs(cast(.5, .5)[1].z) > .99      # plan occupancy is for plates cast from above
        foot = [cast(ea+(1-2*ea)*i/ka, eb+(1-2*eb)*j/kb)[0] for i in range(ka+1) for j in range(kb+1)]
        if vertical and any(self.taken(q) for q in foot):
            self.dropped.append((label, tuple(round(c, 1) for c in foot[0][:2]), 'occupied'))
            return False
        sm = None
        if seam:
            sm = self.sheet(cast, nu, nv, lambda a, b: .28, lambda a, b: -.35, keys, label)
            if sm is None:
                return False
        a0, a1, b0, b1 = da, 1-da, db, 1-db
        p = self.sheet(sub(a0, a1, b0, b1), nu, nv, lambda a, b: H(a0+(a1-a0)*a, b0+(b1-b0)*b),
                       lambda a, b: -.35, keys, label)
        if p is None:
            return False
        if sm:
            self.emit(self.batch(f'Detail {prefix} seams', self.mats['seam']), sm)
        self.emit(self.batch(f'Detail {prefix} panels {tone}', self.mats[tone]), p)
        for i in range(ka+1):
            for j in range(kb+1):
                if vertical:
                    self.occupy(cast(i/ka, j/kb)[0])
        if doubler:
            inset, dh, tone2 = doubler
            ea, eb = da+inset/max(La, .1), db+inset/max(Lb, .1)
            if ea < .4 and eb < .4:
                d = self.sheet(sub(ea, 1-ea, eb, 1-eb), max(1, nu-1), max(1, nv-1),
                               lambda a, b: H(ea+(1-2*ea)*a, eb+(1-2*eb)*b)+dh,
                               lambda a, b: H(ea+(1-2*ea)*a, eb+(1-2*eb)*b)-.2, None, label+' doubler', jump=1.5)
                if d:
                    self.emit(self.batch(f'Detail {prefix} panels {tone2}', self.mats[tone2]), d)
        return True

    OCC = .5

    def taken(self, q):
        return (int(math.floor(q.x/self.OCC)), int(math.floor(q.y/self.OCC))) in self.occ

    def occupy(self, q):
        """Mark the plan around q (cast origins carry the plan point for plan casts; for
        wall casts the origin's plan point is outside the wall, which is harmless)."""
        self.occ.add((int(math.floor(q.x/self.OCC)), int(math.floor(q.y/self.OCC))))

    def flush(self):
        """Build every batch gathered so far; returns the new objects' names."""
        names = []
        for name, (b, mat) in sorted(self.batches.items()):
            ob = b.build(self.kit, name, mat)
            if ob is not None:
                names.append(ob.name)
                self.faces += len(b.f)
        self.batches = {}
        return names

    def finish(self):
        self.flush()
        if DEBUG:
            for d in self.dropped:
                print('DETAIL TOP DROPPED', d)
        from collections import Counter
        print(f'DETAIL TOP: {self.faces} faces, {len(self.dropped)} parts dropped',
              dict(Counter(d[-1] for d in self.dropped)))


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


# --- the big layer (detail pass 2: plates and bays that read at 1.5 km) ---------------
#
# At 1.5 km a unit is about 0.8 px, so this layer works in plates of 8-25 units, seams
# about a unit wide set near-black, steps of 0.6-1.5 and value changes between
# neighbouring plates; machinery in fewer, bigger masses. The close-up layer (pass 1,
# the area functions further down) is seated on top of it afterwards.

def _field(dt, key, s, xs, y0, y1):
    """{x: (|Y| lo, |Y| hi)} of the field whose seat name contains `key`, sampled at xs."""
    out = {}
    for x in xs:
        sp = _edges(dt, x, (key,), s, y0, y1, step=.25)
        if sp and sp[1]-sp[0] > 1.5:
            out[x] = sp
    return out


def _interp(tab, x):
    ks = sorted(tab)
    if not ks or x < ks[0]-1.0 or x > ks[-1]+1.0:
        return None
    x = min(max(x, ks[0]), ks[-1])
    for a, b in zip(ks, ks[1:]):
        if a <= x <= b:
            t = (x-a)/(b-a) if b > a else 0.0
            return tuple(p+(q-p)*t for p, q in zip(tab[a], tab[b]))
    return tab[ks[0]]


def _strip(dt, prefix, key, s, seams, courses, y0, y1, edge=.45, label='strip'):
    """Panels on a strip-shaped field between X `seams` (fore to aft): `courses(k)` gives,
    for span k, a list of (f0, f1, h, tone, tilt, doubler) across the field's width."""
    lo_x, hi_x = min(seams), max(seams)
    tab = _field(dt, key, s, [lo_x+i*1.0 for i in range(int(hi_x-lo_x)+1)], y0, y1)
    ks = sorted(tab)        # smoothed, so the seams run straight rather than following 0.25-unit steps
    lo, hi = _smooth([tab[x][0] for x in ks], 3), _smooth([tab[x][1] for x in ks], 3)
    tab = {x: (a, b) for x, a, b in zip(ks, lo, hi)}
    for k, (xa, xb) in enumerate(zip(seams, seams[1:])):
        ea, eb = _interp(tab, xa-.6), _interp(tab, xb+.6)
        if ea is None or eb is None:
            continue
        for f0, f1, h, tone, tilt, dbl in courses(k):
            def P(a, b, xa=xa, xb=xb, f0=f0, f1=f1):
                x = xa+(xb-xa)*a
                e = _interp(tab, x) or (ea if a < .5 else eb)
                lo, hi = e[0]+edge, e[1]-edge
                return x, s*(lo+(hi-lo)*(f0+(f1-f0)*b))
            w = ((ea[1]-ea[0])+(eb[1]-eb[0]))/2-2*edge
            dt.panel(prefix, P, abs(xa-xb), w*(f1-f0), h, tone, (key,), label, tilt=tilt, doubler=dbl,
                     gap=1.4 if 'orange' in tone else .9)


def _big_glacis(dt):
    """The grey A-legs as stepped blocks of alternating value, the orange band as a
    course of long plates in two tones split by wide dark seams (the sprite's rhythm)."""
    for side, s in (('port', 1), ('starboard', -1)):
        def grey(k):
            if k % 2 == 0:   # one big block, stepped up by a doubler of the other value
                tone, other = ('light grey', 'blue steel') if k % 4 == 0 else ('blue steel', 'light grey')
                return [(0, 1, 1.0, tone, (0, .5*s), (1.7, .7, other))]
            return [(0, .52, .8, 'grey', (.3, 0), None), (.52, 1, 1.2, 'light grey', (0, -.4*s), None)]
        _strip(dt, f'{side} glacis grey', f'{side} glacis grey', s,
               [101.0, 92.0, 84.0, 76.0, 68.0, 60.0, 52.0, 44.0, 36.0, 26.0], grey, 5.0, 62.0, label='grey block')

        def band(k):
            # one long plate a span, the base orange, every third the dirtier one; the
            # rhythm is the wide dark joints between them, as on the sprite
            tone = 'dirty orange' if k % 3 == 2 else 'orange'
            return [(0, 1, 1.0, tone, (0, 0), None)]
        _strip(dt, f'{side} glacis band', f'{side} glacis band', s,
               [103.0, 92.0, 80.0, 68.0, 56.0, 43.0, 30.0, 17.0, 5.0], band, 10.0, 92.0, label='band plate')


def _big_shoulders(dt):
    """The prow's orange shoulders as two courses of plates, the courses offset."""
    for side, s in (('port', 1), ('starboard', -1)):
        def course(k):
            tone = 'dirty orange' if k % 3 == 2 else 'orange'
            return [(0, 1, 1.0, tone, (0, 0), None)]
        _strip(dt, f'{side} prow shoulder', f'{side} prow shoulder', s,
               [203.0, 191.0, 175.0, 157.0, 140.0, 125.0, 109.0], course, 4.0, 45.0, label='shoulder plate')


BROWNS = ('brown', 'dark brown', 'brown', 'bone brown', 'dark brown', 'brown')


APRONS = {'WS 007': 'Hull prow', 'WS 008': 'Hull prow', 'WS 009': 'Hull prow', 'WS 019': 'Hull prow',
          'WS 012': 'Hull core', 'WS 013': 'Hull core', 'WS 014': 'Hull core', 'WS 015': 'Hull core',
          'WS 021': 'Hull core', 'WS 022': 'Hull core', 'WS 023': 'Hull core', 'WS 024': 'Hull core'}


def _aprons(dt):
    """A ring of plates round each deck turret's well, dark and light by turns: the
    sprite's ringed gun wells, read as one shape at range."""
    for sid, (x, y), angle, arc, size, _, _ in os_turrets.SLOTS:
        if sid not in APRONS:
            continue
        R = os_turrets.RING[size]
        r0 = KEEP_RING*R+.55
        r1 = r0+{'SMALL': 3.6, 'MEDIUM': 4.6, 'LARGE': 5.6}[size]
        n = {'SMALL': 8, 'MEDIUM': 10, 'LARGE': 14}[size]
        for k in range(n):
            b0, b1 = 360.0*k/n, 360.0*(k+1)/n
            def P(a, c, b0=b0, b1=b1):
                t = math.radians(b0+(b1-b0)*a)
                r = r0+(r1-r0)*c
                return x+r*math.cos(t), y+r*math.sin(t)
            tone = ('blue steel', 'dark brown')[k % 2]
            dt.panel(f'{sid.lower().replace(" ", "")} apron', P, math.radians(360.0/n)*(r0+r1)/2, r1-r0,
                     (.7, .9)[k % 2], tone, (APRONS[sid],), 'apron', gap=.8, depth=2, min_a=2.5, min_b=2.5)


def _fill(dt, prefix, key, s, x0, x1, y0, y1, max_l=14, max_w=10, min_area=14, res=1.0, tones=BROWNS,
          label='fill'):
    """Fill what is left of a field with the biggest plates that fit: a plan mask of the
    field (its own surface, clear of turrets and of plates already laid), then
    repeatedly the largest rectangle (at most max_l along X by max_w across) over
    ground without a step, laid as a panel."""
    ni, nj = int((x1-x0)/res), int((y1-y0)/res)
    Z = {}
    stats = {'taken': 0, 'turret': 0, 'placed': 0, 'failed': 0}
    for i in range(ni):
        for j in range(nj):
            x, y = x0+res*(i+.5), s*(y0+res*(j+.5))
            hit = dt.seat.top(x, y)
            if hit is None or key not in hit[2]:
                continue
            q = Vector((x, y, 0))
            if dt.taken(q):
                stats['taken'] += 1
                continue
            if not dt.keep.ok([(x, y)], hit[0].z+.45):
                stats['turret'] += 1
                c = dt.keep.ceiling(x, y)
                stats.setdefault('clear', []).append(None if c is None else round(c-hit[0].z, 1))
                continue
            Z[i, j] = hit[0].z

    def link(a, b):
        return a in Z and b in Z and abs(Z[a]-Z[b]) < 1.8
    k = 0
    dt.jump = 2.0       # curved flanks: the mask's links already refuse real steps
    if DEBUG:
        nr = sum(1 for (i, j) in Z if link((i, j), (i+1, j)))
        nd = sum(1 for (i, j) in Z if link((i, j), (i, j+1)))
        zs = sorted(abs(Z[i, j]-Z[i+1, j]) for (i, j) in Z if (i+1, j) in Z)
        print('DETAIL TOP FILL LINKS', label, len(Z), nr, nd, zs[len(zs)//2] if zs else None, zs[-5:])

    for _ in range(80):
        best = None
        for (i, j) in Z:
            # grow rows upward (j), keeping the common run along i
            w = max_l
            for h in range(max_w):
                jj = j+h
                if (i, jj) not in Z or (h and not link((i, jj-1), (i, jj))):
                    break
                run = 1
                while run < w and link((i+run-1, jj), (i+run, jj)) and (h == 0 or link((i+run, jj-1), (i+run, jj))):
                    run += 1
                w = min(w, run)
                area = w*(h+1)
                if area >= min_area and min(w, h+1) >= 3 and (best is None or area > best[0]):
                    best = (area, i, j, w, h+1)
        if best is None:
            break
        _, i, j, w, h = best
        xa, xb = x0+res*i+.25, x0+res*(i+w)-.25
        ya, yb = y0+res*j+.25, y0+res*(j+h)-.25
        def P(a, b, xa=xa, xb=xb, ya=ya, yb=yb):
            return xa+(xb-xa)*a, s*(ya+(yb-ya)*b)
        tone = tones[k % len(tones)]
        hh = (.9, 1.2, .8, 1.1)[k % 4]
        dbl = (1.6, .6, tones[(k+3) % len(tones)]) if (w*h >= 60 and k % 3 == 0) else None
        ok = dt.panel(prefix, P, xb-xa, yb-ya, hh, tone, (key,), label, tilt=((.5, -.4, 0)[k % 3], (0, .4, -.4)[k % 3]),
                      doubler=dbl, depth=2, min_a=2.3, min_b=2.3)
        stats['placed' if ok else 'failed'] += 1
        for ii in range(i, i+w):
            for jj in range(j, j+h):
                Z.pop((ii, jj), None)
        k += 1
    dt.jump = 1.2
    if DEBUG:
        cl = stats.pop('clear', [])
        print('DETAIL TOP FILL', label, s, len(Z), stats, 'ring', sum(1 for c in cl if c is None),
              sorted(c for c in cl if c is not None)[::25])


def _wall_fill(dt, prefix, keys, s, x0, x1, z0, z1, max_l=12, max_w=8, min_area=12, label='wall'):
    """Plates on the steep faces the plan fill cannot reach (the core's shoulders seen
    abeam): a mask cast inboard from outside the beam on an X-Z grid, kept to steep
    surface of the field not already plated from above, filled with the biggest
    rectangles that fit, each plate standing out of the face against the cast."""
    Z = {}
    ni, nj = int(x1-x0), int(z1-z0)
    d = Vector((0, -s, 0))
    for i in range(ni):
        for j in range(nj):
            x, z = x0+i+.5, z0+j+.5
            hit = dt.seat.cast(Vector((x, s*130.0, z)), d)
            if hit is None or not any(k in hit[3] for k in keys) or abs(hit[2].z) > .72:
                continue
            p = hit[1]
            if dt.taken(p) or not dt.keep.ok([(p.x, p.y)], p.z+1.2):
                continue
            Z[i, j] = s*p.y

    def link(a, b):
        return a in Z and b in Z and abs(Z[a]-Z[b]) < 1.8
    k = 0
    dt.jump = 2.0
    for _ in range(60):
        best = None
        for (i, j) in Z:
            w = max_l
            for h in range(max_w):
                jj = j+h
                if (i, jj) not in Z or (h and not link((i, jj-1), (i, jj))):
                    break
                run = 1
                while run < w and link((i+run-1, jj), (i+run, jj)) and (h == 0 or link((i+run, jj-1), (i+run, jj))):
                    run += 1
                w = min(w, run)
                area = w*(h+1)
                if area >= min_area and min(w, h+1) >= 3 and (best is None or area > best[0]):
                    best = (area, i, j, w, h+1)
        if best is None:
            break
        _, i, j, w, h = best
        xa, xb = x0+i+.25, x0+i+w-.25
        za, zb = z0+j+.25, z0+j+h-.25
        def P(a, b, xa=xa, xb=xb, za=za, zb=zb):
            return Vector((xa+(xb-xa)*a, s*130.0, za+(zb-za)*b)), d
        tone = BROWNS[(k+1) % len(BROWNS)]
        dt.panel(prefix, P, xb-xa, zb-za, (.9, 1.2, .8)[k % 3], tone, keys, label, plan=False, depth=2,
                 tilt=((.4, -.4, 0)[k % 3], 0), min_a=2.3, min_b=2.3,
                 doubler=(1.4, .5, BROWNS[(k+4) % len(BROWNS)]) if w*h >= 40 and k % 2 == 0 else None)
        for ii in range(i, i+w):
            for jj in range(j, j+h):
                Z.pop((ii, jj), None)
        k += 1
    dt.jump = 1.2
    if DEBUG:
        print('DETAIL TOP WALL', label, s, k)


def _nose(dt):
    """The stem's top chamfer ahead of WS 007: a plate a side either side of the stem
    light's centreline strip, in two values, and one aft of the light."""
    dt.jump = 2.2
    for side, s in (('port', 1), ('starboard', -1)):
        for xa, xb, ya, yb, tone, hh in ((222.0, 214.5, 2.4, 10.5, 'dark brown', .7),
                                         (214.5, 206.5, 2.4, 13.5, 'bone brown', .9),
                                         (206.5, 198.5, 2.4, 12.0, 'dark brown', .9)):
            def P(a, b, xa=xa, xb=xb, ya=ya, yb=yb):
                return xa+(xb-xa)*a, s*(ya+(yb-ya)*b)
            dt.panel(f'{side} prow nose', P, xa-xb, yb-ya, hh, tone, ('Hull prow',), 'nose plate',
                     tilt=(0, .4), depth=1, min_a=3.0, min_b=2.4)

    def P(a, b):
        return 209.0-9.0*a, -2.0+4.0*b
    dt.panel('prow nose', P, 9.0, 4.0, .7, 'blue steel', ('Hull prow',), 'nose strip plate', depth=2,
             min_a=3.0, min_b=2.4)
    dt.jump = 1.2


def _big_prow(dt):
    """The prow's gun decks: the biggest brown plates of three values that fit between
    the wells, the lip and the shoulders."""
    for side, s in (('port', 1), ('starboard', -1)):
        _fill(dt, f'{side} prow gallery deck', 'Hull prow', s, 92.0, 209.5, .45, 27.0, label='prow deck')


def _big_core(dt):
    """The core terrace round the citadel in rings of sector plates (the sprite's
    radial plating), and the core's after flanks on a plan grid."""
    rings = ((23.5, 30.5), (30.5, 38.5))
    for side, s in (('port', 1), ('starboard', -1)):
        for r, (r0, r1) in enumerate(rings):
            step = (24.0, 19.0, 15.0)[r]
            b = 30.0
            k = 0
            while b < 180.0-1e-6:
                b1 = min(180.0, b+step)
                def P(a, c, b0=b, b1=b1, r0=r0, r1=r1):
                    return _polar(r0+(r1-r0)*c, s*(b0+(b1-b0)*a))
                L = math.radians(b1-b)*(r0+r1)/2
                tone = BROWNS[(k+2*r) % len(BROWNS)]
                h = (.9, 1.2, .8)[(k+r) % 3]
                dbl = (1.5, .6, 'bone brown' if tone != 'bone brown' else 'dark brown') if (k+r) % 4 == 2 else None
                dt.panel(f'{side} core deck', P, L, r1-r0, h, tone, ('Hull core',), 'core sector',
                         tilt=(0, (.5, -.4)[k % 2]), doubler=dbl)
                b, k = b1, k+1
        # The rest of the core deck: the outer terrace and the after flanks.
        _fill(dt, f'{side} core deck', 'Hull core', s, -16.0, 92.0, .45, 90.0, label='core fill')


BUNDLE_END = {}


TRENCH_FORE = 84.0   # forward end of the waist trench


def _trench(dt, kit):
    """The waist valleys as open machinery trenches: a near-black floor between light
    coamings, and in it a few big masses - tank pairs, heat exchangers with bone fins,
    pump sets and upright drums - with a pipe bundle along the inboard side."""
    M = dt.mats
    for side, s in (('port', 1), ('starboard', -1)):
        floor = dt.batch(f'Detail {side} waist trench floor', M['seam'])
        rim = dt.batch(f'Detail {side} waist trench coaming', M['mach light'])
        body = dt.batch(f'Detail {side} waist machinery bodies', M['blue steel'])
        light = dt.batch(f'Detail {side} waist machinery', M['mach light'])
        caps = dt.batch(f'Detail {side} waist machinery caps', dt.kit.bone)
        pipes = dt.batch(f'Detail {side} waist pipe bundle', M['pipe'])
        clamps = dt.batch(f'Detail {side} waist pipe clamps', dt.kit.steel)
        wheels = dt.batch(f'Detail {side} waist valve wheels', dt.kit.orange)
        tab = _field(dt, f'{side} waist', s, [4.0+i for i in range(96)], 30.0, 100.0)
        if not tab:
            continue
        ks = sorted(tab)
        lo = dict(zip(ks, _smooth([tab[x][0] for x in ks], 3)))
        hi = dict(zip(ks, _smooth([tab[x][1] for x in ks], 3)))
        both = {x: (lo[x], hi[x]) for x in ks}

        def at(x, f):
            e = _interp(both, x)
            return None if e is None else e[0]+(e[1]-e[0])*f

        def frame(x, y):
            """Seat frame on the floor at (x, |y|): point, u along the valley (aft), v
            outboard (port +Y), n up."""
            hit = dt.seat.top(x, s*y)
            if hit is None:
                return None
            ya, yb = at(x+2, .5), at(x-2, .5)
            if ya is None or yb is None:
                return None
            u = Vector((-4.0, s*(yb-ya), 0)).normalized()
            v = Vector((-u.y, u.x, 0))
            if v.y*s < 0:
                v = -v
            return hit[0], u, v, Vector((0, 0, 1))
        # Floor and coamings, in lengths of 6 along the valley. The trench stops at
        # TRENCH_FORE, short of the arm's own machinery bay (os_detail_arms, X 85.5 on).
        x = TRENCH_FORE
        while x > ks[0]+1.0:
            xb = max(ks[0]+.5, x-6.0)

            def F(f0, f1, xa=x, xb=xb):
                def P(a, b):
                    xx = xa+(xb-xa)*a
                    e = _interp(both, xx)
                    return Vector((xx, s*(e[0]+(e[1]-e[0])*(f0+(f1-f0)*b)), 160.0)), Vector((0, 0, -1))
                return P
            e = _interp(both, (x+xb)/2)
            if e and e[1]-e[0] > 2.5:
                w = e[1]-e[0]
                nu = max(1, int(math.ceil((x-xb)/2.5)))
                for f0, f1, hh, b, nv in ((.02, .98, .3, floor, max(1, int(w/2.5))),
                                          (.02, .02+.8/w, 1.3, rim, 1), (.98-.8/w, .98, 1.6, rim, 1)):
                    d = dt.sheet(F(f0, f1), nu, nv, lambda a, c, hh=hh: hh, lambda a, c: -.3, (f'{side} waist',),
                                 'trench', jump=1.2)
                    if d:
                        dt.emit(b, d)
            x = xb
        # Pipe bundle along the inboard side, out of the bay frame's after face.
        # It runs aft while the trench is wide enough for it beside the machinery, then
        # turns down into a trunk collar on the floor.
        x_end = TRENCH_FORE-2.0
        while x_end > 20.0 and at(x_end-2.0, 1.0)-at(x_end-2.0, 0.0) > 8.0:
            x_end -= 2.0
        BUNDLE_END[side] = x_end
        for off, r in ((1.6, .55), (2.8, .55)):
            xs_ = [TRENCH_FORE-2.0-6.0*i for i in range(16) if TRENCH_FORE-2.0-6.0*i > x_end+1.0]+[x_end]
            line = [(xx, s*(at(xx, 0.0)+off)) for xx in xs_ if at(xx, 0.0) is not None]
            c = dt.run(pipes, clamps, line, r, lift=.35, step=2.0, saddle_every=3)
            if c:
                end = c[-1]
                z0 = dt.seat.top(end.x, end.y)[0].z
                pipes.bar(end+Vector((0, 0, r*.3)), Vector((end.x, end.y, z0-.1)), r)
                clamps.bar(Vector((end.x, end.y, z0-.1)), Vector((end.x, end.y, z0+.7)), r*1.6)
        # The machinery: fore to aft, sized to the trench, dark floor left between.
        x = TRENCH_FORE-4.0
        kind = 0
        while x > 10.0:
            a, b = at(x, 0.0), at(x, 1.0)
            if a is None:
                x -= 2.0
                continue
            y0 = a+(3.6 if x > BUNDLE_END[side]+1.0 else .8)    # outboard of the bundle
            w = b-.6-y0
            if w < 1.8:
                x -= 2.0
                continue
            yc = y0+w/2
            f = frame(x, yc)
            if f is None:
                x -= 2.0
                continue
            p, u, v, n = f
            z0 = p.z+.2
            wide = w >= 5.5
            K = ('tanks', 'hx', 'pump', 'drum', 'valve') if wide else ('drum', 'pump', 'drum', 'valve')
            k = K[kind % len(K)]
            if k == 'tanks':
                L, top = 8.0, 0.0
                r = min(1.7, (w-.6)/4)
                foot = [(p+u*su*L/2+v*sv*w/2) for su in (-1, 1) for sv in (-1, 1)]
                if dt.keep.ok([(q.x, q.y) for q in foot], z0+2*r+.5):
                    for sv in (-1, 1):
                        c = p+v*sv*(r+.15)+n*(r+.5)
                        light.bar(c-u*L/2, c+u*L/2, r, n=12)
                        body.bar(c-u*(L/2+.3), c-u*L/2, r*.8, n=12)
                        for t in (-.33, .33):
                            caps.bar(c+u*L*t-u*.3, c+u*L*t+u*.3, r*1.06, n=12)
                    for t in (-.3, .3):
                        body.box(p+u*L*t+n*(r*.6+.25), u, v, n, .4, w/2-.1, r*.6+.3)
                    top = 2*r+.5
            elif k == 'hx':
                L = 7.0
                hh = 2.2
                foot = [(p+u*su*L/2+v*sv*w/2) for su in (-1, 1) for sv in (-1, 1)]
                if dt.keep.ok([(q.x, q.y) for q in foot], z0+hh+1.3):
                    body.box(p+n*(hh/2), u, v, n, L/2, w/2-.2, hh/2+.2)
                    nf = int((L-.6)/1.45)
                    for i in range(nf):
                        t = -L/2+.6+i*1.45+.4
                        caps.box(p+u*t+n*(hh+.55), u, v, n, .25, w/2-.6, .6)
                    light.box(p+n*(hh+1.15)+u*(L/2-.25), u, v, n, .25, w/2-.4, .1)
                    light.box(p+n*(hh+1.15)-u*(L/2-.25), u, v, n, .25, w/2-.4, .1)
            elif k == 'pump':
                L = 6.0
                r = min(1.5, w/2-.4)
                foot = [(p+u*su*L/2+v*sv*w/2) for su in (-1, 1) for sv in (-1, 1)]
                if dt.keep.ok([(q.x, q.y) for q in foot], z0+2*r+1.6):
                    body.box(p+n*.5, u, v, n, L/2, w/2-.2, .7)
                    c = p+n*(1.1+r)
                    light.bar(c-u*(L/2-.4), c+u*.8, r, n=12)                 # motor
                    caps.bar(c-u*(L/2-.2), c-u*(L/2-.4), r*.7, n=12)
                    q = p+u*(L/2-1.4)
                    light.bar(q+n*.9, q+n*(2.0+r), min(1.3, w/2-.3), n=12)   # volute
                    caps.bar(q+n*(2.0+r), q+n*(2.3+r), min(1.3, w/2-.3)*.75, n=12)
            elif k == 'drum':
                r = min(2.0, w/2-.15)
                hh = 3.4
                foot = [(p+u*su*r+v*sv*r) for su in (-1, 1) for sv in (-1, 1)]
                if dt.keep.ok([(q.x, q.y) for q in foot], z0+hh+.4):
                    body.bar(p-n*.1, p+n*.6, r*1.08, n=14)
                    light.bar(p+n*.6, p+n*hh, r, n=14)
                    caps.bar(p+n*hh, p+n*(hh+.35), r*.85, n=14)
                    body.bar(p+n*(hh*.55), p+n*(hh*.55+.4), r*1.05, n=14)
                L = 2*r
            else:  # valve chest
                L = 3.0
                foot = [(p+u*su*L/2+v*sv*w/2) for su in (-1, 1) for sv in (-1, 1)]
                if dt.keep.ok([(q.x, q.y) for q in foot], z0+3.0):
                    body.box(p+n*1.1, u, v, n, L/2, w/2-.3, 1.3)
                    wheels.bar(p+n*2.4, p+n*2.75, min(1.1, w/2-.4), n=12)
                    light.bar(p+n*1.3-v*(w/2-.3), p+n*1.3-v*(w/2+1.3), .5, n=8)   # branch to the bundle
            x -= L+2.2
            kind += 1


def _spine(dt, kit):
    """A pipe bundle down each gutter beside the spine, and radiator banks of big
    fins outboard of it where the gutter widens aft."""
    M = dt.mats
    for side, s in (('port', 1), ('starboard', -1)):
        pipes = dt.batch(f'Detail {side} spine pipe bundle', M['pipe'])
        clamps = dt.batch(f'Detail {side} spine pipe clamps', kit.steel)
        for y, r in ((12.5, .6), (13.8, .6), (15.0, .5), (16.1, .5)):
            dt.run(pipes, clamps, [(80.5, s*y), (59.0, s*y)], r, lift=.1, step=2.0, saddle_every=3)
        base = dt.batch(f'Detail {side} spine radiator frames', M['seam'])
        fins = dt.batch(f'Detail {side} spine radiator fins', M['mach light'])
        tab = _field(dt, f'Hull core', s, [58.0+i for i in range(22)], 9.0, 26.0)
        x = 76.0
        while x > 59.5:
            e = _interp(tab, x-2.0)
            if e and e[1]-17.4 > 2.6:
                y0, y1 = 17.2, e[1]-.5
                def P(a, b, xa=x, y0=y0, y1=y1):
                    return Vector((xa-4.2*a, s*(y0+(y1-y0)*b), 160.0)), Vector((0, 0, -1))
                d = dt.sheet(P, 2, 2, lambda a, b: .6, lambda a, b: -.3, ('Hull core',), 'radiator', jump=1.0)
                if d:
                    dt.emit(base, d)
                    zt = max(q[2] for q in d[0][:9])
                    nf = max(2, int((y1-y0)/1.25))
                    if dt.keep.ok([(x, s*y0), (x-4.2, s*y1)], zt+2.0):
                        for i in range(nf):
                            yy = y0+.5+i*(y1-y0-1.0)/max(1, nf-1)
                            fins.box(Vector((x-2.1, s*yy, zt+.85)), Vector((1, 0, 0)), Vector((0, 1, 0)),
                                     Vector((0, 0, 1)), 1.95, .22, 1.0)
            x -= 4.6


def _big_citadel(dt):
    """The citadel in alternating bone and grey armour: sector plates on the dome's
    tiers split by near-black seams on the facet lines, and darker plates on
    alternate drum flats below the glazing."""
    for k in range(12):
        for r, (r0, r1) in enumerate(((7.2, 12.0), (12.0, 16.6), (16.6, 21.0))):
            b0, b1 = k*30.0-15.0, k*30.0+15.0
            def P(a, c, b0=b0, b1=b1, r0=r0, r1=r1):
                return _polar(r0+(r1-r0)*c, b0+(b1-b0)*a)
            tone = 'bone' if (k+r) % 2 == 0 else 'bone grey'
            L = math.radians(30.0)*(r0+r1)/2
            dt.panel('citadel dome', P, L, r1-r0, .4, tone, ('command dome',), 'dome plate', gap=1.0, grid=2.2)
    for bd in (30.0, 60.0, 150.0):
        for s in (1, -1):
            b = math.radians(s*bd)
            out = Vector((math.cos(b), math.sin(b), 0))
            tan = Vector((-math.sin(b), math.cos(b), 0))
            hw = CIT_A*math.tan(math.radians(15.0))-1.0
            def P(a, c, out=out, tan=tan, bd=bd, s=s):
                o = _cit(CIT_A+12.0, s*bd, 38.0+10.5*c)+tan*(a-.5)*2*hw
                return o, -out
            dt.panel('citadel drum', P, 2*hw, 10.5, .45, 'bone grey', ('command dome', 'drum plate'), 'drum plate',
                     gap=.8, plan=False, grid=2.4, seam=False)


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
        # (The gutter conduits of pass 1 are now the big layer's spine bundle; its junction
        # box stands at the bundle's after end.)
        dt.plate(steel, 57.6, s*14.4, 0, 2.4, 5.0, 1.6, max_rise=.6, label='gutter junction box')
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
    dt.mats = {
        'seam': material('Detail seam black', (.010, .009, .010), .3, .8),
        'brown': kit.plate,
        'dark brown': material('Detail plate dark brown', (.068, .043, .036), .35, .62),
        'bone brown': material('Detail plate bone brown', (.30, .205, .16), .3, .58),
        'grey': kit.grey,
        'light grey': material('Detail plate light grey', (.17, .165, .17), .4, .5),
        'blue steel': material('Detail plate blue steel', (.032, .038, .05), .5, .5),
        'orange': kit.orange,
        'dirty orange': material('Detail plate dirty orange', (.25, .056, .018), .25, .64),
        'bone': kit.bone,
        'bone grey': material('Detail plate bone grey', (.24, .225, .215), .25, .5),
        'mach light': material('Detail machinery light steel', (.34, .325, .31), .6, .42),
        'pipe': pipe_steel,
    }
    # The big layer first, on the hull's own surfaces.
    _big_glacis(dt)
    _big_shoulders(dt)
    for s in (1, -1):      # the spine gutters are kept for the pipe bundle and radiators
        for i in range(73):
            for j in range(31):
                dt.occupy(Vector((56.0+.5*i, s*(9.0+.5*j), 0)))
    _aprons(dt)
    _nose(dt)
    _big_prow(dt)
    _big_core(dt)
    for side, s in (('port', 1), ('starboard', -1)):
        _wall_fill(dt, f'{side} core shoulder', ('Hull core', f'{side} core aft'), s, -20.0, 60.0, 12.0, 40.0,
                   label='core shoulder')
    _big_citadel(dt)
    _trench(dt, kit)
    _spine(dt, kit)
    big = dt.flush()
    # The close-up layer (pass 1) seated on the hull and the big layer's plates.
    dt.seat = _Seat(extra=[n for n in big if 'panels' in n or 'seams' in n or 'trench floor' in n])
    _prow(dt, kit, pipe_steel)
    _glacis(dt, kit, hatch_orange)
    _core(dt, kit, pipe_steel)
    _citadel(dt, kit, pipe_steel)
    dt.finish()
