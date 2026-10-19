"""Castle's face against her portrait, 1920x1080 at 100%, in EEVEE and Workbench.

    python face_review.py --out DIR [--blend PATH] [--prefix NAME-]

Writes, per engine:
- <prefix>face-vs-portrait-<engine>.png: her portrait (art-source/portraits/castle) on the
  left, the figure on the right from the portrait's angle (three-quarter from her left, at her
  eye height, 85 mm);
- <prefix>face-turn-<engine>.png: front, three-quarter and profile from her left.

Cameras aim at the posed eyes, so the C2 pose's bowed head stays framed. EEVEE uses
castle_review.light_eevee. Nothing here is a finished render, and outputs are never committed.
"""
import math
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "castle-figure.blend"
PORTRAIT = HERE.parents[1] / "portraits" / "castle" / "castle-master.png"
W, H = 1920, 1080
# (panel, yaw round her from the front (90 = her left), distance m, lens mm, width px)
COMPARE = ("compare", 32, 0.75, 85, W - H)
TURN = [("front", 0, 0.75, 85, W // 3), ("three-quarter", 40, 0.75, 85, W // 3),
        ("profile", 90, 0.75, 85, W - 2 * (W // 3))]


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    from PIL import Image
    out = Path(argv[argv.index("--out") + 1])
    blend = Path(argv[argv.index("--blend") + 1]) if "--blend" in argv else BLEND
    prefix = argv[argv.index("--prefix") + 1] if "--prefix" in argv else ""
    out.mkdir(parents=True, exist_ok=True)
    portrait = Image.open(PORTRAIT).convert("RGB").resize((H, H), Image.LANCZOS)
    for engine in ("eevee", "workbench"):
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.check_call([BLENDER, "--background", "--factory-startup", str(blend), "--python",
                                   str(Path(__file__).resolve()), "--", "--panels", tmp, "--engine", engine])
            sheet = Image.new("RGB", (W, H))
            sheet.paste(portrait, (0, 0))
            sheet.paste(Image.open(Path(tmp) / "compare.png").convert("RGB"), (H, 0))
            sheet.save(out / ("%sface-vs-portrait-%s.png" % (prefix, engine)))
            sheet = Image.new("RGB", (W, H))
            x = 0
            for name, *_, width in TURN:
                sheet.paste(Image.open(Path(tmp) / (name + ".png")).convert("RGB"), (x, 0))
                x += width
            sheet.save(out / ("%sface-turn-%s.png" % (prefix, engine)))
            print("FACE", engine, out, flush=True)


def blender_panels(argv):
    import bpy
    from mathutils import Vector
    sys.path.insert(0, str(HERE))
    from castle_review import light_eevee
    panels = Path(argv[argv.index("--panels") + 1])
    engine = argv[argv.index("--engine") + 1]
    scene = bpy.context.scene
    if engine == "eevee":
        light_eevee(scene)
    else:
        scene.render.engine = "BLENDER_WORKBENCH"
        sh = scene.display.shading
        sh.light, sh.color_type = "STUDIO", "MATERIAL"
        sh.show_shadows, sh.show_cavity = True, True
        sh.background_type, sh.background_color = "VIEWPORT", (.16, .16, .17)
        scene.view_settings.view_transform = "Standard"
    scene.render.resolution_y, scene.render.resolution_percentage = H, 100
    scene.render.image_settings.file_format = "PNG"
    root = bpy.data.objects["Castle figure root"]
    root.location, root.rotation_euler = (0, 0, 0), (0, 0, 0)
    bpy.context.view_layer.update()
    rig = bpy.data.objects["Castle rig"]
    eyes = [rig.matrix_world @ rig.pose.bones[b].head for b in ("eye.L", "eye.R")]
    aim = (eyes[0] + eyes[1]) / 2 + Vector((0, 0, -0.03))     # between the eyes and the mouth
    # Her face's own forward: across the eyes, turned a quarter (she faces +X; left is +Y).
    across = (eyes[0] - eyes[1]).normalized()
    forward = Vector((across.y, -across.x, 0)).normalized()
    cam = bpy.data.objects.new("Face camera", bpy.data.cameras.new("Face camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.sensor_width, cam.data.sensor_fit = 36.0, "VERTICAL"
    cam.data.clip_start = .02
    for name, yaw, dist, lens, width in [COMPARE, *TURN]:
        a = math.radians(yaw)
        d = forward * math.cos(a) + Vector((-forward.y, forward.x, 0)) * math.sin(a)
        cam.location = aim + d * dist + Vector((0, 0, 0.02))
        cam.rotation_euler = (aim - cam.location).to_track_quat("-Z", "Y").to_euler()
        cam.data.lens = lens
        scene.render.resolution_x = width
        scene.render.filepath = str(panels / (name + ".png"))
        bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_panels(argv)
    else:
        launch(argv)
