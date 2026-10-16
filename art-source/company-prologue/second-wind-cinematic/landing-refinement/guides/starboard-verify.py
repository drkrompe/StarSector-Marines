import bpy,json,sys
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
p=Path(r'C:/Users/Dillon/IdeaProjects/starsectormarines/.claude/worktrees/jangala-greybox-animatic/art-source/company-prologue/second-wind-cinematic/landing-refinement')
sys.path.insert(0,str(p));from ship_view import configure_ship_view
s=bpy.context.scene;c=configure_ship_view(s);r=bpy.data.objects['SECOND WIND | flight root']
out={}
for f in [580,600,624,648,696,744,774]:
 s.frame_set(f);bpy.context.view_layer.update()
 points=[world_to_camera_view(s,c,ob.matrix_world@Vector(v)) for ob in r.children_recursive if ob.type=='MESH' and not ob.hide_render and not ob.name.startswith('LIFT VECTOR |') for v in ob.bound_box]
 out[f]={'root':list(r.matrix_world.translation),'hull_pixel_bounds':[min(v.x for v in points)*1920,(1-max(v.y for v in points))*1080,max(v.x for v in points)*1920,(1-min(v.y for v in points))*1080]}
(p/'guides/starboard-framing.json').write_text(json.dumps(out,indent=2)+'\n', encoding='utf-8', newline='\n')
print(json.dumps(out))
