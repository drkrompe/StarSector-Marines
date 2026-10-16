"""Read-only construction evidence; run Blender --background --factory-startup.

Evaluates the exact 192 edited poses. Writes JSON even when a check fails,
then exits nonzero. No scene is saved and no rendering is performed.
"""
from pathlib import Path
import array
import hashlib
import json
import struct
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from bpy_extras.object_utils import world_to_camera_view

HERE=Path(__file__).resolve().parent
SOURCE=HERE.parent/'second-wind-surface-proof/second-wind-daylight-landing-proof.blend'
FINAL=HERE/'landing-refinement.blend'
KNOTS=((0,580),(2,624),(2.5,648),(6.5,744),(8,774))

def mapped(t):
    for (a,x),(b,y) in zip(KNOTS,KNOTS[1:]):
        if t<=b:return x+(y-x)*(t-a)/(b-a)
    return KNOTS[-1][1]
FRAMES=[mapped(i/24) for i in range(192)]
def frame(f):bpy.context.scene.frame_set(int(f),subframe=f%1)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def matrix(o):return tuple(v for row in o.matrix_world for v in row)
def digestmesh(o):
    h=hashlib.sha256()
    for v in o.data.vertices:h.update(struct.pack('<3f',*v.co))
    for p in o.data.polygons:h.update(struct.pack('<%di'%len(p.vertices),*p.vertices))
    return h.hexdigest()
def uvhash(objects):
    h=hashlib.sha256()
    for o in sorted(objects,key=lambda o:o.name):
        for layer in o.data.uv_layers:
            values=array.array('f',[0])*(len(layer.data)*2);layer.data.foreach_get('uv',values)
            h.update(o.name.encode());h.update(layer.name.encode());h.update(values.tobytes())
    return h.hexdigest()
def bounds(o):
    ev=o.evaluated_get(bpy.context.evaluated_depsgraph_get());m=ev.to_mesh()
    pts=[ev.matrix_world@v.co for v in m.vertices];ev.to_mesh_clear()
    return [(min(p[i] for p in pts),max(p[i] for p in pts)) for i in range(3)]
def curves(o):
    a=o.animation_data.action if o.animation_data else None
    if not a:return None
    return [(c.data_path,c.array_index,[(tuple(k.co),tuple(k.handle_left),tuple(k.handle_right),k.interpolation) for k in c.keyframe_points]) for l in a.layers for st in l.strips for bag in st.channelbags for c in bag.fcurves]
