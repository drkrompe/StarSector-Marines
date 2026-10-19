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
- 'Pilot rig', MPFB's default skeleton with its weights, seated, with the action 'Pilot draws
  the yoke out and flies' on C2's frames (frame = C2 seconds x 24);
- the body, eyes, eyebrows, eyelashes, hair and clothes MPFB fits to it, from MakeHuman's own
  CC0 system-asset pack (listed in README.md);
- what that pack has no piece for, modelled here on the posed body: the coverall's collar, the
  harness, the patches, the belt and the headset;
- 'Pilot yoke', the helm yoke: its housing, the column (keyed: drawn out and pushed) and the
  wheel (keyed: turn).

He flies with a yoke (a wheel on a telescoping column out of a housing under the console's
sloped face) that belongs to this package, because it moves with him. The column is how the
helm fits its pilot: stowed against the console, drawn out to where his hands rest with his back
in the seat. His poses are solved rather than keyed by hand, as Castle's is
(`castle-figure/build_castle.py`): relaxed, sat back with his forearms on the armrests; leaned
in to take the stowed wheel; drawing it out; and flying, sat back with his elbows bent at his
sides and his hands round the grips, the yoke drawn out, pushed and turned. Hips are placed on
the seat, each leg a two-bone solve to an ankle on the dais, each arm to a wrist target. The
action eases between them over C2's timeline: in to the yoke and drawing it out before the
de-orbit, pushed for its pitch-down with a small turn, and letting go once it is done, the yoke
left where he set it.
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
    "yoke": (0.11, 0.11, 0.105),           # the helm yoke: worn dark metal
    "yoke boss": (0.17, 0.17, 0.16),       # its housing, bolted to the console face
    "grip": (0.03, 0.03, 0.03),            # rubber
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
# The helm yoke (owner, 2026-10-19: a futuristic steering wheel, as on the Rodger Young or the
# Aliens dropship): a wheel on a column out of a housing bolted to the console. It turns to roll
# and slides to pitch, push nose-down and pull nose-up. It is also how the helm fits whoever sits
# it (owner, 2026-10-19): the column telescopes, so a pilot draws the wheel out to where his
# hands rest with his back in the seat and can fly for hours, and stows it against the console
# when he is off the controls. It belongs to this package, not to the bridge, because it moves
# with him; the bridge's console is where its housing sits, under the overhang of the sloped face
# so the helm's screen above it stays clear.
# Package frame, metres: where the column leaves the housing, the column's rise toward him
# (degrees up from horizontal), the head's tilt of the wheel's axis (degrees: the wheel leans
# back toward him less than the column does), how far the hub stands out along the column
# stowed and drawn out to him, the grips' centres off the hub (side, up), the column's length,
# and the push and turn he flies with.
YOKE_MOUNT = (0.50, 0.0, 0.40)
YOKE_RISE, YOKE_HEAD = 40.0, 18.0
YOKE_STOWED, YOKE_OUT = 0.02, 0.40
YOKE_GRIP = (0.17, 0.035)
YOKE_GRIP_AFT = 0.03                       # the handles stand this far aft of the hub, toward him
YOKE_GRIP_R = 0.022                        # the handles' radius: a fist's worth of rubber
# Built heavy (owner, 2026-10-19: the first one read flimsy): a ship's helm, not a game controller.
YOKE_COLUMN = 0.55
YOKE_PUSH, YOKE_TURN = 0.05, 10.0          # m in along the column, degrees (positive: rolls to port)

# His poses (owner, 2026-10-19). At rest he sits back against the seat, forearms on the
# armrests, hands hanging loose over their ends, the yoke stowed. To fly he leans in and takes
# the stowed wheel, then draws it out to him as he sits back, and flies with his back in the
# seat, elbows bent at his sides; he pushes it for the de-orbit and turns it a little. Each:
# slide (m his hips move forward on the pan), spine lean (degrees forward from upright; the seat
# back reclines 8), head lift (negative raises the chin), head turn (positive to port), the
# elbows' pole from the shoulder (fwd, side out, z); at rest the wrists (fwd, side, z) and the
# fingers' curl, left and right; at the helm the yoke's state (how far out along the column m,
# turn degrees) whose grips the hands close round.
RELAXED = dict(slide=0.0, lean=-6.0, head_lift=2.0, head_turn=6.0,
               wrists=((0.1, 0.27, 0.565), (0.08, -0.27, 0.565)),
               elbow_pole=(-0.6, 0.35, -0.8), curl=(30, 34))
