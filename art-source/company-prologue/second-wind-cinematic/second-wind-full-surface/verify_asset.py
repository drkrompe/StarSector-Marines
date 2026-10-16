"""Read-only saved-asset structural and material verification; background Blender."""
from pathlib import Path
import hashlib,json,math,struct
import bpy
from mathutils import Vector
BASE=Path(__file__).resolve().parent
SOURCE=BASE.parent/'landing-wreck-pass/landing-wreck-pass.blend'
ASSET=BASE/'second-wind-full-surface.blend'
ROOT='SECOND WIND | flight root'
ATTRS={'SurfaceRestPosition':'FLOAT_VECTOR','SurfacePlateVariation':'FLOAT','SurfacePatina':'FLOAT','SurfacePanelGrid':'FLOAT'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def sig(o):
 h=hashlib.sha256()
 for v in o.data.vertices:h.update(struct.pack('<3f',*v.co))
 for p in o.data.polygons:h.update(struct.pack('<I',len(p.vertices)));h.update(struct.pack('<'+'I'*len(p.vertices),*p.vertices))
 return h.hexdigest()
def coordhash(o):
 h=hashlib.sha256()
 for d in o.data.uv_layers['SurfaceTileUV'].data:h.update(struct.pack('<2f',*d.uv))
 for name,kind in ATTRS.items():
  for d in o.data.attributes[name].data:h.update(struct.pack('<3f',*d.vector) if kind=='FLOAT_VECTOR' else struct.pack('<f',d.value))
 return h.hexdigest()
def relative(o,r):return r.matrix_world.inverted()@o.matrix_world
errors=[]
def check(ok,msg):
 if not ok:errors.append(msg)
bpy.ops.wm.open_mainfile(filepath=str(SOURCE));bpy.context.scene.frame_set(744);bpy.context.view_layer.update()
r=bpy.data.objects[ROOT]
source_transforms={o.name:relative(o,r).copy() for o in r.children_recursive}
source={o.name:{'hash':sig(o),'matrix':relative(o,r).copy()} for o in r.children_recursive if o.type=='MESH'}
bpy.ops.wm.open_mainfile(filepath=str(ASSET));s=bpy.context.scene;s.frame_set(1);bpy.context.view_layer.update();r=bpy.data.objects[ROOT]
meshes=[o for o in r.children_recursive if o.type=='MESH'];corners=0;polygons=0;max_matrix=0
for o in r.children_recursive:
 check(o.name in source_transforms,'Unexpected descendant '+o.name)
 if o.name in source_transforms:check(max(abs(relative(o,r)[i][j]-source_transforms[o.name][i][j]) for i in range(4) for j in range(4))<1e-4,'Changed hardpoint/pivot matrix '+o.name)
for o in meshes:
 check(o.name in source,'Unexpected mesh '+o.name)
 if o.name in source:
  check(sig(o)==source[o.name]['hash'],'Changed geometry '+o.name)
  delta=max(abs(relative(o,r)[i][j]-source[o.name]['matrix'][i][j]) for i in range(4) for j in range(4));max_matrix=max(max_matrix,delta)
  check(delta<1e-4,'Changed relative transform '+o.name+': '+str(delta))
 m=o.data;corners+=len(m.loops);polygons+=len(m.polygons)
 check(len(m.uv_layers)==1 and m.uv_layers[0].name=='SurfaceTileUV','Unexpected UVs '+o.name)
 check(not any('guide' in a.name.lower() or 'coverage' in a.name.lower() for a in m.attributes),'Guide attribute '+o.name)
 for name,kind in ATTRS.items():
  a=m.attributes.get(name);check(a is not None and a.domain=='CORNER' and a.data_type==kind,'Missing/wrong attribute '+o.name+' '+name)
  if a:
   check(all(math.isfinite(v) for d in a.data for v in (d.vector if kind=='FLOAT_VECTOR' else [d.value])),'Nonfinite attribute '+o.name+' '+name)
 rest=m.attributes.get('SurfaceRestPosition')
 if rest:
  check(all((rest.data[li].vector-relative(o,r)@m.vertices[loop.vertex_index].co).length<1e-4 for li,loop in enumerate(m.loops)),'Rest position differs from retained shell '+o.name)
 for d in m.uv_layers['SurfaceTileUV'].data:check(all(math.isfinite(v) for v in d.uv),'Nonfinite UV '+o.name)
 for p in m.polygons:check(p.material_index<len(m.materials) and m.materials[p.material_index] is not None,'Unassigned polygon '+o.name)
check(len(meshes)==196,'Unexpected retained mesh count')
expected={'FULL SURFACE | '+x for x in ('cream','oxide','gunmetal','backing')}
check(set(i.name for i in bpy.data.images if i.type not in ('RENDER_RESULT','COMPOSITING'))==expected,'Unexpected image datablocks '+str([i.name for i in bpy.data.images]))
for i in bpy.data.images:
 if i.type in ('RENDER_RESULT','COMPOSITING'):continue
 check(bool(i.packed_file),'Unpacked image '+i.name);check(list(i.size)==[627,627],'Wrong image size '+i.name)
 role=i.name.split(' | ')[-1];check(sha(BASE/'assets'/(role+'.png'))==hashlib.sha256(i.packed_file.data).hexdigest(),'Packed image bytes differ '+i.name)
allowed_attrs=set(ATTRS)
for m in bpy.data.materials:
 check(m.name.startswith('FULL SURFACE | '),'Old material '+m.name)
 for n in m.node_tree.nodes:
  check(n.type not in ('TEX_COORD','GROUP','SCRIPT'),'Unsupported implicit coordinate/group node '+m.name+' '+n.name)
  if n.type=='ATTRIBUTE':check(n.attribute_name in allowed_attrs,'Unapproved attribute '+m.name)
  if n.type=='UVMAP':check(n.uv_map=='SurfaceTileUV','Unapproved UV map '+m.name)
  if n.type=='TEX_IMAGE':
   check(n.image is not None and n.image.name in expected,'Unapproved texture '+m.name)
   check(bool(n.inputs['Vector'].links) and n.inputs['Vector'].links[0].from_node.type=='UVMAP','Image vector not explicit UV '+m.name)
  if n.type=='TEX_NOISE':check(bool(n.inputs['Vector'].links) and n.inputs['Vector'].links[0].from_node.type=='ATTRIBUTE' and n.inputs['Vector'].links[0].from_node.attribute_name=='SurfaceRestPosition','Noise not rest mapped '+m.name)
check(s.camera is None,'Asset has active camera')
check(not any(o.type in ('CAMERA','LIGHT') for o in s.objects),'Asset contains camera/light: '+str([o.name for o in s.objects if o.type in ('CAMERA','LIGHT')]))
check(not any(o.name.startswith(('LANDING FX','LIFT VECTOR','Cloud breakup')) for o in s.objects),'Asset contains old FX')
baseline={o.name:coordhash(o) for o in meshes};local={o.name:relative(o,r).copy() for o in meshes};max_motion_delta=0
transforms=[((0,0,0),(0,0,a)) for a in (0,45,90,180,270,360)]+[((310,-210,75),(p,0,37)) for p in (-45,45)]+[((-120,370,-45),(0,roll,123)) for roll in (-45,45)]
for location,angles in transforms:
 r.location=location;r.rotation_euler=tuple(math.radians(a) for a in angles);bpy.context.view_layer.update()
 for o in meshes:
  check(coordhash(o)==baseline[o.name],'Stored coordinates changed under motion '+o.name)
  delta=max(abs(relative(o,r)[i][j]-local[o.name][i][j]) for i in range(4) for j in range(4));max_motion_delta=max(max_motion_delta,delta)
  check(delta<2e-4,'Root-relative motion drift '+o.name)
report={'passed':not errors,'errors':errors,'asset_sha256':sha(ASSET),'source_sha256':sha(SOURCE),'build_script_sha256':sha(BASE/'build_asset.py'),'verification_script_sha256':sha(Path(__file__)),'retained_mesh_count':len(meshes),'retained_vertices':sum(len(o.data.vertices) for o in meshes),'physical_polygons':polygons,'verified_corners':corners,'all_retained_geometry_hashes_match':all(sig(o)==source[o.name]['hash'] for o in meshes),'max_source_relative_matrix_error':max_matrix,'motion_transform_count':len(transforms),'motion_transforms':transforms,'all_stored_coordinate_hashes_invariant':not any('Stored coordinates changed' in e for e in errors),'max_motion_relative_matrix_error':max_motion_delta,'materials':sorted(m.name for m in bpy.data.materials),'images':sorted(i.name for i in bpy.data.images if i.type not in ('RENDER_RESULT','COMPOSITING')),'ignored_blender_runtime_images':sorted(i.name for i in bpy.data.images if i.type in ('RENDER_RESULT','COMPOSITING')),'limits':'Finite structural/material graph checks only. No rendered pixels, art acceptance, seamlessness, physically measured PBR response or continuous collision verification. Source bow/stern/belly simplicity and asymmetric deep damage remain.'}
collection_name='SECOND WIND | full surface asset'
collection=bpy.data.collections.get(collection_name)
check(collection is not None,'Missing appendable asset collection')
if collection:check(set(collection.all_objects)=={r,*r.children_recursive},'Asset collection does not exactly contain root and descendants')
append_expected={o.name:{'geometry':sig(o),'coordinates':coordhash(o)} for o in meshes}
append_names={o.name for o in [r,*r.children_recursive]}
# Exercise real append, with all prior scene/datablocks discarded. No asset is saved.
bpy.ops.wm.read_factory_settings(use_empty=True)
with bpy.data.libraries.load(str(ASSET),link=False) as (available,loaded):
 check(collection_name in available.collections,'Collection unavailable for library append')
 loaded.collections=[collection_name]
appended=loaded.collections[0]
if appended:
 bpy.context.scene.collection.children.link(appended)
 check({o.name for o in appended.all_objects}==append_names,'Appended object inventory differs')
 appended_root=bpy.data.objects.get(ROOT)
 check(appended_root is not None,'Appended root missing')
 appended_meshes=[o for o in appended.all_objects if o.type=='MESH']
 check(len(appended_meshes)==196,'Append did not retain 196 meshes')
 check(not any(o.type in ('CAMERA','LIGHT') for o in bpy.context.scene.objects),'Append requires camera/light')
 for o in appended_meshes:
  check(o.name in append_expected,'Unexpected appended mesh '+o.name)
  if o.name in append_expected:
   check(sig(o)==append_expected[o.name]['geometry'],'Appended geometry differs '+o.name)
   check(coordhash(o)==append_expected[o.name]['coordinates'],'Appended coordinate attributes differ '+o.name)
  check(all(m is not None and m.library is None for m in o.data.materials),'Appended material requires external library '+o.name)
  check(o.library is None and o.data.library is None,'Appended geometry remains linked '+o.name)
 check(len(bpy.data.materials)==9,'Append did not retain nine material roles')
 check({i.name for i in bpy.data.images if i.type not in ('RENDER_RESULT','COMPOSITING')}==expected,'Append did not retain exact four neutral images')
 for i in bpy.data.images:
  if i.type in ('RENDER_RESULT','COMPOSITING'):continue
  check(i.library is None and bool(i.packed_file),'Appended image depends on external file/library '+i.name)
  role=i.name.split(' | ')[-1]
  check(hashlib.sha256(i.packed_file.data).hexdigest()==sha(BASE/'assets'/(role+'.png')),'Appended packed pixels differ '+i.name)
 for m in bpy.data.materials:
  check(m.library is None,'Appended material remains linked '+m.name)
  for n in m.node_tree.nodes:
   if n.type=='TEX_IMAGE':check(n.image is not None and bool(n.image.packed_file) and n.image.name in expected,'Appended graph texture unresolved '+m.name)
   if n.type=='ATTRIBUTE':check(n.attribute_name in allowed_attrs,'Appended graph attribute unresolved '+m.name)
   if n.type=='UVMAP':check(n.uv_map=='SurfaceTileUV','Appended graph UV unresolved '+m.name)
 # Blender retains the append-source Library record even after localizing every ID.
 linked_ids=[id.name_full for id in bpy.data.user_map() if id.library is not None]
 check(not linked_ids,'Append left linked external datablocks: '+str(linked_ids))
 report['append_test']={'factory_clean_scene':True,'collection':collection_name,'objects':len(appended.all_objects),'mesh_count':len(appended_meshes),'material_roles':len(bpy.data.materials),'packed_neutral_images':len([i for i in bpy.data.images if i.type not in ('RENDER_RESULT','COMPOSITING')]),'linked_external_datablocks':linked_ids,'append_source_library_records':len(bpy.data.libraries),'geometry_and_coordinate_hashes_match':not any('Appended geometry differs' in e or 'Appended coordinate attributes differ' in e for e in errors),'saved_scene':False}
report['passed']=not errors
report['errors']=errors
report['verification_script_sha256']=sha(Path(__file__))
(BASE/'review').mkdir(exist_ok=True);(BASE/'review/asset-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
print(json.dumps({k:v for k,v in report.items() if k not in ('motion_transforms',)},indent=2))
if errors:raise RuntimeError('Asset validation failed: '+str(len(errors)))
