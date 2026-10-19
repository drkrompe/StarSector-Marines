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

# PRODUCTION: the one window is the orange-and-cream cabin of forest-reveal-realism.blend,
# rendered from its own reveal camera ("02 REVEAL / near glass", frame 144), which pitches
# 19.29 degrees down (c2_cabin_render.py measures it into guides/c2-cabin.json). The 18.6
# above was read off the grey realism frame and is kept only for the blockout scripts.
CABIN_PITCH_DEG = 19.29

# Glass opening of realism-lookdev/window-realism-v1.png, in 1920x1080 frame pixels
# (measured: left jamb x = 157 + 0.172 y, right jamb x = 1764 - 0.172 y, sill y = 704;
# the opening runs out through the top of the frame).
APERTURE = [(157.0, 0.0), (1764.0, 0.0), (1643.0, 704.0), (278.0, 704.0)]
SILL_Y = 704.0


def basis(pitch_deg=None):
    p = math.radians(PITCH_DEG if pitch_deg is None else pitch_deg)
    fwd = (0.0, math.cos(p), -math.sin(p))
    right = (1.0, 0.0, 0.0)
    up = (0.0, math.sin(p), math.cos(p))
    return fwd, right, up


def project(pt, pitch_deg=None):
    """World point -> (x, y) frame pixels, or None when behind the camera."""
    fwd, right, up = basis(pitch_deg)
    d = sum(a * b for a, b in zip(pt, fwd))
    if d <= 1e-6:
        return None
    return (W / 2 + F_PX * sum(a * b for a, b in zip(pt, right)) / d,
            H / 2 - F_PX * sum(a * b for a, b in zip(pt, up)) / d)


def ray(px, py, pitch_deg=None):
    """Unit world direction through frame pixel (px, py)."""
    fwd, right, up = basis(pitch_deg)
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


# ---------------------------------------------------------------- the patrol, production
# Owner decision 2026-10-16: the patrol is a Hegemony Legion (XIV), a real model rendered as
# a Blender sequence by render_patrol_sequence.py. The Dominator numbers above are the
# blockout's stand-in. Model axes are these world axes (+X bow, +Y port, +Z dorsal), one
# hull unit per vanilla sprite pixel, 0.65 m per unit. geometry.json's 'c2' block places it:
# starboard extreme 105 m from the glass. The .blend is swappable (a detailing pass is in
# progress elsewhere): set C2_LEGION_BLEND, keep the object names geometry.json lists.
import os  # noqa: E402
from pathlib import Path  # noqa: E402

LEGION_DIR = Path(__file__).resolve().parents[2] / "legion-geometry"
LEGION_BLEND = Path(os.environ.get("C2_LEGION_BLEND", LEGION_DIR / "legion-geometry.blend"))
LEGION_GEOMETRY = LEGION_DIR / "geometry.json"
BROADSWORD_DIR = Path(__file__).resolve().parents[2] / "broadsword-geometry"
BROADSWORD_BLEND = Path(os.environ.get("C2_BROADSWORD_BLEND", BROADSWORD_DIR / "broadsword-geometry.blend"))

# Scale is per hull (owner, 2026-10-16): every number below that depends on the Legion's
# size is derived from its geometry.json metres_per_unit and dimensions, read at import, so
# a new figure is one re-render. The pass keeps its shape at any size: the starboard
# extreme stays NEAR_FLANK_M from the glass, the crown CROWN_BELOW_EYE_M below eye level,
# and the speed and the de-orbit rise scale with the hull so it still crosses the glass
# on the hail's clock (abeam at PATROL_T_MID, clear of the glass by about 13.5 s).
NEAR_FLANK_M = 105.0
CROWN_BELOW_EYE_M = 3.0
PLANNED_LENGTH_M = 191.7                   # the length the pass was timed for (0.65 m/unit)
import json as _json  # noqa: E402

