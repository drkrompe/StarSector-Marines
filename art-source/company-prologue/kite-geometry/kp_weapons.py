"""Kite (P) weapon slots, exactly as kite.ship declares them, fitted as the
vanilla kite_pirates_Raider variant arms them.

WS 001  HARDPOINT  SMALL MISSILE    at (6, 22),  angle 0, arc 6    Hammer torpedo
WS 002  HARDPOINT  SMALL MISSILE    at (6, -22), angle 0, arc 6    Swarmer SRM
WS 003  TURRET     SMALL BALLISTIC  at (-7, 0),  angle 0, arc 200  light dual machine gun

The hardpoints sit in the slot between each missile sponson and the
fuselage (the sprite's gap at Y 20..24), launcher faces forward through it.
The turret stands on the aft hump inside the sprite's dark ring (radius
about 5): a barbette and the shared turret kit's light machine gun with two
barrels (the kit has no Light Dual Machine Gun). Only the parts above the
traverse gap train, on the empty 'Weapon WS 003 pivot', which rests at the
slot angle.
"""
import math
import sys
from pathlib import Path
import bpy
from kp_kit import bx, blocks

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402

SLOTS = [
    {'id': 'WS 001', 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'location': [6, 22], 'angle': 0,
     'arc': 6, 'weapon': 'hammer'},
    {'id': 'WS 002', 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'location': [6, -22], 'angle': 0,
     'arc': 6, 'weapon': 'swarmer'},
    {'id': 'WS 003', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [-7, 0], 'angle': 0,
     'arc': 200, 'weapon': 'lightdualmg'},
]
RING = 4.2            # turret ring radius, units (sprite ring about 5 with its coaming)
LIFT = 1.2            # barbette drum above the hump deck
MPU = .86             # blockout guess for the kit's fittings; pass 4 publishes the hull's value
# Launcher box per hardpoint: (length aft of the face, half width, height), units.
LAUNCHER = {'hammer': (7.0, 2.0, 3.2), 'swarmer': (6.0, 2.0, 3.2)}
# Tube grid (rows, cols) and tube radius.
TUBES = {'hammer': (1, 1, 1.05), 'swarmer': (2, 3, .45)}


def _launcher(kit, slot):
    sid, (x, y), w = slot['id'], slot['location'], slot['weapon']
    length, hw, h = LAUNCHER[w]
    hit = kit.surface_any(x - length/2, y)
    z0 = (hit[0].z if hit else 1.8) - .4
    face = x + 2.0
    bx(kit, f'Weapon {sid} launcher housing', face - length, face, y - hw, y + hw, z0, z0 + h, kit.steel, .15)
    rows, cols, tr = TUBES[w]
    anchors = []
    for r in range(rows):
        for c in range(cols):
            ty = y + (c - (cols - 1)/2)*2*tr*1.25
            tz = z0 + h/2 + (r - (rows - 1)/2)*2*tr*1.25
            kit.cylinder(f'Weapon {sid} tube mouth {r*cols+c+1}', (face + .02, ty, tz), tr, .1, kit.dark, 'X', 16, 0)
            a = bpy.data.objects.new(f'Weapon {sid} muzzle anchor {r*cols+c+1}', None)
            a.empty_display_size = tr
            a.location = (face + .05, ty, tz)
            a.rotation_euler = (0, math.pi/2, 0)     # local +Z along +X, the line of fire
            bpy.context.collection.objects.link(a)
            anchors.append(a.name)
    slot['muzzles'] = anchors


def _turret(kit, slot):
    sid, (x, y) = slot['id'], slot['location']
    hit = kit.surface_any(x, y)
    seat = hit[0].z if hit else 7.5
    mats = {'armour': kit.cream, 'plate': kit.panel, 'steel': kit.steel, 'dark': kit.dark}
    tk.barbette(f'Weapon {sid} barbette', RING, LIFT, mats, MPU, location=(x, y, seat))
    pivot = bpy.data.objects.new(f'Weapon {sid} pivot', None)
    pivot.empty_display_type = 'ARROWS'
    pivot.location = (x, y, seat + LIFT)
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'], pivot['arc_deg'], pivot['rest_angle_deg'] = sid, slot['arc'], slot['angle']
    bpy.context.collection.objects.link(pivot)
    t = tk.build('lightmg', f'Weapon {sid}', pivot, RING*.8, mats, MPU, barrels=2)
    slot['muzzles'] = [a.name for a in t['muzzles']]
    slot['pivot'] = pivot.name
    slot['seat_z'] = round(seat, 2)


def build(kit):
    for slot in SLOTS:
        if slot['mount'] == 'HARDPOINT':
            _launcher(kit, slot)
        else:
            _turret(kit, slot)
