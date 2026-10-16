"""Source-world industrial traffic; +X is vehicle forward, metres and seconds."""
import json
import math
from pathlib import Path

import bpy
from mathutils import Vector

PREFIX = 'PORT TRAFFIC | '
START, END, SOURCE_FPS = 400.0, 448.0, 12.0


def _material(name, color, cell=None, metallic=0.0):
    material = bpy.data.materials.new(PREFIX + name)
    material.use_nodes = True
    nodes, links = material.node_tree.nodes, material.node_tree.links
    shader = nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = .78
    shader.inputs['Metallic'].default_value = metallic
    atlas = Path(__file__).resolve().parent.parent / 'port-approach-realism/assets/industrial-materials-v1.png'
    if cell is not None and atlas.exists():
        texture = nodes.new('ShaderNodeTexImage')
        texture.image = bpy.data.images.load(str(atlas), check_existing=True)
        coordinate = nodes.new('ShaderNodeTexCoord')
        mapping = nodes.new('ShaderNodeVectorMath')
        mapping.operation = 'MULTIPLY_ADD'
        mapping.inputs[1].default_value = (.46, .46, 1)
        mapping.inputs[2].default_value = (.02 + cell[0] * .5, .02 + cell[1] * .5, 0)
        links.new(coordinate.outputs['UV'], mapping.inputs[0])
        links.new(mapping.outputs[0], texture.inputs['Vector'])
        links.new(texture.outputs['Color'], shader.inputs['Base Color'])
    return material


def _paint(obj, material):
    obj.data.materials.append(material)
    obj.pass_index = 15
    # Each physical component owns stable face-local UVs throughout motion.
    uv = obj.data.uv_layers.active or obj.data.uv_layers.new()
    for face in obj.data.polygons:
        axis = max(range(3), key=lambda i: abs(face.normal[i]))
        axes = [i for i in range(3) if i != axis]
        points = [obj.data.vertices[obj.data.loops[i].vertex_index].co for i in face.loop_indices]
        bounds = [(min(p[a] for p in points), max(p[a] for p in points)) for a in axes]
        for index, point in zip(face.loop_indices, points):
            uv.data[index].uv = tuple((point[a] - lo) / max(hi - lo, .001) for a, (lo, hi) in zip(axes, bounds))
    return obj


def _box(parent, name, position, dimensions, material, bevel=.10):
    bpy.ops.mesh.primitive_cube_add(size=1)
    obj = bpy.context.object
    obj.name = PREFIX + name
    obj.parent = parent
    obj.location = position
    obj.scale = dimensions
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    _paint(obj, material)
    if bevel:
        modifier = obj.modifiers.new('assembled plate edge', 'BEVEL')
        modifier.width = bevel
        modifier.segments = 1
        obj.modifiers.new('weighted panel normals', 'WEIGHTED_NORMAL')
    return obj


def _cab(parent, name, x, length, width, height, materials):
    # Sloped windshield and chamfered nose: eight-sided profile extruded across Y.
    profile = [(x-length/2, 1.0), (x+length/2-.5, 1.0), (x+length/2, 1.5),
               (x+length/2-.4, height-.65), (x+length/2-1.2, height), (x-length/2, height)]
    vertices = [(px, side*width/2, z) for side in (-1, 1) for px, z in profile]
    count = len(profile)
    faces = [tuple(range(count-1, -1, -1)), tuple(range(count, count*2))]
    faces += [(i, (i+1)%count, (i+1)%count+count, i+count) for i in range(count)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], faces)
    obj = bpy.data.objects.new(PREFIX + name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    obj.parent = parent
    _paint(obj, materials['cream'])
    for side in (-1, 1):
        _box(parent, name+' side glazing', (x+.2, side*(width/2+.025), height-.75), (length*.46, .045, .8), materials['glass'], .02)
        _box(parent, name+' door lower', (x-.1, side*(width/2+.055), 1.55), (length*.50, .07, .63), materials['oxide'], .03)
        _box(parent, name+' access step', (x-.1, side*(width/2+.22), .82), (1.7, .50, .18), materials['steel'])
        _box(parent, name+' mirror arm', (x+.9, side*(width/2+.27), height-.4), (.14, .55, .12), materials['steel'], .02)
    windshield = _box(parent, name+' front windshield', (x+length/2-.73, 0, height-.48), (.08, width*.79, .78), materials['glass'], .025)
    windshield.rotation_euler.y = -.62
    _box(parent, name+' impact bumper', (x+length/2+.13, 0, .95), (.28, width+.4, .42), materials['steel'])
    for side in (-1, 1):
        _box(parent, name+' work lamp', (x+length/2-.05, side*width*.32, 1.35), (.15, .35, .20), materials['lamp'], .02)


def _wheels(parent, name, axles, width, radius, materials, speed):
    wheels = []
    for axle in axles:
        _box(parent, name+' axle', (axle, 0, radius), (.23, width, .23), materials['steel'], .03)
        for side in (-1, 1):
            bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=radius, depth=.48)
            wheel = bpy.context.object
            wheel.name = PREFIX + name+' tire'
            wheel.parent = parent
            wheel.location = (axle, side*width/2, radius)
            wheel.rotation_euler.x = math.pi/2
            _paint(wheel, materials['rubber'])
            for frame in (START, END):
                wheel.rotation_euler.y = (frame-START)/SOURCE_FPS*speed/radius
                wheel.keyframe_insert(data_path='rotation_euler', frame=frame)
            wheels.append(wheel)
            bpy.ops.mesh.primitive_cylinder_add(vertices=12, radius=radius*.48, depth=.51)
            hub = bpy.context.object
            hub.name = PREFIX+name+' recessed hub'
            hub.parent = parent
            hub.location = wheel.location
            hub.rotation_euler.x = math.pi/2
            _paint(hub, materials['steel'])
    return wheels


