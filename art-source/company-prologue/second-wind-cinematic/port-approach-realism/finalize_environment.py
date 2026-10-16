"""Lowturf owns nearbank RGB; geometryclip keeps woodland behind river."""
from pathlib import Path
import bpy,bmesh
from mathutils import Vector
H=Path(__file__).resolve().parent
def finalize_environment(s):
 w=bpy.data.objects['LAYERED | inverse source flight'];land=bpy.data.objects['Jangala | continuous landform'];m=land.data.materials[0];n=m.node_tree.nodes;l=m.node_tree.links;e=next(x for x in n if x.type=='EMISSION');co=n.new('ShaderNodeTexCoord');co.object=w;scale=n.new('ShaderNodeVectorMath');scale.operation='SCALE';scale.inputs[3].default_value=1/80;l.new(co.outputs['Object'],scale.inputs[0]);tex=n.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(H/'assets/managed-turf-v1.png'),check_existing=True);tex.extension='REPEAT';l.new(scale.outputs[0],tex.inputs[0]);l.new(tex.outputs['Color'],e.inputs['Color']);tex.image.filepath='//assets/managed-turf-v1.png'
 for polygon in land.data.polygons:polygon.material_index=0
 # Exact source far estuary sits as a thin strip above mainriver in this camera;
 # painted far forest owns that distance, so omit conflicting farwater overlay.
 bpy.data.objects['Jangala | far estuary'].hide_render=True
 o=bpy.data.objects['PORT LAYER | accepted woodland bank'];bm=bmesh.new();bm.from_mesh(o.data);to=w.matrix_world.inverted()@o.matrix_world;inv=to.inverted()
 for v in bm.verts:v.co=to@v.co
 bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),dist=1e-5,plane_co=(0,3850,0),plane_no=(0,1,0),clear_inner=True)
 for v in bm.verts:v.co=inv@v.co
 bm.to_mesh(o.data);bm.free();o['farshore_cutoff_y']=3850
if __name__=='__main__':
 bpy.ops.wm.open_mainfile(filepath=str(H/'port-approach-realism.blend'));s=bpy.context.scene;s.frame_set(423,subframe=.75);finalize_environment(s);bpy.ops.wm.save_as_mainfile(filepath=str(H/'port-approach-realism.blend'),compress=True,relative_remap=False);s.render.filepath=str(H/'review/grass-river-midpoint.png');bpy.ops.render.render(write_still=True)
