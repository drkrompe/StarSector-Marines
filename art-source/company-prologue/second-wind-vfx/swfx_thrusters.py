"""Second Wind thrusters: state-aware main-drive plumes and RCS puffs.

Per running engine, parented to its engine anchor (local +X aft, origin at
the bell mouth centre):

  core    a short, pale, blue-white ionised emission volume at the mouth
  plume   a long transparent mixing volume, mostly emission at low density,
          widening, tapering and fading with distance, 4D noise advected aft
  glow    an emissive disc inside the bell, so the bell reads lit from inside
  light   a point light just aft of the mouth for the can face, pod and hull

All four follow the effective throttle thr_throttle * thr_throttle_e{i}. It
drives an emission/density envelope and the plume length: the plume container
scales along local X from its origin at the mouth, so the attachment never
moves. The core shrinks last (only below a quarter throttle).

Engine modes (swfx_core.STATES):
  clean     steady, even blue-white
  salvaged  the bolted-on coil on engines 3/4: a seeded per-engine flicker
            (a few per cent of wobble plus occasional brief dips, scaled by
            thr_instability), warmer and less even colour, a dirtier plume with
            grey-brown particulate and faint soot puffs. Never fire.
  capped / dead  nothing at all: no volume, glow or light.

RCS: eight cold-gas quads at hull corners (art placement, not canon: the
vanilla hull defines no manoeuvring thrusters). Each is a small nozzle stub on
the nearest ship part with a cone volume; rcs_puff() keys a brief, fast,
expanding, quickly fading white puff. None when the state is unpowered.

Everything is procedural and driven by `frame` through simple-expression
drivers (no Python driver evaluation, no caches), so a file re-renders
identically.
"""
import math
import re
import random
import shutil
from pathlib import Path

import bpy
from mathutils import Vector

import swfx_core
from swfx_core import math_node

GROUP = 'Thrusters'
PREFIX = 'SWFX thrusters'
HERE = Path(__file__).resolve().parent

# Base container lengths in units of the bell radius (thr_plume_length = 1).
PLUME_LEN = 11.0
CORE_LEN = 2.6

COLOURS = {
    'clean': dict(core=(.62, .78, 1.0), plume=(.28, .46, 1.0), warm=(.40, .55, 1.0),
                  glow=(.72, .84, 1.0), light=(.70, .80, 1.0)),
    'salvaged': dict(core=(.80, .82, 1.0), plume=(.42, .50, .95), warm=(1.0, .50, .25),
                     glow=(1.0, .86, .78), light=(.95, .84, .80)),
}

# RCS sizes, from physical metres so a ship rescale is one edit in swfx_core:
# a ~0.25 m cold-gas nozzle, a puff reaching ~4 m before it is gone.
RCS_NOZZLE_R = swfx_core.units(.25)
RCS_PUFF_LEN = swfx_core.units(4.0)

# RCS quads: name -> (hull-local seed position, outward thrust direction).
# Positions are pushed onto the nearest ship surface at build time.
RCS_QUADS = {
    'bow_port':       ((50, 18, 4), (0, 1, 0)),
    'bow_starboard':  ((50, -18, 4), (0, -1, 0)),
    'stern_port':     ((-30, 38, 4), (0, 1, 0)),
    'stern_starboard': ((-30, -38, 4), (0, -1, 0)),
    'dorsal_fore':    ((35, 0, 30), (0, 0, 1)),
    'dorsal_aft':     ((-20, 0, 30), (0, 0, 1)),
    'ventral_fore':   ((35, 0, -12), (0, 0, -1)),
    'ventral_aft':    ((-20, 0, -12), (0, 0, -1)),
}


# --- expressions -----------------------------------------------------------
def eff_expr(i):
    return f'max(0.0, min(1.0, thr_throttle * thr_throttle_e{i}))'


def flicker_expr(i, seed=0):
    """Seeded (wobble, dip) simple expressions of frame for a salvaged engine:
    wobble in 0..1 (a few % of intensity), dip 0..1 occasional brief drops."""
    rnd = random.Random(7919 * i + seed)
    w = [rnd.uniform(a, b) for a, b in ((.9, 1.4), (2.1, 3.1), (4.3, 5.9), (.13, .19), (.31, .43))]
    p = [rnd.uniform(0, 2 * math.pi) for _ in range(5)]
    wob = (f'0.5 + 0.25 * sin(frame * {w[0]:.4f} + {p[0]:.3f}) + 0.15 * sin(frame * {w[1]:.4f} + {p[1]:.3f})'
           f' + 0.10 * sin(frame * {w[2]:.4f} + {p[2]:.3f})')
    dip = (f'min(1.0, max(0.0, sin(frame * {w[3]:.4f} + {p[3]:.3f}) * sin(frame * {w[4]:.4f} + {p[4]:.3f}) - 0.72) * 3.6)')
    return wob, dip


SIG = ('eff', 'flick', 'dip')


def _sig_vars(sig, names=SIG):
    return {n: (sig, f'["{n}"]') for n in names}


def _assert_simple(fc):
    if hasattr(fc.driver, 'is_simple_expression') and not fc.driver.is_simple_expression:
        raise RuntimeError(f'driver is not a simple expression: {fc.driver.expression}')
    return fc


