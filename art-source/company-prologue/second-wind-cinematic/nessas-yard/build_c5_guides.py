"""Geometry-first guides for C5 NESSA'S YARD (c5-layout.md).

"C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" --background --factory-startup ../landing-wreck-pass/landing-wreck-pass.blend --python build_c5_guides.py [-- --samples 24 --scale 1.0 --test]

Opens ../landing-wreck-pass/landing-wreck-pass.blend read-only at its settled frame (774):
the damaged Second Wind standing in its cradle, the cradle's four contact pads and rails, and
the port-refinement district behind. Adds what C5 needs and the landing never had: the
sheltered crew gantry with its hoist, the yard crew, Nessa, near-ground props, a far tree line
and steam blockers. Everything is clay, coloured by layer, and each depth layer is rendered
alone on a transparent film at plate resolution (3840x2160) from the one C5 camera.

Writes guides/c5-<layer>-guide.png, guides/c5-clay-all.png and guides/c5-camera.json.
The source blend is never saved.
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

BASE = Path(__file__).resolve().parent
SRC = BASE.parent / "landing-wreck-pass" / "landing-wreck-pass.blend"
SETTLED = 774

# Camera: starboard stern quarter, eye height of someone on the yard's perimeter walkway.
# Sun is astern and 51 degrees up (the shared sun), so this angle is front-lit from the left
# and rakes along the starboard flank, where the deep craters are.
CAM_POS = (-128.0, -178.0, 7.0)
CAM_AIM = (10.0, 0.0, 17.0)
LENS_MM = 40.0
PUSH = (1.00, 1.08)
PUSH_CENTRE = (1000, 600)   # output px: just aft of midships, between the hull and Nessa

GANTRY = (50.0, -66.0)      # sheltered crew gantry, near side of the bow pad
NESSA = (6.0, -40.0)
CREW_START = [(44.0, -61.0), (47.0, -64.5), (41.0, -64.0), (45.0, -68.0), (39.5, -60.0)]
WALK_DIR = None             # set from the camera: parallel to the picture plane, toward Nessa

LAYERS = ["L1-sky", "L2-yard", "L3-ship", "L3e-crew", "L4-fore"]
CLAY = {
    "ship": (0.62, 0.42, 0.32), "cradle": (0.36, 0.44, 0.56), "district": (0.66, 0.66, 0.64),
    "ground": (0.48, 0.48, 0.47), "ground_far": (0.50, 0.50, 0.48), "trees": (0.16, 0.28, 0.14),
    "gantry": (0.80, 0.62, 0.20), "crew": (0.95, 0.95, 0.90), "nessa": (0.15, 0.55, 0.85),
    "steam": (0.95, 0.95, 0.95), "fore": (0.30, 0.30, 0.32),
}


def opt(name, default):
    a = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if name in a:
        i = a.index(name)
        return a[i + 1] if i + 1 < len(a) and not a[i + 1].startswith("--") else True
    return default


def clay(name):
    m = bpy.data.materials.get("c5 " + name) or bpy.data.materials.new("c5 " + name)
    m.use_nodes = True
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs["Base Color"].default_value = (*CLAY[name], 1)
    b.inputs["Roughness"].default_value = 0.8
    if name == "steam":
        b.inputs["Alpha"].default_value = 0.45
    return m


def set_clay(ob, name):
    if ob.type != "MESH":
        return
    ob.data = ob.data.copy()
    ob.data.materials.clear()
    ob.data.materials.append(clay(name))


def coll(name):
    c = bpy.data.collections.get("C5 " + name) or bpy.data.collections.new("C5 " + name)
    if c.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(c)
    return c


def move(ob, layer):
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    coll(layer).objects.link(ob)


def add_box(layer, mat, centre, size, name):
    bpy.ops.mesh.primitive_cube_add(size=1, location=centre)
    ob = bpy.context.active_object
    ob.name, ob.scale = "C5 " + name, size
    set_clay(ob, mat)
    move(ob, layer)
    return ob


def add_person(layer, mat, x, y, name, h=1.8):
    bpy.ops.mesh.primitive_cylinder_add(radius=0.28, depth=h - 0.5, location=(x, y, (h - 0.5) / 2))
    body = bpy.context.active_object
    body.name = "C5 " + name
    set_clay(body, mat)
    move(body, layer)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.15, location=(x, y, h - 0.17))
    head = bpy.context.active_object
    head.name = "C5 " + name + " head"
    set_clay(head, mat)
    move(head, layer)


def descendants(o):
    out = [o]
    for c in o.children:
        out += descendants(c)
    return out


def classify():
    sc = bpy.context.scene
    root = bpy.data.objects["SECOND WIND | flight root"]
    ship = set(o.name for o in descendants(root))
    for ob in list(sc.objects):
        n = ob.name
        if ob.type in ("CAMERA", "LIGHT") and n != "Shared sun | all shots":
            ob.hide_render = True
            continue
        if n in ship:
            if ob.type == "MESH":
                set_clay(ob, "ship")
            move(ob, "L3-ship")
        elif n.startswith(("YARD_", "Empty maintenance cradle")) or (
                n.startswith("LANDING YARD |") and not any(k in n for k in ("far", "precinct", "fixed port"))):
            set_clay(ob, "cradle")
            move(ob, "L3-ship")
        elif n.startswith("DISTRICT"):
            set_clay(ob, "district")
            move(ob, "L2-yard")
        elif n == "Shared sun | all shots":
            pass
        else:
            ob.hide_render = True
            ob.hide_viewport = True


def build_additions():
    cam = Vector(CAM_POS)
    fwd = (Vector(CAM_AIM) - cam)
    fwd.z = 0
    fwd.normalize()
    right = Vector((fwd.y, -fwd.x, 0.0))
    walk = -right                                    # parallel to the picture plane, screen left
    # ground split at a seam the district's front edge hides: apron with the ship, the rest behind
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, -180, 0))
    g = bpy.context.active_object
    g.name, g.scale = "C5 apron", (1400, 740, 1)
    g.location.y = -550 + 370
    set_clay(g, "ground")
    move(g, "L3-ship")
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 1400, 0))
    g2 = bpy.context.active_object
    g2.name, g2.scale = "C5 yard beyond seam", (4000, 2420, 1)
    g2.location.y = 190 + 1210
    set_clay(g2, "ground_far")
    move(g2, "L2-yard")
    # far tree line (L1, with the sky)
    import random
    rng = random.Random(9)
    for i in range(220):
        x, y = rng.uniform(-2500, 2500), rng.uniform(2300, 2900)
        r = rng.uniform(14, 30)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=(x, y, r * 0.3), segments=10, ring_count=6)
        t = bpy.context.active_object
        t.scale.z = rng.uniform(0.9, 1.4)
        set_clay(t, "trees")
        move(t, "L1-sky")
    # sheltered crew gantry: four legs, roof, back screen, stair; a jib hoist on its outboard end
    gx, gy = GANTRY
    for dx in (-7, 7):
        for dy in (-3.5, 3.5):
            add_box("L3-ship", "gantry", (gx + dx, gy + dy, 5), (0.6, 0.6, 10), "gantry leg")
    add_box("L3-ship", "gantry", (gx, gy, 10.3), (16, 8.5, 0.6), "gantry roof")
    add_box("L3-ship", "gantry", (gx, gy - 4, 3.5), (14, 0.3, 7), "gantry wind screen")
    add_box("L3-ship", "gantry", (gx - 9, gy, 5), (0.5, 0.5, 10), "hoist mast")
    add_box("L3-ship", "gantry", (gx - 13, gy, 10.6), (9, 0.5, 0.5), "hoist jib")
    add_box("L3-ship", "cradle", (gx - 16.5, gy, 4.0), (2.4, 2.4, 1.6), "hoist load (loop)")
    # yard crew in a loose file leaving the gantry, and Nessa, arms folded, near the aft pad
    for i, (x, y) in enumerate(CREW_START):
        add_person("L3e-crew", "crew", x, y, "crew %d" % i)
    add_person("L3e-crew", "nessa", *NESSA, "Nessa", h=1.72)
    # steam: soft blockers over the cradle vents and the stern (moving element, own sheets)
    for (x, y, z, r) in ((-30, -18, 6, 4), (50, -18, 6, 3.5), (-60, -4, 34, 5), (-63, -14, 24, 4)):
        bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=(x, y, z))
        s = bpy.context.active_object
        s.name = "C5 steam blocker"
        s.scale.z = 1.6
        set_clay(s, "steam")
        move(s, "L3s-steam")
    # foreground: perimeter barrier and service gear across the bottom of frame (hides the
    # apron's differential-scale seam under the push)
    base = cam + fwd * 52
    for k in range(-7, 8, 2):
        p = base + right * (k * 4.2)
        add_box("L4-fore", "fore", (p.x, p.y, 0.55), (4.0, 0.6, 0.9), "barrier")
    p = cam + fwd * 46 + right * 17
    add_box("L4-fore", "fore", (p.x, p.y, 1.0), (2.4, 2.4, 2.0), "cable reel")
    p = cam + fwd * 50 - right * 22
    add_box("L4-fore", "fore", (p.x, p.y, 1.3), (5.5, 2.4, 2.6), "service cart")
    return walk


def camera():
    sc = bpy.context.scene
    cd = bpy.data.cameras.new("C5 camera")
    cd.lens, cd.sensor_width, cd.sensor_fit = LENS_MM, 36, "HORIZONTAL"
    cd.clip_start, cd.clip_end = 0.5, 20000
    co = bpy.data.objects.new("C5 camera", cd)
    sc.collection.objects.link(co)
    co.location = CAM_POS
    co.rotation_euler = (Vector(CAM_AIM) - Vector(CAM_POS)).to_track_quat("-Z", "Y").to_euler()
    sc.camera = co
    return co


def world_sky():
    w = bpy.data.worlds.new("C5 sky")
    bpy.context.scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    nt.nodes.clear()
    tc, sep = nt.nodes.new("ShaderNodeTexCoord"), nt.nodes.new("ShaderNodeSeparateXYZ")
    ramp, bg, out = nt.nodes.new("ShaderNodeValToRGB"), nt.nodes.new("ShaderNodeBackground"), nt.nodes.new("ShaderNodeOutputWorld")
    nt.links.new(tc.outputs["Generated"], sep.inputs[0])
    nt.links.new(sep.outputs["Z"], ramp.inputs[0])
    nt.links.new(ramp.outputs[0], bg.inputs[0])
    nt.links.new(bg.outputs[0], out.inputs[0])
    ramp.color_ramp.elements[0].color = (0.80, 0.86, 0.92, 1)
    ramp.color_ramp.elements[1].position = 0.4
    ramp.color_ramp.elements[1].color = (0.36, 0.55, 0.82, 1)
    bg.inputs["Strength"].default_value = 0.8


def render(path, only=None, transparent=True, scale=1.0, samples=24):
    sc = bpy.context.scene
    for c in sc.collection.children:
        if c.name.startswith("C5 "):
            lay = c.name[3:]
            c.hide_render = only is not None and lay not in only
        elif only is not None:
            c.hide_render = True
    sc.render.film_transparent = transparent
    sc.render.resolution_x, sc.render.resolution_y = 3840, 2160
    sc.render.resolution_percentage = int(scale * 100)
    sc.cycles.samples = samples
    sc.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    print("wrote", Path(path).name)


def main():
    if Path(bpy.data.filepath).resolve() != SRC.resolve():
        raise SystemExit("open the source blend on the command line: blender -b %s --python build_c5_guides.py" % SRC)
    sc = bpy.context.scene
    sc.frame_set(SETTLED)
    classify()
    walk = build_additions()
    camera()
    world_sky()
    sc.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    try:
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = True
        sc.cycles.device = "GPU"
    except Exception as e:  # noqa: BLE001
        print("CPU render:", e)
    sc.cycles.use_denoising = True
    sc.view_settings.view_transform = "Standard"
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    scale = float(opt("--scale", 1.0))
    samples = int(opt("--samples", 24))
    g = BASE / "guides"
    g.mkdir(exist_ok=True)
    if opt("--test", False):
        render(g / "c5-test.png", None, False, 0.25, 8)
        return
    render(g / "c5-clay-all.png", None, False, scale, samples)
    for lay in ("L1-sky", "L2-yard", "L3-ship", "L3s-steam", "L3e-crew", "L4-fore"):
        render(g / ("c5-%s-guide.png" % lay), {lay}, lay != "L1-sky", scale, samples)
    # screen positions of the walkers and Nessa for compose_c5.py
    from bpy_extras.object_utils import world_to_camera_view
    co = sc.camera

    def scr(p):
        v = world_to_camera_view(sc, co, Vector(p))
        return [round(v.x * 1920, 1), round((1 - v.y) * 1080, 1), round(v.z, 1)]

    meta = dict(source=str(SRC.relative_to(BASE.parent)), settled_frame=SETTLED, camera=dict(
        position=CAM_POS, aim=CAM_AIM, lens_mm=LENS_MM, sensor_mm=36, plate=[3840, 2160], output=[1920, 1080]),
        push=PUSH, push_centre=PUSH_CENTRE, walk_dir=list(walk),
        crew=[dict(start=scr((x, y, 0)), head=scr((x, y, 1.8)), step_1m=scr((x + walk.x, y + walk.y, 0))) for x, y in CREW_START],
        nessa=dict(foot=scr((NESSA[0], NESSA[1], 0)), head=scr((NESSA[0], NESSA[1], 1.72))),
        gantry=scr((GANTRY[0], GANTRY[1], 10)), hoist_load=scr((GANTRY[0] - 16.5, GANTRY[1], 4)),
        ship_bow=scr((85, 0, 21)), ship_stern=scr((-65, 0, 21)), ship_top=scr((10, 0, 47)),
        engine_bell=scr((-62, -12, 20)), cradle_pads=[scr((-31, -15, 5)), scr((51, -15, 5))])
    (g / "c5-camera.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")


main()
