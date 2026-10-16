from pathlib import Path
import bpy,sys
OUT=Path(__file__).resolve().parent
sys.path.append(str(OUT.parent/'landing-refinement'))
from render_config import configure_render_device
bpy.ops.wm.open_mainfile(filepath=str(OUT/'space-approach.blend'))
s=bpy.context.scene;print(configure_render_device(s),flush=True)
for label,frame in [('start',1),('mid',48),('end',96)]:
    s.frame_set(frame);s.render.filepath=str(OUT/'review'/f'{label}.png');bpy.ops.render.render(write_still=True)