# --- shader helpers -----------------------------------------------------------
def _vec_math(nodes, links, op, a, b):
    n = nodes.new('ShaderNodeVectorMath')
    n.operation = op
    for idx, v in enumerate((a, b)):
        if hasattr(v, 'node'):
            links.new(v, n.inputs[idx])
        else:
            n.inputs[idx].default_value = v
    return n.outputs[0]


def _smooth(nodes, links, value, lo, hi):
    n = nodes.new('ShaderNodeMapRange')
    n.interpolation_type = 'SMOOTHSTEP'
    n.inputs['From Min'].default_value = lo
    n.inputs['From Max'].default_value = hi
    links.new(value, n.inputs['Value'])
    return n.outputs[0]


def _mix_rgb(nodes, links, fac, a, b):
    n = nodes.new('ShaderNodeMix')
    n.data_type = 'RGBA'
    links.new(fac, n.inputs[0]) if hasattr(fac, 'node') else setattr(n.inputs[0], 'default_value', fac)
    n.inputs[6].default_value = (*a, 1)
    n.inputs[7].default_value = (*b, 1)
    return n.outputs[2]


def _driven_value(ctx, nodes, name, expr, props=(), extra=None):
    n = nodes.new('ShaderNodeValue')
    n.name = n.label = name
    _assert_simple(ctx.drive(n.outputs[0], 'default_value', expr, props, extra=extra))
    return n.outputs[0]


def _noise(nodes, links, ctx, vec, scale, w_expr, detail=3.0, rough=.6):
    n = nodes.new('ShaderNodeTexNoise')
    n.noise_dimensions = '4D'
    n.inputs['Scale'].default_value = scale
    n.inputs['Detail'].default_value = detail
    n.inputs['Roughness'].default_value = rough
    links.new(vec, n.inputs['Vector'])
    fc = n.inputs['W'].driver_add('default_value')
    fc.driver.type = 'SCRIPTED'
    fc.driver.expression = w_expr
    _assert_simple(fc)
    return n.outputs['Fac']


def _radial(nodes, links, sep, radius_of_x):
    """rho^2 = (y^2+z^2)/R(x)^2 for a socket R(x)."""
    y2 = math_node(nodes, links, 'MULTIPLY', sep.outputs['Y'], sep.outputs['Y'])
    z2 = math_node(nodes, links, 'MULTIPLY', sep.outputs['Z'], sep.outputs['Z'])
    r2 = math_node(nodes, links, 'MULTIPLY', radius_of_x, radius_of_x)
    return math_node(nodes, links, 'DIVIDE', math_node(nodes, links, 'ADD', y2, z2), r2)


