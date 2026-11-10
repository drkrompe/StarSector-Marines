"""Geometry guides for the ruins vault-theme props (``ruins-theme-art.md`` brief, ``RuinsThemeDressing``).

One sheet per prop family at 320 px a cell: each prop is a flat grey block-out on its exact cell footprint, with
transparent gutters between frames, so ImageGen reskins the shapes and keeps their size and placement. Floor patches
are drawn as low irregular blots (the concept sheets' ``blot``; the production ``patches`` sheet's smoother ``organic``
spills) whose outline must stay inside their frame. Run from the repository root:

    python art-source/ruins-themes/make_guide.py

Writes ``art-source/templates/ruins-<family>-guide.png`` and prints each frame (x, y, w, h) in guide pixels.
"""
import math
import random

from PIL import Image, ImageDraw

CELL = 320
GUTTER = 64

# Each family: (id, cells w, cells h, kind) laid left to right, tops aligned; None starts a new row below.
FAMILIES = {
    "frost": [("cryo-rack", 1, 2, "box"), ("frost-heatsink", 2, 1, "fins"), ("frost-patch", 2, 2, "blot")],
    "vats": [("culture-vat", 2, 2, "round"), ("grow-bed", 2, 1, "trough"), ("growth-patch", 2, 2, "blot")],
    "patches": [("frost-patch-a", 2, 2, "organic"), ("frost-patch-b", 2, 2, "organic"),
                ("growth-patch-a", 2, 2, "organic"), ("growth-patch-b", 2, 2, "organic")],
    "forge": [("assembly-cradle", 2, 2, "cradle"), ("shaft-head", 3, 3, "shaft"), ("hoist-winch", 2, 1, "drum")],
    # The plant kit every theme's halls share (``ruins-vault-facility``): a line module that joins end to end along
    # its long axis, so its bed runs flush off both short ends with one cross-section; a line end carrying that same
    # cross-section in from its east edge; an empty 3x3 cradle (open middle: the product stands in it).
    "plant-line": [("line-module", 6, 3, "line"), ("line-end", 3, 3, "lineend"), ("empty-cradle", 3, 3, "ring")],
    # Rack rows, stripped and with a few crates left, and a dock door with its leveler, the door on the north edge.
    "plant-store": [("rack-stripped", 6, 2, "rack"), ("dock-door", 4, 2, "dock"), None,
                    ("rack-crates", 6, 2, "rackfull")],
}

# The line's bed: the middle half of its width, flush to the short ends, shared by the module and the line end.
BED = (0.3, 0.7)


def frames(family):
    x, y, row_h = GUTTER, GUTTER, 0
    out = []
    for entry in FAMILIES[family]:
        if entry is None:
            x, y, row_h = GUTTER, y + row_h + GUTTER, 0
            continue
        pid, cw, ch, kind = entry
        out.append((pid, x, y, cw * CELL, ch * CELL, kind))
        x += cw * CELL + GUTTER
        row_h = max(row_h, ch * CELL)
    return out


