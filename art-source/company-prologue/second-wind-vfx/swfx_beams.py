"""Beam weapon effects: continuous and burst energy beams off declared muzzles.

The Second Wind carries no beam weapon, so this group is never in swfx.GROUPS
and a Second Wind build is untouched: a hull whose profile declares BEAMS asks
for it (`swfx.apply(groups=(..., 'beams'))`). The first such hull is the
Paragon (`paragon-cinematic/vfx_hull.py`).

A profile's BEAMS is a list of dicts, one per emitter:

  id        short name-safe key ('ws003'); knobs and objects carry it
  muzzle    the object the beam rides (+X along the line of fire), e.g. the
            turret kit's `Weapon WS 003 muzzle anchor 1`, so it trains with the
            pivot and elevates with the elevation empty
  weapon    the vanilla weapon id ('tachyonlance', 'gravitonbeam', ...)
  width     the vanilla beam width (hull units: one sprite pixel each)
  range     the vanilla range (hull units)
  core, fringe, glow   the vanilla .wpn colours (0..1 RGB)
  chargeup, chargedown   seconds (weapon_data.csv)
  burst     seconds a burst beam stays at full power (None: continuous)

Per beam, build() makes a rig that is dark until fired:

  beam      a unit-length cylinder along the muzzle's +X, scaled to
            beam_len_<id> (hull units, default the weapon's range): a narrow
            white-hot core inside a wider, soft fringe in the weapon's colour,
            both additive (transparent + emission). A noise field that slides
            along the beam with the frame gives the vanilla ROUGH texture.
  flare     an additive glow sphere at the muzzle in the glow colour. It
            reads the charge envelope, so a charging lance builds at the
            emitter before the beam appears.
  impact    the same glow at the beam's far end (it follows beam_len_<id>):
            where the beam meets a target, or burns out at range in open space.
  lights    point lights at the muzzle and the impact, glow coloured, so a
            firing beam lights its own turret and the hull around it.

Knobs on SWFX controls: beam_t_<id> (beam envelope 0..1), beam_charge_<id>
(charge envelope 0..1), beam_len_<id> (hull units), and the group's
beam_gain, beam_width, beam_flare and beam_light. fire_beam() keys the
envelopes from the weapon's own charge-up, burst and charge-down; a shot may
key them by hand instead. The rig is parented to its muzzle, so a beam
swings with a training turret; aim the turret at the target and set
beam_len_<id> to the distance to make it land.
"""
import bpy
from mathutils import Vector

import swfx_core
import swfx_hull

GROUP = 'Beams'
PREFIX = 'SWFX beams'
BEAMS = swfx_hull.get('BEAMS', [])

KNOBS = [
    ('beam_gain', 1.0, 0.0, 4.0, 'master beam brightness (core, fringe, flares)'),
    ('beam_width', 1.0, 0.1, 4.0, 'beam thickness multiplier'),
    ('beam_flare', 1.0, 0.0, 4.0, 'muzzle and impact flare brightness'),
    ('beam_light', 1.0, 0.0, 4.0, 'muzzle and impact point-light power multiplier'),
]
# Fraction of the vanilla width each layer fills (vanilla's width is the whole
# glow; the white core is a small part of it).
CORE_FRAC, FRINGE_FRAC = .07, .3
LIGHT_W = 6000.0        # muzzle light (W) at envelope 1 for a 25-unit beam


def _knob(ctx, name, default, lo, hi, description):
    ctx.prop(name, default, lo, hi, description)
    return name


class _G:
    def __init__(self, nodes, links):
        self.nodes, self.links = nodes, links

    def op(self, op, a, b=None):
        return swfx_core.math_node(self.nodes, self.links, op, a, b)

    def mul(self, a, b): return self.op('MULTIPLY', a, b)
    def add(self, a, b): return self.op('ADD', a, b)
    def sub(self, a, b): return self.op('SUBTRACT', a, b)
    def pow(self, a, b): return self.op('POWER', a, b)
    def clamp01(self, a): return self.op('MINIMUM', self.op('MAXIMUM', a, 0.0), 1.0)


