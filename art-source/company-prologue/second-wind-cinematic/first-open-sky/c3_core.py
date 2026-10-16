"""Shared camera, scale and layer model for C3 FIRST OPEN SKY (c3-layout.md).

Every C3 script takes its numbers from here, so the guides, the band cut and the
animatic cannot drift into separate cameras. Distances are metres in the world;
screen rates follow from them through the one lens, never the other way round.
"""
import math
from pathlib import Path

BASE = Path(__file__).resolve().parent
CIN = BASE.parent
ROOT = BASE.parents[3]

W, H, FPS = 1920, 1080, 24
DURATION = 12.0

# --- camera: the reveal camera's eye point and lens, re-pitched (c3_cabin_guide.py)
LENS_MM, SENSOR_MM = 26.0, 36.0
F_PX = W * LENS_MM / SENSOR_MM                 # 1386.7 px at 1920
PITCH_DEG = -9.0                               # the reveal camera is -19.29
HORIZON_Y = H / 2 - F_PX * math.tan(math.radians(-PITCH_DEG))   # 320.4

# --- flight: low approach toward Greywater Down, port-side window, bow to screen right
ALTITUDE_M = 400.0        # above the canopy tops under the river valley
GROUND_SPEED = 20.0       # m/s, along screen right; the world therefore slides left
SINK_RATE = 2.0           # m/s descent; the ground rises in frame at its own rate

# --- the lean: the passenger leans 5 cm toward the glass (cabin glass at ~1.2 m)
LEAN_M = 0.05
CABIN_DEPTH_M = 1.2
LEAN_SPAN = (3.5, 9.5)    # seconds; eased


def truck_px(distance_m):
    """Screen slide over the whole shot for a body at this slant distance (px, leftward)."""
    return F_PX * GROUND_SPEED * DURATION / distance_m


def rise_px(distance_m):
    """Screen rise over the shot from the sink rate (px, upward), for ground bodies."""
    return F_PX * SINK_RATE * DURATION / distance_m


def ground_distance_at_row(y):
    """Slant distance to flat canopy seen at frame row y (m); None at or above the horizon."""
    a = math.radians(-PITCH_DEG) + math.atan((y - H / 2) / F_PX)
    return ALTITUDE_M / math.sin(a) if a > 0 else None


# --- the salvaged plate in the frame: the approved landscape master, scaled so its
# own implied camera matches this lens (master horizon row 200 -> HORIZON_Y).
MASTER = CIN / "realism-lookdev" / "landscape-realism-v1.png"
MASTER_SIZE = (1672, 941)
MASTER_HORIZON = 200.0
PLATE_SCALE = 1.2                                  # master px -> frame px
PLATE_X0 = 100.0                                   # frame x of the master's left edge at t=0
PLATE_Y0 = HORIZON_Y - MASTER_HORIZON * PLATE_SCALE

# Five depth layers (storyboard-v3 "no more than five"). Each band's distance is the
# median of the canopy distances over the rows it occupies (ground_distance_at_row). A layer is a parallax rate,
# not a picture region: one layer may carry several disjoint cut-outs that share its
# distance. `distance` (m) sets the rate through the lens.
LAYERS = [
    # name,      distance_m, carries
    ("horizon",  40000.0, "sky gradient, horizon cumulus, the blue range (band 'mountains')"),
    ("far",      7000.0,  "far wooded ridges (band 'far'); planned: the overhead cumulus row"),
    ("mid",      1800.0,  "middle forest, river and banks (band 'mid')"),
    ("near",     1000.0,  "nearest canopy bank, lower right (band 'near')"),
    ("cabin",    CABIN_DEPTH_M, "window frame and cabin; fixed to the camera, scales with the lean only"),
]

# Moving elements ride one host layer, at its rate plus their own travel
# (storyboard-v3 "Moving elements"). Own travel is what differs from the host.
#   name,         host,   true distance m, own travel (m/s along +x bow, +up)
ELEMENTS = [
    ("mist-high",   "far",  4500.0, (-1.0, 0.0), "wisps between the far ridges; depth offset plus a 1 m/s breeze from the bow"),
    ("atlas",       "mid",  2200.0, (60.0, 8.0), "300 m Atlas lifter climbing out, bow right"),
    ("mist-low",    "mid",  1700.0, (-1.0, 0.0), "valley mist over the middle forest, same breeze"),
    ("flock",       "near", 700.0,  (-8.0, 0.0), "eleven 3 m fliers crossing aft through the low sky"),
    ("entry-cloud", "near", 800.0,  (0.0, 0.0), "the entry's last cloud, burning off over 1.6 s"),
]


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def lean_scale(t, depth_m):
    """Per-layer scale from the lean: d / (d - lean). The world (km) does not visibly scale."""
    u = smooth((t - LEAN_SPAN[0]) / (LEAN_SPAN[1] - LEAN_SPAN[0]))
    return depth_m / (depth_m - LEAN_M * u)
