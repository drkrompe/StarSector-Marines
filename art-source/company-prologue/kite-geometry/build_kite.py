"""Rebuild the Kite (P) geometry with Blender's Python.

Run (from this folder): sh ../../blender.sh --background --factory-startup --python build_kite.py -- [args]
Optional, after a literal "--":
  --out DIR        write renders (and the .blend/geometry.json unless
                   --no-save) to DIR instead of renders/ and this folder
  --views a,b      render only these cameras; default the standard views
                   (top, port-side, bow, stern, port-quarter, stern-quarter,
                   underside). Close-ups render only when named: bow-detail,
                   cockpit-detail, sponson-detail, turret-detail,
                   stern-detail, underside-detail
  --samples N      Cycles samples (default 32)
  --no-save        skip the .blend and geometry.json
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho camera (repeatable)
  --figure         add a 1.8 m scale figure on the dorsal spine (renders only)
  --state NAME     condition state; only 'clean' exists (no damage states needed)
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
sys.path.insert(0, str(HERE.parent))
import ship_scale  # noqa: E402
import kp_kit, kp_hull, kp_cabin, kp_engines, kp_weapons
MODULES = (kp_kit, kp_hull, kp_cabin, kp_engines, kp_weapons)
for module in MODULES:
    importlib.reload(module)

parser = argparse.ArgumentParser()
parser.add_argument('--views', default='top,port-side,bow,stern,port-quarter,stern-quarter,underside')
parser.add_argument('--samples', type=int, default=32)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--out')
parser.add_argument('--cam', action='append', default=[])
parser.add_argument('--figure', action='store_true')
parser.add_argument('--state', default='clean', choices=('clean',))
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])

# Pass 4: the hull's one real-world scale. Every metric value (VFX sizes and
# speeds, lamp falloff, cameras, depth of field, flight speeds, texel density)
# derives from it; nothing hard-codes metres. Solved by ship_scale.py option C
# from the measured enclosed volume (SOLIDS['kite']): 0.804, published 0.80.
METRES_PER_UNIT = 0.80
STATUS = 'Passes 1-4: blockout, detail, scale.'
PASSES = {'1 blockout': 'built; v1-blockout renders regenerate from commit 0af310a38',
          '2 plausible detailing': 'built: hull and cabin here, engines and weapons by one Opus agent each',
          '4 real-world scale': 'metres_per_unit 0.80 from ship_scale.py option C (solves 0.804, range 0.779-0.828)'}
RENDERS = Path(args.out).resolve() if args.out else HERE/'renders'
SAVE_DIR = Path(args.out).resolve() if args.out else HERE
RENDERS.mkdir(parents=True, exist_ok=True)

# Enclosed volume under ship_scale.SOLIDS' definition: the pressure hull with
# its cabin. The drive cans, sponsons and launchers are excluded.
HABITABLE = ('Hull fuselage', 'Hull wing body', 'Hull dorsal spine', 'Hull aft hump')


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
kit = kp_kit.Kit()


def build_module(module):
    # Tag what each module adds, so a clash sweep can tell modules apart.
    before = set(bpy.data.objects)
    module.build(kit)
    for ob in set(bpy.data.objects) - before:
        ob['module'] = module.__name__


for module in (kp_hull, kp_cabin):
    build_module(module)
# Measured before the drives cut their bores, so drive cans stay out of it.
envelope_u3 = union_volume(HABITABLE)
for module in (kp_engines, kp_weapons):
    build_module(module)
print('VOLUME', round(envelope_u3))

if args.figure:
    skin = kp_kit.material('Scale figure', (.85, .75, .2), 0, .6)
    u = 1/METRES_PER_UNIT
    fx, fy = 10.0, 0.0
    fz = kp_hull.SPINE_DECK
    kp_kit.bx(kit, 'Scale figure legs', fx-.16*u, fx+.16*u, fy-.21*u, fy+.21*u, fz, fz+.94*u, skin, .05*u)
    kp_kit.bx(kit, 'Scale figure torso', fx-.18*u, fx+.18*u, fy-.28*u, fy+.28*u, fz+.94*u, fz+1.66*u, skin, .06*u)
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
for name, loc, energy, size in [('Key', (80, 90, 150), 110000, 110), ('Fill', (-50, -80, 90), 60000, 100)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()

# (name, position, target, orthographic scale). The top camera is fixed at
# 12 px per unit with the bow to the left and port below, the mapping
# compose_review.py uses to lay the sprite over it.
TOP_CENTRE = (5, 0)
TOP_SCALE = 160
CAMERAS = [('top', (TOP_CENTRE[0], TOP_CENTRE[1], 100), None, TOP_SCALE),
           ('port-side', (5, 200, 0), (5, 0, 0), 90),
           ('bow', (200, 0, 0), (5, 0, 0), 100),
           ('stern', (-200, 0, 0), (5, 0, 0), 100),
           ('port-quarter', (120, 130, 85), (4, 0, -1), 125),
           ('stern-quarter', (-120, 110, 75), (-4, 0, -1), 125),
           ('underside', (50, 85, -95), (4, 0, -3), 125),
           # Close-ups, rendered only when named in --views.
           ('bow-detail', (95, 55, 45), (26, 0, 2), 42),
           ('cockpit-detail', (75, 30, 55), (24, 0, 6), 30),
           ('sponson-detail', (55, 70, 45), (8, 24, 1), 30),
           ('turret-detail', (25, 55, 55), (-7, 0, 8), 26),
           ('stern-detail', (-100, 65, 35), (-22, 0, -1), 60),
           ('underside-detail', (50, 65, -60), (0, 0, -5), 60)]
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

if not args.no_save and not args.figure:
    scene.camera = bpy.data.objects['Camera port-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(SAVE_DIR/'kite-geometry.blend'))
    bpy.context.view_layer.update()
    lo = Vector((1e9,)*3); hi = Vector((-1e9,)*3)
    for ob in bpy.data.objects:
        if ob.type == 'MESH' and not ob.name.startswith('Scale figure'):
            for v in ob.data.vertices:      # vertices, not bound_box: a joined object's box can be stale
                w = ob.matrix_world @ v.co
                lo = Vector(map(min, lo, w)); hi = Vector(map(max, hi, w))
    gj = SAVE_DIR/'geometry.json'
    old = json.loads(gj.read_text(encoding='utf-8')) if gj.exists() else {}
    manifest = {
        'status': STATUS,
        'passes': PASSES,
        'hull': {'hullId': 'kite', 'skinHullId': 'kite_pirates', 'hullName': 'Kite (P)', 'hullSize': 'FRIGATE',
                 'style': 'MIDLINE', 'tech': 'Pirate', 'designation': 'Shuttle', 'builtInMods': ['civgrade'],
                 'system': 'maneuveringjets', 'crew': [2, 30], 'cargo': 30, 'fuel': 15,
                 'variant': 'kite_pirates_Raider (WS 001 hammer, WS 002 swarmer, WS 003 lightdualmg)',
                 'source': 'starsector-core/data/hulls/kite.ship, skins/kite_pirates.skin, ship_data.csv',
                 'sprite': 'starsector-core/graphics/ships/aeroshuttle/aeroshuttle_pirates.png '
                           '(82x66, centre 41,28 from bottom-left)',
                 'lights': 'none: the aeroshuttle sprites have no lights layer',
                 'cinematic_role': 'Asharu battle: the two pirate boarding shuttles that carry the boarders to '
                                   'the Colossus (S4 distant, S7, S9 at 350-550 m)'},
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'Vanilla hull units (1 sprite px = 1 unit); multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': ship_scale.record('Kite (P)'),
        'person_1_8m_units': round(1.8/METRES_PER_UNIT, 2),
        'enclosed_volume_units3': round(envelope_u3),
        'enclosed_volume_objects': list(HABITABLE),
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(h - l, 2) for l, h in zip(lo, hi)],
                       'size_metres': [round((h - l)*METRES_PER_UNIT, 2) for l, h in zip(lo, hi)],
                       'game_bounds_polygon': [[37, 0], [-12, 40], [-26, 0], [-11, -39]],
                       'collisionRadius': 64},
        'decks': [{'x0': a, 'x1': b, 'z': z} for a, b, z in kp_hull.DECKS],
        'weapon_slots': kp_weapons.SLOTS,
        'engines': [{'object_prefix': f'Engine {i}', 'location': [x, y], 'z': kp_engines.z_of(i), 'angle': a,
                     'width': w, 'style': 'MIDLINE',
                     'bell_mouth_radius': round(kp_engines.bell_radius(i, w), 2),
                     'exhaust_direction': [round(math.cos(math.radians(a)), 3), round(math.sin(math.radians(a)), 3), 0],
                     'anchor': f'Engine {i} exhaust anchor', 'role': kp_engines.ROLES[i-1]}
                    for i, (x, y, a, w) in enumerate(kp_engines.ENGINES, 1)],
        'condition_states': {'built': ['clean'], 'note': 'no damage states needed (brief)'},
        'cameras': [{'name': n, 'position': list(p), 'target': list(t) if t else None, 'orthographic_scale': s,
                     'resolution': [1920, 1080]} for n, p, t, s in CAMERAS],
        'top_view_mapping': {'pixels_per_unit': 1920/TOP_SCALE, 'centre': list(TOP_CENTRE), 'bow': 'left', 'port': 'down'},
        'mesh_authority': 'All views render the same objects; later surface art is subordinate to this geometry.',
    }
    for keep in ('silhouette',):
        if keep in old:
            manifest[keep] = old[keep]
    gj.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
