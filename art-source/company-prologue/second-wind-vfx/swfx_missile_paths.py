"""Authored missile flight: curved paths, contrails left in place, and the end of a flight.

The kit's launcher (`swfx_guns._launch`) flies a missile straight down the line of fire. This
module gives a launch a *flight* instead, planned before anything is keyed, so point defence
(`swfx_pd`) can read where every missile is on every frame and kill some short before the
flights are keyed. Opt-in: nothing here runs unless a caller passes `flights=` to
`swfx_guns.fire`, and the Second Wind never does.

Planning (plain maths, world metres, one sample per frame):

    flights = salvo('harpoon', starts, target, space=ship_space)   # [Flight]
    swfx_pd.screen(flights, ...)                                  # marks kills short (optional)
    with ship.kit() as (ctx, kit):
        kit.guns.fire(ctx, 5, f0, shots=2, interval_frames=6, flights=flights[:2])

A missile is steered, not drawn: an authored *guide* curve says where the salvo should go (the
Harpoon's wide swing and curve back in, the Swarmer's weave), and the missile chases a point a
little ahead on that guide at its profile's turn rate. So every path is smooth, a missile turns
off its tube as fast as its game turn rate allows, and the curve width is one knob
(`width`: the guide's peak sideways offset as a fraction of the launch-to-target range, reached
a third of the way down the lane).

- **Cold eject, then lit** (owner, 2026-10-20). A launch pushes the missile clear of the tube
  at `eject_mps` along the tube axis; its motor lights `ignite_s` later, with a small ignition
  flash, and only then does it turn and accelerate. The flash is swfx_burst's lobed one unless the
  profile says `ignition='flare'`: the glare streak flaring long and hot in the motor's colour, then
  settling (the Harpoon's; owner, 2026-10-25, S5: the burst's white-hot heart read as a lobed
  peach-white blob at each ignition).
- **Seeker corrections** (owner, 2026-10-20: guided missiles are chase missiles that hunt).
  A guided missile does not fly its guide smoothly: its seeker updates the aim every `seek_s`
  and each update carries a seeded error of up to `seek_deg`, which shrinks as the missile
  closes (with the remaining range, and to nothing over the last tenth). The aim is held
  between updates, so the missile turns onto each new aim at its turn rate and the path
  hunts in small corrections. Each new error tends to swing the other way (`seek_swing`), so
  the corrections read as a seeker over-correcting, not as noise. Torpedoes and rockets fly
  straight and have no seeker. `seek_s=0` turns it off.
- **Contrails** are one tube mesh per missile along its whole path, each ring stamped with the
  frame the missile passed it (`birth`). The shader shows a ring once its frame has come and
  thins it with age: the core spreads toward `trail_spread` times its width and fades over
  `trail_life_s` (times `trail_scale`, default 1: pass `trail_scale=1.5` to `plan`/`salvo`
  for a hero shot's longer trails). Nothing moves, so in vacuum the trail stays where the motor spent it.
  Colours are vanilla: the Harpoon's blue (100,165,255), grey smoke (100,100,100,150) for the
  others. The Harpoon's trail is denser than the game's alpha 75 (.45 against .29) and glows a
  little, so it still reads as a line at 3 km: the curving contrails are the S5 shot.
- **The end of a flight** is one of:
  - `hit`: a jagged burst (`swfx_burst`, never an orb: owner, 2026-10-21) thrown back out of
    the target point, `on='armour'` or `on='shield'` (the caller supplies the
    point: the hull's surface, or the shield's);
  - `kill`: killed short (by `swfx_pd`): a jagged pop with cooling fragments
    (`swfx_burst.burst`; profile `blasts='orb'` for the old balls), the motor dies, the body
    tumbles on, dark;
  - `decoy`: diverted onto a flare (`Flight.divert`) and detonated on it;
  - `burnout`: an unguided round that reached its range without a target.
  `arrivals(flights)` lists every end as a cue (frame, world point, kind, target, by), for the
  scene's cue list and for W5/W6 (the hit that starts a cook-off).

Keying (`key(ctx, flight)`, called by `swfx_guns._launch`) builds the missile in the ship's own
effects collection, in that collection's space: `Flight.space` is the ship's world matrix (from
`space_of(ship)`), so the same plan keys correctly into any copy. Everything keyed is named
`SWFX missile ...` and belongs to the ship that fired it.
"""
import math
import random

from mathutils import Matrix, Quaternion, Vector

PREFIX = 'SWFX missile'


def srgb(c):
    """Vanilla 0..255 sRGB to linear 0..1."""
    def f(v):
        v /= 255.0
        return v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4
    return tuple(f(v) for v in c)


