"""People on foot for C5, seen from overhead: where they can walk, how they move, what they look like.

Three parts, used by `c5_scene.py`:

  Nav     a walk map of the ground, read from the built scene itself: every mesh is sliced at
          knee and chest height and its outline marked (a crate, a vehicle, a wall, a washing
          line), then grown by a body's radius so nobody brushes through an edge. Distance fields from
          each destination give every walker the way round whatever is in the way.
  simulate  moves everyone at 24 steps a second: groups walk together (a leader on the field, the
          rest keeping their places beside or behind), everyone keeps a little space from people
          not in their group, nobody steps into a blocked cell. Plans are short lists of steps:
          go somewhere, wait, stand there for good, or go inside.
  bodies  either simple 3D figures (a body, a head, two swinging legs, a bag) or flat sprite cards
          flipping through a walk cycle with a sun-facing card for the shadow; the walk cycle
          advances with the distance actually walked, so feet do not slide and standing people
          stand still.
"""
import heapq
import math
import random
from pathlib import Path

import bmesh
import bpy
import numpy as np
from mathutils import Vector

CELL = 0.3            # walk-map resolution, metres
RADIUS = 0.3          # a body, for keeping clear of things
FPS = 24
STRIDE = 1.5          # metres a full walk cycle (two steps)


# ---------------------------------------------------------------- walk map

