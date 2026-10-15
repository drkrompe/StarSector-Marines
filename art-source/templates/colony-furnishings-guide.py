from PIL import Image, ImageDraw
from pathlib import Path
# Six full 256 px placement frames per row; no recentering at export.
image = Image.new("RGBA", (1536, 768))
draw = ImageDraw.Draw(image)
colors = ["#71818b", "#b8bcad", "#899b9f", "#697a72", "#b8b8ab", "#9d9f8f"]
for row in range(3):
    for col in range(6):
        x, y = col*256, row*256
        color = colors[col]
        if row == 2:
            for points in [[(39,48),(112,36),(98,100),(43,109)],[(135,42),(213,54),(217,115),(148,101)],[(57,144),(105,123),(121,207),(47,217)],[(146,136),(211,155),(205,214),(141,201)]]:
                draw.polygon([(x+a,y+b) for a,b in points], fill=color)
            continue
        draw.rounded_rectangle((x+32,y+36,x+224,y+220), radius=14, fill=color, outline="#303d44", width=8)
        if col == 0:
            draw.rounded_rectangle((x+54,y+62,x+202,y+194), radius=9, outline="#b8c2bd", width=7)
            for a in [66,178]: draw.rectangle((x+a,y+37,x+a+12,y+65),fill="#303d44")
        elif col == 1:
            draw.line((x+128,y+45,x+128,y+208),fill="#404b4b",width=7)
            draw.rectangle((x+109,y+104,x+116,y+140),fill="#ced5ca")
        elif col == 2:
            draw.rectangle((x+50,y+53,x+205,y+116),fill="#26373b")
            draw.rectangle((x+62,y+65,x+192,y+103),fill="#498e8c")
            draw.rectangle((x+68,y+150,x+188,y+180),fill="#394b51")
        elif col == 3:
            for a in range(66,181,20): draw.line((x+57,y+a,x+143,y+a), fill="#394c48",width=7)
            draw.rectangle((x+171,y+60,x+204,y+98),fill="#bfaa6b")
        elif col == 4:
            draw.rounded_rectangle((x+50,y+60,x+124,y+130),radius=12,fill="#445056")
            draw.ellipse((x+150,y+63,x+200,y+113),fill="#445056")
            draw.rectangle((x+54,y+158,x+200,y+190),fill="#787f7b")
        else:
            for a in [52,107,162]:
                draw.rectangle((x+48,y+a,x+208,y+a+40),fill="#444e50")
                for b in [57,109,161]: draw.rectangle((x+b,y+a+6,x+b+33,y+a+30),fill="#aab2a3")
        if row == 1:
            draw.line([(x+40,y+75),(x+94,y+108),(x+77,y+150),(x+154,y+203)],fill="#252c30",width=14)
            draw.polygon([(x+185,y+45),(x+215,y+49),(x+207,y+81),(x+173,y+101)],fill="#252c30")
image.save(Path(__file__).with_suffix(".png"))
