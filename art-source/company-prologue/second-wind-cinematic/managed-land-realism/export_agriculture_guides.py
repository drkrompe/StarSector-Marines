"""Background Blender --factory-startup --python this.py; never saves scene.

Loads the current built source, builds/replaces AGRI geometry in memory with
procedural materials, and exports registered actual-camera guides and masks.
"""
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from agriculture_props import build_agriculture_props, RESKIN_CROP_UV

bpy.ops.wm.open_mainfile(filepath=str(HERE/'managed-land-realism.blend'))
scene=bpy.context.scene
scene.frame_set(263,subframe=.75)
world=bpy.data.objects['LAYERED | inverse source flight']
camera=bpy.data.objects['03 LAND / side window']
land=bpy.data.objects['Jangala | continuous landform']
matrix=world.matrix_world.inverted()@land.matrix_world
ground=BVHTree.FromPolygons([matrix@v.co for v in land.data.vertices],
                            [tuple(p.vertices) for p in land.data.polygons])
# Guide generation must remain independent of the optional art/registration.
metadata=build_agriculture_props(scene,world,ground,use_reskin=False)
scene.frame_set(263,subframe=.75)
scene.camera=camera
for marker in scene.timeline_markers:
    marker.camera=None
scene.render.engine='BLENDER_EEVEE'
scene.eevee.taa_render_samples=48
scene.render.resolution_x=1920
scene.render.resolution_y=1080
scene.render.resolution_percentage=100
scene.render.use_compositing=False
scene.render.image_settings.file_format='PNG'
scene.render.image_settings.color_mode='RGBA'
guides=HERE/'guides'
guides.mkdir(exist_ok=True)
def render(name):
    scene.render.filepath=str(guides/name)
    bpy.ops.render.render(write_still=True)

render('shot03-agriculture-context-guide.png')
for obj in scene.objects:
    if obj.type in ('MESH','CURVE','VOLUME'):
        obj.hide_render=not (obj.pass_index==61 and obj.name.startswith('AGRI | '))
scene.render.film_transparent=True
render('shot03-agriculture-isolated-guide.png')
white=bpy.data.materials.new('AGRI guide white mask')
white.use_nodes=True
nodes=white.node_tree.nodes
nodes.clear()
emission=nodes.new('ShaderNodeEmission')
out=nodes.new('ShaderNodeOutputMaterial')
white.node_tree.links.new(emission.outputs[0],out.inputs[0])
scene.view_layers[0].material_override=white
render('shot03-agriculture-mask.png')
scene.view_layers[0].material_override=None
original_lens=camera.data.lens
camera.data.lens=original_lens/1.08
render('shot03-agriculture-overscan-guide.png')
camera.data.lens=original_lens
scene.render.resolution_x=7680
scene.render.resolution_y=4320
scene.render.use_border=True
scene.render.use_crop_to_border=True
scene.render.border_min_x,scene.render.border_max_x,scene.render.border_min_y,scene.render.border_max_y=RESKIN_CROP_UV
render('shot03-agriculture-tight-reskin-guide.png')
scene.render.use_border=False
scene.render.use_crop_to_border=False
scene.render.resolution_x=1920
scene.render.resolution_y=1080
metadata.update({'reference_source_frame':263.75,'dimensions':[1920,1080],
                 'camera':camera.name,'lens_mm':original_lens,
                 'guide':'shot03-agriculture-isolated-guide.png',
                 'context':'shot03-agriculture-context-guide.png',
                 'mask':'shot03-agriculture-mask.png',
                 'overscan':'shot03-agriculture-overscan-guide.png',
                 'overscan_factor':1.08,
                 'reskin_contract':'Use shot03-agriculture-tight-reskin-guide.png as geometry authority. Preserve exact crop canvas and alpha silhouette; materials only, no extra components. Save registered RGBA as assets/agriculture-reskin-v1.png. Original full guides are inspection only.',
                 'reskin_crop':{'file':'shot03-agriculture-tight-reskin-guide.png','original_camera_pixel_bounds_top_left':[1150,1640,370,740],'original_camera_uv_bounds':RESKIN_CROP_UV,'density_multiplier':4,'native_dimensions':[1960,1480],'method':'Blender camera render border, crop_to_border; no raster cropping or rescaling'},
                 'projection':'One midpoint projector parented per rigid assembly, then per-assembly alpha-bounds affine from agriculture-reskin-registration.json when art exists. Approximate projected material finish: generated internal panels are not exactly registered. Procedural material retained on backward faces and outside image alpha.',
                 'visible_parts':'Near right: low crop tender with four lift pods, long transverse crop spray arms, cream tank, orange control cabin. Far right: two low service sheds, three cylindrical silos, cargo crates, service pad, antenna. Small survey drone above distant field.',
                 'light_baking_limit':'Request neutral weathered material without added shadows/highlights: BSDF supplies scene light. Ground shadow is separate terrain-conforming procedural decal.',
                 'object_screen_bounds':{}})
from bpy_extras.object_utils import world_to_camera_view
for obj in scene.objects:
    if obj.type=='MESH' and obj.pass_index==61:
        points=[world_to_camera_view(scene,camera,obj.matrix_world@Vector(v)) for v in obj.bound_box]
        metadata['object_screen_bounds'][obj.name]=[min(p.x for p in points),max(p.x for p in points),min(p.y for p in points),max(p.y for p in points)]
(HERE/'agriculture-guide-manifest.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
print('AGRICULTURE_GUIDES_READY',flush=True)
