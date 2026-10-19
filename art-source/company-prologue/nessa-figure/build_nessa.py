"""Nessa Venn as a 3D figure: a MakeHuman body built by MPFB, rigged and posed, in metres.

    python build_nessa.py [--pose folded|slate] [--mask face|neck] [--out PATH]   # Blender 5.2, headless

Nessa owns Kilo Nine, the private yard where Second Wind lands (`character.md` beside this
file): a woman of 58, 1.72 m, solid and square, warm medium-brown skin, short iron-grey hair,
a long ochre canvas work coat over dark trousers and boots, a biofilter mask. Built the way
Castle is (`castle-figure/build_castle.py`): the figure owns silhouette, proportion, pose and
light, and an ImageGen repaint owns the face and cloth finish (`geometry-first-imagegen.md`).

--pose folded (the default) is C5's: standing apart, arms folded, weight back.
--pose slate holds the estimate's work slate out in her right hand, for the Y1 picture.
--mask face (the default) wears the biofilter, as everyone does outdoors on Jangala;
--mask neck hangs it under her chin, for indoors and the portrait.

What this builds, in the file's own frame (metres, z up from the soles, she faces +X):

- collection 'Nessa figure', holding everything below; append it, never link pieces of it;
- 'Nessa figure root', an empty at her feet: place and turn this, nothing else;
- 'Nessa rig', MPFB's default skeleton, posed;
- the body, eyes, eyebrows, hair, work suit and boots MPFB fits, from MakeHuman's CC0 pack;
- modelled here, since the pack has no long coat: the coat's skirt, skinned to her hips and
  legs, its stand collar, the mask and (slate pose) the slate.

Materials are flat colours (diffuse_color for Workbench, the same base colour in a Principled
BSDF). The .blend is a build output (gitignored).
"""
import math
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "nessa-figure.blend"
COLLECTION = "Nessa figure"
ROOT = "Nessa figure root"

# MakeHuman's age slider: 0.5 is 25 years, 1.0 is 90. 58 is about 0.754.
PHENOTYPE = dict(gender=0.0, age=0.754, muscle=0.64, weight=0.64, proportions=0.45, height=0.57,
                 cupsize=0.4, firmness=0.35,
                 race=dict(caucasian=0.28, asian=0.12, african=0.60))     # warm medium brown, Mara's
ASSETS = dict(eyes="high-poly.mhclo", eyebrows="eyebrow010.mhclo", eyelashes="eyelashes01.mhclo",
              hair="short02.mhclo")
CLOTHES = ["male_casualsuit02.mhclo", "shoes03.mhclo"]

COLOURS = {
    "skin": (0.27, 0.15, 0.095),
    "hair": (0.36, 0.355, 0.345),        # iron grey
    "coat": (0.38, 0.24, 0.085),         # weathered ochre canvas
    "coat lining": (0.20, 0.13, 0.06),
    "trousers": (0.07, 0.07, 0.068),
    "boots": (0.07, 0.055, 0.045),
    "eye": (0.12, 0.07, 0.04),
    "mask": (0.05, 0.055, 0.055),
    "filter": (0.16, 0.17, 0.16),
    "strap": (0.08, 0.08, 0.075),
    "slate": (0.10, 0.11, 0.11),
    "screen": (0.30, 0.42, 0.36),
}

COAT_SKIRT_DROP = 0.11      # skirt hem this far below the knee joint
COAT_FLARE = 0.06           # extra radius at the hem
COAT_GAP_DEG = 26.0         # the open front, total angle


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    cmd = [BLENDER, "--background", "--python", str(Path(__file__).resolve()), "--", *argv]
    sys.exit(subprocess.call(cmd))


