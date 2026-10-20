"""Gimbal hardware shared by every kit gun turret (owner, 2026-10-20: the turrets
should "look more like a useful gimballed turret").

A gun turret is two bearings. The house trains on a slewing ring seated in the
barbette; the gun elevates on trunnions across its cradle. The hand builders draw
each weapon's own house and barrels from its sprite; this module adds the
hardware that makes those two axes legible, measured off what the builder made,
so it fits every house without a builder knowing about it:

- traverse: a turntable disc the house stands on (a ring of bolts round its top,
  a dark race groove in the gap below), so a house narrower than its ring is
  seen standing on a rotating base rather than on the barbette's lip;
- open mounts (the trunnion stands clear of the house roof): two yoke cheeks
  rising from the roof to bearing blocks with caps, the cradle between them
  carrying the barrels, trunnion pins through the bearings, the feed entering
  one trunnion end and a sight head on the other, so both stay put at any
  elevation (a feed through the trunnion is how real mounts keep the belt from
  flexing);
- enclosed houses (the barrels leave through the house face): a curved mantlet
  struck about the trunnion axis, proud of the face, so the gun visibly pivots
  in its port; a bolted rim round it;
- every barrel: a recoil sleeve where it leaves the cradle or mantlet, a collar
  at its mouth and a slide rail beneath, fixed to the elevating mass, so the
  barrel (on its own 'recoil k' empty) slides back through it.

Everything is new objects ('<prefix> gimbal <key>' on the pivot, '<prefix> cradle
<key>' on the elevation empty); nothing the builder made is renamed or moved, and
no empty (pivot, elevation, recoil, muzzle anchor) changes. Sizes go with the
ring and the barrels, bolts in true metres. GIMBAL overrides a weapon's
defaults; 'off' leaves a weapon as its builder made it.
"""
import math
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

# Per weapon: kind 'auto' measures open or enclosed; 'open' / 'enclosed' force it;
# 'off' skips everything; 'feed' is 'belt' (ballistic chute), 'cable' (power), None.
# 'traverse' False skips the turntable (a round drum already reads as its own base).
GIMBAL = {
    'none': dict(kind='off'),
    'tachyonlance': dict(kind='off'), 'gravitonbeam': dict(kind='off'), 'taclaser': dict(kind='off'),
    'reaper': dict(kind='off'), 'pdlaser': dict(kind='off'),
    'ioncannon': dict(feed='cable'), 'railgun': dict(feed='cable', sleeves=False),
    'lightmg': dict(kind='open'),
    # The heavy autocannon's fingers are its elevating mass, hinged in the house
    # (_house_heavyac2): sleeves on the barrels only.
    'heavyac': dict(kind='fingers'),
    'squall': dict(kind='off'), 'hurricane': dict(kind='off'),
}
ENERGY = {'ioncannon', 'railgun'}


def spec(weapon, house):
    g = dict(kind='auto', feed='belt', traverse=True, sight=True)
    if house == 'box':
        g.update(kind='off')
    g.update(GIMBAL.get(weapon, {}))
    return g


def _local_tree(objs, inv):
    """One BVH of the given meshes in the frame `inv` maps world into."""
    verts, polys = [], []
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in objs:
        if ob.type != 'MESH':
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        m = inv @ ob.matrix_world
        base = len(verts)
        verts += [m @ v.co for v in me.vertices]
        polys += [tuple(base+i for i in p.vertices) for p in me.polygons]
        ev.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys) if polys else None


