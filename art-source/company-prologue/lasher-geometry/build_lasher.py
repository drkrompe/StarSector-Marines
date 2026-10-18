"""Build the Lasher escort frigate with Blender's Python: model, reviews, export.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_lasher.py -- --out DIR
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (34, 35), so slot and engine locations from
lasher.ship are model XY unchanged. Heights are proposed art.

One module per area: ls_hull (bow head, nose cap, spine, wing body,
nacelles), ls_pods (gun and missile pods), ls_engines (the five drives),
ls_mounts (the nine slots: turrets, hardpoints, system anchors), ls_lights
(running lights); ls_kit holds the shared helpers.

Options after a literal "--":
  --out DIR      write renders, geometry.json and lasher.blend to DIR
                 (default build/lasher under the repository root; nothing
                 the build writes is tracked except geometry.json, which
                 --manifest copies back into this folder)
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, plus the close-ups bow-detail,
                 turret-detail, engine-detail, stern-detail and the shot
                 checks shot-1500m, shot-3000m; 'details' = the close-ups,
                 'shot' = both shot checks, 'none' = no renders)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip lasher.blend and geometry.json
  --manifest     also copy geometry.json into this folder (the tracked copy)

The saved lasher.blend is the appendable cinematic asset: one collection
`LASHER` holding the root empty `LASHER | root`, which parents every part;
anchors live in the child collection `LASHER | anchors`. Every modifier is
baked (no live booleans or bevels), turrets train by their `Turret <i>
pivot`, and nothing else (cameras, lamps, review rig) is in the file.
Then run compose_review.py (Python + Pillow + numpy) for the top-view sprite
overlay, the silhouette IoU and the review set.
"""
from pathlib import Path
import argparse
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
import ls_kit, ls_hull, ls_pods, ls_engines, ls_mounts, ls_lights
for module in (ls_kit, ls_hull, ls_pods, ls_engines, ls_mounts, ls_lights):
    importlib.reload(module)
from starsector_path import GAME_CORE as GAME
sys.path.insert(0, str(HERE.parent))  # company-prologue: ship_scale
import ship_scale

SHIP = 'data/hulls/lasher.ship'
SPRITE = 'graphics/ships/lasher/lasher_base.png'
LIGHTS_SPRITE = 'graphics/ships/lasher/lasher_lights.png'
COLL = 'LASHER'
ROOT = 'LASHER | root'

# Real-world scale (pipeline pass 4), from the shared sizing basis
# (../ship_scale.py, owner 2026-10-18): contents (50 crew, 40 cargo, 25 fuel)
# over the frigate machinery share, against the measured enclosed volume, the
# union of ship_scale.SOLIDS['lasher'] (head, nose cap, spine, wing body,
# ventral keel; never the drive nacelles or weapon pods). The driver re-measures
# that union on every build and fails if it drifts from ship_scale.HULLS, so a
# shape change has to be carried into the shared table, where it moves the
# solved scale for every reader at once.
SCALE = ship_scale.record('Lasher')
METRES_PER_UNIT = ship_scale.PUBLISHED['Lasher']
assert abs(SCALE['solved_metres_per_unit']-METRES_PER_UNIT) < .01, (SCALE['solved_metres_per_unit'], METRES_PER_UNIT)
SOLIDS = [n for n in ('Hull bow head', 'Hull nose cap', 'Hull spine', 'Hull wing body', 'Hull ventral keel',
                      'Hull port engine nacelle', 'Hull starboard engine nacelle', 'Pod port gun body')
          if ship_scale.SOLIDS['lasher']['include'](n)]

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--manifest', action='store_true')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out or HERE.parents[2]/'build'/'lasher').resolve()
OUT.mkdir(parents=True, exist_ok=True)


def read_ship():
    import re
    return json.loads(re.sub(r'#.*', '', (GAME/SHIP).read_text(encoding='utf-8')))


