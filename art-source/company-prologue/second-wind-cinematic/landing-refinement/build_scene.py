"""Assemble the opposite-side landing from retained ship and port assets."""
from pathlib import Path
import sys,json,hashlib,os
import bpy
from mathutils import Vector
OUT=Path(__file__).resolve().parent
sys.path.insert(0,str(OUT))
from ship_view import configure_ship_view,apply_starboard_surface
from yard_environment import apply_yard
from thruster_fx import apply_thruster_fx
from render_config import configure_render_device
SOURCE=OUT.parent/'second-wind-surface-proof/second-wind-daylight-landing-proof.blend'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
    scene=bpy.context.scene
    configure_ship_view(scene)
    surface=apply_starboard_surface(scene,OUT/'assets/starboard-surface-v1.png')
    yard=apply_yard(scene)
    fx=apply_thruster_fx(scene)
    # Broad daylight illuminates the newly visible side, while retaining the
    # source directional light. Environment art is unchanged across the take.
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=.8
    scene.view_settings.exposure=.15
    lamp=bpy.data.lights.new('LANDING LIGHT | starboard sky bounce','AREA')
    lamp.energy=200000;lamp.shape='DISK';lamp.size=180
    ob=bpy.data.objects.new(lamp.name,lamp);scene.collection.objects.link(ob)
    ob.location=(20,-150,130)
    ob.rotation_euler=(Vector((0,0,25))-ob.location).to_track_quat('-Z','Y').to_euler()
    scene.render.engine='CYCLES';scene.cycles.samples=32;scene.cycles.use_denoising=True
    scene.render.use_persistent_data=True
    device=configure_render_device(scene)
    scene.render.resolution_x=1920;scene.render.resolution_y=1080;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
    scene.frame_set(744);configure_ship_view(scene)
    (OUT/'build').mkdir(exist_ok=True)
    (OUT/'review').mkdir(exist_ok=True)
    if '--yard-finish' in sys.argv:
        from yard_finish import apply_yard_finish
        apply_yard_finish(scene,OUT/'assets/yard-finish-v1.png')
    scene.frame_set(744)
    scene.render.filepath=str(OUT/'review/contact.png')
    # External authoring assets remain repository-relative when the worktree moves.
    for image in bpy.data.images:
        if image.source=='FILE' and image.filepath and not image.packed_file:
            resolved=Path(bpy.path.abspath(image.filepath))
            if resolved.is_file():image.filepath='//'+Path(os.path.relpath(resolved,OUT)).as_posix()
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'landing-refinement.blend'),relative_remap=False)
    report={'source_scene':str(SOURCE.relative_to(OUT.parent)),'source_sha256':sha(SOURCE),
      'port_source':'../port-refinement/port-refinement.blend','port_sha256':sha(OUT.parent/'port-refinement/port-refinement.blend'),
      'camera':scene.camera.name,'surface':surface,'yard':yard,'fx_collection':fx.name,'render_device':device,
      'render':[1920,1080],'output_duration_seconds':8,'output_fps':24,
      'retiming_knots':[[0,580],[2,624],[2.5,648],[6.5,744],[8,774]],
      'retiming_note':'Explicit edit retime; original actions evaluated at mapped source times. No horizontal drift introduced after source624.'}
    (OUT/'scene-manifest.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8', newline='\n')
    if '--preview' in sys.argv:
        for tag,frame in [('start',580),('hold',648),('contact',744),('settled',774)]:
            scene.frame_set(frame);scene.render.filepath=str(OUT/'review'/f'{tag}.png')
            bpy.ops.render.render(write_still=True)
    if '--yard-guide' in sys.argv:
        root=bpy.data.objects['SECOND WIND | flight root']
        for ob in [root,*root.children_recursive]:
            if ob.type=='MESH':ob.hide_render=True
        for ob in scene.objects:
            if ob.name.startswith(('LANDING FX |','PORT TRAFFIC |')):ob.hide_render=True
        scene.frame_set(744)
        scene.render.filepath=str(OUT/'guides/yard-contact-guide.png')
        bpy.ops.render.render(write_still=True)
if __name__=='__main__':main()
