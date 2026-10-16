"""Build the Hound (A) cinematic blockout with Blender's Python.

Run from this folder:
  blender --background --factory-startup --python build_hound.py
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (31, 27), so slot and engine locations from
hound.ship are model XY unchanged. Heights are proposed art.

One module per area so later passes do not collide: hd_hull (cargo body,
deckhouse, spar, stern skids), hd_engines (the two drive pods), hd_bridge
(port bow capsule and cage), hd_fork (starboard bow gun fork), hd_mounts
(the three weapon slots); hd_kit holds the shared helpers.

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, plus the close-ups bow-detail,
                 bridge-detail, fork-detail, turret-detail, engine-detail,
                 stern-detail; 'details' names all close-ups)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
                 (repeatable) and render it as NAME.png
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
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
import bmesh
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import hd_kit, hd_hull, hd_engines, hd_bridge, hd_fork, hd_mounts
for module in (hd_kit, hd_hull, hd_engines, hd_bridge, hd_fork, hd_mounts):
    importlib.reload(module)

GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SHIP = 'data/hulls/hound.ship'
SKIN = 'data/hulls/skins/hound_hegemony.skin'
SPRITE = 'graphics/ships/hound/hound_hegemony.png'

# Real-world scale (pipeline pass 4). One number; every metric value -
# VFX sizes and speeds, lamp falloff, cameras, depth of field, flight speeds,
# texel density - reads it. Derived from what the ship carries, not from the
# sprite: the mod's deck program for a FRIGATE "Combat Freighter" with 10-40
# crew and 75 cargo (DeckProgramProbe) is 2,427 m2 of rooms, x1.40
# circulation = 3,398 m2, x3.2 m deck height = 10,873 m3. The pressure
# hull (body, deckhouse, spar, bridge capsule and the drive pods' casings,
# measured below on every build) is about 28,960 cubic units; 55% usable
# gives 0.88 m per unit. Plausible range 0.83 (65% usable) to 1.07 (pods
# hold no crew space). Fittings agree: the bridge airlock hatch is
# 1.7 x 2.1 m, viewports about 0.9 x 1.0 m, the stern cargo doors
# 9.7 x 4.8 m, the body 9.7 m keel to deck (three 3.2 m decks).
METRES_PER_UNIT = 0.88
REQUIRED_M3 = 10873
USABLE = .55
PRESSURE_HULL = ['Hull cargo body', 'Hull shielded hold deckhouse', 'Hull engine spar',
                 'Bridge capsule hull', 'Bridge capsule nose'] + [
    f'Engine {i} {side} {part}' for i, side in ((1, 'port'), (2, 'starboard'))
    for part in ('intake cap', 'forward casing', 'armour collar', 'aft casing', 'thrust housing')]

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern')
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
    for mine in hd_mounts.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (mine['location'], mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from hound.ship: {theirs} vs {ours}")
    if len(slots) != len(hd_mounts.SLOTS):
        raise RuntimeError('hound.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in hd_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from hound.ship: {engines}')
    return 'verified against the installed hound.ship'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = hd_kit.Kit()
hd_hull.build(kit)
hd_fork.build(kit)
hd_bridge.build(kit)
hd_engines.build(kit)
hd_mounts.build(kit)

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
for name, loc, energy, size in [('Key', (50, 60, 90), 120000, 70), ('Fill', (-40, -55, 50), 70000, 60)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.energy = energy
    obj.data.shape = 'DISK'
    obj.data.size = size
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()

# Every view observes the same objects; only the active camera changes.
TARGET = Vector((9, 0, .5))
CAMERAS = [  # name, position, orthographic scale
    ('top', (9, 0, 120), 125),
    ('port-side', (9, 150, .5), 92),
    ('front', (150, 0, .5), 82),
    ('three-quarter', (95, 110, 70), 112),
    ('stern', (-150, 0, .5), 82),
]
# Close-ups, rendered only when named: (name, position, target, ortho scale).
CLOSEUPS = [
    ('bow-detail', (80, 45, 40), (30, 0, 2), 52),
    ('bridge-detail', (62, 58, 34), (30, 9, 2), 28),
    ('fork-detail', (78, -36, 40), (34, -7, 2), 30),
    ('turret-detail', (24, 40, 40), (-3, 3, 7), 22),
    ('engine-detail', (14, 82, 34), (-5, 22, 0), 46),
    ('stern-detail', (-78, 42, 34), (-20, 0, 0), 46),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
wanted = []
for v in [v for v in args.views.split(',') if v]:
    wanted += [c[0] for c in CLOSEUPS[:6]] if v == 'details' else [v]
wanted += [spec.split(':')[0] for spec in args.cam]
for name, pos, target, scale in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
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

if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'hound-geometry.blend'))
    lo = Vector((1e9,)*3)
    hi = Vector((-1e9,)*3)
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in scene.objects:
        if ob.type == 'MESH':
            for c in ob.evaluated_get(dg).bound_box:
                w = ob.matrix_world @ Vector(c)
                lo = Vector(map(min, lo, w))
                hi = Vector(map(max, hi, w))
    def scale_record(lo, hi):
        volume = 0.0
        for name in PRESSURE_HULL:
            bm = bmesh.new()
            ob = bpy.data.objects[name]
            bm.from_object(ob, dg)
            bm.transform(ob.matrix_world)
            volume += abs(bm.calc_volume())
            bm.free()
        solve = lambda usable: (REQUIRED_M3/(usable*volume))**(1/3)
        return {
            'method': 'deck program (DeckProgramProbe FRIGATE "Combat Freighter" 10 40 75): 2427 m2 rooms '
                      'x1.40 circulation x3.2 m deck height = required m3; solved against usable pressure-hull volume',
            'required_m3': REQUIRED_M3, 'pressure_hull_units3': round(volume), 'usable_fraction': USABLE,
            'solved_metres_per_unit': round(solve(USABLE), 3),
            'range': {'cramped_65pct_usable': round(solve(.65), 2), 'pods_not_crewed': 1.07},
            'size_m': [round((b-a)*METRES_PER_UNIT, 1) for a, b in zip(lo, hi)],
            'cross_checks_m': {'bridge airlock hatch': [1.7, 2.1], 'bridge viewport glass': [0.9, 1.0],
                               'stern cargo doors': [9.7, 4.8], 'body keel to deck': 9.7, 'person 1.8 m in units': round(1.8/METRES_PER_UNIT, 2)},
            'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number',
        }

    manifest = {
        'status': 'Blockout: silhouette, sections, mass and slots. No fine detail, damage or textures.',
        'hull': {
            'name': 'Hound (A)', 'skinHullId': 'hound_hegemony', 'baseHullId': 'hound',
            'size': 'FRIGATE', 'style': 'LOW_TECH', 'tech': 'Hegemony',
            'designation': 'Combat Freighter (Hegemony auxiliary list)',
            'source': {'ship': SHIP, 'skin': SKIN, 'sprite': SPRITE, 'ship_data': 'data/hulls/ship_data.csv (row id hound)'},
            'built_in_mods': ['rugged', 'shielded_holds', 'armoredweapons'],
            'built_in_weapons': [], 'ship_system': 'flarelauncher (via hidden SYSTEM slot WS 003)',
            'shield': 'NONE',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(lo, hi),
        'sprite': {'size': [62, 72], 'center': [31, 27],
                   'pixel_to_model': 'col c, row r covers X 44-r..45-r, Y 30-c..31-c'},
        'dimensions': {'min': [round(v, 2) for v in lo], 'max': [round(v, 2) for v in hi],
                       'size': [round(b-a, 2) for a, b in zip(lo, hi)]},
        'weapon_slots': [dict(s, location=[*s['location'], round(hd_mounts.ANCHOR_Z[s['id']], 2)],
                              objects=f"Weapon {s['id']} ...", anchor=f"Weapon {s['id']} anchor")
                         for s in hd_mounts.SLOTS],
        'engines': [{'index': i, 'objects': f'Engine {i} ...', 'anchor': f"Engine {i} {'port' if y > 0 else 'starboard'} exhaust anchor",
                     'location': [x, y, 0], 'width': w, 'length': 80, 'style': 'LOW_TECH', 'contrailSize': 64,
                     'angle': 180, 'bell_radius': round(hd_engines.BELL_R, 2)}
                    for i, (x, y, w) in enumerate(hd_engines.ENGINES, 1)],
        'object_names': {'Hull ...': 'cargo body, shielded-hold deckhouse, engine spar, stern skids',
                         'Fork ...': 'starboard bow gun fork around WS 001',
                         'Bridge ...': 'port bow bridge capsule and crash cage',
                         'Engine <i> <side> ...': 'drive pod sections, engine can and bell',
                         'Weapon <slot id> ...': 'mount geometry; anchors point along the slot angle'},
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
