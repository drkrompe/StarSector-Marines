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
  fit        optional: {'margin': hull units}. The bubble becomes a shell that
             fits the hull instead of the vanilla sphere: the hull's convex
             hull seen from the centre, pushed out by margin and smoothed,
             centred on the anchor in plan and on the hull's mid-height
  type       optional: 'OMNI' (default) or 'FRONT'. An OMNI shield unfolds as
             a cone about the facing and closes behind the ship (the Paragon).
             A FRONT shield is an arc in plan: everything within arc/2 of the
             facing, measured about +Z, so it covers the hull's full height
             over its width; its ends stay lit when fully open
  center     optional (x, y) hull units: the vanilla shieldCenter, an offset
             from the anchor in plan (for a hull whose anchor is its root)
  scheme     optional name in SCHEMES: fills ring / inner / inner_alpha (and
             the fortress colours) that the profile leaves out
  efficiency optional vanilla shield efficiency (flux per damage,
             ship_data.csv): sets shield_strength's default through
             strength_from_efficiency; without it the default is 1.0
  raise_s, lower_s   seconds to raise and drop the shield
  fortress_up_s, fortress_down_s   the system's charge up / down (ship_systems.csv)

build() makes, dark until raised:

  bubble    a sphere of the vanilla radius on the anchor (or with `fit`, a
            shell fitted to the hull), additive (transparent
            + emission): the inner colour faint across its face and the ring
            colour bright at its silhouette (a fresnel rim), with a slow noise
            ripple that settles out in fortress mode (vanilla's
            shieldFluctuationMult 0). Raising it unfolds the bubble the way an
            OMNI shield unfolds in the game: an arc that opens both ways from
            the facing direction (shield_facing) and closes behind the ship,
            led by a bright unfolding edge.
  hits      HITS hit points on the bubble, each an empty a shot aims by
            azimuth and elevation about the bubble's centre, held on its
            surface by a shrinkwrap constraint (shield_hit_az_<k>,
            shield_hit_el_<k>, degrees, hull frame; 0/0 is the bow): a flash
            in the ring colour, spreading over the bubble round that point.
  nodes     on each emitter node, an additive glow and a point light in the
            inner colour. They lead the raise (fully lit by a third of it), so
            the emitter array lights before the bubble closes.

Strength: shield_strength (0..1.5) is one control for how solid the shield
reads. 1.0 is the Paragon's accepted look exactly. Lower is thinner and
flickers: the face and rim fade, the whole shield wavers and patches of it
drop out, moving fast. Higher is denser: brighter, and it filters what lies
behind it toward the inner colour. strength_from_efficiency maps the vanilla
stat onto it (0.6, the best shield in vanilla, is 1.0; 1.0 is 0.8), and a shot keys it
like any knob, e.g. down as the ship's flux climbs.

Colour schemes follow the shield's technology, the hull style. SCHEMES holds the vanilla hull-style shield colours
(hull_styles.json: LOW_TECH inner 255,125,125; MIDLINE and HIGH_TECH inner
125,125,255; ring white, inner alpha 75 for all) and faction schemes built on
them: the faction's hull style, with ring and inner leaned toward the faction's
own colour (its .faction "color"). Vanilla .ship files carry no shield colours
of their own, so the hull style is the vanilla answer. Each scheme also carries
a finish (FINISHES): high tech is the Paragon's look exactly; midline and low
tech ripple harder and coarser with a dimmer rim, so high tech reads as better
tech though vanilla gives it midline's blue. The faction schemes stay
available but are no profile's default. vanilla_styles() reads hull_styles.json from an install to check them.
`swfx.apply(..., shield_scheme=NAME, shield_arc=DEG, shield_type=T)` overrides
the profile for one build.

