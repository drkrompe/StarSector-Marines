"""State overlay: what a condition state adds on top of the base wear.

The base wear (`wear.py`, `apply_skin.py --wear <state>`, weights in
`wear.json`) gives every part skin and class swatch eight years in the belt:
abrasion, pitting, chalking, ore-dust grime, soot round the bells, neglect.
It deliberately leaves scar and repair materials alone. This overlay adds the
part of each state that the base wear cannot know about, from the state's own
scar statuses, scar sites and running engines, with one parameter file per
state (`overlay/<state>.json`). Every mask is ours; Codex output never
decides where wear goes.

Library, imported by the per-state export (second-wind-cinematic/second-wind-states/
export_state.py) after the meshes are baked. It reads two attributes the bake
writes: POINT `hull_rest` (vertex position in hull units at rest, +X bow, +Y
port, +Z dorsal; the bake runs after the scar Booleans, so cut vertices carry
true positions) and FACE `hull_normal` (face normal, hull space). Masks ride
the ship however a shot moves it.

One group per state, `SW overlay <state>`, chained after whatever feeds each
material's Principled BSDF (the base wear's output on skins and swatches).
Its `SW overlay master` value scales the whole overlay (0 = base wear only).
Per-material inputs come from its family (`family`): Paint, Age (how much of
fade/chips/grime reaches it; 0 on base materials, which the base wear owns),
Scorch (whether scar scorch lands on it; 0 on fresh replacement plate),
PaintOver and PaintColor.

Layers, in order:
1. paint-over (restored): one coat over the old surface, whose own luminance
   (base wear, chips, pits, plate breakup) still reads through it, bounded by
   detail_min/detail_max; a per-object mismatch makes plates disagree;
2-4. fade, chips, grime on scar and repair materials only (age-scaled), so
   salvage plates, welds, torn metal and backing age with the hull round them;
5. drive soot from the engines that ran in this state, darkening by
   multiplication so a studio's specular cannot wash it out;
6. scorch at scar sites, per status: a dark burn and a heat-tint annulus
   (`scorch.by_status.<status>`: weight, dark, tint, tint_amount, radius_scale).

Repair surfacing: flat damage and repair materials (salvage plates, shop
primer, repaint, torn metal, backing) first take the luminance detail of a
class swatch (textures/swatch-<class>.png) box-projected on `hull_rest`, per
family (`repair_detail`), so every face carries texture with no new generation.
"""
from pathlib import Path
import json
import bpy

HERE = Path(__file__).resolve().parent
PAL = json.loads((HERE / 'palette.json').read_text())['classes']
BASE_CLASS = {
    'Chalked cream armor': 'cream',
    'Oxide orange armor': 'oxide',
    'Engine collars': 'gunmetal',
    'Recess and machinery': 'dark',
    'Grapple arm pale grey': 'pale',
}
PAINT_CLASSES = ('cream', 'oxide', 'pale')
LUM = (0.2126, 0.7152, 0.0722)
STATES = ('derelict', 'patched', 'refit', 'restored')


def load_params(state):
    return json.loads((HERE / 'overlay' / f'{state}.json').read_text())


def srgb_to_linear(c):
    c = c / 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def class_colour(cls):
    """Linear RGB of a palette class mean (the coat a repaint aims at)."""
    srgb = PAL[cls].get('mean_srgb') or [128, 128, 128]
    return tuple(srgb_to_linear(c) for c in srgb)


# ---- material families -----------------------------------------------------
SKIP = ('light', 'Light', 'lamp', 'glazing', 'beacon', 'glow', 'heat', 'Scar dead lamp',
        'Scar unlit glazing')
STEEL = ('Scar spalled backing', 'Scar scorched plating', 'Scar recess dark', 'Scar fitting steel',
         'External cable run', 'Borrowed seal rubber')


