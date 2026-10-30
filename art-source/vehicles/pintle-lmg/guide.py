"""Geometry guide for the roof light machine gun: the drawing a hull's fitted LIGHT_MACHINE_GUN turns on its gun plate.

The runtime draws the gun's whole sprite VehicleGun.lengthCells long, muzzle at the top edge and the rear of the spade
grips at the bottom edge, its centre column on the turret's bearing and the pintle VehicleGun.RING_FROM_REAR (40%) of
the length up from the bottom, on the plate's centre. So the barrel, the receiver and the pintle all stand on the
canvas's centre column; only the ammunition drum sits off it, on the gun's right. The sprite is cut symmetric about the
barrel (build.py), leaving clear canvas on the left to balance the drum.

Every size is a share of the gun's length L. The plate's flat rail (gun-plate/guide.py) is .152 L wide, so the receiver
(.11 L) and the cradle stand inside it and the barrel jacket (.055 L) passes through the shield's notch.

  guide.png        the edit target: 1024 x 1536, gun L = 1300 px from y 118 (muzzle) to y 1418 (rear), axis x 512

Run from anywhere: python art-source/vehicles/pintle-lmg/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
W, H = 1024, 1536
AXIS = W / 2
TOP = 118
L = 1300
PIVOT = TOP + .6 * L          # the pintle: 40% of the length up from the rear
DRUM_X = AXIS + .16 * L       # the drum's centre, on the gun's right
DRUM_Y = TOP + .52 * L
DRUM_R = .12 * L


def y(share: float) -> float:
    return TOP + share * L


def box(draw, half_width_share, top_share, bottom_share, fill, outline=(40, 40, 40, 255), width=5):
    half = half_width_share * L
    draw.rectangle((AXIS - half, y(top_share), AXIS + half, y(bottom_share)), fill=fill, outline=outline, width=width)


def guide() -> Image.Image:
    im = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(im)
    # The pintle cradle: a yoke round the receiver at the pivot, its trunnion caps showing either side.
    box(d, .085, .555, .645, (95, 95, 95, 255))
    for side in (-1, 1):
        cx = AXIS + side * .085 * L
        d.ellipse((cx - 22, PIVOT - 22, cx + 22, PIVOT + 22), fill=(70, 70, 70, 255), outline=(30, 30, 30, 255), width=4)
    # Barrel: flash hider at the tip, bare barrel, perforated cooling jacket back to the receiver.
    box(d, .026, .0, .055, (55, 55, 55, 255))
    box(d, .017, .055, .14, (75, 75, 75, 255))
    box(d, .0275, .14, .40, (120, 120, 120, 255))
    for i in range(9):
        yy = y(.16 + i * .026)
        d.ellipse((AXIS - 9, yy - 9, AXIS + 9, yy + 9), fill=(45, 45, 45, 255))
    # Front sight post and the carrying handle on the jacket.
    box(d, .012, .085, .105, (50, 50, 50, 255))
    # Receiver with its hinged top cover, the cocking handle on the left.
    box(d, .055, .40, .78, (150, 150, 150, 255))
    box(d, .042, .43, .62, (170, 170, 170, 255), width=4)
    d.rectangle((AXIS - .055 * L - 40, y(.66), AXIS - .055 * L, y(.69)), fill=(70, 70, 70, 255), outline=(30, 30, 30, 255), width=3)
    # The drum: seated on the receiver's right, its feed throat meeting the receiver side.
    d.rectangle((AXIS + .05 * L, DRUM_Y - 26, DRUM_X - DRUM_R + 20, DRUM_Y + 26), fill=(100, 100, 100, 255),
                outline=(35, 35, 35, 255), width=4)
    d.ellipse((DRUM_X - DRUM_R, DRUM_Y - DRUM_R, DRUM_X + DRUM_R, DRUM_Y + DRUM_R), fill=(165, 165, 165, 255),
              outline=(35, 35, 35, 255), width=6)
    d.ellipse((DRUM_X - DRUM_R * .62, DRUM_Y - DRUM_R * .62, DRUM_X + DRUM_R * .62, DRUM_Y + DRUM_R * .62),
              outline=(70, 70, 70, 255), width=5)
    d.ellipse((DRUM_X - 26, DRUM_Y - 26, DRUM_X + 26, DRUM_Y + 26), fill=(80, 80, 80, 255), outline=(35, 35, 35, 255), width=4)
    # Back plate and the twin spade grips with the butterfly trigger between them.
    box(d, .06, .78, .82, (90, 90, 90, 255))
    for side in (-1, 1):
        x0 = AXIS + side * .07 * L
        d.rectangle((min(x0, x0 + side * .03 * L), y(.82), max(x0, x0 + side * .03 * L), y(1.0)),
                    fill=(60, 60, 60, 255), outline=(25, 25, 25, 255), width=4)
    box(d, .07, .80, .84, (75, 75, 75, 255), width=3)
    box(d, .022, .84, .90, (110, 110, 110, 255), width=3)
    return im


if __name__ == "__main__":
    guide().save(HERE / "guide.png")
    print("wrote", HERE / "guide.png")
