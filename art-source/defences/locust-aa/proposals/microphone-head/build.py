"""Geometry-only proposal. Run in an isolated Blender background process.
The head is a capped cylinder, with every bore parallel to the forward axis.
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

cube('Rear magazine box', (0,-0.63,0.27), (1.12,1.0,0.46), armor)
cube('Rear service panel', (0,-0.76,0.52), (0.88,0.65,0.07), armor)
for side in (-1,1):
    cube('Compact side trunnion', (side*0.73,-0.27,0.31), (0.30,0.66,0.36), metal)
    cube('Hinge armor', (side*0.89,-0.27,0.33), (0.12,0.52,0.40), armor)
    for i in range(6):
        cube('Cooling rib', (side*0.59,-0.46-i*0.105,0.46), (0.14,0.045,0.08), metal, 0.01)

# Rounded microphone-style head: straight cylindrical skirt plus convex front cap.
# Local +Y is the firing axis; the entire head is elevated 25 degrees.
radius = 0.62
pitch = Matrix.Rotation(radians(25), 4, 'X')
origin = Vector((0,0.23,0.57))
def world(p):
    return origin + pitch.to_3x3() @ Vector(p)
axis = pitch.to_3x3() @ Vector((0,1,0))
ring_profile = [(-0.43, radius), (-0.30, radius), (0.22, radius)]
for i in range(1,17):
    theta = i*pi/2/17
    ring_profile.append((0.22+radius*sin(theta),radius*cos(theta)))
ring_profile.append((0.22+radius,0.002))
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
        mouth_y=0.22+sqrt(radius*radius-x*x-z*z)
        surface=world((x,mouth_y,z))
        bpy.ops.mesh.primitive_cylinder_add(vertices=32,radius=0.063,depth=0.76,location=surface-axis*0.25)
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
        bpy.ops.mesh.primitive_cylinder_add(vertices=24,radius=0.060,depth=0.005,location=surface-axis*0.60)
        floor=bpy.context.object
        floor.name='Recessed bore floor'
        floor.rotation_mode='QUATERNION'
        floor.rotation_quaternion=axis.to_track_quat('Z','Y')
        floor.data.materials.append(inside)
        objects.append(floor)
        ports.append((x,z))

bpy.ops.object.camera_add(location=(0,0,12))
camera=bpy.context.object
camera.data.type='ORTHO'
camera.data.ortho_scale=7.5
camera.rotation_euler=(0,0,0)
scene.camera=camera
for pos,energy,size in [((-3,4,7),850,4),((4,-2,6),300,5)]:
    bpy.ops.object.light_add(type='AREA',location=pos)
    light=bpy.context.object
    light.data.energy=energy
    light.data.shape='DISK'
    light.data.size=size
    light.rotation_euler=(Vector((0,0,0.3))-light.location).to_track_quat('-Z','Y').to_euler()

# Left: exact overhead. Right: same geometry tilted for explanatory inspection.
left=bpy.data.objects.new('Overhead presentation root',None)
scene.collection.objects.link(left)
right=bpy.data.objects.new('Angled presentation root',None)
scene.collection.objects.link(right)
for ob in objects:
    clone=ob.copy()
    clone.data=ob.data
    scene.collection.objects.link(clone)
    clone.parent=right
    ob.parent=left
left.location=(-1.78,-0.05,0)
right.location=(1.72,-0.05,0)
right.rotation_euler=(radians(45),radians(-15),radians(-20))
scene.render.filepath=str(HERE/'shape-views.png')
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'proposal.blend'))
bpy.ops.render.render(write_still=True)
print({'parallel_ports':len(ports),'pitch_degrees':25,'render':scene.render.filepath})
