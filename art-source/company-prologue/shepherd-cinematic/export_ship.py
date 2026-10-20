"""Export the textured Shepherd (Morrow) as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../shepherd-surface/build/skinned.blend, from
apply_skin.py on the clean geometry) and the geometry's geometry.json. Output,
beside this script:
  shepherd.blend  one collection `SHEPHERD` holding the root empty
                  `SHEPHERD | root`, which parents every ship part and anchor;
                  images packed
  manifest.json   metres_per_unit, axes, bounds, engine / RCS / turret /
                  hardpoint / drone-bay anchors, lamps

What the export does to the build, in order (the Hermes's export,
../hermes-cinematic/export_ship.py, is the pattern; the Shepherd has no gear or
doors, so there is no control empty):
1. drops cameras and lights;
2. bakes every mesh's modifiers into a plain mesh under the same object name,
   keeping materials, `skin` UVs and parenting;
3. writes `hull_rest` (vertex position, hull units, at rest) and
   `hull_normal` (face normal, hull space);
4. parents every top-level object to the root;
5. `Turret 1 pivot` (WS 007) at the geometry's traverse centre: every part
   the geometry marks `trained` rides it, the deck ring and barbette stay;
   `Anchor turret 1 port|starboard gun muzzle` sit on its twin muzzle brakes
   (port is left of the line of fire, the kit's twin-mount tags). The fixed
   WS 008 hardpoint (the turret kit's Swarmer pack) gets `Anchor turret 2 gun
   muzzle` on its first tube and `Anchor turret 2 tube 2|3|4 muzzle` on the
   others (the kit's gun rigs number every declared muzzle as a turret);
6. `Engine 1|2 exhaust anchor` at the two main drive mouths and `RCS
   <side> <forward|aft> jet anchor` at the four maneuvering jets (the
   geometry's own `Engine 3-6` empties are replaced); the drone bay's three
   `mouth` and `launch point` empties are kept and recorded. Local +X is the
   effect axis (exhaust, line of fire, launch), the second-wind-vfx convention.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import os
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
SURFACE = PROLOGUE / 'shepherd-surface'
GEOMETRY = PROLOGUE / 'shepherd-geometry'

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'SHEPHERD', 'SHEPHERD | root'
MAIN_BELL_R = 4.7          # sh_engines.MAIN_BELL mouth radius


def jet_r(w):
    return .5 + .35 * w    # sh_engines.jet nozzle radius


geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']
bay = geom['drone_bay']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and ob.name.startswith('Engine ') and ob.name.endswith(' exhaust anchor'):
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
    z = z.normalized() if z.length > 1e-6 else Vector((1, 0, 0))
    return Matrix((x, z.cross(x), z)).transposed()


def bbox(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


movers = [o for o in bpy.data.objects if o.type == 'EMPTY']      # slot pivots, muzzles, drone-bay anchors
root = empty(ROOT, coll, 20, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None:
        ob.parent = root          # root sits at the hull origin: no inverse needed
bpy.context.view_layer.update()

# 5. Turret pivot and muzzles; the hardpoint's muzzle.
turrets = []
slot = next(s for s in geom['weapon_slots'] if s['mount'] == 'TURRET')
pre = 'Turret 1 ' + slot['id'] + ' '
parts = [o for o in ship if o.name.startswith(pre)]
geo_pivot = bpy.data.objects[slot['pivot']]
pc = geo_pivot.matrix_world.translation.copy()
pivot = empty('Turret 1 pivot', anchors_coll, 2.5, 'SINGLE_ARROW', root, Matrix.Translation(pc))
pivot['rest_angle_deg'] = slot['angle']
pivot['slot'] = slot['id']
pivot['train_axis'] = 'hull +Z'
pivot['arc_deg'] = slot['arc']
trained = [o for o in parts if o.get('trained')]
assert trained, 'no trained turret parts (sh_mounts marks them)'
for o in trained:
    reparent(o, pivot)
reparent(bpy.data.objects[slot['muzzle']], pivot)
bpy.context.view_layer.update()
fire = Vector((math.cos(math.radians(slot['angle'])), math.sin(math.radians(slot['angle'])), 0))
left = Vector((-fire.y, fire.x, 0))
muzzles = []
for k in (1, 2):
    brake = bpy.data.objects[f'{pre}muzzle brake {k}']
    pts = [brake.matrix_world @ v.co for v in brake.data.vertices]
    far = max(p.dot(fire) for p in pts)
    ring = [p for p in pts if far - p.dot(fire) < .05]
    tip = sum(ring, Vector()) / len(ring) + fire * .02
    tag = 'port gun' if (tip - pc).dot(left) > 0 else 'starboard gun'
    a = empty(f'Anchor turret 1 {tag} muzzle', anchors_coll, .8, 'SINGLE_ARROW', pivot,
              Matrix.Translation(tip) @ frame(fire).to_4x4())
    muzzles.append({'tag': tag, 'brake': brake.name, 'anchor': a.name, 'at': [round(c, 3) for c in tip],
                    'axis': [round(c, 3) for c in fire]})
assert {m['tag'] for m in muzzles} == {'port gun', 'starboard gun'}, muzzles
turrets.append({'index': 1, 'slot': slot['id'], 'size': slot['size'], 'pivot': pivot.name,
                'pivot_at': [round(c, 3) for c in pc], 'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
                'trained_parts': len(trained), 'fixed_parts': len(parts) - len(trained), 'muzzles': muzzles})

hp = next(s for s in geom['weapon_slots'] if s['mount'] == 'HARDPOINT')
hm = bpy.data.objects[hp['muzzle']]
hfire = Vector((math.cos(math.radians(hp['angle'])), math.sin(math.radians(hp['angle'])), 0))
# The geometry's own muzzle (on the first tube's mouth) keeps the rig's single-gun name;
# the turret kit's anchors on the other tubes are 'tube <k>' of the same rig 2.
ha = empty('Anchor turret 2 gun muzzle', anchors_coll, .8, 'SINGLE_ARROW', root,
           Matrix.Translation(hm.matrix_world.translation) @ frame(hfire).to_4x4())
kit = hp.get('hardpoint') or {}
tubes = [{'tag': 'gun', 'anchor': ha.name, 'rides': hm.name, 'at': [round(c, 3) for c in hm.matrix_world.translation]}]
for k, name in enumerate(kit.get('muzzles', [])[1:], 2):
    src = bpy.data.objects[name]
    a = empty(f'Anchor turret 2 tube {k} muzzle', anchors_coll, .8, 'SINGLE_ARROW', root,
              Matrix.Translation(src.matrix_world.translation) @ frame(hfire).to_4x4())
    tubes.append({'tag': f'tube {k}', 'anchor': a.name, 'rides': src.name,
                  'at': [round(c, 3) for c in src.matrix_world.translation]})
hardpoints = [{'slot': hp['id'], 'mount': 'HARDPOINT', 'fixed': True, 'angle_deg': hp['angle'], 'arc_deg': hp['arc'],
               'weapon': kit.get('weapon'), 'variant': kit.get('variant'), 'kind': 'launcher' if kit else 'gun',
               'anchor': ha.name, 'at': [round(c, 3) for c in hm.matrix_world.translation],
               'axis': [round(c, 3) for c in hfire], 'muzzles': tubes,
               'rounds': kit.get('moving', []),
               'note': "the kit numbers every declared muzzle as a turret: this is gun rig 2, which never trains; "
                       "'gun' is tube 1, the hull's own muzzle"}]

# 6. Drive and jet anchors at the slot mouths; drone bay anchors.
engines, rcs = [], []
for e in geom['engines']:
    i = e['index']
    mouth = Vector(e['anchor_location'])
    axis = Vector(e['exhaust_direction'])
    if e['role'] == 'main drive':
        a = empty(f'Engine {i} exhaust anchor', anchors_coll, MAIN_BELL_R, 'SINGLE_ARROW', root,
                  Matrix.Translation(mouth) @ frame(axis).to_4x4())
        a['running'] = True
        a['radius_m'] = MAIN_BELL_R * MPU
        engines.append({'index': i, 'role': e['role'], 'anchor': a.name, 'mouth': list(mouth), 'axis': list(axis),
                        'radius': MAIN_BELL_R, 'width': e['width'], 'style': e['style'], 'running': True})
    else:
        side = 'port' if e['location'][1] > 0 else 'starboard'
        fore = 'forward' if e['location'][0] > -26 else 'aft'
        r = jet_r(e['width'])
        a = empty(f'RCS {side} {fore} jet anchor', anchors_coll, r, 'SINGLE_ARROW', root,
                  Matrix.Translation(mouth) @ frame(axis).to_4x4())
        a['working'] = True
        a['radius_m'] = r * MPU
        rcs.append({'anchor': a.name, 'engine_slot': i, 'at': list(mouth), 'exhaust_dir': list(axis),
                    'radius_units': round(r, 3), 'working': True})
root['engines_running'] = [e['index'] for e in engines]

openings = []
for o in bay['openings']:
    m, lp = bpy.data.objects[o['mouth_anchor']], bpy.data.objects[o['launch_point_anchor']]
    openings.append({'index': o['index'], 'mouth': m.name, 'launch_point': lp.name,
                     'mouth_at': [round(c, 3) for c in m.matrix_world.translation],
                     'launch_point_at': [round(c, 3) for c in lp.matrix_world.translation],
                     'clear_radius': o['clear_radius'], 'host': o['host']})

root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['hull_object'] = 'Hull central body'

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
    'asset': 'shepherd.blend',
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
    'hardpoints': hardpoints,
    'drone_bay': {'slot': bay['slot'], 'wing': bay['wing'], 'launch_direction': bay['launch_direction'],
                  'departure_heading': bay['departure_heading'], 'note': bay['note'], 'openings': openings},
    'lamps_lit': len(lamps),
    'lamps': lamps,
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'effects': None,
    'sources': {'skinned_blend': os.path.relpath(SKINNED, PROLOGUE).replace('\\', '/'),
                'geometry_json_sha256': sha(GEOM_JSON),
                'skin_manifest_sha256': sha(SURFACE / 'skin-manifest.json')},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft; line of fire; launch up for drone-bay anchors); '
                   '+Z as near hull-dorsal as that allows.',
        'turrets': 'Rotate Turret 1 pivot about its local Z to train (arc 200 deg about the slot angle); the rest '
                   'pose is the slot angle. The WS 008 hardpoint is fixed.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'rcs', len(rcs),
      'turret parts trained', len(trained), 'muzzles', len(muzzles) + len(tubes), 'openings', len(openings), 'lamps', len(lamps))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'shepherd.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'shepherd.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
