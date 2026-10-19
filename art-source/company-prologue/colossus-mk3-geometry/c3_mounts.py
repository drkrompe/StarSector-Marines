"""Colossus Mk.III weapon mounts: twelve small ballistic turrets and four hidden flare launchers.

Slots exactly as colossus3.ship lists them (the pirate refit; WS 017 is the launch bay,
built in c3_hull). Every gun stands on one of the sprite's big turret rings: a sponson
drum built out over the cut-down module wall (WS 001-004, 013, 014), or a solid deck tub
(WS 009-012 on the module decks and longerons, WS 015/016 on the hold deck either side of
the launch bay). Each rests at its slot angle.

Guns, chosen by station rather than by anything the .ship says (small ballistic only):
the four bow guns WS 013-016 (arcs 160-200, the close-in arcs) are light rotary chainguns;
WS 001, 003, 011 and 012 keep the Colossus's twin light autocannon; the broadside pairs
WS 002/004 and 009/010 are single long light cannon. All share the Colossus's faceted gun
house, with pirate cheek armour welded over its sides and a searchlight on the roof.

Turret (about 0.5 m per unit): a fixed barbette with a bolted base ring and a dark
traverse race, and above that gap the turning parts (custom property traverse = True).
Launcher: as on the Colossus, a plinth, armoured housing and lid, and a rack of six
flare tubes tilted to the surface (WS 005/006 sit on the bow corner sponsons' rims).

Names: 'Turret <i> WS 0nn ...' and 'Launcher <i> WS 00n flare launcher ...', with a
'<prefix> pivot' empty on the traverse axis and muzzle empties at the barrel tips (+X along
the rest angle): '<prefix> muzzle' for a single bore, '<prefix> muzzle port|starboard' for
the twin. Only parts above the traverse gap turn: T(x,y) @ Rz(angle) @ T(-x,-y).
"""
import math
import bpy
import c3_hull
from c3_hull import Parts
from c3_kit import prism, section
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree


def _slot(id_, loc, angle, arc, gun=None):
    if gun:
        return {'id': id_, 'location': loc, 'angle': angle, 'arc': arc, 'mount': 'TURRET', 'size': 'SMALL',
                'type': 'BALLISTIC', 'gun': gun}
    return {'id': id_, 'location': loc, 'angle': angle, 'arc': arc, 'mount': 'HIDDEN', 'size': 'SMALL',
            'type': 'SYSTEM'}


