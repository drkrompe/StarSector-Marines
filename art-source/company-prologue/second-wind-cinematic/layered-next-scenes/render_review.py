"""Blender background renderer for the four-shot rough layered review."""
import argparse
from pathlib import Path
import sys
import bpy

HERE = Path(__file__).resolve().parent
SHOTS = ((1, 96, 240), (97, 192, 400), (193, 312, 565), (313, 576, 647))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--blend', type=Path, default=HERE / 'layered-next-scenes.blend')
    parser.add_argument('--output', type=Path, default=HERE / 'build/frames')
    parser.add_argument('--preview', action='store_true')
    parser.add_argument('--start', type=int, default=1)
    parser.add_argument('--end', type=int, default=576)
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
    if not 1 <= args.start <= args.end <= 576:
        parser.error('Expected 1 <= start <= end <= 576')
    bpy.ops.wm.open_mainfile(filepath=str(args.blend))
    scene = bpy.context.scene
    assert scene.render.fps / scene.render.fps_base == 12
    assert (scene.render.resolution_x, scene.render.resolution_y) == (1920, 1080)
    assert scene.render.resolution_percentage == 100
    markers = sorted((m for m in scene.timeline_markers if m.camera), key=lambda m: m.frame)
    if not markers or markers[0].frame > 240:
        raise RuntimeError('Expected bound source camera markers covering source frame 240')
    scene.render.image_settings.file_format = 'PNG'
    args.output.mkdir(parents=True, exist_ok=True)
    frames = sorted({frame for start, end, _ in SHOTS for frame in (start, (start + end) // 2, end)}) if args.preview else range(args.start, args.end + 1)
    for frame in frames:
        if not args.start <= frame <= args.end:
            continue
        start, _, source_start = next(shot for shot in SHOTS if shot[0] <= frame <= shot[1])
        source_frame = source_start + (frame - start) * .5
        integer = int(source_frame)
        scene.frame_set(integer, subframe=source_frame - integer)
        scene.camera = max((m for m in markers if m.frame <= source_frame), key=lambda m: m.frame).camera
        scene.render.filepath = str(args.output / f'frame_{frame:04d}.png')
        bpy.ops.render.render(write_still=True)
        print(f'REVIEW_FRAME {frame}/576 source={source_frame} camera={scene.camera.name}', flush=True)


if __name__ == '__main__':
    main()
