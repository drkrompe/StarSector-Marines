"""Gun effects for the Second Wind's four turrets.

Its fit from the shared turret kit (sw_turrets): Vulcans on turrets 1 and 2
('barrel 1' / 'barrel 2'); 3 and 4 are empty housings and fire nothing.
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
  weapon     arm(ctx, turret, '<WEAPONS key>') makes a mount fire as that game
             weapon (light MG, Vulcan, autocannons, Mark IX, Hellbore, assault
             gun, chaingun, needler, ion cannon, blasters): its flash, linger,
             round and cadence (fire_weapon) come from the game's data. Opt-in:
             an unarmed mount fires exactly as before. See "weapons" below.
"""
import math
import random
import re

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
    """Write a whole F-curve in one go (fast; for per-frame casing paths). index -1 for a
    scalar property (a light's energy)."""
    if index < 0:
        ob.keyframe_insert(path, frame=frames[0])
        index = 0
    else:
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


def _flash_material(ctx, label, tint=None, gain=1.0):
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
    links.new(g.mul(g.mul(field, env), 45.0 * gain), em.inputs['Strength'])
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
    if armed(_rig(ctx, a)):
        return _weapon_shot(ctx, a, frame)
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
    rec['puff'] = _puff(ctx, coll, a, label, frame, rnd)
    if _knob(ctx, 'gun_casings') >= .5:
        rec['casing'] = _casing(ctx, coll, a, label, frame, rnd)
    return rec


def _puff(ctx, coll, a, label, frame, rnd):
    """One round's propellant puff at the muzzle; returns its object name."""
    s = a.scale
    fps = ctx.scene.render.fps or swfx_core.FPS
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
    return pf.name


def _casing(ctx, coll, a, label, frame, rnd):
    """One round's brass case thrown from the turret-house side; returns its object name."""
    s = a.scale
    fps = ctx.scene.render.fps or swfx_core.FPS
    atm = _knob(ctx, 'gun_atmosphere')
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
    return cs.name


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


# --- weapons (opt-in): each gun reads as its game weapon ---------------------
# A mount fires as the kit's generic gun until arm() names its weapon. From then
# on its flash takes that weapon's colour, size and linger, and fire_weapon()
# keys the game's cadence (burst size and delay, cooldown, charge-up, barrel
# order) with that weapon's round: a warm streak (machine guns, Vulcan), a
# visible green-white shell (autocannons), a slow orange slug in its own glow
# (Hellbore), long white needles (needler), a white-hot plasma bolt in a
# coloured corona that lights what it passes (blasters) or a jagged ion bolt
# after a charge-up glow (ion cannon). emp_arcs() crawls lightning over a hull
# the ion bolt hits; feeder() is the Ammo Feeder's amber barrel glow. Numbers
# are the game's (weapon_data.csv, .wpn, .proj); speeds and sizes are
# cinematic metres. An unarmed mount, and every file built before arming
# existed, is untouched.
def _lin(rgb):
    """A game RGB (0-255, sRGB) as linear light."""
    return tuple((c / 255.0) ** 2.2 for c in rgb[:3])


# The weapon layer is tuned in metres (watts at metre distances, glow emission per metre), but
# like the rest of the kit it is authored into a hull-unit file, which a scene places at
# Scale(mpu) and ship_fx.metre_scale then corrects (light exposure 2*log2(mpu), volume
# emission / mpu). These two put a metre-tuned number into hull units so the round trip lands
# it where it was tuned.
def _hull_watts(w):
    return w / swfx_core.METRES_PER_UNIT ** 2


def _hull_glow(k):
    """A glow's emission per metre (object colour B) as per hull unit."""
    return k * swfx_core.METRES_PER_UNIT


def _mg(**kw):
    w = dict(energy=False, burst=5, burst_delay=.1, cooldown=.4, barrels='alternating', spread=.0035,
             flash=(1.0, .5, .2), flash_tint=None, flash_size=(1.0, 1.0, 1.0), flash_light=(255, 240, 165),
             cloud=dict(rgb=(255, 240, 165), r=(.35, 1.1), frames=4, peak=2),
             round=dict(speed=450, range=350, length=8.0, core=.09, corona=.32, fringe=(240, 220, 220),
                        hot=(245, 245, 245), core_gain=22, corona_gain=1.6),
             puff=True, brass=True)
    w.update(kw)
    return w


def _ac(**kw):
    w = dict(energy=False, burst=1, burst_delay=0, cooldown=.5, barrels='alternating', spread=.003,
             flash=(1.0, .4, .1), flash_tint=(165, 225, 185), flash_size=(1.25, 1.35, 1.35),
             flash_light=(165, 225, 185), flash_light_gain=1.6,
             cloud=dict(rgb=(165, 225, 185), r=(.7, 2.4), frames=3, peak=2),
             round=dict(speed=320, range=600, length=1.6, core=.14, corona=.22, fringe=(235, 255, 215),
                        hot=(240, 255, 230), glow=(120, 235, 150), core_gain=18, corona_gain=3.0,
                        trail=dict(length=5.0, radius=.08, gain=3.5, ember=(70, 150, 80), fleck_m=.8)),
             heavy=dict(recoil=dict(m=.2, return_s=.2), smoke=dict(r=(.4, 2.6), life_s=.8, heat_s=.12, density=1.2, glow=1.0)),
             puff=False, brass=True)
    w.update(kw)
    return w


