"""Seat the Borer on every Shepherd launch cradle and measure the fit: no render.

  blender -b --factory-startup --python borer_cradle.py -- --shepherd <shepherd.blend> \
      [--borer borer/borer-geometry.blend] [--collection "BORER | geometry" (the export: "BORER")] [--out borer/cradle-check.json]

Appends the Shepherd's collection (the accepted build, builds/shepherd/<commit>/shepherd.blend, copied
local first) and the Borer's, and for each 'Drone bay WS 003 launch point <i>' places the Borer the way
a shot does: its 'Borer cradle seat' on the launch point, its root scaled by the Borer's
metres_per_unit over the Shepherd's, heading the slot angle. It then reports, per station:
  - overlaps: Borer parts crossing Shepherd parts (BVH overlap with the Borer lifted 1 mm, so the pads
    standing on the deck do not count), which must be none;
  - pads: every pad sole's height above the cradle deck under it (ray cast down), which must be 0;
  - socket: the gap from the belly charging socket's contact face to the cradle's charging contact;
  - clearances: the nearest Shepherd geometry to the Borer that is not its own cradle, and the
    corner chocks' distance from the Borer's plan outline;
  - lift: whether the Borer rises straight up (+Z, the bay's launch direction) to the mouth without
    touching anything (swept in 0.25 m steps).
Writes the JSON report (--out) and prints CRADLE lines. Exit status 1 when any station fails.
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []


def arg(name, default=None):
    return argv[argv.index(name)+1] if name in argv else default


SHEP = Path(arg('--shepherd')).resolve()
BORER = Path(arg('--borer', str(HERE/'borer'/'borer-geometry.blend'))).resolve()
BCOLL = arg('--collection', 'BORER | geometry')
OUT = Path(arg('--out', str(HERE/'borer'/'cradle-check.json'))).resolve()
GEOM = json.loads((HERE/'borer'/'geometry.json').read_text(encoding='utf-8'))
SHEP_MPU = .78

bpy.ops.wm.read_factory_settings(use_empty=True)
for path, names in ((SHEP, ['SHEPHERD']), (BORER, [BCOLL])):
    with bpy.data.libraries.load(str(path), link=False) as (src, dst):
        dst.collections = [n for n in names if n in src.collections]
    for c in dst.collections:
        bpy.context.scene.collection.children.link(c)
shep = bpy.data.collections['SHEPHERD']
bor = bpy.data.collections[BCOLL]
broot = next(o for o in bor.all_objects if o.type == 'EMPTY' and o.parent is None)
mpu = broot.get('metres_per_unit', GEOM['metres_per_unit'])
# The geometry names it 'Borer cradle seat'; the cinematic export re-declares it 'Anchor cradle seat'.
seat = next(bor.all_objects[n] for n in ('Borer cradle seat', 'Anchor cradle seat') if n in bor.all_objects)
dg = bpy.context.evaluated_depsgraph_get()


def tris(objs, mw_extra=Matrix()):
    v, f = [], []
    for ob in objs:
        if ob.type != 'MESH' or ob.hide_render:
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        m = mw_extra @ ev.matrix_world
        k = len(v)
        v += [m @ x.co for x in me.vertices]
        f += [tuple(k+i for i in t.vertices) for t in me.loop_triangles]
        ev.to_mesh_clear()
    return v, f


def per_object(objs, mw_extra):
    out = {}
    for ob in objs:
        v, f = tris([ob], mw_extra)
        if f:
            out[ob.name] = (BVHTree.FromPolygons(v, f), v)
    return out


shep_parts = per_object(list(shep.all_objects), Matrix())
shep_all = BVHTree.FromPolygons(*tris(list(shep.all_objects)))
seat_local = seat.matrix_world.translation.copy()       # Borer units, root at the origin
k = mpu/SHEP_MPU
report = {'borer_metres_per_unit': mpu, 'shepherd_metres_per_unit': SHEP_MPU, 'root_scale': round(k, 4),
          'shepherd_build': str(SHEP.as_posix()), 'borer_build': str(BORER.as_posix()), 'stations': []}
failed = False
borer_meshes = [o for o in bor.all_objects if o.type == 'MESH' and not o.hide_render]
# What stands on the deck: round 1's pads, or round 2's skid wear shoes.
pads = [o for o in borer_meshes if (o.name.startswith('Foot ') and o.name.endswith(' pad'))
        or (o.name.startswith('Skid ') and o.name.endswith(' shoe'))]
contact_names = [n for n in shep_parts if 'charging contact' in n]
for i in (1, 2, 3):
    lp = bpy.data.objects[f'Drone bay WS 003 launch point {i}']
    T = lp.matrix_world @ Matrix.Scale(k, 4) @ Matrix.Translation(-seat_local)
    lift = Matrix.Translation((0, 0, .001/SHEP_MPU))
    mine = per_object(borer_meshes, lift @ T)
    hits = sorted({(b, s) for b, (bt, _) in mine.items() for s, (st, _) in shep_parts.items() if bt.overlap(st)})
    # Pad soles: height above whatever is directly under each pad's lowest point.
    soles = {}
    for ob in pads:
        _, v = per_object([ob], T)[ob.name]
        low = min(v, key=lambda p: p.z)
        hit = shep_all.ray_cast(low+Vector((0, 0, .002/SHEP_MPU)), Vector((0, 0, -1)), 2.0)
        soles[ob.name] = None if hit[0] is None else round((low.z-hit[0].z)*SHEP_MPU, 4)
    # Socket face to the cradle's charging contact top.
    sk = per_object([bpy.data.objects['Belly charging socket contact plate']], T)
    sv = list(sk.values())[0][1]
    face_z = min(p.z for p in sv)
    c = next((n for n in contact_names if f'opening {i} ' in n), None)
    contact_top = max(p.z for p in shep_parts[c][1]) if c else None
    # Nearest Shepherd geometry that is not this station's cradle, deck or contact.
    own = f'opening {i} '
    near, near_name = 1e9, None
    samples = [p for _, v in mine.values() for p in v[::7]]
    for name, (st, _) in shep_parts.items():
        if own in name and any(w in name for w in ('cradle', 'contact', 'well floor')):
            continue
        for p in samples:
            q = st.find_nearest(p, 3.0)
            if q[0] is not None and q[3] < near:
                near, near_name = q[3], name
    # Lift straight up to the mouth: swept in 0.25 m steps, nothing but air.
    mouth = bpy.data.objects[f'Drone bay WS 003 opening {i} mouth'].matrix_world.translation
    steps, blocked = 0, []
    z = .25/SHEP_MPU
    while lp.matrix_world.translation.z+z < mouth.z+2:
        up = per_object(borer_meshes, Matrix.Translation((0, 0, z)) @ T)
        for b, (bt, _) in up.items():
            for s, (st, _) in shep_parts.items():
                if bt.overlap(st):
                    blocked.append((round(z*SHEP_MPU, 2), b, s))
        if blocked:
            break
        steps += 1
        z += .25/SHEP_MPU
    st = {'station': i, 'launch_point': [round(x, 3) for x in lp.matrix_world.translation],
          'overlaps': [list(h) for h in hits], 'pad_height_above_deck_m': soles,
          'socket_gap_to_contact_m': None if contact_top is None else round((face_z-contact_top)*SHEP_MPU, 4),
          'charging_contact': c, 'nearest_other_geometry_m': round(near*SHEP_MPU, 3), 'nearest_other': near_name,
          'lift_to_mouth_clear': not blocked, 'lift_steps_m': round(steps*.25, 2), 'lift_blocked_by': blocked[:4]}
    ok = not hits and all(v is not None and abs(v) < .005 for v in soles.values()) and not blocked
    st['ok'] = ok
    failed |= not ok
    report['stations'].append(st)
    print('CRADLE', json.dumps(st))
OUT.write_text(json.dumps(report, indent=1)+'\n', encoding='utf-8')
print('CRADLE', 'all stations fit' if not failed else 'FAILED', '->', OUT)
sys.exit(1 if failed else 0)
