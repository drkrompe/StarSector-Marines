"""Verify the saved refined scene; no rendering or .blend writes.

Run in background Blender with --factory-startup. The scene manifest supplies
the intended roads/obstacle inventory; geometry and animation come from the
actual saved scene, never from its earlier build-time verification receipts.
"""
from pathlib import Path
import hashlib
import json
import sys

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from bpy_extras.object_utils import world_to_camera_view

H=Path(__file__).resolve().parent
sys.path.insert(0,str(H))
from verify_ground import verify_ground
from port_traffic import validate_traffic

FRAMES=[400+i*.5 for i in range(96)]
CAMERA='04 PORT / side window'
WORLD='LAYERED | inverse source flight'
ROOT='SECOND WIND | flight root'

def mesh_hash(obj):
    data=dict(vertices=[list(v.co) for v in obj.data.vertices],faces=[list(p.vertices) for p in obj.data.polygons])
    return hashlib.sha256(json.dumps(data,sort_keys=True).encode('utf-8')).hexdigest()

def matrix_rows(matrix):
    return [list(row) for row in matrix]

def sample_transforms(scene):
    result=[]
    for frame in FRAMES:
        scene.frame_set(int(frame),subframe=frame%1)
        result.append(dict(source_frame=frame,matrices={name:matrix_rows(bpy.data.objects[name].matrix_world) for name in (CAMERA,WORLD,ROOT)}))
    return result

def parked_contacts(scene, world):
    """Check actual cradle/platform feet against the owning ground mesh."""
    scene.frame_set(400)
    inverse=world.matrix_world.inverted()
    top=bpy.data.objects['PORT GROUND | continuous precinct and roads']
    transform=inverse@top.matrix_world
    tree=BVHTree.FromPolygons([transform@v.co for v in top.data.vertices],[list(p.vertices) for p in top.data.polygons])
    feet=[]
    for obj in scene.objects:
        if obj.type!='MESH' or not obj.name.startswith('PARKED | '):continue
        if 'service support foot' not in obj.name and 'maintenance platform grounded foot' not in obj.name:continue
        points=[inverse@obj.matrix_world@v.co for v in obj.data.vertices]
        bottom=min(p.z for p in points)
        probes=[]
        # All foot's bottom corners, not only its centre, require owned ground.
        for p in points:
            if abs(p.z-bottom)>.001:continue
            hit,normal,index,distance=tree.ray_cast(Vector((p.x,p.y,1000)),Vector((0,0,-1)),2000)
            probes.append(None if hit is None else bottom-hit.z)
        feet.append(dict(object=obj.name,render_visible=not obj.hide_render,bottom_z=bottom,corner_gaps=probes,passed=bool(probes) and all(gap is not None and abs(gap)<.002 for gap in probes)))
    roots=[obj for obj in scene.objects if obj.name.startswith('PARKED | ') and obj.name.endswith('source-plan root')]
    initial={obj.name:inverse@obj.matrix_world for obj in roots}
    stationary_error=0
    for frame in FRAMES:
        scene.frame_set(int(frame),subframe=frame%1)
        inverse=world.matrix_world.inverted()
        for obj in roots:
            matrix=inverse@obj.matrix_world
            stationary_error=max(stationary_error,max(abs(matrix[a][b]-initial[obj.name][a][b]) for a in range(4) for b in range(4)))
    hidden_guides=bool(feet) and all(not row['render_visible'] for row in feet)
    return dict(passed=len(roots)==2 and len(feet)==16 and all(row['passed'] for row in feet) and stationary_error<.001,
                parked_root_count=len(roots),foot_count=len(feet),feet=feet,max_source_stationary_error=stationary_error,
                evidence_scope='Hidden geometry-guide contacts only; visible card support depiction requires rendered QA.' if hidden_guides else 'Visible authored geometry feet contact owning ground.',
                limits='Bottom-corner/ground contacts and stationary guide roots only; no complete structural-load, depicted card support contact, or hull-clearance proof.')

