"""Shared camera, light, scale and timing model for C2 PATROL ABEAM and the entry
transition (c2-layout.md).

Pure standard library, so Blender's Python (patrol_guide.py) and ordinary Python
(make_guides.py, compose_blockout.py) read one model and cannot drift apart.

World axes, in metres, camera at the origin:
  +X  Second Wind's bow, which is screen RIGHT through this window (the established
      window flow: the parallax-window study measured scenery moving right to left,
      so the world moves aft = right to left, and the window looks out to port);
  +Y  outboard, out through the glass (port);
  +Z  up.
"""
import math

W, H, FPS = 1920, 1080, 24

# ---------------------------------------------------------------- camera
# The cabin camera the realism window frame was painted for. Lens and sensor are the
# forest-reveal / parallax-window cabin camera (26 mm on a 36 mm sensor). The pitch is
# measured from the frame itself: its jambs converge to a vertical vanishing point
# about 4670 px below the top of the frame, which a 26 mm camera reaches when looking
# 18.6 degrees down (the Blender cabin camera reads 19.3).
LENS_MM, SENSOR_MM = 26.0, 36.0
F_PX = W * LENS_MM / SENSOR_MM            # 1386.7 px focal length at 1920 wide
PITCH_DEG = 18.6                           # camera looks this far below horizontal
EYE_TO_GLASS_M = 1.1                       # camera to the glass (Blender cabin: 1.0-1.3 m)

# Glass opening of realism-lookdev/window-realism-v1.png, in 1920x1080 frame pixels
# (measured: left jamb x = 157 + 0.172 y, right jamb x = 1764 - 0.172 y, sill y = 704;
# the opening runs out through the top of the frame).
APERTURE = [(157.0, 0.0), (1764.0, 0.0), (1643.0, 704.0), (278.0, 704.0)]
SILL_Y = 704.0


def basis():
    p = math.radians(PITCH_DEG)
    fwd = (0.0, math.cos(p), -math.sin(p))
    right = (1.0, 0.0, 0.0)
    up = (0.0, math.sin(p), math.cos(p))
    return fwd, right, up


def project(pt):
    """World point -> (x, y) frame pixels, or None when behind the camera."""
    fwd, right, up = basis()
    d = sum(a * b for a, b in zip(pt, fwd))
    if d <= 1e-6:
        return None
    return (W / 2 + F_PX * sum(a * b for a, b in zip(pt, right)) / d,
            H / 2 - F_PX * sum(a * b for a, b in zip(pt, up)) / d)


def ray(px, py):
    """Unit world direction through frame pixel (px, py)."""
    fwd, right, up = basis()
    a, b = (px - W / 2) / F_PX, (H / 2 - py) / F_PX
    v = [f + a * r + b * u for f, r, u in zip(fwd, right, up)]
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def in_aperture(x, y):
    if y > SILL_Y:
        return False
    return 157.0 + 0.172 * y < x < 1764.0 - 0.172 * y


# ---------------------------------------------------------------- light
# One sun. C1 keys Corvus from the upper left of its exterior camera
# (c1_core.SUN_IN_CAMERA). Carried into Second Wind's frame, that sun is aft, to port and
# above; through a port window it is upper left and outside the glass (29.5 degrees up,
# 46 degrees aft of the window normal, 65 degrees off the lens axis), so it is out of
# frame, it throws a hard patch onto the forward (right) jamb and sill, and the patrol's
# near flank (which faces us, away from it) is in shadow.
SUN = (-0.622, 0.609, 0.492)               # toward Corvus, world axes above
SUN_COLOUR = (1.0, 0.97, 0.90)
# Jangala below is the only other source: blue planet-shine from beneath, roughly a
# twentieth of the sun on a surface facing straight down. No ambient anywhere.
PLANETSHINE = 0.05

# ---------------------------------------------------------------- Jangala
PLANET_RADIUS_M = 6.0e6                    # Earth-like terran world (assumed; vanilla gives no km)
ALTITUDE_M = 2.0e5                         # 200 km: above the ~120 km entry interface
ORBITAL_SPEED = math.sqrt(3.986e14 * (PLANET_RADIUS_M / 6.371e6) ** 3 / (PLANET_RADIUS_M + ALTITUDE_M))


