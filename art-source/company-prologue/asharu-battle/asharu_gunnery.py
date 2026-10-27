"""Asharu battle fire control: who shoots whom, when, where, and how well (asharu-gunnery.md).

scene_fx is the execution layer: it trains each turret at its game turn rate, fires bursts only
when the turret is on target, in range and has a clear line, flies the rounds straight and walks
each burst about its aim point. This module is the decision layer above it. For every armed ship
in a shot it plans engagements, turret by turret, from the flight paths (bearing arcs, range
against weapon range, which hull is in the way) plus the story: the damage ledger's decisive hits
(asharu_damage.LEDGER), the ships that fall silent, the moments a ship holds fire.

    import asharu_gunnery
    engs = asharu_gunnery.plan(shot_kf, ship_key, turret, weapon, t0, t1)

plan() returns None when it has no opinion (the shot or the ship is not one it plans): the caller
then falls back to the blockout's fx entry. Otherwise a list, possibly empty (= hold fire for the
whole window), of engagements in start order, each a dict:

    target     ship key fired at (a hull, or a drone for point defence)
    region     what is aimed at: 'centre', 'bow', 'engines', 'bridge', 'port flank',
               'starboard flank', 'deck', 'belly', or a named story spot ('port pod', 'drives',
               'forward guns', ...)
    aim        (x, y, z) metres in the TARGET's own frame (+X bow, +Y port, +Z dorsal)
    walk_to    (x, y, z) target frame or None: a rake walks the burst from aim toward it
    start, end sequence seconds, inside [t0, t1]
    bursts     [(on, off), ...] sequence seconds: when the trigger is held. Concrete and seeded, so
               the gun code and the damage ledger see the same bursts
    burst_s    (lo, hi) seconds a burst lasts; gap_s (lo, hi) seconds between bursts: the ranges
               the bursts were drawn from
    hit_share  0..1, the share of rounds that should strike the target (hull or shield)
    walk_m     metres: how far off the hull the misses pass; the burst walks within that ring
    walk_r     scene_fx.planned's WALK_R (walk reach, share of her length) that lands hit_share
               along this engagement's line of fire (walk_r() calibrates it against her hull)
    elevation_deg, traverse_deg  (lo, hi) the mount's lay off its deck and keel over the engagement
    flight_s   seconds a round takes at range_m (the kit's round speed)
    arrive     for a story moment, the ledger time its rounds land (bursts are fired flight_s earlier)
    kind       'hull' or 'pd' (point defence on a drone or small craft)
    force      True for a story moment: the range gate is waived (arc and line still apply)
    ledger     the asharu_damage Hit id this engagement delivers, or None for incidental fire
    ledgers    every Hit id its held burst delivers (two moments on one target can share a burst)
    why        the doctrine, in words: 'story: ...', 'closest threat', 'point defence', ...
    shooter, turret, weapon, range_m (median metres over the engagement)

track(...) (same arguments) is the mount's continuous lay for the slew: the target and aim it is
on or swinging to, through the gaps. shot_plan(kf) is every engagement in a shot (for the damage ledger and the note's tables);
expected_hits(eng) is rounds fired and expected hits from the weapon's cadence; check() lists
what the plan cannot satisfy (ledger moments no mount can deliver). Plain Python, no Blender:

    python asharu_gunnery.py --table [kf ...]    # markdown tables, one per shot, a row per engagement
    python asharu_gunnery.py --summary [kf ...]  # the same grouped by ship, target and intent
    python asharu_gunnery.py --check             # ledger moments the geometry cannot deliver
    python asharu_gunnery.py --json PATH         # every shot's plan
"""
import json
import math
import random
import re
import sys
from functools import lru_cache
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
import battle_blockout as bb      # noqa: E402  plain Python: shots, flights, rotation order
import roster                     # noqa: E402

try:                               # the damage ledger (a sibling session's module): optional
    import asharu_damage
except ImportError:                # not merged yet: no story moments, no kills from it
    asharu_damage = None

DT = .1                  # planning step, seconds
LEAD_IN = 2.0            # guns are already firing this long before a shot opens (rounds in flight at the cut)

# ====================================================================================== weapons
# The game's weapon_data.csv: range (game units), turn rate (deg/s), damage per shot.
GAME = {
    'lightmg': (300, 50, 25), 'lightdualmg': (300, 50, 25), 'vulcan': (300, 75, 30),
    'lightac': (700, 20, 50), 'heavyac': (800, 7, 150), 'mark9': (800, 10, 200),
    'lightag': (700, 40, 40), 'lightneedler': (700, 20, 50), 'chaingun': (450, 30, 75),
    'hellbore': (900, 5, 750), 'ioncannon': (500, 40, 25), 'miningblaster': (500, 7, 200),
    'heavyblaster': (600, 15, 500), 'irpulse': (500, 50, 50), 'pdlaser': (500, 100, 75),
}
# Metres of cinematic range per game unit. The shots fight at 0.3-5 km (the shot doc's scale);
# taken literally the game's ranges (300-900) would silence every opening exchange, which is
# fought at 2.5-4 km. One knob for every weapon keeps their order: a Hellbore 4.5 km, a light
# autocannon 3.5 km, a heavy blaster 3 km, a chaingun 2.25 km, a light MG 1.5 km.
RANGE_SCALE = 5.0
# swfx_guns.WEAPONS cadence (burst rounds, s between them, cooldown s, chargeup s) and round
# speed m/s, mirrored here because swfx_guns needs Blender to import: change both together.
CADENCE = {
    'lightmg': (5, .1, .4, 0, 450), 'lightdualmg': (5, .1, .2, 0, 450), 'vulcan': (1, 0, .05, 0, 480),
    'lightac': (1, 0, .5, 0, 360), 'heavyac': (3, .2, 1.0, 0, 380), 'mark9': (4, .1, 2.0, 0, 400),
    'lightag': (1, 0, .25, 0, 380), 'lightneedler': (15, .05, 4.3, 0, 900), 'chaingun': (1, 0, .15, 0, 420),
    'hellbore': (1, 0, 3.0, 0, 170), 'ioncannon': (1, 0, 0.0, .5, 700), 'miningblaster': (1, 0, .2, 0, 600),
    'heavyblaster': (1, 0, 1.0, 0, 600),
}
# Trigger discipline, seconds: (burst lo, hi), (gap lo, hi). Gunners fire in bursts and walk them,
# they do not hose; heavy guns hold the trigger through their own slow cycle.
DISCIPLINE = {
    'mg': ((.6, 1.4), (.4, 1.1)), 'chain': ((.8, 1.6), (.4, 1.0)), 'ac': ((1.0, 2.2), (.5, 1.3)),
    'needler': ((.8, .8), (3.6, 4.4)), 'slug': ((3.1, 6.4), (.4, 1.5)), 'energy': ((.5, 1.4), (.5, 1.4)),
    'feeder': ((1.6, 3.0), (.2, .5)),
}
FAMILY = {'lightmg': 'mg', 'lightdualmg': 'mg', 'vulcan': 'mg', 'chaingun': 'chain', 'lightac': 'ac',
          'heavyac': 'ac', 'mark9': 'ac', 'lightag': 'ac', 'lightneedler': 'needler', 'hellbore': 'slug',
          'ioncannon': 'energy', 'miningblaster': 'energy', 'heavyblaster': 'energy', 'irpulse': 'energy'}
# Base share of rounds that strike a 70 m hull at no range and no crossing rate. Tasteful misses
# (owner, 2026-10-23): most rounds of a burst fly past and on.
BASE_HIT = {'mg': .42, 'chain': .45, 'ac': .48, 'needler': .55, 'slug': .7, 'energy': .5, 'feeder': .42}
MISS_TASTE = .8          # one knob on every share: lower for more misses
MOMENT_HIT = .6          # a story moment's decisive burst: it lands, and still a third of it flies past

# Which weapon each kit turret carries: scene_fx.WEAPON_OF / DEFAULT_WEAPON, mirrored for
# shot_plan() (scene_fx needs Blender). plan() always uses the weapon the caller passes.
WEAPON_OF = {
    'manticore-p': {1: 'hellbore', 2: 'lightag', 3: 'lightneedler', 4: 'lightag'},
    'lasher': {1: 'lightmg', 2: 'lightmg', 3: 'lightmg'},
    'wolf-p': {1: 'ioncannon', 2: 'miningblaster'},
    'wolf': {1: 'ioncannon', 4: 'heavyblaster'},
    'colossus-mk3': {**{t: 'lightmg' for t in range(1, 7)}, 7: 'lightac', 8: 'lightac'},
    'hound-p': {1: 'chaingun', 2: 'lightdualmg'},
    'hound': {1: 'chaingun', 2: 'lightdualmg'},
}
DEFAULT_WEAPON = {'falcon-p': 'heavyblaster', 'kite-p': 'lightdualmg', 'colossus': 'lightmg',
                  'tarsus': 'lightmg', 'tarsus-boarded': 'lightmg', 'shepherd': 'lightmg'}
