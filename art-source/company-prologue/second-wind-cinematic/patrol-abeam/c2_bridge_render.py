"""C2 from Second Wind's bridge, lit: Cycles frames of the pass c2_bridge_blockout.py plans.

    python c2_bridge_render.py [--fps 24] [--frames 0,72,192] [--samples 64] [--resume]
                               [--only A:B] [--out DIR] [--state patched|derelict|...] [--half] [--no-bulkhead]
    python c2_bridge_render.py --movie [--out DIR]     # c2-bridge.mp4 with captions and cues

The motion is the blockout's (`c2_bridge_blockout`: the Legion's track and bank, the
Broadsword screen, the bridge camera's head turn, Second Wind's nose-down pitch into the
de-orbit), imported, so the sketch and the render cannot disagree. This script only dresses
and lights it:

- Second Wind is the patched state of the pinned build SW_PIN (manifest in `../pins/`; the .blend is
  copied from Drive into `second-wind-states/patched`), appended as a collection as C1 and C5 use it,
  under a ship frame that carries the bridge interior and the camera, so
  pitching the ship pitches all three together against a fixed sun and planet.
- The command deck's interior is the model's own (second-wind-geometry/sw_bridge_interior),
  so the patched state export must have been rebuilt since it landed; the script refuses a
  state without it. The pilot and Castle are added (sw_bridge_interior.figures). The glazing
  is the model's own clear glass (sw_kit.glass): the camera looks through it and catches the
  room's reflections in it, and shadow rays pass it, so the sun still reaches the room.
- The Legion is the finished, textured asset (legion-cinematic: the LEGION collection with
  the optional SWFX effects in the same load), moved by its root only. Its ten drives idle on
  the kit's LOW_TECH plumes (the patrol coasts) with the exhaust haze off (vacuum); its 27
  running lights blink on their own data, lens and kit lamp together. The mounts stay cold.
- The Broadswords are the finished asset (broadsword-cinematic: BROADSWORD plus its SWFX, one
  load per fighter), gear up, drives at full throttle, guns cold, running lights on.
- Jangala is a sphere scaled about the eye (it looks identical from there), shaded by its own
  normal against the sun so nothing nearby can shadow it, with C1's maps and a thin limb glow.
  It turns at the true orbital rate, aft.
- One sun, c2_core.SUN at C1's strength, and Jangala's planet-shine from below. No world light:
  the sky holds a procedural star field that only camera rays see.
- c2_bulkhead's dressing, which frames the slits as windows cut through an armoured bulkhead (owner,
  2026-10-20: in the final). `--no-bulkhead`, or C2_BULKHEAD_DETAIL=0, leaves it out for a control.
- Inside, the bridge's own practicals (bridge_practicals): the two ceiling lamp strips, a soft
  screen-coloured glow off each console toward its operator, and the overhead amber display's
  spill on the helm seat. They are ship's power, so `--state derelict` has none.

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

SW_STATE = "patched"                      # C2's; --state picks another for a lighting check
# The Second Wind build this shot renders: its manifest, copied from Drive builds/second-wind/<commit>/
# and committed under ../pins/. The shot is pinned to it and does not follow second-wind-states/'s
# manifest, which moves with every new candidate build; adopting a new build means changing this.
SW_PIN = "dc62710dc"
C1_ASSETS = BASE.parent / "corvus-approach" / "assets"
SUN_STRENGTH = 4.5                        # C1's key, as render_patrol_sequence.py
SHINE_COLOR = (0.55, 0.75, 1.0)
LEGION_IDLE = 0.2                          # SWFX master throttle: the patrol coasts (as its thrust marker)
PLANET_K = 1.0 / 2000.0                    # planet proxy scale about the eye
MAP_SPAN_DEG = (100.0, 50.0)               # C1's detail maps cover this much arc
STAR_SCALE = 300.0                         # Voronoi cells per unit of sky direction (a cell is about 4 px)
STAR_SHARE = 0.035                         # share of cells holding a star
STAR_FLOOR, STAR_GAIN = 0.05, 2.5          # faintest star, and the brightest's strength

# Bridge practicals, in hull units (sw_bridge_interior's coordinates; the lights ride the root).
# Every one is ship's power: sw_damage's 'ship-power' scar is open only in 'derelict', where every
# emissive part is dark, so there none of these exist. Area lights are hidden from camera rays:
# a floating rectangle in shot would read as a pane of light, not as the screen that makes it.
CEILING_STRIPS = [((13.2, y, 20.1), (2.5, 0.45)) for y in (-1.6, 1.6)]   # under the two caged lamps
CEILING_ENERGY, CEILING_RGB = 24.0, (1.0, 0.82, 0.6)                       # W per strip
# A soft overhead fill over the forward station (owner, 2026-10-19: the consoles and frames should
# read and the fighters be easy to find, the view outside still the brightest thing in shot). It
# lights the room without being seen in the glass itself (hidden from glossy rays).
ROOM_FILL = ((14.8, 0.0, 20.0), (5.0, 6.0), 36.0, (1.0, 0.9, 0.78))         # loc, size, W, rgb
# The slits' glass reflects at the model's own strength (owner, 2026-10-20: turning it down to a
# faint 1% and 35% was a mistake, the slits stopped reading as windows). The glazing keeps its
# Fresnel IOR 1.9 (about 10% straight on); GLASS_REFLECT scales the whole reflection if it is
# ever tuned again.
GLASS_MATERIAL, GLASS_FRESNEL_IOR, GLASS_REFLECT = "Bridge smoked viewport glazing", 1.9, 1.0
SCREEN_RGB = (0.22, 0.62, 0.5)                                            # 'Bridge display' emission
CONSOLE_TOP = 18.25
# (name, footprint centre, yaw_deg, screens (u along the run, width)) as sw_bridge_interior.build
# lays the banks out; the operator stands toward (sin yaw, -cos yaw).
BANKS = [
    ("forward console", (17.43, 0.0), -90.0, [(-2.0, .9), (-.7, .9), (.7, .9), (2.0, .9)]),
    ("port chamfer console", (16.45, 3.36), -45.0, [(0, 1.0)]),
    ("starboard chamfer console", (16.45, -3.36), -135.0, [(0, 1.0)]),
    ("port comms bank", (12.75, 4.2), 0.0, [(-1.6, .9), (0, 1.1), (1.6, .9)]),
    ("starboard systems bank", (12.75, -4.2), 180.0, [(-1.6, .9), (0, 1.1), (1.6, .9)]),
]
SCREEN_GLOW_W = 2.5                        # W per hull unit of lit screen run
SCREEN_GLOW_OUT, SCREEN_GLOW_UP = 0.3, 0.3 # in front of the screens, and above them
SCREEN_GLOW_SPREAD = 70.0                  # deg: toward the operator, not across the deck
# The overhead amber display (16.5, 0, 19.94) down onto the helm seat (16.3, -1.7).
HELM_FILL = ((16.45, -1.2, 19.88), (0.5, 0.9), (16.3, -1.7, 18.4), 8.0, (0.9, 0.45, 0.12))


# What the bridge's screens show (owner, 2026-10-19: "plausible things on these screens"): Codex
# ImageGen pages under assets/bridge-screens/, briefs in prompts/bridge-screen-*.txt. Per screen
# object: (page, crop centre u, v, crop scale), the crop being the largest window of the screen's
# own aspect that fits the page, scaled; so one page can serve two screens without repeating.
# 'face' says which side of the object's local box is the screen and which way is up on it.
SCREEN_PAGES = {
    "Bridge display forward console 1": ("traffic", .5, .5, 1.0),
    "Bridge display forward console 2": ("nav", .5, .5, 1.0),
    "Bridge display forward console 3": ("flight", .5, .5, 1.0),
    "Bridge display forward console 4": ("systems", .5, .3, 1.0),
    "Bridge display port chamfer console 1": ("sensors", .5, .5, 1.0),
    "Bridge display starboard chamfer console 1": ("traffic", .78, .5, 1.0),
    "Bridge display port comms bank 1": ("comms", .5, .5, 1.0),
    "Bridge display port comms bank 2": ("sensors", .3, .5, .75),
    "Bridge display port comms bank 3": ("comms", .72, .4, .65),
    "Bridge display starboard systems bank 1": ("systems", .28, .45, .62),
    "Bridge display starboard systems bank 2": ("systems", .5, .5, 1.0),
    "Bridge display starboard systems bank 3": ("systems", .74, .6, .62),
    "Bridge display salvaged": ("flight", .5, .5, .55),
    "Bridge display plot": ("plot", .5, .5, 1.0),
    "Bridge display overhead": ("overhead", .5, .5, 1.0),
}
SCREEN_FACE = {"Bridge display salvaged": "aft", "Bridge display plot": "up", "Bridge display overhead": "down"}
SCREEN_STRENGTH = 3.0                       # emission of a page's brightest pixel
SCREEN_DIR = BASE / "assets" / "bridge-screens"
SCREEN_VERSION = {"plot": 2, "systems": 2}  # v2: the ship's and planet's own names; others v1


def bridge_practicals(powered):
    """[(name, location, (size_x, size_y), aim point or None for straight down, energy W, rgb,
    spread deg)]."""
    if not powered:
        return []
    out = [("bridge ceiling light %d" % k, loc, size, None, CEILING_ENERGY, CEILING_RGB, 180.0)
           for k, (loc, size) in enumerate(CEILING_STRIPS)]
    for name, (cx, cy), yaw_deg, screens in BANKS:
        yaw = math.radians(yaw_deg)
        ux, uy = math.cos(yaw), math.sin(yaw)
        ox, oy = math.sin(yaw), -math.cos(yaw)
        lo = min(u - w / 2 for u, w in screens)
        hi = max(u + w / 2 for u, w in screens)
        mid = (lo + hi) / 2
        out_ = .08 + SCREEN_GLOW_OUT
        loc = (cx + ux * mid + ox * out_, cy + uy * mid + oy * out_, CONSOLE_TOP + .2 + SCREEN_GLOW_UP)
        # Aim at the operator's hands over the switch row; the face is still in its spread.
        aim = (loc[0] + ox * 1.2, loc[1] + oy * 1.2, CONSOLE_TOP + .1)
        out.append(("%s screen glow" % name, loc, (hi - lo, .3), aim, SCREEN_GLOW_W * (hi - lo), SCREEN_RGB,
                    SCREEN_GLOW_SPREAD))
    loc, size, aim, energy, rgb = HELM_FILL
    out.append(("helm overhead fill", loc, size, aim, energy, rgb, 120.0))
    loc, size, energy, rgb = ROOM_FILL
    out.append(("bridge room fill", loc, size, None, energy, rgb, 160.0))
    return out


def plan(frames, fps, samples_n, state=SW_STATE):
    import c2_core as C
    times = [f / fps for f in frames]
    base = BB.plan(times, [1920, 1080])
    for s, f in zip(base["samples"], frames):
        s["frame"] = f
        s["ship_pitch"] = BB.ship_pitch_deg(s["t"])
    base.update({
        "fps": fps, "cycles_samples": samples_n, "sw_state": state,
        "sw_state_blend": str(BASE.parent / "second-wind-states" / state / ("second-wind-%s.blend" % state)),
        "practicals": bridge_practicals(state != "derelict"),
        "glass_material": GLASS_MATERIAL, "glass_fresnel_ior": GLASS_FRESNEL_IOR,
        "glass_reflect": GLASS_REFLECT,
        "screen_pages": SCREEN_PAGES if state != "derelict" else {},
        "screen_face": SCREEN_FACE, "screen_version": SCREEN_VERSION, "screen_dir": str(SCREEN_DIR), "screen_strength": SCREEN_STRENGTH,
        "albedo": str(C1_ASSETS / "planet-albedo-detail-v1.png"),
        "clouds": str(C1_ASSETS / "planet-clouds-detail-v1.png"),
        "legion_idle": LEGION_IDLE, "orbital_speed": C.ORBITAL_SPEED,
        "planetshine": C.PLANETSHINE,
        "eye_m": [c * BB.SW_MPU for c in BB.STATION_FORWARD],
    })
    return base


def check_state(path, state=SW_STATE):
    """Refuse a Second Wind state .blend that is missing or is not the pinned build (SW_PIN). Copy
    it from Drive builds/second-wind/<SW_PIN>/<state>/ to the path given."""
    pin = BASE.parent / "pins" / ("second-wind-%s-%s.manifest.json" % (SW_PIN, state))
    if not pin.exists():
        raise SystemExit("no pinned manifest %s: copy it from Drive builds/second-wind/%s/%s/" % (pin, SW_PIN, state))
    man = json.loads(pin.read_text(encoding="utf-8"))
    BB.check_broadsword(path, man["asset_sha256"])


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
    fps, frames, samples_n, resume, only, out, state = 24.0, None, 64, False, None, OUT, SW_STATE
    half = False
    bulkhead = os.environ.get("C2_BULKHEAD_DETAIL", "1") != "0"
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--resume":
            resume = True
            i += 1
            continue
        if a == "--half":                           # 960x540 for a review pass, scaled up to view
            half = True
            i += 1
            continue
        if a == "--no-bulkhead":                    # leave c2_bulkhead's dressing out (also C2_BULKHEAD_DETAIL=0)
            bulkhead = False
            i += 1
            continue
        if a == "--bulkhead":                       # the default now; kept so older command lines still parse
            bulkhead = True
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
        elif a == "--state":
            state = v
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
    p = plan(frames, fps, samples_n, state)
    BB.check_broadsword(p["broadsword_blend"], p["broadsword_sha256"])
    check_state(p["sw_state_blend"], state)
    p["out"], p["resume"], p["percentage"] = str(out), resume, 50 if half else 100
    p["bulkhead"] = bulkhead
    if bulkhead:                                    # the whole shot's eye path, for c2_bulkhead
        path = BB.plan([k / 8.0 for k in range(int(C.C2_LEN * 8) + 1)], [1920, 1080])["samples"]
        p["bulkhead_eyes"] = [(s["cam_pos_u"][:2], s["cam_yaw"], math.degrees(math.atan(18.0 / s["lens_mm"])) + 6.0)
                              for s in path]
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
        dst.collections = ["SECOND WIND | %s" % p["sw_state"]]
    scene.collection.children.link(dst.collections[0])
    sw = bpy.data.objects["SECOND WIND | %s | root" % p["sw_state"]]
    if abs(sw.get("metres_per_unit", p["sw_mpu"]) - p["sw_mpu"]) > 1e-9:
        raise RuntimeError("Second Wind state is %s m/unit" % sw.get("metres_per_unit"))
    sw.parent = ship
    sw.matrix_parent_inverse = Matrix.Identity(4)
    sw.scale = (p["sw_mpu"],) * 3
    for o in [sw, *sw.children_recursive]:
        o.animation_data_clear()
    if not any(o.name.startswith("Bridge deck plating") for o in sw.children_recursive):
        raise RuntimeError("%s has no bridge interior: rebuild the %s state export" % (p["sw_state_blend"], p["sw_state"]))
    glazing = bpy.data.materials.get(p["glass_material"])
    fresnel = [n for n in glazing.node_tree.nodes if n.type == "FRESNEL"] if glazing else []
    if len(fresnel) != 1:
        raise RuntimeError("the bridge glazing %r has no single Fresnel to lower" % p["glass_material"])
    fresnel[0].inputs["IOR"].default_value = p["glass_fresnel_ior"]
    nt = glazing.node_tree
    link = next(l for l in nt.links if l.from_node == fresnel[0])
    scale = nt.nodes.new("ShaderNodeMath")
    scale.operation, scale.inputs[1].default_value = "MULTIPLY", p["glass_reflect"]
    to = link.to_socket
    nt.links.remove(link)
    nt.links.new(fresnel[0].outputs["Fac"], scale.inputs[0])
    nt.links.new(scale.outputs[0], to)
    # The room's practicals (bridge_practicals): a strip under each caged ceiling lamp, a soft
    # screen-coloured glow off every console's displays toward its operator, and the overhead
    # amber display's spill onto the helm seat. None in the derelict state: the power is off.
    for name, loc, (sx, sy), aim, energy, rgb, spread in p["practicals"]:
        name = "C2B | " + name
        lamp = bpy.data.objects.new(name, bpy.data.lights.new(name, "AREA"))
        lamp.data.shape, lamp.data.size, lamp.data.size_y = "RECTANGLE", sx, sy
        lamp.data.energy, lamp.data.color, lamp.data.spread = energy, rgb, math.radians(spread)
        lamp.visible_camera = False
        lamp.visible_glossy = not name.endswith("room fill")
        scene.collection.objects.link(lamp)
        lamp.parent = sw
        lamp.location = loc
        if aim is not None:
            d = Vector(aim) - Vector(loc)
            lamp.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    pages = {}
    for name, (page, cu, cv, k) in p["screen_pages"].items():
        if page not in pages:
            img = bpy.data.images.load(str(Path(p["screen_dir"]) / ("%s-v%d.png" % (page, p["screen_version"].get(page, 1)))))
            mat = bpy.data.materials.new("C2B | bridge screen %s" % page)
            mat.use_nodes = True
            bs = mat.node_tree.nodes["Principled BSDF"]
            bs.inputs["Base Color"].default_value = (.01, .015, .015, 1)
            bs.inputs["Roughness"].default_value = .25
            tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
            tex.image, tex.extension = img, "EXTEND"
            mat.node_tree.links.new(tex.outputs["Color"], bs.inputs["Emission Color"])
            bs.inputs["Emission Strength"].default_value = p["screen_strength"]
            pages[page] = (mat, img.size[0] / img.size[1])
        screen_page(bpy.data.objects[name], *pages[page], cu, cv, k, p["screen_face"].get(name, "operator"))
    sys.path.insert(0, p["sw_dir"])
    import sw_bridge_interior
    sw_bridge_interior.figures(p["sw_mpu"], parent=sw)
    if p.get("bulkhead"):                           # c2_bulkhead's dressing; --no-bulkhead leaves it out
        import c2_bulkhead
        print("C2B bulkhead detail: %d objects" % len(c2_bulkhead.build(sw, p["bulkhead_eyes"])))

    cam = bpy.data.objects.new("C2B | bridge camera", bpy.data.cameras.new("C2B | bridge camera"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.parent = ship
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = p["lens_mm"], 36.0, "HORIZONTAL"
    cam.data.clip_start, cam.data.clip_end = 0.05, 1.0e5

    # ---- the Legion: the finished asset and its effects in one load, moved by its root only.
    with bpy.data.libraries.load(p["legion_blend"], link=False) as (src, dst2):
        dst2.collections = [p["legion_collection"], "SWFX"]
    for c in dst2.collections:
        scene.collection.children.link(c)
    lroot = bpy.data.objects[p["legion_root"]]
    lroot.scale = (p["legion_mpu"],) * 3
    lroot.rotation_euler = Euler((math.radians(p["roll"]), 0, math.radians(p["yaw"])), "XYZ")
    fx = bpy.data.objects["SWFX controls"]
    fx["thr_throttle"] = p["legion_idle"]
    fx["smk_haze"] = 0.0                    # in vacuum the exhaust disperses; no haze round the stern
    fx.update_tag()
    # Running lights: each lens carries its blink; the kit's lamp for it rides along.
    lights = []
    for lens in [o for o in lroot.children_recursive if "blink_period_s" in o.keys()]:
        lamps = [lens] + [o for o in bpy.data.objects if o.type == "LIGHT"
                          and o.name.split(".")[0] == "SWFX lighting " + lens.name]
        lights.append((lamps, lens["blink_period_s"], lens["blink_phase_s"], lens.get("blink_duty", 0.18)))

    # ---- Broadswords: the finished asset with its effects, one append per fighter so each has its
    # own SWFX (LOW_TECH drives at full throttle, both guns cold, running lights on the kit's own
    # strobe). Gear stays up; no haze or landing dust in vacuum.
    fighters = [root for root, _ in BB.append_broadswords(p, len(p["samples"][0]["fighters"]), effects=True)]

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
        look = Euler((math.radians(90 + s["cam_pitch"]), 0, math.radians(s["cam_yaw"] - 90)), "XYZ").to_matrix()
        roll = Matrix.Rotation(math.radians(s.get("cam_roll", 0.0)), 3, "Z")      # about the view axis
        cam.rotation_euler = (look @ roll).to_euler("XYZ", cam.rotation_euler)
        cam.data.lens = s.get("lens_mm", p["lens_mm"])
        cam.keyframe_insert("location", frame=fr)
        cam.keyframe_insert("rotation_euler", frame=fr)
        cam.data.keyframe_insert("lens", frame=fr)
        lroot.location = Vector(s["legion_centre_m"]) - rot @ (lmid * p["legion_mpu"])
        lroot.keyframe_insert("location", frame=fr)
        for f, (pos, hdg, bank) in zip(fighters, s["fighters"]):
            f.location = pos
            f.rotation_euler = (math.radians(bank), 0, math.radians(hdg))
            f.keyframe_insert("location", frame=fr)
            f.keyframe_insert("rotation_euler", frame=fr)
        planet.rotation_euler = (0, -omega * s["t"], 0)
        planet.keyframe_insert("rotation_euler", frame=fr)
        for lamps, period, phase, duty in lights:
            for lamp in lamps:
                lamp.hide_render = ((s["t"] + phase) % period) > period * duty
                lamp.keyframe_insert("hide_render", frame=fr)
    for o in bpy.data.objects:
        ad = o.animation_data
        if ad and ad.action:
            for fc in _fcurves(ad.action):
                for kp in fc.keyframe_points:
                    kp.interpolation = "CONSTANT" if fc.data_path == "hide_render" else "LINEAR"
    sys.path.insert(0, str(BASE.parent / "thrust-markers"))
    import thrust_shots                      # every craft's nozzles marked for VFX and sound
    thrust_shots.mark_c2(scene, p)
    if p.get("build_only"):                  # thrust-markers/check_thrust_markers.py stops here
        return scene

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
    scene.render.resolution_percentage = int(p.get("percentage", 100))
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


def screen_page(obj, mat, page_aspect, cu, cv, k, face):
    """Show a crop of a page on a screen: UVs from the object's local box, its own material.

    face: 'operator' (top of the box, the operator at local -Y, the page's top toward +Y),
    'aft' (the -X side, for a pilot facing +X), 'up' or 'down' (read from aft, top forward)."""
    me = obj.data
    xs, ys, zs = zip(*(v.co for v in me.vertices))
    lo = (min(xs), min(ys), min(zs))
    hi = (max(xs), max(ys), max(zs))
    def uv_of(co):
        f = [(co[i] - lo[i]) / max(hi[i] - lo[i], 1e-9) for i in range(3)]
        if face == "operator":
            return f[0], f[1]
        if face == "aft":
            return 1 - f[1], f[2]
        return 1 - f[1], f[0]                          # up / down: the page's top is forward
    dims = {"operator": (hi[0] - lo[0], hi[1] - lo[1]), "aft": (hi[1] - lo[1], hi[2] - lo[2])}
    w, h = dims.get(face, (hi[1] - lo[1], hi[0] - lo[0]))
    aspect = w / h
    # The largest window of the screen's aspect inside the page, scaled by k, centred on (cu, cv)
    # and kept on the page.
    if aspect > page_aspect:
        cw, ch = 1.0, page_aspect / aspect
    else:
        cw, ch = aspect / page_aspect, 1.0
    cw, ch = cw * k, ch * k
    u0 = min(max(cu - cw / 2, 0), 1 - cw)
    v0 = min(max(cv - ch / 2, 0), 1 - ch)
    uv = me.uv_layers.active or me.uv_layers.new()
    for loop in me.loops:
        a, b = uv_of(me.vertices[loop.vertex_index].co)
        uv.data[loop.index].uv = (u0 + a * cw, v0 + b * ch)
    me.materials.clear()
    me.materials.append(mat)


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
