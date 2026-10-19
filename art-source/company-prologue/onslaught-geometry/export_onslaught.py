"""Export the Onslaught build as one appendable Blender collection.

  sh ../../blender.sh --background onslaught-geometry.blend --python export_onslaught.py -- [--out DIR]

Input is the .blend build_onslaught.py saves beside geometry.json, or the textured
build onslaught-surface/build/skinned.blend (its images are packed). Output, in
DIR (default ./export, ignored):
  onslaught.blend   one collection `ONSLAUGHT | clean` holding a root empty
                    `ONSLAUGHT | clean | root` that parents every ship part and
                    anchor (the Second Wind states' shape); a child collection
                    `ONSLAUGHT | clean | anchors` holds the empties
  manifest.json     metres_per_unit, axes, bounds, engines with their exhaust
                    anchors, turret pivots, hardpoint muzzles, running lights

What it does, in order:
1. drops cameras and lights;
2. bakes every mesh's modifiers (plate-edge bevels) into a plain mesh under the
   same object name, so the asset costs nothing to evaluate when a shot moves it;
3. moves meshes into the collection and empties into its anchors child;
4. parents everything that has no parent to the root, which sits at the hull
   origin (+X bow, +Y port, +Z dorsal, hull units); each turret's trainable
   parts stay children of `Weapon <slot> pivot`, which turns about its local Z.
The root carries metres_per_unit; scale the root by it to place the hull in
metres. Running lights carry their colour and blink data as custom properties.
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--out', default=str(HERE/'export'))
ap.add_argument('--geometry', default=str(HERE/'geometry.json'))
args = ap.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
OUT = Path(args.out)
OUT.mkdir(parents=True, exist_ok=True)
geom = json.loads(Path(args.geometry).read_text(encoding='utf-8'))
MPU = geom['metres_per_unit']
COLL = 'ONSLAUGHT | clean'
ROOT = f'{COLL} | root'

scene = bpy.context.scene
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
bpy.context.view_layer.update()

# 2. Bake modifiers.
dg = bpy.context.evaluated_depsgraph_get()
baked = 0
for ob in [o for o in scene.objects if o.type == 'MESH' and o.modifiers]:
    ev = ob.evaluated_get(dg)
    me = bpy.data.meshes.new_from_object(ev)
    old = ob.data
    ob.modifiers.clear()
    ob.data = me
    me.name = ob.name
    if old.users == 0:
        bpy.data.meshes.remove(old)
    baked += 1

# 3./4. Collection, anchors, root.
coll = bpy.data.collections.new(COLL)
scene.collection.children.link(coll)
anchors = bpy.data.collections.new(f'{COLL} | anchors')
coll.children.link(anchors)
root = bpy.data.objects.new(ROOT, None)
root.empty_display_type, root.empty_display_size = 'ARROWS', 20
coll.objects.link(root)
ship = [o for o in scene.objects if o is not root]
lights = {l['name']: l for l in geom.get('running_lights', [])}
for ob in ship:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (anchors if ob.type == 'EMPTY' else coll).objects.link(ob)
    if ob.parent is None:
        w = ob.matrix_world.copy()
        ob.parent = root          # root sits at the hull origin
        ob.matrix_parent_inverse = Matrix.Identity(4)
        ob.matrix_world = w
    if ob.name in lights:
        l = lights[ob.name]
        ob['running_light_colour'] = l['colour']
        ob['blink_period_s'] = l['blink']['period_s']
        ob['blink_phase_s'] = l['blink']['phase_s']
        ob['blink_duty'] = l['blink']['duty']
root['metres_per_unit'] = MPU
root['hull'] = 'onslaught_xiv'
root['axes'] = '+X bow, +Y port, +Z dorsal; hull units (one sprite pixel)'
bpy.context.view_layer.update()

# Strip everything outside the collection and save.
keep = set(coll.all_objects)
for ob in list(bpy.data.objects):
    if ob not in keep:
        bpy.data.objects.remove(ob)
for c in list(bpy.data.collections):
    if c not in (coll, anchors):
        bpy.data.collections.remove(c)
bpy.data.orphans_purge(do_recursive=True)

lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
meshes = [o for o in coll.all_objects if o.type == 'MESH']
for ob in meshes:
    for c in ob.bound_box:
        w = ob.matrix_world @ Vector(c)
        lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
blend = OUT/'onslaught.blend'
bpy.context.preferences.filepaths.save_version = 0
# A skinned build (onslaught-surface/build/skinned.blend) carries its part skins and
# swatches as image files beside it: pack them so the asset is one self-contained file.
images = [im for im in bpy.data.images if im.source == 'FILE' and im.filepath]
if images:
    bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(blend), compress=True)
manifest = {
    'asset': blend.name,
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors.name,
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units': [[round(c, 2) for c in lo], [round(c, 2) for c in hi]],
    'size_metres': [round((h-l)*MPU, 1) for l, h in zip(lo, hi)],
    'engines': geom['engines'],
    'weapon_slots': geom['weapon_slots'],
    'running_lights': geom['running_lights'],
    'objects': len(coll.all_objects),
    'meshes': len(meshes),
    'polygons': sum(len(o.data.polygons) for o in meshes),
    'baked_meshes': baked,
    'sources': {'geometry_json_sha256': hashlib.sha256(Path(args.geometry).read_bytes()).hexdigest()},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only. '
                'Scale it by metres_per_unit for metres.',
        'anchors': 'Engine <i> exhaust anchor: at the bell mouth, local +X down the exhaust (drive glow emits '
                   'along it); its custom properties hold exhaust_direction, mouth_radius and slot_width. '
                   'Weapon <slot> muzzle: the TPC muzzles, +X along the line of fire.',
        'turrets': 'Rotate a Weapon <slot> pivot about its local Z to train; rest pose is the slot angle; '
                   'the pivot carries slot_angle and arc.',
        'lights': 'Light ... objects are emissive running lights; colour and blink (period, phase, duty) are '
                  'custom properties on each and in running_lights.',
    },
}
(OUT/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
print('exported', blend, manifest['objects'], 'objects', manifest['polygons'], 'polygons')
