"""Shared Blender helpers for the Paragon, the Tri-Tachyon battleship.

A standalone copy of the kit helpers the Onslaught, Legion and Second Wind
builds use (materials, primitives, prisms, booleans, a downward ray-cast); it
deliberately imports no other hull's package, which other sessions are
editing. Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one sprite
pixel per unit about the .ship centre.

Tri-Tachyon is high tech: where the Hegemony hulls are faceted plate and
riveted modules, the Paragon is smooth. Masses are heightfields over their
plan (smooth_mass), shaded smooth with crisp edges kept only where a surface
turns sharply, so plating reads as sleek curved skin; seams glow.
"""
import math
import bmesh
import bpy
from mathutils import Vector


def material(name, color, metallic=0.0, roughness=.65, emission=None, strength=2.0, coat=0.0):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = roughness
    if coat:
        bsdf.inputs['Coat Weight'].default_value = coat
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*emission, 1)
        bsdf.inputs['Emission Strength'].default_value = strength
    return m


# Glow colours, shared with the VFX pass: Tri-Tachyon drives, shield and
# seams burn blue-white.
GLOW_BLUE = (.30, .62, 1.0)
GLOW_WHITE = (.80, .92, 1.0)


class Kit:
    def __init__(self):
        # Tri-Tachyon livery read off paragon.png: a pale blue-grey skin, the
        # near-white command core and prow, a darker slate for recessed
        # bands, dark technical lining round the ring, and blue-white glow.
        # Flat base materials until a texturing pass.
        # Matte painted armour, not gloss (owner, pass 1): a warship's paint is
        # flat and a little rough; only the emitters and glazing shine.
        self.skin = material('Tri-Tachyon blue-grey skin', (.30, .36, .42), .25, .62)
        self.pale = material('Tri-Tachyon pale skin', (.46, .51, .56), .25, .6)
        self.white = material('Tri-Tachyon white core', (.66, .68, .70), .15, .58)
        self.slate = material('Tri-Tachyon slate band', (.08, .095, .115), .45, .55)
        self.tech = material('Technical lining', (.03, .035, .045), .55, .55)
        self.steel = material('Emitter metal', (.20, .22, .25), .75, .4)
        self.glow = material('Tri-Tachyon blue glow', (.05, .1, .2), .0, .4, emission=GLOW_BLUE, strength=12.0)
        self.glow_white = material('Tri-Tachyon white glow', (.2, .2, .2), .0, .4, emission=GLOW_WHITE, strength=10.0)
        self.glass = material('Bridge glazing', (.02, .04, .06), .2, .08, emission=(.55, .8, 1.0), strength=2.5)
        self.mpu = 1.0  # metres per unit; the driver sets it before building
        self.hull = []  # objects the downward ray-cast seats parts on
        self.detail = []  # pass 2 detail objects; turret wells cut through these too

    def finish(self, obj, name, mat, bevel=0, smooth=False):
        obj.name = name
        obj.data.name = name
        obj.data.materials.append(mat)
        if smooth:
            obj.data.shade_smooth()
            obj.data.set_sharp_from_angle(angle=math.radians(smooth if smooth is not True else 35))
        if bevel:
            b = obj.modifiers.new('Plate edge chamfer', 'BEVEL')
            b.width, b.segments, b.limit_method = bevel, 1, 'ANGLE'
        return obj

    def box(self, name, loc, dims, mat, bevel=.15):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        obj = bpy.context.object
        obj.dimensions = dims
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        return self.finish(obj, name, mat, bevel)

    def cylinder(self, name, loc, radius, depth, mat, axis='Z', vertices=32, bevel=.08, smooth=True):
        bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
        obj = bpy.context.object
        if axis == 'X':
            obj.rotation_euler[1] = math.pi / 2
        elif axis == 'Y':
            obj.rotation_euler[0] = math.pi / 2
        return self.finish(obj, name, mat, bevel, smooth)

    def mesh(self, name, verts, faces, mat, bevel=0, smooth=False):
        """Polygon solid with outward normals."""
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(me)
        bm.free()
        me.update()
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        return self.finish(ob, name, mat, bevel, smooth)

    def empty(self, name, loc, angle_deg=0.0, kind='ARROWS', size=3):
        """An anchor: its +X points along angle_deg in plan."""
        ob = bpy.data.objects.new(name, None)
        ob.empty_display_type = kind
        ob.empty_display_size = size
        ob.location = loc
        ob.rotation_euler = (0, 0, math.radians(angle_deg))
        bpy.context.collection.objects.link(ob)
        return ob

    def surface(self, x, y, top=160):
        """Highest (point, normal) where a ray dropped at (x, y) meets the hull parts."""
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        best = None
        for ob in self.hull:
            ev = ob.evaluated_get(dg)
            inv = ev.matrix_world.inverted()
            o = inv @ Vector((x, y, top))
            d = (inv.to_3x3() @ Vector((0, 0, -1))).normalized()
            hit, p, n, _ = ev.ray_cast(o, d)
            if hit:
                p = ev.matrix_world @ p
                if best is None or p.z > best[0].z:
                    best = (p, (ev.matrix_world.to_3x3() @ n).normalized())
        return best


