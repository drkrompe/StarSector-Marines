"""Smoke-test still for the render queue: one half-size (960x540) frame, a few seconds.

    blender -b --factory-startup --python-exit-code 1 --python tools/render_queue_test_scene.py -- OUT.png [LABEL]
"""
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
out = args[0] if args else "//render-queue-test.png"
label = args[1] if len(args) > 1 else "render queue"

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
bpy.ops.mesh.primitive_monkey_add(location=(0, 0, 0))
bpy.ops.object.shade_smooth()
bpy.ops.mesh.primitive_plane_add(size=20, location=(0, 0, -1))
bpy.ops.object.text_add(location=(-2.6, -1.5, -0.99))
bpy.context.object.data.body = label
bpy.context.object.data.size = 0.6
bpy.ops.object.light_add(type="SUN", location=(4, -4, 6))
bpy.context.object.data.energy = 4
bpy.ops.object.camera_add(location=(0, -6, 2.2), rotation=(1.29, 0, 0))
scene.camera = bpy.context.object

scene.render.engine = "CYCLES"
scene.cycles.samples = 8
scene.cycles.use_denoising = False
scene.render.resolution_x = 1920
scene.render.resolution_y = 1080
scene.render.resolution_percentage = 50
scene.render.image_settings.file_format = "PNG"
scene.render.filepath = out
bpy.ops.render.render(write_still=True)
print("wrote", out)
