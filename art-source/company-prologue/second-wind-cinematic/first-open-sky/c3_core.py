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
DURATION = 16.0

# --- camera: the reveal camera's eye point and lens, re-pitched (c3_cabin_guide.py)
LENS_MM, SENSOR_MM = 26.0, 36.0
F_PX = W * LENS_MM / SENSOR_MM                 # 1386.7 px at 1920
PITCH_DEG = -9.0                               # the reveal camera is -19.29
HORIZON_Y = H / 2 - F_PX * math.tan(math.radians(-PITCH_DEG))   # 320.4

# --- flight: low approach toward Greywater Down, port-side window, bow to screen right.
# C3 opens with the ship still coming down out of the entry, nose low, and flattening
# out: the speeds and the attitude ease from their entry values to level cruise over
# LEVEL_SPAN, and the rest of the shot is the level slide.
ALTITUDE_M = 400.0        # above the canopy tops under the river valley (at level-off)
GROUND_SPEED = 16.0       # m/s level, along screen right; the world therefore slides left
SINK_RATE = 2.0           # m/s descent once level; the ground rises in frame at its own rate
ENTRY_SPEED = 30.0        # m/s along at C3 frame 0, still braking out of the descent
ENTRY_SINK = 16.0         # m/s down at C3 frame 0
NOSE_DOWN_DEG = 8.0       # attitude at frame 0. Through a port window a nose-down pitch is a
                          # roll of the picture: the horizon rises toward the bow (right)
LEVEL_SPAN = (0.4, 5.6)   # seconds: the attitude, sink and speed ease to level over this
LEVEL_OVERSHOOT_DEG = 0.7 # the nose comes up a touch past level and settles, ~5-7 s

# --- the lean: the passenger leans 5 cm toward the glass (cabin glass at ~1.2 m)
LEAN_M = 0.05
CABIN_DEPTH_M = 1.2
LEAN_SPAN = (6.5, 12.5)   # seconds; eased; after the ship is level, under Castle's line

# Castle, over the nature view (owner's script, 2026-10-19; arrival-audio/castle_lines.py):
# "So much greenery and life... it's like the travel reels from the canteen."
# Take v4_c3_reels_t03, first read (5.2 s), shipped as jangala_arrival_castle_reels. It starts
# once the ship is level and the shake has gone.
CASTLE_REELS_AT = 7.0
CASTLE_REELS_TAKE = ("v4_c3_reels_t03", 5.2)


def smoother(u):
    u = min(1.0, max(0.0, u))
    return u * u * u * (u * (6 * u - 15) + 10)


def level_u(t):
    """0 at frame 0 (descending, nose down) to 1 once level (LEVEL_SPAN)."""
    return smoother((t - LEVEL_SPAN[0]) / (LEVEL_SPAN[1] - LEVEL_SPAN[0]))


def speed(t):
    """(along m/s, sink m/s) at C3 time t."""
    u = level_u(t)
    return ENTRY_SPEED + (GROUND_SPEED - ENTRY_SPEED) * u, ENTRY_SINK + (SINK_RATE - ENTRY_SINK) * u


def nose_down_deg(t):
    """The ship's nose-down attitude: NOSE_DOWN_DEG at frame 0, easing level, with the
    flare carrying the nose a touch past level before it settles."""
    flare = math.sin(math.pi * min(1.0, max(0.0, (t - 3.6) / 3.6))) ** 2
    return NOSE_DOWN_DEG * (1 - level_u(t)) - LEVEL_OVERSHOOT_DEG * flare


_TRACK_DT = 1.0 / 240


def _track():
    xs, zs, x, z, t = [0.0], [0.0], 0.0, 0.0, 0.0
    while t < DURATION + 1.0:
        a0, s0 = speed(t)
        a1, s1 = speed(t + _TRACK_DT)
        x += (a0 + a1) / 2 * _TRACK_DT
        z += (s0 + s1) / 2 * _TRACK_DT
        t += _TRACK_DT
        xs.append(x)
        zs.append(z)
    return xs, zs


_XS, _ZS = _track()


def travelled(t):
    """(metres along, metres down) since C3 frame 0."""
    k = max(0.0, t) / _TRACK_DT
    i = min(int(k), len(_XS) - 2)
    f = k - i
    return _XS[i] * (1 - f) + _XS[i + 1] * f, _ZS[i] * (1 - f) + _ZS[i + 1] * f


def truck_at(t, distance_m):
    """Screen slide by time t for a body at this slant distance (px, leftward)."""
    return F_PX * travelled(t)[0] / distance_m


def rise_at(t, distance_m):
    """Screen rise by time t from the descent (px, upward), for ground bodies."""
    return F_PX * travelled(t)[1] / distance_m


def truck_px(distance_m):
    """Screen slide over the whole shot for a body at this slant distance (px, leftward)."""
    return truck_at(DURATION, distance_m)


def rise_px(distance_m):
    """Screen rise over the shot from the descent (px, upward), for ground bodies."""
    return rise_at(DURATION, distance_m)


# --- shake. Seconds `s` are counted from the start of the entry transition, so the
# entry (patrol-abeam/compose_entry.py) and C3 share one shake and the cut cannot show.
ENTRY_LEN = 7.0           # the entry transition's length; C3 frame 0 is s = ENTRY_LEN


def shake_envelope(s):
    """Camera shake amplitude in px: rising through the heat, peaking in the cloud, then
    easing away through C3's level-off (gone by about C3 5 s)."""
    rise = smooth((s - 0.6) / 3.4)
    fall = math.exp(-max(0.0, s - 5.2) / 1.8)
    return 0.6 + 8.4 * rise * fall if s < 5.2 else 0.6 * math.exp(-max(0.0, s - 5.2) / 2.5) + 8.4 * fall


def _noise(s, rate, seed):
    """Smooth 1-D value noise in -1..1 at `rate` keys per second (deterministic)."""
    k = s * rate
    i = math.floor(k)
    f = k - i
    f = f * f * (3 - 2 * f)

    def key(n):
        h = (n * 374761393 + seed * 668265263) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((h ^ (h >> 16)) & 0xFFFF) / 32767.5 - 1.0
    return key(i) * (1 - f) + key(i + 1) * f


def camera_shake(s):
    """(dx px, dy px, roll degrees, zoom) of the whole picture, cabin included, at entry
    second s: a fast buffet, a slower heave, and enough zoom that no edge shows."""
    a = shake_envelope(s)
    dx = a * (0.75 * _noise(s, 13.0, 1) + 0.35 * _noise(s, 2.7, 2))
    dy = a * (0.85 * _noise(s, 11.0, 3) + 0.45 * _noise(s, 2.1, 4))
    rot = a * 0.045 * (_noise(s, 7.0, 5) + 0.5 * _noise(s, 1.6, 6))
    zoom = 1.0 + (1.6 * a + abs(rot) * 0.0175 * 1100) / 540.0
    return dx, dy, rot, zoom


def world_buffet(s):
    """The airframe moving under the eye: a small extra jitter of the world alone."""
    a = 0.35 * shake_envelope(s)
    return a * _noise(s, 17.0, 7), a * 0.7 * _noise(s, 15.0, 8)


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
