"""Armour and machinery character study for the Tri-Tachyon hulls: three ceramic treatments side by side.

  sh ../../blender.sh --background <built paragon-geometry.blend> --python study_armour.py -- --out DIR [--samples N]
Owner (pass 3): find the faction's character for armour and machinery, "almost
like ceramic plating": a laser-heavy faction would armour against energy
weapons. Each option replaces the flat base materials of the built model with
a procedural ceramic (triplanar, so walls and decks tile alike) and a matching
machinery finish, and renders the same cameras, so only the character differs:

  A  thermal tiles      a fine grid of square ceramic tiles with dark gaps, like a
                        re-entry heat shield: it soaks and sheds heat. Machinery:
                        graphite with brushed-titanium housings.
  B  hex ablative       a regular grid of hexagonal ceramic plates with dark seams,
                        each crowned so one can spall away under fire and be
                        replaced. Machinery: blue-black ceramic, pale ceramic caps.
  C  refractive panels  big glazed ceramic panels with a thin-film sheen and
                        glowing seams, a skin that scatters and bleeds off beam
                        energy. Machinery: black glass with bright conduits.

Sizes are true metres, converted with the model's metres_per_unit. The study
never saves the .blend; the chosen option becomes pass 6's finish reference.
"""
import argparse
import json
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector

args = argparse.ArgumentParser()
args.add_argument('--out', required=True)
args.add_argument('--samples', type=int, default=32)
args.add_argument('--options', default='A,B,C')
args = args.parse_args(sys.argv[sys.argv.index('--')+1:])
OUT = Path(args.out)
OUT.mkdir(parents=True, exist_ok=True)
blend_dir = Path(bpy.data.filepath).parent
MPU = json.loads((blend_dir/'geometry.json').read_text())['metres_per_unit']
sc = bpy.context.scene
sc.cycles.samples = args.samples

ARMOUR = {'Tri-Tachyon blue-grey skin': 'skin', 'Tri-Tachyon pale skin': 'pale', 'Tri-Tachyon white core': 'white'}
MACHINE = {'Technical lining': 'tech', 'Tri-Tachyon slate band': 'slate', 'Emitter metal': 'steel'}
GLOWS = ('Tri-Tachyon blue glow', 'Tri-Tachyon white glow')


def u(m):
    return m/MPU


class N:
    """Small node-tree builder."""
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


def triplanar(n, tex_fn, sharp=4.0):
    """Sample tex_fn(vector socket) on the three axis planes in object space and blend by the normal."""
    tc = n.new('ShaderNodeTexCoord')
    sep = n.new('ShaderNodeSeparateXYZ')
    n.link(tc.outputs['Object'], sep.inputs[0])
    geo = n.new('ShaderNodeNewGeometry')
    nsep = n.new('ShaderNodeSeparateXYZ')
    n.link(geo.outputs['Normal'], nsep.inputs[0])
    outs, ws = [], []
    for (a, b), axis in ((('Y', 'Z'), 'X'), (('X', 'Z'), 'Y'), (('X', 'Y'), 'Z')):
        cmb = n.new('ShaderNodeCombineXYZ')
        n.link(sep.outputs[a], cmb.inputs[0])
        n.link(sep.outputs[b], cmb.inputs[1])
        outs.append(tex_fn(cmb.outputs[0]))
        ws.append(n.math('POWER', n.math('ABSOLUTE', nsep.outputs[axis]), sharp))
    total = n.math('ADD', n.math('ADD', ws[0], ws[1]), ws[2])
    def blend(key):
        acc = None
        for o, w in zip(outs, ws):
            term = n.math('MULTIPLY', o[key], n.math('DIVIDE', w, total))
            acc = term if acc is None else n.math('ADD', acc, term)
        return acc
    def blend_col(key):
        acc = None
        for o, w in zip(outs, ws):
            mix = n.new('ShaderNodeMix', data_type='RGBA', blend_type='MULTIPLY')
            mix.inputs['Factor'].default_value = 1.0
            n.link(o[key], mix.inputs['A'])
            f = n.math('DIVIDE', w, total)
            cmb = n.new('ShaderNodeCombineXYZ')
            for i in range(3):
                n.link(f, cmb.inputs[i])
            n.link(cmb.outputs[0], mix.inputs['B'])
            if acc is None:
                acc = mix.outputs['Result']
            else:
                add = n.new('ShaderNodeMix', data_type='RGBA', blend_type='ADD')
                add.inputs['Factor'].default_value = 1.0
                n.link(acc, add.inputs['A'])
                n.link(mix.outputs['Result'], add.inputs['B'])
                acc = add.outputs['Result']
        return acc
    return blend, blend_col


