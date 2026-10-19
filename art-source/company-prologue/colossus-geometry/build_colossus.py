"""Build the Colossus heavy freighter cinematic model with Blender's Python.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_colossus.py -- [options]
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (93, 128), so slot and engine locations from
colossus.ship are model XY unchanged. Heights are proposed art.

One module per area so later passes do not collide: cl_hull (central cargo
hold, hatch cover, longerons, four side cargo modules), cl_aft (keel spine,
aft machinery body, lattice frames), cl_bridge (deckhouse, glazing, mast), cl_engines (two drive
pods, six drives), cl_mounts (four turrets, four flare launchers); cl_kit holds
the shared helpers, copied rather than imported from any other hull.

The hull is split into named modules along the sprite's own joints so the
crashed state (pass 5, not built) can tear it apart without renaming
anything: the campaign's broken Colossus lies against Asharu's northern
canyon wall (shepherd-recovery.md, mapgen-nouns.md).

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, starboard-quarter, plus the close-ups
                 bow-detail, cargo-detail, bridge-detail, turret-detail,
                 engine-detail, stern-detail, underside; 'details' names all)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --figure       stand a 1.8 m mannequin by the bridge (never saved)
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
import cl_kit, cl_hull, cl_aft, cl_bridge, cl_engines, cl_mounts
MODULES = (ship_scale, cl_kit, cl_hull, cl_aft, cl_bridge, cl_engines, cl_mounts)
for module in MODULES:
    importlib.reload(module)

GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SHIP = 'data/hulls/colossus.ship'
SPRITE = 'graphics/ships/colossus/colossus.png'
NAME = 'Colossus'

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern,starboard-quarter')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--figure', action='store_true')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out or HERE).resolve()
OUT.mkdir(parents=True, exist_ok=True)

# Real-world scale (pipeline pass 4): one number every metric value reads.
# Solved on the shared basis (../ship_scale.py, option C) against the
# enclosed volume of ship_scale.SOLIDS['colossus'], re-measured every build.
# Published 0.50 by owner override (2026-10-18); the deepened block solves 0.426.
SCALE = ship_scale.record(NAME) if NAME in ship_scale.HULLS else None
METRES_PER_UNIT = ship_scale.PUBLISHED.get(NAME) or (round(SCALE['solved_metres_per_unit'], 2) if SCALE else None)


def check_against_install():
    """Fail if the vanilla slots no longer match the constants the model uses."""
    path = GAME/SHIP
    if not path.exists():
        return 'install not found; constants used as committed'
    ship = json.loads(path.read_text(encoding='utf-8'))
    slots = {s['id']: s for s in ship['weaponSlots']}
    for mine in cl_mounts.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (tuple(mine['location']), mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from colossus.ship: {theirs} vs {ours}")
    if len(slots) != len(cl_mounts.SLOTS):
        raise RuntimeError('colossus.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['angle']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in cl_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from colossus.ship: {engines}')
    if ship['center'] != [93, 128] or ship['hullId'] != 'colossus':
        raise RuntimeError('colossus.ship centre or hull id changed')
    return 'verified against the installed colossus.ship'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = cl_kit.Kit()
cl_hull.build(kit)
cl_aft.build(kit)
cl_bridge.build(kit)
cl_engines.build(kit)
cl_mounts.build(kit)

scene = bpy.context.scene
scene.render.engine = 'CYCLES'
# --factory-startup leaves Cycles on the CPU; take the GPU when there is one.
_prefs = bpy.context.preferences.addons['cycles'].preferences
for _kind in ('OPTIX', 'CUDA', 'HIP', 'METAL', 'ONEAPI'):
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
for name, loc, energy, size in [('Key', (150, 175, 250), 900000, 200), ('Fill', (-125, -150, 140), 520000, 170)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()

figure = None
if args.figure:
    # A 1.8 m mannequin on the bridge's aft door landing, in this hull's units.
    h = 1.8/(METRES_PER_UNIT or .5)
    p = kit.surface(-74.5, 4.5)[0]
    figure = kit.cylinder('Review figure', (p.x, p.y, p.z+h/2), h*.13, h, kit.lamp, vertices=12)

# Every view observes the same objects; only the active camera changes.
TARGET = Vector((-17, 1.5, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (-17, 1.5, 400), 340),
    ('port-side', (-17, 450, 2), 240),
    ('front', (450, 1.5, 2), 205),
    ('three-quarter', (290, 330, 220), 300),
    ('stern', (-450, 1.5, 2), 205),
    ('starboard-quarter', (270, -320, 210), 300),
]
# Close-ups, rendered only when named: (name, position, target, ortho scale).
CLOSEUPS = [
    ('bow-detail', (300, 190, 170), (55, 15, 5), 190),
    ('cargo-detail', (120, 260, 150), (30, 60, 8), 110),
    ('bridge-detail', (40, 110, 110), (-56, 1.5, 18), 70),
    ('turret-detail', (110, 140, 90), (69, 75, 16), 34),
    ('engine-detail', (-320, 230, 120), (-100, 45, 0), 150),
    ('stern-detail', (-260, -120, 120), (-80, 1.5, 0), 150),
    ('underside', (40, 1.5, -420), (-17, 1.5, -10), 420),
    ('keel-docking', (120, 160, -160), (30, 30, -40), 110),
    ('clamp-detail', (30, 120, -90), (70, 67, -48), 22),
    ('aft-bulkhead', (-120, -60, -70), (-19, 1.5, -28), 70),
    ('pod-quarter', (-200, 140, 60), (-95, 55, 0), 120),
    ('bridge-quarter', (-20, 90, 80), (-75, 1.5, 20), 70),
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


def scale_record(lo, hi):
    spec = ship_scale.SOLIDS['colossus']
    solids = sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and spec['include'](o.name))
    measured = union_volume(solids, spec['step'])
    hold = union_volume(['Hull central cargo hold']+[f'Cargo {s} {p} module' for s, _ in cl_hull.SIDES
                                                    for p in ('fore', 'aft')], spec['step'])
    print('MEASURED enclosed units3', round(measured), 'cargo body units3', round(hold))
    if SCALE is None:
        return {'measured_this_build': {'enclosed_units3': round(measured), 'solids': solids}}
    table = ship_scale.HULLS[NAME]['units3']
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f'{NAME} units3 {table}: update the shared table')
    m = METRES_PER_UNIT
    return {
        **SCALE, 'published_metres_per_unit': METRES_PER_UNIT,
        'published_note': 'owner override, 2026-10-18: kept at 0.50 (108 m) after the thick cargo front '
                          'deepened the block; option C solves 0.431 (94 m) with the round-2 container bays',
        'measured_this_build': {'enclosed_units3': round(measured), 'solids': solids,
                                'cargo_body_units3': round(hold), 'cargo_body_m3': round(hold*m**3),
                                'cargo_body_share': round(hold/measured, 3)},
        'size_m': [round((b-a)*m, 1) for a, b in zip(lo, hi)],
        'cross_checks_m': {'person 1.8 m in units': round(1.8/m, 2),
                           'cargo hold keel to deck': round((cl_hull.HOLD_DECK-cl_hull.HOLD_KEEL)*m, 1),
                           'cargo body width': round(2*cl_hull.SIDE_Y[1]*m, 1)},
    }


if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'colossus-geometry.blend'))
    lo, hi = bounds()
    manifest = {
        'status': 'Pass 2 plausible detail and pass 4 scale on the clean base; waiting on owner review (pass 3). No damage, condition states or textures.',
        'hull': {
            'name': 'Colossus', 'hullId': 'colossus', 'size': 'CRUISER', 'style': 'LOW_TECH', 'tech': 'Low Tech',
            'designation': 'Heavy Freighter',
            'source': {'ship': SHIP, 'sprite': SPRITE, 'ship_data': 'data/hulls/ship_data.csv (row id colossus)',
                       'lights_layer': 'none in the install'},
            'ship_data': {'min_crew': 40, 'max_crew': 200, 'cargo': 900, 'fuel': 120, 'mass': 1500,
                          'hitpoints': 6000, 'armor': 300},
            'built_in_mods': ['civgrade'], 'ship_system': 'flarelauncher', 'shield': 'FRONT (radius 170, arc 150)',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(lo, hi),
        'sprite': {'size': [184, 224], 'center': [93, 128], 'centreline_y': cl_hull.YC,
                   'pixel_to_model': 'col c, row r covers X 95-r..96-r, Y 92-c..93-c'},
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(b-a, 2) for a, b in zip(lo, hi)]},
        'weapon_slots': [dict({k: v for k, v in s.items() if k != 'prefix'},
                              location=[*s['location'], round(cl_mounts.ANCHOR_Z[s['id']], 2)],
                              objects=f"{s['prefix']} ...", pivot=f"{s['prefix']} pivot",
                              muzzle=f"{s['prefix']} muzzle", muzzle_location=list(cl_mounts.MUZZLES[s['id']]))
                         for s in cl_mounts.SLOTS],
        'engines': [{'index': i, 'pod': 'port' if y > 0 else 'starboard',
                     'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
                     'location': [x, y, cl_engines.POD_Z], 'width': w, 'angle': a, 'style': 'LOW_TECH',
                     'anchor_location': list(cl_engines.ANCHORS[i][0]),
                     'exhaust_direction': list(cl_engines.ANCHORS[i][1])}
                    for i, (x, y, w, a) in enumerate(cl_engines.ENGINES, 1)],
        'object_names': {'Hull ...': 'central cargo hold, hatch cover, longerons, keel spine, aft machinery body',
                         'Cargo <side> fore|aft module': 'the four side cargo modules (tear-apart units)',
                         'Frame ...': 'open lattice trusses at the waist and between the spine and the pods',
                         'Bridge ...': 'deckhouse, windscreen, wing ports, mast',
                         'Drive pod <side> casing': 'the two drive pods',
                         'Engine <i> ...': 'bell / mouth lip / dark throat / exhaust anchor',
                         'Turret <i> WS 00n ... / Launcher <i> WS 00n flare launcher ...': 'mounts; pivot and muzzle empties',
                         'Scar <id> ...': 'reserved for condition states (crashed / broken); not built'},
        'condition_states': {'planned': ['crashed (broken against the northern canyon wall, Asharu)'],
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
