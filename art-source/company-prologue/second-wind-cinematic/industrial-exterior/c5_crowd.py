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

    def read_scene(self, skip, open_at, heights=(0.55, 1.1), extra=()):
        """Blocked wherever any surface crosses knee or chest height (`heights`): every mesh is
        sliced there and its outline marked. Nothing walks through an outline, so the inside of
        a building or a crate is never reached and need not be filled. Objects in `skip` are
        left out; `extra` adds (centre, half-size, heading) rectangles for anything better
        blocked whole, such as the hull. Pockets the open ground at `open_at` cannot reach (the
        inside of a crate, a cab) are then blocked too, so no destination lands in one."""
        dg = bpy.context.evaluated_depsgraph_get()
        raw = np.zeros((self.ny, self.nx), bool)
        x1, y1 = self.x0 + self.nx * CELL, self.y0 + self.ny * CELL
        lo, hi = min(heights), max(heights)
        for ob in bpy.context.scene.objects:
            if ob.type != 'MESH' or ob.name in skip:
                continue
            ev = ob.evaluated_get(dg)
            mw = ev.matrix_world
            bb = np.array([tuple(mw @ Vector(c)) for c in ev.bound_box])
            if (bb[:, 0].max() < self.x0 or bb[:, 0].min() > x1 or bb[:, 1].max() < self.y0
                    or bb[:, 1].min() > y1 or bb[:, 2].min() > hi or bb[:, 2].max() < lo):
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
                self._mark_slices(raw, co[tri.reshape(-1, 3)], heights)
            ev.to_mesh_clear()
        for (cx, cy), (hu, hv), h in extra:
            ys, xs = np.mgrid[0:self.ny, 0:self.nx]
            px, py = self.x0 + (xs + 0.5) * CELL - cx, self.y0 + (ys + 0.5) * CELL - cy
            u, v = px * math.cos(h) + py * math.sin(h), -px * math.sin(h) + py * math.cos(h)
            raw |= (abs(u) <= hu) & (abs(v) <= hv)
        raw[0, :] = raw[-1, :] = raw[:, 0] = raw[:, -1] = True
        self.solid = raw
        self.blocked = grow(raw, math.ceil(RADIUS / CELL))
        j, i = self.cell(open_at)
        reach = np.zeros_like(self.blocked)
        reach[j, i] = True
        while True:
            more = grow(reach, 1) & ~self.blocked
            if (more == reach).all():
                break
            reach = more
        self.blocked |= ~reach
        self.near = grow(self.blocked, 2) & ~self.blocked

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


def grow(mask, r):
    out = mask.copy()
    for dj in range(-r, r + 1):
        for di in range(-r, r + 1):
            if dj * dj + di * di > r * r + r:
                continue
            out |= np.roll(np.roll(mask, dj, 0), di, 1)
    return out


# ---------------------------------------------------------------- walkers and groups

class Walker:
    def __init__(self, name, role, scale=1.0, speed=1.25, bag=False, look=None):
        self.name, self.role, self.scale, self.speed, self.bag = name, role, scale, speed, bag
        self.look = look                     # where to face when standing (None: keep heading)
        self.pos = None
        self.vel = Vector((0, 0))
        self.heading = 0.0
        self.phase = 0.0
        self.shown = False
        self.track = []                      # (frame, x, y, heading, phase, shown)


class Group:
    """People who walk together. `plan` is a list of steps run by the leader:
    ('go', (x, y), radius), ('wait', seconds), ('stay',), ('enter',)."""

    def __init__(self, members, spawn, t0, plan, slots=None):
        self.members, self.spawn, self.t0, self.plan = members, Vector(spawn), t0, list(plan)
        self.slots = slots or default_slots(len(members))
        self.step = 0
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
            step = g.plan[g.step] if g.step < len(g.plan) else ('stay',)
            want = {}
            if step[0] == 'go':
                f_ = nav.field(step[1])
                if nav.value(f_, lead.pos) <= step[2] and all(
                        m.pos is not None and (m.pos - lead.pos).length < 3.0 for m in g.members):
                    g.step += 1
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
                same = any(w in g.members and o in g.members for g in groups)
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
                target = math.atan2(w.look[1] - w.pos.y, w.look[0] - w.pos.x)
            else:
                target = w.heading
            dh = (target - w.heading + math.pi) % (2 * math.pi) - math.pi
            w.heading += dh * min(1.0, dt * 6.0)
        for w in walkers:
            if w.pos is not None:
                w.track.append((f, w.pos.x, w.pos.y, w.heading, w.phase, w.shown))
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
               'nessa': (0.0, 0.6, 0.2)}
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
         'worker': [(1.0, 0.50, 0.06)], 'customs': [(0.88, 0.88, 0.86)], 'nessa': [(0.13, 0.13, 0.15)]}


