"""C1 lane traffic cards: vanilla hull plans as lit 3D heightfields, one sun, no ambient.

sh ../../../blender.sh --background
    --factory-startup --python traffic_render_cards.py

The far and mid lane traffic is 6-60 px on screen, where a painted card's detail is
lost and only three things read: the silhouette, the heading and the light. This
renders those three exactly instead of asking an image model for them. Each hull is a
heightfield built from its installed vanilla sprite (read-only; the same crown-and-
relief construction as flotilla_guides.py) textured with the sprite's own colours,
posed in C1's orbital plane at a chosen heading and lit by the shared key:

- the sun is c1_core.SUN_IN_CAMERA (Corvus, upper left and toward camera), hard,
  0.5 degrees wide, with NO world light: shadow sides go black, as in vacuum;
- planet-shine is a faint cool lamp from Jangala's direction (behind the traffic,
  slightly right of frame centre), about 3% of the key;
- nothing is self-lit here; engines and running lights are composited as glows.

Guide mode, for an ImageGen sketch-to-render whose pose AND light must match:
    ... --python traffic_render_cards.py -- --guide <hull> <heading> <px> <out.png>
renders that one hull in plain grey under the same sun and no ambient, so the guide is
both the silhouette authority and the lighting authority, plus <out>.json anchors.

Writes assets/traffic-lane-v1-<hull>-<heading>.png (RGBA, longest side CARD px) and
assets/traffic-lane-v1.json: per card the real length in metres, the pixel scale
(metres per card px) and anchor points in card pixels (sterns, bow, port and
starboard tips, dorsal top) with whether each is visible from the camera.
"""
import json
import math
from pathlib import Path

import bpy
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector

BASE = Path(__file__).resolve().parent
SHIPS = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core\graphics\ships')
OUT = BASE / 'assets'
CARD = 640                      # side of a written card, px (the compositor downsamples)
STEP = 2                        # sprite px per mesh cell
PITCH = math.radians(44)        # C1 camera looks this far down across the orbital plane (flotilla_guides.py)
SUN_IN_CAMERA = Vector((-0.62, 0.50, 0.50))      # c1_core.SUN_IN_CAMERA
PLANET_IN_CAMERA = Vector((0.12, -0.01, -1.0))   # from the traffic toward Jangala's centre

# hull id: (sprite, metres per sprite px). Vanilla sprite px are treated as metres.
HULLS = {
    'tarsus': ('tarsus/tarsus_base.png', 1.0),
    'buffalo': ('buffalo/buffalo_base.png', 1.0),
    'mule': ('mule/mule_base.png', 1.0),
    'atlas': ('atlas/atlas.png', 1.0),
    'prometheus': ('prometheus/prometheus.png', 1.0),
    'colossus': ('colossus/colossus.png', 1.0),
    'valkyrie': ('valkyrie/valkyrie_ap.png', 1.0),
    'hermes': ('hermes/hermes_base.png', 1.0),
    'mercury': ('mercury/mercury_base.png', 1.0),
    'ox': ('ox/ox2.png', 1.0),
    'aeroshuttle': ('aeroshuttle/aeroshuttle_base.png', 1.0),
    'nebula': ('nebula/nebula.png', 1.0),
    'lasher': ('lasher/lasher_base.png', 1.0),
    'dominator': ('dominator/dominator_hegemony.png', 1.0),
}
# heading id: (degrees in the orbital plane from screen-right, positive turns away from camera)
HEADINGS = {
    'in': 22.0,        # inbound lane: toward screen right, a little away (toward the station)
    'out': 180.0 + 14.0,   # outbound: toward screen left, a little toward camera
    'cross': -25.0,    # outbound past the station and tugs: right and toward camera
    'away': 62.0,      # departing toward the descent corridor: right and well away
    'hero': 206.0,     # the hero Dominator's glide: left and toward camera
    'park': 150.0,     # holding at the customs anchorage, bows to upper left
    'flot': 223.8,     # the Hegemony battlegroup's heading (flotilla_guides.HEADING): lower left
}
CARDS = [
    ('tarsus', 'in'), ('buffalo', 'in'), ('mule', 'in'), ('atlas', 'in'), ('prometheus', 'in'),
    ('colossus', 'in'), ('nebula', 'in'), ('valkyrie', 'in'), ('mercury', 'in'), ('hermes', 'in'),
    ('tarsus', 'out'), ('buffalo', 'out'), ('mule', 'out'), ('colossus', 'out'), ('atlas', 'out'),
    ('valkyrie', 'out'), ('mercury', 'out'),
    ('ox', 'cross'), ('aeroshuttle', 'cross'), ('hermes', 'cross'), ('ox', 'in'), ('ox', 'out'),
    ('aeroshuttle', 'away'), ('hermes', 'away'), ('buffalo', 'away'),
    ('lasher', 'flot'), ('hermes', 'out'), ('tarsus', 'park'), ('mule', 'park'), ('buffalo', 'park'), ('nebula', 'park'), ('colossus', 'park'),
    ('tarsus', 'cross'), ('buffalo', 'cross'), ('mule', 'cross'), ('colossus', 'cross'), ('mercury', 'cross'),
]