Knobs on SWFX controls: shield_t (raise envelope 0..1), shield_fortress
(fortress-mode intensity 0..1), shield_strength (0..1.5, 1 the Paragon),
shield_facing (degrees about +Z, 0 the bow),
shield_hit_<k> (flash 0..1), shield_hit_az_<k>, shield_hit_el_<k>, and the
group's shield_gain, shield_rim, shield_hit_size and shield_light.
raise_shield(), fortress() and hit() key them with the vanilla timings
(in_arc() says whether a hit lands on an open FRONT arc); a shot
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
# Fortress mode keeps the bubble nearly clear: the face fades to this share and
# the rim to RIM_FORTRESS; the colour lives in the hits, which spread wider
# (HIT_SPREAD) and darken what lies behind them (HIT_SHADE, HIT_SHADE_FORTRESS:
# how far a hit's centre filters the light through it, toward the inner colour).
FACE_FORTRESS, RIM_FORTRESS, HIT_SPREAD = .12, .55, 1.0
HIT_SHADE, HIT_SHADE_FORTRESS = .25, .7
NODE_W = 1500.0         # node light (W) at envelope 1
EDGE_W = .035           # unfolding edge half-width as a fraction of the arc
# A FRONT shield's ends keep this share of the unfolding edge once fully open,
# so where the arc stops reads as an edge rather than a fade.
END_GLOW = .45
FLAT_EDGE = .2          # share of that edge left where the shell faces up or down
# Strength: below 1 the shield wavers by up to WAVER of its brightness and
# patches drop out (DROPOUT of it at most, over noise DROP_SCALE / R, moving
# FLICKER_RATE times faster than the ripple); above 1, DENSE_SHADE of the extra
# filters what lies behind it toward the inner colour.
STRENGTH_MAX = 1.5
WAVER, DROPOUT, DROP_SCALE, FLICKER_RATE, DENSE_SHADE = 1.4, 1.8, 2.2, 7.0, .45
BEST_EFFICIENCY = .6    # the best vanilla shield efficiency (Paragon, Medusa): strength 1
STRENGTH_PER_EFFICIENCY = .5   # strength lost per unit of efficiency above that: 1.0 reads 0.8


def _rgb(*c):
    return tuple(round(v / 255, 4) for v in c)


# Vanilla shield colours by hull style (hull_styles.json shieldRingColor /
# shieldInnerColor, Starsector 0.98a). Ring alpha is 255 and inner alpha 75 in
# every style; .ship files carry no overrides.
STYLES = {
    'LOW_TECH': dict(ring=(255, 255, 255), inner=(255, 125, 125), inner_alpha=75),
    'MIDLINE': dict(ring=(255, 255, 255), inner=(125, 125, 255), inner_alpha=75),
    'HIGH_TECH': dict(ring=(255, 255, 255), inner=(125, 125, 255), inner_alpha=75),
    'OMEGA': dict(ring=(255, 255, 255), inner=(150, 125, 255), inner_alpha=75),
    'THREAT': dict(ring=(255, 255, 255), inner=(156, 186, 174), inner_alpha=75),
    'DWELLER': dict(ring=(255, 255, 255), inner=(255, 0, 50), inner_alpha=75),
}
# Finish by shield technology: ripple (amplitude), grain (noise frequency)
# and rim (brightness), each a multiplier on the Paragon's accepted look. Vanilla
# gives midline and high tech the same blue; what separates them here is the
# finish. High tech is the Paragon itself, the reference (all 1): steady, even,
# a bright rim. Midline and low tech are busier, coarser and dimmer at the rim,
# the shield of an older emitter rather than a different paint.
IDENTITY = dict(ripple=1.0, grain=1.0, rim=1.0)
FINISHES = {
    'HIGH_TECH': IDENTITY,
    'OMEGA': IDENTITY,
    'MIDLINE': dict(ripple=1.9, grain=1.7, rim=.8),
    'LOW_TECH': dict(ripple=2.3, grain=2.0, rim=.75),
    'THREAT': dict(ripple=2.3, grain=2.0, rim=.75),
    'DWELLER': dict(ripple=1.9, grain=1.7, rim=.8),
}
# Factions (not defaults: colour follows the shield's technology, the hull
# style, and the owner keys schemes on that): (hull style, the faction's
# .faction "color"). The scheme is the
# style's colours leaned toward the faction colour (brightened to full): the
# ring by RING_LEAN, the inner by INNER_LEAN.
FACTIONS = {
    'hegemony': ('LOW_TECH', (245, 150, 30)),
    'tritachyon': ('HIGH_TECH', (135, 206, 255)),
    'independent': ('MIDLINE', (150, 150, 150)),
    'sindrian_diktat': ('MIDLINE', (180, 0, 120)),
    'luddic_church': ('LOW_TECH', (5, 115, 10)),
    'pirates': ('LOW_TECH', (255, 0, 0)),
}
RING_LEAN, INNER_LEAN = .3, .4


