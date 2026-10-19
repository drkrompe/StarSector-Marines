"""Castle as a 3D figure: a MakeHuman body built by MPFB, rigged and posed, in metres.

    python build_castle.py                     # launches Blender 5.2 (blender_path) headless
    blender --background --python build_castle.py -- [--out castle-figure.blend]

Castle is the company's shipboard operations officer (`art-source/portraits/castle/prompt.txt`):
a woman in her early fifties, warm pale olive skin, dark brown hair going silver at the
temples in a short practical bun, a worn charcoal-olive high-collared duty jacket over a faded
undershirt, dark work trousers and boots, a small earpiece with a short boom microphone. C2
films her from Second Wind's bridge, standing at the port comms bank on the hail; close shots
are repainted over these frames with ImageGen (`geometry-first-imagegen.md`), so the figure
carries the silhouette, proportions, pose and lighting, not the finish.

What this builds, in the file's own frame (metres, z up from the soles, she faces +X: the
mannequin convention in `sw_bridge_interior.figures`):

- collection 'Castle figure', holding everything below; append it, never link pieces of it;
- 'Castle figure root', an empty at her feet: place and turn this, nothing else;
- 'Castle rig', MPFB's default skeleton (MakeHuman 'default' rig with its weights), posed;
- the body, eyes, eyebrows, hair and clothes MPFB fits to it, from MakeHuman's own CC0
  system-asset pack (`NOTES` in the review run lists them), each skinned to the rig;
- what that pack has no piece for, modelled here and parented to bones: the bun, the
  jacket's stand collar, the earpiece and its boom.

The pose is the C2 one: standing at the console, left hand resting on its top edge, right
hand up at the earpiece as she speaks, a little weight on the right leg, the head bowed to the
screens. It is solved here rather than keyed by hand, so it can be re-aimed: each arm is a
two-bone solve from the shoulder to a wrist target with the elbow pushed toward a pole, and
the hand is turned to a named finger direction and palm normal. Targets are given relative
to measured landmarks of the built body (the ear, the console's top in front of her), so a
different phenotype keeps her hand on her ear.

Materials are flat colours (diffuse_color for Workbench, the same base colour in a Principled
BSDF): MPFB's skin and clothes materials reference textures inside one machine's Blender
profile, and the finish comes from the ImageGen repaint, not from these. The .blend is a
build output (gitignored); rebuild it, or copy it from Drive (starsector/builds/castle/).

Requires the MPFB extension (2.0.17 tested) and the MakeHuman system-asset pack installed in
the Blender this runs under; it fails with a message naming what is missing.
"""
import math
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "castle-figure.blend"
COLLECTION = "Castle figure"
ROOT = "Castle figure root"

# ---------------------------------------------------------------- who she is (MakeHuman macros)
# MakeHuman's age slider is piecewise: 0.5 is 25 years, 1.0 is 90. Early fifties is about 0.71.
PHENOTYPE = dict(gender=0.0, age=0.71, muscle=0.55, weight=0.52, proportions=0.6, height=0.62,
                 cupsize=0.45, firmness=0.45,
                 race=dict(caucasian=0.72, asian=0.12, african=0.16))      # warm pale olive
ASSETS = dict(eyes="high-poly.mhclo", eyebrows="eyebrow006.mhclo", eyelashes="eyelashes01.mhclo",
              hair="short04.mhclo")                    # swept back; the bun is added below
CLOTHES = ["male_casualsuit02.mhclo", "shoes02.mhclo"]  # long-sleeved top over trousers; boots

COLOURS = {          # linear-ish RGB for Workbench; read against the portrait's palette
    "skin": (0.56, 0.40, 0.31),
    "hair": (0.10, 0.075, 0.06),
    "jacket": (0.13, 0.14, 0.11),       # charcoal-olive
    "trousers": (0.075, 0.075, 0.07),
    "boots": (0.06, 0.05, 0.045),
    "eye": (0.30, 0.31, 0.28),
    "headset": (0.05, 0.05, 0.055),
    "headset band": (0.25, 0.24, 0.22),
}

