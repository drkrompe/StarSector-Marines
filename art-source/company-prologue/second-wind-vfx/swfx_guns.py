"""Gun effects for the Second Wind's four turrets.

Its fit from the shared turret kit (sw_turrets): Vulcans on turrets 1 and 4
('barrel 1' / 'barrel 2'); 2 and 3 are empty housings and fire nothing.
Builds from before 2026-10-20 carried the old
gun houses: small mounts 1-3 and the medium twin (turret 4, 'port gun' and
'starboard gun'), which the tags below still read. Everything rides the gun anchors
(swfx_anchors.GunAnchor: an empty at the muzzle, local +X along
the barrel, local +Z as near hull-dorsal as allowed), so the effects follow the
model. Effect geometry is authored in mount units and scaled by the anchor's
mount scale (1.0 small, 1.3 the medium twin).

Per gun, build() makes a reusable rig that is dark until fired:

  flash      an emission-only volume in a small box at the mouth: a bright
             mouth core, a forward flame tongue with streaky breakup, and two
             side jets out of the slotted brake's lateral port. Its brightness
             is the per-gun envelope knob gun_flash_t{i}_{k}; its shape is
             re-rolled per shot by gun_seed_t{i}_{k} (keyed CONSTANT), so no
             two shots look the same and every shot re-renders identically.
  light      a warm point light just ahead of the mouth, driven by the same
             envelope, lighting the turret house and the hull around it.

fire() then spawns per-shot objects deterministically (seeded from turret, gun
and frame, never from time or hash randomisation):

  tracer     an additive emissive streak that leaves the muzzle along the
             barrel at gun_muzzle_velocity (m/s) and burns out over
             gun_tracer_range (m); one per gun_tracer_every rounds of that gun.
             Metres convert through swfx_core.units(), never by hand;
             one per gun_tracer_every rounds of that gun. It really travels
             (keyed location), so render motion blur stretches it.
  puff       a soft volume puff at the muzzle and brake ports that grows and
             fades: in vacuum (gun_atmosphere 0) it flashes out fast and thin,
             in air (1) it is denser, slower, and hangs about a second.
  casing     a small brass case thrown from the turret-house side (see
             EJECT below) on a tumbling ballistic path, under gravity that
             gun_gravity * gun_atmosphere (m/s^2): a ship hovering in air sees
             its brass fall; in orbit ship and brass fall together.

Projectiles, puffs and casings are parented to the anchor: they inherit the
ship's motion (correct for rounds fired from a moving ship) but would swing
with the turret if it trained mid-shot; the turrets rest at their slot angles.

Derelict: the guns are stripped, no anchors exist, and nothing is built.

Other hulls' mounts (swfx_hull MOUNTS, swfx_anchors.GunAnchor):

  barrels    a mount with more than two barrels tags them 'barrel <k>' and
             each gets its own rig and knobs, gun_flash_t{i}_{k}; the Second
             Wind's 'gun' / 'port gun' / 'starboard gun' keep k 1, 1 and 2.
             Overlapping flash boxes on one mount are padded apart a little,
             so Cycles never cuts one off at a shared face.
  launcher   a missile mount (MOUNTS kind 'launcher', tubes 'tube <k>') gets
             no muzzle flash, tracer or brass. Its rig is a launch bloom
             (ignition at the cell mouth and backblast out of its rear) and
             a light, on gun_launch_t{i}_{k}; fire() keys that and launches
             a missile that leaves the tube at gun_missile_velocity and
             accelerates at gun_missile_accel along the line of fire, its
             motor burning, out to gun_missile_range, shedding motor smoke
             along its first stretch. The launcher knobs exist only when a
             launcher does, so a hull without one keeps the gun knobs alone.
  energy     an energy projectile mount (MOUNTS kind 'energy': an ion cannon,
             a blaster) gets the gun rig and knobs, but its flash, flash light
             and bolt take the weapon's own colour (the profile's
             ENERGY_COLOURS {mount index: linear rgb}, else ion blue) running
             to white where hottest, instead of a blackbody flame; it throws no
             propellant puff and no brass. Opt-in: 'gun' mounts are untouched.
"""
import math
import random

import bpy
from mathutils import Matrix, Vector

import swfx_core
import swfx_hull
import swfx_anchors

GROUP = 'Guns'
PREFIX = 'SWFX guns'

# Muzzle-to-mount-centre distance along -X and house half-width, in mount
# units (sw_turrets: barrel origin 3.0 ahead of centre; recoil sleeve 1.7,
# chase 2.6L, tube 2.3L, brake 1.0, L = 1.25 on the medium twin; house sides
# raked from 2.5 to 1.95 half-width).
def _muzzle_back(twin):
    L = 1.25 if twin else 1.0
    return 3.0 + 1.7 + 2.6 * L + 2.3 * L + 1.0


# Casing ejection port, anchor-local in mount units: on the turret-house side
# wall at barrel height, a little ahead of the house's mid-length. Small
# mounts eject to the gun's right (starboard of the barrel, -Y local); on the
# twin each gun ejects through its own outboard wall.
HOUSE_HALF = 2.3
EJECT_HOUSE_X = 0.2      # house-local x of the port (house spans -2.5..3.0)
EJECT_Z = -0.15

KNOBS = [
    # name, default, lo, hi, description
    ('gun_flash_gain', 1.0, 0.0, 4.0, 'master muzzle-flash brightness (volume and light)'),
    ('gun_flash_light', 2500.0, 0.0, 20000.0, 'flash point-light power in W at envelope 1'),
    ('gun_smoke_gain', 1.0, 0.0, 4.0, 'muzzle-puff density multiplier'),
    ('gun_atmosphere', 0.0, 0.0, 1.0, '0 vacuum (fast thin dispersal) .. 1 air (denser, lingering puffs, '
                                      'casings fall); timing read at fire()'),
    ('gun_tracer_gain', 1.0, 0.0, 4.0, 'tracer brightness'),
    ('gun_tracer_every', 2.0, 1.0, 10.0, 'one tracer per N rounds of each gun (read at fire())'),
    # Real autocannon rounds leave at ~1000 m/s, which crosses the whole
    # ship in a frame at 24 fps; 400 m/s is the cinematic default.
    ('gun_muzzle_velocity', 400.0, 50.0, 1500.0, 'tracer speed in m/s (read at fire())'),
    ('gun_tracer_range', 280.0, 30.0, 3000.0, 'tracer burnout range in metres (read at fire())'),
    ('gun_gravity', 9.81, 0.0, 30.0, 'gravity on ejected brass, m/s^2, scaled by gun_atmosphere (read at fire())'),
    ('gun_casings', 1.0, 0.0, 1.0, 'eject brass casings (>= .5 on; read at fire())'),
]
# Missile mounts only (made when the hull has a launcher; read at fire() unless noted).
LAUNCH_KNOBS = [
    ('gun_launch_gain', 1.0, 0.0, 4.0, 'master launch-bloom brightness (volume and light)'),
    ('gun_launch_light', 4000.0, 0.0, 60000.0, 'launch point-light power in W at envelope 1'),
    ('gun_missile_gain', 1.0, 0.0, 4.0, 'missile motor flame brightness'),
    ('gun_missile_velocity', 40.0, 0.0, 400.0, 'missile speed leaving the tube, m/s'),
    ('gun_missile_accel', 220.0, 0.0, 2000.0, 'missile acceleration under its motor, m/s^2'),
    ('gun_missile_range', 1200.0, 50.0, 6000.0, 'distance at which the missile leaves the shot, metres'),
    ('gun_missile_smoke', 1.0, 0.0, 4.0, 'motor smoke trail density multiplier'),
]


