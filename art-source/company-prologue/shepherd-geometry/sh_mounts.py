"""Shepherd weapon mounts at the shepherd.ship slots, resting at the slot angles.

  WS 007  TURRET    SMALL BALLISTIC  (-3, -23)  angle -75, arc 200
  WS 008  HARDPOINT SMALL UNIVERSAL  (27, -19)  angle 0,   arc 20
  WS 003  HIDDEN    SMALL LAUNCH_BAY (-3, 19) (12, 19) (26, 19) - the drone
          bay; its openings belong to sh_dronebay.

The sprite's large grey ring at (-3, -23) is the turret's barbette ring; the
turret proper trains above the traverse gap. Only parts above the gap
rotate: T(x, y) @ Rz(angle) @ T(-x, -y). Every trained object carries a
custom property 'trained' = True so a clash sweep can re-train them to the
arc limits.

WS 007 is a small twin naval mount: a bolted deck ring and a stepped
barbette, a dark traverse gap with its roller race, then the trained part -
turntable, a chamfered gun house with a rear ammunition bustle, trunnion
bosses on the cheeks, a mantlet with blast gaiters, twin barrels with
jackets and slotted muzzle brakes, a roof sight hood and commander's hatch,
and a belt feed chute running from a side ready-use box into the house.

WS 008 is the shared turret kit's hardpoint (../turret-kit, tk.build_hardpoint) for the
stock fit the campaign flies, shepherd_Frontier: the Swarmer SRM pack, four tubes from
a breech housing, a round on each 'missile k' empty. It is built on the slot's pivot at
HARDPOINT_K hull units per weapon-sprite pixel and sunk in the gantry drum's well, which
sh_gantry cuts to the kit's own bay (hardpoint_bay) with the housing standing on the
bed at its floor.

Names: 'Turret 1 WS 007 ...', 'Hardpoint 1 WS 008 ...', with a
'<prefix> pivot' empty at the traverse centre (the hardpoint: on its axis at the
slot) and a '<prefix> muzzle' empty whose +X is the slot angle (the hardpoint: at
its first tube's mouth; the kit adds '<prefix> muzzle anchor 1-4', one per tube).
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Matrix
from sh_kit import loft_x, octagon

_PROLOGUE = Path(__file__).resolve().parent.parent
sys.path[:0] = [p for p in (str(_PROLOGUE), str(_PROLOGUE/'turret-kit')) if p not in sys.path]
import ship_scale  # noqa: E402
import turret_kit as tk  # noqa: E402

SLOTS = [
    {'id': 'WS 007', 'location': (-3, -23), 'angle': -75, 'arc': 200, 'mount': 'TURRET', 'size': 'SMALL',
     'type': 'BALLISTIC', 'prefix': 'Turret 1 WS 007'},
    {'id': 'WS 008', 'location': (27, -19), 'angle': 0, 'arc': 20, 'mount': 'HARDPOINT', 'size': 'SMALL',
     'type': 'UNIVERSAL', 'prefix': 'Hardpoint 1 WS 008'},
]
ANCHOR_Z = {}   # slot id -> pivot height, filled by build()
MUZZLES = {}    # slot id -> muzzle location

# Extra barbette height above the seat. On the thickened hull the aft
# crossbeam (top +7) crossed the muzzle brakes from -150 to -175 deg; one
# unit of lift clears every neighbouring mesh at the rest angle, both arc
# limits and every 10 deg between (BVH sweep). Raise this, never move the
# slot, if a neighbour grows into the arc.
BARBETTE_LIFT = 1.0
# WS 008 is armed as shepherd_Frontier, the stock variant the campaign creates for Morrow
# (TenderLoadout.STOCK_VARIANT; the kit's README lists the Swarmer as the Shepherd
# Frontier's). The pack is built at HARDPOINT_K hull units per sprite pixel, so its
# 16 px sprite width (8.4 px half-bay) sits in the 12-unit drum with walls either side.
HARDPOINT_VARIANT = 'shepherd_Frontier'
HARDPOINT_WEAPON = 'swarmer'
HARDPOINT_K = .45
METRES_PER_UNIT = ship_scale.PUBLISHED['Shepherd']
# The well's floor: the top of 'Gantry forward hardpoint bed' (sh_gantry.BED_TOP), where
# the kit's housing (its underside 2.6 px below the axis) stands, sunk HOUSING_SINK into it.
HARDPOINT_FLOOR_Z = 2.62
HOUSING_UNDERSIDE = -2.6
HOUSING_SINK = .05
# The tubes' line of fire on over the drum's falling nose, cut as a lined trough: from
# just inside the collars' mouths (12 px ahead of the slot) to TROUGH_END hull units
# ahead of it, as wide as the tube bank with its collars and floored under them.
TROUGH_PX = (11.8, 6.9, -1.9)     # x0, half-width, floor below the axis (sprite px, times k)
TROUGH_END = 8.5
HARDPOINT_RECORD = {}   # slot id -> what the kit built, for geometry.json


def axis_z():
    """The hardpoint's axis height: its housing standing on the well floor."""
    return HARDPOINT_FLOOR_Z-HOUSING_UNDERSIDE*HARDPOINT_K-HOUSING_SINK


