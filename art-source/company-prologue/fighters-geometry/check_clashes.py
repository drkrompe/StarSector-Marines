"""Overlap sweep for one fighter's integrated pass-2 build; run inside Blender.

  blender --background <id>/<id>-geometry.blend --python check_clashes.py

Writes <id>/clashes.txt next to the .blend. Every mesh is either pass-1 body
or pass-2 detail (the driver tags detail parts detail_pass = 2). Seated
detail is bedded into the body on purpose, so detail-on-body contacts are
only counted per detail part. What the sweep is for:
- detail crossing detail: two fittings that pass through each other, such
  as a strut through a grille, listed pair by pair for review;
- floating detail: a pass-2 part whose surface meets no other part (a plate
  laid on a part the ray-cast did not see, or a part left in mid-air);
- body crossing body, which pass 2 must not change from the blockout.
Contacts inside one fitting (parts sharing their first words) are counted,
not listed. Read the cross-fitting list the way the Hound's and the Broadsword's
were read: most pairs are deliberate (a band round a barrel, a frame on
the glass), and the ones that only cross are the faults.
"""
from pathlib import Path
import bpy
from mathutils.bvhtree import BVHTree

dg = bpy.context.evaluated_depsgraph_get()


def tree(ob):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    t = BVHTree.FromPolygons([ob.matrix_world @ v.co for v in me.vertices], [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear()
    return t


col = next(c for c in bpy.data.collections if c.name.endswith('| geometry'))
obs = sorted((o for o in col.all_objects if o.type == 'MESH'), key=lambda o: o.name)
detail = {o.name for o in obs if o.get('detail_pass') == 2}
trees = {o.name: tree(o) for o in obs}
touch = {o.name: [] for o in obs}
for i, a in enumerate(obs):
    for b in obs[i+1:]:
        if trees[a.name].overlap(trees[b.name]):
            touch[a.name].append(b.name)
            touch[b.name].append(a.name)
def assembly(a, b):
    """Parts of one fitting (a grille's slats and well, a drive's centre body and vanes, the canopy
    frame's pieces) share at least their first two words: their contact is how they are built."""
    wa, wb = a.split(' '), b.split(' ')
    k = 0
    while k < min(len(wa), len(wb)) and wa[k] == wb[k]:
        k += 1
    return k >= 2 and not (wa[0] == 'Hull' and k < 3)  # 'Hull' alone names the whole body


pairs = sorted({tuple(sorted((a, b))) for a in detail for b in touch[a] if b in detail})
dd = [p for p in pairs if not assembly(*p)]
within = len(pairs)-len(dd)
bb = sorted({tuple(sorted((a, b))) for a in touch if a not in detail for b in touch[a] if b not in detail})
floating = sorted(n for n in detail if not touch[n])
lines = [f'{col.name}: {len(obs)} meshes, {len(detail)} pass-2 detail',
         f'detail meeting detail within one fitting: {within} pairs (counted, not listed)',
         f'detail crossing detail across fittings: {len(dd)} pairs']
lines += [f'    {a} | {b}' for a, b in dd]
lines += [f'floating detail (touches nothing): {len(floating)}'] + [f'    {n}' for n in floating]
lines += [f'body crossing body (pass 1): {len(bb)} pairs'] + [f'    {a} | {b}' for a, b in bb]
lines += ['detail bedded into body (part: body parts it meets):']
lines += [f'    {n}: {", ".join(sorted(b for b in touch[n] if b not in detail))}'
          for n in sorted(detail) if any(b not in detail for b in touch[n])]
out = Path(bpy.data.filepath).with_name('clashes.txt')
out.write_text('\n'.join(lines)+'\n', encoding='utf-8')
print('\n'.join(lines[:4+len(dd)+1+len(floating)+1]))
print('CLASHES written', out)
