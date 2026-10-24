"""Every burst the kit makes, side by side, frame by frame (coordinator, 2026-10-23: one bench sheet
covering all burst callers, so no path keeps the old held white flash).

  blender --background --factory-startup --python bench_bursts.py -- --out DIR [--samples 16] [--frames 12]

Six in a row across empty space, each thrown up and a little toward the camera, all on frame HIT:
an HE shell (swfx_impacts.he), a Hellbore slug (hellbore), a kinetic round (kinetic), a Harpoon's
warhead (swfx_missile_paths.burst 'hit'), a cook-off blast with its satellites (swfx_fire's jagged
blast) and a flak canister (swfx_pd). One 1600x400 PNG per frame from the hit on, at 24 fps.
A caller that fails to build is reported and its slot left empty; the rest still render.
"""
import argparse
import random
import sys
import traceback
from pathlib import Path
from types import SimpleNamespace

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import swfx_core  # noqa: E402
import swfx_impacts  # noqa: E402

HIT = 4
THROW = (0.0, -.35, .94)
SPACING = 28.0


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    ap.add_argument('--samples', type=int, default=16)
    ap.add_argument('--frames', type=int, default=12)
    a = ap.parse_args(argv)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = a.samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = 1600, 400
    scene.render.fps = swfx_core.FPS
    scene.view_settings.view_transform = 'AgX'
    world = bpy.data.worlds.new('bench')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.0
    scene.world = world
    ctx = swfx_core.Context(scene, 'patched', None)
    swfx_impacts._knobs(ctx)
    u = swfx_core.units
    mpu = swfx_core.METRES_PER_UNIT
    sun = bpy.data.objects.new('bench sun', bpy.data.lights.new('bench sun', 'SUN'))
    sun.data.energy = 4.6          # Asharu's sun, so smoke reads as it does in shot
    sun.rotation_euler = (.6, .3, .8)
    scene.collection.objects.link(sun)
    throw = Vector(THROW).normalized()
    coll = ctx.collection(swfx_impacts.GROUP)

    def at(i):
        return ((i - 2.5) * SPACING, 0.0, 0.0)

    def he(i):
        swfx_impacts.he(ctx, HIT, at(i), throw)

    def hellbore(i):
        swfx_impacts.hellbore(ctx, HIT, at(i), throw)

    def kinetic(i):
        swfx_impacts.kinetic(ctx, HIT, at(i), throw)

    def harpoon(i):
        import swfx_missile_paths as mp
        here = Vector([u(c) for c in at(i)])
        mp.burst(ctx, coll, mp.PROFILES['harpoon'], 'hit', 'bench', here, HIT, mpu, axis=throw,
                 rnd=random.Random('bench-harpoon'))

    def cookoff(i):
        import swfx_fire
        swfx_fire._knobs(ctx)
        ctx.controls['fir_exp'], ctx.controls['fir_exp_scale'], ctx.controls['fir_exp_frame'] = 1.0, 1.0, float(HIT)
        anchor = swfx_core.empty('bench cook-off anchor', coll, size=u(.5))
        anchor.location = Vector([u(c) for c in at(i)])
        anchor['size_m'] = 14.0
        bpy.context.view_layer.update()
        swfx_fire._jagged_blast(ctx, coll, anchor, 7, 0.0, 1.0)

    def flak(i):
        import swfx_pd
        here = Vector([u(c) for c in at(i)])
        swfx_pd._flak_burst(ctx, coll, 0, SimpleNamespace(frame=HIT), here, mpu, random.Random('bench-flak'))

    for i, (name, make) in enumerate((('he', he), ('hellbore', hellbore), ('kinetic', kinetic),
                                      ('harpoon', harpoon), ('cook-off', cookoff), ('flak', flak))):
        try:
            make(i)
            print(f'BENCH built {name}', flush=True)
        except Exception:
            print(f'BENCH FAILED {name}:\n{traceback.format_exc()}', flush=True)
    cam = bpy.data.objects.new('bench cam', bpy.data.cameras.new('bench cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.lens = 35.0
    cam.location = Vector([u(c) for c in (0, -95, 6)])
    cam.rotation_euler = (Vector([u(c) for c in (0, 0, 6)]) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    for j in range(a.frames):
        scene.frame_set(HIT + j)
        scene.render.filepath = str(out / f'bench-bursts-f{j:02d}.png')
        bpy.ops.render.render(write_still=True)
        print('BENCH wrote', scene.render.filepath, flush=True)


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