WEAPONS = {
    # Machine guns: 5-round bursts 0.1 s apart, a 0.4 s pause (dual 0.2 s, its barrels taking
    # bursts in turn); warm white streaks; a small warm flash, 0.15 s.
    'lightmg': _mg(name='Light Machine Gun'),
    'lightdualmg': _mg(name='Light Dual Machine Gun', cooldown=.2, barrels='alternating_burst'),
    # Vulcan: twin barrels alternating at 20 rounds/s, pale gold; the two streams cross
    # (braid, metres to the crossing) so the stream zig-zags.
    'vulcan': _mg(name='Vulcan Cannon', burst=1, burst_delay=0, cooldown=.05, braid=60.0, flash=(1.0, .4), puff=False,
                  cloud=dict(rgb=(255, 240, 165), r=(.3, .9), frames=3, peak=1.75),
                  round=dict(speed=480, range=300, length=7.0, core=.08, corona=.28, fringe=(240, 220, 180),
                             hot=(245, 245, 225), core_gain=22, corona_gain=1.6)),
    # Cannons (owner, 2026-10-23): every shot an event, not a stream. A hard short flash, smoke
    # that glows going out and hangs, the barrel kicking back into its sleeve (heavy: recoil m /
    # return_s, smoke r m / life_s / heat_s; the Hellbore also a squat shock of gas, ring: a thin
    # ring read as a bar edge-on and a hoop face-on, bench 2026-10-23), and a
    # short thick hot slug, slower than a machine gun's streak, shedding an ember trail behind
    # it. Cadence is the game's (weapon_data.csv burst size, burst delay, chargedown).
    # Autocannons: green-white kinetic shells with a broad pale-green flash, one every 0.5 s.
    'lightac': _ac(name='Light Autocannon'),
    # Heavy autocannon: three shells 0.2 s apart, then 1 s.
    'heavyac': _ac(name='Heavy Autocannon', burst=3, burst_delay=.2, cooldown=1.0, flash_size=(1.5, 1.6, 1.6),
                   flash_light_gain=2.2,
                   cloud=dict(rgb=(165, 225, 185), r=(1.0, 3.2), frames=3, peak=2.2),
                   round=dict(speed=290, range=800, length=2.2, core=.22, corona=.34, fringe=(235, 255, 215),
                              hot=(240, 255, 230), glow=(120, 235, 150), core_gain=18, corona_gain=3.0,
                              trail=dict(length=8.0, radius=.11, gain=3.5, ember=(70, 150, 80), fleck_m=1.0)),
                   heavy=dict(recoil=dict(m=.35, return_s=.25),
                              smoke=dict(r=(.6, 3.6), life_s=1.0, heat_s=.15, density=1.2, glow=1.2))),
    # Mark IX: the four-round slam every 2 s.
    'mark9': _ac(name='Mark IX Autocannon', burst=4, burst_delay=.1, cooldown=2.0, flash=(1.0, .45, .12),
                 flash_size=(1.4, 1.5, 1.5), flash_light_gain=2.5,
                 cloud=dict(rgb=(165, 225, 185), r=(1.2, 3.8), frames=3, peak=2.2),
                 round=dict(speed=300, range=800, length=2.4, core=.24, corona=.36, fringe=(235, 255, 215),
                            hot=(240, 255, 230), glow=(120, 235, 150), core_gain=18, corona_gain=3.0,
                            trail=dict(length=9.0, radius=.12, gain=3.5, ember=(70, 150, 80), fleck_m=1.0)),
                 heavy=dict(recoil=dict(m=.45, return_s=.2),
                            smoke=dict(r=(.7, 4.2), life_s=1.2, heat_s=.15, density=1.2, glow=1.2))),
    # Hellbore: one heavy HE slug every 3 s, slow, orange-white; the game's biggest flash (255,120,50).
    # Here the blast is hard and short and the linger is its smoke, cooling from orange to grey.
    # No orbs (owner, 2026-10-23: S6's Manticore fired "an orb glowing blob"): the round is an
    # elongated white-hot slug with a short heat streak, and the game's 60-unit glow is left out
    # (its light stays, off the camera).
    'hellbore': dict(name='Hellbore Cannon', energy=False, burst=1, burst_delay=0, cooldown=3.0,
                     barrels='alternating', spread=.001,
                     flash=(1.0, .6, .25, .1), flash_tint=(255, 120, 50),
                     flash_size=(1.9, 2.0, 2.0), flash_light=(255, 120, 50), flash_light_gain=6.0,
                     cloud=dict(rgb=(255, 120, 50), r=(1.5, 6.5), frames=7, peak=2.6),
                     round=dict(speed=150, range=900, length=6.0, core=.45, corona=.65, fringe=(255, 165, 30),
                                hot=(255, 250, 240), core_gain=20, corona_gain=1.4, light=40000,
                                trail=dict(length=14.0, radius=.28, gain=5.0, ember=(220, 60, 15), fleck_m=1.5)),
                     heavy=dict(recoil=dict(m=1.8, return_s=.8), ring=dict(r=(.6, 3.2), frames=4, peak=1.0, thick=.5),
                                smoke=dict(r=(1.5, 9.0), life_s=2.2, heat_s=.35, density=1.6, glow=2.5, drift=.8)),
                     puff=False, brass=False),
    # Light assault gun: orange HE shells every 0.25 s.
    'lightag': _ac(name='Light Assault Gun', cooldown=.25, flash=(1.0, .5, .18), flash_tint=(255, 155, 105),
                   flash_size=(1.1, 1.2, 1.2), flash_light=(255, 155, 105), flash_light_gain=1.8,
                   cloud=dict(rgb=(255, 155, 105), r=(.7, 2.2), frames=3, peak=2),
                   round=dict(speed=300, range=600, length=1.8, core=.15, corona=.24, fringe=(255, 200, 70),
                              hot=(255, 240, 220), core_gain=18, corona_gain=2.2,
                              trail=dict(length=6.0, radius=.08, gain=3.5, ember=(200, 60, 15), fleck_m=.8)),
                   heavy=dict(recoil=dict(m=.2, return_s=.15),
                              smoke=dict(r=(.4, 2.4), life_s=.8, heat_s=.12, density=1.2, glow=1.0))),
    'chaingun': _mg(name='Assault Chaingun', burst=1, burst_delay=0, cooldown=.15, flash_size=(1.5, .8, .8),
                    cloud=dict(rgb=(255, 240, 165), r=(.5, 1.4), frames=4, peak=2),
                    round=dict(speed=420, range=450, length=5.0, core=.12, corona=.45, fringe=(255, 200, 70),
                               hot=(255, 230, 200), core_gain=18, corona_gain=2.0)),
    # Needler: 15-round bursts 0.05 s apart, long white needles, much the fastest round.
    'lightneedler': _mg(name='Light Needler', burst=15, burst_delay=.05, cooldown=4.3, spread=.002, flash=(1.0, .3),
                        flash_tint=(255, 255, 255), flash_size=(1.4, .55, .55), flash_light=(255, 255, 255),
                        cloud=dict(rgb=(255, 255, 255), r=(.3, .9), frames=5, peak=1.5),
                        round=dict(speed=900, range=700, length=22.0, core=.05, corona=.16, fringe=(255, 255, 255),
                                   hot=(255, 255, 255), core_gain=25, corona_gain=1.2),
                        brass=False),
    # Energy: no propellant gas, no brass. Ion cannon: 0.5 s charge-up, a crackling blue-white bolt.
    'ioncannon': dict(name='Ion Cannon', energy=True, burst=1, burst_delay=0, cooldown=0.0, chargeup=.5,
                      barrels='alternating', spread=.002, flash=(1.0, .4, .12), flash_tint=(100, 200, 255),
                      flash_size=(1.0, 1.0, 1.0), flash_light=(100, 200, 255),
                      cloud=dict(rgb=(100, 200, 255), r=(.5, 1.6), frames=3, peak=2),
                      charge=dict(rgb=(100, 200, 255), r=(.4, 2.0), peak=6.0, light=6000),
                      round=dict(speed=700, range=500, length=30.0, core=.22, corona=1.3, fringe=(25, 100, 155),
                                 hot=(225, 225, 255), core_gain=20, corona_gain=2.5, jag=True, light=8000,
                                 light_rgb=(100, 200, 255)),
                      arcs=(140, 200, 255), puff=False, brass=False),
    # Blasters: a white-hot core in a coloured corona, its glow lighting the hull it passes.
    'miningblaster': dict(name='Mining Blaster', energy=True, burst=1, burst_delay=0, cooldown=.2,
                          barrels='alternating', spread=.002, flash=(1.0, .45, .15), flash_tint=(255, 100, 100),
                          flash_size=(1.25, 1.4, 1.4), flash_light=(255, 100, 100),
                          cloud=dict(rgb=(255, 100, 100), r=(.6, 2.0), frames=3, peak=2),
                          round=dict(speed=600, range=500, length=12.0, core=.35, corona=.95, fringe=(255, 100, 100),
                                     hot=(255, 255, 255), core_gain=22, corona_gain=3.0, light=10000,
                                     light_rgb=(255, 60, 60)),
                          puff=False, brass=False),
    'heavyblaster': dict(name='Heavy Blaster', energy=True, burst=1, burst_delay=0, cooldown=1.0,
                         barrels='alternating', spread=.002, flash=(1.0, .45, .15), flash_tint=(100, 100, 255),
                         flash_size=(1.25, 1.4, 1.4), flash_light=(100, 100, 255),
                         cloud=dict(rgb=(100, 100, 255), r=(.6, 2.0), frames=3, peak=2),
                         round=dict(speed=600, range=600, length=12.0, core=.4, corona=.95, fringe=(100, 100, 255),
                                    hot=(255, 255, 255), core_gain=22, corona_gain=3.0, light=12000,
                                    light_rgb=(100, 100, 255)),
                         puff=False, brass=False),
}
# The game's turn rates (weapon_data.csv "turn rate", degrees per second): how fast aim()
# swings each turret onto its target. A Hellbore takes six seconds over thirty degrees.
TURN_RATES = {'lightmg': 50, 'lightdualmg': 50, 'vulcan': 75, 'lightac': 20, 'heavyac': 7, 'mark9': 10,
              'hellbore': 5, 'lightag': 40, 'chaingun': 30, 'lightneedler': 20, 'ioncannon': 40,
              'miningblaster': 7, 'heavyblaster': 15}
for _k, _v in TURN_RATES.items():
    WEAPONS[_k].setdefault('turn', _v)

FEEDER_RGB = (255, 200, 0)          # Accelerated Ammo Feeder weapon glow


def armed(rig):
    """The WEAPONS key a rig fires as, or None (the kit's generic gun). Remembered on the flash
    box, so a later kit block over the same ship finds it."""
    w = rig.get('weapon')
    if w is None and rig.get('flash') is not None:
        w = rig['flash'].get('swfx_weapon')
        if w:
            rig['weapon'] = w
    return w if w in WEAPONS else None


def arm(ctx, turret, weapon, tag=None):
    """Make one mount fire as a game weapon (a WEAPONS key): its flash takes the weapon's colour
    and size and its flash light the weapon's colour and power. fire() and fire_weapon() then
    fire that weapon's rounds. Opt-in; returns one record per barrel armed."""
    w = WEAPONS[weapon]
    out = []
    for a in ctx.anchors.guns:
        if a.turret != turret or getattr(a, 'kind', 'gun') == 'launcher' or (tag and a.tag != tag):
            continue
        rig = _rig(ctx, a)
        label = gun_label(a)
        rig['weapon'] = weapon
        box = rig.get('flash') or bpy.data.objects.get(f'{PREFIX} flash {label}')
        if box is not None:
            # flash_gain (default 1) scales a weapon's flash gas, for a flash that wants it.
            mat = _flash_material(ctx, label, _lin(w['flash_tint']) if w.get('flash_tint') else None,
                                  w.get('flash_gain', 1.0))
            box.data.materials[0] = mat
            s = a.scale
            box.scale = tuple(s * k for k in w['flash_size'])
            box['swfx_weapon'] = weapon
            rig['flash'] = box
        light = rig.get('light') or bpy.data.objects.get(f'{PREFIX} flash light {label}')
        if light is not None:
            light.data.color = _lin(w['flash_light'])
            env = f'gun_flash_{label}'
            light.data.driver_remove('energy')
            ctx.drive(light.data, 'energy', f"{env}*gun_flash_gain*gun_flash_light*{w.get('flash_light_gain', 1.0):g}",
                      (env, 'gun_flash_gain', 'gun_flash_light'))
            rig['light'] = light
        out.append({'turret': a.turret, 'tag': a.tag, 'label': label, 'weapon': weapon})
    return out


def cadence(weapon, seconds, feeder=False):
    """[(time s, shot in burst, burst)] of one mount firing `weapon` for `seconds`, by the game's
    numbers: charge-up, then burst rounds burst_delay apart, then the cooldown. The Ammo Feeder
    doubles a ballistic weapon's rate."""
    w = WEAPONS[weapon]
    rate = 2.0 if feeder and not w['energy'] else 1.0
    out, t, k = [], w.get('chargeup', 0.0), 0
    cycle = w.get('chargeup', 0.0) + ((w['burst'] - 1) * w['burst_delay'] + w['cooldown']) / rate
    while t <= seconds + 1e-9:
        for i in range(w['burst']):
            ti = t + i * w['burst_delay'] / rate
            if ti > seconds + 1e-9:
                break
            out.append((ti, i, k))
        t += max(cycle, 1e-3)
        k += 1
    return out


DEGENERATE_REACHES = 20.0


