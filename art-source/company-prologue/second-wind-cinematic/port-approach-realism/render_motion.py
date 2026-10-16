import bpy,sys,json,hashlib
from pathlib import Path
H=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(H/'port-approach-realism.blend'))
s=bpy.context.scene
for marker in s.timeline_markers:marker.camera=None
s.camera=bpy.data.objects['04 PORT / side window'];s.eevee.taa_render_samples=64
B=H/'build/frames';B.mkdir(exist_ok=True,parents=True)
preview='--preview' in sys.argv
for i in ([0,47,95] if preview else range(96)):
 f=400+i*.5;s.frame_set(int(f),subframe=f%1);s.render.filepath=str(B/('frame-%04d.png'%i));bpy.ops.render.render(write_still=True)
print('PORT_FRAMES_RENDERED',flush=True)
