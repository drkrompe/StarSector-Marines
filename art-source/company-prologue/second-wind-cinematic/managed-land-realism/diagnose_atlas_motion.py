"""Read saved shot03 and measure Atlas at all96 delivered times; no render/save."""
from pathlib import Path
import json
import hashlib
import bpy
from bpy_extras.object_utils import world_to_camera_view
HERE=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(HERE/'managed-land-realism.blend'))
scene=bpy.context.scene
camera=bpy.data.objects['03 LAND / side window']
atlas=bpy.data.objects['REALISM | distant Atlas card']
path=[]
for i in range(96):
 t=240+i*.5;scene.frame_set(int(t),subframe=t%1);bpy.context.view_layer.update()
 p=world_to_camera_view(scene,camera,atlas.matrix_world.translation)
 path.append({'output_frame':i+1,'source_frame':t,'centre_px':[p.x*1920,(1-p.y)*1080], 'source_position_m':list(atlas.location)})
elapsed=(path[-1]['source_frame']-path[0]['source_frame'])/12
velocity=[(path[-1]['source_position_m'][i]-path[0]['source_position_m'][i])/elapsed for i in range(3)]
report={'scene_sha256':hashlib.sha256((HERE/'managed-land-realism.blend').read_bytes()).hexdigest(), 'scope':'Numeric projection of actual saved scene; not playback review', 'source_velocity_m_s':velocity, 'velocity_is_provisional_compositing_choice':True, 'mapping':'source240+0.5*i; source12fps, delivery24fps, duration4s', 'previous19m_s_screen_delta_px':[-49.8904,-6.7205], 'screen_delta_px':[path[-1]['centre_px'][j]-path[0]['centre_px'][j] for j in (0,1)], 'monotonic_right':all(path[i+1]['centre_px'][0]>path[i]['centre_px'][0] for i in range(95)), 'path':path}
(HERE/'review/atlas-motion-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print('CURRENT_ATLAS_MOTION',json.dumps({k:v for k,v in report.items() if k!='path'}))
