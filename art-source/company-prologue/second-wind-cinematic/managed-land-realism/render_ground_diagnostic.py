import bpy
from pathlib import Path
h=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(h/'managed-land-realism.blend'))
s=bpy.context.scene
s.render.resolution_x=1920;s.render.resolution_y=1080;s.render.resolution_percentage=100
for i in [1,2,3,48,49]:
 t=240+(i-1)*.5;s.frame_set(int(t),subframe=t%1)
 s.render.filepath=str(h/('build/terrain-corrected/frame_%04d.png'%i))
 bpy.ops.render.render(write_still=True)
