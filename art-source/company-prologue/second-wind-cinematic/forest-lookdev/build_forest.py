"""Build a foliage material/motion study from the retained arrival greybox.

Background Blender only. The source scene and its camera keys stay unchanged.
"""
from pathlib import Path
import argparse
import json
import math
import random
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from plants import build_plants

BUILD = HERE / "build"
REVIEW = HERE / "review"
ASSETS = HERE / "assets"
for folder in (BUILD, REVIEW, ASSETS, BUILD / "frames"):
    folder.mkdir(parents=True, exist_ok=True)
parser = argparse.ArgumentParser()
parser.add_argument("--preview", action="store_true")
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
source = HERE.parent / "animatic" / "jangala-blockout.blend"
bpy.ops.wm.open_mainfile(filepath=str(source))
scene = bpy.context.scene
prototypes = build_plants(bpy, ASSETS / "foliage-atlas.png")
for plant in prototypes:
    plant["object"].hide_render = True
    plant["object"].hide_set(True)

def aim(obj, point):
    obj.rotation_euler = (Vector(point) - obj.location).to_track_quat("-Z", "Y").to_euler()

def camera(name, position, target, lens=40, ortho=None):
    data = bpy.data.cameras.new(name)
    ob = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(ob)
    ob.location = position
    data.clip_end = 60000
    data.lens = lens
    if ortho:
        data.type = "ORTHO"
        data.ortho_scale = ortho
    aim(ob, target)
    return ob

# Bake the exact 3D plants, not unrelated generated tree pictures. Neutral broad
# fill keeps the distant impostor's baked light restrained under the shared sun.
bake = bpy.data.scenes.new("BAKE | plant appearance")
bpy.context.window.scene = bake
bake.render.engine = "BLENDER_EEVEE"
bake.eevee.taa_render_samples = 48
bake.render.resolution_x = 768
bake.render.resolution_y = 768
bake.render.resolution_percentage = 100
bake.render.image_settings.file_format = "PNG"
bake.render.image_settings.color_mode = "RGBA"
bake.render.film_transparent = True
bake.view_settings.view_transform = "Standard"
bake.world = bpy.data.worlds.new("Bake soft neutral daylight")
bake.world.use_nodes = True
bake.world.node_tree.nodes["Background"].inputs[0].default_value = (.72, .76, .72, 1)
bake.world.node_tree.nodes["Background"].inputs[1].default_value = .8
lightdata = bpy.data.lights.new("Bake broad fill", "AREA")
lightdata.energy = 11000
lightdata.shape = "DISK"
lightdata.size = 55
light = bpy.data.objects.new("Bake broad fill", lightdata)
bake.collection.objects.link(light)
light.location = (0, -15, 45)
aim(light, (0, 0, 15))
plant_copies = []
for i, plant in enumerate(prototypes):
    ob = bpy.data.objects.new(plant["name"] + " bake", plant["object"].data)
    bake.collection.objects.link(ob)
    plant_copies.append(ob)
    ob.hide_render = True
cam = camera("BAKE camera", (0, -80, 20), (0, 0, 20), ortho=44)
bake.camera = cam
for i, (plant, ob) in enumerate(zip(prototypes, plant_copies)):
    ob.hide_render = False
    height = plant["height"]
    span = max(height, plant["crown_radius"] * 2) * 1.12
    cam.data.ortho_scale = span
    for direction in ("side", "top"):
        if direction == "side":
            cam.location = (0, -90, height / 2)
            aim(cam, (0, 0, height / 2))
        else:
            cam.location = (0, 0, 100)
            aim(cam, (0, 0, 0))
        bake.render.filepath = str(ASSETS / f"plant-{i}-{direction}.png")
        bpy.ops.render.render(write_still=True)
    plant["bake_span"] = span
    ob.hide_render = True
    print("PLANT_BAKED", i, flush=True)
bpy.context.window.scene = scene

