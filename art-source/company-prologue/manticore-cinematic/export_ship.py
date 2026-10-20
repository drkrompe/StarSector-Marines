"""Export the textured Manticore (P) as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--in PATH] [--state S] [--out DIR] [--no-save]

Input is the surfacing build (../manticore-surface/build/skinned.blend:
apply_skin.py with the battle wear, then livery_decal.py; `--in` takes any
build with the geometry's object names, e.g. a clean
../manticore-geometry/build_manticore.py output) and the geometry's
geometry.json. `--state` is the condition recorded on the root and in the
manifest: `battle` (the default, the surfacing's wear) or `clean`.
Output:
  build/export/manticore-p.blend  one collection `MANTICORE (P)` holding the root
                  empty `MANTICORE (P) | root`, which parents every ship part and
                  anchor; images packed (ignored; the build goes to Drive)
  manifest.json   beside this script (tracked): metres_per_unit, axes, bounds,
                  drive / turret / launcher / system-slot anchors, lamps

What the export does to the build, in order (../kite-cinematic/export_ship.py
is the pattern; the Manticore has no moving gear, so no control empty):
1. drops cameras and lights;
2. bakes every mesh's modifiers into a plain mesh under the same object name;
3. writes `hull_rest` (vertex position, hull units) and `hull_normal` (face
   normal, hull space);
4. moves everything into the new collection under the new root, retiring the
   geometry's own `MANTICORE` collection and `MANTICORE | root`, keeping the
   turret hierarchy (`Weapon WS 00x pivot` trains the house and gun; its
   `elevation` empty lifts the gun; the barbette stays);
5. declares the anchors a shot and the VFX kit read, local +X the effect axis:
   `Engine <i> exhaust anchor` at the five drive mouths (re-declared: the
   geometry's are turned 180 about Z, which is right, but carry no radius);
   the muzzles are the geometry's own `Anchor turret <i> barrel|tube <k>
   muzzle` (mt_mounts, numbered as the VFX profile's MOUNTS: 1 the WS 000
   Hellbore, 2/4 the WS 001/003 light guns, 3 the WS 002 twin needler, 5/6 the
   WS 004/005 Harpoon pods, three tubes each); the hidden canister-flak slots
   keep `Weapon WS 006..009 anchor`.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import subprocess
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
SURFACE = PROLOGUE / 'manticore-surface'
GEOMETRY = PROLOGUE / 'manticore-geometry'

ap = argparse.ArgumentParser()
ap.add_argument('--in', dest='src', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--state', default='battle', choices=('battle', 'clean'))
ap.add_argument('--out', default=str(HERE / 'build' / 'export'))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SRC, GEOM_JSON, OUT = Path(args.src).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'MANTICORE (P)', 'MANTICORE (P) | root'
OLD_COLL, OLD_ROOT = 'MANTICORE', 'MANTICORE | root'
ASSET = 'manticore-p.blend'
# Lamps: the hull has no lights layer, so these are the geometry's (mt_lights:
# four running lamps, each an empty at the lens plus a '<name> lens' mesh) and
# the two mast beacons (mt_refine: the bridge light pole and the aft mast).
LAMPS = [('Light port wing tip 1', 'red', 'port side light (extremity)'),
         ('Light starboard wing tip 1', 'green', 'starboard side light (extremity)'),
         ('Light centre stern 1', 'white', 'stern light'),
         ('Light centre prow 1', 'white', 'prow anti-collision strobe'),
         ('Bridge mast beacon 1', 'red', 'mast beacon (bridge light pole)'),
         ('Bridge mast beacon 2', 'red', 'mast beacon (aft mast)')]

geom = json.loads(GEOM_JSON.read_text(encoding='utf-8'))
MPU = geom['metres_per_unit']

bpy.ops.wm.open_mainfile(filepath=str(SRC))
scene = bpy.context.scene
# A loaded file keeps each object's last cached world matrix; tag everything so
# every matrix read below is the evaluated one.
for ob in bpy.data.objects:
    ob.update_tag(refresh={'OBJECT'})
bpy.context.view_layer.update()
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and ob.name.endswith(' exhaust anchor'):
        bpy.data.objects.remove(ob)        # re-declared below with radius and keys
scene.frame_set(scene.frame_current)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
for name in sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and o.hide_render):
    bpy.data.objects.remove(bpy.data.objects[name])

# 2. Bake modifiers under the same object names.
dg = bpy.context.evaluated_depsgraph_get()
baked = {}
for ob in ship:
    if ob.modifiers:
        baked[ob.name] = bpy.data.meshes.new_from_object(ob.evaluated_get(dg), preserve_all_data_layers=True,
                                                         depsgraph=dg)
for ob in ship:
    me = baked.get(ob.name)
    if me is None:
        continue
    overrides = [(s.link, s.material) for s in ob.material_slots]
    ob.modifiers.clear()
    old = ob.data
    ob.data = me
    me.name = ob.name
    for k, (link, mat) in enumerate(overrides[:len(ob.material_slots)]):
        if link == 'OBJECT':
            ob.material_slots[k].link = 'OBJECT'
            ob.material_slots[k].material = mat
    if old.users == 0:
        bpy.data.meshes.remove(old)
bpy.context.view_layer.update()

# 3. Hull-space attributes at rest; a shared mesh is made single-user first.
for ob in ship:
    if ob.data.users > 1:
        ob.data = ob.data.copy()
for ob in ship:
    me, mw = ob.data, ob.matrix_world
    nm = mw.to_3x3().inverted_safe().transposed()
    rest = me.attributes.get('hull_rest') or me.attributes.new('hull_rest', 'FLOAT_VECTOR', 'POINT')
    rest.data.foreach_set('vector', [c for v in me.vertices for c in (mw @ v.co)])
    hn = me.attributes.get('hull_normal') or me.attributes.new('hull_normal', 'FLOAT_VECTOR', 'FACE')
    hn.data.foreach_set('vector', [c for p in me.polygons for c in (nm @ p.normal).normalized()])

# 4. Collection and root. The geometry's root sits at the hull origin with an
# identity transform, so its children move onto the new root unchanged.
coll = bpy.data.collections.new(COLL)
scene.collection.children.link(coll)
anchors_coll = bpy.data.collections.new(f'{COLL} | anchors')
coll.children.link(anchors_coll)


def empty(name, where, size=1.0, kind='PLAIN_AXES', parent=None, matrix=None):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type, ob.empty_display_size = kind, size
    where.objects.link(ob)
    ob.matrix_world = matrix if matrix is not None else Matrix.Identity(4)
    if parent is not None:
        reparent(ob, parent)
    return ob


def reparent(ob, parent):
    w = ob.matrix_world.copy()
    ob.parent = parent
    ob.matrix_parent_inverse = parent.matrix_world.inverted()
    ob.matrix_world = w


def frame(axis, up=Vector((0, 0, 1))):
    """+X along axis, +Z as near hull-dorsal as that allows (second-wind-vfx convention)."""
    x = Vector(axis).normalized()
    z = up - x * x.dot(up)
    z = z.normalized() if z.length > 1e-6 else Vector((0, 1, 0))
    return Matrix((x, z.cross(x), z)).transposed()


def bbox(ob):
    pts = [ob.matrix_world @ v.co for v in ob.data.vertices] if ob.type == 'MESH' and len(ob.data.vertices) else \
        [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


def r3(v, n=3):
    return [round(c, n) for c in v]


old_root = bpy.data.objects.get(OLD_ROOT)
movers = [o for o in bpy.data.objects if o.type == 'EMPTY' and o is not old_root]
root = empty(ROOT, coll, 30, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None or ob.parent is old_root:
        reparent(ob, root)
if old_root is not None:
    root['source'] = old_root.get('source', '')
    bpy.data.objects.remove(old_root)
for c in [c for c in bpy.data.collections if c not in (coll, anchors_coll)]:
    bpy.data.collections.remove(c)
bpy.context.view_layer.update()

# 5a. Drives: every .ship engine slot, at its bell mouth (mt_engines: the slot
# point is the mouth plane, mouth radius 0.30 x slot width).
engines = []
for e in geom['engines']:
    i = e['index']
    mouth = Vector(e['location'])
    axis = Vector(e['exhaust_direction'])
    a = empty(f'Engine {i} exhaust anchor', anchors_coll, e['mouth_radius'], 'SINGLE_ARROW', root,
              Matrix.Translation(mouth) @ frame(axis).to_4x4())
    a['running'] = True
    a['radius_m'] = e['mouth_radius'] * MPU
    a['slot_width'] = e['width']
    a['style'] = e['style']
    side = 'port' if mouth.y > 1 else 'starboard' if mouth.y < -1 else 'centre'
    engines.append({'index': i, 'slot': e['slot'], 'role': f"{'main' if e['width'] >= 16 else 'outer'} drive, {side}",
                    'anchor': a.name, 'mouth': r3(mouth), 'axis': r3(axis), 'radius': e['mouth_radius'],
                    'radius_m': round(e['mouth_radius'] * MPU, 3), 'width': e['width'], 'length': e['length'],
                    'style': e['style'], 'running': True})
root['engines_running'] = [e['index'] for e in engines]


# 5b. Turrets and launchers: the geometry's pivots, elevations and declared muzzles.
def muzzle_records(t):
    out = []
    for m in t['muzzles']:
        a = bpy.data.objects[m['anchor']]
        mw = a.matrix_world
        out.append({'tag': m['tag'], 'anchor': a.name, 'rides': a.parent.name if a.parent else None,
                    'at': r3(mw.translation), 'axis': r3((mw.to_3x3() @ Vector((1, 0, 0))).normalized(), 4)})
    return out


def descendants(ob):
    return [o for o in ob.children_recursive if o.type == 'MESH']


turrets, launchers = [], []
for t in geom['turrets']:
    piv = bpy.data.objects[t['pivot']]
    elev = bpy.data.objects[t['elevation']]
    piv['train_axis'] = 'local Z (hull +Z)'
    rec = {'index': t['index'], 'slot': t['slot'], 'mount': t['mount'], 'size': t['size'], 'weapon': t['weapon'],
           'variant_weapon': t['variant_weapon'], 'kind': t['kind'], 'pivot': piv.name, 'elevation': elev.name,
           'pivot_at': r3(piv.matrix_world.translation), 'rest_angle_deg': t['rest_angle_deg'],
           'arc_deg': t['arc_deg'], 'vfx_scale': t['vfx_scale'], 'trainable': t['trainable'],
           'trained_parts': len(descendants(piv)), 'muzzles': muzzle_records(t)}
    barbette = bpy.data.objects.get(f"Weapon {t['slot']} barbette")
    if barbette is not None:
        rec['barbette'] = barbette.name
    if t['kind'] == 'launcher':
        rec['note'] = 'hardpoint: never trains (arc 5); one launch anchor per tube, +X out of the tube'
        launchers.append(rec)
    else:
        if t['weapon'] == 'hellbore':
            rec['note'] = 'main gun: the launch/muzzle anchor is the Hellbore barrel tip'
        turrets.append(rec)

# 5c. Hidden system slots (the canister-flak system): anchors only.
systems = []
for s in geom['weapon_slots']:
    if s['mount'] != 'HIDDEN':
        continue
    a = bpy.data.objects[s['anchor']]
    systems.append({'slot': s['id'], 'anchor': a.name, 'angle_deg': s['angle'], 'at': r3(a.matrix_world.translation),
                    'axis': r3((a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized(), 4)})

root['metres_per_unit'] = MPU
root['state'] = args.state
root['hull_object'] = 'Hull core'
root['axes'] = '+X bow, +Y port, +Z dorsal; hull units'

lamps = []
rl = {l['name']: l for l in geom['running_lights']}
for name, colour, role in LAMPS:
    lens = bpy.data.objects.get(f'{name} lens') or bpy.data.objects.get(name)
    if lens is None or lens.type != 'MESH':
        continue
    anchor = bpy.data.objects.get(name) if name in rl else None
    at = anchor.matrix_world.translation if anchor is not None else sum(bbox(lens), Vector()) / 2
    lamps.append({'object': lens.name, 'anchor': anchor.name if anchor else None, 'colour': colour, 'role': role,
                  'material': lens.material_slots[0].material.name if lens.material_slots else None,
                  'blink': rl[name]['blink'] if name in rl else None, 'lit': True, 'at': r3(at, 2)})

# Strip everything outside the collection, pack, save.
keep = set(coll.all_objects)
for ob in list(bpy.data.objects):
    if ob not in keep:
        bpy.data.objects.remove(ob)
bpy.data.orphans_purge(do_recursive=True)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
external = [img.name for img in bpy.data.images if img.source == 'FILE' and not img.packed_file]
assert not external, external
bpy.context.view_layer.update()

lo = Vector((1e9,) * 3)
hi = Vector((-1e9,) * 3)
for ob in ship:
    a, b = bbox(ob)
    lo, hi = Vector(map(min, lo, a)), Vector(map(max, hi, b))
polys = sum(len(o.data.polygons) for o in ship)


def sha(p):
    p = Path(p)
    return hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None


def rel(p):
    try:
        return str(Path(p).relative_to(PROLOGUE)).replace('\\', '/')
    except ValueError:
        return str(p).replace('\\', '/')


def source_commit():
    """HEAD, marked '-dirty' when the geometry, surface or this package has local edits; None outside git."""
    try:
        head = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=HERE, capture_output=True, text=True,
                              check=True).stdout.strip()
        dirty = subprocess.run(['git', 'status', '--porcelain', '--', str(GEOMETRY), str(SURFACE), str(HERE)],
                               cwd=HERE, capture_output=True, text=True, check=True).stdout.strip()
        return head + ('-dirty' if dirty else '')
    except (OSError, subprocess.CalledProcessError):
        return None


manifest = {
    'asset': ASSET,
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'state': args.state,
    'livery': 'pirate (manticore-surface: Codex part skins, battle wear, the sprite\'s markings)'
              if args.state == 'battle' else 'geometry\'s light procedural livery (no surfacing skin)',
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units_rest': [r3(lo, 2), r3(hi, 2)],
    'size_metres_rest': [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'turrets': turrets,
    'launchers': launchers,
    'system_slots': systems,
    'lamps_lit': len(lamps),
    'lamps': lamps,
    'lights_layer': 'none: manticore_pirate.png has no lights layer; the lamps are the geometry\'s four running '
                    'lamps (mt_lights) and the two red mast beacons (mt_refine)',
    'rcs': 'none declared: the RCS quads (mt_refine) are detail meshes without anchors; the effects build passes rcs=False',
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'effects': None,
    'source_commit': source_commit(),
    'sources': {'blend': rel(SRC),
                'geometry_json_sha256': sha(GEOM_JSON),
                'surface_wear_sha256': sha(SURFACE / 'wear.json'),
                'surface_palette_sha256': sha(SURFACE / 'palette.json')},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft; line of fire; out of the tube); +Z as near '
                   'hull-dorsal as that allows.',
        'turrets': "Rotate 'Weapon WS 00x pivot' about its local Z (hull +Z) to train, within arc_deg about the "
                   "rest angle (the slot angle, the saved pose); 'Weapon WS 00x elevation' lifts the gun about its "
                   "local Y (negative lifts). Turrets 1-4 train; the Harpoon pods (5, 6) are fixed hardpoints.",
        'engines': 'Engines 1-3 are the main drives (port, centre, starboard), 4/5 the outer drives (port, '
                   'starboard); with the effects collection each has its own throttle, thr_throttle_e<i>.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'turrets', len(turrets),
      'launchers', len(launchers), 'muzzles', sum(len(w['muzzles']) for w in turrets + launchers), 'lamps', len(lamps),
      'state', args.state)
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / ASSET), compress=True)
    manifest['asset_path'] = rel(OUT / ASSET)
    manifest['asset_sha256'] = sha(OUT / ASSET)
    (HERE / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT / ASSET)