def fire_weapon(ctx, turret, start_frame, seconds, feeder=False, target=None, tag=None, train=True, only=None,
                allow=None, pose=None, after=None, passes=None):
    """Fire an armed mount for `seconds` from `start_frame` at the game's cadence (cadence()):
    barrels alternate per round ('alternating'), per burst ('alternating_burst') or fire together
    ('linked'). target: a point in the ship's own (copy) space the rounds fly to and stop at, as
    on a hit; None flies them down the bore to their range. With a target and `train`, the
    turret is trained onto it (aim()) before each round, so the flash points along the rounds.
    Returns one record per round, with 'hit_frame' when a target was given.
    target may also be a function of the frame giving that point, for a target that moves against
    the ship over the burst: each round flies to, and the turret trains onto, where it is then.
    only: (first, last) frames: rounds fired outside them are counted but not built. A motion span
    renders each frame from its own rebuild, so a frame builds only the rounds it can see, on the
    same cadence and the same per-round seeds as the whole burst.
    allow(frame) -> bool: a round on that frame fires only if it says so (counted either way, so
    seeds and barrels stay in step). pose(frame): called before a round is built, to stand the
    turret where a planned aim has it then (anchor_matrix reads the current pose, not the keys).
    after(frame, objects): the unparented objects that round made (world_rounds' input).
    passes(frame) -> bool: that round misses: it flies through its target point and on to its
    range, fading, with no 'hit_frame' (a burst walked across a ship, most rounds going past)."""
    guns = [g for g in ctx.anchors.guns if g.turret == turret and getattr(g, 'kind', 'gun') != 'launcher'
            and (tag is None or g.tag == tag)]
    if not guns or not ctx.spec.get('guns', True):
        return []
    guns.sort(key=gun_index)
    weapon = armed(_rig(ctx, guns[0]))
    if weapon is None:
        raise ValueError(f'turret {turret} is not armed: call arm(ctx, {turret}, <weapon>) first')
    w = WEAPONS[weapon]
    fps = ctx.scene.render.fps or swfx_core.FPS
    target_at = target if callable(target) else (lambda f, t=target: t)
    if target is not None:
        target = target_at(start_frame)
        # A gun whose anchor is degenerate (its muzzle anchor missing) puts any target absurdly far
        # off in its own space; its rounds would fly the wrong way for minutes. It fires nothing, and
        # the caller draws the shot some other way.
        try:
            far = (anchor_matrix(guns[0].empty).inverted() @ Vector(target)).length
        except ValueError:
            far = float('inf')
        reach = swfx_core.units(w['round']['speed'] if w.get('round') else 1000.0) * MAX_FLIGHT_S
        # Out of reach is not degenerate: a target 3 km off a Hellbore is a range question, which
        # the caller's own gating answers (Asharu S6's Manticore refused every gun at 1.5-3x reach).
        # Degenerate is absurd: S7's anchor put its target 370 reaches away.
        if not math.isfinite(far) or far > reach * DEGENERATE_REACHES:
            chain, o = [], guns[0].empty
            while o is not None:
                b = o.matrix_basis
                chain.append(f'{o.name} t={tuple(round(c, 3) for c in b.translation)} '
                             f's={tuple(round(c, 4) for c in b.to_scale())}')
                o = o.parent
            lib = getattr(guns[0].empty, 'library', None)
            print(f'SWFX guns: degenerate anchor chain for turret {turret}: {" <- ".join(chain)}; '
                  f'anchor matrix det {anchor_matrix(guns[0].empty).determinant():.4g}; '
                  f'library {lib.filepath if lib else None}; file {bpy.data.filepath!r}; target {tuple(round(c, 2) for c in target)}',
                  flush=True)
            print(f'SWFX guns: turret {turret} cannot fire {weapon} at its target '
                  f'({far:.4g} u off in the space of its anchor, reach {reach:.4g} u): degenerate anchor', flush=True)
            return []
    out = []
    for n, (t, i, k) in enumerate(cadence(weapon, seconds, feeder)):
        exact = start_frame + t * fps
        f = int(round(exact))
        mode = w['barrels']
        pick = guns if mode == 'linked' else [guns[(k if mode == 'alternating_burst' else n) % len(guns)]]
        if (only is not None and not only[0] <= f <= only[1]) or (allow is not None and not allow(f)):
            for g in pick:
                _rig(ctx, g)['count'] += 1
            continue
        if pose is not None:
            pose(f)
        before = set(ctx.collection(GROUP).objects.keys()) if after is not None else None
        tg = target_at(f) if target is not None else None
        trained = None
        if tg is not None and train:
            trained = aim(ctx, turret, tg, f, tag=tag, turn_rate=w.get('turn'))
        for g in pick:
            if w.get('charge') and w.get('chargeup'):
                _charge(ctx, g, f, int(round(w['chargeup'] * fps)), w)
            out.append(_weapon_shot(ctx, g, f, lead=f - exact, target=tg,
                                    through=passes is not None and tg is not None and passes(f)))
            if trained is not None:
                out[-1]['aim'] = trained
        if after is not None:
            objs = ctx.collection(GROUP).objects
            after(f, [objs[n] for n in objs.keys() if n not in before and objs[n].parent is None])
    return out


def _key_flash(ctx, a, rig, label, frame, prof, gain, seed):
    env, sn = f'gun_flash_{label}', f'gun_seed_{label}'
    for j, v in enumerate(prof):
        _key_env(ctx, env, frame + j, v * gain)
    ctx.key(sn, frame, seed, 'CONSTANT')
    _key_zero(ctx, env, frame + len(prof))
    _key_zero(ctx, env, frame - 1)
    box = rig.get('flash') or bpy.data.objects.get(f'{PREFIX} flash {label}')
    if box is not None:
        lit = rig.setdefault('flash_frames', _shown_frames(box))
        lit.update(range(frame, frame + len(prof)))
        _rebuild_visibility(box, lit)


def _weapon_shot(ctx, a, frame, lead=0.0, target=None, through=False):
    """One round of an armed mount at an integer frame. lead: frames by which the true firing
    instant precedes `frame` (the round has already flown that far)."""
    coll = ctx.collection(GROUP)
    label = gun_label(a)
    rig = _rig(ctx, a)
    weapon = armed(rig)
    w = WEAPONS[weapon]
    n = rig['count']
    rig['count'] += 1
    rnd = random.Random(f'swfx-weapon|{weapon}|{a.turret}|{a.tag}|{frame}|{n}')
    _key_flash(ctx, a, rig, label, frame, w['flash'], rnd.uniform(.88, 1.12), rnd.random())
    rec = {'turret': a.turret, 'tag': a.tag, 'frame': frame, 'round': n, 'weapon': weapon,
           'flash_frames': len(w['flash'])}
    rec['cloud'] = _cloud(ctx, coll, a, label, frame, w['cloud'])
    rec.update(_round(ctx, coll, a, label, frame, rnd, weapon, lead, target, through))
    if w.get('heavy'):
        rec.update(_heavy(ctx, coll, a, rig, label, frame, rnd, w))
    if w.get('puff'):
        rec['puff'] = _puff(ctx, coll, a, label, frame, rnd)
    if w.get('brass') and _knob(ctx, 'gun_casings') >= .5:
        rec['casing'] = _casing(ctx, coll, a, label, frame, rnd)
    return rec


def _object_colour(nodes):
    """The object's own colour, keyed per object. Object Info's Color is the instancer's for an
    object drawn through a collection instance (every per-ship copy is), which read (1, 1, 1) and
    drove every glow at full strength into a white disc (passes 1-4); an Object attribute looks
    on the object itself first. Its output is named Color as Object Info's is."""
    at = nodes.new('ShaderNodeAttribute')
    at.attribute_type = 'OBJECT'
    at.attribute_name = 'color'
    return at


GLOW_NODE = 'swfx glow'
GLOW_PATH = f'nodes["{GLOW_NODE}"].outputs[0].default_value'


def _glow_material(ctx, rgb, name, jag=False):
    """Soft emissive gas: the weapon's colour, whiter where densest. One per glow object: its
    own RGB node GLOW_NODE (keyed through GLOW_PATH, _glow_keys) holds R the fade, G a seed, B a
    twentieth of the emission per metre (peak radiance / diameter in metres), so a glow reads
    the same however large it is. Object coordinates span -1..1 in the unit box.

    Not the object's colour: an object drawn through a collection instance (every per-ship
    copy is) never hands its keyed colour to Cycles, Object Info or Object attribute alike, and
    every glow rendered at full strength as a white disc (passes 1-5; the diagnostic run showed
    the discs were these glows).

    jag: torn rather than round (owner, 2026-10-21: no orb explosions). The coordinates are
    pushed about by a seeded noise field before the falloff, so the gas breaks into lobes and
    tongues with ragged gaps, and the densest heart is less a ball."""
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    p = tc.outputs['Object']
    ctl = nodes.new('ShaderNodeRGB')
    ctl.name = ctl.label = GLOW_NODE
    ctl.outputs[0].default_value = (0, 0, 0, 1)
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(ctl.outputs[0], sep.inputs[0])
    fade, seed, k = sep.outputs[0], sep.outputs[1], sep.outputs[2]
    if jag:
        tn = nodes.new('ShaderNodeTexNoise')
        tn.noise_dimensions = '4D'
        tn.inputs['Scale'].default_value = 1.1
        tn.inputs['Detail'].default_value = 2.0
        tn.inputs['Roughness'].default_value = .55
        links.new(p, tn.inputs['Vector'])
        links.new(g.mul(seed, 53.0), tn.inputs['W'])
        off = nodes.new('ShaderNodeVectorMath')
        off.operation = 'MULTIPLY_ADD'
        links.new(tn.outputs['Color'], off.inputs[0])
        off.inputs[1].default_value = (1.3, 1.3, 1.3)
        off.inputs[2].default_value = (-.65, -.65, -.65)
        moved = nodes.new('ShaderNodeVectorMath')
        moved.operation = 'ADD'
        links.new(p, moved.inputs[0])
        links.new(off.outputs[0], moved.inputs[1])
        p = moved.outputs[0]
    x, y, z = g.xyz(p)
    r2 = g.add(g.add(g.sq(x), g.sq(y)), g.sq(z))
    shell = g.pow(g.mx(g.sub(1.0, r2), 0.0), 2.0)
    n1 = g.noise(p, g.mul(seed, 37.0), 1.8, 3.0, .6)
    lumps = g.add(.55, g.mul(g.clamp01(g.mul(g.sub(n1, .3), 2.0)), .9))
    if jag:
        lumps = g.mul(g.clamp01(g.mul(g.sub(n1, .38), 3.5)), 1.5)        # ragged gaps, not a soft ball
    dens = g.mul(g.mul(shell, lumps), fade)
    mix = nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    # Only the densest heart goes toward white, and only part way: AgX already washes strong
    # emitters, and a glow that whitened everywhere above .45 read as a white ball (pass 1).
    links.new(g.mul(g.clamp01(g.mul(g.sub(dens, 1.15), 1.5)), .45), mix.inputs['Factor'])
    mix.inputs['A'].default_value = (*rgb, 1)
    mix.inputs['B'].default_value = (1, 1, 1, 1)
    em = nodes.new('ShaderNodeEmission')
    links.new(mix.outputs['Result'], em.inputs['Color'])
    links.new(g.mul(dens, g.mul(k, 20.0)), em.inputs['Strength'])
    links.new(em.outputs[0], out.inputs['Volume'])
    return m


def _glow_object(ctx, coll, name, rgb, jag=False):
    ob = bpy.data.objects.new(name, _unit_box_mesh(f'{name} mesh', _glow_material(ctx, rgb, f'{name} gas', jag)))
    coll.objects.link(ob)
    ob.visible_shadow = False
    return ob


def _glow_keys(ob):
    """The node tree a glow object's fade / seed / strength are keyed on (GLOW_PATH)."""
    return ob.data.materials[0].node_tree


