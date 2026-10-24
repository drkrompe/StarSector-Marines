"""Fire, venting and explosions: the opt-in 'fire' group, for wrecks and kills.

Not in swfx.GROUPS: a shot asks for it with `swfx.apply(groups=(..., 'fire'))`.
Canon keeps fire off the Second Wind, so nothing here runs unless a scene has
the anchors below; a Second Wind file has none and gets an empty record.

Anchors, found by name on any hull (an export's own empties, usually its
condition state's scar anchors):
  '... fire anchor ...'       a burning breach: a flickering fire volume, its
                              smoke, and a flickering light. Optional custom
                              props on the empty: 'size_m' (fire radius,
                              default 3.5 m), 'seed'.
  '... vent anchor ...'       a ruptured tank or line venting along its +X: a
                              cone of pale propellant vapour, pulsing.
                              Optional 'length_m' (default 30 m), 'seed'.
  '... explosion anchor ...'  a detonation: a fireball that blooms, cools and
                              turns to smoke, a flash light, a shock ring in
                              the anchor's YZ plane, and a burst of embers.
                              Optional 'delay_s' (after fir_exp_frame),
                              'size_m' (fireball radius, default 30 m), 'seed',
                              and for the lesser blasts of a chain (a
                              cook-off): 'ring' (shock ring strength, 0 none,
                              default 1), 'flash' (flash light strength,
                              default 1), 'embers' (count, default 120).
Every effect is a child of its anchor, so it rides with the wreck section
the anchor hangs from.

Explosion style: 'fireball' (the default, the accepted Phaeton cook-off of
2026-10-19) or 'jagged': swfx_burst's irregular burst instead of the round
volume fireball and shock ring (owner, 2026-10-21: no orb explosions), with
the same flash light and embers. Opt in per anchor (a string prop 'style')
or for a whole build (build(..., blast_style='jagged')). A jagged blast is
keyed at build time from fir_exp_frame + delay_s, fir_exp_scale and fir_exp,
so re-run the build after changing those knobs. A build that already
carries volume fireballs (the Phaeton wreck's baked cook-off) is switched on
its scene copy by jagged_cookoff(ctx), after its break_frame is set.

Knobs (all on 'SWFX controls'):
  fir_fire        fire amount on every fire anchor (0 out, 1 burning, 2 raging)
  fir_vent        venting amount on every vent anchor
  fir_smoke       smoke from the fires (0 none, 1 thick)
  fir_ignite      seconds after fir_exp_frame for the fires and vents to come
                  up (they ramp in over a second; 0 = burning from the start
                  of the shot when fir_exp_frame is before it)
  fir_exp_frame   frame of the detonation (every explosion anchor's t = 0)
  fir_exp_scale   explosion size multiplier
  fir_exp         explosion amount (0 suppresses it, for a wreck already burning)
Everything is a pure function of the frame and the knobs (procedural volumes
and geometry-node points), so the same file renders the same frames.
Sizes are in metres through swfx_core.units, so they follow the hull's scale.
"""
import math
import re
import zlib

import bpy
from mathutils import Vector

import swfx_core
from swfx_core import node_material, units, value_node
from swfx_smoke import (G, _ballistic, _no_shadow_catch, _rnd, _sock, _spark_group, _spark_material,
                        _spark_streaks, box_mesh, volume_output)

GROUP = 'Fire'
PREFIX = 'SWFX fire'
STYLE = 'fireball'        # explosion style when neither the anchor nor the build names one
FPS = swfx_core.FPS
FIRE = re.compile(r'fire anchor', re.I)
VENT = re.compile(r'vent anchor', re.I)
BLAST = re.compile(r'explosion anchor', re.I)
FIRE_M, VENT_M, BLAST_M = 3.5, 30.0, 30.0


def _seed(ob):
    return int(ob.get('seed', zlib.crc32(ob.name.encode()) % 997))


def _knobs(ctx):
    ctx.prop('fir_fire', 1.0, 0, 2, 'Fire amount on every fire anchor')
    ctx.prop('fir_vent', 1.0, 0, 2, 'Venting amount on every vent anchor')
    ctx.prop('fir_smoke', 1.0, 0, 2, 'Smoke from the fires')
    ctx.prop('fir_ignite', 0.0, -10, 30, 'Seconds after fir_exp_frame when fires and vents come up')
    ctx.prop('fir_exp_frame', 1.0, -10000, 100000, 'Frame of the detonation')
    ctx.prop('fir_exp_scale', 1.0, 0, 5, 'Explosion size multiplier')
    ctx.prop('fir_exp', 1.0, 0, 2, 'Explosion amount (0 suppresses it)')
    ctx.prop('smk_atmosphere', 0.0, 0, 1, '0 vacuum, 1 atmosphere (shared with the smoke group)')


