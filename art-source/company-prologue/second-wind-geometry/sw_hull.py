"""Main pressure hull, flank bays, layered armor and dorsal service spine.

Owns the planform outline and the upper-shell thickness profile. Locked
features (engines, port pod, the four mount XY positions and both bay
openings) are placed elsewhere and must stay supported by this shell.
"""
import bpy
from mathutils import Vector

# Planform follows Cerberus's wide forebody, narrow waist, two aft sponsons
# and central stern notch. The port shoulder pod is a SEPARATE low solid.
CORNERS = [(57,8),(53,18),(43,25),(28,28),(3,22),(1,35),
           (-30,36),(-36,31),(-29,10),(-27,0),(-29,-10),
           (-37,-30),(-30,-35),(2,-34),(3,-22),(30,-28),(47,-20),(57,-8)]
RINGS = [(-8,.78),(-6,.93),(-3,1.0),(3,1.0),
         (6,.94),(8,.86),(9.2,.72),(9.7,.52)]
THICKNESS_STATIONS = [(-40,1.15),(-29,1.50),(-6,1.50),(3,1.10),
                      (18,1.10),(26,1.50),(40,1.50),(52,1.15),(60,1.05)]


def thickness_at(x):
    # Plateaus and straight chamfer transitions, not inflated smooth lobes.
    for (x0,s0),(x1,s1) in zip(THICKNESS_STATIONS,THICKNESS_STATIONS[1:]):
        if x<=x1:
            t=max(0,min(1,(x-x0)/(x1-x0)))
            return s0+(s1-s0)*t
    return THICKNESS_STATIONS[-1][1]


def shell_z(x,z):
    return z*(1.25 if z<0 else thickness_at(x))


def outline():
    # Broad manufactured flats with short rounded corner transitions. Avoid the
    # continuous spline shoulders that made the previous guide feel organic.
    points=[]
    for i in range(len(CORNERS)):
        p0,p1,p2=[Vector(CORNERS[j % len(CORNERS)]) for j in (i-1,i,i+1)]
        a=p1+(p0-p1)*.14
        b=p1+(p2-p1)*.14
        for k in range(5):
            t=k/4
            p=(1-t)**2*a+2*(1-t)*t*p1+t*t*b
            points.append(tuple(p))
    return points


def build_shell(kit):
    ring_outline=outline()
    verts=[(x*s,y*s,shell_z(x*s,z)) for z,s in RINGS for x,y in ring_outline]
    n=len(ring_outline)
    bottom_center=len(verts)
    verts.append((10,0,shell_z(10,-8)))
    top_center=len(verts)
    verts.append((10,0,9.7*thickness_at(10)))
    faces=[(bottom_center,(i+1)%n,i) for i in range(n)]
    for r in range(len(RINGS)-1):
        for i in range(n):
            faces.append((r*n+i,r*n+(i+1)%n,(r+1)*n+(i+1)%n,(r+1)*n+i))
    last=(len(RINGS)-1)*n
    faces+=[(top_center,last+i,last+(i+1)%n) for i in range(n)]
    mesh=bpy.data.meshes.new('Cerberus shaped pressure hull')
    mesh.from_pydata(verts,[],faces)
    mesh.update()
    hull=bpy.data.objects.new('Main hull - level datum Z0',mesh)
    bpy.context.collection.objects.link(hull)
    hull.data.materials.append(kit.orange)
    kit.hull=hull
    return hull


def build_bays(kit):
    # Two distinct bays, not a through tunnel. Port open, starboard sealed.
    hull=kit.hull
    for side in (1,-1):
        cutter=kit.box('Temporary bay cutter',(14,side*25,-.5),(23,20,9),kit.dark,0)
        bpy.context.view_layer.objects.active=hull
        mod=hull.modifiers.new('Lateral shuttle aperture','BOOLEAN')
        mod.operation='DIFFERENCE'
        mod.object=cutter
        bpy.ops.object.modifier_apply(modifier=mod.name)
        bpy.data.objects.remove(cutter,do_unlink=True)
        kit.box(('Port' if side==1 else 'Starboard')+' bay interior',(14,side*15.3,-.5),(22,.5,8.6),kit.dark,.1)
        kit.box('Bay level floor',(14,side*20,-5),(23,10,.5),kit.steel,.1)
        kit.box('Bay ceiling',(14,side*20,4.1),(23,10,.5),kit.dark,.1)
        for x in (2,26):
            kit.box('Bay jamb',(x,side*23,-.5),(1.2,3,10),kit.cream,.2)
        for z in (-5.5,4.6):
            kit.box('Bay coaming',(14,side*23,z),(25,3,1),kit.cream,.2)
        if side==-1:
            kit.box('Starboard bay sealed sectional door',(14,-23,-.4),(22,.8,8.5),kit.steel,.15)
            for x in (6,10,14,18,22):
                kit.box('Closed door stiffener',(x,-23.6,-.4),(.25,.3,8),kit.orange,.05)
        else:
            for x in (6,22):
                kit.box('Port bay interior lamp',(x,16,3.2),(2,.4,.5),kit.lightmat,.05)
                kit.box('Empty maintenance cradle',(x,19,-4.4),(1.2,6,.7),kit.steel,.1)
    for poly in hull.data.polygons:
        poly.use_smooth=True
    b=hull.modifiers.new('Hull edge radii','BEVEL')
    b.width=.3
    b.segments=3
    hull.modifiers.new('Hull normals','WEIGHTED_NORMAL')