# --- small helpers ---------------------------------------------------------
class _G:
    """Terse shader-math builder over one node tree."""

    def __init__(self, nodes, links):
        self.n, self.l = nodes, links

    def op(self, op, a, b=None, clamp=False):
        return swfx_core.math_node(self.n, self.l, op, a, b, clamp)

    def add(self, a, b): return self.op('ADD', a, b)
    def sub(self, a, b): return self.op('SUBTRACT', a, b)
    def mul(self, a, b): return self.op('MULTIPLY', a, b)
    def div(self, a, b): return self.op('DIVIDE', a, b)
    def pow(self, a, b): return self.op('POWER', a, b)
    def mx(self, a, b): return self.op('MAXIMUM', a, b)
    def mn(self, a, b): return self.op('MINIMUM', a, b)
    def sq(self, a): return self.op('MULTIPLY', a, a)
    def clamp01(self, a): return self.op('MINIMUM', self.op('MAXIMUM', a, 0.0), 1.0)
    def exp_neg(self, a): return self.op('EXPONENT', self.op('MULTIPLY', a, -1.0))
    def fract(self, a): return self.op('FRACT', a)

    def smooth(self, e0, e1, a):
        t = self.clamp01(self.div(self.sub(a, e0), e1 - e0))
        return self.mul(self.sq(t), self.sub(3.0, self.mul(t, 2.0)))

    def xyz(self, vec):
        s = self.n.new('ShaderNodeSeparateXYZ')
        self.l.new(vec, s.inputs[0])
        return s.outputs[0], s.outputs[1], s.outputs[2]

    def combine(self, x, y, z):
        c = self.n.new('ShaderNodeCombineXYZ')
        for i, v in enumerate((x, y, z)):
            if hasattr(v, 'node'):
                self.l.new(v, c.inputs[i])
            else:
                c.inputs[i].default_value = v
        return c.outputs[0]

    def noise(self, vec, w, scale, detail=2.0, rough=.6):
        t = self.n.new('ShaderNodeTexNoise')
        t.noise_dimensions = '4D'
        t.inputs['Scale'].default_value = scale
        t.inputs['Detail'].default_value = detail
        t.inputs['Roughness'].default_value = rough
        self.l.new(vec, t.inputs['Vector'])
        if hasattr(w, 'node'):
            self.l.new(w, t.inputs['W'])
        else:
            t.inputs['W'].default_value = w
        return t.outputs['Fac']


