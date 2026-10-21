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
tech ripple harder and coarser with a dim rim and a murkier face, so high tech reads as better
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

The film (opt-in, `build(ctx, film=True)`; asharu-effects.md, the owner's
answers of 2026-10-20). The shield is a film of suspended plasma held by the
emitters: what it absorbs consumes it, flux is how thin it has worn. Without
`film` nothing below is built and the bubble is the accepted look above,
byte for byte. With it:

  - Instance-safe: the bubble's shader reads only its own object coordinates
    and knob values (no texture coordinates of other objects), so a ship
    placed through a collection instance at any transform (the Asharu scene,
    the effects bench) shades in its own frame. The hits are found from their
    azimuth and elevation directly, by angle over the shell's surface.
  - Hits brighten the whole film (opacity tied to hits) and each throws a
    ripple ring outward along the surface (shield_ring_<k>, its phase: 0 at
    impact, 1 spent), fading as it spreads; several overlap.
  - shield_flux (0..1): the film wears thin. Moving thin patches open up,
    ripples spread wider and ring longer, thin-film banding colours the
    thinnest film (a soap bubble about to burst), and a hit on a thin patch
    flashes dimmer (it leaks through).
  - shield_overload (0..1, a timeline): a forced dissolve. Holes burn open
    at the last hit (shield_tear_az / _el) and at the thinnest patches at once
    and spread until they meet, each edged with a bright band like the raise's
    unfolding edge. shield_shards (off by default) also breaks the film into
    ragged burning pieces that drift off;
    arcs crawl over the hull back toward the emitter; a light at the tear lights
    the hull. At 1 the shield is gone. overload() keys it, stutters the ship's
    lamps (lit_ship_master, where the build has it) and drops the shield.
  - shield_vent (0..1): the vents and radiators named in SHIELD['vents'] glow
    and shed shimmering vapour jets; vent() keys it and drops the shield and
    the flux. A running ship's vent is heat and vapour, never fire.

Recovery is raise_shield() again: the emitters repaint the film from the
facing outward. arc_material() and arc_curve() are the crawling-arc pieces,
public for the ion cannon's EMP arcs (W4).

