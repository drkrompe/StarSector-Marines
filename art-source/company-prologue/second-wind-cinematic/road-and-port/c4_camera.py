"""C4 THE ROAD AND THE PORT: the one camera model every C4 layer is painted to (c4-layout.md).

World axes match the ship and every earlier window shot: +X is Second Wind's heading
(screen right through the port-side cabin window), +Y is outward from that window (distance
from the flight track), +Z is up. The ship flies level along +X; the window looks out along
+Y with a level optical axis and a downward lens shift, so verticals stay vertical.

A layer is a depth band of ground (a range of Y). Because the camera only trucks along X,
a layer painted as a *pushbroom* panorama at its representative depth slides rigidly and
exactly reproduces the true view of things at that depth. Band edges are lines of constant Y,
which project to horizontal screen lines: two layers sliding at different rates never open a
gap between them. That is the reason this shot is a side-window truck and not a pan.

Run with plain Python to print the scale table and write guides/c4-camera.json.
"""
import json
import math
from pathlib import Path

BASE = Path(__file__).resolve().parent

OUT_W, OUT_H, FPS = 1920, 1080, 24
PLATE_SCALE = 2                     # plates are painted at 2x output density (2160 tall)
ROOT = BASE.parents[3]
# The picture's length in whole frames. It was cut to Castle's old Nessa memory (1.0 s of fields,
# the 15.84 s take, a 1.5 s hold); her C4 line now is the shorter yard line at 1.0-8.2
# (arrival-audio/castle_lines.py), and the picture keeps its length until it is re-cut.
DURATION = 441 / 24
ALTITUDE = 220.0                    # m above the plain, held level for the whole shot
SPEED = 62.0                        # m/s ground speed along +X (about 220 km/h on final approach)
HFOV_DEG = 50.0                     # through-the-window horizontal field of view at 1920 wide
F_PX = (OUT_W / 2) / math.tan(math.radians(HFOV_DEG / 2))   # 2059 px at output scale
LENS_MM = F_PX * 36.0 / OUT_W      # 38.6 mm on a 36 mm sensor
HORIZON_Y = 180.0                   # output px from the top: level axis, lens shifted down
SILL_Y = 860.0                      # bottom edge of the glass in the orange cabin (c4_cabin_render.py)
GLASS_X = (211.0, 1708.0)           # its jambs; the head is above the frame
MARGIN_PX = 96.0                    # output px of overscan either side of the travel
X_START = 296.0                     # camera X at t = 0: the port fills the window as "Let me talk first." lands

# Sun: one star, shared with every window shot and the landing yard ("Shared sun | all shots"
# in landing-wreck-pass.blend). Unit vector toward the sun: astern, a little to starboard
# (behind the window), 51 degrees up. Late morning.
SUN_TOWARD = (-0.61, -0.09, 0.78)

# Five layers, back to front. y_band = (near, far) metres from the track; d_rep is the depth
# the plate is painted at (its parallax rate); haze is the atmospheric blend toward horizon sky.
LAYERS = [
    dict(id="L1-far", name="Sky, far ridges, launch field", y_band=(3800.0, 60000.0), d_rep=8000.0, haze=0.55),
    dict(id="L2-port", name="Port district and distant farmland", y_band=(1700.0, 3800.0), d_rep=2500.0, haze=0.30),
    dict(id="L3-mid", name="Mid fields, farmstead, freight terminal", y_band=(1100.0, 1700.0), d_rep=1350.0, haze=0.16),
    dict(id="L4-near", name="Near fields and the freight road", y_band=(840.0, 1100.0), d_rep=950.0, haze=0.08),
    dict(id="L4f-fore", name="Foreground fields under the sill", y_band=(640.0, 840.0), d_rep=730.0, haze=0.05),
    dict(id="L5-frame", name="Cabin window frame (fixed)", y_band=(0.6, 0.6), d_rep=math.inf, haze=0.0),
]


def camera_x(t):
    return X_START + SPEED * t


def px_per_m(d):
    """Output pixels per metre, laterally and vertically, at depth d."""
    return F_PX / d


def ground_y(d):
    """Output screen y of flat ground at distance d."""
    return HORIZON_Y + F_PX * ALTITUDE / d


def ground_distance(y):
    return F_PX * ALTITUDE / (y - HORIZON_Y)


def layer_rate(layer):
    """Output px per second a layer slides left."""
    return 0.0 if math.isinf(layer["d_rep"]) else SPEED * px_per_m(layer["d_rep"])


def layer_travel(layer, duration=DURATION):
    return layer_rate(layer) * duration


def layer_x_range(layer, duration=DURATION):
    """World X covered by a layer's plate (window plus overscan over the whole move)."""
    d = layer["d_rep"]
    half = (OUT_W / 2 + MARGIN_PX) * d / F_PX
    return camera_x(0) - half, camera_x(duration) + half