def _jet_material(ctx, i, mode, r, kind, env_expr, sig):
    """Core or plume volume for engine i. Object coordinates are hull units in
    the container's own frame: x from the mouth aft, so stretching the
    container stretches the field with it."""
    col = COLOURS[mode]
    salv = mode == 'salvaged'
    m, nodes, links = swfx_core.node_material(f'{PREFIX} {kind} e{i}')
    out = nodes.new('ShaderNodeOutputMaterial')
    tex = nodes.new('ShaderNodeTexCoord')
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(tex.outputs['Object'], sep.inputs[0])
    x = sep.outputs['X']
    env = _driven_value(ctx, nodes, 'envelope', env_expr, ['thr_brightness'], _sig_vars(sig))
    seed = 13.7 * i
    if kind == 'core':
        L = CORE_LEN * r
        u = math_node(nodes, links, 'DIVIDE', x, L)
        # Narrowing core: full bell-throat width at the mouth, a point at L.
        R = math_node(nodes, links, 'MULTIPLY',
                      math_node(nodes, links, 'SUBTRACT', 1.0, math_node(nodes, links, 'MULTIPLY', u, .5)), .5 * r)
        R = math_node(nodes, links, 'MAXIMUM', R, .05 * r)
        rho2 = _radial(nodes, links, sep, R)
        radial = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MAXIMUM',
                           math_node(nodes, links, 'SUBTRACT', 1.0, rho2), 0.0), 2.0)
        # Brightest just aft of the mouth, dying away smoothly (no hard cone tip).
        axial = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MAXIMUM',
                          math_node(nodes, links, 'SUBTRACT', 1.0, math_node(nodes, links, 'MULTIPLY', u, u)), 0.0), 3.0)
        inside = _smooth(nodes, links, x, -.45 * r, .05 * r)
        field = math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', radial, axial), inside)
        n = _noise(nodes, links, ctx, tex.outputs['Object'], 1.2 / r, f'frame * 0.21 + {seed:.2f}', 2.0)
        field = math_node(nodes, links, 'MULTIPLY', field,
                          math_node(nodes, links, 'ADD', .8, math_node(nodes, links, 'MULTIPLY', n, .4)))
        field = math_node(nodes, links, 'MULTIPLY', field, env)
        vol = nodes.new('ShaderNodeVolumePrincipled')
        vol.inputs['Density'].default_value = 0.0
        vol.inputs['Emission Color'].default_value = (*col['core'], 1)
        links.new(math_node(nodes, links, 'MULTIPLY', field, 9.0 / (r / 2.6)), vol.inputs['Emission Strength'])
        links.new(vol.outputs[0], out.inputs['Volume'])
        return m

    # plume
    L = PLUME_LEN * r
    # Advect the noise aft at a fixed speed so turbulence streams out of the bell.
    adv = _vec_math(nodes, links, 'SUBTRACT', tex.outputs['Object'],
                    (0, 0, 0))
    fc = adv.node.inputs[1].driver_add('default_value', 0)
    fc.driver.type = 'SCRIPTED'
    fc.driver.expression = f'frame * {.9 * r:.3f}'
    _assert_simple(fc)
    u = math_node(nodes, links, 'DIVIDE', x, L)
    R = math_node(nodes, links, 'MULTIPLY',
                  math_node(nodes, links, 'ADD', .78, math_node(nodes, links, 'MULTIPLY', u, 1.25)), r)
    rho2 = _radial(nodes, links, sep, R)
    radial = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MAXIMUM',
                       math_node(nodes, links, 'SUBTRACT', 1.0, rho2), 0.0), 2.4)
    taper = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MAXIMUM',
                      math_node(nodes, links, 'SUBTRACT', 1.0, u), 0.0), 1.8)
    decay = math_node(nodes, links, 'EXPONENT', math_node(nodes, links, 'MULTIPLY', u, -3.4))
    inside = _smooth(nodes, links, x, -.3 * r, .35 * r)
    shape = math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', radial, taper),
                      math_node(nodes, links, 'MULTIPLY', decay, inside))
    turb = _noise(nodes, links, ctx, adv, .8 / r, f'frame * 0.035 + {seed:.2f}', 5.0, .66)
    # Turbulence grows downstream: even at the mouth, ragged at the tail.
    # Salvaged plumes are lumpier; a clean drive stays even.
    tz = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MULTIPLY', turb, 1.85), 3.0 if salv else 1.6)
    tmix = math_node(nodes, links, 'MINIMUM', math_node(nodes, links, 'MULTIPLY', u, 2.2), 1.0)
    rag = math_node(nodes, links, 'ADD', math_node(nodes, links, 'MULTIPLY', tz, tmix),
                    math_node(nodes, links, 'SUBTRACT', 1.0, tmix))
    field = math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', shape, rag), env)
    vol = nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Anisotropy'].default_value = .3
    if salv:
        warmn = _noise(nodes, links, ctx, adv, .35 / r, f'frame * 0.05 + {seed + 3:.2f}', 2.0)
        warm = _smooth(nodes, links, warmn, .45, .75)
        warm = math_node(nodes, links, 'MULTIPLY', warm, math_node(nodes, links, 'MINIMUM',
                         math_node(nodes, links, 'MULTIPLY', u, 3.0), 1.0))
        links.new(_mix_rgb(nodes, links, math_node(nodes, links, 'MULTIPLY', warm, .85), col['plume'], col['warm']),
                  vol.inputs['Emission Color'])
        # Grey-brown particulate: steady haze plus advecting soot puffs that
        # swell on the coil's dips.
        dip = _driven_value(ctx, nodes, 'dip', 'thr_instability * dip', ['thr_instability'], _sig_vars(sig, ('dip',)))
        sootn = _noise(nodes, links, ctx, adv, .28 / r, f'frame * 0.02 + {seed + 9:.2f}', 3.0, .55)
        puffs = math_node(nodes, links, 'POWER', _smooth(nodes, links, sootn, .52, .78), 1.5)
        soot_amt = math_node(nodes, links, 'ADD', .25, math_node(nodes, links, 'MULTIPLY', dip, 2.5))
        widen = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MAXIMUM',
                          math_node(nodes, links, 'SUBTRACT', 1.0, math_node(nodes, links, 'MULTIPLY', rho2, .55)), 0.0), 1.2)
        soot = math_node(nodes, links, 'MULTIPLY',
                         math_node(nodes, links, 'MULTIPLY', puffs, soot_amt),
                         math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', widen, taper),
                                   math_node(nodes, links, 'MULTIPLY', inside, tmix)))
        thr = _driven_value(ctx, nodes, 'throttle', 'eff', (), _sig_vars(sig, ('eff',)))
        soot = math_node(nodes, links, 'MULTIPLY', soot, thr)
        links.new(math_node(nodes, links, 'ADD', math_node(nodes, links, 'MULTIPLY', field, .006 / r * 2.6),
                            math_node(nodes, links, 'MULTIPLY', soot, .16 / r * 2.6)), vol.inputs['Density'])
        vol.inputs['Color'].default_value = (.30, .26, .22, 1)
        vol.inputs['Absorption Color'].default_value = (.10, .08, .06, 1)
    else:
        vol.inputs['Emission Color'].default_value = (*col['plume'], 1)
        links.new(math_node(nodes, links, 'MULTIPLY', field, .003 / r * 2.6), vol.inputs['Density'])
        vol.inputs['Color'].default_value = (.8, .85, .95, 1)
    links.new(math_node(nodes, links, 'MULTIPLY', field, .55 / (r / 2.6)), vol.inputs['Emission Strength'])
    links.new(vol.outputs[0], out.inputs['Volume'])
    return m


