"""Export the textured Mudskipper as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../mudskipper-surface/build/skinned.blend, from
apply_skin.py on the clean geometry in its stowed pose) and the geometry's
geometry.json. Output, beside this script:
  mudskipper.blend   one collection `MUDSKIPPER` holding the root empty
                     `MUDSKIPPER | root`, which parents every ship part and
                     anchor; images packed
  manifest.json      metres_per_unit, axes, bounds, engine / RCS / turret /
                     bay-door / ramp / leg anchors, the landing knobs, lamps

What the export does to the build, in order (the Second Wind's state export,
second-wind-cinematic/second-wind-states/export_state.py, for one clean state):
1. drops cameras and lights;
2. bakes every mesh's modifiers (bevels) into a plain mesh under the same
   object name, keeping materials, `skin` UVs and parenting;
3. writes `hull_rest` (vertex position, hull units, at the rest pose: every
   landing knob 0) and `hull_normal` (face normal, hull space);
4. parents every top-level object to the root, keeping the landing
   hierarchy (leg slides, door-leaf slides, ramp tilt and slide) and the
   'Landing control' empty whose knobs drive it;
5. adds `Turret 1 pivot` (turns about hull +Z; the gun house, mantlet and guns
   ride it, the barbette and collar stay) with `Anchor turret 1 <side> gun
   muzzle` on each barrel; `Engine <i> exhaust anchor` at the three drive bell
   mouths; `RCS <side> maneuvering jet anchor` at the two jets; and landing
   anchors: `Anchor <side> cargo bay door` (opening centre), `Anchor <side>
   freight ramp toe` (rides the ramp) and `Anchor leg <name> sole` (rides the
   foot). Local +X is the effect axis (exhaust, line of fire, outboard,
   down for a sole), the second-wind-vfx convention.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
SURFACE = PROLOGUE / 'mudskipper-surface'
GEOMETRY = PROLOGUE / 'mudskipper-geometry'

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'MUDSKIPPER', 'MUDSKIPPER | root'
CONTROL = 'Landing control'
KNOBS = ('deploy', 'load', 'door_port', 'door_starboard', 'ramp_port', 'ramp_starboard')
FIXED_TURRET_PARTS = ('barbette', 'armoured collar', 'collar bolt cover', 'traverse gap')

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']
landing = geom['landing']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
control = bpy.data.objects[CONTROL]
for k in KNOBS:
    assert float(control[k]) == 0.0, f'the skinned build is not at rest: {k}={control[k]}'
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
bpy.context.scene.frame_set(scene.frame_current)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
left_out = sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and o.hide_render)
for name in left_out:
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

# 3. Hull-space attributes at the rest pose.
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
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


movers = [o for o in bpy.data.objects if o.type == 'EMPTY']      # leg/door/ramp slides, control, slot marker
root = empty(ROOT, coll, 12, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None:
        ob.parent = root          # root sits at the hull origin: no inverse needed
bpy.context.view_layer.update()

# 5a. Turret pivot and muzzles.
slot = next(s for s in geom['weapon_slots'] if s['mount'] == 'TURRET')
parts = [o for o in ship if o.name.startswith('Turret 1 ')]
gap = next(o for o in parts if 'traverse gap' in o.name)
px, py = slot['location']
pz = bbox(gap)[1].z
pivot = empty('Turret 1 pivot', anchors_coll, 2.5, 'SINGLE_ARROW', root, Matrix.Translation((px, py, pz)))
pivot['rest_angle_deg'] = slot['angle']
pivot['slot'] = slot['id']
pivot['train_axis'] = 'hull +Z'
trained = [o for o in parts if not any(k in o.name for k in FIXED_TURRET_PARTS)]
for o in trained:
    reparent(o, pivot)
bpy.context.view_layer.update()
fire = Vector((math.cos(math.radians(slot['angle'])), math.sin(math.radians(slot['angle'])), 0))
muzzles = []
for side in ('port', 'starboard'):
    bore = next(o for o in parts if o.name.startswith(f'Turret 1 {slot["id"]} {side} gun bore'))
    tip = bore.matrix_world.translation + fire * .02
    a = empty(f'Anchor turret 1 {side} gun muzzle', anchors_coll, .6, 'SINGLE_ARROW', pivot,
              Matrix.Translation(tip) @ frame(fire).to_4x4())
    muzzles.append({'tag': f'{side} gun', 'anchor': a.name, 'at': [round(c, 3) for c in tip],
                    'axis': [round(c, 3) for c in fire]})
turrets = [{'index': 1, 'slot': slot['id'], 'size': slot['size'], 'pivot': pivot.name,
            'pivot_at': [px, py, round(pz, 3)], 'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
            'trained_parts': len(trained), 'fixed_parts': len(parts) - len(trained), 'muzzles': muzzles}]

# 5b. Drive and jet anchors, at the slot mouths (the geometry's own contract).
engines, rcs = [], []
for i, e in enumerate(geom['engines'], 1):
    mouth = Vector((*e['location'], e['z']))
    axis = Vector(e['exhaust_direction'])
    if e['role'].endswith('drive'):
        a = empty(f'Engine {i} exhaust anchor', anchors_coll, e['bell_mouth_radius'], 'SINGLE_ARROW', root,
                  Matrix.Translation(mouth) @ frame(axis).to_4x4())
        a['running'] = True
        a['radius_m'] = e['bell_mouth_radius'] * MPU
        engines.append({'index': i, 'role': e['role'], 'anchor': a.name, 'mouth': list(mouth), 'axis': list(axis),
                        'radius': e['bell_mouth_radius'], 'width': e['width'], 'running': True})
    else:
        side = 'port' if e['location'][1] > 0 else 'starboard'
        a = empty(f'RCS {side} maneuvering jet anchor', anchors_coll, e['bell_mouth_radius'], 'SINGLE_ARROW', root,
                  Matrix.Translation(mouth) @ frame(axis).to_4x4())
        a['working'] = True
        a['radius_m'] = e['bell_mouth_radius'] * MPU
        rcs.append({'anchor': a.name, 'engine_slot': i, 'at': list(mouth), 'exhaust_dir': list(axis),
                    'radius_units': e['bell_mouth_radius'], 'working': True})
root['engines_running'] = [e['index'] for e in engines]

# 5c. Landing anchors.
bay = []
for s, side in ((1, 'Port'), (-1, 'Starboard')):
    a = empty(f'Anchor {side} cargo bay door', anchors_coll, 2.0, 'SINGLE_ARROW', root,
              Matrix.Translation((10, s * 22.5, -.55)) @ frame(Vector((0, s, 0))).to_4x4())
    slide = bpy.data.objects[f'{side} freight ramp slide']
    deck = bpy.data.objects[f'{side} freight ramp deck']
    lo, hi = bbox(deck)
    toe_y = hi.y if s > 0 else lo.y
    t = empty(f'Anchor {side} freight ramp toe', anchors_coll, 1.5, 'SINGLE_ARROW', slide,
              Matrix.Translation(((lo.x + hi.x) / 2, toe_y, hi.z)) @ frame(Vector((0, s, 0))).to_4x4())
    bay.append({'side': side.lower(), 'door_anchor': a.name, 'opening_units': [16.8, 8.4],
                'opening_m': [round(16.8 * MPU, 2), round(8.4 * MPU, 2)],
                'door_knob': f'door_{side.lower()}', 'ramp_knob': f'ramp_{side.lower()}',
                'leaf_slides': [f'{side} cargo bay door {leaf} leaf slide' for leaf in ('aft', 'forward')],
                'ramp_tilt': f'{side} freight ramp tilt', 'ramp_slide': slide.name, 'ramp_toe_anchor': t.name})
soles = []
for leg in landing['legs']:
    sole = bpy.data.objects[f'Leg {leg["name"]} sole']
    stage = bpy.data.objects[leg['stage_2_slide']]
    lo, hi = bbox(sole)
    a = empty(f'Anchor leg {leg["name"]} sole', anchors_coll, 1.0, 'SINGLE_ARROW', stage,
              Matrix.Translation(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z)) @ frame(Vector((0, 0, -1)), Vector((1, 0, 0))).to_4x4())
    soles.append(a.name)

root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['landing_control'] = CONTROL

lamps = []
for ob in ship:
    for sl in ob.material_slots:
        m = sl.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and bsdf.inputs['Emission Strength'].default_value > 0 and any(bsdf.inputs['Emission Color'].default_value[:3]) \
                and 'glazing' not in m.name.lower() and 'skylight' not in m.name.lower():
            lamps.append({'object': ob.name, 'material': m.name, 'lit': True,
                          'at': [round(c, 2) for c in (sum(bbox(ob), Vector()) / 2)]})
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
for ob in bpy.data.objects:
    for fc in (ob.animation_data.drivers if ob.animation_data else ()):
        fc.driver.is_valid = True
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
    'asset': 'mudskipper.blend',
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'state': 'clean',
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units_rest': [[round(c, 2) for c in lo], [round(c, 2) for c in hi]],
    'size_metres_rest': [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'rcs': rcs,
    'turrets': turrets,
    'bay_doors': bay,
    'landing': dict(landing, sole_anchors=soles, saved_pose={k: 0.0 for k in KNOBS}),
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
        'anchors': 'Local +X is the effect axis (exhaust aft; line of fire; outboard for doors and ramp toes; down for leg soles); +Z as near hull-dorsal as that allows.',
        'turrets': 'Rotate Turret 1 pivot about its local Z to train; the rest pose is the slot angle (trained aft).',
        'landing': "Animate the knobs on the 'Landing control' empty (deploy, load, door_<side>, ramp_<side>; 0..1). "
                   'Landed (deploy 1, load 1) the ground is at landing.ground_z_landed in hull space: '
                   'place the root that far above the ground plane. Ramps solve their own angle to that ground.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'rcs', len(rcs), 'lamps', len(lamps))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'mudskipper.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'mudskipper.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
