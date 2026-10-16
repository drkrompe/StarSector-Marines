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
    'derelict': ('SECOND WIND / DERELICT - CONDITION STATE GEOMETRY',
                 'NEARLY A DECADE IN THE BELT  |  UNPOWERED PARTS BIN  |  WINDWARD STRIKES, SALVAGE STRIPPING, BOTH PODS DEAD',
                 'Scar table over the clean V3 guide (sw_damage.py). No power. Silhouette, bays, mounts and engine centres unchanged.'),
    'patched': ('SECOND WIND / PATCHED - CONDITION STATE GEOMETRY',
                'THE ESCAPE  |  ONLY WHAT THE FLIGHT NEEDED  |  STARBOARD POD ON A JURY-RIGGED SALVAGED COIL',
                'Same scar ids as derelict. Port pod dead and cold, feed lines capped. No fire, no breaches.'),
    'refit': ('SECOND WIND / REFIT - CONDITION STATE GEOMETRY',
              'LIMITED BERTHING / COMMAND CONVERSION  |  A FEW PATCHES REPLACED BY PROPER PLATES',
              'Same scar ids. Heavy craters and the dead port pod remain; fresh primer plates and seams mark the new work.'),
    'restored': ('SECOND WIND / RESTORED - CONDITION STATE GEOMETRY',
                 'STRUCTURALLY WHOLE  |  BOTH PODS RUNNING  |  REPAINTED, NOT NEW',
                 'Same scar ids. Replacement plates repainted near the hull colour; old seams and dents still read under the paint.'),
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