def _linear(obj):
    if obj.animation_data and obj.animation_data.action:
        action = obj.animation_data.action
        curves = [curve for layer in action.layers for strip in layer.strips for bag in strip.channelbags for curve in bag.fcurves]
        for curve in curves:
            for point in curve.keyframe_points:
                point.interpolation = 'LINEAR'


def build_traffic(scene, world, layout):
    """Return manifest and all-96-sample clearance evidence; never edit source objects."""
    materials = dict(cream=_material('worn cream plates', (.56,.56,.48), (1,1)),
                     oxide=_material('oxide orange machinery', (.36,.15,.06), (1,0)),
                     steel=_material('gunmetal chassis', (.20,.25,.25), (0,1), .35),
                     rubber=_material('tire rubber', (.025,.032,.031)),
                     glass=_material('smoked cab glazing', (.035,.085,.10), metallic=.30),
                     lamp=_material('work light glass', (.82,.78,.50)))
    z = layout.get('road_z', .4)
    specs = [('articulated freight tug', -650, 753, 8, 0, 20.0, 3.8),
             ('maintenance carrier', -420, 767, 7, math.pi, 9.0, 3.0),
             ('port utility van', -870, 349, 6, 0, 6.8, 2.8)]
    records=[]
    for kind, x, y, speed, yaw, length, width in specs:
        root=bpy.data.objects.new(PREFIX+kind, None)
        scene.collection.objects.link(root)
        root.parent=world
        root.rotation_euler.z=yaw
        direction=1 if yaw==0 else -1
        for frame in (START, END):
            root.location=(x+direction*speed*(frame-START)/SOURCE_FPS, y, z)
            root.keyframe_insert(data_path='location', frame=frame)
        _linear(root)
        if kind=='articulated freight tug':
            _box(root, kind+' tractor chassis', (5.4,0,.92), (7.1,3.4,.68), materials['steel'])
            _cab(root, kind+' cab', 7.4,3.3,3.25,3.65,materials)
            _box(root, kind+' engine cowling', (4.2,0,1.8), (2.8,2.8,1.15),materials['oxide'])
            for dy in (-1,1):
                for dx in (3.4,3.8,4.2,4.6):
                    _box(root,kind+' cooling slat',(dx,dy*1.43,1.95),(.12,.07,.62),materials['steel'],.01)
            _box(root,kind+' articulated drawbar',(1.0,0,.78),(2.5,.48,.40),materials['steel'])
            _box(root,kind+' lowbed trailer',(-4.4,0,1.18),(10.8,3.7,.55),materials['steel'])
            for cx in (-7.2,-3.2):
                _box(root,kind+' cargo machinery skid',(cx,0,1.65),(3.3,3.0,.35),materials['oxide'])
                _box(root,kind+' strapped industrial cargo',(cx,0,2.85),(2.9,2.65,2.1),materials['cream'])
                for offset in (-.95,.95):
                    _box(root,kind+' restraint band',(cx+offset,0,2.88),(.14,2.75,2.22),materials['steel'],.02)
            wheels=_wheels(root,kind,(-8.3,-6.7,3.4,7.8),3.6,.78,materials,speed)
        else:
            long=kind=='maintenance carrier'
            _box(root,kind+' frame',(0,0,.90),(length-.5,width,.58),materials['steel'])
            _cab(root,kind+' cab',length/2-1.6,2.9,width-.2,3.1 if long else 2.85,materials)
            _box(root,kind+' service body',(-1.35,0,1.95),(4.2 if long else 2.8,width-.15,1.55),materials['oxide'] if long else materials['cream'])
            for side in (-1,1):
                for cx in (-2.3,-1.3,-.3):
                    _box(root,kind+' compartment door',(cx,side*width/2,1.95),(.85,.07,1.1),materials['cream'],.03)
                    _box(root,kind+' recessed latch',(cx+.25,side*(width/2+.05),1.95),(.12,.05,.14),materials['steel'],.01)
            if long:
                _box(root,kind+' roof equipment rack',(-1.4,0,2.91),(3.9,2.6,.22),materials['steel'])
                _box(root,kind+' secured pump housing',(-1.8,0,3.35),(2.1,1.8,.70),materials['cream'])
            wheels=_wheels(root,kind,(-length*.31,length*.31),width,.65,materials,speed)
        for wheel in wheels:_linear(wheel)
        records.append(dict(object=root.name,kind=kind,start=[x,y,z],speed_mps=speed,direction=direction,length_m=length,width_m=width,road_y_bounds=([745,775] if y>700 else [345,365]),road_x_bounds=([-2200,500] if y>700 else [-1740,-210]),wheel_contact_z=z))
    report=validate_traffic(scene,world,records,layout)
    folder=Path(__file__).resolve().parent/'build/traffic'
    folder.mkdir(parents=True,exist_ok=True)
    (folder/'traffic-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    return report


def validate_traffic(scene, world, records, layout):
    """Conservative source-space AABB checks at every actual rendered source time."""
    prior=scene.frame_current+scene.frame_subframe
    failures=[]
    samples=[]
    obstacle_names=layout.get('obstacle_objects',[])
    for sample in range(96):
        frame=START+sample*.5
        scene.frame_set(int(frame),subframe=frame%1)
        inverse=world.matrix_world.inverted()
        envelopes=[]
        for row in records:
            root=bpy.data.objects[row['object']]
            points=[inverse @ child.matrix_world @ vertex.co for child in root.children_recursive if child.type=='MESH' for vertex in child.data.vertices]
            lo=[min(p[i] for p in points) for i in range(3)]
            hi=[max(p[i] for p in points) for i in range(3)]
            if lo[0]<row['road_x_bounds'][0] or hi[0]>row['road_x_bounds'][1] or lo[1]<row['road_y_bounds'][0] or hi[1]>row['road_y_bounds'][1]:
                failures.append(dict(sample=sample,object=root.name,reason='outside road'))
            if abs(lo[2]-.4)>.015:
                failures.append(dict(sample=sample,object=root.name,reason='wheel contact',bottom=lo[2]))
            for name in obstacle_names:
                obj=bpy.data.objects.get(name)
                if not obj or obj.type!='MESH' or obj.hide_render:continue
                pts=[inverse@obj.matrix_world@Vector(c) for c in obj.bound_box]
                a=[min(p[i] for p in pts) for i in range(3)];b=[max(p[i] for p in pts) for i in range(3)]
                if all(lo[i]<b[i] and hi[i]>a[i] for i in range(3)):
                    failures.append(dict(sample=sample,object=root.name,reason='obstacle envelope',obstacle=name))
            envelopes.append(dict(object=root.name,lo=lo,hi=hi))
        for i,a in enumerate(envelopes):
            for b in envelopes[i+1:]:
                if all(a['lo'][axis]<b['hi'][axis] and a['hi'][axis]>b['lo'][axis] for axis in range(3)):
                    failures.append(dict(sample=sample,reason='traffic envelope overlap',objects=[a['object'],b['object']]))
        samples.append(dict(source_frame=frame,envelopes=envelopes))
    scene.frame_set(int(prior),subframe=prior%1)
    report=dict(status='PASS' if not failures else 'FAIL',source_fps=12,output_fps=24,sample_count=96,vehicles=records,samples=samples,failures=failures,clearance_method='Conservative mesh bounds in source world; reserved road and supplied building envelopes. Visual contact/render review separate.')
    if failures:raise RuntimeError('Traffic clearance failed: '+json.dumps(failures[:5]))
    return report
