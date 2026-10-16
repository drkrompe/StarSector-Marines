"""C3 cabin card: the approved painted cabin, re-viewed from C3's level-looking camera.

Background Blender 5.2 with --factory-startup:

    blender --background --factory-startup --python c3_cabin_render.py -- [--pitch -9]

Opens `forest-reveal-realism/forest-reveal-realism.blend` read-only (never saved). Its
cabin carries `cabin-realism-v1.png` through the ship-local projector, and C3's camera
sits at that projector's own eye point. A camera that only *rotates* about the
projector's centre sees every projected surface exactly as painted, so this re-pitch is
a valid re-view, not a reskin. What the projector never saw (above about +4 degrees, the
window head and the wall over it) has no paint; this script also writes the mask of
that region so a single Codex inpaint can complete it (c3-layout.md, "Cabin").

Writes guides/c3-cabin-projected-p<N>.png (RGBA, the glass transparent) and
guides/c3-cabin-unpainted-p<N>.png (white where the projector had no coverage).
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector
from bpy_extras.object_utils import world_to_camera_view

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / "forest-reveal-realism" / "forest-reveal-realism.blend"
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
PITCH = float(argv[argv.index("--pitch") + 1]) if "--pitch" in argv else -9.0
TAG = "p%02d" % round(abs(PITCH))

bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
scene = bpy.context.scene
scene.frame_set(144)
# The saved blend holds absolute image paths into the worktree it was built in; point
# them at this checkout's copy of the same files (in memory only).
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
fwd = -(rot.to_matrix().col[2])
right = rot.to_matrix().col[0].normalized()
delta = math.radians(PITCH - math.degrees(math.asin(fwd.z)))
cam = bpy.data.objects.new("C3 / level glass", reveal.data.copy())
scene.collection.objects.link(cam)
cam.matrix_world = Matrix.Translation(loc) @ (Matrix.Rotation(delta, 3, right) @ rot.to_matrix()).to_4x4()
scene.camera = cam

scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.render.filepath = str(HERE / "guides" / ("c3-cabin-projected-%s.png" % TAG))
bpy.ops.render.render(write_still=True)

# Projector coverage: for each output pixel's view ray, is the direction inside the
# projector's frustum? Both cameras share an eye point, so direction is enough.
import numpy as np  # noqa: E402  (Blender bundles numpy)

W, H = 1920, 1080
f = W * cam.data.lens / cam.data.sensor_width
xs = (np.arange(W) + 0.5 - W / 2) / f
ys = -(np.arange(H) + 0.5 - H / 2) / f
cm = np.array(cam.matrix_world.to_3x3().normalized())
pm = np.array(projector.matrix_world.to_3x3().normalized())
fp = W * projector.data.lens / projector.data.sensor_width
X, Y = np.meshgrid(xs, ys)
dirs = np.stack([X, Y, -np.ones_like(X)], -1) @ cm.T          # world directions
local = dirs @ pm                                              # into projector space
u = local[..., 0] / -local[..., 2] * fp + W / 2
v = -local[..., 1] / -local[..., 2] * fp + H / 2
outside = (local[..., 2] >= 0) | (u < 0) | (u > W) | (v < 0) | (v > H)
mask = np.where(outside, 1.0, 0.0)
img = bpy.data.images.new("unpainted", W, H, alpha=False)
px = np.dstack([mask, mask, mask, np.ones_like(mask)])[::-1].astype(np.float32).ravel()
img.pixels.foreach_set(px)
img.filepath_raw = str(HERE / "guides" / ("c3-cabin-unpainted-%s.png" % TAG))
img.file_format = "PNG"
img.save()
print("C3 cabin render", TAG, "unpainted fraction %.3f" % mask.mean())
