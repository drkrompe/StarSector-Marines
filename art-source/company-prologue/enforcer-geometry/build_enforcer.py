"""Build the Enforcer destroyer with Blender's Python: model, reviews, export.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_enforcer.py -- --variant merchant
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (68, 67), so slot and engine locations from
enforcer.ship are model XY unchanged. Heights are proposed art.

One build serves both fleets (asharu-battle-shot.md): --variant merchant is
the convoy's independent escort lead (base hull, enforcer_base.png), and
--variant pirate is the Enforcer (P) (skin enforcer_d_pirates,
enforcer_pirates.png): the same hull objects plus its welded armour
(ef_armour_p) in the pirate livery.

One module per area: ef_hull (carapace, wells, ventral keel), ef_bow (neck,
prow, missile pods), ef_spine (aft spine, bridge, tanks), ef_engines (engine
blocks and the six drives), ef_mounts
(the nine slots), ef_lights (running lights), ef_armour_p (pirate refit);
ef_kit holds the shared helpers.

Options after a literal "--":
  --variant V    merchant (default) or pirate
  --out DIR      write renders, geometry.json and the .blend to DIR
                 (default build/enforcer/<variant> under the repository root;
                 nothing the build writes is tracked except geometry.json,
                 which --manifest copies back into this folder)
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, the close-ups bow-detail,
                 turret-detail, engine-detail, stern-detail, bridge-detail and
                 the shot checks shot-s6, shot-s4; 'details' = the close-ups,
                 'shot' = both shot checks, 'none' = no renders)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip the .blend and geometry.json
  --manifest     also copy geometry.json into this folder (the tracked copy;
                 merchant only, the pirate's goes to geometry-p.json)
"""
from pathlib import Path
import argparse
import importlib
import json
import math
import shutil
import sys
import bpy
from mathutils import Matrix, Quaternion, Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent.parent))  # art-source: starsector_path
import ef_kit, ef_hull, ef_bow, ef_spine, ef_engines, ef_mounts, ef_lights, ef_armour_p
for module in (ef_kit, ef_hull, ef_bow, ef_spine, ef_engines, ef_mounts, ef_lights, ef_armour_p):
    importlib.reload(module)
from starsector_path import GAME_CORE as GAME
sys.path.insert(0, str(HERE.parent))  # company-prologue: ship_scale
import ship_scale

SHIP = 'data/hulls/enforcer.ship'
SKIN = 'data/hulls/skins/enforcer_d_pirates.skin'
SPRITES = {'merchant': 'graphics/ships/enforcer/enforcer_base.png',
           'pirate': 'graphics/ships/enforcer/enforcer_pirates.png'}

parser = argparse.ArgumentParser()
parser.add_argument('--variant', default='merchant', choices=('merchant', 'pirate'))
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--manifest', action='store_true')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
PIRATE = args.variant == 'pirate'
SPRITE = SPRITES[args.variant]
COLL = 'ENFORCER (P)' if PIRATE else 'ENFORCER'
ROOT = f'{COLL} | root'
BLEND = 'enforcer-p.blend' if PIRATE else 'enforcer.blend'
OUT = Path(args.out or HERE.parents[2]/'build'/'enforcer'/args.variant).resolve()
OUT.mkdir(parents=True, exist_ok=True)

# Real-world scale (pipeline pass 4), from the shared sizing basis
# (../ship_scale.py, owner's option C): until the hull is in ship_scale.HULLS
# the blockout uses the shot plan's estimate (95 m over the 134-unit hull).
if 'Enforcer' in ship_scale.HULLS:
    SCALE = ship_scale.record('Enforcer')
    METRES_PER_UNIT = ship_scale.PUBLISHED['Enforcer']
    SOLIDS_KEY = 'enforcer'
else:
    SCALE, METRES_PER_UNIT, SOLIDS_KEY = None, .71, None
SOLIDS_CANDIDATES = ('Hull carapace', 'Hull ventral keel', 'Hull neck', 'Hull prow', 'Hull aft spine',
                     'Bridge deckhouse', 'Hull port engine block', 'Hull starboard engine block')


def read_ship():
    import re
    return json.loads(re.sub(r'#.*', '', (GAME/SHIP).read_text(encoding='utf-8')))


