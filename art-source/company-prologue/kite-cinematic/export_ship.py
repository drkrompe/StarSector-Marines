"""Export the textured Kite (P) as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../kite-surface/build/skinned.blend: apply_skin.py
with the pirate wear, then livery_decal.py) and the geometry's geometry.json.
Output, beside this script:
  kite-p.blend    one collection `KITE (P)` holding the root empty
                  `KITE (P) | root`, which parents every ship part and anchor;
                  images packed (the part skins, swatches and the claw-slash mask)
  manifest.json   metres_per_unit, axes, bounds, engine / RCS / weapon anchors,
                  lamps

What the export does to the build, in order (../hermes-cinematic/export_ship.py
is the pattern; the Kite has no landing gear or doors that move, so no control
empty):
1. drops cameras and lights;
2. bakes every mesh's modifiers into a plain mesh under the same object name;
3. writes `hull_rest` (vertex position, hull units) and `hull_normal` (face
   normal, hull space);
4. parents every top-level object to the root, keeping the turret hierarchy
   (`Weapon WS 003 pivot` trains the gun house and guns; its elevation empty
   lifts the guns; the barbette stays);
5. declares the anchors a shot and the VFX kit read, local +X the effect axis:
   `Engine <i> exhaust anchor` at all eight .ship engine mouths (the wing-tip
   maneuvering jets burn like the rest in the game), `RCS <site> <k> anchor`
   at the eight RCS nozzles, and `Anchor turret <i> <tag> muzzle` for the three
   weapons, numbered as the VFX profile's MOUNTS: 1 the WS 001 Hammer
   (`tube 1|2`), 2 the WS 002 Swarmer (`tube 1..4`), 3 the WS 003 twin light
   machine gun (`barrel 1|2`, riding the trained guns).
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import re
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
SURFACE = PROLOGUE / 'kite-surface'
GEOMETRY = PROLOGUE / 'kite-geometry'

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'KITE (P)', 'KITE (P) | root'
# The VFX profile's MOUNTS order: (slot, tag stem, kind).
WEAPONS = [('WS 001', 'tube', 'launcher'), ('WS 002', 'tube', 'launcher'), ('WS 003', 'barrel', 'gun')]

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
# A loaded file keeps each object's last cached world matrix and re-evaluates only
# what is tagged; the twin MG's copied guns were saved with their originals'
# cache. Tag everything so every matrix read below is the evaluated one.
for ob in bpy.data.objects:
    ob.update_tag(refresh={'OBJECT'})
bpy.context.view_layer.update()
old_engine = re.compile(r'^Engine \d+ exhaust anchor$')
old_rcs = re.compile(r'^RCS (.+) anchor (\d+)$')
rcs_src = []
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and old_engine.match(ob.name):
        bpy.data.objects.remove(ob)        # re-declared below, +X along the exhaust
    elif ob.type == 'EMPTY' and old_rcs.match(ob.name):
        m = old_rcs.match(ob.name)
        rcs_src.append((m.group(1), int(m.group(2)), ob.matrix_world.translation.copy(),
                        (ob.matrix_world.to_3x3() @ Vector((0, 0, 1))).normalized()))
        bpy.data.objects.remove(ob)
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

# 3. Hull-space attributes at rest. They are per object, so a mesh shared between
# objects (the twin MG's second gun is a copy of the first) is made single-user first.
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

# 4. Collection and root.
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
        w = ob.matrix_world.copy()
        ob.parent = parent
        ob.matrix_parent_inverse = parent.matrix_world.inverted()
        ob.matrix_world = w
    return ob


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


movers = [o for o in bpy.data.objects if o.type == 'EMPTY']
root = empty(ROOT, coll, 30, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None:
        ob.parent = root          # root sits at the hull origin: no inverse needed
bpy.context.view_layer.update()

# 5a. Engines: every .ship engine slot, at its mouth.
engines = []
for i, e in enumerate(geom['engines'], 1):
    mouth = Vector((*e['location'], e['z']))
    axis = Vector(e['exhaust_direction'])
    a = empty(f'Engine {i} exhaust anchor', anchors_coll, e['bell_mouth_radius'], 'SINGLE_ARROW', root,
              Matrix.Translation(mouth) @ frame(axis).to_4x4())
    a['running'] = True
    a['radius_m'] = e['bell_mouth_radius'] * MPU
    a['slot_width'] = e['width']
    engines.append({'index': i, 'role': e['role'], 'anchor': a.name, 'mouth': list(mouth), 'axis': list(axis),
                    'radius': e['bell_mouth_radius'], 'width': e['width'], 'running': True})
root['engines_running'] = [e['index'] for e in engines]

# 5b. RCS nozzles.
rcs = []
for site, k, at, normal in sorted(rcs_src):
    a = empty(f'RCS {site} {k} anchor', anchors_coll, .5, 'SINGLE_ARROW', root,
              Matrix.Translation(at) @ frame(normal).to_4x4())
    a['working'] = True
    rcs.append({'anchor': a.name, 'at': [round(c, 3) for c in at], 'exhaust_dir': [round(c, 3) for c in normal],
                'working': True})

# 5c. Weapons: one 'turret' index per weapon, as the VFX profile's MOUNTS.
weapons = []
slots = {s['id']: s for s in geom['weapon_slots']}
for i, (sid, stem, kind) in enumerate(WEAPONS, 1):
    slot = slots[sid]
    muzzles = []
    for k, name in enumerate(slot['muzzles'], 1):
        src = bpy.data.objects[name]
        mw = src.matrix_world
        # The geometry's own muzzle empties: the kit's turret anchors fire along their
        # local +X, the hardpoint launchers' along their local +Z; take whichever lies
        # nearest the slot's line of fire.
        fire_slot = Vector((math.cos(math.radians(slot['angle'])), math.sin(math.radians(slot['angle'])), 0))
        cand = [(mw.to_3x3() @ Vector(v)).normalized() for v in ((1, 0, 0), (0, 0, 1))]
        fire = max(cand, key=lambda v: v.dot(fire_slot))
        a = empty(f'Anchor turret {i} {stem} {k} muzzle', anchors_coll, .6, 'SINGLE_ARROW', src,
                  Matrix.Translation(mw.translation) @ frame(fire).to_4x4())
        muzzles.append({'tag': f'{stem} {k}', 'anchor': a.name, 'rides': src.name,
                        'at': [round(c, 3) for c in mw.translation], 'axis': [round(c, 3) for c in fire]})
    rec = {'index': i, 'slot': sid, 'mount': slot['mount'], 'size': slot['size'], 'weapon': slot['weapon'],
           'kind': kind, 'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'], 'muzzles': muzzles}
    if slot.get('pivot'):
        piv = bpy.data.objects[slot['pivot']]
        piv['train_axis'] = 'hull +Z'
        rec['pivot'] = piv.name
        rec['elevation'] = f'Weapon {sid} elevation'
        rec['pivot_at'] = [round(c, 3) for c in piv.matrix_world.translation]
    weapons.append(rec)

root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['hull_object'] = 'Hull fuselage'

lamps = []
for ob in ship:
    if not (ob.name.startswith('Lamp ') or ob.name.startswith('Cabin window') or ob.name.startswith('Dorsal spotlight lens')):
        continue
    lamps.append({'object': ob.name, 'material': ob.material_slots[0].material.name if ob.material_slots else None,
                  'lit': True, 'at': [round(c, 2) for c in (sum(bbox(ob), Vector()) / 2)]})

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
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


manifest = {
    'asset': 'kite-p.blend',
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'state': 'clean',
    'livery': 'pirate refit (kite-surface: Codex part skins, heavy wear, the sprite\'s red claw slashes)',
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units': [[round(c, 2) for c in lo], [round(c, 2) for c in hi]],
    'size_metres': [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'rcs': rcs,
    'weapons': weapons,
    'boarding': {'collar': 'Hull ventral boarding collar ring', 'hatch': 'Hull ventral boarding hatch',
                 'note': 'the ventral collar mates to the prize\'s hull; it does not move'},
    'lamps_lit': len(lamps),
    'lamps': lamps,
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'effects': None,
    'sources': {'skinned_blend': str(SKINNED.relative_to(PROLOGUE)).replace('\\', '/'),
                'geometry_json_sha256': sha(GEOM_JSON),
                'skin_manifest_sha256': sha(SURFACE / 'skin-manifest.json')},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft; line of fire; RCS outward); +Z as near hull-dorsal as that allows.',
        'turret': "Rotate 'Weapon WS 003 pivot' about its local Z to train (rest: dead ahead, arc 200); "
                  "'Weapon WS 003 elevation' lifts the guns about its local Y (negative lifts).",
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'rcs', len(rcs),
      'weapons', len(weapons), 'muzzles', sum(len(w['muzzles']) for w in weapons), 'lamps', len(lamps))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'kite-p.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'kite-p.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