# ---------------------------------------------------------------- the C2 stance (MPFB frame)
# MPFB builds her facing -Y with her left on +X. Positions below are in that frame, metres,
# relative to the floor. The comms bank's operator face stands 0.325 m in front of her toes'
# root (Castle at y 3.25, face at y 3.75 Second Wind units, 0.65 m each) and its top is
# 1.07 m up (sw_bridge_interior.CONSOLE_TOP), sloping up away from her.
CONSOLE_FRONT = 0.325
CONSOLE_TOP = 1.07
LEAN_DEG = 6.0          # bent a little toward the console from the lower back
HEAD_BOW_DEG = 12.0     # eyes on the screens
HEAD_TURN_DEG = -6.0    # a little toward her right hand
# Left hand: flat on the console's sloped face just past its near edge. The face is tipped 16
# degrees, high edge away from her, and its near edge stands about 4 cm above the casing top.
LEFT_WRIST = (0.17, -(CONSOLE_FRONT + 0.03), CONSOLE_TOP + 0.085)
LEFT_POLE = (0.55, 0.25, 1.0)                 # elbow out and back
LEFT_FINGERS = (-0.12, -1.0, 0.29)            # up the slope
LEFT_PALM = (0.0, -0.28, -1.0)                # palm on the face
# Right hand: up at the earpiece. The wrist sits below and in front of the ear, the fingers
# point up at it with the palm toward the head.
RIGHT_WRIST_FROM_EAR = (-0.045, -0.06, -0.12)
RIGHT_POLE = (-0.6, -0.25, 1.0)               # elbow out to her right and forward
RIGHT_FINGERS = (0.25, 0.25, 1.0)             # up, a little in and back toward the ear
RIGHT_PALM = (1.0, 0.1, 0.0)                  # toward the head


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    # Not --factory-startup: that would leave the MPFB extension disabled.
    cmd = [BLENDER, "--background", "--python", str(Path(__file__).resolve()), "--", *argv]
    sys.exit(subprocess.call(cmd))