def family(m):
    """(family, class, paint) for a material, or None to leave it alone.

    family keys the state's `age` table; class picks the swatch detail and the
    restored coat; paint says whether chips show bare metal and sun fades it."""
    name = m.name.split('.')[0] if m.name.count('.') and m.name.rsplit('.', 1)[1].isdigit() else m.name
    if m.get('skin_part') is not None:
        cls = m.get('skin_class')
        return 'base', cls, cls in PAINT_CLASSES
    if m.get('skin_swatch') is not None:
        cls = m.get('skin_swatch')
        return 'base', cls, cls in PAINT_CLASSES
    if name in BASE_CLASS:
        cls = BASE_CLASS[name]
        return 'base', cls, cls in PAINT_CLASSES
    if name.startswith('Scar repaint over '):
        return 'repaint', BASE_CLASS.get(name[len('Scar repaint over '):], 'gunmetal'), True
    if name.startswith('Scar torn '):
        cls = BASE_CLASS.get(name[len('Scar torn '):], 'gunmetal')
        return 'torn', cls, cls in PAINT_CLASSES
    if any(k in name for k in SKIP):
        return None
    if name in STEEL:
        return 'steel', 'dark' if 'dark' in name or 'backing' in name else 'gunmetal', False
    if name == 'Salvage plate bare steel' or name == 'Salvaged drive coil copper':
        return 'salvage', 'gunmetal', False
    if name.startswith('Salvage plate') or name == 'Cargo rail safety yellow':
        return 'salvage', 'cream' if 'cream' in name else 'gunmetal', True
    if name == 'Replacement plate fastener':
        return 'replacement', 'gunmetal', False
    if name.startswith('Replacement'):
        return 'replacement', 'gunmetal', True
    if name == 'Weld bead':
        return 'weld', 'gunmetal', False
    return None


# ---- node builder ----------------------------------------------------------
class NB:
    """Terse shader-node construction inside one node tree."""
    def __init__(self, nt):
        self.nt, self.n = nt, nt.nodes

    def link(self, src, dst):
        if isinstance(src, (int, float)):
            dst.default_value = src
        elif isinstance(src, tuple):
            dst.default_value = src if len(dst.default_value) == len(src) else (*src, 1)[:len(dst.default_value)]
        else:
            self.nt.links.new(src, dst)

    def math(self, op, a, b=0.0, clamp=False):
        nd = self.n.new('ShaderNodeMath')
        nd.operation, nd.use_clamp = op, clamp
        self.link(a, nd.inputs[0])
        self.link(b, nd.inputs[1])
        return nd.outputs[0]

    def add(self, a, b, clamp=False): return self.math('ADD', a, b, clamp)
    def mul(self, a, b, clamp=False): return self.math('MULTIPLY', a, b, clamp)
    def sub(self, a, b, clamp=False): return self.math('SUBTRACT', a, b, clamp)
    def mx(self, a, b): return self.math('MAXIMUM', a, b)

    def vmath(self, op, a, b=(0, 0, 0)):
        nd = self.n.new('ShaderNodeVectorMath')
        nd.operation = op
        self.link(a, nd.inputs[0])
        self.link(b, nd.inputs[1])
        return nd.outputs['Value'] if op in ('DOT_PRODUCT', 'LENGTH', 'DISTANCE') else nd.outputs['Vector']

    def smooth(self, x, lo, hi):
        nd = self.n.new('ShaderNodeMapRange')
        nd.interpolation_type = 'SMOOTHSTEP'
        self.link(x, nd.inputs['Value'])
        self.link(lo, nd.inputs['From Min'])
        self.link(hi, nd.inputs['From Max'])
        return nd.outputs['Result']

    def xyz(self, v):
        nd = self.n.new('ShaderNodeSeparateXYZ')
        self.link(v, nd.inputs[0])
        return nd.outputs

    def combine(self, x, y, z):
        nd = self.n.new('ShaderNodeCombineXYZ')
        for s, v in zip(nd.inputs, (x, y, z)):
            self.link(v, s)
        return nd.outputs[0]

    def noise(self, vec, scale, detail=3.0, rough=0.55):
        nd = self.n.new('ShaderNodeTexNoise')
        nd.inputs['Scale'].default_value = scale
        nd.inputs['Detail'].default_value = detail
        nd.inputs['Roughness'].default_value = rough
        self.link(vec, nd.inputs['Vector'])
        return nd.outputs['Fac']

    def mix(self, fac, a, b):
        nd = self.n.new('ShaderNodeMix')
        nd.data_type = 'RGBA'
        nd.clamp_factor = True
        self.link(fac, nd.inputs['Factor'])
        self.link(a, nd.inputs['A'])
        self.link(b, nd.inputs['B'])
        return nd.outputs['Result']

    def fmix(self, fac, a, b):
        nd = self.n.new('ShaderNodeMix')
        nd.data_type = 'FLOAT'
        nd.clamp_factor = True
        self.link(fac, nd.inputs['Factor'])
        self.link(a, nd.inputs['A'])
        self.link(b, nd.inputs['B'])
        return nd.outputs['Result']

    def cmul(self, a, b):
        nd = self.n.new('ShaderNodeMix')
        nd.data_type, nd.blend_type = 'RGBA', 'MULTIPLY'
        nd.inputs['Factor'].default_value = 1
        self.link(a, nd.inputs['A'])
        self.link(b, nd.inputs['B'])
        return nd.outputs['Result']

    def value(self, v, name=None):
        nd = self.n.new('ShaderNodeValue')
        nd.outputs[0].default_value = v
        if name:
            nd.name = nd.label = name
        return nd.outputs[0]

    def attr(self, name):
        nd = self.n.new('ShaderNodeAttribute')
        nd.attribute_type, nd.attribute_name = 'GEOMETRY', name
        return nd.outputs['Vector']

    def lum(self, c):
        return self.vmath('DOT_PRODUCT', c, LUM)


