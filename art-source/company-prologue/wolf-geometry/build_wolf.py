"""Build the Wolf (P) pirate frigate with Blender's Python: model, reviews, export.

Run from this folder:
  sh ../../blender.sh --background --factory-startup --python build_wolf.py -- --out DIR
Coordinates are hull-local, +X bow, +Y port, +Z dorsal; one sprite pixel is
one unit about the .ship centre (26, 42), so slot and engine locations from
wolf.ship are model XY unchanged. Heights are proposed art.

One module per area: wf_hull (bow fork, head and outriggers, neck, main
body and missile pods, aft body and armour domes, engine block, spine),
wf_engines (the three drives), wf_mounts (the four slots the pirate skin
keeps), wf_lights (hull lamps and running lights), wf_detail (pass 2 detail read off
the sprites); wf_kit holds the helpers.

Options after a literal "--":
  --out DIR      write renders, geometry.json and wolf-p.blend to DIR
                 (default build/wolf-p under the repository root; nothing
                 the build writes is tracked except geometry.json, which
                 --manifest copies back into this folder)
  --views a,b    render only these cameras (top, port-side, front,
                 three-quarter, stern, plus the close-ups bow-detail,
                 turret-detail, engine-detail, stern-detail and the shot
                 checks shot-670m (S7-S9), shot-1500m (S6), shot-4000m (S4);
                 'details' = the close-ups, 'shot' = the shot checks,
                 'none' = no renders)
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
  --samples N    Cycles samples (default 24)
  --res WxH      render size (default 1920x1080)
  --no-save      skip wolf-p.blend and geometry.json
  --manifest     also copy geometry.json into this folder (the tracked copy)

The saved wolf-p.blend is the appendable cinematic asset: one collection
`WOLF_P` holding the root empty `WOLF_P | root`, which parents every part;
anchors live in the child collection `WOLF_P | anchors`. Every modifier is
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
import wf_kit, wf_hull, wf_engines, wf_mounts, wf_lights, wf_detail
for module in (wf_kit, wf_hull, wf_engines, wf_mounts, wf_lights, wf_detail):
    importlib.reload(module)
from starsector_path import GAME_CORE as GAME
sys.path.insert(0, str(HERE.parent))  # company-prologue: ship_scale
import ship_scale

SHIP = 'data/hulls/wolf.ship'
SKIN = 'data/hulls/skins/wolf_d_pirates.skin'
SPRITE = 'graphics/ships/wolf/wolf_pirates.png'
BASE_SPRITE = 'graphics/ships/wolf/wolf_base.png'
COLL = 'WOLF_P'
ROOT = 'WOLF_P | root'
NAME = 'Wolf (P)'

# Real-world scale (pipeline pass 4), from the shared sizing basis
# (../ship_scale.py, option C, owner 2026-10-18): contents (30 crew, 50 cargo,
# 20 fuel) over the frigate machinery share, against the measured enclosed
# volume, the union of ship_scale.SOLIDS['wolf-p'] (head, neck and its flanks,
# main body, centre hump, wing roots, aft body, dorsal spine, ventral keel;
# never the bow fork, outriggers, missile
# pods, armour domes or the engine block, which is the drive section). The
# driver re-measures that union on every build and fails if it drifts from
# ship_scale.HULLS, so a shape change is carried into the shared table.
SCALE = ship_scale.record(NAME) if NAME in ship_scale.HULLS else None
METRES_PER_UNIT = ship_scale.PUBLISHED.get(NAME, .9)
if SCALE:
    assert abs(SCALE['solved_metres_per_unit']-METRES_PER_UNIT) < .01, (SCALE['solved_metres_per_unit'], METRES_PER_UNIT)
SOLID_NAMES = ('Hull head', 'Hull neck', 'Hull main body', 'Hull aft body', 'Hull dorsal spine fore',
               'Hull dorsal spine aft', 'Hull ventral keel', 'Hull centre hump', 'Hull neck port flank',
               'Hull neck starboard flank', 'Hull wing port root', 'Hull wing starboard root')
SOLIDS = [n for n in SOLID_NAMES if NAME not in ship_scale.HULLS or ship_scale.SOLIDS['wolf-p']['include'](n)]

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views', default='top,port-side,front,three-quarter,stern')
parser.add_argument('--res', default='1920x1080')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--manifest', action='store_true')
parser.add_argument('--cam', action='append', default=[])
parser.add_argument('--variant', default='pirate', choices=('pirate', 'merchant'))
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
# The variant: 'pirate' is the Wolf (P) (the skin, its armour over WS 002 / WS 003);
# 'merchant' the base hull with every slot armed, for the convoy's own Wolf. Same
# hull, same scale; separate collection, asset and manifest.
wf_kit.VARIANT = args.variant
wf_mounts.configure(args.variant)
MERCHANT = args.variant == 'merchant'
if MERCHANT:
    COLL, ROOT, NAME_SHOWN = 'WOLF', 'WOLF | root', 'Wolf'
else:
    NAME_SHOWN = NAME
ASSET = 'wolf.blend' if MERCHANT else 'wolf-p.blend'
GEOM_JSON = 'geometry-merchant.json' if MERCHANT else 'geometry.json'
OUT = Path(args.out or HERE.parents[2]/'build'/('wolf' if MERCHANT else 'wolf-p')).resolve()
OUT.mkdir(parents=True, exist_ok=True)


def read_ship():
    import re
    return json.loads(re.sub(r'#.*', '', (GAME/SHIP).read_text(encoding='utf-8')))


def check_against_install():
    """Fail if the vanilla slots, skin or engines no longer match the constants the model uses."""
    if not (GAME/SHIP).exists():
        return 'install not found; constants used as committed'
    ship = read_ship()
    slots = {s['id']: s for s in ship['weaponSlots']}
    import re  # the skin is loose JSON (bare .8, trailing commas): read the one list we need
    found = re.search(r'"removeWeaponSlots"\s*:\s*\[([^\]]*)\]', (GAME/SKIN).read_text(encoding='utf-8'))
    removed = set(re.findall(r'"([^"]+)"', found.group(1))) if found else set()
    if removed != {s['id'] for s in (wf_mounts.PIRATE['removed'])}:
        raise RuntimeError(f'wolf_d_pirates.skin removes {removed}')
    for mine in wf_mounts.PIRATE['slots']+wf_mounts.PIRATE['removed']:
        s = slots[mine['id']]
        theirs = (tuple(s['locations']), s['angle'], s['arc'], s['mount'], s['size'], s['type'])
        ours = (mine['location'], mine['angle'], mine['arc'], mine['mount'], mine['size'], mine['type'])
        if theirs != ours:
            raise RuntimeError(f"{mine['id']} differs from wolf.ship: {theirs} vs {ours}")
    if len(slots) != len(wf_mounts.PIRATE['slots'])+len(wf_mounts.PIRATE['removed']):
        raise RuntimeError('wolf.ship weapon slot count changed')
    engines = [(e['location'][0], e['location'][1], e['width'], e['length']) for e in ship['engineSlots']]
    if engines != [tuple(e) for e in wf_engines.ENGINES]:
        raise RuntimeError(f'engine slots differ from wolf.ship: {engines}')
    if tuple(ship['center']) != (26, 42):
        raise RuntimeError(f"wolf.ship centre changed: {ship['center']}")
    return f'verified against the installed {SHIP} and {SKIN}'


provenance = check_against_install()

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = wf_kit.Kit()
kit.mpu = METRES_PER_UNIT
wf_hull.build(kit)
wf_engines.build(kit)
wf_mounts.build(kit)
wf_lights.build(kit)
wf_detail.build(kit)
bpy.context.view_layer.update()

trained = [n for t in wf_mounts.TURRETS for n in t['trained']
           if bpy.data.objects[n].type == 'MESH']
clash = wf_mounts.clearance(
    trained, [o.name for o in bpy.data.objects if o.type == 'MESH' and (o.name.startswith(('Hull ', 'Pod ')))])
if clash:
    raise RuntimeError(f'turret clashes with the hull at rest: {clash}')


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
        # The hull is flat shaded; the turret kit's guns keep their own smoothing
        # (smooth with sharp creases past 32 degrees).
        if ob.type == 'MESH' and not ob.name.startswith(('Turret ', 'Weapon ')):
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
    print(f'ENCLOSED {measured:.0f} units3 over {SOLIDS}; length {HI.x-LO.x:.1f} units')
    if SCALE is None:
        return {'pending': 'add Wolf (P) to ship_scale.HULLS with this measurement',
                'measured_this_build': {'enclosed_units3': round(measured), 'solids': SOLIDS},
                'length_units': round(HI.x-LO.x, 1), 'size_m_at_placeholder': size}
    table = ship_scale.HULLS[NAME]['units3']
    if abs(measured/table-1) > .02:
        raise RuntimeError(f'enclosed volume {measured:.0f} units3 differs from ship_scale.HULLS '
                           f"{NAME} units3 {table}: update the shared table")
    return {**SCALE, 'published_metres_per_unit': METRES_PER_UNIT,
            'measured_this_build': {'enclosed_units3': round(measured), 'solids': SOLIDS},
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

TARGET = Vector((7, 0, 0))
CAMERAS = [  # name, position, orthographic scale
    ('top', (7, 0, 150), 152),
    ('port-side', (7, 180, 0), 108),
    ('front', (180, 0, 0), 72),
    ('three-quarter', (110, 130, 85), 124),
    ('stern', (-180, 0, 0), 72),
]
CLOSEUPS = [  # name, position, target, ortho scale
    ('bow-detail', (100, 55, 45), (44, 0, 1), 48),
    ('turret-detail', (50, 55, 50), (11, 4, 4), 40),
    ('engine-detail', (-90, 50, 30), (-34, 4, 0), 46),
    ('stern-detail', (-90, -55, 40), (-22, 0, 0), 60),
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

# Shot checks for the Asharu battle (asharu-battle-shot.md): S9's 28 mm at
# 670 m (the close shots S7-S9), S6's 30 mm at 1.5 km, S4's 35 mm at 4 km, all
# on a 36 mm sensor, the hull seen on its port bow quarter from a little above,
# lit only by a low sun from the camera's left (no fill, black sky).
# Perspective is scale-free, so the distance is converted to hull units.
SUN_IN_CAMERA = Vector((-0.70, 0.35, 0.62)).normalized()
SHOTS = [('shot-670m', 670.0, 28), ('shot-1500m', 1500.0, 30), ('shot-4000m', 4000.0, 35)]
view_dir = Vector((-.55, -.78, -.30)).normalized()  # toward the port bow quarter, from above
for name, metres, lens in SHOTS:
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
sun.data.energy = 4.0
sun_world = (bpy.data.objects['Camera shot-670m'].matrix_world.to_3x3() @ SUN_IN_CAMERA).normalized()
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
    # The top view is the IoU silhouette: the hull sprite draws no weapons, and the
    # hardpoint guns stand proud of the bow and pods as the game draws them, so
    # they are left out of it (every other view shows them).
    for ob in bpy.data.objects:
        if ob.name.startswith('Weapon WS ') and ob.type == 'MESH':
            ob.hide_render = name == 'top'
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

if not args.no_save:
    manifest_path = OUT/GEOM_JSON
    old = json.loads((HERE/GEOM_JSON).read_text(encoding='utf-8')) if (HERE/GEOM_JSON).exists() else {}
    manifest = {
        'status': 'Pass 1 blockout: silhouette, sections, slots, drives, anchors, lamps; blockout '
                  'materials in the pirate scheme. Built for the Asharu battle (S7-S9 close, S6 mid, S4 distant).',
        'variant': args.variant,
        'hull': {
            'name': NAME_SHOWN, 'hullId': 'wolf', 'skinHullId': None if MERCHANT else 'wolf_d_pirates', 'size': 'FRIGATE',
            'style': 'HIGH_TECH', 'tech': 'Pirate (skin) on a High Tech hull', 'designation': 'Frigate',
            'livery': 'merchant: an independent convoy escort in the base hull\'s unpainted scheme (wolf_base.png)'
            if MERCHANT else 'pirate: "heavily modified - and poorly maintained - by its pirate crew" (the skin)',
            'source': {'ship': SHIP, 'skin': SKIN, 'sprite': SPRITE, 'base_sprite': BASE_SPRITE,
                       'ship_data': 'data/hulls/ship_data.csv (row id wolf)',
                       'variant': 'data/variants/wolf/wolf_d_pirates_Attack.variant (loadout modelled)'},
            'removed_weapon_slots': [s['id'] for s in wf_mounts.REMOVED],
            'removed_slot_records': wf_mounts.REMOVED,
            'loadout': wf_mounts.LOADOUT,
            'built_as': wf_mounts.BUILT_AS,
            'built_in_mods': [], 'built_in_weapons': [], 'ship_system': 'displacer' if MERCHANT else 'displacer_degraded',
            'shield': 'FRONT 150 deg (not modelled)', 'crew': [15, 30], 'cargo': 50, 'fuel': 20,
        },
        'provenance': provenance,
        'axes': {'+X': 'bow', '+Y': 'port', '+Z': 'dorsal'},
        'units': 'One sprite pixel per unit about the .ship centre; multiply by metres_per_unit for metres',
        'metres_per_unit': METRES_PER_UNIT,
        'scale': scale_record(),
        'sprite': {'size': [52, 96], 'center': [26, 42],
                   'pixel_to_model': 'col c, row r (from the top) covers X 53-r..54-r, Y 25-c..26-c'},
        'dimensions': {'min': [round(v, 2) for v in LO], 'max': [round(v, 2) for v in HI],
                       'size': [round(b-a, 2) for a, b in zip(LO, HI)]},
        'weapon_slots': [dict(s, location=[*s['location'], round(wf_mounts.ANCHOR_Z[s['id']], 2)],
                              anchor=f"Weapon {s['id']} anchor", muzzles=wf_mounts.MUZZLES.get(s['id'], []))
                         for s in wf_mounts.SLOTS],
        'turrets': wf_mounts.TURRETS,
        'hardpoints': wf_mounts.HARDPOINTS,
        'vfx_mounts': wf_mounts.mounts(),
        'engines': wf_engines.records(),
        'running_lights': wf_lights.records(),
        'asset': {'file': ASSET, 'collection': COLL, 'root': ROOT, 'anchors_collection': f'{COLL} | anchors',
                  'baked': 'every modifier applied; no live booleans or bevels',
                  'append': f"bpy.data.libraries.load(path, link=True) and take collection '{COLL}'; "
                            "instance it with a collection-instance empty scaled by metres_per_unit",
                  'triangles': sum(len(p.vertices)-2 for o in meshes for p in o.data.polygons),
                  'objects': len(meshes), 'materials': sorted({m.name for o in meshes for m in o.data.materials})},
        'object_names': {'Hull ...': 'bow fork and tines, head, outriggers, neck, main body, aft body, armour '
                                     'domes, engine block, dorsal spine, ventral keel, and their detail',
                         'Pod <side> missile ...': 'the missile pods round the WS 005 / WS 006 hardpoints',
                         'Engine <i> ...': 'drive can, aft-facing bell, mouth lip, dark throat, exhaust anchor, '
                                           'drive glow anchor',
                         'Turret <i> ...': 'barbette and traverse gap (fixed); gunhouse, mantlet, barrels and '
                                           'Anchor turret <i> barrel <k> muzzle under Turret <i> pivot',
                         'Weapon <slot id> ...': 'hardpoint guns and launchers; anchor, and Anchor weapon <slot id> muzzle',
                         'Light <side> <where> <k>': 'running lights; positions, colours and blink in running_lights'},
        'cameras': [{'name': n, 'position': list(p), 'orthographic_scale': s,
                     'rotation': 'bow left, port below' if n == 'top' else 'looks at '+str(list(TARGET))}
                    for n, p, s in CAMERAS],
        'shot_checks': [{'name': n, 'distance_m': m, 'lens_mm': lens, 'sensor_mm': 36,
                         'view_direction': [round(v, 3) for v in view_dir], 'sun_in_camera': list(SUN_IN_CAMERA)}
                        for n, m, lens in SHOTS],
    }
    for key in ('silhouette', 'shot_extent_px'):
        if key in old:
            manifest[key] = old[key]
    manifest_path.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    if args.manifest:
        shutil.copy(manifest_path, HERE/GEOM_JSON)

    # The clean geometry build for the surfacing harness (../wolf-surface): the
    # same model with the review cameras and studio, never the appendable asset.
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'wolf-geometry.blend'), copy=True, compress=True)
    import wf_export
    importlib.reload(wf_export)
    wf_export.package(OUT/ASSET, METRES_PER_UNIT, coll_name=COLL)
    print('saved', OUT/ASSET)
