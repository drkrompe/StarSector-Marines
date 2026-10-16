"""Retained, terrain-registered agricultural machinery for the shot03 window.

All coordinates are metres in the source land frame. Physical materials use
object coordinates, so wear stays attached during the independent vehicle move.
"""
import math
import json
from pathlib import Path

import bpy
from mathutils import Vector

PREFIX = 'AGRI | '
# Exact Blender render border in the original 1920x1080 camera, lower-left UV.
# Native crop is 1960x1480 at four times the original pixel density.
RESKIN_CROP_UV = (1150/1920,1640/1920,340/1080,710/1080)
TENDER_SPEED_M_S = 12
TENDER_HALF_TRAVEL_M = TENDER_SPEED_M_S*(287.5-240)/12/2


def build_agriculture_props(scene, world, ground_tree, use_reskin=True):
    """Return JSON-safe construction metadata; caller retains its camera/timing."""
    for obj in list(scene.objects):
        if obj.name.startswith(PREFIX):
            bpy.data.objects.remove(obj, do_unlink=True)

    def finish(name, color, metal=0):
        mat = bpy.data.materials.new(PREFIX + name)
        mat.use_nodes = True
        nodes, links = mat.node_tree.nodes, mat.node_tree.links
        bsdf = nodes.get('Principled BSDF')
        bsdf.inputs['Metallic'].default_value = metal
        bsdf.inputs['Roughness'].default_value = .78
        coord = nodes.new('ShaderNodeTexCoord')
        noise = nodes.new('ShaderNodeTexNoise')
        noise.inputs['Scale'].default_value = 7
        noise.inputs['Detail'].default_value = 3
        links.new(coord.outputs['Generated'], noise.inputs['Vector'])
        ramp = nodes.new('ShaderNodeValToRGB')
        ramp.color_ramp.elements[0].color = tuple(v*.48 for v in color)+(1,)
        ramp.color_ramp.elements[1].color = tuple(color)+(1,)
        links.new(noise.outputs['Fac'], ramp.inputs[0])
        links.new(ramp.outputs[0], bsdf.inputs['Base Color'])
        bump = nodes.new('ShaderNodeBump')
        bump.inputs['Strength'].default_value = .13
        bump.inputs['Distance'].default_value = .025
        links.new(noise.outputs['Fac'], bump.inputs['Height'])
        links.new(bump.outputs[0], bsdf.inputs['Normal'])
        return mat

    cream = finish('weathered ceramic alloy', (.58,.56,.43), .35)
    orange = finish('faded oxide safety panels', (.46,.16,.055), .3)
    steel = finish('dark structural steel', (.10,.14,.14), .65)
    roof = finish('galvanized roof', (.28,.34,.32), .55)
    concrete = finish('service concrete', (.25,.26,.21))
    dark = finish('recesses rubber and shadow', (.025,.038,.037))
    glass = finish('dusty armored sensor glass', (.06,.16,.18), .6)
    objects = []

    def height(x,y):
        hit,_,_,_ = ground_tree.ray_cast(Vector((x,y,5000)),Vector((0,0,-1)),10000)
        if hit is None:
            raise RuntimeError(f'No terrain under agriculture prop at {x},{y}')
        return hit.z

    def empty(name, xyz, parent=world):
        obj = bpy.data.objects.new(PREFIX+name,None)
        scene.collection.objects.link(obj)
        obj.parent=parent
        obj.location=xyz
        return obj

    def assign(obj,name,xyz,mat,parent):
        obj.name=PREFIX+name
        obj.parent=parent
        obj.location=xyz
        obj.data.materials.append(mat)
        obj.pass_index=61
        objects.append(obj)
        return obj

    def box(name,xyz,size,mat,parent,bevel=.12):
        bpy.ops.mesh.primitive_cube_add()
        obj=bpy.context.object
        obj.scale=tuple(v/2 for v in size)
        bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
        assign(obj,name,xyz,mat,parent)
        if bevel:
            mod=obj.modifiers.new('assembled edge radius','BEVEL')
            mod.width=bevel
            mod.segments=2
            obj.modifiers.new('weighted panel normals','WEIGHTED_NORMAL')
        return obj

    def cylinder(name,xyz,radius,depth,mat,parent):
        bpy.ops.mesh.primitive_cylinder_add(vertices=24,radius=radius,depth=depth)
        return assign(bpy.context.object,name,xyz,mat,parent)

    def linear_animation(data):
        if data.animation_data and data.animation_data.action:
            for layer in data.animation_data.action.layers:
                for strip in layer.strips:
                    for bag in strip.channelbags:
                        for curve in bag.fcurves:
                            for key in curve.keyframe_points:
                                key.interpolation='LINEAR'

    # The tender is intentionally heavy and low: visible negative space below
    # the chassis, four separate lift shoes, protected tank and service gantries.
    tender_xy=(-3520,660)
    tender_z=height(*tender_xy)
    tender=empty('crop tender moving frame',(*tender_xy,tender_z+5.8))
    tender.scale=(1.5,1.5,1.5)
    tender.rotation_euler.z=math.radians(-18)
    box('tender keel',(0,0,0),(25,13,3.8),steel,tender,.6)
    box('tender upper casing',(-1,0,3),(20,11,3.2),cream,tender,.5)
    box('tender control cabin',(8,0,5.1),(6,7,4),orange,tender,.5)
    box('tender front glazing',(11.05,0,5.5),(.18,5.8,2),glass,tender,.02)
    for side in (-1,1):
        for x in (-9,8):
            box('tender lift outrigger',(x,side*9,-.3),(6,8,1.4),steel,tender)
            cylinder('tender lift shroud',(x,side*11,-1.1),3.8,2.2,orange,tender)
            cylinder('tender dark lift mouth',(x,side*11,-2.24),2.9,.16,dark,tender)
            cylinder('tender lift ceramic ring',(x,side*11,-2.34),2.25,.12,cream,tender)
        box('tender extended spray spar',(-3,side*22,.3),(1.5,30,1.3),steel,tender)
        box('tender oxide spar stripe',(-3,side*22,1.02),(1.7,26,.2),orange,tender)
        for y in (14,22,30,36):
            cylinder('tender downward nozzle',(-3,side*y,-.7),.45,1.1,dark,tender)
        box('tender service rail',(-4,side*5.3,5),(13,.22,.4),steel,tender,.02)
        for x in (-10,-4,2):
            box('tender rail support',(x,side*5.3,4.3),(.2,.2,1.4),steel,tender,.02)
    tank=cylinder('tender reagent tank',(-4,0,5.4),3.1,11,cream,tender)
    tank.rotation_euler.y=math.pi/2
    for x in (-8,-1):
        strap=cylinder('tender tank retaining band',(x,0,5.4),3.2,.4,steel,tender)
        strap.rotation_euler.y=math.pi/2
    for x in (-9,-6,-3,0,3):
        box('tender top cooling fins',(x,0,7.6),(1.2,5,.4),steel,tender,.03)
    for t,dx in ((240,-TENDER_HALF_TRAVEL_M),(287.5,TENDER_HALF_TRAVEL_M)):
        tender.location=(tender_xy[0]+dx,tender_xy[1],height(tender_xy[0]+dx,tender_xy[1])+5.8)
        tender.keyframe_insert(data_path='location',frame=t)
    linear_animation(tender)

    # Eight restrained downward fans start exactly at the nozzle mouths and
    # drift aft as fine droplets. Separate nozzle-centered objects give stable
    # blend sorting; the lower edge stays above the crop surface.
    spray_mat=bpy.data.materials.new(PREFIX+'fine downward irrigation spray')
    spray_mat.use_nodes=True
    nodes,links=spray_mat.node_tree.nodes,spray_mat.node_tree.links
    nodes.clear()
    coords=nodes.new('ShaderNodeTexCoord')
    split=nodes.new('ShaderNodeSeparateXYZ')
    links.new(coords.outputs['Generated'],split.inputs[0])
    fade=nodes.new('ShaderNodeMapRange')
    fade.inputs['From Min'].default_value=0
    fade.inputs['From Max'].default_value=1
    fade.inputs['To Min'].default_value=.015
    fade.inputs['To Max'].default_value=.22
    links.new(split.outputs['Z'],fade.inputs['Value'])
    turbulence=nodes.new('ShaderNodeTexNoise')
    turbulence.noise_dimensions='4D'
    turbulence.inputs['Scale'].default_value=5
    turbulence.inputs['Detail'].default_value=2
    links.new(coords.outputs['Generated'],turbulence.inputs['Vector'])
    for t,w in ((240,0),(287.5,9.5)):
        turbulence.inputs['W'].default_value=w
        turbulence.inputs['W'].keyframe_insert(data_path='default_value',frame=t)
    density=nodes.new('ShaderNodeMath')
    density.operation='MULTIPLY'
    links.new(fade.outputs[0],density.inputs[0])
    links.new(turbulence.outputs['Fac'],density.inputs[1])
    transparent=nodes.new('ShaderNodeBsdfTransparent')
    droplets=nodes.new('ShaderNodeEmission')
    droplets.inputs[0].default_value=(.62,.73,.66,1)
    droplets.inputs[1].default_value=.8
    mix=nodes.new('ShaderNodeMixShader')
    links.new(density.outputs[0],mix.inputs[0])
    links.new(transparent.outputs[0],mix.inputs[1])
    links.new(droplets.outputs[0],mix.inputs[2])
    out=nodes.new('ShaderNodeOutputMaterial')
    links.new(mix.outputs[0],out.inputs[0])
    spray_mat.surface_render_method='BLENDED'
    linear_animation(spray_mat.node_tree)
    spray_objects=[]
    for side in (-1,1):
        for y in (14,22,30,36):
            vertices=[]
            for z,radius,wake in ((-1.25,.14,0),(-2.2,1,-1.2),(-3.45,2.2,-3.4)):
                for i in range(12):
                    angle=i*math.tau/12
                    vertices.append((wake+math.cos(angle)*radius,
                                     math.sin(angle)*radius,z+1.25))
            faces=[(j*12+i,j*12+(i+1)%12,(j+1)*12+(i+1)%12,(j+1)*12+i)
                   for j in range(2) for i in range(12)]
            mesh=bpy.data.meshes.new(PREFIX+'downward droplet fan')
            mesh.from_pydata(vertices,[],faces)
            obj=bpy.data.objects.new(mesh.name,mesh)
            scene.collection.objects.link(obj)
            obj.parent=tender
            obj.location=(-3,side*y,-1.25)
            mesh.materials.append(spray_mat)
            obj.pass_index=63
            spray_objects.append(obj.name)

    # The accepted ground is emissive plate art and cannot receive EEVEE
    # shadows. A soft, terrain-conforming translucent footprint supplies the
    # hover separation without replacing the retained landscape material.
    shadow_mat=bpy.data.materials.new(PREFIX+'soft hover footprint')
    shadow_mat.use_nodes=True
    nodes,links=shadow_mat.node_tree.nodes,shadow_mat.node_tree.links
    nodes.clear()
    uv=nodes.new('ShaderNodeTexCoord')
    center=nodes.new('ShaderNodeVectorMath')
    center.operation='SUBTRACT'
    center.inputs[1].default_value=(tender_xy[0]+4,tender_xy[1]+4,0)
    links.new(uv.outputs['Object'],center.inputs[0])
    for t,dx in ((240,-TENDER_HALF_TRAVEL_M),(287.5,TENDER_HALF_TRAVEL_M)):
        center.inputs[1].default_value[0]=tender_xy[0]+4+dx
        center.inputs[1].keyframe_insert(data_path='default_value',index=0,frame=t)
    linear_animation(shadow_mat.node_tree)
    scale=nodes.new('ShaderNodeVectorMath')
    scale.operation='MULTIPLY'
    scale.inputs[1].default_value=(1/55,1/50,0)
    links.new(center.outputs[0],scale.inputs[0])
    subtract=nodes.new('ShaderNodeVectorMath')
    subtract.operation='SUBTRACT'
    subtract.inputs[1].default_value=(0,0,0)
    links.new(scale.outputs[0],subtract.inputs[0])
    length=nodes.new('ShaderNodeVectorMath')
    length.operation='LENGTH'
    links.new(subtract.outputs[0],length.inputs[0])
    ramp=nodes.new('ShaderNodeMapRange')
    ramp.inputs['From Min'].default_value=.14
    ramp.inputs['From Max'].default_value=.5
    ramp.inputs['To Min'].default_value=.32
    ramp.inputs['To Max'].default_value=0
    links.new(length.outputs['Value'],ramp.inputs['Value'])
    transparent=nodes.new('ShaderNodeBsdfTransparent')
    black=nodes.new('ShaderNodeEmission')
    black.inputs[0].default_value=(.015,.025,.015,1)
    mix=nodes.new('ShaderNodeMixShader')
    links.new(ramp.outputs[0],mix.inputs[0])
    links.new(transparent.outputs[0],mix.inputs[1])
    links.new(black.outputs[0],mix.inputs[2])
    out=nodes.new('ShaderNodeOutputMaterial')
    links.new(mix.outputs[0],out.inputs[0])
    shadow_mat.surface_render_method='BLENDED'
    land=bpy.data.objects['Jangala | continuous landform']
    source_matrix=world.matrix_world.inverted()@land.matrix_world
    source_vertices=[source_matrix@v.co for v in land.data.vertices]
    land.data.calc_loop_triangles()
    vertices=[]
    faces=[]
    xmin,xmax=tender_xy[0]-TENDER_HALF_TRAVEL_M-40,tender_xy[0]+TENDER_HALF_TRAVEL_M+48
    ymin,ymax=tender_xy[1]-35,tender_xy[1]+43
    for tri in land.data.loop_triangles:
        points=[source_vertices[i] for i in tri.vertices]
        if (max(p.x for p in points)<xmin or min(p.x for p in points)>xmax or
            max(p.y for p in points)<ymin or min(p.y for p in points)>ymax):
            continue
        start=len(vertices)
        vertices.extend([(p.x,p.y,p.z+.16) for p in points])
        faces.append((start,start+1,start+2))
    mesh=bpy.data.meshes.new(PREFIX+'terrain sampled hover footprint')
    mesh.from_pydata(vertices,[],faces)
    shadow=bpy.data.objects.new(mesh.name,mesh)
    scene.collection.objects.link(shadow)
    shadow.parent=world
    mesh.materials.append(shadow_mat)
    shadow.pass_index=62

    drone_xy=(-2780,1680)
    drone=empty('survey drone frame',(*drone_xy,height(*drone_xy)+22))
    box('drone center',(0,0,0),(8,5,3),orange,drone,.4)
    for x in (-6,6):
        box('drone arm',(x/2,0,0),(7,1,1),steel,drone)
        cylinder('drone duct',(x,0,-.7),2.4,1.8,cream,drone)
        cylinder('drone dark duct',(x,0,-1.65),1.7,.1,dark,drone)
    cylinder('drone survey head',(2,0,-2),1.5,2,glass,drone)

    compound_xy=(-2680,1280)
    z=height(*compound_xy)
    compound=empty('right agricultural service compound',(*compound_xy,z))
    box('compound broad apron',(0,0,.25),(210,155,.5),concrete,compound,.1)
    for j,(x,y,sx,sy) in enumerate(((-45,30,95,44),(53,37,65,34))):
        box(f'shed {j} plinth',(x,y,1),(sx+3,sy+3,2),steel,compound)
        box(f'shed {j} walls',(x,y,9),(sx,sy,16),cream,compound,.4)
        for side in (-1,1):
            panel=box(f'shed {j} sloped roof',(x,y+side*sy/4,18.7),(sx+6,sy/2+2,1.1),roof,compound)
            panel.rotation_euler.x=-side*.13
        for dx in range(-int(sx/2)+4,int(sx/2),9):
            box(f'shed {j} seam',(x+dx,y,18.9),(.35,sy+3,.6),steel,compound,.02)
        for dx in (-sx*.28,sx*.28):
            box(f'shed {j} recessed service door',(x+dx,y-sy/2-.16,7),(14,.3,11),dark,compound,.02)
            box(f'shed {j} door header',(x+dx,y-sy/2-.5,13),(15,1,1.2),orange,compound)
        for dx in (-sx*.45,0,sx*.45):
            box(f'shed {j} wall buttress',(x+dx,y-sy/2,8),(1.4,2,15),steel,compound)
        for dx in (-sx*.2,sx*.23):
            box(f'shed {j} rooftop HVAC',(x+dx,y+3,21),(10,8,4),steel,compound)
    for x in (-77,-54,-31):
        cylinder('compound storage silo',(x,-45,13),8.5,25,cream,compound)
        for h in (3,12,23):
            cylinder('silo reinforcing ring',(x,-45,h),8.7,.7,steel,compound)
        cylinder('silo crown',(x,-45,26),6.5,1.5,orange,compound)
    box('pump service building',(-4,-45,4),(22,17,8),orange,compound)
    box('pump roof',(-4,-45,8.5),(24,19,1),steel,compound)
    box('landing service pad',(62,-37,.65),(48,48,.5),roof,compound,.05)
    for dy in (-15,15):
        box('landing pad bar',(62,-37+dy,.94),(32,1,.08),cream,compound,.01)
    for dx in (-10,10):
        box('landing H legs',(62+dx,-37,.96),(1,20,.08),cream,compound,.01)
    box('landing H bridge',(62,-37,.97),(20,1,.08),cream,compound,.01)
    cylinder('compound antenna mast',(91,61,21),.6,42,steel,compound)
    for h,w in ((30,12),(38,8)):
        box('antenna transverse spar',(91,61,h),(w,.5,.5),steel,compound,.02)
    for x in (19,32,45):
        box('compound cargo crate',(x,-4,2),(9,7,4),orange if x==32 else cream,compound)
    for obj in (tender,drone,compound):
        obj['source_world_frame']='LAYERED inverse source flight'
    reskin=Path(__file__).resolve().parent/'assets/agriculture-reskin-v1.png'
    reskin_applied=use_reskin and reskin.is_file()
    if reskin_applied:
        # Freeze the *actual* midpoint lens relative to each rigid assembly.
        # The same exact camera-border RGBA crop serves all assemblies.
        saved_frame=scene.frame_current+scene.frame_subframe
        scene.frame_set(263,subframe=.75)
        bpy.context.view_layer.update()
        camera=bpy.data.objects['03 LAND / side window']
        image=bpy.data.images.load(str(reskin),check_existing=True)
        registration=json.loads((reskin.parent.parent/'agriculture-reskin-registration.json').read_text())
        for assembly,group in ((tender,'tender'),(drone,'drone'),(compound,'compound')):
            guide_bounds=registration['guide']['groups'][group]['bbox_uv_top_left']
            art_bounds=registration['art']['groups'][group]['bbox_uv_top_left']
            data=camera.data.copy()
            projection=bpy.data.objects.new(PREFIX+'attached reskin projector '+assembly.name,data)
            scene.collection.objects.link(projection)
            projection.parent=assembly
            projection.matrix_basis=assembly.matrix_world.inverted()@camera.matrix_world.normalized()
            toward_camera=camera.matrix_world.normalized().to_3x3()@Vector((0,0,1))
            cache={}
            for obj in assembly.children_recursive:
                if obj.type!='MESH' or obj.pass_index!=61:
                    continue
                for slot in obj.material_slots:
                    base=slot.material
                    if base not in cache:
                        mat=base.copy()
                        mat.name=base.name+' registered '+assembly.name
                        nodes,links=mat.node_tree.nodes,mat.node_tree.links
                        bsdf=nodes.get('Principled BSDF')
                        previous=bsdf.inputs['Base Color'].links[0].from_socket
                        coords=nodes.new('ShaderNodeTexCoord')
                        coords.object=projection
                        split=nodes.new('ShaderNodeSeparateXYZ')
                        links.new(coords.outputs['Object'],split.inputs[0])
                        negative=nodes.new('ShaderNodeMath')
                        negative.operation='MULTIPLY'
                        negative.inputs[1].default_value=-1
                        links.new(split.outputs['Z'],negative.inputs[0])
                        vector=nodes.new('ShaderNodeCombineXYZ')
                        for axis,gain in (('X',data.lens/data.sensor_width),('Y',data.lens/data.sensor_width*1920/1080)):
                            divide=nodes.new('ShaderNodeMath')
                            divide.operation='DIVIDE'
                            links.new(split.outputs[axis],divide.inputs[0])
                            links.new(negative.outputs[0],divide.inputs[1])
                            remap=nodes.new('ShaderNodeMath')
                            remap.operation='MULTIPLY_ADD'
                            start,end=RESKIN_CROP_UV[:2] if axis=='X' else RESKIN_CROP_UV[2:]
                            if axis=='X':
                                g0,g1=guide_bounds[0],guide_bounds[2]
                                a0,a1=art_bounds[0],art_bounds[2]
                            else:
                                g0,g1=1-guide_bounds[3],1-guide_bounds[1]
                                a0,a1=1-art_bounds[3],1-art_bounds[1]
                            ratio=(a1-a0)/(g1-g0)
                            remap.inputs[1].default_value=gain/(end-start)*ratio
                            remap.inputs[2].default_value=((.5-start)/(end-start)-g0)*ratio+a0
                            links.new(divide.outputs[0],remap.inputs[0])
                            links.new(remap.outputs[0],vector.inputs[axis])
                        texture=nodes.new('ShaderNodeTexImage')
                        texture.image=image
                        texture.extension='CLIP'
                        links.new(vector.outputs[0],texture.inputs['Vector'])
                        geo=nodes.new('ShaderNodeNewGeometry')
                        dot=nodes.new('ShaderNodeVectorMath')
                        dot.operation='DOT_PRODUCT'
                        dot.inputs[1].default_value=toward_camera
                        links.new(geo.outputs['Normal'],dot.inputs[0])
                        gate=nodes.new('ShaderNodeMath')
                        gate.operation='GREATER_THAN'
                        gate.inputs[1].default_value=.05
                        links.new(dot.outputs['Value'],gate.inputs[0])
                        alpha=nodes.new('ShaderNodeMath')
                        alpha.operation='MULTIPLY'
                        links.new(gate.outputs[0],alpha.inputs[0])
                        links.new(texture.outputs['Alpha'],alpha.inputs[1])
                        mix=nodes.new('ShaderNodeMixRGB')
                        links.new(alpha.outputs[0],mix.inputs[0])
                        links.new(previous,mix.inputs[1])
                        links.new(texture.outputs['Color'],mix.inputs[2])
                        links.new(mix.outputs[0],bsdf.inputs['Base Color'])
                        cache[base]=mat
                    slot.material=cache[base]
        scene.frame_set(int(saved_frame),subframe=saved_frame%1)
    return {'objects':[obj.name for obj in objects], 'pass_index':61,
            'compound_source_xyz_m':[*compound_xy,z],
            'tender_ground_xyz_m':[*tender_xy,tender_z],
            'tender_nominal_lift_clearance_at_root_datum_m':2.2,
            'drone_nominal_clearance_at_root_datum_m':19,
            'clearance_measurement':'Nominal root datum values only; actual terrain-wide evaluated-mesh clearance is verified separately.',
            'hover_shadow':'Actual retained terrain triangles +.16m; static source-frame mesh, animated sourceXY radial mask matching tender displacement with +4,+4m light offset',
            'tender_motion':{'direction_source_xyz':[1,0,0],'source_frames':[240,287.5],
                             'speed_m_s':TENDER_SPEED_M_S,'travel_m':2*TENDER_HALF_TRAVEL_M,
                             'interpolation':'LINEAR, constant speed; midpoint retained at263.75',
                             'heading_note':'Existing18degree crab angle retained; transverse boom, sourceX field-row tracking.'},
            'spray_fx':{'objects':spray_objects,'pass_index':63,
                        'representation':'Eight open downward droplet fans from actual nozzle mouths, .625m nominal minimum ground gap; aft wake5.1m, object-attached4D noise',
                        'material':'BLENDED transparent/emission with separate nozzle-centered origins; maximum per-surface opacity .22 times noise, lower edge .015 times noise; no opaque cards or raster modifications',
                        'projection':'Excluded from reskin; local geometry and generated coordinates follow tender',
                        'validation':'Review all96times at1080p for visible downward flow without white blanket; validate every spray vertex above terrain and shadow support covers radial mask along full47.5m track.'},
            'reskin_crop_original_uv':RESKIN_CROP_UV,
            'reskin_registration':'Per-assembly alpha-bounds affine from agriculture-reskin-registration.json; preserves geometry but is approximate material registration, not matched internal panels. Rear-facing and alpha0 faces retain procedural finish.',
            'reskin_applied':reskin_applied,
            'reskin_missing_behavior':'Optional art absent: complete procedural materials. Art present: registration JSON required; malformed or missing registration fails build rather than silently applying misregistered art.',
            'material_mapping':'Generated object-local procedural base plus optional rigid-assembly-attached perspective finish; physical BSDF',
            'dimensions_provisional':True}
