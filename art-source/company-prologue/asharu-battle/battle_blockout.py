"""Asharu battle: the blockout scene and its key frames (see asharu-battle-shot.md).

    python battle_blockout.py                 # build in Blender, render every key frame, label them
    python battle_blockout.py --frames k1,k4  # some key frames only
    python battle_blockout.py --samples 16    # quicker, noisier
    python battle_blockout.py --post windows  # ... and post the run to the Drive review folder
    python battle_blockout.py --skip-render   # relabel the last raw renders
    python battle_blockout.py --run r7        # output under build/asharu-battle/r7/ instead of today's date;
                                              # --post then dates the review folder by the day r7 began
    python battle_blockout.py --rerender      # render every frame even if a finished one is on disk
    python battle_blockout.py --root DIR      # put run folders under DIR (a render-queue job's {work})
    python battle_blockout.py --date D        # the review folder's date, when the run starts (default today)
    python battle_blockout.py --half          # render at half size (a light check; labels drawn at full size)
    python battle_blockout.py --surface base  # the game's desert01 alone, without the detail tiles (the control)
    python battle_blockout.py --stage-only    # build and pose the scene in Blender, render nothing (a pre-flight)
    python battle_blockout.py --stage-only --los   # ... and ray-cast every shot: does the round meet its target?
    python battle_blockout.py --combined      # one pass per key frame; the default (from r10) renders them as
                                              # backdrop / ships / effects layers in one EXR (raw/<kf>.layers.exr),
                                              # composed into the still (composite_layers.py)
    python battle_blockout.py --plates DIR    # ... with the planet session's painted backdrops (<kf>.backdrop.exr)
    python battle_blockout.py --motion-preview   # the whole sequence in Workbench, an MP4 to judge motion (asharu_preview.py)

A frame already finished in the run folder (its PNG, its labels, and the same samples and source)
is not rendered again, so a job stopped part-way resumes where it stopped. Pass --run with a fixed
name for a job that may resume on another day.

Run as plain Python it is the driver: it launches Blender on itself (blender_path.py), then
labels the frames with Pillow, draws the orbit diagram and writes NOTES.md. Inside Blender it
builds the scene and renders. Output goes to <repo>/build/asharu-battle/<date>/ (ignored);
renders are never committed.

World frame, metres, co-moving with the convoy: +X prograde (the direction of orbit), +Y north,
+Z local up. Asharu's centre is straight below at (0, 0, -(R + ALT)). The planet is a dense
spherical cap under the battle so its limb is a true curve, painted with the game's own desert
map read from the install (never copied) and broken up with procedural relief at the scales a
low orbit sees.

Hulls. Finished cinematic builds are appended once from build/asharu-battle/builds/<dir>/ and placed
as collection instances at their own metres per unit. A hull with no build yet is a stand-in:
the game sprite's silhouette raised into a lens-shaped slab, painted with the sprite, at the
size ship_scale.py's option C solves for it (roster.py). Every drive carries a stand-in glow and
plume at its exhaust anchor or the .ship's engine slot.
"""
import json
import math
import os
import re
import subprocess
import sys
import zlib
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
ART = PROLOGUE.parent
REPO = ART.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(PROLOGUE))
sys.path.insert(0, str(ART))
import debris_field
import wreck_meshes
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'asharu-wrecks'))
import wreck_kit  # noqa: E402  the wreck kit's swap-in contract (plain Python): which kit wreck fills each box
import roster
from motion import boarding_run, fleet_at   # ships move about their stations: seeded weave, bank and breathing spacing  # noqa: E402
from motion import (A_FULL, IDLE, Pose, camera, carried, drift, follow, held, keyed, mass_class, recoil, riding,  # noqa: E402
                    station, struck)

# One folder per finished build (roster.FINISHED's dir), copied whole from Drive.
BUILDS = Path(os.environ.get('ASHARU_BUILDS') or REPO / 'build' / 'asharu-battle' / 'builds')


def build_file(f):
    return BUILDS / f['dir'] / f['file']


def build_manifest(f):
    return json.loads((BUILDS / f['dir'] / f.get('manifest', 'manifest.json')).read_text())
W, H = 1920, 1080

# Asharu and the orbit. Asharu's real size is not given anywhere in the game or the story; a
# small dry world a little smaller than Earth reads right and keeps the limb curving in frame.
R = 5.0e6          # planet radius, m
ALT = 250e3        # convoy altitude, m
SUN_EL, SUN_AZ = 16.0, -40.0   # degrees: low morning sun, ahead of the convoy and to the south
MAP_LAT, MAP_LON = 12.0, 95.0   # where on the game's desert01 map the battle is fought
# ImageGen detail tiles and settlements laid over desert01 under the battle (surface/README.md).
# --surface base renders the game map alone, the control.
SURFACE_DETAIL = True

# --------------------------------------------------------------------------------- key frames
# Each key frame: what the camera sees and where every ship is. Placements are
# (x, y, z, heading deg, pitch deg, roll deg, throttle 0-1). Effects are listed per frame.
CONVOY = {   # a working convoy in loose column: escorts at point, flanks and rear
    'wolf-m': (2300, -200, 60, 0, 1, 3, .6),
    'lasher-lead': (1500, 120, 80, 0, 1, -2, .6),
    'lasher-a': (950, -380, 140, 0, 0, 3, .6),
    'mule': (700, 260, -20, 0, 0, -2, .5),
    'buffalo-a': (350, -280, 50, 0, 1, 4, .5),
    'colossus': (0, 0, 0, 0, 0, 0, .5),
    'morrow': (-40, -165, -35, 0, 1, -3, .5),
    'phaeton': (-350, 300, 20, 0, 0, 2, .5),
    'tarsus': (-650, 90, 30, 0, 0, 2, .5),
    'buffalo-b': (-900, -300, -40, 0, -1, -3, .5),
    'dram': (-1150, 260, 60, 0, 1, 2, .5),
    'lasher-b': (-750, 640, -60, 0, -1, -4, .6),
    'hound-m': (-1500, -150, 110, 0, 2, 3, .6),
}


def climb(base, dx=0, dy=0, dz=0, heading=None, pitch=None, roll=None, throttle=None):
    x, y, z, h, p, r, t = base
    return (x + dx, y + dy, z + dz, h if heading is None else heading, p if pitch is None else pitch,
            r if roll is None else roll, t if throttle is None else throttle)


PIRATES_RISING = {   # coming up out of the platform, behind and below the convoy, climbing to it
    'falcon-p': (-6200, 250, -2900, 4, 14, -3, 1),
    'manticore-p': (-6900, -650, -3300, 2, 13, 4, 1),
    'colossus3': (-8200, 100, -3700, 3, 10, 2, .8),
    'buffalo2-a': (-7700, -1300, -3500, 0, 12, 5, 1),
    'hound-p-c': (-8600, 1500, -3900, 8, 11, -4, 1),
    'wolf-p-a': (-5200, -300, -2400, -2, 17, 8, 1),
    'wolf-p-b': (-5500, 700, -2600, 5, 16, -9, 1),
    'hound-p-a': (-5800, -1000, -2700, -4, 15, 6, 1),
    'hound-p-b': (-6000, 1200, -2900, 7, 15, -5, 1),
    'kite-p-a': (-6600, -150, -3100, 1, 14, 0, 1),
    'kite-p-b': (-6700, 450, -3150, 3, 14, 2, 1),
}

# The derelict Asharu platform: about 4.5 km from the convoy, astern and below. It sat 10.5 km out at
# (-9500, 1200, -4300) until 2026-10-22, when the pirates had to cover 7 km in the six seconds of the
# S4 to S5 cut to reach their S6 positions. It was brought in along its own line of sight to the
# convoy, so the convoy sees the field from the same direction (TOWARD_CONVOY) and the debris field,
# its covers and its keep-outs, which the wreck kit is fitted to, are the same box for box.
PLATFORM_FAR = (-9500, 1200, -4300)
PLATFORM = tuple(c * .43 for c in PLATFORM_FAR)


PLATFORM_HD = -70.0     # the platform's long axis, turned so the convoy sees it broadside from its north side


def on_platform(offset):
    """A point in the platform's own metres (+X along its length, +Y its north side) in the battle frame."""
    c, s = math.cos(math.radians(PLATFORM_HD)), math.sin(math.radians(PLATFORM_HD))
    return (PLATFORM[0] + c * offset[0] - s * offset[1], PLATFORM[1] + s * offset[0] + c * offset[1],
            PLATFORM[2] + offset[2])


def moored(offset, hd=0.0, pt=0.0, rl=0.0):
    """A pirate hull lying cold at the platform: offset in the platform's metres, her attitude
    relative to it, and her drives dark."""
    return on_platform(offset) + (hd + PLATFORM_HD, pt, rl, 0.0)


def platform_parts():
    """The derelict platform as parts in its own metres (on_platform places them): the game's own description,
    "castoff bulk-carrier segments welded between a honeycomb of emptied fuel tanks", with empty
    hangars. [(kind, name, centre, size, (hd, pt, rl))]: a box's size is its half extents; a drum's
    is (radius, half length) along its own X. A sagging double girder carries five mismatched hulls,
    one knocked askew; clusters of drums are packed between them with gaps where drums have gone;
    two open hangar bays hang under and beside the hulls; a broken mast stands off the forward end.
    Nothing is a sphere (the owner: no orbs) and nothing is a flat card."""
    parts = []

    def box(name, c, h, a=(0, 0, 0)):
        parts.append(('box', name, c, h, a))

    for side in (-1, 1):                           # the spine: two girders, sagging 4 degrees at the middle
        box('Platform girder', (-230, side * 16, 6), (190, 3, 3), (0, -2, 0))
        box('Platform girder', (190, side * 16, 6), (230, 3, 3), (0, 2, 0))
    for x in range(-400, 401, 70):                 # cross ties
        box('Platform tie', (x, 0, 6 + abs(x) * .035), (2.5, 18, 2.5))
    for x, h, a in (((-330, (55, 26, 20), (0, 0, 6))), ((-215, (40, 30, 24), (3, 0, -4))),
                    ((-70, (65, 34, 28), (0, 0, 0))), ((110, (50, 28, 22), (-5, 3, 0))),
                    ((265, (45, 24, 18), (14, -2, 9)))):
        box('Platform bulk segment', (x, 0 if x != 265 else 12, 0), h, a)       # the castoff hulls
    hexes = [(0, 0)] + [(16 * math.cos(math.radians(a)), 16 * math.sin(math.radians(a))) for a in range(0, 360, 60)]
    ring2 = [(32 * math.cos(math.radians(a)), 32 * math.sin(math.radians(a))) for a in range(30, 360, 60)]
    for x, cells, gone in ((-272, hexes, {2, 5}), (20, hexes + ring2, {4, 9, 11}), (190, hexes, {1})):
        for n, (y, z) in enumerate(cells):         # the honeycomb of emptied fuel tanks, drums along X
            if n not in gone:
                parts.append(('drum', 'Platform tank', (x, y, z + 4), (7.6, 18 if x != 20 else 26), (0, 0, 0)))
    # hangar bay under the fourth hull, open to the south: floor, back wall, end walls
    box('Platform hangar', (110, -28, -66), (55, 38, 2))
    box('Platform hangar', (110, 10, -44), (55, 2, 22))
    for x in (55, 165):
        box('Platform hangar', (x, -28, -44), (2, 38, 22))
    # hangar bay beside the first hull, open to the north
    box('Platform hangar', (-330, 66, -27), (55, 40, 2))
    box('Platform hangar', (-330, 66, 23), (55, 40, 2))
    for x in (-385, -275):
        box('Platform hangar', (x, 66, -2), (2, 40, 23))
    box('Platform mast', (330, 0, 60), (2, 2, 70), (0, 18, 0))                  # broken mast and its stub arms
    box('Platform mast', (318, 0, 105), (18, 1.5, 1.5), (0, 0, 25))
    return parts


def to_platform(world):
    """A battle-frame point in the platform's own metres (on_platform's inverse)."""
    c, s_ = math.cos(math.radians(-PLATFORM_HD)), math.sin(math.radians(-PLATFORM_HD))
    x, y, z = (world[i] - PLATFORM[i] for i in range(3))
    return (c * x - s_ * y, s_ * x + c * y, z)


# The convoy's line of sight onto the platform, in its metres: what a hiding ship must be out of
TOWARD_CONVOY = debris_field.unit(to_platform((0.0, 0.0, 0.0)))

# Hiding (owner, 2026-10-22: eleven hulls pouring out of the station read as a clown car). The station
# hides only the two ships its hangars and hulls can hold; the rest lie out in its debris field, each
# behind a wreck at least her size set between her and the convoy (debris_field.cover_piece), across
# the whole field and at every depth, so the launch comes out of many places at once. Four are left
# half-seen in S3 (peek: a nose past a hulk's end, or, negative, a drive cluster), so S4 reveals what
# the audience half-saw. at: the platform's metres; hd, pt, rl relative to it.
HIDES = {
    'manticore-p': dict(at=(110, -52, -40), hd=-90, pt=0, rl=4),            # the station's south hangar
    'colossus3': dict(at=(-70, -45, -72), hd=180, pt=-6, rl=175),            # under its big hull, inverted
    # nose-on to S3's camera, sunlit, lamps on her face: she slides out toward the lens as her drives
    # light behind her, the hulk lying behind her lit orange by them (peek +1.6; coordinator, 2026-10-24)
    'falcon-p': dict(at=(-950, -420, -180), hd=30, pt=-4, rl=12,
                     cover='hulk', cover_size=(230, 201, 112), peek=1.6),
    'buffalo2-a': dict(at=(480, -520, -260), hd=150, pt=6, rl=-8, cover='block'),
    'hound-p-c': dict(at=(1080, -160, 90), hd=-20, pt=8, rl=35, cover='hulk', peek=.75),
    'wolf-p-a': dict(at=(-1180, 140, 130), hd=160, pt=-5, rl=-20, cover='slab'),
    'wolf-p-b': dict(at=(-380, 420, -330), hd=-35, pt=4, rl=80, cover='hulk', peek=.75, cover_size=(103, 90, 45)),
    'hound-p-a': dict(at=(230, 450, 190), hd=140, pt=-6, rl=180, cover='block', peek=-.55),
    'hound-p-b': dict(at=(880, 320, -210), hd=-25, pt=10, rl=-15, cover='hulk'),
    'kite-p-a': dict(at=(-980, -640, -320), hd=155, pt=12, rl=40, cover='slab'),
    'kite-p-b': dict(at=(-160, -780, 260), hd=-15, pt=-8, rl=-30, cover='hulk', peek=.75),
}
PIRATES_MOORED = {k: moored(h['at'], hd=h['hd'], pt=h['pt'], rl=h['rl']) for k, h in HIDES.items()}
SHIP_LENGTH = {m['key']: roster.size_row(m)['length_m'] for m in roster.MERCHANT + roster.PIRATE + roster.DRONES}

# Where the Tarsus rides in the Colossus Mk.III's grapples, in Mk.III metres: the build's
# grapples.tarsus_origin_units (20.398, 0.49, -78.254) at 0.495 m/u, axes aligned.
CLAMP_TARSUS_M = (10.10, 0.24, -38.74)


def aboard(parent, offset, thr=0):
    """A ship riding with another: the parent's spec moved by offset (metres, parent frame),
    with the parent's heading, pitch and roll. Same rotation order as frame_of."""
    x, y, z, hd, pt, rl = parent[:6]
    ch, sh = math.cos(math.radians(hd)), math.sin(math.radians(hd))
    cp, sp = math.cos(math.radians(-pt)), math.sin(math.radians(-pt))
    cr, sr = math.cos(math.radians(rl)), math.sin(math.radians(rl))
    ox, oy, oz = offset
    oy, oz = cr * oy - sr * oz, sr * oy + cr * oz                    # roll about X
    ox, oz = cp * ox + sp * oz, -sp * ox + cp * oz                   # pitch about Y
    ox, oy = ch * ox - sh * oy, sh * ox + ch * oy                    # heading about Z
    return (x + ox, y + oy, z + oz, hd, pt, rl, thr)


def _attitude(hd, pt, rl):
    """frame_of's rotation, Rz(hd) Ry(-pt) Rx(rl), as rows."""
    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    h, p, r = math.radians(hd), math.radians(-pt), math.radians(rl)
    rz = [[math.cos(h), -math.sin(h), 0], [math.sin(h), math.cos(h), 0], [0, 0, 1]]
    ry = [[math.cos(p), 0, math.sin(p)], [0, 1, 0], [-math.sin(p), 0, math.cos(p)]]
    rx = [[1, 0, 0], [0, math.cos(r), -math.sin(r)], [0, math.sin(r), math.cos(r)]]
    return mul(mul(rz, ry), rx), mul


def near(parent, offset, hd=0.0, pt=0.0, rl=0.0, thr=.3):
    """A small craft working about another ship: at offset (metres, the parent's frame) with its
    own heading, pitch and roll relative to the parent's, composed into the battle frame."""
    x, y, z = aboard(parent, offset)[:3]
    m, mul = _attitude(*parent[3:6])
    m = mul(m, _attitude(hd, pt, rl)[0])
    pitch = math.degrees(math.asin(max(-1.0, min(1.0, m[2][0]))))
    return (x, y, z, math.degrees(math.atan2(m[1][0], m[0][0])), pitch,
            math.degrees(math.atan2(m[2][1], m[2][2])), thr)


def boat(mother, prize, seat_off, t0, t1, t, launch_off=(-8.0, 0.0, -12.0), carrier=None, hold=0.0):
    """A boarding boat (a Hermes) on its run from under its mother craft to a seat on the prize's deck
    (motion.boarding_run). launch_off and seat_off are metres in the mother's and the prize's frames;
    it leaves down and aft from the mother's belly, where the camera cannot see it launch, and comes in
    along the prize's deck from astern, so it sits down facing aft, gear down."""
    m, _ = _attitude(*mother[3:6])
    p, _ = _attitude(*prize[3:6])
    down_aft = [-.8 * m[i][2] - .6 * m[i][0] for i in range(3)]
    n = math.hypot(*down_aft)
    args = (aboard(mother, launch_off)[:3], [c / n for c in down_aft], aboard(prize, seat_off)[:3],
            [p[i][0] for i in range(3)], near(prize, seat_off, hd=180.0, thr=0.0), t0, t1)
    run = Pose(tuple(boarding_run(*args, t, hold=hold)), t, lambda tt: tuple(boarding_run(*args, tt, hold=hold)))
    return run if carrier is None else carried(run, carrier)     # the seat moves with the prize


# ------------------------------------------------------------------------- flown motion (owner, 2026-10-22)
# Every ship in a key frame's shot flies (motion.keyed and friends): a key frame's pose is where her
# flight passes at the key time (the t each shot passes), so a lit still is a sample of the motion
# the preview plays. Speeds are relative to the convoy's co-moving frame: a ship on station is at
# rest in it, a raider closing from astern flies forward through it, a ship falling out drifts aft.
SPEED = {'frigate': 210.0, 'destroyer': 170.0, 'cruiser': 130.0, 'freighter': 90.0}
WOBBLE = {'frigate': 6.0, 'destroyer': 4.0, 'cruiser': 2.5, 'freighter': 1.5}
JINK = {'frigate': (6.0, 28.0), 'destroyer': (3.5, 14.0), 'cruiser': (1.5, 6.0), 'freighter': (.6, 2.0)}


def fly(key, pose, t, keys=(), v=None, **kw):
    """A ship flown through attitude keys (motion.keyed) at her class's closing speed unless v says.
    Her drives burn with the acceleration the motion implies (motion.A_FULL); thrust=None keeps the
    keyed throttle instead (a ship whose drives are out, or one holding a burn the story sets)."""
    c = mass_class(key)
    kw.setdefault('wobble', WOBBLE[c])
    kw.setdefault('thrust', A_FULL[c])
    return keyed(key, pose, t, keys, v=SPEED[c] if v is None else v, **kw)


def jinking(key, pose, t, t0, t1, v=None, amp=1.0, **kw):
    """A ship on her run, jinking: seeded heading and roll keys about her pose every 2 s from t0 to
    t1 (none within a second of the key time), as hard as her class throws itself about."""
    c = mass_class(key)
    dh, dr = JINK[c]
    v = SPEED[c] if v is None else v
    h = zlib.crc32(key.encode())
    keys, n = [], 0
    tt = t0
    while tt <= t1 + 1e-6:
        if abs(tt - t) >= 1.0:
            r = ((h >> (n % 24)) & 0xFF) / 255.0 * 2 - 1
            side = 1 if n % 2 else -1
            keys.append(dict(t=tt, hd=pose[3] + side * dh * amp * (.5 + .5 * abs(r)), rl=pose[5] + side * dr * amp * r,
                             pt=pose[4] + dh * .4 * amp * r))
        tt += 2.0
        n += 1
    # still gaining on the column: speed builds through the shot, so the drives burn
    keys[0]['v'], keys[-1]['v'] = v * .8, v * 1.15
    return fly(key, pose, t, keys, v=v, **kw)
# The convoy's escorts work up and down the column (m/s, relative to it): the lead moving up, the
# rear ones dropping back, so the column reads as a moving formation rather than a parked one.


def convoy_at(t):
    """The convoy at t: freighters weaving on station, escorts working along the column."""
    return fleet_at(CONVOY, t)     # escorts too: each moves along her own nose (S4, S6 notes)


def track(pose, point, share=1.0, lag=0.0):
    """A camera point that sits at point at the pose's key time and then moves with that ship (share
    of her motion, lag seconds behind): a camera that pans or travels with the action."""
    return follow(pose, tuple(point[n] - pose[n] for n in range(3)), lag, share)


def lit(t0, level=.6, ramp=1.5):
    """A cold ship's drives coming up from t0: the throttle, as a function of time."""
    return lambda t: level * max(0.0, min(1.0, (t - t0) / ramp))


# Morrow's drone bay (Shepherd hull units x 0.78): the three Borer launch points, cradle seat on the
# point, the drone's root 1.44 m above it (Borer manifest cradle_seat). The bay lifts straight up (+Z).
BORER_BAY_M = [(-2.34, 14.82, -1.73), (9.36, 14.82, -1.73), (20.28, 14.82, -1.73)]
K4_COLOSSUS = (0, 0, 0, 0, -3, 6, .2)
K4_MORROW = (-10, 35, -78, 0, 8, 4, 1)
COLOSSUS_PORT_POD_M = (-62, 30, 0)    # the shot-out port drive pod (k4's blast, fire and debris)

STAR_GAIN = 1.0      # starfield brightness on the key frames (0 hides it)

C3_PRIZE = (-150, 330, 60, 30, -4, -6, .3)

# S7: the Tarsus fallen out of the column, a Kite (P) on each beam, and their boats coming over to her deck
K3B_T = 86
K3B_TARSUS = (-650, 90, 30, 6, -2, 9, 0)
K3B_KITE_A = (-620, 160, 40, 6, -1, 9, .2)     # port beam
K3B_KITE_B = (-700, 25, 0, 4, 2, 6, .2)        # starboard beam
# the boats' seats on her deck (Tarsus metres; deck heights checked by the seat cast, ASHARU seat)
K3B_SEATS = {'hermes-p-a': (2.0, -4.0, 6.43), 'hermes-p-b': (-22.0, 3.0, 8.0), 'hermes-p-c': (24.0, -4.0, 6.0)}

# S3-S4: the launch, one motion across the cut. Drives light in a ripple from the Falcon (P), each
# hull eases out of her hiding place along her nose, turns onto her climb and accelerates; S3 stills
# 34 s of it (only the Falcon showing a light) and S4 picks the same flights up mid-climb at 40 s.
K1B_T = 34
K2_T = 40
LAMPS_LEAD_S = 1.6      # a pirate's lamps wake this long before her drives light (S3's beat), over 0.3 s
# S3's subject slides out toward the camera on her thrusters from the moment her lamps wake, and
# faster, clear of anything by 34 s (coordinator, 2026-10-24)
EARLY_EASE = {'falcon-p'}
# The keep-outs the seeded debris field was laid against, where a ship has since moved (_field)
FIELD_LAID_AS = {'falcon-p': dict(peek=-.75)}
LAUNCH = {'falcon-p': 33.8, 'manticore-p': 35.0, 'colossus3': 35.6, 'kite-p-a': 36.0, 'wolf-p-a': 36.3,
          'hound-p-a': 36.6, 'wolf-p-b': 35.4, 'kite-p-b': 37.2, 'hound-p-b': 37.5, 'buffalo2-a': 37.8,
          'hound-p-c': 38.2}
CLIMB_SPEED = {'frigate': 300.0, 'destroyer': 240.0, 'cruiser': 190.0, 'freighter': 150.0}


def _near(a, b):
    """b moved by whole turns to within 180 degrees of a."""
    return a + ((b - a + 180.0) % 360.0 - 180.0)


def launch(key, hide, t_light, climb, early=None):
    """A hidden ship's launch: dark at hide, drives lighting at t_light, easing out along her nose for
    four and a half seconds until she is clear of the structure, then turning onto her climb attitude
    (climb's heading, pitch and roll) as she gathers speed, then burning up toward the column at her class's speed.
    A ship in EARLY_EASE starts easing out on her thrusters as her lamps wake, and faster."""
    x, y, z, hd, pt, rl, _ = hide
    chd, cpt, crl = _near(hd, climb[3]), climb[4], _near(rl, climb[5])
    v = CLIMB_SPEED[mass_class(key)]
    early = key in EARLY_EASE if early is None else early
    if early:
        keys = [dict(t=t_light - LAMPS_LEAD_S, v=0.0, thr=0.0), dict(t=t_light - .05, v=10.0, thr=0.0),
                dict(t=t_light + .6, v=20.0, thr=.25), dict(t=t_light + 1.2, v=32.0, thr=.5),
                dict(t=t_light + 2.4, v=.3 * v, thr=.8)]
    else:
        keys = [dict(t=t_light - .05, v=0.0, thr=0.0), dict(t=t_light + .6, v=0.0, thr=.25),
                dict(t=t_light + 2.4, v=18.0, thr=.6)]
    keys += [dict(t=t_light + 4.6, v=(.4 if early else .3) * v, thr=1.0),     # still on her nose, clear
            dict(t=t_light + 7.0, hd=(hd + chd) / 2, pt=(pt + cpt) / 2, rl=(rl + crl) / 2, v=.5 * v),
            dict(t=t_light + 9.5, hd=chd, pt=cpt, rl=crl, v=.8 * v),
            dict(t=t_light + 13.0, hd=chd + 3, pt=cpt + 2, rl=crl, v=v)]
    if t_light < K1B_T:          # her drives are already lighting at the still
        keys = [k for k in keys if abs(k['t'] - K1B_T) > .05]
    return keyed(key, (x, y, z, hd, pt, rl, 0.0 if t_light >= K1B_T else .08), K1B_T, keys, v=0.0,
                 wobble=.3, thrust=None, bank=False)


PIRATE_LAUNCH = {k: launch(k, PIRATES_MOORED[k], LAUNCH[k], PIRATES_RISING[k]) for k in PIRATES_MOORED}

# Each pirate rejoins the flight her next shot gives her (S5 and S6 for most): (that shot's start, the
# offset from her launch to it there). The launch is eased onto it, so a cut never jumps her. Those
# rejoining at S5 or S6 ease over from 4.6 s after their drives light, once clear of their wrecks;
# the rest, next seen from S6b on, only once S4 has cut away (44 s). The two S5 ships share theirs
# with S5 (S5_SHARE). Measured by rejoin_offsets(); --check says when they are stale.
REJOIN = {
    'manticore-p': (44.0, (-29.3, -565.0, 622.2)),
    'colossus3': (88.0, (-6730.2, -5615.4, -3326.8)),
    'falcon-p': (50.0, (-673.7, -2003.1, 930.3)),
    'buffalo2-a': (50.0, (-1075.1, -147.2, 1026.1)),
    'hound-p-c': (58.0, (-2472.7, 524.4, 100.3)),
    'wolf-p-a': (50.0, (413.2, -2007.3, 1033.5)),
    'wolf-p-b': (44.0, (-53.4, -360.9, 674.1)),
    'hound-p-a': (74.0, (-11236.5, -3039.9, -4141.1)),
    'hound-p-b': (74.0, (-10062.0, -3951.7, -3462.5)),
    'kite-p-a': (80.0, (-11775.0, -6673.0, -5968.8)),
    'kite-p-b': (80.0, (-11131.8, -5111.7, -5894.5)),
}


def rejoin_start(t_light, t_next):
    """When a pirate starts easing onto her next shot's flight."""
    return t_light + 4.6 if t_next <= 50.0 else max(t_light + 4.6, 44.0)


def rejoined(key, f, t_light):
    """A launch flight eased onto its REJOIN offset. The offset moves her; her nose follows where
    she is actually going, turned from the launch's attitude toward her track by the share of her
    speed the offset gives her (none at either end of the ease, so every cut keeps its attitude).
    It used to move positions only, and five S4 hulls crabbed sideways at 30-98 degrees off their
    noses with their drives at full (owner, 2026-10-23: "ships flying sideways")."""
    if key not in REJOIN:
        return f
    t_next, d = REJOIN[key]
    ta = rejoin_start(t_light, t_next)
    span = t_next - ta

    def pos(t):
        p = f.at(t)
        u = min(1.0, max(0.0, (t - ta) / span))
        w = u * u * (3 - 2 * u)
        return p, (p[0] + w * d[0], p[1] + w * d[1], p[2] + w * d[2])

    def want(t):
        """The nose she should have at t: the launch's, turned toward her track."""
        p, _ = pos(t)
        hd0, pt0 = math.radians(p[3]), math.radians(p[4])
        n0 = (math.cos(pt0) * math.cos(hd0), math.cos(pt0) * math.sin(hd0), math.sin(pt0))
        u = (t - ta) / span
        if not 0.0 < u < 1.0:
            return n0
        h = .05
        v = [(pos(t + h)[1][i] - pos(t - h)[1][i]) / (2 * h) for i in range(3)]
        sp = math.sqrt(sum(c * c for c in v))
        if sp < 1.0:
            return n0
        vo = 6 * u * (1 - u) / span * math.sqrt(sum(c * c for c in d))   # the offset's share of her speed
        s = min(1.0, vo / sp)
        s = s * s * (3 - 2 * s)
        return tuple(n0[i] * (1 - s) + v[i] / sp * s for i in range(3))

    def at(t):
        p, q = pos(t)
        # Her nose comes round over the last 1.5 s of where she should point, not at once: a hull
        # turns at a hull's rate (a destroyer swung 115 degrees in a second without it).
        n = [0.0, 0.0, 0.0]
        for k in range(11):
            w = 11 - k
            for i, c in enumerate(want(t - k * .15)):
                n[i] += c * w
        ln = math.sqrt(sum(c * c for c in n)) or 1.0
        n = [c / ln for c in n]
        if abs(n[2]) > .9999 or max(abs(c) for c in n) < 1e-9:
            return (*q, *p[3:])
        hd = _near(p[3], math.degrees(math.atan2(n[1], n[0])))
        pt = math.degrees(math.asin(max(-1.0, min(1.0, n[2]))))
        return (*q, hd, pt, *p[5:])
    return Pose(at(f.t), f.t, at)


# What the shots fly. The debris field was laid out against PIRATE_LAUNCH (its keep-outs), and stays
# so; hide_lines checks the clearance of these.
PIRATE_FLIGHT = {k: rejoined(k, f, LAUNCH[k]) for k, f in PIRATE_LAUNCH.items()}
K1B_SHIPS = PIRATE_FLIGHT
K2_CAM = (PLATFORM[0] + 600, PLATFORM[1] - 1500, PLATFORM[2] + 350)     # off the south flank, a little above
K2_AIM = (PLATFORM[0] + 100, PLATFORM[1] - 60, PLATFORM[2] + 40)
K2_PIRATES = {k: Pose(f.at(K2_T), K2_T, f.at) for k, f in PIRATE_FLIGHT.items()}