class Nav:
    def __init__(self, x0, y0, x1, y1):
        self.x0, self.y0 = x0, y0
        self.nx, self.ny = int((x1 - x0) / CELL), int((y1 - y0) / CELL)
        self.blocked = np.zeros((self.ny, self.nx), bool)
        self.near = np.zeros((self.ny, self.nx), bool)
        self.fields = {}

    def cell(self, p):
        return int((p[1] - self.y0) / CELL), int((p[0] - self.x0) / CELL)

    def centre(self, j, i):
        return Vector((self.x0 + (i + 0.5) * CELL, self.y0 + (j + 0.5) * CELL))

    def free(self, p):
        j, i = self.cell(p)
        return 0 <= j < self.ny and 0 <= i < self.nx and not self.blocked[j, i]

    def read_scene(self, skip, open_at, heights=(0.55, 1.1), extra=(), movers=()):
        """Blocked wherever any surface crosses knee or chest height (`heights`): every mesh is
        sliced there and its outline marked. Nothing walks through an outline, so the inside of
        a building or a crate is never reached and need not be filled. Objects in `skip` are
        left out; `extra` adds (centre, half-size, heading) rectangles for anything better
        blocked whole, such as the hull. `movers` (from `moving_vehicles`) are blocked along the
        whole of their way through the shot, so nobody stands in a lane something drives down.
        Pockets the open ground at `open_at` (a point, or several where lanes cut the ground in
        parts) cannot reach (the inside of a crate, a cab) are then blocked too, so no destination
        lands in one. `solid` keeps only what stands still, for
        the clip report."""
        raw = np.zeros((self.ny, self.nx), bool)
        x1, y1 = self.x0 + self.nx * CELL, self.y0 + self.ny * CELL
        for name, P in scene_triangles(heights).items():
            if name in skip:
                continue
            P = P[(P[:, :, 0].max(1) >= self.x0) & (P[:, :, 0].min(1) <= x1)
                  & (P[:, :, 1].max(1) >= self.y0) & (P[:, :, 1].min(1) <= y1)]
            if len(P):
                self._mark_slices(raw, P, heights)
        for (cx, cy), (hu, hv), h in extra:
            ys, xs = np.mgrid[0:self.ny, 0:self.nx]
            px, py = self.x0 + (xs + 0.5) * CELL - cx, self.y0 + (ys + 0.5) * CELL - cy
            u, v = px * math.cos(h) + py * math.sin(h), -px * math.sin(h) + py * math.cos(h)
            raw |= (abs(u) <= hu) & (abs(v) <= hv)
        raw[0, :] = raw[-1, :] = raw[:, 0] = raw[:, -1] = True
        self.solid = raw.copy()
        for name, ext, track in movers:                # where anything drives during the shot: keep off it
            self._mark_swept(raw, ext, [(x, y, h) for f, x, y, h in track])
        self.blocked = grow(raw, math.ceil(RADIUS / CELL))
        reach = np.zeros_like(self.blocked)
        for p in ([open_at] if isinstance(open_at, Vector) else open_at):   # several, where lanes cut the ground
            j, i = self.cell(self.nearest_free(Vector(p)))    # in case a lane or a crate covers the point given
            reach[j, i] = True
        while True:
            more = grow(reach, 1) & ~self.blocked
            if (more == reach).all():
                break
            reach = more
        self.blocked |= ~reach
        self.near = grow(self.blocked, 2) & ~self.blocked

    def _mark_swept(self, raw, ext, track, margin=0.4):
        """Mark a vehicle's footprint (x0, x1, y0, y1 in its own frame) at every (x, y, heading)."""
        x0, x1, y0, y1 = ext[0] - margin, ext[1] + margin, ext[2] - margin, ext[3] + margin
        r = math.hypot(max(abs(x0), abs(x1)), max(abs(y0), abs(y1)))
        X1, Y1 = self.x0 + self.nx * CELL, self.y0 + self.ny * CELL
        for x, y, h in track:
            if x + r < self.x0 or x - r > X1 or y + r < self.y0 or y - r > Y1:
                continue
            i0, i1 = max(0, int((x - r - self.x0) / CELL)), min(self.nx, int((x + r - self.x0) / CELL) + 1)
            j0, j1 = max(0, int((y - r - self.y0) / CELL)), min(self.ny, int((y + r - self.y0) / CELL) + 1)
            ys, xs = np.mgrid[j0:j1, i0:i1]
            px, py = self.x0 + (xs + 0.5) * CELL - x, self.y0 + (ys + 0.5) * CELL - y
            u, v = px * math.cos(h) + py * math.sin(h), -px * math.sin(h) + py * math.cos(h)
            raw[j0:j1, i0:i1] |= (u >= x0) & (u <= x1) & (v >= y0) & (v <= y1)

    def _mark_slices(self, raw, P, heights):
        """Mark where triangles `P` (n, 3 corners, xyz) cross each height."""
        for h in heights:
            z = P[:, :, 2] - h
            up = z > 0
            k = up.sum(1)
            sel = (k == 1) | (k == 2)
            if not sel.any():
                continue
            Q, Z, U = P[sel], z[sel], up[sel]
            pts, ok = [], []
            for a, b in ((0, 1), (1, 2), (2, 0)):
                cross = U[:, a] != U[:, b]
                t = Z[:, a] / np.where(cross, Z[:, a] - Z[:, b], 1.0)
                pts.append(Q[:, a, :2] + (Q[:, b, :2] - Q[:, a, :2]) * t[:, None])
                ok.append(cross)
            pts, ok = np.stack(pts, 1), np.stack(ok, 1)
            order = np.argsort(~ok, axis=1, kind='stable')[:, :2]      # the two edges it crosses
            r = np.arange(len(Q))
            A, B = pts[r, order[:, 0]], pts[r, order[:, 1]]
            n = np.ceil(np.linalg.norm(B - A, axis=1) / (CELL * 0.5)).astype(np.int64) + 1
            seg = np.repeat(np.arange(len(Q)), n)
            first = np.cumsum(n) - n
            t = (np.arange(n.sum()) - first[seg]) / np.maximum(n - 1, 1)[seg]
            xy = A[seg] + (B - A)[seg] * t[:, None]
            i = ((xy[:, 0] - self.x0) / CELL).astype(np.int64)
            j = ((xy[:, 1] - self.y0) / CELL).astype(np.int64)
            inside = (i >= 0) & (i < self.nx) & (j >= 0) & (j < self.ny)
            raw[j[inside], i[inside]] = True

    def nearest_free(self, p):
        j0, i0 = self.cell(p)
        for r in range(0, 60):
            best = None
            for j in range(j0 - r, j0 + r + 1):
                for i in range(i0 - r, i0 + r + 1):
                    if 0 <= j < self.ny and 0 <= i < self.nx and not self.blocked[j, i]:
                        d = (j - j0) ** 2 + (i - i0) ** 2
                        if best is None or d < best[0]:
                            best = (d, j, i)
            if best:
                return self.centre(best[1], best[2])
        raise ValueError('no free ground near %s' % (p,))

    def field(self, goal):
        """Walking distance in metres to `goal` from every free cell (inf where unreachable),
        a little dearer close to obstacles so people do not hug walls."""
        key = (round(goal[0], 1), round(goal[1], 1))
        if key in self.fields:
            return self.fields[key]
        nx, ny = self.nx, self.ny
        blocked = self.blocked.ravel().tolist()
        near = self.near.ravel().tolist()
        dist = [math.inf] * (nx * ny)
        g = self.nearest_free(goal)
        j, i = self.cell(g)
        start = j * nx + i
        dist[start] = 0.0
        heap = [(0.0, start)]
        steps = [(-1, 0, 1.0), (1, 0, 1.0), (0, -1, 1.0), (0, 1, 1.0),
                 (-1, -1, 1.4142), (-1, 1, 1.4142), (1, -1, 1.4142), (1, 1, 1.4142)]
        while heap:
            d, k = heapq.heappop(heap)
            if d > dist[k]:
                continue
            kj, ki = divmod(k, nx)
            for dj, di, w in steps:
                jj, ii = kj + dj, ki + di
                if not (0 <= jj < ny and 0 <= ii < nx):
                    continue
                kk = jj * nx + ii
                if blocked[kk]:
                    continue
                if dj and di and (blocked[kj * nx + ii] or blocked[jj * nx + ki]):
                    continue                                      # no cutting corners
                nd = d + w * CELL * (1.6 if near[kk] else 1.0)
                if nd < dist[kk]:
                    dist[kk] = nd
                    heapq.heappush(heap, (nd, kk))
        f = np.array(dist).reshape(ny, nx)
        self.fields[key] = f
        return f

    def value(self, f, p):
        j, i = self.cell(p)
        if 0 <= j < self.ny and 0 <= i < self.nx:
            return f[j, i]
        return math.inf

    def way(self, f, p, look=1.2):
        """The direction downhill on field `f` from `p`, looking `look` metres ahead."""
        here = self.value(f, p)
        best, bd = None, here
        for k in range(16):
            a = 2 * math.pi * k / 16
            q = p + Vector((math.cos(a), math.sin(a))) * look
            v = self.value(f, q)
            if v < bd and self.clear_line(p, q):
                best, bd = q, v
        if best is None:                                          # boxed in at this reach: look closer
            for k in range(24):
                a = 2 * math.pi * k / 24
                q = p + Vector((math.cos(a), math.sin(a))) * CELL * 1.5
                v = self.value(f, q)
                if v < bd:
                    best, bd = q, v
        return None if best is None else (best - p).normalized()

    def clear_line(self, a, b):
        n = max(2, int((b - a).length / (CELL * 0.5)))
        return all(self.free(a.lerp(b, t / n)) for t in range(n + 1))