def _ramp_expr():
    # 0 before ignition, 1 a second after; frame and fir_exp_frame in frames.
    return f'min(max(((frame-fir_exp_frame)/{FPS}-fir_ignite),0),1)'


def _blackbody(g, temperature):
    bb = g.node('ShaderNodeBlackbody')
    g._in(bb.inputs['Temperature'], temperature)
    return bb.outputs[0]


def _link_emission_color(g, vol, color):
    g.links.new(color, vol.inputs['Emission Color'])


def _fire_material(ctx, name, seed, radius):
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    t = value_node(ctx, nodes, 't', f'frame/{FPS}')
    amount = value_node(ctx, nodes, 'amount', f'fir_fire*{_ramp_expr()}', ('fir_fire', 'fir_exp_frame', 'fir_ignite'))
    # Domain warp: a churning vector noise displaces the space the flame is
    # measured in, so its edge breaks into ragged tongues instead of a ball.
    warp = g.node('ShaderNodeTexNoise', noise_dimensions='4D')
    g._in(warp.inputs['Vector'], co)
    g._in(warp.inputs['W'], g.m('MULTIPLY_ADD', t, 2.2, seed*1.3))
    g._in(warp.inputs['Scale'], 1.3/radius)
    warp.inputs['Detail'].default_value = 3.0
    warp.inputs['Roughness'].default_value = .6
    off = g.v('SCALE', g.v('SUBTRACT', warp.outputs['Color'], (.5, .5, .5)), scale=radius*1.6)
    d = g.m('DIVIDE', g.v('LENGTH', g.v('ADD', co, off)), radius)
    # Tongues: a fast-churning noise, stretched outward, eats the ball's edge.
    w = g.m('MULTIPLY_ADD', t, 1.6, seed*3.1)
    n = g.noise(g.v('SCALE', co, scale=1.0), w, 2.2/radius, detail=4.0, rough=.6, distortion=.4)
    n2 = g.noise(co, g.m('MULTIPLY_ADD', t, 3.3, seed), 5.0/radius, detail=2.0)
    core = g.m('SUBTRACT', 1.0, g.mr(d, .05, .85))
    shape = g.m('MULTIPLY', core, g.mr(g.m('MULTIPLY_ADD', n, 1.0, g.m('MULTIPLY', n2, .35)), .58, .82))
    dens = g.m('MULTIPLY', g.m('MULTIPLY', shape, 2.5/units(1.0)), amount)
    temp = g.m('MULTIPLY_ADD', shape, 1700.0, 1100.0)
    vol = volume_output(g, dens, (0.05, 0.03, 0.02, 1), (0.02, 0.02, 0.02, 1), .1,
                        emission=g.m('MULTIPLY', g.m('MULTIPLY', shape, 24.0/units(1.0)), amount))
    _link_emission_color(g, vol, _blackbody(g, temp))
    return m


def _smoke_material(ctx, name, seed, radius):
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    t = value_node(ctx, nodes, 't', f'frame/{FPS}')
    amount = value_node(ctx, nodes, 'amount', f'fir_smoke*fir_fire*{_ramp_expr()}',
                        ('fir_smoke', 'fir_fire', 'fir_exp_frame', 'fir_ignite'))
    d = g.m('DIVIDE', g.v('LENGTH', co), radius)
    n = g.noise(co, g.m('MULTIPLY_ADD', t, .25, seed*1.7), 1.6/radius, detail=5.0, rough=.62, distortion=.2)
    shape = g.m('MULTIPLY', g.m('SUBTRACT', 1.0, g.mr(d, .2, 1.0)), g.mr(n, .4, .8))
    dens = g.m('MULTIPLY', g.m('MULTIPLY', shape, .35/units(1.0)), amount)
    volume_output(g, dens, (0.06, 0.055, 0.05, 1), (0.6, 0.6, 0.62, 1), .3)
    return m


