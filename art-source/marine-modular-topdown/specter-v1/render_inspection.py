"""Disposable 1920x1080 registered-mask and paint inspection plate."""
import json
from PIL import Image, ImageDraw
from export_assets import HERE, OUTPUT, REPOSITORY

def main():
    data=json.loads((HERE/"export-measurements.json").read_text())
    source=Image.open(HERE/"source.png").convert("RGBA")
    canvas=Image.new("RGBA",(1920,1080),(25,30,35,255))
    draw=ImageDraw.Draw(canvas)
    for index,(name,part) in enumerate(data["parts"].items()):
        base=source.crop(tuple(part["source_content_bounds"]))
        mask=Image.open(HERE/"masks"/f"{name}-source.png").convert("L")
        overlay=Image.new("RGBA",base.size,(255,0,255,0))
        overlay.putalpha(mask.point(lambda a:round(a*.48)))
        shown=Image.alpha_composite(base,overlay)
        shown.thumbnail((680,420))
        x=(30,740,1300)[index]
        canvas.alpha_composite(shown,(x,90))
        draw.text((x,40),name+" registered casing mask overlay",fill="white")
    for row,paint in enumerate(("slate","olive","oxide","sand")):
        draw.text((40,570+row*115),paint,fill="white")
        directory=OUTPUT if paint=="slate" else OUTPUT/"paints"/paint
        for column,name in enumerate(("body","head","foot")):
            canvas.alpha_composite(Image.open(directory/f"{name}.png"),
                                   (220+column*240,555+row*115))
    preview=REPOSITORY/"build/sprite-previews/infantry/specter-mask-registration.png"
    preview.parent.mkdir(parents=True,exist_ok=True)
    canvas.save(preview)
    print(preview)

if __name__=="__main__":
    main()
