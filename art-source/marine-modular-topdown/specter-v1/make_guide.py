"""Retained geometry authority for the north-facing Specter layer plate."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
SIZE = (1920, 1080)
PARTS = {
    "body": {"region": [80, 250, 820, 850], "target_width": 150},
    "head": {"region": [900, 300, 1280, 740], "target_width": 72},
    "foot": {"region": [1400, 300, 1740, 760], "target_size": [38, 52]},
}

def main():
    image = Image.new("RGBA", SIZE)
    draw = ImageDraw.Draw(image)
    plate, edge, joints = "#7e929e", "#c1cbd0", "#28343a"
    # Symmetric articulated shoulders, torso and compact rear (south) power pack.
    draw.polygon([(150,360),(270,310),(345,340),(555,340),(630,310),
                  (750,360),(750,560),(680,650),(600,665),(560,700),
                  (340,700),(300,665),(220,650),(150,560)], fill=joints)
    for points in [ [(150,360),(260,320),(325,350),(300,530),(225,565),(150,535)],
                    [(750,360),(640,320),(575,350),(600,530),(675,565),(750,535)]]:
        draw.polygon(points, fill=plate, outline=edge, width=5)
    draw.polygon([(325,380),(575,380),(600,570),(555,650),(345,650),(300,570)],
                 fill=plate, outline=edge, width=5)
    draw.rounded_rectangle((345,650,555,740), radius=16, fill=joints, outline=edge,width=5)
    draw.rectangle((370,668,430,720), fill=plate)
    draw.rectangle((470,668,530,720), fill=plate)
    # Empty socket is forward/north of the rear power pack.
    draw.ellipse((336,355,564,575), fill=(0,0,0,0))
    draw.ellipse((328,347,572,583), outline=joints, width=9)
    # Helmet: .38 shoulder-width guide footprint, forward marker at north.
    draw.polygon([(1035,365),(1145,365),(1204,425),(1204,510),(1170,565),
                  (1010,565),(976,510),(976,425)], fill=plate, outline=edge,width=5)
    draw.rectangle((1040,370,1140,382), fill="#62b8c0")
    draw.line((1090,400,1090,540), fill=joints,width=4)
    # Single symmetric boot; runtime owns independent left/right placement.
    draw.polygon([(1513,390),(1627,390),(1665,445),(1665,570),
                  (1625,650),(1515,650),(1475,570),(1475,445)],
                 fill=plate, outline=edge,width=5)
    draw.rounded_rectangle((1520,530,1620,615), radius=18, fill=joints)
    draw.line((1500,455,1640,455),fill=joints,width=5)
    image.save(HERE / "guide.png")
    metadata = {"canvas": list(SIZE), "facing": "north", "north_axis": "negativeY",
                "parts": PARTS, "body_socket_center": [450,465],
                "body_socket_bounds": [336,355,564,575],
                "body_opaque_bounds": [150,310,751,741]}
    (HERE / "geometry.json").write_text(json.dumps(metadata, indent=2)+"\n")

if __name__ == "__main__":
    main()