def _scheme(ring, inner, alpha, style, source):
    return dict(ring=_rgb(*ring), inner=_rgb(*inner), inner_alpha=round(alpha / 255, 4),
                finish=dict(FINISHES[style]), source=source)


def _lean(a, b, k):
    # Toward the faction colour's hue at full brightness, so a dark faction
    # colour (the Church's green) tints the shield instead of muddying it.
    top = max(b) or 1
    return tuple(round(x + (y * 255 / top - x) * k) for x, y in zip(a, b))


SCHEMES = {name.lower().replace('_', '-'): _scheme(s['ring'], s['inner'], s['inner_alpha'], name,
                                                  f'vanilla hull style {name}')
           for name, s in STYLES.items()}
for _f, (_style, _col) in FACTIONS.items():
    _s = STYLES[_style]
    SCHEMES[_f] = _scheme(_lean(_s['ring'], _col, RING_LEAN), _lean(_s['inner'], _col, INNER_LEAN),
                          _s['inner_alpha'], _style, f'{_style} leaned toward the {_f} faction colour {_col}')


def strength_from_efficiency(efficiency):
    """shield_strength for a vanilla shield efficiency (flux per damage absorbed;
    lower is better), linear: 0.6, the best in vanilla, is exactly 1.0; the
    capitals' 1.0 (Onslaught, Legion) is 0.8; the Conquest's 1.4 is 0.6."""
    s = 1.0 - STRENGTH_PER_EFFICIENCY * (float(efficiency) - BEST_EFFICIENCY)
    return max(0.0, min(STRENGTH_MAX, s))


def vanilla_styles(install):
    """The shield colours in an install's hull_styles.json, {style: dict(ring,
    inner, inner_alpha)} in 0..255, to check STYLES against (read only)."""
    import re
    from pathlib import Path
    text = (Path(install) / 'starsector-core' / 'data' / 'config' / 'hull_styles.json').read_text(encoding='utf-8')
    out = {}
    for m in re.finditer(r'"([A-Z_]+)"\s*:\s*\{(.*?)\n\t\}', text, re.S):
        body = m.group(2)
        ring = re.search(r'"shieldRingColor"\s*:\s*\[([^\]]*)\]', body)
        inner = re.search(r'"shieldInnerColor"\s*:\s*\[([^\]]*)\]', body)
        if ring and inner:
            r = [int(v) for v in ring.group(1).split(',')]
            i = [int(v) for v in inner.group(1).split(',')]
            out[m.group(1)] = dict(ring=tuple(r[:3]), inner=tuple(i[:3]), inner_alpha=i[3])
    return out


def resolve(spec, scheme=None, arc=None, shield_type=None):
    """A profile's SHIELD with its scheme's colours filled in (explicit colours
    win) and any per-build overrides applied."""
    s = dict(spec)
    name = scheme or s.get('scheme')
    if scheme:
        for k in ('ring', 'inner', 'inner_alpha', 'fortress_ring', 'fortress_inner', 'fortress_alpha', 'finish'):
            s.pop(k, None)
    if name:
        c = SCHEMES[name]
        s['scheme'] = name
        for k in ('ring', 'inner', 'inner_alpha', 'finish'):
            s.setdefault(k, c[k])
    s['finish'] = dict(IDENTITY, **s.get('finish', {}))
    s.setdefault('fortress_ring', s['ring'])
    s.setdefault('fortress_inner', s['inner'])
    s.setdefault('fortress_alpha', s['inner_alpha'])
    if arc is not None:
        s['arc'] = float(arc)
    if shield_type:
        s['type'] = shield_type
    s['type'] = s.get('type', 'OMNI').upper()
    return s


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


def _additive(nodes, links, colour, strength, transmit=None):
    out = nodes.new('ShaderNodeOutputMaterial')
    em = nodes.new('ShaderNodeEmission')
    if hasattr(colour, 'node'):
        links.new(colour, em.inputs['Color'])
    else:
        em.inputs['Color'].default_value = (*colour, 1)
    links.new(strength, em.inputs['Strength'])
    tr = nodes.new('ShaderNodeBsdfTransparent')
    if transmit is not None:
        links.new(transmit, tr.inputs['Color'])
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


