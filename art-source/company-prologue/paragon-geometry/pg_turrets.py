"""Weapon mounts of the Paragon: 21 turrets on pivots and the two bow hardpoints.

Every slot is read from paragon.ship and placed at its location, resting at its
slot angle; the build fails if the installed file disagrees. A turret is a
well cut into the hull along the local surface normal (`Weapon <slot> well`,
lined dark), a fixed barbette in it (`Weapon <slot> barbette`) and a pivot empty (`Weapon <slot>
pivot`) at the traverse gap, parenting the trainable parts (housing, emitters)
and a `Weapon <slot> muzzle` empty per barrel. Rotate the pivot about its
local Z to train. Hardpoints are fixed: `Weapon <slot> hardpoint` (anchor),
housing, emitter and muzzle.

The turrets are placeholders (owner, pass 1): a dark well-sized barbette and a
puck with a stub showing the resting direction, until real mounts are designed.
The two capital mounts in the prow bays keep their housing and emitter. Barbette radii are read off the sprite's turret
wells (large 14, medium 10.5, small 6.5 units).
"""
import math
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree
import pg_kit as K
import pg_hull

SLOTS = [  # id, location, angle, arc, size, type, mount
    ('WS 001', (163, 16), 0, 5, 'LARGE', 'ENERGY', 'HARDPOINT'),
    ('WS 002', (163, -16), 0, 5, 'LARGE', 'ENERGY', 'HARDPOINT'),
    ('WS 003', (3, 99), 80, 220, 'LARGE', 'ENERGY', 'TURRET'),
    ('WS 004', (3, -99), -80, 220, 'LARGE', 'ENERGY', 'TURRET'),
    ('WS 005', (126, 40), 20, 210, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 006', (126, -40), -20, 210, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 007', (51, 90), 70, 210, 'MEDIUM', 'UNIVERSAL', 'TURRET'),
    ('WS 008', (51, -90), -70, 210, 'MEDIUM', 'UNIVERSAL', 'TURRET'),
    ('WS 009', (-143, 62), 170, 150, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 010', (-143, -62), 190, 150, 'MEDIUM', 'ENERGY', 'TURRET'),
    ('WS 011', (106, 69), 50, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 012', (78, 55), 50, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 013', (-40, 112), 100, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 014', (-52, 81), 130, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 015', (-92, 104), 90, 150, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 016', (-145, 121), 120, 250, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 017', (106, -69), -40, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 018', (78, -55), -50, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 019', (-40, -112), 260, 210, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 020', (-52, -81), 230, 360, 'SMALL', 'MISSILE', 'TURRET'),
    ('WS 021', (-92, -104), -90, 150, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 022', (-145, -121), 240, 250, 'SMALL', 'ENERGY', 'TURRET'),
    ('WS 023', (-150, 0), 180, 150, 'SMALL', 'ENERGY', 'TURRET'),
]
# Fittings of the Elite variant, for the record (the model shows the mount, not the weapon).
ELITE = {'WS 001': 'autopulse', 'WS 002': 'autopulse', 'WS 003': 'tachyonlance', 'WS 004': 'tachyonlance',
         'WS 005': 'gravitonbeam', 'WS 006': 'gravitonbeam', 'WS 009': 'gravitonbeam', 'WS 010': 'gravitonbeam',
         'WS 012': 'reaper', 'WS 014': 'reaper', 'WS 018': 'reaper', 'WS 020': 'reaper',
         'WS 013': 'taclaser', 'WS 015': 'taclaser', 'WS 019': 'taclaser', 'WS 021': 'taclaser'}

# size: barbette radius, collar height, housing radius, housing height, emitter length, emitter radius, emitters
SPEC = {
    'LARGE':  dict(r=16.0, collar=2.6, hr=10.5, hh=6.0, length=34.0, er=1.5, n=1),
    'MEDIUM': dict(r=10.5, collar=2.2, hr=7.6, hh=4.6, length=22.0, er=1.1, n=2),
    'SMALL':  dict(r=6.5, collar=1.6, hr=4.6, hh=3.0, length=12.0, er=.7, n=1),
}
RECORDS = []


def housing_profile(hr, hh):
    """A low smooth dome: flat foot, rounded shoulder, flattened crown."""
    prof = [(0, 0)]
    for k in range(9):
        a = math.radians(90*k/8)
        prof.append((hr*(.62+.38*math.cos(a)) if k else hr, hh*math.sin(a)*.92 if k else 0))
    prof.append((0, hh))
    return prof


def seat(kit, x, y, r):
    """Where a turret sits: the plane fitted through the hull surface on a ring of radius r about (x, y).

    Returns (centre, normal). The well is cut along that normal, so a turret on
    a glacis or the belt's slope leans with the plate it stands in rather than
    standing upright out of it (owner, pass 1)."""
    pts = []
    for k in range(10):
        a = 2*math.pi*k/10
        hit = kit.surface(x+r*math.cos(a), y+r*math.sin(a))
        if hit:
            pts.append(hit[0])
    centre = kit.surface(x, y)[0]
    if len(pts) < 3:
        return centre, Vector((0, 0, 1))
    # Least-squares plane z = a x + b y + c through the ring.
    import numpy as np
    A = np.array([[p.x, p.y, 1.0] for p in pts])
    zz = np.array([p.z for p in pts])
    (ga, gb, gc), *_ = np.linalg.lstsq(A, zz, rcond=None)
    n = Vector((-ga, -gb, 1.0)).normalized()
    return Vector((x, y, ga*x+gb*y+gc)), n


def frame(centre, normal):
    return Matrix.Translation(centre) @ Vector((0, 0, 1)).rotation_difference(normal).to_matrix().to_4x4()


_TREES = {}


def near_hull(kit, point, reach):
    """Hull objects whose surface passes within reach of point."""
    dg = bpy.context.evaluated_depsgraph_get()
    out = []
    for ob in kit.hull+kit.detail:
        if ob.name not in bpy.data.objects:
            continue
        key = (ob.name, len(ob.data.polygons))
        if key not in _TREES:
            _TREES.clear() if len(_TREES) > 64 else None
            _TREES[key] = BVHTree.FromObject(ob, dg)
        loc, _, _, d = _TREES[key].find_nearest(ob.matrix_world.inverted() @ point)
        if loc is not None and d < reach:
            out.append(ob)
    return out


# The dimple round each well (the sprite's concentric rings): a flat terrace at
# the well rim out to DIMPLE x the well radius, then a chamfer back up to the
# plate. It clears the gun's near field and keeps the mount low in the hull.
DIMPLE = {'LARGE': 1.75, 'MEDIUM': 1.8, 'SMALL': 1.9}
DIMPLE_CHAMFER_DEG = 32.0


def _cut(kit, ob, profile, M):
    cut = K.revolve(kit, 'Temporary well cutter', profile, ob.material_slots[0].material, segments=48, smooth=False)
    cut.matrix_world = M
    K.boolean(ob, cut)
    _TREES.pop((ob.name, len(ob.data.polygons)), None)


def well(kit, sid, spec, M, size):
    """Dish the plate round the turret, then cut its well along the frame's Z and line it dark."""
    R, D = spec['r']+1.0, spec['collar']+1.6
    Rt = R*DIMPLE[size]
    rise = math.tan(math.radians(DIMPLE_CHAMFER_DEG))
    dish = [(0, 0), (Rt, 0), (Rt+30/rise, 30), (0, 30)]
    centre = M.translation
    for ob in near_hull(kit, centre, Rt+30/rise):
        _cut(kit, ob, dish, M)
    for ob in near_hull(kit, centre, R+4):
        _cut(kit, ob, [(0, -D), (R, -D), (R, 30), (0, 30)], M)
    liner = K.revolve_loop(kit, f'Weapon {sid} well', [(R-.6, -D-.6), (R, -D-.6), (R, .15), (R-.6, .15)], kit.tech,
                           segments=48, smooth=False)
    floor = K.revolve(kit, f'Weapon {sid} well floor', [(0, -D-.8), (R-.3, -D-.8), (R-.3, -D), (0, -D)], kit.tech,
                      segments=48, smooth=False)
    liner.matrix_world = M
    floor.matrix_world = M
    return D


def barbette(kit, sid, spec, M, D):
    r, c = spec['r'], spec['collar']
    prof = [(0, 0), (r, 0), (r, c*.55), (r*.93, c), (0, c)]
    b = K.revolve(kit, f'Weapon {sid} barbette', prof, kit.tech, segments=48, smooth=False)
    b.matrix_world = M @ Matrix.Translation((0, 0, -D))
    return -D+c


def emitter(kit, name, start, length, radius, mat_tip):
    """A Tri-Tachyon emitter rod along local +X from start: a tapered sleeve, the rod, a bright tip collar."""
    sx, sy, sz = start
    sleeve = kit.cylinder(name+' sleeve', (sx+length*.18, sy, sz), radius*1.9, length*.36, kit.pale, axis='X',
                          vertices=24, bevel=0)
    rod = kit.cylinder(name+' rod', (sx+length*.6, sy, sz), radius, length*.8, kit.steel, axis='X',
                       vertices=16, bevel=0)
    tip = kit.cylinder(name+' tip collar', (sx+length*.97, sy, sz), radius*1.45, length*.06, mat_tip, axis='X',
                       vertices=16, bevel=0)
    return [sleeve, rod, tip], (sx+length, sy, sz)


def parent(objs, pivot):
    for o in objs:
        mw = o.matrix_world.copy()
        o.parent = pivot
        o.matrix_parent_inverse = pivot.matrix_world.inverted()
        o.matrix_world = mw


def turret(kit, sid, loc, angle, arc, size, typ):
    spec = SPEC[size]
    x, y = loc
    centre, normal = seat(kit, x, y, spec['r']+1.0)
    M = frame(centre, normal)
    D = well(kit, sid, spec, M, size)
    ztop = barbette(kit, sid, spec, M, D)
    # The pivot sits at the traverse gap on the barbette's top face, its Z along
    # the well's axis; rotate it about its local Z to train. Trainable parts are
    # built facing +X about the origin and parented to it.
    pivot = kit.empty(f'Weapon {sid} pivot', (0, 0, 0), 0, 'ARROWS', spec['r'])
    pivot['slot'] = sid
    pivot['angle_deg'] = angle
    pivot['arc_deg'] = arc
    pivot['size'] = size
    pivot['type'] = typ
    # Placeholder (owner, pass 1: the turrets are scrap): a dark puck showing the
    # pivot, size and resting direction, sitting in its well. Real mounts replace
    # these parts; the pivot, muzzle anchors and their names stay.
    hr = spec['hr']
    house = K.revolve(kit, f'Weapon {sid} placeholder', [(0, 0), (hr, 0), (hr*.8, 2.0), (0, 2.0)], kit.slate,
                      segments=24, smooth=False)
    stub = kit.box(f'Weapon {sid} placeholder stub', (hr*.9, 0, 1.0), (hr*.9, hr*.22, 1.2), kit.slate, 0)
    parts = [house, stub]
    muzzles = [(hr*1.35, 0, 1.0)] if typ != 'MISSILE' else [(hr*.5, j*hr*.3, 2.0) for j in (-1, 1)]
    mz = [kit.empty(f'Weapon {sid} muzzle {j+1}' if len(muzzles) > 1 else f'Weapon {sid} muzzle', p, 0,
                    'SINGLE_ARROW', 3) for j, p in enumerate(muzzles)]
    bpy.context.view_layer.update()
    parent(parts+mz, pivot)
    pivot.matrix_world = M @ Matrix.Translation((0, 0, ztop)) @ Matrix.Rotation(math.radians(angle), 4, 'Z')
    bpy.context.view_layer.update()
    p = pivot.matrix_world.translation
    RECORDS.append({'id': sid, 'mount': 'TURRET', 'size': size, 'type': typ, 'location': [x, y],
                    'angle_deg': angle, 'arc_deg': arc, 'pivot': pivot.name,
                    'pivot_location': [round(v, 2) for v in p], 'train_axis': [round(v, 4) for v in normal],
                    'well_depth': round(D, 2), 'barbette': base_name(sid), 'muzzles': [e.name for e in mz],
                    'placeholder': True, 'elite_variant': ELITE.get(sid)})
    return pivot


def base_name(sid):
    return f'Weapon {sid} barbette'


def hardpoint(kit, sid, loc, angle, arc, size, typ):
    """A fixed bow hardpoint: a slot in the prow face with the emitter running out of it."""
    x, y = loc
    z = pg_hull.BAY['z']  # recessed in its bay in the prow (pg_hull.hardpoint_bays)
    anchor = kit.empty(f'Weapon {sid} hardpoint', (x, y, z), angle, 'ARROWS', 6)
    anchor['slot'] = sid
    anchor['angle_deg'] = angle
    anchor['arc_deg'] = arc
    # The capital mount fills its bay: a breech block at the back, a heavy
    # cradle, and a thick emitter whose tip stops just inside the prow face.
    house = kit.box(f'Weapon {sid} housing', (x-23, y, z), (24, 17.0, 17.0), kit.slate, 1.2)
    cradle = kit.box(f'Weapon {sid} cradle', (x-8, y, z), (10, 13.0, 13.0), kit.steel, .8)
    objs, tip = emitter(kit, f'Weapon {sid} emitter', (x-12, y, z), 19.0, 3.2, kit.glow_white)
    objs.append(cradle)
    mz = kit.empty(f'Weapon {sid} muzzle', tip, angle, 'SINGLE_ARROW', 3)
    parent([house]+objs+[mz], anchor)
    RECORDS.append({'id': sid, 'mount': 'HARDPOINT', 'size': size, 'type': typ, 'location': [x, y],
                    'angle_deg': angle, 'arc_deg': arc, 'anchor': anchor.name, 'anchor_z': round(z, 2),
                    'muzzles': [mz.name], 'elite_variant': ELITE.get(sid)})
    return anchor


def build(kit):
    RECORDS.clear()
    for sid, loc, angle, arc, size, typ, mount in SLOTS:
        if mount == 'HARDPOINT':
            hardpoint(kit, sid, loc, angle, arc, size, typ)
        else:
            turret(kit, sid, loc, angle, arc, size, typ)


def records():
    return list(RECORDS)
