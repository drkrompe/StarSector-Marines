import bpy,json,hashlib,math
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
H=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(H/'port-approach-realism.blend'));s=bpy.context.scene;c=bpy.data.objects['04 PORT / side window'];w=bpy.data.objects['LAYERED | inverse source flight'];r=bpy.data.objects['SECOND WIND | flight root'];land=bpy.data.objects['Jangala | continuous landform']
R=H/'review';R.mkdir(exist_ok=True)
checks={};samples=[]
for f in [400+i*.5 for i in range(96)]:
 s.frame_set(int(f),subframe=f%1);s.camera=c
 samples.append(dict(source_frame=f,camera_matrix=[list(row) for row in c.matrix_world],world_translation=list(w.location)))
checks['ship_root_rebased']=r.location.length<1e-6
checks['96_half_frame_samples']=len(samples)==96 and len({tuple(x['world_translation']) for x in samples})==96
coords=w.matrix_world.inverted()@land.matrix_world
bad=[]
for p in land.data.polygons:
 center=coords@p.center
 if -1745+1e-4<center.x<-195-1e-4 and 65+1e-4<center.y<495-1e-4:bad.append(p.index)
checks['no_terrain_faces_beneath_precinct']=not bad
checks['all_atlas_materials_on_geometry']=all(any(n.type=='TEX_IMAGE' and 'industrial-materials-v1' in n.image.name for n in bpy.data.materials['PORT REALISM | '+name].node_tree.nodes) for name in ['offwhite ribbed cladding','weathered grey standing seam','freight concrete','oxide structural steel'])
coverage=[]
for frame in (400,423.75,447.5):
 s.frame_set(int(frame),subframe=frame%1)
 for o in s.objects:
  if not o.name.startswith('PORT LAYER'):continue
  pts=[world_to_camera_view(s,c,o.matrix_world@v.co) for v in o.data.vertices]
  xs=[p.x for p in pts];ys=[p.y for p in pts];coverage.append(dict(frame=frame,layer=o.name,bounds=[min(xs),max(xs),min(ys),max(ys)],positive_depth=all(p.z>0 for p in pts)))
checks['positive_layer_depths']=all(x['positive_depth'] for x in coverage)
# Sky must cover entire screen. Transparent banks require silhouette review, not full-frame fill.
checks['sky_endpoint_coverage']=all(x['bounds'][0]<=0 and x['bounds'][1]>=1 and x['bounds'][2]<=0 and x['bounds'][3]>=1 for x in coverage if 'sky' in x['layer'])
def geometry_hash(o):
 return hashlib.sha256(json.dumps(dict(vertices=[list(v.co) for v in o.data.vertices],faces=[list(p.vertices) for p in o.data.polygons]),sort_keys=True).encode('utf-8')).hexdigest()
mesh_hashes={o.name:geometry_hash(o) for o in r.children_recursive if o.type=='MESH'}
checks['all_file_images_exist']=all(Path(bpy.path.abspath(im.filepath)).exists() for im in bpy.data.images if im.source=='FILE' and im.filepath)
checks['all_file_images_relative']=all(im.filepath.startswith('//') for im in bpy.data.images if im.source=='FILE' and im.filepath)
bpy.ops.wm.open_mainfile(filepath=str(H.parent/'layered-next-scenes/layered-next-scenes.blend'));base=bpy.context.scene;bc=bpy.data.objects['04 PORT / side window'];bw=bpy.data.objects['LAYERED | inverse source flight'];br=bpy.data.objects['SECOND WIND | flight root']
source_hashes={o.name:geometry_hash(o) for o in br.children_recursive if o.type=='MESH'}
checks['ship_meshes_identical_to_saved_source']=mesh_hashes==source_hashes
max_camera=max_world=0
for entry in samples:
 f=entry['source_frame'];base.frame_set(int(f),subframe=f%1)
 max_camera=max(max_camera,max(abs(bc.matrix_world[a][b]-entry['camera_matrix'][a][b]) for a in range(4) for b in range(4)))
 max_world=max(max_world,max(abs(bw.location[a]-entry['world_translation'][a]) for a in range(3)))
checks['96_cameras_match_saved_source']=max_camera<1e-6
checks['96_inverse_transforms_match_saved_source']=max_world<1e-6

report=dict(passed=all(checks.values()),checks=checks,coverage=coverage,scene_sha256=hashlib.sha256((H/'port-approach-realism.blend').read_bytes()).hexdigest(),timing='400+0.5i source12fps, i=0..95, output24fps',samples=samples,ship_meshes_compared=len(mesh_hashes),max_camera_matrix_error=max_camera,max_inverse_translation_error=max_world,limitations='Synthetic layer bounds and terrain face ownership; endpoints and motion still need visual review')
(R/'scene-validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(checks),flush=True)
if not report['passed']:raise RuntimeError('Port scene validation failed')
