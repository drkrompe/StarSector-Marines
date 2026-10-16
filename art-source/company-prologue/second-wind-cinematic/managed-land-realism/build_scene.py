"""Build shot03 realism from supplied registered plates; background Blender only.

The source scene stays unchanged. --allow-missing is for layout inspection only:
missing art stays grey. Full render must use all required registered assets.
"""
import argparse
import json
from pathlib import Path
import sys

import bpy
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
ASSETS = HERE / 'assets'
BUILD = HERE / 'build'
BUILD.mkdir(exist_ok=True)
parser = argparse.ArgumentParser()
parser.add_argument('--allow-missing', action='store_true')
parser.add_argument('--hills', default='hills-realism-v2.png', help='Registered hills asset filename inside assets/')
parser.add_argument('--cabin', default='cabin-realism-v2.png', help='Registered cabin asset filename inside assets/')
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
manifest = json.loads((HERE / 'guide-manifest.json').read_text())
source = HERE.parent / 'layered-next-scenes/layered-next-scenes.blend'
required = ['fields-realism-v1.png', args.hills, 'woodland-realism-v1.png',
            'sky-realism-v1.png', args.cabin, 'environment-realism-v1.png', 'field-detail-realism-v1.png']
missing = [name for name in required if not (ASSETS / name).is_file()]
if missing and not args.allow_missing:
    raise RuntimeError('Missing required registered art: ' + ', '.join(missing))
bpy.ops.wm.open_mainfile(filepath=str(source))
scene = bpy.context.scene
camera = bpy.data.objects['03 LAND / side window']
world = bpy.data.objects['LAYERED | inverse source flight']
root = bpy.data.objects['SECOND WIND | flight root']
for marker in scene.timeline_markers:
    marker.camera = None
scene.frame_set(263, subframe=.75)
bpy.context.view_layer.update()
reference = camera.matrix_world.normalized().copy()
source_reference = world.matrix_world.inverted() @ reference

def projector(name, parent, matrix, lens):
    data = camera.data.copy()
    data.lens = lens
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    obj.parent = parent
    obj.matrix_basis = parent.matrix_world.inverted() @ matrix
    return obj

environment_projector = projector('REALISM | source-world landscape projector', world,
                                  reference, camera.data.lens * 1920 / 2560 / 1.08)
cabin_projector = projector('REALISM | ship-local cabin projector', root,
                            reference, camera.data.lens / 1.12)
source_coords = bpy.data.objects.new('REALISM | source-world coordinates', None)
scene.collection.objects.link(source_coords)
source_coords.parent = world

def image_texture(nodes, path):
    node = nodes.new('ShaderNodeTexImage')
    node.image = bpy.data.images.load(str(ASSETS / path), check_existing=True)
    node.image.alpha_mode = 'STRAIGHT'
    node.extension = 'CLIP'
    node.interpolation = 'Linear'
    if path == args.hills or path == 'woodland-realism-v1.png':
        pixels = np.empty(len(node.image.pixels), dtype=np.float32)
        node.image.pixels.foreach_get(pixels)
        if node.image.channels < 4 or not np.any(pixels[3::4] < .01) or not np.any(pixels[3::4] > .99):
            raise RuntimeError(f'{path} needs actual visible and transparent alpha, not a checkerboard or opaque background')
        aspect = node.image.size[0] / node.image.size[1]
        if abs(aspect / (2560 / 1080) - 1) > .01:
            raise RuntimeError(f'{path} must retain the full v2guidecanvas aspect2560:1080; got{list(node.image.size)}')
    return node

