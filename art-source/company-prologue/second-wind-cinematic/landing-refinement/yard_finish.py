"""Fixed-camera surface finish on reused static geometry; dynamic hull/FX excluded."""
from pathlib import Path
import bpy,json,hashlib
OUT=Path(__file__).resolve().parent
def apply_yard_finish(scene,path):
    path=Path(path);image=bpy.data.images.load(str(path.resolve()),check_existing=True);image.pack()
    cam=scene.camera;scene.frame_set(744);bpy.context.view_layer.update()
    projector=bpy.data.objects.new('YARD FINISH | immutable starboard camera',None)
    scene.collection.objects.link(projector);projector.matrix_world=cam.matrix_world.copy()
    frame=cam.data.view_frame(scene=scene)
    hx=max(abs(v.x/v.z) for v in frame);hy=max(abs(v.y/v.z) for v in frame)
    cache={};objects=[]
    def finished(original):
        if original in cache:return cache[original]
        m=original.copy();m.name='YARD FINISH | '+original.name
        n,l=m.node_tree.nodes,m.node_tree.links
        co=n.new('ShaderNodeTexCoord');co.object=projector
        sep=n.new('ShaderNodeSeparateXYZ');l.new(co.outputs['Object'],sep.inputs[0])
        def math(op,a,b):
            q=n.new('ShaderNodeMath');q.operation=op
            for i,v in enumerate((a,b)):
                if isinstance(v,(float,int)):q.inputs[i].default_value=v
                else:l.new(v,q.inputs[i])
            return q.outputs[0]
        depth=math('MULTIPLY',sep.outputs['Z'],-1)
        u=math('ADD',math('MULTIPLY',math('DIVIDE',sep.outputs['X'],depth),.5/hx),.5)
        v=math('ADD',math('MULTIPLY',math('DIVIDE',sep.outputs['Y'],depth),.5/hy),.5)
        valid=math('GREATER_THAN',depth,.1)
        for gate in (math('GREATER_THAN',u,0),math('LESS_THAN',u,1),math('GREATER_THAN',v,0),math('LESS_THAN',v,1)):
            valid=math('MULTIPLY',valid,gate)
        uv=n.new('ShaderNodeCombineXYZ');l.new(u,uv.inputs[0]);l.new(v,uv.inputs[1])
        tex=n.new('ShaderNodeTexImage');tex.image=image;tex.extension='CLIP';l.new(uv.outputs[0],tex.inputs['Vector'])
        sockets=[q.inputs['Base Color'] for q in n if q.type=='BSDF_PRINCIPLED']
        if not sockets:sockets=[q.inputs['Color'] for q in n if q.type=='EMISSION']
        for socket in sockets:
            prior=socket.links[0].from_socket if socket.links else None
            mix=n.new('ShaderNodeMixRGB');l.new(valid,mix.inputs[0]);l.new(tex.outputs['Color'],mix.inputs[2])
            if prior:l.new(prior,mix.inputs[1])
            else:mix.inputs[1].default_value=socket.default_value
            l.new(mix.outputs[0],socket)
        m['coverage_limit']='Fixed-camera static scenery finish; original shader beyond guide aperture. Generated fine details not all modeled.'
        cache[original]=m;return m
    ship=bpy.data.objects['SECOND WIND | flight root'];shipobjects=set(ship.children_recursive)
    for ob in scene.objects:
        if ob.hide_render or ob.type!='MESH' or ob in shipobjects:continue
        if ob.name.startswith(('LANDING FX |','PORT TRAFFIC |','YARD_clamp_slide_','YARD_retaining_wedge_')):continue
        if not ob.name.startswith(('DISTRICT |','LANDING YARD |','YARD_')):continue
        if ob.animation_data:continue
        ob.data=ob.data.copy()
        for i,material in enumerate(ob.data.materials):
            if material and material.use_nodes:ob.data.materials[i]=finished(material)
        objects.append(ob.name)
    report={'image':str(path.relative_to(OUT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'dimensions':list(image.size),
      'guide':'guides/yard-contact-guide.png','camera_matrix':[list(r) for r in cam.matrix_world],
      'registration':'Identity normalized guide-camera coordinates, per-pixel perspective divide; static camera.',
      'applied_objects':objects,'materials':len(cache),'source_geometry':'Reused physical port district, new landing detail, original contact rig.',
      'limits':'Artwork enriches surfaces beyond physical detail; fixed-camera coverage only. Native detail/baked lighting not a complete relightable UV asset. Moving traffic, clamps, ship and exhaust excluded.'}
    (OUT/'yard-finish-registration.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8', newline='\n');return report
