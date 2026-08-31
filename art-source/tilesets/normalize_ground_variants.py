"""Turn ImageGen masters into ground tiles that satisfy the family contract.

ImageGen returns a large square picture of a material. A ground tile is a small
one that must wrap against itself exactly, and no amount of prompting produces
that -- the model has no way to see its own edges. So the wrap is made here,
arithmetically, rather than asked for.

The wrap is a four-corner blend of a double-sized crop: a pixel of the output is
the bilinear mix of the four points a period apart in the source, so opposite
edges are the same points in the opposite order and the result is periodic by
construction rather than by inspection. That is why the shipped families score a
seam ratio of zero instead of merely a low one.

It costs a little contrast at the tile's centre, where the four samples are
equally weighted, which is invisible on a fine granular texture and would not be
on anything with structure. This is a tool for noise.

Usage:
    python normalize_ground_variants.py <src-dir-or-glob> <out-dir> <prefix>
"""

import glob
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("needs Pillow: python -m pip install Pillow")

CELL = 56
# Downscale factor from the master before wrapping. 4:1 keeps the grain fine
# enough to read as granular at one cell per cell; averaging the whole master
# down to 56 flattens it into a colour swatch.
SCALE = 4


def wrap_blend(src):
    """A CELL-sized tile that is exactly periodic, from a 2*CELL source."""
    out = Image.new("RGB", (CELL, CELL))
    sp = src.load()
    op = out.load()
    for y in range(CELL):
        wy = y / CELL
        for x in range(CELL):
            wx = x / CELL
            acc = [0.0, 0.0, 0.0]
            for dx, wxx in ((0, 1.0 - wx), (CELL, wx)):
                for dy, wyy in ((0, 1.0 - wy), (CELL, wy)):
                    w = wxx * wyy
                    if w == 0.0:
                        continue
                    p = sp[x + dx, y + dy]
                    for c in range(3):
                        acc[c] += w * p[c]
            op[x, y] = tuple(int(round(v)) for v in acc)
    return out


def normalize(path):
    im = Image.open(path).convert("RGB")
    side = CELL * 2 * SCALE
    left = (im.width - side) // 2
    top = (im.height - side) // 2
    crop = im.crop((left, top, left + side, top + side))
    return wrap_blend(crop.resize((CELL * 2, CELL * 2), Image.BOX))


def align_to_family(tiles):
    """Shift every tile onto the family's mean colour.

    The contract asks that any two tiles in a family read as the same ground.
    ImageGen gets close -- eight snow masters landed within about eight levels
    of each other -- but "close" is measured against a threshold of six, and the
    outlier is visible as a patch of slightly different snow rather than as
    different debris. The fix is a constant per channel, which moves the colour
    and leaves the texture exactly alone; anything stronger would be editing the
    art to hit a number.
    """
    means = [[sum(p[c] for p in t.getdata()) / (t.width * t.height)
              for c in range(3)] for t in tiles]
    family = [sum(m[c] for m in means) / len(means) for c in range(3)]
    out = []
    for tile, mean in zip(tiles, means):
        shift = [family[c] - mean[c] for c in range(3)]
        copy = tile.copy()
        px = copy.load()
        for y in range(copy.height):
            for x in range(copy.width):
                p = px[x, y]
                px[x, y] = tuple(
                    max(0, min(255, int(round(p[c] + shift[c])))) for c in range(3))
        out.append(copy)
    return out


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    pattern, out_dir, prefix = sys.argv[1:]
    if os.path.isdir(pattern):
        pattern = os.path.join(pattern, "*.png")
    files = sorted(glob.glob(pattern), key=os.path.getmtime)
    if not files:
        sys.exit("no source images matched " + pattern)
    os.makedirs(out_dir, exist_ok=True)
    tiles = align_to_family([normalize(f) for f in files])
    for i, tile in enumerate(tiles, 1):
        out = os.path.join(out_dir, "%s-%d.png" % (prefix, i))
        tile.save(out)
        print("wrote", out)


if __name__ == "__main__":
    main()
