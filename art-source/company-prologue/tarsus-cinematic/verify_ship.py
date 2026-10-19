"""Structural checks on an exported Tarsus state, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py -- --state clean|boarded

Appends `TARSUS` and `SWFX` into a factory-empty scene and checks, writing
<state>/validation.json (exit 1 on any failure):
- the collection, root and anchors collection exist; the root carries
  metres_per_unit and the state; every object descends from the root; nothing
  stays linked to a library; every image is packed; counts match the manifest;
- every ship mesh carries `hull_rest` (point) and `hull_normal` (face), and
  hull_rest equals the vertex's root-space position at rest;
- six drive anchors pointing aft, running in clean and dead in boarded;
- turning each turret pivot 30 degrees swings both muzzle anchors 30 degrees
  about it and leaves the barbette fixed;
- the four clamp receiver and two Kite collar anchors are present where the
  geometry published them;
- boarded: every scar anchor is present, the drive lamps are dark, and the
  effects hold no drive plume but do hold the damage smoke and sparks;
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
ap.add_argument('--state', choices=('clean', 'boarded'), default='clean')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
DIR = HERE / args.state
man = json.loads((DIR / 'manifest.json').read_text())
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
with bpy.data.libraries.load(str(DIR / man['asset']), link=False) as (src, dst):
    dst.collections = [man['append_collection'], 'SWFX']
for c in dst.collections:
    scene.collection.children.link(c)
coll = dst.collections[0]
fx = dst.collections[1]
bpy.context.view_layer.update()
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


root = bpy.data.objects.get(man['root'])
check('root present', root is not None)
check('root scale and state', root is not None and abs(root['metres_per_unit'] - man['metres_per_unit']) < 1e-9
      and root['state'] == args.state)
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
for o in meshes[::5]:
    rest = o.data.attributes['hull_rest'].data
    inv = root.matrix_world.inverted() @ o.matrix_world
    for i in range(0, len(o.data.vertices), max(1, len(o.data.vertices) // 8)):
        worst = max(worst, ((inv @ o.data.vertices[i].co) - Vector(rest[i].vector)).length)
check('hull_rest is rest position', worst < 1e-3, round(worst, 6))

anchors = {o.name: o for o in objs if o.type == 'EMPTY'}


def axis(a):
    return a.matrix_world.to_3x3() @ Vector((1, 0, 0))


drives = [anchors.get(e['anchor']) for e in man['engines']]
want = args.state == 'clean'
check('six drive anchors', all(drives) and len(drives) == 6)
check('drives point aft', all(axis(a).dot(Vector((-1, 0, 0))) > .999 for a in drives if a))
check('drives running only when clean', all(bool(a.get('running')) == want for a in drives if a))

for t in man['turrets']:
    piv = anchors[t['pivot']]
    fixed = next(o for o in meshes if o.name.startswith(f"Turret {t['index']} ") and not o.get('traverse')
                 and o.parent != piv)
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

for key, n in (('clamp_receivers', 4), ('kite_collars', 2)):
    rows = man['docking'][key]
    err = max(((anchors[r['anchor']].matrix_world.translation - Vector(r['location'])).length for r in rows),
              default=1e9)
    check(f'{key} anchors', len(rows) == n and all(r['anchor'] in anchors for r in rows) and err < 1e-3,
          round(err, 6))

fx_names = [o.name for o in fx.all_objects]
plumes = [n for n in fx_names if n.startswith('SWFX thruster') or n.startswith('SWFX plume')]
if args.state == 'boarded':
    missing = [a['anchor'] for a in man['scar_anchors'] if a['anchor'] not in anchors]
    check('scar anchors present', man['scar_anchors'] and not missing, missing)
    dead = [s for s in man['scars'] if s['kind'] == 'dead']
    lit = []
    for row in dead:
        for name in row['objects']:
            for sl in bpy.data.objects[name].material_slots:
                b = sl.material.node_tree.nodes.get('Principled BSDF')
                if b and b.inputs['Emission Strength'].default_value > 0 and any(b.inputs['Emission Color'].default_value[:3]):
                    lit.append(name)
    check('drive lamps dark', dead and not lit, lit)
    smoke = man['effects']['damage_smoke']
    check('damage smoke built', smoke and all(s['object'] in fx_names for s in smoke), len(smoke))
    check('sparks built', any(' sparks ' in n for n in fx_names))
    heat = [n for n in fx_names if 'plume' in n.lower() or 'core' in n.lower()]
    check('no drive plume', not heat, heat[:5])
else:
    check('drive plumes built', any('plume' in n.lower() for n in fx_names))

ref = {o.name: o.matrix_world.copy() for o in meshes}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(1.7, 4)
root.matrix_world = M @ root.matrix_world
bpy.context.view_layer.update()
rigid = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, M @ ref[o.name]) for a, b in zip(ra, rb)) for o in meshes)
root.matrix_world = M.inverted() @ root.matrix_world
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))

result = {'asset': man['asset'], 'state': args.state, 'collection': man['append_collection'], 'root': man['root'],
          'objects': len(objs), 'meshes': len(meshes), 'effects_objects': len(fx_names),
          'images': len(bpy.data.images), 'passed': not fails, 'failed': fails, 'checks': checks}
(DIR / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', args.state, 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