def noise_material(name, low, high, scale, roughness=.9):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    shader = nodes.get("Principled BSDF")
    shader.inputs["Roughness"].default_value = roughness
    noise = nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = scale
    noise.inputs["Detail"].default_value = 4
    coord = nodes.new("ShaderNodeTexCoord")
    links.new(coord.outputs["Object"], noise.inputs["Vector"])
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (*low, 1)
    ramp.color_ramp.elements[1].color = (*high, 1)
    links.new(noise.outputs["Fac"], ramp.inputs[0])
    links.new(ramp.outputs[0], shader.inputs["Base Color"])
    bump = nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = .18
    bump.inputs["Distance"].default_value = .12
    links.new(noise.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs[0], shader.inputs["Normal"])
    return mat

land = noise_material("LOOKDEV | wet forest floor", (.018,.036,.019), (.072,.105,.041), .012)
terrain = bpy.data.objects["Jangala | continuous landform"]
terrain.data.materials.clear()
terrain.data.materials.append(land)
ridge = bpy.data.objects.get("Jangala | distant folded ridgeline")
if ridge:
    ridge.data.materials.clear()
    ridge.data.materials.append(land)
    for polygon in ridge.data.polygons:
        polygon.use_smooth = True
    smooth = ridge.modifiers.new("LOOKDEV | soften distant blockout ridges", "SUBSURF")
    smooth.levels = 2
    smooth.render_levels = 2
water = noise_material("LOOKDEV | river water", (.035,.11,.13), (.10,.23,.23), .024, .24)
for name in ("Jangala | winding river", "Jangala | far estuary"):
    ob = bpy.data.objects.get(name)
    if ob:
        ob.data.materials.clear()
        ob.data.materials.append(water)
for name in ("Jangala | interlocking forest canopy", "Jangala | port approach canopy"):
    bpy.data.objects[name].hide_render = True
    bpy.data.objects[name].hide_set(True)
# The faceted storm masses were timing proxies. For the foliage study, a quiet
# bright overcast sky makes leaf edges and depth easier to assess.
for ob in scene.objects:
    if "Storm" in ob.name or "storm" in ob.name or "Cloud breakup" in ob.name:
        ob.hide_render = True
        if ob.animation_data:
            ob.animation_data_clear()
world = scene.world
world.node_tree.nodes["Background"].inputs[0].default_value = (.48,.61,.66,1)
world.node_tree.nodes["Background"].inputs[1].default_value = .8
sun = bpy.data.objects["Shared sun | all shots"]
sun.data.energy = 2.6
sun.data.angle = .16

# Roots are seated on evaluated terrain geometry, including its coarse slopes.
depsgraph = bpy.context.evaluated_depsgraph_get()
evaluated_land = terrain.evaluated_get(depsgraph)
def ground(x, y):
    hit, p, _, _ = evaluated_land.ray_cast(Vector((x,y,5000)),Vector((0,0,-1)))
    return p.z if hit else 0

def river_y(x):
    return 2850 + 560 * math.sin(x / 3200) + 230 * math.sin(x / 1250)

near_collection = bpy.data.collections.new("FOREST | near three-dimensional plants")
scene.collection.children.link(near_collection)
rng = random.Random(8137)
near_count = 0
for x in range(-1450, 801, 26):
    for y in range(480, 1271, 25):
        if rng.random() < .10:
            continue
        xx, yy = x + rng.uniform(-10,10), y + rng.uniform(-8,8)
        kind = rng.choices((0,1,2), (5,2,3))[0]
        plant = prototypes[kind]
        ob = bpy.data.objects.new(f"Forest edge {near_count:04d}", plant["object"].data)
        near_collection.objects.link(ob)
        ob.location = (xx, yy, ground(xx, yy))
        s = rng.uniform(.75,1.25)
        ob.scale = (s,s,s*rng.uniform(.9,1.1))
        ob.rotation_euler[2] = rng.uniform(0,math.tau)
        near_count += 1

