"""Rebuild the Mudskipper geometry blockout with Blender's Python.

Run: blender --background --factory-startup --python build_reference.py
Optional, after a literal "--":
  --out DIR      write renders (and the .blend/geometry.json unless
                 --no-save) to DIR instead of renders/ and this folder
  --views a,b    render only these cameras; default the five standard views
                 (top, port-side, bow, port-quarter, stern-quarter). Close-ups
                 render only when named: bow-detail, bridge-detail,
                 turret-detail, waist-detail, stern-detail, engine-detail
  --samples N    Cycles samples (default 32)
  --no-save      skip the .blend and geometry.json
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho camera (repeatable)
Then: python compose_review.py [--renders DIR]  (sprite overlay, IoU, sheet)

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

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import ms_kit, ms_hull, ms_bridge, ms_deck, ms_engines, ms_turrets
for module in (ms_kit, ms_hull, ms_bridge, ms_deck, ms_engines, ms_turrets):
    importlib.reload(module)

parser = argparse.ArgumentParser()
parser.add_argument('--views', default='top,port-side,bow,port-quarter,stern-quarter')
parser.add_argument('--samples', type=int, default=32)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--out')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
# Pass 4: the hull's one real-world scale. Every metric value (VFX sizes and
# speeds, lamp falloff, cameras, depth of field, flight speeds, texel density)
# derives from it; nothing hard-codes metres. Derivation in scale_check.py.
METRES_PER_UNIT = 0.85
RENDERS = Path(args.out).resolve() if args.out else HERE/'renders'
SAVE_DIR = Path(args.out).resolve() if args.out else HERE
RENDERS.mkdir(parents=True, exist_ok=True)

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = ms_kit.Kit()
ms_hull.build(kit)
ms_bridge.build(kit)
ms_deck.build(kit)
ms_engines.build(kit)
ms_turrets.build(kit)

scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.74,.76,.78,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = .65
scene.view_settings.view_transform = 'AgX'
for name, loc, energy, size in [('Key',(60,70,120),70000,90),('Fill',(-40,-60,70),40000,80)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z','Y').to_euler()

# (name, position, target, orthographic scale). The top camera is fixed at
# 16 px per unit with the bow to the left and port below, the mapping
# compose_review.py uses to lay the sprite over it.
TOP_CENTRE = (3, 0)
TOP_SCALE = 120
CAMERAS = [('top', (TOP_CENTRE[0],TOP_CENTRE[1],100), None, TOP_SCALE),
           ('port-side', (3,150,1), (3,0,1), 100),
           ('bow', (150,0,1), (3,0,1), 100),
           ('port-quarter', (95,110,75), (3,0,0), 118),
           ('stern-quarter', (-100,85,60), (-3,0,0), 118),
           # Close-ups, rendered only when named in --views.
           ('bow-detail', (90,55,45), (32,0,4), 54),
           ('bridge-detail', (70,40,48), (32,0,9), 44),
           ('turret-detail', (12,42,38), (-9,0,10), 32),
           ('waist-detail', (20,95,30), (8,20,0), 42),
           ('stern-detail', (-95,60,40), (-28,0,2), 60),
           ('engine-detail', (-60,-75,55), (-26,-12,2), 64)]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CAMERAS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
for name, pos, target, scale in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.rotation_euler = (0,0,math.pi) if target is None else (Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler()
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 1000
for name in args.views.split(','):
    scene.camera = bpy.data.objects['Camera '+name]
    # The top view renders on transparent film so its alpha is the silhouette.
    scene.render.film_transparent = name == 'top'
    scene.render.filepath = str(RENDERS/(name+'.png'))
    bpy.ops.render.render(write_still=True)
scene.render.film_transparent = False

if not args.no_save:
    scene.camera = bpy.data.objects['Camera port-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(SAVE_DIR/'mudskipper-geometry.blend'))
    bpy.context.view_layer.update()
    lo = Vector((1e9,)*3); hi = Vector((-1e9,)*3)
    for ob in bpy.data.objects:
        if ob.type == 'MESH':
            for c in ob.bound_box:
                w = ob.matrix_world @ Vector(c)
                lo = Vector(map(min, lo, w)); hi = Vector(map(max, hi, w))
    manifest = {
        'status': 'Pass 2 (plausible detailing) awaiting owner review (pass 3). No damage states or textures.',
        'passes': {'1 blockout': 'accepted; snapshot in reference-history/v1-blockout',
                   '2 plausible detailing': 'built; awaiting review (pass 3)',
                   '4 real-world scale': 'metres_per_unit published'},
        'hull': {'hullId':'mudskipper','hullName':'Mudskipper','hullSize':'FRIGATE','style':'LOW_TECH',
                 'designation':'Civilian Transport','builtInMods':['civgrade'],'builtInWeapons':[],
                 'system':'maneuveringjets',
                 'source':'starsector-core/data/hulls/mudskipper.ship, ship_data.csv',
                 'sprite':'starsector-core/graphics/ships/mudskipper/mudskipper.png (64x84, centre 32,39 from bottom-left)',
                 'skins':[{'skinHullId':'mudskipper2','hullName':'Mudskipper Mk.II',
                           'sprite':'graphics/ships/mudskipper/mudskipper2.png',
                           'changes':'removes WS 003; WS 002 becomes a LARGE BALLISTIC HARDPOINT; built-ins ill_advised, comp_storage'}],
                 'no_variant_hulls':'No (A), (D) or other hull variants exist; Mk.II is a skin, not modelled here.'},
        'axes': {'+X':'bow','+Y':'port','+Z':'dorsal'},
        'units': 'Vanilla hull units (1 sprite px = 1 unit); multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': {
            'method': 'Deck program volume against measured pressure-hull volume (Second Wind method; scale_check.py)',
            'deck_program': 'DeckSizing.programFor(FRIGATE, LINER from "Civilian Transport", crew 5-100, cargo 70): '
                            '3,329 m2 of rooms incl. three 16x10 boat bays for the 95 carried; '
                            'x1.40 circulation x 3.2 m deck height = 14,914 m3',
            'main_hull_units3': 44608,
            'usable_share': .55,
            'derived': 0.847,
            'range': {'low': 0.76, 'high': 0.91,
                      'note': '0.76 if sponsons, hood and cockpit also count as pressure space at 65% usable; '
                              '0.91 for the main hull alone at 45% usable'},
            'cross_checks': ['cargo bay door opening 16.8 x 8.4 units = 14.3 x 7.1 m against the 16 x 10 m boat bay in the program',
                             'crew hatch 1.35 x 2.75 units = 1.15 x 2.3 m; a 1.8 m person is 2.1 units',
                             'keel to deck about 17.5 units = 14.9 m: four 3.2 m decks plus structure'],
            'not_the_battle_map_scale': 'landed-ships-true-scale.md sizes landed ships for tactical-map readability; a different number',
        },
        'dimensions': {'min':[round(v,2) for v in lo],'max':[round(v,2) for v in hi],
                       'size':[round(h-l,2) for l,h in zip(lo,hi)],
                       'size_metres':[round((h-l)*METRES_PER_UNIT,1) for l,h in zip(lo,hi)],
                       'game_bounds_polygon':[[-33,28],[34,23],[41,0],[35,-22],[-33,-28]],
                       'collisionRadius':71},
        'decks': [{'x0':a,'x1':b,'z':z} for a,b,z in ms_hull.DECKS],
        'weapon_slots': ms_turrets.SLOTS,
        'engines': [{'object_prefix':f'Engine {i}','location':[x,y],'z':(2 if i >= 4 else 0),'angle':a,'width':w,
                     'bell_mouth_radius':round(ms_engines.bell_radius(i,w),2),
                     'exhaust_direction':[round(math.cos(math.radians(a)),3),round(math.sin(math.radians(a)),3),0],
                     'role':['main drive','port pod drive','starboard pod drive','port maneuvering jet','starboard maneuvering jet'][i-1]}
                    for i,(x,y,a,w) in enumerate(ms_engines.ENGINES,1)],
        'cameras': [{'name':n,'position':list(p),'target':list(t) if t else None,'orthographic_scale':s,
                     'resolution':[1920,1080]} for n,p,t,s in CAMERAS],
        'top_view_mapping': {'pixels_per_unit':1920/TOP_SCALE,'centre':list(TOP_CENTRE),'bow':'left','port':'down'},
        'mesh_authority': 'All views render the same objects; later surface art is subordinate to this geometry.'
    }
    (SAVE_DIR/'geometry.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
