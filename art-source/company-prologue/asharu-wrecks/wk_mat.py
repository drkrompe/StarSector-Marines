"""Materials for the Asharu wreck kit (Blender). Four materials share one surface group, in the slot
order of wk_geom: PLATING, STRUCTURE, INTERIOR, BARE.

The look is long-dead hulls in hard vacuum sunlight: paint bleached and chalked toward a pale
grey-tan, pitted and streaked, edges scraped to metal, nothing lit. Each wreck (object) is
bleached its own amount and carries its own overall value; patches tens of metres across keep
their old paint, others are scorched dark, so the field has value contrast against the desert. Each face carries its own
painted colour (`wk_base`, the wreck's livery or a derived hull's own) and a tone offset
(`wk_tone`), so plates differ from their neighbours; the group bleaches the colour and lays the
ImageGen detail tiles over it in a box projection at metre scale, so a tile is the same size on
every wreck. Faces seen from behind are the hull's inside: dark.

Detail tiles (ImageGen masters in textures/generated/, made seamless by make_tiles.py into
build/asharu-wrecks/tiles/, packed into the published blend):
  plating_tile.png    sun-bleached pitted hull paint, flaking to primer and metal
  structure_tile.png  bare structural steel, oxidised, scorched, pitted
Missing tiles fall back to procedural noise, so the kit still builds without them."""
from pathlib import Path

import bpy

HERE = Path(__file__).resolve().parent
TILES = HERE.parents[2] / 'build' / 'asharu-wrecks' / 'tiles'      # make_tiles.py writes them; ignored
SLOTS = ('PLATING', 'STRUCTURE', 'INTERIOR', 'BARE')
TILE_M = {'plating': 10.0, 'structure': 3.0}     # metres one tile covers

# bleach toward this pale, dusty grey-tan, by this much, after desaturating by this much
BLEACH_TO, BLEACH, DESAT = (.40, .38, .34), .28, .32

# per slot: (rough, metal, detail tile, detail strength, colour gain)
SLOT_LOOK = {
    'PLATING': (.78, .15, 'plating', .55, 1.0),
    'STRUCTURE': (.62, .55, 'structure', .65, .42),
    'INTERIOR': (.9, .2, 'structure', .5, .14),
    'BARE': (.45, .85, 'structure', .4, 1.25),
}


def _tile(name):
    p = TILES / ('%s_tile.png' % name)
    if not p.exists():
        return None
    img = bpy.data.images.get(p.name) or bpy.data.images.load(str(p))
    img.colorspace_settings.name = 'Non-Color'     # a detail signal about .5, not a colour
    return img