def _glow_material(ctx, i, mode, env_expr, sig):
    col = COLOURS[mode]['glow']
    m, nodes, links = swfx_core.node_material(f'{PREFIX} bell glow e{i}')
    out = nodes.new('ShaderNodeOutputMaterial')
    tex = nodes.new('ShaderNodeTexCoord')
    # Disc mesh UV-free: radial falloff from object coordinates (unit disc).
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(tex.outputs['Object'], sep.inputs[0])
    rho2 = _radial(nodes, links, sep, 1.0)
    core = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MAXIMUM',
                     math_node(nodes, links, 'SUBTRACT', 1.0, rho2), 0.0), 1.5)
    env = _driven_value(ctx, nodes, 'envelope', env_expr, (), _sig_vars(sig))
    em = nodes.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (*col, 1)
    links.new(math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', core, env), 30.0),
              em.inputs['Strength'])
    tr = nodes.new('ShaderNodeBsdfTransparent')
    mix = nodes.new('ShaderNodeMixShader')
    links.new(math_node(nodes, links, 'MINIMUM', math_node(nodes, links, 'MULTIPLY', core, 3.0), 1.0), mix.inputs[0])
    links.new(tr.outputs[0], mix.inputs[1])
    links.new(em.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], out.inputs['Surface'])
    return m


