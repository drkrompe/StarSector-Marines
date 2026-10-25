"""Guide for the seven field-stone groups (nature.rock-small-1..2, -medium-1..2, -large-1..3).

One group per 512 px frame on a 4x2 sheet (2048x1024), in tileset order; the eighth frame stays empty. A strip
frame is stretched to fill its whole cell at runtime, cut at its alpha box, so each group spans a near-square box
over most of a 480 px cell: what changes between small and large is how much stone fills that box, not the box.
Simple top-lit volumes (light from the upper left) say where each stone is high. Outlines are smooth and slightly
irregular, so the reskin paints weathered rounded stones rather than facets.

Usage: python art-source/templates/field-stones-guide.py <out.png>
"""
import math
import random
import sys

from PIL import Image, ImageDraw

PITCH, CELL = 512, 480
# Stones as (cx, cy, rx, ry, angle degrees) in cell fractions, per group, following the groups' accepted identities.
GROUPS = [
    [(.16, .2, .13, .11, 20), (.83, .36, .12, .1, -30), (.46, .84, .14, .12, 10)],          # small-1: three wide pebbles
    [(.4, .45, .27, .23, 15), (.82, .2, .13, .11, 0), (.78, .82, .14, .12, 40)],            # small-2: three, one larger
    [(.46, .42, .38, .34, -10), (.2, .86, .13, .11, 20), (.82, .84, .14, .12, -15)],        # medium-1: boulder, two at foot
    [(.44, .42, .4, .27, 35), (.84, .86, .1, .09, 0)],                                      # medium-2: tilted boulder, pebble
    [(.3, .4, .3, .34, -10), (.7, .6, .3, .34, 15)],                                        # large-1: boulder pair
    [(.58, .62, .36, .3, 0), (.42, .42, .44, .22, -35)],                                    # large-2: slab leaning on a stone
    [(.56, .58, .4, .36, 10), (.3, .36, .26, .24, -20), (.24, .8, .16, .14, 30)],           # large-3: the biggest cluster
]


def blob(cx, cy, rx, ry, ang, phases, n=64):
    """A rounded outline: two slow waves round an ellipse, so it is weathered, never faceted."""
    pts = []
    t = math.radians(ang)
    for i in range(n):
        a = i * 2 * math.pi / n
        k = 1 - .05 * (1 + math.sin(2 * a + phases[0])) - .04 * (1 + math.sin(3 * a + phases[1]))
        x, y = math.cos(a) * rx * k, math.sin(a) * ry * k
        pts.append((cx + x * math.cos(t) - y * math.sin(t), cy + x * math.sin(t) + y * math.cos(t)))
    return pts


def main(out):
    img = Image.new("RGBA", (PITCH * 4, PITCH * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for slot, stones in enumerate(GROUPS):
        rnd = random.Random(slot)
        ox, oy = slot % 4 * PITCH + (PITCH - CELL) / 2, slot // 4 * PITCH + (PITCH - CELL) / 2
        for cx, cy, rx, ry, ang in stones:
            phases = (rnd.uniform(0, 2 * math.pi), rnd.uniform(0, 2 * math.pi))
            d.polygon([(ox + x * CELL, oy + y * CELL) for x, y in blob(cx, cy, rx, ry, ang, phases)],
                      fill=(104, 102, 96, 255))
            # The lit top: the same outline shrunk toward the upper left.
            top = blob(cx - rx * .12, cy - ry * .14, rx * .62, ry * .62, ang, phases)
            d.polygon([(ox + x * CELL, oy + y * CELL) for x, y in top], fill=(146, 143, 134, 255))
    img.save(out)


if __name__ == "__main__":
    main(sys.argv[1])
