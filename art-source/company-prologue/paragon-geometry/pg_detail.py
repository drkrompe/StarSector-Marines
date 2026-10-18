"""Pass 2 detail on the Paragon: machinery insets, dimples, niches, the prow grille, reactor glow and seams.

Everything here is read off paragon.png at 6x (port side; starboard mirrors it)
and has a job:
- machinery insets: recessed dark trays with machinery standing in them, where
  the sprite shows dark gridded machinery between armour (beside the bow module,
  on the bow module's flanks, beside WS 013, the pods' forward-outboard bays);
- dimples: small rectangular vents and square indents pressed into the plate;
- niches: the arched recesses along each pod's aft slope, above the drives;
- the prow grille: fore-aft vanes in a recessed field on top of the prow block;
- reactor glow: the segmented blue-white arc round each reactor well;
- seams: radial plate seams across the gun shelf and the belt, transverse seams
  across the aft body, and a thin glow line along the shelf's riser, the
  Tri-Tachyon seam light;
- stern grilles: dark finned machinery let into each pod's aft face between the
  drive blocks (the sprite's gridded stern);
- deck windows: glazed slits along the command deck's tiers;
- lining ribs: radial machinery ribs on the bore lining between the emitter nodes;
- flank panels: service panels let into the belt's armoured flank;
- gantry: frames and pipe runs on the command deck gantry's flanks.
A cut is made only into the hull objects that reach the feature, and every cut
face takes the plate's own material; floors are dark technical lining.
"""
import math
import random
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree
import pg_kit as K
import pg_hull

# Machinery insets, port side: name, plan (X, Y), depth below the lowest plate point over the plan.
INSETS = [
    ('bow machinery', [(118, 21), (111, 33), (97, 38), (85, 31), (85, 21)], 4.0),
    ('bow module flank', [(132, 8.5), (132, 15.5), (98, 13.5), (98, 8.5)], 2.5),
    ('beam machinery', [(-58, 103), (-58, 112), (-71, 113), (-71, 104)], 3.0),
    ('pod machinery bay', [(-111, 121), (-111, 141), (-127, 146), (-130, 125)], 5.0),
]
# Dimples, port side: centre (X, Y), width (across), length (along), bearing (deg), depth.
DIMPLES = [
    ((30, 112), 2.4, 6.0, -20, 1.6), ((26, 106), 2.4, 6.0, -20, 1.6),
    ((-90, 70), 3.0, 3.0, 45, 1.4), ((-96, 64), 3.0, 3.0, 45, 1.4), ((-84, 76), 3.0, 3.0, 45, 1.4),
    ((60, 118), 2.2, 5.0, 35, 1.4), ((-20, 128), 2.2, 5.0, 95, 1.4), ((-66, 126), 2.2, 5.0, 115, 1.4),
]
# Niches on each pod's aft slope, port side: centre (X, Y), width, length, depth.
NICHES = [((-156, 143), 5.5, 9.0, 5.0), ((-155, 131), 5.5, 9.0, 5.0), ((-157, 120), 5.0, 8.0, 5.0),
          ((-159, 104), 9.0, 10.0, 6.0)]
# Reactor glow: segments on the well rim, from the bow round the outboard side to aft (deg, port side).
REACTOR_GLOW = (-40.0, 145.0, 11)
# Plate seams: bearings (deg about the bore centre) of radial seams across the shelf and belt.
SEAMS = [10, 38, 62, 118, 142, 168, -10, -38, -62, -118, -142, -168]
SEAM_W, SEAM_D = .8, .9
SEED = 1307


def _trees(objs):
    dg = bpy.context.evaluated_depsgraph_get()
    return [(ob, BVHTree.FromObject(ob, dg)) for ob in objs]


def _near(kit, plan, reach=2.0):
    """Hull objects any part of whose surface lies under the plan (sampled)."""
    xs = [p[0] for p in plan]
    ys = [p[1] for p in plan]
    cx, cy = sum(xs)/len(xs), sum(ys)/len(ys)
    pts = list(plan)+[(cx, cy)]
    out = []
    for ob, t in _trees(kit.hull):
        inv = ob.matrix_world.inverted()
        for x, y in pts:
            loc, _, _, _ = t.ray_cast(inv @ Vector((x, y, 200)), Vector((0, 0, -1)))
            if loc is not None:
                out.append(ob)
                break
    return out


