"""Structural checks on the exported Hermes, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py

Appends `MUDSKIPPER` into a factory-empty scene and checks, writing
validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- three drive anchors pointing aft and running, two working jet anchors;
- turning each turret pivot 30 degrees swings both its muzzle anchors 30
  degrees about it and leaves its barbette fixed;
- the control knobs work after the append: at rest every gear sole stands on
  landing.ground_z; gear_up 1 lifts every sole into the hull and closes every
  gear door; door_<side> 1 swings each boarding door clear of its opening;
  every knob back to 0 restores the rest pose exactly;
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


def settle():
    # A knob set from Python does not tag the depsgraph; without the tag the
    # drivers are not re-run on the same frame (a keyframed knob needs nothing).
    for o in bpy.data.objects:
        if o.name.startswith('Hermes control'):
            o.update_tag()
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
check('drive anchors', all(eng) and len(eng) == 3)
check('drive anchors point aft', all((a.matrix_world.to_3x3() @ Vector((1, 0, 0))).dot(Vector((-1, 0, 0))) > .999
                                     for a in eng if a) and all(a.get('running') for a in eng if a))
jets = [anchors.get(r['anchor']) for r in man['rcs']]
check('jet anchors', all(jets) and len(jets) == 2 and all(a.get('working') for a in jets if a))

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

# Control knobs, driven after the append.
ctl = anchors[man['control']['object']]
ground = man['landing']['ground_z']
rest_mw = {o.name: o.matrix_world.copy() for o in objs}
soles = [o for o in meshes if o.name.startswith('Gear ') and o.name.endswith(' sole plate')]
settle()
sole_err = max(abs(zmin([s]) - ground) for s in soles)
check('gear stands on ground_z at rest', len(soles) == 3 and sole_err < 1e-3, round(sole_err, 5))
gear_doors = [o for o in anchors.values() if o.name.startswith('Gear ') and o.name.endswith(' door hinge')]
ctl['gear_up'] = 1.0
settle()
lowest = min(zmin([s]) for s in soles)
door_ang = max(abs(math.degrees(o.matrix_world.to_euler().x - o.parent.matrix_world.to_euler().x)) for o in gear_doors)
check('gear_up stows the gear and closes its doors', lowest > ground + 2.5 and door_ang < 1.0 and len(gear_doors) == 6,
      {'lowest_sole_z': round(lowest, 3), 'worst_gear_door_deg': round(door_ang, 3)})
ctl['gear_up'] = 0.0
moved = {}
for d in man['boarding_doors']:
    ctl[d['knob']] = 1.0
settle()
for d in man['boarding_doors']:
    leaf = next(o for o in meshes if o.name == f"Hull {d['side']} boarding door")
    moved[d['side']] = round((leaf.matrix_world.translation - rest_mw[leaf.name].translation).length, 3)
check('boarding doors swing clear', len(moved) == 2 and all(v > 3 for v in moved.values()), moved)
for k in man['control']['knobs']:
    ctl[k] = 0.0
settle()
back = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, rest_mw[o.name]) for a, b in zip(ra, rb)) for o in objs)
check('knobs at 0 restore the rest pose', back < 1e-4, round(back, 6))

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