def hardpoint_bay():
    """The well and trough sh_gantry cuts for WS 008, in hull units: the kit's own bay
    tuple (x0, x1 along the fire line, half-width, floor below the axis) scaled by k."""
    x, y = SLOTS[1]['location']
    x0, x1, hw, floor = (v*HARDPOINT_K for v in tk.HARDPOINT_WEAPONS[HARDPOINT_WEAPON]['bay'])
    c = axis_z()
    tx0, thw, tfl = (v*HARDPOINT_K for v in TROUGH_PX)
    tx1 = TROUGH_END
    return {'x': (x+x0, x+x1), 'y': y, 'half_width': hw, 'floor': c+floor, 'axis': c,
            'trough_x': (x+tx0, x+tx1), 'trough_half_width': thw, 'trough_floor': c+tfl}


def train(objs, x, y, angle):
    turn = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(angle), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    bpy.context.view_layer.update()
    for ob in objs:
        ob.matrix_world = turn @ ob.matrix_world


def _ring(kit, name, x, y, z, r, n, size, mat, phase=0.0):
    """n small bolt heads on a circle of radius r at height z."""
    out = []
    for i in range(n):
        a = phase+2*math.pi*i/n
        out.append(kit.cylinder(f'{name} {i+1}', (x+r*math.cos(a), y+r*math.sin(a), z+size/2), size, size, mat, 'Z', 6, 0))
    return out


