"""Optional fixed-view architecture finish; bounded moving-camera proof only.

Only camera-visible broad DISTRICT faces receive projected finished art. Geometry,
original UVs and original materials remain the fallback; no ground/card changes.
"""
from pathlib import Path
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree


def apply_architecture_finish(scene, world, path):
    path=Path(path)
    if not path.exists():return dict(applied=False,reason='Optional architecture finish image absent',path=str(path))
    old_frame=scene.frame_current;old_sub=scene.frame_subframe
    scene.frame_set(424);bpy.context.view_layer.update()
    camera=bpy.data.objects['04 PORT / side window'];camera_matrix=camera.matrix_world.copy()
    projector=bpy.data.objects.new('ARCHITECTURE FINISH | fixed source camera 424',None)
    scene.collection.objects.link(projector);projector.parent=world
    projector.matrix_parent_inverse.identity();projector.matrix_basis=world.matrix_world.inverted()@camera_matrix
    frame=camera.data.view_frame(scene=scene);hx=max(abs(v.x/v.z) for v in frame);hy=max(abs(v.y/v.z) for v in frame)
    image=bpy.data.images.load(str(path.resolve()),check_existing=True);image.alpha_mode='STRAIGHT'
    graph=bpy.context.evaluated_depsgraph_get();origin=camera_matrix.translation
    projected={};assigned=[];skipped=[]
    # Scene ray casting treats transparent landscape/viewport planes as solid.
    # Build an opaque architecture-only BVH to test actual building occlusion.
    bvhverts=[];bvhfaces=[];owners=[]
    for ob in scene.objects:
        if ob.type!='MESH' or not ob.name.startswith(('DISTRICT | ','SHIP CARD | ')) or ob.hide_render:continue
        offset=len(bvhverts);bvhverts.extend(ob.matrix_world@v.co for v in ob.data.vertices)
        ob.data.calc_loop_triangles()
        for tri in ob.data.loop_triangles:
            bvhfaces.append(tuple(offset+i for i in tri.vertices));owners.append(ob.name)
    architecture_bvh=BVHTree.FromPolygons(bvhverts,bvhfaces,all_triangles=True)


    def finish_material(original):
        key=original.name
        if key in projected:return projected[key]
        mat=original.copy();mat.name='ARCHITECTURE FINISH | '+original.name
        n,l=mat.node_tree.nodes,mat.node_tree.links
        output=next(v for v in n if v.type=='OUTPUT_MATERIAL' and v.is_active_output)
        prior=output.inputs['Surface'].links[0].from_socket
        co=n.new('ShaderNodeTexCoord');co.object=projector
        sep=n.new('ShaderNodeSeparateXYZ');l.new(co.outputs['Object'],sep.inputs[0])
        def mathnode(operation,a,b=None):
            q=n.new('ShaderNodeMath');q.operation=operation
            if hasattr(a,'node'):l.new(a,q.inputs[0])
            else:q.inputs[0].default_value=a
            if b is not None:
                if hasattr(b,'node'):l.new(b,q.inputs[1])
                else:q.inputs[1].default_value=b
            return q.outputs[0]
        depth=mathnode('MULTIPLY',sep.outputs['Z'],-1)
        u=mathnode('ADD',mathnode('MULTIPLY',mathnode('DIVIDE',sep.outputs['X'],depth),.5/hx),.5)
        v=mathnode('ADD',mathnode('MULTIPLY',mathnode('DIVIDE',sep.outputs['Y'],depth),.5/hy),.5)
        valid=mathnode('GREATER_THAN',depth,.01)
        for value in (mathnode('GREATER_THAN',u,.112),mathnode('LESS_THAN',u,.865),mathnode('GREATER_THAN',v,.175),mathnode('LESS_THAN',v,.965)):
            valid=mathnode('MULTIPLY',valid,value)
        vec=n.new('ShaderNodeCombineXYZ');l.new(u,vec.inputs['X']);l.new(v,vec.inputs['Y'])
        tex=n.new('ShaderNodeTexImage');tex.image=image;tex.extension='CLIP';l.new(vec.outputs[0],tex.inputs[0])
        emit=n.new('ShaderNodeEmission');l.new(tex.outputs['Color'],emit.inputs[0]);emit.inputs['Strength'].default_value=1
        mix=n.new('ShaderNodeMixShader');l.new(valid,mix.inputs[0]);l.new(prior,mix.inputs[1]);l.new(emit.outputs[0],mix.inputs[2]);l.new(mix.outputs[0],output.inputs['Surface'])
        mat['projection_limit']='Fixed frame424 source-camera finish; original shader outside conservative unobstructed viewport aperture and unselected faces'
        projected[key]=mat;return mat

    def visible(obj,point,allow_family=False):
        direction=point-origin;distance=direction.length
        location,normal,index,hitdistance=architecture_bvh.ray_cast(origin,direction.normalized(),distance+1)
        if location is None:return False
        if owners[index]==obj.name and (location-point).length<.7:return True
        if allow_family:
            family='DISTRICT | freight ' if 'freight ' in obj.name else 'DISTRICT | heavy overhaul hall'
            # Small clerestories/extractors belong to this continuous building.
            # Foreign building silhouettes still reject that sample.
            return owners[index].startswith(family)
        return False

    for obj in list(scene.objects):
        if obj.type!='MESH' or not obj.name.startswith('DISTRICT | ') or obj.hide_render:continue
        # Cables, portal trim, lattice hardware and utility piping retain native finish.
        words=('cable','pipe','ladder','rail','light','antenna','aerial','jamb','rib','stiffener','valve','brace','buttress','girder','column','leg','spine pier','terminal','spreader')
        if any(word in obj.name.lower() for word in words):continue
        source_points=[world.matrix_world.inverted()@(obj.matrix_world@v.co) for v in obj.data.vertices]
        if not source_points or max(v.y for v in source_points)>650:continue
        data=obj.data.copy();obj.data=data;normal_matrix=obj.matrix_world.to_3x3().inverted().transposed()
        old_materials=list(data.materials);material_map={};count=0
        for face in data.polygons:
            if face.area<12:continue
            center=obj.matrix_world@face.center;normal=(normal_matrix@face.normal).normalized()
            if normal.dot((origin-center).normalized())<.18:continue
            verts=[obj.matrix_world@data.vertices[i].co for i in face.vertices]
            # Do not splice projected/native appearance through a broad face.
            # Entire face must lie inside the unobstructed authored viewport.
            camera_inverse=camera_matrix.inverted()
            projected_vertices=[camera_inverse@v for v in verts]
            if any(v.z>=-.01 for v in projected_vertices):continue
            projected_uv=[(.5+v.x/(-v.z)*.5/hx,.5+v.y/(-v.z)*.5/hy) for v in projected_vertices]
            if not all(.112<u<.865 and .175<v<.965 for u,v in projected_uv):continue
            samples=[center]+[center.lerp(v,.78) for v in verts]
            broad_roof=obj.name.startswith(('DISTRICT | freight sawtooth roof','DISTRICT | freight transfer hall','DISTRICT | heavy overhaul hall'))
            votes=[visible(obj,p,allow_family=broad_roof) for p in samples]
            if (sum(votes)/len(votes)<.6) if broad_roof else not all(votes):continue
            original=old_materials[face.material_index] if face.material_index<len(old_materials) else None
            if original is None:continue
            if original.name not in material_map:
                data.materials.append(finish_material(original));material_map[original.name]=len(data.materials)-1
            face.material_index=material_map[original.name];count+=1
        if count:assigned.append(dict(object=obj.name,faces=count))
        else:skipped.append(obj.name)
    scene.frame_set(old_frame,subframe=old_sub)
    return dict(applied=True,path=str(path),projector=projector.name,frame=424,assigned=assigned,skipped=skipped,
                guard='Broad facing faces only; midpoint ray visibility at center and inset vertices; freight/overhaul broad faces permit60percent same-family visibility. Backfaces and outside-image samples retain original materials.',
                limitation='Single fixed projection is a limited-motion proof; new reveals and interpolation across partially occluded faces require endpoint visual review.')
