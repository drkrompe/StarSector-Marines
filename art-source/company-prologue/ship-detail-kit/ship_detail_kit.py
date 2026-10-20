"""Ship detail kit: the shared detail builders for every cinematic hull.

The Legion battlecarrier and the Colossus heavy freighter are the owner's
reference hulls; this module is their detail language as code. Everything a
hull needs to hold up at a close view - plate runs with seams, framed
recessed glazing, hatches, doors, vents, RCS clusters, masts, dishes, lamps,
rails and ladders, open structure - comes from here, at the sizes in
`ship-detail-guide.md`.

Use, inside a hull's Blender build:

    sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'ship-detail-kit'))
    import ship_detail_kit as sdk
    S = sdk.Scale(METRES_PER_UNIT)        # metres -> hull units, segment rule
    class Kit(sdk.BaseKit): ...            # the hull's own palette on top

The module has two layers.

1. Primitives moved verbatim out of the Legion and Colossus builds
   (`BaseKit`, `prism`, `loft_x`, `sweep`, `modules`, `capped`, `beam`,
   `block`, `solid`, `disc`, `annulus`, `rail_run`, `wall_vent`, `wall_cover`,
   `bolted_plate`, `louvre_vent`, `space_frame`, `pipes`, `studs`,
   `revolve_dir`, `rcs_quad`, `lamp_housing`, ...). Their arguments are hull
   units and their defaults are the sizes the reference hull used, so the
   Legion and Colossus builds import them and stay byte-identical. Defaults
   that are Colossus sizes are 0.5 m per unit; pass sizes through `Scale.u`
   on any other hull.
2. Metric builders (`window_row`, `round_port`, `deck_hatch`, `wall_door`,
   `mast`, `dish`, `lamp_fitting`, `ladder`, `mushroom_vent`, `rcs_cluster`,
   `seam_cut`, `skin_groove`, `armour_run`, `smooth`, `bevel`) that take a
   `Scale` and real sizes in metres, apply the segment rule and the bevel
   table, and keep emissives off everything but a lamp's lens or a lit pane.

Coordinates are hull-local, +X bow, +Y port, +Z dorsal. Nothing here imports
a hull package; hulls import this.
"""
import math
import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree


# =============================================================================
# The standard: segment rule, bevel table, person sizes (ship-detail-guide.md)
# =============================================================================

SAG_M = .005            # a round's chord may stand at most 5 mm inside the true arc
MIN_SEGMENTS = 12       # any round part from 5 cm radius up
MAX_SEGMENTS = 96       # ceiling (turret-kit ROUND_MAX); rounds that big are hull facets by design
ROD_SEGMENTS = 8        # rods, whips, pins and pipes under 5 cm radius
BOLT_SEGMENTS = 6       # bolt and nut heads are hexagons by design
VISIBLE_SAG_M = .025    # check gate: about one pixel at the accepted Legion/Colossus close views

# Bevel widths in metres by the size of the part (guide: Edge treatment). Measured on the
# Colossus: boxes 7.5 cm, prisms 6 cm, fittings 3 cm, small parts 1.5 cm; Legion hull boxes 15 cm.
BEVEL_M = {'hull': .12, 'module': .06, 'fitting': .03, 'small': .015}
# Person-scale sizes in metres (guide: Hatches and doors; Railings and walkways).
PERSON_M = {'door': (1.1, 2.1), 'hatch': (1.46, 2.07), 'deck_hatch': (1.9, 1.9), 'rail': 1.1,
            'stanchion_pitch': 1.4, 'rung_pitch': .3, 'ladder_width': .55, 'walkway': 1.2, 'figure': 1.8}


def segments(radius_m, sag=SAG_M):
    """Segments a round of `radius_m` metres needs so no facet shows at a close view.

    The smallest multiple of four whose chord sits at most 5 mm inside the arc, 12 to 96
    (8 under 5 cm radius). This is what the references' finished rounds measure: the
    Colossus mast (0.175 m, 16, 3.4 mm), dome (1 m, 32, 4.8 mm) and dish (1.7 m, 48, 3.6 mm).
    Against turret-kit's 0.22 m chord it is the same at 1.2 m radius, stricter below
    and looser above."""
    r = abs(radius_m)
    if r < .05:
        return ROD_SEGMENTS
    n = math.pi/math.acos(max(-1.0, 1-sag/r))
    return min(MAX_SEGMENTS, max(MIN_SEGMENTS, 4*math.ceil(n/4-1e-9)))


def sag(radius_m, n):
    """How far (metres) the chord of an n-sided round of this radius sits inside the arc."""
    return abs(radius_m)*(1-math.cos(math.pi/max(3, n)))


class Scale:
    """One hull's metres-per-unit, for metric fittings in hull units."""

    def __init__(self, mpu):
        self.mpu = mpu

    def u(self, metres):
        """Metres to hull units."""
        return metres/self.mpu

    def m(self, units):
        """Hull units to metres."""
        return units*self.mpu

    def n(self, radius_units):
        """Segment count for a round of this radius in hull units."""
        return segments(radius_units*self.mpu)

    def bevel(self, kind):
        """The bevel width (hull units) for 'hull', 'module', 'fitting' or 'small' parts."""
        return BEVEL_M[kind]/self.mpu


def material(name, color, metallic=0.0, roughness=.65, emission=None, strength=2.0):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = roughness
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*emission, 1)
        bsdf.inputs['Emission Strength'].default_value = strength
    return m


def shared_material(name, *args, **kw):
    """The scene's material of this name, made on first use."""
    return bpy.data.materials.get(name) or material(name, *args, **kw)


# =============================================================================
# Layer 1: primitives moved verbatim from the Legion and Colossus builds
# =============================================================================

class BaseKit:
    """Primitives and the seating ray-cast. A hull subclasses it and adds its palette
    in __init__ (call BaseKit.__init__ or set self.hull = [])."""

    def __init__(self):
        self.hull = []  # objects the downward ray-cast seats parts on

    def finish(self, obj, name, mat, bevel=0):
        obj.name = name
        obj.data.name = name
        obj.data.materials.append(mat)
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

    def cylinder(self, name, loc, radius, depth, mat, axis='Z', vertices=12, bevel=.08):
        bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
        obj = bpy.context.object
        if axis == 'X':
            obj.rotation_euler[1] = math.pi / 2
        elif axis == 'Y':
            obj.rotation_euler[0] = math.pi / 2
        return self.finish(obj, name, mat, bevel)

    def mesh(self, name, verts, faces, mat, bevel=0):
        """Flat-shaded polygon solid with outward normals."""
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(me)
        bm.free()
        me.update()
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        return self.finish(ob, name, mat, bevel)

    def empty(self, name, loc, angle_deg):
        """A plain-axes anchor at a slot: its +X points along the slot angle."""
        ob = bpy.data.objects.new(name, None)
        ob.empty_display_type = 'ARROWS'
        ob.empty_display_size = 3
        ob.location = loc
        ob.rotation_euler = (0, 0, math.radians(angle_deg))
        bpy.context.collection.objects.link(ob)
        return ob

    def surface(self, x, y, top=60):
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


def prism(kit, name, plan, profile, mat, bevel=.12):
    n = len(plan)
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


