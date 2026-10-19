"""Compose the Legion export review: 1920x1080 sheets, the turn movie and its contact sheet.

  python compose_review.py [--renders DIR] [--fps 24]

Reads review_ship.py's renders (default ../legion-surface/build/final/) and
writes review/ beside this script (ignored; renders are never committed):
  views.png     the built pose: four quarters
  details.png   the forward battery, the squall launcher, a flight-deck mouth, the drives
  ops.png       every control knob set and every turret trained and elevated
  effects.png   the optional SWFX collection (drives lit), and the ship's own lamps alone
  fire.png      the guns and launchers firing through the kit (swfx.apply + fire)
  turn.mp4      H.264 1920x1080, the ship yawed 360 degrees (only with --turn)
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
ap.add_argument('--renders', default=str(HERE.parent / 'legion-surface' / 'build' / 'final'))
ap.add_argument('--fps', type=int, default=24)
a = ap.parse_args()
src, out = Path(a.renders), HERE / 'review'
out.mkdir(parents=True, exist_ok=True)
try:
    font = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 22)
except OSError:
    font = ImageFont.load_default()
PAGES = {
    'views': ['three-quarter', 'starboard-quarter', 'stern-quarter', 'under-quarter'],
    'details': ['turret-detail', 'launcher-detail', 'flight-deck-detail', 'engine-detail'],
    'ops': ['three-quarter-ops', 'turret-detail-ops', 'flight-deck-detail-ops', 'launcher-detail-ops'],
    'effects': ['stern-quarter-fx', 'engine-detail-fx', 'three-quarter-dark', 'bow-quarter-dark'],
    'fire': ['three-quarter-fire', 'turret-detail-fire', 'launcher-detail-fire', 'bow-quarter-fire'],
}
NOTES = {'-ops': '  (knobs set, turrets trained)', '-dark': '  (studio off: ship lamps only)',
         '-fx': '  (SWFX effects collection)', '-fire': '  (kit firing: guns and launchers)'}
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
        d.text((x + 12, y + 5), f'Legion (XIV)  ·  {v}{note}', font=font, fill=(235, 235, 235))
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