def _turret(kit, s):
    x, y = s['location']
    p = s['prefix']
    z = kit.surface(x, y)[0].z
    L = BARBETTE_LIFT

    # Fixed: deck ring, its bolt circle, a stepped barbette and the race.
    kit.cylinder(f'{p} barbette ring', (x, y, z+.6), 4.6, 1.2, kit.grey, 'Z', 24, .15)
    kit.cylinder(f'{p} deck ring seam', (x, y, z+1.22), 3.7, .06, kit.dark, 'Z', 24, 0)
    _ring(kit, f'{p} deck ring bolt', x, y, z+1.2, 4.1, 24, .13, kit.steel, math.pi/24)
    kit.cylinder(f'{p} barbette', (x, y, z+1.6+L/2), 3.4, 1.2+L, kit.steel, 'Z', 20, .12)
    kit.cylinder(f'{p} barbette weld band', (x, y, z+1.55), 3.46, .12, kit.dark, 'Z', 20, 0)
    for i in range(10):  # vertical strata: welded barbette segments
        a = 2*math.pi*i/10+math.pi/20
        kit.box(f'{p} barbette segment rib {i+1}', (x+3.42*math.cos(a), y+3.42*math.sin(a), z+1.6+L/2),
                (.18, .18, 1.0+L), kit.grey, 0).rotation_euler[2] = a
    top = z+2.2+L
    gap = top+.15
    kit.cylinder(f'{p} traverse gap', (x, y, top+.05), 3.15, .12, kit.dark, 'Z', 20, 0)
    kit.cylinder(f'{p} roller race', (x, y, top+.02), 3.5, .08, kit.grey, 'Z', 24, 0)

    g = gap  # trained parts are built in the local frame (+X along the barrels) then turned
    h = 1.3  # gun axis above the gap
    trained = [kit.cylinder(f'{p} turntable', (x, y, g+.25), 3.0, .5, kit.steel, 'Z', 20, .08)]
    # Gun house: chamfered octagonal sections, cheeks raked in toward a sloped face.
    house = loft_x(kit, f'{p} gun house', [
        (x-2.3, octagon(y-1.85, y+1.85, g+.5, g+2.3, .55, .2)),
        (x+1.3, octagon(y-1.85, y+1.85, g+.5, g+2.3, .55, .2)),
        (x+2.45, octagon(y-1.35, y+1.35, g+.5, g+1.95, .45, .2)),
    ], kit.tan, .06)
    trained.append(house)
    trained.append(kit.box(f'{p} gun house roof seam', (x-.4, y, g+2.31), (.08, 2.6, .04), kit.dark, 0))
    trained.append(kit.box(f'{p} gun house side seam port', (x-.4, y+1.86, g+1.4), (.08, .04, 1.4), kit.dark, 0))
    trained.append(kit.box(f'{p} gun house side seam starboard', (x-.4, y-1.86, g+1.4), (.08, .04, 1.4), kit.dark, 0))
    # Ammunition bustle overhanging the rear, with its loading door.
    trained.append(loft_x(kit, f'{p} ammunition bustle', [
        (x-3.4, octagon(y-1.4, y+1.4, g+.9, g+2.1, .35)),
        (x-2.2, octagon(y-1.5, y+1.5, g+.8, g+2.15, .35)),
    ], kit.tan, .05))
    # Its chamfers are plate (45 deg): the bevel takes only the square edges, so the
    # chamfered box does not read as a sixteen-sided round.
    trained[-1].modifiers['Plate edge chamfer'].angle_limit = math.radians(50)
    trained.append(kit.box(f'{p} bustle loading door', (x-3.42, y, g+1.5), (.06, 1.6, .8), kit.steel, 0))
    trained += [kit.box(f'{p} bustle door hinge {i}', (x-3.45, y+dy, g+1.5), (.1, .12, .9), kit.dark, 0)
                for i, dy in ((1, .85), (2, -.85))]
    # Trunnion bosses on both cheeks: the elevation axis.
    trained += [kit.cylinder(f'{p} trunnion {side}', (x+1.2, y+dy, g+h), .45, .3, kit.steel, 'Y', 10, .04)
                for side, dy in (('port', 1.95), ('starboard', -1.95))]
    trained += [kit.cylinder(f'{p} trunnion cap {side}', (x+1.2, y+dy, g+h), .22, .1, kit.dark, 'Y', 8, 0)
                for side, dy in (('port', 2.12), ('starboard', -2.12))]
    # Mantlet over the gun ports, with canvas blast gaiters.
    trained.append(kit.box(f'{p} mantlet', (x+2.75, y, g+h), (.7, 2.3, 1.2), kit.steel, .12))
    for j, dy in enumerate((.6, -.6), 1):
        trained.append(kit.cylinder(f'{p} blast gaiter {j}', (x+3.3, y+dy, g+h), .38, .5, kit.dark, 'X', 10, .04))
        trained.append(kit.cylinder(f'{p} barrel jacket {j}', (x+4.3, y+dy, g+h), .34, 1.6, kit.steel, 'X', 12, .03))
        trained.append(kit.cylinder(f'{p} jacket band {j}', (x+4.9, y+dy, g+h), .37, .14, kit.dark, 'X', 12, 0))
        trained.append(kit.cylinder(f'{p} barrel {j}', (x+5.6, y+dy, g+h), .24, 5.0, kit.dark, 'X', 12, .03))
        trained.append(kit.cylinder(f'{p} muzzle brake {j}', (x+8.15, y+dy, g+h), .32, .7, kit.steel, 'X', 8, .03))
        trained.append(kit.box(f'{p} muzzle brake port {j}', (x+8.2, y+dy, g+h), (.3, .66, .16), kit.dark, 0))
    # Roof: sight hood with its optic window, commander's hatch, vents.
    trained.append(kit.box(f'{p} sight hood', (x+.6, y+1.05, g+2.55), (1.3, .7, .55), kit.steel, .08))
    trained.append(kit.box(f'{p} sight optic', (x+1.26, y+1.05, g+2.58), (.06, .5, .28), kit.glass, 0))
    trained.append(kit.cylinder(f'{p} commander hatch', (x-1.2, y-.75, g+2.38), .55, .16, kit.steel, 'Z', 12, .03))
    trained.append(kit.box(f'{p} hatch hinge', (x-1.75, y-.75, g+2.43), (.12, .5, .12), kit.dark, 0))
    trained += [kit.box(f'{p} roof vent {i}', (x+.1, y-.85-.3*i, g+2.34), (.9, .14, .06), kit.dark, 0)
                for i in range(1, 3)]
    trained += _ring(kit, f'{p} roof bolt', x+.2, y, g+2.27, 1.3, 8, .1, kit.steel)
    # Ready-use ammunition box on the port cheek and its belt feed chute.
    trained.append(kit.box(f'{p} ready ammunition box', (x-1.4, y+2.25, g+1.1), (1.6, .7, 1.1), kit.steel, .06))
    trained.append(kit.box(f'{p} ready box lid', (x-1.4, y+2.25, g+1.68), (1.7, .78, .08), kit.dark, 0))
    trained.append(kit.mesh(f'{p} ammunition feed chute', *_chute(x-.65, y+2.25, g+1.35, x+.2, y+1.85, g+1.55), kit.dark))
    muzzle = kit.empty(f'{p} muzzle', (x+8.5, y, g+h), 0)
    pivot = kit.empty(f'{p} pivot', (x, y, g), 0)
    for ob in trained:
        ob['trained'] = True
    train(trained+[muzzle, pivot], x, y, s['angle'])
    ANCHOR_Z[s['id']] = g
    bpy.context.view_layer.update()
    MUZZLES[s['id']] = tuple(round(v, 3) for v in muzzle.matrix_world.translation)


