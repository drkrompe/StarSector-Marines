"""Nessa's placement silhouette for a yard picture: her stand-in figure alone, from the picture's camera.

    blender --background --factory-startup --python nessa_silhouette.py -- --guide guides/y1-guide-v7.json
            [--figure PATH] [--out PATH] [--ahead 1.8] [--right 0.75]

The yard pictures are layered (owner, 2026-10-19): the background is painted with nobody in the
foreground, and Nessa is a separate cut-out composited over it. This renders the stand-in figure
(`nessa-figure/build_nessa.py --pose slate --mask none`) from the camera recorded with the
background's guide, on a transparent background in flat grey, so her cut-out is painted to the same
size, pose and spot. No yard is built, so it takes seconds. Writes `<out>` (RGBA, the guide's size).
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
SHIFT_Y = 0.11            # y1_guide.SHIFT_Y: the background's lens shift


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    arg = lambda k, d: argv[argv.index(k) + 1] if k in argv else d   # noqa: E731
    guide = json.loads((HERE / arg('--guide', 'guides/y1-guide-v7.json')).read_text())
    figure = Path(arg('--figure', str(REPO / 'build' / 'nessa-figure' / 'nessa-slate.blend')))
    out = Path(arg('--out', str(REPO / 'build' / 'yard-stills' / 'nessa-silhouette.png'))).resolve()
    ahead, right_off = float(arg('--ahead', '1.8')), float(arg('--right', '0.75'))

    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    cam_p, aim = Vector(guide['camera']), Vector(guide['aim'])
    toward = (aim - cam_p).to_2d().normalized()
    right = Vector((toward.y, -toward.x))
    ground = cam_p.z - 1.6
    spot = cam_p.to_2d() + toward * ahead + right * right_off

    with bpy.data.libraries.load(str(figure)) as (src, dst):
        dst.collections = ['Nessa figure']
    scene.collection.children.link(dst.collections[0])
    root = bpy.data.objects['Nessa figure root']
    root.location = (spot.x, spot.y, ground)
    to_cam = cam_p.to_2d() - spot
    root.rotation_euler = (0, 0, math.atan2(to_cam.y, to_cam.x) + math.radians(-12))

    cam = bpy.data.objects.new('Camera', bpy.data.cameras.new('Camera'))
    scene.collection.objects.link(cam)
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = guide['lens_mm'], 36.0, 'HORIZONTAL'
    cam.data.shift_y = SHIFT_Y
    cam.data.clip_start = 0.05
    cam.location = cam_p
    cam.rotation_euler = (aim - cam_p).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam

    scene.render.engine = 'BLENDER_WORKBENCH'
    sh = scene.display.shading
    sh.light, sh.color_type = 'STUDIO', 'MATERIAL'
    scene.render.film_transparent = True
    scene.render.resolution_x, scene.render.resolution_y = guide['size']
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format, scene.render.image_settings.color_mode = 'PNG', 'RGBA'
    out.parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(out)
    bpy.ops.render.render(write_still=True)
    print('NESSA_SILHOUETTE', out, flush=True)


if __name__ == '__main__':
    main()
