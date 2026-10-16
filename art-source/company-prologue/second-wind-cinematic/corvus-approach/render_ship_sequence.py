"""Second Wind's C1 flight as a rendered alpha sequence (Cycles, OPTIX).

    python render_ship_sequence.py [options]

Plain Python plans the flight from c1_ship.py (the single owner of the motion), then
runs Blender on this same file to render it, one image per sample:

    layers/second-wind-seq/plan.json      the flight handed to Blender
    layers/second-wind-seq/meta.json      per sample: file, crop, ship centre, nozzles
    layers/second-wind-seq/f_00000.png    RGBA, cropped around the ship

The layer is generated (and large); regenerate it with this command whenever c1_ship.py's
motion constants, the shot length or the model change. c1_ship.py checks meta.json's
motion key and falls back to the fixed card if the sequence is stale.

The ship is placed at its true position and attitude in the plate camera's space: camera
at the origin looking down -z, 50 mm across 36 mm on 1920x1080 (render_planet.py's
lens). Each image is a lens-shifted crop of that plate camera around the hull, so its
perspective is exactly what the plate would see; only the crop is rendered. Light is
c1_core.SUN_IN_CAMERA (Corvus, upper left and toward camera, render_planet.py's key) and
a faint cool planet-shine from Jangala's side, below. The bell throats glow; nozzle
mouth centres, radii, facing and occlusion are projected per sample for the additive
halos c1_ship.py lays over them, so the glows stay registered as the attitude changes.

Options:
  --fps N            samples per second (default 24; 12 halves the render and holds attitude)
  --samples N        Cycles samples (default 64, denoised)
  --supersample X    image px per plate px (default 1.15)
  --blend PATH       ship master (default SHIP_BLEND); see SHIP_* below to swap the model
  --root NAME        flight-root object; everything parented under it renders
  --nozzles REGEX    objects whose origins are the bell mouths (axis: the ship's -X)
  --throats REGEX    objects given the engine-glow emission
  --only A:B         render only samples A..B-1 (for timing and spot checks)
  --resume           keep frames already written for the same plan key

Model convention (the swap contract): +X bow, +Y port, +Z up, origin anywhere. The hull is
scaled so its X extent is c1_ship.SW_LENGTH_M metres and rotated about its bounding-box
centre. A new model needs only SHIP_BLEND / SHIP_ROOT / NOZZLE_PATTERN / THROAT_PATTERN
(or the flags); with no nozzle matches the sequence renders without glows.
"""
import json
import math
import re
import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent
OUT = BASE / "layers" / "second-wind-seq"
BLENDER = Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")

# ---------------------------------------------------------------- the swappable model
SHIP_BLEND = BASE.parent / "second-wind-full-surface" / "second-wind-full-surface.blend"
SHIP_ROOT = "SECOND WIND | flight root"
NOZZLE_PATTERN = r"^Engine \d+ mouth rim$"
# Arrival is the patched state (second-wind-pipeline.md): only the starboard pod, engines 3 and 4,
# runs on the salvaged drive coil. The port pod (1 and 2) is dead and stays cold.
THROAT_PATTERN = r"^Engine [34] dark throat$"
NOZZLE_RADIUS_M = None        # None: half the mouth object's largest cross-section extent

# ---------------------------------------------------------------- look
SUN_STRENGTH = 4.5            # render_ship_card.py's key, so the sequence and the card agree
SUN_COLOR = (1.0, 0.97, 0.9)  # render_planet.py's Corvus
SHINE_DIR = (0.15, -0.55, -1.0)   # toward the planet-shine source: Jangala, behind and below
SHINE_STRENGTH = 0.35
SHINE_COLOR = (0.55, 0.75, 1.0)
THROAT_EMISSION = ((0.55, 0.78, 1.0), 6.0)
CROP_PAD_PX = 90              # overscan beyond the plate frame the crop may cover (truck and push)


def _args(argv):
    opts = {"fps": 24, "samples": 64, "supersample": 1.15, "blend": str(SHIP_BLEND), "root": SHIP_ROOT,
            "nozzles": NOZZLE_PATTERN, "throats": THROAT_PATTERN, "only": None, "resume": False, "plan": None}
    i = 0
    while i < len(argv):
        k = argv[i].lstrip("-").replace("-", "_")
        if k == "resume":
            opts["resume"] = True
            i += 1
            continue
        v = argv[i + 1]
        opts[k] = type(opts[k])(v) if isinstance(opts[k], (int, float)) and not isinstance(opts[k], bool) else v
        i += 2
    return opts


# ================================================================ plain Python: plan and launch

