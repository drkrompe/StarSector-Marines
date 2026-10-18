"""Rebuild the Hermes geometry with Blender's Python.

Run: blender --background --factory-startup --python build_hermes.py
Optional, after a literal "--":
  --out DIR        write renders (and the .blend/geometry.json unless
                   --no-save) to DIR instead of renders/ and this folder
  --views a,b      render only these cameras; default the standard views
                   (top, port-side, bow, stern, port-quarter, stern-quarter,
                   underside). Close-ups render only when named: bow-detail,
                   cockpit-detail, turret-detail, aft-turret-detail,
                   stern-detail, underside-detail
  --samples N      Cycles samples (default 32)
  --no-save        skip the .blend and geometry.json
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho camera (repeatable)
  --gear up|down   pose the landing gear (default down; the .blend saves down)
  --doors-open DEG swing both boarding doors open by DEG ('full' = their open angle)
  --figure         add a 1.8 m scale figure at the port boarding door (renders only)
  --state NAME     condition state; only 'clean' exists yet. 'wrecked' and
                   'patched' are reserved (see README, Condition states)
Then: python compose_review.py  (sprite overlay, IoU, sheets)

Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one unit is one
pixel of the vanilla sprite about the .ship centre. XY of the planform,
weapon slots and engine slots come from the game; Z is art interpretation.
Every view renders the same objects at 1920x1080; only the camera changes.
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
import hm_kit, hm_hull, hm_cockpit, hm_engines, hm_turrets, hm_ventral
MODULES = (hm_kit, hm_hull, hm_cockpit, hm_engines, hm_turrets, hm_ventral)
for module in MODULES:
    importlib.reload(module)

parser = argparse.ArgumentParser()
parser.add_argument('--views', default='top,port-side,bow,stern,port-quarter,stern-quarter,underside')
parser.add_argument('--samples', type=int, default=32)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--out')
parser.add_argument('--cam', action='append', default=[])
parser.add_argument('--gear', choices=('up', 'down'), default='down')
parser.add_argument('--doors-open', default='0')
parser.add_argument('--figure', action='store_true')
parser.add_argument('--state', default='clean', choices=('clean',))
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])

# Pass 4: the hull's one real-world scale. Every metric value (VFX sizes and
# speeds, lamp falloff, cameras, depth of field, flight speeds, texel density)
# derives from it; nothing hard-codes metres. Derivation: scale_record below
# and README (Scale).
METRES_PER_UNIT = 0.11
STATUS = 'Passes 1-4 done; pass 3 accepted by the owner. Clean state only; no damage states or textures.'
PASSES = {'1 blockout': 'built; v1-blockout renders regenerate from commit 624cd3ea8',
          '2 plausible detailing': 'built: one Opus agent per module (hull, cockpit, engines, turrets, ventral)',
          '3 owner review': 'stubby legs and a flat stance; ventral ramp replaced by side boarding doors; spine fairing '
                            'and gun sponsons reshaped from the sprite; pass-2 detail refitted',
          '4 real-world scale': 'metres_per_unit published; derivation in scale_record'}
RENDERS = Path(args.out).resolve() if args.out else HERE/'renders'
SAVE_DIR = Path(args.out).resolve() if args.out else HERE
RENDERS.mkdir(parents=True, exist_ok=True)

# The boat's own program (one cell a metre, as the mod's deck program): what
# the Hermes carries in this mod, not the vanilla frigate's 10 crew / 50 cargo.
# ShuttleType.HERMES: one fire team, capacity 4 (Squad.FIRE_TEAM_SIZE); the
# Boat Deck: "four seats at standard fit", no upgrade path.
CREWED_PROGRAM_M3 = {
    'flight deck, two seats abreast (2.0 x 2.0 m, 1.6 m)': 6.4,
    'troop bay, four armoured seats on facing benches (2.0 x 2.4 m, 1.9 m)': 9.1,
    'kit and casualty-litter stowage at the bay head': 1.2,
    'passage flight deck to bay (0.8 x 1.2 m, 1.9 m)': 1.8,
}
CREWED_SHARE = {'cramped': .25, 'nominal': .20, 'roomy': .15}
HABITABLE = ('Main hull - spine', 'Wing body', 'Aft drive block')


def union_volume(names, step=.5):
    """Volume of the union of closed solids, by vertical ray intervals per column."""
    dg = bpy.context.evaluated_depsgraph_get()
    trees, lo, hi = [], Vector((1e9,)*3), Vector((-1e9,)*3)
    for n in names:
        ob = bpy.data.objects[n].evaluated_get(dg)
        me = ob.to_mesh()
        vs = [ob.matrix_world @ v.co for v in me.vertices]
        trees.append(BVHTree.FromPolygons(vs, [p.vertices[:] for p in me.polygons]))
        for v in vs:
            lo = Vector(map(min, lo, v)); hi = Vector(map(max, hi, v))
        ob.to_mesh_clear()
    total = 0.0
    x = lo.x + step/2
    while x < hi.x:
        y = lo.y + step/2
        while y < hi.y:
            spans = []
            for t in trees:
                zs, z = [], hi.z + 1
                while True:
                    p, _, _, _ = t.ray_cast(Vector((x, y, z)), Vector((0, 0, -1)))
                    if p is None:
                        break
                    zs.append(p.z)
                    z = p.z - 1e-4
                spans += [(zs[i+1], zs[i]) for i in range(0, len(zs) - 1, 2)]
            spans.sort()
            cur = None
            for a, b in spans:
                if cur and a <= cur[1]:
                    cur[1] = max(cur[1], b)
                else:
                    if cur:
                        total += cur[1] - cur[0]
                    cur = [a, b]
            if cur:
                total += cur[1] - cur[0]
            y += step
        x += step
    return total*step*step


bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = hm_kit.Kit()


def build_module(module):
    # Tag what each module adds, so check_clashes.py can tell modules apart.
    before = set(bpy.data.objects)
    module.build(kit)
    for ob in set(bpy.data.objects) - before:
        ob['module'] = module.__name__


build_module(hm_hull)
# Measured before the underside is cut, so wells do not count against it.
envelope_u3 = union_volume(HABITABLE)
for module in (hm_cockpit, hm_engines, hm_turrets, hm_ventral):
    build_module(module)

required = sum(CREWED_PROGRAM_M3.values())
solve = lambda share: (required/(share*envelope_u3))**(1/3)
x0, x1 = hm_ventral.DOOR_X
scale_record = {
    'published': METRES_PER_UNIT,
    'crewed_program_m3': CREWED_PROGRAM_M3,
    'crewed_required_m3': round(required, 1),
    'envelope_union_units3': round(envelope_u3),
    'envelope_objects': list(HABITABLE),
    'solved_by_share': {k: round(solve(v), 3) for k, v in CREWED_SHARE.items()},
    'section_check': {
        'troop_bay_clear_height_units': round((hm_hull.SPINE_DECK - 1.5) - (hm_hull.KEEL + 1.5), 1),
        'troop_bay_clear_width_units': 2*(11 - 1.0),
        'note': 'spine interior less 1.5 u floor/roof structure and 1 u walls; '
                '1.9 m headroom and 2.0 m between benches set the scale from the section',
    },
    'boarding_door_clear_m': [round((x1 - x0)*METRES_PER_UNIT, 2),
                              round((hm_ventral.DOOR_HEAD - hm_ventral.DOOR_SILL)*METRES_PER_UNIT, 2)],
    'boarding_sill_above_ground_m': round((hm_ventral.DOOR_SILL - hm_ventral.GROUND)*METRES_PER_UNIT, 2),
    'person_1_8m_units': round(1.8/METRES_PER_UNIT, 1),
}
sec = scale_record['section_check']
sec['scale_from_headroom'] = round(1.9/sec['troop_bay_clear_height_units'], 3)
sec['scale_from_width'] = round(2.0/sec['troop_bay_clear_width_units'], 3)
print('SCALE ' + json.dumps(scale_record))

if args.gear == 'up':
    for name, _, _ in hm_ventral.GEAR:
        e = bpy.data.objects[f'Gear {name} hinge']
        e.rotation_euler[1] = math.radians(e['retract_angle_deg'])
doors_deg = 0.0
for side in ('port', 'starboard'):
    e = bpy.data.objects[f'Hull {side} boarding door hinge']
    doors_deg = e['open_angle_deg'] if args.doors_open == 'full' else float(args.doors_open)
    e.rotation_euler[2] = e['open_sign']*math.radians(doors_deg)
if args.figure:
    skin = hm_kit.material('Scale figure', (.85, .75, .2), 0, .6)
    u = 1/METRES_PER_UNIT
    fx, fy, fz = 6.5, 32.0, hm_ventral.GROUND   # on the ground outside the port boarding door
    hm_kit.bx(kit, 'Scale figure legs', fx-.16*u, fx+.16*u, fy-.21*u, fy+.21*u, fz, fz+.94*u, skin, .05*u)
    hm_kit.bx(kit, 'Scale figure torso', fx-.18*u, fx+.18*u, fy-.28*u, fy+.28*u, fz+.94*u, fz+1.66*u, skin, .06*u)
    kit.cylinder('Scale figure head', (fx, fy, fz+1.8*u-.15*u), .13*u, .3*u, skin, 'Z', 12, .04*u)

scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.74, .76, .78, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = .65
scene.view_settings.view_transform = 'AgX'
for name, loc, energy, size in [('Key', (60, 70, 120), 70000, 90), ('Fill', (-40, -60, 70), 40000, 80)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()

# (name, position, target, orthographic scale). The top camera is fixed at
# 16 px per unit with the bow to the left and port below, the mapping
# compose_review.py uses to lay the sprite over it.
TOP_CENTRE = (1, 0)
TOP_SCALE = 120
CAMERAS = [('top', (TOP_CENTRE[0], TOP_CENTRE[1], 100), None, TOP_SCALE),
           ('port-side', (1, 150, -2), (1, 0, -2), 100),
           ('bow', (150, 0, -2), (1, 0, -2), 70),
           ('stern', (-150, 0, -2), (1, 0, -2), 70),
           ('port-quarter', (95, 110, 75), (1, 0, -3), 110),
           ('stern-quarter', (-100, 85, 60), (-3, 0, -3), 110),
           ('underside', (40, 70, -80), (1, 0, -8), 110),
           # Close-ups, rendered only when named in --views.
           ('bow-detail', (85, 50, 40), (32, 0, 0), 40),
           ('cockpit-detail', (70, 30, 50), (36, 0, 4), 26),
           ('turret-detail', (40, 60, 45), (12, 18, 7), 26),
           ('aft-turret-detail', (-10, 50, 50), (-18, 0, 11), 26),
           ('stern-detail', (-90, 55, 30), (-32, 0, 0), 50),
           ('underside-detail', (45, 55, -45), (10, 0, -12), 50)]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CAMERAS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
for name, pos, target, scale in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera ' + name
    cam.rotation_euler = (0, 0, math.pi) if target is None else (Vector(target) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 1000
for name in [v for v in args.views.split(',') if v] + [c.split(':')[0] for c in args.cam]:
    scene.camera = bpy.data.objects['Camera ' + name]
    # The top view renders on transparent film so its alpha is the silhouette.
    scene.render.film_transparent = name == 'top'
    scene.render.filepath = str(RENDERS/(name + '.png'))
    bpy.ops.render.render(write_still=True)
scene.render.film_transparent = False

if not args.no_save and not args.figure and args.gear == 'down' and doors_deg == 0:
    scene.camera = bpy.data.objects['Camera port-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(SAVE_DIR/'hermes-geometry.blend'))
    bpy.context.view_layer.update()
    lo = Vector((1e9,)*3); hi = Vector((-1e9,)*3)
    for ob in bpy.data.objects:
        if ob.type == 'MESH':
            for c in ob.bound_box:
                w = ob.matrix_world @ Vector(c)
                lo = Vector(map(min, lo, w)); hi = Vector(map(max, hi, w))
    gj = SAVE_DIR/'geometry.json'
    old = json.loads(gj.read_text(encoding='utf-8')) if gj.exists() else {}
    manifest = {
        'status': STATUS,
        'passes': PASSES,
        'hull': {'hullId': 'hermes', 'hullName': 'Hermes', 'hullSize': 'FRIGATE', 'style': 'MIDLINE',
                 'designation': 'Shuttle', 'builtInMods': ['civgrade'], 'builtInWeapons': [],
                 'system': 'maneuveringjets', 'crew': [2, 10], 'cargo': 50, 'fuel': 15,
                 'source': 'starsector-core/data/hulls/hermes.ship, ship_data.csv',
                 'sprite': 'starsector-core/graphics/ships/hermes/hermes_base.png (56x82, centre 28,39 from bottom-left)',
                 'lights': 'starsector-core/graphics/ships/hermes/hermes_lights.png',
                 'mod_role': 'ShuttleType.HERMES: one fire team, 4 seats, 1 hardpoint in battle; the gig of '
                             'ShipsBoats and the Second Wind\'s original wrecked boats (Boat Deck: patched courier, '
                             'four seats at standard fit, no upgrade path)'},
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'Vanilla hull units (1 sprite px = 1 unit); multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale_record': scale_record,
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(h - l, 2) for l, h in zip(lo, hi)],
                       'size_metres': [round((h - l)*METRES_PER_UNIT, 2) for l, h in zip(lo, hi)],
                       'ground_z_gear_down': hm_ventral.GROUND,
                       'game_bounds_polygon': [[42, 5], [21, 10], [14, 24], [-22, 17], [-37, 16],
                                               [-37, -15], [-21, -18], [14, -25], [21, -10], [42, -4]],
                       'collisionRadius': 60},
        'decks': [{'x0': a, 'x1': b, 'z': z} for a, b, z in hm_hull.DECKS],
        'weapon_slots': hm_turrets.SLOTS,
        'engines': [{'object_prefix': f'Engine {i}', 'location': [x, y], 'z': hm_engines.z_of(i), 'angle': a, 'width': w,
                     'bell_mouth_radius': round(hm_engines.bell_radius(i, w), 2),
                     'exhaust_direction': [round(math.cos(math.radians(a)), 3), round(math.sin(math.radians(a)), 3), 0],
                     'anchor': f'Engine {i} exhaust anchor', 'role': hm_engines.ROLES[i-1]}
                    for i, (x, y, a, w) in enumerate(hm_engines.ENGINES, 1)],
        'moving_parts': {
            'boarding_doors': [{'pivot': f'Hull {side} boarding door hinge', 'axis': 'Z', 'open_sign': s,
                                'open_angle_deg': 100.0, 'built': 'closed',
                                'opening_units': [x1 - x0, hm_ventral.DOOR_HEAD - hm_ventral.DOOR_SILL]}
                               for s, side in ((1, 'port'), (-1, 'starboard'))],
            'gear': [{'pivot': f'Gear {n} hinge', 'axis': 'Y', 'retract_angle_deg': a, 'built': 'extended'}
                     for n, _, a in hm_ventral.GEAR],
            # Every pivot empty in the build with its custom properties
            # (boarding doors, gear doors and the rest), so nothing that moves
            # is missing from the manifest.
            'pivots': {ob.name: {k: (v if isinstance(v, (int, float, str)) else str(v)) for k, v in ob.items() if k != 'module'}
                       for ob in sorted(bpy.data.objects, key=lambda o: o.name)
                       if ob.type == 'EMPTY' and ob.name.endswith('hinge')},
        },
        'condition_states': {
            'built': ['clean'],
            'reserved': ['wrecked', 'patched'],
            'scar_prefix': 'Scar H<nn>',
            'contract': 'second-wind-pipeline.md (Condition states): damage adds Scar objects and never renames, '
                        'replaces or deletes base objects; each state builds with --state',
        },
        'cameras': [{'name': n, 'position': list(p), 'target': list(t) if t else None, 'orthographic_scale': s,
                     'resolution': [1920, 1080]} for n, p, t, s in CAMERAS],
        'top_view_mapping': {'pixels_per_unit': 1920/TOP_SCALE, 'centre': list(TOP_CENTRE), 'bow': 'left', 'port': 'down'},
        'mesh_authority': 'All views render the same objects; later surface art is subordinate to this geometry.',
    }
    for keep in ('silhouette', 'second_wind_bay_fit'):
        if keep in old:
            manifest[keep] = old[keep]
    gj.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
