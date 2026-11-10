"""Block-out guides for the foundry drop's textures (``ruins-forge-area``, the ``FOUNDRY`` void style).

Both repeat exactly at their own width (and the wall at its height too), so ImageGen reskins a pattern whose healed
seams in ``derive.py`` meet brick to brick. Run from the repository root:

    python art-source/voids/foundry/make_guide.py

- ``firebrick-wall-guide.png``, 1024 square: the drop's wall seen straight on, 16 courses of running-bond firebrick,
  8 bricks a course, so a course meets itself across every edge.
- ``foundry-lip-guide.png``, 1024x512: one cell of the drop's lip, the hall floor along the top, the edge where the
  wall drops away along the bottom. A cast-steel kerb plate with its bolts, over two refractory blocks, over a
  chamfered steel nosing; both short ends cut through the middle of a joint so it repeats left to right.
"""
from PIL import Image, ImageDraw

OUT = "art-source/voids/foundry/"
GREY, MORTAR, STEEL, DARK = (150, 150, 150, 255), (90, 90, 90, 255), (120, 124, 130, 255), (60, 62, 66, 255)


def wall():
    im = Image.new("RGBA", (1024, 1024), MORTAR)
    d = ImageDraw.Draw(im)
    course, brick, joint = 64, 128, 6
    for row in range(16):
        y = row * course
        shift = brick // 2 if row % 2 else 0
        for k in range(-1, 9):
            x = k * brick + shift
            d.rectangle((x + joint // 2, y + joint // 2, x + brick - joint // 2 - 1, y + course - joint // 2 - 1),
                        fill=GREY)
    return im


def lip():
    im = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 1023, 180), fill=STEEL)
    for bx in (128, 384, 640, 896):
        d.ellipse((bx - 18, 72, bx + 18, 108), fill=DARK)
    d.rectangle((0, 180, 1023, 190), fill=DARK)
    for k in range(3):
        x = -256 + k * 512
        d.rectangle((x + 6, 196, x + 506, 400), fill=GREY)
    d.rectangle((0, 400, 1023, 410), fill=DARK)
    d.rectangle((0, 410, 1023, 500), fill=STEEL)
    return im


if __name__ == "__main__":
    wall().save(OUT + "firebrick-wall-guide.png")
    lip().save(OUT + "foundry-lip-guide.png")
    print("guides written")
