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

Pass 2 detail, sized for a ~9 m boat (0.11 m per unit): each mount is a
small naval-style twin light gun. The sprite's big dark ring round each gun
(radius ~4.5 forward, ~5 aft) is a recessed well: a dark well floor laid on
the deck round the barbette, closed by a bolted ring coaming, both seated
vertex by vertex on whatever deck lies beneath by ray casts. In the well
stands a faceted barbette with an armoured collar and the dark traverse
gap; above it, trained, a turntable ring and a lofted gun house (~0.5 m
across) with a sloped glacis and raked sides, a rear bustle, a trunnion
mantlet drum the two guns pierce, jacketed barrels with clamp bands and
slotted flash hiders, an ammunition feed box on each cheek with a feed
chute into the house, a roof hatch and a sight/director on a short post.
"""
import math
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree
from hm_kit import bx, blocks

SLOTS = [
    {'id': 'WS 001', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [12, 18], 'angle': 45, 'arc': 150},
    {'id': 'WS 002', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [12, -18], 'angle': -45, 'arc': 150},
    {'id': 'WS 003', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [-18, 0], 'angle': 180, 'arc': 210},
    {'id': 'WS 004', 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM', 'location': [-40, 0], 'angle': 180, 'arc': 30},
]
BARBETTE_R = 3.0     # faceted foot; the well floor runs from here to the coaming
LIFT = 0.0           # extra barbette height, the only cure for a clash
# The sprite's dark ring: outer radius of the coaming per slot (forward ~4.5, aft ~5).
COAMING_R = {'WS 001': 4.6, 'WS 002': 4.6, 'WS 003': 5.0}
COAMING_W = .45      # coaming lip width
COAMING_H = .4       # coaming lip height above the deck
CLASH_WITH = ('Main hull - spine', 'Wing body', 'Aft drive block', 'Nose collar')

# Gun-house plan, counter-clockwise from above, built bow-facing about the
# slot centre: chamfered nose at the foot, glacis and raked sides drawn in
# at the roof. About 4.2 units (0.46 m) across.
HOUSE_BASE = [(2.3, -1.0), (2.3, 1.0), (1.1, 2.05), (-2.0, 2.05), (-2.0, -2.05), (1.1, -2.05)]
HOUSE_ROOF = [(.95, -.75), (.95, .75), (.35, 1.6), (-1.8, 1.6), (-1.8, -1.6), (.35, -1.6)]
HOUSE_H = 1.65
BUSTLE_BASE = [(-1.9, -1.7), (-1.9, 1.7), (-2.6, 1.5), (-2.95, .95), (-2.95, -.95), (-2.6, -1.5)]
BUSTLE_ROOF = [(-1.8, -1.45), (-1.8, 1.45), (-2.5, 1.3), (-2.75, .8), (-2.75, -.8), (-2.5, -1.3)]


def loft(kit, name, base, roof, z0, z1, mat, bevel=.08):
    """Prism lofted between two CCW plans of equal vertex count."""
    n = len(base)
    verts = [(x, y, z0) for x, y in base] + [(x, y, z1) for x, y in roof]
    faces = [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    faces += [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    return kit.mesh(name, verts, faces, mat, bevel=bevel)


def strut(kit, name, a, b, w, h, mat, bevel=.03):
    """A box of section w x h running from point a to point b."""
    a, b = Vector(a), Vector(b)
    d = b - a
    ob = kit.box(name, (a+b)/2, (d.length, w, h), mat, bevel)
    ob.rotation_euler = d.to_track_quat('X', 'Z').to_euler()
    return ob


def deck_z(kit, x, y):
    hit = kit.surface_any(x, y)
    if hit is None:
        raise RuntimeError(f'No hull deck under ({x:.2f}, {y:.2f}) for a turret well')
    return hit[0].z


def deck_ring(kit, name, x, y, r0, r1, sink, height, mat, n=36, bevel=0, top=None):
    """An annulus seated on the deck: every foot vertex is ray-cast to the
    hull beneath it and sunk `sink` below. The top follows the deck at
    `height` above it, or, given `top`, is one level plane, so a ring that
    reaches over a chamfered deck edge stands as a boss rather than
    drooping down the chamfer."""
    verts = []
    for k in range(n):
        a = k*math.tau/n
        c, s = math.cos(a), math.sin(a)
        zi = deck_z(kit, x + r0*c, y + r0*s)
        zo = deck_z(kit, x + r1*c, y + r1*s)
        verts += [(x + r0*c, y + r0*s, zi - sink), (x + r1*c, y + r1*s, zo - sink),
                  (x + r1*c, y + r1*s, top if top is not None else zo + height),
                  (x + r0*c, y + r0*s, top if top is not None else zi + height)]
    faces = []
    for k in range(n):
        a, b = 4*k, 4*((k+1) % n)
        faces += [(a+0, b+0, b+1, a+1),   # foot
                  (a+1, b+1, b+2, a+2),   # outer wall
                  (a+2, b+2, b+3, a+3),   # top
                  (a+3, b+3, b+0, a+0)]   # inner wall
    return kit.mesh(name, verts, faces, mat, bevel)


def seat(kit, x, y, r):
    """Highest and lowest hull Z under the barbette footprint."""
    zs = [deck_z(kit, x, y)]
    for k in range(12):
        a = k*math.tau/12
        zs.append(deck_z(kit, x + r*math.cos(a), y + r*math.sin(a)))
    return max(zs), min(zs)


def seat_z(kit, x, y, r):
    """Highest hull point under the barbette's ring."""
    return seat(kit, x, y, r)[0]