`hull_collection` (a collection holding the ship's hull) makes build() find
the anchor, nodes, vents and hull points inside that collection rather than
by name in bpy.data: a scene with several ships of one build passes each
ship's own (ship_fx.ShipFx.hull).
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

# --- the film (build(film=True)) -----------------------------------------
FILM_KNOBS = [
    ('shield_flux', 0.0, 0.0, 1.0, 'hard flux: the film worn thin (thin patches, wider and longer ripples, '
                                   'banding, leaking hits)'),
    ('shield_overload', 0.0, 0.0, 1.0, 'overload timeline: 0 none; the tear runs, the film shatters, arcs '
                                       'crawl; 1 the shield is gone'),
    ('shield_tear_az', 0.0, -180.0, 180.0, 'where the overload tear starts: azimuth, degrees (the last hit)'),
    ('shield_tear_el', 0.0, -90.0, 90.0, 'where the overload tear starts: elevation, degrees'),
    ('shield_vent', 0.0, 0.0, 1.0, 'venting: radiators and vents glow and shed vapour jets'),
    ('shield_shards', 0.0, 0.0, 2.0, 'overload: also break the film into burning pieces (0 the dissolve alone)'),
    ('shield_ripple', 1.0, 0.0, 4.0, 'ripple ring brightness'),
    ('shield_band', 1.0, 0.0, 4.0, 'thin-film banding brightness'),
    ('shield_arcs', 1.0, 0.0, 4.0, 'overload arc brightness'),
]
HIT_LIFT = .25          # whole-film brightening per unit of live hit flash (opacity tied to hits)
FILM_FACE, FILM_RIM = .45, .8      # the film at rest: fainter than the bubble's face and rim (owner, 2026-10-21)
FILM_HIT_PEAK = 2.0     # a hit's flash on the film: local and dimmer than the bubble's HIT_PEAK
RING_PEAK = 1.0         # ripple ring brightness at impact
RING_REACH = (.35, .8)  # how far a ring runs, in mean radii: at flux 0, and the extra at flux 1
RING_WIDTH = (.7, .25)  # ring half-width as a share of the hit spread: flux 0, extra at flux 1
FILM_HIT_SIZE = .09     # the film's hit spread (shield_hit_size): a tight local flash; the ring carries it outward
RING_S = .7             # seconds a ring takes to run out at flux 0 (x (1 + 1.5 flux))
FLUX_SCALE, FLUX_RATE = 2.6, .45   # thin patches: noise scale over R, drift (per second of clock)
THIN_FACE, THIN_RIM = .85, .5      # how much a fully thin patch dims the face and the rim
LEAK = .65              # how much a hit on a fully thin patch dims (it leaks through)
BANDS, BAND_PEAK = 2.2, .4        # thin-film interference bands across the thinness, and their brightness
TEAR_SPAN = .45         # share of the overload timeline the dissolve takes to eat the whole film
# The dissolve: the order the film goes in (0 first, 1 last) mixes where it is thinnest
# (DISSOLVE_THIN), how far from the last hit (DISSOLVE_HIT) and a fine ragged grain.
DISSOLVE_THIN, DISSOLVE_HIT, DISSOLVE_GRAIN, DISSOLVE_GRAIN_SCALE = .6, .4, .12, 16.0
DISSOLVE_SOFT, DISSOLVE_EDGE, DISSOLVE_PEAK = .008, .012, 2.4   # hole softness, edge half-width, edge glow
TEAR_PEAK = 6.0         # the dissolve edge's glow is scaled against this
TEAR_W = 6000.0         # the tear light at its flash (W): a flicker on the hull, not a white-out
SHARD_PEAK, SHARD_DECAY, SHARD_LIFT = 2.5, 1.6, .16   # shard glow, burn rate, scatter (mean radii)
SHARD_SUBDIV, SHARD_PIECES = 5, 150  # the shard mesh: a fine icosphere grown into this many ragged pieces
SHARD_GAP, SHARD_SHRINK = .93, .45   # pieces pulled in from each other; shrunk to this as they burn
SHARD_BURN_SCALE = 7.0               # burn-hole noise scale over the mean radius
ARCS, ARC_PEAK = 8, 9.0
VENT_PEAK, JET_PEAK, VENT_W = 2.5, 1.4, 1800.0
JET_LEN = .32           # vapour jet length in mean radii
HEAT = (1.0, .32, .08)  # radiator glow (a dull orange-red heat, not flame)
VAPOUR = (.85, .82, .78)
# Overload: the ship's lamps stutter (frame offset, share of their level), then come back.
LAMP_STUTTER = [(0, 1.0), (2, .12), (4, .9), (5, .05), (8, .7), (10, .0), (13, .5), (15, .0), (24, .35), (34, 1.0)]


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
# Finish by shield technology: ripple (amplitude), grain (noise frequency),
# rim (brightness) and face (density of the face), each a multiplier on the Paragon's accepted look. Vanilla
# gives midline and high tech the same blue; what separates them here is the
# finish. High tech is the Paragon itself, the reference (all 1): steady, even,
# a bright rim. Midline and low tech are rougher and coarser, with a dim rim and
# a murkier face: the shield of an older emitter rather than a different paint.
IDENTITY = dict(ripple=1.0, grain=1.0, rim=1.0, face=1.0)
FINISHES = {
    'HIGH_TECH': IDENTITY,
    'OMEGA': IDENTITY,
    # Owner's pick (round 3, candidate C): rough, a dim rim and a murky face.
    # Low tech shares it; its pink-red is what tells it apart.
    'MIDLINE': dict(ripple=3.0, grain=2.6, rim=.5, face=1.8),
    'LOW_TECH': dict(ripple=3.0, grain=2.6, rim=.5, face=1.8),
    'THREAT': dict(ripple=3.0, grain=2.6, rim=.5, face=1.8),
    'DWELLER': dict(ripple=3.0, grain=2.6, rim=.5, face=1.8),
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
    face_s = g.mul(g.mul(g.mul(inner_a, FACE_PEAK / .3 * fin['face']), ripple), grad)
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


# --- the film ----------------------------------------------------------------

def _additive_multi(nodes, links, parts, transmit=None):
    """Transparent plus one emission per (colour, strength) part."""
    out = nodes.new('ShaderNodeOutputMaterial')
    tr = nodes.new('ShaderNodeBsdfTransparent')
    if transmit is not None:
        links.new(transmit, tr.inputs['Color'])
    shader = tr.outputs[0]
    for colour, strength in parts:
        em = nodes.new('ShaderNodeEmission')
        if hasattr(colour, 'node'):
            links.new(colour, em.inputs['Color'])
        else:
            em.inputs['Color'].default_value = (*colour, 1)
        links.new(strength, em.inputs['Strength'])
        add = nodes.new('ShaderNodeAddShader')
        links.new(shader, add.inputs[0])
        links.new(em.outputs[0], add.inputs[1])
        shader = add.outputs[0]
    links.new(shader, out.inputs['Surface'])


def _unit(g, nodes, links, pos):
    """(x, y, z) sockets of a position socket normalised."""
    nrm = nodes.new('ShaderNodeVectorMath')
    nrm.operation = 'NORMALIZE'
    links.new(pos, nrm.inputs[0])
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(nrm.outputs[0], sep.inputs[0])
    return sep.outputs['X'], sep.outputs['Y'], sep.outputs['Z']


def _direction(ctx, nodes, az, el):
    """(x, y, z) of the unit direction at knobs az / el (degrees, hull frame), as driven values."""
    p = (az, el)
    return (swfx_core.value_node(ctx, nodes, f'{az} x', f'cos(radians({az})) * cos(radians({el}))', p),
            swfx_core.value_node(ctx, nodes, f'{az} y', f'sin(radians({az})) * cos(radians({el}))', p),
            swfx_core.value_node(ctx, nodes, f'{az} z', f'sin(radians({el}))', p))


def _angle(g, n, d):
    """Angle (radians) between unit sockets n and d."""
    dot = g.add(g.add(g.mul(n[0], d[0]), g.mul(n[1], d[1])), g.mul(n[2], d[2]))
    return g.op('ARCCOSINE', g.op('MINIMUM', g.op('MAXIMUM', dot, -1.0), 1.0))


def _arc_cover(ctx, g, nodes, links, pos, s, t):
    """What of the arc a position (centre frame) lies within at raise t: (cover, frac, reach).
    The facing is a rotation of the position by -shield_facing, so nothing reads another
    object's frame (instance-safe)."""
    rot = nodes.new('ShaderNodeVectorRotate')
    rot.rotation_type = 'Z_AXIS'
    links.new(pos, rot.inputs['Vector'])
    links.new(swfx_core.value_node(ctx, nodes, 'unfacing', '-radians(shield_facing)', ('shield_facing',)),
              rot.inputs['Angle'])
    half_arc = math.radians(float(s.get('arc', 360))) / 2
    front = s.get('type', 'OMNI') == 'FRONT'
    if front:
        sep = nodes.new('ShaderNodeSeparateXYZ')
        links.new(rot.outputs[0], sep.inputs[0])
        frac = g.mul(g.op('ABSOLUTE', g.op('ARCTAN2', sep.outputs['Y'], sep.outputs['X'])), 1.0 / half_arc)
    else:
        x = _unit(g, nodes, links, rot.outputs[0])[0]
        frac = g.mul(g.op('ARCCOSINE', g.op('MINIMUM', g.op('MAXIMUM', x, -1.0), 1.0)), 1.0 / half_arc)
    reach = g.mul(t, 1.0 + (1 if front else 2) * EDGE_W)
    cover = g.clamp01(g.mul(g.sub(reach, frac), 1.0 / EDGE_W))
    return cover, frac, reach


def _tear_front():
    """The dissolve level: how far down the dissolve order the film has gone (0 .. past 1)."""
    return f'{1.0 + DISSOLVE_GRAIN:.3f} * min(1.0, shield_overload / {TEAR_SPAN})'


def _thin_noise(g, nodes, links, pos, R, fin, clock):
    """The film's moving thin-patch field (the flux patches and the dissolve share it)."""
    pn = nodes.new('ShaderNodeTexNoise')
    pn.noise_dimensions = '4D'
    pn.inputs['Scale'].default_value = FLUX_SCALE * math.sqrt(fin['grain']) / R
    pn.inputs['Detail'].default_value = 2.5
    links.new(pos, pn.inputs['Vector'])
    links.new(g.mul(clock, FLUX_RATE), pn.inputs['W'])
    return pn.outputs['Fac']


def _dissolve_order(ctx, g, nodes, links, pos, thin_fac, R):
    """Where a point of film falls in the overload's dissolve (0 goes first, 1 last): the
    thinnest patches and the last hit first, so holes open at several spots at once and
    spread until they meet, with ragged edges."""
    th = _angle(g, _unit(g, nodes, links, pos), _direction(ctx, nodes, 'shield_tear_az', 'shield_tear_el'))
    fine = nodes.new('ShaderNodeTexNoise')
    fine.noise_dimensions = '3D'
    fine.inputs['Scale'].default_value = DISSOLVE_GRAIN_SCALE / R
    fine.inputs['Detail'].default_value = 3.0
    links.new(pos, fine.inputs['Vector'])
    order = g.add(g.mul(g.clamp01(g.mul(g.sub(.72, thin_fac), 2.2)), DISSOLVE_THIN),
                  g.mul(th, DISSOLVE_HIT / math.pi))
    return g.add(order, g.mul(g.sub(fine.outputs['Fac'], .5), 2 * DISSOLVE_GRAIN))


def _film_material(ctx, s, R, Rm):
    """The film: the accepted bubble (same face, rim, ripple, unfold, strength and fortress
    terms) in the bubble's own coordinates, plus hits that lift the film and throw ripple
    rings, flux thinning with banding and leaks, and the overload tear."""
    m, nodes, links = swfx_core.node_material(f'{PREFIX} film')
    g = _G(nodes, links)
    v = lambda name, expr, props: swfx_core.value_node(ctx, nodes, name, expr, props)  # noqa: E731
    fort = v('fortress', 'shield_fortress', ('shield_fortress',))
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    rim = g.pow(g.clamp01(lw.outputs['Facing']), 4.0)
    tc = nodes.new('ShaderNodeTexCoord')
    pos = tc.outputs['Object']
    fin = s['finish']
    noise = nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = 3.0 * fin['grain'] / R
    noise.inputs['Detail'].default_value = 2.0
    links.new(pos, noise.inputs['Vector'])
    clock = v('shield time', 'frame / 30.0', ())
    links.new(clock, noise.inputs['W'])
    ripple = g.add(1.0, g.mul(g.sub(noise.outputs['Fac'], .5), g.mul(g.sub(1.0, fort), 1.2 * fin['ripple'])))
    t = v('raise', 'shield_t', ('shield_t',))
    cover, frac, reach = _arc_cover(ctx, g, nodes, links, pos, s, t)
    front = s.get('type', 'OMNI') == 'FRONT'
    d = g.mul(g.sub(frac, g.sub(reach, EDGE_W)), 1.0 / EDGE_W)
    band = g.op('EXPONENT', g.mul(g.mul(d, d), -1.0))
    opening = g.clamp01(g.mul(g.sub(1.0, t), 20.0))
    if front:
        opening = g.add(opening, g.mul(g.sub(1.0, opening), END_GLOW))
        nsep = nodes.new('ShaderNodeSeparateXYZ')
        links.new(tc.outputs['Normal'], nsep.inputs[0])
        nz = g.op('ABSOLUTE', nsep.outputs['Z'])
        opening = g.mul(opening, g.sub(1.0, g.mul(g.mul(nz, nz), 1.0 - FLAT_EDGE)))
    edge = g.mul(g.mul(band, opening), EDGE_PEAK)
    env = v('shield env', 'shield_gain * (shield_t > 0.001)', ('shield_gain', 'shield_t'))
    face_k = g.add(1.0, g.mul(fort, FACE_FORTRESS - 1.0))
    rim_f = g.add(1.0, g.mul(fort, RIM_FORTRESS - 1.0))
    rimk = v('rim', 'shield_rim', ('shield_rim',))
    inner_a = g.add(s['inner_alpha'], g.mul(fort, s['fortress_alpha'] - s['inner_alpha']))
    grad = g.add(.35, g.mul(g.pow(g.clamp01(lw.outputs['Facing']), 1.5), .65))
    face_s = g.mul(g.mul(g.mul(inner_a, FACE_PEAK / .3 * fin['face'] * FILM_FACE), ripple), grad)
    rim_s = g.mul(g.mul(rim, rimk), RIM_PEAK * fin['rim'] * FILM_RIM)
    sig = v('strength', 'shield_strength', ('shield_strength',))
    weak = g.op('MAXIMUM', g.sub(1.0, sig), 0.0)
    dense = g.op('MAXIMUM', g.sub(sig, 1.0), 0.0)
    face_s = g.mul(face_s, sig)
    rim_s = g.mul(rim_s, g.sub(1.0, g.mul(g.sub(1.0, sig), .6)))
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
    links.new(pos, dn.inputs['Vector'])
    links.new(g.mul(fast, .5), dn.inputs['W'])
    hole = g.clamp01(g.mul(g.sub(.55, dn.outputs['Fac']), 4.0))
    drop = g.clamp01(g.sub(1.0, g.mul(g.mul(weak, DROPOUT), hole)))
    solid = g.mul(waver, drop)
    inner_c = g.mix_rgb(fort, s['inner'], s['fortress_inner'])
    ring_c = g.mix_rgb(fort, s['ring'], s['fortress_ring'])
    # Flux: moving thin patches that open wider as it climbs.
    flux = v('flux', 'shield_flux', ('shield_flux',))
    thin_fac = _thin_noise(g, nodes, links, pos, R, fin, clock)
    thin = g.clamp01(g.sub(g.mul(flux, 1.4), g.clamp01(g.mul(g.sub(.6, thin_fac), 5.0))))
    face_s = g.mul(face_s, g.sub(1.0, g.mul(thin, THIN_FACE)))
    rim_s = g.mul(rim_s, g.sub(1.0, g.mul(thin, THIN_RIM)))
    # Hits, found by angle over the surface from their own azimuth and elevation.
    n = _unit(g, nodes, links, pos)
    ln = nodes.new('ShaderNodeVectorMath')
    ln.operation = 'LENGTH'
    links.new(pos, ln.inputs[0])
    radius = ln.outputs['Value']
    size = v('hit size', f'shield_hit_size * {Rm} * (1.0 + {HIT_SPREAD} * shield_fortress)',
             ('shield_hit_size', 'shield_fortress'))
    width = g.mul(size, g.add(RING_WIDTH[0], g.mul(flux, RING_WIDTH[1])))
    run = g.mul(g.add(RING_REACH[0], g.mul(flux, RING_REACH[1])), Rm)
    hit_s = ring_s = None
    for k in range(1, HITS + 1):
        dist = g.mul(_angle(g, n, _direction(ctx, nodes, f'shield_hit_az_{k}', f'shield_hit_el_{k}')), radius)
        q = g.op('DIVIDE', dist, size)
        amt = v(f'hit {k}', f'shield_hit_{k}', (f'shield_hit_{k}',))
        one = g.mul(g.op('EXPONENT', g.mul(g.mul(q, q), -1.0)), amt)
        phase = v(f'ring {k}', f'shield_ring_{k}', (f'shield_ring_{k}',))
        rq = g.op('DIVIDE', g.sub(dist, g.mul(phase, run)), width)
        ring = g.mul(g.op('EXPONENT', g.mul(g.mul(rq, rq), -1.0)), g.pow(g.clamp01(g.sub(1.0, phase)), 1.5))
        hit_s = one if hit_s is None else g.add(hit_s, one)
        ring_s = ring if ring_s is None else g.add(ring_s, ring)
    lift = v('hit lift', f'1.0 + {HIT_LIFT} * min(2.0, ' + ' + '.join(f'shield_hit_{k}' for k in range(1, HITS + 1)) + ')',
             tuple(f'shield_hit_{k}' for k in range(1, HITS + 1)))
    leak = g.sub(1.0, g.mul(thin, LEAK))
    hit_s = g.mul(hit_s, leak)
    ring_w = g.mul(g.mul(ring_s, v('ripple', 'shield_ripple', ('shield_ripple',))), RING_PEAK)
    # Overload: a forced dissolve. Holes burn open at the last hit and at the thinnest
    # patches together and spread until they meet, each edged with a bright band, the way
    # the raise's unfolding edge runs, only torn open.
    order = _dissolve_order(ctx, g, nodes, links, pos, thin_fac, R)
    tear_at = v('tear front', _tear_front(), ('shield_overload',))
    on_o = v('overloading', '(shield_overload > 0.0001) * 1.0', ('shield_overload',))
    torn = g.mul(g.clamp01(g.mul(g.sub(tear_at, order), 1.0 / DISSOLVE_SOFT)), on_o)
    film = g.sub(1.0, torn)
    te = g.mul(g.sub(order, tear_at), 1.0 / DISSOLVE_EDGE)
    tear_env = v('tear env', f'min(1.0, shield_overload / 0.02) * max(0.0, 1.0 - shield_overload / {TEAR_SPAN * 1.1})',
                 ('shield_overload',))
    tear = g.mul(g.mul(g.op('EXPONENT', g.mul(g.mul(te, te), -1.0)), tear_env), DISSOLVE_PEAK / TEAR_PEAK)
    # Colour: inner across the face; ring at the rim, the edges, the hits and the rings.
    rim_s = g.mul(rim_s, rim_f)
    hit_w = g.mul(g.mul(hit_s, FILM_HIT_PEAK), g.sub(1.0, g.mul(fort, .75)))
    # Rings stay mostly in the film's tint: overlapping wide rings at high flux washed it white.
    ring_mix = g.clamp01(g.mul(g.add(g.add(rim_s, edge), g.add(hit_w, g.mul(ring_w, .3))), .5))
    colour = g.mix_rgb(ring_mix, inner_c, ring_c)
    body = g.add(g.mul(g.mul(face_s, face_k), lift), rim_s)
    lit = g.add(g.mul(g.add(body, edge), solid), g.mul(g.add(g.mul(hit_s, FILM_HIT_PEAK), ring_w), waver))
    strength = g.mul(g.mul(g.add(g.mul(lit, film), g.mul(tear, TEAR_PEAK)), cover), env)
    # Thin-film banding where the film is thin but not gone (black at the very thinnest).
    h = g.sub(1.0, thin)
    bands = nodes.new('ShaderNodeCombineXYZ')
    for i, off in enumerate((0.0, .33, .67)):
        c = g.add(.5, g.mul(g.op('COSINE', g.mul(g.add(g.mul(h, BANDS), off), 2 * math.pi)), .5))
        links.new(c, bands.inputs[i])
    band_w = g.mul(g.mul(g.mul(thin, h), 4.0 * BAND_PEAK), g.mul(flux, v('band', 'shield_band', ('shield_band',))))
    band_s = g.mul(g.mul(g.mul(g.mul(band_w, solid), film), cover), env)
    on = v('shield on', '(shield_t > 0.001) * 1.0', ('shield_t',))
    shade = g.mul(g.mul(g.clamp01(g.mul(g.mul(hit_s, cover), film)), on),
                  g.add(HIT_SHADE, g.mul(fort, HIT_SHADE_FORTRESS - HIT_SHADE)))
    tint = g.mix_rgb(shade, (1.0, 1.0, 1.0), tuple(.35 * c for c in s['inner']))
    tint_f = g.mix_rgb(shade, (1.0, 1.0, 1.0), tuple(.35 * c for c in s['fortress_inner']))
    transmit = g.mix_rgb(fort, tint, tint_f)
    thick = g.mul(g.mul(g.mul(g.mul(dense, DENSE_SHADE), cover), on), film)
    transmit = g.mix_rgb(thick, transmit, tuple(.5 * c for c in s['inner']))
    _additive_multi(nodes, links, [(colour, strength), (bands.outputs[0], band_s)], transmit)
    return _blended(m)


def _shard_pieces(rng):
    """A fine icosphere's triangles grown into SHARD_PIECES irregular pieces (a jittered
    Voronoi growth from scattered seeds), so a piece is a ragged curved patch, never a quad.
    Returns (unit vertex positions, triangles as vertex-index triples, piece per triangle)."""
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=SHARD_SUBDIV, radius=1.0)
    bm.faces.ensure_lookup_table()
    bm.verts.ensure_lookup_table()
    nbr = [[g.index for e in f.edges for g in e.link_faces if g is not f] for f in bm.faces]
    # A Voronoi growth with jittered steps: each piece spreads from its seed one ring at a
    # time, so pieces come out compact (a clear middle inside a torn rim) with ragged outlines.
    import heapq
    owner = [-1] * len(bm.faces)
    heap = [(0.0, k, i) for k, i in enumerate(rng.sample(range(len(bm.faces)), SHARD_PIECES))]
    heapq.heapify(heap)
    while heap:
        d, k, i = heapq.heappop(heap)
        if owner[i] >= 0:
            continue
        owner[i] = k
        for j in nbr[i]:
            if owner[j] < 0:
                heapq.heappush(heap, (d + .4 + rng.random() * 1.2, k, j))
    verts = [tuple(v.co.normalized()) for v in bm.verts]
    tris = [tuple(v.index for v in f.verts) for f in bm.faces]
    bm.free()
    return verts, tris, owner


def _shards(ctx, coll, centre, bubble, s, Rm, seed):
    """The overload's shards: the film breaking into ragged, curved pieces that follow the
    shell (a fine icosphere grown into irregular pieces, projected onto it). Every piece has
    its own vertices, a 'rim' weight (1 on its torn edge, 0 inside) for the shader's glowing
    edge, the 'rest' position it tore from and its own 'shard_rand'. A 'scatter' shape key,
    driven by shield_overload, lifts each piece off the shell, lets it drift and curl a
    little and shrinks it toward its middle as the plasma burns away."""
    import random
    from mathutils import Matrix, Vector
    from mathutils.bvhtree import BVHTree
    rng = random.Random(seed)
    me_b = bubble.data
    tree = BVHTree.FromPolygons([v.co.copy() for v in me_b.vertices], [tuple(p.vertices) for p in me_b.polygons])
    unit, tris, owner = _shard_pieces(rng)
    on_shell = []
    for dvec in unit:
        dvec = Vector(dvec)
        hit = tree.ray_cast(Vector((0, 0, 0)), dvec)
        on_shell.append(dvec * (hit[3] if hit[0] is not None else Rm))
    pieces = {}
    for t, k in zip(tris, owner):
        pieces.setdefault(k, []).append(t)
    verts, faces, rest, rim, rand, moved = [], [], [], [], [], []
    for k, ptris in pieces.items():
        count = {}
        for t in ptris:
            for a, b in ((t[0], t[1]), (t[1], t[2]), (t[2], t[0])):
                key = (min(a, b), max(a, b))
                count[key] = count.get(key, 0) + 1
        edge = {v for (a, b), n in count.items() if n == 1 for v in (a, b)}
        used = sorted({v for t in ptris for v in t})
        c = sum((on_shell[v] for v in used), Vector()) / len(used)
        r = rng.random()
        axis = Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1))).normalized()
        curl = Matrix.Rotation(math.radians(rng.uniform(5, 25)), 3, axis)
        side = Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1))) * .3
        shift = (c.normalized() + side) * (SHARD_LIFT * Rm * (.4 + r))
        index = {}
        for v in used:
            p = c + (on_shell[v] - c) * SHARD_GAP
            index[v] = len(verts)
            verts.append(tuple(p))
            rest.append(tuple(p))
            rim.append(1.0 if v in edge else 0.0)
            moved.append(tuple(c + shift + curl @ ((p - c) * SHARD_SHRINK)))
        for t in ptris:
            faces.append(tuple(index[v] for v in t))
            rand.append(r)
    me = bpy.data.meshes.new(f'{PREFIX} shards')
    me.from_pydata(verts, [], faces)
    me.update()
    for p in me.polygons:
        p.use_smooth = True
    a = me.attributes.new('rest', 'FLOAT_VECTOR', 'POINT')
    a.data.foreach_set('vector', [x for p in rest for x in p])
    a = me.attributes.new('rim', 'FLOAT', 'POINT')
    a.data.foreach_set('value', rim)
    a = me.attributes.new('shard_rand', 'FLOAT', 'FACE')
    a.data.foreach_set('value', rand)
    ob = bpy.data.objects.new(f'{PREFIX} shards', me)
    coll.objects.link(ob)
    swfx_core.attach(ob, centre)
    ob.visible_shadow = ob.visible_diffuse = ob.visible_glossy = False
    ob.shape_key_add(name='Basis')
    sk = ob.shape_key_add(name='scatter')
    sk.data.foreach_set('co', [x for p in moved for x in p])
    ctx.drive(sk, 'value', 'sqrt(min(1.0, shield_overload * 1.3))', ('shield_overload',))
    for ax in range(3):
        ctx.drive(ob, 'scale', '(shield_overload > 0.0001) * (shield_shards > 0.001) * 1.0',
                  ('shield_overload', 'shield_shards'), index=ax)
    me.materials.append(_shard_material(ctx, s, Rm))
    return ob