def horizon_dip_deg():
    return math.degrees(math.acos(PLANET_RADIUS_M / (PLANET_RADIUS_M + ALTITUDE_M)))


# ---------------------------------------------------------------- the patrol
# Hegemony Dominator-class cruiser: C1's hero warship, now the patrol. Vanilla sprite
# dominator_hegemony.png is 220 x 180 px (bow up); C1 reads sprite pixels as metres.
PATROL_LENGTH_M = 180.0
PATROL_BEAM_M = 220.0
PATROL_NEAR_FLANK_M = 105.0                # horizontal distance, window to near flank
PATROL_CENTRE_Y = PATROL_NEAR_FLANK_M + PATROL_BEAM_M / 2
PATROL_BASE_Z = -24.0                      # heightfield base: hull spans about -46 .. -3 m
PATROL_SLAB_M = 0.12 * PATROL_LENGTH_M     # flank wall below the crown (21.6 m)
PATROL_BOTTOM_Z, PATROL_DECK_Z = PATROL_BASE_Z - PATROL_SLAB_M, PATROL_BASE_Z + 20.7
PATROL_SPEED = 28.0                        # m/s overtaking (relative), bow first, left to right
PATROL_T_MID = 8.0                         # C2 seconds: hull centred abeam, under "Patrol has your signature"
# Second Wind begins its de-orbit on Castle's read-back (line 8): it drops away at 0.8 g
# while the patrol holds its orbit, so in the cabin's frame the patrol rises. A hull this
# broad cannot clear a 60-degree window sideways at a watchable speed (its far side, 340 m
# out, needs 570 m of travel); rising out through the top of the glass is how it leaves.
PATROL_DROP_T = 9.6                        # C2 seconds
PATROL_DROP_ACCEL = 8.0                    # m/s^2, relative


def patrol_x(t):
    """Patrol centre along +X at C2 time t (constant velocity: it is coasting past)."""
    return PATROL_SPEED * (t - PATROL_T_MID)


def patrol_rise(t):
    """Patrol height gained relative to Second Wind once the de-orbit burn starts."""
    return 0.5 * PATROL_DROP_ACCEL * max(0.0, t - PATROL_DROP_T) ** 2


def patrol_position(t):
    """Patrol heightfield origin (centre of its plan, base of its crown) at C2 time t."""
    return (patrol_x(t), PATROL_CENTRE_Y, PATROL_BASE_Z + patrol_rise(t))


# ---------------------------------------------------------------- timing
# Absolute seconds on the hail timeline (build_hail_preview.timeline()); C2 time is
# absolute minus C2_START. Measured 2026-10-16 from the treated clips; compose_blockout.py
# re-derives them from the catalog and refuses to run if they have moved.
C2_START = 37.70                           # C1's cut: 0.3 s before Approach keys up for line 7
C2_LEN = 15.0
ENTRY_START = C2_START + C2_LEN            # 52.70
ENTRY_LEN = 3.0                            # cut to C3 at 55.70
HAIL = {                                   # absolute seconds
    "squelch_open_7": 38.00,
    "line7": (38.16, 46.64),
    "line7_mounts_cold": (42.42, 44.74),   # "Keep your mounts cold, and stay in the corridor."
    "line7_patrol": (45.20, 46.54),        # "Patrol has your signature."
    "squelch_close_7": (46.64, 46.86),
    "line8": (47.46, 51.46),
    "line9": (52.86, 55.58),
    "line9_well": (53.06, 53.47),          # "Well."
    "line9_knows": (54.00, 55.27),         # "Somebody knows we're here."
}

# Entry transition, seconds from ENTRY_START. Both wipes travel right to left: the glow
# grows from the bow side, the cloud deck streams aft, the same way the world moves.
GLOW_IN = (0.00, 1.10)                     # orange heat glow wipes across the glass
GLOW_HOLD = (1.10, 1.50)
CLOUD_IN = (1.30, 2.50)                    # cloud wipes across and over the glow
WHITE_HOLD = (2.50, 3.00)                  # cloud whiteout; C3 opens out of white


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)