def _cloud(ctx, coll, a, label, frame, spec):
    """The flash's glowing gas, lingering a little for spectacle (owner, 2026-10-20) and thinning
    out: it grows from r[0] to r[1] metres over `frames` and fades. Torn and thrown down the
    bore, not a ball (owner, 2026-10-21): stretched along +X, turned a seeded amount about it."""
    rgb = _lin(spec['rgb'])
    ob = _glow_object(ctx, coll, f'{PREFIX} cloud {label} f{frame:04d}', rgb, jag=True)
    jr = random.Random(f'cloud-shape|{label}|{frame}')
    ob.rotation_euler = (jr.uniform(0, 2 * math.pi), 0, 0)
    stretch = (1.45, jr.uniform(.7, .9), jr.uniform(.6, .8))
    swfx_core.attach(ob, a.empty)
    r0, r1 = spec['r']
    life = max(1, int(spec['frames']))
    fr, sc, lx, fade, kk = [], [], [], [], []
    for j in range(life + 1):
        t = j / life
        e = 1 - (1 - t) ** 2.5
        r = r0 + (r1 - r0) * e
        fr.append(frame + j)
        sc.append(swfx_core.units(r))
        lx.append(swfx_core.units(r * .55 + .3 * r1 * e))
        fade.append((1 - t) ** 1.6)
        kk.append(_hull_glow(spec['peak'] / (2 * r)) / 20.0)
    for i in range(3):
        _bake(ob, 'scale', i, fr, [v * stretch[i] for v in sc])
    _bake(ob, 'location', 0, fr, [v * 1.3 for v in lx])
    t = _glow_keys(ob)
    _bake(t, GLOW_PATH, 0, fr, fade)
    _bake(t, GLOW_PATH, 1, [frame], [random.Random(f'cloud|{label}|{frame}').random()], 'CONSTANT')
    _bake(t, GLOW_PATH, 2, fr, kk)
    _visible_between(ob, frame, frame + life)
    return ob.name


def _charge(ctx, a, frame, frames, w):
    """An energy weapon's charge-up: a glow gathering at the muzzle for `frames` before it fires,
    with a light rising under it."""
    spec = w['charge']
    coll = ctx.collection(GROUP)
    label = gun_label(a)
    rgb = _lin(spec['rgb'])
    ob = _glow_object(ctx, coll, f'{PREFIX} charge {label} f{frame:04d}', rgb, jag=True)
    swfx_core.attach(ob, a.empty, local=(swfx_core.units(.2), 0, 0))
    stretch = (1.8, .8, .7)          # gathering down the bore, torn (owner: no orbs)
    r0, r1 = spec['r']
    f0 = frame - max(1, frames)
    rnd = random.Random(f'charge|{label}|{frame}')
    fr, sc, fade, kk = [], [], [], []
    for j in range(frame - f0):
        t = (j + 1) / (frame - f0)
        r = r0 + (r1 - r0) * t ** 1.5
        fr.append(f0 + j)
        sc.append(swfx_core.units(r))
        fade.append((.25 + .75 * t * t) * rnd.uniform(.8, 1.0))
        kk.append(_hull_glow(spec['peak'] / (2 * r)) / 20.0)
    for i in range(3):
        _bake(ob, 'scale', i, fr, [v * stretch[i] for v in sc])
    t = _glow_keys(ob)
    _bake(t, GLOW_PATH, 0, fr, fade)
    _bake(t, GLOW_PATH, 1, [f0], [rnd.random()], 'CONSTANT')
    _bake(t, GLOW_PATH, 2, fr, kk)
    _visible_between(ob, f0, frame - 1)
    light = swfx_core.point_light(f'{PREFIX} charge light {label} f{frame:04d}', coll, rgb, 0.0,
                                  radius=swfx_core.units(r1 * .5))
    light.visible_camera = False      # a light with a radius is a sphere the camera sees
    swfx_core.attach(light, a.empty, local=(swfx_core.units(.2), 0, 0))
    _bake(light.data, 'energy', -1, [f0 - 1] + fr + [frame], [0.0] + [_hull_watts(spec['light']) * v for v in fade] + [0.0])
    return ob.name


def _round_material(ctx, weapon, part):
    """A round's additive rod: 'core' the hot body (the weapon's fringe colour along the tail,
    its hot core colour at the head), 'corona' the soft coloured sheath around it. Object
    colour R is the burn (keyed)."""
    name = f'{PREFIX} round {part} {weapon}'
    m = bpy.data.materials.get(name)
    if m:
        return m
    r = WEAPONS[weapon]['round']
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    gx, _, _ = g.xyz(tc.outputs['Generated'])
    info = _object_colour(nodes)
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    burn = sep.outputs[0]
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    gain = swfx_core.value_node(ctx, nodes, 'tracer gain', 'gun_tracer_gain', ('gun_tracer_gain',))
    em = nodes.new('ShaderNodeEmission')
    fringe, hot = _lin(r['fringe']), _lin(r['hot'])
    if part == 'core':
        facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 2.0)
        along = g.add(.25, g.mul(g.pow(gx, 1.6), .75))         # tail (x=0) -> head (x=1)
        head = g.exp_neg(g.mul(g.sq(g.sub(gx, .92)), 90.0))
        strength = g.mul(g.mul(g.add(along, head), g.mul(facing, burn)), g.mul(gain, float(r['core_gain'])))
        mix = nodes.new('ShaderNodeMix')
        mix.data_type = 'RGBA'
        links.new(g.clamp01(g.add(g.mul(head, 1.2), g.mul(gx, .4))), mix.inputs['Factor'])
        mix.inputs['A'].default_value = (*fringe, 1)
        mix.inputs['B'].default_value = (*hot, 1)
        links.new(mix.outputs['Result'], em.inputs['Color'])
    else:
        facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 1.4)
        ends = g.mul(g.smooth(0.0, .35, gx), g.sub(1.0, g.smooth(.8, 1.0, gx)))
        strength = g.mul(g.mul(g.mul(facing, ends), burn), g.mul(gain, float(r['corona_gain'])))
        em.inputs['Color'].default_value = (*_lin(r.get('glow', r['fringe'])), 1)
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


def _jag_mesh(name, lines, radius, sides=4, mat=None):
    """A thin tube along each polyline in `lines` (one mesh): lightning, bolts, arcs."""
    verts, faces = [], []
    for pts in lines:
        base = len(verts)
        for i, p in enumerate(pts):
            t = (pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]).normalized()
            u = t.orthogonal().normalized()
            v = t.cross(u)
            for k in range(sides):
                ang = k * math.tau / sides
                verts.append(tuple(p + (u * math.cos(ang) + v * math.sin(ang)) * radius))
        for i in range(len(pts) - 1):
            for k in range(sides):
                a0 = base + i * sides + k
                a1 = base + i * sides + (k + 1) % sides
                faces.append((a0, a1, a1 + sides, a0 + sides))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    if mat:
        me.materials.append(mat)
    return me


BOLT_POOL = 12      # jagged bolt strikes per gun
MAX_FLIGHT_S = 6.0   # no round is drawn flying longer than this


def _bolt_pool(label, weapon, length, amp, radius, mat):
    """BOLT_POOL jagged bolt meshes for one gun, made once and reused by every round it fires (a
    bolt is drawn along its own length, so any strike fits any frame of any round)."""
    out = []
    for i in range(BOLT_POOL):
        name = f'{PREFIX} bolt mesh {label} {weapon} {length:.4f} {i}'
        me = bpy.data.meshes.get(name)
        if me is None:
            rnd = random.Random(f'bolt-pool|{label}|{weapon}|{i}')
            me = _jag_mesh(name, _jagged(rnd, Vector((-length, 0, 0)), Vector(), 12, amp, forks=2), radius, 5, mat)
        out.append(me)
    return out


def _jagged(rnd, p0, p1, n, amp, forks=0, fork_len=.25):
    """A crackling polyline from p0 to p1 in n segments, jittered sideways by about `amp`; plus
    `forks` short side branches. Returns the list of polylines."""
    d = p1 - p0
    t = d.normalized()
    u = t.orthogonal().normalized()
    v = t.cross(u)
    pts = []
    for i in range(n + 1):
        f = i / n
        off = (u * rnd.gauss(0, amp) + v * rnd.gauss(0, amp)) * math.sin(math.pi * f) ** .5 if 0 < i < n else Vector()
        pts.append(p0 + d * f + off)
    lines = [pts]
    for _ in range(forks):
        i = rnd.randrange(1, n)
        q = pts[i]
        out_dir = (t * rnd.uniform(.2, .8) + u * rnd.uniform(-1, 1) + v * rnd.uniform(-1, 1)).normalized()
        end = q + out_dir * d.length * fork_len * rnd.uniform(.5, 1.0)
        lines.append(_jagged(rnd, q, end, 3, amp * .6)[0])
    return lines


def anchor_matrix(ob):
    """An object's matrix in its file's own space from its parent chain (basis and parent
    inverse). Unlike matrix_world it is right for a copy that is never evaluated, such as a
    per-ship copy in an excluded collection drawn through a collection instance."""
    m = ob.matrix_basis.copy()
    while ob.parent is not None:
        m = ob.parent.matrix_basis @ ob.matrix_parent_inverse @ m
        ob = ob.parent
    return m


def _kit_name(name):
    """'Turret 1 pivot.001 @lasher-a' -> 'Turret 1 pivot' (a per-ship copy's own name)."""
    name = re.sub(r' ~?@[^@]*$', '', name)
    return re.sub(r'\.\d{3}$', '', name)


def trainers(empty):
    """(pivot, elevation) objects a gun anchor rides, found up its parent chain by name
    ('<mount> pivot', '<mount> elevation', as the turret kit names them). (None, None) for a
    fixed hardpoint; elevation None for a mount that only traverses."""
    piv = elev = None
    o = empty.parent
    while o is not None and piv is None:
        n = _kit_name(o.name)
        if elev is None and n.endswith(' elevation'):
            elev = o
        elif n.endswith(' pivot'):
            piv = o
        o = o.parent
    return piv, elev


def _signed_angle(a, b, axis):
    a = (a - axis * a.dot(axis))
    b = (b - axis * b.dot(axis))
    if a.length < 1e-9 or b.length < 1e-9:
        return 0.0
    return math.atan2(axis.dot(a.cross(b)), a.dot(b))