def _shard_material(ctx, s, Rm):
    """A torn piece of plasma film: its broken rim glows hot, its middle stays a thin mottled
    film in the shield's own colour, and as it ages it burns away through noise holes that
    open from within, each hole edged with a brief bright front."""
    m, nodes, links = swfx_core.node_material(f'{PREFIX} shard')
    g = _G(nodes, links)
    v = lambda name, expr, props: swfx_core.value_node(ctx, nodes, name, expr, props)  # noqa: E731
    rest = nodes.new('ShaderNodeAttribute')
    rest.attribute_name = 'rest'
    rnd = nodes.new('ShaderNodeAttribute')
    rnd.attribute_name = 'shard_rand'
    rimv = nodes.new('ShaderNodeAttribute')
    rimv.attribute_name = 'rim'
    t = v('raise', 'shield_t', ('shield_t',))
    cover = _arc_cover(ctx, g, nodes, links, rest.outputs['Vector'], s, g.op('MAXIMUM', t, 1.0))[0]
    clock = v('shield time', 'frame / 30.0', ())
    order = _dissolve_order(ctx, g, nodes, links, rest.outputs['Vector'],
                            _thin_noise(g, nodes, links, rest.outputs['Vector'], Rm, s['finish'], clock), Rm)
    tear_at = v('tear front', _tear_front(), ('shield_overload',))
    age = g.sub(tear_at, order)
    appear = g.clamp01(g.mul(age, 1.0 / .015))
    late = v('shard age', f'max(0.0, shield_overload - {TEAR_SPAN}) * 1.5', ('shield_overload',))
    # How far the piece has burnt: 0 just torn, 1 gone (sooner for some pieces than others).
    burnt = g.clamp01(g.sub(1.0, g.op('EXPONENT', g.mul(g.mul(g.add(g.op('MAXIMUM', age, 0.0), late), -SHARD_DECAY),
                                                          g.add(.5, rnd.outputs['Fac'])))))
    env = v('shard env', '(shield_overload > 0.0001) * shield_shards * shield_gain'
                         ' * max(0.0, 1.0 - max(0.0, shield_overload - 0.6) / 0.35)',
            ('shield_overload', 'shield_shards', 'shield_gain'))
    # Burning away: noise over where the film sat; a point survives while its noise beats the burn.
    bn = nodes.new('ShaderNodeTexNoise')
    bn.noise_dimensions = '4D'
    bn.inputs['Scale'].default_value = SHARD_BURN_SCALE / Rm
    bn.inputs['Detail'].default_value = 4.0
    bn.inputs['Roughness'].default_value = .6
    links.new(rest.outputs['Vector'], bn.inputs['Vector'])
    links.new(g.mul(rnd.outputs['Fac'], 17.0), bn.inputs['W'])
    # The rim burns last of all: a little ahead of the noise there.
    n = g.add(bn.outputs['Fac'], g.mul(rimv.outputs['Fac'], .12))
    gap = g.sub(n, g.add(g.mul(burnt, 1.05), .18))
    alive = g.clamp01(g.mul(gap, 14.0))
    front = g.op('EXPONENT', g.mul(g.mul(g.mul(gap, gap), -1.0), 1.0 / (.035 * .035)))
    # Glow: the torn rim hot, the inside a thin mottled film.
    rim_w = g.pow(rimv.outputs['Fac'], 1.5)
    film = g.add(.18, g.mul(g.clamp01(g.mul(g.sub(bn.outputs['Fac'], .35), 2.0)), .3))
    glow = g.add(g.mul(g.add(film, g.mul(rim_w, 1.6)), alive), g.mul(g.mul(front, g.sub(1.0, alive)), 1.0))
    strength = g.mul(g.mul(g.mul(g.mul(appear, glow), g.mul(cover, env)), g.sub(1.0, g.mul(burnt, .5))), SHARD_PEAK)
    # The rim runs hotter than the film but keeps the shield's tint: a whiter hot colour blows out.
    hot = tuple(min(1.0, c + .3) for c in s['inner'])
    colour = g.mix_rgb(g.clamp01(g.add(rim_w, front)), s['inner'], hot)
    _additive_multi(nodes, links, [(colour, strength)])
    return _blended(m)


