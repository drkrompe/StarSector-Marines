"""Render actual half-frame samples from the original 12fps camera animation."""
from pathlib import Path
import bpy

HERE = Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(HERE / "jangala-parallax-window.blend"))
scene = bpy.context.scene
camera = bpy.data.objects["02 REVEAL / near glass"]
output = HERE / "build/frames"
output.mkdir(parents=True, exist_ok=True)
# Render markers are deliberately unbound in the derived scene. Original source
# cameras and keyframes remain intact; this test uses one named shot only.
assert all(marker.camera is None for marker in scene.timeline_markers)
assert scene.render.fps == 12, "Do not retime the source animation to output FPS"
assert (scene.render.resolution_x, scene.render.resolution_y) == (1920,1080)
assert scene.render.resolution_percentage == 100
scene.render.image_settings.file_format = 'PNG'
for index in range(96):
    source_frame = 120 + index * .5
    integer = int(source_frame)
    scene.frame_set(integer, subframe=source_frame-integer)
    scene.camera = camera
    scene.render.filepath = str(output / f"frame_{index:04d}.png")
    bpy.ops.render.render(write_still=True)
    print(f"PARALLAX_FRAME {index+1}/96 source={source_frame}", flush=True)