FEEDER = {'lasher'}      # the Lasher's Ammo Feeder: longer, faster bursts when she presses (S6b, S9b)


def weapon_of(build, turret):
    return (WEAPON_OF.get(build) or {}).get(turret) or DEFAULT_WEAPON.get(build) or 'lightmg'


# The execution layer's own gate (scene_fx.SceneFx.MAX_FLIGHT_S, MAX_RANGE_M), mirrored so the plan
# asks only for fire that planned() will let through, and the kit's ceiling (swfx_guns.MAX_FLIGHT_S:
# no round is drawn flying longer). Change them with scene_fx. A story moment (force) may fly past
# the execution gate up to the kit's ceiling; scene_fx must honour `force` for it to fire.
EXEC_FLIGHT_S, EXEC_RANGE_M = 3.0, 3500.0
KIT_FLIGHT_S = 6.0


def speed_of(weapon):
    return CADENCE.get(weapon, (1, 0, .5, 0, 400))[4]


def reach_m(weapon):
    """How far doctrine fires this weapon: the game's range scaled, and no further than the round
    may fly under scene_fx's gate."""
    return min(GAME.get(weapon, (400,))[0] * RANGE_SCALE, speed_of(weapon) * EXEC_FLIGHT_S, EXEC_RANGE_M)


# Whether a story moment may fly past the execution gate. scene_fx.planned gates every round by
# reach_m's rule and has no `force`, so a moment beyond it is range-skipped there (coordinator, S6
# verify span 52-54 s): the plan asks only for what the execution fires, and --check reports the rest.
FORCE_PAST_GATE = False


def force_reach_m(weapon):
    """How far a story moment may fire it: the execution gate, or (FORCE_PAST_GATE) as far as the
    kit draws a round, a margin short of its ceiling."""
    return speed_of(weapon) * (KIT_FLIGHT_S - .3) if FORCE_PAST_GATE else reach_m(weapon)


def turn_rate(weapon):
    return GAME.get(weapon, (0, 30))[1]


# ====================================================================================== mounts
# Each finished build's kit turrets, numbered as the kit numbers them (ctx.anchors.guns.turret):
# (pivot metres in the ship frame, rest bearing deg, arc deg, 'turret' | 'hardpoint'). From the
# build manifests (pivot units x metres_per_unit), so this needs no build on disk. Launchers are
# not here: missiles are planned by the blockout's 'missiles' entries.
T, HP = 'turret', 'hardpoint'
MOUNTS = {
    'lasher': {1: ((3.8, 9.5, 7.6), 60, 270, T), 2: ((3.8, -9.5, 7.6), -60, 270, T), 3: ((35.2, 0, 8.4), 0, 270, T)},
    'wolf': {1: ((8.4, 0, 7.9), 0, 150, T), 2: ((-9.9, 9.1, 5.9), 90, 200, T), 3: ((-9.9, -8.4, 5.9), -90, 200, T),
             4: ((34.2, 0, -2.7), 0, 10, HP)},
    'wolf-p': {1: ((8.4, 0, 7.9), 0, 150, T), 2: ((34.2, 0, -2.7), 0, 10, HP)},
    # assumption: the kit numbers the Hound's forward hardpoint (WS 001) 1 and her dorsal turret (WS 002) 2
    'hound': {1: ((30.8, -5.3, .2), 0, 20, HP), 2: ((-3.5, 3.5, 8.6), 145, 290, T)},
    'hound-p': {1: ((30.8, -5.3, .2), 0, 20, HP), 2: ((-3.5, 3.5, 8.6), 145, 290, T)},
    'manticore-p': {1: ((12.4, 0, 5.7), 0, 90, T), 2: ((13.6, 38.4, -.9), 80, 235, T), 3: ((43.1, 0, .6), 0, 235, T),
                    4: ((13.6, -38.4, -.9), -80, 235, T)},
    'falcon-p': {7: ((44.6, 23.5, 9.1), 65, 180, T), 8: ((44.6, -23.5, 9.1), -65, 180, T)},
    'kite-p': {3: ((-5.6, 0, 7.0), 0, 200, T)},
    'colossus': {1: ((34.5, 37.5, 8), 80, 140, T), 2: ((1.5, 37.5, 8), 90, 130, T), 3: ((35, -37, 8), -80, 140, T),
                 4: ((2, -36.5, 8), -90, 130, T)},
    'colossus-mk3': {1: ((20.8, 29.7, 8.6), 80, 140, T), 2: ((-3.0, 31.2, 8.6), 90, 130, T),
                     3: ((20.8, -28.7, 8.6), -80, 140, T), 4: ((-3.0, -30.2, 8.6), -90, 130, T),
                     5: ((9.4, -25.2, 11.7), -90, 130, T), 6: ((8.9, 26.2, 12.2), 90, 130, T),
                     7: ((29.2, -19.3, 12.5), -40, 170, T), 8: ((29.2, 20.3, 12.5), 50, 170, T),
                     9: ((39.6, -26.2, 8.6), -45, 200, T), 10: ((39.6, 27.2, 8.6), 45, 200, T),
                     11: ((41.6, 14.4, 10.5), 6, 160, T), 12: ((41.6, -13.4, 10.5), -6, 160, T)},
    'tarsus': {1: ((-20.5, 18.5, 7.2), 135, 250, T), 2: ((-20.5, -18.5, 7.2), 225, 250, T),
               3: ((50.5, 10.5, 4.7), 45, 250, T), 4: ((50.5, -10, 4.7), -45, 250, T)},
    'shepherd': {1: ((-2.3, -17.9, 5.0), -75, 200, T)},
    'phaeton': {1: ((-24.8, 0, 4.7), 180, 150, T), 2: ((12.5, 12.5, 4.0), 90, 180, T),
                3: ((11.9, -11.2, 4.0), -90, 180, T), 4: ((41.6, 4.3, 4.0), 0, 210, T)},
}
MOUNTS['tarsus-boarded'] = MOUNTS['tarsus']
GUN_ELEVATION = bb.GUN_ELEVATION      # a dorsal turret's reach above (and just below) its deck plane
HARDPOINT_CONE = 6.0                  # half-angle a fixed gun fires within off its keel line
SHIP_HALF = bb.debris_field.SHIP_HALF       # a hull's box, a share of her length (blocked)
UNARMED = {'hermes', 'borer', 'phaeton-wreck'}      # the boats carry no guns here; the wreck has none

MEMBERS = {m['key']: m for m in roster.MERCHANT + roster.PIRATE + getattr(roster, 'DRONES', [])}
PIRATE_SIDE = {m['key'] for m in roster.PIRATE}


def side(key):
    if key.startswith('hermes-p'):
        return 'pirate'          # the Kites' (P) boarding boats
    if key.startswith('borer'):
        return 'convoy'          # Morrow's drones
    if key in MEMBERS:
        return 'pirate' if key in PIRATE_SIDE else 'convoy'
    return 'pirate' if key.startswith('hermes-p') else 'convoy'    # Borers are Morrow's


def build_of(kf, key):
    st = bb.SHOTS[kf].get('states', {}).get(key)
    if st:
        return st
    m = MEMBERS.get(key)
    if m is None:
        return key.split('-')[0]          # 'borer-c' -> 'borer', 'hermes-p-a' -> 'hermes'
    return m.get('finished')


def length_m(key):
    if key in bb.SHIP_LENGTH:
        return bb.SHIP_LENGTH[key]
    return {'borer': 3.6, 'hermes': 9.0}.get(key.split('-')[0], 50.0)


@lru_cache(maxsize=None)
def standin_mounts(key):
    """A stand-in's guns from the game's weapon slots (bb.mounts), numbered 1.. in slot order."""
    rows = [r for r in bb.mounts(key) if not r[5]]
    return {n + 1: (tuple(p), rest, arc, HP if kind == 'hardpoint' else T) for n, (p, rest, arc, kind, _m, _l) in
            enumerate(rows)}


def mounts_of(kf, key):
    b = build_of(kf, key)
    if b in UNARMED or key.split('-')[0] in UNARMED:
        return {}
    if b in MOUNTS:
        return MOUNTS[b]
    if b is None and key in MEMBERS:
        return standin_mounts(key)
    return {}