SLOTS = [
    _slot('WS 001', (42, 60), 80, 140, 'twin'),
    _slot('WS 002', (-6, 63), 90, 130, 'cannon'),
    _slot('WS 003', (42, -58), -80, 140, 'twin'),
    _slot('WS 004', (-6, -61), -90, 130, 'cannon'),
    _slot('WS 005', (89, 64), 45, 20),
    _slot('WS 006', (89, -61), -45, 20),
    _slot('WS 007', (-83, 84), 100, 20),
    _slot('WS 008', (-84, -81), 260, 20),
    _slot('WS 009', (19, -51), -90, 130, 'cannon'),
    _slot('WS 010', (18, 53), 90, 130, 'cannon'),
    _slot('WS 011', (59, -39), -40, 170, 'twin'),
    _slot('WS 012', (59, 41), 50, 170, 'twin'),
    _slot('WS 013', (80, -53), -45, 200, 'rotary'),
    _slot('WS 014', (80, 55), 45, 200, 'rotary'),
    _slot('WS 015', (84, 29), 6, 160, 'rotary'),
    _slot('WS 016', (84, -27), -6, 160, 'rotary'),
]
# WS 017 (LAUNCH_BAY, four locations) is the launch bay in c3_hull; listed for the install check.
BAY_SLOT = {'id': 'WS 017', 'locations': [69, -5, 69, 9, 52, -6, 52, 9], 'angle': 0, 'arc': 8,
            'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'LAUNCH_BAY'}
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
TUBS = {}

# Turret dimensions (units), the Colossus's: barbette radius, base flange radius, barbette
# height above the tub, traverse gap, gun-house height, gun axis above the house floor,
# muzzle distance from the traverse axis.
BARBETTE_R, FLANGE_R, BARBETTE_H, GAP = 5.4, 6.3, 2.0, .25
HOUSE_H, AXIS_H, MUZZLE_X = 3.4, 1.6, 12.2
BARREL_DY = 1.1
# Ring radii read off colossus3_pirate.png: the sponsons carry the outline out to rel y 70-74.
SPONSONS = {'WS 001': 13.0, 'WS 003': 13.0, 'WS 002': 12.5, 'WS 004': 12.5, 'WS 013': 13.0, 'WS 014': 13.0}
TUB_R = 7.0


def _turn(objs, x, y, angle):
    bpy.context.view_layer.update()  # matrix_world must carry each part's own rotation first
    m = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(angle), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    for o in objs:
        o.matrix_world = m @ o.matrix_world


def _place(objs, m):
    """Move parts built in a local frame by the world matrix m."""
    bpy.context.view_layer.update()
    for o in objs:
        o.matrix_world = m @ o.matrix_world


def _frustum(kit, name, bottom, z0, top, z1, mat, bevel=.12):
    """Faceted solid between two equal-count plan rings (local XY), bottom at z0, top at z1."""
    n = len(bottom)
    verts = [(x, y, z0) for x, y in bottom]+[(x, y, z1) for x, y in top]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def _studs(kit, name, centres, r, h, mat, n=6, axis='Z'):
    """One mesh of small hexagonal studs (bolt heads, rivets) standing on the given centres."""
    verts, faces = [], []
    for cx, cy, cz in centres:
        b = len(verts)
        for k in range(n):
            a = 2*math.pi*k/n
            u, v = r*math.cos(a), r*math.sin(a)
            if axis == 'Z':
                verts += [(cx+u, cy+v, cz), (cx+u, cy+v, cz+h)]
            else:  # +X facing stud
                verts += [(cx, cy+u, cz+v), (cx+h, cy+u, cz+v)]
        faces.append(tuple(b+2*k for k in reversed(range(n))))
        faces.append(tuple(b+2*k+1 for k in range(n)))
        faces += [(b+2*k, b+2*((k+1) % n), b+2*((k+1) % n)+1, b+2*k+1) for k in range(n)]
    return kit.mesh(name, verts, faces, mat)


def _ring(cx, cy, r, n, z, phase=0.0):
    return [(cx+r*math.cos(phase+2*math.pi*k/n), cy+r*math.sin(phase+2*math.pi*k/n), z) for k in range(n)]


def _clip(poly, rel_min, sign):
    """Keep the part of a plan polygon outboard of rel y = rel_min on side `sign` (Sutherland-Hodgman)."""
    edge = c3_hull.y(rel_min, sign)
    inside = (lambda p: p[1] >= edge) if sign > 0 else (lambda p: p[1] <= edge)
    out = []
    for i, p in enumerate(poly):
        q = poly[i-1]
        if inside(p):
            if not inside(q):
                t = (edge-q[1])/(p[1]-q[1])
                out.append((q[0]+t*(p[0]-q[0]), edge))
            out.append(p)
        elif inside(q):
            t = (edge-q[1])/(p[1]-q[1])
            out.append((q[0]+t*(p[0]-q[0]), edge))
    return out


def gun_tub(kit, s):
    """The armoured ring each refit gun stands on: the sprite's big turret rings.

    A sponson (SPONSONS) is a drum built out over the module wall, trimmed at the longeron's
    outboard flange so it never rides over it, standing 3 units into the module and carried
    on welded gusset brackets where it overhangs. A deck tub is a solid pedestal from the
    lowest deck under it to just over the highest, so a gun set over a longeron or a deck step
    stands level. Both are added to the seating surfaces; the barbette stands on top."""
    x, y = s['location']
    p = s['prefix']
    r = SPONSONS.get(s['id'], TUB_R)
    ring = [(x+r*math.cos(2*math.pi*k/24), y+r*math.sin(2*math.pi*k/24)) for k in range(24)]
    sign = 1 if y > c3_hull.YC else -1
    samples = [kit.surface(px, py) for px, py in ring[::3]+[(x, y)]]
    zs = [h[0].z for h in samples if h]
    if s['id'] in SPONSONS:
        plan = _clip(ring, c3_hull.LONGERON_Y[1]+.75, sign)
        deck = c3_hull.SIDE_DECK
        z0, z1 = deck-3.0, deck+1.4
    else:
        plan = ring
        z0, z1 = min(zs)-.3, max(zs)+1.2
    plan = c3_hull.ccw(plan)
    tub = prism(kit, f'{p} gun tub', plan, section(z0, z1, .6 if s['id'] in SPONSONS else .2, .45), kit.armour, bevel=.1)
    kit.hull.append(tub)
    TUBS[s['id']] = z1
    # Rim: a bolted armour ring round the top edge and an orange traverse-danger ring painted
    # round the barbette; on a sponson, radial plate seams in weld beads.
    rim = Parts()
    k0 = 0
    for k in range(48):
        a = 2*math.pi*(k+.5)/48
        px, py = x+(r-.55)*math.cos(a), y+(r-.55)*math.sin(a)
        if s['id'] in SPONSONS and (py-c3_hull.y(c3_hull.LONGERON_Y[1]+1.4, sign))*sign < 0:
            continue
        rim.cyl((px, py, z1), .2, .16, 'Z', 6)
        k0 += 1
    rim.build(kit, f'{p} gun tub rim bolts', kit.steel)
    Parts().tube(x, y, z1+.01, z1+.07, FLANGE_R+1.2, FLANGE_R+1.2, .5, 40).build(kit, f'{p} gun tub danger ring', kit.orange)
    if s['id'] in SPONSONS:
        welds, gussets = Parts(), Parts()
        a0 = math.atan2(sign, 0)
        for da in (-1.0, -.35, .35, 1.0):
            a = a0+da
            u0, u1 = FLANGE_R+1.8, r-.3
            if (y+u1*math.sin(a)-c3_hull.y(c3_hull.LONGERON_Y[1]+1, sign))*sign < 0:
                continue
            welds.beam((x+u0*math.cos(a), y+u0*math.sin(a), z1), (x+u1*math.cos(a), y+u1*math.sin(a), z1), .22, .2)
        welds.build(kit, f'{p} sponson plate welds', kit.weld)
        wall = c3_hull.y(c3_hull.WALL, sign)
        for dx in (-r*.55, 0.0, r*.55):
            cx = x+dx
            half = math.sqrt(max(r*r-dx*dx, 0))
            reach = (y+sign*half-wall)*sign-.4                     # overhang beyond the wall here
            if reach < 1.0:
                continue
            gussets.slab([(cx-.3, wall, z0), (cx-.3, wall+sign*reach, z0), (cx-.3, wall, z0-min(7.0, reach*1.1))],
                         (.6, 0, 0))
        gussets.build(kit, f'{p} sponson gussets', kit.armour)


def turret(kit, s):
    x, y = s['location']
    p = s['prefix']
    gun = s['gun']
    gun_tub(kit, s)
    deck = TUBS[s['id']]
    base = deck+BARBETTE_H
    ANCHOR_Z[s['id']] = base
    kit.empty(f'{p} pivot', (x, y, base), s['angle'])
    fixed, turning = [], []

    # --- Fixed: barbette, bolted base ring, traverse race, feed trunk. ---
    fixed.append(kit.cylinder(f'{p} barbette', (x, y, (deck-.6+base)/2), BARBETTE_R, base-deck+.6,
                              kit.steel, vertices=16))
    fixed.append(kit.cylinder(f'{p} base ring', (x, y, deck+.3), FLANGE_R, 1.2, kit.steel, vertices=24, bevel=.12))
    fixed.append(_studs(kit, f'{p} base ring bolts', _ring(x, y, FLANGE_R-.45, 20, deck+.9, .1), .22, .25, kit.dark))
    fixed.append(kit.cylinder(f'{p} barbette armour belt', (x, y, base-.55), BARBETTE_R+.18, .5, kit.steel,
                              vertices=16, bevel=.06))
    fixed.append(_studs(kit, f'{p} barbette rivets', _ring(x, y, BARBETTE_R+.1, 16, base-1.45, math.pi/16),
                        .14, .18, kit.dark))
    fixed.append(kit.cylinder(f'{p} traverse race', (x, y, base+GAP/2), BARBETTE_R-.7, GAP+.1, kit.dark,
                              vertices=24, bevel=0))

    # --- Turning: gun house and everything on it. ---
    f0 = base+GAP
    roof = f0+HOUSE_H
    axis = f0+AXIS_H
    bottom = [(-4.6, -2.8), (-3.8, -3.6), (2.6, -3.6), (4.0, -2.2), (4.0, 2.2), (2.6, 3.6), (-3.8, 3.6), (-4.6, 2.8)]
    top = [(-4.3, -2.3), (-3.5, -2.9), (1.4, -2.9), (2.4, -1.7), (2.4, 1.7), (1.4, 2.9), (-3.5, 2.9), (-4.3, 2.3)]
    turning.append(_frustum(kit, f'{p} shield', [(x+u, y+v) for u, v in bottom], f0,
                            [(x+u, y+v) for u, v in top], roof, kit.pale, bevel=.18))
    turning.append(_frustum(kit, f'{p} shield skirt', [(x+u*1.03, y+v*1.03) for u, v in bottom], f0-.05,
                            [(x+u*1.03, y+v*1.03) for u, v in bottom], f0+.55, kit.steel, bevel=.08))
    turning.append(_studs(kit, f'{p} shield roof rivets',
                          [(x+u*.9, y+v*.9, roof-.02) for u, v in top], .13, .14, kit.dark))
    turning.append(kit.box(f'{p} shield roof weld', (x-.9, y, roof+.02), (.22, 5.6, .1), kit.steel, bevel=0))
    # Pirate cheek armour: a slab welded over each side of the house, proud of it, bead along its edge.
    for side, sv in (('left', 1), ('right', -1)):
        turning.append(kit.mesh(f'{p} {side} cheek armour',
                                [(x-3.4, y+sv*3.65, f0+.4), (x+2.4, y+sv*3.65, f0+.4), (x+1.6, y+sv*3.15, roof-.35),
                                 (x-3.4, y+sv*3.15, roof-.35), (x-3.4, y+sv*4.15, f0+.4), (x+2.4, y+sv*4.15, f0+.4),
                                 (x+1.6, y+sv*3.65, roof-.35), (x-3.4, y+sv*3.65, roof-.35)],
                                [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)],
                                kit.armour, .05))
        turning.append(kit.box(f'{p} {side} cheek weld', (x-.5, y+sv*3.95, f0+.4), (5.6, .25, .2), kit.weld, bevel=0))
    turning.append(kit.box(f'{p} ammunition bustle', (x-5.0, y, f0+1.3), (1.6, 4.6, 2.2), kit.pale, bevel=.2))
    hb = [(-1.4, 1.1), (.6, 1.1), (1.2, 1.5), (1.2, 2.5), (.6, 2.7), (-1.4, 2.7)]
    ht = [(-1.3, 1.25), (.3, 1.25), (.7, 1.55), (.7, 2.4), (.3, 2.55), (-1.3, 2.55)]
    turning.append(_frustum(kit, f'{p} sight hood', [(x+u, y+v) for u, v in hb], roof-.1,
                            [(x+u, y+v) for u, v in ht], roof+.85, kit.steel, bevel=.08))
    turning.append(kit.box(f'{p} sight slit', (x+1.0, y+2.0, roof+.45), (.25, .85, .26), kit.dark, bevel=0))
    turning.append(kit.cylinder(f'{p} periscope', (x-.6, y+1.9, roof+1.15), .3, .7, kit.steel, vertices=8, bevel=.04))
    turning.append(kit.box(f'{p} periscope head', (x-.45, y+1.9, roof+1.55), (.8, .5, .35), kit.steel, bevel=.06))
    turning.append(kit.cylinder(f'{p} access hatch', (x-2.4, y-1.0, roof+.1), 1.0, .25, kit.steel, vertices=12, bevel=.05))
    turning.append(kit.cylinder(f'{p} access hatch hinge', (x-3.45, y-1.0, roof+.15), .16, 1.3, kit.dark,
                                axis='Y', vertices=8, bevel=0))
    turning.append(kit.box(f'{p} access hatch handle', (x-1.9, y-1.0, roof+.32), (.18, .9, .18), kit.dark, bevel=0))
    # Searchlight on the roof's right rear corner: pirates board by eye.
    turning.append(kit.cylinder(f'{p} searchlight', (x-1.6, y-2.0, roof+.75), .55, 1.1, kit.steel, axis='X',
                                vertices=10, bevel=.04))
    turning.append(kit.cylinder(f'{p} searchlight lens', (x-1.0, y-2.0, roof+.75), .42, .08, kit.lamp, axis='X',
                                vertices=10, bevel=0))
    turning.append(kit.box(f'{p} searchlight yoke', (x-1.6, y-2.0, roof+.18), (.5, .3, .4), kit.dark, bevel=0))
    turning.append(kit.cylinder(f'{p} mantlet', (x+3.5, y, axis), 1.35, 4.4, kit.steel, axis='Y', vertices=14, bevel=.1))
    turning.append(kit.cylinder(f'{p} trunnions', (x+3.5, y, axis), .55, 5.4, kit.dark, axis='Y', vertices=10, bevel=.04))
    turning.append(_studs(kit, f'{p} mantlet bolts',
                          [(x+3.5+1.3*math.cos(t), y+d, axis+1.3*math.sin(t)) for d in (-1.95, 1.95)
                           for t in (-.7, 0, .7)], .13, .15, kit.dark, axis='X'))
    tips = GUNS[gun](kit, p, x, y, axis, turning)
    for o in turning:
        o['traverse'] = True
    for o in fixed:
        o['traverse'] = False
    _turn(fixed+turning, x, y, s['angle'])
    a = math.radians(s['angle'])
    rot = lambda dx, dy: (x+dx*math.cos(a)-dy*math.sin(a), y+dx*math.sin(a)+dy*math.cos(a), axis)
    names = {1: ['muzzle'], 2: ['muzzle port', 'muzzle starboard']}[len(tips)]
    MUZZLES[s['id']] = {}
    for n, (dx, dy) in zip(names, tips):
        tip = rot(dx, dy)
        kit.empty(f'{p} {n}', tip, s['angle'])
        MUZZLES[s['id']][n] = tuple(round(v, 3) for v in tip)


