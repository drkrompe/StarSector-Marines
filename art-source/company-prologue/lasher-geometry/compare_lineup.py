"""Render the Lasher beside other hulls at true scale, for an owner comparison.

  sh ../../blender.sh --background --factory-startup --python compare_lineup.py -- [--out DIR]
Appends the built Lasher (build/lasher/lasher.blend, collection LASHER) and
the Hound (A)'s tracked geometry (../hound-geometry/hound-geometry.blend),
puts each hull under its own empty scaled by that hull's metres_per_unit
(from each geometry.json), lines them up bow-left a fixed gap apart, and
renders two 1920x1080 frames in metres: a port-side elevation and a
three-quarter view from above, with a 10 m ground grid and a 1.8 m figure
for reference. Untextured hulls render flat grey-orange; this is a size and
proportion comparison, not a look.
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
CP = HERE.parent
ROOT_DIR = HERE.parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--out', default=str(ROOT_DIR/'build'/'lasher'))
parser.add_argument('--samples', type=int, default=32)
args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
OUT = Path(args.out)
OUT.mkdir(parents=True, exist_ok=True)

HULLS = [  # label, blend, collection (None = every mesh/empty in the file), geometry.json
    ('Hound (A)', CP/'hound-geometry'/'hound-geometry.blend', None, CP/'hound-geometry'/'geometry.json'),
    ('Lasher', ROOT_DIR/'build'/'lasher'/'lasher.blend', 'LASHER', HERE/'geometry.json'),
]
GAP = 25.0  # metres between hulls

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
placed = []
x_cursor = 0.0
for label, blend, coll_name, manifest in HULLS:
    mpu = json.loads(manifest.read_text(encoding='utf-8'))['metres_per_unit']
    with bpy.data.libraries.load(str(blend), link=False) as (src, dst):
        if coll_name:
            dst.collections = [coll_name]
        else:
            dst.objects = list(src.objects)
    objs = list(dst.collections[0].all_objects) if coll_name else [o for o in dst.objects if o]
    objs = [o for o in objs if o.type in ('MESH', 'EMPTY')]
    holder = bpy.data.objects.new(f'{label} holder', None)
    scene.collection.objects.link(holder)
    for o in objs:
        scene.collection.objects.link(o)
        if o.parent is None or o.parent not in objs:
            o.parent = holder
    holder.scale = (mpu,)*3
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for o in objs if o.type == 'MESH' for c in o.bound_box]
    lo = Vector(map(min, *pts))
    hi = Vector(map(max, *pts))
    # Bow left in the side view: hull +X points to world -X, sterns to the right.
    holder.rotation_euler = (0, 0, math.pi)
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for o in objs if o.type == 'MESH' for c in o.bound_box]
    lo = Vector(map(min, *pts))
    hi = Vector(map(max, *pts))
    holder.location = (x_cursor-lo.x, -(lo.y+hi.y)/2, -lo.z)
    bpy.context.view_layer.update()
    placed.append((label, mpu, x_cursor, x_cursor+(hi.x-lo.x), hi-lo))
    x_cursor += (hi.x-lo.x)+GAP

# 1.8 m figure between the hulls, a 10 m grid underfoot.
figure_x = placed[0][3]+GAP/2
bpy.ops.mesh.primitive_cylinder_add(radius=.25, depth=1.8, location=(figure_x, 0, .9))
fig = bpy.context.object
fig.name = 'Figure 1.8 m'
bpy.ops.mesh.primitive_plane_add(size=1, location=(x_cursor/2, 0, -.05))
ground = bpy.context.object
ground.scale = (x_cursor+80, 160, 1)
gm = bpy.data.materials.new('Grid')
gm.use_nodes = True
nt = gm.node_tree
bsdf = nt.nodes['Principled BSDF']
tex = nt.nodes.new('ShaderNodeTexCoord')
mapn = nt.nodes.new('ShaderNodeMapping')
mapn.inputs['Scale'].default_value = (.1, .1, .1)
grid = nt.nodes.new('ShaderNodeTexBrick')
grid.inputs['Mortar Size'].default_value = .012
grid.inputs['Color1'].default_value = (.62, .63, .64, 1)
grid.inputs['Color2'].default_value = (.62, .63, .64, 1)
grid.inputs['Mortar'].default_value = (.35, .36, .37, 1)
grid.offset = 0
grid.inputs['Brick Width'].default_value = 1
grid.inputs['Row Height'].default_value = 1
nt.links.new(tex.outputs['Object'], mapn.inputs['Vector'])
nt.links.new(mapn.outputs['Vector'], grid.inputs['Vector'])
nt.links.new(grid.outputs['Color'], bsdf.inputs['Base Color'])
ground.data.materials.append(gm)

scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.view_settings.view_transform = 'AgX'
scene.world = bpy.data.worlds.new('World')
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.74, .76, .78, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = .6
bpy.ops.object.light_add(type='SUN')
sun = bpy.context.object
sun.data.energy = 3.5
sun.rotation_euler = (math.radians(40), math.radians(20), math.radians(-30))

span = x_cursor-GAP
centre = Vector((span/2, 0, 8))
VIEWS = [('compare-side', centre+Vector((0, -400, 0)), span*1.12),
         ('compare-three-quarter', centre+Vector((-180, -300, 220)), span*1.2)]
for name, pos, scale in VIEWS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.data.type, cam.data.ortho_scale, cam.data.clip_end = 'ORTHO', scale, 2000
    cam.rotation_euler = (centre-cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

(OUT/'compare-lineup.json').write_text(json.dumps(
    [{'hull': l, 'metres_per_unit': m, 'size_m': [round(v, 1) for v in size]} for l, m, a, b, size in placed],
    indent=2)+'\n', encoding='utf-8')
print('LINEUP', [(l, m, [round(v, 1) for v in size]) for l, m, a, b, size in placed])