def verify_cards(scene, world):
    """Validate retained card registration; painted support contact needs visual QA."""
    objects=[o for o in scene.objects if 'ship_card_id' in o]
    if not objects:return dict(present=False,passed=True,cards=[],evidence_scope='Geometry presentation; no ship cards in saved scene.')
    if any('airtraffic_contract' in obj for obj in objects):return verify_air_cards(scene,world,objects)
    contract=json.loads((H/'ship-card-layout.json').read_text(encoding='utf-8'))
    registration=json.loads((H/'artwork-registration.json').read_text(encoding='utf-8'))
    overlay={row['id']:row for row in registration['ships']}
    expected={row['id']:dict(row,**{key:value for key,value in overlay[row['id']].items() if key!='id'}) for row in contract['ships']}
    cards=[]
    scene.frame_set(423,subframe=.75)
    inverse=world.matrix_world.inverted()
    initial={o.name:inverse@o.matrix_world for o in objects}
    for obj in objects:
        row=expected.get(obj['ship_card_id'])
        checks={}
        if row is None:
            cards.append(dict(object=obj.name,passed=False,error='No matching card layout contract'));continue
        checks['one_quad_four_vertices']=len(obj.data.vertices)==4 and len(obj.data.polygons)==1 and len(obj.data.polygons[0].vertices)==4
        checks['source_world_parent']=obj.parent==world
        checks['visible']=not obj.hide_render
        center=Vector(row['source_center']);right=Vector(row['source_right'])*row['width']/2;up=Vector(row['source_up'])*row['height']/2
        desired=[center-right-up,center+right-up,center+right+up,center-right+up]
        actual=[inverse@obj.matrix_world@v.co for v in obj.data.vertices]
        geometry_error=max((actual[i]-desired[i]).length for i in range(min(len(actual),4)))
        checks['quad_matches_retained_source_geometry']=len(actual)==4 and geometry_error<.002
        uv=obj.data.uv_layers.active
        checks['unit_canvas_uvs']=uv is not None and len(uv.data)==4 and all((uv.data[i].uv-Vector(pair)).length<1e-6 for i,pair in enumerate(((0,0),(1,0),(1,1),(0,1))))
        contact_uv=list(obj.get('contact_uv',[]));contact_source=list(obj.get('contact_source',[]))
        checks['contact_metadata_matches_contract']=contact_uv==row['contact_uv'] and contact_source==row['contact_source']
        checks['contact_inside_canvas']=len(contact_uv)==2 and all(0<=value<=1 for value in contact_uv)
        anchor_error=None
        source_anchor_error=None
        if len(actual)==4 and len(contact_uv)==2 and len(contact_source)==3:
            u,v=contact_uv
            card_anchor=actual[0]+u*(actual[1]-actual[0])+v*(actual[3]-actual[0])
            projected=world_to_camera_view(scene,bpy.data.objects[CAMERA],world.matrix_world@card_anchor)
            target=world_to_camera_view(scene,bpy.data.objects[CAMERA],world.matrix_world@Vector(contact_source))
            anchor_error=((projected.x-target.x)**2+(projected.y-target.y)**2)**.5
            source_anchor_error=(card_anchor-Vector(contact_source)).length
            checks['reference_contact_screen_registration']=projected.z>0 and target.z>0 and anchor_error<.001
            checks['authored_foot_anchor_source_registration']=source_anchor_error<.002 and abs(contact_source[2]-.4)<.002
        else:checks['reference_contact_screen_registration']=False
        guide_names=row['guide_meshes']
        checks['retained_geometry_guides_hidden']=bool(guide_names) and all(name in bpy.data.objects and bpy.data.objects[name].hide_render for name in guide_names)
        mats=[m for m in obj.data.materials if m and m.use_nodes]
        materials_ok=False;images=[]
        for material in mats:
            textures=[n for n in material.node_tree.nodes if n.type=='TEX_IMAGE' and n.image]
            for texture in textures:
                path=Path(bpy.path.abspath(texture.image.filepath)).resolve()
                links=material.node_tree.links
                alpha_link=any(link.from_node==texture and link.from_socket.name=='Alpha' and link.to_node.type=='MIX_SHADER' and link.to_socket==link.to_node.inputs[0] for link in links)
                correct_path=path==(H/row['asset']).resolve()
                materials_ok |= correct_path and alpha_link and texture.extension=='CLIP' and texture.image.alpha_mode=='STRAIGHT' and material.surface_render_method=='BLENDED'
                width,height=texture.image.size
                corners=[float(texture.image.pixels[4*(y*width+x)+3]) for x,y in ((0,0),(width-1,0),(width-1,height-1),(0,height-1))]
                checks['registered_canvas_dimensions']=list(texture.image.size)==row['canvas']
                checks['transparent_canvas_corners']=max(corners)<.001
                images.append(dict(path=texture.image.filepath,size=list(texture.image.size),expected_canvas=row['canvas'],alpha_corners=corners))
        checks['registered_straight_alpha_material']=materials_ok
        cards.append(dict(object=obj.name,id=row['id'],passed=all(checks.values()),checks=checks,max_quad_position_error_metres=geometry_error,reference_contact_screen_error=anchor_error,authored_foot_anchor_source_error_metres=source_anchor_error,images=images))
    stationary_error=0
    for frame in FRAMES:
        scene.frame_set(int(frame),subframe=frame%1)
        inverse=world.matrix_world.inverted()
        for obj in objects:
            matrix=inverse@obj.matrix_world
            stationary_error=max(stationary_error,max(abs(matrix[a][b]-initial[obj.name][a][b]) for a in range(4) for b in range(4)))
    return dict(present=True,passed=len(cards)==len(expected)==2 and all(row['passed'] for row in cards) and stationary_error<.001,cards=cards,max_source_stationary_error=stationary_error,
                registration='Guide layout merged with artwork-registration.json overlay, matching build_ship_cards. Authored opaque foot anchor; not exact pixel correspondence with retained mesh guide.',
                evidence_scope='Fixed source-space parallax quads and one authored foot anchor per card; other painted supports, shadow contact and disocclusion remain rendered visual QA.')