def _disc(name, coll, radius, material, segments=32):
    """A unit disc in the anchor's YZ plane, scaled to radius."""
    verts = [(0, 0, 0)] + [(0, math.cos(a), math.sin(a)) for a in
                           (2 * math.pi * k / segments for k in range(segments))]
    faces = [(0, 1 + k, 1 + (k + 1) % segments) for k in range(segments)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    me.materials.append(material)
    ob.scale = (1, radius, radius)
    ob.visible_shadow = False
    return ob


def _make_props(ctx):
    ctx.prop('thr_throttle', 1.0, 0, 1, 'Master drive throttle (shared)')
    for i in range(1, 5):
        ctx.prop(f'thr_throttle_e{i}', 1.0, 0, 1, f'Engine {i} throttle (shared)')
    ctx.prop('thr_instability', .5, 0, 1, 'Salvaged coil flicker/sputter depth')
    ctx.prop('thr_plume_length', 1.0, 0, 3, 'Plume length multiplier')
    ctx.prop('thr_glow', 1.0, 0, 4, 'Engine glow light energy multiplier')
    ctx.prop('thr_brightness', 1.0, 0, 4, 'Plume/core emission multiplier')


# --- build ---------------------------------------------------------------------
def build_engine(ctx, coll, anchor, mode):
    i, r = anchor.index, anchor.radius
    salv = mode == 'salvaged'
    # Per-engine signals (driven custom props): effective throttle, flicker
    # multiplier and dip. Everything else reads these, which keeps each
    # driver expression short and simple (Blender caps them at 256 chars).
    sig = swfx_core.empty(f'{PREFIX} signals e{i}', coll, .5, 'PLAIN_AXES')
    swfx_core.attach(sig, anchor.empty)
    sig.hide_render = True
    for n in (*SIG, 'wob'):
        sig[n] = 1.0 if n == 'flick' else 0.0
    _assert_simple(ctx.drive(sig, '["eff"]', eff_expr(i), ['thr_throttle', f'thr_throttle_e{i}']))
    if salv:
        wob, dip = flicker_expr(i)
        _assert_simple(ctx.drive(sig, '["wob"]', wob))
        _assert_simple(ctx.drive(sig, '["dip"]', dip))
        _assert_simple(ctx.drive(sig, '["flick"]', '1.0 - thr_instability * (0.14 * wob + 0.36 * dip)',
                                 ['thr_instability'], extra=_sig_vars(sig, ('dip',)) | {'wob': (sig, '["wob"]')}))
    else:
        sig['flick'] = 1.0
    env = 'thr_brightness * pow(eff, 1.3) * flick'
    made = {'signals': sig.name}

    core = swfx_core.box_volume(f'{PREFIX} core e{i}', coll, -.5 * r, CORE_LEN * r, .62 * r, .62 * r,
                                _jet_material(ctx, i, mode, r, 'core', env, sig), pad=swfx_core.engine_pad(i))
    swfx_core.attach(core, anchor.empty)
    # Cores shrink last: full size down to a quarter throttle.
    for ax in range(3):
        _assert_simple(ctx.drive(core, 'scale', '0.3 + 0.7 * min(1.0, 4.0 * eff)', (),
                                 index=ax, extra=_sig_vars(sig, ('eff',))))
    made['core'] = core.name

    plume = swfx_core.box_volume(f'{PREFIX} plume e{i}', coll, -.35 * r, PLUME_LEN * r, 2.1 * r, 2.1 * r,
                                 _jet_material(ctx, i, mode, r, 'plume', env, sig), pad=swfx_core.engine_pad(i))
    swfx_core.attach(plume, anchor.empty)
    _assert_simple(ctx.drive(plume, 'scale', 'thr_plume_length * (0.15 + 0.85 * eff)', ['thr_plume_length'],
                             index=0, extra=_sig_vars(sig, ('eff',))))
    made['plume'] = plume.name

    glow = _disc(f'{PREFIX} bell glow e{i}', coll, .66 * r,
                 _glow_material(ctx, i, mode, 'pow(eff, 0.8) * flick', sig))
    swfx_core.attach(glow, anchor.empty, local=(-2.25, 0, 0))
    glow.scale = (1, .66 * r, .66 * r)
    made['glow'] = glow.name

    light = swfx_core.point_light(f'{PREFIX} light e{i}', coll, COLOURS[mode]['light'], 0.0, radius=.5 * r)
    swfx_core.attach(light, anchor.empty, local=(.9 * r, 0, 0))
    peak = 900.0 * (r / 2.6) ** 2
    _assert_simple(ctx.drive(light.data, 'energy', f'{peak:.1f} * thr_glow * eff * flick', ['thr_glow'],
                             extra=_sig_vars(sig, ('eff', 'flick'))))
    made['light'] = light.name
    return made


def _ship_parts():
    return [o for o in bpy.data.objects if o.type == 'MESH' and not o.name.startswith('SWFX')
            and not any(c.name.startswith(swfx_core.ROOT) for c in o.users_collection)
            and len(o.data.polygons) > 0]


def _nearest_surface(parts, point):
    best = None
    for ob in parts:
        inv = ob.matrix_world.inverted()
        try:
            ok, loc, nor, _ = ob.closest_point_on_mesh(inv @ Vector(point))
        except RuntimeError:  # a part whose modifiers evaluate to nothing
            continue
        if not ok:
            continue
        w = ob.matrix_world @ loc
        d = (w - Vector(point)).length
        if best is None or d < best[0]:
            best = (d, ob, w)
    return best


def _drive_age(ctx, owner, path, quad, expression, index=-1):
    """Driver reading the quad's puff-age knob as variable 'age'."""
    fc = owner.driver_add(path, index) if index >= 0 else owner.driver_add(path)
    d = fc.driver
    d.type = 'SCRIPTED'
    v = d.variables.new()
    v.name = 'age'
    v.type = 'SINGLE_PROP'
    v.targets[0].id = ctx.controls
    v.targets[0].data_path = f'["thr_rcs_{quad}"]'
    d.expression = expression
    return _assert_simple(fc)


def _rcs_material(ctx, quad):
    m, nodes, links = swfx_core.node_material(f'{PREFIX} rcs {quad}')
    out = nodes.new('ShaderNodeOutputMaterial')
    tex = nodes.new('ShaderNodeTexCoord')
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(tex.outputs['Object'], sep.inputs[0])
    x = sep.outputs['X']
    L = RCS_PUFF_LEN
    u = math_node(nodes, links, 'DIVIDE', x, L)
    R = math_node(nodes, links, 'ADD', .65 * RCS_NOZZLE_R, math_node(nodes, links, 'MULTIPLY', x, .55))
    rho2 = _radial(nodes, links, sep, R)
    radial = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MAXIMUM',
                       math_node(nodes, links, 'SUBTRACT', 1.0, rho2), 0.0), 1.5)
    taper = math_node(nodes, links, 'MAXIMUM', math_node(nodes, links, 'SUBTRACT', 1.0, u), 0.0)
    inside = _smooth(nodes, links, x, 0.0, .8 * RCS_NOZZLE_R)
    n = _noise(nodes, links, ctx, tex.outputs['Object'], 5.4 / L, f'frame * 0.3 + {sum(map(ord, quad)) % 97:.1f}', 3.0, .6)
    rag = math_node(nodes, links, 'POWER', math_node(nodes, links, 'MULTIPLY', n, 1.8), 2.0)
    envn = nodes.new('ShaderNodeValue')
    envn.name = envn.label = 'puff'
    _drive_age(ctx, envn.outputs[0], 'default_value', quad,
               'min(1.0, age * 400.0) * (1.0 - age) * (1.0 - age) * (1.0 - age)')
    env = envn.outputs[0]
    field = math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', radial, taper),
                      math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', inside, rag), env))
    # Readability gain: a real cold-gas puff in a shadowed quarter is all but
    # invisible; shots that need it seen raise thr_rcs_gain.
    ctx.prop('thr_rcs_gain', 1.0, 0, 20, 'RCS puff density and self-light multiplier (readability)')
    gain = swfx_core.value_node(ctx, nodes, 'rcs gain', 'thr_rcs_gain', ('thr_rcs_gain',))
    field = math_node(nodes, links, 'MULTIPLY', field, gain)
    vol = nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Color'].default_value = (.92, .94, .97, 1)
    vol.inputs['Anisotropy'].default_value = .4
    links.new(math_node(nodes, links, 'MULTIPLY', field, 1.2), vol.inputs['Density'])
    # A trace of self-light so a puff in an unlit quarter still reads at all.
    vol.inputs['Emission Color'].default_value = (.8, .85, .95, 1)
    links.new(math_node(nodes, links, 'MULTIPLY', field, .06), vol.inputs['Emission Strength'])
    links.new(vol.outputs[0], out.inputs['Volume'])
    return m


