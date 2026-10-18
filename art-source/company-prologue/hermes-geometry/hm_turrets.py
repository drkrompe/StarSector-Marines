"""Hermes weapon slots, exactly as hermes.ship declares them.

WS 001  TURRET  SMALL BALLISTIC  at (12, 18),  angle 45,  arc 150
WS 002  TURRET  SMALL BALLISTIC  at (12, -18), angle -45, arc 150
WS 003  TURRET  SMALL BALLISTIC  at (-18, 0),  angle 180, arc 210
WS 004  HIDDEN  SMALL SYSTEM     at (-40, 0),  angle 180, arc 30
        The maneuveringjets system slot: an empty marker only, no gun.

The two forward guns sit on the wing body's gun sponsons, the round rings
in the sprite either side of the spine; the third sits on the spine's aft
end inside the sprite's horseshoe. Each rests at its slot angle. Only the
parts above the traverse gap train: T(x,y) @ Rz(angle) @ T(-x,-y).
The mod flies the Hermes with one hardpoint (ShuttleType.HERMES); the
model keeps all three of the hull's mounts, because a mount is never
invented or dropped.
"""
import math
import bpy
from mathutils import Matrix, Vector
from hm_kit import bx

SLOTS = [
    {'id': 'WS 001', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [12, 18], 'angle': 45, 'arc': 150},
    {'id': 'WS 002', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [12, -18], 'angle': -45, 'arc': 150},
    {'id': 'WS 003', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [-18, 0], 'angle': 180, 'arc': 210},
    {'id': 'WS 004', 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM', 'location': [-40, 0], 'angle': 180, 'arc': 30},
]
BARBETTE_R = 3.0
LIFT = 0.0


def seat_z(kit, x, y, r):
    """Highest hull point under the barbette's ring."""
    zs = []
    for k in range(12):
        a = 2*math.pi*k/12
        hit = kit.surface_any(x + r*math.cos(a), y + r*math.sin(a))
        if hit:
            zs.append(hit[0].z)
    hit = kit.surface_any(x, y)
    if hit:
        zs.append(hit[0].z)
    return max(zs)


def build_turret(kit, n, slot):
    x, y = slot['location']
    sid = slot['id']
    z = seat_z(kit, x, y, BARBETTE_R) + LIFT
    slot['seat_z'] = round(z, 2)
    kit.cylinder(f'Turret {n} {sid} barbette', (x, y, z + .6), BARBETTE_R, 1.6, kit.steel, 'Z', 24, .12)
    kit.cylinder(f'Turret {n} {sid} traverse gap', (x, y, z + 1.5), BARBETTE_R*.9, .2, kit.dark, 'Z', 24, 0)
    zt = z + 1.6
    trained = [
        kit.cylinder(f'Turret {n} {sid} ring', (x, y, zt + .35), BARBETTE_R*.95, .7, kit.panel, 'Z', 24, .1),
        bx(kit, f'Turret {n} {sid} gun house', x - 2.0, x + 1.6, y - 1.7, y + 1.7, zt + .6, zt + 2.4, kit.cream, .2),
        bx(kit, f'Turret {n} {sid} mantlet', x + 1.4, x + 2.2, y - 1.1, y + 1.1, zt + .9, zt + 2.1, kit.steel, .1),
    ]
    for k, dy in enumerate((.5, -.5), 1):
        trained.append(kit.cylinder(f'Turret {n} {sid} barrel {k}', (x + 4.2, y + dy, zt + 1.5), .22, 4.4, kit.steel, 'X', 12, .02))
    # Rest at the slot angle about the slot's own axis.
    m = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(slot['angle']), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    bpy.context.view_layer.update()
    for ob in trained:
        ob.matrix_world = m @ ob.matrix_world


def build(kit):
    n = 0
    for slot in SLOTS:
        if slot['mount'] != 'TURRET':
            ob = bpy.data.objects.new(f"Slot {slot['id']} system marker", None)
            ob.location = (*slot['location'], 0)
            ob.empty_display_type = 'PLAIN_AXES'
            bpy.context.collection.objects.link(ob)
            continue
        n += 1
        build_turret(kit, n, slot)
