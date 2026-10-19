"""Shield effects: an additive shield bubble off a declared emitter, with hit flashes.

The Second Wind has no shield, so this group is never in swfx.GROUPS and a
Second Wind build is untouched: a hull whose profile declares SHIELD asks for
it (`swfx.apply(groups=(..., 'shield'))`). The first such hull is the Paragon
(`paragon-cinematic/vfx_hull.py`), whose OMNI shield and Fortress Shield system
are the model here.

A profile's SHIELD is a dict:

  anchor     the empty the bubble is centred on (vanilla shieldCenter), e.g.
             the Paragon's `Shield emitter`
  radius     the vanilla shieldRadius (hull units)
  arc        the vanilla shield arc in degrees (360 for an OMNI bubble)
  nodes      names of emitter empties that glow as the shield rises (may be empty)
  ring       the vanilla shieldRingColor (0..1 RGB): the bubble's rim and hits
  inner      the vanilla shieldInnerColor (0..1 RGB), and inner_alpha its alpha
  fortress_ring, fortress_inner, fortress_alpha   the ship system's
             shieldRingColor / shieldInnerColor when it is a SHIELD_MOD
             (fortressshield): what the bubble blends to at shield_fortress 1
  raise_s, lower_s   seconds to raise and drop the shield
  fortress_up_s, fortress_down_s   the system's charge up / down (ship_systems.csv)

build() makes, dark until raised:

  bubble    a sphere of the vanilla radius on the anchor, additive (transparent
            + emission): the inner colour faint across its face and the ring
            colour bright at its silhouette (a fresnel rim), with a slow noise
            ripple that settles out in fortress mode (vanilla's
            shieldFluctuationMult 0). Raising it unfolds the bubble the way an
            OMNI shield unfolds in the game: an arc that opens both ways from
            the facing direction (shield_facing) and closes behind the ship,
            led by a bright unfolding edge.
  hits      HITS hit points on the bubble, each an empty a shot aims by
            azimuth and elevation about the anchor (shield_hit_az_<k>,
            shield_hit_el_<k>, degrees, hull frame; 0/0 is the bow): a flash
            in the ring colour, spreading over the bubble round that point.
  nodes     on each emitter node, an additive glow and a point light in the
            inner colour. They lead the raise (fully lit by a third of it), so
            the emitter array lights before the bubble closes.

Knobs on SWFX controls: shield_t (raise envelope 0..1), shield_fortress
(fortress-mode intensity 0..1), shield_facing (degrees about +Z, 0 the bow),
shield_hit_<k> (flash 0..1), shield_hit_az_<k>, shield_hit_el_<k>, and the
group's shield_gain, shield_rim, shield_hit_size and shield_light.
raise_shield(), fortress() and hit() key them with the vanilla timings; a shot
may key them by hand instead. In the game a Fortress Shield ship cannot fire
while the system is on (noFiring): a shot that shows it should keep the beams
dark.
"""
import math

import bpy
import bmesh

import swfx_core
import swfx_hull

GROUP = 'Shield'
PREFIX = 'SWFX shield'
SHIELD = swfx_hull.get('SHIELD', None)
HITS = 4

KNOBS = [
    ('shield_gain', 1.0, 0.0, 4.0, 'master shield brightness (bubble, rim, hits, nodes)'),
    ('shield_rim', 1.0, 0.0, 4.0, 'rim (ring colour) brightness relative to the face'),
    ('shield_hit_size', .1, .02, .6, 'hit flash spread as a fraction of the shield radius'),
    ('shield_light', 1.0, 0.0, 4.0, 'emitter node point-light power multiplier'),
]
# Peak emission strengths at envelope 1. Kept low: a strong emitter goes white
# under AgX and the bubble's colour is the point of it.
# The face is seen twice (the near and the far wall add), so it is the faintest.
FACE_PEAK, RIM_PEAK, EDGE_PEAK, HIT_PEAK = .16, 1.3, 2.2, 4.0
FORTRESS_GAIN = 1.0     # extra face and rim brightness at shield_fortress 1
NODE_W = 1500.0         # node light (W) at envelope 1
EDGE_W = .035           # unfolding edge half-width as a fraction of the arc


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

    def mix_rgb(self, fac, a, b):
        n = self.nodes.new('ShaderNodeMix')
        n.data_type = 'RGBA'
        self.links.new(fac, n.inputs['Factor'])
        for sock, v in ((n.inputs[6], a), (n.inputs[7], b)):
            if hasattr(v, 'node'):
                self.links.new(v, sock)
            else:
                sock.default_value = (*v, 1)
        return n.outputs[2]