# --- Planform helpers (shared with the other hull kits). ---

# --- Planform prisms: one keel chamfer, one wall, one shoulder chamfer. ---

def _left(x, y):
    length = (x*x+y*y)**.5
    return (-y/length, x/length)


def inset(poly, d):
    """Mitred inward offset of a CCW polygon."""
    out = []
    n = len(poly)
    for i in range(n):
        (ax, ay), (bx, by), (cx, cy) = poly[i-1], poly[i], poly[(i+1) % n]
        n0 = _left(bx-ax, by-ay)
        n1 = _left(cx-bx, cy-by)
        k = d/(1+n0[0]*n1[0]+n0[1]*n1[1])
        out.append((bx+(n0[0]+n1[0])*k, by+(n0[1]+n1[1])*k))
    return out


def section(keel, deck, keel_c, shoulder_c):
    """Profile (inset, z) keel to deck: keel chamfer, vertical wall, shoulder chamfer."""
    return [(keel_c, keel), (0, keel+keel_c), (0, deck-shoulder_c), (shoulder_c, deck)]


INSET_FAULTS = []   # (object name, inset) where an inset ring folded over; the driver reports them


def _crossing(poly):
    n = len(poly)
    def hit(a, b, c, d):
        def o(p, q, r):
            return (q[0]-p[0])*(r[1]-p[1])-(q[1]-p[1])*(r[0]-p[0])
        return o(a, b, c)*o(a, b, d) < 0 and o(c, d, a)*o(c, d, b) < 0
    for i in range(n):
        for j in range(i+2, n):
            if i == 0 and j == n-1:
                continue
            if hit(poly[i], poly[(i+1) % n], poly[j], poly[(j+1) % n]):
                return True
    return False


def prism(kit, name, plan, profile, mat, bevel=.12):
    n = len(plan)
    for d, _ in profile:
        ring = inset(plan, d)
        area = sum(ring[i-1][0]*q[1]-q[0]*ring[i-1][1] for i, q in enumerate(ring))
        if d and (area <= 0 or _crossing(ring)):
            INSET_FAULTS.append((name, d))
            break
    verts = [(x, y, z) for d, z in profile for x, y in inset(plan, d)]
    faces = [tuple(reversed(range(n)))]
    for r in range(len(profile)-1):
        faces += [(r*n+i, r*n+(i+1) % n, (r+1)*n+(i+1) % n, (r+1)*n+i) for i in range(n)]
    top = (len(profile)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, mat, bevel)


def mirror_y(plan):
    """A port-side CCW plan reflected to starboard, still CCW."""
    return [(x, -y) for x, y in reversed(plan)]


