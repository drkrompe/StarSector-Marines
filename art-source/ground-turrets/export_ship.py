"""Phase 2: cut the shipped brutalist ground-turret set into mod/graphics/structures/ground-turrets/.

Mechanical, from the retained raws and their guides (make_guide.py), at 128 px a cell:

  collar-octagon.png, collar-round.png         1x1 bases, 128x128
  <role>-head.png + <role>-barrel.png          rotating head and its recoil layer, 160x160 (visualCells 1.25),
                                               pivot at the centre, muzzle up; role = autogun, cannon, grenade, pd
  plinth.png                                   3x3 raised plinth, 384x384, cast shadow drawn in code
  heavy-head.png + heavy-barrel.png            384x384 (visualCells 3.0)

The barrel layer is everything above the row where the head's body begins, extended a further eighth of the sprite
down under the body (copied from its lowest visible row), so a barrel pulled back by recoil (a tenth of the sprite
height in `BattleRenderer`) never shows a gap at the mantlet. Drawn first, under the head, it composes back to the
painted piece exactly at rest.

Muzzles are measured off the barrel layer: one per separate tube, at its tip, in cells from the pivot
([lateral, forward], lateral positive to the right of the bearing), and written to muzzles.json beside this file.

Usage: python art-source/ground-turrets/export_ship.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from export import contact_shadow, masked, plinth_with_shadow  # noqa: E402

ROOT = HERE.parent.parent
OUT = ROOT / "mod" / "graphics" / "structures" / "ground-turrets"
PPC = 128
SMALL = 160          # 1.25 cells
SMALL_CELLS = 1.25
EXTEND = 0.125       # barrel continued under the body, as a fraction of the sprite


def split_row(img):
    """First row from the top whose opaque width passes half the widest row: where the body begins."""
    a = img.getchannel("A").load()
    w, h = img.size
    widths = [sum(1 for x in range(w) if a[x, y] > 128) for y in range(h)]
    limit = max(widths) * 0.5
    top = next(y for y in range(h) if widths[y] > 0)
    for y in range(top, h):
        if widths[y] > limit:
            return y
    raise ValueError("no body row")


def split(img):
    """(head, barrel) layers of one composed head, and the barrel's split row."""
    w, h = img.size
    row = split_row(img)
    src = img.load()
    head = img.copy()
    hp = head.load()
    barrel = Image.new("RGBA", img.size, (0, 0, 0, 0))
    bp = barrel.load()
    for y in range(row):
        for x in range(w):
            bp[x, y] = src[x, y]
            hp[x, y] = (0, 0, 0, 0)
    last = row - 1
    for y in range(row, min(h, row + int(round(EXTEND * h)))):
        for x in range(w):
            if src[x, last][3] > 0:
                bp[x, y] = src[x, last]
    return head, barrel, row


def muzzles(barrel, cells):
    """[lateral, forward] of each separate tube's tip, in cells from the pivot."""
    a = barrel.getchannel("A").load()
    w, h = barrel.size
    px = w / cells
    top = next(y for y in range(h) if any(a[x, y] > 128 for x in range(w)))
    probe = top + max(2, int(0.02 * h))
    runs, start = [], None
    for x in range(w + 1):
        on = x < w and a[x, probe] > 128
        if on and start is None:
            start = x
        elif not on and start is not None:
            runs.append((start, x - 1))
            start = None
    out = []
    for x0, x1 in runs:
        tip = next(y for y in range(h) if any(a[x, y] > 128 for x in range(x0, x1 + 1)))
        cx = (x0 + x1 + 1) / 2
        out.append([round((cx - w / 2) / px, 3), round((h / 2 - tip) / px, 3)])
    return out


