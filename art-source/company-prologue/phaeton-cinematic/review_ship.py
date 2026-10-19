"""Review the exported Phaeton the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      --mode clean|destroyed|staged [--views a,b] [--frames a,b] [--samples 32] [--out DIR]
      [--effects] [--device GPU|CPU]

clean      appends `PHAETON` (with --effects its `SWFX`, drives running);
destroyed  appends `PHAETON WRECK` (with --effects its `SWFX`: fires, vents)
           and renders it well after the detonation, burning;
staged     appends both, each with its effects, at the same root, sets
           break_frame and swaps one for the other at it, as a shot does:
           renders the listed --frames under one camera (intact drives,
           flash, fireball, break-up, burning wreck).
Lit with a neutral studio (dark world, key, fill, rim, under; the ship's own
emissives are its lamps). Orthographic views at 1920x1080 / 100%, plus 's6',
a perspective camera at 1.2 km, the S6 range. Output defaults to
../phaeton-surface/build/final/ (ignored).
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
ap.add_argument('--mode', default='clean', choices=('clean', 'destroyed', 'staged'))
ap.add_argument('--views', default='three-quarter,starboard-quarter')
ap.add_argument('--frames', default='20,25,30,36,48,84,144,264')
ap.add_argument('--break-frame', type=int, default=24)
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--out', default=str(HERE.parent / 'phaeton-surface' / 'build' / 'final'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
ap.add_argument('--percent', type=int, default=100, help='internal checks only: owner previews are 100')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
T0 = (22, 0, 0)
MPU = .33
# (azimuth from +Y toward +X, elevation, ortho scale, target)
VIEWS = {
    'three-quarter': (43, 33, 300, T0),
    'starboard-quarter': (140, 30, 300, T0),
    'port-side': (0, 0, 260, T0),
    'top': (0, 90, 290, T0),
    'stern-quarter': (-50, 25, 300, T0),
    'wide': (43, 25, 520, T0),
    'break-detail': (60, 35, 120, (44, 0, 4)),
    's6': None,
}

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene


def append(blend, coll, effects):
    before = set(bpy.data.collections)
    with bpy.data.libraries.load(str(HERE / blend), link=False) as (src, dst):
        dst.collections = [coll, 'SWFX'] if effects else [coll]
    added = []
    for c in dst.collections:
        scene.collection.children.link(c)
        added.append(c)
    return added


groups = {}
if args.mode in ('clean', 'staged'):
    groups['intact'] = append('phaeton.blend', 'PHAETON', args.effects or args.mode == 'staged')
if args.mode in ('destroyed', 'staged'):
    groups['wreck'] = append('phaeton-destroyed.blend', 'PHAETON WRECK', args.effects or args.mode == 'staged')
missing = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
assert not missing, f'unpacked images: {missing}'
ctrl = bpy.data.objects.get('PHAETON WRECK | controls')
if ctrl is not None:
    ctrl['break_frame'] = float(args.break_frame)
    ctrl.update_tag()

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
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, args.percent
scene.cycles.volume_step_rate = 2.0     # coarser volume steps: a wreck's fires and smoke at ship range
scene.cycles.volume_max_steps = 256
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.view_settings.view_transform = 'AgX'
world = bpy.data.worlds.new('Review studio')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (.045, .047, .05, 1)
scene.world = world
rig = bpy.data.collections.new('Review rig')
scene.collection.children.link(rig)
S = 2.5
for name, loc, energy, size in (('Key', (60, 70, 120), 300000, 90), ('Fill', (-40, -60, 70), 90000, 80),
                                ('Rim', (-120, 30, 40), 140000, 60), ('Under', (20, 40, -90), 40000, 90)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy, ld.shape, ld.size = energy * S * S, 'DISK', size * S
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = Vector(loc) * S + Vector(T0)
    lo.rotation_euler = (-Vector(loc)).to_track_quat('-Z', 'Y').to_euler()

cd = bpy.data.cameras.new('Review camera')
cd.clip_end = 20000
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam


def aim(view):
    if view == 's6':
        # 1.2 km off the port bow quarter, slightly above, 85 mm: the S6 range, centre frame.
        cd.type, cd.lens = 'PERSP', 85
        t = Vector(T0)
        d = 1200 / MPU
        a, e = math.radians(50), math.radians(14)
        cam.location = t + d * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
        cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
        return
    az, el, scale, target = VIEWS[view]
    cd.type = 'ORTHO'
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cam.location = t + 900 * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    if el >= 89.9:
        cam.rotation_euler = (0, 0, math.pi)
        cam.location = Vector((t[0], t[1], 900))
    cd.ortho_scale = scale


def show(which):
    for key, colls in groups.items():
        for c in colls:
            scene.view_layers[0].layer_collection.children[c.name].exclude = key != which


OUT.mkdir(parents=True, exist_ok=True)
views = [v for v in args.views.split(',') if v]
if args.mode == 'staged':
    fps = scene.render.fps / scene.render.fps_base
    for v in views:
        aim(v)
        for f in (int(x) for x in args.frames.split(',') if x):
            show('intact' if f < args.break_frame else 'wreck')
            scene.frame_set(f)
            t = (f - args.break_frame) / fps
            scene.render.filepath = str(OUT / f'staged-{v}-f{f:04d}.png')
            bpy.ops.render.render(write_still=True)
            print('REVIEW staged', v, f, f't={t:+.2f}s')
else:
    if args.mode == 'destroyed':
        # The wreck already burning: blast suppressed (fir_exp 0), five seconds on.
        knobs = bpy.data.objects.get('SWFX controls')
        if knobs is not None:
            knobs['fir_exp'] = 0.0
            knobs.update_tag()
        scene.frame_set(args.break_frame + 5 * 24)
    else:
        scene.frame_set(24)
    suffix = ('-wreck' if args.mode == 'destroyed' else '') + ('-fx' if args.effects else '')
    for v in views:
        aim(v)
        scene.render.filepath = str(OUT / f'{v}{suffix}.png')
        bpy.ops.render.render(write_still=True)
        print('REVIEW still', v + suffix)