def soot_points(params, anchors):
    """[(mouth x, y, radius, amount)] for engines with soot in this state."""
    s = params.get('soot', {})
    out = []
    for e in anchors:
        amt = s.get('amount', 0) * s.get('engines', {}).get(str(e['index']), 0)
        if amt > 0:
            out.append((e['mouth'][0], e['mouth'][1], e['mouth'][2], e['radius'], amt))
    return out


def scorch_points(params, scars, sites):
    """[(p, radius, dark, tint_amount, tint)] from the state's scar statuses
    and the site record. Per status: `scorch.by_status.<status>` =
    {weight, dark, tint_amount, tint, radius_scale}; a status not listed gets
    no scorch. A site's own radius wins over default_radius when the record
    holds the same point twice."""
    s = params.get('scorch', {})
    if not s.get('amount'):
        return []
    by_status, by_kind = s.get('by_status', {}), s.get('kinds', {})
    default_r = s.get('default_radius', 2.0)
    pts = {}
    for row in scars:
        st = by_status.get(row['status'])
        if not st:
            continue
        w = st.get('weight', 1) * by_kind.get(row['kind'], 1) * s['amount']
        if w <= 0:
            continue
        for p in sites.get(row['id'], []):
            key = tuple(round(c * 2) for c in p['p'])      # half-unit cells
            r = p.get('r', default_r) * st.get('radius_scale', s.get('radius_scale', 1.4))
            if key in pts and 'r' not in p:
                continue
            pts[key] = (p['p'], r, w * st.get('dark', 1.0), w * st.get('tint_amount', 0.0),
                        tuple(st.get('tint', s.get('tint', (.2, .14, .08)))))
    return list(pts.values())