def armor_patch(kit,name,x0,x1,y0,y1,mat,offset=.22):
    y0,y1=sorted((y0,y1))
    pv=[]
    count=5
    for i in range(count):
        for j in range(count):
            x=x0+(x1-x0)*i/(count-1)
            y=y0+(y1-y0)*j/(count-1)
            hit=kit.surface(x,y)
            if hit is None:
                return None
            p,normal=hit
            pv.append(tuple(p+normal*offset))
    pf=[]
    for i in range(count-1):
        for j in range(count-1):
            a=i*count+j
            pf.append((a,a+count,a+count+1,a+1))
    me=bpy.data.meshes.new(name)
    me.from_pydata(pv,[],pf)
    me.update()
    ob=bpy.data.objects.new(name,me)
    bpy.context.collection.objects.link(ob)
    ob.data.materials.append(mat)
    for poly in me.polygons:
        poly.use_smooth=True
    sol=ob.modifiers.new('Armor plate thickness','SOLIDIFY')
    sol.thickness=.45
    sol.offset=1
    return ob


def build_armor_and_spine(kit):
    # Build actual layered geometry, not only a weathered pattern on a flat shell.
    for x in (23,32,41):
        for y in (-23,-14,5,14):
            armor_patch(kit,'Forebody replaceable armor',x,x+8,y,y+8,kit.cream if (x+y)%3 else kit.orange)
    for x in (-28,-19,-10):
        for y in (-30,-21,13,22):
            armor_patch(kit,'Engine room armored shoulder',x,x+8,y,y+8,kit.cream if x==-19 else kit.orange)
    for x in (29,40):
        armor_patch(kit,'Forebody reinforcing band',x,x+1.1,-24,24,kit.steel,.7)
    for x in (-24,-13):
        for side in (-1,1):
            armor_patch(kit,'Engine room reinforcing band',x,x+1.1,side*13,side*32,kit.steel,.7)

    # A stepped service spine: massive fore/aft cases, lower exposed machinery
    # amidships. Its changing height follows the hull, not a flat plank.
    # The fore pressure casing belongs to sw_bridge, which builds on it.
    for name,x,length,z in [('Aft machinery casing',-18,20,15)]:
        kit.box(name,(x,0,z),(length,11,4),kit.cream,1.5)
        for px in (x-5,x,x+5):
            kit.box('Casing access cover',(px,0,z+2.1),(4.1,8,.5),kit.cream,.25)
    kit.box('Recessed central service bed',(10,0,11.7),(22,10,1.5),kit.dark,.5)
    for y in (-3,0,3):
        kit.cylinder('Exposed longitudinal service trunk',(10,y,13),.9,19,kit.steel,'X')
    for x in (3,11,19):
        kit.box('Service trunk support hoop',(x,0,13.6),(1.1,10,1.2),kit.cream,.3)
    for x,y in ((30,21),(30,-21),(-22,25),(-22,-25)):
        hit=kit.surface(x,y)
        if hit:
            p,normal=hit
            obj=kit.box('Shoulder louver vent',tuple(p+normal*.6),(6,4,1),kit.dark,.25)
            obj.rotation_euler=normal.to_track_quat('Z','Y').to_euler()
            for dx in (-2,-1,0,1,2):
                kit.box('Vent louver',(x+dx,y,p.z+1.25),(.3,3.6,.25),kit.steel,.05)
    for side in (-1,1):
        # Keep these reinforcements clear of the existing bay aperture and pod.
        for x in (1,27):
            kit.box('Bay structural buttress',(x,side*23,-.5),(2,3.5,14),kit.steel,.5)
        for x in (-28,-18,-8):
            kit.box('Aft flank access housing',(x,side*35.4,0),(6,2.4,6),kit.cream,.7)
        kit.cylinder('Protected waist service line',(14,side*23,6.6),.7,21,kit.steel,'X')


def build(kit):
    build_shell(kit)
    build_bays(kit)
    build_armor_and_spine(kit)
