"""Build the Shepherd (Morrow) cinematic model with Blender's Python.

Run from this folder:
  blender --background --factory-startup --python build_shepherd.py -- [options]
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (39, 45), so slot and engine locations from
shepherd.ship are model XY unchanged. Heights are proposed art.

One module per area so later passes do not collide: sh_hull (central crew
body, spine, aft crossbeam and machinery), sh_bridge (bridge cab, habitat
shell, sensor boom and head), sh_dronebay (port hangar pods, the WS 003
openings and launch points, port outrigger), sh_gantry (starboard repair
gantry, turret deck, starboard outrigger), sh_engines (two drive pods and
four maneuvering jets), sh_mounts (WS 007 turret, WS 008 hardpoint);
sh_kit holds the shared helpers, copied rather than imported from any
other hull.

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, starboard-quarter, plus the close-ups
                 bow-detail, bridge-detail, dronebay-detail, gantry-detail,
                 turret-detail, engine-detail, stern-detail; 'details' names
                 all close-ups)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
                 (repeatable) and render it as NAME.png
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip the .blend and geometry.json
Then run compose_review.py (ordinary Python + Pillow + numpy) for the top
sprite overlay, the silhouette IoU and the review sheet.

Condition states (not built yet). The campaign shows Morrow grounded and
damaged on Basin's service cradle (Salvage Rights, Cold Start) and
recovered (Working Passage and after). Those will follow
second-wind-pipeline.md's layers: this clean build stays the base, and a
state adds 'Scar <id> ...' objects and cuts from a scar table with stable
ids, never renaming or deleting a base object. A '--state' option is
reserved for that pass.
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
import sh_kit, sh_hull, sh_bridge, sh_dronebay, sh_gantry, sh_engines, sh_mounts
MODULES = (sh_kit, sh_hull, sh_bridge, sh_dronebay, sh_gantry, sh_engines, sh_mounts)
for module in MODULES:
    importlib.reload(module)

GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SHIP = 'data/hulls/shepherd.ship'
SPRITE = 'graphics/ships/drone_tender.png'

# Real-world scale (pipeline pass 4): see scale_record() and README.txt.
METRES_PER_UNIT = 0.80
REQUIRED_M3 = 9726      # crew deck program less its notional HANGAR (see scale_record)
USABLE = .55
PRESSURE_HULL = ['Hull central body', 'Hull port spine', 'Hull aft machinery block',
                 'Bridge cab', 'Bridge habitat shell']
DRONE_BAY_HULL = ['Drone bay aft hangar pod', 'Drone bay forward hangar pod', 'Drone bay forward service block']
# The Borer has no cinematic model yet; its size is an assumption stated in
# metres (an industrial cutting drone: 4.4 m long, 4.0 m across the arms,
# 1.8 m high), the wing is four (wing_data borer_wing).
DRONE_BAY = {'craft': 'Borer (drone_borer)', 'wing_size': 4, 'assumed_m': {'length': 4.4, 'span': 4.0, 'height': 1.8}}

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern,starboard-quarter')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
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
    for mine in sh_mounts.SLOTS + [dict(sh_dronebay.SLOT, location=sum(sh_dronebay.SLOT['locations'], ()))]:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (tuple(mine['location']), mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from shepherd.ship: {theirs} vs {ours}")
    if len(slots) != len(sh_mounts.SLOTS)+1:
        raise RuntimeError('shepherd.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['angle']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in sh_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from shepherd.ship: {engines}')
    if ship['center'] != [39, 45] or ship['builtInWings'] != ['borer_wing']:
        raise RuntimeError('shepherd.ship centre or built-in wing changed')
    return 'verified against the installed shepherd.ship'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = sh_kit.Kit()
sh_hull.build(kit)
sh_bridge.build(kit)
sh_dronebay.build(kit)
sh_gantry.build(kit)
sh_engines.build(kit)
sh_mounts.build(kit)

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
bg.inputs[0].default_value = (.74, .76, .78, 1)
bg.inputs[1].default_value = .65
scene.view_settings.view_transform = 'AgX'
for name, loc, energy, size in [('Key', (60, 70, 100), 150000, 80), ('Fill', (-50, -60, 55), 85000, 70)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()

# Every view observes the same objects; only the active camera changes.
TARGET = Vector((2, 0, 1))
CAMERAS = [  # name, position, orthographic scale
    ('top', (2, 0, 140), 140),
    ('port-side', (2, 180, 1), 104),
    ('front', (180, 0, 1), 92),
    ('three-quarter', (120, 130, 85), 128),
    ('stern', (-180, 0, 1), 92),
    ('starboard-quarter', (110, -130, 80), 128),
]
# Close-ups, rendered only when named: (name, position, target, ortho scale).
CLOSEUPS = [
    ('bow-detail', (90, 40, 45), (28, 0, 3), 46),
    ('bridge-detail', (70, 30, 40), (24, -2, 6), 30),
    ('dronebay-detail', (40, 80, 60), (10, 19, 2), 54),
    ('gantry-detail', (70, -70, 50), (20, -19, 2), 44),
    ('turret-detail', (25, -55, 40), (-3, -23, 3), 22),
    ('engine-detail', (-70, 70, 30), (-30, 20, 0), 40),
    ('stern-detail', (-90, -35, 35), (-26, 0, 0), 64),
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
    cam.data.clip_end = 1000
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, scale in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 1000
    if name == 'top':
        cam.rotation_euler = (0, 0, math.pi)  # bow left, port below
    else:
        cam.rotation_euler = (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name in wanted:
    scene.camera = bpy.data.objects['Camera '+name]
    # The top view is rendered with a transparent film so its alpha is the
    # model's silhouette; compose_review.py measures IoU from it.
    scene.render.film_transparent = name == 'top'
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)


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
    volume = union_volume(PRESSURE_HULL)
    bay = union_volume(DRONE_BAY_HULL)
    solve = lambda usable, v=volume: (REQUIRED_M3/(usable*v))**(1/3)
    m = METRES_PER_UNIT
    d = DRONE_BAY['assumed_m']
    # Drone stowage: each Borer on a servicing cradle with walk-round room
    # (x1.8 footprint, x1.6 overhead), one spare airframe, plus a 25%
    # parts-and-charging allowance: the Legion's hangar formula.
    stow_m3 = (DRONE_BAY['wing_size']+1)*d['length']*d['span']*1.8*d['height']*1.6*1.25
    r = sh_dronebay.OPENING_R
    return {
        'method': 'deck program (DeckProgramProbe FRIGATE "Drone Tender" 15 30 100 -> WARSHIP role): 2331 m2 rooms, '
                  'less the notional 16x10 HANGAR (160 m2) because the drone bay is sized from the Borer wing instead, '
                  '= 2171 m2 x1.40 circulation x3.2 m deck height = required m3; solved against 55% of the crew '
                  'pressure hull (union of PRESSURE_HULL by vertical ray intervals)',
        'required_m3': REQUIRED_M3, 'pressure_hull': PRESSURE_HULL,
        'pressure_hull_units3': round(volume), 'usable_fraction': USABLE,
        'solved_metres_per_unit': round(solve(USABLE), 3),
        'range': {'roomy_45pct_usable': round(solve(.45), 2), 'cramped_65pct_usable': round(solve(.65), 2)},
        'drone_bay': {
            'hull': DRONE_BAY_HULL, 'units3': round(bay), 'm3_at_published_scale': round(bay*m**3),
            'stowage_needed_m3': round(stow_m3), 'craft': DRONE_BAY['craft'], 'wing_size': DRONE_BAY['wing_size'],
            'craft_assumed_m': d, 'craft_in_units': {k: round(v/m, 2) for k, v in d.items()},
            'opening_clear_diameter_m': round(2*r*m, 2),
            'opening_clears_borer_diagonal': 2*r*m > (d['length']**2+d['span']**2)**.5,
            'note': 'no Borer cinematic model exists yet; its size is an assumption until one is built',
        },
        'size_m': [round((b-a)*m, 1) for a, b in zip(lo, hi)],
        'cross_checks_m': {'person 1.8 m in units': round(1.8/m, 2),
                           'central body keel to deck': round((sh_hull.BODY_DECK-sh_hull.BODY_KEEL)*m, 1)},
        'battle_map': {'cells_long': 24, 'cells_wide': 20, 'metres_per_unit_equivalent': round(24/92, 3),
                       'note': 'landed-ships-true-scale.md / shepherd-recovery.md: tactical-readability footprint '
                               '(one cell is one metre), not the cinematic scale'},
    }


if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'shepherd-geometry.blend'))
    lo, hi = bounds()
    bay_slot = sh_dronebay.SLOT
    manifest = {
        'status': 'Clean base geometry. No damage, condition states or textures.',
        'hull': {
            'name': 'Shepherd', 'campaign_name': 'Morrow', 'hullId': 'shepherd', 'size': 'FRIGATE',
            'style': 'LOW_TECH', 'tech': 'Low Tech', 'manufacturer': 'Ko Combine r450',
            'designation': 'Drone Tender',
            'source': {'ship': SHIP, 'sprite': SPRITE, 'ship_data': 'data/hulls/ship_data.csv (row id shepherd)',
                       'lights_layer': 'none in the install'},
            'ship_data': {'min_crew': 15, 'max_crew': 30, 'cargo': 100, 'fuel': 40, 'mass': 200,
                          'hitpoints': 1200, 'armor': 200},
            'built_in_mods': ['civgrade', 'surveying_equipment', 'repair_gantry'],
            'built_in_wings': ['borer_wing'], 'ship_system': 'maneuveringjets', 'shield': 'OMNI (radius 70)',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(lo, hi),
        'sprite': {'size': [78, 92], 'center': [39, 45],
                   'pixel_to_model': 'col c, row r covers X 46-r..47-r, Y 38-c..39-c'},
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(b-a, 2) for a, b in zip(lo, hi)]},
        'drone_bay': {
            'slot': bay_slot['id'], 'mount': bay_slot['mount'], 'type': bay_slot['type'], 'side': 'port',
            'slot_angle': bay_slot['angle'], 'arc': bay_slot['arc'], 'wing': 'borer_wing (4 drones)',
            'objects': 'Drone bay ...',
            'launch_direction': [0, 0, 1],
            'departure_heading': [1, 0, 0],
            'note': 'three dorsal openings, one per .ship launch location; a drone lifts out along +Z, '
                    'then turns onto the slot angle (0 deg, forward)',
            'openings': sh_dronebay.OPENINGS,
            'launch_points': [[o['slot_location'][0], o['slot_location'][1], o['floor_z']] for o in sh_dronebay.OPENINGS],
            'launch_point_anchors': [o['launch_point_anchor'] for o in sh_dronebay.OPENINGS],
        },
        'weapon_slots': [dict({k: v for k, v in s.items() if k != 'prefix'},
                              location=[*s['location'], round(sh_mounts.ANCHOR_Z[s['id']], 2)],
                              objects=f"{s['prefix']} ...", pivot=f"{s['prefix']} pivot",
                              muzzle=f"{s['prefix']} muzzle", muzzle_location=list(sh_mounts.MUZZLES[s['id']]))
                         for s in sh_mounts.SLOTS],
        'engines': [{'index': i, 'role': 'main drive' if i <= 2 else 'maneuvering jet',
                     'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
                     'location': [x, y, 0], 'width': w, 'angle': a, 'style': 'LOW_TECH',
                     'anchor_location': list(sh_engines.ANCHORS[i][0]),
                     'exhaust_direction': list(sh_engines.ANCHORS[i][1])}
                    for i, (x, y, w, a) in enumerate(sh_engines.ENGINES, 1)],
        'object_names': {'Hull ...': 'central crew body, port spine, aft crossbeam, aft machinery block',
                         'Bridge ...': 'bridge cab, glazing, habitat shell',
                         'Mast ...': 'forward sensor boom, starboard sensor head',
                         'Drone bay ...': 'port hangar pods, service block, WS 003 openings and launch points',
                         'Gantry ...': 'starboard repair gantry frame, core, crane',
                         'Frame ...': 'port and starboard outriggers, starboard turret deck',
                         'Engine <i> ...': 'drive pods and jets; bell / mouth lip / dark throat / exhaust anchor',
                         'Turret 1 WS 007 ... / Hardpoint 1 WS 008 ...': 'weapon mounts; pivot and muzzle empties',
                         'Scar <id> ...': 'reserved for condition states (grounded/damaged, recovered); not built'},
        'condition_states': {'planned': ['grounded', 'recovered'],
                             'contract': 'second-wind-pipeline.md layers: clean base + scar table with stable ids '
                                         '(open -> patched -> replaced -> faded/gone) + wear masks',
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