def _barrels(moving, objects, inv):
    """[(y, z, x0, x1, radius_at(x))] for each 'recoil k' empty's meshes, in the pivot frame."""
    out = []
    for e in moving:
        if ' recoil ' not in e.name:
            continue
        kids = [o for o in objects if o.parent == e and o.type == 'MESH']
        if not kids:
            continue
        c = inv @ e.matrix_world.translation
        pts = []
        for o in kids:
            m = inv @ o.matrix_world
            pts += [m @ v.co for v in o.data.vertices]
        xs = [p.x for p in pts]
        x0, x1 = min(xs), max(xs)

        def radius_at(x, pts=pts, c=c, x0=x0, x1=x1):
            w = .06*(x1-x0)+1e-3
            rr = [math.hypot(p.y-c.y, p.z-c.z) for p in pts if abs(p.x-x) <= w]
            return max(rr) if rr else max(math.hypot(p.y-c.y, p.z-c.z) for p in pts)
        out.append((c.y, c.z, x0, x1, radius_at))
    return out


def _arc_slab(mesh, c, R0, R1, a0, a1, y0, y1, n=12):
    """A curved slab about the Y axis through c: radii R0..R1, angles a0..a1 (radians,
    0 = +X, positive up toward +Z), from y0 to y1. A mantlet struck about the trunnions."""
    bm, base = mesh.bm, mesh.base
    cx, cy, cz = c
    rings = []
    for i in range(n+1):
        a = a0+(a1-a0)*i/n
        ca, sa = math.cos(a), math.sin(a)
        rings.append([bm.verts.new(base @ Vector((cx+R*ca, cy+y, cz+R*sa)))
                      for R, y in ((R0, y0), (R1, y0), (R1, y1), (R0, y1))])
    for a, b in zip(rings, rings[1:]):
        for j in range(4):
            bm.faces.new((a[j], a[(j+1) % 4], b[(j+1) % 4], b[j]))
    bm.faces.new(rings[0][::-1])
    bm.faces.new(rings[-1])


