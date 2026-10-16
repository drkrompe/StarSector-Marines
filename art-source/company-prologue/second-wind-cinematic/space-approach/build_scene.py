"""Fixed-aspect battered Second Wind approaching Jangala; no source mutations."""
from pathlib import Path
import bpy, math, json, hashlib, os, sys, random
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
OUT=Path(__file__).resolve().parent
SOURCE=OUT.parent/'landing-wreck-pass/landing-wreck-pass.blend'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE));s=bpy.context.scene;s.frame_set(744)
    root=s.objects['SECOND WIND | flight root']; keep={root,*root.children_recursive}
    # Retain the imported rigid hierarchy, including its object-local projectors.
    for o in list(s.objects):
        if o not in keep:bpy.data.objects.remove(o,do_unlink=True)
    for o in keep:
        o.animation_data_clear()
        if o.type=='CAMERA' or o.name.startswith(('LANDING FX','LIFT VECTOR','Cloud breakup','ENTRY CLOUD','ENTRY WARM')):o.hide_render=True
    root.location=(0,0,0);root.rotation_euler=(0,0,0)
    # The accepted camera aspect is unchanged; orthographic projection keeps it
    # invariant as the distant ship drifts across the frame.
    bpy.ops.object.camera_add();cam=bpy.context.object;cam.name='SPACE | retained starboard aspect'
    cam.rotation_euler=(1.4234719276428223,0,.08490180224180222)
    cam.data.type='ORTHO';cam.data.ortho_scale=680;cam.data.clip_end=20000
    basis=cam.rotation_euler.to_matrix();right=basis@Vector((1,0,0));up=basis@Vector((0,1,0));back=basis@Vector((0,0,1))
    cam.location=back*1500+right*190-up*90;s.camera=cam;s.timeline_markers.clear()
    for f,x in ((1,-20),(96,45)):
        root.location=(x,0,0);root.keyframe_insert('location',frame=f)
    for layer in root.animation_data.action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for c in bag.fcurves:
                    for k in c.keyframe_points:k.interpolation='LINEAR'
    s.render.engine='CYCLES';s.cycles.samples=24;s.cycles.use_denoising=True
    s.render.resolution_x=1920;s.render.resolution_y=1080;s.render.resolution_percentage=100
    s.render.image_settings.file_format='PNG';s.render.fps=24;s.frame_start=1;s.frame_end=96
    s.world=bpy.data.worlds.new('Space | black');s.world.use_nodes=True;s.world.node_tree.nodes['Background'].inputs['Color'].default_value=(.003,.005,.009,1);s.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.12
    bpy.ops.object.light_add(type='SUN',location=(0,0,500));sun=bpy.context.object;sun.name='SPACE | upper-left sun';sun.data.energy=2;sun.rotation_euler=(.35,-.45,-.5);sun.data.angle=.04
    bpy.ops.object.light_add(type='AREA');fill=bpy.context.object;fill.name='SPACE | reflected planet fill';fill.location=(150,-180,-70);fill.rotation_euler=(Vector((0,0,0))-fill.location).to_track_quat('-Z','Y').to_euler();fill.data.energy=15000;fill.data.color=(.22,.42,.65);fill.data.shape='DISK';fill.data.size=300
    # Full-frame distant emissive plate. The native ImageGen master stays separate.
    plate=OUT/'assets/planet-plate.png'
    if not plate.exists():plate=OUT/'guides/planet-layout.png'
    distance=8000;center=cam.location-back*distance
    mesh=bpy.data.meshes.new('Planet plate frame');w=680;h=w*1080/1920
    verts=[center+right*x+up*y for x,y in ((-w/2,-h/2),(w/2,-h/2),(w/2,h/2),(-w/2,h/2))];mesh.from_pydata(verts,[],[(0,1,2,3)]);uv=mesh.uv_layers.new()
    for loop,co in zip(uv.data,((0,0),(1,0),(1,1),(0,1))):loop.uv=co
    ob=bpy.data.objects.new('SPACE | stable distant planet plate',mesh);s.collection.objects.link(ob)
    mat=bpy.data.materials.new('SPACE | planet unlit plate');mat.use_nodes=True;n=mat.node_tree.nodes;n.clear();tex=n.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(plate));tex.interpolation='Linear';em=n.new('ShaderNodeEmission');out=n.new('ShaderNodeOutputMaterial');mat.node_tree.links.new(tex.outputs['Color'],em.inputs['Color']);mat.node_tree.links.new(em.outputs[0],out.inputs['Surface']);ob.data.materials.append(mat)
    # Sparse stars are independent geometry, never painted into the planet.
    star=bpy.data.materials.new('SPACE | distant stars');star.use_nodes=True;sn=star.node_tree.nodes;sn.clear();se=sn.new('ShaderNodeEmission');se.inputs['Color'].default_value=(.52,.63,.76,1);se.inputs['Strength'].default_value=.65;so=sn.new('ShaderNodeOutputMaterial');star.node_tree.links.new(se.outputs[0],so.inputs['Surface'])
    rng=random.Random(42);sv=[];sf=[]
    for i in range(210):
        px=rng.randrange(1920);py=rng.randrange(1080)
        if math.hypot(px-1530,py-1070)<1184:continue
        pos=cam.location-back*7000+right*((px/1920-.5)*w)+up*((.5-py/1080)*h);rad=rng.uniform(.09,.22);j=len(sv)
        sv.extend([pos+right*a+up*b for a,b in ((-rad,-rad),(rad,-rad),(rad,rad),(-rad,rad))]);sf.append((j,j+1,j+2,j+3))
    sm=bpy.data.meshes.new('Sparse star quads');sm.from_pydata(sv,[],sf);st=bpy.data.objects.new('SPACE | independent sparse stars',sm);s.collection.objects.link(st);sm.materials.append(star)
    # Short transparent main-engine mixing volumes, attached at actual aft mouths.
    for engine in [o for o in root.children_recursive if 'aft-facing nozzle' in o.name]:
        bounds=[engine.matrix_world@Vector(c) for c in engine.bound_box];p=Vector((min(v.x for v in bounds),sum(v.y for v in bounds)/8,sum(v.z for v in bounds)/8))
        bpy.ops.mesh.primitive_uv_sphere_add(segments=24,ring_count=12,location=p+Vector((-6,0,0)));fx=bpy.context.object;fx.name='SPACE | soft aft exhaust '+engine.name;fx.scale=(8,1.8,1.8);world=fx.matrix_world.copy();fx.parent=root;fx.matrix_world=world
        m=bpy.data.materials.new(fx.name);m.use_nodes=True;n=m.node_tree.nodes;n.clear();tc=n.new('ShaderNodeTexCoord');sub=n.new('ShaderNodeVectorMath');sub.operation='SUBTRACT';sub.inputs[1].default_value=(.5,.5,.5);length=n.new('ShaderNodeVectorMath');length.operation='LENGTH';ramp=n.new('ShaderNodeMapRange');ramp.inputs['From Min'].default_value=.06;ramp.inputs['From Max'].default_value=.5;ramp.inputs['To Min'].default_value=1;ramp.inputs['To Max'].default_value=0;vol=n.new('ShaderNodeVolumePrincipled');vol.inputs['Density'].default_value=.015;vol.inputs['Emission Color'].default_value=(.25,.43,.8,1);out=n.new('ShaderNodeOutputMaterial');l=m.node_tree.links;l.new(tc.outputs['Generated'],sub.inputs[0]);l.new(sub.outputs[0],length.inputs[0]);l.new(length.outputs['Value'],ramp.inputs['Value']);l.new(ramp.outputs[0],vol.inputs['Emission Strength']);l.new(vol.outputs[0],out.inputs['Volume']);fx.data.materials.append(m)
    s.view_settings.view_transform='Standard';s.view_settings.look='None';s.view_settings.exposure=0;s.view_settings.gamma=1
    (OUT/'review').mkdir(exist_ok=True);s.frame_set(48)
    for image in bpy.data.images:
        if image.source=='FILE' and image.filepath and not image.packed_file:
            p=Path(bpy.path.abspath(image.filepath))
            if p.exists():image.filepath='//'+Path(os.path.relpath(p,OUT)).as_posix()
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'space-approach.blend'),relative_remap=False)
    report={'source':str(SOURCE),'source_sha256':sha(SOURCE),'frames':96,'fps':24,'duration_seconds':4,'resolution':[1920,1080],'camera':cam.name,'camera_rotation':list(cam.rotation_euler),'ortho_width':680,'plate':str(plate),'planet_layout_px':{'center':[1530,1070],'radius':1170},'root_translation':[[1,-20,0,0],[96,45,0,0]],'ship_geometry':'Unmodified imported damaged hull; prior yard and flight effects removed/hidden.','review_scope':'Scene construction only; render, media and playback reports own review status.'}
    report['scene_sha256']=sha(OUT/'space-approach.blend')
    report['plate_sha256']=sha(plate)
    (OUT/'scene-manifest.json').write_text(json.dumps(report,indent=2)+'\n',newline='\n')
if __name__=='__main__':main()
