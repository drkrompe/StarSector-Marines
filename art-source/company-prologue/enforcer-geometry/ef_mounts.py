"""The nine weapon slots of enforcer.ship: five medium ballistic turrets and
four small missile hardpoints.

Turrets stand on the carapace: WS 001 / WS 005 on its aft quarters (resting
broadside, 90 deg to port and starboard), WS 002 / WS 004 on its bow
shoulders (resting 30 deg off the bow) and WS 003 on the centreline ahead of
the centre well (resting ahead). Each is a fixed barbette plus a trainable top
parented to `Turret <i> pivot`, an empty at the top of the traverse gap, so a
shot trains a turret by turning its pivot about hull +Z. Turret i counts the
turret slots in .ship order (1 = WS 001 ... 5 = WS 005); the slot id is a
custom property on the pivot. The guns are generic twin medium cannon: the
variants mount anything from flak to heavy mortars, and a twin barrel reads
as a warship's gun at the distances this hull is seen from.

The hardpoints (WS 006-009) are fixed launchers in the missile pods' forward
faces. Every slot has `Weapon <id> anchor` (+X along the slot angle); every
gun has a muzzle empty, +X along the line of fire: `Anchor turret <i> barrel
<k> muzzle` on the turrets and `Anchor weapon <id> muzzle` on the launchers,
the names the shared VFX kit (../second-wind-vfx) looks for.
"""
import math
import bpy
from ef_kit import parent

SLOTS = [
    {'id': 'WS 001', 'location': (-15, 44), 'angle': 90, 'arc': 210, 'mount': 'TURRET', 'size': 'MEDIUM', 'type': 'BALLISTIC'},
    {'id': 'WS 002', 'location': (12, 29), 'angle': 30, 'arc': 125, 'mount': 'TURRET', 'size': 'MEDIUM', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (19, 0), 'angle': 0, 'arc': 125, 'mount': 'TURRET', 'size': 'MEDIUM', 'type': 'BALLISTIC'},
    {'id': 'WS 004', 'location': (12, -29), 'angle': -30, 'arc': 125, 'mount': 'TURRET', 'size': 'MEDIUM', 'type': 'BALLISTIC'},
    {'id': 'WS 005', 'location': (-15, -44), 'angle': -90, 'arc': 210, 'mount': 'TURRET', 'size': 'MEDIUM', 'type': 'BALLISTIC'},
    {'id': 'WS 006', 'location': (29, 49), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 007', 'location': (38, 30), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 008', 'location': (38, -30), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 009', 'location': (29, -49), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
]
BARBETTE_R = 7.4
BARBETTE_H = 1.2
GAP = .2
POD_FRONT = {'WS 006': 36.5, 'WS 007': 46.0, 'WS 008': 46.0, 'WS 009': 36.5}
ANCHOR_Z = {}   # slot id -> Z of its anchor, for geometry.json
TURRETS = []    # records for geometry.json
MUZZLES = {}    # slot id -> list of muzzle anchor names


def turret(kit, i, slot):
    x, y = slot['location']
    sid = slot['id']
    z0 = kit.surface(x, y)[0].z
    kit.cylinder(f'Turret {i} barbette', (x, y, z0+BARBETTE_H/2-.4), BARBETTE_R, BARBETTE_H+.8, kit.steel,
                 vertices=20, bevel=.15)
    kit.cylinder(f'Turret {i} traverse gap', (x, y, z0+BARBETTE_H+GAP/2), BARBETTE_R-.5, GAP+.02, kit.dark,
                 vertices=20, bevel=0)
    top = z0+BARBETTE_H+GAP
    pivot = kit.empty(f'Turret {i} pivot', (x, y, top), 0, size=4, kind='SINGLE_ARROW')
    # Trainable top, built pointing +X about the pivot, then parented and turned.
    parts = [
        kit.cylinder(f'Turret {i} gunhouse', (x-.6, y, top+1.5), 6.2, 3.0, kit.trim, vertices=8, bevel=.25),
        kit.box(f'Turret {i} mantlet', (x+5.6, y, top+1.6), (2.0, 5.0, 2.4), kit.steel, .15),
    ]
    muzzles = []
    for k, dy in enumerate((1.25, -1.25), 1):
        parts.append(kit.cylinder(f'Turret {i} barrel {k}', (x+12.0, y+dy, top+1.6), .55, 11.0, kit.steel, 'X', 10, 0))
        parts.append(kit.cylinder(f'Turret {i} barrel {k} muzzle brake', (x+17.2, y+dy, top+1.6), .78, 1.2,
                                  kit.dark, 'X', 10, 0))
        m = kit.empty(f'Anchor turret {i} barrel {k} muzzle', (x+17.8, y+dy, top+1.6), 0, size=1.5)
        parts.append(m)
        muzzles.append(m.name)
    for ob in parts:
        parent(ob, pivot)
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'] = sid
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['train_axis'] = 'hull +Z'
    kit.empty(f'Weapon {sid} anchor', (x, y, top), slot['angle'], size=2)
    ANCHOR_Z[sid] = top
    MUZZLES[sid] = muzzles
    TURRETS.append({'index': i, 'slot': sid, 'pivot': pivot.name, 'location': [x, y, round(top, 2)],
                    'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
                    'fixed': [f'Turret {i} barbette', f'Turret {i} traverse gap'],
                    'trained': [o.name for o in parts], 'muzzles': muzzles})


def hardpoint(kit, slot):
    x, y = slot['location']
    sid = slot['id']
    front = POD_FRONT[sid]
    z = 1.0
    kit.box(f'Weapon {sid} launcher', ((x-3+front+.6)/2, y, z), (front+.6-(x-3), 5.6, 5.0), kit.steel, .12)
    for k, (dy, dz) in enumerate(((1.3, 1.2), (-1.3, 1.2), (1.3, -1.2), (-1.3, -1.2)), 1):
        kit.cylinder(f'Weapon {sid} tube {k}', (front+.65, y+dy, z+dz), .85, .3, kit.dark, 'X', 10, 0)
    m = kit.empty(f'Anchor weapon {sid} muzzle', (front+.8, y, z), 0, size=1.5)
    kit.empty(f'Weapon {sid} anchor', (x, y, z), slot['angle'], size=2)
    ANCHOR_Z[sid] = z
    MUZZLES[sid] = [m.name]


def build(kit):
    ANCHOR_Z.clear()
    TURRETS.clear()
    MUZZLES.clear()
    i = 0
    for slot in SLOTS:
        if slot['mount'] == 'TURRET':
            i += 1
            turret(kit, i, slot)
        else:
            hardpoint(kit, slot)


def clearance(names_a, names_b):
    """Pairs of meshes that overlap (BVH), for the trained barrels against the hull."""
    from mathutils.bvhtree import BVHTree
    dg = bpy.context.evaluated_depsgraph_get()

    def tree(name):
        ob = bpy.data.objects[name]
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        verts = [ob.matrix_world @ v.co for v in me.vertices]
        polys = [tuple(p.vertices) for p in me.polygons]
        ev.to_mesh_clear()
        return BVHTree.FromPolygons(verts, polys)
    hits = []
    for a in names_a:
        ta = tree(a)
        for b in names_b:
            if ta.overlap(tree(b)):
                hits.append((a, b))
    return hits