# Each profile is read from the game's .proj (engine and contrail colours, turn rate) and scaled
# to the scene's kilometres (speeds, sizes). guide: 'swing' (wide arc, curve in), 'weave',
# 'arc' (a slow shallow arc, then a dart), or 'straight' (unguided).
PROFILES = {
    'harpoon': dict(guide='swing', width=.18, seek_s=.3, seek_deg=8.0, lift=.04, turn_deg_s=70.0, eject_mps=18.0, ignite_s=.35,
                    accel=650.0, vmax=950.0, engine=srgb((100, 165, 255)), trail=srgb((100, 165, 255)),
                    trail_alpha=.45, trail_glow=1.4, trail_r_m=3.0, trail_spread=2.5, trail_life_s=1.7,
                    glare_m=5.0, blast=srgb((100, 100, 255)), blast_m=24.0, ignition='flare'),
    'swarmer': dict(guide='weave', width=.05, seek_s=.18, seek_deg=10.0, lift=.02, weave=.035, weave_cycles=(2.5, 4.0), turn_deg_s=240.0,
                    eject_mps=12.0, ignite_s=.2, accel=1400.0, vmax=700.0, engine=srgb((255, 145, 75)),
                    trail=srgb((100, 100, 100)), trail_alpha=150 / 255, trail_glow=0.0, trail_r_m=1.2,
                    trail_spread=1.6, trail_life_s=.7, glare_m=3.0, blast=srgb((255, 155, 75)), blast_m=10.0),
    'pilum': dict(guide='arc', width=.08, seek_s=.5, seek_deg=5.0, lift=.10, turn_deg_s=35.0, eject_mps=10.0, ignite_s=.4,
                  accel=150.0, vmax=260.0, dart_at=.78, dart_vmax=1100.0, dart_accel=2500.0,
                  engine=srgb((255, 145, 75)), trail=srgb((100, 100, 100)), trail_alpha=150 / 255,
                  trail_glow=0.0, trail_r_m=2.0, trail_spread=2.0, trail_life_s=2.6, glare_m=4.0,
                  blast=srgb((255, 155, 75)), blast_m=16.0),
    'rocket': dict(guide='straight', fan_deg=(-7.0, -3.5, 0.0, 3.5, 7.0), turn_deg_s=0.0, eject_mps=0.0,
                   ignite_s=0.0, accel=900.0, vmax=800.0, range_m=1800.0, engine=srgb((255, 190, 140)),
                   trail=srgb((190, 180, 160)), trail_alpha=.45, trail_glow=0.0, trail_r_m=1.4,
                   trail_spread=1.8, trail_life_s=.8, glare_m=3.0, blast=srgb((255, 155, 75)), blast_m=12.0),
    'torpedo': dict(guide='straight', turn_deg_s=0.0, eject_mps=14.0, ignite_s=.5, accel=120.0, vmax=420.0,
                    range_m=3000.0, engine=srgb((255, 100, 100)), trail=srgb((255, 100, 100)), trail_alpha=.35,
                    trail_glow=1.0, trail_r_m=3.0, trail_spread=2.0, trail_life_s=2.0, glare_m=7.0,
                    blast=srgb((255, 100, 100)), blast_m=45.0),
}

SUB = 4            # steering sub-steps per frame


class Seeker:
    """A guided missile's seeker: the aim error it holds between updates (a small rotation of
    the wanted heading), resampled every `seek_s`, up to `seek_deg` scaled by the remaining
    range, and gone over the last tenth of the lane. Its own random stream, so a profile
    without a seeker plans exactly as before."""

    def __init__(self, prof, key):
        self.every = prof.get('seek_s', 0.0)
        self.deg = prof.get('seek_deg', 0.0)
        self.swing = prof.get('seek_swing', .6)
        self.rnd = random.Random(f'swfx-seeker|{key}')
        self.next_t = None
        self.err = (0.0, 0.0)            # (sideways, up) degrees about the wanted heading
        self.updates = []                # (t, sideways, up): the corrections, for the bench report

    def aim(self, t, want, s):
        """The heading the seeker steers for at time t, given the true wanted heading `want`
        (unit) at lane fraction s."""
        if not self.every or not self.deg:
            return want
        if self.next_t is None or t >= self.next_t:
            self.next_t = t + self.every * self.rnd.uniform(.8, 1.2)
            k = max(0.0, min(1.0, (.9 - s) / .9)) ** .8       # shrinks as it closes, 0 at s=.9
            a = self.deg * k
            ex = -self.swing * self.err[0] + self.rnd.gauss(0, a * .7)
            ey = -self.swing * self.err[1] + self.rnd.gauss(0, a * .45)
            m = math.hypot(ex, ey)
            if m > a and m > 1e-9:
                ex, ey = ex * a / m, ey * a / m
            self.err = (ex, ey)
            self.updates.append((round(t, 3), round(ex, 2), round(ey, 2)))
        ex, ey = self.err
        if abs(ex) < 1e-6 and abs(ey) < 1e-6:
            return want
        side = want.cross(Vector((0, 0, 1)))           # a frame that turns with the heading
        side = side.normalized() if side.length > 1e-3 else want.orthogonal().normalized()
        up = side.cross(want).normalized()
        q = Quaternion(up, math.radians(ex)) @ Quaternion(side, math.radians(ey))
        return (q @ want).normalized()


def space_of(ship):
    """A placed ship's effects space: world <- its SWFX collection (hull units). Takes W0's
    ship_fx.ShipFx (its .matrix and metres per unit)."""
    return ship.matrix @ Matrix.Scale(ship.mpu, 4)


def anchor_matrix(ob):
    """An anchor's matrix in its collection's own space, from its parent chain. The bench and
    the scene keep the library collections excluded, so Blender never evaluates their
    matrix_world; the chain of basis and parent-inverse matrices is what it would be."""
    m = Matrix.Identity(4)
    while ob is not None:
        m = (ob.matrix_parent_inverse @ ob.matrix_basis if ob.parent is not None else ob.matrix_basis) @ m
        ob = ob.parent
    return m


def tube(ship, ctx, turret, tag):
    """(world position, world axis) of one launcher tube (or gun muzzle) of a placed ship,
    read from this ship's copy of its anchor (call inside ship.kit())."""
    a = next(g for g in ctx.anchors.guns if g.turret == turret and g.tag == tag)
    m = space_of(ship) @ anchor_matrix(a.empty)
    return m.translation.copy(), (m.to_3x3() @ Vector((1, 0, 0))).normalized()


