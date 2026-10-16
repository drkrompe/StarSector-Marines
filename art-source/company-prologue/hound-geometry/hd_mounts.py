"""The three weapon slots of hound.ship, at their exact locations and angles.

WS 001  HARDPOINT MEDIUM BALLISTIC  (35, -6)  angle 0    arc 5
WS 002  TURRET    SMALL  BALLISTIC  (-4,  4)  angle 145  arc 290
WS 003  HIDDEN    SMALL  SYSTEM     (-19, 0)  angle 180  arc 90  (flare launcher)

Hound (A) carries Armored Weapon Mounts as a built-in hullmod, so the turret
barbette has a heavier armour collar than the Second Wind's small mounts.
No weapon positions are invented, and the hull has no built-in weapons:
the guns are generic placeholders sized by slot, resting at the slot angle.
Every object is named 'Weapon <slot id> ...' and each slot has an anchor
empty whose +X points along the slot angle.
"""
import math
import bpy
from mathutils import Matrix


SLOTS = [
    {'id': 'WS 001', 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'BALLISTIC', 'location': (35, -6), 'angle': 0, 'arc': 5},
    {'id': 'WS 002', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': (-4, 4), 'angle': 145, 'arc': 290},
    {'id': 'WS 003', 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM', 'location': (-19, 0), 'angle': 180, 'arc': 90},
]
ANCHOR_Z = {}

HOUSE_BASE = [(3.0, -1.3), (3.0, 1.3), (1.7, 2.5), (-2.5, 2.5), (-2.5, -2.5), (1.7, -2.5)]
HOUSE_ROOF = [(1.3, -.85), (1.3, .85), (.5, 1.95), (-2.25, 1.95), (-2.25, -1.95), (.5, -1.95)]


def loft_z(kit, name, base, roof, z0, z1, mat):
    n = len(base)
    verts = [(x, y, z0) for x, y in base] + [(x, y, z1) for x, y in roof]
    faces = [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    faces += [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    return kit.mesh(name, verts, faces, mat, .1)


def hardpoint(kit, slot):
    x, y = slot['location']
    z = .4
    t = slot['id']
    # Housing set into the fork gap, mantlet flush with the cross bar.
    # The breech sits on the slot point (the sprite's rows 13-14 block); the
    # gun stays between the tines, its brake level with their points.
    kit.box(f'Weapon {t} hardpoint housing', (x-3.4, y, z), (4.6, 4.8, 4.6), kit.steel, .15)
    kit.box(f'Weapon {t} breech block', (x-.4, y, z), (2.0, 3.6, 3.2), kit.rust, .12)
    kit.cylinder(f'Weapon {t} recoil sleeve', (x+1.3, y, z), 1.0, 1.4, kit.steel, 'X', 12, .06)
    kit.cylinder(f'Weapon {t} barrel', (x+3.0, y, z), .62, 2.2, kit.steel, 'X', 12, .04)
    kit.box(f'Weapon {t} muzzle brake', (x+4.5, y, z), (.9, 1.5, 1.1), kit.steel, .08)
    ANCHOR_Z[t] = z
    kit.empty(f'Weapon {t} anchor', (x, y, z), slot['angle'])


def turret(kit, slot):
    x, y = slot['location']
    t = slot['id']
    # The sprite's armoured ring is about ten pixels across.
    s = 1.0
    R = 4.3
    zs = []
    for k in range(13):
        a = k*math.tau/12
        px, py = (x, y) if k == 12 else (x+R*math.cos(a), y+R*math.sin(a))
        hit = kit.surface(px, py)
        if hit:
            zs.append(hit[0].z)
    if kit.surface(x, y) is None:
        raise RuntimeError(f'{t} has no supporting hull surface')
    zmax, zmin = max(zs), min(zs)
    # Armoured barbette (Armored Weapon Mounts): foot sunk into the deck,
    # a thick octagonal collar, then the traverse gap.
    kit.cylinder(f'Weapon {t} barbette foot', (x, y, (zmin-.4+zmax+.3)/2), R+.5, zmax+.7-zmin, kit.steel, 'Z', 12, .1)
    kit.cylinder(f'Weapon {t} armoured collar', (x, y, zmax+.8), R+.1, 1.2, kit.orange, 'Z', 8, .12)
    kit.cylinder(f'Weapon {t} turret ring', (x, y, zmax+1.35), R-.3, .5, kit.steel, 'Z', 16, .06)
    kit.cylinder(f'Weapon {t} traverse gap', (x, y, zmax+1.7), R-1.0, .3, kit.dark, 'Z', 16, 0)
    zb = zmax+1.8
    fixed = set(bpy.data.objects)
    P = lambda pts: [(x+px*s, y+py*s) for px, py in pts]
    loft_z(kit, f'Weapon {t} gun house', P(HOUSE_BASE), P(HOUSE_ROOF), zb, zb+2.0*s, kit.steel)
    bb = [(-2.4, -2.1), (-2.4, 2.1), (-3.4, 1.9), (-4.0, 1.3), (-4.0, -1.3), (-3.4, -1.9)]
    loft_z(kit, f'Weapon {t} rear bustle', P(bb), P([(px+.1, py*.86) for px, py in bb]), zb+.15*s, zb+1.6*s, kit.steel)
    gz = zb+.95*s
    kit.cylinder(f'Weapon {t} mantlet drum', (x+2.3*s, y, gz), .78*s, 1.9*s, kit.rust, 'Y', 12, .1)
    kit.cylinder(f'Weapon {t} barrel', (x+(3.0+2.4)*s, y, gz), .38*s, 4.8*s, kit.steel, 'X', 12, .04)
    kit.box(f'Weapon {t} muzzle brake', (x+8.2*s, y, gz), (.8*s, .9*s, .7*s), kit.steel, .06)
    kit.cylinder(f'Weapon {t} roof hatch', (x-1.2*s, y-.7*s, zb+2.0*s+.08), .55*s, .16, kit.steel, 'Z', 12, .04)
    train = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(slot['angle']), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    for obj in set(bpy.data.objects)-fixed:
        obj.matrix_world = train @ obj.matrix_world
    ANCHOR_Z[t] = gz
    kit.empty(f'Weapon {t} anchor', (x, y, gz), slot['angle'])


def flare_launcher(kit, slot):
    # Hidden system mount: a flush dispenser box on the aft bulkhead in the
    # stern notch, six tubes facing along the slot angle (aft).
    x, y = slot['location']
    t = slot['id']
    z = -.5
    kit.box(f'Weapon {t} flare dispenser', (x+.4, y, z), (1.8, 3.4, 3.4), kit.steel, .12)
    for k in range(4):
        ty, tz = y+(k % 2-.5)*1.5, z+(k//2-.5)*1.5
        kit.cylinder(f'Weapon {t} flare tube {k+1}', (x-.55, ty, tz), .5, .3, kit.dark, 'X', 8, 0)
    ANCHOR_Z[t] = z
    kit.empty(f'Weapon {t} anchor', (x, y, z), slot['angle'])


def build(kit):
    for slot in SLOTS:
        {'HARDPOINT': hardpoint, 'TURRET': turret, 'HIDDEN': flare_launcher}[slot['mount']](kit, slot)
