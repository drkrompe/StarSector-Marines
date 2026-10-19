"""Structural checks on the exported Kite (P), appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py

Appends `KITE (P)` and the optional `SWFX` collection into a factory-empty scene
and checks, writing validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- eight drive anchors at their slot mouths, +X along the slot's exhaust, running;
  eight working RCS anchors;
- every weapon muzzle anchor fires along +X (dead ahead at rest); turning
  'Weapon WS 003 pivot' 30 degrees swings both its muzzles 30 degrees about it
  and leaves its barbette fixed; the launchers' muzzles stay put;
- every SWFX object but the kit's controls and ground landing dust descends from the
  ship's root (the effects ride the ship);
- moving the root (translate, yaw, pitch, scale) moves every part rigidly.
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
man = json.loads((HERE / 'manifest.json').read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX']
for c in dst.collections:
    scene.collection.children.link(c)
coll, fx = dst.collections
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


def top(o):
    while o.parent:
        o = o.parent
    return o


check('all under root', all(top(o) == root for o in objs), [o.name for o in objs if top(o) != root][:5])
# The kit's control empty and its ground-level landing dust stand in the scene on purpose.
loose_fx = [o.name for o in fx.all_objects if top(o) != root
            and o.name != 'SWFX controls' and not o.name.startswith('SWFX smoke landing dust')]
check('effects ride the ship', not loose_fx, loose_fx[:5])
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
    if a is None or not a.get('running') or (a.matrix_world.translation - Vector(e['mouth'])).length > 1e-3 \
            or (a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized().dot(Vector(e['axis'])) < .999:
        bad.append(e['anchor'])
check('eight drive anchors at their mouths, along the exhaust', len(man['engines']) == 8 and not bad, bad)
jets = [anchors.get(r['anchor']) for r in man['rcs']]
check('RCS anchors', len(jets) == 8 and all(a and a.get('working') for a in jets))

ahead = []
for w in man['weapons']:
    for m in w['muzzles']:
        a = anchors[m['anchor']]
        if (a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized().dot(Vector((1, 0, 0))) < .99:
            ahead.append(m['anchor'])
check('muzzles fire ahead at rest', not ahead, ahead)
gun = next(w for w in man['weapons'] if w.get('pivot'))
piv = anchors[gun['pivot']]
barbette = bpy.data.objects[f"Weapon {gun['slot']} barbette"]
before_b = barbette.matrix_world.copy()
muz = [anchors[m['anchor']] for m in gun['muzzles']]
before = [m.matrix_world.translation.copy() for m in muz]
launch = [anchors[m['anchor']] for w in man['weapons'] if not w.get('pivot') for m in w['muzzles']]
before_l = [m.matrix_world.translation.copy() for m in launch]
piv.rotation_euler.z += math.radians(30)
bpy.context.view_layer.update()
c = piv.matrix_world.translation
err = max((m.matrix_world.translation - (c + Matrix.Rotation(math.radians(30), 3, 'Z') @ (b - c))).length
          for m, b in zip(muz, before))
err = max(err, max(abs(a - b) for ra, rb in zip(barbette.matrix_world, before_b) for a, b in zip(ra, rb)))
still = max((m.matrix_world.translation - b).length for m, b in zip(launch, before_l))
piv.rotation_euler.z -= math.radians(30)
bpy.context.view_layer.update()
check('WS 003 trains its two muzzles; barbette fixed', err < 1e-3 and len(muz) == 2, round(err, 6))
check('launcher muzzles stay with the hull', still < 1e-6, still)

ref = {o.name: o.matrix_world.copy() for o in meshes}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(1.7, 4)
root.matrix_world = M @ root.matrix_world
bpy.context.view_layer.update()
rigid = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, M @ ref[o.name]) for a, b in zip(ra, rb)) for o in meshes)
root.matrix_world = M.inverted() @ root.matrix_world
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))

result = {'asset': man['asset'], 'collection': man['append_collection'], 'root': man['root'],
          'objects': len(objs), 'meshes': len(meshes), 'effects_objects': len(fx.all_objects),
          'images': len(bpy.data.images), 'passed': not fails, 'failed': fails, 'checks': checks}
(HERE / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