def visible(o):return not o.hide_render and not any(c.hide_render for c in o.users_collection)
def overlap(a,b):return all(a[i][0]<b[i][1] and a[i][1]>b[i][0] for i in range(len(b)))
def main():
    bpy.ops.wm.open_mainfile(filepath=str(HERE.parent/'animatic/jangala-blockout.blend'))
    names=sorted(o.name for o in bpy.data.objects['SECOND WIND | flight root'].children_recursive if o.type=='MESH')
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE));s=bpy.context.scene
    hashes={n:digestmesh(s.objects[n]) for n in names}
    rig=[o.name for o in s.objects if o.name.startswith(('YARD_clamp_slide_','YARD_retaining_wedge_'))]
    rigactions={n:curves(s.objects[n]) for n in rig}
    compare=names+['SECOND WIND | flight root']+rig
    poses=[]
    for f in FRAMES:
        frame(f);poses.append({n:matrix(s.objects[n]) for n in compare})
    bpy.ops.wm.open_mainfile(filepath=str(FINAL));s=bpy.context.scene
    checks={};details={}
    checks['171_original_ship_meshes_identified']=len(names)==171
    mismatches=[n for n in names if n not in s.objects or digestmesh(s.objects[n])!=hashes[n]]
    checks['original_ship_vertex_polygon_positions_preserved']=not mismatches;details['mesh_mismatches']=mismatches
    checks['original_clamp_action_keys_preserved']=bool(rig) and all(n in s.objects and curves(s.objects[n])==rigactions[n] for n in rig)
    uvobjects=[o for o in s.objects if o.type=='MESH' and o.data.uv_layers]
    baseuv=uvhash(uvobjects);uvsame=True;maxerror=0;camera_matrices=[];contacts=[];roots=[];corridor_failures=[]
    shoes=[o for o in s.objects if o.name.startswith('PROPOSAL keel shoe')]
    pads=[o for o in s.objects if o.name.startswith('YARD_contact_pad_')]
    checks['four_contact_pairs_exist']=len(shoes)==len(pads)==4
    manifest=json.loads((HERE/'scene-manifest.json').read_text())
    reused_names=manifest['yard']['reused_objects'];reused=[s.objects.get(n) for n in reused_names]
    checks['manifest_reused_objects_exist']=bool(reused) and all(o is not None for o in reused)
    reserved=((-300,150),(-110,110))
    for index,f in enumerate(FRAMES):
        frame(f);maxerror=max(maxerror,max(abs(a-b) for n in compare for a,b in zip(poses[index][n],matrix(s.objects[n]))))
        uvsame=uvsame and uvhash(uvobjects)==baseuv
        root=s.objects['SECOND WIND | flight root'];roots.append((f,tuple(root.matrix_world.translation)))
        camera_matrices.append(matrix(s.camera))
        for shoe in shoes:
            sb=bounds(shoe);pad=min(pads,key=lambda p:sum((p.matrix_world.translation[i]-(sb[i][0]+sb[i][1])/2)**2 for i in (0,1)))
            pb=bounds(pad);gap=sb[2][0]-pb[2][1]
            contacts.append(dict(source_frame=f,shoe=shoe.name,pad=pad.name,gap_m=gap,footprint_inside=all(sb[i][0]>=pb[i][0]-.025 and sb[i][1]<=pb[i][1]+.025 for i in (0,1))))
        for ob in reused:
            if not ob or ob.type!='MESH' or not visible(ob):continue
            pts=[ob.matrix_world@Vector(v) for v in ob.bound_box]
            bb=[(min(p[i] for p in pts),max(p[i] for p in pts)) for i in range(3)]
            if overlap(bb,reserved):corridor_failures.append(dict(frame=f,object=ob.name,bounds=bb))
    checks['original_ship_root_and_rig_transforms_all_192_poses']=maxerror<1e-5
    checks['all_mesh_uv_values_static_all_192_poses']=uvsame
    checks['fixed_camera_all_192_poses']=all(m==camera_matrices[0] for m in camera_matrices)
    frame(744);origin=Vector((0,0,20));a=world_to_camera_view(s,s.camera,origin);b=world_to_camera_view(s,s.camera,origin+Vector((10,0,0)))
    checks['starboard_camera_bow_X_projects_right']=s.camera.matrix_world.translation.y<0 and b.x>a.x
    moving=[p for f,p in roots if f<624];held=[p for f,p in roots if 624<=f<=744]
    checks['positive_X_translation_before_624']=len(moving)>1 and moving[-1][0]>moving[0][0]+1 and all(q[0]>=p[0]-1e-5 for p,q in zip(moving,moving[1:]))
    checks['no_horizontal_drift_624_to_contact']=bool(held) and all(abs(p[i]-held[0][i])<1e-5 for p in held for i in (0,1))
    checks['shoe_no_pad_penetration_sampled_approach']=bool(contacts) and all(c['gap_m']>=-.025 for c in contacts)
    settled=[c for c in contacts if c['source_frame']>=744]
    checks['shoe_contact_and_footprints_744_plus']=bool(settled) and all(abs(c['gap_m'])<.025 and c['footprint_inside'] for c in settled)
    checks['reused_geometry_clear_conservative_apron_approach_AABB_all_192']=not corridor_failures
    controls=[o for o in s.objects if o.name.startswith('LIFT VECTOR | placeholder')]
    checks['four_hidden_lift_controls']=len(controls)==4 and all(o.hide_render for o in controls)
    volumes=[o for o in s.objects if o.type=='MESH' and o.name.startswith('LANDING FX |')]
    shaders=[]
    for o in volumes:
        for m in o.data.materials:
            outputs=[n for n in m.node_tree.nodes if n.type=='OUTPUT_MATERIAL'] if m and m.use_nodes else []
            shaders.append(bool(outputs) and all(n.inputs['Volume'].is_linked and not n.inputs['Surface'].is_linked for n in outputs))
    checks['12_FX_containers_volume_only']=len(volumes)==12 and all(shaders)
    materials={m for o in volumes for m in o.data.materials if m}
    envelope=[]
    for f in (FRAMES[0],FRAMES[-1]):
        frame(f);envelope.append([m.node_tree.nodes['Thrust envelope'].outputs[0].default_value for m in materials])
    checks['FX_initial_positive_end_zero']=bool(materials) and all(v>0 for v in envelope[0]) and all(v==0 for v in envelope[1])
    # Geometry-derived upward ground triangles near datum; no name whitelist.
    frame(744);trees=[];ground_vertices=[]
    for o in s.objects:
        if o.type!='MESH' or not visible(o):continue
        ev=o.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh=ev.to_mesh();world=[ev.matrix_world@v.co for v in mesh.vertices]
        faces=[]
        for poly in mesh.polygons:
            p=[world[i] for i in poly.vertices]
            if len(p)>=3 and max(v.z for v in p)-min(v.z for v in p)<.001 and abs(sum(v.z for v in p)/len(p))<.02 and (p[1]-p[0]).cross(p[2]-p[0]).z>0:
                faces.append(tuple(poly.vertices));ground_vertices.extend(p)
        if faces:trees.append((o.name,BVHTree.FromPolygons(world,faces)))
        ev.to_mesh_clear()
    probes=[(x,y) for x in (-113,-91,-63,-31,0,31,63,91,113) for y in (-103,-77,-49,0,49,77,103)]
    edge_pairs=[]
    for j in range(17):
        q=(j+.5)/17
        for x,y,dx,dy in ((-120,-110+220*q,1,0),(120,-110+220*q,1,0),(-120+240*q,-110,0,1),(-120+240*q,110,0,1)):
            pair=((x-.05*dx,y-.05*dy),(x+.05*dx,y+.05*dy));edge_pairs.append(pair);probes.extend(pair)
    failures=[];heights={}
    for x,y in probes:
        hits=[]
        for name,tree in trees:
            hit,normal,idx,dist=tree.ray_cast(Vector((x,y,1)),Vector((0,0,-1)),2)
            if hit is not None:hits.append((name,hit.z))
        heights[(x,y)]=hits
        if len(hits)!=1:failures.append(dict(x=x,y=y,owners=hits))
    joins=[abs(heights[a][0][1]-heights[b][0][1]) for a,b in edge_pairs if len(heights[a])==len(heights[b])==1]
    checks['one_visible_ground_owner_at_apron_and_boundary_probes']=bool(trees) and not failures
    checks['ground_boundary_height_join_all_68_pairs']=len(joins)==68 and max(joins,default=999)<.001
    # Small deliberate numerical controls prove predicates reject known bad cases.
    checks['predicate_fault_controls']=overlap(((-1,1),(-1,1)),reserved) and not overlap(((200,210),(0,1)),reserved) and not (-.05>=-.025) and not (abs(.05)<.025)
    details.update(original_mesh_hashes=hashes,source_frames=FRAMES,max_transform_element_error=maxerror,UV_sha256=baseuv,UV_mesh_count=len(uvobjects),camera_projected_X_delta=b.x-a.x,shoe_pad_samples=contacts,corridor_violations=corridor_failures,FX_envelopes=envelope,ground_owner_meshes=[n for n,t in trees],ground_probe_count=len(probes),ground_ownership_failures=failures,ground_max_join_height_error=max(joins,default=None))
    report=dict(passed=all(checks.values()),checks=checks,source_blend_sha256=sha(SOURCE),final_blend_sha256=sha(FINAL),source_fps=12,output_fps=24,output_frames=192,retiming_knots=KNOTS,details=details,limits=['Finite geometry/contact/ray sampling, not continuous collision, load engineering, thermal simulation or visual acceptance.','Ground rays cover upward near-datum faces at finite apron/boundary probes; arbitrary tiny holes and above-datum overlays require image review.','Conservative world AABB corridor excludes reused visible district/traffic geometry and may flag empty corners; authored apron support/service geometry is outside this check.','171 original ship meshes derive from immutable animatic; added surface meshes are covered by static UV and evaluated source pose checks only where original identity exists.'])
    (HERE/'review').mkdir(exist_ok=True);(HERE/'review/scene-validation.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps(dict(passed=report['passed'],failed_checks=[k for k,v in checks.items() if not v],max_transform_error=maxerror,corridor_failures=len(corridor_failures),ground_failures=len(failures))))
    if not report['passed']:raise AssertionError('Scene validation failed; see review/scene-validation.json')
if __name__=='__main__':main()
