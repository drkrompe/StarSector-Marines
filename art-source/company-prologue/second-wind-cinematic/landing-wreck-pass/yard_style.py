"""Return landing scenery to the preceding port's physical material vocabulary.

Apply to the saved landing-refinement scene. This only changes static yard
material slots: the ship, lift, moving clamps, traffic, camera and lights remain
untouched. No images are altered or blurred, and no geometry is added.
"""
from pathlib import Path
import bpy

HERE=Path(__file__).resolve().parent
BASE=HERE.parent
ATLAS=BASE/'port-approach-realism/assets/industrial-materials-v1.png'
PREFIX='YARD CONTINUITY | '
LANDSCAPE_EXCEPTIONS={'LANDING YARD | far forest and hills','LANDING YARD | far daylight sky'}


def apply_yard_style(scene):
    ship=bpy.data.objects['SECOND WIND | flight root']
    protected=set(ship.children_recursive)|{ship}
    ship_materials={ob.name:tuple(m.as_pointer() if m else 0 for m in ob.data.materials)
                    for ob in protected if ob.type=='MESH'}
    restored=[];missing=[];changed=[]
    # Unwrap the later fixed-camera photographic RGB finish. Its unmodified
    # source material remains in the blend, including original district UVs.
    for ob in scene.objects:
        if ob.type!='MESH' or ob in protected or ob.hide_render or ob.name in LANDSCAPE_EXCEPTIONS:continue
        if ob.name.startswith(('LANDING FX |','PORT TRAFFIC |','YARD_clamp_slide_','YARD_retaining_wedge_')):continue
        if not ob.name.startswith(('DISTRICT |','LANDING YARD |','YARD_')):continue
        for i,mat in enumerate(ob.data.materials):
            if mat and mat.name.startswith('YARD FINISH | '):
                original_name=mat.name.removeprefix('YARD FINISH | ')
                original=bpy.data.materials.get(original_name)
                if original is None:
                    missing.append(dict(object=ob.name,material=mat.name));continue
                ob.data.materials[i]=original
                restored.append(dict(object=ob.name,slot=i,from_material=mat.name,to_material=original.name))
        changed.append(ob.name)
    if missing:raise RuntimeError('Missing retained yard fallback materials: '+str(missing))

    fixed_root=bpy.data.objects['LANDING YARD | fixed port district']
    atlas=bpy.data.images.load(str(ATLAS),check_existing=True)
    def ground_material(name,tint=1,apron=False):
        # Keep the original berth's safety paint graph, but replace its base
        # and extra seam layer with the same20m concrete tile used by the port.
        original=bpy.data.materials['LANDING YARD | exposed aggregate concrete']
        mat=original.copy() if apron else bpy.data.materials.new(PREFIX+name)
        mat.name=PREFIX+name;mat.use_nodes=True
        n,l=mat.node_tree.nodes,mat.node_tree.links
        shader=n.get('Principled BSDF');shader.inputs['Metallic'].default_value=0
        shader.inputs['Roughness'].default_value=.82
        co=n.new('ShaderNodeTexCoord');co.object=fixed_root
        scale=n.new('ShaderNodeVectorMath');scale.operation='SCALE';scale.inputs[3].default_value=1/20
        l.new(co.outputs['Object'],scale.inputs[0])
        wrap=n.new('ShaderNodeVectorMath');wrap.operation='FRACTION';l.new(scale.outputs[0],wrap.inputs[0])
        tile=n.new('ShaderNodeVectorMath');tile.operation='MULTIPLY_ADD'
        tile.inputs[1].default_value=(.46,.46,1);tile.inputs[2].default_value=(.02,.02,0)
        l.new(wrap.outputs[0],tile.inputs[0])
        tex=n.new('ShaderNodeTexImage');tex.image=atlas;tex.extension='EXTEND'
        l.new(tile.outputs[0],tex.inputs['Vector'])
        muted=n.new('ShaderNodeMixRGB');muted.blend_type='MULTIPLY';muted.inputs[0].default_value=1
        muted.inputs[2].default_value=(tint,tint,tint,1);l.new(tex.outputs['Color'],muted.inputs[1])
        if apron:
            paint=n.get('Mix (Legacy).002')
            if not paint:raise RuntimeError('Retained apron safety-paint graph missing')
            l.new(muted.outputs[0],paint.inputs[1])
            l.new(paint.outputs[0],shader.inputs['Base Color'])
        else:l.new(muted.outputs[0],shader.inputs['Base Color'])
        # Restrained physical aggregate, no glossy puddle masks or camera RGB.
        for node in n:
            if node.type=='BUMP':node.inputs['Strength'].default_value=.1;node.inputs['Distance'].default_value=.015
        mat['continuity_source']='PORT REALISM | freight concrete: same industrial atlas cell,20m repeat,.82roughness'
        return mat
    apron=bpy.data.objects['YARD_apron_240x220m']
    apron.data.materials[0]=ground_material('port concrete landing apron',apron=True)
    precinct=bpy.data.objects['LANDING YARD | partitioned precinct ground']
    precinct.data.materials[0]=ground_material('port concrete outer hardstand',tint=.88)
    # Preserve the separately partitioned road asphalt material, including all
    # original footprint ownership and contact geometry.
    after={ob.name:tuple(m.as_pointer() if m else 0 for m in ob.data.materials)
           for ob in protected if ob.type=='MESH'}
    if after!=ship_materials:raise AssertionError('Ship material assignment changed')
    remaining=[]
    for ob in scene.objects:
        if ob.hide_render or ob.type!='MESH' or ob in protected or ob.name in LANDSCAPE_EXCEPTIONS:continue
        if any(m and m.name.startswith('YARD FINISH | ') for m in ob.data.materials):remaining.append(ob.name)
    if remaining:raise AssertionError('Latest yard camera finish still assigned: '+str(remaining))
    return dict(restored_slots=restored,restored_slot_count=len(restored),static_objects_considered=len(changed),
                ground_materials=[apron.data.materials[0].name,precinct.data.materials[0].name],
                atlas=str(ATLAS),atlas_cell_xy=[0,0],repeat_metres=20,roughness=.82,
                outer_hardstand_linear_tint=.88,remaining_latest_camera_projection_objects=remaining,
                ship_materials_unchanged=True,
                retained_landscape_camera_finish_objects=sorted(LANDSCAPE_EXCEPTIONS),
                landscape_exception_reason='Retain prior approved camera finish on these two distant cards only; avoids stretched native source canopy/horizon. All physical yard geometry uses restored port atlas. No blur.',
                preserved='All geometry/transforms/actions, source ground joins and pad contacts, moving component-local clamp finish, traffic, sky/forest, camera and daylight.',
                limit='Physical atlas treatment matched to preceding port. No new multiangle texture claim and no full-frame blur.')
