"""Impacts: what a round does when it lands, the opt-in 'impacts' group (W5 of asharu-effects-plan.md).

Not in swfx.GROUPS, and nothing is built at import or by any apply: a shot places each impact
itself, at runtime, on the ship it hits. A Second Wind file is untouched.

    with ship.kit() as (ctx, kit):                       # ship_fx.ShipFx, one ship's own copy
        import swfx_impacts as imp
        imp.kinetic(ctx, frame, at_m, normal)            # a light MG round on armour
        imp.he(ctx, frame, at_m, normal, size_m=2.5)     # a light assault gun shell
        imp.breach(ctx, frame, at_m, normal)             # the hull opens: blast, fire, vent
        imp.debris(ctx, frame, at_m, normal)             # plate fragments tumbling clear
        imp.strike(ctx, frame, at_m, normal, 'kinetic')  # shield first if one is up (W1's knobs)

`at_m` and `normal` are in the ship's own frame, metres (+X bow, +Z dorsal); `ray_to_hull`
finds them on the hull from a shot's line. Each impact hangs from its own empty (the runtime
anchor a clean build lacks), placed in the ship's SWFX copy, so it rides with the ship. The
empty carries the impact's start frame (`imp_frame`), and every driver of that impact reads it
there, so any number of hits land at their own times without a knob each.

The looks, from asharu-effects.md:
  kinetic   a spray of white-yellow sparks thrown off the face, a tight jagged flash for two
            or three frames, and a small pock left glowing orange that cools to a dark scorch.
  he        an orange jagged burst thrown out of the face (lobed flash, shrapnel cooling
            fast, torn smoke that thins in vacuum), slow orange spall, a wider glowing crater.

Bursts, not orbs (owner, 2026-10-21): the flash and the HE blast are `swfx_burst`'s, leaning
along the surface normal. STYLE (or style= on kinetic/he/breach) 'orb' brings back the old
flash sphere and volume fireball as the control. A burst is keyed from the impact's frame at
build time and hangs from the impact's empty.
  breach    an HE blast at the hole, then the fire group's own flame, smoke and flickering
            light coming up over a second and a vent of pale vapour along the normal; timed by
            the breach's own empty, not by the build's fir_exp_frame.
  debris    tumbling plates (hot at first, dark metal after) and a spark burst, flying
            straight in vacuum: the break-up the blockout's grey beads stood in for.

Knobs, on 'SWFX controls', created by the first impact (so a ship without one has none):
  imp_sparks    spark and spall brightness (0 off)
  imp_flash     flash light and flash body strength
  imp_glow      pock and crater glow
  imp_glow_s    seconds a pock takes to cool to a dull red
  imp_debris    debris glow and spark brightness
Breaches also make the fire group's knobs (fir_fire, fir_smoke, fir_vent) and follow them.

Shields (W1) are reached through their knobs only: `strike` reads `shield_t`, `shield_facing`
and, if present, `shield_arc` and `shield_flux`, and keys the next free `shield_hit_<k>` with
its `_az_` / `_el_`. Up and in arc, the round lands on the shield. Late in the load
(`shield_flux` at or above LEAK_FLUX) it leaks: a dimmed shield flash and a smaller armour hit
behind it. Down or out of arc, it lands on the armour.

Everything is a pure function of the frame and the knobs; seeds come from the impact's index.
"""
import math
import zlib

import bpy
from mathutils import Matrix, Vector

import swfx_core
from swfx_core import node_material, units, value_node
from swfx_smoke import G, _ballistic, _sock, _no_shadow_catch, _rnd, _spark_group, _spark_material, _spark_streaks, box_mesh, volume_output

GROUP = 'Impacts'
PREFIX = 'SWFX impact'
STYLE = 'jagged'         # 'orb': the old flash sphere and volume fireball
FPS = swfx_core.FPS
LEAK_FLUX = .75
SHARD_SIDES = 7          # rim points of one debris shard before it is torn
# The kit draws a spark as a streak two frames of travel long and ~0.1 m thick: at a fast spark's
# speed that is a 10 m bar, which makes a 60-90 m hull read as a model. Impact sparks are drawn
# finer: half as thick and under half as long (refine_sparks).
SPARK_WIDTH, SPARK_STREAK = .5, .4
# kind -> look. Sizes in metres, times in seconds. Light power is real watts for a hull-unit
# file (swfx_lighting.watts); ShipFx.metre_scale corrects it once a copy is instanced in metres.
KINDS = {
    'kinetic': dict(sparks=50, spark_speed=(12.0, 55.0), spark_life=(.08, .3), spark_width=22.0, spark_gain=26.0,
                    spall=0, flash_m=.5, flash_s=.08, flash_W=2.5e5, fireball_m=0.0,
                    pock_m=.6, pock_gain=16.0),
    'he': dict(sparks=30, spark_speed=(20.0, 80.0), spark_life=(.08, .25), spark_width=22.0, spark_gain=22.0,
               spall=40, flash_m=1.4, flash_s=.14, flash_W=1.6e6, fireball_m=2.6,
               pock_m=1.4, pock_gain=12.0),
}

# ======================================================================================= helpers

def _knobs(ctx):
    ctx.prop('imp_sparks', 1.0, 0, 3, 'Impact spark and spall brightness')
    ctx.prop('imp_flash', 1.0, 0, 3, 'Impact flash light and body')
    ctx.prop('imp_glow', 1.0, 0, 3, 'Impact pock and crater glow')
    ctx.prop('imp_glow_s', 2.5, .2, 20, 'Seconds an impact pock takes to cool')
    ctx.prop('imp_debris', 1.0, 0, 3, 'Debris glow and spark brightness')


