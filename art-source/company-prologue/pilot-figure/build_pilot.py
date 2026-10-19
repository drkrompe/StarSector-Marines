"""Second Wind's pilot as a 3D figure: a MakeHuman body built by MPFB, rigged and seated, in metres.

    python build_pilot.py [--out PATH]          # Blender 5.2, headless
    blender --background --python build_pilot.py -- [--out PATH]

The pilot has no name and no design anywhere else (owner, 2026-10-19: "him"). He is an
ordinary crewman of the same escaped Claim company as Castle: a man in his mid thirties in a
faded olive flight coverall that has been patched and re-patched, a webbing harness over it,
worn boots, a cropped haircut gone shaggy at the nape, and a battered over-ear headset with a
boom microphone. Nothing about him should read as a hero; he is the man who flies the ship.

C2 films him from Second Wind's bridge (`patrol-abeam/c2-layout.md`): he sits at the helm on
the raised flight dais, hands on the low forward console, eyes out of the slit. The cameras
stand to port of him and behind, so the detail is spent on his back, his left side and the
back of his head: the harness yoke, the patches on the left shoulder and the back, the
headset's band and cups, the collar and the hair at the nape. His face is MakeHuman's own.

What this builds, in the file's own frame (metres, z up from the dais plating under the helm
seat, he faces +X: the convention of `sw_bridge_interior.figures`):

- collection 'Pilot figure', holding everything below; append it, never link pieces of it;
- 'Pilot figure root', an empty on the dais at the helm seat's pedestal axis: place and turn
  this, nothing else (the seat's pan is SEAT_TOP above it);
- 'Pilot rig', MPFB's default skeleton with its weights, posed seated;
- the body, eyes, eyebrows, eyelashes, hair and clothes MPFB fits to it, from MakeHuman's own
  CC0 system-asset pack (listed in README.md);
- what that pack has no piece for, modelled here on the posed body and parented to bones: the
  coverall's collar, the harness, the patches, the belt and the headset.

The pose is solved, not keyed, as Castle's is (`castle-figure/build_castle.py`): hips placed on
the seat, each leg a two-bone solve from the hip to an ankle on the dais, each arm a two-bone
solve from the shoulder to a wrist on the console's sloped face, the hands turned flat onto it.
Skin, eyes, brows and lashes use MakeHuman's textures through castle_face's material helpers;
clothes and the modelled parts are flat colours (diffuse_color for Workbench). Close shots are
repainted with ImageGen (`geometry-first-imagegen.md`), so the figure carries silhouette,
proportions, pose and light, not finish.

The .blend is a build output (gitignored); rebuild it, or copy it from Drive
(starsector/builds/pilot/). Requires the MPFB extension (2.0.17 tested) and the MakeHuman
system-asset pack installed in the Blender this runs under.
"""
import math
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "pilot-figure.blend"
CASTLE = HERE.parent / "castle-figure"
COLLECTION = "Pilot figure"
ROOT = "Pilot figure root"

# ---------------------------------------------------------------- who he is (MakeHuman macros)
# Age 0.58 is about 35 (0.5 is 25, 1.0 is 90). A wiry, ordinary build; a mixed Claim face.
PHENOTYPE = dict(gender=1.0, age=0.58, muscle=0.6, weight=0.45, proportions=0.5, height=0.5,
                 race=dict(caucasian=0.5, asian=0.3, african=0.2))
ASSETS = dict(eyes="high-poly.mhclo", eyebrows="eyebrow009.mhclo", eyelashes="eyelashes01.mhclo",
              hair="short01.mhclo")
# MakeHuman's young male face is soft; a squarer jaw, a heavier brow, a thicker neck, a lean
# face with a few more years in it.
FACE_TARGETS = [("head-square", 0.3), ("head-age-incr", 0.3), ("head-fat-decr", 0.2),
                ("chin-width-incr", 0.35), ("chin-bones-incr", 0.4), ("chin-prominent-incr", 0.2),
                ("eyebrows-trans-forward", 0.35), ("eyebrows-angle-down", 0.2),
                ("l-cheek-volume-decr", 0.3), ("r-cheek-volume-decr", 0.3),
                ("nose-scale-horiz-incr", 0.15), ("mouth-scale-horiz-incr", 0.1),
                ("neck-scale-horiz-incr", 0.3), ("neck-scale-depth-incr", 0.2)]
