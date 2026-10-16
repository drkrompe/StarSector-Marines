"""Shared Blender helpers for the Mudskipper geometry build.

Copied from the Second Wind kit (second-wind-geometry/sw_kit.py, sw_hull.py)
rather than imported, so the two packages can change independently.
Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one unit = one
sprite pixel about the vanilla hull centre.
"""
import math
import bpy
from mathutils import Vector


def material(name, color, metallic=0.0, roughness=.65, emission=None):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = roughness
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*emission, 1)
        bsdf.inputs['Emission Strength'].default_value = 2.0
    return m


class Kit:
    def __init__(self):
        # Blockout palette read off the vanilla sprite: pale grey hull plate,
        # red stripe panels, grey-green sponsons, tan hatch work, dark drives.
        self.plate = material('Pale hull plate', (.50, .50, .48), .3)
        self.stripe = material('Red stripe panel', (.36, .05, .04), .3)
        self.sponson = material('Grey-green sponson plate', (.24, .28, .26), .35)
        self.tan = material('Tan hatch and fender work', (.42, .33, .20), .3)
        self.dark = material('Recess and machinery', (.055, .065, .072), .5)
        self.steel = material('Drive casing steel', (.19, .21, .22), .65)
        self.glass = material('Cockpit glazing', (.05, .09, .11), .2, .15, emission=(.35, .5, .55))
        self.lamp = material('Bow running lamps', (.8, .7, .42), emission=(.9, .75, .35))
        self.hull = None

    def finish(self, obj, name, mat, bevel=0):
        obj.name = name
        obj.data.materials.append(mat)
        if bevel:
            crisp(obj, bevel)
        return obj

    def box(self, name, loc, dims, mat, bevel=.15):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        obj = bpy.context.object
        obj.dimensions = dims
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        return self.finish(obj, name, mat, bevel)

    def cylinder(self, name, loc, radius, depth, mat, axis='Z', vertices=32, bevel=.1):
        bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
        obj = bpy.context.object
        if axis == 'X':
            obj.rotation_euler[1] = math.pi / 2
        elif axis == 'Y':
            obj.rotation_euler[0] = math.pi / 2
        return self.finish(obj, name, mat, bevel)

    def mesh(self, name, verts, faces, mat, bevel=0):
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        me.update()
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        for poly in me.polygons:
            poly.use_smooth = False
        return self.finish(ob, name, mat, bevel)

    def surface(self, x, y, obj=None):
        """(point, normal) where a ray dropped at (x, y) meets the hull, or None."""
        bpy.context.view_layer.update()
        target = obj or self.hull
        evaluated = target.evaluated_get(bpy.context.evaluated_depsgraph_get())
        hit, p, normal, _ = evaluated.ray_cast(Vector((x, y, 100)), Vector((0, 0, -1)))
        return (p, normal) if hit else None


def crisp(obj, width=.18):
    """One-segment chamfer on hard edges only: a highlight line, not a radius."""
    b = obj.modifiers.new('Plate edge chamfer', 'BEVEL')
    b.width, b.segments, b.limit_method = width, 1, 'ANGLE'
    return obj


def ccw(poly):
    area = sum(poly[i-1][0]*poly[i][1]-poly[i][0]*poly[i-1][1] for i in range(len(poly)))
    return poly if area > 0 else list(reversed(poly))


def _left(x, y):
    length = (x*x+y*y)**.5
    return (-y/length, x/length)


def inset(poly, d):
    """Mitred inward offset of a CCW polygon: fixed-width chines on every edge."""
    out = []
    n = len(poly)
    for i in range(n):
        (ax,ay),(bx,by),(cx,cy) = poly[i-1],poly[i],poly[(i+1)%n]
        n0 = _left(bx-ax,by-ay)
        n1 = _left(cx-bx,cy-by)
        k = d/(1+n0[0]*n1[0]+n0[1]*n1[1])
        out.append((bx+(n0[0]+n1[0])*k, by+(n0[1]+n1[1])*k))
    return out


def prism(kit, name, plan, profile, mat):
    """Plate solid: a plan polygon swept through (inset, z) section stations."""
    plan = ccw(plan)
    n = len(plan)
    verts = [(x,y,z) for d,z in profile for x,y in inset(plan,d)]
    faces = [tuple(reversed(range(n)))]
    for r in range(len(profile)-1):
        faces += [(r*n+i, r*n+(i+1)%n, (r+1)*n+(i+1)%n, (r+1)*n+i) for i in range(n)]
    top = (len(profile)-1)*n
    faces.append(tuple(range(top, top+n)))
    return kit.mesh(name, verts, faces, mat)


def section(keel, wall_top, deck, keel_chamfer=2.5, shoulder=2.5, proud=0):
    """One keel chamfer, one vertical wall, one shoulder chamfer."""
    return [(keel_chamfer-proud,keel),(-proud,keel+keel_chamfer),(-proud,wall_top),(shoulder-proud,deck)]


def boolean(obj, other, op, keep=False):
    bpy.context.view_layer.objects.active = obj
    mod = obj.modifiers.new('Hull block '+op.lower(), 'BOOLEAN')
    mod.operation = op
    mod.object = other
    mod.solver = 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)  # ahead of the chamfer
    bpy.ops.object.modifier_apply(modifier=mod.name)
    if not keep:
        bpy.data.objects.remove(other, do_unlink=True)


def facet(name, point, down, angle):
    """A flat armour plane through `point` falling along plan direction
    `down` at `angle` degrees below horizontal: (name, point, outward normal)."""
    a = math.radians(angle)
    dx, dy = down
    length = (dx*dx+dy*dy)**.5
    return name, Vector(point), Vector((dx/length*math.sin(a), dy/length*math.sin(a), math.cos(a)))


def plane_cut(obj, name, point, normal):
    """Remove everything on the normal side of the plane with a large box."""
    normal = normal.normalized()
    bpy.ops.mesh.primitive_cube_add(size=1, location=point+normal*200)
    cutter = bpy.context.object
    cutter.name = 'Temporary '+name+' cutter'
    cutter.scale = (400, 400, 400)
    cutter.rotation_euler = normal.to_track_quat('Z','Y').to_euler()
    boolean(obj, cutter, 'DIFFERENCE')
