"""Review an exported Tarsus state the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      --state clean|boarded [--views a,b] [--samples 16] [--effects] [--frame N] [--device CPU|GPU] [--out DIR]

Appends `TARSUS` (and with --effects the optional `SWFX` collection, in the same
load) into a factory scene, lights it with a neutral studio (dark world, key,
fill, rim, under; the ship's own emissives are its only lamps) and renders
orthographic 1920x1080 views at 100% to <out>/<state>-<view>[-fx].png.
Views are the battle's close cameras (s7-astern, s10-above-behind) and the
surfacing review's three-quarter. With --effects on the boarded state and no
--frame, it renders the first frame after 24 at which the scar sparks are
bursting, so one still shows both the smoke and the sparks. The CPU is the
default device: the shared GPU is usually saturated. Defaults go to
../tarsus-surface/build/final/ (ignored).
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'second-wind-vfx'))
ap = argparse.ArgumentParser()
ap.add_argument('--state', choices=('clean', 'boarded'), default='clean')
ap.add_argument('--views', default='s7-astern')
ap.add_argument('--samples', type=int, default=16)
ap.add_argument('--out', default=str(HERE.parent / 'tarsus-surface' / 'build' / 'final'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--frame', type=int)
ap.add_argument('--device', default='CPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
DIR = HERE / args.state
man = json.loads((DIR / 'manifest.json').read_text())
# (azimuth from +Y toward +X, elevation, ortho scale, target): the surfacing review's framing.
VIEWS = {
    's7-astern': (-90, 11, 150, (-40, 1, 0)),
    's10-above-behind': (-121, 36, 200, (5, 1, 0)),
    'three-quarter': (43, 33, 250, (16, 1, 0)),
}
S = 2.2        # the Colossus review studio (108 m) scaled to a 94 m ship's framing

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(DIR / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX'] if args.effects else [man['append_collection']]
for c in dst.collections:
    scene.collection.children.link(c)

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
T0 = Vector((16, 1, 0))
for name, loc, energy, size in (('Key', (60, 70, 120), 300000, 90), ('Fill', (-40, -60, 70), 90000, 80),
                                ('Rim', (-120, 30, 40), 140000, 60), ('Under', (20, 40, -90), 40000, 90)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy, ld.shape, ld.size = energy * S * S, 'DISK', size * S
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = Vector(loc) * S + T0
    lo.rotation_euler = (-Vector(loc)).to_track_quat('-Z', 'Y').to_euler()

frame = args.frame
if frame is None:
    frame = 24
    if args.effects and args.state == 'boarded':
        import swfx_smoke
        names = [a['anchor'] for a in man['scar_anchors'] if a['sparking']]
        bursts = sorted(b for n in names for b in swfx_smoke.spark_bursts(swfx_smoke.spark_seed(n), 24, 24 * 20))
        if bursts:
            frame = bursts[0][0] + 1
scene.frame_set(frame)

cd = bpy.data.cameras.new('Review camera')
cd.type, cd.clip_end = 'ORTHO', 3000
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam


def aim(az, el, scale, target, dist=700):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cd.ortho_scale = scale


OUT.mkdir(parents=True, exist_ok=True)
suffix = '-fx' if args.effects else ''
for v in [v for v in args.views.split(',') if v]:
    aim(*VIEWS[v])
    scene.render.filepath = str(OUT / f'{args.state}-{v}{suffix}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', args.state, v + suffix, 'frame', frame)
