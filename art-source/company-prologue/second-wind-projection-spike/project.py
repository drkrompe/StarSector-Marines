"""Project registered reskins back onto the clean mesh and review them.

  blender --background --factory-startup --python project.py -- --tag NAME
      --proj VIEW:IMAGE:REGISTRATION [--proj ...] [--samples 48]
  e.g. --proj port-side:generated/reskin-v1.png:registration/reskin-v1.json

VIEW names a camera in views.py whose guide render_guide.py already made (its
build/depth-VIEW.exr is needed). Opens build/base.blend (the modifier-applied
copy) and stores, per projector, data on every mesh corner so it travels with
the hull:
  P<i>Guide    where that guide camera saw the point (ortho, so linear across a
               planar face and exact at every pixel)
  P<i>Reskin   the same, put through the measured registration into the
               generated image's frame
  P<i>Depth    the point's view depth in that camera
  P<i>Cos      |cosine| between the face plane and that camera's view axis. The
               sign is ignored because the model's winding is not reliable:
               1,619 faces whose normal points away are still the frontmost
               surface in the port-side guide.
The shader decides visibility PER PIXEL, like a shadow map: a point is seen by
projector i when its depth is no greater than build/depth-VIEW.exr at its
guide UV, plus TOL. (A first per-face version -- a face covered when half its
samples were visible -- left 23% of the port-side view uncovered: large faces
partly behind a pipe and whole bevel strips dropped out.)
Each projector's weight is visible * max(cos - FACING, 0)^2, so the most
head-on view dominates and views cross-fade where they are comparable.
Pixels no projector saw show a neutral fallback.

Writes build/review/NAME-{guide,quarter30,quarter60}.png in a light rig that adds a
key sun the guides did not have, so baked lighting is exposed;
build/review/NAME-coverage60.png with a magenta fallback; and review/NAME-projection.json
with each view's textured share of visible hull. With two or more projectors it
also measures how much the first two disagree where both see the same surface.
"""
from pathlib import Path
import json
import math
import sys
import bpy
import numpy as np
from mathutils import Vector

HERE = Path(__file__).resolve().parent
BUILD = HERE / 'build'
REVIEW = HERE / 'review'          # measurements (tracked)
STILLS = BUILD / 'review'        # renders (reproducible, untracked)
for d in (REVIEW, STILLS):
    d.mkdir(parents=True, exist_ok=True)
sys.path.insert(0, str(HERE))
import views

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
def arg(name, default=None):
    return argv[argv.index(name) + 1] if name in argv else default
tag = arg('--tag')
samples = int(arg('--samples', 48))
specs = [argv[i + 1].split(':', 2) for i, a in enumerate(argv) if a == '--proj']
FACING, TOL = 0.25, 0.35
FALLBACK = (0.30, 0.30, 0.30, 1)

bpy.ops.wm.open_mainfile(filepath=str(BUILD / 'base.blend'))
scene = bpy.context.scene
W, H = scene.render.resolution_x, scene.render.resolution_y


