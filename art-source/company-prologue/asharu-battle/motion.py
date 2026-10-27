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
# m/s^2 of acceleration that takes a class's drives to full burn (the thrust model: a ship coasting
# in orbit has its drives at idle, one gaining speed or hauling round a turn burns in proportion)
A_FULL = {'frigate': 25.0, 'destroyer': 15.0, 'cruiser': 10.0, 'freighter': 5.0}
IDLE = .08

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
    """Every ship of a dict at time t (live(): her pose at t, and her motion through a shot around
    it), except those named in keep (authored poses left as posed)."""
    return {k: (v if k in keep else live(k, v, t, scale)) for k, v in ships.items()}


# No ship holds a fixed spot (owner, 2026-10-23, S6), and none flies sideways (S4). A fleet ship
# through a shot steers her weave: drift()'s heading, pitch, bank and throttle, played livelier for the
# heavy hulls (LIVE_GAIN), and she moves only along her own nose, at her class's CREEP seeded per ship
# (0.6 to 1.4 of it, so a formation shifts against itself) and surging with her throttle. drift()'s
# own position weave is a convoy-frame offset that reads as crabbing in a still frame, so it is used
# at the key time only: there she is exactly where drift() puts her, and every still keeps its framing.
CREEP = {'frigate': 18.0, 'destroyer': 12.0, 'cruiser': 8.0, 'freighter': 5.0}
LIVE_GAIN = {'frigate': 1.0, 'destroyer': 1.5, 'cruiser': 2.5, 'freighter': 3.0}
LIVE_DT = .05


def live(key, st, t_key, scale=1.0):
    cls = mass_class(key)
    g = LIVE_GAIN[cls]
    k = .6 + .8 * (zlib.crc32(f'{key}|creep'.encode()) % 1000) / 999.0
    creep = CREEP[cls] * k if st[6] > 0 else 0.0     # dead drives: she drifts, she does not creep
    base = drift(key, st, t_key, scale)
    memo = {}

    def att(tt):
        d = drift(key, st, tt, scale)
        thr = max(d[6], .3) if creep else d[6]       # her drives show while she creeps
        return (base[3] + (d[3] - base[3]) * g, base[4] + (d[4] - base[4]) * g, base[5] + (d[5] - base[5]) * g, thr)

    def vel(tt):
        hd, pt, _, thr = att(tt)
        s = creep * (.7 + .6 * thr)
        hr, pr = math.radians(hd), math.radians(pt)
        return (s * math.cos(pr) * math.cos(hr), s * math.cos(pr) * math.sin(hr), s * math.sin(pr))

    def step(n):
        if n not in memo:
            dn = 1 if n >= 0 else -1
            m = max((q for q in memo if (q - n) * dn <= 0 and q * dn >= 0), key=abs, default=0)
            pos = memo.get(m, base[:3])
            for q in range(m, n, dn):                    # midpoint steps along her own heading
                v = vel(t_key + (q + .5 * dn) * LIVE_DT)
                pos = tuple(pos[c] + v[c] * LIVE_DT * dn for c in range(3))
                memo[q + dn] = pos
            memo[n] = pos
        return memo[n]

    def at(tt):
        # between her LIVE_DT steps by their share, not the nearest one: 0.05 s steps rounded at 24 fps held
        # every ship (and a camera riding her) still one frame in six (owner, 2026-10-27, motion lint)
        x_ = (tt - t_key) / LIVE_DT
        n0 = math.floor(x_)
        a, c = step(n0), step(n0 + 1)
        w = x_ - n0
        return (*(a[k] + (c[k] - a[k]) * w for k in range(3)), *att(tt))
    return Pose(at(t_key), t_key, at)


def _bez(p, s):
    """A cubic Bezier through four points at s, and its derivative."""
    a, b = 1 - s, s
    pos = [a ** 3 * p[0][i] + 3 * a * a * b * p[1][i] + 3 * a * b * b * p[2][i] + b ** 3 * p[3][i] for i in range(3)]
    der = [3 * a * a * (p[1][i] - p[0][i]) + 6 * a * b * (p[2][i] - p[1][i]) + 3 * b * b * (p[3][i] - p[2][i])
           for i in range(3)]
    return pos, der


