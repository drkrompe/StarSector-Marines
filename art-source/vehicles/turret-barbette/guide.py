"""Draw the silhouette guide for the open Hegemony barbette (hegemony-armor-line.md).

An open-topped armoured ring fixed to the hull, seen from straight above, its front toward the top of the canvas:
a chamfered octagonal outer wall (thicker across the front, the shield a raised launcher fires over), a wide open
well with a dark opaque deck and the turntable's bearing race in it, and two ammunition lockers on the rear wall.
The well is empty: the weapon and its turntable are separate layers drawn over it. The canvas is 1024 px and the
outer wall's flats are 940 px apart, so the sprite is cut to that square.
Run: python art-source/vehicles/turret-barbette/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
S = 1024
C = S // 2


def octagon(r: float, chamfer: float, dy: float = 0):
    a = r - chamfer
    return [(C - a, C - r + dy), (C + a, C - r + dy), (C + r, C - a + dy), (C + r, C + a + dy),
            (C + a, C + r + dy), (C - a, C + r + dy), (C - r, C + a + dy), (C - r, C - a + dy)]


def main() -> None:
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon(octagon(470, 150), fill=(120, 120, 120, 255))      # outer wall face
    d.polygon(octagon(440, 140), fill=(175, 175, 175, 255))      # wall top
    d.rectangle((C - 260, C - 470, C + 260, C - 330), fill=(195, 195, 195, 255))  # thick front shield
    d.ellipse((C - 335, C - 300, C + 335, C + 370), fill=(40, 40, 40, 255))       # open well, deck
    d.ellipse((C - 290, C - 255, C + 290, C + 325), outline=(85, 85, 85, 255), width=26)  # bearing race
    d.rectangle((C - 300, C + 330, C - 140, C + 440), fill=(150, 150, 150, 255))  # rear lockers
    d.rectangle((C + 140, C + 330, C + 300, C + 440), fill=(150, 150, 150, 255))
    im.save(HERE / "guide.png")


if __name__ == "__main__":
    main()
