"""Shared Blender helpers for the Manticore (P) destroyer.

A standalone copy of the few Hound kit helpers this build needs (materials,
primitives, chamfered prisms, booleans, a downward ray-cast); it deliberately
does not import another hull's package, which other sessions are editing.
Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one sprite pixel per
unit about the .ship centre.

The Lasher is a distant escort (1.5-3 km from camera in C1 v2, 20-40 px
long at 1080p), so the kit is deliberately light: few materials, flat
shading, one-segment chamfers that the export bakes.
"""
import math
import sys
from pathlib import Path
import bmesh
import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402


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


def livery(name, base, finish='paint', vary=.12, grime=.0):
    """Pass 6 (light texturing): the pirate livery as a procedural weathered paint.

    The shared turret kit's weathered paint (turret_kit.paint_material, the
    same tree the guns wear): low mottle, edges chipped through to primer and
    bare steel, grime in crevices from ambient occlusion. On top of it, what
    "heavily modified - and poorly maintained" (manticore_pirates.skin) asks
    for: every plate a slightly different shade of the same paint (Object Info
    random, +-`vary` in value), as patched and re-sprayed armour is, and with
    `grime` > 0 a large-scale streaky darkening (noise stretched along hull Z,
    on object coordinates) of old soot and leaks. Procedural, so nothing to
    pack, and baked geometry carries it unchanged."""
    m = tk.paint_material(name, base, finish, base=base)
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N['Principled BSDF']
    # The turret kit re-weathers a hull material from this value (turret_kit._sprite_mats).
    bsdf.inputs['Base Color'].default_value = (*base, 1)
    link = bsdf.inputs['Base Color'].links[0]
    src = link.from_socket
    info = N.new('ShaderNodeObjectInfo')
    rng = N.new('ShaderNodeMapRange')
    rng.inputs['To Min'].default_value, rng.inputs['To Max'].default_value = 1-vary, 1+vary
    L.new(info.outputs['Random'], rng.inputs['Value'])
    gain = rng.outputs['Result']
    if grime:
        tc = N.new('ShaderNodeTexCoord')
        mp = N.new('ShaderNodeMapping')
        mp.inputs['Scale'].default_value = (.08, .08, .5)
        L.new(tc.outputs['Object'], mp.inputs['Vector'])
        nz = N.new('ShaderNodeTexNoise')
        nz.inputs['Scale'].default_value = 1.0
        nz.inputs['Detail'].default_value = 4
        L.new(mp.outputs['Vector'], nz.inputs['Vector'])
        g = N.new('ShaderNodeMapRange')
        g.inputs['From Min'].default_value, g.inputs['From Max'].default_value = .5, .75
        g.inputs['To Min'].default_value, g.inputs['To Max'].default_value = 1.0, 1-grime
        L.new(nz.outputs['Fac'], g.inputs['Value'])
        mul = N.new('ShaderNodeMath')
        mul.operation = 'MULTIPLY'
        L.new(gain, mul.inputs[0])
        L.new(g.outputs['Result'], mul.inputs[1])
        gain = mul.outputs[0]
    mix = N.new('ShaderNodeMix')
    mix.data_type, mix.blend_type = 'RGBA', 'MULTIPLY'
    mix.inputs['Factor'].default_value = 1.0
    L.new(src, mix.inputs['A'])
    comb = N.new('ShaderNodeCombineColor')
    for k in range(3):
        L.new(gain, comb.inputs[k])
    L.new(comb.outputs['Color'], mix.inputs['B'])
    L.new(mix.outputs['Result'], bsdf.inputs['Base Color'])
    m['livery'] = 'pirate (manticore_pirates.skin)'
    return m