def _fcurves(idblock):
    ad = idblock.animation_data
    if not ad or not ad.action:
        return []
    act = ad.action
    out = list(getattr(act, 'fcurves', []) or [])
    for layer in getattr(act, 'layers', []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                out.extend(bag.fcurves)
    return out


_INTERP = {'CONSTANT': 0, 'LINEAR': 1, 'BEZIER': 2}


def _bake(ob, path, index, frames, values, interp='LINEAR'):
    """Write a whole F-curve in one go (fast; for per-frame casing paths)."""
    ob.keyframe_insert(path, index=index, frame=frames[0])
    fc = swfx_core.find_fcurve(ob, path, index)
    kps = fc.keyframe_points
    kps.clear()
    kps.add(len(frames))
    flat = []
    for f, v in zip(frames, values):
        flat += [float(f), float(v)]
    kps.foreach_set('co', flat)
    kps.foreach_set('interpolation', [_INTERP[interp]] * len(frames))
    fc.update()
    return fc


def _visible_between(ob, f0, f1):
    """hide_render (and viewport) keyed so ob exists only for f0..f1."""
    for path in ('hide_render', 'hide_viewport'):
        setattr(ob, path, True)
        ob.keyframe_insert(path, frame=f0 - 1)
        setattr(ob, path, False)
        ob.keyframe_insert(path, frame=f0)
        setattr(ob, path, True)
        ob.keyframe_insert(path, frame=f1 + 1)


def _shown_frames(ob):
    """Frames on which ob is keyed visible (recovers state from a saved file)."""
    fc = swfx_core.find_fcurve(ob, 'hide_render')
    if fc is None:
        return set()
    keys = sorted((int(round(k.co.x)), k.co.y) for k in fc.keyframe_points)
    out = set()
    for (f, v), nxt in zip(keys, keys[1:] + [(None, None)]):
        if v < .5 and nxt[0] is not None:
            out.update(range(f, nxt[0]))
    return out


def _rebuild_visibility(ob, frames):
    """hide_render keyed CONSTANT so ob renders exactly on `frames`."""
    fs = sorted(frames)
    keys = {fs[0] - 1: 1.0}
    for f in fs:
        if f - 1 not in frames:
            keys[f] = 0.0
        if f + 1 not in frames:
            keys[f + 1] = 1.0
    ks = sorted(keys)
    ob.hide_render = True
    ob.keyframe_insert('hide_render', frame=ks[0])
    fc = swfx_core.find_fcurve(ob, 'hide_render')
    kps = fc.keyframe_points
    kps.clear()
    kps.add(len(ks))
    flat = []
    for f in ks:
        flat += [float(f), keys[f]]
    kps.foreach_set('co', flat)
    kps.foreach_set('interpolation', [0] * len(ks))
    fc.update()


def _key_env(ctx, name, frame, value):
    """Key a CONSTANT envelope value; overlapping shots keep the larger."""
    fc = swfx_core.find_fcurve(ctx.controls, f'["{name}"]')
    if fc is not None:
        for kp in fc.keyframe_points:
            if abs(kp.co.x - frame) < 1e-4:
                if value > kp.co.y:
                    kp.co.y = value
                    kp.handle_left.y = kp.handle_right.y = value
                kp.interpolation = 'CONSTANT'
                fc.update()
                return
    ctx.key(name, frame, value, 'CONSTANT')


def _key_zero(ctx, name, frame):
    fc = swfx_core.find_fcurve(ctx.controls, f'["{name}"]')
    if fc is not None and any(abs(kp.co.x - frame) < 1e-4 for kp in fc.keyframe_points):
        return
    ctx.key(name, frame, 0.0, 'CONSTANT')


def _knob(ctx, name):
    return float(ctx.controls.get(name, dict((k[0], k[1]) for k in KNOBS + LAUNCH_KNOBS).get(name, 0.0)))


def gun_index(anchor):
    """k in the knob names: 1 for a single gun or the twin's port gun, 2 for its starboard gun;
    k itself for a multi-barrel mount's 'barrel <k>' / 'tube <k>'."""
    barrel = getattr(anchor, 'barrel', None)
    if barrel is not None:
        return barrel
    return 2 if anchor.tag == 'starboard gun' else 1


def gun_label(anchor):
    return f't{anchor.turret}_{gun_index(anchor)}'


# --- materials ---------------------------------------------------------------
ENERGY_DEFAULT = (.39, .78, 1.0)     # ion blue (vanilla ion cannon glow 100, 200, 255)


def energy_tint(a):
    """An energy mount's colour (linear rgb), or None for a gun or launcher."""
    if getattr(a, 'kind', 'gun') != 'energy':
        return None
    return tuple(swfx_anchors.ENERGY_COLOURS.get(a.turret, ENERGY_DEFAULT))


def _flash_material(ctx, label, tint=None):
    """Emission-only volume: mouth core + forward tongue + brake side jets,
    broken up by seeded noise. Object coordinates are mount units (the box
    is scaled by the mount scale), origin at the brake mouth, +X downrange."""
    env_name, seed_name = f'gun_flash_{label}', f'gun_seed_{label}'
    m, nodes, links = swfx_core.node_material(f'{PREFIX} flash {label}')
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    p = tc.outputs['Object']
    x, y, z = g.xyz(p)
    env = swfx_core.value_node(ctx, nodes, 'envelope', f'{env_name}*gun_flash_gain', (env_name, 'gun_flash_gain'))
    seed = swfx_core.value_node(ctx, nodes, 'shot seed', seed_name, (seed_name,))
    sa = g.fract(g.mul(seed, 7.137))
    sb = g.fract(g.mul(seed, 13.71))
    sc = g.fract(g.mul(seed, 29.3))

    rr = g.add(g.sq(y), g.sq(z))
    # Mouth core: a hot ball just out of the brake.
    core = g.exp_neg(g.add(g.mul(g.sq(g.sub(x, .2)), 9.0), g.mul(rr, 18.0)))
    # Forward tongue: length 1.6..3.4 per shot, widening, tapering to nothing.
    L = g.add(1.6, g.mul(sa, 1.8))
    t = g.clamp01(g.div(x, L))
    w = g.add(.13, g.mul(t, .36))
    tongue = g.mul(g.exp_neg(g.div(rr, g.sq(w))), g.pow(g.sub(1.0, t), 1.6))
    tongue = g.mul(tongue, g.smooth(-.15, .25, x))
    # Side jets out of the brake's lateral port (brake centre at x = -.5,
    # sides at |y| = .48): out along +/-Y, raked slightly aft.
    ay = g.op('ABSOLUTE', y)
    u = g.sub(ay, .45)
    Ls = g.add(.9, g.mul(sb, .9))
    tj = g.clamp01(g.div(u, Ls))
    xc = g.sub(-.5, g.mul(u, .18))
    dj = g.add(g.sq(g.sub(x, xc)), g.sq(z))
    wj = g.add(.12, g.mul(tj, .38))
    side = g.mul(g.exp_neg(g.div(dj, g.sq(wj))), g.pow(g.sub(1.0, tj), 1.8))
    side = g.mul(side, g.smooth(-.05, .12, u))
    # Per-shot side balance: one port often vents harder than the other.
    bal = g.add(.65, g.mul(g.op('SIGN', y), g.mul(g.sub(sc, .5), .7)))
    side = g.mul(side, bal)

    # Breakup: billowy noise plus streaks along the line of fire (noise on the
    # angle about the barrel, stretched downrange) -> a ragged star, not a cone.
    w4 = g.mul(seed, 97.0)
    billow = g.noise(p, w4, 2.6, 3.0, .65)
    ang = g.op('ARCTAN2', z, y)
    streak = g.noise(g.combine(g.mul(ang, 1.4), g.mul(x, .25), 0.0), g.add(w4, 3.0), 2.2, 2.0, .5)
    brk = g.mul(g.clamp01(g.mul(g.sub(billow, .28), 2.4)), g.add(.2, g.mul(g.pow(streak, 3.0), 3.4)))
    field = g.add(g.mul(core, 5.0), g.mul(g.add(g.mul(tongue, 1.5), g.mul(side, 2.0)), brk))
    field = g.add(field, g.mul(core, g.mul(brk, 1.5)))
    # Box-safe ellipsoid mask (Generated spans the box 0..1).
    gx, gy, gz = g.xyz(tc.outputs['Generated'])
    ell = g.add(g.add(g.sq(g.sub(gx, .5)), g.sq(g.sub(gy, .5))), g.sq(g.sub(gz, .5)))
    mask = g.pow(g.mx(g.sub(1.0, g.mul(ell, 4.0)), 0.0), .5)
    field = g.pow(g.mul(field, mask), 1.35)

    # Hot frames are white-yellow; decay frames and the fringe cool to orange.
    hot = g.clamp01(g.mul(field, env))
    temp = g.add(1300.0, g.mul(g.pow(hot, .6), 3600.0))
    em = nodes.new('ShaderNodeEmission')
    if tint is None:
        bb = nodes.new('ShaderNodeBlackbody')
        links.new(temp, bb.inputs[0])
        links.new(bb.outputs[0], em.inputs['Color'])
    else:
        # An energy discharge: the weapon's colour, white only in its hottest core.
        mix = nodes.new('ShaderNodeMix')
        mix.data_type = 'RGBA'
        links.new(g.clamp01(g.mul(g.pow(hot, 1.5), .75)), mix.inputs['Factor'])
        mix.inputs['A'].default_value = (*tint, 1)
        mix.inputs['B'].default_value = (1, 1, 1, 1)
        links.new(mix.outputs['Result'], em.inputs['Color'])
    links.new(g.mul(g.mul(field, env), 45.0), em.inputs['Strength'])
    links.new(em.outputs[0], out.inputs['Volume'])
    return m


def _tracer_material(ctx, tint=None):
    """Additive soft rod: transparent + emission, bright head, fading tail.
    Object colour R carries the per-tracer burn (keyed). With a tint it is an
    energy bolt: the weapon's colour along the tail, near-white at the head."""
    name = f'{PREFIX} tracer' if tint is None else f'{PREFIX} bolt ' + '-'.join(f'{c:.3f}' for c in tint)
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    gx, _, _ = g.xyz(tc.outputs['Generated'])
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    burn = sep.outputs[0]
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 2.5)
    along = g.pow(gx, 2.2)                      # tail (x=0) -> head (x=1)
    head = g.exp_neg(g.mul(g.sq(g.sub(gx, .93)), 120.0))
    gain = swfx_core.value_node(ctx, nodes, 'tracer gain', 'gun_tracer_gain', ('gun_tracer_gain',))
    strength = g.mul(g.mul(g.add(along, g.mul(head, 2.0)), g.mul(facing, burn)), g.mul(gain, 90.0))
    em = nodes.new('ShaderNodeEmission')
    # Burning tracer composition: red-orange tail, near-white head.
    mix = nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    links.new(g.clamp01(g.add(g.mul(head, 1.2), g.mul(along, .25))), mix.inputs['Factor'])
    mix.inputs['A'].default_value = (1.0, .28, .06, 1) if tint is None else (*tint, 1)
    mix.inputs['B'].default_value = (1.0, .82, .55, 1) if tint is None else \
        (*(.55 + .45 * c for c in tint), 1)
    links.new(mix.outputs['Result'], em.inputs['Color'])
    links.new(strength, em.inputs['Strength'])
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


