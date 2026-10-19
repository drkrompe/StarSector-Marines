"""Export the textured Legion (XIV) battlecarrier as one appendable Blender collection.

  blender --background --factory-startup --python export_ship.py -- [--skinned PATH] [--out DIR] [--no-save]

Input is the surfacing build (../legion-surface/build/skinned.blend: apply_skin.py
on the geometry build cut along the tile lines by make_groups.py) and the
geometry's geometry.json. Output, beside this script:
  legion.blend    one collection `LEGION` holding the root empty `LEGION | root`,
                  which parents every ship part and anchor; images packed
  manifest.json   metres_per_unit, axes, bounds, engine / turret / flight-deck
                  anchors, the control knobs, lamps and their blink

What the export does to the build, in order (the Hermes's and Shepherd's
exports, ../hermes-cinematic/ and ../shepherd-cinematic/, are the pattern):
1. drops cameras and lights, and the turret kit's stray recoil empties (a
   childless, unparented duplicate of a recoil group's empty);
2. bakes every mesh's modifiers into a plain mesh under the same object name,
   keeping materials, `skin` UVs and parenting;
3. writes `hull_rest` (vertex position, hull units, at rest: every knob 0)
   and `hull_normal` (face normal, hull space), on single-user meshes (the
   build shares one mesh between some mirrored parts);
4. parents every top-level object to the root, keeping the hierarchy the
   geometry built: each turret's `Weapon <slot> pivot` (trains about local Z;
   rest is the slot angle) carries its house and `Weapon <slot> elevation`
   (elevates about local Y, negative lifts), which carries the guns or
   launcher and the geometry's `Weapon <slot> muzzle anchor <k>`;
5. adds `Anchor turret <i> barrel <k> muzzle` (guns) or `Anchor turret <i>
   tube <k> muzzle` (the squall and hurricane launchers) on every muzzle
   anchor, numbered in weapon_slots order: the VFX kit's barrel-indexed
   declared muzzles. Local +X is the line of fire;
6. replaces the geometry's `Engine <i> exhaust anchor` empties under the same
   names, parented to the root, +X aft, marked `running`, with `radius_m`;
7. keeps the flight decks' mouth and launch-point empties (recorded with their
   launch and recovery directions) and puts each running light's blink on its
   lamp as custom properties;
8. adds the `Legion control` empty and drives every keyable part from its
   knobs: the four blast deflectors, both recovery barriers, the six transfer
   doors, the six traversers and both gantry cranes.
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
SURFACE = PROLOGUE / 'legion-surface'
GEOMETRY = PROLOGUE / 'legion-geometry'

ap = argparse.ArgumentParser()
ap.add_argument('--skinned', default=str(SURFACE / 'build' / 'skinned.blend'))
ap.add_argument('--geometry', default=str(GEOMETRY / 'geometry.json'))
ap.add_argument('--out', default=str(HERE))
ap.add_argument('--no-save', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
SKINNED, GEOM_JSON, OUT = Path(args.skinned).resolve(), Path(args.geometry).resolve(), Path(args.out).resolve()
COLL, ROOT = 'LEGION', 'LEGION | root'
CONTROL = 'Legion control'
HULL_OBJECT = 'Hull deck plating'
LAUNCHERS = ('squall', 'hurricane')        # missile mounts: launch effects, not muzzle flashes
STRAY_RECOIL = re.compile(r'^Weapon WS \d+ recoil \d+$')
# Effect size against the Second Wind's small mount (house half-width about 2.5
# units): ring radius / 2.5, times how much of the ring a barrel's flash spans.
GUN_FLASH = {'vulcan': .45, 'flak': .65, 'heavyac': .7}
GUN_FLASH_DEFAULT = .6
SW_HOUSE = 2.5
MISSILE_BODY = 2.2                          # swfx_guns missile body length, mount units

geom = json.loads(GEOM_JSON.read_text())
MPU = geom['metres_per_unit']

bpy.ops.wm.open_mainfile(filepath=str(SKINNED))
scene = bpy.context.scene
strays = []
for ob in list(bpy.data.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob)
    elif ob.type == 'EMPTY' and STRAY_RECOIL.match(ob.name) and ob.parent is None and not ob.children:
        strays.append(ob.name)
        bpy.data.objects.remove(ob)
scene.frame_set(scene.frame_current)
for p in [o for o in bpy.data.objects if o.name.endswith(' pivot') and o.name.startswith('Weapon ')]:
    slot = next(s for s in geom['weapon_slots'] if s['pivot'] == p.name)
    assert abs(math.degrees(p.rotation_euler.z) - slot['angle']) < 1e-3, f'turret not at rest: {p.name}'
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

# 3. Hull-space attributes at rest. Mirrored parts can share one mesh, and
# hull_rest is per object, so every mesh is made single-user first.
shared = 0
for ob in ship:
    if ob.data.users > 1:
        ob.data = ob.data.copy()
        ob.data.name = ob.name
        shared += 1
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


def rnd(v, n=3):
    return [round(c, n) for c in v]


for ob in [o for o in bpy.data.objects if o.type == 'EMPTY' and o.name.startswith('Engine ')
           and o.name.endswith(' exhaust anchor')]:
    bpy.data.objects.remove(ob)        # re-declared below with the export's keys
movers = [o for o in bpy.data.objects if o.type == 'EMPTY']      # pivots, elevations, recoil, hinges, slides, deck anchors
root = empty(ROOT, coll, 40, 'ARROWS')
for ob in ship + movers:
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    (coll if ob.type == 'MESH' else anchors_coll).objects.link(ob)
for ob in ship + movers:
    if ob.parent is None:
        ob.parent = root          # root sits at the hull origin: no inverse needed
bpy.context.view_layer.update()

# 5. Turrets: the geometry's pivots and elevations, the kit's declared muzzles.
turrets = []
for i, slot in enumerate(geom['weapon_slots'], 1):
    pivot, elev = bpy.data.objects[slot['pivot']], bpy.data.objects[slot['elevation']]
    weapon = slot['weapon']
    kind = 'launcher' if weapon in LAUNCHERS else 'gun'
    pivot['slot'] = slot['id']
    pivot['turret'] = i
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['train_axis'] = 'local Z'
    elev['elevation_axis'] = 'local Y (negative lifts the muzzles)'
    pre = f"Weapon {slot['id']} muzzle anchor "
    geo_muzzles = sorted((o for o in bpy.data.objects if o.name.startswith(pre)), key=lambda o: int(o.name[len(pre):]))
    if kind == 'launcher':
        missile = bpy.data.objects[f"Weapon {slot['id']} missile 1 missile"]
        fire = geo_muzzles[0].matrix_world.to_3x3().col[0].normalized()
        lo, hi = bbox(missile)
        length = max(abs((hi - lo).dot(fire)), max(missile.dimensions))
        scale = round(length / MISSILE_BODY, 3)
    else:
        scale = round(slot['ring_radius'] / SW_HOUSE * GUN_FLASH.get(weapon, GUN_FLASH_DEFAULT), 3)
    muzzles = []
    for k, gm in enumerate(geo_muzzles, 1):
        tag = f"{'tube' if kind == 'launcher' else 'barrel'} {k}"
        mw = gm.matrix_world
        fire = mw.to_3x3().col[0].normalized()
        a = empty(f'Anchor turret {i} {tag} muzzle', anchors_coll, .4 * scale, 'SINGLE_ARROW', gm,
                  Matrix.Translation(mw.translation) @ frame(fire).to_4x4())
        a['scale'] = scale
        a['kind'] = kind
        muzzles.append({'tag': tag, 'anchor': a.name, 'geometry_anchor': gm.name,
                        'at': rnd(mw.translation), 'axis': rnd(fire)})
    turrets.append({'index': i, 'slot': slot['id'], 'weapon': weapon, 'kind': kind, 'size': slot['size'],
                    'type': slot['type'], 'pivot': pivot.name, 'elevation': elev.name,
                    'pivot_at': rnd(pivot.matrix_world.translation), 'elevation_at': rnd(elev.matrix_world.translation),
                    'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'], 'ring_radius': slot['ring_radius'],
                    'elevation_limits_deg': [elev.get('elevation_min_deg'), elev.get('elevation_max_deg')]
                    if 'elevation_min_deg' in elev.keys() else None,
                    'vfx_scale': scale, 'keyable_for_c2': slot.get('keyable_for_c2', False),
                    'muzzles': muzzles})


def ancestors(o):
    out = []
    while o.parent:
        o = o.parent
        out.append(o)
    return out


for t in turrets:
    piv = bpy.data.objects[t['pivot']]
    elv = bpy.data.objects[t['elevation']]
    t['trained_parts'] = sum(1 for o in ship if piv in ancestors(o))
    t['elevating_parts'] = sum(1 for o in ship if elv in ancestors(o))

# 6. Engine anchors at the drive mouths, +X aft.
engines = []
for e in geom['engines']:
    i = e['index']
    mouth = Vector(e['location'])
    axis = Vector(e['exhaust_direction'])
    a = empty(e['anchor'], anchors_coll, e['mouth_radius'], 'SINGLE_ARROW', root,
              Matrix.Translation(mouth) @ frame(axis).to_4x4())
    a['running'] = True
    a['radius_m'] = e['mouth_radius'] * MPU
    a['slot_width'] = e['width']
    engines.append({'index': i, 'anchor': a.name, 'mouth': rnd(mouth), 'axis': list(axis),
                    'radius': e['mouth_radius'], 'width': e['width'], 'style': e['style'], 'running': True})
root['engines_running'] = [e['index'] for e in engines]

# 7. Flight decks and running lights.
decks = []
for fd in geom['flight_decks']:
    mouth = bpy.data.objects[fd['mouth']['anchor']]
    mouth['normal'] = fd['mouth']['normal']
    mouth['clear_width_units'] = fd['mouth']['clear_width']
    mouth['clear_height_units'] = fd['mouth']['clear_height']
    points = []
    for name in fd['launch_point_anchors']:
        lp = bpy.data.objects[name]
        lp['launch_direction'] = fd['launch_direction']
        lp['recovery_approach'] = fd['recovery_approach']
        points.append({'anchor': name, 'at': rnd(lp.matrix_world.translation),
                       'x_axis': rnd(lp.matrix_world.to_3x3().col[0].normalized())})
    decks.append({'slot': fd['slot'], 'side': fd['side'], 'end': fd['end'], 'role': fd['role'],
                  'launch_direction': fd['launch_direction'], 'recovery_approach': fd['recovery_approach'],
                  'mouth': {'anchor': mouth.name, 'centre': rnd(mouth.matrix_world.translation),
                            'normal': fd['mouth']['normal'], 'clear_width': fd['mouth']['clear_width'],
                            'clear_height': fd['mouth']['clear_height']},
                  'launch_points': points, 'blast_deflector': fd['deck']['blast_deflector']})
lamps = []
for rl in geom['running_lights']:
    ob = bpy.data.objects[rl['name']]
    ob['colour'] = rl['colour']
    ob['role'] = rl['role']
    for k, v in rl['blink'].items():
        ob[f'blink_{k}'] = v
    lamps.append({'object': rl['name'], 'colour': rl['colour'], 'rgb': rl['rgb'], 'role': rl['role'],
                  'blink': rl['blink'], 'at': rnd(sum(bbox(ob), Vector()) / 2, 2)})

# 8. Control empty and the keyable parts' drivers.
control = empty(CONTROL, anchors_coll, 8, 'CUBE', root, Matrix.Translation((0, 0, 45)))
knobs, hinges = [], []


def knob(name, lo, hi, desc):
    control[name] = 0.0
    control.id_properties_ui(name).update(min=lo, max=hi, soft_min=lo, soft_max=hi, description=desc)
    knobs.append({'name': name, 'range': [lo, hi], 'description': desc})


def drive(ob, path, index, expr, k):
    fc = ob.driver_add(path, index)
    d = fc.driver
    d.type = 'SCRIPTED'
    v = d.variables.new()
    v.name, v.type = 'k', 'SINGLE_PROP'
    v.targets[0].id_type, v.targets[0].id = 'OBJECT', control
    v.targets[0].data_path = f'["{k}"]'
    d.expression = expr


def g(v):
    return f'{v:.6g}'


keyable = {fd['side']: (fd['keyable'], fd) for fd in geom['flight_decks']}     # one record per wing arm
for fd in geom['flight_decks']:
    slot = fd['slot']
    hinge = bpy.data.objects[fd['deck']['blast_deflector']]
    raise_txt = fd['keyable']['blast_deflectors'][slot]['raise']
    ang = float(re.search(r'by ([+-]?\d+(?:\.\d+)?) deg', raise_txt).group(1))
    name = f"deflector_{slot.replace(' ', '').lower()}"
    knob(name, 0.0, 1.0, f'{slot} blast deflector: 0 flat (rest), 1 raised {abs(ang):g} deg')
    r0 = hinge.rotation_euler[1]
    drive(hinge, 'rotation_euler', 1, f'{g(r0)} + k * {g(math.radians(ang))}', name)
    hinges.append({'object': hinge.name, 'knob': name, 'channel': 'rotation Y', 'at_1': f'{ang:g} deg'})
for side, (rec, fd) in sorted(keyable.items()):
    b = rec['recovery_barrier']
    hinge = bpy.data.objects[b['empty']]
    ang = float(re.search(r'by ([+-]?\d+(?:\.\d+)?) deg', b['raise']).group(1))
    knob(f'barrier_{side}', 0.0, 1.0, f'{side} recovery barrier: 0 stowed flat (rest), 1 raised')
    drive(hinge, 'rotation_euler', 1, f'{g(hinge.rotation_euler[1])} + k * {g(math.radians(ang))}', f'barrier_{side}')
    hinges.append({'object': hinge.name, 'knob': f'barrier_{side}', 'channel': 'rotation Y', 'at_1': f'{ang:g} deg'})
    for door in fd['transfer_doors']:
        slide = bpy.data.objects[door['slide_empty']]
        tz = door['open']['translate_local']
        n = f"transfer_door_{side}_{door['k']}"
        knob(n, 0.0, 1.0, f"{side} transfer door {door['k']}: 0 closed (rest), 1 open (the leaf rises)")
        for ax in range(3):
            if abs(tz[ax]) > 1e-9:
                drive(slide, 'location', ax, f'{g(slide.location[ax])} + k * {g(tz[ax])}', n)
        hinges.append({'object': slide.name, 'knob': n, 'channel': 'location (local)', 'at_1': tz})
    for rail in fd['traverser_rails']:
        slide = bpy.data.objects[rail['traverser_empty']]
        lane, lock = rail['travel_local_y']['to_launch_points'], rail['travel_local_y']['to_lock_centre']
        n = f"traverser_{side}_{rail['door']}"
        knob(n, -1.0, 1.0, f"{side} traverser {rail['door']}: 0 parked at its door, 1 at the launch points, -1 at the lock centre")
        drive(slide, 'location', 1, f'{g(slide.location[1])} + max(k, 0) * {g(lane)} - min(k, 0) * {g(lock)}', n)
        hinges.append({'object': slide.name, 'knob': n, 'channel': 'location Y (local)',
                       'at_1': lane, 'at_minus_1': lock})
    c = rec['gantry_crane']
    slide = bpy.data.objects[c['empty']]
    aft, fwd = c['travel_local_x']
    knob(f'crane_{side}', -1.0, 1.0, f'{side} gantry crane: 0 parked, 1 full travel forward, -1 full travel aft')
    drive(slide, 'location', 0, f'{g(slide.location[0])} + max(k, 0) * {g(fwd)} - min(k, 0) * {g(aft)}', f'crane_{side}')
    hinges.append({'object': slide.name, 'knob': f'crane_{side}', 'channel': 'location X (local)',
                   'at_1': fwd, 'at_minus_1': aft})
bpy.context.view_layer.update()

root['metres_per_unit'] = MPU
root['state'] = 'clean'
root['control'] = CONTROL
root['hull_object'] = HULL_OBJECT
assert HULL_OBJECT in bpy.data.objects

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
    'asset': 'legion.blend',
    'append_collection': COLL,
    'root': ROOT,
    'anchors_collection': anchors_coll.name,
    'state': 'clean',
    'metres_per_unit': MPU,
    'axes': geom['axes'],
    'units': geom['units'],
    'bounds_units_rest': [rnd(lo, 2), rnd(hi, 2)],
    'size_metres_rest': [round((h - l) * MPU, 1) for l, h in zip(lo, hi)],
    'engines': engines,
    'engines_running': root['engines_running'][:],
    'turrets': turrets,
    'flight_decks': decks,
    'control': {'object': CONTROL, 'knobs': knobs, 'drives': hinges, 'saved_pose': {k['name']: 0.0 for k in knobs}},
    'lamps': lamps,
    'objects': len(ship),
    'polygons': polys,
    'baked_meshes': len(baked),
    'unshared_meshes': shared,
    'dropped_stray_empties': strays,
    'images': sorted(i.name for i in bpy.data.images if i.packed_file),
    'effects': None,
    'sources': {'skinned_blend': str(SKINNED.relative_to(PROLOGUE)).replace('\\', '/'),
                'geometry_json_sha256': sha(GEOM_JSON),
                'skin_manifest_sha256': sha(SURFACE / 'skin-manifest.json')},
    'conventions': {
        'root': 'Every ship part and anchor descends from the root; move, rotate or scale the root only.',
        'anchors': 'Local +X is the effect axis (exhaust aft; line of fire); +Z as near hull-dorsal as that allows.',
        'turrets': 'Rotate the turret pivot (Weapon <slot> pivot) about its local Z to train; the rest pose is the '
                   'slot angle. Rotate Weapon <slot> elevation about its local Y to elevate; negative lifts the muzzles.',
        'control': "Animate the knobs on the 'Legion control' empty (control.knobs); all 0 is the built pose: "
                   'deflectors and barriers stowed, doors closed, traversers and cranes parked.',
        'lamps': 'Running-light blink is stored on each lamp (blink_period_s, blink_phase_s, blink_duty); the lens '
                 'materials are shared, so a shot that blinks them keys per-lamp copies.',
        'metric': 'Hull units; multiply by metres_per_unit for metres. Nothing in the asset is in metres.',
    },
}
print('EXPORT', len(ship), 'objects', polys, 'polys; engines', len(engines), 'turrets', len(turrets),
      'muzzles', sum(len(t['muzzles']) for t in turrets), 'launch tubes',
      sum(len(t['muzzles']) for t in turrets if t['kind'] == 'launcher'), 'decks', len(decks), 'lamps', len(lamps),
      'knobs', len(knobs), 'strays', len(strays))
if not args.no_save:
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'legion.blend'), compress=True)
    manifest['asset_sha256'] = sha(OUT / 'legion.blend')
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1) + '\n')
    print('EXPORT saved', OUT)
