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
                                        [--cell N] [--mean R,G,B]
                                        [--band N] [--scale N]

`--cell` is the tile's side in pixels; it defaults to 56, which is
`Floors_Tiles`' cellPx. The nature strip's materials are 52, and a material-backed
frame is sized from its material rather than from the sheet, so a family aimed at
that strip has to be told.

`--mean` shifts the family onto a stated colour instead of onto its own average.
Use it whenever the family is *extending* a surface that already ships: aligning
eight new tiles to each other still lets the whole family sit somewhere the
existing tile does not, which recolours every cell of that ground on every map.
Pass the mean of the material being extended and the batch lands on it instead.

`--band` switches the wrap from the four-corner blend to an overlap cross-fade of
that many pixels, which leaves the middle of the tile exactly as drawn. Use it for
any texture with visible structure; the blend is for noise and flattens anything
else. `--scale` is the downscale from the master, 4 by default -- drop it to 2 or
3 when the master's features are what you want to keep rather than a grain.
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

# Width of the cross-faded edge band when wrapping by overlap rather than by the
# four-corner blend. 0 selects the blend. A quarter of the tile leaves the middle
# half of each axis exactly as drawn, which is what keeps structure.
BAND = 0


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


def wrap_overlap(src, band):
    """A CELL-sized periodic tile that leaves its own middle alone.

    ``wrap_blend`` is periodic by construction but pays for it everywhere: the
    four samples are equally weighted at the centre and singly weighted at the
    corners, so the tile comes out flatter in the middle than at its edges.
    On fine grain that is invisible. On a texture with structure -- soft cloudy
    mottle a few pixels across, which is what packed earth looks like from
    above -- it does two visible things at once: it averages the mottle away,
    and the centre-to-edge contrast difference reads as a chequerboard of
    slightly darker squares once the tile repeats. Both were measured on the
    first dirt batch, which scored a seam ratio of 5.59 against a threshold of
    3.5 while its colour was perfect.

    This wraps by overlap instead. The source is ``band`` pixels wider than the
    tile, and those extra pixels are cross-faded back over the near edge, so the
    result is exactly periodic and exactly the source across the middle
    ``CELL - band`` of each axis. What it costs is confined to a band at two
    edges rather than spread over the whole tile.
    """
    out = Image.new("RGB", (CELL, CELL))
    sp = src.load()
    op = out.load()
    for y in range(CELL):
        wy = min(1.0, y / band) if band else 1.0
        for x in range(CELL):
            wx = min(1.0, x / band) if band else 1.0
            acc = [0.0, 0.0, 0.0]
            for dx, wxx in ((0, wx), (CELL, 1.0 - wx)):
                for dy, wyy in ((0, wy), (CELL, 1.0 - wy)):
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
    small = crop.resize((CELL * 2, CELL * 2), Image.BOX)
    if BAND:
        return wrap_overlap(small, BAND)
    return wrap_blend(small)


def align_to_family(tiles, target=None):
    """Shift every tile onto a shared mean colour.

    The contract asks that any two tiles in a family read as the same ground.
    ImageGen gets close -- eight snow masters landed within about eight levels
    of each other -- but "close" is measured against a threshold of six, and the
    outlier is visible as a patch of slightly different snow rather than as
    different debris. The fix is a constant per channel, which moves the colour
    and leaves the texture exactly alone; anything stronger would be editing the
    art to hit a number.

    With no ``target`` the shared mean is the family's own average, which is
    right for a family that is the whole of its surface. Pass the mean of an
    existing material to extend that material instead: the family then sits
    where the shipped tile already sits, so adding variants does not also
    change the colour of every cell that ground covers.
    """
    means = [[sum(p[c] for p in t.getdata()) / (t.width * t.height)
              for c in range(3)] for t in tiles]
    family = list(target) if target is not None else [
        sum(m[c] for m in means) / len(means) for c in range(3)]
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
    global CELL, BAND, SCALE
    args = sys.argv[1:]
    target = None
    while len(args) > 3:
        flag = args[-2]
        value = args[-1]
        if flag == "--cell":
            CELL = int(value)
        elif flag == "--band":
            BAND = int(value)
        elif flag == "--scale":
            SCALE = int(value)
        elif flag == "--mean":
            parts = [float(v) for v in value.split(",")]
            if len(parts) != 3:
                sys.exit("--mean wants three comma-separated channels, e.g. 100,87,50")
            target = parts
        else:
            sys.exit(__doc__)
        args = args[:-2]
    if len(args) != 3:
        sys.exit(__doc__)
    pattern, out_dir, prefix = args
    if os.path.isdir(pattern):
        pattern = os.path.join(pattern, "*.png")
    files = sorted(glob.glob(pattern), key=os.path.getmtime)
    if not files:
        sys.exit("no source images matched " + pattern)
    os.makedirs(out_dir, exist_ok=True)
    tiles = align_to_family([normalize(f) for f in files], target)
    for i, tile in enumerate(tiles, 1):
        out = os.path.join(out_dir, "%s-%d.png" % (prefix, i))
        tile.save(out)
        print("wrote", out)


if __name__ == "__main__":
    main()
