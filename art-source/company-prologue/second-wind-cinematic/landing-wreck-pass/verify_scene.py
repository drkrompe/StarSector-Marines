"""Finite saved-scene checks for the combined wreck pass; never saves a blend.

Run Blender --background --factory-startup --python-exit-code 1 --python this_file.
Writes a compact SHA-bound report and raises on failures. Artistic acceptance,
continuous swept collision and flight/thermal simulation are outside this scope.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import math
import bpy
import bmesh
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from bpy_extras.object_utils import world_to_camera_view

HERE=Path(__file__).resolve().parent
PREVIOUS=HERE.parent/'landing-refinement'
SOURCE=PREVIOUS/'landing-refinement.blend'
FINAL=HERE/'landing-wreck-pass.blend'
ROOT='SECOND WIND | flight root'
LANDSCAPE_EXCEPTIONS={'LANDING YARD | far forest and hills','LANDING YARD | far daylight sky'}

def load(path,name):
    spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
helpers=load(PREVIOUS/'verify_scene.py','retained_landing_helpers')
motion=load(HERE/'unstable_arrival.py','wreck_motion_helpers')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def setframe(f):bpy.context.scene.frame_set(int(f),subframe=f%1)
def uvrecord(o):return {layer.name:[tuple(v.uv) for v in layer.data] for layer in o.data.uv_layers}
def uvequal(a,b):return a==b

def ground_check(scene):
    trees=[]
    for o in scene.objects:
        if o.type!='MESH' or not helpers.visible(o):continue
        ev=o.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh=ev.to_mesh();pts=[ev.matrix_world@v.co for v in mesh.vertices];faces=[]
        for p in mesh.polygons:
            v=[pts[i] for i in p.vertices]
            if len(v)>=3 and max(q.z for q in v)-min(q.z for q in v)<.001 and abs(sum(q.z for q in v)/len(v))<.02 and (v[1]-v[0]).cross(v[2]-v[0]).z>0:faces.append(tuple(p.vertices))
        if faces:trees.append((o.name,BVHTree.FromPolygons(pts,faces)))
        ev.to_mesh_clear()
    pairs=[]
    for i in range(17):
        u=(i+.5)/17
        for x,y,dx,dy in ((-120,-110+220*u,1,0),(120,-110+220*u,1,0),(-120+240*u,-110,0,1),(-120+240*u,110,0,1)):
            pairs.append(((x-.05*dx,y-.05*dy),(x+.05*dx,y+.05*dy)))
    probes=[(x,y) for x in (-113,-91,-63,-31,0,31,63,91,113) for y in (-103,-77,-49,0,49,77,103)]+[p for pair in pairs for p in pair]
    hits={};failures=[]
    for point in probes:
        h=[]
        for name,tree in trees:
            hit,*_=tree.ray_cast(Vector((*point,1)),Vector((0,0,-1)),2)
            if hit is not None:h.append((name,hit.z))
        hits[point]=h
        if len(h)!=1:failures.append(dict(xy=point,owners=h))
    joins=[abs(hits[a][0][1]-hits[b][0][1]) for a,b in pairs if len(hits[a])==len(hits[b])==1]
    return dict(passed=not failures and len(joins)==68 and max(joins,default=999)<.001,ray_count=len(probes),edge_pairs=len(joins),maximum_join_error_m=max(joins,default=None),failures=failures[:10],failure_count=len(failures))

def main():
    if not FINAL.exists():raise FileNotFoundError('Build final landing-wreck-pass.blend before validation')
    bpy.ops.wm.open_mainfile(filepath=str(HERE.parent/'animatic/jangala-blockout.blend'))
    original_names=sorted(o.name for o in bpy.data.objects[ROOT].children_recursive if o.type=='MESH')
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE));source_scene=bpy.context.scene;setframe(744)
    ship_names={o.name for o in source_scene.objects[ROOT].children_recursive}
    source_meshes={o.name:helpers.digestmesh(o) for o in source_scene.objects if o.type=='MESH'}
    base_uvs={n:uvrecord(source_scene.objects[n]) for n in original_names}
    damage=json.loads((HERE/'damage-contract.json').read_text())
    allowed_base={item['mesh']:item for item in damage.get('localized_exterior_skin_modifications',[])}
    source_surfaces={};source_materials={}
    for n in allowed_base:
        o=source_scene.objects[n];matrix=source_scene.objects[ROOT].matrix_world.inverted()@o.matrix_world
        bm=bmesh.new();bm.from_mesh(o.data);selected=[]
        for face in bm.faces:
            center=matrix@face.calc_center_median()
            if center.y<0 and any(((center.x-zone['root_local_center_xz'][0])/(zone['radius_xz'][0]*1.3))**2+((center.z-zone['root_local_center_xz'][1])/(zone['radius_xz'][1]*1.3))**2<1 for zone in damage['zones']):selected.append(face)
        edges=set(e for face in selected for e in face.edges if (matrix.to_3x3()@(e.verts[1].co-e.verts[0].co)).length>.9)
        if edges:bmesh.ops.subdivide_edges(bm,edges=list(edges),cuts=5,use_grid_fill=True)
        bm.verts.ensure_lookup_table();pts=[matrix@v.co for v in bm.verts];bm.verts.index_update();surface=BVHTree.FromPolygons(pts,[tuple(v.index for v in f.verts) for f in bm.faces]);source_surfaces[n]=surface;bm.free()
    for n in LANDSCAPE_EXCEPTIONS:source_materials[n]=[m.name if m else None for m in source_scene.objects[n].data.materials]

    sacrificial_uvs={o.name:uvrecord(o) for o in source_scene.objects if o.type=='MESH' and o.name.startswith(('Physical armor |','Armor captive fasteners |'))}
    fixed_names=[n for n in source_meshes if n not in ship_names and not n.startswith('LANDING FX |')]
    fixed_matrices={n:helpers.matrix(source_scene.objects[n]) for n in fixed_names}
    fixed_actions={n:helpers.curves(source_scene.objects[n]) for n in fixed_names}
    rig=[n for n in fixed_names if n.startswith(('YARD_contact_pad_','YARD_pad_contact_face_','YARD_clamp_slide_','YARD_retaining_wedge_','YARD_cradle_rail_'))]
    contact_matrix=helpers.matrix(source_scene.objects[ROOT]);camera_matrix=helpers.matrix(source_scene.camera)
    camera_intrinsics=(source_scene.camera.data.type,source_scene.camera.data.lens,source_scene.camera.data.sensor_width)
    setframe(580);start_position=tuple(source_scene.objects[ROOT].location)
    rig_poses=[]
    for index in range(192):
        setframe(motion._mapped(index/24));rig_poses.append({n:helpers.matrix(source_scene.objects[n]) for n in rig})
    bpy.ops.wm.open_mainfile(filepath=str(FINAL));s=bpy.context.scene;setframe(744)
    checks={};details={}
    mismatch=[n for n in original_names if n not in s.objects or n not in allowed_base and helpers.digestmesh(s.objects[n])!=source_meshes[n]]
    checks['171_base_ship_objects_and_unmodified_meshes_preserved']=len(original_names)==171 and not mismatch;details['base_mesh_mismatches']=mismatch
    checks['unmodified_base_ship_UVs_preserved']=all(n in s.objects and uvrecord(s.objects[n])==base_uvs[n] for n in original_names if n not in allowed_base)
    bounded_failures=[];maximum_unwarped_surface_error=0;uv_range_failures=[]
    for n,item in allowed_base.items():
        o=s.objects.get(n)
        if o is None or not n.startswith(('Main hull','Forebody replaceable armor','Engine room armored shoulder')):
            bounded_failures.append(dict(mesh=n,error='Not an authorized retained outer skin'));continue
        matrix=s.objects[ROOT].matrix_world.inverted()@o.matrix_world
        moved=0
        for vertex in o.data.vertices:
            point=matrix@vertex.co;depth=0
            if point.y<0:
                for zone in damage['zones']:
                    x,z=zone['root_local_center_xz'];rx,rz=zone['radius_xz']
                    radius=math.sqrt(((point.x-x)/(rx*.95))**2+((point.z-z)/(rz*.95))**2)
                    if radius<1:depth=max(depth,1.8*(1-radius*radius)**1.35)
            if depth:moved+=1
            unwarped=point.copy();unwarped.y-=depth
            near,normal,index,distance=source_surfaces[n].find_nearest(unwarped)
            maximum_unwarped_surface_error=max(maximum_unwarped_surface_error,distance if near is not None else 999)
        if len(o.data.vertices)!=item['final_vertex_count'] or abs(moved-item['displaced_vertices'])>8 or maximum_unwarped_surface_error>.03:
            bounded_failures.append(dict(mesh=n,vertices=len(o.data.vertices),expected_vertices=item['final_vertex_count'],displaced=moved,expected_displaced=item['displaced_vertices'],surface_error=maximum_unwarped_surface_error))
        finaluv=uvrecord(o)
        if set(finaluv)!=set(base_uvs[n]):uv_range_failures.append(n)
        for layer,values in finaluv.items():
            original=base_uvs[n].get(layer,[])
            if not original:continue
            if any(v[axis]<min(q[axis] for q in original)-1e-5 or v[axis]>max(q[axis] for q in original)+1e-5 for v in values for axis in (0,1)):uv_range_failures.append(n)
    checks['recorded_local_outer_skin_concavity_bounded_to_source_surface']=bool(allowed_base) and not bounded_failures
    checks['tessellated_outer_skin_UV_layers_and_interpolated_ranges_retained']=not uv_range_failures
    details['localized_outer_skin_exceptions']=list(allowed_base);details['bounded_concavity_failures']=bounded_failures;details['maximum_unwarped_source_surface_error']=maximum_unwarped_surface_error;details['localized_UV_range_failures']=uv_range_failures

    fixed_changed=[n for n in fixed_names if n not in s.objects or helpers.digestmesh(s.objects[n])!=source_meshes[n]]
    checks['all_existing_environment_and_contact_mesh_geometry_preserved']=not fixed_changed;details['environment_mesh_mismatches']=fixed_changed
    action_changed=[n for n in fixed_names if n not in s.objects or helpers.curves(s.objects[n])!=fixed_actions[n]]
    checks['environment_traffic_and_support_actions_preserved']=not action_changed;details['action_mismatches']=action_changed
    fixed_name_set=set(fixed_names)
    static_names=[n for n in fixed_names if fixed_actions[n] is None]
    moved=[n for n in static_names if n not in s.objects or max(abs(a-b) for a,b in zip(helpers.matrix(s.objects[n]),fixed_matrices[n]))>1e-5]
    checks['static_environment_transforms_preserved']=not moved;details['static_transform_mismatches']=moved
    checks['original_support_rig_present']=bool(rig) and all(n in s.objects for n in rig)
    changed_ship=[n for n in source_meshes if n in ship_names and n in s.objects and helpers.digestmesh(s.objects[n])!=source_meshes[n]]
    disallowed=[n for n in changed_ship if n not in allowed_base and not n.startswith(('Physical armor |','Armor captive fasteners |'))]
    missing_ship=[n for n in ship_names if n not in s.objects]
    checks['only_recorded_outer_skin_and_sacrificial_damage_changes']=not disallowed and not missing_ship
    uvfail=[]
    for n in changed_ship:
        if n in allowed_base:continue
        for layer,values in uvrecord(s.objects[n]).items():
            allowed=set(sacrificial_uvs.get(n,{}).get(layer,[]))
            if any(v not in allowed for v in values):uvfail.append(n);break
    checks['surviving_cut_armor_UV_values_retained']=not uvfail
    details['sacrificial_mesh_change_count']=len(changed_ship);details['disallowed_ship_changes']=disallowed;details['sacrificial_UV_mismatches']=uvfail
    projected=[]
    protected=set(s.objects[ROOT].children_recursive)
    for o in s.objects:
        if o.type!='MESH' or not helpers.visible(o) or o in protected:continue
        if any(m and m.name.startswith('YARD FINISH | ') for m in o.data.materials) and o.name not in LANDSCAPE_EXCEPTIONS:projected.append(o.name)
    checks['two_landscape_only_projection_exceptions_unchanged']=all(n in s.objects and [m.name if m else None for m in s.objects[n].data.materials]==source_materials[n] for n in LANDSCAPE_EXCEPTIONS)
    details['landscape_only_projection_exceptions']=sorted(LANDSCAPE_EXCEPTIONS)
    checks['no_active_latest_YARD_FINISH_on_physical_yard']=not projected;details['latest_projection_objects']=projected
    missing_images=[];image_count=0
    for image in bpy.data.images:
        if image.source!='FILE' or not image.filepath:continue
        image_count+=1
        if not image.packed_file and not Path(bpy.path.abspath(image.filepath)).is_file():missing_images.append(dict(name=image.name,path=image.filepath))
    checks['all_file_textures_packed_or_readable']=not missing_images;details['missing_images']=missing_images;details['file_image_count']=image_count
    shoes=[o for o in s.objects if o.name.startswith('PROPOSAL keel shoe')];pads=[o for o in s.objects if o.name.startswith('YARD_contact_pad_')]
    controls=[o for o in s.objects if o.name.startswith('LIFT VECTOR | placeholder')]
    checks['four_hidden_lift_controls']=len(controls)==4 and all(o.hide_render for o in controls)
    volumes=[o for o in s.objects if o.type=='MESH' and o.name.startswith('LANDING FX |')]
    opaque=[]
    for o in volumes:
        for m in o.data.materials:
            outs=[n for n in m.node_tree.nodes if n.type=='OUTPUT_MATERIAL'] if m and m.node_tree else []
            if not outs or any(n.inputs['Surface'].is_linked or not n.inputs['Volume'].is_linked for n in outs):opaque.append(o.name)
    checks['12_FX_containers_volume_only']=len(volumes)==12 and not opaque
    report_motion=json.loads((HERE/'motion-contract.json').read_text())['design'];zknots=report_motion['altitude_knots'];xknots=report_motion['horizontal_knots']
    max_path_error=0;minimum_gap=1e9;contact_failures=[];maximum_anchor_error=0;settled_error=0;camera_error=0;previous_z=None;upward=0;max_roll=0;max_pitch=0;uvbase=helpers.uvhash([o for o in s.objects if o.type=='MESH' and o.data.uv_layers]);uvstatic=True;corridor={};trajectory=[]
    nozzle_materials=[s.objects['LANDING FX | core %02d'%i].data.materials[0] for i in range(4)]
    initial_envelopes=[];last_envelopes=[];rig_error=0;rotation_error=0
    rotation=report_motion['rotation_degrees']
    for index in range(192):
        f=motion._mapped(index/24);setframe(f);root=s.objects[ROOT];p=root.matrix_world.translation
        expected=(motion._curve(xknots,f),contact_matrix[7],motion._curve(zknots,f))
        max_path_error=max(max_path_error,max(abs(p[i]-expected[i]) for i in range(3)))
        if previous_z is not None and p.z>previous_z+1e-5:upward+=1
        previous_z=p.z;max_roll=max(max_roll,abs(math.degrees(root.rotation_euler.x)));max_pitch=max(max_pitch,abs(math.degrees(root.rotation_euler.y)))
        expected_roll=motion._angle(f,604,rotation['roll_peak_frame'],730,rotation['roll_peak'])
        expected_pitch=motion._angle(f,612,rotation['pitch_peak_frame'],732,rotation['pitch_peak'])
        rotation_error=max(rotation_error,abs(math.degrees(root.rotation_euler.x)-expected_roll),abs(math.degrees(root.rotation_euler.y)-expected_pitch))
        camera_error=max(camera_error,max(abs(a-b) for a,b in zip(helpers.matrix(s.camera),camera_matrix)))
        for n in rig:rig_error=max(rig_error,max(abs(a-b) for a,b in zip(helpers.matrix(s.objects[n]),rig_poses[index][n])))
        if f>=744:settled_error=max(settled_error,max(abs(a-b) for a,b in zip(helpers.matrix(root),contact_matrix)))
        for shoe in shoes:
            sb=helpers.bounds(shoe);pad=min(pads,key=lambda o:sum((o.matrix_world.translation[j]-(sb[j][0]+sb[j][1])/2)**2 for j in (0,1)))
            pb=helpers.bounds(pad);gap=sb[2][0]-pb[2][1];minimum_gap=min(minimum_gap,gap)
            inside=all(sb[j][0]>=pb[j][0]-.025 and sb[j][1]<=pb[j][1]+.025 for j in (0,1))
            if gap<-.025 or f>=744 and (abs(gap)>.025 or not inside):contact_failures.append(dict(frame=f,shoe=shoe.name,gap=gap,inside=inside))
        for item in report_motion['nozzles']:
            anchor=s.objects['LANDING FX | nozzle anchor %02d'%item['nozzle']];mouth=s.objects[item['mouth']]
            maximum_anchor_error=max(maximum_anchor_error,(anchor.matrix_world.translation-mouth.matrix_world.translation).length)
        uvstatic=uvstatic and helpers.uvhash([o for o in s.objects if o.type=='MESH' and o.data.uv_layers])==uvbase
        for o in s.objects:
            if o.name not in fixed_name_set or o.type!='MESH' or not helpers.visible(o) or not o.name.startswith(('DISTRICT |','PORT TRAFFIC |')):continue
            pts=[o.matrix_world@Vector(v) for v in o.bound_box];bb=[(min(q[i] for q in pts),max(q[i] for q in pts)) for i in range(3)]
            if helpers.overlap(bb,((-300,150),(-110,110))):corridor.setdefault(o.name,dict(first_frame=f,bounds=bb))
        if index==0:initial_envelopes=[m.node_tree.nodes['Thrust envelope'].outputs[0].default_value for m in nozzle_materials]
        if index==191:last_envelopes=[m.node_tree.nodes['Thrust envelope'].outputs[0].default_value for m in nozzle_materials]
        if index%24==0 or index==191:trajectory.append(dict(output_frame=index,source_frame=f,position=list(p),rotation_degrees=[math.degrees(v) for v in root.rotation_euler]))
    checks.update(original_pad_clamp_transforms_all_192_poses=rig_error<1e-6,current_authored_trajectory_all_192_poses=max_path_error<.002,monotone_COM_descent=upward==0,visible_single_bank_amplitude=abs(max_roll-rotation['roll_peak'])<.02 and abs(max_pitch-rotation['pitch_peak'])<.02 and rotation_error<.003,four_contact_pairs=len(shoes)==len(pads)==4,no_sampled_pad_penetration_and_settled_contacts=not contact_failures,exact_settled_source_pose_744_plus=settled_error<1e-6,nozzle_attachment_all_192_poses=maximum_anchor_error<1e-5,fixed_source_camera_all_192_poses=camera_error<1e-6,all_current_mesh_UVs_static=uvstatic,initial_positive_final_zero_exhaust=all(v>0 for v in initial_envelopes) and all(v==0 for v in last_envelopes),conservative_reused_district_corridor_clear=not corridor)
    setframe(744);origin=Vector((0,0,20));a=world_to_camera_view(s,s.camera,origin);b=world_to_camera_view(s,s.camera,origin+Vector((10,0,0)))
    checks['camera_intrinsics_and_bow_right_preserved']=(s.camera.data.type,s.camera.data.lens,s.camera.data.sensor_width)==camera_intrinsics and s.camera.location.y<0 and b.x>a.x
    ground=ground_check(s);checks['actual_ground_ownership_and_boundary_joins']=ground['passed']
    details.update(maximum_support_rig_matrix_error=rig_error,maximum_trajectory_error_m=max_path_error,minimum_shoe_pad_gap_m=minimum_gap,maximum_nozzle_attachment_error_m=maximum_anchor_error,maximum_settled_matrix_error=settled_error,maximum_camera_matrix_error=camera_error,maximum_rotation_curve_error_degrees=rotation_error,peak_roll_degrees=max_roll,peak_pitch_degrees=max_pitch,contact_failure_count=len(contact_failures),contact_failures=contact_failures[:10],corridor_violations=corridor,FX_initial_envelopes=initial_envelopes,FX_final_envelopes=last_envelopes,ground=ground,trajectory_samples=trajectory,static_environment_meshes_compared=len(fixed_names),support_rig_meshes_compared=len(rig))
    report=dict(passed=all(checks.values()),checks=checks,failed_checks=[k for k,v in checks.items() if not v],source_blend_sha256=sha(SOURCE),final_blend_sha256=sha(FINAL),validator_sha256=sha(Path(__file__)),source_frames=192,source_clock_knots=motion.KNOTS,details=details,limits=['Finite 192-pose shoe/pad and nozzle checks; no continuous swept collision, structural-load or fluid simulation.','171 base mesh objects remain; exact geometry/UV hashes required except recorded outer skin concavity. Those tessellated vertices are analytically unwarped and checked against an independently reconstructed authorized source tessellated source surface within0.03root-local units (nonplanar BMesh subdivision grids may choose different interior triangulations) (near-zero boundary depth counts tolerate8 floating-point classification changes), with retained UV layer/range checks. Sacrificial plate/fastener face deletion/buckle allowed; surviving UV values checked against source values.','Geometry-derived near-datum ground rays sample199 apron/boundary points with68 edge pairs; tiny holes, overlays at other heights and full polygon ownership need further review.','Conservative AABB corridor checks apply to retained visible district/traffic meshes; authored apron/support/service objects are intentionally excluded.','No active latest YARD FINISH physical-yard assignment (two exact unchanged distant landscape exceptions) and readable texture paths are construction checks, not visual continuity acceptance.'])
    (HERE/'review').mkdir(exist_ok=True);(HERE/'review/scene-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(passed=report['passed'],failed_checks=report['failed_checks'],maximum_trajectory_error_m=max_path_error,contact_failures=len(contact_failures),corridor_violations=len(corridor))))
    if not report['passed']:raise AssertionError('Combined scene validation failed; see review/scene-validation.json')
if __name__=='__main__':main()
