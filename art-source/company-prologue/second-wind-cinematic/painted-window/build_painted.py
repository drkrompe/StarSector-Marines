"""Project fixed shot paintings onto the retained arrival scene; background Blender."""
from pathlib import Path
import argparse
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
ASSETS = HERE / 'assets'
BUILD = HERE / 'build'
BUILD.mkdir(exist_ok=True)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--preview', action='store_true')
parser.add_argument('--landscape-only', action='store_true', help='Retain grey cabin for its paint guide')
parser.add_argument('--terrain-projection', action='store_true', help='Experimental coarse terrain projection; distant ridges can stretch')
args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
source = HERE.parent / 'animatic/jangala-blockout.blend'
bpy.ops.wm.open_mainfile(filepath=str(source))
scene = bpy.context.scene
scene.frame_set(144)
bpy.context.view_layer.update()
root = bpy.data.objects['SECOND WIND | flight root']
camera = bpy.data.objects['02 REVEAL / near glass']
scene.camera = camera
for marker in scene.timeline_markers:
    marker.camera = None

def projector(name, overscan=1.0, parent=None):
    ob = bpy.data.objects.new(name, camera.data.copy())
    scene.collection.objects.link(ob)
    ob.data.lens = camera.data.lens / overscan
    if parent:
        ob.parent = parent
        ob.matrix_basis = camera.matrix_basis.copy()
    else:
        ob.matrix_world = camera.matrix_world.normalized()
    return ob

land_camera = projector('PAINT | fixed landscape projector', 1.35)
cabin_camera = projector('PAINT | ship-local cabin projector', 1.12, parent=root)

def material(name, path, projection):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    tex = nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(str(path), check_existing=True)
    tex.extension = 'EXTEND'
    tex.interpolation = 'Linear'
    # Divide in the surface shader, per pixel. Vertex UV projection stretches
    # across large cabin faces and triangles that cross the projector plane.
    coord = nodes.new('ShaderNodeTexCoord')
    coord.object = projection
    split = nodes.new('ShaderNodeSeparateXYZ')
    links.new(coord.outputs['Object'], split.inputs[0])
    negative_z = nodes.new('ShaderNodeMath')
    negative_z.operation = 'MULTIPLY'
    negative_z.inputs[1].default_value = -1
    links.new(split.outputs['Z'], negative_z.inputs[0])
    vector = nodes.new('ShaderNodeCombineXYZ')
    for axis, scale in (('X',projection.data.lens/projection.data.sensor_width),
                        ('Y',projection.data.lens/projection.data.sensor_width*16/9)):
        divide = nodes.new('ShaderNodeMath')
        divide.operation = 'DIVIDE'
        links.new(split.outputs[axis], divide.inputs[0])
        links.new(negative_z.outputs[0], divide.inputs[1])
        remap = nodes.new('ShaderNodeMath')
        remap.operation = 'MULTIPLY_ADD'
        remap.inputs[1].default_value = scale
        remap.inputs[2].default_value = .5
        links.new(divide.outputs[0], remap.inputs[0])
        links.new(remap.outputs[0], vector.inputs[axis])
    links.new(vector.outputs['Vector'], tex.inputs['Vector'])
    emission = nodes.new('ShaderNodeEmission')
    links.new(tex.outputs['Color'], emission.inputs['Color'])
    out = nodes.new('ShaderNodeOutputMaterial')
    links.new(emission.outputs[0], out.inputs['Surface'])
    return mat

land_mat = material('PAINT | fixed landscape artwork', ASSETS/'landscape-painted-v2.png', land_camera)
cabin_mat = None if args.landscape_only else material('PAINT | fixed cabin artwork', ASSETS/'cabin-painted-v2.png', cabin_camera)

def descendant(ob):
    while ob.parent:
        ob = ob.parent
        if ob == root:
            return True
    return False

def paint(ob, mat):
    ob.data = ob.data.copy()
    ob.data.materials.clear()
    ob.data.materials.append(mat)
    for face in ob.data.polygons:
        face.material_index = 0
    # Projection coordinates come from the material's fixed camera object.

environment = ['Jangala | continuous landform', 'Jangala | winding river',
               'Jangala | far estuary']
painted_names = []
for ob in list(scene.objects):
    if ob.type != 'MESH':
        continue
    if 'Cloud' in ob.name or 'CLOUD' in ob.name or 'storm' in ob.name.lower():
        ob.animation_data_clear()
        ob.hide_render = True
    elif ob.name in environment and args.terrain_projection:
        ob.hide_render = False
        paint(ob, land_mat)
        painted_names.append(ob.name)
    elif descendant(ob):
        if cabin_mat:
            paint(ob, cabin_mat)
    else:
        ob.hide_render = True