def boarding_run(launch, leave, seat, arrive, seated, t0, t1, t, hold=0.0):
    """A boarding boat t seconds in, on its run from its mother craft to the prize (owner, 2026-10-22).

    launch: the point it leaves from, out of the camera's sight (under or behind its mother craft);
    leave: the unit direction it drops away along; seat: the point it sets down on, and arrive: the
    unit direction it comes in along (astern of the seat, along the prize's deck); seated: the
    station once down. The path is a cubic curve from launch to seat, flown between t0 and t1 on a
    burn, coast and brake: it burns out along the curve for the first 30%, coasts, yaws end for end
    (from 58% to 70%) and burns against its motion for the last 30%, so it arrives tail first, nose to
    the way it came, which is the way it sits down (seated's heading). Before t0 it is at the launch
    point, at rest; from t1 it is seated.

    hold (seconds): the boat settles before it sets down (owner, 2026-10-23, S7: "somewhat still
    navigating into a stable position near their target"). The run then brakes to rest at a hold
    point HOLD_ASTERN m astern of the seat and HOLD_UP m above it, keeps station there for hold
    seconds (sub-metre drift, a degree of attitude, short RCS pulses on the throttle), and eases down
    onto the seat over SETTLE_S."""
    if hold <= 0.0:
        return _run(launch, leave, seat, arrive, seated, t0, t1, t)
    t_hold = t1 - SETTLE_S - hold
    over = [seat[i] - arrive[i] * HOLD_ASTERN + (HOLD_UP if i == 2 else 0.0) for i in range(3)]
    if t < t_hold:
        return _run(launch, leave, over, arrive, (*over, seated[3], seated[4], 0.0, .2), t0, t_hold, t)
    if t >= t1:
        return seated
    import random
    rnd = random.Random('boarding hold|%.1f|%.1f' % (launch[0], launch[1]))
    ph = [rnd.uniform(0, 6.3) for _ in range(6)]
    per = [rnd.uniform(2.0, 4.0) for _ in range(6)]
    amp = [rnd.uniform(.3, .8) for _ in range(3)]
    if t < t1 - SETTLE_S:
        u, base = 0.0, over
    else:
        u = (t - (t1 - SETTLE_S)) / SETTLE_S
        u = u * u * (3 - 2 * u)
        base = [over[i] + (seat[i] - over[i]) * u for i in range(3)]
    still = 1.0 - u                                              # the drift dies as she sets down
    pos = [base[i] + amp[i] * still * math.sin(2 * math.pi * t / per[i] + ph[i]) for i in range(3)]
    hd = seated[3] + 1.2 * still * math.sin(2 * math.pi * t / per[3] + ph[3])
    pt = seated[4] + .8 * still * math.sin(2 * math.pi * t / per[4] + ph[4])
    rl = .8 * still * math.sin(2 * math.pi * t / per[5] + ph[5])
    # RCS: a short pulse every second or so, corrections against the drift
    k = int(t * 1.3)
    pulse = random.Random('rcs|%d|%.1f' % (k, launch[0])).uniform(.1, .45) if (t * 1.3 - k) < .25 else 0.0
    return (pos[0], pos[1], pos[2], hd, pt, rl, .1 + pulse * still + .2 * u)


HOLD_ASTERN, HOLD_UP, SETTLE_S = 14.0, 6.0, 3.0


def _run(launch, leave, seat, arrive, seated, t0, t1, t):
    """boarding_run's run itself: the cubic from launch to seat, burn, coast, flip and brake."""
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


# ======================================================================= flown motion (owner, 2026-10-22)
# The battle must move: every ship in every shot flies, and every camera has a motivated move. A
# key frame's pose is where a flight passes at the key frame's time, so a lit still is a sample of
# the same motion the preview plays (Pose.at(t)).

class Pose(tuple):
    """A station (x, y, z, heading, pitch, roll, throttle) at time t, that can pose the same ship at
    any other time: at(t). The motion preview plays at(); a key frame stills the tuple."""
    def __new__(cls, pose, t, at):
        o = tuple.__new__(cls, pose)
        o.t, o.at = t, at
        return o


def _orthonormal(m):
    """Rows of a near-rotation 3x3 (a blend of two close ones) made a rotation again, columns
    Gram-Schmidt in order: forward, then left, then up as their cross product."""
    c = [[m[r][k] for r in range(3)] for k in range(3)]
    def norm(v):
        n = math.sqrt(sum(x * x for x in v)) or 1.0
        return [x / n for x in v]
    f = norm(c[0])
    d = sum(c[1][i] * f[i] for i in range(3))
    l = norm([c[1][i] - d * f[i] for i in range(3)])
    u = [f[1] * l[2] - f[2] * l[1], f[2] * l[0] - f[0] * l[2], f[0] * l[1] - f[1] * l[0]]
    return [[f[r], l[r], u[r]] for r in range(3)]


