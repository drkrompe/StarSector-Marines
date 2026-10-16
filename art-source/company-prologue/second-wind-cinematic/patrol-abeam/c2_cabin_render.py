"""C2's window: the one cabin, rendered from its own camera, plus its light passes.

Background Blender 5.2 with --factory-startup:

    blender --background --factory-startup --python-exit-code 1 --python c2_cabin_render.py [-- --samples N]

Opens `forest-reveal-realism/forest-reveal-realism.blend` read-only (never saved) at frame
144, the frame its cabin painting was projected for, and renders from that painting's own
camera ("02 REVEAL / near glass"), so every surface is seen exactly as painted (no
re-pitch, no unpainted region). C2's camera IS that camera: 26 mm on 36 mm, 19.29 degrees
down. C3 re-pitches the same camera to 9 degrees (c3_cabin_render.py); the entry
transition's whiteout hides that change.

Writes to guides/:
  c2-cabin-paint.png     RGBA; the painted cabin as the scene renders it, glass transparent.
                         Its alpha IS the glass aperture and frame mask for C2 and the entry.
  c2-cabin-sun.png       16-bit linear grey; the cabin in neutral white, lit only by Corvus
                         (c2_core.SUN carried into the ship frame), hard, no world light.
                         Cycles carries the bounce, so the patch's own spill is real.
  c2-cabin-shine.png     16-bit linear; lit only by Jangala's planet-shine from below the glass.
  c2-cabin-window.png    16-bit linear; lit only by a diffuse emitter filling the glass from
                         outside (the entry's heat glow and the cloud's soft light).
  c2-cabin.json          the camera measured (pitch, eye, axes) and how the C2 world maps to it.

compose_c2.py multiplies the painting (as albedo) by these passes to relight the cabin
into the space, glow and cloud states. The painting carries some baked daylight; that is
accepted as material variation, while the light's direction and shadows come from here.
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
CIN = HERE.parent
SOURCE = CIN / "forest-reveal-realism" / "forest-reveal-realism.blend"
GUIDES = HERE / "guides"
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SAMPLES = int(argv[argv.index("--samples") + 1]) if "--samples" in argv else 256
sys.path.insert(0, str(HERE))
import c2_core as C  # noqa: E402

bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
scene = bpy.context.scene
scene.frame_set(144)
for image in bpy.data.images:          # the blend holds absolute paths into the worktree it was built in
    p = image.filepath.replace("\\", "/")
    marker = "/second-wind-cinematic/"
    if marker in p:
        local = CIN / p.split(marker, 1)[1]
        if local.exists():
            image.filepath = str(local)
            image.reload()
for m in scene.timeline_markers:
    m.camera = None
root = bpy.data.objects["SECOND WIND | flight root"]
ship = set(root.children_recursive)
for ob in scene.objects:
    if ob.type == "MESH" and ob not in ship:
        ob.hide_render = True
cam = bpy.data.objects["02 REVEAL / near glass"]
scene.camera = cam
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = C.W, C.H, 100
scene.render.film_transparent = True
GUIDES.mkdir(exist_ok=True)

# The C2 world in this scene: +X is the camera's horizontal right (screen right, toward the
# bow), +Y its horizontal forward (out through the glass), +Z up.
mw = cam.matrix_world.to_3x3().normalized()
fwd = -mw.col[2]
pitch = math.degrees(math.asin(-fwd.z))
fy = Vector((fwd.x, fwd.y, 0)).normalized()
fx = Vector((fy.y, -fy.x, 0))
fz = Vector((0, 0, 1))


def to_scene(v):
    return (v[0] * fx + v[1] * fy + v[2] * fz).normalized()


info = {
    "source": "forest-reveal-realism/forest-reveal-realism.blend (read-only), frame 144",
    "camera": cam.name, "pitch_down_deg": round(pitch, 3), "lens_mm": cam.data.lens,
    "sensor_mm": cam.data.sensor_width, "eye": [round(c, 4) for c in cam.matrix_world.translation],
    "c2_axes_in_scene": {"x_right": list(fx), "y_out": list(fy), "z_up": list(fz)},
    "sun_c2": list(C.SUN), "sun_scene": list(to_scene(C.SUN)),
    "note": "c2_core.CABIN_PITCH_DEG must equal pitch_down_deg (compose_c2.py checks).",
}

# ---- pass 1: the painting, as the scene renders it.
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.render.image_settings.color_depth = "8"
scene.render.filepath = str(GUIDES / "c2-cabin-paint.png")
bpy.ops.render.render(write_still=True)

# ---- light passes: neutral white cabin, Cycles, no world light, one source each.
white = bpy.data.materials.new("C2 | neutral cabin")
white.use_nodes = True
bsdf = white.node_tree.nodes["Principled BSDF"]
bsdf.inputs["Base Color"].default_value = (0.8, 0.8, 0.8, 1)
bsdf.inputs["Roughness"].default_value = 0.85
for ob in ship:
    if ob.type == "MESH":
        ob.data = ob.data.copy()
        ob.data.materials.clear()
        ob.data.materials.append(white)
        for face in ob.data.polygons:
            face.material_index = 0
for ob in list(scene.objects):
    if ob.type == "LIGHT":
        ob.hide_render = True
scene.world = bpy.data.worlds.new("C2 | space")
scene.world.use_nodes = True
scene.world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.0

scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"            # the GPU is shared; these are three stills
scene.cycles.samples = SAMPLES
scene.cycles.use_denoising = True
scene.cycles.max_bounces = 6
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"
scene.view_settings.exposure = 0
scene.view_settings.gamma = 1
scene.render.image_settings.color_depth = "16"
scene.render.image_settings.color_mode = "BW"          # 16-bit grey: the light is white, and PIL reads I;16
scene.render.image_settings.color_management = "OVERRIDE"
scene.render.image_settings.view_settings.view_transform = "Raw"


def sun(name, toward_c2, energy, angle):
    lamp = bpy.data.objects.new(name, bpy.data.lights.new(name, "SUN"))
    lamp.data.energy, lamp.data.angle = energy, math.radians(angle)
    lamp.rotation_euler = to_scene(toward_c2).to_track_quat("Z", "Y").to_euler()
    scene.collection.objects.link(lamp)
    return lamp


eye = cam.matrix_world.translation
# A diffuse emitter filling the glass from outside: a plane facing in, 3 m beyond the eye
# (the glass is about 1.2 m out), wide enough to cover the whole opening as seen from the cabin.
bpy.ops.mesh.primitive_plane_add(size=1.0)
pane = bpy.context.active_object
pane.name = "C2 | window emitter"
pane.scale = (9.0, 9.0, 1)
pane.location = eye + fy * 4.5 - fz * 1.0
pane.rotation_euler = (-fy).to_track_quat("Z", "Y").to_euler()
glow = bpy.data.materials.new("C2 | window emitter")
glow.use_nodes = True
nt = glow.node_tree
nt.nodes.clear()
em = nt.nodes.new("ShaderNodeEmission")
em.inputs["Strength"].default_value = 1.0
out = nt.nodes.new("ShaderNodeOutputMaterial")
nt.links.new(em.outputs[0], out.inputs[0])
pane.data.materials.append(glow)
pane.visible_camera = False

lamps = {
    "sun": sun("C2 | Corvus", C.SUN, 1.0, 0.5),
    "shine": sun("C2 | Jangala shine", (0.0, 0.55, -0.83), 1.0, 40.0),
}
for name in ("sun", "shine", "window"):
    for key, lamp in lamps.items():
        lamp.hide_render = key != name
    pane.hide_render = name != "window"
    scene.render.filepath = str(GUIDES / ("c2-cabin-%s.png" % name))
    bpy.ops.render.render(write_still=True)

(GUIDES / "c2-cabin.json").write_text(json.dumps(info, indent=2), encoding="utf-8")
print("C2 cabin", json.dumps(info))