def _key_constant(ob, path, index, frame):
    ob.keyframe_insert(path, index=index, frame=frame)
    fc = swfx_core.find_fcurve(ob, path, index)
    for kp in fc.keyframe_points if fc else ():
        if abs(kp.co.x - frame) < 1e-4:
            kp.interpolation = 'CONSTANT'


def _pose_at(ob, index, frame):
    """A rotation channel's value at `frame` (its keys, else its current value), and the last
    key before `frame` (None if it has none)."""
    fc = swfx_core.find_fcurve(ob, 'rotation_euler', index)
    if fc is None or not len(fc.keyframe_points):
        return ob.rotation_euler[index], None
    before = [kp.co.x for kp in fc.keyframe_points if kp.co.x < frame - 1e-4]
    return fc.evaluate(frame), (max(before) if before else None)


def _key_slew(ob, index, f0, v0, f1, v1):
    """Hold v0 to f0, swing eased to v1 at f1, hold there (CONSTANT) until the next key."""
    ob.rotation_euler[index] = v0
    ob.keyframe_insert('rotation_euler', index=index, frame=f0)
    ob.rotation_euler[index] = v1
    ob.keyframe_insert('rotation_euler', index=index, frame=f1)
    fc = swfx_core.find_fcurve(ob, 'rotation_euler', index)
    for kp in fc.keyframe_points:
        if abs(kp.co.x - f0) < 1e-4:
            kp.interpolation = 'BEZIER'
            kp.easing = 'AUTO'
            kp.handle_left_type = kp.handle_right_type = 'AUTO_CLAMPED'
        elif abs(kp.co.x - f1) < 1e-4:
            kp.interpolation = 'CONSTANT'
            kp.handle_left_type = kp.handle_right_type = 'AUTO_CLAMPED'
    fc.update()


def aim(ctx, turret, target, frame, tag=None, slew_frames=None, turn_rate=None, iterations=4):
    """Train a turret onto `target` (a point in the ship's own copy space, units) by `frame`:
    its pivot traverses about its own Z and its elevation lifts about its own Y until the
    muzzle's line of fire passes through the target, so the flash, which rides the muzzle,
    points where the rounds go.

    The turret swings there from the pose it has at that frame (its earlier keys), eased, over
    `slew_frames`, or over the time `turn_rate` (degrees per second; the game's "turn rate":
    a Hellbore 5, a light MG 50) takes for the larger of the two angles. The swing starts no
    earlier than the turret's previous key, so a turret given too little time turns faster
    than its rate rather than late. Neither: it snaps, keyed CONSTANT. Elevation is not clamped.

    The pivot is a hull object. A ship placed without its own hull copy (ship_fx.ShipFx
    own_hull) shares its turrets with every other copy of that build, and training one trains
    them all. Returns (traverse, elevation) in degrees, or None for a mount with no pivot."""
    guns = sorted((g for g in ctx.anchors.guns if g.turret == turret and (tag is None or g.tag == tag)),
                  key=gun_index)
    if not guns:
        return None
    a = guns[0]
    piv, elev = trainers(a.empty)
    if piv is None:
        return None
    T = Vector(target)
    chans = [(piv, 2)] + ([(elev, 1)] if elev is not None else [])
    start, prev = {}, []
    for ob, i in chans:
        v, k = _pose_at(ob, i, frame)
        ob.rotation_euler[i] = v
        start[ob.name] = v
        if k is not None:
            prev.append(k)
    X, Y, Z = Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1))
    for _ in range(iterations):
        m = anchor_matrix(a.empty)
        piv.rotation_euler.z += _signed_angle(m.to_3x3() @ X, T - m.translation,
                                              (anchor_matrix(piv).to_3x3() @ Z).normalized())
        if elev is not None:
            m = anchor_matrix(a.empty)
            elev.rotation_euler.y += _signed_angle(m.to_3x3() @ X, T - m.translation,
                                                   (anchor_matrix(elev).to_3x3() @ Y).normalized())
    swing = max(abs(math.degrees(ob.rotation_euler[i] - start[ob.name])) for ob, i in chans)
    dur = 0.0
    if slew_frames:
        dur = float(slew_frames)
    elif turn_rate:
        dur = swing / turn_rate * (ctx.scene.render.fps or swfx_core.FPS)
    f0 = frame - dur
    if prev:
        f0 = max(f0, max(prev))
        if swing > .05 and f0 > frame - 1.5:
            # Owner, 2026-10-23: turrets "popping" instead of interpolating. A turret tracking a
            # moving target re-aims every round, a few frames apart: too small a swing for its turn
            # rate to stretch over a frame used to be keyed as a step. It swings on from its last key.
            f0, dur = max(prev), max(dur, 1.0)
    if dur > 0 and swing > .05 and f0 < frame - .5:
        for ob, i in chans:
            _key_slew(ob, i, round(f0), start[ob.name], frame, ob.rotation_euler[i])
    else:
        for ob, i in chans:
            _key_constant(ob, 'rotation_euler', i, frame)
    return (round(math.degrees(piv.rotation_euler.z), 2),
            round(math.degrees(elev.rotation_euler.y), 2) if elev is not None else None)


def aim_solve(ctx, turret, target, tag=None, iterations=4):
    """aim()'s solve without keying: the mount's pivot traversed and its elevation lifted until
    the muzzle line passes through `target` (copy space, units), left standing there. Returns
    (pivot, elevation, traverse rad, elevation rad); elevation None for a traverse-only mount;
    None for a fixed hardpoint."""
    guns = sorted((g for g in ctx.anchors.guns if g.turret == turret and (tag is None or g.tag == tag)),
                  key=gun_index)
    if not guns:
        return None
    a = guns[0]
    piv, elev = trainers(a.empty)
    if piv is None:
        return None
    T = Vector(target)
    X, Y, Z = Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1))
    for _ in range(iterations):
        m = anchor_matrix(a.empty)
        piv.rotation_euler.z += _signed_angle(m.to_3x3() @ X, T - m.translation,
                                              (anchor_matrix(piv).to_3x3() @ Z).normalized())
        if elev is not None:
            m = anchor_matrix(a.empty)
            elev.rotation_euler.y += _signed_angle(m.to_3x3() @ X, T - m.translation,
                                                   (anchor_matrix(elev).to_3x3() @ Y).normalized())
    return piv, elev, piv.rotation_euler.z, (elev.rotation_euler.y if elev is not None else None)


def world_rounds(objs, delta_at):
    """Make a round fly straight in the world while its ship moves on (owner, 2026-10-23: shells
    "track while in flight onto moving targets"). The round was built in its ship's copy space as
    the ship stood when it fired; delta_at(frame) is the matrix from that space to the copy space
    on `frame`. A keyed mover gets location and rotation keys through it on each of its keyed
    frames; a static object (one bolt strike, shown on one frame) is moved by its first shown
    frame's delta. Parented objects (flash, cloud, puff, brass) ride the turret and are not passed.
    Each object passed is tagged 'world_space' so a later sweep (Asharu's SceneFx.to_world) skips it."""
    for ob in objs:
        ob['world_space'] = True
        fcs = [swfx_core.find_fcurve(ob, 'location', i) for i in range(3)]
        if all(fc is not None and len(fc.keyframe_points) for fc in fcs):
            frames = [kp.co.x for kp in fcs[0].keyframe_points]
            r0 = ob.rotation_euler.to_matrix()
            locs, rots, prev = [], [], ob.rotation_euler.copy()
            for f in frames:
                d = delta_at(int(round(f)))
                locs.append(d @ Vector([fc.evaluate(f) for fc in fcs]))
                e = (d.to_3x3().normalized() @ r0).to_euler('XYZ', prev)
                rots.append(e)
                prev = e
            for i in range(3):
                _bake(ob, 'location', i, frames, [v[i] for v in locs])
            if ob.type != 'LIGHT':
                for i in range(3):
                    _bake(ob, 'rotation_euler', i, frames, [e[i] for e in rots])
            continue
        vis = swfx_core.find_fcurve(ob, 'hide_render')
        shown = [kp.co.x for kp in vis.keyframe_points if kp.co.y < .5] if vis is not None else []
        if shown:
            ob.matrix_basis = delta_at(int(round(min(shown)))) @ ob.matrix_basis


def _braid_dir(a, converge_m):
    """Bore direction (anchor space) that crosses the mount's other barrels' streams converge_m
    downrange: alternate rounds zig-zag across one braid."""
    mine = [g['anchor'] for g in _RIGS.values()
            if g['anchor'].turret == a.turret and getattr(g['anchor'], 'kind', 'gun') != 'launcher']
    if len(mine) < 2:
        return Vector((1, 0, 0))
    inv = anchor_matrix(a.empty).inverted()
    centre = sum((inv @ anchor_matrix(g.empty).translation for g in mine), Vector()) / len(mine)
    return Vector((swfx_core.units(converge_m), centre.y, centre.z)).normalized()