REACH = dict(slide=0.06, lean=22.0, head_lift=-20.0, head_turn=3.0, yoke=(YOKE_STOWED, 0.0),
             elbow_pole=(-0.3, 0.8, -0.7))
DRAWING = dict(slide=0.03, lean=11.0, head_lift=-10.0, head_turn=3.0,
               yoke=((YOKE_STOWED + YOKE_OUT) / 2, 0.0), elbow_pole=(-0.4, 0.65, -0.8))
FLYING = dict(slide=0.0, lean=1.0, head_lift=-3.0, head_turn=3.0, yoke=(YOKE_OUT, 0.0),
              elbow_pole=(-0.5, 0.5, -0.9))
PUSHED = dict(FLYING, lean=4.0, head_lift=-6.0, yoke=(YOKE_OUT - YOKE_PUSH, 0.0))
TURNED = dict(PUSHED, head_turn=6.0, yoke=(YOKE_OUT - YOKE_PUSH, YOKE_TURN))
# When, in C2 seconds (24 fps, frame = t x 24): relaxed through the hail; in to the stowed yoke
# and drawing it out before Second Wind's de-orbit starts at 9.6 s; held through its pitch-down
# (to 13.1 s); then he lets go and settles back, leaving the yoke drawn out. Eased in and out;
# the entry after C2 stays relaxed.
REACH_IN = (8.3, 8.8)
DRAW_OUT = (8.8, 9.45)
PUSH_IN, PUSH_OUT = (9.5, 10.4), (12.5, 13.2)      # the pitch-down runs 9.6-13.1 s
TURN_IN, TURN_OUT = (10.7, 11.3), (11.9, 12.5)     # a small correction while pushed
LET_GO = (13.5, 14.7)
FPS, LAST_FRAME = 24, 18 * 24              # C2 (15 s) and the entry transition (3 s)


def _same(a, b, eps=1e-6):
    return all(abs(x - y) < eps for x, y in zip(a, b))