class Projector:
    def __init__(self, i, view, image, registration):
        self.i, self.view = i, view
        self.cam = views.camera(view)
        self.inv = self.cam.matrix_world.inverted()
        self.axis = (self.cam.matrix_world.to_3x3() @ Vector((0, 0, -1))).normalized()
        self.half_w = self.cam.data.ortho_scale / 2
        self.half_h = self.half_w * H / W
        self.reg = json.loads((HERE / registration).read_text())
        fit = self.reg['fits'][self.reg['best_fit']]['T_guide_to_generated']
        (self.sx, self.sy), (self.tx, self.ty) = fit['scale'], fit['offset_px']
        self.gw, self.gh = self.reg['guide_size']
        self.image_path = image
        self.image = bpy.data.images.load(str((HERE / image).resolve()))
        self.depth = bpy.data.images.load(str(BUILD / f'depth-{view}.exr'))
        self.depth.colorspace_settings.name = 'Non-Color'

    def uvz(self, p):
        c = self.inv @ p
        return (c.x / self.half_w + 1) / 2, (c.y / self.half_h + 1) / 2, -c.z

    def reskin_uv(self, u, v):
        x, y = u * self.gw, (1 - v) * self.gh
        return (self.sx * x + self.tx) / self.gw, 1 - (self.sy * y + self.ty) / self.gh

    def nodes(self, nt):
        """(weight socket, colour socket) for this projector inside node tree nt."""
        n, l = nt.nodes, nt.links
        def math_node(op, a, b):
            m = n.new('ShaderNodeMath'); m.operation = op
            for k, x in enumerate((a, b)):
                if isinstance(x, (int, float)): m.inputs[k].default_value = x
                else: l.new(x, m.inputs[k])
            return m.outputs[0]
        guv = n.new('ShaderNodeUVMap'); guv.uv_map = f'P{self.i}Guide'
        dtex = n.new('ShaderNodeTexImage'); dtex.image = self.depth
        dtex.interpolation = 'Closest'; dtex.extension = 'CLIP'
        l.new(guv.outputs['UV'], dtex.inputs['Vector'])
        frag = n.new('ShaderNodeAttribute'); frag.attribute_name = f'P{self.i}Depth'
        cos = n.new('ShaderNodeAttribute'); cos.attribute_name = f'P{self.i}Cos'
        visible = math_node('LESS_THAN', math_node('SUBTRACT', frag.outputs['Fac'], dtex.outputs['Color']), TOL)
        visible = math_node('MULTIPLY', visible, dtex.outputs['Alpha'])
        facing = math_node('MAXIMUM', math_node('SUBTRACT', cos.outputs['Fac'], FACING), 0.0)
        weight = math_node('MULTIPLY', visible, math_node('POWER', facing, 2.0))
        ruv = n.new('ShaderNodeUVMap'); ruv.uv_map = f'P{self.i}Reskin'
        tex = n.new('ShaderNodeTexImage'); tex.image = self.image; tex.extension = 'CLIP'
        l.new(ruv.outputs['UV'], tex.inputs['Vector'])
        return weight, tex.outputs['Color']


projectors = [Projector(i, *spec) for i, spec in enumerate(specs)]

for ob in [o for o in scene.objects if o.type == 'MESH']:
    me = ob.data
    mw = ob.matrix_world
    nmat = mw.to_3x3().inverted().transposed()
    world_verts = [mw @ v.co for v in me.vertices]
    normals = [(nmat @ poly.normal).normalized() for poly in me.polygons]
    for p in projectors:
        guv = me.uv_layers.new(name=f'P{p.i}Guide')
        ruv = me.uv_layers.new(name=f'P{p.i}Reskin')
        dep = me.attributes.new(f'P{p.i}Depth', 'FLOAT', 'CORNER')
        cos = me.attributes.new(f'P{p.i}Cos', 'FLOAT', 'CORNER')
        for poly, nrm in zip(me.polygons, normals):
            c = abs(nrm.dot(p.axis))
            for li in poly.loop_indices:
                u, v, z = p.uvz(world_verts[me.loops[li].vertex_index])
                guv.data[li].uv = (u, v)
                ruv.data[li].uv = p.reskin_uv(u, v)
                dep.data[li].value = z
                cos.data[li].value = c


def blended(nt):
    """(coverage, colour) sockets: weighted mean of every projector's colour."""
    n, l = nt.nodes, nt.links
    total = None; acc = None
    for p in projectors:
        w, col = p.nodes(nt)
        scaled = n.new('ShaderNodeVectorMath'); scaled.operation = 'SCALE'
        l.new(col, scaled.inputs[0]); l.new(w, scaled.inputs['Scale'])
        if total is None:
            total, acc = w, scaled.outputs[0]
        else:
            t = n.new('ShaderNodeMath'); t.operation = 'ADD'
            l.new(total, t.inputs[0]); l.new(w, t.inputs[1]); total = t.outputs[0]
            a = n.new('ShaderNodeVectorMath'); a.operation = 'ADD'
            l.new(acc, a.inputs[0]); l.new(scaled.outputs[0], a.inputs[1]); acc = a.outputs[0]
    safe = n.new('ShaderNodeMath'); safe.operation = 'MAXIMUM'; safe.inputs[1].default_value = 1e-6
    l.new(total, safe.inputs[0])
    inv = n.new('ShaderNodeMath'); inv.operation = 'DIVIDE'; inv.inputs[0].default_value = 1.0
    l.new(safe.outputs[0], inv.inputs[1])
    mean = n.new('ShaderNodeVectorMath'); mean.operation = 'SCALE'
    l.new(acc, mean.inputs[0]); l.new(inv.outputs[0], mean.inputs['Scale'])
    cov = n.new('ShaderNodeMath'); cov.operation = 'GREATER_THAN'; cov.inputs[1].default_value = 0.0
    l.new(total, cov.inputs[0])
    return cov.outputs[0], mean.outputs[0]