def _round(ctx, coll, a, label, frame, rnd, weapon, lead, target, through=False):
    """The round itself: core and corona rods (or a jagged bolt re-struck every frame), an
    optional glow halo and travelling light, flying from the muzzle to range or target."""
    w = WEAPONS[weapon]
    r = w['round']
    fps = ctx.scene.render.fps or swfx_core.FPS
    v = swfx_core.units(r['speed']) / fps
    stop = swfx_core.units(r['range'])
    d = Vector((1, 0, 0))
    if target is not None:
        loc = anchor_matrix(a.empty).inverted() @ Vector(target)
        stop, d = loc.length, loc.normalized()
        # A degenerate anchor (a gun whose muzzle anchor is missing) can invert to a target thousands
        # of kilometres off: its rounds then fly for millions of frames and the build never ends
        # (Asharu S7's Wolf (P) ion cannon). No round flies further than MAX_FLIGHT_S at its speed.
        most = swfx_core.units(r['speed']) * MAX_FLIGHT_S
        if not math.isfinite(stop) or stop > most:
            print(f'SWFX round {label}: target {stop:.4g} u off, flight capped at {MAX_FLIGHT_S:g} s', flush=True)
            stop = most
            if not all(math.isfinite(c) for c in d) or d.length < .5:
                d = Vector((1, 0, 0))
        if through:          # a miss: on past the target point to the round's range
            stop = min(max(stop * 1.15, swfx_core.units(r['range'])), most)
    elif w.get('braid'):
        d = _braid_dir(a, w['braid'])
    yaw, pitch = rnd.gauss(0, w['spread']), rnd.gauss(0, w['spread'])
    d = (Matrix.Rotation(yaw, 3, 'Z') @ Matrix.Rotation(-pitch, 3, 'Y') @ d).normalized()
    # Rounds live in the ship's own space, not on the anchor: once fired they no longer
    # follow the turret, so a turret that trains on (aim()) never swings them.
    M = anchor_matrix(a.empty)
    rot = (M.to_3x3().normalized() @ d.to_track_quat('X', 'Z').to_matrix()).to_euler()
    L = swfx_core.units(r['length'])
    heads = []
    j = 0
    while True:
        h = max(v * .3, v * (j + lead))
        heads.append(min(h, stop))
        if h >= stop:
            break
        j += 1
    life = len(heads) - 1
    frames = [frame + j for j in range(life + 1)]
    hits = target is not None and not through
    burn = [1.0 if hits else max(0.0, min(1.0, (1 - hh / stop) / .3)) for hh in heads]
    rec = {'flight_frames': life}
    if hits:
        rec['hit_frame'] = frame + life

    def mover(ob):
        coll.objects.link(ob)
        ob.rotation_euler = rot
        ob.visible_shadow = False
        for i in range(3):
            _bake(ob, 'location', i, frames, [(M @ (d * hh))[i] for hh in heads])
        _bake(ob, 'scale', 0, frames, [max(.05, min(1.0, hh / L)) for hh in heads])
        _bake(ob, 'color', 0, frames, burn)
        ob.color = (1, 0, 0, 1)
        _visible_between(ob, frame, frame + life)
        return ob

    cor = mover(bpy.data.objects.new(f'{PREFIX} round {label} f{frame:04d}', _cylinder_mesh(
        f'{PREFIX} round corona mesh {weapon}', swfx_core.units(r['corona']), -L, 0.0, 12,
        _round_material(ctx, weapon, 'corona'))))
    rec['round'] = cor.name
    if r.get('jag'):
        # The bolt crackles: a fresh jagged core every frame of its flight.
        # The strikes come from a small pool per gun (_bolt_pool): a long burst re-strikes hundreds of
        # frames, and a mesh each cost a whole-shot build (Asharu S7's ion cannon) most of an hour.
        mat = _round_material(ctx, weapon, 'core')
        pool = _bolt_pool(label, weapon, L, swfx_core.units(r['corona']) * .35, swfx_core.units(r['core']), mat)
        for j, f in enumerate(frames):
            pick = random.Random(f'bolt|{label}|{frame}|{j}').randrange(len(pool))
            ob = bpy.data.objects.new(f'{PREFIX} bolt {label} f{frame:04d} {j}', pool[pick])
            coll.objects.link(ob)
            ob.location, ob.rotation_euler = M @ (d * heads[j]), rot
            ob.scale.x = max(.05, min(1.0, heads[j] / L))
            ob.visible_shadow = False
            ob.color = (burn[j], 0, 0, 1)
            _visible_between(ob, f, f)
    else:
        mover(bpy.data.objects.new(f'{PREFIX} round core {label} f{frame:04d}', _cylinder_mesh(
            f'{PREFIX} round core mesh {weapon}', swfx_core.units(r['core']), -L, 0.0, 10,
            _round_material(ctx, weapon, 'core'))))
    if r.get('trail'):
        rec['trail'] = _slug_trail(ctx, coll, label, frame, weapon, frames, heads, burn, M, d, rot, L)
    if r.get('halo'):
        h = r['halo']
        # A wake, not a ball (owner: no orbs): stretched back along the flight, torn, sitting
        # behind the slug's head.
        st = h.get('stretch', 1.0)
        ob = _glow_object(ctx, coll, f'{PREFIX} round halo {label} f{frame:04d}', _lin(r['fringe']), jag=st > 1.0)
        back = L * .35 + swfx_core.units(h['r']) * (st - 1.0) * .7
        for i in range(3):
            _bake(ob, 'location', i, frames, [(M @ (d * max(0.0, hh - back)))[i] for hh in heads])
        ob.rotation_euler = rot
        ob.scale = (swfx_core.units(h['r']) * st, swfx_core.units(h['r']), swfx_core.units(h['r']))
        t = _glow_keys(ob)
        _bake(t, GLOW_PATH, 0, frames, burn)
        _bake(t, GLOW_PATH, 1, [frame], [rnd.random()], 'CONSTANT')
        _bake(t, GLOW_PATH, 2, [frame], [_hull_glow(h['peak'] / (2 * h['r'])) / 20.0], 'CONSTANT')
        _visible_between(ob, frame, frame + life)
    if r.get('light'):
        light = swfx_core.point_light(f'{PREFIX} round light {label} f{frame:04d}', coll,
                                      _lin(r.get('light_rgb', r['fringe'])), 0.0,
                                      radius=swfx_core.units(r['corona']))
        light.visible_camera = False
        for i in range(3):
            _bake(light, 'location', i, frames, [(M @ (d * (hh - L * .3)))[i] for hh in heads])
        _bake(light.data, 'energy', -1, [frame - 1] + frames + [frame + life + 1],
              [0.0] + [_hull_watts(r['light']) * b for b in burn] + [0.0])
    return rec


def crawl_path(cast, p, n, rnd, steps=7, step_m=3.0, lift_m=.25):
    """A path crawling over a hull from p (metres) whose surface normal is n: each step turns a
    little and is pulled back onto the hull by cast(origin, direction) -> (hit, point, normal),
    in the same space. Returns the points, lifted lift_m off the surface. For EMP arcs here and
    for anything else that crawls over a hull (shield overload arcs)."""
    n = Vector(n).normalized()
    cur = Vector(p)
    t = (Matrix.Rotation(rnd.uniform(0, math.tau), 3, n) @ n.orthogonal()).normalized()
    pts = [cur + n * lift_m]
    for _ in range(steps):
        t = Matrix.Rotation(rnd.gauss(0, .6), 3, n) @ t
        t = (t - n * t.dot(n)).normalized()
        probe = cur + t * step_m * rnd.uniform(.6, 1.3) + n * step_m
        hit, q, qn = cast(probe, -n)
        if not hit or (Vector(q) - cur).length > 3 * step_m:
            break
        cur, n = Vector(q), Vector(qn).normalized()
        pts.append(cur + n * lift_m)
    return pts


def _fractal(points, rnd, rough=.16, decay=1.0, levels=5, cap_m=1.2):
    """Lightning: every segment split at its midpoint, the midpoint kicked sideways by `rough`
    x the segment's length, and again on the halves at `decay` of that, `levels` deep, so the
    line zig-zags at every scale (a crawl step of 3 m ends in pieces of about 10 cm) instead of
    running straight between a few kinks (owner, 2026-10-21: arcs read linear up close).
    Self-similar (decay 1) turns about 27 degrees a vertex, median; the old four-cut jag ran
    1.2 m straight between kicks. Segments longer than cap_m kick as if cap_m long, so the
    arc keeps to its crawl path over the hull and the jaggedness is at the small scale."""
    pts = [Vector(p) for p in points]
    amp = rough
    for _ in range(levels):
        out = [pts[0]]
        for a, b in zip(pts, pts[1:]):
            seg = b - a
            ln = seg.length
            if ln > 1e-9:
                t = seg / ln
                u = t.orthogonal().normalized()
                v = t.cross(u)
                ang = rnd.uniform(0, math.tau)
                kick = (u * math.cos(ang) + v * math.sin(ang)) * rnd.gauss(0, 1) * amp * min(ln, swfx_core.units(cap_m))
                out.append((a + b) * .5 + kick)
            out.append(b)
        pts = out
        amp *= decay
    return pts


def _forks(main, rnd, n, length=(.06, .18)):
    """n short side branches off a fractal arc, each its own fractal, as fractions of the arc's
    length: the hair that makes lightning read as lightning."""
    total = sum((b - a).length for a, b in zip(main, main[1:]))
    out = []
    for _ in range(n):
        i = rnd.randrange(1, len(main) - 1)
        d = (main[i + 1] - main[i - 1]).normalized()
        side = (d.orthogonal().normalized() * rnd.choice((-1, 1)) + d * rnd.uniform(.3, .9)).normalized()
        side = (Matrix.Rotation(rnd.uniform(-1, 1), 3, d) @ side).normalized()
        end = main[i] + side * total * rnd.uniform(*length)
        out.append(_fractal([main[i], end], rnd, rough=.22, levels=4))
    return out


def emp_arcs(ctx, frame, paths, frames=8, rgb=(140, 200, 255), seed=0, light=3000.0):
    """The ion cannon's EMP on a hit: lightning crawling over the hull along each path (points
    in the ship's own copy space, units; crawl_path() makes them) for `frames`, flickering, with
    a flickering light at the strike. The arcs are W1's material and tube (swfx_shield.arc_material
    / arc_curve, shared with the shield overload) on the knob gun_emp, keyed per strike, drawn
    along a fractal of each path (_fractal) with a few hair-thin forks (_forks); two fractals per
    path alternate frame by frame so the bolt re-strikes. Returns the object names."""
    import swfx_shield
    coll = ctx.collection(GROUP)
    rnd = random.Random(f'swfx-emp|{frame}|{seed}')
    lin = _lin(rgb)
    ctx.prop('gun_emp', 0.0, 0.0, 1.0, 'ion cannon EMP arcs envelope (keyed by emp_arcs())')
    _key_zero(ctx, 'gun_emp', frame - 1)
    for j in range(frames):
        _key_env(ctx, 'gun_emp', frame + j, 1.0 - .6 * j / max(1, frames))
    _key_zero(ctx, 'gun_emp', frame + frames)
    mat = bpy.data.materials.get(f'{PREFIX} emp arc') or swfx_shield.arc_material(
        ctx, f'{PREFIX} emp arc', lin, 'gun_emp', ('gun_emp',))
    names = []
    for k, path in enumerate(paths):
        path = [tuple(p) for p in path]
        if len(path) < 2:
            continue
        for alt in (0, 1):
            main = _fractal(path, rnd)
            ob = swfx_shield.arc_curve(f'{PREFIX} emp arc f{frame:04d} {k} {alt}', coll,
                                       [tuple(p) for p in main], swfx_core.units(.06), mat)
            for fork in _forks(main, rnd, rnd.randint(2, 4)):
                sp = ob.data.splines.new('POLY')
                sp.points.add(len(fork) - 1)
                for j, (q, c) in enumerate(zip(sp.points, fork)):
                    q.co = (*c, 1.0)
                    q.radius = .6 - .4 * j / max(1, len(fork) - 1)      # hair-thin, tapering
            shown = set(range(frame + alt, frame + frames, 2))
            if shown:
                _rebuild_visibility(ob, shown)
                names.append(ob.name)
            else:
                ob.hide_render = True
    if paths:
        lt = swfx_core.point_light(f'{PREFIX} emp light f{frame:04d}', coll, lin, 0.0, radius=swfx_core.units(.5))
        lt.visible_camera = False
        lt.location = Vector(paths[0][0])
        _bake(lt.data, 'energy', -1, [frame - 1] + [frame + j for j in range(frames)] + [frame + frames],
              [0.0] + [_hull_watts(light) * rnd.uniform(.3, 1.0) for _ in range(frames)] + [0.0])
        names.append(lt.name)
    return names


