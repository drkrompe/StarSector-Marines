"""The ruins stores' block-out guide (``stores_list.py``). Run from the worktree root:
``python art-source/ruins-stores/make_guide.py``."""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import stores_list  # noqa: E402

CELL, GUTTER = 160, 64
BODY, EDGE, DARK, LIGHT = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)
PALLETED = ("store-metals", "store-supplies", "store-organics", "store-domestic-goods", "store-pallet")


def frames():
    """(id, x, y, w, h) of each piece's frame on the guide, in px."""
    x, y, row_h, out = GUTTER, GUTTER, 0, []
    for e in stores_list.LOADS:
        if e is None:
            x, y, row_h = GUTTER, y + row_h + GUTTER, 0
            continue
        pid, w, h = e[:3]
        out.append((pid, x, y, w * CELL, h * CELL))
        x += w * CELL + GUTTER
        row_h = max(row_h, h * CELL)
    return out


def pallet(d, x, y, w, h, i=10):
    d.rectangle((x + i, y + i, x + w - i, y + h - i), fill=EDGE)
    for k in range(5):
        sx = x + i + k * (w - 2 * i) / 4.6
        d.rectangle((sx, y + i, sx + (w - 2 * i) / 9, y + h - i), fill=BODY)


def grid_boxes(d, x, y, w, h, margin, gap, band=False):
    for r in range(2):
        for c in range(2):
            bx, by = x + margin + c * (w - 2 * margin) / 2, y + margin + r * (h - 2 * margin) / 2
            bw, bh = (w - 2 * margin) / 2 - gap, (h - 2 * margin) / 2 - gap
            d.rectangle((bx, by, bx + bw, by + bh), fill=LIGHT, outline=EDGE, width=4)
            if band:
                d.rectangle((bx, by + bh * 0.4, bx + bw, by + bh * 0.6), fill=BODY)
            else:
                for k in range(1, 4):
                    d.line((bx, by + k * bh / 4, bx + bw, by + k * bh / 4), fill=EDGE, width=3)
                d.rectangle((bx + bw * 0.45, by, bx + bw * 0.55, by + bh), fill=DARK)


def draw_piece(d, pid, x, y, w, h):
    rng = random.Random(pid)
    i = 14
    if pid in PALLETED:
        pallet(d, x, y, w, h)
    if pid == "store-metals":
        grid_boxes(d, x, y, w, h, 30, 12)
    elif pid == "store-supplies":
        grid_boxes(d, x, y, w, h, 26, 10, band=True)
    elif pid in ("store-transplutonics", "store-volatiles"):
        d.rectangle((x + i, y + i, x + w - i, y + h - i), fill=EDGE, outline=DARK, width=8)
        n, rad = (3, 0.13) if pid == "store-transplutonics" else (2, 0.2)
        for r in range(n):
            for c in range(n):
                cx, cy = x + (c + 0.5) * w / n, y + (r + 0.5) * h / n
                d.ellipse((cx - rad * w, cy - rad * h, cx + rad * w, cy + rad * h), fill=LIGHT, outline=DARK, width=4)
                d.ellipse((cx - 0.05 * w, cy - 0.05 * h, cx + 0.05 * w, cy + 0.05 * h), fill=BODY)
    elif pid == "store-organics":
        for cx, cy in ((0.3, 0.3), (0.7, 0.3), (0.3, 0.7), (0.7, 0.7), (0.5, 0.5)):
            px, py, r = x + cx * w, y + cy * h, 0.17 * w
            d.ellipse((px - r, py - r, px + r, py + r), fill=BODY, outline=DARK, width=5)
            d.ellipse((px - r * 0.4, py - r * 0.4, px + r * 0.4, py + r * 0.4), fill=LIGHT)
    elif pid == "store-heavy-machinery":
        d.rectangle((x + i, y + i, x + w - i, y + h - i), fill=EDGE)
        d.rectangle((x + 40, y + 40, x + w - 40, y + h - 40), outline=DARK, width=14)
        d.polygon([(x + 90, y + 100), (x + w - 120, y + 90), (x + w - 90, y + h / 2), (x + w - 130, y + h - 100),
                   (x + 100, y + h - 90), (x + 80, y + h / 2)], fill=LIGHT, outline=DARK)
        d.ellipse((x + w * 0.55, y + h * 0.5, x + w * 0.75, y + h * 0.7), fill=BODY, outline=DARK, width=4)
        d.rectangle((x + w * 0.25, y + h * 0.3, x + w * 0.45, y + h * 0.45), fill=BODY)
    elif pid in ("store-ore", "store-transplutonic-ore"):
        d.rectangle((x + i, y + i, x + w - i, y + h - i), fill=EDGE, outline=DARK, width=10)
        for _ in range(60):
            px, py = x + rng.uniform(0.2, 0.8) * w, y + rng.uniform(0.2, 0.8) * h
            r = rng.uniform(8, 18)
            d.ellipse((px - r, py - r, px + r, py + r), fill=LIGHT if pid.endswith("transplutonic-ore") else BODY)
    elif pid == "store-fuel":
        for k in range(1, 10):  # the hazard bay's striped kerb along the front
            d.line((x + k * w / 10 - 20, y + h - 6, x + k * w / 10 + 20, y + h - 46), fill=LIGHT, width=10)
        d.rectangle((x + i, y + i, x + w - i, y + h - 48), fill=EDGE)
        for c in range(2):
            cx = x + (c + 0.5) * w / 2
            d.rounded_rectangle((cx - 0.17 * w, y + 26, cx + 0.17 * w, y + h - 60), radius=0.17 * w, fill=LIGHT,
                                outline=DARK, width=5)
            d.ellipse((cx - 14, y + 50, cx + 14, y + 78), fill=BODY)
    elif pid == "store-domestic-goods":
        for bx0, by0, bx1, by1 in ((0.12, 0.12, 0.5, 0.45), (0.55, 0.12, 0.88, 0.38), (0.12, 0.5, 0.38, 0.88),
                                   (0.42, 0.42, 0.88, 0.7), (0.45, 0.74, 0.7, 0.88), (0.74, 0.74, 0.88, 0.88)):
            d.rectangle((x + bx0 * w, y + by0 * h, x + bx1 * w, y + by1 * h), fill=rng.choice((LIGHT, BODY)),
                        outline=DARK, width=4)
    elif pid in ("store-rack-bay", "store-rack-bay-stripped"):
        d.rectangle((x + i, y + i, x + w - i, y + h - i), fill=BODY, outline=DARK, width=14)  # mesh decks
        d.line((x + i, y + h / 2, x + w - i, y + h / 2), fill=DARK, width=14)
        for k in range(1, 8):
            if k != 4:
                d.line((x + i + 10, y + k * h / 8, x + w - i - 10, y + k * h / 8), fill=EDGE, width=5)
        if pid.endswith("stripped"):
            d.rectangle((x + 50, y + 60, x + 150, y + 150), fill=LIGHT, outline=DARK, width=4)
            d.polygon([(x + 180, y + h - 160), (x + 260, y + h - 190), (x + 280, y + h - 110), (x + 200, y + h - 90)],
                      fill=BODY, outline=DARK)


def draw():
    fr = frames()
    width = max(x + w for _, x, _, w, _ in fr) + GUTTER
    height = max(y + h for _, _, y, _, h in fr) + GUTTER
    im = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for pid, x, y, w, h in fr:
        draw_piece(d, pid, x, y, w, h)
    return im


if __name__ == "__main__":
    out = HERE.parent / "templates" / "ruins-stores-guide.png"
    draw().save(out)
    print(out)