def _count(ctx):
    n = int(ctx.controls.get('imp_count', 0)) + 1
    ctx.controls['imp_count'] = n
    return n


def _frame_of(normal):
    """A rotation whose +Z is the normal (the surface's outward direction)."""
    z = Vector(normal).normalized()
    return z.to_track_quat('Z', 'X').to_matrix().to_4x4()


def anchor(ctx, kind, frame, at_m, normal, **props):
    """The impact's runtime empty: at at_m (ship frame, metres), +Z out of the surface, carrying
    imp_frame (its start) and any other props. Returns (empty, seed)."""
    _knobs(ctx)
    n = _count(ctx)
    coll = ctx.collection(GROUP)
    e = swfx_core.empty(f'{PREFIX} {kind} {n}', coll, size=units(.5), display='SINGLE_ARROW')
    e.matrix_world = Matrix.Translation(Vector(at_m) / swfx_core.METRES_PER_UNIT) @ _frame_of(normal)
    e['imp_frame'] = float(frame)
    for k, v in props.items():
        e[k] = float(v)
    e.hide_render = True
    seed = zlib.crc32(f'{kind}{n}'.encode()) % 997
    return e, seed


def _start(e):
    """Driver extra: the impact's own start frame, read off its empty."""
    return {'t0': (e, '["imp_frame"]')}


def _child(ob, e, local=(0, 0, 0), rotation=(0, 0, 0)):
    swfx_core.attach(ob, e, local, rotation)
    return ob


# ======================================================================================= pieces

def _sparks(ctx, e, seed, name, count, speed_m, life_s, width, knob, gain, cool, hot, spread=1.0, lift=1.25):
    """A burst of velocity-aligned streaks from the empty's origin, thrown out of the face (+Z)."""
    tree, g, gout = _spark_group(f'{name} {seed}')
    atm = value_node(ctx, g.nodes, 'atmosphere', 'smk_atmosphere', ('smk_atmosphere',)) \
        if 'smk_atmosphere' in ctx.controls.keys() else 0.0
    t0 = value_node(ctx, g.nodes, 'start', 't0', extra=_start(e))
    on = value_node(ctx, g.nodes, 'on', knob, (knob,))
    idx = g.node('GeometryNodeInputIndex').outputs[0]
    T = g.node('GeometryNodeInputSceneTime').outputs['Frame']
    age = g.m('SUBTRACT', T, t0)
    life = g.m('MULTIPLY', _rnd(g, 'FLOAT', life_s[0], life_s[1], idx, seed + 1), float(FPS))
    alive = g.m('MULTIPLY', g.m('MULTIPLY', g.m('GREATER_THAN', age, -.001), g.m('LESS_THAN', age, life)),
                g.m('GREATER_THAN', on, .001))
    secs = g.m('DIVIDE', g.m('MAXIMUM', age, 0.0), float(FPS))
    rv = g.v('SCALE', _rnd(g, 'FLOAT_VECTOR', (-1, -1, -1), (1, 1, 1), idx, seed), scale=spread)
    direction = g.v('NORMALIZE', g.v('ADD', rv, (0.0, 0.0, lift)))
    speed = g.m('MULTIPLY', _rnd(g, 'FLOAT', units(speed_m[0]), units(speed_m[1]), idx, seed + 2),
                g.m('POWER', _rnd(g, 'FLOAT', 0.0, 1.0, idx, seed + 3), .5))
    pos, vel = _ballistic(g, atm, direction, speed, secs)
    heat = g.m('SUBTRACT', 1.0, g.m('DIVIDE', g.m('MAXIMUM', age, 0.0), life), clamp=True)
    mat = _spark_material(ctx, knob=knob, name=f'{PREFIX} {name}', gain=gain, cool=cool, hot=hot)
    _spark_streaks(g, gout, count, pos, vel, heat, alive, mat, width=width)
    _refine(tree, SPARK_WIDTH, SPARK_STREAK)
    ob = bpy.data.objects.new(f'{PREFIX} {name} | {e.name}', bpy.data.meshes.new(f'{PREFIX} {name} {seed}'))
    ctx.collection(GROUP).objects.link(ob)
    ob.modifiers.new('Sparks', 'NODES').node_group = tree
    _no_shadow_catch(ob)
    return _child(ob, e)


def _burst(ctx, e, flash_rgb, size_m, **kw):
    """swfx_burst's jagged burst at the impact, thrown along its normal, hanging from its empty."""
    import random
    import swfx_burst
    import swfx_missile_paths as mp
    m = mp.anchor_matrix(e)
    return swfx_burst.burst(ctx, ctx.collection(GROUP), f'{e.name[len(PREFIX) + 1:]}', m.translation,
                            int(round(e['imp_frame'])), swfx_core.METRES_PER_UNIT, random.Random(f'swfx-impact|{e.name}'),
                            flash_rgb, size_m=size_m, axis=m.to_3x3() @ Vector((0, 0, 1)), parent=e,
                            prefix=PREFIX, **kw)