def _field():
    """The debris field: a cover wreck per hidden ship, then seeded wreckage across the field kept
    out of every launch corridor, the station's body and S4's camera."""
    def covers(laid):
        out = []
        for n, (k, h) in enumerate(HIDES.items()):
            if h.get('cover'):
                peek = FIELD_LAID_AS.get(k, {}).get('peek', h.get('peek', 0.0)) if laid else h.get('peek', 0.0)
                out.append(debris_field.cover_piece('cover %s' % k, (*h['at'], h['hd'], h['pt'], h['rl']),
                                                    SHIP_LENGTH[k], TOWARD_CONVOY, h['cover'], peek,
                                                    seed=n, t0=K1B_T, size=h.get('cover_size')))
                out[-1].cover_for = k
        return out

    def corridors(laid):
        out = []
        for k, f in PIRATE_LAUNCH.items():
            if laid and k in FIELD_LAID_AS:
                f = launch(k, PIRATES_MOORED[k], LAUNCH[k], PIRATES_RISING[k], early=False)
            for i in range(0, 25):
                t = LAUNCH[k] + i * .5
                out.append((to_platform(f.at(t)[:3]), SHIP_LENGTH[k] * .6 + 45.0))
        return out
    cover = covers(False)
    keep = [(to_platform(K2_CAM), 450.0)]
    keep += [((x, 0.0, 0.0), 160.0) for x in range(-450, 451, 150)]
    # The seeded field is laid against the keep-outs it was first laid against (FIELD_LAID_AS): a
    # rejection sampler reshuffles every later piece when one keep-out moves (66 of 80 pieces, for the
    # Falcon (P)'s S3 rework). Then any piece in a moved ship's new cover or corridor is dropped.
    laid_keep = keep + corridors(True) + [(c.centre, c.radius() + 20.0) for c in covers(True)]
    keep = keep + corridors(False) + [(c.centre, c.radius() + 20.0) for c in cover]
    wrecks = debris_field.field_pieces(11, 70, (1650.0, 1050.0, 560.0), laid_keep, t0=K1B_T)
    moved = [(p, r) for p, r in keep if (p, r) not in laid_keep]
    wrecks = [w for w in wrecks if not any(sum((a - b) ** 2 for a, b in zip(w.centre, p)) ** .5 < r + w.radius()
                                           for p, r in moved)]
    # Sized to the wreck kit's hull-derived sections (wreck_kit.hulks), so a real hulk drops in. The
    # ids stay put: a resize keeps its piece, and the big hulks are appended after the seeded field.
    for w in wrecks:
        if w.name == 'debris section 10':                 # a stripped Colossus
            w.boxes[0] = ((0, 0, 0), (56.0, 44.5, 21.5), w.boxes[0][2])
    for name, size, seed in (('debris hulk onslaught-stern', (293, 230, 112), 1),):
        big = debris_field.free_hulk(name, size, BIG_HULK_SITES, keep, cover + wrecks, seed=seed, t0=K1B_T)
        if big:
            wrecks.append(big)
    return cover, wrecks


# where a free-standing big hulk may lie, in platform metres, tried in order: the field's far
# corners, off the launch corridors and S4's camera
BIG_HULK_SITES = [(-1350, 650, -300), (1350, -700, -280), (-1300, -750, 250), (1300, 750, -320),
                  (-1450, 100, -380), (1450, 100, 330), (0, -950, -420), (-700, 950, 350)]


FIELD_COVER, FIELD_WRECKS = _field()
FIELD = FIELD_COVER + FIELD_WRECKS

# S6: the lead Lasher turns back into the fire and is hit; Lasher A burns and falls back; the
# Phaeton is struck and drifts aft cooking off; the raiders close at speed, guns kicking
K3_T = 56
K3_SHIPS = {
    'lasher-lead': fly('lasher-lead', (330, 430, 70, 165, 4, -15, 1), K3_T, [
        dict(t=50, hd=0, pt=0, rl=-5, v=190), dict(t=52.5, hd=70, pt=2, rl=-70), dict(t=54.8, hd=145, pt=4, rl=-45),
        dict(t=58, hd=172, pt=6, rl=-20, v=170)],
        hits=recoil([55.0], every=.25, until=57.5) + [dict(t=56.0, push=(0, -6, 4), spin=(35, 8, -12), decay=1.2, thr=.55)]),
    'lasher-a': fly('lasher-a', (-150, 560, 160, 10, 6, 40, .2), K3_T, [
        dict(t=50, hd=2, pt=1, rl=3, v=0, thr=.6), dict(t=52.5, hd=4, pt=2, rl=8, thr=.6),
        dict(t=58, hd=13, pt=8, rl=55, v=-40)], v=-25, thrust=None,
        hits=[dict(t=52.6, push=(0, 6, 4), spin=(12, 2, 3), decay=2.0, thr=.2)]),
    'phaeton': fly('phaeton', (-350, 300, 20, 8, -4, 30, 0), K3_T, [
        dict(t=50, hd=0, pt=0, rl=2, v=0, thr=.5), dict(t=53.9, hd=.5, pt=0, rl=3, thr=.5),
        dict(t=58, hd=11, pt=-6, rl=38, v=-22)], v=-15, thrust=None,
        hits=[dict(t=54.0, push=(-4, 3, 2), spin=(3, 0, 1), decay=3.0, thr=0.0)]),
    # steadied on her gun run at the Wolf (P), then starting the turn back that S6b finishes
    'lasher-b': fly('lasher-b', CONVOY['lasher-b'], K3_T, [dict(t=50, v=0), dict(t=58, hd=-20, rl=40, v=90)], v=0,
                    hits=recoil([54.5], kick=.3, every=.4, until=57.5)),
    'falcon-p': jinking('falcon-p', (-3100, -700, -650, 6, 4, -3, 1), K3_T, 50, 58,
                        hits=recoil([51.0], kick=.12, shove=.2, every=.8, until=58)),
    'manticore-p': jinking('manticore-p', (-2500, -1000, -480, 8, 3, 4, 1), K3_T, 50, 58,
                           hits=recoil([50.5], kick=.2, shove=.3, every=.6, until=58)),
    'wolf-p-a': jinking('wolf-p-a', (-1250, 200, -120, 2, 6, 25, 1), K3_T, 50, 58),
    'wolf-p-b': jinking('wolf-p-b', (-1700, -650, -300, 3, 5, -20, 1), K3_T, 50, 58),
    'buffalo2-a': jinking('buffalo2-a', (-3600, 150, -760, 0, 5, 3, 1), K3_T, 50, 58),
}

# S6b: Lasher B rolls over to starboard (150) and the merchant Wolf to port (-140), both onto the
# climbing raiders, guns down; the Hound (P) soaks the burst, then breaks up as Lasher B's lands (62.83 s)
K3C_T = 62
K3C_SHIPS = {
    'lasher-b': fly('lasher-b', (-1300, 450, -150, -175, -15, 150, 1), K3C_T, [
        dict(t=58, hd=-20, pt=0, rl=40, v=170), dict(t=60, hd=-100, pt=-4, rl=85), dict(t=61.2, hd=-150, pt=-10, rl=125),
        dict(t=64, hd=-183, pt=-24, rl=160), dict(t=66, hd=-195, pt=-8, rl=70, v=200)],
        hits=recoil([61.5], every=.2, until=64.5)),
    'wolf-m': fly('wolf-m', (-1420, 560, -120, 175, -18, -140, 1), K3C_T, [
        dict(t=58, hd=5, pt=0, rl=-10, v=180), dict(t=60, hd=90, pt=-4, rl=-80), dict(t=61.2, hd=150, pt=-12, rl=-115),
        dict(t=64, hd=182, pt=-24, rl=-155), dict(t=66, hd=195, pt=-6, rl=-60, v=210)],
        hits=recoil([61.4], every=.25, until=64.5)),
    'hound-p-c': fly('hound-p-c', (-1800, 300, -500, 5, 12, -4, .4), K3C_T, [
        dict(t=58, hd=3, pt=14, rl=4, thr=1.0), dict(t=60.8, hd=4, pt=13, rl=0, thr=1.0),
        dict(t=63, v=120), dict(t=66, hd=15, pt=0, rl=-60, v=50)], v=200,     # the wreck slows, tumbling
        hits=[dict(t=61.0, spin=(25, 6, 10), push=(2, 0, -3), decay=1.0, thr=.4),
              dict(t=62.9, spin=(90, 30, 50), push=(-10, 8, 6), decay=2.0, thr=0.0)]),     # 2 frames after the cook-off
    'buffalo2-a': jinking('buffalo2-a', (-4200, -1200, -1500, 3, 10, 3, 1), K3C_T, 58, 66,
                          hits=recoil([59.0], kick=.2, shove=.3, every=.5, until=66)),
    'manticore-p': jinking('manticore-p', (-3000, -800, -900, 8, 6, 4, 1), K3C_T, 58, 66),
}

# S6c: the Wolves (P) blink through the gap and rake the Tarsus, whose drives go; the merchant Wolf chases
K3D_T = 72
K3D_FLEET = fleet_at({k: CONVOY[k] for k in ('colossus', 'morrow', 'tarsus', 'buffalo-b', 'mule')}, K3D_T)
K3D_SHIPS = {
    **K3D_FLEET,
    'tarsus': struck(K3D_FLEET['tarsus'], [dict(t=72.3, push=(-8, 1, -1), spin=(4, 0, -2), decay=3.0, thr=0.0)], K3D_T),
    'wolf-p-a': fly('wolf-p-a', (-380, -60, -30, 75, 6, -60, 1), K3D_T, [
        dict(t=66, hd=50, pt=4, rl=-30, v=220), dict(t=69.6, hd=65, rl=-45), dict(t=74, hd=105, pt=2, rl=-70)],
        jumps=[(69.0, (90.0, 156.0, 0.0))], hits=recoil([70.0], every=.25, until=73.5)),
    'wolf-p-b': fly('wolf-p-b', (-180, 80, 10, 95, 4, -70, 1), K3D_T, [
        dict(t=66, hd=60, rl=-30, v=220), dict(t=69.8, hd=80, rl=-50), dict(t=74, hd=125, rl=-80)],
        jumps=[(69.4, (40.0, 175.0, 0.0))], hits=recoil([70.3], every=.25, until=73.5)),
    'wolf-m': fly('wolf-m', (-560, -300, -60, 70, 4, -20, 1), K3D_T, [
        dict(t=66, hd=40, rl=10, v=230), dict(t=70, hd=60, rl=-15), dict(t=74, hd=85, rl=-35)],
        hits=recoil([69.5], every=.3, until=74)),
}

# S6d: the convoy's Hound rolls over (110) to face the two Hounds (P), kills the first, and the
# second's fire takes her drives: she tumbles back toward the column
K3E_T = 77
K3E_SHIPS = {
    'hound-m': fly('hound-m', (-1500, -150, 110, -160, -25, 110, 1), K3E_T, [
        # She has rolled over onto them before the shot opens (72-74 s), so through S6d she is steady enough
        # for her turret to stay on the first Hound (P) and fire down onto her: rolling over inside the shot
        # swung the target 40-93 deg/s across her turret and the 5-degree gate held all but 5 late rounds
        # (owner, 2026-10-23: "I don't see any shots coming out of our hero Hound")
        dict(t=72, hd=0, pt=2, rl=5, v=160), dict(t=74, hd=-150, pt=-18, rl=95),
        dict(t=78.5, hd=-170, pt=-18, rl=125, v=120), dict(t=80, hd=-176, pt=-8, rl=145, v=60)],
        # her gun's kick while her burst runs (74.0-76.2 s), then the drive hit at 77.25 s
        hits=recoil([74.0], every=.2, until=76.2) + [dict(t=77.25, push=(6, -4, 3), spin=(25, 10, -15), decay=1.5, thr=.25)]),
    'hound-p-a': fly('hound-p-a', (-1700, -50, -20, 10, 20, 0, .4), K3E_T, [
        dict(t=74, hd=8, pt=21, rl=-10, thr=1.0), dict(t=76, hd=9, pt=20, rl=0, thr=1.0), dict(t=79, v=50)], v=200,
        hits=[dict(t=76.3, spin=(15, 4, 6), decay=.8, thr=.4),
              dict(t=76.88, spin=(110, 40, 70), push=(-15, 8, 10), decay=2.0, thr=0.0)]),     # from the cook-off
    'hound-p-b': fly('hound-p-b', (-1680, -380, 20, 30, 16, 0, 1), K3E_T, [
        dict(t=74, hd=22, pt=18, rl=10), dict(t=78.5, hd=45, pt=14, rl=-30), dict(t=80, hd=70, pt=8, rl=-60)], v=200,
        hits=recoil([74.6], every=.2, until=76.7) + recoil([79.0], every=.2, until=80.0)),     # her two bursts
}

# S7: the Tarsus adrift, rolling slowly; the Kites (P) hold alongside; the boats come over and the one
# on her deck rides with her; a Wolf (P) makes a covering pass; the convoy draws away ahead
K3B_TARSUS_FLOWN = fly('tarsus', K3B_TARSUS, K3B_T, [
    dict(t=80, hd=4.5, pt=-1.5, rl=6), dict(t=88, hd=6.8, pt=-2.3, rl=11)], v=0.0, wobble=1.0, thrust=None)
K3B_SHIPS = {
    'tarsus': K3B_TARSUS_FLOWN,
    'kite-p-a': held('kite-p-a', K3B_KITE_A, K3B_T, wobble=1.5),
    'kite-p-b': held('kite-p-b', K3B_KITE_B, K3B_T, wobble=1.5),
    # the boats: one down on her deck, one braking in over her stern, one just out from under its Kite
    # S7 (80-88 s) sees all three settle: the first holds over her seat and sets down by the key time, the
    # second brakes in and keeps station through the shot, the third is still on her way in
    'hermes-p-a': boat(K3B_KITE_B, K3B_TARSUS, K3B_SEATS['hermes-p-a'], 64, 86, K3B_T, carrier=K3B_TARSUS_FLOWN, hold=5.0),
    'hermes-p-b': boat(K3B_KITE_A, K3B_TARSUS, K3B_SEATS['hermes-p-b'], 70, 93, K3B_T, carrier=K3B_TARSUS_FLOWN, hold=7.0),
    'hermes-p-c': boat(K3B_KITE_B, K3B_TARSUS, K3B_SEATS['hermes-p-c'], 74, 100, K3B_T, carrier=K3B_TARSUS_FLOWN, hold=6.0),
    'wolf-p-b': fly('wolf-p-b', (-420, 330, 120, -20, -32, 110, 1), K3B_T, [
        dict(t=80, hd=40, pt=-8, rl=40, v=200), dict(t=84, hd=5, pt=-25, rl=95), dict(t=88, hd=-60, pt=-20, rl=70)],
        hits=recoil([84.5], every=.25, until=87.0)),
    **{k: station(k, v, K3B_T, vel=(45.0, 0.0, 0.0)) for k, v in (
        ('colossus', (1200, -80, 40, 0, 0, 2, .9)), ('morrow', (1150, -250, 0, 0, 1, -3, .9)),
        ('mule', (1900, 260, 20, 0, 0, -2, .9)), ('buffalo-a', (1600, -350, 80, 0, 1, 4, .9)))},
}

# S8-S9: the Colossus wallows with her port pod shot out; Morrow and the Borers ride with her; the
# Mk.III settles in from above; the Kite (P) makes her run, is driven off and breaks away
K4_T = 95
K4_COLOSSUS_FLOWN = fly('colossus', K4_COLOSSUS, K4_T, [
    dict(t=88, hd=.5, pt=-1, rl=2, thr=.5), dict(t=90.2, hd=.4, pt=-1.3, rl=2.5, thr=.5),
    dict(t=102, hd=-2.5, pt=-6, rl=11)], v=4.0, wobble=.5, thrust=None,
    hits=[dict(t=90.3, push=(0, -1.5, -.5), spin=(.8, -.3, -.3), decay=3.0, thr=.2)])
K4_MORROW_FLOWN = carried(held('morrow', K4_MORROW, K4_T, wobble=.8), K4_COLOSSUS_FLOWN)
K4_SHIPS = {
    'colossus': K4_COLOSSUS_FLOWN, 'morrow': K4_MORROW_FLOWN,
    # Borers fight; they are not repair craft (owner, 2026-10-23): the two off the shot-out pod both nose
    # up at the Mk.III grappling down onto her, the bigger threat; two more out of Morrow's bay, climbing
    'borer-a': carried(held('borer-a', near(K4_COLOSSUS, (-58, 70, 2), hd=-90, pt=75, rl=6, thr=.6), K4_T, wobble=.3),
                       K4_COLOSSUS_FLOWN),
    'borer-b': carried(held('borer-b', near(K4_COLOSSUS, (-74, 38, -22), hd=66, pt=80, rl=-10, thr=.6), K4_T, wobble=.3),
                       K4_COLOSSUS_FLOWN),
    'borer-c': carried(keyed('borer-c', near(K4_MORROW, (BORER_BAY_M[1][0], BORER_BAY_M[1][1], 18), hd=10, pt=30, thr=.8),
                             K4_T, [], v=2.5, wobble=0.0), K4_MORROW_FLOWN),
    'colossus3': fly('colossus3', (-60, 70, 175, -4, -8, 64, .6), K4_T, [
        dict(t=88, hd=-2, pt=-5, rl=55, v=14), dict(t=102, hd=-6, pt=-10, rl=70, v=8)], v=12, wobble=1.0),
    'kite-p-b': fly('kite-p-b', (110, 210, 40, -60, 6, 32, 1), K4_T, [
        dict(t=88, hd=-100, pt=-2, rl=10, v=150), dict(t=93, hd=-80, pt=2, rl=20), dict(t=98, hd=-10, pt=12, rl=55, v=180),
        dict(t=102, hd=30, pt=10, rl=20, v=210)],
        hits=[dict(t=95.3, spin=(12, 3, -6), decay=.8)]),
    'wolf-p-a': fly('wolf-p-a', (260, 160, 110, -150, 2, -40, 1), K4_T, [
        dict(t=88, hd=-100, pt=0, rl=-10, v=210), dict(t=92, hd=-125, rl=-30), dict(t=98, hd=-185, pt=-4, rl=-60),
        dict(t=102, hd=-220, rl=-30)]),
}

def sortie(key, before, t0, runs, vmax=420.0, accel=200.0, t_end=110.0, dt=1 / 48):
    """A Borer's sortie: before t0 she is the pose `before` (in the bay); from t0 she flies her own
    pursuit, run after run at the attackers. Each run is (target key, target pose, side): she steers
    for a point beside the target's hull (side: +1 her port, -1 her starboard, a little above), led by
    the closing time, turning at most 150 deg/s and jinking about that, every rate eased (no angular
    velocity jumps for --check), banking with her turn and
    burning with her acceleration (motion's thrust model); a run ends when she has passed within 60 m
    and opened to 140 m again. A run with side 0 is her way home to the ship she works from, flown
    to the end. Returns (her Pose, her rakes: [(target, t_in, t_out, side)], the spans
    she is inside 110 m of a target, where her mining laser sweeps the hull)."""
    x, y, z, hd, pt, rl, _ = before.at(t0)
    v = 2.5
    a_full = A_FULL[mass_class(key)]
    wh = wp = wr = 0.0
    phase = (zlib.crc32(key.encode()) % 628) / 100.0
    rows, rakes = [], []
    run, closest, inside = 0, 1e9, None
    t = t0
    while t <= t_end + 1e-9 and run < len(runs):
        tk, tp, side = runs[run]
        q = tp.at(t)
        d = math.dist((x, y, z), q[:3])
        lead = min(1.2, d / max(v + 150.0, 1.0))
        q = tp.at(t + lead)
        m = _attitude(*q[3:6])[0]
        off = (0.0, side * 30.0, 14.0) if side else (-40.0, 60.0, -30.0)
        aim = [q[i] + sum(m[i][j] * off[j] for j in range(3)) for i in range(3)]
        dx, dy, dz = (aim[i] - (x, y, z)[i] for i in range(3))
        want_h = math.degrees(math.atan2(dy, dx))
        want_p = max(-55.0, min(55.0, math.degrees(math.atan2(dz, math.hypot(dx, dy)))))
        eh = (want_h - hd + 180.0) % 360.0 - 180.0
        ep = want_p - pt
        jink = 30.0 * math.sin(2 * math.pi * 1.1 * (t - t0) + phase) if t - t0 > .8 else 0.0
        for e, w, j, name in ((eh, wh, jink, 'h'), (ep, wp, .4 * jink, 'p')):
            target_w = max(-150.0, min(150.0, 3.0 * e)) + j
            nw = w + max(-350.0 * dt, min(350.0 * dt, target_w - w))
            if name == 'h':
                wh = nw
            else:
                wp = nw
        want_v = vmax if side else max(15.0, min(vmax, .8 * d))          # home: slowing as she arrives
        dv = max(-accel * dt, min(accel * dt, want_v - v)) if t - t0 > .3 else 4.0 * dt
        v += dv
        hd += wh * dt
        pt = max(-80.0, min(80.0, pt + wp * dt))
        omega = math.radians(math.hypot(wh, wp))
        thr = min(1.0, IDLE + (abs(dv) / dt + .5 * v * omega) / a_full)
        bank = -60.0 * math.tanh(v * math.radians(wh) / 400.0)      # rolls into the turn, smoothly
        wr += max(-600.0 * dt, min(600.0 * dt, max(-240.0, min(240.0, (bank - rl) / .25)) - wr))
        rl += wr * dt
        ch, sh, cp, sp = (math.cos(math.radians(hd)), math.sin(math.radians(hd)),
                          math.cos(math.radians(pt)), math.sin(math.radians(pt)))
        x, y, z = x + v * dt * cp * ch, y + v * dt * cp * sh, z + v * dt * sp
        rows.append((t, x, y, z, hd, pt, rl, thr))
        dn = math.dist((x, y, z), tp.at(t)[:3])
        if side == 0:                  # home: back to the ship she works from, no laser
            t += dt
            continue
        if dn < 110.0 and inside is None:
            inside = t
        if dn >= 110.0 and inside is not None:
            rakes.append((tk, inside, t, side))
            inside = None
        closest = min(closest, dn)
        if closest < 60.0 and dn > 140.0:
            run, closest = run + 1, 1e9
        t += dt
    last = rows[-1]

    def at(tt):
        if tt < t0:
            return before.at(tt)
        if tt >= last[0]:
            c = math.cos(math.radians(last[5]))
            g = tt - last[0]
            return (last[1] + v * g * c * math.cos(math.radians(last[4])),
                    last[2] + v * g * c * math.sin(math.radians(last[4])),
                    last[3] + v * g * math.sin(math.radians(last[5])), last[4], last[5], last[6], last[7])
        i = min(len(rows) - 2, max(0, int((tt - t0) / dt)))
        a, b = rows[i], rows[i + 1]
        u = max(0.0, min(1.0, (tt - a[0]) / (b[0] - a[0])))
        return tuple(a[k] + (b[k] - a[k]) * u for k in range(1, 8))
    return Pose(before, before.t, at), rakes


K4_BORER_C, K4_RAKES_C = sortie('borer-c', K4_SHIPS['borer-c'], 95.4,
                                [('wolf-p-a', K4_SHIPS['wolf-p-a'], 1), ('wolf-p-a', K4_SHIPS['wolf-p-a'], -1),
                                 ('colossus', K4_SHIPS['colossus'], 0)])
K4_BORER_D, K4_RAKES_D = sortie('borer-d', carried(held('borer-d', near(K4_MORROW, (BORER_BAY_M[2][0], BORER_BAY_M[2][1], 7),
                                                                       hd=10, pt=20, rl=-4, thr=.1), K4_T, wobble=0.0),
                                                   K4_MORROW_FLOWN), 96.2,
                                [('kite-p-b', K4_SHIPS['kite-p-b'], -1), ('wolf-p-a', K4_SHIPS['wolf-p-a'], 1),
                                 ('colossus', K4_SHIPS['colossus'], 0)])
K4_SHIPS.update({'borer-c': K4_BORER_C, 'borer-d': K4_BORER_D})
K4_RAKES = [('borer-c',) + r for r in K4_RAKES_C] + [('borer-d',) + r for r in K4_RAKES_D]


# S9b: the lead Lasher rolls onto her back over the Falcon (P), guns walking down her flank; the
# Falcon is hit and her broadside answers, and the Lasher tumbles away: a held roll-dominant tumble of
# about 20 deg/s, what a heavy round off her centre could plausibly give a 90 m frigate in a film (owner,
# 2026-10-23: the 150 deg/s pinwheel was overdone; physically it would be under 1 deg/s)
K4B_T = 105
K4B_LEAD = fly('lasher-lead', (-560, 380, 30, -160, -20, 160, 1), K4B_T, [
    dict(t=102, hd=-150, pt=-4, rl=25, v=170), dict(t=103.8, hd=-157, pt=-16, rl=120),
    dict(t=106.6, hd=-163, pt=-25, rl=172), dict(t=110, hd=-170, pt=-30, rl=190, v=130)], v=165,
    hits=recoil([103.5], every=.15, until=106.8)
    + [dict(t=107.6, spin=(20, 3, 5), push=(0, 20, 8), decay=2.5, thr=.1, held=True)])     # the cook-off's own impulse
K4B_SHIPS = {
    'falcon-p': fly('falcon-p', (-700, 200, -100, 10, 6, -4, .8), K4B_T, [
        dict(t=102, hd=8, pt=5, rl=-2), dict(t=110, hd=16, pt=3, rl=-12, v=30)], v=45,
        hits=recoil([106.0], kick=.12, shove=.2, every=.3, until=108.5)
        + [dict(t=104.79, push=(0, -4, -2), spin=(-5, 1, 3), decay=2.0, thr=.5)]),
    'lasher-lead': K4B_LEAD,
}

# S10: the Mk.III turning away with her prize and the docked Kite; the Falcon (P) smoking; far below,
# the Colossus and Morrow sliding down and away, nose-down
K5_T = 114
K5_C3 = fly('colossus3', C3_PRIZE, K5_T, [
    dict(t=110, hd=25, pt=-3, rl=-4, v=18), dict(t=118, hd=36, pt=-5, rl=-8, v=20)], v=18, wobble=.8)
K5_SHIPS = {
    'falcon-p': fly('falcon-p', (-560, 120, 120, 25, -6, 8, .8), K5_T, [
        dict(t=110, hd=22, rl=5), dict(t=118, hd=30, rl=12)], v=60, thrust=None),
    'colossus3': K5_C3, 'tarsus': riding(K5_C3, CLAMP_TARSUS_M, thr=0), 'kite-p-a': riding(K5_C3, (10, 66, -40), thr=0),
    'colossus': fly('colossus', (480, -30, -780, 5, -26, 14, .15), K5_T, [
        dict(t=110, hd=4, pt=-24, rl=11), dict(t=118, hd=6, pt=-28, rl=17)], v=45, wobble=1.0, thrust=None),
    'morrow': fly('morrow', (500, -175, -830, 0, -22, 8, 1), K5_T, [
        dict(t=110, hd=-1, pt=-21, rl=6), dict(t=118, hd=1, pt=-23, rl=10)], v=45, wobble=1.0, thrust=None),
}


def _far(look, d=300e3):
    """A camera target d metres along a look direction: the trails are 150-350 km off."""
    n = math.sqrt(sum(c * c for c in look))
    return tuple(c / n * d for c in look)

