"""Build the Tarsus freighter cinematic model with Blender's Python.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_tarsus.py -- [options]
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (53, 80), so slot and engine locations from
tarsus.ship are model XY unchanged. Heights are proposed art.

One module per area so later passes do not collide: ts_hull (twin arms,
central body, aft body and drums, drive block), ts_bridge (forward command
module), ts_engines (two main drives, four auxiliaries, drive skirt),
ts_mounts (four turrets), ts_fittings (tanks, pipe, grapple receivers, Kite
collars); ts_kit holds the shared helpers, copied rather than imported from
any other hull.

The Tarsus is the Asharu battle's prize (asharu-battle-shot.md): cut out of
the convoy with her drives shot out, boarded by Kites (P) and carried off
clamped under the Colossus Mk.III.

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, starboard-quarter, plus the close-ups;
                 'details' names the first seven close-ups)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --cpu          render on the CPU rather than the shared GPU
  --res WxH      render size (default 1920x1080)
  --figure       stand a 1.8 m mannequin by the starboard Kite collar (never saved)
  --no-save      skip the .blend and geometry.json
Then run compose_review.py (ordinary Python + Pillow + numpy) for the top
sprite overlay, the silhouette IoU and the review sheet.
"""
from pathlib import Path
import argparse
import importlib
import json
import math
import sys
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))  # company-prologue: ship_scale
import ship_scale
import ts_kit, ts_hull, ts_arms, ts_body, ts_bridge, ts_engines, ts_mounts, ts_fittings
MODULES = (ship_scale, ts_kit, ts_hull, ts_arms, ts_body, ts_bridge, ts_engines, ts_mounts, ts_fittings)
for module in MODULES:
    importlib.reload(module)

GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SHIP = 'data/hulls/tarsus.ship'
SPRITE = 'graphics/ships/tarsus/tarsus_base.png'
NAME = 'Tarsus'

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern,starboard-quarter')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--figure', action='store_true')
parser.add_argument('--cam', action='append', default=[])
parser.add_argument('--cpu', action='store_true', help='render on the CPU (the GPU is shared and often saturated)')
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out or HERE).resolve()
OUT.mkdir(parents=True, exist_ok=True)

# Real-world scale (pipeline pass 4): one number every metric value reads.
# Solved on the shared basis (../ship_scale.py, option C) against the enclosed
# volume of ship_scale.SOLIDS['tarsus'], re-measured every build.
SCALE = ship_scale.record(NAME) if NAME in ship_scale.HULLS else None
METRES_PER_UNIT = ship_scale.PUBLISHED.get(NAME) or (round(SCALE['solved_metres_per_unit'], 2) if SCALE else None)


