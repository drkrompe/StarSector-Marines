"""Castle's clothes after her turnaround (`turnaround/castle-turnaround-body-v1.png`).

The concept dresses her in a worn charcoal-olive duty jacket, quilted and weathered, hip length,
open over a faded cream undershirt, a stand collar, a flap pocket on the left breast and on the
left upper sleeve, cuffs rolled once (not modelled); a canvas belt with a squared buckle; dark olive cargo
trousers with a flap pocket on each thigh, bloused over laced work boots.

MakeHuman's `male_casualsuit05` (a jacket over a shirt, with trousers) is the base: one mesh on
one texture atlas. Its faces are sorted by sampling that atlas at each face's UV centre (grey
green is the jacket, the striped shirt is her undershirt, denim is the trousers), and each part
takes a material of its own built from MakeHuman's normal and ambient-occlusion maps (seams,
stitching, pocket outlines) under a procedural weathered fabric. What the pack has no piece for
is modelled here on the clothes' own surface and parented to the nearest bone: the pockets, the
belt and buckle.

Called from build_castle.py inside Blender, in MPFB's rest frame (she faces -Y, her left +X).
"""
import math
from pathlib import Path

SUIT, BOOTS = "male_casualsuit05", "shoes03"

# linear RGB, read against the turnaround
JACKET = (0.075, 0.072, 0.050)       # charcoal-olive, faded
UNDERSHIRT = (0.42, 0.37, 0.30)      # cream, faded and grubby
TROUSERS = (0.060, 0.058, 0.040)     # dark olive canvas
BOOT = (0.040, 0.030, 0.022)         # brown-black leather
BUCKLE = (0.35, 0.33, 0.30)
BELT = (0.050, 0.045, 0.032)


def _image(path, non_color=False):
    import bpy
    img = bpy.data.images.load(str(path), check_existing=True)
    if non_color:
        img.colorspace_settings.name = "Non-Color"
    return img