def draw(family):
    fr = frames(family)
    width = max(f[1] + f[3] for f in fr) + GUTTER
    height = max(f[2] + f[4] for f in fr) + GUTTER
    im = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    body, edge, dark, light = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)
    rng = random.Random(family)
    for pid, x, y, w, h, kind in fr:
        inset = 18
        if kind == "box":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), fill=body, outline=edge, width=8)
            d.rectangle((x + 60, y + 60, x + w - 60, y + h // 2 - 20), fill=light, outline=edge, width=4)
        elif kind == "fins":
            d.rectangle((x + inset, y + 90, x + w - inset, y + h - 90), fill=edge)
            for i in range(9):
                fx = x + 50 + i * (w - 100) // 8
                d.rectangle((fx - 14, y + inset, fx + 14, y + h - inset), fill=body, outline=edge, width=4)
        elif kind == "round":
            d.ellipse((x + inset, y + inset, x + w - inset, y + h - inset), fill=body, outline=edge, width=10)
            d.ellipse((x + 110, y + 110, x + w - 110, y + h - 110), fill=light, outline=edge, width=6)
        elif kind == "trough":
            d.rectangle((x + inset, y + inset + 30, x + w - inset, y + h - inset - 30), fill=body, outline=edge,
                        width=8)
            d.rectangle((x + 50, y + 80, x + w - 50, y + h - 80), fill=dark)
        elif kind == "cradle":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), outline=edge, width=26)
            d.rectangle((x + 120, y + 90, x + w - 120, y + h - 90), fill=body, outline=edge, width=6)
        elif kind == "shaft":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), fill=body, outline=edge, width=10)
            d.rectangle((x + 200, y + 200, x + w - 200, y + h - 200), fill=dark)
            d.line((x + inset, y + inset, x + w - inset, y + h - inset), fill=edge, width=30)
            d.line((x + w - inset, y + inset, x + inset, y + h - inset), fill=edge, width=30)
        elif kind == "drum":
            d.rectangle((x + inset, y + 60, x + w - inset, y + h - 60), fill=edge)
            d.rectangle((x + 140, y + inset + 20, x + w - 140, y + h - inset - 20), fill=light, outline=edge,
                        width=6)
        elif kind in ("line", "lineend"):
            b0, b1 = y + round(h * BED[0]), y + round(h * BED[1])
            bed_x0 = x if kind == "line" else x + 150
            d.rectangle((bed_x0, b0, x + w, b1), fill=body)
            d.rectangle((bed_x0, b0, x + w, b0 + 16), fill=edge)
            d.rectangle((bed_x0, b1 - 16, x + w, b1), fill=edge)
            # Rollers across the bed at a pitch that divides the module, so a seam falls between two rollers.
            for rx in range(bed_x0 + 20, x + w, 80):
                d.rectangle((rx, b0 + 24, rx + 36, b1 - 24), fill=light)
            if kind == "line":
                for sx in (x + 220, x + w - 620):
                    d.rectangle((sx, y + inset, sx + 400, b0 - 20), fill=body, outline=edge, width=8)
                    d.rectangle((sx + 100, b1 + 20, sx + 300, y + h - inset), fill=body, outline=edge, width=8)
            else:
                d.rectangle((x + inset, y + 120, x + 420, y + h - 120), fill=body, outline=edge, width=10)
                d.rectangle((x + 80, y + 200, x + 340, y + h - 200), fill=dark)
        elif kind == "ring":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), outline=edge, width=60)
            for cx, cy in ((x + 140, y + 140), (x + w - 140, y + 140), (x + 140, y + h - 140),
                           (x + w - 140, y + h - 140)):
                d.rectangle((cx - 50, cy - 50, cx + 50, cy + 50), fill=body, outline=edge, width=6)
        elif kind in ("rack", "rackfull"):
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), fill=dark, outline=edge, width=24)
            for i in range(1, 6):
                bx = x + i * w // 6
                d.rectangle((bx - 10, y + inset, bx + 10, y + h - inset), fill=edge)
            if kind == "rackfull":
                for bay, wide in ((0, 1), (2, 2), (5, 1)):
                    bx = x + bay * w // 6
                    d.rectangle((bx + 40, y + 70, bx + wide * w // 6 - 40, y + h - 70), fill=light, outline=edge,
                                width=6)
        elif kind == "dock":
            d.rectangle((x, y, x + w, y + 120), fill=edge)
            d.rectangle((x + 60, y + 20, x + w - 60, y + 110), fill=body)
            d.rectangle((x + 80, y + 130, x + w - 80, y + h - inset), fill=light, outline=edge, width=10)
        elif kind == "blot":
            cx, cy, r = x + w / 2, y + h / 2, min(w, h) / 2 - 50
            pts = []
            for i in range(28):
                a = i / 28 * 2 * math.pi
                rr = r * (0.65 + 0.35 * rng.random())
                pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
            d.polygon(pts, fill=(150, 154, 160, 150))
        elif kind == "organic":
            # A lopsided low-frequency outline: a few slow harmonics and an off-centre middle, so it reads as a
            # spill, never as a star or a circle.
            cx = x + w / 2 + rng.uniform(-30, 30)
            cy = y + h / 2 + rng.uniform(-30, 30)
            r = min(w, h) / 2 - 70
            harm = [(k, rng.uniform(0.06, 0.16) / k ** 0.5, rng.uniform(0, 2 * math.pi)) for k in (2, 3, 5)]
            stretch, tilt = rng.uniform(0.75, 0.95), rng.uniform(0, math.pi)
            pts = []
            for i in range(180):
                a = i / 180 * 2 * math.pi
                rr = r * (1 + sum(amp * math.sin(k * a + ph) for k, amp, ph in harm))
                px, py = rr * math.cos(a), rr * stretch * math.sin(a)
                pts.append((cx + px * math.cos(tilt) - py * math.sin(tilt),
                            cy + px * math.sin(tilt) + py * math.cos(tilt)))
            d.polygon(pts, fill=(150, 154, 160, 140))
    return im, fr


if __name__ == "__main__":
    for fam in FAMILIES:
        im, fr = draw(fam)
        path = f"art-source/templates/ruins-{fam}-guide.png"
        im.save(path)
        print(path, im.size)
        for pid, x, y, w, h, _ in fr:
            print(f"  {pid}: {x} {y} {w} {h}")
