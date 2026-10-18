"""Geometry-first guides for C5 NESSA'S YARD (c5-layout.md).

sh ../../../blender.sh --background --factory-startup ../landing-wreck-pass/landing-wreck-pass.blend --python build_c5_guides.py [-- --samples 16 --scale 1.0 --test]

Opens ../landing-wreck-pass/landing-wreck-pass.blend read-only at its settled frame (774):
the damaged Second Wind standing in its cradle, the cradle's contact pads and rails, and the
port-refinement district behind. The landing scene carries the old provisional 1.5 m per hull
unit; the shared scale is `metres_per_unit` in second-wind-geometry/states/patched/geometry.json
(0.65). So the ship and its cradle are shrunk about the hull root by K = mpu / 1.5 and the
camera is brought in by the same factor, which keeps the ship's framing while everything
human-sized (people, the gantry, props, the camera's eye height) stays its true size. The
district is left where it stands: it is far enough away that it reads as further off, which
is what a smaller ship in the same yard means.

Adds what C5 needs and the landing never had: the sheltered crew gantry with its hoist, the
yard crew, Nessa, near-ground props and a far tree line. Each depth layer is rendered alone on
a transparent film at plate resolution (3840x2160) from the one C5 camera, all in clay.

Writes guides/c5-<layer>-guide.png, guides/c5-plate-guide.png (the whole set with the hull
invisible to the camera but still casting its shadow: the Codex yard plate is painted from it,
and the rendered hull card goes on top), guides/c5-clay-all.png and guides/c5-camera.json.
The source blend is never saved.
"""
import json
import math
import random
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

BASE = Path(__file__).resolve().parent
SRC = BASE.parent / "landing-wreck-pass" / "landing-wreck-pass.blend"
GEOMETRY_JSON = BASE.parents[1] / "second-wind-geometry" / "states" / "patched" / "geometry.json"
SETTLED = 774
LANDING_M_PER_UNIT = 1.5          # the landing scene's provisional scale (its root carries it)
MPU = json.loads(GEOMETRY_JSON.read_text(encoding="utf-8"))["metres_per_unit"]
K = MPU / LANDING_M_PER_UNIT      # ship-sized things in the landing scene shrink by this

# Camera: starboard stern quarter from the yard's perimeter walkway, C1's entrance angle on the
# ground. Ship-relative placement is the landing layout's, scaled by K; eye height is a person's
# on a raised walkway. Sun astern and 51 degrees up: front-lit from the left, raking the flank.
CAM_POS = (-128.0 * K, -178.0 * K, 4.0)
CAM_AIM = (10.0 * K, 0.0, 17.0 * K)
LENS_MM = 40.0
PUSH = (1.00, 1.08)
PUSH_CENTRE = (1000, 600)   # output px: just aft of midships, between the hull and Nessa

# Human-scale additions, in metres in the C5 scene (hull root at the origin, +X bow, +Y port).
GANTRY = (21.0, -30.0)       # sheltered crew gantry, off the bow's starboard pad
GANTRY_SIZE = (9.0, 4.5, 4.2)
NESSA = (-8.0, -31.0)        # apart, on sunlit concrete, between the crew and the stern
CREW_START = [(14.6, -28.4), (16.4, -29.9), (13.0, -30.6), (17.8, -27.6), (15.4, -31.4)]
WALK_SPEED = 1.3
HOIST_LOAD = (GANTRY[0] - 7.6, GANTRY[1] + 0.4)   # under the jib's head: the crate's ground spot
STEAM_SOURCES = [            # (x, y, z, kind): two cradle cooling vents and the hull beside the bells
    (-14.0, -9.0, 0.4, "vent"), (21.5, -9.0, 0.4, "vent"), (-26.0, -9.5, 15.0, "hull"),
]
SHIMMER = [(-29.0, -9.5, 13.0, 4.5), (-29.0, 9.5, 13.0, 4.5)]  # engine bell mouths, radius m

CLAY = {
    "ship": (0.62, 0.42, 0.32), "cradle": (0.36, 0.44, 0.56), "district": (0.66, 0.66, 0.64),
    "ground": (0.48, 0.48, 0.47), "ground_far": (0.50, 0.50, 0.48), "trees": (0.16, 0.28, 0.14),
    "gantry": (0.80, 0.62, 0.20), "crew": (0.95, 0.95, 0.90), "nessa": (0.15, 0.55, 0.85),
    "fore": (0.30, 0.30, 0.32),
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
    bpy.ops.mesh.primitive_cylinder_add(radius=0.28, depth=h - 0.3, location=(x, y, (h - 0.3) / 2))
    body = bpy.context.active_object
    body.name = "C5 " + name
    set_clay(body, mat)
    move(body, layer)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.14, location=(x, y, h - 0.14))
    head = bpy.context.active_object
    head.name = "C5 " + name + " head"
    set_clay(head, mat)
    move(head, layer)


