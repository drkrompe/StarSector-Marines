"""Render a clean guide view, its silhouette mask and its depth map.

Run after build_reference.py has written build/geom/second-wind-geometry.blend:
  blender --background --factory-startup --python render_guide.py [-- --view quarter30]
--view is a name from views.py (default port-side).

Opens that untouched scene, applies every modifier on a copy (so later UVs and
visibility tests address the faces that actually render), and saves it as
build/base.blend. Renders the chosen view at 1920x1080 with
no lamps: a uniform white world gives even, shadowless light that still reads
the form through ambient occlusion.

Outputs
  guide/<view>-guide.png      RGB, hull composited on flat BACKGROUND
  guide/<view>-mask.png       L, render alpha (coverage), antialiased
  guide/<view>-depth.png      L, view-depth preview (near = bright), review only
  build/depth-<view>.exr      float view-Z depth, alpha 0 off-hull, no filtering
  guide/<view>-camera.json    camera matrix, ortho scale, resolution
"""
from pathlib import Path
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
BUILD = HERE / 'build'
GUIDE = HERE / 'guide'
GUIDE.mkdir(exist_ok=True)
RES = (1920, 1080)
BACKGROUND = (0.84, 0.84, 0.84)  # display-referred flat grey behind the hull
argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
VIEW = argv[argv.index('--view') + 1] if '--view' in argv else 'port-side'
sys.path.insert(0, str(HERE))
import views

bpy.ops.wm.open_mainfile(filepath=str(BUILD / 'geom' / 'second-wind-geometry.blend'))
scene = bpy.context.scene

# Apply modifiers (bevels) on every mesh so the saved base has real faces.
bpy.ops.object.select_all(action='DESELECT')
for ob in scene.objects:
    if ob.type == 'MESH':
        ob.select_set(True)
        bpy.context.view_layer.objects.active = ob
bpy.ops.object.convert(target='MESH')
for ob in [o for o in scene.objects if o.type == 'LIGHT']:
    bpy.data.objects.remove(ob)

scene.render.resolution_x, scene.render.resolution_y = RES
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.view_settings.view_transform = 'Standard'
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (1, 1, 1, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 1.6
scene.cycles.samples = 64
bpy.ops.wm.save_as_mainfile(filepath=str(BUILD / 'base.blend'))  # before any spike camera exists
cam = scene.camera = views.camera(VIEW)

scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.filepath = str(BUILD / f'guide-rgba-{VIEW}.png')
bpy.ops.render.render(write_still=True)

# Depth: every surface emits its camera view-Z; one sample and a minimal pixel
# filter so edge pixels hold one surface's depth rather than a blend.
depth_mat = bpy.data.materials.new('SPIKE depth')
depth_mat.use_nodes = True
nodes, links = depth_mat.node_tree.nodes, depth_mat.node_tree.links
nodes.clear()
camdata = nodes.new('ShaderNodeCameraData')
emit = nodes.new('ShaderNodeEmission')
out = nodes.new('ShaderNodeOutputMaterial')
links.new(camdata.outputs['View Z Depth'], emit.inputs['Color'])
links.new(emit.outputs[0], out.inputs['Surface'])
scene.view_layers[0].material_override = depth_mat
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0
scene.cycles.samples = 1
scene.cycles.use_denoising = False
scene.cycles.filter_width = 0.01
scene.view_settings.view_transform = 'Raw' if 'Raw' in [t.identifier for t in
    bpy.types.ColorManagedViewSettings.bl_rna.properties['view_transform'].enum_items] else 'Standard'
scene.render.image_settings.file_format = 'OPEN_EXR'
scene.render.image_settings.color_depth = '32'
scene.render.filepath = str(BUILD / f'depth-{VIEW}.exr')
bpy.ops.render.render(write_still=True)

(GUIDE / f'{VIEW}-camera.json').write_text(json.dumps({
    'camera': cam.name,
    'source': 'build_reference.py port-side camera, unchanged' if views.VIEWS[VIEW] is None else 'views.py orbit (azimuth, elevation, ortho scale) = %s' % (views.VIEWS[VIEW],),
    'matrix_world': [list(r) for r in cam.matrix_world],
    'ortho_scale': cam.data.ortho_scale,
    'resolution': list(RES),
    'background_srgb': BACKGROUND,
    'lighting': 'No lamps; uniform white world, strength 1.6, Standard view transform',
}, indent=2) + '\n')

# Post: composite, mask and depth preview, with Blender's bundled numpy.
import numpy as np

def pixels(path):
    im = bpy.data.images.load(str(path))
    w, h = im.size
    a = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]
    return im, a

def save(arr, path, mode):
    h, w = arr.shape[:2]
    channels = arr if arr.ndim == 3 else np.repeat(arr[..., None], 3, 2)
    rgba = np.concatenate([channels, np.ones((h, w, 1), np.float32)], 2)
    im = bpy.data.images.new(path.stem, w, h, alpha=True, float_buffer=False)
    im.colorspace_settings.name = 'Non-Color'
    im.pixels[:] = rgba[::-1].ravel()
    im.filepath_raw = str(path)
    im.file_format = 'PNG'
    im.save()

_, rgba = pixels(BUILD / f'guide-rgba-{VIEW}.png')  # PNG loads as display sRGB bytes
alpha = rgba[..., 3:4]
save(rgba[..., :3] * alpha + np.array(BACKGROUND, np.float32) * (1 - alpha), GUIDE / f'{VIEW}-guide.png', 'RGB')
save(alpha[..., 0], GUIDE / f'{VIEW}-mask.png', 'L')
_, depth = pixels(BUILD / f'depth-{VIEW}.exr')
z, cover = depth[..., 0], depth[..., 3] > 0.5
near, far = z[cover].min(), z[cover].max()
preview = np.where(cover, 0.15 + 0.85 * (far - z) / (far - near), 0)
save(preview.astype(np.float32), GUIDE / f'{VIEW}-depth.png', 'L')
print('SPIKE depth range', near, far, 'covered px', int(cover.sum()))