def finish(mat, texture, transparent=False):
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    emission = nodes.new('ShaderNodeEmission')
    if texture.image.name.startswith('hills-realism'):
        # Extraction supplies alpha only. The approved master supplies registered
        # RGB, avoiding repainted canopy and extraction-edge color pollution.
        rgb = image_texture(nodes, 'environment-realism-v1.png')
        for link in list(links):
            if link.to_node == texture and link.to_socket.name == 'Vector':
                links.new(link.from_socket, rgb.inputs['Vector'])
        links.new(rgb.outputs['Color'], emission.inputs['Color'])
    elif texture.image.name.startswith('woodland-realism'):
        # Original master RGB is authoritative above its actual far shoreline.
        # Below that shoreline it contains baked water/fields, so use the opaque
        # extracted forest completion there rather than making floating rivers.
        rgb=image_texture(nodes,'environment-realism-v1.png')
        uv=nodes.new('ShaderNodeTexCoord')
        links.new(uv.outputs['UV'],rgb.inputs['Vector'])
        split=nodes.new('ShaderNodeSeparateXYZ')
        links.new(uv.outputs['UV'],split.inputs[0])
        shoreline=nodes.new('ShaderNodeMath')
        shoreline.operation='MULTIPLY_ADD'
        shoreline.inputs[1].default_value=.15
        shoreline.inputs[2].default_value=.60
        links.new(split.outputs['X'],shoreline.inputs[0])
        distance=nodes.new('ShaderNodeMath')
        distance.operation='SUBTRACT'
        links.new(split.outputs['Y'],distance.inputs[0])
        links.new(shoreline.outputs[0],distance.inputs[1])
        blend=nodes.new('ShaderNodeMath')
        blend.operation='MULTIPLY_ADD'
        blend.use_clamp=True
        blend.inputs[1].default_value=1/.03
        blend.inputs[2].default_value=.5
        links.new(distance.outputs[0],blend.inputs[0])
        mix=nodes.new('ShaderNodeMixRGB')
        links.new(blend.outputs[0],mix.inputs[0])
        links.new(texture.outputs['Color'],mix.inputs[1])
        links.new(rgb.outputs['Color'],mix.inputs[2])
        links.new(mix.outputs[0],emission.inputs['Color'])
    elif texture.image.name.startswith('atlas-painted-v1'):
        haze = nodes.new('ShaderNodeMixRGB')
        haze.blend_type = 'MIX'
        haze.inputs[0].default_value = .32
        haze.inputs[2].default_value = (.38, .52, .69, 1)
        links.new(texture.outputs['Color'], haze.inputs[1])
        links.new(haze.outputs[0], emission.inputs['Color'])
    else:
        links.new(texture.outputs['Color'], emission.inputs['Color'])
    output = nodes.new('ShaderNodeOutputMaterial')
    if transparent:
        mat.surface_render_method = 'BLENDED'
        alpha = nodes.new('ShaderNodeBsdfTransparent')
        mix = nodes.new('ShaderNodeMixShader')
        remap = nodes.new('ShaderNodeMapRange')
        remap.clamp = True
        remap.inputs['From Min'].default_value = .015
        remap.inputs['From Max'].default_value = .98
        links.new(texture.outputs['Alpha'], remap.inputs['Value'])
        if texture.image.name.startswith('hills-realism'):
            coord=nodes.new('ShaderNodeTexCoord')
            split=nodes.new('ShaderNodeSeparateXYZ')
            links.new(coord.outputs['UV'],split.inputs[0])
            lower=nodes.new('ShaderNodeMath')
            lower.operation='LESS_THAN'
            lower.inputs[1].default_value=.78
            links.new(split.outputs['Y'],lower.inputs[0])
            maximum=nodes.new('ShaderNodeMath')
            maximum.operation='MAXIMUM'
            links.new(lower.outputs[0],maximum.inputs[0])
            links.new(remap.outputs['Result'],maximum.inputs[1])
            links.new(maximum.outputs[0],mix.inputs[0])
        else:
            links.new(remap.outputs['Result'], mix.inputs[0])
        links.new(alpha.outputs[0], mix.inputs[1])
        links.new(emission.outputs[0], mix.inputs[2])
        links.new(mix.outputs[0], output.inputs[0])
    else:
        links.new(emission.outputs[0], output.inputs[0])

