"""The state-driven wear layer: eight years in an asteroid belt, as masks we own.

Built by apply_skin.py (`--wear <state>`) on top of every part skin and class
swatch, never on scar or repair materials, which keep their own look so the
field repairs read as recent and improvised against the worn paint round them.
Everything here is procedural and placed by rules (second-wind-pipeline.md,
Layers: "Codex output never decides where wear goes").

Coordinates are the SHIP's, not the world's: position is the main hull
object's own space (a Texture Coordinate node pointed at it), and each base
mesh carries a baked face attribute `hull_nrm` (its normal in the hull frame).
So the pattern stays put when a cinematic parents, moves, turns or scales the
ship as a body, and runs continuously across object and part boundaries.
Edge (Bevel node) and recess (AO node) detection run in render space at radii
set for the model's own hull units; a cinematic that scales the ship scales
those radii too.

Layers, in order, each scaled by the state's weight in wear.json:
  neglect   per-panel cells (square, ~5.5 units): a few repainted in the wrong
            shade, a few replacement panels left in bare primer
  fade      UV/thermal chalking: paint lightens and desaturates toward chalk,
            strongest on dorsal (sun-side) panels, varying plate to plate
            (a baked per-face random, `panel_rand`, plus a weak 4-unit cell);
            each plate also takes its own soiling gain (0.68-1.10)
  abrasion  dust and micrometeorite sandblasting: bands stretched along the
            direction of travel (+X), densest on bow-facing and dorsal faces
            and on edges; paint wears to primer, then to bright bare steel
  pitting   micrometeorite pits (0.6-unit cells) and larger dings (3-unit
            cells): dark centres, bare rims, bump
  grime     ore dust packed into recesses (AO) and streaks trailing aft
  soot      engine soot round the drive bells and the stern, broken by noise
Colours are graded in the shared palette: palette.json `wear` holds primer,
bare steel, ore dust, soot and chalk in CIELAB, converted here to linear.
Paint classes (cream, oxide, pale) take every layer at full strength; gunmetal
skips primer and fades less; recess takes grime and soot only.
"""
from pathlib import Path
import hashlib
import json
import bpy
import variation as V
import hull as H

HERE = Path(__file__).resolve().parent
DATA = H.DIR  # the hull's folder: its tables, art and outputs (hull.py)
HULL = H.HULL_OBJECT
STATES = json.loads((DATA / 'wear.json').read_text())
WEAR_LAB = json.loads((DATA / 'palette.json').read_text())['wear']
# Paint response per palette class: (paint, fade, metal-reveal)
CLASS_RESPONSE = H.WEAR_RESPONSE
ENGINES = H.ENGINES  # drive bell centres, hull units (surface_hull.py)
STERN_SOOT = H.get('STERN_SOOT', (-22.0, -40.0))  # hull X where stern soot starts and is full


def colour(name):
    return (*V.lab_to_linear(WEAR_LAB[name]['lab']), 1.0)


