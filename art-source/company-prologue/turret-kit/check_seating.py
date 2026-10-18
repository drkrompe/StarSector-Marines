"""Report turrets that do not sit on their hull; run inside the hull's built .blend.

  blender --background <model>.blend --python check_seating.py

For every 'Weapon <slot> barbette', casts rays straight down at 24 points just
outside the barbette's foot flange, through everything that is not a weapon.
It reports the largest gap between the flange's lowest point and the hull
surface under it. A positive gap means daylight under the barbette (floating).
"""
import json
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

dg = bpy.context.evaluated_depsgraph_get()
hull = []
for ob in bpy.context.scene.objects:
    if ob.type != 'MESH' or ob.name.startswith('Weapon '):
        continue
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    if me.polygons:
        hull.append(BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                         [tuple(p.vertices) for p in me.polygons]))
    ev.to_mesh_clear()

out = []
for ob in sorted(bpy.data.objects, key=lambda o: o.name):
    if not (ob.name.startswith('Weapon ') and ob.name.endswith(' barbette')) or ob.type != 'MESH':
        continue
    pts = [o.matrix_world @ v.co for o in [ob]+[ch for ch in ob.children if ch.type == 'MESH']
           for v in o.data.vertices]
    c = ob.matrix_world.translation
    rad = max((Vector((p.x, p.y)) - Vector((c.x, c.y))).length for p in pts)
    bottom = min(p.z for p in pts)
    top = max(p.z for p in pts)
    worst, misses = -1e9, 0
    for k in range(24):
        a = 2*math.pi*k/24
        for f in (.6, .97):
            o = Vector((c.x+f*rad*math.cos(a), c.y+f*rad*math.sin(a), top+50))
            best = None
            for t in hull:
                hit = t.ray_cast(o, Vector((0, 0, -1)))[0]
                if hit is not None and (best is None or hit.z > best):
                    best = hit.z
            if best is None:
                misses += 1
                continue
            worst = max(worst, bottom-best)
    out.append({'barbette': ob.name, 'foot_bottom': round(bottom, 2), 'radius': round(rad, 2),
                'max_gap_below_foot': round(worst, 2), 'rays_with_no_hull': misses})
print('SEATING ' + json.dumps(out))