KEYFRAMES = {
    'k0': dict(
        shot='S1a', title='The view the player knows',
        caption='Straight down on the convoy, prograde up the frame: the ships as the player has always seen them, '
                'small against the desert far below. From here the camera tips down into the 3D fight.',
        # the player's view holds, then tips down and pulls in, landing on S1's framing at its first frame
        # a long-range optic straight down, prograde up, as the player knows the battle: it never turns.
        # ZOOM OUT to the convoy small on the desert, then ZOOM IN on it, the focus hunting as the lens
        # racks in and snapping sharp; then the cut to S1's action camera
        # (sliding sideways onto the convoy's core as it racks in: the same look, so still no turn)
        camera=camera('k0', [dict(t=t, loc=(80 + dx, 120 + dy, 11000), target=(190 + dx, 120 + dy, 0), lens=lens,
                                  focus=focus, fstop=1.0)
                             for t, lens, focus, dx, dy in ((10, 100, 11000, 0, 0), (11.6, 35, 11000, 0, 0),
                                                            (12.4, 60, 11000, -60, -60), (13.4, 200, 600, -150, -150),
                                                            (14.2, 330, 220, -170, -180), (14.8, 310, 500, -170, -180),
                                                            (15.3, 300, 11000, -170, -180), (16, 300, 11000, -170, -180))],
                      10),
        ships=convoy_at(10),
        labels=['colossus', 'morrow', 'tarsus', 'mule', 'phaeton', 'buffalo-a', 'lasher-a']),
    'k1': dict(
        shot='S1', title='Low orbit over Asharu',
        caption='A working convoy in loose column over the day side, slowing for its descent window: the Colossus '
                'with Morrow alongside, a Tarsus, a Mule, a Phaeton tanker, Buffalos and a Dram, escorted by '
                'three Lashers, a Wolf and a Hound.',
        # DOLLY + ZOOM OUT: the camera drops back along the column as it overtakes the lens, then widens
        # to put the convoy against the limb
        camera=camera('k1', [dict(t=16, loc=(-1900, 2700, 1500), target=(300, -80, -150), lens=35),
                             dict(t=20, loc=(-2300, 2600, 1450), target=(380, -70, -145), lens=30),
                             dict(t=24, loc=(-2800, 2450, 1400), target=(450, -60, -140), lens=22)], 16),
        ships=convoy_at(16),
        labels=['colossus', 'morrow', 'tarsus', 'mule', 'phaeton', 'buffalo-a', 'buffalo-b', 'dram',
                'lasher-lead', 'lasher-a', 'lasher-b', 'wolf-m', 'hound-m']),
    'k1b': dict(
        shot='S3', title='Running dark',
        caption='Long lens from the convoy\'s tail, back down at the derelict platform it has just passed: dark, '
                'stripped, hulks in its bays. Then one of the hulks shows a light: the Falcon (P)\'s drives coming '
                'up. Eleven more lie cold beside her.',
        # ZOOM, then CRASH ZOOM: finding the platform, holding until it reads as junk, then snapping in
        # on the Falcon (P) as her drives light; handheld at the long end. The lenses were refitted when
        # the platform came in to 4.5 km (90, 135, 165, 300 mm at 10 km): the same framings, closer.
        camera=camera('k1b', [dict(t=30, loc=(-1560, -150, 85), target=PLATFORM, lens=32),
                              dict(t=34, loc=(-1600, -120, 90), target=PLATFORM, lens=48),
                              dict(t=36.4, loc=(-1610, -110, 92), target=track(K1B_SHIPS['falcon-p'], PIRATES_MOORED['falcon-p'][:3]),
                                   lens=68),
                              dict(t=38, loc=(-1630, -95, 96), target=track(K1B_SHIPS['falcon-p'], PIRATES_MOORED['falcon-p'][:3]),
                                   lens=122)], K1B_T, shake=.05),
        ships=K1B_SHIPS,
        platform=PLATFORM, platform_t=K1B_T,
        # her drives light the hulk and bay behind her orange as they come up (a light, no bloom disc)
        knobs={'falcon-p': {'thr_glow': 5.5}},
        lamp_gain={'falcon-p': 4.0},     # the crash zoom's subject: her lamps read at 300 mm before her drives
        labels=['platform', 'falcon-p', 'colossus3', 'manticore-p']),
    'k2': dict(
        shot='S4', title='First contact',
        caption='Behind and below: drives light up in the derelict Asharu platform the convoy has just passed. '
                'Twelve pirate hulls climb out of it toward the column.',
        # off the pack's flank as it climbs out of the platform, wide while the spread opens up, then a
        # CRASH ZOOM onto the Falcon (P) and panning with her
        camera=camera('k2', [dict(t=t, loc=track(K2_PIRATES['falcon-p'], K2_CAM, .3),
                                  target=track(K2_PIRATES['falcon-p'], aim, share), lens=lens)
                             for t, lens, aim, share in ((38, 24, K2_AIM, .5), (40, 28, K2_AIM, .5), (41.4, 32, K2_AIM, .6),
                                                         (42.4, 95, K2_PIRATES['falcon-p'][:3], 1.0),
                                                         (44, 110, K2_PIRATES['falcon-p'][:3], 1.0))], K2_T, shake=.1),
        ships={**fleet_at({k: CONVOY[k] for k in ('hound-m', 'tarsus')}, K2_T), **K2_PIRATES},
        platform=PLATFORM,
        labels=['falcon-p', 'colossus3', 'manticore-p', 'wolf-p-a', 'kite-p-a', 'hound-p-b', 'platform']),
    'k3': dict(
        shot='S6', title='The screen breaks',
        caption='Looking aft along the convoy. Manticore (P) and Falcon (P) open from 3-4 km astern. The Phaeton '
                'tanker goes up, Lasher A is burning, and the lead Lasher turns back into the fire and is hit. '
                'The freighters keep running.',
        # ZOOM OUT along the salvo: long on the Manticore (P) as she fires, widening as her missiles come in
        # on the Phaeton, then the key framing and a handheld push, jolted by the hits
        camera=camera('k3', [dict(t=50, loc=(960, 700, 230), target=track(K3_SHIPS['manticore-p'], (-2500, -1000, -480)), lens=85),
                             dict(t=52.4, loc=(930, 690, 225), target=track(K3_SHIPS['manticore-p'], (-2500, -1000, -480)), lens=85),
                             dict(t=54.2, loc=(900, 670, 215), target=(-450, 200, 0), lens=50),
                             dict(t=56, loc=(860, 640, 200), target=(-1300, -420, -280), lens=35),
                             dict(t=58, loc=(790, 600, 180), target=(-1330, -400, -270), lens=32)], K3_T, shake=.15,
                     kicks=[(54.0, .5), (56.0, .7)]),
        states={'phaeton': 'phaeton-wreck'},
        knobs={'phaeton': {'break_frame': {'ago_s': 2.0}}},     # 2 s into the cook-off: tank 1 has just gone
        ships={**fleet_at({k: v for k, v in CONVOY.items() if k not in ('lasher-lead', 'lasher-a', 'phaeton', 'wolf-m',
                                                                            'lasher-b')}, K3_T),
               **K3_SHIPS},
        fx=[('tracer', 'manticore-p', 'lasher-lead'), ('tracer', 'falcon-p', 'lasher-lead'),
            ('tracer', 'falcon-p', 'lasher-b'), ('tracer', 'lasher-lead', 'manticore-p'),
            ('tracer', 'lasher-b', 'wolf-p-a'), ('missiles', 'manticore-p', 'phaeton'),
            # the second salvo: the lead Lasher's 55.9 s breach is a Harpoon's (fire-control plan)
            ('missiles', 'manticore-p', 'lasher-lead', 55.9),
            # a Lasher burns (S6's beat): a salvo onto Lasher A first, her fire lit where it meets her
            ('missiles', 'manticore-p', 'lasher-a', 53.2), ('fire_on', 'lasher-a', (-4, 12, -5), (-.2, .5, 1), 56 - 53.2)],
        cues={'lasher-lead': {'fire': 'all'}, 'lasher-b': {'fire': ['t3_1']},
              'manticore-p': {'fire': ['t1_1']}, 'phaeton': {'impacts': 'cookoff'}},
        labels=['lasher-lead', 'lasher-a', 'phaeton', 'colossus', 'morrow', 'falcon-p', 'manticore-p', 'wolf-p-a']),
    'k3c': dict(
        shot='S6b', title='The escorts answer',
        caption='Lasher B and the merchant Wolf turn back and roll over onto the climbing raiders, firing down. '
                'A Hound (P) takes the full Ammo Feeder burst on her bare armour and breaks up: the convoy draws blood.',
        # CHASE: behind Lasher B over her roll, looking down past her; jolted as the Hound (P) breaks up
        camera=camera('k3c', [dict(t=t, loc=track(K3C_SHIPS['lasher-b'], (-900, 700, 150), .9, .2),
                                   target=track(K3C_SHIPS['lasher-b'], (-1600, 300, -350), .8, .1), lens=lens)
                              for t, lens in ((58, 40), (62, 50), (66, 60))], K3C_T, shake=.3, kicks=[(62.83, .6)]),
        ships=K3C_SHIPS,
        # the Buffalo Mk.II no longer fires on Lasher B: a stand-in hull has no kit guns to draw it, and
        # no hit lands without a visible shooter (coordinator, 2026-10-23; kit rounds from stand-ins is open)
        # Lasher B's Ammo Feeder burst kills her: it is in flight about a second at this range, so both
        # guns cease at 62.0 s and she breaks up at 62.83 s as its last rounds land (S6b audit12: her kill
        # at 61.9 came before any of them, and the guns fired on into the wreck). walk .35: the killing
        # burst lands on her rather than walking past
        fx=[('tracer', 'lasher-b', 'hound-p-c', (0, 0, 0), dict(cease_t=62.0, walk=.35)),
            ('tracer', 'wolf-m', 'hound-p-c', (0, 0, 0), dict(cease_t=62.0)),
            ('blast_on', 'hound-p-c', 18, (0, 0, 0), 62.83), ('debris', 'hound-p-c', 50, (0, 0, 0), 62.83)],
        labels=['lasher-b', 'wolf-m', 'hound-p-c', 'buffalo2-a']),
    'k3d': dict(
        shot='S6c', title='The column is cut',
        caption='Alongside the column at 300 m. The two Wolves (P) blink through the gap between the freighters, '
                'raking the Tarsus\'s drives as they pass. The Colossus, the Mule\'s guns and the merchant Wolf chase them; '
                'shields flare on both sides.',
        # TRACKING DOLLY: dropping back along the column at 70 m/s, the Wolves (P) passing the lens
        camera=camera('k3d', [dict(t=66, loc=(120, -800, 105), target=(60, 0, 0), lens=28),
                              dict(t=72, loc=(-250, -760, 90), target=(-200, 20, -10), lens=28),
                              dict(t=74, loc=(-420, -740, 80), target=(-330, 40, -10), lens=26)], K3D_T, shake=.2,
                      kicks=[(72.3, .3)]),
        ships=K3D_SHIPS,
        # shields flare on both sides: the raiders' fronts under the Colossus's and the Wolf's fire (the
        # Mule, a stand-in with no kit guns, no longer fires: coordinator, 2026-10-23)
        shields={'wolf-p-b': dict(toward='colossus', flux=.6), 'wolf-p-a': dict(toward='wolf-m', flux=.45),
                 'wolf-m': dict(toward='wolf-p-a', flux=.2)},
        # the lead Wolf (P)'s guns on the Tarsus's drives, held tight: the round that opens them lands at
        # 70.5 s on her starboard quarter, the face she shows the Wolves (the fire between it and the
        # one S7 carries on)
        fx=[('tracer', 'wolf-p-a', 'tarsus', (-40, 0, 6), dict(walk=.2, bursts_t=[(67.4, 68.6), (69.2, 70.4)])), ('tracer', 'wolf-p-b', 'tarsus'),
            ('tracer', 'wolf-m', 'wolf-p-a'),
            # the Colossus's guns on the raiders as they close inside 1.35 km (owner, 2026-10-23: "why are none
            # of the Colossus turrets taking shots at those Wolfs"): her 360-degree mounts bear on both
            ('tracer', 'colossus', 'wolf-p-a'), ('tracer', 'colossus', 'wolf-p-b'),
            ('fire_on', 'tarsus', (-37, -2, 6), (-.2, .5, 1))],
        labels=['wolf-p-a', 'wolf-p-b', 'tarsus', 'wolf-m', 'colossus']),
    'k3e': dict(
        shot='S6d', title='The rear guard',
        caption='The convoy\'s Hound, rolled over onto two Hounds (P) climbing at her, fires down on the first and kills her at close range '
                'and takes the second\'s fire full on as she falls back on the column.',
        # HANDHELD CHASE: close behind the Hound, rolled over and firing, jolted by her kill and her hit
        camera=camera('k3e', [dict(t=t, loc=track(K3E_SHIPS['hound-m'], (-1450, 120, 160), .95, .15),
                                   target=track(K3E_SHIPS['hound-m'], (-1650, -180, 20), .8, .05), lens=lens)
                              for t, lens in ((74, 30), (77, 35), (80, 40))], K3E_T, shake=.45, kicks=[(76.83, .7), (77.25, 1.0)]),
        ships=K3E_SHIPS,
        # the hero shot (owner): her turret on the first Hound (P) through the shot, already trained on her
        # as it opens (she rolled over onto them at 72-74 s), walking bursts on until she dies at 76.83 s, on the last round that lands
        fx=[('tracer', 'hound-m', 'hound-p-a', (0, 0, 0), dict(trained=True, cease_t=77.0, walk=2.0, sure_t=76.83)), ('tracer', 'hound-p-b', 'hound-m'),
            ('blast_on', 'hound-p-a', 14, (0, 0, 0), 76.83), ('debris', 'hound-p-a', 40, (0, 0, 0), 76.83),
            # no flares: a Hound mounts no missiles, so nothing here is a seeker for them to answer (owner,
            # 2026-10-23: flares need a visible cause, an inbound seeker on screen); her 76.5 s breach
            # burns where the second Hound (P)'s rounds land (the damage ledger keys that fire)
            ],
        cues={'hound-m': {'fire': 'all'}, 'hound-p-b': {'fire': 'all'}},
        labels=['hound-m', 'hound-p-a', 'hound-p-b']),
    'k3b': dict(
        shot='S7', title='Boarding the Tarsus',
        caption='The Tarsus has fallen out of the column with her drives shot out. Kites (P) lie alongside and '
                'their boats bring the boarders over: one down on her deck, one braking in over her stern, one '
                'just out from under its Kite. A Wolf (P) covers them. The rest of the convoy runs on ahead.',
        # PUSH-IN + ZOOM onto the boats: from wide behind the Tarsus in to her deck as they set down
        camera=camera('k3b', [dict(t=80, loc=(-1500, 450, 210), target=(380, -40, -60), lens=28),
                              dict(t=86, loc=(-1250, 380, 170), target=(400, -40, -60), lens=35),
                              dict(t=88, loc=(-1150, 350, 150), target=(-480, 50, 10), lens=55)], K3B_T, shake=.08),
        states={'tarsus': 'tarsus-boarded'},
        knobs={'hermes-p-a': {'gear_up': 0.0, 'door_port': 1.0, 'door_starboard': 1.0},    # down, boarders out
               'hermes-p-b': {'gear_up': 0.5}, 'hermes-p-c': {'gear_up': 1.0}},
        seated={'hermes-p-a': 'tarsus'},
        ships=K3B_SHIPS,
        fx=[('tracer', 'wolf-p-b', 'tarsus', (30, 0, 10)), ('fire_on', 'tarsus', (-38, 6, 8), (-.2, .5, 1)),
            ('smoke', 'tarsus', (-38, 6, 8), (-.3, .4, 1))],
        labels=['tarsus', 'kite-p-a', 'kite-p-b', 'wolf-p-b', 'colossus', 'mule']),
    'k4': dict(
        shot='S8-S9', title='She will not be taken',
        caption='The Colossus Mk.III closes from above to take the Colossus too. Her port drive pod is shot out, '
                'but she fights: flares out, both turrets firing, a Kite (P) driven off. Morrow is under her keel '
                'with her drives lit, holding her up, and her Borers are already out: two turn their mining lasers '
                'up on the Mk.III, the bigger threat, while two more launch from the bay and go for the Wolf and the Kite.',
        # PULL BACK + ZOOM OUT: close on the shot-out pod, back to the wide, then wider for the Mk.III's scale
        camera=camera('k4', [dict(t=88, loc=(-190, 150, 30), target=(-62, 30, 0), lens=50),
                             dict(t=91, loc=(-260, 260, 60), target=(-50, 28, 6), lens=40),
                             dict(t=95, loc=(-420, 470, 120), target=(-40, 20, 20), lens=28),
                             dict(t=102, loc=(-480, 540, 160), target=(-30, 15, 40), lens=20)], K4_T, shake=.12,
                     kicks=[(90.3, .7), (95.5, .3)]),
        # the kit's own beam stays dark: every Borer shares one build's knobs, so raising it for two lit all
        # four (one of them a "tether" to Morrow), and it is a turret beam ending in orbs. The lasers
        # are the 'mining' effects below (swfx_mining)
        knobs={'colossus3': {'grapples': 0.5}},
        ships=K4_SHIPS,
        rakes=K4_RAKES,      # the sortie Borers' mining lasers swept along the attackers' hulls
        shields={'kite-p-b': dict(toward='colossus', flux=.85)},     # her omni shield nearly gone: she breaks off
        # the port pod shot out by the Wolf (P)'s Harpoons (95.5 s), and burning from them
        fx=[('blast_on', 'colossus', 12, (-64, 36, -7), 95.5), ('fire_on', 'colossus', (-60, 36, -2), (-1, .4, .3), 95 - 95.55),
            ('tracer', 'colossus3', 'colossus', (-60, 30, 6)), ('tracer', 'colossus', 'wolf-p-a'),
            # the Colossus's fire on the Mk.III walks wide: about a quarter to a third lands, the rest
            # flies on past her (coordinator, 2026-10-24)
            ('tracer', 'colossus', 'kite-p-b'), ('tracer', 'colossus', 'colossus3', (0, 0, 0), dict(walk=2.0)),
            ('flares', 'colossus'), ('debris', 'colossus', 45, (-64, 36, -7), 95.5),
            # what her flares answer (owner, 2026-10-23: a flare needs a visible cause): the Wolf's
            # Harpoons (the Mk.III mounts no missiles), landing after the flares go out at 93.8 s,
            # one of them pulled off onto a flare
            ('missiles', 'wolf-p-a', 'colossus', 95.6, 1, (-62, 30, 0)),     # at her port pod
            # every Borer's laser is on an attacker (owner, 2026-10-23: Borers are not repair ships): the two
            # by the pod both on the Mk.III above her, the bigger threat; two more, out of the bay, on the Wolf
            # and the Kite
            ('mining', 'borer-a', 'colossus3'), ('mining', 'borer-b', 'colossus3'),
            ('mining', 'borer-c', 'wolf-p-a'), ('mining', 'borer-d', 'kite-p-b')],
        labels=['colossus', 'morrow', 'colossus3', 'kite-p-b', 'wolf-p-a']),
    'k4b': dict(
        shot='S9b', title='The lead\'s last run',
        caption='Smoking from S6, the lead Lasher makes one more run over the Falcon (P), rolled onto her back. '
                'She presses the flagship\'s shield into overload and walks her guns down the open flank. The '
                'Falcon is hit hard, and her broadside answers.',
        # PASS-BY: the camera drifts while the Lasher dives past the lens and away, panning after her
        camera=camera('k4b', [dict(t=102, loc=(-500, 880, -40), target=track(K4B_LEAD, (-620, 280, -130), .9), lens=26),
                              dict(t=105, loc=(-560, 900, -60), target=track(K4B_LEAD, (-620, 280, -130), .9), lens=28),
                              dict(t=110, loc=(-640, 930, -90), target=track(K4B_LEAD, (-620, 280, -130), .9), lens=24)],
                      K4B_T, shake=.35, kicks=[(104.79, .5), (107.6, .9)]),
        ships=K4B_SHIPS,
        # her front shield at full flux, torn by the Lasher's guns a moment ago: the flank is open
        shields={'falcon-p': dict(toward='lasher-lead', flux=1.0, overload_s=.4, up_s=6.0)},
        fx=[('tracer', 'lasher-lead', 'falcon-p'), ('tracer', 'falcon-p', 'lasher-lead', (0, 0, 0),
                                                         # her guns bear only until 105 s (then the Lasher swings 60-87
                                                         # deg off them, past their turn rate): the bolts that land her
                                                         # 103.5 s breach and 104.6 s plating loss;
                                                         # one HE flash per spot per quarter second
                                                         dict(trained=True, bursts_t=[(102.5, 105.0)], walk=.35,
                                                              flash_gap_s=.25)),
            # the Lasher's burst that opens the Falcon's flank (104.79 s, on its round)
            ('blast_on', 'falcon-p', 18, (14, -5, -5), 104.79),
            # option (c), coordinator 2026-10-24: the Falcon's two drawn bursts (103.71 and 104.5 s) set her
            # forward deck burning and tear plating off her bow; the fire spreads aft along her deck,
            # venting, with small secondary pops, until it reaches her magazine at 107.6 s and she breaks up
            ('fire_on', 'lasher-lead', (49, -5, 5), (-.2, .5, 1), 105 - 103.71),
            ('debris', 'lasher-lead', 25, (53, -3, -4), 104.5),
            ('fire_on', 'lasher-lead', (38, -2, 6), (-.4, .4, 1), 105 - 105.5),
            ('fire_on', 'lasher-lead', (27, 2, 6), (-.4, .4, 1), 105 - 106.2),
            ('fire_on', 'lasher-lead', (16, 4, 6), (-.4, .4, 1), 105 - 106.9),
            # each pop on the face toward the camera, growing toward the break-up and never rivalling it, with
            # its own spray of plating (24 fps checks, 2026-10-24: r11 facing outshone the cook-off two to one)
            ('blast_on', 'lasher-lead', 6, (40, -3, 5), 105.9, 'facing'), ('debris', 'lasher-lead', 10, (40, -3, 5), 105.9),
            ('blast_on', 'lasher-lead', 7, (28, 1, 6), 106.6, 'facing'), ('debris', 'lasher-lead', 10, (28, 1, 6), 106.6),
            ('blast_on', 'lasher-lead', 8, (17, 3, 6), 107.2, 'facing'), ('debris', 'lasher-lead', 12, (17, 3, 6), 107.2),
            ('blast_on', 'lasher-lead', 16, (8, 4, 6), 107.6), ('debris', 'lasher-lead', 40, (8, 4, 6), 107.6)],
        labels=['falcon-p', 'lasher-lead']),
    'k5': dict(
        shot='S10', title='The prize taken',
        caption='The pirates have their prize: the captured Tarsus is clamped to the Colossus Mk.III, with a Kite (P) '
                'still docked. Far below, the Colossus has lost her orbit, nose down, with Morrow still at her side. '
                'A burning freighter heading into the atmosphere is not worth chasing.',
        # ZOOM OUT to scale: long on the clamped prize, widening and tilting down to the Colossus falling away
        camera=camera('k5', [dict(t=110, loc=(-960, 400, 460), target=(-160, 330, 40), lens=85),
                             dict(t=114, loc=(-900, 380, 420), target=(420, -60, -560), lens=35),
                             dict(t=118, loc=(-840, 360, 390), target=(440, -70, -610), lens=24)], K5_T, shake=.06),
        states={'tarsus': 'tarsus-boarded'},
        knobs={'colossus3': {'grapples': 1.0}},
        ships=K5_SHIPS,
        fx=[('fire_on', 'colossus', (-55, 36, 4)), ('smoke', 'colossus', (-60, 34, 0)),
            ('debris', 'colossus', 70, (-30, 0, 10)),
            ('fire_on', 'falcon-p', (14, -5, -5), (-.3, -.6, .7)), ('smoke', 'falcon-p', (14, -5, -5), (-.6, -.4, .6))],
        labels=['colossus', 'morrow', 'falcon-p', 'colossus3', 'tarsus']),
    'k6': dict(
        shot='S12', title='Two trails toward the northern canyons',
        caption='Long lens from the pirates\' orbit. The Colossus and Morrow burn into the atmosphere, two plasma '
                'trails heading north and down toward the canyon country where Basin will find them.',
        # A slow push-in and pan (owner, 2026-10-22: "S12's camera is still held"): from 45 mm, easing in
        # to 70 mm and tilting down after the trails as they fall toward the canyons. At the key time it
        # is the old still's framing, 50 mm along (0.9, 0.02, -0.42).
        camera=camera('k6', [dict(t=126, loc=(0, 0, 0), target=_far((0.92, -0.01, -0.36)), lens=45),
                             dict(t=130, loc=(0, 0, 0), target=_far((0.9, 0.02, -0.42)), lens=50),
                             dict(t=134, loc=(0, 0, 0), target=_far((0.86, 0.05, -0.5)), lens=70)], 130),
        ships={},
        fx=[('entry_trails',)],
        labels=['trail-colossus', 'trail-morrow'],
        clean=True),      # the review copy carries nothing over the trails: no labels, caption or shot id
}


def _nose(pose):
    """A pose's nose direction (unit vector) from its heading and pitch."""
    h, p_ = math.radians(pose[3]), math.radians(pose[4])
    return (math.cos(p_) * math.cos(h), math.cos(p_) * math.sin(h), math.sin(p_))


def _along(pose, back, up=0.0, side=0.0):
    """A point `back` metres behind a pose along her nose, raised `up` and moved `side` to her port."""
    n = _nose(pose)
    h = math.radians(pose[3])
    return (pose[0] - n[0] * back - math.sin(h) * side, pose[1] - n[1] * back + math.cos(h) * side,
            pose[2] - n[2] * back + up)


# S2: alongside the Colossus, Morrow off her starboard quarter with her drones stowed (no Borer is
# placed), Lasher A sliding through the foreground between the lens and the Colossus
M2_T = 27.0
M2_LASHER = fly('lasher-a', (-110, 185, 60, 3, 1, -28, .9), M2_T, [
    dict(t=24, hd=-3, pt=0, rl=-12), dict(t=30, hd=8, pt=2, rl=-35)], v=110)
M2_SHIPS = {**{k: v for k, v in convoy_at(M2_T).items() if k in ('colossus', 'morrow', 'buffalo-a')},
            'lasher-a': M2_LASHER}

# S5: the Manticore (P) opens at long range on the column's tail, Hellbore slugs and a Harpoon salvo
# crossing 3-4 km; her flight is S6's, so the cut into S6 keeps her where she is
M5_T = 47.0
# The effects the finished kit (scene_fx) draws; under --swfx none of them falls back to a stand-in.
KIT_FX = ('tracer', 'missiles', 'blast_on', 'debris', 'fire_on', 'smoke', 'flares')


def at_time(pose, t):
    """A flight keyed at another shot's key time, stilled at t instead: the same motion, so a camera
    tracking it and the preview's key frame both register at t."""
    return Pose(pose.at(t), t, pose.at)


# The two pirates S5 shows rejoin their S6 flights across both S4 and S5 rather than in S4's last
# seconds alone: S4 eases them S5_SHARE of the way (REJOIN), S5 the rest, by 50 s, where S6 picks up.
S5_SHARE = {'manticore-p': .5, 'wolf-p-b': .4}


def s5_eased(key, t_still):
    """Her S6 flight, still short of it at S5's start by the part of the rejoin S4 left (1 -
    S5_SHARE), eased onto it by 50 s."""
    f, raw = K3_SHIPS[key], PIRATE_LAUNCH[key]
    s6, here = f.at(44.0), raw.at(44.0)
    e = [(1 - S5_SHARE[key]) * (s6[i] - here[i]) for i in range(3)]

    def at(t):
        p = f.at(t)
        u = min(1.0, max(0.0, (t - 44.0) / 6.0))
        w = 1 - u * u * (3 - 2 * u)
        return (p[0] - w * e[0], p[1] - w * e[1], p[2] - w * e[2], *p[3:])
    return Pose(at(t_still), t_still, at)


M5_MANTICORE = s5_eased('manticore-p', 47.0)
_m5 = M5_MANTICORE.at(M5_T)
_m5_aim = convoy_at(M5_T)['phaeton']
# The Phaeton and Lasher A fly S6's flights (K3_SHIPS), so the salvos S5 shows leaving (each aimed to
# land when S6 has it land) meet the ships S6 has them meet, and the Phaeton is where S6 opens on her
M5_SHIPS = {**{k: v for k, v in convoy_at(M5_T).items() if k in ('tarsus', 'hound-m', 'buffalo-b', 'dram')},
            **{k: at_time(K3_SHIPS[k], M5_T) for k in ('phaeton', 'lasher-a')},
            **{k: s5_eased(k, M5_T) for k in ('manticore-p', 'wolf-p-b')}}
_m5_dir = [b - a for a, b in zip(_m5[:3], _m5_aim[:3])]
_m5_len = math.sqrt(sum(x * x for x in _m5_dir))
_m5_dir = [x / _m5_len for x in _m5_dir]


def _m5_point(d, up=0.0, side=0.0):
    """A point d metres along the Manticore's line of fire from her, at the key time."""
    return (_m5[0] + _m5_dir[0] * d - _m5_dir[1] * side, _m5[1] + _m5_dir[1] * d + _m5_dir[0] * side,
            _m5[2] + _m5_dir[2] * d + up)


# S11: entry, a chase from just behind the Colossus as plasma builds round both hulls and Morrow drifts
# apart from her; the last of it breaks into the recording screen's static (asharu_preview)
M11_T = 122.0
M11_COLOSSUS = at_time(K5_SHIPS['colossus'], 122.0)
M11_MORROW = fly('morrow', K5_SHIPS['morrow'].at(M11_T), M11_T, [
    dict(t=118, hd=1, pt=-23, rl=10), dict(t=126, hd=-9, pt=-27, rl=-4, v=36)], v=44, wobble=1.0, thrust=None)
_m11 = M11_COLOSSUS.at(M11_T)

# S12, the wreckfall (owner, 2026-10-22: "There's no motion or a burning ship to see", and "we could
# have a meteor shower with the other ships that were lost as well in chunks"). From the pirates'
# orbit, a long lens tracks the Colossus and Morrow down: two burning hulls inside their plasma
# sheaths, mostly whole (Basin finds them later, the hold sealed), falling away toward the canyons,
# while the ships the battle lost come down around them in pieces. At the key time the camera looks
# exactly along the planet plate's own direction at 50 mm, so the painted plate sits behind it.
K6_T = 130.0
K6_LOOK = tuple(c / math.sqrt(.9 ** 2 + .02 ** 2 + .42 ** 2) for c in (.9, .02, -.42))
K6_RANGE = 1800.0       # metres from the camera to the Colossus at the key time


def falling(key, start, t0, accel, drift=(0.0, 0.0, 0.0), aoa=20.0, yaw=0.0, roll=0.0, wobble=3.0,
            phase=0.0, yaw_rate=0.0):
    """A hull falling into the air from its pose at t0 (a flight's at(t0)): her velocity there, from
    the flight, plus a steady acceleration along accel (m/s^2, the dive) and a drift (m/s). Her nose
    points into the airflow over her (K6_WIND reversed), turned by yaw and pitched up by aoa: she
    falls belly first, tilted back as if still trying to climb (owner, 2026-10-22), with a slow roll
    and pitch wobble; yaw_rate lets her heading drift. Drives dark: thr 0."""
    p0, p1 = start.at(t0 - .5), start.at(t0)
    v0 = [2 * (p1[i] - p0[i]) + drift[i] for i in range(3)]
    into = [-c for c in K6_WIND]
    hd0 = math.degrees(math.atan2(into[1], into[0]))
    pt0 = math.degrees(math.asin(max(-1.0, min(1.0, into[2]))))

    def at(t):
        dt = t - t0
        pos = [p1[i] + v0[i] * dt + .5 * accel[i] * dt * dt for i in range(3)]
        return (*pos, hd0 + yaw + yaw_rate * dt, pt0 + aoa + .6 * wobble * math.sin(.7 * dt + phase),
                roll + wobble * math.sin(1.1 * dt + 1.3 * phase), 0.0)
    return Pose(at(K6_T), K6_T, at)


K6_DIVE = tuple(40.0 * c for c in K6_LOOK)
# The airflow over them: the way the plasma streams off each hull. Physically it runs back up the orbit,
# almost straight at a camera that rides behind them, which drew every bow shock as an orb and every
# wake as a dot (S12 lit span, 2026-10-22). The shot authors it across the frame instead, up and to the
# left on screen, so the wakes stream long behind the two as in the chosen paintover (option a).
K6_WIND = tuple(c / math.sqrt(sum(((.3 * K6_LOOK[i] + (0, .7, .6)[i]) ** 2 for i in range(3))))
                for c in (.3 * K6_LOOK[i] + (0, .7, .6)[i] for i in range(3)))
K6_SHIPS = {'colossus': falling('colossus', M11_COLOSSUS, 126.0, K6_DIVE, drift=(0.0, 40.0, 0.0),
                                 aoa=22.0, yaw=4.0, roll=5.0, wobble=3.0, phase=.4),
            'morrow': falling('morrow', M11_MORROW, 126.0, tuple(34.0 * c for c in K6_LOOK),
                              drift=(0.0, 28.0, 0.0), aoa=17.0, yaw=-8.0, roll=-7.0, wobble=4.5,
                              phase=2.1, yaw_rate=2.0)}
_k6c = K6_SHIPS['colossus'].at(K6_T)
K6_CAM = tuple(_k6c[i] - K6_RANGE * K6_LOOK[i] for i in range(3))


def _k6_cam(t):
    """The camera rides along with a little over half of the Colossus's fall and dollies in."""
    c = K6_SHIPS['colossus'].at(t)
    return tuple(K6_CAM[i] + .55 * (c[i] - _k6c[i]) + 60.0 * (t - K6_T) * K6_LOOK[i] for i in range(3))


def _k6_aim(t, toward_morrow):
    c, m = K6_SHIPS['colossus'].at(t), K6_SHIPS['morrow'].at(t)
    return tuple(c[i] + toward_morrow * (m[i] - c[i]) for i in range(3))


# The wreckfall: the ships the battle loses (the shot doc's casualty table: the Phaeton and the two
# Hounds (P) destroyed, the lead Lasher lost in S9b), coming down in pieces around the two: a few
# large chunks per hull, and many small streaks. Each falls on a heading near the Colossus's, at its
# own depth, breaking up as it enters. (name, of, size m, start at t, position at K6_T, velocity).
# Nothing here is from the Colossus: she comes down whole.
# Owner, 2026-10-22, on the paintovers: "A bit too much meteor shower comes across like doom". So a
# thin fall, behind and beside the two and dimmer than them: four burning chunks and ten faint streaks,
# low in the frame, nothing crossing the upper sky. Two ships going down with their battle's debris.
WRECKFALL_LOST = {'phaeton': 2, 'lasher-lead': 1, 'hound-p-c': 1}


def _wreckfall(seed=12, small=10):
    import random
    rnd = random.Random(seed)
    vc = [(K6_SHIPS['colossus'].at(K6_T + .5)[i] - _k6c[i]) * 2 for i in range(3)]
    out = []
    pieces = [(k, 'chunk') for k, n in WRECKFALL_LOST.items() for _ in range(n)]
    pieces += [(rnd.choice(list(WRECKFALL_LOST)), 'streak') for _ in range(small)]
    for n, (of, kind) in enumerate(pieces):
        d = rnd.uniform(4000, 20000) if kind == 'streak' else rnd.uniform(2600, 9000)    # behind the two (1800 m)
        ax, ay = rnd.uniform(-.3, .3), rnd.uniform(-.2, .03)        # beside and below them, never high
        dirn = [K6_LOOK[0] - ay * .4, K6_LOOK[1] + ax, K6_LOOK[2] + ay]
        nn = math.sqrt(sum(c * c for c in dirn))
        pos = tuple(K6_CAM[i] + dirn[i] / nn * d for i in range(3))
        turn = math.radians(rnd.uniform(-9, 9))
        spd = rnd.uniform(.8, 1.4)
        vel = (spd * (vc[0] * math.cos(turn) - vc[1] * math.sin(turn)),
               spd * (vc[0] * math.sin(turn) + vc[1] * math.cos(turn)), spd * vc[2] * rnd.uniform(.9, 1.3))
        size = rnd.uniform(14, 40) if kind == 'chunk' else rnd.uniform(1.5, 8)
        out.append(dict(name='%s %s %d' % (of, kind, n), of=of, kind=kind, size=size,
                        t_in=126.0 + rnd.uniform(-2.0, 5.5), at=pos, vel=vel, seed=n))
    # two chunks, from different hulls, break up during the shot (coordinator, 2026-10-22)
    chunks = [p for p in out if p['kind'] == 'chunk']
    chunks[0]['split'], chunks[-1]['split'] = 129.6, 130.4
    return out


WRECKFALL = _wreckfall()


def add_span(arg):
    """--span kf:t0:t1:fps (owner, 2026-10-22: the plasma judged moving, not as a still): a lit
    motion span as one generated key frame per step, each with the ships,
    the camera and the effects at its own time. Returns their ids. The env var carries it into Blender.
    The frames are named by sequence frame, '<kf>_f<round(t * fps)>', so two chunks of one shot never
    collide; t1 is exclusive (the frame at t1 is the next chunk's, or the next shot's).
    Each step also carries the key frame's own time ('key_t', which the kit's effects are timed
    against) and the shot's span ('shot_t'): the effects play through the shot (blender_main sets the
    scene frame to t; scene_fx fires the guns across the whole shot)."""
    kf, t0, t1, fps = arg.split(':')
    t0, t1, fps = float(t0), float(t1), float(fps)
    base = KEYFRAMES[kf]
    path = base['camera']['at']
    key_t = next(p.t for p in base['ships'].values() if hasattr(p, 'at'))
    ids = []
    for n in range(int(round((t1 - t0) * fps))):
        t = t0 + n / fps
        cam = dict(path(t))
        cam['at'] = path
        spec = dict(base, camera=cam, t=t, title='%s at %.2f s' % (base['title'], t), key_t=key_t, span_t=t, span_of=kf,
                    shot_t=(path.keys[0]['t'], path.keys[-1]['t']),
                    ships={k: at_time(p, t) if hasattr(p, 'at') else p for k, p in base['ships'].items()})
        key = '%s_f%05d' % (kf, int(round(t * fps)))
        KEYFRAMES[key] = SHOTS[key] = spec
        ids.append(key)
    return ids


