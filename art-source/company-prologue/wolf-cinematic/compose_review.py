"""Compose the Wolf (P) cinematic review sheets from build/review/ into review/ (1920x1080).

  python3 compose_review.py
Each sheet is four renders at half size with captions; the full-size renders
are copied alongside so the review set can be read at 100%.
"""
from pathlib import Path
import shutil
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SRC, DST = HERE / 'build' / 'review', HERE / 'review'
DST.mkdir(exist_ok=True)


def font(s):
    for f in ('/System/Library/Fonts/Supplemental/Arial.ttf', 'C:/Windows/Fonts/arial.ttf'):
        if Path(f).exists():
            return ImageFont.truetype(f, s)
    return ImageFont.load_default()


SHEETS = [
    ('effects', 'WOLF (P) / DRIVES, HAZE AND LAMPS (SWFX)',
     [('three-quarter-fx', 'port bow quarter, effects'), ('stern-quarter-fx', 'starboard quarter, effects'),
      ('engine-detail-fx', 'three HIGH_TECH drives'), ('stern-quarter-fx-dark', 'studio off: drive glow and lamps')]),
    ('firing', 'WOLF (P) / GUNS FIRING',
     [('three-quarter-fire', 'ion cannon, mining blaster, Annihilator ripples'), ('bow-fire-fire', 'the bow guns close'),
      ('asharu-s9-fire', 'S9: 28 mm at 670 m, firing'), ('asharu-s9-fx', 'S9: 28 mm at 670 m, drives lit')]),
]
for name, title, panels in SHEETS:
    sheet = Image.new('RGB', (1920, 1080), (18, 19, 21))
    d = ImageDraw.Draw(sheet)
    d.text((24, 14), title, font=font(30), fill=(235, 235, 235))
    for (src, label), (x, y) in zip(panels, [(0, 64), (960, 64), (0, 572), (960, 572)]):
        p = SRC / f'{src}.png'
        if not p.exists():
            continue
        im = Image.open(p).convert('RGB').resize((960, 540), Image.LANCZOS).crop((0, 30, 960, 500))
        sheet.paste(im, (x, y))
        d.text((x + 16, y + 476), label, font=font(20), fill=(220, 220, 220))
    sheet.save(DST / f'{name}.png', optimize=True)
for p in sorted(SRC.glob('*.png')):
    shutil.copy(p, DST / p.name)
print('sheets', [s[0] for s in SHEETS], 'and', len(list(SRC.glob('*.png'))), 'renders ->', DST)
