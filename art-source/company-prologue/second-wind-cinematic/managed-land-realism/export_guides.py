"""Shot03 camera/coverage guides. Background Blender --factory-startup only.

Preserves source blend; all output stays beside this script. Guide-only stage.
"""
import argparse
import json
import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / 'layered-next-scenes/layered-next-scenes.blend'
GUIDES = HERE / 'guides'
GUIDES.mkdir(exist_ok=True)
parser = argparse.ArgumentParser()
parser.add_argument('--inspect', action='store_true')
parser.add_argument('--wide-only-version', type=int, choices=(1, 2))
parser.add_argument('--single-plot', action='store_true')
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
scene = bpy.context.scene
camera = bpy.data.objects['03 LAND / side window']
root = bpy.data.objects['SECOND WIND | flight root']
world = bpy.data.objects['LAYERED | inverse source flight']
for marker in scene.timeline_markers:
    marker.camera = None

def frame(value):
    scene.frame_set(int(value), subframe=value % 1)
    bpy.context.view_layer.update()

frame(263.75)
scene.camera = camera
ship = set(root.children_recursive)
original_visible = {obj: not obj.hide_render for obj in scene.objects}
source_mid_inverse = world.matrix_world.inverted()
reference_matrix = camera.matrix_world.normalized().copy()
land = bpy.data.objects['Jangala | continuous landform']
terrain_matrix = source_mid_inverse @ land.matrix_world
terrain = BVHTree.FromPolygons([terrain_matrix @ vertex.co for vertex in land.data.vertices],
                               [tuple(poly.vertices) for poly in land.data.polygons])
hits = []
samples = []
camera_matrices = []
for i in range(96):
    t = 240 + i * .5
    frame(t)
    matrix = world.matrix_world.inverted() @ camera.matrix_world.normalized()
    camera_matrices.append(matrix.copy())
    corners = camera.data.view_frame(scene=scene)
    # Blender corners: top-right, bottom-right, bottom-left, top-left.
    eye = matrix.translation
    for iy in range(19):
        v = iy / 18
        left = corners[2].lerp(corners[3], v)
        right = corners[1].lerp(corners[0], v)
        for ix in range(49):
            point = matrix @ left.lerp(right, ix / 48)
            hit, _, _, _ = terrain.ray_cast(eye, (point - eye).normalized(), 60000)
            if hit is not None and -150 <= hit.y <= 2700:
                hits.append(list(hit))
    if i in (0, 47, 95):
        samples.append({'source_frame': t, 'camera_world_matrix': [list(row) for row in camera.matrix_world],
                        'camera_source_world_matrix': [list(row) for row in matrix],
                        'scenery_translation': list(world.location)})
if not hits:
    raise RuntimeError('No managed-land ray intersections found')
# Include guardband and useful continuous land behind the outer hedgerow.
xmin, xmax = min(p[0] for p in hits) - 300, max(p[0] for p in hits) + 300
ymin, ymax = -150, 2700
centre = ((xmin + xmax) / 2, (ymin + ymax) / 2)
width = xmax - xmin
height = ymax - ymin
# Square metric pixels; image resolution is tailored to this footprint.
top_width = 3072
top_height = round(top_width * height / width)
source_reference = source_mid_inverse @ reference_matrix
ref_forward = source_reference.to_3x3() @ Vector((0, 0, -1))
ref_inverse = source_reference.inverted()
coverage = []
for role, y in (('woodland', 3000), ('hills', 11000), ('sky', 35000)):
    depth = (y - source_reference.translation.y) / ref_forward.y
    halfw = depth * camera.data.sensor_width / (camera.data.lens * 1920 / 2560 / 1.08) / 2
    halfh = halfw * 1080 / 2560
    centre = source_reference.translation + ref_forward * depth
    coordinates = []
    for matrix in camera_matrices:
        eye = matrix.translation
        for corner in camera.data.view_frame(scene=scene):
            ray = matrix.to_3x3() @ corner
            distance = (centre - eye).dot(ref_forward) / ray.dot(ref_forward)
            projected = ref_inverse @ (eye + ray * distance)
            coordinates.append((.5 + projected.x / (2 * halfw), .5 + projected.y / (2 * halfh)))
    bounds = [min(p[0] for p in coordinates), max(p[0] for p in coordinates),
              min(p[1] for p in coordinates), max(p[1] for p in coordinates)]
    coverage.append({'role': role, 'source_y_m': y, 'reference_optical_depth_m': depth,
                     'full96_frame_uv_bounds': bounds,
                     'all_output_corners_inside': 0 <= bounds[0] < bounds[1] <= 1 and 0 <= bounds[2] < bounds[3] <= 1})