def _surface_z(kit, plan):
    zs = []
    xs = [p[0] for p in plan]
    ys = [p[1] for p in plan]
    for x, y in list(plan)+[(sum(xs)/len(xs), sum(ys)/len(ys))]:
        hit = kit.surface(x, y)
        if hit:
            zs.append(hit[0].z)
    return zs


def _prism(kit, name, plan, z0, z1, mat):
    plan = K.ccw(plan)
    n = len(plan)
    verts = [(x, y, z0) for x, y in plan]+[(x, y, z1) for x, y in plan]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, 0, False)


def _cut(kit, targets, plan, floor):
    for ob in targets:
        cut = _prism(kit, 'Temporary detail cutter', plan, floor, 200.0, ob.material_slots[0].material)
        K.boolean(ob, cut)


def _mirror(plan, side):
    return [(x, y*side) for x, y in plan]


def _rect(c, w, l, bearing):
    a = math.radians(bearing)
    ux, uy = math.cos(a), math.sin(a)
    vx, vy = -uy, ux
    cx, cy = c
    return [(cx+ux*l/2*sx+vx*w/2*sy, cy+uy*l/2*sx+vy*w/2*sy) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]


def _inside_outline(plan, margin=3.0):
    """The plan with every point pulled inside the hull outline by margin (insets never break the edge)."""
    out = []
    for x, y in plan:
        lim = pg_hull.half_width(x)-margin
        out.append((x, max(-lim, min(lim, y))))
    return out


def inset(kit, name, plan, depth, rng):
    plan = _inside_outline(plan)
    zs = _surface_z(kit, plan)
    if not zs:
        return
    floor = min(zs)-depth
    _cut(kit, _near(kit, plan), plan, floor)
    tray = _prism(kit, f'Detail {name} tray', plan, floor-1.5, floor+.05, kit.tech)
    kit.detail.append(tray)
    # Machinery standing in the tray: pipe runs along the long axis, pump bodies, junction boxes.
    xs = [p[0] for p in plan]
    ys = [p[1] for p in plan]
    x0, x1, y0, y1 = min(xs)+2, max(xs)-2, min(ys)+2, max(ys)-2
    long_x = (x1-x0) >= (y1-y0)
    span = (x1-x0) if long_x else (y1-y0)
    k = 0
    for j in range(max(1, int(((y1-y0) if long_x else (x1-x0))/2.6))):
        off = (y0 if long_x else x0)+1.3+j*2.6
        r = .55+.25*rng.random()
        if long_x:
            loc, axis = ((x0+x1)/2, off, floor+r+.1), 'X'
        else:
            loc, axis = (off, (y0+y1)/2, floor+r+.1), 'Y'
        if not K.inside([loc[:2]], K.ccw(plan))[0]:
            continue
        p = kit.cylinder(f'Detail {name} pipe {k}', loc, r, span*.9, kit.steel, axis=axis, vertices=10, bevel=0)
        kit.detail.append(p)
        k += 1
    for b in range(int(span/5)):
        t = (b+.5)/int(span/5)
        x = x0+(x1-x0)*(t if long_x else rng.random())
        y = y0+(y1-y0)*(rng.random() if long_x else t)
        if not K.inside([(x, y)], K.ccw(plan))[0]:
            continue
        h = 1.2+1.4*rng.random()
        if rng.random() < .5:
            o = kit.cylinder(f'Detail {name} pump {b}', (x, y, floor+h/2), .9+.6*rng.random(), h, kit.slate,
                             vertices=12, bevel=0)
        else:
            o = kit.box(f'Detail {name} box {b}', (x, y, floor+h/2), (1.6+rng.random()*1.6, 1.4+rng.random()*1.2, h),
                        kit.slate, .15)
        kit.detail.append(o)