def material(name, filename, projection=None, aspect=16/9, topdown=False, transparent=False):
    if not (ASSETS / filename).exists():
        return None
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    texture = image_texture(nodes, filename)
    if topdown:
        texture.extension='EXTEND'
    coord = nodes.new('ShaderNodeTexCoord')
    if projection or topdown:
        coord.object = source_coords if topdown else projection
        separate = nodes.new('ShaderNodeSeparateXYZ')
        links.new(coord.outputs['Object'], separate.inputs[0])
        vector = nodes.new('ShaderNodeCombineXYZ')
        if topdown:
            xmin, xmax, ymin, ymax = manifest['topdown']['source_world_bounds_xy_m']
            for axis, start, extent in (('X', xmin, xmax-xmin), ('Y', ymin, ymax-ymin)):
                remap = nodes.new('ShaderNodeMath')
                remap.operation = 'MULTIPLY_ADD'
                remap.inputs[1].default_value = 1 / extent
                remap.inputs[2].default_value = -start / extent
                links.new(separate.outputs[axis], remap.inputs[0])
                links.new(remap.outputs[0], vector.inputs[axis])
        else:
            negative_z = nodes.new('ShaderNodeMath')
            negative_z.operation = 'MULTIPLY'
            negative_z.inputs[1].default_value = -1
            links.new(separate.outputs['Z'], negative_z.inputs[0])
            for axis, scale in (('X', projection.data.lens/projection.data.sensor_width),
                                ('Y', projection.data.lens/projection.data.sensor_width*aspect)):
                divide = nodes.new('ShaderNodeMath')
                divide.operation = 'DIVIDE'
                links.new(separate.outputs[axis], divide.inputs[0])
                links.new(negative_z.outputs[0], divide.inputs[1])
                remap = nodes.new('ShaderNodeMath')
                remap.operation = 'MULTIPLY_ADD'
                remap.inputs[1].default_value = scale
                remap.inputs[2].default_value = .5
                links.new(divide.outputs[0], remap.inputs[0])
                links.new(remap.outputs[0], vector.inputs[axis])
        links.new(vector.outputs[0], texture.inputs['Vector'])
    else:
        if filename == 'sky-realism-v1.png':
            # This sky is an independent style plate, not a registered layer.
            # Cover the wider card by cropping its height, never stretching clouds.
            source_aspect = texture.image.size[0] / texture.image.size[1]
            scale = min(1.0, source_aspect / (2560 / 1080))
            mapping = nodes.new('ShaderNodeVectorMath')
            mapping.operation = 'MULTIPLY_ADD'
            mapping.inputs[1].default_value = (1, scale, 1)
            mapping.inputs[2].default_value = (0, (1-scale)/2, 0)
            links.new(coord.outputs['UV'], mapping.inputs[0])
            links.new(mapping.outputs[0], texture.inputs['Vector'])
        else:
            links.new(coord.outputs['UV'], texture.inputs['Vector'])
    finish(mat, texture, transparent)
    return mat

def paint(obj, mat):
    if mat is None:
        return
    obj.data = obj.data.copy()
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    for polygon in obj.data.polygons:
        polygon.material_index = 0

fields = material('REALISM | source-world orthographic fields', 'fields-realism-v1.png', topdown=True)
cabin = material('REALISM | ship-local registered cabin', args.cabin, cabin_projector)
environment = material('REALISM | registered far ground', 'environment-realism-v1.png', environment_projector, 2560/1080)
transparent_ground = bpy.data.materials.new('REALISM | far terrain hidden behind stable bank card')
transparent_ground.use_nodes=True
nodes=transparent_ground.node_tree.nodes
nodes.clear()
trans=nodes.new('ShaderNodeBsdfTransparent')
out=nodes.new('ShaderNodeOutputMaterial')
transparent_ground.node_tree.links.new(trans.outputs[0],out.inputs[0])
transparent_ground.surface_render_method='BLENDED'

