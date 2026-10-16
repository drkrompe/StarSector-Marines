"""C2's Legion pass as a rendered alpha sequence (Cycles, motion blur), with its fighters.

    python render_patrol_sequence.py [--samples N] [--only A:B] [--resume] [--blend PATH]

Plain Python plans the pass from c2_core.py (the single owner of the motion): the hull's
track and rise, the scrutiny turret's training, every other mount laid fore or aft, and
the fighters' launch and recovery paths along geometry.json's deck vectors. It then runs
Blender on this same file, which opens the Legion master read-only, keys all of that per
frame and renders 1920x1080 RGBA frames through C2's cabin camera:

    build/cinematic/c2-patrol/legion-seq/plan.json     the pass handed to Blender
    build/cinematic/c2-patrol/legion-seq/f_00000.png   one per C2 frame (24 fps, 360 frames)
    build/cinematic/c2-patrol/legion-seq/meta.json     plan key, model, sample count

The camera is the cabin camera (c2_core.CABIN_PITCH_DEG, 26 mm on 36 mm) at the world
origin, so a frame registers to the cabin plates with no crop or shift. Light is
c2_core.SUN, hard, with Jangala's planet-shine from below at c2_core.PLANETSHINE of it and
no world light: the near (starboard) flank faces away from the sun and goes black, as the
layout requires. Motion blur uses a 180-degree shutter.

Fighters are heightfields of the installed vanilla sprites (identity references, read at
build time, never copied), the same construction as corvus-approach/traffic_render_cards.py,
lit by the same sun, so they sit in the hull's light and are occluded by it correctly.
They are parented to the hull, flying the paths c2_core.fighter_pose gives in hull units.

The model is swappable (a detailing pass is in progress): C2_LEGION_BLEND or --blend. The
contract is geometry.json's object names: 'Weapon <slot> pivot', 'Engine <i> dark throat',
'Flight deck ...', 'Wing starboard sponson', 'Bridge command dome'. Cameras and lamps in
the master are discarded.

GPU: OptiX by default; set C1_CYCLES_CPU=1 to render on the CPU when the GPU is busy.
"""
import hashlib
import json
import math
import os
import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
OUT = ROOT / "build" / "cinematic" / "c2-patrol" / "legion-seq"
BLENDER = Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")
GAME = Path(os.environ.get("STARSECTOR_DIR", r"C:\Program Files (x86)\Fractal Softworks\Starsector")) / "starsector-core"

FIGHTERS = {   # hull: (sprite, engine slots in .ship coordinates, x forward and y port)
    "broadsword": ("graphics/ships/broadsword.png", [(-11, 6), (-11, -5)]),
    "hoplon": ("graphics/ships/khopesh.png", [(-14, 7), (-14, -7)]),
}

SUN_STRENGTH = 4.5             # C1's key (render_ship_sequence.py), so the two shots agree
SUN_COLOR = (1.0, 0.97, 0.9)
SHINE_COLOR = (0.55, 0.75, 1.0)
LEGION_THROAT = ((1.0, 0.62, 0.30), 2.5)      # low-tech drive idling: the patrol coasts
FIGHTER_FLAME = ((1.0, 0.70, 0.38), 40.0)     # at full throttle
RUNNING_LIGHTS = [  # (where, colour, blink period s, phase s)
    ("starboard tip", (1.0, 0.12, 0.08), 1.6, 0.0),
    ("bow", (1.0, 1.0, 1.0), 1.6, 0.8),
    ("dome", (1.0, 1.0, 1.0), 2.4, 0.3),
    ("starboard stern", (1.0, 0.12, 0.08), 1.6, 0.5),
]


def _args(argv):
    opts = {"samples": 48, "only": None, "resume": False, "blend": None, "plan": None}
    i = 0
    while i < len(argv):
        k = argv[i].lstrip("-").replace("-", "_")
        if k == "resume":
            opts["resume"] = True
            i += 1
            continue
        opts[k] = int(argv[i + 1]) if k == "samples" else argv[i + 1]
        i += 2
    return opts


# ================================================================ plain Python: plan and launch

def mount_rest(slot):
    """'Mounts cold': fore (0) or aft (180), whichever the arc allows nearer the rest angle."""
    lo, hi = slot["angle"] - slot["arc"] / 2, slot["angle"] + slot["arc"] / 2
    choices = [a for a in (0.0, 180.0, -180.0) if lo <= a <= hi]
    if not choices:
        return float(slot["angle"])
    return min(choices, key=lambda a: abs(a - slot["angle"]))


