"""Check lasher.blend is a cheap, appendable asset: link it the way a shot will.

  sh ../../blender.sh --background --factory-startup --python verify_asset.py -- [PATH]
Links the collection `LASHER` from PATH (default build/lasher/lasher.blend)
into an empty scene, instances it three times through collection-instance
empties scaled by metres_per_unit, and fails unless: one root parents every
object, no modifier or constraint is left, every engine has its bell, lip,
throat, can, exhaust and glow anchors, every turret has a pivot with its
slot, and the running lights are present. Prints the counts.
"""
from pathlib import Path
import sys
import bpy

HERE = Path(__file__).resolve().parent
argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
path = Path(argv[0] if argv else HERE.parents[2]/'build'/'lasher'/'lasher.blend').resolve()
COLL, ROOT = 'LASHER', 'LASHER | root'

bpy.ops.wm.read_factory_settings(use_empty=True)
with bpy.data.libraries.load(str(path), link=True) as (src, dst):
    assert COLL in src.collections, src.collections
    dst.collections = [COLL]
coll = dst.collections[0]
objs = list(coll.all_objects)
names = {o.name for o in objs}
root = bpy.data.objects[ROOT]
assert root.name in coll.objects
assert not [o.name for o in objs if o is not root and o.parent is None], 'objects outside the root'
assert not [o.name for o in objs if o.type == 'MESH' and o.modifiers], 'live modifiers left'
assert not [o.name for o in objs if o.constraints], 'constraints left'
assert not [o.name for o in objs if o.type in ('CAMERA', 'LIGHT')], 'review rig saved into the asset'
mpu = root['metres_per_unit']
for i in range(1, 6):
    for part in ('aft-facing bell', 'mouth lip', 'dark throat', 'drive can', 'exhaust anchor', 'drive glow anchor'):
        assert f'Engine {i} {part}' in names, f'Engine {i} {part}'
for i, slot in ((1, 'WS 005'), (2, 'WS 006'), (3, 'WS 007')):
    pivot = bpy.data.objects[f'Turret {i} pivot']
    assert pivot['slot'] == slot and any(c.name.startswith(f'Turret {i} barrel') for c in pivot.children)
for sid in ('WS 00%d' % k for k in range(1, 10)):
    assert f'Weapon {sid} anchor' in names, sid
lights = [n for n in names if n.startswith('Light ')]
assert len(lights) == 4, lights
scene = bpy.context.scene
for k in range(3):  # a formation: three instances, each scaled to metres
    inst = bpy.data.objects.new(f'Lasher {k+1}', None)
    inst.instance_type, inst.instance_collection = 'COLLECTION', coll
    inst.scale = (mpu,)*3
    inst.location = (k*120.0, k*-60.0, 0)
    scene.collection.objects.link(inst)
bpy.context.view_layer.update()
meshes = [o for o in objs if o.type == 'MESH']
tris = sum(len(p.vertices)-2 for o in meshes for p in o.data.polygons)
mats = sorted({m.name for o in meshes for m in o.data.materials if m})
print(f'OK {path.name}: collection {COLL!r}, root {ROOT!r}, {len(objs)} objects ({len(meshes)} meshes), '
      f'{tris} triangles, {len(mats)} materials, metres_per_unit {mpu}; linked and instanced 3x')
