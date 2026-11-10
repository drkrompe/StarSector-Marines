"""Geometry guide for the bore shaft's collar lip (``ruins-theme-halls``, the shaft hall's ``SHAFT`` drop).

The collar is the engineered edge round the mantle bore's shaft, laid along every edge where the hall floor meets the
drop as the foundry's kerb is (``art-source/voids/foundry``). The strip is 1024x512 and repeats left to right with a
period of 512, so both short ends cut a collar segment through its middle; the top meets the floor, the bottom row is
the drop's edge. Run from the repository root:

    python art-source/voids/shaft/make_guide.py
"""
from PIL import Image, ImageDraw

OUT = "art-source/voids/shaft/"
STEEL, GREY, DARK, LIGHT = (128, 132, 138, 255), (150, 150, 146, 255), (40, 42, 46, 255), (176, 180, 186, 255)


def lip():
    im = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # The kerb plate meeting the floor, its bolts on a quarter period.
    d.rectangle((0, 0, 1023, 130), fill=STEEL)
    for bx in (128, 384, 640, 896):
        d.ellipse((bx - 16, 49, bx + 16, 81), fill=DARK)
    d.rectangle((0, 130, 1023, 140), fill=DARK)
    # The cast collar ring in segments a half period long, joints at the period's quarters.
    for k in range(5):
        x = -128 + k * 256
        d.rectangle((x + 6, 146, x + 250, 380), fill=GREY)
        for ax in (x + 64, x + 192):
            d.rectangle((ax - 20, 240, ax + 20, 286), fill=LIGHT)
    d.rectangle((0, 380, 1023, 390), fill=DARK)
    # The steel nosing over the drop.
    d.rectangle((0, 390, 1023, 500), fill=STEEL)
    return im


if __name__ == "__main__":
    lip().save(OUT + "shaft-collar-lip-guide.png")
    print("guide written")
