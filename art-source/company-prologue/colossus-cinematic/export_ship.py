"""Export the textured Colossus as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../colossus-surface/build/skinned.blend, from
apply_skin.py on the clean geometry) and the geometry's geometry.json. Output,
beside this script:
  colossus.blend  one collection `COLOSSUS` holding the root empty
                  `COLOSSUS | root`, which parents every ship part and anchor;
                  images packed
  manifest.json   metres_per_unit, axes, bounds, drive / lift-thruster /
                  turret / flare-launcher anchors, lamps

What the export does to the build, in order (the Shepherd's export,
../shepherd-cinematic/export_ship.py, is the pattern; the Colossus has no gear
or doors that move, so there is no control empty):
1. drops cameras and lights;
2. bakes every mesh's modifiers into a plain mesh under the same object name,
   keeping materials, `skin` UVs and parenting;
3. writes `hull_rest` (vertex position, hull units, at rest) and
   `hull_normal` (face normal, hull space);
4. parents every top-level object to the root;
5. `Turret <i> pivot` (WS 001-004) on each traverse axis: every part the
   geometry marks `traverse` rides it, the barbette, base ring and feed trunk
   stay; `Anchor turret <i> port|starboard gun muzzle` sit on its twin muzzle
   brakes (port is left of the line of fire, the kit's twin-mount tags). The
   four WS 005-008 flare launchers are fixed: `Anchor launcher <i> flare
   muzzle` on each muzzle plate, +X along the slot angle;
6. `Engine 1-6 exhaust anchor` at the six main drive mouths and `Engine 7-16
   exhaust anchor` at the ten ventral lift thrusters (lift thruster 1-10,
   pointing down); the geometry's own `Engine <i>` and `Lift thruster <i>`
   empties are replaced. Local +X is the effect axis (exhaust, line of fire),
   the second-wind-vfx convention.
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
SURFACE = PROLOGUE / 'colossus-surface'
GEOMETRY = PROLOGUE / 'colossus-geometry'
sys.path.insert(0, str(HERE))
import vfx_hull  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'COLOSSUS', 'COLOSSUS | root'

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and ob.name.endswith(' exhaust anchor'):
        bpy.data.objects.remove(ob)        # re-declared below with the export's own names and keys
scene.frame_set(scene.frame_current)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
for name in sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and o.hide_render):
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
        reparent(ob, parent)
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
    if z.length < 1e-6:                    # straight down or up: +Z toward the bow
        z = Vector((1, 0, 0)) - x * x.dot(Vector((1, 0, 0)))
    z = z.normalized()
    return Matrix((x, z.cross(x), z)).transposed()


def bbox(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


movers = [o for o in bpy.data.objects if o.type == 'EMPTY']      # slot pivots and muzzles
root = empty(ROOT, coll, 40, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None:
        ob.parent = root          # root sits at the hull origin: no inverse needed
bpy.context.view_layer.update()

# 5. Turret pivots and muzzles; the flare launchers' muzzles.
turrets, trained_total = [], 0
for i, slot in enumerate((s for s in geom['weapon_slots'] if s['mount'] == 'TURRET'), 1):
    pre = f"Turret {i} {slot['id']} "
    parts = [o for o in ship if o.name.startswith(pre)]
    geo_pivot = bpy.data.objects[slot['pivot']]
    pc = geo_pivot.matrix_world.translation.copy()
    pivot = empty(f'Turret {i} pivot', anchors_coll, 3.0, 'SINGLE_ARROW', root, Matrix.Translation(pc))
    pivot['rest_angle_deg'] = slot['angle']
    pivot['slot'] = slot['id']
    pivot['train_axis'] = 'hull +Z'
    pivot['arc_deg'] = slot['arc']
    trained = [o for o in parts if o.get('traverse')]
    assert trained, f'no traverse parts on turret {i} (cl_mounts marks them)'
    for o in trained:
        reparent(o, pivot)
    for e in (slot['pivot'], slot['muzzle']):
        reparent(bpy.data.objects[e], pivot)
    bpy.context.view_layer.update()
    fire = Vector((math.cos(math.radians(slot['angle'])), math.sin(math.radians(slot['angle'])), 0))
    left = Vector((-fire.y, fire.x, 0))
    muzzles = []
    for k in (1, 2):
        brake = bpy.data.objects[f'{pre}barrel {k} muzzle brake']
        pts = [brake.matrix_world @ v.co for v in brake.data.vertices]
        far = max(p.dot(fire) for p in pts)
        ring = [p for p in pts if far - p.dot(fire) < .05]
        tip = sum(ring, Vector()) / len(ring) + fire * .02
        tag = 'port gun' if (tip - pc).dot(left) > 0 else 'starboard gun'
        a = empty(f'Anchor turret {i} {tag} muzzle', anchors_coll, .8, 'SINGLE_ARROW', pivot,
                  Matrix.Translation(tip) @ frame(fire).to_4x4())
        muzzles.append({'tag': tag, 'brake': brake.name, 'anchor': a.name, 'at': [round(c, 3) for c in tip],
                        'axis': [round(c, 3) for c in fire]})
    assert {m['tag'] for m in muzzles} == {'port gun', 'starboard gun'}, muzzles
    trained_total += len(trained)
    turrets.append({'index': i, 'slot': slot['id'], 'size': slot['size'], 'pivot': pivot.name,
                    'pivot_at': [round(c, 3) for c in pc], 'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
                    'trained_parts': len(trained), 'fixed_parts': len(parts) - len(trained), 'muzzles': muzzles})

launchers = []
for i, slot in enumerate((s for s in geom['weapon_slots'] if s['type'] == 'SYSTEM'), 1):
    lm = bpy.data.objects[slot['muzzle']]
    lfire = lm.matrix_world.to_3x3() @ Vector((1, 0, 0))
    la = empty(f'Anchor launcher {i} flare muzzle', anchors_coll, .8, 'SINGLE_ARROW', root,
               Matrix.Translation(lm.matrix_world.translation) @ frame(lfire).to_4x4())
    launchers.append({'index': i, 'slot': slot['id'], 'mount': slot['mount'], 'fixed': True,
                      'angle_deg': slot['angle'], 'anchor': la.name,
                      'at': [round(c, 3) for c in lm.matrix_world.translation],
                      'axis': [round(c, 3) for c in lfire.normalized()]})

# 6. Drive and lift-thruster anchors at the bell mouths.
engines = []
for e in geom['engines']:
    i = e['index']
    mouth, axis = Vector(e['anchor_location']), Vector(e['exhaust_direction'])
    a = empty(f'Engine {i} exhaust anchor', anchors_coll, vfx_hull.MAIN_BELL_R, 'SINGLE_ARROW', root,
              Matrix.Translation(mouth) @ frame(axis).to_4x4())
    a['running'] = True
    a['radius_m'] = vfx_hull.MAIN_BELL_R * MPU
    engines.append({'index': i, 'role': 'main drive', 'pod': e['pod'], 'anchor': a.name, 'mouth': list(mouth),
                    'axis': list(axis), 'radius': vfx_hull.MAIN_BELL_R, 'width': e['width'], 'style': e['style'],
                    'running': True})
n_main = len(engines)
for t in geom['lift_thrusters']:
    i = n_main + t['index']
    r = vfx_hull.BELL_R[vfx_hull.LIFT_WIDTH[t['well_radius']]]
    mouth, axis = Vector(t['anchor_location']), Vector(t['exhaust_direction'])
    a = empty(f'Engine {i} exhaust anchor', anchors_coll, r, 'SINGLE_ARROW', root,
              Matrix.Translation(mouth) @ frame(axis).to_4x4())
    a['running'] = True
    a['radius_m'] = r * MPU
    a['lift_thruster'] = t['index']
    engines.append({'index': i, 'role': 'lift thruster', 'lift_thruster': t['index'], 'host': t['host'],
                    'anchor': a.name, 'mouth': list(mouth), 'axis': list(axis), 'radius': round(r, 3),
                    'style': 'LOW_TECH', 'running': True})
root['engines_running'] = [e['index'] for e in engines]
root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['hull_object'] = 'Hull central cargo hold'

lamps = []
for ob in ship:
    for sl in ob.material_slots:
        m = sl.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and not bsdf.inputs['Emission Color'].is_linked and bsdf.inputs['Emission Strength'].default_value > 0 \
                and any(bsdf.inputs['Emission Color'].default_value[:3]) and 'glazing' not in m.name.lower():
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
    'asset': 'colossus.blend',
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
    'main_drives': list(vfx_hull.MAIN_INDICES),
    'lift_thrusters': list(vfx_hull.LIFT_INDICES),
    'turrets': turrets,
    'launchers': launchers,
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
        'anchors': 'Local +X is the effect axis (exhaust: aft for the main drives, down for the lift thrusters; '
                   'line of fire); +Z as near hull-dorsal as that allows (toward the bow for a downward axis).',
        'turrets': 'Rotate Turret <i> pivot about its local Z to train within its arc about the slot angle; the '
                   'rest pose is the slot angle. The four flare launchers are fixed.',
        'engines': 'Engines 1-6 are the main drives, 7-16 the lift thrusters (lift thruster 1-10); with the '
                   'effects collection each has its own throttle, thr_throttle_e<i>.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'turret parts trained', trained_total,
      'turrets', len(turrets), 'launchers', len(launchers), 'lamps', len(lamps))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'colossus.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'colossus.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
