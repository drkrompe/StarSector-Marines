"""Geometry guide for the Hegemony emitter: the drawing an Onager fitted with a three-row energy weapon carries under its
closed Hegemony head, in place of the mech's white Tri-Tachyon emitter (owner: the white looks cartoonish there).

The runtime draws a fitted gun's whole sprite VehicleGun.lengthCells long, muzzle at the top edge and the rear at the
bottom, its pivot VehicleGun.RING_FROM_REAR (40%) of the length up from the bottom on the ring's centre. Under the
Onager's closed head about the front third shows ahead of the mantlet, the middle half is hidden under the head and the
last seventh shows behind it (measured on a vehicle-colours still), so the detail goes at the two ends: the barrel and
its aperture ahead, a power coupling behind. The hidden middle is a plain block that only has to join them.

Every size is a share of the gun's length L; the sprite is cut symmetric about the barrel, .4 L wide, the mech PL-3
drawing's proportion (build.py).

  guide.png        the edit target: 1024 x 1536, gun L = 1300 px from y 118 (muzzle) to y 1418 (rear), axis x 512

Run from anywhere: python art-source/vehicles/hegemony-emitter/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
W, H = 1024, 1536
AXIS = W / 2
TOP = 118
L = 1300


def y(share: float) -> float:
    return TOP + share * L


def box(draw, half_width_share, top_share, bottom_share, fill, outline=(40, 40, 40, 255), width=5):
    half = half_width_share * L
    draw.rectangle((AXIS - half, y(top_share), AXIS + half, y(bottom_share)), fill=fill, outline=outline, width=width)


def guide() -> Image.Image:
    im = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(im)
    # The hidden body under the head: one plain block joining the barrel to the coupling.
    box(d, .13, .30, .87, (110, 110, 110, 255))
    # The barrel ahead of the mantlet: aperture housing at the tip, then a shroud with cooling rings.
    box(d, .062, .0, .07, (70, 70, 70, 255))
    box(d, .036, .005, .03, (150, 40, 30, 255), outline=(30, 20, 20, 255), width=4)
    box(d, .05, .07, .32, (130, 130, 130, 255))
    for i in range(6):
        top = .095 + i * .036
        box(d, .066, top, top + .016, (90, 90, 90, 255), width=4)
    # Behind the head: the power coupling, a wider box with two cable glands on its rear face.
    box(d, .15, .87, .985, (120, 120, 120, 255))
    for side in (-1, 1):
        cx = AXIS + side * .075 * L
        d.ellipse((cx - 30, y(.95) - 30, cx + 30, y(.95) + 30), fill=(60, 60, 60, 255), outline=(25, 25, 25, 255),
                  width=4)
    return im


if __name__ == "__main__":
    guide().save(HERE / "guide.png")
    print("wrote", HERE / "guide.png")
