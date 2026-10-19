"""C2 from Second Wind's bridge: a grey blockout of the pass (owner direction 2026-10-18).

    python c2_bridge_blockout.py [--fps 1] [--times 0,4,8] [--res 1920x1080]
                                 [--pitch] [--camera walk,duck,...] [--measure]

One continuous shot from inside the command deck. The camera is someone on the bridge:
it starts looking forward over Second Wind's own trunk and forward turrets at the Legion
ahead, and turns with it, through the port chamfer to the port side slits, as Second Wind
closes on the patrol and passes it. Then Second Wind starts down and the Legion rises out
of the top of the slits. No cut: the pan is the move.

Plain Python plans the motion (the Legion's track, the camera's head turn) and runs Blender
on this same file. Blender opens Second Wind's patched state (second-wind-geometry, read
only), which carries the bridge interior (sw_bridge_interior), adds the pilot and Castle
(sw_bridge_interior.figures), appends the textured Legion (legion-cinematic's LEGION
collection, moved by its root only), and renders Workbench frames:

    build/cinematic/c2-bridge/blockout/b_<t>.png      1920x1080, one per requested time
    build/cinematic/c2-bridge/blockout/plan.json

--hull-fit renders masks instead of pictures (the Legion alone, Second Wind alone) at a quarter
of the size, and writes hull-fit.json: per time, how much of the hull's silhouette lies inside
the frame and inside the glass, and which edges of the frame it runs off.

Frame of reference is Second Wind, in metres: +X bow, +Y port, +Z dorsal, origin at its
model origin. Second Wind is 0.65 m per unit, the Legion 0.99 (each geometry.json).

The command deck is low and armoured: viewport slits from 18.6 to 19.45 units (about 55 cm
of glass) at standing eye height. Workbench draws the glazing opaque, so it is hidden here.
"""
import json
import math
import os
import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
OUT = ROOT / "build" / "cinematic" / "c2-bridge" / "blockout"
sys.path.insert(0, str(BASE.parents[2]))
from blender_path import BLENDER  # noqa: E402

SW_DIR = BASE.parents[1] / "second-wind-geometry"
SW_BLEND = SW_DIR / "states" / "patched" / "second-wind-geometry.blend"   # C2 is the patched state
# The Legion is the finished asset (legion-cinematic, accepted 2026-10-19): append the LEGION
# collection and move its root only. legion.blend is built, not committed (its finalize.sh).
LEGION_ASSET_DIR = BASE.parents[1] / "legion-cinematic"
LEGION_ASSET = Path(os.environ.get("C2_LEGION_ASSET", LEGION_ASSET_DIR / "legion.blend"))
LEGION_MANIFEST = LEGION_ASSET_DIR / "manifest.json"

# ---------------------------------------------------------------- the bridge (Second Wind units)
SW_MPU = 0.65
# The bridge is sw_bridge_interior's: floor z 16.6, slits 18.6 to 19.45, a standing bridge.
EYE_Z = 19.2               # standing eye, about 1.69 m above the floor, mid-slit
# The camera is someone standing at the slits: between the flight seats, leaning on the
# forward console (its face is at x 16.85), then at the port comms bank (face at y 3.75),
# a step forward of Castle.
STATION_FORWARD = (16.75, 1.2, EYE_Z)       # on the dais's clear port side, leaning over the console
STATION_PORT = (13.0, 3.6, EYE_Z)           # 1.05 units (70 cm) inboard of the port glass, at the bank
LENS_MM = 22.0             # on a 36 mm sensor: 78.6 degrees across
POSTS = 1.0                # width of the slit mullions in the blockout (1 = the model's own)