def well(kit, tag, sid, x, y):
    """The sprite's dark ring: recessed well floor and bolted ring coaming."""
    ro = COAMING_R[sid]
    ri = ro - COAMING_W
    # One datum for the whole well: the highest deck under the coaming.
    zref = max(deck_z(kit, x + r*math.cos(a), y + r*math.sin(a))
               for r in (BARBETTE_R, ri, ro) for a in (k*math.tau/36 for k in range(36)))
    deck_ring(kit, f'{tag} well floor', x, y, BARBETTE_R - .2, ri + .05, .15, .05, kit.dark, 36, top=zref + .05)
    deck_ring(kit, f'{tag} ring coaming', x, y, ri, ro, .3, COAMING_H, kit.steel, 36, .05, top=zref + COAMING_H)
    deck_ring(kit, f'{tag} ring coaming drip lip', x, y, ri - .08, ri + .05, .1, COAMING_H + .06, kit.dark, 36, top=zref + COAMING_H + .06)
    bolts = []
    for k in range(16):
        a = k*math.tau/16 + math.tau/32
        c, s = math.cos(a), math.sin(a)
        rb = (ri + ro)/2
        z = zref + COAMING_H
        px, py = x + rb*c, y + rb*s
        bolts.append((px - .15, px + .15, py - .15, py + .15, z - .05, z + .12))
    blocks(kit, f'{tag} ring coaming bolts', bolts, kit.steel)
    # Two drain scuppers in the well floor, at the low side of the deck.
    for k in range(2):
        a = math.tau*(k + .25)/2
        c, s = math.cos(a), math.sin(a)
        r = (BARBETTE_R + ri)/2
        z = zref
        ob = kit.box(f'{tag} well drain grate', (x + r*c, y + r*s, z + .07), (.7, .35, .04), kit.steel, 0)
        ob.rotation_euler[2] = a + math.pi/2


def gun(kit, tag, k, x, y, z):
    """One light gun along +X from the mantlet face: the bare barrel (the
    pass-1 'barrel k'), a receiver sleeve, a cooling jacket with clamp bands,
    and a slotted flash hider."""
    kit.cylinder(f'{tag} barrel {k}', (x + 4.55, y, z), .1, 3.7, kit.steel, 'X', 10, 0)
    kit.cylinder(f'{tag} barrel {k} receiver sleeve', (x + 3.0, y, z), .24, .7, kit.steel, 'X', 12, .03)
    kit.cylinder(f'{tag} barrel {k} cooling jacket', (x + 4.2, y, z), .19, 1.7, kit.steel, 'X', 12, .02)
    for j in range(3):
        kit.cylinder(f'{tag} barrel {k} jacket clamp band', (x + 3.6 + j*.6, y, z), .235, .1, kit.dark, 'X', 12, 0)
    kit.cylinder(f'{tag} barrel {k} flash hider', (x + 6.55, y, z), .16, .55, kit.steel, 'X', 10, .02)
    kit.box(f'{tag} barrel {k} flash hider slot', (x + 6.55, y, z), (.36, .34, .07), kit.dark, 0)
    kit.cylinder(f'{tag} barrel {k} bore', (x + 6.83, y, z), .075, .02, kit.dark, 'X', 10, 0)