def fabric(name, rgb, *, normal=None, ao=None, diffuse=None, quilt=0.0, mottle=0.35, rough=0.85,
           bump=0.25, wear=0.0):
    """A weathered fabric: base colour mottled by noise, darkened by the asset's AO, its seams
    from the asset's normal map, and (quilt > 0) the concept's faint diamond quilting."""
    import bpy
    m = bpy.data.materials.new("Castle " + name)
    m.diffuse_color = (*rgb, 1.0)
    m.roughness = rough
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = rough
    uv = N.new("ShaderNodeTexCoord")
    noise = N.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 18.0
    noise.inputs["Detail"].default_value = 8.0
    noise.inputs["Roughness"].default_value = 0.62
    L.new(uv.outputs["Object"], noise.inputs["Vector"])
    ramp = N.new("ShaderNodeMapRange")
    ramp.inputs["To Min"].default_value = 1.0 - mottle
    ramp.inputs["To Max"].default_value = 1.0 + mottle
    L.new(noise.outputs["Fac"], ramp.inputs["Value"])
    base = N.new("ShaderNodeRGB")
    base.outputs[0].default_value = (*rgb, 1.0)
    col = N.new("ShaderNodeMix")
    col.data_type, col.blend_type = "RGBA", "MULTIPLY"
    col.inputs["Factor"].default_value = 1.0
    L.new(base.outputs[0], col.inputs["A"])
    L.new(ramp.outputs["Result"], col.inputs["B"])
    out = col.outputs["Result"]
    if diffuse is not None:     # the asset's own picture as a soft overlay (leather grain, laces)
        t = N.new("ShaderNodeTexImage")
        t.image = _image(diffuse)
        L.new(uv.outputs["UV"], t.inputs["Vector"])
        bw = N.new("ShaderNodeRGBToBW")
        L.new(t.outputs["Color"], bw.inputs["Color"])
        k = N.new("ShaderNodeMapRange")
        k.inputs["To Min"].default_value, k.inputs["To Max"].default_value = 0.55, 1.6
        L.new(bw.outputs["Val"], k.inputs["Value"])
        mul = N.new("ShaderNodeMix")
        mul.data_type, mul.blend_type = "RGBA", "MULTIPLY"
        mul.inputs["Factor"].default_value = 1.0
        L.new(out, mul.inputs["A"])
        L.new(k.outputs["Result"], mul.inputs["B"])
        out = mul.outputs["Result"]
    if ao is not None:
        t = N.new("ShaderNodeTexImage")
        t.image = _image(ao, non_color=True)
        L.new(uv.outputs["UV"], t.inputs["Vector"])
        mul = N.new("ShaderNodeMix")
        mul.data_type, mul.blend_type = "RGBA", "MULTIPLY"
        mul.inputs["Factor"].default_value = 0.85
        L.new(out, mul.inputs["A"])
        L.new(t.outputs["Color"], mul.inputs["B"])
        out = mul.outputs["Result"]
    if wear > 0:                 # paler, rubbed patches
        w = N.new("ShaderNodeTexNoise")
        w.inputs["Scale"].default_value = 6.0
        w.inputs["Detail"].default_value = 4.0
        L.new(uv.outputs["Object"], w.inputs["Vector"])
        cr = N.new("ShaderNodeMapRange")
        cr.inputs["From Min"].default_value, cr.inputs["From Max"].default_value = 0.55, 0.75
        cr.inputs["To Max"].default_value = wear
        L.new(w.outputs["Fac"], cr.inputs["Value"])
        light = N.new("ShaderNodeMix")
        light.data_type, light.blend_type = "RGBA", "SCREEN"
        light.inputs["B"].default_value = (0.30, 0.27, 0.20, 1.0)
        L.new(cr.outputs["Result"], light.inputs["Factor"])
        L.new(out, light.inputs["A"])
        out = light.outputs["Result"]
    L.new(out, bsdf.inputs["Base Color"])

    height = None
    if quilt > 0:
        # Two diagonal bands crossing: a diamond lattice about 4 cm across, as a height.
        waves = []
        for ang in (45.0, -45.0):
            mp = N.new("ShaderNodeMapping")
            mp.inputs["Rotation"].default_value = (0.0, math.radians(ang), 0.0)
            L.new(uv.outputs["Object"], mp.inputs["Vector"])
            wv = N.new("ShaderNodeTexWave")
            wv.wave_type, wv.bands_direction, wv.wave_profile = "BANDS", "X", "SIN"
            wv.inputs["Scale"].default_value = 24.0
            wv.inputs["Distortion"].default_value = 1.2
            L.new(mp.outputs["Vector"], wv.inputs["Vector"])
            waves.append(wv)
        mn = N.new("ShaderNodeMath")
        mn.operation = "MINIMUM"
        L.new(waves[0].outputs["Fac"], mn.inputs[0])
        L.new(waves[1].outputs["Fac"], mn.inputs[1])
        height = mn.outputs[0]
    nrm_vec = None
    if normal is not None:
        t = N.new("ShaderNodeTexImage")
        t.image = _image(normal, non_color=True)
        L.new(uv.outputs["UV"], t.inputs["Vector"])
        nm = N.new("ShaderNodeNormalMap")
        nm.inputs["Strength"].default_value = 1.2
        L.new(t.outputs["Color"], nm.inputs["Color"])
        nrm_vec = nm.outputs["Normal"]
    b = N.new("ShaderNodeBump")
    b.inputs["Strength"].default_value = bump * (1.0 if height is not None else 0.5)
    b.inputs["Distance"].default_value = 0.002
    L.new(height if height is not None else noise.outputs["Fac"], b.inputs["Height"])
    if nrm_vec is not None:
        L.new(nrm_vec, b.inputs["Normal"])
    L.new(b.outputs["Normal"], bsdf.inputs["Normal"])
    return m