# ---------------------------------------------------------------- the pass (metres, Second Wind frame)
# The Legion comes the other way, bow first, a little angled so its bow quarter and deck read,
# and Second Wind passes it to port. Closing speed bleeds off from V0 to V1 by abeam (both
# ships trimming to look at each other); past abeam it drifts aft at V1.
LEGION_MPU = 0.99
T_ABEAM = 8.0              # under "Patrol has your signature" (C2 7.50-8.84)
V0, V1 = 140.0, 22.0       # m/s relative, at C2 0 s and at abeam
# Re-solved for the finished 292 m asset (2026-10-19): at 265 m (near flank about 120 m) the hull
# ran off the bottom of the frame from 7 to 11 s and the bridge hid 29% of it at abeam. At 340 m
# (near flank about 195 m) `--hull-fit` puts 97-100% of the hull inside the glass from 7.5 to
# 12.5 s with nothing off the frame, and it still rises clear of the slits by 15 s.
ABEAM_Y = 340.0            # Legion midline out to port at abeam
CROWN_BELOW_EYE_M = -20.0  # negative: the Legion's crown is 20 m ABOVE our eye at abeam; it towers
YAW_DEG = 168.0            # heading against ours; 180 is dead reciprocal
ROLL_DEG = -8.0            # banked so its deck tilts toward us
DROP_T, DROP_ACCEL = 9.6, 9.0   # Second Wind's de-orbit: the Legion rises relative to us
# Second Wind pitches nose-down as it starts down (owner, 2026-10-18): the whole ship, bridge
# and camera with it, so from inside the Legion and Jangala swing up the slits and the sun
# moves across the bridge. Eased in over the first seconds of the burn.
SHIP_PITCH_DEG = 16.0
SHIP_PITCH_IN = (DROP_T, DROP_T + 3.5)


# The air wing screens ahead of the carrier (owner, 2026-10-18): Broadswords (the Legion XIV
# flies two Broadsword wings; no Hoplon model exists yet) come off its bow toward us and
# streak past the port slits, the first flight at 3.0 s and the second at 4.2 s, before the
# carrier itself closes. Each flight is a vic of three, reciprocal to us, nearly level with
# our eye so they cross the slit band. Offsets are (behind the leader, out to port, up), m.
FIGHTER_SPEED = 160.0       # m/s relative, constant
FLIGHTS = [  # (C2 time the leader passes abeam our eye, out to port m, up from eye m)
    (3.0, 55.0, 7.0),
    (4.2, 80.0, 9.0),
]
VIC = [(0.0, 0.0, 0.0), (22.0, -14.0, -2.0), (22.0, 14.0, 1.5)]
FIGHTER_BANK_DEG = 14.0      # rolled a little away from us as they slip past


def fighter_positions(t):
    """[(position m, heading deg, bank deg)] of every fighter at C2 time t, Second Wind frame."""
    eye_x = STATION_FORWARD[0] * SW_MPU
    out = []
    for t_pass, y, z in FLIGHTS:
        x_lead = eye_x + FIGHTER_SPEED * (t_pass - t)
        for back, dy, dz in VIC:
            out.append(([x_lead + back, y + dy, EYE_Z * SW_MPU + z + dz], 180.0, FIGHTER_BANK_DEG))
    return out


def legion_along(t):
    """Midpoint distance ahead of our eye (m) at C2 time t."""
    if t <= T_ABEAM:
        d = T_ABEAM - t
        return V1 * d + (V0 - V1) * d * d / (2 * T_ABEAM)
    return -V1 * (t - T_ABEAM)


def legion_rise(t):
    return 0.5 * DROP_ACCEL * max(0.0, t - DROP_T) ** 2


def bearing_deg(t):
    """Bearing of the Legion's midpoint from the bow, positive to port."""
    return math.degrees(math.atan2(ABEAM_Y, legion_along(t)))


def fighter_bearing_deg(t):
    """Bearing of the first flight's leader from our eye, positive to port."""
    t_pass, y, _ = FLIGHTS[0]
    return math.degrees(math.atan2(y, FIGHTER_SPEED * (t_pass - t)))


# ---------------------------------------------------------------- camera refinements (2026-10-19)
# Each is a separate switch so the owner can compare them one at a time against the move as
# reviewed on 2026-10-18 (no switches): `--camera walk,breath` on the blockout,
# C2_CAMERA=walk,breath for the lit render.
#   walk    the step down to Castle's station is a person walking, on its own clock, instead of
#           a glide tied to the head turn (which slid the body forward and back with the head as
#           it followed the fighters): leaning on the dais through both flights, then back off
#           the console, down the dais step past the grab pole and four paces to the comms bank.
#   breath  a standing person is never still: a slow breath in pitch and height and a slower
#           weight shift in yaw and roll, a few tenths of a degree.
#   frame   the head sits lower once it is at the port glass, so the slit fills the frame
#           rather than the visor brow above it; it still looks up after the rising Legion.
#   lean    at abeam the person leans over the comms bank toward the glass, 70 cm to 45 cm, so
#           the slit is taller in the frame, and straightens again for the read-back.
#   lens    the lens creeps from 22 mm to 27 mm toward abeam and back for the rise. An eye
#           cannot zoom, so this is a film camera's choice, offered for comparison.
#   duck    the fighters fly above our eye, so from the dais they skim the top of the slit and
#           go behind the visor brow at about 2.8 s: the person ducks lower over the console
#           and closer to the glass to keep them in sight, and comes back up after.

