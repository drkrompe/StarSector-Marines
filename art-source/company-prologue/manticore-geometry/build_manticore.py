"""Build the Manticore (P) destroyer with Blender's Python: model, reviews, export.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_manticore.py -- --out DIR
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (82, 67), so slot and engine locations from
manticore.ship are model XY unchanged. Heights are proposed art.

One module per area: mt_hull (bow head, neck, core, wings, aft body, turret
pods, launcher housings, command dome), mt_engines (the five drives),
mt_mounts (the ten slots: turrets and hardpoints from the shared turret kit,
system anchors), mt_lights (running lights); mt_kit holds the shared helpers.

Options after a literal "--":
  --out DIR      write renders, geometry.json and manticore.blend to DIR
                 (default build/manticore under the repository root; nothing
                 the build writes is tracked except geometry.json, which
                 --manifest copies back into this folder)
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, plus the close-ups bow-detail,
                 turret-detail, engine-detail, stern-detail and the shot
                 checks shot-s5-1500m, shot-s4-4000m; 'details' = the
                 close-ups, 'shot' = the shot checks, 'none' = no renders)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip manticore.blend and geometry.json
  --manifest     also copy geometry.json into this folder (the tracked copy)

The saved manticore.blend is the appendable cinematic asset: one collection
`MANTICORE` holding the root empty `MANTICORE | root`, which parents every
part; anchors live in the child collection `MANTICORE | anchors`. Every
modifier is baked (no live booleans or bevels), turrets train by their
`Weapon <slot> pivot`, and nothing else (cameras, lamps, review rig) is in
the file. Then run compose_review.py (Python + Pillow + numpy) for the
top-view sprite overlay, the silhouette IoU and the review set.
"""
from pathlib import Path
import argparse
import re
import importlib
import json
import math
import shutil
import sys
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent.parent))  # art-source: starsector_path
import mt_kit, mt_hull, mt_detail, mt_engines, mt_mounts, mt_lights
for module in (mt_kit, mt_hull, mt_detail, mt_engines, mt_mounts, mt_lights):
    importlib.reload(module)
from starsector_path import GAME_CORE as GAME
sys.path.insert(0, str(HERE.parent))  # company-prologue: ship_scale
import ship_scale

SHIP = 'data/hulls/manticore.ship'
SKIN = 'data/hulls/skins/manticore_pirates.skin'
SPRITE = 'graphics/ships/manticore/manticore_pirate.png'
COLL = 'MANTICORE'
ROOT = 'MANTICORE | root'
NAME = 'Manticore (P)'

# Real-world scale (pipeline pass 4), from the shared sizing basis
# (../ship_scale.py, owner's option C): contents (80 crew, 80 cargo, 50 fuel)
# over the destroyer machinery share, against the measured enclosed volume,
# the union of ship_scale.SOLIDS['manticore'] (never the drive cans, turret
# pods, launcher housings or weapons). Until the hull is in ship_scale.HULLS
# the build uses the battle roster's plan-area estimate (about 96 m).
STATUS = ('Review round 2: deep faceted lofted hull, glacis neck, main gun sunk in a well, raised command tier, '
          'drooping arms; pass 6 light pirate livery + pass 4 scale (0.54 m/u, ship_scale.py option C) + pass 2 '
          'detail on the pass 1 slots, drives, turret-kit weapons, anchors and running lights.')
SOLIDS = ['Hull bow head', 'Hull neck', 'Hull core', 'Hull wing body', 'Hull aft body', 'Hull ventral keel',
          'Bridge command tier', 'Bridge command dome']
if NAME in ship_scale.HULLS:
    SCALE = ship_scale.record(NAME)
    METRES_PER_UNIT = ship_scale.PUBLISHED[NAME]
    assert abs(SCALE['solved_metres_per_unit']-METRES_PER_UNIT) < .01, (SCALE['solved_metres_per_unit'], METRES_PER_UNIT)
    SOLIDS = [n for n in SOLIDS if ship_scale.SOLIDS['manticore']['include'](n)]
else:
    SCALE = {'method': 'provisional: asharu-battle/roster.py option C plan-area estimate (96 m)'}
    METRES_PER_UNIT = .62

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--manifest', action='store_true')
parser.add_argument('--cam', action='append', default=[])
parser.add_argument('--ahead', action='store_true', help='train every mount dead ahead for the review renders')
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out or HERE.parents[2]/'build'/'manticore').resolve()
OUT.mkdir(parents=True, exist_ok=True)