class Flight:
    """One missile's planned flight, in world metres, one sample per frame from `frame`
    (launch). pos[i] is where it is on frame `frame + i`; the last sample is where it ends."""

    def __init__(self, kind, prof, frame, start, axis, space, label=None, seed=0):
        self.kind, self.prof, self.frame = kind, prof, int(frame)
        self.start, self.axis = Vector(start), Vector(axis).normalized()
        self.space = space
        self.label = label
        self.seed = seed
        self.pos = []
        self.ignite = self.frame + int(round(prof['ignite_s'] * 24))
        self.end = None          # dict(kind, frame, point, on, target, by)
        self.hot = None          # (frame, frame): a PD laser heating it before the kill
        self.dart = None         # frame a Pilum's terminal stage separates
        self.seeker = None       # a guided flight's Seeker (its corrections), set by plan()

    # ------------------------------------------------------------------ reading
    @property
    def last(self):
        return self.frame + len(self.pos) - 1

    def at(self, f):
        """Position on frame f (clamped to the flight)."""
        i = min(max(int(round(f)) - self.frame, 0), len(self.pos) - 1)
        return self.pos[i]

    def velocity(self, f, fps=24):
        i = min(max(int(round(f)) - self.frame, 1), len(self.pos) - 1)
        return (self.pos[i] - self.pos[i - 1]) * fps

    def heading(self, f):
        v = self.velocity(f)
        return v.normalized() if v.length > 1e-6 else self.axis

    def lateral_peak(self, target):
        """The flight's largest sideways distance from its launch-to-target line, in metres
        and as a fraction of the range: the curve width it actually flew."""
        u = (Vector(target) - self.start)
        L = u.length
        u.normalize()
        best = max(((p - self.start) - u * (p - self.start).dot(u)).length for p in self.pos)
        return round(best, 1), round(best / max(L, 1e-6), 3)

    # ------------------------------------------------------------------ cutting short
    def kill(self, frame, by=None, hot_from=None):
        """Kill it short on `frame` (point defence): the motor dies there and the body tumbles
        on along its last velocity, dark, for a second. hot_from: a PD laser has had it in its
        beam since that frame (the glare heats toward white before the pop)."""
        frame = int(frame)
        if frame <= self.ignite or frame >= self.last:
            raise ValueError(f'{self.label}: kill on frame {frame} is outside its powered flight '
                             f'({self.ignite + 1}..{self.last - 1})')
        v = self.velocity(frame) / 24
        p = self.at(frame)
        self.pos = self.pos[:frame - self.frame + 1]
        for j in range(1, 25):
            self.pos.append(p + v * j * (1 - .02 * j))
        self.end = dict(kind='kill', frame=frame, point=p.copy(), on=None, target=self.end and self.end['target'],
                        by=by)
        if hot_from is not None:
            self.hot = (int(hot_from), frame)
        return self

    def divert(self, frame, chase, by=None, turn_deg_s=None, fps=24):
        """From `frame` the seeker takes a decoy instead: it steers onto `chase(f)` (the
        flare's world position on frame f) at its turn rate and detonates on it."""
        frame = int(frame)
        i = frame - self.frame
        p = self.pos[i].copy()
        h = self.heading(frame)
        speed = self.velocity(frame).length
        omega = math.radians(turn_deg_s if turn_deg_s is not None else max(self.prof['turn_deg_s'], 90))
        target = self.end and self.end['target']
        self.pos = self.pos[:i + 1]
        dt = 1.0 / (fps * SUB)
        for f in range(frame + 1, frame + 8 * fps):
            for _ in range(SUB):
                h = _steer(h, (chase(f) - p).normalized(), omega * dt)
                p = p + h * speed * dt
            self.pos.append(p.copy())
            if (chase(f) - p).length < speed / fps * 1.2:
                self.end = dict(kind='decoy', frame=f, point=chase(f).copy(), on=None, target=target, by=by)
                return self
        self.end = dict(kind='burnout', frame=self.last, point=p.copy(), on=None, target=target, by=by)
        return self


def _steer(h, want, max_angle):
    """Turn unit heading h toward unit `want` by at most max_angle radians."""
    ang = h.angle(want, 0.0)
    if ang <= max_angle or ang < 1e-9:
        return want.copy()
    axis = h.cross(want)
    if axis.length < 1e-9:
        axis = h.orthogonal()
    return (Quaternion(axis.normalized(), max_angle) @ h).normalized()


def _speed(prof, t, dart_t=None):
    """Distance-rate (m/s) t seconds after launch: eject, then accelerate to vmax; a Pilum's
    second stage accelerates again from dart_t."""
    if t < prof['ignite_s']:
        return prof['eject_mps']
    v = min(prof['vmax'], prof['eject_mps'] + prof['accel'] * (t - prof['ignite_s']))
    if dart_t is not None and t >= dart_t:
        v = min(prof['dart_vmax'], v + prof['dart_accel'] * (t - dart_t))
    return v


