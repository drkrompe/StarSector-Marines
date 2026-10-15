"""Geometry-only proposal. Run in an isolated Blender background process.
The elongated launch-tube head pitches between side trunnions over a yaw deck.
No ImageGen is invoked. Source geometry is retained for review and adjustment.
"""
from pathlib import Path
from math import sin, cos, pi, sqrt, radians
import bpy
from mathutils import Vector, Matrix

HERE = Path(__file__).resolve().parent
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 40
scene.render.resolution_x = 1920
scene.render.resolution_y = 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.film_transparent = True
scene.world.color = (0.18, 0.18, 0.18)
scene.view_settings.view_transform = 'AgX'
scene.render.use_file_extension = True

def mat(name, color, metallic=0.2):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    p = m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value = (*color, 1)
    p.inputs['Metallic'].default_value = metallic
    p.inputs['Roughness'].default_value = 0.6
    return m

armor = mat('Matte olive shape guide', (0.28, 0.32, 0.25))
metal = mat('Dark hinge metal', (0.10, 0.13, 0.12), 0.45)
inside = mat('Dark bore floor', (0.005, 0.008, 0.007))
objects = []

def cube(name, center, dims, material, bevel=0.04):
    bpy.ops.mesh.primitive_cube_add(size=1, location=center)
    ob = bpy.context.object
    ob.name = name
    ob.dimensions = dims
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    ob.data.materials.append(material)
    b = ob.modifiers.new('Armored edge radius', 'BEVEL')
    b.width = bevel
    b.segments = 3
    ob.modifiers.new('Weighted normals', 'WEIGHTED_NORMAL')
    objects.append(ob)
    return ob

def cylinder(name, center, radius, depth, material, direction=(0,0,1)):
    bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=radius, depth=depth, location=center)
    ob = bpy.context.object
    ob.name = name
    ob.rotation_mode = 'QUATERNION'
    ob.rotation_quaternion = Vector(direction).to_track_quat('Z','Y')
    ob.data.materials.append(material)
    edge = ob.modifiers.new('Bearing bevel', 'BEVEL')
    edge.width = 0.018
    edge.segments = 3
    ob.modifiers.new('Weighted normals', 'WEIGHTED_NORMAL')
    objects.append(ob)
    return ob

# The floor and cheeks rotate together in yaw. The head is centered over them.
deck_top = 0.33
axle_height = 1.32
preview_pitch = 20
maximum_pitch = 25
cylinder('Yaw bearing beneath floor', (0,0,0.09), 0.77, 0.18, metal)
cube('Gimbal floor', (0,0,0.22), (1.98,1.64,0.22), armor, 0.10)
cube('Rear drive cover', (0,-0.64,0.35), (0.96,0.30,0.12), armor)
for side in (-1,1):
    cube('Cheek support clipped to head', (side*0.80,0,0.82), (0.23,0.68,1.06), armor, 0.055)
    cylinder('Fixed side bearing', (side*0.79,0,axle_height), 0.255, 0.25, metal, (1,0,0))
    cylinder('Outer axle cap', (side*0.935,0,axle_height), 0.20, 0.07, armor, (1,0,0))
    cylinder('Axle center', (side*0.978,0,axle_height), 0.085, 0.025, metal, (1,0,0))
    for i in range(4):
        cube('Deck cooling rib', (side*0.71,-0.48-i*0.08,0.37), (0.24,0.035,0.06), metal, 0.008)

head_start = len(objects)
for side in (-1,1):
    cylinder('Head trunnion boss', (side*0.625,0,0), 0.22, 0.13, armor, (1,0,0))

# Rounded microphone-style head: straight cylindrical skirt plus convex front cap.
# Local +Y is the firing axis. All head parts share one actual pitch parent.
radius = 0.62
rear_end = -0.78
cap_start = 0.33
cap_depth = 0.59
def world(p):
    return Vector(p)
axis = Vector((0,1,0))
ring_profile = [(rear_end, radius), (-0.30, radius), (cap_start, radius)]
for i in range(1,17):
    theta = i*pi/2/17
    ring_profile.append((cap_start+cap_depth*sin(theta),radius*cos(theta)))
ring_profile.append((cap_start+cap_depth,0.002))
verts = []
faces = []
segments = 64
for y,r in ring_profile:
    for j in range(segments):
        angle = 2*pi*j/segments
        verts.append(tuple(world((r*cos(angle),y,r*sin(angle)))))
for row in range(len(ring_profile)-1):
    for j in range(segments):
        a=row*segments+j
        b=row*segments+(j+1)%segments
        faces.append((a,b,b+segments,a+segments))
faces.append(tuple(reversed(range(segments))))
faces.append(tuple((len(ring_profile)-1)*segments+j for j in range(segments)))
mesh=bpy.data.meshes.new('Capped cylindrical head mesh')
mesh.from_pydata(verts,[],faces)
mesh.update()
head=bpy.data.objects.new('Microphone-shaped launcher head',mesh)
scene.collection.objects.link(head)
head.data.materials.append(armor)
objects.append(head)
for p in head.data.polygons:
    p.use_smooth=True