def arc_material(ctx, name, colour, env_expr, props, peak=ARC_PEAK):
    """A crawling electric arc (the beam core's construction: a hot white core in a coloured
    fringe, noise sliding along it): strength peak x env_expr (an expression of control
    props), flickering on and off per object. Shared with the ion cannon's EMP arcs."""
    m, nodes, links = swfx_core.node_material(name)
    g = _G(nodes, links)
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    core = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 2.0)
    tc = nodes.new('ShaderNodeTexCoord')
    noise = nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = .6
    noise.inputs['Detail'].default_value = 3.0
    links.new(tc.outputs['Object'], noise.inputs['Vector'])
    clock = swfx_core.value_node(ctx, nodes, 'arc time', 'frame / 3.0')
    links.new(clock, noise.inputs['W'])
    crawl = g.clamp01(g.mul(g.sub(noise.outputs['Fac'], .38), 3.5))
    info = nodes.new('ShaderNodeObjectInfo')
    fl = nodes.new('ShaderNodeTexNoise')
    fl.noise_dimensions = '1D'
    fl.inputs['Scale'].default_value = 1.0
    links.new(g.add(g.mul(clock, 1.3), g.mul(info.outputs['Random'], 97.0)), fl.inputs['W'])
    flick = g.clamp01(g.mul(g.sub(fl.outputs['Fac'], .42), 12.0))
    env = swfx_core.value_node(ctx, nodes, 'arc env', env_expr, props)
    white = tuple(.8 + .2 * c for c in colour)
    col = g.mix_rgb(core, colour, white)
    strength = g.mul(g.mul(g.mul(g.add(.25, g.mul(core, .75)), g.add(.3, crawl)), g.mul(flick, env)), peak)
    _additive_multi(nodes, links, [(col, strength)])
    return _blended(m)


