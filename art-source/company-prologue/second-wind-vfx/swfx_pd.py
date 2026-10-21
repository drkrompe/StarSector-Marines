"""Point defence: guns and lasers that pick missiles out of a salvo, canister flak, and flares.

Opt-in (no build carries it; nothing in swfx.apply calls it). It works on the planned flights of
`swfx_missile_paths`, in two steps, so a kill is decided before either side is keyed:

1. **Plan** (plain maths, world metres): `screen(flights, guns, ...)` assigns missiles to PD
   mounts, works out every round or beam frame, and kills the chosen missiles short
   (`Flight.kill`). `plan_flak(...)` and `plan_flares(...)` do the same for the Manticore's
   canister flak and a flare launcher's decoys (`Flight.divert`).
2. **Key**, on each ship inside its own `ship.kit()`:
   - the defender keys its fire: `key_screen(ctx, engagements, space)`, `key_flak(...)` and
     `key_flares(...)`;
   - the shooter keys its flights through `swfx_guns.launch_flights`, which now end where
     the plan says.

The look, from the game (`asharu-effects.md`, Guns and Missiles):

- **Light MG / Vulcan:** the kit's own flash on that mount, keyed in the weapon's exact
  cadence (light MG: 5 rounds 0.1 s apart, 0.4 s pause; Vulcan: 20 rounds/s, barrels
  alternating), and a tracer per round along the line to where the missile will be when the
  round gets there. The stream starts off and walks on: the aim error shrinks round by round
  until one lands, and the missile pops. A stream on a missile it is not going to kill stays
  off it.
- **PD laser** (vanilla fringe 155,0,0, core white, glow 255,100,100): a thin flickering beam
  that snaps onto the missile, holds it, heats it toward white (the glare), and pops it.
- **Canister flak** (vanilla particle 255,225,125, cloud 155,125,75): canisters thrown out
  from the hull burst in a loose ring a couple of hundred metres off, in small flashes that
  leave a brief dim cloud. A missile crossing the ring dies in one.
- **Flares** (vanilla 255,125,75 with a white core): white-orange decoys flung off the flare
  anchors, slowing as they go, flickering, trailing a short bright smoke. A seeker diverted
  onto one turns away and detonates on it.

What it does not do: it does not train the turrets. A hull without its own control object
keeps its build's pose (asharu-effects-plan.md, W0 limits), so a mount's flash points along
its rest bearing while its tracers fly at the missile. At bench and scene distances the
tracer line is what reads.
"""
import math
import random

from mathutils import Matrix, Quaternion, Vector

import swfx_missile_paths as mp

PREFIX = 'SWFX pd'

# Cadence in seconds: rounds in a burst, the gap between rounds, the pause after the burst.
CADENCE = {'mg': (5, .1, .4), 'vulcan': (20, .05, .0)}
PD_LASER = dict(core=(1.0, 1.0, 1.0), fringe=mp.srgb((155, 0, 0)), glow=mp.srgb((255, 100, 100)))
FLAK = dict(flash=mp.srgb((255, 225, 125)), cloud=mp.srgb((155, 125, 75)))
FLARE = mp.srgb((255, 125, 75))


def gun(ship, ctx, turret, tag, kind='mg', range_m=800.0):
    """A PD mount of a placed ship (call inside its ship.kit()): its world muzzle and axis."""
    p, d = mp.tube(ship, ctx, turret, tag)
    import swfx_guns
    a = next(g for g in ctx.anchors.guns if g.turret == turret and g.tag == tag)
    return dict(ship=ship.key, turret=turret, tag=tag, label=swfx_guns.gun_label(a), kind=kind, at=p, axis=d,
                range_m=range_m)


# ======================================================================================= planning

