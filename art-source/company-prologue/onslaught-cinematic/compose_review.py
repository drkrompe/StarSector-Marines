"""Compose the Onslaught cinematic review: 1920x1080 sheets, the formation frame, the turn movie.

  python3 compose_review.py [--renders DIR] [--fps 24]

Reads review_ship.py's renders (default build/review/) and writes review/
beside this script (ignored; renders are never committed):
  whole.png            beauty three-quarter and stern quarter, without and with effects
  effects.png          the six drives close (studio, and studio off), the stern quarter
                       with the studio off (lamps and drive glow only), and a 2x crop
                       of the formation frame around the hull
  formation-1.5km.png  the C1 v2 space-lit formation frame with effects, as rendered
  turn.mp4             H.264 1920x1080, the ship (with effects) yawed 360 degrees
  turn-sheet.png       sixteen evenly spaced frames of the turn
Needs ffmpeg on PATH for the movie.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--renders', default=str(HERE / 'build' / 'review'))
ap.add_argument('--fps', type=int, default=24)
a = ap.parse_args()
src, out = Path(a.renders), HERE / 'review'
out.mkdir(parents=True, exist_ok=True)
font = None
for f in ('/System/Library/Fonts/Supplemental/Arial Bold.ttf', 'C:/Windows/Fonts/arialbd.ttf'):
    try:
        font = ImageFont.truetype(f, 22)
        break
    except OSError:
        pass
font = font or ImageFont.load_default()
PAGES = {
    'whole': ['three-quarter', 'stern-quarter', 'three-quarter-fx', 'stern-quarter-fx'],
    'effects': ['engine-detail-fx', 'engine-detail-fx-dark', 'stern-quarter-fx-dark', 'formation-1.5km-fx@crop'],
}
NOTES = {'-dark': '  (studio off: drive glow and lamps only)', '-fx': '  (SWFX effects)',
         '@crop': '  (2x crop, effects)'}


def load(v):
    name, _, mode = v.partition('@')
    f = src / f'{name}.png'
    if not f.exists():
        return None
    im = Image.open(f).convert('RGB')
    assert im.size == (1920, 1080), (f, im.size)
    if mode == 'crop':          # 960x540 about the frame centre, shown at 2x (pixel-doubled)
        return im.crop((720, 405, 1200, 675)).resize((960, 540), Image.NEAREST)
    return im.resize((960, 540), Image.LANCZOS)


for name, views in PAGES.items():
    W = Image.new('RGB', (1920, 1080))
    d = ImageDraw.Draw(W)
    for k, v in enumerate(views):
        x, y = (k % 2) * 960, (k // 2) * 540
        im = load(v)
        if im is not None:
            W.paste(im, (x, y))
        d.rectangle((x, y, x + 960, y + 34), fill=(0, 0, 0))
        note = ''.join(t for s, t in NOTES.items() if s in v and not (s == '-fx' and ('-dark' in v or '@' in v)))
        d.text((x + 12, y + 5), f"Onslaught (XIV)  ·  {v.split('@')[0].replace('-fx', '').replace('-dark', '')}{note}",
               font=font, fill=(235, 235, 235))
    W.save(out / f'{name}.png')
f = src / 'formation-1.5km-fx.png'
if f.exists():
    shutil.copyfile(f, out / 'formation-1.5km.png')
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
