"""Tarsus weapon mounts: four small ballistic turrets at tarsus.ship's slots.

WS 001 (-41, 37) 135 and WS 002 (-41, -37) 225 stand on the aft drums;
WS 003 (101, 21) 45 and WS 004 (101, -20) -45 on the arms' bow drums. Each
rests at its slot angle: only the parts above the traverse gap are rotated,
T(x,y) @ Rz(angle) @ T(-x,-y), about the slot centre. The barbette seats
itself on the surface below it.

Pass 2 (about 0.51 m per unit, so a 3.7 m barbette): fixed below the gap, an
armoured barbette with a bolted base ring and an armour belt, and an
ammunition trunk running in along the deck behind it. Turning above the gap
(custom property traverse = True): a faceted gun house with a skirt course,
roof weld bead and rivets, vision slits, a sight block and a roof hatch; a
rear ammunition bustle with a belt feed chute along the left cheek; a rotor
mantlet on trunnions; and a twin light autocannon (recoil sleeves, barrels,
slotted muzzle brakes, dark bores). The muzzle empty sits at the barrel tips.

Names: 'Turret <i> WS 00n ...' with '<prefix> pivot' and '<prefix> muzzle'."""
import math
import bpy
from mathutils.bvhtree import BVHTree
from ts_kit import Parts

