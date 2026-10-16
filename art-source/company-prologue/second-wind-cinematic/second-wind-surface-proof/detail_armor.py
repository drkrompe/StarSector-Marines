"""Physical plate hierarchy on retained pressure mesh; isolated authoring proof."""
from pathlib import Path
import bpy,math,random,json
from mathutils import Vector
OUT=Path(__file__).resolve().parent
STAGES=OUT/'build'/'stages';STAGES.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(OUT/'build'/'baselines'/'second-wind-textured-proof.blend'))
scene=bpy.context.scene;scene.frame_set(1)
root=bpy.data.objects['SECOND WIND | flight root'];hull=bpy.data.objects['Main hull - level datum Z0']
surface_parts=[o for o in root.children_recursive if o.type=='MESH' and o.name.startswith(('Main hull','Forebody replaceable armor','Engine room armored shoulder'))]
for o in list(bpy.data.objects):
    if o.name.startswith(('Spall ','Salvage patch ','Repair plate captive')):bpy.data.objects.remove(o,do_unlink=True)
random.seed(81010)
mats=[bpy.data.materials['ARRIVAL | '+r] for r in ('oxide','cream','gunmetal','backing','exposed weld')]
col=bpy.data.collections.new('ARRIVAL | physical armor courses');scene.collection.children.link(col)
geometry=[([],[]) for _ in mats]
bolts=([],[]);plate_count=0;missing=0
scars=[(45,5.6,4.3,2.8),(-16,7.2,5.3,3.3),(32,-3.9,3.8,2.3)]
def cast(u,v,face):
    if face==0:org=Vector((u,v,100));direction=Vector((0,0,-1))
    else:org=Vector((u,face*100,v));direction=Vector((0,-face,0))
    candidates=[]
    for ob in surface_parts:
        matrix=root.matrix_world.inverted()@ob.matrix_world
        inv=matrix.inverted()
        hit,p,n,idx=ob.evaluated_get(bpy.context.evaluated_depsgraph_get()).ray_cast(inv@org,inv.to_3x3()@direction)
        if hit:
            p=matrix@p;n=(matrix.to_3x3()@n).normalized();candidates.append((p,n))
    return min(candidates,key=lambda pn:(pn[0]-org).length) if candidates else None
def add(poly,role,center,normal):
    if (Vector(poly[2])-Vector(poly[0])).cross(Vector(poly[4])-Vector(poly[2])).dot(normal)<0:poly=list(reversed(poly))
    verts,faces=geometry[role];start=len(verts);verts.extend(poly)
    verts.append(tuple(center));ci=len(verts)-1
    faces.extend((ci,start+j,start+(j+1)%len(poly)) for j in range(len(poly)))
def fastener(p,n):
    verts,faces=bolts;i=len(verts);p=p+n*.13
    a=n.cross(Vector((1,0,0))).normalized();b=n.cross(a).normalized()
    verts.extend([tuple(p+a*.085),tuple(p+b*.085),tuple(p-a*.085),tuple(p-b*.085),tuple(p+n*.075)])
    faces.extend([(i,i+1,i+4),(i+1,i+2,i+4),(i+2,i+3,i+4),(i+3,i,i+4)])
for face in (1,-1,0):
    v=-8 if face else -33
    while v<(13.5 if face else 34):
        height=random.uniform(2.0,3.4) if face else random.uniform(3.2,5.0)
        u=-36+random.uniform(-2,2)
        while u<56:
            width=random.uniform(3.2,5.8);x=u+width/2;z=v+height/2
            is_missing=face and any(((x-a)/rx)**2+((z-b)/rz)**2<1 for a,b,rx,rz in scars)
            margin=.11;ch=.32
            uv=[(u+margin+ch,v+margin),(u+width-margin-ch,v+margin),(u+width-margin,v+margin+ch),
                (u+width-margin,v+height-margin-ch),(u+width-margin-ch,v+height-margin),
                (u+margin+ch,v+height-margin),(u+margin,v+height-margin-ch),(u+margin,v+margin+ch)]
            uv=[p for j,a in enumerate(uv) for p in (a,((a[0]+uv[(j+1)%8][0])/2,(a[1]+uv[(j+1)%8][1])/2))]
            hits=[cast(a,b,face) for a,b in uv]
            center=cast(x,z,face)
            if center:
                normal=center[1]
                if (face==0 and normal.z<abs(normal.y)) or (face!=0 and abs(normal.y)<=normal.z):
                    u+=width;continue
            if all(hits) and center:
                # Exclude spans which bridge a bay or cross to a different shell face.
                if max((p-center[0]).length for p,n in hits)<width+height:
                    # Plate courses stop at major armor steps: do not bridge them into fins.
                    if max((n-center[1]).length for p,n in hits)>.70:
                        u+=width;continue
                    if is_missing:
                        missing+=1
                        add([tuple(p+n*.16) for p,n in hits],3,center[0]+center[1]*.16,center[1])
                    else:
                        near=face and any(((x-a)/rx)**2+((z-b)/rz)**2<2 for a,b,rx,rz in scars)
                        poly=[]
                        for j,(p,n) in enumerate(hits):
                            # Local buckled corners at damage boundaries, no target rings.
                            lift=random.uniform(.15,.21) if near and j%3==0 else .15
                            poly.append(tuple(p+n*lift))
                        role=1 if random.random()<(.3 if face else .38) else 0
                        if random.random()<.07:role=2
                        add(poly,role,center[0]+center[1]*.15,center[1]);plate_count+=1
                        for j in (0,4,8,12):fastener(hits[j][0]+hits[j][1]*.13,hits[j][1])
            u+=width
        v+=height