def _vent_material(ctx, name, seed, length):
    """A cone along +X: pale vapour, dense at the mouth, spreading and thinning."""
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    x, y, z = g.sep(co)
    t = value_node(ctx, nodes, 't', f'frame/{FPS}')
    pulse = f'(0.75+0.25*sin(frame*0.71+{seed})*sin(frame*0.23+{seed*2}))'
    amount = value_node(ctx, nodes, 'amount', f'fir_vent*{_ramp_expr()}*{pulse}',
                        ('fir_vent', 'fir_exp_frame', 'fir_ignite'))
    u = g.m('DIVIDE', x, length)
    rad = g.m('MULTIPLY_ADD', u, length*.22, units(.6))
    r = g.m('DIVIDE', g.v('LENGTH', g.comb(0.0, y, z)), rad)
    along = g.m('MULTIPLY', g.mr(u, -.02, .03), g.m('SUBTRACT', 1.0, g.mr(u, .25, 1.0)))
    flow = g.comb(g.m('SUBTRACT', x, g.m('MULTIPLY', t, units(18.0))), y, z)
    n = g.noise(flow, g.m('MULTIPLY_ADD', t, .8, seed), 3.0/length, detail=4.0, rough=.6)
    shape = g.m('MULTIPLY', g.m('MULTIPLY', g.gauss(r, 1.0, 1.6), along), g.mr(n, .3, .75))
    dens = g.m('MULTIPLY', g.m('MULTIPLY', shape, .9/units(1.0)), amount)
    volume_output(g, dens, (0.85, 0.87, 0.9, 1), (0.9, 0.92, 0.95, 1), .35)
    return m


def _fireball_material(ctx, name, seed, radius, delay):
    """Blooms in ~0.3 s from a white flash, burns orange by ~0.5 s, cools to dark smoke by ~2 s."""
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    te = f'((frame-fir_exp_frame)/{FPS}-{delay})'
    t = value_node(ctx, nodes, 'since', te, ('fir_exp_frame',))
    on = value_node(ctx, nodes, 'on', f'fir_exp*({te}>0)', ('fir_exp', 'fir_exp_frame'))
    scale = value_node(ctx, nodes, 'scale', 'fir_exp_scale', ('fir_exp_scale',))
    R = g.m('MULTIPLY', scale, radius)
    grow = g.m('SUBTRACT', 1.0, g.m('EXPONENT', g.m('MULTIPLY', t, -4.5)))       # 0 -> 1, ~0.5 s
    r_now = g.m('MULTIPLY', R, g.m('MULTIPLY_ADD', grow, .95, .05))
    d = g.m('DIVIDE', g.v('LENGTH', co), r_now)
    n = g.noise(co, g.m('MULTIPLY_ADD', t, .9, seed*2.3), g.m('DIVIDE', 2.4, R), detail=5.0, rough=.6, distortion=.35)
    shell = g.m('SUBTRACT', 1.0, g.mr(g.m('MULTIPLY_ADD', n, -.5, d), .2, .8))   # billowed edge
    heat = g.m('EXPONENT', g.m('MULTIPLY', t, -2.2))                              # 1 -> 0 over ~1.5 s
    fade = g.m('EXPONENT', g.m('MULTIPLY', t, -.45))                             # smoke thins over ~5 s
    dens = g.m('MULTIPLY', g.m('MULTIPLY', g.m('MULTIPLY', shell, on), fade),
               g.m('DIVIDE', 1.4, g.m('MULTIPLY', scale, units(1.0))))
    temp = g.m('MULTIPLY_ADD', g.m('MULTIPLY', heat, g.mr(n, .2, .8, .75, 1.0)), 6500.0, 1000.0)
    em = g.m('MULTIPLY', g.m('MULTIPLY', g.m('MULTIPLY', shell, on), g.m('POWER', heat, 3.5)),
             g.m('DIVIDE', 16.0, g.m('MULTIPLY', scale, units(1.0))))
    vol = volume_output(g, dens, (0.05, 0.045, 0.04, 1), (0.18, 0.18, 0.19, 1), .2, emission=em)
    _link_emission_color(g, vol, _blackbody(g, temp))
    return m


def _ring_material(ctx, name, delay, gain=1.0):
    # A thin bright band at the disc's rim (object radius 1), soft inside: an
    # annulus, not a filled disc, so the ring reads against the fireball.
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    te = f'((frame-fir_exp_frame)/{FPS}-{delay})'
    strength = value_node(ctx, nodes, 'strength', f'fir_exp*({te}>0)*exp(-max({te},0)*3.2)*{1.2*gain:.3f}',
                          ('fir_exp', 'fir_exp_frame'))
    r = g.v('LENGTH', g.node('ShaderNodeTexCoord').outputs['Object'])
    band = g.m('MULTIPLY', g.mr(r, .86, .965, smooth=True), g.m('SUBTRACT', 1.0, g.mr(r, .97, 1.0)))
    em = g.node('ShaderNodeEmission')
    em.inputs['Color'].default_value = (1.0, .78, .5, 1)
    g._in(em.inputs['Strength'], g.m('MULTIPLY', strength, band))
    tr = g.node('ShaderNodeBsdfTransparent')
    add = g.node('ShaderNodeAddShader')
    g.links.new(em.outputs[0], add.inputs[0])
    g.links.new(tr.outputs[0], add.inputs[1])
    out = g.node('ShaderNodeOutputMaterial')
    g.links.new(add.outputs[0], out.inputs['Surface'])
    return m