SLOTS = [
    dict(id='WS 001', location=(-41, 37), angle=135, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
    dict(id='WS 002', location=(-41, -37), angle=225, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
    dict(id='WS 003', location=(101, 21), angle=45, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
    dict(id='WS 004', location=(101, -20), angle=-45, arc=250, mount='TURRET', size='SMALL', type='BALLISTIC'),
]
for i, s in enumerate(SLOTS, 1):
    s['prefix'] = f"Turret {i} {s['id']}"
ANCHOR_Z = {}
MUZZLES = {}

RING_R, BARBETTE_H = 3.6, 1.4
GAP = .2                 # traverse gap above the barbette
AXIS_H = 1.6             # gun axis above the barbette top
MUZZLE_X = 9.4           # barrel tips from the traverse axis
BARREL_DY = .75


def _rot(p, cx, cy, a):
    x, y, z = p
    c, s = math.cos(a), math.sin(a)
    dx, dy = x-cx, y-cy
    return (cx+c*dx-s*dy, cy+s*dx+c*dy, z)


def _turned(parts, cx, cy, a):
    parts.v = [_rot(p, cx, cy, a) for p in parts.v]
    return parts


def _frustum(parts, bottom, z0, top, z1, cx, cy):
    """Faceted solid between two equal-count plan rings (local XY about cx, cy)."""
    n = len(bottom)
    verts = [(cx+u, cy+v, z0) for u, v in bottom]+[(cx+u, cy+v, z1) for u, v in top]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return parts._add(verts, faces)


def _studs_ring(parts, cx, cy, r, count, z, size=.16, h=.16, phase=0.0):
    for k in range(count):
        t = phase+2*math.pi*k/count
        parts.cyl((cx+r*math.cos(t), cy+r*math.sin(t), z), size, h, 'Z', 6)


def _out(kit, parts, name, mat, cx, cy, a, bevel=0, turning=True):
    ob = _turned(parts, cx, cy, a).build(kit, name, mat, bevel)
    ob['traverse'] = turning
    return ob


def turret(kit, s):
    cx, cy = s['location']
    p, _ = kit.surface(cx, cy)
    z0 = p.z
    pre = s['prefix']
    a = math.radians(s['angle'])
    zt = z0+BARBETTE_H
    f0 = zt+GAP                        # gun house floor
    roof = f0+2.3
    ax = zt+AXIS_H                     # gun axis

    # --- Fixed: barbette, base ring and bolts, armour belt, ammunition trunk. ---
    base = Parts().cyl((cx, cy, z0-.6), RING_R, BARBETTE_H+.6, 'Z', 32)
    _out(kit, base, f'{pre} barbette', kit.steel, cx, cy, a, .1, False)
    ring = Parts().cyl((cx, cy, z0-.2), RING_R+.7, .6, 'Z', 32)
    ring.cyl((cx, cy, zt-.6), RING_R+.15, .4, 'Z', 32)                     # armour belt
    ring.box(-6.6+cx, -3.3+cx, cy-.75, cy+.75, z0-.2, z0+1.0)              # ammunition trunk, aft
    ring.box(-7.5+cx, -6.3+cx, cy-1.2, cy+1.2, z0-.2, z0+.35)              # trunk deck gland
    ring.box(-3.6+cx, -3.2+cx, cy-1.0, cy+1.0, z0-.1, z0+1.25)             # trunk collar at the barbette
    _out(kit, ring, f'{pre} base ring', kit.grey, cx, cy, a, .06, False)
    bolts = Parts()
    _studs_ring(bolts, cx, cy, RING_R+.4, 20, z0+.4, .16, .14)
    _studs_ring(bolts, cx, cy, RING_R+.17, 16, zt-.45, .12, .2, math.pi/16)
    for u in (-7.2, -6.6):
        for v in (-.9, .9):
            bolts.cyl((cx+u, cy+v, z0+.35), .14, .12, 'Z', 6)
    _out(kit, bolts, f'{pre} base ring bolts', kit.dark, cx, cy, a, 0, False)

    # --- Turning: race, gun house, bustle, feed, mantlet, guns. ---
    race = Parts().cyl((cx, cy, zt-.05), RING_R-.35, GAP+.15, 'Z', 32)
    _out(kit, race, f'{pre} traverse race', kit.dark, cx, cy, a)
    bottom = [(-2.9, -2.0), (-2.4, -2.5), (1.4, -2.5), (2.4, -1.6), (2.4, 1.6), (1.4, 2.5), (-2.4, 2.5), (-2.9, 2.0)]
    top = [(-2.7, -1.6), (-2.2, -2.0), (.7, -2.0), (1.6, -1.2), (1.6, 1.2), (.7, 2.0), (-2.2, 2.0), (-2.7, 1.6)]
    house = _frustum(Parts(), bottom, f0+.5, top, roof, cx, cy)
    _out(kit, house, f'{pre} gun house', kit.grey, cx, cy, a, .1)
    skirt = _frustum(Parts(), [(u*1.03, v*1.03) for u, v in bottom], f0, [(u*1.03, v*1.03) for u, v in bottom],
                     f0+.55, cx, cy)
    skirt.box(cx-3.75, cx-2.75, cy-1.7, cy+1.7, f0+.15, f0+1.9)                     # ammunition bustle
    skirt.box(cx-1.1, cx-.85, cy-1.9, cy+1.9, roof-.02, roof+.08)                   # roof weld bead
    # Sight block on the right of the roof, its dark slit forward; a roof hatch on the left.
    skirt.box(cx-.6, cx+.9, cy-1.65, cy-.55, roof-.05, roof+.75)
    skirt.cyl((cx-1.8, cy+.9, roof-.05), .7, .22, 'Z', 12)
    skirt.cyl((cx-2.5, cy+.9, roof+.2), .12, .95, 'Y', 6)                           # hatch hinge
    # Belt feed chute: from the bustle along the left cheek to the breech, beside the mantlet.
    skirt.beam((cx-3.0, cy+2.05, f0+1.3), (cx+.3, cy+2.35, f0+1.5), .55, .5)
    skirt.beam((cx+.3, cy+2.35, f0+1.5), (cx+1.6, cy+1.85, ax-.2), .55, .5)
    _out(kit, skirt, f'{pre} house fittings', kit.steel, cx, cy, a, .04)
    dark = Parts()
    dark.box(cx+.9, cx+.98, cy-1.45, cy-.75, roof+.3, roof+.55)                     # sight slit
    for e in (1, -1):                                                              # cheek vision slits
        dark.box(cx-.9, cx+.1, cy+e*2.12-.05, cy+e*2.12+.05, roof-.75, roof-.55)
    for u, v in top:
        dark.cyl((cx+u*.88, cy+v*.88, roof-.02), .1, .12, 'Z', 6)                   # roof rivets
    dark.beam((cx-1.4, cy+.4, roof+.25), (cx-1.4, cy+1.3, roof+.25), .1, .1)        # hatch handle
    dark.box(cx-3.0, cx-2.4, cy+1.75, cy+2.35, f0+1.0, f0+1.6)                      # feed throat
    _out(kit, dark, f'{pre} house details', kit.dark, cx, cy, a)

    mant = Parts().cyl((cx+2.55, cy-1.75, ax), .95, 3.5, 'Y', 16)
    _out(kit, mant, f'{pre} mantlet', kit.steel, cx, cy, a, .06)
    trun = Parts().cyl((cx+2.55, cy-2.2, ax), .42, 4.4, 'Y', 10)
    for e in (1, -1):
        for t in (-.6, 0, .6):
            trun.cyl((cx+2.55+.9*math.cos(t), cy+e*1.74, ax+.9*math.sin(t)), .1, -e*.12, 'Y', 6)
    _out(kit, trun, f'{pre} trunnions', kit.dark, cx, cy, a)

    bar, sleeves, bores = Parts(), Parts(), Parts()
    for dy in (-BARREL_DY, BARREL_DY):
        bar.cyl((cx+3.3, cy+dy, ax), .26, MUZZLE_X-3.3-.9, 'X', 12)
        sleeves.cyl((cx+3.2, cy+dy, ax), .42, 1.8, 'X', 12)                         # recoil sleeve
        sleeves.cyl((cx+5.0, cy+dy, ax), .34, .25, 'X', 12)                         # sleeve collar
        sleeves.cyl((cx+MUZZLE_X-.95, cy+dy, ax), .36, .95, 'X', 8)                 # muzzle brake
        for bx in (-.7, -.35):                                                      # brake ports
            bores.box(cx+MUZZLE_X+bx-.09, cx+MUZZLE_X+bx+.09, cy+dy-.38, cy+dy+.38, ax-.14, ax+.14)
        bores.cyl((cx+MUZZLE_X-.1, cy+dy, ax), .17, .14, 'X', 8)
    _out(kit, bar, f'{pre} barrels', kit.steel, cx, cy, a)
    _out(kit, sleeves, f'{pre} recoil sleeves and brakes', kit.steel, cx, cy, a, .03)
    _out(kit, bores, f'{pre} bores and brake ports', kit.dark, cx, cy, a)

    kit.empty(f'{pre} pivot', (cx, cy, zt), s['angle'])
    mz = _rot((cx+MUZZLE_X, cy, ax), cx, cy, a)
    kit.empty(f'{pre} muzzle', mz, s['angle'])
    ANCHOR_Z[s['id']] = zt
    MUZZLES[s['id']] = tuple(round(v, 2) for v in mz)


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
    trees = {o.name: _tree(o) for o in meshes}
    report = {}
    for s in SLOTS:
        p = s['prefix']
        mine = [o.name for o in meshes if o.name.startswith(p+' ') and o.get('traverse')]
        others = [o.name for o in meshes if not o.name.startswith(p+' ')]
        hits = sorted({(m, n) for m in mine for n in others if trees[m].overlap(trees[n])})
        report[s['id']] = hits
        print('MOUNT CLASH', s['id'], 'clear' if not hits else hits)
    return report


def build(kit):
    ANCHOR_Z.clear()
    MUZZLES.clear()
    for s in SLOTS:
        turret(kit, s)
    clash_report()