def _smoke_material(ctx):
    """Soft propellant-gas puff; object colour R is the puff's own fade, G a
    seed. Object coordinates span -1..1 in the (scaled) unit box."""
    name = f'{PREFIX} smoke'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    p = tc.outputs['Object']
    x, y, z = g.xyz(p)
    r2 = g.add(g.add(g.sq(x), g.sq(y)), g.sq(z))
    shell = g.pow(g.mx(g.sub(1.0, r2), 0.0), 1.4)
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    fade, seed = sep.outputs[0], sep.outputs[1]
    w = g.mul(seed, 71.0)
    n1 = g.noise(p, w, 2.6, 5.0, .66)
    lumps = g.pow(g.clamp01(g.mul(g.sub(n1, .40), 3.2)), 1.5)
    field = g.mul(shell, lumps)
    atm = swfx_core.value_node(ctx, nodes, 'atmosphere density', '(0.12+0.88*gun_atmosphere)*gun_smoke_gain',
                               ('gun_atmosphere', 'gun_smoke_gain'))
    vol = nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Color'].default_value = (.52, .5, .47, 1)
    vol.inputs['Anisotropy'].default_value = .3
    links.new(g.mul(g.mul(field, fade), g.mul(atm, 1.2)), vol.inputs['Density'])
    links.new(vol.outputs[0], out.inputs['Volume'])
    return m


def _brass_material():
    name = f'{PREFIX} brass'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    out = nodes.new('ShaderNodeOutputMaterial')
    b = nodes.new('ShaderNodeBsdfPrincipled')
    b.inputs['Base Color'].default_value = (.78, .55, .25, 1)
    b.inputs['Metallic'].default_value = 1.0
    b.inputs['Roughness'].default_value = .32
    links.new(b.outputs[0], out.inputs['Surface'])
    return m


# --- shared meshes -----------------------------------------------------------
def _cylinder_mesh(name, radius, x0, x1, sides=10, mat=None):
    me = bpy.data.meshes.get(name)
    if me:
        return me
    verts, faces = [], []
    for x in (x0, x1):
        for k in range(sides):
            a = k * math.tau / sides
            verts.append((x, radius * math.cos(a), radius * math.sin(a)))
    for k in range(sides):
        faces.append((k, (k + 1) % sides, sides + (k + 1) % sides, sides + k))
    faces.append(tuple(reversed(range(sides))))
    faces.append(tuple(range(sides, 2 * sides)))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    for poly in me.polygons:
        poly.use_smooth = True
    if mat:
        me.materials.append(mat)
    return me


