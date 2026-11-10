"""Geometry guide for the military base's doodads (`military-base` sheet): the pieces ImageGen paints, each in its own
frame at 160 px a cell on a white canvas. The parade square's painted markings are drawn by `export.py` itself.

Light is from the north-west, so shadows fall to the south-east, inside each frame.

Usage: python art-source/raid-sites/military/make_guide.py   (writes guide.png beside it)
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
CELL = 160
W, H = 1536, 768
# (id, x, y, cells w, cells h)
FRAMES = (
    ("watchtower", 96, 144, 3, 3),
    ("saluting-base", 672, 224, 3, 2),
    ("flag-mast", 1248, 304, 1, 1),
)
BODY, EDGE, DARK, LIGHT = (128, 132, 120), (70, 74, 66), (40, 42, 40), (176, 180, 168)
SAND = (186, 166, 120)
SHADOW = (150, 150, 150)


def watchtower(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.polygon([f(.3, .3), f(.98, .62), f(.98, .98), f(.62, .98), f(.3, .7)], fill=SHADOW)
    for cx, cy in ((.14, .14), (.86, .14), (.14, .86), (.86, .86)):
        d.rectangle((*f(cx - .04, cy - .04), *f(cx + .04, cy + .04)), fill=DARK)
    d.rectangle((*f(.1, .1), *f(.9, .9)), fill=BODY, outline=EDGE, width=6)
    for k in range(12):
        t = .1 + k * .8 / 12
        for box in ((t, .1, t + .8 / 12, .18), (t, .82, t + .8 / 12, .9), (.1, t, .18, t + .8 / 12),
                    (.82, t, .9, t + .8 / 12)):
            d.ellipse((*f(box[0], box[1]), *f(box[2], box[3])), fill=SAND, outline=EDGE, width=2)
    d.rectangle((*f(.32, .32), *f(.68, .68)), fill=LIGHT, outline=EDGE, width=6)
    d.line((*f(.32, .32), *f(.68, .68)), fill=EDGE, width=3)
    d.line((*f(.68, .32), *f(.32, .68)), fill=EDGE, width=3)
    d.ellipse((*f(.2, .2), *f(.3, .3)), fill=(220, 220, 200), outline=DARK, width=4)
    for k in range(5):
        yy = .9 + k * .02
        d.line((*f(.44, yy), *f(.56, yy)), fill=DARK, width=4)
    d.line((*f(.44, .9), *f(.44, 1.0)), fill=DARK, width=4)
    d.line((*f(.56, .9), *f(.56, 1.0)), fill=DARK, width=4)


def saluting_base(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.polygon([f(.12, .2), f(.97, .3), f(.97, .97), f(.12, .9)], fill=SHADOW)
    d.rectangle((*f(.06, .12), *f(.9, .8)), fill=LIGHT, outline=EDGE, width=6)
    for k in range(3):
        d.rectangle((*f(.3 + k * .02, .8 + k * .05), *f(.66 - k * .02, .85 + k * .05)), fill=BODY, outline=EDGE,
                    width=3)
    d.line((*f(.1, .18), *f(.86, .18)), fill=DARK, width=6)
    d.rectangle((*f(.44, .3), *f(.54, .42)), fill=BODY, outline=EDGE, width=4)


def flag_mast(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.line((*f(.3, .36), *f(.94, .94)), fill=SHADOW, width=6)
    d.polygon([f(.8, .78), f(.95, .8), f(.9, .92)], fill=SHADOW)
    d.ellipse((*f(.16, .22), *f(.44, .5)), fill=LIGHT, outline=EDGE, width=4)
    d.ellipse((*f(.26, .32), *f(.34, .4)), fill=DARK)
    d.polygon([f(.32, .3), f(.82, .4), f(.34, .5)], fill=(170, 150, 70), outline=EDGE)


DRAW = {"watchtower": watchtower, "saluting-base": saluting_base, "flag-mast": flag_mast}


def main():
    im = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(im)
    for pid, x, y, cw, ch in FRAMES:
        DRAW[pid](d, x, y, cw * CELL, ch * CELL)
    im.save(HERE / "guide.png")


if __name__ == "__main__":
    main()
