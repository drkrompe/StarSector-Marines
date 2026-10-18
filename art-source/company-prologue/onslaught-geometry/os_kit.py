"""Shared Blender helpers for the Onslaught (XIV) battleship.

A standalone copy of the kit helpers the Legion, Second Wind and Hound (A)
builds use (materials, primitives, chamfered prisms, booleans, a downward
ray-cast); it deliberately imports no other hull's package, which other
sessions are editing. Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one
sprite pixel per unit about the .ship centre.
"""
import math
import bmesh
import bpy
from mathutils import Vector


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


class Kit:
    def __init__(self):
        # Hegemony (XIV) livery read off onslaught_hegemony.png: Hegemony
        # orange armour, dark grey armour panels between the orange, a warm
        # brown-grey structure, the bone-white central spine and domes,
        # gunmetal machinery and dark recesses. Light service wear is a later
        # texturing pass; these are flat base materials.
        self.orange = material('Hegemony orange armour', (.60, .15, .035), .25, .55)
        self.grey = material('Hegemony dark grey armour', (.055, .053, .055), .4, .55)
        self.plate = material('Hegemony brown-grey structure', (.15, .095, .08), .35, .6)
        self.bone = material('Hegemony bone white spine', (.60, .54, .50), .2, .5)
        self.steel = material('Gunmetal machinery', (.10, .095, .10), .7, .5)
        self.dark = material('Recess and machinery', (.03, .028, .03), .5)
        self.glass = material('Bridge glazing', (.04, .06, .07), .2, .15, emission=(.9, .75, .45), strength=3.0)
        self.lamp = material('Bay lamp amber', (.1, .07, .02), .0, .5, emission=(1.0, .7, .25), strength=6.0)
        self.mpu = 1.0  # metres per unit; the driver sets it before building
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
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
    mod.operation = op
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
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
