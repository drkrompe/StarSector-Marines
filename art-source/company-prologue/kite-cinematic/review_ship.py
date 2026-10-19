"""Review the exported Kite (P) the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      [--views a,b] [--samples 16] [--out DIR] [--effects] [--shot]

Appends `KITE (P)` (and with --effects the optional `SWFX` collection, in the
same load) into a factory scene and renders 1920x1080 at 100%:
- studio views (orthographic, dark world, key/fill/rim/under; the ship's own
  emissives are its only lamps): port-quarter, starboard-quarter, stern-quarter,
  top;
- --shot: the S9 read (asharu-battle-shot.md: 28 mm, the Kites 350-550 m off):
  the root scaled to metres, a 28 mm perspective camera 450 m off her starboard
  bow, lit only by the battle blockout's sun (elevation 16, azimuth -40,
  4.6 W/m2) against black space, drives and lamps lit.
Defaults go to ../kite-surface/build/final/ (ignored).
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
ap.add_argument('--views', default='port-quarter,stern-quarter')
ap.add_argument('--samples', type=int, default=16)
ap.add_argument('--out', default=str(HERE.parent / 'kite-surface' / 'build' / 'final'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--shot', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
man = json.loads((HERE / 'manifest.json').read_text())
MPU = man['metres_per_unit']
T0 = (4, 0, -1)
# (azimuth from +Y toward +X, elevation, ortho scale, target)
VIEWS = {
    'port-quarter': (41, 31.5, 125, T0),
    'starboard-quarter': (140, 30, 125, T0),
    'stern-quarter': (-50, 25, 125, T0),
    'top': (0, 90, 125, T0),
}
SUN_EL, SUN_AZ, SUN_W = 16.0, -40.0, 4.6     # asharu-battle/battle_blockout.py

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX'] if (args.effects or args.shot) else [man['append_collection']]
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects[man['root']]

scene.render.engine = 'CYCLES'
prefs = bpy.context.preferences.addons['cycles'].preferences
for kind in ('METAL', 'OPTIX', 'CUDA'):
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
world = bpy.data.worlds.new('Review world')
world.use_nodes = True
scene.world = world
rig = bpy.data.collections.new('Review rig')
scene.collection.children.link(rig)
cd = bpy.data.cameras.new('Review camera')
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam
scene.frame_set(1)
OUT.mkdir(parents=True, exist_ok=True)
fx = '-fx' if args.effects else ''

if args.shot:
    world.node_tree.nodes['Background'].inputs[0].default_value = (0, 0, 0, 1)
    root.scale = (MPU,) * 3
    sun = bpy.data.objects.new('Asharu sun', bpy.data.lights.new('Asharu sun', 'SUN'))
    rig.objects.link(sun)
    sun.data.energy, sun.data.color, sun.data.angle = SUN_W, (1.0, .96, .88), math.radians(.5)
    d = Vector((math.cos(math.radians(SUN_EL)) * math.cos(math.radians(SUN_AZ)),
                math.cos(math.radians(SUN_EL)) * math.sin(math.radians(SUN_AZ)), math.sin(math.radians(SUN_EL))))
    sun.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    cd.type, cd.lens, cd.sensor_width, cd.clip_end = 'PERSP', 28.0, 36.0, 5000
    a, e = math.radians(140), math.radians(12)    # her starboard bow quarter, the sun's side
    cam.location = 450 * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (-cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.render.filepath = str(OUT / 'shot-s9-450m.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW shot s9 at 450 m, 28 mm')
    sys.exit(0)

world.node_tree.nodes['Background'].inputs[0].default_value = (.045, .047, .05, 1)
for name, loc, energy, size in (('Key', (80, 90, 150), 450000, 110), ('Fill', (-50, -80, 90), 140000, 100),
                                ('Rim', (-150, 40, 50), 200000, 80), ('Under', (25, 50, -110), 60000, 110)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy, ld.shape, ld.size = energy, 'DISK', size
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = loc
    lo.rotation_euler = (-Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
cd.type, cd.clip_end = 'ORTHO', 2000
for v in [v for v in args.views.split(',') if v]:
    az, el, scale, target = VIEWS[v]
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cam.location = t + 300 * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    if el >= 89.9:
        cam.rotation_euler, cam.location = (0, 0, math.pi), Vector((t[0], t[1], 300))
    cd.ortho_scale = scale
    scene.render.filepath = str(OUT / f'{v}{fx}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', v + fx)
