"""C3 cabin geometry guides: the retained cabin, seen from C3's level-looking camera.

Background Blender 5.2 with --factory-startup:

    blender --background --factory-startup --python c3_cabin_guide.py -- [--pitch -9]

Opens the retained `parallax-window/jangala-parallax-atlas-lift.blend` read-only (it is
never saved) and renders, at 1920x1080 with neutral grey materials:

- guides/c3-cabin-geometry-p<N>.png  the cabin from the original eye point and 26 mm
  lens, pitched to <N> degrees (the reveal camera is -19.29);
- guides/c3-cabin-depth-p<N>.png     16-bit camera distance, 0..4 blend units mapped to 0..65535,
  for splitting the cabin into a near ledge card and a far rim card;
- guides/c3-cabin-geometry-p<N>.json the camera actually used.

Only the camera's pitch changes: same eye position, same heading, same lens, same
geometry. That is the C3 proposal in c3-layout.md ("Camera"), made concrete.
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / "parallax-window" / "jangala-parallax-atlas-lift.blend"
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
PITCH = float(argv[argv.index("--pitch") + 1]) if "--pitch" in argv else -9.0
TAG = "p%02d" % round(abs(PITCH))

bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
scene = bpy.context.scene
scene.frame_set(144)
for marker in scene.timeline_markers:
    marker.camera = None

root = bpy.data.objects["SECOND WIND | flight root"]
ship = set(root.children_recursive)
reveal = bpy.data.objects["02 REVEAL / near glass"]

# Neutral guide material (as export_cabin_guide.py) and a distance material for the depth pass.
grey = bpy.data.materials.new("GUIDE | neutral cabin")
grey.use_nodes = True
bsdf = grey.node_tree.nodes.get("Principled BSDF")
bsdf.inputs["Base Color"].default_value = (.32, .38, .40, 1)
bsdf.inputs["Roughness"].default_value = .8

dist = bpy.data.materials.new("GUIDE | camera distance")
dist.use_nodes = True
nt = dist.node_tree
for n in list(nt.nodes):
    nt.nodes.remove(n)
cam_data = nt.nodes.new("ShaderNodeCameraData")
scale = nt.nodes.new("ShaderNodeMath")
scale.operation = "MULTIPLY"
scale.inputs[1].default_value = 1.0 / 4.0          # 0..4 m -> 0..1
emit = nt.nodes.new("ShaderNodeEmission")
out = nt.nodes.new("ShaderNodeOutputMaterial")
nt.links.new(cam_data.outputs["View Distance"], scale.inputs[0])
nt.links.new(scale.outputs[0], emit.inputs["Color"])
nt.links.new(emit.outputs[0], out.inputs["Surface"])

meshes = []
for ob in scene.objects:
    if ob.type != "MESH":
        continue
    if ob not in ship:
        ob.hide_render = True
        continue
    ob.data = ob.data.copy()
    meshes.append(ob)


def assign(mat):
    for ob in meshes:
        ob.data.materials.clear()
        ob.data.materials.append(mat)
        for face in ob.data.polygons:
            face.material_index = 0


# C3 camera: the reveal camera, re-pitched about its own horizontal axis (no roll, no yaw).
m = reveal.matrix_world.copy()
loc, rot, scl = m.decompose()
fwd = -(rot.to_matrix().col[2])
pitch_now = math.degrees(math.asin(fwd.z))
right = rot.to_matrix().col[0].normalized()
delta = math.radians(PITCH - pitch_now)
new_rot = (Matrix.Rotation(delta, 3, right) @ rot.to_matrix()).to_4x4()
cam = bpy.data.objects.new("C3 / level glass", reveal.data.copy())
scene.collection.objects.link(cam)
cam.matrix_world = Matrix.Translation(loc) @ new_rot
scene.camera = cam

scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, 100
guides = HERE / "guides"
guides.mkdir(exist_ok=True)

assign(grey)
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_depth = "8"
scene.render.filepath = str(guides / ("c3-cabin-geometry-%s.png" % TAG))
bpy.ops.render.render(write_still=True)

assign(dist)
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"
scene.view_settings.exposure = 0
scene.view_settings.gamma = 1
scene.display_settings.display_device = "sRGB"
scene.render.image_settings.color_depth = "16"
scene.render.image_settings.color_management = "OVERRIDE"
scene.render.image_settings.view_settings.view_transform = "Raw"
scene.render.filter_size = 0.0
scene.world = scene.world or bpy.data.worlds.new("w")
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes.get("Background")
if bg:
    bg.inputs["Color"].default_value = (1, 1, 1, 1)   # glass/beyond reads as 4 m+ (max)
    bg.inputs["Strength"].default_value = 1
scene.render.filepath = str(guides / ("c3-cabin-depth-%s.png" % TAG))
bpy.ops.render.render(write_still=True)

new_fwd = -(cam.matrix_world.to_3x3().col[2].normalized())
info = {
    "source": "../parallax-window/jangala-parallax-atlas-lift.blend (opened read-only, never saved)",
    "source_camera": "02 REVEAL / near glass",
    "source_pitch_deg": round(pitch_now, 3),
    "c3_pitch_deg": round(math.degrees(math.asin(new_fwd.z)), 3),
    "lens_mm": cam.data.lens,
    "sensor_mm": cam.data.sensor_width,
    "focal_px_at_1920": round(1920 * cam.data.lens / cam.data.sensor_width, 1),
    "horizon_y_px": round(540 - 1920 * cam.data.lens / cam.data.sensor_width * math.tan(math.radians(-PITCH)), 1),
    "eye_location_m": [round(v, 4) for v in loc],
    "frame": 144,
    "depth_encoding": "16-bit RGB PNG, value/65535 * 4 = camera distance in blend units; the ship root is scaled 1.5, so real metres = that / 1.5 (glass rim ~1.2 m, ledge front ~1.1 m); background = 4 (beyond the glass)",
}
(guides / ("c3-cabin-geometry-%s.json" % TAG)).write_text(json.dumps(info, indent=2), encoding="utf-8")
print("C3 guide", json.dumps(info))