def _twin(kit, p, x, y, axis, turning):
    """The Colossus's twin light autocannon: braked barrels in recoil sleeves."""
    for k, dy in enumerate((-BARREL_DY, BARREL_DY), 1):
        by = y+dy
        turning.append(kit.cylinder(f'{p} barrel {k}', (x+(4.6+MUZZLE_X)/2, by, axis), .3, MUZZLE_X-4.6,
                                    kit.steel, axis='X', vertices=10, bevel=.03))
        turning.append(kit.cylinder(f'{p} barrel {k} recoil sleeve', (x+5.7, by, axis), .52, 2.4,
                                    kit.steel, axis='X', vertices=12, bevel=.06))
        turning.append(kit.cylinder(f'{p} barrel {k} sleeve collar', (x+6.95, by, axis), .42, .3,
                                    kit.dark, axis='X', vertices=12, bevel=0))
        turning.append(kit.cylinder(f'{p} barrel {k} muzzle brake', (x+MUZZLE_X-.5, by, axis), .46, 1.0,
                                    kit.steel, axis='X', vertices=8, bevel=.05))
        for j, bx in enumerate((-.75, -.35), 1):
            turning.append(kit.box(f'{p} barrel {k} brake port {j}', (x+MUZZLE_X+bx, by, axis), (.2, 1.0, .34),
                                   kit.dark, bevel=0))
        turning.append(kit.cylinder(f'{p} barrel {k} bore', (x+MUZZLE_X-.15, by, axis), .19, .34,
                                    kit.dark, axis='X', vertices=8, bevel=0))
    # The twin's two muzzles: port is the barrel left of the line of fire (+local y).
    return [(MUZZLE_X, BARREL_DY), (MUZZLE_X, -BARREL_DY)]


