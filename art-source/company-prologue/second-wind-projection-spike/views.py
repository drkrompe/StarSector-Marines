"""Cameras and part selections shared by render_guide.py and project.py (Blender only).

Whole-ship views: 'port-side' is build_reference.py's own camera, used
unchanged. The others orbit the same target at the same distance: azimuth
swings from the port beam toward the bow, elevation lifts above the deck datum.
They render at 1920x1080.

Part views ('part-*') look square-on along one direction at a selection of
objects rendered ALONE, so nothing else occludes them, and render at TILE,
the size of one tile of a 2x2 sheet with gutters. A part projector paints only faces of its
own objects whose centre lies inside its region.
"""
import math
import bpy
from mathutils import Matrix, Vector

TARGET = Vector((5, 0, 1))
TILE = (912, 513)  # one tile of the 2x2 parts sheet (see parts_sheet.py), 16:9
# name: (azimuth deg from +Y toward +X, elevation deg, ortho scale); None = existing camera
VIEWS = {
    'port-side': None,
    'quarter30': (30, 12, 150),
    'quarter60': (60, 24, 170),
    'high45': (45, 50, 170),   # review only: shows deck tops and turret roofs
}

EXCLUDE_BOW = ('turret', 'grapple', 'arm ', 'davit', 'winch', 'sponson', 'lamp', 'light')

def _centre(ob):
    vs = [ob.matrix_world @ v.co for v in ob.data.vertices]
    return Vector([(min(v[i] for v in vs) + max(v[i] for v in vs)) / 2 for i in range(3)])

HULL = 'Main hull - level datum Z0'
# name: centre, view direction (camera sits along +dir), screen-up, ortho width,
#       object predicate (object, its world bbox centre), face region (lo, hi)
PARTS = {
    'part-deck': dict(centre=(31, 0, 14), dir=(0, 0, 1), up=(1, 0, 0), scale=66,
                      objects=lambda ob, c: ob.name == HULL,
                      region=((11, -36, 2), (51, 36, 40))),
    'part-turret': dict(centre=(-11, 23, 19), dir=(0, 0, 1), up=(1, 0, 0), scale=18,
                        objects=lambda ob, c: ob.name.startswith('Turret 1 '),
                        region=((-99, -99, -99), (99, 99, 99))),
    'part-pod': dict(centre=(-29, 25, 3), dir=(0, 1, 0), up=(0, 0, 1), scale=46,
                     objects=lambda ob, c: ob.name == HULL or (c.x < -8 and c.y > 10 and not ob.name.startswith('Turret')),
                     region=((-52, 8, -40), (-8, 45, 40))),
    'part-bow': dict(centre=(42, 14, 4), dir=(math.sqrt(.5), math.sqrt(.5), 0), up=(0, 0, 1), scale=50,
                     objects=lambda ob, c: ob.name == HULL or (c.x > 28 and c.y > -4 and not any(k in ob.name.lower() for k in EXCLUDE_BOW)),
                     region=((25, -4, -40), (65, 45, 40))),
}


def resolution(name):
    return TILE if name in PARTS else (1920, 1080)


def part_objects(name):
    spec = PARTS[name]
    return [ob for ob in bpy.context.scene.objects if ob.type == 'MESH' and spec['objects'](ob, _centre(ob))]


def camera(name, dist=180):
    if name in VIEWS and VIEWS[name] is None:
        return bpy.data.objects['Camera ' + name]
    existing = bpy.data.objects.get('SPIKE ' + name)
    if existing:
        return existing
    data = bpy.data.cameras.new('SPIKE ' + name)
    data.type = 'ORTHO'
    cam = bpy.data.objects.new('SPIKE ' + name, data)
    bpy.context.scene.collection.objects.link(cam)
    if name in PARTS:
        spec = PARTS[name]
        z = Vector(spec['dir']).normalized()
        y = (Vector(spec['up']) - Vector(spec['up']).dot(z) * z).normalized()
        x = y.cross(z)
        cam.matrix_world = Matrix.Translation(Vector(spec['centre']) + z * 120) @ Matrix((x, y, z)).transposed().to_4x4()
        data.ortho_scale = spec['scale']
        data.clip_end = 400
    else:
        az, el, scale = VIEWS[name]
        a, e = math.radians(az), math.radians(el)
        pos = TARGET + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
        data.ortho_scale = scale
        cam.location = pos
        cam.rotation_euler = (TARGET - pos).to_track_quat('-Z', 'Y').to_euler()
    bpy.context.view_layer.update()  # matrix_world is stale until the depsgraph runs
    return cam