def _k6c_cam(t):
    c = K6_SHIPS['colossus'].at(t)
    return tuple(c[i] - 560.0 * K6_LOOK[i] for i in range(3))


KEYFRAMES['k6'].update(
    title='The wreckfall',
    caption="From the pirates' orbit, a long lens"
            ' follows the Colossus and Morrow down: two burning hulls '
            'inside their plasma sheaths, falling toward the canyons, while the ships lost in the fight come '
            'down around them in pieces.',
    # TRACKING PUSH-IN: rides down with a little over half of the Colossus's fall, dollying in, from 40
    # mm to the plate's 50 mm at the key, then on to 85 mm, ending tight on the two burning hulls
    camera=camera('k6', [dict(t=126, loc=_k6_cam, target=lambda t: _k6_aim(t, .35), lens=40),
                         dict(t=130, loc=_k6_cam, target=lambda t: _k6_aim(t, 0.0), lens=50),
                         dict(t=134, loc=_k6_cam, target=lambda t: _k6_aim(t, .5), lens=85)], K6_T),
    ships=K6_SHIPS,
    fx=[('entry_sheath', 'colossus'), ('entry_sheath', 'morrow'), ('wreckfall',)],
    bloom=1.0,          # the plasma is light: it blooms in the composite (composite_layers.bloom)
    labels=['colossus', 'morrow'])
# Coordinator, 2026-10-22: "one lit still of the Colossus alone, close enough to judge the plasma
# conforming to the hull" before another span. The k6 camera's direction, 560 m off, at 50 mm.
KEYFRAMES['k6c'] = dict(KEYFRAMES['k6'], title='The Colossus burning, close',
                        camera=camera('k6c', [dict(t=129, loc=_k6c_cam, target=lambda t: K6_SHIPS['colossus'].at(t)[:3],
                                                   lens=50), dict(t=131, loc=_k6c_cam,
                                                   target=lambda t: K6_SHIPS['colossus'].at(t)[:3], lens=50)], K6_T),
                        ships={'colossus': K6_SHIPS['colossus']}, fx=[('entry_sheath', 'colossus')],
                        labels=['colossus'])

# Shots with no key frame, blocked for the motion preview so no shot plays as a slate: the camera's
# move and every ship's flight, the same spec as a key frame's. They are never lit: the lit run's
# frames are KEYFRAMES only, and SHOTS is what the preview and --check read.
BLOCKED = {
    'm2': dict(
        shot='S2', title='Alongside the Colossus',
        caption='Tracking alongside the Colossus at 300 m. Morrow rides off her starboard quarter, drones stowed; '
                'Lasher A slides through the foreground.',
        # TRACKING: alongside at 300 m off her port side, a little above, easing forward along her length
        camera=camera('m2', [dict(t=24, loc=(-260, 300, 90), target=(-60, -60, -10), lens=28),
                             dict(t=27, loc=(-170, 305, 85), target=(-20, -70, -12), lens=28),
                             dict(t=30, loc=(-80, 300, 80), target=(20, -80, -14), lens=28)], M2_T, shake=.04),
        ships=M2_SHIPS,
        labels=['colossus', 'morrow', 'lasher-a']),
    'm5': dict(
        shot='S5', title='The opening',
        caption='The Manticore (P) opens at long range: two Harpoon salvos leave her tubes and cross 3-4 km of '
                'space toward the column\'s tail.',
        # ALONG THE LINE OF FIRE, ZOOM OUT: long on the Manticore from behind as she fires (130 mm), widening
        # down the line as the salvo crosses toward the column (85 to 50 mm)
        camera=camera('m5', [dict(t=44, loc=track(M5_MANTICORE, _m5_point(-850, 110, 90)),
                                  target=track(M5_MANTICORE, _m5_point(0)), lens=130),
                             dict(t=45.6, loc=track(M5_MANTICORE, _m5_point(-880, 115, 95)),
                                  target=track(M5_MANTICORE, _m5_point(120)), lens=110),
                             dict(t=47, loc=track(M5_MANTICORE, _m5_point(-900, 120, 100)),
                                  target=track(M5_MANTICORE, _m5_point(700), .5), lens=85),
                             dict(t=50, loc=track(M5_MANTICORE, _m5_point(-940, 130, 110)),
                                  target=_m5_point(_m5_len * .7), lens=50)], M5_T, shake=.08, kicks=[(44.6, .4)]),
        ships=M5_SHIPS,
        # the two Harpoon salvos S6 lands, leaving her tubes: the Phaeton's on her cook-off (54.0 s, S6's
        # break_frame) and Lasher A's (53.2 s, her S6 breach). No Hellbore: the Phaeton is past its
        # range gate (scene_fx MAX_RANGE_M, 3.5 km), and slugs still in flight at the cut would vanish
        # in S6, where her guns are on the lead Lasher (S5 staging, 2026-10-24)
        fx=[('missiles', 'manticore-p', 'phaeton', 54.0), ('missiles', 'manticore-p', 'lasher-a', 53.2)],
        labels=['manticore-p', 'phaeton', 'tarsus', 'hound-m']),
    'm11': dict(
        shot='S11', title='Entry',
        caption='From just behind, plasma builds round both hulls; Morrow drifts apart from her. The radio breaks '
                'up, the picture breaks into the recording screen\'s static, and END OF RECORDING.',
        # CHASE: just behind and above the Colossus, closing a little as the plasma builds, shaking more
        camera=camera('m11', [dict(t=118, loc=track(M11_COLOSSUS, _along(_m11, 340, 80, 0), 1.0, .3),
                                   target=track(M11_COLOSSUS, _along(_m11, 0, 0, -75)), lens=42),
                              dict(t=122, loc=track(M11_COLOSSUS, _along(_m11, 300, 70, 0), 1.0, .3),
                                   target=track(M11_COLOSSUS, _along(_m11, 0, 0, -75)), lens=42),
                              dict(t=126, loc=track(M11_COLOSSUS, _along(_m11, 270, 65, 0), 1.0, .3),
                                   target=track(M11_COLOSSUS, _along(_m11, -40, 0, -75)), lens=38)], M11_T, shake=.3),
        ships={'colossus': M11_COLOSSUS, 'morrow': M11_MORROW},
        # entry plasma is new effects work: a proxy here (asharu_preview 'plasma'), building from 118 s to 124 s
        fx=[('plasma', 'colossus', 118.0, 124.0), ('plasma', 'morrow', 118.5, 124.5)],
        labels=['colossus', 'morrow']),
}
# S5 is lit (coordinator, 2026-10-24): it goes through the kit, the link and motion audits and the
# blob lint like every key frame. Its key time is the first of its ships' (M5_T).
KEYFRAMES['m5'] = BLOCKED.pop('m5')
SHOTS = {**KEYFRAMES, **BLOCKED}
SPAN = add_span(os.environ['ASHARU_SPAN']) if os.environ.get('ASHARU_SPAN') else []

# Drawn larger than true in the shots, never on the roster sheets (which place hulls their own way):
# coordinator on r10's k4 (2026-10-22), "the Borers can't be seen" - 3.6 m drones are 9 px at 28 mm.
READABLE = {'borer': 2.5}

ROSTER_SHEETS = ('r1', 'r2')   # top-down and broadside fleet sheets


# ======================================================================================= Blender

def surface_layers(nt, tc, base_col, mnode, sock):
    """The detail tiles over the base map colour, in the planet material (surface/README.md): each
    finished tile looked up on the tangent plane by object X, Y and mixed in by its feathered
    edge. Returns (colour, the topmost tile's weight)."""
    import bpy
    import numpy as np
    sys.path.insert(0, str(HERE / 'surface'))
    import surface_detail as sd

    def pixels(img):
        a = np.empty(img.size[0] * img.size[1] * 4, dtype=np.float32)
        img.pixels.foreach_get(a)
        return a.reshape(img.size[1], img.size[0], 4)[::-1, :, :3].astype(np.float64)

    def load(path):
        img = bpy.data.images.load(str(path))
        try:
            return pixels(img)
        finally:
            bpy.data.images.remove(img)

    base = load(roster.GAME_CORE / 'graphics' / 'planets' / 'desert01.jpg')

    def raw(name):
        p = HERE / 'surface' / 'raw' / f'{name}.png'
        return load(p) if p.exists() else None

    finished = sd.finish_all(base, raw)
    col, weight = base_col, None
    for name, tile in finished.items():
        cx, cy, side, n = sd.TILES[name]
        im = bpy.data.images.new(f'Asharu | surface {name}', n, n, alpha=False)
        rgba = np.ones((n, n, 4), dtype=np.float32)
        rgba[..., :3] = tile[::-1]
        im.pixels.foreach_set(rgba.ravel())
        im.pack()
        sep = nt.nodes.new('ShaderNodeSeparateXYZ')
        nt.links.new(tc.outputs['Object'], sep.inputs[0])
        u = mnode('ADD', mnode('DIVIDE', mnode('SUBTRACT', sep.outputs['X'], cx), side), .5)
        v = mnode('ADD', mnode('DIVIDE', mnode('SUBTRACT', sep.outputs['Y'], cy), side), .5)
        uv = nt.nodes.new('ShaderNodeCombineXYZ')
        nt.links.new(u, uv.inputs['X'])
        nt.links.new(v, uv.inputs['Y'])
        tex = nt.nodes.new('ShaderNodeTexImage')
        tex.image = im
        tex.interpolation = 'Cubic'
        tex.extension = 'EXTEND'
        nt.links.new(uv.outputs[0], tex.inputs['Vector'])
        # Feather: 1 inside, smoothstep to 0 over the outer FEATHER of the half-width.
        edge = mnode('MAXIMUM', mnode('ABSOLUTE', mnode('MULTIPLY', mnode('SUBTRACT', u, .5), 2.0)),
                     mnode('ABSOLUTE', mnode('MULTIPLY', mnode('SUBTRACT', v, .5), 2.0)))
        ramp = nt.nodes.new('ShaderNodeMapRange')
        ramp.interpolation_type = 'SMOOTHSTEP'
        ramp.inputs['From Min'].default_value = 0.0
        ramp.inputs['From Max'].default_value = sd.FEATHER
        nt.links.new(mnode('SUBTRACT', 1.0, edge), ramp.inputs['Value'])
        w = ramp.outputs['Result']
        mix = nt.nodes.new('ShaderNodeMix')
        mix.data_type = 'RGBA'
        nt.links.new(w, mix.inputs['Factor'])
        nt.links.new(col, sock(mix.inputs, 'A'))
        nt.links.new(tex.outputs['Color'], sock(mix.inputs, 'B'))
        col = sock(mix.outputs, 'Result')
        weight = w if weight is None else mnode('MAXIMUM', weight, w)
    print('ASHARU surface tiles:', ', '.join(finished) or 'none', flush=True)
    return col, weight


