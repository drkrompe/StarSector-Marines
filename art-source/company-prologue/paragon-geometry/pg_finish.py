"""The Tri-Tachyon finish: hex ablative ceramic armour, ceramic machinery, and the bare substrate under the plates.

The owner chose the hex ablative character from study_armour.py (option B):
crowned hexagonal ceramic plates with dark seams, a skin that spalls plate by
plate under beam fire and is re-plated. It replaces the flat base materials of
the armour (skin, pale, white), so every part, cut face and dimple wears it.

Plate size differs by part (HEX_M, true metres): the big structural surfaces
carry big plates, the tight curves and small parts small ones, and every steep
face (bevels, flanks, walls) takes plates at STEEP_FACTOR of its part's size,
as a curved surface would need. The size reaches the shader through each
object's `hex_u` custom property (plate size in units).

Where the plates are left off, the substrate shows: `Tri-Tachyon substrate`,
the machined structure the plates are fixed to, which carries the hatches and
machinery (pg_service). Machinery is blue-black ceramic with pale ceramic caps.
Procedural and triplanar in object space; a texturing pass may replace it.
"""
import bpy
import pg_kit as K

HEX_M = {
    'Hull main ring': 4.0, 'Hull aft body': 3.6, 'Hull belt': 3.0, 'Hull port pod': 3.0, 'Hull starboard pod': 3.0,
    'Hull bow module': 2.6, 'Hull prow': 2.2, 'Hull port bow horn': 2.4, 'Hull starboard bow horn': 2.4,
    'Hull port bow lobe': 2.4, 'Hull starboard bow lobe': 2.4, 'Hull command deck': 2.2,
    'Hull command deck upper': 2.0, 'Hull command spine': 2.0, 'Bridge tower': 1.8,
}
DEFAULT_M = 2.6
WEAPON_M = 1.0   # the guns' housings take small plates
STEEP_FACTOR = .6
BASE = {'skin': (.40, .45, .50), 'pale': (.58, .62, .66), 'white': (.80, .81, .82)}


class N:
    def __init__(self, mat):
        mat.use_nodes = True
        self.nt = mat.node_tree
        self.nt.nodes.clear()

    def new(self, kind, **kw):
        n = self.nt.nodes.new(kind)
        for k, v in kw.items():
            setattr(n, k, v)
        return n

    def link(self, a, b):
        self.nt.links.new(a, b)

    def math(self, op, a, b=None):
        n = self.new('ShaderNodeMath', operation=op)
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                self.link(v, n.inputs[i])
        return n.outputs[0]


def _hex(n, v, size):
    """Hex plate fields for a 2D vector socket at a size socket: edge distance, crown, cell colour."""
    sep = n.new('ShaderNodeSeparateXYZ')
    n.link(v, sep.inputs[0])
    x = n.math('DIVIDE', sep.outputs[0], size)
    y = n.math('DIVIDE', sep.outputs[1], size)

    def wrap(t, period, shift):
        t = n.math('SUBTRACT', t, shift) if shift else t
        return n.math('SUBTRACT', n.math('SUBTRACT', t, n.math('MULTIPLY', n.math('FLOOR', n.math('DIVIDE', t, period)),
                                                                   period)), period/2)
    R = 1.7320508
    ax, ay = wrap(x, 1.0, 0), wrap(y, R, 0)
    bx, by = wrap(x, 1.0, .5), wrap(y, R, R/2)
    da = n.math('ADD', n.math('MULTIPLY', ax, ax), n.math('MULTIPLY', ay, ay))
    db = n.math('ADD', n.math('MULTIPLY', bx, bx), n.math('MULTIPLY', by, by))
    pick = n.math('LESS_THAN', da, db)

    def sel(a_, b_):
        return n.math('ADD', n.math('MULTIPLY', a_, pick), n.math('MULTIPLY', b_, n.math('SUBTRACT', 1.0, pick)))
    gx, gy = sel(ax, bx), sel(ay, by)
    d = n.math('MAXIMUM', n.math('ADD', n.math('MULTIPLY', n.math('ABSOLUTE', gx), .5),
                                 n.math('MULTIPLY', n.math('ABSOLUTE', gy), .8660254)), n.math('ABSOLUTE', gx))
    cid = n.new('ShaderNodeCombineXYZ')
    n.link(n.math('SUBTRACT', x, gx), cid.inputs[0])
    n.link(n.math('SUBTRACT', y, gy), cid.inputs[1])
    n.link(size, cid.inputs[2])
    wn = n.new('ShaderNodeTexWhiteNoise', noise_dimensions='3D')
    n.link(cid.outputs[0], wn.inputs['Vector'])
    return {'Edge': n.math('SUBTRACT', .5, d), 'Crown': n.math('SUBTRACT', 1.0, n.math('MULTIPLY', d, 2.0)),
            'Cell': wn.outputs['Color']}