def water_material():
    mat=bpy.data.materials.new('REALISM | source-world river ripples without projected foliage')
    mat.use_nodes=True
    nodes,links=mat.node_tree.nodes,mat.node_tree.links
    nodes.clear()
    coords=nodes.new('ShaderNodeTexCoord')
    coords.object=source_coords
    wave=nodes.new('ShaderNodeTexWave')
    wave.wave_type='BANDS'
    wave.bands_direction='DIAGONAL'
    wave.inputs['Scale'].default_value=.04
    wave.inputs['Distortion'].default_value=4
    wave.inputs['Detail Scale'].default_value=.18
    links.new(coords.outputs['Object'],wave.inputs['Vector'])
    noise=nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value=.025
    noise.inputs['Detail'].default_value=3
    links.new(coords.outputs['Object'],noise.inputs['Vector'])
    multiply=nodes.new('ShaderNodeMath')
    multiply.operation='MULTIPLY'
    links.new(wave.outputs['Fac'],multiply.inputs[0])
    links.new(noise.outputs['Fac'],multiply.inputs[1])
    ramp=nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].color=(.13,.23,.27,1)
    ramp.color_ramp.elements[1].position=.65
    ramp.color_ramp.elements[1].color=(.19,.30,.34,1)
    links.new(multiply.outputs[0],ramp.inputs[0])
    # Sample an inspected clean water-only patch from the retained top-down
    # master. Shader UVs tile this source-world surface; no bitmap is rewritten.
    water=image_texture(nodes,'fields-realism-v1.png')
    split=nodes.new('ShaderNodeSeparateXYZ')
    links.new(coords.outputs['Object'],split.inputs[0])
    vector=nodes.new('ShaderNodeCombineXYZ')
    for axis,period,scale,offset in (('X',800,.1455,.2328),('Y',600,.0492,.8525)):
        divide=nodes.new('ShaderNodeMath')
        divide.operation='DIVIDE'
        divide.inputs[1].default_value=period
        links.new(split.outputs[axis],divide.inputs[0])
        repeat=nodes.new('ShaderNodeMath')
        repeat.operation='PINGPONG'
        repeat.inputs[1].default_value=1
        links.new(divide.outputs[0],repeat.inputs[0])
        remap=nodes.new('ShaderNodeMath')
        remap.operation='MULTIPLY_ADD'
        remap.inputs[1].default_value=scale
        remap.inputs[2].default_value=offset
        links.new(repeat.outputs[0],remap.inputs[0])
        links.new(remap.outputs[0],vector.inputs[axis])
    links.new(vector.outputs[0],water.inputs['Vector'])
    color=nodes.new('ShaderNodeMixRGB')
    color.inputs[0].default_value=.12
    links.new(water.outputs['Color'],color.inputs[1])
    links.new(ramp.outputs['Color'],color.inputs[2])
    emission=nodes.new('ShaderNodeEmission')
    links.new(color.outputs[0],emission.inputs[0])
    output=nodes.new('ShaderNodeOutputMaterial')
    links.new(emission.outputs[0],output.inputs[0])
    return mat

river_mat=water_material()

def near_bank_ground(base):
    mat=base.copy()
    mat.name='REALISM | field ground clipped at actual source river nearbank'
    nodes,links=mat.node_tree.nodes,mat.node_tree.links
    coords=nodes.new('ShaderNodeTexCoord')
    coords.object=source_coords
    split=nodes.new('ShaderNodeSeparateXYZ')
    links.new(coords.outputs['Object'],split.inputs[0])
    def math_node(operation,a=None,b=None,value=None):
        node=nodes.new('ShaderNodeMath')
        node.operation=operation
        if a is not None: links.new(a,node.inputs[0])
        if b is not None: links.new(b,node.inputs[1])
        if value is not None: node.inputs[1].default_value=value
        return node.outputs[0]
    def sine(divisor,amplitude):
        scaled=math_node('DIVIDE',split.outputs['X'],value=divisor)
        wave=math_node('SINE',scaled)
        return math_node('MULTIPLY',wave,value=amplitude)
    term1=sine(3200,560)
    term2=sine(1250,230)
    width_wave=sine(2100,1)
    squared=math_node('MULTIPLY',width_wave,width_wave)
    width=math_node('MULTIPLY',squared,value=65)
    curve=math_node('ADD',term1,term2)
    curve=math_node('ADD',curve,value=2635) #2850-195-20m shoreline overlap.
    boundary=math_node('SUBTRACT',curve,width)
    behind=math_node('GREATER_THAN',split.outputs['Y'],boundary)
    output=next(node for node in nodes if node.type=='OUTPUT_MATERIAL')
    surface=output.inputs['Surface'].links[0].from_socket
    transparent=nodes.new('ShaderNodeBsdfTransparent')
    mix=nodes.new('ShaderNodeMixShader')
    links.new(behind,mix.inputs[0])
    links.new(surface,mix.inputs[1])
    links.new(transparent.outputs[0],mix.inputs[2])
    links.new(mix.outputs[0],output.inputs['Surface'])
    mat.surface_render_method='BLENDED'
    return mat

fields_ground=near_bank_ground(fields)

