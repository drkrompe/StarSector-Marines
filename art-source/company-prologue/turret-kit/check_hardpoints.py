"""Report fixed mounts that clip their hull; run inside a hull's built .blend.

  blender --background <model>.blend --python check_hardpoints.py [-- --tol 0.15 --json PATH]

A hardpoint from build_hardpoint is the meshes '<prefix> hardpoint <key>' (and its
'recoil k', 'rotor', 'missile k' groups) under one mount empty. For every vertex of
every hardpoint mesh this tests whether it lies inside another solid of the ship (an
odd number of crossings along a ray) deeper than --tol units below that solid's
surface. Touching the bay it is sunk in is not a clash; passing through a pod wall,
a frame, the neck or another weapon is. Prints one line per mount and exits non-zero
when anything clips (2 when it finds no mount at all, so a file with nothing to check cannot
pass silently); a hull build imports it and calls clashes() after its bake (build_lasher.py
fails on any clip). The turrets have their own arc
sweep (each hull's mounts module); this is the same guarantee for fixed mounts.
"""
import argparse
import json
import sys
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

def clashes(tol=.15, scene=None):
    """{mount name: [(other object, deepest vertex depth, vertices inside), ...]}."""
    scene = scene or bpy.context.scene
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    fx = {o for c in bpy.data.collections if c.name.startswith('SWFX') for o in c.all_objects}
    mounts = {}
    for ob in scene.objects:
        # A kit hardpoint is '<prefix> hardpoint <key>' on a mount empty whose own name starts
        # with that prefix ('Weapon WS 004 mount', 'Hardpoint 1 WS 008 pivot'), at the top level
        # or under a hull root alike. A hull part that merely says 'hardpoint' ('Hull gantry
        # hardpoint well', parented to the root) is not one.
        if ob.type == 'MESH' and ' hardpoint ' in ob.name and ob.parent is not None and ob.parent.type == 'EMPTY':
            prefix = ob.name.split(' hardpoint ')[0]
            if ob.parent.name.startswith(prefix+' '):
                m = ob.parent
                mounts.setdefault(m.name, set()).update(c for c in m.children_recursive if c not in fx)
    weapon = set().union(*mounts.values()) if mounts else set()
    solids = {}
    for ob in scene.objects:
        if ob.type != 'MESH' or ob in weapon or ob in fx:   # the effects kit's flashes wrap muzzles by design,
            # and are neither hull nor weapon (they ride the muzzle anchors, so a mount's
            # children include them too)
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        if me.polygons:
            ws = [ev.matrix_world @ v.co for v in me.vertices]
            solids[ob.name] = (Vector(map(min, *ws)), Vector(map(max, *ws)), BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                                   [tuple(p.vertices) for p in me.polygons]))
        ev.to_mesh_clear()
    out = {}
    for mname, parts in sorted(mounts.items()):
        pts = []
        for ob in parts:
            if ob.type == 'MESH':
                pts += [ob.matrix_world @ v.co for v in ob.evaluated_get(dg).data.vertices]
        lo = Vector(map(min, *pts)) if pts else None
        hi = Vector(map(max, *pts)) if pts else None
        hits = []
        for name, (slo, shi, t) in solids.items():
            if any(shi[i] < lo[i] or slo[i] > hi[i] for i in range(3)):
                continue
            found, deep, at = 0, 0.0, None
            for p in pts:
                if any(p[i] < slo[i] or p[i] > shi[i] for i in range(3)):
                    continue
                n = t.find_nearest(p)
                if n[0] is None or n[3] < tol:
                    continue
                # inside: odd crossings straight up
                c, o = 0, p.copy()
                while True:
                    h = t.ray_cast(o, Vector((0, 0, 1)), 1e4)
                    if h[0] is None:
                        break
                    c += 1
                    o = h[0]+Vector((0, 0, 1e-4))
                    if c > 64:
                        break
                if c % 2:
                    found += 1
                    if n[3] > deep:
                        deep, at = n[3], p
            if found:
                hits.append((name, round(deep, 3), found, [round(v, 2) for v in at]))
        out[mname] = {'clips': sorted(hits, key=lambda h: -h[1]), 'bounds': [list(lo), list(hi)] if pts else None}
    return out


if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument('--tol', type=float, default=.15)
    ap.add_argument('--json', default='')
    args = ap.parse_args(argv)
    res = clashes(args.tol)
    if not res:
        print('NO MOUNTS: no kit hardpoint (a \'<prefix> hardpoint <key>\' mesh on a \'<prefix> ...\' empty) '
              'in this file, so nothing was checked')
        sys.exit(2)
    bad = 0
    for m, r in res.items():
        if r['clips']:
            bad += 1
            print(f'CLIP {m}: ' + ', '.join(f'{n} (depth {d}, {c} vertices, deepest at {a})' for n, d, c, a in r['clips'][:8]))
        else:
            print(f'OK   {m}: clear of the hull (tolerance {args.tol})')
    if args.json:
        with open(args.json, 'w') as fh:
            json.dump({m: {'clips': r['clips']} for m, r in res.items()}, fh, indent=1)
    if bad:
        sys.exit(1)
