"""P2 (THE WRITING ON THE WALL) as a blocked-out scene: the guide the page is repainted over.

    blender --background --factory-startup --python-exit-code 1 --python p2_scene.py --
            --hermes <hermes.blend> --out <png> [--samples 32] [--percent 50]

The owner found pasted boats lopsided and off their pads (2026-10-19), so the whole room is
modelled in metres: the habitat's departure gallery, its wide window onto a launch bay cut in
the rock, two real Hermes (hermes-cinematic) standing on their pads with gear down and the port
door open, a guarded gate with a lit gangway on the right, and grey stand-ins for the people:
the company manager reassuring the miners, the miners behind a tape barrier with Castle at the
front, two guards, and management's family going through the gate with luggage. ImageGen paints
the people from the stand-ins, keeping their size and place. Cycles on the processor.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector


def args():
    a = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    o = {"samples": "32", "percent": "50"}
    for i in range(0, len(a), 2):
        o[a[i].lstrip("-")] = a[i + 1]
    return o


def mat(name, rgb, emit=0.0, rough=.7):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*rgb, 1)
    b.inputs["Roughness"].default_value = rough
    if emit:
        b.inputs["Emission Color"].default_value = (*rgb, 1)
        b.inputs["Emission Strength"].default_value = emit
    return m


def box(name, lo, hi, m):
    lo, hi = Vector(lo), Vector(hi)
    bpy.ops.mesh.primitive_cube_add(size=1, location=(lo + hi) / 2)
    ob = bpy.context.object
    ob.name = name
    ob.scale = hi - lo
    ob.data.materials.append(m)
    return ob


def cyl(name, a, b, r, m, verts=12):
    a, b = Vector(a), Vector(b)
    d = b - a
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=max(d.length, 1e-3), location=(a + b) / 2)
    ob = bpy.context.object
    ob.name = name
    ob.rotation_euler = d.to_track_quat("Z", "Y").to_euler()
    ob.data.materials.append(m)
    return ob


def ball(name, c, r, m):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=r, location=c)
    ob = bpy.context.object
    ob.name = name
    ob.data.materials.append(m)
    return ob


def person(name, at, facing_deg, m, height=1.75, arms="down"):
    """A stand-in in metres: +X its facing in its own frame, rotated to facing_deg about Z."""
    k = height / 1.75
    yaw = math.radians(facing_deg)
    f, l = Vector((math.cos(yaw), math.sin(yaw), 0)), Vector((-math.sin(yaw), math.cos(yaw), 0))
    base = Vector(at)

    def p(x, y, z):
        return base + f * (x * k) + l * (y * k) + Vector((0, 0, z * k))
    for s in (1, -1):
        cyl(name + " leg", p(0, .1 * s, .9), p(0, .11 * s, .05), .075 * k, m)
    cyl(name + " torso", p(0, 0, .95), p(0, 0, 1.5), .17 * k, m)
    ball(name + " head", p(.01, 0, 1.64), .11 * k, m)
    ball(name + " nose", p(.11, 0, 1.64), .025 * k, mat("nose", (.04, .04, .04)))
    for s in (1, -1):
        sh = p(0, .2 * s, 1.45)
        if arms == "open":            # palms raised outward, reassuring
            el, ha = p(.22, .34 * s, 1.25), p(.38, .36 * s, 1.5)
        elif arms == "folded":
            el, ha = p(.12, .2 * s, 1.15), p(.2, -.08 * s, 1.24)
        elif arms == "luggage":
            el, ha = p(-.02, .26 * s, 1.15), p(-.05, .27 * s, .85)
        else:
            el, ha = p(.02, .24 * s, 1.15), p(.03, .25 * s, .85)
        cyl(name + " arm", sh, el, .05 * k, m)
        cyl(name + " forearm", el, ha, .045 * k, m)
        ball(name + " hand", ha, .05 * k, m)


def main(o):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    steel = mat("gallery steel", (.12, .12, .13), rough=.5)
    floor = mat("gallery floor", (.22, .2, .17), rough=.6)
    rock = mat("bay rock", (.12, .11, .1), rough=.95)
    pad = mat("pad", (.2, .2, .21), rough=.6)
    stripe = mat("pad stripe", (.75, .55, .1))
    glow = mat("panel glow", (1.0, .78, .45), emit=4.0)
    crowd = mat("miners", (.3, .25, .2))
    castle = mat("castle", (.38, .4, .33))
    suit = mat("management", (.55, .55, .58))
    guard = mat("guards", (.22, .25, .3))
    luggage = mat("luggage", (.6, .6, .62), rough=.3)
    tape = mat("tape", (.85, .7, .1))

    # Gallery: floor, back wall (y = 0) with the window (x -11..3, z 1..7) and the gate (x 4.5..7.5).
    box("gallery floor", (-16, -20, -.1), (14, 0, 0), floor)
    for lo, hi in [((-16, 0, 0), (-11, .6, 9)), ((3, 0, 0), (4.5, .6, 9)), ((7.5, 0, 0), (14, .6, 9)),
                   ((-11, 0, 0), (3, .6, 1)), ((-11, 0, 7), (3, .6, 9)), ((4.5, 0, 3.2), (7.5, .6, 9))]:
        box("gallery wall", lo, hi, steel)
    for x in (-6.3, -1.6):                              # mullions
        box("mullion", (x - .25, -.1, 1), (x + .25, .7, 7), steel)
    box("ceiling", (-16, -20, 9), (14, .6, 9.4), steel)
    box("side wall", (13.6, -20, 0), (14, 0, 9), steel)
    box("departures board", (4.7, -.05, 3.7), (7.3, 0, 4.7), glow)
    # Gate: a lit gangway running back from the right of the window toward the bay.
    box("gangway floor", (4.5, .6, 0), (7.5, 14, .1), floor)
    box("gangway roof", (4.5, .6, 3.2), (7.5, 14, 3.4), steel)
    box("gangway wall", (7.5, .6, 0), (7.7, 14, 3.4), steel)
    box("gangway glow", (5.0, 3, 3.1), (7.0, 12, 3.15), glow)
    for x in (4.1, 7.9):
        box("gate post", (x - .2, -.6, 0), (x + .2, 0, 3.4), steel)

    # Launch bay beyond the glass: rock walls, two pads, the boats.
    box("bay floor", (-30, .6, -.1), (30, 60, 0), mat("bay floor", (.16, .16, .17), rough=.4))
    box("bay back", (-30, 58, 0), (30, 60, 25), rock)
    box("bay left", (-30, .6, 0), (-28, 60, 25), rock)
    box("bay roof", (-30, .6, 22), (30, 60, 25), rock)
    hermes_at = [((-6.5, 9.5, 0), -30.0), ((0.5, 17, 0), 200.0)]
    for i, (at, hdg) in enumerate(hermes_at):
        cyl("pad %d" % i, (at[0], at[1], 0), (at[0], at[1], .03), 7.0, pad, 48)
        cyl("pad ring %d" % i, (at[0], at[1], .03), (at[0], at[1], .05), 6.2, stripe, 48)
        cyl("pad inner %d" % i, (at[0], at[1], .05), (at[0], at[1], .06), 5.9, pad, 48)
    for x, y in ((-14, 22), (6, 26), (-2, 6)):          # flood masts round the pads
        cyl("mast", (x, y, 0), (x, y, 12), .2, steel)
        bpy.ops.object.light_add(type="SPOT", location=(x, y - .8, 12.6))   # clear of the mast
        lamp = bpy.context.object
        lamp.data.energy, lamp.data.spot_size = 250000, math.radians(80)
        lamp.data.color = (1.0, .9, .75)
        lamp.rotation_euler = (Vector((-3, 13, 0)) - lamp.location).to_track_quat("-Z", "Y").to_euler()

    hb = o["hermes"]
    for i, (at, hdg) in enumerate(hermes_at):
        with bpy.data.libraries.load(hb, link=False) as (src, dst):
            dst.collections = ["HERMES"]
        col = dst.collections[0]
        sc.collection.children.link(col)
        root = next(ob for ob in col.all_objects if ob.name.startswith("HERMES | root"))
        ctl = next((ob for ob in col.all_objects if ob.name.startswith("Hermes control")), None)
        if ctl is not None:
            ctl["gear_up"], ctl["door_port"] = 0.0, 1.0 if i == 0 else 0.0
            ctl.update_tag()
        mpu = root.get("metres_per_unit", .11)
        root.scale = (mpu,) * 3
        root.rotation_euler = (0, 0, math.radians(hdg))
        root.location = at
        bpy.context.view_layer.update()
        soles = [ob.matrix_world.translation.z for ob in col.all_objects if "sole" in ob.name.lower()]
        if soles:                                       # stand the gear on the pad
            root.location.z += .06 - min(soles)
        bpy.context.view_layer.update()

    # People. Camera stands at (-2, -13, 1.65) looking at the window.
    person("manager", (.2, -8.0, 0), 205, suit, 1.8, "open")
    for j, (x, y) in enumerate([(-5.2, -9.4), (-4.4, -8.2), (-6.2, -7.6), (-4.0, -6.8), (-5.4, -6.2),
                                (-6.8, -5.6), (-3.8, -5.4), (-7.2, -8.6), (-7.6, -6.6)]):
        person("miner %d" % j, (x, y, 0), -10 + (j % 3) * 8, crowd, 1.65 + .05 * (j % 3))
    person("Castle", (-3.6, -10.4, 0), 25, castle, 1.66, "folded")
    for y in (-11.0, -8.6, -6.2, -3.8):                 # stanchions with tape between
        cyl("stanchion", (-2.8, y, 0), (-2.8, y, 1.0), .04, steel)
    cyl("tape", (-2.8, -11.0, .95), (-2.8, -3.8, .95), .03, tape)
    person("guard L", (3.6, -1.4, 0), -80, guard, 1.82)
    person("guard R", (8.4, -1.4, 0), -100, guard, 1.8)
    person("admin", (5.6, -.4, 0), 90, suit, 1.78, "luggage")
    person("admin wife", (6.5, -1.2, 0), 90, suit, 1.68, "luggage")
    person("admin child", (6.1, -2.2, 0), 90, suit, 1.15)
    box("suitcase", (5.3, -.7, 0), (5.8, -.4, .75), luggage)
    box("suitcase", (6.8, -1.6, 0), (7.3, -1.3, .7), luggage)
    box("trolley", (2.0, -4.2, 0), (3.4, -3.2, 1.1), luggage)

    # Interior light: warm lamps under the ceiling, brighter at the gate.
    for x, y, e in ((-5, -8, 500), (1, -8, 400), (6, -2, 900)):
        bpy.ops.object.light_add(type="AREA", location=(x, y, 8.6))
        lamp = bpy.context.object
        lamp.data.energy, lamp.data.size, lamp.data.color = e, 3.0, (1.0, .78, .55)
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (.01, .01, .012, 1)
    sc.world = world

    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cam.location = (-2.0, -13.0, 1.65)
    cam.rotation_euler = (Vector((-1.2, 0, 3.0)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    cam.data.lens, cam.data.clip_end = 20, 500
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = int(o["samples"])
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = 1536, 1024
    sc.render.resolution_percentage = int(o["percent"])
    sc.render.image_settings.file_format = "PNG"
    sc.view_settings.view_transform = "AgX"
    sc.render.filepath = str(Path(o["out"]).resolve())
    bpy.ops.render.render(write_still=True)
    print("GUIDE p2-scene", flush=True)


main(args())
