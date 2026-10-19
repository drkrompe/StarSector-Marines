"""Review sheets of the pilot figure, 1920x1080 at 100%.

    python pilot_review.py --out DIR [--blend PATH] [--engine workbench|eevee] [--prefix NAME-] [--no-bridge]

Writes a turnaround of pilot-figure.blend (build_pilot.py builds it) seated at a grey stand-in
of the helm (the seat's pan and back, the forward console's casing and sloped face, at their
sw_bridge_interior sizes): four panels on one sheet, from his front left, his left, behind and
behind to his right, and a close sheet of his head and shoulders from the same four sides.
Then, unless --no-bridge, it renders him at the helm of Second Wind's bridge through the
bridge's own review script: the helm close-up and C2's two camera stations turned onto him.

Workbench by default; --engine eevee lights him with castle_review.light_eevee. Nothing here
is a finished render, and the outputs are never committed.
"""
import math
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "pilot-figure.blend"
SW_DIR = HERE.parent / "second-wind-geometry"
SW_BLEND = SW_DIR / "states" / "patched" / "second-wind-geometry.blend"
BRIDGE_VIEWS = "12-helm-seat,15-pilot-from-forward-station,16-pilot-from-port-station"

W, H = 1920, 1080
# (sheet, camera distance m, aim height m, lens mm, yaws round him from the front (90 = his
#  left), camera height m)
SHEETS = [
    ("01-turnaround", 3.4, 0.62, 60, (35, 90, 180, 230), 1.1),
    ("02-head-and-shoulders", 1.3, 0.95, 60, (35, 90, 160, 210), 1.15),
]


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    out = Path(argv[argv.index("--out") + 1]).resolve()
    blend = Path(argv[argv.index("--blend") + 1]) if "--blend" in argv else BLEND
    engine = argv[argv.index("--engine") + 1] if "--engine" in argv else "workbench"
    prefix = argv[argv.index("--prefix") + 1] if "--prefix" in argv else ""
    if not blend.exists():
        sys.exit("%s is not built: python build_pilot.py" % blend.name)
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.check_call([BLENDER, "--background", "--factory-startup", str(blend),
                               "--python", str(Path(__file__).resolve()), "--", "--panels", tmp,
                               "--engine", engine])
        from PIL import Image, ImageDraw
        for name, _, _, _, yaws, _ in SHEETS:
            sheet = Image.new("RGB", (W, H))
            draw = ImageDraw.Draw(sheet)
            pw = W // len(yaws)
            for k, yaw in enumerate(yaws):
                sheet.paste(Image.open(Path(tmp) / ("%s-%d.png" % (name, k))).convert("RGB"), (k * pw, 0))
                draw.text((k * pw + 12, H - 24), "%d deg" % yaw, fill=(170, 170, 160))
            for k in range(1, len(yaws)):
                draw.line([(k * pw, 0), (k * pw, H)], fill=(60, 60, 60), width=2)
            sheet.save(out / ("%s%s-%s.png" % (prefix, name, engine)))
            print("SHEET", name, flush=True)
    if "--no-bridge" not in argv:
        subprocess.check_call([BLENDER, "--background", "--factory-startup", str(SW_BLEND), "--python",
                               str(SW_DIR / "bridge_interior_review.py"), "--", "--out", str(out),
                               "--figures", "--views", BRIDGE_VIEWS])


def stand_in_helm():
    """Grey blocks at the helm's sizes, in the figure's frame (metres, faces +X)."""
    import bpy
    sys.path.insert(0, str(HERE))
    from build_pilot import BACK_FWD, CONSOLE_FWD, CONSOLE_TOP, FACE_EDGE_Z, FACE_TILT, SEAT_TOP
    grey = bpy.data.materials.new("Review helm")
    grey.diffuse_color = (.3, .31, .3, 1)
    u = .65

    def block(name, centre, size, tilt=0.0):
        bpy.ops.mesh.primitive_cube_add(size=1, location=centre)
        ob = bpy.context.object
        ob.name = "Review " + name
        ob.scale = size
        ob.rotation_euler[1] = math.radians(tilt)
        ob.data.materials.append(grey)

    block("seat pan", (0.01, 0, SEAT_TOP - .04), (.74 * u, .68 * u, .08))
    block("seat back", (BACK_FWD - .04, 0, SEAT_TOP + .3), (.08, .66 * u, .6), -8)
    block("console", (CONSOLE_FWD + .16, 0, CONSOLE_TOP / 2), (.32, 1.4, CONSOLE_TOP))
    block("console face", (CONSOLE_FWD + .17, 0, FACE_EDGE_Z + .02), (.3, 1.3, .05), -FACE_TILT)
    block("dais", (0.2, 0, -.02), (1.6, 1.6, .04))


def blender_panels(argv):
    import bpy
    from mathutils import Vector
    sys.path.insert(0, str(HERE.parent / "castle-figure"))
    from castle_review import light_eevee
    panels = argv[argv.index("--panels") + 1]
    engine = argv[argv.index("--engine") + 1]
    scene = bpy.context.scene
    if engine == "eevee":
        light_eevee(scene)
    else:
        scene.render.engine = "BLENDER_WORKBENCH"
        sh = scene.display.shading
        sh.light, sh.color_type = "STUDIO", "MATERIAL"
        sh.show_shadows, sh.show_cavity = True, True
        sh.shadow_intensity = .5
        sh.background_type, sh.background_color = "VIEWPORT", (.16, .16, .17)
        scene.display.light_direction = (-.4, -.5, .75)
        scene.view_settings.view_transform = "Standard"
    scene.render.resolution_y, scene.render.resolution_percentage = H, 100
    scene.render.image_settings.file_format = "PNG"
    stand_in_helm()
    cam = bpy.data.objects.new("Review camera", bpy.data.cameras.new("Review camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.sensor_width, cam.data.sensor_fit = 36.0, "VERTICAL"
    cam.data.clip_start = .02
    for name, dist, z, lens, yaws, eye in SHEETS:
        cam.data.lens = lens
        scene.render.resolution_x = W // len(yaws)
        for k, yaw in enumerate(yaws):
            a = math.radians(yaw)
            target = Vector((0.1, 0, z))
            cam.location = target + Vector((math.cos(a) * dist, math.sin(a) * dist, eye - z))
            cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
            scene.render.filepath = str(Path(panels) / ("%s-%d.png" % (name, k)))
            bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_panels(argv)
    else:
        launch(argv)