def _additive(nodes, links, colour_socket_or_rgb, strength):
    out = nodes.new('ShaderNodeOutputMaterial')
    em = nodes.new('ShaderNodeEmission')
    if hasattr(colour_socket_or_rgb, 'node'):
        links.new(colour_socket_or_rgb, em.inputs['Color'])
    else:
        em.inputs['Color'].default_value = (*colour_socket_or_rgb, 1)
    links.new(strength, em.inputs['Strength'])
    tr = nodes.new('ShaderNodeBsdfTransparent')
    add = nodes.new('ShaderNodeAddShader')
    links.new(tr.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    links.new(add.outputs[0], out.inputs['Surface'])


def _beam_material(ctx, b, layer):
    """layer 'core' (white, hard) or 'fringe' (weapon colour, soft edge)."""
    m, nodes, links = swfx_core.node_material(f"{PREFIX} {b['id']} {layer}")
    g = _G(nodes, links)
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    # Facing is 1 at the rod's silhouette centre line and 0 at its edges.
    facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 1.5 if layer == 'core' else 3.0)
    # ROUGH texture: noise along the beam (object X, in hull units through the
    # scale) that slides with the frame, so the fringe crawls and the core shimmers.
    tc = nodes.new('ShaderNodeTexCoord')
    noise = nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = 2.5 if layer == 'core' else 1.2
    noise.inputs['Detail'].default_value = 3.0
    links.new(tc.outputs['Object'], noise.inputs['Vector'])
    t = swfx_core.value_node(ctx, nodes, 'beam time', 'frame / 6.0')
    links.new(t, noise.inputs['W'])
    rough = g.add(.55 if layer == 'core' else .25, g.mul(noise.outputs['Fac'], .9 if layer == 'core' else 1.5))
    # Soft start at the emitter and burn-out over the last tenth of the length.
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(tc.outputs['Generated'], sep.inputs[0])
    gx = sep.outputs['X']
    ends = g.mul(g.clamp01(g.mul(gx, 60.0)), g.clamp01(g.mul(g.sub(1.0, gx), 10.0)))
    env = swfx_core.value_node(ctx, nodes, 'beam env', f"beam_t_{b['id']} * beam_gain",
                               (f"beam_t_{b['id']}", 'beam_gain'))
    # Kept low: a strong emitter goes white under AgX and loses the weapon's colour.
    peak = 5.0 if layer == 'core' else 2.2
    strength = g.mul(g.mul(g.mul(facing, rough), ends), g.mul(env, peak))
    _additive(nodes, links, (1.0, 1.0, 1.0) if layer == 'core' else b['fringe'], strength)
    if layer == 'core':
        # Not pure white: a trace of the glow colour, as vanilla's core over its fringe reads.
        em = next(n for n in nodes if n.type == 'EMISSION')
        em.inputs['Color'].default_value = (*[.82 + .18 * c for c in b['core']], 1)
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    return m


def _flare_material(ctx, b, which):
    m, nodes, links = swfx_core.node_material(f"{PREFIX} {b['id']} {which} flare")
    g = _G(nodes, links)
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 7.0)
    if which == 'muzzle':
        expr = f"max(beam_t_{b['id']}, beam_charge_{b['id']}) * beam_gain * beam_flare"
        props = (f"beam_t_{b['id']}", f"beam_charge_{b['id']}", 'beam_gain', 'beam_flare')
    else:
        expr = f"beam_t_{b['id']} * beam_gain * beam_flare"
        props = (f"beam_t_{b['id']}", 'beam_gain', 'beam_flare')
    env = swfx_core.value_node(ctx, nodes, 'flare env', expr, props)
    _additive(nodes, links, b['glow'], g.mul(facing, g.mul(env, 3.0)))
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    return m


def _rod(name, coll, radius, mat, sides=16):
    """Unit-length cylinder along +X (x 0..1), radius `radius`, capped."""
    import math
    verts, faces = [], []
    for x in (0.0, 1.0):
        for k in range(sides):
            a = 2 * math.pi * k / sides
            verts.append((x, radius * math.cos(a), radius * math.sin(a)))
    for k in range(sides):
        k2 = (k + 1) % sides
        faces.append((k, k2, sides + k2, sides + k))
    faces.append(tuple(range(sides))[::-1])
    faces.append(tuple(range(sides, 2 * sides)))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.materials.append(mat)
    for p in me.polygons:
        p.use_smooth = True
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    return ob