def _container(name, coll, anchor, half, material, x0=None, x1=None):
    lo = (-half if x0 is None else x0, -half, -half)
    hi = (half if x1 is None else x1, half, half)
    ob = box_mesh(name, coll, lo, hi, material)
    swfx_core.attach(ob, anchor)
    return ob


def _flicker_light(ctx, name, coll, anchor, metre_watts, seed, colour=(1.0, .5, .18)):
    from swfx_lighting import watts
    light = swfx_core.point_light(name, coll, colour, 0.0, radius=units(1.5))
    swfx_core.attach(light, anchor)
    expr = (f'{watts(metre_watts):.1f}*fir_fire*{_ramp_expr()}*'
            f'(0.7+0.18*sin(frame*1.31+{seed})+0.12*sin(frame*3.7+{seed*3}))')
    ctx.drive(light.data, 'energy', expr, ('fir_fire', 'fir_exp_frame', 'fir_ignite'))
    return light


def _stream_axes(anchor, sweep=.9):
    """(S, E) unit vectors in the anchor's frame (+Z out of the hull): S the way a breach fire
    streams, out of the hole and swept aft (the hull's -X) as the gas leaving her is left behind
    her; E the breach edge it burns along, in the hull plane across the stream. A custom prop
    'stream' (x, y, z in the hull's frame) overrides the sweep."""
    from mathutils import Vector as V
    R = anchor.matrix_world.to_3x3().normalized()
    want = V(anchor['stream']) if 'stream' in anchor else V((-1.0, 0.0, 0.0))
    t = R.transposed() @ want
    t.z = 0.0
    if t.length < 1e-6:
        t = V((1.0, 0.0, 0.0))
    S = (V((0.0, 0.0, 1.0)) + t.normalized() * (sweep if 'stream' not in anchor else 1.0)).normalized()
    E = V((0.0, 0.0, 1.0)).cross(S).normalized()
    return S, E


def _stream_fire_material(ctx, name, seed, radius, length, edge):
    """A breach flame streaming along its container's +X from the hull at x=0: tongues licking out
    of an edge `edge` long (its container's Y) and thinning as they go `length` downstream, hottest
    at the root (owner, 2026-10-23: no orbs or blobs; damage fire follows the burn, flame tongues
    along the breach edge streaming with the flow). No distance from a point anywhere in it: the
    body is the distance from the edge line, so it cannot read as a ball."""
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    t = value_node(ctx, nodes, 't', f'frame/{FPS}')
    amount = value_node(ctx, nodes, 'amount', f'fir_fire*{_ramp_expr()}', ('fir_fire', 'fir_exp_frame', 'fir_ignite'))
    # the flow: features carried downstream at about a flame length a second
    flow = g.v('SUBTRACT', co, g.comb(g.m('MULTIPLY', t, length * 1.1), 0.0, 0.0))
    warp = g.node('ShaderNodeTexNoise', noise_dimensions='4D')
    g._in(warp.inputs['Vector'], g.v('MULTIPLY', flow, (.45, 1.0, 1.0)))
    g._in(warp.inputs['W'], g.m('MULTIPLY_ADD', t, 1.4, seed * 1.3))
    g._in(warp.inputs['Scale'], 1.2 / radius)
    warp.inputs['Detail'].default_value = 3.0
    warp.inputs['Roughness'].default_value = .6
    off = g.v('MULTIPLY', g.v('SUBTRACT', warp.outputs['Color'], (.5, .5, .5)), (radius * .5, radius * 1.1, radius * 1.1))
    p = g.v('ADD', co, off)
    x, y, z = g.sep(p)
    u = g.m('DIVIDE', x, length)                                    # 0 at the hull, 1 at the tips
    ey = g.m('MAXIMUM', g.m('SUBTRACT', g.m('ABSOLUTE', y), edge * .5), 0.0)  # off the end of the edge
    dp = g.m('SQRT', g.m('ADD', g.m('MULTIPLY', ey, ey), g.m('MULTIPLY', z, z)))
    width = g.m('MULTIPLY_ADD', g.mr(u, 0.0, 1.0), -radius * .42, radius * .5)
    body = g.m('SUBTRACT', 1.0, g.mr(g.m('DIVIDE', dp, g.m('MAXIMUM', width, radius * .04)), .15, 1.0))
    along = g.m('MULTIPLY', g.mr(x, -radius * .15, radius * .1), g.m('SUBTRACT', 1.0, g.mr(u, .25, 1.0, smooth=True)))
    # tongues: noise stretched along the flow (four times longer than wide), so the edge breaks
    # into licks pulled downstream, not into round puffs
    n = g.noise(g.v('MULTIPLY', flow, (.25, 1.0, 1.0)), g.m('MULTIPLY_ADD', t, 2.6, seed * 3.1), 2.4 / radius,
                detail=4.0, rough=.62, distortion=.5)
    n2 = g.noise(g.v('MULTIPLY', flow, (.4, 1.0, 1.0)), g.m('MULTIPLY_ADD', t, 4.1, seed), 6.0 / radius, detail=2.0)
    licks = g.mr(g.m('MULTIPLY_ADD', n, 1.0, g.m('MULTIPLY', n2, .35)), g.m('MULTIPLY_ADD', u, .22, .5), .8)
    shape = g.m('MULTIPLY', g.m('MULTIPLY', body, along), licks)
    dens = g.m('MULTIPLY', g.m('MULTIPLY', shape, 2.5 / units(1.0)), amount)
    temp = g.m('MULTIPLY_ADD', g.m('MULTIPLY', shape, g.m('MULTIPLY_ADD', u, -.6, 1.0)), 1700.0, 1000.0)
    vol = volume_output(g, dens, (0.05, 0.03, 0.02, 1), (0.02, 0.02, 0.02, 1), .1,
                        emission=g.m('MULTIPLY', g.m('MULTIPLY', shape, 24.0 / units(1.0)), amount))
    _link_emission_color(g, vol, _blackbody(g, temp))
    return m


