"""Y1's guide: Second Wind down on Kilo Nine, from the ground, with Nessa and her estimate.

    blender --background --factory-startup --python y1_guide.py -- [--out PATH] [--scale 50] [--samples N]
            [--figure PATH] [--no-figure]

The yard report page (`CompanyYardScreen`, storyboard-v3.md row Y1) shows the ship Nessa is about
to convert. This renders it from the cinematic's own yard (`industrial-exterior/c5_scene.py`, which
builds C4's port with the ship in the cradle, the scaffold arms swung in, the tug, bowser and crew
carrier at the hull and her crew in hi-vis), at the moment the vehicles have parked and before
anyone has come off the ramp. The camera stands on the open gravel off the ship's starboard flank, a
little above eye height, looking square at its side (owner, 2026-10-19: a side view). Nessa (`nessa-figure/build_nessa.py
--pose slate`) stands in the foreground holding the work slate out toward the viewer; the crowd's
stand-in Nessa is hidden. --no-figure leaves her out: the yard pictures are layered, the
background painted without her and her cut-out composited over it (owner, 2026-10-19).

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
# A broadside on the ship's starboard (east) flank, where the tug, bowser, crew carrier and the
# yard hands are (owner, 2026-10-19: the whole ship and more of the ground crew in view): the
# camera in the ship's own frame (u ahead along its heading, v to its left), Nessa a few steps in
# front of it toward the ship and a little to the right of frame.
# A three-quarter view on the bow from the port side (owner, 2026-10-19), over the open yard
# between the cradle and the works; the starboard side is fenced in by scaffold towers. The camera
# takes the first of these spots (u ahead along the ship's heading, v to its left) with open sky
# around it and nothing but the ship on its lines to the hull.
CAMERA_TRY = ((42.0, 72.0), (32.0, 82.0), (52.0, 62.0), (24.0, 90.0), (58.0, 78.0), (38.0, 98.0))
AIM_U = 4.0                      # a little forward of amidships
# A standing viewer's camera (owner, 2026-10-19: the tilted-up wide view read as lying on the
# ground): level at eye height, no tilt, the frame raised by lens shift so the horizon sits about
# two thirds down and the ground fills only the bottom third, without converging verticals.
SHIFT_Y = 0.11
NESSA_AHEAD, NESSA_RIGHT = 1.8, 0.75   # close: she is handing the estimate to the viewer
CAMERA_Z = 1.6                   # eye height
LENS = 32.0


def clear_spot(scene):
    """The first candidate spot with open sky round it and only the ship on its lines to the hull."""
    kx, ky, kh = c4.K9[0], c4.K9[1], c4.k9_heading()
    manifest = json.loads((c4.STATES / 'manifest.json').read_text())
    ship = {o.name for o in bpy.data.collections[manifest['append_collection']].all_objects}
    dg = bpy.context.evaluated_depsgraph_get()
    targets = [Vector(c4.at(kx, ky, kh, u, 0, z)) for u in range(-30, 31, 6) for z in (3.0, 8.0, 13.0, 18.0)]
    best = None
    for u, v in CAMERA_TRY:
        xy = Vector(c4.at(kx, ky, kh, u, v)[:2])
        eye = Vector((xy.x, xy.y, c5_scene.ground_z(xy.x, xy.y) + CAMERA_Z))
        roof = any(scene.ray_cast(dg, eye + Vector((dx, dy, 0)), Vector((0, 0, 1)), distance=80.0)[0]
                   for dx, dy in ((0, 0), (4, 0), (-4, 0), (0, 4), (0, -4)))
        clear = 0
        for t in targets:
            d = (t - eye).length
            hit, _, _, _, ob, _ = scene.ray_cast(dg, eye, (t - eye).normalized(), distance=d + 40)
            clear += bool(hit and ob is not None and (ob.name in ship or ob.name.startswith('Drive ')))
        share = clear / len(targets)
        print('Y1_SPOT u=%.0f v=%.0f roof=%s sees %.0f%%' % (u, v, roof, share * 100), flush=True)
        if not roof and share >= 0.9:
            return xy
        score = share - (1.0 if roof else 0.0)
        if best is None or score > best[0]:
            best = (score, xy)
    return best[1]


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
    cam_xy = clear_spot(scene)
    toward = (ship - cam_xy).normalized()
    right = Vector((toward.y, -toward.x))
    NESSA = cam_xy + toward * NESSA_AHEAD + right * NESSA_RIGHT
    gz = c5_scene.ground_z(NESSA.x, NESSA.y)
    if '--no-figure' not in argv:
        with bpy.data.libraries.load(str(figure)) as (src, dst):
            dst.collections = ['Nessa figure']
        scene.collection.children.link(dst.collections[0])
        root = bpy.data.objects['Nessa figure root']
        root.location = (NESSA.x, NESSA.y, gz)
        to_cam = cam_xy - NESSA
        root.rotation_euler = (0, 0, math.atan2(to_cam.y, to_cam.x) + math.radians(-12))

    cam = bpy.data.objects.new('Y1 camera', bpy.data.cameras.new('Y1 camera'))
    scene.collection.objects.link(cam)
    cam.animation_data_clear()
    cam.data.lens, cam.data.sensor_width, cam.data.sensor_fit = LENS, 36.0, 'HORIZONTAL'
    cam.data.clip_start, cam.data.clip_end = 0.1, 60000.0
    cam.location = (cam_xy.x, cam_xy.y, gz + CAMERA_Z)
    aim = Vector((*c4.at(c4.K9[0], c4.K9[1], c4.k9_heading(), AIM_U, 0)[:2], gz + CAMERA_Z))
    cam.data.shift_y = SHIFT_Y
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
