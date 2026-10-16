"""Select every part's faces on the clean build and render each source part alone.

  blender --background --factory-startup --python render_parts.py [-- --group g[,g]] [--only part,part]

Needs build/geom/second-wind-geometry.blend (rebuild.sh step 1: the clean
geometry build, imported, never edited). Selection runs over every group's
parts, so overlaps resolve the same way they will in the integrated build;
only the named groups' parts are listed and rendered. Writes:
  part-list/<group>.json    every part: members, face indices per object, area,
                            view extent, tile camera, texels per model unit
  guide/<part>-guide.png    the part's faces alone, square-on, base materials
                            under a uniform white world (no lamps), on BACKGROUND
  guide/<part>-mask.png     coverage
  guide/<part>-class.png    flat material class (CLASS_ID colours): what the
                            guide says is cream / oxide / gunmetal / dark
  build/<group>/tiles/<part>-normal.png  flat world normal, for plane-wise de-lighting
Only source parts (those in a sheet) are rendered; skin_from parts reuse one.
"""
from pathlib import Path
import json
import sys
import bpy
import bmesh
import numpy as np
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import parts as P

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
ONLY = argv[argv.index('--only') + 1].split(',') if '--only' in argv else None
GROUPS = P.group_arg(argv)
MINE = P.parts_in(GROUPS)
GUIDE = HERE / 'guide'
GUIDE.mkdir(exist_ok=True)
(HERE / 'part-list').mkdir(exist_ok=True)

bpy.ops.wm.open_mainfile(filepath=str(HERE / 'build' / 'geom' / 'second-wind-geometry.blend'))
scene = bpy.context.scene
for ob in [o for o in scene.objects if o.type == 'LIGHT']:
    bpy.data.objects.remove(ob)
selection = P.all_selections(strict=set(MINE))

# ---- part list
listing = {}
for part in MINE:
    spec = P.PARTS[part]
    faces = selection[part]
    ext = P.group_extent(part, faces)
    src = P.source_part(part)
    cam = P.tile_camera(part, ext)
    area = sum(bpy.data.objects[n].data.polygons[i].area for n, idx in faces.items() for i in idx)
    listing[part] = {
        'what': spec['what'], 'sheet': P.sheet_of(src)[0], 'tile': P.sheet_of(src)[1], 'skin_from': spec.get('skin_from'),
        'members': [{'object': n, 'seed': list(s), 'faces': faces[n]} for n, s in spec['members']],
        'face_count': sum(len(v) for v in faces.values()), 'area_units2': round(area, 2),
        'view_dir': list(spec['dir']), 'screen_up': list(spec['up']), 'cone_cos': spec['cone'],
        'extent_units': [round(ext['w'], 2), round(ext['h'], 2)],
        'tile_camera': {'centre_uv': [cam[0], cam[1]], 'ortho_width': cam[2]},
        # The generated sheet comes back 1672 px wide: one tile is TILE/SHEET of it.
        'native_texels_per_unit': round(1672 * P.TILE[0] / P.SHEET[0] / cam[2], 1),
    }
for g in sorted({P.PART_GROUP[p] for p in listing}):
    rows = {p: v for p, v in listing.items() if P.PART_GROUP[p] == g}
    path = HERE / 'part-list' / f'{g}.json'
    if ONLY and path.exists():  # a partial run keeps the group's other rows
        rows = json.loads(path.read_text()) | rows
    path.write_text(json.dumps(rows, indent=1) + '\n')
for p, v in listing.items():
    print('SURF', f"{p:26s} faces {v['face_count']:4d} area {v['area_units2']:8.1f} extent {v['extent_units']} t/u {v['native_texels_per_unit']}")