def plan(kind, frame, start, axis, target, space, label=None, seed=0, fps=24, target_name=None, on='armour',
         **override):
    """One flight of profile `kind` launched on `frame` from `start` along `axis` (world) at
    the world point `target`. override: any profile value (width, turn_deg_s, vmax, ...)."""
    prof = dict(PROFILES[kind], **override)
    fl = Flight(kind, prof, frame, start, axis, space, label, seed)
    rnd = random.Random(f'swfx-missile|{kind}|{label}|{frame}|{seed}')
    T = Vector(target)
    P0 = fl.start + fl.axis * prof['eject_mps'] * prof['ignite_s']     # where the motor lights
    u = T - P0
    L = u.length
    u.normalize()
    # The side the guide swings to: away from the launcher's own side of the line of fire (a
    # port pod swings to port), turned about the line by the caller's or a seeded roll, so a
    # salvo opens out as a fan rather than in one plane.
    side = override.get('side')
    if side is None:
        off = fl.start - space.translation
        side = off - u * off.dot(u)
        if side.length < 1e-3:
            side = u.orthogonal()
    side = Vector(side)
    side = (side - u * side.dot(u)).normalized()
    roll = math.radians(override.get('roll_deg', rnd.uniform(-25, 25)))
    side = Quaternion(u, roll) @ side
    up = u.cross(side).normalized()
    W = prof.get('width', 0.0) * rnd.uniform(.9, 1.1)
    lift = prof.get('lift', 0.0) * rnd.uniform(.6, 1.4) * rnd.choice((-1, 1))
    ncyc = rnd.uniform(*prof.get('weave_cycles', (3, 3)))
    ph1, ph2 = rnd.uniform(0, 2 * math.pi), rnd.uniform(0, 2 * math.pi)

    def guide(s):
        s = min(max(s, 0.0), 1.0)
        g = guide_shape(prof['guide'], s)
        p = P0 + u * (s * L) + side * (W * L * g) + up * (lift * L * g)
        if prof['guide'] == 'weave':
            env = math.sin(math.pi * min(s * 1.3, 1.0)) * (1 - s) ** .5
            a = prof['weave'] * L * env
            p += side * (a * math.sin(2 * math.pi * ncyc * s + ph1)) + up * (a * .7 * math.cos(2 * math.pi * ncyc * s + ph2))
        return p

    straight = prof['guide'] == 'straight'
    if straight:
        fan = override.get('fan_deg', 0.0)
        aim = (Quaternion(up, math.radians(fan)) @ u) if fan else u
        rng = prof.get('range_m', L * 1.1)
    omega = math.radians(prof['turn_deg_s'])
    dt = 1.0 / (fps * SUB)
    p, h = fl.start.copy(), fl.axis.copy()
    dart_t = None
    seeker = None if straight else Seeker(prof, f'{kind}|{label}|{frame}|{seed}')
    fl.seeker = seeker
    fl.pos.append(p.copy())
    look = .08
    travelled = 0.0
    t = 0.0
    for f in range(frame + 1, frame + 30 * fps):
        for _ in range(SUB):
            t += dt
            v = _speed(prof, t, dart_t)
            if t > prof['ignite_s']:
                if straight:
                    h = _steer(h, aim, math.radians(400) * dt)
                else:
                    s = (p - P0).dot(u) / L
                    if prof['guide'] == 'arc' and dart_t is None and s >= prof['dart_at']:
                        dart_t = t
                        fl.dart = f
                    want = guide(s + look) if s + look < 1.0 else T
                    h = _steer(h, seeker.aim(t, (want - p).normalized(), s), omega * dt * (1 + 2 * max(0.0, s - .85) / .15))
            p = p + h * v * dt
            travelled += v * dt
        fl.pos.append(p.copy())
        if straight:
            if travelled >= rng:
                fl.end = dict(kind='burnout', frame=f, point=p.copy(), on=None, target=target_name, by=None)
                break
            if (p - T).length < v / fps * 1.5 and not override.get('fan_deg'):
                fl.end = dict(kind='hit', frame=f, point=T.copy(), on=on, target=target_name, by=None)
                fl.pos[-1] = T.copy()
                break
        elif (p - T).length < v / fps * 1.5 or (p - P0).dot(u) > L + 2 * v / fps:
            fl.pos[-1] = T.copy()
            fl.end = dict(kind='hit', frame=f, point=T.copy(), on=on, target=target_name, by=None)
            break
    if fl.end is None:
        fl.end = dict(kind='burnout', frame=fl.last, point=p.copy(), on=None, target=target_name, by=None)
    return fl


def guide_shape(guide, s):
    """The sideways shape of a guide, 0 at launch and at the target, peaking at 1."""
    if guide == 'swing':          # out early, curving back in, arriving along the line
        return 6.75 * s * (1 - s) ** 2
    if guide in ('arc', 'weave'):
        return math.sin(math.pi * s)
    return 0.0


def salvo(kind, starts, target, space, frames, spread_m=6.0, seed=0, target_name=None, on='armour', **override):
    """A salvo: one flight per (start, axis, label) in `starts`, launched on the matching
    entry of `frames`, each at `target` plus a seeded scatter of `spread_m` across the line of
    fire (they converge on the hull, not on one point). Rockets take the profile's fan."""
    rnd = random.Random(f'swfx-salvo|{kind}|{seed}')
    prof = PROFILES[kind]
    T = Vector(target)
    out = []
    for i, ((start, axis, label), f) in enumerate(zip(starts, frames)):
        u = (T - Vector(start)).normalized()
        a, b = u.orthogonal().normalized(), None
        b = u.cross(a)
        tgt = T + a * rnd.gauss(0, spread_m) + b * rnd.gauss(0, spread_m)
        kw = dict(override)
        if prof['guide'] == 'straight' and prof.get('fan_deg'):
            kw.setdefault('fan_deg', prof['fan_deg'][i % len(prof['fan_deg'])])
        out.append(plan(kind, f, start, axis, tgt, space, label=label, seed=seed * 101 + i,
                        target_name=target_name, on=on, **kw))
    return out


def arrivals(flights):
    """Every flight's end as a cue: {frame, kind, point (world metres), on, target, by, label}."""
    return [dict(fl.end, point=[round(v, 2) for v in fl.end['point']], label=fl.label, missile=fl.kind)
            for fl in flights if fl.end]


# ======================================================================================= keying

def _bpy():
    import bpy
    return bpy


def _bake(ob, path, index, frames, values, interp='LINEAR'):
    import swfx_guns
    return swfx_guns._bake(ob, path, index, frames, values, interp)


def _visible_between(ob, f0, f1):
    import swfx_guns
    swfx_guns._visible_between(ob, f0, f1)