def _sphere(name, coll, radius, mat):
    me = bpy.data.meshes.new(name)
    import bmesh
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=24, v_segments=12, radius=radius)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    return ob


def _point(name, coll, colour, radius):
    ob = swfx_core.point_light(name, coll, colour, 0.0, radius=radius)
    ob.visible_camera = False
    return ob


def build_beam(ctx, coll, b, muzzle):
    i = b['id']
    _knob(ctx, f'beam_t_{i}', 0.0, 0, 1, f"{b['weapon']} {i}: beam envelope")
    _knob(ctx, f'beam_charge_{i}', 0.0, 0, 1, f"{b['weapon']} {i}: charge-up glow at the emitter")
    _knob(ctx, f'beam_len_{i}', float(b['range']), 0, 4 * float(b['range']),
          f"{b['weapon']} {i}: beam length in hull units (the distance to the target)")
    w = float(b['width'])
    rig = swfx_core.empty(f'{PREFIX} {i}', coll, w)
    swfx_core.attach(rig, muzzle)
    # Lengths and widths are hull units: cancel any scale the muzzle carries
    # (the turret kit scales a mount by its kit_scale).
    ms = muzzle.matrix_world.to_scale()
    rig.scale = (1 / ms.x, 1 / ms.y, 1 / ms.z)
    parts = []
    for layer, frac in (('core', CORE_FRAC), ('fringe', FRINGE_FRAC)):
        rod = _rod(f'{PREFIX} {i} {layer}', coll, w * frac, _beam_material(ctx, b, layer))
        swfx_core.attach(rod, rig)
        # Length along X, thickness in Y and Z; a dark beam collapses to nothing.
        on = f'(beam_t_{i} > 0.001)'
        ctx.drive(rod, 'scale', f'{on} * beam_len_{i}', (f'beam_t_{i}', f'beam_len_{i}'), index=0)
        for ax in (1, 2):
            ctx.drive(rod, 'scale', f'{on} * beam_width * (0.6 + 0.4 * beam_t_{i})',
                      (f'beam_t_{i}', 'beam_width'), index=ax)
        rod.visible_shadow = False
        parts.append(rod.name)
    flare = _sphere(f'{PREFIX} {i} muzzle flare', coll, w * .32, _flare_material(ctx, b, 'muzzle'))
    swfx_core.attach(flare, rig)
    flare.visible_shadow = False
    impact = _sphere(f'{PREFIX} {i} impact flare', coll, w * .7, _flare_material(ctx, b, 'impact'))
    swfx_core.attach(impact, rig)
    impact.visible_shadow = False
    ctx.drive(impact, 'location', f'beam_len_{i}', (f'beam_len_{i}',), index=0)
    for ob, which in ((flare, 'muzzle'), (impact, 'impact')):
        env = (f'max(beam_t_{i}, beam_charge_{i})' if which == 'muzzle' else f'beam_t_{i}')
        props = (f'beam_t_{i}', f'beam_charge_{i}', 'beam_flare') if which == 'muzzle' else (f'beam_t_{i}', 'beam_flare')
        s = f'(0.35 + 0.65 * {env}) * ({env} > 0.001) * min(beam_flare, 2.0)'
        # Drawn out along the beam (local X), never a ball at either end (owner, 2026-10-23: no orbs).
        for ax, k in ((0, 2.6), (1, .6), (2, .6)):
            ctx.drive(ob, 'scale', f'{k:g} * {s}', props, index=ax)
    power = LIGHT_W * (w / 25.0) ** 2
    lights = []
    for which, at in (('muzzle', (w * .6, 0, 0)), ('impact', None)):
        lt = _point(f'{PREFIX} {i} {which} light', coll, b['glow'], w * .5)
        swfx_core.attach(lt, rig, local=at or (0, 0, 0))
        env = f'max(beam_t_{i}, beam_charge_{i})' if which == 'muzzle' else f'beam_t_{i}'
        props = (f'beam_t_{i}', f'beam_charge_{i}', 'beam_gain', 'beam_light') if which == 'muzzle' \
            else (f'beam_t_{i}', 'beam_gain', 'beam_light')
        ctx.drive(lt.data, 'energy', f'{power:.0f} * {env} * beam_gain * beam_light', props)
        if which == 'impact':
            ctx.drive(lt, 'location', f'beam_len_{i} - {w * .6:.2f}', (f'beam_len_{i}',), index=0)
        lights.append(lt.name)
    return {'id': i, 'weapon': b['weapon'], 'muzzle': muzzle.name, 'width': w, 'range': b['range'],
            'parts': parts, 'flares': [flare.name, impact.name], 'lights': lights}