def build_turret(kit, n, slot):
    x, y = slot['location']
    sid = slot['id']
    tag = f'Turret {n} {sid}'
    zmax, zmin = seat(kit, x, y, BARBETTE_R)
    zmax += LIFT
    slot['seat_z'] = round(zmax, 2)
    well(kit, tag, sid, x, y)
    # Fixed structure: a faceted foot sunk into the deck, an armoured collar
    # proud of the highest ground, the dark traverse gap.
    kit.cylinder(f'{tag} barbette', (x, y, (zmin - .4 + zmax + 1.0)/2), BARBETTE_R, zmax + 1.0 - zmin + .4, kit.steel, 'Z', 12, .1)
    kit.cylinder(f'{tag} armoured collar', (x, y, zmax + 1.1), BARBETTE_R + .12, .4, kit.steel, 'Z', 12, .06)
    for k in range(6):
        a = k*math.tau/6 + math.tau/12
        kit.box(f'{tag} collar bolt cover', (x + (BARBETTE_R + .14)*math.cos(a), y + (BARBETTE_R + .14)*math.sin(a), zmax + 1.1),
                (.3, .5, .3), kit.steel, .04).rotation_euler[2] = a
    kit.cylinder(f'{tag} traverse gap', (x, y, zmax + 1.4), BARBETTE_R*.92, .2, kit.dark, 'Z', 24, 0)
    zt = zmax + 1.5
    # Everything above the traverse gap is built bow-facing, then trained.
    fixed = set(bpy.data.objects)
    P = lambda pts: [(x + px, y + py) for px, py in pts]
    kit.cylinder(f'{tag} ring', (x, y, zt + .08), BARBETTE_R*.9, .16, kit.panel, 'Z', 24, .03)
    zh = zt + .16
    roof = zh + HOUSE_H
    loft(kit, f'{tag} gun house', P(HOUSE_BASE), P(HOUSE_ROOF), zh, roof, kit.cream)
    loft(kit, f'{tag} rear bustle', P(BUSTLE_BASE), P(BUSTLE_ROOF), zh + .1, zh + 1.25, kit.panel, .06)
    for k in range(3):
        kit.box(f'{tag} bustle vent slat', (x - 2.35, y + (k - 1)*.7, zh + 1.27), (.7, .4, .05), kit.dark, 0)
    # House plate seams: a dark band across the glacis root and round the roof edge.
    kit.box(f'{tag} gun house roof seam', (x - .45, y, roof + .01), (.08, 3.0, .03), kit.dark, 0)
    # Trunnion mantlet: a drum across the glacis on two trunnion caps.
    gz = zh + .85
    kit.cylinder(f'{tag} mantlet', (x + 2.2, y, gz), .6, 2.0, kit.steel, 'Y', 16, .06)
    kit.box(f'{tag} mantlet face', (x + 2.55, y, gz), (.4, 1.75, .9), kit.steel, .08)
    for s in (1, -1):
        kit.cylinder(f'{tag} trunnion cap', (x + 2.2, y + s*1.08, gz), .36, .16, kit.dark, 'Y', 12, .02)
    kit.box(f'{tag} mantlet gun ports', (x + 2.76, y, gz), (.04, 1.4, .5), kit.dark, 0)
    for k, gy in ((1, .48), (2, -.48)):
        gun(kit, tag, k, x, y + gy, gz)
    # Ammunition feed boxes on the cheeks, each feeding its gun through a chute.
    for s, side in ((1, 'port'), (-1, 'starboard')):
        kit.box(f'{tag} {side} ammunition feed box', (x + .05, y + s*2.3, zh + .6), (1.3, .5, .95), kit.panel, .06)
        kit.box(f'{tag} {side} ammunition feed box lid', (x + .05, y + s*2.3, zh + 1.1), (1.1, .4, .06), kit.dark, 0)
        kit.box(f'{tag} {side} ammunition feed box latch', (x + .05, y + s*2.56, zh + .7), (.3, .04, .2), kit.steel, 0)
        strut(kit, f'{tag} {side} feed chute', (x + .65, y + s*2.25, zh + .9), (x + 1.6, y + s*1.4, zh + .9), .3, .26, kit.dark)
    # Roof: crew hatch and a sight/director on a short post seeing over the glacis.
    kit.cylinder(f'{tag} roof hatch', (x - 1.05, y + .7, roof + .07), .48, .14, kit.steel, 'Z', 12, .03)
    kit.box(f'{tag} roof hatch hinge', (x - 1.5, y + .7, roof + .12), (.12, .5, .1), kit.dark, 0)
    kit.cylinder(f'{tag} sight post', (x - .55, y - .7, roof + .25), .13, .5, kit.steel, 'Z', 8, 0)
    kit.box(f'{tag} sight director', (x - .5, y - .7, roof + .72), (1.0, .7, .55), kit.steel, .08)
    kit.box(f'{tag} sight director hood', (x - .02, y - .7, roof + 1.02), (.22, .74, .06), kit.steel, 0)
    kit.box(f'{tag} sight director lens', (x + .01, y - .7, roof + .75), (.04, .46, .3), kit.glass, 0)
    # Lifting eyes on the roof corners.
    for s in (1, -1):
        kit.cylinder(f'{tag} lifting eye', (x - 1.6, y + s*1.35, roof + .12), .14, .06, kit.steel, 'Y', 8, 0)
    bpy.context.view_layer.update()
    train = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(slot['angle']), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    for ob in set(bpy.data.objects) - fixed:
        ob.matrix_world = train @ ob.matrix_world
    bpy.context.view_layer.update()
    return [ob for ob in bpy.data.objects if ob not in fixed]


