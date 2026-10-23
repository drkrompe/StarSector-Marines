"""One final shot's frames -> its MP4 (the render queue's follow-up job after a shot's chunks).

    python assemble_shot.py <frames dir> <kf> <first frame> <count> <out.mp4> [--wait-minutes 30] [--ffmpeg <path>]

The frames are battle_blockout's final composites, '<kf>_f<frame>.png' at 24 fps, copied to Drive by
chunk jobs that may have run on the other machine; Drive can take a while to bring them here, so this
waits (up to --wait-minutes) until every one is present before encoding. Exits 1 if any are still
missing, naming them.
"""
import subprocess
import sys
import time
from pathlib import Path


def main(argv):
    frames, kf, first, count, out = Path(argv[0]), argv[1], int(argv[2]), int(argv[3]), Path(argv[4])
    ffmpeg = argv[argv.index('--ffmpeg') + 1] if '--ffmpeg' in argv else 'ffmpeg'
    wait = float(argv[argv.index('--wait-minutes') + 1]) if '--wait-minutes' in argv else 30.0
    names = [frames / f'{kf}_f{first + i:05d}.png' for i in range(count)]
    deadline = time.time() + wait * 60
    while True:
        missing = [p.name for p in names if not p.exists() or p.stat().st_size == 0]
        if not missing:
            break
        if time.time() > deadline:
            print(f'ASSEMBLE {kf}: {len(missing)} of {count} frames missing, e.g. {missing[:5]}', flush=True)
            return 1
        print(f'ASSEMBLE {kf}: waiting for {len(missing)} frames', flush=True)
        time.sleep(60)
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix('.partial.mp4')
    subprocess.check_call([ffmpeg, '-y', '-framerate', '24', '-start_number', str(first),
                           '-i', str(frames / f'{kf}_f%05d.png'), '-frames:v', str(count),
                           '-c:v', 'libx264', '-preset', 'slow', '-crf', '14', '-pix_fmt', 'yuv420p',
                           str(tmp)])
    tmp.replace(out)
    print(f'ASSEMBLE {kf}: {count} frames -> {out}', flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
