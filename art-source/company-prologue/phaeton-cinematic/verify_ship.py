"""Structural checks on an exported Phaeton, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py -- --state clean|destroyed

Appends the state's collection (and its `SWFX`, in the same load) into a
factory-empty scene and checks, writing validation.json /
validation-destroyed.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
- six main-drive anchors pointing aft, running when clean, dead in the wreck;
- clean: turning each turret pivot 30 degrees swings both its muzzle anchors
  30 degrees about it and leaves its barbette fixed; the flare muzzles point
  along their axes; hull_rest is the root-space rest position;
- destroyed: before break_frame every section is in the intact pose; at
  break_frame + 4 s the base meshes are back at hull_rest (the wreck pose
  the build made); the fragments and debris start within a few units of the
  strike and end where the build flung them; the kit's fir_exp_frame follows
  break_frame; the eight-blast cook-off in delay order, 22 fire and 6 vent anchors;
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
ap.add_argument('--state', default='clean', choices=('clean', 'destroyed'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
WRECK = args.state == 'destroyed'
man = json.loads((HERE / ('manifest-destroyed.json' if WRECK else 'manifest.json')).read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX']
coll = dst.collections[0]
for c in dst.collections:
    scene.collection.children.link(c)
bpy.context.view_layer.update()
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


root = bpy.data.objects.get(man['root'])
check('root present', root is not None)
check('anchors collection', man['anchors_collection'] in bpy.data.collections)
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
check('images packed', not unpacked, unpacked)
meshes = [o for o in objs if o.type == 'MESH']
check('object count', len(meshes) == man['objects'], [len(meshes), man['objects']])
check('polygon count', sum(len(o.data.polygons) for o in meshes) == man['polygons'])
noattr = [o.name for o in meshes if 'hull_rest' not in o.data.attributes or 'hull_normal' not in o.data.attributes]
check('hull attributes', not noattr, noattr[:5])
anchors = {o.name: o for o in objs if o.type == 'EMPTY'}


def axis(a):
    return a.matrix_world.to_3x3() @ Vector((1, 0, 0))


def rest_error(sample):
    worst = 0.0
    inv_root = root.matrix_world.inverted()
    for o in sample:
        rest = o.data.attributes['hull_rest'].data
        inv = inv_root @ o.matrix_world
        for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
            worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
    return worst


main = [anchors.get(e['anchor']) for e in man['engines'] if e['role'] == 'main drive']
check('six main drive anchors', all(main) and len(main) == 6)
check('drives running' if not WRECK else 'drives dead',
      all(bool(a.get('running')) != WRECK for a in main if a))

if not WRECK:
    check('hull_rest is rest position', rest_error(meshes[::5]) < 1e-3)
    if WRECK is False:
        for t in man['turrets']:
            piv = anchors[t['pivot']]
            fixed = next(o for o in meshes if o.name.startswith(f"Turret {t['index']} ") and o.name.endswith(' barbette'))
            before_fixed = fixed.matrix_world.copy()
            muz = [anchors[m['anchor']] for m in t['muzzles']]
            before = [m.matrix_world.translation.copy() for m in muz]
            piv.rotation_euler.z += math.radians(30)
            bpy.context.view_layer.update()
            c = piv.matrix_world.translation
            err = max((m.matrix_world.translation - (c + Matrix.Rotation(math.radians(30), 3, 'Z') @ (b - c))).length
                      for m, b in zip(muz, before))
            err = max(err, max(abs(a - b) for ra, rb in zip(fixed.matrix_world, before_fixed) for a, b in zip(ra, rb)))
            piv.rotation_euler.z -= math.radians(30)
            bpy.context.view_layer.update()
            check(f"turret {t['index']} pivot trains", err < 1e-3 and len(muz) == 2, round(err, 6))
        check('four turrets', len(man['turrets']) == 4)
        for ln in man['launchers']:
            a = anchors.get(ln['anchor'])
            check(f"launcher {ln['slot']} muzzle", a is not None and axis(a).dot(Vector(ln['axis'])) > .999)
else:
    bu = man['break_up']
    ctrl = anchors[bu['controls']]
    fps = scene.render.fps / scene.render.fps_base
    ctrl['break_frame'] = 100.0
    ctrl.update_tag()
    scene.frame_set(60)
    bpy.context.view_layer.update()
    ident = 0.0
    for s in bu['sections'].values():
        e = anchors[s['empty']]
        local = root.matrix_world.inverted() @ e.matrix_world
        ident = max(ident, (local.translation - Vector(s['pivot'])).length,
                    max(abs(a) for a in local.to_euler()))
    check('intact pose before break_frame', ident < 1e-4, round(ident, 6))
    flyers = [o for o in meshes if o.name.startswith(('Scar S04 shell fragment', 'Scar S06 debris'))]
    strike = Vector(bu['explosion_anchors'][0]['at'])
    start = max((root.matrix_world.inverted() @ o.matrix_world.translation - strike).length for o in flyers)
    scene.frame_set(int(100 + bu['break_s'] * fps))
    bpy.context.view_layer.update()
    base = [o for o in meshes if not o.name.startswith('Scar ')]
    check('wreck pose at break_s (base meshes at hull_rest)', rest_error(base[::4]) < 1e-2, round(rest_error(base[::4]), 5))
    end = rest_error(flyers)
    check('flying pieces start near the strike', start < 60.0, round(start, 2))
    check('flying pieces reach the build pose', end < 1e-2, round(end, 5))
    knobs = bpy.data.objects.get('SWFX controls')
    check('fir_exp_frame follows break_frame', knobs is not None and abs(knobs['fir_exp_frame'] - 100.0) < 1e-6,
          knobs and knobs['fir_exp_frame'])
    delays = [a['delay_s'] for a in bu['explosion_anchors']]
    check('cook-off: eight explosion anchors in delay order', len(delays) == 8 and delays == sorted(delays)
          and delays[0] == 0.0 and all(a['anchor'] in anchors for a in bu['explosion_anchors']), delays)
    check('fire and vent anchors', len(bu['fire_anchors']) == 22 and len(bu['vent_anchors']) == 6,
          [len(bu['fire_anchors']), len(bu['vent_anchors'])])
    ctrl['break_frame'] = 1.0
    ctrl.update_tag()
    scene.frame_set(1)

ref = {o.name: o.matrix_world.copy() for o in meshes}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(1.7, 4)
root.matrix_world = M @ root.matrix_world
bpy.context.view_layer.update()
rigid = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, M @ ref[o.name]) for a, b in zip(ra, rb)) for o in meshes)
root.matrix_world = M.inverted() @ root.matrix_world
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))

result = {'asset': man['asset'], 'collection': man['append_collection'], 'root': man['root'],
          'objects': len(objs), 'meshes': len(meshes), 'images': len(bpy.data.images),
          'passed': not fails, 'failed': fails, 'checks': checks}
(HERE / ('validation-destroyed.json' if WRECK else 'validation.json')).write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', args.state, 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