def blender_main(cfg):
    import bmesh
    import bpy
    import numpy as np
    from bpy_extras.object_utils import world_to_camera_view
    from mathutils import Euler, Matrix, Quaternion, Vector
    import ship_fx
    import composite_layers

    out = Path(cfg['out'])
    raw = out / 'raw'
    raw.mkdir(parents=True, exist_ok=True)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.resolution_x, scene.render.resolution_y = W, H
    scene.render.resolution_percentage = 50 if cfg.get('half') else 100     # --half: a light check
    if cfg.get('percent'):              # --percent P: a timing check at any size (never a review frame)
        scene.render.resolution_percentage = int(cfg['percent'])
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = cfg['samples']
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 4
    scene.cycles.transparent_max_bounces = 16
    if cfg.get('volume_biased'):        # --volume-biased: ray marching (step rate, max steps) for null scattering
        scene.cycles.volume_biased = True
    if cfg.get('volume_step_rate'):     # --volume-step-rate: coarser ray marching through the kit's volumes
        scene.cycles.volume_step_rate = float(cfg['volume_step_rate'])
    if cfg.get('volume_max_steps'):     # --volume-max-steps: the cap on steps per volume a ray takes
        scene.cycles.volume_max_steps = int(cfg['volume_max_steps'])
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Base Contrast'
    import time
    said = [0.0]

    def progress(stats):     # Cycles prints nothing while a frame renders: say where it is, every 30 s at most
        if time.time() - said[0] >= 30.0:
            said[0] = time.time()
            print('ASHARU progress', time.strftime('%H:%M:%S'), stats, flush=True)
    bpy.app.handlers.render_stats.append(progress)
    try:
        prefs = bpy.context.preferences.addons['cycles'].preferences
        for kind in ('OPTIX', 'CUDA', 'METAL'):
            try:
                prefs.compute_device_type = kind
                prefs.get_devices()
                if any(d.type == kind for d in prefs.devices):
                    for d in prefs.devices:
                        d.use = d.type == kind
                    scene.cycles.device = 'GPU'
                    break
            except TypeError:
                continue
    except KeyError:
        pass

    def sock(sockets, name, kind='RGBA'):
        return next(x for x in sockets if x.name == name and x.type == kind)

    def link(obj, coll=None):
        (coll or scene.collection).objects.link(obj)
        return obj

    def emission_mat(name, rgb, strength, fade=None):
        """Emission; fade='z' fades it out along the object's generated Z (plumes, trails)."""
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        nt = mat.node_tree
        nt.nodes.remove(nt.nodes['Principled BSDF'])
        em = nt.nodes.new('ShaderNodeEmission')
        em.inputs['Color'].default_value = (*rgb, 1)
        em.inputs['Strength'].default_value = strength
        outn = nt.nodes['Material Output']
        if fade is None:
            nt.links.new(em.outputs[0], outn.inputs['Surface'])
            return mat
        tc = nt.nodes.new('ShaderNodeTexCoord')
        sep = nt.nodes.new('ShaderNodeSeparateXYZ')
        nt.links.new(tc.outputs['Generated'], sep.inputs[0])
        inv = nt.nodes.new('ShaderNodeMath')
        inv.operation = 'SUBTRACT'
        inv.inputs[0].default_value = 1.0
        nt.links.new(sep.outputs['Z'], inv.inputs[1])
        pw = nt.nodes.new('ShaderNodeMath')
        pw.operation = 'POWER'
        pw.inputs[1].default_value = fade
        nt.links.new(inv.outputs[0], pw.inputs[0])
        clear = nt.nodes.new('ShaderNodeBsdfTransparent')
        mix = nt.nodes.new('ShaderNodeMixShader')
        nt.links.new(pw.outputs[0], mix.inputs['Fac'])
        nt.links.new(clear.outputs[0], mix.inputs[1])
        nt.links.new(em.outputs[0], mix.inputs[2])
        nt.links.new(mix.outputs[0], outn.inputs['Surface'])
        return mat

    def principled(name, rgb, rough=.5, metal=.3, alpha=1.0):
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        p = mat.node_tree.nodes['Principled BSDF']
        p.inputs['Base Color'].default_value = (*rgb, 1)
        p.inputs['Roughness'].default_value = rough
        p.inputs['Metallic'].default_value = metal
        p.inputs['Alpha'].default_value = alpha
        return mat

    # ---------------------------------------------------------------------------- world, sun
    world = bpy.data.worlds.new('Asharu | space')
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Color'].default_value = (.0008, .0009, .0014, 1)
    # A starfield (owner, 2026-10-21): a camera exposed for sunlit hulls would lose the stars, but space reads
    # wrong without them. Seen by the camera only (Light Path: Is Camera Ray), so they light nothing. Sharp
    # Voronoi points on the view direction, a few bright ones and many faint, with a faint warm/cool tint.
    wn, wl = world.node_tree.nodes, world.node_tree.links
    bg_out = wn['World Output']
    tc = wn.new('ShaderNodeTexCoord')
    vor = wn.new('ShaderNodeTexVoronoi')
    vor.voronoi_dimensions = '3D'
    vor.inputs['Scale'].default_value = 420.0
    wl.new(tc.outputs['Generated'], vor.inputs['Vector'])
    pts = wn.new('ShaderNodeMapRange')               # distance to the cell's point -> a tight dot
    pts.inputs['From Min'].default_value, pts.inputs['From Max'].default_value = 0.0, 0.06
    pts.inputs['To Min'].default_value, pts.inputs['To Max'].default_value = 1.0, 0.0
    wl.new(vor.outputs['Distance'], pts.inputs['Value'])
    mag = wn.new('ShaderNodeMath'); mag.operation = 'POWER'      # each cell's random colour as its magnitude
    sep = wn.new('ShaderNodeSeparateColor')
    wl.new(vor.outputs['Color'], sep.inputs['Color'])
    wl.new(sep.outputs['Red'], mag.inputs[0]); mag.inputs[1].default_value = 9.0
    lum = wn.new('ShaderNodeMath'); lum.operation = 'MULTIPLY'
    wl.new(pts.outputs['Result'], lum.inputs[0]); wl.new(mag.outputs['Value'], lum.inputs[1])
    tint = wn.new('ShaderNodeMix'); tint.data_type = 'RGBA'
    tint.inputs['A'].default_value, tint.inputs['B'].default_value = (1.0, .86, .72, 1), (.75, .85, 1.0, 1)
    wl.new(sep.outputs['Green'], tint.inputs['Factor'])
    star = wn.new('ShaderNodeBackground'); star.name = 'Stars'
    wl.new(tint.outputs['Result'], star.inputs['Color'])
    gain = wn.new('ShaderNodeMath'); gain.operation = 'MULTIPLY'; gain.name = 'Star gain'
    gain.inputs[1].default_value = 1.0
    wl.new(lum.outputs['Value'], gain.inputs[0]); wl.new(gain.outputs['Value'], star.inputs['Strength'])
    lp = wn.new('ShaderNodeLightPath')
    only_cam = wn.new('ShaderNodeMixShader')
    wl.new(lp.outputs['Is Camera Ray'], only_cam.inputs['Fac'])
    wl.new(wn['Background'].outputs['Background'], only_cam.inputs[1])
    add = wn.new('ShaderNodeAddShader')
    wl.new(wn['Background'].outputs['Background'], add.inputs[0]); wl.new(star.outputs['Background'], add.inputs[1])
    wl.new(add.outputs['Shader'], only_cam.inputs[2])
    wl.new(only_cam.outputs['Shader'], bg_out.inputs['Surface'])
    sun_dir = Vector((math.cos(math.radians(SUN_EL)) * math.cos(math.radians(SUN_AZ)),
                      math.cos(math.radians(SUN_EL)) * math.sin(math.radians(SUN_AZ)),
                      math.sin(math.radians(SUN_EL))))
    sun = link(bpy.data.objects.new('Asharu | Corvus', bpy.data.lights.new('Asharu | Corvus', 'SUN')))
    sun.data.energy = 4.6
    sun.data.color = (1.0, .96, .88)
    sun.data.angle = math.radians(.5)
    sun.rotation_euler = sun_dir.to_track_quat('Z', 'Y').to_euler()

    # ---------------------------------------------------------------------------- planet
    centre = Vector((0, 0, -(R + ALT)))

    def cap(name, radius, max_colat_deg, rings, segments):
        bm = bmesh.new()
        top = bm.verts.new((0, 0, radius))
        prev = None
        for i in range(1, rings + 1):
            th = math.radians(max_colat_deg) * (i / rings) ** 1.0
            ring = [bm.verts.new((radius * math.sin(th) * math.cos(2 * math.pi * j / segments),
                                  radius * math.sin(th) * math.sin(2 * math.pi * j / segments),
                                  radius * math.cos(th))) for j in range(segments)]
            for j in range(segments):
                k = (j + 1) % segments
                if prev is None:
                    bm.faces.new((top, ring[j], ring[k]))
                else:
                    bm.faces.new((prev[j], ring[j], ring[k], prev[k]))
            prev = ring
        me = bpy.data.meshes.new(name)
        bm.to_mesh(me)
        bm.free()
        for p in me.polygons:
            p.use_smooth = True
        obj = link(bpy.data.objects.new(name, me))
        obj.location = centre
        return obj

    ground = cap('Asharu | surface', R, 24.0, 420, 1024)
    gm = bpy.data.materials.new('Asharu | desert')
    gm.use_nodes = True
    nt = gm.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Roughness'].default_value = .95
    tc = nt.nodes.new('ShaderNodeTexCoord')
    # Map lookup by direction from the centre: the game's equirectangular desert01.jpg (scarps
    # and canyons between dune seas), turned so world north is the map's north and the battle
    # sits over latitude MAP_LAT, longitude MAP_LON.
    turn = nt.nodes.new('ShaderNodeVectorRotate')
    turn.rotation_type = 'Z_AXIS'
    turn.inputs['Angle'].default_value = math.radians(90)
    nt.links.new(tc.outputs['Object'], turn.inputs['Vector'])
    rot = nt.nodes.new('ShaderNodeVectorRotate')
    rot.rotation_type = 'EULER_XYZ'
    rot.inputs['Rotation'].default_value = (0, math.radians(90 - MAP_LAT), math.radians(MAP_LON))
    nt.links.new(turn.outputs[0], rot.inputs['Vector'])
    nrm = nt.nodes.new('ShaderNodeVectorMath')
    nrm.operation = 'NORMALIZE'
    nt.links.new(rot.outputs[0], nrm.inputs[0])
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(nrm.outputs[0], sep.inputs[0])

    def mnode(op, a, b=None, val=None):
        n = nt.nodes.new('ShaderNodeMath')
        n.operation = op
        for idx, x in enumerate((a, b)):
            if x is None:
                continue
            if isinstance(x, (int, float)):
                n.inputs[idx].default_value = x
            else:
                nt.links.new(x, n.inputs[idx])
        return n.outputs[0]

    lon = mnode('ARCTAN2', sep.outputs['Y'], sep.outputs['X'])
    lat = mnode('ARCSINE', sep.outputs['Z'])
    u = mnode('ADD', mnode('DIVIDE', lon, 2 * math.pi), .5)
    v = mnode('ADD', mnode('DIVIDE', lat, math.pi), .5)
    uv = nt.nodes.new('ShaderNodeCombineXYZ')
    nt.links.new(u, uv.inputs['X'])
    nt.links.new(v, uv.inputs['Y'])
    img = nt.nodes.new('ShaderNodeTexImage')
    img.image = bpy.data.images.load(str(roster.GAME_CORE / 'graphics' / 'planets' / 'desert01.jpg'))
    img.interpolation = 'Cubic'
    nt.links.new(uv.outputs[0], img.inputs['Vector'])
    ground_col, detail_w = img.outputs['Color'], None
    if cfg.get('surface', SURFACE_DETAIL):
        ground_col, detail_w = surface_layers(nt, tc, ground_col, mnode, sock)
    # Relief at the scales a 250 km orbit resolves: basins (60 km), ridges (8 km), dune fields.
    def noise(scale, detail=6, rough=.55):
        n = nt.nodes.new('ShaderNodeTexNoise')
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        nt.links.new(tc.outputs['Object'], n.inputs['Vector'])
        return n.outputs['Fac']
    basin = noise(1.6e-5)
    mid = noise(4.5e-5, 8, .6)
    ridge = noise(1.3e-4, 8, .62)
    fine = noise(1.2e-3, 4, .5)
    vein = mnode('POWER', mnode('SUBTRACT', 1.0, mnode('MULTIPLY', mnode('ABSOLUTE', mnode('SUBTRACT', noise(2.2e-4, 6, .55), .5)), 2.0)), 14.0)
    wave = nt.nodes.new('ShaderNodeTexWave')
    wave.inputs['Scale'].default_value = 6e-4
    wave.inputs['Distortion'].default_value = 6.0
    nt.links.new(tc.outputs['Object'], wave.inputs['Vector'])
    shade = mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', basin, .5), 1.1),
                  mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', ridge, .5), 1.6),
                        mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', fine, .5), .35),
                              mnode('MULTIPLY', mnode('SUBTRACT', wave.outputs['Fac'], .5), .12))))
    shade = mnode('ADD', shade, mnode('ADD', mnode('MULTIPLY', mnode('SUBTRACT', mid, .5), 1.2),
                                        mnode('MULTIPLY', vein, -.55)))
    if detail_w is not None:     # the tiles carry their own relief: keep half the procedural on them
        shade = mnode('MULTIPLY', shade, mnode('SUBTRACT', 1.0, mnode('MULTIPLY', detail_w, .5)))
    gain = mnode('MAXIMUM', mnode('ADD', 1.0, shade), .15)
    tint = nt.nodes.new('ShaderNodeMix')
    tint.data_type = 'RGBA'
    tint.blend_type = 'MULTIPLY'
    tint.inputs['Factor'].default_value = 1.0
    nt.links.new(ground_col, sock(tint.inputs, 'A'))
    cmb = nt.nodes.new('ShaderNodeCombineColor')
    for ch in ('Red', 'Green', 'Blue'):
        nt.links.new(gain, cmb.inputs[ch])
    nt.links.new(cmb.outputs[0], sock(tint.inputs, 'B'))
    dim = nt.nodes.new('ShaderNodeMix')
    dim.data_type = 'RGBA'
    dim.blend_type = 'MULTIPLY'
    dim.inputs['Factor'].default_value = 1.0
    nt.links.new(sock(tint.outputs, 'Result'), sock(dim.inputs, 'A'))
    sock(dim.inputs, 'B').default_value = (.78, .7, .62, 1)
    nt.links.new(sock(dim.outputs, 'Result'), bsdf.inputs['Base Color'])
    bump = nt.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = .35
    nt.links.new(mnode('ADD', ridge, mnode('MULTIPLY', fine, .5)), bump.inputs['Height'])
    nt.links.new(bump.outputs[0], bsdf.inputs['Normal'])
    ground.data.materials.append(gm)

    air = cap('Asharu | air', R + 14e3, 25.0, 300, 1024)
    am = bpy.data.materials.new('Asharu | air')
    am.use_nodes = True
    nt2 = am.node_tree
    nt2.nodes.remove(nt2.nodes['Principled BSDF'])
    # A thin, soft blue haze over the limb (coordinator, 2026-10-22: the old shell, brightest at its
    # own outer edge, drew "a hard, flat grey band"). u is how far in from the shell's edge a ray
    # passes, 1 where it grazes the ground: the haze climbs to full there and dies away over the disc.
    lw = nt2.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5

    def m2(op, a, b):
        m = nt2.nodes.new('ShaderNodeMath')
        m.operation = op
        for k, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                m.inputs[k].default_value = v
            else:
                nt2.links.new(v, m.inputs[k])
        return m.outputs[0]
    grazing = math.sqrt(2 * 14e3 / R)                  # 1 - facing where a ray just grazes the ground
    u = m2('DIVIDE', m2('SUBTRACT', 1.0, lw.outputs['Facing']), grazing)
    haze = m2('MINIMUM', m2('POWER', m2('MINIMUM', u, 1.0), 4.0),
              m2('EXPONENT', m2('MULTIPLY', m2('SUBTRACT', u, 1.0), -2.5), None))
    haze = m2('MULTIPLY', haze, .6)        # look1 (2026-10-22): at .85 and 1.1 it was a thick flat band
    em = nt2.nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (.32, .52, 1.0, 1)
    em.inputs['Strength'].default_value = .45
    clear = nt2.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt2.nodes.new('ShaderNodeMixShader')
    nt2.links.new(haze, mix.inputs['Fac'])
    nt2.links.new(clear.outputs[0], mix.inputs[1])
    nt2.links.new(em.outputs[0], mix.inputs[2])
    nt2.links.new(mix.outputs[0], nt2.nodes['Material Output'].inputs['Surface'])
    air.data.materials.append(am)
    air.visible_shadow = False
    backdrop = bpy.data.collections.new('Backdrop')     # the planet: its own view layer under --layers
    scene.collection.children.link(backdrop)
    for o in (ground, air):
        scene.collection.objects.unlink(o)
        backdrop.objects.link(o)

    # ---------------------------------------------------------------------------- hull library
    library = {}      # game/finished key -> dict(coll, mpu, engines [(pos, dir, radius, style)], length_m)

    def excluded(coll):
        bpy.context.view_layer.layer_collection.children[coll.name].exclude = True
        return coll

    def append(path, name, extra=()):
        with bpy.data.libraries.load(str(path), link=False) as (src, dst):
            dst.collections = [name, *extra]
        colls = list(dst.collections)
        for coll in colls:
            scene.collection.children.link(coll)
            excluded(coll)
        return colls[0] if not extra else colls

    def tinted_copy(coll, rgb, label):
        new = bpy.data.collections.new(coll.name + ' | ' + label)
        scene.collection.children.link(new)
        excluded(new)
        mats = {}
        remap = {}
        for obj in coll.all_objects:
            o2 = obj.copy()
            if obj.data is not None and hasattr(obj.data, 'materials'):
                o2.data = obj.data.copy()
                for i, m in enumerate(o2.data.materials):
                    if m is None:
                        continue
                    if m.name not in mats:
                        m2 = m.copy()
                        m2.name = m.name + ' | ' + label
                        if m2.use_nodes:
                            for n in m2.node_tree.nodes:
                                if n.type == 'BSDF_PRINCIPLED':
                                    base = n.inputs['Base Color']
                                    mul = m2.node_tree.nodes.new('ShaderNodeMix')
                                    mul.data_type = 'RGBA'
                                    mul.blend_type = 'MULTIPLY'
                                    mul.inputs['Factor'].default_value = .85
                                    sock(mul.inputs, 'B').default_value = (*rgb, 1)
                                    if base.is_linked:
                                        m2.node_tree.links.new(base.links[0].from_socket, sock(mul.inputs, 'A'))
                                    else:
                                        sock(mul.inputs, 'A').default_value = base.default_value
                                    m2.node_tree.links.new(sock(mul.outputs, 'Result'), base)
                        mats[m.name] = m2
                    o2.data.materials[i] = mats[m.name]
            remap[obj] = o2
            new.objects.link(o2)
        for obj, o2 in remap.items():
            if obj.parent in remap:
                o2.parent = remap[obj.parent]
        return new

    def finished(key):
        f = roster.FINISHED[key]
        m = build_manifest(f)
        fx = None
        if (m.get('effects') or {}).get('collection'):     # the build's own SWFX, copied per ship (ShipFx)
            coll, fx = append(build_file(f), m['append_collection'], (m['effects']['collection'],))
        else:
            coll = append(build_file(f), m['append_collection'])
        ctl = (m.get('control') or {}).get('object') or (m.get('break_up') or {}).get('controls')
        ctl = next((o for o in coll.all_objects if ctl and o.name.split('.')[0] == ctl), None) or (
            bpy.data.objects.get(ctl) if ctl else None)
        eng = []
        for e in m['engines']:
            role = e.get('role', 'main drive')
            if any(w in role for w in ('lift', 'jet', 'rcs', 'manoeuv', 'maneuv')):    # glows are for the drives only
                continue
            if e.get('running') is False:    # a dead drive (a damaged state) shows no glow
                continue
            eng.append((Vector(e.get('mouth') or e['location']), Vector(e.get('axis') or e['exhaust_direction']),
                        e.get('radius') or e['mouth_radius'], e.get('style', 'LOW_TECH')))
        knobs = {}
        if ctl and m.get('control'):      # knobs: a list of {name, ...} or of names, or a dict of name -> description
            ks = m['control'].get('knobs') or []
            pose = m['control'].get('saved_pose') or {}
            knobs = {n: pose.get(n, 0.0) for n in (ks if isinstance(ks, dict) else
                                                   [k if isinstance(k, str) else k['name'] for k in ks])}
        hull_ctl = ctl if ctl is not None and ctl.name in coll.all_objects else None    # gear, grapples, break-up
        if f.get('livery') == 'scrap':     # a pirate's boat: the company paint greyed and dulled, on this copy only
            for mat in {sl.material for o in coll.all_objects for sl in o.material_slots if sl.material}:
                for n in [n for n in (mat.node_tree.nodes if mat.use_nodes else ()) if n.type == 'BSDF_PRINCIPLED']:
                    base = n.inputs['Base Color']
                    hs = mat.node_tree.nodes.new('ShaderNodeHueSaturation')
                    hs.inputs['Saturation'].default_value = .15
                    hs.inputs['Value'].default_value = .7
                    if base.is_linked:
                        mat.node_tree.links.new(base.links[0].from_socket, hs.inputs['Color'])
                    else:
                        hs.inputs['Color'].default_value = base.default_value
                    mat.node_tree.links.new(hs.outputs['Color'], base)
        # the build's own lamps and volumes (shared by every ship of the build: one build, one mpu)
        ship_fx.metre_scale({d for o in coll.all_objects for d in
                             (o.data if o.type == 'LIGHT' else None, *(sl.material for sl in o.material_slots))
                             if d is not None}, m['metres_per_unit'])
        return dict(coll=coll, fx=fx, mpu=m['metres_per_unit'], engines=eng, length_m=f['length_m'], ctl=ctl,
                    knobs=knobs, manifest=m, hull_ctl=hull_ctl, swfx=bool(f.get('swfx')), own_fx=bool(f.get('own_fx')))

    def standin(game, length_m=None):
        """The sprite's silhouette as a lens-shaped slab in hull units (one sprite px per unit,
        about the .ship centre), painted with the sprite, sized by option C."""
        spec = roster.game_spec(game)
        im = bpy.data.images.load(spec['sprite'], check_existing=True)
        w, h = im.size
        px = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]   # row 0 = top
        alpha = px[:, :, 3] > .5
        area = int(alpha.sum())
        rows = alpha.any(1).nonzero()[0]
        length_px = rows.max() - rows.min() + 1
        if length_m is None:
            mpu, units3, cls = roster.option_c(spec, area)
        else:
            cls = 'capital'
            mpu = length_m / length_px
            units3 = roster.ship_scale.estimate_units3(area, cls)
        step = 2 if max(w, h) < 260 else 3
        hh, ww = h // step, w // step
        cells = alpha[:hh * step, :ww * step].reshape(hh, step, ww, step).mean(axis=(1, 3)) > .5
        # Distance from the edge, in cells (4-neighbour erosion).
        d = np.zeros(cells.shape, dtype=np.float32)
        cur = cells.copy()
        while cur.any():
            d += cur
            pad = np.pad(cur, 1)
            cur = cur & pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
        soft = max(2.0, d.max() * .55)
        prof = np.clip((d - 1) / soft, 0, 1) ** .35
        cell_area = step * step
        vol_per_h = (prof.sum() * (1 + .7)) * cell_area
        h0 = 2.0 * units3 / vol_per_h if vol_per_h > 0 else 1.0   # x2: reads as a hull beside built models
        cx, cy = spec['centre']
        bm = bmesh.new()
        uvl = bm.loops.layers.uv.new('UVMap')
        top, bot, uvs = {}, {}, {}
        for r, c in zip(*np.nonzero(cells)):
            pxx, pyy = c * step + step / 2, r * step + step / 2
            x = (h - pyy) - cy
            y = cx - pxx
            z = prof[r, c] * h0
            top[(r, c)] = bm.verts.new((x, y, z))
            bot[(r, c)] = top[(r, c)] if z == 0 else bm.verts.new((x, y, -.7 * z))
            uvs[(r, c)] = (pxx / w, 1 - pyy / h)
        for (r, c) in list(top):
            q = [(r, c), (r + 1, c), (r + 1, c + 1), (r, c + 1)]
            if not all(k in top for k in q):
                continue
            for layer, order in ((top, q), (bot, q[::-1])):
                vs = [layer[k] for k in order]
                if len(set(vs)) < 3:
                    continue
                try:
                    f = bm.faces.new(list(dict.fromkeys(vs)))
                except ValueError:
                    continue
                for loop in f.loops:
                    k = next(kk for kk in order if layer[kk] is loop.vert)
                    loop[uvl].uv = uvs[k]
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
        me = bpy.data.meshes.new('Stand-in | ' + game)
        bm.to_mesh(me)
        bm.free()
        for p in me.polygons:
            p.use_smooth = True
        mat = bpy.data.materials.new('Stand-in | ' + game)
        mat.use_nodes = True
        nt = mat.node_tree
        tex = nt.nodes.new('ShaderNodeTexImage')
        tex.image = im
        p = nt.nodes['Principled BSDF']
        nt.links.new(tex.outputs['Color'], p.inputs['Base Color'])
        p.inputs['Roughness'].default_value = .55
        p.inputs['Metallic'].default_value = .35
        me.materials.append(mat)
        coll = bpy.data.collections.new('Stand-in | ' + game)
        scene.collection.children.link(coll)
        excluded(coll)
        coll.objects.link(bpy.data.objects.new(me.name, me))
        eng = []
        for e in spec['engines']:
            a = math.radians(e['angle'])
            eng.append((Vector((e['location'][0], e['location'][1], 0)), Vector((math.cos(a), math.sin(a), 0)),
                        e['width'] * .32, e['style']))
        return dict(coll=coll, mpu=mpu, engines=eng, length_m=length_px * mpu, cls=cls)

    def hull(m):
        k = m['finished'] + ('|tint' if m.get('tint') else '') if m.get('finished') else m['game']
        if k in library:
            return library[k]
        if m.get('finished'):
            base_key = m['finished']
            if base_key not in library:
                library[base_key] = finished(base_key)
            if m.get('tint'):
                b = library[base_key]
                library[k] = {**b, 'coll': tinted_copy(b['coll'], m['tint'], 'pirate')}
        else:
            library[k] = standin(m['game'], m.get('published_m'))
        return library[k]

    members = {m['key']: m for m in roster.MERCHANT + roster.PIRATE + roster.REFERENCES + roster.DRONES}

    # ---------------------------------------------------------------------------- plumes, fx
    plume_me = bpy.data.meshes.new('Plume unit')
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=False, segments=16, radius1=1.0, radius2=.35, depth=1.0)
    bmesh.ops.translate(bm, verts=bm.verts[:], vec=(0, 0, .5))
    bm.to_mesh(plume_me)
    bm.free()
    box_me = bpy.data.meshes.new('Box unit')
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=2.0)
    bm.to_mesh(box_me)
    bm.free()
    bead_me = bpy.data.meshes.new('Bead unit')
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
    bm.to_mesh(bead_me)
    bm.free()
    STYLE_RGB = {'LOW_TECH': (1.0, .55, .25), 'MIDLINE': (1.0, .8, .45), 'HIGH_TECH': (.45, .7, 1.0)}
    plume_mats = {s: emission_mat('Plume ' + s, c, 14.0, fade=1.6) for s, c in STYLE_RGB.items()}
    bead_mats = {s: emission_mat('Drive bead ' + s, c, 40.0) for s, c in STYLE_RGB.items()}
    tracer_mat = emission_mat('Tracer', (1.0, .62, .3), 18.0)
    missile_mat = emission_mat('Missile', (1.0, .85, .6), 40.0)
    blast_mat = emission_mat('Blast', (1.0, .5, .18), 7.0)
    fire_mat = emission_mat('Fire', (1.0, .4, .12), 12.0, fade=1.2)
    flare_mat = emission_mat('Flare', (1.0, .9, .7), 60.0)
    plasma_mat = emission_mat('Entry plasma', (1.0, .62, .32), 30.0, fade=1.4)
    smoke_mat = principled('Smoke', (.16, .15, .14), rough=1, metal=0, alpha=.28)
    smoke_mat.node_tree.nodes['Principled BSDF'].inputs['Specular IOR Level'].default_value = 0.0   # matte, not glass
    debris_mat = principled('Debris', (.08, .08, .08), rough=.7, metal=.5)
    plume_me.materials.append(tracer_mat)
    bead_me.materials.append(tracer_mat)
    box_me.materials.append(tracer_mat)

    stage = bpy.data.collections.new('Stage')
    scene.collection.children.link(stage)
    stage_ships = bpy.data.collections.new('Stage | ships')       # hulls, stand-ins, the platform
    stage_fx = bpy.data.collections.new('Stage | effects')        # each ship's kit copy, the placeholder effects
    stage.children.link(stage_ships)
    stage.children.link(stage_fx)
    placed = {}
    ships_fx = {}     # (ship key, build key) -> ShipFx: that ship's own SWFX (and hull, where it has gear)

    def ship_fx_for(key, m, lib):
        """The ship's own effects copy, when it needs one: always for a build that drives hull parts
        (gear, grapples) or carries scene effects (the wreck), and for every build under --swfx."""
        if lib.get('manifest') is None:
            return None
        if not (lib['hull_ctl'] is not None or lib['swfx'] or lib.get('own_fx')
                or (cfg.get('swfx') and lib['fx'] is not None)):
            return None
        k = (key, m['finished'])
        if k not in ships_fx:
            fx = lib['fx'] if (cfg.get('swfx') or lib['swfx'] or lib.get('own_fx')) else None
            s = ship_fx.ShipFx(key, lib['coll'], fx, lib['manifest'], hull_ctl=lib['hull_ctl'],
                               clock=not lib['swfx'], own_hull=bool(cfg.get('swfx')))   # turrets train
            for c in (s.hull if s.hull is not lib['coll'] else None, s.fx):
                if c is not None:
                    scene.collection.children.link(c)
                    excluded(c)
            ships_fx[k] = s
        return ships_fx[k]

    def frame_of(loc, heading, pitch, roll):
        q = (Quaternion((0, 0, 1), math.radians(heading)) @ Quaternion((0, 1, 0), -math.radians(pitch))
             @ Quaternion((1, 0, 0), math.radians(roll)))
        return Matrix.Translation(Vector(loc)) @ q.to_matrix().to_4x4()

    def place(key, spec, state=None):
        """state names another FINISHED build of the same hull (a condition state) for this frame."""
        x, y, z, hd, pt, rl, thr = spec
        thr = min(1.0, max(0.0, thr))    # a keyed throttle eased between keys can overshoot (S3 Falcon: -0.24)
        m = {**members[key], 'finished': state} if state else members[key]
        lib = hull(m)
        sfx = ship_fx_for(key, m, lib)
        e = bpy.data.objects.new('Ship | ' + key, None)
        e.instance_type = 'COLLECTION'
        e.instance_collection = sfx.hull if sfx is not None else lib['coll']
        mw = frame_of((x, y, z), hd, pt, rl)
        grow = READABLE.get(key.split('-')[0], 1.0)
        e.matrix_world = mw @ Matrix.Scale(lib['mpu'] * grow, 4)
        link(e, stage_ships)
        if sfx is not None and sfx.fx is not None:
            e2 = bpy.data.objects.new('Ship fx | ' + key, None)
            e2.instance_type = 'COLLECTION'
            e2.instance_collection = sfx.fx
            e2.matrix_world = e.matrix_world
            link(e2, stage_fx)
        placed[key] = dict(matrix=mw, mpu=lib['mpu'] * grow, length=lib['length_m'] * grow, lib=lib, fx=sfx, throttle=thr,
                           build=m.get('finished') or m['game'])
        if sfx is not None:
            sfx.matrix = mw
        if thr <= 0 or (sfx is not None and sfx.fx is not None and not lib['swfx']):
            return      # no throttle, or the kit's own drives draw this ship's plumes
        for pos, dirn, rad, style in lib['engines']:
            style = style if style in STYLE_RGB else 'LOW_TECH'
            p = mw @ (pos * lib['mpu'])
            dw = (mw.to_3x3() @ dirn).normalized()
            r = max(rad * lib['mpu'], 1.0)
            obj('Drive bead | ' + key, bead_me, bead_mats[style], Matrix.Translation(p) @ Matrix.Scale(r * .9, 4))
            q = dw.to_track_quat('Z', 'Y')
            L = r * (4 + 8 * thr)
            obj('Drive plume | ' + key, plume_me, plume_mats[style], Matrix.Translation(p) @ q.to_matrix().to_4x4()
                @ Matrix.Diagonal((r * .9, r * .9, L, 1)))

    def at(key, local=(0, 0, 0)):
        """A point on a placed ship, local in metres in its own frame."""
        return placed[key]['matrix'] @ Vector(local)

    def mining_hit(drone, target, local=None):
        """Where a Borer's mining laser meets the target: (emitter, point, normal, met her hull), world
        metres. Aimed at `local` on the target (metres, its frame) or at its middle, landed where a ray
        from the emitter meets the target's real hull."""
        d = at(drone)
        aim = at(target, local) if local is not None else at(target)
        emit = d + (aim - d).normalized() * placed[drone]['length'] * .55
        vl = next((v for v in scene.view_layers if v.name == 'ships'), bpy.context.view_layer)
        vl.update()
        ray, start, left = (aim - emit).normalized(), emit, (aim - emit).length * 1.5
        for _ in range(12):     # past the drone's own body and any kit object (her dark kit beam is a rod)
            hit, loc, nrm, _i, ob, _m = scene.ray_cast(vl.depsgraph, start, ray, distance=left)
            if not hit or not ((loc - d).length < placed[drone]['length'] * .8 or ob.name.startswith('SWFX')):
                break
            left -= (loc - start).length + .05
            start = loc + ray * .05
        if not hit:             # past the hull: stop at the aim point, facing the drone
            loc, nrm = aim, (emit - aim).normalized()
        return emit, loc, nrm, hit

    def mining(drone, target, local=None):
        """A Borer's mining laser (swfx_mining) from her emitter onto the target's plating
        (mining_hit), with that face's normal."""
        if str(PROLOGUE / 'second-wind-vfx') not in sys.path:
            sys.path.insert(0, str(PROLOGUE / 'second-wind-vfx'))
        import swfx_mining
        emit, loc, nrm, hit = mining_hit(drone, target, local)
        swfx_mining.laser(f'Mining {drone}', emit, loc, nrm, stage_fx, lights=stage_ships,
                          seed=zlib.crc32(drone.encode()) % 997, width_m=.3)
        print(f'ASHARU mining {drone} -> {target}: {(loc - emit).length:.0f} m, '
              f'{"on her hull" if hit else "no hull hit, ends at the aim point"}', flush=True)

    def obj(name, me, mat, matrix, coll=None):
        o = bpy.data.objects.new(name, me)
        o.matrix_world = matrix
        link(o, coll or stage_fx)
        o.material_slots[0].link = 'OBJECT'
        o.material_slots[0].material = mat
        o.visible_shadow = mat in (smoke_mat, debris_mat)   # light and plasma cast no shadow
        return o

    rng = np.random.default_rng(7)
    span_now = {}

    def span_dt():
        """Seconds from the key frame's instant in a motion span (add_span), else 0."""
        return span_now.get('dt', 0.0)

    def segment(p0, p1, radius, mat, name):
        d = p1 - p0
        q = d.normalized().to_track_quat('Z', 'Y')
        obj(name, plume_me, mat, Matrix.Translation(p0) @ q.to_matrix().to_4x4()
            @ Matrix.Diagonal((radius, radius, d.length, 1)))

    def chain(o):
        """An appended object's matrix in its build's root frame (hull units): an object outside the
        scene has no evaluated matrix_world, so its parent chain is composed."""
        w = o.matrix_basis
        while o.parent:
            w = o.parent.matrix_basis @ o.matrix_parent_inverse @ w
            o = o.parent
        return w

    def build_objects(key):
        """The objects a placed ship is drawn from: her own hull copy (ShipFx, its turrets trained)
        first, then the shared build."""
        sfx = placed[key].get('fx')
        own = list(sfx.hull.all_objects) if sfx is not None and sfx.hull is not placed[key]['lib']['coll'] else []
        return own + list(placed[key]['lib']['coll'].all_objects)

    def barrel_tip(objs, name):
        """A muzzle the build does not name, found from the gun itself the way swfx_guns.trainers
        finds a mount's joints: the mount ('Turret 2', 'Weapon WS 005') from the anchor's name, its
        elevation (else pivot) for the bore, and the vertex of its gun, recoil or barrel meshes
        furthest along that bore. None when the mount has no such geometry."""
        m = re.search(r'(turret \d+|weapon ws \d+)', name, re.I)
        if m is None:
            return None
        stem = m.group(1).lower()
        joint = next((o for suffix in (' elevation', ' pivot') for o in objs
                      if ship_fx.base_name(o.name).lower() == stem + suffix), None)
        meshes = [o for o in objs if o.type == 'MESH' and ship_fx.base_name(o.name).lower().startswith(stem + ' ')
                  and any(w in ship_fx.base_name(o.name).lower() for w in (' gun', ' recoil', ' barrel'))]
        if joint is None or not meshes:
            return None
        bore = (chain(joint).to_3x3() @ Vector((1.0, 0.0, 0.0))).normalized()
        best = None
        for o in meshes:
            w = chain(o)
            for v in o.data.vertices:
                q = w @ v.co
                if best is None or q.dot(bore) > best.dot(bore):
                    best = q
        return best

    def muzzle_world(key, muzzle):
        """A shot's start: the bearing mount's muzzle (bearing()), on the placed ship. A named anchor
        is matched by its kit name (ship_fx.base_name), so it is found in her own hull copy while
        ShipFx.kit() has the names swapped (that swap made every anchor looked up inside a kit read
        as missing and fire from the hull centre, animatic 2026-10-23); an anchor the build lacks is
        taken from the barrel (barrel_tip). One with neither is recorded in muzzle_world.missing,
        which SceneFx.projectile_check fails on."""
        if muzzle is None:
            return at(key)
        if muzzle[0] == 'anchor':
            lib = placed[key]['lib']
            objs = build_objects(key)
            o = next((o for o in objs if ship_fx.base_name(o.name) == muzzle[1]), None)
            if o is not None:
                return at(key, chain(o).translation * lib['mpu'])
            tip = barrel_tip(objs, muzzle[1])
            if tip is not None:
                if (key, muzzle[1]) not in muzzle_world.derived:
                    muzzle_world.derived.add((key, muzzle[1]))
                    print('ASHARU muzzle anchor not in the build, taken from its barrel tip:', key, muzzle[1], flush=True)
                return at(key, tip * lib['mpu'])
            if (key, muzzle[1]) not in muzzle_world.missing:
                muzzle_world.missing.append((key, muzzle[1]))
                print('ASHARU ERROR muzzle anchor missing and no barrel to take it from:', key, muzzle[1], flush=True)
            return at(key)
        return at(key, muzzle[1])

    muzzle_world.missing = []       # (ship, anchor) with no muzzle at all: fails projectile_check
    muzzle_world.derived = set()

    CLEAR_GUNS = {}     # (kind, shooter, target) -> every bearing muzzle with a clear line, the chosen one first

    def line_of_sight(kf, spec, muzzles, report):
        """--los: cast from each shot's muzzle to its aim point through the posed hulls (before any effect is
        made) and say what the round meets first: its target, the shooter's own hull, or another ship. A
        stand-in fires from inside its slab, so its own-hull hits are not meaningful."""
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        centres = {k: (at(k), placed[k]['length'] * .6) for k in placed if k in spec['ships']}
        for fx in spec.get('fx', ()):
            if fx[0] not in ('tracer', 'missiles') or fx[1] not in centres or fx[2] not in centres:
                continue
            a, b = fx[1], fx[2]
            p1 = at(b, fx[3] if fx[0] == 'tracer' and len(fx) > 3 else (0, 0, 0))

            def cast(muzzle):
                p0 = muzzle_world(a, muzzle)
                d = (p1 - p0)
                dist = d.length
                d.normalize()
                o = p0 + d * 2.0
                while True:
                    hit, loc, _, _, ob, _ = scene.ray_cast(dg, o, d, distance=max(0.0, dist - (o - p0).length))
                    if not hit:
                        return True, 'nothing (open space to the aim point)'
                    if ob is not None and ob.name.startswith('SWFX'):
                        o = loc + d * .5       # the kit's own volumes (flash boxes, plumes): not armour
                        continue
                    near = min(centres, key=lambda k: (loc - centres[k][0]).length)
                    if (loc - centres[near][0]).length > centres[near][1]:
                        o = loc + d * .5       # not a ship (sky, planet shell, platform): look past it
                        continue
                    what = ('its target' if near == b else 'ITS OWN HULL' if near == a else 'ANOTHER SHIP: ' + near) +                         f' at {(loc - p0).length:.0f} m of {dist:.0f} m' + ('' if near == b else f' ({ob.name})')
                    return near == b, what

            first = muzzles.get(fx[:3])
            clear, what = cast(first)
            tried = 1
            if not clear and members[a].get('finished'):
                for cand in BEARING_CANDIDATES.get((kf,) + tuple(fx[:3]), []):
                    if cand[5] == first:
                        continue
                    tried += 1
                    ok2, what2 = cast(cand[5])
                    if ok2:
                        muzzles[fx[:3]] = cand[5]
                        clear, what = True, what2 + f' (switched to another gun, {cand[2]}, after {tried - 1} blocked)'
                        break
            if clear and fx[0] == 'tracer' and members[a].get('finished'):   # every gun that bears with a clear line
                CLEAR_GUNS[fx[:3]] = [muzzles[fx[:3]]] + [c[5] for c in BEARING_CANDIDATES.get((kf,) + tuple(fx[:3]), [])
                                                          if c[5] != muzzles[fx[:3]] and cast(c[5])[0]]
            if report:
                standin = '' if members[a].get('finished') else '  (stand-in: fires from inside its slab)'
                print(f'ASHARU LOS {kf} {fx[0]:8} {a:12} -> {b:12} {what}{standin}', flush=True)

    def seat_check(kf, spec):
        """A boat set down on its prize (spec 'seated': boat -> prize): with the boat hidden, cast down
        through each gear sole (its manifest's landing sole anchors) onto the prize's hull and print the
        gap, so a boat that floats or sinks into the deck shows without a render. Positive: above the hull."""
        for b, prize in spec.get('seated', {}).items():
            pl = placed[b]
            soles = [(s_['leg'], muzzle_world(b, ('anchor', s_['anchor'])))
                     for s_ in (pl['lib']['manifest'].get('landing') or {}).get('sole_anchors') or []]
            up = (pl['matrix'].to_3x3() @ Vector((0, 0, 1))).normalized()
            mine = [o for o in stage.all_objects if o.name in ('Ship | ' + b, 'Ship fx | ' + b)]
            for o in mine:
                o.hide_viewport = True
            bpy.context.view_layer.update()
            dg = bpy.context.evaluated_depsgraph_get()
            centre_p, reach_p = at(prize), placed[prize]['length'] * .6

            def gap(p):
                o = p + up * 40.0
                for _ in range(64):
                    hit, loc, _, _, ob, _ = scene.ray_cast(dg, o, -up, distance=80.0)
                    if not hit:
                        return None
                    if (ob is not None and ob.name.startswith('SWFX')) or (loc - centre_p).length > reach_p:
                        o = loc - up * .05      # the kit's volumes, or not the prize
                        continue
                    return (p - loc).dot(up)
                return None

            gaps = [(leg, gap(p)) for leg, p in soles]
            row = ', '.join(f'{leg} {"no hull" if g is None else f"{g:+.2f} m"}' for leg, g in gaps)
            ok = bool(gaps) and all(g is not None and abs(g) <= .35 for _, g in gaps)
            print(f'ASHARU seat {kf} {b} on {prize}: {row}  {"SEATED" if ok else "NOT SEATED"}', flush=True)
            if not ok and soles:       # where it would sit flat: the deck around the seat, in the prize's frame
                rot = placed[prize]['matrix'].to_3x3()
                best = None
                for dx in range(-30, 31, 2):
                    for dy in range(-12, 13, 2):
                        t_ = rot @ Vector((dx, dy, 0))
                        gs = [gap(p + t_) for _, p in soles]
                        if any(g is None for g in gs):
                            continue
                        spread = max(gs) - min(gs)
                        if best is None or spread < best[0]:
                            best = (spread, dx, dy, min(gs))
                if best is not None:
                    print(f'ASHARU seat {kf} {b} flattest within 30 m: move ({best[1]:+d}, {best[2]:+d}) m in '
                          f'the {prize} frame and down {best[3]:+.2f} m: soles within {best[0]:.2f} m', flush=True)
            for o in mine:
                o.hide_viewport = False
            bpy.context.view_layer.update()

    def tracer(a, b, local=None, n=6, muzzle=None):
        p0 = muzzle_world(a, muzzle)
        p1 = at(b, local or (0, 0, 0)) + Vector(rng.normal(0, 12, 3))
        firing = True
        if span_now.get('span'):     # in motion: bursts with gaps (owner: no long bursts at nothing)
            import random as _random
            r, t, firing = _random.Random(f'stand-in bursts|{a}|{b}'), -10.0 + _random.Random(a).uniform(0, 1.5), False
            while t < 10.0 and not firing:
                on = r.uniform(.8, 2.0)
                firing = t <= span_dt() < t + on
                t += on + r.uniform(1.0, 2.6)
        fam = streak_family(a)
        for i in range(n):
            length = STREAK_LEN_M[fam]
            f0 = .08 + (rng.uniform(0, .87) + span_dt() * 1200.0 / (p1 - p0).length) % .87   # rounds travel in a span
            f1 = min(1.0, f0 + length / (p1 - p0).length)
            if firing:
                head, tail = p0.lerp(p1, f1), p0.lerp(p1, f0)
                segment(head, tail, streak_radius((head + tail) / 2, STREAK_R_M[fam]), tracer_mat, 'Tracer')

    # A streak is as wide as its round (owner, 2026-10-23, S6c: "how 'wide' a streak should be related
    # to its gun bore"): by weapon family, and never thinner or wider on screen than a believable
    # tracer (STREAK_PX, at 1920 px across), whatever the camera's distance.
    STREAK_R_M = {'mg': .09, 'ac': .16, 'heavy': .3, 'energy': .2}
    STREAK_LEN_M = {'mg': 14.0, 'ac': 18.0, 'heavy': 10.0, 'energy': 22.0}
    STREAK_PX = (.5, 1.4)        # radius: a streak 1 to 3 px wide

    def streak_family(key):
        import scene_fx
        b = placed[key].get('build') if key in placed else None
        w = (scene_fx.WEAPON_OF.get(b) or {}).get(1) or scene_fx.DEFAULT_WEAPON.get(b) or 'lightmg'
        return scene_fx.SceneFx.FAMILY.get(w, "mg")

    def streak_radius(p, r_m):
        c = spec['camera']          # this frame's camera (the scene camera is aimed after the stand-ins)
        d = max(1.0, (Vector(c['loc']) - p).length)
        mpp = 2.0 * d * (18.0 / c['lens']) / 1920.0       # metres per pixel there (36 mm sensor, horizontal)
        return min(max(r_m, STREAK_PX[0] * mpp), STREAK_PX[1] * mpp)

    def cam_at_of(spec):
        """scene frame -> (camera world location, horizontal fov rad) along a span's camera path."""
        path = spec['camera'].get('at')

        def cam_at(f):
            c = path(spec['key_t'] + (f - anchor) / fps_s) if path is not None else spec['camera']
            return Vector(c['loc']), 2.0 * math.atan(18.0 / c['lens'])
        return cam_at

    blob_state = {}

    def white_blob_lint(kf, display, effects_alpha):
        """blob_lint on a finished span frame: near-white regions the effects own, held too long. The
        ships' drives are excluded by where their engines project. Finals raise; ASHARU_AUDIT_SOFT logs."""
        import blob_lint
        from bpy_extras.object_utils import world_to_camera_view
        spec_ = KEYFRAMES[kf]
        h_, w_ = display.shape[:2]
        engines = []
        for key, pl in placed.items():
            for pos, _d, _r, _s in pl['lib'].get('engines', ()):
                c = world_to_camera_view(scene, scene.camera, pl['matrix'] @ (pos * pl['mpu']))
                if c.z > 0:
                    engines.append((c.x * w_, (1.0 - c.y) * h_))
        span_ = spec_['span_of']
        st = blob_state.setdefault(span_, {'lint': blob_lint.Lint(), 'last_t': None})
        n_ = int(kf.rsplit('_f', 1)[1])
        dt = (spec_['span_t'] - st['last_t']) if st['last_t'] is not None else 1.0 / 24
        rows_, flagged_ = st['lint'].frame(n_, dt, display, effects_alpha, engines)
        st['last_t'] = spec_['span_t']
        print(f'ASHARU blob lint {kf}: {len(rows_)} white regions, {len(flagged_)} held'
              + ''.join(f'; {r_}' for r_ in rows_[:4]), flush=True)
        if flagged_ and not os.environ.get('ASHARU_AUDIT_SOFT'):
            raise RuntimeError(f'{kf}: a white region holds past a flash (the old HE blob): {flagged_[:3]}')

    def cam_view_of(spec):
        """scene frame -> (camera world location, rotation quaternion, lens) along a span's camera
        path, as aim() sets the camera (the motion audit's out-of-frame test)."""
        path = spec['camera'].get('at')

        def view(f):
            c = path(spec['key_t'] + (f - anchor) / fps_s) if path is not None else spec['camera']
            loc = Vector(c['loc'])
            d = (Vector(c['target']) - loc) if c.get('target') is not None else Vector(c['look'])
            q = d.normalized().to_track_quat('-Z', 'Y') @ Quaternion((0, 0, 1), math.radians(c.get('roll', 0.0)))
            return loc, q, c['lens']
        return view

    def missiles(a, b, n=5, muzzle=None):
        p0, p1 = muzzle_world(a, muzzle), at(b)
        for i in range(n):
            f = .3 + (rng.uniform(0, .6) + span_dt() * 250.0 / (p1 - p0).length) % .6
            p = p0.lerp(p1, f) + Vector(rng.normal(0, 60, 3))
            back = (p0 - p1).normalized()
            # no orbs (owner, 2026-10-23: "white orbs"): the body is a short streak along its flight
            segment(p + back * 9, p, .9, missile_mat, 'Missile')
            segment(p + back * 9, p + back * 140, 2.5, plume_mats['LOW_TECH'], 'Missile trail')

    def blast(p, radius):
        """No orbs: a torn, spiked burst (swfx_burst's jagged mesh), turned at random, in smoke."""
        if str(PROLOGUE / 'second-wind-vfx') not in sys.path:
            sys.path.insert(0, str(PROLOGUE / 'second-wind-vfx'))
        import random as _random
        import swfx_burst
        me, _ = swfx_burst.lobed_mesh('Blast burst', blast_mat, _random.Random(int(rng.integers(1 << 30))),
                                      lobes=(4, 7), spikes=(5, 9), lean=.3)
        obj('Blast', me, blast_mat, Matrix.Translation(p) @ Euler(rng.uniform(0, 6.3, 3)).to_matrix().to_4x4()
            @ Matrix.Scale(radius * .7, 4))
        obj('Blast smoke', bead_me, smoke_mat, Matrix.Translation(p) @ Matrix.Scale(radius * 1.15, 4))

    def debris(p, spread, n=40):
        for i in range(n):
            o = Vector(rng.normal(0, spread, 3))
            s = rng.uniform(.6, 3.5)
            m = Matrix.Translation(p + o) @ Euler(rng.uniform(0, 6.3, 3)).to_matrix().to_4x4() @ Matrix.Diagonal((s, s * .5, s * .2, 1))
            obj('Debris', bead_me, debris_mat, m)

    def fire(p, toward):
        for i in range(4):
            q = (toward + Vector(rng.normal(0, .25, 3))).normalized().to_track_quat('Z', 'Y')
            obj('Fire', plume_me, fire_mat, Matrix.Translation(p) @ q.to_matrix().to_4x4()
                @ Matrix.Diagonal((5, 5, rng.uniform(18, 35), 1)))

    def smoke(p, toward, n=10):
        for i in range(n):
            f = i / n
            o = toward * (20 + 180 * f) + Vector(rng.normal(0, 6 + 20 * f, 3))
            obj('Smoke', bead_me, smoke_mat, Matrix.Translation(p + o) @ Matrix.Scale(5 + 14 * f, 4))

    def flares(key, life=1.6):
        """Decoy flares (a stand-in for a ship the kit has no flare muzzles on). No orbs (owner,
        2026-10-23: "white orbs around the hound"): each is thrown off her flank at its own moment
        through the shot, a short hot streak along its motion with a smoke tail, slowing and fading,
        and gone after `life` seconds; nothing is drawn before it is thrown or after it burns out."""
        # In the world (owner, 2026-10-23, S6d: flares "stay in relation to the hound = wrong"): each
        # leaves her flank where she was when she threw it, with her velocity then plus its own
        # ejection, which slows; her later turns and rolls do not carry it.
        import random as _random
        dt = span_dt()
        world = None
        if 'span_t' in spec and hasattr(spec['ships'].get(key), 'at'):
            ms = ship_matrix_at(spec)
            world = lambda t_s: ms(key, anchor + t_s * fps_s)        # t_s: seconds from the key frame
        for i in range(10):
            side = rng.choice((-1, 1))
            s0 = Vector((rng.uniform(-20, 10), side * rng.uniform(8, 15), rng.uniform(0, 8)))
            out = Vector((rng.uniform(-60, 40), side * rng.uniform(50, 120), rng.uniform(-10, 40))).normalized()
            thrown = rng.uniform(-2.0, 4.0)              # seconds from the key frame
            age = dt - thrown
            if not 0.0 <= age <= life:
                continue
            go = lambda a, s0=s0, out=out: s0 + out * 70.0 * (a - a * a / (2.2 * life))     # thrown hard, slowing
            if world is None:
                pos = lambda a, go=go: at(key, go(a))
            else:
                m0 = world(thrown)
                v = (world(thrown + .05).translation - world(thrown - .05).translation) / .1
                pos = lambda a, m0=m0, v=v, go=go, s0=s0: m0 @ s0 + v * a + m0.to_3x3() @ (go(a) - s0)
            heat = 1.0 - age / life
            head, tail = pos(age), pos(max(0.0, age - .12))
            if (head - tail).length > .05:
                segment(tail, head, streak_radius(head, .12 + .1 * heat), flare_mat, 'Flare')
            # thin smoke, broken into wisps that widen and drift apart as they age, not one rod
            wr = _random.Random(f'flare wisps|{key}|{i}')
            for k in range(1, 9):
                a1, a2 = age - .07 * k, age - .07 * (k + 1)
                if a2 < 0:
                    break
                if wr.random() < .35:
                    continue
                spread = age - a1
                j1 = Vector([wr.gauss(0, .2 + 1.4 * spread) for _ in range(3)])
                j2 = Vector([wr.gauss(0, .2 + 1.4 * spread) for _ in range(3)])
                segment(pos(a2) + j2, pos(a1) + j1, .1 + .6 * spread, smoke_mat, 'Flare smoke')

    def entry_trails(labels3d):
        """Two entry trails 150-45 km up, 150-800 km downrange and heading north, from orbit: each
        hull's bow shock and core at the far, low end, its wake cooling and widening back up the
        track toward the camera, and a faint ionisation trail beyond (swfx_entry, W7)."""
        if str(PROLOGUE / 'second-wind-vfx') not in sys.path:
            sys.path.insert(0, str(PROLOGUE / 'second-wind-vfx'))
        import swfx_entry
        for name, side, scale in (('trail-colossus', 0.0, 1.0), ('trail-morrow', -.0045, .7)):
            pts = []
            for i in range(61):
                f = i / 60
                th = (150e3 + 650e3 * f) / R              # downrange arc, prograde (north-east)
                alt = 150e3 - 105e3 * f ** .8
                sd = side + .0035 * f                      # a slight drift off the track
                dirn = Vector((math.sin(th), sd, math.cos(th))).normalized()
                pts.append(centre + dirn * (R + alt))
            labels3d[name] = swfx_entry.trail('Entry ' + name, pts, stage_fx, width_m=1600 * scale, glow=scale)

    def entry_kit():
        if str(PROLOGUE / 'second-wind-vfx') not in sys.path:
            sys.path.insert(0, str(PROLOGUE / 'second-wind-vfx'))
        import swfx_entry
        return swfx_entry

    def entry_sheath(pose, key, t):
        """S12: a falling hull inside her plasma, shaped by her own hull (swfx_entry.sheath): the sheath
        white on the faces she meets the air with, the shock layer standing off them, flames peeling
        off her silhouette into one wake that grows as she goes deeper since 126 s."""
        kit = entry_kit()
        e = bpy.data.objects.get('Ship | ' + key)
        if e is None:
            return
        verts, faces = kit.hull_soup(e)
        kit.sheath('Entry ' + key, verts, faces, stage_fx, K6_WIND, SHIP_LENGTH[key], glow=1.0,
                   streamers=22 if key == 'colossus' else 16, seed=len(key), grow=min(1.0, (t - 126.0) / 4.5),
                   lights=stage_ships,      # the sheath's glow on her own windward plates
                   belly=e.matrix_world.to_3x3() @ Vector((0.0, 0.0, -1.0)))

    def wreckfall(t):
        """S12: the ships the battle lost coming down in pieces (WRECKFALL): each piece a short entry
        charred rock burning on its windward face (the heroes' sheath, smaller), flickering; two
        chunks split during the shot."""
        import random
        kit = entry_kit()
        for pc in WRECKFALL:
            if t < pc['t_in']:
                continue
            rnd = random.Random(pc['seed'])
            age = min(t - pc['t_in'], 1.5 if pc['kind'] == 'chunk' else .8)

            def at(tt, pc=pc):
                return Vector([pc['at'][i] + pc['vel'][i] * (tt - K6_T) for i in range(3)])
            parts = [(0.0, 0.0, 0.0)]
            split = pc.get('split')
            if split is not None and t > split:     # breaking up in the shot: splinters peel off
                parts += [tuple(rnd.gauss(0, 140) for _ in range(3)) for _ in range(2)]
            # a tumbling fragment's head flickers: its brightness per frame, from the frame's own time
            flick = random.Random(pc['seed'] * 1000 + int(round(t * 24))).uniform(.6, 1.35)
            for k, dv in enumerate(parts):
                head = at(t) + Vector(dv) * (t - split if k else 0.0)
                grown = age if k == 0 else t - split
                size = pc['size'] * (1 if k == 0 else rnd.uniform(.4, .65))
                rv, rf = kit.rock('Wreckfall %s %d rock' % (pc['name'], k), head, size, pc['seed'] * 7 + k,
                                  stage_fx, turn=(t - K6_T) * rnd.uniform(.4, 1.2))
                kit.sheath('Wreckfall %s %d' % (pc['name'], k), rv, rf, stage_fx, K6_WIND, size,
                           glow=(.5 if pc['kind'] == 'chunk' else .3) * flick * (1 if k == 0 else .8),
                           streamers=4 if pc['kind'] == 'chunk' else 2, seed=pc['seed'] * 7 + k,
                           grow=min(1.0, .3 + grown), voxel=size / 9.0)

    def field_matrix(piece, box, t, local=None):
        """A debris box's world matrix at time t: the wreck turning about its own axis (debris_field).
        local: the wreck kit's 3x3 for the box (wreck_kit.pick), in place of the unit box's stretch."""
        c, half, r = FIELD[piece].boxes_at(t)[box]
        rz = frame_of(Vector((0, 0, 0)), PLATFORM_HD, 0, 0).to_3x3()
        fit = Matrix(local).to_4x4() if local is not None else Matrix.Diagonal((*half, 1))
        return Matrix.Translation(Vector(on_platform(c))) @ (rz @ Matrix(r)).to_4x4() @ fit

    kit_coll = []

    def wreck_kit_collection():
        """The wreck kit's ASHARU WRECKS collection, appended once and excluded (its objects are
        instanced, never drawn in place). A lit render refuses to fall back to the stand-ins: a
        missing or different build is an error, not a quieter picture."""
        if not kit_coll:
            f = roster.WRECKS
            path = BUILDS / f['dir'] / f['file']
            if not path.exists():
                raise SystemExit(f'wreck kit missing: {path} (Drive {f["drive"]}); '
                                 'or pass --blockout-wrecks for the stand-ins')
            import hashlib
            sha = hashlib.sha256(path.read_bytes()).hexdigest()
            if sha != f['asset_sha256']:
                raise SystemExit(f'wreck kit {path} is {sha[:12]}, roster.WRECKS wants {f["asset_sha256"][:12]}')
            if not wreck_kit.station_current(platform_parts()):
                raise SystemExit('the wreck kit station was built from another platform_parts(): rebuild the kit')
            kit_coll.append(append(path, 'ASHARU WRECKS'))
        return kit_coll[0]

    def kit_instance(name, src, matrix):
        """A kit object placed by matrix, keeping its own materials (they read its face attributes)."""
        o = src.copy()
        o.name = name
        o.matrix_world = matrix
        link(o, stage_ships)
        o.visible_shadow = True
        return o

    def platform(p, t=K1B_T):
        """The derelict Asharu platform (platform_parts) and its debris field (FIELD, posed at t), at p,
        casting shadows its hidden ships lie in. A lit render draws the wreck kit (roster.WRECKS): the
        station as one modelled piece in the platform's frame, and each box filled by the kit wreck
        wreck_kit.pick chooses for it. The motion preview, and --blockout-wrecks, draw the blockout:
        the station's boxes and drums, and wreck_meshes' broken shapes."""
        use_kit = not cfg.get('motion_preview') and not cfg.get('blockout_wrecks')
        if use_kit:
            kit = wreck_kit_collection().all_objects
            station_obj, _ = wreck_kit.station()
            kit_instance('Platform station', kit[station_obj],
                         Matrix.Translation(Vector(PLATFORM)) @ Matrix.Rotation(math.radians(PLATFORM_HD), 4, 'Z'))
            for i, piece in enumerate(FIELD):
                for j, (_, half, _) in enumerate(piece.boxes):
                    pk = wreck_kit.pick(half, i * 31 + j)
                    o = kit_instance('Platform debris %d.%d' % (i, j), kit[pk.object],
                                     field_matrix(i, j, t, pk.local))
                    o.rotation_mode = 'QUATERNION'
                    o['field_piece'], o['field_box'], o['kit_local'] = i, j, [v for row in pk.local for v in row]
            print('ASHARU wreck kit: the station and %d wrecks placed (%s)' % (
                sum(len(pc.boxes) for pc in FIELD), roster.WRECKS['drive']), flush=True)
            return
        mat = principled('Platform', (.32, .3, .28), rough=.75, metal=.4)
        if 'Drum unit' not in bpy.data.meshes:
            me = bpy.data.meshes.new('Drum unit')
            bm = bmesh.new()
            bmesh.ops.create_cone(bm, cap_ends=True, segments=20, radius1=1.0, radius2=1.0, depth=2.0)
            bmesh.ops.rotate(bm, verts=bm.verts[:], cent=(0, 0, 0), matrix=Matrix.Rotation(math.radians(90), 3, 'Y'))
            bm.to_mesh(me)
            bm.free()
            me.materials.append(mat)
        drum_me = bpy.data.meshes['Drum unit']
        for kind, name, c, size, (hd, pt, rl) in platform_parts():
            m = frame_of(Vector(on_platform(c)), hd + PLATFORM_HD, pt, rl)
            if kind == 'box':
                o = obj(name, box_me, mat, m @ Matrix.Diagonal((*size, 1)), stage_ships)
            else:
                o = obj(name, drum_me, mat, m @ Matrix.Diagonal((size[1], size[0], size[0], 1)), stage_ships)
            o.visible_shadow = True
        for i, piece in enumerate(FIELD):
            for j, (_, half, _) in enumerate(piece.boxes):
                # a broken hull, section, plate, spar or chunk inside the box, not the box itself (owner,
                # 2026-10-22: the stand-ins "must not read as boxes"); the box stays what --check measures
                kind, verts, faces = wreck_meshes.wreck(half, i * 31 + j)
                me = bpy.data.meshes.new('Wreck %s %d.%d' % (kind, i, j))
                me.from_pydata(verts, [], faces)
                me.validate()
                me.materials.append(mat)
                o = obj('Platform debris %d.%d' % (i, j), me, mat, field_matrix(i, j, t), stage_ships)
                o.visible_shadow = True
                o.rotation_mode = 'QUATERNION'
                o['field_piece'], o['field_box'] = i, j

    def apply_cue(key, s, cue):
        """One ship's effects cue for this frame (KEYFRAMES 'cues'): 'fire' lists the guns flashing
        ('t1_1' ..., or 'all'); 'impacts': 'cookoff' marks the wreck with W6's cause (the lead
        Lasher's bursts and the Harpoons, timed against her own break_frame) and draws her embers
        at ship scale; any other entry sets that knob on this ship only."""
        for name, val in cue.items():
            if name == 'fire':
                for lab in (s.gun_labels() if val == 'all' else val):
                    s.flash(lab)
            elif name == 'impacts' and val == 'cookoff':
                import bench_impacts
                with s.kit() as (ctx, _kit):
                    imp = bench_impacts._imp()
                    imp.apply_cue(ctx, bench_impacts.cookoff_entries(int(s.get('break_frame'))),
                                  matrix=placed[key]['matrix'])
                    imp.refine_sparks(ctx)
            else:
                s.set(name, val)

    def fx_report(kf):
        """--fx-check: what each ship's own copy reads on this frame, evaluated."""
        dg = bpy.context.evaluated_depsgraph_get()
        for k, pl in placed.items():
            if pl['fx'] is not None:
                row = pl['fx'].reading(dg)
                FX_ROWS.append(dict(frame=kf, ship=k, **row))
                print(f'ASHARU fx {kf} {k:12} {json.dumps(row)}', flush=True)
                # Metre scale (ship_fx.metre_scale): the kit tunes lights and volumes in a hull-unit file;
                # here each light should read its kit energy x mpu^2 (exposure 2*log2(mpu)) and each
                # volume material should carry the 1/mpu it was scaled by.
                lights = [o for o in pl['fx'].ids if isinstance(o, bpy.types.Object) and o.type == 'LIGHT'
                          and o.users_collection]
                lit = [(o, o.data.evaluated_get(dg).energy, o.data.exposure) for o in lights]
                lit = [t for t in lit if t[1] > 0]
                want = 2 * math.log2(pl['mpu'])
                off = [ship_fx.base_name(o.name) for o in lights if abs(o.data.exposure - want) > 1e-4]
                mats = [o for o in pl['fx'].ids if isinstance(o, bpy.types.Material) and o.node_tree
                        and ship_fx._volume_leaves(o.node_tree) != ([], [])]
                unscaled = [ship_fx.base_name(o.name) for o in mats if o.get(ship_fx.SCALED) != pl['mpu']]
                top = max(lit, key=lambda t: t[1]) if lit else None
                print(f'ASHARU light {kf} {k:12} mpu {pl["mpu"]:.3f} exposure {want:+.2f} '
                      f'lights {len(lights)} lit {len(lit)} off {len(off)} '
                      f'sum {sum(e * 2 ** x for _, e, x in lit):.0f} W'
                      + (f' top {ship_fx.base_name(top[0].name)} {top[1]:.0f} W kit -> {top[1] * 2 ** top[2]:.0f} W'
                         if top else '')
                      + f' volumes {len(mats)} unscaled {len(unscaled)}' + (f' {off[:3]} {unscaled[:3]}' if off or unscaled else ''),
                      flush=True)
                # Per-object colour (ship_fx.own_colours): nothing may still read the drawing object's
                # colour, and every routed node must carry the object's own colour curve.
                c = ship_fx.colour_audit(pl['fx'])
                print(f'ASHARU colour {kf} {k:12} {json.dumps(c)}', flush=True)
                # Stale transforms: the copy is only ever instanced, never evaluated, so a stored
                # matrix_world can disagree with its parent chain; anything reading it is off by this.
                worst, name = 0.0, ''
                for o in pl['fx'].ids:
                    if isinstance(o, bpy.types.Object) and o.parent is not None:
                        d = (o.matrix_world.translation - mat_chain(o).translation).length
                        if d > worst:
                            worst, name = d, ship_fx.base_name(o.name)
                print(f'ASHARU stale {kf} {k:12} worst {worst:.2f} u = {worst * pl["mpu"]:.1f} m {name}', flush=True)

    def volume_audit(kf, label=''):
        """--volume-audit: every volume the camera can see on this frame, by what it costs to march: its
        share of the frame times its depth along the view, and the 20 worst (no render)."""
        from bpy_extras.object_utils import world_to_camera_view
        import re
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        cam_o = scene.camera
        rows, n = [], 0
        for inst in dg.object_instances:
            ob = inst.object
            if ob.type != 'MESH' or not ob.material_slots:
                continue
            vol = [sl.material for sl in ob.material_slots if sl.material and sl.material.node_tree and any(
                n_.type == 'OUTPUT_MATERIAL' and n_.inputs['Volume'].is_linked for n_ in sl.material.node_tree.nodes)]
            if not vol:
                continue
            orig = ob.original
            if orig.hide_render:
                continue
            n += 1
            mw = inst.matrix_world
            pts = [mw @ Vector(c) for c in ob.bound_box]
            uv = [world_to_camera_view(scene, cam_o, p) for p in pts]
            if all(q.z <= 0 for q in uv):
                continue
            xs = [min(1, max(0, q.x)) for q in uv if q.z > 0]
            ys = [min(1, max(0, q.y)) for q in uv if q.z > 0]
            area = (max(xs) - min(xs)) * (max(ys) - min(ys)) if xs else 0
            size = max((pts[6] - pts[0]).length, 1e-6)
            rows.append((area * size, area, size, orig.name, vol[0].name))
        rows.sort(reverse=True)
        print(f'ASHARU volumes {kf} {label}: {n} visible volume instances, {len(rows)} in front of the camera, '
              f'sum size {sum(r[2] for r in rows):.0f} m, sum frame share {sum(r[1] for r in rows) * 100:.1f}%', flush=True)
        kinds = {}
        for cost, area, size, name, mat in rows:
            k_ = re.sub(r'( \S*\d\S*)+$', '', ship_fx.base_name(name))
            c_ = kinds.setdefault(k_, [0, 0.0, 0.0])
            c_[0] += 1
            c_[1] += size
            c_[2] += area
        for k_, (c_, sz, ar) in sorted(kinds.items(), key=lambda t: -t[1][1])[:14]:
            print(f'ASHARU volume kind {kf} {label:10} {k_[:40]:40} x{c_:3}  sum size {sz:7.0f} m  sum area {ar * 100:5.2f}%', flush=True)

    def clear_stage():
        for o in list(stage.all_objects):
            bpy.data.objects.remove(o)
        placed.clear()

    cam = link(bpy.data.objects.new('Camera', bpy.data.cameras.new('Camera')))
    scene.camera = cam
    cam.data.sensor_fit = 'HORIZONTAL'
    cam.data.sensor_width = 36.0
    cam.data.clip_start = 2.0
    cam.data.clip_end = 4.0e6

    def aim(loc, target=None, lens=35, look=None, roll=0.0):
        cam.location = Vector(loc)
        d = (Vector(target) - Vector(loc)) if target is not None else Vector(look)
        cam.rotation_mode = 'QUATERNION'
        cam.rotation_quaternion = d.normalized().to_track_quat('-Z', 'Y') @ Quaternion((0, 0, 1), math.radians(roll))
        cam.data.lens = lens
        cam.data.type = 'PERSP'
        bpy.context.view_layer.update()

    def project(p):
        v = world_to_camera_view(scene, cam, Vector(p))
        if v.z <= 0 or not (-.05 <= v.x <= 1.05 and -.05 <= v.y <= 1.05):
            return None
        return [round(v.x * W, 1), round((1 - v.y) * H, 1)]

    def ship_label(key):
        if key not in placed:
            return None
        pl = placed[key]
        p = pl['matrix'].translation
        # Below the hull's footprint on screen: drop the point by half the hull's length.
        dn = cam.matrix_world.to_3x3() @ Vector((0, -1, 0))
        return project(p + dn * pl['length'] * .45)

    # ------------------------------------------------------------------------- roster sheets
    def bounds(lib):
        """(length, beam, height) of a library hull in metres, from its collection's objects."""
        lo, hi = Vector((1e9,) * 3), Vector((-1e9,) * 3)
        for o in lib['coll'].all_objects:
            if o.type != 'MESH':
                continue
            for c in o.bound_box:
                w = mat_chain(o) @ Vector(c)
                lo = Vector(map(min, lo, w))
                hi = Vector(map(max, hi, w))
        d = (hi - lo) * lib['mpu']
        return d.x, d.y, d.z

    def mat_chain(o):
        m = o.matrix_basis.copy()
        while o.parent is not None:
            m = o.parent.matrix_basis @ o.matrix_parent_inverse @ m
            o = o.parent
        return m

    def roster_sheet(kf):
        """Both fleets in rows at true relative size, with the scale references: r1 from above
        (the game's own view), r2 broadside. Orthographic, planet hidden, studio-lit."""
        ground.hide_render = True
        air.hide_render = True
        sun.data.energy = 4.0
        sun.rotation_euler = Vector((.35, -.55, .76)).to_track_quat('Z', 'Y').to_euler()
        rows = []
        for fleet, ms in (('merchant', roster.MERCHANT), ('pirate', roster.PIRATE), ('reference', roster.REFERENCES)):
            seen, row = set(), []
            for m in ms:
                tag = (m['game'], bool(m.get('tint')))
                if tag in seen:
                    continue
                seen.add(tag)
                row.append(m)
            rows.append(row)
        gap = 22.0
        width = max(sum(hull(m)['length_m'] for m in row) + gap * (len(row) - 1) for row in rows)
        beam = [max(bounds(hull(m))[1 if kf == 'r1' else 2] for m in row) for row in rows]
        heights = [b + 70.0 for b in beam]                 # the hull plus room for its label
        ortho = max(width * 1.06, sum(heights) * 1.25 * W / H)
        top = sum(heights) / 2 + 20.0
        lab = {}
        for ri, row in enumerate(rows):
            total = sum(hull(m)['length_m'] for m in row) + gap * (len(row) - 1)
            x = -total / 2
            yrow = top - sum(heights[:ri]) - beam[ri] / 2
            for m in row:
                L = hull(m)['length_m']
                cxm = x + L / 2
                if kf == 'r1':
                    loc = (cxm, yrow, 0)
                    place(m['key'], (*loc, 0, 0, 0, 0))
                else:
                    loc = (cxm, 0, yrow)
                    place(m['key'], (*loc, 0, 0, 0, 0))
                lab[m['key']] = dict(name=m['name'], length_m=round(L, 1), row=ri,
                                     basis=roster.size_row(m)['basis'], at=None, loc=list(loc))
                x += L + gap
        if kf == 'r1':
            cam.location = (0, 0, 2000)
            cam.rotation_mode = 'XYZ'
            cam.rotation_euler = (0, 0, 0)
        else:
            cam.location = (0, -2000, 0)
            cam.rotation_mode = 'XYZ'
            cam.rotation_euler = (math.radians(90), 0, 0)
        cam.data.type = 'ORTHO'
        cam.data.ortho_scale = ortho
        bpy.context.view_layer.update()
        for k, v in lab.items():
            pl = placed[k]
            p = pl['matrix'].translation
            b = beam[lab[k]['row']] / 2 + 8
            off = Vector((0, -b, 0)) if kf == 'r1' else Vector((0, 0, -b))
            v['at'] = project(p + off)
        lab['_metres_per_px'] = ortho / W
        world.node_tree.nodes['Background'].inputs['Color'].default_value = (.035, .037, .042, 1)
        world.node_tree.nodes['Star gain'].inputs[1].default_value = 0.0      # a roster sheet is a studio
        return lab

    # ---------------------------------------------------------------------------- layers (--layers)
    # A key frame rendered as three view layers into one multilayer EXR, composed by composite_layers.py, so
    # the backdrop and the effects can change without the hulls being rendered again (owner, 2026-10-22):
    #   backdrop  the planet and the starfield, at BACKDROP_SAMPLES; the ships take no part
    #   ships     the hulls at full samples; the effects and the planet are indirect-only, so every drive,
    #             muzzle flash, burst light, the sun and the planet's bounce still light them
    #   effects   volumes, shields, rounds and bursts; ships and planet are holdouts (they still cast and bounce)
    # Each layer writes Cryptomatte (object, material, asset) and depth. A backdrop already rendered for the same
    # locked camera stands in for the backdrop layer, which then does not render. A painted plate
    # (composite_layers.plate_for) replaces it in the composite but not in the render: a PNG plate's swap
    # and a matted plate's sky both need the rendered one.
    ROLES = {'backdrop': {'Stage': 'exclude'},
             'ships': {'Stage | effects': 'indirect', 'Backdrop': 'indirect'},
             'effects': {'Stage | ships': 'holdout', 'Backdrop': 'holdout'}}

    def walk(lc):
        yield lc
        for c in lc.children:
            yield from walk(c)

    def layered(kf, backdrop_needed):
        """The three view layers for this frame, their collections synced to the base layer's exclusions
        (ShipFx sources and appended libraries are excluded there as they are made), then given their role."""
        base_vl = scene.view_layers[0]
        base_vl.use = False
        base = {lc.collection.name: lc.exclude for lc in walk(base_vl.layer_collection)}
        for name in composite_layers.LAYERS:
            vl = scene.view_layers.get(name) or scene.view_layers.new(name)
            vl.use = name != 'backdrop' or backdrop_needed
            vl.samples = min(cfg['samples'], composite_layers.BACKDROP_SAMPLES) if name == 'backdrop' else cfg['samples']
            vl.cycles.use_denoising = True
            vl.use_pass_z = True
            vl.use_pass_environment = name == 'backdrop'
            vl.use_pass_cryptomatte_object = True
            vl.use_pass_cryptomatte_asset = True
            vl.use_pass_cryptomatte_material = True
            role = ROLES[name]
            for lc in walk(vl.layer_collection):
                if lc is vl.layer_collection:
                    continue
                lc.exclude = base.get(lc.collection.name, False)
                lc.holdout = lc.indirect_only = False
                r = role.get(lc.collection.name)
                if r == 'exclude':
                    lc.exclude = True
                elif r == 'holdout':
                    lc.holdout = True
                elif r == 'indirect':
                    lc.indirect_only = True
        return layer_audit(kf)

    def layer_audit(kf):
        """What each layer will draw, from the collections alone: a kit object inside a hull copy would render
        on the ships layer and never change with the effects, and a loose object at the scene root renders on
        every layer. Both are leaks; the frame's row says so."""
        leaks = []
        for e in stage_ships.all_objects:
            for o in (e.instance_collection.all_objects if e.instance_collection else ()):
                if ship_fx.base_name(o.name).startswith('SWFX') and o.type != 'EMPTY':
                    leaks.append(f'{o.name} in the hull of {e.name}')
        loose = [o.name for o in scene.collection.objects if o.type not in ('CAMERA', 'LIGHT')]
        n = {c.name: sum(1 for _ in c.all_objects) for c in (stage_ships, stage_fx, backdrop)}
        print(f'ASHARU layers {kf}: ships {n[stage_ships.name]} objects, effects {n[stage_fx.name]}, '
              f'backdrop {n[backdrop.name]}; kit objects in hull copies {len(leaks)}, loose at the root {len(loose)}',
              flush=True)
        for row in leaks[:12] + loose[:12]:
            print('ASHARU layers leak', kf, row, flush=True)
        return not leaks and not loose

    def backdrop_key(kf):
        """Everything the backdrop layer's pixels depend on: a locked camera's backdrop renders once."""
        import hashlib
        cam_m = [round(v, 4) for row in cam.matrix_world for v in row]
        world_n = world.node_tree.nodes
        h = hashlib.sha256(json.dumps([cam_m, cam.data.type, cam.data.lens, cam.data.ortho_scale,
                                       [round(v, 5) for v in sun.rotation_euler], sun.data.energy,
                                       list(world_n['Background'].inputs['Color'].default_value),
                                       world_n['Star gain'].inputs[1].default_value,
                                       scene.render.resolution_percentage,
                                       min(cfg['samples'], composite_layers.BACKDROP_SAMPLES),
                                       frame_meta(0, False, cfg.get('surface', SURFACE_DETAIL))]).encode())
        return h.hexdigest()[:16]

    def ship_matrix_at(spec):
        """(key, scene frame) -> that ship's world matrix (metres) at the frame's time in a motion span:
        the kit's guns track their targets along both ships' flights (scene_fx.SceneFx.tracking)."""
        def at(key, f):
            sp = spec['ships'][key]
            x, y, z, hd, pt, rl, _thr = sp.at(spec['key_t'] + (f - anchor) / fps_s) if hasattr(sp, 'at') else sp
            return frame_of((x, y, z), hd, pt, rl)
        return at

    labels = {}
    anchor = scene.frame_current      # every frame's effects are built at this frame (the key frame's instant)
    fps_s = scene.render.fps / scene.render.fps_base
    built_for, kit_drew = [None], set()
    for kf in cfg['frames']:
        # --kit-once: a motion span's frames share one kit build. The first frame of a run builds every
        # ship's copy with the whole shot's effects keyed (scene_fx fires through the shot); the rest only
        # re-place the hulls, redraw the world-space stand-ins and step the scene frame.
        span_of = SHOTS[kf].get('span_of') if kf in SHOTS and cfg.get('kit_once') else None
        step = span_of is not None and span_of == built_for[0]
        scene.frame_set(anchor)
        clear_stage()
        if not step:
            for s in ships_fx.values():      # every ship's copy is rebuilt per key frame: one frame's kit effects
                s.remove()                   # (rounds, impacts, shields) never show on another
            ships_fx.clear()
        air.hide_render = False
        ground.hide_render = False
        sun.data.energy = 4.6
        sun.rotation_euler = sun_dir.to_track_quat('Z', 'Y').to_euler()
        world.node_tree.nodes['Background'].inputs['Color'].default_value = (.0008, .0009, .0014, 1)
        world.node_tree.nodes['Star gain'].inputs[1].default_value = STAR_GAIN
        if kf in SHOTS:
            spec = SHOTS[kf]
            rng = np.random.default_rng(7)     # every frame draws the same stand-ins: a span's do not flicker
            span_now['dt'] = spec['span_t'] - spec['key_t'] if 'span_t' in spec else 0.0
            span_now['span'] = 'span_t' in spec
            # The kit is the shot's, not the chunk's: under --kit-once it is built with every ship where
            # she is as the shot opens, whichever chunk this run renders, so every chunk plans the same
            # guns, rounds and hits and nothing pops at a chunk boundary (2026-10-23: S7's chunks
            # planned 0, 5, 5 and 6 rounds for one gun); the ships are then put back at this frame.
            build_at = spec['shot_t'][0] if (not step and cfg.get('kit_once') and 'span_t' in spec) else None
            if build_at is not None:
                span_now['dt'] = build_at - spec['key_t']
                staged = set(stage.all_objects)
                for key, sp in KEYFRAMES[spec['span_of']]['ships'].items():
                    place(key, at_time(sp, build_at) if hasattr(sp, 'at') else sp, spec.get('states', {}).get(key))
                build_placed = [o for o in stage.all_objects if o not in staged]
                print(f"ASHARU kit {kf}: built at the shot's opening, {build_at:.3f} s", flush=True)
            else:
                for key, sp in spec['ships'].items():
                    place(key, sp, spec.get('states', {}).get(key))
            if not step:                     # the kit's build: once per run under --kit-once
                for lib in library.values():     # a build's control knobs: this frame's pose, else its saved pose
                    if lib.get('ctl') is not None:
                        for name, rest in lib['knobs'].items():
                            lib['ctl'][name] = rest
                for s in ships_fx.values():      # every ship's own copy: saved pose, no flashes
                    s.reset()
                for key, pl in placed.items():   # the kit's drives follow the placement's throttle
                    if pl['fx'] is not None and pl['fx'].fx is not None and not pl['lib']['swfx']:
                        pl['fx'].set('thr_throttle', pl['throttle'])
                    if pl['fx'] is not None and pl['fx'].fx is not None:
                        pl['fx'].vacuum()        # Asharu is fought in orbit: no hanging haze, no landing dust
                for key, kn in spec.get('knobs', {}).items():
                    for name, val in kn.items():
                        if isinstance(val, dict):     # {'ago_s': t}: a frame t seconds before this one
                            val = scene.frame_current - round(val['ago_s'] * scene.render.fps / scene.render.fps_base)
                        if placed[key]['fx'] is not None:
                            placed[key]['fx'].set(name, val)
                        else:
                            placed[key]['lib']['ctl'][name] = val
                for key, cue in spec.get('cues', {}).items() if cfg.get('swfx') else ():
                    if 'span_t' in spec:     # in motion a gun flashes when it fires a round, not on cue
                        cue = {k: v for k, v in cue.items() if k != 'fire'}
                    apply_cue(key, placed[key]['fx'], cue)
                if ships_fx or any(lib.get('ctl') is not None for lib in library.values()):
                    ship_fx.refresh(scene)     # drivers read the knobs
                    bpy.context.view_layer.update()
                if cfg.get('swfx'):            # no orb explosions: a built cook-off becomes bursts at its break_frame
                    for key, pl in placed.items():
                        if pl['fx'] is not None and pl['fx'].groups.get('SWFX | Fire') is not None:
                            import swfx_fire
                            with pl['fx'].kit() as (ctx, _kit):
                                print(f'ASHARU cook-off bursts {key}', swfx_fire.jagged_cookoff(ctx), flush=True)
                                print(f'ASHARU vents rebuilt {key}: {swfx_fire.revent(ctx)}', flush=True)
                    ship_fx.refresh(scene)
                    bpy.context.view_layer.update()
            extra3d = {}
            if spec.get('platform'):
                platform(spec['platform'], spec.get('platform_t', K1B_T))
                extra3d['platform'] = Vector(spec['platform']) + Vector((0, 0, -140))
            # under --kit-once the guns are picked where the ships are as the shot opens, as the kit is
            # built: a chunk that picked them at its own first frame fired other turrets than the chunk
            # before it, and the rounds in flight jumped at the boundary (2026-10-25: S6b's merchant Wolf
            # t3 then t2; S9b's lead Lasher t1 then t2/t3, failing the link its first chunk drew)
            shot_ships = ({k: at_time(sp, build_at) if hasattr(sp, 'at') else sp
                           for k, sp in KEYFRAMES[spec['span_of']]['ships'].items()} if build_at is not None else None)
            muzzles = {(k, a, b): (best[5] if best else None) for k, a, b, best, _ in bearing(kf, shot_ships)}
            CLEAR_GUNS.clear()
            line_of_sight(kf, spec, muzzles, cfg.get('los'))     # picks, per shot, a bearing gun with a clear line
            if build_at is not None:
                # the guns this chunk fires, for assemble_shot --guns: every chunk of a shot must have
                # picked the same, or the rounds in flight jump at a chunk seam (coordinator, 2026-10-25)
                guns_ = {' '.join(k): [str(m) for m in (CLEAR_GUNS.get(k) or [muzzles.get(k)])] for k in sorted(muzzles)}
                (raw / f"{spec['span_of']}_guns_f{int(kf.rsplit('_f', 1)[1]):05d}.json").write_text(
                    json.dumps(guns_, indent=1, sort_keys=True))
                print(f'ASHARU guns {kf}: {guns_}', flush=True)
            seat_check(kf, spec)
            if not step:
                kit_drew = set()
            if cfg.get('swfx') and not step:     # the finished kit draws what it can (scene_fx); the stand-ins the rest
                import scene_fx
                sfx_ = scene_fx.SceneFx(scene, placed, lambda k: placed[k]['build'],
                                        muzzle_world, scene.frame_current, log=lambda t: print(t, flush=True),
                                        span=tuple(anchor + round((t_ - spec['key_t']) * fps_s)
                                                   for t_ in spec['shot_t']) if 'span_t' in spec else None,
                                        cur=anchor + round((spec['span_t'] - spec['key_t']) * fps_s)
                                        if 'span_t' in spec and not cfg.get('kit_once') else None,
                                        matrix_at=ship_matrix_at(spec) if 'span_t' in spec else None,
                                        cam_at=cam_at_of(spec) if 'span_t' in spec else None)
                for key, sh in spec.get('shields', {}).items():
                    sfx_.shield(key, sh)
                for i, fx in enumerate(spec.get('fx', ())):
                    kind, done = fx[0], False
                    if kind == 'tracer':
                        # ('tracer', a, b, aim, opts): opts trained=True (on b when the shot opens), cease_t
                        # (sequence time b dies; no round after it), walk (scene_fx.tracer) and bursts_t
                        # [(first, last)] sequence times the trigger is held (a story broadside)
                        opts = dict(fx[4]) if len(fx) > 4 else {}
                        fr_ = lambda t_: anchor + round((t_ - spec['key_t']) * fps_s)
                        if 'cease_t' in opts:
                            opts['cease'] = fr_(opts.pop('cease_t'))
                        if 'sure_t' in opts:     # a round must land then (the kill): laid dead on
                            opts['sure'] = fr_(opts.pop('sure_t'))
                        if 'flash_gap_s' in opts:     # scene_fx.tracer flash_gap, in frames
                            opts['flash_gap'] = round(opts.pop('flash_gap_s') * fps_s)
                        if 'bursts_t' in opts:
                            opts['bursts'] = [(fr_(t0_), fr_(t1_)) for t0_, t1_ in opts.pop('bursts_t')]
                        done = sfx_.tracer(fx[1], fx[2], fx[3] if len(fx) > 3 else None,
                                           muzzles=CLEAR_GUNS.get(fx[:3]) or [muzzles.get(fx[:3])], **opts)
                    elif kind == 'missiles':
                        try:         # a salvo on a ship that breaks arrives on her break_frame (the cook-off)
                            bf = int(placed[fx[2]]['fx'].get('break_frame'))
                        except (AttributeError, KeyError, TypeError):
                            bf = None
                        if len(fx) > 3:       # ('missiles', a, b, t): the salvo arrives at sequence time t
                            bf = anchor + round((fx[3] - spec.get('key_t', fx[3])) * fps_s)
                        # ('missiles', a, b, t, n, aim): n of its seekers take b's flares (thrown earlier in
                        # fx); aim, the point on b they seek (her frame, metres; default her centre)
                        done = sfx_.missiles(fx[1], fx[2], arrive=bf, decoys=fx[4] if len(fx) > 4 else 0,
                                             aim=fx[5] if len(fx) > 5 else (0, 0, 0))
                        if not done and len(fx) > 4:
                            print(f'ASHARU WARNING {kf} missiles {fx[1]} -> {fx[2]}: no launcher tubes on the '
                                  f'{fx[1]} build, so the flares of {fx[2]} have no seeker to answer', flush=True)
                    elif kind == 'blast_on':     # optional 5th: its sequence time (default 2 frames before the key);
                        # optional 6th 'facing': on the face of her hull toward the camera (scene_fx.blast_on)
                        done = sfx_.blast_on(fx[1], fx[2], fx[3], frame=anchor + round((fx[4] - spec['key_t']) * fps_s)
                                             if len(fx) > 4 else None, facing=len(fx) > 5 and fx[5] == 'facing')
                    elif kind == 'debris':       # optional 5th: the blast's time; its plates are 8 frames ahead of it
                        done = sfx_.debris(fx[1], fx[2], fx[3] if len(fx) > 3 else (0, 0, 0),
                                           frame=anchor + round((fx[4] - spec['key_t']) * fps_s) - 8
                                           if len(fx) > 4 else None)
                    elif kind == 'fire_on':
                        # optional 5th: how long before the key time it was lit (default 1.5 s)
                        done = sfx_.breach(fx[1], fx[2], fx[3] if len(fx) > 3 else (-1, .4, .3),
                                           fx[4] if len(fx) > 4 else 1.5, vent=True)
                    elif kind == 'smoke':
                        done = sfx_.breach(fx[1], fx[2], fx[3] if len(fx) > 3 else (-1, .2, .5), 5.0, vent=False)
                    elif kind == 'flares':
                        done = sfx_.flares(fx[1])
                    if done:
                        kit_drew.add(i)
                # the battle's damage so far, and this shot's hits as they land (asharu_damage's ledger)
                kt = spec.get('key_t', next((p_.t for p_ in spec['ships'].values() if hasattr(p_, 'at')), None))
                if kt is not None:
                    import asharu_damage
                    t0_, t1_ = spec['shot_t'] if 'span_t' in spec else (kt, kt)
                    fr_of_ = lambda t_, kt=kt: anchor + round((t_ - kt) * fps_s)
                    impacts_ = list(sfx_.impacts)
                    for fx in spec.get('fx', ()):    # a mining beam meets her hull the whole shot
                        if fx[0] == 'mining' and fx[2] in placed:
                            _e, loc_, _n, met_ = mining_hit(fx[1], fx[2], fx[3] if len(fx) > 3 else None)
                            if met_:
                                impacts_.append((fx[2], fr_of_(t0_), fr_of_(t1_),
                                                 tuple(placed[fx[2]]['matrix'].inverted() @ loc_), f'{fx[1]} beam'))
                    dmg_ = asharu_damage.apply_all(placed, t0_, t1_, fr_of_, log=lambda t: print(t, flush=True),
                                                   shot_fx=spec.get('fx'), impacts=impacts_)
                    if 'span_t' in spec:
                        rows_, fails_ = asharu_damage.link_check([e for r in dmg_.values() for e in r['events']],
                                                                 impacts_)
                        for row in rows_ + [f'LINK FAIL {f_}' for f_ in fails_]:
                            print(f'ASHARU damage {kf} {row}', flush=True)
                        if fails_ and not os.environ.get('ASHARU_AUDIT_SOFT'):
                            raise RuntimeError(f'{kf}: {len(fails_)} in-shot hits with no drawn cause, e.g. {fails_[:3]}')
                else:
                    print(f'ASHARU damage {kf}: no key time (no flown ship), ledger not applied', flush=True)
                try:
                    sfx_.projectile_check()
                finally:
                    for row in sfx_.rows:
                        print(f'ASHARU kit {kf} {row}', flush=True)
                if 'span_t' in spec and cfg.get('kit_once'):
                    import motion_audit      # every effect the kit made, moving as the camera will see it
                    rows_, fails_ = motion_audit.audit(sfx_, cam_view_of(spec))
                    for row in rows_:
                        print(f'ASHARU kit {kf} {row}', flush=True)
                    if fails_ and not os.environ.get('ASHARU_AUDIT_SOFT'):
                        raise RuntimeError(f'{kf}: the motion audit failed {len(fails_)} effects, e.g. {fails_[:4]}')
                if os.environ.get('ASHARU_DIAG_HIDE'):
                    # diagnosis only: which effect a picture shows, by rendering without it. The pattern
                    # must match a whole object name (fullmatch), never a substring: 'vent' once hid the
                    # Wolf's ventral docking lamps and a wreck's scar vents along with the vent it meant.
                    import swfx_core as swfx_core_
                    rx_ = re.compile(os.environ['ASHARU_DIAG_HIDE'])
                    hid_ = [o for o in bpy.data.objects if o.name.startswith('SWFX') and rx_.fullmatch(o.name)]
                    if not hid_:
                        raise RuntimeError(f"ASHARU_DIAG_HIDE {os.environ['ASHARU_DIAG_HIDE']!r} matches no whole object name")
                    for o in hid_:
                        fc_ = swfx_core_.find_fcurve(o, 'hide_render')
                        if fc_ is not None:
                            fc_.mute = True
                        o.hide_render = True
                    print(f'ASHARU DIAG hid {len(hid_)} objects matching {rx_.pattern!r}: '
                          f'{sorted({ship_fx.base_name(o.name)[:40] for o in hid_})[:30]}', flush=True)
                ship_fx.refresh(scene)
                bpy.context.view_layer.update()
            if build_at is not None:     # back to this frame's own poses, before anything else reads them
                for o in build_placed:
                    bpy.data.objects.remove(o)
                span_now['dt'] = spec['span_t'] - spec['key_t']
                for key, sp in spec['ships'].items():
                    place(key, sp, spec.get('states', {}).get(key))
                bpy.context.view_layer.update()
            if cfg.get('kit_once') and 'span_t' in spec:
                # The kit's drives follow this frame's throttle, not the one the kit was built at: under
                # --kit-once the build sets it once, at the shot's opening, and S3's pirates stayed dark
                # through the whole shot although each lights her drives inside it (audit, 2026-10-23).
                # So does everything else that changes over a shot and the build sets only once (state
                # audit, 2026-10-23): a pirate's lamps come on as she wakes, before her drives, and a raised
                # shield faces her attacker where he is now. Each value is checked against this plan once
                # the frame is set (ASHARU state below), so a key or driver that overrides it stops the run.
                state_plan_ = {}
                for key, pl in placed.items():
                    if pl['fx'] is None or pl['fx'].fx is None:
                        continue
                    if not pl['lib']['swfx']:
                        state_plan_[(key, 'thr_throttle')] = pl['throttle']
                    if key in LAUNCH:
                        try:
                            pl['fx'].get('lit_ship_master')
                        except KeyError:
                            print(f'ASHARU state {kf}: {key} has no ship lamps to wake', flush=True)
                        else:
                            wake_ = (spec['span_t'] - (LAUNCH[key] - LAMPS_LEAD_S)) / .3
                            if pl['fx'].keyed('lit_ship_master'):
                                # the kit keys them itself (S9b: the Falcon's shield overload stutters her
                                # lamps): those keys are the picture, and the plan would only contradict them
                                print(f'ASHARU state {kf}: {key} lamps keyed by the kit, not planned', flush=True)
                            else:
                                state_plan_[(key, 'lit_ship_master')] = (min(1.0, max(0.0, wake_))
                                                                         * spec.get('lamp_gain', {}).get(key, 1.0))
                for key, sh in spec.get('shields', {}).items():
                    if key in placed and sh.get('toward') in placed and placed[key]['fx'] is not None:
                        d_ = placed[key]['matrix'].inverted() @ placed[sh['toward']]['matrix'].translation
                        state_plan_[(key, 'shield_facing')] = math.degrees(math.atan2(d_.y, d_.x))
                for (key, knob), val in state_plan_.items():
                    placed[key]['fx'].set(knob, val)
                ship_fx.refresh(scene)
                bpy.context.view_layer.update()
                print(f"ASHARU state plan {kf}: "
                      f"{ {f'{k} {n}': round(v, 2) for (k, n), v in sorted(state_plan_.items())} }", flush=True)
            if not step:
                built_for[0] = span_of
            else:
                print(f'ASHARU kit {kf}: stepped (the {span_of} kit, built on the first frame of the run)', flush=True)
            if cfg.get('fx_check'):
                fx_report(kf)
                FX_AUDIT.append(fx_audit(list(ships_fx.values()), [lib.get('fx') for lib in library.values()]))
            for i, fx in enumerate(spec.get('fx', ())):
                if i in kit_drew:
                    continue
                kind = fx[0]
                if cfg.get('swfx') and kind in KIT_FX:
                    # no stand-in under the kit (owner, 2026-10-23, S7: a stand-in stream is a still
                    # picture of rounds, held for the length of a burst, that no projectile check sees):
                    # an effect the kit should draw and did not stops the render
                    raise RuntimeError(f'{kf}: the kit did not draw {fx}, and a kit render draws no stand-ins')
                if kind == 'tracer':
                    tracer(fx[1], fx[2], fx[3] if len(fx) > 3 else None, muzzle=muzzles.get(fx[:3]))
                elif kind == 'missiles':
                    missiles(fx[1], fx[2], muzzle=muzzles.get(fx[:3]))
                elif kind == 'blast':
                    blast(at(fx[1]), fx[2])
                elif kind == 'blast_on':
                    blast(at(fx[1], fx[3]), fx[2])
                elif kind == 'mining':
                    mining(fx[1], fx[2], fx[3] if len(fx) > 3 else None)
                elif kind == 'fire_on':     # optional 4th: the flame's direction in the hull frame (default aft and up)
                    d = fx[3] if len(fx) > 3 else (-1, .4, .3)
                    fire(at(fx[1], fx[2]), (placed[fx[1]]['matrix'].to_3x3() @ Vector(d)).normalized())
                elif kind == 'smoke':
                    d = fx[3] if len(fx) > 3 else (-1, .2, .5)
                    smoke(at(fx[1], fx[2]), (placed[fx[1]]['matrix'].to_3x3() @ Vector(d)).normalized())
                elif kind == 'debris':
                    debris(at(fx[1], fx[3]) if len(fx) > 3 else at(fx[1]), fx[2])
                elif kind == 'flares':
                    flares(fx[1])
                elif kind == 'entry_trails':
                    entry_trails(extra3d)
                elif kind == 'entry_sheath' and not cfg.get('motion_preview'):     # the preview has proxies
                    entry_sheath(spec['ships'][fx[1]], fx[1], spec.get('t', K6_T))
                elif kind == 'wreckfall' and not cfg.get('motion_preview'):
                    wreckfall(spec.get('t', K6_T))
            c = spec['camera']
            aim(c['loc'], c.get('target'), c['lens'], c.get('look'), c.get('roll', 0.0))
            if cfg.get('volume_audit'):       # in vacuum (as rendered), then as the kit's atmosphere default
                volume_audit(kf, 'vacuum')
                for s_ in ships_fx.values():
                    for n_ in ('smk_atmosphere', 'smk_dust'):
                        try:
                            s_.set(n_, 1.0)
                        except KeyError:
                            pass
                    for o_ in s_.ids:
                        if isinstance(o_, bpy.types.Object) and ship_fx.base_name(o_.name).startswith('SWFX smoke landing dust'):
                            o_.hide_render = False
                ship_fx.refresh(scene)
                volume_audit(kf, 'atmosphere')
                for s_ in ships_fx.values():
                    s_.vacuum()
                ship_fx.refresh(scene)
            if 'span_t' in spec and cfg.get('kit_once'):
                # each ship drawn this frame where the kit's planner has her (the rounds it fired leave
                # the muzzle there): her placement against her flight evaluated at this frame
                f_ = anchor + round((spec['span_t'] - spec['key_t']) * fps_s)
                m_at = ship_matrix_at(spec)
                worst = max((((placed[k_]['matrix'].translation - m_at(k_, f_).translation).length, k_)
                             for k_ in placed if hasattr(spec['ships'].get(k_), 'at')), default=(0.0, None))
                print(f'ASHARU placement {kf}: worst {worst[0] * 100:.2f} cm ({worst[1]}) from the planner', flush=True)
                if worst[0] > .03 and not os.environ.get('ASHARU_AUDIT_SOFT'):
                    raise RuntimeError(f'{kf}: {worst[1]} is drawn {worst[0]:.3f} m from where the kit planned her')
            if 'span_t' in spec:         # a motion span: the effects built at the key instant play on to this one
                scene.frame_set(anchor + round((spec['span_t'] - spec['key_t']) * fps_s))
                print(f"ASHARU span {kf}: scene frame {scene.frame_current} ({spec['span_t'] - spec['key_t']:+.3f} s "
                      f"from the key)", flush=True)
                if cfg.get('kit_once'):
                    # what the frame evaluates to, against this frame's plan: a value the build set once, or
                    # one a key or driver overrides, shows here (the S3 pirates' drives stayed dark unseen)
                    off_ = sorted(((abs(float(placed[k_]['fx'].get(n_)) - v_), k_, n_, v_)
                                   for (k_, n_), v_ in state_plan_.items()), reverse=True)
                    w_ = off_[0] if off_ else (0.0, None, None, None)
                    print(f'ASHARU state {kf}: {len(off_)} values, worst {w_[0]:.4f} ({w_[1]} {w_[2]})', flush=True)
                    if w_[0] > 1e-3 and not os.environ.get('ASHARU_AUDIT_SOFT'):
                        raise RuntimeError(f'{kf}: {w_[1]} {w_[2]} evaluates {w_[0]:.4f} off its plan {w_[3]:.4f}')
            lab = {}
            for key in spec['labels']:
                if key in extra3d:
                    lab[key] = project(extra3d[key])
                else:
                    lab[key] = ship_label(key)
            labels[kf] = lab
            if 'span_t' in spec:
                print(f'ASHARU on screen {kf}: {lab}', flush=True)
            if cfg.get('motion_preview'):     # --motion-preview: play this shot in Workbench (asharu_preview.py)
                import asharu_preview
                asharu_preview.shot(locals(), kf)
                continue
        else:
            labels[kf] = roster_sheet(kf)
        layers = bool(cfg.get('layers')) and kf in KEYFRAMES
        scene.render.film_transparent = layers
        if layers:
            plate = composite_layers.plate_for(cfg.get('plates'), kf)
            cached = raw / 'backdrop' / f'{backdrop_key(kf)}.exr'
            if not layered(kf, not cached.exists()) and not cfg.get('stage_only'):
                raise SystemExit(f'ASHARU layers {kf}: an object would render on the wrong layer (see the leak rows)')
        else:
            for vl in scene.view_layers:
                vl.use = vl == scene.view_layers[0]
        if cfg.get('stage_only'):                 # --stage-only: build and pose every hull, render nothing
            print('ASHARU staged', kf, flush=True)
            continue
        if cfg.get('hide_volumes'):      # --hide-volumes a,b | all: a timing control, the kit's volumes left out
            prefixes = cfg['hide_volumes'].split(',')
            gone = 0
            for s_ in ships_fx.values():
                for o_ in s_.ids:
                    if not isinstance(o_, bpy.types.Object) or o_.hide_render:
                        continue
                    vol = [sl.material for sl in o_.material_slots if sl.material and sl.material.use_nodes and any(
                        n.type == 'OUTPUT_MATERIAL' and n.inputs['Volume'].is_linked for n in sl.material.node_tree.nodes)]
                    if vol and ('all' in prefixes or any(ship_fx.base_name(o_.name).startswith(p) for p in prefixes)):
                        o_.hide_render = True
                        gone += 1
            print(f'ASHARU hid {gone} volume objects ({cfg["hide_volumes"]})', flush=True)
        if cfg.get('swfx'):     # no orbs (owner, 2026-10-21 and 2026-10-23): flag any round emissive effect
            if str(PROLOGUE / 'second-wind-vfx') not in sys.path:
                sys.path.insert(0, str(PROLOGUE / 'second-wind-vfx'))
            import swfx_lint
            # measured as at full size: the lint counts pixels at the render's own percentage
            orb_rows = swfx_lint.orbs(scene, min_px=4.0 * scene.render.resolution_percentage / 100.0)
            print(f'ASHARU lint {kf}: {len(orb_rows)} orbs' + ''.join(
                f'; {r_["object"]} {r_["px"]} px ratio {r_["ratio"]} ({r_["why"]})' for r_ in orb_rows[:4]), flush=True)
        partial = raw / f'{kf}.partial'           # not *.png, so a queue's frame count never counts it
        t_render = time.time()
        scene.render.use_file_extension = False
        st = scene.render.image_settings
        if layers:
            st.media_type = 'MULTI_LAYER_IMAGE'      # Blender 5: multilayer EXR is a media type
            st.color_depth, st.exr_codec = '16', 'ZIP'
            exr_partial = raw / f'{kf}.layers.partial'
            scene.render.filepath = str(exr_partial)
            bpy.ops.render.render(write_still=True)
            os.replace(exr_partial, raw / f'{kf}.layers.exr')
            st.media_type = 'IMAGE'
            st.file_format, st.color_mode, st.color_depth = 'PNG', 'RGBA', '8'
            composite_layers.compose_frame(raw / f'{kf}.layers.exr', partial, scene, plate=plate, backdrop=cached,
                                           log=lambda m: print(m, flush=True),
                                           glow=KEYFRAMES.get(kf, {}).get('bloom', 0.0))
            if cfg.get('swfx') and 'span_t' in KEYFRAMES.get(kf, {}):
                white_blob_lint(kf, composite_layers.read_png(partial), composite_layers.compose_frame.effects_alpha)
        else:
            st.media_type = 'IMAGE'
            st.file_format, st.color_mode, st.color_depth = 'PNG', 'RGBA', '8'
            scene.render.filepath = str(partial)
            bpy.ops.render.render(write_still=True)
        os.replace(partial, raw / f'{kf}.png')      # a PNG under its own name is always a finished one
        print(f'ASHARU render time {kf} {time.time() - t_render:.1f} s', flush=True)
        store = raw / 'labels.json'   # saved per frame: a stopped job keeps every finished frame's labels
        old = json.loads(store.read_text()) if store.exists() else {}
        store.write_text(json.dumps({**old, kf: labels[kf]}, indent=1))
        (raw / f'{kf}.meta.json').write_text(json.dumps(frame_meta(cfg['samples'], cfg.get('half', False),
                                                                      cfg.get('surface', SURFACE_DETAIL),
                                                                      layers and cfg.get('plates', None))))
        print('ASHARU rendered', kf, flush=True)
    if cfg.get('fx_check'):
        fx_verdict(out)