# --- heavy guns ----------------------------------------------------------------
# A cannon's or a Hellbore's shot is an event, not a stream (owner, 2026-10-23: "heavier pulsed
# firing and their in flight projectile needs to convey that"): a hard short blast, a pressure
# ring thrown off the muzzle, the barrel kicking back into its sleeve, smoke that glows as it
# leaves and then hangs, and a short hot slug trailing embers (_round, 'trail'). A WEAPONS entry
# opts in with 'heavy'; the machine guns and the Vulcan's streak have none.

def _recoil_empties(a):
    """[(k, empty)] of the '<mount> recoil <k>' empties under the gun's elevation (turret_kit hangs
    each barrel on one, sliding along -X to recoil), by k; [] for a mount built without them."""
    _, elev = trainers(a.empty)
    if elev is None:
        return []
    head = _kit_name(elev.name)
    if not head.endswith(' elevation'):
        return []
    pat = re.compile(re.escape(head[:-len(' elevation')]) + r' recoil (\d+)')
    found, stack = [], list(elev.children)
    while stack:
        o = stack.pop()
        m = pat.fullmatch(_kit_name(o.name))
        if m:
            found.append((int(m.group(1)), o))
        stack.extend(o.children)
    return sorted(found, key=lambda t: t[0])


def _recoil(ctx, a, rig, frame, spec):
    """Kick the gun's barrel back along its bore into its sleeve at `frame` and run it out again
    (spec m: travel in metres, return_s: run-out). It keys the barrel's recoil empty, a hull
    object, never the pivot or elevation aim() keys, and only on a ship whose hull is its own
    (ctx.own_hull, as ship_fx sets it): on a shared hull every copy of the build would kick.
    A shot inside the last one's run-out kicks from where the barrel is. Returns the empties."""
    if not getattr(ctx, 'own_hull', True):
        return []
    if 'recoil' not in rig:
        rig['recoil'] = _recoil_empties(a)
    found = rig['recoil']
    if not found:
        return []
    k = gun_index(a)
    pick = [o for n, o in found if n == k] if len(found) > 1 else [found[0][1]]
    pick = pick or [o for _, o in found]
    fps = ctx.scene.render.fps or swfx_core.FPS
    n = max(2, int(round(spec['return_s'] * fps)))
    bore = (anchor_matrix(a.empty).to_3x3() @ Vector((1, 0, 0))).normalized()
    out = []
    for e in pick:
        rest = Vector(tuple(e['swfx_recoil_rest'])) if 'swfx_recoil_rest' in e else e.location.copy()
        e['swfx_recoil_rest'] = list(rest)
        B = (anchor_matrix(e.parent) @ e.matrix_parent_inverse).to_3x3() if e.parent else Matrix.Identity(3)
        back = B.inverted() @ (-bore * swfx_core.units(spec['m']))
        keys = {int(f): v for f, v in (e.get('swfx_recoil') or {}).items()}
        keys.setdefault(frame - 1, 0.0)
        # The shot frame already shows the barrel on its way; the next is full travel; then it
        # runs out, quickly at first and settling.
        keys[frame] = max(keys.get(frame, 0.0), .65)
        keys[frame + 1] = 1.0
        for j in range(1, n):
            keys[frame + 1 + j] = (1 - j / (n - 1)) ** 2.2
        e['swfx_recoil'] = {str(f): v for f, v in keys.items()}
        fr = sorted(keys)
        for i in range(3):
            _bake(e, 'location', i, fr, [rest[i] + back[i] * keys[f] for f in fr])
        e.location = rest
        out.append(e.name)
    return out


def _ring_material(ctx, rgb, name):
    """The pressure ring: a thin torus of hot gas about the bore (object X), in the unit box at
    radius .72. Keyed like a glow (GLOW_NODE: R fade, G seed, B emission per metre / 20), and
    torn by noise so it reads as a shock of gas, not a hoop."""
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    p = tc.outputs['Object']
    ctl = nodes.new('ShaderNodeRGB')
    ctl.name = ctl.label = GLOW_NODE
    ctl.outputs[0].default_value = (0, 0, 0, 1)
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(ctl.outputs[0], sep.inputs[0])
    fade, seed, k = sep.outputs[0], sep.outputs[1], sep.outputs[2]
    x, y, z = g.xyz(p)
    rho = g.op('SQRT', g.add(g.sq(y), g.sq(z)))
    band = g.exp_neg(g.mul(g.sq(g.sub(rho, .72)), 60.0))
    slab = g.exp_neg(g.mul(g.sq(x), 7.0))
    n1 = g.noise(p, g.mul(seed, 41.0), 3.4, 3.0, .6)
    lumps = g.mul(g.clamp01(g.mul(g.sub(n1, .34), 2.8)), 1.4)
    dens = g.mul(g.mul(g.mul(band, slab), lumps), fade)
    mix = nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    links.new(g.mul(g.clamp01(g.sub(dens, .8)), .35), mix.inputs['Factor'])
    mix.inputs['A'].default_value = (*rgb, 1)
    mix.inputs['B'].default_value = (1, 1, 1, 1)
    em = nodes.new('ShaderNodeEmission')
    links.new(mix.outputs['Result'], em.inputs['Color'])
    links.new(g.mul(dens, g.mul(k, 20.0)), em.inputs['Strength'])
    links.new(em.outputs[0], out.inputs['Volume'])
    return m


def _ring(ctx, coll, a, label, frame, spec, rgb):
    """The pressure ring a heavy round's gas throws off the muzzle: it snaps out from the bore to
    r[1] metres over `frames`, a little downrange, and is gone."""
    name = f'{PREFIX} ring {label} f{frame:04d}'
    ob = bpy.data.objects.new(name, _unit_box_mesh(f'{name} mesh', _ring_material(ctx, rgb, f'{name} gas')))
    coll.objects.link(ob)
    ob.visible_shadow = False
    swfx_core.attach(ob, a.empty)
    r0, r1 = spec['r']
    life = max(2, int(spec['frames']))
    fr, sc, th, lx, fade, kk = [], [], [], [], [], []
    for j in range(life + 1):
        t = j / life
        e = 1 - (1 - t) ** 3.0
        R = (r0 + (r1 - r0) * e) / .72
        thick = spec.get('thick', .18) * R + .1
        fr.append(frame + j)
        sc.append(swfx_core.units(R))
        th.append(swfx_core.units(thick))
        lx.append(swfx_core.units(.25 * r1 + .35 * r1 * e))
        fade.append((1 - t) ** 1.8)
        kk.append(_hull_glow(spec['peak'] / (1.2 * thick)) / 20.0)
    _bake(ob, 'scale', 0, fr, th)
    for i in (1, 2):
        _bake(ob, 'scale', i, fr, sc)
    _bake(ob, 'location', 0, fr, lx)
    t = _glow_keys(ob)
    _bake(t, GLOW_PATH, 0, fr, fade)
    _bake(t, GLOW_PATH, 1, [frame], [random.Random(f'ring|{label}|{frame}').random()], 'CONSTANT')
    _bake(t, GLOW_PATH, 2, fr, kk)
    _visible_between(ob, frame, frame + life)
    return ob.name


def _hot_smoke_material(ctx, rgb, name):
    """A heavy round's propellant smoke: grey lumpy gas that glows the weapon's colour while it is
    hot and then only scatters the light. Its own GLOW_NODE: R density per metre (hull units), G a
    seed, B emission per metre (hull units). Object coordinates span -1..1 in the unit box."""
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    p = tc.outputs['Object']
    ctl = nodes.new('ShaderNodeRGB')
    ctl.name = ctl.label = GLOW_NODE
    ctl.outputs[0].default_value = (0, 0, 0, 1)
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(ctl.outputs[0], sep.inputs[0])
    dens, seed, heat = sep.outputs[0], sep.outputs[1], sep.outputs[2]
    x, y, z = g.xyz(p)
    r2 = g.add(g.add(g.sq(x), g.sq(y)), g.sq(z))
    shell = g.pow(g.mx(g.sub(1.0, r2), 0.0), 1.3)
    w = g.mul(seed, 71.0)
    n1 = g.noise(p, w, 2.2, 5.0, .64)
    n2 = g.noise(p, g.add(w, 5.0), 6.0, 2.0, .5)
    lumps = g.pow(g.clamp01(g.mul(g.sub(g.add(n1, g.mul(n2, .25)), .5), 3.0)), 1.4)
    field = g.mul(shell, lumps)
    vol = nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Color'].default_value = (.5, .48, .45, 1)
    vol.inputs['Anisotropy'].default_value = .3
    links.new(g.mul(field, dens), vol.inputs['Density'])
    vol.inputs['Emission Color'].default_value = (*rgb, 1)
    links.new(g.mul(g.pow(field, 1.5), heat), vol.inputs['Emission Strength'])
    links.new(vol.outputs[0], out.inputs['Volume'])
    return m


