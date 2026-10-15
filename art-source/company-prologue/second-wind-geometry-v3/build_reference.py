"""Rebuild the editable Second Wind geometry guide with Blender's Python.

Run: blender --background --factory-startup --python build_reference.py
Output is source art only. Coordinates are hull-local, +X bow, +Y port, +Z dorsal.
The vanilla engine XY positions and relative widths are retained; Z and hull
depth are proposed art geometry, not canonical game dimensions.
"""
from pathlib import Path
import json
import math
import bpy
from mathutils import Vector

OUT = Path(__file__).resolve().parent
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

def material(name, color, metallic=0.0):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['Roughness'].default_value = .65
    return m

cream = material('Chalked cream armor', (.56, .52, .41), .3)
orange = material('Oxide orange armor', (.38, .16, .065), .3)
dark = material('Recess and machinery', (.055, .065, .072), .5)
steel = material('Engine collars', (.19, .21, .22), .65)
podmat = material('Port pod - muted teal identification', (.13, .32, .31), .35)
lightmat = material('Bay work lights', (.8, .7, .42))

def finish(obj, name, mat, bevel=0):
    obj.name = name
    obj.data.materials.append(mat)
    if bevel:
        b = obj.modifiers.new('Manufactured edge radii', 'BEVEL')
        b.width = bevel
        b.segments = 3
        obj.modifiers.new('Weighted corner normals', 'WEIGHTED_NORMAL')
    return obj

def box(name, loc, dims, mat, bevel=.3):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    obj = bpy.context.object
    obj.dimensions = dims
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return finish(obj, name, mat, bevel)

def cylinder(name, loc, radius, depth, mat, axis='Z', vertices=32):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
    obj = bpy.context.object
    if axis == 'X':
        obj.rotation_euler[1] = math.pi / 2
    elif axis == 'Y':
        obj.rotation_euler[0] = math.pi / 2
    return finish(obj, name, mat, .15)

# Planform follows Cerberus's wide forebody, narrow waist, two aft sponsons
# and central stern notch. The port shoulder pod is a SEPARATE low solid.
corners = [(57,8),(53,18),(43,25),(28,28),(3,22),(1,35),
           (-30,36),(-36,31),(-29,10),(-27,0),(-29,-10),
           (-37,-30),(-30,-35),(2,-34),(3,-22),(30,-28),(47,-20),(57,-8)]
# Broad manufactured flats with short rounded corner transitions. Avoid the
# continuous spline shoulders that made the previous guide feel organic.
outline=[]
for i in range(len(corners)):
    p0,p1,p2=[Vector(corners[j % len(corners)]) for j in (i-1,i,i+1)]
    a=p1+(p0-p1)*.14
    b=p1+(p2-p1)*.14
    for k in range(5):
        t=k/4
        p=(1-t)**2*a+2*(1-t)*t*p1+t*t*b
        outline.append(tuple(p))
rings = [(-8,.78),(-6,.93),(-3,1.0),(3,1.0),
         (6,.94),(8,.86),(9.2,.72),(9.7,.52)]
THICKNESS_STATIONS=[(-40,1.15),(-29,1.50),(-6,1.50),(3,1.10),
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

verts = [(x*s,y*s,shell_z(x*s,z)) for z,s in rings for x,y in outline]
n = len(outline)
bottom_center=len(verts)
verts.append((10,0,shell_z(10,-8)))
top_center=len(verts)
verts.append((10,0,9.7*thickness_at(10)))
faces = [(bottom_center,(i+1)%n,i) for i in range(n)]
for r in range(len(rings)-1):
    for i in range(n):
        faces.append((r*n+i,r*n+(i+1)%n,(r+1)*n+(i+1)%n,(r+1)*n+i))
last=(len(rings)-1)*n
faces += [(top_center,last+i,last+(i+1)%n) for i in range(n)]
mesh = bpy.data.meshes.new('Cerberus shaped pressure hull')
mesh.from_pydata(verts, [], faces)
mesh.update()
hull = bpy.data.objects.new('Main hull - level datum Z0', mesh)
bpy.context.collection.objects.link(hull)
hull.data.materials.append(orange)

# Two distinct bays, not a through tunnel. Port open, starboard sealed.
for side in (1,-1):
    cutter = box('Temporary bay cutter', (14,side*25,-.5), (23,20,9), dark, 0)
    bpy.context.view_layer.objects.active = hull
    mod = hull.modifiers.new('Lateral shuttle aperture', 'BOOLEAN')
    mod.operation = 'DIFFERENCE'
    mod.object = cutter
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)
    box(('Port' if side==1 else 'Starboard')+' bay interior', (14,side*15.3,-.5), (22,.5,8.6), dark, .1)
    box('Bay level floor', (14,side*20,-5), (23,10,.5), steel, .1)
    box('Bay ceiling', (14,side*20,4.1), (23,10,.5), dark, .1)
    for x in (2,26):
        box('Bay jamb', (x,side*23,-.5), (1.2,3,10), cream, .2)
    for z in (-5.5,4.6):
        box('Bay coaming', (14,side*23,z), (25,3,1), cream, .2)
    if side==-1:
        box('Starboard bay sealed sectional door', (14,-23,-.4), (22,.8,8.5), steel, .15)
        for x in (6,10,14,18,22):
            box('Closed door stiffener', (x,-23.6,-.4), (.25,.3,8), orange, .05)
    else:
        for x in (6,22):
            box('Port bay interior lamp', (x,16,3.2), (2,.4,.5), lightmat, .05)
            box('Empty maintenance cradle', (x,19,-4.4), (1.2,6,.7), steel, .1)