# ====================================================================================== regions
# Aim points in each hull's own frame, metres. Generic ones scale with the hull's length; the
# story spots are where the shot doc and the damage ledger put the hits.
SPOTS = {
    'colossus': {'port pod': (-62, 30, 0), 'starboard pod': (-62, -30, 0), 'bridge': (40, 0, 14)},
    'tarsus': {'drives': (-44, 0, 4), 'forward guns': (46, 0, 6), 'bridge': (30, 0, 12)},
    'tarsus-boarded': {'drives': (-44, 0, 4), 'forward guns': (46, 0, 6), 'bridge': (30, 0, 12)},
    'falcon-p': {'starboard flank': (-20, -34, 4), 'port flank': (-20, 34, 4), 'bridge': (40, 0, 14)},
    'lasher': {'bridge': (-10, 0, 10)},
    'manticore-p': {'bridge': (0, 0, 9)},
}


def regions(build, L):
    r = {'centre': (0, 0, 0), 'bow': (.36 * L, 0, 0), 'engines': (-.42 * L, 0, 0), 'bridge': (.1 * L, 0, .1 * L),
         'port flank': (0, .12 * L, 0), 'starboard flank': (0, -.12 * L, 0), 'deck': (0, 0, .08 * L),
         'belly': (0, 0, -.08 * L)}
    r.update(SPOTS.get(build, {}))
    return r


def nearest_region(build, L, p):
    rs = regions(build, L)
    return min(rs, key=lambda k: math.dist(rs[k], p))


def presented(build, L, toward_local):
    """The aspect a gunner aims at: the face of the hull the shooter sees most of (centre mass of
    the presented area), from the shooter's direction in the target's frame."""
    x, y, z = toward_local
    ax, ay, az = abs(x) * .35, abs(y), abs(z) * 1.2      # a long thin hull shows its flank or its deck
    rs = regions(build, L)
    if ay >= ax and ay >= az:
        k = 'port flank' if y > 0 else 'starboard flank'
    elif az >= ax:
        k = 'deck' if z > 0 else 'belly'
    else:
        k = 'bow' if x > 0 else 'engines'
    return k, rs[k]


# ====================================================================================== geometry
def pose_at(p, t):
    return p.at(t) if hasattr(p, 'at') else p


def frame(p):
    return p[:3], bb._rot(*p[3:6])


def to_world(fr, local):
    o, R = fr
    return tuple(o[i] + sum(R[i][j] * local[j] for j in range(3)) for i in range(3))


def to_local(fr, w):
    o, R = fr
    d = [w[i] - o[i] for i in range(3)]
    return tuple(sum(R[j][i] * d[j] for j in range(3)) for i in range(3))


def bears(mount, local):
    """(ok, miss deg) whether a mount can lay on a point given in the shooter's frame."""
    pos, rest, arc, kind = mount
    v = [local[i] - pos[i] for i in range(3)]
    h = math.hypot(v[0], v[1])
    el = math.degrees(math.atan2(v[2], h))
    if kind == HP:
        cone = math.degrees(math.acos(max(-1.0, min(1.0, v[0] / max(1e-9, math.sqrt(h * h + v[2] ** 2))))))
        lim = max(arc / 2, HARDPOINT_CONE)
        return cone <= lim, max(0.0, cone - lim)
    off = (math.degrees(math.atan2(v[1], v[0])) - rest + 180) % 360 - 180
    in_az = abs(off) <= arc / 2 or el >= 75
    miss = max(0.0, GUN_ELEVATION[0] - el, el - GUN_ELEVATION[1], (abs(off) - arc / 2) if el < 75 else 0.0)
    return in_az and GUN_ELEVATION[0] <= el <= GUN_ELEVATION[1], miss


# Where each finished hull's own plating stands in the way of her own guns (battle_blockout --turret-masks,
# cast from the builds: a ray every MASKS[build]['step'] deg of bearing and elevation from each muzzle, her
# frame). A gun does not fire through her own hull (owner, 2026-10-27: "Specifically I just want to make sure
# it's not firing through it's own hull"; hull_cross_lint holds the drawn rounds to it).
MASK_FILE = HERE / 'turret_masks.json'
MASKS = json.loads(MASK_FILE.read_text()) if MASK_FILE.exists() else {}
MUZZLE_UP = 2.0          # the muzzle above its pivot, as the masks and hull_cross_lint place it


@lru_cache(maxsize=None)
def _mask_bits(build, turret):
    import base64
    m = MASKS.get(build, {}).get('mounts', {}).get(str(turret))
    return base64.b64decode(m) if m else None


