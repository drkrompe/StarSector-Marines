"""Colossus weapon mounts: four small ballistic turrets and four hidden flare launchers.

Slots exactly as colossus.ship lists them. The turrets stand on the side cargo
modules, each at the round turret ring the sprite draws there (about 12 px
across: a dark well inside a pale ring), and rest at their slot angles
(broadside, port and starboard). WS 005-008 are HIDDEN SYSTEM slots at the four
corners: the ship system is flarelauncher, so each is a low armoured launcher
box laid along its slot angle and seated on whatever surface is there.

Turret (about 0.5 m per unit, so a 5.5 m barbette): a fixed armoured barbette
with a bolted base ring seated on the deck, an ammunition feed trunk running
into it from the deck on the inboard side, a dark traverse race, and above that
gap the turning parts: a faceted gun house with a sight hood and periscope, a
roof access hatch, a rear ammunition bustle, a rotor mantlet on trunnions and a
twin light autocannon (barrels, recoil sleeves, slotted muzzle brakes).
Turning parts carry the custom property traverse = True.

Launcher: a bolted base flange and plinth that follow the surface, an armoured
housing with a hinged lid and a lid ram, and a muzzle plate with a rack of six
flare tubes, tilted to the surface normal (WS 007/008 lie on the drive pods'
sloping outboard shoulders).

Names: 'Turret <i> WS 00n ...' and 'Launcher <i> WS 00n ...', with a
'<prefix> pivot' empty on the traverse axis and a '<prefix> muzzle' empty at
the barrel tip (+X along the rest angle). Only parts above the traverse gap
turn: T(x,y) @ Rz(angle) @ T(-x,-y).
"""
import math
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