def loft_x(kit, name, stations, mat, bevel=.12, smooth=False):
    """Solid lofted along X between equal-count YZ sections [(x, [(y, z), ...]), ...]."""
    n = len(stations[0][1])
    verts = [(x, y, z) for x, sec in stations for y, z in sec]
    faces = [tuple(range(n)), tuple(range((len(stations)-1)*n, len(stations)*n))]
    for s in range(len(stations)-1):
        faces += [(s*n+i, s*n+(i+1) % n, (s+1)*n+(i+1) % n, (s+1)*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel, smooth)


def octagon(y0, y1, z0, z1, ct, cb=None, ci=None, co=None):
    """Chamfered rectangle in YZ: top corners cut by ct, bottom by cb.
    ci/co override the top chamfer on the +Y / -Y side."""
    cb = ct if cb is None else cb
    hi = ct if ci is None else ci
    lo = ct if co is None else co
    return [(y0, z0+cb), (y0+cb, z0), (y1-cb, z0), (y1, z0+cb),
            (y1, z1-hi), (y1-hi, z1), (y0+lo, z1), (y0, z1-lo)]


def boolean(target, cutter, op='DIFFERENCE', keep=False):
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
    mod.operation = op
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    if any(p.use_smooth for p in target.data.polygons):
        target.data.set_sharp_from_angle(angle=math.radians(40))
    if not keep:
        bpy.data.objects.remove(cutter, do_unlink=True)


def plane_cut(target, name, point, normal, size=200):
    """Remove everything on the normal side of a plane: one flat facet."""
    normal = Vector(normal).normalized()
    bpy.ops.mesh.primitive_cube_add(size=1, location=Vector(point)+normal*size/2)
    cutter = bpy.context.object
    cutter.name = 'Temporary '+name+' cutter'
    cutter.scale = (size, size, size)
    cutter.rotation_euler = normal.to_track_quat('Z', 'Y').to_euler()
    boolean(target, cutter)


def facet_normal(down, angle):
    """Outward normal of a face falling along plan direction `down` at `angle` deg below horizontal."""
    a = math.radians(angle)
    dx, dy = down
    length = (dx*dx+dy*dy)**.5
    return (dx/length*math.sin(a), dy/length*math.sin(a), math.cos(a))


def strip_plan(points, width):
    """CCW plan of a band of constant `width` centred on a polyline."""
    left, right = [], []
    for i, (x, y) in enumerate(points):
        a = points[max(i-1, 0)]
        b = points[min(i+1, len(points)-1)]
        nx, ny = _left(b[0]-a[0], b[1]-a[1])
        left.append((x+nx*width/2, y+ny*width/2))
        right.append((x-nx*width/2, y-ny*width/2))
    return right+list(reversed(left))


def ccw(plan):
    """The plan with counter-clockwise winding."""
    area = sum(plan[i-1][0]*p[1]-p[0]*plan[i-1][1] for i, p in enumerate(plan))
    return list(plan) if area > 0 else list(reversed(plan))


def clip_rect(poly, x0, x1, y0, y1):
    """Sutherland-Hodgman clip of a plan polygon to an axis-aligned rectangle."""
    def clip(pts, inside, cross):
        out = []
        for i, p in enumerate(pts):
            q = pts[i-1]
            if inside(p):
                if not inside(q):
                    out.append(cross(q, p))
                out.append(p)
            elif inside(q):
                out.append(cross(q, p))
        return out
    def at_x(x):
        return lambda a, b: (x, a[1]+(b[1]-a[1])*(x-a[0])/(b[0]-a[0]))
    def at_y(y):
        return lambda a, b: (a[0]+(b[0]-a[0])*(y-a[1])/(b[1]-a[1]), y)
    pts = list(poly)
    for inside, cross in ((lambda p: p[0] >= x0, at_x(x0)), (lambda p: p[0] <= x1, at_x(x1)),
                          (lambda p: p[1] >= y0, at_y(y0)), (lambda p: p[1] <= y1, at_y(y1))):
        pts = clip(pts, inside, cross)
        if not pts:
            break
    out = []   # drop repeated corners where a vertex lies on a clip edge
    for p in pts:
        if not out or abs(p[0]-out[-1][0])+abs(p[1]-out[-1][1]) > 1e-6:
            out.append(p)
    while len(out) > 1 and abs(out[0][0]-out[-1][0])+abs(out[0][1]-out[-1][1]) <= 1e-6:
        out.pop()
    return out


def curved(keel, deck, bilge_in, bilge_h, tumble_in, tumble_h, segs=4):
    """Profile (inset, z) keel to deck with a rounded bilge and a rounded tumblehome.

    The owner asked for the Onslaught to be thick, with large curving hull
    depth: the lower hull turns in under a quarter-ellipse bilge (bilge_in
    inboard over bilge_h of height) and the upper hull rolls in to the deck
    under a quarter-ellipse shoulder. Flat-shaded facets, so it still reads
    as plate."""
    out = []
    for k in range(segs+1):
        a = math.pi/2*k/segs
        out.append((bilge_in*(1-math.sin(a)), keel+bilge_h*(1-math.cos(a))))
    for k in range(segs+1):
        a = math.pi/2*k/segs
        out.append((tumble_in*(1-math.cos(a)), deck-tumble_h+tumble_h*math.sin(a)))
    return out


def rounded_prism(kit, name, plan, keel, deck, bilge_in=0.0, bilge_h=0.0, tumble_in=0.0, tumble_h=0.0,
                  mat=None, segs=4, bevel=.12):
    """A plan extruded keel to deck whose bottom edge turns under on a rounded bilge
    (bilge_in inboard over bilge_h of height) and whose top edge rolls over on a
    rounded shoulder (tumble_in over tumble_h).

    Unlike prism()'s mitred insets, which fold over wherever an edge is shorter than
    the inset, the rounding is Blender's bevel with overlap clamping, so a narrow
    or concave plan rounds less where it must instead of turning inside out. Each
    bevel runs in a Z-scaled space so its quarter-round becomes the bilge's or the
    shoulder's own ellipse."""
    plan = ccw(plan)
    n = len(plan)
    bm = bmesh.new()
    lo = [bm.verts.new((x, y, keel)) for x, y in plan]
    hi = [bm.verts.new((x, y, deck)) for x, y in plan]
    bm.faces.new(list(reversed(lo)))
    bm.faces.new(hi)
    for i in range(n):
        bm.faces.new((lo[i], lo[(i+1) % n], hi[(i+1) % n], hi[i]))
    bm.normal_update()

    def round_edges(z_level, inset_d, height):
        if inset_d <= 0 or height <= 0:
            return
        f = inset_d/height
        for v in bm.verts:
            v.co.z *= f
        target = z_level*f
        edges = [e for e in bm.edges if all(abs(v.co.z-target) < 1e-4 for v in e.verts)
                 and len(e.link_faces) == 2 and any(abs(fc.normal.z) < .5 for fc in e.link_faces)]
        bmesh.ops.bevel(bm, geom=edges, offset=inset_d, offset_type='OFFSET', segments=segs, profile=.5,
                        affect='EDGES', clamp_overlap=True)
        for v in bm.verts:
            v.co.z /= f
        bm.normal_update()

    round_edges(keel, bilge_in, bilge_h)
    round_edges(deck, tumble_in, tumble_h)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    return kit.finish(ob, name, mat, bevel)


def _edge_distance(p, plan):
    best = 1e9
    px, py = p
    n = len(plan)
    for i in range(n):
        ax, ay = plan[i]
        bx, by = plan[(i+1) % n]
        dx, dy = bx-ax, by-ay
        L = dx*dx+dy*dy
        t = 0.0 if L == 0 else max(0.0, min(1.0, ((px-ax)*dx+(py-ay)*dy)/L))
        qx, qy = ax+t*dx-px, ay+t*dy-py
        best = min(best, qx*qx+qy*qy)
    return best**.5


def _resample(plan, step):
    out = []
    n = len(plan)
    for i in range(n):
        (ax, ay), (bx, by) = plan[i], plan[(i+1) % n]
        L = ((bx-ax)**2+(by-ay)**2)**.5
        k = max(1, int(math.ceil(L/step)))
        out += [(ax+(bx-ax)*j/k, ay+(by-ay)*j/k) for j in range(k)]
    return out




# --- Smooth masses: heightfields over a plan, with holes. ---

def _loops_segments(loops):
    import numpy as np
    a, b = [], []
    for loop in loops:
        n = len(loop)
        for i in range(n):
            a.append(loop[i])
            b.append(loop[(i+1) % n])
    return np.array(a, float), np.array(b, float)


def edge_distance(points, loops):
    """Distance from each (x, y) to the nearest edge of any loop."""
    import numpy as np
    A, B = _loops_segments(loops)
    P = np.asarray(points, float)
    out = np.empty(len(P))
    D = B-A
    L = np.maximum((D*D).sum(1), 1e-12)
    for s in range(0, len(P), 512):
        p = P[s:s+512, None, :]
        t = np.clip(((p-A)*D).sum(2)/L, 0, 1)
        q = A+t[..., None]*D-p
        out[s:s+512] = np.sqrt((q*q).sum(2).min(1))
    return out


def inside(points, loop):
    """Even-odd point-in-polygon test for each point against one loop."""
    import numpy as np
    P = np.asarray(points, float)
    res = np.zeros(len(P), bool)
    n = len(loop)
    for i in range(n):
        (ax, ay), (bx, by) = loop[i], loop[(i+1) % n]
        cond = (ay > P[:, 1]) != (by > P[:, 1])
        with np.errstate(divide='ignore', invalid='ignore'):
            xc = ax+(P[:, 1]-ay)*(bx-ax)/(by-ay)
        res ^= cond & (P[:, 0] < xc)
    return res


def cw(plan):
    return list(reversed(ccw(plan)))


def circle(cx, cy, r, n=96, start=0.0):
    return [(cx+r*math.cos(start+2*math.pi*k/n), cy+r*math.sin(start+2*math.pi*k/n)) for k in range(n)]


def smooth_mass(kit, name, plan, keel, deck, holes=(), round_in=8.0, round_h=6.0, belly_in=None, belly_h=None,
                crown=0.0, crown_w=20.0, belly_crown=0.0, mat=None, step=2.5, segs=5, smooth=True, sharp=50):
    """A plan (with optional holes) extruded keel to deck, whose top and belly are smooth heightfields.

    The top rolls over from the wall's top (deck - round_h) on a quarter
    ellipse to the deck round_in inboard of the nearest edge, then crowns up by
    `crown` over the next crown_w; the belly mirrors it (belly_in, belly_h,
    belly_crown). Distance is to the nearest edge of the plan or of a hole, so
    a ring rises to a ridge midway between its outer edge and its bore, and a
    narrow part simply reaches a lower crown; nothing folds over. Shaded smooth,
    with sharp edges kept only past `sharp` degrees, so the skin reads as one
    curved surface."""
    import numpy as np
    from mathutils.geometry import delaunay_2d_cdt
    belly_in = round_in if belly_in is None else belly_in
    belly_h = round_h if belly_h is None else belly_h
    outer = ccw(plan)
    hl = [cw(h) for h in holes]
    loops = [_resample(outer, step)]+[_resample(h, step) for h in hl]
    rawloops = [outer]+hl
    # Contour rows over the rounded edge (denser where it turns) and the crown.
    def ease(k, n):
        return 1-math.cos(k/n*math.pi/2)
    dists = sorted({round(v, 3) for v in
                    [round_in*ease(k, segs) for k in range(1, segs+1) if round_in > 0] +
                    [belly_in*ease(k, segs) for k in range(1, segs+1) if belly_in > 0] +
                    [max(round_in, belly_in)+crown_w*f for f in (.25, .5, .75, 1.0)
                     if (crown or belly_crown) and crown_w > 0]})
    pts = [p for loop in loops for p in loop]
    nbs = [len(l) for l in loops]
    inner = []
    last = 0.0
    for d in dists:
        cand = []
        for loop in loops:
            m = len(loop)
            for i, (x, y) in enumerate(loop):
                ax, ay = loop[i-1]
                bx, by = loop[(i+1) % m]
                l0 = _left(x-ax, y-ay)
                l1 = _left(bx-x, by-y)
                nx, ny = l0[0]+l1[0], l0[1]+l1[1]
                nl = (nx*nx+ny*ny)**.5 or 1.0
                cand.append((x+nx/nl*d, y+ny/nl*d))
        if not cand:
            break
        ed = edge_distance(cand, rawloops)
        ok = np.abs(ed-d) < .25*min(step, max(d, .3))
        row = []
        for q, good in zip(cand, ok):
            if good and (not row or (q[0]-row[-1][0])**2+(q[1]-row[-1][1])**2 > (.55*step)**2):
                row.append(q)
        if not row:
            break
        inner += row
        last = d
    xs = [p[0] for p in loops[0]]
    ys = [p[1] for p in loops[0]]
    g = step*1.3
    grid = [(x, y) for y in np.arange(min(ys)+g/2, max(ys), g) for x in np.arange(min(xs)+g/2, max(xs), g)]
    if grid:
        gd = edge_distance(grid, rawloops)
        ins = inside(grid, outer)
        for h in hl:
            ins &= ~inside(grid, h)
        inner += [p for p, d, i in zip(grid, gd, ins) if i and d > last+.45*g]
    if inner:
        ins = inside(inner, outer)
        for h in hl:
            ins &= ~inside(inner, h)
        inner = [p for p, i in zip(inner, ins) if i]
    allpts = pts+inner
    vin = [Vector((x, y)) for x, y in allpts]
    cons, s = [], 0
    for n in nbs:
        cons.append(list(range(s, s+n)))
        s += n
    res = delaunay_2d_cdt(vin, [], cons, 2, 1e-5)
    vco, faces = res[0], res[2]
    dist = edge_distance([(v.x, v.y) for v in vco], rawloops)
    wall_hi, wall_lo = deck-round_h, keel+belly_h

    def z_top(d):
        if round_in > 0 and d < round_in:
            t = d/round_in
            return wall_hi+round_h*math.sqrt(max(0.0, 1-(1-t)**2))
        if crown and crown_w > 0:
            return deck+crown*math.sin(min(1.0, (d-round_in)/crown_w)*math.pi/2)
        return deck

    def z_bot(d):
        if belly_in > 0 and d < belly_in:
            t = d/belly_in
            return wall_lo-belly_h*math.sqrt(max(0.0, 1-(1-t)**2))
        if belly_crown and crown_w > 0:
            return keel-belly_crown*math.sin(min(1.0, (d-belly_in)/crown_w)*math.pi/2)
        return keel

    m = len(vco)
    verts = [(v.x, v.y, z_top(d)) for v, d in zip(vco, dist)]+[(v.x, v.y, z_bot(d)) for v, d in zip(vco, dist)]
    fs = [tuple(f) for f in faces]+[tuple(m+i for i in reversed(f)) for f in faces]
    vmap = {}
    for j, origs in enumerate(res[3]):
        for o in origs:
            vmap[o] = j
    s = 0
    for n in nbs:
        b = [vmap[i] for i in range(s, s+n)]
        for i in range(n):
            a, c = b[i], b[(i+1) % n]
            if a != c:
                fs.append((a+m, c+m, c, a))
        s += n
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], fs)
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    return kit.finish(ob, name, mat, 0, sharp if smooth else False)