GEOMETRY_RCS = re.compile(r'^RCS (.+) anchor(\.\d+)?$')


def rcs_key(label):
    """Knob-safe key for an RCS label: 'Bow port Y' -> 'bow_port_y'."""
    return re.sub(r'[^a-z0-9]+', '_', label.lower()).strip('_')


def geometry_rcs():
    """RCS mouths the geometry build declares: empties named
    'RCS <cluster> <axis> anchor', local +X along the exhaust. When any
    exist they replace the kit's art-placed quads. Anchors whose 'working'
    prop is false are skipped."""
    found = []
    for ob in bpy.data.objects:
        m = GEOMETRY_RCS.match(ob.name)
        # 'working' (per state, set by the geometry build): a dead thruster
        # gets no puff.
        if m and bool(ob.get('working', True)):
            found.append((rcs_key(m.group(1)), ob))
    return sorted(found, key=lambda t: t[0])


def _rcs_puff_on(ctx, coll, key, anchor, offset):
    ctx.prop(f'thr_rcs_{key}', 0.0, 0, 1, f'RCS {key} puff age (0 idle, rising 0->1 over a puff)')
    vol = swfx_core.box_volume(f'{PREFIX} rcs puff {key}', coll, -.1, RCS_PUFF_LEN, .6 * RCS_PUFF_LEN, .6 * RCS_PUFF_LEN,
                               _rcs_material(ctx, key))
    swfx_core.attach(vol, anchor, local=(offset, 0, 0))
    for ax in range(3):
        _drive_age(ctx, vol, 'scale', key, '0.35 + 1.9 * pow(age, 0.5)', index=ax)
    return vol


def build_rcs(ctx, coll):
    declared = geometry_rcs()
    if declared:
        made = {}
        for key, anchor in declared:
            _rcs_puff_on(ctx, coll, key, anchor, 0.0)
            made[key] = dict(anchor=anchor.name, source='geometry',
                             at=[round(v, 2) for v in anchor.matrix_world.translation],
                             axis=[round(v, 3) for v in anchor.matrix_world.to_3x3().col[0].normalized()])
        return made
    parts = _ship_parts()
    dark = bpy.data.materials.get(f'{PREFIX} rcs nozzle') or bpy.data.materials.new(f'{PREFIX} rcs nozzle')
    dark.diffuse_color = (.05, .05, .05, 1)
    dark.use_nodes = True
    bsdf = dark.node_tree.nodes.get('Principled BSDF')
    if bsdf:
        bsdf.inputs['Base Color'].default_value = (.04, .04, .045, 1)
        bsdf.inputs['Metallic'].default_value = .8
        bsdf.inputs['Roughness'].default_value = .45
    made = {}
    for quad, (seed, direction) in RCS_QUADS.items():
        hit = _nearest_surface(parts, seed)
        if hit is None:
            continue
        _, part, at = hit
        d = Vector(direction).normalized()
        pos = at + d * .4 * RCS_NOZZLE_R
        anchor = swfx_core.empty(f'{PREFIX} rcs {quad}', coll, 1.0)
        from swfx_anchors import frame as axis_frame
        world = Vector(pos)
        mw = axis_frame(d).to_4x4()
        mw.translation = world
        anchor.parent = part
        anchor.matrix_parent_inverse = part.matrix_world.inverted()
        anchor.matrix_basis = mw
        # Nozzle stub: a short dark cylinder along the thrust axis.
        bpy.ops.mesh.primitive_cylinder_add(vertices=12, radius=RCS_NOZZLE_R, depth=1.8 * RCS_NOZZLE_R)
        stub = bpy.context.object
        for c in stub.users_collection:
            c.objects.unlink(stub)
        coll.objects.link(stub)
        stub.name = f'{PREFIX} rcs nozzle {quad}'
        stub.data.materials.append(dark)
        swfx_core.attach(stub, anchor, local=(-.5 * RCS_NOZZLE_R, 0, 0), rotation=(0, math.pi / 2, 0))
        _rcs_puff_on(ctx, coll, quad, anchor, .5 * RCS_NOZZLE_R)
        made[quad] = dict(part=part.name, source='art placement', at=[round(v, 2) for v in pos], axis=list(direction))
    return made


def _refresh_props(*obs):
    """Custom props added to an object the depsgraph has already evaluated are
    missing from its evaluated copy until it is tagged, and a driver reading
    one in that window fails and is flagged invalid for good. Tag, then
    re-evaluate before any driver is made."""
    for ob in obs:
        ob.update_tag()
    bpy.context.view_layer.update()


def _revalidate(coll):
    """Clear any invalid flag left on this group's drivers, then re-evaluate."""
    owners = set()
    for ob in coll.objects:
        owners.add(ob)
        if ob.data is not None:
            owners.add(ob.data)
        for slot in getattr(ob, 'material_slots', []):
            if slot.material:
                owners.add(slot.material.node_tree)
    bad = 0
    for o in owners:
        ad = getattr(o, 'animation_data', None)
        for fc in (ad.drivers if ad else []):
            fc.driver.is_valid = True
    bpy.context.view_layer.update()
    for o in owners:
        ad = getattr(o, 'animation_data', None)
        bad += sum(not fc.driver.is_valid for fc in (ad.drivers if ad else []))
    return bad


