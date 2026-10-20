"""Build the Falcon (P) light cruiser with Blender's Python: model, reviews, export.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_falcon.py -- --out DIR
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (81, 88), so slot and engine locations from
falcon.ship are model XY unchanged. Heights are proposed art.

One module per area: fc_hull (prongs, central body, bridge dome, waist,
drive blocks, outrigger pods, bow forks), fc_detail (pass 2 surface detail),
fc_engines (the twelve drives), fc_mounts (the eight slots the skin keeps),
fc_lights (running lights, bridge glazing); fc_kit holds the shared helpers.

Options after a literal "--":
  --out DIR      write renders, geometry.json and falcon-p.blend to DIR
                 (default build/falcon-p under the repository root; nothing the
                 build writes is tracked except geometry.json, which --manifest
                 copies back into this folder)
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, plus the close-ups bow-detail,
                 bridge-detail, turret-detail, engine-detail, stern-detail and
                 the shot checks shot-s4, shot-s6, shot-s10; 'details' = the
                 close-ups, 'shot' = all shot checks, 'none' = no renders)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --figure       stand a 1.8 m figure on the dorsal deck for review renders
  --no-detail    skip pass 2 detail (the accepted blockout)
  --no-save      skip falcon-p.blend and geometry.json
  --manifest     also copy geometry.json into this folder (the tracked copy)

The saved falcon-p.blend is the appendable cinematic asset: one collection
`FALCON (P)` holding the root empty `FALCON (P) | root`, which parents every
part; anchors live in the child collection `FALCON (P) | anchors`. Every
modifier is baked, turrets train by their `Weapon <slot> pivot`, and nothing
else (cameras, lamps, review rig, figure) is in the file. Then run
compose_review.py (Python + Pillow + numpy) for the top-view sprite overlay,
the silhouette IoU and the review set.
"""
from pathlib import Path
import argparse
import importlib
import json
import math
import re
import shutil
import sys
import bpy
from mathutils import Matrix, Quaternion, Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent.parent))  # art-source: starsector_path
import fc_kit, fc_hull, fc_detail, fc_engines, fc_mounts, fc_lights, export_falcon
for module in (fc_kit, fc_hull, fc_detail, fc_engines, fc_mounts, fc_lights, export_falcon):
    importlib.reload(module)
from starsector_path import GAME_CORE as GAME
sys.path.insert(0, str(HERE.parent))  # company-prologue: ship_scale
import ship_scale

SHIP = 'data/hulls/falcon.ship'
SKIN = 'data/hulls/skins/falcon_p.skin'
SPRITE = 'graphics/ships/falcon/falcon_p.png'
VARIANT = 'data/variants/falcon/falcon_p_Strike.variant'
NAME = 'Falcon (P)'
COLL = 'FALCON (P)'
ROOT = 'FALCON (P) | root'

# Real-world scale (pipeline pass 4), from the shared sizing basis
# (../ship_scale.py, option C): contents (180 crew, 120 cargo, 100 fuel) over
# the cruiser machinery share, against the measured enclosed volume, the union
# of ship_scale.SOLIDS['falcon-p'] (prongs, central body, bridge dome, waist
# decks; never the drive blocks, outrigger thruster pods, bow forks or tanks
# laid on the deck). The driver re-measures that union on every build and
# fails if it drifts from ship_scale.HULLS.
SCALE = ship_scale.record(NAME)
METRES_PER_UNIT = ship_scale.PUBLISHED[NAME]
SOLIDS = [n for n in ('Hull port prong', 'Hull starboard prong', 'Hull central body', 'Bridge command dome',
                      'Hull port waist deck', 'Hull starboard waist deck', 'Hull port drive block')
          if ship_scale.SOLIDS['falcon-p']['include'](n)]

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--figure', action='store_true')
parser.add_argument('--no-detail', action='store_true')
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--manifest', action='store_true')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out or HERE.parents[2]/'build'/'falcon-p').resolve()
OUT.mkdir(parents=True, exist_ok=True)


def read_json(rel):
    # Starsector's loose JSON: '#' comments and trailing commas.
    text = re.sub(r'#.*', '', (GAME/rel).read_text(encoding='utf-8'))
    return json.loads(re.sub(r',(\s*[}\]])', r'\1', text))