def revolve(kit, name, profile, mat, center=(0, 0, 0), segments=48, smooth=True, cap=True):
    """Solid of revolution about a vertical axis: profile [(r, z), ...] from the axis bottom to the axis top."""
    cx, cy, cz = center
    rings = []
    verts = []
    for r, z in profile:
        if r < 1e-6:
            rings.append([len(verts)])
            verts.append((cx, cy, cz+z))
        else:
            rings.append(list(range(len(verts), len(verts)+segments)))
            verts += [(cx+r*math.cos(2*math.pi*k/segments), cy+r*math.sin(2*math.pi*k/segments), cz+z)
                      for k in range(segments)]
    faces = []
    for a, b in zip(rings, rings[1:]):
        if len(a) == 1 and len(b) == 1:
            continue
        if len(a) == 1:
            faces += [(a[0], b[k], b[(k+1) % segments]) for k in range(segments)]
        elif len(b) == 1:
            faces += [(a[k], a[(k+1) % segments], b[0]) for k in range(segments)]
        else:
            faces += [(a[k], a[(k+1) % segments], b[(k+1) % segments], b[k]) for k in range(segments)]
    if cap:
        if len(rings[0]) > 1:
            faces.append(tuple(reversed(rings[0])))
        if len(rings[-1]) > 1:
            faces.append(tuple(rings[-1]))
    return kit.mesh(name, verts, faces, mat, 0, 40 if smooth else False)