def loft_x(kit, name, stations, mat, bevel=.12):
    """Solid lofted along X between equal-count YZ sections [(x, [(y, z), ...]), ...]."""
    n = len(stations[0][1])
    verts = [(x, y, z) for x, sec in stations for y, z in sec]
    faces = [tuple(range(n)), tuple(range((len(stations)-1)*n, len(stations)*n))]
    for s in range(len(stations)-1):
        faces += [(s*n+i, s*n+(i+1) % n, (s+1)*n+(i+1) % n, (s+1)*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def octagon(y0, y1, z0, z1, ct, cb=None, ci=None, co=None):
    """Chamfered rectangle in YZ: top corners cut by ct, bottom by cb.
    ci/co override the top chamfer on the +Y / -Y side."""
    cb = ct if cb is None else cb
    hi = ct if ci is None else ci
    lo = ct if co is None else co
    return [(y0, z0+cb), (y0+cb, z0), (y1-cb, z0), (y1, z0+cb),
            (y1, z1-hi), (y1-hi, z1), (y0+lo, z1), (y0, z1-lo)]


def boolean(target, cutter, op='DIFFERENCE', keep=False):
    """MANIFOLD boolean, applied (the Legion's form: the target keeps its modifiers)."""
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
    mod.operation = op
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    if not keep:
        bpy.data.objects.remove(cutter, do_unlink=True)


def planar(obj):
    """Split the warped n-gons a boolean leaves, which the live bevel turns into spikes."""
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bmesh.ops.dissolve_degenerate(bm, dist=1e-4, edges=bm.edges[:])
    bmesh.ops.connect_verts_nonplanar(bm, angle_limit=0.0005, faces=bm.faces[:])
    bm.to_mesh(obj.data)
    bm.free()


def boolean_clean(target, cutter, op='DIFFERENCE', keep=False):
    """The Colossus's boolean: as boolean(), then planar() and the live bevel dropped.

    A live bevel on a cut mesh folds back over the short edges the cut leaves and turns
    faces inside out (the Colossus hatch cover's deck rendered as a dark inverted wedge).
    A cut mesh keeps its section chamfers and loses the modifier."""
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
    mod.operation = op
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    if not keep:
        bpy.data.objects.remove(cutter, do_unlink=True)
    planar(target)
    for m in [m for m in target.modifiers if m.type == 'BEVEL']:
        target.modifiers.remove(m)


def plane_cut(target, name, point, normal, size=200, clean=False):
    """Remove everything on the normal side of a plane: one flat facet.

    clean=True is the Colossus's form: the facet takes the part's own material and
    the cut goes through boolean_clean()."""
    normal = Vector(normal).normalized()
    bpy.ops.mesh.primitive_cube_add(size=1, location=Vector(point)+normal*size/2)
    cutter = bpy.context.object
    cutter.name = 'Temporary '+name+' cutter'
    cutter.scale = (size, size, size)
    cutter.rotation_euler = normal.to_track_quat('Z', 'Y').to_euler()
    if clean:
        cutter.data.materials.append(target.data.materials[0])
        boolean_clean(target, cutter)
    else:
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


def ngon(cy, cz, ry, rz, n=12, phase=None):
    """An n-sided YZ section about (cy, cz), flats top and bottom by default."""
    phase = math.pi/n if phase is None else phase
    return [(cy+ry*math.cos(phase+2*math.pi*i/n), cz+rz*math.sin(phase+2*math.pi*i/n)) for i in range(n)]


def pod(kit, name, stations, cy, cz, mat, n=12, bevel=.1):
    """Faceted body of revolution along X: stations [(x, ry, rz), ...] aft to fore.
    n=12 is a deliberate facet count for armoured drums; pass segments() for a true round."""
    return loft_x(kit, name, [(x, ngon(cy, cz, ry, rz, n)) for x, ry, rz in stations], mat, bevel)


def revolve_x(kit, name, profile, cy, cz, mat, n=24, bevel=0):
    """Thin-walled solid of revolution about an X line: profile [(x, r), ...] is the closed outline."""
    verts = [(x, cy+r*math.cos(2*math.pi*i/n), cz+r*math.sin(2*math.pi*i/n)) for x, r in profile for i in range(n)]
    m = len(profile)
    faces = []
    for a in range(m):
        b = (a+1) % m
        faces += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


# --- Accumulated shells and polyline sweeps (the Legion's armour runs) ---

class Acc:
    """Vertices and faces of several closed shells merged into one object."""
    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces):
        o = len(self.verts)
        self.verts += list(verts)
        self.faces += [tuple(o+i for i in f) for f in faces]

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.verts, self.faces, mat, bevel) if self.faces else None


def hexa(c):
    return c, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def box(x0, x1, y0, y1, z0, z1):
    return hexa([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                 (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)])


def obox(c, u, half_len, half_wid, z0, z1):
    """Box oriented along plan unit vector u about plan centre c."""
    (cx, cy), (ux, uy) = c, u
    vx, vy = -uy, ux
    pts = [(cx-ux*half_len-vx*half_wid, cy-uy*half_len-vy*half_wid),
           (cx+ux*half_len-vx*half_wid, cy+uy*half_len-vy*half_wid),
           (cx+ux*half_len+vx*half_wid, cy+uy*half_len+vy*half_wid),
           (cx-ux*half_len+vx*half_wid, cy-uy*half_len+vy*half_wid)]
    return hexa([(x, y, z0) for x, y in pts]+[(x, y, z1) for x, y in pts])


def cyl(x, y, r, z0, z1, n=16):
    verts = [(x+r*math.cos(2*math.pi*k/n), y+r*math.sin(2*math.pi*k/n), z) for z in (z0, z1) for k in range(n)]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return verts, faces


def rod_x(x0, x1, y, z, r, n=8):
    verts = [(x, y+r*math.cos(2*math.pi*(k+.5)/n), z+r*math.sin(2*math.pi*(k+.5)/n)) for x in (x0, x1) for k in range(n)]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return verts, faces


def _norm(x, y):
    length = (x*x+y*y)**.5 or 1.0
    return x/length, y/length


def normals(pts):
    """Mitred left normals (scaled for constant offset) of a plan polyline [(x, y, ...)]."""
    out = []
    n = len(pts)
    for i in range(n):
        a, b = pts[max(i-1, 0)], pts[min(i+1, n-1)]
        if 0 < i < n-1:
            d0 = _norm(pts[i][0]-a[0], pts[i][1]-a[1])
            d1 = _norm(b[0]-pts[i][0], b[1]-pts[i][1])
            n0, n1 = (-d0[1], d0[0]), (-d1[1], d1[0])
            m = _norm(n0[0]+n1[0], n0[1]+n1[1])
            k = 1/max(.5, m[0]*n0[0]+m[1]*n0[1])
            out.append((m[0]*k, m[1]*k))
        else:
            d = _norm(b[0]-a[0], b[1]-a[1])
            out.append((-d[1], d[0]))
    return out


def sweep(acc, pts, profiles, sign=1.0):
    """Closed solid swept along a 3D polyline [(x, y, z)]. A profile is a closed (d, dz) loop,
    d along the plan left normal times `sign`; pass one profile, or one per station."""
    if not isinstance(profiles[0][0], (tuple, list)):
        profiles = [profiles]*len(pts)
    nrm = normals(pts)
    m = len(profiles[0])
    verts = []
    for (x, y, z), (nx, ny), prof in zip(pts, nrm, profiles):
        verts += [(x+nx*d*sign, y+ny*d*sign, z+dz) for d, dz in prof]
    s = len(pts)
    faces = [tuple(range(m)), tuple(range((s-1)*m, s*m))]
    for j in range(s-1):
        faces += [(j*m+i, j*m+(i+1) % m, (j+1)*m+(i+1) % m, (j+1)*m+i) for i in range(m)]
    acc.add(verts, faces)


def arclen(pts):
    s = [0.0]
    for a, b in zip(pts, pts[1:]):
        s.append(s[-1]+math.dist(a[:2], b[:2]))
    return s


def point_at(pts, s_acc, s):
    for i in range(len(pts)-1):
        if s <= s_acc[i+1] or i == len(pts)-2:
            t = 0 if s_acc[i+1] == s_acc[i] else (s-s_acc[i])/(s_acc[i+1]-s_acc[i])
            t = min(max(t, 0.0), 1.0)
            return tuple(a+(b-a)*t for a, b in zip(pts[i], pts[i+1]))


def sub_polyline(pts, s0, s1):
    s = arclen(pts)
    out = [point_at(pts, s, s0)]
    out += [p for p, si in zip(pts, s) if s0+.05 < si < s1-.05]
    out.append(point_at(pts, s, s1))
    return out


def modules(pts, length, gap):
    """Split a polyline into modules of about `length`, separated by `gap`."""
    total = arclen(pts)[-1]
    n = max(1, round(total/length))
    step = total/n
    return [sub_polyline(pts, i*step+(gap/2 if i else 0), (i+1)*step-(gap/2 if i < n-1 else 0)) for i in range(n)]


