"""Colossus weapon mounts: four small ballistic turrets and four hidden flare launchers.

Slots exactly as colossus.ship lists them. The turrets stand on the side cargo
modules, each at the round turret ring the sprite draws there, and rest at
their slot angles (broadside, port and starboard). WS 005-008 are HIDDEN
SYSTEM slots at the four corners: the ship system is flarelauncher, so each is
a low flare-launcher box laid along its slot angle.

Names: 'Turret <i> WS 00n ...' and 'Launcher <i> WS 00n ...', with a
'<prefix> pivot' empty on the traverse axis and a '<prefix> muzzle' empty at
the barrel tip (+X along the rest angle). Only parts above the traverse gap
turn: T(x,y) @ Rz(angle) @ T(-x,-y).
"""
import math
import bpy
from mathutils import Matrix, Vector

SLOTS = [
    {'id': 'WS 001', 'location': (69, 75), 'angle': 80, 'arc': 140, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 002', 'location': (3, 75), 'angle': 90, 'arc': 130, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (70, -74), 'angle': -80, 'arc': 140, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 004', 'location': (4, -73), 'angle': -90, 'arc': 130, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 005', 'location': (87, 83), 'angle': 45, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 006', 'location': (86, -83), 'angle': -45, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 007', 'location': (-83, 83), 'angle': 100, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 008', 'location': (-84, -81), 'angle': 260, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
]
t = l = 0
for s in SLOTS:
    if s['mount'] == 'TURRET':
        t += 1
        s['prefix'] = f"Turret {t} {s['id']}"
    else:
        l += 1
        s['prefix'] = f"Launcher {l} {s['id']} flare launcher"
ANCHOR_Z = {}
MUZZLES = {}


def _turn(objs, x, y, angle):
    bpy.context.view_layer.update()  # matrix_world must carry each part's own rotation first
    m = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(angle), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    for o in objs:
        o.matrix_world = m @ o.matrix_world


def turret(kit, s):
    x, y = s['location']
    p = s['prefix']
    z = kit.surface(x, y)[0].z
    ring = kit.cylinder(f'{p} barbette', (x, y, z+1.0), 5.2, 2.0, kit.steel, vertices=16)
    base = z+2.0
    ANCHOR_Z[s['id']] = base
    kit.empty(f'{p} pivot', (x, y, base), s['angle'])
    gap = .25
    house = kit.box(f'{p} shield', (x-.6, y, base+gap+1.6), (8.2, 7.4, 3.2), kit.pale, bevel=.5)
    mantlet = kit.box(f'{p} mantlet', (x+3.8, y, base+gap+1.6), (1.2, 4.0, 2.2), kit.steel, bevel=.2)
    parts = [house, mantlet]
    for k, dy in enumerate((-1.1, 1.1), 1):
        parts.append(kit.cylinder(f'{p} barrel {k}', (x+8.2, y+dy, base+gap+1.6), .45, 8.0, kit.steel, axis='X', vertices=10))
    _turn(parts, x, y, s['angle'])
    a = math.radians(s['angle'])
    tip = (x+12.2*math.cos(a), y+12.2*math.sin(a), base+gap+1.6)
    kit.empty(f'{p} muzzle', tip, s['angle'])
    MUZZLES[s['id']] = tuple(round(v, 3) for v in tip)


def launcher(kit, s):
    x, y = s['location']
    p = s['prefix']
    hit = kit.surface(x, y)
    z = hit[0].z if hit else 0.0
    ANCHOR_Z[s['id']] = z
    box = kit.box(f'{p} housing', (x, y, z+.8), (5.0, 3.6, 2.0), kit.steel, bevel=.25)
    tubes = [kit.cylinder(f'{p} tube {k}', (x+2.4, y+dy, z+1.0), .55, 1.2, kit.dark, axis='X', vertices=10)
             for k, dy in enumerate((-.9, .9), 1)]
    _turn([box]+tubes, x, y, s['angle'])
    a = math.radians(s['angle'])
    tip = (x+3.0*math.cos(a), y+3.0*math.sin(a), z+1.0)
    kit.empty(f'{p} pivot', (x, y, z), s['angle'])
    kit.empty(f'{p} muzzle', tip, s['angle'])
    MUZZLES[s['id']] = tuple(round(v, 3) for v in tip)


def build(kit):
    ANCHOR_Z.clear()
    MUZZLES.clear()
    for s in SLOTS:
        (turret if s['mount'] == 'TURRET' else launcher)(kit, s)