def _bubble_material(ctx, s, hits, R, facing):
    m, nodes, links = swfx_core.node_material(f'{PREFIX} bubble')
    g = _G(nodes, links)
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
    fin = s['finish']
    noise.inputs['Scale'].default_value = 3.0 * fin['grain'] / R
    noise.inputs['Detail'].default_value = 2.0
    links.new(tc.outputs['Object'], noise.inputs['Vector'])
    clock = swfx_core.value_node(ctx, nodes, 'shield time', 'frame / 30.0')
    links.new(clock, noise.inputs['W'])
    ripple = g.add(1.0, g.mul(g.sub(noise.outputs['Fac'], .5), g.mul(g.sub(1.0, fort), 1.2 * fin['ripple'])))
    # Unfolding: the angle from the facing direction (the facing empty's +X,
    # at the centre), as a fraction of the half-arc; the shield covers what is
    # within shield_t of it. OMNI measures it in 3D (a cone that closes behind
    # the ship); FRONT measures it in plan, about +Z, so the arc spans the
    # hull's whole height.
    ftc = nodes.new('ShaderNodeTexCoord')
    ftc.object = facing
    sep = nodes.new('ShaderNodeSeparateXYZ')
    half_arc = math.radians(float(s.get('arc', 360))) / 2
    front = s.get('type', 'OMNI') == 'FRONT'
    if front:
        links.new(ftc.outputs['Object'], sep.inputs[0])
        frac = g.mul(g.op('ABSOLUTE', g.op('ARCTAN2', sep.outputs['Y'], sep.outputs['X'])), 1.0 / half_arc)
    else:
        nrm = nodes.new('ShaderNodeVectorMath')
        nrm.operation = 'NORMALIZE'
        links.new(ftc.outputs['Object'], nrm.inputs[0])
        links.new(nrm.outputs[0], sep.inputs[0])
        frac = g.mul(g.op('ARCCOSINE', g.op('MINIMUM', g.op('MAXIMUM', sep.outputs['X'], -1.0), 1.0)),
                     1.0 / half_arc)
    t = swfx_core.value_node(ctx, nodes, 'raise', 'shield_t', ('shield_t',))
    # A FRONT arc stops at its half-arc when fully open; an OMNI one overshoots
    # so it closes behind the ship.
    reach = g.mul(t, 1.0 + (1 if front else 2) * EDGE_W)
    cover = g.clamp01(g.mul(g.sub(reach, frac), 1.0 / EDGE_W))
    # A bright band at the unfolding edge while the arc is still opening (and,
    # on a FRONT arc, a dimmer one that stays at its ends).
    d = g.mul(g.sub(frac, g.sub(reach, EDGE_W)), 1.0 / EDGE_W)
    band = g.op('EXPONENT', g.mul(g.mul(d, d), -1.0))
    opening = g.clamp01(g.mul(g.sub(1.0, t), 20.0))
    if front:
        opening = g.add(opening, g.mul(g.sub(1.0, opening), END_GLOW))
        # The arc's edge is a radial line across the shell's top and bottom
        # (the game fills its arc from the centre, a pie slice): keep the band
        # bright on the walls and faint there, or it reads as a beam.
        nsep = nodes.new('ShaderNodeSeparateXYZ')
        links.new(tc.outputs['Normal'], nsep.inputs[0])
        nz = g.op('ABSOLUTE', nsep.outputs['Z'])
        opening = g.mul(opening, g.sub(1.0, g.mul(g.mul(nz, nz), 1.0 - FLAT_EDGE)))
    edge = g.mul(g.mul(band, opening), EDGE_PEAK)
    env = swfx_core.value_node(ctx, nodes, 'shield env', 'shield_gain * (shield_t > 0.001)',
                               ('shield_gain', 'shield_t'))
    face_k = g.add(1.0, g.mul(fort, FACE_FORTRESS - 1.0))
    rim_f = g.add(1.0, g.mul(fort, RIM_FORTRESS - 1.0))
    rimk = swfx_core.value_node(ctx, nodes, 'rim', 'shield_rim', ('shield_rim',))
    inner_a = g.add(s['inner_alpha'], g.mul(fort, s['fortress_alpha'] - s['inner_alpha']))
    # Thinner face-on, thickening toward the silhouette (the vanilla shield's gradient).
    grad = g.add(.35, g.mul(g.pow(g.clamp01(lw.outputs['Facing']), 1.5), .65))
    face_s = g.mul(g.mul(g.mul(inner_a, FACE_PEAK / .3), ripple), grad)
    rim_s = g.mul(g.mul(rim, rimk), RIM_PEAK * fin['rim'])
    # Strength: every term is exactly 1 (or 0) at shield_strength 1, the
    # Paragon's look. The face thins with it, the rim less so.
    sig = swfx_core.value_node(ctx, nodes, 'strength', 'shield_strength', ('shield_strength',))
    weak = g.op('MAXIMUM', g.sub(1.0, sig), 0.0)
    dense = g.op('MAXIMUM', g.sub(sig, 1.0), 0.0)
    face_s = g.mul(face_s, sig)
    rim_s = g.mul(rim_s, g.sub(1.0, g.mul(g.sub(1.0, sig), .6)))
    # A weak shield wavers as a whole and drops out in patches, both fast.
    fast = g.mul(clock, FLICKER_RATE)
    wn = nodes.new('ShaderNodeTexNoise')
    wn.noise_dimensions = '1D'
    wn.inputs['Scale'].default_value = 1.0
    links.new(fast, wn.inputs['W'])
    waver = g.op('MAXIMUM', g.add(1.0, g.mul(g.mul(weak, WAVER), g.sub(wn.outputs['Fac'], .5))), 0.0)
    dn = nodes.new('ShaderNodeTexNoise')
    dn.noise_dimensions = '4D'
    dn.inputs['Scale'].default_value = DROP_SCALE / R
    dn.inputs['Detail'].default_value = 3.0
    links.new(tc.outputs['Object'], dn.inputs['Vector'])
    links.new(g.mul(fast, .5), dn.inputs['W'])
    hole = g.clamp01(g.mul(g.sub(.55, dn.outputs['Fac']), 4.0))
    drop = g.clamp01(g.sub(1.0, g.mul(g.mul(weak, DROPOUT), hole)))
    solid = g.mul(waver, drop)
    inner_c = g.mix_rgb(fort, s['inner'], s['fortress_inner'])
    ring_c = g.mix_rgb(fort, s['ring'], s['fortress_ring'])
    # Hits: a flash in the ring colour where the bubble is nearest each hit empty.
    size = swfx_core.value_node(ctx, nodes, 'hit size',
                                f'shield_hit_size * {R} * (1.0 + {HIT_SPREAD} * shield_fortress)',
                                ('shield_hit_size', 'shield_fortress'))
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
    rim_s = g.mul(rim_s, rim_f)
    # In fortress mode a hit glows in the inner colour rather than white.
    hit_w = g.mul(g.mul(hit_s, HIT_PEAK), g.sub(1.0, g.mul(fort, .75)))
    ring_w = g.clamp01(g.mul(g.add(g.add(rim_s, edge), hit_w), .5))
    colour = g.mix_rgb(ring_w, inner_c, ring_c)
    body = g.add(g.mul(face_s, face_k), rim_s)
    strength = g.mul(g.add(g.mul(g.mul(g.add(body, edge), solid), cover),
                           g.mul(g.mul(g.mul(hit_s, HIT_PEAK), waver), cover)), env)
    # A hit also filters what is behind it (darker, in the inner colour), most in fortress mode.
    on = swfx_core.value_node(ctx, nodes, 'shield on', '(shield_t > 0.001) * 1.0', ('shield_t',))
    shade = g.mul(g.mul(g.clamp01(g.mul(hit_s, cover)), on),
                  g.add(HIT_SHADE, g.mul(fort, HIT_SHADE_FORTRESS - HIT_SHADE)))
    tint = g.mix_rgb(shade, (1.0, 1.0, 1.0), tuple(.35 * c for c in s['inner']))
    tint_f = g.mix_rgb(shade, (1.0, 1.0, 1.0), tuple(.35 * c for c in s['fortress_inner']))
    transmit = g.mix_rgb(fort, tint, tint_f)
    # A dense shield filters what lies behind it toward the inner colour.
    thick = g.mul(g.mul(g.mul(dense, DENSE_SHADE), cover), on)
    transmit = g.mix_rgb(thick, transmit, tuple(.5 * c for c in s['inner']))
    _additive(nodes, links, colour, strength, transmit)
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
    if mat is not None:
        me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    ob.visible_shadow = False
    return ob