LEGION_GEOM = _json.loads(LEGION_GEOMETRY.read_text(encoding="utf-8"))
M_PER_UNIT = float(LEGION_GEOM["metres_per_unit"])
_LO, _HI = LEGION_GEOM["dimensions"]["min"], LEGION_GEOM["dimensions"]["max"]
LEGION_LENGTH_M = (_HI[0] - _LO[0]) * M_PER_UNIT
LEGION_CENTRE_Y = NEAR_FLANK_M - _LO[1] * M_PER_UNIT       # model origin, metres out from the glass
LEGION_ORIGIN_Z = -CROWN_BELOW_EYE_M - _HI[2] * M_PER_UNIT  # model origin height
PATROL_SPEED = 28.0 * LEGION_LENGTH_M / PLANNED_LENGTH_M
PATROL_DROP_ACCEL = 8.0 * LEGION_LENGTH_M / PLANNED_LENGTH_M


def legion_origin(t):
    """World position (metres) of the Legion's model origin at C2 time t."""
    return (patrol_x(t), LEGION_CENTRE_Y, LEGION_ORIGIN_Z + patrol_rise(t))


# Scrutiny beat: WS 007 (starboard dorsal heavy autocannon) tracks Second Wind from the
# start and slews back to fore and aft on "Patrol has your signature". Every other mount is
# laid fore or aft (whichever its arc allows nearer its rest) for the whole pass: "mounts cold".
SCRUTINY_SLOT = "WS 007"
SCRUTINY_RELEASE = (45.20 - 37.70, 45.20 - 37.70 + 1.6)   # C2 seconds: the slew back to fore

# Fighters: the Broadsword cinematic model (broadsword-geometry), each scaled by its own
# metres_per_unit. The Legion XIV Elite also flies Hoplons; no Hoplon model exists yet. They are parented to the hull,
# so their paths are in model units relative to it and they inherit its pass and rise.
# Launches leave the starboard forward deck WS 013 along its launch vector and accelerate
# away forward; recoveries come up from aft along WS 011's recovery approach and settle
# onto its launch points. The camera faces the starboard side, so both decks are ours.
LAUNCHES = [  # (hull, deck, launch point index, C2 time it starts to roll)
    ("broadsword", "WS 013", 2, 4.10),
    ("broadsword", "WS 013", 0, 4.75),
]
LAUNCH_ACCEL = 70.0                        # units/s^2 along the launch vector, from rest
RECOVERIES = [  # (hull, deck, launch point index, C2 time the fighter crosses the mouth)
    ("broadsword", "WS 011", 0, 9.85),     # aft hangars recover only (their mouths face aft)
    ("broadsword", "WS 011", 1, 10.75),
]
RECOVERY_APPROACH_S = 4.4                  # seconds from first sight to the mouth
RECOVERY_RUN_UNITS = 245.0                 # path length flown along the approach in that time
RECOVERY_MOUTH_SPEED = 12.0                # units/s across the mouth
RECOVERY_GLIDE = 0.05                      # height gained per unit of distance still to fly


def fighter_pose(fighter, kind, t, geom):
    """(position in model units, engine throttle 0..1) of one fighter at C2 time t, or None
    while it does not exist yet. kind is 'launch' or 'recover'. Heading follows the path's
    tangent (the caller differentiates); a parked fighter keeps its deck's launch heading."""
    hull, slot, point, t0 = fighter
    deck = next(d for d in geom["flight_decks"] if d["slot"] == slot)
    lp = deck["launch_points"][point]
    d = deck["launch_direction"]
    if kind == "launch":
        tau = max(t - t0, 0.0)
        s = 0.5 * LAUNCH_ACCEL * tau * tau
        free = max(0.0, s - 40.0)             # clear of the hood it climbs and eases outboard
        side = -1.0 if d[1] < 0 else 1.0
        pos = (lp[0] + d[0] * s, lp[1] + d[1] * s + side * 0.10 * free, lp[2] + 0.0016 * free ** 1.6)
        return pos, (0.25 if t < t0 else min(1.0, 0.25 + tau / 0.6))
    tau = t0 - t                              # recovery: counts down to the mouth crossing
    mouth = deck["mouth"]["centre"]
    n = deck["mouth"]["normal"]
    a = (-n[0], -n[1])                        # straight in along the mouth's own axis
    if tau > RECOVERY_APPROACH_S + 0.5:
        return None
    if tau >= 0:
        b = RECOVERY_MOUTH_SPEED
        k = (RECOVERY_RUN_UNITS - b * RECOVERY_APPROACH_S) / RECOVERY_APPROACH_S ** 2
        dist = k * tau * tau + b * tau
        pos = (mouth[0] - a[0] * dist, mouth[1] - a[1] * dist, mouth[2] + RECOVERY_GLIDE * dist)
        return pos, 0.35
    u = smooth(-tau / 1.6)                     # on deck: roll from the mouth to its spot, easing to a stop
    pos = tuple(m + (l - m) * u for m, l in zip(mouth, lp))
    return pos, 0.35 * (1.0 - u)


