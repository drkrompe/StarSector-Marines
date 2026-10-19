"""Review the kit's shields on any hull: front arcs, colour schemes, strength.

  blender --background --factory-startup --python review_shields.py --
      --hull DIR --blend PATH --collection NAME --set front|schemes|strength|flicker|paragon
      [--out DIR] [--samples 32] [--kit DIR] [--distance 1.02] [--device GPU|CPU]

Appends one hull's ship collection (from its built cinematic .blend, without
its own SWFX) into a factory scene, builds the kit's shield group on it from
the hull's profile (`--hull`, the folder holding vfx_hull.py with a SHIELD)
and renders 1920x1080 at 100% in Cycles, space-lit (one hard sun, black
world) from high on the port bow:

- front:    the shield closed (just opening, shield_t .06), half (.5) and
            fully open, then fully open turned 40 degrees to port with two hits;
- schemes:  fully open with one hit, once per colour scheme in SCHEMES_SHOWN;
- strength: fully open with one hit at each strength in STRENGTHS;
- flicker:  a weak shield (strength .3) on four frames a few apart, to show
            that the weakness moves;
- paragon:  the Paragon's own shield stills (its review camera), for an A/B
            of the kit: run once with --kit pointing at another checkout's
            second-wind-vfx and compare the PNGs.

Writes <set>-<k>.png into --out (default build/review/shields/<hull>); the
compose step is compose_shields.py. Renders are never committed.
"""
import argparse
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--hull', required=True)
ap.add_argument('--blend', required=True)
ap.add_argument('--collection', required=True)
ap.add_argument('--set', required=True, choices=('front', 'schemes', 'strength', 'flicker', 'paragon'))
ap.add_argument('--out', default=None)
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--kit', default=str(HERE))
ap.add_argument('--distance', type=float, default=1.02, help='camera distance in ship diagonals')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
HULL = Path(args.hull).resolve()
OUT = Path(args.out).resolve() if args.out else HERE / 'build' / 'review' / 'shields' / HULL.name
OUT.mkdir(parents=True, exist_ok=True)
sys.path.insert(0, str(Path(args.kit).resolve()))
import swfx  # noqa: E402

SCHEMES_SHOWN = ('low-tech', 'midline', 'high-tech', 'hegemony', 'tritachyon', 'independent',
                 'sindrian_diktat', 'luddic_church', 'pirates')
STRENGTHS = (.25, .43, .6, 1.0, 1.25, 1.5)
# Stills: (name, shield_t, facing, [(hit, azimuth, elevation, strength)], extra knobs, frame).
FRONT = [
    ('closed', .06, 0.0, [], {}, 1),
    ('half', .5, 0.0, [], {}, 1),
    ('full', 1.0, 0.0, [(1, 12, 8, 1.0), (2, -38, 18, .7)], {}, 1),
    ('turned', 1.0, 40.0, [(1, 55, 10, 1.0), (2, 95, -5, .7)], {}, 1),
]
PARAGON = [
    ('shield-half', .5, 0.0, [], {}, 1),
    ('shield-raised', 1.0, 0.0, [(1, 35, 10, 1.0), (2, 160, 40, .7)], {}, 1),
    ('shield-fortress', 1.0, 0.0, [(1, 35, 10, 1.0), (2, 70, -15, .8), (3, -20, 25, .6)],
     {'shield_fortress': 1.0}, 1),
]

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(Path(args.blend).resolve()), link=False) as (src, dst):
    dst.collections = [args.collection]
ship = dst.collections[0]
scene.collection.children.link(ship)
bpy.context.view_layer.update()