# Young, spreading frond plants break up the empty floor below mature crowns.
# This lower layer uses the same mesh and atlas; no extra plant textures.
understory_count = 0
for x in range(-1450, 801, 17):
    for y in range(481, 1271, 18):
        if rng.random() < .25:
            continue
        xx, yy = x + rng.uniform(-7,7), y + rng.uniform(-7,7)
        ob = bpy.data.objects.new(f"Forest understory {understory_count:04d}", prototypes[2]["object"].data)
        near_collection.objects.link(ob)
        ob.location = (xx, yy, ground(xx, yy))
        s = rng.uniform(.28,.62)
        ob.scale = (s*1.4,s*1.4,s*.8)
        ob.rotation_euler[2] = rng.uniform(0,math.tau)
        understory_count += 1

def impostor_material(kind, view):
    mat = bpy.data.materials.new(f"LOOKDEV | distant {kind} {view}")
    mat.use_nodes = True
    mat.surface_render_method = "DITHERED"
    mat.use_backface_culling = False
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    shader = nodes.get("Principled BSDF")
    shader.inputs["Roughness"].default_value = .9
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(str(ASSETS / f"plant-{kind}-{view}.png"))
    tex.extension = "CLIP"
    links.new(tex.outputs["Color"], shader.inputs["Base Color"])
    cut = nodes.new("ShaderNodeMath")
    cut.operation = "GREATER_THAN"
    cut.inputs[1].default_value = .25
    links.new(tex.outputs["Alpha"], cut.inputs[0])
    links.new(cut.outputs[0], shader.inputs["Alpha"])
    return mat

materials = [impostor_material(kind, view) for kind in range(3) for view in ("side","top")]
verts, faces, slots = [], [], []
def quad(points, slot):
    offset = len(verts)
    verts.extend(points)
    faces.append((offset,offset+1,offset+2,offset+3))
    slots.append(slot)

far_count = 0
regions = [(-13900,-4300,1400,10500,29),(-1800,2800,1300,8200,32)]
for xmin,xmax,ymin,ymax,step in regions:
    for x in range(xmin,xmax,step):
        for y in range(ymin,ymax,step):
            xx,yy = x+rng.uniform(-step*.4,step*.4), y+rng.uniform(-step*.4,step*.4)
            if abs(yy-river_y(xx)) < 290:
                continue
            # Broken forest edges, with a denser continuous canopy in each belt.
            edge = min(xx-xmin,xmax-xx,yy-ymin,ymax-yy)
            if rng.random() < max(.04, .8 * (1 - min(1,max(0,edge)/400))):
                continue
            kind = rng.choices((0,1,2),(6,2,2))[0]
            plant = prototypes[kind]
            s = rng.uniform(1.3,1.85)
            z = ground(xx,yy)
            span = plant["bake_span"]*s
            h = plant["height"]*s
            a = rng.uniform(0,math.tau)
            u = Vector((math.cos(a),math.sin(a),0))*span/2
            v = Vector((-math.sin(a),math.cos(a),0))*span/2
            base = Vector((xx,yy,z))
            # Two upright views retain side silhouettes; the horizontal canopy
            # view supplies coverage to the elevated window camera. They remain
            # fixed in world space and never turn to face the camera.
            for axis in (u,v):
                centre=base+Vector((0,0,h/2))
                zz=Vector((0,0,span/2))
                quad([tuple(centre-axis-zz),tuple(centre+axis-zz),tuple(centre+axis+zz),tuple(centre-axis+zz)],kind*2)
            centre=base+Vector((0,0,h*.76))
            quad([tuple(centre-u-v),tuple(centre+u-v),tuple(centre+u+v),tuple(centre-u+v)],kind*2+1)
            far_count += 1
mesh=bpy.data.meshes.new("Distant forest fixed impostor mesh")
mesh.from_pydata(verts,[],faces)
for mat in materials:
    mesh.materials.append(mat)
uv=mesh.uv_layers.new(name="Baked plant views")
for poly,slot in zip(mesh.polygons,slots):
    poly.material_index=slot
    for index,co in zip(poly.loop_indices,((0,0),(1,0),(1,1),(0,1))):
        uv.data[index].uv=co
mesh.update()
far=bpy.data.objects.new("FOREST | fixed side and top impostors",mesh)
scene.collection.objects.link(far)