def _stream_smoke_material(ctx, name, seed, radius, length):
    """The fire's smoke: a plume along +X from the flame, widening and thinning downstream."""
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    t = value_node(ctx, nodes, 't', f'frame/{FPS}')
    amount = value_node(ctx, nodes, 'amount', f'fir_smoke*fir_fire*{_ramp_expr()}',
                        ('fir_smoke', 'fir_fire', 'fir_exp_frame', 'fir_ignite'))
    x, y, z = g.sep(co)
    u = g.m('DIVIDE', x, length)
    dp = g.m('SQRT', g.m('ADD', g.m('MULTIPLY', y, y), g.m('MULTIPLY', z, z)))
    width = g.m('MULTIPLY_ADD', g.mr(u, 0.0, 1.0), radius * 1.4, radius * .5)
    body = g.m('SUBTRACT', 1.0, g.mr(g.m('DIVIDE', dp, width), .2, 1.0))
    along = g.m('MULTIPLY', g.mr(x, 0.0, radius * .6), g.m('SUBTRACT', 1.0, g.mr(u, .3, 1.0, smooth=True)))
    flow = g.v('SUBTRACT', co, g.comb(g.m('MULTIPLY', t, length * .35), 0.0, 0.0))
    n = g.noise(g.v('MULTIPLY', flow, (.5, 1.0, 1.0)), g.m('MULTIPLY_ADD', t, .25, seed * 1.7), 1.6 / radius,
                detail=5.0, rough=.62, distortion=.3)
    shape = g.m('MULTIPLY', g.m('MULTIPLY', body, along), g.mr(n, .38, .78))
    dens = g.m('MULTIPLY', g.m('MULTIPLY', shape, .35 / units(1.0)), amount)
    volume_output(g, dens, (0.06, 0.055, 0.05, 1), (0.6, 0.6, 0.62, 1), .3)
    return m


def build_fire(ctx, coll, anchor):
    """A breach fire on anchor: its flame, smoke and flickering light. The anchor's prop 'flame':
    'stream' builds the flame as tongues streaming off the breach edge (_stream_fire_material);
    anything else the round churning volume of the accepted Phaeton wreck."""
    if anchor.get('flame') == 'stream':
        return _build_stream_fire(ctx, coll, anchor)
    seed = _seed(anchor)
    r = units(float(anchor.get('size_m', FIRE_M)))
    fire = _container(f'{PREFIX} flame | {anchor.name}', coll, anchor, r*1.7,
                      _fire_material(ctx, f'{PREFIX} flame {seed}', seed, r))
    smoke = _container(f'{PREFIX} smoke | {anchor.name}', coll, anchor, r*3.2,
                       _smoke_material(ctx, f'{PREFIX} smoke {seed}', seed, r*3.0))
    smoke.location = (0, 0, r*.8)
    light = _flicker_light(ctx, f'{PREFIX} light | {anchor.name}', coll, anchor, 40000.0, seed)
    return {'anchor': anchor.name, 'radius_units': round(r, 2), 'objects': [fire.name, smoke.name, light.name]}