def classify():
    sc = bpy.context.scene
    root = bpy.data.objects["SECOND WIND | flight root"]
    ship = {o.name for o in (root, *root.children_recursive)}
    for ob in list(sc.objects):
        n = ob.name
        if ob.type in ("CAMERA", "LIGHT") and n != "Shared sun | all shots":
            ob.hide_render = True
            continue
        if n in ship:
            if ob.type == "MESH":
                set_clay(ob, "ship")
                far = max((ob.matrix_world @ Vector(c)).length for c in ob.bound_box) > 200
                ob.hide_render = ob.hide_render or far
            move(ob, "L3h-hull")
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
    # Animation drives the ship; freeze the settled pose so the shrink holds, then shrink ship
    # and cradle (top-most of each set only: children follow their parents).
    sc.frame_set(SETTLED)
    group = list(coll("L3h-hull").objects) + list(coll("L3-ship").objects)
    settled = {ob: ob.matrix_world.copy() for ob in group}
    for ob in group:
        ob.animation_data_clear()
    for ob in group:
        ob.matrix_world = settled[ob]
    bpy.context.view_layer.update()
    names = {ob.name for ob in group}
    for ob in group:
        if ob.parent is None or ob.parent.name not in names:
            ob.matrix_world = Matrix.Scale(K, 4) @ ob.matrix_world
            bpy.context.view_layer.update()


def hull_box():
    root = bpy.data.objects["SECOND WIND | flight root"]
    ms = [o for o in root.children_recursive if o.type == "MESH" and not o.hide_render
          and not o.name.startswith(("PROPOSAL", "LIFT VECTOR", "LANDING FX", "WRECK PASS"))]
    pts = [o.matrix_world @ Vector(c) for o in ms for c in o.bound_box]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    return lo, hi


def build_additions():
    cam = Vector(CAM_POS)
    fwd = (Vector(CAM_AIM) - cam)
    fwd.z = 0
    fwd.normalize()
    right = Vector((fwd.y, -fwd.x, 0.0))
    walk = -right                                    # parallel to the picture plane, screen left
    # one ground for everything near: apron, cradle hardstand surround and the yard beyond
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 600, -0.02))
    g = bpy.context.active_object
    g.name, g.scale = "C5 yard ground", (5000, 2400, 1)
    set_clay(g, "ground")
    move(g, "L3-ship")
    # far tree line (L1, with the sky)
    rng = random.Random(9)
    for _ in range(220):
        x, y = rng.uniform(-2500, 2500), rng.uniform(2300, 2900)
        r = rng.uniform(14, 30)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=(x, y, r * 0.3), segments=10, ring_count=6)
        t = bpy.context.active_object
        t.scale.z = rng.uniform(0.9, 1.4)
        set_clay(t, "trees")
        move(t, "L1-sky")
    # sheltered crew gantry: legs, roof, back screen; a jib hoist on its stern-side end
    gx, gy = GANTRY
    sx, sy, sz = GANTRY_SIZE
    for dx in (-sx / 2 + 0.3, sx / 2 - 0.3):
        for dy in (-sy / 2 + 0.3, sy / 2 - 0.3):
            add_box("L3-ship", "gantry", (gx + dx, gy + dy, sz / 2), (0.25, 0.25, sz), "gantry leg")
    add_box("L3-ship", "gantry", (gx, gy, sz + 0.15), (sx + 0.6, sy + 0.6, 0.3), "gantry roof")
    add_box("L3-ship", "gantry", (gx, gy - sy / 2, 1.6), (sx - 0.6, 0.12, 3.2), "gantry wind screen")
    add_box("L3-ship", "gantry", (gx - sx / 2 - 0.4, gy + 0.4, 3.2), (0.3, 0.3, 6.4), "hoist mast")
    add_box("L3-ship", "gantry", (gx - sx / 2 - 3.6, gy + 0.4, 6.3), (7.0, 0.25, 0.3), "hoist jib")
    # yard crew in a loose file leaving the gantry, and Nessa
    for i, (x, y) in enumerate(CREW_START):
        add_person("L3e-crew", "crew", x, y, "crew %d" % i)
    add_person("L3e-crew", "nessa", *NESSA, "Nessa", h=1.72)
    # foreground: perimeter barrier and service gear across the bottom of frame
    base = cam + fwd * 24
    for k in range(-5, 6, 2):
        p = base + right * (k * 3.2)
        add_box("L4-fore", "fore", (p.x, p.y, 0.45), (2.8, 0.5, 0.9), "barrier")
    p = cam + fwd * 21 + right * 8.5
    add_box("L4-fore", "fore", (p.x, p.y, 0.6), (1.2, 1.2, 1.2), "cable reel")
    p = cam + fwd * 22.5 + right * 11.5
    add_box("L4-fore", "fore", (p.x, p.y, 0.75), (2.4, 1.3, 1.5), "service cart")
    return walk


def camera():
    sc = bpy.context.scene
    cd = bpy.data.cameras.new("C5 camera")
    cd.lens, cd.sensor_width, cd.sensor_fit = LENS_MM, 36, "HORIZONTAL"
    cd.clip_start, cd.clip_end = 0.3, 20000
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