def screen(flights, guns, kills=1, seed=0, picks=None, walk_s=None, tracer_mps=1600.0, fps=24,
           error_m=45.0):
    """Point defence against a salvo. Each missile is engaged by the first mount that has it in
    range; `kills` of them (or the flights at `picks`) die short; the rest are fired on and
    missed. Kills are planned onto the flights. Returns the engagements, one per (mount,
    missile), each with its rounds (fire frame, arrival frame, world aim point) or beam frames.
    By default the kills are the first missiles engaged; `picks` (flight indices) overrides."""
    rnd = random.Random(f'swfx-pd-screen|{seed}')
    cands = []
    for i, fl in enumerate(flights):
        if fl.end is None or fl.end['kind'] not in ('hit', 'burnout'):
            continue
        best = None
        for gi, g in enumerate(guns):
            for f in range(fl.ignite + 2, fl.end['frame'] - 1):
                if (fl.at(f) - g['at']).length <= g['range_m']:
                    if best is None or f < best[0]:
                        best = (f, gi)
                    break
        if best:
            cands.append((best[0], best[1], i))
    cands.sort()
    if picks is None:           # the screen kills what it engages first
        picks = [c[2] for c in cands][:kills]
    picks = set(picks)
    busy = {}          # mount -> frame it is free again
    out = []
    for open_f, gi, i in cands:
        fl = flights[i]
        g = guns[gi]
        # A mount fires on one missile at a time: a busy one hands this missile to the next
        # free mount in range, else it waits for the first to come free.
        free = [gj for gj in [gi] + [k for k in range(len(guns)) if k != gi]
                if busy.get(gj, -1) <= open_f and (fl.at(open_f + 2) - guns[gj]['at']).length <= guns[gj]['range_m'] * 1.1]
        if free:
            gi = free[0]
        else:
            gi = min(range(len(guns)), key=lambda k: busy.get(k, -1))
            open_f = busy[gi] + 1
            if open_f >= fl.end['frame'] - 3:
                continue
        g = guns[gi]
        kill = i in picks
        if g['kind'] == 'laser':
            e = _laser(fl, g, open_f, kill, rnd, fps, walk_s)
        else:
            e = _stream(fl, g, open_f, kill, rnd, fps, walk_s, tracer_mps, error_m)
        busy[gi] = e['close']
        out.append(e)
    return out


def _tof(g, p, speed):
    return (p - g['at']).length / speed


def _perp(los, rnd):
    a = los.orthogonal().normalized()
    return a, los.cross(a).normalized()


def _stream(fl, g, open_f, kill, rnd, fps, walk_s, speed, error_m):
    """A gun's rounds at one missile: walking on and killing it, or hosing and missing."""
    n, gap, pause = CADENCE[g['kind']]
    walk = (walk_s if walk_s is not None else (.7 if g['kind'] == 'mg' else .45)) * fps
    end = fl.end['frame']
    t, rounds, k = float(open_f), [], 0
    theta = rnd.uniform(0, 2 * math.pi)
    while t < end - 1:
        f = int(round(t))
        # Lead: where the missile will be when the round arrives (two passes are plenty).
        tof = _tof(g, fl.at(f), speed)
        tof = _tof(g, fl.at(f + tof * fps), speed)
        arrive = f + max(1, int(round(tof * fps)))
        if arrive >= end:
            break
        rounds.append([f, arrive, None])
        k += 1
        t += gap * fps if k % n else gap * fps + pause * fps
    if not rounds:
        return dict(gun=g, flight=fl, open=open_f, close=open_f, kill=False, rounds=[], kind=g['kind'])
    K = None
    if kill:
        land = next((r for r in rounds if r[1] >= open_f + walk), rounds[-1])
        K = land[1]
        if K >= end - 1 or K <= fl.ignite + 1:
            kill, K = False, None
        else:
            rounds = [r for r in rounds if r[0] <= land[0] + 2]
    for j, r in enumerate(rounds):
        p = fl.at(r[1])
        a, b = _perp((p - g['at']).normalized(), rnd)
        if kill:
            e = error_m * max(0.0, (K - r[1]) / max(K - rounds[0][1], 1))
            if r[1] > K:          # rounds already in the air when it pops: through the cloud
                e = error_m * .15 * (r[1] - K)
        else:
            e = error_m * (.45 + .35 * math.sin(.9 * j + theta))
        ang = theta + 1.3 * j
        r[2] = p + (a * math.cos(ang) + b * math.sin(ang)) * e
    if kill:
        fl.kill(K, by=g['label'])
    close = rounds[-1][0] + 2
    return dict(gun=g, flight=fl, open=open_f, close=close, kill=kill, K=K, rounds=rounds, kind=g['kind'])