def capped(pts, prof, cap=.7, shrink=.8):
    """Stations and per-station profiles giving a module chamfered end caps.
    `prof` is one (d, dz) loop or a function of the station point returning one."""
    s = arclen(pts)
    total = s[-1]
    cap = min(cap, total/3)
    fn = prof if callable(prof) else (lambda p: prof)

    def small(pr):
        z0 = min(dz for _, dz in pr)
        return [(d*shrink, z0+(dz-z0)*(1-(1-shrink)*.6)) for d, dz in pr]
    st = [point_at(pts, s, 0.0), point_at(pts, s, cap)]
    st += [p for p, si in zip(pts, s) if cap+.05 < si < total-cap-.05]
    st += [point_at(pts, s, total-cap), point_at(pts, s, total)]
    profs = [fn(p) for p in st]
    return st, [small(profs[0])]+profs[1:-1]+[small(profs[-1])]


def dist_seg(p, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx-ax, by-ay
    t = 0 if dx == dy == 0 else max(0, min(1, ((p[0]-ax)*dx+(p[1]-ay)*dy)/(dx*dx+dy*dy)))
    return math.hypot(p[0]-ax-dx*t, p[1]-ay-dy*t)


def dist_poly(p, pts):
    return min(dist_seg(p, a, b) for a, b in zip(pts, pts[1:]))


def inside(p, poly):
    x, y = p
    c = False
    for (ax, ay), (bx, by) in zip(poly, poly[1:]+poly[:1]):
        if (ay > y) != (by > y) and x < ax+(y-ay)*(bx-ax)/(by-ay):
            c = not c
    return c


def flip(pts):
    return [(p[0], -p[1])+tuple(p[2:]) for p in pts]


class Caster:
    """Ray casts against a frozen set of objects (evaluated, world space)."""
    def __init__(self, objs):
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        self.trees = []
        for ob in objs:
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            mw = ev.matrix_world
            self.trees.append(BVHTree.FromPolygons([mw @ v.co for v in me.vertices],
                                                   [tuple(p.vertices) for p in me.polygons]))
            ev.to_mesh_clear()

    def cast(self, origin, direction):
        best = None
        for t in self.trees:
            p, n, _, d = t.ray_cast(Vector(origin), Vector(direction).normalized())
            if p is not None and (best is None or d < best[2]):
                best = (p, n, d)
        return best

    def down(self, x, y, top=120.0):
        h = self.cast((x, y, top), (0, 0, -1))
        return h[0].z if h else None

    def up(self, x, y, bottom=-120.0):
        h = self.cast((x, y, bottom), (0, 0, 1))
        return h[0].z if h else None


# --- Bars, blocks and solids gathered into one object (the Colossus's fittings) ---

def beam(verts, faces, a, b, w, h=None, side=None):
    """Append a rectangular bar from a to b: w across `side`, h across the third axis."""
    h = w if h is None else h
    a, b = Vector(a), Vector(b)
    d = (b-a).normalized()
    s = Vector(side) if side is not None else (Vector((0, 0, 1)) if abs(d.z) < .9 else Vector((1, 0, 0)))
    s = (s-d*s.dot(d)).normalized()
    t = d.cross(s).normalized()
    n = len(verts)
    for p in (a, b):
        for i, j in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            verts.append(tuple(p+s*(i*w/2)+t*(j*h/2)))
    faces += [(n, n+1, n+2, n+3), (n+4, n+7, n+6, n+5),
              (n, n+4, n+5, n+1), (n+1, n+5, n+6, n+2), (n+2, n+6, n+7, n+3), (n+3, n+7, n+4, n)]


def block(verts, faces, lo, hi):
    """Append an axis-aligned box from corner lo to corner hi."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    beam(verts, faces, (x0, (y0+y1)/2, (z0+z1)/2), (x1, (y0+y1)/2, (z0+z1)/2), y1-y0, z1-z0, (0, 1, 0))


def solid(kit, name, verts, faces, mat, bevel=0):
    """One object holding every bar appended to verts/faces, normals recalculated outward."""
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    return kit.finish(ob, name, mat, bevel)


def seat(kit, x, yy, fallback):
    """Height of the hull under (x, yy), or the fallback."""
    hit = kit.surface(x, yy)
    return hit[0].z if hit else fallback


def lamp(kit, name, loc, r=.4, mat=None):
    """The Colossus's bare lamp body (10-sided, emissive). New work uses lamp_fitting()."""
    return kit.cylinder(name, loc, r, r*1.1, mat or kit.lamp, vertices=10, bevel=.06)


def frame(axis):
    """Two unit vectors perpendicular to axis: (axis, u, w)."""
    axis = Vector(axis).normalized()
    s = Vector((0, 0, 1)) if abs(axis.z) < .9 else Vector((1, 0, 0))
    u = (s-axis*s.dot(axis)).normalized()
    return axis, u, axis.cross(u)


def disc(v, f, c, axis, r, depth, n=24):
    """Append a closed n-sided cylinder centred on c along axis."""
    a, u, w = frame(axis)
    c = Vector(c)
    k = len(v)
    for e in (-depth/2, depth/2):
        for i in range(n):
            t = 2*math.pi*i/n
            v.append(tuple(c+a*e+u*(r*math.cos(t))+w*(r*math.sin(t))))
    f.append(tuple(range(k+n-1, k-1, -1)))
    f.append(tuple(range(k+n, k+2*n)))
    f += [(k+i, k+(i+1) % n, k+n+(i+1) % n, k+n+i) for i in range(n)]


def annulus(v, f, c, axis, r0, r1, depth, n=24):
    """Append a closed ring (washer) centred on c along axis."""
    a, u, w = frame(axis)
    c = Vector(c)
    k = len(v)
    for e, r in ((-depth/2, r0), (-depth/2, r1), (depth/2, r1), (depth/2, r0)):
        for i in range(n):
            t = 2*math.pi*i/n
            v.append(tuple(c+a*e+u*(r*math.cos(t))+w*(r*math.sin(t))))
    for ring in range(4):
        nr = (ring+1) % 4
        f += [(k+ring*n+i, k+ring*n+(i+1) % n, k+nr*n+(i+1) % n, k+nr*n+i) for i in range(n)]


def revolve_z(kit, name, profile, mat, n=32):
    """Closed (r, z) outline revolved about the local Z axis."""
    verts = [(r*math.cos(2*math.pi*i/n), r*math.sin(2*math.pi*i/n), z) for r, z in profile for i in range(n)]
    m = len(profile)
    faces = []
    for a in range(m):
        b = (a+1) % m
        faces += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
    return solid(kit, name, verts, faces, mat)


def revolve_at(kit, name, profile, cx, cy, z, mat, n=24):
    """Closed (r, dz) outline revolved about a vertical axis at (cx, cy, z)."""
    verts = [(cx+r*math.cos(2*math.pi*i/n), cy+r*math.sin(2*math.pi*i/n), z+dz) for r, dz in profile for i in range(n)]
    m = len(profile)
    faces = []
    for a in range(m):
        b = (a+1) % m
        faces += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat)


def revolve_dir(kit, name, profile, origin, axis, mat, n=24, bevel=0):
    """Closed (t, r) outline revolved about the line origin + t*axis."""
    o, a = Vector(origin), Vector(axis).normalized()
    u = a.cross(Vector((0, 0, 1)) if abs(a.z) < .9 else Vector((1, 0, 0))).normalized()
    w = a.cross(u)
    verts = []
    for t, r in profile:
        for i in range(n):
            ang = 2*math.pi*(i+.5)/n
            verts.append(tuple(o+a*t+u*(r*math.cos(ang))+w*(r*math.sin(ang))))
    m = len(profile)
    faces = []
    for p in range(m):
        q = (p+1) % m
        faces += [(p*n+i, p*n+(i+1) % n, q*n+(i+1) % n, q*n+i) for i in range(n)]
    return kit.mesh(name, verts, faces, mat, bevel)


def cut_transfer(kit, target, name, v, f, mat, cutter=None):
    """Boolean difference whose cut faces keep the cutter's material (a dark reveal)."""
    cutter = cutter or solid(kit, 'Temporary '+name+' cutter', v, f, mat)
    if mat.name not in [m.name for m in target.data.materials]:
        target.data.materials.append(mat)
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
    mod.operation = 'DIFFERENCE'
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    try:
        mod.material_mode = 'TRANSFER'
    except (AttributeError, TypeError):
        pass
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)
    planar(target)