def verify_air_cards(scene,world,objects):
    """Check actual independent airborne motion without any foot-contact claim."""
    registration=json.loads((H/'airtraffic-registration.json').read_text(encoding='utf-8'))
    expected={row['ship']:row for row in registration['ships']}
    guides=json.loads((H/'ship-card-layout.json').read_text(encoding='utf-8'))
    guide_names={row['id']:row['guide_meshes'] for row in guides['ships']}
    cards=[];materials=set();textures=set();failures=[]
    for obj in objects:
        row=expected.get(obj.get('ship_card_id'))
        if row is None or 'airtraffic_contract' not in obj:
            cards.append(dict(object=obj.name,passed=False,error='Missing airborne contract'));continue
        checks=dict(visible=not obj.hide_render,source_world_parent=obj.parent==world,
                    one_quad_four_vertices=len(obj.data.vertices)==4 and len(obj.data.polygons)==1 and len(obj.data.polygons[0].vertices)==4,
                    actual_contract_matches_retained_registration=json.loads(obj['airtraffic_contract'])==row)
        right=Vector(row['source_right'])*row['width']/2;up=Vector(row['source_up'])*row['height']/2
        expected_local=[-right-up,right-up,right+up,-right+up]
        checks['quad_dimensions_match_contract']=len(obj.data.vertices)==4 and max((v.co-p).length for v,p in zip(obj.data.vertices,expected_local))<.002
        uv=obj.data.uv_layers.active
        checks['unit_canvas_uvs']=uv is not None and len(uv.data)==4 and all((uv.data[i].uv-Vector(pair)).length<1e-6 for i,pair in enumerate(((0,0),(1,0),(1,1),(0,1))))
        image_rows=[];shader_ok=False
        for mat in obj.data.materials:
            if mat is None or mat.node_tree is None:continue
            materials.add(mat.name)
            for node in mat.node_tree.nodes:
                if node.type!='TEX_IMAGE' or node.image is None:continue
                image=node.image;textures.add(image.name)
                texture_matches=Path(bpy.path.abspath(image.filepath)).resolve()==(H/'assets'/Path(row['texture']).name).resolve()
                alpha_link=any(link.from_node==node and link.from_socket.name=='Alpha' and link.to_node.type=='MIX_SHADER' and link.to_socket==link.to_node.inputs[0] for link in mat.node_tree.links)
                shader_ok |= texture_matches and alpha_link and node.extension=='CLIP' and image.alpha_mode=='STRAIGHT' and mat.surface_render_method=='BLENDED'
                width,height=image.size
                corners=[float(image.pixels[4*(y*width+x)+3]) for x,y in ((0,0),(width-1,0),(width-1,height-1),(0,height-1))]
                checks['registered_canvas_dimensions']=list(image.size)==row['canvas']
                checks['transparent_canvas_corners']=max(corners)<.001
                image_rows.append(dict(path=image.filepath,size=list(image.size),alpha_corners=corners))
        checks['registered_straight_alpha_material']=shader_ok
        checks['rough_geometry_guides_hidden']=all(name in bpy.data.objects and bpy.data.objects[name].hide_render for name in guide_names[row['ship']])
        scene.frame_set(400)
        initial=world.matrix_world.inverted()@obj.matrix_world
        max_motion_error=0;max_orientation_error=0;minimum_height=float('inf');minimum_clearance=float('inf');nearest_obstacle=None;sample_bounds=[]
        for frame in FRAMES:
            scene.frame_set(int(frame),subframe=frame%1)
            inverse=world.matrix_world.inverted();matrix=inverse@obj.matrix_world
            factor=(frame-row['source_frames'][0])/(row['source_frames'][1]-row['source_frames'][0])
            expected_position=Vector(row['start_source']).lerp(Vector(row['end_source']),factor)
            max_motion_error=max(max_motion_error,(matrix.translation-expected_position).length)
            max_orientation_error=max(max_orientation_error,max(abs(matrix[a][b]-initial[a][b]) for a in range(3) for b in range(3)))
            points=[matrix@v.co for v in obj.data.vertices]
            lo=[min(p[i] for p in points) for i in range(3)];hi=[max(p[i] for p in points) for i in range(3)]
            minimum_height=min(minimum_height,lo[2])
            for obstacle in scene.objects:
                if obstacle.type!='MESH' or obstacle.hide_render or not obstacle.name.startswith('DISTRICT | '):continue
                obpoints=[inverse@obstacle.matrix_world@Vector(corner) for corner in obstacle.bound_box]
                oblo=[min(p[i] for p in obpoints) for i in range(3)];obhi=[max(p[i] for p in obpoints) for i in range(3)]
                if all(lo[i]<obhi[i] and hi[i]>oblo[i] for i in range(2)):
                    clearance=lo[2]-obhi[2]
                    if clearance<minimum_clearance:minimum_clearance=clearance;nearest_obstacle=obstacle.name
            projected=[world_to_camera_view(scene,bpy.data.objects[CAMERA],obj.matrix_world@v.co) for v in obj.data.vertices]
            bounds=[min(p.x for p in projected),max(p.x for p in projected),min(p.y for p in projected),max(p.y for p in projected)]
            visible=all(p.z>0 for p in projected) and bounds[0]>=.112 and bounds[1]<=.865 and bounds[2]>=.175 and bounds[3]<=.965
            if not visible:failures.append(dict(ship=row['ship'],frame=frame,reason='outside conservative clear window aperture',bounds=bounds))
            sample_bounds.append(dict(frame=frame,bounds=bounds,source_bottom_z=lo[2]))
        checks['linear_source_motion_all96']=max_motion_error<.002
        checks['fixed_source_orientation_all96']=max_orientation_error<1e-5
        checks['full_quad_positive_source_altitude']=minimum_height>0
        checks['above_overlapped_district_bounds']=minimum_clearance>0
        checks['full_quad_in_clear_window_all96']=not any(f['ship']==row['ship'] for f in failures)
        cards.append(dict(object=obj.name,id=row['ship'],passed=all(checks.values()),checks=checks,images=image_rows,
            max_linear_motion_error_metres=max_motion_error,max_source_orientation_error=max_orientation_error,
            min_full_quad_source_height_metres=minimum_height,min_conservative_district_clearance_metres=None if minimum_clearance==float('inf') else minimum_clearance,
            nearest_overlapped_obstacle=nearest_obstacle,samples=sample_bounds))
    return dict(present=True,representation='Independent airborne source-motion cards',passed=len(cards)==2 and len(materials)==2 and len(textures)==2 and all(row['passed'] for row in cards),
                cards=cards,independent_materials=len(materials),independent_textures=len(textures),window_failures=failures,ground_contact_claim=False,
                limits='Conservative full-quad bounds including transparent margin versus visible DISTRICT source-space AABBs at96 samples; no continuous-time collision or general alpha sorting proof. Window aperture is conservative normalized rectangle[.112,.865,.175,.965]. No landing/support contact claim.')

