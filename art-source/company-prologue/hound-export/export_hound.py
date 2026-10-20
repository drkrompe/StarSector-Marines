"""Export the Hound (A) as an appendable Blender collection.

  blender --background --factory-startup --python export_hound.py -- [--state clean]
      [--skinned PATH] [--out DIR] [--no-effects] [--no-save]

Input is the skinned build (hound-surface: rebuild.sh -> build/skinned.blend,
service wear already on the skins and swatches) and hound-geometry/geometry.json
(scale, slots, engines). Output, under <out>/<state>/ (default beside this script):
  hound-a-<state>.blend   collection `HOUND (A) | <state>` with root empty
                          `HOUND (A) | <state> | root` parenting every part and
                          anchor; images packed. Unless --no-effects, also the
                          VFX kit's `SWFX` collection (second-wind-vfx, swfx.apply)
  manifest.json           metres_per_unit, axes, bounds, engine, weapon/turret and
                          ramp anchors, lamps, effects

Same conventions as second-wind-cinematic/second-wind-states/export_state.py:
cameras and lights dropped; every mesh's modifiers baked into a plain mesh under
the same name (materials, `skin` UVs and wear attributes survive); the root sits
at the hull origin; anchors' local +X is the effect axis (exhaust aft, line of
fire), +Z as near hull-dorsal as that allows. The wear reads Object coordinates,
which ride every part however it is parented, so a moved root, a trained turret
or an opened ramp keep their wear.

Hound specifics:
- WS 002 (small turret, slot angle 145) trains: `Weapon WS 002 pivot` turns about
  hull +Z; everything above the traverse gap rides it, the barbette stays.
- WS 001 (medium hardpoint) is fixed: the turret kit's chaingun on `Weapon WS 001 mount`,
  its parts kept on their own empties (recoil 1, rotor); `Anchor weapon WS 001 muzzle`
  sits on the kit's `Weapon WS 001 muzzle anchor 1`, held by the root. WS 003 (hidden flare system) has its slot
  anchor and no muzzle.
- The aft ventral ramp hangs on `Hull aft ventral ramp hinge` (local Y, negative
  angles lower the aft end; ground_contact_deg on the empty). The leaf, treads,
  stiffeners and toe ride it; frame, hinge barrel, actuators and lights stay.
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
GEOM_JSON = PROLOGUE / 'hound-geometry' / 'geometry.json'
VFX = PROLOGUE / 'second-wind-vfx'

# Liveries: (asset name, surfacing folder, asset file stem, geometry state). The Hegemony
# auxiliary is the original asset, names unchanged. Each livery is its own collection,
# so two can be appended into one scene. The pirate livery is the Hound (P): its
# geometry is hound-geometry's pirate state (armour slabs, chaingun, twin MG, scars).
LIVERIES = {
    'hegemony': ('HOUND (A)', 'hound-surface', 'hound-a', 'clean'),
    'independent': ('HOUND (independent)', 'hound-surface-independent', 'hound-independent', 'clean'),
    'pirate': ('HOUND (P)', 'hound-surface-pirate', 'hound-p', 'pirate'),
}
ap = argparse.ArgumentParser()
ap.add_argument('--state', default='clean', choices=('clean', 'pirate'))   # reserved: battle-damaged, wrecked
ap.add_argument('--livery', default='hegemony', choices=sorted(LIVERIES),
                help='paint scheme; pirate is the Hound (P), whose geometry is the pirate state')
ap.add_argument('--geometry', help='geometry.json of the build the skin was applied to (default: the livery\'s)')
ap.add_argument('--skinned')
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-effects', action='store_true')
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
NAME, SURFACE, STEM, GEOM_STATE = LIVERIES[args.livery]
STATE = GEOM_STATE if args.livery == 'pirate' else args.state
assert STATE == GEOM_STATE, f'the {args.livery} livery is built on the {GEOM_STATE} geometry'
SKINNED = Path(args.skinned or PROLOGUE / SURFACE / 'build' / 'skinned.blend').resolve()
# Output folder: <state>/ for the Hegemony asset (as before), <livery>[-<state>]/ otherwise.
OUT = Path(args.out).resolve() / (STATE if args.livery == 'hegemony' else
                                  args.livery if STATE in ('clean', args.livery) else f'{args.livery}-{STATE}')
COLL = f'{NAME} | {STATE}'
ROOT = f'{COLL} | root'
ASSET = f'{STEM}-{STATE}.blend'
if args.geometry:
    GEOM_JSON = Path(args.geometry).resolve()
elif GEOM_STATE != 'clean':
    GEOM_JSON = PROLOGUE.parents[1] / 'build' / f'{STEM}-geom' / 'geometry.json'   # build_hound.py --state <s> --out
_skin = PROLOGUE / SURFACE / 'skin-manifest.json'
WEAR = json.loads(_skin.read_text())['wear']['state'] if _skin.exists() else 'service'
HULL_OBJECT = 'Hull cargo body'
TURRET = 'WS 002'
RAMP_HINGE = 'Hull aft ventral ramp hinge'
RAMP_PARTS = ('Hull aft ventral ramp', 'Hull aft ventral ramp treads', 'Hull aft ventral ramp stiffeners',
              'Hull aft ventral ramp toe')

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']
SLOTS = {s['id']: s for s in geom['weapon_slots']}

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene

# 1. What ships: meshes; the geometry's own empties become anchors.
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
ship = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
left_out = sorted(o.name for o in bpy.data.objects if o.type == 'MESH' and o.hide_render)
geo_anchors = [o for o in bpy.data.objects if o.type == 'EMPTY']

# 2. Bake modifiers and shape keys.
dg = bpy.context.evaluated_depsgraph_get()
baked = {ob.name: bpy.data.meshes.new_from_object(ob.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
         for ob in ship if ob.modifiers or ob.data.shape_keys}
for ob in ship:
    me = baked.get(ob.name)
    if me is None:
        continue
    overrides = [(s.link, s.material) for s in ob.material_slots]
    ob.modifiers.clear()
    if ob.data.shape_keys:
        ob.shape_key_clear()
    old = ob.data
    ob.data = me
    me.name = ob.name
    for k, (link, mat) in enumerate(overrides[:len(ob.material_slots)]):
        if link == 'OBJECT':
            ob.material_slots[k].link = 'OBJECT'
            ob.material_slots[k].material = mat
    if old.users == 0:
        bpy.data.meshes.remove(old)
for ob in [o for o in bpy.data.objects if o.type == 'MESH' and o not in ship]:
    bpy.data.objects.remove(ob)

# 3. Collection, root.
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
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


def rounded(v, n=3):
    return [round(c, n) for c in v]


root = empty(ROOT, coll, 12, 'ARROWS')
for ob in ship:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    coll.objects.link(ob)
    if ob.parent is None:     # a kit hardpoint's parts keep their own empties (mount, recoil, rotor)
        ob.parent = root      # root sits at the hull origin: no inverse needed
for ob in geo_anchors:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    anchors_coll.objects.link(ob)
    if ob.parent is None:
        reparent(ob, root)
bpy.context.view_layer.update()
root['asset'] = NAME
root['state'] = STATE
root['livery'] = args.livery
root['metres_per_unit'] = MPU
root['hull_object'] = HULL_OBJECT
by_name = {o.name: o for o in ship}

# 4. Weapons. The geometry's slot anchors point along the slot angle.
weapons = []
for slot_id, slot in SLOTS.items():
    anchor = bpy.data.objects[slot['anchor']]
    axis = (anchor.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    want = Vector((math.cos(math.radians(slot['angle'])), math.sin(math.radians(slot['angle'])), 0))
    assert axis.dot(want) > 0.999, (slot_id, axis, want)
    parts = sorted((o for o in ship if o.name.startswith(f'Weapon {slot_id} ')), key=lambda o: o.name)
    rec = {'slot': slot_id, 'mount': slot['mount'], 'size': slot['size'], 'type': slot['type'],
           'slot_anchor': anchor.name, 'at': rounded(anchor.matrix_world.translation), 'angle_deg': slot['angle'],
           'arc_deg': slot['arc'], 'parts': len(parts), 'pivot': None, 'muzzle': None}
    holder = root
    if slot['mount'] == 'TURRET':
        gap = by_name[f'Weapon {slot_id} traverse gap']
        g_lo, _ = bbox(gap)
        ring_lo, ring_hi = bbox(by_name[f'Weapon {slot_id} turret ring'])
        centre = (ring_lo + ring_hi) / 2
        pivot = empty(f'Weapon {slot_id} pivot', anchors_coll, 3.0, 'SINGLE_ARROW', root,
                      Matrix.Translation((centre.x, centre.y, g_lo.z)))
        pivot['rest_angle_deg'] = slot['angle']
        pivot['arc_deg'] = slot['arc']
        pivot['slot'] = slot_id
        pivot['train_axis'] = 'hull +Z'
        trained = [o for o in parts if o is not gap and bbox(o)[0].z >= g_lo.z - 0.05]
        for o in trained + [anchor]:
            reparent(o, pivot)
        holder = pivot
        rec['pivot'] = {'name': pivot.name, 'at': rounded(pivot.matrix_world.translation),
                        'rest_angle_deg': slot['angle'], 'trained_parts': len(trained),
                        'fixed_parts': len(parts) - len(trained)}
        bpy.context.view_layer.update()
    # One muzzle brake, or a twin mount's numbered brakes (`... muzzle brake <k>`,
    # the Hound (P)'s light dual MG), each giving `Anchor weapon <slot> muzzle[ <k>]`.
    brakes = ([(None, by_name[f'Weapon {slot_id} muzzle brake'])] if f'Weapon {slot_id} muzzle brake' in by_name else
              [(k, by_name[f'Weapon {slot_id} muzzle brake {k}']) for k in range(1, 9)
               if f'Weapon {slot_id} muzzle brake {k}' in by_name])
    rec['muzzles'] = []
    # A turret-kit hardpoint (WS 001's chaingun, tk.build_hardpoint) declares its own
    # muzzle empties, '<slot prefix> muzzle anchor <k>' on its mount (fixed: they do not
    # ride the recoil). The declared anchor sits on it, held by the root as the hull's own
    # hardpoint's was, so the VFX kit's flash rig is not counted as part of the mount.
    kit_muzzles = [bpy.data.objects[n] for n in (f'Weapon {slot_id} muzzle anchor {k}' for k in range(1, 9))
                   if n in bpy.data.objects]
    if not brakes and kit_muzzles:
        rec['built_by'] = 'turret-kit build_hardpoint'
        rec['mount_empty'] = f'Weapon {slot_id} mount'
        rec['moving'] = sorted(o.name for o in geo_anchors if o.name in (f'Weapon {slot_id} recoil 1', f'Weapon {slot_id} rotor'))
    for k, km in enumerate(kit_muzzles if not brakes else [], 1):
        tip = km.matrix_world.translation.copy()
        scale = 1.3 if slot['size'] == 'MEDIUM' else 1.0
        a = empty(f'Anchor weapon {slot_id} muzzle' + (f' {k}' if k > 1 else ''), anchors_coll, 1.0, 'SINGLE_ARROW',
                  holder, Matrix.Translation(tip) @ frame(axis).to_4x4())
        a['slot'] = slot_id
        a['scale'] = scale
        rec['muzzles'].append({'anchor': a.name, 'at': rounded(tip), 'axis': rounded(axis), 'scale': scale,
                               'kit_muzzle': km.name})
    for k, brake in brakes:
        pts = [brake.matrix_world @ v.co for v in brake.data.vertices]
        reach = max(p.dot(axis) for p in pts)
        lo, hi = bbox(brake)
        mid = (lo + hi) / 2
        tip = mid + axis * (reach - mid.dot(axis))
        scale = 1.3 if slot['size'] == 'MEDIUM' else 1.0
        a = empty(f'Anchor weapon {slot_id} muzzle' + (f' {k}' if k else ''), anchors_coll, 1.0, 'SINGLE_ARROW',
                  holder, Matrix.Translation(tip) @ frame(axis).to_4x4())
        a['slot'] = slot_id
        a['scale'] = scale
        rec['muzzles'].append({'anchor': a.name, 'at': rounded(tip), 'axis': rounded(axis), 'scale': scale})
    rec['muzzle'] = rec['muzzles'][0] if rec['muzzles'] else None
    weapons.append(rec)

# 5. Engines: the geometry's declared mouth anchors, +X aft.
engines = []
for e in geom['engines']:
    a = bpy.data.objects[e['anchor']]
    axis = a.matrix_world.to_3x3() @ Vector((1, 0, 0))
    assert axis.x < -0.999, (a.name, axis)
    a['running'] = True
    a['bell_radius'] = e['bell_radius']
    a['radius_m'] = round(e['bell_radius'] * MPU, 3)
    a['slot_width'] = e['width']
    side = 'port' if a.matrix_world.translation.y > 0 else 'starboard'
    engines.append({'index': e['index'], 'side': side, 'anchor': a.name, 'slot_width': e['width'],
                    'radius': e['bell_radius'], 'radius_m': a['radius_m'],
                    'mouth': rounded(a.matrix_world.translation), 'axis': rounded(axis),
                    'style': e['style'], 'running': True})
root['engines_running'] = [e['index'] for e in engines]

# 6. Ramp: the leaf rides its hinge.
hinge = bpy.data.objects[RAMP_HINGE]
for n in RAMP_PARTS:
    reparent(by_name[n], hinge)
hinge.empty_display_type, hinge.empty_display_size = 'SINGLE_ARROW', 2.0
ramp = {'hinge': hinge.name, 'at': rounded(hinge.matrix_world.translation), 'open_axis': hinge['open_axis'],
        'ground_contact_deg': hinge['ground_contact_deg'], 'rides_hinge': list(RAMP_PARTS),
        'note': 'Rotate the hinge about local Y; 0 is closed, -ground_contact_deg puts the toe on the ground '
                'the landing pads stand on. Actuators stay with the hull (they do not extend).'}

# 7. Lamps (emissive materials), as export_state.py records them.
lamps = []
for ob in ship:
    for s in ob.material_slots:
        m = s.material
        bsdf = m and m.node_tree and m.node_tree.nodes.get('Principled BSDF')
        if bsdf and any(k in m.name.lower() for k in ('light', 'beacon', 'lamp')):
            lit = bsdf.inputs['Emission Strength'].default_value > 0 and any(bsdf.inputs['Emission Color'].default_value[:3])
            lamps.append({'object': ob.name, 'material': m.name, 'lit': bool(lit),
                          'at': rounded(sum(bbox(ob), Vector()) / 2, 2)})
            break

# Strip everything outside the collection, pack.
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

# 8. Effects: the kit's own apply, appended beside the ship (export_effects.py's pattern).
effects = None
if not args.no_effects:
    sys.path.insert(0, str(VFX))
    import swfx  # noqa: E402
    import swfx_core  # noqa: E402
    groups = ('thrusters', 'smoke', 'guns')
    # The kit's states are the Second Wind's conditions; the Hound (P)'s drives and guns
    # are healthy (its scars are light hull damage), so its effects are the kit's clean ones.
    FX_STATE = 'clean' if STATE == 'pirate' else STATE
    ctx = swfx.apply(state=FX_STATE, groups=groups, rcs=False, dust=False)
    fx = bpy.data.collections[swfx_core.ROOT]
    found = ctx.records['anchors']
    assert len(found['engines']) == len(engines) and len(found['guns']) == sum(len(w['muzzles']) for w in weapons), found

    def summary(rec):
        if isinstance(rec, dict):
            return {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in rec.items()
                    if isinstance(v, (list, dict, int, float, str, bool))}
        return rec

    effects = {'collection': swfx_core.ROOT, 'objects': len(fx.all_objects),
               'kit': f"second-wind-vfx (swfx.apply state '{FX_STATE}', groups {', '.join(groups)}; rcs and landing dust "
                      'off: the Hound has no RCS quads and the dust is the Second Wind\'s; no rig preset)',
               'metres_per_unit_used': swfx_core.METRES_PER_UNIT,
               'anchors': {'engines': len(found['engines']), 'guns': len(found['guns'])},
               'groups': {g: summary(ctx.records.get(g)) for g in groups},
               'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
               'append': f"load both '{COLL}' and '{swfx_core.ROOT}' in one call; the ship alone works without it"}
    for img in bpy.data.images:
        if img.source == 'FILE' and not img.packed_file:
            img.pack()


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


manifest = {
    'asset': ASSET,
    'hull': geom['hull']['name'],
    'skin_hull_id': geom['hull']['skinHullId'],
    'state': STATE,
    'livery': args.livery,
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units': [rounded(lo, 2), rounded(hi, 2)],
    'size_metres': [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'weapons': weapons,
    'turrets': [w for w in weapons if w['pivot']],
    'ramp': ramp,
    'lamps_lit': sum(l['lit'] for l in lamps),
    'lamps': lamps,
    'objects': len(ship),
    'polygons': sum(len(o.data.polygons) for o in ship),
    'baked_meshes': len(baked),
    'left_out_objects': left_out,
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'wear': (f'{SURFACE}/wear.json row `{WEAR}` (wear.py group `SW wear {WEAR}`), on skins and swatches'),
    'effects': effects,
    'sources': {'skinned_blend': str(SKINNED.relative_to(PROLOGUE.parents[1])).replace('\\', '/'),
                'geometry_json': str(GEOM_JSON.relative_to(PROLOGUE.parents[1])).replace('\\', '/'),
                'geometry_json_sha256': sha(GEOM_JSON)},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (drive exhaust aft; gun line of fire); +Z as near hull-dorsal as that allows.',
        'turrets': 'Rotate `Weapon WS 002 pivot` about its local Z to train (rest pose is the slot angle, 145 deg; '
                   'arc 290). Its muzzle anchor and slot anchor ride it.',
        'ramp': 'Rotate the hinge about its local Y; negative lowers the aft end.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', NAME, STATE, len(ship), 'objects', manifest['polygons'], 'polys; baked', len(baked),
      'engines', len(engines), 'muzzles', sum(len(w['muzzles']) for w in weapons), 'lamps', len(lamps),
      'effects', effects and effects['objects'])
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / ASSET), compress=True)
    manifest['asset_sha256'] = sha(OUT / ASSET)
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n', newline='\n')
    print('EXPORT saved', OUT)