def rail_run(v, f, pts, z0, height=2.2, pitch=2.8, post=.22, top=.2):
    """Stanchions along a polyline [(x, y), ...] standing on z0 (or per-point z), with top and knee rails.
    Defaults are the Colossus's (0.5 m/u): a 1.1 m rail, stanchions every 1.4 m."""
    pts = [p if len(p) == 3 else (p[0], p[1], z0) for p in pts]
    for (ax, ay, az), (bx, by, bz) in zip(pts, pts[1:]):
        a, b = Vector((ax, ay, az)), Vector((bx, by, bz))
        n = max(1, math.ceil((b-a).length/pitch))
        for i in range(n+1):
            p = a+(b-a)*(i/n)
            beam(v, f, p, p+Vector((0, 0, height)), post)
        beam(v, f, a+Vector((0, 0, height)), b+Vector((0, 0, height)), top)
        beam(v, f, a+Vector((0, 0, height/2)), b+Vector((0, 0, height/2)), top*.8)


def wall_vent(kit, name, c, nrm, hx, width, height, slats=5, backing=None, louvres=None):
    """Louvred grille on a vertical wall: dark backing, angled slats, a frame."""
    c, nrm, hx = Vector(c), Vector(nrm).normalized(), Vector(hx).normalized()
    up = Vector((0, 0, 1))
    v, f = [], []
    beam(v, f, c-hx*width/2+nrm*.1, c+hx*width/2+nrm*.1, .3, height, nrm)
    solid(kit, name+' backing', v, f, backing or kit.dark)
    v, f = [], []
    for i in range(slats):
        z = -height/2+.3+(height-.6)*i/(slats-1)
        p = c+up*z+nrm*.4
        beam(v, f, p-hx*(width/2-.15), p+hx*(width/2-.15), .5, .14, nrm+up*.6)
    for e in (-1, 1):
        beam(v, f, c+hx*e*(width/2+.15)-up*(height/2+.3)+nrm*.3, c+hx*e*(width/2+.15)+up*(height/2+.3)+nrm*.3, .4, .3, nrm)
        beam(v, f, c+up*e*(height/2+.15)-hx*(width/2+.3)+nrm*.3, c+up*e*(height/2+.15)+hx*(width/2+.3)+nrm*.3, .4, .3, nrm)
    solid(kit, name+' louvres', v, f, louvres or kit.steel, bevel=.03)


def wall_cover(kit, name, c, nrm, hx, width, height, mat, bolts=None):
    """Bolted service cover on a vertical wall, with a grab handle."""
    c, nrm, hx = Vector(c), Vector(nrm).normalized(), Vector(hx).normalized()
    up = Vector((0, 0, 1))
    v, f = [], []
    beam(v, f, c-hx*width/2+nrm*.15, c+hx*width/2+nrm*.15, .3, height, nrm)
    solid(kit, name, v, f, mat, bevel=.06)
    v, f = [], []
    nx, nz = max(2, round(width/.9)), max(2, round(height/.9))
    pts = set()
    for i in range(nx+1):
        for e in (-1, 1):
            pts.add((-width/2+.3+(width-.6)*i/nx, e*(height/2-.3)))
    for j in range(nz+1):
        for e in (-1, 1):
            pts.add((e*(width/2-.3), -height/2+.3+(height-.6)*j/nz))
    for a, b in pts:
        p = c+hx*a+up*b+nrm*.3
        beam(v, f, p, p+nrm*.16, .26)
    for e in (-1, 1):
        beam(v, f, c+hx*e*.5+nrm*.3, c+hx*e*.5+nrm*.75, .16)
    beam(v, f, c-hx*.6+nrm*.75, c+hx*.6+nrm*.75, .16)
    solid(kit, name+' bolts', v, f, bolts or kit.steel)


def bolted_plate(kit, name, cx, cy, z, sx, sy, mat, pitch=1.4, t=.35, down=False, bolts=None):
    """A service cover laid on a deck at height z (or under a keel, down=True), with a ring of bolt heads."""
    k = -1 if down else 1
    kit.box(name, (cx, cy, z+k*t/2), (sx, sy, t), mat, bevel=.08)
    v, f = [], []
    nx, ny = max(2, round(sx/pitch)), max(2, round(sy/pitch))
    pts = set()
    for i in range(nx+1):
        for yy in (-1, 1):
            pts.add((cx-sx/2+.45+(sx-.9)*i/nx, cy+yy*(sy/2-.45)))
    for j in range(ny+1):
        for xx in (-1, 1):
            pts.add((cx+xx*(sx/2-.45), cy-sy/2+.45+(sy-.9)*j/ny))
    for px, py in pts:
        block(v, f, (px-.15, py-.15, z+k*t-(.18 if down else 0)), (px+.15, py+.15, z+k*t+(0 if down else .18)))
    solid(kit, name+' bolts', v, f, bolts or kit.steel)


def louvre_vent(kit, name, cx, cy, z, sx, sy, n=5, box_mat=None, blade_mat=None):
    """A dark vent box with pale louvre blades across X."""
    kit.box(name, (cx, cy, z+.5), (sx, sy, 1.0), box_mat or kit.dark, bevel=.08)
    v, f = [], []
    for i in range(n):
        px = cx-sx/2+.5+(sx-1)*i/(n-1)
        beam(v, f, (px, cy-sy/2+.3, z+1.15), (px, cy+sy/2-.3, z+1.15), .35, .3, (1, 0, 0))
    block(v, f, (cx-sx/2-.15, cy-sy/2-.15, z), (cx+sx/2+.15, cy+sy/2+.15, z+.25))
    solid(kit, name+' louvres', v, f, blade_mat or kit.pale)


def space_frame(kit, name, x0, x1, y0, y1, z0, z1, bx, by, chord=.9, lace=.5, mat=None, grating=None):
    """Open lattice box: top and bottom chord grids, perimeter verticals and Warren lacing in every
    vertical panel, plus a dark walkway grating at mid-depth that keeps the plan opaque."""
    xs = [x0+(x1-x0)*i/bx for i in range(bx+1)]
    ys = [y0+(y1-y0)*j/by for j in range(by+1)]
    v, f = [], []
    for z in (z0, z1):
        for yy in ys:
            beam(v, f, (x0, yy, z), (x1, yy, z), chord)
        for xx in xs:
            beam(v, f, (xx, y0, z), (xx, y1, z), chord)
    for xx in xs:
        for yy in ys:
            if xx in (x0, x1) or yy in (y0, y1):
                beam(v, f, (xx, yy, z0), (xx, yy, z1), chord)
    for yy in ys:      # lacing in the X-running panels
        for i in range(bx):
            a, b = (xs[i], z0), (xs[i+1], z1)
            if i % 2:
                a, b = (xs[i], z1), (xs[i+1], z0)
            beam(v, f, (a[0], yy, a[1]), (b[0], yy, b[1]), lace)
    for xx in xs:      # lacing in the Y-running panels
        for j in range(by):
            a, b = (ys[j], z0), (ys[j+1], z1)
            if j % 2:
                a, b = (ys[j], z1), (ys[j+1], z0)
            beam(v, f, (xx, a[0], a[1]), (xx, b[0], b[1]), lace)
    frame_ob = solid(kit, name, v, f, mat or kit.steel)
    zm = (z0+z1)/2
    v, f = [], []
    block(v, f, (x0, y0, zm-.2), (x1, y1, zm+.2))
    solid(kit, name+' walkway grating', v, f, grating or kit.dark)
    return frame_ob, zm


def pipes(kit, name, runs, r, mat, n=8):
    """Pipe runs [(a, b), ...] joined into one object (n-sided section; the Colossus used 8).
    New work passes n=Scale.n(r)."""
    v, f = [], []
    for a, b in runs:
        a, b = Vector(a), Vector(b)
        d = (b-a).normalized()
        s = Vector((0, 0, 1)) if abs(d.z) < .9 else Vector((1, 0, 0))
        s = (s-d*s.dot(d)).normalized()
        t = d.cross(s)
        k0 = len(v)
        for p in (a, b):
            for k in range(n):
                ang = 2*math.pi*k/n+math.pi/n
                v.append(tuple(p+s*(r*math.cos(ang))+t*(r*math.sin(ang))))
        f += [tuple(range(k0+n-1, k0-1, -1)), tuple(range(k0+n, k0+2*n))]
        f += [(k0+k, k0+(k+1) % n, k0+n+(k+1) % n, k0+n+k) for k in range(n)]
    return solid(kit, name, v, f, mat)