def keyed(walkers, ground_z, frame0, make, every=2):
    """Build each walker's body with `make(walker, index)` -> (root, legs, cards) and key it."""
    edit = bpy.context.preferences.edit
    was = edit.keyframe_new_interpolation_type
    try:
        _keyed(walkers, ground_z, frame0, make, every, edit, was)
    finally:
        edit.keyframe_new_interpolation_type = was


def _keyed(walkers, ground_z, frame0, make, every, edit, smooth):
    for n, w in enumerate(walkers):
        if not w.track:
            continue
        root, legs, cards = make(w, n)
        last = len(w.track) - 1
        for k, (f, x, y, h, ph, shown) in enumerate(w.track):
            if k % every and k != last and shown == w.track[max(0, k - 1)][5]:
                continue
            fr = frame0 + f
            root.location = (x, y, ground_z(x, y))
            root.rotation_euler = (0, 0, h)
            s = w.scale if shown else 1e-4
            root.scale = (s, s, s)
            root.keyframe_insert('location', frame=fr)
            root.keyframe_insert('rotation_euler', frame=fr)
            root.keyframe_insert('scale', frame=fr)
            moving = k > 0 and abs(ph - w.track[k - 1][4]) > 1e-4
            for hip, side in legs:
                hip.rotation_euler = (0, side * 0.45 * math.sin(2 * math.pi * ph) if moving else 0.0, 0)
                hip.keyframe_insert('rotation_euler', frame=fr)
            if cards:
                edit.keyframe_new_interpolation_type = 'CONSTANT'      # a flip book: no in-betweens
                for c in cards:
                    c['walk'] = float(int(ph * 8) % 8) if moving else 0.0
                    c.keyframe_insert('["walk"]', frame=fr)
                edit.keyframe_new_interpolation_type = smooth
        if w.track[0][0] > 0:                                     # hidden until spawned
            root.scale = (1e-4,) * 3
            root.keyframe_insert('scale', frame=frame0)


def body_3d(mesh, box, material):
    mats = {}

    def mat(name, rgb):
        if name not in mats:
            mats[name] = material(name, rgb, 0.9)
        return mats[name]

    def make(w, n):
        palette = CLOTH[w.role]
        cloth = mat('Walker %s %d' % (w.role, n % len(palette)), palette[n % len(palette)])
        root = link(bpy.data.objects.new(w.name, None))
        b = bmesh.new()
        box(b, (0.30, 0.50, 0.70), (0, 0, 1.15))
        mesh(w.name + ' body', b, cloth).parent = root
        hd = bmesh.new()
        box(hd, (0.24, 0.22, 0.26), (0.02, 0, 1.65))
        mesh(w.name + ' head', hd, mat('Walker head', (0.20, 0.14, 0.10))).parent = root
        if w.bag:
            bg = bmesh.new()
            box(bg, (0.45, 0.22, 0.35), (0, -0.38, 0.75))
            mesh(w.name + ' bag', bg, mat('Walker bag', (0.30, 0.24, 0.16))).parent = root
        legs = []
        for side in (-1, 1):
            hip = link(bpy.data.objects.new('%s hip %d' % (w.name, side), None))
            hip.parent = root
            hip.location = (0, side * 0.13, 0.85)
            lg = bmesh.new()
            box(lg, (0.16, 0.16, 0.85), (0, 0, -0.425))
            mesh('%s leg %d' % (w.name, side), lg, mat('Walker legs', (0.12, 0.11, 0.10))).parent = hip
            legs.append((hip, side))
        return root, legs, []
    return make


SPRITE_ROWS = ['family'] * 4 + ['worker', 'customs', 'nessa']


def body_sprite(mesh, sun_dir, out_dir):
    """Flat cards from the placeholder sheets in `c5_crowd_test.py`, one row per look: four
    family rows, then hi-vis, customs white, Nessa's dark coat. The frame comes from the card's
    'walk' property and the row from its 'row' property."""
    import c5_crowd_test as sheets
    sheets.VARIANTS = [*sheets.VARIANTS[:4], ((1.0, 0.50, 0.06), (0.16, 0.12, 0.10)),
                       ((0.88, 0.88, 0.86), (0.96, 0.59, 0.12)), ((0.13, 0.13, 0.15), (0.30, 0.22, 0.16))]
    out_dir.mkdir(parents=True, exist_ok=True)
    top = sheets.to_image('Crowd sheet top', sheets.top_sheet(), out_dir / 'crowd-top.png')
    side = sheets.to_image('Crowd sheet side', sheets.side_sheet(), out_dir / 'crowd-side.png')
    rows = len(sheets.VARIANTS)
    top_m, side_m = sprite_material('Crowd top', top, rows), sprite_material('Crowd shadow', side, rows)
    sun_xy = Vector((sun_dir.x, sun_dir.y)).normalized()

    def make(w, n):
        row = n % 4 if w.role == 'family' else SPRITE_ROWS.index(w.role)
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
        return root, [], [t, s]
    return make


def sprite_material(name, img, rows):
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
    u = op('DIVIDE', op('ADD', sep.outputs['X'], attr('walk')), 8.0)
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
