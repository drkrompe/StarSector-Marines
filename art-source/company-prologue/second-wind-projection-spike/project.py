"""Project registered reskins back onto the clean mesh and review them.

  blender --background --factory-startup --python project.py -- --tag NAME
      --proj VIEW:IMAGE:REGISTRATION [--proj ...] [--samples 48]
  e.g. --proj port-side:generated/reskin-v1.png:registration/reskin-v1.json
       --proj part-pod:build/parts/part-pod.png:registration/part-pod.json

VIEW names a camera in views.py whose guide render_guide.py already made (its
build/depth-VIEW.exr is needed). A whole-ship projector may paint any face; a
part projector ('part-*') only faces of its own objects inside its region.
Opens build/base.blend (the modifier-applied copy) and stores, per projector,
data on the mesh corners it may paint, so it travels with the hull:
  P<i>Guide    UV: where that guide camera saw the point (ortho, so linear
               across a planar face and exact at every pixel). The measured
               registration into the generated image is applied in the shader,
               which keeps one UV layer per projector (Blender allows eight).
  P<i>Depth    the point's view depth in that camera
  P<i>Cos      |cosine| between the face plane and that camera's view axis; 0 on
               faces the projector may not paint. The sign is ignored because the
               model's winding is not reliable: 1,619 faces whose normal points
               away are still the frontmost surface in the port-side guide.
The shader decides visibility PER PIXEL, like a shadow map: a point is seen by
projector i when its depth is no greater than build/depth-VIEW.exr at its
guide UV, plus TOL. (A first per-face version -- a face covered when half its
samples were visible -- left 23% of the port-side view uncovered: large faces
partly behind a pipe and whole bevel strips dropped out.)
Each projector's weight is visible * max(cos - FACING, 0)^2, so the most
head-on view dominates and views cross-fade where they are comparable.
Pixels no projector saw show a neutral fallback.

Writes build/review/NAME-<view>.png for each review view, in a light rig that
adds a key sun the guides did not have, so baked lighting is exposed;
build/review/NAME-coverage60.png with a magenta fallback;
build/review/NAME-seams-<view>.png marking projector hand-overs;
build/review/NAME-disagreement-<view>.png for the first two projectors; and
review/NAME-projection.json. Measurements come from flat emission passes of
every projector's weight and colour, recombined exactly as the shader does:
  coverage   textured share of each view's visible hull
  stretch    1/cos of the dominant projector: how far texels are smeared
  seams      colour step across a hand-over between dominant projectors on one
             plane (neighbouring pixels with matching true normal and depth),
             against the step between same-plane neighbours that share a
             projector; creases are excluded, since paint changes there anyway
  pairs      for each pair of projectors, how much they disagree where both
             see the same surface
"""
from pathlib import Path
import itertools
import json
import math
import sys
import bpy
import numpy as np
from mathutils import Vector

HERE = Path(__file__).resolve().parent
BUILD = HERE / 'build'
REVIEW = HERE / 'review'          # measurements (tracked)
STILLS = BUILD / 'review'         # renders (reproducible, untracked)
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
REVIEW_VIEWS = ['port-side', 'quarter30', 'quarter60', 'high45']
LABELS = {'port-side': 'guide', 'quarter30': 'quarter30', 'quarter60': 'quarter60', 'high45': 'high45'}

bpy.ops.wm.open_mainfile(filepath=str(BUILD / 'base.blend'))
scene = bpy.context.scene
W, H = scene.render.resolution_x, scene.render.resolution_y
meshes = [o for o in scene.objects if o.type == 'MESH']