def _hull_points(anchor):
    """World positions of every mesh vertex of the ship the anchor belongs to."""
    import numpy as np
    root = anchor
    while root.parent:
        root = root.parent
    pts = []
    for o in bpy.data.objects:
        if o.type != 'MESH' or o.name.startswith('SWFX'):
            continue
        top = o
        while top.parent:
            top = top.parent
        if top != root:
            continue
        n = len(o.data.vertices)
        a = np.empty(n * 3)
        o.data.vertices.foreach_get('co', a)
        M = np.array(o.matrix_world)
        pts.append(a.reshape(-1, 3) @ M[:3, :3].T + M[:3, 3])
    return np.concatenate(pts)


def _shell(name, coll, centre, pts, margin, u=128, v=64, smooth=40):
    """A closed shell round pts (world), in centre's local frame: a unit sphere
    pushed out along each direction to the convex hull of pts plus margin, then
    smoothed and pushed back out wherever smoothing cut inside half the margin.
    Returns the object and its mean radius."""
    import numpy as np
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree
    inv = np.array(centre.matrix_world.inverted())
    local = pts @ inv[:3, :3].T + inv[:3, 3]
    hb = bmesh.new()
    for p in local:
        hb.verts.new(p)
    bmesh.ops.convex_hull(hb, input=hb.verts)
    for vt in [vt for vt in hb.verts if not vt.link_faces]:
        hb.verts.remove(vt)
    tree = BVHTree.FromBMesh(hb)
    corners = [Vector(vt.co) for vt in hb.verts]
    hb.free()
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=u, v_segments=v, radius=1.0)
    bm.verts.ensure_lookup_table()
    dirs = [vt.co.normalized() for vt in bm.verts]
    reach = []
    for d in dirs:
        hit = tree.ray_cast(Vector((0, 0, 0)), d)
        reach.append((hit[3] if hit[0] is not None else 0.0) + margin)
    reach = np.array(reach)
    rad = reach.copy()
    nbr = [[e.other_vert(vt).index for e in vt.link_edges] for vt in bm.verts]
    for _ in range(smooth):
        rad = np.array([.5 * rad[i] + .5 * rad[n].mean() for i, n in enumerate(nbr)])
        rad = np.maximum(rad, reach - .5 * margin)
    # Sampled directions can miss a narrow feature (a prong tip) between them:
    # test every convex-hull corner against the shell and swell it smoothly
    # round any corner that sits closer than half the margin.
    D = np.array([tuple(d) for d in dirs])
    for _ in range(4):
        for vt, d, r in zip(bm.verts, dirs, rad):
            vt.co = d * float(r)
        shell = BVHTree.FromBMesh(bm)
        bump = np.zeros(len(rad))
        for c in corners:
            n = c.normalized()
            hit = shell.ray_cast(Vector((0, 0, 0)), n)
            short = c.length + .5 * margin - (hit[3] if hit[0] is not None else 0.0)
            if short > 0:
                ang = np.arccos(np.clip(D @ np.array(tuple(n)), -1, 1))
                bump = np.maximum(bump, (short * 1.2) * np.exp(-(ang / math.radians(8)) ** 2))
        if not bump.any():
            break
        rad += bump
    for vt, d, r in zip(bm.verts, dirs, rad):
        vt.co = d * float(r)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    ob.visible_shadow = False
    return ob, float(rad.mean())


