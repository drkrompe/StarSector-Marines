"""C5 hull card: Second Wind settled in its cradle, rendered from the C5 camera (Cycles, OPTIX).

    python render_c5_hull.py [--samples 96] [--blend PATH] [--root NAME] [--scale 1.0]

Plain Python reads guides/c5-camera.json (build_c5_guides.py) and the shared hull length, then
runs Blender on this same file. Writes layers/c5-hull-shadow.png (the shade the hull throws on
the yard, alpha only: the plate was painted without it) and layers/c5-hull.png: RGBA at plate resolution (3840x2160),
the hull alone on a transparent film, held out wherever the cradle, gantry or foreground props
stand in front of it (guides/c5-occluders.glb), so the card drops onto the painted yard.

The camera only scales in C5 and the hull does not move, so one still is the whole layer.

Model convention (the swap contract, shared with corvus-approach/render_ship_sequence.py):
+X bow, +Y port, +Z up, origin anywhere. The hull is scaled so its X extent is
c1_ship.SW_LENGTH_M (from `metres_per_unit` in second-wind-geometry's patched geometry.json),
and it is centred where the landing scene's settled hull stands, shrunk to that scale, with its
keel down on the cradle's pad tops. A new model needs only SHIP_BLEND / SHIP_ROOT / THROAT_PATTERN (or the flags); a model
without a root object gets one.

Light is the shared sun of landing-wreck-pass.blend (read from that file, never retyped), its
world colour and its AgX exposure, so C5 is lit like C4 and the landing. The yard ground is
present for bounce light but invisible to the camera; a second pass turns it into a shadow
catcher for the shadow layer.
"""
import json
import math
import os
import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent
OUT = BASE / "layers"
BLENDER = Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")
LANDING_BLEND = BASE.parent / "landing-wreck-pass" / "landing-wreck-pass.blend"
SUN_NAME = "Shared sun | all shots"

# ---------------------------------------------------------------- the swappable model
# The surfaced patched state, a gitignored build output of ../../second-wind-surface
# (`rebuild.sh`, or build_reference.py --state patched then apply_skin.py; see its README).
# It has no root object: with SHIP_ROOT None every object is parented to a new empty.
# The older full-surface master is SHIP_BLEND = second-wind-full-surface.blend with
# SHIP_ROOT = "SECOND WIND | flight root".
SHIP_BLEND = BASE.parents[1] / "second-wind-surface" / "build" / "skinned-patched.blend"
SHIP_ROOT = None
# Arrival is the patched state: only the starboard pod (engines 3 and 4) ran on the way in.
# In the yard after landing all four are cold, so no throat glows (a regex here would light them).
THROAT_PATTERN = None
THROAT_EMISSION = ((1.0, 0.32, 0.08), 0.6)
# Cold, heat-darkened bells: their interiors and throats take a sooty matte finish. The
# surfaced model's recess material mirrors the sky from inside a bell seen nearly head-on.
SOOT_PATTERN = r"^Engine \d (aft-facing bell|dark throat|aft-facing nozzle)$"
SOOT_RGB = (0.025, 0.023, 0.022)
# The engine parts are modelled round but flat-shaded, which reads as facets at this range:
# shade them smooth, keeping edges sharper than this angle (rims, lips) crisp.
SMOOTH_PATTERN = r"^Engine \d "
SMOOTH_SHARP_DEG = 35.0
LANDING_PAD_TOP_M = 5.0       # the landing cradle's contact pads, before the shrink
GROUND_RGB = (0.30, 0.30, 0.29)


def _args(argv):
    opts = {"samples": 96, "blend": str(SHIP_BLEND), "root": SHIP_ROOT or "", "throats": THROAT_PATTERN or "",
            "scale": 1.0, "plan": None}
    i = 0
    while i < len(argv):
        k = argv[i].lstrip("-").replace("-", "_")
        v = argv[i + 1]
        opts[k] = type(opts[k])(v) if isinstance(opts[k], (int, float)) and opts[k] is not None else v
        i += 2
    return opts


def plan_and_launch(argv):
    opts = _args(argv)
    sys.path.insert(0, str(BASE.parent / "corvus-approach"))
    import c1_ship
    cam = json.loads((BASE / "guides" / "c5-camera.json").read_text(encoding="utf-8"))
    plan = dict(opts, camera=cam["camera"], hull_box=cam["hull_box"], length_m=c1_ship.SW_LENGTH_M,
                pad_top=LANDING_PAD_TOP_M * cam["scale_from_landing"],
                landing=str(LANDING_BLEND), sun=SUN_NAME, occluders=str(BASE / "guides" / "c5-occluders.glb"),
                out=str(OUT / "c5-hull.png"), shadow_out=str(OUT / "c5-hull-shadow.png"))
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / "c5-hull-plan.json"
    path.write_text(json.dumps(plan, indent=1), encoding="utf-8")
    print("C5 hull: %.1f m, %d samples" % (plan["length_m"], plan["samples"]))
    t0 = time.time()
    subprocess.run([str(BLENDER), "--background", "--factory-startup", "--python-exit-code", "1",
                    "--python", str(Path(__file__).resolve()), "--", "--plan", str(path)], check=True)
    print("Rendered in %.0fs" % (time.time() - t0))