class Projector:
    def __init__(self, i, view, image, registration):
        self.i, self.view = i, view
        self.cam = views.camera(view)
        self.inv = self.cam.matrix_world.inverted()
        self.axis = (self.cam.matrix_world.to_3x3() @ Vector((0, 0, -1))).normalized()
        rw, rh = views.resolution(view)
        self.half_w = self.cam.data.ortho_scale / 2
        self.half_h = self.half_w * rh / rw
        self.reg = json.loads((HERE / registration).read_text())
        fit = self.reg['fits'][self.reg['best_fit']]['T_guide_to_generated']
        (self.sx, self.sy), (self.tx, self.ty) = fit['scale'], fit['offset_px']
        self.gw, self.gh = self.reg['guide_size']
        self.image_path = image
        self.image = bpy.data.images.load(str((HERE / image).resolve()))
        self.depth = bpy.data.images.load(str(BUILD / f'depth-{view}.exr'))
        self.depth.colorspace_settings.name = 'Non-Color'
        if view in views.PARTS:
            self.objects = set(views.part_objects(view))
            self.region = [Vector(c) for c in views.PARTS[view]['region']]
        else:
            self.objects, self.region = None, None
        # Generated-image texels per model unit, measured on the native image.
        native_w = self.reg['native_size'][0]
        if view in views.PARTS:
            native_w *= views.TILE[0] / 1920  # the tile's share of the native sheet
        self.texels_per_unit = native_w / self.cam.data.ortho_scale

    def may_paint(self, ob, centre):
        if self.objects is None:
            return True
        lo, hi = self.region
        return ob in self.objects and all(lo[k] <= centre[k] <= hi[k] for k in range(3))

    def uvz(self, p):
        c = self.inv @ p
        return (c.x / self.half_w + 1) / 2, (c.y / self.half_h + 1) / 2, -c.z

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
        # Registration: guide pixel (x, y-down) -> generated pixel, in UV terms.
        reg = n.new('ShaderNodeVectorMath'); reg.operation = 'MULTIPLY_ADD'
        reg.inputs[1].default_value = (self.sx, self.sy, 1)
        reg.inputs[2].default_value = (self.tx / self.gw, 1 - self.sy - self.ty / self.gh, 0)
        l.new(guv.outputs['UV'], reg.inputs[0])
        tex = n.new('ShaderNodeTexImage'); tex.image = self.image; tex.extension = 'CLIP'
        l.new(reg.outputs[0], tex.inputs['Vector'])
        return weight, tex.outputs['Color']


projectors = [Projector(i, *spec) for i, spec in enumerate(specs)]

for ob in meshes:
    me = ob.data
    mw = ob.matrix_world
    nmat = mw.to_3x3().inverted().transposed()
    world_verts = [mw @ v.co for v in me.vertices]
    for p in projectors:
        allowed = [p.may_paint(ob, mw @ poly.center) for poly in me.polygons]
        if not any(allowed):
            continue
        guv = me.uv_layers.new(name=f'P{p.i}Guide')
        dep = me.attributes.new(f'P{p.i}Depth', 'FLOAT', 'CORNER')
        cos = me.attributes.new(f'P{p.i}Cos', 'FLOAT', 'CORNER')
        for poly, ok in zip(me.polygons, allowed):
            c = abs((nmat @ poly.normal).normalized().dot(p.axis)) if ok else 0.0
            for li in poly.loop_indices:
                u, v, z = p.uvz(world_verts[me.loops[li].vertex_index])
                guv.data[li].uv = (u, v)
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
for ob in meshes:
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
scene.render.image_settings.color_mode = 'RGB'
for v in REVIEW_VIEWS:
    scene.camera = views.camera(v)
    scene.render.filepath = str(STILLS / f'{tag}-{LABELS[v]}.png')
    bpy.ops.render.render(write_still=True)
mix.inputs['A'].default_value = (0.9, 0.0, 0.75, 1)
scene.camera = views.camera('quarter60')
scene.render.filepath = str(STILLS / f'{tag}-coverage60.png')
bpy.ops.render.render(write_still=True)