def read_ship():
    return json.loads(re.sub(r'#.*', '', (GAME/SHIP).read_text(encoding='utf-8')))


def check_against_install():
    """Fail if the vanilla slots no longer match the constants the model uses."""
    if not (GAME/SHIP).exists():
        return 'install not found; constants used as committed'
    ship = read_ship()
    slots = {s['id']: s for s in ship['weaponSlots']}
    for mine in mt_mounts.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (mine['location'], mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from manticore.ship: {theirs} vs {ours}")
    if len(slots) != len(mt_mounts.SLOTS):
        raise RuntimeError('manticore.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['length']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in mt_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from manticore.ship: {engines}')
    if tuple(ship['center']) != (82, 67):
        raise RuntimeError(f"manticore.ship centre changed: {ship['center']}")
    skin = json.loads(re.sub(r',(\s*[}\]])', r'\1', re.sub(r'#.*', '', (GAME/SKIN).read_text(encoding='utf-8'))))
    if skin['spriteName'] != SPRITE or skin['removeWeaponSlots'] or skin['removeEngineSlots']:
        raise RuntimeError(f'manticore_pirates.skin changed: {skin}')
    return f'verified against the installed {SHIP} and {SKIN}'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = mt_kit.Kit()
kit.mpu = METRES_PER_UNIT
mt_hull.build(kit)
mt_detail.build(kit)
mt_engines.build(kit)
mt_mounts.build(kit)
mt_lights.build(kit)
bpy.context.view_layer.update()


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
        if ob.type == 'MESH':
            for p in ob.data.polygons:
                p.use_smooth = False


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


def union_volume(names, step=.5):
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
    size = [round((b-a)*METRES_PER_UNIT, 1) for a, b in zip(LO, HI)]
    out = {**SCALE, 'published_metres_per_unit': METRES_PER_UNIT,
           'measured_this_build': {'enclosed_units3': round(measured), 'solids': SOLIDS},
           'size_m': size, 'person_1_8_m_in_units': round(1.8/METRES_PER_UNIT, 2),
           'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number'}
    if NAME in ship_scale.HULLS:
        table = ship_scale.HULLS[NAME]['units3']
        if abs(measured/table-1) > .02:
            raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                               f"{NAME} units3 {table}: update the shared table")
    return out


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
for name, loc, energy, size in [('Key', (100, 120, 170), 430000, 135), ('Fill', (-85, -110, 95), 230000, 120)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    lt = bpy.context.object
    lt.name = 'Review '+name
    lt.data.energy, lt.data.shape, lt.data.size = energy, 'DISK', size
    lt.rotation_euler = (-lt.location).to_track_quat('-Z', 'Y').to_euler()
    studio.append(lt)

TARGET = Vector((10, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (10, 0, 220), 290),
    ('port-side', (10, 260, 0), 172),
    ('front', (260, 0, 0), 176),
    ('three-quarter', (170, 200, 130), 215),
    ('stern', (-260, 0, 0), 176),
]
CLOSEUPS = [  # name, position, target, ortho scale
    ('bow-detail', (150, 80, 70), (64, 0, 6), 70),
    ('turret-detail', (75, 70, 65), (21, 0, 12), 62),
    ('engine-detail', (-135, 70, 45), (-58, 8, 0), 72),
    ('stern-detail', (-140, -95, 70), (-38, 0, 2), 112),
    ('wing-detail', (70, 125, 60), (24, 54, 4), 64),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
for name, pos, target, sc in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type, cam.data.ortho_scale, cam.data.clip_end = 'ORTHO', sc, 1000
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, sc in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type, cam.data.ortho_scale, cam.data.clip_end = 'ORTHO', sc, 1000
    cam.rotation_euler = (0, 0, math.pi) if name == 'top' else \
        (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()

# Shot checks for the Asharu battle (asharu-battle-shot.md): S5's 85 mm along
# the line of fire, the Manticore 1.5 km off, bow on and a little from
# starboard; S4 / S6's distant flank view at 4 km on a 35 mm. 36 mm sensor,
# 1920x1080, black sky, no fill. The sun is the battle's (battle_blockout.py
# SUN_EL 16, SUN_AZ -40) with the hull heading along world +X: low, ahead and
# to starboard, so the hull is side-lit, never a backlit silhouette.
# Perspective is scale-free, so the distance is converted to hull units.
SUN_EL, SUN_AZ = 16.0, -40.0
SUN_DIR = Vector((math.cos(math.radians(SUN_EL))*math.cos(math.radians(SUN_AZ)),
                  math.cos(math.radians(SUN_EL))*math.sin(math.radians(SUN_AZ)),
                  math.sin(math.radians(SUN_EL))))
SHOTS = [  # name, metres, lens mm, view direction (camera to hull)
    ('shot-s5-1500m', 1500.0, 85, Vector((-.93, .3, -.2)).normalized()),
    ('shot-s4-4000m', 4000.0, 35, Vector((-.15, .96, -.22)).normalized()),
]
for name, metres, lens, view_dir in SHOTS:
    d = metres/METRES_PER_UNIT
    bpy.ops.object.camera_add(location=TARGET-view_dir*d)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = lens, 36, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = d*.5, d*2
    cam.rotation_euler = view_dir.to_track_quat('-Z', 'Y').to_euler()
bpy.context.view_layer.update()
bpy.ops.object.light_add(type='SUN')
sun = bpy.context.object
sun.name = 'Review Asharu sun'
sun.data.energy = 4.6
sun.data.color = (1.0, .96, .88)
sun.rotation_euler = (-SUN_DIR).to_track_quat('-Z', 'Y').to_euler()


# --- What the bridge sees, and which guns bear ahead (owner, round 2). ---
# Both are measured on the built geometry, never asserted from the design.
def _cast(origin, direction, skip=(), dist=1000):
    dg = bpy.context.evaluated_depsgraph_get()
    o = Vector(origin)
    for _ in range(20):
        hit, p, _n, _i, ob, _m = scene.ray_cast(dg, o, direction, distance=dist)
        if not hit:
            return None, None
        if not any(ob.name.startswith(s) for s in skip):
            return ob.name, p
        o = p+direction*.05
    return None, None


def _pivots():
    return {t['slot']: bpy.data.objects[f"Weapon {t['slot']} pivot"] for t in mt_mounts.TURRETS}


def forward_checks():
    bpy.context.view_layer.update()
    # The captain's eye: on the centreline behind the middle of the dome's glazed band.
    name, p = _cast((60, 0, BRIDGE_EYE_Z), Vector((-1, 0, 0)), skip=('Weapon ',))
    assert name and name.startswith('Bridge'), f'eye ray met {name}'
    eye = p+Vector((.05, 0, 0))
    runs, last = [], None
    for k in range(0, 241):
        a = math.radians(k*.25)
        ob, q = _cast(eye, Vector((math.cos(a), 0, -math.sin(a))))
        label = 'clear' if ob is None else ob
        if label != last:
            runs.append({'from_deg': k*.25, 'meets': label, 'at_x': None if q is None else round(q.x, 1)})
            last = label
    # Can the bridge see the bow gun (WS 002's house roof) over the main gun?
    bow_gun = [o for o in bpy.data.objects if o.name.startswith('Weapon WS 002 house') and o.type == 'MESH']
    ztop = max((o.matrix_world @ Vector(c)).z for o in bow_gun for c in o.bound_box)
    aim = Vector((73, 0, ztop-.3))
    d = (aim-eye).normalized()
    ob, q = _cast(eye, d)
    bow_gun_seen = ob is not None and ob.startswith('Weapon WS 002')
    bow = Vector((86.5, 0, mt_hull.HEAD_STATIONS[0][2]))
    to_bow = (bow-eye).normalized()
    ob2, q = _cast(eye, to_bow)
    bow_seen = ob2 is None or (q-bow).length < 1.5
    sight = {'eye': [round(v, 2) for v in eye], 'eye_height_above_core_deck_m': round((eye.z-mt_hull.CORE_DECK)*METRES_PER_UNIT, 1),
             'depression_runs': runs, 'prow_visible': bow_seen,
             'prow_depression_deg': round(math.degrees(math.asin(-to_bow.z)), 1),
             'prow_ray_meets': ob2, 'bow_gun_visible': bow_gun_seen,
             'bow_gun_depression_deg': round(math.degrees(math.asin(-d.z)), 1)}
    # Every mount trained dead ahead (bearing 0): a horizontal ray from each
    # muzzle along +X must leave the ship without meeting any other part.
    pivots = _pivots()
    rest = {sid: pv.rotation_euler.z for sid, pv in pivots.items()}
    for pv in pivots.values():
        pv.rotation_euler.z = 0
    bpy.context.view_layer.update()
    bearing = []
    for t in mt_mounts.TURRETS:
        sid = t['slot']
        blocked = []
        for m in t['muzzles']:
            mo = bpy.data.objects[m['anchor']]
            ob, _q = _cast(mo.matrix_world.translation, Vector((1, 0, 0)), skip=(f'Weapon {sid} ', 'Anchor '))
            if ob:
                blocked.append(ob)
        bearing.append({'slot': sid, 'weapon': t['weapon'], 'bears_dead_ahead': not blocked,
                        'blocked_by': sorted(set(blocked))})
    if not args.ahead:
        for sid, pv in pivots.items():
            pv.rotation_euler.z = rest[sid]
        bpy.context.view_layer.update()
    n = sum(b['bears_dead_ahead'] for b in bearing)
    print(f'bridge eye {sight["eye"]}: bow gun visible {bow_gun_seen} at {sight["bow_gun_depression_deg"]} deg; prow visible {bow_seen} at {sight["prow_depression_deg"]} deg down; '
          f'runs {[(r["from_deg"], r["meets"]) for r in runs]}')
    print(f'bearing dead ahead: {n} of {len(bearing)}: {[(b["slot"], b["blocked_by"]) for b in bearing]}')
    return {'bridge_sightline': sight, 'bearing_dead_ahead': {'count': n, 'of': len(bearing), 'mounts': bearing}}


BRIDGE_EYE_Z = mt_hull.BRIDGE_DECK+3.4
FORWARD = forward_checks()
# The captain's view: a 24 mm perspective from the eye, 7 deg down, looking ahead.
bpy.ops.object.camera_add(location=Vector(FORWARD['bridge_sightline']['eye'])+Vector((.4, 0, 0)))  # just outside the glass
cam = bpy.context.object
cam.name = 'Camera bridge-eye'
cam.data.lens, cam.data.sensor_width, cam.data.clip_start, cam.data.clip_end = 24, 36, .3, 2000
cam.rotation_euler = Vector((1, 0, -.12)).to_track_quat('-Z', 'Y').to_euler()

wanted = []
for v in [v for v in args.views.split(',') if v and v != 'none']:
    wanted += [c[0] for c in CLOSEUPS[:5]] if v == 'details' else [s[0] for s in SHOTS] if v == 'shot' else [v]
wanted += [spec.split(':')[0] for spec in args.cam]
shot_extent = {}
for name in wanted:
    shot = name.startswith('shot-')
    scene.camera = bpy.data.objects['Camera '+name]
    sun.hide_render = not shot
    for lt in studio:
        lt.hide_render = shot
    bg.inputs[0].default_value = (0, 0, 0, 1) if shot else (.74, .76, .78, 1)
    bg.inputs[1].default_value = 0 if shot else .65
    # The top view and the shot checks render with a transparent film: the
    # top's alpha is the silhouette compose_review.py measures IoU from, the
    # shots' alpha is the measured frame extent.
    scene.render.film_transparent = name == 'top' or shot
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

if not args.no_save:
    manifest_path = OUT/'geometry.json'
    old = json.loads((HERE/'geometry.json').read_text(encoding='utf-8')) if (HERE/'geometry.json').exists() else {}
    manifest = {
        'status': STATUS,
        'hull': {
            'name': NAME, 'hullId': 'manticore', 'skinHullId': 'manticore_pirates', 'size': 'DESTROYER',
            'style': 'LOW_TECH', 'tech': 'Pirate (skin of the Low Tech manticore)', 'designation': 'Destroyer',
            'livery': 'pirate (manticore_pirates.skin, sprite manticore_pirate.png): blood-red armour, '
                      'brown gunmetal, pale command dome',
            'source': {'ship': SHIP, 'skin': SKIN, 'sprite': SPRITE, 'lights': 'none (the hull has no lights layer)',
                       'ship_data': 'data/hulls/ship_data.csv (row id manticore)',
                       'variant': 'data/variants/manticore/manticore_pirates_Support.variant (weapons modelled)'},
            'built_in_mods': ['ballistic_rangefinder'], 'built_in_weapons': [], 'ship_system': 'canister_flak',
            'shield': 'OMNI 150 deg (not modelled)', 'crew': [50, 80], 'cargo': 80, 'fuel': 50,
            'role': 'Asharu battle: the long-range destroyer that opens the fight and breaks the escort screen',
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(),
        'sprite': {'size': [164, 154], 'center': [82, 67],
                   'pixel_to_model': 'col c, row r (from the top) covers X 86-r..87-r, Y 81-c..82-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)]},
        'weapon_slots': [dict(s, location=[*s['location'], round(mt_mounts.ANCHOR_Z[s['id']], 2)],
                              weapon=mt_mounts.WEAPON.get(s['id']), variant_weapon=mt_mounts.VARIANT_WEAPON.get(s['id']),
                              pivot=f"Weapon {s['id']} pivot" if s['id'] in mt_mounts.WEAPON else None,
                              anchor=None if s['id'] in mt_mounts.WEAPON else f"Weapon {s['id']} anchor")
                         for s in mt_mounts.SLOTS],
        'turrets': mt_mounts.TURRETS,
        'engines': mt_engines.records(),
        'running_lights': mt_lights.records(),
        'asset': {'file': 'manticore.blend', 'collection': COLL, 'root': ROOT, 'anchors_collection': f'{COLL} | anchors',
                  'baked': 'every modifier applied; no live booleans or bevels',
                  'append': f"bpy.data.libraries.load(path, link=True) and take collection '{COLL}'; "
                            "instance it with a collection-instance empty scaled by metres_per_unit",
                  'triangles': sum(len(p.vertices)-2 for o in meshes for p in o.data.polygons),
                  'objects': len(meshes), 'materials': sorted({m.name for o in meshes for m in o.data.materials})},
        'object_names': {'Hull ...': 'bow head, neck, core, wing body, aft body, ventral keel, turret pods, '
                                     'launcher housings and cheeks, turret rings, detail',
                         'Bridge ...': 'the pale command dome and its glazing',
                         'Engine <i> ...': 'drive can, aft-facing bell, mouth lip, dark throat, exhaust anchor, '
                                           'drive glow anchor',
                         'Weapon <slot id> ...': 'barbette (fixed); pivot (trains about local Z), elevation '
                                                 '(local Y, negative lifts), turret-kit house and gun parts, '
                                                 'muzzle anchor <k>',
                         'Anchor turret <i> barrel|tube <k> muzzle': "the VFX kit's declared muzzles, mount i in "
                                                                     'turrets order; scale and kind properties',
                         'Weapon WS 006..009 anchor': 'hidden system slots (canister flak), anchor only',
                         'Light <side> <where> <k>': 'running lights; positions, colours and blink in running_lights'},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
        'closeups': [{'name': n, 'position': list(p), 'target': list(t), 'orthographic_scale': s}
                     for n, p, t, s in CLOSEUPS],
        'forward': FORWARD,
        'shot_checks': [{'name': n, 'distance_m': m, 'lens_mm': lens, 'sensor_mm': 36,
                         'view_direction': [round(v, 3) for v in vd], 'sun_direction': [round(v, 3) for v in SUN_DIR],
                         'sun': 'asharu-battle battle_blockout.py SUN_EL 16 / SUN_AZ -40, hull heading world +X'}
                        for n, m, lens, vd in SHOTS],
    }
    for key in ('silhouette', 'shot_extent_px'):
        if key in old:
            manifest[key] = old[key]
    manifest_path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    if args.manifest:
        shutil.copy(manifest_path, HERE/'geometry.json')

    # --- The appendable asset: one collection, one root, nothing but the ship. ---
    for ob in [o for o in bpy.data.objects if o.type in ('CAMERA', 'LIGHT')]:
        bpy.data.objects.remove(ob, do_unlink=True)
    coll = bpy.data.collections.new(COLL)
    scene.collection.children.link(coll)
    anchors = bpy.data.collections.new(f'{COLL} | anchors')
    coll.children.link(anchors)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = 'ARROWS', 12
    coll.objects.link(root)
    root['metres_per_unit'] = METRES_PER_UNIT
    root['axes'] = '+X bow, +Y port, +Z dorsal; hull units'
    root['source'] = 'art-source/company-prologue/manticore-geometry/build_manticore.py'
    for ob in [o for o in bpy.data.objects if o is not root]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        (anchors if ob.type == 'EMPTY' else coll).objects.link(ob)
        if ob.parent is None:
            ob.parent = root  # root sits at the hull origin: no inverse needed
    bpy.context.view_layer.update()
    orphans = [o.name for o in coll.all_objects if o is not root and o.parent is None]
    assert not orphans, orphans
    for c in [c for c in bpy.data.collections if c.name not in (COLL, f'{COLL} | anchors')]:
        bpy.data.collections.remove(c)
    bpy.ops.outliner.orphans_purge(do_recursive=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'manticore.blend'), compress=True)
    print('saved', OUT/'manticore.blend')
