"""The ruins' lamps (``ruins-light``): the block-out guide ImageGen reskins. Run from the worktree root:
``python art-source/ruins-light/make_guide.py``.

Each fixture sits in its own one-cell frame (``CELL`` px a cell), seen from above, long axis east-west. The housing is
grey, the lens a lighter block: ``export.py`` cuts each fixture unlit as drawn and derives its lit state by firing the
lens region, so the two states share every housing pixel.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
CELL, GUTTER = 256, 96
BODY, EDGE, LENS, CAGE = (128, 132, 138, 255), (70, 74, 80, 255), (176, 186, 196, 255), (60, 62, 66, 255)

# id, lens colour when lit (r, g, b), lens shape: ("strip", half-length, half-width) or ("round", radius) in cells.
FIXTURES = [
    ("ceiling-strip", (255, 244, 214), ("strip", 0.40, 0.09)),
    ("ceiling-highbay", (255, 236, 200), ("round", 0.24)),
    ("emergency-strip", (255, 52, 36), ("strip", 0.28, 0.06)),
    ("emergency-beacon", (255, 44, 30), ("round", 0.12)),
]


def frames():
    """(id, x, y) of each fixture's one-cell frame on the guide."""
    return [(pid, GUTTER + k * (CELL + GUTTER), GUTTER) for k, (pid, _, _) in enumerate(FIXTURES)]


def lens_mask(shape, size):
    """The lens region of a fixture in a ``size`` px cell, as an L mask."""
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    c = size / 2
    if shape[0] == "strip":
        hl, hw = shape[1] * size, shape[2] * size
        d.rectangle((c - hl, c - hw, c + hl, c + hw), fill=255)
    else:
        r = shape[1] * size
        d.ellipse((c - r, c - r, c + r, c + r), fill=255)
    return m


def draw():
    w = GUTTER + len(FIXTURES) * (CELL + GUTTER)
    im = Image.new("RGBA", (w, CELL + 2 * GUTTER), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for (pid, x, y), (_, _, shape) in zip(frames(), FIXTURES):
        c = (x + CELL / 2, y + CELL / 2)
        if shape[0] == "strip":
            hl, hw = shape[1] * CELL, shape[2] * CELL
            pad = 0.06 * CELL
            d.rectangle((c[0] - hl - pad, c[1] - hw - pad, c[0] + hl + pad, c[1] + hw + pad), fill=BODY, outline=EDGE,
                        width=5)
            for ex in (-1, 1):  # mounting brackets at each end
                bx = c[0] + ex * (hl + pad + 0.03 * CELL)
                d.rectangle((bx - 0.03 * CELL, c[1] - hw - 0.1 * CELL, bx + 0.03 * CELL, c[1] + hw + 0.1 * CELL),
                            fill=EDGE)
            d.rectangle((c[0] - hl, c[1] - hw, c[0] + hl, c[1] + hw), fill=LENS)
            if pid == "ceiling-strip":  # a guard cage over the lens
                for k in range(1, 8):
                    gx = c[0] - hl + k * 2 * hl / 8
                    d.line((gx, c[1] - hw - pad, gx, c[1] + hw + pad), fill=CAGE, width=4)
        else:
            r = shape[1] * CELL
            rim = 0.07 * CELL if pid == "ceiling-highbay" else 0.06 * CELL
            if pid == "emergency-beacon":  # a square mounting plate behind the dome
                pr = r + rim + 0.06 * CELL
                d.rectangle((c[0] - pr, c[1] - pr, c[0] + pr, c[1] + pr), fill=EDGE, outline=CAGE, width=6)
            d.ellipse((c[0] - r - rim, c[1] - r - rim, c[0] + r + rim, c[1] + r + rim), fill=BODY, outline=EDGE,
                      width=5)
            d.ellipse((c[0] - r, c[1] - r, c[0] + r, c[1] + r), fill=LENS)
            n = 6 if pid == "ceiling-highbay" else 4
            for k in range(n):  # cage wires across the lens
                a = math.pi * k / n
                dx, dy = math.cos(a) * (r + rim), math.sin(a) * (r + rim)
                d.line((c[0] - dx, c[1] - dy, c[0] + dx, c[1] + dy), fill=CAGE, width=4)
    return im


if __name__ == "__main__":
    out = HERE.parent / "templates" / "ruins-light-guide.png"
    draw().save(out)
    print(out)