def _fcurves(ob):
    ad = ob.animation_data
    if ad is None or ad.action is None:
        return []
    out = list(getattr(ad.action, 'fcurves', []) or [])
    for layer in getattr(ad.action, 'layers', []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                out += list(bag.fcurves)
    return out


def moving_vehicles(f0, f1, step=3, top=2.2):
    """Everything at ground level that moves between frames f0 and f1: a vehicle (an unparented
    empty with mesh children, as `c4x_scene.vehicle` and `port_life` build them) or a loose mesh
    such as a container a crane carries. Returns [(name, footprint (x0, x1, y0, y1) in its own
    frame, [(frame, x, y, heading)])], keeping only the moments it is lower than `top` (a box swung
    overhead blocks nobody)."""
    out = []
    frames = list(range(f0, f1 + 1, step))
    for ob in bpy.data.objects:
        if ob.parent is not None or ob.type not in ('EMPTY', 'MESH') or ob.instance_type == 'COLLECTION':
            continue
        fcs = _fcurves(ob)
        if not any(fc.data_path == 'location' for fc in fcs):
            continue
        parts = [ob] if ob.type == 'MESH' else [o for o in ob.children_recursive if o.type == 'MESH']
        pts = []
        for o in parts:
            m = ob.matrix_world.inverted() @ o.matrix_world
            pts += [tuple(m @ Vector(c)) for c in o.bound_box]
        if not pts:
            continue
        a = np.array(pts)
        tr = np.tile([ob.location.x, ob.location.y, ob.location.z, ob.rotation_euler.z], (len(frames), 1))
        for fc in fcs:
            col = {('location', 0): 0, ('location', 1): 1, ('location', 2): 2, ('rotation_euler', 2): 3}.get(
                (fc.data_path, fc.array_index))
            if col is not None:
                tr[:, col] = [fc.evaluate(f) for f in frames]
        if np.ptp(tr[:, 0]) + np.ptp(tr[:, 1]) + np.ptp(tr[:, 2]) < 0.3:
            continue
        low = tr[:, 2] + a[:, 2].min() < top
        if not low.any():
            continue
        out.append((ob.name, (a[:, 0].min(), a[:, 0].max(), a[:, 1].min(), a[:, 1].max()),
                    [(f, x, y, h) for f, (x, y, z, h), lo in zip(frames, tr, low) if lo]))
    return out


def mover_clips(walkers, movers, frame0, names=None):
    """Walker-frames in which a vehicle that is moving covers somebody (sampled at the vehicles'
    own step): should be 0, since their lanes are blocked on the walk map. A vehicle standing
    still is not counted: once parked it is on the walk map like a wall, and the clip report covers
    it. `names` (optional set) collects who was caught, for finding them."""
    n = 0
    for name, (x0, x1, y0, y1), track in movers:
        for i, (f, x, y, h) in enumerate(track):
            near = track[max(i - 1, 0):i + 2]
            if all(abs(q[1] - x) + abs(q[2] - y) < 0.05 and abs(q[3] - h) < 0.01 for q in near):
                continue
            for w in walkers:
                k = f - frame0
                if 0 <= k < len(w.track) and w.track[k][5]:
                    px, py = w.track[k][1] - x, w.track[k][2] - y
                    u, v = px * math.cos(h) + py * math.sin(h), -px * math.sin(h) + py * math.cos(h)
                    if x0 - RADIUS <= u <= x1 + RADIUS and y0 - RADIUS <= v <= y1 + RADIUS:
                        n += 1
                        if names is not None:
                            names.add((w.name, name, k))
    return n


_TRIANGLES = {}


def scene_triangles(heights):
    """Every mesh's world-space triangles that reach between the lowest and highest of `heights`,
    by object name, at the current frame. Read once and kept, so several walk maps over one scene
    (one per place people work) cost one pass over its meshes."""
    scene = bpy.context.scene
    key = (scene.frame_current, tuple(heights))
    if key in _TRIANGLES:
        return _TRIANGLES[key]
    lo, hi = min(heights), max(heights)
    dg = bpy.context.evaluated_depsgraph_get()
    out = {}
    for ob in scene.objects:
        if ob.type != 'MESH':
            continue
        ev = ob.evaluated_get(dg)
        mw = ev.matrix_world
        bb = np.array([tuple(mw @ Vector(c)) for c in ev.bound_box])
        if bb[:, 2].min() > hi or bb[:, 2].max() < lo:
            continue
        me = ev.to_mesh()
        me.calc_loop_triangles()
        nt = len(me.loop_triangles)
        if nt:
            co = np.empty(len(me.vertices) * 3)
            me.vertices.foreach_get('co', co)
            tri = np.empty(nt * 3, np.int64)
            me.loop_triangles.foreach_get('vertices', tri)
            m = np.array(mw)
            co = co.reshape(-1, 3) @ m[:3, :3].T + m[:3, 3]
            P = co[tri.reshape(-1, 3)]
            P = P[(P[:, :, 2].max(1) > lo) & (P[:, :, 2].min(1) < hi)]
            if len(P):
                out[ob.name] = P
        ev.to_mesh_clear()
    _TRIANGLES.clear()
    _TRIANGLES[key] = out
    return out


def grow(mask, r):
    out = mask.copy()
    for dj in range(-r, r + 1):
        for di in range(-r, r + 1):
            if dj * dj + di * di > r * r + r:
                continue
            out |= np.roll(np.roll(mask, dj, 0), di, 1)
    return out


# ---------------------------------------------------------------- walkers and groups

POSES = ('stand', 'crouch', 'signal', 'carry', 'talk', 'folded', 'work')


class Walker:
    def __init__(self, name, role, scale=1.0, speed=1.25, bag=False, look=None, pose='stand', carry=False):
        self.name, self.role, self.scale, self.speed, self.bag = name, role, scale, speed, bag
        self.look = look                     # where to face when standing: a point, another Walker, or None
        self.pose = pose                     # what the body does while standing (POSES)
        self.carry = carry                   # holding a box or a crate in front
        self.pos = None
        self.vel = Vector((0, 0))
        self.heading = 0.0
        self.phase = 0.0
        self.shown = False
        self.group = None
        self.track = []                      # (frame, x, y, heading, phase, shown)
        self.state = []                      # (pose, carry), one per track entry


class Group:
    """People who walk together. `plan` is a list of steps run by the leader:
    ('go', (x, y), radius), ('wait', seconds), ('stay',), ('enter', (x, y)),
    ('until', seconds)        wait until that time into the shot,
    ('meet', walker, metres)  wait until that walker is within reach,
    ('carry', bool), ('pose', name), ('face', point or walker)   change at once and go on."""

    def __init__(self, members, spawn, t0, plan, slots=None):
        self.members, self.spawn, self.t0, self.plan = members, Vector(spawn), t0, list(plan)
        self.slots = slots or default_slots(len(members))
        for m in members:
            m.group = self
        self.step = 0
        self.best, self.since = None, 0.0         # closest the leader has come to the current goal, and when
        self.wait_until = None
        self.entered = False
        self.done = False


def default_slots(n):
    """Places relative to the leader, in (ahead, left) metres: two abreast, then a pair behind."""
    base = [(0, 0), (0, 0.8), (-1.0, 0.4), (-1.0, -0.4), (-2.0, 0.2), (-2.0, 0.9)]
    return [Vector(s) for s in base[:n]]


def simulate(nav, groups, frames, ground_z, dt=1.0 / FPS, rng=None):
    rng = rng or random.Random(3)
    walkers = [w for g in groups for w in g.members]
    for f in range(frames):
        t = f * dt
        # spawn: members come out one after another, a step apart
        for g in groups:
            for k, w in enumerate(g.members):
                if not w.shown and w.pos is None and t >= g.t0 + k * 0.45:
                    w.pos = nav.nearest_free(g.spawn + Vector((rng.uniform(-0.4, 0.4), rng.uniform(-0.4, 0.4))))
                    w.shown = True
                    w.heading = 0.0
        live = [w for w in walkers if w.shown]
        for g in groups:
            lead = g.members[0]
            if lead.pos is None or g.done:
                continue
            while g.step < len(g.plan) and g.plan[g.step][0] in ('carry', 'pose', 'face'):
                kind, arg = g.plan[g.step]
                setattr(lead, {'carry': 'carry', 'pose': 'pose', 'face': 'look'}[kind], arg)
                g.step += 1
            step = g.plan[g.step] if g.step < len(g.plan) else ('stay',)
            want = {}
            if step[0] == 'until':
                if t >= step[1]:
                    g.step += 1
            elif step[0] == 'meet':
                o = step[1]
                if o.pos is not None and o.shown and (o.pos - lead.pos).length <= step[2]:
                    g.step += 1
            elif step[0] == 'go':
                f_ = nav.field(step[1])
                left = nav.value(f_, lead.pos)
                # Close enough and no longer getting closer (someone is standing on the spot):
                # call it there rather than jostle for it for the rest of the shot.
                if g.best is None or left < g.best - 0.05:
                    g.best, g.since = left, t
                stalled = left <= step[2] + 1.2 and t - g.since > 1.5
                if (left <= step[2] or stalled) and all(
                        m.pos is not None and (m.pos - lead.pos).length < 3.0 for m in g.members):
                    g.step += 1
                    g.best = None
                else:
                    d = nav.way(f_, lead.pos)
                    lag = max((m.pos - lead.pos).length for m in g.members if m.pos is not None)
                    slow = 0.55 if lag > 3.0 else 1.0                  # the leader waits for stragglers
                    want[lead] = d * lead.speed * slow if d is not None else Vector((0, 0))
                    fwd = Vector((math.cos(lead.heading), math.sin(lead.heading)))
                    left = Vector((-fwd.y, fwd.x))
                    for m, s in zip(g.members[1:], g.slots[1:]):
                        if m.pos is None:
                            continue
                        target = lead.pos + fwd * s.x + left * s.y
                        if not nav.free(target):
                            target = lead.pos - fwd * 0.8
                        to = target - m.pos
                        if to.length > 6.0:                              # lost: walk the field instead
                            dd = nav.way(f_, m.pos)
                            want[m] = dd * m.speed * 1.2 if dd is not None else Vector((0, 0))
                        else:
                            want[m] = to.normalized() * min(m.speed * 1.35, to.length * 1.8) if to.length > 0.05 else Vector((0, 0))
            elif step[0] == 'wait':
                if g.wait_until is None:
                    g.wait_until = t + step[1]
                if t >= g.wait_until:
                    g.wait_until = None
                    g.step += 1
            elif step[0] == 'enter':                                   # each in turn through the door
                f_ = nav.field(step[1])
                for m in g.members:
                    if not m.shown:
                        continue
                    if nav.value(f_, m.pos) <= 0.8:
                        m.shown = False
                        continue
                    d = nav.way(f_, m.pos, look=0.9)
                    want[m] = d * m.speed if d is not None else Vector((0, 0))
                if all(not m.shown for m in g.members if m.pos is not None):
                    g.done = True
            for m in g.members:
                if m.pos is not None and m not in want:
                    want[m] = Vector((0, 0))
            for m, v in want.items():
                m.want = v
        # keep a little space; integrate; stay on free ground
        for w in live:
            if not w.shown:
                continue
            push = Vector((0, 0))
            for o in live:
                if o is w or not o.shown:
                    continue
                d = w.pos - o.pos
                same = w.group is o.group
                r = 0.65 if same else 0.95
                if 1e-4 < d.length < r:
                    push += d.normalized() * (r - d.length) * (2.0 if same else 4.0)
            v_des = getattr(w, 'want', Vector((0, 0))) + push
            if v_des.length > w.speed * 1.5:
                v_des = v_des.normalized() * w.speed * 1.5
            w.vel = w.vel.lerp(v_des, min(1.0, dt * 5.0))
            new = w.pos + w.vel * dt
            if not nav.free(new):
                alt = [w.pos + Vector((w.vel.x * dt, 0)), w.pos + Vector((0, w.vel.y * dt))]
                new = next((a for a in alt if nav.free(a)), w.pos)
                if new is w.pos:
                    w.vel = Vector((0, 0))
            w.phase += (new - w.pos).length / STRIDE
            w.pos = new
            if w.vel.length > 0.2:
                target = math.atan2(w.vel.y, w.vel.x)
            elif w.look is not None:
                at_ = w.look.pos if isinstance(w.look, Walker) else w.look
                target = w.heading if at_ is None else math.atan2(at_[1] - w.pos.y, at_[0] - w.pos.x)
            else:
                target = w.heading
            dh = (target - w.heading + math.pi) % (2 * math.pi) - math.pi
            w.heading += dh * min(1.0, dt * 6.0)
        for w in walkers:
            if w.pos is not None:
                w.track.append((f, w.pos.x, w.pos.y, w.heading, w.phase, w.shown))
                w.state.append((w.pose, w.carry))
    return walkers


def clip_report(nav, walkers):
    """Frames in which anyone stands inside something, and the closest two strangers came."""
    inside = sum(1 for w in walkers for (_, x, y, _, _, s) in w.track
                 if s and nav.solid[nav.cell((x, y))])
    return inside


# ---------------------------------------------------------------- debug map

def save_map(nav, walkers, path, scale=2):
    img = np.full((nav.ny, nav.nx, 3), 0.72, np.float32)
    img[nav.blocked] = (0.45, 0.45, 0.45)
    img[nav.solid] = (0.15, 0.15, 0.15)
    colours = {'family': (0.2, 0.4, 0.9), 'worker': (1.0, 0.5, 0.0), 'customs': (0.9, 0.1, 0.1),
               'nessa': (0.0, 0.6, 0.2), 'port': (0.6, 0.85, 0.0), 'guard': (0.1, 0.1, 0.5),
               'crew': (0.0, 0.7, 0.7)}
    for w in walkers:
        c = colours.get(w.role, (1, 0, 1))
        for (_, x, y, _, _, s) in w.track:
            if s:
                j, i = nav.cell((x, y))
                if 0 <= j < nav.ny and 0 <= i < nav.nx:
                    img[j, i] = c
    img = np.repeat(np.repeat(img, scale, 0), scale, 1)
    h, w_, _ = img.shape
    rgba = np.concatenate([img, np.ones((h, w_, 1), np.float32)], 2)
    im = bpy.data.images.new('C5 walk map', w_, h)
    im.pixels = rgba.ravel()
    im.filepath_raw = str(path)
    im.file_format = 'PNG'
    im.save()


# ---------------------------------------------------------------- bodies

CLOTH = {'family': [(0.35, 0.40, 0.48), (0.55, 0.30, 0.22), (0.62, 0.58, 0.48), (0.25, 0.36, 0.28),
                    (0.45, 0.38, 0.30), (0.30, 0.30, 0.34)],
         'worker': [(1.0, 0.50, 0.06)], 'customs': [(0.88, 0.88, 0.86)], 'nessa': [(0.13, 0.13, 0.15)],
         # The public port: Hegemony port hands in lime vests, MuniSec guards in navy, freighter
         # crews in faded coveralls; the port's officials wear customs white.
         'port': [(0.62, 0.80, 0.12)], 'guard': [(0.10, 0.14, 0.26)],
         'crew': [(0.38, 0.42, 0.40), (0.30, 0.38, 0.46), (0.46, 0.40, 0.30)]}


def pose_at(pose, moving, phase, t, seed):
    """What the body is doing this frame: (left arm, right arm, squash, sprite frame). An arm is
    (pitch forward, raise out) in radians; squash shortens the figure (a crouch); the sprite frame
    indexes the sheet's columns: 0-7 walk, then stand, crouch a/b, signal up/out, carry, talk,
    folded (`SHEET_COLUMNS`)."""
    if moving:
        s = math.sin(2 * math.pi * phase)
        if pose == 'carry':
            return (1.25, 0.1), (1.25, 0.1), 1.0, int(phase * 8) % 8
        return (-0.35 * s, 0.05), (0.35 * s, 0.05), 1.0, int(phase * 8) % 8
    k = t + seed
    if pose == 'crouch':
        b = math.sin(2 * math.pi * 0.7 * k) > 0
        return (1.1 if b else 0.8, 0.1), (0.9 if b else 1.2, 0.1), 0.62, 9 if b else 10
    if pose == 'signal':                                   # both arms up and out, waving in
        up = math.sin(2 * math.pi * 0.9 * k) > 0
        return (0.0, 2.7 if up else 1.5), (0.0, 2.7 if up else 1.5), 1.0, 11 if up else 12
    if pose == 'carry':
        return (1.25, 0.1), (1.25, 0.1), 1.0, 13
    if pose == 'work':                                     # hands busy in front: a hose, a tool
        b = math.sin(2 * math.pi * 0.8 * k) > 0
        return (1.0 if b else 0.7, 0.1), (0.7 if b else 1.0, 0.1), 1.0, 13 if b else 8
    if pose == 'talk':                                     # now and then a hand comes up
        b = math.sin(2 * math.pi * 0.31 * k) + 0.6 * math.sin(2 * math.pi * 0.73 * k) > 0.7
        return (0.0, 0.05), (0.9 if b else 0.0, 0.25 if b else 0.05), 1.0, 14 if b else 8
    if pose == 'folded':
        return (1.35, 0.0), (1.35, 0.0), 1.0, 15
    return (0.0, 0.05), (0.0, 0.05), 1.0, 8


SHEET_COLUMNS = 16      # 8 walk frames, then stand, crouch a, crouch b, signal up, signal out, carry, talk, folded


def keyed(walkers, ground_z, frame0, make, every=2):
    """Build each walker's body with `make(walker, index)` -> (root, legs, cards[, extra]) and key it.
    `extra` may hold 'arms' [(shoulder, side)], 'box' (shown while carrying) and 'squash' (scaled
    down the z axis for a crouch)."""
    for n, w in enumerate(walkers):
        if not w.track:
            continue
        built = make(w, n)
        root, legs, cards = built[:3]
        extra = built[3] if len(built) > 3 else {}
        seed = (n * 0.6180339) % 1.0 * 7.0
        last = len(w.track) - 1
        ch = {}

        def put(ob, path, i, fr, v):
            ch.setdefault((ob, path, i), []).append((fr, v))
        for k, (f, x, y, h, ph, shown) in enumerate(w.track):
            if k % every and k != last and shown == w.track[max(0, k - 1)][5]:
                continue
            fr = frame0 + f
            moving = k > 0 and abs(ph - w.track[k - 1][4]) > 1e-4
            pose, carry = w.state[k] if k < len(w.state) else ('stand', False)
            arm_l, arm_r, squash, frame = pose_at(pose, moving, ph, f / FPS, seed)
            s = w.scale if shown else 1e-4
            for i, v in enumerate((x, y, ground_z(x, y))):
                put(root, 'location', i, fr, v)
            put(root, 'rotation_euler', 2, fr, h)
            for i in range(3):
                put(root, 'scale', i, fr, s)
            for hip, side in legs:
                put(hip, 'rotation_euler', 1, fr, side * 0.45 * math.sin(2 * math.pi * ph) if moving else 0.0)
            for (sh, side), (pitch, out) in zip(extra.get('arms', ()), (arm_l, arm_r)):
                put(sh, 'rotation_euler', 1, fr, -pitch)
                put(sh, 'rotation_euler', 0, fr, side * out)
            if 'squash' in extra:
                put(extra['squash'], 'scale', 2, fr, squash)
            if 'box' in extra:
                put(extra['box'], 'scale', 0, fr, 1.0 if carry else 0.0)
            for c in cards:
                put(c, '["walk"]', 0, fr, float(frame))
        if w.track[0][0] > 0:                                     # hidden until spawned
            for i in range(3):
                ch[(root, 'scale', i)].insert(0, (frame0, 1e-4))
        for (ob, path, i), keys in ch.items():
            step = path == '["walk"]' or ob is extra.get('box')   # a flip book: no in-betweens
            bake(ob, path, i, keys, 'CONSTANT' if step else 'BEZIER')


_INTERP = {}


def bake(ob, path, index, keys, interp):
    """Write a whole channel at once, much faster than a keyframe_insert per frame. Runs of equal
    values are thinned to their ends, so people standing still cost almost nothing."""
    from bpy_extras import anim_utils
    thin = [keys[0]]
    for a, b, c in zip(keys, keys[1:], keys[2:]):
        if not (abs(a[1] - b[1]) < 1e-6 and abs(b[1] - c[1]) < 1e-6):
            thin.append(b)
    if len(keys) > 1:
        thin.append(keys[-1])
    # A custom property ('["walk"]') is a scalar: Blender wants no index for it, and its curve is index 0.
    ob.keyframe_insert(path, index=-1 if path.startswith('[') else index, frame=thin[0][0])
    ad = ob.animation_data
    fc = anim_utils.action_get_channelbag_for_slot(ad.action, ad.action_slot).fcurves.find(path, index=index)
    if interp not in _INTERP:
        _INTERP[interp] = bpy.types.Keyframe.bl_rna.properties['interpolation'].enum_items[interp].value
    fc.keyframe_points.clear()
    fc.keyframe_points.add(len(thin))
    fc.keyframe_points.foreach_set('co', [v for kv in thin for v in kv])
    fc.keyframe_points.foreach_set('interpolation', [_INTERP[interp]] * len(thin))
    fc.update()


def body_3d(mesh, box, material):
    mats, shapes = {}, {}

    def mat(name, rgb):
        if name not in mats:
            mats[name] = material(name, rgb, 0.9)
        return mats[name]

    def part(name, key, size, centre, m, parent):
        """One shared mesh per shape and material: hundreds of people, a handful of meshes."""
        if (key, m.name) not in shapes:
            b = bmesh.new()
            box(b, size, centre)
            shapes[(key, m.name)] = mesh(name, b, m).data
            ob = bpy.data.objects[name]
        else:
            ob = link(bpy.data.objects.new(name, shapes[(key, m.name)]))
        ob.parent = parent
        return ob

    def make(w, n):
        palette = CLOTH[w.role]
        cloth = mat('Walker %s %d' % (w.role, n % len(palette)), palette[n % len(palette)])
        legs_m = mat('Walker legs', (0.12, 0.11, 0.10))
        root = link(bpy.data.objects.new(w.name, None))
        fig = link(bpy.data.objects.new(w.name + ' figure', None))      # a crouch shortens this
        fig.parent = root
        part(w.name + ' body', 'body', (0.30, 0.50, 0.70), (0, 0, 1.15), cloth, fig)
        part(w.name + ' head', 'head', (0.24, 0.22, 0.26), (0.02, 0, 1.65), mat('Walker head', (0.20, 0.14, 0.10)), fig)
        if w.bag:
            part(w.name + ' bag', 'bag', (0.45, 0.22, 0.35), (0, -0.38, 0.75), mat('Walker bag', (0.30, 0.24, 0.16)), fig)
        load = link(bpy.data.objects.new(w.name + ' load', None))       # what is carried, shown by its scale
        load.parent = fig
        part(w.name + ' crate', 'crate', (0.42, 0.46, 0.32), (0.42, 0, 1.12), mat('Walker load', (0.55, 0.42, 0.22)), load)
        legs, arms = [], []
        for side in (-1, 1):
            hip = link(bpy.data.objects.new('%s hip %d' % (w.name, side), None))
            hip.parent = fig
            hip.location = (0, side * 0.13, 0.85)
            part('%s leg %d' % (w.name, side), 'leg', (0.16, 0.16, 0.85), (0, 0, -0.425), legs_m, hip)
            legs.append((hip, side))
            sh = link(bpy.data.objects.new('%s shoulder %d' % (w.name, side), None))
            sh.parent = fig
            sh.location = (0, side * 0.32, 1.45)
            part('%s arm %d' % (w.name, side), 'arm', (0.12, 0.12, 0.62), (0, 0, -0.31), cloth, sh)
            arms.append((sh, side))
        return root, legs, [], {'arms': arms, 'box': load, 'squash': fig}
    return make


SPRITE_ROWS = ['family'] * 4 + ['worker', 'customs', 'nessa', 'port', 'guard'] + ['crew'] * 3
SPRITE_LOOKS = [((1.0, 0.50, 0.06), (0.16, 0.12, 0.10)), ((0.88, 0.88, 0.86), (0.96, 0.59, 0.12)),
                ((0.13, 0.13, 0.15), (0.30, 0.22, 0.16)), ((0.62, 0.80, 0.12), (0.15, 0.12, 0.10)),
                ((0.10, 0.14, 0.26), (0.08, 0.08, 0.09)), ((0.38, 0.42, 0.40), (0.20, 0.14, 0.10)),
                ((0.30, 0.38, 0.46), (0.12, 0.10, 0.08)), ((0.46, 0.40, 0.30), (0.28, 0.20, 0.12))]
SHEETS = Path(__file__).resolve().parent / 'crowd-sprites'


def body_sprite(mesh, sun_dir, out_dir, box=None, material=None):
    """Flat cards, one row of the sheet per look (`SPRITE_ROWS`): four family rows, then
    Nessa's hi-vis, customs white, Nessa's dark coat, the port's lime vests, MuniSec navy and
    three freighter-crew coveralls. Columns are `SHEET_COLUMNS`: the walk, then the work poses.
    The frame comes from the card's 'walk' property and the row from its 'row' property.

    The sheets are `crowd-sprites/crowd-top.png` and `crowd-side.png` when those have been made,
    otherwise the placeholders `c5_crowd_test.py` draws. A carried crate is a small box (`box`,
    `material`: `c4x_scene`'s), shown while carrying, as with the 3D figures."""
    import c5_crowd_test as sheets
    sheets.VARIANTS = [*sheets.VARIANTS[:4], *SPRITE_LOOKS]
    out_dir.mkdir(parents=True, exist_ok=True)
    if (SHEETS / 'crowd-top.png').exists():
        top = bpy.data.images.load(str(SHEETS / 'crowd-top.png'))
    else:
        top = sheets.to_image('Crowd sheet top', sheets.top_sheet(True), out_dir / 'crowd-top.png')
    if (SHEETS / 'crowd-side.png').exists():
        side = bpy.data.images.load(str(SHEETS / 'crowd-side.png'))
    else:
        side = sheets.to_image('Crowd sheet side', sheets.side_sheet(True), out_dir / 'crowd-side.png')
    rows = len(SPRITE_ROWS)
    top_m, side_m = sprite_material('Crowd top', top, rows), sprite_material('Crowd shadow', side, rows)
    sun_xy = Vector((sun_dir.x, sun_dir.y)).normalized()
    crate = {}

    def make(w, n):
        if w.role == 'family':
            row = n % 4
        elif w.role == 'crew':
            row = SPRITE_ROWS.index('crew') + n % 3
        else:
            row = SPRITE_ROWS.index(w.role)
        root = link(bpy.data.objects.new(w.name, None))
        t = sheets.card(w.name + ' top', 64 * sheets.PX, 64 * sheets.PX, top_m, False)
        t.parent = root
        t.location = (0, 0, 0.25)
        s = sheets.card(w.name + ' shadow', 64 * sheets.PX, 96 * sheets.PX, side_m, True)
        s.parent = root
        # The shadow card faces the sun whatever the walker's heading: undo the root's turn with a
        # driver, so the silhouette is always side-on to the light.
        s.rotation_mode = 'XYZ'
        d = s.driver_add('rotation_euler', 2).driver
        d.type = 'SCRIPTED'
        v = d.variables.new()
        v.name = 'h'
        v.targets[0].id = root
        v.targets[0].data_path = 'rotation_euler[2]'
        d.expression = '%.5f - h' % (math.atan2(sun_xy.y, sun_xy.x) + math.pi / 2)
        s.visible_camera = False
        s.visible_diffuse = s.visible_glossy = False
        for c in (t, s):
            c['row'] = float(row)
            c['walk'] = 0.0
        extra = {}
        if box is not None:
            load = link(bpy.data.objects.new(w.name + ' load', None))
            load.parent = root
            if 'data' not in crate:
                b = bmesh.new()
                box(b, (0.42, 0.46, 0.32), (0.42, 0, 1.12))
                crate['data'] = mesh(w.name + ' crate', b, material('Walker load', (0.55, 0.42, 0.22), 0.9)).data
                ob = bpy.data.objects[w.name + ' crate']
            else:
                ob = link(bpy.data.objects.new(w.name + ' crate', crate['data']))
            ob.parent = load
            extra['box'] = load
        return root, [], [t, s], extra
    return make


def sprite_material(name, img, rows, cols=SHEET_COLUMNS):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.surface_render_method = 'DITHERED'
    try:
        m.use_transparent_shadow = True
    except AttributeError:
        pass
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Roughness'].default_value = 0.9

    def attr(prop):
        a = nt.nodes.new('ShaderNodeAttribute')
        a.attribute_type = 'OBJECT'
        a.attribute_name = '["%s"]' % prop
        return a.outputs['Fac']

    def op(kind, a, b):
        n = nt.nodes.new('ShaderNodeMath')
        n.operation = kind
        for k, v in enumerate((a, b)):
            if isinstance(v, (int, float)):
                n.inputs[k].default_value = v
            else:
                nt.links.new(v, n.inputs[k])
        return n.outputs[0]
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(nt.nodes.new('ShaderNodeTexCoord').outputs['UV'], sep.inputs[0])
    u = op('DIVIDE', op('ADD', sep.outputs['X'], attr('walk')), float(cols))
    v = op('DIVIDE', op('ADD', sep.outputs['Y'], op('SUBTRACT', float(rows - 1), attr('row'))), float(rows))
    comb = nt.nodes.new('ShaderNodeCombineXYZ')
    nt.links.new(u, comb.inputs['X'])
    nt.links.new(v, comb.inputs['Y'])
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Closest'
    nt.links.new(comb.outputs[0], tex.inputs['Vector'])
    nt.links.new(tex.outputs['Color'], p.inputs['Base Color'])
    nt.links.new(tex.outputs['Alpha'], p.inputs['Alpha'])
    return m


def link(ob):
    bpy.context.scene.collection.objects.link(ob)
    return ob
