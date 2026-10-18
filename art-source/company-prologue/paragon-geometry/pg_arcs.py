"""Firing lines: every turret's game arc must be clear of its own hull, and the hull is shaped to make it so.

The owner's rule (pass 1): turrets are not lifted out of the hull to see over it;
the hull is shaped the way a brawling capital's would be, so each mount sits low
in its well and the plating falls away across the arc it fires through.

Each turret sits in a dimple (pg_turrets.well) that clears its near field. `check(kit)` then
casts rays from each turret's barrel line (BARREL_H above the well rim, along the
well's axis) across its paragon.ship arc in 5-degree steps, level and slightly
depressed, and reports the share of the arc that is clear and what blocks the
rest. A blocker is answered by reshaping the part (lowering the bow module, for
instance), never by cutting wedges round the gun, which leaves the mount on a
pedestal. Missile slots are reported but not held to it: a missile leaves its
launcher upward and steers.
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
import pg_turrets

BARREL_H = {'LARGE': 4.5, 'MEDIUM': 3.5, 'SMALL': 2.5}   # barrel line above the well rim (units)
DEPRESS = {'LARGE': 1.5, 'MEDIUM': 1.5, 'SMALL': 1.5}    # slight depression the line must also clear (deg)
RANGE = 400.0
STEP = 5.0
def _trees():
    dg = bpy.context.evaluated_depsgraph_get()
    out = []
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.startswith('Weapon ') or ob.name.startswith('Engine ') \
                or ob.name.startswith('Shield ') or ob.name.startswith('Temporary'):
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        out.append((ob.name, BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                                 [tuple(p.vertices) for p in me.polygons])))
        ev.to_mesh_clear()
    return out


def _origin(rec):
    p = Vector(rec['pivot_location'])
    n = Vector(rec['train_axis'])
    rim = p+n*(rec['well_depth']-pg_turrets.SPEC[rec['size']]['collar'])  # back up to the well rim
    return rim+n*BARREL_H[rec['size']], n


def _dirs(rec):
    a0, arc = rec['angle_deg'], rec['arc_deg']
    k = max(1, int(round(arc/STEP)))
    return [a0-arc/2+arc*i/k for i in range(k+1)] if arc < 360 else [STEP*i for i in range(int(360/STEP))]


def check(kit, trees=None):
    trees = trees or _trees()
    report = []
    for rec in pg_turrets.records():
        if rec['mount'] != 'TURRET':
            continue
        o, n = _origin(rec)
        r_well = pg_turrets.SPEC[rec['size']]['r']+1.0
        blocked = []
        for az in _dirs(rec):
            for el in (0.0, -DEPRESS[rec['size']]):
                d = Vector((math.cos(math.radians(az))*math.cos(math.radians(el)),
                            math.sin(math.radians(az))*math.cos(math.radians(el)), math.sin(math.radians(el))))
                start = o+Vector((d.x, d.y, 0)).normalized()*r_well*.6
                hit = None
                for name, t in trees:
                    loc, _, _, dist = t.ray_cast(start, d, RANGE)
                    if loc is not None and (hit is None or dist < hit[1]):
                        hit = (name, dist)
                if hit:
                    blocked.append((az, el, hit[0], round(hit[1], 1)))
        n_rays = 2*len(_dirs(rec))
        report.append({'id': rec['id'], 'size': rec['size'], 'type': rec['type'], 'arc_deg': rec['arc_deg'],
                       'clear_share': round(1-len(blocked)/n_rays, 3),
                       'blocked_by': sorted({b[2] for b in blocked}), 'blocked': blocked})
    return report
