"""Shared Blender helpers for the Kite (P) geometry build.

Copied from the Second Wind kit (second-wind-geometry/sw_kit.py, sw_hull.py)
rather than imported, and from the Hound kit (hound-geometry/hd_hull.py: bx, blocks), so the
packages can change independently.
Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one unit = one
sprite pixel about the vanilla hull centre.
"""
import math
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
        # Blockout palette read off the vanilla sprite (Midline): cream hull
        # plate on the spine and shoulders, cool grey panels, gunmetal drive
        # and turret work, dark seams and recesses.
        self.cream = material('Cream hull plate', (.60, .56, .44), .25, .55)
        self.panel = material('Grey hull panel', (.42, .44, .46), .35, .5)
        self.steel = material('Gunmetal machinery', (.17, .18, .20), .7, .45)
        self.dark = material('Recess and machinery', (.045, .05, .058), .5)
        self.glass = material('Cockpit glazing', (.03, .05, .06), .2, .12, emission=(.35, .5, .55), strength=.25)
        self.lamp = material('Running lamps', (.8, .75, .5), emission=(1.0, .9, .6), strength=3)
        # Pass 2: the cabin windows' warm interior glow, the belly's entry
        # heat shield, the pirate crew's welded scrap plate, navigation lamps.
        self.window = material('Cabin window glow', (.05, .045, .035), .1, .2, emission=(1.0, .78, .45), strength=1.5)
        self.shield = material('Heat shield tile', (.075, .07, .068), .1, .8)
        self.scrap = material('Pirate scrap plate', (.30, .26, .22), .45, .6)
        self.nav_red = material('Nav lamp red', (.5, .05, .04), emission=(1.0, .08, .05), strength=6)
        self.nav_green = material('Nav lamp green', (.05, .5, .1), emission=(.1, 1.0, .25), strength=6)
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

    def surface_any(self, x, y, names=('Hull fuselage', 'Hull wing body', 'Hull dorsal spine', 'Hull aft hump')):
        """Highest (point, normal) under (x, y) among the named hull solids, or None."""
        best = None
        for n in names:
            ob = bpy.data.objects.get(n)
            hit = ob and self.surface(x, y, ob)
            if hit and (best is None or hit[0].z > best[0].z):
                best = hit
        return best


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


def bx(kit, name, x0, x1, y0, y1, z0, z1, mat, bevel=.06):
    """Axis-aligned box from its extents."""
    return kit.box(name, ((x0+x1)/2, (y0+y1)/2, (z0+z1)/2), (x1-x0, y1-y0, z1-z0), mat, bevel)


def blocks(kit, name, boxes, mat, bevel=0):
    """Many small boxes (bolts, slats, stiffeners) as one object."""
    verts, faces = [], []
    for x0, x1, y0, y1, z0, z1 in boxes:
        b = len(verts)
        verts += [(x, y, z) for z in (z0, z1) for y in (y0, y1) for x in (x0, x1)]
        faces += [(b, b+2, b+3, b+1), (b+4, b+5, b+7, b+6), (b, b+1, b+5, b+4),
                  (b+2, b+6, b+7, b+3), (b, b+4, b+6, b+2), (b+1, b+3, b+7, b+5)]
    return kit.mesh(name, verts, faces, mat, bevel)


def clip(poly, a, b, c):
    """Keep the part of a plan polygon where a*x+b*y <= c (Sutherland-Hodgman)."""
    out = []
    for i in range(len(poly)):
        p, q = poly[i-1], poly[i]
        fp, fq = a*p[0]+b*p[1]-c, a*q[0]+b*q[1]-c
        if fq <= 0:
            if fp > 0:
                t = fp/(fp-fq)
                out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
            out.append(q)
        elif fp <= 0:
            t = fp/(fp-fq)
            out.append((p[0]+(q[0]-p[0])*t, p[1]+(q[1]-p[1])*t))
    return [p for i, p in enumerate(out) if abs(p[0]-out[i-1][0])+abs(p[1]-out[i-1][1]) > 1e-6]


def mirror(plan):
    """A port half-plan (y >= 0, listed bow to stern) closed with its starboard mirror."""
    return list(plan) + [(x, -y) for x, y in reversed(plan) if y > 0]


def pivot(name, loc, props=None):
    """A named empty at a pivot; moving parts are parented to it."""
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type = 'ARROWS'
    ob.empty_display_size = 2
    ob.location = loc
    bpy.context.collection.objects.link(ob)
    for k, v in (props or {}).items():
        ob[k] = v
    return ob


def parent_to(parts, empty):
    """Parent parts to an empty without moving them."""
    bpy.context.view_layer.update()
    for p in parts:
        p.parent = empty
        p.matrix_parent_inverse = empty.matrix_world.inverted()