# ---- render setup: uniform white world, no lamps (as the spike's guides)
scene.render.engine = 'CYCLES'
scene.render.resolution_x, scene.render.resolution_y = P.TILE
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.view_settings.view_transform = 'Standard'
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (1, 1, 1, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 1.6
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
camdata = bpy.data.cameras.new('SURF tile')
camdata.type = 'ORTHO'
camdata.clip_end = 800
cam = bpy.data.objects.new('SURF tile', camdata)
scene.collection.objects.link(cam)
scene.camera = cam
originals = [o for o in scene.objects if o.type == 'MESH']


def emission(name, rgb, socket=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    e = nt.nodes.new('ShaderNodeEmission')
    o = nt.nodes.new('ShaderNodeOutputMaterial')
    if socket == 'normal':
        g = nt.nodes.new('ShaderNodeNewGeometry')
        vm = nt.nodes.new('ShaderNodeVectorMath'); vm.operation = 'MULTIPLY_ADD'
        vm.inputs[1].default_value = (.5, .5, .5); vm.inputs[2].default_value = (.5, .5, .5)
        nt.links.new(g.outputs['True Normal'], vm.inputs[0])
        nt.links.new(vm.outputs[0], e.inputs['Color'])
    else:
        e.inputs['Color'].default_value = (*[c / 255 for c in rgb], 1)
    nt.links.new(e.outputs[0], o.inputs['Surface'])
    return m


CLASS_MATS = {c: emission('SURF class ' + c, rgb) for c, rgb in P.CLASS_ID.items()}
NORMAL_MAT = emission('SURF normal', None, 'normal')


def isolate(part):
    """Temporary objects holding only the part's faces (modifiers kept)."""
    temps = []
    for name, idx in selection[part].items():
        src = bpy.data.objects[name]
        ob = src.copy()
        ob.data = src.data.copy()
        ob.name = 'SURF temp ' + name
        ob.hide_render = False
        scene.collection.objects.link(ob)
        bm = bmesh.new()
        bm.from_mesh(ob.data)
        keep = set(idx)
        bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.index not in keep], context='FACES')
        bm.to_mesh(ob.data)
        bm.free()
        temps.append(ob)
    return temps


def place_camera(part):
    spec = P.PARTS[part]
    x, y, z = P.basis(spec)
    c = listing[part]['tile_camera']
    u, v = c['centre_uv']
    centre = x * u + y * v + z * 0
    ext = P.group_extent(part, selection[part])
    cam.matrix_world = Matrix.Translation(centre + z * (ext['wmax'] + 150)) @ Matrix((x, y, z)).transposed().to_4x4()
    camdata.ortho_scale = c['ortho_width']
    bpy.context.view_layer.update()


def render(path, samples, filt=1.5):
    scene.cycles.samples = samples
    scene.cycles.filter_width = filt
    scene.cycles.use_denoising = samples > 8
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def load(path):
    im = bpy.data.images.load(str(path))
    w, h = im.size
    a = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1]
    bpy.data.images.remove(im)
    return a


def save(arr, path):
    h, w = arr.shape[:2]
    rgba = np.concatenate([arr, np.ones((h, w, 1), np.float32)], 2)
    im = bpy.data.images.new(path.stem, w, h, alpha=True)
    im.colorspace_settings.name = 'Non-Color'
    im.pixels[:] = rgba[::-1].ravel()
    im.filepath_raw = str(path)
    im.file_format = 'PNG'
    im.save()
    bpy.data.images.remove(im)


bg = np.array(P.BACKGROUND, np.float32) / 255
for part in MINE:
    if P.source_part(part) != part or (ONLY and part not in ONLY):
        continue
    TILES = P.tiles_dir(part)
    for o in originals:
        o.hide_render = True
    temps = isolate(part)
    place_camera(part)
    scene.view_settings.view_transform = 'Standard'
    scene.world.node_tree.nodes['Background'].inputs[1].default_value = 1.6
    render(TILES / f'{part}-rgba.png', 64)
    rgba = load(TILES / f'{part}-rgba.png')
    a = rgba[..., 3:4]
    save(rgba[..., :3] * a + bg * (1 - a), GUIDE / f'{part}-guide.png')
    save(np.repeat(a, 3, 2), GUIDE / f'{part}-mask.png')
    # Flat passes: one sample, minimal filter, no world light.
    scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0
    scene.view_settings.view_transform = 'Raw' if 'Raw' in [t.identifier for t in
        bpy.types.ColorManagedViewSettings.bl_rna.properties['view_transform'].enum_items] else 'Standard'
    for ob in temps:
        for slot in ob.material_slots:
            cls = P.MATERIAL_CLASS.get(slot.material.name.split('.')[0], 'other') if slot.material else 'other'
            slot.link = 'OBJECT'
            slot.material = CLASS_MATS[cls]
    render(TILES / f'{part}-class-rgba.png', 1, .01)
    cl = load(TILES / f'{part}-class-rgba.png')
    save(cl[..., :3] * (cl[..., 3:4] > .5), GUIDE / f'{part}-class.png')
    for ob in temps:
        for slot in ob.material_slots:
            slot.material = NORMAL_MAT
    render(TILES / f'{part}-normal-rgba.png', 1, .01)
    nm = load(TILES / f'{part}-normal-rgba.png')
    save(nm[..., :3] * (nm[..., 3:4] > .5), TILES / f'{part}-normal.png')
    for ob in temps:
        bpy.data.objects.remove(ob)
    print('SURF rendered', part)
