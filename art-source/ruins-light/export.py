"""Cuts the ruins' lamps (``ruins-light``) from the ImageGen sheet and writes each fixture's unlit and lit states to
``mod/graphics/battle/lights/ruins-<id>-{unlit,lit}.png``, one 128 px cell each, centred, long axis east-west. Run
from the worktree root: ``python art-source/ruins-light/export.py``.

The unlit state is the drawing. The lit state is the same pixels with the lens fired: a lens pixel that matches the
lens glass (not the cage wires across it) is pushed to the lamp's colour, hotter toward the middle, so the housing and
cage are identical in both states and a lamp can switch on in place. No halo: the pool of light is the game's.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
import make_guide  # noqa: E402

OUT_PX = 128
OUT = ROOT / "mod" / "graphics" / "battle" / "lights"


def blobs(raw):
    """The raw's drawn pieces, left to right, as alpha bounding boxes (split at fully transparent columns)."""
    a = raw.getchannel("A").point(lambda v: 255 if v > 24 else 0)
    cols = [a.crop((x, 0, x + 1, a.height)).getbbox() is not None for x in range(a.width)]
    out, start = [], None
    for x, on in enumerate(cols + [False]):
        if on and start is None:
            start = x
        elif not on and start is not None:
            box = a.crop((start, 0, x, a.height)).getbbox()
            out.append((start, box[1], x, box[3]))
            start = None
    return out


def guide_boxes(guide):
    return [guide.crop((x, y, x + make_guide.CELL, y + make_guide.CELL)).getbbox() for _, x, y in make_guide.frames()]


def lens(cell, colour, shape):
    """The lens as drawn, a clean L mask of the guide's shape: where the guide put it for the white lamps; for the red
    ones fitted to the red glass ImageGen drew, which it drew larger than the guide's on the beacon."""
    mask = make_guide.lens_mask(shape, OUT_PX)
    if colour[1] > 120:
        return mask.filter(ImageFilter.MinFilter(3))
    grown, px = mask.filter(ImageFilter.MaxFilter(27)).load(), cell.load()
    red = [(x, y) for y in range(OUT_PX) for x in range(OUT_PX)
           if grown[x, y] and px[x, y][3] > 200 and px[x, y][0] > 1.35 * max(px[x, y][1:3]) and px[x, y][0] > 60]
    xs, ys = sorted(x for x, _ in red), sorted(y for _, y in red)
    q = len(red) // 50  # trim strays
    box = (xs[q], ys[q], xs[-1 - q], ys[-1 - q])
    out = Image.new("L", cell.size, 0)
    d = ImageDraw.Draw(out)
    (d.ellipse if shape[0] == "round" else d.rectangle)(box, fill=255)
    return out.filter(ImageFilter.MinFilter(3))


def fire(cell, colour, shape):
    """The lit state: lens pixels pushed to the lamp's colour, hotter toward the lens middle. The cage wires and
    rivets across the lens stay as drawn: they stand out from a median of the glass around them, the glass does not."""
    m = lens(cell, colour, shape).filter(ImageFilter.GaussianBlur(0.7))
    mp, px = m.load(), cell.load()
    med = cell.filter(ImageFilter.MedianFilter(9)).load()
    pts = [(x, y) for y in range(OUT_PX) for x in range(OUT_PX) if mp[x, y] > 128]
    cx = sum(x for x, _ in pts) / len(pts)
    cy = sum(y for _, y in pts) / len(pts)
    rx = max(abs(x - cx) for x, _ in pts) + 1
    ry = max(abs(y - cy) for _, y in pts) + 1
    lit = cell.copy()
    lp = lit.load()
    for y in range(OUT_PX):
        for x in range(OUT_PX):
            if mp[x, y] < 4:
                continue
            r, g, b, a = px[x, y]
            mr, mg, mb, _ = med[x, y]
            diff = abs((r + g + b) - (mr + mg + mb)) / 3 * 0.55 + abs((r - b) - (mr - mb))
            glass = max(0.0, min(1.0, (34 - diff) / 16)) * mp[x, y] / 255
            if glass <= 0:
                continue
            core = max(0.0, 1 - math.hypot((x - cx) / rx, (y - cy) / ry))
            hot = [min(255, round(v * 0.9 + (255 - v) * 0.6 * core)) for v in colour]
            lp[x, y] = tuple(round(o + (h - o) * glass) for o, h in zip((r, g, b), hot)) + (a,)
    return lit


def main():
    raw = Image.open(HERE / "raw" / "ruins-light.raw.png").convert("RGBA")
    raw.putalpha(raw.getchannel("A").point(lambda v: 0 if v < 8 else v))
    guide = Image.open(ROOT / "art-source" / "templates" / "ruins-light-guide.png").convert("RGBA")
    found, wanted = blobs(raw), guide_boxes(guide)
    assert len(found) == len(make_guide.FIXTURES), found
    OUT.mkdir(parents=True, exist_ok=True)
    for (pid, colour, shape), rb, gb in zip(make_guide.FIXTURES, found, wanted):
        # Each piece is centred in its frame; scale its cell by how wide ImageGen drew the piece against the guide.
        s = ((rb[2] - rb[0]) / (gb[2] - gb[0]) + (rb[3] - rb[1]) / (gb[3] - gb[1])) / 2
        half = make_guide.CELL * s / 2
        cx, cy = (rb[0] + rb[2]) / 2, (rb[1] + rb[3]) / 2
        cell = raw.crop((round(cx - half), round(cy - half), round(cx + half), round(cy + half)))
        cell = cell.resize((OUT_PX, OUT_PX), Image.LANCZOS)
        cell.save(OUT / f"ruins-{pid}-unlit.png")
        fire(cell, colour, shape).save(OUT / f"ruins-{pid}-lit.png")
        print(pid, f"scale {s:.2f}")


if __name__ == "__main__":
    main()
