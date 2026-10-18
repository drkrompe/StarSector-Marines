"""The nine weapon slots of lasher.ship: three turrets, four hardpoints, two
hidden system mounts.

Turrets (WS 005, WS 006 on the wing body either side of the pinched spine,
WS 007 on the bow ring) are a fixed barbette plus a trainable top parented
to `Turret <i> pivot`, an empty at the top of the traverse gap, so a shot
trains a turret by turning its pivot about hull +Z. Each rests at its slot
angle (WS 005 60 deg to port, WS 006 60 deg to starboard, WS 007 ahead).
Turret i counts the turret slots in .ship order: 1 = WS 005, 2 = WS 006,
3 = WS 007; the slot id is a custom property on the pivot.

The hardpoints are fixed: the ballistic pair WS 002 / WS 003 sit in the gun
pods' forward gaps, the missile pair WS 001 / WS 004 in the missile pods'.
WS 008 / WS 009 are HIDDEN SYSTEM slots (the ammo-feed system) and carry
only an anchor. Every slot has `Weapon <id> anchor` (+X along the slot
angle); every gun has `Weapon <id> muzzle anchor` at its muzzle, +X along
the line of fire. Guns are generic placeholders sized by slot: nothing
smaller reads at the distances this hull is seen from.
"""
import math
import bpy
from mathutils import Matrix, Vector
from ls_kit import parent

SLOTS = [
    {'id': 'WS 001', 'location': (11, 27), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 002', 'location': (24, 16), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (24, -15), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 004', 'location': (11, -27), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 005', 'location': (4, 10), 'angle': 60, 'arc': 270, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 006', 'location': (4, -10), 'angle': -60, 'arc': 270, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 007', 'location': (37, 0), 'angle': 0, 'arc': 270, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 008', 'location': (-15, 29), 'angle': 160, 'arc': 60, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 009', 'location': (-15, -29), 'angle': 200, 'arc': 60, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
]
BARBETTE_R = 4.6
BARBETTE_H = 1.0
GAP = .15
ANCHOR_Z = {}   # slot id -> Z of its anchor, for geometry.json
TURRETS = []    # records for geometry.json
MUZZLES = {}    # slot id -> list of muzzle anchor names


def turret(kit, i, slot):
    x, y = slot['location']
    sid = slot['id']
    z0 = kit.surface(x, y)[0].z
    kit.cylinder(f'Turret {i} barbette', (x, y, z0+BARBETTE_H/2-.3), BARBETTE_R, BARBETTE_H+.6, kit.steel,
                 vertices=12, bevel=.12)
    kit.cylinder(f'Turret {i} traverse gap', (x, y, z0+BARBETTE_H+GAP/2), BARBETTE_R-.35, GAP+.02, kit.dark,
                 vertices=12, bevel=0)
    top = z0+BARBETTE_H+GAP
    pivot = kit.empty(f'Turret {i} pivot', (x, y, top), 0, size=2.5, kind='SINGLE_ARROW')
    pivot.rotation_euler = (0, 0, 0)
    # Trainable top, built pointing +X about the pivot, then parented and turned.
    parts = [
        kit.cylinder(f'Turret {i} gunhouse', (x-.3, y, top+.9), 3.8, 1.8, kit.orange, vertices=8, bevel=.15),
        kit.box(f'Turret {i} mantlet', (x+3.5, y, top+1.0), (1.2, 3.0, 1.4), kit.steel, .1),
    ]
    muzzles = []
    for k, dy in enumerate((.75, -.75), 1):
        parts.append(kit.cylinder(f'Turret {i} barrel {k}', (x+6.4, y+dy, top+1.0), .32, 5.0, kit.steel, 'X', 8, 0))
        m = kit.empty(f'Turret {i} barrel {k} muzzle anchor', (x+8.9, y+dy, top+1.0), 0, size=1)
        parts.append(m)
        muzzles.append(m.name)
    for ob in parts:
        parent(ob, pivot)
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'] = sid
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['train_axis'] = 'hull +Z'
    a = kit.empty(f'Weapon {sid} anchor', (x, y, top), slot['angle'], size=2)
    ANCHOR_Z[sid] = top
    MUZZLES[sid] = muzzles
    TURRETS.append({'index': i, 'slot': sid, 'pivot': pivot.name, 'location': [x, y, round(top, 2)],
                    'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
                    'fixed': [f'Turret {i} barbette', f'Turret {i} traverse gap'],
                    'trained': [o.name for o in parts], 'muzzles': muzzles})


def hardpoint(kit, slot):
    x, y = slot['location']
    sid = slot['id']
    if slot['type'] == 'BALLISTIC':
        z = 1.2
        kit.box(f'Weapon {sid} breech', (x+2.5, y, z), (5, 2.0, 1.8), kit.steel, .1)
        kit.cylinder(f'Weapon {sid} barrel', (x+6.5, y, z), .4, 6.0, kit.steel, 'X', 8, 0)
        kit.cylinder(f'Weapon {sid} muzzle brake', (x+9.3, y, z), .55, .9, kit.dark, 'X', 8, 0)
        m = kit.empty(f'Weapon {sid} muzzle anchor', (x+9.8, y, z), 0, size=1)
    else:
        z = 1.4
        kit.box(f'Weapon {sid} launcher', (x+1.6, y, z), (5.6, 2.8, 2.6), kit.steel, .1)
        for k, (dy, dz) in enumerate(((.65, .6), (-.65, .6), (.65, -.6), (-.65, -.6)), 1):
            kit.cylinder(f'Weapon {sid} tube {k}', (x+4.45, y+dy, z+dz), .42, .2, kit.dark, 'X', 8, 0)
        m = kit.empty(f'Weapon {sid} muzzle anchor', (x+4.6, y, z), 0, size=1)
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
        elif slot['mount'] == 'HARDPOINT':
            hardpoint(kit, slot)
        else:
            x, y = slot['location']
            kit.empty(f'Weapon {slot["id"]} anchor', (x, y, 0), slot['angle'], size=2)
            ANCHOR_Z[slot['id']] = 0.0


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
