"""Label the common-scale lineup and add a metric scale bar.

Run with ordinary Python (Pillow) after lineup.py. Writes lineup.png
(1920x1080): the top view in metres with each fighter's name, length,
span and metres_per_unit, a 10 m bar, and the three-quarter view inset.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
BG = (222, 223, 223)
INK, SOFT = (39, 49, 55), (72, 81, 88)
font = lambda s: ImageFont.truetype('C:/Windows/Fonts/arial.ttf', s)
rec = json.loads((HERE/'lineup.json').read_text(encoding='utf-8'))
top = Image.open(HERE/'lineup-top.png').convert('RGBA')
sheet = Image.new('RGBA', (1920, 1080), BG+(255,))
sheet.alpha_composite(top)
d = ImageDraw.Draw(sheet)
d.text((28, 15), 'FIGHTER BLOCKOUTS / COMMON SCALE, METRES', font=font(32), fill=INK)
d.text((28, 55), 'Each hull at its own metres_per_unit (from its cockpit, guns and drives); nose up, longest first. '
                 'Pass 1 geometry review only.', font=font(19), fill=SOFT)
for f in rec['fighters']:
    x = f['centre_px'][0]
    y = f['bottom_px']+14
    for i, line in enumerate((f['name'].upper(), f"{f['length_m']} x {f['span_m']} m", f"{f['metres_per_unit']} m/unit")):
        w = d.textlength(line, font=font(20 if i == 0 else 16))
        d.text((x-w/2, y+i*22), line, font=font(20 if i == 0 else 16), fill=INK if i == 0 else SOFT)
k = rec['px_per_metre']
x0, y0 = 60, 1040
d.rectangle((x0, y0, x0+10*k, y0+8), fill=INK)
for i in range(11):
    d.line((x0+i*k, y0-6 if i % 5 == 0 else y0-3, x0+i*k, y0), fill=INK, width=2)
d.text((x0+10*k+12, y0-10), '10 m', font=font(18), fill=INK)
tq = HERE/'lineup-three-quarter.png'
if tq.exists():
    inset = Image.open(tq).convert('RGBA').resize((576, 324), Image.LANCZOS)
    sheet.alpha_composite(inset, (1920-576-20, 1080-324-20))
sheet.convert('RGB').save(HERE/'lineup.png', optimize=True)
print('wrote lineup.png')