class G:
    """Tiny node-building helper over one node tree."""

    def __init__(self, tree):
        self.t = tree

    def node(self, kind, **props):
        n = self.t.nodes.new(kind)
        for k, v in props.items():
            setattr(n, k, v)
        return n

    def link(self, a, b):
        self.t.links.new(a, b)

    def val(self, x):
        if isinstance(x, (int, float)):
            n = self.node('ShaderNodeValue')
            n.outputs[0].default_value = x
            return n.outputs[0]
        return x

    def math(self, op, a, b=None, c=None, clamp=False):
        n = self.node('ShaderNodeMath', operation=op, use_clamp=clamp)
        for i, x in enumerate((a, b, c)):
            if x is None:
                continue
            if isinstance(x, (int, float)):
                n.inputs[i].default_value = x
            else:
                self.link(x, n.inputs[i])
        return n.outputs[0]

    def smooth(self, x, lo, hi):
        n = self.node('ShaderNodeMapRange', interpolation_type='SMOOTHSTEP')
        self.link(x, n.inputs['Value'])
        n.inputs['From Min'].default_value = lo
        n.inputs['From Max'].default_value = hi
        return n.outputs['Result']

    def mix(self, fac, a, b, blend='MIX'):
        n = self.node('ShaderNodeMix', data_type='RGBA', blend_type=blend, clamp_factor=True)
        for sock, x in ((n.inputs['Factor'], fac), (n.inputs['A'], a), (n.inputs['B'], b)):
            if isinstance(x, tuple):
                sock.default_value = x
            elif isinstance(x, (int, float)):
                sock.default_value = x
            else:
                self.link(x, sock)
        return n.outputs['Result']

    def mixf(self, fac, a, b):
        n = self.node('ShaderNodeMix', data_type='FLOAT', clamp_factor=True)
        for sock, x in ((n.inputs['Factor'], fac), (n.inputs['A'], a), (n.inputs['B'], b)):
            if isinstance(x, (int, float)):
                sock.default_value = x
            else:
                self.link(x, sock)
        return n.outputs['Result']

    def scale(self, vec, s):
        n = self.node('ShaderNodeVectorMath', operation='MULTIPLY')
        self.link(vec, n.inputs[0])
        n.inputs[1].default_value = s
        return n.outputs[0]

    def noise(self, vec, scale, detail=6.0, rough=0.6):
        n = self.node('ShaderNodeTexNoise', noise_dimensions='3D')
        self.link(vec, n.inputs['Vector'])
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        # Blender's noise clusters in about 0.32..0.68; stretch it to 0..1 so the
        # thresholds below mean what they say.
        r = self.node('ShaderNodeMapRange', clamp=True)
        self.link(n.outputs['Fac'], r.inputs['Value'])
        r.inputs['From Min'].default_value = 0.32
        r.inputs['From Max'].default_value = 0.68
        return r.outputs['Result']

    def voronoi(self, vec, scale, metric='EUCLIDEAN'):
        n = self.node('ShaderNodeTexVoronoi', voronoi_dimensions='3D', distance=metric)
        self.link(vec, n.inputs['Vector'])
        n.inputs['Scale'].default_value = scale
        sep = self.node('ShaderNodeSeparateColor')
        self.link(n.outputs['Color'], sep.inputs[0])
        return n.outputs['Distance'], sep.outputs[0], sep.outputs[1], sep.outputs[2]


def bake_hull_frame(ob):
    """Store the hull-frame face normal on a base mesh (once). Face attributes
    survive the damage Booleans intact (each split face keeps its value);
    point attributes do not (a cutter's new vertices read zero), which is why
    position comes from the main hull's object space instead."""
    me = ob.data
    if 'hull_nrm' in me.attributes:
        return
    n3 = ob.matrix_world.to_3x3().inverted().transposed()
    nrm = me.attributes.new('hull_nrm', 'FLOAT_VECTOR', 'FACE')
    for p, a in zip(me.polygons, nrm.data):
        a.vector = (n3 @ p.normal).normalized()
    # One stable random per face (object name + face index): panel-to-panel
    # variation that follows the actual plates rather than an abstract cell grid.
    pr = me.attributes.new('panel_rand', 'FLOAT_COLOR', 'FACE')
    for p, a in zip(me.polygons, pr.data):
        h = hashlib.sha256(f'{ob.name}|{p.index}'.encode()).digest()
        a.color = (h[0] / 255, h[1] / 255, h[2] / 255, 1.0)


