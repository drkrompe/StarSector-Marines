"""Build the Phaeton tanker cinematic model with Blender's Python.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_phaeton.py -- [options]
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (60, 94), so slot and engine locations from
phaeton.ship are model XY unchanged. Heights are proposed art.

One module per area so later passes do not collide: ph_hull (tank hull,
the three spherical tanks, gallery block, sponsons, aft machinery spine),
ph_bow (bridge cab, hose crane), ph_engines (stern drive block, six bells,
reaction drums, drive housings and guard arcs), ph_mounts (four turrets,
two flare launchers); ph_kit holds the shared helpers, copied rather than
imported from any other hull.

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, starboard-quarter, plus the close-ups;
                 'details' names all close-ups)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --figure       stand a 1.8 m figure at the bridge door (review only, never saved)
  --engine workbench   fast flat renders for internal checks only
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
import ph_kit, ph_hull, ph_bow, ph_engines, ph_mounts
MODULES = (ph_kit, ph_hull, ph_bow, ph_engines, ph_mounts)
for module in MODULES:
    importlib.reload(module)

GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SHIP = 'data/hulls/phaeton.ship'
SPRITE = 'graphics/ships/tanker_medium.png'
NAME = 'Phaeton'

# Real-world scale (pipeline pass 4): the shared basis (../ship_scale.py,
# option C) solved against the enclosed volume of ship_scale.SOLIDS
# ['phaeton'], re-measured on every build. Until the hull is in the table
# the option C estimate stands (73 m over 229 units).
if NAME in ship_scale.HULLS:
    SCALE = ship_scale.record(NAME)
    METRES_PER_UNIT = ship_scale.PUBLISHED.get(NAME, round(SCALE['solved_metres_per_unit'], 2))
else:
    SCALE = None
    METRES_PER_UNIT = .32

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern,starboard-quarter')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--figure', action='store_true')
parser.add_argument('--engine', default='cycles', choices=('cycles', 'workbench'),
                    help='workbench: fast flat renders for internal checks (IoU), never for the owner')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out or HERE).resolve()
OUT.mkdir(parents=True, exist_ok=True)


def check_against_install():
    """Fail if the vanilla slots no longer match the constants the model uses."""
    path = GAME/SHIP
    if not path.exists():
        return 'install not found; constants used as committed'
    ship = json.loads(path.read_text(encoding='utf-8'))
    slots = {s['id']: s for s in ship['weaponSlots']}
    for mine in ph_mounts.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (tuple(mine['location']), mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from phaeton.ship: {theirs} vs {ours}")
    if len(slots) != len(ph_mounts.SLOTS):
        raise RuntimeError('phaeton.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['angle']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in ph_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from phaeton.ship: {engines}')
    if ship['center'] != [60, 94] or ship['hullId'] != 'phaeton':
        raise RuntimeError('phaeton.ship centre or hull id changed')
    return 'verified against the installed phaeton.ship'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = ph_kit.Kit()
for module in MODULES[1:]:
    module.build(kit)

scene = bpy.context.scene
scene.render.engine = 'CYCLES' if args.engine == 'cycles' else 'BLENDER_WORKBENCH'
_prefs = bpy.context.preferences.addons['cycles'].preferences
for _kind in () if args.engine != 'cycles' else ('OPTIX', 'CUDA', 'HIP', 'METAL', 'ONEAPI'):
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
lights = []
for name, loc, energy, size in [('Key', (120, 140, 190), 520000, 150), ('Fill', (-110, -120, 100), 300000, 130)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (Vector((20, 0, 0))-obj.location).to_track_quat('-Z', 'Y').to_euler()
    lights.append(obj)

figure = []
if args.figure:  # a 1.8 m person beside the bridge, for review renders only
    h = 1.8/METRES_PER_UNIT
    z0 = kit.surface(104, -26)[0].z
    figure.append(kit.cylinder('Review figure', (98, -24, z0+h/2), h*.14, h, kit.cream, 'Z', 12, 0))

TARGET = Vector((22, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (22, 0, 220), 250),
    ('port-side', (22, 300, 2), 250),
    ('front', (320, 0, 2), 140),
    ('three-quarter', (220, 230, 150), 260),
    ('stern', (-280, 0, 2), 140),
    ('starboard-quarter', (-160, -230, 140), 260),
]
CLOSEUPS = [
    ('bow-detail', (190, 80, 90), (110, 0, 8), 70),
    ('bridge-detail', (160, -90, 60), (110, -22, 12), 40),
    ('crane-detail', (150, 120, 80), (105, 38, 15), 50),
    ('tanks-detail', (60, 140, 110), (44, 0, 8), 120),
    ('turret-detail', (70, 90, 50), (38, 38, 8), 30),
    ('engine-detail', (-170, 90, 50), (-80, 20, 0), 80),
    ('stern-detail', (-180, -60, 50), (-75, 0, 0), 110),
    ('underside', (10, 60, -220), (20, 0, -10), 250),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
wanted = []
for v in [v for v in args.views.split(',') if v]:
    wanted += [c[0] for c in CLOSEUPS[:8]] if v == 'details' else [v]
wanted += [spec.split(':')[0] for spec in args.cam]
for name, pos, target, sc in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = sc
    cam.data.clip_end = 2000
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, sc in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = sc
    cam.data.clip_end = 2000
    if name == 'top':
        cam.rotation_euler = (0, 0, math.pi)  # bow left, port below
    else:
        cam.rotation_euler = (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name in wanted:
    scene.camera = bpy.data.objects['Camera '+name]
    scene.render.film_transparent = name == 'top'
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)
for ob in figure:
    bpy.data.objects.remove(ob, do_unlink=True)


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
    spec = ship_scale.SOLIDS.get('phaeton')
    if spec is None:
        return {'note': 'not in ship_scale.py yet; option C estimate 73 m over 229 units',
                'provisional_metres_per_unit': METRES_PER_UNIT}
    solids = sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and spec['include'](o.name))
    measured = union_volume(solids, spec['step'])
    table = ship_scale.HULLS[NAME]['units3']
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f'{NAME} units3 {table}: update the shared table')
    m = METRES_PER_UNIT
    tanks = [{'name': n, 'diameter_m': round(2*r*m, 1), 'volume_m3': round(4/3*math.pi*(r*m)**3)}
             for n, _, r in ph_hull.TANKS]
    return {
        **SCALE, 'published_metres_per_unit': METRES_PER_UNIT,
        'measured_this_build': {'enclosed_units3': round(measured), 'solids': solids},
        'size_m': [round((b-a)*m, 1) for a, b in zip(lo, hi)],
        'cross_checks_m': {
            'person 1.8 m in units': round(1.8/m, 2),
            'cargo tanks': tanks,
            'tank volume total m3': sum(t['volume_m3'] for t in tanks),
            'fuel 800 x 1.5 m3 (ship_scale)': 1200,
            'hull keel to deck': round((ph_hull.DECK-ph_hull.KEEL)*m, 1),
            'against known ships': {'Hound (A) 61 m': round((hi.x-lo.x)*m/61, 2),
                                    'Colossus 108 m': round((hi.x-lo.x)*m/108, 2)},
        },
    }


if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'phaeton-geometry.blend'))
    lo, hi = bounds()
    if 'phaeton' not in ship_scale.SOLIDS:
        print('ENCLOSED', round(union_volume(sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and (
            o.name.startswith(('Hull ', 'Tank ', 'Bridge cab')) and 'skirt' not in o.name and 'sponson' not in o.name
            and 'skylight' not in o.name)), 1.0)))
    manifest = {
        'status': 'Pass 1 blockout on the clean base. No detail, damage, condition states or textures.',
        'hull': {
            'name': NAME, 'hullId': 'phaeton', 'size': 'DESTROYER', 'style': 'LOW_TECH', 'tech': 'Low Tech',
            'designation': 'Tanker',
            'source': {'ship': SHIP, 'sprite': SPRITE, 'ship_data': 'data/hulls/ship_data.csv (row id phaeton)',
                       'lights_layer': 'none in the install'},
            'ship_data': {'min_crew': 10, 'max_crew': 20, 'cargo': 20, 'fuel': 800, 'mass': 400,
                          'hitpoints': 2000, 'armor': 350},
            'built_in_mods': ['civgrade'], 'ship_system': 'flarelauncher', 'shield': 'OMNI (radius 160)',
            'campaign_role': 'the Asharu convoy tanker, destroyed in the opening (asharu-battle-shot.md S1, S6)',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(lo, hi),
        'sprite': {'size': [120, 234], 'center': [60, 94],
                   'pixel_to_model': 'col c, row r covers X 139-r..140-r, Y 59-c..60-c'},
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(b-a, 2) for a, b in zip(lo, hi)]},
        'tanks': [{'name': n, 'centre': [x, 0, ph_hull.DECK], 'radius': r} for n, x, r in ph_hull.TANKS],
        'weapon_slots': [dict({k: v for k, v in s.items() if k != 'prefix'},
                              location=[*s['location'], round(ph_mounts.ANCHOR_Z[s['id']], 2)],
                              objects=f"{s['prefix']} ...", pivot=f"{s['prefix']} pivot",
                              muzzle=f"{s['prefix']} muzzle", muzzle_location=list(ph_mounts.MUZZLES[s['id']]))
                         for s in ph_mounts.SLOTS],
        'engines': [{'index': i, 'role': 'main drive', 'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
                     'location': [x, y, ph_engines.ENGINE_Z], 'width': w, 'angle': a, 'style': 'LOW_TECH',
                     'anchor_location': list(ph_engines.ANCHORS[i][0]),
                     'exhaust_direction': list(ph_engines.ANCHORS[i][1])}
                    for i, (x, y, w, a) in enumerate(ph_engines.ENGINES, 1)],
        'object_names': {'Hull ...': 'tank hull, port gallery block, turret sponsons, aft machinery spine, reactor dome',
                         'Tank <i> ...': 'the three spherical cargo tanks (aft, mid, fore) and their skirts',
                         'Bridge ...': 'bridge cab and glazing on the starboard bow',
                         'Crane ...': 'port bow hose crane',
                         'Drive ...': 'stern drive block, reaction drums, drive housings',
                         'Frame ...': 'guard arcs and yokes',
                         'Engine <i> ...': 'bell / mouth lip / dark throat / exhaust anchor',
                         'Turret <n> WS 00x ... / Launcher <n> WS 00x ...': 'weapon mounts; pivot and muzzle empties',
                         'Scar <id> ...': 'reserved for the destroyed condition state'},
        'condition_states': {'planned': ['destroyed'],
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
    print('SIZE', manifest['dimensions']['size'])