def ceramic(mat, option, role):
    """Rebuild one armour material as option's ceramic; role is skin / pale / white."""
    n = N(mat)
    out = n.new('ShaderNodeOutputMaterial')
    bs = n.new('ShaderNodeBsdfPrincipled')
    n.link(bs.outputs[0], out.inputs['Surface'])
    base = {'skin': (.40, .45, .50), 'pale': (.58, .62, .66), 'white': (.80, .81, .82)}[role]
    bump = n.new('ShaderNodeBump')
    n.link(bump.outputs[0], bs.inputs['Normal'])
    if option == 'A':
        tile = u(2.4) if role != 'white' else u(3.2)
        def tex(v):
            b = n.new('ShaderNodeTexBrick', offset=0.0, squash=1.0)
            b.inputs['Scale'].default_value = 1.0/tile
            b.inputs['Mortar Size'].default_value = .025
            b.inputs['Mortar Smooth'].default_value = .3
            b.inputs['Bias'].default_value = 0.0
            b.inputs['Brick Width'].default_value = 1.0
            b.inputs['Row Height'].default_value = 1.0
            b.inputs['Color1'].default_value = (*[c*1.08 for c in base], 1)
            b.inputs['Color2'].default_value = (*[c*.90 for c in base], 1)
            b.inputs['Mortar'].default_value = (*[c*.45 for c in base], 1)
            n.link(v, b.inputs['Vector'])
            return {'Color': b.outputs['Color'], 'Fac': b.outputs['Fac']}
        blend, blend_col = triplanar(n, tex)
        n.link(blend_col('Color'), bs.inputs['Base Color'])
        n.link(n.math('SUBTRACT', 1.0, blend('Fac')), bump.inputs['Height'])
        bump.inputs['Strength'].default_value = .5
        bs.inputs['Roughness'].default_value = .62
        bs.inputs['Metallic'].default_value = .0
    elif option == 'B':
        cell = u(3.5) if role != 'white' else u(4.5)
        def tex(v):
            sep = n.new('ShaderNodeSeparateXYZ')
            n.link(v, sep.inputs[0])
            x = n.math('DIVIDE', sep.outputs[0], cell)
            y = n.math('DIVIDE', sep.outputs[1], cell)
            def wrap(t, period, shift):
                t = n.math('SUBTRACT', t, shift) if shift else t
                return n.math('SUBTRACT', n.math('SUBTRACT', t, n.math('MULTIPLY', n.math('FLOOR', n.math('DIVIDE', t, period)), period)), period/2)
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
            edge = n.math('SUBTRACT', .5, d)
            cid = n.new('ShaderNodeCombineXYZ')
            n.link(n.math('SUBTRACT', x, gx), cid.inputs[0])
            n.link(n.math('SUBTRACT', y, gy), cid.inputs[1])
            wn = n.new('ShaderNodeTexWhiteNoise', noise_dimensions='3D')
            n.link(cid.outputs[0], wn.inputs['Vector'])
            return {'Edge': edge, 'Cell': wn.outputs['Color'], 'Crown': n.math('SUBTRACT', 1.0, n.math('MULTIPLY', d, 2.0))}
        blend, blend_col = triplanar(n, tex)
        edge = blend('Edge')
        seam = n.math('LESS_THAN', edge, .03)
        height = n.math('MULTIPLY', n.math('SUBTRACT', 1.0, seam), n.math('ADD', .7, n.math('MULTIPLY', blend('Crown'), .3)))
        n.link(height, bump.inputs['Height'])
        bump.inputs['Strength'].default_value = .5
        var = n.new('ShaderNodeMix', data_type='RGBA', blend_type='OVERLAY')
        var.inputs['Factor'].default_value = .10
        var.inputs['A'].default_value = (*base, 1)
        n.link(blend_col('Cell'), var.inputs['B'])
        dark = n.new('ShaderNodeMix', data_type='RGBA')
        n.link(seam, dark.inputs['Factor'])
        n.link(var.outputs['Result'], dark.inputs['A'])
        dark.inputs['B'].default_value = (*[c*.35 for c in base], 1)
        n.link(dark.outputs['Result'], bs.inputs['Base Color'])
        bs.inputs['Roughness'].default_value = .56
        bs.inputs['Coat Weight'].default_value = .15
    else:
        pw, ph = (u(8.0), u(4.0)) if role != 'white' else (u(10.0), u(5.0))
        def tex(v):
            b = n.new('ShaderNodeTexBrick', offset=0.0, squash=1.0)
            b.inputs['Scale'].default_value = 1.0/pw
            b.inputs['Mortar Size'].default_value = .008
            b.inputs['Mortar Smooth'].default_value = .0
            b.inputs['Brick Width'].default_value = 1.0
            b.inputs['Row Height'].default_value = ph/pw
            b.inputs['Color1'].default_value = (*[c*1.02 for c in base], 1)
            b.inputs['Color2'].default_value = (*[c*.97 for c in base], 1)
            b.inputs['Mortar'].default_value = (0, 0, 0, 1)
            n.link(v, b.inputs['Vector'])
            return {'Color': b.outputs['Color'], 'Fac': b.outputs['Fac']}
        blend, blend_col = triplanar(n, tex)
        n.link(blend_col('Color'), bs.inputs['Base Color'])
        n.link(n.math('SUBTRACT', 1.0, blend('Fac')), bump.inputs['Height'])
        bump.inputs['Strength'].default_value = .3
        bs.inputs['Roughness'].default_value = .32
        bs.inputs['Coat Weight'].default_value = .5
        bs.inputs['Coat Roughness'].default_value = .2
        bs.inputs['Thin Film Thickness'].default_value = 420.0
        bs.inputs['Thin Film IOR'].default_value = 1.45
        # Seams glow: the energy the skin bleeds off.
        n.link(blend('Fac'), bs.inputs['Emission Strength'])
        bs.inputs['Emission Color'].default_value = (.35, .65, 1.0, 1)
        em = n.math('MULTIPLY', blend('Fac'), .7)
        n.link(em, bs.inputs['Emission Strength'])