def check_against_install():
    """Fail if the vanilla slots no longer match the constants the model uses."""
    if not (GAME/SHIP).exists():
        return 'install not found; constants used as committed'
    ship = read_ship()
    slots = {s['id']: s for s in ship['weaponSlots']}
    for mine in ls_mounts.SLOTS:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (mine['location'], mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from lasher.ship: {theirs} vs {ours}")
    if len(slots) != len(ls_mounts.SLOTS):
        raise RuntimeError('lasher.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['length']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in ls_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from lasher.ship: {engines}')
    if tuple(ship['center']) != (34, 35):
        raise RuntimeError(f"lasher.ship centre changed: {ship['center']}")
    return f'verified against the installed {SHIP}'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = ls_kit.Kit()
kit.mpu = METRES_PER_UNIT
ls_hull.build(kit)
ls_pods.build(kit)
ls_engines.build(kit)
ls_mounts.build(kit)
ls_lights.build(kit)
bpy.context.view_layer.update()

clash = ls_mounts.clearance(
    [o.name for o in bpy.data.objects if o.name.startswith('Turret ') and ' barrel ' in o.name
     and o.type == 'MESH'],
    [o.name for o in bpy.data.objects if o.type == 'MESH' and (o.name.startswith(('Hull ', 'Pod ')))])
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
    measured = union_volume(SOLIDS)
    table = ship_scale.HULLS['Lasher']['units3']
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f"Lasher units3 {table}: update the shared table")
    size = [round((b-a)*METRES_PER_UNIT, 1) for a, b in zip(LO, HI)]
    return {**SCALE, 'published_metres_per_unit': METRES_PER_UNIT,
            'measured_this_build': {'enclosed_units3': round(measured), 'solids': SOLIDS,
                                    'all_hull_solids_units3_incl_nacelles': round(union_volume(
                                        SOLIDS+['Hull port engine nacelle', 'Hull starboard engine nacelle']))},
            'size_m': size, 'person_1_8_m_in_units': round(1.8/METRES_PER_UNIT, 2),
            'not_this': 'landed-ships-true-scale.md (battle-map readability) is a different, smaller number'}


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
for name, loc, energy, size in [('Key', (60, 70, 100), 150000, 80), ('Fill', (-50, -65, 55), 80000, 70)]:
    bpy.ops.object.light_add(type='AREA', location=loc)
    lt = bpy.context.object
    lt.name = 'Review '+name
    lt.data.energy, lt.data.shape, lt.data.size = energy, 'DISK', size
    lt.rotation_euler = (-lt.location).to_track_quat('-Z', 'Y').to_euler()
    studio.append(lt)

TARGET = Vector((13, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (13, 0, 150), 152),
    ('port-side', (13, 180, 0), 112),
    ('front', (180, 0, 0), 100),
    ('three-quarter', (110, 130, 85), 136),
    ('stern', (-180, 0, 0), 100),
]
CLOSEUPS = [  # name, position, target, ortho scale
    ('bow-detail', (95, 55, 45), (42, 0, 2), 52),
    ('turret-detail', (40, 60, 45), (6, 6, 4), 40),
    ('engine-detail', (-80, 48, 30), (-27, 6, 0), 46),
    ('stern-detail', (-85, -55, 40), (-20, 0, 0), 64),
]
for spec in args.cam:
    n, p, t, sc = spec.split(':')
    CLOSEUPS.append((n, tuple(map(float, p.split(','))), tuple(map(float, t.split(','))), float(sc)))
for name, pos, target, sc in CLOSEUPS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type, cam.data.ortho_scale = 'ORTHO', sc
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat('-Z', 'Y').to_euler()
for name, pos, sc in CAMERAS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.type, cam.data.ortho_scale, cam.data.clip_end = 'ORTHO', sc, 1000
    cam.rotation_euler = (0, 0, math.pi) if name == 'top' else \
        (TARGET-cam.location).to_track_quat('-Z', 'Y').to_euler()

# Shot checks for C1 v2: the c1_core camera (50 mm lens on a 36 mm sensor),
# the hull 1.5 and 3 km away seen from abeam-and-above, lit only by a sun at
# c1_core.SUN_IN_CAMERA (left, up, behind the camera), black sky, no fill.
# Perspective is scale-free, so the distance is converted to hull units.
SUN_IN_CAMERA = Vector((-0.62, 0.50, 0.50)).normalized()
SHOTS = [('shot-1500m', 1500.0), ('shot-3000m', 3000.0)]
view_dir = Vector((-.35, -.9, -.26)).normalized()  # camera looks toward -Y mostly: the port flank
for name, metres in SHOTS:
    d = metres/METRES_PER_UNIT
    bpy.ops.object.camera_add(location=TARGET-view_dir*d)
    cam = bpy.context.object
    cam.name = 'Camera '+name
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = 50, 36, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = d*.5, d*2
    cam.rotation_euler = view_dir.to_track_quat('-Z', 'Y').to_euler()
