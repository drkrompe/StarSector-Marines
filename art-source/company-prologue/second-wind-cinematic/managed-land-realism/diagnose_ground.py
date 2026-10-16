import bpy,json,argparse,sys
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from pathlib import Path
here=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--scene',default='managed-land-realism.blend')
parser.add_argument('--report',default='review/ground-overlap-current.json')
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(here/args.scene))
s=bpy.context.scene;s.frame_set(263,subframe=.75)
w=bpy.data.objects['LAYERED | inverse source flight'];land=bpy.data.objects['Jangala | continuous landform']
m=w.matrix_world.inverted()@land.matrix_world
tree=BVHTree.FromPolygons([m@v.co for v in land.data.vertices],[tuple(p.vertices) for p in land.data.polygons])
r=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render or o.pass_index!=13:continue
 mat=w.matrix_world.inverted()@o.matrix_world;ds=[]
 for p in o.data.polygons:
  if p.normal.z<.5:continue
  vs=[mat@o.data.vertices[i].co for i in p.vertices]
  for i in range(21):
   for j in range(21-i):
    a=i/20;b=j/20;pt=vs[0]*(1-a-b)+vs[1]*a+vs[2]*b
    hit,*_=tree.ray_cast(Vector((pt.x,pt.y,5000)),Vector((0,0,-1)),10000)
    if hit is not None:ds.append(pt.z-hit.z)
 if ds:r.append(dict(object=o.name,min=min(ds),max=max(ds),negative=sum(d<0 for d in ds),near=sum(abs(d)<.15 for d in ds),samples=len(ds)))
(here/args.report).write_text(json.dumps(r,indent=2))
print(json.dumps(r))