def dimple(kit, name, rect, depth):
    zs = _surface_z(kit, rect)
    if not zs:
        return
    floor = min(zs)-depth
    _cut(kit, _near(kit, rect), rect, floor)
    kit.detail.append(_prism(kit, f'Detail {name} floor', rect, floor-1.0, floor+.05, kit.tech))


def niche(kit, name, c, w, l, depth):
    """An arched recess: a slot with a round forward end, open aft, on the pod's aft slope."""
    cx, cy = c
    pts = [(cx-l/2, cy-w/2), (cx+l/2-w/2, cy-w/2)]
    for k in range(9):
        a = -math.pi/2+math.pi*k/8
        pts.append((cx+l/2-w/2+w/2*math.cos(a), cy+w/2*math.sin(a)))
    pts.append((cx-l/2, cy+w/2))
    dimple(kit, name, pts, depth)


def grille(kit, prow):
    """The prow grille: a recessed field on the prow top with fore-aft vanes in it."""
    x0, x1, hy = 143.0, 169.0, 26.0
    plan = [(x0, -hy), (x1, -hy), (x1, hy), (x0, hy)]
    zs = _surface_z(kit, plan)
    top = max(zs)
    floor = top-3.0
    _cut(kit, [prow], plan, floor)
    kit.detail.append(_prism(kit, 'Detail prow grille floor', plan, floor-1, floor+.05, kit.tech))
    n = 13
    for i in range(n):
        y = -hy+2+(2*hy-4)*i/(n-1)
        v = kit.box(f'Detail prow grille vane {i+1}', ((x0+x1)/2, y, floor+1.2), (x1-x0-2, 1.0, 2.4), kit.pale, .1)
        kit.detail.append(v)


def reactor_glow(kit, side, label):
    """Glow segments set into the reactor well's dark disk, round the dome on the bow-outboard-aft side."""
    cx, cy = pg_hull.REACTOR[0], pg_hull.REACTOR[1]*side
    z = pg_hull.REACTOR_TOP[label]
    a0, a1, n = REACTOR_GLOW
    for k in range(n):
        a = math.radians((a0+(a1-a0)*k/(n-1))*side)
        x, y = cx+12.2*math.cos(a), cy+12.2*math.sin(a)
        g = kit.box(f'Hull {label} reactor glow {k+1}', (x, y, z+.3), (2.0, 3.2, .8), kit.glow, 0)
        g.rotation_euler = (0, 0, a)
        kit.detail.append(g)


def seams(kit, parts):
    cx, cy = pg_hull.BORE_C
    targets = [parts['main ring'], parts['belt']]
    for b in SEAMS:
        a = math.radians(b)
        # From the shelf riser out to past the outer edge.
        r0, r1 = 100.0, 150.0
        ux, uy = math.cos(a), math.sin(a)
        vx, vy = -uy, ux
        plan = [(cx+ux*r0+vx*SEAM_W/2, cy+uy*r0+vy*SEAM_W/2), (cx+ux*r1+vx*SEAM_W/2, cy+uy*r1+vy*SEAM_W/2),
                (cx+ux*r1-vx*SEAM_W/2, cy+uy*r1-vy*SEAM_W/2), (cx+ux*r0-vx*SEAM_W/2, cy+uy*r0-vy*SEAM_W/2)]
        for ob in targets:
            # A seam follows the plate down: cut a thin slab whose floor sits SEAM_D under the surface at
            # each end, so it reads on the shelf and on the belt's glacis alike.
            cut = _seam_cutter(kit, plan, ob)
            if cut:
                K.boolean(ob, cut)


def _seam_cutter(kit, plan, ob):
    pts = []
    for (x, y) in plan:
        hit = kit.surface(x, y)
        pts.append(hit[0].z-SEAM_D if hit else None)
    if any(p is None for p in pts):
        return None
    verts = [(x, y, z) for (x, y), z in zip(plan, pts)]+[(x, y, 200.0) for x, y in plan]
    faces = [(3, 2, 1, 0), (4, 5, 6, 7)]+[(i, (i+1) % 4, 4+(i+1) % 4, 4+i) for i in range(4)]
    return kit.mesh('Temporary seam cutter', verts, faces, ob.material_slots[0].material)


