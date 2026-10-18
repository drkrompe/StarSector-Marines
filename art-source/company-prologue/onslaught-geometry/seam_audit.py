"""Find hull sections that don't line up; run inside Blender on a built .blend.

  sh ../../blender.sh --background onslaught-geometry.blend --python seam_audit.py -- [--step 2] [--json OUT]

Owner: "there are sections of hull that don't line up". On a plan grid this
casts a vertical ray through every hull, armour and bridge solid and reports
  gaps    air between two solids in one column (a part overhanging another
          with a slot of daylight under it: a floating edge) - 0.4 to 30 units;
  steps   where the topmost solid changes between neighbouring columns and
          the top surface jumps by more than 3 units (an edge that does not
          meet its neighbour; deliberate tiers show up here too, so read the
          object pairs);
  bottom  the same jump test on the lowest surface (belly seams).
Findings are grouped by object pair and merged into regions with a centre,
an extent and the worst value, most severe first.
"""
import json
import math
import sys
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
STEP = float(args[args.index('--step')+1]) if '--step' in args else 2.0
OUT = args[args.index('--json')+1] if '--json' in args else None
FAMILIES = ('Hull', 'Armour', 'Bridge')

dg = bpy.context.evaluated_depsgraph_get()
trees, lo, hi = {}, Vector((1e9,)*3), Vector((-1e9,)*3)
for ob in bpy.context.scene.objects:
    if ob.type != 'MESH' or ob.name.split(' ')[0] not in FAMILIES:
        continue
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    pts = [ev.matrix_world @ v.co for v in me.vertices]
    if not pts:
        ev.to_mesh_clear()
        continue
    trees[ob.name] = BVHTree.FromPolygons(pts, [tuple(p.vertices) for p in me.polygons])
    for p in pts:
        lo, hi = Vector(map(min, lo, p)), Vector(map(max, hi, p))
    ev.to_mesh_clear()


def column(x, y):
    """[(z_bottom, z_top, name)] solid intervals in this column, sorted by bottom."""
    spans = []
    for name, t in trees.items():
        zs, z = [], hi.z+1
        while True:
            p, _, _, _ = t.ray_cast(Vector((x, y, z)), Vector((0, 0, -1)))
            if p is None:
                break
            zs.append(p.z)
            z = p.z-1e-4
        spans += [(zs[i+1], zs[i], name) for i in range(0, len(zs)-1, 2)]
    return sorted(spans)


nx = int((hi.x-lo.x)/STEP)+1
ny = int((hi.y-lo.y)/STEP)+1
top, bot = {}, {}
gaps = []
for i in range(nx):
    x = lo.x+STEP*(i+.5)
    for j in range(ny):
        y = lo.y+STEP*(j+.5)
        spans = column(x, y)
        if not spans:
            continue
        # merge overlapping spans, remembering which solids bound each air gap
        cur = [spans[0][0], spans[0][1], spans[0][2]]
        for a, b, n in spans[1:]:
            if a <= cur[1]+1e-3:
                if b > cur[1]:
                    cur[1], cur[2] = b, n
            else:
                g = a-cur[1]
                if .4 < g < 30:
                    gaps.append((x, y, g, cur[2], n))
                cur = [a, b, n]
        tz = max(spans, key=lambda s: s[1])
        bz = min(spans, key=lambda s: s[0])
        top[(i, j)] = (tz[1], tz[2])
        bot[(i, j)] = (bz[0], bz[2])


def jumps(field, limit):
    out = []
    for (i, j), (z, n) in field.items():
        for di, dj in ((1, 0), (0, 1)):
            o = field.get((i+di, j+dj))
            if o and o[1] != n and abs(o[0]-z) > limit:
                out.append((lo.x+STEP*(i+.5), lo.y+STEP*(j+.5), abs(o[0]-z), *sorted((n, o[1]))))
    return out


def regions(items):
    """Group (x, y, value, a, b) by object pair, merge neighbours into regions."""
    by = {}
    for x, y, v, a, b in items:
        by.setdefault((a, b), []).append((x, y, v))
    out = []
    for (a, b), pts in by.items():
        left = list(pts)
        while left:
            seed = [left.pop()]
            grown = True
            while grown:
                grown = False
                for p in list(left):
                    if any(abs(p[0]-q[0]) <= STEP*1.5 and abs(p[1]-q[1]) <= STEP*1.5 for q in seed):
                        seed.append(p)
                        left.remove(p)
                        grown = True
            xs, ys, vs = zip(*seed)
            out.append({'pair': [a, b], 'cells': len(seed), 'worst': round(max(vs), 1),
                        'centre': [round(sum(xs)/len(xs), 1), round(sum(ys)/len(ys), 1)],
                        'x': [round(min(xs), 1), round(max(xs), 1)], 'y': [round(min(ys), 1), round(max(ys), 1)]})
    return sorted(out, key=lambda r: -(r['worst']*r['cells']**.5))


report = {'gaps': regions(gaps), 'top_steps': regions(jumps(top, 3.0)), 'bottom_steps': regions(jumps(bot, 3.0))}
for key in report:
    print(f'== {key}: {len(report[key])} regions')
    for r in report[key][:25]:
        print(f"  worst {r['worst']:5.1f}  cells {r['cells']:4d}  at {r['centre']}  x{r['x']} y{r['y']}  {r['pair'][0]} | {r['pair'][1]}")
if OUT:
    with open(OUT, 'w') as f:
        json.dump(report, f, indent=1)
