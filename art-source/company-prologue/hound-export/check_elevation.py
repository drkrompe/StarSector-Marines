"""Sweep WS 002's elevation over its whole traverse arc and find where the gun first clips.

  blender --background --factory-startup BUILD.blend --python check_elevation.py -- [--step 1] [--bearing-step 10] [--json OUT]

BUILD is an export carrying `Weapon WS 002 elevation` (export_hound.py or elevate_export.py).
At each bearing of the arc, the gun's own parts (hd_elevation.elevating_parts) are tested
against every other mesh (the house that trains with it, the barbette, the hull) by BVH
triangle overlap. Contact already present at rest is embedding by construction (the drum and
trunnions sit in the cheeks) and is not counted; the first new contact lifting from 0 and
depressing from 0 sets the clean range at that bearing. The limits are the range clean at every
bearing, and the run checks the empty's stored limits against it (exit 1 if they exceed it).
"""
from pathlib import Path
import json
import math
import sys
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
import hd_elevation  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
STEP = float(argv[argv.index('--step') + 1]) if '--step' in argv else 1.0
BSTEP = float(argv[argv.index('--bearing-step') + 1]) if '--bearing-step' in argv else 10.0
OUT = argv[argv.index('--json') + 1] if '--json' in argv else None
S = hd_elevation.SLOT
pivot = bpy.data.objects[f'Weapon {S} pivot']
elev = bpy.data.objects[f'Weapon {S} elevation']
arc = pivot.get('arc_deg', 290)
gun = hd_elevation.elevating_parts(bpy.data.objects)
gun_set = set(gun)
others = [o for o in bpy.data.objects if o.type == 'MESH' and o not in gun_set and not o.hide_render]
upd = bpy.context.view_layer.update


def bvh(o):
    mw = o.matrix_world
    return BVHTree.FromPolygons([mw @ v.co for v in o.data.vertices], [p.vertices[:] for p in o.data.polygons])


def bound(objs):
    pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
    return Vector(map(min, *pts)), Vector(map(max, *pts))


centre = elev.matrix_world.translation.copy()
reach = max((o.matrix_world @ Vector(c) - centre).length for o in gun for c in o.bound_box) + .5
near = []
for o in others:
    lo, hi = bound([o])
    d = Vector([max(lo[i] - centre[i], 0, centre[i] - hi[i]) for i in range(3)]).length
    if d < reach:
        near.append(o)
static = {o.name: bvh(o) for o in near if not o.name.startswith(f'Weapon {S} ') or o.parent is None
          or o.parent.name != pivot.name}
moving = [o for o in near if o.name not in static]


def contacts():
    trees = dict(static)
    trees.update({o.name: bvh(o) for o in moving})
    gt = [bvh(g) for g in gun]
    return {n for n, t in trees.items() if any(g.overlap(t) for g in gt)}


bearings = []
b = -arc / 2
while b <= arc / 2 + 1e-6:
    bearings.append(b)
    b += BSTEP
if bearings[-1] < arc / 2:
    bearings.append(arc / 2)
rows = []
for off in bearings:
    pivot.rotation_euler.z = math.radians(off)
    hd_elevation.set_elevation(elev, 0)
    upd()
    base = contacts()
    found = {}
    for sign, limit in ((1, 90), (-1, 30)):
        k, first = 0.0, None
        while k + STEP <= limit + 1e-6:
            k += STEP
            hd_elevation.set_elevation(elev, sign * k)
            upd()
            new = contacts() - base
            if new:
                first = (sign * k, sorted(new))
                break
        found['up' if sign > 0 else 'down'] = first
    rows.append({'bearing_offset_deg': off, 'at_rest': sorted(base),
                 'max_clear_deg': (found['up'][0] - STEP) if found['up'] else 90.0,
                 'first_up_contact': found['up'], 'min_clear_deg': (found['down'][0] + STEP) if found['down'] else -30.0,
                 'first_down_contact': found['down']})
    print(f"ELEV bearing {off:+6.1f}: clear {rows[-1]['min_clear_deg']:+5.1f} .. {rows[-1]['max_clear_deg']:+5.1f}"
          f"  up hits {found['up'][1] if found['up'] else '-'}  down hits {found['down'][1] if found['down'] else '-'}")
pivot.rotation_euler.z = 0
hd_elevation.set_elevation(elev, 0)
upd()
hi = min(r['max_clear_deg'] for r in rows)
lo = max(r['min_clear_deg'] for r in rows)
stored = (elev.get('elevation_min_deg'), elev.get('elevation_max_deg'))
ok = stored[0] is not None and stored[0] >= lo - 1e-6 and stored[1] <= hi + 1e-6
print(f'ELEV clean at every bearing: {lo:+.1f} .. {hi:+.1f}; stored {stored}; {"OK" if ok else "EXCEEDS"}')
if OUT:
    Path(OUT).write_text(json.dumps({'clean_range_deg': [lo, hi], 'stored_deg': stored, 'ok': ok,
                                     'gun_parts': [g.name for g in gun], 'tested_against': len(near), 'rows': rows}, indent=1))
sys.exit(0 if ok else 1)
