"""Review sheets of the Castle figure, 1920x1080 at 100%.

    python castle_review.py --out DIR [--blend PATH] [--engine workbench|eevee]
                            [--sheets 01-turnaround-full,02-turnaround-head,03-c2-distance]
                            [--prefix NAME-] [--no-bridge]

Writes turnarounds of castle-figure.blend (build_castle.py builds it), each view a panel side
by side on one 1920x1080 sheet: front, her left, back and her right; the same four
three-quarter turns closer on the head and shoulders; and one full-frame view from C2's close
shot (the port station turned aft onto her, 1.4 m off, 24 mm). Then, unless --no-bridge, it
renders her on Second Wind's bridge through the bridge's own review script (patched state,
sw_bridge_interior.figures with the MakeHuman Castle): C2's port station, the station turned
aft onto her, and across the room.

Workbench by default (grey, flat colours). --engine eevee lights her with a warm key, a neutral
fill and a rim, and draws the materials' node trees (the groom's Principled Hair BSDF); each
panel's render time is printed. Nothing here is a finished render, and the outputs are never
committed.
"""
import math
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
BLEND = HERE / "castle-figure.blend"
SW_DIR = HERE.parent / "second-wind-geometry"
SW_BLEND = SW_DIR / "states" / "patched" / "second-wind-geometry.blend"
BRIDGE_VIEWS = "02-port-station,09-castle-from-port-station,10-castle-across-the-room,03-room-from-helm"

W, H = 1920, 1080
# (sheet, camera distance m, aim height m, lens mm, yaws: degrees round her from the front,
#  camera height m or None for a tenth of the distance above the aim)
SHEETS = [
    ("01-turnaround-full", 6.4, 0.86, 80, (0, 90, 180, 270), None),
    ("02-turnaround-head", 1.75, 1.5, 60, (20, 65, 160, 300), None),
    # bridge_interior_review's 09-castle-from-port-station, in her frame: 1.4 m off her right
    # front, eye 1.69 m up, aimed at 1.46 m.
    ("03-c2-distance", 1.38, 1.46, 24, (280,), 1.69),
]
DEFAULT_SHEETS = "01-turnaround-full,02-turnaround-head"


def launch(argv):
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    out = Path(argv[argv.index("--out") + 1])
    blend = Path(argv[argv.index("--blend") + 1]) if "--blend" in argv else BLEND
    engine = argv[argv.index("--engine") + 1] if "--engine" in argv else "workbench"
    names = (argv[argv.index("--sheets") + 1] if "--sheets" in argv else DEFAULT_SHEETS).split(",")
    prefix = argv[argv.index("--prefix") + 1] if "--prefix" in argv else ""
    sheets = [s for s in SHEETS if s[0] in names]
    if not blend.exists():
        sys.exit("%s is not built: python build_castle.py" % blend.name)
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.check_call([BLENDER, "--background", "--factory-startup", str(blend),
                               "--python", str(Path(__file__).resolve()), "--", "--panels", tmp,
                               "--engine", engine, "--sheets", ",".join(s[0] for s in sheets)])
        from PIL import Image, ImageDraw
        for name, _, _, _, yaws, _ in sheets:
            sheet = Image.new("RGB", (W, H))
            draw = ImageDraw.Draw(sheet)
            pw = W // len(yaws)
            for k, yaw in enumerate(yaws):
                sheet.paste(Image.open(Path(tmp) / ("%s-%d.png" % (name, k))).convert("RGB"), (k * pw, 0))
                draw.text((k * pw + 12, H - 24), "%d deg" % yaw, fill=(170, 170, 160))
            for k in range(1, len(yaws)):
                draw.line([(k * pw, 0), (k * pw, H)], fill=(60, 60, 60), width=2)
            sheet.save(out / (prefix + name + ".png"))
            print("SHEET", out / (prefix + name + ".png"), flush=True)
    if "--no-bridge" not in argv:
        subprocess.check_call([BLENDER, "--background", "--factory-startup", str(SW_BLEND), "--python",
                               str(SW_DIR / "bridge_interior_review.py"), "--", "--out", str(out),
                               "--figures", "--views", BRIDGE_VIEWS])


def light_eevee(scene):
    """A warm amber key from her front left, a neutral fill and a cool rim, on a dark world."""
    import bpy
    from mathutils import Vector
    scene.render.engine = "BLENDER_EEVEE"
    scene.eevee.taa_render_samples = 64
    scene.view_settings.view_transform = "AgX"
    world = bpy.data.worlds.new("Review world")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (.012, .012, .014, 1)
    scene.world = world
    for name, pos, energy, rgb, size in (("key", (1.6, 1.3, 2.4), 45, (1.0, .78, .55), .8),
                                         ("fill", (1.4, -1.8, 1.6), 12, (.85, .88, .92), 1.5),
                                         ("rim", (-1.6, -.4, 2.2), 35, (.75, .82, 1.0), .5)):
        lamp = bpy.data.lights.new("Review " + name, "AREA")
        lamp.energy, lamp.color, lamp.size = energy, rgb, size
        ob = bpy.data.objects.new("Review " + name, lamp)
        scene.collection.objects.link(ob)
        ob.location = pos
        ob.rotation_euler = (Vector((0, 0, 1.4)) - Vector(pos)).to_track_quat("-Z", "Y").to_euler()


def blender_panels(argv):
    import bpy
    from mathutils import Vector
    panels = argv[argv.index("--panels") + 1]
    engine = argv[argv.index("--engine") + 1]
    names = argv[argv.index("--sheets") + 1].split(",")
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
    scene.render.resolution_y = H
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
    for name, dist, z, lens, yaws, eye in SHEETS:
        if name not in names:
            continue
        cam.data.lens = lens
        # A sheet of panels fits the sensor to their height; a single full frame fits its width,
        # as bridge_interior_review's cameras do.
        cam.data.sensor_fit = "VERTICAL" if len(yaws) > 1 else "HORIZONTAL"
        scene.render.resolution_x = W // len(yaws)
        for k, yaw in enumerate(yaws):
            a = math.radians(yaw)               # she faces +X; 90 is her left side
            target = Vector((0, 0, z))
            height = eye - z if eye is not None else .1 * dist
            cam.location = target + Vector((math.cos(a) * dist, math.sin(a) * dist, height))
            cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
            scene.render.filepath = str(Path(panels) / ("%s-%d.png" % (name, k)))
            t = time.perf_counter()
            bpy.ops.render.render(write_still=True)
            print("PANEL %s-%d %s %.1fs" % (name, k, engine, time.perf_counter() - t), flush=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_panels(argv)
    else:
        launch(argv)