def _chute(x0, y0, z0, x1, y1, z1, w=.32, n=5):
    """A square-section feed chute bent from (x0,y0,z0) to (x1,y1,z1)."""
    verts, faces = [], []
    for i in range(n+1):
        t = i/n
        cx = x0+(x1-x0)*t
        cy = y0+(y1-y0)*t
        cz = z0+(z1-z0)*t+.35*math.sin(math.pi*t)
        for dy, dz in ((-w/2, -w/2), (w/2, -w/2), (w/2, w/2), (-w/2, w/2)):
            verts.append((cx, cy+dy*.7, cz+dz))
    for i in range(n):
        a, b = 4*i, 4*(i+1)
        faces += [(a+k, a+(k+1) % 4, b+(k+1) % 4, b+k) for k in range(4)]
    faces += [(0, 1, 2, 3), (4*n+3, 4*n+2, 4*n+1, 4*n)]
    return verts, faces


def _hardpoint(kit, s):
    """WS 008: the shared turret kit's hardpoint for the stock fit (HARDPOINT_WEAPON), built
    on the slot's pivot empty at HARDPOINT_K hull units per weapon-sprite pixel and seated
    on the bed of the well sh_gantry cuts to the kit's own bay (hardpoint_bay)."""
    x, y = s['location']
    p = s['prefix']
    c = axis_z()
    pivot = kit.empty(f'{p} pivot', (x, y, c), s['angle'])
    hp = tk.build_hardpoint(HARDPOINT_WEAPON, p, pivot, _hardpoint_mats(kit), METRES_PER_UNIT, k=HARDPOINT_K)
    bpy.context.view_layer.update()
    # The hull's own muzzle tag rides the first tube's mouth (the kit's muzzle anchor 1),
    # +X along the slot angle; the kit's four anchors stay as the per-tube fire points.
    first = hp['muzzles'][0].matrix_world.translation.copy()
    kit.empty(f'{p} muzzle', tuple(first), s['angle'])
    ANCHOR_Z[s['id']] = c
    MUZZLES[s['id']] = tuple(round(v, 3) for v in first)
    HARDPOINT_RECORD[s['id']] = {
        'weapon': HARDPOINT_WEAPON, 'variant': HARDPOINT_VARIANT,
        'built_by': 'turret-kit/turret_kit.py build_hardpoint', 'k': HARDPOINT_K,
        'bay': {k: [round(v, 3) for v in b] if isinstance(b, tuple) else round(b, 3)
                for k, b in hardpoint_bay().items()},
        'parts': sorted(o.name for o in hp['parts']), 'moving': sorted(e.name for e in hp['moving']),
        'muzzles': [m.name for m in hp['muzzles']],
        'muzzle_locations': [[round(v, 3) for v in m.matrix_world.translation] for m in hp['muzzles']],
    }


def _hardpoint_mats(kit):
    """The hull's materials for the kit's hardpoint keys; the weapon's own paints are laid
    over them by the kit (its sprite colours, weathered)."""
    return {'armour': kit.tan, 'plate': kit.steel, 'steel': kit.steel, 'dark': kit.dark}


def build(kit):
    _turret(kit, SLOTS[0])
    _hardpoint(kit, SLOTS[1])
