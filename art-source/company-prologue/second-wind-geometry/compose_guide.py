"""Assemble unretouched Blender geometry views for the ImageGen reskin input.
Run with ordinary Python + Pillow, after build_reference.py in Blender.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
sheet = Image.new('RGB', (1920,1080), '#dedfdf')
draw = ImageDraw.Draw(sheet)
fontpath = Path('C:/Windows/Fonts/arial.ttf')
def font(size):
    return ImageFont.truetype(str(fontpath),size)

draw.text((28,15),'SECOND WIND / GEOMETRY V3 DETAIL',font=font(32),fill='#273137')
draw.text((28,55),'FACETED HULL  |  AFT BRIDGE  |  NAVAL TURRETS  |  FIXED POD AND ENGINES',font=font(19),fill='#485158')
panels=[('port-quarter',0,92,'PORT BOW QUARTER'),
        ('top',960,92,'TOP / BOW LEFT - PORT BELOW'),
        ('port-side',0,580,'PORT SIDE / INNER ENGINES OCCLUDED'),
        ('stern',960,580,'STERN / LARGE OUTER, SMALL INNER NOZZLES')]
for name,x,y,label in panels:
    sheet.paste(Image.open(ROOT/(name+'.png')).convert('RGB'),(x,y))
    draw.text((x+28,y+442),label,font=font(20),fill='#273137')
draw.text((28,1050),'Proposed design, scale provisional. Teal identifies the fixed port service pod. Geometry review only.',font=font(17),fill='#485158')
sheet.save(ROOT/'second-wind-geometry.reference.png',optimize=True)
