"""Structural checks on the exported Manticore (P), appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py -- [--blend PATH]

Appends `MANTICORE (P)` and the optional `SWFX` collection into a factory-empty
scene and checks, writing validation.json (exit 1 on any failure; every check
prints a `VERIFY` line, every failure a `BAD` line):
- the collection, root and anchors collection exist under the manifest's names;
  the root carries metres_per_unit 0.59 and the manifest's state;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest; the
  rest bounds match it;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- five drive anchors at their mouths, +X along the exhaust (aft), running, with
  the manifest's radius;
- every muzzle and launch anchor at its manifest place and axis; at rest each
  turret's pivot bears its slot angle; the hidden system anchors bear theirs;
- each lamp at its manifest place;
- each trainable turret: turning its pivot 30 degrees swings its muzzles 30
  degrees about it and moves its gun, the barbette stays; lifting its elevation
  raises the muzzles; the Harpoon tubes stay with the hull meanwhile;
- every SWFX object but the kit's controls descends from the ship's root; the
  kit built five drive rigs with per-engine throttles, a rig per muzzle and a
  light per lamp;
- moving the root (translate, yaw, pitch, scale) moves every part rigidly.
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--blend', default=str(HERE / 'build' / 'export' / 'manticore-p.blend'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
man = json.loads((HERE / 'manifest.json').read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(Path(args.blend).resolve()), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX']
for c in dst.collections:
    if c is not None:
        scene.collection.children.link(c)
coll, fx = dst.collections
bpy.context.view_layer.update()
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


def top(o):
    while o.parent:
        o = o.parent
    return o


def x_axis(ob):
    return (ob.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()


def far(a, b, tol=1e-3):
    return (Vector(a) - Vector(b)).length > tol


root = bpy.data.objects.get(man['root'])
check('root present', root is not None and coll is not None)
check('anchors collection', man['anchors_collection'] in bpy.data.collections)
check('metres_per_unit on root', root is not None and abs(root.get('metres_per_unit', 0) - man['metres_per_unit']) < 1e-9
      and abs(man['metres_per_unit'] - .59) < 1e-9, root.get('metres_per_unit') if root else None)
check('state on root', root is not None and root.get('state') == man['state'], root.get('state') if root else None)
objs = list(coll.all_objects)
check('all under root', all(top(o) == root for o in objs), [o.name for o in objs if top(o) != root][:5])
check('effects collection present', fx is not None)
fx_objs = list(fx.all_objects) if fx else []
loose_fx = [o.name for o in fx_objs if top(o) != root and o.name != 'SWFX controls']
check('effects ride the ship', fx_objs and not loose_fx, loose_fx[:5])
linked = [f'{c}:{i.name}' for c in ('objects', 'meshes', 'materials', 'images', 'node_groups', 'collections')
          for i in getattr(bpy.data, c) if i.library is not None]
check('nothing linked (all appended)', not linked, linked[:5])
unpacked = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
check('images packed', not unpacked, unpacked)
meshes = [o for o in objs if o.type == 'MESH']
check('object count', len(meshes) == man['objects'], [len(meshes), man['objects']])
check('polygon count', sum(len(o.data.polygons) for o in meshes) == man['polygons'])
lo = Vector((1e9,) * 3)
hi = Vector((-1e9,) * 3)
for o in meshes:
    pts = [o.matrix_world @ v.co for v in o.data.vertices] or [o.matrix_world @ Vector(c) for c in o.bound_box]
    lo, hi = Vector(map(min, lo, *pts)), Vector(map(max, hi, *pts))
check('rest bounds', not far(lo, man['bounds_units_rest'][0], .01) and not far(hi, man['bounds_units_rest'][1], .01),
      [[round(c, 2) for c in lo], [round(c, 2) for c in hi]])
size = [round((h - l) * man['metres_per_unit'], 1) for l, h in zip(lo, hi)]
check('about 91 m long', 88 < size[0] < 94 and size == man['size_metres_rest'], size)
noattr = [o.name for o in meshes if 'hull_rest' not in o.data.attributes or 'hull_normal' not in o.data.attributes]
check('hull attributes', not noattr, noattr[:5])
worst = 0.0
for o in meshes[::5]:
    rest = o.data.attributes['hull_rest'].data
    inv = root.matrix_world.inverted() @ o.matrix_world
    for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
        worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
check('hull_rest is rest position', worst < 1e-3, round(worst, 6))

anchors = {o.name: o for o in objs if o.type == 'EMPTY'}
bad = []
for e in man['engines']:
    a = anchors.get(e['anchor'])
    if a is None or not a.get('running') or far(a.matrix_world.translation, e['mouth']) \
            or x_axis(a).dot(Vector(e['axis'])) < .999 or Vector(e['axis']).x > -.99 \
            or abs(a.get('radius_m', 0) - e['radius'] * man['metres_per_unit']) > 1e-6:
        bad.append(e['anchor'])
check('five drive anchors at their mouths, aft along the exhaust', len(man['engines']) == 5 and not bad, bad)

bad = []
for w in man['turrets'] + man['launchers']:
    for m in w['muzzles']:
        a = anchors.get(m['anchor'])
        if a is None or far(a.matrix_world.translation, m['at']) or x_axis(a).dot(Vector(m['axis'])) < .999:
            bad.append(m['anchor'])
n_muzzles = sum(len(w['muzzles']) for w in man['turrets'] + man['launchers'])
check('muzzle and launch anchors at their places and axes', n_muzzles == 11 and not bad, bad)
hellbore = [w for w in man['turrets'] if w['slot'] == 'WS 000']
check('Hellbore launch anchor (WS 000)', len(hellbore) == 1 and len(hellbore[0]['muzzles']) == 1
      and hellbore[0]['muzzles'][0]['anchor'] in anchors)
tubes = {w['slot']: len(w['muzzles']) for w in man['launchers']}
check('Harpoon launch anchors, one per tube (WS 004/005)', tubes == {'WS 004': 3, 'WS 005': 3}, tubes)
bad = []
for w in man['turrets'] + man['launchers']:
    piv = anchors.get(w['pivot'])
    bearing = math.radians(w['rest_angle_deg'])
    if piv is None or x_axis(piv).dot(Vector((math.cos(bearing), math.sin(bearing), 0))) < .999:
        bad.append(w['pivot'])
check('pivots rest at their slot angles', not bad, bad)
bad = [s['anchor'] for s in man['system_slots']
       if s['anchor'] not in anchors or far(anchors[s['anchor']].matrix_world.translation, s['at'])
       or x_axis(anchors[s['anchor']]).dot(Vector(s['axis'])) < .999]
check('system slot anchors (WS 006-009)', len(man['system_slots']) == 4 and not bad, bad)
everything = {o.name: o for o in objs}
bad = [l['object'] for l in man['lamps'] if l['object'] not in everything
       or (l['anchor'] and (l['anchor'] not in anchors or far(anchors[l['anchor']].matrix_world.translation, l['at'], .01)))]
check('six lamps (four running lamps, two mast beacons)', len(man['lamps']) == 6 and not bad, bad)

# Turret training and elevation.
launch = [anchors[m['anchor']] for w in man['launchers'] for m in w['muzzles']]
before_l = [m.matrix_world.translation.copy() for m in launch]
train_err, still, moved, lift = 0.0, 0.0, [], []
for w in man['turrets']:
    piv, elev = anchors[w['pivot']], anchors[w['elevation']]
    barbette = everything.get(w.get('barbette'))
    muz = [anchors[m['anchor']] for m in w['muzzles']]
    before = [m.matrix_world.translation.copy() for m in muz]
    before_b = barbette.matrix_world.copy() if barbette else None
    guns = [o for o in piv.children_recursive if o.type == 'MESH']
    probe = max(guns, key=lambda o: (o.matrix_world.translation - piv.matrix_world.translation).length
                + max(o.dimensions)) if guns else None
    before_g = [probe.matrix_world @ Vector(c) for c in probe.bound_box] if probe else []
    piv.rotation_euler.z += math.radians(30)
    bpy.context.view_layer.update()
    c, axis = piv.matrix_world.translation, piv.matrix_world.to_3x3().col[2].normalized()
    turn = Matrix.Rotation(math.radians(30), 3, axis)
    train_err = max(train_err, max((m.matrix_world.translation - (c + turn @ (b - c))).length
                                   for m, b in zip(muz, before)))
    if barbette:
        train_err = max(train_err, max(abs(a - b) for ra, rb in zip(barbette.matrix_world, before_b)
                                       for a, b in zip(ra, rb)))
    after_g = [probe.matrix_world @ Vector(c) for c in probe.bound_box] if probe else []
    moved.append(bool(after_g) and max((a - b).length for a, b in zip(after_g, before_g)) > .1)
    still = max(still, max((m.matrix_world.translation - b).length for m, b in zip(launch, before_l)))
    piv.rotation_euler.z -= math.radians(30)
    elev.rotation_euler.y -= math.radians(10)
    bpy.context.view_layer.update()
    lift.append(all(m.matrix_world.translation.z > b.z + .05 for m, b in zip(muz, before)))
    elev.rotation_euler.y += math.radians(10)
    bpy.context.view_layer.update()
check('turrets train their muzzles about the pivot; barbettes fixed', len(man['turrets']) == 4 and train_err < 1e-3,
      round(train_err, 6))
check('training moves each gun', all(moved), moved)
check('elevation (negative local Y) lifts each gun', all(lift), lift)
check('Harpoon tubes stay with the hull', still < 1e-6, still)

# Effects.
ctl = bpy.data.objects.get('SWFX controls')
throttles = [f'thr_throttle_e{e["index"]}' for e in man['engines']]
check('SWFX controls with per-engine throttles', ctl is not None and 'thr_throttle' in ctl.keys()
      and all(t in ctl.keys() for t in throttles), [t for t in throttles if not ctl or t not in ctl.keys()])
fxa = {o.name for o in fx_objs if o.name.startswith('SWFX anchor ')}
want = [f'SWFX anchor engine {e["index"]}' for e in man['engines']] + \
       [f'SWFX anchor turret {w["index"]} {m["tag"]}' for w in man['turrets'] + man['launchers'] for m in w['muzzles']] + \
       [f'SWFX anchor lamp {l["object"]}' for l in man['lamps']]
missing = [n for n in want if n not in fxa]
check('SWFX anchors on every drive, muzzle, tube and lamp', not missing, missing[:5])
lights = [o for o in fx_objs if o.type == 'LIGHT' and any(o.name.endswith(l['object']) for l in man['lamps'])]
check('a light per lamp', len(lights) == len(man['lamps']), len(lights))
eff = man.get('effects') or {}
check('effects recorded in the manifest', eff.get('collection') == 'SWFX' and eff.get('objects') == len(fx_objs),
      [eff.get('objects'), len(fx_objs)])

ref = {o.name: o.matrix_world.copy() for o in objs}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(1.7, 4)
root.matrix_world = M @ root.matrix_world
bpy.context.view_layer.update()
rigid = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, M @ ref[o.name]) for a, b in zip(ra, rb)) for o in objs)
root.matrix_world = M.inverted() @ root.matrix_world
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))

result = {'asset': man['asset'], 'collection': man['append_collection'], 'root': man['root'],
          'state': man['state'], 'metres_per_unit': man['metres_per_unit'], 'size_metres_rest': size,
          'objects': len(objs), 'meshes': len(meshes), 'effects_objects': len(fx_objs),
          'images': len(bpy.data.images), 'asset_sha256': man.get('asset_sha256'),
          'passed': not fails, 'failed': fails, 'checks': checks}
(HERE / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
for k, v in checks.items():
    print('VERIFY ok' if v['ok'] else 'BAD', k, v.get('detail', ''))
print('VERIFY', 'PASS' if not fails else 'FAIL', f'{len(checks) - len(fails)}/{len(checks)} checks')
sys.exit(1 if fails else 0)
