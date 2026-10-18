"""Review the exported Hound the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_hound.py -- [--state clean]
      [--views a,b] [--turn N] [--samples 32] [--effects] [--out DIR] [--device GPU|CPU]

Appends `HOUND (A) | <state>` (and with --effects the `SWFX` collection, in the
same load) into a factory scene, lights it with second-wind-states/review_state.py's
neutral studio, and renders orthographic 1920x1080 at 100%:
  <out>/<view>[-fx].png        stills
  <out>/turn/turn-NNN.png      --turn N: N frames of a 360-degree yaw of the ROOT
                               under a fixed camera and fixed lamps
`ramp-open` lowers the ramp to its ground contact; `turret-trained` trains WS 002
forward (0 degrees). Default output: hound-export/<state>/review (ignored).
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
ap.add_argument('--state', default='clean')
ap.add_argument('--views', default='port-quarter,starboard-quarter,port-side,top,stern-quarter,bow-quarter,'
                                   'under-quarter,ramp-open,turret-trained')
ap.add_argument('--turn', type=int, default=0)
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--out')
ap.add_argument('--effects', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
DIR = HERE / args.state
man = json.loads((DIR / 'manifest.json').read_text())
OUT = Path(args.out or DIR / 'review').resolve()
T0 = (8, 0, 2)
# (azimuth from +Y toward +X, elevation, ortho scale, target)
VIEWS = {
    'port-quarter': (37.6, 24, 100, T0),
    'starboard-quarter': (142.4, 24, 100, T0),
    'port-side': (0, 0, 84, T0),
    'top': (0, 90, 92, T0),
    'stern-quarter': (-40, 20, 96, T0),
    'bow-quarter': (70, 18, 96, T0),
    'under-quarter': (35, -32, 100, T0),
    'ramp-open': (-62, -22, 34, (-15, 0, -5)),
    'turret-trained': (30, 32, 40, (-4, 4, 8)),
}

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
want = [man['append_collection']] + (['SWFX'] if args.effects else [])
with bpy.data.libraries.load(str(DIR / man['asset']), link=False) as (src, dst):
    dst.collections = want
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects[man['root']]
hinge = bpy.data.objects[man['ramp']['hinge']]
pivot = bpy.data.objects[man['turrets'][0]['pivot']['name']]

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
# review_state.py's studio, scaled to a hull two-thirds the Second Wind's length.
for name, loc, energy, size in (('Key', (60, 70, 120), 300000, 90), ('Fill', (-40, -60, 70), 90000, 80),
                                ('Rim', (-120, 30, 40), 140000, 60), ('Under', (20, 40, -90), 40000, 90)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy, ld.shape, ld.size = energy, 'DISK', size
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = loc
    lo.rotation_euler = (-Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
cd = bpy.data.cameras.new('Review camera')
cd.type, cd.clip_end = 'ORTHO', 2000
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam


def aim(az, el, scale, target, dist=300):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    if el >= 89.9:          # top: bow to the left, as the geometry review
        cam.rotation_euler = (0, 0, math.pi)
        cam.location = Vector((t[0], t[1], dist))
    cd.ortho_scale = scale


def pose(view):
    hinge.rotation_euler.y = -math.radians(man['ramp']['ground_contact_deg']) if view == 'ramp-open' else 0.0
    rest = math.radians(man['turrets'][0]['pivot']['rest_angle_deg'])
    pivot.rotation_euler.z = -rest if view == 'turret-trained' else 0.0


OUT.mkdir(parents=True, exist_ok=True)
fx = '-fx' if args.effects else ''
for v in [v for v in args.views.split(',') if v]:
    pose(v)
    aim(*VIEWS[v])
    scene.render.filepath = str(OUT / f'{v}{fx}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', v)
pose(None)
if args.turn:
    (OUT / f'turn{fx}').mkdir(exist_ok=True)
    aim(37.6, 22, 112, T0)
    for k in range(args.turn):
        root.rotation_euler = (0, 0, 2 * math.pi * k / args.turn)
        scene.render.filepath = str(OUT / f'turn{fx}' / f'turn-{k:03d}.png')
        bpy.ops.render.render(write_still=True)
    root.rotation_euler = (0, 0, 0)
    print('REVIEW turn', args.turn)