def studs(kit, name, points, axis, r, h, mat, n=6):
    """Bolt heads: one mesh of short n-gon prisms at points, standing along axis ('X'|'Y'|'Z', sign)."""
    ax, sign = axis
    verts, faces = [], []
    for p in points:
        base = len(verts)
        for lvl in (0, h*sign):
            for k in range(n):
                a = 2*math.pi*k/n
                c, s = r*math.cos(a), r*math.sin(a)
                if ax == 'Z':
                    verts.append((p[0]+c, p[1]+s, p[2]+lvl))
                elif ax == 'Y':
                    verts.append((p[0]+c, p[1]+lvl, p[2]+s))
                else:
                    verts.append((p[0]+lvl, p[1]+c, p[2]+s))
        faces.append(tuple(base+k for k in range(n)))
        faces.append(tuple(base+n+k for k in range(n)))
        faces += [(base+k, base+(k+1) % n, base+n+(k+1) % n, base+n+k) for k in range(n)]
    return kit.mesh(name, verts, faces, mat)


def rod(kit, name, p0, p1, r, mat, n=8):
    """A cylinder primitive between two points."""
    p0, p1 = Vector(p0), Vector(p1)
    d = p1-p0
    bpy.ops.mesh.primitive_cylinder_add(vertices=n, radius=r, depth=d.length, location=(p0+p1)/2)
    ob = bpy.context.object
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    return kit.finish(ob, name, mat, 0)


def join(parts, name):
    """Join objects into the first; the result takes `name`."""
    bpy.ops.object.select_all(action='DESELECT')
    for p in parts:
        p.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    ob.name = ob.data.name = name
    return ob


def bent_rail(kit, name, pts, r, mat, n=8):
    """A bent bar through pts: one rod per leg, gathered into one object."""
    return join([rod(kit, name+' leg', p, q, r, mat, n) for p, q in zip(pts, pts[1:])], name)


def rcs_quad(kit, name, x, y, z, out, fore_sign, mats):
    """A low-tech RCS quad: pedestal, bolted housing and four bells (fore/aft, outboard, up, down-slant).
    Colossus sizes at 0.5 m/u: a 1.2 m housing, 0.43 m bells. mats['lip'] colours bells 2 and 4."""
    revolve_at(kit, name+' pedestal', [(0, -1.5), (1.15, -1.5), (1.15, .2), (.9, .45), (0, .45)], x, y, z,
               kit.steel, n=24)
    hz = z+1.3
    kit.mesh(name+' housing', *_block_mesh(x-1.2, x+1.2, y-1.2, y+1.2, z+.3, z+2.3), kit.pale, .18)
    studs(kit, name+' housing bolts', [(x+dx, y+dy, z+2.3) for dx in (-.85, .85) for dy in (-.85, .85)],
          ('Z', 1), .13, .14, kit.steel)
    dirs = [(fore_sign, 0, 0), (0, out, 0), (0, 0, 1), (0, out*.7, -.7)]
    for k, d in enumerate(dirs, 1):
        dv = Vector(d).normalized()
        base = Vector((x, y, hz))+Vector((dv.x*1.15, dv.y*1.15, dv.z*1.0))
        revolve_dir(kit, f'{name} bell {k}', [(0, 0), (0, .32), (.25, .3), (.85, .55), (.85, .45), (.3, .2), (.3, 0)],
                    base, dv, kit.steel if k % 2 else mats['lip'], n=16)


def _block_mesh(x0, x1, y0, y1, z0, z1):
    v = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    f = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    return v, f


def lamp_housing(kit, name, x, y, z, aim, mat_lens):
    """An unlit work-lamp housing on a bracket, its lens facing along aim (no lights layer)."""
    a = Vector(aim).normalized()
    rod(kit, name+' bracket', (x, y, z-.2), (x, y, z+1.0), .14, kit.steel, n=8)
    p = Vector((x, y, z+1.3))
    revolve_dir(kit, name+' housing', [(-.55, 0), (-.55, .38), (-.2, .5), (.25, .5), (.25, 0)], p, a, kit.steel, n=20)
    revolve_dir(kit, name+' lens', [(.2, 0), (.2, .42), (.32, .42), (.32, 0)], p, a, mat_lens, n=20)


# =============================================================================
# Layer 2: metric builders. Sizes in metres; S is the hull's Scale.
# =============================================================================

def smooth(obj, angle=35.0):
    """Shade smooth with edges sharper than `angle` degrees kept sharp (auto smooth).
    Use on every curved body (domes, dishes, bells, tanks, pipes); never on plate."""
    me = obj.data
    me.shade_smooth()
    if hasattr(me, 'set_sharp_from_angle'):
        me.set_sharp_from_angle(angle=math.radians(angle))
    return obj


def bevel(obj, width, segments=1, angle=30.0, harden=False):
    """Angle-limited bevel modifier, width in hull units (Scale.bevel(kind)).

    One segment is the reference look: a crisp chamfer that catches a highlight.
    Two or three only on parts seen within a few metres (a door frame, a hand rail end)."""
    b = obj.modifiers.new('Plate edge chamfer', 'BEVEL')
    b.width, b.segments, b.limit_method = width, segments, 'ANGLE'
    b.angle_limit = math.radians(angle)
    if harden:
        b.harden_normals = True
    return b


def seam_cut(kit, target, name, boxes, groove_mat):
    """Cut seam grooves into a plate: boxes [(lo, hi), ...] in hull units, the cut faces dark.

    Guide: seams 0.25-0.35 m wide on a module hull, 0.6-1.0 m between capital armour
    modules, cut 0.15-0.3 m deep; never painted on."""
    v, f = [], []
    for lo, hi in boxes:
        block(v, f, lo, hi)
    cut_transfer(kit, target, name, v, f, groove_mat)


def skin_groove(kit, target, name, plan, z0, z1, keel, deck, keel_c, shoulder_c, depth, groove_mat):
    """A strata groove round a prism's walls between z0 and z1, `depth` deep: the slab
    minus the solid inset by depth, so only a skin band is cut (Colossus bridge, aft body)."""
    lo_x = min(p[0] for p in plan)-5
    hi_x = max(p[0] for p in plan)+5
    lo_y = min(p[1] for p in plan)-5
    hi_y = max(p[1] for p in plan)+5
    v, f = [], []
    block(v, f, (lo_x, lo_y, z0), (hi_x, hi_y, z1))
    shell = solid(kit, 'Temporary '+name+' slab', v, f, groove_mat)
    core = prism(kit, 'Temporary '+name+' core', inset(plan, depth), section(keel-2, deck+2, keel_c, shoulder_c),
                 groove_mat, bevel=0)
    boolean(shell, core)
    cut_transfer(kit, target, name, None, None, groove_mat, cutter=shell)


def armour_run(kit, name, pts, sign, base, upper, mats, length, gap, cap=.45, shrink=.88, underlay=None):
    """The Legion's armour belt along a plan polyline: modules of about `length` with
    `gap` seams, each a base course and a stepped upper course with chamfered end caps,
    over a dark underlay that shows in the seams. Profiles are closed (d, z) loops, +d
    toward `sign`. mats = (armour, underlay). Returns (armour, underlay) objects."""
    acc, under = Acc(), Acc()
    pts3 = [(x, y, 0.0) for x, y in pts]
    for mod in modules(pts3, length, gap):
        for prof in (base, upper):
            st, profs = capped(mod, prof, cap=cap, shrink=shrink)
            sweep(acc, st, profs, sign)
    if underlay:
        sweep(under, pts3, underlay)
    return acc.build(kit, name, mats[0]), under.build(kit, name+' seam underlay', mats[1])


