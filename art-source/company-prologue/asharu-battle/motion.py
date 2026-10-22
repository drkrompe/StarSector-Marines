"""Asharu battle: how a ship moves around its station, so a fleet never flies in lockstep.

Each ship's spec in battle_blockout.py is its station: where it sits in the formation (or on its
attack run) and its attitude there. drift(key, station, t) is the same ship t seconds into the
cinematic, moved about that station by a seeded, deterministic motion:

- it weaves across and over its line, and its spacing to its neighbours breathes fore and aft;
- its heading and pitch follow its velocity, so a weave reads as a course being flown;
- it banks into each turn, from its sideways acceleration, and its throttle surges as it closes
  up and eases off as it falls back.

How much, and how fast, is the ship's mass. Frigates jink: big, quick weaves and hard banks.
Destroyers and cruisers swing wider and slower. Freighters, tankers and tenders barely wander and
barely bank. The class comes from the game's own hull size, with civilian hulls a class heavier.

The seed is the ship's key, so two ships of one build (three Lashers, two Wolves (P)) never move
alike, and a key frame stills one instant of a motion that an animation can play.
"""
import math
import zlib

import roster

V_REF = 90.0        # m/s: the formation's relative cruising speed, which turns a weave into a heading
G_BANK = 12.0       # m/s^2 of sideways acceleration that reads as a 45 degree bank

# per class: across, over and fore-aft weave amplitude (m), base period (s), bank limit (deg),
# throttle surge (fraction)
CLASSES = {
    'frigate':   dict(across=55.0, over=28.0, fore=45.0, period=13.0, bank=32.0, surge=.25),
    'destroyer': dict(across=38.0, over=15.0, fore=28.0, period=24.0, bank=14.0, surge=.15),
    'cruiser':   dict(across=22.0, over=9.0, fore=18.0, period=36.0, bank=7.0, surge=.10),
    'freighter': dict(across=12.0, over=5.0, fore=14.0, period=50.0, bank=3.0, surge=.06),
}

_CIVILIAN = ('freighter', 'tanker', 'tender', 'liner', 'transport', 'tug')   # 'combat freighter' is not one
_HEAVIER = {'frigate': 'destroyer', 'destroyer': 'cruiser', 'cruiser': 'freighter', 'freighter': 'freighter'}
_SIZE = {'FIGHTER': 'frigate', 'FRIGATE': 'frigate', 'DESTROYER': 'destroyer', 'CRUISER': 'cruiser', 'CAPITAL_SHIP': 'freighter'}
_MEMBERS = {m['key']: m for m in roster.MERCHANT + roster.PIRATE + roster.REFERENCES + roster.DRONES}
_CLASS_CACHE = {}


def mass_class(key):
    """The ship's motion class, from the game's hull size; a civilian hull moves one class heavier."""
    if key not in _CLASS_CACHE:
        spec = roster.game_spec(_MEMBERS[key]['game'])
        cls = _SIZE.get(spec['hull_size'], 'destroyer')
        des = (spec['designation'] or '').lower()
        if 'combat' not in des and any(w in des for w in _CIVILIAN):
            cls = _HEAVIER[cls]
        _CLASS_CACHE[key] = cls
    return _CLASS_CACHE[key]


def _waves(key, axis, period):
    """Two seeded sines on one axis: (amplitude fraction, angular frequency, phase) for each."""
    h = zlib.crc32(f'{key}|{axis}'.encode())
    r = [((h >> s) & 0xFFFF) / 65535.0 for s in (0, 8, 16, 4)]
    w1 = 2 * math.pi / (period * (.8 + .5 * r[0]))
    return [(1.0, w1, 2 * math.pi * r[1]), (.3, w1 * (1.6 + .5 * r[2]), 2 * math.pi * r[3])]


def _eval(waves, t, amp):
    """Position, velocity and acceleration of a sum of sines at time t."""
    p = v = a = 0.0
    for f, w, ph in waves:
        p += amp * f * math.sin(w * t + ph)
        v += amp * f * w * math.cos(w * t + ph)
        a -= amp * f * w * w * math.sin(w * t + ph)
    return p, v, a


