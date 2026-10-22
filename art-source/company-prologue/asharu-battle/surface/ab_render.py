"""A/B render of the surface detail: the same key frames with the tiles and without (README.md).

    python surface/ab_render.py --frames k0,k6 --samples 16 --half --root DIR

Runs battle_blockout.py twice, into DIR/detail and DIR/base (--surface base), and copies each raw
frame to DIR/ab/<frame>-detail.png and DIR/ab/<frame>-base.png, the one flat folder a render-queue
job counts. Both runs resume frame by frame, so a swapped-out job picks up where it stopped.
Meant to run as a queue job, never from a session's own shell.
"""
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main():
    argv = sys.argv[1:]

    def opt(flag, default=None):
        return argv[argv.index(flag) + 1] if flag in argv else default

    root = Path(opt('--root'))
    frames = opt('--frames', 'k0,k6')
    passthrough = ['--frames', frames, '--samples', opt('--samples', '16'), '--root', str(root)]
    if '--half' in argv:
        passthrough.append('--half')
    ab = root / 'ab'
    ab.mkdir(parents=True, exist_ok=True)
    for run, extra in (('detail', []), ('base', ['--surface', 'base'])):
        subprocess.check_call([sys.executable, str(HERE.parent / 'battle_blockout.py'), '--run', run,
                               *passthrough, *extra], cwd=HERE.parent)
        for kf in frames.split(','):
            shutil.copy2(root / run / 'raw' / f'{kf}.png', ab / f'{kf}-{run}.png')


if __name__ == '__main__':
    main()