def _rotary(kit, p, x, y, axis, turning):
    """A light rotary chaingun (the close-in guns at the bow): six barrels round a spindle,
    two clamp rings, a shroud at the breech and a drum magazine on the house's left cheek."""
    L = MUZZLE_X-.8
    turning.append(kit.cylinder(f'{p} rotor housing', (x+5.6, y, axis), .95, 2.4, kit.steel, axis='X',
                                vertices=14, bevel=.06))
    for j in range(6):
        a = 2*math.pi*j/6
        by, bz = y+.5*math.cos(a), axis+.5*math.sin(a)
        turning.append(kit.cylinder(f'{p} barrel {j+1}', (x+(6.8+L)/2, by, bz), .14, L-6.8, kit.steel, axis='X',
                                    vertices=8, bevel=0))
    for cx in (x+8.6, x+L-.5):
        turning.append(kit.cylinder(f'{p} barrel clamp {1 if cx < x+9 else 2}', (cx, y, axis), .8, .35, kit.steel,
                                    axis='X', vertices=14, bevel=.03))
    turning.append(kit.cylinder(f'{p} spindle', (x+(6.8+L)/2, y, axis), .2, L-6.8, kit.dark, axis='X',
                                vertices=8, bevel=0))
    turning.append(kit.cylinder(f'{p} drum magazine', (x+.4, y+4.6, axis), 1.35, 1.0, kit.pale, axis='Y',
                                vertices=16, bevel=.06))
    turning.append(kit.box(f'{p} feed chute', (x+2.6, y+3.7, axis+.2), (3.0, .7, .5), kit.dark, bevel=.05))
    return [(L, 0.0)]


