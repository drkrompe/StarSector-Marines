"""Compose the armour study renders (study_armour.py) into 1920x1080 sheets (Pillow).

  python3 compose_study.py --dir DIR
Writes study-sheet.png (options in rows, cameras in columns) and one sheet per
camera with the three options side by side (study-<camera>.png).
"""
from pathlib import Path
import argparse
import sys
from PIL import Image, ImageDraw, ImageFont

FONT = '/System/Library/Fonts/Supplemental/Arial.ttf' if sys.platform == 'darwin' else 'C:/Windows/Fonts/arial.ttf'
font = lambda s: ImageFont.truetype(FONT, s)
OPTIONS = {'A': 'A  THERMAL TILES - 2.4 m ceramic tile grid; graphite and titanium machinery',
           'B': 'B  HEX ABLATIVE - 3.5 m crowned hexagonal ceramic plates; blue-black ceramic machinery',
           'C': 'C  REFRACTIVE PANELS - 8 x 4 m glazed thin-film ceramic, glowing seams; black-glass machinery'}
CAMS = ['study-quarter', 'study-aft', 'study-bow', 'study-plate']
ap = argparse.ArgumentParser()
ap.add_argument('--dir', required=True)
src = Path(ap.parse_args().dir)
BG = (222, 223, 223)

sheet = Image.new('RGB', (1920, 1080), BG)
d = ImageDraw.Draw(sheet)
d.text((20, 10), 'PARAGON / TRI-TACHYON ARMOUR AND MACHINERY CHARACTER - three ceramic options, same cameras',
       font=font(26), fill=(39, 49, 55))
tw, th = 472, 266
for r, (opt, label) in enumerate(OPTIONS.items()):
    y = 52+r*(th+76)
    d.text((20, y), label, font=font(19), fill=(39, 49, 55))
    for c, cam in enumerate(CAMS):
        p = src/f'{opt}-{cam}.png'
        if p.exists():
            sheet.paste(Image.open(p).convert('RGB').resize((tw, th), Image.LANCZOS), (8+c*(tw+6), y+28))
sheet.save(src/'study-sheet.png', optimize=True)

for cam in CAMS:
    s = Image.new('RGB', (1920, 1080), BG)
    d = ImageDraw.Draw(s)
    d.text((20, 10), f'PARAGON / ARMOUR STUDY - {cam.replace("study-", "").upper()} - A | B | C', font=font(26),
           fill=(39, 49, 55))
    for i, opt in enumerate(OPTIONS):
        p = src/f'{opt}-{cam}.png'
        if not p.exists():
            continue
        im = Image.open(p).convert('RGB')
        im = im.crop((240, 0, 1680, 1080)).resize((632, 474), Image.LANCZOS) if cam != 'study-quarter' \
            else im.crop((160, 100, 1760, 980)).resize((632, 348), Image.LANCZOS)
        s.paste(im, (8+i*638, 60))
        y = 60+im.height+8
        words, line, lines = OPTIONS[opt].split(' '), '', []
        for w in words:
            if len(line)+len(w) > 46:
                lines.append(line)
                line = ''
            line += w+' '
        lines.append(line)
        for k, t in enumerate(lines):
            d.text((14+i*638, y+k*24), t, font=font(19), fill=(39, 49, 55))
    s.save(src/f'study-{cam}.png', optimize=True)
print('sheets written to', src)
