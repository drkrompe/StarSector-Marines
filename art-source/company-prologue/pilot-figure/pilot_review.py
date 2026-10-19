"""Review sheets of the pilot figure, 1920x1080 at 100%.

    python pilot_review.py --out DIR [--blend PATH] [--engine workbench|eevee] [--prefix NAME-] [--no-bridge]
                           [--video]

Writes, for each of his two poses (relaxed at frame 0, flying at 11 s), a turnaround of
pilot-figure.blend (build_pilot.py builds it) seated at a grey stand-in of the helm (the seat's
pan, back and armrests, the forward console's casing and sloped face, at their
sw_bridge_interior sizes): four panels on one sheet, from his front left, his left, behind and
behind to his right, and a close sheet of his head and shoulders from the same four sides; then
one sheet of the move from his left at six moments of C2. Then, unless --no-bridge, it renders
both poses at the helm of Second Wind's bridge through the bridge's own review script
(bridge-relaxed/, bridge-flying/): the helm close-up and C2's two camera stations turned onto him.

--video also renders the move as an mp4 (24 fps, from his left, C2 seconds VIDEO_SPAN) with
the time burnt in; it needs ffmpeg. Workbench by default; --engine eevee lights him with castle_review.light_eevee. Nothing here
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
    ("turnaround", 3.4, 0.62, 60, (35, 90, 180, 230), 1.1),
    ("head-and-shoulders", 1.3, 0.95, 60, (35, 90, 160, 210), 1.15),
]
POSES = (("relaxed", 0), ("flying", 11 * 24))           # (name, frame): at rest, and at 11 s
# The move, from his left at the C2 seconds named: at rest with the yoke stowed, taking it,
# drawing it out, flying it pushed, letting go, and sat back with it left out.
MOTION = (3.3, 0.62, 45, 60, 1.0, (8.3, 8.8, 9.15, 9.45, 12.0, 14.7))
VIDEO_SPAN = (7.5, 15.5)


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
                               "--engine", engine, *(["--video"] if "--video" in argv else [])])
        from PIL import Image, ImageDraw
        def sheet(panels, labels, path):
            img = Image.new("RGB", (W, H))
            draw = ImageDraw.Draw(img)
            pw = W // len(panels)
            for k, (panel, label) in enumerate(zip(panels, labels)):
                img.paste(Image.open(Path(tmp) / panel).convert("RGB"), (k * pw, 0))
                draw.text((k * pw + 12, H - 24), label, fill=(170, 170, 160))
            for k in range(1, len(panels)):
                draw.line([(k * pw, 0), (k * pw, H)], fill=(60, 60, 60), width=2)
            img.save(path)
            print("SHEET", path.name, flush=True)

        n = 1
        for pose, _ in POSES:
            for name, _, _, _, yaws, _ in SHEETS:
                sheet(["%s-%s-%d.png" % (pose, name, k) for k in range(len(yaws))],
                      ["%s, %d deg" % (pose, y) for y in yaws],
                      out / ("%s%02d-%s-%s-%s.png" % (prefix, n, pose, name, engine)))
                n += 1
        if "--video" in argv:
            frames = sorted(Path(tmp).glob("video-*.png"))
            for f in frames:
                img = Image.open(f).convert("RGB")
                ImageDraw.Draw(img).text((16, H - 30), "C2 %.2f s" % (int(f.stem.split("-")[1]) / 24),
                                         fill=(200, 200, 190))
                img.save(f)
            video = out / ("%sdraw-out-and-fly-%s.mp4" % (prefix, engine))
            subprocess.check_call(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", "24",
                                   "-start_number", frames[0].stem.split("-")[1],
                                   "-i", str(Path(tmp) / "video-%04d.png"), "-pix_fmt", "yuv420p",
                                   "-c:v", "libx264", "-crf", "18", str(video)])
            print("VIDEO", video.name, flush=True)
        times = MOTION[5]
        sheet(["motion-%d.png" % k for k in range(len(times))], ["%.1f s" % t for t in times],
              out / ("%s%02d-draw-out-and-fly-%s.png" % (prefix, n, engine)))
    if "--no-bridge" not in argv:
        for pose, frame in POSES:
            sub = out / ("bridge-" + pose)
            subprocess.check_call([BLENDER, "--background", "--factory-startup", str(SW_BLEND), "--python",
                                   str(SW_DIR / "bridge_interior_review.py"), "--", "--out", str(sub),
                                   "--figures", "--views", BRIDGE_VIEWS, "--frame", str(frame)])


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
    for side in (1, -1):        # the armrest pads: tops at the seat's arm height, out at its sides
        block("armrest %d" % side, (-.033, side * .286, SEAT_TOP + .31 * u - .02), (.5 * u, .08 * u, .05 * u))


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
    def shoot(path, yaw, dist, z, lens, eye, width, fwd=0.1):
        a = math.radians(yaw)
        target = Vector((fwd, 0, z))
        cam.data.lens = lens
        scene.render.resolution_x = width
        cam.location = target + Vector((math.cos(a) * dist, math.sin(a) * dist, eye - z))
        cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = str(Path(panels) / path)
        bpy.ops.render.render(write_still=True)

    for pose, frame in POSES:
        scene.frame_set(frame)
        for name, dist, z, lens, yaws, eye in SHEETS:
            for k, yaw in enumerate(yaws):
                shoot("%s-%s-%d.png" % (pose, name, k), yaw, dist, z, lens, eye, W // len(yaws))
    dist, z, lens, yaw, eye, times = MOTION
    for k, t in enumerate(times):
        scene.frame_set(round(t * 24))
        shoot("motion-%d.png" % k, yaw, dist, z, lens, eye, W // len(times), fwd=0.25)
    if "--video" in argv:
        for frame in range(round(VIDEO_SPAN[0] * 24), round(VIDEO_SPAN[1] * 24) + 1):
            scene.frame_set(frame)
            shoot("video-%04d.png" % frame, yaw, dist * 0.9, z + .05, lens * .75, eye, W)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_panels(argv)
    else:
        launch(argv)