def _group():
    g = bpy.data.node_groups.get('WK wreck surface')
    if g:
        return g
    g = bpy.data.node_groups.new('WK wreck surface', 'ShaderNodeTree')
    io = g.interface
    for name, typ in (('Rough', 'NodeSocketFloat'), ('Metal', 'NodeSocketFloat'), ('Detail', 'NodeSocketFloat'),
                      ('Gain', 'NodeSocketFloat'), ('Tile', 'NodeSocketColor')):
        io.new_socket(name, in_out='INPUT', socket_type=typ)
    io.new_socket('Shader', in_out='OUTPUT', socket_type='NodeSocketShader')
    n, l = g.nodes, g.links
    gi, go = n.new('NodeGroupInput'), n.new('NodeGroupOutput')
    base = n.new('ShaderNodeAttribute')
    base.attribute_type, base.attribute_name = 'GEOMETRY', 'wk_base'
    tone = n.new('ShaderNodeAttribute')
    tone.attribute_type, tone.attribute_name = 'GEOMETRY', 'wk_tone'
    # bleach: desaturate, then mix toward the dust tone
    hsv = n.new('ShaderNodeHueSaturation')
    hsv.inputs['Saturation'].default_value = 1 - DESAT
    l.new(base.outputs['Color'], hsv.inputs['Color'])
    bl = n.new('ShaderNodeMix')
    bl.data_type = 'RGBA'
    # each wreck bleached its own amount and carrying its own value: the field is not one tan
    oi = n.new('ShaderNodeObjectInfo')
    bf = n.new('ShaderNodeMapRange')
    bf.inputs['To Min'].default_value, bf.inputs['To Max'].default_value = BLEACH * .35, BLEACH * 1.5
    l.new(oi.outputs['Random'], bf.inputs['Value'])
    l.new(bf.outputs['Result'], bl.inputs['Factor'])
    bl.inputs['B'].default_value = (*BLEACH_TO, 1)
    l.new(hsv.outputs['Color'], bl.inputs['A'])
    # large-scale streaks and sun-fade: object-space noise at ~40 m
    tc = n.new('ShaderNodeTexCoord')
    big = n.new('ShaderNodeTexNoise')
    big.inputs['Scale'].default_value = .03
    big.inputs['Detail'].default_value = 4
    big.inputs['Roughness'].default_value = .6
    l.new(tc.outputs['Object'], big.inputs['Vector'])
    ramp = n.new('ShaderNodeMapRange')
    ramp.inputs['From Min'].default_value, ramp.inputs['From Max'].default_value = .3, .7
    ramp.inputs['To Min'].default_value, ramp.inputs['To Max'].default_value = .78, 1.12
    l.new(big.outputs['Fac'], ramp.inputs['Value'])
    # tone: plate-to-plate variety
    tmul = n.new('ShaderNodeMath')
    tmul.operation = 'MULTIPLY_ADD'
    tmul.inputs[1].default_value, tmul.inputs[2].default_value = .09, 1.0
    l.new(tone.outputs['Fac'], tmul.inputs[0])
    f1 = n.new('ShaderNodeMath')
    f1.operation = 'MULTIPLY'
    l.new(ramp.outputs['Result'], f1.inputs[0])
    l.new(tmul.outputs['Value'], f1.inputs[1])
    f2 = n.new('ShaderNodeMath')
    f2.operation = 'MULTIPLY'
    l.new(f1.outputs['Value'], f2.inputs[0])
    l.new(gi.outputs['Gain'], f2.inputs[1])
    col = n.new('ShaderNodeMix')
    col.data_type, col.blend_type = 'RGBA', 'MULTIPLY'
    col.inputs['Factor'].default_value = 1.0
    # surviving paint: patches tens of metres across where the livery held its colour
    oc = n.new('ShaderNodeTexCoord')
    pn = n.new('ShaderNodeTexNoise')
    pn.inputs['Scale'].default_value = .035
    pn.inputs['Detail'].default_value = 5
    pn.inputs['Roughness'].default_value = .65
    l.new(oc.outputs['Object'], pn.inputs['Vector'])
    pm = n.new('ShaderNodeMapRange')
    pm.inputs['From Min'].default_value, pm.inputs['From Max'].default_value = .56, .64
    l.new(pn.outputs['Fac'], pm.inputs['Value'])
    paint = n.new('ShaderNodeHueSaturation')
    paint.inputs['Saturation'].default_value = 1.1
    paint.inputs['Value'].default_value = .85
    l.new(base.outputs['Color'], paint.inputs['Color'])
    keep = n.new('ShaderNodeMix')
    keep.data_type = 'RGBA'
    l.new(pm.outputs['Result'], keep.inputs['Factor'])
    l.new(bl.outputs['Result'], keep.inputs['A'])
    l.new(paint.outputs['Color'], keep.inputs['B'])
    # scorch and soot: darker patches from old fires and the hits that killed her
    off = n.new('ShaderNodeVectorMath')
    off.operation = 'ADD'
    off.inputs[1].default_value = (311.0, -127.0, 53.0)
    l.new(oc.outputs['Object'], off.inputs[0])
    sn = n.new('ShaderNodeTexNoise')
    sn.inputs['Scale'].default_value = .05
    sn.inputs['Detail'].default_value = 6
    sn.inputs['Roughness'].default_value = .7
    l.new(off.outputs['Vector'], sn.inputs['Vector'])
    sm = n.new('ShaderNodeMapRange')
    sm.inputs['From Min'].default_value, sm.inputs['From Max'].default_value = .6, .72
    l.new(sn.outputs['Fac'], sm.inputs['Value'])
    scorch = n.new('ShaderNodeMix')
    scorch.data_type, scorch.blend_type = 'RGBA', 'MULTIPLY'
    l.new(sm.outputs['Result'], scorch.inputs['Factor'])
    l.new(keep.outputs['Result'], scorch.inputs['A'])
    scorch.inputs['B'].default_value = (.16, .13, .11, 1)
    # per wreck value: some hulls darker overall
    vm = n.new('ShaderNodeMapRange')
    vm.inputs['To Min'].default_value, vm.inputs['To Max'].default_value = .5, 1.05
    oi2 = n.new('ShaderNodeObjectInfo')
    wr = n.new('ShaderNodeMath')
    wr.operation = 'FRACT'
    wm = n.new('ShaderNodeMath')
    wm.operation = 'MULTIPLY'
    wm.inputs[1].default_value = 7.31
    l.new(oi2.outputs['Random'], wm.inputs[0])
    l.new(wm.outputs['Value'], wr.inputs[0])
    l.new(wr.outputs['Value'], vm.inputs['Value'])
    vmix = n.new('ShaderNodeMix')
    vmix.data_type, vmix.blend_type = 'RGBA', 'MULTIPLY'
    vmix.inputs['Factor'].default_value = 1.0
    vc = n.new('ShaderNodeCombineColor')
    for k in ('Red', 'Green', 'Blue'):
        l.new(vm.outputs['Result'], vc.inputs[k])
    l.new(scorch.outputs['Result'], vmix.inputs['A'])
    l.new(vc.outputs['Color'], vmix.inputs['B'])
    l.new(vmix.outputs['Result'], col.inputs['A'])
    comb = n.new('ShaderNodeCombineColor')
    for k in ('Red', 'Green', 'Blue'):
        l.new(f2.outputs['Value'], comb.inputs[k])
    l.new(comb.outputs['Color'], col.inputs['B'])
    # detail tile: overlay-ish multiply about its mean (the tile arrives normalised to mean .5)
    det = n.new('ShaderNodeMix')
    det.data_type, det.blend_type = 'RGBA', 'LINEAR_LIGHT'
    l.new(gi.outputs['Detail'], det.inputs['Factor'])
    l.new(col.outputs['Result'], det.inputs['A'])
    l.new(gi.outputs['Tile'], det.inputs['B'])
    # inside faces: dark
    geo = n.new('ShaderNodeNewGeometry')
    dark = n.new('ShaderNodeMix')
    dark.data_type = 'RGBA'
    l.new(geo.outputs['Backfacing'], dark.inputs['Factor'])
    l.new(det.outputs['Result'], dark.inputs['A'])
    dark.inputs['B'].default_value = (.035, .03, .026, 1)
    bsdf = n.new('ShaderNodeBsdfPrincipled')
    l.new(dark.outputs['Result'], bsdf.inputs['Base Color'])
    # roughness: the slot's, raised where the tile is dark (pits hold dust)
    tbw = n.new('ShaderNodeRGBToBW')
    l.new(gi.outputs['Tile'], tbw.inputs['Color'])
    rr = n.new('ShaderNodeMath')
    rr.operation = 'MULTIPLY_ADD'
    rr.inputs[1].default_value = -.35
    l.new(tbw.outputs['Val'], rr.inputs[0])
    l.new(gi.outputs['Rough'], rr.inputs[2])
    rc = n.new('ShaderNodeMath')
    rc.operation, rc.use_clamp = 'ADD', True
    rc.inputs[1].default_value = .17
    l.new(rr.outputs['Value'], rc.inputs[0])
    l.new(rc.outputs['Value'], bsdf.inputs['Roughness'])
    l.new(gi.outputs['Metal'], bsdf.inputs['Metallic'])
    l.new(bsdf.outputs['BSDF'], go.inputs['Shader'])
    return g