def machinery(mat, option, role):
    n = N(mat)
    out = n.new('ShaderNodeOutputMaterial')
    bs = n.new('ShaderNodeBsdfPrincipled')
    n.link(bs.outputs[0], out.inputs['Surface'])
    spec = {
        'A': {'tech': ((.035, .036, .04), 0, .7), 'slate': ((.06, .065, .07), 0, .75), 'steel': ((.55, .56, .58), .9, .32)},
        'B': {'tech': ((.02, .03, .05), 0, .45), 'slate': ((.10, .12, .15), 0, .5), 'steel': ((.62, .65, .68), 0, .5)},
        'C': {'tech': ((.008, .01, .015), 0, .08), 'slate': ((.012, .015, .022), 0, .1), 'steel': ((.8, .82, .85), 1, .12)},
    }[option][role]
    col, met, rough = spec
    bs.inputs['Base Color'].default_value = (*col, 1)
    bs.inputs['Metallic'].default_value = met
    bs.inputs['Roughness'].default_value = rough
    if option == 'C':
        bs.inputs['Coat Weight'].default_value = 1.0


def glows(option):
    for name in GLOWS:
        m = bpy.data.materials.get(name)
        if not m:
            continue
        bs = m.node_tree.nodes.get('Principled BSDF')
        if bs:
            bs.inputs['Emission Strength'].default_value = {'A': 8.0, 'B': 10.0, 'C': 18.0}[option]


def camera(name, pos, target, scale):
    cam = bpy.data.objects.get('Camera '+name)
    if cam is None:
        bpy.ops.object.camera_add(location=pos)
        cam = bpy.context.object
        cam.name = 'Camera '+name
    cam.location = pos
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 3000
    cam.rotation_euler = (Vector(target)-Vector(pos)).to_track_quat('-Z', 'Y').to_euler()
    return cam


CAMS = [
    ('study-quarter', (430, -460, 320), (-8, 0, 0), 560),
    ('study-aft', (-20, 260, 150), (-100, 90, 10), 130),
    ('study-bow', (330, -170, 150), (140, 0, 5), 130),
    ('study-plate', (60, -230, 110), (10, -110, 20), 60),
]
originals = {m.name: m.copy() for m in bpy.data.materials if m.name in ARMOUR or m.name in MACHINE or m.name in GLOWS}
for opt in args.options.split(','):
    for name, role in ARMOUR.items():
        if name in bpy.data.materials:
            ceramic(bpy.data.materials[name], opt, role)
    for name, role in MACHINE.items():
        if name in bpy.data.materials:
            machinery(bpy.data.materials[name], opt, role)
    glows(opt)
    for name, pos, target, scale in CAMS:
        sc.camera = camera(name, pos, target, scale)
        sc.render.film_transparent = False
        sc.render.filepath = str(OUT/f'{opt}-{name}.png')
        bpy.ops.render.render(write_still=True)
print('study done', OUT)