# --- measurement: flat emission passes, one sample, no pixel filter ---------
def emission_override(build):
    m = bpy.data.materials.new('SPIKE measure')
    m.use_nodes = True
    t = m.node_tree; t.nodes.clear()
    e = t.nodes.new('ShaderNodeEmission'); o = t.nodes.new('ShaderNodeOutputMaterial')
    t.links.new(build(t), e.inputs['Color']); t.links.new(e.outputs[0], o.inputs['Surface'])
    return m

def view_depth(t):
    return t.nodes.new('ShaderNodeCameraData').outputs['View Z Depth']

def true_normal(t):
    return t.nodes.new('ShaderNodeNewGeometry').outputs['True Normal']

def render_pass(material, name):
    scene.view_layers[0].material_override = material
    scene.render.filepath = str(BUILD / f'{tag}-{name}.exr')
    bpy.ops.render.render(write_still=True)
    a = np.array(bpy.data.images.load(scene.render.filepath).pixels[:], np.float32).reshape(H, W, 4)
    return a

def to_srgb(x):
    x = np.clip(x, 0, None)
    return np.where(x <= 0.0031308, 12.92 * x, 1.055 * x ** (1 / 2.4) - 0.055) * 255

scene.render.film_transparent = True
scene.cycles.samples = 1; scene.cycles.use_denoising = False; scene.cycles.filter_width = 0.01
scene.view_settings.view_transform = 'Standard'
scene.render.image_settings.file_format = 'OPEN_EXR'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.image_settings.color_depth = '32'
weight_mats = [emission_override(lambda t, p=p: p.nodes(t)[0]) for p in projectors]
colour_mats = [emission_override(lambda t, p=p: p.nodes(t)[1]) for p in projectors]
depth_mat = emission_override(view_depth)
normal_mat = emission_override(true_normal)

stats = {'projectors': [{'view': p.view, 'image': p.image_path, 'fit': p.reg['best_fit'],
                         'scale': [p.sx, p.sy], 'offset_px': [p.tx, p.ty],
                         'generated_texels_per_model_unit': p.texels_per_unit} for p in projectors],
         'views': {}}