def _laser(fl, g, open_f, kill, rnd, fps, walk_s):
    """A PD laser's beam on one missile: it snaps on (a few frames of search), holds and burns."""
    snap = int(round((walk_s if walk_s is not None else .2) * fps))
    burn = int(round(.3 * fps))
    end = fl.end['frame']
    K = open_f + snap + burn
    if not kill or K >= end - 1:
        kill = False
        close = min(end - 1, open_f + snap + int(round(.6 * fps)))
    else:
        close = K
    beam = []
    theta = rnd.uniform(0, 2 * math.pi)
    for f in range(open_f, close + 1):
        p = fl.at(f)
        a, b = _perp((p - g['at']).normalized(), rnd)
        lock = min(1.0, (f - open_f) / max(snap, 1))
        e = 14.0 * (1 - lock) if kill else 14.0 * (.5 + .5 * math.sin(.8 * (f - open_f) + theta))
        ang = theta + .9 * (f - open_f)
        beam.append((f, p + (a * math.cos(ang) + b * math.sin(ang)) * e))
    if kill:
        fl.kill(K, by=g['label'], hot_from=open_f + snap)
    return dict(gun=g, flight=fl, open=open_f, close=close, kill=kill, K=K if kill else None, beam=beam,
                kind='laser')


class Burst:
    def __init__(self, frame, at, kill=None):
        self.frame, self.at, self.kill = int(frame), Vector(at), kill


def plan_flak(centre, flights, radius_m=200.0, ring=10, window=(0, 24), launch_s=.45, seed=0, kills=None, fps=24):
    """Canister flak around a ship at world `centre`: one burst on each inbound flight where it
    crosses `radius_m` (killing it, or only `kills` of them), plus `ring` more scattered over
    the shell inside `window` (frames), so the ring reads even where nothing is coming.
    Returns [Burst]; each burst's canister leaves the hull `launch_s` before it."""
    rnd = random.Random(f'swfx-flak|{seed}')
    C = Vector(centre)
    out = []
    n_kill = 0
    for fl in flights:
        if fl.end is None or fl.end['kind'] not in ('hit', 'burnout'):
            continue
        f = next((f for f in range(fl.ignite + 1, fl.end['frame']) if (fl.at(f) - C).length <= radius_m), None)
        if f is None:
            continue
        do_kill = kills is None or n_kill < kills
        p = fl.at(f)
        if do_kill:
            fl.kill(f, by='canister flak')
            n_kill += 1
        out.append(Burst(f, p + Vector((rnd.gauss(0, 3), rnd.gauss(0, 3), rnd.gauss(0, 3))), fl if do_kill else None))
        for k in range(2):           # a canister throws a cluster, not one burst
            out.append(Burst(f + rnd.randint(-2, 3), p + Vector([rnd.gauss(0, 18) for _ in range(3)])))
    f0, f1 = window
    for k in range(ring):
        v = Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, .5))).normalized()
        out.append(Burst(rnd.randint(f0, f1), C + v * radius_m * rnd.uniform(.8, 1.15)))
    return out


class Flare:
    """One decoy: thrown off an anchor at `speed_mps`, slowing (no air: it is a decoy charge
    braking on its own thrust), curving a little, burning `life_s`."""

    def __init__(self, frame, at, axis, speed_mps, life_s, rnd):
        self.frame, self.p0 = int(frame), Vector(at)
        d = Vector(axis).normalized()
        a, b = _perp(d, rnd)
        self.d = (d + a * rnd.gauss(0, .25) + b * rnd.gauss(0, .25)).normalized()
        self.curve = (a * rnd.gauss(0, 1) + b * rnd.gauss(0, 1)) * speed_mps * .12
        self.v0, self.tau, self.life = speed_mps, 1.1, int(round(life_s * 24))
        self.flicker = rnd.random()

    def at(self, f):
        t = max(0.0, (f - self.frame) / 24.0)
        s = self.v0 * self.tau * (1 - math.exp(-t / self.tau))
        return self.p0 + self.d * s + self.curve * t * t * .5

    @property
    def last(self):
        return self.frame + self.life


