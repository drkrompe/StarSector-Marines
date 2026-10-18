"""Compose the Shepherd export review: 1920x1080 sheets, the turn movie and its contact sheet.

  python compose_review.py [--renders DIR] [--fps 24]

Reads review_ship.py's renders (default ../mudskipper-surface/build/final/) and
writes review/ beside this script (ignored; renders are never committed):
  whole.png     four quarters
  detail.png    the turret at rest and trained, the drone bay, the top
  effects.png   the optional SWFX collection, and the ship's own lamps alone
  turn.mp4      H.264 1920x1080, the ship yawed 360 degrees
  turn-sheet.png  sixteen evenly spaced frames of the turn
Needs ffmpeg on PATH for the movie.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--renders', default=str(HERE.parent / 'shepherd-surface' / 'build' / 'final'))
ap.add_argument('--fps', type=int, default=24)
a = ap.parse_args()
src, out = Path(a.renders), HERE / 'review'
out.mkdir(parents=True, exist_ok=True)
try:
    font = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 22)
except OSError:
    font = ImageFont.load_default()
PAGES = {
    'whole': ['three-quarter', 'starboard-quarter', 'stern-quarter', 'under-quarter'],
    'detail': ['turret-detail', 'turret-detail-trained', 'dronebay-detail', 'top'],
    'effects': ['three-quarter-fx', 'stern-quarter-fx', 'bow-quarter-fx', 'three-quarter-dark'],
}
NOTES = {'-trained': '  (turret trained 60 deg)', '-dark': '  (studio off: ship lamps only)', '-fx': '  (SWFX effects collection)'}
for name, views in PAGES.items():
    W = Image.new('RGB', (1920, 1080))
    d = ImageDraw.Draw(W)
    for k, v in enumerate(views):
        f = src / f'{v}.png'
        x, y = (k % 2) * 960, (k // 2) * 540
        if f.exists():
            im = Image.open(f).convert('RGB')
            assert im.size == (1920, 1080), (f, im.size)
            W.paste(im.resize((960, 540), Image.LANCZOS), (x, y))
        d.rectangle((x, y, x + 960, y + 34), fill=(0, 0, 0))
        note = next((t for s, t in NOTES.items() if v.endswith(s)), '')
        d.text((x + 12, y + 5), f'Shepherd  ·  {v}{note}', font=font, fill=(235, 235, 235))
    W.save(out / f'{name}.png')
frames = sorted((src / 'turn').glob('turn-*.png'))
if frames:
    if shutil.which('ffmpeg'):
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(a.fps), '-i',
                        str(frames[0].parent / 'turn-%03d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                        '-crf', '20', '-preset', 'slow', '-movflags', '+faststart', str(out / 'turn.mp4')], check=True)
    sheet = Image.new('RGB', (1920, 1080))
    for k in range(16):
        im = Image.open(frames[round(k * len(frames) / 16) % len(frames)]).convert('RGB').resize((480, 270), Image.LANCZOS)
        sheet.paste(im, ((k % 4) * 480, (k // 4) * 270))
    sheet.save(out / 'turn-sheet.png')
print('COMPOSED', sorted(p.name for p in out.iterdir()))