def arc_curve(name, coll, points, radius, material):
    """A jagged poly curve through points (local coordinates), a tube of radius."""
    cu = bpy.data.curves.new(name, 'CURVE')
    cu.dimensions = '3D'
    cu.bevel_depth = radius
    cu.bevel_resolution = 1
    sp = cu.splines.new('POLY')
    sp.points.add(len(points) - 1)
    for p, q in zip(sp.points, points):
        p.co = (*q, 1.0)
    cu.materials.append(material)
    ob = bpy.data.objects.new(name, cu)
    coll.objects.link(ob)
    ob.visible_shadow = ob.visible_diffuse = ob.visible_glossy = False
    return ob


def jag(points, rng, amount=.18, cuts=5):
    """points with every segment cut into `cuts` pieces kicked sideways by amount x its length."""
    from mathutils import Vector
    out = [Vector(points[0])]
    for a, b in zip(points, points[1:]):
        a, b = Vector(a), Vector(b)
        seg = b - a
        for i in range(1, cuts + 1):
            p = a + seg * (i / cuts)
            if i < cuts:
                p += Vector((rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1))) * amount * seg.length
            out.append(p)
    return [tuple(p) for p in out]


def _arcs(ctx, coll, centre, bubble, s, Rm, hull_local, node_local, seed):
    """Overload arcs: from points on the open arc of the film down to the hull, crawling
    over it (or, where the profile declares emitter nodes, snapping back to the nearest)."""
    import random
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree
    from mathutils.kdtree import KDTree
    rng = random.Random(seed)
    me_b = bubble.data
    tree = BVHTree.FromPolygons([v.co.copy() for v in me_b.vertices], [tuple(p.vertices) for p in me_b.polygons])
    sample = hull_local if len(hull_local) <= 6000 else [hull_local[i] for i in
                                                         sorted(rng.sample(range(len(hull_local)), 6000))]
    kd = KDTree(len(sample))
    for i, p in enumerate(sample):
        kd.insert(p, i)
    kd.balance()
    half = min(float(s.get('arc', 360)), 300.0) / 2 * .9
    colour = tuple(min(1.0, .45 + .55 * c) for c in s['inner'])
    env = ('shield_arcs * shield_gain * min(1.0, max(0.0, (shield_overload - 0.04) / 0.08))'
           ' * min(1.0, max(0.0, (0.8 - shield_overload) / 0.3))')
    mat = arc_material(ctx, f'{PREFIX} arc', colour, env, ('shield_arcs', 'shield_gain', 'shield_overload'))
    out = []
    for k in range(ARCS):
        az, el = math.radians(rng.uniform(-half, half)), math.radians(rng.uniform(-35, 35))
        dvec = Vector((math.cos(az) * math.cos(el), math.sin(az) * math.cos(el), math.sin(el)))
        hit = tree.ray_cast(Vector((0, 0, 0)), dvec)
        start = dvec * (hit[3] if hit[0] is not None else Rm)
        path = [start]
        if node_local:
            path.append(min(node_local, key=lambda p: (Vector(p) - start).length))
        else:
            cur = Vector(kd.find(start)[0])
            path.append(cur)
            for _ in range(3):
                near = [Vector(co) for co, i, dd in kd.find_range(cur, .16 * Rm) if dd > .05 * Rm]
                if not near:
                    break
                cur = near[rng.randrange(len(near))]
                path.append(cur)
        ob = arc_curve(f'{PREFIX} arc {k + 1}', coll, jag(path, rng), .006 * Rm, mat)
        swfx_core.attach(ob, centre)
        out.append(ob)
    return out