def blender_main(argv):
    import bmesh
    import bpy
    from mathutils import Matrix, Vector
    from mathutils.kdtree import KDTree

    out = Path(argv[argv.index("--out") + 1]) if "--out" in argv else BLEND
    pose = argv[argv.index("--pose") + 1] if "--pose" in argv else "folded"
    mask = argv[argv.index("--mask") + 1] if "--mask" in argv else "face"
    if pose not in ("folded", "slate") or mask not in ("face", "neck"):
        sys.exit("--pose folded|slate, --mask face|neck")

    try:
        import addon_utils
        addon_utils.enable("bl_ext.blender_org.mpfb", default_set=True)
        from bl_ext.blender_org.mpfb.services.humanservice import HumanService
        from bl_ext.blender_org.mpfb.services.assetservice import AssetService
    except Exception as exc:     # noqa: BLE001
        sys.exit("MPFB is not installed in this Blender (Get Extensions > MPFB): %s" % exc)
    for part, name in [*((k, v) for k, v in ASSETS.items()), *(("clothes", c) for c in CLOTHES)]:
        if AssetService.find_asset_absolute_path(name, asset_subdir=part) is None:
            sys.exit("MakeHuman asset %s/%s is missing: install the system asset pack in MPFB" % (part, name))

    for ob in list(bpy.data.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    for col in list(bpy.data.collections):
        bpy.data.collections.remove(col)
    scene = bpy.context.scene
    view = bpy.context.view_layer

    # ---- the body
    info = HumanService._create_default_human_info_dict()
    info["phenotype"] = PHENOTYPE
    info["name"] = "Nessa"
    info["rig"] = "default"
    info.update(ASSETS)
    info["clothes"] = list(CLOTHES)
    info["skin_material_type"] = "NONE"
    settings = HumanService.get_default_deserialization_settings()
    settings.update(scale=0.1, subdiv_levels=1, override_skin_model="NONE")
    body = HumanService.deserialize_from_dict(info, settings)
    rig = body.parent
    rig.name = rig.data.name = "Nessa rig"
    body.name = "Nessa body"
    parts = [o for o in rig.children if o.type == "MESH"]
    for o in parts:
        if o is not body:
            o.name = "Nessa " + o.name.split(".", 1)[-1]

    def flat(name):
        m = bpy.data.materials.get("Nessa " + name) or bpy.data.materials.new("Nessa " + name)
        rgb = COLOURS[name]
        m.diffuse_color = (*rgb, 1.0)
        m.roughness = 0.8
        m.use_nodes = True
        bsdf = m.node_tree.nodes.get("Principled BSDF")
        if bsdf is not None:
            bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
            bsdf.inputs["Roughness"].default_value = 0.8
        return m

    def paint(ob, name):
        ob.data.materials.clear()
        ob.data.materials.append(flat(name))
        ob.color = ob.data.materials[0].diffuse_color

    by_role = {"body": "skin", "high-poly": "eye", "eyebrow": "hair", "eyelashes": "hair",
               "short02": "hair", "shoes03": "boots", "male_casualsuit02": "coat"}
    for o in parts:
        key = "body" if o is body else o.name.split(" ", 1)[1]
        paint(o, next(v for k, v in by_role.items() if key.startswith(k)))

    bones = rig.data.bones
    suit = bpy.data.objects["Nessa male_casualsuit02"]
    suit.name = "Nessa coat and trousers"
    suit.data.materials.append(flat("trousers"))
    hip_z = bones["pelvis.L"].tail_local.z if "pelvis.L" in bones else bones["upperleg01.L"].head_local.z
    hem = bones["upperleg01.L"].head_local.z + 0.05

    # ---- rest-pose shapes (shape keys on, armature off), for fitting modelled parts
    def shaped(ob):
        shown = [(m, m.show_viewport) for m in ob.modifiers]
        for m, _ in shown:
            m.show_viewport = False
        view.update()
        ev = ob.evaluated_get(bpy.context.evaluated_depsgraph_get())
        pts = [ob.matrix_world @ v.co for v in ev.data.vertices]
        for m, s in shown:
            m.show_viewport = s
        view.update()
        return pts

    knee_z = bones["lowerleg01.L"].head_local.z
    sverts = shaped(suit)
    for poly in suit.data.polygons:
        c = sum((sverts[i] for i in poly.vertices), Vector()) / len(poly.vertices)
        if (c.z < hem and abs(c.x) < 0.24) or c.z < knee_z + 0.1:
            poly.material_index = 1
    bverts = shaped(body)
    bgroups = {g.index: g.name for g in body.vertex_groups}
    eye_z = (bones["eye.R"].head_local.z + bones["eye.L"].head_local.z) / 2
    eye_y = bones["eye.R"].head_local.y
    knee_z = bones["lowerleg01.L"].head_local.z
    top_z = max(v.z for v in bverts)

    def bone_parent(ob, bone_name):
        b = bones[bone_name]
        ob.parent = rig
        ob.parent_type = "BONE"
        ob.parent_bone = bone_name
        rest = rig.matrix_world @ Matrix.Translation(b.tail_local) @ b.matrix_local.to_3x3().to_4x4()
        ob.matrix_parent_inverse = rest.inverted()

    def new_obj(name, bm, mat):
        me = bpy.data.meshes.new("Nessa " + name)
        bm.to_mesh(me)
        bm.free()
        ob = bpy.data.objects.new("Nessa " + name, me)
        scene.collection.objects.link(ob)
        paint(ob, mat)
        for p in me.polygons:
            p.use_smooth = True
        return ob

    def add(op, name, mat, **kw):
        op(**kw)
        ob = bpy.context.object
        ob.name = ob.data.name = "Nessa " + name
        paint(ob, mat)
        bpy.ops.object.shade_smooth()
        return ob

    # ---- the coat's skirt: a flared open-fronted tube from the hips to below the knee, fitted
    # round the legs in the rest pose and skinned with the nearest leg or pelvis weights.
    def limb(v_index):
        vg = body.data.vertices[v_index].groups
        if not any(bgroups.get(g.group) == "body" and g.weight > 0.5 for g in vg):
            return None
        gs = [(bgroups[g.group], g.weight) for g in vg if bgroups.get(g.group) in bones]
        if not gs:
            return None
        name = max(gs, key=lambda x: x[1])[0]
        return name if name.startswith(("pelvis", "upperleg", "lowerleg", "spine0", "root")) else None

    legs = [i for i, v in enumerate(bverts) if knee_z - 0.2 < v.z < hip_z + 0.12 and limb(i)]
    z0, z1 = hem + 0.10, knee_z - COAT_SKIRT_DROP
    rings, segs = 14, 48
    gap = math.radians(COAT_GAP_DEG) / 2
    bm = bmesh.new()
    grid = []
    for r in range(rings + 1):
        t = r / rings
        z = z0 + (z1 - z0) * t
        band = [bverts[i] for i in legs if abs(bverts[i].z - z) < 0.04] or [bverts[i] for i in legs]
        cx = sum(v.x for v in band) / len(band)
        cy = sum(v.y for v in band) / len(band)
        rx = max(abs(v.x - cx) for v in band) + 0.015 + COAT_FLARE * t
        ry = max(abs(v.y - cy) for v in band) + 0.012 + COAT_FLARE * 0.8 * t
        row = []
        for s in range(segs + 1):
            # Angle from the front (-Y), round her left, back, right, to the front again; the
            # gap widens a little toward the hem, as an unbuttoned coat hangs.
            g = gap * (0.5 + 0.5 * t)
            a = g + (2 * math.pi - 2 * g) * s / segs
            row.append(bm.verts.new((cx + math.sin(a) * rx, cy - math.cos(a) * ry, z)))
        grid.append(row)
    for r in range(rings):
        for s in range(segs):
            bm.faces.new((grid[r][s], grid[r][s + 1], grid[r + 1][s + 1], grid[r + 1][s]))
    bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.008)
    skirt = new_obj("coat skirt", bm, "coat")
    kd = KDTree(len(legs))
    for i in legs:
        kd.insert(bverts[i], i)
    kd.balance()
    vgs = {}
    for v in skirt.data.vertices:
        hits = kd.find_n(v.co, 6)
        acc = {}
        for co, i, d in hits:
            w0 = 1.0 / max(d, 0.01)
            for g in body.data.vertices[i].groups:
                if g.group in bgroups and g.weight > 1e-4 and bgroups[g.group].startswith(
                        ("pelvis", "upperleg", "lowerleg", "spine0", "root")):
                    acc[bgroups[g.group]] = acc.get(bgroups[g.group], 0.0) + g.weight * w0
        total = sum(acc.values()) or 1.0
        for name, w in acc.items():
            vg = vgs.get(name) or skirt.vertex_groups.new(name=name)
            vgs[name] = vg
            vg.add([v.index], w / total, "REPLACE")
    skirt.modifiers.new("Armature", "ARMATURE").object = rig
    skirt.parent = rig

    # ---- the coat's stand collar
    neck = bones["neck01"].head_local
    collar = add(bpy.ops.mesh.primitive_cone_add, "coat collar", "coat", vertices=24,
                 radius1=0.085, radius2=0.078, depth=0.07, end_fill_type="NOTHING",
                 location=(neck.x, neck.y + 0.012, neck.z + 0.01))
    collar.rotation_euler = (math.radians(-10), 0, 0)
    collar.modifiers.new("Thickness", "SOLIDIFY").thickness = 0.007
    bone_parent(collar, "neck01")

    # ---- the biofilter mask: a moulded half-mask over nose and mouth with two side filters and
    # a strap round the head; or hung under the chin from the strap.
    face = [v for v in bverts if abs(v.x) < 0.012 and eye_z - 0.11 < v.z < eye_z - 0.02]
    front_y = min(v.y for v in face) if face else eye_y - 0.03
    on_face = mask == "face"
    mz = eye_z - 0.062 if on_face else eye_z - 0.175
    my = front_y + (0.012 if on_face else 0.045)
    bone = "head" if on_face else "neck01"
    shell = add(bpy.ops.mesh.primitive_uv_sphere_add, "mask", "mask", segments=24, ring_count=14,
                radius=1.0, location=(0, my, mz))
    shell.scale = (0.036, 0.024, 0.040)
    shell.rotation_euler = (math.radians(-12 if on_face else 25), 0, 0)
    bone_parent(shell, bone)
    for sgn in (1, -1):
        f = add(bpy.ops.mesh.primitive_cylinder_add, "mask filter %s" % ("L" if sgn > 0 else "R"), "filter",
                vertices=20, radius=0.016, depth=0.018,
                location=(sgn * 0.033, my - 0.002, mz - 0.012))
        f.rotation_euler = (math.radians(90), 0, math.radians(sgn * 40))
        bone_parent(f, bone)
    strap = add(bpy.ops.mesh.primitive_torus_add, "mask strap", "strap", major_radius=1.0,
                minor_radius=0.05, major_segments=40, minor_segments=6,
                location=(0, eye_y + 0.075, eye_z - 0.035 if on_face else eye_z - 0.16))
    strap.scale = (0.074, 0.092, 0.06) if on_face else (0.07, 0.075, 0.06)
    strap.rotation_euler = (math.radians(-18 if on_face else 8), 0, 0)
    bone_parent(strap, bone)

    # ---- the pose
    pbs = rig.pose.bones

    def rotate_world(name, axis, deg):
        pb = pbs[name]
        h = pb.head.copy()
        r = Matrix.Rotation(math.radians(deg), 4, Vector(axis))
        pb.matrix = Matrix.Translation(h) @ r @ Matrix.Translation(-h) @ pb.matrix
        view.update()

    def frame(y, n):
        y = Vector(y).normalized()
        x = Vector(n).cross(y).normalized()
        z = x.cross(y)
        return Matrix((x, y, z)).transposed()

    def aim(name, direction):
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = rest.col[1].rotation_difference(Vector(direction).normalized())
        m = (q.to_matrix() @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def orient(name, fingers, palm, rest_palm):
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = frame(fingers, palm) @ frame(rest.col[1], rest_palm).transposed()
        m = (q @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def two_bone(side, wrist, pole):
        up = ["upperarm01." + side, "upperarm02." + side]
        lo = ["lowerarm01." + side, "lowerarm02." + side]
        a = sum(bones[b].length for b in up)
        b = sum(bones[x].length for x in lo)
        s = pbs[up[0]].head.copy()
        w = Vector(wrist)
        d = w - s
        L = min(d.length, (a + b) * 0.999)
        dn = d.normalized()
        along = (a * a - b * b + L * L) / (2 * L)
        off = math.sqrt(max(a * a - along * along, 0.0))
        p = Vector(pole) - s
        perp = (p - dn * p.dot(dn)).normalized()
        elbow = s + dn * along + perp * off
        for name in up:
            aim(name, elbow - s)
        wrist_pt = elbow + (w - elbow).normalized() * b
        for name in lo:
            aim(name, wrist_pt - elbow)
        return elbow, wrist_pt

    def curl(side, finger_deg, thumb_deg, palm):
        n = Vector(palm).normalized()
        for f in range(1, 6):
            deg = thumb_deg if f == 1 else finger_deg
            for j in range(1, 4):
                name = "finger%d-%d.%s" % (f, j, side)
                pb = pbs[name]
                axis = (pb.tail - pb.head).normalized().cross(n)
                best = None
                for sgn in (1, -1):
                    r = Matrix.Rotation(math.radians(sgn * deg), 3, axis)
                    t = r @ (pb.tail - pb.head)
                    if best is None or t.dot(n) > best[1]:
                        best = (sgn, t.dot(n))
                rotate_world(name, axis, best[0] * deg)

    rest_palm = {"L": Vector((-1.0, 0, 0)), "R": Vector((1.0, 0, 0))}
    # Weight back on her heels, chin level, square to the ship.
    rotate_world("spine02", (1, 0, 0), -3)
    rotate_world("head", (1, 0, 0), 3)
    # Feet a little apart, weight on her left leg.
    rotate_world("upperleg01.R", (0, 1, 0), -3)
    rotate_world("upperleg01.L", (0, 1, 0), 2)
    rotate_world("upperleg01.R", (1, 0, 0), -3)
    rotate_world("lowerleg01.R", (1, 0, 0), 6)

    sh_z = pbs["upperarm01.L"].head.z
    chest_y = min(v.y for v in bverts if abs(v.z - (sh_z - 0.2)) < 0.02 and abs(v.x) < 0.1)
    if pose == "folded":
        # Right forearm on top across her chest, its hand on her left upper arm; left forearm
        # under it, its hand tucked behind her right arm. Elbows held out at her sides.
        fz = sh_z - 0.27
        rw = (0.10, chest_y - 0.075, fz + 0.03)
        lw = (-0.09, chest_y - 0.045, fz - 0.035)
        two_bone("R", rw, (-0.8, -0.2, sh_z - 0.35))
        two_bone("L", lw, (0.8, -0.2, sh_z - 0.35))
        orient("wrist.R", (1.0, 0.15, -0.35), (0.0, 1.0, 0.1), rest_palm["R"])
        orient("wrist.L", (-1.0, 0.35, -0.1), (0.0, 1.0, 0.2), rest_palm["L"])
        curl("R", 35, 15, (0.0, 1.0, 0.1))
        curl("L", 55, 20, (0.0, 1.0, 0.2))
    else:
        # The slate held out in her right hand at chest height, screen turned to whoever she's
        # talking to (the viewer, -Y); left hand loose at her side.
        rw = (-0.08, chest_y - 0.17, sh_z - 0.36)
        two_bone("R", rw, (-0.5, 0.4, sh_z - 0.7))
        orient("wrist.R", (0.15, -0.6, 0.15), (-0.2, 0.0, 1.0), rest_palm["R"])
        curl("R", 25, 10, (-0.2, 0.0, 1.0))
        # Left arm hangs loose at her side (MakeHuman's rest is an A-pose).
        two_bone("L", (0.21, 0.02, sh_z - 0.56), (0.35, 0.6, sh_z - 0.3))
        orient("wrist.L", (0.02, 0.0, -1.0), (-1.0, 0.1, 0.0), rest_palm["L"])
        curl("L", 20, 8, (-1.0, 0.1, 0.0))
        view.update()
        bm = bmesh.new()
        bmesh.ops.create_cube(bm, size=1.0)
        bmesh.ops.scale(bm, vec=(0.20, 0.014, 0.15), verts=bm.verts)
        slate = new_obj("slate", bm, "slate")
        bm = bmesh.new()
        bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=0.5)
        bmesh.ops.scale(bm, vec=(0.17, 0.12, 1.0), verts=bm.verts)
        bmesh.ops.rotate(bm, verts=bm.verts, cent=(0, 0, 0), matrix=Matrix.Rotation(math.radians(90), 3, "X"))
        bmesh.ops.translate(bm, verts=bm.verts, vec=(0, -0.0075, 0))
        screen = new_obj("slate screen", bm, "screen")
        # In her grip: centred a little past the wrist along the hand, screen tipped up toward the
        # viewer (-Y) at about 35 degrees.
        wr = pbs["wrist.R"]
        hand = (wr.tail - wr.head).normalized()
        centre = wr.tail + hand * 0.03 + Vector((0.06, -0.02, 0.04))
        world = Matrix.Translation(centre) @ Matrix.Rotation(math.radians(-35), 4, "X") @ Matrix.Rotation(math.radians(-10), 4, "Z")
        parent = rig.matrix_world @ wr.matrix @ Matrix.Translation((0, wr.bone.length, 0))
        for ob in (slate, screen):
            ob.parent = rig
            ob.parent_type = "BONE"
            ob.parent_bone = "wrist.R"
            ob.matrix_parent_inverse = parent.inverted()
            ob.matrix_basis = world
        view.update()

    # Re-ground.
    dg = bpy.context.evaluated_depsgraph_get()
    lowest = min(min((o.evaluated_get(dg).matrix_world @ v.co).z for v in o.evaluated_get(dg).data.vertices)
                 for o in (body, bpy.data.objects["Nessa shoes03"]))
    rig.location.z -= lowest

    # ---- the appendable package
    col = bpy.data.collections.new(COLLECTION)
    scene.collection.children.link(col)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = "ARROWS", 0.3
    col.objects.link(root)
    rig.parent = root
    rig.rotation_euler = (0, 0, math.radians(90))
    for ob in [rig, *rig.children_recursive]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        col.objects.link(ob)
    rig.hide_render = True
    root["height_m"] = round(top_z - min(v.z for v in bverts), 3)
    root["pose"], root["mask"] = pose, mask
    root["source"] = "art-source/company-prologue/nessa-figure/build_nessa.py"
    root["frame"] = "metres; z up from the soles; she faces +X"

    out.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(out), compress=True)
    print("NESSA height %.3f m pose %s mask %s -> %s" % (root["height_m"], pose, mask, out), flush=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        launch(argv)