def _bvh(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    mw = ob.matrix_world
    verts = [mw @ v.co for v in me.vertices]
    polys = [tuple(p.vertices) for p in me.polygons]
    ev.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys)


def clash_report(trained_by_turret):
    """BVH overlap of every trained part (barrels included) against the hull
    solids and every object that is not part of the same turret."""
    bpy.context.view_layer.update()
    hulls = {n: _bvh(bpy.data.objects[n]) for n in CLASH_WITH if bpy.data.objects.get(n)}
    others = {}
    lines, total = [], 0
    for tag, parts in trained_by_turret.items():
        mine = set(p.name for p in parts)
        prefix = tag + ' '
        for ob in bpy.data.objects:
            if ob.type == 'MESH' and not ob.name.startswith(prefix) and ob.name not in hulls and ob.name not in others:
                others[ob.name] = None
        for p in parts:
            if p.type != 'MESH':
                continue
            t = _bvh(p)
            for hn, ht in hulls.items():
                if t.overlap(ht):
                    lines.append(f'  CLASH {p.name} x {hn}')
                    total += 1
            for on in others:
                if on.startswith(prefix):
                    continue
                if others[on] is None:
                    others[on] = _bvh(bpy.data.objects[on])
                if t.overlap(others[on]):
                    lines.append(f'  CLASH {p.name} x {on}')
                    total += 1
    print(f'[hm_turrets] clash check: {total} overlap(s) of trained turret parts with other objects')
    for line in lines:
        print(line)
    return total


def build(kit):
    n = 0
    trained = {}
    for slot in SLOTS:
        if slot['mount'] != 'TURRET':
            ob = bpy.data.objects.new(f"Slot {slot['id']} system marker", None)
            ob.location = (*slot['location'], 0)
            ob.empty_display_type = 'PLAIN_AXES'
            bpy.context.collection.objects.link(ob)
            continue
        n += 1
        trained[f"Turret {n} {slot['id']}"] = build_turret(kit, n, slot)
    clash_report(trained)
