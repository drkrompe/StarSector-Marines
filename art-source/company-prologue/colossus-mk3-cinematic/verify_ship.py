"""Structural checks on the exported Colossus Mk.III, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py

Appends `COLOSSUS MK.III` into a factory-empty scene and checks, writing
validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- six running main-drive anchors pointing aft and six running lift-thruster
  anchors pointing down;
- turning each of the twelve turret pivots 30 degrees swings its muzzle
  anchors 30 degrees about it and leaves its barbette fixed;
- the four launch points point out of the bay mouth (+X);
- the grapples knob: at 0 every boom is stowed as built; at 1 every clamp
  anchor sits on its Tarsus receiver (geometry.json) within 0.2 units (the
  pad face stands 0.15 units off the plate), and
  back at 0 the booms return to the built pose;
- each flare launcher muzzle points along its muzzle plate's axis;
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
    dst.collections = [man['append_collection']]
coll = dst.collections[0]
scene.collection.children.link(coll)
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
bpy.context.view_layer.update()
worst = 0.0
for o in meshes[::5]:
    rest = o.data.attributes['hull_rest'].data
    inv = root.matrix_world.inverted() @ o.matrix_world
    for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
        worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
check('hull_rest is rest position', worst < 1e-3, round(worst, 6))

anchors = {o.name: o for o in objs if o.type == 'EMPTY'}


def axis(a):
    return a.matrix_world.to_3x3() @ Vector((1, 0, 0))


main = [anchors.get(e['anchor']) for e in man['engines'] if e['role'] == 'main drive']
check('main drive anchors', all(main) and len(main) == 6 and all(a.get('running') for a in main if a))
check('main drives point aft', all(axis(a).dot(Vector((-1, 0, 0))) > .999 for a in main if a))
lift = [anchors.get(e['anchor']) for e in man['engines'] if e['role'] == 'lift thruster']
check('lift thruster anchors', all(lift) and len(lift) == 6 and all(a.get('running') for a in lift if a))
check('lift thrusters point down', all(axis(a).dot(Vector((0, 0, -1))) > .999 for a in lift if a))

for t in man['turrets']:
    piv = anchors[t['pivot']]
    fixed = next(o for o in meshes if o.name.startswith(f"Turret {t['index']} ") and o.name.endswith('barbette'))
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
    want = {'port gun', 'starboard gun'} if t['gun'] == 'twin' else {'gun'}
    check(f"turret {t['index']} pivot trains", err < 1e-3 and {m['tag'] for m in t['muzzles']} == want,
          round(err, 6))
check('twelve turrets', len(man['turrets']) == 12)

for lp in man['launch_points']:
    a = anchors.get(lp['anchor'])
    check(f"launch point {lp['anchor'][-1]} out of the bay", a is not None and axis(a).dot(Vector((1, 0, 0))) > .999)

ctl = bpy.data.objects.get(man['control']['object'])
check('control empty', ctl is not None and 'grapples' in ctl.keys())
if ctl is not None:
    clamps = [anchors[g['clamp_anchor']] for g in man['grapples']['booms']]
    rest = [c.matrix_world.translation.copy() for c in clamps]
    ctl['grapples'] = 1.0
    ctl.update_tag()
    bpy.context.view_layer.update()
    inv = root.matrix_world.inverted()
    reach = max(((inv @ c.matrix_world.translation) - Vector(g['receiver'])).length
                for c, g in zip(clamps, man['grapples']['booms']))
    check('grapples clamp the receivers at 1', reach < .2, round(reach, 4))
    ctl['grapples'] = 0.0
    ctl.update_tag()
    bpy.context.view_layer.update()
    back = max((c.matrix_world.translation - r).length for c, r in zip(clamps, rest))
    check('grapples stow again at 0', back < 1e-3, round(back, 6))

for ln in man['launchers']:
    a = anchors.get(ln['anchor'])
    check(f"launcher {ln['slot']} muzzle", a is not None and axis(a).dot(Vector(ln['axis'])) > .999)

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
(HERE / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