def make(name,data,mat,thickness=0):
    verts,faces=data;me=bpy.data.meshes.new(name);me.from_pydata(verts,[],faces);me.update()
    ob=bpy.data.objects.new(name,me);col.objects.link(ob);ob.parent=root;me.materials.append(mat)
    if thickness:
        sol=ob.modifiers.new('Sacrificial plate thickness','SOLIDIFY');sol.thickness=thickness
        sol.offset=1
        bevel=ob.modifiers.new('Straight manufactured chamfer','BEVEL');bevel.width=.045;bevel.segments=1
    return ob
for i,data in enumerate(geometry):
    if data[0]:make('Physical armor | '+mats[i].name,data,mats[i],.07 if i<3 else .035)
make('Armor captive fasteners | combined mesh',bolts,mats[4])
# Add exposed splint/strap seams across a few damage-adjacent backing tiles.
for face in (1,-1):
    for x,z in [(42,5),(-17,7),(33,-4)]:
        for dx in (-2.4,2.4):
            hits=[cast(x+dx+a,z+b,face) for a,b in [(-.18,-2.5),(.18,-2.5),(.18,2.5),(-.18,2.5)]]
            if all(hits):make('Emergency backing splint',([tuple(p+n*.36) for p,n in hits],[(0,1,2,3)]),mats[2],.18)
# Fine service panel seams on the actual pod, dorsal blocks and bay frame.
seamverts=[];seamfaces=[];servicebolts=([],[])
def component_hit(ob,a,b,top=False):
    matrix=root.matrix_world.inverted()@ob.matrix_world
    org=Vector((a,b,100)) if top else Vector((a,100,b))
    direction=Vector((0,0,-1)) if top else Vector((0,-1,0))
    hit,p,n,idx=ob.evaluated_get(bpy.context.evaluated_depsgraph_get()).ray_cast(org,direction)
    return (matrix@p,(matrix.to_3x3()@n).normalized()) if hit else None
def seam_rect(ob,a,b,w,h,top=False):
    uv=[(a-w/2,b-h/2),(a+w/2,b-h/2),(a+w/2,b+h/2),(a-w/2,b+h/2)]
    for j,(x,z) in enumerate(uv):
        nx,nz=uv[(j+1)%4];dx=nx-x;dz=nz-z;length=math.hypot(dx,dz);px=-dz/length*.025;pz=dx/length*.025
        hits=[component_hit(ob,u,v,top) for u,v in [(x+px,z+pz),(nx+px,nz+pz),(nx-px,nz-pz),(x-px,z-pz)]]
        if all(hits):
            start=len(seamverts);seamverts.extend(tuple(p+n*.012) for p,n in hits);seamfaces.append(tuple(range(start,start+4)))
        hit=component_hit(ob,x,z,top)
        if hit:fastener(hit[0]+hit[1]*.035,hit[1])
for ob in list(root.children_recursive):
    if ob.type!='MESH':continue
    if ob.name.startswith('PORT SERVICE POD -'):
        for x in (-5.9,0,5.9):seam_rect(ob,x,0,4.6,3.8)
        for x in (-5.9,0,5.9):seam_rect(ob,x,0,4.6,4.4,True)
    elif ob.name.startswith(('Fore pressure casing','Aft machinery casing')):
        for x in (-5,0,5):seam_rect(ob,x,0,4.2,8.2,True)
    elif ob.name.startswith('Bay structural buttress'):
        seam_rect(ob,0,0,.8,9)
    elif ob.name.startswith('Bay coaming'):
        for x in (-9,-3,3,9):seam_rect(ob,x,0,4,.4)
make('Service panel recessed seam inlays',(seamverts,seamfaces),mats[3])
# Rebuild fasteners after the small hardware seam pass, retaining a single mesh.
old=bpy.data.objects.get('Armor captive fasteners | combined mesh')
if old:bpy.data.objects.remove(old,do_unlink=True)
make('Armor captive fasteners | combined mesh',bolts,mats[4])
scene.camera=bpy.data.objects['PROOF | port side landing surface']
scene.cycles.samples=32
bpy.ops.wm.save_as_mainfile(filepath=str(STAGES/'second-wind-refined-surface-proof.blend'))
(OUT/'armor-detail-metrics.json').write_text(json.dumps({'physical_armor_plates':plate_count,'exposed_backing_tiles':missing,
 'fasteners':len(bolts[0])//5,'retained_pressure_hull_uncut':True,'damage':'Missing external armor tiles and buckled surrounding corners, external backing splints. Coarse circular rings removed.',
 'limitation':'Surface-panel preparation; no actual pressure-hull cavity cutting. Repeating source microtexture, generalized seams and simplified hardpoints remain.'},indent=2))
for tag,cam in [('refined-port-side','PROOF | port side landing surface'),('refined-port-quarter','PROOF | port bow quarter')]:
    scene.camera=bpy.data.objects[cam];scene.render.filepath=str(OUT/(tag+'.png'));bpy.ops.render.render(write_still=True)