def shelf_glow(kit):
    """The seam light along the gun shelf's riser (pg_hull.MAIN_RING: shelf r 99 at z 28.5, crown r 96 at z 36)."""
    loop = [(98.6, 28.7), (99.2, 28.7), (98.9, 30.0), (98.3, 30.0)]
    g = K.revolve_loop(kit, 'Hull main ring seam light', loop, kit.glow, center=(*pg_hull.BORE_C, 0), segments=180,
                       smooth=40)
    kit.detail.append(g)


def build(kit, parts):
    rng = random.Random(SEED)
    for side, label in ((1, 'port'), (-1, 'starboard')):
        for name, plan, depth in INSETS:
            inset(kit, f'{label} {name}', _mirror(plan, side), depth, rng)
        for i, (c, w, l, bearing, depth) in enumerate(DIMPLES, start=1):
            dimple(kit, f'{label} dimple {i}', _rect((c[0], c[1]*side), w, l, bearing*side), depth)
        for i, (c, w, l, depth) in enumerate(NICHES, start=1):
            niche(kit, f'{label} pod niche {i}', (c[0], c[1]*side), w, l, depth)
        reactor_glow(kit, side, label)
    grille(kit, parts['prow'])
    seams(kit, parts)
    shelf_glow(kit)
    aft_grilles(kit, parts)
    deck_windows(kit, parts)
    lining_ribs(kit)
    aft_body_seams(kit, parts)
    flank_panels(kit, parts)
    gantry(kit, parts)


# Stern machinery: dark finned grilles let into each pod's aft face between the drive blocks (port Y spans).
AFT_GRILLES = [(36.0, 51.0), (79.0, 94.0), (117.0, 129.0)]


def aft_grilles(kit, parts):
    x_face = pg_hull.POD_REAR_X
    for side, label in ((1, 'port'), (-1, 'starboard')):
        pod = parts['pod '+label]
        for i, (y0, y1) in enumerate(AFT_GRILLES, start=1):
            ya, yb = sorted((y0*side, y1*side))
            z0, z1 = -18.0, 2.0  # below the pod's rear wall top, so the plate above stays whole
            bpy.ops.mesh.primitive_cube_add(size=1, location=(x_face-2.0, (ya+yb)/2, (z0+z1)/2))
            cut = bpy.context.object
            cut.dimensions = (10.0, yb-ya, z1-z0)
            bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
            cut.data.materials.append(pod.material_slots[0].material)
            K.boolean(pod, cut)
            back = x_face+3.0
            kit.detail.append(kit.box(f'Detail {label} aft grille {i} back', (back-.5, (ya+yb)/2, (z0+z1)/2),
                                      (1.0, yb-ya, z1-z0), kit.tech, 0))
            n = int((yb-ya)/2.2)
            for f in range(n):
                y = ya+1.1+(yb-ya-2.2)*f/max(1, n-1)
                kit.detail.append(kit.box(f'Detail {label} aft grille {i} fin {f+1}', (back-2.2, y, (z0+z1)/2),
                                          (3.4, .5, z1-z0-1.0), kit.slate, 0))


def _flank_point(ob, x, y_dir, z):
    """Where a horizontal ray from outboard at (x, z), heading inboard along -y_dir, meets ob."""
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    inv = ev.matrix_world.inverted()
    o = inv @ Vector((x, 400.0*y_dir, z))
    d = (inv.to_3x3() @ Vector((0, -y_dir, 0))).normalized()
    hit, p, n, _ = ev.ray_cast(o, d)
    return (ev.matrix_world @ p, (ev.matrix_world.to_3x3() @ n).normalized()) if hit else None


def deck_windows(kit, parts):
    """Rows of glazed slits along the command deck's tiers."""
    for tier, z, xs in (('deck', pg_hull.DECK_Z+6.5, range(-30, -100, -5)),
                        ('deck upper', pg_hull.DECK_Z+14.0, range(-36, -96, -6))):
        ob = parts[tier]
        for side, label in ((1, 'port'), (-1, 'starboard')):
            for i, x in enumerate(xs, start=1):
                hit = _flank_point(ob, x, side, z)
                if not hit:
                    continue
                p, n = hit
                w = kit.box(f'Detail {label} {tier} window {i}', tuple(p), (3.0, .5, 1.1), kit.glass, 0)
                w.rotation_euler = (0, 0, math.atan2(n.y, n.x)+math.pi/2)
                kit.detail.append(w)


