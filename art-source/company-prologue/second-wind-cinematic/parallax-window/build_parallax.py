"""Build the stabilized, layered painted Jangala window study in background Blender."""
import argparse
import json
import os
from pathlib import Path
import sys
import bpy
from mathutils import Vector
sys.path.insert(0, str(Path(__file__).resolve().parent))
from stabilize import stabilize, _curves

HERE = Path(__file__).resolve().parent
BUILD = HERE / "build"
BUILD.mkdir(exist_ok=True)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--preview", action="store_true")
args = parser.parse_args(sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(HERE.parent / "painted-window/jangala-painted-window.blend"))
scene = bpy.context.scene
scene.frame_set(144)
camera = bpy.data.objects["02 REVEAL / near glass"]
root = bpy.data.objects["SECOND WIND | flight root"]
projection = bpy.data.objects["PAINT | fixed landscape projector"]
reference = projection.matrix_world.copy()
scenery, origin, stabilization = stabilize(scene, root)
scene.frame_set(144)
for marker in scene.timeline_markers:
    marker.camera = None
ship_names = {ob.name for ob in root.children_recursive}
for ob in scene.objects:
    if ob.type == "MESH" and ob.name not in ship_names:
        ob.hide_render = True

def material(name, path, opacity=1, haze=0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.surface_render_method = "BLENDED"
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    texture = nodes.new("ShaderNodeTexImage")
    texture.image = bpy.data.images.load(str(path), check_existing=True)
    texture.image.alpha_mode = "STRAIGHT"
    texture.extension = "CLIP"
    texture.interpolation = "Linear"
    emission = nodes.new("ShaderNodeEmission")
    if haze:
        tint = nodes.new("ShaderNodeMixRGB")
        tint.blend_type = "MIX"
        tint.inputs[0].default_value = haze
        tint.inputs[2].default_value = (.34,.43,.50,1)
        links.new(texture.outputs["Color"], tint.inputs[1])
        links.new(tint.outputs[0], emission.inputs["Color"])
    else:
        links.new(texture.outputs["Color"], emission.inputs["Color"])
    alpha = nodes.new("ShaderNodeMapRange")
    alpha.clamp = True
    alpha.inputs["From Min"].default_value = .015
    alpha.inputs["From Max"].default_value = .98
    alpha.inputs["To Min"].default_value = 0
    alpha.inputs["To Max"].default_value = opacity
    links.new(texture.outputs["Alpha"], alpha.inputs["Value"])
    transparent = nodes.new("ShaderNodeBsdfTransparent")
    mix = nodes.new("ShaderNodeMixShader")
    links.new(alpha.outputs[0], mix.inputs[0])
    links.new(transparent.outputs[0], mix.inputs[1])
    links.new(emission.outputs[0], mix.inputs[2])
    out = nodes.new("ShaderNodeOutputMaterial")
    links.new(mix.outputs[0], out.inputs["Surface"])
    return mat

right = reference.to_3x3() @ Vector((1,0,0))
up = reference.to_3x3() @ Vector((0,1,0))
layers = []
def card(name, file, depth, offset_y=0, opacity=1, center=None, width=None, haze=0):
    if width:
        image = bpy.data.images.load(str(file), check_existing=True)
        aspect = image.size[0] / image.size[1]
        halfw, halfh = width/2, width/aspect/2
        vieww = depth * projection.data.sensor_width / projection.data.lens
        viewh = vieww * 9/16
        cx, cy = (center[0]-.5)*vieww, (.5-center[1])*viewh
    else:
        halfw = depth * projection.data.sensor_width / projection.data.lens / 2
        halfh = halfw * 9/16
        cx, cy = 0, -offset_y * halfh * 2
    local = [(cx-halfw,cy-halfh,-depth),(cx+halfw,cy-halfh,-depth),
             (cx+halfw,cy+halfh,-depth),(cx-halfw,cy+halfh,-depth)]
    points = [reference @ Vector(p) - origin for p in local]
    center_point = sum(points, Vector()) / 4
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([p-center_point for p in points], [], [(0,1,2,3)])
    uv = mesh.uv_layers.new(name="Full retained canvas")
    for loop, value in zip(uv.data,((0,0),(1,0),(1,1),(0,1))):
        loop.uv = value
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    obj.parent = scenery
    obj.location = center_point
    mesh.materials.append(material(name,file,opacity,haze))
    obj["depth_m"] = depth
    obj["source_image"] = file.relative_to(HERE).as_posix()
    layers.append({"name":name,"image":file.relative_to(HERE).as_posix(),
                   "depth_m":depth,"canvas_y_registration":offset_y,
                   "opacity":opacity,"center_uv_top_left":center,"width_m":width})
    return obj

card("PARALLAX | sky",HERE/"layers/sky-v1.png",48000)
card("PARALLAX | distant hills",HERE/"layers/hills-v1.png",34000,haze=.10)
card("PARALLAX | far trees",HERE/"layers/far-trees-v1.png",20000,offset_y=-.12)
card("PARALLAX | medium trees and river",HERE/"layers/medium-trees-v1.png",10500)
card("PARALLAX | near trees",HERE/"layers/near-trees-v1.png",5000)
clouds = card("PARALLAX | cloud bank",HERE/"layers/clouds-v1.png",27000,opacity=.85)
atlas = card("PARALLAX | Atlas industrial transit",HERE/"traffic/atlas-painted-v1.png",
             6500,center=(.61,.185),width=1000,haze=.20)
# Independent world motion: keep freight moving bow-first, with an unhurried pass.
for ob, speed in ((clouds,12),(atlas,170)):
    base_position = ob.location.copy()
    for frame in (73,144,216):
        ob.location = base_position + right * (speed * (frame-144) / 12)
        ob.keyframe_insert(data_path="location",frame=frame)
    for curves in _curves(ob.animation_data.action):
        for fc in curves:
            for key in fc.keyframe_points:
                key.interpolation = "LINEAR"

scene.camera = camera
scene.frame_start,scene.frame_end = 120,168
scene.render.resolution_x,scene.render.resolution_y = 1920,1080
scene.render.resolution_percentage = 100
scene.render.fps = 12
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGB"
scene.eevee.taa_render_samples = 64
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"
scene.render.use_compositing = False
# Resolve old scene-relative paths BEFORE changing the scene's saved directory.
for image in bpy.data.images:
    if image.source == "FILE" and image.filepath:
        absolute = Path(bpy.path.abspath(image.filepath)).resolve()
        image.filepath = "//" + os.path.relpath(absolute,HERE).replace(os.sep,"/")
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/"jangala-parallax-window.blend"),
                           relative_remap=False,compress=True)
manifest = {"source":"../painted-window/jangala-painted-window.blend",
            "source_fps":12,"source_test_frames":[120,167.5],"output_fps":24,
            "reference_frame":144,"layers":layers,"stabilization":stabilization,
            "alpha_processing":"Raw ImageGen pixels unchanged; shader remaps alpha .015..98 to0..layer opacity to suppress faint flecks and make near-opaque interiors solid.",
            "traffic":"One Atlas interpretation from canonical sprite,6500m plane/1000m full canvas width. Shot scale and altitude are compositing choices, not canonical ship dimensions.",
            "scope":"Single window view; independent fixed painted layers, no geometric individual-tree depth."}
(HERE/"scene-manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
if args.preview:
    for frame in (120,144,168):
        scene.frame_set(frame)
        scene.camera = camera
        scene.render.filepath = str(BUILD/f"preview_{frame:04d}.png")
        bpy.ops.render.render(write_still=True)
print("PARALLAX_READY",flush=True)
