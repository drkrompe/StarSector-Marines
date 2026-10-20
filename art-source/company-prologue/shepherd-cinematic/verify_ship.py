"""Structural checks on the exported Shepherd, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py [-- --dir DIR]

Appends `SHEPHERD` into a factory-empty scene and checks, writing
validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- two drive anchors pointing aft and running, four working jet anchors;
- turning the turret pivot 30 degrees swings both its muzzle anchors 30
  degrees about it and leaves its barbette fixed; the hardpoint muzzle points
  along its slot angle;
- the drone bay's three mouth and launch-point anchors are present, each
  launch point below its mouth;
- moving the root (translate, yaw, pitch, scale) moves every part rigidly.
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
# --dir DIR reads and writes the asset, manifest and validation there (default: here).
_argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
OUT = Path(_argv[_argv.index('--dir') + 1]).resolve() if '--dir' in _argv else HERE
man = json.loads((OUT / 'manifest.json').read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(OUT / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection']]
coll = dst.collections[0]
scene.collection.children.link(coll)
bpy.context.view_layer.update()
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


def settle():
    scene.frame_set(scene.frame_current)
    bpy.context.view_layer.update()


def zmin(obs):
    dg = bpy.context.evaluated_depsgraph_get()
    return min((o.evaluated_get(dg).matrix_world @ Vector(c)).z for o in obs for c in o.bound_box)


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
settle()
worst = 0.0
for o in meshes[::5]:
    rest = o.data.attributes['hull_rest'].data
    inv = root.matrix_world.inverted() @ o.matrix_world
    for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
        worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
check('hull_rest is rest position', worst < 1e-3, round(worst, 6))

anchors = {o.name: o for o in objs if o.type == 'EMPTY'}
eng = [anchors.get(e['anchor']) for e in man['engines']]
check('drive anchors', all(eng) and len(eng) == 2)
check('drive anchors point aft', all((a.matrix_world.to_3x3() @ Vector((1, 0, 0))).dot(Vector((-1, 0, 0))) > .999
                                     for a in eng if a) and all(a.get('running') for a in eng if a))
jets = [anchors.get(r['anchor']) for r in man['rcs']]
check('jet anchors', all(jets) and len(jets) == 4 and all(a.get('working') for a in jets if a))

for t in man['turrets']:
    piv = anchors[t['pivot']]
    fixed = next(o for o in meshes if o.name.startswith(f"Turret {t['index']} ") and 'barbette' in o.name)
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
    check(f"turret {t['index']} pivot trains", err < 1e-3 and len(muz) == 2
          and {m['tag'] for m in t['muzzles']} == {'port gun', 'starboard gun'}, round(err, 6))

for h in man['hardpoints']:
    a = anchors.get(h['anchor'])
    ax = a.matrix_world.to_3x3() @ Vector((1, 0, 0)) if a else Vector()
    check(f"hardpoint {h['slot']} muzzle", a is not None and ax.dot(Vector(h['axis'])) > .999)
bad = []
for o in man['drone_bay']['openings']:
    m, lp = anchors.get(o['mouth']), anchors.get(o['launch_point'])
    if not (m and lp and lp.matrix_world.translation.z < m.matrix_world.translation.z - 5):
        bad.append(o['index'])
check('drone bay anchors', not bad and len(man['drone_bay']['openings']) == 3, bad)

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
(OUT / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