def window_row(kit, S, name, centre, normal, along, pane_w, pane_h, count, mats, lit=(),
               target=None, groove=None, recess_m=.33, mullion_m=(.4, .22), surround_m=.2,
               visor_m=None, wipers=False):
    """A row of framed panes set into a wall recess (the Colossus windscreen and wing ports).

    centre: the wall point at the row's middle; normal: the wall's outward normal;
    along: the direction the row runs. pane_w x pane_h in metres. mats: dict with
    'glass' (dark, never emissive), 'lit' (emissive, used only for panes in `lit`),
    'frame' (mullions, sill, head), 'surround' (proud trim). target/groove cut the
    recess into the wall when given. Returns the objects built."""
    n = Vector(normal).normalized()
    hx = Vector(along).normalized()
    up = n.cross(hx).normalized()
    if up.z < 0:
        up = -up
    c = Vector(centre)
    pw, ph = S.u(pane_w), S.u(pane_h)
    w = pw*count
    depth = S.u(recess_m)
    mw, md = S.u(mullion_m[0]), S.u(mullion_m[1])
    sw = S.u(surround_m)
    out = []
    if target is not None and groove is not None:
        v, f = [], []
        beam(v, f, c-hx*(w/2+mw), c+hx*(w/2+mw), depth*2, ph+mw*2, n)
        cut_transfer(kit, target, name+' recess', v, f, groove)
    v, f, lv, lf = [], [], [], []
    for k in range(count):
        p = c-n*(depth*.7)+hx*(-w/2+pw*(k+.5))
        beam(lv if k in lit else v, lf if k in lit else f, p-hx*(pw/2), p+hx*(pw/2), S.u(.03), ph, n)
    if f:
        out.append(solid(kit, name+' glass', v, f, mats['glass']))
    if lf:
        out.append(solid(kit, name+' lit panes', lv, lf, mats['lit']))
    v, f = [], []
    for k in range(count+1):                                   # mullions, deep in the recess
        p = c-n*(depth*.4)+hx*(-w/2+pw*k)
        beam(v, f, p-up*(ph/2+mw/2), p+up*(ph/2+mw/2), mw, md, n)
    for e in (-1, 1):                                          # sill and head
        p = c-n*(depth*.4)+up*e*(ph/2+mw/4)
        beam(v, f, p-hx*(w/2+mw/2), p+hx*(w/2+mw/2), mw, md*1.2, n)
    out.append(solid(kit, name+' frame', v, f, mats['frame'], bevel=S.bevel('small')))
    v, f = [], []                                              # proud surround round the recess
    for e in (-1, 1):
        p = c+n*(sw*.3)+hx*e*(w/2+mw+sw/2)
        beam(v, f, p-up*(ph/2+mw+sw), p+up*(ph/2+mw+sw), sw, sw*.8, n)
        p = c+n*(sw*.3)+up*e*(ph/2+mw+sw/2)
        beam(v, f, p-hx*(w/2+mw+sw), p+hx*(w/2+mw+sw), sw, sw*.8, n)
    out.append(solid(kit, name+' surround', v, f, mats['surround'], bevel=S.bevel('fitting')))
    if visor_m:                                                # an armoured brow over the row
        v, f = [], []
        p = c+up*(ph/2+mw+sw*1.5)+n*(S.u(visor_m)/2)
        beam(v, f, p-hx*(w/2+mw+sw*2), p+hx*(w/2+mw+sw*2), S.u(visor_m), S.u(.3), up)
        out.append(solid(kit, name+' brow', v, f, mats['surround'], bevel=S.bevel('fitting')))
    if wipers:
        v, f = [], []
        for k in range(count):
            p = c-n*(depth*.5)+hx*(-w/2+pw*(k+.5))-up*(ph/2-S.u(.1))
            beam(v, f, p-hx*pw*.3, p+hx*pw*.3+up*ph*.3, S.u(.05), S.u(.07), n)
        out.append(solid(kit, name+' wipers', v, f, mats['frame']))
    return out


def round_port(kit, S, name, centre, normal, radius_m, mats, target=None, groove=None, bolts=8, deadlight=True):
    """A bolted round port: recess, dark glass, a rim ring, bolt heads and a dead-light lug
    (the Colossus annex ports: 0.8 m glass, 1.15 m rim, eight bolts)."""
    n = Vector(normal).normalized()
    c = Vector(centre)
    r = S.u(radius_m)
    seg = S.n(r*1.45)
    if target is not None and groove is not None:
        v, f = [], []
        disc(v, f, c, n, r, r*1.1, n=seg)
        cut_transfer(kit, target, name+' recess', v, f, groove)
    v, f = [], []
    disc(v, f, c-n*(r*.37), n, r*1.02, r*.19, n=seg)
    glass = solid(kit, name+' glass', v, f, mats['glass'])
    v, f = [], []
    annulus(v, f, c+n*(r*.12), n, r*.97, r*1.44, r*.5, n=seg)
    rim = solid(kit, name+' rim', v, f, mats['frame'], bevel=S.bevel('small'))
    v, f = [], []
    _, u, w = frame(n)
    for i in range(bolts):
        t = 2*math.pi*(i+.5)/bolts
        q = c+n*(r*.37)+u*(r*1.21*math.cos(t))+w*(r*1.21*math.sin(t))
        beam(v, f, q, q+n*(r*.17), r*.2)
    if deadlight:
        beam(v, f, c+n*(r*.19)+u*(r*1.75)-w*(r*.5), c+n*(r*.19)+u*(r*1.75)+w*(r*.5), r*.37, r*.37)
    hw = solid(kit, name+' bolts', v, f, mats['frame'])
    return glass, rim, hw


def deck_hatch(kit, S, name, centre, size_m, mats, axis=(1, 0, 0), skylight=False):
    """A deck hatch: coaming, lid, hinge pin along one edge, two dogs and a handle
    (the Colossus annex roof hatch: 1.9 m coaming, 0.4 m high). skylight=True gives
    a glazed lid with guard bars instead (mats['glass'] dark)."""
    c = Vector(centre)
    hx = Vector(axis).normalized()
    hy = Vector((0, 0, 1)).cross(hx).normalized()
    sx, sy = S.u(size_m[0]), S.u(size_m[1])
    h = S.u(.4)
    t = S.u(.15)
    v, f = [], []
    beam(v, f, c-hx*sx/2+Vector((0, 0, h/2)), c+hx*sx/2+Vector((0, 0, h/2)), sy, h, hy)
    coaming = solid(kit, name+' coaming', v, f, mats['frame'], bevel=S.bevel('fitting'))
    v, f = [], []
    lid_c = c+Vector((0, 0, h+t/2))
    if skylight:
        beam(v, f, lid_c-hx*(sx/2-S.u(.2)), lid_c+hx*(sx/2-S.u(.2)), sy-S.u(.4), t*.6, hy)
        lid = solid(kit, name+' glass', v, f, mats['glass'])
        v, f = [], []
        for k in (-1, 1):
            p = lid_c+hx*k*sx/6+Vector((0, 0, t))
            beam(v, f, p-hy*(sy/2-S.u(.1)), p+hy*(sy/2-S.u(.1)), S.u(.08), S.u(.07))
        solid(kit, name+' guard bars', v, f, mats['frame'], bevel=S.bevel('small'))
        return coaming, lid
    beam(v, f, lid_c-hx*(sx/2-S.u(.1)), lid_c+hx*(sx/2-S.u(.1)), sy-S.u(.2), t, hy)
    lid = solid(kit, name+' lid', v, f, mats['lid'], bevel=S.bevel('fitting'))
    v, f = [], []
    pin = c-hx*(sx/2-S.u(.05))+Vector((0, 0, h+t*.5))
    disc(v, f, pin, hy, S.u(.06), sy*.8, n=S.n(S.u(.06)))
    for e in (-.5, .5):
        p = c+hx*(sx/2+S.u(.15))+hy*e*sy*.55+Vector((0, 0, h*.9))
        beam(v, f, p-Vector((0, 0, h*.4)), p+Vector((0, 0, h*.4)), S.u(.15), S.u(.3), hy)
    p = c+hx*(sx/2-S.u(.35))+Vector((0, 0, h+t*1.4))
    beam(v, f, p-hy*S.u(.35), p+hy*S.u(.35), S.u(.07), S.u(.12))
    fittings = solid(kit, name+' fittings', v, f, mats['frame'])
    return coaming, lid, fittings


