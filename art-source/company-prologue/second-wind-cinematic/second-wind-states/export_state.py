"""Export one Second Wind condition state as an appendable Blender collection.

  blender --background --factory-startup --python export_state.py -- --state patched
      [--skinned PATH] [--geometry PATH] [--out DIR] [--no-save] [--params PATH]

Input is the skinned build of that state (second-wind-surface/apply_skin.py on
`build_reference.py -- --state S`) and the state's geometry.json (its scar
record, sites and engine table). Output, under <out>/<state>/ (default beside
this script):
  second-wind-<state>.blend   one collection `SECOND WIND | <state>` holding a root
                              empty `SECOND WIND | <state> | root` that parents
                              every ship part and anchor; images packed
  manifest.json               metres_per_unit, axes, bounds, engine and turret
                              anchors, which engines run, lamps, scar statuses

What the export does to the build, in order:
1. drops cameras, lights and everything the state hides (scar_hidden base
   parts, boolean cutters); their names go in the manifest;
2. bakes every mesh's modifiers and shape keys (scar cuts, bevels, dents) into
   a plain mesh under the same object name, so the asset costs nothing to
   evaluate when a shot moves it; materials, `skin` UVs and object-level
   overrides survive;
3. writes `hull_rest` (vertex position, hull units) and `hull_normal` (face
   normal, hull space), which the wear masks read so they ride the ship;
4. parents everything to the root; each turret's trainable parts (above the
   traverse gap) to `Turret <i> pivot`, which turns about hull +Z;
5. keeps the geometry's anchors (`Engine <i> exhaust anchor` at each bell
   mouth, `RCS <cluster> <axis> anchor` at each thruster), writes this state's
   `running` / `working` on them, and adds `Anchor turret <i> <gun> muzzle` at
   each muzzle; local +X is the effect axis (down the exhaust, along the line
   of fire), the second-wind-vfx convention;
6. chains the state overlay (second-wind-surface/wear_overlay.py, overlay/<state>.json)
   after the base wear that apply_skin.py --wear <state> already put on the skins.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent.parent
SURFACE = PROLOGUE / 'second-wind-surface'
GEOMETRY = PROLOGUE / 'second-wind-geometry'
for p in (SURFACE, GEOMETRY):
    sys.path.insert(0, str(p))
import wear_overlay as overlay  # noqa: E402
from sw_turrets import MOUNTS  # noqa: E402  (tables only; importing builds nothing)
from sw_engines import ENGINES, BELL_R  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--state', required=True, choices=overlay.STATES)
ap.add_argument('--skinned')
ap.add_argument('--geometry')
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--params')
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
STATE = args.state
SKINNED = Path(args.skinned or SURFACE / 'build' / f'skinned-{STATE}.blend').resolve()
GEOM_JSON = Path(args.geometry or SURFACE / 'build' / f'{STATE}-geom' / 'geometry.json').resolve()
OUT = Path(args.out).resolve() / STATE
COLL = f'SECOND WIND | {STATE}'
ROOT = f'SECOND WIND | {STATE} | root'
# Drive status -> running. A jury-rigged coil runs (patched, refit starboard).
RUNNING = {'running', 'jury'}
PORT_ENGINES, STBD_ENGINES = (1, 2), (3, 4)   # sw_engines.ENGINES order: +Y is port
FIXED_TURRET_PARTS = ('barbette foot', 'armoured collar', 'traverse gap')

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']
damage = geom['damage']
assert damage['state'] == STATE, (damage['state'], STATE)
status = {r['id']: r['status'] for r in damage['scars']}
params = json.loads(Path(args.params).read_text()) if args.params else overlay.load_params(STATE)

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene

# 1. What ships in this state.
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
# The geometry's own anchors (sw_engines `Engine <i> exhaust anchor`, sw_rcs
# `RCS <cluster> <axis> anchor`): kept, under the root.
geo_anchors = [o for o in bpy.data.objects if o.type == 'EMPTY']
left_out = sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and o.hide_render)
hidden_parts = {o.name: o.get('scar_hidden') for o in bpy.data.objects if o.get('scar_hidden')}

# 1b. Where each scar id lives in this state: the union of everything it
# touches (its own `Scar <id> ...` parts, base parts it hides, shape-keys or
# overrides), for the cross-state continuity review.
def _box(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


touch = {}
for ob in bpy.data.objects:
    if ob.type != 'MESH' or ob.name.startswith('Scar cutter'):
        continue
    ids = set()
    if ob.name.startswith('Scar '):
        ids.add(ob.name.split(' ')[1].split('.')[0])
    for key in ('scar_hidden', 'scar_override'):
        if ob.get(key):
            ids.add(ob[key])
    for kb in (ob.data.shape_keys.key_blocks if ob.data.shape_keys else ()):
        if kb.name.startswith('Scar '):
            ids.add(kb.name[5:])
    for sid in ids:
        touch.setdefault(sid, []).append((ob.name, _box(ob)))
footprints = {}
for sid, items in sorted(touch.items()):
    lo = Vector([min(c) for c in zip(*[b[0] for _, b in items])])
    hi = Vector([max(c) for c in zip(*[b[1] for _, b in items])])
    footprints[sid] = {'centre': [round(c, 3) for c in (lo + hi) / 2],
                       'extent': [round(c, 3) for c in hi - lo], 'objects': len(items)}

# 2. Bake modifiers and shape keys (evaluate everything first: cutters are still needed).
dg = bpy.context.evaluated_depsgraph_get()
baked = {}
for ob in ship:
    if ob.modifiers or ob.data.shape_keys:
        baked[ob.name] = bpy.data.meshes.new_from_object(ob.evaluated_get(dg), preserve_all_data_layers=True,
                                                         depsgraph=dg)
for ob in ship:
    me = baked.get(ob.name)
    if me is None:
        continue
    overrides = [(s.link, s.material) for s in ob.material_slots]
    ob.modifiers.clear()
    if ob.data.shape_keys:
        ob.shape_key_clear()
    old = ob.data
    ob.data = me
    me.name = ob.name
    for k, (link, mat) in enumerate(overrides[:len(ob.material_slots)]):
        if link == 'OBJECT':
            ob.material_slots[k].link = 'OBJECT'
            ob.material_slots[k].material = mat
    if old.users == 0:
        bpy.data.meshes.remove(old)
for ob in [o for o in bpy.data.objects if o.type == 'MESH' and o not in ship]:
    bpy.data.objects.remove(ob)

# 3. Hull-space attributes for the wear masks.
for ob in ship:
    me, mw = ob.data, ob.matrix_world
    nm = mw.to_3x3().inverted_safe().transposed()
    rest = me.attributes.get('hull_rest') or me.attributes.new('hull_rest', 'FLOAT_VECTOR', 'POINT')
    rest.data.foreach_set('vector', [c for v in me.vertices for c in (mw @ v.co)])
    hn = me.attributes.get('hull_normal') or me.attributes.new('hull_normal', 'FLOAT_VECTOR', 'FACE')
    hn.data.foreach_set('vector', [c for p in me.polygons for c in (nm @ p.normal).normalized()])

# 4. Collection, root, turret pivots.
coll = bpy.data.collections.new(COLL)
scene.collection.children.link(coll)
anchors_coll = bpy.data.collections.new(f'{COLL} | anchors')
coll.children.link(anchors_coll)


def empty(name, where, size=1.0, kind='PLAIN_AXES', parent=None, matrix=None):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type, ob.empty_display_size = kind, size
    where.objects.link(ob)
    ob.matrix_world = matrix if matrix is not None else Matrix.Identity(4)
    if parent is not None:
        w = ob.matrix_world.copy()
        ob.parent = parent
        ob.matrix_parent_inverse = parent.matrix_world.inverted()
        ob.matrix_world = w
    return ob


root = empty(ROOT, coll, 12, 'ARROWS')
for ob in ship:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    coll.objects.link(ob)
    ob.parent = root          # root sits at the hull origin: no inverse needed
for ob in geo_anchors:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    anchors_coll.objects.link(ob)
    if ob.parent is None or ob.parent.name not in coll.objects:
        w = ob.matrix_world.copy()
        ob.parent = root
        ob.matrix_parent_inverse = Matrix.Identity(4)
        ob.matrix_world = w
bpy.context.view_layer.update()


def bbox(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


def by_prefix(prefix):
    return sorted((o for o in ship if o.name.startswith(prefix)), key=lambda o: o.name)


def frame(axis, up=Vector((0, 0, 1))):
    """+X along axis, +Z as near hull-dorsal as that allows (second-wind-vfx convention)."""
    x = Vector(axis).normalized()
    z = up - x * x.dot(up)
    z = z.normalized() if z.length > 1e-6 else Vector((0, 1, 0))
    return Matrix((x, z.cross(x), z)).transposed()


turrets = []
for i, (x, y, angle, scale, heavy) in enumerate(MOUNTS, 1):
    parts = by_prefix(f'Turret {i} ')
    gap = next((o for o in parts if o.name == f'Turret {i} traverse gap'), None)
    z = bbox(gap)[1].z if gap else (max(bbox(o)[0].z for o in parts) if parts else 0.0)
    pivot = empty(f'Turret {i} pivot', anchors_coll, 3 * scale, 'SINGLE_ARROW', root,
                  Matrix.Translation((x, y, z)))
    pivot['rest_angle_deg'] = angle
    pivot['slot'] = f'WS 00{i}'
    pivot['train_axis'] = 'hull +Z'
    trained = [o for o in parts if not o.name.endswith(FIXED_TURRET_PARTS)]
    for o in trained:
        w = o.matrix_world.copy()
        o.parent = pivot
        o.matrix_parent_inverse = pivot.matrix_world.inverted()
        o.matrix_world = w
    bpy.context.view_layer.update()
    muzzles = []
    for brake in [o for o in parts if o.name.endswith('muzzle brake')]:
        tag = brake.name[len(f'Turret {i} '):-len(' muzzle brake')]
        mw = brake.matrix_world
        lo = Vector(map(min, *[Vector(c) for c in brake.bound_box]))
        hi = Vector(map(max, *[Vector(c) for c in brake.bound_box]))
        tip = mw @ Vector((hi.x, (lo.y + hi.y) / 2, (lo.z + hi.z) / 2))
        axis = (mw.to_3x3() @ Vector((1, 0, 0))).normalized()
        a = empty(f'Anchor turret {i} {tag} muzzle', anchors_coll, 1.0, 'SINGLE_ARROW', pivot,
                  Matrix.Translation(tip) @ frame(axis).to_4x4())
        muzzles.append({'tag': tag, 'anchor': a.name, 'at': [round(c, 3) for c in tip],
                        'axis': [round(c, 3) for c in axis]})
    turrets.append({'index': i, 'slot': f'WS 00{i}', 'size': 'MEDIUM' if heavy else 'SMALL',
                    'pivot': pivot.name, 'pivot_at': [x, y, round(z, 3)], 'rest_angle_deg': angle,
                    'trained_parts': len(trained), 'fixed_parts': len(parts) - len(trained),
                    'status': status.get(f'sp-turret-{i}'), 'present': bool(trained), 'muzzles': muzzles})

# 5. Engine and RCS anchors: the geometry's own (geometry.json engine_anchors,
# rcs_anchors), with this state's running / working flags written onto them.
drive = {'port': status.get('pod-port-drive'), 'starboard': status.get('pod-starboard-drive')}
engines = []
for rec in geom.get('engine_anchors', []):
    i = rec['engine']
    side = 'port' if i in PORT_ENGINES else 'starboard'
    running = drive[side] in RUNNING
    assert running == bool(rec.get('running', running)), (i, running, rec.get('running'))
    a = bpy.data.objects.get(rec['name'])
    assert a is not None and a in set(coll.all_objects), rec['name']
    a['running'] = running
    mouth = a.matrix_world.translation
    w = next(wd for k, (_, _, wd) in enumerate(ENGINES, 1) if k == i)
    engines.append({'index': i, 'side': side, 'slot_width': w, 'radius': rec['radius'], 'anchor': a.name,
                    'mouth': [round(c, 3) for c in mouth],
                    'axis': [round(c, 3) for c in a.matrix_world.to_3x3() @ Vector((1, 0, 0))],
                    'drive_status': drive[side], 'running': running})
assert len(engines) == 4, 'geometry.json has no engine_anchors'
rcs = []
for rec in geom.get('rcs_anchors', []):
    a = bpy.data.objects.get(rec['name'])
    if a is None or a not in set(coll.all_objects):
        continue
    a['working'] = bool(rec.get('working'))
    rcs.append({'anchor': a.name, 'cluster': rec['cluster'], 'axis': rec['axis'],
                'at': [round(c, 3) for c in a.matrix_world.translation],
                'exhaust_dir': rec['exhaust_dir'], 'radius_units': round(rec['radius_m'] / MPU, 3),
                'working': bool(rec.get('working'))})
root['state'] = STATE
root['metres_per_unit'] = MPU
root['engines_running'] = [e['index'] for e in engines if e['running']]

lamps = []
for ob in ship:
    for s in ob.material_slots:
        m = s.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and ('light' in m.name.lower() or 'beacon' in m.name.lower() or 'lamp' in m.name.lower()):
            lit = bsdf.inputs['Emission Strength'].default_value > 0 and any(bsdf.inputs['Emission Color'].default_value[:3])
            lamps.append({'object': ob.name, 'material': m.name, 'lit': bool(lit),
                          'at': [round(c, 2) for c in (sum(bbox(ob), Vector()) / 2)]})
            break

# 6. Wear.
wear_summary = overlay.apply(STATE, params, engines, damage['scars'], damage.get('sites', {}))

# Strip everything outside the collection, pack, save.
keep = set(coll.all_objects)
for ob in list(bpy.data.objects):
    if ob not in keep:
        bpy.data.objects.remove(ob)
bpy.data.orphans_purge(do_recursive=True)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
external = [img.name for img in bpy.data.images if img.source == 'FILE' and not img.packed_file]
assert not external, external

lo = Vector((1e9,) * 3)
hi = Vector((-1e9,) * 3)
for ob in ship:
    a, b = bbox(ob)
    lo, hi = Vector(map(min, lo, a)), Vector(map(max, hi, b))
polys = sum(len(o.data.polygons) for o in ship)


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


manifest = {
    'state': STATE,
    'asset': f'second-wind-{STATE}.blend',
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units': [[round(c, 2) for c in lo], [round(c, 2) for c in hi]],
    'size_metres': [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'rcs': rcs,
    'rcs_working': sum(r['working'] for r in rcs),
    'turrets': turrets,
    'lamps_lit': sum(l['lit'] for l in lamps),
    'lamps': lamps,
    'scars': [{'id': r['id'], 'kind': r['kind'], 'status': r['status']} for r in damage['scars']],
    'scar_sites': damage.get('sites', {}),
    'scar_footprints': footprints,
    'hidden_base_parts': hidden_parts,
    'left_out_objects': len(left_out),
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'wear': dict(wear_summary, base='second-wind-surface/wear.py, wear.json', params=f'second-wind-surface/overlay/{STATE}.json', note=params.get('note')),
    'effects': None,
    'sources': {'skinned_blend': SKINNED.name, 'geometry_json_sha256': sha(GEOM_JSON),
                'wear_params_sha256': hashlib.sha256(json.dumps(params, sort_keys=True).encode()).hexdigest()},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (drive exhaust aft; gun line of fire); +Z as near hull-dorsal as that allows.',
        'turrets': 'Rotate a Turret <i> pivot about its local Z to train; rest pose is the slot angle.',
        'wear': 'Base wear: group `SW wear <state>` on skins and swatches (wear.py). State overlay: group `SW overlay <state>`, its `SW overlay master` value scales it (0 = base wear only).',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', STATE, len(ship), 'objects', polys, 'polys; wear', wear_summary['materials'],
      'scorch', wear_summary['scorch_sites'], 'soot', wear_summary['soot_engines'],
      'running', manifest['engines_running'], 'lamps lit', manifest['lamps_lit'])
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / f'second-wind-{STATE}.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / f'second-wind-{STATE}.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
