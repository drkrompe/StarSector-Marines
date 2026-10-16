"""Optional single-guide finish on visible near-side geometry; retains atlas fallback.

Blender --background --factory-startup --python project_side.py -- --image <png>
Optional --registration <json>: {"scale":[sx,sy],"offset":[ox,oy],"notes":...}.
Correction is global UV framing only; it never establishes panel registration.
"""
from pathlib import Path
import bpy,json,sys,hashlib
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
OUT=Path(__file__).resolve().parent
STAGES=OUT/'build'/'stages';STAGES.mkdir(parents=True,exist_ok=True)
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
def argument(name,default=None):return args[args.index(name)+1] if name in args else default
path=Path(argument('--image')).resolve()
reg_path=argument('--registration')
registration=json.loads(Path(reg_path).read_text()) if reg_path else {'scale':[1,1],'offset':[0,0],'notes':'No global adjustment; unmeasured generated internal registration.'}
bpy.ops.wm.open_mainfile(filepath=str(STAGES/'second-wind-refined-surface-proof.blend'))
scene=bpy.context.scene;scene.frame_set(1);root=bpy.data.objects['SECOND WIND | flight root']
cam=scene.camera=bpy.data.objects['PROOF | port side landing surface']
im=bpy.data.images.load(str(path));im.pack()
dg=bpy.context.evaluated_depsgraph_get();material_cache={};covered=0;total=0;covered_objects=[]
def projected_material(m):
    if m in material_cache:return material_cache[m]
    out=m.copy();out.name=m.name+' | exact-guide side UV'
    n=out.node_tree.nodes;l=out.node_tree.links;p=n.get('Principled BSDF')
    current=p.inputs['Base Color'].links[0].from_socket if p.inputs['Base Color'].links else None
    tex=n.new('ShaderNodeTexImage');tex.image=im;tex.extension='CLIP';tex.interpolation='Linear'
    uv=n.new('ShaderNodeUVMap');uv.uv_map='SideGuideUV'
    mapping=n.new('ShaderNodeVectorMath');mapping.operation='MULTIPLY_ADD'
    mapping.inputs[1].default_value=(*registration['scale'],1);mapping.inputs[2].default_value=(*registration['offset'],0)
    l.new(uv.outputs[0],mapping.inputs[0]);l.new(mapping.outputs[0],tex.inputs['Vector'])
    attr=n.new('ShaderNodeAttribute');attr.attribute_name='SideGuideCoverage'
    mult=n.new('ShaderNodeMath');mult.operation='MULTIPLY';l.new(attr.outputs['Fac'],mult.inputs[0]);l.new(tex.outputs['Alpha'],mult.inputs[1])
    mix=n.new('ShaderNodeMixRGB');l.new(mult.outputs[0],mix.inputs[0]);l.new(tex.outputs['Color'],mix.inputs[2])
    if current:l.new(current,mix.inputs[1])
    else:mix.inputs[1].default_value=p.inputs['Base Color'].default_value
    l.new(mix.outputs[0],p.inputs['Base Color'])
    out['registration_limit']='Single near-side view only; global bounds correction does not register individual generated panels.'
    material_cache[m]=out;return out
for ob in root.children_recursive:
    if ob.type!='MESH' or not ob.data.materials:continue
    mesh=ob.data;uv=mesh.uv_layers.get('SideGuideUV') or mesh.uv_layers.new(name='SideGuideUV')
    attr=mesh.attributes.get('SideGuideCoverage') or mesh.attributes.new('SideGuideCoverage','FLOAT','CORNER')
    invtrans=ob.matrix_world.to_3x3().inverted().transposed()
    ncovered=0
    for p in mesh.polygons:
        total+=1
        center=ob.matrix_world@p.center;normal=(invtrans@p.normal).normalized();to_cam=(cam.location-center).normalized()
        proj=world_to_camera_view(scene,cam,center)
        eligible=normal.dot(to_cam)>.35 and proj.z>0 and 0<proj.x<1 and 0<proj.y<1
        facing=eligible
        if eligible:
            # Large accepted quads can have an occluded center (pod hatch) while
            # most of the face is visible. Test inset corner samples as well.
            eligible=False
            for sample in [center]+[(ob.matrix_world@mesh.vertices[vi].co)*.92+center*.08 for vi in p.vertices]:
                ray=sample-cam.location
                hit,point,n,index,hitob,matrix=scene.ray_cast(dg,cam.location,ray.normalized(),distance=ray.length+1)
                if hit and hitob.original==ob.original and abs((point-cam.location).length-ray.length)<1:
                    eligible=True;break
        # The low horizontal pod has one large chamfered front quad whose center
        # is occupied by its separate opaque access hatch. Its remaining front
        # face is demonstrably in the guide; the hatch still owns occlusion.
        if facing and ob.name.startswith('PORT SERVICE POD -'):eligible=True
        weight=1.0 if eligible else 0.0
        if eligible:covered+=1;ncovered+=1
        for li in p.loop_indices:
            v=ob.matrix_world@mesh.vertices[mesh.loops[li].vertex_index].co
            projected=world_to_camera_view(scene,cam,v)
            uv.data[li].uv=(projected.x,projected.y);attr.data[li].value=weight
    if ncovered:
        covered_objects.append({'name':ob.name,'covered_polygons':ncovered})
        for i,m in enumerate(mesh.materials):
            if m:mesh.materials[i]=projected_material(m)
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(STAGES/'second-wind-side-projection-proof.blend'))
report={'status':'Optional near-side object-attached single-guide surface prototype; hidden faces use neutral atlas',
 'source_image':str(path),'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'native_image_dimensions':list(im.size),
 'geometry_guide':'guides/side-surface-guide-v1.png','camera_name':cam.name,'camera_matrix_world':[list(r) for r in cam.matrix_world],
 'camera_matrix_ship_local':[list(r) for r in root.matrix_world.inverted()@cam.matrix_world],
 'guide_resolution':[1920,1080],'global_registration':registration,'covered_polygons':covered,'tested_polygons':total,'covered_objects':covered_objects,
 'coverage_method':'Guide-camera visible polygon center/inset-corner samples plus camera-facing normal threshold0.35; pod broad-front hatch occlusion exception. Material blend stored per corner; rigid UV coverage travels with mesh.',
 'limits':'Generated artwork may alter internal geometry. UV projection does not certify internal correspondence. Single aspect, no hidden-surface texture claim; source lighting remains partly baked into RGB.'}
(OUT/'side-projection-registration.json').write_text(json.dumps(report,indent=2))
if '--render' in args:
    for tag,name in [('projected-port-side','PROOF | port side landing surface'),('projected-port-quarter','PROOF | port bow quarter')]:
        scene.camera=bpy.data.objects[name];scene.render.filepath=str(OUT/(tag+'.png'));bpy.ops.render.render(write_still=True)
