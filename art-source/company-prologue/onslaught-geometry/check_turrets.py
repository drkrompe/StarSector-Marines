"""Turret clash and running-light burial check for an integrated build; run inside Blender.

  sh ../../blender.sh --background onslaught-geometry.blend --python check_turrets.py

Every trainable part (a child of a Weapon <slot> pivot) against every mesh not of
its slot, each running light for burial, and any trainable part whose centre is
inside another solid. Expect 0 trainable clashes.
"""
import bpy, re
from mathutils import Vector
from mathutils.bvhtree import BVHTree
dg = bpy.context.evaluated_depsgraph_get()
def tree(ob):
    ev = ob.evaluated_get(dg); me = ev.to_mesh()
    t = BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices], [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear(); return t
meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH']
trees = {o.name: tree(o) for o in meshes}
slot = lambda n: (re.match(r'Weapon (WS \d+) ', n) or [None, None])[1]
hits = []
for o in meshes:
    if not (o.parent and o.parent.name.endswith(' pivot')):
        continue
    s = slot(o.name)
    for p in meshes:
        if slot(p.name) == s or p.name.startswith('Light '):
            continue
        if trees[o.name].overlap(trees[p.name]):
            hits.append((o.name, p.name))
print('TRAINABLE CLASHES', len(hits))
for a, b in hits:
    print('  ', a, '|', b)
# Lights: buried if a ray from the centre in its six +hemisphere directions hits within 0.5
print('LIGHTS')
for l in [o for o in meshes if o.name.startswith('Light ')]:
    c = l.matrix_world.translation
    blocked = 0
    for d in [(0,0,1),(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(.5,.5,.7),(-.5,-.5,.7)]:
        for n, t in trees.items():
            if n == l.name or n.startswith('Light '):
                continue
            p, _, _, dist = t.ray_cast(c, Vector(d).normalized(), .7)
            if p is not None:
                blocked += 1; break
    print('  ', l.name, tuple(round(v, 1) for v in c), 'blocked dirs', blocked, '/7')
print('ENCLOSED (trainable part centre inside another solid)')
for o in meshes:
    if not (o.parent and o.parent.name.endswith(' pivot')):
        continue
    c = o.matrix_world.translation
    s = slot(o.name)
    for p in meshes:
        if slot(p.name) == s or p.name.startswith('Light '):
            continue
        n, z = 0, Vector(c)
        t = trees[p.name]
        while True:
            hit, _, _, d = t.ray_cast(z, Vector((0, 0, 1)))
            if hit is None:
                break
            n += 1
            z = hit+Vector((0, 0, 1e-3))
        if n % 2:
            print('  ', o.name, 'inside', p.name)
