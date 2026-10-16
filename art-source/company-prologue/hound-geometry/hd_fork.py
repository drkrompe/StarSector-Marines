"""The gun fork on the starboard bow: the structure around hardpoint WS 001.

The sprite's starboard forward block (cols 28-47, rows 5-24) is an H: a
solid base (rows 15-24), two tall tines running forward with twin points
(cols 29-34 and 41-47), and a cross bar (row 11) over the gap they leave
(cols 35-40) for the medium hardpoint. The tines rake down to their points
and their chins rake up, so the fork reads as armour, not a slab.

Pass 2 reads the tines as channel-section rails: each shows a brown outer
flange and a dark interior (cols 29-33, 40-46) with a grey inner edge, so
each tine is a U-channel open dorsally, a dark web at the bottom of the
slot crossed by stiffener ribs, its inner flange capped with a steel rail.
The cream points (rows 5-7) are armoured tip caps. The light-grey gun body
(cols 35-37) runs back through the base, so the base carries a gun trough
with cradle rails and a coaming; its top is segmented bolted armour. The
dark cross bar is a bolted I-beam carrying a targeting sensor.

The helpers here (bolts, loft between two polygons, ray-cast onto chosen
parts) are shared with hd_mounts.
"""
import math
import bmesh
import bpy
from mathutils import Vector
from hd_kit import boolean, facet_normal, material, plane_cut, prism, section

BASE_PLAN = [(19.5, -16), (28.4, -16), (29.4, -15), (29.4, 2), (28.4, 3), (19.5, 3)]
BASE = section(-4.6, 4.4, 1.6, 1.0)
TINES = {'port': (-3, 3), 'starboard': (-16, -9)}
TINE = section(-4.2, 4.8, 1.2, .9)

# Rake planes of the tines (applied below): the top falls 24 degrees forward
# of X 33, the chin rises 30 degrees forward of it.
TOP_Z, CHIN_Z, RAKE_X = 4.8, -4.2, 33.0
TROUGH_Y, TROUGH_FLOOR = (-8.0, -4.0), -1.4   # WS 001 gun trough in the base
SLOT_HALF, SLOT_FLOOR, SLOT_X = 1.3, -1.0, 27.0  # each tine's channel


def tine_top(x):
    return TOP_Z-math.tan(math.radians(24))*max(0, x-RAKE_X)


def tine_chin(x):
    return CHIN_Z+math.tan(math.radians(30))*max(0, x-RAKE_X)


def tine_plan(y0, y1):
    # Twin points at the tip (sprite rows 5-6), a notch between them.
    ym = (y0+y1)/2
    return [(26, y0), (38, y0), (40, y0+.5), (40, ym-1.1), (38.6, ym-.3), (38.6, ym+.3),
            (40, ym+1.1), (40, y1-.5), (38, y1), (26, y1)]


# --- helpers (also used by hd_mounts) ---

def triangulate_ngons(ob):
    """Ear-clip the concave n-gons a boolean leaves (a U-shaped top face
    otherwise renders as a black sheet across the opening)."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    big = [f for f in bm.faces if len(f.verts) > 4]
    bmesh.ops.triangulate(bm, faces=big, quad_method='BEAUTY', ngon_method='EAR_CLIP')
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()


def drop(objs, x, y, top=60.0):
    """Highest Z where a ray dropped at (x, y) meets any of objs, or None."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    best = None
    for ob in objs:
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        o = inv @ Vector((x, y, top))
        d = (inv.to_3x3() @ Vector((0, 0, -1))).normalized()
        hit, p, _, _ = ev.ray_cast(o, d)
        if hit:
            z = (ev.matrix_world @ p).z
            best = z if best is None or z > best else best
    return best