def _flash(ctx, e, seed, radius_m, decay_s, metre_watts, colour=(1.0, .86, .6), body=True, style=None):
    """The flash: a point light and a small white-hot body over the impact, gone in a few frames
    (held a touch longer than vacuum would, owner 2026-10-20). The body is a jagged burst flash
    (style 'jagged', the default) or the old soft sphere ('orb'); body=False: the light only."""
    from swfx_lighting import watts
    coll = ctx.collection(GROUP)
    te = f'((frame-t0)/{FPS})'
    env = f'imp_flash*({te}>=0)*exp(-max({te},0)/{decay_s:.3f})'
    light = swfx_core.point_light(f'{PREFIX} flash light | {e.name}', coll, colour, 0.0, radius=units(radius_m * .5))
    _child(light, e, (0, 0, units(radius_m * .6)))
    ctx.drive(light.data, 'energy', f'{watts(metre_watts):.1f}*{env}', ('imp_flash',), extra=_start(e))
    if not body:
        return [light]
    if (style or STYLE) == 'jagged':
        return [light] + _burst(ctx, e, colour, (radius_m * .8, radius_m * 1.2), shrapnel=None, smoke=0,
                                flash_frames=max(2, int(round(decay_s * FPS * 2))))
    m, nodes, links = node_material(f'{PREFIX} flash body {seed}')
    g = G(m.node_tree)
    strength = value_node(ctx, nodes, 'strength', f'{25.0:.1f}*{env}', ('imp_flash',), extra=_start(e))
    # Bright at the centre, falling off to the silhouette: a soft star rather than a ball.
    lw = g.node('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    core = g.m('POWER', g.m('SUBTRACT', 1.0, lw.outputs['Facing']), 3.0)
    em = g.node('ShaderNodeEmission')
    em.inputs['Color'].default_value = (*colour, 1)
    g._in(em.inputs['Strength'], g.m('MULTIPLY', strength, core))
    tr = g.node('ShaderNodeBsdfTransparent')
    add = g.node('ShaderNodeAddShader')
    links.new(em.outputs[0], add.inputs[0])
    links.new(tr.outputs[0], add.inputs[1])
    out = g.node('ShaderNodeOutputMaterial')
    links.new(add.outputs[0], out.inputs['Surface'])
    body = _sphere(f'{PREFIX} flash body | {e.name}', coll, units(radius_m), m)
    _child(body, e, (0, 0, units(radius_m * .35)))
    # Expands a little as it fades.
    for k in range(3):
        ctx.drive(body, 'scale', f'0.001+({te}>=0)*(0.6+0.8*(1-exp(-max({te},0)/{decay_s:.3f})))',
                  extra=_start(e), index=k)
    return [light, body]


def _sphere(name, coll, radius, mat):
    me = bpy.data.meshes.new(name)
    import bmesh
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=radius)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    _no_shadow_catch(ob)
    ob.visible_shadow = False
    return ob


def _fireball(ctx, e, seed, radius_m):
    """A small HE fireball: white-orange for a few frames, dark smoke by a second, thinned out by
    three. Faster than the fire group's ship-killing blasts: a shell, not a tank."""
    m, nodes, links = node_material(f'{PREFIX} fireball {seed}')
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    te = f'((frame-t0)/{FPS})'
    t = value_node(ctx, nodes, 'since', f'max({te},0)', extra=_start(e))
    on = value_node(ctx, nodes, 'on', f'imp_flash*({te}>=0)', ('imp_flash',), extra=_start(e))
    R = units(radius_m)
    grow = g.m('SUBTRACT', 1.0, g.m('EXPONENT', g.m('MULTIPLY', t, -9.0)))
    r_now = g.m('MULTIPLY', R, g.m('MULTIPLY_ADD', grow, .9, .1))
    d = g.m('DIVIDE', g.v('LENGTH', co), r_now)
    n = g.noise(co, g.m('MULTIPLY_ADD', t, 1.4, seed * 2.3), 2.6 / R, detail=4.0, rough=.6, distortion=.4)
    shell = g.m('SUBTRACT', 1.0, g.mr(g.m('MULTIPLY_ADD', n, -.55, d), .2, .8))
    heat = g.m('EXPONENT', g.m('MULTIPLY', t, -5.0))
    fade = g.m('EXPONENT', g.m('MULTIPLY', t, -1.3))
    dens = g.m('MULTIPLY', g.m('MULTIPLY', g.m('MULTIPLY', shell, on), fade), 1.6 / units(1.0))
    temp = g.m('MULTIPLY_ADD', g.m('MULTIPLY', heat, g.mr(n, .2, .8, .75, 1.0)), 4200.0, 1100.0)
    em = g.m('MULTIPLY', g.m('MULTIPLY', g.m('MULTIPLY', shell, on), g.m('POWER', heat, 2.5)), 22.0 / units(1.0))
    vol = volume_output(g, dens, (0.05, 0.045, 0.04, 1), (0.16, 0.16, 0.17, 1), .2, emission=em)
    bb = g.node('ShaderNodeBlackbody')
    g._in(bb.inputs['Temperature'], temp)
    g.links.new(bb.outputs[0], vol.inputs['Emission Color'])
    h = R * 1.7
    ob = box_mesh(f'{PREFIX} fireball | {e.name}', ctx.collection(GROUP), (-h, -h, -h * .4), (h, h, h * 1.6), m)
    return _child(ob, e, (0, 0, R * .4))