def terrain_field_detail(mat, plots):
    """Paint parcel detail onto the landform itself: no competing top faces.

    Plot proxies supply source-world footprints only. Their corner-conformed
    quads crossed the terrain between corners, so no height offset fixes them.
    """
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    emission = next(n for n in nodes if n.type == 'EMISSION')
    color = emission.inputs['Color'].links[0].from_socket
    coords = nodes.new('ShaderNodeTexCoord')
    coords.object = source_coords
    split = nodes.new('ShaderNodeSeparateXYZ')
    links.new(coords.outputs['Object'], split.inputs[0])

    def math(operation, a, b):
        node = nodes.new('ShaderNodeMath')
        node.operation = operation
        for socket, value in zip(node.inputs, (a, b)):
            if isinstance(value, (float, int)):
                socket.default_value = value
            else:
                links.new(value, socket)
        return node.outputs[0]

    footprints = []
    for obj in plots:
        transform = world.matrix_world.inverted() @ obj.matrix_world
        vertices = [transform @ v.co for v in obj.data.vertices]
        xmin, xmax = min(v.x for v in vertices), max(v.x for v in vertices)
        ymin, ymax = min(v.y for v in vertices), max(v.y for v in vertices)
        numbers = [int(n) for n in obj.name.split()[-2:]]
        flip = sum(numbers) % 2 == 1
        texture = image_texture(nodes, 'field-detail-realism-v1.png')
        vector = nodes.new('ShaderNodeCombineXYZ')
        mask = None
        for axis, lo, hi, scale, offset in (
                ('X', xmin, xmax, -.53125 if flip else .53125, .765625 if flip else .234375),
                ('Y', ymin, ymax, .958333333334, .020833333333)):
            local = math('MULTIPLY', math('SUBTRACT', split.outputs[axis], lo), 1 / (hi - lo))
            mapped = math('ADD', math('MULTIPLY', local, scale), offset)
            links.new(mapped, vector.inputs[axis])
            inside = math('MULTIPLY', math('GREATER_THAN', local, 0), math('LESS_THAN', local, 1))
            mask = inside if mask is None else math('MULTIPLY', mask, inside)
        links.new(vector.outputs[0], texture.inputs['Vector'])
        brightness = nodes.new('ShaderNodeMixRGB')
        brightness.blend_type = 'MULTIPLY'
        brightness.inputs[0].default_value = 1
        gain = .96 + ((numbers[0] * 3 + numbers[1]) % 5) * .02
        brightness.inputs[2].default_value = (gain, gain, gain, 1)
        links.new(texture.outputs['Color'], brightness.inputs[1])
        mix = nodes.new('ShaderNodeMixRGB')
        links.new(mask, mix.inputs[0])
        links.new(color, mix.inputs[1])
        links.new(brightness.outputs[0], mix.inputs[2])
        color = mix.outputs[0]
        footprints.append({'proxy': obj.name, 'source_xy_bounds_m': [xmin, xmax, ymin, ymax]})
    links.new(color, emission.inputs['Color'])
    return footprints

field_footprints = terrain_field_detail(fields_ground, [obj for obj in scene.objects
    if obj.type == 'MESH' and obj.name.startswith('Plantation | clearing ')])
land_obj = bpy.data.objects['Jangala | continuous landform']
land_obj['ground_surface_contract'] = 'Single terrain surface; plantation proxies are footprint guides only'
land_obj['field_footprints_json'] = json.dumps(field_footprints)

ship = set(root.children_recursive)
for obj in list(scene.objects):
    if obj.type != 'MESH' or obj.hide_render:
        continue
    if obj in ship:
        paint(obj, cabin)
    elif obj.pass_index == 34:
        # These rough silhouette ribbons are replaceable layout proxies. Their
        # coarse raised banks fight the realistic orthographic field texture.
        obj.hide_render = True
    elif obj.pass_index == 13:
        # Retain source proxy geometry for authoring, but render only the actual
        # terrain. Parcel detail is shaded on that surface in source-world XY.
        obj.hide_render = True
    elif obj.pass_index in (31, 32, 33, 35, 36, 37, 38, 39):
        obj.hide_render = True
    elif obj.name == 'Jangala | continuous landform':
        # Retain source topology. Face-level split keeps the nearby orthographic
        # field projection separate from broad distant environment projection.
        obj.data = obj.data.copy()
        obj.data.materials.clear()
        available = [fields_ground]
        for mat in available:
            obj.data.materials.append(mat)
        if available:
            inverse = world.matrix_world.inverted() @ obj.matrix_world
            for polygon in obj.data.polygons:
                y = (inverse @ polygon.center).y
                polygon.material_index = 0
    elif obj.name in ('Jangala | winding river', 'Jangala | far estuary'):
        paint(obj, river_mat)