def verify_architecture(scene, world):
    """Check optional fixed source-camera projector in the actual saved scene."""
    name='ARCHITECTURE FINISH | fixed source camera 424'
    projector=bpy.data.objects.get(name)
    if projector is None:return dict(present=False,passed=True)
    scene.frame_set(424)
    camera=bpy.data.objects[CAMERA]
    camera_error=max(abs(projector.matrix_world[a][b]-camera.matrix_world[a][b]) for a in range(4) for b in range(4))
    initial=world.matrix_world.inverted()@projector.matrix_world
    stationary_error=0
    for frame in FRAMES:
        scene.frame_set(int(frame),subframe=frame%1)
        current=world.matrix_world.inverted()@projector.matrix_world
        stationary_error=max(stationary_error,max(abs(current[a][b]-initial[a][b]) for a in range(4) for b in range(4)))
    projected_objects=[];images=[];foreign_objects=[]
    for obj in scene.objects:
        if obj.type!='MESH':continue
        used={p.material_index for p in obj.data.polygons}
        projected=False
        for index,mat in enumerate(obj.data.materials):
            if index not in used or mat is None or mat.node_tree is None:continue
            if not any(node.type=='TEX_COORD' and node.object==projector for node in mat.node_tree.nodes):continue
            projected=True
            images.extend(node.image for node in mat.node_tree.nodes if node.type=='TEX_IMAGE' and node.image is not None and 'architecture' in node.image.name.lower())
        if projected:
            projected_objects.append(obj.name)
            if not obj.name.startswith('DISTRICT | '):foreign_objects.append(obj.name)
    checks=dict(parented_to_source_world=projector.parent==world,reference_424_camera_matrix_matches=camera_error<.001,
                fixed_source_transform_all96=stationary_error<.001,only_district_material_assignments=not foreign_objects,
                projected_image_assigned=bool(images) and bool(projected_objects))
    image_records=[dict(name=im.name,path=im.filepath,relative=im.filepath.startswith('//'),exists=Path(bpy.path.abspath(im.filepath)).exists()) for im in set(images)]
    checks['projected_images_relative_and_present']=all(row['relative'] and row['exists'] for row in image_records)
    return dict(present=True,passed=all(checks.values()),checks=checks,projector=name,reference_source_frame=424,
                max_reference_camera_matrix_error=camera_error,max_source_stationary_error=stationary_error,
                projected_object_count=len(projected_objects),foreign_projected_objects=foreign_objects,images=image_records,
                limits='Actual fixed-projector transform/material assignment only; projected pixel registration, guard transitions, occlusion and new face reveals require rendered endpoint/motion review.')

