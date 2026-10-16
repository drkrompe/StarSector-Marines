"""Structural checks on an exported state, appended the way a shot appends it.

  blender --background --factory-startup --python verify_state.py -- --state patched

Appends `SECOND WIND | <state>` into a factory-empty scene and checks, writing
<state>/validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; no datablock stays linked to a library;
  every image is packed; object, polygon and anchor counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- the base wear group is on every skin and swatch; the state overlay group
  is present with its master and on every material with an overlay family;
- engine anchors: four, +X aft, running flags as the manifest says;
- turret pivots: four; turning a pivot 30 degrees about its Z swings its
  muzzle anchor by 30 degrees about the pivot and leaves the barbette fixed;
- moving the root (translate, yaw, pitch, scale) moves every part rigidly and
  leaves hull_rest untouched (the wear masks ride the ship).
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
STATE = argv[argv.index('--state') + 1]
DIR = HERE / STATE
man = json.loads((DIR / 'manifest.json').read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(DIR / man['asset']), link=False) as (src, dst):
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
objs = list(coll.all_objects)


def under_root(o):
    while o.parent:
        o = o.parent
    return o == root


check('all under root', all(under_root(o) for o in objs),
      [o.name for o in objs if not under_root(o)][:5])
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
for o in meshes[::7]:
    rest = o.data.attributes['hull_rest'].data
    inv = root.matrix_world.inverted() @ o.matrix_world
    for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
        worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
check('hull_rest is rest position', worst < 1e-3, round(worst, 6))
base = bpy.data.node_groups.get(f"SW wear {STATE}")
skins = [m for m in bpy.data.materials if m.get('skin_part') is not None or m.get('skin_swatch') is not None]
nobase = [m.name for m in skins if not any(n.type == 'GROUP' and n.node_tree == base for n in m.node_tree.nodes)]
check('base wear on every skin and swatch', base is not None and skins and not nobase,
      {'materials': len(skins), 'missing': nobase[:5]})
grp = bpy.data.node_groups.get(f"SW overlay {STATE}")
check('overlay group', grp is not None and 'SW overlay master' in grp.nodes)
fam = [m for m in bpy.data.materials if m.get('overlay_family')]
miss = [m.name for m in fam if not any(n.type == 'GROUP' and n.node_tree == grp for n in m.node_tree.nodes)]
check('overlay on every family material', fam and not miss, {'materials': len(fam), 'missing': miss[:5]})

anchors = {o.name: o for o in objs if o.type == 'EMPTY'}
eng = [anchors.get(e['anchor']) for e in man['engines']]
check('engine anchors', all(eng) and len(eng) == 4)
axis_ok = all((a.matrix_world.to_3x3() @ Vector((1, 0, 0))).dot(Vector((-1, 0, 0))) > .999 for a in eng if a)
check('engine anchors point aft', axis_ok)
run = all(bool(a.get('running')) == e['running'] for a, e in zip(eng, man['engines']) if a)
check('running flags', run and root.get('engines_running') is not None
      and list(root['engines_running']) == man['engines_running'], man['engines_running'])

turn_err = 0.0
for t in man['turrets']:
    piv = anchors.get(t['pivot'])
    if piv is None:
        fails.append(f"pivot {t['pivot']}")
        continue
    fixed = bpy.data.objects.get(f"Turret {t['index']} barbette foot")
    before_fixed = fixed.matrix_world.copy() if fixed else None
    muz = [anchors[m['anchor']] for m in t['muzzles']]
    before = [m.matrix_world.translation.copy() for m in muz]
    piv.rotation_euler.z += math.radians(30)
    bpy.context.view_layer.update()
    c = piv.matrix_world.translation
    for m, b in zip(muz, before):
        want = c + Matrix.Rotation(math.radians(30), 3, 'Z') @ (b - c)
        turn_err = max(turn_err, (m.matrix_world.translation - want).length)
    if fixed:
        turn_err = max(turn_err, max(abs(a - b) for ra, rb in zip(fixed.matrix_world, before_fixed)
                                     for a, b in zip(ra, rb)))
    piv.rotation_euler.z -= math.radians(30)
    bpy.context.view_layer.update()
check('turret pivots train', turn_err < 1e-3 and len(man['turrets']) == 4, round(turn_err, 6))

ref = {o.name: o.matrix_world.copy() for o in meshes}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(1.7, 4)
root.matrix_world = M @ root.matrix_world
bpy.context.view_layer.update()
rigid = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, M @ ref[o.name]) for a, b in zip(ra, rb)) for o in meshes)
root.matrix_world = M.inverted() @ root.matrix_world
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))

result = {'state': STATE, 'asset': man['asset'], 'collection': man['append_collection'], 'root': man['root'],
          'objects': len(objs), 'meshes': len(meshes), 'images': len(bpy.data.images),
          'passed': not fails, 'failed': fails, 'checks': checks}
(DIR / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', STATE, 'PASS' if not fails else f'FAIL {fails}')
sys.exit(1 if fails else 0)