scene.render.engine = 'CYCLES'
prefs = bpy.context.preferences.addons['cycles'].preferences
for kind in (('OPTIX', 'CUDA', 'METAL') if args.device == 'GPU' else ()):
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
scene.cycles.seed = 0
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.view_settings.view_transform = 'AgX'
world = bpy.data.worlds.new('Review space')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (0, 0, 0, 1)
scene.world = world
rig = bpy.data.collections.new('Review rig')
scene.collection.children.link(rig)
sd = bpy.data.lights.new('Space sun', 'SUN')
sd.energy, sd.angle = 4.0, math.radians(.53)
sun = bpy.data.objects.new('Space sun', sd)
rig.objects.link(sun)
sun.rotation_euler = Vector((.35, -.75, .55)).to_track_quat('Z', 'Y').to_euler()
cd = bpy.data.cameras.new('Review camera')
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam

# The ship's world bounds, for a camera that fits any hull.
pts = [o.matrix_world @ Vector(c) for o in ship.all_objects if o.type == 'MESH' for c in o.bound_box]
lo = Vector([min(p[i] for p in pts) for i in range(3)])
hi = Vector([max(p[i] for p in pts) for i in range(3)])
MID, SIZE = (lo + hi) / 2, (hi - lo).length


def aim(location, target, lens):
    cd.type, cd.lens, cd.sensor_width = 'PERSP', lens, 36.0
    cd.clip_start, cd.clip_end = 1.0, 50000.0
    cam.location = location
    cam.rotation_euler = (target - location).to_track_quat('-Z', 'Y').to_euler()


if args.set == 'paragon':
    aim(Vector((390, 470, 260)), Vector((10, 0, 25)), 30.0)
else:
    # High on the port bow (azimuth from the bow toward port), looking a little
    # forward of the middle, where a front arc stands.
    az, el = math.radians(36), math.radians(46)
    d = Vector((math.cos(az) * math.cos(el), math.sin(az) * math.cos(el), math.sin(el)))
    target = MID + Vector((.12 * (hi.x - lo.x), 0, 0))
    aim(target + d * SIZE * args.distance, target, 30.0)


def build(**options):
    ctx = swfx.apply(state='clean', groups=('shield',), hull=str(HULL), **options)
    return ctx, bpy.data.objects['SWFX controls']


def still(ctrl, name, t, facing, hits, extra, frame):
    scene.frame_set(frame)
    for k in range(1, 5):
        ctrl[f'shield_hit_{k}'] = 0.0
    ctrl['shield_t'], ctrl['shield_facing'] = float(t), float(facing)
    for k, a, e, amt in hits:
        ctrl[f'shield_hit_az_{k}'], ctrl[f'shield_hit_el_{k}'], ctrl[f'shield_hit_{k}'] = float(a), float(e), amt
    for k, v in extra.items():
        ctrl[k] = float(v)
    ctrl.update_tag()
    bpy.context.view_layer.update()
    scene.render.filepath = str(OUT / f'{name}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW', scene.render.filepath, flush=True)


if args.set in ('front', 'paragon'):
    ctx, ctrl = build()
    print('SHIELD', {k: v for k, v in ctx.records['shield'].items() if k not in ('hits', 'nodes', 'lights')})
    for i, s in enumerate(FRONT if args.set == 'front' else PARAGON, 1):
        name = f'front-{i}-{s[0]}' if args.set == 'front' else s[0]
        still(ctrl, name, *s[1:])
elif args.set == 'schemes':
    for i, scheme in enumerate(SCHEMES_SHOWN, 1):
        ctx, ctrl = build(shield_scheme=scheme)
        still(ctrl, f'scheme-{i}-{scheme}', 1.0, 0.0, [(1, 15, 10, .8)], {}, 1)
elif args.set == 'strength':
    ctx, ctrl = build()
    for i, sig in enumerate(STRENGTHS, 1):
        still(ctrl, f'strength-{i}-{sig:.2f}', 1.0, 0.0, [(1, 15, 10, .8)], {'shield_strength': sig}, 1)
else:
    ctx, ctrl = build()
    for i, frame in enumerate((1, 4, 7, 10), 1):
        still(ctrl, f'flicker-{i}-f{frame}', 1.0, 0.0, [], {'shield_strength': .3}, frame)