def hull_visible(camera_visible):
    for ob in coll("L3h-hull").objects:
        ob.visible_camera = camera_visible


def export_occluders(path):
    """Everything near that can stand in front of the hull, for render_c5_hull.py's holdouts."""
    bpy.ops.object.select_all(action="DESELECT")
    keep = [o for name in ("L3-ship", "L4-fore") for o in coll(name).all_objects
            if o.type == "MESH" and not o.hide_render and o.name != "C5 yard ground"]
    for o in keep:
        o.hide_set(False)
        o.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(path), use_selection=True, export_apply=True,
                              export_materials="NONE", export_animations=False)
    print("wrote", Path(path).name, len(keep), "occluders")


def render(path, only=None, transparent=True, scale=1.0, samples=16, holdout=()):
    sc = bpy.context.scene
    for lc in bpy.context.view_layer.layer_collection.children:
        lc.holdout = lc.name[3:] in holdout if lc.name.startswith("C5 ") else False
    for c in sc.collection.children:
        if c.name.startswith("C5 "):
            c.hide_render = only is not None and c.name[3:] not in only
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
    samples = int(opt("--samples", 16))
    g = BASE / "guides"
    g.mkdir(exist_ok=True)
    if opt("--test", False):
        render(g / "c5-test.png", None, False, 0.25, 8)
        return
    hull_visible(False)
    # the near layer as the camera sees it in front of the district (the split between L2 and L3)
    render(g / "c5-L3-front-guide.png", {"L2-yard", "L3-ship", "L3h-hull"}, True, scale, samples, holdout={"L2-yard"})
    if opt("--masks", False):
        return
    hull_visible(True)
    render(g / "c5-clay-all.png", None, False, scale, samples)
    hull_visible(False)                      # from here the hull only casts its shadow
    render(g / "c5-plate-guide.png", {"L1-sky", "L2-yard", "L3-ship", "L3h-hull", "L4-fore"}, False, scale, samples)
    render(g / "c5-L1-sky-guide.png", {"L1-sky"}, False, scale, samples)
    render(g / "c5-L2-yard-guide.png", {"L2-yard"}, True, scale, samples)
    render(g / "c5-L3-ship-guide.png", {"L3-ship", "L3h-hull"}, True, scale, samples)
    render(g / "c5-L4-fore-guide.png", {"L4-fore"}, True, scale, samples)
    export_occluders(g / "c5-occluders.glb")
    hull_visible(True)
    render(g / "c5-L3e-crew-guide.png", {"L3e-crew"}, True, scale, samples)

    from bpy_extras.object_utils import world_to_camera_view
    co = sc.camera

    def scr(p):
        v = world_to_camera_view(sc, co, Vector(p))
        return [round(v.x * 1920, 1), round((1 - v.y) * 1080, 1), round(v.z, 1)]

    lo, hi = hull_box()
    meta = dict(
        source=str(SRC.relative_to(BASE.parent)), settled_frame=SETTLED, metres_per_unit=MPU, scale_from_landing=K,
        camera=dict(position=CAM_POS, aim=CAM_AIM, lens_mm=LENS_MM, sensor_mm=36, plate=[3840, 2160], output=[1920, 1080]),
        hull_box=dict(lo=lo, hi=hi), push=PUSH, push_centre=PUSH_CENTRE, walk_dir=list(walk), walk_speed=WALK_SPEED,
        crew=[dict(start=scr((x, y, 0)), head=scr((x, y, 1.8)),
                   step_1m=scr((x + walk.x, y + walk.y, 0))) for x, y in CREW_START],
        nessa=dict(foot=scr((NESSA[0], NESSA[1], 0)), head=scr((NESSA[0], NESSA[1], 1.72))),
        gantry=scr((GANTRY[0], GANTRY[1], GANTRY_SIZE[2])),
        hoist=dict(head=scr((HOIST_LOAD[0], HOIST_LOAD[1], 6.2)), low=scr((HOIST_LOAD[0], HOIST_LOAD[1], 0.0)),
                   up=scr((HOIST_LOAD[0], HOIST_LOAD[1], 2.0)), crate_m=1.4),
        steam=[dict(kind=k, at=scr((x, y, z)), metre_px=scr((x, y, z + 1.0))[1] - scr((x, y, z))[1]) for x, y, z, k in STEAM_SOURCES],
        shimmer=[dict(at=scr((x, y, z)), r_px=abs(scr((x, y, z + r))[1] - scr((x, y, z))[1])) for x, y, z, r in SHIMMER],
        ship_bow=scr((hi[0], 0, (lo[2] + hi[2]) / 2)), ship_stern=scr((lo[0], 0, (lo[2] + hi[2]) / 2)),
        ship_top=scr(((lo[0] + hi[0]) / 2, 0, hi[2])))
    (g / "c5-camera.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")


main()