def build(ctx, **options):
    _make_props(ctx)
    if ctx.spec['powered'] and options.get('rcs', True):
        for quad in RCS_QUADS:
            ctx.prop(f'thr_rcs_{quad}', 0.0, 0, 1, f'RCS {quad} puff age (0 idle, rising 0->1 over a puff)')
    _refresh_props(ctx.controls)
    coll = ctx.collection(GROUP)
    rec = dict(engines={}, rcs={}, note='RCS quad positions are art placement, not canon.')
    for anchor in ctx.anchors.engines:
        mode = ctx.engine_mode(anchor.index)
        if mode in ('dead', 'capped'):
            rec['engines'][anchor.index] = dict(mode=mode)
            continue
        made = build_engine(ctx, coll, anchor, mode)
        rec['engines'][anchor.index] = dict(mode=mode, radius=anchor.radius, **made)
    if ctx.spec['powered'] and options.get('rcs', True):
        rec['rcs'] = build_rcs(ctx, coll)
    rec['invalid_drivers'] = _revalidate(coll)
    if rec['invalid_drivers']:
        print(f'[swfx thrusters] WARNING {rec["invalid_drivers"]} invalid drivers')
    return rec


# --- shot helpers ------------------------------------------------------------
def set_throttle(ctx, frame, value, engines=None, interpolation=None):
    """Key throttle at frame: the master knob, or the listed engines' own."""
    names = ['thr_throttle'] if engines is None else [f'thr_throttle_e{i}' for i in engines]
    for n in names:
        ctx.prop(n, 1.0, 0, 1)
        ctx.key(n, frame, value, interpolation)


def ignition(ctx, start_frame, ramp_frames=18, level=1.0, engines=None):
    """Cold to `level` over ramp_frames: dark until start, a quick catch, then
    an eased climb."""
    set_throttle(ctx, start_frame, 0.0, engines, 'BEZIER')
    set_throttle(ctx, start_frame + max(1, round(ramp_frames * .25)), .3 * level, engines, 'BEZIER')
    set_throttle(ctx, start_frame + ramp_frames, level, engines, 'BEZIER')


def shutdown(ctx, start_frame, ramp_frames=12, from_level=1.0, engines=None):
    set_throttle(ctx, start_frame, from_level, engines, 'BEZIER')
    set_throttle(ctx, start_frame + ramp_frames, 0.0, engines, 'BEZIER')


def rcs_puff(ctx, quad, frame, duration=8):
    """A brief cold-gas puff from one RCS quad (see RCS_QUADS)."""
    name = f'thr_rcs_{quad}'
    if name not in ctx.controls.keys():
        return
    ctx.key(name, frame - 1, 0.0, 'CONSTANT')
    ctx.key(name, frame, 0.0001, 'LINEAR')
    ctx.key(name, frame + duration, 1.0, 'CONSTANT')
    ctx.key(name, frame + duration + 1, 0.0, 'CONSTANT')


# Which geometry RCS anchors (rcs_key labels) make each move, as published
# by the geometry session with the drive/RCS detail. A non-working anchor
# has no knob, so the move simply uses whatever the state still has (in
# patched/refit stern-port is missing, so yaw-port is bow-stbd alone).
MANOEUVRES = {
    'roll-starboard-down': ('pod_port_down', 'pod_stbd_up'),
    'roll-port-down': ('pod_port_up', 'pod_stbd_down'),
    'yaw-starboard': ('bow_port_port', 'stern_stbd_stbd'),
    'yaw-port': ('bow_stbd_stbd', 'stern_port_port'),
    'pitch-up': ('ventral_down_1', 'ventral_down_2', 'dorsal_up_1', 'dorsal_up_2', 'stern_port_up', 'stern_stbd_up'),
    'pitch-down': ('bow_port_up', 'bow_stbd_up', 'stern_port_down', 'stern_stbd_down'),
}


def manoeuvre(ctx, move, start_frame, end_frame, pulse_frames=6, gap_frames=5):
    """Puff the RCS thrusters that make `move` (a MANOEUVRES key, e.g.
    'roll-starboard-down' for C1's bank) between start and end. Returns the
    thrusters actually used; empty on a build without geometry RCS."""
    used = [q for q in MANOEUVRES[move] if f'thr_rcs_{q}' in ctx.controls.keys()]
    rcs_pulses(ctx, used, start_frame, end_frame, pulse_frames, gap_frames)
    return used


def rcs_pulses(ctx, quads, start_frame, end_frame, pulse_frames=6, gap_frames=5):
    """A sustained manoeuvre (a bank or a heading correction) as a train of
    short puffs from each named quad between start and end, staggered so the
    quads do not pulse in lockstep. Quads are rcs_key labels (geometry RCS
    anchors) or the art-placed RCS_QUADS names; unknown ones are ignored."""
    period = pulse_frames + gap_frames
    for k, quad in enumerate(quads):
        f = start_frame + (k * 2) % period
        while f + pulse_frames <= end_frame:
            rcs_puff(ctx, quad, f, pulse_frames)
            f += period


