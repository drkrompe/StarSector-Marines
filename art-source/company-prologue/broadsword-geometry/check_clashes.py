"""Cross-module overlap sweep for an integrated build; run inside Blender.

  blender --background broadsword-geometry.blend --python check_clashes.py

Module agents each see only their own parts, so a pipe one of them routes
can run straight through a chute another one added. This lists every pair
of mesh objects from different modules (the first word of the name) whose
surfaces intersect. Most hits are deliberate: struts, lines and plates
sunk into the hull they attach to. Read the list for parts that merely
cross, such as two fittings passing through each other in mid-air.
"""
import bpy
from mathutils.bvhtree import BVHTree

dg = bpy.context.evaluated_depsgraph_get()


def tree(ob):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    t = BVHTree.FromPolygons([ob.matrix_world @ v.co for v in me.vertices], [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear()
    return t


obs = [o for o in bpy.context.scene.objects if o.type == 'MESH']
module = lambda o: o.name.split(' ')[0]
trees = {o.name: tree(o) for o in obs}
pairs = {}
for i, a in enumerate(obs):
    for b in obs[i+1:]:
        if module(a) != module(b) and trees[a.name].overlap(trees[b.name]):
            pairs.setdefault(tuple(sorted((module(a), module(b)))), []).append((a.name, b.name))
for key, hits in sorted(pairs.items()):
    print(' / '.join(key), len(hits))
    for a, b in hits:
        print('   ', a, '|', b)
