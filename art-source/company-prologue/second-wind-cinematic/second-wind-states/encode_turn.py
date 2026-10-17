"""Encode a state's turn review (review_state.py --turn N) and its contact sheet.

  python encode_turn.py <state> [--renders DIR] [--fps 24]

Reads <renders>/<state>/turn/turn-NNN.png (default ../../second-wind-surface/build/final/)
and writes, beside the state's asset:
  <state>/review/turn.mp4        H.264 1920x1080, yuv420p, the whole 360 degrees, no
                                 repeated end frame
  <state>/review/turn-sheet.png  1920x1080, sixteen evenly spaced frames of the turn
Needs ffmpeg on PATH.
"""
from pathlib import Path
import argparse
import subprocess
from PIL import Image

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('state')
ap.add_argument('--renders', default=str(HERE.parent.parent / 'second-wind-surface' / 'build' / 'final'))
ap.add_argument('--fps', type=int, default=24)
a = ap.parse_args()
frames = sorted((Path(a.renders) / a.state / 'turn').glob('turn-*.png'))
assert frames, 'no turn frames'
for f in frames[:1]:
    assert Image.open(f).size == (1920, 1080), f
out = HERE / a.state / 'review'
out.mkdir(parents=True, exist_ok=True)
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(a.fps), '-i',
                str(frames[0].parent / 'turn-%03d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                '-crf', '20', '-preset', 'slow', '-movflags', '+faststart', str(out / 'turn.mp4')], check=True)
sheet = Image.new('RGB', (1920, 1080))
for k in range(16):
    im = Image.open(frames[round(k * len(frames) / 16) % len(frames)]).convert('RGB').resize((480, 270), Image.LANCZOS)
    sheet.paste(im, ((k % 4) * 480, (k // 4) * 270))
sheet.save(out / 'turn-sheet.png')
print('TURN', a.state, len(frames), 'frames ->', out / 'turn.mp4')