# Agriculture geometry is authored independently of the terrain surface shader.
# It samples the same actual landform triangles for support/contact heights.
land_source = world.matrix_world.inverted() @ land_obj.matrix_world
ground_tree = BVHTree.FromPolygons([land_source @ v.co for v in land_obj.data.vertices],
                                 [tuple(p.vertices) for p in land_obj.data.polygons])
sys.path.insert(0, str(HERE))
from agriculture_props import build_agriculture_props
agriculture = build_agriculture_props(scene, world, ground_tree)
from compound_pad import stabilize_compound_pad
agriculture['ground_surface_partition'] = stabilize_compound_pad(scene, world)

layers = []
def card(name, filename, source_y, transparent):
    if not (ASSETS / filename).is_file():
        return
    forward = source_reference.to_3x3() @ Vector((0,0,-1))
    depth = (source_y - source_reference.translation.y) / forward.y
    # Match the wide guide exactly, including 8% vertical guardband.
    halfw = depth * environment_projector.data.sensor_width / environment_projector.data.lens / 2
    halfh = halfw * 1080 / 2560
    vertices = [source_reference @ Vector((x,y,-depth)) for x,y in
                ((-halfw,-halfh),(halfw,-halfh),(halfw,halfh),(-halfw,halfh))]
    centre = sum(vertices, Vector()) / 4
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([p-centre for p in vertices], [], [(0,1,2,3)])
    uv = mesh.uv_layers.new(name='Registered full canvas')
    for loop, value in zip(uv.data, ((0,0),(1,0),(1,1),(0,1))):
        loop.uv = value
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    obj.parent = world
    obj.location = centre
    mesh.materials.append(material(name, filename, transparent=transparent))
    layers.append({'object': name, 'asset': filename, 'source_y_m': source_y, 'camera_depth_m': depth})

card('REALISM | sky', 'sky-realism-v1.png', 35000, False)
card('REALISM | distant hills', args.hills, 11000, True)
card('REALISM | middle woodland', 'woodland-realism-v1.png', 3000, True)

# A fixed-aspect distant freighter is suitable for this short, fixed-side view.
# Its size/depth/speed are compositing choices, not canonical Atlas dimensions.
traffic_file = '../../parallax-window/traffic/atlas-painted-v1.png'
traffic_path = (ASSETS / traffic_file).resolve()
if not traffic_path.is_file():
    raise RuntimeError(f'Missing retained traffic artwork: {traffic_path}')
traffic_mat = material('REALISM | distant Atlas alpha with sky haze', traffic_file, transparent=True)
forward = source_reference.to_3x3() @ Vector((0,0,-1))
right = source_reference.to_3x3() @ Vector((1,0,0))
up = source_reference.to_3x3() @ Vector((0,1,0))
traffic_depth = (8500 - source_reference.translation.y) / forward.y
view_width = traffic_depth * camera.data.sensor_width / camera.data.lens
traffic_width = view_width * 135 / 1920
traffic_height = traffic_width / 2  # Retained1774x887canvas; no crop or rescale.
traffic_centre = source_reference.translation + forward*traffic_depth + right*(.17*view_width) + up*(.38*view_width*9/16)
mesh = bpy.data.meshes.new('REALISM | distant Atlas card')
mesh.from_pydata([right*x+up*y for x,y in ((-traffic_width/2,-traffic_height/2),
    (traffic_width/2,-traffic_height/2),(traffic_width/2,traffic_height/2),(-traffic_width/2,traffic_height/2))],[],[(0,1,2,3)])
uv = mesh.uv_layers.new(name='Retained Atlas alpha canvas')
for loop,value in zip(uv.data,((0,0),(1,0),(1,1),(0,1))):
    loop.uv=value
