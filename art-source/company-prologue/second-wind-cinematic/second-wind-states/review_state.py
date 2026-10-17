"""Review an exported state the way a shot uses it: appended into a clean scene.

  blender --background --factory-startup --python review_state.py -- --state patched
      [--views a,b] [--turn N] [--samples 32] [--out DIR] [--wear-off] [--asset PATH]
      [--cam NAME:AZ,EL,SCALE[,TX,TY,TZ]] [--res WxH] [--device GPU|CPU]

Appends `SECOND WIND | <state>` from <state>/second-wind-<state>.blend into a
factory scene (so a missing image, a broken parent or an external reference
shows here first), lights it with a neutral studio (dark world, key, fill,
rim; no ship lamps added: the ship's own emissives are its only lights), and
renders orthographic 1920x1080 views:
  <out>/<view>.png              stills (default: the review set below)
  <out>/turn/turn-NNN.png       --turn N: N frames of a 360-degree yaw of the
                                ROOT under a fixed camera and fixed lamps
--wear-off sets the state overlay's `SW overlay master` to 0 (base wear only) for
before/after reads of what the state adds.
--lights-off turns the studio and world off, so only the ship's own emissives
render (stills get a `-dark` suffix): the check that a dead ship is dark.
--sites writes <view>.sites.json: every recorded scar site (manifest
`scar_sites`) projected to pixels, with whether the camera sees it (a ray from
the camera reaches it unobstructed). --no-render skips the images.
Views are (azimuth from +Y toward +X, elevation, ortho scale, target), the
surfacing review's set, so a state compares against the base-skin review.
"""
from pathlib import Path
import argparse
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--state', required=True)
ap.add_argument('--asset')
ap.add_argument('--views', default='port-quarter,starboard-quarter,port-side,starboard-side,top,stern,bow-quarter,under-quarter')
ap.add_argument('--turn', type=int, default=0)
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--res', default='1920x1080')
ap.add_argument('--out')
ap.add_argument('--wear-off', action='store_true')
ap.add_argument('--lights-off', action='store_true')
ap.add_argument('--sites', action='store_true')
ap.add_argument('--no-render', action='store_true')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
ap.add_argument('--cam', action='append', default=[])
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
ASSET = Path(args.asset or HERE / args.state / f'second-wind-{args.state}.blend').resolve()
OUT = Path(args.out or HERE.parent.parent / 'second-wind-surface' / 'build' / f'review-{args.state}').resolve()
COLL = f'SECOND WIND | {args.state}'
ROOT = f'SECOND WIND | {args.state} | root'
T0 = (5, 0, 1)
VIEWS = {
    'port-quarter': (37.6, 31.5, 163, T0),
    'starboard-quarter': (142.4, 31.5, 163, T0),
    'port-side': (0, 0, 132, T0),
    'starboard-side': (180, 0, 132, T0),
    'top': (0, 90, 205, T0),
    'stern': (-90, 0, 130, T0),
    'bow-quarter': (70, 20, 150, T0),
    'under-quarter': (35, -35, 165, T0),
    'high-quarter': (45, 50, 170, T0),
    'port-flank-detail': (38.3, 23.6, 52, (34, 24, 6)),
    'port-aft-detail': (19.3, 30.8, 52, (-22, 28, 6)),
    'starboard-aft-detail': (127.3, 31.0, 55, (-24, -26, 6)),
    'stern-detail': (-75.2, 11.9, 62, (-36, 6, 4)),
    'bridge-detail': (37.7, 25.3, 55, (12, 0, 14)),
    'bow-detail': (54.5, 22.7, 70, (40, 0, 8)),
}
for spec in args.cam:
    name, rest = spec.split(':')
    v = [float(c) for c in rest.split(',')]
    VIEWS[name] = (v[0], v[1], v[2], tuple(v[3:6]) if len(v) >= 6 else T0)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(ASSET), link=False) as (src, dst):
    assert COLL in src.collections, f'{COLL} not in {ASSET.name}: {src.collections}'
    dst.collections = [COLL]
coll = dst.collections[0]
scene.collection.children.link(coll)
root = bpy.data.objects[ROOT]
missing = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
assert not missing, f'unpacked images: {missing}'
orphans = [o.name for o in coll.all_objects if o is not root and not o.parent]
assert not orphans, f'objects not under the root: {orphans[:5]}'
if args.wear_off:
    for g in bpy.data.node_groups:
        if g.name.startswith('SW overlay') and 'SW overlay master' in g.nodes:
            g.nodes['SW overlay master'].outputs[0].default_value = 0.0

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
w, h = map(int, args.res.split('x'))
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = w, h, 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.view_settings.view_transform = 'AgX'
world = bpy.data.worlds.new('Review studio')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (.045, .047, .05, 1)
world.node_tree.nodes['Background'].inputs[1].default_value = 1.0
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
cd = bpy.data.cameras.new('Review camera')
cd.type, cd.clip_end = 'ORTHO', 2000
cam = bpy.data.objects.new('Review camera', cd)
rig.objects.link(cam)
scene.camera = cam


def aim(az, el, scale, target, dist=300):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    up = 'Y' if abs(el) < 89 else 'Y'
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', up).to_euler()
    if el >= 89.9:          # top: bow to the left, as the geometry review
        cam.rotation_euler = (0, 0, math.pi)
        cam.location = Vector((t[0], t[1], dist))
    cd.ortho_scale = scale


import json
from bpy_extras.object_utils import world_to_camera_view
MAN = json.loads((ASSET.parent / 'manifest.json').read_text()) if args.sites else {}
SITES, FOOT = MAN.get('scar_sites', {}), MAN.get('scar_footprints', {})


def project_sites(v):
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    fwd = cam.matrix_world.to_3x3() @ Vector((0, 0, -1))
    out = {}
    for sid, pts in SITES.items():
        for p in pts:
            q = Vector(p['p']) + Vector(p['n']) * .05
            c = world_to_camera_view(scene, cam, q)
            hit, loc, *_ = scene.ray_cast(dg, q - fwd * 400, fwd)
            seen = bool(hit) and (loc - q).length < .6
            out.setdefault(sid, []).append([round(c.x * w, 1), round((1 - c.y) * h, 1), seen])
    foot = {}
    for sid, f in FOOT.items():
        c = world_to_camera_view(scene, cam, Vector(f['centre']))
        foot[sid] = [round(c.x * w, 1), round((1 - c.y) * h, 1)]
    (OUT / f'{v}.sites.json').write_text(json.dumps({'sites': out, 'footprints': foot}))


OUT.mkdir(parents=True, exist_ok=True)
for v in [v for v in args.views.split(',') if v]:
    aim(*VIEWS[v])
    if args.sites:
        project_sites(v)
    if args.no_render:
        continue
    scene.render.filepath = str(OUT / f"{v}{'-dark' if args.lights_off else ''}{'-nowear' if args.wear_off else ''}.png")
    bpy.ops.render.render(write_still=True)
    print('REVIEW still', v)
if args.turn:
    (OUT / 'turn').mkdir(exist_ok=True)
    aim(37.6, 24, 175, T0)
    for k in range(args.turn):
        root.rotation_euler = (0, 0, 2 * math.pi * k / args.turn)
        scene.render.filepath = str(OUT / 'turn' / f'turn-{k:03d}.png')
        bpy.ops.render.render(write_still=True)
    root.rotation_euler = (0, 0, 0)
    print('REVIEW turn', args.turn)