def plan_flares(anchors, frame, per_anchor=2, interval=4, speed_mps=90.0, life_s=3.5, seed=0):
    """Flares from each (world position, world axis) anchor, `per_anchor` each, `interval`
    frames apart. Returns [Flare]."""
    rnd = random.Random(f'swfx-flares|{seed}')
    out = []
    for i, (p, d) in enumerate(anchors):
        for k in range(per_anchor):
            out.append(Flare(frame + k * interval + rnd.randint(0, 1), p, d, speed_mps * rnd.uniform(.8, 1.2),
                             life_s * rnd.uniform(.85, 1.1), rnd))
    return out


def decoy(flights, flares, frame, count=1, seed=0):
    """`count` seekers take a flare on `frame`: each diverts onto the nearest live flare."""
    rnd = random.Random(f'swfx-decoy|{seed}')
    live = [fl for fl in flights if fl.end and fl.end['kind'] == 'hit' and fl.ignite < frame < fl.end['frame'] - 2]
    rnd.shuffle(live)
    out = []
    for fl in live[:count]:
        fr = min((x for x in flares if x.frame < frame < x.last), key=lambda x: (x.at(frame) - fl.at(frame)).length,
                 default=None)
        if fr is not None:
            fl.divert(frame, fr.at, by='flare')
            out.append((fl, fr))
    return out


def flare_anchors(ship):
    """(world position, world axis) of a placed ship's `Anchor launcher <i> flare muzzle`
    empties (hull objects: the Colossus's four)."""
    out = []
    for o in sorted(ship.hull.all_objects, key=lambda o: o.name):
        if 'flare muzzle' in o.name:
            m = mp.space_of(ship) @ mp.anchor_matrix(o)
            out.append((m.translation.copy(), (m.to_3x3() @ Vector((1, 0, 0))).normalized()))
    return out


# ======================================================================================= keying

def _bpy():
    import bpy
    return bpy


def _rod_mesh(name, mat, sides=8):
    """A unit rod from x=-1 to 0 (radius 1): scaled to a tracer or a beam."""
    bpy = _bpy()
    me = bpy.data.meshes.get(name)
    if me:
        return me
    import swfx_guns
    me = swfx_guns._cylinder_mesh(name, 1.0, -1.0, 0.0, sides, mat)
    return me


def _flash_gun(ctx, label, frame, rnd):
    """The kit's own muzzle flash on one mount for one round (the same keys fire() makes)."""
    import swfx_guns
    rig = swfx_guns._RIGS.get(next((k for k, v in swfx_guns._RIGS.items()
                                     if swfx_guns.gun_label(v['anchor']) == label), None))
    env, seed = f'gun_flash_{label}', f'gun_seed_{label}'
    prof = swfx_guns.FLASH_PROFILES[rnd.randrange(len(swfx_guns.FLASH_PROFILES))]
    gain = rnd.uniform(.82, 1.18)
    for j, v in enumerate(prof):
        swfx_guns._key_env(ctx, env, frame + j, v * gain)
    ctx.key(seed, frame, rnd.random(), 'CONSTANT')
    swfx_guns._key_zero(ctx, env, frame + len(prof))
    swfx_guns._key_zero(ctx, env, frame - 1)
    box = rig and rig.get('flash')
    if box is not None:
        lit = rig.setdefault('flash_frames', swfx_guns._shown_frames(box))
        lit.update(range(frame, frame + len(prof)))
        swfx_guns._rebuild_visibility(box, lit)


