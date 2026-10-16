"""Give each compound ground footprint exactly one visible surface owner."""
import json
import bpy
import bmesh
from mathutils import Vector


def stabilize_compound_pad(scene, world):
    compound = bpy.data.objects['AGRI | right agricultural service compound']
    terrain = bpy.data.objects['Jangala | continuous landform']
    apron = bpy.data.objects['AGRI | compound broad apron']
    landing = bpy.data.objects['AGRI | landing service pad']

    def mask(nodes, links, rectangles):
        coords = nodes.new('ShaderNodeTexCoord')
        coords.object = compound
        split = nodes.new('ShaderNodeSeparateXYZ')
        links.new(coords.outputs['Object'], split.inputs[0])
        def math(op, a, b):
            node = nodes.new('ShaderNodeMath'); node.operation = op
            for socket,value in zip(node.inputs,(a,b)):
                if isinstance(value,(int,float)): socket.default_value=value
                else: links.new(value,socket)
            return node.outputs[0]
        result = None
        for xmin,xmax,ymin,ymax in rectangles:
            inside = math('MULTIPLY',math('GREATER_THAN',split.outputs['X'],xmin),math('LESS_THAN',split.outputs['X'],xmax))
            inside = math('MULTIPLY',inside,math('GREATER_THAN',split.outputs['Y'],ymin))
            inside = math('MULTIPLY',inside,math('LESS_THAN',split.outputs['Y'],ymax))
            result = inside if result is None else math('MAXIMUM',result,inside)
        return result

    def cut_covered_surface(obj, rectangles, owner):
        # Partition real geometry, preserving the original terrain shader and
        # its renderer depth/sorting behavior. No new transparency is introduced.
        for material in obj.data.materials:
            if material.get('surface_cutout_owner'):
                output=next(n for n in material.node_tree.nodes if n.type=='OUTPUT_MATERIAL')
                outer=output.inputs['Surface'].links[0].from_node
                if outer.type=='MIX_SHADER':
                    material.node_tree.links.new(outer.inputs[1].links[0].from_socket,output.inputs['Surface'])
                for key in ('surface_cutout_owner','surface_cutout_rectangles','surface_cutout_frame'):
                    if key in material:del material[key]
                # Existing source near-bank terrain already used BLENDED.
                if obj==terrain:material.surface_render_method='BLENDED'
        if obj==terrain and not obj.get('ground_reference_geometry_json'):
            obj['ground_reference_geometry_json']=json.dumps({
                'vertices':[list(v.co) for v in obj.data.vertices],
                'polygons':[list(p.vertices) for p in obj.data.polygons]})
        mesh=obj.data.copy();obj.data=mesh
        if obj==terrain:
            # Freeze Blender's exact original triangulation before clipping;
            # subdividing nonplanar quads first can change their surface.
            mesh.calc_loop_triangles()
            tris=[tuple(t.vertices) for t in mesh.loop_triangles]
            indices=[t.material_index for t in mesh.loop_triangles]
            triangulated=bpy.data.meshes.new(mesh.name+' exact triangles')
            triangulated.from_pydata([v.co[:] for v in mesh.vertices],[],tris)
            for material in mesh.materials:triangulated.materials.append(material)
            for polygon,index in zip(triangulated.polygons,indices):polygon.material_index=index
            obj.data=triangulated;mesh=triangulated
        bm=bmesh.new();bm.from_mesh(mesh)
        to_compound=compound.matrix_world.inverted() @ obj.matrix_world
        from_compound=to_compound.inverted()
        for vertex in bm.verts:vertex.co=to_compound @ vertex.co
        for xmin,xmax,ymin,ymax in rectangles:
            for point,normal in (((xmin,0,0),(1,0,0)),((xmax,0,0),(1,0,0)),
                                 ((0,ymin,0),(0,1,0)),((0,ymax,0),(0,1,0))):
                bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),
                    dist=1e-5,plane_co=Vector(point),plane_no=Vector(normal),
                    clear_inner=False,clear_outer=False)
            faces=[face for face in bm.faces if xmin+1e-5<face.calc_center_median().x<xmax-1e-5
                   and ymin+1e-5<face.calc_center_median().y<ymax-1e-5]
            bmesh.ops.delete(bm,geom=faces,context='FACES')
        for vertex in bm.verts:vertex.co=from_compound @ vertex.co
        bm.normal_update();bm.to_mesh(mesh);bm.free();mesh.update()
        obj['ground_geometry_cutout_owner']=owner
        obj['ground_geometry_cutout_rectangles']=json.dumps(rectangles)
    # The lower surface is absent beneath each slab; separation no longer
    # depends on depth-buffer precision at the 1.5km grazing camera distance.
    cut_covered_surface(terrain,[(-105,105,-77.5,77.5)],apron.name)
    cut_covered_surface(apron,[(38,86,-61,-13)],landing.name)

    # Paint the H and edge bars into the landing slab's own material. Raised
    # 8cm marker boxes only 1cm apart cannot remain depth-separated here.
    rectangles=[(46,78,-52.5,-51.5),(46,78,-22.5,-21.5),
                (51.5,52.5,-47,-27),(71.5,72.5,-47,-27),(52,72,-37.5,-36.5)]
    for material in landing.data.materials:
        nodes,links=material.node_tree.nodes,material.node_tree.links
        bsdf=next(n for n in nodes if n.type=='BSDF_PRINCIPLED')
        previous=bsdf.inputs['Base Color'].links[0].from_socket
        mix=nodes.new('ShaderNodeMixRGB')
        links.new(mask(nodes,links,rectangles),mix.inputs[0])
        links.new(previous,mix.inputs[1]);mix.inputs[2].default_value=(.58,.56,.43,1)
        links.new(mix.outputs[0],bsdf.inputs['Base Color'])
    hidden=[]
    for obj in compound.children_recursive:
        if any(part in obj.name for part in ('landing pad bar','landing H legs','landing H bridge')):
            obj.hide_render=True;hidden.append(obj.name)
    contract={'terrain_cutout':[-105,105,-77.5,77.5], 'apron_cutout':[38,86,-61,-13],
              'coordinate_frame':compound.name,'hidden_marker_meshes':hidden,
              'landing_markers':'Material masks on landing slab; no separate marker faces',
              'partition_method':'Exact mesh-plane bisect and removal of lower faces; source terrain shader unchanged'}
    compound['ground_surface_partition']=json.dumps(contract)
    return contract
