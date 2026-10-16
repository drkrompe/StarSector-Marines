"""Assemble unretouched Blender geometry views for the ImageGen reskin input.
Run with ordinary Python + Pillow, after build_reference.py in Blender.

With no arguments it composes the clean guide in this folder. --state NAME
composes a damage state's review sheet from states/NAME/ into that folder.
"""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
STATES = {
    'derelict': ('SECOND WIND / DERELICT - DAMAGE STATE GEOMETRY',
                 'NEARLY A DECADE IN THE BELT  |  UNPOWERED PARTS BIN  |  IMPACT BASINS, TORN PLATES, SHEARED MAST, SMASHED PORT CAN',
                 'Review render of real damage geometry over the clean V3 guide. No lights; every pod dead. Silhouette, bays, mounts and engine centres unchanged.'),
    'patched': ('SECOND WIND / PATCHED - DAMAGE STATE GEOMETRY',
                'EMERGENCY ESCAPE REPAIRS  |  WELDED SALVAGE PLATES, SPLINTS, CARGO-RAIL SHORE  |  STARBOARD POD ON A SALVAGED DRIVE COIL',
                'Review render of real damage geometry. Port pod dead, stripped and blanked; starboard pod runs on the bolted-on coil. No fire, no breaches.'),
}
CLEAN_PANELS = [('port-quarter','PORT BOW QUARTER'), ('top','TOP / BOW LEFT - PORT BELOW'),
                ('port-side','PORT SIDE / INNER ENGINES OCCLUDED'),
                ('stern','STERN / LARGE OUTER, SMALL INNER NOZZLES')]
STATE_PANELS = [('port-quarter','PORT BOW QUARTER'), ('starboard-quarter','STARBOARD BOW QUARTER'),
                ('top','TOP / BOW LEFT - PORT BELOW'), ('stern','STERN / PORT ON THE LEFT')]

parser = argparse.ArgumentParser()
parser.add_argument('--state', choices=sorted(STATES))
args = parser.parse_args()

if args.state:
    src = ROOT/'states'/args.state
    title, subtitle, footer = STATES[args.state]
    panels, out = STATE_PANELS, src/f'second-wind-{args.state}.reference.png'
else:
    src = ROOT
    title = 'SECOND WIND / GEOMETRY V3 DETAIL'
    subtitle = 'SEGMENTED HULL  |  ENGINE PODS  |  SPINE COMMAND DECK  |  PORT GRAPPLE'
    footer = 'Proposed design, scale provisional. Port grapple is the only asymmetric fitting. Geometry review only.'
    panels, out = CLEAN_PANELS, ROOT/'second-wind-geometry.reference.png'

sheet = Image.new('RGB', (1920,1080), '#dedfdf')
draw = ImageDraw.Draw(sheet)
fontpath = Path('C:/Windows/Fonts/arial.ttf')
def font(size):
    return ImageFont.truetype(str(fontpath),size)

draw.text((28,15),title,font=font(32),fill='#273137')
draw.text((28,55),subtitle,font=font(19),fill='#485158')
for (name,label),(x,y) in zip(panels,[(0,92),(960,92),(0,580),(960,580)]):
    sheet.paste(Image.open(src/(name+'.png')).convert('RGB'),(x,y))
    draw.text((x+28,y+442),label,font=font(20),fill='#273137')
draw.text((28,1050),footer,font=font(17),fill='#485158')
sheet.save(out,optimize=True)
