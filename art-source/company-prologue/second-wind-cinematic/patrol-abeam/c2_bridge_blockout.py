"""C2 from Second Wind's bridge: a grey blockout of the pass (owner direction 2026-10-18).

    python c2_bridge_blockout.py [--fps 1] [--times 0,4,8] [--res 1920x1080]

One continuous shot from inside the command deck. The camera is someone on the bridge:
it starts looking forward over Second Wind's own trunk and forward turrets at the Legion
ahead, and turns with it, through the port chamfer to the port side slits, as Second Wind
closes on the patrol and passes it. Then Second Wind starts down and the Legion rises out
of the top of the slits. No cut: the pan is the move.

Plain Python plans the motion (the Legion's track, the camera's head turn) and runs Blender
on this same file. Blender opens Second Wind's clean master (second-wind-geometry, read
only), blocks out the command deck's interior around the model's own slits and mullions,
appends the Legion master, and renders Workbench frames:

    build/cinematic/c2-bridge/blockout/b_<t>.png      1920x1080, one per requested time
    build/cinematic/c2-bridge/blockout/plan.json

Frame of reference is Second Wind, in metres: +X bow, +Y port, +Z dorsal, origin at its
model origin. Second Wind is 0.65 m per unit, the Legion 0.99 (each geometry.json).

The command deck the exterior model carries is low and armoured: viewport slits from
18.6 to 19.45 units (about 55 cm of glass), under a visor brow and over a sill ledge. Its
interior is built here and nowhere else: a floor just above the waist housing's top, inner
walls on the glass line below the sill and above the header, a ceiling under the roof, a
console run below the forward and port slits, and two seats. The exterior solids that would
fill the room (glazing, lower bulkhead, brow and roof undersides) are hidden from the
camera but still cast shadows, so sun through the slits is honest.
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
SW_BLEND = SW_DIR / "second-wind-geometry.blend"

# ---------------------------------------------------------------- the bridge (Second Wind units)
SW_MPU = 0.65
FLOOR_Z = 17.55            # just above the waist housing's top (17.5)
SILL_Z = 18.6              # viewport sill ledge top
HEADER_Z = 19.45           # visor brow underside
CEILING_Z = 19.95          # under the armoured roof
EYE_Z = 19.2               # seated eye, about 1.07 m above the floor
# The glazing's plan (sw_bridge.plan(9.0, 17.9, 4.8, 2.0, .8)): forward face at x 17.9,
# chamfers to the sides at |y| 4.8. The camera leans in toward the slit it is looking out of.
STATION_FORWARD = (16.9, 1.1, EYE_Z)        # 1.0 unit (65 cm) behind the forward face
STATION_PORT = (12.9, 3.8, EYE_Z)           # 1.0 unit inboard of the port side slits
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
            "bridge": {"floor": FLOOR_Z, "sill": SILL_Z, "header": HEADER_Z, "ceiling": CEILING_Z}}


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

    # The exterior solids that fill the room: hidden from the camera, still casting shadows.
    for name in ("Command deck viewport slits", "Command deck lower bulkhead", "Command deck visor brow",
                 "Command deck armoured roof", "Viewport sill ledge"):
        for o in bpy.data.objects:
            if o.name == name or o.name.startswith(name + "."):
                o.visible_camera = False
                o.hide_render = True       # Workbench ignores ray visibility

    b = p["bridge"]

    def poly_plan(x0, x1, hw, cf, ca):
        return [(x0 + ca, -hw), (x1 - cf, -hw), (x1, -hw + cf), (x1, hw - cf),
                (x1 - cf, hw), (x0 + ca, hw), (x0, hw - ca), (x0, -hw + ca)]

    def mesh(name, verts, faces, colour):
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        ob = bpy.data.objects.new(name, me)
        scene.collection.objects.link(ob)
        ob.parent = sw_root
        ob.color = colour
        return ob

    def ring(name, poly, z0, z1, colour):
        n = len(poly)
        verts = [(x, y, z0) for x, y in poly] + [(x, y, z1) for x, y in poly]
        faces = [(i, (i + 1) % n, n + (i + 1) % n, n + i) for i in range(n)]
        return mesh(name, verts, faces, colour)

    def cap(name, poly, z, colour):
        return mesh(name, [(x, y, z) for x, y in poly], [tuple(range(len(poly)))], colour)

    def box(name, c, s, colour, rz=0.0):
        bpy.ops.mesh.primitive_cube_add(size=1, location=c)
        ob = bpy.context.active_object
        ob.name = name
        ob.scale = s
        ob.rotation_euler[2] = rz
        ob.parent = sw_root
        ob.color = colour
        return ob

    dark, panel, seat = (0.10, 0.10, 0.11, 1), (0.22, 0.22, 0.23, 1), (0.16, 0.13, 0.11, 1)
    inner = poly_plan(9.05, 17.85, 4.75, 1.98, 0.8)     # just inboard of the glazing
    ring("C2B | bridge lower wall", inner, b["floor"], b["sill"], panel)
    ring("C2B | bridge header wall", inner, b["header"], b["ceiling"], panel)
    cap("C2B | bridge floor", inner, b["floor"], dark)
    cap("C2B | bridge ceiling", inner, b["ceiling"], dark)
    # Console run under the forward face and the port chamfer, and one along the port side.
    box("C2B | forward console", (17.25, 0, 18.25), (1.0, 5.4, 0.7), panel)
    box("C2B | port chamfer console", (16.75, 3.75, 18.25), (1.0, 2.4, 0.7), panel, math.radians(45))
    box("C2B | port side console", (12.8, 4.25, 18.25), (3.6, 0.9, 0.7), panel)
    for y in (-1.6, 1.6):
        box("C2B | seat back", (14.9, y, 18.6), (0.25, 1.2, 1.6), seat)
        box("C2B | seat pan", (15.4, y, 18.05), (1.1, 1.2, 0.2), seat)

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
