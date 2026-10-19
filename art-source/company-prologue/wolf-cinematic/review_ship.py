"""Review the Wolf (P) cinematic asset the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      [--views a,b] [--effects] [--fire FRAME] [--lights-off] [--samples 32] [--out DIR] [--device GPU|CPU]

Appends `WOLF_P` (with --effects or --fire also `SWFX`, in the same load)
into a factory scene and renders 1920x1080 at 100% in Cycles:

- studio views (orthographic, a dark neutral studio: key, fill, rim, under):
  `three-quarter` (port bow quarter), `stern-quarter` (starboard quarter from
  aft), `engine-detail` (the three bells), `bow-fire` (close on the bow and
  turret from ahead and to port);
- `asharu-s9`: the battle's close shot: S9's 28 mm lens on a 36 mm sensor
  670 m off the hull, on its port bow quarter from a little above, one hard
  low sun from the camera's left (asharu-battle-shot.md: low, side-lighting),
  no fill, black sky;
- --fire FRAME rebuilds the kit on the appended ship the way a shot does
  (swfx.apply with vfx_hull.py) and fires it: the ion cannon and the mining
  blaster a few bolts each, both Annihilator pods a full ripple of five,
  then renders frame FRAME with bolts and rockets in flight. Turret 1 is
  trained 20 degrees to port for it;
- --lights-off drops the studio, leaving the ship's own lamps and drive glow.

Outputs go to build/review/ as <view>[-fx|-fire][-dark].png.
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
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--out', default=str(HERE / 'build' / 'review'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--fire', type=int, default=0)
ap.add_argument('--lights-off', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
ap.add_argument('--variant', default='pirate', choices=('pirate', 'merchant'))
ap.add_argument('--res', default='1920x1080')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
import os
os.environ['WOLF_VARIANT'] = args.variant
man = json.loads((HERE / ('manifest-merchant.json' if args.variant == 'merchant' else 'manifest.json')).read_text())
MPU = man['metres_per_unit']
(lx, ly, lz), (hx, hy, hz) = man['bounds_units']
MID = Vector(((lx + hx) / 2, 0, (lz + hz) / 2))
T0 = Vector((7, 0, 0))
# Studio views: (azimuth from +Y toward +X, elevation, ortho scale, target).
VIEWS = {
    'three-quarter': (50, 28, 128, T0),
    'stern-quarter': (-130, 26, 128, T0),
    'engine-detail': (-125, 14, 52, Vector((-36, 0, 0))),
    'bow-fire': (60, 22, 90, Vector((30, 4, 2))),
}
# The close Asharu shot: (azimuth from +X toward +Y, elevation, metres, lens mm).
SHOTS = {'asharu-s9': (55, 16, 670.0, 28.0)}
SUN_IN_CAMERA = Vector((-0.70, 0.35, 0.62)).normalized()

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
names = [man['append_collection'], 'SWFX'] if (args.effects or args.fire) else [man['append_collection']]
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
scene.render.resolution_x, scene.render.resolution_y = map(int, args.res.split('x'))
scene.render.resolution_percentage = 100
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
STUDIO = []
for name, loc, energy, size in (('Key', (60, 70, 120), 300000, 90), ('Fill', (-40, -60, 70), 90000, 80),
                                ('Rim', (-120, 30, 40), 140000, 60), ('Under', (20, 40, -90), 40000, 90)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy, ld.shape, ld.size = energy, 'DISK', size
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = T0 + Vector(loc)
    lo.rotation_euler = (T0 - lo.location).to_track_quat('-Z', 'Y').to_euler()
    STUDIO.append(lo)
sd = bpy.data.lights.new('Asharu sun', 'SUN')
sd.energy, sd.angle = 4.5, math.radians(.53)
sun = bpy.data.objects.new('Asharu sun', sd)
rig.objects.link(sun)
cd = bpy.data.cameras.new('Review camera')
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam
scene.frame_set(1)

if args.fire:
    sys.path.insert(0, str(HERE.parent / 'second-wind-vfx'))
    import swfx  # noqa: E402
    ctx = swfx.apply(state='clean', hull=HERE, dust=False, haze=False)
    ctx.set('smk_atmosphere', 0.0)
    guns = swfx.module('guns')
    for t in man['turrets']:
        bpy.data.objects[t['pivot']].rotation_euler.z += math.radians(20)
    F = args.fire
    for m in man['vfx_mounts']:
        if m['kind'] == 'launcher':
            n = len(m['muzzles'])
            guns.fire(ctx, m['index'], F - 3 - 3 * (n - 1) - 2, n, 3)
        else:
            guns.fire(ctx, m['index'], F - 8 - (m['index'] - 1), 3, 4)
    scene.frame_set(F)
    scene.cycles.volume_step_rate = 2.0


def studio(on):
    space = not on
    for lo in STUDIO:
        lo.hide_render = space or args.lights_off
    sun.hide_render = not space
    bg.inputs[0].default_value = (0, 0, 0, 1) if space else (.045, .047, .05, 1)
    bg.inputs[1].default_value = 0.0 if (space or args.lights_off) else 1.0


def aim_ortho(az, el, scale, target, dist=600):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cd.type, cd.ortho_scale, cd.clip_start, cd.clip_end = 'ORTHO', scale, 1, 4000
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()


def aim_shot(az, el, metres, lens):
    d = metres / MPU
    a, e = math.radians(az), math.radians(el)
    cd.type, cd.lens, cd.sensor_width, cd.sensor_fit = 'PERSP', lens, 36.0, 'HORIZONTAL'
    cd.clip_start, cd.clip_end = 10, 20000
    cam.location = (MID.x + d * math.cos(e) * math.cos(a), d * math.cos(e) * math.sin(a), MID.z + d * math.sin(e))
    cam.rotation_euler = (MID - cam.location).to_track_quat('-Z', 'Y').to_euler()
    bpy.context.view_layer.update()
    s = (cam.matrix_world.to_3x3() @ SUN_IN_CAMERA).normalized()
    sun.rotation_euler = (-s).to_track_quat('-Z', 'Y').to_euler()


suffix = ('-fx' if args.effects else '') + ('-fire' if args.fire else '') + ('-dark' if args.lights_off else '')
OUT.mkdir(parents=True, exist_ok=True)
for v in [v for v in args.views.split(',') if v]:
    if v in SHOTS:
        studio(False)
        aim_shot(*SHOTS[v])
    else:
        studio(True)
        aim_ortho(*VIEWS[v])
    scene.render.filepath = str(OUT / f"{v}{suffix}{'-merchant' if args.variant == 'merchant' else ''}.png")
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', v + suffix, flush=True)
