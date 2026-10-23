"""Structural checks on the exported Hound, appended the way a shot appends it.

  blender --background --factory-startup --python verify_hound.py -- [--state clean] [--dir BUILD]

Appends `HOUND (A) | <state>` and, when the manifest records effects, `SWFX` in
the same load into a factory-empty scene. Writes <state>/validation.json (exit 1
on any failure):
- the collection, root and anchors collection exist under the manifest's names;
  every ship object descends from the root; every image is packed; nothing is
  linked to a library; object and polygon counts match the manifest;
- the skin's wear group (manifest row) is on the materials;
- engine anchors: as many as the manifest, +X aft, running, radius_m;
- the WS 002 pivot: training it 30 degrees swings its muzzle 30 degrees about
  the pivot and leaves the barbette where it was; WS 001's muzzle stays put;
- the WS 002 elevation: the VFX kit's swfx_guns.trainers finds pivot and elevation
  from every muzzle anchor; at both ends of the arc, at rest and at 30 degrees, posed
  to each limit, every muzzle line tilts by that angle on its own bearing, the gun
  house stays, and the gun touches no mesh it did not touch at rest;
- the ramp: lowering the hinge by ground_contact_deg brings the toe down to
  the bottom of the skids' landing pads, within half a unit;
- moving the root (translate, yaw, pitch, scale) moves every part and every
  effect anchor rigidly.
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Euler, Matrix, Vector

HERE = Path(__file__).resolve().parent
argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
STATE = argv[argv.index('--state') + 1] if '--state' in argv else 'clean'
DIR = Path(argv[argv.index('--dir') + 1]).resolve() if '--dir' in argv else HERE / STATE   # --dir: a build folder
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


def wpos(name):
    return bpy.data.objects[name].matrix_world.translation.copy()


root = bpy.data.objects.get(man['root'])
check('root', root is not None and root.get('metres_per_unit') == man['metres_per_unit'])
check('anchors collection', man['anchors_collection'] in bpy.data.collections)
objs = list(coll.all_objects)
meshes = [o for o in objs if o.type == 'MESH']


def under_root(o):
    while o.parent is not None:
        o = o.parent
    return o == root


check('everything under root', all(under_root(o) for o in objs),
      [o.name for o in objs if not under_root(o)][:5])
check('images packed', all(i.packed_file for i in bpy.data.images if i.source == 'FILE'),
      [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file])
check('no libraries', not bpy.data.libraries[:] or all(not b.library for b in [*bpy.data.objects, *bpy.data.materials]))
check('object count', len(meshes) == man['objects'], [len(meshes), man['objects']])
check('polygon count', sum(len(o.data.polygons) for o in meshes) == man['polygons'])
# The wear row the skin was applied with ('service'; 'pirate' on the Hound (P)), read off the manifest.
row = man['wear'].split('row `')[1].split('`')[0] if 'row `' in man.get('wear', '') else 'service'
wear = bpy.data.node_groups.get(f'SW wear {row}')
check(f'{row} wear group', wear is not None and wear.users > 0)

for e in man['engines']:
    a = bpy.data.objects.get(e['anchor'])
    ax = a.matrix_world.to_3x3() @ Vector((1, 0, 0)) if a else Vector()
    check(f"engine {e['index']} anchor", a is not None and ax.x < -0.999 and a.get('running') is True
          and abs(a.get('radius_m', 0) - e['radius_m']) < 1e-6, [round(c, 3) for c in ax])

# Turret training.
for t in man['turrets']:
    pivot = bpy.data.objects[t['pivot']['name']]
    muzzle = t['muzzle']['anchor']
    fixed = bpy.data.objects[f"Weapon {t['slot']} armoured collar"]
    p0, m0, f0 = wpos(pivot.name), wpos(muzzle), fixed.matrix_world.copy()
    pivot.rotation_euler.z += math.radians(30)
    bpy.context.view_layer.update()
    m1 = wpos(muzzle)
    r0, r1 = (m0 - p0).to_2d(), (m1 - p0).to_2d()
    swing = math.degrees(math.atan2(r0.x * r1.y - r0.y * r1.x, r0.dot(r1)))   # + is anticlockwise from above
    barbette_still = all(abs(a - b) < 1e-5 for ra, rb in zip(fixed.matrix_world, f0) for a, b in zip(ra, rb))
    check(f"{t['slot']} trains", abs(swing - 30) < 0.05 and abs(m1.z - m0.z) < 1e-4 and barbette_still,
          {'swing_deg': round(swing, 3), 'barbette_still': barbette_still})
    pivot.rotation_euler.z -= math.radians(30)
    bpy.context.view_layer.update()
# Turret elevation: the VFX kit's own lookup finds pivot and elevation up each muzzle anchor's
# chain; posed to each limit (and trained), the muzzle line tilts by exactly that angle about the
# trunnion axis, the house stays on the pivot, and the gun touches nothing it did not at rest.
sys.path.insert(0, str(HERE.parent / 'second-wind-vfx'))
sys.path.insert(0, str(HERE))
import swfx_guns  # noqa: E402
import hd_elevation  # noqa: E402
from mathutils.bvhtree import BVHTree  # noqa: E402


def tree(o):
    mw = o.matrix_world
    return BVHTree.FromPolygons([mw @ v.co for v in o.data.vertices], [p.vertices[:] for p in o.data.polygons])


for t in man['turrets']:
    rec = t.get('elevation')
    if rec is None:
        check(f"{t['slot']} elevation joint", False, 'no elevation in the manifest')
        continue
    pivot, elev = bpy.data.objects[t['pivot']['name']], bpy.data.objects.get(rec['name'])
    found = [swfx_guns.trainers(bpy.data.objects[m['anchor']]) for m in t['muzzles']]
    check(f"{t['slot']} kit finds pivot and elevation", elev is not None and all(f == (pivot, elev) for f in found),
          [[o and o.name for o in f] for f in found])
    lim = (elev.get('elevation_min_deg'), elev.get('elevation_max_deg'))
    check(f"{t['slot']} elevation limits", lim == (rec['min_deg'], rec['max_deg']) and lim[0] < 0 < lim[1], lim)
    house = bpy.data.objects[f"Weapon {t['slot']} gun house"]
    gun = hd_elevation.elevating_parts(objs)
    near = [o for o in meshes if o not in gun and (o.matrix_world.translation - elev.matrix_world.translation).length < 40]
    errs, clips = [], {}
    for bearing in (-t['arc_deg'] / 2, 0.0, 30.0, t['arc_deg'] / 2):
        pivot.rotation_euler.z = math.radians(bearing)
        hd_elevation.set_elevation(elev, 0)
        bpy.context.view_layer.update()
        h0 = house.matrix_world.copy()
        trees = {o.name: tree(o) for o in near}
        base = {n for n, tr in trees.items() if any(tree(g).overlap(tr) for g in gun)}
        a0 = {m['anchor']: bpy.data.objects[m['anchor']].matrix_world.copy() for m in t['muzzles']}
        for deg in lim:
            hd_elevation.set_elevation(elev, deg)
            bpy.context.view_layer.update()
            for n, m0 in a0.items():
                x0, x1 = m0.to_3x3().col[0].normalized(), bpy.data.objects[n].matrix_world.to_3x3().col[0].normalized()
                tilt = math.degrees(math.asin(max(-1, min(1, x1.z)))) - math.degrees(math.asin(max(-1, min(1, x0.z))))
                if abs(tilt - deg) > .05 or abs(x1.to_2d().normalized().dot(x0.to_2d().normalized()) - 1) > 1e-4:
                    errs.append((bearing, deg, n, round(tilt, 3)))
            if any(abs(a - b) > 1e-5 for ra, rb in zip(house.matrix_world, h0) for a, b in zip(ra, rb)):
                errs.append((bearing, deg, 'house moved'))
            trees.update({o.name: tree(o) for o in near if o.parent and o.parent.name == pivot.name})
            new = sorted({n for n, tr in trees.items() if any(tree(g).overlap(tr) for g in gun)} - base)
            if new:
                clips[f'{bearing:+.0f}/{deg:+.0f}'] = new
    pivot.rotation_euler.z = 0
    hd_elevation.set_elevation(elev, 0)
    bpy.context.view_layer.update()
    check(f"{t['slot']} elevates about its trunnions", not errs, errs[:6])
    check(f"{t['slot']} clear at its elevation limits", not clips, clips)
hard = next(w for w in man['weapons'] if w['mount'] == 'HARDPOINT')
check('hardpoint muzzle at rest', (wpos(hard['muzzle']['anchor']) - Vector(hard['muzzle']['at'])).length < 1e-3)

# Ramp.
ramp = man['ramp']
hinge = bpy.data.objects[ramp['hinge']]
toe = bpy.data.objects['Hull aft ventral ramp toe']


def lowest(o):
    return min((o.matrix_world @ v.co).z for v in o.data.vertices)


ground = min(lowest(o) for o in meshes if ' foot pad ' in o.name)   # the landing pads stand on the ground
t0 = lowest(toe)
hinge.rotation_euler.y -= math.radians(ramp['ground_contact_deg'])
bpy.context.view_layer.update()
t1 = lowest(toe)
check('ramp lowers to the ground', t1 < t0 and abs(t1 - ground) < 0.5,
      {'toe_closed_z': round(t0, 3), 'toe_open_z': round(t1, 3), 'pad_bottom_z': round(ground, 3)})
hinge.rotation_euler.y += math.radians(ramp['ground_contact_deg'])
bpy.context.view_layer.update()

# Rigid root move, effects included.
fx = [o for o in bpy.data.objects if o.name.startswith('SWFX anchor')]
movers = meshes + fx
rest = {o.name: o.matrix_world.copy() for o in movers}
M = Matrix.Translation((140, -60, 35)) @ Euler((0, math.radians(12), math.radians(70))).to_matrix().to_4x4() @ Matrix.Scale(1.7, 4)
root.matrix_world = M @ root.matrix_world
bpy.context.view_layer.update()
worst = max(max(abs(a - b) for ra, rb in zip(o.matrix_world, M @ rest[o.name]) for a, b in zip(ra, rb)) for o in movers)
check('root moves rigidly', worst < 1e-3, {'max_matrix_error': worst, 'effect_anchors': len(fx)})
if man.get('effects'):
    check('effect anchors', len(fx) == sum(man['effects']['anchors'].values()), len(fx))

out = {'asset': man['asset'], 'state': STATE, 'ok': not fails, 'failed': fails, 'checks': checks}
(DIR / 'validation.json').write_text(json.dumps(out, indent=1) + '\n', newline='\n')
print('VERIFY', 'OK' if not fails else f'FAILED {fails}', len(checks), 'checks')
sys.exit(1 if fails else 0)