def _unit_box_mesh(name, mat):
    me = bpy.data.meshes.get(name)
    if me:
        return me
    verts = [(x, y, z) for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    me.materials.append(mat)
    return me


# --- build ------------------------------------------------------------------
_RIGS = {}   # (turret, tag) -> dict; rebuilt by every build()


def _anchor(ctx, turret, tag):
    return next((g for g in ctx.anchors.guns if g.turret == turret and g.tag == tag), None)


def build(ctx, fire=None, **options):
    """Reusable per-gun rigs (flash volume, flash light, envelope/seed knobs).
    Fires nothing unless fire=[{turret, start_frame, shots, interval_frames,
    tag?}, ...] or fire='demo' is passed."""
    coll = ctx.collection(GROUP)
    for name, default, lo, hi, desc in KNOBS:
        ctx.prop(name, default, lo, hi, desc)
    _RIGS.clear()
    record = {'state': ctx.state, 'guns': [], 'shots': []}
    guns = list(ctx.anchors.guns) if ctx.spec.get('guns', True) else []
    if not guns:
        record['built'] = False
        record['reason'] = ('no gun anchors: the derelict hulk carries no guns (stripped)'
                            if ctx.state == 'derelict' else 'no gun anchors found on this build')
        return record
    record['built'] = True
    launchers = [a for a in guns if getattr(a, 'kind', 'gun') == 'launcher']
    if launchers:
        for name, default, lo, hi, desc in LAUNCH_KNOBS:
            ctx.prop(name, default, lo, hi, desc)
        record['launchers'] = [_build_launcher(ctx, coll, a) for a in launchers]
    for a in guns:
        if a in launchers:
            continue
        label = gun_label(a)
        s = a.scale
        ctx.prop(f'gun_flash_{label}', 0.0, 0.0, 2.0, f'turret {a.turret} {a.tag} flash envelope (keyed by fire())')
        ctx.prop(f'gun_seed_{label}', 0.0, 0.0, 1.0, f'turret {a.turret} {a.tag} per-shot flash shape seed')
        tint = energy_tint(a)
        mat = _flash_material(ctx, label, tint)
        box = swfx_core.box_volume(f'{PREFIX} flash {label}', coll, -1.3, 4.4, 2.6, 1.2, mat,
                                   **({'pad': .012 * (a.barrel - 1)} if getattr(a, 'barrel', None) else {}))
        swfx_core.attach(box, a.empty)
        box.scale = (s, s, s)
        box.visible_shadow = False
        box.hide_render = True       # only exists on flash frames (cheap)
        light = swfx_core.point_light(f'{PREFIX} flash light {label}', coll, tint or (1.0, .72, .42), 0.0,
                                      radius=.35 * s)
        swfx_core.attach(light, a.empty, local=(1.0 * s, 0, .25 * s))
        env = f'gun_flash_{label}'
        ctx.drive(light.data, 'energy', f'{env}*gun_flash_gain*gun_flash_light', (env, 'gun_flash_gain', 'gun_flash_light'))
        _RIGS[(a.turret, a.tag)] = dict(anchor=a, flash=box, light=light, count=0)
        record['guns'].append({'turret': a.turret, 'tag': a.tag, 'label': label, 'scale': s,
                               'flash': box.name, 'light': light.name,
                               'eject_local': [round(v, 3) for v in _eject_point(a)]})
    if fire == 'demo':
        fire = [dict(turret=t, start_frame=10 + 2 * t, shots=6, interval_frames=4) for t in (1, 2, 3, 4)]
    for spec in fire or []:
        record['shots'] += globals()['fire'](ctx, **spec)
    return record


def _eject_point(a):
    s = a.scale
    back = _muzzle_back(a.twin)
    if a.twin:
        gy = .85 if a.tag == 'port gun' else -.85
        side = 1.0 if gy > 0 else -1.0
        y = side * (HOUSE_HALF - abs(gy))
    else:
        y = -HOUSE_HALF
    return Vector((-(back - EJECT_HOUSE_X) * s, y * s, EJECT_Z * s))


def _rig(ctx, a):
    rig = _RIGS.get((a.turret, a.tag))
    if rig is None:
        rig = dict(anchor=a, count=0)
        _RIGS[(a.turret, a.tag)] = rig
    return rig


# --- firing -----------------------------------------------------------------
FLASH_PROFILES = ((1.0,), (1.0, .32), (1.0, .55, .14), (.9, .4))


def _shot(ctx, a, frame, shot_no):
    """Key one round of one gun at an integer frame. Returns its record."""
    if getattr(a, 'kind', 'gun') == 'launcher':
        return _launch(ctx, a, frame)
    coll = ctx.collection(GROUP)
    label = gun_label(a)
    rig = _rig(ctx, a)
    n = rig['count']
    rig['count'] += 1
    rnd = random.Random(f'swfx-guns|{a.turret}|{a.tag}|{frame}|{n}')
    s = a.scale
    fps = ctx.scene.render.fps or swfx_core.FPS

    # Flash envelope (1-3 frames) and its shape seed.
    prof = FLASH_PROFILES[rnd.randrange(len(FLASH_PROFILES))]
    gain = rnd.uniform(.82, 1.18)
    env, seed = f'gun_flash_{label}', f'gun_seed_{label}'
    for j, v in enumerate(prof):
        _key_env(ctx, env, frame + j, v * gain)
    ctx.key(seed, frame, rnd.random(), 'CONSTANT')
    _key_zero(ctx, env, frame + len(prof))
    _key_zero(ctx, env, frame - 1)
    box = rig.get('flash') or bpy.data.objects.get(f'{PREFIX} flash {label}')
    if box is not None:
        lit = rig.setdefault('flash_frames', _shown_frames(box))
        lit.update(range(frame, frame + len(prof)))
        _rebuild_visibility(box, lit)

    rec = {'turret': a.turret, 'tag': a.tag, 'frame': frame, 'round': n, 'flash_frames': len(prof)}

    # Tracer every Nth round of this gun.
    every = max(1, int(round(_knob(ctx, 'gun_tracer_every'))))
    if n % every == 0:
        speed = swfx_core.units(_knob(ctx, 'gun_muzzle_velocity'))
        rng = swfx_core.units(_knob(ctx, 'gun_tracer_range'))
        life = max(2, int(math.ceil(rng / speed * fps)))
        tint = energy_tint(a)
        tmesh = _cylinder_mesh(f'{PREFIX} tracer mesh', .085, -4.5, 0.0, 8, _tracer_material(ctx)) if tint is None \
            else _cylinder_mesh(f'{PREFIX} bolt mesh {label}', .55, -5.0, 0.0, 10, _tracer_material(ctx, tint))
        tr = bpy.data.objects.new(f'{PREFIX} tracer {label} f{frame:04d}', tmesh)
        coll.objects.link(tr)
        swfx_core.attach(tr, a.empty)
        tr.scale = (s, s, s)
        tr.visible_shadow = False
        # Slight dispersion: a fraction of a degree off the bore.
        yaw, pitch = rnd.gauss(0, .0035), rnd.gauss(0, .0035)
        tr.rotation_euler = (0, -pitch, yaw)
        d = Vector((math.cos(yaw), math.sin(yaw), pitch)).normalized()
        v = speed / fps
        p0, p1 = d * (v * .5), d * (v * .5 + v * life)
        for i in range(3):
            _bake(tr, 'location', i, [frame, frame + life], [p0[i], p1[i]], 'LINEAR')
        burn = [1.0, .85, 0.0]
        bf = [frame, frame + int(life * .7), frame + life]
        _bake(tr, 'color', 0, bf, burn, 'LINEAR')
        tr.color = (1, 0, 0, 1)
        _visible_between(tr, frame, frame + life)
        rec['tracer'] = tr.name

    if energy_tint(a) is not None:     # an energy bolt: no propellant gas, no brass
        return rec

    # Muzzle puff.
    atm = _knob(ctx, 'gun_atmosphere')
    life = int(round(fps * (.4 + .6 * atm) * rnd.uniform(.85, 1.15)))
    pmesh = _unit_box_mesh(f'{PREFIX} puff mesh', _smoke_material(ctx))
    pf = bpy.data.objects.new(f'{PREFIX} puff {label} f{frame:04d}', pmesh)
    coll.objects.link(pf)
    swfx_core.attach(pf, a.empty)
    pf.visible_shadow = False
    seedg = rnd.random()
    grow = (1.0 + 1.0 * (1 - atm)) * rnd.uniform(.85, 1.15)      # vacuum spreads wider
    drift = (2.6 - 1.4 * atm) * s
    rise = .5 * atm * s
    frames, sx, sy, sz, lx, lz, fade = [], [], [], [], [], [], []
    for j in range(life + 1):
        t = j / life
        e = 1 - (1 - t) ** (3.0 - 1.4 * atm)          # ease-out growth
        frames.append(frame + j)
        sx.append(s * (.7 + 1.9 * grow * e))
        sy.append(s * (.9 + 1.5 * grow * e))
        sz.append(s * (.5 + 1.3 * grow * e))
        lx.append(s * .4 + drift * e)
        lz.append(rise * t)
        a_in = min(1.0, (j + .5) / 1.5)
        fade.append(a_in * (1 - t) ** (2.2 - 1.2 * atm))
    for i, arr in enumerate((sx, sy, sz)):
        _bake(pf, 'scale', i, frames, arr)
    _bake(pf, 'location', 0, frames, lx)
    _bake(pf, 'location', 2, frames, lz)
    _bake(pf, 'color', 0, frames, fade)
    _bake(pf, 'color', 1, [frame], [seedg], 'CONSTANT')
    _visible_between(pf, frame, frame + life)
    rec['puff'] = pf.name

    # Casing.
    if _knob(ctx, 'gun_casings') >= .5:
        g = swfx_core.units(_knob(ctx, 'gun_gravity')) * atm
        # A small mount's case (~57 mm class: 0.38 m long, 0.035 m radius),
        # scaled with the mount like the gun itself.
        hl = swfx_core.units(.19)
        cmesh = _cylinder_mesh(f'{PREFIX} casing mesh', swfx_core.units(.035), -hl, hl, 10, _brass_material())
        cs = bpy.data.objects.new(f'{PREFIX} casing {label} f{frame:04d}', cmesh)
        coll.objects.link(cs)
        swfx_core.attach(cs, a.empty)
        cs.scale = (s, s, s)
        p = _eject_point(a)
        side = 1.0 if p.y > 0 else -1.0
        # Ejection ~6-9 m/s outboard, a little up and aft (metres -> units).
        vel = Vector((swfx_core.units(rnd.uniform(-1.6, .3)), side * swfx_core.units(rnd.uniform(5.8, 9.0)),
                      swfx_core.units(rnd.uniform(1.3, 3.2))))
        bpy.context.view_layer.update()
        down = a.empty.matrix_world.to_3x3().inverted() @ Vector((0, 0, -g))
        spin = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1))) * rnd.uniform(12, 30)
        r0 = Vector((rnd.uniform(0, math.tau), 0, math.pi / 2 + rnd.uniform(-.3, .3)))
        clife = int(fps * 1.4)
        f0 = frame + 1                                  # breech cycles a frame after the flash
        fr, cols = [], [[], [], [], [], [], []]
        for j in range(clife + 1):
            t = j / fps
            q = p + vel * t + down * (.5 * t * t)
            r = r0 + spin * t
            fr.append(f0 + j)
            for i in range(3):
                cols[i].append(q[i])
                cols[3 + i].append(r[i])
        for i in range(3):
            _bake(cs, 'location', i, fr, cols[i])
            _bake(cs, 'rotation_euler', i, fr, cols[3 + i])
        _visible_between(cs, f0, f0 + clife)
        rec['casing'] = cs.name
    return rec