def _cannon(kit, p, x, y, axis, turning):
    """A single long light cannon (the broadside guns): a thicker barrel with a fume extractor,
    a recoil cylinder over it and a big two-baffle brake."""
    L = MUZZLE_X+2.6
    turning.append(kit.cylinder(f'{p} barrel', (x+(4.6+L)/2, y, axis), .42, L-4.6, kit.steel, axis='X',
                                vertices=12, bevel=.03))
    turning.append(kit.cylinder(f'{p} barrel recoil sleeve', (x+6.0, y, axis), .78, 3.0, kit.steel, axis='X',
                                vertices=14, bevel=.06))
    turning.append(kit.cylinder(f'{p} fume extractor', (x+10.6, y, axis), .62, 2.2, kit.steel, axis='X',
                                vertices=14, bevel=.12))
    turning.append(kit.cylinder(f'{p} recoil cylinder', (x+6.6, y, axis+1.0), .3, 4.2, kit.dark, axis='X',
                                vertices=10, bevel=.02))
    turning.append(kit.cylinder(f'{p} muzzle brake', (x+L-.7, y, axis), .66, 1.4, kit.steel, axis='X',
                                vertices=8, bevel=.05))
    for j, bx in enumerate((-1.05, -.45), 1):
        turning.append(kit.box(f'{p} brake port {j}', (x+L+bx, y, axis), (.3, 1.4, .5), kit.dark, bevel=0))
    turning.append(kit.cylinder(f'{p} bore', (x+L-.12, y, axis), .26, .3, kit.dark, axis='X', vertices=10, bevel=0))
    return [(L, 0.0)]