FX_ROWS = []     # --fx-check: one row per ship per frame, what its own copy evaluated to


FX_AUDIT = []    # --fx-check: each key frame's copies audited before the next frame rebuilds them


def fx_audit(ships, library_fx):
    """One key frame's copies: ship_fx.audit, invalid drivers, and which ships share a build."""
    import ship_fx
    builds = {}
    for s in ships:
        builds.setdefault(s.manifest['asset'] if 'asset' in s.manifest else s.manifest['append_collection'],
                          set()).add(s.key)
    return dict(audit=ship_fx.audit(ships, library_fx), builds=builds,
                invalid={s.key for s in ships for i in s.ids for fc in ship_fx._drivers(i) if not fc.driver.is_valid})


def fx_verdict(out):
    """--fx-check's verdict over every key frame staged: every ship's copy reaches only itself
    (ship_fx.audit), no driver is invalid, and on some frame two ships of one build read
    different throttle or firing values."""
    audit, builds, invalid = {}, {}, set()
    for a in FX_AUDIT:
        for k, v in a['audit'].items():
            old = audit.setdefault(k, dict(own=0, shared=0, foreign=[]))
            old['own'], old['shared'] = max(old['own'], v['own']), max(old['shared'], v['shared'])
            old['foreign'] += v['foreign']
        for b, keys in a['builds'].items():
            builds.setdefault(b, set()).update(keys)
        invalid |= a['invalid']
    invalid = sorted(invalid)
    ships = audit
    pairs = []
    for kf in dict.fromkeys(r['frame'] for r in FX_ROWS):
        rows = {r['ship']: r for r in FX_ROWS if r['frame'] == kf}
        for build, keys in builds.items():
            here = sorted(k for k in keys if k in rows)
            for i, a in enumerate(here):
                for b in here[i + 1:]:
                    ra, rb = rows[a], rows[b]
                    diff = [n for n in ('thr_throttle', 'plume', 'firing', 'flash_W', 'gear_down', 'grapples')
                            if n in ra and ra.get(n) != rb.get(n)]
                    if diff:
                        pairs.append(dict(frame=kf, build=build, ships=[a, b], differ=diff,
                                          a={n: ra.get(n) for n in diff}, b={n: rb.get(n) for n in diff}))
    foreign = {k: v['foreign'] for k, v in audit.items() if v['foreign']}
    ok = not foreign and not invalid and bool(pairs)
    report = dict(ok=ok, ships=len(ships), audit={k: dict(own=v['own'], shared=v['shared'], foreign=v['foreign'])
                                                  for k, v in audit.items()},
                  invalid_drivers=invalid, independent_pairs=pairs, rows=FX_ROWS)
    (out / 'fx-check.json').write_text(json.dumps(report, indent=1))
    for k, v in audit.items():
        print(f"ASHARU fx audit {k:12} own {v['own']:5}  shared {v['shared']:4}  foreign {len(v['foreign'])}", flush=True)
    for pr in pairs:
        print(f"ASHARU fx independent {pr['frame']}: {pr['ships'][0]} {pr['a']}  vs  {pr['ships'][1]} {pr['b']}",
              flush=True)
    print('ASHARU fx-check', 'PASS' if ok else 'FAIL', out / 'fx-check.json', flush=True)
    if not ok:
        raise SystemExit(1)


