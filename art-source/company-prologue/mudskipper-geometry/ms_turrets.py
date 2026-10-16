"""Mudskipper weapon slots, exactly as mudskipper.ship declares them.

WS 003  TURRET  SMALL BALLISTIC  at (-9, 0), angle 180, arc 120
        The one gun: the codex's rear-facing turret (the Standard variant
        fits a light dual machine gun). Built as Turret 1, resting at 180,
        i.e. trained straight aft.
WS 002  HIDDEN  LARGE DECORATIVE at (17, 0), angle 0, arc 5
        No gun on the base hull. The Mk.II skin converts this slot to a
        large ballistic hardpoint. Kept as an empty marker only.
No other weapon positions exist, and the hull has no built-in weapons.
"""
import math
import bpy
from mathutils import Matrix

SLOTS = [
    {'id':'WS 003','mount':'TURRET','size':'SMALL','type':'BALLISTIC','location':[-9,0],'angle':180,'arc':120},
    {'id':'WS 002','mount':'HIDDEN','size':'LARGE','type':'DECORATIVE','location':[17,0],'angle':0,'arc':5},
]
HOUSE_BASE = [(2.4,-1.1),(2.4,1.1),(1.2,2.1),(-2.1,2.1),(-2.1,-2.1),(1.2,-2.1)]
HOUSE_ROOF = [(1.0,-.8),(1.0,.8),(.4,1.65),(-1.9,1.65),(-1.9,-1.65),(.4,-1.65)]


def loft(kit, name, base, roof, z0, z1, mat):
    n = len(base)
    verts = [(x,y,z0) for x,y in base] + [(x,y,z1) for x,y in roof]
    faces = [(i,(i+1)%n,n+(i+1)%n,n+i) for i in range(n)]
    faces += [tuple(reversed(range(n))), tuple(range(n,2*n))]
    return kit.mesh(name, verts, faces, mat, bevel=.1)


def build_turret(kit, i, slot):
    x, y = slot['location']
    zs = []
    for k in range(13):
        a = k*math.tau/12
        px, py = (x,y) if k == 12 else (x+3.2*math.cos(a), y+3.2*math.sin(a))
        hit = kit.surface(px, py)
        if hit is None:
            raise RuntimeError(f'Turret {i} ({slot["id"]}) has no supporting hull surface')
        zs.append(hit[0].z)
    zmax, zmin = max(zs), min(zs)
    tag = f'Turret {i} {slot["id"]}'
    kit.cylinder(f'{tag} barbette', (x,y,(zmin-.4+zmax+.5)/2), 3.4, zmax+.9-zmin, kit.steel, 'Z', 12, .1)
    kit.cylinder(f'{tag} traverse gap', (x,y,zmax+.65), 2.9, .3, kit.dark, 'Z', 24, 0)
    zb = zmax+.8
    fixed = set(bpy.data.objects)
    P = lambda pts: [(x+px,y+py) for px,py in pts]
    loft(kit, f'{tag} gun house', P(HOUSE_BASE), P(HOUSE_ROOF), zb, zb+1.8, kit.steel)
    kit.box(f'{tag} mantlet', (x+2.3,y,zb+.8), (.6,2.4,1.1), kit.steel, .08)
    # Light dual machine gun: two plain barrels on the mantlet.
    for gy, side in ((.55,'left'),(-.55,'right')):
        kit.cylinder(f'{tag} {side} barrel', (x+4.4,y+gy,zb+.8), .22, 4.0, kit.steel, 'X', 12, .03)
    kit.box(f'{tag} ammunition bustle', (x-2.6,y,zb+.7), (1.4,2.8,1.2), kit.steel, .1)
    # Built bow-facing, then trained about the mount to the slot angle.
    train = Matrix.Translation((x,y,0)) @ Matrix.Rotation(math.radians(slot['angle']),4,'Z') @ Matrix.Translation((-x,-y,0))
    for ob in set(bpy.data.objects)-fixed:
        ob.matrix_world = train @ ob.matrix_world
    return zmax


def build(kit):
    for i, slot in enumerate(s for s in SLOTS if s['mount'] != 'HIDDEN'):
        slot['seat_z'] = round(build_turret(kit, i+1, slot), 3)
    for slot in SLOTS:
        if slot['mount'] == 'HIDDEN':
            hit = kit.surface(*slot['location'])
            z = hit[0].z if hit else 0
            e = bpy.data.objects.new(f'Mount {slot["id"]} hidden {slot["size"].lower()} {slot["type"].lower()} marker', None)
            e.empty_display_type = 'SINGLE_ARROW'
            e.empty_display_size = 6
            e.location = (*slot['location'], z)
            e.rotation_euler = (0, math.pi/2, math.radians(slot['angle']))
            for k in ('id','mount','size','type','angle','arc'):
                e[k] = slot[k]
            bpy.context.collection.objects.link(e)
            slot['seat_z'] = round(z, 3)