def _pock(ctx, e, seed, radius_m, gain, scorch=.85):
    """The mark the round leaves: a glowing pock on the plate that cools from orange to a dull red
    over imp_glow_s and is left as a dark scorch."""
    m, nodes, links = node_material(f'{PREFIX} pock {seed}')
    g = G(m.node_tree)
    te = f'((frame-t0)/{FPS})'
    on = value_node(ctx, nodes, 'on', f'({te}>=0)', extra=_start(e))
    cool = value_node(ctx, nodes, 'cool', f'exp(-max({te},0)/imp_glow_s)', ('imp_glow_s',), extra=_start(e))
    glow = value_node(ctx, nodes, 'glow', 'imp_glow', ('imp_glow',))
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    r = g.m('DIVIDE', g.v('LENGTH', co), units(radius_m))
    n = g.noise(co, float(seed), 3.0 / units(radius_m), detail=3.0, rough=.6)
    rr = g.m('MULTIPLY_ADD', n, .5, g.m('SUBTRACT', r, .25))          # ragged edge
    hot = g.m('MULTIPLY', g.m('SUBTRACT', 1.0, g.mr(rr, 0.0, .55, smooth=True)), on)
    mark = g.m('MULTIPLY', g.m('SUBTRACT', 1.0, g.mr(rr, .35, 1.0, smooth=True)), on)
    temp = g.m('MULTIPLY_ADD', cool, 1500.0, 850.0)
    bb = g.node('ShaderNodeBlackbody')
    g._in(bb.inputs['Temperature'], temp)
    em = g.node('ShaderNodeEmission')
    g.links.new(bb.outputs[0], em.inputs['Color'])
    g._in(em.inputs['Strength'], g.m('MULTIPLY', g.m('MULTIPLY', g.m('MULTIPLY', hot, glow),
                                                     g.m('POWER', cool, 1.6)), gain))
    soot = g.node('ShaderNodeBsdfDiffuse')
    soot.inputs['Color'].default_value = (.012, .011, .01, 1)
    tr = g.node('ShaderNodeBsdfTransparent')
    mix = g.node('ShaderNodeMixShader')
    g._in(mix.inputs[0], g.m('MULTIPLY', mark, scorch))
    links.new(tr.outputs[0], mix.inputs[1])
    links.new(soot.outputs[0], mix.inputs[2])
    add = g.node('ShaderNodeAddShader')
    links.new(mix.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    out = g.node('ShaderNodeOutputMaterial')
    links.new(add.outputs[0], out.inputs['Surface'])
    bpy.ops.mesh.primitive_circle_add(vertices=20, radius=units(radius_m * 1.3), fill_type='NGON')
    ob = bpy.context.object
    for c in ob.users_collection:
        c.objects.unlink(ob)
    ctx.collection(GROUP).objects.link(ob)
    ob.name = ob.data.name = f'{PREFIX} pock | {e.name}'
    ob.data.materials.append(m)
    ob.visible_shadow = False
    _no_shadow_catch(ob)
    return _child(ob, e, (0, 0, units(.04)))


# ======================================================================================= impacts

def kinetic(ctx, frame, at_m, normal, scale=1.0, style=None):
    """A kinetic round on armour: sparks, a tight flash, a glowing pock. Returns its record."""
    k = KINDS['kinetic']
    e, seed = anchor(ctx, 'kinetic', frame, at_m, normal)
    obs = [_sparks(ctx, e, seed, 'kinetic spark', k['sparks'], [v * scale for v in k['spark_speed']], k['spark_life'],
                   k['spark_width'] * scale ** .5, 'imp_sparks', k['spark_gain'], (1.0, .5, .12, 1), (1.0, .92, .7, 1))]
    obs += _flash(ctx, e, seed, k['flash_m'] * scale, k['flash_s'], k['flash_W'] * scale ** 2, style=style)
    obs.append(_pock(ctx, e, seed, k['pock_m'] * scale, k['pock_gain']))
    return _record('kinetic', frame, at_m, e, obs)


def he(ctx, frame, at_m, normal, size_m=None, scale=1.0, flash_colour=(1.0, .62, .3), style=None, tear=True):
    """A high-explosive shell on armour: an orange flash and short fireball, slow spall, sparks,
    a wider glowing crater. size_m sets the fireball radius (default KINDS['he']); flash_colour
    tints the flash and its light (a Harpoon warhead's is the game's blue, 100,165,255). tear: the
    jagged flash is whole for one frame and then tears into tongues (False: the old whole flash
    held four frames, kept for the bench's before/after)."""
    k = KINDS['he']
    s = (size_m / k['fireball_m'] if size_m else 1.0) * scale
    e, seed = anchor(ctx, 'he', frame, at_m, normal)
    obs = [_sparks(ctx, e, seed, 'he spark', k['sparks'], [v * s ** .5 for v in k['spark_speed']], k['spark_life'],
                   k['spark_width'], 'imp_sparks', k['spark_gain'], (1.0, .5, .12, 1), (1.0, .9, .65, 1)),
           _sparks(ctx, e, seed + 11, 'he spall', k['spall'], (6.0 * s ** .5, 24.0 * s ** .5), (.4, 1.2),
                   26.0 * s ** .5, 'imp_sparks', 10.0, (.6, .12, .02, 1), (1.0, .55, .16, 1), spread=1.2, lift=1.0)]
    if (style or STYLE) == 'jagged':
        # One burst: the jagged flash, shrapnel cooling fast and torn smoke, all thrown out of
        # the face; the flash light stays the kit's own.
        R = k['fireball_m'] * s
        # Torn: the light dies with the white frame (0.14 s kept the smoke lit cream-white for half
        # a second, the blob that held, bench_he 2026-10-23) and the smoke flies off the blast.
        obs += _flash(ctx, e, seed, k['flash_m'] * s, k['flash_s'] * (.4 if tear else 1.0), k['flash_W'] * s ** 2,
                      colour=flash_colour, body=False)
        obs += _burst(ctx, e, flash_colour, (R * .55, R * .8), lean=1.2, shrapnel=(3, 6), smoke=.6,
                      smoke_life=(14, 22), flash_frames=5 if tear else 4, tear=tear, smoke_drift=1.2 if tear else 0.0)
    else:
        obs += _flash(ctx, e, seed, k['flash_m'] * s, k['flash_s'], k['flash_W'] * s ** 2, colour=flash_colour,
                      style='orb')
        obs.append(_fireball(ctx, e, seed, k['fireball_m'] * s))
    obs.append(_pock(ctx, e, seed, k['pock_m'] * s, k['pock_gain']))
    return _record('he', frame, at_m, e, obs)


# The Hellbore's 750-damage HE slug (hitGlowRadius 150, against a light assault gun's 40 damage):
# the HE hit at about two and a half times the size, in its own orange, its burst a jagged lobed
# flash thrown out of the face like every HE hit's (owner, 2026-10-23: no orbs).
HELLBORE = dict(size_m=6.5, flash_colour=(1.0, .5, .18))


def hellbore(ctx, frame, at_m, normal, scale=1.0, style=None, **kw):
    """A Hellbore slug on armour: he() at the Hellbore's size and colour. Returns its record."""
    spec = dict(HELLBORE, **kw)
    return he(ctx, frame, at_m, normal, scale=scale, style=style, **spec)


def breach(ctx, frame, at_m, normal, size_m=3.0, vent=True, vent_m=24.0, ignite_s=.15, blast_m=None, style=None,
           flame=None):
    """The hull opens at at_m on `frame`: an HE blast in the hole, then the fire group's flame,
    smoke and flickering light coming up over a second (from ignite_s after the blast), and,
    with vent, a cone of pale vapour along the normal. The fire group's knobs (fir_fire,
    fir_smoke, fir_vent) scale it; its timing is the breach's own (imp_frame, imp_ignite).
    flame='stream': the flame streams off the breach edge (swfx_fire.build_fire), not a churning ball."""
    import swfx_fire
    rec = he(ctx, frame, at_m, normal, size_m=blast_m or size_m * 1.1, style=style)
    e, seed = anchor(ctx, 'breach', frame, at_m, normal, size_m=size_m, imp_ignite=ignite_s, length_m=vent_m)
    if flame:
        e['flame'] = flame
    swfx_fire._knobs(ctx)
    coll = ctx.collection(GROUP)
    fire = swfx_fire.build_fire(ctx, coll, e)
    made = [bpy.data.objects[n] for n in fire['objects']]
    if vent:
        v = swfx_core.empty(f'{PREFIX} breach vent {seed}', coll, size=units(.5))
        _child(v, e, (0, 0, units(.2)), (0, -math.pi / 2, 0))     # vent +X along the normal
        v['length_m'] = float(vent_m)
        v['seed'] = float(seed)
        v.hide_render = True
        made += [bpy.data.objects[n] for n in swfx_fire.build_vent(ctx, coll, v)['objects']]
    n = _retime(made, e)
    rec['objects'] += [o.name for o in made]
    rec['breach'] = dict(anchor=e.name, size_m=size_m, vent=vent, drivers_retimed=n)
    return rec


def _retime(objects, e):
    """Point the fire group's clock (fir_exp_frame, fir_ignite) at this breach's own empty."""
    to = {'fir_exp_frame': '["imp_frame"]', 'fir_ignite': '["imp_ignite"]'}
    n = 0
    owners = []
    for o in objects:
        owners += [o, o.data]
        owners += [s.material for s in o.material_slots if s.material is not None]
    for idb in owners:
        for own in (idb, getattr(idb, 'node_tree', None)):
            ad = getattr(own, 'animation_data', None) if own is not None else None
            for fc in (ad.drivers if ad else ()):
                for var in fc.driver.variables:
                    if var.name in to:
                        var.targets[0].id = e
                        var.targets[0].data_path = to[var.name]
                        n += 1
    return n


def debris(ctx, frame, at_m, normal, count=60, speed_m=(4.0, 38.0), size_m=(.6, 3.2), spread_m=4.0, sparks=120):
    """Plate fragments blown out of at_m on `frame` and tumbling clear, hot for a second then dark
    metal under the key light, with a spark burst. They fly straight (vacuum)."""
    e, seed = anchor(ctx, 'debris', frame, at_m, normal)
    tree, g, gout = _spark_group(f'{PREFIX} debris {seed}')
    t0 = value_node(ctx, g.nodes, 'start', 't0', extra=_start(e))
    idx = g.node('GeometryNodeInputIndex').outputs[0]
    T = g.node('GeometryNodeInputSceneTime').outputs['Frame']
    age = g.m('SUBTRACT', T, t0)
    secs = g.m('DIVIDE', g.m('MAXIMUM', age, 0.0), float(FPS))
    rv = _rnd(g, 'FLOAT_VECTOR', (-1, -1, -1), (1, 1, 1), idx, seed)
    direction = g.v('NORMALIZE', g.v('ADD', rv, (0.0, 0.0, .9)))
    speed = _rnd(g, 'FLOAT', units(speed_m[0]), units(speed_m[1]), idx, seed + 2)
    p0 = g.v('SCALE', _rnd(g, 'FLOAT_VECTOR', (-1, -1, -.2), (1, 1, .4), idx, seed + 5), scale=units(spread_m * .3))
    pos = g.v('ADD', p0, g.v('SCALE', direction, scale=g.m('MULTIPLY', speed, secs)))
    pts = g.node('GeometryNodePoints')
    pts.inputs['Count'].default_value = count
    g.links.new(pos, pts.inputs['Position'])
    heat = g.m('EXPONENT', g.m('MULTIPLY', secs, -3.0))
    # Tumble: a random attitude turning at a random rate about a random axis.
    spin = g.v('ADD', g.v('SCALE', _rnd(g, 'FLOAT_VECTOR', (0, 0, 0), (1, 1, 1), idx, seed + 6), scale=6.283),
               g.v('SCALE', _rnd(g, 'FLOAT_VECTOR', (-5, -5, -5), (5, 5, 5), idx, seed + 7), scale=secs))
    big = _rnd(g, 'FLOAT', units(size_m[0]), units(size_m[1]), idx, seed + 8)
    shape = g.v('SCALE', _rnd(g, 'FLOAT_VECTOR', (.6, .35, .05), (1.0, .8, .1), idx, seed + 9), scale=big)
    geo = pts.outputs[0]
    for name, kind, value in (('swfx_heat', 'FLOAT', heat), ('swfx_id', 'FLOAT', idx), ('swfx_p0', 'FLOAT_VECTOR', pos),
                              ('swfx_spin', 'FLOAT_VECTOR', spin), ('swfx_shape', 'FLOAT_VECTOR', shape)):
        st = g.node('GeometryNodeStoreNamedAttribute', data_type=kind, domain='POINT')
        g.links.new(geo, st.inputs['Geometry'])
        st.inputs['Name'].default_value = name
        g.links.new(value, _sock(st, 'Value'))
        geo = st.outputs[0]
    dele = g.node('GeometryNodeDeleteGeometry', domain='POINT')
    g.links.new(geo, dele.inputs['Geometry'])
    g.links.new(g.m('LESS_THAN', age, 0.0), dele.inputs['Selection'])
    # Each fragment is its own torn shard, not a shared box: a thin SHARD_SIDES-gon whose rim
    # points are pulled in or pushed out by their own random amounts (wedges, slivers, ragged
    # plates), shaped after realizing so no two pieces match, then sized and turned.
    plate = g.node('GeometryNodeMeshCylinder', fill_type='NGON')
    plate.inputs['Vertices'].default_value = SHARD_SIDES
    plate.inputs['Radius'].default_value = .5
    plate.inputs['Depth'].default_value = 1.0
    inst = g.node('GeometryNodeInstanceOnPoints')
    g.links.new(dele.outputs[0], inst.inputs['Points'])
    g.links.new(plate.outputs['Mesh'], inst.inputs['Instance'])
    real = g.node('GeometryNodeRealizeInstances')
    g.links.new(inst.outputs[0], real.inputs['Geometry'])

    def named(attr, kind):
        n = g.node('GeometryNodeInputNamedAttribute', data_type=kind)
        n.inputs['Name'].default_value = attr
        return _sock(n, 'Attribute', out=True)

    vi = g.node('GeometryNodeInputIndex').outputs[0]
    rim = g.m('ADD', g.m('MULTIPLY', named('swfx_id', 'FLOAT'), float(SHARD_SIDES)),
              g.m('FLOORED_MODULO', vi, float(SHARD_SIDES)))
    p0 = named('swfx_p0', 'FLOAT_VECTOR')
    local = g.v('SUBTRACT', g.node('GeometryNodeInputPosition').outputs[0], p0)
    lx, ly, lz = g.sep(local)
    reach = _rnd(g, 'FLOAT', .25, 1.15, rim, seed + 10)            # this rim point's distance out
    slide = _rnd(g, 'FLOAT_VECTOR', (-.12, -.12, 0), (.12, .12, 0), rim, seed + 11)
    thick = _rnd(g, 'FLOAT', .55, 1.4, vi, seed + 12)               # uneven plate thickness
    torn = g.v('ADD', g.comb(g.m('MULTIPLY', lx, reach), g.m('MULTIPLY', ly, reach), g.m('MULTIPLY', lz, thick)), slide)
    sized = g.v('MULTIPLY', torn, named('swfx_shape', 'FLOAT_VECTOR'))
    turn = g.node('ShaderNodeVectorRotate', rotation_type='EULER_XYZ')
    g.links.new(sized, turn.inputs['Vector'])
    g.links.new(named('swfx_spin', 'FLOAT_VECTOR'), turn.inputs['Rotation'])
    setp = g.node('GeometryNodeSetPosition')
    g.links.new(real.outputs[0], setp.inputs['Geometry'])
    g.links.new(g.v('ADD', p0, turn.outputs['Vector']), setp.inputs['Position'])
    real = setp
    sm = g.node('GeometryNodeSetMaterial')
    g.links.new(real.outputs[0], sm.inputs['Geometry'])
    sm.inputs['Material'].default_value = _debris_material(ctx)
    g.links.new(sm.outputs[0], gout.inputs[0])
    ob = bpy.data.objects.new(f'{PREFIX} debris | {e.name}', bpy.data.meshes.new(f'{PREFIX} debris {seed}'))
    ctx.collection(GROUP).objects.link(ob)
    ob.modifiers.new('Debris', 'NODES').node_group = tree
    _child(ob, e)
    obs = [ob]
    if sparks:
        obs.append(_sparks(ctx, e, seed + 3, 'debris spark', sparks, (10.0, 50.0), (.15, .6), 26.0, 'imp_debris',
                           24.0, (1.0, .45, .1, 1), (1.0, .9, .65, 1), spread=1.0, lift=.8))
    return _record('debris', frame, at_m, e, obs)


def _debris_material(ctx):
    name = f'{PREFIX} debris plate'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    heat = g.node('ShaderNodeAttribute', attribute_name='swfx_heat', attribute_type='GEOMETRY').outputs['Fac']
    bsdf = g.node('ShaderNodeBsdfPrincipled')
    bsdf.inputs['Base Color'].default_value = (.06, .058, .055, 1)
    bsdf.inputs['Metallic'].default_value = .6
    bsdf.inputs['Roughness'].default_value = .55
    bb = g.node('ShaderNodeBlackbody')
    g._in(bb.inputs['Temperature'], g.m('MULTIPLY_ADD', heat, 900.0, 800.0))
    g.links.new(bb.outputs[0], bsdf.inputs['Emission Color'])
    amt = value_node(ctx, nodes, 'amount', 'imp_debris', ('imp_debris',))
    g._in(bsdf.inputs['Emission Strength'], g.m('MULTIPLY', g.m('MULTIPLY', g.m('POWER', heat, 2.0), 3.0), amt))
    out = g.node('ShaderNodeOutputMaterial')
    links.new(bsdf.outputs[0], out.inputs['Surface'])
    return m


def _record(kind, frame, at_m, e, obs):
    return dict(kind=kind, frame=frame, at_m=[round(v, 2) for v in at_m], anchor=e.name,
                objects=[o.name for o in obs])


# ======================================================================================= streams and shields

def stream(ctx, kind, first, last, every, at0_m, at1_m, normal, jitter_m=.6, seed=0, aim=None, matrix=None, **kw):
    """A burst walking across the plate: one `kind` hit every `every` frames (a float: the cadence
    need not be whole frames) from first to last, from at0_m to at1_m with a little scatter.
    With aim=((from0_m, toward0_m), (from1_m, toward1_m)) and the ship's matrix, each round is
    put where its line meets the hull instead (ray_to_hull), so the stream follows the plating.
    Returns the records."""
    import random
    rnd = random.Random(seed)
    n = int((last - first) / max(every, .5)) + 1
    out = []
    for i in range(n):
        f = round(first + i * every)
        u = i / max(1, n - 1)
        j = Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1))) * jitter_m
        if aim is not None:
            (o0, t0), (o1, t1) = aim
            got = ray_to_hull(ctx.scene, matrix, tuple(Vector(o0).lerp(Vector(o1), u)),
                              tuple(Vector(t0).lerp(Vector(t1), u) + j))
            if got is None:
                continue
            p, nrm = got
        else:
            p, nrm = tuple(Vector(at0_m).lerp(Vector(at1_m), u) + j), normal
        out.append((kinetic if kind == 'kinetic' else he)(ctx, f, p, nrm, **kw))
    return out


