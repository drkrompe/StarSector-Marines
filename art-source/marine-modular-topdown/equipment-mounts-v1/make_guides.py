"""Deterministic geometry specifications only; ImageGen supplies finished art."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
GEOMETRY = {
    "missile-pod": {"guide_canvas": [1024, 1024], "guide_bounds": [332, 227, 692, 797],
                    "export_canvas": [128, 192], "content_size": [96, 152],
                    "runtime_content_size_sw": [.24, .38], "forward": "north", "mount": "left-pauldron"},
    "screen-emitter": {"guide_canvas": [1024, 1024], "guide_bounds": [332, 272, 692, 752],
                       "export_canvas": [128, 160], "content_size": [72, 96],
                       "runtime_content_size_sw": [.18, .24], "forward": "north", "mount": "left-pauldron"},
}

def main():
    for name, geometry in GEOMETRY.items():
        image = Image.new("RGBA", geometry["guide_canvas"])
        draw = ImageDraw.Draw(image)
        x0, y0, x1, y1 = geometry["guide_bounds"]
        draw.polygon([(x0+35,y0),(x1-35,y0),(x1,y0+35),(x1,y1-35),
                      (x1-35,y1),(x0+35,y1),(x0,y1-35),(x0,y0+35)],
                     fill=(74,84,99,255), outline=(165,177,191,255), width=10)
        if name == "missile-pod":
            for cx in (400, 512, 624):
                draw.rounded_rectangle((cx-37,y0+30,cx+37,y1-85), radius=20,
                                       fill=(39,46,57,255), outline=(127,141,157,255), width=7)
                draw.rectangle((cx-28,y0+30,cx+28,y0+75), fill=(16,21,28,255))
            draw.rectangle((x0+45,y1-65,x1-45,y1-38),fill=(32,37,44,255))
        else:
            draw.polygon([(x0+55,y0+65),(x1-55,y0+65),(x1-75,y0+140),(x0+75,y0+140)],
                         fill=(39,155,173,255))
            draw.rectangle((x0+75,y0+190,x1-75,y1-100),fill=(39,46,57,255))
            draw.rectangle((x0+45,y1-70,x1-45,y1-40),fill=(27,32,40,255))
        image.save(HERE / f"{name}-guide.png")
    (HERE / "geometry.json").write_text(json.dumps(GEOMETRY, indent=2)+"\n")

if __name__ == "__main__":
    main()
