"""C2 from Second Wind's bridge, lit: Cycles frames of the pass c2_bridge_blockout.py plans.

    python c2_bridge_render.py [--fps 24] [--frames 0,72,192] [--samples 64] [--resume]
                               [--only A:B] [--out DIR]
    python c2_bridge_render.py --movie [--out DIR]     # c2-bridge.mp4 with captions and cues

The motion is the blockout's (`c2_bridge_blockout`: the Legion's track and bank, the
Broadsword screen, the bridge camera's head turn, Second Wind's nose-down pitch into the
de-orbit), imported, so the sketch and the render cannot disagree. This script only dresses
and lights it:

- Second Wind is the patched state (`second-wind-states/patched`, appended collection, as C1
  and C5 use it), under a ship frame that carries the bridge interior and the camera, so
  pitching the ship pitches all three together against a fixed sun and planet.
- The command deck's interior is the model's own (second-wind-geometry/sw_bridge_interior),
  so the patched state export must have been rebuilt since it landed; the script refuses a
  state without it. The pilot and Castle are added (sw_bridge_interior.figures). The glazing
  is hidden from camera and shadow rays: it is opaque paint, not glass.
- The Legion and its Broadswords keep their own materials; the Legion's throats and running
  lights and the fighters' flames are added as in render_patrol_sequence.py.
- Jangala is a sphere scaled about the eye (it looks identical from there), shaded by its own
  normal against the sun so nothing nearby can shadow it, with C1's maps and a thin limb glow.
  It turns at the true orbital rate, aft.
- One sun, c2_core.SUN at C1's strength, and Jangala's planet-shine from below. No world light:
  the sky holds a procedural star field that only camera rays see.

Output: build/cinematic/c2-bridge/lit/f_<frame>.png (1920x1080 RGBA... opaque), meta.json.
GPU: OptiX by default; C1_CYCLES_CPU=1 renders on the CPU.
"""
import json
import math
import os
import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
OUT = ROOT / "build" / "cinematic" / "c2-bridge" / "lit"
sys.path.insert(0, str(BASE))
sys.path.insert(0, str(BASE.parents[2]))
from blender_path import BLENDER  # noqa: E402
import c2_bridge_blockout as BB  # noqa: E402

SW_STATE_BLEND = BASE.parent / "second-wind-states" / "patched" / "second-wind-patched.blend"
SW_COLLECTION = "SECOND WIND | patched"
SW_ROOT = "SECOND WIND | patched | root"
C1_ASSETS = BASE.parent / "corvus-approach" / "assets"
SUN_STRENGTH = 4.5                        # C1's key, as render_patrol_sequence.py
SHINE_COLOR = (0.55, 0.75, 1.0)
LEGION_THROAT = ((1.0, 0.62, 0.30), 2.5)
FIGHTER_FLAME = ((1.0, 0.70, 0.38), 40.0)
PLANET_K = 1.0 / 2000.0                    # planet proxy scale about the eye
MAP_SPAN_DEG = (100.0, 50.0)               # C1's detail maps cover this much arc
STAR_SCALE = 300.0                         # Voronoi cells per unit of sky direction (a cell is about 4 px)
STAR_SHARE = 0.035                         # share of cells holding a star
STAR_FLOOR, STAR_GAIN = 0.05, 2.5          # faintest star, and the brightest's strength


def plan(frames, fps, samples_n):
    import c2_core as C
    times = [f / fps for f in frames]
    base = BB.plan(times, [1920, 1080])
    for s, f in zip(base["samples"], frames):
        s["frame"] = f
        s["ship_pitch"] = BB.ship_pitch_deg(s["t"])
    lg = C.LEGION_GEOM
    base.update({
        "fps": fps, "cycles_samples": samples_n, "sw_state_blend": str(SW_STATE_BLEND),
        "albedo": str(C1_ASSETS / "planet-albedo-detail-v1.png"),
        "clouds": str(C1_ASSETS / "planet-clouds-detail-v1.png"),
        "running_lights": lg.get("running_lights", []), "orbital_speed": C.ORBITAL_SPEED,
        "planetshine": C.PLANETSHINE,
        "eye_m": [c * BB.SW_MPU for c in BB.STATION_FORWARD],
    })
    return base


