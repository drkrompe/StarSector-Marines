"""Build the Onslaught (XIV) battleship cinematic model with Blender's Python.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_onslaught.py -- [options]
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (144, 140 from the bottom), so slot and
engine locations from onslaught.ship are model XY unchanged. Heights are
proposed art.

One module per area so later passes do not collide: os_hull (prow, core,
arms, aft body with its machinery wells, tail, legs), os_armour (the orange
and grey armour bands), os_bridge (spine and
domes), os_machinery (pipes and machinery in the stern wells), os_engines
(the six drive units), os_turrets (24 turrets and the two TPC hardpoints),
os_fittings (masts and running lights); os_kit holds the helpers.

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR (default: here;
                 renders and the .blend are ignored)
  --views a,b    render only these cameras: top, starboard-side, port-side,
                 front, stern, three-quarter, belly; 'details' names the
                 close-ups; 'formation' names the C1 v2 distance views
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip the .blend and geometry.json
  --export       also write the appendable onslaught.blend (export_onslaught.py)
Then run compose_review.py (ordinary Python + Pillow + numpy) for the top
sprite overlay, the silhouette IoU and the review sheets.
"""
from pathlib import Path
import argparse
import importlib
import json
import math
import os
import sys
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import os_kit, os_hull, os_armour, os_bridge, os_machinery, os_engines, os_turrets, os_fittings
for module in (os_kit, os_hull, os_armour, os_bridge, os_machinery, os_engines, os_turrets, os_fittings):
    importlib.reload(module)

