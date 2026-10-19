"""Lay the approved title over C5's rendered frames, and cut them into a review video.

    python c5_title.py [--step 24] [--fps 24]

Reads `build/c5/frames/` (as `c5_scene.py` renders them, one file every `--step` frames), writes
`build/c5/titled/` and `build/c5/c5-sketch.mp4`. The title is direction A (`title-logo.md`): the
game's wordmark, read from the player's install, with MARINES in warm white, revealed on the
approved timing counted from the end of Castle's last line (`c5_scene.TITLE_T0`). The camera's
own rise does the settling that `title_a.reveal` imitates with a zoom on a still frame, so only
the title, the darkening under it and the fade to black come from there. Output, never committed.
"""
import argparse
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'title-logo'))
import title_a  # noqa: E402

REPO = HERE.parents[3]
OUT = REPO / 'build' / 'c5'
FPS = 24
TITLE_T0 = 24.0                         # keep equal to c5_scene.TITLE_T0 (that one imports bpy)
# C5 holds the title over the working port for about ten seconds (owner, 2026-10-19), longer than
# the standalone reveal's own fade, then fades to black with half a second to spare before
# c5_scene.DURATION (40 s): fade over C5 38.0-39.5.
FADE_OUT = (14.0, 15.5)


def titled(bg, parts, t):
    """The frame at C5 second t with the title as it stands then."""
    r = t - TITLE_T0
    if r <= title_a.STARSECTOR_IN[0]:
        return bg
    f = title_a.frame(bg, parts, title_a.ramp(r, title_a.STARSECTOR_IN), title_a.ramp(r, title_a.MARINES_IN),
                      title_a.ramp(r, title_a.STARSECTOR_IN))
    out = 1 - title_a.ramp(r, FADE_OUT)
    return Image.blend(Image.new('RGB', f.size), f, out) if out < 1 else f


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--step', type=int, default=24)
    ap.add_argument('--fps', type=int, default=None, help='video rate; default one rendered frame a second')
    a = ap.parse_args()
    parts = title_a.layout(title_a.wordmark_sharp())
    target = OUT / 'titled'
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    frames = sorted((OUT / 'frames').glob('*.png'))
    for i, p in enumerate(frames):
        bg = Image.open(p).convert('RGB')
        if bg.size != (1920, 1080):                   # a CINE_SCALE sketch: the title is laid out full size
            bg = bg.resize((1920, 1080), Image.LANCZOS)
        titled(bg, parts, i * a.step / FPS).save(target / p.name)
    rate = a.fps or max(1, round(FPS / a.step))
    subprocess.check_call(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(rate), '-i',
                           str(target / '%05d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18',
                           str(OUT / 'c5-sketch.mp4')])
    print('C5_TITLE %d frames -> %s' % (len(frames), OUT / 'c5-sketch.mp4'))


if __name__ == '__main__':
    main()
