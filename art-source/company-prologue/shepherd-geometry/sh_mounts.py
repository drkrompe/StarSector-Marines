"""Shepherd weapon mounts at the shepherd.ship slots, resting at the slot angles.

  WS 007  TURRET    SMALL BALLISTIC  (-3, -23)  angle -75, arc 200
  WS 008  HARDPOINT SMALL UNIVERSAL  (27, -19)  angle 0,   arc 20
  WS 003  HIDDEN    SMALL LAUNCH_BAY (-3, 19) (12, 19) (26, 19) - the drone
          bay; its openings belong to sh_dronebay.

The sprite's large grey ring at (-3, -23) is the turret's barbette ring; the
turret proper trains above the traverse gap. Only parts above the gap
rotate: T(x, y) @ Rz(angle) @ T(-x, -y).

Names: 'Turret 1 WS 007 ...', 'Hardpoint 1 WS 008 ...', with a
'<prefix> pivot' empty at the traverse centre and a '<prefix> muzzle' empty
whose +X is the slot angle.
"""
import math
import bpy
from mathutils import Matrix, Vector

SLOTS = [
    {'id': 'WS 007', 'location': (-3, -23), 'angle': -75, 'arc': 200, 'mount': 'TURRET', 'size': 'SMALL',
     'type': 'BALLISTIC', 'prefix': 'Turret 1 WS 007'},
    {'id': 'WS 008', 'location': (27, -19), 'angle': 0, 'arc': 20, 'mount': 'HARDPOINT', 'size': 'SMALL',
     'type': 'UNIVERSAL', 'prefix': 'Hardpoint 1 WS 008'},
]
ANCHOR_Z = {}   # slot id -> pivot height, filled by build()
MUZZLES = {}    # slot id -> muzzle location


def train(objs, x, y, angle):
    turn = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(angle), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    bpy.context.view_layer.update()
    for ob in objs:
        ob.matrix_world = turn @ ob.matrix_world


def build(kit):
    # WS 007: barbette ring on the turret deck, a gun house above the gap.
    s = SLOTS[0]
    x, y = s['location']
    p = s['prefix']
    z = kit.surface(x, y)[0].z
    kit.cylinder(f'{p} barbette ring', (x, y, z+.6), 4.6, 1.2, kit.grey, 'Z', 24, .15)
    kit.cylinder(f'{p} barbette', (x, y, z+1.6), 3.4, 1.2, kit.steel, 'Z', 20, .12)
    gap = z+2.35
    trained = [
        kit.cylinder(f'{p} turntable', (x, y, gap+.25), 3.0, .5, kit.steel, 'Z', 20, .08),
        kit.box(f'{p} gun house', (x+.3, y, gap+1.4), (4.6, 3.6, 1.9), kit.tan, .3),
        kit.box(f'{p} mantlet', (x+2.8, y, gap+1.3), (.8, 2.4, 1.4), kit.steel, .12),
    ]
    for j, dy in enumerate((.6, -.6), 1):
        trained.append(kit.cylinder(f'{p} barrel {j}', (x+5.6, y+dy, gap+1.3), .28, 5.0, kit.dark, 'X', 12, .03))
    muzzle = kit.empty(f'{p} muzzle', (x+8.1, y, gap+1.3), 0)
    pivot = kit.empty(f'{p} pivot', (x, y, gap), 0)
    train(trained+[muzzle, pivot], x, y, s['angle'])
    ANCHOR_Z[s['id']] = gap
    bpy.context.view_layer.update()
    MUZZLES[s['id']] = tuple(round(v, 3) for v in muzzle.matrix_world.translation)

    # WS 008: a fixed forward hardpoint in the gantry's forward bed.
    s = SLOTS[1]
    x, y = s['location']
    p = s['prefix']
    z = kit.surface(x, y)[0].z
    kit.box(f'{p} housing', (x+.5, y, z+1.0), (5, 3.2, 2.0), kit.steel, .2)
    kit.box(f'{p} recoil sleeve', (x+3.7, y, z+1.0), (1.4, 1.4, 1.4), kit.dark, .1)
    kit.cylinder(f'{p} barrel', (x+4.9, y, z+1.0), .32, 2.4, kit.dark, 'X', 12, .03)
    kit.empty(f'{p} muzzle', (x+6.1, y, z+1.0), s['angle'])
    kit.empty(f'{p} pivot', (x, y, z+1.0), s['angle'])
    ANCHOR_Z[s['id']] = z+1.0
    MUZZLES[s['id']] = (x+6.1, y, round(z+1.0, 3))
