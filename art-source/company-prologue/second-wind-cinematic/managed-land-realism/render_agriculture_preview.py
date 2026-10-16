"""Five actual-camera samples without touching the retained full frame sequence."""
from pathlib import Path
import bpy

HERE=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(HERE/'managed-land-realism.blend'))
scene=bpy.context.scene
scene.camera=bpy.data.objects['03 LAND / side window']
for marker in scene.timeline_markers:
    marker.camera=None
output=HERE/'build/agriculture-preview'
output.mkdir(parents=True,exist_ok=True)
assert (scene.render.resolution_x,scene.render.resolution_y,scene.render.resolution_percentage)==(1920,1080,100)
for index in (0,24,48,72,95):
    time=240+index*.5
    scene.frame_set(int(time),subframe=time%1)
    scene.render.filepath=str(output/f'frame_{index+1:04d}.png')
    bpy.ops.render.render(write_still=True)
    print('AGRICULTURE_PREVIEW',index+1,flush=True)
