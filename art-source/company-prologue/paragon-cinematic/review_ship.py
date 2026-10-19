"""Review the Paragon cinematic asset the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_ship.py --
      [--views a,b] [--effects] [--lights-off] [--beams] [--lance-sequence] [--shield]
      [--samples 32] [--out DIR] [--device GPU|CPU]

Appends `PARAGON | clean` (and with --effects the optional `SWFX` collection,
in the same load) into a factory scene and renders 1920x1080 at 100% in Cycles:

- studio views (orthographic, the surfacing review's neutral studio: dark
  world, key, fill, rim and under, scaled to the 350 m hull):
  `three-quarter` (starboard bow quarter), `stern-quarter` (port quarter,
  from aft), `engine-detail` (the ten drives from the port quarter);
  --lights-off drops the studio, leaving the ship's own glow and the drives;
- `formation-1.5km`: the space look the other capitals use: a 24 mm lens on a
  36 mm sensor 1.5 km off the hull's middle (azimuth -40 degrees from the bow
  toward port, elevation 18), one hard sun (0.53 degrees), no fill, black world;
- --beams: one point TARGET ahead of the bow engaged as the game allows
  (aim.py): every turret whose arc holds it trains on it, and a beam fires
  only if it lies on the target within its elevation limits along a line
  clear of the hull (envelope 1, length the distance from its muzzle); the
  rest stay dark. The bow lances fire along their own axes. Views `beam-broadside` (the whole ship and
  the converging beams from high on the port beam, one hard sun) and
  `beam-bow` (the bow lances and the forward turrets close, perspective);
  then a second target, TARGET_PORT, high on the port bow, seen past the
  ship from the starboard quarter (`beam-port`);
- --lance-sequence: the bow lances only, six stills through one shot (charge
  building, full charge, beam on, beam held, charge-down, dark again) from the
  `beam-bow` camera, as lance-<k>.png.
- --shield: the Fortress Shield (swfx_shield, fitted to the hull) from high on the port bow,
  space-lit, three stills: `shield-half` (shield_t 0.5: the bubble unfolding
  from the bow, its edge still opening, the emitter nodes already lit),
  `shield-raised` (fully raised, two hits flashing: port bow and dorsal aft)
  and `shield-fortress` (the Fortress Shield on: nearly clear, three hits
  carrying its colour).

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
sys.path.insert(0, str(HERE))
import aim  # noqa: E402
ap = argparse.ArgumentParser()
ap.add_argument('--views', default='three-quarter,stern-quarter')
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--out', default=str(HERE / 'build' / 'review'))
ap.add_argument('--effects', action='store_true')
ap.add_argument('--lights-off', action='store_true')
ap.add_argument('--beams', action='store_true')
ap.add_argument('--lance-sequence', action='store_true')
ap.add_argument('--shield', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
man = json.loads((HERE / 'manifest.json').read_text())
MPU = man['metres_per_unit']
(lx, ly, lz), (hx, hy, hz) = man['bounds_units']
MID = Vector(((lx + hx) / 2, 0, (lz + hz) / 2))
T0 = Vector((5, 0, 20))
# Studio views: (azimuth from +Y toward +X, elevation, ortho scale, target).
VIEWS = {
    'three-quarter': (128, 28, 500, T0),
    'stern-quarter': (-48, 24, 500, T0 + Vector((-20, 0, 0))),
    'engine-detail': (-58, 16, 300, Vector((-178, 20, 0))),
}
# Formation: (azimuth from +X toward +Y, elevation, km).
FORMATION = {'formation-1.5km': (-40, 18, 1.5)}
# Two engagements: dead ahead at deck height (the bow lances' line), and high
# on the port bow, where the most weapons bear (8 of 17).
TARGET = Vector((1000, 0, 25))
TARGET_PORT = Vector((850, 500, 200))
# Beam views (perspective): (camera location, look-at, lens mm).
BEAM_VIEWS = {
    'beam-broadside': (Vector((420, 1250, 520)), Vector((400, 0, 10)), 30.0),
    'beam-port': (Vector((-760, -700, 520)), Vector((260, 200, 70)), 26.0),
    'beam-bow': (Vector((330, 170, 95)), Vector((150, 0, 20)), 28.0),
    'shield': (Vector((390, 470, 260)), Vector((10, 0, 25)), 30.0),
}
# Shield stills: (shield_t, shield_fortress, [(hit, azimuth, elevation, strength), ...]).
SHIELD_STILLS = {
    'shield-half': (.5, 0.0, []),
    'shield-raised': (1.0, 0.0, [(1, 35, 10, 1.0), (2, 160, 40, .7)]),
    'shield-fortress': (1.0, 1.0, [(1, 35, 10, 1.0), (2, 70, -15, .8), (3, -20, 25, .6)]),
}

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
# The Shepherd's studio scaled to this hull: positions x4.5, so energy x20.
K = 4.5
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
ctrl = bpy.data.objects.get('SWFX controls')


def studio(on):
    space = not on
    for lo in STUDIO:
        lo.hide_render = space or args.lights_off
    sun.hide_render = not space
    bg.inputs[0].default_value = (0, 0, 0, 1) if space else (.045, .047, .05, 1)
    bg.inputs[1].default_value = 0.0 if (space or args.lights_off) else 1.0


def aim_ortho(az, el, scale, target, dist=1400):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cd.type, cd.ortho_scale, cd.clip_start, cd.clip_end = 'ORTHO', scale, 1, 5000
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()


def aim_persp(location, target, lens):
    cd.type, cd.lens, cd.sensor_width, cd.sensor_fit = 'PERSP', lens, 36.0, 'HORIZONTAL'
    cd.clip_start, cd.clip_end = 1, 20000
    cam.location = location
    cam.rotation_euler = (target - location).to_track_quat('-Z', 'Y').to_euler()


def aim_formation(az, el, km):
    d = km * 1000 / MPU
    a, e = math.radians(az), math.radians(el)
    aim_persp(Vector((MID.x + d * math.cos(e) * math.cos(a), d * math.cos(e) * math.sin(a), MID.z + d * math.sin(e))),
              MID, 24.0)


upd = aim.upd


def knob(name, value):
    ctrl[name] = float(value)
    ctrl.update_tag()


def fire_all(target, which=None):
    """Every beam that can bear (aim.engage: arc, elevation, clear line) on at
    full length toward target; `which` limits it to those slots."""
    shots = aim.engage(man, target, slots=which)
    for b in man['beams']:
        on = b['id'] in shots['firing']
        knob(f"beam_len_{b['id']}", shots['length'].get(b['id'], b['range']))
        knob(f"beam_t_{b['id']}", 1.0 if on else 0.0)
        knob(f"beam_charge_{b['id']}", 1.0 if on else 0.0)
    upd()
    print('REVIEW firing', len(shots['firing']), 'of', len(man['beams']), shots['firing'], flush=True)
    for k, why in shots['held'].items():
        print('REVIEW held', k, why, flush=True)
    return shots['firing']


def render(name):
    scene.render.filepath = str(OUT / f'{name}.png')
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', name, flush=True)


suffix = ('-fx' if args.effects else '') + ('-dark' if args.lights_off else '')
OUT.mkdir(parents=True, exist_ok=True)
for v in [v for v in args.views.split(',') if v]:
    if v in FORMATION:
        studio(False)
        aim_formation(*FORMATION[v])
    else:
        studio(True)
        aim_ortho(*VIEWS[v])
    render(f'{v}{suffix}')
if args.beams:
    assert ctrl is not None, '--beams needs --effects'
    fire_all(TARGET)
    knob('beam_light', 1.0)
    for v in ('beam-broadside', 'beam-bow'):
        studio(False)
        aim_persp(*BEAM_VIEWS[v])
        render(f'{v}{suffix}')
    fire_all(TARGET_PORT)
    studio(False)
    aim_persp(*BEAM_VIEWS['beam-port'])
    render(f'beam-port{suffix}')
if args.lance_sequence:
    assert ctrl is not None, '--lance-sequence needs --effects'
    lances = fire_all(TARGET, which=('WS 001', 'WS 002'))
    studio(False)
    aim_persp(*BEAM_VIEWS['beam-bow'])
    # (charge envelope, beam envelope): fire_beam's shape for the lance.
    STEPS = [(.35, 0), (1, 0), (1, 1), (.8, 1), (.6, .45), (0, 0)]
    for k, (c, t) in enumerate(STEPS, 1):
        for b in man['beams']:
            if b['id'] in lances:
                knob(f"beam_charge_{b['id']}", c)
                knob(f"beam_t_{b['id']}", t)
        upd()
        render(f'lance-{k}')
if args.shield:
    assert ctrl is not None, '--shield needs --effects'
    for b in man['beams']:
        knob(f"beam_t_{b['id']}", 0.0)
        knob(f"beam_charge_{b['id']}", 0.0)
    studio(False)
    aim_persp(*BEAM_VIEWS['shield'])
    for name, (t, fort, hits) in SHIELD_STILLS.items():
        knob('shield_t', t)
        knob('shield_fortress', fort)
        for k in range(1, 5):
            knob(f'shield_hit_{k}', 0.0)
        for k, az, el, amt in hits:
            knob(f'shield_hit_az_{k}', az)
            knob(f'shield_hit_el_{k}', el)
            knob(f'shield_hit_{k}', amt)
        upd()
        render(name)