def _tile_node(nt, which):
    """A box-projected tile in object space at TILE_M metres, or noise when the tile is missing."""
    n, l = nt.nodes, nt.links
    tc = n.new('ShaderNodeTexCoord')
    mp = n.new('ShaderNodeMapping')
    s = 1.0 / TILE_M[which]
    mp.inputs['Scale'].default_value = (s, s, s)
    l.new(tc.outputs['Object'], mp.inputs['Vector'])
    img = _tile(which)
    if img is not None:
        t = n.new('ShaderNodeTexImage')
        t.image = img
        t.projection, t.projection_blend = 'BOX', .25
        t.interpolation = 'Cubic'
        l.new(mp.outputs['Vector'], t.inputs['Vector'])
        return t.outputs['Color']
    t = n.new('ShaderNodeTexNoise')
    t.inputs['Scale'].default_value = 6.0
    t.inputs['Detail'].default_value = 8
    l.new(mp.outputs['Vector'], t.inputs['Vector'])
    return t.outputs['Color']


def materials():
    """The four kit materials, in slot order."""
    out = []
    g = _group()
    for slot in SLOTS:
        name = 'WK %s' % slot.lower()
        m = bpy.data.materials.get(name)
        if m is None:
            m = bpy.data.materials.new(name)
            m.use_nodes = True
            nt = m.node_tree
            for node in list(nt.nodes):
                nt.nodes.remove(node)
            rough, metal, tile, detail, gain = SLOT_LOOK[slot]
            gn = nt.nodes.new('ShaderNodeGroup')
            gn.node_tree = g
            gn.inputs['Rough'].default_value = rough
            gn.inputs['Metal'].default_value = metal
            gn.inputs['Detail'].default_value = detail
            gn.inputs['Gain'].default_value = gain
            nt.links.new(_tile_node(nt, tile), gn.inputs['Tile'])
            o = nt.nodes.new('ShaderNodeOutputMaterial')
            nt.links.new(gn.outputs['Shader'], o.inputs['Surface'])
        out.append(m)
    return out
