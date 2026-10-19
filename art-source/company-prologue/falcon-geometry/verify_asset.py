"""Check falcon-p.blend is a cheap, appendable asset: link it the way a shot will.

  sh ../../blender.sh --background --factory-startup --python verify_asset.py -- [PATH]
Links the collection `FALCON (P)` from PATH (default the skinned export,
../falcon-surface/build/export/falcon-p.blend) into an empty scene, instances
it three times through collection-instance empties scaled by metres_per_unit,
and fails unless: one root parents every object, no modifier or constraint is
left, every engine has its bell, lip, throat, can, exhaust and glow anchors,
every turret has a pivot with its slot, every weapon slot has its anchor and
the VFX kit's declared muzzles, every image is packed, and the six running
lights are present. Then it trains every turret through its arc and checks the
kit muzzles follow. Prints the counts.
"""
from pathlib import Path
import json
import math
import sys
import bpy

HERE = Path(__file__).resolve().parent
argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
path = Path(argv[0] if argv else HERE.parent/'falcon-surface'/'build'/'export'/'falcon-p.blend').resolve()
geom = json.loads((HERE/'geometry.json').read_text(encoding='utf-8'))
bpy.ops.wm.read_factory_settings(use_empty=True)
with bpy.data.libraries.load(str(path), link=False) as (src, dst):
    COLL = 'FALCON (P)'
    ROOT = f'{COLL} | root'
    dst.collections = [COLL]
coll = dst.collections[0]
bpy.context.scene.collection.children.link(coll)
objs = list(coll.all_objects)
names = {o.name for o in objs}
root = bpy.data.objects[ROOT]
assert root.name in coll.objects
assert not [o.name for o in objs if o is not root and o.parent is None], 'objects outside the root'
assert not [o.name for o in objs if o.type == 'MESH' and o.modifiers], 'live modifiers left'
assert not [o.name for o in objs if o.constraints], 'constraints left'
assert not [o.name for o in objs if o.type in ('CAMERA', 'LIGHT')], 'review rig saved into the asset'
unpacked = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
assert not unpacked, f'unpacked images {unpacked[:5]}'
mpu = root['metres_per_unit']
for e in geom['engines']:
    i = e['index']
    for part in ('aft-facing bell', 'mouth lip', 'dark throat', 'drive can', 'exhaust anchor', 'drive glow anchor'):
        assert f'Engine {i} {part}' in names, f'Engine {i} {part}'
muzzles = 0
for m, s in enumerate(geom['weapon_slots'], 1):
    assert f"Weapon {s['id']} anchor" in names, s['id']
    kit = [n for n in names if n.startswith(f'Anchor turret {m} ') and n.endswith(' muzzle')]
    assert len(kit) == len(s['muzzle_anchors']), (s['id'], kit)
    muzzles += len(kit)
    if s['mount'] == 'TURRET':
        pivot = bpy.data.objects[f"Weapon {s['id']} pivot"]
        assert pivot['slot'] == s['id']
        k = bpy.data.objects[sorted(kit)[0]]
        bpy.context.view_layer.update()
        before = k.matrix_world.translation.copy()
        pivot.rotation_euler.z = math.radians(s['angle'] + s['arc']/2)
        bpy.context.view_layer.update()
        assert (k.matrix_world.translation - before).length > .5, f"{s['id']} muzzle did not train"
        pivot.rotation_euler.z = math.radians(s['angle'])
lights = [n for n in names if n.startswith('Light ')]
assert len(lights) == 6, lights
scene = bpy.context.scene
for k in range(3):  # a formation: three instances, each scaled to metres
    inst = bpy.data.objects.new(f'Falcon {k+1}', None)
    inst.instance_type, inst.instance_collection = 'COLLECTION', coll
    inst.scale = (mpu,)*3
    inst.location = (k*250.0, k*-120.0, 0)
    scene.collection.objects.link(inst)
bpy.context.view_layer.update()
meshes = [o for o in objs if o.type == 'MESH']
tris = sum(len(p.vertices)-2 for o in meshes for p in o.data.polygons)
mats = sorted({m.name for o in meshes for m in o.data.materials if m})
print(f'OK {path.name}: collection {COLL!r}, root {ROOT!r}, {len(objs)} objects ({len(meshes)} meshes), '
      f'{tris} triangles, {len(mats)} materials, {len(bpy.data.images)} images packed, {muzzles} kit muzzles, '
      f'metres_per_unit {mpu}; appended and instanced 3x, turrets trained')