def fire(ctx, turret, start_frame, shots, interval_frames, tag=None):
    """Fire `shots` rounds from one turret, one every `interval_frames`,
    starting at `start_frame`. tag: None (the single gun; on the twin the two
    guns alternate, port first; a multi-barrel mount or launcher ripples
    through its barrels or tubes in order), 'port gun' / 'starboard gun' /
    'barrel <k>' / 'tube <k>' (that one only), or 'both' (salvo: every
    barrel every interval). Deterministic: the same
    call always keys the same envelopes and spawns the same objects.
    Returns one record per round."""
    guns = [g for g in ctx.anchors.guns if g.turret == turret]
    if not guns or not ctx.spec.get('guns', True):
        return []
    if tag in ('port gun', 'starboard gun', 'gun') or (tag and tag.startswith(('barrel ', 'tube '))):
        guns = [g for g in guns if g.tag == tag]
        if not guns:
            return []
    guns.sort(key=gun_index)
    out = []
    for n in range(int(shots)):
        f = int(round(start_frame + n * interval_frames))
        if tag == 'both' or len(guns) == 1:
            for g in guns:
                out.append(_shot(ctx, g, f, n))
        else:
            out.append(_shot(ctx, guns[n % len(guns)], f, n))
    return out


def burst(ctx, turrets=(1, 2, 3, 4), start_frame=1, shots=8, interval_frames=3, stagger_frames=2, seed=0):
    """Several turrets firing together: each opens a little after the last
    (stagger_frames, with a deterministic +/-1 frame of slop from `seed`) and
    keeps its own rate. Returns every round's record."""
    rnd = random.Random(f'swfx-guns-burst|{seed}')
    out = []
    for k, t in enumerate(turrets):
        jitter = rnd.choice((-1, 0, 0, 1)) if stagger_frames else 0
        start = start_frame + k * stagger_frames + (jitter if k else 0)
        out += fire(ctx, t, max(start, start_frame), shots, interval_frames)
    return out


# --- launchers ----------------------------------------------------------------
def _launch_material(ctx, label, scale=1.0):
    """Emission-only volume for one tube's launch: an ignition ball at the cell
    mouth, a short forward lick as the missile clears it, and the backblast out
    of the cell's rear (launcher cells vent aft), broken up by seeded noise.
    Object coordinates are mount units, origin at the tube mouth, +X downrange;
    the backblast leaves about 3 units behind it, at the launcher's rear face.
    Volume emission adds up along the ray in world units, so the strength is
    divided by the mount scale: a launcher reads as bright at any size."""
    env_name, seed_name = f'gun_launch_{label}', f'gun_seed_{label}'
    m, nodes, links = swfx_core.node_material(f'{PREFIX} launch {label}')
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    p = tc.outputs['Object']
    x, y, z = g.xyz(p)
    env = swfx_core.value_node(ctx, nodes, 'envelope', f'{env_name}*gun_launch_gain', (env_name, 'gun_launch_gain'))
    seed = swfx_core.value_node(ctx, nodes, 'shot seed', seed_name, (seed_name,))
    sa = g.fract(g.mul(seed, 7.137))
    sb = g.fract(g.mul(seed, 13.71))
    rr = g.add(g.sq(y), g.sq(z))
    ignition = g.exp_neg(g.add(g.mul(g.sq(g.sub(x, .35)), 3.0), g.mul(rr, 5.0)))
    L = g.add(1.2, g.mul(sa, 1.2))
    t = g.clamp01(g.div(x, L))
    lick = g.mul(g.exp_neg(g.div(rr, g.sq(g.add(.25, g.mul(t, .5))))), g.pow(g.sub(1.0, t), 1.3))
    lick = g.mul(lick, g.smooth(-.1, .3, x))
    # Backblast: a fan out of the rear face (x = -3), widening as it goes aft.
    u = g.sub(-3.0, x)
    Lb = g.add(1.6, g.mul(sb, 1.4))
    tb = g.clamp01(g.div(u, Lb))
    back = g.mul(g.exp_neg(g.div(rr, g.sq(g.add(.45, g.mul(tb, 1.1))))), g.pow(g.sub(1.0, tb), 1.5))
    back = g.mul(back, g.smooth(-.2, .25, u))
    billow = g.noise(p, g.mul(seed, 61.0), 1.8, 3.0, .6)
    brk = g.add(.25, g.mul(g.clamp01(g.mul(g.sub(billow, .3), 2.2)), 1.4))
    field = g.add(g.mul(ignition, 3.0), g.mul(g.add(g.mul(lick, 1.2), g.mul(back, 1.6)), brk))
    gx, gy, gz = g.xyz(tc.outputs['Generated'])
    ell = g.add(g.add(g.sq(g.sub(gx, .5)), g.sq(g.sub(gy, .5))), g.sq(g.sub(gz, .5)))
    mask = g.pow(g.mx(g.sub(1.0, g.mul(ell, 4.0)), 0.0), .5)
    field = g.pow(g.mul(field, mask), 1.25)
    hot = g.clamp01(g.mul(field, env))
    temp = g.add(1500.0, g.mul(g.pow(hot, .6), 3300.0))
    bb = nodes.new('ShaderNodeBlackbody')
    links.new(temp, bb.inputs[0])
    em = nodes.new('ShaderNodeEmission')
    links.new(bb.outputs[0], em.inputs['Color'])
    links.new(g.mul(g.mul(field, env), 30.0 / max(scale, 1e-3)), em.inputs['Strength'])
    links.new(em.outputs[0], out.inputs['Volume'])
    return m


def _motor_material(ctx):
    """The missile's motor flame: an additive soft rod trailing the nozzle, a
    white-hot root fading through yellow to orange. Object colour R carries the
    burn (keyed: full while the motor runs)."""
    name = f'{PREFIX} missile motor'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    gx, _, _ = g.xyz(tc.outputs['Generated'])
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    burn = sep.outputs[0]
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 3.0)
    along = g.pow(gx, 3.5)                      # flame tail (x=0) -> nozzle (x=1)
    root = g.exp_neg(g.mul(g.sq(g.sub(gx, .97)), 220.0))
    gain = swfx_core.value_node(ctx, nodes, 'motor gain', 'gun_missile_gain', ('gun_missile_gain',))
    strength = g.mul(g.mul(g.add(along, g.mul(root, 3.0)), g.mul(facing, burn)), g.mul(gain, 45.0))
    em = nodes.new('ShaderNodeEmission')
    mix = nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    links.new(g.clamp01(g.add(g.mul(root, 1.3), g.mul(along, .5))), mix.inputs['Factor'])
    mix.inputs['A'].default_value = (1.0, .42, .10, 1)
    mix.inputs['B'].default_value = (1.0, .92, .74, 1)
    links.new(mix.outputs['Result'], em.inputs['Color'])
    links.new(strength, em.inputs['Strength'])
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


def _missile_material():
    name = f'{PREFIX} missile body'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    out = nodes.new('ShaderNodeOutputMaterial')
    b = nodes.new('ShaderNodeBsdfPrincipled')
    b.inputs['Base Color'].default_value = (.32, .32, .3, 1)
    b.inputs['Metallic'].default_value = .6
    b.inputs['Roughness'].default_value = .45
    links.new(b.outputs[0], out.inputs['Surface'])
    return m