CAMERA = set(filter(None, os.environ.get("C2_CAMERA", "").split(",")))
CAMERA_OPTIONS = ("walk", "breath", "frame", "lean", "lens", "duck")
DAIS_STEP = (16.05, 1.65)        # on the dais at the head of the step (its tread is at y 1.65)
STEP_FOOT = (15.15, 1.85)        # on the main floor at its foot; the grab pole is 0.8 to port
WALK = (4.6, 6.6)                # after the second flight (4.2 s), settled before abeam


def _walk_point(u):
    """(x, y) at fraction u of the walk's length, on straight legs (Second Wind units)."""
    pts = [STATION_FORWARD[:2], DAIS_STEP, STEP_FOOT, STATION_PORT[:2]]
    legs = [math.dist(a, b) for a, b in zip(pts, pts[1:])]
    d = u * sum(legs)
    for (a, b), n in zip(zip(pts, pts[1:]), legs):
        if d <= n:
            return a[0] + (b[0] - a[0]) * d / n, a[1] + (b[1] - a[1]) * d / n
        d -= n
    return pts[-1]


def head_full(t, options=None):
    """Camera at C2 time t: {yaw, pitch, roll (deg), pos (Second Wind units), lens (mm)}, with
    the refinements in `options` (default CAMERA) applied.
    Someone on the bridge watches the fighters first: the head follows the first flight in
    from dead ahead, lets it go as it whips past (no head turns that fast), and settles on
    the carrier, then follows the carrier with a little lag, never further aft than 104."""
    opts = CAMERA if options is None else set(options)
    on_fighters = 1.0 - smooth((t - 2.3) / 1.3)
    f_yaw = min(62.0, fighter_bearing_deg(max(0.0, t - 0.15)))
    c_yaw = min(104.0, bearing_deg(max(0.0, t - 0.35)))
    yaw = max(4.0, f_yaw * on_fighters + c_yaw * (1.0 - on_fighters))
    # Looking slightly down at the start (our own hull), level by abeam, then up after it.
    pitch = -8.0 + 12.0 * smooth(t / T_ABEAM) + 6.0 * smooth((t - DROP_T - 0.6) / 3.0)
    roll, lens = 0.0, LENS_MM
    if "frame" in opts:
        pitch -= 7.0 * smooth((t - 4.0) / 3.0)
    if "walk" in opts:
        a, b = WALK
        u = min(1.0, max(0.0, (t - a) / (b - a)))
        x, y = _walk_point(smooth(u))
        z = EYE_Z
        if 0.0 < u < 1.0:
            # Four paces: the eye drops a little at each footfall and sways over the standing
            # foot; the first footfall is the step down off the dais, the deepest.
            env, ph = math.sin(math.pi * u), 4.0 * u
            z -= 0.035 * (0.5 - 0.5 * math.cos(2 * math.pi * ph)) * env
            z -= 0.05 * math.sin(math.pi * min(1.0, ph)) * (ph < 1.0)
            sway = 0.02 * math.sin(math.pi * ph) * env
            x -= math.sin(math.radians(yaw)) * sway
            y += math.cos(math.radians(yaw)) * sway
            roll += 0.5 * math.sin(math.pi * ph) * env
        pos = (x, y, z)
    else:
        u = smooth((yaw - 20.0) / 60.0)
        pos = tuple(a + (b - a) * u for a, b in zip(STATION_FORWARD, STATION_PORT))
    if "duck" in opts:
        k = smooth((t - 1.5) / 1.0) * (1.0 - smooth((t - 3.4) / 1.0))
        pos = (pos[0] + 0.15 * k, pos[1], pos[2] - 0.20 * k)
        pitch += 3.0 * k
    if "lean" in opts:
        k = smooth((t - 6.6) / 1.2) * (1.0 - smooth((t - 9.4) / 1.4))
        pos = (pos[0], pos[1] + 0.38 * k, pos[2] - 0.06 * k)
        pitch -= 2.0 * k
    if "lens" in opts:
        lens += 5.0 * smooth((t - 5.5) / 2.5) * (1.0 - smooth((t - 10.0) / 2.0))
    if "breath" in opts:
        br = 2 * math.pi * 0.26 * t
        pitch += 0.30 * math.sin(br) + 0.12 * math.sin(2 * math.pi * 0.61 * t + 1.3)
        yaw += 0.35 * math.sin(2 * math.pi * 0.11 * t + 0.4) + 0.10 * math.sin(2 * math.pi * 0.47 * t)
        roll += 0.25 * math.sin(2 * math.pi * 0.09 * t + 2.0)
        pos = (pos[0], pos[1], pos[2] + 0.008 * math.sin(br - 0.6))
    return {"yaw": yaw, "pitch": pitch, "roll": roll, "pos": pos, "lens": lens}


