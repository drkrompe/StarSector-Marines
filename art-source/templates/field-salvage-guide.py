"""Geometry only: three open recovery skids, full 512px frames, overhead."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
guide = Image.new('RGBA', (1536, 1024))
for variant in range(3):
    cell = Image.new('RGBA', (512, 512))
    d = ImageDraw.Draw(cell)
    # Clipped corners and projecting lift lugs distinguish caches from closed crates.
    d.polygon([(108,72),(404,72),(440,108),(440,404),(404,440),(108,440),(72,404),(72,108)], fill='#434b4c')
    for x in (94,382):
        d.rounded_rectangle((x,56,x+36,456),8,fill='#ad8537')
    for y in (106,372):
        d.rectangle((78,y,434,y+24),fill='#293335')
    if variant == 0:
        for y in (136,199,262,325):
            d.polygon([(143,y),(350,y),(373,y+38),(162,y+42)], fill='#a3aaa7')
        for x in (179,321): d.rectangle((x,124,x+16,381),fill='#5e5140')
    elif variant == 1:
        d.rounded_rectangle((150,153,309,351),24,fill='#788784')
        for y in range(171,336,24): d.rectangle((162,y,270,y+9),fill='#424d4c')
        d.ellipse((279,220,383,324),fill='#b29e78')
        d.ellipse((302,243,360,301),fill='#354343')
        d.rectangle((137,190,393,207),fill='#8c733d')
        d.rectangle((137,335,393,352),fill='#8c733d')
    else:
        for x,y in ((140,138),(259,138),(140,266),(259,266)):
            d.rounded_rectangle((x,y,x+111,y+116),14,fill='#bac0a9')
            d.rectangle((x+43,y,x+60,y+116),fill='#606856')
        d.rectangle((130,244,382,260),fill='#a7833e')
    d.rounded_rectangle((222,393,290,421),6,fill='#e4b84c')
    guide.alpha_composite(cell,(variant*512,256))
guide.save(ROOT/'art-source/templates/field-salvage-guide.png')
