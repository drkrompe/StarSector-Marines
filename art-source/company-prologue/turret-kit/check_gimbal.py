"""Build every kit turret headless (no render) and check its gimbal.

  blender --background --factory-startup --python check_gimbal.py -- [--only a,b] [--mpu 0.95]

Per weapon: the gimbal kind measured (open / enclosed / off), the objects and
faces added, the anchors (pivot, elevation, recoil, muzzle) and their positions,
and an elevation sweep: the elevating group (gun and cradle) against the fixed
house and gimbal at each elevation in the weapon's limits (default -5..25 deg),
by BVH overlap. Parts meant to touch (trunnion pins in their bearings, a sleeve
on its barrel) are on the same side and are not tested against each other.
--json PATH writes the anchors so a later build can be compared for drift.
"""
import argparse
import json
import math
import sys
from pathlib import Path
import bpy
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
import turret_kit as tk  # noqa: E402
import studio  # noqa: E402

argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--only', default='')
ap.add_argument('--mpu', type=float, default=.99)
ap.add_argument('--json', default='')
ap.add_argument('--no-gimbal', action='store_true')
args = ap.parse_args(argv)

bpy.ops.wm.read_factory_settings(use_empty=True)
MATS = studio.hull_mats()
RING = {'LARGE': 12.5, 'MEDIUM': 9.5, 'SMALL': 5.5}
BOX = {'harpoonpod': 'MEDIUM', 'sabotpod': 'MEDIUM', 'salamanderpod': 'SMALL', 'annihilatorpod': 'MEDIUM',
       'pilum': 'MEDIUM', 'typhoon': 'LARGE', 'none': 'SMALL'}


def tree(objs):
    dg = bpy.context.evaluated_depsgraph_get()
    verts, polys = [], []
    for ob in objs:
        if ob.type != 'MESH':
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        b = len(verts)
        verts += [ob.matrix_world @ v.co for v in me.vertices]
        polys += [tuple(b+i for i in p.vertices) for p in me.polygons]
        ev.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys) if polys else None


names = [n for n in tk.WEAPONS if not args.only or n in args.only.split(',')]
report, anchors = {}, {}
for i, w in enumerate(names):
    s = tk.WEAPONS[w]
    size = tk.sprite(w)['size'] if s['house'] in ('sprite', 'hand') else BOX.get(w, 'MEDIUM')
    r = RING[size]
    piv = bpy.data.objects.new(f'W{w} pivot', None)
    bpy.context.scene.collection.objects.link(piv)
    piv.location = (i*60, 0, 0)
    t = tk.build(w, f'W{w}', piv, r, MATS, args.mpu, gimbal=not args.no_gimbal)
    elev = t['elevation']
    bpy.context.view_layer.update()
    added = [o for o in t['parts'] if ' gimbal ' in o.name or ' cradle ' in o.name]
    added_names = ' '.join(o.name for o in added)
    kind = elev.get('gimbal', 'traverse only' if added else 'off')
    moving_set = set(elev.children_recursive)
    elevating = [o for o in t['parts'] if o in moving_set]
    fixed = [o for o in t['parts'] if o not in moving_set]
    lo, hi = elev.get('elevation_min_deg', -5), elev.get('elevation_max_deg', 25)
    clashes = {}
    tf = tree(fixed)
    INTENDED = ('pin x gimbal plate#yoke', 'pin x gimbal steel')

    def pairs(a):
        elev.rotation_euler = (0, math.radians(-a), 0)
        bpy.context.view_layer.update()
        out = set()
        for o in elevating:
            to = tree([o])
            if not (to and tf and to.overlap(tf)):
                continue
            for fo in fixed:
                tfo = tree([fo])
                if tfo and to.overlap(tfo):
                    out.add(f'{o.name.split(" ", 1)[1]} x {fo.name.split(" ", 1)[1]}')
        return {p for p in out if not p.endswith(INTENDED)}
    rest = pairs(0)
    new_at_rest = sorted(p for p in rest if ' gimbal ' in p or ' cradle ' in p or p.startswith('cradle'))
    if new_at_rest:
        clashes['rest (gimbal parts)'] = new_at_rest
    for a in (lo, hi):
        extra = sorted(pairs(a)-rest)
        if extra:
            clashes[f'{a:+.0f} deg (not at rest)'] = extra
    elev.rotation_euler = (0, 0, 0)
    bpy.context.view_layer.update()
    anchors[w] = {e.name.split(' ', 1)[1]: [round(v, 4) for v in (piv.matrix_world.inverted() @ e.matrix_world).translation]
                  for e in [elev, *t['muzzles'], *t['moving']]}
    report[w] = {'kind': kind, 'added_objects': len(added),
                 'added_faces': sum(len(o.data.polygons) for o in added),
                 'faces': sum(len(o.data.polygons) for o in t['parts'] if o.type == 'MESH'),
                 'elevation': [lo, hi], 'clashes': clashes}
    print(f'GIMBAL {w:14s} {kind:13s} +{len(added)} objs +{report[w]["added_faces"]} faces '
          f'(total {report[w]["faces"]}) elev {lo}..{hi} clashes {len(clashes)}')
    for a, h in clashes.items():
        print(f'    {a}: {h[:8]}')
if args.json:
    Path(args.json).write_text(json.dumps({'report': report, 'anchors': anchors}, indent=1))