def _close(a, b, eps=1e-5):
    return all(abs(a[i][j] - b[i][j]) < eps for i in range(4) for j in range(4))


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

    def rest_palm(side):
        """The way the palm faces in the rig's rest pose, measured off the hand's bones: square to
        the fingers and across the knuckles, on the side the fingers curl toward."""
        w = bones["wrist." + side]
        f = (w.tail_local - w.head_local).normalized()
        a = (bones["finger5-1." + side].head_local - bones["finger2-1." + side].head_local).normalized()
        n = f.cross(a).normalized()
        d1 = bones["finger3-1." + side].tail_local - bones["finger3-1." + side].head_local
        d3 = bones["finger3-3." + side].tail_local - bones["finger3-3." + side].head_local
        return n if n.dot(d3.normalized() - d1.normalized()) > 0 else -n

    def wrap(name, centre, axis, toward, radius):
        """Aim a finger bone along the circle of `radius` round a bar (its centre and axis), the
        way round whose tangent leads toward `toward`: the bone's tail lands on that circle one
        bone-length further round from its head."""
        pb = pbs[name]
        h = pb.head.copy()
        rel = h - centre
        along = axis * axis.dot(rel)
        radial = rel - along
        if radial.length < 1e-6:
            return
        u = radial.normalized()
        sgn = 1 if axis.cross(u).dot(toward) > 0 else -1
        theta = sgn * pb.bone.length / radius
        tail = centre + along + (Matrix.Rotation(theta, 3, axis) @ u) * radius
        aim(name, tail - h)

    def grip_hand(side, centre, axis, f, p):
        """Close the hand round a bar under the palm: each finger wraps it forward from the
        knuckle and back round its inner side; the thumb crosses behind it the other way."""
        for k in range(2, 6):
            for j in range(1, 4):
                wrap("finger%d-%d.%s" % (k, j, side), centre, axis, f, YOKE_GRIP_R + 0.011)
        aim("finger1-1." + side, f * 0.3 + p * 0.9)
        for j in (2, 3):
            wrap("finger1-%d.%s" % (j, side), centre, axis, p, YOKE_GRIP_R + 0.013)

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

    def tilt(deg):
        """Pitch about his side axis: his forward axis goes forward and down by deg."""
        return Matrix.Rotation(math.radians(deg), 4, mp(0, 1, 0))

    def hub_at(out):
        """The wheel's hub (MPFB frame) with the column drawn out to `out` m."""
        r = math.radians(YOKE_RISE)
        return mp(YOKE_MOUNT[0] - out * math.cos(r), YOKE_MOUNT[1], YOKE_MOUNT[2] + out * math.sin(r))

    def yoke_frame(out, turn):
        """The wheel's placement (MPFB frame) drawn out to `out` (m) and turned (degrees)."""
        return (Matrix.Translation(hub_at(out)) @ tilt(YOKE_HEAD)
                @ Matrix.Rotation(math.radians(turn), 4, mp(1, 0, 0)))

    def grip(side, out, turn):
        sgn = 1 if side == "L" else -1
        # The middle of the handle he holds, which flares out a little toward its top.
        return yoke_frame(out, turn) @ mp(-YOKE_GRIP_AFT, sgn * (YOKE_GRIP[0] + 0.006), YOKE_GRIP[1])

    def solve(spec):
        """Pose him from the rest pose: legs on the dais, the spine and head, arms to the wrists.
        Returns every pose bone's matrix_basis."""
        for pb in pbs:
            pb.matrix_basis = Matrix.Identity(4)
        view.update()
        # Scoot: the whole body slides forward on the pan; the legs re-solve to the same feet.
        root_pb = next(pb for pb in pbs if pb.parent is None)
        root_pb.matrix = Matrix.Translation(mp(spec["slide"], 0, 0)) @ root_pb.matrix
        view.update()
        for side, sgn in (("L", 1), ("R", -1)):
            two_bone(["upperleg01." + side, "upperleg02." + side], ["lowerleg01." + side, "lowerleg02." + side],
                     local(mp(ANKLE[0], sgn * ANKLE_SIDE, ANKLE[1])), local(mp(KNEE_POLE[0], sgn * 0.2, KNEE_POLE[1])))
            aim("foot." + side, mp(1.0, sgn * 0.08, -0.25))         # sole flat, toes a little out
        for name, share in (("spine05", 0.15), ("spine04", 0.25), ("spine03", 0.3), ("spine02", 0.3)):
            if name in pbs:
                rotate_world(name, (1, 0, 0), spec["lean"] * share)
        rotate_world("neck01", (1, 0, 0), spec["head_lift"] * 0.5)
        rotate_world("head", (1, 0, 0), spec["head_lift"] * 0.5)
        rotate_world("head", (0, 0, 1), spec["head_turn"])
        for k, side in enumerate(("L", "R")):
            sgn = 1 if side == "L" else -1
            pole = spec["elbow_pole"]
            sh = pbs["upperarm01." + side].head
            if "yoke" in spec:
                # Round the grip from outside: fingers forward and a little up, palm in toward
                # the hub, the wrist back from the bar by the palm and out by the hand's depth.
                out, turn = spec["yoke"]
                R = yoke_frame(out, turn).to_3x3()
                fingers = R @ mp(1.0, 0, 0.35)
                palm = R @ mp(0, -sgn, 0)
                bar = local(grip(side, out, turn))
                bar_axis = (R @ mp(0, sgn * 0.012, 0.125)).normalized()
                wrist = grip(side, out, turn) - fingers.normalized() * 0.075 - palm * (YOKE_GRIP_R + 0.014)
            else:           # over the armrest's end, palm down, fingers drooping
                f, s_, z = spec["wrists"][k]
                wrist = mp(f, s_, z)
                fingers = mp(1.0, -sgn * 0.1, -0.6)
                palm = mp(0.3, 0, -1.0)
            two_bone(["upperarm01." + side, "upperarm02." + side], ["lowerarm01." + side, "lowerarm02." + side],
                     local(wrist), sh + mp(pole[0], sgn * pole[1], pole[2]))
            orient("wrist." + side, fingers, palm, rest_palm(side))
            if "yoke" in spec:
                grip_hand(side, bar, bar_axis, fingers.normalized(), palm.normalized())
            else:
                curl(side, spec["curl"][k], 12, palm)
        return {pb.name: pb.matrix_basis.copy() for pb in pbs}

    relaxed = solve(RELAXED)
    reach = solve(REACH)
    drawing = solve(DRAWING)
    pushed = solve(PUSHED)
    turned = solve(TURNED)
    flying = solve(FLYING)            # left posed: what the pack lacks is fitted to this pose
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

    # ---- the yoke: a housing on the console face, a column sliding out of it, the wheel on the
    # column's end. Built in the MPFB frame under one empty; turned onto +X with the rig.
    yoke = bpy.data.objects.new("Pilot yoke", None)
    scene.collection.objects.link(yoke)

    def tube(bm, a, b, r, segs=16):
        d = b - a
        m = Matrix.Translation((a + b) / 2) @ d.to_track_quat("Z", "Y").to_matrix().to_4x4()
        bmesh.ops.create_cone(bm, cap_ends=True, segments=segs, radius1=r, radius2=r, depth=d.length, matrix=m)

    def solid(name, build, mat, parent, at=Vector()):
        bm = bmesh.new()
        build(bm)
        me = bpy.data.meshes.new("Pilot " + name)
        bm.to_mesh(me)
        bm.free()
        for f in me.polygons:
            f.use_smooth = True
        ob = obj(name, me, mat)
        ob.parent = parent
        ob.location = at
        return ob

    # The housing is built in the column's own frame (forward = into the console along the
    # column): a sleeve block under the face's overhang, its aft face square to the column.
    mount = mp(*YOKE_MOUNT)
    along = tilt(YOKE_RISE)
    hw, hh, hd = 0.20, 0.17, 0.14            # housing width, height, depth along the column

    def boss(bm):
        bmesh.ops.create_cube(bm, size=1.0, matrix=Matrix.Translation(mount) @ along
                              @ Matrix.Translation(mp(hd / 2, 0, 0)) @ Matrix.Diagonal((hw, hd, hh, 1)))
        tube(bm, mount + along @ mp(0.02, 0, 0), mount - along @ mp(0.012, 0, 0), 0.066, 24)   # the flange
        for k, r in enumerate((0.058, 0.052, 0.058, 0.05)):                        # the bellows boot
            tube(bm, mount - along @ mp(0.012 + k * 0.012, 0, 0), mount - along @ mp(0.024 + k * 0.012, 0, 0), r, 24)
        for sy, sz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):                       # bolts on its aft face
            c = mount + along @ mp(0, sy * hw * 0.38, sz * hh * 0.38)
            tube(bm, c, c - along @ mp(0.012, 0, 0), 0.012, 6)
        lever = mount + along @ mp(-0.01, hw / 2, hh * 0.15)                    # the column lock
        tube(bm, lever, lever + along @ mp(-0.07, 0.03, 0.015), 0.01, 10)
        tube(bm, lever + along @ mp(-0.07, 0.03, 0.015), lever + along @ mp(-0.085, 0.034, 0.018), 0.018, 12)
    solid("yoke housing", boss, "yoke boss", yoke)

    def col_(bm):                   # the column runs from the hub forward and down into the housing
        tube(bm, mp(0, 0, 0), mp(YOKE_COLUMN, 0, 0), 0.034, 24)
        tube(bm, mp(0.02, 0, 0), mp(0.06, 0, 0), 0.042, 24)                      # the clamp collar
        for k in range(1, 9):       # its graduations: where he set it is the first thing a relief reads
            tube(bm, mp(0.06 + k * 0.04, 0, 0), mp(0.065 + k * 0.04, 0, 0), 0.0365, 24)
    column = solid("yoke column", col_, "yoke", yoke, hub_at(YOKE_STOWED))
    column.rotation_mode = "XYZ"
    column.rotation_euler = along.to_euler()

    def head_(bm):                  # the knuckle that sets the wheel back less steeply than the column
        tube(bm, mp(-0.02, 0, 0), mp(0.07, 0, 0), 0.05, 24)
        bmesh.ops.create_cube(bm, size=1.0, matrix=Matrix.Translation(mp(-0.02, 0, -0.005))
                              @ Matrix.Diagonal((0.13, 0.07, 0.09, 1)))
    wheel = bpy.data.objects.new("Pilot yoke wheel", None)
    scene.collection.objects.link(wheel)
    wheel.parent = column
    wheel.rotation_mode = "XYZ"
    head_tilt = tilt(YOKE_HEAD - YOKE_RISE)
    wheel.rotation_euler = head_tilt.to_euler()
    solid("yoke head", head_, "yoke", wheel)
    gw, gz = YOKE_GRIP

    def frame_(bm):
        for sgn in (1, -1):         # the bar out to each grip's foot, sweeping back a little
            tube(bm, mp(-0.02, sgn * 0.05, -0.01), mp(-YOKE_GRIP_AFT, sgn * gw, gz - 0.07), 0.024, 16)
        # the crossbeam under the bars, flat-faced toward him
        bmesh.ops.create_cube(bm, size=1.0, matrix=Matrix.Translation(mp(-0.026, 0, -0.03))
                              @ Matrix.Diagonal((2 * gw - 0.03, 0.035, 0.03, 1)))
    solid("yoke wheel frame", frame_, "yoke", wheel)

    def grips(bm):
        for sgn in (1, -1):         # the handles, flared out a touch at the top
            a, b = mp(-YOKE_GRIP_AFT, sgn * gw, gz - 0.07), mp(-YOKE_GRIP_AFT, sgn * (gw + 0.012), gz + 0.07)
            tube(bm, a, b, YOKE_GRIP_R, 20)
            d = (b - a).normalized()
            tube(bm, a - d * 0.005, a + d * 0.015, YOKE_GRIP_R + 0.006, 20)        # the foot collar
            tube(bm, b - d * 0.01, b + d * 0.012, YOKE_GRIP_R + 0.007, 20)         # the pommel
    solid("yoke grips", grips, "grip", wheel)

    def tape(bm):                   # his left grip, taped where the rubber split
        tube(bm, mp(-YOKE_GRIP_AFT, gw + 0.004, gz + 0.012), mp(-YOKE_GRIP_AFT, gw + 0.006, gz + 0.04), YOKE_GRIP_R + 0.0025, 20)
    solid("yoke grip tape", tape, "tape", wheel)

    # ---- skin the cloth-borne parts to the rig: fitted in the flying pose, they must follow the
    # chest and arms as he leans back, which no single bone does. Each vertex takes the weights
    # of the nearest coverall vertex and is carried back to the rest pose through that blend.
    from mathutils.kdtree import KDTree
    subs = [m for m in suit.modifiers if m.type == "SUBSURF"]
    for m in subs:
        m.show_viewport = False
    view.update()
    ev = suit.evaluated_get(bpy.context.evaluated_depsgraph_get())
    me_ev = ev.to_mesh()
    if len(me_ev.vertices) != len(suit.data.vertices):
        sys.exit("the coverall's evaluated mesh does not match its vertices; cannot copy weights")
    kd = KDTree(len(me_ev.vertices))
    for v in me_ev.vertices:
        kd.insert(ev.matrix_world @ v.co, v.index)
    kd.balance()
    ev.to_mesh_clear()
    for m in subs:
        m.show_viewport = True
    names = {g.index: g.name for g in suit.vertex_groups}
    deform = {}
    for pb in pbs:
        deform[pb.name] = (rig.matrix_world @ pb.matrix @ pb.bone.matrix_local.inverted()
                           @ rig.matrix_world.inverted())
    to_rig = rig.matrix_world.inverted()
    for ob in [o for o in scene.objects if o.type == "MESH" and o.name.startswith(
            ("Pilot harness", "Pilot belt", "Pilot patch", "Pilot coverall collar"))]:
        for m in [m for m in ob.modifiers]:
            if m.type == "SOLIDIFY":            # the collar: bake its thickness in first
                with bpy.context.temp_override(object=ob, active_object=ob):
                    bpy.ops.object.modifier_apply(modifier=m.name)
        world = ob.matrix_world.copy()
        groups = {}
        for v in ob.data.vertices:
            p_ = world @ v.co
            _, i, _ = kd.find(p_)
            ws = [(names[g.group], g.weight) for g in suit.data.vertices[i].groups
                  if names[g.group] in deform and g.weight > 1e-4]
            total = sum(w for _, w in ws) or 1.0
            M = sum((deform[b] * (w / total) for b, w in ws), Matrix(((0,) * 4,) * 4)) if ws else Matrix.Identity(4)
            v.co = to_rig @ (M.inverted() @ p_)
            for b, w in ws:
                groups.setdefault(b, []).append((v.index, w / total))
        ob.parent, ob.parent_type, ob.parent_bone = rig, "OBJECT", ""
        ob.matrix_parent_inverse = Matrix.Identity(4)
        ob.matrix_basis = Matrix.Identity(4)
        for b, entries in groups.items():
            vg = ob.vertex_groups.new(name=b)
            for i, w in entries:
                vg.add([i], w, "REPLACE")
        ob.modifiers.new("Armature", "ARMATURE").object = rig
    view.update()

    # ---- the appendable package: root on the dais under the seat's axis, he faces +X
    col = bpy.data.collections.new(COLLECTION)
    scene.collection.children.link(col)
    root = bpy.data.objects.new(ROOT, None)
    root.empty_display_type, root.empty_display_size = "ARROWS", 0.3
    col.objects.link(root)
    pivot = Matrix.Rotation(math.radians(90), 4, "Z")          # MPFB's -Y front onto +X
    rig.matrix_world = pivot @ rig.matrix_world
    rig.parent = root
    yoke.matrix_world = pivot @ yoke.matrix_world
    yoke.parent = root
    for ob in [yoke, *yoke.children_recursive]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        col.objects.link(ob)
    for ob in [rig, *rig.children_recursive]:
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        col.objects.link(ob)
    rig.hide_render = True
    root["seat_top_m"] = round(SEAT_TOP, 4)
    root["source"] = "art-source/company-prologue/pilot-figure/build_pilot.py"
    root["frame"] = "metres; z up from the dais plating at the helm seat's axis; he faces +X"

    # ---- the move: relaxed, in to the controls, and back, keyed on every frame (a consumer that
    # resets interpolation to linear, as c2_bridge_render does, keeps the easing)
    def ease(x):
        x = min(max(x, 0.0), 1.0)
        return x * x * x * (x * (x * 6 - 15) + 10)

    def rise(sec, span):
        return ease((sec - span[0]) / (span[1] - span[0]))

    def ramp(sec, up, down):
        if sec <= up[0] or sec >= down[1]:
            return 0.0
        if sec < up[1]:
            return ease((sec - up[0]) / (up[1] - up[0]))
        if sec > down[0]:
            return 1.0 - ease((sec - down[0]) / (down[1] - down[0]))
        return 1.0

    # Chained: each state blends from everything before it, and letting go blends the lot back
    # to the relaxed pose, so once he has let go the earlier weights no longer matter.
    states = (relaxed, reach, drawing, flying, pushed, turned)
    moving = [pb for pb in pbs if any(not _close(relaxed[pb.name], st[pb.name]) for st in states[1:])]
    parts_ = {}
    for pb in moving:
        pb.rotation_mode = "QUATERNION"
        locs, quats = [], []
        for st in states:
            loc, q, _ = st[pb.name].decompose()
            if quats and quats[-1].dot(q) < 0:
                q.negate()
            locs.append(loc)
            quats.append(q)
        parts_[pb.name] = (locs, quats)

    def weights(sec):
        """Reach, draw-out progress, push, turn and let-go shares at a C2 second."""
        return (rise(sec, REACH_IN), rise(sec, DRAW_OUT), ramp(sec, PUSH_IN, PUSH_OUT),
                ramp(sec, TURN_IN, TURN_OUT), rise(sec, LET_GO))

    rig.animation_data_create()
    rig.animation_data.action = bpy.data.actions.new("Pilot draws the yoke out and flies")
    for ob in (column, wheel):
        ob.animation_data_create()
        ob.animation_data.action = bpy.data.actions.new(ob.name + " moves")
    last = None
    for frame in range(0, LAST_FRAME + 1):
        wt = weights(frame / FPS)
        nxt = weights((frame + 1) / FPS)
        if last is not None and 0 < frame < LAST_FRAME and _same(wt, last) and _same(wt, nxt):
            continue                # a held stretch: its ends are keyed
        a, d, pw, tw, r = wt
        # The draw-out runs through the half-drawn pose, so the hands keep close to the grips
        # they are carrying out along the column.
        chain = (a, min(1.0, 2 * d), max(0.0, 2 * d - 1), pw, tw)
        for pb in moving:
            locs, quats = parts_[pb.name]
            loc, q = locs[0], quats[0]
            for k, w in enumerate(chain, 1):
                loc, q = loc.lerp(locs[k], w), q.slerp(quats[k], w)
            loc, q = loc.lerp(locs[0], r), q.slerp(quats[0], r)
            pb.location, pb.rotation_quaternion = loc, q
            pb.keyframe_insert("location", frame=frame)
            pb.keyframe_insert("rotation_quaternion", frame=frame)
        column.location = hub_at(YOKE_STOWED + (YOKE_OUT - YOKE_STOWED) * d - YOKE_PUSH * pw)
        column.keyframe_insert("location", frame=frame)
        wheel.rotation_euler = (head_tilt @ Matrix.Rotation(math.radians(YOKE_TURN * tw), 4, mp(1, 0, 0))).to_euler()
        wheel.keyframe_insert("rotation_euler", frame=frame)
        last = wt
    bpy.context.scene.frame_set(0)
    root["reach_s"], root["draw_out_s"], root["let_go_s"] = list(REACH_IN), list(DRAW_OUT), list(LET_GO)

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