class Kit:
    def __init__(self):
        # Pirate scheme read off manticore_pirate.png: blood-red armour plates
        # (sprite median sRGB 165,63,52, darkened for albedo), brown gunmetal
        # machinery (77,68,64), a pale command dome (222,219,218), near-black
        # recesses and warm lamps. Pass 6 (light texturing) wears them as the
        # turret kit's weathered paint plus per-plate shade and streaked grime (livery).
        self.red = livery('Manticore pirate red armour', (.30, .040, .028), vary=.22, grime=.5)
        self.steel = livery('Manticore gunmetal machinery', (.074, .058, .052), vary=.25, grime=.4)
        self.plate = livery('Manticore grey plate', (.16, .145, .14), vary=.12, grime=.25)
        self.pale = livery('Manticore pale command plating', (.52, .51, .50), vary=.06, grime=.3)
        self.dark = material('Manticore recess', (.025, .022, .02), .4, .6)
        # Round 14 (owner: the lower-deck windows "read as weird rectangle
        # lights"): dark, glossy glass that reflects the sky, with only a faint
        # warm interior glow behind it, never a lit panel.
        # Round 15 (owner, round 14: the glass still read tan): an absorbing
        # near-black tint with a low specular level, so it reads dark even under
        # a bright grey studio world, and only the faintest interior glow.
        self.glass = material('Manticore bridge glazing', (.004, .005, .006), 0, .06,
                              emission=(1.0, .78, .5), strength=.025)
        self.glass.node_tree.nodes.get('Principled BSDF').inputs['Specular IOR Level'].default_value = .25
        self.vent = material('Manticore heat vent glow', (.05, .03, .02), .3, .5,
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

    def smooth(self, obj, bevel=None, segments=3, angle=30):
        """Round 14: hard edges get a rounded multi-segment chamfer with hardened
        normals, and every face below `angle` of its neighbour shades smooth, so a
        curved form reads as curved at a close view instead of as facets."""
        me = obj.data
        me.shade_smooth()
        me.set_sharp_from_angle(angle=math.radians(angle))
        b = obj.modifiers.get('Plate edge chamfer') or obj.modifiers.new('Plate edge chamfer', 'BEVEL')
        if bevel:
            b.width = bevel
        b.segments, b.limit_method, b.angle_limit = segments, 'ANGLE', math.radians(angle)
        b.harden_normals = True
        w = obj.modifiers.new('Weighted normals', 'WEIGHTED_NORMAL')
        w.keep_sharp = True
        return obj

    def box(self, name, loc, dims, mat, bevel=.15):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        obj = bpy.context.object
        obj.dimensions = dims
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        self.finish(obj, name, mat, bevel)
        if bevel and not name.startswith('Temporary'):
            self.smooth(obj, segments=2)   # round 14: rounded plate edges, flat faces
        return obj

    def cylinder(self, name, loc, radius, depth, mat, axis='Z', vertices=12, bevel=.08):
        # Round 14 (owner: "we're getting the rough shape right, but need to add
        # detail"): no round part reads as a polygon at a close view.
        if not name.startswith('Temporary'):
            vertices = max(vertices, 48)
        bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
        obj = bpy.context.object
        if axis == 'X':
            obj.rotation_euler[1] = math.pi / 2
        elif axis == 'Y':
            obj.rotation_euler[0] = math.pi / 2
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
        if not name.startswith('Temporary'):
            shade_round(obj)
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
    return list(port) + [(x, -y) for x, y in reversed(port)]


def armour_plates(kit, ob, breaks=(), gap=.08, rise=.45, plate_mat=None, seam_mat=None, skip_up=False,
                  angle=35, bolts=1.4):
    """Armour laid as the Onslaught lays it (owner, round 15: the plating gaps
    "seem weird"; take the cues from the Onslaught for the armour facing combat).
    Each plane in `breaks` ((point, normal)) splits the part where one plate ends
    and the next begins; faces of the part's armour material (`plate_mat`) are
    then grouped into plates across creases gentler than `angle`, so a plate is a
    thick slab bent over the form, not one tile per facet. Each plate is lifted
    `rise` with a chamfered border, leaving a narrow seam `gap` wide in
    `seam_mat` between neighbours, and (when `bolts` is a pitch) carries a row
    of bolt heads just inside its edges. With `skip_up`, level decks where
    fittings stand are left whole. Returns the bolts object, or None."""
    me = ob.data
    plate_mat = plate_mat or me.materials[0]
    seam_mat = seam_mat or kit.steel
    names = [m.name for m in me.materials]
    if seam_mat.name not in names:
        me.materials.append(seam_mat)
        names.append(seam_mat.name)
    pi = names.index(plate_mat.name) if plate_mat.name in names else 0
    si = names.index(seam_mat.name)
    bm = bmesh.new()
    bm.from_mesh(me)
    planes = [(Vector(co), Vector(no).normalized()) for co, no in breaks]
    for co, no in planes:
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:]+bm.edges[:]+bm.faces[:], plane_co=co, plane_no=no)

    def on_break(e):
        return any(all(abs((v.co-co).dot(no)) < 1e-3 for v in e.verts) for co, no in planes)

    ok = {f for f in bm.faces if f.material_index == pi and not (skip_up and f.normal.z > .95)}
    lim = math.radians(angle)
    regions, seen = [], set()
    for f0 in ok:
        if f0 in seen:
            continue
        reg, stack = [], [f0]
        seen.add(f0)
        while stack:
            f = stack.pop()
            reg.append(f)
            for e in f.edges:
                if on_break(e):
                    continue
                for g in e.link_faces:
                    if g in ok and g not in seen and f.normal.angle(g.normal, 0) < lim:
                        seen.add(g)
                        stack.append(g)
        area = sum(f.calc_area() for f in reg)
        if area > 1.0:
            regions.append(reg)
    studs = []
    for reg in regions:
        res = bmesh.ops.inset_region(bm, faces=reg, thickness=gap, depth=rise, use_even_offset=True,
                                     use_boundary=True)
        ring = set(res['faces'])
        for f in ring:
            f.material_index = si
        if not bolts:
            continue
        rs = set(reg)
        for f in reg:
            c = f.calc_center_median()
            for e in f.edges:
                if not any(g in ring for g in e.link_faces):
                    continue
                a, b = e.verts[0].co.copy(), e.verts[1].co.copy()
                ln = (b-a).length
                if ln < bolts*1.2:
                    continue
                d = (b-a).normalized()
                inward = f.normal.cross(d)
                if inward.dot(c-a) < 0:
                    inward = -inward
                n = int(ln/bolts)
                for k in range(n):
                    p = a+d*((k+.5)*ln/n)+inward*.4
                    studs.append((p, f.normal.copy()))
    bm.to_mesh(me)
    bm.free()
    me.update()
    mod = ob.modifiers.get('Plate edge chamfer')
    if mod:
        mod.width, mod.segments = min(mod.width, .1), 2
    if not studs:
        return None
    verts, faces = [], []
    for p, n in studs:
        side = n.orthogonal().normalized()
        up = n.cross(side)
        base = len(verts)
        for h in (-.05, .12):
            for k in range(6):
                t = 2*math.pi*k/6
                verts.append(tuple(p+n*h+side*(.11*math.cos(t))+up*(.11*math.sin(t))))
        faces += [tuple(range(base+5, base-1, -1)), tuple(range(base+6, base+12))]
        faces += [(base+k, base+(k+1) % 6, base+6+(k+1) % 6, base+6+k) for k in range(6)]
    return kit.mesh(ob.name+' bolts', verts, faces, kit.steel, 0)


