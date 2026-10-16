"""Mudskipper weapon slots, exactly as mudskipper.ship declares them.

WS 003  TURRET  SMALL BALLISTIC  at (-9, 0), angle 180, arc 120
        The one gun: the codex's rear-facing turret, there to shoot down
        Salamander missiles (the Standard variant fits a light dual machine
        gun). Built as Turret 1, a small point-defence mount resting at 180,
        i.e. trained straight aft.
WS 002  HIDDEN  LARGE DECORATIVE at (17, 0), angle 0, arc 5
        No gun on the base hull. The Mk.II skin converts this slot to a
        large ballistic hardpoint. Kept as an empty marker only.
No other weapon positions exist, and the hull has no built-in weapons.

Turret 1 follows the Second Wind's naval-industrial idiom (copied, never
imported): a faceted barbette seated on whatever deck lies beneath it by
ray casts, an armoured collar, a dark traverse gap, then a lofted gun house
with a sloped glacis and raked sides, a trunnion mantlet the twin guns
pierce, jacketed light MG barrels with flash hiders, side ammunition cans
feeding the receivers, a rear bustle and a roof tracking director, which
a point-defence mount needs to find a missile at all. The sprite's turret
ring is about 4 units in radius; the barbette stays inside 3.6 so the deck
module's turret-well coaming (radius 3.8 and up) stays clear.
"""
import math
import bpy
from mathutils import Matrix, Vector

SLOTS = [
    {'id':'WS 003','mount':'TURRET','size':'SMALL','type':'BALLISTIC','location':[-9,0],'angle':180,'arc':120},
    {'id':'WS 002','mount':'HIDDEN','size':'LARGE','type':'DECORATIVE','location':[17,0],'angle':0,'arc':5},
]
BARBETTE_R = 3.4     # faceted foot; must stay <= 3.6 (deck coaming at >= 3.8)
SEAT_R = 3.2         # ray-cast ring for the seat
LIFT = 0.0           # extra barbette height, the only cure for a clash

# Gun-house plan, counter-clockwise from above, built bow-facing: chamfered
# nose at the bottom ring; glacis and raked sides pulled in at the roof.
HOUSE_BASE = [(2.4,-1.1),(2.4,1.1),(1.2,2.1),(-2.1,2.1),(-2.1,-2.1),(1.2,-2.1)]
HOUSE_ROOF = [(1.0,-.8),(1.0,.8),(.4,1.65),(-1.9,1.65),(-1.9,-1.65),(.4,-1.65)]
HOUSE_H = 1.7
# Rear bustle: ammunition stowage and vents, lower than the house roof.
BUSTLE_BASE = [(-2.0,-1.75),(-2.0,1.75),(-2.75,1.55),(-3.15,1.0),(-3.15,-1.0),(-2.75,-1.55)]
BUSTLE_ROOF = [(-1.9,-1.5),(-1.9,1.5),(-2.65,1.35),(-2.95,.85),(-2.95,-.85),(-2.65,-1.35)]


def loft(kit, name, base, roof, z0, z1, mat, bevel=.1):
    """Prism lofted between two CCW plans of equal vertex count."""
    n = len(base)
    verts = [(x,y,z0) for x,y in base] + [(x,y,z1) for x,y in roof]
    faces = [(i,(i+1)%n,n+(i+1)%n,n+i) for i in range(n)]
    faces += [tuple(reversed(range(n))), tuple(range(n,2*n))]
    return kit.mesh(name, verts, faces, mat, bevel=bevel)


def strut(kit, name, a, b, w, h, mat, bevel=.03):
    """A box of section w x h running from point a to point b."""
    a, b = Vector(a), Vector(b)
    d = b-a
    ob = kit.box(name, (a+b)/2, (d.length, w, h), mat, bevel)
    ob.rotation_euler = d.to_track_quat('X','Z').to_euler()
    return ob


def seat(kit, i, slot):
    """Highest and lowest hull Z under the barbette footprint."""
    x, y = slot['location']
    zs = []
    for k in range(13):
        a = k*math.tau/12
        px, py = (x,y) if k == 12 else (x+SEAT_R*math.cos(a), y+SEAT_R*math.sin(a))
        hit = kit.surface(px, py)
        if hit is None:
            raise RuntimeError(f'Turret {i} ({slot["id"]}) has no supporting hull surface')
        zs.append(hit[0].z)
    return max(zs), min(zs)


def gun(kit, tag, x, y, z):
    """One light MG: receiver sleeve, finned cooling jacket, bare barrel and
    a slotted flash hider, laid along +X from the mantlet face."""
    kit.cylinder(f'{tag} receiver sleeve', (x+2.95,y,z), .24, .7, kit.steel, 'X', 12, .03)
    kit.cylinder(f'{tag} cooling jacket', (x+4.15,y,z), .19, 1.7, kit.steel, 'X', 12, .02)
    for k in range(3):
        kit.cylinder(f'{tag} jacket clamp band', (x+3.55+k*.6,y,z), .235, .1, kit.dark, 'X', 12, 0)
    kit.cylinder(f'{tag} barrel', (x+5.4,y,z), .1, .9, kit.steel, 'X', 10, 0)
    kit.cylinder(f'{tag} flash hider', (x+6.05,y,z), .16, .5, kit.steel, 'X', 10, .02)
    kit.box(f'{tag} flash hider slot', (x+6.05,y,z), (.32,.34,.07), kit.dark, 0)
    kit.cylinder(f'{tag} bore', (x+6.3,y,z), .08, .02, kit.dark, 'X', 10, 0)


