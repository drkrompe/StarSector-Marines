"""C1 distant Hegemony flotilla: grey blockout guides for ImageGen sketch-to-render.

"C:\\Program Files\\Blender Foundation\\Blender 5.2\\blender.exe" --background
    --factory-startup --python flotilla_guides.py

Each hull is a heightfield built from its vanilla top-down sprite (read-only, from
the install): the sprite's alpha is the plan silhouette, an inward distance field
gives the hull its crown and the sprite's luminance adds plate relief, and a
solidify gives the slab real side walls in perspective. Every bow is on one
heading toward the inbound lane (screen lower left). The camera is the C1 camera:
above the orbital plane, looking slightly down, slightly ahead of the ships. The
sun is from the upper left.

Both guides are rendered through ONE full-frame 1920x1080 camera at SCALE x and
cropped to the same WINDOW, so a painted result composites at the window's frame
anchor with no further placement. Writes guides/flotilla-mid.png (capital group),
guides/flotilla-far.png (escort wedge) and guides/flotilla-guides.json (window,
scale, per-hull projected boxes in final-frame pixels).
"""
import json
import math
from pathlib import Path

import bpy
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

BASE = Path(__file__).resolve().parent
SHIPS = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core\graphics\ships')
SPRITES = {
    'onslaught': SHIPS / 'onslaught' / 'onslaught_hegemony.png',
    'dominator': SHIPS / 'dominator' / 'dominator_hegemony.png',
    'eagle': SHIPS / 'eagle' / 'eagle_hegemony.png',
    'enforcer': SHIPS / 'enforcer' / 'enforcer_hegemony.png',
    'lasher': SHIPS / 'lasher' / 'lasher_base.png',
}
FRAME = (1920, 1080)
SCALE = 3                                  # guide pixels per final-frame pixel
WINDOW = (247, 148, 823, 472)              # final-frame crop shared by both guides (16:9)
# Each layer's formation hangs off one anchor: the point the ray through that
# final-frame pixel reaches at that distance (sprite px are treated as metres).
ANCHORS = {'mid': ((450, 330), 7600.0),    # the flagship
           'far': ((610, 255), 11000.0)}   # the wedge's lead Eagle, beyond the capitals
PITCH = math.radians(44)                   # camera looks this far down across the plane
HEADING = Vector((-0.72, -0.69, 0)).normalized()   # toward camera-left: screen lower left
SUN = Vector((-0.75, -0.25, 0.62))         # upper left of frame
STEP = 2                                   # sprite px per mesh cell

# (layer, class, metres along heading, metres to the ship's right) from the layer anchor
FORMATION = [
    ('mid', 'onslaught', 0, 0),
    ('mid', 'dominator', 120, -360),
    ('mid', 'dominator', -230, 380),
    ('far', 'eagle', 0, 0),
    ('far', 'eagle', -243, -433),
    ('far', 'eagle', -338, 467),
    ('far', 'enforcer', -363, -667),
    ('far', 'enforcer', -620, -76),
    ('far', 'enforcer', -528, 316),
    ('far', 'lasher', -183, -127),
    ('far', 'lasher', -254, 177),
]


def heightfield(name, path):
    img = bpy.data.images.load(str(path), check_existing=True)
    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)                     # row 0 is the bottom of the sprite
    gw, gh = w // STEP, h // STEP
    cells = px[:gh * STEP, :gw * STEP].reshape(gh, STEP, gw, STEP, 4).mean(axis=(1, 3))
    solid = cells[..., 3] > 0.5
    lum = (cells[..., :3] @ np.array([0.3, 0.55, 0.15])) / np.maximum(cells[..., 3], 1e-3)
    # Inward distance (cells) by repeated erosion: the crown of the hull.
    dist = np.zeros(solid.shape, dtype=np.float32)
    cur = solid.copy()
    while cur.any():
        dist += cur
        nxt = cur.copy()
        nxt[1:] &= cur[:-1]; nxt[:-1] &= cur[1:]; nxt[:, 1:] &= cur[:, :-1]; nxt[:, :-1] &= cur[:, 1:]
        cur = nxt
    crown = np.sqrt(dist / max(dist.max(), 1))
    length = h
    # Smooth luminance a little so relief reads as plates, not pixel noise.
    sm = lum.copy()
    for _ in range(2):
        sm[1:-1, 1:-1] = (sm[1:-1, 1:-1] * 4 + sm[:-2, 1:-1] + sm[2:, 1:-1] + sm[1:-1, :-2] + sm[1:-1, 2:]) / 8
    top = length * (0.03 + 0.07 * crown + 0.015 * np.clip(sm, 0, 1))

    verts, faces, index = [], [], {}
    def vid(i, j):
        key = (i, j)
        if key not in index:
            ii, jj = min(i, gh - 1), min(j, gw - 1)
            # corner height: max of the solid cells touching it
            zs = [top[a, b] for a in (i - 1, i) for b in (j - 1, j)
                  if 0 <= a < gh and 0 <= b < gw and solid[a, b]]
            z = max(zs) if zs else 0.0
            index[key] = len(verts)
            verts.append(((j * STEP - w / 2), (i * STEP - h / 2), float(z)))
        return index[key]
    for i in range(gh):
        for j in range(gw):
            if solid[i, j]:
                faces.append((vid(i, j), vid(i, j + 1), vid(i + 1, j + 1), vid(i + 1, j)))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    mod = obj.modifiers.new('slab', 'SOLIDIFY')
    mod.thickness = length * 0.05
    mod.offset = -1
    return obj, length


