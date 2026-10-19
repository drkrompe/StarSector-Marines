"""The Wolf (P)'s weapon slots: wolf.ship less the two the pirate skin removes.

wolf_d_pirates.skin: "removeWeaponSlots": ["WS 002", "WS 003"], so the aft
pair of small energy turrets (-13, +-12) is gone and the sprite paints
crimson armour domes over their rings (wf_hull). What remains:
  WS 001  TURRET     SMALL   ENERGY   (11, 0)    0    arc 150   Turret 1
  WS 004  HARDPOINT  MEDIUM  ENERGY   (45, 0)    0    arc 5     in the bow fork
  WS 005  HARDPOINT  SMALL   MISSILE  (11, 20)   0    arc 5     port pod
  WS 006  HARDPOINT  SMALL   MISSILE  (11, -19)  0    arc 5     starboard pod
Loadout: wolf_d_pirates_Attack.variant, the pirate fleets' goal variant: an
ion cannon on WS 001, a mining blaster on WS 004, Annihilator rocket pods on
WS 005 / WS 006.

The turret is a fixed barbette plus a trainable top parented to `Turret 1
pivot`, an empty at the top of the traverse gap; a shot trains it by turning
the pivot about hull +Z. It rests at its slot angle (ahead). Every slot has
`Weapon <id> anchor` (+X along the slot angle); every gun a muzzle empty, +X
along the line of fire: `Anchor turret 1 barrel <k> muzzle` on the turret and
`Anchor weapon <id> muzzle` on the hardpoints, the names the shared VFX kit
(../second-wind-vfx) looks for.
"""
import math
import bpy
from wf_kit import parent

SLOTS = [
    {'id': 'WS 001', 'location': (11, 0), 'angle': 0, 'arc': 150, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 004', 'location': (45, 0), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'ENERGY'},
    {'id': 'WS 005', 'location': (11, 20), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 006', 'location': (11, -19), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
]
REMOVED = [
    {'id': 'WS 002', 'location': (-13, 12), 'angle': 90, 'arc': 200, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 003', 'location': (-13, -11), 'angle': -90, 'arc': 200, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY'},
]
LOADOUT = {'WS 001': 'ioncannon', 'WS 004': 'miningblaster', 'WS 005': 'annihilator', 'WS 006': 'annihilator'}
BARBETTE_R = 4.4
BARBETTE_H = 2.0
GAP = .15
ANCHOR_Z = {}
TURRETS = []
MUZZLES = {}


def turret(kit, i, slot):
    x, y = slot['location']
    sid = slot['id']
    z0 = kit.surface(x, y)[0].z
    kit.cylinder(f'Turret {i} barbette', (x, y, z0+BARBETTE_H/2-.4), BARBETTE_R, BARBETTE_H+.8, kit.steel,
                 vertices=20, bevel=.12)
    kit.cylinder(f'Turret {i} traverse gap', (x, y, z0+BARBETTE_H+GAP/2), BARBETTE_R-.35, GAP+.02, kit.dark,
                 vertices=20, bevel=0)
    top = z0+BARBETTE_H+GAP
    pivot = kit.empty(f'Turret {i} pivot', (x, y, top), 0, size=2.5, kind='SINGLE_ARROW')
    parts = [
        kit.cylinder(f'Turret {i} gunhouse', (x-.3, y, top+.9), 3.7, 1.8, kit.steel, vertices=10, bevel=.15),
        kit.box(f'Turret {i} mantlet', (x+3.4, y, top+1.0), (1.4, 3.2, 1.5), kit.steel, .1),
    ]
    muzzles = []
    for k, dy in enumerate((0.0,), 1):
        parts.append(kit.cylinder(f'Turret {i} barrel {k}', (x+6.0, y+dy, top+1.0), .55, 4.6, kit.steel, 'X', 10, 0))
        m = kit.empty(f'Anchor turret {i} barrel {k} muzzle', (x+8.3, y+dy, top+1.0), 0, size=1)
        parts.append(m)
        muzzles.append(m.name)
    for ob in parts:
        parent(ob, pivot)
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'] = sid
    pivot['weapon'] = LOADOUT[sid]
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['train_axis'] = 'hull +Z'
    kit.empty(f'Weapon {sid} anchor', (x, y, top), slot['angle'], size=2)
    ANCHOR_Z[sid] = top
    MUZZLES[sid] = muzzles
    TURRETS.append({'index': i, 'slot': sid, 'weapon': LOADOUT[sid], 'pivot': pivot.name,
                    'location': [x, y, round(top, 2)], 'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'],
                    'fixed': [f'Turret {i} barbette', f'Turret {i} traverse gap'],
                    'trained': [o.name for o in parts], 'muzzles': muzzles})


def hardpoint(kit, slot):
    x, y = slot['location']
    sid = slot['id']
    if slot['type'] == 'ENERGY':   # the mining blaster in the bow channel
        z = .6
        kit.box(f'Weapon {sid} breech', (x, y, z), (5.0, 4.2, 3.4), kit.steel, .15)
        kit.cylinder(f'Weapon {sid} barrel', (x+5.0, y, z), 1.05, 6.0, kit.steel, 'X', 12, 0)
        kit.cylinder(f'Weapon {sid} emitter', (x+8.3, y, z), 1.35, .9, kit.dark, 'X', 12, 0)
        m = kit.empty(f'Anchor weapon {sid} muzzle', (x+8.8, y, z), 0, size=1)
    else:                          # an Annihilator pod in the missile pod's face
        z = .3
        kit.box(f'Weapon {sid} launcher', (x+2.6, y, z), (5.4, 4.6, 3.6), kit.steel, .12)
        for k, (dy, dz) in enumerate(((1.1, .85), (-1.1, .85), (1.1, -.85), (-1.1, -.85)), 1):
            kit.cylinder(f'Weapon {sid} tube {k}', (x+5.35, y+dy, z+dz), .6, .2, kit.dark, 'X', 10, 0)
        m = kit.empty(f'Anchor weapon {sid} muzzle', (x+5.5, y, z), 0, size=1)
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
