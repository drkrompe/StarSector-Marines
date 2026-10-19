"""The Kite (P)'s red claw slashes: the pirate crew's sprayed marking, laid on
after apply_skin.py so grading never pulls it toward the hull's palette.

Run inside Blender on the skinned build (rebuild.sh step 5):
  blender --background --factory-startup --python livery_decal.py -- --hull ../kite-surface [--in B] [--out B]

The marking is read straight off the vanilla sprite (aeroshuttle_pirates.png):
each pixel's redness (red over the larger of green and blue, times alpha)
becomes one texel of a small mask image packed into the .blend. The hull's
own materials sample it by position in the fuselage's frame, projected straight down: sprite
column = 41 - Y and row from the bottom = X + 28 (kite.ship's centre 41,28
from the bottom-left), so the slashes land exactly where the sprite draws
them. Only upward-facing faces of the hull solids and the sponsons take it.
Linear interpolation of the 82x66 mask and a noise threshold make a sprayed,
ragged edge; a finer noise wears it through to the paint beneath.
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
SPRITE = GAME_CORE/'graphics/ships/aeroshuttle/aeroshuttle_pirates.png'
CENTRE = (41.0, 28.0)          # kite.ship centre, sprite px from the bottom-left
TARGETS = ('Hull fuselage', 'Hull wing body', 'Hull dorsal spine', 'Hull aft hump',
           'Hull port missile sponson', 'Hull starboard missile sponson')
PAINT = (.34, .022, .016)     # linear: a sprayed red gone dull, darker than the sprite's flat 216
COVER = .82                   # the paint's opacity: the grime and plate texture beneath show through
EDGE = (.10, .28)              # mask values the sprayed edge runs between

bpy.ops.wm.open_mainfile(filepath=str(IN))

src = bpy.data.images.load(str(SPRITE))
w, h = src.size
px = list(src.pixels)
vals = []
for i in range(w*h):
    r, g, b, a = px[4*i:4*i + 4]
    vals.append(max(0.0, min(1.0, (r - 1.6*max(g, b))*2.0))*a)
# Written as an 8-bit grey PNG by hand (Blender drops a generated image's
# pixels on save), loaded back and packed: the marking travels with the .blend.
png = HERE/'build'/'claw-slash-mask.png'
png.parent.mkdir(parents=True, exist_ok=True)
rows = b''.join(b'\x00' + bytes(int(round(vals[(h - 1 - y)*w + x]*255)) for x in range(w)) for y in range(h))
chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
png.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))
bpy.data.images.remove(src)
mask = bpy.data.images.load(str(png))
mask.name = 'Kite (P) claw slash mask'
mask.colorspace_settings.name = 'Non-Color'
mask.pack()
out = vals
painted = sum(1 for v in out if v > .3)
if not painted:
    raise SystemExit('livery_decal: the sprite gave no red pixels; nothing to paint')
if max(list(mask.pixels)[0::4]) <= 0:
    raise SystemExit('livery_decal: the packed mask is empty')


def decal(mat):
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if bsdf is None or mat.get('claw_decal'):
        return False
    base = bsdf.inputs['Base Color']
    N = nt.nodes.new
    # Hull-unit coordinates in the fuselage's own frame (its origin is the hull
    # origin), so the marking stays put when a shot parents, scales or turns the ship.
    tc = N('ShaderNodeTexCoord'); tc.object = bpy.data.objects['Hull fuselage']
    tcn = N('ShaderNodeTexCoord')     # the shaded object's own normal (no object rotations here)
    sep = N('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Object'], sep.inputs[0])
    # u = (41 - Y)/82, v = (X + 28)/66 in hull units (the build's world frame).
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
    f3 = N('ShaderNodeMath'); f3.operation = 'MULTIPLY'; f3.inputs[1].default_value = COVER
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
    b.default_value = (*PAINT, 1)
    nt.links.new(res, base)
    mat['claw_decal'] = True
    return True


done = 0
for name in TARGETS:
    ob = bpy.data.objects.get(name)
    if ob is None:
        continue
    for slot in ob.material_slots:
        if slot.material and slot.material.use_nodes:
            # A material shared with objects outside TARGETS would take the marking
            # there too; give this object its own copy first.
            users = [o for o in bpy.data.objects if o.type == 'MESH' and o.name not in TARGETS
                     and any(s.material == slot.material for s in o.material_slots)]
            if users:
                slot.material = slot.material.copy()
            done += decal(slot.material)
print(f'SURF claw-slash decal on {done} materials ({w}x{h} mask, {painted} painted texels, packed) -> {OUT}')
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