def check_against_install():
    import re
    """Fail if the vanilla slots no longer match the constants the model uses."""
    if not (GAME/SHIP).exists():
        return 'install not found; constants used as committed'
    ship = read_ship()
    slots = {s['id']: s for s in ship['weaponSlots']}
    for mine in ef_mounts.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (mine['location'], mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
                raise RuntimeError(f"{mine['id']} differs from enforcer.ship: {theirs} vs {ours}")
    if len(slots) != len(ef_mounts.SLOTS):
        raise RuntimeError('enforcer.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['length']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in ef_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from enforcer.ship: {engines}')
    if tuple(ship['center']) != (68, 67):
        raise RuntimeError(f"enforcer.ship centre changed: {ship['center']}")
    skin = json.loads(re.sub(r',(\s*[}\]])', r'\1', re.sub(r'#.*', '', (GAME/SKIN).read_text(encoding='utf-8'))))
    if skin['baseHullId'] != 'enforcer' or skin['removeWeaponSlots'] or skin['removeEngineSlots'] \
            or skin['spriteName'] != SPRITES['pirate']:
        raise RuntimeError(f'{SKIN} changes slots or sprite: {skin}')
    return f'verified against the installed {SHIP} and {SKIN}'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = ef_kit.Kit('pirate' if PIRATE else 'merchant')
kit.mpu = METRES_PER_UNIT
ef_hull.build(kit)
ef_bow.build(kit)
ef_spine.build(kit)
ef_engines.build(kit)
ef_mounts.build(kit)
ef_lights.build(kit)
ARMOUR_P = [o.name for o in ef_armour_p.build(kit)] if PIRATE else []
bpy.context.view_layer.update()

clash = ef_mounts.clearance(
    [o.name for o in bpy.data.objects if o.name.startswith('Turret ') and ' barrel ' in o.name
     and o.type == 'MESH'],
    [o.name for o in bpy.data.objects if o.type == 'MESH' and (o.name.startswith(('Hull ', 'Pod ', 'Bridge ')))])
if clash:
    raise RuntimeError(f'turret barrels clash at rest: {clash}')


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
    solids = [n for n in SOLIDS_CANDIDATES if n in bpy.data.objects
              and (SOLIDS_KEY is None or ship_scale.SOLIDS[SOLIDS_KEY]['include'](n))]
    measured = union_volume(solids)
    size = [round((b-a)*METRES_PER_UNIT, 1) for a, b in zip(LO, HI)]
    rec = {'published_metres_per_unit': METRES_PER_UNIT,
           'measured_this_build': {'enclosed_units3': round(measured), 'solids': solids},
           'size_m': size, 'person_1_8_m_in_units': round(1.8/METRES_PER_UNIT, 2),
           'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number'}
    if SCALE is None:
        return {**rec, 'method': 'provisional: the shot plan\'s 95 m estimate over the 134-unit hull; '
                                 'pass 4 solves it with ship_scale.py'}
    table = ship_scale.HULLS['Enforcer']['units3']
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f"Enforcer units3 {table}: update the shared table")
    return {**SCALE, **rec}


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
for name, loc, energy, size in [('Key', (90, 100, 140), 300000, 110), ('Fill', (-80, -95, 80), 160000, 100)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    lt = bpy.context.object
    lt.name = 'Review '+name
    lt.data.energy, lt.data.shape, lt.data.size = energy, 'DISK', size
    lt.rotation_euler = (-lt.location).to_track_quat('-Z', 'Y').to_euler()
    studio.append(lt)

TARGET = Vector((0, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (0, 0, 200), 250),
    ('port-side', (0, 250, -1), 150),
    ('front', (250, 0, -1), 150),
    ('three-quarter', (140, 165, 110), 175),
    ('stern', (-250, 0, -1), 150),
]
CLOSEUPS = [  # name, position, target, ortho scale
    ('bow-detail', (120, 70, 60), (45, 10, 0), 70),
    ('turret-detail', (60, 90, 60), (12, 29, 7), 40),
    ('engine-detail', (-120, 70, 40), (-60, 28, 0), 55),
    ('stern-detail', (-120, -80, 70), (-35, 0, 2), 90),
    ('bridge-detail', (25, 40, 40), (-15, 0, 8), 34),
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
    cam.rotation_euler = (0, 0, 0) if name == "top" else \
        (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()

# Shot checks against the Asharu battle blockout (asharu-battle/battle_blockout.py,
# key frames k3 = S6 and k2 = S4): the ship placed as the blockout places it
# (x, y, z m, heading, pitch, roll), the camera at its position and lens, lit
# only by the blockout's sun (elevation 16, azimuth -40), black sky, no fill.
# The hull stays at the origin in hull units; the camera is moved into the
# hull's frame instead, which is the same picture.
SUN_EL, SUN_AZ = 16.0, -40.0
SHOTS = {  # name: (ship placement, camera loc, camera target, lens)
    'shot-s6': ((480, 330, 90, 165, 4, 25), (1350, 750, 380), (-1400, -350, -260), 30),
    'shot-s4': ((-7300, 900, -3400, 6, 12, -6), (-4800, -2600, -1700), (-6500, 200, -3100), 35),
}


def ship_frame(x, y, z, hd, pt, rl):
    q = (Quaternion((0, 0, 1), math.radians(hd)) @ Quaternion((0, 1, 0), -math.radians(pt))
         @ Quaternion((1, 0, 0), math.radians(rl)))
    return Matrix.Translation(Vector((x, y, z))) @ q.to_matrix().to_4x4() @ Matrix.Scale(METRES_PER_UNIT, 4)


SHOT_TO_HULL = {}
for name, (place, loc, target, lens) in SHOTS.items():
    to_hull = ship_frame(*place).inverted()
    SHOT_TO_HULL[name] = to_hull
    p, t = to_hull @ Vector(loc), to_hull @ Vector(target)
    bpy.ops.object.camera_add(location=p)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = lens, 36, 'HORIZONTAL'
    d = (p-Vector((0, 0, 0))).length
    cam.data.clip_start, cam.data.clip_end = d*.3, d*3
    # World up through the same transform keeps the blockout's horizon.
    up = (to_hull.to_3x3() @ Vector((0, 0, 1))).normalized()
    fwd = (t-p).normalized()
    right = fwd.cross(up).normalized()
    cam.matrix_world = Matrix((right, right.cross(fwd), -fwd)).transposed().to_4x4() @ Matrix()
    cam.location = p
sun_world = Vector((math.cos(math.radians(SUN_EL))*math.cos(math.radians(SUN_AZ)),
                    math.cos(math.radians(SUN_EL))*math.sin(math.radians(SUN_AZ)), math.sin(math.radians(SUN_EL))))
bpy.ops.object.light_add(type='SUN')
sun = bpy.context.object
sun.name = 'Review Asharu sun'
sun.data.energy, sun.data.color = 4.6, (1.0, .96, .88)

wanted = []
for v in [v for v in args.views.split(',') if v and v != 'none']:
    wanted += [c[0] for c in CLOSEUPS[:5]] if v == 'details' else list(SHOTS) if v == 'shot' else [v]
wanted += [spec.split(':')[0] for spec in args.cam]
for name in wanted:
    shot = name.startswith('shot-')
    scene.camera = bpy.data.objects['Camera '+name]
    sun.hide_render = not shot
    if shot:
        sd = (SHOT_TO_HULL[name].to_3x3() @ sun_world).normalized()
        sun.rotation_euler = sd.to_track_quat('Z', 'Y').to_euler()
    for lt in studio:
        lt.hide_render = shot
    bg.inputs[0].default_value = (0, 0, 0, 1) if shot else (.74, .76, .78, 1)
    bg.inputs[1].default_value = 0 if shot else .65
    scene.render.film_transparent = name == 'top' or shot
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

if not args.no_save:
    manifest_path = OUT/'geometry.json'
    tracked = HERE/('geometry-p.json' if PIRATE else 'geometry.json')
    old = json.loads(tracked.read_text(encoding='utf-8')) if tracked.exists() else {}
    manifest = {
        'status': 'Pass 1 blockout: silhouette, sections, slots, drives, anchors and running lights; '
                  'material classes coloured per livery.',
        'variant': args.variant,
        'hull': {
            'name': 'Enforcer (P)' if PIRATE else 'Enforcer', 'hullId': 'enforcer',
            'skinHullId': 'enforcer_d_pirates' if PIRATE else None,
            'size': 'DESTROYER', 'style': 'LOW_TECH', 'tech': 'Pirate' if PIRATE else 'Low Tech',
            'designation': 'Destroyer',
            'livery': 'pirate (enforcer_pirates.png): stripped gunmetal, red welded slabs' if PIRATE else
                      'independent escort (the Asharu convoy\'s hired screen); the base hull and sprite',
            'source': {'ship': SHIP, 'skin': SKIN if PIRATE else None, 'sprite': SPRITE,
                       'ship_data': 'data/hulls/ship_data.csv (row id enforcer)',
                       'variants': 'data/variants/enforcer/*.variant (slots only; loadouts not modelled)'},
            'ship_system': 'burndrive', 'shield': 'FRONT 150 deg (not modelled)', 'crew': [60, 100],
            'cargo': 90, 'fuel': 90,
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(),
        'sprite': {'size': [136, 136], 'center': [68, 67],
                   'pixel_to_model': 'col c, row r (from the top) covers X 68-r..69-r, Y 67-c..68-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)]},
        'weapon_slots': [dict(s, location=[*s['location'], round(ef_mounts.ANCHOR_Z[s['id']], 2)],
                              anchor=f"Weapon {s['id']} anchor", muzzles=ef_mounts.MUZZLES.get(s['id'], []))
                         for s in ef_mounts.SLOTS],
        'turrets': ef_mounts.TURRETS,
        'engines': ef_engines.records(),
        'running_lights': ef_lights.records(),
        'pirate_armour': ARMOUR_P,
        'asset': {'file': BLEND, 'collection': COLL, 'root': ROOT, 'anchors_collection': f'{COLL} | anchors',
                  'baked': 'every modifier applied; no live booleans or bevels',
                  'append': f"bpy.data.libraries.load(path, link=True) and take collection '{COLL}'; "
                            "instance it with a collection-instance empty scaled by metres_per_unit",
                  'triangles': sum(len(p.vertices)-2 for o in meshes for p in o.data.polygons),
                  'objects': len(meshes), 'materials': sorted({m.name for o in meshes for m in o.data.materials})},
        'object_names': {'Hull ...': 'carapace, ventral keel, neck, prow, aft spine, engine blocks, tanks',
                         'Bridge ...': 'deckhouse and glazing band on the aft spine',
                         'Pod <side> inner|outer missile': 'missile pods round the hardpoints',
                         'Engine <i> ...': 'drive can, aft-facing bell, mouth lip, dark throat, exhaust anchor, '
                                           'drive glow anchor',
                         'Turret <i> ...': 'barbette and traverse gap (fixed); gunhouse, mantlet, barrels and '
                                           'Anchor turret <i> barrel <k> muzzle under Turret <i> pivot',
                         'Weapon <slot id> ...': 'hardpoint launchers; anchor, and Anchor weapon <slot id> muzzle',
                         'Light <side> <where> <k>': 'running lights; positions, colours and blink in running_lights',
                         'Armour P ...': 'pirate variant only: welded slabs, girders, prow cross, neck sensor'},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s} for n, p, s in CAMERAS],
        'shot_checks': [{'name': n, 'ship': list(v[0]), 'camera': list(v[1]), 'target': list(v[2]), 'lens_mm': v[3],
                         'sensor_mm': 36, 'sun_el_az_deg': [SUN_EL, SUN_AZ],
                         'source': 'asharu-battle/battle_blockout.py ' + ('k3' if n == 'shot-s6' else 'k2')}
                        for n, v in SHOTS.items()],
    }
    for key in ('silhouette', 'shot_extent_px'):
        if key in old:
            manifest[key] = old[key]
    manifest_path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    if args.manifest:
        shutil.copy(manifest_path, tracked)

    # --- The appendable asset: one collection, one root, nothing but the ship. ---
    for ob in [o for o in bpy.data.objects if o.type in ('CAMERA', 'LIGHT')]:
        bpy.data.objects.remove(ob, do_unlink=True)
    coll = bpy.data.collections.new(COLL)
    scene.collection.children.link(coll)
    anchors = bpy.data.collections.new(f'{COLL} | anchors')
    coll.children.link(anchors)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = 'ARROWS', 16
    coll.objects.link(root)
    root['metres_per_unit'] = METRES_PER_UNIT
    root['axes'] = '+X bow, +Y port, +Z dorsal; hull units'
    root['variant'] = args.variant
    root['source'] = 'art-source/company-prologue/enforcer-geometry/build_enforcer.py'
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
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/BLEND), compress=True)
    print('saved', OUT/BLEND)