# Parallel bores pierce the convex front cap. Their depths and openings follow
# the cap, but none points radially or vertically out of the upper shell.
ports=[]
for z,columns in [(-0.35,4),(-0.12,5),(0.12,5),(0.35,4)]:
    for col in range(columns):
        x=(col-(columns-1)/2)*0.20
        mouth_y=cap_start+cap_depth*sqrt(1-(x*x+z*z)/(radius*radius))
        # Bore through the elongated magazine to a common recessed rear plane.
        bore_end = rear_end + 0.10
        cutter_end = mouth_y + 0.13
        bore_depth = cutter_end - bore_end
        bore_center = world((x,(cutter_end+bore_end)/2,z))
        bpy.ops.mesh.primitive_cylinder_add(vertices=32,radius=0.063,depth=bore_depth,location=bore_center)
        cutter=bpy.context.object
        cutter.rotation_mode='QUATERNION'
        cutter.rotation_quaternion=axis.to_track_quat('Z','Y')
        bpy.context.view_layer.objects.active=head
        head.select_set(True)
        boolean=head.modifiers.new('Forward parallel launch bore','BOOLEAN')
        boolean.operation='DIFFERENCE'
        boolean.object=cutter
        bpy.ops.object.modifier_apply(modifier=boolean.name)
        bpy.data.objects.remove(cutter,do_unlink=True)
        bpy.ops.mesh.primitive_cylinder_add(vertices=24,radius=0.060,depth=0.005,location=world((x,bore_end+0.003,z)))
        floor=bpy.context.object
        floor.name='Recessed bore floor'
        floor.rotation_mode='QUATERNION'
        floor.rotation_quaternion=axis.to_track_quat('Z','Y')
        floor.data.materials.append(inside)
        objects.append(floor)
        ports.append((x,z))

yaw=bpy.data.objects.new('Yaw deck assembly',None)
scene.collection.objects.link(yaw)
elevation=bpy.data.objects.new('Head elevation pivot - shared X axle',None)
scene.collection.objects.link(elevation)
elevation.parent=yaw
elevation.location=(0,0,axle_height)
elevation.rotation_euler=(radians(preview_pitch),0,0)
limit=elevation.constraints.new('LIMIT_ROTATION')
limit.name='Restricted elevation for long rocket tubes'
limit.owner_space='LOCAL'
limit.use_limit_x=True
limit.min_x=0
limit.max_x=radians(maximum_pitch)
limit.use_limit_y=True
limit.use_limit_z=True
for index, ob in enumerate(objects):
    ob.parent=elevation if index >= head_start else yaw
objects.extend([yaw,elevation])

# Conservative shell-height checks at each degree of the restricted range.
# The rear cover is checked as an infinite plane, stricter than its footprint.
clearances={}
for angle in range(maximum_pitch+1):
    rotation=Matrix.Rotation(radians(angle),3,'X')
    minimum_z=min((rotation @ Vector(v)).z+axle_height for v in verts)
    clearance=minimum_z-deck_top
    assert clearance > 0.04, (angle,clearance)
    assert minimum_z-0.41 > 0.015, (angle,'rear cover',minimum_z-0.41)
    if angle in (0,10,20,maximum_pitch):
        clearances[angle]={'deck':round(clearance,3),'rear_cover':round(minimum_z-0.41,3)}

bpy.ops.object.camera_add(location=(0,0,12))
camera=bpy.context.object
camera.data.type='ORTHO'
camera.data.ortho_scale=8.9
camera.rotation_euler=(0,0,0)
scene.camera=camera
for pos,energy,size in [((-3,4,7),850,4),((4,-2,6),300,5)]:
    bpy.ops.object.light_add(type='AREA',location=pos)
    light=bpy.context.object
    light.data.energy=energy
    light.data.shape='DISK'
    light.data.size=size
    light.rotation_euler=(Vector((0,0,0.3))-light.location).to_track_quat('-Z','Y').to_euler()

# Three views preserve the rig: floor/cheeks in yaw, head on its own pitch axle.
left=bpy.data.objects.new('Overhead presentation root',None)
scene.collection.objects.link(left)
def duplicate_rig(name):
    root=bpy.data.objects.new(name,None)
    scene.collection.objects.link(root)
    copies={}
    for ob in objects:
        clone=ob.copy()
        scene.collection.objects.link(clone)
        copies[ob]=clone
    for ob,clone in copies.items():
        clone.parent=copies.get(ob.parent,root)
    return root,copies
angled,_=duplicate_rig('Angled presentation root')
side,side_objects=duplicate_rig('Side inspection root')
yaw.parent=left
left.location=(-2.75,-0.05,0)
angled.location=(0,-0.35,0)
angled.rotation_euler=(radians(48),radians(-15),radians(155))
side.location=(2.75,-0.85,0)
side.rotation_mode='QUATERNION'
side.rotation_quaternion=Matrix(((0,1,0),(0,0,1),(1,0,0))).to_quaternion()
scene.render.filepath=str(HERE/'shape-views.png')
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'proposal.blend'))
bpy.ops.render.render(write_still=True)
print({'parallel_ports':len(ports),'pitch_degrees':preview_pitch,
       'maximum_pitch_degrees':maximum_pitch,'head_length':round(cap_start+cap_depth-rear_end,2),
       'axle_height':axle_height,'floor_clearance_by_elevation':clearances,
       'render':scene.render.filepath})