def _trail_material(ctx):
    """Motor smoke: the muzzle puff's soft lumpy volume, paler, denser in air
    and thin in vacuum. Object colour R is the puff's fade, G a seed, B the
    inverse of its mount scale: density adds up along the ray in world units,
    so a big launcher's smoke is no more opaque than a small one's."""
    name = f'{PREFIX} missile smoke'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    p = tc.outputs['Object']
    x, y, z = g.xyz(p)
    r2 = g.add(g.add(g.sq(x), g.sq(y)), g.sq(z))
    shell = g.pow(g.mx(g.sub(1.0, r2), 0.0), 1.2)
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    fade, seed, thin = sep.outputs[0], sep.outputs[1], sep.outputs[2]
    n1 = g.noise(p, g.mul(seed, 53.0), 2.2, 2.0, .62)
    lumps = g.pow(g.clamp01(g.mul(g.sub(n1, .36), 2.8)), 1.3)
    atm = swfx_core.value_node(ctx, nodes, 'atmosphere density', '(0.2+0.8*gun_atmosphere)*gun_missile_smoke',
                               ('gun_atmosphere', 'gun_missile_smoke'))
    vol = nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Color'].default_value = (.66, .64, .6, 1)
    vol.inputs['Anisotropy'].default_value = .35
    links.new(g.mul(g.mul(g.mul(g.mul(shell, lumps), fade), g.mul(atm, 2.5)), thin), vol.inputs['Density'])
    links.new(vol.outputs[0], out.inputs['Volume'])
    return m


def _build_launcher(ctx, coll, a):
    """One tube's reusable rig: the launch bloom volume and its light, dark until fired."""
    label = gun_label(a)
    s = a.scale
    ctx.prop(f'gun_launch_{label}', 0.0, 0.0, 2.0, f'turret {a.turret} {a.tag} launch envelope (keyed by fire())')
    ctx.prop(f'gun_seed_{label}', 0.0, 0.0, 1.0, f'turret {a.turret} {a.tag} per-launch bloom shape seed')
    box = swfx_core.box_volume(f'{PREFIX} launch {label}', coll, -6.2, 3.0, 2.2, 2.2, _launch_material(ctx, label, s),
                               pad=.012 * ((a.barrel or 1) - 1))
    swfx_core.attach(box, a.empty)
    box.scale = (s, s, s)
    box.visible_shadow = False
    box.hide_render = True       # only exists on launch frames
    light = swfx_core.point_light(f'{PREFIX} launch light {label}', coll, (1.0, .74, .45), 0.0, radius=.5 * s)
    swfx_core.attach(light, a.empty, local=(-.5 * s, 0, .3 * s))
    env = f'gun_launch_{label}'
    ctx.drive(light.data, 'energy', f'{env}*gun_launch_gain*gun_launch_light',
              (env, 'gun_launch_gain', 'gun_launch_light'))
    _RIGS[(a.turret, a.tag)] = dict(anchor=a, flash=box, light=light, count=0)
    return {'turret': a.turret, 'tag': a.tag, 'label': label, 'scale': s, 'kind': 'launcher',
            'bloom': box.name, 'light': light.name}


LAUNCH_PROFILE = (.7, 1.0, .75, .5, .3, .16, .07)
TRAIL_PUFFS = 4           # a launch puff plus this many along the trail: smoke is most of a launch's render cost


def _launch(ctx, a, frame):
    """Key one missile launch from one tube at an integer frame. Returns its record."""
    coll = ctx.collection(GROUP)
    label = gun_label(a)
    rig = _rig(ctx, a)
    n = rig['count']
    rig['count'] += 1
    rnd = random.Random(f'swfx-launch|{a.turret}|{a.tag}|{frame}|{n}')
    s = a.scale
    fps = ctx.scene.render.fps or swfx_core.FPS

    gain = rnd.uniform(.85, 1.15)
    env, seed = f'gun_launch_{label}', f'gun_seed_{label}'
    for j, v in enumerate(LAUNCH_PROFILE):
        _key_env(ctx, env, frame + j, v * gain)
    ctx.key(seed, frame, rnd.random(), 'CONSTANT')
    _key_zero(ctx, env, frame + len(LAUNCH_PROFILE))
    _key_zero(ctx, env, frame - 1)
    box = rig.get('flash') or bpy.data.objects.get(f'{PREFIX} launch {label}')
    if box is not None:
        lit = rig.setdefault('flash_frames', _shown_frames(box))
        lit.update(range(frame, frame + len(LAUNCH_PROFILE)))
        _rebuild_visibility(box, lit)
    rec = {'turret': a.turret, 'tag': a.tag, 'frame': frame, 'round': n, 'kind': 'launcher',
           'bloom_frames': len(LAUNCH_PROFILE)}

    # Flight along the line of fire (anchor +X), a fraction of a degree off.
    v0 = swfx_core.units(_knob(ctx, 'gun_missile_velocity'))
    acc = swfx_core.units(_knob(ctx, 'gun_missile_accel'))
    rng = swfx_core.units(_knob(ctx, 'gun_missile_range'))
    if acc > 1e-6:
        t_end = (-v0 + math.sqrt(v0 * v0 + 2 * acc * rng)) / acc
    else:
        t_end = rng / max(v0, 1e-6)
    life = max(2, int(math.ceil(t_end * fps)))
    yaw, pitch = rnd.gauss(0, .006), rnd.gauss(0, .006)
    d = Vector((math.cos(yaw), math.sin(yaw), pitch)).normalized()

    def at(tsec):
        return d * (v0 * tsec + .5 * acc * tsec * tsec)

    body_mesh = _cylinder_mesh(f'{PREFIX} missile body mesh', .2, -2.2, 0.0, 10, _missile_material())
    body = bpy.data.objects.new(f'{PREFIX} missile {label} f{frame:04d}', body_mesh)
    coll.objects.link(body)
    swfx_core.attach(body, a.empty)
    body.scale = (s, s, s)
    body.rotation_euler = (0, -pitch, yaw)
    frames = [frame + j for j in range(life + 1)]
    path = [at(j / fps) for j in range(life + 1)]
    for i in range(3):
        _bake(body, 'location', i, frames, [q[i] for q in path])
    _visible_between(body, frame, frame + life)
    motor_mesh = _cylinder_mesh(f'{PREFIX} missile motor mesh', .12, -4.5, 0.0, 10, _motor_material(ctx))
    motor = bpy.data.objects.new(f'{PREFIX} missile motor {label} f{frame:04d}', motor_mesh)
    coll.objects.link(motor)
    motor.parent = body
    motor.location = (-2.2, 0, 0)
    motor.visible_shadow = False
    # The motor lights a frame after the bloom and stretches as the missile picks up speed.
    f_ign = frame + 1
    _bake(motor, 'scale', 0, [frame, f_ign, f_ign + 6], [.15, .6, 1.0])
    _bake(motor, 'color', 0, [frame, f_ign, frame + life], [0.0, 1.0, 1.0])
    motor.color = (1, 0, 0, 1)
    _visible_between(motor, f_ign, frame + life)
    rec['missile'] = body.name
    rec['flight_frames'] = life

    # Launch smoke at the mouth and the motor's trail over its first stretch,
    # each puff left where the missile was (anchor space: it rides the ship).
    atm = _knob(ctx, 'gun_atmosphere')
    smesh = _unit_box_mesh(f'{PREFIX} missile smoke mesh', _trail_material(ctx))
    puffs = []
    for k in range(TRAIL_PUFFS + 1):
        f0 = frame + (0 if k == 0 else 1 + int(round(k * k * 1.6)))
        tsec = (f0 - frame) / fps
        p0 = Vector((-1.2 * s, 0, 0)) if k == 0 else at(tsec) - d * 3.0 * s
        # Sized from the missile (body radius .2 mount units), not the mount: the launch
        # puff starts about three missile diameters across, the trail about two.
        size = s * (.6 if k == 0 else .4 + .05 * k) * rnd.uniform(.85, 1.15)
        plife = int(round(fps * (.7 + 1.6 * atm) * rnd.uniform(.85, 1.2)))
        pf = bpy.data.objects.new(f'{PREFIX} missile smoke {label} f{frame:04d} {k}', smesh)
        coll.objects.link(pf)
        swfx_core.attach(pf, a.empty)
        pf.visible_shadow = False
        pf.color = (1.0, 0.0, 1.0 / max(s, 1e-3), 1.0)
        sg = rnd.random()
        grow = (1.0 + 1.2 * (1 - atm)) * rnd.uniform(.85, 1.15)
        fr, sc, fade = [], [], []
        for j in range(plife + 1):
            t = j / plife
            e = 1 - (1 - t) ** (3.0 - 1.2 * atm)
            fr.append(f0 + j)
            sc.append(size * (.6 + 1.8 * grow * e))
            fade.append(min(1.0, (j + .5) / 1.5) * (1 - t) ** (2.0 - 1.0 * atm))
        for i in range(3):
            _bake(pf, 'scale', i, fr, sc)
            _bake(pf, 'location', i, [f0], [p0[i]], 'CONSTANT')
        _bake(pf, 'color', 0, fr, fade)
        _bake(pf, 'color', 1, [f0], [sg], 'CONSTANT')
        _visible_between(pf, f0, f0 + plife)
        puffs.append(pf.name)
    rec['smoke'] = puffs
    return rec