def _knob_at(ctl, name, frame):
    """A knob's value on `frame`: its F-curve if keyed, else its value."""
    if name not in ctl.keys():
        return None
    fc = swfx_core.find_fcurve(ctl, f'["{name}"]')
    return float(fc.evaluate(frame)) if fc else float(ctl[name])


def shield_slots(ctl):
    return sorted(int(k.rsplit('_', 1)[1]) for k in ctl.keys()
                  if k.startswith('shield_hit_') and k.rsplit('_', 1)[1].isdigit() and '_az_' not in k and '_el_' not in k)


def strike(ctx, frame, at_m, normal, kind='kinetic', shield_centre_m=(0, 0, 0), arc=None, decay_s=.4, **kw):
    """One round arriving at at_m. With a shield up (W1's shield_t) and the hit in its arc, it lands
    on the shield: the next free shield_hit_<k> keyed from the hit's direction. Late in the flux
    load (shield_flux >= LEAK_FLUX) it leaks through, a dimmed flash and a smaller armour hit.
    Otherwise it hits the armour. Returns 'shield', 'leak' or 'armour'."""
    ctl = ctx.controls
    up = _knob_at(ctl, 'shield_t', frame) or 0.0
    slots = shield_slots(ctl)
    d = Vector(at_m) - Vector(shield_centre_m)
    az = math.degrees(math.atan2(d.y, d.x))
    el = math.degrees(math.atan2(d.z, max(1e-6, math.hypot(d.x, d.y))))
    facing = _knob_at(ctl, 'shield_facing', frame) or 0.0
    arc = float(arc if arc is not None else (_knob_at(ctl, 'shield_arc', frame) or 360.0))
    in_arc = abs((az - facing + 180.0) % 360.0 - 180.0) <= up * arc / 2
    hit = {'kinetic': kinetic, 'hellbore': hellbore}.get(kind, he)
    if not slots or up < .5 or not in_arc:
        hit(ctx, frame, at_m, normal, **kw)
        return 'armour'
    flux = _knob_at(ctl, 'shield_flux', frame) or 0.0
    leak = flux >= LEAK_FLUX
    k = slots[int(ctl.get('imp_shield_next', 0)) % len(slots)]
    ctl['imp_shield_next'] = int(ctl.get('imp_shield_next', 0)) + 1
    gone = frame + max(1, round(decay_s * FPS))
    ctx.key(f'shield_hit_az_{k}', frame, az, 'CONSTANT')
    ctx.key(f'shield_hit_el_{k}', frame, el, 'CONSTANT')
    for f, v in ((frame - 1, 0.0), (frame, .5 if leak else 1.0), (gone, 0.0)):
        ctx.key(f'shield_hit_{k}', f, v, 'LINEAR')
    if leak:
        hit(ctx, frame, at_m, normal, scale=.6, **kw)
        return 'leak'
    return 'shield'


