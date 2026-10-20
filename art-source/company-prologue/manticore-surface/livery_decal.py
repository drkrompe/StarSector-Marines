"""The Manticore (P)'s sprayed markings, read off the sprite and laid on after
apply_skin.py so grading never pulls them toward the hull's palette (as the
Kite (P)'s claw slashes and the Colossus Mk.III's winged heart).

Run inside Blender on the skinned build (rebuild.sh step 5):
  blender --background --factory-startup --python livery_decal.py -- --hull ../manticore-surface [--in B] [--out B]

Two markings, each a small mask read off manticore_pirate.png, packed into the
.blend and projected straight down in the core's frame: sprite column =
82 - Y and row from the bottom = X + 67 (manticore.ship's centre 82,67 from the
bottom-left), so they land where the sprite draws them, on upward faces only:
  drips   the scarlet runs down the pale command dome's aft half (the sprite's
          red inside the dome's window), on the dome and the command tier;
  flames  the yellow flame flashes either side of the dome, on the command
          tier and the core deck beside it.
Linear interpolation, a noise threshold for a ragged sprayed edge and a finer
noise wearing it through, as on the Kite (P).
"""
import struct
import sys
import zlib
from pathlib import Path
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent))
from starsector_path import GAME_CORE  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
IN = Path(argv[argv.index('--in') + 1]) if '--in' in argv else HERE/'build'/'skinned.blend'
OUT = Path(argv[argv.index('--out') + 1]) if '--out' in argv else IN
SPRITE = GAME_CORE/'graphics/ships/manticore/manticore_pirate.png'
CENTRE = (82.0, 67.0)          # manticore.ship centre, sprite px from the bottom-left
EDGE = (.10, .28)              # mask values the sprayed edge runs between
# name: (window in sprite px (col0, row0, col1, row1) from the top-left, value(r, g, b),
#        paint (linear), cover, target objects)
MARKS = {
    'drips': ((72, 97, 92, 110), lambda r, g, b: (r - 1.6*max(g, b))*2.2,
              (.42, .035, .02), .85, ('Bridge command dome', 'Bridge command tier')),
    'flames': ((54, 86, 110, 110), lambda r, g, b: (min(r, g) - b - .12)*5.0 if g > .7*r and min(r, g) > .55 else 0.0,
               (.62, .40, .06), .8, ('Bridge command tier', 'Hull core')),
}

bpy.ops.wm.open_mainfile(filepath=str(IN))
src = bpy.data.images.load(str(SPRITE))
w, h = src.size
px = list(src.pixels)
bpy.data.images.remove(src)
chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)


def mask_image(name, window, value):
    c0, r0, c1, r1 = window
    vals = []
    for i in range(w*h):            # Blender's pixels run from the bottom-left
        col, row = i % w, h - 1 - i//w
        r, g, b, a = px[4*i:4*i + 4]
        inside = c0 <= col < c1 and r0 <= row < r1
        vals.append(max(0.0, min(1.0, value(r, g, b)))*a if inside else 0.0)
    # Written as an 8-bit grey PNG by hand (Blender drops a generated image's
    # pixels on save), loaded back and packed: the marking travels with the .blend.
    png = HERE/'build'/f'{name}-mask.png'
    png.parent.mkdir(parents=True, exist_ok=True)
    rows = b''.join(b'\x00' + bytes(int(round(vals[(h - 1 - y)*w + x]*255)) for x in range(w)) for y in range(h))
    png.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
                    + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))
    img = bpy.data.images.load(str(png))
    img.name = f'Manticore (P) {name} mask'
    img.colorspace_settings.name = 'Non-Color'
    img.pack()
    painted = sum(1 for v in vals if v > .3)
    if not painted:
        raise SystemExit(f'livery_decal: the sprite gave no {name} pixels; nothing to paint')
    return img, painted


