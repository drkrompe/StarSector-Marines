"""Compose the Phaeton cinematic review sheets from review_ship.py's renders.

  python compose_review.py [--dir ../phaeton-surface/build/final]

Writes into <dir>/sheets/ (1920x1080, ignored):
  staged-<view>.png   the explosion staging: the listed frames in a 4x2 grid,
                      each captioned with its time from the detonation
  stills.png          intact, intact with drives, wreck burning (2x2 or 3x2)
"""
from pathlib import Path
import argparse
import re
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--dir', default=str(HERE.parent / 'phaeton-surface' / 'build' / 'final'))
ap.add_argument('--break-frame', type=int, default=24)
ap.add_argument('--fps', type=float, default=24.0)
args = ap.parse_args()
D = Path(args.dir)
OUT = D / 'sheets'
OUT.mkdir(parents=True, exist_ok=True)
W, H = 1920, 1080


def font(size):
    for name in ('arial.ttf', 'DejaVuSans.ttf', 'Helvetica.ttc'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def grid(cells, cols, rows, title, path):
    sheet = Image.new('RGB', (W, H), (12, 13, 14))
    draw = ImageDraw.Draw(sheet)
    top = 44
    cw, ch = W // cols, (H - top) // rows
    for k, (img, label) in enumerate(cells):
        im = Image.open(img).convert('RGB')
        s = min(cw / im.width, ch / im.height)
        im = im.resize((int(im.width * s), int(im.height * s)), Image.LANCZOS)
        x, y = (k % cols) * cw + (cw - im.width) // 2, top + (k // cols) * ch + (ch - im.height) // 2
        sheet.paste(im, (x, y))
        draw.rectangle((x + 6, y + 6, x + 12 + draw.textlength(label, font=font(20)), y + 32), fill=(0, 0, 0))
        draw.text((x + 9, y + 8), label, font=font(20), fill=(240, 220, 170))
    draw.text((16, 10), title, font=font(26), fill=(230, 230, 230))
    sheet.save(path)
    print('sheet', path)


staged = {}
for p in sorted(D.glob('staged-*-f*.png')):
    m = re.match(r'staged-(.+)-f(\d+)\.png', p.name)
    staged.setdefault(m.group(1), []).append((int(m.group(2)), p))
for view, frames in staged.items():
    cells = [(p, f't = {(f - args.break_frame) / args.fps:+.2f} s' + ('  intact' if f < args.break_frame else ''))
             for f, p in sorted(frames)]
    grid(cells, 4, 2, f'PHAETON / EXPLOSION STAGING - {view} - intact until break_frame, then the wreck (one knob)',
         OUT / f'staged-{view}.png')
stills = [(D / n, lab) for n, lab in (('three-quarter.png', 'intact'), ('three-quarter-fx.png', 'intact, drives'),
                                       ('three-quarter-wreck-fx.png', 'wreck, burning (t = +8 s)'),
                                       ('s6-fx.png', 'intact at 1.2 km (S6 range)'),
                                       ('s6-wreck-fx.png', 'wreck at 1.2 km'),
                                       ('starboard-quarter-wreck-fx.png', 'wreck, starboard quarter'))
          if (D / n).exists()]
if stills:
    grid(stills, 3 if len(stills) > 4 else 2, 2, 'PHAETON / CINEMATIC EXPORT - intact and destroyed', OUT / 'stills.png')