def head(t):
    """Camera yaw (deg from the bow, + to port), pitch (deg, + up) and station at C2 time t
    (head_full, with the refinements in CAMERA)."""
    h = head_full(t)
    return h["yaw"], h["pitch"], h["pos"]


def ship_pitch_deg(t):
    """Second Wind's nose-down pitch (degrees) at C2 time t."""
    a, b = SHIP_PITCH_IN
    return SHIP_PITCH_DEG * smooth((t - a) / (b - a))


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def plan(times, res):
    sys.path.insert(0, str(BASE))
    import c2_core as C
    man = json.loads(LEGION_MANIFEST.read_text(encoding="utf-8"))
    if abs(man["metres_per_unit"] - LEGION_MPU) > 1e-9:
        raise RuntimeError("legion-cinematic is %s m/unit; the pass is solved for %s" % (man["metres_per_unit"], LEGION_MPU))
    lo, hi = man["bounds_units_rest"]
    mid_u = [(lo[i] + hi[i]) / 2 for i in range(3)]
    eye_m = EYE_Z * SW_MPU
    samples = []
    for t in times:
        h = head_full(t)
        yaw, pitch, pos = h["yaw"], h["pitch"], h["pos"]
        centre = (legion_along(t) + STATION_FORWARD[0] * SW_MPU, ABEAM_Y,
                  eye_m - CROWN_BELOW_EYE_M - (hi[2] - mid_u[2]) * LEGION_MPU + legion_rise(t))
        samples.append({"t": t, "cam_yaw": yaw, "cam_pitch": pitch, "cam_pos_u": list(pos),
                        "cam_roll": h["roll"], "lens_mm": h["lens"],
                        "legion_centre_m": list(centre), "legion_mid_u": mid_u,
                        "fighters": fighter_positions(t), "ship_pitch": ship_pitch_deg(t)})
    bw = json.loads((C.BROADSWORD_DIR / "geometry.json").read_text(encoding="utf-8"))
    return {"sw_blend": str(SW_BLEND), "legion_blend": str(LEGION_ASSET), "legion_root": man["root"],
            "legion_collection": man["append_collection"], "sw_mpu": SW_MPU,
            "broadsword_blend": str(C.BROADSWORD_BLEND), "fighter_mpu": float(bw["metres_per_unit"]),
            "legion_mpu": LEGION_MPU, "yaw": YAW_DEG, "roll": ROLL_DEG, "lens_mm": LENS_MM,
            "sun": list(C.SUN), "res": res, "samples": samples, "out": str(OUT),
            "planet_radius_m": C.PLANET_RADIUS_M, "altitude_m": C.ALTITUDE_M,
            "sw_dir": str(SW_DIR), "camera": sorted(CAMERA), "posts": POSTS}