def key_screen(ctx, engagements, space, tracer_m=(14.0, .35), mpu=None):
    """Key one defending ship's PD fire (inside its ship.kit()): flashes and tracers, or beams.
    space: that ship's mp.space_of(ship)."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    coll = ctx.collection('Guns')
    inv = space.inverted()
    mpu = mpu or swfx_core.METRES_PER_UNIT
    tmat = swfx_guns._tracer_material(ctx)
    rec = []
    for e in engagements:
        g = e['gun']
        rnd = random.Random(f"swfx-pd-key|{g['label']}|{e['open']}")
        muzzle = inv @ g['at']
        if e['kind'] == 'laser':
            rec.append(_key_beam(ctx, coll, e, inv, muzzle, mpu, rnd))
            continue
        L, R = tracer_m[0] / mpu, tracer_m[1] / mpu
        for j, (f, arrive, aim) in enumerate(e['rounds']):
            _flash_gun(ctx, g['label'], f, rnd)
            tgt = inv @ aim
            d = (tgt - muzzle)
            dist = d.length
            d.normalize()
            tr = bpy.data.objects.new(f"{PREFIX} tracer {g['label']} f{f:04d}", _rod_mesh(f'{PREFIX} rod mesh', tmat))
            coll.objects.link(tr)
            tr.visible_shadow = False
            tr.rotation_mode = 'QUATERNION'
            tr.rotation_quaternion = d.to_track_quat('X', 'Z')
            tr.scale = (L, R, R)
            span = max(arrive - f, 1)
            over = 3 if not (e['kill'] and arrive == e.get('K')) else 0
            p0 = muzzle + d * min(L, dist * .2)
            p1 = muzzle + d * dist
            p2 = p1 + d * (dist / span) * over
            frames = [f, arrive] + ([arrive + over] if over else [])
            locs = [p0, p1] + ([p2] if over else [])
            for i in range(3):
                swfx_guns._bake(tr, 'location', i, frames, [p[i] for p in locs])
            swfx_guns._bake(tr, 'color', 0, [f, arrive, arrive + over + 1], [1.0, .9, 0.0])
            tr.color = (1, 0, 0, 1)
            swfx_guns._visible_between(tr, f, arrive + over)
        rec.append(dict(gun=g['label'], target=e['flight'].label, open=e['open'], close=e['close'],
                        kill=e['kill'], K=e.get('K'), rounds=len(e['rounds'])))
    return rec


def beam_material(ctx):
    """The PD laser: a white core in a deep red fringe, additive, its ROUGH texture a noise
    that slides along the beam with the frame. Object colour R is the beam's power (keyed)."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    name = f'{PREFIX} laser beam'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = swfx_guns._G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    c = g.clamp01(g.sub(1.0, lw.outputs['Facing']))
    now = swfx_core.value_node(ctx, nodes, 'frame', 'frame', ())
    rough = g.noise(tc.outputs['Generated'], g.mul(now, .37), 9.0, 2.0, .6)
    flick = g.add(.55, g.mul(g.clamp01(g.mul(g.sub(rough, .3), 2.0)), .9))
    core = g.pow(c, 14.0)
    fringe = g.pow(c, 2.0)
    mix = nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    links.new(g.clamp01(g.mul(core, 1.5)), mix.inputs['Factor'])
    mix.inputs['A'].default_value = (*PD_LASER['fringe'], 1)
    mix.inputs['B'].default_value = (*PD_LASER['core'], 1)
    em = nodes.new('ShaderNodeEmission')
    links.new(mix.outputs['Result'], em.inputs['Color'])
    links.new(g.mul(g.mul(g.add(g.mul(core, 4.0), fringe), flick), g.mul(sep.outputs[0], 12.0)), em.inputs['Strength'])
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


