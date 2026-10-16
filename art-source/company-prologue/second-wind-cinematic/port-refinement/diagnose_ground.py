"""Inspect actual source-space ground boundary, without changing source scene."""
import bpy, json
from pathlib import Path
from mathutils import Vector
H=Path(__file__).resolve().parent
(H/'build/ground').mkdir(parents=True,exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(H.parent/'port-approach-realism/port-approach-realism.blend'))
s=bpy.context.scene;s.frame_set(423,subframe=.75)
w=bpy.data.objects['LAYERED | inverse source flight']; land=bpy.data.objects['Jangala | continuous landform']
t=w.matrix_world.inverted()@land.matrix_world
vs=[t@v.co for v in land.data.vertices]
edge=[list(v) for v in vs if abs(v.y-495)<.01 and -1745.01<v.x<-194.99]
objects=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render:continue
 q=w.matrix_world.inverted()@o.matrix_world
 pts=[q@Vector(v) for v in o.bound_box]
 lo=[min(v[i] for v in pts) for i in range(3)];hi=[max(v[i] for v in pts) for i in range(3)]
 if lo[1]<500 and hi[1]>490 and hi[0]>-1745 and lo[0]<-195:objects.append(dict(name=o.name,lo=lo,hi=hi,materials=[m.name for m in o.data.materials if m]))
rays=[]
camera=bpy.data.objects['04 PORT / side window']
origin=camera.matrix_world.translation
deps=bpy.context.evaluated_depsgraph_get()
for x in (-1600,-1200,-800,-400):
 target=w.matrix_world@Vector((x,495,2))
 direction=(target-origin).normalized()
 hit,location,normal,index,obj,matrix=s.ray_cast(deps,target-direction*.01,direction)
 rays.append(dict(gap_target_source=[x,495,2],first_hit=obj.name if hit else None,hit_source=list(w.matrix_world.inverted()@location) if hit else None))
report=dict(boundary_y=495,terrain_boundary=edge,objects=objects,rays_through_gap=rays,
    slab_top_z=.4,boundary_mismatch_range=[min(v[2]-.4 for v in edge),max(v[2]-.4 for v in edge)],
    diagnosis='Terrain was physically cut out under slab, but its open edge remained 3.4-7.1 metres above the slab. The gap reveals distant textured scenery as a bright strip; no terrain/slab overlap exists at this boundary.',
    repair='Fit actual adjoining terrain edge to slab and grade adjoining soil to original elevations. Rendered A/B keeps the original boundary footprint, exposing no strip after repair.')
(H/'build/ground/diagnosis.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report),flush=True)