def decal(mat, mask, paint, key, cover):
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if bsdf is None or mat.get(key):
        return False
    base = bsdf.inputs['Base Color']
    N = nt.nodes.new
    # Hull-unit coordinates in the core's own frame (its origin is the hull
    # origin), so the marking stays put when a shot parents, scales or turns the ship.
    tc = N('ShaderNodeTexCoord'); tc.object = bpy.data.objects['Hull core']
    tcn = N('ShaderNodeTexCoord')     # the shaded object's own normal (no object rotations here)
    sep = N('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Object'], sep.inputs[0])
    # u = (82 - Y)/164, v = (X + 67)/154 in hull units (the build's world frame).
    u = N('ShaderNodeMath'); u.operation = 'MULTIPLY_ADD'
    nt.links.new(sep.outputs['Y'], u.inputs[0]); u.inputs[1].default_value = -1/w; u.inputs[2].default_value = CENTRE[0]/w
    v = N('ShaderNodeMath'); v.operation = 'MULTIPLY_ADD'
    nt.links.new(sep.outputs['X'], v.inputs[0]); v.inputs[1].default_value = 1/h; v.inputs[2].default_value = CENTRE[1]/h
    uv = N('ShaderNodeCombineXYZ')
    nt.links.new(u.outputs[0], uv.inputs[0]); nt.links.new(v.outputs[0], uv.inputs[1])
    tex = N('ShaderNodeTexImage'); tex.image = mask; tex.interpolation = 'Linear'; tex.extension = 'CLIP'
    nt.links.new(uv.outputs[0], tex.inputs['Vector'])
    # Ragged sprayed edge: the threshold wanders with a coarse noise.
    coarse = N('ShaderNodeTexNoise'); coarse.inputs['Scale'].default_value = .35; coarse.inputs['Detail'].default_value = 4
    nt.links.new(tc.outputs['Object'], coarse.inputs['Vector'])
    jitter = N('ShaderNodeMapRange')
    nt.links.new(coarse.outputs['Fac'], jitter.inputs['Value'])
    jitter.inputs['To Min'].default_value, jitter.inputs['To Max'].default_value = -.12, .12
    lo = N('ShaderNodeMath'); lo.operation = 'ADD'; lo.inputs[1].default_value = EDGE[0]
    nt.links.new(jitter.outputs[0], lo.inputs[0])
    hi = N('ShaderNodeMath'); hi.operation = 'ADD'; hi.inputs[1].default_value = EDGE[1]
    nt.links.new(jitter.outputs[0], hi.inputs[0])
    edge = N('ShaderNodeMapRange'); edge.interpolation_type = 'SMOOTHSTEP'
    nt.links.new(tex.outputs['Color'], edge.inputs['Value'])
    nt.links.new(lo.outputs[0], edge.inputs['From Min']); nt.links.new(hi.outputs[0], edge.inputs['From Max'])
    # Worn through: a fine noise takes the paint off in flecks.
    fine = N('ShaderNodeTexNoise'); fine.inputs['Scale'].default_value = 3.0; fine.inputs['Detail'].default_value = 8
    nt.links.new(tc.outputs['Object'], fine.inputs['Vector'])
    wear = N('ShaderNodeMapRange')
    nt.links.new(fine.outputs['Fac'], wear.inputs['Value'])
    wear.inputs['From Min'].default_value, wear.inputs['From Max'].default_value = .36, .50
    # Only faces that look up take a top-down marking.
    sepn = N('ShaderNodeSeparateXYZ')
    nt.links.new(tcn.outputs['Normal'], sepn.inputs[0])
    up = N('ShaderNodeMapRange')
    nt.links.new(sepn.outputs['Z'], up.inputs['Value'])
    up.inputs['From Min'].default_value, up.inputs['From Max'].default_value = .35, .6
    f1 = N('ShaderNodeMath'); f1.operation = 'MULTIPLY'
    nt.links.new(edge.outputs[0], f1.inputs[0]); nt.links.new(wear.outputs[0], f1.inputs[1])
    f2 = N('ShaderNodeMath'); f2.operation = 'MULTIPLY'
    nt.links.new(f1.outputs[0], f2.inputs[0]); nt.links.new(up.outputs[0], f2.inputs[1])
    f3 = N('ShaderNodeMath'); f3.operation = 'MULTIPLY'; f3.inputs[1].default_value = cover
    nt.links.new(f2.outputs[0], f3.inputs[0])
    f2 = f3
    mix = N('ShaderNodeMix'); mix.data_type = 'RGBA'
    # The Mix node carries float, vector and colour sockets under the same
    # names; take the colour ones by identifier.
    sock = lambda coll, ident: next(x for x in coll if x.identifier == ident)
    a, b, res = sock(mix.inputs, 'A_Color'), sock(mix.inputs, 'B_Color'), sock(mix.outputs, 'Result_Color')
    nt.links.new(f2.outputs[0], sock(mix.inputs, 'Factor_Float'))
    if base.links:
        nt.links.new(base.links[0].from_socket, a)
    else:
        a.default_value = base.default_value
    b.default_value = (*paint, 1)
    nt.links.new(res, base)
    mat[key] = True
    return True


report = []
for name, (window, value, paint, cover, targets) in MARKS.items():
    mask, painted = mask_image(name, window, value)
    done = 0
    for oname in targets:
        ob = bpy.data.objects.get(oname)
        if ob is None:
            raise SystemExit(f'livery_decal: {oname} is missing from the build')
        for slot in ob.material_slots:
            if slot.material and slot.material.use_nodes:
                # A material shared with objects outside this marking's targets would
                # take it there too; give this object its own copy first.
                users = [o for o in bpy.data.objects if o.type == 'MESH' and o.name not in targets
                         and any(s.material == slot.material for s in o.material_slots)]
                if users:
                    slot.material = slot.material.copy()
                done += decal(slot.material, mask, paint, 'decal_' + name, cover)
    report.append(f'{name} on {done} materials ({painted} painted texels)')
print('SURF markings: ' + '; '.join(report) + f' -> {OUT}')
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