def drift(key, station, t, scale=1.0):
    """The ship t seconds in, moved about its station. Station: (x, y, z, heading, pitch, roll,
    throttle) in the battle frame (+X prograde, +Y north, +Z up). scale multiplies the weave, for
    a shot that wants a ship steadier or looser than its class."""
    x, y, z, hd, pt, rl, thr = station
    c = CLASSES[mass_class(key)]
    hr = math.radians(hd)
    fwd = (math.cos(hr), math.sin(hr))           # the ship's own fore and port, in the horizontal
    port = (-math.sin(hr), math.cos(hr))
    fa, fv, fa_acc = _eval(_waves(key, 'fore', c['period'] * 1.6), t, c['fore'] * scale)
    sa, sv, s_acc = _eval(_waves(key, 'across', c['period']), t, c['across'] * scale)
    oa, ov, _ = _eval(_waves(key, 'over', c['period'] * 1.3), t, c['over'] * scale)
    dx, dy = fwd[0] * fa + port[0] * sa, fwd[1] * fa + port[1] * sa
    speed = V_REF + fv                         # along her own line
    d_hd = math.degrees(math.atan2(sv, speed))  # the weave across is a heading change
    d_pt = math.degrees(math.atan2(ov, math.hypot(speed, sv)))
    # bank into the turn: accelerating to port (a left turn) puts the port side down (negative roll)
    bank = -math.degrees(math.atan2(s_acc, G_BANK)) * (c['bank'] / 45.0)
    bank = max(-c['bank'], min(c['bank'], bank))
    surge = max(0.0, min(1.0, thr + c['surge'] * fv / max(1.0, abs(fv) + 6.0) if thr > 0 else thr))
    return (x + dx, y + dy, z + oa, hd + d_hd, pt + d_pt, rl + bank, surge)


def fleet_at(ships, t, scale=1.0, keep=()):
    """Every ship of a dict at time t, except those named in keep (authored poses left as posed)."""
    return {k: (v if k in keep else drift(k, v, t, scale)) for k, v in ships.items()}


def _bez(p, s):
    """A cubic Bezier through four points at s, and its derivative."""
    a, b = 1 - s, s
    pos = [a ** 3 * p[0][i] + 3 * a * a * b * p[1][i] + 3 * a * b * b * p[2][i] + b ** 3 * p[3][i] for i in range(3)]
    der = [3 * a * a * (p[1][i] - p[0][i]) + 6 * a * b * (p[2][i] - p[1][i]) + 3 * b * b * (p[3][i] - p[2][i])
           for i in range(3)]
    return pos, der


def boarding_run(launch, leave, seat, arrive, seated, t0, t1, t):
    """A boarding boat t seconds in, on its run from its mother craft to the prize (owner, 2026-10-22).

    launch: the point it leaves from, out of the camera's sight (under or behind its mother craft);
    leave: the unit direction it drops away along; seat: the point it sets down on, and arrive: the
    unit direction it comes in along (astern of the seat, along the prize's deck); seated: the
    station once down. The path is a cubic curve from launch to seat, flown between t0 and t1 on a
    burn, coast and brake: it burns out along the curve for the first 30%, coasts, yaws end for end
    (from 58% to 70%) and burns against its motion for the last 30%, so it arrives tail first, nose to
    the way it came, which is the way it sits down (seated's heading). Before t0 it is at the launch
    point, at rest; from t1 it is seated."""
    u = max(0.0, min(1.0, (t - t0) / (t1 - t0)))
    if u >= 1.0:
        return seated
    reach = math.dist(launch, seat)
    pts = (launch, [launch[i] + leave[i] * reach * .35 for i in range(3)],
           [seat[i] - arrive[i] * reach * .45 for i in range(3)], seat)
    s = u * u * (3 - 2 * u)                       # speeding up, then slowing down: the burns
    pos, der = _bez(pts, s)
    v = [d * 6 * u * (1 - u) for d in der] if 0 < u < 1 else der
    if math.hypot(*v) < 1e-6:
        v = der
    hd = math.degrees(math.atan2(v[1], v[0]))
    pt = math.degrees(math.atan2(v[2], math.hypot(v[0], v[1])))
    f = max(0.0, min(1.0, (u - .58) / .12))       # the flip, yawed end for end over 12% of the run
    f = f * f * (3 - 2 * f)
    hd, pt = hd + 180.0 * f, pt * (1 - 2 * f)
    thr = 1.0 if u < .3 or u > .7 else .15        # burn out, coast with the drives idling, brake
    return (pos[0], pos[1], pos[2], hd, pt, 0.0, thr)