def _tear_light(ctx, coll, centre, bubble, s, Rm):
    """A light where the tear opens: on the film (projected onto the shell the way the hit
    empties are), just inside it, so it lights the hull beneath."""
    pivot = swfx_core.empty(f'{PREFIX} tear pivot', coll, Rm * .05, 'PLAIN_AXES')
    swfx_core.attach(pivot, centre)
    ctx.drive(pivot, 'rotation_euler', 'radians(shield_tear_az)', ('shield_tear_az',), index=2)
    ctx.drive(pivot, 'rotation_euler', '-radians(shield_tear_el)', ('shield_tear_el',), index=1)
    at = swfx_core.empty(f'{PREFIX} tear', coll, Rm * .05, 'SPHERE')
    swfx_core.attach(at, pivot, local=(3 * Rm * 4, 0, 0))
    sw = at.constraints.new('SHRINKWRAP')
    sw.target = bubble
    sw.shrinkwrap_type = 'PROJECT'
    sw.project_axis = 'NEG_X'
    sw.project_axis_space = 'LOCAL'
    sw.project_limit = 0.0
    lt = swfx_core.point_light(f'{PREFIX} tear light', coll, tuple(.5 + .5 * c for c in s['inner']), 0.0,
                               radius=.04 * Rm)
    lt.visible_camera = False
    swfx_core.attach(lt, at, local=(-.08 * Rm, 0, 0))
    ctx.drive(lt.data, 'energy', f'{TEAR_W:.0f} * shield_gain * shield_light * (shield_overload > 0.0001)'
                                 f' * max(0.0, 1.0 - shield_overload / 0.45)',
              ('shield_gain', 'shield_light', 'shield_overload'))
    return lt


def _heat_material(ctx):
    m, nodes, links = swfx_core.node_material(f'{PREFIX} vent heat')
    g = _G(nodes, links)
    tc = nodes.new('ShaderNodeTexCoord')
    noise = nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = .4
    links.new(tc.outputs['Object'], noise.inputs['Vector'])
    links.new(swfx_core.value_node(ctx, nodes, 'heat time', 'frame / 20.0'), noise.inputs['W'])
    env = swfx_core.value_node(ctx, nodes, 'vent env', 'shield_vent * shield_gain', ('shield_vent', 'shield_gain'))
    strength = g.mul(g.mul(env, g.add(.6, g.mul(noise.outputs['Fac'], .8))), VENT_PEAK)
    _additive_multi(nodes, links, [(HEAT, strength)])
    return _blended(m)


def _jet_material(ctx, length, radius):
    """Vapour shed off a hot vent: brightest at the vent, fading along the jet (+X), soft at
    its sides, streaked by noise that runs outward."""
    m, nodes, links = swfx_core.node_material(f'{PREFIX} vent jet')
    g = _G(nodes, links)
    tc = nodes.new('ShaderNodeTexCoord')
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(tc.outputs['Object'], sep.inputs[0])
    along = g.clamp01(g.mul(sep.outputs['X'], 1.0 / length))
    fade = g.mul(g.pow(g.sub(1.0, along), 1.6), g.clamp01(g.mul(along, 12.0)))
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    soft = g.pow(g.clamp01(g.sub(1.0, lw.outputs['Facing'])), 2.0)
    mp = nodes.new('ShaderNodeMapping')
    mp.inputs['Scale'].default_value = (.35 / radius, 1.0 / radius, 1.0 / radius)
    links.new(tc.outputs['Object'], mp.inputs['Vector'])
    noise = nodes.new('ShaderNodeTexNoise')
    noise.noise_dimensions = '4D'
    noise.inputs['Scale'].default_value = 1.0
    noise.inputs['Detail'].default_value = 3.0
    links.new(mp.outputs[0], noise.inputs['Vector'])
    links.new(swfx_core.value_node(ctx, nodes, 'jet time', 'frame / 8.0'), noise.inputs['W'])
    streak = g.add(.25, g.mul(g.clamp01(g.mul(g.sub(noise.outputs['Fac'], .35), 2.5)), 1.4))
    env = swfx_core.value_node(ctx, nodes, 'vent env', 'shield_vent * shield_gain', ('shield_vent', 'shield_gain'))
    strength = g.mul(g.mul(g.mul(g.mul(fade, soft), streak), env), JET_PEAK)
    colour = g.mix_rgb(g.pow(along, .5), HEAT, VAPOUR)
    _additive_multi(nodes, links, [(colour, strength)])
    return _blended(m)


