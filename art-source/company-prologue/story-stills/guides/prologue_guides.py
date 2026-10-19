"""Guide renders for the prologue pages: the real Second Wind and its bridge, with nobody in them.

    blender --background --factory-startup --python-exit-code 1 --python prologue_guides.py --
            [--blend PATH] [--views p1,p6,...] [--samples 64] [--out DIR] [--percent 100]

Each view is one prologue page's camera on the patched state (`second-wind-states/patched`),
rendered 1536x1024 (the prologue's master canvas) in Cycles. The bridge views carry the room's
own practicals and screen pages from C2 (`c2_bridge_render`), so the light and the screens are
the cinematic's. No figures: the owner wants the people painted by ImageGen from their
portraits, not the MakeHuman figures (2026-10-19). The glazing is hidden from camera rays, as
in C2; what lies outside the slits is left to the repaint.

These are geometry and light authority for codex_imagegen (geometry-first-imagegen.md): the
repaint keeps the room, the ship and the camera, and paints people, props, materials and the
world outside. Output: build/prologue-guides/<view>.png (ignored), copied beside the prompts
when a page is generated.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parents[1]
ROOT = PROLOGUE.parents[1]
PATROL = PROLOGUE / "second-wind-cinematic" / "patrol-abeam"
sys.path.insert(0, str(PATROL))
sys.path.insert(0, str(PROLOGUE.parent))
import c2_bridge_render as CB  # noqa: E402

STATE = "patched"
SUN_STBD, SUN_PORT = (0.35, -0.55, 0.75), (0.25, 0.8, 0.45)   # direction the light comes from
DEFAULT_BLEND = PROLOGUE / "second-wind-cinematic" / "second-wind-states" / STATE / ("second-wind-%s.blend" % STATE)

# name: (interior?, camera position, target, lens mm), hull units (+X bow, +Y port, +Z dorsal).
# The bridge floor stands at about 16.6; a standing eye is about 19.2.
VIEWS = {
    # P1: the room from the aft hatch, forward; Castle's comms bank on the left, the helm dais ahead.
    "p1-bridge-from-hatch": (True, (9.7, -1.6, 19.35), (17.5, 1.2, 18.1), 16.0),
    # P6: across the room onto the port comms bank, where Castle writes; the slits above it.
    "p6-comms-bank": (True, (14.2, -1.0, 19.3), (11.6, 4.0, 18.55), 22.0),
    # P8: from the flight seat down onto the forward console's lip and screens.
    "p8-forward-console": (True, (16.05, -1.7, 19.25), (17.6, -0.4, 18.15), 24.0),
    # P4: stern starboard quarter, the two running drives (3, 4) lit; the port pod is capped.
    "p4-stern-quarter": (False, (-95.0, -92.0, 34.0), (4.0, 0.0, 2.0), 40.0),
    # P5: low on the port beam, onto the side bay the families board through.
    "p5-port-bay": (False, (-12.0, 62.0, -9.0), (22.0, 0.0, 4.0), 26.0),
}


def args():
    a = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    o = {"blend": str(DEFAULT_BLEND), "views": ",".join(VIEWS), "samples": "64",
         "out": str(ROOT / "build" / "prologue-guides"), "percent": "100"}
    for i in range(0, len(a), 2):
        o[a[i].lstrip("-")] = a[i + 1]
    return o


def setup(blend):
    scene = bpy.context.scene
    for ob in list(bpy.data.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    with bpy.data.libraries.load(blend, link=False) as (src, dst):
        dst.collections = ["SECOND WIND | %s" % STATE]
    scene.collection.children.link(dst.collections[0])
    sw = bpy.data.objects["SECOND WIND | %s | root" % STATE]
    if not any(ob.name.startswith("Bridge deck plating") for ob in sw.children_recursive):
        raise RuntimeError("%s has no bridge interior: rebuild the state" % blend)
    glazing = [ob for ob in sw.children_recursive if ob.name.startswith("Command deck viewport slits")]
    for ob in [sw, *sw.children_recursive]:
        ob.animation_data_clear()
    lamps = []
    for name, loc, (sx, sy), aim, energy, rgb, spread in CB.bridge_practicals(True):
        lamp = bpy.data.objects.new("PG | " + name, bpy.data.lights.new("PG | " + name, "AREA"))
        # The root is unscaled here (hull units), so a lamp's area is 1/0.65^2 of C2's: keep its
        # radiance by raising the energy to match.
        lamp.data.shape, lamp.data.size, lamp.data.size_y = "RECTANGLE", sx, sy
        lamp.data.energy = energy / (0.65 ** 2)
        lamp.data.color, lamp.data.spread = rgb, math.radians(spread)
        lamp.visible_camera = False
        scene.collection.objects.link(lamp)
        lamp.parent, lamp.location = sw, loc
        if aim is not None:
            lamp.rotation_euler = (Vector(aim) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
        lamps.append(lamp)
    pages = {}
    for name, (page, cu, cv, k) in CB.SCREEN_PAGES.items():
        if name not in bpy.data.objects:
            continue
        if page not in pages:
            img = bpy.data.images.load(str(CB.SCREEN_DIR / ("%s-v%d.png" % (page, CB.SCREEN_VERSION.get(page, 1)))))
            mat = bpy.data.materials.new("PG | screen %s" % page)
            mat.use_nodes = True
            bs = mat.node_tree.nodes["Principled BSDF"]
            bs.inputs["Base Color"].default_value = (.01, .015, .015, 1)
            bs.inputs["Roughness"].default_value = .25
            tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
            tex.image, tex.extension = img, "EXTEND"
            mat.node_tree.links.new(tex.outputs["Color"], bs.inputs["Emission Color"])
            bs.inputs["Emission Strength"].default_value = CB.SCREEN_STRENGTH
            pages[page] = (mat, img.size[0] / img.size[1])
        CB.screen_page(bpy.data.objects[name], *pages[page], cu, cv, k, CB.SCREEN_FACE.get(name, "operator"))

    # Space: black with faint stars seen by camera rays only, so the room is lit by its own lamps.
    world = bpy.data.worlds.new("PG | space")
    scene.world = world
    world.use_nodes = True
    nt = world.node_tree
    nt.nodes.clear()
    tc = nt.nodes.new("ShaderNodeTexCoord")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 900.0
    nt.links.new(tc.outputs["Generated"], noise.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position, ramp.color_ramp.elements[1].position = 0.78, 0.84
    nt.links.new(noise.outputs["Fac"], ramp.inputs[0])
    bg = nt.nodes.new("ShaderNodeBackground")
    bg.inputs["Strength"].default_value = 0.6
    nt.links.new(ramp.outputs["Color"], bg.inputs["Color"])
    lp = nt.nodes.new("ShaderNodeLightPath")
    black = nt.nodes.new("ShaderNodeBackground")
    black.inputs["Strength"].default_value = 0.0
    mix = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(lp.outputs["Is Camera Ray"], mix.inputs["Fac"])
    nt.links.new(black.outputs[0], mix.inputs[1])
    nt.links.new(bg.outputs[0], mix.inputs[2])
    wo = nt.nodes.new("ShaderNodeOutputWorld")
    nt.links.new(mix.outputs[0], wo.inputs["Surface"])

    sun = bpy.data.objects.new("PG | sun", bpy.data.lights.new("PG | sun", "SUN"))
    sun.data.energy, sun.data.color, sun.data.angle = 3.5, (1.0, 0.95, 0.88), math.radians(0.5)
    sun.rotation_euler = Vector((0.35, -0.55, 0.75)).to_track_quat("Z", "Y").to_euler()
    scene.collection.objects.link(sun)
    fill = bpy.data.objects.new("PG | fill", bpy.data.lights.new("PG | fill", "SUN"))
    fill.data.energy, fill.data.color, fill.data.angle = 0.35, (0.6, 0.7, 0.9), math.radians(40)
    fill.rotation_euler = Vector((-0.4, 0.6, -0.3)).to_track_quat("Z", "Y").to_euler()
    scene.collection.objects.link(fill)
    return scene, glazing, sun


def render(o):
    scene, glazing, sun = setup(o["blend"])
    cam = bpy.data.objects.new("PG | camera", bpy.data.cameras.new("PG | camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.sensor_width, cam.data.sensor_fit = 36.0, "HORIZONTAL"
    cam.data.clip_start, cam.data.clip_end = 0.05, 5000
    scene.render.engine = "CYCLES"
    scene.cycles.samples = int(o["samples"])
    scene.cycles.use_denoising = True
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "OPTIX"
    prefs.get_devices()
    gpus = [d for d in prefs.devices if d.type == "OPTIX"]
    for d in prefs.devices:
        d.use = d.type == "OPTIX"
    scene.cycles.device = "GPU" if gpus else "CPU"
    scene.render.resolution_x, scene.render.resolution_y = 1536, 1024
    scene.render.resolution_percentage = int(o["percent"])
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.view_settings.view_transform = "AgX"
    out = Path(o["out"])
    out.mkdir(parents=True, exist_ok=True)
    for name in o["views"].split(","):
        name = next(v for v in VIEWS if v.startswith(name))
        interior, pos, target, lens = VIEWS[name]
        for g in glazing:
            g.visible_camera = g.visible_shadow = not interior
        sun.data.energy = 0.6 if interior else 3.5
        # The port-side views want the light on the port flank (a lit dock), the rest from starboard.
        towards = SUN_PORT if name.startswith("p5") else SUN_STBD
        sun.rotation_euler = Vector(towards).to_track_quat("Z", "Y").to_euler()
        cam.location = pos
        cam.rotation_euler = (Vector(target) - Vector(pos)).to_track_quat("-Z", "Y").to_euler()
        cam.data.lens = lens
        scene.render.filepath = str(out / (name + ".png"))
        bpy.ops.render.render(write_still=True)
        print("GUIDE", name, flush=True)


render(args())