GUNS = {'twin': _twin, 'rotary': _rotary, 'cannon': _cannon}


# Launcher footprint (local, x along the slot angle): housing half-length / half-width.
L_HX, L_HY = 2.5, 1.9


def _seat(kit, x, y, angle):
    """Set the launcher back from the slot along -angle until its footprint lies on one surface facet.

    Returns (centre, normal, setback): the footprint centre on the fitted plane and its normal."""
    a = math.radians(angle)
    d = Vector((math.cos(a), math.sin(a), 0))
    side = Vector((-d.y, d.x, 0))
    best = None
    for k in range(26):
        back = .2*k
        c = Vector((x, y, 0))-d*back
        pts = []
        for u in (-L_HX-.4, 0, L_HX+.4):
            for v in (-L_HY-.4, 0, L_HY+.4):
                q = c+d*u+side*v
                h = kit.surface(q.x, q.y)
                pts.append(h[0] if h else None)
        if any(p is None for p in pts):
            continue
        n = _plane_normal(pts)
        dev = max(abs((p-pts[4]).dot(n)) for p in pts)
        if best is None or dev < best[0]-1e-6:
            best = (dev, pts[4], n, back)
        if dev < .15:
            break
    n = best[2]
    if n.z > .995:  # a flat deck: sit square on it rather than on the fit's residual tilt
        n = Vector((0, 0, 1))
    return best[1], n, best[3]