# ======================================================================================= driver

def frame_meta(samples, half=False, surface=SURFACE_DETAIL, layers=False):
    """What a finished frame was rendered with: its samples, the staging source, and for a layered frame
    (layers: the --plates value, None for the plates beside the script, '' for none) the plates."""
    import hashlib
    h = hashlib.sha256()
    for f in (HERE / 'battle_blockout.py', HERE / 'roster.py', HERE / 'composite_layers.py'):
        h.update(f.read_bytes())
    meta = {'samples': samples, 'half': half, 'source_sha256': h.hexdigest()}
    if layers is not False:
        meta['layers'] = True
        root = composite_layers_plates() if layers is None else (Path(layers) if layers else None)
        if root is not None and root.exists():
            p = hashlib.sha256()
            for f in sorted(x for x in root.rglob('*') if x.is_file() and x.suffix.lower() in ('.png', '.exr')):
                p.update(str(f.relative_to(root)).encode())
                p.update(f.read_bytes())
            meta['plates_sha256'] = p.hexdigest()
    if surface:
        s = hashlib.sha256()
        for f in [HERE / 'surface' / 'surface_detail.py', *sorted((HERE / 'surface' / 'raw').glob('*.png'))]:
            s.update(f.read_bytes())
        meta['surface_sha256'] = s.hexdigest()
    return meta


def composite_layers_plates():
    return REPO / 'build' / 'asharu-battle' / 'plates'     # composite_layers.PLATES


def finished(raw, kf, samples, half=False, surface=SURFACE_DETAIL, layers=False):
    """Whether raw/<kf>.png is a finished frame of this staging at these samples."""
    png, meta, store = raw / f'{kf}.png', raw / f'{kf}.meta.json', raw / 'labels.json'
    try:
        if json.loads(meta.read_text()) != frame_meta(samples, half, surface, layers) or kf not in json.loads(store.read_text()):
            return False
        with open(png, 'rb') as fh:
            fh.seek(-12, 2)
            return fh.read(12)[4:8] == b'IEND'
    except (OSError, ValueError):
        return False


def git_commit():
    try:
        return subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=REPO, text=True).strip()
    except Exception:
        return 'unknown'


def label_frames(run):
    from PIL import Image, ImageDraw
    from review_font import review_font
    labels = json.loads((run / 'raw' / 'labels.json').read_text())
    f_title, f_cap, f_lab = review_font(30, True), review_font(21), review_font(19, True)
    names = {m['key']: m['name'] for m in roster.MERCHANT + roster.PIRATE + roster.REFERENCES + roster.DRONES}
    names.update({'platform': 'Asharu platform (derelict)', 'trail-colossus': 'Colossus', 'trail-morrow': 'Morrow'})
    pirates = {m['key'] for m in roster.PIRATE}
    order = [k for k in KEYFRAMES] + list(ROSTER_SHEETS)
    outputs = []
    for i, kf in enumerate([k for k in order if (run / 'raw' / f'{k}.png').exists()]):
        im = Image.open(run / 'raw' / f'{kf}.png').convert('RGB')
        if im.size != (W, H):       # a --half light check: labels are placed for the full frame
            im = im.resize((W, H), Image.LANCZOS)
        dr = ImageDraw.Draw(im, 'RGBA')
        lab = labels.get(kf, {})
        clean = kf in KEYFRAMES and KEYFRAMES[kf].get('clean')
        if kf in KEYFRAMES:
            spec = KEYFRAMES[kf]
            title = f"{spec['shot']}  {spec['title']}"
            cap = spec['caption']
            boxes = []
            for key, p in sorted(lab.items(), key=lambda kv: (kv[1] or [0, 0])[1]):
                if not p or clean:
                    continue
                col = (255, 140, 110, 255) if key in pirates else (150, 210, 255, 255)
                txt = names.get(key, key)
                tw = dr.textlength(txt, font=f_lab)
                x, y = min(max(p[0] - tw / 2, 8), W - tw - 8), min(max(p[1], 70), H - 120)
                while any(x < bx1 and x + tw > bx0 and y < by1 and y + 26 > by0 for bx0, by0, bx1, by1 in boxes):
                    y += 28
                boxes.append((x - 5, y - 2, x + tw + 5, y + 24))
                dr.rectangle((x - 5, y - 2, x + tw + 5, y + 24), fill=(0, 0, 0, 140))
                dr.text((x, y), txt, font=f_lab, fill=col)
        else:
            mpp = lab.pop('_metres_per_px')
            title = 'Fleet roster from above' if kf == 'r1' else 'Fleet roster broadside'
            cap = ('Every hull at true relative size. Blue: merchant convoy. Red: pirates. Grey: known ships for scale. '
                   'Sprite-painted slabs are stand-ins at option C size; the rest are the finished models.')
            for key, v in lab.items():
                if not v['at']:
                    continue
                col = ((255, 140, 110, 255) if v['row'] == 1 else (150, 210, 255, 255) if v['row'] == 0
                       else (200, 200, 200, 255))
                for j, txt in enumerate((v['name'], f"{v['length_m']:.0f} m" +
                                         ('' if v['basis'].startswith('finished') else '  stand-in'))):
                    tw = dr.textlength(txt, font=f_lab)
                    dr.text((v['at'][0] - tw / 2, v['at'][1] + j * 22), txt, font=f_lab, fill=col)
            bar = 100 / mpp
            dr.rectangle((60, H - 150, 60 + bar, H - 142), fill=(230, 230, 230, 255))
            dr.text((60, H - 138), '100 m', font=f_lab, fill=(230, 230, 230, 255))
        if not clean:   # a shot that sells on its effects carries nothing over them (no id burn-in either)
            dr.rectangle((0, 0, W, 58), fill=(0, 0, 0, 170))
            dr.text((22, 12), title, font=f_title, fill=(255, 255, 255, 255))
            dr.rectangle((0, H - 92, W, H), fill=(0, 0, 0, 170))
            y = H - 82
            for line in wrap(dr, cap, f_cap, W - 44):
                dr.text((22, y), line, font=f_cap, fill=(230, 230, 230, 255))
                y += 27
        name = f"{order.index(kf) + 2:02d}-{kf}-{slug(title)}.png"   # by shot order: two jobs' halves interleave
        im.save(run / name)
        outputs.append(name)
    return outputs


def slug(s):
    import re
    return re.sub(r'[^a-z0-9]+', '-', s.lower()).strip('-')[:40]


def wrap(dr, text, font, width):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if dr.textlength(t, font=font) > width and cur:
            lines.append(cur)
            cur = w
        else:
            cur = t
    return lines + [cur]


