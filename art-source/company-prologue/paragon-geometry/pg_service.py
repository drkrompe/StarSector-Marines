"""Service fields: the places the hex plating is left off, where the hatches and machinery are.

Owner (pass 3): vary the plate sizes and leave uncovered areas for the
faction's hatches and machinery. A field is a shallow recess cut through the
plating to the substrate (`Tri-Tachyon substrate`), with its contents standing
on it:
- hatches: rounded-rectangle ceramic hatches with a glowing seam round them, a
  split line and a status light, sized for people in true metres;
- iris hatches: round, with radial leaf lines and a glow ring;
- vents: banks of dark slots; fins: a small radiator block.
Fields go where a crew needs to get out onto the hull: the main ring's crown
between the turrets, the command deck's after end and the pod tops. A field that would meet a turret's dimple is left out (it prints).
"""
import math
import bpy
from mathutils import Vector
import pg_kit as K
import pg_hull
import pg_turrets

# name, centre (X, Y) port side, length x width (m), bearing (deg; None = tangent to the bore), contents
FIELDS = [
    ('ring crown fore', ('ring', 8), (13.0, 7.0), None, ['hatch', 'vents', 'hatch']),
    ('ring crown aft quarter', ('ring', 150), (13.0, 7.0), None, ['hatch', 'fins', 'vents']),
    ('pod top', (-156, 92), (18.0, 9.0), 0, ['hatch', 'vents', 'fins', 'hatch']),
    ('deck aft', (-92, 0), (9.0, 5.0), 0, ['iris', 'hatch']),
]
RING_FIELD_R = 86.0  # the middle of the main ring's crown (r 78-96)
FIELD_DEPTH = .5
HATCH_M = (2.6, 1.6)
IRIS_M = 1.4
RECORDS = []


def _rounded_rect(cx, cy, l, w, bearing, r, steps=4):
    a = math.radians(bearing)
    ux, uy, vx, vy = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
    pts = []
    for (sx, sy), a0 in (((1, 1), 0), ((-1, 1), 90), ((-1, -1), 180), ((1, -1), 270)):
        ox, oy = sx*(l/2-r), sy*(w/2-r)
        for k in range(steps+1):
            t = math.radians(a0+90*k/steps)
            px, py = ox+r*math.cos(t), oy+r*math.sin(t)
            pts.append((cx+ux*px+vx*py, cy+uy*px+vy*py))
    return pts


def _prism(kit, name, plan, z0, z1, mat):
    plan = K.ccw(plan)
    n = len(plan)
    verts = [(x, y, z0) for x, y in plan]+[(x, y, z1) for x, y in plan]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, 0, False)


def _centre(spec, side):
    if spec[0] == 'ring':
        b = math.radians(spec[1])
        cx, cy = pg_hull.BORE_C
        return (cx+RING_FIELD_R*math.cos(b), (cy+RING_FIELD_R*math.sin(b))*side), spec[1]*side+90
    cx, cy = pg_hull.BORE_C
    return (spec[0], spec[1]*side), math.degrees(math.atan2(spec[1]*side-cy, spec[0]-cx))+90


def _clear_of_turrets(x, y, l, w, bearing):
    """The first turret whose dimple reaches the field's rectangle, or None."""
    a = math.radians(bearing)
    ux, uy, vx, vy = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
    for sid, loc, angle, arc, size, typ, mount in pg_turrets.SLOTS:
        if mount != 'TURRET':
            continue
        R = (pg_turrets.SPEC[size]['r']+1.0)*pg_turrets.DIMPLE[size]+1.0
        dx, dy = loc[0]-x, loc[1]-y
        pu, pv = dx*ux+dy*uy, dx*vx+dy*vy
        qu, qv = max(abs(pu)-l/2, 0.0), max(abs(pv)-w/2, 0.0)
        if math.hypot(qu, qv) < R:
            return sid
    return None


