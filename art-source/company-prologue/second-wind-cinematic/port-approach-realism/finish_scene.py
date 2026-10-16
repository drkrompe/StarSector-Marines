"""Final restrained trim and fixture detail; reusable by build_scene."""
from pathlib import Path
import bpy
H=Path(__file__).resolve().parent
def finish_scene(s):
 w=bpy.data.objects['LAYERED | inverse source flight'];trim=bpy.data.materials['PORT REALISM | galvanized service hardware'];roof=bpy.data.materials['PORT REALISM | weathered grey standing seam'];trim.node_tree.nodes.clear()
 # Copy the retained grey material graph rather than repainting nearby geometry.
 textured=roof.copy();textured.name='PORT REALISM | worn galvanized fixtures'
 for o in s.objects:
  if o.type=='MESH':
   for slot in o.material_slots:
    if slot.material==trim:slot.material=textured
 dark=bpy.data.materials['PORT REALISM | recesses and rubber']
 def box(name,loc,dim,material):
  x,y,z=loc;a,b,c=(d/2 for d in dim);vs=[(x-a,y-b,z-c),(x+a,y-b,z-c),(x+a,y+b,z-c),(x-a,y+b,z-c),(x-a,y-b,z+c),(x+a,y-b,z+c),(x+a,y+b,z+c),(x-a,y+b,z+c)];mesh=bpy.data.meshes.new(name);mesh.from_pydata(vs,[],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]);mesh.materials.append(material);o=bpy.data.objects.new('PORT DETAIL | '+name,mesh);s.collection.objects.link(o);o.parent=w;o.pass_index=12
 for o in list(s.objects):
  if o.name.startswith('PORT DETAIL | shed') and 'vent dark top' in o.name:
   x,y,z=o.location
   for j in range(-2,3):box(o.name+' louver%d'%j,(x,y+j,z+.3),(7.6,.3,.3),textured)
  if o.name.startswith('Port communications tower'):
   x,y,z=o.location
   for zz in (18,38,58):box(o.name+' service strap%d'%zz,(x,y,zz),(8.2,8.2,.65),dark)
   for dx in (-10,10):box(o.name+' crossarm terminal%d'%dx,(x+dx,y,65),(4,3,3),textured)
 for im in bpy.data.images:
  if im.source=='FILE' and im.filepath:
   absolute=Path(bpy.path.abspath(im.filepath)).resolve()
   if not absolute.exists():
    candidate=H.parent/'managed-land-realism/assets'/absolute.name
    if candidate.exists():absolute=candidate
   im.filepath=bpy.path.relpath(str(absolute),start=str(H))
if __name__=='__main__':
 bpy.ops.wm.open_mainfile(filepath=str(H/'port-approach-realism.blend'));s=bpy.context.scene;finish_scene(s);bpy.ops.wm.save_as_mainfile(filepath=str(H/'port-approach-realism.blend'),compress=True,relative_remap=False)
