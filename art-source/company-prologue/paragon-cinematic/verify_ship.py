"""Structural checks on the Paragon cinematic asset, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py

Two appends into a factory-empty scene, writing validation.json (exit 1 on any
failure):

Ship alone (`PARAGON | clean`):
- the collection, root and anchors collection exist under the manifest's names;
  the root carries metres_per_unit equal to geometry.json's;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; mesh and polygon counts match the manifest;
- ten `Engine <i> exhaust` empties at the slot points, +X along each
  slot's exhaust direction (aft);
- every turret slot has its pivot, elevation and muzzle anchors; training a
  pivot 30 degrees about its local Z swings its muzzles 30 degrees about that
  axis and leaves its barbette fixed; elevating moves the muzzles;
- the two bow tachyon lance hardpoint muzzles point along the bow;
- every kit gun anchor (`Anchor turret <rig> <tag> muzzle`) rides its turret
  kit muzzle at zero offset, the two needler twins tagged port and starboard;
- moving the root (translate, yaw, pitch, scale) moves every part rigidly.

Ship with effects (`PARAGON | clean` + `SWFX` in one load):
- plume, core, haze and glow light on all ten engines; each plume starts at
  its engine's nozzle face (mouth_x) and runs along that engine's exhaust direction, sized to
  its mouth (the core box half-width is 0.62 of the mouth radius);
- one gun rig anchor per kit gun anchor;
- a beam rig on all 17 energy weapons, dark (collapsed) until fired; fired,
  each beam's core runs from its muzzle along the line of fire to its range
  and its impact flare sits at the far end; a beam on a training turret
  swings with it; the lance's charge envelope lights the emitter first;
- engaging a target ahead and one on the port bow (aim.py): the PD lasers
  never fire on the main target, the aft turrets hold fire ahead, and a ray
  along every fired beam's core meets no part of the ship but its own weapon;
- point defence against five missiles: at least four PD lasers engage, each a
  missile inside its 500-unit reach along a clear line, and the missile beyond
  every PD laser's reach is left alone;
- every driver in SWFX is valid;
- moving the root carries every plume with the ship.
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
man = json.loads((HERE / 'manifest.json').read_text())
geom = json.loads((HERE.parent / 'paragon-geometry' / 'geometry.json').read_text())
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


def load(names):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
        dst.collections = names
    for c in dst.collections:
        scene.collection.children.link(c)
    scene.frame_set(1)
    bpy.context.view_layer.update()
    return dst.collections


def upd():
    bpy.context.view_layer.update()


def maxdiff(a, b):
    return max(abs(x - y) for ra, rb in zip(a, b) for x, y in zip(ra, rb))


# ---- ship alone -------------------------------------------------------------
coll = load([man['append_collection']])[0]
root = bpy.data.objects.get(man['root'])
check('root present', root is not None)
check('anchors collection', man['anchors_collection'] in bpy.data.collections)
mpu = root.get('metres_per_unit') if root else None
check('root metres_per_unit', mpu is not None and abs(mpu - geom['metres_per_unit']) < 1e-9
      and abs(mpu - man['metres_per_unit']) < 1e-9, mpu)
objs = list(coll.all_objects)


def under_root(o):
    while o.parent:
        o = o.parent
    return o == root


check('all under root', all(under_root(o) for o in objs), [o.name for o in objs if not under_root(o)][:5])
linked = [f'{c}:{i.name}' for c in ('objects', 'meshes', 'materials', 'images', 'node_groups', 'collections')
          for i in getattr(bpy.data, c) if i.library is not None]
check('nothing linked (all appended)', not linked, linked[:5])
unpacked = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
check('images packed', not unpacked and len(bpy.data.images) > 0, [len(bpy.data.images), unpacked[:5]])
meshes = [o for o in objs if o.type == 'MESH']
check('mesh count', len(meshes) == man['meshes'], [len(meshes), man['meshes']])
polys = sum(len(o.data.polygons) for o in meshes)
check('polygon count', polys == man['polygons'], [polys, man['polygons']])
anchors = {o.name: o for o in objs if o.type == 'EMPTY'}

bad = []
for e in man['engines']:
    a = anchors.get(e['anchor'])
    if a is None:
        bad.append((e['index'], 'missing'))
        continue
    ax = (a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    off = (a.matrix_world.translation.xy - Vector(e['slot_location'])).length
    if ax.dot(Vector(e['exhaust_direction'])) < .999 or ax.x > -.9 or off > 1e-3:
        bad.append((e['index'], [round(c, 3) for c in ax], round(off, 4)))
check('ten engine empties at the slots, pointing aft', not bad and len(man['engines']) == 10, bad)

turrets = [s for s in man['weapon_slots'] if s['mount'] == 'TURRET']
bad, worst = [], 0.0
for s in turrets:
    piv, ele = anchors.get(s['pivot']), anchors.get(s['pivot'].replace('pivot', 'elevation'))
    muz = [anchors.get(n) for n in s['muzzles']]
    barb = bpy.data.objects.get(s['pivot'].replace('pivot', 'barbette'))
    if not (piv and ele and all(muz)):
        bad.append((s['id'], 'missing'))
        continue
    before = [m.matrix_world.translation.copy() for m in muz]
    dirs = [(m.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized() for m in muz]
    fixed = barb.matrix_world.copy() if barb else None
    c, axis = piv.matrix_world.translation.copy(), piv.matrix_world.to_3x3().col[2].normalized()
    basis = piv.matrix_basis.copy()
    piv.matrix_basis = basis @ Matrix.Rotation(math.radians(30), 4, 'Z')
    upd()
    R = Matrix.Rotation(math.radians(30), 3, axis)
    err = max((m.matrix_world.translation - (c + R @ (b - c))).length for m, b in zip(muz, before))
    if fixed is not None:
        err = max(err, maxdiff(barb.matrix_world, fixed))
    piv.matrix_basis = basis
    upd()
    eb = ele.matrix_basis.copy()
    ele.matrix_basis = eb @ Matrix.Rotation(math.radians(-10), 4, 'Y')
    upd()
    # Position or line of fire: a graviton emitter sits on its own trunnion axis.
    # Moved, or turned (degrees / 10): a graviton emitter sits on its own trunnion axis.
    moved = min(max((m.matrix_world.translation - b).length,
                    math.degrees((m.matrix_world.to_3x3() @ Vector((1, 0, 0))).angle(d0)) / 10)
                for m, b, d0 in zip(muz, before, dirs))
    ele.matrix_basis = eb
    upd()
    worst = max(worst, err)
    if err > 1e-3 or moved < .05:
        bad.append((s['id'], round(err, 5), round(moved, 3)))
check(f'{len(turrets)} turret pivots train and elevate', not bad and len(turrets) == 21,
      bad or f'worst train error {worst:.2e}')

bad = []
for s in man['weapon_slots']:
    if s['mount'] == 'HARDPOINT':
        a = anchors.get(s['muzzles'][0])
        if a is None or (a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized().x < .999:
            bad.append(s['id'])
check('bow lance muzzles point forward', not bad and len([s for s in man['weapon_slots'] if s['mount'] == 'HARDPOINT']) == 2, bad)

bad, n = [], 0
for r in man['gun_rigs']:
    tags = sorted(b['tag'] for b in r['barrels'])
    if tags != (['port gun', 'starboard gun'] if r['twin'] else ['gun']):
        bad.append((r['rig'], tags))
    for b in r['barrels']:
        n += 1
        a, s = anchors.get(b['anchor']), anchors.get(b['rides'])
        if not (a and s and a.parent == s and maxdiff(a.matrix_world, s.matrix_world) < 1e-4):
            bad.append((r['rig'], b['anchor']))
check('kit gun anchors ride the turret muzzles', not bad and n == 4 and len(man['gun_rigs']) == 2,
      bad or f'{len(man["gun_rigs"])} rigs, {n} barrels')


ref = {o.name: o.matrix_world.copy() for o in objs}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(.87, 4)
root.matrix_world = M @ root.matrix_world
upd()
rigid = max(maxdiff(o.matrix_world, M @ ref[o.name]) for o in objs)
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))
ship_objects = len(objs)

# ---- ship with effects ---------------------------------------------------------
ship_c, fx_c = load([man['append_collection'], 'SWFX'])
root = bpy.data.objects[man['root']]
fx = list(fx_c.all_objects)
check('SWFX present', len(fx) == man['effects']['objects'], [len(fx), man['effects']['objects']])
P = 'SWFX thrusters'
bad = []
for e in man['engines']:
    i, r = e['index'], e['mouth_radius']
    parts = [bpy.data.objects.get(f'{P} {k} e{i}') for k in ('plume', 'core', 'light')]
    haze = bpy.data.objects.get(f'SWFX smoke haze e{i}')
    if not all(parts) or haze is None:
        bad.append((i, 'missing'))
        continue
    plume, core = parts[0], parts[1]
    mouth, d = Vector((e['mouth_x'], e['slot_location'][1], e['axis_z'])), Vector(e['exhaust_direction'])
    pw = plume.matrix_world
    axis = (pw.to_3x3() @ Vector((1, 0, 0))).normalized()
    # box_volume spans x0..x1 along local +X; its origin sits on the engine anchor.
    origin_off = (pw.translation - mouth).length
    cw = core.matrix_world
    pts = [cw @ Vector(c) for c in core.bound_box]
    half = max(abs((p - cw.translation).dot((cw.to_3x3() @ Vector((0, 0, 1))).normalized())) for p in pts)
    far = max((p - mouth).dot(d) for p in (pw @ Vector(c) for c in plume.bound_box))
    if axis.dot(d) < .999 or origin_off > .5 or abs(half / r - .62) > .08 or far < 5 * r:
        bad.append((i, round(axis.dot(d), 4), round(origin_off, 3), round(half / r, 3), round(far / r, 1)))
check('plumes leave every bell aft, sized to it', not bad, bad)
ga = [o for o in fx if o.name.startswith('SWFX anchor turret ')]
check('gun rig anchors', len(ga) == 4, len(ga))

# Beams: dark until fired; fired, core from muzzle to range along the line of fire.
B = 'SWFX beams'
ctrl = bpy.data.objects['SWFX controls']
bad, dark = [], []
for b in man['beams']:
    core, imp = bpy.data.objects.get(f"{B} {b['id']} core"), bpy.data.objects.get(f"{B} {b['id']} impact flare")
    muz = bpy.data.objects.get(b['muzzle'])
    if not (core and imp and muz):
        bad.append((b['id'], 'missing'))
        continue
    if core.matrix_world.to_scale().x > 1e-3:
        dark.append(b['id'])
    ctrl[f"beam_t_{b['id']}"] = 1.0
    ctrl.update_tag()
    upd()
    m0 = muz.matrix_world.translation
    fire = (muz.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    cw = core.matrix_world
    start, end = cw @ Vector((0, 0, 0)), cw @ Vector((1, 0, 0))
    err = max((start - m0).length, abs((end - start).length - b['range']), 1 - (end - start).normalized().dot(fire))
    ierr = (imp.matrix_world.translation - end).length
    if err > 1e-2 or ierr > 1e-2:
        bad.append((b['id'], round(err, 4), round(ierr, 4)))
    ctrl[f"beam_t_{b['id']}"] = 0.0
check('beams dark until fired', not dark, dark)
check('17 beams run from the muzzle to range along the line of fire', not bad and len(man['beams']) == 17, bad)
# A beam on a training turret swings with it.
s = next(s for s in man['weapon_slots'] if s['id'] == 'WS 005')
piv = bpy.data.objects[s['pivot']]
ctrl['beam_t_ws005'] = 1.0
ctrl.update_tag()
upd()
core = bpy.data.objects[f'{B} ws005 core']
e0 = (core.matrix_world @ Vector((1, 0, 0))) - core.matrix_world.translation
basis = piv.matrix_basis.copy()
piv.matrix_basis = basis @ Matrix.Rotation(math.radians(30), 4, 'Z')
upd()
e1 = (core.matrix_world @ Vector((1, 0, 0))) - core.matrix_world.translation
piv.matrix_basis = basis
ctrl['beam_t_ws005'] = 0.0
ang = math.degrees(e0.angle(e1))
check('a beam trains with its turret', abs(ang - 30) < 1.0, round(ang, 2))
# The lance's charge lights the emitter before the beam.
ctrl['beam_charge_ws001'] = 1.0
ctrl.update_tag()
upd()
fl, co = bpy.data.objects[f'{B} ws001 muzzle flare'], bpy.data.objects[f'{B} ws001 core']
lt = bpy.data.objects[f'{B} ws001 muzzle light']
check('lance charge lights the emitter first', fl.matrix_world.to_scale().x > .1 and co.matrix_world.to_scale().x < 1e-3
      and lt.data.energy > 0, [round(fl.matrix_world.to_scale().x, 3), round(lt.data.energy, 1)])
ctrl['beam_charge_ws001'] = 0.0
ctrl.update_tag()
upd()
inval = []
for o in fx:
    for owner in (o, o.data):
        ad = getattr(owner, 'animation_data', None) if owner is not None else None
        inval += [f'{o.name}:{fc.data_path}' for fc in (ad.drivers if ad else ()) if not fc.driver.is_valid]
    if o.type == 'MESH':
        for m in o.data.materials:
            ad = m.node_tree.animation_data if m and m.node_tree else None
            inval += [f'{m.name}:{fc.data_path}' for fc in (ad.drivers if ad else ()) if not fc.driver.is_valid]
check('drivers valid', not inval, inval[:5])
# Engagement (aim.py): arcs hold the aft turrets off a target ahead, and no beam
# that fires cuts the hull: a ray along each fired core meets only its own weapon.
sys.path.insert(0, str(HERE))
import aim  # noqa: E402
import swfx_beams  # noqa: E402
bad, fired = [], {}
for name, target in (('ahead', (1000, 0, 25)), ('port bow', (850, 500, 200))):
    shots = aim.engage(man, target)
    fired[name] = shots['firing']
    for i in shots['firing']:
        ctrl[f'beam_len_{i}'] = shots['length'][i]
        ctrl[f'beam_t_{i}'] = 1.0
    ctrl.update_tag()
    upd()
    for i in shots['firing']:
        b = next(x for x in man['beams'] if x['id'] == i)
        cw = bpy.data.objects[f'{B} {i} core'].matrix_world
        ok, blocker = swfx_beams.clear_line(cw @ Vector((0, 0, 0)), cw @ Vector((1, 0, 0)),
                                            ignore=(f"Weapon {b['slot']}",))
        if not ok:
            bad.append((name, i, blocker))
        ctrl[f'beam_t_{i}'] = 0.0
pd_ids = {b['id'] for b in man['beams'] if b['pd']}
check('point defence never fires on the main target', pd_ids == {'ws011', 'ws016', 'ws017', 'ws022', 'ws023'}
      and not pd_ids & set(fired['ahead'] + fired['port bow']), sorted(pd_ids))
check('aft turrets hold fire on a target ahead', not {'ws009', 'ws010', 'ws023'} & set(fired['ahead']), fired['ahead'])
# Point defence: each PD laser takes a missile inside its 500-unit reach along a
# clear line; one far beyond every PD laser's reach is left alone.
threats = [Vector(x) for x in ((260, 230, 60), (240, -230, 70), (-290, -190, 50), (-340, 40, 90), (900, -60, 80))]
pd = aim.point_defense(man, threats)
upd()
bad = []
for i, k in pd['firing'].items():
    b = next(x for x in man['beams'] if x['id'] == i)
    muz = bpy.data.objects[b['muzzle']].matrix_world.translation
    reach = (threats[k] - muz).length
    ok, blocker = swfx_beams.clear_line(muz, threats[k], ignore=(f"Weapon {b['slot']}",), short=aim.THREAT_RADIUS)
    if reach > b['range'] or not ok or k == len(threats) - 1:
        bad.append((i, k, round(reach), blocker))
check('point defence engages missiles in reach along clear lines', not bad and len(pd['firing']) >= 4,
      bad or {i: k + 1 for i, k in pd['firing'].items()})
check('no fired beam cuts the hull', not bad and all(fired.values()), bad or {k: len(v) for k, v in fired.items()})
p = bpy.data.objects[f'{P} plume e5']
q = bpy.data.objects[f'{B} ws003 rig'] if f'{B} ws003 rig' in bpy.data.objects else bpy.data.objects[f'{B} ws003']
qb = q.matrix_world.copy()
before = p.matrix_world.copy()
root.matrix_world = M @ root.matrix_world
upd()
err = max(maxdiff(p.matrix_world, M @ before), maxdiff(q.matrix_world, M @ qb))
check('root carries the effects', err < 1e-3, round(err, 6))

result = {'asset': man['asset'], 'collection': man['append_collection'], 'root': man['root'],
          'effects_collection': 'SWFX', 'objects': ship_objects, 'meshes': len(meshes),
          'effects_objects': len(fx), 'images': len(bpy.data.images),
          'passed': not fails, 'failed': fails, 'checks': checks}
(HERE / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