def _plane_normal(pts):
    """Least-squares plane normal (pointing up) through the sample points."""
    cx = sum(p.x for p in pts)/len(pts)
    cy = sum(p.y for p in pts)/len(pts)
    cz = sum(p.z for p in pts)/len(pts)
    sxx = sum((p.x-cx)**2 for p in pts)
    syy = sum((p.y-cy)**2 for p in pts)
    sxy = sum((p.x-cx)*(p.y-cy) for p in pts)
    sxz = sum((p.x-cx)*(p.z-cz) for p in pts)
    syz = sum((p.y-cy)*(p.z-cz) for p in pts)
    det = sxx*syy-sxy*sxy
    b = (sxz*syy-syz*sxy)/det
    c = (syz*sxx-sxz*sxy)/det
    return Vector((-b, -c, 1)).normalized()


def launcher(kit, s):
    x, y = s['location']
    p = s['prefix']
    centre, n, back = _seat(kit, x, y, s['angle'])
    a = math.radians(s['angle'])
    d = Vector((math.cos(a), math.sin(a), 0))
    fx = (d-n*d.dot(n)).normalized()
    fy = n.cross(fx)
    basis = Matrix((fx, fy, n)).transposed().to_4x4()
    m = Matrix.Translation(centre) @ basis
    # Local frame: x along the slot angle, z along the surface normal, origin on the surface.
    parts = []
    parts.append(_frustum(kit, f'{p} plinth', [(-L_HX-.3, -L_HY-.3), (L_HX+.3, -L_HY-.3), (L_HX+.3, L_HY+.3),
                                                (-L_HX-.3, L_HY+.3)], -1.4,
                          [(-L_HX-.3, -L_HY-.3), (L_HX+.3, -L_HY-.3), (L_HX+.3, L_HY+.3), (-L_HX-.3, L_HY+.3)],
                          .15, kit.maroon, bevel=.06))
    parts.append(kit.box(f'{p} base flange', (0, 0, .2), (2*L_HX+1.3, 2*L_HY+1.3, .3), kit.steel, bevel=.08))
    parts.append(_studs(kit, f'{p} base flange bolts',
                        [(u, v, .35) for u in (-L_HX-.3, 0, L_HX+.3) for v in (-L_HY-.3, L_HY+.3)]
                        + [(u, 0, .35) for u in (-L_HX-.3, L_HX+.3)], .15, .14, kit.dark))
    hb = [(-L_HX, -L_HY), (L_HX, -L_HY), (L_HX, L_HY), (-L_HX, L_HY)]
    ht = [(-L_HX+.3, -L_HY+.3), (L_HX-.1, -L_HY+.3), (L_HX-.1, L_HY-.3), (-L_HX+.3, L_HY-.3)]
    parts.append(_frustum(kit, f'{p} housing', hb, .3, ht, 1.75, kit.steel, bevel=.1))
    # Hinged armoured lid: a slightly oversize plate, hinge at the rear, ram on one side.
    parts.append(_frustum(kit, f'{p} armoured lid', [(-L_HX+.15, -L_HY+.15), (L_HX+.15, -L_HY+.15),
                                                      (L_HX+.15, L_HY-.15), (-L_HX+.15, L_HY-.15)], 1.7,
                          [(-L_HX+.35, -L_HY+.35), (L_HX-.05, -L_HY+.35), (L_HX-.05, L_HY-.35),
                           (-L_HX+.35, L_HY-.35)], 2.0, kit.maroon, bevel=.06))
    parts.append(kit.cylinder(f'{p} lid hinge', (-L_HX+.2, 0, 1.8), .2, 2*L_HY-.6, kit.dark, axis='Y',
                              vertices=8, bevel=0))
    parts.append(kit.cylinder(f'{p} lid ram', (-.4, L_HY+.12, 1.15), .16, 2.6, kit.dark, axis='X',
                              vertices=8, bevel=0))
    parts.append(_studs(kit, f'{p} lid rivets', [(u, v, 1.98) for u in (-1.6, 0, 1.6) for v in (-1.05, 1.05)],
                        .1, .1, kit.dark))
    # Muzzle plate and the rack of six flare tubes, three over three, angled up a little.
    parts.append(kit.box(f'{p} muzzle plate', (L_HX+.12, 0, 1.0), (.24, 2*L_HY-.3, 1.3), kit.steel, bevel=.05))
    k = 0
    for row, tz in enumerate((.68, 1.32)):
        for ty in (-1.0, 0, 1.0):
            k += 1
            name = f'{p} tube {k}'
            parts.append(kit.cylinder(name, (L_HX+.2, ty, tz), .27, .5, kit.steel, axis='X', vertices=10, bevel=.03))
            parts.append(kit.cylinder(f'{name} bore', (L_HX+.3, ty, tz), .18, .5, kit.dark, axis='X',
                                      vertices=10, bevel=0))
    _place(parts, m)
    pivot_loc = Vector((x, y, 0))
    hit = kit.surface(x, y)
    pivot_loc.z = hit[0].z if hit else centre.z
    ANCHOR_Z[s['id']] = pivot_loc.z
    tip = m @ Vector((L_HX+.45, 0, 1.0))
    piv = kit.empty(f'{p} pivot', pivot_loc, s['angle'])
    mz = kit.empty(f'{p} muzzle', tip, s['angle'])
    for e in (piv, mz):
        e.rotation_euler = basis.to_euler()
    MUZZLES[s['id']] = {'muzzle': tuple(round(v, 3) for v in tip)}