def launch(argv):
    fps, times, res, burst, pitched, measure, hull_fit = 1.0, None, (1920, 1080), [], False, False, False
    i = 0
    while i < len(argv):
        if argv[i] == "--hull-fit":          # hull-fit.json instead of frames (see hull_fit_frame)
            hull_fit, res = True, (480, 270)
            i += 1
            continue
        if argv[i] == "--fps":
            fps = float(argv[i + 1])
        elif argv[i] == "--times":
            times = [float(x) for x in argv[i + 1].split(",")]
        elif argv[i] == "--set":           # --set NAME=value overrides a pass constant
            k, v = argv[i + 1].split("=")
            globals()[k] = float(v)
        elif argv[i] == "--out":
            global OUT
            OUT = Path(argv[i + 1])
        elif argv[i] == "--burst":          # --burst A:B:STEP adds frames, e.g. 2.0:4.6:0.2
            a, b_, st = (float(x) for x in argv[i + 1].split(":"))
            burst = [round(a + k * st, 3) for k in range(int(round((b_ - a) / st)) + 1)]
        elif argv[i] == "--res":
            res = tuple(int(x) for x in argv[i + 1].split("x"))
        elif argv[i] == "--pitch":          # apply the de-orbit pitch, as the lit render does
            pitched = True
            i -= 1
        elif argv[i] == "--camera":         # --camera walk,breath: refinements to apply (CAMERA)
            CAMERA.clear()
            CAMERA.update(c for c in argv[i + 1].split(",") if c and c != "none")
            if CAMERA - set(CAMERA_OPTIONS):
                raise SystemExit("unknown camera refinement %s; choose from %s"
                                 % (sorted(CAMERA - set(CAMERA_OPTIONS)), CAMERA_OPTIONS))
        elif argv[i] == "--posts":          # --posts 0.4: preview the slit mullions at that width
            global POSTS
            POSTS = float(argv[i + 1])
        elif argv[i] == "--measure":        # measure.json instead of frames (see measure_frame)
            measure = True
            i -= 1
        i += 2
    import c2_core as C
    if times is None:
        n = int(round(C.C2_LEN * fps))
        times = [round(k / fps, 3) for k in range(n + 1)]
    times = sorted(set(times) | set(burst))
    p = plan(times, list(res))
    p["pitched"], p["measure"], p["hull_fit"] = pitched, measure, hull_fit
    if not LEGION_ASSET.exists():
        raise SystemExit("%s is not built: run legion-cinematic/finalize.sh" % LEGION_ASSET)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "plan.json").write_text(json.dumps(p, indent=1), encoding="utf-8")
    for s in p["samples"]:
        print("t=%5.2f bearing %5.1f yaw %5.1f pitch %5.1f ahead %7.1f m" % (
            s["t"], bearing_deg(s["t"]), s["cam_yaw"], s["cam_pitch"], legion_along(s["t"])))
    subprocess.run([str(BLENDER), "--background", "--factory-startup", "--python-exit-code", "1",
                    "--python", str(Path(__file__).resolve()), "--", "--plan", str(OUT / "plan.json")], check=True)


# ================================================================ Blender

