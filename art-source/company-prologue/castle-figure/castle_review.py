"""Workbench review sheets of the Castle figure, 1920x1080 at 100%.

    python castle_review.py --out DIR

Writes two turnarounds of castle-figure.blend (build_castle.py builds it), each four views
side by side on one 1920x1080 sheet: front, her left, back and her right, then the same four
three-quarter turns closer on the head and shoulders. Then it renders her on Second Wind's
bridge through the bridge's own review script (patched state, sw_bridge_interior.figures with
the MakeHuman Castle): C2's port station, the station turned aft onto her, and across the room.

Grey Workbench only; nothing here is a finished render, and the outputs are never committed.
"""
import math
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "castle-figure.blend"
SW_DIR = HERE.parent / "second-wind-geometry"
SW_BLEND = SW_DIR / "states" / "patched" / "second-wind-geometry.blend"
BRIDGE_VIEWS = "02-port-station,09-castle-from-port-station,10-castle-across-the-room,03-room-from-helm"

W, H = 1920, 1080
PANELS = 4
# (sheet, camera distance m, aim height m, lens mm, yaws: degrees round her from the front)
SHEETS = [
    ("01-turnaround-full", 6.4, 0.86, 80, (0, 90, 180, 270)),
    ("02-turnaround-head", 1.75, 1.5, 60, (20, 65, 160, 300)),
]


def launch(out):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    if not BLEND.exists():
        sys.exit("castle-figure.blend is not built: python build_castle.py")
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.check_call([BLENDER, "--background", "--factory-startup", str(BLEND),
                               "--python", str(Path(__file__).resolve()), "--", "--panels", tmp])
        from PIL import Image, ImageDraw
        for name, *_ , yaws in SHEETS:
            sheet = Image.new("RGB", (W, H))
            draw = ImageDraw.Draw(sheet)
            for k, yaw in enumerate(yaws):
                sheet.paste(Image.open(Path(tmp) / ("%s-%d.png" % (name, k))).convert("RGB"), (k * W // PANELS, 0))
                draw.text((k * W // PANELS + 12, H - 24), "%d deg" % yaw, fill=(170, 170, 160))
            for k in range(1, PANELS):
                draw.line([(k * W // PANELS, 0), (k * W // PANELS, H)], fill=(60, 60, 60), width=2)
            sheet.save(out / (name + ".png"))
            print("SHEET", out / (name + ".png"), flush=True)
    subprocess.check_call([BLENDER, "--background", "--factory-startup", str(SW_BLEND), "--python",
                           str(SW_DIR / "bridge_interior_review.py"), "--", "--out", str(out),
                           "--figures", "--views", BRIDGE_VIEWS])


def blender_panels(panels):
    import bpy
    from mathutils import Vector
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    sh = scene.display.shading
    sh.light, sh.color_type = "STUDIO", "MATERIAL"
    sh.show_shadows, sh.show_cavity = True, True
    sh.shadow_intensity = .5
    sh.background_type, sh.background_color = "VIEWPORT", (.16, .16, .17)
    scene.display.light_direction = (-.4, -.5, .75)
    scene.view_settings.view_transform = "Standard"
    scene.render.resolution_x, scene.render.resolution_y = W // PANELS, H
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    root = bpy.data.objects["Castle figure root"]
    root.location, root.rotation_euler = (0, 0, 0), (0, 0, 0)
    floor = bpy.data.objects.new("Review floor", bpy.data.meshes.new("Review floor"))
    floor.data.from_pydata([(-3, -3, 0), (3, -3, 0), (3, 3, 0), (-3, 3, 0)], [], [(0, 1, 2, 3)])
    scene.collection.objects.link(floor)
    cam = bpy.data.objects.new("Review camera", bpy.data.cameras.new("Review camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.sensor_width, cam.data.sensor_fit = 36.0, "VERTICAL"
    cam.data.clip_start = .02
    for name, dist, z, lens, yaws in SHEETS:
        cam.data.lens = lens
        for k, yaw in enumerate(yaws):
            a = math.radians(yaw)               # she faces +X; 90 is her left side
            target = Vector((0, 0, z))
            cam.location = target + Vector((math.cos(a) * dist, math.sin(a) * dist, .1 * dist))
            cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
            scene.render.filepath = str(Path(panels) / ("%s-%d.png" % (name, k)))
            bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_panels(argv[argv.index("--panels") + 1])
    else:
        launch(Path(argv[argv.index("--out") + 1]))