bpy.context.view_layer.update()
bpy.ops.object.light_add(type='SUN')
sun = bpy.context.object
sun.name = 'Review C1 sun'
sun.data.energy = 4.0
sun_world = (bpy.data.objects['Camera shot-1500m'].matrix_world.to_3x3() @ SUN_IN_CAMERA).normalized()
sun.rotation_euler = (-sun_world).to_track_quat('-Z', 'Y').to_euler()

wanted = []
for v in [v for v in args.views.split(',') if v and v != 'none']:
    wanted += [c[0] for c in CLOSEUPS[:4]] if v == 'details' else [s[0] for s in SHOTS] if v == 'shot' else [v]
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
        'status': 'Pass 2 (light) + pass 4 scale: silhouette, sections, slots, drives, anchors, glazing, '
                  'vents and running lights; one Hegemony base material with light procedural wear. '
                  'Built for distant formation shots (C1 v2); no interiors, damage or articulation beyond turrets.',
        'hull': {
            'name': 'Lasher', 'hullId': 'lasher', 'size': 'FRIGATE', 'style': 'LOW_TECH', 'tech': 'Low Tech',
            'designation': 'Frigate', 'livery': 'Hegemony (the base hull and sprite; Hegemony fleets fly it '
                                                'unskinned, data/world/factions/hegemony.faction)',
            'source': {'ship': SHIP, 'sprite': SPRITE, 'lights': LIGHTS_SPRITE,
                       'ship_data': 'data/hulls/ship_data.csv (row id lasher)',
                       'variants': 'data/variants/lasher/lasher_*.variant (slots only; loadouts not modelled)'},
            'built_in_mods': ['ballistic_rangefinder'], 'built_in_weapons': [], 'ship_system': 'ammofeed',
            'shield': 'FRONT 210 deg (not modelled)', 'crew': [25, 50], 'cargo': 40, 'fuel': 25,
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(),
        'sprite': {'size': [68, 96], 'center': [34, 35],
                   'pixel_to_model': 'col c, row r (from the top) covers X 60-r..61-r, Y 33-c..34-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)]},
        'weapon_slots': [dict(s, location=[*s['location'], round(ls_mounts.ANCHOR_Z[s['id']], 2)],
                              anchor=f"Weapon {s['id']} anchor", muzzles=ls_mounts.MUZZLES.get(s['id'], []))
                         for s in ls_mounts.SLOTS],
        'turrets': ls_mounts.TURRETS,
        'engines': ls_engines.records(),
        'running_lights': ls_lights.records(),
        'asset': {'file': 'lasher.blend', 'collection': COLL, 'root': ROOT, 'anchors_collection': f'{COLL} | anchors',
                  'baked': 'every modifier applied; no live booleans or bevels',
                  'append': f"bpy.data.libraries.load(path, link=True) and take collection '{COLL}'; "
                            "instance it with a collection-instance empty scaled by metres_per_unit",
                  'triangles': sum(len(p.vertices)-2 for o in meshes for p in o.data.polygons),
                  'objects': len(meshes), 'materials': sorted({m.name for o in meshes for m in o.data.materials})},
        'object_names': {'Hull ...': 'bow head, nose cap, spine, wing body, nacelles, glazing, vents, seams',
                         'Pod <side> gun|missile ...': 'weapon pods round the hardpoints',
                         'Engine <i> ...': 'drive can, aft-facing bell, mouth lip, dark throat, exhaust anchor, '
                                           'drive glow anchor',
                         'Turret <i> ...': 'barbette and traverse gap (fixed); gunhouse, mantlet, barrels and '
                                           'Anchor turret <i> barrel <k> muzzle under Turret <i> pivot',
                         'Weapon <slot id> ...': 'hardpoint guns and launchers; anchor, and Anchor weapon <slot id> muzzle',
                         'Light <side> <where> <k>': 'running lights; positions, colours and blink in running_lights'},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
        'shot_checks': [{'name': n, 'distance_m': m, 'lens_mm': 50, 'sensor_mm': 36,
                         'view_direction': [round(v, 3) for v in view_dir], 'sun_in_camera': list(SUN_IN_CAMERA)}
                        for n, m in SHOTS],
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
    root['source'] = 'art-source/company-prologue/lasher-geometry/build_lasher.py'
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
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'lasher.blend'), compress=True)
    print('saved', OUT/'lasher.blend')