def place(ob, loc=(0, 0, 0), rot_z=0.0, rot=None):
    ob.location = loc
    ob.rotation_euler = rot if rot else (0, 0, math.radians(rot_z))
    return ob


def revolve_loop(kit, name, loop, mat, center=(0, 0, 0), segments=48, smooth=40):
    """A closed (r, z) section loop revolved about a vertical axis: rings, shells, lips."""
    cx, cy, cz = center
    n = len(loop)
    verts = [(cx+r*math.cos(2*math.pi*k/segments), cy+r*math.sin(2*math.pi*k/segments), cz+z)
             for k in range(segments) for r, z in loop]
    faces = []
    for k in range(segments):
        a, b = k*n, ((k+1) % segments)*n
        faces += [(a+i, a+(i+1) % n, b+(i+1) % n, b+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, 0, smooth)


def profile_mass(kit, name, plan, top, bot, holes=(), mat=None, step=2.5, smooth=False):
    """A plan (with holes) whose top and belly are piecewise-linear in edge distance.

    top and bot are [(d, z), ...] with d rising from 0 (the edge): the surface
    is z at distance d from the nearest edge of the plan or a hole, held at the
    last z beyond the last d. Flat-shaded, each segment of a profile is one
    facet band running round the plan: a steep first step is an armour bevel,
    a shallow one a glacis. The wall joins top[0] to bot[0] at the edge."""
    import numpy as np
    from mathutils.geometry import delaunay_2d_cdt
    outer = ccw(plan)
    hl = [cw(h) for h in holes]
    loops = [_resample(outer, step)]+[_resample(h, step) for h in hl]
    rawloops = [outer]+hl
    dists = sorted({round(d, 3) for d, _ in list(top)+list(bot) if d > 0})

    def interp(prof, d):
        if d <= prof[0][0]:
            return prof[0][1]
        for (d0, z0), (d1, z1) in zip(prof, prof[1:]):
            if d <= d1:
                return z0+(z1-z0)*(d-d0)/(d1-d0)
        return prof[-1][1]

    pts = [p for loop in loops for p in loop]
    nbs = [len(l) for l in loops]
    inner, last = [], 0.0
    for d in dists:
        cand = []
        for loop in loops:
            m = len(loop)
            for i, (x, y) in enumerate(loop):
                ax, ay = loop[i-1]
                bx, by = loop[(i+1) % m]
                l0, l1 = _left(x-ax, y-ay), _left(bx-x, by-y)
                nx, ny = l0[0]+l1[0], l0[1]+l1[1]
                nl = (nx*nx+ny*ny)**.5 or 1.0
                cand.append((x+nx/nl*d, y+ny/nl*d))
        ed = edge_distance(cand, rawloops)
        ok = np.abs(ed-d) < .25*min(step, max(d, .3))
        row = []
        for q, good in zip(cand, ok):
            if good and (not row or (q[0]-row[-1][0])**2+(q[1]-row[-1][1])**2 > (.55*step)**2):
                row.append(q)
        if not row:
            break
        inner += row
        last = d
    xs = [p[0] for p in loops[0]]
    ys = [p[1] for p in loops[0]]
    g = step*1.6
    grid = [(x, y) for y in np.arange(min(ys)+g/2, max(ys), g) for x in np.arange(min(xs)+g/2, max(xs), g)]
    if grid:
        gd = edge_distance(grid, rawloops)
        inner += [p for p, d in zip(grid, gd) if d > last+.45*g]
    if inner:
        ins = inside(inner, outer)
        for h in hl:
            ins &= ~inside(inner, h)
        inner = [p for p, i in zip(inner, ins) if i]
    vin = [Vector((x, y)) for x, y in pts+inner]
    cons, s = [], 0
    for n in nbs:
        cons.append(list(range(s, s+n)))
        s += n
    res = delaunay_2d_cdt(vin, [], cons, 2, 1e-5)
    vco, faces = res[0], res[2]
    dist = edge_distance([(v.x, v.y) for v in vco], rawloops)
    m = len(vco)
    verts = [(v.x, v.y, interp(top, d)) for v, d in zip(vco, dist)]+[(v.x, v.y, interp(bot, d)) for v, d in zip(vco, dist)]
    fs = [tuple(f) for f in faces]+[tuple(m+i for i in reversed(f)) for f in faces]
    vmap = {}
    for j, origs in enumerate(res[3]):
        for o in origs:
            vmap[o] = j
    s = 0
    for n in nbs:
        b = [vmap[i] for i in range(s, s+n)]
        for i in range(n):
            a, c = b[i], b[(i+1) % n]
            if a != c:
                fs.append((a+m, c+m, c, a))
        s += n
    ob = kit.mesh(name, verts, fs, mat, 0, smooth)
    # Merge the coplanar triangles of each facet band so the flat faces read as plates.
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.dissolve_limit(bm, angle_limit=math.radians(1.0), verts=bm.verts, edges=bm.edges)
    bm.to_mesh(ob.data)
    bm.free()
    return ob
