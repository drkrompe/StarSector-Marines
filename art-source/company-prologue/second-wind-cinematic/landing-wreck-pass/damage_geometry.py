"""Localized lost sacrificial armor and conforming impact backing; pressure shell intact."""
from pathlib import Path
import json, math, random
import bpy, bmesh
from mathutils import Vector
OUT=Path(__file__).resolve().parent
# Root-local positions on the actual starboard skin, away from bay/engine/supports.
ZONES=[('aft broad spall',-24,4.6,7.4,4.3,121),('aft upper impact',-9,9.1,4.4,2.8,315),('fore impact basin',43,4.5,6.7,4.6,577),('fore lower scar',34,-4.1,4.4,2.4,809)]

def apply_damage(scene):
    scene.frame_set(744);bpy.context.view_layer.update()
    root=bpy.data.objects['SECOND WIND | flight root']
    collection=bpy.data.collections.new('WRECK PASS | localized sacrificial armor loss')
    scene.collection.children.link(collection)
    surfaces=[o for o in root.children_recursive if o.type=='MESH' and o.name.startswith(('Main hull','Forebody replaceable armor','Engine room armored shoulder'))]
    dg=bpy.context.evaluated_depsgraph_get()
    def cast(x,z):
        origin=Vector((x,-100,z));direction=Vector((0,1,0));hits=[]
        for ob in surfaces:
            matrix=root.matrix_world.inverted()@ob.matrix_world;inv=matrix.inverted()
            hit,p,n,index=ob.evaluated_get(dg).ray_cast(inv@origin,inv.to_3x3()@direction)
            if hit:
                p=matrix@p;n=(matrix.to_3x3().inverted().transposed()@n).normalized()
                if n.y<-.25:hits.append((p,n))
        return min(hits,key=lambda hit:(hit[0]-origin).length) if hits else None
    def material(name,color,metal):
        m=bpy.data.materials.new('WRECK PASS | '+name);m.use_nodes=True
        p=m.node_tree.nodes.get('Principled BSDF');p.inputs['Base Color'].default_value=(*color,1);p.inputs['Roughness'].default_value=.82;p.inputs['Metallic'].default_value=metal
        noise=m.node_tree.nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=2.1;noise.inputs['Detail'].default_value=3
        coord=m.node_tree.nodes.new('ShaderNodeTexCoord');coord.object=root;m.node_tree.links.new(coord.outputs['Object'],noise.inputs['Vector'])
        ramp=m.node_tree.nodes.new('ShaderNodeValToRGB')
        ramp.color_ramp.elements[0].position=.23;ramp.color_ramp.elements[0].color=(*(c*.28 for c in color),1)
        ramp.color_ramp.elements[1].position=.77;ramp.color_ramp.elements[1].color=(*(min(c*1.7,1) for c in color),1)
        m.node_tree.links.new(noise.outputs['Fac'],ramp.inputs[0]);m.node_tree.links.new(ramp.outputs[0],p.inputs['Base Color'])
        bump=m.node_tree.nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.4;bump.inputs['Distance'].default_value=.085
        m.node_tree.links.new(noise.outputs['Fac'],bump.inputs['Height']);m.node_tree.links.new(bump.outputs['Normal'],p.inputs['Normal'])
        return m
    backing=material('sealed oxidized impact backing',(.075,.055,.038),.7)
    source_backing=bpy.data.materials.get('ARRIVAL | backing')
    if source_backing:
        backing=source_backing.copy();backing.name='WRECK PASS | retained neutral backing atlas'
    exposed=material('sheared raw armor edge',(.24,.23,.20),.8)
    tornpaint=material('spalled oxide laminate',(.22,.072,.025),.5)
    salvage=material('welded salvage restraint',(.28,.27,.22),.8)
    def make(name,verts,faces,mat):
        me=bpy.data.meshes.new(name);me.from_pydata(verts,[],faces);me.update()
        ob=bpy.data.objects.new('WRECK PASS | '+name,me);collection.objects.link(ob);ob.parent=root;me.materials.append(mat)
        # Generated face winding is corrected against the starboard side.
        bm=bmesh.new();bm.from_mesh(me);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
        return ob
    # Polygonal boundaries intentionally irregular, shared by the cutout and replacement backing.
    boundaries=[]
    for name,x,z,rx,rz,seed in ZONES:
        rng=random.Random(seed);boundary=[]
        for i in range(18):
            angle=2*math.pi*i/18;radius=rng.uniform(.83,1.15)
            boundary.append((x+math.cos(angle)*rx*radius,z+math.sin(angle)*rz*radius))
        boundaries.append(boundary)
    def inside(x,z,points):
        yes=False;j=len(points)-1
        for i,(xi,zi) in enumerate(points):
            xj,zj=points[j]
            if (zi>z)!=(zj>z) and x<(xj-xi)*(z-zi)/(zj-zi)+xi:yes=not yes
            j=i
        return yes
    # Local concavity in the actual exterior skin, with a continuous sealed mesh.
    # Subdivide only impact-adjacent starboard faces, preserving interpolated UVs.
    base_changes=[]
    for ob in surfaces:
        matrix=root.matrix_world.inverted()@ob.matrix_world;inv=matrix.inverted()
        bm=bmesh.new();bm.from_mesh(ob.data)
        selected=[]
        for face in bm.faces:
            center=matrix@face.calc_center_median()
            if center.y<0 and any(((center.x-x)/(rx*1.3))**2+((center.z-z)/(rz*1.3))**2<1 for _,x,z,rx,rz,_ in ZONES):selected.append(face)
        if not selected:bm.free();continue
        old_vertices=len(bm.verts)
        edges=set(e for face in selected for e in face.edges if (matrix.to_3x3()@(e.verts[1].co-e.verts[0].co)).length>.9)
        if edges:bmesh.ops.subdivide_edges(bm,edges=list(edges),cuts=5,use_grid_fill=True)
        moved=0;max_depth=0
        for v in bm.verts:
            p=matrix@v.co
            if p.y>=0:continue
            depth=0
            for _,x,z,rx,rz,_ in ZONES:
                radius=math.sqrt(((p.x-x)/(rx*.95))**2+((p.z-z)/(rz*.95))**2)
                if radius<1:depth=max(depth,1.8*(1-radius*radius)**1.35)
            if depth:
                p.y+=depth;v.co=inv@p;moved+=1;max_depth=max(max_depth,depth)
        if moved:
            ob.data=ob.data.copy();bm.to_mesh(ob.data);ob.data.update()
            base_changes.append({'mesh':ob.name,'operation':'Locally tessellated closed exterior skin and depressed starboardimpact vertices inward +Y; no deleted pressure faces.','original_vertex_count':old_vertices,'final_vertex_count':len(bm.verts),'displaced_vertices':moved,'max_root_local_inward_depth':max_depth})
        bm.free()
    bpy.context.view_layer.update();dg=bpy.context.evaluated_depsgraph_get()
    changes=[]
    for ob in list(root.children_recursive):
        if ob.type!='MESH' or not ob.name.startswith(('Physical armor |','Armor captive fasteners |')):continue
        ob.data=ob.data.copy();bm=bmesh.new();bm.from_mesh(ob.data);matrix=root.matrix_world.inverted()@ob.matrix_world
        removed=[]
        for face in bm.faces:
            center=matrix@face.calc_center_median()
            if center.y<0 and any(inside(center.x,center.z,b) for b in boundaries):removed.append(face)
        if removed:
            count=len(removed);bmesh.ops.delete(bm,geom=removed,context='FACES');bm.to_mesh(ob.data);ob.data.update();changes.append({'mesh':ob.name,'removed_sacrificial_faces':count})
        bm.free()
        if ob.name.startswith('Physical armor |'):
            inv=matrix.inverted();moved=0
            for vertex in ob.data.vertices:
                p=matrix@vertex.co
                if p.y>=0:continue
                for _,x,z,rx,rz,_ in ZONES:
                    radius=math.sqrt(((p.x-x)/rx)**2+((p.z-z)/rz)**2)
                    if .85<radius<1.42:
                        hit=cast(p.x,p.z)
                        if hit:
                            weight=max(0,1-abs(radius-1.04)/.38)
                            vertex.co=inv@(p+hit[1]*(.60*weight**1.4));moved+=1
                        break
            if moved:changes.append({'mesh':ob.name,'buckled_surviving_sacrificial_vertices':moved,'max_root_local_buckle':.60})
            ob.data.update()
    zones=[]
    for spec,boundary in zip(ZONES,boundaries):
        name,x,z,rx,rz,seed=spec;rng=random.Random(seed+1)
        # Each ring follows a ray onto the actual retained skin. Its depressed center is
        # sealed metal above that shell, not an invented hole through an occupied volume.
        vertices=[];rings=[]
        for scale in (.03,.34,.7,1.0):
            ring=[]
            for bx,bz in boundary:
                xx=x+(bx-x)*scale;zz=z+(bz-z)*scale;hit=cast(xx,zz)
                if hit is None:raise ValueError('No retained hull beneath '+name)
                p,n=hit
                offset=.035+(.1*scale**2)+rng.uniform(0,.045)
                ring.append(len(vertices));vertices.append(tuple(p+n*offset))
            rings.append(ring)
        faces=[]
        for a,b in zip(rings,rings[1:]):
            for i in range(len(a)):j=(i+1)%len(a);faces.append((a[i],a[j],b[j],b[i]))
        faces.append(tuple(reversed(rings[0])));make(name+' | recessed sealed backing',vertices,faces,backing)
        # Separate broken plate teeth: irregular interrupted edges, not a smooth torus.
        rv=[];rf=[]
        for i,(bx,bz) in enumerate(boundary):
            if i%4==2:continue
            nx,nz=boundary[(i+1)%len(boundary)]
            points=[(bx,bz,1),(nx,nz,1),(x+(nx-x)*1.12,z+(nz-z)*1.12,0),(x+(bx-x)*1.12,z+(bz-z)*1.12,0)]
            start=len(rv)
            for xx,zz,inner in points:
                p,n=cast(xx,zz);lift=rng.uniform(.22,.62) if inner else .13
                rv.append(tuple(p+n*lift))
            rf.append((start,start+1,start+2,start+3))
            # Visible sheared thickness at each lifted broken edge.
            for index in (0,1):rv.append(tuple(Vector(rv[start+index])-cast(points[index][0],points[index][1])[1]*.11))
            rf.append((start,start+4,start+5,start+1))
        rim=make(name+' | torn plate lips',rv,rf,exposed);rim.data.materials.append(tornpaint)
        for poly in rim.data.polygons:
            poly.material_index=1 if poly.index%2==0 else 0
        # Two slender off-center repair restraints across the lost armor. They retain
        # a sealed backing surface; they do not imply the crater has been rebuilt.
        sv=[];sf=[]
        for dx in (-rx*.48,rx*.37):
            start=len(sv)
            for xx,zz in [(x+dx-.25,z-rz*.9),(x+dx+.25,z-rz*.9),(x+dx+.25,z+rz*.9),(x+dx-.25,z+rz*.9)]:
                p,n=cast(xx,zz);sv.append(tuple(p+n*.24))
            sf.append((start,start+1,start+2,start+3))
        strap=make(name+' | welded emergency restraints',sv,sf,salvage)
        solid=strap.modifiers.new('External salvage strap thickness','SOLIDIFY');solid.thickness=.10;solid.offset=1
        # Broken internal backing courses and shallow scores make the intact metal
        # legible as a buckled sealed boundary instead of a featureless black hole.
        cv=[];cf=[]
        for j in range(7):
            a=rng.uniform(0,math.tau);rr=rng.uniform(.1,.7);cx=x+math.cos(a)*rx*rr;cz=z+math.sin(a)*rz*rr
            width=rng.uniform(.6,1.6);height=rng.uniform(.1,.35);start=len(cv)
            for xx,zz in [(cx-width,cz-height),(cx+width,cz-height),(cx+width*.8,cz+height),(cx-width*.9,cz+height)]:
                p,n=cast(xx,zz);cv.append(tuple(p+n*rng.uniform(.15,.23)))
            cf.append((start,start+1,start+2,start+3))
        make(name+' | exposed buckled backing seams',cv,cf,exposed)
        zones.append({'name':name,'root_local_center_xz':[x,z],'radius_xz':[rx,rz],'boundary_xz':boundary,'backing_offset_above_retained_shell':.035,'torn_edge_lift_range':[.22,.62]})
    report={'status':'Physical local impact basins; exterior skin tessellated and indented continuously, sealed backing retained.', 'zones':zones,'modified_meshes':changes,'localized_exterior_skin_modifications':base_changes,'backing_material_source':source_backing.name if source_backing else 'procedural oxidized backing fallback','deformation_contract':{'coordinate_space':'original flight-root local','affected_side':'Y<0 only','inward_direction':'+Y','maximum_inward_displacement':1.8,'deformation_ellipse_scale':.95,'deformation_profile':'1.8*(1-r*r)**1.35 for r<1; r uses zone radius*.95; X and Z retained','topology':'Subdivide edges longer than .9 local units,5cuts,on starboard faces whose centers lie within zone ellipse expanded1.3. Original UVs interpolated by BMesh; no pressure faces deleted.'},'added_mesh_objects':len(collection.objects),'added_vertices':sum(len(o.data.vertices) for o in collection.objects),'preserved':'Overall accepted silhouette, bay apertures, engines, turrets, support/shoe/clamp contacts and all animation unchanged. Added geometry rigidly parented to original flight root.','limits':'Localized broken outer armor and continuous depressed exterior skin; sealed backing follows actual concavity. No open pressure-hull penetration or structurally simulated impacts claimed. Existing UV finish remains on retained geometry.'}
    (OUT/'damage-contract.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
    return report

if __name__=='__main__':
    apply_damage(bpy.context.scene)
    scene=bpy.context.scene;scene.render.filepath=str(OUT/'build/damage/contact.png');scene.cycles.samples=24
    bpy.ops.render.render(write_still=True)