def _build_stream_fire(ctx, coll, anchor):
    from mathutils import Matrix as M
    seed = _seed(anchor)
    r = units(float(anchor.get('size_m', FIRE_M)))
    L, edge = r * 3.4, r * 1.6
    S, E = _stream_axes(anchor)
    turn = M((S, E, S.cross(E))).transposed().to_4x4()          # container +X along S, +Y along E
    fire = box_mesh(f'{PREFIX} flame | {anchor.name}', coll, (-r * .3, -(edge * .5 + r * .9), -r * .9),
                    (L + r * .4, edge * .5 + r * .9, r * .9),
                    _stream_fire_material(ctx, f'{PREFIX} stream flame {seed}', seed, r, L, edge))
    swfx_core.attach(fire, anchor)
    fire.matrix_basis = turn
    sl = L * 2.2
    smoke = box_mesh(f'{PREFIX} smoke | {anchor.name}', coll, (0.0, -r * 2.2, -r * 2.2), (sl, r * 2.2, r * 2.2),
                     _stream_smoke_material(ctx, f'{PREFIX} stream smoke {seed}', seed, r, sl))
    swfx_core.attach(smoke, anchor)
    smoke.matrix_basis = M.Translation(S * L * .45) @ turn
    light = _flicker_light(ctx, f'{PREFIX} light | {anchor.name}', coll, anchor, 40000.0, seed)
    light.location = S * r * .8
    return {'anchor': anchor.name, 'radius_units': round(r, 2), 'objects': [fire.name, smoke.name, light.name]}


def build_vent(ctx, coll, anchor):
    seed = _seed(anchor)
    L = units(float(anchor.get('length_m', VENT_M)))
    ob = _container(f'{PREFIX} vent | {anchor.name}', coll, anchor, L*.3,
                    _vent_material(ctx, f'{PREFIX} vent {seed}', seed, L), x0=-units(.5), x1=L)
    return {'anchor': anchor.name, 'length_units': round(L, 2), 'objects': [ob.name]}


def _ember_tree(ctx, name, seed, delay, count=120):
    tree, g, gout = _spark_group(name)
    atm = value_node(ctx, g.nodes, 'atmosphere', 'smk_atmosphere', ('smk_atmosphere',))
    t0 = value_node(ctx, g.nodes, 'start', f'fir_exp_frame+{delay*FPS}', ('fir_exp_frame',))
    on = value_node(ctx, g.nodes, 'on', 'fir_exp', ('fir_exp',))
    scale = value_node(ctx, g.nodes, 'scale', 'fir_exp_scale', ('fir_exp_scale',))
    idx = g.node('GeometryNodeInputIndex').outputs[0]
    T = g.node('GeometryNodeInputSceneTime').outputs['Frame']
    age = g.m('SUBTRACT', T, t0)
    life = g.m('MULTIPLY', _rnd(g, 'FLOAT', 1.2, 4.0, idx, seed+1), float(FPS))
    alive = g.m('MULTIPLY', g.m('MULTIPLY', g.m('GREATER_THAN', age, 0.0), g.m('LESS_THAN', age, life)),
                g.m('GREATER_THAN', on, .001))
    secs = g.m('DIVIDE', age, float(FPS))
    direction = g.v('NORMALIZE', _rnd(g, 'FLOAT_VECTOR', (-1, -1, -1), (1, 1, 1), idx, seed))
    speed = g.m('MULTIPLY', g.m('MULTIPLY', _rnd(g, 'FLOAT', units(25.0), units(140.0), idx, seed+2), scale),
                g.m('POWER', _rnd(g, 'FLOAT', 0.0, 1.0, idx, seed+3), .5))
    pos, vel = _ballistic(g, atm, direction, speed, g.m('MAXIMUM', secs, 0.0))
    heat = g.m('SUBTRACT', 1.0, g.m('DIVIDE', age, life), clamp=True)
    mat = _spark_material(ctx, knob='fir_exp', name=f'{PREFIX} ember', gain=6.0)
    _spark_streaks(g, gout, count, pos, vel, heat, alive, mat, width=20.0)
    return tree


