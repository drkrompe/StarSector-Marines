"""Shared Blender helpers for the Enforcer destroyer.

A standalone copy of the few Hound kit helpers this build needs (materials,
primitives, chamfered prisms, booleans, a downward ray-cast); it deliberately
does not import another hull's package, which other sessions are editing.
Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one sprite pixel per
unit about the .ship centre.

The Enforcer is the hero of S6 at about 1 km (about 150 px long at 1080p on
the 30 mm lens) and distant in S1 and S4, so the kit stays light: material
classes rather than per-part materials, flat shading, one-segment chamfers
that the export bakes. One build serves both fleets: the livery (merchant or
pirate) only recolours the classes, and the pirate's welded armour is its own
named objects (ef_armour_p).
"""
import math
import bmesh
import bpy
from mathutils import Vector


def material(name, color, metallic=0.0, roughness=.65, emission=None, strength=2.0, wear=0.0):
    """Principled material; `wear` > 0 adds light service wear: a large-scale
    noise that darkens and roughens the base colour a little (procedural, so
    nothing to pack, and it rides the object's own coordinates)."""
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    nodes, links = m.node_tree.nodes, m.node_tree.links
    bsdf = nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = roughness
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*emission, 1)
        bsdf.inputs['Emission Strength'].default_value = strength
    if wear:
        tex = nodes.new('ShaderNodeTexCoord')
        noise = nodes.new('ShaderNodeTexNoise')
        noise.inputs['Scale'].default_value = .18
        noise.inputs['Detail'].default_value = 6
        links.new(tex.outputs['Object'], noise.inputs['Vector'])
        ramp = nodes.new('ShaderNodeValToRGB')
        ramp.color_ramp.elements[0].position = .45
        ramp.color_ramp.elements[1].position = .75
        links.new(noise.outputs['Fac'], ramp.inputs['Fac'])
        mix = nodes.new('ShaderNodeMix')
        mix.data_type = 'RGBA'
        mix.inputs['A'].default_value = (*color, 1)
        mix.inputs['B'].default_value = (*(c*(1-wear) for c in color), 1)
        links.new(ramp.outputs['Color'], mix.inputs['Factor'])
        links.new(mix.outputs['Result'], bsdf.inputs['Base Color'])
        rough = nodes.new('ShaderNodeMapRange')
        rough.inputs['To Min'].default_value = roughness
        rough.inputs['To Max'].default_value = min(1, roughness+.15)
        links.new(ramp.outputs['Color'], rough.inputs['Value'])
        links.new(rough.outputs['Result'], bsdf.inputs['Roughness'])
    return m


# Material classes, coloured per livery. Names are identifiers: the texture
# pass maps classes to its swatches by these names, never by colour.
LIVERIES = {
    # Independent escort: the convoy's hired screen, in the same scheme as the
    # convoy's two Lashers (lasher-geometry ls_kit LIVERIES 'independent'):
    # pale warm grey from the vanilla independent colour (150,150,150), a
    # slate-blue accent, warm gunmetal machinery; the sprite's pale tank
    # cladding kept.
    'merchant': dict(armour=(.25, .245, .232), accent=(.045, .075, .12), machinery=(.10, .085, .075),
                     tank=(.42, .37, .30)),
    # Pirate (enforcer_pirates.png): the carapace stripped back to rusty
    # gunmetal, red slabs welded over it, the tank bays caged.
    'pirate': dict(armour=(.12, .085, .07), accent=(.42, .03, .02), machinery=(.085, .07, .065),
                   tank=(.20, .16, .13)),
}


class Kit:
    def __init__(self, livery='merchant'):
        c = LIVERIES[livery]
        self.livery = livery
        self.armour = material('Enforcer armour', c['armour'], .3, .55, wear=.22)
        self.accent = material('Enforcer accent armour', c['accent'], .3, .55, wear=.2)
        self.steel = material('Enforcer machinery', c['machinery'], .6, .5, wear=.18)
        # Trim: the livery's accent where the merchant scheme wears it (turret
        # gunhouses, drive lips, prow and pod bands); the pirate keeps gunmetal
        # there and wears its red on the welded slabs instead.
        self.trim = self.accent if livery == 'merchant' else self.steel
        self.tank = material('Enforcer tank cladding', c['tank'], .4, .5, wear=.15)
        self.dark = material('Enforcer recess', (.025, .022, .02), .4, .6)
        self.glass = material('Enforcer bridge glazing', (.03, .03, .025), .2, .15,
                              emission=(1.0, .85, .45), strength=4.0)
        self.vent = material('Enforcer heat vent glow', (.05, .03, .02), .3, .5,
                             emission=(1.0, .45, .12), strength=2.5)
        self.hull = []  # objects the downward ray-cast seats parts on
        self.mpu = 1.0  # metres per unit; the driver sets it before building

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
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
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

    def empty(self, name, loc, angle_deg, size=3, kind='ARROWS'):
        """A plain-axes anchor: its +X points along `angle_deg` in plan."""
        ob = bpy.data.objects.new(name, None)
        ob.empty_display_type = kind
        ob.empty_display_size = size
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


def parent(child, par):
    """Parent keeping the world transform."""
    bpy.context.view_layer.update()
    w = child.matrix_world.copy()
    child.parent = par
    child.matrix_parent_inverse = par.matrix_world.inverted()
    child.matrix_world = w


def sym(port):
    """A CCW plan from its port (+Y) edge listed bow to stern; starboard mirrors it."""
    return list(port) + [(x, -y) for x, y in reversed(port) if abs(y) > 1e-9]


def revolve_x(kit, name, profile, y, z, mat, n=12):
    """Closed solid of revolution about an X-parallel axis through (y, z):
    profile is [(x, r), ...] round the section, closed."""
    m = len(profile)
    verts = []
    for k in range(n):
        a = 2*math.pi*k/n
        for x, r in profile:
            verts.append((x, y+r*math.cos(a), z+r*math.sin(a)))
    faces = []
    for k in range(n):
        k2 = (k+1) % n
        for i in range(m):
            i2 = (i+1) % m
            faces.append((k*m+i, k*m+i2, k2*m+i2, k2*m+i))
    ob = kit.mesh(name, verts, faces, mat)
    me = ob.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
    bmesh.ops.dissolve_degenerate(bm, edges=bm.edges, dist=1e-4)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    return ob


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
    cutter.data.materials.append(target.data.materials[0])  # the cut face takes the target's finish
    boolean(target, cutter)


def facet_normal(down, angle):
    """Outward normal of a face falling along plan direction `down` at `angle` deg below horizontal."""
    a = math.radians(angle)
    dx, dy = down
    length = (dx*dx+dy*dy)**.5
    return (dx/length*math.sin(a), dy/length*math.sin(a), math.cos(a))
