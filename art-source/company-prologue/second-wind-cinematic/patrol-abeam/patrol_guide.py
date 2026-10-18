"""C2 PATROL ABEAM: the Hegemony Dominator's blockout on its planned track.

sh ../../../blender.sh --background
    --factory-startup --python patrol_guide.py [-- --blockout-only]

The hull is a heightfield built from the vanilla top-down sprite (read-only, from the
install): the sprite's alpha is the plan, an inward distance field is the crown, its
luminance adds plate relief, and a solidify gives the slab side walls. Turret domes sit
on the .ship file's weapon slots and emissive discs on its engine slots, so the
silhouette carries the barbettes and the engine block that identify the class.

Everything is placed by c2_core: the C2 cabin camera (26 mm, 18.6 degrees down, looking
out to port), the sun, the planet-shine and the patrol's track. Writes

  guides/patrol-pose-{entry,abeam,exit}.png     3840x2160, the ImageGen silhouette
                                                guides (grey form, a weak frontal
                                                fill so the shadowed flank reads)
  guides/patrol-light-{entry,abeam,exit}.png    3840x2160, the true light only: sun
                                                and planet-shine, no fill, no ambient
  guides/patrol-blockout-tNN.N.png              1920x1080 true-light frames at the
                                                review-still times
  guides/patrol-guides.json                     track, scale and per-time screen boxes
"""
import json
import math
import re
import sys
from pathlib import Path

import bpy
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c2_core as C  # noqa: E402

INSTALL = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
SPRITE = INSTALL / 'graphics' / 'ships' / 'dominator' / 'dominator_hegemony.png'
SHIP = INSTALL / 'data' / 'hulls' / 'dominator.ship'
STEP = 1                                   # sprite px per mesh cell: this hull is close
KEY_POSES = {'entry': 3.0, 'abeam': 8.0, 'exit': 11.4}
BLOCKOUT_TIMES = [0.6, 3.0, 8.0, 11.4, 12.6]