for poly in hull.data.polygons:
    poly.use_smooth = True
b = hull.modifiers.new('Hull edge radii', 'BEVEL')
b.width = .3
b.segments = 3
hull.modifiers.new('Hull normals', 'WEIGHTED_NORMAL')

# Build actual layered geometry, not only a weathered pattern on a flat shell.
bpy.context.view_layer.update()
def hull_surface(x,y):
    hit,p,normal,index=hull.evaluated_get(bpy.context.evaluated_depsgraph_get()).ray_cast(Vector((x,y,100)),Vector((0,0,-1)))
    return (p,normal) if hit else None

def armor_patch(name,x0,x1,y0,y1,mat,offset=.22):
    y0,y1=sorted((y0,y1))
    pv=[]
    count=5
    for i in range(count):
        for j in range(count):
            x=x0+(x1-x0)*i/(count-1)
            y=y0+(y1-y0)*j/(count-1)
            hit=hull_surface(x,y)
            if hit is None:
                return
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

for x in (23,32,41):
    for y in (-23,-14,5,14):
        armor_patch('Forebody replaceable armor',x,x+8,y,y+8,cream if (x+y)%3 else orange)
for x in (-28,-19,-10):
    for y in (-30,-21,13,22):
        armor_patch('Engine room armored shoulder',x,x+8,y,y+8,cream if x==-19 else orange)
for x in (29,40):
    armor_patch('Forebody reinforcing band',x,x+1.1,-24,24,steel,.7)
for x in (-24,-13):
    for side in (-1,1):
        armor_patch('Engine room reinforcing band',x,x+1.1,side*13,side*32,steel,.7)

# A stepped service spine: massive fore/aft cases, lower exposed machinery
# amidships. Its changing height follows the hull, not a flat plank.
for name,x,length,z in [('Fore pressure casing',34,18,15),('Aft machinery casing',-18,20,15)]:
    box(name,(x,0,z),(length,11,4),cream,1.5)
    for px in (x-5,x,x+5):
        box('Casing access cover',(px,0,z+2.1),(4.1,8,.5),cream,.25)
box('Recessed central service bed',(10,0,11.7),(22,10,1.5),dark,.5)
for y in (-3,0,3):
    cylinder('Exposed longitudinal service trunk',(10,y,13),.9,19,steel,'X')
for x in (3,11,19):
    box('Service trunk support hoop',(x,0,13.6),(1.1,10,1.2),cream,.3)
for x,y in ((30,21),(30,-21),(-22,25),(-22,-25)):
    hit=hull_surface(x,y)
    if hit:
        p,normal=hit
        obj=box('Shoulder louver vent',tuple(p+normal*.6),(6,4,1),dark,.25)
        obj.rotation_euler=normal.to_track_quat('Z','Y').to_euler()
        for dx in (-2,-1,0,1,2):
            slat=box('Vent louver',(x+dx,y,p.z+1.25),(.3,3.6,.25),steel,.05)
for side in (-1,1):
    # Keep these reinforcements clear of the existing bay aperture and pod.
    for x in (1,27):
        box('Bay structural buttress',(x,side*23,-.5),(2,3.5,14),steel,.5)
    for x in (-28,-18,-8):
        box('Aft flank access housing',(x,side*35.4,0),(6,2.4,6),cream,.7)
    cylinder('Protected waist service line',(14,side*23,6.6),.7,21,steel,'X')

# A proposed service/airlock pod, not a standing mast: long axis +X,
# centered below the top of the pressure hull and physically on PORT only.
box('Port service pod neck', (37,28,1), (12,8,4), steel, .6)
pod = box('PORT SERVICE POD - fixed low horizontal body', (37,33,1), (19,8,6), podmat, 2)
cylinder('Pod outward access hatch', (37,37.1,1), 2.2, .4, steel, 'Y')
box('Pod dorsal inspection plate', (37,33,4.1), (10,4,.25), cream, .3)