# Optional inspection camera is outside the six-shot cinematic. It travels past
# the same forest edge, allowing the leaf cards and branch depth to be judged.
detail=camera("LOOKDEV ONLY | close forest inspection",(-930,448,28),(-820,535,20),45)
for f,loc,target in ((801,(-1000,447,29),(-910,530,22)),(848,(-920,447,29),(-830,530,22))):
    detail.location=loc
    aim(detail,target)
    detail.keyframe_insert(data_path="location",frame=f)
    detail.keyframe_insert(data_path="rotation_euler",frame=f)
marker=scene.timeline_markers.new("LOOKDEV ONLY | close forest inspection",frame=801)
marker.camera=detail
scene.frame_end=848
scene.eevee.taa_render_samples=48
scene.render.resolution_x=1920
scene.render.resolution_y=1080
scene.render.image_settings.color_mode="RGB"
scene.render.fps=12

# Depth-based atmospheric perspective is calculated once in scene space.
# It keeps distant canopy readable without flattening the nearby leaf cards.
scene.view_layers[0].use_pass_z = True
comp = bpy.data.node_groups.new("LOOKDEV | distance haze", "CompositorNodeTree")
comp.interface.new_socket(name="Image", in_out="OUTPUT", socket_type="NodeSocketColor")
nodes, links = comp.nodes, comp.links
layers = nodes.new("CompositorNodeRLayers")
layers.scene = scene
attenuation = nodes.new("ShaderNodeMath")
attenuation.operation = "MULTIPLY"
attenuation.inputs[1].default_value = -.000028
links.new(layers.outputs["Depth"], attenuation.inputs[0])
exponential = nodes.new("ShaderNodeMath")
exponential.operation = "EXPONENT"
links.new(attenuation.outputs[0], exponential.inputs[0])
fog = nodes.new("ShaderNodeMath")
fog.operation = "SUBTRACT"
fog.inputs[0].default_value = 1
links.new(exponential.outputs[0], fog.inputs[1])
mix = nodes.new("ShaderNodeMixRGB")
mix.blend_type = "MIX"
links.new(fog.outputs[0], mix.inputs[0])
links.new(layers.outputs["Image"], mix.inputs[1])
mix.inputs[2].default_value = (.42,.53,.56,1)
out = nodes.new("NodeGroupOutput")
links.new(mix.outputs[0], out.inputs["Image"])
scene.compositing_node_group = comp
scene.render.use_compositing = True
scene.frame_set(432)
scene.camera=bpy.data.objects["04 PORT / side window"]
# Use relative texture paths, retained alongside the scene. No duplicate pack.
for image in bpy.data.images:
    if image.source=="FILE" and image.filepath:
        try:
            path=Path(bpy.path.abspath(image.filepath))
            if path.is_relative_to(HERE):
                image.filepath="//"+path.relative_to(HERE).as_posix()
        except ValueError:
            pass
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/"jangala-forest-lookdev.blend"),relative_remap=False,compress=True)
manifest={"base_scene":"../animatic/jangala-blockout.blend","source_camera_keys_changed":False,
          "near_instances":near_count,"understory_instances":understory_count,"far_impostors":far_count,"atlas_pixels":[1254,1254],
          "species":[{k:v for k,v in p.items() if k!="object"} for p in prototypes],
          "review_ranges":[[120,191],[396,467],[801,848]],"source_fps":12,
          "notes":"Texture and foliage depth study. Final cabin, city, weather and hull skins remain greybox. No camera-facing billboards."}
(HERE/"forest-manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
if args.preview:
    for frame in (144,180,408,432,456,816):
        scene.frame_set(frame)
        scene.camera=max((m for m in scene.timeline_markers if m.camera and m.frame<=frame),key=lambda m:m.frame).camera
        scene.render.filepath=str(BUILD/"frames"/f"frame_{frame:04d}.png")
        bpy.ops.render.render(write_still=True)
        print("FOREST_PREVIEW",frame,flush=True)
print("FOREST_READY",near_count,far_count,flush=True)