def build(kit):
    m = kit.mpu
    for side, label in ((1, 'port'), (-1, 'starboard')):
        for name, spec, (lm, wm), bearing, contents in FIELDS:
            if name == 'deck aft' and side < 0:
                continue  # on the centreline: one field
            (x, y), tangent = _centre(spec, side)
            brg = tangent if bearing is None else bearing
            l, w = lm/m, wm/m
            clash = _clear_of_turrets(x, y, l, w, brg)
            if clash:
                print(f'SERVICE FIELD {label} {name} skipped: meets the dimple of {clash}')
                continue
            # The field follows the plate: its floor is parallel to the plane fitted to the
            # surface under it, FIELD_DEPTH down, and its cut walls show the substrate.
            if kit.surface(x, y) is None:
                print(f'SERVICE FIELD {label} {name} skipped: no hull under it')
                continue
            centre, normal = pg_turrets.seat(kit, x, y, min(l, w)/2)
            M = pg_turrets.frame(centre, normal)
            Minv = M.inverted()
            plan = _rounded_rect(0, 0, l, w, brg, min(l, w)*.18)
            zs = []
            for px, py in plan:
                hit = kit.surface(*(M @ Vector((px, py, 0)))[:2])
                if hit:
                    zs.append((Minv @ hit[0]).z)
            floor = min(zs+[0.0])-FIELD_DEPTH
            for ob in kit.hull:
                lo = [min((ob.matrix_world @ Vector(c))[i] for c in ob.bound_box) for i in range(3)]
                hi = [max((ob.matrix_world @ Vector(c))[i] for c in ob.bound_box) for i in range(3)]
                if hi[0] < x-l or lo[0] > x+l or hi[1] < y-l or lo[1] > y+l or hi[2] < centre.z-l:
                    continue
                cut = _prism(kit, 'Temporary field cutter', plan, floor, 60.0, kit.substrate)
                cut.matrix_world = M
                K.boolean(ob, cut)
            fname = f'Service {label} {name}'
            parts = [_prism(kit, fname+' floor', plan, floor-1.0, floor+.04, kit.substrate)]
            parts += _contents(kit, fname, 0.0, 0.0, floor+.04, brg, l, w, contents, m)
            for o in parts:
                o.matrix_world = M @ o.matrix_world
            kit.detail += parts
            RECORDS.append({'name': fname, 'centre': [round(x, 1), round(y, 1)], 'size_m': [lm, wm],
                            'normal': [round(v, 3) for v in normal], 'contents': contents})


def _contents(kit, fname, x, y, z, brg, l, w, contents, m):
    a = math.radians(brg)
    ux, uy = math.cos(a), math.sin(a)
    n = len(contents)
    made = []
    for i, kind in enumerate(contents):
        off = (i-(n-1)/2)*(l/n)
        cx, cy = x+ux*off, y+uy*off
        tag = f'{fname} {kind} {i+1}'
        if kind == 'hatch':
            hl, hw = HATCH_M[1]/m, HATCH_M[0]/m   # long side across the field
            glow = _prism(kit, tag+' seam', _rounded_rect(cx, cy, hl+.5, hw+.5, brg, .5), z, z+.12, kit.glow)
            plate = _prism(kit, tag+' plate', _rounded_rect(cx, cy, hl, hw, brg, .45), z, z+.3, kit.hatch)
            split = kit.box(tag+' split', (cx, cy, z+.31), (.18, hw-.6, .05), kit.tech, 0)
            split.rotation_euler = (0, 0, a)
            lamp = kit.box(tag+' status light', (cx+ux*(hl/2+.6), cy+uy*(hl/2+.6), z+.15), (.35, .35, .3),
                           kit.glow_white, 0)
            made += [glow, plate, split, lamp]
        elif kind == 'iris':
            r = IRIS_M/m
            ring = K.revolve_loop(kit, tag+' ring', [(r, 0), (r+.35, 0), (r+.35, .15), (r, .15)], kit.glow,
                                  center=(cx, cy, z), segments=32, smooth=False)
            disc = K.revolve(kit, tag+' disc', [(0, 0), (r, 0), (r, .22), (r*.3, .32), (0, .32)], kit.hatch,
                             center=(cx, cy, z), segments=32, smooth=False)
            made += [ring, disc]
            for k in range(6):
                t = 2*math.pi*k/6+a
                g = kit.box(f'{tag} leaf {k+1}', (cx+math.cos(t)*r*.5, cy+math.sin(t)*r*.5, z+.33), (r*.95, .08, .04),
                            kit.tech, 0)
                g.rotation_euler = (0, 0, t+math.radians(25))
                made.append(g)
        elif kind == 'vents':
            for k in range(5):
                o = (k-2)*.9
                v = kit.box(f'{tag} slot {k+1}', (cx-uy*o, cy+ux*o, z+.05), (w*.5, .45, .12), kit.tech, 0)
                v.rotation_euler = (0, 0, a)
                made.append(v)
        elif kind == 'fins':
            for k in range(6):
                o = (k-2.5)*.7
                f = kit.box(f'{tag} fin {k+1}', (cx+ux*o, cy+uy*o, z+.6), (.22, w*.55, 1.2), kit.slate, 0)
                f.rotation_euler = (0, 0, a)
                made.append(f)
    return made