def blender_main(argv):
    import bpy
    from mathutils import Euler, Matrix, Vector

    p = json.loads(Path(argv[argv.index("--plan") + 1]).read_text(encoding="utf-8"))
    bpy.ops.wm.open_mainfile(filepath=p["sw_blend"])
    scene = bpy.context.scene
    for o in list(bpy.data.objects):
        if o.type in ("CAMERA", "LIGHT"):
            bpy.data.objects.remove(o, do_unlink=True)
    sw_root = bpy.data.objects.new("C2B | Second Wind root", None)
    scene.collection.objects.link(sw_root)
    for o in [o for o in bpy.data.objects if o.parent is None and o is not sw_root]:
        o.parent = sw_root
        o.matrix_parent_inverse = Matrix.Identity(4)
        o.color = (0.62, 0.60, 0.55, 1)
    sw_root.scale = (p["sw_mpu"],) * 3

    # The bridge is the model's own (sw_bridge_interior), cut into the command deck. Workbench
    # draws the glazing opaque, so it is hidden here; the figures are added, never saved.
    for o in bpy.data.objects:
        if o.name.startswith("Command deck viewport slits"):
            o.hide_render = True
        if o.type == "MESH" and o.data.materials and o.data.materials[0] is not None:
            o.color = o.data.materials[0].diffuse_color
    sys.path.insert(0, p["sw_dir"])
    import sw_bridge_interior
    figure_roots = sw_bridge_interior.figures(p["sw_mpu"], parent=sw_root)
    for root in figure_roots:
        for ch in root.children_recursive:      # the MakeHuman Castle also holds a rig and empties
            if ch.type == "MESH" and ch.data.materials:
                ch.color = ch.data.materials[0].diffuse_color

    # A preview of thinner window posts (--posts): the slit mullions narrowed along the glass in
    # place. Only this render changes; the hull model is untouched.
    for o in bpy.data.objects:
        if "mullion" in o.name:
            o.scale.x *= p.get("posts", 1.0)

    # Legion: the asset's collection, moved by its root only, scaled to metres.
    with bpy.data.libraries.load(p["legion_blend"], link=False) as (src, dst):
        dst.collections = [p["legion_collection"]]
    scene.collection.children.link(dst.collections[0])
    lroot = bpy.data.objects[p["legion_root"]]
    legion = [lroot, *lroot.children_recursive]
    for o in legion:
        o.color = (0.78, 0.42, 0.18, 1)
    lroot.scale = (p["legion_mpu"],) * 3
    lroot.rotation_euler = Euler((math.radians(p["roll"]), 0, math.radians(p["yaw"])), "XYZ")

    # Fighters: the Broadsword model appended once, one copy of its root tree per fighter.
    with bpy.data.libraries.load(p["broadsword_blend"], link=False) as (src, dst2):
        dst2.collections = [c for c in src.collections if c == "BROADSWORD | geometry"]
    proto_root = next(o for o in dst2.collections[0].objects if o.name.startswith("Broadsword root"))

    def clone(obj, parent):
        c = obj.copy()
        scene.collection.objects.link(c)
        c.parent = parent
        c.matrix_parent_inverse = obj.matrix_parent_inverse.copy()
        c.color = (0.80, 0.50, 0.22, 1)
        for ch in obj.children:
            clone(ch, c)
        return c

    fighters = []
    for k in range(len(p["samples"][0]["fighters"])):
        f = clone(proto_root, None)
        f.name = "C2B | Broadsword %d" % k
        f.scale = (p["fighter_mpu"],) * 3
        fighters.append(f)

    # Jangala, 200 km down. A sphere scaled about the eye by 1/2000 looks identical from it
    # and keeps the depth range sane (the preview renderer's depth buffer cannot span 6000 km).
    k = 1.0 / 2000.0
    R = p["planet_radius_m"] * k
    eye = Vector(p["samples"][0]["cam_pos_u"]) * p["sw_mpu"]
    bpy.ops.mesh.primitive_uv_sphere_add(radius=R, segments=256, ring_count=128,
                                         location=eye + Vector((0, 0, -(p["planet_radius_m"] + p["altitude_m"]) * k
                                                                - eye.z * k)))
    planet = bpy.context.active_object
    planet.color = (0.20, 0.34, 0.55, 1)

    cam = bpy.data.objects.new("C2B | bridge camera", bpy.data.cameras.new("C2B | bridge camera"))
    scene.collection.objects.link(cam)
    # With --pitch the ship frame (hull, bridge, figures and camera) pitches nose-down about
    # Second Wind's origin against the fixed Legion, fighters and planet, as in the lit render.
    ship = bpy.data.objects.new("C2B | ship frame", None)
    scene.collection.objects.link(ship)
    sw_root.parent = ship
    cam.parent = ship
    scene.camera = cam
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = p["lens_mm"], 36.0, "HORIZONTAL"
    cam.data.clip_start, cam.data.clip_end = 0.05, 8000.0

    scene.render.engine = "BLENDER_WORKBENCH"
    sh = scene.display.shading
    sh.light = "STUDIO"
    sh.color_type = "OBJECT"
    sh.show_shadows = True
    sh.shadow_intensity = 0.85
    sh.show_cavity = True
    scene.display.light_direction = tuple(Vector(p["sun"]).normalized())
    sh.background_type = "VIEWPORT"
    sh.background_color = (0, 0, 0)
    scene.render.resolution_x, scene.render.resolution_y = p["res"]
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.view_settings.view_transform = "Standard"

    out = Path(p["out"])
    lmid = Vector(p["samples"][0]["legion_mid_u"])
    fit = []
    kinds = {}
    if p.get("measure"):
        for o in bpy.data.objects:
            kinds[o.name] = "ship"
        for o in lroot.children_recursive:
            kinds[o.name] = "legion"
        for f in fighters:
            for o in [f, *f.children_recursive]:
                kinds[o.name] = "fighter"
        for root in figure_roots:
            who = "pilot" if "pilot" in root.name else "castle"
            for o in root.children_recursive:
                kinds[o.name] = ("yoke" if "yoke" in o.name.lower() else who)
        kinds[planet.name] = "planet"
        for o in bpy.data.objects:
            if o.name.startswith("Command deck viewport slits"):
                kinds[o.name] = "glass"
        rows = []
    for s in p["samples"]:
        scene.frame_set(int(round(s["t"] * 24)))     # animated figures (the pilot leans in at ~9 s)
        ship.rotation_euler = (0, math.radians(s["ship_pitch"] if p.get("pitched") else 0.0), 0)
        rot = lroot.rotation_euler.to_matrix()
        lroot.location = Vector(s["legion_centre_m"]) - rot @ (lmid * p["legion_mpu"])
        cam.location = Vector(s["cam_pos_u"]) * p["sw_mpu"]
        for f, (pos, hdg, bank) in zip(fighters, s["fighters"]):
            f.location = pos
            f.rotation_euler = Euler((math.radians(bank), 0, math.radians(hdg)), "XYZ")
        # Camera looks along -Z: point it at the yaw/pitch heading in Second Wind's frame.
        yaw, pitch = math.radians(s["cam_yaw"]), math.radians(s["cam_pitch"])
        d = Vector((math.cos(pitch) * math.cos(yaw), math.cos(pitch) * math.sin(yaw), math.sin(pitch)))
        q = d.to_track_quat("-Z", "Y")
        q.rotate(Matrix.Rotation(math.radians(s.get("cam_roll", 0.0)), 4, d))
        cam.rotation_euler = q.to_euler()
        cam.data.lens = s.get("lens_mm", p["lens_mm"])
        bpy.context.view_layer.update()
        if p.get("measure"):
            rows.append(dict(t=s["t"], **measure_frame(scene, cam, kinds, p["res"])))
            print("measured", s["t"], flush=True)
            continue
        if p.get("hull_fit"):
            fit.append(dict(t=s["t"], **hull_fit_frame(scene, cam, legion, sw_root, out, p["res"])))
            print("HULL FIT t=%5.2f in frame %.2f in glass %.2f share %.2f bbox %s off %s" % (
                s["t"], fit[-1]["in_frame"], fit[-1]["in_glass"], fit[-1]["frame_share"],
                fit[-1]["bbox_px_1080"], ",".join(fit[-1]["runs_off"]) or "-"), flush=True)
            continue
        scene.render.filepath = str(out / ("b_%05.2f.png" % s["t"]))
        bpy.ops.render.render(write_still=True)
        print("rendered", s["t"], flush=True)
    if p.get("measure"):
        (out / "measure.json").write_text(json.dumps(rows, indent=1), encoding="utf-8")
    if p.get("hull_fit"):
        (out / "hull-fit.json").write_text(json.dumps(fit, indent=1), encoding="utf-8")