def plan(opts):
    sys.path.insert(0, str(BASE))
    import c2_core as C
    geom = json.loads(C.LEGION_GEOMETRY.read_text(encoding="utf-8"))
    frames = round(C.C2_LEN * C.FPS)
    scrutiny = next(s for s in geom["weapon_slots"] if s["id"] == C.SCRUTINY_SLOT)
    lo, hi = scrutiny["angle"] - scrutiny["arc"] / 2, scrutiny["angle"] + scrutiny["arc"] / 2
    rel0, rel1 = C.SCRUTINY_RELEASE
    cold = mount_rest(scrutiny)
    samples = []
    for i in range(frames + 1):        # one past the end so motion blur has a key to read
        t = i / C.FPS
        o = C.legion_origin(t)
        tx = o[0] + C.M_PER_UNIT * scrutiny["location"][0]
        ty = o[1] + C.M_PER_UNIT * scrutiny["location"][1]
        track = max(lo, min(hi, math.degrees(math.atan2(-ty, -tx))))
        u = C.smooth((t - rel0) / (rel1 - rel0))
        fighters = []
        for kind, group in (("launch", C.LAUNCHES), ("recover", C.RECOVERIES)):
            for j, f in enumerate(group):
                pose = C.fighter_pose(f, kind, t, geom)
                fighters.append(None if pose is None else {"pos": list(pose[0]), "throttle": pose[1]})
        samples.append({"index": i, "t": t, "origin": list(o), "scrutiny_deg": track + (cold - track) * u,
                        "fighters": fighters})
    p = {
        "frames": frames, "fps": C.FPS, "width": C.W, "height": C.H,
        "lens_mm": C.LENS_MM, "sensor_mm": C.SENSOR_MM, "pitch_deg": C.CABIN_PITCH_DEG,
        "scale": C.M_PER_UNIT, "sun": list(C.SUN), "sun_color": list(C.SUN_COLOUR), "planetshine": C.PLANETSHINE,
        "blend": str(opts["blend"] or C.LEGION_BLEND), "scrutiny_slot": C.SCRUTINY_SLOT,
        "mount_rest": {s["id"]: mount_rest(s) for s in geom["weapon_slots"]},
        "fighters": [{"hull": f[0], "kind": kind, "deck": f[1],
                      "heading": [(1 if kind == "launch" else -1) * c for c in
                                  next(d for d in geom["flight_decks"] if d["slot"] == f[1])["launch_direction"]]}
                     for kind, group in (("launch", C.LAUNCHES), ("recover", C.RECOVERIES)) for f in group],
        "fighter_art": {k: [str(GAME / v[0]), v[1]] for k, v in FIGHTERS.items()},
        "samples": samples, "cycles_samples": opts["samples"], "only": opts["only"], "resume": opts["resume"],
        "out": str(OUT),
    }
    blend = Path(p["blend"])
    key_src = json.dumps({k: v for k, v in p.items() if k not in ("only", "resume", "out")}, sort_keys=True)
    p["key"] = hashlib.sha1((key_src + str(blend.stat().st_mtime_ns) + Path(__file__).read_text(encoding="utf-8"))
                            .encode()).hexdigest()[:16]
    return p


def plan_and_launch(argv):
    opts = _args(argv)
    p = plan(opts)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "plan.json").write_text(json.dumps(p), encoding="utf-8")
    print("C2 Legion: %d frames, model %s, key %s" % (p["frames"], p["blend"], p["key"]))
    started = time.time()
    subprocess.run([str(BLENDER), "--background", "--factory-startup", "--python-exit-code", "1",
                    "--python", str(Path(__file__).resolve()), "--", "--plan", str(OUT / "plan.json")], check=True)
    print("Rendered in %.0fs" % (time.time() - started))


# ================================================================ Blender

