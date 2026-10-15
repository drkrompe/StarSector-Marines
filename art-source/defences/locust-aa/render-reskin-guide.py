"""Render the approved Blender assembly as one centered overhead edit target.

Run in an isolated background Blender process. The approved .blend is read only.
"""
from pathlib import Path
import bpy

HERE = Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(HERE / 'proposals/microphone-head/proposal.blend'))
scene = bpy.context.scene
root = bpy.data.objects['Overhead presentation root']
for obj in scene.objects:
    if obj.type != 'MESH':
        continue
    ancestor = obj
    while ancestor.parent:
        ancestor = ancestor.parent
    obj.hide_render = ancestor != root
root.location = (0, 0, 0)
scene.camera.location = (0, 0, 12)
scene.camera.rotation_euler = (0, 0, 0)
scene.camera.data.ortho_scale = 3.0
scene.render.resolution_x = 1024
scene.render.resolution_y = 1024
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.filepath = str(HERE / 'locust-reskin-guide.png')
bpy.ops.render.render(write_still=True)
print({'guide': scene.render.filepath, 'yaw_pivot_px': [512,512], 'pitch_degrees': 20})
