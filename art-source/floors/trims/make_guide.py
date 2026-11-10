"""Geometry guides for the built floors' step-up trims (``ruins-theme-halls``: the core hall's raised floor and the
stage hall's stage, the ``ruins-ground`` surfaces ``raised-floor`` and ``stage-floor``).

Each trim lies inside the raised or stage cell along an edge where it steps down to the hall floor, drawn for the south
edge (the renderer turns it to face each side). A guide is 1024x512, one cell along the edge by half a cell across,
and repeats left to right with a period of one cell. Its top rows are transparent, so the panel's own surface shows
through; the bottom row is the step's edge. The panel joints fall where the floor's do: the raised floor's half-cell
panels at 0 and 512, the stage's cell panels at 0 (cut through at both short ends). Run from the repository root:

    python art-source/floors/trims/make_guide.py
"""
from PIL import Image, ImageDraw

OUT = "art-source/floors/trims/"
TOP = 224
DARK = (34, 36, 40, 255)


def raised():
    im = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, TOP, 1023, TOP + 12), fill=DARK)                      # the gasket against the panel
    d.rectangle((0, TOP + 12, 1023, 440), fill=(170, 174, 178, 255))      # the aluminium edge trim
    d.rectangle((0, 300, 1023, 380), fill=(140, 144, 150, 255))           # its ribbed tread band
    for y in range(306, 380, 14):
        d.rectangle((0, y, 1023, y + 4), fill=(110, 114, 120, 255))
    for x in (128, 384, 640, 896):                                         # countersunk screws
        d.ellipse((x - 12, 254, x + 12, 278), fill=DARK)
    d.rectangle((0, 440, 1023, 511), fill=(96, 100, 106, 255))            # the bullnose turning down
    for x in (0, 512, 1024):                                               # the panel joints
        d.rectangle((x - 5, TOP, x + 4, 511), fill=DARK)
    return im


def stage():
    im = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, TOP, 1023, TOP + 10), fill=DARK)                      # the joint against the deck
    d.rectangle((0, TOP + 10, 1023, 340), fill=(52, 56, 64, 255))         # the edge-light channel
    for k in range(4):                                                     # its frosted lens segments, unlit
        x = k * 256
        d.rectangle((x + 24, 262, x + 232, 304), fill=(110, 124, 134, 255))
        d.ellipse((x + 116, 270, x + 140, 296), fill=(150, 168, 178, 255))
    d.rectangle((0, 340, 1023, 350), fill=DARK)
    d.rectangle((0, 350, 1023, 511), fill=(26, 28, 32, 255))              # the black chamfered nosing
    d.rectangle((0, 450, 1023, 474), fill=(150, 120, 52, 255))            # a worn amber edge stripe
    for x in (0, 1024):                                                    # the cell panel joint
        d.rectangle((x - 5, TOP, x + 4, 511), fill=DARK)
    return im


if __name__ == "__main__":
    raised().save(OUT + "raised-floor-trim-guide.png")
    stage().save(OUT + "stage-floor-trim-guide.png")
    print("guides written")