def build(ctx, shield_scheme=None, shield_arc=None, shield_type=None, **options):
    coll = ctx.collection(GROUP)
    record = {'bubble': None, 'hits': [], 'nodes': [], 'lights': [], 'missing': []}
    if SHIELD is None or not ctx.spec.get('powered', True):
        return record
    s = resolve(SHIELD, shield_scheme, shield_arc, shield_type)
    anchor = bpy.data.objects.get(s['anchor'])
    if anchor is None:
        record['missing'].append(s['anchor'])
        return record
    for name, default, lo, hi, desc in KNOBS:
        ctx.prop(name, default, lo, hi, desc)
    ctx.prop('shield_t', 0.0, 0, 1, 'shield raise envelope (0 down, 1 fully raised)')
    ctx.prop('shield_fortress', 0.0, 0, 1, 'Fortress Shield intensity (0 off, 1 on)')
    ctx.prop('shield_facing', 0.0, -180, 180, 'direction the shield unfolds from, degrees about +Z (0 the bow)')
    eff = s.get('efficiency')
    strength = strength_from_efficiency(eff) if eff else 1.0
    ctx.prop('shield_strength', strength, 0, STRENGTH_MAX,
             'how solid the shield reads: 1 the Paragon, lower thinner and flickering, higher denser')
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
    # The bubble's centre: the anchor, or with a fit, the anchor in plan at the
    # hull's mid-height.
    fit = s.get('fit')
    centre = swfx_core.empty(f'{PREFIX} centre', coll, R * .1, 'PLAIN_AXES')
    swfx_core.attach(centre, rig)
    pts = None
    if fit:
        pts = _hull_points(anchor)
        zmid = .5 * (pts[:, 2].min() + pts[:, 2].max())
        centre.location = (0, 0, zmid - anchor.matrix_world.translation.z)
    if s.get('center'):
        cx, cy = s['center'][:2]
        centre.location = (cx, cy, centre.location.z)
    bpy.context.view_layer.update()
    # Facing turns the unfolding direction only (the shader reads it); the
    # bubble and the hits stay in the hull frame.
    facing = swfx_core.empty(f'{PREFIX} facing', coll, R * .2)
    swfx_core.attach(facing, centre)
    ctx.drive(facing, 'rotation_euler', 'radians(shield_facing)', ('shield_facing',), index=2)
    if fit:
        bubble, R = _shell(f'{PREFIX} bubble', coll, centre, pts, float(fit.get('margin', 12.0)))
    else:
        bubble = _sphere(f'{PREFIX} bubble', coll, R, None, u=96, v=48)
    swfx_core.attach(bubble, centre)
    hits = []
    for k in range(1, HITS + 1):
        pivot = swfx_core.empty(f'{PREFIX} hit {k} pivot', coll, R * .05, 'PLAIN_AXES')
        swfx_core.attach(pivot, centre)
        # XYZ order: the elevation (Y) applies before the azimuth (Z).
        ctx.drive(pivot, 'rotation_euler', f'radians(shield_hit_az_{k})', (f'shield_hit_az_{k}',), index=2)
        ctx.drive(pivot, 'rotation_euler', f'-radians(shield_hit_el_{k})', (f'shield_hit_el_{k}',), index=1)
        h = swfx_core.empty(f'{PREFIX} hit {k}', coll, R * .05, 'SPHERE')
        # Out past the bubble, then projected back in along -X onto its surface.
        swfx_core.attach(h, pivot, local=(3 * R, 0, 0))
        sw = h.constraints.new('SHRINKWRAP')
        sw.target = bubble
        sw.shrinkwrap_type = 'PROJECT'
        sw.project_axis = 'NEG_X'
        sw.project_axis_space = 'LOCAL'
        sw.project_limit = 0.0
        hits.append(h)
        record['hits'].append(h.name)
    bubble.data.materials.append(_bubble_material(ctx, s, hits, R, facing))
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
    record['radius'] = float(s['radius'])
    record['type'] = s['type']
    record['arc'] = float(s.get('arc', 360))
    record['scheme'] = s.get('scheme')
    record['colours'] = {k: s[k] for k in ('ring', 'inner', 'inner_alpha')}
    record['finish'] = s['finish']
    record['strength'] = round(strength, 3)
    record['mean_radius'] = round(R, 2)
    record['fit'] = dict(fit) if fit else None
    record['centre'] = centre.name
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


def in_arc(azimuth, facing=0.0, t=1.0, arc=None):
    """True when a hit from azimuth (degrees, hull frame) lands on the open
    shield: within t of half the arc either side of facing. An OMNI shield of
    360 degrees takes every azimuth once fully raised. A shot uses it to send
    a round that misses the arc on to the hull instead."""
    arc = float(arc if arc is not None else (SHIELD or {}).get('arc', 360))
    off = abs((azimuth - facing + 180.0) % 360.0 - 180.0)
    return off <= t * arc / 2