def objects(img):
    """Bounding boxes of the sheet's objects, left to right: runs of columns holding any opaque pixel."""
    a = img.getchannel("A").point(lambda v: 255 if v > 32 else 0)
    w, h = img.size
    px = a.load()
    occupied = [any(px[x, y] for y in range(0, h, 2)) for x in range(w)]
    boxes, start = [], None
    for x in range(w + 1):
        on = x < w and occupied[x]
        if on and start is None:
            start = x
        elif not on and start is not None:
            boxes.append(a.crop((start, 0, x, h)).getbbox())
            boxes[-1] = (start, boxes[-1][1], x, boxes[-1][3])
            start = None
    return boxes


def fit_object(raw, guide, index, frame):
    """Raw object `index` scaled uniformly (by width) onto guide object `index`, centred across and standing on its
    rear edge, returned as the guide's `frame` crop. Generations move and enlarge small pieces (objects here spill
    across the guide's frame lines), so pieces are found by separation, not by frame."""
    rb = objects(raw)[index]
    gb = objects(guide)[index]
    k = (gb[2] - gb[0]) / (rb[2] - rb[0])
    w, h = gb[2] - gb[0], max(1, round((rb[3] - rb[1]) * k))
    canvas = Image.new("RGBA", guide.size, (0, 0, 0, 0))
    canvas.alpha_composite(raw.crop(rb).resize((w, h), Image.LANCZOS), (gb[0], gb[3] - h))
    return canvas.crop(frame)


def head_layers(sheet, guide, box, size, shadow_frac, index):
    piece = fit_object(sheet, guide, index, box).resize((size, size), Image.LANCZOS)
    head, barrel, _ = split(piece)
    shaded = contact_shadow(size, shadow_frac)
    shaded.alpha_composite(head)
    return shaded, barrel


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    cx = [18 + 500 * i + 250 for i in range(3)]
    frame = lambda i: (cx[i] - 250, 262, cx[i] + 250, 762)  # noqa: E731
    collar_box = (cx[0] - 200, 312, cx[0] + 200, 712)
    measured = {}

    def heads(raw_name, guide_name, roles):
        guide = Image.open(HERE / "guides" / guide_name)
        sheet = masked(Image.open(HERE / "raw" / raw_name), guide)
        for index, (role, shadow) in roles.items():
            if role.startswith("collar"):
                fit_object(sheet, guide, index, collar_box).resize((PPC, PPC), Image.LANCZOS).save(OUT / f"{role}.png")
                continue
            head, barrel = head_layers(sheet, guide, frame(index), SMALL, shadow, index)
            head.save(OUT / f"{role}-head.png")
            barrel.save(OUT / f"{role}-barrel.png")
            measured[role] = {"visualCells": SMALL_CELLS, "muzzles": muzzles(barrel, SMALL_CELLS)}

    heads("brutalist-points.raw.png", "brutalist-points-guide.png",
          {0: ("collar-octagon", 0), 1: ("autogun", 0.22), 2: ("cannon", 0.24)})
    heads("brutalist-points2.raw.png", "brutalist-points2-guide.png",
          {0: ("collar-round", 0), 1: ("grenade", 0.22), 2: ("pd", 0.19)})

    guide = Image.open(HERE / "guides" / "brutalist-hardpoint-guide.png")
    sheet = masked(Image.open(HERE / "raw" / "brutalist-hardpoint.raw.png"), guide)
    plinth_with_shadow(fit_object(sheet, guide, 0, (0, 128, 768, 896)), 3 * PPC).save(OUT / "plinth.png")
    head, barrel = head_layers(sheet, guide, (768, 128, 1536, 896), 3 * PPC, 0.2, 1)
    head.save(OUT / "heavy-head.png")
    barrel.save(OUT / "heavy-barrel.png")
    measured["heavy"] = {"visualCells": 3.0, "muzzles": muzzles(barrel, 3.0)}

    (HERE / "muzzles.json").write_text(json.dumps(measured, indent=2) + "\n")
    print(json.dumps(measured, indent=2))


if __name__ == "__main__":
    main()
