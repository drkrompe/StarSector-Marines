"""Mesh-owned precinct surfaces and fitted terrain boundaries in source coordinates.

Roads subdivide the precinct's sole top mesh; they never overlay another top.
The terrain is triangulated before cutting to preserve its nonplanar surface.
Boundary vertices are fitted to the precinct height, grading outward to original
terrain over ``grade_width`` metres. No transparent masking or hidden lower deck.
"""
import bpy
import bmesh
import json
from mathutils import Vector

DEFAULT_BOUNDS = (-1780., -160., -80., 650.)

def inside(x, y, bounds, tolerance=0.):
    a,b,c,d=bounds
    return a-tolerance <= x <= b+tolerance and c-tolerance <= y <= d+tolerance

def repair_ground(scene, precinct_bounds=DEFAULT_BOUNDS, roads=(), z=.4, grade_width=30.):
    """Replace old deck; cut terrain and construct one material-partitioned top.

    Bounds are (xmin,xmax,ymin,ymax), in inverse-source-flight coordinates.
    New bounds must contain the old hole; rebuilding a smaller footprint requires
    restoring terrain from its uncut source rather than inventing its surface.
    Roads are dictionaries with ``bounds`` and a Blender ``material``.
    """
    w=bpy.data.objects['LAYERED | inverse source flight']
    land=bpy.data.objects['Jangala | continuous landform']
    old=json.loads(land.get('port_pad_cutout','[-1745,-195,65,495]'))
    a,b,c,d=precinct_bounds
    if not (a<=old[0] and b>=old[1] and c<=old[2] and d>=old[3]):
        raise ValueError('New precinct must contain the existing terrain cutout')
    concrete=bpy.data.materials['PORT REALISM | freight concrete']
    for obj in list(scene.objects):
        if obj.name.startswith(('PORT DETAIL | single industrial precinct surface','PORT GROUND |')):
            bpy.data.objects.remove(obj,do_unlink=True)
    source=land.data
    source.calc_loop_triangles()
    mesh=bpy.data.meshes.new('Fitted terrain exact source triangles')
    mesh.from_pydata([v.co[:] for v in source.vertices],[],[tuple(t.vertices) for t in source.loop_triangles])
    for material in source.materials: mesh.materials.append(material)
    for p,t in zip(mesh.polygons,source.loop_triangles):p.material_index=t.material_index
    land.data=mesh
    bm=bmesh.new();bm.from_mesh(mesh)
    transform=w.matrix_world.inverted()@land.matrix_world
    inverse=transform.inverted()
    for v in bm.verts:v.co=transform@v.co
    # Explicit grade ring ensures terrain has vertices within the grading band.
    for axis,values in ((0,(a-grade_width,a,b,b+grade_width)),(1,(c-grade_width,c,d,d+grade_width))):
        normal=Vector((1,0,0) if axis==0 else (0,1,0))
        for value in values:
            point=Vector((0,0,0));point[axis]=value
            bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),dist=1e-5,plane_co=point,plane_no=normal)
    covered=[f for f in bm.faces if inside(f.calc_center_median().x,f.calc_center_median().y,precinct_bounds)]
    bmesh.ops.delete(bm,geom=covered,context='FACES')
    fitted=[];grade_return=[]
    for v in bm.verts:
        x,y=v.co.x,v.co.y
        dx=max(a-x,0,x-b);dy=max(c-y,0,y-d)
        distance=(dx*dx+dy*dy)**.5
        if abs(distance-grade_width)<.001:
            grade_return.append(list(v.co))
        if distance < grade_width:
            factor=distance/grade_width
            v.co.z=z+(v.co.z-z)*factor
            if distance<1e-3:fitted.append(list(v.co))
        v.co=inverse@v.co
    bm.to_mesh(mesh);bm.free();mesh.update()
    land['port_pad_cutout']=json.dumps(precinct_bounds)
    # Use a shared grid so neighboring material cells share exact vertices.
    xs=sorted({a,b,*[max(a,min(b,r['bounds'][i])) for r in roads for i in (0,1)]})
    ys=sorted({c,d,*[max(c,min(d,r['bounds'][i])) for r in roads for i in (2,3)]})
    vertices=[(x,y,z) for y in ys for x in xs]; faces=[];indices=[]
    materials=[concrete]
    for r in roads:
        if r['material'] not in materials:materials.append(r['material'])
    for j in range(len(ys)-1):
        for i in range(len(xs)-1):
            index=j*len(xs)+i
            faces.append((index,index+1,index+1+len(xs),index+len(xs)))
            mx=(xs[i]+xs[i+1])/2;my=(ys[j]+ys[j+1])/2
            chosen=next((r['material'] for r in reversed(roads) if inside(mx,my,r['bounds'])),concrete)
            indices.append(materials.index(chosen))
    top=bpy.data.meshes.new('Single ground owner with road material cells');top.from_pydata(vertices,[],faces)
    for m in materials:top.materials.append(m)
    uv=top.uv_layers.new(name='source metres / 20')
    for p,mi in zip(top.polygons,indices):
        p.material_index=mi
        for li in p.loop_indices:
            v=top.vertices[top.loops[li].vertex_index].co
            uv.data[li].uv=(v.x/20,v.y/20)
    obj=bpy.data.objects.new('PORT GROUND | continuous precinct and roads',top)
    scene.collection.objects.link(obj);obj.parent=w;obj.pass_index=12
    report=dict(bounds=list(precinct_bounds),top_z=z,grade_width=grade_width,fitted_boundary_vertices=fitted,grade_return_source_vertices=grade_return,road_cells=len(faces),terrain_faces=len(mesh.polygons),owner=obj.name)
    obj['surface_contract']=json.dumps(report)
    bpy.context.view_layer.update()
    return report
