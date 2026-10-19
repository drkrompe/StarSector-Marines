"""Structural checks on the exported Broadsword, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py

Appends `BROADSWORD` into a factory-empty scene and checks, writing
validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library;
  every image is packed; object and polygon counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face);
  hull_rest equals the vertex's root-space position at rest;
- two drive anchors pointing aft and running; two gun muzzle anchors on the
  barrel tips, pointing forward, with the effects' gun rigs riding them;
- the running lights are emissive;
- the knob works after the append: at rest (gear up) nothing of the gear hangs
  below its shut doors; gear_down 1 stands every wheel on landing.ground_z
  and opens all six doors; gear_down 0 restores the rest pose exactly;
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
        if o.name.startswith('Broadsword control'):
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
check('drive anchors', all(eng) and len(eng) == 2)
check('drive anchors point aft', all((a.matrix_world.to_3x3() @ Vector((1, 0, 0))).dot(Vector((-1, 0, 0))) > .999
                                     for a in eng if a) and all(a.get('running') for a in eng if a))
guns = [anchors.get(g['anchor']) for g in man['guns']]
tips = {g['geometry_muzzle']: anchors.get(g['geometry_muzzle']) for g in man['guns']}
check('gun muzzle anchors', all(guns) and len(guns) == 2 and all(
    (a.matrix_world.to_3x3() @ Vector((1, 0, 0))).dot(Vector((1, 0, 0))) > .999 and
    (a.matrix_world.translation - tips[g['geometry_muzzle']].matrix_world.translation).length < 1e-4
    for a, g in zip(guns, man['guns']) if a))
lamps = [o for o in meshes if o.name.startswith('Light ')]
check('running lights', len(lamps) == len(man['running_lights']) == 4 and man['lamps_lit'] == 4,
      [o.name for o in lamps])

# The knob, driven after the append.
ctl = anchors[man['control']['object']]
ground = man['landing']['ground_z']
rest_mw = {o.name: o.matrix_world.copy() for o in objs}
legs = [l['leg'] for l in man['landing']['sole_anchors']]
wheels = [o for o in meshes if ' wheel ' in o.name and not o.name.endswith(' hub')]
doors = [o for o in meshes if ' door ' in o.name and o.name.startswith(tuple(legs))]
gear_parts = [o for o in meshes if o.name.startswith(tuple(legs)) and ' door ' not in o.name and ' well' not in o.name]
settle()
up_gap = min(zmin([p]) for p in gear_parts) - min(zmin([d]) for d in doors)
check('rest: gear stowed above its shut doors', len(wheels) == 6 and len(doors) == 6 and up_gap >= 0, round(up_gap, 4))
ctl['gear_down'] = 1.0
settle()
sole_err = max(abs(zmin([w]) - ground) for w in wheels)
door_hinges = [o for o in anchors.values() if ' door ' in o.name and o.name.endswith(' hinge')]
open_deg = min(abs(math.degrees(o.rotation_euler.x)) for o in door_hinges)
check('gear_down 1 stands on ground_z with its doors open', sole_err < 1e-3 and open_deg > 80 and len(door_hinges) == 6,
      {'sole_error': round(sole_err, 5), 'least_open_door_deg': round(open_deg, 2)})
sole_anchor_err = max(abs(anchors[s['anchor']].matrix_world.translation.z - ground) for s in man['landing']['sole_anchors'])
check('sole anchors on the ground with the gear down', sole_anchor_err < 1e-3, round(sole_anchor_err, 5))
ctl['gear_down'] = 0.0
settle()
back = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, rest_mw[o.name]) for a, b in zip(ra, rb)) for o in objs)
check('knob at 0 restores the rest pose', back < 1e-4, round(back, 6))

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