def hex_ceramic(mat, role, default_u):
    n = N(mat)
    out = n.new('ShaderNodeOutputMaterial')
    bs = n.new('ShaderNodeBsdfPrincipled')
    n.link(bs.outputs[0], out.inputs['Surface'])
    base = BASE[role]
    attr = n.new('ShaderNodeAttribute', attribute_type='OBJECT', attribute_name='hex_u')
    size = n.math('ADD', attr.outputs['Fac'], n.math('MULTIPLY', n.math('LESS_THAN', attr.outputs['Fac'], .01), default_u))
    geo = n.new('ShaderNodeNewGeometry')
    nsep = n.new('ShaderNodeSeparateXYZ')
    n.link(geo.outputs['Normal'], nsep.inputs[0])
    steep = n.math('LESS_THAN', n.math('ABSOLUTE', nsep.outputs['Z']), .55)
    size = n.math('MULTIPLY', size, n.math('SUBTRACT', 1.0, n.math('MULTIPLY', steep, 1.0-STEEP_FACTOR)))
    tc = n.new('ShaderNodeTexCoord')
    sep = n.new('ShaderNodeSeparateXYZ')
    n.link(tc.outputs['Object'], sep.inputs[0])
    outs, ws = [], []
    for (a, b), axis in ((('Y', 'Z'), 'X'), (('X', 'Z'), 'Y'), (('X', 'Y'), 'Z')):
        cmb = n.new('ShaderNodeCombineXYZ')
        n.link(sep.outputs[a], cmb.inputs[0])
        n.link(sep.outputs[b], cmb.inputs[1])
        outs.append(_hex(n, cmb.outputs[0], size))
        ws.append(n.math('POWER', n.math('ABSOLUTE', nsep.outputs[axis]), 4.0))
    # Each point takes its dominant plane's plates outright: blending two planes'
    # hexes on a 45-degree face breaks the plates into fragments.
    big = n.math('GREATER_THAN', ws[2], n.math('MAXIMUM', ws[0], ws[1]))
    side = n.math('GREATER_THAN', ws[0], ws[1])
    one = [n.math('MULTIPLY', n.math('SUBTRACT', 1.0, big), side),
           n.math('MULTIPLY', n.math('SUBTRACT', 1.0, big), n.math('SUBTRACT', 1.0, side)), big]

    def blend(key):
        acc = None
        for o, w in zip(outs, one):
            t = n.math('MULTIPLY', o[key], w)
            acc = t if acc is None else n.math('ADD', acc, t)
        return acc
    c_side = n.new('ShaderNodeMix', data_type='RGBA')
    n.link(side, c_side.inputs['Factor'])
    n.link(outs[1]['Cell'], c_side.inputs['A'])
    n.link(outs[0]['Cell'], c_side.inputs['B'])
    cell = n.new('ShaderNodeMix', data_type='RGBA')
    n.link(big, cell.inputs['Factor'])
    n.link(c_side.outputs['Result'], cell.inputs['A'])
    n.link(outs[2]['Cell'], cell.inputs['B'])
    edge = blend('Edge')
    seam = n.math('LESS_THAN', edge, .03)
    height = n.math('MULTIPLY', n.math('SUBTRACT', 1.0, seam), n.math('ADD', .7, n.math('MULTIPLY', blend('Crown'), .3)))
    bump = n.new('ShaderNodeBump')
    n.link(height, bump.inputs['Height'])
    bump.inputs['Strength'].default_value = .5
    n.link(bump.outputs[0], bs.inputs['Normal'])
    var = n.new('ShaderNodeMix', data_type='RGBA', blend_type='OVERLAY')
    var.inputs['Factor'].default_value = .10
    var.inputs['A'].default_value = (*base, 1)
    n.link(cell.outputs['Result'], var.inputs['B'])
    dark = n.new('ShaderNodeMix', data_type='RGBA')
    n.link(seam, dark.inputs['Factor'])
    n.link(var.outputs['Result'], dark.inputs['A'])
    dark.inputs['B'].default_value = (*[c*.35 for c in base], 1)
    n.link(dark.outputs['Result'], bs.inputs['Base Color'])
    bs.inputs['Roughness'].default_value = .56
    bs.inputs['Coat Weight'].default_value = .15


def plain(mat, colour, metallic, rough, coat=0.0):
    bs = mat.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value = (*colour, 1)
    bs.inputs['Metallic'].default_value = metallic
    bs.inputs['Roughness'].default_value = rough
    bs.inputs['Coat Weight'].default_value = coat
    mat.diffuse_color = (*colour, 1)


def apply(kit):
    """Rebuild the kit's armour and machinery materials in the chosen finish; add substrate and hatch."""
    default_u = DEFAULT_M/kit.mpu
    hex_ceramic(kit.skin, 'skin', default_u)
    hex_ceramic(kit.pale, 'pale', default_u)
    hex_ceramic(kit.white, 'white', default_u)
    plain(kit.tech, (.02, .03, .05), .0, .45)
    plain(kit.slate, (.10, .12, .15), .0, .5)
    plain(kit.steel, (.62, .65, .68), .0, .5)
    kit.substrate = K.material('Tri-Tachyon substrate', (.15, .18, .22), .35, .42)
    # The bore's exposed machinery plating, in the sprite's mid and light greys.
    kit.bore_mid = K.material('Bore plating mid grey', (.20, .22, .25), .45, .45)
    kit.bore_light = K.material('Bore plating light grey', (.40, .42, .45), .35, .45)
    # The guns' blue-white armour (the weapon sprites' pale blue) and their red status lamps.
    kit.weapon_blue = K.material('Tri-Tachyon weapon blue plate', (.42, .56, .66), .3, .4, coat=.2)
    kit.glow_red = K.material('Weapon status lamp red', (.2, .02, .02), .0, .4, emission=(1.0, .15, .1), strength=6.0)
    kit.hatch = K.material('Tri-Tachyon hatch ceramic', (.72, .74, .77), .0, .32, coat=.3)


def assign_sizes(kit):
    for ob in bpy.context.scene.objects:
        if ob.type == 'MESH':
            m = HEX_M.get(ob.name, WEAPON_M if ob.name.startswith('Weapon ') else DEFAULT_M)
            ob['hex_u'] = m/kit.mpu