def measure_frame(scene, cam, kinds, res, cols=96):
    """What the camera sees, from a grid of rays: the share of the frame that is outside
    (space, planet, Legion, fighters: the view through the glass), the ship's own posts and
    bridge, and each figure; how much of the Legion the pilot, yoke or Castle hides; and
    how near the eye comes to any surface of the ship or a figure (m)."""
    import bpy
    from mathutils import Vector
    dg = bpy.context.evaluated_depsgraph_get()
    mw = cam.matrix_world
    o = mw.translation.copy()
    rows_n = int(round(cols * res[1] / res[0]))
    half = 18.0 / cam.data.lens
    counts, hidden = {}, {}
    for j in range(rows_n):
        for i in range(cols):
            a = ((i + 0.5) / cols * 2 - 1) * half
            b = (1 - (j + 0.5) / rows_n * 2) * half * res[1] / res[0]
            d = (mw.to_3x3() @ Vector((a, b, -1.0))).normalized()
            start, first, behind = o, None, None
            for _ in range(8):
                hit, loc, _n, _f, ob, _m = scene.ray_cast(dg, start, d, distance=1e5)
                k = kinds.get(ob.original.name if hit else "", "space") if hit else "space"
                if k == "glass":
                    start = loc + d * 1e-3
                    continue
                if first is None:
                    first = k
                    if k in ("pilot", "yoke", "castle"):
                        start = loc + d * 1e-3
                        continue
                    break
                if k in ("pilot", "yoke", "castle"):
                    start = loc + d * 1e-3
                    continue
                behind = k
                break
            counts[first] = counts.get(first, 0) + 1
            if behind == "legion":
                hidden[first] = hidden.get(first, 0) + 1
    n = cols * rows_n
    share = {k: round(v / n, 4) for k, v in counts.items()}
    outside = sum(share.get(k, 0) for k in ("space", "planet", "legion", "fighter"))
    legion = share.get("legion", 0)
    near, near_kind = 99.0, ""
    for ob in bpy.data.objects:
        if ob.type != "MESH" or kinds.get(ob.name) not in ("ship", "pilot", "yoke", "castle"):
            continue
        ev = ob.evaluated_get(dg)
        box = [ev.matrix_world @ Vector(c) for c in ev.bound_box]
        gap = Vector([max(min(b[i] for b in box) - o[i], 0, o[i] - max(b[i] for b in box)) for i in range(3)])
        if gap.length >= near:
            continue
        try:
            ok, loc, _n, _f = ev.closest_point_on_mesh(ev.matrix_world.inverted() @ o)
        except RuntimeError:                    # no evaluated mesh (an empty hinge proxy)
            continue
        if ok:
            dist = (ev.matrix_world @ loc - o).length
            if dist < near:
                near, near_kind = dist, ob.name
    return {"outside": round(outside, 4), "legion": legion, "share": share,
            "legion_hidden_by": {k: round(v / n, 4) for k, v in hidden.items()},
            "nearest_m": round(near, 3), "nearest": near_kind}