def build_group(state, params, anchors, scars, sites):
    """The state's wear group. Constants are baked in from params; one
    `SW overlay master` value node scales the whole overlay (0 = base wear only)."""
    name = f'SW overlay {state}'
    if name in bpy.data.node_groups:
        bpy.data.node_groups.remove(bpy.data.node_groups[name])
    g = bpy.data.node_groups.new(name, 'ShaderNodeTree')
    io = g.interface
    for nm, kind, dv in (('Color', 'NodeSocketColor', None), ('Roughness', 'NodeSocketFloat', .5),
                         ('Metallic', 'NodeSocketFloat', 0.), ('Paint', 'NodeSocketFloat', 1.),
                         ('Age', 'NodeSocketFloat', 1.), ('PaintOver', 'NodeSocketFloat', 0.),
                         ('PaintColor', 'NodeSocketColor', None), ('Scorch', 'NodeSocketFloat', 1.)):
        sock = io.new_socket(nm, in_out='INPUT', socket_type=kind)
        if dv is not None:
            sock.default_value = dv
    for nm, kind in (('Color', 'NodeSocketColor'), ('Roughness', 'NodeSocketFloat'),
                     ('Metallic', 'NodeSocketFloat')):
        io.new_socket(nm, in_out='OUTPUT', socket_type=kind)
    b = NB(g)
    gi, go = b.n.new('NodeGroupInput'), b.n.new('NodeGroupOutput')
    c, rough, metal = gi.outputs['Color'], gi.outputs['Roughness'], gi.outputs['Metallic']
    paint, age = gi.outputs['Paint'], gi.outputs['Age']
    master = b.value(params.get('master', 1.0), 'SW overlay master')
    age = b.mul(age, master)
    P, N = b.attr('hull_rest'), b.attr('hull_normal')
    Px, Py, Pz = b.xyz(P)
    Nz = b.xyz(N)[2]

    # 1. Paint-over (restored).
    po = params.get('paint_over', {})
    if po.get('amount', 0) > 0:
        ratio = b.math('DIVIDE', b.lum(c), b.mx(b.lum(gi.outputs['PaintColor']), .002))
        detail = b.math('POWER', b.math('MAXIMUM', ratio, .05), po.get('detail', .45))
        # The coat fills and levels: old dark marks (chips, rust stars) read as
        # a soft tone shift rather than stains, and bright bare-metal scratches
        # are mostly buried. detail_min / detail_max bound that through-show.
        detail = b.math('MINIMUM', b.mx(detail, po.get('detail_min', .35)), po.get('detail_max', 1.8))
        info = b.n.new('ShaderNodeObjectInfo')
        jitter = b.add(1.0, b.mul(b.sub(info.outputs['Random'], .5), 2 * po.get('mismatch', .05)))
        # class_scale: the coat's tone per class relative to the class mean
        # (e.g. collars take a darker heat enamel), matched on PaintColor.
        tone = 1.0
        for cls, s in po.get('class_scale', {}).items():
            hit = b.math('LESS_THAN', b.vmath('DISTANCE', gi.outputs['PaintColor'], class_colour(cls)), .002)
            tone = b.add(tone, b.mul(hit, s - 1.0))
        coat = b.cmul(gi.outputs['PaintColor'], b.combine(*(b.mul(b.mul(detail, jitter), tone),) * 3))
        f = b.mul(b.mul(gi.outputs['PaintOver'], po['amount']), master)
        c = b.mix(f, c, coat)
        rough = b.fmix(f, rough, po.get('roughness', .5))
        metal = b.fmix(f, metal, 0.0)

    # 2. Paint fade: sun side, painted surfaces.
    fd = params.get('fade', {})
    if fd.get('amount', 0) > 0:
        sun = b.smooth(Nz, -.2, .9)
        brk = b.add(.55, b.mul(b.noise(P, fd.get('scale', .08), 3), .45))
        f = b.mul(b.mul(b.mul(sun, brk), b.mul(paint, fd['amount'])), age, clamp=True)
        grey = b.lum(c)
        washed = b.mix(fd.get('desaturate', .5), c, b.combine(grey, grey, grey))
        washed = b.cmul(washed, b.combine(*(fd.get('lighten', 1.2),) * 3))
        c = b.mix(f, c, washed)
        rough = b.fmix(f, rough, .8)

    # 3. Chips on convex edges.
    ch = params.get('chips', {})
    if ch.get('density', 0) > 0:
        bev = b.n.new('ShaderNodeBevel')
        bev.samples = 8
        bev.inputs['Radius'].default_value = ch.get('radius', .12)
        geo = b.n.new('ShaderNodeNewGeometry')
        edge = b.sub(1.0, b.vmath('DOT_PRODUCT', bev.outputs['Normal'], geo.outputs['Normal']))
        edge = b.smooth(edge, .002, .06)
        n = b.noise(P, ch.get('scale', 1.6), 6, .65)
        d = b.mul(ch['density'], age, clamp=True)
        score = b.add(b.mul(edge, ch.get('edge', .55)), n)
        thr = b.sub(1.3, b.mul(d, .75))
        chip = b.mul(b.smooth(score, thr, b.add(thr, .05)), b.smooth(d, 0, .05))
        bare = b.mix(paint, b.cmul(c, (1.7, 1.7, 1.7)), ch.get('steel', (.11, .11, .105)))
        c = b.mix(chip, c, bare)
        rough = b.fmix(chip, rough, .38)
        metal = b.fmix(b.mul(chip, paint), metal, .7)

    # 4. Grime: cavity, underside, blotches.
    gr = params.get('grime', {})
    if gr.get('amount', 0) > 0:
        ao = b.n.new('ShaderNodeAmbientOcclusion')
        ao.samples = 8
        ao.only_local = False
        ao.inputs['Distance'].default_value = gr.get('distance', 1.5)
        cav = b.sub(1.0, ao.outputs['AO'])
        under = b.smooth(b.mul(Nz, -1), 0, .8)
        blot = b.smooth(b.noise(P, gr.get('blotch_scale', .12), 4), .45, .78)
        g_ = b.add(b.add(b.mul(cav, gr.get('cavity', 1)), b.mul(under, gr.get('under', .4))),
                   b.mul(blot, gr.get('blotch', .4)))
        g_ = b.mul(b.mul(g_, gr['amount']), age, clamp=True)
        c = b.mix(g_, c, b.cmul(c, tuple(gr.get('color', (.42, .36, .3)))))
        rough = b.fmix(g_, rough, .85)

    # 5. Drive soot from the engines that ran: multiplies the albedo down
    # (a mix toward a constant came out no darker than the gunmetal it
    # replaced under a studio's specular), raises roughness, keeps some metal.
    sp = soot_points(params, anchors)
    if sp:
        so = params['soot']
        streak = b.noise(b.combine(b.mul(Px, .12), b.mul(Py, 1.4), b.mul(Pz, 1.4)), 1.0, 4)
        tot = 0.0
        for x0, y0, z0, r, amt in sp:
            rad = b.vmath('LENGTH', b.combine(b.sub(Py, y0), b.sub(Pz, z0), 0))
            ring = b.sub(1.0, b.smooth(rad, r * so.get('inner', .9), r * so.get('outer', 2.0)))
            along = b.sub(1.0, b.smooth(b.sub(Px, x0), 0, so.get('reach', 7.0)))
            tot = b.add(tot, b.mul(b.mul(ring, along), amt))
        s = b.mul(b.mul(tot, b.add(.45, b.mul(streak, .9))), master, clamp=True)
        c = b.mix(s, c, b.cmul(c, tuple(so.get('multiply', (.12, .11, .1)))))
        rough = b.fmix(s, rough, .9)
        metal = b.fmix(s, metal, b.mul(metal, .4))

    # 6. Scorch about scar sites: a dark burn (disc) and a heat-tint ring
    # (annulus), each weighted per status; gated per material by Scorch.
    pts = scorch_points(params, scars, sites)
    if pts:
        sc = params['scorch']
        brk = b.add(.5, b.mul(b.noise(P, sc.get('scale', .9), 4), .7))
        gate = b.mul(gi.outputs['Scorch'], master)
        dark = 0.0
        tints = {}
        for p, r, wd, wt, tint in pts:
            dist = b.vmath('DISTANCE', P, tuple(p))
            disc = b.sub(1.0, b.smooth(dist, r * .25, r))
            if wd > 0:
                dark = b.mx(dark, b.mul(disc, wd))
            if wt > 0:
                ann = b.mul(b.mul(disc, b.sub(1.0, disc)), 4 * wt)
                tints[tint] = b.mx(tints.get(tint, 0.0), ann)
        for tint, ring in tints.items():
            c = b.mix(b.mul(b.mul(ring, brk), gate, clamp=True), c, tint)
        if not isinstance(dark, float):
            d = b.mul(b.mul(dark, brk), gate, clamp=True)
            c = b.mix(b.smooth(d, .3, 1.0), c, tuple(sc.get('color', (.03, .026, .023))))
            rough = b.fmix(d, rough, .8)

    b.link(c, go.inputs['Color'])
    b.link(rough, go.inputs['Roughness'])
    b.link(metal, go.inputs['Metallic'])
    g['scorch_sites'] = len(pts)
    g['soot_engines'] = len(sp)
    return g


