"""Tile the look render's four panels into one 1920x1080 review sheet, plus page JPEGs.

  python compose_review.py <panels dir> <out dir>

Reads hero/stern/cradle/talon.png (960x540 each, review_look.py) and writes
  01-sheet.png      1920x1080, 100%: the 2x2 sheet with a caption strip on each panel
  01-sheet.jpg      the same at 1600 px wide, for the review page
  02-<panel>.jpg    each panel alone at 1600 px wide (upscaled from 960; the page's close look)
"""
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

SRC, OUT = Path(sys.argv[1]), Path(sys.argv[2])
ORDER = [('hero', 'Mining laser on, three drives running'), ('stern', 'Stern: centre drive and two splayed side drives'),
         ('cradle', 'On the Shepherd: cradles 1 and 3, lifting out of bay 2'),
         ('talon', 'Common scale: Borer 3.6 m, Talon 12 m, figure 1.8 m')]
try:
    FONT = ImageFont.truetype('bahnschrift.ttf', 22)
except OSError:
    FONT = ImageFont.load_default(size=22)
OUT.mkdir(parents=True, exist_ok=True)
sheet = Image.new('RGB', (1920, 1080), (12, 12, 13))
for k, (name, caption) in enumerate(ORDER):
    im = Image.open(SRC / f'{name}.png').convert('RGB')
    assert im.size == (960, 540), (name, im.size)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 540 - 34, 960, 540), fill=(12, 12, 13))
    d.text((12, 540 - 30), caption, font=FONT, fill=(225, 225, 225))
    sheet.paste(im, ((k % 2) * 960, (k // 2) * 540))
    im.resize((1600, 900), Image.LANCZOS).save(OUT / f'02-{name}.jpg', quality=90)
sheet.save(OUT / '01-sheet.png')
sheet.resize((1600, 900), Image.LANCZOS).save(OUT / '01-sheet.jpg', quality=90)
print('COMPOSE', OUT)