def loft(kit, name, lower, upper, mat, bevel=0):
    """Solid between two equal-count 3D polygons (a plate, cap or wedge)."""
    n = len(lower)
    verts = [tuple(v) for v in lower]+[tuple(v) for v in upper]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def bolts(kit, name, points, axis, mat, r=.13, h=.12):
    """One mesh of hexagonal bolt heads at points, standing out along axis
    (one axis for all, or a list with one per point)."""
    axes = list(axis) if isinstance(axis, list) else [axis]*len(points)
    verts, faces = [], []
    for p, axis in zip(points, axes):
        axis = Vector(axis).normalized()
        u = axis.orthogonal().normalized()
        w = axis.cross(u)
        p = Vector(p)
        b = len(verts)
        ring = [p+r*(math.cos(k*math.pi/3)*u+math.sin(k*math.pi/3)*w) for k in range(6)]
        verts += [tuple(v-axis*.05) for v in ring]+[tuple(v+axis*h) for v in ring]
        faces += [tuple(b+k for k in reversed(range(6))), tuple(b+6+k for k in range(6))]
        faces += [(b+k, b+(k+1) % 6, b+6+(k+1) % 6, b+6+k) for k in range(6)]
    return kit.mesh(name, verts, faces, mat)


def boxes(kit, name, specs, mat, bevel=0):
    """One mesh of axis-aligned boxes [((cx, cy, cz), (dx, dy, dz)), ...]."""
    verts, faces = [], []
    for (cx, cy, cz), (dx, dy, dz) in specs:
        b = len(verts)
        verts += [(cx+sx*dx/2, cy+sy*dy/2, cz+sz*dz/2) for sz in (-1, 1) for sy in (-1, 1) for sx in (-1, 1)]
        faces += [(b, b+2, b+3, b+1), (b+4, b+5, b+7, b+6), (b, b+1, b+5, b+4),
                  (b+2, b+6, b+7, b+3), (b, b+4, b+6, b+2), (b+1, b+3, b+7, b+5)]
    return kit.mesh(name, verts, faces, mat, bevel)


def seated_plate(kit, name, x0, x1, y0, y1, on, t, mat, sink=.25, bevel=.05):
    """A plate whose corners follow the parts `on` below it, raised by t."""
    corners = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    zs = [drop(on, x, y) for x, y in corners]
    zs = [z if z is not None else max(z for z in zs if z is not None) for z in zs]
    return loft(kit, name, [(x, y, z-sink) for (x, y), z in zip(corners, zs)],
                [(x, y, z+t) for (x, y), z in zip(corners, zs)], mat, bevel)


def _tip_cap(kit, name, pts, mat):
    """Armoured cap enveloping one twin point: chin to just above the rake."""
    lower = [(x, y, tine_chin(x)-.06) for x, y in pts]
    upper = [(x, y, tine_top(x)+.22) for x, y in pts]
    return loft(kit, name, lower, upper, mat, .05)