for v in REVIEW_VIEWS:
    scene.camera = views.camera(v)
    label = LABELS[v]
    wts = np.stack([render_pass(m, f'w{i}-{label}')[..., 0] for i, m in enumerate(weight_mats)])
    cols = np.stack([to_srgb(render_pass(m, f'c{i}-{label}')[..., :3]) for i, m in enumerate(colour_mats)])
    d = render_pass(depth_mat, f'depth-{label}')
    hull, z = d[..., 3] > 0.5, d[..., 0]
    nrm = render_pass(normal_mat, f'normal-{label}')[..., :3]
    total = wts.sum(0)
    textured = hull & (total > 0)
    blend = (wts[..., None] * cols).sum(0) / np.maximum(total, 1e-9)[..., None]
    dominant = np.where(textured, wts.argmax(0), -1)
    # Stretch: the dominant projector's weight is (cos - FACING)^2 where visible.
    cos_dom = FACING + np.sqrt(np.take_along_axis(wts, np.maximum(dominant, 0)[None], 0)[0])
    stretch = 1 / cos_dom[textured]
    # Seams: neighbouring pixels on one plane whose dominant projectors
    # differ, against same-plane neighbours that share one.
    steps = {'handover': [], 'same': []}
    seam_px = np.zeros((H, W), bool)
    for dy, dx in ((0, 1), (1, 0)):
        a = (slice(0, H - dy), slice(0, W - dx)); b = (slice(dy, H), slice(dx, W))
        coplanar = (np.abs((nrm[a] * nrm[b]).sum(-1)) > 0.995) & (np.abs(z[a] - z[b]) < 0.15)
        both = textured[a] & textured[b] & coplanar
        jump = np.abs(blend[a] - blend[b]).mean(-1)
        cross = both & (dominant[a] != dominant[b])
        steps['handover'].append(jump[cross]); steps['same'].append(jump[both & ~cross])
        seam_px[a] |= cross
    handover = np.concatenate(steps['handover']); same = np.concatenate(steps['same'])
    pairs = {}
    for i, j in itertools.combinations(range(len(projectors)), 2):
        both = hull & (wts[i] > 0) & (wts[j] > 0)
        if both.sum() < 0.005 * hull.sum():
            continue
        diff = np.abs(cols[i] - cols[j])[both]
        pairs[f'{projectors[i].view} vs {projectors[j].view}'] = {
            'overlap_share_of_visible_hull': float(both.sum() / hull.sum()),
            'mean_abs_srgb_diff_0_255': float(diff.mean()),
            'mean_luminance_offset_0_255': float(((cols[i] - cols[j])[both] @ np.array([0.2126, 0.7152, 0.0722])).mean())}
    if len(projectors) >= 2:
        # Heat image of where the first two projectors disagree, for the review sheet.
        both = hull & (wts[0] > 0) & (wts[1] > 0)
        heat = np.zeros((H, W, 4), np.float32); heat[..., 3] = 1
        heat[hull, :3] = 0.12
        diff = np.abs(cols[0] - cols[1]).mean(-1) / 255
        heat[both, 0] = np.clip(diff[both] * 3, 0, 1); heat[both, 1] = np.clip(diff[both] * 3 - 0.5, 0, 1)
        heat[both, 2] = 0
        im = bpy.data.images.new('heat', W, H); im.colorspace_settings.name = 'Non-Color'; im.pixels[:] = heat.ravel()
        im.filepath_raw = str(STILLS / f'{tag}-disagreement-{label}.png'); im.file_format = 'PNG'; im.save()
    stats['views'][label] = {
        'pixel_coverage_of_visible_hull': float(textured.sum() / hull.sum()),
        'dominant_share': {projectors[k].view: float((dominant[hull] == k).sum() / hull.sum()) for k in range(len(projectors))},
        'stretch_mean': float(stretch.mean()) if stretch.size else None,
        'stretch_share_over_1_5': float((stretch > 1.5).mean()) if stretch.size else None,
        'handover_pixels_share_of_textured': float(seam_px.sum() / max(textured.sum(), 1)),
        'handover_step_mean_0_255': float(handover.mean()) if handover.size else None,
        'same_projector_step_mean_0_255': float(same.mean()) if same.size else None,
        'pairs': pairs}
    # Seam picture: the blended albedo, hand-overs in cyan.
    img = np.zeros((H, W, 4), np.float32); img[..., 3] = 1
    img[..., :3] = 0.08
    img[textured, :3] = blend[textured] / 255  # display values, written as-is
    img[hull & ~textured, :3] = (0.55, 0.0, 0.5)
    img[seam_px, :3] = (0.0, 0.9, 1.0)
    im = bpy.data.images.new('seams', W, H); im.colorspace_settings.name = 'Non-Color'
    im.pixels[:] = img.ravel()  # EXR rows are already bottom-up, as Blender wants
    im.filepath_raw = str(STILLS / f'{tag}-seams-{label}.png'); im.file_format = 'PNG'; im.save()

scene.view_layers[0].material_override = None
stats.update({'facing_cosine': FACING, 'depth_tolerance_units': TOL,
              'review_views': {LABELS[v]: views.VIEWS[v] or 'build_reference port-side camera' for v in REVIEW_VIEWS}})
(REVIEW / f'{tag}-projection.json').write_text(json.dumps(stats, indent=2) + '\n')
bpy.ops.wm.save_as_mainfile(filepath=str(BUILD / f'{tag}-projected.blend'))
print('SPIKE', json.dumps({v: {k: s[k] for k in ('pixel_coverage_of_visible_hull', 'stretch_mean', 'stretch_share_over_1_5', 'handover_step_mean_0_255', 'same_projector_step_mean_0_255')} for v, s in stats['views'].items()}))
