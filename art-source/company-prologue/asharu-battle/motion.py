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


def _hit_offsets(hits, jumps, tt):
    """Summed effect of hits and jumps at tt: battle-frame shove (m), body-frame turn (roll, pitch,
    yaw deg) and body-frame shove (m, fore/port/up). A hit with kick=True is transient: it peaks at
    its push/spin/bpush values decay seconds after t and dies away (a gun's recoil); otherwise it is
    a lasting shove that builds over decay seconds (spin and push in deg/s and m/s)."""
    dp, ang, bdp = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
    for h in hits:
        if tt <= h['t']:
            continue
        tau = h.get('decay', 2.0)
        x = tt - h['t']
        k = (x / tau) * math.exp(1 - x / tau) if h.get('kick') else tau * (1 - math.exp(-x / tau))
        for n in range(3):
            dp[n] += h.get('push', (0, 0, 0))[n] * k
            ang[n] += h.get('spin', (0, 0, 0))[n] * k
            bdp[n] += h.get('bpush', (0, 0, 0))[n] * k
    for tj, off in jumps:
        if tt >= tj:
            for n in range(3):
                dp[n] += off[n]
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

    def __call__(self, t):
        p, m = self._state(round((t - self.t_key) / self.DT))
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
    def __init__(self, key, anchor, t_key, keys, hits=(), jumps=(), wobble=2.0, v=None, thrust=None):
        super().__init__(key, anchor, t_key, [dict(t=-1e9, v=0.0)], hits, jumps, wobble)
        self.thrust = thrust       # m/s^2 at full burn: the throttle is the motion's acceleration
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
        self.keys = ks

    def attitude(self, t):
        ks = self.keys
        if t <= ks[0]['t']:
            k = ks[0]
            return k['hd'], k['pt'], k['rl'], k['v'], k['thr']
        if t >= ks[-1]['t']:
            k = ks[-1]
            return k['hd'], k['pt'], k['rl'], k['v'], k['thr']
        i = max(n for n in range(len(ks) - 1) if ks[n]['t'] <= t)
        a, b = ks[i], ks[i + 1]
        u = _ease((t - a['t']) / (b['t'] - a['t']))
        uv = (t - a['t']) / (b['t'] - a['t'])           # speed and throttle change linearly
        return tuple(a[f] + (b[f] - a[f]) * u for f in ('hd', 'pt', 'rl')) + tuple(
            a[f] + (b[f] - a[f]) * uv for f in ('v', 'thr'))

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
        p, _ = self._state(round((t - self.t_key) / self.DT))
        hd, pt, rl, v, thr = self.attitude(t)
        if self.thrust:      # the drives burn with the acceleration: speeding up, and hauling round a turn
            thr = max(IDLE, min(1.0, IDLE + (abs(self._accel(t)) + .5 * abs(v) * self._turn(t)) / self.thrust))
        elif thr > 0:        # the drives burn to gain speed and ease off to lose it
            thr = max(.05, min(1.0, thr + (self._accel(t) - self._accel(self.t_key)) / 40.0))
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
        return (self.attitude(t + .25)[3] - self.attitude(t - .25)[3]) / .5

    def _turn(self, t):
        """How fast her nose is swinging, rad/s."""
        a, b = _rot(*self.attitude(t - .1)[:3]), _rot(*self.attitude(t + .1)[:3])
        c = sum(a[k][0] * b[k][0] for k in range(3))
        return math.acos(max(-1.0, min(1.0, c))) / .2


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


class CameraPath:
    """A camera move through timed keys: dict(t=, loc=(x, y, z) or f(t), target=(x, y, z) or f(t),
    lens=mm, roll=deg). Positions run on a Catmull-Rom curve with each span eased; lens and roll ease
    between keys. shake: handheld drift, degrees of seeded wander in the aim and the roll (an
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

        def curve(field):
            return _cr(*[self._v(ks[max(0, min(len(ks) - 1, n))], field, t) for n in (i - 1, i, j, j + 1)], u)
        loc, target = curve('loc'), curve('target')
        lens = ks[i].get('lens', 35) + (ks[j].get('lens', 35) - ks[i].get('lens', 35)) * u
        roll = ks[i].get('roll', 0.0) + (ks[j].get('roll', 0.0) - ks[i].get('roll', 0.0)) * u
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
        return dict(loc=tuple(loc), target=tuple(target), lens=lens, roll=roll)


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
