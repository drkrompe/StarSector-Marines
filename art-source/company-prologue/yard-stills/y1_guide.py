"""Y1's guide: Second Wind down on Kilo Nine, from the ground, with Nessa and her estimate.

    blender --background --factory-startup --python y1_guide.py -- [--out PATH] [--scale 50] [--samples N]
            [--figure PATH]

The yard report page (`CompanyYardScreen`, storyboard-v3.md row Y1) shows the ship Nessa is about
to convert. This renders it from the cinematic's own yard (`industrial-exterior/c5_scene.py`, which
builds C4's port with the ship in the cradle, the scaffold arms swung in, the tug, bowser and crew
carrier at the hull and her crew in hi-vis), at the moment the vehicles have parked and before
anyone has come off the ramp. The camera stands on the open gravel off the ship's port flank, a
little above eye height, looking square at its side (owner, 2026-10-19: a side view). Nessa (`nessa-figure/build_nessa.py
--pose slate`) stands in the foreground holding the work slate out toward the viewer; the crowd's
stand-in Nessa is hidden.

The render is a guide for an ImageGen repaint (`geometry-first-imagegen.md`), never the shipped
picture. It writes `<out>/y1-guide.png` (1536x1024 at --scale percent) and `y1-guide.json` with the
camera, so the repaint and any re-render agree.
"""
import json
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
EXTERIOR = PROLOGUE / 'second-wind-cinematic' / 'industrial-exterior'
sys.path.insert(0, str(EXTERIOR))
sys.argv += ['--no-port']                # the public port's walkers are too far off to matter here
import c4x_scene as c4  # noqa: E402
import c5_scene  # noqa: E402

REPO = PROLOGUE.parents[1]
W, H = 1536, 1024
C5_T = 7.6                       # vehicles parked, the ramp crew out, the families not yet down
# A broadside on the ship's port (west) flank, from the open yard between the cradle and the
# works: the camera in the ship's own frame (u ahead along its heading, v to its left), Nessa a few
# steps in front of it toward the ship and a little to the right of frame.
CAMERA_UV = (-12.0, 50.0)
NESSA_AHEAD, NESSA_RIGHT = 1.8, 0.75   # close: she is handing the estimate to the viewer
CAMERA_Z = 1.6                   # eye height
LOOK_AT_Z = 9.0                  # aim: the middle of the ship's flank
LENS = 22.0


def main():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    out = (Path(argv[argv.index('--out') + 1]) if '--out' in argv else REPO / 'build' / 'yard-stills').resolve()
    scale = int(argv[argv.index('--scale') + 1]) if '--scale' in argv else 100
    samples = int(argv[argv.index('--samples') + 1]) if '--samples' in argv else 32
    figure = Path(argv[argv.index('--figure') + 1]) if '--figure' in argv else \
        REPO / 'build' / 'nessa-figure' / 'nessa-slate.blend'

    scene = c5_scene.setup('3d')
    frame = c5_scene.C4_END + round(C5_T * c4.FPS)
    scene.frame_set(frame)
    for ob in bpy.data.objects:
        if ob.name.startswith('Nessa'):
            ob.hide_render = True
            ob.hide_viewport = True

    # Nessa, from her figure package, facing the camera.
    ship = Vector(c4.K9)
    cam_xy = Vector(c4.at(c4.K9[0], c4.K9[1], c4.k9_heading(), *CAMERA_UV)[:2])
    toward = (ship - cam_xy).normalized()
    right = Vector((toward.y, -toward.x))
    NESSA = cam_xy + toward * NESSA_AHEAD + right * NESSA_RIGHT
    with bpy.data.libraries.load(str(figure)) as (src, dst):
        dst.collections = ['Nessa figure']
    col = dst.collections[0]
    scene.collection.children.link(col)
    root = bpy.data.objects['Nessa figure root']
    gz = c5_scene.ground_z(NESSA.x, NESSA.y)
    root.location = (NESSA.x, NESSA.y, gz)
    to_cam = cam_xy - NESSA
    root.rotation_euler = (0, 0, math.atan2(to_cam.y, to_cam.x) + math.radians(-12))

    cam = bpy.data.objects.new('Y1 camera', bpy.data.cameras.new('Y1 camera'))
    scene.collection.objects.link(cam)
    cam.animation_data_clear()
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = LENS, 36.0, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 0.1, 60000.0
    cam.location = (cam_xy.x, cam_xy.y, gz + CAMERA_Z)
    aim = Vector((ship.x, ship.y, LOOK_AT_Z))
    cam.rotation_euler = (aim - cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam

    scene.render.resolution_x, scene.render.resolution_y = W, H
    scene.render.resolution_percentage = scale
    scene.eevee.taa_render_samples = samples
    scene.eevee.shadow_resolution_scale = 1.0
    out.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(out / 'y1-guide.png')
    bpy.ops.render.render(write_still=True)
    (out / 'y1-guide.json').write_text(json.dumps({
        'c5_seconds': C5_T, 'frame': frame, 'lens_mm': LENS,
        'camera': list(cam.location), 'aim': list(aim), 'nessa': [NESSA.x, NESSA.y, gz],
        'figure': str(figure), 'size': [W, H], 'scale': scale}, indent=1))
    print('Y1_GUIDE', out / 'y1-guide.png', flush=True)


if __name__ == '__main__':
    main()