report = {'source': str(SOURCE), 'camera': camera.name, 'source_fps': 12,
          'source_frames': [240, 287.5], 'output_fps': 24, 'output_frames': 96,
          'samples_per_pixel': 96, 'lens_mm': camera.data.lens,
          'sensor_width_mm': camera.data.sensor_width,
          'midpoint_world_matrix': [list(row) for row in reference_matrix],
          'samples': samples, 'root_translation': list(root.location),
          'perspective_plate_coverage': coverage,
          'topdown': {'source_world_bounds_xy_m': [xmin, xmax, ymin, ymax],
                      'image_dimensions': [top_width, top_height],
                      'image_orientation': '+X right, +Y up; image top is ymax',
                      'method': '96 half-frame camera frusta; 49x19 rays each against source terrain. Retain hits at source Y -150..2700; add300m X guardband.'},
          'perspective_environment': {'dimensions': [2560, 1080], 'horizontal_overscan': 2560 / 1920 * 1.08,
                                      'vertical_overscan': 1.08, 'reference_source_frame': 263.75},
          'cabin': {'dimensions': [1920, 1080], 'overscan_xy': 1.12, 'reference_source_frame': 263.75},
          'objects': [{'name': obj.name, 'index': obj.pass_index, 'visible': original_visible[obj]}
                      for obj in scene.objects if obj.type == 'MESH']}
(HERE / 'guide-manifest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print('GUIDE_GEOMETRY', json.dumps({k: report[k] for k in ('lens_mm', 'sensor_width_mm', 'topdown', 'perspective_environment')}), flush=True)
if args.inspect:
    sys.exit(0)

scene.render.engine = 'BLENDER_EEVEE'
scene.eevee.taa_render_samples = 96
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.use_compositing = False
scene.render.image_settings.compression = 25
scene.render.film_transparent = False

def visibility(predicate):
    for obj in scene.objects:
        if obj.type in ('MESH', 'CURVE', 'VOLUME'):
            obj.hide_render = not (original_visible.get(obj, False) and predicate(obj))

def render(name, cam, resolution=(1920, 1080), transparent=False):
    scene.camera = cam
    scene.render.resolution_x, scene.render.resolution_y = resolution
    scene.render.film_transparent = transparent
    scene.render.filepath = str(GUIDES / name)
    bpy.ops.render.render(write_still=True)
    print('GUIDE_RENDER', name, flush=True)

if args.single_plot:
    frame(263.75)
    visibility(lambda obj: obj.pass_index in (13, 14))
    data = bpy.data.cameras.new('GUIDE | single clearing topdown')
    top = bpy.data.objects.new(data.name, data)
    scene.collection.objects.link(top)
    centre = (-4390,1470)
    top.location = world.matrix_world @ Vector((*centre,5000))
    data.type = 'ORTHO'
    data.ortho_scale = 1280
    data.clip_end = 12000
    top.rotation_euler=(0,0,0)
    render('shot03-single-field-topdown-guide.png',top)
    patch = {'camera':'Orthographic vertical, +X right/+Y up',
             'source_world_canvas_xy_m':[-5030,-3750,1110,1830],
             'source_plot':'Plantation | clearing 2 1',
             'source_plot_xy_m':[-4730,-4050,1125,1815],
             'plot_texture_uv_bounds':[.234375,.765625,.020833333333,.979166666667],
             'dimensions':[1920,1080],
             'usage':'Map central plot subrectangle per clearing; preserve raw master and use shader UV cropping. Fine crop rows, irrigation and surface material, not high walls.'}
    (HERE/'single-field-guide.json').write_text(json.dumps(patch,indent=2),encoding='utf-8')
    print('SINGLE_FIELD_GUIDE',json.dumps(patch),flush=True)
    sys.exit(0)

frame(263.75)
visibility(lambda obj: True)
if not args.wide_only_version:
    render('shot03-camera-midpoint.png', camera)

def projector(name, lens, width=1920, height=1080):
    data = camera.data.copy()
    data.lens = lens
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    obj.matrix_world = reference_matrix
    return obj

cabin = projector('GUIDE | cabin overscan', camera.data.lens / 1.12)
visibility(lambda obj: True)
if not args.wide_only_version:
    render('shot03-cabin-overscan-guide.png', cabin)
visibility(lambda obj: obj in ship)
if not args.wide_only_version:
    render('shot03-cabin-only-guide.png', cabin, transparent=True)

version = args.wide_only_version or 2
guard = 1.08 if version == 2 else 1.0
suffix = '-v2' if version == 2 else ''
wide = projector('GUIDE | environment horizontal overscan', camera.data.lens * 1920 / 2560 / guard)
visibility(lambda obj: obj not in ship)
render(f'shot03-environment-wide-guide{suffix}.png', wide, (2560, 1080))
for role, ids in (('hills', {31}), ('woodland', {32, 33}), ('cloud', {35}), ('near-hedgerows', {34})):
    visibility(lambda obj, ids=ids: obj not in ship and obj.pass_index in ids)
    render(f'shot03-{role}-wide-guide{suffix}.png', wide, (2560, 1080), transparent=True)

if args.wide_only_version:
    print('WIDE_GUIDES_READY', version, flush=True)
    sys.exit(0)

frame(263.75)
visibility(lambda obj: obj not in ship and (obj.pass_index in (13, 14, 34)))
data = bpy.data.cameras.new('GUIDE | source-world fields orthographic')
top = bpy.data.objects.new(data.name, data)
scene.collection.objects.link(top)
top.location = world.matrix_world @ Vector((centre[0], centre[1], 5000))
top.rotation_euler = (0, 0, 0)
data.type = 'ORTHO'
data.ortho_scale = width
data.clip_end = 12000
render('shot03-fields-topdown-guide.png', top, (top_width, top_height))
print('GUIDES_READY', GUIDES, flush=True)
