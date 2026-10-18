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

WS 008 is a fixed armoured universal mount sunk into the gantry's forward
bed (bed top z 5.5): a module cradle bolted to the bed, an armoured housing
with a chamfered glacis, a recoil sleeve and a shrouded barrel, and the
slot's standard quick-change module plate with its coupling block.

Names: 'Turret 1 WS 007 ...', 'Hardpoint 1 WS 008 ...', with a
'<prefix> pivot' empty at the traverse centre and a '<prefix> muzzle' empty
whose +X is the slot angle.
"""
import math
import bpy
from mathutils import Matrix, Vector
from sh_kit import loft_x, octagon

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
HARDPOINT_BED = 'Gantry forward hardpoint bed'


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


def _seat(kit, x, y, names=()):
    """Highest point under (x, y) on the kit's hull plus the named objects."""
    best = kit.surface(x, y)
    z = best[0].z if best else 0.0
    dg = bpy.context.evaluated_depsgraph_get()
    for n in names:
        ob = bpy.data.objects.get(n)
        if ob is None:
            continue
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        hit, p, _, _ = ev.ray_cast(inv @ Vector((x, y, 60)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
        if hit:
            z = max(z, (ev.matrix_world @ p).z)
    return z


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
    x, y = s['location']
    p = s['prefix']
    z = _seat(kit, x, y, (HARDPOINT_BED,))
    c = z+1.0  # bore axis
    # Universal-slot cradle bolted to the bed top, and its quick-change plate.
    kit.box(f'{p} module cradle', (x+.3, y, z+.15), (5.6, 3.8, .3), kit.grey, .05)
    for i, (dx, dy) in enumerate(((-2.2, 1.6), (2.8, 1.6), (-2.2, -1.6), (2.8, -1.6)), 1):
        kit.cylinder(f'{p} cradle bolt {i}', (x+dx, y+dy, z+.36), .14, .14, kit.steel, 'Z', 6, 0)
    kit.box(f'{p} module plate', (x-2.15, y, c), (.3, 2.8, 1.7), kit.cream, .05)
    kit.box(f'{p} module coupling', (x-2.5, y, c-.2), (.4, 1.2, .7), kit.dark, .04)
    kit.box(f'{p} module plate stripe', (x-2.31, y, c+.55), (.02, 2.4, .18), kit.orange, 0)
    # Armoured housing: chamfered octagon section narrowing into a glacis.
    kit.box(f'{p} housing', (x+.5, y, c), (4.2, 3.2, 1.7), kit.steel, .2)  # core, kept for its name
    loft_x(kit, f'{p} housing armour', [
        (x-2.0, octagon(y-1.75, y+1.75, z+.25, c+1.0, .5, .25)),
        (x+1.8, octagon(y-1.75, y+1.75, z+.25, c+1.0, .5, .25)),
        (x+3.0, octagon(y-1.2, y+1.2, z+.3, c+.6, .35, .2)),
    ], kit.tan, .05)
    for i in range(1, 4):  # cooling fins along both cheeks: the universal module's heat sink
        for side, dy in (('port', 1.85), ('starboard', -1.85)):
            kit.box(f'{p} cooling fin {side} {i}', (x-1.6+.45*i, y+dy, c+.1), (.12, .25, 1.1), kit.dark, 0)
    kit.box(f'{p} housing seam', (x+.2, y, c+1.01), (.08, 2.6, .04), kit.dark, 0)
    kit.box(f'{p} service cover', (x-.9, y, c+1.02), (1.2, 1.4, .06), kit.grey, .02)
    for i, (dx, dy) in enumerate(((-1.4, .6), (-.4, .6), (-1.4, -.6), (-.4, -.6)), 1):
        kit.cylinder(f'{p} cover bolt {i}', (x+dx, y+dy, c+1.06), .07, .06, kit.dark, 'Z', 6, 0)
    kit.box(f'{p} cheek plate port', (x+1.3, y+1.8, c-.1), (1.4, .1, 1.0), kit.grey, .02)
    kit.box(f'{p} cheek plate starboard', (x+1.3, y-1.8, c-.1), (1.4, .1, 1.0), kit.grey, .02)
    # Recoil sleeve, shroud and barrel.
    kit.box(f'{p} recoil sleeve', (x+3.4, y, c), (1.0, 1.3, 1.3), kit.dark, .1)
    kit.cylinder(f'{p} recoil collar', (x+4.0, y, c), .55, .3, kit.steel, 'X', 12, .03)
    kit.cylinder(f'{p} barrel shroud', (x+4.9, y, c), .45, 1.6, kit.steel, 'X', 8, .04)
    for i in range(1, 4):  # cooling slots in the shroud
        kit.box(f'{p} shroud slot {i}', (x+4.35+.4*i, y, c+.4), (.18, .5, .1), kit.dark, 0)
    kit.cylinder(f'{p} barrel', (x+5.3, y, c), .24, 2.4, kit.dark, 'X', 12, .03)
    kit.cylinder(f'{p} muzzle ring', (x+6.4, y, c), .3, .2, kit.steel, 'X', 10, .02)
    kit.empty(f'{p} muzzle', (x+6.5, y, c), s['angle'])
    kit.empty(f'{p} pivot', (x, y, c), s['angle'])
    ANCHOR_Z[s['id']] = c
    MUZZLES[s['id']] = (x+6.5, y, round(c, 3))


def build(kit):
    _turret(kit, SLOTS[0])
    _hardpoint(kit, SLOTS[1])
