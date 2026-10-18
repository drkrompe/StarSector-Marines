"""Encode the Hound's turn review (review_hound.py --effects --turn N) and its contact sheet.

  python encode_turn.py [--state clean] [--fps 24]

Reads <state>/review/turn-fx/turn-NNN.png and writes beside them:
  <state>/review/turn.mp4        H.264 1920x1080, yuv420p, the whole 360 degrees
  <state>/review/turn-sheet.png  1920x1080, sixteen evenly spaced frames
Needs ffmpeg on PATH. Both are ignored outputs (review/).
"""
from pathlib import Path
import argparse
import subprocess
from PIL import Image

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--state', default='clean')
ap.add_argument('--fps', type=int, default=24)
a = ap.parse_args()
out = HERE / a.state / 'review'
frames = sorted((out / 'turn-fx').glob('turn-*.png'))
assert frames, 'no turn frames'
assert Image.open(frames[0]).size == (1920, 1080), frames[0]
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(a.fps), '-i',
                str(frames[0].parent / 'turn-%03d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                '-crf', '20', '-preset', 'slow', '-movflags', '+faststart', str(out / 'turn.mp4')], check=True)
sheet = Image.new('RGB', (1920, 1080))
for k in range(16):
    im = Image.open(frames[round(k * len(frames) / 16) % len(frames)]).convert('RGB').resize((480, 270), Image.LANCZOS)
    sheet.paste(im, ((k % 4) * 480, (k // 4) * 270))
sheet.save(out / 'turn-sheet.png')
print('TURN', len(frames), 'frames ->', out / 'turn.mp4')