def main():
    path=H/'port-refinement.blend'
    source=H.parent/'port-approach-realism/port-approach-realism.blend'
    manifest=json.loads((H/'scene-manifest.json').read_text(encoding='utf-8'))
    scene_digest=hashlib.sha256(path.read_bytes()).hexdigest()
    bpy.ops.wm.open_mainfile(filepath=str(path))
    scene=bpy.context.scene
    scene.frame_set(400)
    checks={}
    checks['saved_camera_is_retained_port_camera']=scene.camera is bpy.data.objects[CAMERA]
    checks['no_camera_marker_override']=all(marker.camera is None or marker.camera==scene.camera for marker in scene.timeline_markers)
    image_records=[dict(name=im.name,path=im.filepath,relative=im.filepath.startswith('//'),exists=Path(bpy.path.abspath(im.filepath)).exists()) for im in bpy.data.images if im.source=='FILE' and im.filepath]
    checks['all_file_images_exist']=all(row['exists'] for row in image_records)
    checks['all_file_images_relative']=all(row['relative'] for row in image_records)
    root=bpy.data.objects[ROOT]
    final_meshes={obj.name:mesh_hash(obj) for obj in root.children_recursive if obj.type=='MESH'}
    final_assembly={obj.name:matrix_rows(obj.matrix_local) for obj in root.children_recursive if obj.type=='MESH'}
    final_camera={key:getattr(bpy.data.objects[CAMERA].data,key) for key in ('lens','sensor_width','sensor_height','sensor_fit','shift_x','shift_y','clip_start','clip_end')}
    samples=sample_transforms(scene)
    ground=verify_ground(scene)
    checks['actual_saved_ground_passes']=ground['passed']
    try:
        traffic=validate_traffic(scene,bpy.data.objects[WORLD],manifest['traffic']['vehicles'],manifest['layout'])
        # Keep the report compact; all samples were recomputed from saved meshes.
        traffic=dict(status=traffic['status'],sample_count=traffic['sample_count'],vehicle_count=len(traffic['vehicles']),failures=traffic['failures'],clearance_method=traffic['clearance_method'])
        checks['actual_saved_traffic_passes']=traffic['status']=='PASS'
    except Exception as error:
        traffic=dict(status='FAIL',error=str(error))
        checks['actual_saved_traffic_passes']=False
    parked=parked_contacts(scene,bpy.data.objects[WORLD])
    cards=verify_cards(scene,bpy.data.objects[WORLD])
    if cards.get('ground_contact_claim') is False:
        parked['evidence_scope']='Rejected grounded-study hidden guides only; no acceptance evidence for airborne cards.'
    else:checks['parked_geometry_guide_contacts_pass']=parked['passed']
    checks['actual_saved_ship_card_contracts_pass']=cards['passed'] and (not manifest.get('ship_cards') or cards['present'])
    architecture=verify_architecture(scene,bpy.data.objects[WORLD])
    checks['actual_saved_architecture_projector_pass']=architecture['passed'] and (not (manifest.get('architecture') or {}).get('applied') or architecture['present'])

    bpy.ops.wm.open_mainfile(filepath=str(source))
    reference=bpy.context.scene
    source_root=bpy.data.objects[ROOT]
    source_meshes={obj.name:mesh_hash(obj) for obj in source_root.children_recursive if obj.type=='MESH'}
    checks['171_ship_meshes_compared']=len(final_meshes)==len(source_meshes)==171
    checks['ship_mesh_geometry_identical_to_retained_source']=final_meshes==source_meshes
    source_assembly={obj.name:matrix_rows(obj.matrix_local) for obj in source_root.children_recursive if obj.type=='MESH'}
    checks['ship_component_local_assembly_unchanged']=final_assembly==source_assembly
    source_camera={key:getattr(bpy.data.objects[CAMERA].data,key) for key in final_camera}
    checks['camera_intrinsics_match_retained_source']=final_camera==source_camera
    missing=sorted(set(source_meshes)-set(final_meshes));added=sorted(set(final_meshes)-set(source_meshes))
    changed=sorted(name for name in set(final_meshes)&set(source_meshes) if final_meshes[name]!=source_meshes[name])
    errors={CAMERA:0,WORLD:0,ROOT:0}
    for sample in samples:
        frame=sample['source_frame'];reference.frame_set(int(frame),subframe=frame%1)
        for name in errors:
            matrix=bpy.data.objects[name].matrix_world
            errors[name]=max(errors[name],max(abs(matrix[a][b]-sample['matrices'][name][a][b]) for a in range(4) for b in range(4)))
    for name,label in ((CAMERA,'camera'),(WORLD,'inverse_world'),(ROOT,'ship_root')):
        checks['all_96_'+label+'_transforms_match_source']=errors[name]<1e-6
    report=dict(passed=all(checks.values()),checks=checks,scene_sha256=scene_digest,source_scene='../port-approach-realism/port-approach-realism.blend',source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        timing='Source frames 400+0.5i at12fps, i=0..95; actual half-frame animation sampled for24fps output',
        sample_count=len(samples),ship_meshes_compared=len(final_meshes),ship_geometry_differences=dict(missing=missing,added=added,changed=changed),max_matrix_errors=errors,
        camera_intrinsics=final_camera,images=image_records,ground=ground,traffic=traffic,parked_supports=parked,ship_cards=cards,architecture=architecture,
        limits=['Finite sampled animation, ground and conservative traffic envelopes; no continuous-time collision proof.',
                'Ground helper covers one rectangular precinct with rectangular material cells; unrelated untagged overlays and sub-sample holes require visual review.',
                'Parked support checks cover16 authored cradle/platform geometry feet; when hidden as card guides these do not establish visible support contact. Card grounding requires rendered QA.',
                'Shader appearance, close occlusion, actual encoded movie cadence and playback require separate rendered review.'])
    (H/'review').mkdir(exist_ok=True)
    (H/'review/scene-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(passed=report['passed'],checks=checks,scene_sha256=scene_digest,max_matrix_errors=errors)),flush=True)
    if not report['passed']:raise RuntimeError('Saved refined scene validation failed; see review/scene-validation.json')

if __name__=='__main__':main()
