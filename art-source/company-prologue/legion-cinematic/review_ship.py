"""Review the exported Legion the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      [--views a,b] [--turn N] [--samples 32] [--out DIR] [--pose rest|ops]
      [--effects] [--fire FRAME] [--lights-off] [--device GPU|CPU]

Appends `LEGION` into a factory scene, lights it with a neutral studio (dark
world, key, fill, rim, under: the Shepherd's rig scaled to a 292 m hull; the
ship's own emissives are its only lamps) and renders orthographic 1920x1080
views at 100%:
  <out>/<view>[-ops][-fx|-fire][-dark].png   stills
  <out>/turn/turn-NNN.png                    --turn N: N frames of a 360-degree
                                             yaw of the ROOT under a fixed camera
--pose ops sets every control knob (blast deflectors and recovery barriers
raised, transfer doors open, port traversers at the launch points, starboard
at the locks, cranes run out) and trains and elevates every turret, all
through the asset's own controls. --effects appends the optional `SWFX`
collection in the same load. --fire FRAME rebuilds the kit on the appended
ship the way a shot does (swfx.apply with vfx_hull.py) and fires it: every gun
turret on a four-round burst whose third round flashes on FRAME, both
launchers rippling one round per tube four frames apart (the last two frames
before FRAME, its bloom still burning); the stills are rendered at FRAME,
without the exhaust haze (it multiplies every other volume's render cost).
Defaults go to ../legion-surface/build/final/ (ignored).
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
ap.add_argument('--views', default='three-quarter,starboard-quarter,stern-quarter,under-quarter')
ap.add_argument('--turn', type=int, default=0)
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--out', default=str(HERE.parent / 'legion-surface' / 'build' / 'final'))
ap.add_argument('--pose', default='rest', choices=('rest', 'ops'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--fire', type=int, default=0)
ap.add_argument('--lights-off', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
man = json.loads((HERE / 'manifest.json').read_text())
T0 = (10, 0, -12)
# (azimuth from +Y toward +X, elevation, ortho scale, target), the surfacing review's framing.
VIEWS = {
    'three-quarter': (43, 33, 420, T0),
    'starboard-quarter': (140, 30, 420, T0),
    'stern-quarter': (-50, 25, 420, T0),
    'under-quarter': (41, -35, 420, T0),
    'bow-quarter': (75, 20, 400, T0),
    'top': (0, 90, 400, T0),
    'turret-detail': (62, 30, 150, (82, 4, 12)),
    'launcher-detail': (25, 28, 75, (44, 56, 18)),
    'flight-deck-detail': (-80, 4, 80, (-40, 105, -19)),
    'engine-detail': (-75, 12, 190, (-122, 0, -4)),
}
STUDIO = 3.4          # the Shepherd's studio (an 86-unit hull) scaled to this 295-unit one

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX'] if args.effects else [man['append_collection']]
for c in dst.collections:
    scene.collection.children.link(c)
root = bpy.data.objects[man['root']]
control = bpy.data.objects[man['control']['object']]
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
    ld.energy, ld.shape, ld.size = energy * STUDIO ** 2, 'DISK', size * STUDIO
    lo = bpy.data.objects.new(name, ld)
    rig.objects.link(lo)
    lo.location = Vector(loc) * STUDIO
    lo.rotation_euler = (-Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
if args.lights_off:
    world.node_tree.nodes['Background'].inputs[1].default_value = 0.0
    for lo in rig.objects:
        lo.hide_render = True

if args.pose == 'ops':
    for k in man['control']['knobs']:
        lo_, hi_ = k['range']
        control[k['name']] = lo_ if k['name'].startswith('traverser_starboard') else hi_
    for t in man['turrets']:
        bpy.data.objects[t['pivot']].rotation_euler.z += math.radians(25 if t['pivot_at'][1] >= 0 else -25)
        bpy.data.objects[t['elevation']].rotation_euler.y -= math.radians(20)
control.update_tag()          # a knob set from Python does not tag the depsgraph
scene.frame_set(1)

if args.fire:
    sys.path.insert(0, str(HERE.parent / 'second-wind-vfx'))
    import swfx  # noqa: E402
    # No exhaust haze in the firing pass (the effects pass shows it): ten haze boxes
    # wrapping the stern multiply the cost of every muzzle puff and smoke trail.
    ctx = swfx.apply(state='clean', hull=HERE, dust=False, haze=False)
    ctx.set('smk_atmosphere', 0.0)        # as export_effects.py: a patrol in space
    guns = swfx.module('guns')      # the module apply just built with
    F = args.fire
    for t in man['turrets']:
        if t['kind'] == 'launcher':
            n = len(t['muzzles'])
            guns.fire(ctx, t['index'], F - 2 - 4 * (n - 1) - 4, n + 1, 4)
        else:
            guns.fire(ctx, t['index'], F - 8 - t['index'] % 2, 4, 4)
    scene.frame_set(F)
    scene.cycles.volume_step_rate = 2.0     # coarser volume steps: a battery's smoke at close range

cd = bpy.data.cameras.new('Review camera')
cd.type, cd.clip_end = 'ORTHO', 6000
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam


def aim(az, el, scale, target, dist=1500):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    if el >= 89.9:          # top: bow to the left, port below
        cam.rotation_euler = (0, 0, math.pi)
        cam.location = Vector((t[0], t[1], dist))
    cd.ortho_scale = scale


suffix = ('-ops' if args.pose == 'ops' else '') + ('-fx' if args.effects else '') + \
    ('-fire' if args.fire else '') + ('-dark' if args.lights_off else '')
OUT.mkdir(parents=True, exist_ok=True)
for v in [v for v in args.views.split(',') if v]:
    aim(*VIEWS[v])
    scene.render.filepath = str(OUT / f'{v}{suffix}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', v + suffix)
if args.turn:
    (OUT / 'turn').mkdir(exist_ok=True)
    aim(41, 22, 430, T0)
    for k in range(args.turn):
        root.rotation_euler = (0, 0, 2 * math.pi * k / args.turn)
        scene.render.filepath = str(OUT / 'turn' / f'turn-{k:03d}.png')
        bpy.ops.render.render(write_still=True)
    root.rotation_euler = (0, 0, 0)
    print('REVIEW turn', args.turn)
