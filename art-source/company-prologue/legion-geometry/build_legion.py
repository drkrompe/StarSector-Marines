"""Build the Legion (XIV) battlecarrier cinematic model with Blender's Python.

Run from this folder:
  blender --background --factory-startup --python build_legion.py -- [options]
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (150, 149), so slot, bay and engine locations
from legion.ship are model XY unchanged. Heights are proposed art.

One module per area so later passes do not collide: lg_hull (bodies, spines,
armour belts and bow blades), lg_bridge (the command dome), lg_fittings (masts, sensors
and running lights), lg_wings (the wing arms and the
four hangars under them), lg_droop (the arms' anhedral), lg_engines (engine block and the ten drive units),
lg_turrets (the fifteen turrets on named pivots); lg_kit holds the helpers.

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR
  --views a,b    render only these cameras: top, starboard-side, port-side,
                 front, stern, three-quarter; c2-entry, c2-abeam, c2-exit
                 (the C2 PATROL ABEAM cabin camera under c2_core's sun at
                 C2 3.0 / 8.0 / 11.4 s; 'c2' names all three); close-ups
                 ('details' names all of them)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip the .blend and geometry.json
Then run compose_review.py (ordinary Python + Pillow + numpy) for the top
sprite overlay, the silhouette IoU, the C2 composites and the review sheet.
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
import lg_kit, lg_hull, lg_bridge, lg_wings, lg_droop, lg_ventral, lg_engines, lg_fittings, lg_turrets
for module in (lg_kit, lg_hull, lg_bridge, lg_wings, lg_droop, lg_ventral, lg_engines, lg_fittings, lg_turrets):
    importlib.reload(module)
# C2's shared camera, sun and track model (read only; pure standard library).
sys.path.insert(0, str(HERE.parent/'second-wind-cinematic'/'patrol-abeam'))
import c2_core

GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SHIP = 'data/hulls/legion.ship'
SKIN = 'data/hulls/skins/legion_xiv.skin'
SPRITE = 'graphics/ships/legion/legion_xiv.png'
SPRITE_PLAIN = 'graphics/ships/legion/legion.png'
VARIANTS = ['data/variants/legion/legion_xiv_Elite.variant', 'data/variants/legion/legion_Strike.variant']

# Real-world scale (pipeline pass 4, warship form). Each hull is scaled from
# what it is; the Legion is a battlecarrier. Crew and ship spaces come from the
# mod's deck program (DeckProgramProbe CAPITAL "Battlecarrier" 700 1500 300 ->
# CARRIER): 30,827 m2 of rooms, less its three notional 3.2 m-high HANGAR rooms
# (2,160 m2), which the fighter requirement below replaces, is 28,667 m2 x1.40
# circulation x3.2 m = 128,428 m3 of habitable volume. A battlecarrier's hull is
# mostly not habitable: fighter hangars, launch bays and their magazines, belt
# armour (rating 1650), reactors and flux plant, fifteen barbettes with their
# magazines, drive machinery, tanks and structure. The budget below puts the
# habitable share at about a third (30-35%). Fighter stowage is sized in metres
# from each fighter's own scale (fighters-geometry). The driver re-measures the pressure hull on every build and
# records the solved value beside the published one (scale_record).
METRES_PER_UNIT = 0.61
REQUIRED_M3 = 128428
USABLE = .33
USABLE_RANGE = (.30, .35)
# Fighter craft: installed .ship height x width in sprite px (length, span), an
# assumed height in px, and each fighter's own metres per px. Fighters have
# their own scales (fighters-geometry: Broadsword 0.50 from its cockpit); the
# Hoplon and Piranha are outside that package and take its heavy-craft 0.50.
# Stowed at 1.8x footprint for servicing under a 1.6x-height overhead, plus one
# spare per wing and 25% for ordnance handling.
CRAFT = {'broadsword': (33, 30, 6.5, .50), 'hoplon': (36, 38, 8.0, .50), 'piranha': (34, 30, 6.5, .50)}
WINGS = {'legion_xiv_Elite': [('broadsword', 3)]*2+[('hoplon', 2)]*2, 'legion_Strike': [('piranha', 3)]*4}
NON_HABITABLE_BUDGET = {  # share of the pressure hull, judged; the hangar share is measured below
    'fighter hangars, launch bays, fighter ordnance': None,
    'belt and deck armour (rating 1650, about 1.5 m over the envelope)': .15,
    'reactors, flux plant, drive machinery': .12,
    'fifteen barbettes, gun and missile magazines': .05,
    'fuel (300), supplies and water tanks, structure, trunks': .10,
}
PRESSURE_HULL = ['Hull fore body', 'Hull aft body', 'Hull ventral keel', 'Hull dorsal spine', 'Hull tail spine', 'Bridge command dome',
                 'Wing port sponson', 'Wing starboard sponson'] + [
    f'Wing {s} hangar' for s in ('port', 'starboard')] + [
    f'Wing {s} transfer gallery' for s in ('port', 'starboard')]   # pass 2b: the hangar-to-hold transfer trunks
MACHINERY = ['Hull engine block', 'Hull port engine housing', 'Hull starboard engine housing']

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,starboard-side,port-side,front,stern,three-quarter,c2')
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
    for mine in lg_turrets.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (mine['location'], mine['angle'], mine['arc'], 'TURRET', mine['size'],
                lg_turrets.BASE_TYPE.get(mine['id'], mine['type']))
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from legion.ship: {theirs} vs {ours}")
    for mine in lg_wings.LAUNCH_BAYS:
        s = slots[mine['id']]
        if (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['type']) != \
                (mine['locations'], mine['angle'], mine['arc'], mine['mount'], mine['type']):
            raise RuntimeError(f"{mine['id']} differs from legion.ship")
    if len(slots) != len(lg_turrets.SLOTS)+len(lg_wings.LAUNCH_BAYS):
        raise RuntimeError('legion.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['contrailSize'], e['length']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in lg_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from legion.ship: {engines}')
    skin = (GAME/SKIN).read_text(encoding='utf-8')
    for sid in lg_turrets.BASE_TYPE:
        mine = next(s for s in lg_turrets.SLOTS if s['id'] == sid)
        if f'"{sid}"' not in skin or f'"type": "{mine["type"]}"' not in skin:
            raise RuntimeError(f'legion_xiv.skin no longer retypes {sid} to {mine["type"]}')
    return 'verified against the installed legion.ship and legion_xiv.skin'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = lg_kit.Kit()
lg_hull.build(kit)
lg_bridge.build(kit)
lg_wings.build(kit)
lg_droop.apply(kit)   # anhedral, before anything seats itself by ray-cast
lg_ventral.build(kit)  # the underside: belly fittings, pockets cut into the ventral keel
lg_engines.build(kit)
lg_fittings.build(kit)
lg_turrets.build(kit)

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
for name, loc, energy, size in [('Key', (250, 300, 450), 3.0e6, 300), ('Fill', (-200, -275, 250), 1.75e6, 260)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = 'Studio '+name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()
    STUDIO.append(obj)
# C2's light: one hard sun (c2_core.SUN points toward Corvus) and a weak blue
# planet-shine from Jangala below. No ambient. The model's axes are C2's world
# axes (the patrol flies bow first along +X with its starboard toward the cabin).
C2_LIGHTS = []
for name, direction, strength, colour in [('C2 sun', c2_core.SUN, 4.0, c2_core.SUN_COLOUR),
                                          ('C2 planet-shine', (0, 0, -1), 4.0*c2_core.PLANETSHINE, (.55, .7, 1.0))]:
    bpy.ops.object.light_add(type='SUN', location=(0, 0, 200))
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = strength
    obj.data.color = colour
    obj.data.angle = math.radians(.53)
    obj.rotation_euler = Vector(direction).to_track_quat('Z', 'Y').to_euler()  # shines along -Z, from `direction`
    C2_LIGHTS.append(obj)


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
# C2 placement: closest approach puts the near (starboard) extreme 105 m from
# the glass, and the crown 3 m below eye level (c2-layout.md, Scale and distance).
C2_CENTRE_Y_M = c2_core.PATROL_NEAR_FLANK_M + (-LO.y)*S
C2_ORIGIN_Z_M = -3.0 - HI.z*S
C2_TIMES = {'c2-entry': 3.0, 'c2-abeam': c2_core.PATROL_T_MID, 'c2-exit': 11.4}


def c2_camera(t):
    """Cabin camera in model units at C2 time t (the camera is the world origin)."""
    return (-c2_core.patrol_x(t)/S, -C2_CENTRE_Y_M/S, -(C2_ORIGIN_Z_M+c2_core.patrol_rise(t))/S)


TARGET = Vector((0, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (0, 0, 300), 580),
    ('starboard-side', (0, -500, 0), 335),
    ('port-side', (0, 500, 0), 335),
    ('front', (500, 0, 0), 330),
    ('three-quarter', (330, -390, 240), 420),
    ('stern', (-500, 0, 0), 330),
]
# Close-ups, rendered only when named: (name, position, target, ortho scale).
CLOSEUPS = [
    ('bow-detail', (300, -160, 150), (110, 0, 5), 120),
    ('bridge-detail', (170, -150, 150), (35, 0, 20), 80),
    ('turret-detail', (160, -120, 110), (78, -40, 12), 50),
    ('flight-deck-detail', (160, -260, 150), (55, -100, 0), 90),
    ('aft-deck-detail', (-150, -260, 150), (-45, -100, 0), 90),
    ('flank-detail', (0, -320, 40), (0, -120, -2), 140),
    ('engine-detail', (-320, -160, 80), (-118, -40, -4), 110),
    ('stern-detail', (-330, 0, 60), (-110, 0, -2), 180),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
wanted = []
for v in [v for v in args.views.split(',') if v]:
    wanted += [c[0] for c in CLOSEUPS[:8]] if v == 'details' else list(C2_TIMES) if v == 'c2' else [v]
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
pitch = math.radians(c2_core.PITCH_DEG)
for name, t in C2_TIMES.items():
    bpy.ops.object.camera_add(location=c2_camera(t))
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = c2_core.LENS_MM, c2_core.SENSOR_MM, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 1, 3000
    cam.rotation_euler = Vector((0, math.cos(pitch), -math.sin(pitch))).to_track_quat('-Z', 'Y').to_euler()

for name in wanted:
    c2 = name.startswith('c2-')
    scene.camera = bpy.data.objects['Camera '+name]
    for ob in STUDIO:
        ob.hide_render = c2
    for ob in C2_LIGHTS:
        ob.hide_render = not c2
    bg.inputs[0].default_value = (0, 0, 0, 1) if c2 else (.74, .76, .78, 1)
    bg.inputs[1].default_value = 0 if c2 else .65
    # Top and C2 views keep a transparent film: the top alpha is the silhouette
    # compose_review.py measures; the C2 alpha is laid over Jangala there.
    scene.render.film_transparent = name == 'top' or c2 or name.startswith('sil-')
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


def hangar_m3():
    """Stowage volume the fighter complement needs, in cubic metres, per variant."""
    out = {}
    for variant, wings in WINGS.items():
        total = 0.0
        for craft, n in wings:
            length, span, height, mpp = CRAFT[craft]
            total += (n+1)*(length*mpp)*(span*mpp)*1.8*(height*mpp)*1.6
        out[variant] = round(total*1.25)
    return out


def scale_record():
    hull = union_volume(PRESSURE_HULL)
    machinery = union_volume(MACHINERY)
    solve = lambda usable, volume: (REQUIRED_M3/(usable*volume))**(1/3)
    hangar = hangar_m3()
    hangar_share = max(hangar.values())/(hull*METRES_PER_UNIT**3)
    budget = dict(NON_HABITABLE_BUDGET)
    budget['fighter hangars, launch bays, fighter ordnance'] = round(hangar_share, 3)
    habitable = 1-sum(budget.values())
    return {
        'method': 'warship pass 4: deck program (DeckProgramProbe CAPITAL "Battlecarrier" 700 1500 300 -> CARRIER) '
                  'less its notional HANGAR rooms: 28667 m2 x1.40 circulation x3.2 m = required habitable m3; '
                  'solved against the habitable share of the pressure hull (union of its solids, measured on every '
                  'build); fighter stowage sized in metres at each fighter scale',
        'required_habitable_m3': REQUIRED_M3, 'pressure_hull': PRESSURE_HULL, 'pressure_hull_units3': round(hull),
        'machinery_units3': round(machinery), 'usable_fraction': USABLE,
        'non_habitable_budget': budget, 'habitable_share_from_budget': round(habitable, 3),
        'fighter_stowage_m3': hangar, 'fighter_stowage_share_of_hull_at_published': round(hangar_share, 3),
        'budget_note': 'with the fighters at their own scales (0.50 m/px) the itemised budget leaves more than a '
                       'third habitable; 33% is the requested warship share, and budget_share in range is what '
                       'the itemised budget alone solves to. Owner call if they differ much.',
        'solved_metres_per_unit': round(solve(USABLE, hull), 3),
        'range': {f'{int(USABLE_RANGE[1]*100)}pct_habitable': round(solve(USABLE_RANGE[1], hull), 3),
                  f'{int(USABLE_RANGE[0]*100)}pct_habitable': round(solve(USABLE_RANGE[0], hull), 3),
                  'budget_share': round(solve(habitable, hull), 3),
                  'freighter_55pct_for_comparison': round(solve(.55, hull), 3)},
        'published_vs_solved': round(METRES_PER_UNIT/solve(USABLE, hull), 3),
        'size_m': [round((b-a)*METRES_PER_UNIT, 1) for a, b in zip(LO, HI)],
        'hull_volume_m3': round(hull*METRES_PER_UNIT**3),
        'real_world': {'Nimitz': '333 m, about 5,000 crew aboard, 76.8 m flight-deck beam',
                       'fleet and light carriers': '200-260 m',
                       'this hull': f'{round(300*METRES_PER_UNIT)} m long and {round(294*METRES_PER_UNIT)} m wide, '
                                    f'{round(hull*METRES_PER_UNIT**3/1000)}k m3 enclosed for 700-1500 crew',
                       'per crew at max crew m3': round(hull*METRES_PER_UNIT**3/1500)},
        'cross_checks_m': {'forward hangar mouth w x h': [round(lg_wings.clearances()['forward_mouth']['clear_width']*S, 1),
                                                          round(lg_wings.clearances()['forward_mouth']['clear_height']*S, 1)],
                           'aft hangar mouth w x h': [round(lg_wings.clearances()['aft_mouth']['clear_width']*S, 1),
                                                      round(lg_wings.clearances()['aft_mouth']['clear_height']*S, 1)],
                           'transfer door w x h': [round(lg_wings.clearances()['transfer_door']['clear_width']*S, 1),
                                                   round(lg_wings.clearances()['transfer_door']['clear_height']*S, 1)],
                           'fighters (length x span) m, own scales': {k: [round(v[0]*v[3], 1), round(v[1]*v[3], 1)] for k, v in CRAFT.items()},
                           'fighters (length x span) in Legion units': {k: [round(v[0]*v[3]/S, 1), round(v[1]*v[3]/S, 1)] for k, v in CRAFT.items()},
                           'hull keel to crown': round((HI.z-LO.z)*S, 1),
                           'person 1.8 m in units': round(1.8/S, 2),
                           'door 2.1 m / rail 1.1 m / hatch 2 m in units': [round(2.1/S, 1), round(1.1/S, 1), round(2/S, 1)],
                           'C2 targets: hatch 2 m / small dome 8 m / engine bell 26 m in units':
                               [round(2/S, 1), round(8/S, 1), round(26/S, 1)]},
        'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number',
    }


if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    for ob in STUDIO:
        ob.hide_render = False
    for ob in C2_LIGHTS:
        ob.hide_render = True
    bg.inputs[0].default_value = (.74, .76, .78, 1)
    bg.inputs[1].default_value = .65
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'legion-geometry.blend'))
    manifest = {
        'status': 'Blockout: silhouette, sections, mass, slots and the four flight decks. No fine detail or textures. '
                  'Damage-free: a well-kept Hegemony patrol.',
        'hull': {
            'name': 'Legion (XIV)', 'skinHullId': 'legion_xiv', 'baseHullId': 'legion', 'size': 'CAPITAL_SHIP',
            'style': 'LOW_TECH', 'tech': 'XIV Battlegroup', 'designation': 'Battlecarrier',
            'source': {'ship': SHIP, 'skin': SKIN, 'sprite': SPRITE, 'sprite_fallback': SPRITE_PLAIN,
                       'variants': VARIANTS, 'ship_data': 'data/hulls/ship_data.csv (row id legion)'},
            'ship_data': {'min_crew': 700, 'max_crew': 1500, 'cargo': 300, 'fuel': 300, 'fighter_bays': 4},
            'built_in_mods': ['fourteenth'], 'built_in_weapons': [], 'ship_system': 'burndrive',
            'shield': 'FRONT 180', 'wings_elite': ['broadsword_wing', 'broadsword_wing', 'hoplon_wing', 'hoplon_wing'],
            'wings_strike': ['piranha_wing']*4,
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(),
        'sprite': {'size': [300, 308], 'center': [150, 149],
                   'pixel_to_model': 'col c, row r covers X 158-r..159-r, Y 149-c..150-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)]},
        'flight_decks': [lg_droop.correct(lg_wings.deck_record(s)) for s in lg_wings.LAUNCH_BAYS],
        'hangar_fit': lg_wings.fit_table(METRES_PER_UNIT),
        'weapon_slots': lg_turrets.records(),
        'engines': lg_engines.records(),
        'running_lights': lg_fittings.records(),
        'object_names': {'Hull ...': 'fore and aft body, dorsal and tail spines, engine block and housings',
                         'Bridge command dome': 'the bone-white command dome',
                         'Armour <side> ...': 'bow blade, outer belt, inner belt',
                         'Wing <side> sponson': 'the wing arms held out from the torso; the hangars hang beneath them',
                         'Flight deck <slot> ...': 'hangar pod under the wing arm, deck plate, red light strips, '
                                                   'mouth and launch-point anchors (empties)',
                         'Engine <i> ...': 'drive can, aft-facing bell, mouth lip, dark throat, exhaust anchor',
                         'Weapon <slot> ...': 'barbette (fixed) and pivot (empty) with its trainable house and barrels',
                         'Fitting <side> ...': 'masts, sensor arrays and radomes',
                         'Light <side> <where> <k>': 'running lights; positions, colours and blink in running_lights'},
        'c2': {
            'layout': 'second-wind-cinematic/patrol-abeam/c2-layout.md',
            'note': 'Model axes are C2 world axes: the patrol flies bow first along +X with starboard toward the cabin.',
            'patrol_centre_y_m': round(C2_CENTRE_Y_M, 2), 'model_origin_z_m': round(C2_ORIGIN_Z_M, 2),
            'cameras': {n: {'time_s': t, 'position_units': [round(v, 2) for v in c2_camera(t)]} for n, t in C2_TIMES.items()},
            'sun': list(c2_core.SUN), 'planetshine': c2_core.PLANETSHINE,
            'scrutiny_turret': {'slot': lg_turrets.KEYABLE, 'pivot': f'Weapon {lg_turrets.KEYABLE} pivot'},
        },
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
    }
    path = OUT/'geometry.json'
    if path.exists():  # keep the records compose_review.py wrote
        old = json.loads(path.read_text(encoding='utf-8'))
        for key in ('silhouette', 'c2_frame'):
            if key in old:
                manifest[key] = old[key]
    path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('scale', json.dumps(manifest['scale']))
    lg_wings.print_fit_table(manifest['hangar_fit'])