def _additive(nodes, links, colour, strength):
    out = nodes.new('ShaderNodeOutputMaterial')
    em = nodes.new('ShaderNodeEmission')
    if hasattr(colour, 'node'):
        links.new(colour, em.inputs['Color'])
    else:
        em.inputs['Color'].default_value = (*colour, 1)
    links.new(strength, em.inputs['Strength'])
    tr = nodes.new('ShaderNodeBsdfTransparent')
    add = nodes.new('ShaderNodeAddShader')
    links.new(tr.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    links.new(add.outputs[0], out.inputs['Surface'])


def _blended(m):
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    return m


def _bubble_material(ctx, s, hits):
    m, nodes, links = swfx_core.node_material(f'{PREFIX} bubble')
    g = _G(nodes, links)
    R = float(s['radius'])
    fort = swfx_core.value_node(ctx, nodes, 'fortress', 'shield_fortress', ('shield_fortress',))
    # Face: the inner colour at its vanilla alpha. Rim: the ring colour, a fresnel
    # edge (Facing is 0 face-on, 1 at the silhouette).
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    rim = g.pow(g.clamp01(lw.outputs['Facing']), 4.0)
    # Ripple: slow 4D noise over the bubble (object coordinates, hull units),
    # damped toward nothing in fortress mode.
    tc = nodes.new('ShaderNodeTexCoord')
    noise = nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = 3.0 / R
    noise.inputs['Detail'].default_value = 2.0
    links.new(tc.outputs['Object'], noise.inputs['Vector'])
    links.new(swfx_core.value_node(ctx, nodes, 'shield time', 'frame / 30.0'), noise.inputs['W'])
    ripple = g.add(1.0, g.mul(g.sub(noise.outputs['Fac'], .5), g.mul(g.sub(1.0, fort), 1.2)))
    # Unfolding: the angle from the facing direction (local +X), as a fraction
    # of the half-arc; the shield covers what is within shield_t of it.
    nrm = nodes.new('ShaderNodeVectorMath')
    nrm.operation = 'NORMALIZE'
    links.new(tc.outputs['Object'], nrm.inputs[0])
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(nrm.outputs[0], sep.inputs[0])
    half_arc = math.radians(float(s.get('arc', 360))) / 2
    frac = g.mul(g.op('ARCCOSINE', g.op('MINIMUM', g.op('MAXIMUM', sep.outputs['X'], -1.0), 1.0)), 1.0 / half_arc)
    t = swfx_core.value_node(ctx, nodes, 'raise', 'shield_t', ('shield_t',))
    reach = g.mul(t, 1.0 + 2 * EDGE_W)
    cover = g.clamp01(g.mul(g.sub(reach, frac), 1.0 / EDGE_W))
    # A bright band at the unfolding edge while the arc is still opening.
    d = g.mul(g.sub(frac, g.sub(reach, EDGE_W)), 1.0 / EDGE_W)
    band = g.op('EXPONENT', g.mul(g.mul(d, d), -1.0))
    opening = g.clamp01(g.mul(g.sub(1.0, t), 20.0))
    edge = g.mul(g.mul(band, opening), EDGE_PEAK)
    env = swfx_core.value_node(ctx, nodes, 'shield env', 'shield_gain * (shield_t > 0.001)',
                               ('shield_gain', 'shield_t'))
    boost = g.add(1.0, g.mul(fort, FORTRESS_GAIN))
    rimk = swfx_core.value_node(ctx, nodes, 'rim', 'shield_rim', ('shield_rim',))
    inner_a = g.add(s['inner_alpha'], g.mul(fort, s['fortress_alpha'] - s['inner_alpha']))
    # Thinner face-on, thickening toward the silhouette (the vanilla shield's gradient).
    grad = g.add(.35, g.mul(g.pow(g.clamp01(lw.outputs['Facing']), 1.5), .65))
    face_s = g.mul(g.mul(g.mul(inner_a, FACE_PEAK / .3), ripple), grad)
    rim_s = g.mul(g.mul(rim, rimk), RIM_PEAK)
    inner_c = g.mix_rgb(fort, s['inner'], s['fortress_inner'])
    ring_c = g.mix_rgb(fort, s['ring'], s['fortress_ring'])
    # Hits: a flash in the ring colour where the bubble is nearest each hit empty.
    size = swfx_core.value_node(ctx, nodes, 'hit size', f'shield_hit_size * {R}', ('shield_hit_size',))
    hit_s = None
    for k, hit_ob in enumerate(hits, 1):
        htc = nodes.new('ShaderNodeTexCoord')
        htc.object = hit_ob
        ln = nodes.new('ShaderNodeVectorMath')
        ln.operation = 'LENGTH'
        links.new(htc.outputs['Object'], ln.inputs[0])
        q = g.op('DIVIDE', ln.outputs['Value'], size)
        fall = g.op('EXPONENT', g.mul(g.mul(q, q), -1.0))
        amt = swfx_core.value_node(ctx, nodes, f'hit {k}', f'shield_hit_{k}', (f'shield_hit_{k}',))
        one = g.mul(fall, amt)
        hit_s = one if hit_s is None else g.add(hit_s, one)
    # Colour: inner across the face, ring at the rim, the edge and the hits.
    ring_w = g.clamp01(g.mul(g.add(g.add(rim_s, edge), g.mul(hit_s, HIT_PEAK)), .5))
    colour = g.mix_rgb(ring_w, inner_c, ring_c)
    body = g.mul(g.add(face_s, rim_s), boost)
    strength = g.mul(g.add(g.mul(g.add(body, edge), cover), g.mul(g.mul(hit_s, HIT_PEAK), cover)), env)
    _additive(nodes, links, colour, strength)
    return _blended(m)


def _node_material(ctx, s):
    m, nodes, links = swfx_core.node_material(f'{PREFIX} node glow')
    g = _G(nodes, links)
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    facing = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 3.0)
    fort = swfx_core.value_node(ctx, nodes, 'fortress', 'shield_fortress', ('shield_fortress',))
    env = swfx_core.value_node(ctx, nodes, 'node env', f'{_NODE_ENV} * shield_gain',
                               ('shield_t', 'shield_fortress', 'shield_gain'))
    colour = g.mix_rgb(fort, s['inner'], s['fortress_inner'])
    _additive(nodes, links, colour, g.mul(facing, g.mul(env, 6.0)))
    return _blended(m)