def wall_door(kit, S, name, sill, normal, along, mats, size_m=PERSON_M['door'], jamb_m=.25, step=True, rails=True):
    """A personnel door in a wall: the leaf set back, jambs and head proud, a step and
    hand rails (the Colossus aft door: 1.1 x 2.3 m leaf, 0.25 m jambs, 1.1 m rails;
    the Legion's flank hatches are 1.46 x 2.07 m). sill: the wall point under the door's middle."""
    n = Vector(normal).normalized()
    hx = Vector(along).normalized()
    up = Vector((0, 0, 1))
    s0 = Vector(sill)
    w, h = S.u(size_m[0]), S.u(size_m[1])
    j = S.u(jamb_m)
    v, f = [], []
    beam(v, f, s0-n*S.u(.05), s0-n*S.u(.05)+up*h, w, S.u(.12), n)
    leaf = solid(kit, name+' leaf', v, f, mats['leaf'], bevel=S.bevel('small'))
    v, f = [], []
    for e in (-1, 1):
        p = s0+hx*e*(w/2+j/2)+n*S.u(.1)
        beam(v, f, p-up*S.u(.05), p+up*(h+j), j, S.u(.25), n)
    p = s0+up*(h+j/2)+n*S.u(.1)
    beam(v, f, p-hx*(w/2+j), p+hx*(w/2+j), j, S.u(.25), up)
    fr = solid(kit, name+' frame', v, f, mats['frame'], bevel=S.bevel('fitting'))
    v, f = [], []                                     # hinge knuckles and the lever handle
    for z in (.2, .8):
        disc(v, f, s0-hx*(w/2-S.u(.05))+up*h*z+n*S.u(.05), up, S.u(.05), S.u(.18), n=S.n(S.u(.05)))
    p = s0+hx*(w/2-S.u(.15))+up*S.u(1.05)+n*S.u(.08)
    beam(v, f, p, p-hx*S.u(.18), S.u(.04), S.u(.04))
    hw = solid(kit, name+' hardware', v, f, mats['frame'])
    out = [leaf, fr, hw]
    if step:
        v, f = [], []
        beam(v, f, s0+n*S.u(.35)-hx*(w/2+j), s0+n*S.u(.35)+hx*(w/2+j), S.u(.7), S.u(.15), up)
        out.append(solid(kit, name+' step', v, f, mats['frame'], bevel=S.bevel('small')))
    if rails:
        v, f = [], []
        rh = S.u(PERSON_M['rail'])
        for e in (-1, 1):
            a = s0+hx*e*(w/2+j+S.u(.15))+n*S.u(.15)
            b = a+n*S.u(.9)
            for p in (a, b):
                beam(v, f, p, p+up*rh, S.u(.05))
            beam(v, f, a+up*rh, b+up*rh, S.u(.05))
            beam(v, f, a+up*rh/2, b+up*rh/2, S.u(.04))
        out.append(solid(kit, name+' hand rails', v, f, mats['frame']))
    return out


def ladder(kit, S, name, pts, mat, width_m=PERSON_M['ladder_width'], rung_m=PERSON_M['rung_pitch'],
           stile_m=.05, standoff=None):
    """Stiles along a path pts [(x, y, z), ...] and rungs at rung_m pitch, across `width` along
    the normal of the first leg in plan. standoff: (wall_point_offset_vector, every_m) brackets."""
    pts = [Vector(p) for p in pts]
    d0 = (pts[1]-pts[0]).normalized()
    side = Vector((0, 0, 1)).cross(d0)
    if side.length < .1:
        side = Vector((1, 0, 0))
    side.normalize()
    half = S.u(width_m)/2
    st = S.u(stile_m)
    v, f = [], []
    for e in (-1, 1):
        for a, b in zip(pts, pts[1:]):
            beam(v, f, a+side*e*half, b+side*e*half, st, st)
    for a, b in zip(pts, pts[1:]):
        L = (b-a).length
        k = 1
        while k*S.u(rung_m) < L-S.u(.05):
            p = a+(b-a)*(k*S.u(rung_m)/L)
            beam(v, f, p-side*half, p+side*half, st*.75, st*.75)
            k += 1
    if standoff:
        off, every = standoff
        for a, b in zip(pts, pts[1:]):
            L = (b-a).length
            for k in range(1, max(2, int(L/S.u(every)))+0):
                p = a+(b-a)*(k/max(2, int(L/S.u(every))))
                for e in (-1, 1):
                    beam(v, f, p+side*e*half, p+side*e*half+Vector(off), st*.8, st*.8)
    return solid(kit, name, v, f, mat)


def handrail(kit, S, name, pts, z0, mat, height_m=PERSON_M['rail'], pitch_m=PERSON_M['stanchion_pitch'],
             post_m=.06, rail_m=.05):
    """A 1.1 m rail with stanchions every 1.4 m and a knee rail (rail_run in metres)."""
    v, f = [], []
    rail_run(v, f, pts, z0, height=S.u(height_m), pitch=S.u(pitch_m), post=S.u(post_m), top=S.u(rail_m))
    return solid(kit, name, v, f, mat)


def mushroom_vent(kit, S, name, centres, mats, radius_m=.42, height_m=.85):
    """Mushroom vents on a deck: stalk, a dark gap and a cap (Colossus annex roof)."""
    r = S.u(radius_m)
    h = S.u(height_m)
    n = S.n(r)
    v, f = [], []
    for c in centres:
        c = Vector(c)
        disc(v, f, c+Vector((0, 0, h*.35)), (0, 0, 1), r*.41, h*.82, n=n)
        disc(v, f, c+Vector((0, 0, h*.85)), (0, 0, 1), r, h*.2, n=n)
    caps = solid(kit, name, v, f, mats['frame'], bevel=S.bevel('small'))
    v, f = [], []
    for c in centres:
        disc(v, f, Vector(c)+Vector((0, 0, h*.68)), (0, 0, 1), r*.65, h*.18, n=n)
    gaps = solid(kit, name+' gap', v, f, mats['dark'])
    return caps, gaps


def rcs_cluster(kit, S, name, base, outward, fore_sign, mats, housing_m=1.2, bell_m=.43, bells=4):
    """An RCS block at any scale: a round pedestal, a bolted housing and bells
    (fore/aft, outboard, up, down-slant), every round from the segment rule.
    mats: 'housing', 'steel', 'lip' (alternate bells). The Colossus quad is the model."""
    b = Vector(base)
    hs = S.u(housing_m)/2
    out = Vector(outward).normalized()
    pr = hs*.96
    revolve_at(kit, name+' pedestal', [(0, -hs*1.25), (pr, -hs*1.25), (pr, hs*.17), (pr*.78, hs*.37), (0, hs*.37)],
               b.x, b.y, b.z, mats['steel'], n=S.n(pr))
    ob = kit.mesh(name+' housing', *_block_mesh(b.x-hs, b.x+hs, b.y-hs, b.y+hs, b.z+hs*.25, b.z+hs*1.92),
                  mats['housing'])
    bevel(ob, S.bevel('fitting'))
    studs(kit, name+' housing bolts', [(b.x+dx, b.y+dy, b.z+hs*1.92) for dx in (-hs*.7, hs*.7) for dy in (-hs*.7, hs*.7)],
          ('Z', 1), S.u(.065), S.u(.07), mats['steel'], n=6)
    dirs = [Vector((fore_sign, 0, 0)), out, Vector((0, 0, 1)), (out*.7+Vector((0, 0, -.7)))][:bells]
    hz = b.z+hs*1.08
    br = S.u(bell_m)
    for k, d in enumerate(dirs, 1):
        dv = d.normalized()
        p0 = Vector((b.x, b.y, hz))+dv*hs*.96
        prof = [(0, 0), (0, br*.38), (br*.3, br*.35), (br, br*.65), (br, br*.53), (br*.35, br*.24), (br*.35, 0)]
        smooth(revolve_dir(kit, f'{name} bell {k}', [(t, r*1.0) for t, r in prof], p0, dv,
                           mats['steel'] if k % 2 else mats['lip'], n=S.n(br*.65)))
    return ob