def check_against_install():
    """Fail if the vanilla slots no longer match the constants the model uses."""
    if not (GAME/SHIP).exists():
        return 'install not found; constants used as committed'
    ship = read_json(SHIP)
    skin = read_json(SKIN)
    slots = {s['id']: s for s in ship['weaponSlots']}
    if sorted(skin['removeWeaponSlots']) != sorted(fc_mounts.REMOVED):
        raise RuntimeError(f"falcon_p.skin removes {skin['removeWeaponSlots']}")
    if set(slots) - set(fc_mounts.REMOVED) != {s['id'] for s in fc_mounts.SLOTS}:
        raise RuntimeError('falcon.ship weapon slots changed')
    for mine in fc_mounts.SLOTS:
        s = slots[mine['id']]
        stype = skin['weaponSlotChanges'].get(mine['id'], {}).get('type', s['type'])
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], stype)
        ours = (mine['location'], mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from falcon.ship + falcon_p.skin: {theirs} vs {ours}")
    engines = [(e['location'][0], e['location'][1], e['width'], e['length'], e['angle']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in fc_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from falcon.ship: {engines}')
    if tuple(ship['center']) != (81, 88):
        raise RuntimeError(f"falcon.ship centre changed: {ship['center']}")
    variant = read_json(VARIANT)
    fit = {k: v for g in variant['weaponGroups'] for k, v in g['weapons'].items()}
    for mine in fc_mounts.SLOTS:
        if fit.get(mine['id']) != mine['weapon']:
            raise RuntimeError(f"{VARIANT} fits {fit.get(mine['id'])} in {mine['id']}")
    return f'verified against the installed {SHIP}, {SKIN} and {VARIANT}'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = fc_kit.Kit()
kit.mpu = METRES_PER_UNIT
fc_hull.build(kit)
fc_engines.build(kit)
if not args.no_detail:
    fc_detail.build(kit)
fc_mounts.build(kit)
fc_lights.build(kit)
bpy.context.view_layer.update()


# --- Edge treatment (ship-detail-guide.md): the shared bevel table before the bake. ---
def bevel_hard_edges():
    """Every hull part without an edge chamfer of its own gets an angle-limited, one-segment
    bevel sized by its part (hull plates 0.12 m, modules 0.06 m, fittings 0.03 m, small parts
    0.015 m). Turrets and hardpoints are the turret kit's; glazing, recesses and lamps stay sharp."""
    for ob in bpy.data.objects:
        if ob.type != 'MESH' or ob.name.startswith(('Weapon ', 'Turret ', 'Temporary ', 'Light ')):
            continue
        if any(m.type == 'BEVEL' for m in ob.modifiers) or (ob.name not in fc_kit.SMOOTH and any(p.use_smooth for p in ob.data.polygons[:1])):
            continue          # a chamfer of the part's own (or the turret kit's, on a kit-built part)
        mats = {m.name for m in ob.data.materials if m}
        if mats and mats <= {kit.glass.name, kit.dark.name}:
            continue          # glazing and near-black recesses: a chamfer there catches no highlight
        span = sorted(ob.dimensions)[1]*METRES_PER_UNIT
        kind = 'hull' if span > 5 else 'module' if span > 1 else 'fitting' if span > .3 else 'small'
        b = ob.modifiers.new('Edge treatment', 'BEVEL')
        b.width, b.segments, b.limit_method = fc_kit.sdk.BEVEL_M[kind]/METRES_PER_UNIT, 1, 'ANGLE'
        b.angle_limit = math.radians(55)   # every edge the check calls hard (60 deg and up); rounds' soft creases stay
        b.use_clamp_overlap = True


# --- Bake: every modifier applied, so the asset costs nothing to evaluate. ---
def bake():
    dg = bpy.context.evaluated_depsgraph_get()
    for ob in [o for o in bpy.data.objects if o.type == 'MESH' and o.modifiers]:
        ev = ob.evaluated_get(dg)
        me = bpy.data.meshes.new_from_object(ev)
        old = ob.data
        ob.modifiers.clear()
        ob.data = me
        me.name = old.name
        bpy.data.meshes.remove(old)
    for ob in bpy.data.objects:
        # Plate is flat-shaded; the hull's rounds (fc_kit.SMOOTH) and the kit-built parts keep
        # the smooth shading their builders gave them, sharp from angle so plate edges stay crisp.
        if ob.type != 'MESH':
            continue
        if ob.name in fc_kit.SMOOTH:
            fc_kit.sdk.smooth(ob)
        elif any(p.use_smooth for p in ob.data.polygons) and not ob.name.startswith(('Weapon ', 'Turret ')):
            fc_kit.sdk.smooth(ob, 32.0)


bevel_hard_edges()
bake()
leftover = [o.name for o in bpy.data.objects if o.name.startswith('Temporary ')]
assert not leftover, f'cutters left in the scene: {leftover}'


def bounds(objs):
    lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
    for ob in objs:
        for c in ob.bound_box:
            w = ob.matrix_world @ Vector(c)
            lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
    return lo, hi


meshes = [o for o in bpy.data.objects if o.type == 'MESH']
LO, HI = bounds(meshes)


def union_volume(names, step=1.0):
    """Volume of the union of the named solids: vertical ray intervals per column."""
    trees = []
    for name in names:
        ob = bpy.data.objects[name]
        verts = [ob.matrix_world @ v.co for v in ob.data.vertices]
        trees.append(BVHTree.FromPolygons(verts, [tuple(p.vertices) for p in ob.data.polygons]))
    lo, hi = bounds([bpy.data.objects[n] for n in names])
    total = 0.0
    x = lo.x+step/2
    down = Vector((0, 0, -1))
    while x < hi.x:
        y = lo.y+step/2
        while y < hi.y:
            spans = []
            for t in trees:
                hits, z = [], hi.z+1
                while True:
                    p, n, _, d = t.ray_cast(Vector((x, y, z)), down)
                    if p is None:
                        break
                    hits.append(p.z)
                    z = p.z-1e-4
                for a, b in zip(hits[0::2], hits[1::2]):
                    spans.append((b, a))
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
    measured = union_volume(SOLIDS)
    table = ship_scale.HULLS[NAME]['units3']
    print(f'enclosed volume {measured:.0f} units3 (table {table})')
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f"{NAME} units3 {table}: update the shared table")
    size = [round((b-a)*METRES_PER_UNIT, 1) for a, b in zip(LO, HI)]
    return {**SCALE, 'published_metres_per_unit': METRES_PER_UNIT,
            'measured_this_build': {'enclosed_units3': round(measured), 'solids': SOLIDS},
            'size_m': size, 'person_1_8_m_in_units': round(1.8/METRES_PER_UNIT, 2),
            'against_known_ships': {'Hound (A)': round(size[0]/61.0, 2), 'Colossus': round(size[0]/108.5, 2),
                                    'Onslaught': round(size[0]/301.0, 2)},
            'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number'}


SCALE_RECORD = scale_record()

# --- Review scene: studio lamps and cameras (never saved into the asset). ---
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = map(int, args.res.split('x'))
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.view_settings.view_transform = 'AgX'
if scene.world is None:
    scene.world = bpy.data.worlds.new('World')
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']
studio = []
for name, loc, energy, size in [('Key', (120, 140, 200), 600000, 160), ('Fill', (-100, -130, 110), 320000, 140)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    lt = bpy.context.object
    lt.name = 'Review '+name
    lt.data.energy, lt.data.shape, lt.data.size = energy, 'DISK', size
    lt.rotation_euler = (-lt.location).to_track_quat('-Z', 'Y').to_euler()
    studio.append(lt)

figure = None
if args.figure:
    # A 1.8 m person on the port prong's deck, beside the WS 005 turret.
    h = 1.8/METRES_PER_UNIT
    hit = kit.surface(-2, 30, top=80)
    z = hit[0].z if hit else 11
    figure = kit.cylinder('Review figure 1.8 m', (-2, 30, z+h/2), .22/METRES_PER_UNIT, h, kit.hazard, 'Z', 8, 0)

TARGET = Vector((23, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (23, 0, 300), 236),
    ('port-side', (23, 360, 0), 236),
    ('front', (360, 0, 0), 190),
    ('three-quarter', (230, 260, 170), 270),
    ('stern', (-360, 0, 0), 190),
]
CLOSEUPS = [  # name, position, target, ortho scale
    ('bow-detail', (190, 90, 70), (100, 10, 0), 70),
    ('bridge-detail', (70, 50, 60), (2, 0, 10), 52),
    ('turret-detail', (60, 85, 60), (24, 32, 8), 52),
    ('engine-detail', (-150, 90, 60), (-70, 25, 0), 72),
    ('stern-detail', (-170, -110, 110), (-40, 0, 0), 130),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
for name, pos, target, sc in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type, cam.data.ortho_scale, cam.data.clip_end = 'ORTHO', sc, 2000
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, sc in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type, cam.data.ortho_scale, cam.data.clip_end = 'ORTHO', sc, 2000
    cam.rotation_euler = (0, 0, math.pi) if name == 'top' else \
        (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()

# Shot checks: the Asharu battle blockout's key frames (asharu-battle/battle_blockout.py,
# world metres co-moving with the convoy), the Falcon placed as that scene places it
# (heading, pitch, roll about its origin) and its camera and low sun carried into hull
# units through the inverse of that placement. Black sky, sun only, no fill.
SUN_EL, SUN_AZ = 16.0, -40.0
SHOTS = {  # name: (camera loc, target, lens mm, Falcon placement x, y, z, heading, pitch, roll)
    'shot-s4': ((-4800, -2600, -1700), (-6500, 200, -3100), 35, (-6200, 250, -2900, 4, 14, -3)),
    'shot-s6': ((1350, 750, 380), (-1400, -350, -260), 30, (-3100, -700, -650, 6, 4, -3)),
    'shot-s10': ((-900, 380, 420), (420, -60, -560), 35, (-560, 120, 120, 25, -6, 8)),
}


def frame_of(x, y, z, heading, pitch, roll):
    q = (Quaternion((0, 0, 1), math.radians(heading)) @ Quaternion((0, 1, 0), -math.radians(pitch))
         @ Quaternion((1, 0, 0), math.radians(roll)))
    return Matrix.Translation(Vector((x, y, z))) @ q.to_matrix().to_4x4() @ Matrix.Scale(METRES_PER_UNIT, 4)


sun_world = Vector((math.cos(math.radians(SUN_EL))*math.cos(math.radians(SUN_AZ)),
                    math.cos(math.radians(SUN_EL))*math.sin(math.radians(SUN_AZ)),
                    math.sin(math.radians(SUN_EL))))
shot_suns = {}
for name, (loc, target, lens, place) in SHOTS.items():
    inv = frame_of(*place).inverted()
    p = inv @ Vector(loc)
    d = (inv.to_3x3() @ (Vector(target)-Vector(loc))).normalized()
    bpy.ops.object.camera_add(location=p)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = lens, 36, 'HORIZONTAL'
    dist = (p-Vector((23, 0, 0))).length
    cam.data.clip_start, cam.data.clip_end = max(1, dist*.3), dist*3
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    shot_suns[name] = (inv.to_3x3() @ sun_world).normalized()
bpy.ops.object.light_add(type='SUN')
sun = bpy.context.object
sun.name = 'Review Asharu sun'
sun.data.energy = 4.0

wanted = []
for v in [v for v in args.views.split(',') if v and v != 'none']:
    wanted += [c[0] for c in CLOSEUPS[:5]] if v == 'details' else list(SHOTS) if v == 'shot' else [v]
wanted += [spec.split(':')[0] for spec in args.cam]
for name in wanted:
    shot = name.startswith('shot-')
    scene.camera = bpy.data.objects['Camera '+name]
    sun.hide_render = not shot
    if shot:
        sun.rotation_euler = shot_suns[name].to_track_quat('Z', 'Y').to_euler()
    for lt in studio:
        lt.hide_render = shot
    bg.inputs[0].default_value = (0, 0, 0, 1) if shot else (.74, .76, .78, 1)
    bg.inputs[1].default_value = 0 if shot else .65
    scene.render.film_transparent = name == 'top' or shot
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

if figure is not None:
    bpy.data.objects.remove(figure, do_unlink=True)

if not args.no_save:
    manifest_path = OUT/'geometry.json'
    old = json.loads((HERE/'geometry.json').read_text(encoding='utf-8')) if (HERE/'geometry.json').exists() else {}
    meshes = [o for o in bpy.data.objects if o.type == 'MESH']
    manifest = {
        'status': 'Pass 1 blockout + pass 2 detail + pass 4 scale. Built for the Asharu battle: '
                  '3-5 km in S4-S6, about 500 m over its shoulder in S10.',
        'hull': {
            'name': NAME, 'hullId': 'falcon_p', 'baseHullId': 'falcon', 'size': 'CRUISER', 'style': 'MIDLINE',
            'tech': 'Pirate', 'designation': 'Light Cruiser',
            'livery': 'pirate (falcon_p.png): weathered khaki armour, red slash panels, hazard-yellow drives',
            'source': {'ship': SHIP, 'skin': SKIN, 'sprite': SPRITE, 'variant': VARIANT,
                       'ship_data': 'data/hulls/ship_data.csv (row id falcon)',
                       'lights': 'none: falcon has no lights layer'},
            'built_in_mods': ['augmentedengines', 'unstable_injector'], 'ship_system': 'maneuveringjets',
            'shield': 'FRONT 270 deg (not modelled)', 'crew': [100, 180], 'cargo': 120, 'fuel': 100,
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': SCALE_RECORD,
        'sprite': {'size': [162, 222], 'center': [81, 88],
                   'pixel_to_model': 'col c, row r (from the top) covers X 134-r..135-r, Y 80-c..81-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)]},
        'weapon_slots': fc_mounts.records(),
        'turrets': [r for r in fc_mounts.records() if r['mount'] == 'TURRET'],
        'engines': fc_engines.records(),
        'running_lights': fc_lights.records(),
        'asset': {'file': 'falcon-p.blend', 'collection': COLL, 'root': ROOT,
                  'anchors_collection': f'{COLL} | anchors',
                  'baked': 'every modifier applied; no live booleans or bevels',
                  'append': f"bpy.data.libraries.load(path, link=True) and take collection '{COLL}'; "
                            "instance it with a collection-instance empty scaled by metres_per_unit",
                  'triangles': sum(len(p.vertices)-2 for o in meshes for p in o.data.polygons),
                  'objects': len(meshes), 'materials': sorted({m.name for o in meshes for m in o.data.materials})},
        'object_names': {'Hull ...': 'prongs, central body, waist decks and tanks, drive blocks, outrigger pods, '
                                     'bow fork jaws, and their pass 2 detail',
                         'Bridge ...': 'command dome and glazing',
                         'Engine <i> ...': 'drive can, aft-facing bell, mouth lip, dark throat, hazard bands, '
                                           'exhaust anchor, drive glow anchor',
                         'Weapon <slot id> ...': 'turret barbette (fixed) and the trainable turret under '
                                                 '`Weapon <slot> pivot`; hardpoint launchers; anchors and '
                                                 '`Weapon <slot> muzzle anchor <k>`',
                         'Light <side> <where> <k>': 'running lights; positions, colours and blink in running_lights'},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
        'shot_checks': [{'name': n, 'camera_m': list(c), 'target_m': list(t), 'lens_mm': lens, 'sensor_mm': 36,
                         'falcon_placement': list(pl), 'sun_el_az_deg': [SUN_EL, SUN_AZ],
                         'distance_m': round((Vector(c)-Vector(pl[:3])).length)}
                        for n, (c, t, lens, pl) in SHOTS.items()],
    }
    for key in ('silhouette', 'shot_extent_px'):
        if key in old:
            manifest[key] = old[key]
    manifest_path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    if args.manifest:
        shutil.copy(manifest_path, HERE/'geometry.json')

    # The review scene (cameras, studio lamps) is the surfacing harness's input; then the
    # appendable asset (export_falcon.py): one collection, one root, nothing but the ship.
    sun.hide_render = True                 # leave the review scene in its studio setup
    for lt in studio:
        lt.hide_render = False
    bg.inputs[0].default_value, bg.inputs[1].default_value = (.74, .76, .78, 1), .65
    scene.render.film_transparent = False
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'falcon-p-geometry.blend'), compress=True)
    import export_falcon
    export_falcon.make_asset(OUT/'falcon-p.blend', METRES_PER_UNIT)
    print('saved', OUT/'falcon-p.blend')