traffic = bpy.data.objects.new(mesh.name,mesh)
scene.collection.objects.link(traffic)
traffic.parent = world
mesh.materials.append(traffic_mat)
# Observer-relative motion matters: the former +X19 m/s drifted 49.9 px LEFT.
# +X300 m/s gives a restrained ~50 px RIGHT over this four-second camera pass.
# This source-world velocity is a shot composition choice, not Atlas performance.
traffic_speed = 300
for t in (240,287.5):
    traffic.location = traffic_centre + Vector(((t-263.75)/12*traffic_speed,0,0))
    traffic.keyframe_insert(data_path='location',frame=t)
for layer in traffic.animation_data.action.layers:
    for strip in layer.strips:
        for bag in strip.channelbags:
            for curve in bag.fcurves:
                for key in curve.keyframe_points:
                    key.interpolation='LINEAR'
layers.append({'object':traffic.name,'asset':'../parallax-window/traffic/atlas-painted-v1.png',
    'source_y_m':8500,'canvas_width_px_midpoint':135,'independent_source_x_speed_m_s':traffic_speed,
    'intended_screen_travel':'approximately50px right over source240..287.5',
    'source_velocity_is_provisional_compositing_choice':True,
    'haze_mix':.32,'identity':'Fixed-aspect distant Atlas artwork; bow right/engines left',
    'dimensions_are_compositing_choices':True})
scene.camera = camera
scene.render.engine = 'BLENDER_EEVEE'
scene.eevee.taa_render_samples = 96
scene.render.resolution_x, scene.render.resolution_y = 1920,1080
scene.render.resolution_percentage = 100
scene.render.fps = 12
scene.render.fps_base = 1
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.render.use_compositing = False
for obj in scene.objects:
    if obj.type=='LIGHT':
        obj.data.use_shadow=False
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'None'
scene.view_settings.exposure = 0
scene.view_settings.gamma = 1
scene.frame_start, scene.frame_end = 240,288
bpy.context.preferences.filepaths.save_version = 0
for image in bpy.data.images:
    if image.source == 'FILE' and image.filepath:
        path = Path(bpy.path.abspath(image.filepath)).resolve()
        if path.is_relative_to(ASSETS.resolve()):
            image.filepath = '//assets/' + path.name
        elif path == traffic_path:
            image.filepath = '//../parallax-window/traffic/atlas-painted-v1.png'
bpy.ops.wm.save_as_mainfile(filepath=str(HERE / 'managed-land-realism.blend'), compress=True, relative_remap=False)
(HERE / 'scene-manifest.json').write_text(json.dumps({'source':'../layered-next-scenes/layered-next-scenes.blend', 'guide_manifest':'guide-manifest.json',
    'source_frames':[240,287.5], 'source_fps':12, 'output_frames':96, 'output_fps':24,
    'camera':camera.name, 'missing_assets':missing, 'assets':required, 'layers':layers,
    'agriculture':agriculture,
    'environment_relief':{'crop_rows':'Hidden box proxies; crop detail supplied by orthographic surface',
                          'road_clearing_offset_m':0,'hidden_replaceable_proxy_ids':[13,34],
                          'field_detail':'Single landform surface with source-world XY parcel masks; centralplotUV U.234375..765625,V.0208333..9791667; alternateXflip and gain.96..1.04',
                          'far_ground':'Per-pixel source-world analytic river nearbank mask hides all coarse ground beyond nearbank; no canopy projection onto coarse terrain',
                          'river':'Retained river geometry; clean water-only UV subregion of fields master tiled in source-worldXY, with12% low-contrast fine-ripple modulation. No projected tree RGB',
                          'woodland_rgb':'Originalmaster above conservative farshore UV boundary V=.60+.15U; smooth.03Vblend to generatedopaquecompletion below'},
    'independent_cloud_pass':{'included':False,'reason':'Supplied cloud-bank cutout is visually heavy and has colored edge residue; retained sky already includes natural clouds. Raw supplied cloud image preserved.'},
    'scope':'Short textured motion study, no sound or game integration'}, indent=2), encoding='utf-8')
print('MANAGED_LAND_SCENE_READY', HERE, 'MISSING', missing, flush=True)