mat = bpy.data.materials.new('SPIKE projected reskin')
mat.use_nodes = True
nt = mat.node_tree
bsdf = nt.nodes['Principled BSDF']
bsdf.inputs['Roughness'].default_value = 0.75
cov_socket, colour = blended(nt)
mix = nt.nodes.new('ShaderNodeMix'); mix.data_type = 'RGBA'
mix.inputs['A'].default_value = FALLBACK
nt.links.new(cov_socket, mix.inputs['Factor'])
nt.links.new(colour, mix.inputs['B'])
nt.links.new(mix.outputs['Result'], bsdf.inputs['Base Color'])
for ob in [o for o in scene.objects if o.type == 'MESH']:
    ob.data.materials.clear(); ob.data.materials.append(mat)
    for poly in ob.data.polygons:
        poly.material_index = 0

# Review lighting: soft ambient plus a key sun from above the port bow, which
# the flat guides never had, so any shading baked into the images shows up.
scene.render.film_transparent = False
scene.view_settings.view_transform = 'AgX'
bg = scene.world.node_tree.nodes['Background']
bg.inputs[0].default_value = (0.74, 0.76, 0.78, 1); bg.inputs[1].default_value = 0.55
sun_data = bpy.data.lights.new('SPIKE key', 'SUN'); sun_data.energy = 3.0; sun_data.angle = math.radians(8)
sun = bpy.data.objects.new('SPIKE key', sun_data); scene.collection.objects.link(sun)
sun.rotation_euler = Vector((-0.45, -0.8, -0.55)).to_track_quat('-Z', 'Y').to_euler()
scene.cycles.samples = samples
scene.cycles.filter_width = 1.5
scene.cycles.use_denoising = True
scene.render.image_settings.file_format = 'PNG'

review_views = ['port-side', 'quarter30', 'quarter60']
labels = {'port-side': 'guide', 'quarter30': 'quarter30', 'quarter60': 'quarter60'}
for v in review_views:
    scene.camera = views.camera(v)
    scene.render.filepath = str(STILLS / f'{tag}-{labels[v]}.png')
    bpy.ops.render.render(write_still=True)
mix.inputs['A'].default_value = (0.9, 0.0, 0.75, 1)
scene.camera = views.camera('quarter60')
scene.render.filepath = str(STILLS / f'{tag}-coverage60.png')
bpy.ops.render.render(write_still=True)


# Measurement passes: flat emission, one sample, no pixel filter, alpha = hull.
def emission_override(build):
    m = bpy.data.materials.new('SPIKE measure')
    m.use_nodes = True
    t = m.node_tree; t.nodes.clear()
    e = t.nodes.new('ShaderNodeEmission'); o = t.nodes.new('ShaderNodeOutputMaterial')
    t.links.new(build(t), e.inputs['Color']); t.links.new(e.outputs[0], o.inputs['Surface'])
    return m

def measure(material, camera_view, name):
    scene.view_layers[0].material_override = material
    scene.camera = views.camera(camera_view)
    scene.render.filepath = str(BUILD / f'{tag}-{name}-{camera_view}.exr')
    bpy.ops.render.render(write_still=True)
    a = np.array(bpy.data.images.load(scene.render.filepath).pixels[:], np.float32).reshape(H, W, 4)
    return a[..., :3], a[..., 3] > 0.5