def lining_ribs(kit):
    """Radial machinery ribs on the bore lining's top between the emitter nodes."""
    cx, cy = pg_hull.BORE_C
    z = pg_hull.LINING_TOP
    for k in range(48):
        a = 2*math.pi*k/48
        if k % 4 == 0:
            continue  # where the emitter nodes stand (every 30 deg, offset 15: pg_shield)
        r = (pg_hull.BORE_R+pg_hull.LINING_R)/2
        b = kit.box(f'Detail bore lining rib {k+1}', (cx+r*math.cos(a), cy+r*math.sin(a), z+.5),
                    (pg_hull.LINING_R-pg_hull.BORE_R-3.0, 1.4, 1.0), kit.slate, .1)
        b.rotation_euler = (0, 0, a)
        kit.detail.append(b)


def aft_body_seams(kit, parts):
    ob = parts['aft body']
    for x in (-112.0, -134.0, -156.0):
        bpy.ops.mesh.primitive_cube_add(size=1, location=(x, 0, 60))
        cut = bpy.context.object
        cut.dimensions = (SEAM_W, 120, 40)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        # Only the top skin: drop the slab to SEAM_D under the aft body's crown at its centreline.
        top = kit.surface(x, 0)[0].z
        cut.location.z = top-SEAM_D+20
        cut.data.materials.append(ob.material_slots[0].material)
        K.boolean(ob, cut)


# Service panels let into the belt's armoured flank, port side: model X of each panel centre.
FLANK_PANELS = [52, 30, 8, -14, -36, -58, -80]


def flank_panels(kit, parts):
    belt = parts['belt']
    for side, label in ((1, 'port'), (-1, 'starboard')):
        for i, x in enumerate(FLANK_PANELS, start=1):
            hit = _flank_point(belt, x, side, 3.0)
            if not hit:
                continue
            p, n = hit
            yaw = math.atan2(n.y, n.x)
            M = Matrix.Translation(p) @ Matrix.Rotation(yaw, 4, 'Z')
            bpy.ops.mesh.primitive_cube_add(size=1)
            cut = bpy.context.object
            cut.dimensions = (3.0, 12.0, 4.0)   # local X along the outward normal
            bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
            cut.matrix_world = M
            cut.data.materials.append(belt.material_slots[0].material)
            K.boolean(belt, cut)
            back = kit.box(f'Detail {label} flank panel {i}', (0, 0, 0), (.8, 11.4, 3.4), kit.tech, 0)
            back.matrix_world = M @ Matrix.Translation((-1.2, 0, 0))
            kit.detail.append(back)


def gantry(kit, parts):
    """Frames and pipe runs on the command deck gantry's flanks: the dark machinery band round the deck."""
    ob = parts['trench']
    z_mid = (26+pg_hull.DECK_Z)/2
    for side, label in ((1, 'port'), (-1, 'starboard')):
        pts = []
        for i, x in enumerate(range(-34, -102, -6), start=1):
            hit = _flank_point(ob, x, side, z_mid)
            if not hit:
                continue
            p, n = hit
            pts.append(p)
            yaw = math.atan2(n.y, n.x)
            rib = kit.box(f'Detail {label} gantry frame {i}', (0, 0, 0), (1.6, 1.6, pg_hull.DECK_Z-27), kit.slate, .1)
            rib.matrix_world = Matrix.Translation(p) @ Matrix.Rotation(yaw, 4, 'Z')
            kit.detail.append(rib)
        if len(pts) > 1:
            for j, dz in enumerate((-4.0, 3.0), start=1):
                a, b = pts[0], pts[-1]
                mid = (a+b)/2
                pipe = kit.cylinder(f'Detail {label} gantry pipe {j}', (mid.x, mid.y+side*1.2, z_mid+dz), .7,
                                    (a-b).length, kit.steel, axis='X', vertices=10, bevel=0)
                kit.detail.append(pipe)