# Extend the terrain continuously into the distance. The old separate ridge
# blobs caused disocclusion seams through newly painted forest silhouettes.
land = bpy.data.objects['Jangala | continuous landform']
points = [tuple(land.matrix_world @ v.co) for v in land.data.vertices]
xmin, xmax = min(p[0] for p in points), max(p[0] for p in points)
ymin, ymax = min(p[1] for p in points), max(p[1] for p in points)
boundary = (sorted((p for p in points if p[1] == ymin), key=lambda p:p[0])
            + sorted((p for p in points if p[0] == xmax and p[1] != ymin), key=lambda p:p[1])
            + sorted((p for p in points if p[1] == ymax and p[0] != xmax), key=lambda p:-p[0])
            + sorted((p for p in points if p[0] == xmin and p[1] not in (ymin,ymax)), key=lambda p:-p[1]))
cx, cy = (xmin+xmax)/2, (ymin+ymax)/2
outer = [(cx+(x-cx)*8,cy+(y-cy)*8,0) for x,y,z in boundary]
n = len(boundary)
mesh = bpy.data.meshes.new('PAINT | continuous distant ground mesh')
mesh.from_pydata(boundary+outer, [], [(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)])
continuation = bpy.data.objects.new('PAINT | far ground continuation',mesh)
scene.collection.objects.link(continuation)
continuation.hide_render = not args.terrain_projection
paint(continuation,land_mat)
painted_names.append(continuation.name)

# A continuous painted landscape holds distant forest, sky and horizon together.
# High-window motion needs cabin-to-world parallax, not individual tree depth.
# It is fixed in world space with the landscape projector, not parented to the shot camera.
frame = land_camera.data.view_frame(scene=scene)
backing_distance = 35000 if args.terrain_projection else 14000
corners = [land_camera.matrix_world @ (v * (backing_distance / abs(v.z))) for v in frame]
mesh = bpy.data.meshes.new('PAINT | distant backing mesh')
mesh.from_pydata(corners, [], [(0,1,2,3)])
sky = bpy.data.objects.new('PAINT | distant sky backing', mesh)
scene.collection.objects.link(sky)
paint(sky, land_mat)

scene.render.use_compositing = False
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'None'
scene.view_settings.exposure = 0
scene.view_settings.gamma = 1
scene.render.engine = 'BLENDER_EEVEE'
scene.eevee.taa_render_samples = 32
scene.render.resolution_x = 1920
scene.render.resolution_y = 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.render.fps = 12
scene.frame_start = 120
scene.frame_end = 168
scene.camera = camera

for image in bpy.data.images:
    if image.source == 'FILE' and image.filepath:
        path = Path(bpy.path.abspath(image.filepath))
        if path.is_relative_to(HERE):
            image.filepath = '//' + path.relative_to(HERE).as_posix()
filename = 'painted-landscape-cabin-guide.blend' if args.landscape_only else 'jangala-painted-window.blend'
target = BUILD/filename if args.landscape_only else HERE/filename
if args.landscape_only:
    # Relative image paths are relative to the saved .blend, including this temporary guide.
    for image in bpy.data.images:
        if image.source == 'FILE' and image.filepath.startswith('//assets/'):
            image.filepath = '//../' + image.filepath[2:]
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(target), relative_remap=False, compress=True)

if args.landscape_only or args.preview:
    frames = (144,) if args.landscape_only else (120,132,144,156,168)
    for frame in frames:
        scene.frame_set(frame)
        scene.camera = cabin_camera if args.landscape_only else camera
        name = 'cabin-paint-guide-current.png' if args.landscape_only else f'preview_{frame:04d}.png'
        scene.render.filepath = str((HERE/'guides' if args.landscape_only else BUILD) / name)
        bpy.ops.render.render(write_still=True)
        print('PAINT_PREVIEW', frame, flush=True)

if not args.landscape_only:
    manifest = {
        'source': '../animatic/jangala-blockout.blend', 'source_frame':144,
        'shot_camera':'02 REVEAL / near glass', 'source_camera_animation_changed':False,
        'test_source_frames':[120,168], 'landscape_overscan':1.35, 'cabin_overscan':1.12,
        'landscape_art':'assets/landscape-painted-v2.png',
        'cabin_art':'assets/cabin-painted-v2.png',
        'render_camera_marker_bindings':'cleared in derived scene; original source unchanged',
        'landscape_projection':'fixed world-space reference at source frame 144',
        'cabin_projection':'fixed ship-local reference at source frame 144',
        'painted_environment_meshes':painted_names if args.terrain_projection else [sky.name],
        'landscape_mode':'coarse terrain experiment' if args.terrain_projection else 'continuous painted panorama',
        'backing_distance_m':backing_distance,
        'surface_shading':'Emission: artwork contains its own light and brushwork',
        'scope':'One high-window shot; other cameras and unrestricted camera moves are unvalidated.'
    }
    (HERE/'projection-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print('PAINT_READY', target, flush=True)
