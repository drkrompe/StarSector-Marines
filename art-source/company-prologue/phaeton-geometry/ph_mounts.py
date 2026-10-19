"""Phaeton weapon mounts at the phaeton.ship slots, resting at the slot angles.

  WS 001  TURRET  SMALL BALLISTIC  (-75, 0)    angle 180, arc 150 - drive block crown
  WS 002  TURRET  SMALL BALLISTIC  (38, 38)    angle 90,  arc 180 - port sponson
  WS 003  TURRET  SMALL BALLISTIC  (36, -34)   angle -90, arc 180 - starboard sponson
  WS 004  TURRET  SMALL BALLISTIC  (126, 13)   angle 0,   arc 210 - port bow sponson
  WS 005  HIDDEN  SMALL SYSTEM     (-51, 59)   angle 110, arc 5   - port flare launcher
  WS 006  HIDDEN  SMALL SYSTEM     (-51, -58)  angle 250, arc 5   - starboard flare launcher

The turrets are small twin naval mounts, the Shepherd's design (copied, not
imported) scaled up to the sprite's rings (about 14 units across here). The
barbette is fixed; only parts above the traverse gap train:
T(x, y) @ Rz(angle) @ T(-x, -y). Trained objects carry 'trained' = True.
The flare launchers (the hull's flarelauncher system) are fixed boxed
tube packs on the guard arcs' tips, their muzzles along the slot angle.

Names: 'Turret <n> WS 00x ...', 'Launcher <n> WS 00x ...', each with
'<prefix> pivot' and '<prefix> muzzle' empties.
"""
import math
import bpy
from mathutils import Matrix, Vector
from ph_kit import loft_x, octagon

SLOTS = [
    {'id': 'WS 001', 'location': (-75, 0), 'angle': 180, 'arc': 150, 'mount': 'TURRET', 'size': 'SMALL',
     'type': 'BALLISTIC', 'prefix': 'Turret 1 WS 001'},
    {'id': 'WS 002', 'location': (38, 38), 'angle': 90, 'arc': 180, 'mount': 'TURRET', 'size': 'SMALL',
     'type': 'BALLISTIC', 'prefix': 'Turret 2 WS 002'},
    {'id': 'WS 003', 'location': (36, -34), 'angle': -90, 'arc': 180, 'mount': 'TURRET', 'size': 'SMALL',
     'type': 'BALLISTIC', 'prefix': 'Turret 3 WS 003'},
    {'id': 'WS 004', 'location': (126, 13), 'angle': 0, 'arc': 210, 'mount': 'TURRET', 'size': 'SMALL',
     'type': 'BALLISTIC', 'prefix': 'Turret 4 WS 004'},
    {'id': 'WS 005', 'location': (-51, 59), 'angle': 110, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL',
     'type': 'SYSTEM', 'prefix': 'Launcher 1 WS 005'},
    {'id': 'WS 006', 'location': (-51, -58), 'angle': 250, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL',
     'type': 'SYSTEM', 'prefix': 'Launcher 2 WS 006'},
]
ANCHOR_Z = {}   # slot id -> pivot height
MUZZLES = {}    # slot id -> muzzle location
TURRET_SCALE = 1.5
BARBETTE_LIFT = 1.0


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
    before = set(bpy.data.objects)
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
    # Built at the Shepherd's mount size, then scaled about the seat to this
    # hull's sprite ring (TURRET_SCALE); bevel widths stay as authored.
    grow = Matrix.Translation((x, y, z)) @ Matrix.Scale(TURRET_SCALE, 4) @ Matrix.Translation((-x, -y, -z))
    bpy.context.view_layer.update()
    for ob in set(bpy.data.objects)-before:
        ob.matrix_world = grow @ ob.matrix_world
    train(trained+[muzzle, pivot], x, y, s['angle'])
    ANCHOR_Z[s['id']] = z+(g-z)*TURRET_SCALE
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


def _launcher(kit, s):
    """A boxed 2x3 flare tube pack on a fixed saddle astride the guard arc's
    outer rim. Built along +X at the slot, then turned to the slot angle."""
    x, y = s['location']
    p = s['prefix']
    a = math.radians(s['angle'])
    hit = kit.surface(x-1.5*math.cos(a), y-1.5*math.sin(a))  # the arc's crown just inboard of the slot
    z = hit[0].z if hit else 5.0
    before = set(bpy.data.objects)
    # Fixed saddle: base plate on the rim, a cradle, two cheeks and a bracket down the arc's outer chord.
    kit.box(f'{p} saddle base plate', (x-1.4, y, z+.2), (4.8, 3.8, .4), kit.grey, .06)
    kit.box(f'{p} mounting saddle', (x-1.2, y, z+.9), (3.4, 3.0, 1.0), kit.grey, .08)
    for side, dy in (('port', 1.75), ('starboard', -1.75)):
        kit.box(f'{p} saddle cheek {side}', (x-1.2, y+dy, z+1.6), (2.8, .3, 2.4), kit.grey, .04)
        kit.cylinder(f'{p} saddle pin {side}', (x-1.2, y+dy*1.12, z+2.1), .4, .3, kit.steel, 'Y', 10, .02)
    kit.box(f'{p} saddle bracket', (x-.7, y, z-2.4), (.4, 3.0, 4.6), kit.grey, .04)
    for i, (dx, dy) in enumerate(((-3.4, -1.5), (-3.4, 1.5), (.6, -1.5), (.6, 1.5)), 1):
        kit.cylinder(f'{p} saddle bolt {i}', (x+dx, y+dy, z+.45), .16, .14, kit.steel, 'Z', 6, 0)
    # The pack: a box of six tubes, framed at the front, banded, with a rear junction box.
    zp = z+1.4+1.3
    kit.box(f'{p} tube pack', (x-1.2, y, zp), (4.4, 3.4, 2.6), kit.steel, .15)
    kit.box(f'{p} pack front frame', (x+1.05, y, zp), (.2, 3.6, 2.8), kit.grey, .04)
    for i, dx in enumerate((-1.0, .8), 1):
        kit.box(f'{p} pack band {i}', (x-1.2+dx, y, zp), (.3, 3.55, 2.75), kit.grey, .03)
    kit.box(f'{p} pack lid hinge', (x-1.3, y, zp+1.33), (3.8, .3, .1), kit.dark, 0)
    i = 0
    for dz in (.62, -.62):
        for dy in (-1.05, 0, 1.05):
            i += 1
            kit.cylinder(f'{p} tube rim {i}', (x+1.16, y+dy, zp+dz), .5, .14, kit.steel, 'X', 12, 0)
            kit.cylinder(f'{p} tube mouth {i}', (x+1.2, y+dy, zp+dz), .4, .14, kit.dark, 'X', 12, 0)
    kit.box(f'{p} rear junction box', (x-3.7, y, zp-.3), (.6, 1.6, 1.2), kit.dark, .04)
    kit.box(f'{p} firing cable conduit', (x-3.7, y, z+1.6), (.3, .3, 1.4), kit.dark, 0)
    train(list(set(bpy.data.objects)-before), x, y, s['angle'])
    mx, my = x+1.4*math.cos(a), y+1.4*math.sin(a)
    kit.empty(f'{p} muzzle', (mx, my, zp), s['angle'])
    kit.empty(f'{p} pivot', (x, y, zp), s['angle'])
    ANCHOR_Z[s['id']] = zp
    MUZZLES[s['id']] = (round(mx, 3), round(my, 3), round(zp, 3))


def build(kit):
    for s in SLOTS:
        (_turret if s['mount'] == 'TURRET' else _launcher)(kit, s)
