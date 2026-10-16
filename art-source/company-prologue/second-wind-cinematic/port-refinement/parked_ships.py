"""Recognizable parked Hound/Venture extrapolations, not canonical hero models.

Vanilla .ship plan bounds and hardpoints own identity; provisional metre scale,
thickness and raised deck volumes are authored for this distant port view.
"""
from pathlib import Path
import json
import math
import bpy
from mathutils import Vector
from mathutils.geometry import tessellate_polygon

HERE=Path(__file__).resolve().parent


def build_parked_ships(scene, world, layout):
    reports=[]
    def solid(name,color,metal=.25):
        mat=bpy.data.materials.new('PARKED | '+name);mat.use_nodes=True
        p=mat.node_tree.nodes.get('Principled BSDF')
        p.inputs['Base Color'].default_value=(*color,1);p.inputs['Roughness'].default_value=.8;p.inputs['Metallic'].default_value=metal
        return mat
    steel=solid('weathered lower hull',(.105,.125,.125));trim=solid('oxide support framing',(.35,.18,.075))
    dark=solid('unlit engine cavity',(.018,.024,.025),.1);cream=solid('weathered bevel plating',(.30,.26,.19))
    # Side wear is physically shaded object-local steel. Broad repaired patches
    # and fine roughness retain readable dark hull mass at grazing port view.
    nodes,links=steel.node_tree.nodes,steel.node_tree.links
    noise=nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=.34;noise.inputs['Detail'].default_value=2
    co=nodes.new('ShaderNodeTexCoord');links.new(co.outputs['Object'],noise.inputs[0])
    ramp=nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].color=(.055,.065,.062,1);ramp.color_ramp.elements[1].color=(.20,.19,.16,1)
    links.new(noise.outputs['Fac'],ramp.inputs[0]);links.new(ramp.outputs[0],nodes.get('Principled BSDF').inputs['Base Color'])
    for ident,length,pos in [('venture',220,(-50,975)),('hound',90,(210,930))]:
        data=json.loads((HERE/'references'/(ident+'.ship')).read_text(encoding='utf-8'))
        bounds=list(zip(data['bounds'][0::2],data['bounds'][1::2]))
        xmin,xmax=min(p[0] for p in bounds),max(p[0] for p in bounds)
        scale=length/(xmax-xmin)
        # Deck registration uses original sprite plan, never a camera projection.
        generated=HERE/'assets'/(ident+'-skin-v1.png')
        fallback=HERE/'references'/('hound_base.png' if ident=='hound' else 'venture.png')
        if not fallback.exists():fallback=HERE/'references'/(ident+'.png')
        image_path=generated if generated.exists() else fallback
        deck=solid(ident+' registered top deck',(1,1,1))
        nodes,links=deck.node_tree.nodes,deck.node_tree.links
        tex=nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(image_path),check_existing=True);tex.extension='EXTEND'
        links.new(tex.outputs['Color'],nodes.get('Principled BSDF').inputs['Base Color'])
        # Alpha is silhouette guidance only: exact mesh owns opaque deck occlusion.
        root=bpy.data.objects.new('PARKED | '+ident+' source-plan root',None);scene.collection.objects.link(root);root.parent=world
        root.location=(pos[0],pos[1],0)
        objects=[]
        keel=7.5 if ident=='venture' else 5.8
        hullheight=17 if ident=='venture' else 9
        def mesh(name,verts,faces,mat,register=False):
            md=bpy.data.meshes.new(name);md.from_pydata(verts,[],faces);md.update();md.materials.append(mat)
            uv=md.uv_layers.new(name='sprite plan registration')
            for poly in md.polygons:
                for li in poly.loop_indices:
                    v=md.vertices[md.loops[li].vertex_index].co
                    uv.data[li].uv=((data['center'][0]-v.y/scale)/data['width'],(data['center'][1]+v.x/scale)/data['height'])
            ob=bpy.data.objects.new('PARKED | '+ident+' | '+name,md);scene.collection.objects.link(ob);ob.parent=root;ob.pass_index=15;objects.append(ob)
            return ob
        def polygon_volume(name,points,bottom,top,mat):
            n=len(points);vs=[(x*scale,y*scale,z) for z in (bottom,top) for x,y in points]
            # Tessellate actual nonconvex plan, preserving side recesses.
            poly=[Vector((x*scale,y*scale,top)) for x,y in points]
            triangles=tessellate_polygon([poly])
            lookup={tuple(v):i for i,v in enumerate(poly)}
            indices=[tuple(v if isinstance(v,int) else lookup[tuple(v)] for v in tri) for tri in triangles]
            faces=[tuple(v+n for v in tri) for tri in indices]
            faces += [tuple(reversed(tri)) for tri in indices]
            faces += [(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
            ob=mesh(name,vs,faces,mat)
            if mat==deck:
                # Top plan skin supplies only the top. Vertical extrusion walls
                # need metal shading rather than stretched repeated sprite pixels.
                ob.data.materials.append(steel)
                for face in ob.data.polygons:
                    if face.normal.z<.5:face.material_index=1
            return ob
        # Retain exact silhouette along widest shoulder; upper bevel inset is small.
        lower=polygon_volume('exact plan lower hull',bounds,keel,keel+hullheight*.72,steel)
        n=len(bounds);upper=[(x*.90,y*.90) for x,y in bounds]
        verts=[(x*scale,y*scale,keel+hullheight*.72) for x,y in bounds]+[(x*scale,y*scale,keel+hullheight) for x,y in upper]
        mesh('chamfered shoulder',verts,[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)],cream)
        polygon_volume('registered main deck',upper,keel+hullheight-.05,keel+hullheight,deck)
        # Continuous recessed belt and segmented vertical panel joints remain
        # attached to each actual nonconvex hull edge; no rectangular side slab.
        for i,((ax,ay),(bx,by)) in enumerate(zip(bounds,bounds[1:]+bounds[:1])):
            av=Vector((ax*scale,ay*scale,0));bv=Vector((bx*scale,by*scale,0));edge=bv-av
            outward=Vector((-edge.y,edge.x,0)).normalized()*.06
            # Both winding conventions stay outside by radial direction test.
            if outward.dot((av+bv)*.5)<0:outward=-outward
            aa=av+outward;bb=bv+outward;zz=keel+hullheight*.40
            mesh('hull recessed belt %d'%i,[(aa.x,aa.y,zz),(bb.x,bb.y,zz),(bb.x,bb.y,zz+.7),(aa.x,aa.y,zz+.7)],[(0,1,2,3)],dark)
            segments=max(1,int(edge.length/12))
            for j in range(1,segments):
                mid=aa.lerp(bb,j/segments);half=edge.normalized()*.16
                mesh('side plate joint',[(mid.x-half.x,mid.y-half.y,keel+.4),(mid.x+half.x,mid.y+half.y,keel+.4),
                     (mid.x+half.x,mid.y+half.y,keel+hullheight*.70),(mid.x-half.x,mid.y-half.y,keel+hullheight*.70)],[(0,1,2,3)],dark)

        if ident=='venture':
            raised=[('forward spine',[(-5,-30),(74,-25),(89,-13),(89,13),(74,25),(-5,30)],6),
                    ('aft engineering',[(-70,-25),(-14,-29),(-7,-18),(-7,18),(-14,29),(-70,25)],8),
                    ('port engineering',[(-74,52),(20,55),(34,68),(-10,86),(-73,85)],4),
                    ('starboard engineering',[(-74,-52),(20,-55),(34,-68),(-10,-86),(-73,-85)],4)]
        else:
            raised=[('forward cabin',[(0,-9),(35,-10),(41,10),(0,11)],5),
                    ('central engineering',[(-18,-10),(-2,-11),(9,-6),(7,8),(-19,10)],5),
                    ('port engine pod',[(-22,18),(12,18),(13,25),(-22,25)],2),
                    ('starboard engine pod',[(-21,-25),(12,-25),(13,-19),(-21,-19)],2)]
        for name,points,extra in raised:polygon_volume(name,points,keel+hullheight,keel+hullheight+extra,deck)
        def box(name,x,y,z,w,d,h,mat):
            points=[(x-w/2,y-d/2),(x+w/2,y-d/2),(x+w/2,y+d/2),(x-w/2,y+d/2)]
            return polygon_volume(name,points,z-h/2,z+h/2,mat)
        def cylinder(name,x,y,z,r,h,mat,axis='Z'):
            # All coordinates except vertical scale are in original plan units.
            steps=16;vs=[]
            for axial in (-h/2,h/2):
                for i in range(steps):
                    a=i*math.tau/steps
                    if axis=='X':vs.append((x*scale+axial,y*scale+math.cos(a)*r,z+math.sin(a)*r))
                    else:vs.append((x*scale+math.cos(a)*r,y*scale+math.sin(a)*r,z+axial))
            faces=[tuple(reversed(range(steps))),tuple(range(steps,2*steps))]+[(i,(i+1)%steps,(i+1)%steps+steps,i+steps) for i in range(steps)]
            return mesh(name,vs,faces,mat)
        # Engine outlets remain exact-slot aft faces, dark and unlit while parked.
        for i,slot in enumerate(data['engineSlots']):
            x,y=slot['location'];radius=slot['width']*scale*.22;zz=keel+hullheight*.43
            cylinder('engine %d metal collar'%i,x,y,zz,radius,2.8,steel,'X')
            cylinder('engine %d unlit nozzle'%i,x-1.6/scale,y,zz,radius*.77,.35,dark,'X')
        hardpoints=[]
        for slot in data['weaponSlots']:
            if slot['mount']=='HIDDEN':continue
            x,y=slot['locations'][:2]
            radius=(3.1 if slot['size']=='SMALL' else 5)*scale
            cylinder('hardpoint '+slot['id'],x,y,keel+hullheight+1.1,radius,2.2,steel)
            hardpoints.append(dict(id=slot['id'],source_location=[x,y]))
        # Stable four-point steel cradles. Pads touch the shared owning ground.
        contacts=[]
        for xx in (xmin*.53,xmax*.44):
            for yy in (-12,12):
                box('service support foot',xx,yy,.85,10,9,.9,trim)
                box('service support column',xx,yy,(1.3+keel)/2,3,3,keel-1.3,steel)
                box('service cradle saddle',xx,yy,keel-.7,14,7,1.4,cream)
                contacts.append(dict(source_xy=[pos[0]+xx*scale,pos[1]+yy*scale],bottom_z=.4,contact_gap=0))
        # Fixed maintenance stair: supported treads, stringers, platform and rails.
        side=max(y for x,y in bounds)+8
        for step in range(12):
            box('service stair tread',-3+step*1.3,side,.7+step*.8,1.6,4,.6,steel)
        box('service stair top platform',13,side,10.3,6,6,.7,trim)
        def stair_beam(name,a,b,width,mat):
            # Endpoints use metres after source-plan conversion; local transform
            # lets a rectangular section follow the actual sloped load path.
            av,bv=Vector(a),Vector(b);delta=bv-av
            ob=box(name,0,0,0,width/scale,width/scale,delta.length,mat)
            ob.location=(av+bv)/2
            ob.rotation_euler=delta.to_track_quat('Z','Y').to_euler()
            return ob
        for sign in (-1,1):
            rail_y=(side+sign*1.65)*scale
            stair_beam('stair load bearing stringer',(-3.8*scale,rail_y,.65),(13*scale,rail_y,9.95),.55,trim)
            for xx,zz in ((-3,.4),(1,3.0),(5,5.5),(9,8.0),(13,9.95)):
                # Every post rises from a tread/stringer to continuous handrail.
                stair_beam('stair handrail post',(xx*scale,rail_y,zz),(xx*scale,rail_y,zz+1.2),.12,steel)
            stair_beam('stair sloped handrail',(-3*scale,rail_y,1.6),(13*scale,rail_y,11.15),.15,steel)
        for xx in (10.5,15.5):
            for yy in (side-2.5,side+2.5):
                box('maintenance platform grounded foot',xx,yy,.65,1.8,1.8,.5,trim)
                box('maintenance platform column',xx,yy,(.9+9.95)/2,.7,.7,9.05,steel)
                box('maintenance platform rail post',xx,yy,10.9,.12,.12,1.2,steel)
        for yy in (side-2.5,side+2.5):
            stair_beam('maintenance platform long rail',(10.5*scale,yy*scale,11.5),(15.5*scale,yy*scale,11.5),.15,steel)
        stair_beam('maintenance platform end rail',(15.5*scale,(side-2.5)*scale,11.5),(15.5*scale,(side+2.5)*scale,11.5),.15,steel)

        report=dict(id=ident,root=root.name,objects=[o.name for o in objects],length_metres_provisional=length,
                    source_ship=str(HERE/'references'/(ident+'.ship')),sprite=str(fallback),top_texture=str(image_path),
                    engine_count=len(data['engineSlots']),hardpoints=hardpoints,contacts=contacts,
                    lower_hull_plan_bounds_preserved=True,raised_volumes_authored=True,uv_formula='u=(center.x-Y)/width; v=(center.y+X)/height',
                    limitations='Distant parked view extrapolation; raised volumes and thickness authored, not canonical hero turnaround.')
        reports.append(report)
    out=HERE/'build/parked-ships';out.mkdir(parents=True,exist_ok=True)
    result=dict(ships=reports,ground_z=.4,source_assets_untouched=True)
    (out/'geometry-provenance.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    return result
