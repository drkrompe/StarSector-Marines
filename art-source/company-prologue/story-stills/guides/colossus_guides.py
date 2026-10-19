"""Ships for the prologue pages: the cinematic Colossus or Hermes, alone on a transparent film.

    blender --background --factory-startup --python-exit-code 1 --python colossus_guides.py --
            --blend <colossus.blend|hermes.blend> --out DIR [--views p2,p3 | p2-hermes] [--samples 32] [--percent 50]

P2 and P3 show the Claim's company freighter. ImageGen warps a ship it draws from a reference
still (owner, 2026-10-19), so each page gets the real model rendered at that page's angle, to be
composited into the page's guide where the freighter stands. Cycles on the processor (the GPU is
shared), transparent background, one key light. Output: <out>/colossus-<view>.png.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

# view: (azimuth from +Y toward +X, elevation, distance, lens mm), hull units; target the hull centre.
# Each view names its ship: (collection, target in hull units).
SHIPS = {"colossus": ("COLOSSUS", (-17.0, 1.5, -6.0)), "hermes": ("HERMES", (0.0, 0.0, 0.0))}
VIEWS = {
    "p3": ("colossus", -50.0, 22.0, 520.0, 50.0),   # leaving: seen from behind and above, drives toward camera
    "p2": ("colossus", 18.0, 4.0, 470.0, 50.0),     # docked: broadside, seen level from the gallery
    # P2 after the owner swapped the freighter for Hermes boats (2026-10-19: "not enough room"):
    # one boat on its pad, port bow quarter, a little above, gear down and port door open.
    "p2-hermes": ("hermes", 35.0, 12.0, 190.0, 50.0),
}


def args():
    a = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    o = {"views": "p2,p3", "samples": "32", "percent": "50"}
    for i in range(0, len(a), 2):
        o[a[i].lstrip("-")] = a[i + 1]
    return o


def main(o):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    ship = VIEWS[o["views"].split(",")[0]][0]
    collection, target = SHIPS[ship]
    with bpy.data.libraries.load(o["blend"], link=False) as (src, dst):
        dst.collections = [collection]
    scene.collection.children.link(dst.collections[0])
    control = bpy.data.objects.get("Hermes control")
    if control is not None:                      # gear down, port boarding door open
        for k, v in (("gear_up", 0.0), ("door_port", 1.0)):
            if k in control.keys():
                control[k] = v
        control.update_tag()
        scene.frame_set(scene.frame_current)
    world = bpy.data.worlds.new("dark")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (.02, .02, .025, 1)
    scene.world = world
    sun = bpy.data.objects.new("key", bpy.data.lights.new("key", "SUN"))
    sun.data.energy = 4.0
    sun.rotation_euler = Vector((-0.3, 0.6, 0.75)).to_track_quat("Z", "Y").to_euler()
    scene.collection.objects.link(sun)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.clip_end = 5000
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = int(o["samples"])
    scene.cycles.use_denoising = True
    scene.render.film_transparent = True
    scene.render.resolution_x, scene.render.resolution_y = 1536, 1024
    scene.render.resolution_percentage = int(o["percent"])
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.view_settings.view_transform = "AgX"
    out = Path(o["out"])
    out.mkdir(parents=True, exist_ok=True)
    t = Vector(target)
    for v in o["views"].split(","):
        _, az, el, dist, lens = VIEWS[v]
        a, e = math.radians(az), math.radians(el)
        cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
        cam.rotation_euler = (t - cam.location).to_track_quat("-Z", "Y").to_euler()
        cam.data.lens = lens
        scene.render.filepath = str((out / ("colossus-%s.png" % v)).resolve())
        bpy.ops.render.render(write_still=True)
        print("GUIDE", v, flush=True)


main(args())