def _jagged_blast(ctx, coll, anchor, seed, delay, flash_gain, light=False):
    """An explosion anchor as a cook-off cluster, in swfx_burst's language: the main burst thrown
    outward from the wreck's centre (its lobes and shrapnel leave the hull, they do not ring
    it), heavier shrapnel and a long torn smoke for a big blast, and two to four smaller
    satellite bursts popping round it over the next few frames, so a tank going reads as a
    chain of tearing blasts and not one shape. A lesser blast of the chain ('flash' below 1,
    or a small size_m) gets fewer satellites. Sized by size_m and fir_exp_scale; keyed from
    fir_exp_frame + delay_s; every piece carries 'swfx_burst_base' for jagged_cookoff's retime."""
    import random
    import swfx_burst
    import swfx_missile_paths as mp
    from mathutils import Vector
    ctl = ctx.controls
    if float(ctl.get('fir_exp', 1.0)) <= 0:
        return []
    S = float(anchor.get('size_m', BLAST_M)) * float(ctl.get('fir_exp_scale', 1.0))
    f = int(round(float(ctl.get('fir_exp_frame', 1.0)) + delay * FPS))
    mpu = swfx_core.METRES_PER_UNIT
    rnd = random.Random(f'swfx-fire-blast|{anchor.name}|{seed}')
    at = mp.anchor_matrix(anchor).translation
    out_dir = at.normalized() if at.length > 1e-6 else Vector((0, 0, 1))       # away from the wreck's centre
    big = S >= 18.0 and flash_gain >= .8
    made = swfx_burst.burst(ctx, coll, f'blast {anchor.name}', at, f, mpu, rnd, (1.0, .55, .22),
                            size_m=(S * .5, S * .72), axis=out_dir, lean=.8,
                            shrapnel=(12, 18) if big else (6, 10), smoke=.6 if big else .5,
                            smoke_life=(48, 72) if big else (28, 44), flash_frames=3 if big else 2,
                            parent=anchor, prefix=PREFIX, tear=True, smoke_drift=1.2)
    n = rnd.randint(3, 4) if big else rnd.randint(1, 2)
    for k in range(n):
        off = Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized() * S * rnd.uniform(.25, .5)
        here = at + off / mpu
        made += swfx_burst.burst(ctx, coll, f'blast {anchor.name} sat {k}', here, f + rnd.randint(1, 5), mpu, rnd,
                                 (1.0, .6, .25), size_m=(S * .14, S * .24),
                                 axis=(out_dir + off.normalized() * .6).normalized(), lean=.9, shrapnel=(2, 4),
                                 smoke=.4, smoke_life=(16, 28), flash_frames=2, parent=anchor, prefix=PREFIX,
                                 tear=True, smoke_drift=1.2)
    for ob in made:
        ob['swfx_burst_base'] = f
    return made


JAGGED_TAG = 'swfx_jagged_cookoff'


def jagged_cookoff(ctx):
    """Switch a ship's built explosions to bursts (owner rule, 2026-10-21: no orbs) on its own
    copy, inside its ship.kit(): each explosion anchor's volume fireball and shock ring are
    hidden and a jagged blast is keyed in their place at the copy's current fir_exp_frame
    (+ delay_s). Called again after break_frame moves, it re-times the blasts it made rather
    than making more. The flash light and the embers stay. Returns
    {'made': n, 'retimed': n, 'hidden': n, 'frame': fir_exp_frame}."""
    import swfx_burst
    bpy.context.view_layer.update()                     # fir_exp_frame may be driven (break_frame)
    ctl = ctx.controls
    T = float(ctl.get('fir_exp_frame', 1.0))
    coll = ctx.collection(GROUP)
    mine = lambda o: '@' not in o.name                  # inside kit(): this ship's copies carry the kit names
    out = dict(made=0, retimed=0, hidden=0, frame=T)
    have = {}
    for o in bpy.data.objects:
        if mine(o) and o.get(JAGGED_TAG):
            have.setdefault(o[JAGGED_TAG], []).append(o)
    for anchor in sorted((o for o in bpy.data.objects if o.type == 'EMPTY' and mine(o) and BLAST.search(o.name)
                          and not o.name.startswith('SWFX')), key=lambda o: o.name):
        delay = float(anchor.get('delay_s', 0.0))
        for what in ('fireball', 'shock ring'):
            ob = bpy.data.objects.get(f'{PREFIX} {what} | {anchor.name}')
            if ob is not None and not ob.hide_render:
                ob.hide_render = ob.hide_viewport = True
                out['hidden'] += 1
        f = int(round(T + delay * FPS))
        if anchor.name in have:
            if swfx_burst.retime(have[anchor.name], f):
                out['retimed'] += 1
            continue
        for ob in _jagged_blast(ctx, coll, anchor, _seed(anchor), delay, float(anchor.get('flash', 1.0)),
                                light=False):
            ob[JAGGED_TAG] = anchor.name
        out['made'] += 1
    return out