# The nodes lead the raise (lit by a third of it) and burn hotter in fortress mode.
_NODE_ENV = 'min(1.0, shield_t * 3.0) * (1.0 + 0.8 * shield_fortress)'


def _sphere(name, coll, radius, mat, u=24, v=12):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=u, v_segments=v, radius=radius)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    ob.visible_shadow = False
    return ob


def build(ctx, **options):
    coll = ctx.collection(GROUP)
    record = {'bubble': None, 'hits': [], 'nodes': [], 'lights': [], 'missing': []}
    if SHIELD is None or not ctx.spec.get('powered', True):
        return record
    s = SHIELD
    anchor = bpy.data.objects.get(s['anchor'])
    if anchor is None:
        record['missing'].append(s['anchor'])
        return record
    for name, default, lo, hi, desc in KNOBS:
        ctx.prop(name, default, lo, hi, desc)
    ctx.prop('shield_t', 0.0, 0, 1, 'shield raise envelope (0 down, 1 fully raised)')
    ctx.prop('shield_fortress', 0.0, 0, 1, 'Fortress Shield intensity (0 off, 1 on)')
    ctx.prop('shield_facing', 0.0, -180, 180, 'direction the shield unfolds from, degrees about +Z (0 the bow)')
    for k in range(1, HITS + 1):
        ctx.prop(f'shield_hit_{k}', 0.0, 0, 1, f'hit {k}: flash strength')
        ctx.prop(f'shield_hit_az_{k}', 0.0, -180, 180, f'hit {k}: azimuth about +Z, degrees (0 the bow, 90 port)')
        ctx.prop(f'shield_hit_el_{k}', 0.0, -90, 90, f'hit {k}: elevation, degrees')
    R = float(s['radius'])
    rig = swfx_core.empty(f'{PREFIX} rig', coll, R * .1, 'SPHERE')
    swfx_core.attach(rig, anchor)
    # Radius and offsets are hull units: cancel any scale the anchor carries.
    sc = anchor.matrix_world.to_scale()
    rig.scale = (1 / sc.x, 1 / sc.y, 1 / sc.z)
    # Facing turns the unfolding direction only; the hits stay in the hull frame.
    facing = swfx_core.empty(f'{PREFIX} facing', coll, R * .2)
    swfx_core.attach(facing, rig)
    ctx.drive(facing, 'rotation_euler', 'radians(shield_facing)', ('shield_facing',), index=2)
    hits = []
    for k in range(1, HITS + 1):
        pivot = swfx_core.empty(f'{PREFIX} hit {k} pivot', coll, R * .05, 'PLAIN_AXES')
        swfx_core.attach(pivot, rig)
        # XYZ order: the elevation (Y) applies before the azimuth (Z).
        ctx.drive(pivot, 'rotation_euler', f'radians(shield_hit_az_{k})', (f'shield_hit_az_{k}',), index=2)
        ctx.drive(pivot, 'rotation_euler', f'-radians(shield_hit_el_{k})', (f'shield_hit_el_{k}',), index=1)
        h = swfx_core.empty(f'{PREFIX} hit {k}', coll, R * .05, 'SPHERE')
        swfx_core.attach(h, pivot, local=(R, 0, 0))
        hits.append(h)
        record['hits'].append(h.name)
    bubble = _sphere(f'{PREFIX} bubble', coll, R, _bubble_material(ctx, s, hits), u=96, v=48)
    swfx_core.attach(bubble, facing)
    # The bubble lights nothing by itself (the node lights do): keeps it clean
    # of sampling noise on the hull.
    bubble.visible_diffuse = bubble.visible_glossy = False
    for ax in range(3):
        ctx.drive(bubble, 'scale', '(shield_t > 0.001) * 1.0', ('shield_t',), index=ax)
    record['bubble'] = bubble.name
    glow = _node_material(ctx, s)
    props = ('shield_t', 'shield_fortress', 'shield_gain', 'shield_light')
    for name in s.get('nodes', ()):
        n = bpy.data.objects.get(name)
        if n is None:
            record['missing'].append(name)
            continue
        ns = n.matrix_world.to_scale()
        g = _sphere(f'{PREFIX} {name}', coll, 2.4, glow)
        swfx_core.attach(g, n)
        for ax in range(3):
            ctx.drive(g, 'scale', f'(shield_t > 0.001) * (0.5 + 0.5 * min(1.0, shield_t * 3.0)) / {ns[ax]:.6f}',
                      ('shield_t',), index=ax)
        lt = swfx_core.point_light(f'{PREFIX} {name} light', coll, s['inner'], 0.0, radius=2.0)
        lt.visible_camera = False
        swfx_core.attach(lt, n, local=(0, 0, 1.5 / ns.z))
        ctx.drive(lt.data, 'energy', f'{NODE_W:.0f} * {_NODE_ENV} * shield_gain * shield_light', props)
        # Light colour follows the fortress blend.
        for i, (a, b) in enumerate(zip(s['inner'], s['fortress_inner'])):
            ctx.drive(lt.data, 'color', f'{a:.4f} + ({b - a:.4f}) * shield_fortress', ('shield_fortress',), index=i)
        record['nodes'].append(g.name)
        record['lights'].append(lt.name)
    record['radius'] = R
    record['anchor'] = anchor.name
    return record