def orbit_diagram(run):
    """01: where the battle happens. Main picture: Asharu's limb, the convoy's orbit, the battle and
    the fall to the northern canyons (altitude exaggerated). Inset: the battle itself side-on at
    kilometre scale, from KEYFRAMES k1 and k2."""
    from PIL import Image, ImageDraw
    from review_font import review_font
    im = Image.new('RGB', (W, H), (8, 9, 14))
    dr = ImageDraw.Draw(im, 'RGBA')
    ft, fl, fs = review_font(34, True), review_font(22, True), review_font(20)
    EX = 2.2                                  # altitude exaggeration
    KM_PX = 1.6                               # km per pixel
    x0, ysurf = 260, 880                      # the battle's ground point on screen

    def P(downrange_km, alt_km):
        """Screen point: downrange along the orbit (north-east to the right), altitude up."""
        th = downrange_km / (R / 1e3)
        rr = R / 1e3 + alt_km * EX
        x = x0 + rr * math.sin(th) / KM_PX
        y = ysurf + (R / 1e3 - rr * math.cos(th)) / KM_PX
        return x, y

    ground = [P(d, 0) for d in range(-600, 2800, 20)]
    dr.polygon(ground + [(W, H), (0, H)], fill=(150, 116, 80))
    dr.line(ground, fill=(196, 160, 116), width=3)
    dr.line([P(d, 14) for d in range(-600, 2800, 20)], fill=(200, 190, 170, 90), width=2)
    orbit = [P(d, ALT / 1e3) for d in range(-600, 2800, 20)]
    dr.line(orbit, fill=(120, 170, 230), width=3)
    fall = [P(d, ALT / 1e3 * (1 - (d / 1450) ** 1.25)) for d in range(0, 1451, 10)]
    dr.line(fall, fill=(255, 180, 110), width=5)
    glow = [P(d, ALT / 1e3 * (1 - (d / 1450) ** 1.25)) for d in range(650, 1451, 10)]
    dr.line(glow, fill=(255, 225, 170), width=9)
    bx, by = P(0, ALT / 1e3)
    dr.ellipse((bx - 11, by - 11, bx + 11, by + 11), outline=(255, 255, 255), width=3)
    cx, cy = P(1450, 0)
    dr.polygon([(cx - 70, cy + 4), (cx - 40, cy - 38), (cx - 18, cy - 14), (cx + 10, cy - 52),
                (cx + 34, cy - 20), (cx + 64, cy + 4)], fill=(105, 80, 58))

    def label(xy, txt, col, dx=0, dy=-34):
        tw = dr.textlength(txt, font=fs)
        dr.text((min(max(xy[0] + dx - tw / 2, 12), W - tw - 12), xy[1] + dy), txt, font=fs, fill=col)

    label((bx, by), 'the battle: low orbit, 250 km up', (255, 255, 255), dx=40, dy=-44)
    label(P(1700, ALT / 1e3), 'convoy orbit, prograde to the north-east', (120, 170, 230), dx=-60, dy=14)
    label(P(420, 180), 'Colossus and Morrow lose their orbit', (255, 180, 110), dx=-200, dy=14)
    label(P(1000, 95), 'entry: plasma trails (S12)', (255, 225, 170), dx=170, dy=-10)
    label((cx, cy), "northern canyon wall: Basin's valley (the crash: a later shot)", (240, 220, 190), dx=-60, dy=22)
    dr.text((22, 18), 'Orbital geometry: where the battle is fought', font=ft, fill=(255, 255, 255))
    for j, line in enumerate((
            f'Asharu taken as {R / 1e6:.0f},000 km radius, convoy at {ALT / 1e3:.0f} km (altitude drawn {EX:.1f}x). '
            'Orbit about 6 km/s; the desert slides past beneath at about 1.3 degrees a second.',
            'From 250 km the horizon sits 17.6 degrees below level: wide shots tilt down to put the limb high in '
            'frame; looking down, the desert fills the frame behind the ships.',
            'Sun low (16 degrees up), ahead and to the south-east: long shadows across the dunes, ships side-lit. '
            'The fall runs about 1,500 km downrange.')):
        dr.text((22, 64 + j * 28), line, font=fs, fill=(210, 210, 210))
    # Inset: the battle side-on, km scale, from the k1/k2 placements.
    ix0, iy0, iw, ih = W - 820, 170, 790, 430
    dr.rectangle((ix0, iy0, ix0 + iw, iy0 + ih), fill=(14, 16, 24), outline=(90, 96, 110), width=2)
    dr.text((ix0 + 16, iy0 + 10), 'Inset: the ambush, side-on (1 km grid)', font=fl, fill=(230, 230, 230))
    sx, sz = 50.0, 50.0                        # px per km

    def Q(xm, zm):
        return ix0 + iw - 140 + xm / 1e3 * sx, iy0 + 120 - zm / 1e3 * sz

    for k in range(-12, 3):
        x, _ = Q(k * 1e3, 0)
        dr.line((x, iy0 + 46, x, iy0 + ih - 10), fill=(40, 44, 56))
    for k in range(-6, 2):
        _, y = Q(0, k * 1e3)
        if iy0 + 46 < y < iy0 + ih:
            dr.line((ix0 + 4, y, ix0 + iw - 4, y), fill=(40, 44, 56))
    plat = KEYFRAMES['k2']['platform']
    px, py = Q(plat[0], plat[2])
    dr.rectangle((px - 18, py - 7, px + 18, py + 7), fill=(150, 140, 128))
    label((px, py), 'derelict platform', (200, 190, 175), dy=14)
    for key, sp in KEYFRAMES['k2']['ships'].items():
        if key in CONVOY:
            continue
        x, y = Q(sp[0], sp[2])
        dr.ellipse((x - 4, y - 4, x + 4, y + 4), fill=(230, 120, 90))
    for key, sp in CONVOY.items():
        x, y = Q(sp[0], sp[2])
        dr.ellipse((x - 5, y - 5, x + 5, y + 5), fill=(120, 170, 230))
    a, b = Q(-8.5e3, -3.6e3), Q(-1.8e3, -.4e3)
    dr.line((a, b), fill=(230, 120, 90), width=3)
    dr.polygon([(b[0], b[1]), (b[0] - 16, b[1] - 2), (b[0] - 10, b[1] + 13)], fill=(230, 120, 90))
    label(Q(0, 0), 'convoy column', (120, 170, 230), dx=0, dy=-38)
    label(Q(-5.2e3, -2.9e3), 'pirates climb out and close', (230, 120, 90), dx=60, dy=40)
    gx, gy = Q(1.5e3, -4.6e3)
    dr.line((gx - sx, gy, gx, gy), fill=(230, 230, 230), width=3)
    dr.text((gx - sx, gy + 6), '1 km', font=fs, fill=(230, 230, 230))
    dr.text((ix0 + 16, iy0 + ih - 34), 'prograde to the right; the platform sits about 4 km lower and 10 km astern',
            font=fs, fill=(170, 170, 180))
    name = '01-orbit-geometry.png'
    im.save(run / name)
    return name


def check(frames):
    """Where each ship lands in each key frame, without rendering: centre pixel, length in
    pixels, and whether it is in frame. The camera math is Blender's track ('-Z', 'Y')."""
    lengths = {m['key']: roster.size_row(m)['length_m'] for m in roster.MERCHANT + roster.PIRATE + roster.DRONES}
    for kf in frames:
        if kf not in SHOTS:
            continue
        c = SHOTS[kf]['camera']
        loc = c['loc']
        f = [b - a for a, b in zip(loc, c['target'])] if c.get('target') else list(c['look'])
        n = math.sqrt(sum(x * x for x in f))
        f = [x / n for x in f]
        r = [f[1], -f[0], 0.0]
        n = math.sqrt(sum(x * x for x in r))
        r = [x / n for x in r]
        u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
        fpx = c['lens'] / 36.0 * W
        pitch = math.degrees(math.asin(f[2]))
        dip = math.degrees(math.acos(R / (R + ALT + loc[2])))
        hy = H / 2 - fpx * math.tan(math.radians(-dip - pitch))
        print(f'{kf}: pitch {pitch:.1f}, horizon at y={hy:.0f}' + ('' if 0 <= hy <= H else ' (out of frame)'))
        for key, sp in SHOTS[kf]['ships'].items():
            d = [b - a for a, b in zip(loc, sp[:3])]
            z = sum(a * b for a, b in zip(d, f))
            if z <= 0:
                print(f'   {key:12} behind camera')
                continue
            x = W / 2 + fpx * sum(a * b for a, b in zip(d, r)) / z
            y = H / 2 - fpx * sum(a * b for a, b in zip(d, u)) / z
            inside = 0 <= x <= W and 60 <= y <= H - 90
            print(f'   {key:12} ({x:6.0f}, {y:6.0f})  {lengths[key] / z * fpx:5.0f} px  {z:6.0f} m'
                  + ('' if inside else '   OUT'))
        for kind, a, b, best, tgt_el in bearing(kf):
            if best is None:
                verdict = 'no guns known'
            elif best[0]:
                verdict = f'bears ({best[2]}, el {best[3]:+.0f}, {best[4]:+.0f} off rest)'
            else:
                verdict = f'CANNOT BEAR: target {tgt_el:+.0f} deg off her deck plane, best {best[2]} short by {best[1]:.0f} deg'
            print(f'   {kind:8} {a:12} -> {b:12} {verdict}')
        for line in cue_lines(kf):
            print('   ' + line)
        for line in surface_lines(loc, f, r, u, fpx):
            print('   ' + line)
        for line in turn_lines(kf):
            print('   ' + line)


TURN_JUMP_LIMIT = 45.0     # deg/s of angular-velocity change in one frame (1/24 s) that --check reports


def station_boxes():
    """The station's parts as boxes in its own metres (a drum as its bounding box), for hide_lines."""
    out = []
    for kind, _, c, size, (hd, pt, rl) in platform_parts():
        half = size if kind == 'box' else (size[1], size[0], size[0])
        out.append((c, half, debris_field.rot(hd, pt, rl)))
    return out


def rejoin_offsets():
    """{pirate: (t, offset)}: where each pirate is first seen after S4 (the start of the first shot
    from 44 s on that has her), and her launch's offset from that shot's flight there: REJOIN's values."""
    starts = sorted((spec['camera']['at'].keys[0]['t'], k) for k, spec in SHOTS.items()
                    if 'at' in spec['camera'] and spec.get('ships'))
    out = {}
    for p, f in PIRATE_LAUNCH.items():
        nxt = [(t, k) for t, k in starts if t >= 44.0 - 1e-6 and p in SHOTS[k]['ships']]
        if nxt:
            t, k = nxt[0]
            tgt, here = SHOTS[k]['ships'][p], f.at(t)
            tgt = tgt.at(t) if hasattr(tgt, 'at') else tgt
            out[p] = (t, tuple(round(tgt[i] - here[i], 1) for i in range(3)))
    return out


def rejoin_lines():
    """--check's cut report: each pirate's jump into her next shot, and how much faster than her
    launch the rejoin flies her (REJOIN STALE when a flight moved and REJOIN wants re-measuring)."""
    out = ['rejoin (each pirate onto her next shot after S4)']
    fresh = rejoin_offsets()
    for p, (t, d) in fresh.items():
        stale = math.dist(d, REJOIN.get(p, (0, (0, 0, 0)))[1]) > 25.0
        ta = rejoin_start(LAUNCH[p], t)
        extra = math.dist(REJOIN[p][1], (0, 0, 0)) / (t - ta) if p in REJOIN else 0.0
        jump = math.dist(d, REJOIN[p][1]) if p in REJOIN else math.dist(d, (0, 0, 0))
        out.append('   %-12s at %5.1f s  jump %5.0f m  rejoin %5.0f m over %4.1f s (+%3.0f m/s)%s' % (
            p, t, jump, math.dist(d, (0, 0, 0)), t - ta, extra, '  REJOIN STALE: %s' % (d,) if stale else ''))
    return out


def kit_lines():
    """--check's wreck-kit report: whether the kit's station still matches platform_parts(), which
    hulks the field's boxes draw, the worst stretch of a kit wreck into its box, and whether the
    pinned build is here for a lit render."""
    from collections import Counter
    picks = [(pc.name, j, wreck_kit.pick(half, i * 31 + j)) for i, pc in enumerate(FIELD)
             for j, (_, half, _) in enumerate(pc.boxes)]
    fam = Counter(p.family for _, _, p in picks)
    worst = max(picks, key=lambda x: max(abs(math.log(f)) for f in x[2].fill))
    path = BUILDS / roster.WRECKS['dir'] / roster.WRECKS['file']
    out = ['wreck kit (%s)' % roster.WRECKS['drive'],
           '   station %s' % ('matches platform_parts()' if wreck_kit.station_current(platform_parts())
                              else 'STALE: built from another platform_parts(), rebuild the kit'),
           '   %d boxes: %s' % (len(picks), ', '.join('%s %d' % kv for kv in sorted(fam.items()))),
           '   hulks: ' + ', '.join('%s %s' % (n, p.name) for n, _, p in picks if p.family == 'hulk'),
           '   worst stretch %s box %d: %s (fill %s)' % (worst[0], worst[1], worst[2].name, worst[2].fill),
           '   build %s' % ('here' if path.exists() else 'not here (a lit render needs it: %s)' % path)]
    return out


def hide_lines():
    """--check's hiding report (owner, 2026-10-22): per hidden pirate, what she hides behind and how
    big it is against her own bounding box, how much of her the convoy can see before the drives
    light, and whether her launch, eased out and turned onto her climb, passes through any wreck or
    the station. Flags: EXPOSED (under half hidden and not meant to be seen), SMALL (her cover is less
    than her own volume), HALF-SEEN (meant to be glimpsed: a nose or drives past the cover), and HIT
    (her launch path enters a box)."""
    t_dark = min(LAUNCH.values()) - .3
    station = station_boxes()
    boxes = station + [b for pc in FIELD for b in pc.boxes_at(t_dark)]
    out = ['hiding (before the drives light, %.1f s; convoy line of sight %s)' % (
        t_dark, ', '.join('%.2f' % v for v in TOWARD_CONVOY))]
    for k, h in HIDES.items():
        L = SHIP_LENGTH[k]
        ship = (*h['at'], h['hd'], h['pt'], h['rl'])
        vol = 8 * L ** 3 * debris_field.SHIP_HALF[0] * debris_field.SHIP_HALF[1] * debris_field.SHIP_HALF[2]
        cover = next((c for c in FIELD_COVER if c.cover_for == k), None)
        cv = cover.volume() if cover else None
        seen = 1 - debris_field.coverage(ship, L, TOWARD_CONVOY, boxes)
        flags = []
        if h.get('peek'):
            flags.append('HALF-SEEN (intended: %s shows)' % ('her nose' if h['peek'] > 0 else 'her drives'))
        elif seen > .5:
            flags.append('EXPOSED')
        if cover is not None and cv < vol:
            flags.append('SMALL')
        hits = set()
        f = PIRATE_FLIGHT[k]
        for i in range(0, 49):
            t = LAUNCH[k] + i * .25
            q = f.at(t)
            lp = to_platform(q[:3])
            local = (*lp, q[3] - PLATFORM_HD, q[4], q[5])
            for pt_ in debris_field.ship_points(local, L * .92, 3):
                for n, (c, half, r) in enumerate(station + [b for pc in FIELD for b in pc.boxes_at(t)]):
                    rel = tuple(pt_[j] - c[j] for j in range(3))
                    if all(abs(sum(rel[j] * r[j][a] for j in range(3))) < half[a] for a in range(3)):
                        hits.add('%.1f s %s' % (t, 'the station' if n < len(station) else 'a wreck'))
                        break
        if hits:
            flags.append('HIT ' + ', '.join(sorted(hits)[:3]))
        what = ('the station' if cover is None else '%s %.0f%% of her volume' % (
            HIDES[k]['cover'], 100 * cv / vol))
        out.append('   %-12s %5.0f m  behind %-24s  seen %3.0f%%  %s' % (k, L, what, 100 * seen, '  '.join(flags)))
    return out


def turn_lines(kf):
    """--check's motion sweep: every flown ship over her shot at 24 fps, listing frames where her
    angular velocity jumps by more than TURN_JUMP_LIMIT (a turn that stalls or snaps). A hit's
    tumble or a boat setting down is expected there and is marked as such."""
    import asharu_preview
    import motion
    seg = next((s for s in asharu_preview.segments(SHOTS) if s['kf'] == kf), None)
    if seg is None:
        return []
    out = []
    for key, sp in SHOTS[kf]['ships'].items():
        if not hasattr(sp, 'at'):
            continue
        hits = [h['t'] for h in getattr(sp.at, 'hits', ()) if 'kick' not in h]
        for t, j in motion.turn_jumps(sp.at, seg['start'], seg['end'], limit=TURN_JUMP_LIMIT):
            why = 'a hit' if any(abs(t - th) < .1 for th in hits) else ('setting down' if key.startswith('hermes') else 'CHECK')
            out.append(f'turn     {key:12} angular velocity jumps {j:.0f} deg/s at {t:.2f} s ({why})')
    return out


def surface_lines(loc, f, r, u, fpx):
    """--check's view of the ground: the ground metres a pixel spans at frame centre and which
    detail tile paints it, and where each settlement lands in frame."""
    sys.path.insert(0, str(HERE / 'surface'))
    import surface_detail as sd
    if not SURFACE_DETAIL:
        return ['surface: base map only (SURFACE_DETAIL off)']
    c = (0.0, 0.0, -(R + ALT))
    o = [a - b for a, b in zip(loc, c)]

    def hit(px, py):
        d = [f[i] + ((px - W / 2) * r[i] - (py - H / 2) * u[i]) / fpx for i in range(3)]
        n = math.sqrt(sum(x * x for x in d))
        d = [x / n for x in d]
        b = sum(a * b for a, b in zip(o, d))
        disc = b * b - (sum(x * x for x in o) - R * R)
        if disc < 0:
            return None
        t = -b - math.sqrt(disc)
        return [o[i] + t * d[i] for i in range(3)], t

    out = []
    rows = [('centre', H / 2), ('lower edge', H - 1)]
    top = next((py for py in range(0, H, 10) if hit(W / 2, py)), None)
    if top is not None and top > 0:
        rows.insert(0, ('below the horizon', min(H - 1, top + 30)))
    for label, py in rows:
        h = hit(W / 2, py)
        if h is None:
            out.append(f'surface {label}: sky')
            continue
        p, t = h
        mpp = t / fpx
        tiles = [n for n, (cx, cy, side, k) in sd.TILES.items()
                 if abs(p[0] - cx) < side / 2 and abs(p[1] - cy) < side / 2]
        top = tiles[-1] if tiles else None
        tile = f'{top} tile at {sd.TILES[top][2] / sd.TILES[top][3]:.0f} m/px' if top else 'base map only'
        out.append(f'surface {label}: ground {t / 1e3:.0f} km away, {mpp:.0f} m/px across; {tile}')
    for st in sd.SETTLEMENTS:
        if st[1] == 'road':
            continue
        x, y = st[2], st[3]
        z = math.sqrt(R * R - x * x - y * y)
        d = [x - o[0], y - o[1], z - o[2]]
        zc = sum(a * b for a, b in zip(d, f))
        if zc <= 0:
            continue
        sx = W / 2 + fpx * sum(a * b for a, b in zip(d, r)) / zc
        sy = H / 2 - fpx * sum(a * b for a, b in zip(d, u)) / zc
        if 0 <= sx <= W and 0 <= sy <= H and hit(sx, sy) and abs(hit(sx, sy)[1] - math.sqrt(sum(v * v for v in d))) < 5e3:
            out.append(f'settlement {st[1]:5} ({st[0]}) at ({sx:5.0f}, {sy:5.0f}), {2 * st[4] / zc * fpx:4.0f} px across')
    return out


def cue_lines(kf):
    """--check's view of a frame's effects cues: what each ship's own SWFX copy is set to (under
    --swfx) beside the other ships of its build, and anything a cue names that is not there."""
    spec = SHOTS[kf]
    cues = spec.get('cues', {})
    if not cues:
        return []
    out = []
    by_build = {}
    for key, sp in spec['ships'].items():
        m = next((m for m in roster.MERCHANT + roster.PIRATE if m['key'] == key), {})
        build = spec.get('states', {}).get(key) or m.get('finished')
        if build:
            by_build.setdefault(build, []).append(key)
    for key, cue in cues.items():
        if key not in spec['ships']:
            out.append(f'cue      {key:12} NOT IN FRAME')
            continue
    for build, keys in by_build.items():
        if not any(k in cues for k in keys):
            continue
        for k in keys:
            cue = cues.get(k, {})
            fire = cue.get('fire', [])
            bad = []
            if fire != 'all' and fire:
                try:
                    have = set(gun_labels(build))
                    bad = [lab for lab in fire if have and lab not in have]
                except OSError:
                    pass
            knobs = {n: v for n, v in cue.items() if n != 'fire'}
            out.append(f"cue      {k:12} [{build}] throttle {spec['ships'][k][6]:.2f}  fire {fire or '-'}"
                       + (f'  knobs {knobs}' if knobs else '') + (f'  UNKNOWN GUNS {bad}' if bad else ''))
    return out


def gun_labels(build):
    """'t<turret>_<k>' for the guns a build's manifest lists (a list-form effects.anchors.guns)."""
    rows = ((build_manifest(roster.FINISHED[build]).get('effects') or {}).get('anchors') or {}).get('guns')
    return [f"t{g['turret']}_{g.get('barrel', 1)}" for g in rows if g.get('kind', 'gun') != 'launcher'] \
        if isinstance(rows, list) else []


GUN_ELEVATION = (-10.0, 85.0)   # a dorsal turret's reach above (and just below) its own deck plane
MISSILE_CONE = 90.0              # a guided missile can leave a fixed tube up to this far off its target


def _rot(hd, pt, rl):
    """Local-to-world rotation of a ship spec, the same order as frame_of: Rz(hd) Ry(-pt) Rx(rl)."""
    def rz(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

    def ry(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

    def rx(a):
        c, s = math.cos(a), math.sin(a)
        return [[1, 0, 0], [0, c, -s], [0, s, c]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(mul(rz(math.radians(hd)), ry(-math.radians(pt))), rx(math.radians(rl)))


def muzzle_of(t, mpu, mount_m):
    """Where a mount's shot leaves, from the build's current muzzle data: ('m', metres in the ship's
    frame), or ('anchor', name) when the manifest names the anchor without placing it (the anchor
    is then found in the appended build). Builds write it differently: muzzle_locations (units),
    muzzles / muzzle entries with an 'at' (units), or muzzles as anchor names."""
    locs = t.get('muzzle_locations')
    if isinstance(locs, list) and locs and isinstance(locs[0], list):
        return 'm', [v * mpu for v in locs[0]]
    named = [t.get(k) for k in ('muzzles', 'muzzle_anchors') if isinstance(t.get(k), list)]
    for m in sum(named, []) + [t.get('muzzle')]:
        if isinstance(m, dict) and isinstance(m.get('at'), list):
            return 'm', [v * mpu for v in m['at']]
        if isinstance(m, dict) and m.get('anchor'):
            return 'anchor', m['anchor']
        if isinstance(m, str):
            return 'anchor', m
    return 'm', mount_m


def mounts(key, state=None):
    """A ship's guns in its own frame, metres: (position, rest bearing, arc, kind). Finished hulls
    from their manifests (dorsal turrets that train in azimuth, fixed hardpoints); stand-ins from
    the game's weapon slots, laid on the slab's deck."""
    m = next(r for r in roster.MERCHANT + roster.PIRATE if r['key'] == key)
    fin = state or m.get('finished')
    out = []
    if fin:
        f = roster.FINISHED[fin]
        man = build_manifest(f)
        mpu = man['metres_per_unit']
        rows = list(man.get('turrets') or []) + list(man.get('launchers') or [])   # some builds list launchers apart
        for w in man.get('weapon_slots', []) + man.get('weapons', []):
            rows.append(w)
        for t in rows if isinstance(rows, list) else []:
            at = t.get('pivot_at') or t.get('location') or t.get('at') or (t.get('pivot') or {}).get('at')
            if at is None:
                mz = (t.get('muzzles') or [{}])[0]
                at = mz.get('at') if isinstance(mz, dict) else None
            if at is None:
                continue
            rest = t.get('rest_angle_deg', t.get('angle_deg', t.get('angle', 0)))
            arc = t.get('arc_deg', t.get('arc', 360))
            kind = 'hardpoint' if t.get('mount') == 'HARDPOINT' else 'turret'
            pos = [v * mpu for v in at] + [0] * (3 - len(at))
            launcher = t.get('kind') == 'launcher' or 'missile' in str(t.get('type', '')).lower()
            out.append((pos, rest, arc, kind, muzzle_of(t, mpu, pos), launcher))
    else:
        spec = roster.game_spec(m['game'])
        _, length_px, _ = roster.sprite_stats(spec['sprite'])
        mpu = roster.size_row(m)['length_m'] / length_px
        for w in spec['weapons']:
            pos = [w['location'][0] * mpu, w['location'][1] * mpu, 0.0]
            out.append((pos, w['angle'], w['arc'], 'hardpoint' if w['mount'] == 'HARDPOINT' else 'turret',
                        ('m', pos), w.get('type') in ('MISSILE', 'COMPOSITE', 'SYNERGY', 'UNIVERSAL')))
    return out


def bearing(kf, ships=None):
    """For every shot in a key frame, whether the shooter has a gun that can bear on its target, with
    the ships posed as `ships` (default the key frame's own)."""
    spec = SHOTS[kf]
    ships = ships or spec['ships']
    states = spec.get('states', {})
    rows = []
    for fx in spec.get('fx', []):
        if fx[0] not in ('tracer', 'missiles') or fx[1] not in ships or fx[2] not in ships:
            continue
        a, b = fx[1], fx[2]
        sa, sb = ships[a], ships[b]
        R = _rot(*sa[3:6])
        d = [sb[i] - sa[i] for i in range(3)]
        loc = [sum(R[j][i] * d[j] for j in range(3)) for i in range(3)]     # world -> shooter frame
        best, every = None, []
        for pos, rest, arc, kind, muzzle, launcher in mounts(a, states.get(a)):
            fits = launcher == (fx[0] == 'missiles')      # missiles from a launcher, tracers from a gun, when one bears
            v = [loc[i] - pos[i] for i in range(3)]
            h = math.hypot(v[0], v[1])
            el = math.degrees(math.atan2(v[2], h))
            az = math.degrees(math.atan2(v[1], v[0]))
            off = (az - rest + 180) % 360 - 180
            if kind == 'hardpoint':
                cone = math.degrees(math.acos(max(-1, min(1, v[0] / math.sqrt(h * h + v[2] ** 2)))))
                ok = cone <= (MISSILE_CONE if fx[0] == 'missiles' else max(arc / 2, 3))
                miss = cone
            else:
                in_az = abs(off) <= arc / 2 or el >= 75
                ok = in_az and GUN_ELEVATION[0] <= el <= GUN_ELEVATION[1]
                miss = max(0.0, GUN_ELEVATION[0] - el, abs(off) - arc / 2 if el < 75 else 0)
            every.append((ok, miss, kind, el, off, muzzle, fits))
            if best is None or (ok, fits, -miss) > (best[0], best[6], -best[1]):
                best = (ok, miss, kind, el, off, muzzle, fits)
        tgt_el = math.degrees(math.atan2(loc[2], math.hypot(loc[0], loc[1])))
        rows.append((fx[0], a, b, best, tgt_el))
        BEARING_CANDIDATES[(kf,) + tuple(fx[:3])] = sorted([e for e in every if e[0]], key=lambda e: (not e[6], e[1]))
    return rows


BEARING_CANDIDATES = {}     # (kf, kind, shooter, target) -> every mount that bears, launcher-fit first then closest


def main():
    argv = sys.argv[1:]
    if '--check' in argv:
        check(list(SHOTS))
        for line in hide_lines():
            print(line)
        for line in kit_lines():
            print(line)
        for line in rejoin_lines():
            print(line)
        return

    def opt(flag, default=None):
        return argv[argv.index(flag) + 1] if flag in argv else default

    if opt('--span'):            # a lit motion span (add_span): its steps are the frames
        os.environ['ASHARU_SPAN'] = opt('--span')
        argv += ['--frames', ','.join(add_span(opt('--span')))]
    frames = opt('--frames', ','.join(list(KEYFRAMES) + list(ROSTER_SHEETS))).split(',')
    run = Path(opt('--root', REPO / 'build' / 'asharu-battle')) / opt('--run', date.today().isoformat())
    run.mkdir(parents=True, exist_ok=True)
    started = run / 'run-date.txt'      # the day the run began: a resume after midnight posts under it
    if not started.exists():
        started.write_text(opt('--date', date.today().isoformat()))   # a queue round pins one date
    run_date = started.read_text().strip()
    samples = int(opt('--samples', 48))
    layers = '--combined' not in argv   # three view layers and a composite (composite_layers.py), the default from r10
    plates = '' if '--no-plates' in argv else opt('--plates')     # None: build/asharu-battle/plates (roster.PLATES)
    if '--motion-preview' in argv:  # the sequence as a cheap motion preview (asharu_preview.py)
        import asharu_preview
        asharu_preview.main(argv, run, run_date)
        return
    if '--fx-check' in argv:        # per-ship effects: stage the cued frames under the kit, no render
        if '--frames' not in argv:
            frames = [kf for kf in KEYFRAMES if KEYFRAMES[kf].get('cues')]
        argv += ['--stage-only', '--swfx']
    if '--stage-only' in argv:      # the whole Blender scene build for these frames, CPU only, nothing written
        from blender_path import BLENDER
        env = dict(os.environ, ASHARU_CFG=json.dumps(dict(out=str(run), frames=frames, samples=1, stage_only=True,
                                                          los='--los' in argv, swfx='--swfx' in argv,
                                                          fx_check='--fx-check' in argv,
                                                          volume_audit='--volume-audit' in argv,
                                                          layers=layers, plates=plates,
                                                          blockout_wrecks='--blockout-wrecks' in argv,
                                                          kit_once='--kit-once' in argv)))
        subprocess.check_call([str(BLENDER), '--background', '--factory-startup', '--python-exit-code', '1',
                               '--python', __file__], env=env)
        return
    if '--skip-render' not in argv:
        half = '--half' in argv
        surface = opt('--surface', 'detail' if SURFACE_DETAIL else 'base') == 'detail'
        todo = [kf for kf in frames if '--rerender' in argv
                or not finished(run / 'raw', kf, samples, half, surface, plates if layers and kf in KEYFRAMES else False)]
        done = [kf for kf in frames if kf not in todo]
        if done:
            print('ASHARU already finished, not rendered again:', ','.join(done), flush=True)
        if todo:
            from blender_path import BLENDER
            cfg = dict(out=str(run), frames=todo, samples=samples, half=half, swfx='--swfx' in argv, surface=surface,
                       layers=layers, plates=plates, volume_step_rate=opt('--volume-step-rate'),
                       volume_max_steps=opt('--volume-max-steps'), percent=opt('--percent'),
                       volume_biased='--volume-biased' in argv,
                       kit_once='--kit-once' in argv,
                       hide_volumes=opt('--hide-volumes'), blockout_wrecks='--blockout-wrecks' in argv)
            env = dict(os.environ, ASHARU_CFG=json.dumps(cfg))
            subprocess.check_call([str(BLENDER), '--background', '--factory-startup', '--python', __file__], env=env)
    if '--final' in argv:       # final frames: raw/<kf>_f<frame>.png is the clean composite; nothing labelled
        return
    outputs = [orbit_diagram(run)] + label_frames(run)
    print('\n'.join(str(run / o) for o in outputs))
    post = opt('--post')
    if post:
        import shutil
        drive = Path('G:/My Drive/starsector') if os.name == 'nt' else next(
            Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'))
        dest = drive / 'review' / 'cinematic-asharu-battle' / f"{run_date}_{opt('--what', 'blockout')}_{post}"
        marker = dest / '.asharu-run'         # one run may post in parts (two jobs); another run may not
        if dest.exists() and not (marker.exists() and marker.read_text().strip() == run.name):
            raise SystemExit(f'{dest} exists and belongs to another run')
        dest.mkdir(parents=True, exist_ok=True)
        marker.write_text(run.name)
        for o in outputs:
            shutil.copy2(run / o, dest / o)
        if (run / 'NOTES.md').exists():
            shutil.copy2(run / 'NOTES.md', dest / 'NOTES.md')
        print('POSTED', dest)


if __name__ == '__main__':
    try:
        import bpy  # noqa: F401
    except ImportError:
        bpy = None
    if bpy is not None and os.environ.get('ASHARU_CFG'):
        if os.environ.get('ASHARU_STACKS'):     # a build that hangs: every thread's stack, every N seconds
            import faulthandler
            faulthandler.dump_traceback_later(int(os.environ['ASHARU_STACKS']), repeat=True)
        blender_main(json.loads(os.environ['ASHARU_CFG']))
    else:
        main()
