"""Integrated review: every effect group together under the lighting presets.

  blender --background --factory-startup --python review_kit.py -- --shot NAME [--quick]

One shot per run (each opens its own state's cached ship build from
build/_ships/, made by build_vfx.py --groups "" --save). Stills and the movie
go to review/kit/ at 1920x1080, 100%; raw frames to build/kit/.
Shots: approach, deep-space, derelict, berth, action and rcs (movies).
"""
import argparse
import sys
from pathlib import Path

import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import swfx  # noqa: E402
import swfx_core  # noqa: E402

# state, preset, camera (location, target, lens), knobs, frame
SHOTS = {
    # Arrival over Jangala: only the starboard pod burning, on the salvaged
    # coil, its dirty haze hanging in the air; port pod dark.
    'approach': ('patched', 'jangala-approach', ((-95, -150, 45), (-22, -8, 2), 42),
                 dict(smk_atmosphere=1, gun_atmosphere=1, thr_brightness=2.2, thr_throttle=.85), 40),
    # The restored ship in space: all four drives clean, running lights on.
    'deep-space': ('restored', 'deep-space', ((-120, -120, 60), (-15, 0, 4), 40),
                   dict(smk_atmosphere=0, gun_atmosphere=0), 40),
    # The parts-bin hulk: cold, unpowered, nothing lit of its own.
    'derelict': ('derelict', 'deep-space', ((-120, -120, 60), (-15, 0, 4), 40), {}, 40),
    # Berthed at night: drives cold, lamps and work floods on, the patched
    # seals leaking a little steam.
    'berth': ('patched', 'night-berth', ((95, 120, 55), (5, 5, 6), 38),
              dict(thr_throttle=0, smk_atmosphere=1, smk_seal_wisp=.3, gun_atmosphere=1), 40),
    # Movie: under way in space on the starboard pod, turrets 2 and 4 firing,
    # a stern RCS correction.
    # Wide shot: flashes and tracers are small at this distance, so the gains
    # go up, every round is a tracer, and the rounds are slowed to stay in frame.
    'action': ('patched', 'deep-space', ((-75, -135, 85), (12, -6, 6), 34),
               dict(smk_atmosphere=0, gun_atmosphere=0, gun_flash_gain=2.5, gun_flash_light=6000,
                    gun_tracer_gain=2.5, gun_tracer_every=1, gun_muzzle_velocity=220), None),
    # Movie: C1's starboard-down bank and bow-to-starboard correction on the
    # geometry's RCS thrusters (patched: 18 of 24 working).
    'rcs': ('patched', 'deep-space', ((25, -95, 38), (-12, -36, 5), 32),
            dict(smk_atmosphere=0, thr_throttle=.6, thr_rcs_gain=6), None),
}


def camera(loc, target, lens):
    cam = swfx_core.look_camera('SWFX review camera', bpy.context.scene.collection, loc, target, lens)
    bpy.context.scene.camera = cam
    return cam


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--shot', required=True, choices=sorted(SHOTS))
    p.add_argument('--quick', action='store_true')
    p.add_argument('--ships', default=str(HERE / 'build' / '_ships'), help='folder of cached ship builds')
    a = p.parse_args(sys.argv[sys.argv.index('--') + 1:])
    state, preset, (loc, target, lens), knobs, frame = SHOTS[a.shot]
    bpy.ops.wm.open_mainfile(filepath=str(Path(a.ships) / f'{state}.blend'))
    scene = bpy.context.scene
    ctx = swfx.apply(state=state, preset=preset)
    for k, v in knobs.items():
        ctx.prop(k, v, -10, 20000)
        ctx.set(k, v)
    out = HERE / 'review' / 'kit'
    raw = HERE / 'build' / 'kit' / a.shot
    out.mkdir(parents=True, exist_ok=True)
    raw.mkdir(parents=True, exist_ok=True)
    camera(loc, target, lens)
    if frame is not None:
        swfx_core.setup_render(scene, samples=16 if a.quick else 96)
        swfx_core.refresh_drivers(scene)
        scene.frame_set(frame)
        scene.render.filepath = str((raw if a.quick else out) / f'kit-{state}-{preset}.png')
        bpy.ops.render.render(write_still=True)
        return

    import swfx_guns
    import swfx_thrusters
    if a.shot == 'rcs':
        swfx_thrusters.manoeuvre(ctx, 'roll-starboard-down', 4, 30)
        swfx_thrusters.manoeuvre(ctx, 'yaw-starboard', 30, 56)
        swfx_core.setup_render(scene, samples=12 if a.quick else 32)
        scene.frame_start, scene.frame_end = 1, (24 if a.quick else 60)
        swfx_core.refresh_drivers(scene)
        scene.render.filepath = str(raw / 'f####')
        bpy.ops.render.render(animation=True)
        if not a.quick:
            swfx_core.encode_movie(raw / 'f%04d.png', out / f'kit-{state}-rcs-bank-and-yaw.mp4')
        return
    swfx_thrusters.ignition(ctx, 1, ramp_frames=10)
    swfx_guns.fire(ctx, 4, 14, shots=14, interval_frames=3)
    swfx_guns.fire(ctx, 2, 20, shots=8, interval_frames=4)
    swfx_thrusters.rcs_puff(ctx, 'stern_starboard', 40, duration=8)
    swfx_core.setup_render(scene, samples=12 if a.quick else 32)
    scene.render.use_motion_blur = True
    scene.render.motion_blur_shutter = .5
    scene.frame_start, scene.frame_end = 1, (24 if a.quick else 72)
    swfx_core.refresh_drivers(scene)
    scene.render.filepath = str(raw / 'f####')
    bpy.ops.render.render(animation=True)
    if not a.quick:
        swfx_core.encode_movie(raw / 'f%04d.png', out / f'kit-{state}-{preset}-action.mp4')


main()
