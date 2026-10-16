"""Read-only saved-scene traffic visibility proof. Run with background Blender.

    blender --background --factory-startup --python verify_traffic_visibility.py

Writes review/traffic-visibility.json for the exact saved blend SHA256. Re-run
after rebuilding; this helper neither rebuilds nor saves any Blender scene.
"""
import hashlib
import json
from pathlib import Path

import bpy
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

h = Path(__file__).resolve().parent
scene_path = h / 'port-refinement.blend'
scene_hash = hashlib.sha256(scene_path.read_bytes()).hexdigest()
bpy.ops.wm.open_mainfile(filepath=str(scene_path))
s = bpy.context.scene
w = bpy.data.objects['LAYERED | inverse source flight']
c = bpy.data.objects['04 PORT / side window']
r = dict(vehicles=[dict(object='PORT TRAFFIC | articulated freight tug',length_m=20),
                   dict(object='PORT TRAFFIC | maintenance carrier',length_m=9),
                   dict(object='PORT TRAFFIC | port utility van',length_m=6.8)])
rows=[]
for sample in range(96):
 f=400+sample*.5;s.frame_set(int(f),subframe=f%1);dg=bpy.context.evaluated_depsgraph_get();vehicles=[]
 for row in r['vehicles']:
  root=bpy.data.objects[row['object']];children=list(root.children_recursive);own={o.name for o in children};points=[child.matrix_world@v.co for child in children if child.type=='MESH' for v in child.data.vertices];screen=[world_to_camera_view(s,c,p) for p in points];px=(max(p.x for p in screen)-min(p.x for p in screen))*1920;py=(max(p.y for p in screen)-min(p.y for p in screen))*1080
  clear=0;hits=[]
  for x,z in ((0,2.0),(row['length_m']*.3,2.4),(-row['length_m']*.3,2.2),(0,3.0)):
   target=root.matrix_world@Vector((x,0,z));p=world_to_camera_view(s,c,target);delta=target-c.matrix_world.translation;direction=delta.normalized();origin=c.matrix_world.translation.copy();visible=False
   if .08<p.x<.92 and .15<p.y<.87 and p.z>0:
    for attempt in range(100):
     hit,loc,norm,idx,obj,mat=s.ray_cast(dg,origin,direction,distance=(target-origin).length+.01)
     if not hit or obj.name in own:visible=True;break
     if obj.hide_render or 'SEALED GLAZING' in obj.name:origin=loc+direction*.01;continue
     hits.append(obj.name);break
   clear+=visible
  vehicles.append(dict(name=root.name,visible_probes=clear,width_px=px,height_px=py,occluders=hits))
 rows.append(dict(sample=sample,vehicles=vehicles,readable_count=sum(v['visible_probes']>=2 and v['width_px']>=8 for v in vehicles)))
out=dict(min_readable=min(x['readable_count'] for x in rows),two_readable_samples=sum(x['readable_count']>=2 for x in rows),samples=rows)
out.update(scene=str(scene_path),scene_sha256=scene_hash,source_fps=12,output_fps=24,source_frame_start=400,sample_step=.5,method='Four body/cab probes per vehicle against evaluated scene mesh; skip render-hidden meshes and explicit transparent cabin glazing. Readable threshold: two clear probes and projected width >=8 px at 1920x1080. Geometry evidence; visual review separate.')
review=h/'review';review.mkdir(exist_ok=True)
(review/'traffic-visibility.json').write_text(json.dumps(out,indent=2)+'\n',encoding='utf-8')
print('VISIBILITY',out['min_readable'],out['two_readable_samples'],scene_hash,flush=True)
