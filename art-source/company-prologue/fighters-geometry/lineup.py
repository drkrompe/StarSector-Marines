"""Common-scale lineup of every fighter blockout, in metres.

Run from this folder after the per-fighter builds:
  blender --background --factory-startup --python lineup.py -- [--samples N]
  python compose_lineup.py
Appends each fighter's "<NAME> | geometry" collection from its own .blend
(the Broadsword's from ../broadsword-geometry/), scales each root by its
own metres_per_unit so the row is in metres, turns them nose-up and lays
them out longest first. Renders lineup-top.png (orthographic, transparent)
and lineup-three-quarter.png, writes lineup.json (placement and pixel
centres for the labels) and saves fighters-lineup.blend. The per-fighter
.blend files stay in hull units; this file is for comparison only.
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
FIGHTERS = [  # id, blend, collection
    ('broadsword', HERE.parent/'broadsword-geometry'/'broadsword-geometry.blend', 'BROADSWORD | geometry'),
    ('talon', HERE/'talon'/'talon-geometry.blend', 'TALON | geometry'),
    ('wasp', HERE/'wasp'/'wasp-geometry.blend', 'WASP | geometry'),
    ('dagger', HERE/'dagger'/'dagger-geometry.blend', 'DAGGER | geometry'),
    ('longbow', HERE/'longbow'/'longbow-geometry.blend', 'LONGBOW | geometry'),
    ('thunder', HERE/'thunder'/'thunder-geometry.blend', 'THUNDER | geometry'),
]
GAP = 3.0  # metres between neighbours

parser = argparse.ArgumentParser()
parser.add_argument('--samples', type=int, default=32)
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])

for ob in list(bpy.data.objects):
    bpy.data.objects.remove(ob, do_unlink=True)
scene = bpy.context.scene
placed = []
for fid, blend, cname in FIGHTERS:
    if not blend.exists():
        print('LINEUP skip', fid, '(no blend)')
        continue
    with bpy.data.libraries.load(str(blend), link=False) as (src, dst):
        dst.collections = [cname]
    col = dst.collections[0]
    scene.collection.children.link(col)
    root = next(o for o in col.objects if o.parent is None and o.type == 'EMPTY')
    m = root['metres_per_unit']
    root.scale = (m, m, m)
    root.rotation_euler = (0, 0, math.pi/2)  # nose up the frame
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
    for ob in col.all_objects:
        if ob.type == 'MESH':
            for c in ob.evaluated_get(dg).bound_box:
                w = ob.matrix_world @ Vector(c)
                lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
    placed.append({'id': fid, 'name': root.name.replace(' root', ''), 'root': root, 'lo': lo, 'hi': hi, 'mpu': m})
placed.sort(key=lambda p: -(p['hi'].y-p['lo'].y))
x = 0.0
for p in placed:
    w = p['hi'].x-p['lo'].x
    shift = Vector((x-p['lo'].x, -(p['lo'].y+p['hi'].y)/2, -p['lo'].z))
    p['root'].location += shift
    p['lo'], p['hi'] = p['lo']+shift, p['hi']+shift
    x += w+GAP
total_w = x-GAP
max_len = max(p['hi'].y-p['lo'].y for p in placed)

scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.view_settings.view_transform = 'AgX'
if scene.world is None:
    scene.world = bpy.data.worlds.new('World')
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']
bg.inputs[0].default_value = (.74, .76, .78, 1)
bg.inputs[1].default_value = .6
centre = Vector((total_w/2, 0, 1))
for name, d, energy in [('Key', Vector((.5, -.6, 1)), 60000), ('Fill', Vector((-.6, .5, .7)), 30000)]:
    bpy.ops.object.light_add(type='AREA', location=centre+d*total_w)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy*(total_w/90)**2
    obj.data.size = total_w*.6
    obj.rotation_euler = (centre-obj.location).to_track_quat('-Z', 'Y').to_euler()

top_scale = max(total_w*1.08, max_len*1.6*16/9)
bpy.ops.object.camera_add(location=(total_w/2, -max_len*.12, 200))
top = bpy.context.object
top.name = 'Camera lineup-top'
top.data.type = 'ORTHO'
top.data.ortho_scale = top_scale
top.data.clip_end = 1000
D = max(total_w, 3*max_len)
bpy.ops.object.camera_add(location=centre+Vector((-.25*D, 1.0*D, .6*D)))  # ahead of the noses, high
tq = bpy.context.object
tq.name = 'Camera lineup-three-quarter'
tq.data.type = 'ORTHO'
tq.data.ortho_scale = total_w*1.08
tq.data.clip_end = 1000
tq.rotation_euler = (centre-tq.location).to_track_quat('-Z', 'Y').to_euler()

for cam, fname, transparent in ((top, 'lineup-top.png', True), (tq, 'lineup-three-quarter.png', False)):
    scene.camera = cam
    scene.render.film_transparent = transparent
    scene.render.filepath = str(HERE/fname)
    bpy.ops.render.render(write_still=True)
scene.render.film_transparent = False

k = 1920/top_scale  # px per metre in the top view
cx, cy = top.location.x, top.location.y
record = {'px_per_metre': k, 'gap_m': GAP, 'fighters': []}
for p in placed:
    record['fighters'].append({
        'id': p['id'], 'name': p['name'], 'metres_per_unit': p['mpu'],
        'length_m': round(p['hi'].y-p['lo'].y, 1), 'span_m': round(p['hi'].x-p['lo'].x, 1),
        'height_m': round(p['hi'].z-p['lo'].z, 1),
        'centre_px': [960+((p['lo'].x+p['hi'].x)/2-cx)*k, 540-((p['lo'].y+p['hi'].y)/2-cy)*k],
        'bottom_px': 540-(p['lo'].y-cy)*k, 'top_px': 540-(p['hi'].y-cy)*k})
(HERE/'lineup.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
scene.camera = tq
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'fighters-lineup.blend'))
print('LINEUP', [(f['name'], f['length_m']) for f in record['fighters']])
