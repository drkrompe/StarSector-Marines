"""Construction evidence for transferred meshes, transforms and attached surfaces."""
from pathlib import Path
import bpy,json,hashlib,struct,array
OUT=Path(__file__).resolve().parent
SOURCE=OUT.parent/'animatic/jangala-blockout.blend';FINAL=OUT/'second-wind-daylight-landing-proof.blend'
FRAMES=tuple(720+i*.5 for i in range(96))
def setframe(frame):bpy.context.scene.frame_set(int(frame),subframe=frame%1)
def meshhash(ob):
    h=hashlib.sha256()
    for v in ob.data.vertices:h.update(struct.pack('<3f',*v.co))
    for p in ob.data.polygons:h.update(struct.pack('<%di'%len(p.vertices),*p.vertices))
    return h.hexdigest()
def matrix(ob):return [v for row in ob.matrix_world for v in row]
bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
root=bpy.data.objects['SECOND WIND | flight root'];names=[o.name for o in root.children_recursive if o.type=='MESH']
hashes={name:meshhash(bpy.data.objects[name]) for name in names}
source={}
for frame in FRAMES:
    setframe(frame)
    source[frame]={name:matrix(bpy.data.objects[name]) for name in names+['06 LANDING / support review']}
bpy.ops.wm.open_mainfile(filepath=str(FINAL))
scene=bpy.context.scene
assert all(meshhash(bpy.data.objects[name])==hashes[name] for name in names)
maxerror=0
uvmeshes=[o for o in bpy.data.objects if o.type=='MESH' and o.data.uv_layers.get('SideGuideUV')]
def uvhash():
    h=hashlib.sha256()
    for o in sorted(uvmeshes,key=lambda o:o.name):
        layer=o.data.uv_layers['SideGuideUV'];data=array.array('f',[0])*(len(layer.data)*2)
        layer.data.foreach_get('uv',data);h.update(data.tobytes())
    return h.hexdigest()
uvbaseline=uvhash();uvmatch=True;contact=[];local_baseline={};local_motion=set();posehash=hashlib.sha256();sourceposehash=hashlib.sha256();poses=[]
def bounds(ob):
    ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh=ev.to_mesh()
    points=[ev.matrix_world@v.co for v in mesh.vertices];ev.to_mesh_clear()
    return [(min(p[i] for p in points),max(p[i] for p in points)) for i in range(3)]
shoes=[o for o in bpy.data.objects if o.name.startswith('PROPOSAL keel shoe')]
pads=[o for o in bpy.data.objects if o.name.startswith('YARD_contact_pad_')]
assert len(shoes)==len(pads)==4
for frame in FRAMES:
    setframe(frame)
    for name in source[frame]:maxerror=max(maxerror,max(abs(a-b) for a,b in zip(source[frame][name],matrix(bpy.data.objects[name]))))
    for name in sorted(source[frame]):
        posehash.update(struct.pack('<16f',*matrix(bpy.data.objects[name])));sourceposehash.update(struct.pack('<16f',*source[frame][name]))
    poses.append({'frame':frame,'root_matrix':matrix(bpy.data.objects['SECOND WIND | flight root']),'camera_matrix':matrix(bpy.data.objects['06 LANDING / support review'])})
    for ob in bpy.data.objects['SECOND WIND | flight root'].children_recursive:
        if ob.type!='MESH':continue
        local=[v for row in ob.matrix_local for v in row]
        if ob.name not in local_baseline:local_baseline[ob.name]=local
        elif max(abs(a-b) for a,b in zip(local,local_baseline[ob.name]))>.00001:local_motion.add(ob.name)
    uvmatch=uvmatch and uvhash()==uvbaseline
    for shoe in shoes:
        sb=bounds(shoe);pad=min(pads,key=lambda p:(p.matrix_world.translation.x-sum(sb[0])/2)**2+(p.matrix_world.translation.y-sum(sb[1])/2)**2)
        pb=bounds(pad);gap=sb[2][0]-pb[2][1]
        assert gap>=-.025,(frame,shoe.name,gap)
        if frame>=744:
            assert abs(gap)<.025,(frame,shoe.name,gap)
            assert all(sb[i][0]>=pb[i][0]-.025 and sb[i][1]<=pb[i][1]+.025 for i in (0,1))
        contact.append({'frame':frame,'shoe':shoe.name,'pad':pad.name,'gap_m':gap})
assert maxerror<.00001,maxerror
assert uvmatch
packed=[{'name':im.name,'dimensions':list(im.size),'packed':bool(im.packed_file),'filepath':im.filepath,'usable':bool(im.packed_file) or Path(bpy.path.abspath(im.filepath)).is_file()} for im in bpy.data.images if '.png' in im.name]
assert all(im['usable'] for im in packed)
assert all(im['packed'] or Path(bpy.path.abspath(im['filepath'])).is_file() for im in packed)
surfaced_motion=[name for name in local_motion if any(m and m.name.startswith('ARRIVAL |') for m in bpy.data.objects[name].data.materials)]
expected_lifts={f'LIFT VECTOR | placeholder{suffix}' for suffix in ('','.001','.002','.003')}
assert set(surfaced_motion)==expected_lifts,('Expected exactly four locally animated surfaced lift placeholders',surfaced_motion)
for name in surfaced_motion:
    ob=bpy.data.objects[name]
    assert all(node.object==ob for m in ob.data.materials if m and m.name.startswith('ARRIVAL |') for node in m.node_tree.nodes if node.bl_idname=='ShaderNodeTexCoord'),name
report={'status':'PASS for construction invariants, not artistic acceptance or full collision simulation',
 'source_blend':str(SOURCE),'source_blend_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
 'final_blend':str(FINAL),'final_blend_sha256':hashlib.sha256(FINAL.read_bytes()).hexdigest(),
 'retained_meshes':len(names),'all_original_vertex_polygon_hashes_match':True,'source_camera_and_ship_transform_max_element_error':maxerror,
 'retained_mesh_hashes':hashes,
 'source_frames_compared':FRAMES,'side_projection_uv_meshes':len(uvmeshes),'uv_mapping':'Stored per mesh; rigid movement leaves UV values unchanged',
 'side_uv_hash_static_all_96_samples':uvmatch,'side_uv_sha256':uvbaseline,'shoe_pad_samples':contact,
 'source_pose_sha256':sourceposehash.hexdigest(),'final_pose_sha256':posehash.hexdigest(),'root_camera_poses':poses,
 'locally_animated_meshes':sorted(local_motion),'locally_animated_surfaced_meshes':surfaced_motion,
 'mapping_scope':'Four locally animated lift placeholders use individual component-local atlas coordinates. Hull and hardware remain rigid; their atlas coordinates attach to ship root. Side UVs attach to each mesh.',
 'mapping_empty_parent':bpy.data.objects['SURFACE | common rigid hull coordinate system'].parent.name,
 'images':packed,'render_resolution':[scene.render.resolution_x,scene.render.resolution_y],'render_percentage':scene.render.resolution_percentage,
 'review_limits':'Near-side single-view texture with partially baked lighting; atlas fallback elsewhere. Generalized plate hierarchy and original simplified hardpoints; greybox yard. No finished cinematic claim.'}
(OUT/'verification.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k not in ('images','shoe_pad_samples','source_frames_compared','root_camera_poses','retained_mesh_hashes')}))