def recovery_roll(fighter, t, fighter_span_m):
    """Bank (radians) a recovering fighter holds to thread a mouth narrower than its span:
    the horizontal extent of a span s rolled by r is s cos r. Rolled in over the last
    1.5 s of the approach, held through the mouth, 15% clearance."""
    deck = next(d for d in LEGION_GEOM["flight_decks"] if d["slot"] == fighter[1])
    width = deck["mouth"]["width"] * M_PER_UNIT
    need = 0.0 if fighter_span_m * 1.15 <= width else math.acos(min(1.0, width / (fighter_span_m * 1.15)))
    return need * smooth((t - (fighter[3] - 1.5)) / 1.2)


# ---------------------------------------------------------------- timing
# Absolute seconds on the hail timeline (build_hail_preview.timeline()); C2 time is
# absolute minus C2_START. Measured 2026-10-16 from the treated clips; compose_blockout.py
# re-derives them from the catalog and refuses to run if they have moved.
C2_START = 37.70                           # C1's cut: 0.3 s before Approach keys up for line 7
C2_LEN = 15.0
ENTRY_START = C2_START + C2_LEN            # 52.70
ENTRY_LEN = 7.0                            # cut to C3 at 59.70 (first-open-sky/c3_core.ENTRY_LEN)
HAIL = {                                   # absolute seconds
    "squelch_open_7": 38.00,
    "line7": (38.16, 46.64),
    "line7_mounts_cold": (42.42, 44.74),   # "Keep your mounts cold, and stay in the corridor."
    "line7_patrol": (45.20, 46.54),        # "Patrol has your signature."
    "squelch_close_7": (46.64, 46.86),
    "line8": (47.46, 51.46),
}
# Castle to the Commander over the entry, off the hail (arrival-audio/castle_lines.py: entry 0.16-4.0).
CASTLE_PLANETSIDE = (ENTRY_START + 0.16, ENTRY_START + 4.0)

# Entry transition, seconds from ENTRY_START: a descent, not a wipe (owner, 2026-10-19).
# Flames and smoke stream across the glass from bottom right to top left, the way the air
# goes past a ship coming down bow first, while the camera and window shake
# (first-open-sky/c3_core.camera_shake, shared with C3 so the cut cannot show).
FLOW_DEG = 27.0                            # the stream's angle above horizontal, travelling up-left
HEAT = (0.15, 2.40, 4.60, 5.70)            # heat glow: starts, full, starts to quench, gone
SMOKE = (1.60, 3.00, 4.80, 5.60)           # smoke torn off the sheath: in, full, thinning, gone
CLOUD_IN = (4.10, 5.40)                    # cloud streams across and over the flames
WHITE = (5.40, 6.00)                       # cloud whiteout
SETTLE = (6.00, 6.90)                      # the last of the cloud settles onto C3's frame 0
# The same phases under the names the blockout and guide sheets (compose_blockout.py,
# make_guides.py) were drawn with.
GLOW_IN, GLOW_HOLD, WHITE_HOLD = (HEAT[0], HEAT[1]), (HEAT[1], HEAT[2]), WHITE


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)