def sort_suit(suit, diffuse_path):
    """Material index per face from the suit atlas: 0 jacket, 1 undershirt, 2 trousers.

    Faces are grouped into UV islands (faces sharing an edge whose UVs agree on both ends), and
    each island is judged by its mean colour: one face alone can sit on a faded patch of denim or
    a black stripe of the shirt, an island cannot."""
    import bpy
    import numpy as np
    img = bpy.data.images.load(str(diffuse_path), check_existing=True)
    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)
    me = suit.data
    uvl = me.uv_layers.active.data

    def key(uv):
        return (round(uv.x, 5), round(uv.y, 5))

    parent = list(range(len(me.polygons)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    edge_faces = {}
    for poly in me.polygons:
        n = len(poly.loop_indices)
        for k in range(n):
            li, lj = poly.loop_indices[k], poly.loop_indices[(k + 1) % n]
            va, vb = me.loops[li].vertex_index, me.loops[lj].vertex_index
            ua, ub = key(uvl[li].uv), key(uvl[lj].uv)
            e = (va, vb) if va < vb else (vb, va)
            uvs = (ua, ub) if va < vb else (ub, ua)
            for f, other in edge_faces.get(e, []):
                if other == uvs:
                    parent[find(f)] = find(poly.index)
            edge_faces.setdefault(e, []).append((poly.index, uvs))

    sums = {}
    for poly in me.polygons:
        us = [uvl[i].uv for i in poly.loop_indices]
        u = sum(x.x for x in us) / len(us)
        v = sum(x.y for x in us) / len(us)
        p = px[min(h - 1, max(0, int(v * h))), min(w - 1, max(0, int(u * w)))]
        s_ = sums.setdefault(find(poly.index), [0.0, 0.0, 0.0, 0])
        s_[0] += p[0]
        s_[1] += p[1]
        s_[2] += p[2]
        s_[3] += 1
    kind = {}
    for root, (r, g, b, n) in sums.items():
        r, g, b = r / n, g / n, b / n
        if b > r * 1.25:
            kind[root] = 2                           # denim
        elif r > g * 1.3 or abs(r - g) + abs(g - b) < 0.02 and max(r, g, b) < 0.09:
            kind[root] = 1                           # the red-black-white striped shirt
        else:
            kind[root] = 0                           # the grey-green jacket
    counts = [0, 0, 0]
    islands = {}
    for poly in me.polygons:
        poly.material_index = kind[find(poly.index)]
        counts[poly.material_index] += 1
        islands.setdefault(find(poly.index), []).append(poly.index)
    return counts, list(islands.values())


def strip_collars(suit, islands, shoulder_z):
    """Take off the blazer's lapels and the shirt's collar: the concept's jacket has a stand
    collar (modelled in dress) over a crew-neck undershirt. The lapels are the two narrow jacket
    islands running up the chest to the neck; the shirt collar is the shirt islands entirely at
    the neck. Returns how many faces went."""
    import bmesh
    me = suit.data
    gone = set()
    for faces in islands:
        mat = me.polygons[faces[0]].material_index
        vs = [me.vertices[v].co for f in faces for v in me.polygons[f].vertices]
        zmin, zmax = min(v.z for v in vs), max(v.z for v in vs)
        width = max(v.x for v in vs) - min(v.x for v in vs)
        lapel = mat == 0 and len(faces) > 60 and width < 0.15 and zmin > shoulder_z - 0.20 and zmax > shoulder_z + 0.06
        collar = mat == 1 and zmin > shoulder_z - 0.02
        if lapel or collar:
            gone.update(faces)
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.faces.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[bm.faces[i] for i in sorted(gone)], context="FACES_ONLY")
    loose = [v for v in bm.verts if not v.link_faces]
    bmesh.ops.delete(bm, geom=loose, context="VERTS")
    bm.to_mesh(me)
    bm.free()
    me.update()
    return len(gone)


def dress(bpy, suit, boots, rig, body, data_dir, add_part, bone_parent):
    """Materials for the suit and boots, and the modelled pieces. add_part(op, name, mat, **kw)
    makes a smooth object painted with a material; bone_parent(ob, bone) parents it rigidly."""
    from mathutils import Matrix, Vector
    from mathutils.bvhtree import BVHTree

    d = Path(data_dir)
    sd, bd = d / SUIT, d / BOOTS
    jacket = fabric("duty jacket", JACKET, normal=sd / (SUIT + "_normal.png"), ao=sd / (SUIT + "_ao.png"),
                    quilt=1.0, mottle=0.45, wear=0.15)
    shirt = fabric("undershirt", UNDERSHIRT, ao=sd / (SUIT + "_ao.png"), mottle=0.25, bump=0.3)
    trousers = fabric("cargo trousers", TROUSERS, normal=sd / (SUIT + "_normal.png"), ao=sd / (SUIT + "_ao.png"),
                      mottle=0.45, wear=0.3)
    boot = fabric("work boots", BOOT, diffuse=bd / (BOOTS + "_diffuse.png"), mottle=0.3, rough=0.6, wear=0.4)
    belt = fabric("belt", BELT, mottle=0.3, rough=0.7)
    # The modelled pieces carry their own primitive UVs, so they take the same fabrics without the
    # suit's normal and AO maps (which would paint the atlas's seams across them).
    jacket_plain = fabric("duty jacket pieces", JACKET, mottle=0.45, wear=0.15)
    trousers_plain = fabric("cargo trouser pieces", TROUSERS, mottle=0.45, wear=0.3)
    metal = fabric("buckle", BUCKLE, mottle=0.2, rough=0.4)
    metal.node_tree.nodes["Principled BSDF"].inputs["Metallic"].default_value = 0.9
    for m in (jacket, shirt, trousers, boot, belt, metal, jacket_plain, trousers_plain):
        m.diffuse_color = (*m.diffuse_color[:3], 1.0)

    suit.data.materials.clear()
    for m in (jacket, shirt, trousers):
        suit.data.materials.append(m)
    counts, islands = sort_suit(suit, sd / (SUIT + "_diffuse.png"))
    counts.append(strip_collars(suit, islands, rig.data.bones["upperarm01.L"].head_local.z))
    suit.color = jacket.diffuse_color
    boots.data.materials.clear()
    boots.data.materials.append(boot)
    boots.color = boot.diffuse_color
    for m in (jacket, shirt, trousers, boot, belt, metal, jacket_plain, trousers_plain):
        bpy.data.materials[m.name]["castle_part"] = True

    # ---- the suit's rest surface, for laying pieces on it
    sw = suit.matrix_world
    sverts = [sw @ v.co for v in suit.data.vertices]
    tree = BVHTree.FromPolygons(sverts, [list(p.vertices) for p in suit.data.polygons])
    bones = rig.data.bones

    def hit(origin, direction):
        loc, nrm, _, _ = tree.ray_cast(Vector(origin), Vector(direction).normalized())
        if loc is None:
            raise RuntimeError("no suit surface from %s along %s" % (origin, direction))
        if nrm.dot(Vector(direction)) > 0:
            nrm = -nrm
        return loc, nrm

    def patch(name, mat, at, nrm, up, w, h, t):
        """A thin box w x h x t lying on the surface at `at`, facing out along `nrm`."""
        z = nrm.normalized()
        x = Vector(up).cross(z).normalized()
        y = z.cross(x)
        ob = add_part(bpy.ops.mesh.primitive_cube_add, name, mat, size=1.0)
        ob.data.materials.clear()
        ob.data.materials.append(mat)
        ob.data.transform(Matrix.Diagonal((w, h, t, 1.0)))       # real size, so the fabric's scale is true
        ob.matrix_world = Matrix.Translation(at + z * (t / 2 + 0.0015)) @ Matrix((x, y, z)).transposed().to_4x4()
        bev = ob.modifiers.new("Bevel", "BEVEL")
        bev.width, bev.segments = min(t * 0.45, 0.004), 2
        return ob

    def pocket(name, at, nrm, up, w, h, bone):
        """A patch pocket and its flap."""
        z = nrm.normalized()
        y = z.cross(Vector(up).cross(z).normalized())
        body_ = patch(name, jacket_plain, at, nrm, up, w, h, 0.006)
        flap = patch(name + " flap", jacket_plain, at + y * (h / 2 - 0.018) + z * 0.006, nrm, up, w + 0.008, 0.036, 0.005)
        for ob in (body_, flap):
            bone_parent(ob, bone)
        return body_, flap

    def lowest_mat(idx):
        zs = [sverts[v].z for p in suit.data.polygons if p.material_index == idx for v in p.vertices]
        return min(zs), max(zs)

    up = (0.0, 0.0, 1.0)
    spine_bones = sorted((b for b in bones if b.name.startswith("spine")), key=lambda b: b.head_local.z)
    chest_bone = spine_bones[-1].name if spine_bones else "spine01"
    hip_bone = spine_bones[0].name if spine_bones else "root"
    sh_l = bones["upperarm01.L"].head_local

    # Left breast flap pocket.
    zc = sh_l.z - 0.13
    at, n = hit((0.085, -1.0, zc), (0, 1, 0))
    pocket("chest pocket", at, n, up, 0.105, 0.12, chest_bone)

    # Left upper sleeve pocket, on the outside of the arm.
    ua = bones["upperarm01.L"]
    mid = ua.head_local.lerp(bones["lowerarm01.L"].head_local, 0.38)
    at, n = hit((mid.x + 1.0, mid.y, mid.z), (-1, 0, 0))
    arm_up = (bones["upperarm01.L"].head_local - bones["lowerarm01.L"].head_local).normalized()
    pocket("sleeve pocket", at, n, arm_up, 0.075, 0.09, "upperarm01.L")

    # Belt: an elliptical band round the trousers' top, and a squared buckle at the front.
    t0, t1 = lowest_mat(2)
    zb = t1 - 0.03
    ring = [v for v in sverts if abs(v.z - zb) < 0.015]
    xs, ys = [v.x for v in ring], [v.y for v in ring]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    rx, ry = (max(xs) - min(xs)) / 2 + 0.006, (max(ys) - min(ys)) / 2 + 0.006
    band = add_part(bpy.ops.mesh.primitive_cylinder_add, "belt", belt, vertices=48, radius=1.0, depth=0.038,
                    end_fill_type="NOTHING", location=(cx, cy, zb))
    band.data.materials.clear()
    band.data.materials.append(belt)
    band.scale = (rx, ry, 1.0)
    band.modifiers.new("Thickness", "SOLIDIFY").thickness = 0.005
    bone_parent(band, hip_bone)
    at, n = hit((0.0, -1.0, zb), (0, 1, 0))
    buckle = patch("belt buckle", metal, at, n, up, 0.055, 0.045, 0.008)
    bone_parent(buckle, hip_bone)

    # Thigh cargo pockets, on the outside of each thigh.
    for side, sgn in (("L", 1.0), ("R", -1.0)):
        hip, knee = bones["upperleg01." + side].head_local, bones["lowerleg01." + side].head_local
        p = hip.lerp(knee, 0.55)
        at, n = hit((p.x + sgn * 1.0, p.y - 0.01, p.z), (-sgn, 0, 0))
        leg_up = (hip - knee).normalized()
        b_, f_ = pocket("cargo pocket " + side, at, n, leg_up, 0.13, 0.16, "upperleg02." + side
                        if ("upperleg02." + side) in bones else "upperleg01." + side)
        for ob in (b_, f_):
            ob.data.materials.clear()
            ob.data.materials.append(trousers_plain)

    # No modelled cuff roll: rigid on the forearm, a torus read as a bracelet round the wrist.
    return counts