def _tree(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    tree = BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices], [tuple(q.vertices) for q in me.polygons])
    ev.to_mesh_clear()
    return tree


def clash_report():
    """BVH overlap of every turret's turning parts against every mesh outside that turret, at rest."""
    bpy.context.view_layer.update()
    meshes = [o for o in bpy.data.objects if o.type == 'MESH']
    report = {}
    for s in SLOTS:
        if s['mount'] != 'TURRET':
            continue
        p = s['prefix']
        mine = [o for o in meshes if o.name.startswith(p+' ') and o.get('traverse')]
        others = [o for o in meshes if not o.name.startswith(p+' ')]
        trees = {o.name: _tree(o) for o in others}
        hits = set()
        for o in mine:
            t = _tree(o)
            for n, other in trees.items():
                if t.overlap(other):
                    hits.add((o.name, n))
        report[s['id']] = sorted(hits)
        print('MOUNT CLASH', s['id'], 'clear' if not hits else sorted(hits))
    return report


def build(kit):
    ANCHOR_Z.clear()
    MUZZLES.clear()
    TUBS.clear()
    # Guns first: the bow launchers seat on the corner sponsons' rims.
    for s in [s for s in SLOTS if s['mount'] == 'TURRET']+[s for s in SLOTS if s['mount'] != 'TURRET']:
        (turret if s['mount'] == 'TURRET' else launcher)(kit, s)
    clash_report()