def hull_plating(kit, ob, breaks=(), skip_up=True):
    """Ordinary hull plating, not armour: fine plate lines only (narrow, shallow
    seams at `breaks` and sharp creases), no bolts."""
    armour_plates(kit, ob, breaks, gap=.05, rise=.1, skip_up=skip_up, angle=35, bolts=None)
    return ob


def shade_round(ob, angle=30):
    """Smooth shading across faces meeting below `angle`; harder edges stay sharp,
    so a round part shades round and its caps and corners stay crisp."""
    ob.data.shade_smooth()
    ob.data.set_sharp_from_angle(angle=math.radians(angle))
    return ob


def revolve_x(kit, name, profile, y, z, mat, n=12):
    """Closed solid of revolution about an X-parallel axis through (y, z):
    profile is [(x, r), ...] round the section, closed."""
    n = max(n, 48)   # round 14: no faceted drive cans or bells
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
    return ob if name.startswith('Temporary') else shade_round(ob)


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


def sweep(kit, name, path, profile, mat, s=1, bevel=.12):
    """One constant cross-section carried along an open plan polyline, mitred at
    every bend, so each face of the section is one long unbroken panel and each
    corner of it one straight crease the whole length of the path.
    `profile` is a closed list of (d, z): d is the distance to the left of the
    direction of travel (to the right when s is -1, for a mirrored path)."""
    n, m = len(path), len(profile)
    normals = []
    for a, b in zip(path, path[1:]):
        dx, dy = b[0]-a[0], b[1]-a[1]
        ln = math.hypot(dx, dy)
        normals.append((-dy/ln*s, dx/ln*s))
    verts = []
    for i, (x, y) in enumerate(path):
        n0 = normals[max(i-1, 0)]
        n1 = normals[min(i, len(normals)-1)]
        k = 1/(1+n0[0]*n1[0]+n0[1]*n1[1])
        mx, my = (n0[0]+n1[0])*k, (n0[1]+n1[1])*k
        verts += [(x+mx*d, y+my*d, z) for d, z in profile]
    faces = [tuple(range(m)), tuple(reversed(range((n-1)*m, n*m)))]
    for i in range(n-1):
        faces += [(i*m+j, (i+1)*m+j, (i+1)*m+(j+1) % m, i*m+(j+1) % m) for j in range(m)]
    return kit.mesh(name, verts, faces, mat, bevel)  # mesh() makes the normals face out
