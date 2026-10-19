"""Shared Blender helpers for the Tarsus freighter cinematic model.

A standalone copy of the Colossus kit helpers (materials, primitives,
chamfered prisms, booleans, a downward ray-cast, gathered part shells); it
deliberately imports no other hull package, which other sessions edit.
Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one sprite pixel
per unit about the .ship centre (53, 80).
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
        # Low-tech hazard-duty freighter scheme read off tarsus_base.png: weathered
        # ochre-tan module plating, pale cream deck panels, rust-brown bow and drum
        # castings, mid-grey structure, white pressure tanks, a faded blue pipe run,
        # copper drive cans, dark gunmetal machinery and black recesses.
        self.ochre = material('Ochre module plating', (.50, .36, .16), .25, .6)
        self.cream = material('Pale cream deck plating', (.62, .55, .42), .2, .55)
        self.rust = material('Rust brown casting', (.30, .15, .09), .3, .6)
        self.grey = material('Mid grey structure', (.36, .36, .35), .35, .55)
        self.white = material('White pressure tank', (.72, .72, .72), .3, .35)
        self.blue = material('Faded blue pipe run', (.14, .26, .42), .3, .5)
        self.orange = material('Signal orange panel', (.55, .17, .05), .2, .55)
        self.copper = material('Copper drive can', (.42, .22, .14), .6, .45)
        self.steel = material('Gunmetal machinery', (.16, .16, .17), .65)
        self.dark = material('Recess and lattice', (.04, .04, .045), .5)
        self.glass = material('Bridge glazing', (.05, .08, .09), .2, .15, emission=(.9, .75, .4), strength=3.0)
        self.lamp = material('Running lamp', (.2, .2, .15), 0, .4, emission=(1.0, .9, .6), strength=6.0)
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
    planar(target)
    # A live bevel on a cut mesh folds back over the short edges the cut leaves and turns
    # faces inside out (the hatch cover's deck rendered as a dark inverted wedge; the aft
    # body, hold keel and module keels in patches). No miter or corner setting cleared every
    # object, and at 0.12 units (6 cm) on hulls 100 units across the chamfer does not read:
    # a cut mesh keeps its section chamfers and loses the modifier.
    for m in [m for m in target.modifiers if m.type == 'BEVEL']:
        target.modifiers.remove(m)


def planar(obj):
    """Split the warped n-gons a boolean leaves, which the live bevel turns into spikes."""
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bmesh.ops.dissolve_degenerate(bm, dist=1e-4, edges=bm.edges[:])
    bmesh.ops.connect_verts_nonplanar(bm, angle_limit=0.0005, faces=bm.faces[:])
    bm.to_mesh(obj.data)
    bm.free()


def plane_cut(target, name, point, normal, size=200):
    """Remove everything on the normal side of a plane: one flat facet."""
    normal = Vector(normal).normalized()
    bpy.ops.mesh.primitive_cube_add(size=1, location=Vector(point)+normal*size/2)
    cutter = bpy.context.object
    cutter.name = 'Temporary '+name+' cutter'
    cutter.scale = (size, size, size)
    cutter.rotation_euler = normal.to_track_quat('Z', 'Y').to_euler()
    cutter.data.materials.append(target.data.materials[0])  # the facet takes the part's own material
    boolean(target, cutter)


def facet_normal(down, angle):
    """Outward normal of a face falling along plan direction `down` at `angle` deg below horizontal."""
    a = math.radians(angle)
    dx, dy = down
    length = (dx*dx+dy*dy)**.5
    return (dx/length*math.sin(a), dy/length*math.sin(a), math.cos(a))


def ngon(cy, cz, ry, rz, n=12, phase=None):
    """An n-sided YZ section about (cy, cz), flats top and bottom by default."""
    phase = math.pi/n if phase is None else phase
    return [(cy+ry*math.cos(phase+2*math.pi*i/n), cz+rz*math.sin(phase+2*math.pi*i/n)) for i in range(n)]


def pod(kit, name, stations, cy, cz, mat, n=12, bevel=.1):
    """Faceted body of revolution along X: stations [(x, ry, rz), ...] aft to fore."""
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


class Parts:
    """Many closed shells gathered into one mesh object."""

    def __init__(self):
        self.v, self.f, self.shells = [], [], []

    def _add(self, verts, faces):
        o = len(self.v)
        self.shells.append((o, len(verts), len(self.f), len(faces)))
        self.v += [tuple(p) for p in verts]
        self.f += [tuple(i+o for i in fc) for fc in faces]
        return self

    def box(self, x0, x1, y0, y1, z0, z1):
        x0, x1 = sorted((x0, x1))
        y0, y1 = sorted((y0, y1))
        z0, z1 = sorted((z0, z1))
        verts = [(x, yy, z) for x in (x0, x1) for yy in (y0, y1) for z in (z0, z1)]
        return self._add(verts, [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1),
                                 (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)])

    def tube(self, cx, cy, z0, z1, r0, r1, wall, n=40):
        """Annular shell about a vertical axis: radius r0 at z0 to r1 at z1, `wall` thick."""
        vs = []
        for r, z in ((r0, z0), (r1, z1), (r1-wall, z1), (r0-wall, z0)):
            vs += [(cx+r*math.cos(2*math.pi*i/n), cy+r*math.sin(2*math.pi*i/n), z) for i in range(n)]
        fs = []
        for a in range(4):
            b = (a+1) % 4
            fs += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
        return self._add(vs, fs)

    def slab(self, poly, ext):
        """A planar polygon swept along ext."""
        n = len(poly)
        top = [(p[0]+ext[0], p[1]+ext[1], p[2]+ext[2]) for p in poly]
        faces = [tuple(range(n)), tuple(range(n, 2*n))]
        faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
        return self._add(list(poly)+top, faces)

    def cyl(self, c, r, h, axis='Z', n=12):
        """Cylinder from c along +axis by h."""
        pts = []
        for i in range(n):
            a = 2*math.pi*(i+.5)/n
            u, w = r*math.cos(a), r*math.sin(a)
            pts.append({'X': (c[0], c[1]+u, c[2]+w), 'Y': (c[0]+u, c[1], c[2]+w),
                        'Z': (c[0]+u, c[1]+w, c[2])}[axis])
        ext = {'X': (h, 0, 0), 'Y': (0, h, 0), 'Z': (0, 0, h)}[axis]
        return self.slab(pts, ext)

    def beam(self, p0, p1, w, h):
        """Rectangular beam w wide and h deep from p0 to p1."""
        p0, p1 = Vector(p0), Vector(p1)
        d = p1-p0
        side = d.cross(Vector((0, 0, 1)))
        if side.length < 1e-6:
            side = Vector((1, 0, 0))
        side = side.normalized()*w/2
        up = side.cross(d).normalized()*h/2
        if up.z < 0:
            up = -up
        quad = [p0-side-up, p0+side-up, p0+side+up, p0-side+up]
        return self.slab(quad, d)

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.v, self.f, mat, bevel) if self.v else None


def obj(name):
    return bpy.data.objects[name]


def cut(kit, target, parts, mat):
    """Subtract gathered cutter shells from target; the new faces take mat.

    Shells that overlap (crossing seams) would make one self-intersecting cutter,
    which the MANIFOLD solver turns into torn, wrongly-coloured keel faces, so
    they are split into passes whose shells are pairwise disjoint."""
    passes = []                                   # [(Parts, [aabb...])]
    for o, nv, fo, nf in parts.shells:
        vs = parts.v[o:o+nv]
        lo = [min(p[i] for p in vs) for i in range(3)]
        hi = [max(p[i] for p in vs) for i in range(3)]
        for g, boxes in passes:
            if not any(all(lo[i] < b1[i] and b0[i] < hi[i] for i in range(3)) for b0, b1 in boxes):
                break
        else:
            g, boxes = Parts(), []
            passes.append((g, boxes))
        boxes.append((lo, hi))
        g._add(vs, [tuple(i-o for i in fc) for fc in parts.f[fo:fo+nf]])
    for g, _ in passes:
        boolean(target, g.build(kit, 'Temporary hull cutter', mat))
