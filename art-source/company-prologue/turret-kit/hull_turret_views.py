"""One close turret (or hardpoint) view per built hull, for a review of the kit on its hulls.

  blender --background --factory-startup --python hull_turret_views.py -- --out DIR
      [--res 960x540] [--samples 16] LABEL=PATH.blend[:OBJECT] ...

For each hull asset: opens the .blend, picks a kit mount (OBJECT if given, else the turret
pivot or hardpoint mount with the most parts under it), frames it from the front quarter
and above at about four times its size, lights it with the review studio (studio.py) and
renders LABEL.png. A read-only look at what the hull's own build contains: nothing is
saved and the hull is not rebuilt.
"""
import argparse
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
import studio  # noqa: E402

argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--out', required=True)
ap.add_argument('--res', default='960x540')
ap.add_argument('--samples', type=int, default=16)
ap.add_argument('--dry', action='store_true', help='print the mounts picked, render nothing')
ap.add_argument('views', nargs='+')
args = ap.parse_args(argv)
OUT = Path(args.out)
OUT.mkdir(parents=True, exist_ok=True)


def mounts():
    out = []
    for ob in bpy.data.objects:
        if ob.type != 'EMPTY':
            continue
        n = ob.name
        if (n.endswith(' pivot') and (n.startswith('Turret ') or n.startswith('Weapon '))) or n.endswith(' mount'):
            parts = [c for c in ob.children_recursive if c.type == 'MESH']
            if parts:
                out.append((len(parts), ob, parts))
    return sorted(out, key=lambda t: -t[0])


for spec in args.views:
    label, rest = spec.split('=', 1)
    path, _, want = rest.partition(':')
    bpy.ops.wm.open_mainfile(filepath=path)
    scene = bpy.context.scene
    found = mounts()
    if want and want in bpy.data.objects:
        ob = bpy.data.objects[want]
        pick = (0, ob, [c for c in ob.children_recursive if c.type == 'MESH'])
    else:
        pick = found[0] if found else None
    if pick is None:
        print(f'{label}: no kit mount found')
        continue
    _, mount, parts = pick
    if args.dry:
        print(f'PICK {label}: {mount.name} ({len(parts)} parts)')
        continue
    for c in bpy.data.collections:          # the effects collection stays out of the look
        if c.name.startswith('SWFX'):
            c.hide_render = True
    bpy.context.view_layer.update()
    pts = [p.matrix_world @ Vector(c) for p in parts for c in p.bound_box]
    lo, hi = Vector(map(min, *pts)), Vector(map(max, *pts))
    centre, size = (lo+hi)/2, max((hi-lo).length, 1e-3)
    studio.setup(scene, args.samples)
    w, h = (int(v) for v in args.res.split('x'))
    scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = w, h, 100
    fwd = mount.matrix_world.to_3x3() @ Vector((1, 0, 0))
    fwd.z = 0
    fwd = fwd.normalized() if fwd.length > 1e-6 else Vector((1, 0, 0))
    side = Vector((-fwd.y, fwd.x, 0))
    eye = centre+(fwd*.9+side*.75+Vector((0, 0, .7))).normalized()*size*2.4
    cam_data = bpy.data.cameras.new(label)
    cam_data.lens = 50
    cam_data.clip_start, cam_data.clip_end = size*.02, size*400
    cam = bpy.data.objects.new(label, cam_data)
    scene.collection.objects.link(cam)
    cam.location = eye
    cam.rotation_euler = (centre-eye).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam
    scene.render.filepath = str(OUT/f'{label}.png')
    bpy.ops.render.render(write_still=True)
    print(f'{label}: {mount.name} ({len(parts)} parts)')