SLOTS = [
    {'id': 'WS 001', 'location': (69, 75), 'angle': 80, 'arc': 140, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 002', 'location': (3, 75), 'angle': 90, 'arc': 130, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (70, -74), 'angle': -80, 'arc': 140, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 004', 'location': (4, -73), 'angle': -90, 'arc': 130, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 005', 'location': (87, 83), 'angle': 45, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 006', 'location': (86, -83), 'angle': -45, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 007', 'location': (-83, 83), 'angle': 100, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 008', 'location': (-84, -81), 'angle': 260, 'arc': 20, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
]
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

# Turret dimensions (units): barbette radius, base flange radius, barbette
# height above the deck, traverse gap, gun-house height, gun axis above the
# house floor, muzzle distance from the traverse axis.
BARBETTE_R, FLANGE_R, BARBETTE_H, GAP = 5.4, 6.3, 2.0, .25
HOUSE_H, AXIS_H, MUZZLE_X = 3.4, 1.6, 12.2
BARREL_DY = 1.1


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


def turret(kit, s):
    x, y = s['location']
    p = s['prefix']
    # Seat on the deck: the lowest hit under the base flange, so no edge floats.
    hits = [kit.surface(px, py) for px, py, _ in _ring(x, y, FLANGE_R, 12, 0)]+[kit.surface(x, y)]
    deck = min(h[0].z for h in hits if h)
    base = deck+BARBETTE_H
    ANCHOR_Z[s['id']] = base
    kit.empty(f'{p} pivot', (x, y, base), s['angle'])
    fixed, turning = [], []

    # --- Fixed: barbette, bolted base ring, traverse race, feed trunk. ---
    fixed.append(kit.cylinder(f'{p} barbette', (x, y, (deck-.6+base)/2), BARBETTE_R, base-deck+.6,
                              kit.steel, vertices=16))
    fixed.append(kit.cylinder(f'{p} base ring', (x, y, deck+.3), FLANGE_R, 1.2, kit.steel, vertices=24, bevel=.12))
    fixed.append(_studs(kit, f'{p} base ring bolts', _ring(x, y, FLANGE_R-.45, 20, deck+.9, .1), .22, .25, kit.dark))
    # Barbette armour bands: a raised belt with rivets just under the race.
    fixed.append(kit.cylinder(f'{p} barbette armour belt', (x, y, base-.55), BARBETTE_R+.18, .5, kit.steel,
                              vertices=16, bevel=.06))
    fixed.append(_studs(kit, f'{p} barbette rivets', _ring(x, y, BARBETTE_R+.1, 16, base-1.45, math.pi/16),
                        .14, .18, kit.dark))
    fixed.append(kit.cylinder(f'{p} traverse race', (x, y, base+GAP/2), BARBETTE_R-.7, GAP+.1, kit.dark,
                              vertices=24, bevel=0))
    # Ammunition feed trunk: a duct off the deck on the inboard side, elbowing into the barbette.
    tz = deck+.75
    fixed.append(kit.box(f'{p} ammunition feed trunk', (x-7.6, y, tz), (4.6, 1.8, 1.5), kit.steel, bevel=.18))
    fixed.append(kit.box(f'{p} feed trunk deck gland', (x-9.9, y, deck+.2), (1.6, 3.0, .7), kit.steel, bevel=.15))
    fixed.append(_studs(kit, f'{p} feed trunk flange bolts',
                        [(x-9.9+dx, y+dy, deck+.55) for dx in (-.55, .55) for dy in (-1.2, 1.2)], .16, .18, kit.dark))
    fixed.append(kit.box(f'{p} feed trunk collar', (x-5.4, y, tz), (.6, 2.3, 2.0), kit.steel, bevel=.1))

    # --- Turning: gun house and everything on it. ---
    f0 = base+GAP          # house floor
    roof = f0+HOUSE_H
    axis = f0+AXIS_H
    bottom = [(-4.6, -2.8), (-3.8, -3.6), (2.6, -3.6), (4.0, -2.2), (4.0, 2.2), (2.6, 3.6), (-3.8, 3.6), (-4.6, 2.8)]
    top = [(-4.3, -2.3), (-3.5, -2.9), (1.4, -2.9), (2.4, -1.7), (2.4, 1.7), (1.4, 2.9), (-3.5, 2.9), (-4.3, 2.3)]
    house = _frustum(kit, f'{p} shield', [(x+u, y+v) for u, v in bottom], f0,
                     [(x+u, y+v) for u, v in top], roof, kit.pale, bevel=.18)
    turning.append(house)
    # Skirt: a darker lower plate course, the seam between it and the shield reads as a weld line.
    turning.append(_frustum(kit, f'{p} shield skirt', [(x+u*1.03, y+v*1.03) for u, v in bottom], f0-.05,
                            [(x+u*1.03, y+v*1.03) for u, v in bottom], f0+.55, kit.steel, bevel=.08))
    turning.append(_studs(kit, f'{p} shield roof rivets',
                          [(x+u*.9, y+v*.9, roof-.02) for u, v in top], .13, .14, kit.dark))
    # Roof plate joint: a shallow weld bead across the roof between the fore and aft plates.
    turning.append(kit.box(f'{p} shield roof weld', (x-.9, y, roof+.02), (.22, 5.6, .1), kit.steel, bevel=0))
    # Side vision blocks, one on each cheek, with dark slits.
    for side, sv in (('left', 1), ('right', -1)):
        turning.append(kit.box(f'{p} {side} vision block', (x+.2, y+sv*3.35, f0+2.3), (1.4, .5, .7), kit.steel, bevel=.06))
        turning.append(kit.box(f'{p} {side} vision slit', (x+.2, y+sv*3.6, f0+2.35), (.9, .06, .16), kit.dark, bevel=0))
    # Rear ammunition bustle.
    turning.append(kit.box(f'{p} ammunition bustle', (x-5.0, y, f0+1.3), (1.6, 4.6, 2.2), kit.pale, bevel=.2))
    # Sight hood on the left roof, periscope head on it, dark vision slit forward.
    hb = [(-1.4, 1.1), (.6, 1.1), (1.2, 1.5), (1.2, 2.5), (.6, 2.7), (-1.4, 2.7)]
    ht = [(-1.3, 1.25), (.3, 1.25), (.7, 1.55), (.7, 2.4), (.3, 2.55), (-1.3, 2.55)]
    turning.append(_frustum(kit, f'{p} sight hood', [(x+u, y+v) for u, v in hb], roof-.1,
                            [(x+u, y+v) for u, v in ht], roof+.85, kit.steel, bevel=.08))
    turning.append(kit.box(f'{p} sight slit', (x+1.0, y+2.0, roof+.45), (.25, .85, .26), kit.dark, bevel=0))
    turning.append(kit.cylinder(f'{p} periscope', (x-.6, y+1.9, roof+1.15), .3, .7, kit.steel, vertices=8, bevel=.04))
    turning.append(kit.box(f'{p} periscope head', (x-.45, y+1.9, roof+1.55), (.8, .5, .35), kit.steel, bevel=.06))
    # Roof access hatch, hinged at its rear edge, with a grab handle.
    turning.append(kit.cylinder(f'{p} access hatch', (x-2.4, y-1.0, roof+.1), 1.0, .25, kit.steel, vertices=12, bevel=.05))
    turning.append(kit.cylinder(f'{p} access hatch hinge', (x-3.45, y-1.0, roof+.15), .16, 1.3, kit.dark,
                                axis='Y', vertices=8, bevel=0))
    turning.append(kit.box(f'{p} access hatch handle', (x-1.9, y-1.0, roof+.32), (.18, .9, .18), kit.dark, bevel=0))
    # Rotor mantlet on its trunnions across the front face.
    turning.append(kit.cylinder(f'{p} mantlet', (x+3.5, y, axis), 1.35, 4.4, kit.steel, axis='Y', vertices=14, bevel=.1))
    turning.append(kit.cylinder(f'{p} trunnions', (x+3.5, y, axis), .55, 5.4, kit.dark, axis='Y', vertices=10, bevel=.04))
    turning.append(_studs(kit, f'{p} mantlet bolts',
                          [(x+3.5+1.3*math.cos(t), y+d, axis+1.3*math.sin(t)) for d in (-1.95, 1.95)
                           for t in (-.7, 0, .7)], .13, .15, kit.dark, axis='X'))
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
        # Brake ports: dark slots through the brake's sides.
        for j, bx in enumerate((-.75, -.35), 1):
            turning.append(kit.box(f'{p} barrel {k} brake port {j}', (x+MUZZLE_X+bx, by, axis), (.2, 1.0, .34),
                                   kit.dark, bevel=0))
        turning.append(kit.cylinder(f'{p} barrel {k} bore', (x+MUZZLE_X-.15, by, axis), .19, .34,
                                    kit.dark, axis='X', vertices=8, bevel=0))
    for o in turning:
        o['traverse'] = True
    for o in fixed:
        o['traverse'] = False
    _turn(fixed+turning, x, y, s['angle'])
    a = math.radians(s['angle'])
    tip = (x+MUZZLE_X*math.cos(a), y+MUZZLE_X*math.sin(a), axis)
    kit.empty(f'{p} muzzle', tip, s['angle'])
    MUZZLES[s['id']] = tuple(round(v, 3) for v in tip)


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
    MUZZLES[s['id']] = tuple(round(v, 3) for v in tip)


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
    for s in SLOTS:
        (turret if s['mount'] == 'TURRET' else launcher)(kit, s)
    clash_report()
