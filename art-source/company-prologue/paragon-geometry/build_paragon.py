"""Build the Paragon, the Tri-Tachyon battleship, as a cinematic model with Blender's Python.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_paragon.py -- [options]
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is one
unit about the .ship centre (164, 190 from the bottom), so slot, engine and
shield locations from paragon.ship are model XY unchanged. Heights are
proposed art.

One module per area so later passes do not collide: pg_hull (ring, prow, core,
aft pods, bore lining), pg_engines (the ten drive units), pg_turrets (21 turrets
and two hardpoints), pg_shield (the shield emitter anchor and the emitter array
on the bore lining); pg_kit holds the helpers and pg_outline the plan read off
the sprite (trace_sprite.py).

Options after a literal "--":
  --out DIR      write renders, .blend and geometry.json to DIR (default: here;
                 renders and the .blend are ignored)
  --views a,b    render only these cameras: top, top-classes (the part-class
                 render gauntlet.py scores), starboard-side, port-side, front,
                 stern, three-quarter, belly; 'details' names the close-ups
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip the .blend and geometry.json
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
sys.path.insert(0, str(HERE.parent))  # company-prologue: ship_scale
import ship_scale
import pg_kit, pg_outline, pg_hull, pg_finish, pg_detail, pg_bore, pg_bridge, pg_belly, pg_service, pg_hangar, pg_engines, pg_turrets, pg_shield, pg_arcs
MODULES = [pg_kit, pg_outline, pg_hull, pg_finish, pg_detail, pg_bore, pg_bridge, pg_belly, pg_service, pg_hangar, pg_engines,
           pg_turrets, pg_shield, pg_arcs]
for module in MODULES:
    importlib.reload(module)

# The read-only install's core folder: $STARSECTOR_CORE if set, else the platform
# default (macOS keeps it inside the app bundle).
GAME = Path(os.environ.get('STARSECTOR_CORE') or (
    '/Applications/Starsector.app/Contents/Resources/Java' if sys.platform == 'darwin'
    else r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core'))
SHIP = 'data/hulls/paragon.ship'
SPRITE = 'graphics/ships/paragon.png'
VARIANTS = ['data/variants/paragon/paragon_Elite.variant', 'data/variants/paragon/paragon_Escort.variant',
            'data/variants/paragon/paragon_Raider.variant']
NAME = 'Paragon'

# Real-world scale (pipeline pass 4), from the shared sizing basis
# (../ship_scale.py, option C): contents (1,000 crew, 300 cargo, 300 fuel) over
# the capital machinery share, against the measured enclosed volume, the union of
# ship_scale.SOLIDS['paragon'] (belt, main ring, prow, bow module, horns and lobes,
# command deck, aft body and pods; never the open bore, the shield emitter lining,
# the deck gantry, the drive pods, turrets, reactor domes or the emitter array). The driver
# re-measures that union on every build and fails if it drifts from
# ship_scale.HULLS, so a shape change is carried into the shared table.
# The published value is an owner override (2026-10-18): "It should be larger than
# the Onslaught" (301 m). The rule solves 0.748 (271 m) after the hull was slimmed to
# the Legion's and Onslaught's fullness; both numbers go into geometry.json.
OVERRIDE = 'owner: larger than the Onslaught (2026-10-18)'
if NAME in ship_scale.PUBLISHED:
    SCALE = ship_scale.record(NAME)
    METRES_PER_UNIT = ship_scale.PUBLISHED[NAME]
    if not OVERRIDE:
        assert abs(SCALE['solved_metres_per_unit']-METRES_PER_UNIT) < .01, (SCALE['solved_metres_per_unit'], METRES_PER_UNIT)
else:
    SCALE = None
    METRES_PER_UNIT = 1.0  # unscaled blockout: pass 4 publishes the real value
parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,top-classes,starboard-side,port-side,front,stern,three-quarter,belly')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--cam', action='append', default=[])
parser.add_argument('--hangar-open', action='store_true', help='pose the hangar door open (review renders)')
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
    for sid, loc, angle, arc, size, typ, mount in pg_turrets.SLOTS:
        s = slots[sid]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['size'], s['type'], s['mount'])
        if theirs != (loc, angle, arc, size, typ, mount):
            raise RuntimeError(f'{sid} differs from paragon.ship: {theirs} vs {(loc, angle, arc, size, typ, mount)}')
    if len(slots) != len(pg_turrets.SLOTS):
        raise RuntimeError('paragon.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['contrailSize'], e['length'], e['angle'])
               for e in ship['engineSlots']]
    if engines != [tuple(e) for e in pg_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from paragon.ship: {engines}')
    if (tuple(ship['shieldCenter']), ship['shieldRadius']) != (pg_shield.SHIELD_CENTER, pg_shield.SHIELD_RADIUS):
        raise RuntimeError('paragon.ship shield centre or radius changed')
    if tuple(ship['center']) != (164, 190):
        raise RuntimeError('paragon.ship centre changed')
    return 'verified against the installed paragon.ship'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = pg_kit.Kit()
kit.mpu = METRES_PER_UNIT
pg_finish.apply(kit)
hull = pg_hull.build(kit)
pg_detail.build(kit, hull)
pg_bore.build(kit)
pg_bridge.build(kit, hull)
pg_belly.build(kit, hull)
pg_service.build(kit)
pg_hangar.build(kit, args.hangar_open)
pg_engines.build(kit)
pg_turrets.build(kit)
pg_shield.build(kit, METRES_PER_UNIT)
pg_finish.assign_sizes(kit)
# Firing lines: report each turret's clear share of its arc (pg_arcs).
ARCS = pg_arcs.check(kit)
for row in ARCS:
    print(f"ARC {row['id']} {row['size']:6} arc {row['arc_deg']:3} clear {row['clear_share']:.0%} "
          f"blocked by {', '.join(row['blocked_by']) or '-'}")

if os.environ.get('PG_ARC_DEBUG'):
    for row in ARCS:
        if row['id'] in os.environ['PG_ARC_DEBUG'].split(','):
            for b in row['blocked']:
                print('  ARCDBG', row['id'], b)

for name, d in pg_kit.INSET_FAULTS:
    print(f'INSET FAULT: {name} folds over at inset {d:.1f}')

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


def bounds(names=None, guns=True):
    lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in scene.objects:
        if ob.type == 'MESH' and (names is None or ob.name in names) and (guns or not ob.name.startswith('Weapon ')):
            for c in ob.evaluated_get(dg).bound_box:
                w = ob.matrix_world @ Vector(c)
                lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
    return lo, hi


# The hull's own extent: the bow autopulse barrels run 35 units past the prow (as the
# game draws them), and a ship's length is its hull's, not its guns'.
LO, HI = bounds(guns=False)
LO_ALL, HI_ALL = bounds()
S = METRES_PER_UNIT
TARGET = Vector((-8, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (-9, 0, 300), 620),
    ('starboard-side', (-8, -600, 0), 420),
    ('port-side', (-8, 600, 0), 420),
    ('front', (600, 0, 0), 380),
    ('three-quarter', (430, -460, 320), 560),
    ('stern', (-600, 0, 0), 380),
    ('belly', (330, -380, -300), 560),
]
CLOSEUPS = [
    ('bow-detail', (330, -170, 150), (140, 0, 5), 130),
    ('bore-detail', (150, -220, 210), (-5, 0, 0), 190),
    ('core-detail', (60, -200, 170), (-90, 0, 15), 150),
    ('turret-detail', (140, -160, 140), (3, -99, 10), 80),
    ('engine-detail', (-330, -260, 90), (-165, -95, 0), 140),
    ('hangar-detail', (-20, -25, 20), (55, 0, 2), 50),
    ('stern-detail', (-420, -120, 70), (-170, 0, 0), 300),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))

wanted = []
for v in [v for v in args.views.split(',') if v]:
    if v == 'details':
        wanted += [c[0] for c in CLOSEUPS[:7]]
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

# Part classes for the shape gauntlet (gauntlet.py): each object's class comes
# from its material, the way the sprite's tones do: skin (blue-grey armour),
# white (command deck, bow module, reactor domes), dark (lining, wells,
# machinery). Faces steeper than ~57 deg render dark too, as the sprite shades them.
CLASS_OF = {'Tri-Tachyon blue-grey skin': 1, 'Tri-Tachyon pale skin': 1, 'Tri-Tachyon blue glow': 1,
            'Tri-Tachyon substrate': 1, 'Tri-Tachyon hatch ceramic': 1,
            'Tri-Tachyon white core': 2, 'Tri-Tachyon white glow': 2}


def class_material():
    m = bpy.data.materials.new('Gauntlet classes')
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    em = nt.nodes.new('ShaderNodeEmission')
    attr = nt.nodes.new('ShaderNodeAttribute')
    attr.attribute_type, attr.attribute_name = 'OBJECT', 'pg_class'
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(geo.outputs['Normal'], sep.inputs[0])
    def math(op, a, b, c=None):
        n = nt.nodes.new('ShaderNodeMath')
        n.operation = op
        for i, v in enumerate((a, b, c)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                nt.links.new(v, n.inputs[i])
        return n.outputs[0]
    is_white = math('COMPARE', attr.outputs['Fac'], 2.0, .5)
    is_dark = math('MAXIMUM', math('COMPARE', attr.outputs['Fac'], 3.0, .5), math('LESS_THAN', sep.outputs['Z'], .55))
    mix1 = nt.nodes.new('ShaderNodeMix')
    mix1.data_type = 'RGBA'
    nt.links.new(is_white, mix1.inputs['Factor'])
    mix1.inputs['A'].default_value = (0, 0, 1, 1)
    mix1.inputs['B'].default_value = (1, 1, 1, 1)
    mix2 = nt.nodes.new('ShaderNodeMix')
    mix2.data_type = 'RGBA'
    nt.links.new(is_dark, mix2.inputs['Factor'])
    nt.links.new(mix1.outputs['Result'], mix2.inputs['A'])
    mix2.inputs['B'].default_value = (1, 0, 0, 1)
    nt.links.new(mix2.outputs['Result'], em.inputs['Color'])
    nt.links.new(em.outputs[0], out.inputs['Surface'])
    return m


def render_classes(path):
    for ob in scene.objects:
        if ob.type == 'MESH':
            mats = [s.material.name for s in ob.material_slots if s.material]
            ob['pg_class'] = CLASS_OF.get(mats[0], 3) if mats else 3
    vl = bpy.context.view_layer
    vl.material_override = class_material()
    keep = (scene.view_settings.view_transform, scene.cycles.samples, scene.cycles.use_denoising)
    scene.view_settings.view_transform = 'Standard'
    scene.cycles.samples, scene.cycles.use_denoising = 4, False
    scene.camera = bpy.data.objects['Camera top']
    scene.render.film_transparent = True
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    vl.material_override = None
    scene.view_settings.view_transform, scene.cycles.samples, scene.cycles.use_denoising = keep


for name in wanted:
    if name == 'top-classes':
        render_classes(OUT/'top-classes.png')
        continue
    scene.camera = bpy.data.objects['Camera '+name]
    bg.inputs[0].default_value = (.74, .76, .78, 1)
    bg.inputs[1].default_value = .4
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


SOLIDS = ('Hull belt', 'Hull main ring', 'Hull prow', 'Hull bow module', 'Hull port bow horn',
          'Hull starboard bow horn', 'Hull port bow lobe', 'Hull starboard bow lobe', 'Hull aft body', 'Hull command deck',
          'Hull command deck upper', 'Hull command spine', 'Bridge tower', 'Hull port pod', 'Hull starboard pod',
          'Hull forward hangar')


def scale_record():
    measured = union_volume(list(SOLIDS))
    size = [round((b-a)*S, 1) for a, b in zip(LO, HI)]
    base = {'measured_this_build': {'enclosed_units3': round(measured), 'solids': list(SOLIDS)},
            'length_units': round(HI.x-LO.x, 1)}
    if SCALE is None:
        return {**base, 'status': 'unscaled blockout (metres_per_unit 1.0 is a placeholder until pass 4)'}
    table = ship_scale.HULLS[NAME]['units3']
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f'{NAME} units3 {table}: update the shared table')
    return {**SCALE, 'published_metres_per_unit': METRES_PER_UNIT, 'override': OVERRIDE,
            'length_m_published': round((HI.x-LO.x)*S, 1), **base, 'size_m': size,
            'person_1_8_m_in_units': round(1.8/S, 2),
            'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number'}


if not args.no_save:
    scene.render.film_transparent = False
    scene.camera = bpy.data.objects['Camera three-quarter']
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'paragon-geometry.blend'))
    manifest = {
        'status': 'Pass 1 blockout: smooth ring hull round an open bore, prow spine, command core, two aft pods, '
                  'bore lining as the shield emitter ring, 21 turrets on pivots, two bow hardpoints, ten drive '
                  'units, shield emitter anchor. Tri-Tachyon base materials.',
        'hull': {
            'name': 'Paragon', 'hullId': 'paragon', 'size': 'CAPITAL_SHIP', 'style': 'HIGH_TECH',
            'tech': 'High Tech (Tri-Tachyon)', 'designation': 'Battleship',
            'source': {'ship': SHIP, 'sprite': SPRITE, 'variants': VARIANTS,
                       'ship_data': 'data/hulls/ship_data.csv (row id paragon)'},
            'ship_data': {'min_crew': 400, 'max_crew': 1000, 'cargo': 300, 'fuel': 300, 'fighter_bays': 0,
                          'armor': 1500, 'hitpoints': 18000, 'mass': 3500},
            'built_in_mods': ['advancedcore'], 'ship_system': 'fortressshield', 'shield': 'OMNI 360',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(),
        'sprite': {'size': [330, 364], 'center': [164, 190],
                   'pixel_to_model': 'col c, row r covers X 173-r..174-r, Y 163-c..164-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)],
                       'note': 'hull only; with_guns includes the bow hardpoint barrels past the prow',
                       'with_guns': {'min': [round(v, 2) for v in LO_ALL], 'max': [round(v, 2) for v in HI_ALL]}},
        'bore': {'centre': list(pg_hull.BORE_C), 'radius': pg_hull.BORE_R, 'lining_radius': pg_hull.LINING_R},
        'shield': dict(pg_shield.RECORDS),
        'hangar': dict(pg_hangar.RECORD),
        'service_fields': list(pg_service.RECORDS),
        'finish': {'armour': 'hex ablative ceramic (owner pick, study option B)', 'plate_m': pg_finish.HEX_M,
                   'default_plate_m': pg_finish.DEFAULT_M, 'steep_face_factor': pg_finish.STEEP_FACTOR},
        'weapon_slots': pg_turrets.records(),
        'firing_lines': {'method': 'pg_arcs: rays across each slot arc at the barrel line (level and 1.5 deg '
                                   'depressed) from a mount sitting low in its dimpled well',
                         'barrel_line_above_rim': pg_arcs.BARREL_H,
                         'clear_share': {r['id']: r['clear_share'] for r in ARCS},
                         'still_blocked_by': {r['id']: r['blocked_by'] for r in ARCS if r['blocked_by']}},
        'engines': pg_engines.records(),
        'object_names': {'Hull ...': 'belt, main ring, bore lining, prow (grille block with the capital-mount bays), '
                                     'bow module, port/starboard bow horn, command deck (+ upper), core trench '
                                     '(the deck gantry), aft body, port/starboard pod, reactor well and dome',
                         'Bridge ...': 'tower and glazing on the command deck',
                         'Engine <i> ...': 'drive pod, aft-facing bell, mouth lip, dark throat; '
                                           'exhaust anchor (empty, +X along the exhaust)',
                         'Weapon <slot> ...': 'turrets: barbette (fixed) and pivot (empty) parenting the turret '
                                              'kit gun (house parts, elevation, muzzle anchor <k>); hardpoints: hardpoint anchor, housing, '
                                              'emitter and muzzle',
                         'Shield emitter ...': 'the shield anchor at shieldCenter, emitter nodes on the bore lining'},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
        'closeups': [{'name': n, 'position': list(p), 'target': list(t), 'orthographic_scale': s}
                     for n, p, t, s in CLOSEUPS],
    }
    path = OUT/'geometry.json'
    if path.exists():  # keep the records compose_review.py wrote
        old = json.loads(path.read_text(encoding='utf-8'))
        for key in ('silhouette',):
            if key in old:
                manifest[key] = old[key]
    path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('scale', json.dumps(manifest['scale']))