CLOTHES = ["male_casualsuit02.mhclo", "shoes03.mhclo"]      # long sleeves and trousers; boots
SKIN = "middleage_caucasian_male"
SKIN_TINT = (0.93, 0.86, 0.74)            # warmer and a little weathered, like Castle's

COLOURS = {          # Workbench and Principled base colours
    "skin": (0.50, 0.36, 0.27),
    "hair": (0.05, 0.035, 0.025),
    "coverall": (0.14, 0.15, 0.115),       # faded olive drab
    "boots": (0.05, 0.042, 0.036),
    "eye": (0.25, 0.2, 0.15),
    "webbing": (0.045, 0.045, 0.04),
    "buckle": (0.32, 0.31, 0.29),
    "patch rust": (0.24, 0.11, 0.05),      # cut from something else
    "patch grey": (0.19, 0.19, 0.175),
    "patch dark": (0.075, 0.08, 0.065),
    "headset": (0.045, 0.045, 0.05),
    "headset pad": (0.02, 0.02, 0.022),
    "tape": (0.32, 0.27, 0.12),            # the band is taped where it cracked
}

# ---------------------------------------------------------------- the helm (Second Wind units)
U = 0.65                                   # metres per Second Wind unit
# sw_bridge_interior: dais plating top STEP + 0.06; the seat's pan cushion tops out at
# FLIGHT_SEAT_PAN + 0.09 above STEP; the forward console's casing reaches CONSOLE_TOP with its
# operator face 0.88 units ahead of the seat's axis, and the sloped face's near edge stands
# about 0.12 units above the casing.
SEAT_TOP = (0.5 + 0.09 - 0.06) * U        # 0.34 m: a low flight seat
BACK_FWD = -0.23                           # the back cushion's face at the pan, m ahead of the axis
CONSOLE_FWD = (17.18 - 16.3) * U           # 0.57 m: the casing's operator face
CONSOLE_TOP = (18.25 - 17.61) * U          # 0.42 m above the plating
FACE_EDGE_Z = (18.37 - 17.61) * U          # 0.49 m: the sloped face's near edge
FACE_TILT = 16.0                           # degrees, high edge away from him

# ---------------------------------------------------------------- the seated pose
# Package frame here (he faces +fwd, left is +side), converted to MPFB's (faces -Y, left +X).
HIP = (-0.15, 0.43)                        # (fwd, z) of the hip joints: buttocks on the pan
ANKLE = (0.44, 0.085)                      # (fwd, z) of each ankle: feet flat, under the console lip
ANKLE_SIDE = 0.15                          # knees a little apart
KNEE_POLE = (1.5, 0.5)                     # (fwd, z): knees forward and up
LEAN_DEG = 5.0                             # off the seat back, toward the console
HEAD_LIFT_DEG = -16.0                      # chin up: eyes out of the slit, not on the console
HEAD_TURN_DEG = 4.0                        # a touch to port, along the Legion's bearing
WRIST = (0.56, 0.2, 0.575)                 # (fwd, side, z) of the left wrist; the right mirrors
WRIST_RIGHT = (0.6, -0.22, 0.59)           # the right hand a little further up the face
ELBOW_POLE = (0.0, 0.7, -0.4)              # (fwd, side, z) from the shoulder: elbows down and out


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    # Not --factory-startup: that would leave the MPFB extension disabled.
    cmd = [BLENDER, "--background", "--python", str(Path(__file__).resolve()), "--", *argv]
    sys.exit(subprocess.call(cmd))