def raise_shield(ctx, start_frame, hold_s=None, fps=swfx_core.FPS):
    """Key shield_t up over raise_s from start_frame; with hold_s, hold it raised
    that long and drop it over lower_s. Returns the frame it is fully raised
    (no hold_s) or dark again."""
    up = max(1, round(SHIELD.get('raise_s', 1.0) * fps))
    ctx.key('shield_t', start_frame - 1, 0.0, 'BEZIER')
    ctx.key('shield_t', start_frame, 0.0, 'BEZIER')
    ctx.key('shield_t', start_frame + up, 1.0, 'BEZIER')
    if hold_s is None:
        return start_frame + up
    off = start_frame + up + max(1, round(hold_s * fps))
    down = max(1, round(SHIELD.get('lower_s', 1.0) * fps))
    ctx.key('shield_t', off, 1.0, 'BEZIER')
    ctx.key('shield_t', off + down, 0.0, 'BEZIER')
    return off + down


def fortress(ctx, start_frame, hold_s=None, fps=swfx_core.FPS):
    """Key the Fortress Shield on over the system's charge-up (the shield must
    be raised); with hold_s, off again over its charge-down. Returns the frame
    it is fully on (no hold_s) or off again."""
    up = max(1, round(SHIELD.get('fortress_up_s', 1.5) * fps))
    ctx.key('shield_fortress', start_frame - 1, 0.0, 'LINEAR')
    ctx.key('shield_fortress', start_frame, 0.0, 'LINEAR')
    ctx.key('shield_fortress', start_frame + up, 1.0, 'LINEAR')
    if hold_s is None:
        return start_frame + up
    off = start_frame + up + max(1, round(hold_s * fps))
    down = max(1, round(SHIELD.get('fortress_down_s', 1.5) * fps))
    ctx.key('shield_fortress', off, 1.0, 'LINEAR')
    ctx.key('shield_fortress', off + down, 0.0, 'LINEAR')
    return off + down


def hit(ctx, k, frame, azimuth, elevation=0.0, strength=1.0, decay_s=.4, fps=swfx_core.FPS):
    """Key hit flash k at frame from (azimuth, elevation) degrees in the hull
    frame: snaps to strength, fades over decay_s. Returns the frame it is gone."""
    gone = frame + max(1, round(decay_s * fps))
    ctx.key(f'shield_hit_az_{k}', frame, azimuth, 'CONSTANT')
    ctx.key(f'shield_hit_el_{k}', frame, elevation, 'CONSTANT')
    for f, v in ((frame - 1, 0.0), (frame, strength), (gone, 0.0)):
        ctx.key(f'shield_hit_{k}', f, v, 'LINEAR')
    return gone


def direction(azimuth, elevation=0.0):
    """Unit vector (hull frame) of a hit at (azimuth, elevation) degrees."""
    a, e = math.radians(azimuth), math.radians(elevation)
    return (math.cos(a) * math.cos(e), math.sin(a) * math.cos(e), math.sin(e))