def detail_group(cls):
    """`SW repair detail <cls>`: colour times the class swatch's normalised
    luminance, box-projected on hull_rest at the swatch's own scale."""
    name = f'SW repair detail {cls}'
    if name in bpy.data.node_groups:
        return bpy.data.node_groups[name]
    import parts as P_  # the swatch scale lives with the skin
    g = bpy.data.node_groups.new(name, 'ShaderNodeTree')
    g.interface.new_socket('Color', in_out='INPUT', socket_type='NodeSocketColor')
    fs = g.interface.new_socket('Amount', in_out='INPUT', socket_type='NodeSocketFloat')
    fs.default_value = .7
    g.interface.new_socket('Color', in_out='OUTPUT', socket_type='NodeSocketColor')
    b = NB(g)
    gi, go = b.n.new('NodeGroupInput'), b.n.new('NodeGroupOutput')
    path = HERE / 'textures' / f'swatch-{cls}.png'
    img = bpy.data.images.get(f'Swatch {cls}')
    if img is None:
        img = bpy.data.images.load(str(path))
        img.name = f'Swatch {cls}'
        img.pack()
    tex = b.n.new('ShaderNodeTexImage')
    tex.image, tex.projection, tex.projection_blend, tex.extension = img, 'BOX', .3, 'REPEAT'
    b.link(b.vmath('MULTIPLY', b.attr('hull_rest'), (1 / P_.SWATCH_UNITS,) * 3), tex.inputs['Vector'])
    mean = max(.02, sum(a * w for a, w in zip(class_colour(cls), LUM)))
    ratio = b.math('DIVIDE', b.lum(tex.outputs['Color']), mean)
    detailed = b.cmul(gi.outputs['Color'], b.combine(ratio, ratio, ratio))
    b.link(b.mix(gi.outputs['Amount'], gi.outputs['Color'], detailed), go.inputs['Color'])
    return g