def _rot(hd, pt, rl):
    """Rows of Rz(hd) Ry(-pt) Rx(rl), battle_blockout.frame_of's rotation."""
    h, p, r = math.radians(hd), math.radians(-pt), math.radians(rl)
    ch, sh, cp, sp, cr, sr = math.cos(h), math.sin(h), math.cos(p), math.sin(p), math.cos(r), math.sin(r)
    return [[ch * cp, ch * sp * sr - sh * cr, ch * sp * cr + sh * sr],
            [sh * cp, sh * sp * sr + ch * cr, sh * sp * cr - ch * sr],
            [-sp, cp * sr, cp * cr]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _angles(m):
    """(heading, pitch, roll) of a _rot matrix."""
    return (math.degrees(math.atan2(m[1][0], m[0][0])), math.degrees(math.asin(max(-1.0, min(1.0, m[2][0])))),
            math.degrees(math.atan2(m[2][1], m[2][2])))


def _axis_angle(axis, deg):
    """Rotation by deg about body axis 0 (x, fore), 1 (y, port) or 2 (z, up)."""
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 0:
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == 1:
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _body_step(m, roll, pitch, yaw):
    """m turned by small body-frame angles (degrees): yaw to port, pitch nose up, roll about the fore axis."""
    m = _mul(m, _axis_angle(2, yaw))
    m = _mul(m, _axis_angle(1, -pitch))
    return _mul(m, _axis_angle(0, roll))


def _ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def _phase_at(phases, t, blend):
    """The flight's commanded state at t: each phase from its start 't' on, blended in over blend
    seconds so nothing snaps. Fields: v (m/s along the nose), yaw/pitch/roll (deg/s, body frame),
    thr (0-1; else from acceleration), bank (auto-bank factor, default 1). An unset rate is 0; an
    unset v, thr or bank carries over from the phase before."""
    keys = ('v', 'yaw', 'pitch', 'roll', 'thr', 'bank')
    cur = dict(v=0.0, yaw=0.0, pitch=0.0, roll=0.0, thr=None, bank=1.0)
    for i, ph in enumerate(phases):
        if t < ph['t']:
            break
        nxt = {k: ph.get(k, cur[k] if k in ('v', 'thr', 'bank') else 0.0) for k in keys}
        w = _ease((t - ph['t']) / blend) if i else 1.0
        cur = {k: (nxt[k] if (cur[k] is None or nxt[k] is None) else cur[k] + (nxt[k] - cur[k]) * w) for k in keys}
    return cur


JUMP_S = .4


def _hit_offsets(hits, jumps, tt):
    """Summed effect of hits and jumps at tt: battle-frame shove (m), body-frame turn (roll, pitch,
    yaw deg) and body-frame shove (m, fore/port/up). A hit with kick=True is transient: it peaks at
    its push/spin/bpush values decay seconds after t and dies away (a gun's recoil); otherwise it is
    a lasting shove that builds over decay seconds (spin and push in deg/s and m/s). held=True: the
    spin is a tumble that keeps its rate (vacuum has no drag to stop it), reached over the quarter
    second the impulse takes; push still builds over decay (owner, 2026-10-23, S9b: no pinwheel)."""
    dp, ang, bdp = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
    for h in hits:
        if tt <= h['t']:
            continue
        tau = h.get('decay', 2.0)
        x = tt - h['t']
        k = (x / tau) * math.exp(1 - x / tau) if h.get('kick') else tau * (1 - math.exp(-x / tau))
        ks = x - .25 * (1 - math.exp(-x / .25)) if h.get('held') else k
        for n in range(3):
            dp[n] += h.get('push', (0, 0, 0))[n] * k
            ang[n] += h.get('spin', (0, 0, 0))[n] * ks
            bdp[n] += h.get('bpush', (0, 0, 0))[n] * k
    for tj, off in jumps:
        # A Phase Skimmer jump is a dash, not a teleport (owner, 2026-10-23, S6c: a Wolf "pop in on
        # the bottom of the screen, like it was culled"): she crosses the offset in JUMP_S, eased,
        # so she streaks into frame rather than appearing in it.
        if tt > tj:
            k = _ease(min(1.0, (tt - tj) / JUMP_S))
            for n in range(3):
                dp[n] += off[n] * k
    return dp, ang, bdp


def recoil(times, kick=.6, shove=.4, decay=.12, every=None, until=None):
    """Hits for a gun's recoil: at each time (or every `every` s from times[0] to until) the nose
    kicks up kick degrees and the hull shoves back shove metres, gone within half a second."""
    if every:
        n = int((until - times[0]) / every)
        times = [times[0] + i * every for i in range(n + 1)]
    return [dict(t=t, kick=True, decay=decay, spin=(0.0, kick, 0.0), bpush=(-shove, 0.0, 0.0)) for t in times]


def _apply(p, m, hits, jumps, t, t_key):
    """Position p and rotation m with hits and jumps applied, relative to the key time."""
    (dp, ang, bdp), (dp0, ang0, bdp0) = _hit_offsets(hits, jumps, t), _hit_offsets(hits, jumps, t_key)
    ang = [ang[n] - ang0[n] for n in range(3)]
    bd = [bdp[n] - bdp0[n] for n in range(3)]
    if any(ang):
        m = _body_step(m, *ang)
    off = [dp[n] - dp0[n] + sum(m[n][k] * bd[k] for k in range(3)) for n in range(3)]
    return [p[n] + off[n] for n in range(3)], m


class _Curve:
    """A monotone cubic (Fritsch-Carlson) through (ts, ys): continuous in value and in rate, no
    overshoot past a key, carried on at the end rate beyond the ends. A key that is a local peak or trough is where the
    rate passes through zero; every other key is passed through at speed."""
    def __init__(self, ts, ys):
        self.ts, self.ys = list(ts), list(ys)
        n = len(ts)
        d = [(ys[i + 1] - ys[i]) / (ts[i + 1] - ts[i]) for i in range(n - 1)]
        m = [0.0] * n
        if n > 1:
            m[0], m[-1] = d[0], d[-1]
        for i in range(1, n - 1):
            if d[i - 1] * d[i] <= 0:
                m[i] = 0.0
            else:
                w1 = 2 * (ts[i + 1] - ts[i]) + (ts[i] - ts[i - 1])
                w2 = (ts[i + 1] - ts[i]) + 2 * (ts[i] - ts[i - 1])
                m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
        self.m = m

    def _seg(self, t):
        ts = self.ts
        i = next((n for n in range(len(ts) - 1) if t < ts[n + 1]), len(ts) - 2)
        h = ts[i + 1] - ts[i]
        return i, h, (t - ts[i]) / h

    def __call__(self, t):
        ts, ys, m = self.ts, self.ys, self.m
        if len(ts) == 1:
            return ys[0]
        if t <= ts[0]:              # beyond the ends at the end rate: a turn carries on across the cut
            return ys[0] + self.m[0] * (t - ts[0])
        if t >= ts[-1]:
            return ys[-1] + self.m[-1] * (t - ts[-1])
        i, h, u = self._seg(t)
        u2, u3 = u * u, u * u * u
        return ((2 * u3 - 3 * u2 + 1) * ys[i] + (u3 - 2 * u2 + u) * h * m[i]
                + (-2 * u3 + 3 * u2) * ys[i + 1] + (u3 - u2) * h * m[i + 1])

    def slope(self, t):
        ts, ys, m = self.ts, self.ys, self.m
        if len(ts) == 1:
            return 0.0
        if t <= ts[0]:
            return m[0]
        if t >= ts[-1]:
            return m[-1]
        i, h, u = self._seg(t)
        u2 = u * u
        return ((6 * u2 - 6 * u) * ys[i] / h + (3 * u2 - 4 * u + 1) * m[i]
                + (-6 * u2 + 6 * u) * ys[i + 1] / h + (3 * u2 - 2 * u) * m[i + 1])


class Flight:
    """A ship flown by commanded body rates and speed (phases) through her key-frame pose at t_key:
    the motion is integrated outward from that pose, both ways in time, so the pose a key frame
    authored is exactly where the flight passes then.

    - phases: [dict(t=start, v=m/s, yaw=, pitch=, roll= deg/s, thr=)], in time order. A turn's yaw
      rate banks the ship into it (G_BANK, scaled by 'bank'); a roll rate rolls her besides (a
      roll-over onto her back).
    - hits: [dict(t=, push=(x, y, z) m/s, spin=(roll, pitch, yaw) deg/s, decay=s, thr=)]: a shove
      and a tumble that build over decay seconds; thr is her throttle after it (drives shot out);
      kick=True makes it transient (recoil(): a gun's kick).
    - jumps: [(t, (dx, dy, dz))]: a blink (a Wolf's Displacer), a step in position at t.
    - wobble: metres of seeded weave on top, so even a flown line is not a rail."""
    DT = 1.0 / 60.0

    def __init__(self, key, anchor, t_key, phases, hits=(), jumps=(), wobble=2.0, blend=.6):
        self.key, self.t_key, self.blend = key, t_key, blend
        self.phases = sorted(phases, key=lambda p: p['t'])
        self.hits, self.jumps, self.wobble = list(hits), list(jumps), wobble
        x, y, z, hd, pt, rl, thr = anchor
        self.thr_key = thr
        self.samples = {0: ((x, y, z), _rot(hd, pt, rl))}

    def _bank(self, t):
        c = _phase_at(self.phases, t, self.blend)
        return -math.degrees(math.atan2(math.radians(c['yaw']) * c['v'], G_BANK)) * .6 * (c['bank'] or 0.0)

    def _state(self, i):
        """Integrated position and attitude at step i (t = t_key + i DT), cached."""
        if i in self.samples:
            return self.samples[i]
        d = 1 if i > 0 else -1
        j = i - d
        while j not in self.samples:
            j -= d
        p, m = self.samples[j]
        while j != i:
            c = _phase_at(self.phases, self.t_key + (j + .5 * d) * self.DT, self.blend)
            dt = d * self.DT
            m = _body_step(m, c['roll'] * dt, c['pitch'] * dt, c['yaw'] * dt)
            p = tuple(p[k] + m[k][0] * c['v'] * dt for k in range(3))
            j += d
            self.samples[j] = (p, m)
        return p, m

    def _between(self, t):
        """Position and attitude at t, between the integrated steps either side of it (2026-10-23:
        taking the nearest step put a fast ship a whole step, 3.5 m at 210 m/s, off her own planned
        pose whenever a frame's time fell a hair either side of a half step, which at 24 fps is every
        other frame, and moved her in uneven two- and three-step strides)."""
        x = (t - self.t_key) / self.DT
        i0 = math.floor(x)
        f = x - i0
        p0, m0 = self._state(i0)
        if f < 1e-9:
            return p0, m0
        p1, m1 = self._state(i0 + 1)
        p = tuple(p0[k] + (p1[k] - p0[k]) * f for k in range(3))
        if m0 is None or m1 is None:
            return p, m0 if m1 is None else m1
        return p, _orthonormal([[m0[r][c] + (m1[r][c] - m0[r][c]) * f for c in range(3)] for r in range(3)])

    def __call__(self, t):
        p, m = self._between(t)
        p, m = _apply(p, m, self.hits, self.jumps, t, self.t_key)
        m = _body_step(m, self._bank(t) - self._bank(self.t_key), 0.0, 0.0)
        w = [0.0, 0.0, 0.0]
        if self.wobble:
            for n, axis in enumerate(('wx', 'wy', 'wz')):
                wv = _waves(self.key, axis, 9.0)
                w[n] = _eval(wv, t, self.wobble)[0] - _eval(wv, self.t_key, self.wobble)[0]
        hd, pt, rl = _angles(m)
        c = _phase_at(self.phases, t, self.blend)
        thr = c['thr']
        if thr is None:      # the drives follow the speed: burning to speed up, idling to hold it
            a = (_phase_at(self.phases, t + .25, self.blend)['v'] - _phase_at(self.phases, t - .25, self.blend)['v']) / .5
            thr = max(.05, min(1.0, self.thr_key + a / 40.0))
        for h in self.hits:
            if t > h['t'] and 'thr' in h:
                thr = h['thr']
        return tuple(p[n] + w[n] for n in range(3)) + (hd, pt, rl, thr)


class Keyed(Flight):
    """A ship flown through timed attitude keys: [dict(t=, hd=, pt=, rl=, v=, thr=)] (absolute
    degrees; a field left out carries the last key's). Her key-frame pose is a key itself, at
    t_key. Attitude eases from key to key (heading the short way round unless a key's heading says
    otherwise in full: 185 after 0 turns 185 to port); she flies where her nose points at v, so the
    path follows the manoeuvre. Throttle eases between the keys' and follows the thrust model on
    top (a burn while she gains speed, idling while she sheds it). Hits, jumps and wobble as Flight."""
    BANK_GAIN, BANK_MAX = .35, 20.0

    def __init__(self, key, anchor, t_key, keys, hits=(), jumps=(), wobble=2.0, v=None, thrust=None, bank=True):
        super().__init__(key, anchor, t_key, [dict(t=-1e9, v=0.0)], hits, jumps, wobble)
        self.thrust = thrust       # m/s^2 at full burn: the throttle is the motion's acceleration
        self.bank = bank
        x, y, z, hd, pt, rl, thr = anchor
        ks = sorted([dict(k) for k in keys] + [dict(t=t_key, hd=hd, pt=pt, rl=rl, thr=thr,
                                                     **({} if v is None else {'v': v}))], key=lambda k: k['t'])
        last = {}
        for k in ks:                      # carry fields forward, then back to the first key
            for f in ('hd', 'pt', 'rl', 'v', 'thr'):
                if f in k:
                    last[f] = k[f]
                elif f in last:
                    k[f] = last[f]
        for k in reversed(ks):
            for f in ('hd', 'pt', 'rl', 'v', 'thr'):
                if f in k:
                    last[f] = k[f]
                else:
                    k[f] = last.get(f, 0.0 if f != 'thr' else thr)
        merged = {}
        for k in ks:                      # one key per instant; the key frame's own pose wins a tie
            if k['t'] not in merged or abs(k['t'] - t_key) < 1e-9:
                merged[k['t']] = k
        self.keys = ks = [merged[tt] for tt in sorted(merged)]
        self._curves = {f: _Curve([k['t'] for k in ks], [k[f] for k in ks]) for f in ('hd', 'pt', 'rl', 'v', 'thr')}

    def attitude(self, t):
        """Heading, pitch, roll, speed and throttle at t: one continuous monotone cubic through all
        the keys per field (_Curve), so a turn keeps turning through a key instead of pausing there
        (the old per-span ease stopped every rate at every key: a straight inside each turn)."""
        return tuple(self._curves[f](t) for f in ('hd', 'pt', 'rl', 'v', 'thr'))

    def _state(self, i):
        if i in self.samples:
            return self.samples[i]
        d = 1 if i > 0 else -1
        j = i - d
        while j not in self.samples:
            j -= d
        p, _ = self.samples[j]
        while j != i:
            hd, pt, rl, v, _thr = self.attitude(self.t_key + (j + .5 * d) * self.DT)
            fwd = _rot(hd, pt, rl)
            p = tuple(p[k] + fwd[k][0] * v * d * self.DT for k in range(3))
            j += d
            self.samples[j] = (p, None)
        return p, None

    def __call__(self, t):
        p, _ = self._between(t)
        hd, pt, rl, v, thr = self.attitude(t)
        if self.thrust:      # the drives burn with the acceleration: speeding up, and hauling round a turn
            thr = max(IDLE, min(1.0, IDLE + (abs(self._accel(t)) + .5 * abs(v) * self._turn(t)) / self.thrust))
        elif thr > 0:        # the drives burn to gain speed and ease off to lose it
            thr = max(.05, min(1.0, thr + (self._accel(t) - self._accel(self.t_key)) / 40.0))
        if self.bank:        # bank into the turn with the lateral acceleration, on top of the keyed roll
            rl += self._bank_at(t) - self._bank_at(self.t_key)
        p, m = _apply(p, _rot(hd, pt, rl), self.hits, self.jumps, t, self.t_key)
        w = [0.0, 0.0, 0.0]
        if self.wobble:
            for n, axis in enumerate(('wx', 'wy', 'wz')):
                wv = _waves(self.key, axis, 9.0)
                w[n] = _eval(wv, t, self.wobble)[0] - _eval(wv, self.t_key, self.wobble)[0]
        for h in self.hits:
            if t > h['t'] and 'thr' in h:
                thr = h['thr']
        hd, pt, rl = _angles(m)
        return tuple(p[n] + w[n] for n in range(3)) + (hd, pt, rl, thr)

    def _accel(self, t):
        return self._curves['v'].slope(t)

    def _bank_at(self, t):
        """Bank (deg) for the lateral acceleration of the keyed turn: a heading turning to port (rising)
        rolls her port side down (negative), as drift's weave does."""
        a = self._curves['v'](t) * math.radians(self._curves['hd'].slope(t))
        b = -math.degrees(math.atan2(a, G_BANK)) * self.BANK_GAIN
        return max(-self.BANK_MAX, min(self.BANK_MAX, b))

    def _turn(self, t):
        """How fast her nose is swinging, rad/s."""
        a, b = _rot(*self.attitude(t - .05)[:3]), _rot(*self.attitude(t + .05)[:3])
        c = sum(a[k][0] * b[k][0] for k in range(3))
        return math.acos(max(-1.0, min(1.0, c))) / .1


def turn_jumps(at, t0, t1, fps=24, limit=45.0):
    """Frames where a pose function's angular velocity jumps: the change in angular velocity from one
    frame to the next, deg/s, above limit. [(t, jump deg/s)], for --check: a turn that stalls at a
    key, a snap between keys, and a hit's tumble (expected) all show here."""
    def omega(ta, tb):
        a, b = _rot(*at(ta)[3:6]), _rot(*at(tb)[3:6])
        r = _mul(_t(a), b)            # body-frame rotation from ta to tb
        ang = math.degrees(math.acos(max(-1.0, min(1.0, (r[0][0] + r[1][1] + r[2][2] - 1) / 2))))
        ax = (r[2][1] - r[1][2], r[0][2] - r[2][0], r[1][0] - r[0][1])
        n = math.hypot(*ax) or 1.0
        return [ang / (tb - ta) * c / n for c in ax]
    dt, out, prev = 1.0 / fps, [], None
    for i in range(int(round((t1 - t0) * fps))):
        ta = t0 + i * dt
        w = omega(ta, ta + dt)
        if prev is not None:
            j = math.dist(w, prev)
            if j > limit:
                out.append((round(ta, 3), round(j, 1)))
        prev = w
    return out


def keyed(key, anchor, t_key, keys, **kw):
    """A ship flown through attitude keys (Keyed), as her pose at her key time with at(t)."""
    f = Keyed(key, anchor, t_key, keys, **kw)
    return Pose(f(t_key), t_key, f)


def flight(key, anchor, t_key, phases, **kw):
    """A flown ship (Flight) as her pose at her key time, carrying at(t) for the preview."""
    f = Flight(key, anchor, t_key, phases, **kw)
    return Pose(f(t_key), t_key, f)


def held(key, anchor, t_key, wobble=1.5, thr=None):
    """A ship that keeps her place (moored, a hulk, a drifting prize) without being a photograph: a
    slow seeded weave. thr: a function of time for her throttle (drives lighting one by one)."""
    f = Flight(key, anchor, t_key, [dict(t=-1e9, v=0.0, thr=anchor[6])], wobble=wobble, blend=1.0)

    def at(t):
        p = f(t)
        return p if thr is None else p[:6] + (thr(t),)
    return Pose(at(t_key), t_key, at)


def riding(parent, offset, hd=0.0, pt=0.0, rl=0.0, thr=None):
    """A craft riding with another (clamped, docked, working about it): at offset (metres, the
    parent's frame) with its own heading, pitch and roll relative to the parent's, at every time
    the parent has. parent: a Pose (or a plain station, which then holds)."""
    def at(t):
        x, y, z, phd, ppt, prl, pthr = parent.at(t) if hasattr(parent, 'at') else parent
        m = _rot(phd, ppt, prl)
        o = [sum(m[i][k] * offset[k] for k in range(3)) for i in range(3)]
        hd_, pt_, rl_ = _angles(_mul(m, _rot(hd, pt, rl)))
        return (x + o[0], y + o[1], z + o[2], hd_, pt_, rl_, pthr if thr is None else thr)
    t0 = getattr(parent, 't', 0.0)
    return Pose(at(t0), t0, at)


def station(key, st, t_key, vel=(0.0, 0.0, 0.0), scale=1.0):
    """A formation ship on her station (drift's weave), the station moving at vel m/s in the battle
    frame from the key time: the convoy drawing ahead of a ship that has fallen out of it."""
    def at(tt):
        x, y, z, hd, pt, rl, thr = drift(key, st, tt, scale)
        return (x + vel[0] * (tt - t_key), y + vel[1] * (tt - t_key), z + vel[2] * (tt - t_key), hd, pt, rl, thr)
    return Pose(at(t_key), t_key, at)


def _t(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def carried(pose, parent):
    """A pose (a Pose, or a plain station) carried along by its parent's motion since the key time:
    a boat on a deck, a drone at its work, a tender under a keel. At the key time it is the pose as
    authored; after, it moves and turns rigidly with the parent."""
    t_key = getattr(pose, 't', getattr(parent, 't', 0.0))
    p0 = parent.at(t_key)
    r0t = _t(_rot(*p0[3:6]))

    def at(t):
        x, y, z, hd, pt, rl, thr = pose.at(t) if hasattr(pose, 'at') else pose
        p = parent.at(t)
        d = _mul(_rot(*p[3:6]), r0t)
        rel = (x - p0[0], y - p0[1], z - p0[2])
        o = [sum(d[i][k] * rel[k] for k in range(3)) for i in range(3)]
        return (p[0] + o[0], p[1] + o[1], p[2] + o[2]) + _angles(_mul(d, _rot(hd, pt, rl))) + (thr,)
    return Pose(tuple(pose), t_key, at)


def struck(pose, hits, t_key=None):
    """Any pose with hits laid over it (a formation ship whose drives are shot out, a ship's guns
    kicking): shoves, tumbles and recoil as Flight's, and the throttle a hit leaves."""
    t_key = getattr(pose, 't', 0.0) if t_key is None else t_key

    def at(t):
        x, y, z, hd, pt, rl, thr = pose.at(t) if hasattr(pose, 'at') else pose
        p, m = _apply((x, y, z), _rot(hd, pt, rl), hits, (), t, t_key)
        for h in hits:
            if t > h['t'] and 'thr' in h:
                thr = h['thr']
        return tuple(p) + _angles(m) + (thr,)
    return Pose(tuple(pose), t_key, at)


# ----------------------------------------------------------------------------- cameras

def _cr(p0, p1, p2, p3, u):
    """Catmull-Rom between p1 and p2."""
    return [.5 * (2 * p1[i] + (-p0[i] + p2[i]) * u + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * u * u
                  + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * u ** 3) for i in range(len(p1))]


def _carry(ks, ps, i, j, s):
    """The Catmull-Rom parameter on the span i to j at s (0-1, linear in time), timed so the move carries
    through an inner key at the rate its two spans share (the curve's tangent there is common to both, so
    the speed carries): at rest at the first and last key, and at a key the value holds into or out of
    (ps: the values at i-1, i, j, j+1)."""
    h = ks[j]['t'] - ks[i]['t']

    def rate(n, here, before, after):
        if n <= 0 or n >= len(ks) - 1 or math.dist(here, before) < 1e-6 or math.dist(here, after) < 1e-6:
            return 0.0
        return 2.0 / (ks[n + 1]['t'] - ks[n - 1]['t'])
    a, b = rate(i, ps[1], ps[0], ps[2]) * h, rate(j, ps[2], ps[1], ps[3]) * h
    s2, s3 = s * s, s * s * s
    return (s3 - 2 * s2 + s) * a + (3 * s2 - 2 * s3) + (s3 - s2) * b


def _mono(ks, vals, i, j, s):
    """The monotone cubic (Fritsch-Butland) through keyed values vals[n] at ks[n]['t'], on the span i to j
    at s (0-1, linear in time): each inner key's slope is the harmonic mean of its spans' slopes, zero
    where a component holds or turns back, and zero at the first and last key."""
    def slope(n):
        if n <= 0 or n >= len(ks) - 1:
            return [0.0] * len(vals[n])
        out = []
        for a, b, c in zip(vals[n - 1], vals[n], vals[n + 1]):
            d0, d1 = (b - a) / (ks[n]['t'] - ks[n - 1]['t']), (c - b) / (ks[n + 1]['t'] - ks[n]['t'])
            out.append(0.0 if d0 * d1 <= 0 else 2.0 / (1.0 / d0 + 1.0 / d1))
        return out
    if i == j:
        return list(vals[i])
    h = ks[j]['t'] - ks[i]['t']
    m0, m1 = slope(i), slope(j)
    s2, s3 = s * s, s * s * s
    return [(2 * s3 - 3 * s2 + 1) * p0 + (s3 - 2 * s2 + s) * h * a + (3 * s2 - 2 * s3) * p1 + (s3 - s2) * h * b
            for p0, p1, a, b in zip(vals[i], vals[j], m0, m1)]


class CameraPath:
    """A camera move through timed keys: dict(t=, loc=(x, y, z) or f(t), target=(x, y, z) or f(t),
    lens=mm, roll=deg, and optionally focus=m and fstop for a focus pull the preview shows; a lit
    still ignores them). Positions run on a Catmull-Rom curve. Two keys: the span eased. More: the same
    curve, timed so the move carries through each inner key (owner, 2026-10-27, S6c: "speed up, slow
    down, speed up"; easing each span stopped it at every one), at rest only at the first and last key
    and where a value holds; lens and roll on a monotone cubic through their keys in time. shake: handheld drift, degrees of seeded wander in the aim and the roll (an
    operator aboard a ship that is being shot at). kicks: [(t, degrees)], a jolt to the aim on a
    hit that peaks a fifth of a second later and settles (zero at the key time, like the shake)."""
    def __init__(self, name, keys, shake=0.0, t_key=None, kicks=()):
        self.name, self.keys, self.shake, self.t_key = name, sorted(keys, key=lambda k: k['t']), shake, t_key
        self.kicks = list(kicks)

    def _kick(self, t):
        out = [0.0, 0.0, 0.0, 0.0]
        for n, (tk, deg) in enumerate(self.kicks):
            x = t - tk
            if x <= 0:
                continue
            env = (x / .2) * math.exp(1 - x / .2) * math.cos(x * 14.0)    # a jolt that rings down
            h = zlib.crc32(f'{self.name}|kick{n}'.encode())
            for i in range(4):
                out[i] += deg * env * ((((h >> (6 * i)) & 0x3F) / 63.0) * 2 - 1)
        return out

    @staticmethod
    def _v(k, field, t):
        v = k[field]
        return list(v(t) if callable(v) else v)

    def __call__(self, t):
        ks = self.keys
        if len(ks) == 1 or t <= ks[0]['t']:
            i, u = 0, 0.0
        elif t >= ks[-1]['t']:
            i, u = len(ks) - 2, 1.0
        else:
            i = max(n for n in range(len(ks) - 1) if ks[n]['t'] <= t)
            u = _ease((t - ks[i]['t']) / (ks[i + 1]['t'] - ks[i]['t']))
        j = min(i + 1, len(ks) - 1)
        if len(ks) <= 2:
            def curve(field):
                return _cr(*[self._v(ks[max(0, min(len(ks) - 1, n))], field, t) for n in (i - 1, i, j, j + 1)], u)
            lens = ks[i].get('lens', 35) + (ks[j].get('lens', 35) - ks[i].get('lens', 35)) * u
            roll = ks[i].get('roll', 0.0) + (ks[j].get('roll', 0.0) - ks[i].get('roll', 0.0)) * u
        else:
            s_ = 0.0 if i == j else max(0.0, min(1.0, (t - ks[i]['t']) / (ks[j]['t'] - ks[i]['t'])))

            def scalar(field, default):
                vals = {n: [ks[n].get(field, default)] for n in range(max(0, i - 1), min(len(ks), j + 2))}
                return _mono(ks, vals, i, j, s_)[0]

            def curve(field):
                ps = [self._v(ks[max(0, min(len(ks) - 1, n))], field, t) for n in (i - 1, i, j, j + 1)]
                return _cr(*ps, _carry(ks, ps, i, j, s_))
            lens, roll = scalar('lens', 35), scalar('roll', 0.0)
        loc, target = curve('loc'), curve('target')
        if self.shake:      # still at the key time, so the lit still is the framing the keys say
            d = math.dist(loc, target)
            t0 = t if self.t_key is None else self.t_key
            for n, axis in enumerate(('sx', 'sy', 'sz')):
                wv = _waves(self.name, axis, 3.2)
                target[n] += math.radians(_eval(wv, t, self.shake)[0] - _eval(wv, t0, self.shake)[0]) * d
            wr = _waves(self.name, 'sr', 4.1)
            roll += _eval(wr, t, self.shake * .6)[0] - _eval(wr, t0, self.shake * .6)[0]
        if self.kicks:
            d = math.dist(loc, target)
            k, k0 = self._kick(t), self._kick(t if self.t_key is None else self.t_key)
            for n in range(3):
                target[n] += math.radians(k[n] - k0[n]) * d
            roll += 2 * (k[3] - k0[3])
        out = dict(loc=tuple(loc), target=tuple(target), lens=lens, roll=roll)
        for field in ('focus', 'fstop'):     # a focus pull: distance (m) and aperture, eased, preview only
            if any(field in k for k in ks):
                a, b = (next((k[field] for k in reversed(ks[:n + 1]) if field in k),
                             next(k[field] for k in ks if field in k)) for n in (i, j))
                out[field] = a + (b - a) * u
        return out


def camera(name, keys, t_key, shake=0.0, kicks=()):
    """A key frame's camera: its pose at t_key (loc, target, lens, roll: what aim() takes) plus 'at',
    the whole move, which the preview plays."""
    c = CameraPath(name, keys, shake, t_key, kicks)
    d = c(t_key)
    d['at'], d['t'] = c, t_key
    return d


def follow(pose, offset=(0.0, 0.0, 0.0), lag=0.0, share=1.0, t_key=None):
    """A point that moves with a ship, for a camera key: her position (lagging by lag seconds) plus a
    battle-frame offset. share < 1 follows only part of her motion since t_key (a camera that pans
    with the action without riding it)."""
    t_key = getattr(pose, 't', 0.0) if t_key is None else t_key
    p0, lagged = pose.at(t_key), pose.at(t_key - lag)

    def at(t):      # her motion since the key time, as it was lag seconds ago: at the key time, offset from her
        p = pose.at(t - lag)
        return tuple(p0[n] + (p[n] - lagged[n]) * share + offset[n] for n in range(3))
    return at


# ----------------------------------------------------------------------------- the planet turning beneath

ORBIT_V = 7.8e3       # m/s: low orbit at 250 km


def planet_turn_deg(t, t_key, radius):
    """How far the planet has turned under the co-moving battle frame since the key time, degrees
    about +Y (north): the ground slides aft under the convoy at orbital speed, about 60 km in an
    8 s shot. Zero at the key time, so a still and its painted plate stay registered."""
    return -math.degrees(ORBIT_V * (t - t_key) / radius)
