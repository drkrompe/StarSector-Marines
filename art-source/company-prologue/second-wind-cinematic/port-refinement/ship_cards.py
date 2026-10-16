"""Independent airborne RGBA traffic cards; prior grounded study retained as rejected evidence."""
from pathlib import Path
import json,bpy
from mathutils import Vector
H=Path(__file__).resolve().parent
FLIGHT_ASSET_VERSION='v3-flight'

def build_ship_cards(scene,world,layout=None):
    contract=json.loads((H/'ship-card-layout.json').read_text(encoding='utf-8'))
    registration=json.loads((H/'artwork-registration.json').read_text(encoding='utf-8'))
    overlay={s['id']:s for s in registration['ships']}
    results=[];air_records=[]
    specs={'venture':dict(midpoint=[-80,1350,180],delta_x=50),
           'hound':dict(midpoint=[200,1100,120],delta_x=85)}

    for guide_ship in contract['ships']:
        ship=dict(guide_ship);ship.update(overlay[guide_ship['id']])
        ship['asset']='assets/'+ship['id']+'-card-'+FLIGHT_ASSET_VERSION+'.png'
        spec=specs[ship['id']];ship['source_center']=spec['midpoint']
        path=H/ship['asset']
        if not path.exists():raise FileNotFoundError('Registered ship card asset required: '+str(path))
        center=Vector(ship['source_center']);right=Vector(ship['source_right'])*ship['width']/2;up=Vector(ship['source_up'])*ship['height']/2
        vertices=[-right-up,right-up,right+up,-right+up]
        md=bpy.data.meshes.new(ship['id']+' registered parallax quad');md.from_pydata(vertices,[],[(0,1,2,3)]);md.update()
        uv=md.uv_layers.new(name='exact retained guide canvas')
        for loop,pair in zip(uv.data,((0,0),(1,0),(1,1),(0,1))):loop.uv=pair
        obj=bpy.data.objects.new('SHIP CARD | '+ship['id'],md);scene.collection.objects.link(obj);obj.parent=world;obj.location=center;obj.pass_index=16
        mat=bpy.data.materials.new('SHIP CARD | '+ship['id']+' registered RGBA');mat.use_nodes=True;n=mat.node_tree.nodes;l=mat.node_tree.links;n.clear()
        image=n.new('ShaderNodeTexImage');image.image=bpy.data.images.load(str(path),check_existing=True);image.image.alpha_mode='STRAIGHT';image.extension='CLIP'
        emission=n.new('ShaderNodeEmission');l.new(image.outputs['Color'],emission.inputs[0])
        transparent=n.new('ShaderNodeBsdfTransparent');mix=n.new('ShaderNodeMixShader');l.new(image.outputs['Alpha'],mix.inputs[0]);l.new(transparent.outputs[0],mix.inputs[1]);l.new(emission.outputs[0],mix.inputs[2])
        out=n.new('ShaderNodeOutputMaterial');l.new(mix.outputs[0],out.inputs[0]);mat.surface_render_method='BLENDED';mat.use_transparent_shadow=True;md.materials.append(mat)
        # Source motion is independent of inverse-flight parent translation.
        for frame,t in ((400,-.5),(448,.5)):
            obj.location=center+Vector((spec['delta_x']*t,0,0))
            obj.keyframe_insert(data_path='location',frame=frame)
        action=obj.animation_data.action
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        for key in curve.keyframe_points:key.interpolation='LINEAR'
        air=dict(ship=ship['id'],midpoint_source=list(center),source_frames=[400,448],
                 start_source=list(center-Vector((spec['delta_x']/2,0,0))),end_source=list(center+Vector((spec['delta_x']/2,0,0))),
                 delta_source=[spec['delta_x'],0,0],source_fps=12,altitude_basis='quad center source Z; conservative full quad lower bounds checked separately',
                 texture=str(path),width=ship['width'],height=ship['height'],source_right=ship['source_right'],source_up=ship['source_up'],canvas=ship['canvas'],
                 representation='airborne fixed-orientation parallax quad, independent linear source motion; no landing or contact claim')
        obj['ship_card_id']=ship['id'];obj['source_center']=ship['source_center'];obj['canvas']=ship['canvas'];obj['reference_source_frame']=424
        obj['airtraffic_contract']=json.dumps(air);air_records.append(air)
        for name in ship['guide_meshes']:
            old=bpy.data.objects.get(name)
            if old:old.hide_render=True;old['representation']='retained geometry guide only'
        results.append(dict(id=ship['id'],object=obj.name,material=mat.name,asset=str(path),source_center=ship['source_center'],width=ship['width'],height=ship['height'],motion=air,guide_meshes_hidden=len(ship['guide_meshes']),representation='airborne independent linear source-motion RGBA quad'))
    (H/'airtraffic-registration.json').write_text(json.dumps(dict(ships=air_records,rejected_grounded_study='artwork-registration.json',ground_contact_claim=False),indent=2),encoding='utf-8')
    return dict(cards=results,layout=str(H/'ship-card-layout.json'),artwork_registration=str(H/'artwork-registration.json'),airtraffic_registration=str(H/'airtraffic-registration.json'),shadow_strategy='Airborne cards have no ground-contact shadow or landing claim')
