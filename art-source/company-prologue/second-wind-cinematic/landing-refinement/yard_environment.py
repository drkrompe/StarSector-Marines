"""Reuse the port's physical district in a fixed landing-yard coordinate system.

The incoming scene owns ship motion, camera, lights and the retained pad contacts.
No saved source scene or generated artwork is modified by this module.
"""
from pathlib import Path
import json
import math
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
BASE = HERE.parent
PORT = BASE / 'port-refinement/port-refinement.blend'
OFFSET = Vector((850, 240, -.4))
PREFIX = 'LANDING YARD | '


def apply_yard(scene):
    """Add reusable physical assets; return provenance and unchanged contact data."""
    contact_names = [o.name for o in scene.objects if o.name.startswith((
        'YARD_contact_pad_', 'YARD_pad_contact_face_', 'YARD_cradle_rail_',
        'YARD_clamp_slide_', 'YARD_retaining_wedge_'))]
    contacts = {n: [list(row) for row in bpy.data.objects[n].matrix_world] for n in contact_names}
    # Hide only obsolete setting. Ship and the original contact rig are retained.
    hidden = []
    obsolete = ('Jangala |', 'Plantation |', 'Port district', 'Port communications',
                'Port tower', 'PORT DETAIL |', 'LAYER |', 'YARD_shed', 'YARD_crane',
                'YARD_approach_center_', 'YARD_landing_boundary_', 'YARD_landing_end_',
                'Freight vehicle', 'SCALE STUDY ONLY |')
    for o in list(scene.objects):
        if o.name.startswith(obsolete) or o.name in ('YARD_surrounding_ground','YARD_negative_X_approach'):
            o.hide_render = True
            hidden.append(o.name)
    before_images = set(bpy.data.images)
    with bpy.data.libraries.load(str(PORT), link=False) as (src, dst):
        dst.objects = [n for n in src.objects if n.startswith(('DISTRICT | ', 'PORT TRAFFIC | '))]
    imported = [o for o in dst.objects if o]
    root = bpy.data.objects.new(PREFIX+'fixed port district', None)
    scene.collection.objects.link(root)
    root.location = OFFSET
    restored = 0
    for o in imported:
        scene.collection.objects.link(o)
        if o.parent not in imported:
            o.parent = root
            o.matrix_parent_inverse.identity()
        o.hide_render = False
        if o.type == 'MESH':
            for index, mat in enumerate(o.data.materials):
                if mat and mat.name.startswith('ARCHITECTURE FINISH | '):
                    fallback = bpy.data.materials.get(mat.name.removeprefix('ARCHITECTURE FINISH | '))
                    if fallback:
                        o.data.materials[index] = fallback
                        restored += 1
        # Reuse ordinary independent ground traffic over the landing interval.
        if o.animation_data and o.animation_data.action:
            action = o.animation_data.action.copy()
            o.animation_data.action = action
            for layer in action.layers:
                for strip in layer.strips:
                    for bag in strip.channelbags:
                        for curve in bag.fcurves:
                            for key in curve.keyframe_points:
                                for co in (key.co, key.handle_left, key.handle_right):
                                    co.x = 580 + (co.x - 400) * (194 / 48)
    # Resolve assets relative to their source blend, rather than the new output blend.
    for image in bpy.data.images:
        if image not in before_images and image.source == 'FILE' and image.filepath:
            raw = image.filepath
            if raw.startswith('//'):
                image.filepath = str((PORT.parent/raw[2:]).resolve())

    def material(name, base, metal=0, noise_scale=2, atlas=None, cell=None):
        m = bpy.data.materials.new(PREFIX+name); m.use_nodes=True
        n,l=m.node_tree.nodes,m.node_tree.links
        p=n.get('Principled BSDF');p.inputs['Base Color'].default_value=(*base,1)
        p.inputs['Metallic'].default_value=metal;p.inputs['Roughness'].default_value=.84
        co=n.new('ShaderNodeTexCoord');noise=n.new('ShaderNodeTexNoise')
        noise.inputs['Scale'].default_value=noise_scale;noise.inputs['Detail'].default_value=3
        l.new(co.outputs['Object'],noise.inputs['Vector'])
        mix=n.new('ShaderNodeMixRGB');mix.inputs[1].default_value=(*(v*.64 for v in base),1)
        mix.inputs[2].default_value=(*base,1);l.new(noise.outputs['Fac'],mix.inputs[0])
        l.new(mix.outputs[0],p.inputs['Base Color'])
        bump=n.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.17;bump.inputs['Distance'].default_value=.025
        l.new(noise.outputs['Fac'],bump.inputs['Height']);l.new(bump.outputs[0],p.inputs['Normal'])
        if atlas:
            tex=n.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(atlas),check_existing=True)
            mapping=n.new('ShaderNodeVectorMath');mapping.operation='SCALE';mapping.inputs[3].default_value=.08
            l.new(co.outputs['Object'],mapping.inputs[0])
            wrap=n.new('ShaderNodeVectorMath');wrap.operation='FRACTION';l.new(mapping.outputs[0],wrap.inputs[0])
            tile=n.new('ShaderNodeVectorMath');tile.operation='MULTIPLY_ADD'
            tile.inputs[1].default_value=(.46,.46,1);tile.inputs[2].default_value=(cell[0]*.5+.02,cell[1]*.5+.02,0)
            l.new(wrap.outputs[0],tile.inputs[0]);l.new(tile.outputs[0],tex.inputs['Vector'])
            l.new(tex.outputs['Color'],p.inputs['Base Color'])
        return m

    concrete=material('exposed aggregate concrete',(.33,.34,.31),noise_scale=1.4)
    steel=material('burnished support steel',(.16,.19,.19),.65,5)
    oxide=material('weathered oxide mechanism casing',(.32,.14,.065),.5,3)
    asphalt=material('freight road asphalt',(.08,.095,.092),noise_scale=3)
    gravel=material('outer hardstand compacted gravel',(.22,.24,.21),noise_scale=2.5)
    yellow=material('worn safety ochre',(.49,.35,.10),.05,5)
    # Apron markings and expansion seams are shader regions on the sole owner.
    apron=bpy.data.objects['YARD_apron_240x220m']
    apron.data.materials.clear();apron.data.materials.append(concrete)
    n,l=concrete.node_tree.nodes,concrete.node_tree.links;p=n.get('Principled BSDF')
    previous=p.inputs['Base Color'].links[0].from_socket
    co=n.new('ShaderNodeTexCoord');co.object=root
    sep=n.new('ShaderNodeSeparateXYZ');l.new(co.outputs['Object'],sep.inputs[0])
    def mn(op,a,b):
        q=n.new('ShaderNodeMath');q.operation=op
        for i,v in enumerate((a,b)):
            if isinstance(v,(int,float)):q.inputs[i].default_value=v
            else:l.new(v,q.inputs[i])
        return q.outputs[0]
    seam=[]
    for axis in ('X','Y'):
        phase=mn('PINGPONG',sep.outputs[axis],8)
        seam.append(mn('LESS_THAN',phase,.035))
    fac=mn('MAXIMUM',*seam)
    mix=n.new('ShaderNodeMixRGB');l.new(fac,mix.inputs[0]);l.new(previous,mix.inputs[1]);mix.inputs[2].default_value=(.045,.05,.047,1)
    # Stripe safety border, ten metres clear of rail mechanism.
    x=mn('ABSOLUTE',mn('ADD',sep.outputs['X'],OFFSET.x),0)
    y=mn('ABSOLUTE',mn('ADD',sep.outputs['Y'],OFFSET.y),0)
    border=mn('MULTIPLY',mn('LESS_THAN',mn('ABSOLUTE',mn('SUBTRACT',y,38),0),.3),mn('LESS_THAN',x,75))
    paint=n.new('ShaderNodeMixRGB');l.new(border,paint.inputs[0]);l.new(mix.outputs[0],paint.inputs[1]);paint.inputs[2].default_value=(.48,.34,.09,1)
    l.new(paint.outputs[0],p.inputs['Base Color'])

    created=[]
    def mesh(name,vertices,faces,mat):
        data=bpy.data.meshes.new(PREFIX+name);data.from_pydata(vertices,[],faces);data.update()
        data.materials.append(mat);o=bpy.data.objects.new(PREFIX+name,data);scene.collection.objects.link(o);created.append(o);return o
    def box(name,pos,size,mat,bevel=.07):
        x,y,z=pos;a,b,c=(v/2 for v in size)
        o=mesh(name,[(x-a,y-b,z-c),(x+a,y-b,z-c),(x+a,y+b,z-c),(x-a,y+b,z-c),(x-a,y-b,z+c),(x+a,y-b,z+c),(x+a,y+b,z+c),(x-a,y+b,z+c)],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],mat)
        if bevel:
            mod=o.modifiers.new('assembled edges','BEVEL');mod.width=bevel;mod.segments=2
        return o
    # A tessellated surface excludes the apron footprint, shares its edge Z=0,
    # and assigns source-port roads to cells. No lower mesh under either owner.
    layout=json.loads((BASE/'port-refinement/scene-manifest.json').read_text())['layout']
    roads=[dict(bounds=[r['bounds'][0]+OFFSET.x,r['bounds'][1]+OFFSET.x,r['bounds'][2]+OFFSET.y,r['bounds'][3]+OFFSET.y],name=r['name']) for r in layout['roads']]
    xs=sorted(set([-1600,-120,120,1500]+[b for r in roads for b in r['bounds'][:2]]))
    ys=sorted(set([-550,-110,110,OFFSET.y+1840]+[b for r in roads for b in r['bounds'][2:]]))
    verts=[];faces=[];mats=[]
    for x1,x2 in zip(xs,xs[1:]):
        for y1,y2 in zip(ys,ys[1:]):
            x,y=(x1+x2)/2,(y1+y2)/2
            if -120<x<120 and -110<y<110:continue
            k=len(verts);verts.extend([(x1,y1,0),(x2,y1,0),(x2,y2,0),(x1,y2,0)]);faces.append((k,k+1,k+2,k+3))
            isroad=any(a<=x<=b and c<=y<=d for a,b,c,d in (r['bounds'] for r in roads))
            mats.append(1 if isroad else 0)
    ground=mesh('partitioned precinct ground',verts,faces,gravel);ground.data.materials.append(asphalt)
    for f,i in zip(ground.data.polygons,mats):f.material_index=i
    dynamic_materials=[]
    def moving_hardware_material(ob):
        wedge=ob.name.startswith('YARD_retaining_wedge_')
        path=BASE/'second-wind-surface-proof/assets'/('gunmetal.png' if wedge else 'oxide.png')
        mat=bpy.data.materials.new(PREFIX+'component local weathering | '+ob.name);mat.use_nodes=True
        nodes,links=mat.node_tree.nodes,mat.node_tree.links;p=nodes.get('Principled BSDF')
        p.inputs['Metallic'].default_value=.5;p.inputs['Roughness'].default_value=.7
        co=nodes.new('ShaderNodeTexCoord');co.object=ob
        tex=nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(path),check_existing=True)
        tex.projection='BOX';tex.projection_blend=.18;tex.extension='REPEAT'
        mapping=nodes.new('ShaderNodeVectorMath');mapping.operation='MULTIPLY_ADD'
        low=[min(v.co[a] for v in ob.data.vertices) for a in range(3)]
        high=[max(v.co[a] for v in ob.data.vertices) for a in range(3)]
        scale=[1/max(high[a]-low[a],.001) for a in range(3)]
        mapping.inputs[1].default_value=scale;mapping.inputs[2].default_value=[-low[a]*scale[a] for a in range(3)]
        links.new(co.outputs['Object'],mapping.inputs[0]);links.new(mapping.outputs[0],tex.inputs['Vector'])
        mix=nodes.new('ShaderNodeMixRGB');mix.inputs[0].default_value=.65
        mix.inputs[1].default_value=(.12,.15,.15,1) if wedge else (.29,.12,.05,1)
        links.new(tex.outputs['Color'],mix.inputs[2]);links.new(mix.outputs[0],p.inputs['Base Color'])
        bump=nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.08;bump.inputs['Distance'].default_value=.012
        links.new(tex.outputs['Color'],bump.inputs['Height']);links.new(bump.outputs[0],p.inputs['Normal'])
        dynamic_materials.append(dict(object=ob.name,image=str(path),mapping='Normalized component-local BOX; TexCoord object is this moving component',metallic=.5,roughness=.7))
        return mat
    # Keep all contact transforms, add anchored fabrication detail around them.
    for name in contact_names:
        o=bpy.data.objects[name]
        mat=steel if any(w in name for w in ('rail','contact_face','slide')) else oxide
        if name.startswith(('YARD_clamp_slide_', 'YARD_retaining_wedge_')):
            mat=moving_hardware_material(o)
        o.data.materials.clear();o.data.materials.append(mat)
        if name.startswith('YARD_contact_pad_'):
            x,y,z=o.location
            # The retained .1m cap's top was coplanar with the full body top.
            # Give its center footprint to the cap and retain a physical rim.
            body=o.data;vertices=[tuple(v.co) for v in body.vertices]
            faces=[tuple(f.vertices) for f in body.polygons if f.normal.z<.9]
            top=max(v.co.z for v in body.vertices)
            outer=[(min(v.co.x for v in body.vertices),min(v.co.y for v in body.vertices),top),
                   (max(v.co.x for v in body.vertices),min(v.co.y for v in body.vertices),top),
                   (max(v.co.x for v in body.vertices),max(v.co.y for v in body.vertices),top),
                   (min(v.co.x for v in body.vertices),max(v.co.y for v in body.vertices),top)]
            inverse=o.matrix_world.inverted()
            inner=[tuple(inverse@Vector((x+dx,y+dy,5))) for dx,dy in ((-4.7,-4.7),(4.7,-4.7),(4.7,4.7),(-4.7,4.7))]
            start=len(vertices);vertices+=outer+inner
            for i in range(4):faces.append((start+i,start+(i+1)%4,start+4+(i+1)%4,start+4+i))
            replacement=bpy.data.meshes.new(o.name+' cap ownership');replacement.from_pydata(vertices,[],faces);replacement.materials.append(mat);replacement.update();o.data=replacement
            for sx in (-1,1):
                box('pad external rib',(x+sx*5.12,y,2.3),(.24,8.8,4.4),steel)
                for sy in (-1,1):
                    box('foundation anchor',(x+sx*4.2,y+sy*4.2,.23),(.55,.55,.46),steel,.04)
            for zz in (.7,3.5):box('pad horizontal reinforcement',(x,y-5.1,zz),(9.6,.3,.3),steel)
    for x in (-84,84):
        for y in (-55,55):
            box('service bollard foundation',(x,y,.14),(1.3,1.3,.28),concrete)
            box('service bollard',(x,y,1.15),(.45,.45,2.0),yellow)
    # A pair of small physical service cabinets and pipework at the apron rear.
    for x in (-100,95):
        box('service cabinet',(x,84,1.6),(3,1.4,3.2),oxide)
        box('cabinet recessed panel',(x,83.27,1.65),(2.5,.08,2.2),steel,.01)
        for i in range(6):box('cabinet ventilation louvre',(x,83.20,.9+i*.15),(2.2,.08,.045),steel,.01)

    # Reuse transparent distant forest artwork on a buried skirt. The fixed
    # exterior view only sees this distant aspect; close industry stays geometry.
    forest=BASE/'forest-reveal-realism/assets/far-realism-v1.png'
    m=bpy.data.materials.new(PREFIX+'distant forest retained artwork');m.use_nodes=True
    n,l=m.node_tree.nodes,m.node_tree.links;n.clear()
    out=n.new('ShaderNodeOutputMaterial');tex=n.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(forest),check_existing=True)
    emit=n.new('ShaderNodeEmission');l.new(tex.outputs['Color'],emit.inputs[0]);emit.inputs[1].default_value=.7
    transparent=n.new('ShaderNodeBsdfTransparent');mix=n.new('ShaderNodeMixShader');l.new(tex.outputs['Alpha'],mix.inputs[0]);l.new(transparent.outputs[0],mix.inputs[1]);l.new(emit.outputs[0],mix.inputs[2]);l.new(mix.outputs[0],out.inputs[0])
    m.surface_render_method='DITHERED'
    forestobj=mesh('far forest and hills', [(-5000,3000,-400),(5000,3000,-400),(5000,3000,300),(-5000,3000,300)],[(0,1,2,3)],m)
    uv=forestobj.data.uv_layers.new()
    for li,v in enumerate(((0,0),(1,0),(1,1),(0,1))):uv.data[li].uv=v
    sky=BASE/'managed-land-realism/assets/sky-realism-v1.png'
    sm=bpy.data.materials.new(PREFIX+'retained daylight sky');sm.use_nodes=True
    sn,sl=sm.node_tree.nodes,sm.node_tree.links;sn.clear()
    sout=sn.new('ShaderNodeOutputMaterial');st=sn.new('ShaderNodeTexImage')
    st.image=bpy.data.images.load(str(sky),check_existing=True)
    se=sn.new('ShaderNodeEmission');sl.new(st.outputs['Color'],se.inputs[0]);sl.new(se.outputs[0],sout.inputs[0])
    skyobj=mesh('far daylight sky', [(-12000,6000,-1000),(12000,6000,-1000),(12000,6000,10000),(-12000,6000,10000)],[(0,1,2,3)],sm)
    suv=skyobj.data.uv_layers.new()
    for li,v in enumerate(((0,0),(1,0),(1,1),(0,1))):suv.data[li].uv=v
    # Return a contact invariant for the integrating builder to assert.
    bpy.context.view_layer.update()
    corridor_bounds=[-300,150,-110,110]
    violations=[];minimum_y=float('inf');bounds=[]
    for ob in imported:
        if ob.type!='MESH':continue
        points=[ob.matrix_world@Vector(v) for v in ob.bound_box]
        low=[min(p[i] for p in points) for i in range(3)]
        high=[max(p[i] for p in points) for i in range(3)]
        minimum_y=min(minimum_y,low[1])
        if low[0]<150 and high[0]>-300 and low[1]<110 and high[1]>-110:
            violations.append(ob.name)
        bounds.append(dict(object=ob.name,minimum=low,maximum=high))
    if violations:raise AssertionError('District encroaches landing and approach corridor: '+str(violations))
    clearance=dict(reserved_bounds_xy=corridor_bounds,minimum_imported_y=minimum_y,
                   minimum_y_clearance=minimum_y-110,violations=violations,
                   proof='Conservative world AABB exclusion for all imported physical district and traffic meshes at evaluated contact; all imported geometry lies beyond reserved apron/approach Y boundary.',
                   pad_and_service_details='Retained contact rig and named service cabinets/bollards intentionally belong to apron.')
    changed=[n for n in contact_names if any(abs(bpy.data.objects[n].matrix_world[r][c]-contacts[n][r][c])>1e-6 for r in range(4) for c in range(4))]
    if changed:raise AssertionError('Contact transform changed: '+str(changed))
    return dict(source=str(PORT),source_coordinate_translation=list(OFFSET),source_inverse_root_discarded=True,
                reused_objects=[o.name for o in imported],new_objects=[o.name for o in created],hidden_placeholders=hidden,
                restored_atlas_material_slots=restored,contact_transforms=contacts,contact_changes=changed,corridor_clearance=clearance,dynamic_component_materials=dynamic_materials,
                ground_contract='Original 240x220 apron owns its footprint; neighboring tessellation excludes it and shares Z0 boundary.',
                landscape=str(forest),sky=str(sky),traffic_source_frames=[400,448],traffic_retimed_frames=[580,774],limitations='Atlas fallback physical materials; distant forest retained limited-aspect artwork; new-camera architecture finish belongs to parent build.')
