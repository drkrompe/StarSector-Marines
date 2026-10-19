"""Render the Broadsword the way C2 sees it: passing a bridge window at 55-80 m.

  blender --background --factory-startup --python review_pass.py -- [--samples 64] [--out DIR] [--device GPU|CPU]

Appends `BROADSWORD` and `SWFX` in one load into a factory scene: black space,
one hard sun from high and ahead, nothing else lighting the fighter but its own
drives and running lights. The root is scaled to metres (x metres_per_unit), so
the camera stands in metres: a 24 mm lens at eye height, the fighter crossing
left to right at 55 m and at 80 m, flying level with its gear up.
Writes <out>/pass-55m.png and pass-80m.png at 1920x1080, 100%. Defaults to
../fighters-surface/broadsword/build/final/ (ignored).
This is a review of the asset at the shot's distance, not the C2 layout.
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
ap.add_argument('--samples', type=int, default=64)
ap.add_argument('--out', default=str(HERE.parent / 'fighters-surface' / 'broadsword' / 'build' / 'final'))
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
man = json.loads((HERE / 'manifest.json').read_text())
MPU = man['metres_per_unit']

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX']
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects[man['root']]

scene.render.engine = 'CYCLES'
prefs = bpy.context.preferences.addons['cycles'].preferences
for kind in (('OPTIX', 'CUDA') if args.device == 'GPU' else ()):
    try:
        prefs.compute_device_type = kind
        prefs.get_devices()
        if any(d.type == kind for d in prefs.devices):
            for d in prefs.devices:
                d.use = d.type == kind
            scene.cycles.device = 'GPU'
            break
    except TypeError:
        continue
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.view_settings.view_transform = 'AgX'
world = bpy.data.worlds.new('Space')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (0, 0, 0, 1)
world.node_tree.nodes['Background'].inputs[1].default_value = 0.0
scene.world = world
sd = bpy.data.lights.new('Sun', 'SUN')
sd.energy, sd.angle = 5.0, math.radians(.53)
sun = bpy.data.objects.new('Sun', sd)
scene.collection.objects.link(sun)
# High, ahead of the fighter and a little toward the camera: lit flank, shadowed belly.
d = Vector((.55, -.45, .7)).normalized()
sun.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()

# Metres: the fighter flies along +X at the origin; the camera stands to starboard.
root.scale = (MPU,) * 3
root.rotation_euler = (math.radians(4), 0, 0)    # a slight bank toward the window
cd = bpy.data.cameras.new('Bridge')
cd.lens, cd.clip_end = 24, 5000
cam = bpy.data.objects.new('Bridge', cd)
scene.collection.objects.link(cam)
scene.camera = cam
scene.frame_set(1)
OUT.mkdir(parents=True, exist_ok=True)
for dist in (55, 80):
    # dist metres away, abeam and a little aft, a little above her
    cam.location = Vector((2.0, 0, 0)) + Vector((-.35, -1, .1)).normalized() * dist
    cam.rotation_euler = (Vector((2.0, 0, 0)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.render.filepath = str(OUT / f'pass-{dist}m.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW pass', dist)
