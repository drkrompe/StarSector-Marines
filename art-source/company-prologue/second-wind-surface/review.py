"""Review renders of a skinned build, plus flat passes for coverage and seams.

  blender --background --factory-startup --python review.py
      [-- --samples 48] [--blend build/skinned.blend] [--out build/review] [--views a,b]

Opens a skinned build (apply_skin.py) and renders, at 1920x1080 with the
build's own key/fill lamps and world:
  <out>/<view>.png           lit beauty
  <out>/<view>-albedo.png    rendered base colour (emission): the skin after
                             variation and oblique-face blending, swatches
                             as they render; black on anything else
  <out>/<view>-part.png      flat id colour: each part its own (part_colour),
                             swatch faces SWATCH_COLOUR, black on anything
                             else (lamps, glazing, scar and repair materials)
Views: the build's own cameras (build_reference.py) plus the extra ones
below. Close-ups use the geometry's damage review cameras, so damage states
frame the same ground.
"""
from pathlib import Path
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import parts as P
import hull as H
DATA = H.DIR  # the hull's folder: its tables, art and outputs (hull.py)

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
def arg(name, default):
    return argv[argv.index(name) + 1] if name in argv else default
SAMPLES = int(arg('--samples', 48))
BLEND = DATA / Path(arg('--blend', DATA / 'build' / 'skinned.blend'))
OUT = DATA / Path(arg('--out', DATA / 'build' / 'review'))
OUT.mkdir(parents=True, exist_ok=True)
# Whole-hull set, close-ups and extra cameras: per hull (surface_hull.py).
WHOLE, CLOSE = H.REVIEW_WHOLE, H.REVIEW_CLOSE
VIEWS = arg('--views', ','.join(WHOLE + CLOSE)).split(',')
PART_ORDER = list(P.PARTS)
SWATCH_COLOUR = (40, 40, 255)
# (name, azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
EXTRA = H.REVIEW_EXTRA

bpy.ops.wm.open_mainfile(filepath=str(BLEND))
scene = bpy.context.scene
for name, (az, el, scale, target) in EXTRA.items():
    if 'Camera ' + name in bpy.data.objects:
        continue
    a, e = math.radians(az), math.radians(el)
    cd = bpy.data.cameras.new('Camera ' + name)
    cd.type, cd.ortho_scale, cd.clip_end = 'ORTHO', scale, 1000
    cam = bpy.data.objects.new('Camera ' + name, cd)
    scene.collection.objects.link(cam)
    t = Vector(target)
    cam.location = t + 180 * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()

scene.render.engine = 'CYCLES'


def use_gpu():
    """--factory-startup leaves Cycles on the CPU (minutes per still); take the GPU when there is one."""
    prefs = bpy.context.preferences.addons['cycles'].preferences
    for kind in ('OPTIX', 'CUDA', 'HIP', 'METAL', 'ONEAPI'):
        try:
            prefs.compute_device_type = kind
        except TypeError:
            continue
        prefs.get_devices()
        gpus = [d for d in prefs.devices if d.type == kind]
        if gpus:
            for d in prefs.devices:
                d.use = d.type == kind
            scene.cycles.device = 'GPU'
            print('SURF render device', kind, ', '.join(d.name for d in gpus))
            return
    print('SURF render device CPU (no GPU found)')


use_gpu()
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'


def render(view, suffix, samples, denoise=True, filt=1.5):
    scene.camera = bpy.data.objects['Camera ' + view]
    scene.cycles.samples = samples
    scene.cycles.use_denoising = denoise
    scene.cycles.filter_width = filt
    scene.render.filepath = str(OUT / f'{view}{suffix}.png')
    bpy.ops.render.render(write_still=True)


for v in VIEWS:
    render(v, '', SAMPLES)
    print('SURF beauty', v)

# Flat passes: rewire every material to an emission; world and lamps off.
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'None'
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0
for ob in scene.objects:
    if ob.type == 'LIGHT':
        ob.hide_render = True


def lin(v):
    c = v / 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def part_rgb(k):
    """Display value encoding part k (compose_review.part_ids decodes it)."""
    return (30 + 15 * (k % 14), 30 + 15 * (k // 14), 128)


def flat(mode):
    for m in bpy.data.materials:
        if not m.use_nodes:
            continue
        nt = m.node_tree
        out = next((n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL'), None)
        if out is None:
            continue
        em = nt.nodes.get('SURF flat') or nt.nodes.new('ShaderNodeEmission')
        em.name = 'SURF flat'
        for l in list(em.inputs['Color'].links):
            nt.links.remove(l)
        part, swatch = m.get('skin_part'), m.get('skin_swatch')
        if part is None and swatch is None:
            em.inputs['Color'].default_value = (0, 0, 0, 1)
        elif mode == 'albedo':
            bsdf = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
            nt.links.new(bsdf.inputs['Base Color'].links[0].from_socket, em.inputs['Color'])
        elif part is not None and part in PART_ORDER:
            em.inputs['Color'].default_value = (*[lin(c) for c in part_rgb(PART_ORDER.index(part))], 1)
        else:
            em.inputs['Color'].default_value = (*[lin(c) for c in SWATCH_COLOUR], 1)
        nt.links.new(em.outputs[0], out.inputs['Surface'])


for mode in ('albedo', 'part'):
    flat(mode)
    for v in VIEWS:
        render(v, '-' + mode, 1 if mode == 'part' else 16, False, .01 if mode == 'part' else 1.5)
    print('SURF pass', mode)