def fighter_mesh(bpy, np, name, sprite):
    """A vanilla fighter sprite as a lit heightfield, bow along mesh +Y, in sprite px (= hull units).
    traffic_render_cards.heightfield's construction (crown from the plan's distance field)."""
    from mathutils import Vector  # noqa: F401
    img = bpy.data.images.load(sprite, check_existing=True)
    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)
    soft = px[..., 3].copy()
    for _ in range(2):
        pad = np.pad(soft, 1, mode="edge")
        soft = (pad[:-2, 1:-1] + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:] + pad[1:-1, 1:-1] * 2) / 6
    solid = soft > 0.5
    dist = np.zeros(solid.shape, np.float32)
    cur = solid.copy()
    while cur.any():
        dist += cur
        nxt = cur.copy()
        nxt[1:] &= cur[:-1]; nxt[:-1] &= cur[1:]; nxt[:, 1:] &= cur[:, :-1]; nxt[:, :-1] &= cur[:, 1:]
        cur = nxt
    lum = px[..., :3] @ np.array([0.3, 0.55, 0.15], np.float32)
    top = min(w, h) * (0.05 + 0.14 * np.sqrt(dist / max(dist.max(), 1)) + 0.02 * lum)
    verts, uvs, faces, index = [], [], [], {}

    def vid(i, j):
        if (i, j) not in index:
            zs = [top[a, b] for a in (i - 1, i) for b in (j - 1, j) if 0 <= a < h and 0 <= b < w and solid[a, b]]
            index[(i, j)] = len(verts)
            verts.append((j - w / 2, i - h / 2, float(max(zs) if zs else 0.0)))
            uvs.append((j / w, i / h))
        return index[(i, j)]

    for i in range(h):
        for j in range(w):
            if solid[i, j]:
                faces.append((vid(i, j), vid(i, j + 1), vid(i + 1, j + 1), vid(i + 1, j)))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    uv = mesh.uv_layers.new(name="UV")
    for poly in mesh.polygons:
        for li in poly.loop_indices:
            uv.data[li].uv = uvs[mesh.loops[li].vertex_index]
    obj = bpy.data.objects.new(name, mesh)
    sm = obj.modifiers.new("round", "SMOOTH")
    sm.factor, sm.iterations = 0.6, 3
    so = obj.modifiers.new("slab", "SOLIDIFY")
    so.thickness, so.offset = min(w, h) * 0.12, -1
    mat = bpy.data.materials.new(name + " hull")
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = nodes["Principled BSDF"]
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = img
    tex.interpolation = "Closest" if max(w, h) < 64 else "Linear"
    hsv = nodes.new("ShaderNodeHueSaturation")
    hsv.inputs["Saturation"].default_value = 0.85
    hsv.inputs["Value"].default_value = 0.9
    links.new(tex.outputs["Color"], hsv.inputs["Color"])
    links.new(hsv.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.5
    bsdf.inputs["Metallic"].default_value = 0.35
    mesh.materials.append(mat)
    return obj


def emission(bpy, name, color, strength):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (*color, 1)
    em.inputs["Strength"].default_value = strength
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(em.outputs[0], out.inputs[0])
    return mat, em


def blender_main(argv):
    import bpy
    import numpy as np
    from mathutils import Matrix, Quaternion, Vector

    opts = _args(argv)
    plan_ = json.loads(Path(opts["plan"]).read_text(encoding="utf-8"))
    out = Path(plan_["out"])
    bpy.ops.wm.open_mainfile(filepath=plan_["blend"])
    scene = bpy.context.scene
    for obj in list(bpy.data.objects):
        if obj.type in ("CAMERA", "LIGHT"):
            bpy.data.objects.remove(obj, do_unlink=True)
    for obj in bpy.data.objects:
        obj.animation_data_clear()
    tops = [o for o in bpy.data.objects if o.parent is None]
    hull = bpy.data.objects.new("C2 | Legion root", None)
    scene.collection.objects.link(hull)
    hull.scale = (plan_["scale"],) * 3
    for o in tops:
        o.parent = hull
        o.matrix_parent_inverse = Matrix.Identity(4)

    # Mounts cold, the scrutiny turret keyed per frame below.
    pivots = {}
    for slot, rest in plan_["mount_rest"].items():
        pv = bpy.data.objects.get("Weapon %s pivot" % slot)
        if pv is not None:
            pv.rotation_euler.z = math.radians(rest)
            pivots[slot] = pv
    scrutiny = pivots.get(plan_["scrutiny_slot"])

    # Legion drive idling.
    throat, _ = emission(bpy, "C2 | Legion throat", *LEGION_THROAT)
    for o in bpy.data.objects:
        if o.type == "MESH" and o.name.startswith("Engine ") and o.name.endswith("dark throat"):
            o.data.materials.clear()
            o.data.materials.append(throat)

    # Running lights at the hull's extremes, measured in hull units.
    def extreme(names, key):
        best = None
        for n in names:
            o = bpy.data.objects.get(n)
            if o is None or o.type != "MESH":
                continue
            mw = hull.matrix_world.inverted() @ o.matrix_world
            for v in o.data.vertices:
                p = mw @ v.co
                if best is None or key(p) > key(best):
                    best = p
        return best

    bpy.context.view_layer.update()
    allmesh = [o.name for o in bpy.data.objects if o.type == "MESH"]
    spots = {
        "starboard tip": extreme(["Wing starboard sponson"], lambda p: -p.y),
        "bow": extreme(allmesh, lambda p: p.x),
        "dome": extreme(["Bridge command dome"], lambda p: p.z),
        "starboard stern": extreme(["Wing starboard sponson"], lambda p: -p.x - 0.3 * p.y),
    }
    lights = []
    for where, color, period, phase in RUNNING_LIGHTS:
        p = spots.get(where)
        if p is None:
            continue
        mat, _ = emission(bpy, "C2 | running light %s" % where, color, 60.0)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.9, location=(0, 0, 0), segments=12, ring_count=6)
        lamp = bpy.context.active_object
        lamp.name = "C2 | running light %s" % where
        lamp.data.materials.append(mat)
        lamp.parent = hull
        lamp.location = p + Vector((0, 0, 0.6))
        lights.append((lamp, period, phase))

    # Fighters: one heightfield per flight, parented to the hull, engines as emissive bells.
    flights = []
    for k, f in enumerate(plan_["fighters"]):
        sprite, engines = plan_["fighter_art"][f["hull"]]
        body = fighter_mesh(bpy, np, "C2 | %s %d" % (f["hull"], k), sprite)
        scene.collection.objects.link(body)
        body.parent = hull
        body.rotation_mode = "QUATERNION"
        mat, node = emission(bpy, "C2 | %s %d flame" % (f["hull"], k), *FIGHTER_FLAME)
        for ex, ey in engines:
            bpy.ops.mesh.primitive_uv_sphere_add(radius=2.4, segments=12, ring_count=6)
            fl = bpy.context.active_object
            fl.name = "C2 | %s %d flame" % (f["hull"], k)
            fl.scale = (1.0, 1.6, 0.8)
            fl.data.materials.append(mat)
            fl.parent = body
            fl.location = (-ey, ex - 1.5, 2.0)       # .ship (x fwd, y port) -> mesh (x starboard, y bow)
        flights.append((body, node))

    # Camera: the cabin camera at the origin, looking out (+Y) and pitched down.
    cam = bpy.data.objects.new("C2 | cabin camera", bpy.data.cameras.new("C2 | cabin camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.rotation_euler = (math.radians(90 - plan_["pitch_deg"]), 0, 0)
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = plan_["lens_mm"], plan_["sensor_mm"], "HORIZONTAL"
    cam.data.clip_start, cam.data.clip_end = 1.0, 1e5

    scene.world = bpy.data.worlds.new("C2 | space")
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (0, 0, 0, 1)
    bg.inputs["Strength"].default_value = 0.0

    def sun_lamp(name, toward, energy, color, angle):
        lamp = bpy.data.objects.new(name, bpy.data.lights.new(name, "SUN"))
        lamp.data.energy, lamp.data.color, lamp.data.angle = energy, color, math.radians(angle)
        lamp.rotation_euler = Vector(toward).normalized().to_track_quat("Z", "Y").to_euler()
        scene.collection.objects.link(lamp)

    sun_lamp("C2 | Corvus", plan_["sun"], SUN_STRENGTH, plan_["sun_color"], 0.5)
    # Jangala fills the lower half of the sky: its shine arrives from below and a little outboard.
    sun_lamp("C2 | Jangala shine", (0.0, 0.25, -1.0), SUN_STRENGTH * plan_["planetshine"], SHINE_COLOR, 50.0)

    # Key every frame.
    fps = plan_["fps"]
    scene.render.fps = fps
    prev_q = [None] * len(flights)
    last_t = [None] * len(flights)
    samples = plan_["samples"]
    for s in samples:
        i = s["index"]
        hull.location = s["origin"]
        hull.keyframe_insert("location", frame=i)
        if scrutiny is not None:
            scrutiny.rotation_euler.z = math.radians(s["scrutiny_deg"])
            scrutiny.keyframe_insert("rotation_euler", index=2, frame=i)
        for lamp, period, phase in lights:
            lamp.hide_render = ((s["t"] + phase) % period) > period * 0.18
            lamp.keyframe_insert("hide_render", frame=i)
        for k, (body, node) in enumerate(flights):
            f = s["fighters"][k]
            body.hide_render = f is None
            body.keyframe_insert("hide_render", frame=i)
            for ch in body.children:
                ch.hide_render = f is None
                ch.keyframe_insert("hide_render", frame=i)
            if f is None:
                continue
            here = Vector(f["pos"])
            # Heading from the path's own tangent (central difference); a fighter slowing to a
            # stop on deck keeps its last heading, and a parked one its deck's launch heading.
            def at(j):
                return Vector(samples[j]["fighters"][k]["pos"]) if 0 <= j < len(samples) and                     samples[j]["fighters"][k] is not None else None
            a_, b_ = at(i - 1), at(i + 1)
            d = (b_ - a_) if a_ is not None and b_ is not None else                 (b_ - here) if b_ is not None else (here - a_) if a_ is not None else Vector()
            if d.length > 0.05:
                tangent = d.normalized()
            elif last_t[k] is not None:
                tangent = last_t[k]
            else:
                tangent = Vector(plan_["fighters"][k]["heading"])
            last_t[k] = tangent
            q = tangent.to_track_quat("Y", "Z")
            if prev_q[k] is not None and prev_q[k].dot(q) < 0:
                q.negate()
            prev_q[k] = q
            body.location = here
            body.rotation_quaternion = q
            body.keyframe_insert("location", frame=i)
            body.keyframe_insert("rotation_quaternion", frame=i)
            node.inputs["Strength"].default_value = FIGHTER_FLAME[1] * f["throttle"]
            node.inputs["Strength"].keyframe_insert("default_value", frame=i)
    for o in bpy.data.objects:
        ad = o.animation_data
        if ad and ad.action:
            for fc in _fcurves(ad.action):
                for kp in fc.keyframe_points:
                    kp.interpolation = "CONSTANT" if fc.data_path == "hide_render" else "LINEAR"

    scene.render.engine = "CYCLES"
    scene.cycles.samples = int(plan_["cycles_samples"])
    scene.cycles.use_denoising = True
    if os.environ.get("C1_CYCLES_CPU"):
        scene.cycles.device = "CPU"
    else:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        if not [d for d in prefs.devices if d.type == "OPTIX"]:
            raise RuntimeError("OPTIX GPU required (set C1_CYCLES_CPU=1 to choose CPU)")
        for device in prefs.devices:
            device.use = device.type == "OPTIX"
        scene.cycles.device = "GPU"
    scene.render.use_persistent_data = True
    scene.render.use_motion_blur = True
    scene.render.motion_blur_shutter = 0.5
    scene.render.resolution_x, scene.render.resolution_y = plan_["width"], plan_["height"]
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.use_compositing = False
    scene.view_settings.view_transform = "AgX"

    lo_i, hi_i = 0, plan_["frames"]
    if plan_.get("only"):
        a, b = plan_["only"].split(":")
        lo_i, hi_i = int(a), int(b)
    meta_path = out / "meta.json"
    done = set()
    if plan_.get("resume") and meta_path.exists():
        prev = json.loads(meta_path.read_text(encoding="utf-8"))
        if prev.get("key") == plan_["key"]:
            done = set(prev.get("rendered", []))
    times = []
    for i in range(lo_i, hi_i):
        name = out / ("f_%05d.png" % i)
        if i in done and name.exists():
            continue
        scene.frame_set(i)
        scene.render.filepath = str(name)
        t0 = time.time()
        bpy.ops.render.render(write_still=True)
        times.append(time.time() - t0)
        done.add(i)
        print("frame %d %.1fs" % (i, times[-1]), flush=True)
        if len(times) % 12 == 0:
            _meta(meta_path, plan_, done)
    _meta(meta_path, plan_, done)
    if times:
        print("Render: %d frames, mean %.2fs, max %.2fs" % (len(times), sum(times) / len(times), max(times)))


def _fcurves(action):
    """Blender 5 keeps F-curves in layered actions (layers > strips > channelbags)."""
    if hasattr(action, "fcurves") and len(getattr(action, "fcurves", [])):
        return list(action.fcurves)
    out = []
    for layer in getattr(action, "layers", []):
        for strip in layer.strips:
            for bag in getattr(strip, "channelbags", []):
                out += list(bag.fcurves)
    return out


def _meta(path, plan_, done):
    path.write_text(json.dumps({"key": plan_["key"], "blend": plan_["blend"], "frames": plan_["frames"],
                                "samples": plan_["cycles_samples"], "rendered": sorted(done)}), encoding="utf-8")


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        plan_and_launch(argv)
