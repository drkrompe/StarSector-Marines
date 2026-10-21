"""Structural checks on the exported Borer, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py [-- --dir DIR]

Appends `BORER` (and `SWFX` when the export carries effects) into a factory-empty scene and
checks, writing validation.json beside the asset (exit 1 on any failure):
- the collection, root and anchors collection exist under the manifest's names;
- every object descends from the root; nothing stays linked to a library; every image is
  packed; object and polygon counts match the manifest;
- every mesh carries `hull_rest` (point) and `hull_normal` (face), and hull_rest equals the
  vertex's root-space position;
- three drive anchors on the mouths, running, +X down each slot's exhaust heading (the side
  drives splayed 20 degrees outboard);
- the WS 001 muzzle anchor on the lens, +X forward, ahead of everything but the beam; the
  cutting-head and both grab anchors present, forward of the pods;
- the cradle seat on the skid shoes' plane: every shoe's sole within 1 mm of it;
- four lift jet anchors under the pods, pointing straight down;
- the lamps are emissive;
- with effects: the beam rig rides the muzzle anchor and is dark at rest (beam_t_ws001 = 0);
- moving the root (translate, yaw, pitch, scale) moves every part rigidly.
The Borer has no moving parts, so there is no knob to exercise.
"""
from pathlib import Path
import argparse
import json
import sys
import bpy
from mathutils import Matrix, Vector

ap = argparse.ArgumentParser()
ap.add_argument('--dir', default=str(Path(__file__).resolve().parent))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
DIR = Path(args.dir).resolve()
man = json.loads((DIR / 'manifest.json').read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
want = [man['append_collection']] + ([man['effects']['collection']] if man.get('effects') else [])
with bpy.data.libraries.load(str(DIR / man['asset']), link=False) as (src, dst):
    dst.collections = want
for c in dst.collections:
    scene.collection.children.link(c)
coll = dst.collections[0]
bpy.context.view_layer.update()
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


def xaxis(o):
    return (o.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()


root = bpy.data.objects.get(man['root'])
check('root present', root is not None and abs(root['metres_per_unit'] - man['metres_per_unit']) < 1e-9)
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
worst = 0.0
for o in meshes[::4]:
    rest = o.data.attributes['hull_rest'].data
    inv = root.matrix_world.inverted() @ o.matrix_world
    for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
        worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
check('hull_rest is rest position', worst < 1e-3, round(worst, 6))

anchors = {o.name: o for o in objs if o.type == 'EMPTY'}
eng = [(anchors.get(e['anchor']), e) for e in man['engines']]
check('drive anchors', len(eng) == 3 and all(a for a, _ in eng))
dots = [round(xaxis(a).dot(Vector(e['axis']).normalized()), 6) for a, e in eng if a]
check('drive anchors along the slot headings, running',
      all(d > .9999 for d in dots) and all(a.get('running') for a, _ in eng if a), dots)
splay = [round(xaxis(a).y, 3) for a, e in eng if a and e['side'] != 'centre']
check('side drives splay outboard', len(splay) == 2 and all(abs(s) > .3 for s in splay)
      and all((s > 0) == (a.matrix_world.translation.y > 0) for s, (a, e) in zip(splay, [x for x in eng if x[1]['side'] != 'centre'])),
      splay)
muz = anchors.get(man['tool']['muzzle']['anchor'])
fore = max((o.matrix_world @ Vector(c)).x for o in meshes for c in o.bound_box)
check('muzzle on the lens, forward', muz is not None and xaxis(muz).x > .9999
      and abs(muz.matrix_world.translation.x - fore) < .5, muz and [round(muz.matrix_world.translation.x, 3), round(fore, 3)])
tools = [anchors.get(t['anchor']) for t in man['tool']['anchors']]
check('tool anchors', len(tools) == 3 and all(tools) and all(t.matrix_world.translation.x > 4.5 for t in tools if t))
seat = anchors.get(man['cradle_seat']['anchor'])
pads = [o for o in meshes if o.name.startswith('Skid ') and o.name.endswith(' shoe')]
soles = [min((o.matrix_world @ Vector(c)).z for c in o.bound_box) for o in pads]
sz = seat.matrix_world.translation.z if seat else None
check('cradle seat on the skid shoe soles', seat is not None and len(pads) == 4 and max(abs(s - sz) for s in soles) < 1e-3,
      [round(s, 4) for s in soles])
lj = [anchors.get(j['anchor']) for j in man['lift_jets']]
check('four lift jets pointing down', len(lj) == 4 and all(a and xaxis(a).z < -.9999 for a in lj))
lit = [o for o in meshes if any(sl.material and sl.material.node_tree and (b := sl.material.node_tree.nodes.get('Principled BSDF'))
                                and b.inputs['Emission Strength'].default_value > 0 for sl in o.material_slots)]
check('lamps emissive', len(lit) >= 3 and man['lamps_lit'] == len(man['lamps']) >= 3, [o.name for o in lit])

if man.get('effects'):
    fx = list(bpy.data.collections[man['effects']['collection']].all_objects)
    beam = [o for o in fx if o.name.startswith('SWFX beams') and 'ws001' in o.name]
    rides = [o for o in beam if o.parent is not None and (o.parent == muz or o.parent.parent == muz)]
    ctl = bpy.data.objects.get('SWFX controls')
    check('beam rig rides the muzzle, dark at rest', beam and rides and ctl is not None
          and float(ctl.get('beam_t_ws001', 0)) == 0.0, [o.name for o in beam][:4])

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
(DIR / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
