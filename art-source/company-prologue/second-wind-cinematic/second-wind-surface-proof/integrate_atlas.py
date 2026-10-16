"""Install cropped tile albedos, pack images, and render a bounded material motion test.

Requires assets/{cream,oxide,gunmetal,backing}.png. Retains original generated atlas
outside the blend as provenance. Execute after build_proof.py in background Blender.
"""
from pathlib import Path
import bpy,json,sys
from mathutils import Vector
OUT=Path(__file__).resolve().parent
BASELINES=OUT/'build'/'baselines';BASELINES.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(BASELINES/'second-wind-surface-proof.blend'))
scene=bpy.context.scene
roles=[]
for role in ('cream','oxide','gunmetal','backing'):
    path=OUT/'assets'/(role+'.png')
    if not path.exists():raise FileNotFoundError(path)
    m=bpy.data.materials['ARRIVAL | '+role]; n=m.node_tree.nodes; l=m.node_tree.links
    p=n.get('Principled BSDF'); coord=next(x for x in n if x.bl_idname=='ShaderNodeVectorMath')
    tex=n.new('ShaderNodeTexImage'); tex.name='Registered neutral '+role+' tile'; tex.image=bpy.data.images.load(str(path)); tex.image.pack()
    tex.projection='BOX'; tex.projection_blend=.23; tex.extension='REPEAT'
    l.new(coord.outputs[0],tex.inputs['Vector']); l.new(tex.outputs['Color'],p.inputs['Base Color'])
    # Albedo does not drive normal: fine procedural bump supplies restrained microstructure.
    roles.append({'role':role,'file':str(path),'dimensions':list(tex.image.size),'box_blend':tex.projection_blend,'repeat_hull_units':6})
root=bpy.data.objects['SECOND WIND | flight root']; mapping=bpy.data.objects['SURFACE | common rigid hull coordinate system']
scene.camera=bpy.data.objects['PROOF | port side landing surface']
scene.render.film_transparent=False
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.32,.40,.49,1)
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(BASELINES/'second-wind-textured-proof.blend'))
frames=OUT/'build'/'obsolete'/'motion-frames';frames.mkdir(parents=True,exist_ok=True)
positions=[]
for f in range(1,50):
    scene.frame_set(f)
    # Numerical attachment check at an original mesh vertex in all motion frames.
    hull=bpy.data.objects['Main hull - level datum Z0']
    local=mapping.matrix_world.inverted() @ (hull.matrix_world @ hull.data.vertices[0].co)
    positions.append(list(local))
    if '--render' in sys.argv:
        scene.render.filepath=str(frames/f'{f:04d}.png');bpy.ops.render.render(write_still=True)
drift=max((Vector(p)-Vector(positions[0])).length for p in positions)
assert drift<.0001,drift
report={'status':'Textured rigid movement proof, not completed landing','roles':roles,'frames':49,'fps':24,'duration_seconds':49/24,
        'review_resolution':[1920,1080],'common_coordinate_max_drift_hull_units':drift,
        'note':'Albedo is box mapped from common ship-child Object coordinates. No generated multiview ship images projected. Coarse sacrificial damage geometry remains preparation.'}
(BASELINES/'texture-registration.json').write_text(json.dumps(report,indent=2))
if '--render' in sys.argv:
    for tag,cam in [('textured-port-side','PROOF | port side landing surface'),('textured-port-quarter','PROOF | port bow quarter')]:
        scene.frame_set(1);scene.camera=bpy.data.objects[cam];scene.render.filepath=str(OUT/(tag+'.png'));bpy.ops.render.render(write_still=True)
