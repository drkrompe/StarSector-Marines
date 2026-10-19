"""Review the Onslaught cinematic asset the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      [--views a,b] [--effects] [--lights-off] [--turn N] [--samples 32] [--out DIR]
      [--device GPU|CPU]

Appends `ONSLAUGHT | clean` (and with --effects the optional `SWFX`
collection, in the same load) into a factory scene and renders 1920x1080 at
100% in Cycles:

- studio views (orthographic, the surfacing review's neutral studio: dark
  world, key, fill, rim and under, scaled to the 300 m hull):
  `three-quarter` (starboard bow quarter) and `stern-quarter` (port quarter,
  from aft) and `engine-detail` (the six bells from the port quarter);
  --lights-off drops the studio, leaving the ship's own lamps and
  the drive glow;
- `formation-1.5km`: the C1 v2 space look, as the geometry build's formation
  cameras: a 24 mm lens on a 36 mm sensor 1.5 km off the hull's middle
  (azimuth -40 degrees from the bow toward port, elevation 18), one hard sun
  (0.53 degrees, from (.35, -.75, .55)), no fill, black world;
- --turn N: N frames of a 360-degree yaw of the ROOT under a fixed
  quarter-elevation studio camera, into <out>/turn/turn-NNN.png.

Outputs go to build/review/ beside this script (ignored) as
<view>[-fx][-dark].png.
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
ap.add_argument('--views', default='three-quarter,stern-quarter')
ap.add_argument('--turn', type=int, default=0)
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--out', default=str(HERE / 'build' / 'review'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--lights-off', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
man = json.loads((HERE / 'manifest.json').read_text())
MPU = man['metres_per_unit']
(lx, ly, lz), (hx, hy, hz) = man['bounds_units']
MID = Vector(((lx + hx) / 2, 0, (lz + hz) / 2))
T0 = Vector((52, 0, 4))
# Studio views: (azimuth from +Y toward +X, elevation, ortho scale, target).
VIEWS = {
    'three-quarter': (128, 28, 430, T0),
    'stern-quarter': (-48, 24, 430, T0 + Vector((-25, 0, 0))),
    'engine-detail': (-62, 14, 200, Vector((-112, 4, 4))),
}
TURN_VIEW = (128, 22, 450, T0)
# Formation: (azimuth from +X toward +Y, elevation, km), build_onslaught.py's FORMATION.
FORMATION = {'formation-1.5km': (-40, 18, 1.5)}

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
names = [man['append_collection'], 'SWFX'] if args.effects else [man['append_collection']]
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = names
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects[man['root']]
missing = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
assert not missing, f'unpacked images: {missing}'

scene.render.engine = 'CYCLES'
prefs = bpy.context.preferences.addons['cycles'].preferences
for kind in (('METAL', 'OPTIX', 'CUDA') if args.device == 'GPU' else ()):
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
scene.cycles.volume_bounces = 1
scene.render.use_persistent_data = True
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, 100
scene.render.fps = 24
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.view_settings.view_transform = 'AgX'
world = bpy.data.worlds.new('Review studio')
world.use_nodes = True
bg = world.node_tree.nodes['Background']
scene.world = world
rig = bpy.data.collections.new('Review rig')
scene.collection.children.link(rig)
# The Shepherd's studio scaled to this hull: positions x4, so energy x16.
K = 4.0
STUDIO = []
for name, loc, energy, size in (('Key', (60, -70, 120), 300000, 90), ('Fill', (-40, 60, 70), 90000, 80),
                                ('Rim', (-120, -30, 40), 140000, 60), ('Under', (20, -40, -90), 40000, 90)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy, ld.shape, ld.size = energy * K * K, 'DISK', size * K
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = T0 + K * Vector(loc)
    lo.rotation_euler = (T0 - lo.location).to_track_quat('-Z', 'Y').to_euler()
    STUDIO.append(lo)
sd = bpy.data.lights.new('Space sun', 'SUN')
sd.energy, sd.angle = 4.0, math.radians(.53)
sun = bpy.data.objects.new('Space sun', sd)
rig.objects.link(sun)
sun.rotation_euler = Vector((.35, -.75, .55)).to_track_quat('Z', 'Y').to_euler()

cd = bpy.data.cameras.new('Review camera')
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam
scene.frame_set(1)


def studio(on):
    space = not on
    for lo in STUDIO:
        lo.hide_render = space or args.lights_off
    sun.hide_render = not space
    bg.inputs[0].default_value = (0, 0, 0, 1) if space else (.045, .047, .05, 1)
    bg.inputs[1].default_value = 0.0 if (space or args.lights_off) else 1.0


def aim_ortho(az, el, scale, target, dist=1200):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cd.type, cd.ortho_scale, cd.clip_start, cd.clip_end = 'ORTHO', scale, 1, 4000
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()


def aim_formation(az, el, km):
    d = km * 1000 / MPU
    a, e = math.radians(az), math.radians(el)
    cd.type, cd.lens, cd.sensor_width, cd.sensor_fit = 'PERSP', 24.0, 36.0, 'HORIZONTAL'
    cd.clip_start, cd.clip_end = 10, 20000
    cam.location = (MID.x + d * math.cos(e) * math.cos(a), d * math.cos(e) * math.sin(a), MID.z + d * math.sin(e))
    cam.rotation_euler = (MID - cam.location).to_track_quat('-Z', 'Y').to_euler()


suffix = ('-fx' if args.effects else '') + ('-dark' if args.lights_off else '')
OUT.mkdir(parents=True, exist_ok=True)
for v in [v for v in args.views.split(',') if v]:
    if v in FORMATION:
        studio(False)
        aim_formation(*FORMATION[v])
    else:
        studio(True)
        aim_ortho(*VIEWS[v])
    scene.render.filepath = str(OUT / f'{v}{suffix}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', v + suffix, flush=True)
if args.turn:
    (OUT / 'turn').mkdir(exist_ok=True)
    studio(True)
    aim_ortho(*TURN_VIEW)
    for k in range(args.turn):
        root.rotation_euler = (0, 0, 2 * math.pi * k / args.turn)
        scene.frame_set(1 + k)
        scene.render.filepath = str(OUT / 'turn' / f'turn-{k:03d}.png')
        bpy.ops.render.render(write_still=True)
    root.rotation_euler = (0, 0, 0)
    print('REVIEW turn', args.turn, flush=True)
