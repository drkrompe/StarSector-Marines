from pathlib import Path
from PIL import Image, ImageDraw
# Geometry guide only: 512-square slots, 128 px/cell full placement frames.
# Columns: 3x3 turbine-alternator skid, 2x3 coolant/heat-exchanger skid.
# Rows: intact, standing damage, separated low wreckage.
out=Image.new('RGBA',(1024,1536)); d=ImageDraw.Draw(out)
for row in range(3):
    for col,w in enumerate((384,256)):
        x=col*512+(512-w)//2; y=row*512+64; h=384
        if row==2:
            for box in [(12,14,w*.40,49),(w*.65,22,w-14,85),(16,140,w*.30,238),
                        (w*.55,180,w-20,235),(20,300,w*.38,363),(w*.65,302,w-12,362)]:
                d.rectangle((x+box[0],y+box[1],x+box[2],y+box[3]),fill='#697879')
            d.ellipse((x+w*.42,y+100,x+w*.65,y+155),fill='#aaa895')
            continue
        d.rounded_rectangle((x+8,y+8,x+w-8,y+h-8),10,fill='#3a4445',outline='#a6afa3',width=8)
        if col==0:
            d.rounded_rectangle((x+65,y+45,x+215,y+340),45,fill='#919d92',outline='#4d5f5f',width=8)
            for yy in range(95,290,24): d.rectangle((x+62,y+yy,x+220,y+yy+9),fill='#475d60')
            d.rectangle((x+245,y+76,x+337,y+306),fill='#616b66',outline='#b6b5a3',width=5)
            for yy in range(90,294,18): d.line((x+254,y+yy,x+329,y+yy),fill='#323d42',width=8)
            for yy in (33,341): d.rectangle((x+30,y+yy,x+355,y+yy+9),fill='#ad9b61')
        else:
            for yy in (52,192):
                d.ellipse((x+28,y+yy,x+145,y+yy+117),fill='#9ca69b',outline='#4d6261',width=10)
                d.ellipse((x+69,y+yy+41,x+103,y+yy+75),fill='#495e61')
            d.rectangle((x+175,y+42,x+229,y+337),fill='#465858')
            for yy in range(55,325,20): d.rectangle((x+181,y+yy,x+222,y+yy+8),fill='#9ca89b')
            d.line((x+151,y+35,x+158,y+350),fill='#ad9b61',width=12)
        if row==1:
            d.line((x+36,y+120,x+w*.5,y+170,x+w*.42,y+260,x+w-30,y+300),fill='#202729',width=10)
            d.rectangle((x+w-53,y+45,x+w-20,y+76),fill='#55433b')
out.save(Path(__file__).with_suffix('.png'))