def plate_size(layer, duration=DURATION):
    if math.isinf(layer["d_rep"]):
        return OUT_W * PLATE_SCALE, OUT_H * PLATE_SCALE
    w = (OUT_W + 2 * MARGIN_PX + layer_travel(layer, duration)) * PLATE_SCALE
    return int(math.ceil(w / 16) * 16), OUT_H * PLATE_SCALE


def plate_x(layer, x_world, duration=DURATION):
    """Plate pixel column of world X (pushbroom at the layer's representative depth)."""
    x0, _ = layer_x_range(layer, duration)
    return (x_world - x0) * px_per_m(layer["d_rep"]) * PLATE_SCALE


def screen_offset(layer, t, duration=DURATION):
    """Output px to subtract from a plate's x (at output scale) to place it at time t:
    plate output column c lands on screen at c - screen_offset."""
    if math.isinf(layer["d_rep"]):
        return 0.0
    return MARGIN_PX + layer_rate(layer) * t


SCALE_SUBJECTS = [
    # (subject, layer id, depth m, size m, axis)
    ("Freight hauler, tractor + two trailers", "L4-near", 980.0, 32.0, "long"),
    ("Hauler height", "L4-near", 980.0, 4.2, "tall"),
    ("Crop sprayer (hover tender), boom span", "L4-near", 880.0, 24.0, "long"),
    ("Weigh station canopy", "L4-near", 1010.0, 30.0, "long"),
    ("Hedgerow tree, emergent", "L4-near", 1080.0, 28.0, "tall"),
    ("Farm silo", "L3-mid", 1300.0, 22.0, "tall"),
    ("Sawtooth freight warehouse", "L3-mid", 1450.0, 180.0, "long"),
    ("Warehouse height", "L3-mid", 1450.0, 18.0, "tall"),
    ("Container gantry crane", "L2-port", 2250.0, 58.0, "tall"),
    ("Tank, 30 m diameter", "L2-port", 2050.0, 18.0, "tall"),
    ("Port control tower", "L2-port", 2700.0, 95.0, "tall"),
    ("Native canopy tree", "L2-port", 1900.0, 35.0, "tall"),
    ("Launch cradle tower", "L1-far", 6600.0, 140.0, "tall"),
    ("Atlas-class lifter, length (climbing nose-up)", "L1-far", 6600.0, 220.0, "tall"),
    ("Volcanic cone (continuity landmark)", "L1-far", 16000.0, 1500.0, "tall"),
]


def table():
    rows = []
    for name, lid, d, size, axis in SCALE_SUBJECTS:
        rows.append(dict(subject=name, layer=lid, depth_m=d, size_m=size, axis=axis,
                         px=round(size * px_per_m(d), 1), screen_y=round(ground_y(d), 1)))
    return rows


def summary(duration=DURATION):
    layers = []
    for L in LAYERS:
        if math.isinf(L["d_rep"]):
            layers.append(dict(L, d_rep="fixed", rate_px_s=0, travel_px=0, plate=plate_size(L, duration)))
            continue
        x0, x1 = layer_x_range(L, duration)
        layers.append(dict(L, rate_px_s=round(layer_rate(L), 1), travel_px=round(layer_travel(L, duration), 1),
                           plate=plate_size(L, duration), x_range_m=[round(x0), round(x1)],
                           screen_y_band=[round(ground_y(L["y_band"][1]), 1), round(ground_y(L["y_band"][0]), 1)]))
    return dict(output=[OUT_W, OUT_H, FPS], plate_scale=PLATE_SCALE, duration_s=duration, altitude_m=ALTITUDE,
                speed_m_s=SPEED, hfov_deg=HFOV_DEG, focal_px=round(F_PX, 1), lens_mm_36=round(LENS_MM, 2),
                horizon_y=HORIZON_Y, sill_y=SILL_Y, sill_ground_m=round(ground_distance(SILL_Y)),
                camera_x=[camera_x(0), camera_x(duration)], sun_toward=SUN_TOWARD, layers=layers, scale=table())


if __name__ == "__main__":
    s = summary()
    (BASE / "guides").mkdir(exist_ok=True)
    (BASE / "guides" / "c4-camera.json").write_text(json.dumps(s, indent=1), encoding="utf-8")
    print("focal %.1f px, lens %.1f mm, sill ground %d m" % (s["focal_px"], s["lens_mm_36"], s["sill_ground_m"]))
    for L in s["layers"]:
        print(L["id"], L.get("rate_px_s"), L.get("travel_px"), L["plate"], L.get("x_range_m"), L.get("screen_y_band"))
    for r in s["scale"]:
        print("%-46s %-8s %6.0f m %6.1f m -> %6.1f px  (y %.0f)" % (r["subject"], r["layer"], r["depth_m"], r["size_m"], r["px"], r["screen_y"]))
