"""C2 from Second Wind's bridge: a grey blockout of the pass (owner direction 2026-10-18).

    python c2_bridge_blockout.py [--fps 1] [--times 0,4,8] [--res 1920x1080]

One continuous shot from inside the command deck. The camera is someone on the bridge:
it starts looking forward over Second Wind's own trunk and forward turrets at the Legion
ahead, and turns with it, through the port chamfer to the port side slits, as Second Wind
closes on the patrol and passes it. Then Second Wind starts down and the Legion rises out
of the top of the slits. No cut: the pan is the move.

Plain Python plans the motion (the Legion's track, the camera's head turn) and runs Blender
on this same file. Blender opens Second Wind's patched state (second-wind-geometry, read
only), which carries the bridge interior (sw_bridge_interior), adds the pilot and Castle
(sw_bridge_interior.figures), appends the Legion master, and renders Workbench frames:

    build/cinematic/c2-bridge/blockout/b_<t>.png      1920x1080, one per requested time
    build/cinematic/c2-bridge/blockout/plan.json

Frame of reference is Second Wind, in metres: +X bow, +Y port, +Z dorsal, origin at its
model origin. Second Wind is 0.65 m per unit, the Legion 0.99 (each geometry.json).

The command deck is low and armoured: viewport slits from 18.6 to 19.45 units (about 55 cm
of glass) at standing eye height. Workbench draws the glazing opaque, so it is hidden here.
"""
import json
import math
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

# ---------------------------------------------------------------- the pass (metres, Second Wind frame)
# The Legion comes the other way, bow first, a little angled so its bow quarter and deck read,
# and Second Wind passes it to port. Closing speed bleeds off from V0 to V1 by abeam (both
# ships trimming to look at each other); past abeam it drifts aft at V1.
LEGION_MPU = 0.99
T_ABEAM = 8.0              # under "Patrol has your signature" (C2 7.50-8.84)
V0, V1 = 140.0, 22.0       # m/s relative, at C2 0 s and at abeam
ABEAM_Y = 265.0            # Legion midline out to port at abeam (near flank about 120 m)
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


def head(t):
    """Camera yaw (deg from the bow, + to port), pitch (deg, + up) and station at C2 time t.
    Someone on the bridge watches the fighters first: the head follows the first flight in
    from dead ahead, lets it go as it whips past (no head turns that fast), and settles on
    the carrier, then follows the carrier with a little lag, never further aft than 104."""
    on_fighters = 1.0 - smooth((t - 2.3) / 1.3)
    f_yaw = min(62.0, fighter_bearing_deg(max(0.0, t - 0.15)))
    c_yaw = min(104.0, bearing_deg(max(0.0, t - 0.35)))
    yaw = max(4.0, f_yaw * on_fighters + c_yaw * (1.0 - on_fighters))
    # Looking slightly down at the start (our own hull), level by abeam, then up after it.
    pitch = -8.0 + 12.0 * smooth(t / T_ABEAM) + 6.0 * smooth((t - DROP_T - 0.6) / 3.0)
    u = smooth((yaw - 20.0) / 60.0)
    pos = tuple(a + (b - a) * u for a, b in zip(STATION_FORWARD, STATION_PORT))
    return yaw, pitch, pos


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
    lg = C.LEGION_GEOM
    lo, hi = lg["dimensions"]["min"], lg["dimensions"]["max"]
    mid_u = [(lo[i] + hi[i]) / 2 for i in range(3)]
    eye_m = EYE_Z * SW_MPU
    samples = []
    for t in times:
        yaw, pitch, pos = head(t)
        centre = (legion_along(t) + STATION_FORWARD[0] * SW_MPU, ABEAM_Y,
                  eye_m - CROWN_BELOW_EYE_M - (hi[2] - mid_u[2]) * LEGION_MPU + legion_rise(t))
        samples.append({"t": t, "cam_yaw": yaw, "cam_pitch": pitch, "cam_pos_u": list(pos),
                        "legion_centre_m": list(centre), "legion_mid_u": mid_u,
                        "fighters": fighter_positions(t)})
    bw = json.loads((C.BROADSWORD_DIR / "geometry.json").read_text(encoding="utf-8"))
    return {"sw_blend": str(SW_BLEND), "legion_blend": str(C.LEGION_BLEND), "sw_mpu": SW_MPU,
            "broadsword_blend": str(C.BROADSWORD_BLEND), "fighter_mpu": float(bw["metres_per_unit"]),
            "legion_mpu": LEGION_MPU, "yaw": YAW_DEG, "roll": ROLL_DEG, "lens_mm": LENS_MM,
            "sun": list(C.SUN), "res": res, "samples": samples, "out": str(OUT),
            "planet_radius_m": C.PLANET_RADIUS_M, "altitude_m": C.ALTITUDE_M,
            "sw_dir": str(SW_DIR)}


def launch(argv):
    fps, times, res, burst = 1.0, None, (1920, 1080), []
    i = 0
    while i < len(argv):
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
        i += 2
    import c2_core as C
    if times is None:
        n = int(round(C.C2_LEN * fps))
        times = [round(k / fps, 3) for k in range(n + 1)]
    times = sorted(set(times) | set(burst))
    p = plan(times, list(res))
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
    for root in sw_bridge_interior.figures(p["sw_mpu"], parent=sw_root):
        for ch in root.children_recursive:
            ch.color = ch.data.materials[0].diffuse_color

    # Legion: appended, rooted, scaled to metres.
    with bpy.data.libraries.load(p["legion_blend"], link=False) as (src, dst):
        dst.objects = list(src.objects)
    lroot = bpy.data.objects.new("C2B | Legion root", None)
    scene.collection.objects.link(lroot)
    for o in dst.objects:
        if o is None or o.type in ("CAMERA", "LIGHT"):
            continue
        scene.collection.objects.link(o)
    for o in dst.objects:
        if o is not None and o.type not in ("CAMERA", "LIGHT") and o.parent is None:
            o.parent = lroot
            o.matrix_parent_inverse = Matrix.Identity(4)
        if o is not None:
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
    for s in p["samples"]:
        rot = lroot.rotation_euler.to_matrix()
        lroot.location = Vector(s["legion_centre_m"]) - rot @ (lmid * p["legion_mpu"])
        cam.location = Vector(s["cam_pos_u"]) * p["sw_mpu"]
        for f, (pos, hdg, bank) in zip(fighters, s["fighters"]):
            f.location = pos
            f.rotation_euler = Euler((math.radians(bank), 0, math.radians(hdg)), "XYZ")
        # Camera looks along -Z: point it at the yaw/pitch heading in Second Wind's frame.
        yaw, pitch = math.radians(s["cam_yaw"]), math.radians(s["cam_pitch"])
        d = Vector((math.cos(pitch) * math.cos(yaw), math.cos(pitch) * math.sin(yaw), math.sin(pitch)))
        cam.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
        bpy.context.view_layer.update()
        scene.render.filepath = str(out / ("b_%05.2f.png" % s["t"]))
        bpy.ops.render.render(write_still=True)
        print("rendered", s["t"], flush=True)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    import importlib.util
    if importlib.util.find_spec("bpy") is not None:
        blender_main(argv)
    else:
        sys.path.insert(0, str(BASE))
        launch(argv)
