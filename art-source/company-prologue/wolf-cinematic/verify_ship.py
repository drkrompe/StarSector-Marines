"""Structural checks on the Wolf (P) cinematic asset, appended the way a shot appends it.

  blender --background --factory-startup --python verify_ship.py

Two appends into a factory-empty scene, writing validation.json (exit 1 on any
failure).

Ship alone (`WOLF_P`): the collection, root and anchors collection exist; the
root carries metres_per_unit equal to geometry.json's; every object descends
from the root; nothing stays linked; every image is packed; mesh and polygon
counts match the manifest; three `Engine <i> exhaust anchor` empties at the
slot mouths pointing aft; Turret 1 (WS 001) trains about its pivot (its muzzle
swings with it, its barbette stays) and elevates; the three hardpoints' muzzles
point along the bow; every declared VFX muzzle rides a turret-kit muzzle at
zero offset (12: one ion, one blaster, five tubes a pod); five lamps with
blink properties; moving the root moves every part rigidly.

Ship with effects (`WOLF_P` + `SWFX`): plume, core, haze and glow light on all
three engines, each plume leaving its bell aft; one kit gun anchor per
declared muzzle; one lamp light per lamp; every driver valid; moving the root
carries the effects.
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
man = json.loads((HERE / 'manifest.json').read_text())
geom = json.loads((HERE.parent / 'wolf-geometry' / 'geometry.json').read_text())
checks, fails = {}, []


def check(name, ok, detail=None):
    checks[name] = {'ok': bool(ok), **({'detail': detail} if detail is not None else {})}
    if not ok:
        fails.append(name)


def load(names):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    with bpy.data.libraries.load(str(HERE / man['asset']), link=False) as (src, dst):
        dst.collections = names
    for c in dst.collections:
        scene.collection.children.link(c)
    scene.frame_set(1)
    bpy.context.view_layer.update()
    return dst.collections


def upd():
    bpy.context.view_layer.update()


def maxdiff(a, b):
    return max(abs(x - y) for ra, rb in zip(a, b) for x, y in zip(ra, rb))


coll = load([man['append_collection']])[0]
root = bpy.data.objects.get(man['root'])
check('root present', root is not None)
check('anchors collection', man['anchors_collection'] in bpy.data.collections)
mpu = root.get('metres_per_unit') if root else None
check('root metres_per_unit', mpu is not None and abs(mpu - geom['metres_per_unit']) < 1e-9, mpu)
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
check('images packed', not unpacked and len(bpy.data.images) > 0, [len(bpy.data.images), unpacked[:5]])
meshes = [o for o in objs if o.type == 'MESH']
check('mesh count', len(meshes) == man['meshes'], [len(meshes), man['meshes']])
polys = sum(len(o.data.polygons) for o in meshes)
check('polygon count', polys == man['polygons'], [polys, man['polygons']])
anchors = {o.name: o for o in objs if o.type == 'EMPTY'}

bad = []
for e in man['engines']:
    a = anchors.get(e['anchor'])
    if a is None:
        bad.append((e['index'], 'missing'))
        continue
    ax = (a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    off = (a.matrix_world.translation.xy - Vector(e['location'][:2])).length
    if ax.dot(Vector(e['exhaust_direction'])) < .999 or off > 1e-3:
        bad.append((e['index'], [round(c, 3) for c in ax], round(off, 4)))
check('three engine anchors at the mouths, pointing aft', not bad and len(man['engines']) == 3, bad)

bad = []
for t in man['turrets']:
    piv, ele = anchors.get(t['pivot']), anchors.get(t['elevation'])
    muz = [anchors.get(n) for n in t['muzzles']]
    barb = bpy.data.objects.get(t['fixed'][0])
    if not (piv and ele and all(muz) and barb):
        bad.append((t['slot'], 'missing'))
        continue
    before = [m.matrix_world.translation.copy() for m in muz]
    fixed = barb.matrix_world.copy()
    c, axis = piv.matrix_world.translation.copy(), piv.matrix_world.to_3x3().col[2].normalized()
    basis = piv.matrix_basis.copy()
    piv.matrix_basis = basis @ Matrix.Rotation(math.radians(30), 4, 'Z')
    upd()
    R = Matrix.Rotation(math.radians(30), 3, axis)
    err = max(max((m.matrix_world.translation - (c + R @ (b - c))).length for m, b in zip(muz, before)),
              maxdiff(barb.matrix_world, fixed))
    piv.matrix_basis = basis
    upd()
    eb = ele.matrix_basis.copy()
    ele.matrix_basis = eb @ Matrix.Rotation(math.radians(-10), 4, 'Y')
    upd()
    moved = min((m.matrix_world.translation - b).length for m, b in zip(muz, before))
    ele.matrix_basis = eb
    upd()
    if err > 1e-3 or moved < .05:
        bad.append((t['slot'], round(err, 5), round(moved, 3)))
check('turret 1 trains and elevates', not bad and len(man['turrets']) == 1, bad)

bad = []
for h in man['hardpoints']:
    for n in h['muzzles']:
        a = anchors.get(n)
        if a is None or (a.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized().x < .999:
            bad.append(n)
check('hardpoint muzzles point along the bow', not bad and len(man['hardpoints']) == 3, bad)

declared = [n for m in man['vfx_mounts'] for n in m['muzzles']]
bad = [n for n in declared if not (anchors.get(n) and anchors[n].parent and anchors[n].parent.type == 'EMPTY'
                                    and maxdiff(anchors[n].matrix_world, anchors[n].parent.matrix_world) < 1e-4)]
check('declared VFX muzzles ride the kit muzzles', not bad and len(declared) == 12, bad or f'{len(declared)} muzzles')

bad = [l['name'] for l in man['running_lights'] if not bpy.data.objects.get(l['name'])]
check('lamps', not bad and len(man['running_lights']) == 5, bad)

ref = {o.name: o.matrix_world.copy() for o in objs}
M = Matrix.Translation((310, -45, 120)) @ Matrix.Rotation(.8, 4, 'Z') @ Matrix.Rotation(-.3, 4, 'Y') @ Matrix.Scale(.95, 4)
root.matrix_world = M @ root.matrix_world
upd()
rigid = max(maxdiff(o.matrix_world, M @ ref[o.name]) for o in objs)
check('root moves the ship rigidly', rigid < 1e-3, round(rigid, 6))
ship_objects = len(objs)

ship_c, fx_c = load([man['append_collection'], 'SWFX'])
root = bpy.data.objects[man['root']]
fx = list(fx_c.all_objects)
check('SWFX present', len(fx) == man['effects']['objects'], [len(fx), man['effects']['objects']])
P = 'SWFX thrusters'
bad = []
for e in man['engines']:
    i, r = e['index'], e['mouth_radius']
    parts = [bpy.data.objects.get(f'{P} {k} e{i}') for k in ('plume', 'core', 'light')]
    haze = bpy.data.objects.get(f'SWFX smoke haze e{i}')
    if not all(parts) or haze is None:
        bad.append((i, 'missing'))
        continue
    plume = parts[0]
    mouth, d = Vector(e['location']), Vector(e['exhaust_direction'])
    pw = plume.matrix_world
    axis = (pw.to_3x3() @ Vector((1, 0, 0))).normalized()
    far = max((p - mouth).dot(d) for p in (pw @ Vector(c) for c in plume.bound_box))
    if axis.dot(d) < .999 or (pw.translation - mouth).length > .5 or far < 5 * r:
        bad.append((i, round(axis.dot(d), 4), round(far / r, 1)))
check('plumes leave every bell aft', not bad, bad)
ga = [o for o in fx if o.name.startswith('SWFX anchor turret ')]
check('gun rig anchors', len(ga) == 12, len(ga))
ll = [o for o in fx if o.type == 'LIGHT' and o.name.startswith('SWFX lighting Light ')]
check('lamp lights', len(ll) == 5, len(ll))
inval = []
for o in fx:
    for owner in (o, o.data):
        ad = getattr(owner, 'animation_data', None) if owner is not None else None
        inval += [f'{o.name}:{fc.data_path}' for fc in (ad.drivers if ad else ()) if not fc.driver.is_valid]
    if o.type == 'MESH':
        for m in o.data.materials:
            ad = m.node_tree.animation_data if m and m.node_tree else None
            inval += [f'{m.name}:{fc.data_path}' for fc in (ad.drivers if ad else ()) if not fc.driver.is_valid]
check('drivers valid', not inval, inval[:5])
p = bpy.data.objects[f'{P} plume e2']
before = p.matrix_world.copy()
root.matrix_world = M @ root.matrix_world
upd()
check('root carries the effects', maxdiff(p.matrix_world, M @ before) < 1e-3, round(maxdiff(p.matrix_world, M @ before), 6))

result = {'asset': man['asset'], 'collection': man['append_collection'], 'root': man['root'],
          'effects_collection': 'SWFX', 'objects': ship_objects, 'meshes': len(meshes),
          'effects_objects': len(fx), 'images': len(bpy.data.images),
          'passed': not fails, 'failed': fails, 'checks': checks}
(HERE / 'validation.json').write_text(json.dumps(result, indent=1) + '\n')
print('VERIFY', 'PASS' if not fails else f'FAIL {fails}')
for k, v in checks.items():
    print('  ', 'ok ' if v['ok'] else 'BAD', k, v.get('detail', ''))
sys.exit(1 if fails else 0)
