"""Shared Blender helpers for the Second Wind geometry build.

Every detail module receives a Kit: the materials, primitive builders and a
downward ray-cast onto the evaluated main hull. Coordinates are hull-local,
+X bow, +Y port, +Z dorsal.
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
        self.cream = material('Chalked cream armor', (.56, .52, .41), .3)
        self.orange = material('Oxide orange armor', (.38, .16, .065), .3)
        self.dark = material('Recess and machinery', (.055, .065, .072), .5)
        self.steel = material('Engine collars', (.19, .21, .22), .65)
        self.podmat = material('Port pod - muted teal identification', (.13, .32, .31), .35)
        self.lightmat = material('Bay work lights', (.8, .7, .42))
        self.glass = material('Bridge viewport glazing', (.05, .09, .11), .2, .15, emission=(.35, .5, .55))
        self.hull = None

    def finish(self, obj, name, mat, bevel=0):
        obj.name = name
        obj.data.materials.append(mat)
        if bevel:
            b = obj.modifiers.new('Manufactured edge radii', 'BEVEL')
            b.width = bevel
            b.segments = 3
            obj.modifiers.new('Weighted corner normals', 'WEIGHTED_NORMAL')
        return obj

    def box(self, name, loc, dims, mat, bevel=.3):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        obj = bpy.context.object
        obj.dimensions = dims
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        return self.finish(obj, name, mat, bevel)

    def cylinder(self, name, loc, radius, depth, mat, axis='Z', vertices=32, bevel=.15):
        bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
        obj = bpy.context.object
        if axis == 'X':
            obj.rotation_euler[1] = math.pi / 2
        elif axis == 'Y':
            obj.rotation_euler[0] = math.pi / 2
        return self.finish(obj, name, mat, bevel)

    def mesh(self, name, verts, faces, mat, smooth=False, bevel=0):
        """Arbitrary polygon solid, for chamfered or sloped shapes boxes cannot make."""
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        me.update()
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        for poly in me.polygons:
            poly.use_smooth = smooth
        return self.finish(ob, name, mat, bevel)

    def surface(self, x, y):
        """(point, normal) where a ray dropped at (x, y) meets the main hull, or None."""
        bpy.context.view_layer.update()
        evaluated = self.hull.evaluated_get(bpy.context.evaluated_depsgraph_get())
        hit, p, normal, _ = evaluated.ray_cast(Vector((x, y, 100)), Vector((0, 0, -1)))
        return (p, normal) if hit else None
