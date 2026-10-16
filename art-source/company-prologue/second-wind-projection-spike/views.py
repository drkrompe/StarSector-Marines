"""Cameras shared by render_guide.py and project.py (Blender only).

'port-side' is build_reference.py's own camera, used unchanged. The others orbit
the same target at the same distance: azimuth swings from the port beam toward
the bow, elevation lifts above the deck datum. All are orthographic, 1920x1080.
"""
import math
import bpy
from mathutils import Vector

TARGET = Vector((5, 0, 1))
# name: (azimuth deg from +Y toward +X, elevation deg, ortho scale); None = existing camera
VIEWS = {
    'port-side': None,
    'quarter30': (30, 12, 150),
    'quarter60': (60, 24, 170),
}


def camera(name, dist=180):
    if VIEWS[name] is None:
        return bpy.data.objects['Camera ' + name]
    existing = bpy.data.objects.get('SPIKE ' + name)
    if existing:
        return existing
    az, el, scale = VIEWS[name]
    a, e = math.radians(az), math.radians(el)
    pos = TARGET + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    data = bpy.data.cameras.new('SPIKE ' + name)
    data.type = 'ORTHO'; data.ortho_scale = scale
    cam = bpy.data.objects.new('SPIKE ' + name, data)
    bpy.context.scene.collection.objects.link(cam)
    cam.location = pos
    cam.rotation_euler = (TARGET - pos).to_track_quat('-Z', 'Y').to_euler()
    bpy.context.view_layer.update()  # matrix_world is stale until the depsgraph runs
    return cam
