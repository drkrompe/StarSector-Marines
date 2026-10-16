"""Review renders of the skinned clean build, plus flat passes for the seam check.

  blender --background --factory-startup --python review.py [-- --samples 64]

Opens build/skinned.blend (apply_skin.py) and renders, at 1920x1080 with the
clean build's own key/fill lamps and world:
  build/review/<view>.png           lit beauty
  build/review/<view>-albedo.png    skin texture colour only (emission), black off-skin
  build/review/<view>-part.png      flat part-id colour (PART_COLOURS order), black off-skin
Views: port-side and port-quarter (build_reference.py's cameras), high-quarter
(45 deg round from the port beam toward the bow, 50 deg up, the spike's
high45), and two close-ups, port-flank-detail and port-aft-detail
(build_reference.py's damage review cameras, so later damage states frame the
same ground).
"""
from pathlib import Path
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import parts as P

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
SAMPLES = int(argv[argv.index('--samples') + 1]) if '--samples' in argv else 64
OUT = HERE / 'build' / 'review'
OUT.mkdir(parents=True, exist_ok=True)
VIEWS = ['port-side', 'port-quarter', 'high-quarter', 'port-flank-detail', 'port-aft-detail']
PART_ORDER = list(P.PARTS)

bpy.ops.wm.open_mainfile(filepath=str(HERE / 'build' / 'skinned.blend'))
scene = bpy.context.scene
target = Vector((5, 0, 1))
a, e = math.radians(45), math.radians(50)
cd = bpy.data.cameras.new('Camera high-quarter')
cd.type, cd.ortho_scale, cd.clip_end = 'ORTHO', 170, 1000
cam = bpy.data.objects.new('Camera high-quarter', cd)
scene.collection.objects.link(cam)
cam.location = target + 180 * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
cam.rotation_euler = (target - cam.location).to_track_quat('-Z', 'Y').to_euler()

scene.render.engine = 'CYCLES'
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
world_strength = scene.world.node_tree.nodes['Background'].inputs[1]
world_strength.default_value = 0
for ob in scene.objects:
    if ob.type == 'LIGHT':
        ob.hide_render = True


def part_colour(part):
    """Linear emission whose Standard-view display value encodes the part:
    R = 20 + 12k, G = 255 - R, B = 128 (compose_review.part_ids decodes it)."""
    k = PART_ORDER.index(part)
    def lin(v):
        c = v / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r = 20 + 12 * k
    return (lin(r), lin(255 - r), lin(128), 1)


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
        part = m.get('skin_part')
        if part is None:
            em.inputs['Color'].default_value = (0, 0, 0, 1)
        elif mode == 'albedo':
            tex = next(n for n in nt.nodes if n.type == 'TEX_IMAGE')
            nt.links.new(tex.outputs['Color'], em.inputs['Color'])
        else:
            em.inputs['Color'].default_value = part_colour(part)
        nt.links.new(em.outputs[0], out.inputs['Surface'])


for mode in ('albedo', 'part'):
    flat(mode)
    for v in VIEWS:
        render(v, '-' + mode, 1 if mode == 'part' else 16, False, .01 if mode == 'part' else 1.5)
    print('SURF pass', mode)