# The read-only install's core folder: $STARSECTOR_CORE if set, else the platform
# default (macOS keeps it inside the app bundle).
GAME = Path(os.environ.get('STARSECTOR_CORE') or (
    '/Applications/Starsector.app/Contents/Resources/Java' if sys.platform == 'darwin'
    else r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core'))
SHIP = 'data/hulls/onslaught.ship'
SKIN = 'data/hulls/skins/onslaught_xiv.skin'
SPRITE = 'graphics/ships/onslaught/onslaught_hegemony.png'
SPRITE_PLAIN = 'graphics/ships/onslaught/onslaught_base.png'
VARIANTS = ['data/variants/onslaught/onslaught_xiv_Elite.variant', 'data/variants/onslaught/onslaught_Standard.variant']

# Real-world scale (pipeline pass 4, warship form). Crew and ship spaces from the
# mod's deck program: DeckProgramProbe CAPITAL "Battleship" 750 1500 300 -> WARSHIP,
# 30,875 m2 of rooms. The Onslaught carries no fighters, so the program's three
# notional 3.2 m-high HANGAR rooms (2,160 m2) are dropped: 28,715 m2 x1.40
# circulation x3.2 m = 128,643 m3 of habitable volume. The rest of the hull is
# budgeted explicitly below; the driver re-measures the pressure hull on every
# build and records the solved value beside the published one (scale_record).
METRES_PER_UNIT = 0.65
REQUIRED_M3 = 128643
NON_HABITABLE_BUDGET = {   # share of the pressure hull, judged
    'armour (rating 1750, the heaviest in the game; a thick, deep hull)': .20,
    'reactors, flux plant (17,000 capacity, 600 dissipation), drive machinery': .14,
    'twenty-four barbettes, two TPC hardpoints, magazines and hoists': .10,
    'fuel (400), supplies and water tanks, structure, trunks': .12,
}
USABLE = round(1-sum(NON_HABITABLE_BUDGET.values()), 3)
USABLE_RANGE = (.40, .48)
PRESSURE_HULL = ['Hull prow', 'Hull core', 'Hull port arm', 'Hull starboard arm', 'Hull aft body', 'Hull tail',
                 'Hull port aft leg', 'Hull starboard aft leg', 'Bridge spine', 'Bridge command dome', 'Bridge aft dome']

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,starboard-side,port-side,front,stern,three-quarter,belly')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--export', action='store_true')
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
    mine = os_turrets.SLOTS + os_turrets.HARDPOINTS + os_turrets.DECORATIVE
    for sid, loc, angle, arc, size, typ, _ in mine:
        s = slots[sid]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['size'], s['type'])
        if theirs != (loc, angle, arc, size, typ):
            raise RuntimeError(f'{sid} differs from onslaught.ship: {theirs} vs {(loc, angle, arc, size, typ)}')
    if len(slots) != len(mine):
        raise RuntimeError('onslaught.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['contrailSize'], e['length'], e['angle'])
               for e in ship['engineSlots']]
    if engines != [tuple(e) for e in os_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from onslaught.ship: {engines}')
    skin = (GAME/SKIN).read_text(encoding='utf-8')
    if '"removeWeaponSlots":[]' not in skin.replace(' ', '') or '"removeEngineSlots":[]' not in skin.replace(' ', ''):
        raise RuntimeError('onslaught_xiv.skin now changes the slots')
    return 'verified against the installed onslaught.ship and onslaught_xiv.skin'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = os_kit.Kit()
hull = os_hull.build(kit)
os_armour.build(kit)
os_bridge.build(kit)
os_machinery.build(kit)
os_engines.build(kit, hull)
os_turrets.build(kit)
os_fittings.build(kit)

scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = map(int, args.res.split('x'))
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']
scene.view_settings.view_transform = 'AgX'
STUDIO = []
for name, loc, energy, size in [('Key', (300, 340, 480), 3.6e6, 320), ('Fill', (-240, -300, 260), 2.0e6, 280)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = 'Studio '+name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()
    STUDIO.append(obj)
# Space light for the formation views: one hard sun, high and to one side, no fill.
bpy.ops.object.light_add(type='SUN', location=(0, 0, 300))
SPACE_SUN = bpy.context.object
SPACE_SUN.name = 'Space sun'
SPACE_SUN.data.energy = 4.0
SPACE_SUN.data.angle = math.radians(.53)
SPACE_SUN.rotation_euler = Vector((.35, -.75, .55)).to_track_quat('Z', 'Y').to_euler()


def bounds(names=None):
    lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in scene.objects:
        if ob.type == 'MESH' and (names is None or ob.name in names):
            for c in ob.evaluated_get(dg).bound_box:
                w = ob.matrix_world @ Vector(c)
                lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
    return lo, hi


LO, HI = bounds()
S = METRES_PER_UNIT
MID = Vector(((LO.x+HI.x)/2, 0, (LO.z+HI.z)/2))
TARGET = Vector((50, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (50, 0, 300), 540),
    ('starboard-side', (50, -500, 0), 400),
    ('port-side', (50, 500, 0), 400),
    ('front', (500, 0, 0), 330),
    ('three-quarter', (430, -420, 300), 480),
    ('stern', (-500, 0, 0), 330),
    ('belly', (300, -330, -280), 480),
]
CLOSEUPS = [
    ('bow-detail', (420, -200, 160), (180, 0, 0), 120),
    ('arm-detail', (360, -330, 200), (150, -80, 5), 120),
    ('bridge-detail', (180, -170, 160), (15, 0, 25), 110),
    ('turret-detail', (230, -200, 160), (80, -80, 15), 70),
    ('machinery-detail', (-150, -230, 260), (-55, -55, -5), 110),
    ('engine-detail', (-330, -200, 70), (-100, -40, -8), 110),
    ('stern-detail', (-330, 0, 40), (-110, 0, -8), 230),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))

# C1 v2 formation views: the hull at 1.5 and 3 km, the doc's stated range. The
# shot's camera is not built yet, so a 24 mm lens on a 36 mm sensor stands in;
# at that lens the hull spans about the 80-150 px c1-v2-shot.md expects.
FORMATION_LENS = 24.0
FORMATION = []
for km in (1.5, 3.0):
    for label, az, el in (('quarter', -40, 18), ('abeam', -90, 8), ('bow', -150, 12), ('high', 30, 40)):
        d = km*1000/S
        a, e = math.radians(az), math.radians(el)
        FORMATION.append((f'formation-{label}-{km:g}km', (MID.x+d*math.cos(e)*math.cos(a), d*math.cos(e)*math.sin(a),
                                                          MID.z+d*math.sin(e))))

wanted = []
for v in [v for v in args.views.split(',') if v]:
    if v == 'details':
        wanted += [c[0] for c in CLOSEUPS[:7]]
    elif v == 'formation':
        wanted += [c[0] for c in FORMATION]
    else:
        wanted.append(v)
wanted += [spec.split(':')[0] for spec in args.cam]
for name, pos, target, scale in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 3000
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, scale in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 3000
    if name == 'top':
        cam.rotation_euler = (0, 0, math.pi)  # bow left, port below
    else:
        cam.rotation_euler = (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos in FORMATION:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = FORMATION_LENS, 36.0, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 10, 20000
    cam.rotation_euler = (MID-cam.location).to_track_quat('-Z', 'Y').to_euler()

for name in wanted:
    space = name.startswith('formation-')
    scene.camera = bpy.data.objects['Camera '+name]
    for ob in STUDIO:
        ob.hide_render = space
    SPACE_SUN.hide_render = not space
    bg.inputs[0].default_value = (0, 0, 0, 1) if space else (.74, .76, .78, 1)
    bg.inputs[1].default_value = 0 if space else .65
    scene.render.film_transparent = name == 'top'
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)


def union_volume(names, step=2.0):
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


def scale_record():
    hull_u3 = union_volume(PRESSURE_HULL)
    solve = lambda usable: (REQUIRED_M3/(usable*hull_u3))**(1/3)
    size = [round((b-a)*S, 1) for a, b in zip(LO, HI)]
    return {
        'method': 'warship pass 4: deck program (DeckProgramProbe CAPITAL "Battleship" 750 1500 300 -> WARSHIP, '
                  '30875 m2) less its notional HANGAR rooms (no fighter bays): 28715 m2 x1.40 circulation x3.2 m '
                  '= required habitable m3; solved against the habitable share of the pressure hull (union of its '
                  'solids, measured on every build) after an explicit non-habitable budget',
        'required_habitable_m3': REQUIRED_M3, 'pressure_hull': PRESSURE_HULL, 'pressure_hull_units3': round(hull_u3),
        'non_habitable_budget': NON_HABITABLE_BUDGET, 'usable_fraction': USABLE,
        'solved_metres_per_unit': round(solve(USABLE), 3),
        'range': {f'{int(USABLE_RANGE[1]*100)}pct_habitable': round(solve(USABLE_RANGE[1]), 3),
                  f'{int(USABLE_RANGE[0]*100)}pct_habitable': round(solve(USABLE_RANGE[0]), 3),
                  'battlecarrier_33pct_for_comparison': round(solve(.33), 3),
                  'freighter_55pct_for_comparison': round(solve(.55), 3)},
        'published_vs_solved': round(S/solve(USABLE), 3),
        'size_m': size, 'hull_volume_m3': round(hull_u3*S**3),
        'real_world': {'Iowa-class battleship': '270 m, 33 m beam, about 2,700 crew in wartime',
                       'Yamato': '263 m, 38.9 m beam, about 2,800 crew',
                       'this hull': f'{size[0]} m long, {size[1]} m beam, {size[2]} m keel to mast, '
                                    f'{round(hull_u3*S**3/1000)}k m3 enclosed for 750-1500 crew',
                       'per crew at max crew m3': round(hull_u3*S**3/1500)},
        'cross_checks_m': {'person 1.8 m in units': round(1.8/S, 2),
                           'door 2.1 m / rail 1.1 m / hatch 2 m in units': [round(2.1/S, 1), round(1.1/S, 1), round(2/S, 1)],
                           'main bell mouth diameter': round(2*os_engines.MOUTH_R[30]*S, 1),
                           'large turret ring diameter': round(2*os_turrets.RING['LARGE']*S, 1),
                           'medium turret ring diameter': round(2*os_turrets.RING['MEDIUM']*S, 1),
                           'keel to deck at the core': round((os_hull.DECK['core']+50)*S, 1)},
        'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number',
    }


if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    for ob in STUDIO:
        ob.hide_render = False
    SPACE_SUN.hide_render = True
    bg.inputs[0].default_value = (.74, .76, .78, 1)
    bg.inputs[1].default_value = .65
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'onslaught-geometry.blend'))
    manifest = {
        'status': 'Pass 1 blockout: silhouette, deep curved sections, mass, slots, drives, turrets, stern machinery '
                  'wells. Undamaged Hegemony (XIV) battleship; flat Hegemony base materials.',
        'hull': {
            'name': 'Onslaught (XIV)', 'skinHullId': 'onslaught_xiv', 'baseHullId': 'onslaught', 'size': 'CAPITAL_SHIP',
            'style': 'LOW_TECH', 'tech': 'XIV Battlegroup', 'designation': 'Battleship',
            'source': {'ship': SHIP, 'skin': SKIN, 'sprite': SPRITE, 'sprite_plain': SPRITE_PLAIN,
                       'variants': VARIANTS, 'ship_data': 'data/hulls/ship_data.csv (row id onslaught)'},
            'ship_data': {'min_crew': 750, 'max_crew': 1500, 'cargo': 300, 'fuel': 400, 'fighter_bays': 0,
                          'armor': 1750, 'hitpoints': 20000},
            'built_in_mods': ['hbi', 'fourteenth (XIV skin)'], 'built_in_weapons': {'WS 016': 'tpc', 'WS 017': 'tpc'},
            'ship_system': 'burndrive', 'shield': 'FRONT 180',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(),
        'sprite': {'size': [288, 384], 'center': [144, 140],
                   'pixel_to_model': 'col c, row r covers X 243-r..244-r, Y 143-c..144-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)]},
        'weapon_slots': os_turrets.records(),
        'engines': os_engines.records(),
        'running_lights': os_fittings.records(),
        'object_names': {'Hull ...': 'prow, core, port/starboard arm, aft body, tail, port/starboard aft leg, waist',
                         'Bridge ...': 'spine, command dome, aft dome, glazing',
                         'Machinery <side> ...': 'pipe runs, well frames, headers, pumps and valves in the stern wells',
                         'Engine <i> ...': 'drive can, aft-facing bell, mouth lip, dark throat, shroud or drive pod; '
                                           'exhaust anchor (empty, +X along the exhaust)',
                         'Weapon <slot> ...': 'barbette (fixed) and pivot (empty) with its trainable parts; '
                                              'WS 016/017 hardpoint housing, barrel and muzzle (empty)',
                         'Fitting ...': 'sensor mast on WS 025, aft radome',
                         'Light <where> <k>': 'running lights; positions, colours and blink in running_lights'},
        'formation': {'shot': 'second-wind-cinematic/corvus-approach/c1-v2-shot.md',
                      'lens_mm_stand_in': FORMATION_LENS, 'sensor_mm': 36.0,
                      'cameras': {n: [round(v, 1) for v in p] for n, p in FORMATION}},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
    }
    path = OUT/'geometry.json'
    if path.exists():  # keep the records compose_review.py wrote
        old = json.loads(path.read_text(encoding='utf-8'))
        for key in ('silhouette', 'formation_extent'):
            if key in old:
                manifest[key] = old[key]
    path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('scale', json.dumps(manifest['scale']))
