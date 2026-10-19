"""Review the built Lasher with its effects, the way a shot uses it.

  sh ../../blender.sh --background --factory-startup --python review_effects.py -- [--samples N] [--out DIR]

Appends `LASHER` and the optional `SWFX` collection in one load into a factory
scene, as export_effects.py describes, with a black sky, c1_core's sun
(SUN_IN_CAMERA for the 1.5 km view) and a faint rim so the hull reads; the
ship's lamps and the kit's drives are the only other light. Renders 1920x1080
at 100%:
  fx-stern-quarter.png   orthographic, from aft and above, port side
  fx-port-side.png       orthographic elevation
  fx-shot-1500m.png      c1_core's 50 mm camera at 1.5 km (perspective)
Outputs go to build/lasher/ (ignored).
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
ap.add_argument('--samples', type=int, default=48)
ap.add_argument('--out', default=str(HERE.parents[2] / 'build' / 'lasher'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
BLEND = OUT / 'lasher.blend'
geom = json.loads((HERE / 'geometry.json').read_text(encoding='utf-8'))
MPU = geom['metres_per_unit']

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(BLEND), link=False) as (src, dst):
    assert 'SWFX' in src.collections, 'run export_effects.py first'
    ship = next(c for c in src.collections if c.startswith('LASHER') and not c.endswith('| anchors'))
    dst.collections = [ship, 'SWFX']
for c in dst.collections:
    if not any(c.name in [ch.name for ch in p.children] for p in dst.collections):
        scene.collection.children.link(c)
bpy.context.view_layer.update()

scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.view_settings.view_transform = 'AgX'
scene.world = bpy.data.worlds.new('Space')
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (0, 0, 0, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0
scene.frame_set(24)

TARGET = Vector((10, 0, 0))
SUN_IN_CAMERA = Vector((-0.62, 0.50, 0.50)).normalized()
VIEWS = [
    ('fx-stern-quarter', 'ORTHO', TARGET + Vector((-150, 110, 80)), 150),
    ('fx-port-side', 'ORTHO', TARGET + Vector((0, 200, 0)), 140),
    ('fx-shot-1500m', 'PERSP', TARGET - Vector((-.35, -.9, -.26)).normalized() * (1500 / MPU), 50),
]
bpy.ops.object.light_add(type='SUN')
sun = bpy.context.object
sun.data.energy = 4.0
bpy.ops.object.light_add(type='SUN')
rim = bpy.context.object
rim.data.energy = .4
rim.rotation_euler = (Vector((0, 0, 0)) - Vector((40, -60, -30))).to_track_quat('-Z', 'Y').to_euler()
for name, kind, pos, lens in VIEWS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.data.type = kind
    if kind == 'ORTHO':
        cam.data.ortho_scale = lens
    else:
        cam.data.lens, cam.data.sensor_width = lens, 36
    cam.data.clip_end = (pos - TARGET).length * 3
    cam.rotation_euler = (TARGET - cam.location).to_track_quat('-Z', 'Y').to_euler()
    bpy.context.view_layer.update()
    to_sun = (cam.matrix_world.to_3x3() @ SUN_IN_CAMERA).normalized()
    sun.rotation_euler = (-to_sun).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam
    scene.render.filepath = str(OUT / (name + '.png'))
    bpy.ops.render.render(write_still=True)
print('FX REVIEW', [v[0] for v in VIEWS])