def blender_main(argv):
    import re

    import bpy
    from mathutils import Matrix, Vector

    plan = json.loads(Path(_args(argv)["plan"]).read_text(encoding="utf-8"))

    # The shared sun, world and exposure, read out of the landing scene.
    with bpy.data.libraries.load(plan["landing"], link=False) as (src, dst):
        dst.objects = [plan["sun"]]
    sun_src = dst.objects[0]
    sun = dict(dir=list(sun_src.matrix_world.to_3x3() @ Vector((0, 0, 1))), energy=sun_src.data.energy,
               color=list(sun_src.data.color), angle=sun_src.data.angle)
    bpy.ops.wm.open_mainfile(filepath=plan["landing"], load_ui=False)
    sc0 = bpy.context.scene
    bg0 = sc0.world.node_tree.nodes.get("Background")
    world = dict(color=list(bg0.inputs["Color"].default_value), strength=bg0.inputs["Strength"].default_value)
    view = dict(transform=sc0.view_settings.view_transform, look=sc0.view_settings.look,
                exposure=sc0.view_settings.exposure, gamma=sc0.view_settings.gamma)

    if not Path(plan["blend"]).exists():
        raise RuntimeError("Missing ship master %s (build it: second-wind-surface/README.md)" % plan["blend"])
    bpy.ops.wm.open_mainfile(filepath=plan["blend"], load_ui=False)
    scene = bpy.context.scene
    if plan["root"]:
        root = bpy.data.objects.get(plan["root"])
        if root is None:
            raise RuntimeError("Missing ship root %r in %s" % (plan["root"], plan["blend"]))
    else:
        for obj in [o for o in bpy.data.objects if o.type in ("CAMERA", "LIGHT")]:
            bpy.data.objects.remove(obj, do_unlink=True)
        root = bpy.data.objects.new("C5 | ship root", None)
        scene.collection.objects.link(root)
        for obj in bpy.data.objects:
            if obj is not root and obj.parent is None:
                mw = obj.matrix_world.copy()
                obj.parent = root
                obj.matrix_world = mw
    ship = {root, *root.children_recursive}
    for obj in list(bpy.data.objects):
        if obj not in ship:
            bpy.data.objects.remove(obj, do_unlink=True)
    for obj in ship:
        obj.animation_data_clear()

    root.matrix_world = Matrix.Identity(4)
    bpy.context.view_layer.update()
    meshes = [o for o in ship if o.type == "MESH" and not o.hide_render]
    pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
    lo = Vector([min(p[i] for p in pts) for i in range(3)])
    hi = Vector([max(p[i] for p in pts) for i in range(3)])
    s = plan["length_m"] / (hi.x - lo.x)
    tlo, thi = Vector(plan["hull_box"]["lo"]), Vector(plan["hull_box"]["hi"])
    # centred where the landing hull stands, keel down on the cradle's pad tops
    seat = Vector(((tlo.x + thi.x) / 2, (tlo.y + thi.y) / 2, plan["pad_top"]))
    model = Vector(((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, lo.z))
    root.matrix_world = Matrix.Translation(seat) @ Matrix.Scale(s, 4) @ Matrix.Translation(-model)
    bpy.context.view_layer.update()
    print("hull scale %.4f m/unit-of-model, box %s..%s" % (s, tuple(round(v, 2) for v in tlo), tuple(round(v, 2) for v in thi)))

    emit = bpy.data.materials.new("C5 | cooling throat")
    emit.use_nodes = True
    nt = emit.node_tree
    nt.nodes.clear()
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (*THROAT_EMISSION[0], 1)
    em.inputs["Strength"].default_value = THROAT_EMISSION[1]
    nt.links.new(em.outputs[0], nt.nodes.new("ShaderNodeOutputMaterial").inputs[0])
    for o in meshes:
        if plan["throats"] and re.search(plan["throats"], o.name):
            o.data.materials.clear()
            o.data.materials.append(emit)

    for o in meshes:
        if re.search(SMOOTH_PATTERN, o.name):
            o.data = o.data.copy()
            o.data.shade_smooth()
            o.data.set_sharp_from_angle(angle=math.radians(SMOOTH_SHARP_DEG))

    soot = bpy.data.materials.new("C5 | cold sooty bell")
    soot.use_nodes = True
    sb = soot.node_tree.nodes["Principled BSDF"]
    sb.inputs["Base Color"].default_value = (*SOOT_RGB, 1)
    sb.inputs["Roughness"].default_value = 0.95
    sb.inputs["Metallic"].default_value = 0.0
    for o in meshes:
        if re.search(SOOT_PATTERN, o.name):
            o.data.materials.clear()
            o.data.materials.append(soot)

    # Occluders: everything in front of the hull from this camera holds it out.
    hold = bpy.data.materials.new("C5 | holdout")
    hold.use_nodes = True
    hold.node_tree.nodes.clear()
    hold.node_tree.links.new(hold.node_tree.nodes.new("ShaderNodeHoldout").outputs[0],
                             hold.node_tree.nodes.new("ShaderNodeOutputMaterial").inputs[0])
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=plan["occluders"])
    for o in set(bpy.data.objects) - before:
        if o.type == "MESH":
            o.data.materials.clear()
            o.data.materials.append(hold)

    ground = bpy.data.objects.new("C5 | bounce ground", bpy.data.meshes.new("C5 | bounce ground"))
    ground.data.from_pydata([(-2000, -2000, -0.05), (2000, -2000, -0.05), (2000, 2000, -0.05), (-2000, 2000, -0.05)],
                            [], [(0, 1, 2, 3)])
    gm = bpy.data.materials.new("C5 | concrete")
    gm.use_nodes = True
    gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (*GROUND_RGB, 1)
    gm.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.9
    ground.data.materials.append(gm)
    ground.visible_camera = False
    scene.collection.objects.link(ground)

    cam_d = plan["camera"]
    cam = bpy.data.objects.new("C5 camera", bpy.data.cameras.new("C5 camera"))
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = cam_d["lens_mm"], cam_d["sensor_mm"], "HORIZONTAL"
    cam.data.clip_start, cam.data.clip_end = 0.3, 20000
    cam.location = cam_d["position"]
    cam.rotation_euler = (Vector(cam_d["aim"]) - Vector(cam_d["position"])).to_track_quat("-Z", "Y").to_euler()
    scene.collection.objects.link(cam)
    scene.camera = cam

    lamp = bpy.data.objects.new("C5 | shared sun", bpy.data.lights.new("C5 | shared sun", "SUN"))
    lamp.data.energy, lamp.data.color, lamp.data.angle = sun["energy"], sun["color"], sun["angle"]
    lamp.rotation_euler = Vector(sun["dir"]).normalized().to_track_quat("Z", "Y").to_euler()
    scene.collection.objects.link(lamp)
    scene.world = bpy.data.worlds.new("C5 | humid sky")
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = world["color"]
    bg.inputs["Strength"].default_value = world["strength"]

    scene.render.engine = "CYCLES"
    scene.cycles.samples = int(plan["samples"])
    scene.cycles.use_denoising = True
    prefs = bpy.context.preferences.addons["cycles"].preferences
    if os.environ.get("C5_CYCLES_CPU"):
        scene.cycles.device = "CPU"
    else:
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        if not [d for d in prefs.devices if d.type == "OPTIX"]:
            raise RuntimeError("OPTIX GPU required; no silent CPU fallback (set C5_CYCLES_CPU=1 to choose CPU)")
        for d in prefs.devices:
            d.use = d.type == "OPTIX"
        scene.cycles.device = "GPU"
    scene.render.resolution_x, scene.render.resolution_y = cam_d["plate"]
    scene.render.resolution_percentage = int(round(float(plan["scale"]) * 100))
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.use_compositing = False
    scene.view_settings.view_transform = view["transform"]
    scene.view_settings.exposure = view["exposure"]
    scene.view_settings.gamma = view["gamma"]
    scene.render.filepath = plan["out"]
    bpy.ops.render.render(write_still=True)
    print("wrote", plan["out"])

    # Second pass: the hull's shade on the yard alone. The ground turns shadow catcher, the hull
    # and the occluders stay in the light path but leave the camera, so the film holds only the
    # darkening the hull casts (direct shadow and the occlusion under the belly).
    for o in bpy.data.objects:
        if o.type == "MESH":
            o.visible_camera = False
    ground.visible_camera = True
    ground.is_shadow_catcher = True
    ground.location.z = 0.09          # just above the hardstand slab's top, which is an occluder
    scene.render.filepath = plan["shadow_out"]
    bpy.ops.render.render(write_still=True)
    print("wrote", plan["shadow_out"])


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        plan_and_launch(argv)