def _hot_smoke(ctx, coll, a, label, frame, rnd, spec, rgb):
    """The smoke a heavy round leaves: thrown downrange, glowing as it leaves the muzzle and
    cooling over heat_s, then hanging and thinning over life_s (longer in air, gun_atmosphere)."""
    fps = ctx.scene.render.fps or swfx_core.FPS
    atm = _knob(ctx, 'gun_atmosphere')
    gain = _knob(ctx, 'gun_smoke_gain')
    life = max(4, int(round(fps * spec['life_s'] * (1 + .6 * atm) * rnd.uniform(.9, 1.1))))
    hot = max(1, int(round(fps * spec['heat_s'])))
    name = f'{PREFIX} smoke {label} f{frame:04d}'
    ob = bpy.data.objects.new(name, _unit_box_mesh(f'{name} mesh', _hot_smoke_material(ctx, rgb, f'{name} gas')))
    coll.objects.link(ob)
    ob.visible_shadow = False
    swfx_core.attach(ob, a.empty)
    ob.rotation_euler = (rnd.uniform(0, 2 * math.pi), 0, 0)
    stretch = (1.8, rnd.uniform(.8, 1.0), rnd.uniform(.7, .9))       # thrown downrange, never a ball
    r0, r1 = spec['r']
    d0 = spec['density'] * (.35 + .65 * atm) * gain
    fr, sc, lx, dd, hh = [], [], [], [], []
    for j in range(life + 1):
        t = j / life
        e = 1 - (1 - t) ** (2.6 - 1.2 * atm)
        r = r0 + (r1 - r0) * e
        fr.append(frame + j)
        sc.append(swfx_core.units(r))
        lx.append(swfx_core.units(.6 * r + spec.get('drift', .5) * r1 * e))
        a_in = min(1.0, (j + 1) / 2.0)
        # Thinning as it spreads (the same smoke in a larger volume), and fading at the end.
        dd.append(_hull_glow(d0 * a_in * (r0 / r) ** 1.5 * (1 - t ** 2)))
        ht = max(0.0, 1 - j / hot)
        hh.append(_hull_glow(spec['glow'] * ht ** 2 / (2 * r)))
    for i in range(3):
        _bake(ob, 'scale', i, fr, [v * stretch[i] for v in sc])
    _bake(ob, 'location', 0, fr, lx)
    t = _glow_keys(ob)
    _bake(t, GLOW_PATH, 0, fr, dd)
    _bake(t, GLOW_PATH, 1, [frame], [rnd.random()], 'CONSTANT')
    _bake(t, GLOW_PATH, 2, fr, hh)
    _visible_between(ob, frame, frame + life)
    return ob.name


def _heavy(ctx, coll, a, rig, label, frame, rnd, w):
    """The parts of a heavy shot beyond the flash and its gas: recoil, pressure ring, hot smoke."""
    h = w['heavy']
    rgb = _lin(w['flash_tint'] or w['flash_light'])
    rec = {}
    if h.get('recoil'):
        rec['recoil'] = _recoil(ctx, a, rig, frame, h['recoil'])
    if h.get('ring'):
        rec['ring'] = _ring(ctx, coll, a, label, frame, h['ring'], rgb)
    if h.get('smoke'):
        rec['smoke'] = _hot_smoke(ctx, coll, a, label, frame, rnd, h['smoke'], rgb)
    return rec


def _slug_trail_material(ctx, weapon):
    """A heavy slug's ember trail: a thin dim rod behind the slug (object X -1 tail .. 0 slug),
    the weapon's fringe colour at the slug dying to ember red, broken into flecks by noise fixed
    in space, so the embers stay where they were shed and the slug draws away from them. Object
    colour R is the burn (keyed), as on the round."""
    name = f'{PREFIX} round trail {weapon}'
    m = bpy.data.materials.get(name)
    if m:
        return m
    r = WEAPONS[weapon]['round']
    spec = r['trail']
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    gx, _, _ = g.xyz(tc.outputs['Generated'])           # 0 tail .. 1 at the slug
    geo = nodes.new('ShaderNodeNewGeometry')
    info = _object_colour(nodes)
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    burn = sep.outputs[0]
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 1.6)
    flecks = g.noise(geo.outputs['Position'], 0.0, 1.0 / swfx_core.units(spec.get('fleck_m', 1.2)), 2.0, .55)
    flecks = g.add(.25, g.mul(g.clamp01(g.mul(g.sub(flecks, .45), 4.0)), 1.6))
    along = g.pow(gx, 2.4)
    gain = swfx_core.value_node(ctx, nodes, 'tracer gain', 'gun_tracer_gain', ('gun_tracer_gain',))
    strength = g.mul(g.mul(g.mul(along, flecks), g.mul(facing, burn)), g.mul(gain, float(spec['gain'])))
    mix = nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    links.new(g.pow(gx, 1.5), mix.inputs['Factor'])
    mix.inputs['A'].default_value = (*_lin(spec.get('ember', (200, 45, 15))), 1)
    mix.inputs['B'].default_value = (*_lin(r['fringe']), 1)
    em = nodes.new('ShaderNodeEmission')
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


def _slug_trail(ctx, coll, label, frame, weapon, frames, heads, burn, M, d, rot, L):
    """The ember trail behind a heavy slug, its head on the slug's tail: it grows out of the
    muzzle to its length and then rides behind the slug."""
    spec = WEAPONS[weapon]['round']['trail']
    Lt = swfx_core.units(spec['length'])
    ob = bpy.data.objects.new(f'{PREFIX} round trail {label} f{frame:04d}', _cylinder_mesh(
        f'{PREFIX} round trail mesh {weapon}', swfx_core.units(spec['radius']), -1.0, 0.0, 8,
        _slug_trail_material(ctx, weapon)))
    coll.objects.link(ob)
    ob.rotation_euler = rot
    ob.visible_shadow = False
    tails = [max(0.0, hh - L) for hh in heads]
    for i in range(3):
        _bake(ob, 'location', i, frames, [(M @ (d * tt))[i] for tt in tails])
    _bake(ob, 'scale', 0, frames, [max(1e-3, min(Lt, tt)) for tt in tails])
    _bake(ob, 'color', 0, frames, burn)
    ob.color = (1, 0, 0, 1)
    _visible_between(ob, frame, frame + len(frames) - 1)
    return ob.name


def _feeder_material(ctx, knob):
    name = f'{PREFIX} feeder {knob}'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    gx, _, _ = g.xyz(tc.outputs['Generated'])
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    facing = g.add(.35, g.mul(g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 1.2), .65))
    on = swfx_core.value_node(ctx, nodes, 'feeder', knob, (knob,))
    ends = g.mul(g.smooth(0.0, .2, gx), g.sub(1.0, g.smooth(.85, 1.0, gx)))
    em = nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (*_lin(FEEDER_RGB), 1)
    links.new(g.mul(g.mul(g.mul(facing, ends), on), 6.0), em.inputs['Strength'])
    tr = nodes.new('ShaderNodeBsdfTransparent')
    add = nodes.new('ShaderNodeAddShader')
    links.new(tr.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    links.new(add.outputs[0], out.inputs['Surface'])
    return m


def feeder(ctx, turret, start_frame, end_frame, ramp_frames=6, light=2500.0):
    """The Accelerated Ammo Feeder on one turret: its barrels glow amber (255,200,0) from
    start_frame to end_frame on the knob gun_feeder_t<turret>. Fire it with
    fire_weapon(..., feeder=True) over the same frames for the doubled rate."""
    knob = f'gun_feeder_t{turret}'
    ctx.prop(knob, 0.0, 0.0, 1.0, f'turret {turret} Ammo Feeder glow (keyed by feeder())')
    for f, val in ((start_frame - 1, 0.0), (start_frame + ramp_frames, 1.0), (end_frame, 1.0),
                   (end_frame + ramp_frames, 0.0)):
        ctx.key(knob, f, val, 'LINEAR')
    coll = ctx.collection(GROUP)
    mat = _feeder_material(ctx, knob)
    names = []
    for a in ctx.anchors.guns:
        if a.turret != turret or getattr(a, 'kind', 'gun') == 'launcher':
            continue
        s = a.scale
        ob = bpy.data.objects.new(f'{PREFIX} feeder {gun_label(a)}',
                                  _cylinder_mesh(f'{PREFIX} feeder mesh t{turret}', 1.5, -6.0, -.2, 12, mat))
        coll.objects.link(ob)
        swfx_core.attach(ob, a.empty)
        ob.scale = (s, s, s)
        ob.visible_shadow = False
        lt = swfx_core.point_light(f'{PREFIX} feeder light {gun_label(a)}', coll, _lin(FEEDER_RGB), 0.0,
                                   radius=.6 * s)
        lt.visible_camera = False
        swfx_core.attach(lt, a.empty, local=(-3.0 * s, 0, .8 * s))
        ctx.drive(lt.data, 'energy', f'{knob}*{_hull_watts(light):g}', (knob,))
        names += [ob.name, lt.name]
    return names


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


COLD_EJECT = .3           # a planned flight's launch bloom: a gas puff as the tube ejects it, motor not lit


def launch_flights(ctx, flights):
    """Key planned flights (swfx_missile_paths.Flight, opt-in): each from the tube its label
    names ('t5_1'), on its own launch frame, along its own curved path to its own end. The
    plain fire() path is unchanged. Returns one record per flight."""
    out = []
    for fl in flights:
        a = next((g for g in ctx.anchors.guns if gun_label(g) == fl.label
                  and getattr(g, 'kind', 'gun') == 'launcher'), None)
        if a is None:
            raise KeyError(f'no launcher tube {fl.label} on this ship')
        out.append(_launch(ctx, a, fl.frame, flight=fl))
    return out


def _launch(ctx, a, frame, flight=None):
    """Key one missile launch from one tube at an integer frame. Returns its record.
    flight: a planned swfx_missile_paths.Flight (cold eject, curved path, contrail, its end);
    None flies the kit's straight missile."""
    coll = ctx.collection(GROUP)
    label = gun_label(a)
    rig = _rig(ctx, a)
    n = rig['count']
    rig['count'] += 1
    rnd = random.Random(f'swfx-launch|{a.turret}|{a.tag}|{frame}|{n}')
    s = a.scale
    fps = ctx.scene.render.fps or swfx_core.FPS

    gain = rnd.uniform(.85, 1.15) * (COLD_EJECT if flight is not None else 1.0)
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
    if flight is not None:
        import swfx_missile_paths
        rec.update(swfx_missile_paths.key(ctx, coll, flight, a, _motor_material(ctx), _missile_material(),
                                          _cylinder_mesh, swfx_core.METRES_PER_UNIT))
        return rec

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
    # (b) turret 4 (empty now; an older build's twin), a burst over the shoulder.
    a4p = _anchor(ctx, 4, 'barrel 1') or _anchor(ctx, 4, 'port gun') or _anchor(ctx, 2, 'barrel 1')
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
