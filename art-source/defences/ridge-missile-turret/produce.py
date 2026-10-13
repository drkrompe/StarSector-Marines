"""Retained geometry guide and full-frame export for the ridge AA emplacement.

python produce.py guide
python produce.py export <ImageGen transparent master>
Two 512-square frames sit side by side in the central half of a 1024-square
sheet. Both pivots are frame centers; the launcher points up. Never alpha-crop.
"""
from pathlib import Path
import sys
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]

if sys.argv[1] == 'guide':
    im = Image.new('RGBA', (1024, 1024))
    d = ImageDraw.Draw(im)
    d.polygon([(96, 312), (416, 312), (456, 352), (456, 672),
               (416, 712), (96, 712), (56, 672), (56, 352)], fill='#52585b')
    d.ellipse((116, 372, 396, 652), fill='#272d30', outline='#929795', width=12)
    d.ellipse((161, 417, 351, 607), fill='#4d575b')
    for x in (92, 386):
        for y in (350, 642):
            d.rectangle((x-20, y-20, x+20, y+20), fill='#89928f')
    d.ellipse((658, 402, 878, 622), fill='#40494b')
    d.rectangle((616, 480, 920, 556), fill='#727d7a')
    # Two long rectangular missile pods, clearly separate from central radar.
    for x in (640, 896):
        d.rounded_rectangle((x-52, 330, x+52, 666), 12, fill='#697976')
        d.rectangle((x-42, 340, x+42, 385), fill='#252d30')
        for off in (-23, 23):
            d.ellipse((x+off-14, 343, x+off+14, 371), fill='#b0ada1')
        d.rectangle((x-38, 418, x+38, 602), fill='#485652')
    d.rounded_rectangle((724, 450, 812, 574), 15, fill='#788b87')
    d.rectangle((717, 414, 819, 438), fill='#a0a99b')
    im.save(HERE / 'guide.png')
elif sys.argv[1] == 'export':
    im = Image.open(sys.argv[2]).convert('RGBA')
    if im.width != im.height:
        raise ValueError('Guide contract requires a square master')
    if im.getextrema()[3][0] != 0:
        raise ValueError('Master must have real transparency')
    im.save(HERE / 'source.png')
    scale = im.width / 1024
    out = ROOT / 'mod/graphics/structures'
    out.mkdir(parents=True, exist_ok=True)
    for name, x in [('base', 0), ('mount', 512)]:
        box = tuple(round(v * scale) for v in (x, 256, x+512, 768))
        im.crop(box).resize((256, 256), Image.Resampling.LANCZOS).save(out / f'ridge-aa-{name}.png')