def self_masked(build, turret, mount, local):
    """Whether her own hull stands between this mount's muzzle and a point in her frame."""
    bits = _mask_bits(build, turret)
    if bits is None:
        return False
    step = MASKS[build]['step']
    v = [local[0] - mount[0][0], local[1] - mount[0][1], local[2] - mount[0][2] - MUZZLE_UP]
    az = math.degrees(math.atan2(v[1], v[0]))
    el = math.degrees(math.atan2(v[2], math.hypot(v[0], v[1])))
    nb, ne = int(round(360 / step)), int(round(180 / step))
    i, j = min(nb - 1, int((az + 180) / step)), min(ne - 1, int((el + 90) / step))
    for jj in (j - 1, j, j + 1):            # a cell either side: a mast thinner than a cell slips between rays
        for ii in (i - 1, i, i + 1):
            if 0 <= jj < ne:
                n = jj * nb + ii % nb
                if bits[n // 8] >> (n % 8) & 1:
                    return True
    return False


def hull_box(kf, key):
    """Her extent in her own frame, (min corner, max corner) metres: the build's real one (turret_masks.json
    _boxes), else debris_field.SHIP_HALF of her length about her centre."""
    b = MASKS.get('_boxes', {}).get(build_of(kf, key))
    if b:
        return tuple(b[0]), tuple(b[1])
    half = [h * length_m(key) for h in SHIP_HALF]
    return tuple(-h for h in half), tuple(half)


def blocked(a, b, others):
    """The other hull a straight line a -> b passes through, or None. others: [(key, frame, (min, max)
    corners of her box in her frame)]: a bounding sphere of a third of her length missed a long hull's ends
    (hull_cross_lint, 2026-10-27: S8's fire through the Colossus's drive pods)."""
    for key, fr, (bmin, bmax) in others:
        p, q = to_local(fr, a), to_local(fr, b)
        lo, hi = .02, .98                   # the muzzle's and the aim's own ends are not in the way
        for i in range(3):
            d = q[i] - p[i]
            if abs(d) < 1e-9:
                if not bmin[i] <= p[i] <= bmax[i]:
                    lo, hi = 1.0, 0.0
                    break
                continue
            t0, t1 = (bmin[i] - p[i]) / d, (bmax[i] - p[i]) / d
            lo, hi = max(lo, min(t0, t1)), min(hi, max(t0, t1))
        if lo < hi:
            return key
    return None


# ====================================================================================== story
def base_kf(kf):
    """'k3_f01234' (a motion-span frame) -> 'k3'."""
    if kf in bb.SHOTS and not bb.SHOTS[kf].get('span_of'):
        return kf
    return bb.SHOTS[kf]['span_of'] if kf in bb.SHOTS and bb.SHOTS[kf].get('span_of') else re.sub(r'_f\d+$', '', kf)


def window(kf):
    keys = bb.SHOTS[kf]['camera']['at'].keys
    return keys[0]['t'], keys[-1]['t']


# The story's standing orders per shot: (shooter, target) -> weight added to the doctrine score,
# so the ship the story says is being attacked wins over a nearer one. From the shot list and
# the blockout's fx entries.
INTENT = {
    'm5': {('manticore-p', 'phaeton'): 3},
    'k3': {('manticore-p', 'lasher-lead'): 2.5, ('falcon-p', 'lasher-lead'): 2, ('falcon-p', 'lasher-b'): 1.5,
           ('lasher-lead', 'manticore-p'): 3, ('lasher-b', 'wolf-p-a'): 3},
    'k3c': {('lasher-b', 'hound-p-c'): 3, ('wolf-m', 'hound-p-c'): 3, ('buffalo2-a', 'lasher-b'): 2,
            ('hound-p-c', 'lasher-b'): 1.5},
    'k3d': {('wolf-p-a', 'tarsus'): 3, ('wolf-p-b', 'tarsus'): 3, ('wolf-m', 'wolf-p-a'): 3, ('mule', 'wolf-p-b'): 3,
            ('tarsus', 'wolf-p-a'): 1, ('tarsus', 'wolf-p-b'): 1},
    'k3e': {('hound-m', 'hound-p-a'): 3, ('hound-p-b', 'hound-m'): 3, ('hound-p-a', 'hound-m'): 2},
    'k3b': {('wolf-p-b', 'tarsus'): 3, ('tarsus', 'wolf-p-b'): 1.5},
    'k4': {('colossus3', 'colossus'): 3, ('colossus', 'colossus3'): 1.5, ('colossus', 'kite-p-b'): 2,
           ('colossus', 'wolf-p-a'): 2, ('kite-p-b', 'colossus'): 2, ('wolf-p-a', 'colossus'): 1.5},
    'k4b': {('lasher-lead', 'falcon-p'): 3, ('falcon-p', 'lasher-lead'): 3},
}
# Story aim spots for a standing order, when the story names one (target frame, metres).
INTENT_AIM = {
    ('k3d', 'wolf-p-a', 'tarsus'): 'drives', ('k3d', 'wolf-p-b', 'tarsus'): 'drives',
    ('k3b', 'wolf-p-b', 'tarsus'): 'forward guns', ('k4', 'colossus3', 'colossus'): 'port pod',
    ('k4b', 'lasher-lead', 'falcon-p'): 'starboard flank',
}
# Shots where nobody fires, whatever the geometry: (reason).
CEASEFIRE = {
    'k0': 'before the ambush', 'k1': 'before the ambush', 'm2': 'before the ambush',
    'k1b': 'running dark: the pirates are cold in the field, the convoy has seen nothing',
    'k2': 'the pirates still climbing out of the field, the convoy not yet turned: the Manticore opens in S5',
    'k5': "the fight is over: 'Let her go' - the pirates take their prize and nobody shoots",
    'k6': 'the wreckfall', 'k6c': 'the wreckfall', 'm11': 'entry',
}
# Targets nobody on the shooter's side fires at in a shot, except under a standing order (INTENT):
# kf -> targets. The prize her own side's boats are landing on is not shot at, only her guns.
HOLD_ON = {'k3b': {'tarsus'}}

# Ships that stop firing, from a time: (t, why). Kills come from the ledger as well.
SILENT = {
    'lasher-a': (52.6, 'burning and falling back out of the fight (S6)'),
    'phaeton': (54.0, 'struck by the Harpoons and cooking off'),
    'lasher-lead': (107.2, 'tumbling away burning after the broadside (S9b)'),
}
# Per-turret silences: (ship, turret) -> (t, why).
SILENT_TURRET = {
    ('tarsus', 1): (70.5, 'her aft deck burning over the shot-out drives'),
    ('tarsus', 2): (70.5, 'her aft deck burning over the shot-out drives'),
    ('tarsus', 3): (84.4, "silenced by the Wolf (P)'s covering run before the boats land"),
    ('tarsus', 4): (84.4, "silenced by the Wolf (P)'s covering run before the boats land"),
    # rolled onto her side to fight the Colossus, her port bow turret would fire across her own bow end plate
    # (hull_cross_lint, 83.3-85.5 s in S7)
    ('colossus3', 10): (83.0, 'masked by her own bow once she rolls onto her side'),
}
# A target is still worth shooting this long after it is killed (the wreck tumbling apart).
DEAD_LINGER = .4
# The Wolves' (P) Displacer blinks: no gun fires from blink-0.3 to blink+0.4.
BLINK_HOLD = (.3, .4)


def blinks(kf, key):
    """Displacer blink times of a flown ship (motion.Flight.jumps; a keyed Pose's at is its Flight)."""
    p = bb.SHOTS[kf]['ships'].get(key)
    return [j[0] for j in getattr(getattr(p, 'at', None), 'jumps', None) or []]


def killed(key):
    if asharu_damage is not None:
        t = asharu_damage.killed_at(key)
        if t is not None:
            return t
    return None


def silent_from(key, turret=None):
    """(t, why) from when this ship (or turret) no longer fires, or None."""
    rows = []
    if key in SILENT:
        rows.append(SILENT[key])
    if (key, turret) in SILENT_TURRET:
        rows.append(SILENT_TURRET[(key, turret)])
    k = killed(key)
    if k is not None:
        rows.append((k, 'destroyed'))
    return min(rows) if rows else None


# Ledger hits the shots draw with a blast or a fire rather than a tracer, but which the story says a
# gun did: hit id -> the ships whose decisive burst lands it. Hits with no `by` at all are the
# ledger's own story hits and get no gun (its owner's call), so their rows here are inert.
GUN_MADE = {
    'hound-p-c@61.9:kill': ('lasher-b', 'wolf-m'),          # the Ammo Feeder burst breaks her up (S6b)
    'tarsus@70.5:breach': ('wolf-p-a', 'wolf-p-b'),         # the Wolves (P) shoot her drives out (S6c)
    'hound-m@75.5:breach': ('hound-p-b',),                  # drives hit (S6d)
    'hound-m@77.4:he': ('hound-p-b',),                      # the round that takes her thrust (S6d)
    'hound-p-a@76.9:kill': ('hound-m',),                    # killed at close range (S6d)
    'falcon-p@105.0:breach': ('lasher-lead',),              # the flank opened after the overload (S9b)
    'falcon-p@106.0:he': ('lasher-lead',),                  # her last rounds (S9b)
    'lasher-lead@103.5:breach': ('falcon-p',),              # the Falcon's point guns as she rolls in (S9b)
    'lasher-a@52.8:he': ('falcon-p',),                      # a Falcon (P) bolt opens her quarter (S6)
    'wolf-m@73.0:he': ('wolf-p-b',),                        # a blaster graze in the gap (S6c); Wolf (P) A has her astern
    'colossus3@99.5:rake': ('colossus',),                   # a second burst as she swings her grapples away (S9)
}


@lru_cache(maxsize=None)
def moments():
    """The damage ledger's gunfire hits: [(t, shooter, target, at, to, hit id)]: every hit drawn by
    a tracer (its shooter named in `src`) and every GUN_MADE hit."""
    if asharu_damage is None:
        return ()
    out = []
    for h in asharu_damage.LEDGER:
        if not h.bys:                # a story hit the ledger keys itself (asharu_damage, 2026-10-23): no gun
            continue
        m = re.search(r'tracer (\S+)', h.src) if 'tracer' in h.bys else None
        shooters = (m.group(1),) if m else GUN_MADE.get(h.id, ())
        for sh in shooters:
            out.append((h.t, sh, h.ship, h.at, h.to, h.id))
    return tuple(out)


# ====================================================================================== planning
class _Shot:
    """One shot's poses sampled once: frames per ship per step."""

    def __init__(self, kf):
        self.kf = kf
        spec = bb.SHOTS[kf]
        self.ships = spec['ships']
        self.t0, self.t1 = window(kf)
        self.times = [round(self.t0 - LEAD_IN + n * DT, 4) for n in range(int(round((self.t1 - self.t0 + LEAD_IN) / DT)) + 1)]
        self.fr = {k: [frame(pose_at(p, t)) for t in self.times] for k, p in self.ships.items()}

    def i(self, t):
        return max(0, min(len(self.times) - 1, int(round((t - self.times[0]) / DT))))


@lru_cache(maxsize=None)
def _shot(kf):
    return _Shot(kf)


def _valid(sh, key, turret, mount, weapon, target, i, aim_l, force=False):
    """(ok, range m, reason) for one mount on one target point at step i."""
    fa, fb = sh.fr[key][i], sh.fr[target][i]
    aim_w = to_world(fb, aim_l)
    ok, miss = bears(mount, to_local(fa, aim_w))
    if not ok:
        return False, None, f'out of arc by {miss:.0f} deg'
    if self_masked(build_of(sh.kf, key), turret, mount, to_local(fa, aim_w)):
        return False, None, 'masked by her own hull'
    mz = to_world(fa, mount[0])
    d = math.dist(mz, aim_w)
    lim = force_reach_m(weapon) if force else reach_m(weapon)
    if d > lim:
        return False, d, f'out of range ({d:.0f} m > {lim:.0f})'
    others = [(k, sh.fr[k][i], hull_box(sh.kf, k)) for k in sh.ships if k not in (key, target)]
    b = blocked(mz, aim_w, others)
    if b:
        return False, d, f'line fouled by {b}'
    return True, d, ''


def _held(sh, key, turret, t):
    s = silent_from(key, turret)
    if s and t >= s[0]:
        return s[1]
    for tb in blinks(sh.kf, key):
        if tb - BLINK_HOLD[0] <= t <= tb + BLINK_HOLD[1]:
            return 'displacer blink'
    return None


def _alive(target, t):
    k = killed(target)
    return k is None or t <= k + DEAD_LINGER


def _score(kf, sh, key, target, i, d, weapon, current):
    s = INTENT.get(kf, {}).get((key, target), 0.0)
    s += 1.2 * max(0.0, 1.0 - d / reach_m(weapon))                      # closest threat
    if mounts_of(kf, target):
        s += .5                                                          # it can shoot back
    if INTENT.get(kf, {}).get((target, key)):
        s += .6                                                          # it is shooting at me
    if target == current:
        s += .8                                                          # keep the gun on what it is on
    if _pd_target(target):
        s += .4 if d < 700 else -2.0                                     # point defence, only close in
    return s


def _pd_target(key):
    return key.split('-')[0] in ('borer', 'hermes')


def _accuracy(kf, sh, key, mount, weapon, target, i0, i1, aim_l, feeder):
    fam = 'feeder' if feeder else FAMILY.get(weapon, 'mg')
    ds, rates = [], []
    for i in range(i0, i1 + 1, 2):
        fa = sh.fr[key][i]
        w = to_world(sh.fr[target][i], aim_l)
        ds.append(math.dist(to_world(fa, mount[0]), w))
        j = min(i + 1, len(sh.times) - 1)
        if j > i:
            a = to_local(fa, w)
            b = to_local(sh.fr[key][j], to_world(sh.fr[target][j], aim_l))
            na, nb = math.sqrt(sum(c * c for c in a)) or 1, math.sqrt(sum(c * c for c in b)) or 1
            cos = max(-1.0, min(1.0, sum(a[k] * b[k] for k in range(3)) / (na * nb)))
            rates.append(math.degrees(math.acos(cos)) / ((j - i) * DT))
    d = sorted(ds)[len(ds) // 2]
    rate = sorted(rates)[len(rates) // 2] if rates else 0.0
    L = length_m(target)
    hit = BASE_HIT[fam] * MISS_TASTE
    hit *= 1.0 - .55 * min(1.0, d / reach_m(weapon))
    hit *= max(.25, min(1.4, (L / 70.0) ** .5))
    hit /= 1.0 + rate / max(1.0, turn_rate(weapon))
    if _pd_target(target):
        hit = min(hit, .15)
    hit = max(.05, min(.85, hit))
    walk = round(.5 * L * (1.0 + 1.5 * (1.0 - hit)), 1)
    return round(hit, 2), walk, d, fam


def _bursts(seed, start, end, fam, must=()):
    (b0, b1), (g0, g1) = DISCIPLINE[fam]
    rnd = random.Random(seed)
    out, t = [], start + rnd.uniform(0, .3)
    while t < end - .15:
        on = min(end, t + rnd.uniform(b0, b1))
        out.append([round(t, 2), round(on, 2)])
        t = on + rnd.uniform(g0, g1)
    for (a, b) in must:              # a story moment is always under a held trigger
        a, b = max(start, a), min(end, b)
        if b <= a:
            continue
        out.append([round(a, 2), round(b, 2)])
    out.sort()
    merged = []
    for a, b in out:
        if merged and a <= merged[-1][1] + .05:
            merged[-1][1] = max(merged[-1][1], b)
        else:
            merged.append([a, b])
    return [tuple(x) for x in merged]



# ====================================================================================== walk calibration
# scene_fx.planned walks each burst across the aim point: it opens reach x U(.9, 1.6) off to one side,
# walks to reach x U(0, 1.3) on the far side (+-.8 rad), with gaussian jitter of .08 reach, and a round
# hits only where its straight line meets the hull. reach = WALK_R x her length. walk_r() solves for
# the WALK_R whose walk lands hit_share of the rounds, against an ellipsoid hull (half-axes
# HULL_AXES x length) seen along the engagement's own line of fire, by the same walk drawn in 2D.
HULL_AXES = (.5, .3, .1)


def _walk_hits(walk_r, line, aim, L, n_bursts=8, per=16, seed='walk'):
    a = [HULL_AXES[i] * L for i in range(3)]
    d = line
    u = (-d[1], d[0], 0.0) if abs(d[2]) < .9 else (0.0, -d[2], d[1])
    nu = math.sqrt(sum(c * c for c in u)) or 1.0
    u = [c / nu for c in u]
    v = (d[1] * u[2] - d[2] * u[1], d[2] * u[0] - d[0] * u[2], d[0] * u[1] - d[1] * u[0])
    reach = walk_r * L
    rnd = random.Random(seed)
    hits = total = 0
    for _b in range(n_bursts):
        th = rnd.uniform(0, 2 * math.pi)
        r0 = reach * rnd.uniform(.9, 1.6)
        ph = th + math.pi + rnd.uniform(-.8, .8)
        r1 = reach * rnd.uniform(0, 1.3)
        for k in range(per):
            f = k / max(1, per - 1)
            ox = math.cos(th) * r0 + (math.cos(ph) * r1 - math.cos(th) * r0) * f + rnd.gauss(0, .08 * reach)
            oy = math.sin(th) * r0 + (math.sin(ph) * r1 - math.sin(th) * r0) * f + rnd.gauss(0, .08 * reach)
            q = [aim[i] + u[i] * ox + v[i] * oy for i in range(3)]
            qa = sum(q[i] * d[i] / a[i] ** 2 for i in range(3))
            dd = sum(d[i] * d[i] / a[i] ** 2 for i in range(3))
            qq = sum(q[i] * q[i] / a[i] ** 2 for i in range(3))
            hits += qa * qa - dd * (qq - 1.0) >= 0
            total += 1
    return hits / total


def walk_r(hit_share, line, aim, L, seed='walk'):
    """The scene_fx WALK_R (share of her length) that lands hit_share of a planned walk's rounds."""
    lo, hi = .02, 3.0
    if _walk_hits(lo, line, aim, L, seed=seed) <= hit_share:
        return lo
    for _ in range(18):
        mid = (lo + hi) / 2
        if _walk_hits(mid, line, aim, L, seed=seed) > hit_share:
            lo = mid
        else:
            hi = mid
    return round((lo + hi) / 2, 3)


def _attitude_span(sh, key, mount, tgt, aim, i0, i1):
    """(elevation lo, hi), (traverse lo, hi) in degrees off the shooter's deck and keel, and the mean
    line of fire in the target's frame (unit), over an engagement."""
    els, azs, line = [], [], [0.0, 0.0, 0.0]
    for i in range(i0, i1 + 1):
        fa, fb = sh.fr[key][i], sh.fr[tgt][i]
        w = to_world(fb, aim)
        loc = to_local(fa, w)
        vv = [loc[k] - mount[0][k] for k in range(3)]
        els.append(math.degrees(math.atan2(vv[2], math.hypot(vv[0], vv[1]))))
        azs.append(math.degrees(math.atan2(vv[1], vv[0])))
        src = to_local(fb, to_world(fa, mount[0]))
        dl = [aim[k] - src[k] for k in range(3)]
        n = math.sqrt(sum(c * c for c in dl)) or 1.0
        line = [line[k] + dl[k] / n for k in range(3)]
    n = math.sqrt(sum(c * c for c in line)) or 1.0
    return ((round(min(els), 1), round(max(els), 1)), (round(min(azs), 1), round(max(azs), 1)),
            tuple(c / n for c in line))


@lru_cache(maxsize=None)
def _moment_reach(kf, key, turret):
    """The ledger moments this ship owes in this shot, as this mount sees them. The round must ARRIVE
    at the ledger's time, so the gun fires a flight time earlier: a step counts if the round it fires,
    flown at the range it leaves at, lands within 0.4 s of the hit (0.9 s after, for a kill's
    finishing burst), and the mount bears with a clear line and within the gate (force_reach_m)."""
    sh = _shot(kf)
    build = build_of(kf, key)
    mount = mounts_of(kf, key).get(turret) if turret is not None else None
    mount = mount or ((0.0, 0.0, 0.0), 0, 360, T)
    weapon = weapon_of(build, turret) if build in MOUNTS else 'lightmg'
    out = []
    for (t, shooter, target, at, to, hid) in moments():
        if shooter != key or target not in sh.ships or not (sh.times[0] <= t <= sh.t1 + .5):
            continue
        late = .9 if hid.endswith(':kill') else .4      # a kill takes a finishing burst into the breaking wreck
        good, why, miss, flight = [], 'held', None, None
        for i, tt in enumerate(sh.times):
            if tt > t or _held(sh, key, turret, tt):
                continue
            d = math.dist(to_world(sh.fr[key][i], mount[0]), to_world(sh.fr[target][i], at))
            arrive = tt + d / speed_of(weapon)          # this step's round, flown at the range it left at
            if not (t - .4 <= arrive <= t + late):
                continue
            ok, _d, w = _valid(sh, key, turret, mount, weapon, target, i, at, force=True)
            m = bears(mount, to_local(sh.fr[key][i], to_world(sh.fr[target][i], at)))[1]
            miss = m if miss is None else min(miss, m)
            if ok:
                good.append(i)
                flight = d / speed_of(weapon) if flight is None else flight
            else:
                why = w
        if flight is None:               # nothing lands on time: say how far it is at the hit time
            j = sh.i(t)
            d = math.dist(to_world(sh.fr[key][j], mount[0]), to_world(sh.fr[target][j], at))
            flight = d / speed_of(weapon)
            if why == 'held' and not any(t - .4 <= tt + flight <= t + late for tt in sh.times):
                why = 'fires before the shot is simulated'
            if d > force_reach_m(weapon) and why in ('held', 'fires before the shot is simulated'):
                why = (f'{weapon} at {d:.0f} m: round flight {flight:.1f} s, past the execution gate '
                       f'({force_reach_m(weapon):.0f} m)')
        out.append(dict(t=t, target=target, at=at, to=to, hid=hid, steps=frozenset(good), ok=bool(good),
                        why='' if good else why, miss=miss, flight=round(flight, 2)))
    return tuple(out)


@lru_cache(maxsize=None)
def assign_moments(kf, key):
    """hit id -> the ship's mounts that fire it. A moment goes to every mount that can deliver it and
    is not already busy with another moment within 1.2 s; the hardest moment (fewest mounts) first.
    Mounts that can deliver none are listed under nothing, and plan doctrine fire only."""
    turrets = sorted(mounts_of(kf, key))
    reach = {tu: {o['hid']: o for o in _moment_reach(kf, key, tu)} for tu in turrets}
    hids = {o['hid']: o['t'] for o in _moment_reach(kf, key, turrets[0])} if turrets else {}
    able = {h: [tu for tu in turrets if reach[tu][h]['ok']] for h in hids}
    busy = {tu: [] for tu in turrets}
    out = {}
    owner = {}                        # (turret, moment time) -> moment holding it
    for h in sorted(hids, key=lambda h: (len(able[h]), hids[h])):
        free = [tu for tu in able[h] if all(abs(hids[h] - t) > 1.2 for t in busy[tu])]
        if not free:                 # take a mount from a clashing moment that has another to spare
            for tu in able[h]:
                clash = [owner[(tu, t)] for t in busy[tu] if abs(hids[h] - t) <= 1.2]
                if clash and all(len(out[c]) > 1 for c in clash):
                    for c in clash:
                        out[c] = out[c] - {tu}
                        busy[tu].remove(hids[c])
                    free = [tu]
                    break
        chosen = free or sorted(able[h], key=lambda tu: len(busy[tu]))[:1]
        for tu in chosen:
            busy[tu].append(hids[h])
            owner[(tu, hids[h])] = h
        out[h] = frozenset(chosen)
    return out


@lru_cache(maxsize=None)
def _turret_plan(kf, key, turret, weapon):
    """Every engagement of one mount over its shot (cached), before clipping to a caller's window."""
    sh = _shot(kf)
    build = build_of(kf, key)
    mts = mounts_of(kf, key)
    mount = mts.get(turret) if turret is not None else None
    if mount is None:                 # a turret this module has no table for: plan the ship as a whole
        mount = ((0.0, 0.0, 0.0), 0, 360, T)
    hostiles = [k for k in sh.ships if side(k) != side(key)]
    feeder = build in FEEDER and kf in ('k3c', 'k4b')
    # 1. the story moments this mount owes (assign_moments shares them out across the ship's mounts)
    owed = [o for o in _moment_reach(kf, key, turret) if turret is None or turret in assign_moments(kf, key)[o['hid']]]
    # 2. doctrine, step by step: the best valid target, sticky, ignoring the silent and the dead;
    #    a deliverable story moment takes the gun for its steps
    pick, reason = [], []
    current = None
    for i, t in enumerate(sh.times):
        h = _held(sh, key, turret, t)
        if h:
            pick.append(None)
            reason.append(h)
            current = None
            continue
        forced = min((o for o in owed if i in o['steps']), key=lambda o: abs(o['t'] - o['flight'] - t), default=None)
        if forced:
            pick.append((forced['target'], forced['hid']))
            reason.append('')
            current = forced['target']
            continue
        best, why_none = None, 'no hostile in the shot'
        for tgt in hostiles:
            if not _alive(tgt, t) or (tgt in HOLD_ON.get(kf, ()) and not INTENT.get(kf, {}).get((key, tgt))):
                continue
            L = length_m(tgt)
            toward = to_local(sh.fr[tgt][i], sh.fr[key][i][0])
            region = INTENT_AIM.get((kf, key, tgt))
            aim = regions(build_of(kf, tgt), L)[region] if region else presented(build_of(kf, tgt), L, toward)[1]
            ok, d, why = _valid(sh, key, turret, mount, weapon, tgt, i, aim)
            if not ok:
                why_none = why
                continue
            s = _score(kf, sh, key, tgt, i, d, weapon, current)
            if best is None or s > best[0]:
                best = (s, tgt)
        pick.append((best[1], None) if best else None)
        reason.append('' if best else why_none)
        current = best[1] if best else None
    # 3. segments: runs of one target (a story moment its own run), flickers under 0.6 s dropped,
    #    slew time before the first burst whenever the gun changes target
    segs, i = [], 0
    while i < len(pick):
        j = i
        while j + 1 < len(pick) and pick[j + 1] == pick[i]:
            j += 1
        segs.append([pick[i], i, j])
        i = j + 1
    for s in segs:
        if s[0] is not None and s[0][1] is None and (s[2] - s[1] + 1) * DT < .6:
            s[0] = None
    out = []
    prev_dir, prev_tgt, free_at = None, None, sh.times[0]
    for lab, i0, i1 in segs:
        if lab is None:
            prev_tgt = None
            continue
        tgt, hid = lab
        L = length_m(tgt)
        tb = build_of(kf, tgt)
        mom = next((o for o in owed if o['hid'] == hid), None) if hid else None
        region = INTENT_AIM.get((kf, key, tgt))
        if mom:
            at, to = mom['at'], mom['to']
            region = nearest_region(tb, L, at)
        elif region:
            at, to = regions(tb, L)[region], None
        else:
            mid = (i0 + i1) // 2
            region, at = presented(tb, L, to_local(sh.fr[tgt][mid], sh.fr[key][mid][0]))
            to = None
        # the steps chose by the face she presented then; the gun lays on this one aim point the whole
        # engagement, so it fires only while that point is inside the mount's arc (the firing-arc lint)
        lays = lambda i_: (bears(mount, to_local(sh.fr[key][i_], to_world(sh.fr[tgt][i_], at)))[0] and not
                           self_masked(build_of(kf, key), turret, mount, to_local(sh.fr[key][i_], to_world(sh.fr[tgt][i_], at))))
        while i1 > i0 and not lays(i1):
            i1 -= 1
        while i0 < i1 and not lays(i0):
            i0 += 1
        if not lays(i0):
            continue
        hit, walk, d, fam = _accuracy(kf, sh, key, mount, weapon, tgt, i0, i1, at, feeder)
        a = to_local(sh.fr[key][i0], to_world(sh.fr[tgt][i0], at))
        na = math.sqrt(sum(c * c for c in a)) or 1
        dirn = [c / na for c in a]
        if prev_dir is None:
            r = math.radians(mount[1])
            prev_dir = [math.cos(r), math.sin(r), 0.0]
        ang = math.degrees(math.acos(max(-1.0, min(1.0, sum(dirn[k] * prev_dir[k] for k in range(3))))))
        slew = 0.0 if mount[3] == HP or tgt == prev_tgt else min(3.0, ang / max(1.0, turn_rate(weapon)))
        prev_dir, prev_tgt = dirn, tgt
        # the gun starts its swing when it comes free of its last target, not when the new one
        # enters its arc: it trains toward the arc edge meanwhile
        start, end = max(sh.times[i0], free_at + slew), sh.times[i1]     # its last step that bears: no further
        free_at = end
        if mom:                      # the decisive burst: the trigger held through the moment, on target
            start, end = sh.times[i0], sh.times[i1]
            hit, walk = max(hit, MOMENT_HIT), round(.35 * L, 1)
            bursts = [(round(start, 2), round(end, 2))]
        else:
            if end - start < .3:
                continue
            bursts = _bursts(f'asharu-gunnery|{kf}|{key}|{turret}|{tgt}|{i0}', start, end, fam)
        el, az, line = _attitude_span(sh, key, mount, tgt, at, i0, i1)
        out.append(dict(shooter=key, turret=turret, weapon=weapon, target=tgt, region=region,
                        aim=tuple(round(c, 2) for c in at), walk_to=tuple(round(c, 2) for c in to) if to else None,
                        walk_r=walk_r(hit, line, at, L, seed=f'{kf}|{key}|{turret}|{tgt}|{i0}'),
                        elevation_deg=el, traverse_deg=az,
                        start=round(start, 2), end=round(end, 2), bursts=bursts,
                        burst_s=DISCIPLINE[fam][0], gap_s=DISCIPLINE[fam][1], hit_share=hit, walk_m=walk,
                        flight_s=round(d / speed_of(weapon), 2), arrive=mom['t'] if mom else None,
                        kind='pd' if _pd_target(tgt) else 'hull', force=bool(mom),
                        ledger=hid, why=_why(kf, key, tgt, hid, region, d, weapon), range_m=round(d),
                        ledgers=sorted({o['hid'] for o in owed if o['target'] == tgt and hid and
                                        o['steps'] & set(range(i0, i1 + 1))})))
    merged = []                      # one engagement split by a step or two of something else is one
    for e in out:
        m = next((x for x in reversed(merged) if x['target'] == e['target'] and x['ledger'] == e['ledger']
                  and x['region'] == e['region']), None)
        if m is not None and e['start'] - m['end'] < .5 and m is merged[-1]:
            m['bursts'] = sorted(m['bursts'] + e['bursts'])
            m['end'] = e['end']
            continue
        merged.append(e)
    merged = [e for e in merged if not (e['ledger'] and e['end'] - e['start'] < .25 and
                                        sum(x['ledger'] == e['ledger'] for x in merged) > 1)]
    holds = _holds(sh, pick, reason)
    return tuple(merged), tuple(holds), tuple((o['hid'], o['ok'], o['why'], o['miss']) for o in owed)


def _why(kf, key, tgt, hid, region, d, weapon):
    if hid:
        return f'story: ledger {hid}, fired {d / speed_of(weapon):.1f} s ahead so it lands on time' + (', past the execution gate (force)' if d > reach_m(weapon) else '')
    if _pd_target(tgt):
        return 'point defence: a small craft inside 700 m'
    if INTENT.get(kf, {}).get((key, tgt)):
        return f'story: the shot has {key} on {tgt}' + (f', at her {region}' if region else '')
    if INTENT.get(kf, {}).get((tgt, key)):
        return 'answering the ship shooting at her'
    return 'closest threat in arc and range'


def _holds(sh, pick, reason):
    out, i = [], 0
    while i < len(pick):
        if pick[i] is None:
            j = i
            while j + 1 < len(pick) and pick[j + 1] is None:
                j += 1
            if (j - i + 1) * DT >= .5:
                why = max(set(reason[i:j + 1]), key=reason[i:j + 1].count) or 'no target'
                out.append((sh.times[i], round(sh.times[j] + DT, 2), why))
            i = j + 1
        else:
            i += 1
    return out


def _clip(e, t0, t1):
    if e['end'] <= t0 or e['start'] >= t1:
        return None
    bursts = [(max(a, t0), min(b, t1)) for a, b in e['bursts'] if b > t0 and a < t1]
    if not bursts:
        return None
    return dict(e, start=max(e['start'], t0), end=min(e['end'], t1), bursts=bursts)


def track(shot_kf, ship_key, turret, weapon, t0, t1):
    """What the mount is laid on through the window, continuous, for scene_fx.planned's slew:
    [(from, to, target, aim)] in sequence seconds, aim in the target's frame. Between engagements the
    gun is already swinging onto its next target (or holds on its last), so it trains through the
    gaps instead of snapping; None/[] as plan()."""
    engs = plan(shot_kf, ship_key, turret, weapon, t0, t1)
    if not engs:
        return engs
    out, t = [], t0
    for e in engs:
        if e['start'] > t:           # the gap before it: already on the way to it
            out.append((t, e['start'], e['target'], e['aim']))
        out.append((max(t, e['start']), e['end'], e['target'], e['aim']))
        t = max(t, e['end'])
    if t < t1:                       # after its last: on that target while she lives, else the nearest living hostile
        last = engs[-1]
        k = killed(last['target'])
        if k is not None and k < t1:  # her rounds are still in flight to the kill: hold until it lands
            if k > t:
                out.append((t, k, last['target'], last['aim']))
                t = k
            sh = _shot(base_kf(shot_kf))
            i = sh.i(t)
            alive = [h for h in sh.ships if side(h) != side(ship_key) and (killed(h) is None or killed(h) > t)]
            if alive:
                near = min(alive, key=lambda h: math.dist(sh.fr[ship_key][i][0], sh.fr[h][i][0]))
                out.append((t, t1, near, (0, 0, 0)))
                return [r for r in out if r[1] > r[0]]
        out.append((t, t1, last['target'], last['aim']))
    return [r for r in out if r[1] > r[0]]


def plan(shot_kf, ship_key, turret, weapon, t0, t1):
    """The engagements one mount fires between sequence times t0 and t1, or None for no opinion
    (fall back to the blockout's fx entry). See the module docstring for the dict's fields."""
    if shot_kf not in bb.SHOTS:
        return None
    kf = base_kf(shot_kf)
    if kf not in bb.SHOTS or 'camera' not in bb.SHOTS[kf]:
        return None
    if ship_key not in bb.SHOTS[kf]['ships']:
        return None
    if kf in CEASEFIRE:
        return []
    engs, _holds_, _owed = _turret_plan(kf, ship_key, turret, weapon)
    out = [_clip(e, t0, t1) for e in engs]
    return [e for e in out if e]


# ====================================================================================== whole shots
def armed(kf):
    """(ship, turret, weapon) for every gun in a shot."""
    out = []
    for key in bb.SHOTS[kf]['ships']:
        b = build_of(kf, key)
        for turret in sorted(mounts_of(kf, key)):
            out.append((key, turret, weapon_of(b, turret) if b in MOUNTS else 'lightmg'))
    return out


@lru_cache(maxsize=None)
def shot_plan(kf):
    """Every engagement in a shot, plus every hold of fire and every ledger moment's status:
    dict(engagements=[...], holds=[(ship, turret, t0, t1, why)], owed=[(hit id, ok, why)])."""
    kf = base_kf(kf)
    engs, holds = [], []
    if kf in CEASEFIRE or 'camera' not in bb.SHOTS[kf]:
        return dict(engagements=[], holds=[('*', None) + window(kf) + (CEASEFIRE.get(kf, 'no camera'),)], owed=[],
                    opening={}, missiles=missiles(kf))
    t0, t1 = window(kf)
    for key, turret, weapon in armed(kf):
        e, h, o = _turret_plan(kf, key, turret, weapon)
        engs += [x for x in (_clip(x, t0 - LEAD_IN, t1) for x in e)
                 if x and (x['end'] + x['flight_s'] > t0)]      # its rounds land inside the shot
        holds += [(key, turret) + x for x in h]
    per = {}
    for key in bb.SHOTS[kf]['ships']:
        for tu in sorted(mounts_of(kf, key)):
            for o in _moment_reach(kf, key, tu):
                per.setdefault(o['hid'], []).append((key, tu, o))
    owed_rows = []
    for hid, rows in per.items():
        got = [(k, tu) for k, tu, o in rows if o['ok']]
        if got:
            owed_rows.append((hid, True, ', '.join(f'{k} t{tu}' for k, tu in got)))
            continue
        k, tu, o = min(rows, key=lambda r: (r[2]['miss'] is None, r[2]['miss'] or 0))
        owed_rows.append((hid, False, f'{k} t{tu} nearest: {o["why"]}'))
    return dict(engagements=sorted(engs, key=lambda e: (e['start'], e['shooter'], e['turret'])),
                holds=holds, owed=owed_rows, opening=opening(kf), missiles=missiles(kf))


def missiles(kf):
    """The shot's missile salvos (the blockout's 'missiles' entries, which scene_fx flies as Harpoons):
    [(shooter, target)]. What reaches at the ranges no gun does."""
    return [(f[1], f[2]) for f in bb.SHOTS[kf].get('fx', ()) if f[0] == 'missiles']


def opening(kf):
    """For every armed ship in the shot, when its guns first have a hostile in reach and in arc (any
    mount), and the nearest hostile then: {ship: (t or None, target, metres)}. None: never in the
    shot - only missiles reach, or nothing does."""
    sh = _shot(kf)
    out = {}
    for key in sh.ships:
        mts = mounts_of(kf, key)
        if not mts:
            continue
        b = build_of(kf, key)
        best = None
        for i, t in enumerate(sh.times):
            if t < sh.t0 - LEAD_IN or best:
                break
            for tu, m in mts.items():
                w = weapon_of(b, tu) if b in MOUNTS else 'lightmg'
                for tgt in sh.ships:
                    if side(tgt) == side(key) or not _alive(tgt, t) or _held(sh, key, tu, t):
                        continue
                    ok, d, _w = _valid(sh, key, tu, m, w, tgt, i, (0, 0, 0))
                    if ok and (best is None or d < best[2]):
                        best = (t, tgt, round(d))
        if best is None:
            near = min(((math.dist(sh.fr[key][0][0], sh.fr[k][0][0]), k) for k in sh.ships if side(k) != side(key)),
                       default=None)
            out[key] = (None, near[1] if near else None, round(near[0]) if near else None)
        else:
            out[key] = best
    return out


def arrival_windows(kf, shooter, target, at):
    """When the shooter's rounds can land on `at` (target frame, metres) in this shot: {turret:
    [(first, last), ...]} sequence seconds of arrival, fired from a step where the mount bears with a
    clear line within the gate. For the ledger, to time a gunfire hit the guns can deliver."""
    kf = base_kf(kf)
    sh = _shot(kf)
    b = build_of(kf, shooter)
    out = {}
    for tu, m in sorted(mounts_of(kf, shooter).items()):
        w = weapon_of(b, tu) if b in MOUNTS else 'lightmg'
        arr = []
        for i, tt in enumerate(sh.times):
            if _held(sh, shooter, tu, tt):
                continue
            ok, d, _w = _valid(sh, shooter, tu, m, w, target, i, tuple(at), force=True)
            if ok:
                arr.append(round(tt + d / speed_of(w), 1))
        runs = []
        for a in sorted(arr):
            if runs and a - runs[-1][1] <= .3:
                runs[-1][1] = a
            else:
                runs.append([a, a])
        out[tu] = [tuple(r) for r in runs]
    return out


def expected_hits(eng):
    """Rounds fired and rounds expected to strike over an engagement's bursts, from the cadence:
    dict(rounds, hits, damage) (damage in the game's per-shot units)."""
    burst, gap, cool, charge, _speed = CADENCE.get(eng['weapon'], (1, 0, .5, 0, 400))
    cycle = max(.05, (burst - 1) * gap + cool + charge + (gap if burst > 1 else 0))
    rounds = sum(max(1, int((b - a) / cycle * burst + .999)) for a, b in eng['bursts'])
    hits = rounds * eng['hit_share']
    return dict(rounds=rounds, hits=round(hits, 1), damage=round(hits * GAME.get(eng['weapon'], (0, 0, 25))[2]))


def check():
    """Rows the plan cannot satisfy: ledger moments with no mount that bears, and cadence drift."""
    rows = []
    for kf in bb.SHOTS:
        if bb.SHOTS[kf].get('span_of') or 'camera' not in bb.SHOTS[kf]:
            continue
        for hid, ok, why in shot_plan(kf)['owed']:
            if not ok:
                rows.append(f'{kf}: ledger {hid} not deliverable ({why})')
    seen = {m[5] for m in moments()}
    planned = {h for kf in bb.SHOTS if not bb.SHOTS[kf].get('span_of') and 'camera' in bb.SHOTS[kf]
               for e in shot_plan(kf)['engagements'] for h in e['ledgers']}
    for hid in sorted(seen - planned):
        rows.append(f'ledger {hid}: no engagement delivers it')
    return rows


# ====================================================================================== CLI
def table(kf):
    p = shot_plan(kf)
    spec = bb.SHOTS[kf]
    lines = [f"### {spec['shot']} ({kf}) {window(kf)[0]:g}-{window(kf)[1]:g} s: {spec['title']}", '']
    if not p['engagements']:
        lines += [f"Nobody fires: {p['holds'][0][-1] if p['holds'] else 'no hostile in range'}.", '']
        return '\n'.join(lines)
    lines += ['| ship | turret | weapon | target | aim | window | hit | bursts | intent |',
              '|---|---|---|---|---|---|---|---|---|']
    for e in p['engagements']:
        x = expected_hits(e)
        lines.append(f"| {e['shooter']} | t{e['turret']} | {e['weapon']} | {e['target']} | {e['region']} | "
                     f"{e['start']:.1f}-{e['end']:.1f} | {e['hit_share']:.0%} | {len(e['bursts'])} ({x['rounds']} rds) | "
                     f"{e['why']} ({e['range_m']} m) |")
    sil = {}
    for key, turret, a, b, why in p['holds']:
        if why.startswith('out of') or why.startswith('line') or why == 'no hostile in the shot':
            continue
        sil.setdefault((key, why), []).append(turret)
    if sil:
        lines += ['', 'Silent: ' + '; '.join(f"{k} ({', '.join(f't{t}' for t in ts)}) - {why}"
                                            for (k, why), ts in sil.items())]
    bad = [o for o in p['owed'] if not o[1]]
    if bad:
        lines += ['', 'Ledger moments the geometry cannot deliver: ' + '; '.join(f'{h} ({w})' for h, _, w in bad)]
    return '\n'.join(lines + [''])


def _kind(e):
    if e['ledger']:
        return 'story moment'
    if e['kind'] == 'pd':
        return 'point defence'
    return 'story order' if e['why'].startswith('story') else 'doctrine'


def summary(kf):
    """One row per ship, target and intent: the turrets that take part and the span they fire over."""
    p = shot_plan(kf)
    spec = bb.SHOTS[kf]
    lines = [f"#### {spec['shot']} ({kf}) {window(kf)[0]:g}-{window(kf)[1]:g} s: {spec['title']}", '']
    if not p['engagements']:
        return '\n'.join(lines + [f"Nobody fires: {p['holds'][0][-1] if p['holds'] else 'no target'}.", ''])
    rows = {}
    for e in p['engagements']:
        k = (e['shooter'], e['target'], _kind(e))
        r = rows.setdefault(k, dict(turrets=set(), t0=e['start'], t1=e['end'], aims=set(), hits=[], ids=set(),
                                    rds=0, whys=set()))
        r['turrets'].add(e['turret'])
        r['t0'], r['t1'] = min(r['t0'], e['start']), max(r['t1'], e['end'])
        r['aims'].add(e['region'])
        r['hits'].append(e['hit_share'])
        r['rds'] += expected_hits(e)['rounds']
        if e['ledger']:
            r['ids'].update(e['ledgers'])
        else:
            r['whys'].add(e['why'].split(',')[0])
    lines += ['| ship | turrets | target | aim | window s | hit | rounds | intent |', '|---|---|---|---|---|---|---|---|']
    for (a, b, kind), r in sorted(rows.items(), key=lambda kv: kv[1]['t0']):
        intent = kind + (': ' + ', '.join(sorted(r['ids'])) if r['ids'] else
                         ('' if kind != 'doctrine' else ': ' + '; '.join(sorted(r['whys']))))
        lines.append(f"| {a} | {', '.join(f't{t}' for t in sorted(r['turrets'], key=str))} | {b} | "
                     f"{', '.join(sorted(r['aims']))} | {r['t0']:.1f}-{r['t1']:.1f} | "
                     f"{min(r['hits']):.0%}-{max(r['hits']):.0%} | {r['rds']} | {intent} |")
    sil = {}
    for key, turret, a, b, why in p['holds']:
        if why.startswith('out of') or why.startswith('line') or why == 'no hostile in the shot':
            continue
        sil.setdefault((key, why), set()).add(turret)
    if sil:
        lines += ['', 'Silent: ' + '; '.join(f"{k} ({', '.join(f't{t}' for t in sorted(ts, key=str))}): {why}"
                                            for (k, why), ts in sil.items())]
    if p['missiles']:
        lines += ['', 'Missiles: ' + '; '.join(f'{a} -> {b} (Harpoons)' for a, b in p['missiles'])]
    op = [f"{k} {('from %.1f s on ' % v[0]) + v[1] if v[0] is not None else 'never (nearest %s at %s m at the cut)' % (v[1], v[2])}"
          for k, v in sorted(p['opening'].items(), key=lambda kv: (kv[1][0] is None, kv[1][0] or 0))]
    if op:
        lines += ['', 'Guns in reach: ' + '; '.join(op)]
    bad = [o for o in p['owed'] if not o[1]]
    if bad:
        lines += ['', 'Cannot deliver: ' + '; '.join(f'{h} ({w})' for h, _, w in bad)]
    return '\n'.join(lines + [''])


ORDER = ['k0', 'k1', 'm2', 'k1b', 'k2', 'm5', 'k3', 'k3c', 'k3d', 'k3e', 'k3b', 'k4', 'k4b', 'k5', 'm11', 'k6']


def main(argv):
    if '--table' in argv:
        kfs = [a for a in argv[argv.index('--table') + 1:] if not a.startswith('--')] or ORDER
        print('\n'.join(table(k) for k in kfs))
    if '--summary' in argv:
        kfs = [a for a in argv[argv.index('--summary') + 1:] if not a.startswith('--')] or ORDER
        print('\n'.join(summary(k) for k in kfs))
    if '--check' in argv:
        rows = check()
        if asharu_damage is None:
            rows.insert(0, 'asharu_damage is not importable: no ledger moments planned, no kills from it')
        print('\n'.join(rows) if rows else 'every ledger gunfire moment is delivered')
    if '--json' in argv:
        path = Path(argv[argv.index('--json') + 1])
        path.write_text(json.dumps({k: shot_plan(k) for k in ORDER}, indent=1, default=list))
        print('wrote', path)


if __name__ == '__main__':
    main(sys.argv[1:])