def heightfield(name):
    img = bpy.data.images.load(str(SPRITE), check_existing=True)
    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)                     # row 0 is the bottom (stern) of the sprite
    gw, gh = w // STEP, h // STEP
    cells = px[:gh * STEP, :gw * STEP].reshape(gh, STEP, gw, STEP, 4).mean(axis=(1, 3))
    solid = cells[..., 3] > 0.5
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
    for _ in range(3):
        sm[1:-1, 1:-1] = (sm[1:-1, 1:-1] * 4 + sm[:-2, 1:-1] + sm[2:, 1:-1] + sm[1:-1, :-2] + sm[1:-1, 2:]) / 8
    length = h
    top = length * (0.03 + 0.07 * crown + 0.015 * np.clip(sm, 0, 1))

    verts, faces, index = [], [], {}

    def vid(i, j):
        key = (i, j)
        if key not in index:
            zs = [top[a, b] for a in (i - 1, i) for b in (j - 1, j)
                  if 0 <= a < gh and 0 <= b < gw and solid[a, b]]
            index[key] = len(verts)
            verts.append(((j * STEP - w / 2), (i * STEP - h / 2), float(max(zs) if zs else 0.0)))
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
    mod.thickness = C.PATROL_SLAB_M
    mod.offset = -1

    def top_at(col, row):
        i, j = int(min(gh - 1, max(0, row // STEP))), int(min(gw - 1, max(0, col // STEP)))
        return float(top[i, j]) if solid[i, j] else float(top.max() * 0.6)
    return obj, w, h, top_at


def ship_slots():
    text = re.sub(r'#.*', '', SHIP.read_text(encoding='utf-8'))
    data = json.loads(text)
    return data['center'], data['weaponSlots'], data['engineSlots']


def material(name, colour, emission=None, strength=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = (*colour, 1)
    bsdf.inputs['Roughness'].default_value = 0.7
    if emission:
        bsdf.inputs['Emission Color'].default_value = (*emission, 1)
        bsdf.inputs['Emission Strength'].default_value = strength
    return m


def build_patrol(scene):
    root = bpy.data.objects.new('C2 | patrol root', None)
    scene.collection.objects.link(root)
    hull, w, h, top_at = heightfield('C2 | Dominator hull')
    grey = material('guide grey', (0.42, 0.42, 0.42))
    hull.data.materials.append(grey)
    scene.collection.objects.link(hull)
    hull.parent = root
    (cx, cy), weapons, engines = ship_slots()

    # Ship coordinates (.ship): x forward, y to port. Mesh coordinates: X starboard-right of
    # the bow-up sprite, Y toward the bow, both centred on the sprite.
    def mesh_xy(x, y):
        return (cx - y) - w / 2, (cy + x) - h / 2

    for slot in weapons:
        sx, sy = slot['locations'][0], slot['locations'][1]
        mx, my = mesh_xy(sx, sy)
        r = {'SMALL': 4.0, 'MEDIUM': 6.0, 'LARGE': 9.0}[slot['size']]
        z = top_at(mx + w / 2, my + h / 2)
        bpy.ops.mesh.primitive_uv_sphere_add(radius=r, segments=24, ring_count=12, location=(mx, my, z))
        dome = bpy.context.active_object
        dome.scale = (1, 1, 0.55)
        dome.data.materials.append(grey)
        dome.parent = hull
        bpy.ops.mesh.primitive_cylinder_add(radius=r * 0.16, depth=r * 2.2, vertices=12,
                                            location=(mx, my + r * 1.2, z + r * 0.3), rotation=(math.radians(90), 0, 0))
        barrel = bpy.context.active_object
        barrel.data.materials.append(grey)
        barrel.parent = hull

    glow = material('engine glow', (0.0, 0.0, 0.0), emission=(0.75, 0.82, 1.0), strength=30.0)
    nozzle = material('nozzle', (0.2, 0.2, 0.2))
    for slot in engines:
        sx, sy = slot['location']
        mx, my = mesh_xy(sx, sy)
        r = slot['width'] / 2
        bpy.ops.mesh.primitive_cylinder_add(radius=r * 1.1, depth=r * 1.0, vertices=24,
                                            location=(mx, my - r * 0.3, -6.0), rotation=(math.radians(90), 0, 0))
        bell = bpy.context.active_object
        bell.data.materials.append(nozzle)
        bell.parent = hull
        bpy.ops.mesh.primitive_circle_add(radius=r, vertices=24, fill_type='NGON',
                                          location=(mx, my - r * 0.82, -6.0), rotation=(math.radians(90), 0, 0))
        disc = bpy.context.active_object
        disc.data.materials.append(glow)
        disc.parent = hull
    hull.rotation_euler = (0, 0, math.radians(-90))   # bow (mesh +Y) to world +X; starboard faces us
    return root


def setup(scene):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    cam = bpy.data.objects.new('C2 | cabin camera', bpy.data.cameras.new('C2 | cabin camera'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.lens = C.LENS_MM
    cam.data.sensor_fit = 'HORIZONTAL'
    cam.data.sensor_width = C.SENSOR_MM
    cam.data.clip_start = 0.1
    cam.data.clip_end = 1e5
    cam.location = (0, 0, 0)
    cam.rotation_euler = (math.radians(90 - C.PITCH_DEG), 0, 0)   # looking +Y (port), pitched down

    sun = bpy.data.objects.new('C2 | Corvus', bpy.data.lights.new('C2 | Corvus', 'SUN'))
    sun.data.energy = 5.0
    sun.data.color = C.SUN_COLOUR
    sun.data.angle = math.radians(0.5)
    sun.rotation_euler = Vector(C.SUN).to_track_quat('Z', 'Y').to_euler()
    shine = bpy.data.objects.new('C2 | Jangala shine', bpy.data.lights.new('C2 | Jangala shine', 'SUN'))
    shine.data.energy = 5.0 * C.PLANETSHINE
    shine.data.color = (0.55, 0.72, 1.0)
    shine.data.angle = math.radians(60)
    shine.rotation_euler = Vector((0, 0.3, -1)).normalized().to_track_quat('Z', 'Y').to_euler()
    fill = bpy.data.objects.new('C2 | guide fill (form guides only)', bpy.data.lights.new('C2 | guide fill', 'SUN'))
    fill.data.energy = 1.4
    fill.rotation_euler = Vector((0.15, -1.0, 0.25)).normalized().to_track_quat('Z', 'Y').to_euler()
    for lamp in (sun, shine, fill):
        scene.collection.objects.link(lamp)
    scene.world = bpy.data.worlds.new('C2 | space, no ambient')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.0

    engines = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items]
    scene.render.engine = 'BLENDER_EEVEE_NEXT' if 'BLENDER_EEVEE_NEXT' in engines else 'BLENDER_EEVEE'
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.use_compositing = False
    scene.view_settings.view_transform = 'Standard'
    return scene, cam, fill


def place(root, t):
    root.location = C.patrol_position(t)
    bpy.context.view_layer.update()


def screen_box(scene, cam, root):
    """Frame-pixel bounds of the whole hull (unclipped by the frame or the glass)."""
    depsgraph = bpy.context.evaluated_depsgraph_get()
    fwd, right, up = (np.array(v) for v in C.basis())
    xs, ys = [], []
    for child in root.children_recursive:
        if child.type != 'MESH':
            continue
        ev = child.evaluated_get(depsgraph)
        n = len(ev.data.vertices)
        co = np.empty(n * 3, dtype=np.float64)
        ev.data.vertices.foreach_get('co', co)
        co = np.c_[co.reshape(n, 3), np.ones(n)] @ np.array(ev.matrix_world).T
        d = co[:, :3] @ fwd
        ok = d > 1
        xs += list(C.W / 2 + C.F_PX * (co[ok, :3] @ right) / d[ok])
        ys += list(C.H / 2 - C.F_PX * (co[ok, :3] @ up) / d[ok])
    return [round(min(xs), 1), round(min(ys), 1), round(max(xs), 1), round(max(ys), 1)]


def render(scene, path, scale):
    scene.render.resolution_x, scene.render.resolution_y = C.W * scale, C.H * scale
    scene.render.resolution_percentage = 100
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)
    print('Wrote', path)


def main():
    if not bpy.app.background:
        raise RuntimeError('Use background Blender')
    blockout_only = '--blockout-only' in sys.argv
    scene, cam, fill = setup(None)
    root = build_patrol(scene)
    out = BASE / 'guides'
    out.mkdir(exist_ok=True)
    report = {'camera': {'lens_mm': C.LENS_MM, 'sensor_mm': C.SENSOR_MM, 'pitch_down_deg': C.PITCH_DEG,
                         'focal_px_1920': round(C.F_PX, 1), 'looks': 'out to port (+Y); bow (+X) is screen right'},
              'patrol': {'class': 'Hegemony Dominator (dominator_hegemony.png, sprite px read as metres)',
                         'length_m': C.PATROL_LENGTH_M, 'beam_m': C.PATROL_BEAM_M,
                         'near_flank_m': C.PATROL_NEAR_FLANK_M, 'speed_m_s': C.PATROL_SPEED,
                         'abeam_at_c2_s': C.PATROL_T_MID, 'rise_from_c2_s': C.PATROL_DROP_T,
                         'rise_accel_m_s2': C.PATROL_DROP_ACCEL},
              'sun_world': C.SUN, 'boxes_frame_px': {}}
    for t in sorted(set(BLOCKOUT_TIMES) | set(KEY_POSES.values()) | {0.0, 1.0, 2.0, 5.0, 10.0, 11.0, 12.0, 13.0, 13.5, 14.0, 14.5, 15.0}):
        place(root, t)
        report['boxes_frame_px']['%.1f' % t] = screen_box(scene, cam, root)
    for t in BLOCKOUT_TIMES:
        place(root, t)
        fill.hide_render = True
        render(scene, out / ('patrol-blockout-t%04.1f.png' % t), 1)
    if not blockout_only:
        for name, t in KEY_POSES.items():
            place(root, t)
            fill.hide_render = False
            render(scene, out / ('patrol-pose-%s.png' % name), 2)
            fill.hide_render = True
            render(scene, out / ('patrol-light-%s.png' % name), 2)
    report['key_poses_c2_s'] = KEY_POSES
    (out / 'patrol-guides.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report['boxes_frame_px'], indent=1))


main()
