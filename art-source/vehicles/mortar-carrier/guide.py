"""Geometry guides for the mortar carrier's roof turret (VehicleType.FIRE_SUPPORT_APC).

Each guide is the turret frame at twice the shipped scale: 1536x1152 is one 768x576 slot of the APC sheet, whose
2.4-cell width puts 320 px in a cell, so the guide draws 640 px a cell. The pivot is the canvas centre and the gun
points LEFT, as every vehicle sheet's does. Flat plane values stand in for height: the lighter the plane, the higher
it stands. ImageGen repaints the planes in the APC's materials and keeps their places (`*.prompt.txt`).

  enclosed-guide.png  a low armoured turret house with one long breech-loaded 120 mm tube (option A)
  open-guide.png      an open turntable with a raised heavy tube, its bore toward the eye, and a shell rack (option B)

Run from anywhere:
    python art-source/vehicles/mortar-carrier/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
W, H = 1536, 1152
CELL = 640.0
CX, CY = W / 2, H / 2

OUTLINE = (24, 25, 23, 255)
TOP = (176, 178, 168, 255)
UPPER = (150, 152, 142, 255)
SLOPE = (118, 120, 112, 255)
LOW = (92, 94, 88, 255)
METAL = (70, 72, 70, 255)
BORE = (16, 16, 16, 255)


def px(forward, lateral):
    """A point `forward` cells ahead of the pivot (toward the left) and `lateral` cells to its starboard (down)."""
    return (CX - forward * CELL, CY + lateral * CELL)


def poly(d, points, fill, width=5):
    d.polygon([px(f, l) for f, l in points], fill=fill, outline=OUTLINE, width=width)


def rect(d, fwd_a, fwd_b, lat_a, lat_b, fill, width=5):
    poly(d, [(fwd_a, lat_a), (fwd_b, lat_a), (fwd_b, lat_b), (fwd_a, lat_b)], fill, width)


def circle(d, forward, lateral, radius, fill, width=5):
    x, y = px(forward, lateral)
    r = radius * CELL
    d.ellipse((x - r, y - r, x + r, y + r), fill=fill, outline=OUTLINE, width=width)


def enclosed():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Turret house: a low hexagon, 0.62 cells nose to tail and 0.56 across, the pivot a little ahead of its middle.
    house = [(0.22, -0.20), (0.16, -0.28), (-0.38, -0.28), (-0.42, -0.22), (-0.42, 0.22), (-0.38, 0.28),
             (0.16, 0.28), (0.22, 0.20)]
    poly(d, house, SLOPE)
    roof = [(0.17, -0.16), (0.12, -0.22), (-0.34, -0.22), (-0.36, -0.18), (-0.36, 0.18), (-0.34, 0.22),
            (0.12, 0.22), (0.17, 0.16)]
    poly(d, roof, TOP)
    # Rear bustle: the ready-round magazine behind the roof.
    rect(d, -0.30, -0.40, -0.17, 0.17, UPPER)
    # Commander's and loader's hatches, a sight block by the commander's.
    circle(d, -0.13, -0.11, 0.075, UPPER)
    circle(d, -0.13, 0.11, 0.065, UPPER)
    rect(d, 0.04, -0.03, -0.17, -0.10, LOW)
    # Mantlet and barrel: one long tube in a thermal jacket, a muzzle brake at the tip 0.78 cells ahead of the pivot.
    rect(d, 0.27, 0.15, -0.085, 0.085, UPPER)
    rect(d, 0.66, 0.27, -0.050, 0.050, METAL)
    rect(d, 0.42, 0.30, -0.058, 0.058, LOW)
    rect(d, 0.78, 0.66, -0.068, 0.068, METAL)
    rect(d, 0.735, 0.705, -0.068, 0.068, BORE, width=3)
    return img


def open_mount():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Turntable: a ring plate 0.34 cells in radius with a raised rim.
    circle(d, 0.0, 0.0, 0.34, SLOPE)
    circle(d, 0.0, 0.0, 0.30, LOW, width=4)
    # Shell rack round the rear of the ring: two curved rows of round bases.
    for row, radius in ((0, 0.245), (1, 0.185)):
        for i in range(7 - row):
            import math
            a = math.radians(-60 + (120 / (6 - row)) * i)
            circle(d, -radius * math.cos(a), radius * math.sin(a), 0.032, UPPER, width=3)
    # Breech housing and the two recoil cylinders either side of the tube.
    rect(d, 0.05, -0.12, -0.13, 0.13, UPPER)
    rect(d, 0.22, -0.05, -0.15, -0.10, METAL)
    rect(d, 0.22, -0.05, 0.10, 0.15, METAL)
    # The tube stands steeply up, so it reads short and fat; its bore faces up and forward toward the eye.
    poly(d, [(0.36, -0.085), (0.0, -0.075), (0.0, 0.075), (0.36, 0.085)], TOP)
    circle(d, 0.36, 0.0, 0.088, METAL)
    circle(d, 0.36, 0.0, 0.062, BORE, width=3)
    # A low splinter shield across the front of the ring.
    poly(d, [(0.30, -0.24), (0.33, -0.17), (0.33, -0.11), (0.26, -0.11), (0.25, -0.22)], UPPER)
    poly(d, [(0.30, 0.24), (0.33, 0.17), (0.33, 0.11), (0.26, 0.11), (0.25, 0.22)], UPPER)
    return img


def main():
    for name, img in (("enclosed-guide.png", enclosed()), ("open-guide.png", open_mount())):
        img.save(HERE / name)
        print(f"wrote {HERE / name} {img.size}")


if __name__ == "__main__":
    main()