def heightfield(name, path, mpp):
    img = bpy.data.images.load(str(path), check_existing=True)
    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)                     # row 0 is the bottom (stern) of the sprite
    gw, gh = w // STEP, h // STEP
    cells = px[:gh * STEP, :gw * STEP].reshape(gh, STEP, gw, STEP, 4).mean(axis=(1, 3))
    # Round the plan outline before thresholding, so the hull edge is not a pixel staircase.
    soft = cells[..., 3].copy()
    for _ in range(3):
        pad = np.pad(soft, 1, mode='edge')
        soft = (pad[:-2, 1:-1] + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:] + pad[1:-1, 1:-1] * 2) / 6
    solid = soft > 0.5
    lum = (cells[..., :3] @ np.array([0.3, 0.55, 0.15])) / np.maximum(cells[..., 3], 1e-3)
    dist = np.zeros(solid.shape, dtype=np.float32)
    cur = solid.copy()
    while cur.any():
        dist += cur
        nxt = cur.copy()
        nxt[1:] &= cur[:-1]; nxt[:-1] &= cur[1:]; nxt[:, 1:] &= cur[:, :-1]; nxt[:, :-1] &= cur[:, 1:]
        cur = nxt
    crown = np.sqrt(dist / max(dist.max(), 1))
    sm = lum.copy()
    for _ in range(2):
        sm[1:-1, 1:-1] = (sm[1:-1, 1:-1] * 4 + sm[:-2, 1:-1] + sm[2:, 1:-1] + sm[1:-1, :-2] + sm[1:-1, 2:]) / 8
    length = h
    beam = min(w, h)                             # hull depth scales with the plan's narrow side
    top = beam * (0.04 + 0.11 * crown + 0.02 * np.clip(sm, 0, 1))

    verts, uvs, faces, index = [], [], [], {}

    def vid(i, j):
        key = (i, j)
        if key not in index:
            zs = [top[a, b] for a in (i - 1, i) for b in (j - 1, j)
                  if 0 <= a < gh and 0 <= b < gw and solid[a, b]]
            index[key] = len(verts)
            verts.append(((j * STEP - w / 2) * mpp, (i * STEP - h / 2) * mpp, float(max(zs) if zs else 0.0) * mpp))
            uvs.append((j * STEP / w, i * STEP / h))
        return index[key]

    for i in range(gh):
        for j in range(gw):
            if solid[i, j]:
                faces.append((vid(i, j), vid(i, j + 1), vid(i + 1, j + 1), vid(i + 1, j)))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    uv = mesh.uv_layers.new(name='UV')
    for poly in mesh.polygons:
        for li in poly.loop_indices:
            uv.data[li].uv = uvs[mesh.loops[li].vertex_index]
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    smooth = obj.modifiers.new('round', 'SMOOTH')
    smooth.factor, smooth.iterations = 0.6, 4
    mod = obj.modifiers.new('slab', 'SOLIDIFY')
    mod.thickness = beam * 0.12 * mpp
    mod.offset = -1

    mat = bpy.data.materials.new(name + ' hull')
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = nodes['Principled BSDF']
    tex = nodes.new('ShaderNodeTexImage')
    tex.image = img
    tex.interpolation = 'Linear'
    # The sprite's colours carry baked top-down shading; pull them toward a flat albedo.
    hsv = nodes.new('ShaderNodeHueSaturation')
    hsv.inputs['Saturation'].default_value = 0.85
    hsv.inputs['Value'].default_value = 0.9
    links.new(tex.outputs['Color'], hsv.inputs['Color'])
    links.new(hsv.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.55
    bsdf.inputs['Metallic'].default_value = 0.35
    mesh.materials.append(mat)

    # Anchors in sprite pixels (x right, y up from the stern row), then mesh metres.
    alpha = px[..., 3] > 0.5
    rows = np.where(alpha.any(axis=1))[0]
    r0, r1 = rows.min(), rows.max()
    stern_rows = alpha[r0:r0 + max(2, (r1 - r0) // 25)]
    sx = np.where(stern_rows.any(axis=0))[0]
    widths = alpha.sum(axis=1)
    wide = int(np.argmax(widths))
    cols = np.where(alpha[wide])[0]
    bow_rows = alpha[r1 - max(2, (r1 - r0) // 25):r1 + 1]
    bx = np.where(bow_rows.any(axis=0))[0]
    zmax = float(top.max()) * mpp

    def m(x, y, z):
        return Vector(((x - w / 2) * mpp, (y - h / 2) * mpp, z))

    # Twin sterns where the stern row splits into separate runs, else one centre.
    runs, start = [], None
    occ = stern_rows.any(axis=0)
    for c in range(w + 1):
        on = c < w and occ[c]
        if on and start is None:
            start = c
        if not on and start is not None:
            runs.append((start, c - 1)); start = None
    runs = [r for r in runs if r[1] - r[0] >= 3] or [(sx.min(), sx.max())]
    sterns = [m((a + b) / 2, r0, zmax * 0.4) for a, b in runs[:4]]
    anchors = {
        'sterns': sterns,
        'bow': m(bx.mean(), r1, zmax * 0.7),
        'port': m(cols.min(), wide, zmax * 0.35),
        'starboard': m(cols.max(), wide, zmax * 0.35),
        'top': m(w / 2, (r0 + r1) / 2, zmax),
    }
    anchors['span'] = max(w, h) * mpp
    return obj, length * mpp, anchors


def main():
    import sys
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    guide = None
    if argv[:1] == ['--guide']:
        guide = (argv[1], argv[2], int(argv[3]), Path(argv[4]))
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    res = guide[2] if guide else CARD
    scene.render.resolution_x = scene.render.resolution_y = res
    scene.render.resolution_percentage = 100
    engines = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items]
    scene.render.engine = 'BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in engines else 'BLENDER_EEVEE'
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.view_settings.view_transform = 'Standard'
    scene.world = bpy.data.worlds.new('C1 | vacuum')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.0
    try:
        scene.eevee.use_shadows = True
    except AttributeError:
        pass

    cam = bpy.data.objects.new('C1 | camera', bpy.data.cameras.new('C1 | camera'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.type = 'ORTHO'
    cam.data.clip_start, cam.data.clip_end = 1.0, 1e5
    cam.rotation_euler = (math.radians(90) - PITCH, 0, 0)    # looking +Y, pitched down
    view = Vector((0, math.cos(PITCH), -math.sin(PITCH)))
    bpy.context.view_layer.update()
    to_world = cam.matrix_world.to_3x3()

    def lamp(name, toward_cam, energy, color):
        ob = bpy.data.objects.new(name, bpy.data.lights.new(name, 'SUN'))
        ob.data.energy, ob.data.color = energy, color
        ob.data.angle = math.radians(0.5)
        ob.rotation_euler = (to_world @ toward_cam).normalized().to_track_quat('Z', 'Y').to_euler()
        scene.collection.objects.link(ob)
        return ob

    lamp('C1 | Corvus', SUN_IN_CAMERA.normalized(), 4.2, (1.0, 0.96, 0.88))
    lamp('C1 | planet shine', -PLANET_IN_CAMERA.normalized(), 0.13, (0.55, 0.72, 1.0))

    report = {'card_px': CARD, 'pitch_deg': math.degrees(PITCH), 'sun_in_camera': list(SUN_IN_CAMERA),
              'headings_deg': HEADINGS, 'cards': {}}
    built = {}
    grey = bpy.data.materials.new('guide grey')
    grey.use_nodes = True
    grey.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (0.42, 0.42, 0.42, 1)
    grey.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 0.6
    for hull, heading in ([guide[:2]] if guide else CARDS):
        rel, mpp = HULLS[hull]
        if hull not in built:
            built[hull] = heightfield('lane.' + hull, SHIPS / rel, mpp)
            scene.collection.objects.link(built[hull][0])
            if guide:
                built[hull][0].data.materials[0] = grey
        obj, length, anchors = built[hull]
        for o, *_ in built.values():
            o.hide_render = True
        obj.hide_render = False
        a = math.radians(HEADINGS[heading])
        hdg = Vector((math.cos(a), math.sin(a), 0))           # +x screen right, +y away
        obj.rotation_euler = (0, 0, math.atan2(-hdg.x, hdg.y))
        obj.location = view * 20000
        frame_m = anchors['span'] * 1.25
        cam.data.ortho_scale = frame_m
        cam.location = (0, 0, 0)
        bpy.context.view_layer.update()

        size = guide[2] if guide else CARD

        def project(p):
            q = world_to_camera_view(scene, cam, obj.matrix_world @ p)
            return [q.x * size, (1 - q.y) * size]

        stem = 'traffic-lane-v1-%s-%s' % (hull, heading)
        scene.render.filepath = str(guide[3] if guide else OUT / (stem + '.png'))
        bpy.ops.render.render(write_still=True)
        report['cards'][stem] = {
            'hull': hull, 'heading': heading, 'length_m': length,
            'm_per_card_px': frame_m / size,
            'sterns': [project(p) for p in anchors['sterns']],
            # how squarely the sterns face the camera: 1 nozzles on, 0 edge-on, <0 hidden
            'stern_facing': round((obj.matrix_world.to_3x3() @ Vector((0, -1, 0))).normalized().dot(-view), 3),
            'bow': project(anchors['bow']),
            'port': project(anchors['port']),
            'starboard': project(anchors['starboard']),
            'top': project(anchors['top']),
        }
        print(stem, report['cards'][stem]['sterns'])
    if guide:
        guide[3].with_suffix('.json').write_text(json.dumps(report, indent=1))
        return
    (OUT / 'traffic-lane-v1.json').write_text(json.dumps(report, indent=1))


main()