def build_turret(kit, i, slot):
    x, y = slot['location']
    zmax, zmin = seat(kit, i, slot)
    zmax += LIFT
    tag = f'Turret {i} {slot["id"]}'
    # Fixed structure: a faceted foot sunk into the deck, an armoured collar
    # proud of the highest ground, and the dark traverse gap.
    kit.cylinder(f'{tag} barbette', (x,y,(zmin-.4+zmax+.4)/2), BARBETTE_R, zmax+.8-zmin, kit.steel, 'Z', 12, .1)
    kit.cylinder(f'{tag} armoured collar', (x,y,zmax+.6), 3.1, .4, kit.steel, 'Z', 12, .06)
    for k in range(6):
        a = k*math.tau/6 + math.tau/12
        kit.box(f'{tag} collar bolt cover', (x+3.12*math.cos(a), y+3.12*math.sin(a), zmax+.6),
                (.3,.5,.3), kit.steel, .04).rotation_euler[2] = a
    kit.cylinder(f'{tag} traverse gap', (x,y,zmax+.9), 2.85, .2, kit.dark, 'Z', 24, 0)
    zb = zmax+1.0
    # Everything above the traverse gap is built bow-facing, then trained
    # about the mount centre to the slot's resting angle.
    fixed = set(bpy.data.objects)
    P = lambda pts: [(x+px,y+py) for px,py in pts]
    kit.cylinder(f'{tag} turntable ring', (x,y,zb+.08), 2.7, .16, kit.steel, 'Z', 24, .03)
    roof = zb+.16+HOUSE_H
    loft(kit, f'{tag} gun house', P(HOUSE_BASE), P(HOUSE_ROOF), zb+.16, roof, kit.sponson)
    loft(kit, f'{tag} rear bustle', P(BUSTLE_BASE), P(BUSTLE_ROOF), zb+.25, zb+1.45, kit.steel, .08)
    for k in range(3):
        kit.box(f'{tag} bustle vent slat', (x-2.5,y+(k-1)*.75,zb+1.47), (.7,.4,.05), kit.dark, 0)
    # Trunnion mantlet: a drum across the glacis on two trunnion caps; the
    # guns pierce its face.
    gz = zb+.95
    kit.cylinder(f'{tag} mantlet drum', (x+2.2,y,gz), .62, 2.1, kit.steel, 'Y', 16, .06)
    kit.box(f'{tag} mantlet face', (x+2.6,y,gz), (.4,1.8,.95), kit.steel, .08)
    for s in (1,-1):
        kit.cylinder(f'{tag} trunnion cap', (x+2.2,y+s*1.13,gz), .38, .16, kit.dark, 'Y', 12, .02)
    for gy, side in ((.5,'port'),(-.5,'starboard')):
        gun(kit, f'{tag} {side} gun', x, y+gy, gz)
    # Ammunition: a belt can on each cheek feeding its gun through a chute
    # that enters the house where the glacis chamfer begins.
    for s, side in ((1,'port'),(-1,'starboard')):
        kit.box(f'{tag} {side} ammunition can', (x+.1,y+s*2.35,zb+.75), (1.3,.5,.95), kit.tan, .06)
        kit.box(f'{tag} {side} ammunition can lid', (x+.1,y+s*2.35,zb+1.25), (1.1,.4,.06), kit.dark, 0)
        strut(kit, f'{tag} {side} feed chute', (x+.7,y+s*2.3,zb+1.05), (x+1.75,y+s*1.4,zb+1.05), .32, .28, kit.dark)
    # Roof: commander's hatch, and the point-defence tracking director on a
    # short post so it sees over the glacis; a search dish behind it.
    kit.cylinder(f'{tag} roof hatch', (x-1.15,y+.75,roof+.08), .5, .16, kit.steel, 'Z', 12, .03)
    kit.cylinder(f'{tag} director post', (x-.6,y-.7,roof+.3), .14, .6, kit.steel, 'Z', 8, 0)
    kit.box(f'{tag} tracking director', (x-.55,y-.7,roof+.85), (1.1,.75,.6), kit.steel, .1)
    kit.box(f'{tag} director lens', (x+.01,y-.7,roof+.88), (.04,.5,.32), kit.glass, 0)
    kit.cylinder(f'{tag} search dish post', (x-1.55,y-.7,roof+.25), .1, .5, kit.steel, 'Z', 8, 0)
    dish = kit.cylinder(f'{tag} search dish', (x-1.65,y-.7,roof+.7), .5, .1, kit.steel, 'X', 16, .02)
    dish.rotation_euler = (0, math.radians(70), 0)
    bpy.context.view_layer.update()
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