def fit(tk, prefix, pivot, elev, s, f, r, trunnion, parts, moving, mats):
    """Add the gimbal to a built turret. Returns the new objects."""
    g = spec(s['weapon'], s['house'])
    if g['kind'] == 'off' and not (s['house'] == 'box' and g['traverse']):
        return []
    u = f.u
    bpy.context.view_layer.update()
    inv = pivot.matrix_world.inverted()
    tx, ty, tz = trunnion
    house_objs = [o for o in parts if o.parent == pivot]
    tree = _local_tree(house_objs, inv)
    fixed = tk.Group(prefix, 'gimbal', mats, pivot)
    cradle = tk.Group(prefix, 'cradle', mats, elev, origin=trunnion)
    z0 = tk.GAP*r

    # --- traverse: the turntable disc the house stands on --------------------------
    if g['traverse']:
        R, h = .93*r, max(.05*r, .3*u)
        fixed['steel'].lathe([(R, 0), (R, .55*h), (R-.6*h, h), (0, h)], 64, (0, 0, z0-.02*r))
        fixed['dark'].ring((0, 0, z0-.035*r), R-.02*r, R+.012*r, .02*r, 'Z', n=64)    # race groove
        f.bolts(fixed['steel'], (0, 0, 0), R-.45*h, z0-.02*r+h, spacing=.7, r=.06, h=.05)
    if g['kind'] == 'off':
        return fixed.emit()

    barrels = _barrels(moving, parts, inv)
    if not barrels:
        return fixed.emit()
    ys = [b[0] for b in barrels]
    rb = max(b[4](b[2]+.25*(b[3]-b[2])) for b in barrels)        # barrel radius near the root
    span = max(ys)-min(ys)
    cw = span/2+rb*1.6+.05*r                                      # cradle half-width
    ch = max(rb*1.5, .1*r)                                        # cradle half-height
    # A gun group with a body of its own (a breech casing) is its own cradle: the yoke
    # stands outside it rather than through it.
    body = []
    for o in parts:
        if o.type == 'MESH' and o.parent == elev:
            m = inv @ o.matrix_world
            body += [m @ v.co for v in o.data.vertices]
    near = [p for p in body if abs(p.x-tx) < .2*r]
    own_body = False
    if near:
        bw = max(abs(p.y-ty) for p in near)
        bh = max(abs(p.z-tz) for p in near)
        if bw > cw*.8:
            own_body = True
            cw = bw+.02*r
            ch = max(ch, min(bh, .3*r))

    def hit_down(x, y):
        if tree is None:
            return None
        h = tree.ray_cast(Vector((x, y, tz+3*r)), Vector((0, 0, -1)), 6*r)
        return h[0].z if h[0] else None

    def face_x(y, z):
        """Where a ray from far ahead, running back along the line of fire, meets the house."""
        if tree is None:
            return None
        h = tree.ray_cast(Vector((tx+6*r, y, z)), Vector((-1, 0, 0)), 12*r)
        return h[0].x if h[0] else None

    def side_y(sy):
        """The house's side wall at the trunnion: a ray from abeam, running in toward the axis."""
        if tree is None:
            return None
        h = tree.ray_cast(Vector((tx, ty+sy*4*r, tz)), Vector((0, -sy, 0)), 4*r)
        return h[0].y if h[0] else None

    exits = [face_x(b[0], b[1]) for b in barrels]
    exits = [e for e in exits if e is not None and e > tx-.05*r]
    face = max(exits) if exits else None
    kind = g['kind']
    if kind == 'auto':
        # A barrel that leaves through the house face is carried in a mantlet; one that
        # stands clear of the house (nothing ahead of the trunnion on its line) in a yoke.
        kind = 'enclosed' if face is not None and face > tx-.02*r else 'open'
    if kind == 'enclosed' and face is None:
        kind = 'open'

    elev['gimbal'] = kind
    if kind == 'fingers':
        front = max([p.x for p in body if abs(p.z-tz) < ch*2] or [tx])
    if __debug__ and False:
        print('GIMBAL-DEBUG', s['weapon'], 'tx', round(tx/r, 3), 'tz', round(tz/r, 3), 'face', face and round(face/r, 3), 'raw', [e and round(e/r, 3) for e in [face_x(b[0], b[1]) for b in barrels]], 'x0', [round(b[2]/r, 3) for b in barrels], 'rb', round(rb/r, 3))
    if kind == 'fingers':
        pass
    elif kind == 'enclosed':
        # A mantlet struck about the trunnion axis, proud of the face: the gun pivots in its port.
        # Sunk a little into the face so it reads as seated in its port; a face far out
        # is a fairing, not the port. A cast shield, thick enough to read at range.
        R0 = max(min(face-tx, .55*r)-.03*r, .1*r)
        th = max(.1*r, .4*u)
        H = max(ch+.08*r, .35*(tz-z0))
        half = min(math.atan2(H, R0), math.radians(55))
        cw = cw+.03*r
        m = cradle['plate#mantlet']
        _arc_slab(m, trunnion, R0, R0+th, -half, half, -cw, cw)
        rim = cradle['steel']
        for a in (-half, half):
            rim.box((tx+(R0+th*.5)*math.cos(a), ty, tz+(R0+th*.5)*math.sin(a)), (th*1.3, 2*cw+.03*r, .025*r),
                    rot=Matrix.Rotation(-a, 4, 'Y'))
        for sy in (1, -1):
            rim.box((tx+R0+th*.5, ty+sy*cw, tz), (th*1.3, .025*r, 2*(R0+th)*math.sin(half)))
        front = tx+R0+th
        for b in barrels:                                          # the barrel's port in the mantlet
            m.cut().cyl((front-th/2, b[0], b[1]), b[4](front)*1.08, th*3, 'X', n=20)
            rp = b[4](front)
            if rp < .22*r:                                         # a bolted collar round the port
                cradle['steel'].ring((front+.012*r, b[0], b[1]), rp*1.1, rp*1.55+.02*r, .03*r, 'X', n=20)
        # The trunnions show on the house's cheeks: a bearing housing on each side wall at
        # the elevation axis, bolted round, with the pin's end cap turning in it (a lug on
        # the cap, so its turn reads).
        for sy in (1, -1):
            wall = side_y(sy)
            if wall is None or abs(wall-ty) < cw*.6:
                continue
            br = min(max(.12*r, ch*.9), .45*(tz-z0))
            fixed['steel#bearing'].cyl((tx, wall+sy*.02*r, tz), br, .06*r, 'Y', n=24, bevel=.012*r)
            for kk in range(8):
                a = 2*math.pi*(kk+.5)/8
                fixed['steel'].cyl((tx+br*.8*math.cos(a), wall+sy*.055*r, tz+br*.8*math.sin(a)),
                                   max(.012*r, .06*u), .02*r, 'Y', n=6)
            cradle['metal#pin'].cyl((tx, wall+sy*.065*r, tz), br*.5, .05*r, 'Y', n=20, bevel=.008*r)
            cradle['metal#pin'].box((tx+br*.42, wall+sy*.08*r, tz), (br*.35, .03*r, br*.18))
    else:
        # Open mount: yoke cheeks from the roof to the bearings, the cradle between them.
        gap = .03*r
        cth = max(.09*r, .35*u)                                   # cheek thickness
        boss = ch+.04*r
        for sy in (1, -1):
            yc = ty+sy*(cw+gap+cth/2)
            floor = hit_down(tx, yc)
            floor = z0 if floor is None or floor > tz-boss*.6 else floor
            cheek = fixed['plate#yoke']
            hgt = tz-floor
            cheek.box((tx, yc, floor+hgt/2), (2*boss*.95, cth, hgt), .02*r)
            cheek.cyl((tx, yc, tz), boss, cth, 'Y', n=24, bevel=.015*r)
            # Bearing cap and its bolts on the outer face, a foot flange on the roof.
            fixed['steel'].cyl((tx, yc+sy*cth*.55, tz), boss*.62, cth*.25, 'Y', n=20, bevel=.01*r)
            for k in range(6):
                a = 2*math.pi*k/6
                fixed['steel'].cyl((tx+boss*.8*math.cos(a), yc+sy*cth*.52, tz+boss*.8*math.sin(a)),
                                   max(.012*r, .07*u), cth*.12, 'Y', n=6)
            fixed['steel'].box((tx, yc, floor+.015*r), (2*boss*1.25, cth*1.6, .03*r), .008*r)
            # Gusset behind the cheek, so the yoke reads as a frame carrying a load.
            gl = min(hgt, 1.6*boss)
            if gl > .05*r:
                fixed['plate'].box((tx-boss*1.1, yc, floor+gl*.35), (boss*.9, cth*.7, gl*.7), .01*r,
                                   rot=Matrix.Rotation(math.radians(-35), 4, 'Y'))
            # Trunnion pin through the bearing, turning with the gun.
            cradle['metal#pin'].cyl((tx, ty+sy*(cw+gap/2+cth*.6), tz), boss*.42, gap+cth*1.3, 'Y', n=20)
        # The cradle: a box round the barrel roots between the cheeks, with side plates.
        x_back = min(min(b[2] for b in barrels), tx-ch)
        x_frt = tx+max(2.4*ch, .28*r)
        cl = x_frt-x_back
        if not own_body:
            cradle['steel#cradle'].box(((x_back+x_frt)/2, ty, tz), (cl, 2*cw, 2*ch*.9), .025*r)
            for b in barrels:
                cradle['steel#cradle'].cut().cyl((x_frt-cl*.3, b[0], b[1]), b[4](x_frt)*1.12, cl*.7, 'X', n=20)
        else:
            front = max([p.x for p in body if abs(p.z-tz) < ch and abs(p.y-ty) < cw] or [x_frt])
        for sy in (1, -1):
            cradle['plate'].box((tx, ty+sy*(cw-.006*r), tz), (2*ch*.95, .02*r, 2*ch*.8), .006*r)
            f.bolt_row(cradle['steel'], (tx-ch*.8, ty+sy*cw, tz+ch*.7), (tx+ch*.8, ty+sy*cw, tz+ch*.7), axis='Y')
        if not own_body:
            front = x_frt
        # Feed into one trunnion end; sight on the other.
        fy = ty+(cw+gap+cth)
        if g['feed'] == 'belt':
            box_c = Vector((tx-1.3*boss, fy+.12*r, z0+.16*r))
            if hit_down(box_c.x, box_c.y) is not None:
                box_c.z = max(box_c.z, hit_down(box_c.x, box_c.y)+.1*r)
            fixed['paint'].box(box_c, (.38*r, .2*r, .2*r), .02*r)                   # ready-use box
            fixed['steel'].box(box_c+Vector((0, 0, .105*r)), (.34*r, .17*r, .015*r))  # lid
            # The chute: segments climbing from the box lid into the trunnion's end.
            a = box_c+Vector((.12*r, 0, .11*r))
            b = Vector((tx, fy+.04*r, tz))
            n = 5
            for i in range(n):
                p, q = a.lerp(b, i/n), a.lerp(b, (i+1)/n)
                q = q+Vector((0, .03*r*math.sin(math.pi*(i+1)/n), 0))
                d = q-p
                rot = Vector((1, 0, 0)).rotation_difference(d.normalized()).to_matrix().to_4x4()
                fixed['dark'].box((p+q)/2, (d.length*1.02, .07*r, .05*r), rot=rot)
                fixed['steel'].box(q, (.03*r, .09*r, .07*r), rot=rot)
        elif g['feed'] == 'cable':
            tk._cable(fixed['cable'], [(tx-1.6*boss, fy-.05*r, z0+.06*r), (tx-1.4*boss, fy+.15*r, z0+.2*r),
                                       (tx-.2*boss, fy+.12*r, tz), (tx, fy+.02*r, tz)], .04*r, 1, 0)
            fixed['steel'].cyl((tx, fy+.01*r, tz), .06*r, .04*r, 'Y', n=16)
        if g['sight']:
            sy_ = ty-(cw+gap+cth+.06*r)
            cradle['steel'].cyl((tx, sy_+.03*r, tz), .04*r, .08*r, 'Y', n=12)
            cradle['plate'].box((tx+.02*r, sy_-.04*r, tz+.03*r), (.2*r, .09*r, .1*r), .01*r)
            cradle['glass'].box((tx+.122*r, sy_-.04*r, tz+.035*r), (.01*r, .07*r, .05*r))

    # --- every barrel: a recoil sleeve where it leaves the cradle or mantlet ---------
    for y, z, x0, x1, rad in (barrels if g.get('sleeves', True) else []):
        # Sized to the tube just ahead (not a breech jacket the front may fall on), and
        # left off a barrel whose tube is already as fat as a sleeve would be.
        span_ = [front+.05*r+i*.04*r for i in range(8)]
        rr = min(rad(xx) for xx in span_ if xx < x1) if any(xx < x1 for xx in span_) else 0
        if rr <= 0 or rr > .22*r:
            continue
        L = min(max(.12*r, 1.8*rr), .26*r)
        sl = cradle['steel']
        sl.cyl((front+L/2, y, z), rr*1.18+.008*r, L, 'X', n=20, bevel=.008*r)
        sl.ring((front+L-.015*r, y, z), rr*1.02, rr*1.3+.01*r, .03*r, 'X', n=20)
        cradle['dark'].ring((front+L*.45, y, z), rr*1.17+.008*r, rr*1.22+.01*r, .02*r, 'X', n=20)
        if kind == 'enclosed':
            cradle['metal'].box((front+L*.5, y, z-rr*1.18), (L*.9, rr*.6, rr*.3))   # slide rail
    return fixed.emit()+cradle.emit()
