"""Close views of built hull assets, for a detail-rule review.

  blender --background --factory-startup --python closeshot_views.py -- --out DIR
      [--res 960x540] [--samples 16] LABEL=PATH.blend@PX,PY,PZ:TX,TY,TZ:LENS ...

LENS is a focal length in mm, or oSCALE for an orthographic camera of that scale
(so a view can repeat a hull driver's own ortho review camera).

For each view: opens the built .blend as it was exported (root at the origin, hull
units), hides any SWFX effects collection, lights it with the turret kit's review
studio (turret-kit/studio.py), places a camera at P looking at T with that lens
and renders LABEL.png. A view whose PNG already exists is skipped, so a swapped-out
job resumes. A read-only look: nothing is saved.
"""
import argparse
import sys
from pathlib import Path
import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import studio  # noqa: E402

argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--out', required=True)
ap.add_argument('--res', default='960x540')
ap.add_argument('--samples', type=int, default=16)
ap.add_argument('views', nargs='+')
args = ap.parse_args(argv)
OUT = Path(args.out)
OUT.mkdir(parents=True, exist_ok=True)


def vec(s):
    return Vector(tuple(float(v) for v in s.split(',')))


for spec in args.views:
    label, rest = spec.split('=', 1)
    path, _, cam = rest.rpartition('@')
    eye, target, lens = cam.split(':')
    eye, target = vec(eye), vec(target)
    png = OUT/f'{label}.png'
    if png.exists() and png.stat().st_size > 0:
        print(f'{label}: exists, skipped')
        continue
    bpy.ops.wm.open_mainfile(filepath=path)
    scene = bpy.context.scene
    for c in bpy.data.collections:
        if c.name.startswith('SWFX'):
            c.hide_render = True
    studio.setup(scene, args.samples)
    w, h = (int(v) for v in args.res.split('x'))
    scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = w, h, 100
    dist = (target-eye).length
    cam_data = bpy.data.cameras.new(label)
    if lens.startswith('o'):
        cam_data.type, cam_data.ortho_scale = 'ORTHO', float(lens[1:])
    else:
        cam_data.lens = float(lens)
    cam_data.clip_start, cam_data.clip_end = max(dist*.005, 1e-3), dist*200
    ob = bpy.data.objects.new(label, cam_data)
    scene.collection.objects.link(ob)
    ob.location = eye
    ob.rotation_euler = (target-eye).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = ob
    scene.render.filepath = str(png)
    bpy.ops.render.render(write_still=True)
    print(f'CLOSESHOT {label}: {path}')
