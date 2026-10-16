"""Second Wind as a C1 cut-out, alpha, one sun.

blender --background --factory-startup --python render_ship_card.py [-- --view entrance]

Views: `dorsal` (starboard three-quarter from above, bow right) and `entrance`
(rear three-quarter from above and astern, so the ship reads as heading away
from camera toward Jangala, engines toward the viewer).

Opens the damaged full-surface master read-only and writes
layers/second-wind.png (RGBA). The camera looks down across the orbital plane
the way the whole C1 plate does, bow toward screen right (toward Jangala). Light
is a hard sun from the upper left, matching the planet plate, plus a faint blue
fill from below for planet-shine. Never saves over the source asset.
"""
import math
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

BASE = Path(__file__).resolve().parent
ASSET = BASE.parent / 'second-wind-full-surface' / 'second-wind-full-surface.blend'
VIEWS = {'dorsal': Vector((0.18, -0.75, 1.0)),      # from above, starboard side, slightly ahead
         'entrance': Vector((-1.0, -0.55, 0.62))}   # astern, starboard, above: heading away
SUN = Vector((-0.6, -0.35, 0.75))      # upper left of frame, as on the planet plate


def point_at(obj, point):
    obj.rotation_euler = (Vector(point) - obj.location).to_track_quat('-Z', 'Y').to_euler()


def main():
    import sys
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    view = argv[argv.index('--view') + 1] if '--view' in argv else 'dorsal'
    OUT = BASE / 'layers' / ('second-wind.png' if view == 'dorsal' else 'second-wind-%s.png' % view)
    VIEW = VIEWS[view]
    if not bpy.app.background:
        raise RuntimeError('Use background Blender')
    bpy.ops.wm.open_mainfile(filepath=str(ASSET))
    scene = bpy.context.scene
    root = bpy.data.objects.get('SECOND WIND | flight root')
    if root is None:
        raise RuntimeError('Missing SECOND WIND | flight root')
    ship = {root, *root.children_recursive}
    for obj in list(bpy.data.objects):
        if obj not in ship:
            bpy.data.objects.remove(obj, do_unlink=True)
    for obj in ship:
        obj.animation_data_clear()
    root.matrix_world = Matrix.Diagonal((*root.scale, 1))
    bpy.context.view_layer.update()
    visible = [o for o in ship if o.type == 'MESH' and not o.hide_render]
    points = [o.matrix_world @ Vector(c) for o in visible for c in o.bound_box]
    lo = Vector(tuple(min(p[i] for p in points) for i in range(3)))
    hi = Vector(tuple(max(p[i] for p in points) for i in range(3)))
    center, span = (lo + hi) / 2, (hi - lo).length

    scene.world = bpy.data.worlds.new('C1 | space')
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes['Background']
    bg.inputs['Color'].default_value = (0.02, 0.03, 0.05, 1)
    bg.inputs['Strength'].default_value = 0.15

    sun = bpy.data.objects.new('C1 | sun', bpy.data.lights.new('C1 | sun', 'SUN'))
    sun.data.energy = 4.5
    sun.data.angle = math.radians(0.6)
    sun.location = center + SUN.normalized() * span
    point_at(sun, center)
    shine = bpy.data.objects.new('C1 | planet shine', bpy.data.lights.new('C1 | planet shine', 'SUN'))
    shine.data.energy = 0.35
    shine.data.color = (0.55, 0.75, 1.0)
    shine.location = center + Vector((0.5, 0.3, -1)).normalized() * span
    point_at(shine, center)
    for lamp in (sun, shine):
        scene.collection.objects.link(lamp)

    cam = bpy.data.objects.new('C1 | camera', bpy.data.cameras.new('C1 | camera'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = span * 1.05
    cam.data.clip_end = span * 100
    cam.location = center + VIEW.normalized() * span * 3
    point_at(cam, center)

    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 64
    scene.cycles.use_denoising = True
    prefs = bpy.context.preferences.addons['cycles'].preferences
    prefs.compute_device_type = 'OPTIX'
    prefs.get_devices()
    if not [d for d in prefs.devices if d.type == 'OPTIX']:
        raise RuntimeError('OPTIX GPU required; no silent CPU fallback')
    for device in prefs.devices:
        device.use = device.type == 'OPTIX'
    scene.cycles.device = 'GPU'
    scene.render.resolution_x = 2048
    scene.render.resolution_y = 1152
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.use_compositing = False
    scene.view_settings.view_transform = 'AgX'
    OUT.parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(OUT)
    bpy.ops.render.render(write_still=True)
    print('Wrote', OUT)


main()