def check_against_install():
    """Fail if the vanilla slots no longer match the constants the model uses."""
    path = GAME/SHIP
    if not path.exists():
        return 'install not found; constants used as committed'
    ship = json.loads(path.read_text(encoding='utf-8'))
    slots = {s['id']: s for s in ship['weaponSlots']}
    for mine in ts_mounts.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (tuple(mine['location']), mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from tarsus.ship: {theirs} vs {ours}")
    if len(slots) != len(ts_mounts.SLOTS):
        raise RuntimeError('tarsus.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['angle']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in ts_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from tarsus.ship: {engines}')
    if ship['center'] != [53, 80] or ship['hullId'] != 'tarsus':
        raise RuntimeError('tarsus.ship centre or hull id changed')
    return 'verified against the installed tarsus.ship'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = ts_kit.Kit()
ts_hull.build(kit)
ts_arms.build(kit)
ts_body.build(kit)
ts_bridge.build(kit)
ts_engines.build(kit)
ts_fittings.build(kit)
ts_mounts.build(kit)

scene = bpy.context.scene
scene.render.engine = 'CYCLES'
# --factory-startup leaves Cycles on the CPU; take the GPU when there is one.
_prefs = bpy.context.preferences.addons['cycles'].preferences
for _kind in () if args.cpu else ('OPTIX', 'CUDA', 'HIP', 'METAL', 'ONEAPI'):
    try:
        _prefs.compute_device_type = _kind
    except TypeError:
        continue
    _prefs.get_devices()
    if any(d.type == _kind for d in _prefs.devices):
        for d in _prefs.devices:
            d.use = d.type == _kind
        scene.cycles.device = 'GPU'
        print('render device', _kind)
        break
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = map(int, args.res.split('x'))
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']
bg.inputs[0].default_value = (.74, .76, .78, 1)
bg.inputs[1].default_value = .65
scene.view_settings.view_transform = 'AgX'
for name, loc, energy, size in [('Key', (150, 175, 250), 800000, 200), ('Fill', (-125, -150, 140), 460000, 170)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()

figure = None
if args.figure:
    # A 1.8 m mannequin on the deck beside the starboard Kite collar, in this hull's units.
    h = 1.8/(METRES_PER_UNIT or .52)
    p = kit.surface(0.0, -36.0)[0]
    figure = kit.cylinder('Review figure', (p.x, p.y, p.z+h/2), h*.13, h, kit.lamp, vertices=12)

# Every view observes the same objects; only the active camera changes.
TARGET = Vector((16, 1, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (16, 1, 400), 228),
    ('port-side', (16, 450, 0), 222),
    ('front', (450, 1, 0), 120),
    ('three-quarter', (290, 330, 220), 250),
    ('stern', (-450, 1, 0), 120),
    ('starboard-quarter', (270, -320, 210), 250),
]
# Close-ups, rendered only when named: (name, position, target, ortho scale).
CLOSEUPS = [
    ('bow-detail', (260, 150, 130), (90, 1, 0), 95),
    ('bridge-detail', (150, 60, 70), (42, 1, 6), 40),
    ('tanks-detail', (90, 160, 110), (2, 22, 10), 80),
    ('turret-detail', (130, 80, 60), (101, 21, 10), 24),
    ('engine-detail', (-260, 160, 90), (-70, 1, 0), 95),
    ('stern-detail', (-260, -120, 100), (-55, 1, 0), 110),
    ('underside', (16, 1, -420), (16, 1, -10), 228),
    ('drum-detail', (-10, 160, 90), (-41, 37, 8), 50),
    ('clamp-detail', (110, 70, 90), (70, 27, 9), 26),
    ('collar-detail', (40, -170, 30), (0, -42, -1), 34),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
wanted = []
for v in [v for v in args.views.split(',') if v]:
    wanted += [c[0] for c in CLOSEUPS[:7]] if v == 'details' else [v]
wanted += [spec.split(':')[0] for spec in args.cam]
for name, pos, target, scale in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 2000
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, scale in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 2000
    if name == 'top':
        cam.rotation_euler = (0, 0, math.pi)  # bow left, port below
    else:
        cam.rotation_euler = (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name in wanted:
    scene.camera = bpy.data.objects['Camera '+name]
    # The top view has a transparent film so its alpha is the silhouette.
    scene.render.film_transparent = name == 'top'
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)
if figure:
    bpy.data.objects.remove(figure, do_unlink=True)


def bounds(names=None):
    lo = Vector((1e9,)*3)
    hi = Vector((-1e9,)*3)
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in scene.objects:
        if ob.type == 'MESH' and (names is None or ob.name in names):
            for c in ob.evaluated_get(dg).bound_box:
                w = ob.matrix_world @ Vector(c)
                lo = Vector(map(min, lo, w))
                hi = Vector(map(max, hi, w))
    return lo, hi


def union_volume(names, step=1.0):
    """Volume of the union of named solids, by integrating vertical ray intervals per column."""
    dg = bpy.context.evaluated_depsgraph_get()
    trees = []
    for n in names:
        ev = bpy.data.objects[n].evaluated_get(dg)
        me = ev.to_mesh()
        trees.append(BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices], [tuple(p.vertices) for p in me.polygons]))
        ev.to_mesh_clear()
    lo, hi = bounds(names)
    total = 0.0
    x = lo.x+step/2
    while x < hi.x:
        y = lo.y+step/2
        while y < hi.y:
            spans = []
            for t in trees:
                zs, z = [], hi.z+1
                while True:
                    p, _, _, d = t.ray_cast(Vector((x, y, z)), Vector((0, 0, -1)))
                    if p is None:
                        break
                    zs.append(p.z)
                    z = p.z-1e-4
                spans += [(zs[i+1], zs[i]) for i in range(0, len(zs)-1, 2)]
            spans.sort()
            cur = None
            for a, b in spans:
                if cur and a <= cur[1]:
                    cur[1] = max(cur[1], b)
                else:
                    if cur:
                        total += cur[1]-cur[0]
                    cur = [a, b]
            if cur:
                total += cur[1]-cur[0]
            y += step
        x += step
    return total*step*step


# What counts as enclosed volume (mirrors ship_scale.SOLIDS['tarsus']).
SOLID_NAMES = ('Hull port arm', 'Hull starboard arm', 'Hull central body', 'Hull aft body',
               'Hull port aft drum', 'Hull starboard aft drum', 'Hull port bow drum', 'Hull starboard bow drum', 'Hull drive block', 'Bridge command module')


def scale_record(lo, hi):
    spec = ship_scale.SOLIDS.get('tarsus')
    include = spec['include'] if spec else (lambda n: n in SOLID_NAMES)
    solids = sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and include(o.name))
    measured = union_volume(solids, spec['step'] if spec else 1.0)
    arms = union_volume(['Hull port arm', 'Hull starboard arm'], 1.0)
    print('MEASURED enclosed units3', round(measured), 'arms units3', round(arms))
    if SCALE is None:
        return {'measured_this_build': {'enclosed_units3': round(measured), 'solids': solids}}
    table = ship_scale.HULLS[NAME]['units3']
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f'{NAME} units3 {table}: update the shared table')
    m = METRES_PER_UNIT
    return {
        **SCALE, 'published_metres_per_unit': METRES_PER_UNIT,
        'measured_this_build': {'enclosed_units3': round(measured), 'solids': solids,
                                'arms_units3': round(arms), 'arms_m3': round(arms*m**3)},
        'size_m': [round((b-a)*m, 1) for a, b in zip(lo, hi)],
        'cross_checks_m': {'person 1.8 m in units': round(1.8/m, 2),
                           'arm width': round((44.0-ts_hull.ARM_IN)*m, 1),
                           'arm depth': round((ts_hull.ARM_DECK-ts_hull.ARM_KEEL)*m, 1),
                           'body depth': round((ts_hull.BODY_DECK-ts_hull.BODY_KEEL)*m, 1),
                           'main drive mouth diameter': round(20*m, 1),
                           'Kite collar hatch diameter': round(5.2*m, 1)},
    }


