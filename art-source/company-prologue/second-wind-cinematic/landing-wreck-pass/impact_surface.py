"""Registered scar RGB on existing physical impact meshes; no added geometry."""
from pathlib import Path
import hashlib
import bpy
from bpy_extras.object_utils import world_to_camera_view

UV_NAME='ImpactGuideUV'
COVERAGE='ImpactGuideCoverage'


def apply_impact_surface(scene,path,registration=None):
    """Use immutable744 mesh-corner UVs; optional affine uses top-left pixels.

    Registration dictionary accepts affine_top_left_pixel_rows (2x3),
    guide_dimensions and native_dimensions, matching ship_view registration.
    """
    path=Path(path).resolve()
    scene.frame_set(744);bpy.context.view_layer.update()
    cam=scene.camera
    root=bpy.data.objects['SECOND WIND | flight root']
    image=bpy.data.images.load(str(path),check_existing=True);image.pack()
    dg=bpy.context.evaluated_depsgraph_get()
    cache={};covered=[];total=0

    def finished(original):
        if original in cache:return cache[original]
        material=original.copy();material.name='IMPACT FINISH | '+original.name
        nodes=material.node_tree.nodes;links=material.node_tree.links
        bsdf=next((n for n in nodes if n.type=='BSDF_PRINCIPLED'),None)
        if bsdf is None:
            cache[original]=original;return original
        socket=bsdf.inputs['Base Color']
        prior=socket.links[0].from_socket if socket.links else None
        uv=nodes.new('ShaderNodeUVMap');uv.uv_map=UV_NAME
        texture=nodes.new('ShaderNodeTexImage');texture.image=image;texture.extension='CLIP';texture.interpolation='Linear'
        links.new(uv.outputs[0],texture.inputs['Vector'])
        attr=nodes.new('ShaderNodeAttribute');attr.attribute_name=COVERAGE
        weight=nodes.new('ShaderNodeMath');weight.operation='MULTIPLY'
        links.new(attr.outputs['Fac'],weight.inputs[0]);links.new(texture.outputs['Alpha'],weight.inputs[1])
        mix=nodes.new('ShaderNodeMixRGB');links.new(weight.outputs[0],mix.inputs[0]);links.new(texture.outputs['Color'],mix.inputs[2])
        if prior:links.new(prior,mix.inputs[1])
        else:mix.inputs[1].default_value=socket.default_value
        links.new(mix.outputs[0],socket)
        material['scope']='Scar RGB only; existing physical normal/bump, roughness and metal response retained.'
        cache[original]=material;return material

    targets=[ob for ob in root.children_recursive if ob.type=='MESH' and ob.name.startswith('WRECK PASS |') and any(tag in ob.name for tag in ('recessed sealed backing','torn plate lips','exposed buckled backing seams'))]
    if not targets:raise ValueError('No existing physical impact scar meshes found')
    for ob in targets:
        mesh=ob.data
        uv=mesh.uv_layers.get(UV_NAME) or mesh.uv_layers.new(name=UV_NAME)
        attr=mesh.attributes.get(COVERAGE) or mesh.attributes.new(COVERAGE,'FLOAT','CORNER')
        normalmatrix=ob.matrix_world.to_3x3().inverted().transposed()
        count=0
        for polygon in mesh.polygons:
            total+=1
            center=ob.matrix_world@polygon.center
            normal=(normalmatrix@polygon.normal).normalized()
            # Thin sacrificial backing/rims are intentionally two-sided meshes;
            # winding sign is not reliable. Grazing rejection plus actual first-hit
            # visibility rejects occluded reverse surfaces without changing normals.
            facing=abs(normal.dot((cam.matrix_world.translation-center).normalized()))>.2
            projected=world_to_camera_view(scene,cam,center)
            valid=facing and projected.z>0 and 0<projected.x<1 and 0<projected.y<1
            visible=False
            if valid:
                for sample in [center]+[(ob.matrix_world@mesh.vertices[vi].co)*.86+center*.14 for vi in polygon.vertices]:
                    ray=sample-cam.matrix_world.translation
                    hit,point,_,_,hitob,_=scene.ray_cast(dg,cam.matrix_world.translation,ray.normalized(),distance=ray.length+.025)
                    if hit and hitob.original==ob.original and abs((point-cam.matrix_world.translation).length-ray.length)<.025:
                        visible=True;break
            if visible:count+=1
            for li in polygon.loop_indices:
                vertex=ob.matrix_world@mesh.vertices[mesh.loops[li].vertex_index].co
                p=world_to_camera_view(scene,cam,vertex);u,v=p.x,p.y
                if registration:
                    gx=u*registration['guide_dimensions'][0];gy=(1-v)*registration['guide_dimensions'][1]
                    a,b=registration['affine_top_left_pixel_rows']
                    u=(a[0]*gx+a[1]*gy+a[2])/registration['native_dimensions'][0]
                    v=1-(b[0]*gx+b[1]*gy+b[2])/registration['native_dimensions'][1]
                uv.data[li].uv=(u,v);attr.data[li].value=float(visible)
        if count:
            for i,m in enumerate(mesh.materials):
                if m and m.use_nodes:mesh.materials[i]=finished(m)
        covered.append({'name':ob.name,'covered_polygons':count,'tested_polygons':len(mesh.polygons)})
    return {'image':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'native_dimensions':list(image.size),'guide':'guides/impact-surface-guide.png','guide_dimensions':[1920,1080],'source_frame':744,'camera':cam.name,'camera_matrix_world':[list(row) for row in cam.matrix_world],'camera_matrix_ship_local':[list(row) for row in root.matrix_world.inverted()@cam.matrix_world],'registration':registration or 'Identity normalized full-canvas UV; guide/native hardpoint framing inspected.','uv_layer':UV_NAME,'coverage_attribute':COVERAGE,'objects':covered,'covered_polygons':sum(item['covered_polygons'] for item in covered),'tested_polygons':total,'geometry_added':0,'scope':'Existing new scar backing, torn rims and exposed backing seams only. Repair straps, ordinary ship, fittings and yard unchanged. UVs travel rigidly with actual hull.','limits':'Single aspect surface edit; sampled per-polygon visibility. Generated fine fractures and baked shading do not certify physical detail correspondence or a relightable multiangle texture. Existing physical roughness, bump and metal response retained.'}
