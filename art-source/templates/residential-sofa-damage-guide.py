from pathlib import Path
from PIL import Image, ImageDraw
import json

# Renders the ImageGen inputs for the residential sofa damage states: the intact sofas as a material reference and
# a geometry guide (columns back N, S, W, E; rows damaged, destroyed). Each slot is drawn back-north and turned.
root = Path(__file__).resolve().parents[2]
ids = ['doodad.residential-sofa-h', 'doodad.residential-sofa-back-s',
       'doodad.residential-sofa-v', 'doodad.residential-sofa-back-e']
turns = [0, 180, 90, 270]
doc = json.loads((root / 'mod/data/tilesets/doodads.tileset.json').read_text())
atlas = Image.open(root / 'mod' / doc['sheet'])
px = doc['cellPx']
reference = Image.new('RGBA', (2048, 512))
guide = Image.new('RGBA', (2048, 1024))
ARM, BACK, SEAT, SEAM, FOAM, FRAME = '#6f6f4f', '#7b7a57', '#8a8862', '#2c2d22', '#c9bf9a', '#3a3a33'
for i, id in enumerate(ids):
    d = next(x for x in doc['doodads'] if x['id'] == id)
    w, h = d['footprintCells']
    x, y = d['col'] * px, d['row'] * px
    crop = atlas.crop((x, y, x + w * px, y + h * px)).resize((w * 256, h * 256), Image.Resampling.NEAREST)
    reference.paste(crop, (i * 512 + (512 - w * 256) // 2, (512 - h * 256) // 2))
    for state in range(2):
        sofa = Image.new('RGBA', (512, 256))
        draw = ImageDraw.Draw(sofa)
        if state == 0:
            # Still a sofa: arms, back and seat in place; one arm caved, a slashed cushion, foam showing.
            draw.rounded_rectangle((14, 40, 498, 226), radius=16, fill=SEAM)
            draw.rectangle((20, 46, 70, 220), fill=ARM)
            draw.polygon([(442, 46), (492, 52), (486, 200), (450, 214)], fill=ARM)
            draw.rectangle((70, 46, 442, 100), fill=BACK)
            draw.rectangle((76, 106, 252, 214), fill=SEAT)
            draw.rectangle((262, 106, 436, 214), fill=SEAT)
            draw.polygon([(300, 124), (360, 116), (392, 168), (330, 196), (292, 160)], fill=FOAM)
            draw.line([(140, 54), (176, 92)], fill=SEAM, width=8)
            draw.polygon([(442, 46), (470, 46), (452, 70)], fill=(0, 0, 0, 0))
            # Narrow it to the intact sprite's own width, 44..466 of the slot.
            narrowed = sofa.crop((14, 0, 498, 256)).resize((422, 256), Image.Resampling.BICUBIC)
            sofa = Image.new('RGBA', (512, 256))
            sofa.paste(narrowed, (44, 0))
        else:
            # Flattened: a bent frame rail, a split back, cushions thrown apart, loose foam; ground between.
            draw.line([(30, 210), (24, 60), (120, 48)], fill=FRAME, width=12)
            draw.line([(380, 214), (486, 206), (480, 120)], fill=FRAME, width=12)
            draw.polygon([(60, 54), (200, 50), (210, 92), (70, 100)], fill=BACK)
            draw.polygon([(256, 58), (370, 48), (392, 88), (262, 96)], fill=BACK)
            draw.polygon([(84, 128), (214, 118), (228, 196), (96, 210)], fill=SEAT)
            draw.polygon([(282, 132), (392, 140), (380, 214), (270, 202)], fill=SEAT)
            draw.polygon([(420, 70), (470, 64), (476, 108), (426, 112)], fill=ARM)
            draw.polygon([(236, 118), (262, 112), (266, 140), (240, 146)], fill=FOAM)
            draw.polygon([(150, 150), (182, 146), (178, 172)], fill=FOAM)
        sofa = sofa.rotate(turns[i], expand=True)
        guide.paste(sofa, (i * 512 + (512 - sofa.width) // 2, state * 512 + (512 - sofa.height) // 2))
reference.save(root / 'art-source/templates/residential-sofa-reference.png')
guide.save(root / 'art-source/templates/residential-sofa-damage-guide.png')