# Vanilla warhound.ship / Cerberus engineSlots: exact XY and width ratios.
# Four fixed nozzles, two lateral pairs, all at Z=0 and pointed aft (-X).
engines = [(-41,31,12),(-39,24,8),(-39,-24,8),(-41,-31,12)]
for i,(x,y,width) in enumerate(engines,1):
    # Engine-slot width describes a 2D effect, not a solid nozzle diameter.
    # Preserve the 3:2 ratio while leaving clearance between solid collars.
    radius = width*.31
    cylinder(f'Engine {i} body', (x+6,y,0), radius, 8, steel, 'X')
    # A genuine open nozzle mesh: large aft mouth and narrower dark throat.
    ev=[]
    for ex,rr in ((x-2,radius),(x+2,radius*.66)):
        ev += [(ex,y+rr*math.cos(t*math.tau/48),rr*math.sin(t*math.tau/48)) for t in range(48)]
    ef=[(t,(t+1)%48,48+(t+1)%48,48+t) for t in range(48)]
    em=bpy.data.meshes.new(f'Engine {i} nozzle mesh')
    em.from_pydata(ev,[],ef)
    em.update()
    eo=bpy.data.objects.new(f'Engine {i} aft-facing nozzle',em)
    bpy.context.collection.objects.link(eo)
    eo.data.materials.append(dark)
    cylinder(f'Engine {i} dark throat', (x+1.95,y,0), radius*.66,.1,dark,'X')
    bpy.ops.mesh.primitive_torus_add(major_radius=radius,minor_radius=.25,major_segments=48,minor_segments=10,location=(x-2,y,0),rotation=(0,math.pi/2,0))
    finish(bpy.context.object,f'Engine {i} mouth rim',steel)

# Four vanilla mount locations: three small, one medium. Raised housings.
for i,(x,y,size) in enumerate([(-11,20,2.6),(-11,-20,2.6),(41,11,2.6),(36,-16,3.8)],1):
    bpy.context.view_layer.update()
    hit,location,normal,index=hull.evaluated_get(bpy.context.evaluated_depsgraph_get()).ray_cast(Vector((x,y,100)),Vector((0,0,-1)))
    if not hit:
        raise RuntimeError(f'Turret {i} has no supporting hull surface')
    z=location.z+.4
    cylinder(f'Turret {i} shallow ring',(x,y,z),size+1,.8,steel)
    box(f'Turret {i} raised armored housing',(x,y,z+1.6),(size*2,size*1.7,2.6),steel,.7)
    cylinder(f'Turret {i} forward barrel',(x+size+2,y,z+1.6),.38 if size<3 else .6,5,steel,'X',16)

scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.samples=24
scene.cycles.use_denoising=True
scene.render.resolution_x=960
scene.render.resolution_y=440
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.world.color=(.7,.7,.7)
scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.74,.76,.78,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.65
scene.view_settings.view_transform='AgX'
scene.render.film_transparent=False
for name,loc,energy,size in [('Key',(60,70,120),240000,90),('Fill',(-40,-60,70),160000,80)]:
    bpy.ops.object.light_add(type='AREA',location=loc)
    obj=bpy.context.object
    obj.name=name
    obj.data.energy=energy
    obj.data.shape='DISK'
    obj.data.size=size
    obj.rotation_euler=(-obj.location).to_track_quat('-Z','Y').to_euler()

# Each camera observes the SAME objects. No per-view geometry transforms.
cameras=[('port-quarter',(100,130,100),163),('top',(0,0,180),205),
         ('port-side',(0,180,0),132),('stern',(-180,0,0),112)]
for name,pos,scale in cameras:
    bpy.ops.object.camera_add(location=pos)
    camera=bpy.context.object
    camera.name='Camera '+name
    target=Vector((5,0,1))
    camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
    if name=='top':
        # Bow points left, as in the port-side view.
        camera.rotation_euler=(0,0,math.pi)
        camera.location=(5,0,180)
    camera.data.type='ORTHO'
    camera.data.ortho_scale=scale
    scene.camera=camera
    scene.render.filepath=str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

scene.camera=bpy.data.objects['Camera port-quarter']
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'second-wind-geometry.blend'))
manifest={
    'status':'Proposed geometry reference; no scene art or gameplay changes',
    'axes':{'+X':'bow','+Y':'port','+Z':'dorsal'},
    'units':'Vanilla hull-space units; no canonical metre scale asserted',
    'revision':'V3: heavier forebody and engine rooms; recessed waist; layered armor and service machinery',
    'thickness_profile':{'method':'Upper Z uses plateaus and linear chamfer transitions between stations; lower hull has uniform 1.25 depth multiplier for level keel', 'stations':THICKNESS_STATIONS, 'deck_datum':0, 'attachments':'Engine and port-pod coordinates unchanged from V2'},
    'engine_source':'starsector-core/data/hulls/warhound.ship engineSlots',
    'engines':[{'location':[x,y,0],'width':w,'exhaust_direction':[-1,0,0]} for x,y,w in engines],
    'pod':{'center':[37,33,1],'dimensions':[19,8,6],'long_axis':'+X','function':'Proposed service/airlock pod; not canonical lore'},
    'cameras':[{'name':name,'position':list(pos),'orthographic_scale':scale} for name,pos,scale in cameras],
    'bay_state':{'port':'open, empty cradle','starboard':'sealed, awaits repair'},
    'mesh_authority':'All views render the same objects; generated surface art is subordinate to this geometry.'
}
(OUT/'geometry.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