def _key_beam(ctx, coll, e, inv, muzzle, mpu, rnd, width_m=.9):
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    g = e['gun']
    ob = bpy.data.objects.new(f"{PREFIX} laser {g['label']} f{e['open']:04d}", _rod_mesh(f'{PREFIX} beam mesh',
                                                                                          beam_material(ctx), 10))
    coll.objects.link(ob)
    ob.visible_shadow = False
    ob.rotation_mode = 'QUATERNION'
    frames, sx, quats, power = [], [], [], []
    q_prev = None
    for f, aim in e['beam']:
        t = inv @ aim
        d = t - muzzle
        frames.append(f)
        sx.append(d.length)
        q = d.normalized().to_track_quat('X', 'Z')
        if q_prev is not None and q.dot(q_prev) < 0:
            q.negate()
        quats.append(q)
        q_prev = q
        power.append(rnd.uniform(.8, 1.0))
    # The rod runs x -1..0, so it is placed at the far end and points back along the beam.
    ends = [inv @ aim for _, aim in e['beam']]
    for i in range(3):
        swfx_guns._bake(ob, 'location', i, frames, [p[i] for p in ends])
    for i in range(4):
        swfx_guns._bake(ob, 'rotation_quaternion', i, frames, [q[i] for q in quats])
    swfx_guns._bake(ob, 'scale', 0, frames, sx)
    swfx_guns._bake(ob, 'scale', 1, frames, [width_m / mpu] * len(frames))
    swfx_guns._bake(ob, 'scale', 2, frames, [width_m / mpu] * len(frames))
    swfx_guns._bake(ob, 'color', 0, frames, power)
    swfx_guns._visible_between(ob, frames[0], frames[-1])
    # Flares at the emitter and on the missile, and the emitter's light.
    glow = mp.glow_material(ctx, f'{PREFIX} laser glow', PD_LASER['glow'], strength=16.0, core=.5)
    for tag, places in (('emitter', [muzzle] * len(frames)), ('impact', ends)):
        fl = bpy.data.objects.new(f"{PREFIX} laser {tag} {g['label']} f{e['open']:04d}",
                                  mp._sphere_mesh(f'{PREFIX} glow mesh', glow))
        coll.objects.link(fl)
        fl.visible_shadow = False
        r = (2.2 if tag == 'emitter' else 3.2) / mpu
        fl.scale = (r, r, r)
        for i in range(3):
            swfx_guns._bake(fl, 'location', i, frames, [p[i] for p in places])
        swfx_guns._bake(fl, 'color', 0, frames, power)
        swfx_guns._visible_between(fl, frames[0], frames[-1])
    lt = swfx_core.point_light(f"{PREFIX} laser light {g['label']} f{e['open']:04d}", coll, PD_LASER['glow'], 0.0,
                               radius=.5)
    lt.location = muzzle
    for f, v in ((frames[0] - 1, 0.0), (frames[0], 3000.0), (frames[-1], 3000.0), (frames[-1] + 1, 0.0)):
        lt.data.energy = v
        lt.data.keyframe_insert('energy', frame=f)
    return dict(gun=g['label'], target=e['flight'].label, open=e['open'], close=e['close'], kill=e['kill'],
                K=e.get('K'), beam_frames=len(frames))