def hull_fit_frame(scene, cam, legion, sw_root, out, res, wide=3.0):
    """Does the whole Legion fit the view? Flat masks of the Legion alone and of Second Wind
    alone with its glazing left out (so what it does not cover is the view through the slits,
    past posts, brow and consoles): how much of the hull is in the frame and in the glass, and
    which frame edges it runs off. The hull is also drawn through a camera `wide` times as wide
    at the same eye and aim, so a hull that runs off the frame is counted as such. Leaves every
    object's render visibility and colour as it found them."""
    import bpy
    import numpy as np

    every = [o for o in scene.objects if o.type in ("MESH", "CURVE") and not o.hide_render]
    saved = [(o, o.hide_render, tuple(o.color)) for o in every]
    lset = set(legion)
    ship = {o for o in sw_root.children_recursive if not o.name.startswith("Command deck viewport slits")}
    sh = scene.display.shading
    shading = (sh.light, sh.show_shadows, sh.show_cavity)
    sh.light, sh.show_shadows, sh.show_cavity = "FLAT", False, False
    path = str(out / "_hull_fit_mask.png")
    sensor = cam.data.sensor_width

    def render(show, zoom=1.0):
        for o in every:
            o.hide_render = o not in show
            o.color = (1, 1, 1, 1)
        cam.data.sensor_width = sensor * zoom
        scene.render.filepath = path
        bpy.ops.render.render(write_still=True)
        img = bpy.data.images.load(path)
        a = np.array(img.pixels[:], dtype=np.float32).reshape(img.size[1], img.size[0], 4)[::-1, :, 0]
        bpy.data.images.remove(img)
        return a > 0.5

    hull = render(lset)
    total = int(render(lset, wide).sum())
    glass = ~render(ship)
    cam.data.sensor_width = sensor
    for o, hidden, colour in saved:
        o.hide_render, o.color = hidden, colour
    sh.light, sh.show_shadows, sh.show_cavity = shading
    w, h = res
    n = int(hull.sum())
    ys, xs = np.nonzero(hull) if n else ([0], [0])
    edges = [k for k, v in (("left", hull[:, 0].any()), ("right", hull[:, -1].any()),
                            ("top", hull[0, :].any()), ("bottom", hull[-1, :].any())) if v]
    return {"in_frame": round(n / max(total * wide * wide, 1), 3),
            "in_glass": round(int((hull & glass).sum()) / max(n, 1), 3),
            "frame_share": round(n / (w * h), 3),
            "bbox_px_1080": [int(min(xs) * 1920 / w), int(min(ys) * 1080 / h),
                             int((max(xs) + 1) * 1920 / w), int((max(ys) + 1) * 1080 / h)],
            "runs_off": edges}

if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        sys.path.insert(0, str(BASE))
        launch(argv)
