"""Append the exported Onslaught into an empty scene and check it the way a shot uses it.

  sh ../../blender.sh --background --factory-startup --python verify_export.py -- [--blend export/onslaught.blend] [--render PATH]

Checks: the collection appends by name; every object descends from the root;
the six exhaust anchors and every turret pivot are there; training a pivot
moves its parts and nothing else; scaling the root by metres_per_unit gives
the manifest's size in metres. --render writes a 1920x1080 still of the
appended hull at 2 km under one sun (24 mm), as a smoke test.
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--blend', default=str(HERE/'export'/'onslaught.blend'))
ap.add_argument('--render')
args = ap.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
blend = Path(args.blend)
manifest = json.loads((blend.parent/'manifest.json').read_text(encoding='utf-8'))
COLL, ROOT = manifest['append_collection'], manifest['root']

bpy.ops.wm.read_factory_settings(use_empty=True)
with bpy.data.libraries.load(str(blend), link=False) as (src, dst):
    assert COLL in src.collections, f'{COLL} not in {blend}'
    dst.collections = [COLL]
coll = bpy.data.collections[COLL]
bpy.context.scene.collection.children.link(coll)
root = bpy.data.objects[ROOT]
fail = []


def top(ob):
    while ob.parent:
        ob = ob.parent
    return ob


strays = [o.name for o in coll.all_objects if top(o) is not root]
if strays:
    fail.append(f'{len(strays)} objects not under the root: {strays[:5]}')
for e in manifest['engines']:
    if e['anchor'] not in bpy.data.objects:
        fail.append('missing '+e['anchor'])
pivots = [s['pivot'] for s in manifest['weapon_slots'] if 'pivot' in s]
for p in pivots:
    if p not in bpy.data.objects:
        fail.append('missing '+p)

# Train one pivot: its children move, a neighbour's do not.
bpy.context.view_layer.update()
p = bpy.data.objects[pivots[0]]
kids = [c for c in p.children]
other = next(o for o in coll.all_objects if o.type == 'MESH' and o.parent is root)
before = [c.matrix_world.translation.copy() for c in kids], other.matrix_world.translation.copy()
p.rotation_euler.z += math.radians(30)
bpy.context.view_layer.update()
moved = sum((c.matrix_world.translation-b).length > 1e-4 for c, b in zip(kids, before[0]))
if not kids or moved == 0 or (other.matrix_world.translation-before[1]).length > 1e-6:
    fail.append(f'pivot {p.name}: {len(kids)} children, {moved} moved')
p.rotation_euler.z -= math.radians(30)

# Root scale gives metres.
root.scale = (manifest['metres_per_unit'],)*3
bpy.context.view_layer.update()
lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
for ob in coll.all_objects:
    if ob.type == 'MESH':
        for c in ob.bound_box:
            w = ob.matrix_world @ Vector(c)
            lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
size = [round(b-a, 1) for a, b in zip(lo, hi)]
if any(abs(a-b) > .5 for a, b in zip(size, manifest['size_metres'])):
    fail.append(f'size {size} m vs manifest {manifest["size_metres"]}')

if args.render:
    scene = bpy.context.scene
    world = bpy.data.worlds.new('Space')
    scene.world = world
    world.color = (0, 0, 0)
    sun = bpy.data.objects.new('Sun', bpy.data.lights.new('Sun', 'SUN'))
    sun.data.energy = 4.0
    sun.rotation_euler = Vector((.35, -.75, .55)).to_track_quat('Z', 'Y').to_euler()
    scene.collection.objects.link(sun)
    cam = bpy.data.objects.new('Camera', bpy.data.cameras.new('Camera'))
    cam.data.lens, cam.data.sensor_width, cam.data.clip_end = 24, 36, 10000
    centre = (lo+hi)/2
    cam.location = centre+Vector((1300, -1500, 500))
    cam.rotation_euler = (centre-cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.collection.objects.link(cam)
    scene.camera = cam
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 32
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.filepath = args.render
    bpy.ops.render.render(write_still=True)

print('VERIFY', 'OK' if not fail else 'FAIL', json.dumps({'objects': len(coll.all_objects), 'pivots': len(pivots),
                                                          'size_m': size, 'fail': fail}))
if fail:
    sys.exit(1)