def blender_main(argv):
    import bpy
    from mathutils import Matrix, Vector

    out = Path(argv[argv.index("--out") + 1]) if "--out" in argv else BLEND

    try:
        import addon_utils
        addon_utils.enable("bl_ext.blender_org.mpfb", default_set=True)
        from bl_ext.blender_org.mpfb.services.humanservice import HumanService
        from bl_ext.blender_org.mpfb.services.assetservice import AssetService
    except Exception as exc:     # noqa: BLE001
        sys.exit("MPFB is not installed in this Blender (Get Extensions > MPFB): %s" % exc)
    for part, name in [*((k, v) for k, v in ASSETS.items()), *(("clothes", c) for c in CLOTHES)]:
        if AssetService.find_asset_absolute_path(name, asset_subdir=part) is None:
            sys.exit("MakeHuman asset %s/%s is missing: install the MakeHuman system asset "
                     "pack in MPFB (Apply assets > Load pack)." % (part, name))

    # An empty scene, without a factory reset (which would also unload MPFB).
    for ob in list(bpy.data.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    for col in list(bpy.data.collections):
        bpy.data.collections.remove(col)
    scene = bpy.context.scene

    # ---- the body
    info = HumanService._create_default_human_info_dict()
    info["phenotype"] = PHENOTYPE
    info["name"] = "Castle"
    info["rig"] = "default"
    info.update(ASSETS)
    info["clothes"] = list(CLOTHES)
    info["skin_material_type"] = "NONE"
    settings = HumanService.get_default_deserialization_settings()
    settings.update(scale=0.1, subdiv_levels=1, override_skin_model="NONE")   # 0.1: dm to metres
    body = HumanService.deserialize_from_dict(info, settings)
    rig = body.parent
    rig.name = rig.data.name = "Castle rig"
    body.name = "Castle body"
    parts = [o for o in rig.children if o.type == "MESH"]
    for o in parts:
        if o is not body:
            o.name = "Castle " + o.name.split(".", 1)[-1]

    # ---- flat materials
    def flat(name):
        m = bpy.data.materials.get("Castle " + name) or bpy.data.materials.new("Castle " + name)
        rgb = COLOURS[name]
        m.diffuse_color = (*rgb, 1.0)
        m.roughness = 0.75
        m.use_nodes = True
        bsdf = m.node_tree.nodes.get("Principled BSDF")
        if bsdf is not None:
            bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
            bsdf.inputs["Roughness"].default_value = 0.75
        return m

    def paint(ob, name):
        ob.data.materials.clear()
        ob.data.materials.append(flat(name))
        ob.color = ob.data.materials[0].diffuse_color

    by_role = {"body": "skin", "high-poly": "eye", "eyebrow": "hair", "eyelashes": "hair",
               "short04": "hair", "shoes02": "boots", "male_casualsuit02": "jacket"}
    for o in parts:
        key = "body" if o is body else o.name.split(" ", 1)[1]
        paint(o, next(v for k, v in by_role.items() if key.startswith(k)))

    # The top-and-trousers suit is one mesh: faces below the hem around the hips take the
    # trousers colour (rest pose, arms hang well outside |x| 0.25).
    suit = bpy.data.objects["Castle male_casualsuit02"]
    suit.name = "Castle duty jacket and trousers"
    suit.data.materials.append(flat("trousers"))
    hem = 0.96
    for poly in suit.data.polygons:
        c = poly.center
        if c.z < hem and abs(c.x) < 0.25:
            poly.material_index = 1

    # ---- landmarks of the built body (rest pose, MPFB frame)
    verts = [body.matrix_world @ v.co for v in body.data.vertices]
    bones = rig.data.bones
    head_b = bones["head"]
    eye_z = (bones["eye.R"].head_local.z + bones["eye.L"].head_local.z) / 2
    band = [v for v in verts if abs(v.z - (eye_z - 0.02)) < 0.025 and v.y > -0.09]
    ear_r = min(band, key=lambda v: v.x)                       # her right ear, outermost point
    hair = bpy.data.objects["Castle short04"]
    hverts = [hair.matrix_world @ v.co for v in hair.data.vertices]
    nape_back = max(v.y for v in hverts if abs(v.x) < 0.03 and abs(v.z - eye_z) < 0.05)
    neck_c = bones["neck01"].head_local
    mouth = Vector((-0.024, bones["eye.R"].head_local.y - 0.005, eye_z - 0.075))

    # ---- what the pack has no piece for, bone-parented in the rest pose
    def bone_parent(ob, bone_name):
        b = bones[bone_name]
        ob.parent = rig
        ob.parent_type = "BONE"
        ob.parent_bone = bone_name
        # Bone parent space sits at the bone's tail, oriented with the bone.
        rest = rig.matrix_world @ Matrix.Translation(b.tail_local) @ b.matrix_local.to_3x3().to_4x4()
        ob.matrix_parent_inverse = rest.inverted()

    def add(op, name, mat, **kw):
        op(**kw)
        ob = bpy.context.object
        ob.name = ob.data.name = "Castle " + name
        paint(ob, mat)
        bpy.ops.object.shade_smooth()
        return ob

    bun = add(bpy.ops.mesh.primitive_uv_sphere_add, "bun", "hair", segments=20, ring_count=12,
              radius=1.0, location=(0, nape_back - 0.004, eye_z - 0.015))
    bun.scale = (0.036, 0.026, 0.03)
    bone_parent(bun, "head")

    collar = add(bpy.ops.mesh.primitive_cone_add, "duty jacket collar", "jacket", vertices=24,
                 radius1=0.075, radius2=0.066, depth=0.055, end_fill_type="NOTHING",
                 location=(neck_c.x, neck_c.y + 0.008, neck_c.z + 0.012))
    collar.rotation_euler = (math.radians(-8), 0, 0)
    collar.modifiers.new("Thickness", "SOLIDIFY").thickness = 0.006
    bone_parent(collar, "neck01")

    cup = add(bpy.ops.mesh.primitive_cylinder_add, "earpiece", "headset", vertices=20,
              radius=0.014, depth=0.012, location=(ear_r.x - 0.005, ear_r.y, ear_r.z),
              rotation=(0, math.radians(90), 0))
    bone_parent(cup, "head")
    boom_a = Vector((ear_r.x - 0.010, ear_r.y - 0.008, ear_r.z - 0.006))
    boom_b = mouth + Vector((-0.035, -0.015, 0.0))
    d = boom_b - boom_a
    boom = add(bpy.ops.mesh.primitive_cylinder_add, "boom microphone", "headset", vertices=10,
               radius=0.0032, depth=d.length, location=(boom_a + boom_b) / 2)
    boom.rotation_euler = d.to_track_quat("Z", "Y").to_euler()
    bone_parent(boom, "head")
    tip = add(bpy.ops.mesh.primitive_uv_sphere_add, "boom microphone capsule", "headset",
              segments=12, ring_count=8, radius=0.0055, location=boom_b)
    bone_parent(tip, "head")
    band_o = add(bpy.ops.mesh.primitive_torus_add, "earpiece hook", "headset band",
                 major_radius=0.019, minor_radius=0.0022, major_segments=24, minor_segments=6,
                 location=(ear_r.x + 0.002, ear_r.y + 0.008, ear_r.z + 0.012),
                 rotation=(0, math.radians(90), 0))
    bone_parent(band_o, "head")

    # ---- the pose
    view = bpy.context.view_layer
    pbs = rig.pose.bones

    def rotate_world(name, axis, deg):
        """Turn a pose bone (and so its children) about an armature-space axis through its head."""
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
        """Point a bone along `direction` by the smallest turn from its rest direction."""
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = rest.col[1].rotation_difference(Vector(direction).normalized())
        m = (q.to_matrix() @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def orient(name, fingers, palm, rest_palm):
        """Turn a hand so its bone axis runs along `fingers` and its palm faces `palm`."""
        pb = pbs[name]
        rest = pb.bone.matrix_local.to_3x3()
        q = frame(fingers, palm) @ frame(rest.col[1], rest_palm).transposed()
        m = (q @ rest).to_4x4()
        m.translation = pb.head
        pb.matrix = m
        view.update()

    def two_bone(side, wrist, pole):
        """Shoulder-to-wrist solve on the MakeHuman arm (two segments of two bones each)."""
        up = ["upperarm01." + side, "upperarm02." + side]
        lo = ["lowerarm01." + side, "lowerarm02." + side]
        a = sum(bones[b].length for b in up)
        b = sum(bones[x].length for x in lo)
        s = pbs[up[0]].head.copy()
        w = Vector(wrist)
        d = w - s
        L = min(d.length, (a + b) * 0.999)
        dn = d.normalized()
        # Elbow distance along the reach and off it (law of cosines), off toward the pole.
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
        return wrist_pt

    def curl(side, finger_deg, thumb_deg, palm):
        n = Vector(palm).normalized()
        for f in range(1, 6):
            deg = thumb_deg if f == 1 else finger_deg
            for j in range(1, 4):
                name = "finger%d-%d.%s" % (f, j, side)
                pb = pbs[name]
                axis = (pb.tail - pb.head).normalized().cross(n)
                # Whichever way brings the tip toward the palm side.
                best = None
                for sgn in (1, -1):
                    r = Matrix.Rotation(math.radians(sgn * deg), 3, axis)
                    t = r @ (pb.tail - pb.head)
                    if best is None or t.dot(n) > best[1]:
                        best = (sgn, t.dot(n))
                rotate_world(name, axis, best[0] * deg)

    rest_palm = {}
    for side in ("L", "R"):
        # MakeHuman's rest pose hangs the arms with the palms toward the thighs.
        rest_palm[side] = Vector((-1.0, 0, 0)) if side == "L" else Vector((1.0, 0, 0))

    rotate_world("spine04", (1, 0, 0), LEAN_DEG * 0.6)
    rotate_world("spine02", (1, 0, 0), LEAN_DEG * 0.4)
    rotate_world("neck01", (1, 0, 0), HEAD_BOW_DEG * 0.4)
    rotate_world("head", (1, 0, 0), HEAD_BOW_DEG * 0.6)
    rotate_world("head", (0, 0, 1), HEAD_TURN_DEG)
    # Weight on the right leg: the left knee eases and the left hip drops a touch.
    rotate_world("upperleg01.L", (1, 0, 0), -4)
    rotate_world("lowerleg01.L", (1, 0, 0), 8)
    rotate_world("foot.L", (1, 0, 0), -4)

    # Landmarks move with the bent spine and bowed head; read them back posed.
    head_pose = pbs["head"].matrix @ head_b.matrix_local.inverted()
    ear_posed = head_pose @ ear_r
    right_wrist = ear_posed + Vector(RIGHT_WRIST_FROM_EAR)
    for side, wrist, pole, fingers, palm, fdeg in (
            ("L", LEFT_WRIST, LEFT_POLE, LEFT_FINGERS, LEFT_PALM, 12),
            ("R", right_wrist, RIGHT_POLE, RIGHT_FINGERS, RIGHT_PALM, 28)):
        two_bone(side, wrist, pole)
        orient("wrist." + side, fingers, palm, rest_palm[side])
        curl(side, fdeg, 10, palm)

    # Re-ground: the soles on z 0 after the leg change.
    dg = bpy.context.evaluated_depsgraph_get()
    ev = body.evaluated_get(dg)
    lowest = min((ev.matrix_world @ v.co).z for v in ev.data.vertices)
    shoes = bpy.data.objects["Castle shoes02"].evaluated_get(dg)
    lowest = min(lowest, min((shoes.matrix_world @ v.co).z for v in shoes.data.vertices))
    rig.location.z -= lowest

    # ---- the appendable package: a root at her feet, turned so she faces +X
    col = bpy.data.collections.new(COLLECTION)
    scene.collection.children.link(col)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = "ARROWS", 0.3
    col.objects.link(root)
    rig.parent = root
    rig.rotation_euler = (0, 0, math.radians(90))      # MPFB's -Y front onto +X
    for ob in [rig, *rig.children_recursive]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        col.objects.link(ob)
    rig.show_in_front = False
    rig.hide_render = True
    root["height_m"] = round(max(v.z for v in verts) - min(v.z for v in verts), 3)
    root["source"] = "art-source/company-prologue/castle-figure/build_castle.py"
    root["frame"] = "metres; z up from the soles; she faces +X"

    out.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(out), compress=True)
    print("CASTLE height %.3f m, ear %s -> %s" % (root["height_m"], tuple(round(c, 3) for c in ear_r),
                                                   out), flush=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        launch(argv)
