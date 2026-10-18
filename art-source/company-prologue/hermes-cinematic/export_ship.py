"""Export the textured Hermes as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../hermes-surface/build/skinned.blend, from
apply_skin.py on the clean geometry: gear extended, boarding doors closed) and
the geometry's geometry.json. Output, beside this script:
  hermes.blend    one collection `HERMES` holding the root empty
                  `HERMES | root`, which parents every ship part and anchor;
                  images packed
  manifest.json   metres_per_unit, axes, bounds, engine / RCS / turret /
                  boarding-door / gear anchors, the control knobs, lamps

What the export does to the build, in order (the Mudskipper's export,
../mudskipper-cinematic/export_ship.py, is the pattern):
1. drops cameras and lights;
2. bakes every mesh's modifiers into a plain mesh under the same object name,
   keeping materials, `skin` UVs and parenting;
3. writes `hull_rest` (vertex position, hull units, at rest: every knob 0)
   and `hull_normal` (face normal, hull space);
4. parents every top-level object to the root, keeping the hinge hierarchy
   (gear legs, gear doors, boarding doors) and the gear doors' own drivers;
5. adds the `Hermes control` empty and drives the hinges from its knobs:
   `gear_up` (0 extended .. 1 stowed; the gear doors follow their legs) and
   `door_port` / `door_starboard` (0 closed .. 1 open);
6. adds `Turret <i> pivot` for each of the three turrets (turns about hull +Z;
   the gun house, mantlet and guns ride it, the ring, barbette and well stay)
   with `Anchor turret <i> port|starboard gun muzzle` on its twin barrels
   (port is left of the line of fire, the kit's twin-mount tags); `Engine <i>
   exhaust anchor` at the three drive mouths; `RCS <side> maneuvering jet
   anchor` at the two jets (the geometry's own `Engine 4/5` empties are
   replaced); `Anchor <side> boarding door` (opening centre) and `Anchor gear
   <leg> sole` (rides each leg). Local +X is the effect axis (exhaust, line of
   fire, outboard, down for a sole), the second-wind-vfx convention.
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
PROLOGUE = HERE.parent
SURFACE = PROLOGUE / 'hermes-surface'
GEOMETRY = PROLOGUE / 'hermes-geometry'
sys.path.insert(0, str(GEOMETRY))
from hm_ventral import GROUND, DOOR_X, DOOR_FACE  # noqa: E402  (plain constants; bpy-free at import)

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'HERMES', 'HERMES | root'
CONTROL = 'Hermes control'
KNOBS = ('gear_up', 'door_port', 'door_starboard')
# Turret parts that stay with the hull when the turret trains.
FIXED_TURRET_PARTS = ('barbette', 'armoured collar', 'collar bolt cover', 'traverse gap', ' ring',
                      'well floor', 'well drain grate')

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']
moving = geom['moving_parts']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
for p in moving['gear']:
    assert abs(bpy.data.objects[p['pivot']].rotation_euler[1]) < 1e-6, f'gear not extended: {p["pivot"]}'
for p in moving['boarding_doors']:
    assert abs(bpy.data.objects[p['pivot']].rotation_euler[2]) < 1e-6, f'door not closed: {p["pivot"]}'
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and ob.name.startswith('Engine ') and ob.name.endswith(' exhaust anchor'):
        bpy.data.objects.remove(ob)        # re-declared below with the export's own names and keys
scene.frame_set(scene.frame_current)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
left_out = sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and o.hide_render)
for name in left_out:
    bpy.data.objects.remove(bpy.data.objects[name])

# 2. Bake modifiers under the same object names.
dg = bpy.context.evaluated_depsgraph_get()
baked = {}
for ob in ship:
    if ob.modifiers:
        baked[ob.name] = bpy.data.meshes.new_from_object(ob.evaluated_get(dg), preserve_all_data_layers=True,
                                                         depsgraph=dg)
for ob in ship:
    me = baked.get(ob.name)
    if me is None:
        continue
    overrides = [(s.link, s.material) for s in ob.material_slots]
    ob.modifiers.clear()
    old = ob.data
    ob.data = me
    me.name = ob.name
    for k, (link, mat) in enumerate(overrides[:len(ob.material_slots)]):
        if link == 'OBJECT':
            ob.material_slots[k].link = 'OBJECT'
            ob.material_slots[k].material = mat
    if old.users == 0:
        bpy.data.meshes.remove(old)
bpy.context.view_layer.update()

# 3. Hull-space attributes at rest.
for ob in ship:
    me, mw = ob.data, ob.matrix_world
    nm = mw.to_3x3().inverted_safe().transposed()
    rest = me.attributes.get('hull_rest') or me.attributes.new('hull_rest', 'FLOAT_VECTOR', 'POINT')
    rest.data.foreach_set('vector', [c for v in me.vertices for c in (mw @ v.co)])
    hn = me.attributes.get('hull_normal') or me.attributes.new('hull_normal', 'FLOAT_VECTOR', 'FACE')
    hn.data.foreach_set('vector', [c for p in me.polygons for c in (nm @ p.normal).normalized()])

# 4. Collection and root.
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


def reparent(ob, parent):
    w = ob.matrix_world.copy()
    ob.parent = parent
    ob.matrix_parent_inverse = parent.matrix_world.inverted()
    ob.matrix_world = w


def frame(axis, up=Vector((0, 0, 1))):
    """+X along axis, +Z as near hull-dorsal as that allows (second-wind-vfx convention)."""
    x = Vector(axis).normalized()
    z = up - x * x.dot(up)
    z = z.normalized() if z.length > 1e-6 else Vector((0, 1, 0))
    return Matrix((x, z.cross(x), z)).transposed()


def bbox(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


movers = [o for o in bpy.data.objects if o.type == 'EMPTY']      # hinges, actuator eyes, slot marker
root = empty(ROOT, coll, 12, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None:
        ob.parent = root          # root sits at the hull origin: no inverse needed
bpy.context.view_layer.update()

# 5. Control empty and hinge drivers.
control = empty(CONTROL, anchors_coll, 4, 'CUBE', root, Matrix.Translation((0, 0, 16)))
for k in KNOBS:
    control[k] = 0.0
    ui = control.id_properties_ui(k)
    ui.update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0,
              description={'gear_up': 'landing gear: 0 extended (built), 1 stowed; the gear doors follow',
                           'door_port': 'port boarding door: 0 closed, 1 open (swings out and aft)',
                           'door_starboard': 'starboard boarding door: 0 closed, 1 open'}[k])


def drive(ob, index, knob, scale):
    fc = ob.driver_add('rotation_euler', index)
    d = fc.driver
    d.type = 'SCRIPTED'
    v = d.variables.new()
    v.name, v.type = 'k', 'SINGLE_PROP'
    v.targets[0].id_type, v.targets[0].id = 'OBJECT', control
    v.targets[0].data_path = f'["{knob}"]'
    d.expression = f'k * {scale!r}'


hinges = []
for p in moving['gear']:
    drive(bpy.data.objects[p['pivot']], 1, 'gear_up', math.radians(p['retract_angle_deg']))
    hinges.append({'pivot': p['pivot'], 'knob': 'gear_up', 'axis': 'local Y', 'angle_at_1_deg': p['retract_angle_deg']})
for p in moving['boarding_doors']:
    side = 'port' if p['open_sign'] > 0 else 'starboard'
    drive(bpy.data.objects[p['pivot']], 2, f'door_{side}', p['open_sign'] * math.radians(p['open_angle_deg']))
    hinges.append({'pivot': p['pivot'], 'knob': f'door_{side}', 'axis': 'local Z',
                   'angle_at_1_deg': p['open_sign'] * p['open_angle_deg']})
bpy.context.view_layer.update()

# 6a. Turret pivots and muzzles.
turrets = []
for i, slot in enumerate((s for s in geom['weapon_slots'] if s['mount'] == 'TURRET'), 1):
    pre = f'Turret {i} {slot["id"]} '
    parts = [o for o in ship if o.name.startswith(pre)]
    gap = next(o for o in parts if 'traverse gap' in o.name)
    px, py = slot['location']
    pz = bbox(gap)[1].z
    pivot = empty(f'Turret {i} pivot', anchors_coll, 2.0, 'SINGLE_ARROW', root, Matrix.Translation((px, py, pz)))
    pivot['rest_angle_deg'] = slot['angle']
    pivot['slot'] = slot['id']
    pivot['train_axis'] = 'hull +Z'
    trained = [o for o in parts if not any(k in o.name[len(pre) - 1:] for k in FIXED_TURRET_PARTS)]
    for o in trained:
        reparent(o, pivot)
    bpy.context.view_layer.update()
    fire = Vector((math.cos(math.radians(slot['angle'])), math.sin(math.radians(slot['angle'])), 0))
    muzzles = []
    for bore in sorted((o for o in parts if o.name.startswith(pre + 'barrel ') and o.name.endswith(' bore')),
                       key=lambda o: o.name):
        pts = [bore.matrix_world @ v.co for v in bore.data.vertices]
        far = max(p.dot(fire) for p in pts)
        ring = [p for p in pts if far - p.dot(fire) < .05]
        tip = sum(ring, Vector()) / len(ring) + fire * .02
        # The kit's twin-mount tags: the barrel left of the line of fire is the port gun.
        left = Vector((-fire.y, fire.x, 0))
        tag = 'port gun' if (tip - Vector((px, py, tip.z))).dot(left) > 0 else 'starboard gun'
        a = empty(f'Anchor turret {i} {tag} muzzle', anchors_coll, .6, 'SINGLE_ARROW', pivot,
                  Matrix.Translation(tip) @ frame(fire).to_4x4())
        muzzles.append({'tag': tag, 'barrel': bore.name, 'anchor': a.name, 'at': [round(c, 3) for c in tip],
                        'axis': [round(c, 3) for c in fire]})
    turrets.append({'index': i, 'slot': slot['id'], 'size': slot['size'], 'pivot': pivot.name,
                    'pivot_at': [px, py, round(pz, 3)], 'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
                    'trained_parts': len(trained), 'fixed_parts': len(parts) - len(trained), 'muzzles': muzzles})

# 6b. Drive and jet anchors, at the slot mouths (the geometry's own contract).
engines, rcs = [], []
for i, e in enumerate(geom['engines'], 1):
    mouth = Vector((*e['location'], e['z']))
    axis = Vector(e['exhaust_direction'])
    if e['role'].endswith('drive'):
        a = empty(f'Engine {i} exhaust anchor', anchors_coll, e['bell_mouth_radius'], 'SINGLE_ARROW', root,
                  Matrix.Translation(mouth) @ frame(axis).to_4x4())
        a['running'] = True
        a['radius_m'] = e['bell_mouth_radius'] * MPU
        engines.append({'index': i, 'role': e['role'], 'anchor': a.name, 'mouth': list(mouth), 'axis': list(axis),
                        'radius': e['bell_mouth_radius'], 'width': e['width'], 'running': True})
    else:
        side = 'port' if e['location'][1] > 0 else 'starboard'
        a = empty(f'RCS {side} maneuvering jet anchor', anchors_coll, e['bell_mouth_radius'], 'SINGLE_ARROW', root,
                  Matrix.Translation(mouth) @ frame(axis).to_4x4())
        a['working'] = True
        a['radius_m'] = e['bell_mouth_radius'] * MPU
        rcs.append({'anchor': a.name, 'engine_slot': i, 'at': list(mouth), 'exhaust_dir': list(axis),
                    'radius_units': e['bell_mouth_radius'], 'working': True})
root['engines_running'] = [e['index'] for e in engines]

# 6c. Boarding-door and gear anchors.
doors = []
for p in moving['boarding_doors']:
    s = p['open_sign']
    side = 'Port' if s > 0 else 'Starboard'
    hinge = bpy.data.objects[p['pivot']]
    at = Vector(((DOOR_X[0] + DOOR_X[1]) / 2, s * DOOR_FACE, hinge.matrix_world.translation.z))
    a = empty(f'Anchor {side} boarding door', anchors_coll, 2.0, 'SINGLE_ARROW', root,
              Matrix.Translation(at) @ frame(Vector((0, s, 0))).to_4x4())
    w, h = p['opening_units']
    doors.append({'side': side.lower(), 'anchor': a.name, 'hinge': p['pivot'], 'knob': f'door_{side.lower()}',
                  'opening_units': [w, h], 'opening_m': [round(w * MPU, 2), round(h * MPU, 2)],
                  'open_angle_deg': p['open_angle_deg']})
soles = []
for p in moving['gear']:
    leg = p['pivot'][len('Gear '):-len(' hinge')]
    sole = bpy.data.objects[f'Gear {leg} sole plate']
    lo, hi = bbox(sole)
    a = empty(f'Anchor gear {leg} sole', anchors_coll, 1.0, 'SINGLE_ARROW', bpy.data.objects[p['pivot']],
              Matrix.Translation(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z)) @ frame(Vector((0, 0, -1)), Vector((1, 0, 0))).to_4x4())
    soles.append({'leg': leg, 'anchor': a.name, 'sole_z_rest': round(lo.z, 3)})

root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['control'] = CONTROL
root['hull_object'] = 'Main hull - spine'

lamps = []
for ob in ship:
    for sl in ob.material_slots:
        m = sl.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and bsdf.inputs['Emission Strength'].default_value > 0 and any(bsdf.inputs['Emission Color'].default_value[:3]) \
                and 'glazing' not in m.name.lower():
            lamps.append({'object': ob.name, 'material': m.name, 'lit': True,
                          'at': [round(c, 2) for c in (sum(bbox(ob), Vector()) / 2)]})
            break

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
for ob in bpy.data.objects:
    for fc in (ob.animation_data.drivers if ob.animation_data else ()):
        fc.driver.is_valid = True
bpy.context.view_layer.update()

lo = Vector((1e9,) * 3)
hi = Vector((-1e9,) * 3)
for ob in ship:
    a, b = bbox(ob)
    lo, hi = Vector(map(min, lo, a)), Vector(map(max, hi, b))
polys = sum(len(o.data.polygons) for o in ship)


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


manifest = {
    'asset': 'hermes.blend',
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'state': 'clean',
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units_rest': [[round(c, 2) for c in lo], [round(c, 2) for c in hi]],
    'size_metres_rest': [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'rcs': rcs,
    'turrets': turrets,
    'boarding_doors': doors,
    'control': {'object': CONTROL, 'knobs': list(KNOBS), 'hinges': hinges, 'saved_pose': {k: 0.0 for k in KNOBS}},
    'landing': {'ground_z': GROUND, 'note': 'the gear is built extended: at rest the soles stand on ground_z',
                'sole_anchors': soles},
    'lamps_lit': len(lamps),
    'lamps': lamps,
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'effects': None,
    'sources': {'skinned_blend': str(SKINNED.relative_to(PROLOGUE)).replace('\\', '/'),
                'geometry_json_sha256': sha(GEOM_JSON),
                'skin_manifest_sha256': sha(SURFACE / 'skin-manifest.json')},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft; line of fire; outboard for doors; down for gear soles); +Z as near hull-dorsal as that allows.',
        'turrets': 'Rotate Turret <i> pivot about its local Z to train; the rest pose is the slot angle.',
        'control': "Animate the knobs on the 'Hermes control' empty (gear_up, door_port, door_starboard; 0..1). "
                   'At rest (all 0) the gear stands on landing.ground_z in hull space: place the root that far '
                   'above the ground plane.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'rcs', len(rcs),
      'turrets', len(turrets), 'muzzles', sum(len(t['muzzles']) for t in turrets), 'lamps', len(lamps))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'hermes.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'hermes.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