def _input_source(nt, sock):
    """The socket feeding sock, or its default value."""
    if sock.links:
        return sock.links[0].from_socket
    v = sock.default_value
    return tuple(v) if hasattr(v, '__len__') else v


def apply(state, params, anchors, scars, sites):
    """Insert the state's wear (and repair detail) into every material that
    has a family. Returns a per-family count for the manifest."""
    grp = build_group(state, params, anchors, scars, sites)
    ages = params.get('age', {})
    po = params.get('paint_over', {})
    po_classes = set(po.get('classes', PAINT_CLASSES))
    repair = params.get('repair_detail', .7)
    gates = dict({'replacement': 0.0}, **params.get('scorch_gate', {}))
    counts, skipped = {}, set()
    seen = set()
    for ob in bpy.data.objects:
        if ob.type != 'MESH':
            continue
        for slot in ob.material_slots:
            m = slot.material
            if m is None or m.name in seen or not m.use_nodes:
                continue
            seen.add(m.name)
            fam = family(m)
            bsdf = next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
            if fam is None or bsdf is None:
                skipped.add(m.name)
                continue
            fam_name, cls, paint = fam
            nt = m.node_tree
            nb = NB(nt)
            col = _input_source(nt, bsdf.inputs['Base Color'])
            amount = repair.get(fam_name, .7) if isinstance(repair, dict) else repair
            if fam_name != 'base' and amount > 0:
                dg = nt.nodes.new('ShaderNodeGroup')
                dg.node_tree = detail_group(cls)
                dg.inputs['Amount'].default_value = amount
                nb.link(col, dg.inputs['Color'])
                col = dg.outputs['Color']
            node = nt.nodes.new('ShaderNodeGroup')
            node.node_tree = grp
            node.name = node.label = 'SW overlay'
            nb.link(col, node.inputs['Color'])
            nb.link(_input_source(nt, bsdf.inputs['Roughness']), node.inputs['Roughness'])
            nb.link(_input_source(nt, bsdf.inputs['Metallic']), node.inputs['Metallic'])
            node.inputs['Paint'].default_value = 1.0 if paint else 0.0
            # Base skins and swatches carry the base wear already (wear.py).
            node.inputs['Age'].default_value = 0.0 if fam_name == 'base' else ages.get(fam_name, 1.0)
            node.inputs['Scorch'].default_value = gates.get(fam_name, 1.0)
            over = fam_name in ('repaint', 'replacement') or (fam_name == 'base' and cls in po_classes)
            node.inputs['PaintOver'].default_value = 1.0 if over else 0.0
            node.inputs['PaintColor'].default_value = (*class_colour(cls), 1)
            for out, inp in (('Color', 'Base Color'), ('Roughness', 'Roughness'), ('Metallic', 'Metallic')):
                nt.links.new(node.outputs[out], bsdf.inputs[inp])
            m['overlay_family'] = fam_name
            m['overlay_class'] = cls
            counts[fam_name] = counts.get(fam_name, 0) + 1
    return {'group': grp.name, 'materials': counts, 'untouched': sorted(skipped),
            'scorch_sites': grp['scorch_sites'], 'soot_engines': grp['soot_engines']}