def build(ctx, **options):
    coll = ctx.collection(GROUP)
    for name, default, lo, hi, desc in KNOBS:
        ctx.prop(name, default, lo, hi, desc)
    record = {'beams': [], 'missing': []}
    if not ctx.spec.get('guns', True):
        return record
    for b in BEAMS:
        muzzle = bpy.data.objects.get(b['muzzle'])
        if muzzle is None:
            record['missing'].append(b['id'])
            continue
        record['beams'].append(build_beam(ctx, coll, b, muzzle))
    return record


def fire_beam(ctx, beam_id, start_frame, hold_s=None, fps=swfx_core.FPS):
    """Key one shot of beam `beam_id` from `start_frame` with its weapon's own
    timing: the charge glow ramps over chargeup, the beam snaps on and holds
    for its burst (a burst beam such as the tachyon lance) or hold_s seconds
    (a continuous beam; default 2), then fades over chargedown. Returns the
    frame the beam is dark again."""
    b = next(x for x in BEAMS if x['id'] == beam_id)
    up = max(1, round(b['chargeup'] * fps))
    hold = max(1, round((b['burst'] if b.get('burst') else (hold_s or 2.0)) * fps))
    down = max(1, round(b['chargedown'] * fps))
    f0 = start_frame
    on, off, dark = f0 + up, f0 + up + hold, f0 + up + hold + down
    t, c = f'beam_t_{beam_id}', f'beam_charge_{beam_id}'
    for name, frame, value in ((c, f0 - 1, 0.0), (c, f0, 0.0), (c, on, 1.0), (c, off, .6), (c, dark, 0.0),
                               (t, f0 - 1, 0.0), (t, on - 1, 0.0), (t, on, 1.0), (t, off, 1.0), (t, dark, 0.0)):
        ctx.key(name, frame, value, 'LINEAR')
    return dark


def clear_line(origin, target, ignore=(), scene=None, start=0.5, short=0.0):
    """Whether a beam from origin reaches target without crossing the ship:
    returns (True, None) or (False, name of the first object in the way).
    Objects whose names start with any prefix in `ignore` (the firing weapon's
    own parts) and the kit's own objects are passed through; the ray stops
    `short` units before the target, so a target with a body (a missile, a
    fighter) is not its own obstruction. A beam must not cut through its own
    hull: a shot fires a weapon only along a clear line."""
    scene = scene or bpy.context.scene
    dg = bpy.context.evaluated_depsgraph_get()
    o, t = Vector(origin), Vector(target)
    d = (t - o).normalized()
    left = (t - o).length - start - short
    o = o + d * start
    skip = ('SWFX',) + tuple(ignore)
    while left > 0:
        hit, loc, _n, _i, ob, _m = scene.ray_cast(dg, o, d, distance=left)
        if not hit:
            return True, None
        if not ob.name.startswith(skip):
            return False, ob.name
        step = (loc - o).length + 1e-3
        o, left = o + d * step, left - step
    return True, None


def aim(beam_rig_name, target, scene=None):
    """Distance from a beam's emitter to a world-space target, for beam_len_<id>
    (the turret must already be trained on it)."""
    rig = bpy.data.objects[beam_rig_name]
    (scene or bpy.context.scene).frame_set((scene or bpy.context.scene).frame_current)
    return (Vector(target) - rig.matrix_world.translation).length