# --- review ------------------------------------------------------------------
def _review_scene(ctx, quick):
    scene = ctx.scene
    swfx_core.hide_reference_lights(scene)
    swfx_core.dark_world(scene, (.0015, .0017, .002), 1.0)
    swfx_core.setup_render(scene, samples=16 if quick else 96)
    scene.cycles.volume_step_rate = 4.0 if quick else 2.0
    scene.cycles.volume_bounces = 0
    scene.cycles.max_bounces = 4
    rc = ctx.collection(f'{GROUP} review')
    key = bpy.data.objects.get(f'{PREFIX} review key')
    if key is None:
        data = bpy.data.lights.new(f'{PREFIX} review key', 'SUN')
        data.energy = 2.2
        data.color = (1.0, .98, .95)
        data.angle = math.radians(1.0)
        key = bpy.data.objects.new(f'{PREFIX} review key', data)
        rc.objects.link(key)
        key.rotation_euler = (Vector((-.35, .55, .75))).normalized().to_track_quat('Z', 'Y').to_euler()
    return rc


def _camera(rc, name, loc, target, lens):
    cam = bpy.data.objects.get(name) or swfx_core.look_camera(name, rc, loc, target, lens)
    return cam


def _render(scene, cam, path):
    scene.camera = cam
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def review(ctx, out_dir, quick=False):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    final = None if quick else HERE / 'review' / 'thrusters'
    if final:
        final.mkdir(parents=True, exist_ok=True)
    scene = ctx.scene
    rc = _review_scene(ctx, quick)
    cam = _camera(rc, f'{PREFIX} review stern quarter', (-122, -88, 34), (-44, -8, -2), 32)
    sfx = '-quick' if quick else ''
    scene.frame_set(1)

    def keep(src, name):
        if final:
            shutil.copyfile(src, final / name)

    if ctx.state in ('restored', 'clean'):
        p = out_dir / f'restored-stern-quarter{sfx}.png'
        _render(scene, cam, p)
        keep(p, 'restored-stern-quarter.png')
        return
    if ctx.state == 'derelict':
        p = out_dir / f'derelict-stern-quarter{sfx}.png'
        _render(scene, cam, p)
        return

    # (a) patched stern quarter: 3/4 salvaged, port pod dark. Frame 19 is
    # inside one of engine 3's seeded dips, so the pair shows the unevenness.
    scene.frame_set(19)
    p = out_dir / f'{ctx.state}-stern-quarter{sfx}.png'
    _render(scene, cam, p)
    keep(p, f'{ctx.state}-stern-quarter.png')
    close = _camera(rc, f'{PREFIX} review starboard close', (-78, -62, 12), (-48, -27, 0), 40)
    p = out_dir / f'{ctx.state}-starboard-close{sfx}.png'
    _render(scene, close, p)
    keep(p, f'{ctx.state}-starboard-close.png')

    # (c) throttle ramp: ignition -> full -> idle, 3 s.
    n = 72
    ignition(ctx, 4, 20)
    set_throttle(ctx, 40, 1.0, interpolation='BEZIER')
    set_throttle(ctx, 60, .3, interpolation='BEZIER')
    frames = range(1, n + 1, 6) if quick else range(1, n + 1)
    seq = out_dir / f'ramp{sfx}'
    seq.mkdir(exist_ok=True)
    scene.render.use_persistent_data = True
    scene.cycles.samples = 12 if quick else 28
    scene.cycles.volume_step_rate = 4.0
    scene.camera = cam
    for f in frames:
        scene.frame_set(f)
        scene.render.filepath = str(seq / f'f{f:04d}.png')
        bpy.ops.render.render(write_still=True)
    if final:
        swfx_core.encode_movie(seq / 'f%04d.png', final / f'{ctx.state}-throttle-ramp.mp4')
        shutil.copyfile(seq / 'f0030.png', final / f'{ctx.state}-ramp-frame30.png')

    # (d) RCS puff: steady drive, a starboard-stern and dorsal-aft puff.
    for name in ('thr_throttle',):
        ad = ctx.controls.animation_data
        if ad:
            ad.action = None
        ctx.controls[name] = .6
    if 'thr_rcs_stern_starboard' in ctx.controls.keys():
        rcs_puff(ctx, 'stern_starboard', 6, 9)
        rcs_puff(ctx, 'dorsal_aft', 22, 9)
        rcam = _camera(rc, f'{PREFIX} review rcs', (-95, -95, 45), (-28, -30, 10), 35)
        seq = out_dir / f'rcs{sfx}'
        seq.mkdir(exist_ok=True)
        frames = range(1, 31, 4) if quick else range(1, 31)
        scene.camera = rcam
        for f in frames:
            scene.frame_set(f)
            scene.render.filepath = str(seq / f'f{f:04d}.png')
            bpy.ops.render.render(write_still=True)
        if final:
            swfx_core.encode_movie(seq / 'f%04d.png', final / f'{ctx.state}-rcs-puff.mp4')
            shutil.copyfile(seq / 'f0010.png', final / f'{ctx.state}-rcs-puff.png')
    scene.render.use_persistent_data = False