def _sphere_mesh(name, mat, segs=12, rings=6):
    bpy = _bpy()
    me = bpy.data.meshes.get(name)
    if me is not None:
        return me
    verts, faces = [(0, 0, 1)], []
    for r in range(1, rings):
        th = math.pi * r / rings
        for k in range(segs):
            ph = 2 * math.pi * k / segs
            verts.append((math.sin(th) * math.cos(ph), math.sin(th) * math.sin(ph), math.cos(th)))
    verts.append((0, 0, -1))
    for k in range(segs):
        faces.append((0, 1 + k, 1 + (k + 1) % segs))
    for r in range(rings - 2):
        a0, b0 = 1 + r * segs, 1 + (r + 1) * segs
        for k in range(segs):
            k1 = (k + 1) % segs
            faces.append((a0 + k, b0 + k, b0 + k1, a0 + k1))
    last = len(verts) - 1
    base = 1 + (rings - 2) * segs
    for k in range(segs):
        faces.append((base + k, last, base + (k + 1) % segs))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    for p in me.polygons:
        p.use_smooth = True
    me.update()
    me.materials.append(mat)
    return me


def glow_material(ctx, name, colour, strength=30.0, core=.6):
    """An additive soft ball for a motor glare, a pop or a blast: emission concentrated at the
    centre as seen from the camera, running to white where hot. Object colour R is the
    intensity (keyed) and G the heat (0 the colour, 1 white)."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = swfx_guns._G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    c = g.clamp01(g.sub(1.0, lw.outputs['Facing']))
    ball = g.pow(c, 2.2)
    hot = g.clamp01(g.add(g.mul(g.pow(c, 6.0), core), sep.outputs[1]))
    mix = nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    links.new(hot, mix.inputs['Factor'])
    mix.inputs['A'].default_value = (*colour, 1)
    mix.inputs['B'].default_value = (1.0, .97, .92, 1)
    em = nodes.new('ShaderNodeEmission')
    links.new(mix.outputs['Result'], em.inputs['Color'])
    links.new(g.mul(g.mul(ball, sep.outputs[0]), strength), em.inputs['Strength'])
    tr = nodes.new('ShaderNodeBsdfTransparent')
    add = nodes.new('ShaderNodeAddShader')
    links.new(tr.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    links.new(add.outputs[0], out.inputs['Surface'])
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    return m


def contrail_material(ctx, colour, glow, broken=0.0):
    """The trail left in place. Attribute `birth` is the frame the missile passed each ring;
    the current frame comes from a driver. Object colour R is 1 / life in frames, G the
    vanilla alpha, B the spread the core reaches at the end of its life. A ring shows from its
    birth frame, then thins: its core widens and fades with age. glow > 0 adds a faint
    emission in the trail colour (the Harpoon's blue smoke reads lit from inside).
    broken > 0 (a noise scale, per hull unit): torn smoke, not a tube (owner, 2026-10-23: a
    flare's trail read as a solid translucent rod). Noise fixed in the trail's space cuts it into
    wisps, the holes opening as it ages, so it breaks up and disperses."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    name = f'{PREFIX} contrail ' + '-'.join(f'{c:.3f}' for c in colour) + f' g{glow:.2f}' +         (f' broken {broken:.4g}' if broken else '')
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = swfx_guns._G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    attr = nodes.new('ShaderNodeAttribute')
    attr.attribute_type = 'GEOMETRY'
    attr.attribute_name = 'birth'
    now = swfx_core.value_node(ctx, nodes, 'frame', 'frame', ())
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    inv_life, alpha0, spread = sep.outputs[0], sep.outputs[1], sep.outputs[2]
    age = g.sub(now, attr.outputs['Fac'])
    shown = g.op('GREATER_THAN', age, -.01)
    k = g.mx(g.mul(age, inv_life), 0.0)                    # age in lives
    fade = g.exp_neg(g.mul(k, 1.6))
    widen = g.add(1.0, g.mul(g.sub(spread, 1.0), g.sub(1.0, g.exp_neg(g.mul(k, 2.0)))))
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    c = g.clamp01(g.sub(1.0, lw.outputs['Facing']))
    core = g.pow(c, g.div(5.0, widen))                      # narrow and dense young, wide and thin old
    young = g.clamp01(g.mul(g.add(age, .3), 1.5))           # the newest ring eases in at the motor
    dens = g.clamp01(g.mul(g.mul(g.mul(g.mul(core, fade), alpha0), shown), g.div(young, widen)))
    if broken:
        tc = nodes.new('ShaderNodeTexCoord')
        n1 = g.noise(tc.outputs['Object'], g.mul(age, .02), broken, 4.0, .65)
        n2 = g.noise(tc.outputs['Object'], g.add(g.mul(age, .03), 7.0), broken * 3.1, 2.0, .5)
        torn = g.add(n1, g.mul(g.sub(n2, .5), .45))
        holes = g.clamp01(g.mul(g.sub(torn, g.add(.44, g.mul(g.clamp01(k), .16))), 4.0))
        dens = g.mul(dens, holes)
    smoke = nodes.new('ShaderNodeBsdfDiffuse')
    smoke.inputs['Color'].default_value = (*(.35 + .65 * v for v in colour), 1) if glow else (*colour, 1)
    body = smoke
    if glow:
        em = nodes.new('ShaderNodeEmission')
        em.inputs['Color'].default_value = (*colour, 1)
        links.new(g.mul(g.mul(fade, glow), 3.0), em.inputs['Strength'])
        add = nodes.new('ShaderNodeAddShader')
        links.new(smoke.outputs[0], add.inputs[0])
        links.new(em.outputs[0], add.inputs[1])
        body = add
    tr = nodes.new('ShaderNodeBsdfTransparent')
    mix = nodes.new('ShaderNodeMixShader')
    links.new(dens, mix.inputs['Fac'])
    links.new(tr.outputs[0], mix.inputs[1])
    links.new(body.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], out.inputs['Surface'])
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    return m


def _contrail(ctx, coll, fl, inv, mpu, sides=6):
    """One tube along the powered stretch of the flight, rings every half frame, each stamped
    with its birth frame. Built in the ship's effects space (inv: collection <- world)."""
    bpy = _bpy()
    prof = fl.prof
    f_end = fl.end['frame'] if fl.end and fl.end['kind'] in ('kill', 'decoy', 'hit') else fl.last
    f0 = fl.ignite
    if f_end - f0 < 2:
        return None
    r = prof['trail_r_m'] * prof['trail_spread'] / mpu
    samples = []
    for j in range(2 * (f_end - f0) + 1):
        f = f0 + j / 2
        a, b = fl.at(math.floor(f)), fl.at(math.ceil(f))
        samples.append((f, a.lerp(b, f - math.floor(f))))
    verts, birth, faces = [], [], []
    prev_n = None
    for i, (f, p) in enumerate(samples):
        q = samples[min(i + 1, len(samples) - 1)][1] - samples[max(i - 1, 0)][1]
        t = q.normalized() if q.length > 1e-6 else fl.axis
        n = prev_n if prev_n is not None else t.orthogonal().normalized()
        n = (n - t * n.dot(t)).normalized()
        prev_n = n
        bnorm = t.cross(n)
        pl = inv @ p
        for k in range(sides):
            ang = 2 * math.pi * k / sides
            w = (n * math.cos(ang) + bnorm * math.sin(ang))
            verts.append(pl + (inv.to_3x3() @ w).normalized() * r)
            birth.append(f)
    for i in range(len(samples) - 1):
        for k in range(sides):
            a0, a1 = i * sides + k, i * sides + (k + 1) % sides
            faces.append((a0, a1, a1 + sides, a0 + sides))
    me = bpy.data.meshes.new(f'{PREFIX} contrail mesh {fl.label} f{fl.frame:04d}')
    me.from_pydata([tuple(v) for v in verts], [], faces)
    for poly in me.polygons:
        poly.use_smooth = True
    at = me.attributes.new('birth', 'FLOAT', 'POINT')
    at.data.foreach_set('value', birth)
    me.update()
    broken = 1.0 / max(1e-6, prof['trail_r_m'] * 4.0 / mpu) if prof.get('trail_broken') else 0.0
    me.materials.append(contrail_material(ctx, prof['trail'], prof['trail_glow'], broken))
    ob = bpy.data.objects.new(f'{PREFIX} contrail {fl.label} f{fl.frame:04d}', me)
    coll.objects.link(ob)
    ob.visible_shadow = False
    fps = ctx.scene.render.fps or 24
    life = prof['trail_life_s'] * prof.get('trail_scale', 1.0)
    ob.color = (1.0 / (life * fps), prof['trail_alpha'], prof['trail_spread'], 1)
    _visible_between(ob, f0, f_end + int(life * fps * 3))
    return ob


def _orient(t):
    """A quaternion turning +X onto t, Z kept as near up as allowed."""
    return t.to_track_quat('X', 'Z')


def _flash(ctx, coll, name, at_local, frame, colour, radius_u, frames=8, peak=1.0, heat=.8, light_w=0.0, grow=1.0):
    """A pop or a blast: a glow ball at at_local (collection space) that flashes up in a frame
    or two and fades, growing to radius_u, with an optional point light."""
    bpy = _bpy()
    import swfx_core
    mat = glow_material(ctx, f'{PREFIX} glow ' + '-'.join(f'{c:.3f}' for c in colour), colour)
    ob = bpy.data.objects.new(name, _sphere_mesh(f'{PREFIX} glow mesh', mat))
    coll.objects.link(ob)
    ob.location = at_local
    ob.visible_shadow = False
    fr = [frame + j for j in range(frames + 1)]
    env = [peak * (1.0 if j == 0 else (1 - j / frames) ** 1.6) for j in range(frames + 1)]
    sc = [radius_u * (.45 + .55 * (1 - math.exp(-3.0 * grow * (j + 1) / frames))) for j in range(frames + 1)]
    for i in range(3):
        _bake(ob, 'scale', i, fr, sc)
    _bake(ob, 'color', 0, fr, env)
    _bake(ob, 'color', 1, fr, [heat * (1 - j / frames) for j in range(frames + 1)])
    _visible_between(ob, frame, frame + frames)
    out = [ob]
    if light_w:
        lt = swfx_core.point_light(name + ' light', coll, colour, 0.0, radius=radius_u * .3)
        lt.location = at_local
        lt.data.keyframe_insert('energy', frame=frame - 1)
        for j, e in enumerate(env):
            lt.data.energy = light_w * e
            lt.data.keyframe_insert('energy', frame=frame + j)
        lt.data.energy = 0.0
        lt.data.keyframe_insert('energy', frame=frame + frames + 1)
        out.append(lt)
    return out


def burst(ctx, coll, prof, what, tag, here, f, mpu, axis=None, rnd=None):
    """A missile's burst (swfx_burst, never an orb): what is 'ignition' (the motor lighting,
    flaring back along `axis`), 'hit' or 'shield' (the warhead, thrown back out along `axis`),
    'pop' (killed short, fragments carried on along `axis`) or 'decoy' (detonated on a flare).
    here: collection space; returns the objects."""
    import swfx_burst
    rnd = rnd or random.Random(f'swfx-missile-{what}|{tag}')
    B = prof['blast_m']
    kw = dict(prefix=PREFIX, axis=axis)
    if what == 'ignition':
        kw.update(flash=prof['engine'], size_m=(prof['glare_m'] * .9, prof['glare_m'] * 1.3), lean=1.4,
                  shrapnel=None, smoke=0, light_w=8000.0)
    elif what in ('hit', 'shield', 'pop', 'decoy'):
        # A warhead is the approved HE recipe scaled by pieces (coordinator, 2026-10-23): a
        # Hellbore-sized core, torn tongues and burning debris round it, smoke held about a second.
        # Never one big torn flash, which read as a hard plate and then a patterned solid.
        k = {'hit': 1.0, 'shield': .8, 'pop': .6, 'decoy': .7}[what]
        return swfx_burst.warhead(
            ctx, coll, f'{what} {tag}', here, f, mpu, rnd, srgb((255, 190, 120)) if what == 'pop' else prof['blast'],
            core_m=(B * .2 * k, B * .28 * k), axis=None if what == 'decoy' else axis,
            reach=(1.0, 2.0) if what == 'hit' else (.8, 1.6),
            tongues=(5, 7) if what == 'hit' else (3, 5), late=6 if what == 'hit' else (3 if what == 'shield' else 0),
            late_s=1.0, shrapnel=(4, 7) if what in ('hit', 'shield') else (2, 4), smoke=.85 if what == 'hit' else .6,
            smoke_life=(24, 32) if what in ('hit', 'shield') else (16, 22),
            light_w={'hit': 2.5e5, 'shield': 1.2e5, 'pop': 4.0e4, 'decoy': 6.0e4}[what],
            spall={'hit': 24, 'shield': 14, 'pop': 8, 'decoy': 6}[what],
            glow_w={'hit': 5.0e4, 'shield': 2.4e4, 'pop': 8.0e3, 'decoy': 0.0}[what], prefix=PREFIX)
    else:
        raise ValueError(what)
    flash = kw.pop('flash')
    return swfx_burst.burst(ctx, coll, f'{what} {tag}', here, f, mpu, rnd, flash, **kw)


def key(ctx, coll, fl, a, motor_mat, body_mat, cylinder_mesh, mpu):
    """Key one planned flight into a ship's effects collection: body, motor rod, motor glare
    and light, contrail, the ignition flash, and the end (blast, pop, or decoy burst). Called
    by swfx_guns._launch; returns the record's flight fields."""
    bpy = _bpy()
    import swfx_core
    prof = fl.prof
    inv = fl.space.inverted()
    fps = ctx.scene.render.fps or 24
    s = a.scale
    end = fl.end
    burn_end = end['frame'] if end['kind'] in ('kill', 'decoy', 'hit') else fl.last
    f_last = end['frame'] if end['kind'] in ('decoy', 'hit') else fl.last
    frames = list(range(fl.frame, f_last + 1))
    locs = [inv @ fl.at(f) for f in frames]
    rot = inv.to_quaternion()
    quats = []
    rnd = random.Random(f'swfx-missile-tumble|{fl.label}|{fl.frame}')
    spin = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1))).normalized()
    q_prev = None
    for f in frames:
        if end['kind'] == 'kill' and f > end['frame']:
            q = Quaternion(spin, math.radians(25) * (f - end['frame'])) @ q_prev_kill
        else:
            q = rot @ _orient(fl.heading(f) if f > fl.frame else fl.axis)
            if end['kind'] == 'kill':
                q_prev_kill = q
        if q_prev is not None and q.dot(q_prev) < 0:
            q.negate()
        quats.append(q)
        q_prev = q
    body = bpy.data.objects.new(f'{PREFIX} {fl.label} f{fl.frame:04d}', cylinder_mesh(
        f'SWFX guns missile body mesh', .2, -2.2, 0.0, 10, body_mat))
    coll.objects.link(body)
    body.rotation_mode = 'QUATERNION'
    body.scale = (s, s, s)
    for i in range(3):
        _bake(body, 'location', i, frames, [p[i] for p in locs])
    for i in range(4):
        _bake(body, 'rotation_quaternion', i, frames, [q[i] for q in quats])
    _visible_between(body, fl.frame, f_last)
    motor = bpy.data.objects.new(f'{PREFIX} motor {fl.label} f{fl.frame:04d}', cylinder_mesh(
        'SWFX guns missile motor mesh', .12, -4.5, 0.0, 10, motor_mat))
    coll.objects.link(motor)
    motor.parent = body
    motor.location = (-2.2, 0, 0)
    motor.visible_shadow = False
    fi = fl.ignite
    _bake(motor, 'scale', 0, [fi, fi + 1, fi + 6], [.15, .6, 1.0])
    _bake(motor, 'color', 0, [fi - 1, fi, burn_end, burn_end + 1], [0.0, 1.0, 1.0, 0.0], 'CONSTANT')
    motor.color = (1, 0, 0, 1)
    _visible_between(motor, fi, burn_end)
    # The glare: what a motor is at a kilometre, sized in metres (the body is a few pixels).
    gm = glow_material(ctx, f'{PREFIX} glare ' + '-'.join(f'{c:.3f}' for c in prof['engine']), prof['engine'],
                       strength=24.0, core=.8)
    glare = bpy.data.objects.new(f'{PREFIX} glare {fl.label} f{fl.frame:04d}', _sphere_mesh(f'{PREFIX} glow mesh', gm))
    coll.objects.link(glare)
    glare.parent = body
    glare.location = (-2.6, 0, 0)
    glare.visible_shadow = False
    gr = prof['glare_m'] / mpu / s
    # No orbs (owner, 2026-10-23: the Harpoons' heads read as "white orbs"): the glare is a streak
    # drawn out behind the motor along the flight, thin across it, never a round ball.
    glare.location = (-2.6 - gr * 1.4, 0, 0)
    flare = prof.get('ignition') == 'flare' and burn_end - fi > 8
    if flare:
        # Lit: the streak flares long and hot for a few frames, then settles to its running size,
        # in the motor's own colour and drawn out along the flight like the glare it becomes
        _bake(glare, 'scale', 0, [fi, fi + 1, fi + 3, fi + 7], [gr * 1.2, gr * 2.8, gr * 2.4, gr * 1.8])
        _bake(glare, 'scale', 1, [fi, fi + 1, fi + 3, fi + 7], [gr * .2, gr * .4, gr * .36, gr * .3])
        _bake(glare, 'scale', 2, [fi, fi + 1, fi + 3, fi + 7], [gr * .2, gr * .4, gr * .36, gr * .3])
        gf, gv, gh = [fi, fi + 1, fi + 4, fi + 8, burn_end], [1.6, 2.0, 1.4, 1.0, 1.0], [0.0] * 5
    else:
        _bake(glare, 'scale', 0, [fi, fi + 3], [gr * .5, gr * 1.8])
        _bake(glare, 'scale', 1, [fi, fi + 3], [gr * .12, gr * .3])
        _bake(glare, 'scale', 2, [fi, fi + 3], [gr * .12, gr * .3])
        gf, gv, gh = [fi, burn_end], [1.0, 1.0], [0.0, 0.0]
    if fl.hot:                                  # in a PD laser's beam: heating toward white
        h0, h1 = fl.hot
        gf, gv, gh = [fi, h0, h1], [1.0, 1.3, 2.2], [0.0, .2, 1.0]
    _bake(glare, 'color', 0, gf, gv)
    _bake(glare, 'color', 1, gf, gh)
    _visible_between(glare, fi, burn_end)
    lt = swfx_core.point_light(f'{PREFIX} motor light {fl.label} f{fl.frame:04d}', coll, prof['engine'], 0.0,
                               radius=1.0)
    lt.parent = body
    lt.location = (-3.0, 0, 0)
    for f, e in ((fi - 1, 0.0), (fi, 6000.0), (fi + 4, 2500.0), (burn_end, 2500.0), (burn_end + 1, 0.0)):
        lt.data.energy = e
        lt.data.keyframe_insert('energy', frame=f)
    trail = _contrail(ctx, coll, fl, inv, mpu)
    # Bursts, not orbs (owner, 2026-10-21): every flash here is swfx_burst's jagged one unless the
    # profile says blasts='orb' (the old soft balls, kept as the control).
    jag = prof.get('blasts', prof.get('pop', 'jagged')) == 'jagged'
    rot = inv.to_3x3()

    def rnd_for(what):
        return random.Random(f'swfx-missile-{what}|{fl.label}|{fl.frame}')

    # Ignition: the cold-ejected missile lights clear of the tube, flaring back along its axis.
    tag = f'{fl.label} f{fl.frame:04d}'
    if flare:
        # the glare's flare above is the flash; the light it throws on the hull, unseen itself (a
        # light with a radius is a sphere the camera sees)
        lt = swfx_core.point_light(f'{PREFIX} ignition {tag} light', coll, prof['engine'], 0.0, radius=prof['glare_m'] * .3 / mpu)
        lt.visible_camera = False
        lt.location = inv @ fl.at(fi)
        for f, e in ((fi - 1, 0.0), (fi, 8000.0), (fi + 1, 3600.0), (fi + 2, 800.0), (fi + 3, 0.0)):
            lt.data.energy = e
            lt.data.keyframe_insert('energy', frame=f)
    elif jag:
        burst(ctx, coll, prof, 'ignition', tag, inv @ fl.at(fi), fi, mpu, rot @ -fl.heading(fi + 1), rnd_for('ignition'))
    else:
        _flash(ctx, coll, f'{PREFIX} ignition {fl.label} f{fl.frame:04d}', inv @ fl.at(fi), fi, prof['engine'],
               prof['glare_m'] * 1.6 / mpu, frames=4, peak=1.2, heat=1.0, light_w=8000.0)
    rec = {'flight': fl.kind, 'launch': fl.frame, 'ignite': fi, 'end': dict(end, point=[round(v, 1) for v in end['point']]),
           'missile': body.name, 'contrail': trail.name if trail else None, 'flight_frames': f_last - fl.frame}
    here = inv @ end['point']
    back = rot @ -fl.heading(end['frame'])         # a warhead throws back out of what it hit
    B = prof['blast_m']
    if end['kind'] == 'hit':
        shield = end.get('on') == 'shield'
        if jag:
            burst(ctx, coll, prof, 'shield' if shield else 'hit', tag, here, end['frame'], mpu, back, rnd_for('blast'))
        else:
            _flash(ctx, coll, f'{PREFIX} blast {fl.label} f{fl.frame:04d}', here, end['frame'], prof['blast'],
                   B / mpu * (.8 if shield else 1.0), frames=12, peak=1.6, heat=1.0,
                   light_w=(1.2e5 if shield else 2.5e5), grow=1.4)
        rec['blast'] = end['frame']
    elif end['kind'] == 'kill':
        # A missile killed short: a small hot pop, orange whatever its motor colour (the warhead
        # and fuel going), fragments carried on along its flight, and the motor out.
        if jag:
            burst(ctx, coll, prof, 'pop', tag, here, end['frame'], mpu, rot @ fl.heading(end['frame']), rnd_for('pop'))
        else:
            _flash(ctx, coll, f'{PREFIX} pop {fl.label} f{fl.frame:04d}', here, end['frame'], srgb((255, 190, 120)),
                   B * .45 / mpu, frames=7, peak=1.4, heat=1.0, light_w=4.0e4, grow=2.0)
        rec['pop'] = end['frame']
    elif end['kind'] == 'decoy':
        if jag:
            burst(ctx, coll, prof, 'decoy', tag, here, end['frame'], mpu, None, rnd_for('decoy'))
        else:
            _flash(ctx, coll, f'{PREFIX} decoyed {fl.label} f{fl.frame:04d}', here, end['frame'], prof['blast'],
                   B * .6 / mpu, frames=9, peak=1.4, heat=1.0, light_w=6.0e4, grow=1.6)
    return rec
