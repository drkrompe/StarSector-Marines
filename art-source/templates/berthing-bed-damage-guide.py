from pathlib import Path
from PIL import Image, ImageDraw
import json
root=Path(__file__).resolve().parents[2]
ids=['doodad.residential-bed-h','doodad.residential-bed-head-e','doodad.residential-bed-v','doodad.residential-bed-head-n']
doc=json.loads((root/'mod/data/tilesets/doodads.tileset.json').read_text())
atlas=Image.open(root/'mod'/doc['sheet']); px=doc['cellPx']
reference=Image.new('RGBA',(2048,512)); guide=Image.new('RGBA',(2048,1024))
for i,id in enumerate(ids):
 d=next(x for x in doc['doodads'] if x['id']==id); w,h=d['footprintCells'];x,y=d['col']*px,d['row']*px
 crop=atlas.crop((x,y,x+w*px,y+h*px)).resize((w*256,h*256),Image.Resampling.NEAREST)
 reference.paste(crop,(i*512+(512-w*256)//2,(512-h*256)//2))
 for state in range(2):
  bed=Image.new('RGBA',(512,256));draw=ImageDraw.Draw(bed)
  if state==0:
   draw.rounded_rectangle((20,25,492,231),radius=18,fill='#7e8786')
   draw.rectangle((42,43,96,210),fill='#b4b8ad')
   draw.rectangle((108,43,474,213),fill='#536c70')
   draw.line([(220,43),(253,103),(237,147),(284,213)],fill='#242b2a',width=12)
   draw.polygon([(314,25),(360,25),(336,67)],fill=(0,0,0,0))
  else:
   draw.line([(145,33),(29,33),(37,135)],fill='#7e8786',width=14)
   draw.line([(370,226),(478,216),(468,132)],fill='#7e8786',width=14)
   draw.polygon([(52,58),(123,65),(110,132),(61,118)],fill='#b4b8ad')
   draw.polygon([(190,53),(320,47),(337,101),(251,118),(178,90)],fill='#536c70')
   draw.polygon([(149,152),(264,145),(297,211),(171,220)],fill='#536c70')
   draw.polygon([(365,94),(435,87),(449,165),(378,175)],fill='#536c70')
   draw.line([(75,178),(122,207)],fill='#7e8786',width=9)
  bed=bed.rotate([0,180,90,270][i],expand=True)
  guide.paste(bed,(i*512+(512-bed.width)//2,state*512+(512-bed.height)//2))
reference.save(root/'art-source/templates/berthing-bed-reference.png')
guide.save(root/'art-source/templates/berthing-bed-damage-guide.png')