def main():
    if not bpy.app.background:
        raise RuntimeError('Use background Blender')
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.resolution_x, scene.render.resolution_y = FRAME[0] * SCALE, FRAME[1] * SCALE
    scene.render.resolution_percentage = 100

    cam = bpy.data.objects.new('C1 | camera', bpy.data.cameras.new('C1 | camera'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.lens = 50
    cam.data.sensor_fit = 'HORIZONTAL'
    cam.data.sensor_width = 36
    cam.data.clip_end = 1e6
    cam.location = (0, 0, 0)
    cam.rotation_euler = (math.radians(90) - PITCH, 0, 0)    # looking +Y, pitched down
    bpy.context.view_layer.update()

    fx = cam.data.lens / cam.data.sensor_width
    anchors = {}
    for layer, ((px, py), distance) in ANCHORS.items():
        sx = px / FRAME[0] - 0.5
        sy = (0.5 - py / FRAME[1]) * FRAME[1] / FRAME[0]
        anchors[layer] = (cam.matrix_world.to_3x3() @ Vector((sx, sy, -fx))).normalized() * distance
    right = Vector((HEADING.y, -HEADING.x, 0))
    yaw = math.atan2(-HEADING.x, HEADING.y)

    material = bpy.data.materials.new('guide grey')
    material.use_nodes = True
    bsdf = material.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = (0.42, 0.42, 0.42, 1)
    bsdf.inputs['Roughness'].default_value = 0.65

    hulls = []
    for n, (layer, cls, along, side) in enumerate(FORMATION):
        obj, length = heightfield('%s.%s.%02d' % (layer, cls, n), SPRITES[cls])
        obj.data.materials.append(material)
        scene.collection.objects.link(obj)
        obj.location = anchors[layer] + HEADING * along + right * side
        obj.rotation_euler = (0, 0, yaw)
        hulls.append((layer, cls, obj))

    sun = bpy.data.objects.new('C1 | sun', bpy.data.lights.new('C1 | sun', 'SUN'))
    sun.data.energy = 4.0
    sun.data.angle = math.radians(0.5)
    sun.rotation_euler = SUN.normalized().to_track_quat('Z', 'Y').to_euler()
    fill = bpy.data.objects.new('C1 | planet shine', bpy.data.lights.new('C1 | planet shine', 'SUN'))
    fill.data.energy = 0.4
    fill.rotation_euler = Vector((0.4, 0.3, -1)).normalized().to_track_quat('Z', 'Y').to_euler()
    for lamp in (sun, fill):
        scene.collection.objects.link(lamp)
    scene.world = bpy.data.worlds.new('C1 | space')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.08

    engines = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items]
    scene.render.engine = 'BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in engines else 'BLENDER_EEVEE'
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.use_compositing = False
    scene.view_settings.view_transform = 'Standard'
    scene.render.use_border = True
    scene.render.use_crop_to_border = True
    x0, y0, x1, y1 = WINDOW
    scene.render.border_min_x, scene.render.border_max_x = x0 / FRAME[0], x1 / FRAME[0]
    scene.render.border_min_y, scene.render.border_max_y = 1 - y1 / FRAME[1], 1 - y0 / FRAME[1]
    bpy.context.view_layer.update()

    report = {'frame': FRAME, 'scale': SCALE, 'window_frame_px': WINDOW,
              'window_centre_frame_px': [(x0 + x1) / 2, (y0 + y1) / 2],
              'guide_px': [(x1 - x0) * SCALE, (y1 - y0) * SCALE],
              'heading_world': list(HEADING), 'pitch_deg': math.degrees(PITCH), 'hulls': []}
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for layer, cls, obj in hulls:
        ev = obj.evaluated_get(depsgraph)
        pts = [world_to_camera_view(scene, cam, ev.matrix_world @ v.co) for v in ev.data.vertices]
        xs = [p.x * FRAME[0] for p in pts]; ys = [(1 - p.y) * FRAME[1] for p in pts]
        box = [round(min(xs), 1), round(min(ys), 1), round(max(xs), 1), round(max(ys), 1)]
        report['hulls'].append({'layer': layer, 'class': cls, 'frame_box': box,
                                'longest_px': round(max(box[2] - box[0], box[3] - box[1]), 1)})
        print(layer, cls, box, report['hulls'][-1]['longest_px'])

    out = BASE / 'guides'
    out.mkdir(exist_ok=True)
    for layer in ('mid', 'far'):
        for l, _, obj in hulls:
            obj.hide_render = l != layer
        scene.render.filepath = str(out / ('flotilla-%s.png' % layer))
        bpy.ops.render.render(write_still=True)
        print('Wrote', scene.render.filepath)
    (out / 'flotilla-guides.json').write_text(json.dumps(report, indent=2))


main()