scene.render.film_transparent = True
scene.cycles.samples = 1; scene.cycles.use_denoising = False; scene.cycles.filter_width = 0.01
scene.view_settings.view_transform = 'Standard'
scene.render.image_settings.file_format = 'OPEN_EXR'
scene.render.image_settings.color_depth = '32'
stats = {'projectors': [{'view': p.view, 'image': p.image_path, 'fit': p.reg['best_fit'],
                         'scale': [p.sx, p.sy], 'offset_px': [p.tx, p.ty]} for p in projectors],
         'pixel_coverage_of_visible_hull': {}}
cov_mat = emission_override(lambda t: blended(t)[0])
for v in review_views:
    rgb, hull = measure(cov_mat, v, 'coverage')
    stats['pixel_coverage_of_visible_hull'][labels[v]] = float((rgb[..., 0][hull] > 0.5).mean())

if len(projectors) >= 2:
    # Where projectors 0 and 1 both see a surface, how different are their colours?
    def both(t):
        (w0, _), (w1, _) = projectors[0].nodes(t), projectors[1].nodes(t)
        m = t.nodes.new('ShaderNodeMath'); m.operation = 'MULTIPLY'
        t.links.new(w0, m.inputs[0]); t.links.new(w1, m.inputs[1])
        g = t.nodes.new('ShaderNodeMath'); g.operation = 'GREATER_THAN'; g.inputs[1].default_value = 0
        t.links.new(m.outputs[0], g.inputs[0])
        return g.outputs[0]
    def colour_of(k):
        return lambda t: projectors[k].nodes(t)[1]
    overlap = {}
    for v in ('port-side', 'quarter30'):
        both_rgb, hull = measure(emission_override(both), v, 'overlap')
        c0, _ = measure(emission_override(colour_of(0)), v, 'colour0')
        c1, _ = measure(emission_override(colour_of(1)), v, 'colour1')
        mask = hull & (both_rgb[..., 0] > 0.5)
        to_srgb = lambda x: np.where(x <= 0.0031308, 12.92 * x, 1.055 * np.clip(x, 0, None) ** (1 / 2.4) - 0.055)
        d = np.abs(to_srgb(c0) - to_srgb(c1))[mask] * 255
        lum = lambda x: (to_srgb(x) * [0.2126, 0.7152, 0.0722]).sum(-1)
        dl = (lum(c0) - lum(c1))[mask] * 255
        overlap[labels[v]] = {'overlap_share_of_visible_hull': float(mask.sum() / hull.sum()),
                              'mean_abs_srgb_diff_0_255': float(d.mean()),
                              'p90_abs_srgb_diff_0_255': float(np.percentile(d.max(-1), 90)),
                              'mean_luminance_offset_0_255 (view0 - view1)': float(dl.mean())}
        # Heat image of the disagreement, for the review sheet.
        heat = np.zeros((H, W, 4), np.float32); heat[..., 3] = 1
        heat[hull] = (0.12, 0.12, 0.12, 1)
        diff = np.abs(to_srgb(c0) - to_srgb(c1)).mean(-1)
        heat[mask, 0] = np.clip(diff[mask] * 3, 0, 1); heat[mask, 1] = np.clip(diff[mask] * 3 - 0.5, 0, 1)
        im = bpy.data.images.new('heat', W, H); im.pixels[:] = heat.ravel()
        im.filepath_raw = str(STILLS / f'{tag}-disagreement-{labels[v]}.png'); im.file_format = 'PNG'; im.save()
    stats['two_view_disagreement'] = overlap

scene.view_layers[0].material_override = None
stats.update({'facing_cosine': FACING, 'depth_tolerance_units': TOL,
              'review_views': {labels[v]: (views.VIEWS[v] or 'build_reference port-side camera') for v in review_views}})
(REVIEW / f'{tag}-projection.json').write_text(json.dumps(stats, indent=2) + '\n')
bpy.ops.wm.save_as_mainfile(filepath=str(BUILD / f'{tag}-projected.blend'))
print('SPIKE', json.dumps(stats))