def _cone(name, coll, length, r0, r1, material, sides=16, rings=8):
    me = bpy.data.meshes.new(name)
    verts, faces = [], []
    for j in range(rings + 1):
        f = j / rings
        r = r0 + (r1 - r0) * f
        for i in range(sides):
            a = 2 * math.pi * i / sides
            verts.append((length * f, r * math.cos(a), r * math.sin(a)))
    for j in range(rings):
        for i in range(sides):
            a, b = j * sides + i, j * sides + (i + 1) % sides
            faces.append((a, b, b + sides, a + sides))
    me.from_pydata(verts, [], faces)
    for p in me.polygons:
        p.use_smooth = True
    me.materials.append(material)
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    ob.visible_shadow = ob.visible_diffuse = ob.visible_glossy = False
    return ob


def _vents(ctx, coll, rig, find, s, Rm, hull_mid, record):
    """Each vent in SHIELD['vents'] (a hull part's name): its own surfaces glow with heat
    (a copy of the part, sharing its mesh, lifted a hair off it), a vapour jet off its open
    face (its thinnest axis, signed away from the hull's middle) and a warm light."""
    from mathutils import Vector
    heat = None
    for name in s.get('vents', ()):
        part = find(name)
        if part is None or part.type != 'MESH':
            record['missing'].append(name)
            continue
        heat = heat or _heat_material(ctx)
        glow = bpy.data.objects.new(f'{PREFIX} vent glow {name}', part.data)
        coll.objects.link(glow)
        glow.parent = part.parent
        glow.matrix_parent_inverse = part.matrix_parent_inverse.copy()
        glow.matrix_basis = part.matrix_basis.copy()
        for slot in glow.material_slots:
            slot.link = 'OBJECT'
            slot.material = heat
        lift = glow.modifiers.new('lift', 'DISPLACE')
        lift.mid_level, lift.strength = 0.0, .03
        glow.visible_shadow = glow.visible_diffuse = glow.visible_glossy = False
        mw = _mw(part)
        corners = [mw @ Vector(c) for c in part.bound_box]
        mid = sum(corners, Vector()) / 8
        axes = [mw.to_3x3().col[i] for i in range(3)]
        ext = [(max(c.dot(a.normalized()) for c in corners) - min(c.dot(a.normalized()) for c in corners))
               for a in axes]
        i_thin = min(range(3), key=lambda i: ext[i])
        out = axes[i_thin].normalized()
        if out.dot(mid - hull_mid) < 0:
            out = -out
        wide = sorted(ext)[1:]
        r0 = max(.02 * Rm, min(.1 * Rm, .4 * sum(wide) / 2))
        length = JET_LEN * Rm
        jet = _cone(f'{PREFIX} vent jet {name}', coll, length, r0, 2.4 * r0, _jet_material(ctx, length, r0))
        inv = _mw(rig).inverted()
        swfx_core.attach(jet, rig, local=tuple(inv @ (mid + out * .02 * Rm)),
                         rotation=tuple((inv.to_3x3() @ out).to_track_quat('X', 'Z').to_euler()))
        for ax in range(3):
            ctx.drive(jet, 'scale', '(shield_vent > 0.001) * 1.0', ('shield_vent',), index=ax)
        lt = swfx_core.point_light(f'{PREFIX} vent light {name}', coll, HEAT, 0.0, radius=r0)
        lt.visible_camera = False
        swfx_core.attach(lt, rig, local=tuple(inv @ (mid + out * .06 * Rm)))
        ctx.drive(lt.data, 'energy', f'{VENT_W:.0f} * shield_vent * shield_gain * shield_light',
                  ('shield_vent', 'shield_gain', 'shield_light'))
        record['vents'].append(name)


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


def _mw(ob):
    """An object's world matrix composed from its parent chain. A scene that places a build
    through collection instances keeps the build's own collection excluded, and Blender does
    not evaluate an excluded object, so its stored matrix_world can be stale. Composing it
    reads only the authored transforms (no constraints, which the hull parts a fit reads
    do not carry)."""
    m = ob.matrix_basis.copy()
    while ob.parent is not None:
        m = ob.parent.matrix_basis @ ob.matrix_parent_inverse @ m
        ob = ob.parent
    return m


def _base_name(name):
    """A copied or re-appended object's own name: 'Hull fin.002 @lasher-a' -> 'Hull fin'."""
    import re
    strip = lambda n: re.sub(r'\.\d{3}$', '', n)  # noqa: E731
    return strip(re.sub(r' ~?@[^@]*$', '', strip(name)))


def _finder(hull_collection):
    """name -> object: in the ship's own hull collection when one is given (a scene with
    several ships of one build), else by name in bpy.data (one hull per file)."""
    if hull_collection is None:
        return bpy.data.objects.get
    objs = list(hull_collection.all_objects)

    def find(name):
        return next((o for o in objs if o.name == name), None) or next(
            (o for o in objs if _base_name(o.name) == name), None)
    return find


def _hull_points(anchor, objects=None):
    """World positions of every mesh vertex of the ship the anchor belongs to (among
    `objects` when given, else every object in the file)."""
    import numpy as np
    root = anchor
    while root.parent:
        root = root.parent
    pts = []
    for o in (objects if objects is not None else bpy.data.objects):
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
        M = np.array(_mw(o))
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
    inv = np.array(_mw(centre).inverted())
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


