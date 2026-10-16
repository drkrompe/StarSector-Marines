"""Build the shared, editable Jangala greybox in background Blender.
No textures, generated imagery, live-session edits, or runtime game integration.
Run: blender --background --factory-startup --python build_animatic.py -- --preview
Then: blender --background jangala-blockout.blend --python render_frames.py -- --all
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Vector, Matrix

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from yard import build_yard
from terrain import build_terrain

SCALE = 1.5
FPS = 12
FRAMES = 780
FINAL_Z = 21.0
SOURCE = HERE.parent.parent / "second-wind-geometry" / "second-wind-geometry.blend"
BUILD = HERE / "build"
BUILD.mkdir(exist_ok=True)
(BUILD / "frames").mkdir(exist_ok=True)
bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene

def material(name, rgb):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*rgb, 1)
    mat.use_nodes = True
    p = mat.node_tree.nodes.get("Principled BSDF")
    p.inputs["Base Color"].default_value = (*rgb, 1)
    p.inputs["Roughness"].default_value = .82
    return mat

def box(name, loc, dims, mat, bevel=0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = dims
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(mat)
    if bevel:
        mod = obj.modifiers.new("Blockout edge", "BEVEL")
        mod.width = bevel
        mod.segments = 1
    return obj

def cyl(name, loc, radius, depth, mat):
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=radius, depth=depth, location=loc)
    obj = bpy.context.object
    obj.name = name
    obj.data.materials.append(mat)
    return obj

def empty(name):
    ob = bpy.data.objects.new(name, None)
    bpy.context.collection.objects.link(ob)
    return ob

MATS = {
    "hull": material("BLOCKOUT hull", (.40,.43,.45)),
    "plate": material("BLOCKOUT armor layers", (.58,.60,.60)),
    "dark": material("BLOCKOUT recess", (.09,.12,.14)),
    "frame": material("BLOCKOUT cabin framing", (.22,.25,.27)),
    "seat": material("BLOCKOUT passenger and baggage", (.32,.33,.30)),
    "lift": material("PROPOSAL ventral lift", (.21,.49,.55)),
    "support": material("PROPOSAL keel shoes", (.57,.48,.28)),
    "cloud": material("BLOCKOUT cloud bank", (.69,.74,.75)),
    "entry": material("ENTRY glow placeholder", (.82,.39,.12)),
}
helpers = dict(box=box, cyl=cyl, material=material, materials=MATS)
terrain = build_terrain(bpy, helpers)
yard = build_yard(bpy, helpers)
# Keep the low review camera's sightline open across the apron. Service sheds
# remain present, farther behind that camera, rather than hiding cradle contact.
for ob in yard["objects"]:
    if ob.name.startswith("YARD_shed") and ob.location.y > 0:
        ob.location.y += 120

# Extend the port district alongside the route; the forward cradle is not faked
# into a purely lateral window. All structures stay outside the approach lane.
industry = material("BLOCKOUT port frontage", (.40,.44,.45))
roof = material("BLOCKOUT port roof", (.29,.33,.35))
for row in range(3):
    for i in range(9):
        x = -1600 + i*155
        y = 180 + row*90
        h = 16 + ((i*7+row*11)%22)
        box("Port district shed", (x,y,h/2), (88,54,h), industry)
        box("Port district roof", (x,y,h+.8), (92,58,1.6), roof)
box("Port district freight road", (-950,132,.15), (1600,22,.25), roof)
for x in (-1250,-900,-570):
    box("Port communications tower", (x,285,39), (8,8,78), industry)
    box("Port tower crossarm", (x,285,65), (30,4,4), roof)
for x in (-1440,-1130,-820,-500):
    box("Freight vehicle 12m", (x,132,2), (12,3.5,4), MATS["support"])

root = empty("SECOND WIND | flight root")
root.scale = (SCALE,)*3
with bpy.data.libraries.load(str(SOURCE), link=False) as (data_from, data_to):
    data_to.objects = list(data_from.objects)
ship = []
for obj in data_to.objects:
    if obj is None:
        continue
    if obj.type != "MESH":
        bpy.data.objects.remove(obj, do_unlink=True)
        continue
    bpy.context.collection.objects.link(obj)
    obj.parent = root
    ship.append(obj)
    original = obj.data.materials[0].name.lower() if obj.data.materials else ""
    shade = MATS["dark"] if ("recess" in original or "engine" in original) else MATS["plate"] if ("cream" in original or "pod" in original) else MATS["hull"]
    obj.data.materials.clear()
    obj.data.materials.append(shade)

hull = next(o for o in ship if o.name.startswith("Main hull"))
bpy.context.view_layer.update()
hit, point, normal, _ = hull.evaluated_get(bpy.context.evaluated_depsgraph_get()).ray_cast(Vector((49,100,3.1)),Vector((0,-1,0)))
if not hit:
    raise RuntimeError("Window ray missed the real V3 shell")
normal = Vector((normal.x,normal.y,0)).normalized()
if normal.y < 0:
    normal = -normal
tangent = Vector((normal.y,-normal.x,0))
basis = Matrix((tangent, normal, Vector((0,0,1)))).transposed()
window = point + normal*.05
window.z = 3.1

def cabin_box(name, uvw, dims, mat, bevel=0):
    pos = window + basis @ Vector(uvw)
    ob = box(name, pos, dims, mat, bevel)
    ob.rotation_euler = basis.to_euler()
    ob.parent = root
    return ob

cutter = cabin_box("TEMP viewport cutter",(0,0,0),(1.2,6,.733333),MATS["dark"])
bpy.context.view_layer.objects.active = hull
mod = hull.modifiers.new("Proposed pressure-safe crew viewport", "BOOLEAN")
mod.operation = "DIFFERENCE"
mod.solver = "EXACT"
mod.object = cutter
bpy.ops.object.modifier_apply(modifier=mod.name)
bpy.data.objects.remove(cutter, do_unlink=True)
for x in (-.69,.69):
    cabin_box("Viewport vertical frame",(x,.02,0),(.18,.30,1.08),MATS["frame"],.035)
for z in (-.455,.455):
    cabin_box("Viewport horizontal frame",(0,.02,z),(1.56,.30,.18),MATS["frame"],.035)
# Glazing is a named wire placeholder in the editable file; rendering leaves the
# aperture clear for sightline review, not a claim that the pressure space is open.
glazing = cabin_box("SEALED GLAZING | clear proxy",(0,.05,0),(1.19,.025,.723),MATS["cloud"])
glazing.display_type = "WIRE"
glazing.hide_render = True
cabin_box("Cabin floor",(0,-1.8,-1.13),(3.3,3.6,.15),MATS["frame"])
cabin_box("Cabin ceiling",(0,-1.8,1.5),(3.3,3.6,.15),MATS["frame"])
for x in (-1.65,1.65):
    cabin_box("Cabin side lining",(x,-1.9,.1),(.1,3.8,2.6),MATS["frame"])
cabin_box("Window lower wall",(0,0,-.83),(3.3,.16,.57),MATS["frame"])
cabin_box("Passenger seat back",(-1.0,-2.6,-.15),(.72,.15,1.6),MATS["seat"],.08)
cabin_box("Passenger seat cushion",(-1.0,-2.25,-.82),(.72,.75,.2),MATS["seat"],.05)
cabin_box("Crew baggage",(-1.05,-.85,-.78),(.5,.45,.55),MATS["seat"],.06)
# Anonymous seated passenger mass, kept at the edge of the view.
cabin_box("Crew sleeve proxy",(-.48,-.75,-.37),(.20,.8,.20),MATS["seat"],.08)
cabin_box("Crew hand proxy",(-.48,-.35,-.31),(.16,.23,.12),MATS["plate"],.04)

# Review-only support hardware. Existing four aft main engines never move.
shoes = []
for x in (-21,34):
    for y in (-10,10):
        ob = box("PROPOSAL keel shoe", (x,y,-10-1/(2*SCALE)), (4,4,1/SCALE), MATS["support"], .08)
        ob.parent = root
        shoes.append(ob)
lifts, plumes = [], []
for x in (33,-17):
    for y in ((18,-18) if x==33 else (19,-19)):
        ob = cyl("PROPOSAL ventral lift collar",(x,y,-10.4),1.6,1.1,MATS["lift"])
        ob.parent = root
        lifts.append(ob)
        nozzle = cyl("PROPOSAL lift mouth",(x,y,-11.0),1.15,.15,MATS["dark"])
        nozzle.parent = root
        # Geometric cone makes thrust direction legible, not finished exhaust FX.
        bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=2.4, radius2=.9, depth=5, location=(x,y,-13.5))
        plume = bpy.context.object
        plume.name = "LIFT VECTOR | placeholder"
        plume.data.materials.append(MATS["lift"])
        plume.parent = root
        plumes.append(plume)

# Scale study boat outside the flight volume, never a repaired company Hermes.
boatmat = material("SCALE ONLY shuttle proxy",(.48,.52,.56))
box("SCALE STUDY ONLY | six-seat boat 12m", (85,-93,3),(10,7,4),boatmat,.7)
box("SCALE STUDY ONLY | boat nose",(90.5,-93,2.7),(3,5,3.4),boatmat,.8)

# Dense sampled keys preserve specified motion without Bezier overshoot.
def smooth(u):
    return u*u*(3-2*u)

def motion(frame):
    if frame <= 72:
        u=(frame-1)/71
        return (-15000+1100*u,0,2200-150*u)
    if frame <= 216:
        u=(frame-73)/143
        return (-13900+3900*u,0,2050-500*u)
    if frame <= 360:
        u=(frame-217)/143
        return (-5200+1900*u,0,380-80*u)
    if frame <= 504:
        u=(frame-361)/143
        return (-1600+1230*u,0,130-35*u)
    if frame <= 624:
        u=(frame-505)/119
        ease=1-(1-u)**2
        return (-245*(1-ease),0,48-16*ease)
    if frame <= 648:
        return (0,0,32)
    if frame <= 744:
        u=(frame-648)/96
        return (0,0,32-(32-FINAL_Z)*smooth(u))
    return (0,0,FINAL_Z)

for f in range(1,FRAMES+1):
    root.location=motion(f)
    root.keyframe_insert(data_path="location",frame=f)
    lift_strength = 1 if f<=744 else max(0,1-(f-744)/24)
    for plume in plumes:
        plume.scale=(lift_strength,lift_strength,lift_strength)
        plume.keyframe_insert(data_path="scale",frame=f)
for clamp, target in zip(yard["clamps"],yard["clamp_engaged_locations"]):
    start=clamp.location.copy()
    # Wedge meets the 6m-wide keel shoe at Y=+/-18, never penetrates it.
    target=Vector((target[0],20 if target[1]>0 else -20,target[2]))
    for f,loc in ((1,start),(750,start),(774,target),(780,target)):
        clamp.location=loc
        clamp.keyframe_insert(data_path="location",frame=f)

# A few cloud-bank masses cross the window early. They are named proxies.
cloudroot=empty("ENTRY CLOUD | illustrative clearing layer")
cloudroot.parent=root
cloudroot.location=window+normal*220
for i in range(5):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=12,ring_count=8,location=tuple(cloudroot.location+tangent*(i-2)*115))
    ob=bpy.context.object
    ob.name="Cloud breakup proxy"
    ob.scale=(100,70,100)
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    ob.data.materials.append(MATS["cloud"])
    ob.rotation_euler=basis.to_euler()
    ob.parent=root
    for f, shift in ((1,Vector((0,0,0))),(55,normal*80),(120,normal*150+tangent*800)):
        ob.location=cloudroot.location+tangent*(i-2)*115+shift
        ob.keyframe_insert(data_path="location",frame=f)
    ob.hide_render=False
    ob.keyframe_insert(data_path="hide_render",frame=120)
    ob.hide_render=True
    ob.keyframe_insert(data_path="hide_render",frame=121)

def camera(name,loc,target,lens,parent=None):
    bpy.ops.object.camera_add(location=loc)
    cam=bpy.context.object
    cam.name=name
    cam.data.lens=lens
    cam.data.clip_start=.06
    cam.data.clip_end=60000
    cam.data.sensor_width=36
    cam.rotation_euler=(Vector(target)-Vector(loc)).to_track_quat("-Z","Y").to_euler()
    if parent:
        cam.parent=parent
    return cam

cams=[]
for name,start,end,d0,d1,down in [
    ("01 ENTRY / crew window",1,72,1.30,1.05,.20),
    ("02 REVEAL / near glass",73,216,1.05,.90,.35),
    ("03 LAND / side window",217,360,.95,.95,.22),
    ("04 PORT / side window",361,504,.95,.95,.20)]:
    eye_height=Vector((0,0,.18))
    cam=camera(name,window-normal*d0+eye_height,window+normal*40-Vector((0,0,40*down)),26,root)
    for f,d in ((start,d0),(end,d1)):
        cam.location=window-normal*d+eye_height
        cam.rotation_euler=(normal-Vector((0,0,down))).to_track_quat("-Z","Y").to_euler()
        cam.keyframe_insert(data_path="location",frame=f)
        cam.keyframe_insert(data_path="rotation_euler",frame=f)
    cams.append(cam)
cam5=camera("05 EXTERIOR / approach", (180,250,125),(-100,0,25),30)
for f,t in ((505,(-100,0,25)),(624,(5,0,22))):
    cam5.rotation_euler=(Vector(t)-cam5.location).to_track_quat("-Z","Y").to_euler()
    cam5.keyframe_insert(data_path="rotation_euler",frame=f)
cams.append(cam5)
cam6=camera("06 LANDING / support review",(20,190,4),(10,0,23),28)
cams.append(cam6)
for i,(cam,f) in enumerate(zip(cams,(1,73,217,361,505,625)),1):
    marker=scene.timeline_markers.new(cam.name,frame=f)
    marker.camera=cam
scene.camera=cams[0]

# Entry warm fill reduces before the reveal. This is lighting only; no texture.
bpy.ops.object.light_add(type="AREA")
entry=bpy.context.object
entry.name="ENTRY WARM FILL | proxy"
entry.parent=root
entry.location=window+normal*2
entry.rotation_euler=(-normal).to_track_quat("-Z","Y").to_euler()
entry.data.color=(1,.31,.08)
entry.data.shape="DISK"
entry.data.size=4
for f,e in ((1,700),(36,500),(72,0)):
    entry.data.energy=e
    entry.data.keyframe_insert(data_path="energy",frame=f)
bpy.ops.object.light_add(type="SUN", location=(0,0,1000))
sun=bpy.context.object
sun.name="Shared sun | all shots"
sun.rotation_euler=(math.radians(25),math.radians(-30),math.radians(-35))
sun.data.energy=2
sun.data.angle=.18
scene.world.use_nodes=True
scene.world.node_tree.nodes["Background"].inputs[0].default_value=(.45,.57,.66,1)
scene.world.node_tree.nodes["Background"].inputs[1].default_value=.65
scene.render.engine="BLENDER_EEVEE"
scene.eevee.taa_render_samples=16
scene.render.resolution_x=1920
scene.render.resolution_y=1080
scene.render.resolution_percentage=100
scene.render.image_settings.file_format="PNG"
scene.render.image_settings.color_mode="RGB"
scene.render.fps=FPS
scene.frame_start=1
scene.frame_end=FRAMES
scene.render.film_transparent=False
scene.view_settings.view_transform="AgX"
scene.render.use_file_extension=True
# Use linear interpolation across adjacent sampled keys, avoiding hidden changes
# between scene cuts. Blender5 actions expose layered channelbags.
for action in bpy.data.actions:
    for layer in action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for fc in bag.fcurves:
                    for key in fc.keyframe_points:
                        key.interpolation="LINEAR"

scene.frame_set(570)
scene.camera=cam5
# Save a readable opening viewport in the editable .blend.
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=="VIEW_3D":
            area.spaces.active.region_3d.view_perspective="CAMERA"
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/"jangala-blockout.blend"))
manifest={
    "duration_seconds":65,"source_fps":FPS,"frames":FRAMES,
    "ship_scale_m_per_hull_unit":SCALE,"scale_status":"provisional review scale, not canonical metres",
    "source_ship":str(SOURCE.relative_to(HERE.parent.parent)),
    "ship_objects":len(ship),"axes":{"+X":"bow and approach","+Y":"port","+Z":"up"},
    "viewport_local":list(window),"viewport_normal_local":list(normal),
    "viewport_dimensions_m":[1.8,1.1],"glazing":"clear wire proxy omitted from render",
    "proposed_landing_hardware":"four ventral lift modules, four 1m keel shoes; four aft engines unchanged",
    "landing":{"fore_aft_stopped_frame":624,"descent_start_frame":649,"contact_frame":744,
               "clamp_start_frame":751,"clamp_end_frame":774,"lift_off_frame":768,
               "final_root_z":FINAL_Z,"shoe_bottom_z":5,"pad_top_z":5},
    "terrain":terrain,"yard":{"contacts":yard["contact_points"],"exhaust":yard["exhaust_points"]},
    "editorial_cuts":[1,73,217,361,505,625],"time_ellipses":[217,361,505],
    "textures":False,"audio":False}
(HERE/"scene-manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
args=argparse.ArgumentParser()
args.add_argument("--preview",action="store_true")
args.add_argument("--all",action="store_true")
opt=args.parse_args(sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else [])
if opt.preview or opt.all:
    frames=range(1,FRAMES+1) if opt.all else [36,144,288,432,570,648,720,744,780]
    for f in frames:
        scene.frame_set(f)
        # Explicitly resolve marker camera for background rendering.
        index=max(i for i,begin in enumerate((1,73,217,361,505,625)) if f>=begin)
        scene.camera=cams[index]
        scene.render.filepath=str(BUILD/"frames"/("frame_%04d.png"%f))
        bpy.ops.render.render(write_still=True)
        print("ANIMATIC_FRAME",f,flush=True)
print("ANIMATIC_READY",HERE,flush=True)
