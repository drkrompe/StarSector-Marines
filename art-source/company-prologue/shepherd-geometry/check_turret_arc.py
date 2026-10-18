"""BVH-check WS 007's trained parts against every other mesh at rest, both arc limits and every 10 deg.

Run inside Blender after a build (from this folder):
  blender --background --factory-startup --python build_shepherd.py --python check_turret_arc.py -- --no-save --views ""
Prints CLASH angle <deg>: <count>; fix a clash by lifting the barbette (sh_mounts.BARBETTE_LIFT), never the slot.
"""
import math, sys
sys.path.insert(0, '.')
import bpy
from mathutils import Matrix
from mathutils.bvhtree import BVHTree
import sh_mounts

s = sh_mounts.SLOTS[0]
x, y = s['location']
pre = s['prefix']
dg = bpy.context.evaluated_depsgraph_get()

def tree(ob):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    t = BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices], [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear()
    return t

meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH']
trained = [o for o in meshes if o.get('trained')]
others = [o for o in meshes if not o.name.startswith(pre)]
own_fixed = [o for o in meshes if o.name.startswith(pre) and not o.get('trained')]
otrees = {o.name: tree(o) for o in others + own_fixed}
rest = s['angle']
for target in [rest, rest - s['arc']/2, rest + s['arc']/2] + list(range(-170, 25, 10)):
    sh_mounts.train(trained, x, y, target - rest)
    bpy.context.view_layer.update()
    hits = []
    for t in trained:
        tt = tree(t)
        for n, ot in otrees.items():
            if tt.overlap(ot):
                hits.append((t.name, n))
    print(f'CLASH angle {target}: {len(hits)}')
    for h in hits:
        print('   ', h[0], '|', h[1])
    sh_mounts.train(trained, x, y, rest - target)
