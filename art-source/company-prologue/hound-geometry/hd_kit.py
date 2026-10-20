"""Shared Blender helpers for the Hound (A) blockout.

A standalone copy of the few Second Wind kit helpers this build needs
(materials, primitives, chamfered prisms, booleans, a downward ray-cast);
it deliberately does not import second-wind-geometry, which other passes
are editing. Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one
sprite pixel per unit about the .ship centre.
"""
import math
import sys
from pathlib import Path
import bmesh
import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'ship-detail-kit'))
import ship_detail_kit as sdk  # noqa: E402

# Metres per hull unit; build_hound.py sets it from METRES_PER_UNIT before building.
MPU = .88
# A bevel modifier leaves edges flatter than this alone, so a designed 45-degree
# chamfer (the octagon sections) stays one crisp facet instead of soft creases that
# read as a coarse curve (ship-detail-guide.md, Edge treatment).
BEVEL_ANGLE = 50.0
HEX_BOLT_M = .15  # hexagonal heads up to this radius keep six sides (the segment rule's exemption)


def sides(radius_units, n):
    """At least the segment rule's count for a round of this radius (ship_detail_kit.segments);
    six-sided bolt and nut heads up to HEX_BOLT_M keep six."""
    r = abs(radius_units)*MPU
    if n == 6 and r <= HEX_BOLT_M:
        return 6
    return max(n, sdk.segments(r))


def round_shading(obj):
    """Smooth-shade a round body with its creases over 35 degrees kept sharp (sdk.smooth)."""
    return sdk.smooth(obj)


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
        # Hegemony auxiliary scheme read off hound_hegemony.png: oxide
        # orange armour, gunmetal machinery, dark recesses, cream lamps.
        self.orange = material('Hegemony oxide armour', (.40, .15, .055), .3)
        self.rust = material('Weathered brown armour', (.20, .11, .07), .35)
        self.steel = material('Gunmetal machinery', (.19, .20, .21), .65)
        self.dark = material('Recess and machinery', (.045, .05, .055), .5)
        self.cream = material('Chalked cream fittings', (.55, .50, .40), .25)
        self.glass = material('Bridge glazing', (.05, .08, .09), .2, .15, emission=(.9, .75, .4), strength=3.0)
        self.hull = []  # objects the downward ray-cast seats parts on

    def finish(self, obj, name, mat, bevel=0):
        obj.name = name
        obj.data.name = name
        obj.data.materials.append(mat)
        if bevel:
            b = obj.modifiers.new('Plate edge chamfer', 'BEVEL')
            b.width, b.segments, b.limit_method = bevel, 1, 'ANGLE'
            b.angle_limit = math.radians(BEVEL_ANGLE)
        return obj

    def box(self, name, loc, dims, mat, bevel=.15):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        obj = bpy.context.object
        obj.dimensions = dims
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        return self.finish(obj, name, mat, bevel)

    def cylinder(self, name, loc, radius, depth, mat, axis='Z', vertices=12, bevel=.08):
        """A round by the segment rule (at least `vertices` sides), smooth-shaded with sharp caps."""
        n = sides(radius, vertices)
        bpy.ops.mesh.primitive_cylinder_add(vertices=n, radius=radius, depth=depth, location=loc)
        obj = bpy.context.object
        if axis == 'X':
            obj.rotation_euler[1] = math.pi / 2
        elif axis == 'Y':
            obj.rotation_euler[0] = math.pi / 2
        if n > 6:
            round_shading(obj)
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


def edge_treatment(kit):
    """The shared bevel table (ship-detail-guide.md, Edge treatment), the Lasher's pass: every
    hull part with no edge chamfer of its own gets an angle-limited, one-segment bevel sized
    by its part (hull plates 0.12 m, modules 0.06 m, fittings 0.03 m, small parts 0.015 m).
    Turrets and hardpoints are the turret kit's; glazing and near-black recess parts stay
    sharp (a chamfer there catches no highlight), and so do cutters and the scale figure."""
    skip = ('Weapon ', 'Turret ', 'Temporary ', 'Scar cutter', 'Scale figure')
    sharp = {kit.glass.name, kit.dark.name, 'Wheelhouse glazing'}
    for ob in list(bpy.data.objects):
        if ob.type != 'MESH' or ob.name.startswith(skip) or ob.hide_render:
            continue
        if any(m.type == 'BEVEL' for m in ob.modifiers):
            continue        # a chamfer of the part's own is its edge treatment
        if {m.name for m in ob.data.materials if m} & sharp:
            continue
        span = sorted(ob.dimensions)[1]*MPU
        kind = 'hull' if span > 5 else 'module' if span > 1 else 'fitting' if span > .3 else 'small'
        b = ob.modifiers.new('Edge treatment', 'BEVEL')
        b.width, b.segments, b.limit_method = sdk.BEVEL_M[kind]/MPU, 1, 'ANGLE'
        b.angle_limit = math.radians(70)   # only edges sharper than 70 deg: never soft creases on a curve
        b.use_clamp_overlap = True
