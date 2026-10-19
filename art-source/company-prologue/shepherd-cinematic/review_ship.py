"""Review the exported Shepherd the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      [--views a,b] [--turn N] [--samples 32] [--out DIR] [--effects] [--lights-off]
      [--device GPU|CPU]

Appends `SHEPHERD` (and with --effects the optional `SWFX` collection, in the
same load) into a factory scene, lights it with a neutral studio (dark world,
key, fill, rim, under; the ship's own emissives are its only lamps) and renders
orthographic 1920x1080 views at 100%:
  <out>/<view>[-fx][-dark].png   stills
  <out>/turn/turn-NNN.png        --turn N: N frames of a 360-degree yaw of the
                                 ROOT under a fixed camera
--trained turns the turret 60 degrees off its rest angle first. Defaults go to
../shepherd-surface/build/final/ (ignored).
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
ap.add_argument('--views', default='three-quarter,starboard-quarter,top,stern-quarter,bow-quarter,under-quarter')
ap.add_argument('--turn', type=int, default=0)
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--out', default=str(HERE.parent / 'shepherd-surface' / 'build' / 'final'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--lights-off', action='store_true')
ap.add_argument('--trained', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
man = json.loads((HERE / 'manifest.json').read_text())
T0 = (2, 0, 0)
# (azimuth from +Y toward +X, elevation, ortho scale, target), the surfacing review's framing.
VIEWS = {
    'three-quarter': (43, 33, 128, T0),
    'starboard-quarter': (140, 30, 128, T0),
    'port-side': (0, 0, 118, T0),
    'top': (0, 90, 118, T0),
    'stern-quarter': (-50, 25, 128, T0),
    'bow-quarter': (75, 20, 125, T0),
    'under-quarter': (41, -35, 128, T0),
    'turret-detail': (150, 35, 40, (-3, -23, 6)),
    'dronebay-detail': (20, 45, 70, (10, 19, 2)),
}

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX'] if args.effects else [man['append_collection']]
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects[man['root']]
missing = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
assert not missing, f'unpacked images: {missing}'

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
world = bpy.data.worlds.new('Review studio')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (.045, .047, .05, 1)
scene.world = world
rig = bpy.data.collections.new('Review rig')
scene.collection.children.link(rig)
for name, loc, energy, size in (('Key', (60, 70, 120), 300000, 90), ('Fill', (-40, -60, 70), 90000, 80),
                                ('Rim', (-120, 30, 40), 140000, 60), ('Under', (20, 40, -90), 40000, 90)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy, ld.shape, ld.size = energy, 'DISK', size
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = loc
    lo.rotation_euler = (-Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
if args.lights_off:
    world.node_tree.nodes['Background'].inputs[1].default_value = 0.0
    for lo in rig.objects:
        lo.hide_render = True

if args.trained:
    piv = bpy.data.objects[man['turrets'][0]['pivot']]
    piv.rotation_euler.z += math.radians(60)
scene.frame_set(1)
lift = Vector()

cd = bpy.data.cameras.new('Review camera')
cd.type, cd.clip_end = 'ORTHO', 2000
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam


def aim(az, el, scale, target, dist=300):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target) + lift
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    if el >= 89.9:          # top: bow to the left, port below
        cam.rotation_euler = (0, 0, math.pi)
        cam.location = Vector((t[0], t[1], dist))
    cd.ortho_scale = scale


suffix = ('-trained' if args.trained else '') + ('-fx' if args.effects else '') + ('-dark' if args.lights_off else '')
OUT.mkdir(parents=True, exist_ok=True)
for v in [v for v in args.views.split(',') if v]:
    aim(*VIEWS[v])
    scene.render.filepath = str(OUT / f'{v}{suffix}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', v + suffix)
if args.turn:
    (OUT / 'turn').mkdir(exist_ok=True)
    aim(41, 22, 130, T0)
    for k in range(args.turn):
        root.rotation_euler = (0, 0, 2 * math.pi * k / args.turn)
        scene.render.filepath = str(OUT / 'turn' / f'turn-{k:03d}.png')
        bpy.ops.render.render(write_still=True)
    root.rotation_euler = (0, 0, 0)
    print('REVIEW turn', args.turn)
