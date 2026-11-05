"""Draw the silhouette guide for the shared turret head (hegemony-armor-line.md, slice 1).

The head is seen from straight above, its gun port toward the top of the canvas (north, the turret's facing 0): a
chamfered octagonal housing on a round base, a raised mantlet across its front with a dark slot through which the
fitted weapon's barrel pokes, and a short rear bustle. Plain grey planes imply height; the slot is the only black.
The canvas is 1024 px; the base circle is 760 px, so the head is a little larger than the ring it sits in.
Run: python art-source/vehicles/turret-head/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
S = 1024
C = S // 2


def octagon(r: float, chamfer: float):
    a = r - chamfer
    return [(C - a, C - r), (C + a, C - r), (C + r, C - a), (C + r, C + a),
            (C + a, C + r), (C - a, C + r), (C - r, C + a), (C - r, C - a)]


def main() -> None:
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((C - 380, C - 380, C + 380, C + 380), fill=(92, 92, 92, 255))  # base ring skirt
    d.rectangle((C - 150, C + 300, C + 150, C + 420), fill=(110, 110, 110, 255))  # rear bustle
    d.polygon(octagon(330, 110), fill=(150, 150, 150, 255))  # housing sides
    d.polygon(octagon(250, 85), fill=(185, 185, 185, 255))  # roof
    d.rectangle((C - 230, C - 400, C + 230, C - 250), fill=(130, 130, 130, 255))  # mantlet
    d.rectangle((C - 200, C - 385, C + 200, C - 270), fill=(165, 165, 165, 255))  # mantlet face
    d.rectangle((C - 70, C - 420, C + 70, C - 300), fill=(10, 10, 10, 255))  # gun slot, open to the front edge
    d.ellipse((C - 90, C - 40, C + 90, C + 140), fill=(160, 160, 160, 255))  # commander's hatch
    im.save(HERE / "guide.png")


if __name__ == "__main__":
    main()
