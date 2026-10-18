"""Measure a model's enclosed volume the one way ship_scale.py defines it; run inside Blender.

  blender --background <package>/<model>.blend --python ship_scale_measure.py -- <hull key>

The hull key names an entry of ship_scale.SOLIDS: the solids a crew, cargo or
craft could be inside (pressure hull, holds, hangars, boat bays), never drive
pods, engine cans, armour plating laid over the hull or open structure. The
volume is their union, integrated as vertical ray intervals per column, so
overlapping solids are not counted twice. Prints one JSON line.
"""
import json
import sys
from pathlib import Path
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ship_scale


def union_volume(names, step):
    dg = bpy.context.evaluated_depsgraph_get()
    trees, lo, hi = [], Vector((1e9,)*3), Vector((-1e9,)*3)
    for n in names:
        ev = bpy.data.objects[n].evaluated_get(dg)
        me = ev.to_mesh()
        pts = [ev.matrix_world @ v.co for v in me.vertices]
        trees.append(BVHTree.FromPolygons(pts, [tuple(p.vertices) for p in me.polygons]))
        for p in pts:
            lo, hi = Vector(map(min, lo, p)), Vector(map(max, hi, p))
        ev.to_mesh_clear()
    total = 0.0
    x = lo.x+step/2
    while x < hi.x:
        y = lo.y+step/2
        while y < hi.y:
            spans = []
            for t in trees:
                zs, z = [], hi.z+1
                while True:
                    p, _, _, _ = t.ray_cast(Vector((x, y, z)), Vector((0, 0, -1)))
                    if p is None:
                        break
                    zs.append(p.z)
                    z = p.z-1e-4
                spans += [(zs[i+1], zs[i]) for i in range(0, len(zs)-1, 2)]
            spans.sort()
            cur = None
            for a, b in spans:
                if cur and a <= cur[1]:
                    cur[1] = max(cur[1], b)
                else:
                    if cur:
                        total += cur[1]-cur[0]
                    cur = [a, b]
            if cur:
                total += cur[1]-cur[0]
            y += step
        x += step
    return total*step*step, lo, hi


key = sys.argv[sys.argv.index('--')+1]
spec = ship_scale.SOLIDS[key]
names = [ob.name for ob in bpy.data.objects if ob.type == 'MESH' and spec['include'](ob.name)]
vol, lo, hi = union_volume(names, spec['step'])
print(json.dumps({'hull': key, 'enclosed_units3': round(vol), 'solids': names,
                  'solid_extent_units': [round(h-l, 1) for l, h in zip(lo, hi)]}))