def build(ctx, shield_scheme=None, shield_arc=None, shield_type=None, film=False, hull_collection=None,
          **options):
    coll = ctx.collection(GROUP)
    record = {'bubble': None, 'hits': [], 'nodes': [], 'lights': [], 'missing': []}
    if SHIELD is None or not ctx.spec.get('powered', True):
        return record
    s = resolve(SHIELD, shield_scheme, shield_arc, shield_type)
    find = _finder(hull_collection)
    anchor = find(s['anchor'])
    if anchor is None and hull_collection is not None and s['anchor'].endswith('| root'):
        # A profile shared by two builds (the Wolfs) names one build's root: the ship's own root.
        anchor = next((o for o in hull_collection.all_objects
                       if o.parent is None and _base_name(o.name).endswith('| root')), None)
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
    sc = _mw(anchor).to_scale()
    rig.scale = (1 / sc.x, 1 / sc.y, 1 / sc.z)
    # The bubble's centre: the anchor, or with a fit, the anchor in plan at the
    # hull's mid-height.
    fit = s.get('fit')
    centre = swfx_core.empty(f'{PREFIX} centre', coll, R * .1, 'PLAIN_AXES')
    swfx_core.attach(centre, rig)
    pts = None
    hull_objects = list(hull_collection.all_objects) if hull_collection is not None else None
    if fit or film:
        pts = _hull_points(anchor, hull_objects)
    if fit:
        zmid = .5 * (pts[:, 2].min() + pts[:, 2].max())
        centre.location = (0, 0, zmid - _mw(anchor).translation.z)
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
    if film:
        for name, default, lo, hi, desc in FILM_KNOBS:
            ctx.prop(name, default, lo, hi, desc)
        for k in range(1, HITS + 1):
            ctx.prop(f'shield_ring_{k}', 1.0, 0, 1, f'hit {k}: ripple ring phase (0 at impact, 1 spent)')
        ctx.set('shield_hit_size', FILM_HIT_SIZE)
        bubble.data.materials.append(_film_material(ctx, s, R, R))
    else:
        bubble.data.materials.append(_bubble_material(ctx, s, hits, R, facing))
    # The bubble lights nothing by itself (the node lights do): keeps it clean
    # of sampling noise on the hull.
    bubble.visible_diffuse = bubble.visible_glossy = False
    for ax in range(3):
        ctx.drive(bubble, 'scale', '(shield_t > 0.001) * 1.0', ('shield_t',), index=ax)
    record['bubble'] = bubble.name
    glow = _node_material(ctx, s)
    props = ('shield_t', 'shield_fortress', 'shield_gain', 'shield_light')
    node_objects = []
    for name in s.get('nodes', ()):
        n = find(name)
        if n is None:
            record['missing'].append(name)
            continue
        ns = _mw(n).to_scale()
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
        node_objects.append(n)
    if film:
        _build_film(ctx, coll, rig, centre, bubble, s, R, pts, node_objects, find, record)
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


def _build_film(ctx, coll, rig, centre, bubble, s, Rm, pts, node_objects, find, record):
    """The film's pieces beyond the bubble: shards, overload arcs, the tear light, vents."""
    import zlib
    import numpy as np
    from mathutils import Vector
    seed = zlib.crc32(s['anchor'].encode())
    inv = np.array(_mw(centre).inverted())
    hull_local = [tuple(p) for p in (pts @ inv[:3, :3].T + inv[:3, 3])]
    cinv = _mw(centre).inverted()
    node_local = [tuple(cinv @ _mw(n).translation) for n in node_objects]
    record['film'] = True
    record['vents'] = []
    record['shards'] = _shards(ctx, coll, centre, bubble, s, Rm, seed).name
    record['arcs'] = [o.name for o in _arcs(ctx, coll, centre, bubble, s, Rm, hull_local, node_local, seed + 1)]
    record['lights'].append(_tear_light(ctx, coll, centre, bubble, s, Rm).name)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    _vents(ctx, coll, rig, find, s, Rm, Vector(tuple((lo + hi) / 2)), record)


def _knob_at(ctx, name, frame):
    """A knob's keyed value at frame (its current value where it has no keys)."""
    fc = swfx_core.find_fcurve(ctx.controls, f'["{name}"]')
    return float(fc.evaluate(frame)) if fc else float(ctx.controls.get(name, 0.0))


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
    if f'shield_ring_{k}' in ctx.controls.keys():
        # A film shield: ripple ring k runs out over RING_S, longer as the flux climbs. A new
        # hit on k restarts its ring: the old ring's later keys go.
        flux = _knob_at(ctx, 'shield_flux', frame)
        fc = swfx_core.find_fcurve(ctx.controls, f'["shield_ring_{k}"]')
        if fc is not None:
            for kp in reversed([kp for kp in fc.keyframe_points if kp.co.x >= frame - 1]):
                fc.keyframe_points.remove(kp)
        spent = frame + max(2, round(RING_S * (1 + 1.5 * flux) * fps))
        ctx.key(f'shield_ring_{k}', frame - 1, 1.0, 'CONSTANT')
        ctx.key(f'shield_ring_{k}', frame, 0.0, 'LINEAR')
        ctx.key(f'shield_ring_{k}', spent, 1.0, 'CONSTANT')
        gone = max(gone, spent)
    return gone


def flux(ctx, frame, value, interpolation='LINEAR'):
    """Key the film's wear: shield_flux at frame (keys between are interpolated)."""
    ctx.key('shield_flux', frame, value, interpolation)


def overload(ctx, frame, azimuth, elevation=0.0, duration_s=1.5, fps=swfx_core.FPS):
    """The film collapses: the tear opens at (azimuth, elevation), the last hit, and runs
    the arc; the film shatters and arcs crawl over the hull, over duration_s. The ship's
    lamps stutter (lit_ship_master, where the build has it). The shield is down at the
    end; recover() brings it back. Returns the frame the shield is gone."""
    end = frame + max(2, round(duration_s * fps))
    ctx.key('shield_tear_az', frame, azimuth, 'CONSTANT')
    ctx.key('shield_tear_el', frame, elevation, 'CONSTANT')
    ctx.key('shield_overload', frame - 1, 0.0, 'LINEAR')
    ctx.key('shield_overload', frame, 0.0, 'LINEAR')
    ctx.key('shield_overload', end, 1.0, 'CONSTANT')
    t = _knob_at(ctx, 'shield_t', frame)
    ctx.key('shield_t', end - 1, t, 'CONSTANT')
    ctx.key('shield_t', end, 0.0, 'CONSTANT')
    if 'lit_ship_master' in ctx.controls.keys():
        base = _knob_at(ctx, 'lit_ship_master', frame)
        ctx.key('lit_ship_master', frame - 1, base, 'CONSTANT')
        for df, share in LAMP_STUTTER:
            ctx.key('lit_ship_master', frame + df, base * share, 'CONSTANT')
    return end


def recover(ctx, frame, fps=swfx_core.FPS):
    """After an overload: the emitters repaint the film from the facing outward (the
    raise). Returns the frame it is fully up."""
    ctx.key('shield_overload', frame - 1, 1.0, 'CONSTANT')
    ctx.key('shield_overload', frame, 0.0, 'CONSTANT')
    return raise_shield(ctx, frame, fps=fps)


def vent(ctx, frame, duration_s=3.0, fps=swfx_core.FPS):
    """Active vent: the film drops at once, the vents glow and shed vapour, and the flux
    bleeds to nothing over duration_s. Returns the frame the vent is over."""
    end = frame + max(4, round(duration_s * fps))
    t = _knob_at(ctx, 'shield_t', frame)
    if t > 0:
        ctx.key('shield_t', frame - 1, t, 'LINEAR')
        ctx.key('shield_t', frame + max(1, round(.25 * fps)), 0.0, 'LINEAR')
    ctx.key('shield_vent', frame - 1, 0.0, 'LINEAR')
    ctx.key('shield_vent', frame + max(1, round(.3 * fps)), 1.0, 'LINEAR')
    ctx.key('shield_vent', end - max(1, round(1.0 * fps)), 1.0, 'LINEAR')
    ctx.key('shield_vent', end, 0.0, 'LINEAR')
    ctx.key('shield_flux', frame, _knob_at(ctx, 'shield_flux', frame), 'LINEAR')
    ctx.key('shield_flux', end, 0.0, 'LINEAR')
    return end


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