# --- review -----------------------------------------------------------------
def _review_lighting(ctx):
    scene = ctx.scene
    swfx_core.hide_reference_lights(scene)
    swfx_core.dark_world(scene, (.0012, .0013, .0016), 1.0)
    rc = ctx.collection(f'{GROUP} review')
    key = bpy.data.objects.get(f'{PREFIX} review key')
    if key is None:
        data = bpy.data.lights.new(f'{PREFIX} review key', 'SUN')
        data.energy = 2.6
        data.color = (1.0, .98, .95)
        data.angle = math.radians(1.0)
        key = bpy.data.objects.new(f'{PREFIX} review key', data)
        rc.objects.link(key)
        key.rotation_euler = (math.radians(50), 0, math.radians(-150))
        fdata = bpy.data.lights.new(f'{PREFIX} review fill', 'SUN')
        fdata.energy = .12
        fdata.color = (.8, .87, 1.0)
        fill = bpy.data.objects.new(f'{PREFIX} review fill', fdata)
        rc.objects.link(fill)
        fill.rotation_euler = (math.radians(115), 0, math.radians(40))
    return rc


def _world(a):
    bpy.context.view_layer.update()
    m = a.empty.matrix_world
    return m.translation.copy(), (m.to_3x3() @ Vector((1, 0, 0))).normalized()


def _render_still(scene, cam, frame, path):
    scene.camera = cam
    scene.frame_set(frame)
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def _render_anim(scene, cam, f0, f1, frames_dir, movie, quick):
    import shutil
    scene.camera = cam
    frames_dir.mkdir(parents=True, exist_ok=True)
    for old in frames_dir.glob('*.png'):
        old.unlink()
    scene.frame_start, scene.frame_end = f0, f1
    scene.render.filepath = str(frames_dir / 'f')
    bpy.ops.render.render(animation=True)
    pngs = sorted(frames_dir.glob('f*.png'))
    if pngs and movie is not None:
        tmp = frames_dir / 'seq'
        tmp.mkdir(exist_ok=True)
        for old in tmp.glob('*.png'):
            old.unlink()
        for i, pth in enumerate(pngs):
            shutil.copyfile(pth, tmp / f'{i:04d}.png')
        swfx_core.encode_movie(tmp / '%04d.png', movie)


def review(ctx, out_dir, quick=False):
    from pathlib import Path
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    final = Path(__file__).resolve().parent / 'review' / 'guns'
    dest = out_dir / 'quick' if quick else final
    dest.mkdir(parents=True, exist_ok=True)
    scene = ctx.scene
    if not ctx.anchors.guns:
        (out_dir / 'review-skipped.txt').write_text(f'state {ctx.state}: no gun anchors, nothing to review\n')
        return
    swfx_core.setup_render(scene, samples=16 if quick else 64)
    if quick:
        scene.render.resolution_percentage = 50
    scene.render.use_persistent_data = True   # ~5x faster per animation frame here
    scene.render.use_motion_blur = True
    scene.render.motion_blur_shutter = .5
    scene.cycles.volume_bounces = 1
    rc = _review_lighting(ctx)

    # (a) single flash frames on small turret 1 (port beam), two rounds.
    a3 = _anchor(ctx, 1, 'barrel 1') or _anchor(ctx, 1, 'gun')
    fire(ctx, 1, 5, 3, 6)
    m3, x3 = _world(a3)
    up = Vector((0, 0, 1))
    left3 = up.cross(x3).normalized()
    cam_a = swfx_core.look_camera(f'{PREFIX} review cam flash', rc, m3 + x3 * 5.0 + left3 * 17.0 + up * 10.0,
                                  m3 - x3 * 3.5 - up * 1.0, lens=50)
    # (b) turret 4 (the bow Vulcan; the old twin's two guns), a burst over the shoulder.
    a4p = _anchor(ctx, 4, 'barrel 1') or _anchor(ctx, 4, 'port gun')
    a4s = _anchor(ctx, 4, 'starboard gun') or a4p
    fire(ctx, 4, 104, 14, 3)
    m4 = (_world(a4p)[0] + _world(a4s)[0]) / 2
    x4 = _world(a4p)[1]
    right4 = x4.cross(up).normalized()
    centre4 = m4 - x4 * 15.4
    cam_b = swfx_core.look_camera(f'{PREFIX} review cam twin', rc, centre4 - x4 * 18.0 + right4 * 22.0 + up * 18.0,
                                  m4 - x4 * 3.0 - up * 1.0, lens=40)
    # (c) all four, wide.
    burst(ctx, (1, 2, 3, 4), 204, shots=12, interval_frames=4, stagger_frames=3, seed=1)
    lo, hi = ctx.anchors.bounds
    mid = (lo + hi) / 2
    cam_c = swfx_core.look_camera(f'{PREFIX} review cam wide', rc, mid + Vector((95, -105, 85)),
                                  mid + Vector((6, 0, 0)), lens=36)

    if quick:
        _render_still(scene, cam_a, 5, dest / 'flash-a')
        _render_still(scene, cam_a, 11, dest / 'flash-b')
        _render_still(scene, cam_b, 113, dest / 'twin-mid')
        _render_still(scene, cam_c, 222, dest / 'wide-mid')
        return
    _render_still(scene, cam_a, 5, dest / 'guns-flash-small-turret')
    _render_still(scene, cam_a, 11, dest / 'guns-flash-small-turret-round2')
    scene.cycles.samples = 48
    _render_anim(scene, cam_b, 100, 159, out_dir / 'frames-twin', dest / 'guns-twin-burst.mp4', quick)
    _render_anim(scene, cam_c, 200, 271, out_dir / 'frames-wide', dest / 'guns-all-turrets.mp4', quick)