def movie(out_dir):
    """Review mp4: the lit frames with captions burnt in and C2's own cues mixed (compose_c2)."""
    import tempfile
    from PIL import Image
    import compose_c2 as K
    fs = sorted(Path(out_dir).glob("f_*.png"))
    caps = K.review_pages_between(K.C.C2_START, K.C.ENTRY_START)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        for k, f in enumerate(fs):
            t = int(f.stem[2:]) / K.FPS
            K.burn_captions(Image.open(f).convert("RGB"), t, caps).save(tmp / ("%05d.png" % k), compress_level=1)
        K.review_audio(K.audio_cues(), K.DURATION, tmp / "mix.m4a", bed_from=K.C.C2_START)
        target = Path(out_dir) / "c2-bridge.mp4"
        subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(K.FPS),
                        "-i", str(tmp / "%05d.png"), "-i", str(tmp / "mix.m4a"), "-map", "0:v", "-map", "1:a",
                        "-c:v", "libx264", "-crf", "17", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k",
                        "-shortest", str(target)], check=True)
    print("Wrote", target)


def launch(argv):
    if "--movie" in argv:
        i = argv.index("--out") if "--out" in argv else None
        movie(Path(argv[i + 1]) if i is not None else OUT)
        return
    fps, frames, samples_n, resume, only, out = 24.0, None, 64, False, None, OUT
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--resume":
            resume = True
            i += 1
            continue
        v = argv[i + 1]
        if a == "--fps":
            fps = float(v)
        elif a == "--frames":
            frames = [int(x) for x in v.split(",")]
        elif a == "--samples":
            samples_n = int(v)
        elif a == "--only":
            only = v
        elif a == "--out":
            out = Path(v)
        i += 2
    import c2_core as C
    n = int(round(C.C2_LEN * fps))
    render = None
    if frames is not None:                          # test frames: key their neighbours for the blur
        render = frames
        frames = sorted({g for f in frames for g in (f - 1, f, f + 1) if g >= 0})
    else:
        frames = list(range(n + 1))                 # one past the end for motion blur
        if only:
            a, b = (int(x) for x in only.split(":"))
            frames = list(range(a, min(b + 1, n + 1)))
    p = plan(frames, fps, samples_n)
    p["out"], p["resume"] = str(out), resume
    p["render"] = render if render is not None else [f for f in frames if f < n]
    out.mkdir(parents=True, exist_ok=True)
    tag = "" if not only else "-" + only.replace(":", "-")
    pp = out / ("plan%s.json" % tag)
    pp.write_text(json.dumps(p), encoding="utf-8")
    t0 = time.time()
    subprocess.run([str(BLENDER), "--background", "--factory-startup", "--python-exit-code", "1",
                    "--python", str(Path(__file__).resolve()), "--", "--plan", str(pp)], check=True)
    print("Rendered in %.0fs" % (time.time() - t0))


# ================================================================ Blender