def key_flak(ctx, bursts, space, launch_from, launch_s=.45, mpu=None, fps=24):
    """Key canister flak on the firing ship (inside its ship.kit()): a dim canister streak from
    the hull to each burst, then the burst's flash and its brief cloud. launch_from: the world
    point on the hull the canisters leave from."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    coll = ctx.collection('Guns')
    inv = space.inverted()
    mpu = mpu or swfx_core.METRES_PER_UNIT
    tmat = swfx_guns._tracer_material(ctx)
    src = inv @ Vector(launch_from)
    lead = int(round(launch_s * fps))
    for k, b in enumerate(bursts):
        here = inv @ b.at
        rnd = random.Random(f'swfx-flak-key|{k}|{b.frame}')
        f0 = b.frame - lead
        c = bpy.data.objects.new(f'{PREFIX} canister {k} f{b.frame:04d}', _rod_mesh(f'{PREFIX} rod mesh', tmat))
        coll.objects.link(c)
        c.visible_shadow = False
        c.rotation_mode = 'QUATERNION'
        d = (here - src).normalized()
        c.rotation_quaternion = d.to_track_quat('X', 'Z')
        c.scale = (6.0 / mpu, .18 / mpu, .18 / mpu)
        for i in range(3):
            swfx_guns._bake(c, 'location', i, [f0, b.frame], [src[i], here[i]])
        swfx_guns._bake(c, 'color', 0, [f0, b.frame], [.35, .35])
        c.color = (.35, 0, 0, 1)
        swfx_guns._visible_between(c, f0, b.frame - 1)
        mp._flash(ctx, coll, f'{PREFIX} flak {k} f{b.frame:04d}', here, b.frame, FLAK['flash'],
                  rnd.uniform(6, 10) / mpu, frames=5, peak=1.3, heat=.9, light_w=1.5e4 if k % 3 == 0 else 0.0,
                  grow=2.0)
        mp._flash(ctx, coll, f'{PREFIX} flak cloud {k} f{b.frame:04d}', here, b.frame + 1, FLAK['cloud'],
                  rnd.uniform(12, 18) / mpu, frames=16, peak=.18, heat=0.0, grow=.8)
    return len(bursts)


def key_flares(ctx, flares, space, mpu=None):
    """Key flares on the ship that throws them (inside its ship.kit()): a flickering
    white-orange glare, a light, and a short bright trail left in place."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    coll = ctx.collection('Guns')
    inv = space.inverted()
    mpu = mpu or swfx_core.METRES_PER_UNIT
    mat = mp.glow_material(ctx, f'{PREFIX} flare glow', FLARE, strength=30.0, core=.9)
    trail_prof = dict(mp.PROFILES['harpoon'], trail=FLARE, trail_alpha=.5, trail_glow=1.5, trail_r_m=1.6,
                      trail_spread=2.2, trail_life_s=1.0, ignite_s=0.0)
    for k, fr in enumerate(flares):
        rnd = random.Random(f'swfx-flare-key|{k}|{fr.frame}')
        frames = list(range(fr.frame, fr.last + 1))
        locs = [inv @ fr.at(f) for f in frames]
        ob = bpy.data.objects.new(f'{PREFIX} flare {k} f{fr.frame:04d}', mp._sphere_mesh(f'{PREFIX} glow mesh', mat))
        coll.objects.link(ob)
        ob.visible_shadow = False
        for i in range(3):
            swfx_guns._bake(ob, 'location', i, frames, [p[i] for p in locs])
        r = 3.5 / mpu
        size = [r * (.6 + .4 * min(1, j / 3)) * (1 - .5 * (j / len(frames)) ** 3) for j in range(len(frames))]
        for i in range(3):
            swfx_guns._bake(ob, 'scale', i, frames, size)
        flick = [(1.0 + .35 * math.sin(2.7 * j + 6.3 * fr.flicker) * rnd.uniform(.6, 1)) *
                 (1 - (j / len(frames)) ** 4) for j in range(len(frames))]
        swfx_guns._bake(ob, 'color', 0, frames, flick)
        swfx_guns._bake(ob, 'color', 1, [frames[0]], [.5], 'CONSTANT')
        swfx_guns._visible_between(ob, frames[0], frames[-1])
        lt = swfx_core.point_light(f'{PREFIX} flare light {k} f{fr.frame:04d}', coll, FLARE, 0.0, radius=1.0)
        lt.parent = ob
        lt.data.keyframe_insert('energy', frame=frames[0] - 1)
        lt.data.energy = 2.0e4
        lt.data.keyframe_insert('energy', frame=frames[0])
        lt.data.keyframe_insert('energy', frame=frames[-1])
        lt.data.energy = 0.0
        lt.data.keyframe_insert('energy', frame=frames[-1] + 1)
        # Its trail: a Flight carrying the flare's path, through the missiles' own contrail.
        fake = mp.Flight('flare', trail_prof, fr.frame, fr.p0, fr.d, space, label=f'flare{k}')
        fake.pos = [fr.at(f) for f in frames]
        fake.ignite = fr.frame
        fake.end = dict(kind='burnout', frame=fr.last, point=fr.at(fr.last), on=None, target=None, by=None)
        mp._contrail(ctx, coll, fake, inv, mpu)
    return len(flares)
