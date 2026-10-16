"""Contained entry tail, derived from accepted forest scene. Background Blender only."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / 'forest-reveal-realism/forest-reveal-realism.blend'
parser = argparse.ArgumentParser()
parser.add_argument('--preview', action='store_true')
parser.add_argument('--target-preview', action='store_true')
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
scene = bpy.context.scene
camera = bpy.data.objects['02 REVEAL / near glass']
scene.camera = camera
for marker in scene.timeline_markers:
    marker.camera = None
scene.frame_set(120)
bpy.context.view_layer.update()
reference = camera.matrix_world.copy()
root = bpy.data.objects['SECOND WIND | flight root']
ship_meshes = [o for o in root.children_recursive if o.type == 'MESH']
ship_geometry = {o.name: hashlib.sha256(str(([list(v.co) for v in o.data.vertices], [list(p.vertices) for p in o.data.polygons])).encode()).hexdigest() for o in ship_meshes}

def animate(socket, points):
    for frame, value in points:
        socket.default_value = value
        socket.keyframe_insert('default_value', frame=frame)

def card(name, depth, warm=False, asset=False):
    # World-space geometry outside the occupied vessel. Its origin is at its real depth.
    halfw = depth * camera.data.sensor_width / camera.data.lens * .64
    halfh = halfw * 9 / 16
    center = reference @ Vector((0, 0, -depth))
    points = [reference @ Vector((x, y, -depth)) - center for x, y in
              ((-halfw,-halfh),(halfw,-halfh),(halfw,halfh),(-halfw,halfh))]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(points, [], [(0,1,2,3)])
    uv = mesh.uv_layers.new(name='Cloud overscan')
    for loop, coord in zip(uv.data, ((0,0),(1,0),(1,1),(0,1))):
        loop.uv = coord
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    obj.location = center
    obj['depth_m'] = depth
    obj['effect'] = 'Exterior authored depth card; no fluid simulation'
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.surface_render_method = 'BLENDED'
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    texcoord = nodes.new('ShaderNodeTexCoord')
    mapping = nodes.new('ShaderNodeVectorMath')
    mapping.operation = 'ADD'
    links.new(texcoord.outputs['UV'], mapping.inputs[0])
    animate(mapping.inputs[1], [(73.0,(0,0,0)),(120,(.17 if warm else -.12,.07,0))])
    noise = nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = 3.6 if warm else 5.0
    noise.inputs['Detail'].default_value = 5.0
    noise.inputs['Roughness'].default_value = .68
    links.new(mapping.outputs[0], noise.inputs['Vector'])
    ramp = nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = .18
    ramp.color_ramp.elements[1].position = .8
    if warm:
        ramp.color_ramp.elements[0].color = (.36,.07,.012,1)
        ramp.color_ramp.elements[1].color = (1.0,.48,.12,1)
    else:
        ramp.color_ramp.elements[0].color = (.44,.53,.60,1)
        ramp.color_ramp.elements[1].color = (.91,.96,1.0,1)
    links.new(noise.outputs['Fac'], ramp.inputs[0])
    emission = nodes.new('ShaderNodeEmission')
    links.new(ramp.outputs[0], emission.inputs['Color'])
    emission.inputs['Strength'].default_value = 1.25 if warm else 1
    opacity = nodes.new('ShaderNodeValue')
    opacity.label = 'Authored clearing envelope, source clock'
    animate(opacity.outputs[0], [(73.0,.48 if warm else .98),(82,.25 if warm else .98),
                                (96,0 if warm else .91),(105,0 if warm else .68),
                                (116,0 if warm else .13),(120,0)])
    factor = opacity.outputs[0]
    if asset:
        texture = nodes.new('ShaderNodeTexImage')
        texture.image = bpy.data.images.load(str(HERE.parent/'managed-land-realism/assets/clouds-realism-v1.png'), check_existing=True)
        texture.extension = 'CLIP'
        texture.image.alpha_mode = 'STRAIGHT'
        links.new(mapping.outputs[0], texture.inputs['Vector'])
        links.new(texture.outputs['Color'], emission.inputs['Color'])
        multiply = nodes.new('ShaderNodeMath')
        multiply.operation = 'MULTIPLY'
        links.new(texture.outputs['Alpha'], multiply.inputs[0])
        links.new(factor,multiply.inputs[1])
        factor = multiply.outputs[0]
    transparent = nodes.new('ShaderNodeBsdfTransparent')
    mix = nodes.new('ShaderNodeMixShader')
    links.new(factor,mix.inputs[0])
    links.new(transparent.outputs[0],mix.inputs[1])
    links.new(emission.outputs[0],mix.inputs[2])
    output = nodes.new('ShaderNodeOutputMaterial')
    links.new(mix.outputs[0],output.inputs['Surface'])
    mesh.materials.append(mat)
    return obj

layers = [card('ENTRY | distant luminous cloud body',2300),
          card('ENTRY | retained cloud filaments',1600,asset=True),
          card('ENTRY | exterior warm boundary glow',900,warm=True)]
# Sparse broad-flow streaks: stretched smooth noise, thresholded softly, no hard cone.
filaments=card('ENTRY | exterior heating filaments',800,warm=True)
layers.append(filaments)
nodes,links=filaments.active_material.node_tree.nodes,filaments.active_material.node_tree.links
mapping=next(n for n in nodes if n.type=='VECT_MATH')
noise=next(n for n in nodes if n.type=='TEX_NOISE')
stretch=nodes.new('ShaderNodeVectorMath')
stretch.operation='MULTIPLY'
stretch.inputs[1].default_value=(1.3,6,1)
links.new(mapping.outputs[0],stretch.inputs[0])
links.new(stretch.outputs[0],noise.inputs['Vector'])
noise.inputs['Scale'].default_value=4
noise.inputs['Detail'].default_value=2
noise.inputs['Roughness'].default_value=.55
mask=nodes.new('ShaderNodeMapRange')
mask.clamp=True
mask.inputs['From Min'].default_value=.58
mask.inputs['From Max'].default_value=.86
links.new(noise.outputs['Fac'],mask.inputs['Value'])
envelope=nodes.new('ShaderNodeValue')
animate(envelope.outputs[0],[(73, .55),(80,.35),(89,0),(120,0)])
animate(mapping.inputs[1],[(73,(0,0,0)),(89,(1.2,.015,0)),(120,(3.2,.04,0))])
multiply=nodes.new('ShaderNodeMath')
multiply.operation='MULTIPLY'
links.new(mask.outputs[0],multiply.inputs[0])
links.new(envelope.outputs[0],multiply.inputs[1])
mix=next(n for n in nodes if n.type=='MIX_SHADER')
links.new(multiply.outputs[0],mix.inputs[0])
ramp=next(n for n in nodes if n.type=='VALTORGB')
ramp.color_ramp.elements[0].color=(1,.22,.035,1)
ramp.color_ramp.elements[1].color=(1,.87,.62,1)
emission=next(n for n in nodes if n.type=='EMISSION')
emission.inputs['Strength'].default_value=1.8
# The original source camera, cabin, projected materials and scenery keep their own clock.
scene.render.resolution_x = 1920
scene.render.resolution_y = 1080
scene.render.resolution_percentage = 100
scene.eevee.taa_render_samples = 64
scene.frame_start = 72
scene.frame_end = 120
scene.frame_set(120)
bpy.context.view_layer.update()
assert camera.matrix_world == reference
for image in bpy.data.images:
    if image.source == 'FILE' and image.filepath:
        image.filepath = str(Path(bpy.path.abspath(image.filepath)).resolve())
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'entry-window.blend'),relative_remap=True,compress=True)
samples=[]
for i in range(96):
    time=73.0+47.0*i/95
    scene.frame_set(int(time),subframe=time%1)
    bpy.context.view_layer.update()
    samples.append({'index':i,'source_frame':time,'camera':[list(r) for r in camera.matrix_world]})
manifest = {'source':'../forest-reveal-realism/forest-reveal-realism.blend',
    'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
    'timing':'96 frames at 24fps (4 seconds). Independent source clock: 73 + (47/95)*i, i=0..95. Source remains 12fps.',
    'cut':'Last entry sample equals forest source120 pose/light. Forest starts source120; duplicate endpoint is one frame only, optionally skip its first output frame in assembly.',
    'layers':[{'name':o.name,'depth_m':o['depth_m']} for o in layers],
    'ship_geometry_sha256':ship_geometry,'samples':samples,
    'limits':'Authored exterior layered noise/glow and retained cloud art, not physical plasma/fluid simulation. No new cabin lighting or ship geometry. Review/media reports own final render status.'}
cloud_asset=HERE.parent/'managed-land-realism/assets/clouds-realism-v1.png'
manifest['reused_cloud_asset']={'path':'../managed-land-realism/assets/clouds-realism-v1.png',
    'sha256':hashlib.sha256(cloud_asset.read_bytes()).hexdigest(),
    'dimensions':list(bpy.data.images.load(str(cloud_asset),check_existing=True).size),
    'role':'Existing approved cloud artwork, independent exterior depth card; raw pixels unchanged'}
(HERE/'scene-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n')
if args.preview or args.target_preview:
    out=HERE/'build/previews'
    out.mkdir(parents=True,exist_ok=True)
    for i in ((0,) if args.target_preview else (0,48,95)):
        time=73.0+47.0*i/95
        scene.frame_set(int(time),subframe=time%1)
        scene.render.filepath=str(out/f'frame_{i:04d}.png')
        bpy.ops.render.render(write_still=True)
        print('ENTRY_PREVIEW',i,flush=True)
