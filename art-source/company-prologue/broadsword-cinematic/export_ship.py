"""Export the textured Broadsword as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../fighters-surface/broadsword/build/skinned.blend,
from apply_skin.py on the clean geometry built gear up) and the geometry's
geometry.json. Output, beside this script:
  broadsword.blend  one collection `BROADSWORD` holding the root empty
                    `BROADSWORD | root`, which parents every part and anchor;
                    images packed
  manifest.json     metres_per_unit, axes, bounds, engine / gun / gear / lamp
                    anchors, the control knob, the running lights

What the export does to the build, in order (the Hermes's export,
../hermes-cinematic/export_ship.py, is the pattern):
1. drops cameras, lights and the geometry's own engine anchors;
2. bakes every mesh's modifiers into a plain mesh under the same object name,
   keeping materials, `skin` UVs and parenting;
3. writes `hull_rest` (vertex position, hull units, at rest: gear up) and
   `hull_normal` (face normal, hull space);
4. parents every top-level object to the root, keeping the gear hinge
   hierarchy;
5. adds the `Broadsword control` empty with one knob, `gear_down` (0 up, the
   flight rest pose .. 1 down), driving every leg and door hinge exactly as
   `bw_gear.pose` does: legs swing down through the whole range, doors are
   fully open by a third of it;
6. adds `Engine <i> exhaust anchor` at the two drive mouths, `Anchor turret
   <i> gun muzzle` on the two fixed light machine guns (the kit's declared-
   muzzle name; there is no turret, the gun is fixed to the hull), and
   `Anchor gear <leg> sole` riding each leg. Local +X is the effect axis
   (exhaust aft, line of fire forward, down for a sole), the second-wind-vfx
   convention. The geometry's `Weapon WS <id> anchor|muzzle` empties stay.
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
SURFACE = PROLOGUE / 'fighters-surface' / 'broadsword'
GEOMETRY = PROLOGUE / 'broadsword-geometry'

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'BROADSWORD', 'BROADSWORD | root'
CONTROL = 'Broadsword control'
KNOB = 'gear_down'
DOOR_LEAD = 3.0      # doors fully open at gear_down = 1/3 (bw_gear.pose)

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']
gear = geom['landing_gear']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
for leg in gear['legs']:
    h = bpy.data.objects[leg['hinge']]
    assert abs(h.rotation_euler[1] - math.radians(h['retract_deg'])) < 1e-6, f'gear not up: {h.name}'
    for d in leg['doors']:
        assert abs(bpy.data.objects[d].rotation_euler[0]) < 1e-6, f'door not shut: {d}'
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and ob.name.startswith('Engine ') and ob.name.endswith(' exhaust anchor'):
        bpy.data.objects.remove(ob)        # re-declared below with the export's own names and keys
scene.frame_set(scene.frame_current)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render and o.name != 'Parked deck plane']
for o in [o for o in bpy.data.objects if o.type == 'MESH' and o not in ship]:
    bpy.data.objects.remove(o)

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

# 3. Hull-space attributes at rest (gear up).
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


def frame(axis, up=Vector((0, 0, 1))):
    """+X along axis, +Z as near hull-dorsal as that allows (second-wind-vfx convention)."""
    x = Vector(axis).normalized()
    z = up - x * x.dot(up)
    z = z.normalized() if z.length > 1e-6 else Vector((0, 1, 0))
    return Matrix((x, z.cross(x), z)).transposed()


def bbox(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


movers = [o for o in bpy.data.objects if o.type == 'EMPTY']      # hinges, slot anchors, the geometry root
old_root = bpy.data.objects.get('Broadsword root')
root = empty(ROOT, coll, 8, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None or ob.parent == old_root:
        ob.parent = root          # both roots sit at the hull origin: no inverse needed
if old_root is not None:
    bpy.data.objects.remove(old_root)
bpy.context.view_layer.update()

# 5. Control empty and hinge drivers.
control = empty(CONTROL, anchors_coll, 2, 'CUBE', root, Matrix.Translation((0, 0, 8)))
control[KNOB] = 0.0
control.id_properties_ui(KNOB).update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0,
                                      description='landing gear: 0 up and doors shut (flight, built), 1 down; '
                                                  'the doors open over the first third')


def drive(ob, index, expr):
    fc = ob.driver_add('rotation_euler', index)
    d = fc.driver
    d.type = 'SCRIPTED'
    v = d.variables.new()
    v.name, v.type = 'k', 'SINGLE_PROP'
    v.targets[0].id_type, v.targets[0].id = 'OBJECT', control
    v.targets[0].data_path = f'["{KNOB}"]'
    d.expression = expr


hinges, soles = [], []
for leg in gear['legs']:
    h = bpy.data.objects[leg['hinge']]
    r = math.radians(h['retract_deg'])
    # Sole anchor: placed with the leg down, then it rides the hinge.
    h.rotation_euler[1] = 0.0
    bpy.context.view_layer.update()
    wheels = [o for o in ship if o.name.startswith(leg['name'] + ' wheel ') and not o.name.endswith(' hub')]
    lo = Vector(map(min, *[bbox(w)[0] for w in wheels]))
    hi = Vector(map(max, *[bbox(w)[1] for w in wheels]))
    a = empty(f"Anchor gear {leg['name'][len('Hull '):]} sole", anchors_coll, .8, 'SINGLE_ARROW', h,
              Matrix.Translation(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z)) @ frame(Vector((0, 0, -1)), Vector((1, 0, 0))).to_4x4())
    soles.append({'leg': leg['name'], 'anchor': a.name, 'sole_z_down': round(lo.z, 3)})
    h.rotation_euler[1] = r
    drive(h, 1, f'(1 - k) * {r!r}')
    hinges.append({'pivot': h.name, 'axis': 'local Y', 'angle_at_0_deg': h['retract_deg'], 'angle_at_1_deg': 0.0})
    for dn in leg['doors']:
        d = bpy.data.objects[dn]
        o = math.radians(d['open_deg'])
        drive(d, 0, f'min(1.0, {DOOR_LEAD!r} * k) * {o!r}')
        hinges.append({'pivot': dn, 'axis': 'local X', 'angle_at_0_deg': 0.0, 'angle_at_1_deg': d['open_deg'],
                       'full_at': round(1 / DOOR_LEAD, 3)})
bpy.context.view_layer.update()

# 6a. Drive anchors at the mouth-lip plane, +X down the exhaust.
engines = []
for e in geom['engines']:
    i = e['index']
    mouth, axis = Vector(e['anchor_location']), Vector(e['exhaust_direction'])
    a = empty(f'Engine {i} exhaust anchor', anchors_coll, e['bell_radius'], 'SINGLE_ARROW', root,
              Matrix.Translation(mouth) @ frame(axis).to_4x4())
    a['running'] = True
    a['radius_m'] = e['bell_radius'] * MPU
    engines.append({'index': i, 'side': 'port' if mouth.y > 0 else 'starboard', 'anchor': a.name,
                    'mouth': [round(c, 3) for c in mouth], 'axis': list(axis), 'radius': e['bell_radius'],
                    'width': e['width'], 'style': e['style'], 'running': True})
root['engines_running'] = [e['index'] for e in engines]

# 6b. The two fixed guns: the kit's declared-muzzle anchors at the barrel tips.
guns = []
fitted = [s for s in geom['weapon_slots'] if s.get('fitted')]
for i, s in enumerate(fitted, 1):
    src = bpy.data.objects[s['muzzle']]
    fire = (src.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    tip = src.matrix_world.translation.copy()
    a = empty(f'Anchor turret {i} gun muzzle', anchors_coll, .6, 'SINGLE_ARROW', root,
              Matrix.Translation(tip) @ frame(fire).to_4x4())
    a['slot'] = s['id']
    a['weapon'] = s['fitted']
    guns.append({'index': i, 'slot': s['id'], 'weapon': s['fitted'], 'mount': 'fixed (hidden hardpoint)',
                 'anchor': a.name, 'geometry_muzzle': s['muzzle'], 'at': [round(c, 3) for c in tip],
                 'axis': [round(c, 3) for c in fire], 'arc_deg': s['arc'], 'linked': True})

root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['control'] = CONTROL
root['hull_object'] = 'Hull forward fuselage'

lamps = []
for ob in ship:
    for sl in ob.material_slots:
        m = sl.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and bsdf.inputs['Emission Strength'].default_value > 0 and any(bsdf.inputs['Emission Color'].default_value[:3]):
            lamps.append({'object': ob.name, 'material': m.name, 'lit': True,
                          'at': [round(c, 2) for c in ob.matrix_world.translation]})
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
    'asset': 'broadsword.blend',
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
    'guns': guns,
    'control': {'object': CONTROL, 'knobs': [KNOB], 'hinges': hinges, 'saved_pose': {KNOB: 0.0}},
    'landing': {'ground_z': gear['parked_check']['deck_z_units'],
                'note': 'rest is gear up (flight); at gear_down 1 the soles stand on ground_z in hull space',
                'sole_anchors': soles},
    'running_lights': geom['running_lights'],
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
        'root': 'Every part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft; line of fire; down for gear soles); +Z as near hull-dorsal as that allows.',
        'guns': 'The two light machine guns are fixed and linked; aim the fighter, not the gun.',
        'control': "Animate gear_down on the 'Broadsword control' empty (0..1). Rest (0) is gear up for flight; "
                   'at 1 the gear stands on landing.ground_z in hull space: place the root that far above a deck.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'guns', len(guns),
      'lamps', len(lamps), 'hinges', len(hinges))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'broadsword.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'broadsword.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