# ======================================================================================= spark scale

def _refine(tree, width, streak, count=1.0):
    """Scale one spark-streak tree (swfx_smoke._spark_streaks): streak thickness by width, streak
    length (frames of travel) by streak, spark count by count. Idempotent per tree. Returns True
    when the tree is a streak tree."""
    if tree.get('imp_refined'):
        return True
    length = thick = None
    for n in tree.nodes:
        if n.bl_idname != 'ShaderNodeMath' or n.operation != 'MULTIPLY_ADD':
            continue
        if abs(n.inputs[1].default_value - 2.0 / FPS) < 1e-6 and n.inputs[0].is_linked:
            length = n
        elif any(l.to_node.bl_idname == 'ShaderNodeCombineXYZ' and l.to_socket.identifier != 'X'
                 for l in n.outputs[0].links):
            thick = n
    if length is None or thick is None:
        return False
    length.inputs[1].default_value *= streak
    thick.inputs[1].default_value *= width
    thick.inputs[2].default_value *= width
    if count != 1.0:
        for n in tree.nodes:
            if n.bl_idname == 'GeometryNodePoints' and not n.inputs['Count'].is_linked:
                n.inputs['Count'].default_value = max(1, round(n.inputs['Count'].default_value * count))
    tree['imp_refined'] = True
    return True


