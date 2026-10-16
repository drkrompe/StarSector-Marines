"""Daylight lift and bounded native half-frame descent render; no interpolation."""
from pathlib import Path
import bpy,sys,json
from mathutils import Vector
OUT=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(OUT/'build'/'stages'/'second-wind-projected-landing-proof.blend'))
scene=bpy.context.scene
for ob in bpy.data.objects:
    if ob.type=='MESH' and ob.name.startswith('LIFT VECTOR |'):
        for i,m in enumerate(ob.data.materials):
            if m and m.name.startswith('ARRIVAL |'):
                local=m.copy();local.name=m.name+' | component-local lift';ob.data.materials[i]=local
                for node in local.node_tree.nodes:
                    if node.bl_idname=='ShaderNodeTexCoord':node.object=ob
                local['mapping_scope']='Component-local coordinates for animated lift-placeholder shutdown scale'
scene.world.node_tree.nodes['Background'].inputs[1].default_value=1.5
scene.view_settings.exposure=.65
data=bpy.data.lights.new('Apron diffuse sky bounce','AREA');data.energy=150000;data.shape='DISK';data.size=125
fill=bpy.data.objects.new('Apron diffuse sky bounce',data);scene.collection.objects.link(fill);fill.location=(0,115,8)
fill.rotation_euler=(Vector((0,0,21))-fill.location).to_track_quat('-Z','Y').to_euler()
scene.camera=bpy.data.objects['06 LANDING / support review'];scene.frame_set(744)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'second-wind-daylight-landing-proof.blend'))
scene.render.filepath=str(OUT/'daylight-contact-preview.png');bpy.ops.render.render(write_still=True)
if '--render-motion' in sys.argv:
    frames=OUT/'build'/'landing-frames';frames.mkdir(parents=True,exist_ok=True)
    mapping=[]
    for i in range(96):
        source=720+i*.5;scene.frame_set(int(source),subframe=source%1)
        scene.render.filepath=str(frames/f'{i+1:04d}.png');bpy.ops.render.render(write_still=True)
        mapping.append({'output':i+1,'source_frame':source,'source_fps':12})
    (OUT/'motion-frame-map.json').write_text(json.dumps({'output_fps':24,'frames':mapping,'duration_seconds':4,'interpolation':'No synthesized frames; Blender evaluates original source action at half frames.'},indent=2))