def blender_main(argv):
    import bmesh
    import bpy
    from mathutils import Matrix, Vector
    from mathutils.bvhtree import BVHTree

    out = Path(argv[argv.index("--out") + 1]) if "--out" in argv else BLEND
    sys.path.insert(0, str(CASTLE))
    import castle_face      # its material helpers: MakeHuman textures as Principled materials

    try:
        import addon_utils
        addon_utils.enable("bl_ext.blender_org.mpfb", default_set=True)
        from bl_ext.blender_org.mpfb.services.humanservice import HumanService
        from bl_ext.blender_org.mpfb.services.assetservice import AssetService
    except Exception as exc:     # noqa: BLE001
        sys.exit("MPFB is not installed in this Blender (Get Extensions > MPFB): %s" % exc)
    for part, name in [*ASSETS.items(), *(("clothes", c) for c in CLOTHES)]:
        if AssetService.find_asset_absolute_path(name, asset_subdir=part) is None:
            sys.exit("MakeHuman asset %s/%s is missing: install the MakeHuman system asset "
                     "pack in MPFB (Apply assets > Load pack)." % (part, name))

    for ob in list(bpy.data.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    for col in list(bpy.data.collections):
        bpy.data.collections.remove(col)
    scene = bpy.context.scene
    view = bpy.context.view_layer

    # ---- the body
    info = HumanService._create_default_human_info_dict()
    info["phenotype"] = PHENOTYPE
    info["name"] = "Pilot"
    info["rig"] = "default"
    info.update(ASSETS)
    info["clothes"] = list(CLOTHES)
    info["skin_material_type"] = "NONE"
    info["targets"] = [{"target": t, "value": v} for t, v in FACE_TARGETS]
    settings = HumanService.get_default_deserialization_settings()
    settings.update(scale=0.1, subdiv_levels=1, override_skin_model="NONE")   # dm to metres
    body = HumanService.deserialize_from_dict(info, settings)
    rig = body.parent
    rig.name = rig.data.name = "Pilot rig"
    body.name = "Pilot body"
    parts = [o for o in rig.children if o.type == "MESH"]
    for o in parts:
        if o is not body:
            o.name = "Pilot " + o.name.split(".", 1)[-1]

    # ---- materials
    def flat(name):
        m = bpy.data.materials.get("Pilot " + name) or bpy.data.materials.new("Pilot " + name)
        rgb = COLOURS[name]
        m.diffuse_color = (*rgb, 1.0)
        m.roughness = 0.75
        m.use_nodes = True
        bsdf = m.node_tree.nodes.get("Principled BSDF")
        bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
        bsdf.inputs["Roughness"].default_value = 0.75
        return m

    def paint(ob, name):
        ob.data.materials.clear()
        ob.data.materials.append(flat(name))
        ob.color = ob.data.materials[0].diffuse_color

    def asset_dir(subdir, name):
        import os
        return Path(os.path.dirname(AssetService.find_asset_absolute_path(name, asset_subdir=subdir)))

    skin_dir = asset_dir("skins", SKIN + ".mhmat")
    skin_png = next(p for p in skin_dir.iterdir() if p.name.endswith("_diffuse.png"))
    castle_face._assign(body, castle_face._textured("pilot skin", skin_png, COLOURS["skin"], rough=0.55,
                                                    sss=0.12, tint=SKIN_TINT))
    body.data.materials[0].name = "Pilot skin"
    for o in parts:
        key = o.name.split(" ", 1)[-1]
        if key.startswith("high-poly"):
            d = asset_dir("eyes", "brown.mhmat")
            castle_face._assign(o, castle_face._textured("pilot eye", d / "brown_eye.png", COLOURS["eye"],
                                                         alpha=True, rough=0.15))
        elif key.startswith(("eyebrow", "eyelashes")):
            sub = "eyebrows" if key.startswith("eyebrow") else "eyelashes"
            png = asset_dir(sub, key + ".mhclo") / (key + ".png")
            castle_face._assign(o, castle_face._textured("pilot " + sub, png, COLOURS["hair"], alpha=True,
                                                         rough=0.7, tint=(0.25, 0.2, 0.17)))
        elif key.startswith("short01"):
            paint(o, "hair")
        elif key.startswith("shoes"):
            paint(o, "boots")
        elif key.startswith("male_casualsuit02"):
            o.name = "Pilot flight coverall"
            paint(o, "coverall")

    # ---- the seated pose (MPFB frame: forward is -Y, his left is +X)
    def mp(fwd, side, z):
        return Vector((side, -fwd, z))

    bones = rig.data.bones
    pbs = rig.pose.bones
    for need in ("upperleg01.L", "upperleg02.L", "lowerleg01.L", "lowerleg02.L", "foot.L",
                 "upperarm01.L", "upperarm02.L", "lowerarm01.L", "lowerarm02.L", "wrist.L"):
        if need not in bones:
            sys.exit("MPFB default rig has no bone %s" % need)

    def rotate_world(name, axis, deg):
        pb = pbs[name]
        h = pb.head.copy()
        r = Matrix.Rotation(math.radians(deg), 4, Vector(axis))
        pb.matrix = Matrix.Translation(h) @ r @ Matrix.Translation(-h) @ pb.matrix
        view.update()

    def aim(name, direction):
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = rest.col[1].rotation_difference(Vector(direction).normalized())
        m = (q.to_matrix() @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def frame(y, n):
        y = Vector(y).normalized()
        x = Vector(n).cross(y).normalized()
        return Matrix((x, y, x.cross(y))).transposed()

    def orient(name, fingers, palm, rest_palm):
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = frame(fingers, palm) @ frame(rest.col[1], rest_palm).transposed()
        m = (q @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def two_bone(upper, lower, target, pole):
        """Solve a chain of two two-bone segments from the first bone's head to `target`."""
        a = sum(bones[b].length for b in upper)
        b = sum(bones[x].length for x in lower)
        s = pbs[upper[0]].head.copy()
        d = Vector(target) - s
        L = min(d.length, (a + b) * 0.999)
        dn = d.normalized()
        along = (a * a - b * b + L * L) / (2 * L)
        off = math.sqrt(max(a * a - along * along, 0.0))
        p = Vector(pole) - s
        perp = (p - dn * p.dot(dn)).normalized()
        joint = s + dn * along + perp * off
        for name in upper:
            aim(name, joint - s)
        end = joint + (Vector(target) - joint).normalized() * b
        for name in lower:
            aim(name, end - joint)
        return end

    def curl(side, finger_deg, thumb_deg, palm):
        n = Vector(palm).normalized()
        for f in range(1, 6):
            deg = thumb_deg if f == 1 else finger_deg
            for j in range(1, 4):
                pb = pbs["finger%d-%d.%s" % (f, j, side)]
                axis = (pb.tail - pb.head).normalized().cross(n)
                best = max((1, -1), key=lambda sgn: (Matrix.Rotation(math.radians(sgn * deg), 3, axis)
                                                     @ (pb.tail - pb.head)).dot(n))
                rotate_world(pb.name, axis, best * deg)

    # Where the rig must stand so his hip joints land on the seat: armature space is the rig's
    # own, and the rig object is moved by `shift` at the end, so every target is taken back by it.
    hips = (bones["upperleg01.L"].head_local + bones["upperleg01.R"].head_local) / 2
    shift = mp(HIP[0], 0, HIP[1]) - hips
    shift.x = 0.0

    def local(p):
        return Vector(p) - shift

    # Legs: thighs forward onto the seat, shins down to the dais.
    for side, sgn in (("L", 1), ("R", -1)):
        two_bone(["upperleg01." + side, "upperleg02." + side], ["lowerleg01." + side, "lowerleg02." + side],
                 local(mp(ANKLE[0], sgn * ANKLE_SIDE, ANKLE[1])), local(mp(KNEE_POLE[0], sgn * 0.2, KNEE_POLE[1])))
        aim("foot." + side, mp(1.0, sgn * 0.08, -0.25))         # sole flat, toes a little out
    # The spine comes forward off the seat back; the head stays up, eyes on the slit.
    for name, share in (("spine05", 0.25), ("spine04", 0.3), ("spine03", 0.25), ("spine02", 0.2)):
        if name in pbs:
            rotate_world(name, (1, 0, 0), LEAN_DEG * share)
    rotate_world("neck01", (1, 0, 0), HEAD_LIFT_DEG * 0.5)
    rotate_world("head", (1, 0, 0), HEAD_LIFT_DEG * 0.5)
    rotate_world("head", (0, 0, 1), HEAD_TURN_DEG)
    # Arms: forward and down to the console's sloped face, hands flat on it.
    t = math.radians(FACE_TILT)
    fingers = mp(math.cos(t), 0, math.sin(t))
    palm = mp(-math.sin(t), 0, -math.cos(t))
    for side, (f, s, z), fdeg in (("L", WRIST, 14), ("R", WRIST_RIGHT, 26)):
        sh = pbs["upperarm01." + side].head
        sgn = 1 if side == "L" else -1
        two_bone(["upperarm01." + side, "upperarm02." + side], ["lowerarm01." + side, "lowerarm02." + side],
                 local(mp(f, sgn * abs(s), z)), sh + mp(ELBOW_POLE[0], sgn * ELBOW_POLE[1], ELBOW_POLE[2]))
        rest_palm = Vector((-1.0, 0, 0)) if side == "L" else Vector((1.0, 0, 0))
        orient("wrist." + side, fingers + mp(0, -sgn * 0.15, 0), palm, rest_palm)
        curl(side, fdeg, 12, palm)
    rig.location = shift
    view.update()

    # ---- the posed surface, for fitting what the pack lacks
    dg = bpy.context.evaluated_depsgraph_get()

    def posed(ob):
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        vs = [ev.matrix_world @ v.co for v in me.vertices]
        polys = [tuple(p.vertices) for p in me.polygons]
        ev.to_mesh_clear()
        return vs, polys

    suit = bpy.data.objects["Pilot flight coverall"]
    sv, sp = posed(suit)
    bv, bp = posed(body)
    suit_bvh = BVHTree.FromPolygons(sv, sp)
    # The coverall's torso alone, for the harness and belt: the arms reach forward across the
    # chest and the thighs run forward from the waist, and a ray from in front would land on them.
    torso_groups = {g.index for g in suit.vertex_groups
                    if g.name.startswith(("spine", "clavicle", "neck", "root", "pelvis", "breast", "shoulder"))}
    dominant = [max(v.groups, key=lambda e: e.weight).group if v.groups else -1 for v in suit.data.vertices]
    torso_bvh = BVHTree.FromPolygons(sv, [p for p in sp if all(dominant[i] in torso_groups for i in p)])
    body_bvh = BVHTree.FromPolygons(bv, bp)

    def world_bone(name):
        pb = pbs[name]
        return rig.matrix_world @ pb.matrix

    def bone_parent(ob, name):
        pb = pbs[name]
        ob.parent = rig
        ob.parent_type = "BONE"
        ob.parent_bone = name
        tail = rig.matrix_world @ pb.matrix @ Matrix.Translation((0, pb.bone.length, 0))
        ob.matrix_parent_inverse = tail.inverted()

    def hit(bvh, origin, toward):
        d = (Vector(toward) - Vector(origin)).normalized()
        loc, nrm, _, _ = bvh.ray_cast(Vector(origin), d)
        if loc is None:
            return None, None
        return loc, (nrm if nrm.dot(d) < 0 else -nrm)

    def obj(name, mesh, mat):
        ob = bpy.data.objects.new("Pilot " + name, mesh)
        scene.collection.objects.link(ob)
        paint(ob, mat)
        return ob

    def strap(name, pts, normals, width, thick, mat, bone):
        """A flat band through surface points, `width` across and `thick` off the surface."""
        bm = bmesh.new()
        rows = []
        for k, (p, n) in enumerate(zip(pts, normals)):
            nxt = pts[min(k + 1, len(pts) - 1)] - pts[max(k - 1, 0)]
            across = nxt.cross(n).normalized() * (width / 2)
            base = p + n * 0.002
            rows.append([bm.verts.new(base - across), bm.verts.new(base + across),
                         bm.verts.new(base + across + n * thick), bm.verts.new(base - across + n * thick)])
        for a, b in zip(rows, rows[1:]):
            for i in range(4):
                bm.faces.new((a[i], a[(i + 1) % 4], b[(i + 1) % 4], b[i]))
        bm.faces.new(rows[0][::-1])
        bm.faces.new(rows[-1])
        me = bpy.data.meshes.new("Pilot " + name)
        bm.to_mesh(me)
        bm.free()
        ob = obj(name, me, mat)
        bone_parent(ob, bone)
        return ob

    def loop(centre, axis_a, axis_b, angles, bvh, reach=0.5):
        """Surface points round `centre` in the plane of axes a/b, by ray from outside inward."""
        pts, nrms = [], []
        for ang in angles:
            r = math.radians(ang)
            d = axis_a * math.cos(r) + axis_b * math.sin(r)
            loc, n = hit(bvh, centre + d * reach, centre)
            if loc is not None:
                pts.append(loc)
                nrms.append(n)
        return pts, nrms

    def plate(name, at, toward_in, size, mat, bone, up=Vector((0, 0, 1)), lift=0.0025):
        """A patch sewn on where a ray from `at` toward `toward_in` lands: the coverall's own
        faces within a `size` rectangle (in the surface's tangent plane, `up` its long axis),
        lifted off it, so it follows the cloth instead of floating as a card."""
        loc, n = hit(suit_bvh, at, toward_in)
        if loc is None:
            return None
        ax = (up - n * up.dot(n)).normalized()
        bx = n.cross(ax)

        def inside(c, margin=0.0):
            d = c - loc
            return (abs(d.dot(bx)) < size[0] / 2 + margin and abs(d.dot(ax)) < size[1] / 2 + margin
                    and abs(d.dot(n)) < 0.025)

        # The coverall is coarse (faces a few centimetres across): take the faces round the spot,
        # subdivide them finely, keep what lies inside the rectangle and lay it back on the cloth.
        keep = [p for p in sp if any(inside(sv[i], 0.03) for i in p)]
        used = sorted({i for p in keep for i in p})
        remap = {i: k for k, i in enumerate(used)}
        bm = bmesh.new()
        vs = [bm.verts.new(sv[i]) for i in used]
        for p in keep:
            bm.faces.new([vs[remap[i]] for i in p])
        bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=6, use_grid_fill=True)
        bmesh.ops.delete(bm, geom=[v for v in bm.verts if not inside(v.co)], context="VERTS")
        for v in bm.verts:
            near, nrm, _, _ = suit_bvh.find_nearest(v.co)
            v.co = near + (nrm if nrm.dot(n) > 0 else -nrm) * lift
        bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.002)
        me = bpy.data.meshes.new("Pilot " + name)
        bm.to_mesh(me)
        bm.free()
        ob = obj(name, me, mat)
        bone_parent(ob, bone)
        return ob

    chest = world_bone("spine02").translation.lerp(world_bone("neck01").translation, 0.55)
    spine_up = (world_bone("neck01").translation - world_bone("spine03").translation).normalized()
    fwd = mp(1, 0, 0)
    back_dir = (-fwd - spine_up * spine_up.dot(-fwd)).normalized()

    # Harness: two shoulder straps from the belt, over the shoulders and down the back into a
    # yoke, a chest strap across, and a belt at the waist.
    for side, sgn in (("L", 1), ("R", -1)):
        lat = Vector((sgn * 0.1, 0, 0))
        pts, nrm = loop(chest + lat, -back_dir, spine_up, range(-75, 250, 6), torso_bvh)
        strap("harness strap " + side, pts, nrm, 0.045, 0.004, "webbing", "spine03")
    pts, nrm = loop(chest - spine_up * 0.1, -back_dir, Vector((1, 0, 0)), range(-55, 56, 5), torso_bvh)
    strap("harness chest strap", pts, nrm, 0.03, 0.004, "webbing", "spine03")
    pts, nrm = loop(chest - spine_up * 0.08, back_dir, Vector((1, 0, 0)), range(-65, 66, 5), torso_bvh)
    strap("harness back yoke", pts, nrm, 0.04, 0.004, "webbing", "spine03")
    waist = world_bone("spine01").translation + spine_up * 0.03
    pts, nrm = loop(waist, fwd, Vector((1, 0, 0)), range(0, 361, 8), torso_bvh)
    strap("belt", pts, nrm, 0.04, 0.005, "webbing", "spine01")
    plate("harness buckle", chest - spine_up * 0.1 + fwd * 0.4, chest - spine_up * 0.1, (0.05, 0.04),
          "buckle", "spine03", lift=0.008)

    # Patches: a rust square on the left shoulder and a grey one on the left thigh (the side the
    # cameras see; the seat hides his back), darker ones on the left elbow and the right knee:
    # the coverall has outlived whoever it was cut for.
    sh_l = world_bone("upperarm01.L")
    el_l = world_bone("lowerarm01.L").translation
    arm_mid = sh_l.translation.lerp(el_l, 0.3)
    plate("patch shoulder", arm_mid + Vector((0.3, 0.05, 0.05)), arm_mid, (0.07, 0.06), "patch rust",
          "upperarm01.L", up=(el_l - sh_l.translation).normalized())
    knee_l = world_bone("lowerleg01.L").translation
    hip_l = world_bone("upperleg01.L").translation
    thigh = hip_l.lerp(knee_l, 0.55)
    plate("patch thigh", thigh + Vector((0.3, 0, 0.03)), thigh + Vector((0, 0, 0.03)), (0.1, 0.12), "patch grey",
          "upperleg02.L", up=(knee_l - hip_l).normalized())
    plate("patch elbow", el_l + Vector((0.2, 0.15, 0)), el_l, (0.06, 0.07), "patch dark", "lowerarm01.L",
          up=(world_bone("wrist.L").translation - el_l).normalized())
    knee_r = world_bone("lowerleg01.R").translation
    plate("patch knee", knee_r + fwd * 0.3 + Vector((0, 0, 0.1)), knee_r, (0.08, 0.08), "patch dark",
          "lowerleg01.R", up=fwd)

    # Collar: the coverall's stand collar round the base of the neck.
    neck = world_bone("neck01")
    bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=0.072, radius2=0.064, depth=0.045,
                                    end_fill_type="NOTHING", location=neck.translation + spine_up * 0.015)
    collar = bpy.context.object
    collar.name = "Pilot coverall collar"
    paint(collar, "coverall")
    collar.rotation_euler = spine_up.to_track_quat("Z", "Y").to_euler()
    collar.modifiers.new("Thickness", "SOLIDIFY").thickness = 0.006
    bpy.ops.object.shade_smooth()
    bone_parent(collar, "neck01")

    # Headset: a cup over each ear, the band over the crown (taped where it cracked), the boom
    # microphone from his left cup round to the corner of his mouth.
    eye_c = (world_bone("eye.L").translation + world_bone("eye.R").translation) / 2
    cups = {}
    for side, sgn in (("L", 1), ("R", -1)):
        # A ray in from beside the head, a little below and behind the eyes: the ear.
        back = -fwd * 0.07 - Vector((0, 0, 0.025))
        loc, n = hit(body_bvh, eye_c + Vector((sgn * 0.3, 0, 0)) + back, eye_c + back)
        cups[side] = (loc, n)
        bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=0.036, depth=0.028,
                                            location=loc + n * 0.018)
        cup = bpy.context.object
        cup.name = "Pilot headset cup " + side
        cup.rotation_euler = n.to_track_quat("Z", "Y").to_euler()
        bev = cup.modifiers.new("Bevel", "BEVEL")
        bev.width, bev.segments = 0.006, 2
        paint(cup, "headset")
        bpy.ops.object.shade_smooth()
        bone_parent(cup, "head")
        bpy.ops.mesh.primitive_torus_add(major_radius=0.036, minor_radius=0.008, major_segments=24,
                                         minor_segments=8, location=loc + n * 0.006)
        pad = bpy.context.object
        pad.name = "Pilot headset pad " + side
        pad.rotation_euler = n.to_track_quat("Z", "Y").to_euler()
        paint(pad, "headset pad")
        bone_parent(pad, "head")
    (la, ln), (ra, rn) = cups["L"], cups["R"]
    mid = (la + ra) / 2
    up_h = Vector((0, 0, 1))
    band_pts, band_n = [], []
    for k in range(25):
        s = k / 24
        ang = math.pi * s
        d = (la - mid).normalized() * math.cos(ang) + up_h * math.sin(ang)
        d = (d + mp(-0.15, 0, 0) * math.sin(ang)).normalized()     # leaning back over the crown
        loc, n = hit(body_bvh, mid + d * 0.4, mid)
        if loc is None:
            continue
        band_pts.append(loc + n * 0.022)
        band_n.append(n)
    band_pts[0] = la + ln * 0.03 + up_h * 0.03
    band_pts[-1] = ra + rn * 0.03 + up_h * 0.03
    strap("headset band", band_pts, band_n, 0.026, 0.006, "headset", "head")
    taped = len(band_pts) // 3
    strap("headset band tape", band_pts[taped - 2:taped + 2], band_n[taped - 2:taped + 2], 0.03, 0.009,
          "tape", "head")
    mouth = eye_c + Vector((0, 0, -0.075)) + fwd * 0.07 + Vector((0.025, 0, 0))
    boom_a = la + ln * 0.03 + fwd * 0.012 - Vector((0, 0, 0.012))
    ctrl = [boom_a, boom_a.lerp(mouth, 0.5) + ln * 0.03 + fwd * 0.01, mouth + Vector((0.01, 0, 0))]
    curve = bpy.data.curves.new("Pilot boom microphone", "CURVE")
    curve.dimensions = "3D"
    curve.bevel_depth, curve.bevel_resolution = 0.0032, 2
    spl = curve.splines.new("NURBS")
    spl.points.add(len(ctrl) - 1)
    for p, c in zip(spl.points, ctrl):
        p.co = (*c, 1.0)
    spl.use_endpoint_u, spl.order_u = True, 3
    boom = obj("boom microphone", curve, "headset")
    bone_parent(boom, "head")
    bpy.ops.mesh.primitive_uv_sphere_add(segments=12, ring_count=8, radius=0.008, location=ctrl[-1])
    capsule = bpy.context.object
    capsule.name = "Pilot boom microphone capsule"
    paint(capsule, "headset pad")
    bone_parent(capsule, "head")

    # ---- the appendable package: root on the dais under the seat's axis, he faces +X
    col = bpy.data.collections.new(COLLECTION)
    scene.collection.children.link(col)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = "ARROWS", 0.3
    col.objects.link(root)
    pivot = Matrix.Rotation(math.radians(90), 4, "Z")          # MPFB's -Y front onto +X
    rig.matrix_world = pivot @ rig.matrix_world
    rig.parent = root
    for ob in [rig, *rig.children_recursive]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        col.objects.link(ob)
    rig.hide_render = True
    root["seat_top_m"] = round(SEAT_TOP, 4)
    root["source"] = "art-source/company-prologue/pilot-figure/build_pilot.py"
    root["frame"] = "metres; z up from the dais plating at the helm seat's axis; he faces +X"

    out.parent.mkdir(parents=True, exist_ok=True)
    castle_face.pack()
    bpy.ops.wm.save_as_mainfile(filepath=str(out), compress=True)
    print("PILOT seat %.3f m -> %s" % (SEAT_TOP, out), flush=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        launch(argv)