def refine_sparks(ctx, width=.4, streak=.4, count=2.0, prefix='SWFX fire embers'):
    """Draw a build's baked spark streaks finer, on this ship's copy only (a wreck's cook-off
    embers by default): thinner, shorter, and more of them, so a ship-killing blast throws sparks
    at the scale of the ship rather than of a model. Opt-in; returns how many trees it scaled."""
    n = 0
    for o in ctx.root.all_objects:
        if not o.name.startswith(prefix):
            continue
        for mod in o.modifiers:
            if mod.type == 'NODES' and mod.node_group is not None and _refine(mod.node_group, width, streak, count):
                n += 1
    return n


# ======================================================================================= placing

def ray_to_hull(scene, matrix, origin_m, toward_m, ignore=()):
    """Where a round from origin_m toward toward_m (both in the ship's frame, metres) first meets
    geometry, and that surface's normal, back in the ship's frame: (at_m, normal) or None.
    matrix is the ship's world matrix (metres, as ShipFx.matrix)."""
    dg = bpy.context.evaluated_depsgraph_get()
    o = matrix @ Vector(origin_m)
    d = (matrix @ Vector(toward_m) - o).normalized()
    for _ in range(64):            # skip the kit's own volume boxes (a wreck is full of them)
        ok, loc, nrm, _i, ob, _m = scene.ray_cast(dg, o, d)
        if not ok:
            return None
        if ob is not None and (ob.name.startswith('SWFX') or any(base in ob.name for base in ignore)):
            o = loc + d * .05
            continue
        inv = matrix.inverted()
        n = (inv.to_3x3() @ nrm).normalized()
        if n.dot(inv.to_3x3() @ d) > 0:
            n = -n
        return tuple(inv @ loc), tuple(n)
    return None