def build_explosion(ctx, coll, anchor, style=None):
    from swfx_lighting import watts
    seed = _seed(anchor)
    style = style or anchor.get('style') or STYLE
    delay = float(anchor.get('delay_s', 0.0))
    R = units(float(anchor.get('size_m', BLAST_M)))
    ring_gain = float(anchor.get('ring', 1.0))
    flash_gain = float(anchor.get('flash', 1.0))
    ember_count = int(anchor.get('embers', 120))
    if style == 'jagged':
        objects = [o.name for o in _jagged_blast(ctx, coll, anchor, seed, delay, flash_gain)]
        ring_gain = 0.0
    else:
        ball = _container(f'{PREFIX} fireball | {anchor.name}', coll, anchor, R*1.6,
                          _fireball_material(ctx, f'{PREFIX} fireball {seed}', seed, R, delay))
        objects = [ball.name]
    # Shock ring: a flat annulus in the anchor's YZ plane, racing out and fading.
    if ring_gain > 0:
        objects.append(_shock_ring(ctx, coll, anchor, seed, R, delay, ring_gain))
    te = f'((frame-fir_exp_frame)/{FPS}-{delay})'
    # Flash: very bright for a few frames, then the fireball's own glow.
    flash = swfx_core.point_light(f'{PREFIX} flash | {anchor.name}', coll, (1.0, .82, .6), 0.0, radius=R*.3)
    swfx_core.attach(flash, anchor)
    ctx.drive(flash.data, 'energy',
              f'fir_exp*({te}>0)*{watts(1.5e7)*flash_gain:.1f}*fir_exp_scale*fir_exp_scale*'
              f'(exp(-max({te},0)*9)+0.12*exp(-max({te},0)*1.2))', ('fir_exp', 'fir_exp_frame', 'fir_exp_scale'))
    objects.append(flash.name)
    if ember_count > 0:
        embers = bpy.data.objects.new(f'{PREFIX} embers | {anchor.name}',
                                      bpy.data.meshes.new(f'{PREFIX} embers {seed}'))
        coll.objects.link(embers)
        mod = embers.modifiers.new('Embers', 'NODES')
        mod.node_group = _ember_tree(ctx, f'{PREFIX} embers {seed}', seed, delay, ember_count)
        swfx_core.attach(embers, anchor)
        objects.append(embers.name)
    return {'anchor': anchor.name, 'delay_s': delay, 'style': style, 'fireball_radius_units': round(R, 2),
            'objects': objects}


def _shock_ring(ctx, coll, anchor, seed, R, delay, gain):
    bpy.ops.mesh.primitive_circle_add(vertices=64, radius=1.0, fill_type='NGON')
    ring = bpy.context.object
    for c in ring.users_collection:
        c.objects.unlink(ring)
    coll.objects.link(ring)
    ring.name = ring.data.name = f'{PREFIX} shock ring | {anchor.name}'
    ring.data.materials.append(_ring_material(ctx, f'{PREFIX} shock ring {seed}', delay, gain))
    _no_shadow_catch(ring)
    swfx_core.attach(ring, anchor, rotation=(0, math.pi/2, 0))
    te = f'((frame-fir_exp_frame)/{FPS}-{delay})'
    for k in range(3):
        ctx.drive(ring, 'scale', f'{R*2.5:.3f}*fir_exp_scale*(1-exp(-max({te},0)*2.2))+0.001',
                  ('fir_exp_scale', 'fir_exp_frame'), index=k)
    return ring.name


def build(ctx, fires=True, vents=True, explosions=True, **options):
    coll = ctx.collection(GROUP)
    _knobs(ctx)
    rec = {'state': ctx.state, 'fires': [], 'vents': [], 'explosions': []}
    named = sorted((o for o in bpy.data.objects if o.type == 'EMPTY' and not o.name.startswith('SWFX')),
                   key=lambda o: o.name)
    for anchor in named:
        if fires and FIRE.search(anchor.name):
            rec['fires'].append(build_fire(ctx, coll, anchor))
        elif vents and VENT.search(anchor.name):
            rec['vents'].append(build_vent(ctx, coll, anchor))
        elif explosions and BLAST.search(anchor.name):
            rec['explosions'].append(build_explosion(ctx, coll, anchor, options.get('blast_style')))
    ctx.controls.update_tag()
    bpy.context.view_layer.update()
    return rec