if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'tarsus-geometry.blend'))
    lo, hi = bounds()
    mpu = METRES_PER_UNIT
    metres = (lambda p: [round(v*mpu, 2) for v in p]) if mpu else (lambda p: None)
    manifest = {
        'status': 'Pass 1 blockout on the clean base. No detail, damage, condition states or textures.',
        'hull': {
            'name': 'Tarsus', 'hullId': 'tarsus', 'size': 'DESTROYER', 'style': 'LOW_TECH', 'tech': 'Low Tech',
            'designation': 'Freighter',
            'source': {'ship': SHIP, 'sprite': SPRITE, 'ship_data': 'data/hulls/ship_data.csv (row id tarsus)',
                       'lights_layer': 'none in the install'},
            'ship_data': {'min_crew': 30, 'max_crew': 80, 'cargo': 350, 'fuel': 80, 'mass': 400,
                          'hitpoints': 4250, 'armor': 400},
            'built_in_mods': ['civgrade'], 'ship_system': 'burndrive', 'shield': 'OMNI (radius 120, arc 120)',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(lo, hi),
        'sprite': {'size': [106, 204], 'center': [53, 80], 'centreline_y': ts_hull.YC,
                   'pixel_to_model': 'col c, row r covers X 123-r..124-r, Y 52-c..53-c'},
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(b-a, 2) for a, b in zip(lo, hi)]},
        'weapon_slots': [dict({k: v for k, v in s.items() if k != 'prefix'},
                              location=[*s['location'], round(ts_mounts.ANCHOR_Z[s['id']], 2)],
                              objects=f"{s['prefix']} ...", pivot=f"{s['prefix']} pivot",
                              muzzle=f"{s['prefix']} muzzle", muzzle_location=list(ts_mounts.MUZZLES[s['id']]))
                         for s in ts_mounts.SLOTS],
        'engines': [{'index': i, 'kind': 'main' if w >= 20 else 'auxiliary',
                     'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
                     'location': [x, y, ts_engines._z(w)], 'width': w, 'angle': a, 'style': 'LOW_TECH',
                     'anchor_location': list(ts_engines.ANCHORS[i][0]),
                     'exhaust_direction': list(ts_engines.ANCHORS[i][1])}
                    for i, (x, y, w, a) in enumerate(ts_engines.ENGINES, 1)],
        'docking': {
            'note': 'Colossus Mk.III grapples clamp onto the four dorsal receivers (the Tarsus rides under '
                    'her keel, dorsal up, same heading); Kites (P) dock on the two flank collars. '
                    'Anchor +X points out of the hull.',
            'clamp_receivers': [{'anchor': f'Clamp receiver {i} anchor',
                                 'location': list(ts_fittings.ANCHORS[f'Clamp receiver {i}'][0]),
                                 'location_m': metres(ts_fittings.ANCHORS[f'Clamp receiver {i}'][0]),
                                 'outward': list(ts_fittings.ANCHORS[f'Clamp receiver {i}'][1])}
                                for i in range(1, len(ts_fittings.RECEIVERS)+1)],
            'kite_collars': [{'anchor': f'Kite collar {side} anchor',
                              'location': list(ts_fittings.ANCHORS[f'Kite collar {side}'][0]),
                              'location_m': metres(ts_fittings.ANCHORS[f'Kite collar {side}'][0]),
                              'outward': list(ts_fittings.ANCHORS[f'Kite collar {side}'][1])}
                             for side, _, _ in ts_fittings.COLLARS],
        },
        'object_names': {'Hull ...': 'arms, central body, aft body, aft drums, drive block, drive skirt',
                         'Bridge ...': 'forward command module between the arms',
                         'Tank ... / Pipe ...': 'port pressure tanks, starboard cylinder, blue transfer pipe',
                         'Engine <i> ...': 'drive can / aft-facing bell / mouth lip / dark throat / exhaust anchor',
                         'Turret <i> WS 00n ...': 'mounts; pivot and muzzle empties',
                         'Clamp receiver ... / Kite collar ...': 'docking hardpoints and their anchors',
                         'Scar <id> ...': 'reserved for the boarded condition state'},
        'condition_states': {'planned': ['boarded (drive section shot out and scorched, drives dark, '
                                         'Kites and Mk.III clamped on; not destroyed)'],
                             'contract': 'second-wind-pipeline.md layers: clean base + scar table with stable ids',
                             'built': []},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
    }
    path = OUT/'geometry.json'
    if path.exists():  # keep the silhouette record compose_review.py wrote
        old = json.loads(path.read_text(encoding='utf-8'))
        if 'silhouette' in old:
            manifest['silhouette'] = old['silhouette']
    path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('SCALE', json.dumps(manifest['scale']))
