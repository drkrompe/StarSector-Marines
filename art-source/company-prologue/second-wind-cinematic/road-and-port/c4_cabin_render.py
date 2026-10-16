"""C4 cabin card: the orange-and-cream painted cabin, re-viewed at C4's camera (c4-layout.md).

Background Blender 5.2 with --factory-startup:

    blender --background --factory-startup --python-exit-code 1 --python c4_cabin_render.py -- [--lens 26] [--horizon 180]

Opens `forest-reveal-realism/forest-reveal-realism.blend` read-only (never saved), exactly
as `first-open-sky/c3_cabin_render.py` does. C4 looks level with the lens shifted down
(verticals stay vertical), so the camera here is the reveal camera rotated to level about
its own eye point, which is the cabin projector's eye point, plus a vertical lens shift
that puts the horizon at --horizon px. Rotation about the projector centre and a shift
(a crop of a wider frustum) both re-view the projected paint exactly.

Writes guides/c4-cabin-projected.png (RGBA, glass transparent), guides/c4-cabin-unpainted.png
(white where the projector never saw) and guides/c4-cabin.json (the camera used).
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / "forest-reveal-realism" / "forest-reveal-realism.blend"
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
LENS = float(argv[argv.index("--lens") + 1]) if "--lens" in argv else None
HORIZON = float(argv[argv.index("--horizon") + 1]) if "--horizon" in argv else 180.0
TAG = argv[argv.index("--tag") + 1] if "--tag" in argv else ""
W, H = 1920, 1080

bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
scene = bpy.context.scene
scene.frame_set(144)
CIN = HERE.parent
for image in bpy.data.images:
    p = image.filepath.replace("\\", "/")
    marker = "/second-wind-cinematic/"
    if marker in p:
        local = CIN / p.split(marker, 1)[1]
        if local.exists():
            image.filepath = str(local)
            image.reload()
for marker in scene.timeline_markers:
    marker.camera = None
root = bpy.data.objects["SECOND WIND | flight root"]
ship = set(root.children_recursive)
for ob in scene.objects:
    if ob.type == "MESH" and ob not in ship:
        ob.hide_render = True

reveal = bpy.data.objects["02 REVEAL / near glass"]
projector = bpy.data.objects["PAINT | ship-local cabin projector"]
loc, rot, _ = reveal.matrix_world.decompose()
R = rot.to_matrix()
fwd = -(R.col[2])
right = R.col[0].normalized()
pitch_now = math.degrees(math.asin(fwd.z))
level = Matrix.Rotation(math.radians(-pitch_now), 3, right) @ R
cam = bpy.data.objects.new("C4 / level glass", reveal.data.copy())
scene.collection.objects.link(cam)
cam.matrix_world = Matrix.Translation(loc) @ level.to_4x4()
if LENS:
    cam.data.lens = LENS
cam.data.sensor_fit = "HORIZONTAL"
f = W * cam.data.lens / cam.data.sensor_width
cam.data.shift_x = 0.0
cam.data.shift_y = -(H / 2 - HORIZON) / W     # negative: look down, horizon above centre
scene.camera = cam

scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = W, H, 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
out = HERE / "guides" / ("c4-cabin-projected%s.png" % TAG)
scene.render.filepath = str(out)
bpy.ops.render.render(write_still=True)

import numpy as np  # noqa: E402  (Blender bundles numpy)

cx, cy = W / 2, HORIZON
xs = (np.arange(W) + 0.5 - cx) / f
ys = -(np.arange(H) + 0.5 - cy) / f
cm = np.array(cam.matrix_world.to_3x3().normalized())
pm = np.array(projector.matrix_world.to_3x3().normalized())
fp = W * projector.data.lens / projector.data.sensor_width
X, Y = np.meshgrid(xs, ys)
dirs = np.stack([X, Y, -np.ones_like(X)], -1) @ cm.T
local = dirs @ pm
u = local[..., 0] / -local[..., 2] * fp + W / 2 - projector.data.shift_x * W
v = -local[..., 1] / -local[..., 2] * fp + H / 2 + projector.data.shift_y * W
outside = (local[..., 2] >= 0) | (u < 0) | (u > W) | (v < 0) | (v > H)
mask = np.where(outside, 1.0, 0.0)
img = bpy.data.images.new("unpainted", W, H, alpha=False)
img.pixels.foreach_set(np.dstack([mask, mask, mask, np.ones_like(mask)])[::-1].astype(np.float32).ravel())
img.filepath_raw = str(HERE / "guides" / ("c4-cabin-unpainted%s.png" % TAG))
img.file_format = "PNG"
img.save()

info = dict(source="../forest-reveal-realism/forest-reveal-realism.blend (opened read-only, never saved)",
            source_camera="02 REVEAL / near glass", source_pitch_deg=round(pitch_now, 3), pitch_deg=0.0,
            lens_mm=cam.data.lens, sensor_mm=cam.data.sensor_width, focal_px_at_1920=round(f, 1),
            shift_y=round(cam.data.shift_y, 5), horizon_y_px=HORIZON, eye_location=[round(v, 4) for v in loc],
            projector_shift=[projector.data.shift_x, projector.data.shift_y], unpainted_fraction=round(float(mask.mean()), 4))
(HERE / "guides" / ("c4-cabin%s.json" % TAG)).write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
print("C4 cabin", json.dumps(info))