def apply_cue(ctx, entries, frame_now=None, matrix=None):
    """Impacts from cue data (for battle_blockout's per-frame cues): a list of dicts, each
    {'kind': 'kinetic'|'he'|'breach'|'debris'|'stream', 'at': (x, y, z) m, 'normal': (x, y, z),
     'frame': F or 'ago_s': seconds before frame_now, ...the function's other arguments}.
    A 'stream' takes 'hit' (kinetic|he), 'to' (the far end) or 'aim' (two lines, see stream),
    'every' (frames) and 'over_s'. Any other entry may give 'aim': (from_m, toward_m) instead of
    'at' and 'normal': the hit is put where that line meets the hull (ray_to_hull, with matrix
    the ship's world matrix)."""
    out = []
    fns = {'kinetic': kinetic, 'he': he, 'hellbore': hellbore, 'breach': breach, 'debris': debris}
    for c in entries:
        c = dict(c)
        kind = c.pop('kind')
        f = c.pop('frame', None)
        if 'ago_s' in c:
            f = frame_now - round(c.pop('ago_s') * FPS)
        if 'aim' in c and kind != 'stream':
            got = ray_to_hull(ctx.scene, matrix, *c.pop('aim'))
            if got is None:
                continue
            c['at'], c['normal'] = got
        if kind == 'stream':
            n = round(c.pop('over_s') * FPS)
            out += stream(ctx, c.pop('hit', 'kinetic'), f, f + n, c.pop('every', 2), c.pop('at', None),
                          c.pop('to', None), c.pop('normal', (0, 0, 1)), matrix=matrix, **c)
            continue
        at, nrm = c.pop('at'), c.pop('normal', (0, 0, 1))
        out.append(fns[kind](ctx, f, at, nrm, **c))
    return out