def plan_and_launch(argv):
    opts = _args(argv)
    sys.path.insert(0, str(BASE))
    import c1_ship
    import compose_c1
    duration = compose_c1.c1_duration()
    plan = c1_ship.plan(duration, opts["fps"])
    plan.update({k: opts[k] for k in ("supersample", "blend", "root", "nozzles", "throats", "only", "resume")})
    plan["cycles_samples"] = opts["samples"]
    plan["sun"] = list(__import__("c1_core").SUN_IN_CAMERA)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "plan.json").write_text(json.dumps(plan), encoding="utf-8")
    print("C1 %.2fs: %d samples at %d fps, key %s" % (duration, len(plan["samples"]), opts["fps"], plan["key"]))
    started = time.time()
    subprocess.run([str(BLENDER), "--background", "--factory-startup", "--python-exit-code", "1",
                    "--python", str(Path(__file__).resolve()), "--", "--plan", str(OUT / "plan.json")], check=True)
    print("Rendered in %.0fs" % (time.time() - started))


# ================================================================ Blender: render the plan

def blender_main(argv):
    import bpy
    from mathutils import Matrix, Vector

    opts = _args(argv)
    plan = json.loads(Path(opts["plan"]).read_text(encoding="utf-8"))
    fw, fh = plan["frame"]
    focal = plan["focal_px"]
    ss = float(plan["supersample"])

    bpy.ops.wm.open_mainfile(filepath=plan["blend"])
    scene = bpy.context.scene
    root = bpy.data.objects.get(plan["root"])
    if root is None:
        raise RuntimeError("Missing ship root %r in %s" % (plan["root"], plan["blend"]))
    ship = {root, *root.children_recursive}
    for obj in list(bpy.data.objects):
        if obj not in ship:
            bpy.data.objects.remove(obj, do_unlink=True)
    for obj in ship:
        obj.animation_data_clear()

    # Measure the hull in the root's own frame (unit transform, scale 1).
    root.matrix_world = Matrix.Identity(4)
    bpy.context.view_layer.update()
    meshes = [o for o in ship if o.type == "MESH" and not o.hide_render]
    pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
    lo = Vector([min(p[i] for p in pts) for i in range(3)])
    hi = Vector([max(p[i] for p in pts) for i in range(3)])
    centre = (lo + hi) / 2
    scale = plan["length_m"] / (hi.x - lo.x)
    corners = [Vector((x, y, z)) - centre for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]

    nozzles = sorted((o for o in ship if re.search(plan["nozzles"], o.name)), key=lambda o: o.name)
    nozzle_local = []
    for o in nozzles:
        r = NOZZLE_RADIUS_M / scale if NOZZLE_RADIUS_M else \
            max(o.dimensions) * max(o.matrix_world.to_scale()) / max(o.scale) / 2
        nozzle_local.append((o.matrix_world.translation - centre, r, o))
    engine_of = {}
    for o in ship:
        m = re.match(r"(Engine \d+)", o.name)
        if m:
            engine_of[o.name] = m.group(1)

    # Engines lit: an emission on the throats.
    emit = bpy.data.materials.new("C1 | engine throat")
    emit.use_nodes = True
    nt = emit.node_tree
    nt.nodes.clear()
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (*THROAT_EMISSION[0], 1)
    em.inputs["Strength"].default_value = THROAT_EMISSION[1]
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(em.outputs[0], out.inputs[0])
    for o in ship:
        if o.type == "MESH" and re.search(plan["throats"], o.name):
            o.data.materials.clear()
            o.data.materials.append(emit)

    # Plate camera at the origin, unrotated: Blender's camera space is the plate's.
    cam = bpy.data.objects.new("C1 | plate camera", bpy.data.cameras.new("C1 | plate camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.matrix_world = Matrix.Identity(4)
    cam.data.type = "PERSP"
    cam.data.lens = 50.0
    cam.data.sensor_fit = "AUTO"
    cam.data.clip_start = 1.0
    cam.data.clip_end = 1e6

    scene.world = bpy.data.worlds.new("C1 | space")
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (0.02, 0.03, 0.05, 1)
    bg.inputs["Strength"].default_value = 0.15

    def sun_lamp(name, toward, energy, color, angle):
        lamp = bpy.data.objects.new(name, bpy.data.lights.new(name, "SUN"))
        lamp.data.energy, lamp.data.color, lamp.data.angle = energy, color, math.radians(angle)
        lamp.rotation_euler = Vector(toward).normalized().to_track_quat("Z", "Y").to_euler()
        scene.collection.objects.link(lamp)

    sun_lamp("C1 | Corvus", plan["sun"], SUN_STRENGTH, SUN_COLOR, 0.5)
    sun_lamp("C1 | planet shine", SHINE_DIR, SHINE_STRENGTH, SHINE_COLOR, 8.0)

    scene.render.engine = "CYCLES"
    scene.cycles.samples = int(plan["cycles_samples"])
    scene.cycles.use_denoising = True
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.compute_device_type = "OPTIX"
    prefs.get_devices()
    if not [d for d in prefs.devices if d.type == "OPTIX"]:
        raise RuntimeError("OPTIX GPU required; no silent CPU fallback")
    for device in prefs.devices:
        device.use = device.type == "OPTIX"
    scene.cycles.device = "GPU"
    scene.render.use_persistent_data = True
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.use_compositing = False
    scene.view_settings.view_transform = "AgX"

    def project(p):
        return fw / 2 + focal * p.x / -p.z, fh / 2 - focal * p.y / -p.z

    OUT.mkdir(parents=True, exist_ok=True)
    meta_path = OUT / "meta.json"
    old = {}
    if plan.get("resume") and meta_path.exists():
        prev = json.loads(meta_path.read_text(encoding="utf-8"))
        if prev.get("key") == plan["key"]:
            old = {s["index"]: s for s in prev["samples"]}
    lo_i, hi_i = 0, len(plan["samples"])
    if plan.get("only"):
        a, b = plan["only"].split(":")
        lo_i, hi_i = int(a), int(b)
    records, times = [], []
    for s in plan["samples"]:
        i = s["index"]
        rec = {"index": i, "t": s["t"], "range": s["range"], "screen": s["screen"], "supersample": ss}
        if not (lo_i <= i < hi_i):
            if i in old:
                records.append(old[i])
            continue
        f, port, up = Vector(s["bow"]), Vector(s["port"]), Vector(s["up"])
        rot = Matrix((f, port, up)).transposed()        # columns: model X, Y, Z in camera space
        place = Matrix.Translation(Vector(s["position"])) @ rot.to_4x4() @ Matrix.Scale(scale, 4) \
            @ Matrix.Translation(-centre)
        root.matrix_world = place
        world_corners = [Vector(s["position"]) + rot @ (c * scale) for c in corners]
        px = [project(c) for c in world_corners]
        x0 = max(-CROP_PAD_PX, min(p[0] for p in px))
        x1 = min(fw + CROP_PAD_PX, max(p[0] for p in px))
        y0 = max(-CROP_PAD_PX, min(p[1] for p in px))
        y1 = min(fh + CROP_PAD_PX, max(p[1] for p in px))
        if x1 - x0 < 2 or y1 - y0 < 2:
            rec.update({"file": None, "size": [2, 2], "origin_px": [1, 1], "nozzles": []})
            records.append(rec)
            continue
        side = max(x1 - x0, y1 - y0) * 1.03 + 4
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        res = int(math.ceil(side * ss))
        side = res / ss
        left, top = cx - side / 2, cy - side / 2
        name = "f_%05d.png" % i
        rec.update({"file": name, "size": [res, res], "crop": [left, top, side],
                    "origin_px": [(s["screen"][0] - left) * ss, (s["screen"][1] - top) * ss]})

        noz = []
        cam_pos = Vector((0, 0, 0))
        bpy.context.view_layer.update()
        for local, r, obj in nozzle_local:
            wp = Vector(s["position"]) + rot @ (local * scale)
            nx, ny = project(wp)
            axis = -f
            to_cam = (cam_pos - wp).normalized()
            dist = (wp - cam_pos).length
            hit, loc, _, _, hobj, _ = scene.ray_cast(bpy.context.view_layer.depsgraph, cam_pos,
                                                     (wp - cam_pos).normalized(), distance=dist + 1.0)
            visible = 1.0
            if hit and (loc - cam_pos).length < dist - r * scale * 1.5 and \
                    engine_of.get(hobj.name) != engine_of.get(obj.name):
                visible = 0.0
            noz.append({"x": (nx - left) * ss, "y": (ny - top) * ss, "r": focal * r * scale / -wp.z * ss,
                        "facing": max(0.0, axis.dot(to_cam)), "visible": visible})
        rec["nozzles"] = noz

        if plan.get("resume") and i in old and old[i].get("file") == name and (OUT / name).exists() \
                and old[i].get("size") == rec["size"]:
            records.append(old[i])
            continue
        cam.data.sensor_width = 50.0 * side / focal
        cam.data.shift_x = (cx - fw / 2) / side
        cam.data.shift_y = -(cy - fh / 2) / side
        scene.render.resolution_x = res
        scene.render.resolution_y = res
        scene.render.filepath = str(OUT / name)
        t0 = time.time()
        bpy.ops.render.render(write_still=True)
        times.append(time.time() - t0)
        records.append(rec)
        print("sample %d t=%.2f %dpx %.1fs" % (i, s["t"], res, times[-1]), flush=True)
        if len(times) % 24 == 0:
            _write_meta(meta_path, plan, records)

    _write_meta(meta_path, plan, records)
    if times:
        print("Render: %d frames, mean %.2fs, max %.2fs" % (len(times), sum(times) / len(times), max(times)))


def _write_meta(path, plan, records):
    meta = {"key": plan["key"], "duration": plan["duration"], "fps": plan["fps"], "blend": plan["blend"],
            "root": plan["root"], "samples": sorted(records, key=lambda r: r["index"])}
    path.write_text(json.dumps(meta), encoding="utf-8")


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        plan_and_launch(argv)