def group(state):
    """Node group 'SW wear <state>': (Color, Roughness, Metallic, Paint, Fade, Reveal) -> (Color, Roughness, Metallic, Normal)."""
    name = f'SW wear {state}'
    if name in bpy.data.node_groups:
        return bpy.data.node_groups[name]
    w = STATES[state]
    tree = bpy.data.node_groups.new(name, 'ShaderNodeTree')
    io = tree.interface
    for s, t in (('Color', 'NodeSocketColor'), ('Roughness', 'NodeSocketFloat'), ('Metallic', 'NodeSocketFloat'),
                 ('Paint', 'NodeSocketFloat'), ('Fade', 'NodeSocketFloat'), ('Reveal', 'NodeSocketFloat')):
        io.new_socket(s, in_out='INPUT', socket_type=t)
    for s, t in (('Color', 'NodeSocketColor'), ('Roughness', 'NodeSocketFloat'), ('Metallic', 'NodeSocketFloat'),
                 ('Normal', 'NodeSocketVector')):
        io.new_socket(s, in_out='OUTPUT', socket_type=t)
    g = G(tree)
    gi, go = g.node('NodeGroupInput'), g.node('NodeGroupOutput')
    col, rough, metal = gi.outputs['Color'], gi.outputs['Roughness'], gi.outputs['Metallic']
    paint, fade_k, reveal = gi.outputs['Paint'], gi.outputs['Fade'], gi.outputs['Reveal']
    tc = g.node('ShaderNodeTexCoord')
    tc.object = bpy.data.objects[HULL]
    P = tc.outputs['Object']
    Nn = g.node('ShaderNodeAttribute', attribute_type='GEOMETRY', attribute_name='hull_nrm').outputs['Vector']
    sep = g.node('ShaderNodeSeparateXYZ')
    g.link(Nn, sep.inputs[0])
    nx, nz = sep.outputs['X'], sep.outputs['Z']
    psep = g.node('ShaderNodeSeparateXYZ')
    g.link(P, psep.inputs[0])
    px = psep.outputs['X']

    travel = g.math('MAXIMUM', nx, 0.0)
    dorsal = g.math('MAXIMUM', nz, 0.0)
    expo = g.math('MULTIPLY_ADD', g.math('MAXIMUM', travel, g.math('MULTIPLY', dorsal, 0.75)), 0.75, 0.25)  # 0.25..1
    # Edges: the bevel-node normal against the true normal.
    bev = g.node('ShaderNodeBevel', samples=8)
    bev.inputs['Radius'].default_value = 0.35
    geo = g.node('ShaderNodeNewGeometry')
    dot = g.node('ShaderNodeVectorMath', operation='DOT_PRODUCT')
    g.link(bev.outputs['Normal'], dot.inputs[0])
    g.link(geo.outputs['True Normal'], dot.inputs[1])
    edge = g.math('MULTIPLY', g.math('SUBTRACT', 1.0, dot.outputs['Value']), 7.0, clamp=True)
    # Recesses.
    ao = g.node('ShaderNodeAmbientOcclusion', samples=8)
    ao.inputs['Distance'].default_value = 2.0
    recess = g.math('MULTIPLY', g.math('SUBTRACT', 1.0, ao.outputs['AO']), 1.8, clamp=True)

    # --- neglect: square panel cells, wrong-shade repaints and bare-primer replacements
    _, nr, ng, nb = g.voronoi(P, 0.18, 'CHEBYCHEV')  # Blender's spelling
    repaint = g.math('LESS_THAN', nr, w['neglect'] * 0.16)
    replaced = g.math('MULTIPLY', g.math('LESS_THAN', ng, w['neglect'] * 0.10), g.math('SUBTRACT', 1.0, repaint))
    wrong = g.mix(nb, (0.62, 0.55, 0.50, 1), (1.18, 1.05, 0.80, 1))  # darker-redder ... paler-yellower shade
    c = g.mix(g.math('MULTIPLY', repaint, paint), col, g.mix(1.0, col, wrong, 'MULTIPLY'))
    c = g.mix(g.math('MULTIPLY', replaced, g.math('MAXIMUM', paint, 0.4)), c, colour('primer'))
    fresh = g.math('MAXIMUM', repaint, replaced)  # patches were painted later: less fade and abrasion

    # --- fade / chalking, per panel and sun-side
    # Panel randoms: the face's own (baked), blended with a weak 4-unit cell so
    # one huge face (the main deck) still breaks up.
    pa = g.node('ShaderNodeAttribute', attribute_type='GEOMETRY', attribute_name='panel_rand')
    psp = g.node('ShaderNodeSeparateColor')
    g.link(pa.outputs['Color'], psp.inputs[0])
    _, vr, vg, _ = g.voronoi(P, 0.25, 'CHEBYCHEV')
    fr = g.mixf(0.3, psp.outputs[0], vr)
    fg = g.mixf(0.3, psp.outputs[1], vg)
    sun = g.math('MULTIPLY_ADD', dorsal, 0.6, 0.25)
    lowf = g.noise(P, 0.06, 2.0, 0.5)
    famt = g.math('ADD', g.math('MULTIPLY', sun, 0.7), g.math('MULTIPLY', g.math('SUBTRACT', fr, 0.45), 1.3))
    famt = g.math('MULTIPLY', w['fade'], g.math('ADD', famt, g.math('MULTIPLY', g.math('SUBTRACT', lowf, 0.5), 0.6)), clamp=True)
    famt = g.math('MULTIPLY', g.math('MULTIPLY', famt, fade_k), g.math('SUBTRACT', 1.0, g.math('MULTIPLY', fresh, 0.7)))
    chalk = g.mix(0.55, c, colour('chalk'))
    c = g.mix(g.math('MULTIPLY', famt, 0.7), c, chalk)
    # Panel-to-panel soiling: each cell its own darkness, the breakup that reads at distance.
    soil = g.math('MULTIPLY_ADD', fg, 0.42, 0.68)  # 0.68..1.10
    soil = g.mixf(g.math('MULTIPLY', w['grime'], g.math('MAXIMUM', paint, 0.6)), 1.0, soil)
    sc = g.node('ShaderNodeCombineColor')
    for k in range(3):
        g.link(soil, sc.inputs[k])
    c = g.mix(1.0, c, sc.outputs[0], 'MULTIPLY')

    # --- abrasion: travel-stretched bands, edges, fine chipping
    band = g.noise(g.scale(P, (0.35, 1.0, 1.0)), 0.55, 8.0, 0.62)
    fine = g.noise(P, 3.0, 4.0, 0.7)
    a_raw = g.math('MULTIPLY', w['abrasion'], g.math('ADD', g.math('MULTIPLY', expo, 0.7), g.math('MULTIPLY', edge, 0.75)))
    a_raw = g.math('ADD', a_raw, g.math('MULTIPLY', g.math('SUBTRACT', band, 0.5), 1.1))
    a_raw = g.math('ADD', a_raw, g.math('MULTIPLY', g.math('SUBTRACT', fine, 0.5), 0.45))
    a_raw = g.math('MULTIPLY', a_raw, g.math('SUBTRACT', 1.0, g.math('MULTIPLY', fresh, 0.6)))
    primer_m = g.math('MULTIPLY', g.smooth(a_raw, 0.74, 0.86), paint)
    bare_m = g.math('MULTIPLY', g.smooth(a_raw, 0.92, 1.02), reveal)
    c = g.mix(primer_m, c, colour('primer'))
    c = g.mix(bare_m, c, colour('bare'))

    # --- pitting and dings
    pd, pr, _, _ = g.voronoi(P, 1.6)
    pit = g.math('MULTIPLY', g.math('SUBTRACT', 1.0, g.smooth(pd, 0.07, 0.20)),
                 g.math('LESS_THAN', pr, g.math('MULTIPLY', expo, w['pitting'] * 0.85)))
    dd, dr, _, _ = g.voronoi(P, 0.33)
    ding = g.math('MULTIPLY', g.math('SUBTRACT', 1.0, g.smooth(dd, 0.10, 0.30)), g.math('LESS_THAN', dr, w['pitting'] * 0.08))
    rim = g.math('MULTIPLY', g.math('SUBTRACT', 1.0, g.smooth(dd, 0.30, 0.42)), g.math('SUBTRACT', 1.0, ding))
    rim = g.math('MULTIPLY', rim, g.math('LESS_THAN', dr, w['pitting'] * 0.08))
    c = g.mix(g.math('MULTIPLY', rim, g.math('MULTIPLY', reveal, 0.5)), c, colour('bare'))
    hole = g.math('MAXIMUM', pit, ding)
    c = g.mix(g.math('MULTIPLY', hole, 0.75), c, g.mix(1.0, c, (0.35, 0.33, 0.32, 1), 'MULTIPLY'))

    # --- grime: ore dust in recesses, streaks trailing aft
    gn = g.noise(P, 0.45, 5.0, 0.6)
    streak = g.smooth(g.noise(g.scale(P, (0.07, 1.6, 1.6)), 0.9, 3.0, 0.55), 0.52, 0.70)
    gm = g.math('ADD', g.math('MULTIPLY', recess, 0.95), g.math('MULTIPLY', g.math('SUBTRACT', gn, 0.45), 0.6))
    gm = g.math('ADD', gm, g.math('MULTIPLY', streak, g.math('SUBTRACT', 0.55, g.math('MULTIPLY', dorsal, 0.3))))
    blot = g.smooth(g.noise(P, 0.11, 4.0, 0.55), 0.48, 0.72)
    gm = g.math('ADD', gm, g.math('MULTIPLY', blot, 0.55))
    gm = g.math('MULTIPLY', gm, w['grime'], clamp=True)
    c = g.mix(g.math('MULTIPLY', gm, 0.85), c, colour('dust'))

    # --- soot: drive bells and the stern
    near = None
    for e in ENGINES:
        d = g.node('ShaderNodeVectorMath', operation='DISTANCE')
        g.link(P, d.inputs[0])
        d.inputs[1].default_value = e
        n = g.math('SUBTRACT', 1.0, g.smooth(d.outputs['Value'], 4.0, 16.0))
        near = n if near is None else g.math('MAXIMUM', near, n)
    aft = g.smooth(px, *STERN_SOOT)
    sn = g.noise(P, 0.35, 6.0, 0.65)
    sm = g.math('MULTIPLY', g.math('MAXIMUM', g.math('MULTIPLY', aft, 0.55), near), g.math('MULTIPLY_ADD', sn, 1.2, 0.1))
    sm = g.math('MULTIPLY', sm, w['soot'], clamp=True)
    c = g.mix(g.math('MULTIPLY', sm, 0.85), c, colour('soot'))

    # Debug: SW_WEAR_DEBUG=<mask> outputs that mask as grey (see wear_debug.py).
    import os
    dbg = os.environ.get('SW_WEAR_DEBUG')
    masks = {'expo': expo, 'edge': edge, 'recess': recess, 'fresh': fresh, 'fade': famt, 'band': band, 'abrasion': a_raw,
             'primer': primer_m, 'bare': bare_m, 'pit': pit, 'ding': ding, 'grime': gm, 'soot': sm}
    if dbg in masks:
        cc = g.node('ShaderNodeCombineColor')
        for k in range(3):
            g.link(masks[dbg], cc.inputs[k])
        c = cc.outputs[0]

    # --- surface response
    r = g.math('ADD', rough, g.math('MULTIPLY', famt, 0.2))
    r = g.math('ADD', r, g.math('MULTIPLY', g.math('MAXIMUM', gm, sm), 0.2))
    r = g.mixf(bare_m, r, 0.38)
    m = g.mixf(bare_m, metal, 0.85)
    m = g.mixf(g.math('MAXIMUM', gm, g.math('MAXIMUM', sm, primer_m)), m, 0.15)
    bump = g.node('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.8
    bump.inputs['Distance'].default_value = 0.06
    g.link(g.math('SUBTRACT', 0.0, g.math('ADD', g.math('MULTIPLY', pit, 0.5), ding)), bump.inputs['Height'])
    g.link(c, go.inputs['Color'])
    g.link(g.math('MINIMUM', r, 1.0), go.inputs['Roughness'])
    g.link(m, go.inputs['Metallic'])
    g.link(bump.outputs['Normal'], go.inputs['Normal'])
    return tree


def insert(material, state, cls):
    """Route a skin or swatch material's base colour, roughness and metallic through the state's wear group."""
    nt = material.node_tree
    bsdf = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
    src = bsdf.inputs['Base Color'].links[0].from_socket
    wn = nt.nodes.new('ShaderNodeGroup')
    wn.name = 'SW wear'
    wn.node_tree = group(state)
    paint, fade, reveal = CLASS_RESPONSE.get(cls, (0.0, 0.3, 0.5))
    nt.links.new(src, wn.inputs['Color'])
    wn.inputs['Roughness'].default_value = bsdf.inputs['Roughness'].default_value
    wn.inputs['Metallic'].default_value = bsdf.inputs['Metallic'].default_value
    wn.inputs['Paint'].default_value = paint
    wn.inputs['Fade'].default_value = fade
    wn.inputs['Reveal'].default_value = reveal
    nt.links.new(wn.outputs['Color'], bsdf.inputs['Base Color'])
    nt.links.new(wn.outputs['Roughness'], bsdf.inputs['Roughness'])
    nt.links.new(wn.outputs['Metallic'], bsdf.inputs['Metallic'])
    nt.links.new(wn.outputs['Normal'], bsdf.inputs['Normal'])
    material['wear_state'] = state