def build(kit):
    lens = material('Sensor lens glass', (.02, .03, .045), .3, .12)
    base = prism(kit, 'Fork base', BASE_PLAN, BASE, kit.orange)
    plane_cut(base, 'Fork base chin rake', (29.4, 0, -2.6), facet_normal((1, 0), 135))
    # Gun trough down the base for WS 001's cradle (the sprite's grey gun
    # body continues aft through the base, cols 35-37 rows 13-30).
    trough = kit.box('Temporary trough', (24.75, sum(TROUGH_Y)/2, 5), (12.5, TROUGH_Y[1]-TROUGH_Y[0], 12.8), kit.dark, 0)
    boolean(base, trough)
    # The tines overlap the base aft; trim its walls under them so the two
    # parts meet at a real seam instead of sharing a face.
    for y, s in ((-16, -1), (3, 1)):
        cut = kit.box('Temporary tine seam', (28.0, y+s*.4, 0), (4.4, .9, 12), kit.dark, 0)
        boolean(base, cut)
    # Bolted starboard service hatch, recessed into the base's outboard face.
    recess = kit.box('Temporary hatch recess', (23.2, -16, .4), (3.4, .44, 3.2), kit.dark, 0)
    boolean(base, recess)

    tines = []
    for side, (y0, y1) in TINES.items():
        ym = (y0+y1)/2
        tine = prism(kit, f'Fork {side} tine', tine_plan(y0, y1), TINE, kit.rust)
        plane_cut(tine, f'Fork {side} tine top rake', (RAKE_X, 0, TOP_Z), facet_normal((1, 0), 24))
        plane_cut(tine, f'Fork {side} tine chin rake', (RAKE_X, 0, CHIN_Z), facet_normal((1, 0), 150))
        # Channel section: a dorsal slot between the flanges, out through the
        # notch between the twin points.
        slot = kit.box('Temporary tine slot', ((SLOT_X+42)/2, ym, (SLOT_FLOOR+10)/2), (42-SLOT_X, 2*SLOT_HALF, 10-SLOT_FLOOR), kit.dark, 0)
        boolean(tine, slot)
        triangulate_ngons(tine)
        tines.append(tine)
        # Dark web at the bottom of the channel and the stiffener ribs across it.
        kit.box(f'Fork {side} tine web', ((SLOT_X+.1+38.2)/2, ym, SLOT_FLOOR+.12), (38.2-SLOT_X-.1, 2*SLOT_HALF-.04, .24), kit.dark, 0)
        ribs = []
        for x in (28.6, 31.0, 33.4, 35.8):
            ztop = min(tine_top(x)-1.6, 2.6)
            ribs.append(((x, ym, (SLOT_FLOOR+ztop)/2), (.32, 2*SLOT_HALF-.02, ztop-SLOT_FLOOR)))
        boxes(kit, f'Fork {side} tine stiffener ribs', ribs, kit.steel, .03)
        # Rail cap on the inner flange (the sprite's grey inner edge), in two
        # lengths broken at the rake.
        inner = (y0+.9, y0+1.55) if side == 'port' else (y1-1.55, y1-.9)
        for k, (xa, xb) in enumerate(((SLOT_X+.2, RAKE_X-.05), (RAKE_X+.05, 37.4)), 1):
            seated_plate(kit, f'Fork {side} tine rail cap {k}', xa, xb, inner[0], inner[1], [tine], .2, kit.steel, .2, .04)
        # Armoured caps on the twin points (the cream tips, rows 5-7).
        e = .05
        outer_pts = [(37.8, y1+e), (38.0, y1+e), (40+e, y1-.5), (40+e, ym+SLOT_HALF-e), (37.8, ym+SLOT_HALF-e)]
        inner_pts = [(37.8, ym-SLOT_HALF+e), (40+e, ym-SLOT_HALF+e), (40+e, y0+.5-e), (38.0, y0-e), (37.8, y0-e)]
        _tip_cap(kit, f'Fork {side} tine tip cap port', list(reversed(outer_pts)), kit.cream)
        _tip_cap(kit, f'Fork {side} tine tip cap starboard', inner_pts, kit.cream)
        # Bolt rows along the outer flange top (starboard tine: the port
        # tine's outer flange runs through the bridge cage).
        if side == 'starboard':
            pts = []
            for x in (27.2, 28.4, 29.6, 30.8, 32.0, 34.2, 35.6):
                pts.append((x, y0+1.55, tine_top(x)))
            bolts(kit, 'Fork starboard tine flange bolts', pts, (0, 0, 1), kit.steel)

    # Knee gussets bracing the tines' inner faces to the base's front face,
    # top and bottom of the gun gap.
    gussets, gbolts = [], []
    for yf, s in ((-3.0, -1), (-9.0, 1)):
        for z in (3.0, -2.2):
            p = [(29.2, yf, z-.15), (31.0, yf, z-.15), (29.2, yf+s*.95, z-.15)]
            q = [(xx, yy, z+.15) for xx, yy, _ in p]
            gussets.append(loft(kit, f'Fork gusset {len(gussets)+1}', p if s < 0 else list(reversed(p)),
                                q if s < 0 else list(reversed(q)), kit.steel, .03))
            gbolts += [(29.7, yf+s*.3, z+.15), (30.3, yf+s*.25, z+.15)]
    bolts(kit, 'Fork gusset bolts', gbolts, (0, 0, 1), kit.steel, .09, .08)

    # Cross bar: a bolted I-beam over the gun gap (sprite row 11).
    kit.box('Fork cross bar', (33.6, -6, 3.45), (.4, 6.0, 1.3), kit.steel, .03)
    kit.box('Fork cross bar top flange', (33.6, -6, 4.22), (1.6, 6.2, .32), kit.steel, .06)
    kit.box('Fork cross bar bottom flange', (33.6, -6, 2.72), (1.6, 6.2, .3), kit.steel, .06)
    for name, y in (('port', -3.14), ('starboard', -8.86)):
        kit.box(f'Fork cross bar {name} end plate', (33.6, y, 3.45), (2.3, .24, 2.1), kit.steel, .04)
        s = 1 if y > -6 else -1
        bolts(kit, f'Fork cross bar {name} end bolts',
              [(33.6+dx, y-s*.12, 3.45+dz) for dx in (-.75, .75) for dz in (-.7, .7)], (0, -s, 0), kit.steel, .1, .08)
    # Targeting sensor on the bar: an armoured box, a lens looking forward,
    # a sun hood over it.
    kit.box('Fork targeting sensor housing', (33.55, -6, 4.85), (1.5, 1.6, 1.0), kit.steel, .08)
    kit.cylinder('Fork targeting sensor lens', (34.33, -6, 4.85), .36, .12, lens, 'X', 12, 0)
    kit.box('Fork targeting sensor hood', (34.05, -6, 5.42), (.8, 1.5, .14), kit.steel, .03)

    # Gun trough: cradle rails on its floor, a raised coaming along each lip.
    for k, y in enumerate((-7.2, -4.8), 1):
        kit.box(f'Fork trough cradle rail {k}', (25.0, y, TROUGH_FLOOR+.22), (10.4, .5, .44), kit.steel, .04)
    kit.box('Fork trough floor', (24.9, -6, TROUGH_FLOOR+.03), (10.2, 3.9, .06), kit.dark, 0)
    for k, y in enumerate((-8.2, -3.8), 1):
        kit.box(f'Fork trough coaming {k}', (24.4, y, 4.62), (7.6, .4, .5), kit.rust, .05)

    # Segmented armour on the base top, between the body and the tine roots,
    # each plate bolted; dark seam cores under the gaps.
    plates = {'port aft': (20.7, 23.1, -3.5, 2.0), 'port fore': (23.35, 25.8, -3.5, 2.0),
              'starboard aft inboard': (20.7, 23.1, -11.6, -8.5), 'starboard aft outboard': (20.7, 23.1, -15.0, -11.85),
              'starboard fore inboard': (23.35, 25.8, -11.6, -8.5), 'starboard fore outboard': (23.35, 25.8, -15.0, -11.85)}
    pbolts = []
    for name, (x0, x1, y0, y1) in plates.items():
        seated_plate(kit, f'Fork base armour plate {name}', x0, x1, y0, y1, [base], .32, kit.orange, .1)
        pbolts += [(x+dx, y+dy, 4.72) for x, dx in ((x0, .3), (x1, -.3)) for y, dy in ((y0, .3), (y1, -.3))]
    bolts(kit, 'Fork base armour bolts', pbolts, (0, 0, 1), kit.steel, .11, .09)
    boxes(kit, 'Fork base seam cores', [((23.22, -11.7, 4.47), (.2, 6.6, .12)), ((23.22, -.85, 4.47), (.2, 5.5, .12)), ((21.9, -11.72, 4.47), (2.4, .2, .12)),
                                         ((24.6, -11.72, 4.47), (2.4, .2, .12))], kit.dark)

    # Service hatches: two bolted covers on the base armour, one recessed in
    # the starboard face (cut above).
    kit.box('Fork service hatch 1', (21.9, -13.4, 4.87), (1.6, 2.0, .14), kit.steel, .04)
    kit.box('Fork service hatch 1 handle', (21.9, -13.4, 5.0), (.2, 1.0, .12), kit.steel, .02)
    kit.cylinder('Fork service hatch 2', (24.55, -.8, 4.84), .75, .12, kit.steel, 'Z', 12, .03)
    kit.box('Fork service hatch 2 handle', (24.55, -.8, 4.96), (.9, .18, .1), kit.steel, .02)
    kit.box('Fork service hatch 3', (23.2, -15.89, .4), (3.0, .1, 2.8), kit.steel, .04)
    bolts(kit, 'Fork service hatch 3 bolts', [(23.2+dx, -15.84, .4+dz) for dx in (-1.25, 0, 1.25) for dz in (-1.15, 1.15)],
          (0, -1, 0), kit.steel, .1, .08)
    kit.hull.append(base)