def lamp_fitting(kit, S, name, base, aim, mats, lens_m=.2, guard=True):
    """A running or work lamp: a bracket, a housing, a recessed emissive lens and an
    optional wire guard. Only the lens carries the emissive material (mats['lens']);
    the housing is plain metal (mats['housing']). Lens 0.2 m radius by default."""
    a = Vector(aim).normalized()
    p0 = Vector(base)
    r = S.u(lens_m)
    n = S.n(r*1.25)
    rod(kit, name+' bracket', p0, p0+a*r*1.6, r*.35, mats['housing'], n=S.n(r*.35))
    hp = p0+a*r*2.0
    smooth(revolve_dir(kit, name+' housing', [(-r*1.3, 0), (-r*1.3, r*.9), (-r*.5, r*1.25), (r*.6, r*1.25),
                                              (r*.6, r*1.05), (r*.2, r*1.0), (r*.2, 0)], hp, a, mats['housing'], n=n))
    smooth(revolve_dir(kit, name+' lens', [(-r*.1, 0), (-r*.1, r*1.0), (r*.25, r*.9), (r*.35, 0)], hp, a,
                       mats['lens'], n=n))
    if guard:
        v, f = [], []
        _, u, w = frame(a)
        tip = hp+a*r*1.4
        for i in range(4):
            t = 2*math.pi*i/4+math.pi/4
            q = hp+a*r*.5+(u*math.cos(t)+w*math.sin(t))*r*1.2
            beam(v, f, q, tip+(u*math.cos(t)+w*math.sin(t))*r*.35, r*.12, r*.12)
        solid(kit, name+' guard', v, f, mats['housing'])


def mast(kit, S, name, foot, height_m, mats, radius_m=.17, yard_m=3.2, whips=2, lamps=True,
         base_m=1.3, braces=True):
    """A short mast: a bevelled base plinth, a round pole (segment rule, smooth), a yard
    with braces, whip antennae with insulators, and head and yard lamps
    (the Colossus bridge mast: 0.35 m pole, 3.5 m tall, 3.2 m yard). foot: (x, y, z)."""
    x, y, z = foot
    r = S.u(radius_m)
    H = S.u(height_m)
    bs = S.u(base_m)
    kit.box(name+' base', (x, y, z+bs*.3), (bs, bs, bs*.6), mats['steel'], bevel=S.bevel('fitting'))
    pole = kit.cylinder(name, (x, y, z+bs*.6+H/2), r, H, mats['steel'], vertices=S.n(r), bevel=0)
    smooth(pole)
    v, f = [], []
    ytop = z+bs*.6+H*.78
    Y = S.u(yard_m)/2
    beam(v, f, (x, y-Y, ytop), (x, y+Y, ytop), r*.85)
    if braces:
        for s in (1, -1):
            beam(v, f, (x, y+s*r, ytop-H*.55), (x, y+s*Y*.75, ytop-r), r*.35)
    whip = S.u(.03)
    for k in range(whips):
        s = 1 if k % 2 == 0 else -1
        beam(v, f, (x, y+s*Y*.92, ytop), (x, y+s*Y*.92, ytop+H*.5), whip*2)
    beam(v, f, (x, y, z+bs*.6+H), (x, y, z+bs*.6+H+H*.35), whip*2)
    solid(kit, name+' yard and antennae', v, f, mats['steel'])
    v, f = [], []
    for k in range(whips):
        s = 1 if k % 2 == 0 else -1
        disc(v, f, (x, y+s*Y*.92, ytop+S.u(.12)), (0, 0, 1), S.u(.07), S.u(.18), n=S.n(S.u(.07)))
    if whips:
        solid(kit, name+' insulators', v, f, mats['insulator'])
    if lamps:
        lm = {'housing': mats['steel'], 'lens': mats['lens']}
        lamp_fitting(kit, S, name+' head lamp', (x, y, z+bs*.6+H), (0, 0, 1), lm, lens_m=.12, guard=False)
        for s in (1, -1):
            lamp_fitting(kit, S, f'{name} yard lamp {1 if s > 0 else 2}', (x, y+s*Y, ytop), (0, 0, 1), lm,
                         lens_m=.1, guard=False)
    return pole


def dish(kit, S, name, foot, radius_m, mats, tilt_deg=30.0, heading_deg=0.0, focal=.65, ribs=12):
    """A parabolic dish on an azimuth turntable and elevation yoke: a dished reflector
    with a rolled rim, radial back ribs and a stiffening hoop, a hub, three feed struts
    and a feed horn (the Colossus bridge dish: 1.7 m radius, 48 segments).
    foot: (x, y, z) on the deck; heading_deg turns it about Z; tilt_deg is elevation off zenith."""
    x, y, z = foot
    R = S.u(radius_m)
    n = max(48, S.n(R))
    hd = math.radians(heading_deg)
    ped_r = R*.27
    kit.cylinder(name+' pedestal', (x, y, z+R*.3), ped_r, R*.6, mats['steel'], vertices=S.n(ped_r),
                 bevel=S.bevel('small'))
    v, f = [], []
    disc(v, f, (x, y, z+R*.64), (0, 0, 1), R*.45, R*.12, n=S.n(R*.45))
    across = Vector((-math.sin(hd), math.cos(hd), 0))
    H = Vector((x, y, z+R))
    for e in (-1, 1):
        beam(v, f, Vector((x, y, z+R*.68))+across*e*R*.34, H+across*e*R*.34, R*.13, R*.26, across)
        disc(v, f, H+across*e*R*.34, across, R*.15, R*.15, n=S.n(R*.15))
    disc(v, f, H, across, R*.065, R*.85, n=S.n(R*.065))
    solid(kit, name+' yoke', v, f, mats['steel'], bevel=S.bevel('small'))
    foc = R*focal
    zf = lambda r: r*r/(4*foc)
    rs = [R*k for k in (.1, .2, .32, .44, .56, .68, .79, .88, .94)]
    prof = [(r, zf(r)) for r in rs]+[(R, zf(rs[-1])+R*.018), (R, zf(rs[-1])-R*.04)]+[(r, zf(r)-R*.053) for r in reversed(rs)]
    refl = revolve_z(kit, name, prof, mats['dish'], n=n)
    smooth(refl, 20)
    tilt = math.radians(tilt_deg)
    ax = Vector((math.sin(tilt)*math.cos(hd), math.sin(tilt)*math.sin(hd), math.cos(tilt)))
    D = H+ax*R*.26
    v, f = [], []
    for i in range(ribs):
        t = 2*math.pi*i/ribs
        d = Vector((math.cos(t), math.sin(t), 0))
        sd = Vector((-math.sin(t), math.cos(t), 0))
        rr = [R*.13, R*.41, R*.68, R*.93]
        for r0, r1 in zip(rr, rr[1:]):
            beam(v, f, d*r0+Vector((0, 0, zf(r0)-R*.11)), d*r1+Vector((0, 0, zf(r1)-R*.11)), R*.047, R*.12, sd)
    hoop = n//2
    for i in range(hoop):
        t0, t1 = 2*math.pi*i/hoop, 2*math.pi*(i+1)/hoop
        rh = R*.59
        beam(v, f, Vector((rh*math.cos(t0), rh*math.sin(t0), zf(rh)-R*.105)),
             Vector((rh*math.cos(t1), rh*math.sin(t1), zf(rh)-R*.105)), R*.094, R*.047, (0, 0, 1))
    disc(v, f, (0, 0, -R*.12), (0, 0, 1), R*.16, R*.32, n=S.n(R*.16))
    for i in range(3):
        t = 2*math.pi*i/3+math.pi/2
        beam(v, f, (R*.9*math.cos(t), R*.9*math.sin(t), zf(R*.9)+R*.006), (0, 0, foc-R*.1), R*.035)
    disc(v, f, (0, 0, foc-R*.06), (0, 0, 1), R*.065, R*.2, n=S.n(R*.065))
    annulus(v, f, (0, 0, foc-R*.16), (0, 0, 1), R*.06, R*.13, R*.09, n=S.n(R*.13))
    back = solid(kit, name+' ribs and feed', v, f, mats['steel'])
    for ob in (refl, back):        # XYZ Euler: tilt about Y (elevation), then heading about Z
        ob.location = D
        ob.rotation_euler = (0, tilt, hd)
    return refl