def blender_main(argv):
    import bpy
    from mathutils import Euler, Matrix, Vector

    p = json.loads(Path(argv[argv.index("--plan") + 1]).read_text(encoding="utf-8"))
    out = Path(p["out"])
    scene = bpy.context.scene
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    fps = p["fps"]
    scene.render.fps = int(round(fps))

    def emission(name, color, strength):
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        nt = mat.node_tree
        nt.nodes.clear()
        em = nt.nodes.new("ShaderNodeEmission")
        em.inputs["Color"].default_value = (*color, 1)
        em.inputs["Strength"].default_value = strength
        o_ = nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(em.outputs[0], o_.inputs[0])
        return mat, em

    def paint(name, rgb, rough=0.6, metal=0.3):
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        bs = mat.node_tree.nodes["Principled BSDF"]
        bs.inputs["Base Color"].default_value = (*rgb, 1)
        bs.inputs["Roughness"].default_value = rough
        bs.inputs["Metallic"].default_value = metal
        return mat

    # ---- the ship frame: Second Wind, its bridge interior and the camera pitch together.
    ship = bpy.data.objects.new("C2B | ship frame", None)
    scene.collection.objects.link(ship)
    with bpy.data.libraries.load(p["sw_state_blend"], link=False) as (src, dst):
        dst.collections = [SW_COLLECTION]
    scene.collection.children.link(dst.collections[0])
    sw = bpy.data.objects[SW_ROOT]
    if abs(sw.get("metres_per_unit", p["sw_mpu"]) - p["sw_mpu"]) > 1e-9:
        raise RuntimeError("Second Wind state is %s m/unit" % sw.get("metres_per_unit"))
    sw.parent = ship
    sw.matrix_parent_inverse = Matrix.Identity(4)
    sw.scale = (p["sw_mpu"],) * 3
    for o in [sw, *sw.children_recursive]:
        o.animation_data_clear()
        if o.name.startswith("Command deck viewport slits"):
            o.visible_camera = False                # the glazing is not see-through glass
            o.visible_shadow = False
    if not any(o.name.startswith("Bridge deck plating") for o in sw.children_recursive):
        raise RuntimeError("%s has no bridge interior: rebuild the patched state export" % p["sw_state_blend"])
    # The model's ceiling lamps are the room's light; an area lamp under them carries it.
    dome = bpy.data.objects.new("C2B | bridge ceiling light", bpy.data.lights.new("C2B | bridge ceiling light", "AREA"))
    dome.data.shape, dome.data.size, dome.data.size_y = "RECTANGLE", 2.6, 3.6
    dome.data.energy, dome.data.color = 25.0, (1.0, 0.82, 0.6)
    scene.collection.objects.link(dome)
    dome.parent = sw
    dome.location = (13.2, 0, 20.1)
    sys.path.insert(0, p["sw_dir"])
    import sw_bridge_interior
    sw_bridge_interior.figures(p["sw_mpu"], parent=sw)

    cam = bpy.data.objects.new("C2B | bridge camera", bpy.data.cameras.new("C2B | bridge camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.parent = ship
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = p["lens_mm"], 36.0, "HORIZONTAL"
    cam.data.clip_start, cam.data.clip_end = 0.05, 1.0e5

    # ---- the Legion, with its throats and running lights.
    with bpy.data.libraries.load(p["legion_blend"], link=False) as (src, dst2):
        dst2.objects = list(src.objects)
    lroot = bpy.data.objects.new("C2B | Legion root", None)
    scene.collection.objects.link(lroot)
    legion = [o for o in dst2.objects if o is not None and o.type not in ("CAMERA", "LIGHT")]
    for o in legion:
        scene.collection.objects.link(o)
        o.animation_data_clear()
    for o in legion:
        if o.parent is None:
            o.parent = lroot
            o.matrix_parent_inverse = Matrix.Identity(4)
    lroot.scale = (p["legion_mpu"],) * 3
    lroot.rotation_euler = Euler((math.radians(p["roll"]), 0, math.radians(p["yaw"])), "XYZ")
    throat, _ = emission("C2B | Legion throat", *LEGION_THROAT)
    for o in legion:
        if o.type == "MESH" and o.name.startswith("Engine ") and o.name.split(".")[0].endswith("dark throat"):
            o.data.materials.clear()
            o.data.materials.append(throat)
    lights = []
    for rl in p["running_lights"]:
        mat, _ = emission("C2B | %s" % rl["name"], rl["rgb"], 60.0)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.9, segments=12, ring_count=6)
        lamp = bpy.context.active_object
        lamp.data.materials.append(mat)
        lamp.parent = lroot
        lamp.location = rl["position"]
        bl = rl["blink"]
        lights.append((lamp, bl["period_s"], bl["phase_s"], bl.get("duty", 0.18)))

    # ---- Broadswords, one copy of the model's root tree each, flames at full throttle.
    with bpy.data.libraries.load(p["broadsword_blend"], link=False) as (src, dst3):
        dst3.collections = [c for c in src.collections if c == "BROADSWORD | geometry"]
    proto = next(o for o in dst3.collections[0].objects if o.name.startswith("Broadsword root"))
    flame, _ = emission("C2B | Broadsword flame", *FIGHTER_FLAME)

    def clone(obj, parent):
        c = obj.copy()
        scene.collection.objects.link(c)
        c.parent = parent
        c.matrix_parent_inverse = obj.matrix_parent_inverse.copy()
        for ch in obj.children:
            clone(ch, c)
        return c

    fighters = []
    for k in range(len(p["samples"][0]["fighters"])):
        f = clone(proto, None)
        f.name = "C2B | Broadsword %d" % k
        f.scale = (p["fighter_mpu"],) * 3
        for ch in f.children_recursive:
            if ch.type == "MESH" and "dark throat" in ch.name:
                ch.data = ch.data.copy()
                ch.data.materials.clear()
                ch.data.materials.append(flame)
            if ch.type == "EMPTY" and "exhaust anchor" in ch.name:
                bpy.ops.mesh.primitive_uv_sphere_add(radius=1.6, segments=12, ring_count=6)
                fl = bpy.context.active_object
                fl.data.materials.append(flame)
                fl.parent = ch
                fl.location = (1.4, 0, 0)
                fl.scale = (1.8, 0.8, 0.8)
        fighters.append(f)

    # ---- Jangala: proxy sphere about the eye, self-shaded against the sun, C1's maps.
    eye = Vector(p["eye_m"])
    R = p["planet_radius_m"] * PLANET_K
    centre = eye + Vector((0, 0, -(p["planet_radius_m"] + p["altitude_m"] + eye.z) * PLANET_K))
    bpy.ops.mesh.primitive_uv_sphere_add(radius=R, segments=512, ring_count=256, location=centre)
    planet = bpy.context.active_object
    planet.name = "C2B | Jangala"
    bpy.ops.object.shade_smooth()
    planet.visible_shadow = False
    sun_v = Vector(p["sun"]).normalized()
    mat = bpy.data.materials.new("C2B | Jangala")
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    N = nt.nodes.new
    L = nt.links.new
    tc = N("ShaderNodeTexCoord")
    sep = N("ShaderNodeSeparateXYZ")
    L(tc.outputs["Object"], sep.inputs[0])
    lon = N("ShaderNodeMath"); lon.operation = "ARCTAN2"
    L(sep.outputs["X"], lon.inputs[0]); L(sep.outputs["Z"], lon.inputs[1])
    lat = N("ShaderNodeMath"); lat.operation = "ARCTAN2"
    L(sep.outputs["Y"], lat.inputs[0]); L(sep.outputs["Z"], lat.inputs[1])
    uu = N("ShaderNodeMath"); uu.operation = "MULTIPLY_ADD"
    L(lon.outputs[0], uu.inputs[0]); uu.inputs[1].default_value = 1.0 / math.radians(MAP_SPAN_DEG[0]); uu.inputs[2].default_value = 0.5
    vv = N("ShaderNodeMath"); vv.operation = "MULTIPLY_ADD"
    L(lat.outputs[0], vv.inputs[0]); vv.inputs[1].default_value = 1.0 / math.radians(MAP_SPAN_DEG[1]); vv.inputs[2].default_value = 0.5
    uv = N("ShaderNodeCombineXYZ")
    L(uu.outputs[0], uv.inputs["X"]); L(vv.outputs[0], uv.inputs["Y"])
    alb = N("ShaderNodeTexImage"); alb.image = bpy.data.images.load(p["albedo"]); alb.extension = "MIRROR"
    clo = N("ShaderNodeTexImage"); clo.image = bpy.data.images.load(p["clouds"]); clo.extension = "MIRROR"
    L(uv.outputs[0], alb.inputs[0]); L(uv.outputs[0], clo.inputs[0])
    cl_bw = N("ShaderNodeRGBToBW"); L(clo.outputs["Color"], cl_bw.inputs[0])
    mix = N("ShaderNodeMix"); mix.data_type = "RGBA"
    L(cl_bw.outputs[0], mix.inputs["Factor"]); L(alb.outputs["Color"], mix.inputs["A"])
    mix.inputs["B"].default_value = (0.92, 0.94, 0.96, 1)
    geo = N("ShaderNodeNewGeometry")
    dot = N("ShaderNodeVectorMath"); dot.operation = "DOT_PRODUCT"
    L(geo.outputs["Normal"], dot.inputs[0]); dot.inputs[1].default_value = tuple(sun_v)
    lit = N("ShaderNodeMath"); lit.operation = "MAXIMUM"; lit.inputs[1].default_value = 0.0
    L(dot.outputs["Value"], lit.inputs[0])
    gain = N("ShaderNodeMath"); gain.operation = "MULTIPLY"; gain.inputs[1].default_value = SUN_STRENGTH / math.pi
    L(lit.outputs[0], gain.inputs[0])
    em = N("ShaderNodeEmission")
    L(mix.outputs["Result"], em.inputs["Color"]); L(gain.outputs[0], em.inputs["Strength"])
    # Limb: a thin blue glow where the view grazes the sphere.
    lw = N("ShaderNodeLayerWeight"); lw.inputs["Blend"].default_value = 0.12
    rim = N("ShaderNodeEmission"); rim.inputs["Color"].default_value = (0.35, 0.6, 1.0, 1)
    rimg = N("ShaderNodeMath"); rimg.operation = "MULTIPLY"
    L(lw.outputs["Facing"], rimg.inputs[0]); L(gain.outputs[0], rimg.inputs[1])
    L(rimg.outputs[0], rim.inputs["Strength"])
    add = N("ShaderNodeAddShader")
    L(em.outputs[0], add.inputs[0]); L(rim.outputs[0], add.inputs[1])
    mo = N("ShaderNodeOutputMaterial")
    L(add.outputs[0], mo.inputs[0])
    planet.data.materials.append(mat)
    omega = p["orbital_speed"] / (p["planet_radius_m"] + p["altitude_m"])   # rad/s, aft

    # ---- light: one sun and Jangala's shine. No world light.
    # Stars (owner, 2026-10-18: C1 shows them, so C2 does too). A procedural field on the sky
    # itself, so it turns with the head and pitches with the ship; seen by camera rays only,
    # so it lights nothing. One Voronoi point per cell, about 3% of cells lit, brightness
    # weighted heavily toward faint, a slight warm-to-blue tint.
    scene.world = bpy.data.worlds.new("C2B | space")
    scene.world.use_nodes = True
    wn = scene.world.node_tree
    wn.nodes.clear()
    W, WL = wn.nodes.new, wn.links.new
    wtc = W("ShaderNodeTexCoord")
    vor = W("ShaderNodeTexVoronoi"); vor.voronoi_dimensions = "3D"
    vor.inputs["Scale"].default_value = STAR_SCALE
    vor.inputs["Randomness"].default_value = 1.0
    WL(wtc.outputs["Generated"], vor.inputs["Vector"])
    dot_ = W("ShaderNodeMapRange")
    WL(vor.outputs["Distance"], dot_.inputs["Value"])
    dot_.inputs["From Min"].default_value, dot_.inputs["From Max"].default_value = 0.05, 0.22
    dot_.inputs["To Min"].default_value, dot_.inputs["To Max"].default_value = 1.0, 0.0
    rnd = W("ShaderNodeSeparateColor")
    WL(vor.outputs["Color"], rnd.inputs[0])
    gate = W("ShaderNodeMath"); gate.operation = "GREATER_THAN"; gate.inputs[1].default_value = 1.0 - STAR_SHARE
    WL(rnd.outputs["Red"], gate.inputs[0])
    mag = W("ShaderNodeMath"); mag.operation = "POWER"; mag.inputs[1].default_value = 5.0
    WL(rnd.outputs["Green"], mag.inputs[0])
    m1 = W("ShaderNodeMath"); m1.operation = "MULTIPLY"
    WL(dot_.outputs["Result"], m1.inputs[0]); WL(gate.outputs[0], m1.inputs[1])
    m2 = W("ShaderNodeMath"); m2.operation = "MULTIPLY"
    WL(m1.outputs[0], m2.inputs[0]); WL(mag.outputs[0], m2.inputs[1])
    m3 = W("ShaderNodeMath"); m3.operation = "MULTIPLY_ADD"
    WL(m1.outputs[0], m3.inputs[0]); m3.inputs[1].default_value = STAR_FLOOR
    WL(m2.outputs[0], m3.inputs[2])
    tint = W("ShaderNodeMix"); tint.data_type = "RGBA"
    WL(rnd.outputs["Blue"], tint.inputs["Factor"])
    tint.inputs["A"].default_value, tint.inputs["B"].default_value = (1.0, 0.86, 0.72, 1), (0.75, 0.85, 1.0, 1)
    bgs = W("ShaderNodeBackground")
    WL(tint.outputs["Result"], bgs.inputs["Color"])
    gain_ = W("ShaderNodeMath"); gain_.operation = "MULTIPLY"; gain_.inputs[1].default_value = STAR_GAIN
    WL(m3.outputs[0], gain_.inputs[0]); WL(gain_.outputs[0], bgs.inputs["Strength"])
    lp = W("ShaderNodeLightPath")
    black = W("ShaderNodeBackground"); black.inputs["Strength"].default_value = 0.0
    mixs = W("ShaderNodeMixShader")
    WL(lp.outputs["Is Camera Ray"], mixs.inputs["Fac"])
    WL(black.outputs[0], mixs.inputs[1]); WL(bgs.outputs[0], mixs.inputs[2])
    wo = W("ShaderNodeOutputWorld")
    WL(mixs.outputs[0], wo.inputs["Surface"])

    def sun_lamp(name, toward, energy, color, angle):
        lamp = bpy.data.objects.new(name, bpy.data.lights.new(name, "SUN"))
        lamp.data.energy, lamp.data.color, lamp.data.angle = energy, color, math.radians(angle)
        lamp.rotation_euler = Vector(toward).normalized().to_track_quat("Z", "Y").to_euler()
        scene.collection.objects.link(lamp)

    sun_lamp("C2B | Corvus", p["sun"], SUN_STRENGTH, (1.0, 0.97, 0.9), 0.5)
    sun_lamp("C2B | Jangala shine", (0.0, 0.25, -1.0), SUN_STRENGTH * p["planetshine"], SHINE_COLOR, 50.0)

    # ---- key every sample.
    lmid = Vector(p["samples"][0]["legion_mid_u"])
    rot = lroot.rotation_euler.to_matrix()
    for s in p["samples"]:
        fr = s["frame"]
        ship.rotation_euler = (0, math.radians(s["ship_pitch"]), 0)
        ship.keyframe_insert("rotation_euler", frame=fr)
        cam.location = Vector(s["cam_pos_u"]) * p["sw_mpu"]
        cam.rotation_euler = (math.radians(90 + s["cam_pitch"]), 0, math.radians(s["cam_yaw"] - 90))
        cam.keyframe_insert("location", frame=fr)
        cam.keyframe_insert("rotation_euler", frame=fr)
        lroot.location = Vector(s["legion_centre_m"]) - rot @ (lmid * p["legion_mpu"])
        lroot.keyframe_insert("location", frame=fr)
        for f, (pos, hdg, bank) in zip(fighters, s["fighters"]):
            f.location = pos
            f.rotation_euler = (math.radians(bank), 0, math.radians(hdg))
            f.keyframe_insert("location", frame=fr)
            f.keyframe_insert("rotation_euler", frame=fr)
        planet.rotation_euler = (0, -omega * s["t"], 0)
        planet.keyframe_insert("rotation_euler", frame=fr)
        for lamp, period, phase, duty in lights:
            lamp.hide_render = ((s["t"] + phase) % period) > period * duty
            lamp.keyframe_insert("hide_render", frame=fr)
    for o in bpy.data.objects:
        ad = o.animation_data
        if ad and ad.action:
            for fc in _fcurves(ad.action):
                for kp in fc.keyframe_points:
                    kp.interpolation = "CONSTANT" if fc.data_path == "hide_render" else "LINEAR"

    scene.render.engine = "CYCLES"
    scene.cycles.samples = int(p["cycles_samples"])
    scene.cycles.use_denoising = True
    if os.environ.get("C1_CYCLES_CPU"):
        scene.cycles.device = "CPU"
    else:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "OPTIX"
        prefs.get_devices()
        if not [d for d in prefs.devices if d.type == "OPTIX"]:
            raise RuntimeError("OPTIX GPU required (set C1_CYCLES_CPU=1 to choose CPU)")
        for d in prefs.devices:
            d.use = d.type == "OPTIX"
        scene.cycles.device = "GPU"
    scene.render.use_persistent_data = True
    scene.render.use_motion_blur = True
    scene.render.motion_blur_shutter = 0.5
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.view_settings.view_transform = "AgX"

    meta_path = out / "meta.json"
    done = set()
    if p.get("resume") and meta_path.exists():
        done = set(json.loads(meta_path.read_text(encoding="utf-8")).get("rendered", []))
    times = []
    for fr in p["render"]:
        name = out / ("f_%05d.png" % fr)
        if fr in done and name.exists():
            continue
        scene.frame_set(fr)
        scene.render.filepath = str(name)
        t0 = time.time()
        bpy.ops.render.render(write_still=True)
        times.append(time.time() - t0)
        done.add(fr)
        print("frame %d %.1fs" % (fr, times[-1]), flush=True)
        meta_path.write_text(json.dumps({"rendered": sorted(done), "samples": p["cycles_samples"]}), encoding="utf-8")
    if times:
        print("Render: %d frames, mean %.1fs" % (len(times), sum(times) / len(times)))


def _fcurves(action):
    if hasattr(action, "fcurves") and len(getattr(action, "fcurves", [])):
        return list(action.fcurves)
    out = []
    for layer in getattr(action, "layers", []):
        for strip in layer.strips:
            for bag in getattr(strip, "channelbags", []):
                out += list(bag.fcurves)
    return out


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        launch(argv)
