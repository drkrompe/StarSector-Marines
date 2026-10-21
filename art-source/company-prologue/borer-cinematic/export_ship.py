"""Export the textured Borer as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../fighters-surface/borer/build/skinned.blend, from apply_skin.py on
the clean geometry) and the geometry's geometry.json. Output, beside this script:
  borer.blend    one collection `BORER` holding the root empty `BORER | root`, which parents every
                 part and anchor; images packed
  manifest.json  metres_per_unit, axes, bounds, the drive, tool, muzzle and cradle-seat anchors,
                 the Shepherd's launch cradles in this asset's terms, the lamps

What the export does to the build, in order (the Broadsword's export is the pattern):
1. drops cameras, lights, the review furniture and the geometry's own anchors (their positions
   and headings are read first and re-declared under the export's names);
2. bakes every mesh's modifiers into a plain mesh under the same object name, keeping materials,
   `skin` UVs and parenting;
3. writes `hull_rest` (vertex position, hull units) and `hull_normal` (face normal, hull space);
4. parents every object to the root;
5. adds the anchors, local +X the effect axis (second-wind-vfx convention):
   - `Engine <i> exhaust anchor` on the three drive mouths, +X down each slot's exhaust heading
     (the side drives splay 20 degrees outboard), `running`, `radius_m`;
   - `Anchor weapon WS 001 muzzle` on the cutting head's lens, +X along the beam (the kit's
     declared-muzzle name; the beams group rides it);
   - `Anchor tool cutting head` at the emitter barrel's root and `Anchor tool grab port|starboard`
     between each pair of grab tines, +X forward: where a shot puts the work;
   - `Anchor cradle seat` on the pad plane under the .ship centre, +X forward: put it on a
     Shepherd `Drone bay WS 003 launch point <i>` with the root scaled by 0.25 / 0.78.
Nothing on the Borer moves (it has no gear, doors or fold), so there is no control empty.
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
SURFACE = PROLOGUE / 'fighters-surface' / 'borer'
GEOMETRY = PROLOGUE / 'fighters-geometry' / 'borer'
SHEPHERD_MPU = .78

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'BORER', 'BORER | root'

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
scene.frame_set(scene.frame_current)
bpy.context.view_layer.update()

# 1. Read the geometry's anchors, then drop them with the review furniture.
src = {}
for ob in bpy.data.objects:
    if ob.type == 'EMPTY' and (ob.name.endswith(' exhaust anchor') or ob.name.startswith('Weapon ')
                               or ob.name == 'Borer cradle seat'):
        src[ob.name] = ob.matrix_world.copy()
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT') or ob.name in src:
        bpy.data.objects.remove(ob)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render
        and not o.name.startswith(('Parked deck plane', 'Scale figure', 'Pilot proxy'))]
for o in [o for o in bpy.data.objects if o.type == 'MESH' and o not in ship]:
    bpy.data.objects.remove(o)

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

# 3. Hull-space attributes at rest.
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
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


old_root = bpy.data.objects.get('Borer root')
root = empty(ROOT, coll, 4, 'ARROWS')
for ob in ship:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    coll.objects.link(ob)
    if ob.parent is None or ob.parent == old_root:
        ob.parent = root          # both roots sit at the hull origin: no inverse needed
if old_root is not None:
    bpy.data.objects.remove(old_root)
bpy.context.view_layer.update()


def declare(name, at, axis, size, **props):
    a = empty(name, anchors_coll, size, 'SINGLE_ARROW', root, Matrix.Translation(Vector(at)) @ frame(axis).to_4x4())
    for k, v in props.items():
        a[k] = v
    return a


# 5a. Drive anchors at the mouths, +X down each slot's exhaust heading.
engines = []
for e in geom['engines']:
    i = e['index']
    name = next(n for n in src if n.startswith(f'Engine {i} ') and n.endswith(' exhaust anchor'))
    mouth, axis = src[name].translation.copy(), Vector(e['exhaust_direction'])
    bell = {6: .22, 4: .175}[e['width']] / MPU
    a = declare(f'Engine {i} exhaust anchor', mouth, axis, bell, running=True, radius_m=bell * MPU,
                slot_width=e['width'], slot_angle=e['angle'])
    engines.append({'index': i, 'side': 'centre' if abs(mouth.y) < 1e-6 else ('port' if mouth.y > 0 else 'starboard'),
                    'anchor': a.name, 'mouth': [round(c, 3) for c in mouth], 'axis': [round(c, 4) for c in axis],
                    'radius': round(bell, 3), 'width': e['width'], 'angle': e['angle'], 'style': 'LOW_TECH',
                    'running': True})
root['engines_running'] = [e['index'] for e in engines]

# 5b. The mining laser's muzzle, and the tool anchors.
lens = src['Weapon WS 001 muzzle'].translation.copy()
muzzle = declare('Anchor weapon WS 001 muzzle', lens, (1, 0, 0), .8, slot='WS 001', weapon='mininglaser',
                 kind='beam')
tools = [declare('Anchor tool cutting head', (6.0, 0.0, lens.z), (1, 0, 0), .8,
                 note='the emitter barrel\'s root inside the shroud; the beam leaves at the WS 001 muzzle')]
for side, sg in (('port', 1), ('starboard', -1)):
    tips = [bbox(bpy.data.objects[f'Pod {side} tine {t}']) for t in ('outer', 'inner')]
    x = max(b[1].x for b in tips)
    y = sum((b[0].y + b[1].y) / 2 for b in tips) / 2
    z = sum((b[0].z + b[1].z) / 2 for b in tips) / 2
    tools.append(declare(f'Anchor tool grab {side}', (x, y, z), (1, 0, 0), .6,
                         note='between the tine tips: where a held piece of rock or salvage sits'))
seat_at = src['Borer cradle seat'].translation.copy()
seat = declare('Anchor cradle seat', seat_at, (1, 0, 0), 1.2, root_scale_in_shepherd=round(MPU / SHEPHERD_MPU, 6),
               note='the pad plane under the .ship centre: place it on a Shepherd "Drone bay WS 003 launch point <i>", '
                    'heading the slot angle (0 deg, +X forward), root scaled by 0.25 / 0.78')

root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['hull_object'] = 'Hull spine'

lamps = []
for ob in ship:
    for sl in ob.material_slots:
        m = sl.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and bsdf.inputs['Emission Strength'].default_value > 0 and any(bsdf.inputs['Emission Color'].default_value[:3]):
            lamps.append({'object': ob.name, 'material': m.name, 'lit': True,
                          'at': [round(c, 2) for c in (bbox(ob)[0] + bbox(ob)[1]) / 2]})
            break

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


def at(ob):
    return [round(c, 3) for c in ob.matrix_world.translation]


cradle = json.loads((GEOMETRY / 'cradle-check.json').read_text())
manifest = {
    'asset': 'borer.blend',
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'state': 'clean',
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units_rest': [[round(c, 2) for c in lo], [round(c, 2) for c in hi]],
    'size_metres_rest': [round((h - l) * MPU, 2) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'tool': {'weapon': 'mininglaser (WS 001, HIDDEN, small energy beam)',
             'muzzle': {'anchor': muzzle.name, 'at': at(muzzle), 'axis': [1, 0, 0]},
             'anchors': [{'anchor': t.name, 'at': at(t)} for t in tools],
             'beam': 'second-wind-vfx beams group, id ws001: dark until beam_t_ws001 is raised (opt-in); '
                     'beam_len_ws001 is the distance to the work in hull units'},
    'cradle_seat': {'anchor': seat.name, 'at': at(seat),
                    'shepherd': {'metres_per_unit': SHEPHERD_MPU, 'root_scale': round(MPU / SHEPHERD_MPU, 6),
                                 'launch_points': [s['launch_point'] for s in cradle['stations']],
                                 'launch_point_anchors': [f'Drone bay WS 003 launch point {s["station"]}'
                                                          for s in cradle['stations']],
                                 'heading_deg': 0,
                                 'checked': 'fighters-geometry/borer_cradle.py: no overlap, pads on the deck, '
                                            'socket over the charging contact, clear lift to the bay mouth'},
                    'pad_soles_z_units': round(seat_at.z, 3)},
    'control': None,
    'lamps_lit': len(lamps),
    'lamps': lamps,
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'effects': None,
    'sources': {'skinned_blend': str(SKINNED.relative_to(PROLOGUE)).replace('\\', '/'),
                'geometry_json_sha256': sha(GEOM_JSON),
                'skin_manifest_sha256': sha(SURFACE / 'skin-manifest.json') if (SURFACE / 'skin-manifest.json').exists() else None},
    'conventions': {
        'root': 'Every part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft and splayed; line of the beam; forward for tools and '
                   'the seat); +Z as near hull-dorsal as that allows.',
        'scale': 'Beside the Shepherd (0.78 m/u) scale the root by 0.25 / 0.78 = 0.3205; in a metre scene by 0.25.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'tools', len(tools), 'lamps', len(lamps))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'borer.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'borer.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
