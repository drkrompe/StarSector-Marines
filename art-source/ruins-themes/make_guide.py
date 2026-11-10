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
    # Each theme's product (``ruins-vault-facility``), blocked out on a 2x2 frame and shipped on a 3x3 footprint, the
    # frame's margin keeping it just inside the empty cradle's ring and across a line module's bed, in three states left to right: whole (the one sealed unit in the secure store),
    # half-built (on the line) and wrecked (on the line and the floor). One row per product, two products a sheet.
    "products-a": [(f"cryo-unit-{s}", 2, 2, f"cryo:{s}") for s in ("whole", "half", "wrecked")] + [None]
                  + [(f"vat-unit-{s}", 2, 2, f"vat:{s}") for s in ("whole", "half", "wrecked")],
    "products-b": [(f"drone-frame-{s}", 2, 2, f"drone:{s}") for s in ("whole", "half", "wrecked")] + [None]
                  + [(f"forge-core-{s}", 2, 2, f"forge:{s}") for s in ("whole", "half", "wrecked")],
}
# The other themes' products, the same way: one row per product.
for _fam, _prods in (("products-c", (("core-cabinet", "core"), ("field-housing", "lamp"), ("magnet-segment", "magnet"))),
                     ("products-d", (("catalyst-bed", "catalyst"), ("spool-drum", "spool"))),
                     ("products-e", (("bore-head", "bore"), ("projector-stack", "projector")))):
    FAMILIES[_fam] = []
    for _pid, _shape in _prods:
        if FAMILIES[_fam]:
            FAMILIES[_fam].append(None)
        FAMILIES[_fam] += [(f"{_pid}-{s}", 2, 2, f"{_shape}:{s}") for s in ("whole", "half", "wrecked")]

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


BODY, EDGE, DARK, LIGHT = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)


def product_shape(name, size, solid):
    """A product's block-out on its own transparent canvas; ``solid`` False leaves the outer shells as bare frames."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s, i = size, 30
    shell = BODY if solid else None
    if name == "cryo":
        d.rectangle((i, i + 40, s - i, s - i), fill=shell, outline=EDGE, width=12)
        d.rectangle((i + 40, i, s - i - 40, i + 70), fill=DARK)
        for px in (s // 3, 2 * s // 3):
            d.rectangle((px - 26, i - 10, px + 26, i + 40), fill=EDGE)
        for k in range(4):
            y0 = i + 140 + k * 105
            d.rectangle((i + 60, y0, s - i - 60, y0 + 60), fill=LIGHT, outline=EDGE, width=5)
    elif name == "vat":
        for cx, cy in ((i + 40, i + 40), (s - i - 40, i + 40), (i + 40, s - i - 40), (s - i - 40, s - i - 40)):
            d.rectangle((cx - 40, cy - 40, cx + 40, cy + 40), fill=EDGE)
        d.ellipse((i + 30, i + 30, s - i - 30, s - i - 30), fill=shell, outline=EDGE, width=12)
        d.ellipse((s // 2 - 150, s // 2 - 150, s // 2 + 150, s // 2 + 150), fill=LIGHT, outline=EDGE, width=8)
        d.rectangle((s - i - 70, s // 2 - 30, s - i, s // 2 + 30), fill=DARK)
    elif name == "drone":
        c = s // 2
        for cx, cy in ((150, 150), (s - 150, 150), (150, s - 150), (s - 150, s - 150)):
            d.line((c, c, cx, cy), fill=EDGE, width=46)
            d.ellipse((cx - 115, cy - 115, cx + 115, cy + 115), outline=EDGE, width=18)
            d.ellipse((cx - 40, cy - 40, cx + 40, cy + 40), fill=shell or EDGE, outline=EDGE, width=6)
        d.rectangle((c - 110, c - 130, c + 110, c + 130), fill=shell, outline=EDGE, width=12)
        d.rectangle((c - 60, c - 70, c + 60, c + 10), fill=LIGHT)
    elif name == "forge":
        d.rectangle((i, i, s - i, s - i), fill=shell, outline=EDGE, width=14)
        c = s // 2
        for k in range(8):
            a = k / 8 * 2 * math.pi
            ex, ey = c + 210 * math.cos(a), c + 210 * math.sin(a)
            d.rectangle((ex - 42, ey - 42, ex + 42, ey + 42), fill=LIGHT, outline=EDGE, width=6)
        d.ellipse((c - 140, c - 140, c + 140, c + 140), fill=DARK, outline=EDGE, width=14)
        for cx, cy in ((i + 50, i + 50), (s - i - 50, i + 50), (i + 50, s - i - 50), (s - i - 50, s - i - 50)):
            d.ellipse((cx - 20, cy - 20, cx + 20, cy + 20), fill=EDGE)
    elif name == "core":
        d.rectangle((i, i, s - i, s - i), fill=shell, outline=EDGE, width=14)
        for y0 in range(i + 60, s - i - 60, 70):
            d.rectangle((i + 40, y0, i + 110, y0 + 40), fill=DARK)
            d.rectangle((s - i - 110, y0, s - i - 40, y0 + 40), fill=DARK)
        c = s // 2
        d.ellipse((c - 150, c - 150, c + 150, c + 150), fill=EDGE)
        d.ellipse((c - 105, c - 105, c + 105, c + 105), fill=LIGHT, outline=EDGE, width=8)
    elif name == "lamp":
        c, r = s // 2, s // 2 - i
        d.polygon([(c + r * math.cos(a), c + r * math.sin(a)) for a in (k * math.pi / 4 + math.pi / 8 for k in range(8))],
                  fill=shell, outline=EDGE, width=14)
        for k in range(4):
            a = k * math.pi / 2 + math.pi / 4
            d.line((c + 120 * math.cos(a), c + 120 * math.sin(a), c + (r - 30) * math.cos(a), c + (r - 30) * math.sin(a)),
                   fill=EDGE, width=40)
        d.ellipse((c - 130, c - 130, c + 130, c + 130), fill=LIGHT, outline=EDGE, width=14)
        d.ellipse((c - 55, c - 55, c + 55, c + 55), fill=DARK)
    elif name == "magnet":
        cx, cy, ro, ri = -s * 0.25, s * 0.5, s * 1.1, s * 0.62
        d.pieslice((cx - ro, cy - ro, cx + ro, cy + ro), -24, 24, fill=shell, outline=EDGE, width=14)
        d.pieslice((cx - ri, cy - ri, cx + ri, cy + ri), -40, 40, fill=(0, 0, 0, 0))
        rm = (ro + ri) / 2
        d.arc((cx - rm, cy - rm, cx + rm, cy + rm), -25, 25, fill=DARK, width=50)
        for a in (-17, -6, 6, 17):
            t = math.radians(a)
            px, py = cx + rm * math.cos(t), cy + rm * math.sin(t)
            d.rectangle((px - 70, py - 34, px + 70, py + 34), fill=LIGHT, outline=EDGE, width=6)
    elif name == "catalyst":
        d.rectangle((i, i + 60, s - i, s - i - 60), fill=shell, outline=EDGE, width=14)
        for gy in range(3):
            for gx in range(4):
                x0, y0 = i + 50 + gx * 130, i + 120 + gy * 130
                d.rectangle((x0, y0, x0 + 105, y0 + 105), fill=LIGHT, outline=EDGE, width=6)
        d.rectangle((i + 40, i, s - i - 40, i + 60), fill=DARK)
        d.rectangle((i + 40, s - i - 60, s - i - 40, s - i), fill=DARK)
    elif name == "spool":
        d.rectangle((i + 70, i + 60, s - i - 70, s - i - 60), fill=shell, outline=EDGE, width=12)
        for y0 in range(i + 90, s - i - 90, 34):
            d.line((i + 80, y0, s - i - 80, y0), fill=LIGHT, width=12)
        for x0 in (i, s - i - 70):
            d.rectangle((x0, i, x0 + 70, s - i), fill=EDGE)
        d.rectangle((i - 10, s // 2 - 40, s - i + 10, s // 2 + 40), outline=DARK, width=10)
    elif name == "bore":
        c = s // 2
        d.ellipse((i, i, s - i, s - i), fill=shell, outline=EDGE, width=14)
        for k in range(6):
            a = k * math.pi / 3
            ex, ey = c + 185 * math.cos(a), c + 185 * math.sin(a)
            d.ellipse((ex - 55, ey - 55, ex + 55, ey + 55), fill=LIGHT, outline=EDGE, width=8)
            d.line((c, c, c + 260 * math.cos(a + math.pi / 6), c + 260 * math.sin(a + math.pi / 6)), fill=EDGE, width=16)
        d.ellipse((c - 70, c - 70, c + 70, c + 70), fill=DARK)
    elif name == "projector":
        c = s // 2
        d.rectangle((i + 40, i + 40, s - i - 40, s - i - 40), fill=shell, outline=EDGE, width=12)
        for k in range(3):
            a = k * 2 * math.pi / 3 - math.pi / 2
            d.line((c, c, c + 230 * math.cos(a), c + 230 * math.sin(a)), fill=EDGE, width=34)
            d.ellipse((c + 230 * math.cos(a) - 45, c + 230 * math.sin(a) - 45, c + 230 * math.cos(a) + 45,
                       c + 230 * math.sin(a) + 45), fill=LIGHT, outline=EDGE, width=6)
        for r, w in ((170, 12), (115, 10)):
            d.ellipse((c - r, c - r, c + r, c + r), outline=EDGE, width=w)
        d.ellipse((c - 55, c - 55, c + 55, c + 55), fill=LIGHT, outline=EDGE, width=8)
    return im


def product(name, state, size, seed):
    """Whole as drawn; half-built a bare frame filled only across its lower part; wrecked tipped, a wedge torn out and
    two of its fragments fallen beside it, all inside the frame."""
    if state == "whole":
        return product_shape(name, size, True)
    if state == "half":
        im = product_shape(name, size, False)
        solid = product_shape(name, size, True)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).polygon([(0, size * 0.55), (size, size * 0.4), (size, size), (0, size)], fill=255)
        im.alpha_composite(Image.composite(solid, Image.new("RGBA", (size, size)), mask))
        return im
    rng = random.Random(seed)
    whole = product_shape(name, int(size * 0.62), True).rotate(rng.choice((-14, 11, 17)), expand=True,
                                                                 resample=Image.BICUBIC)
    torn = Image.new("L", whole.size, 255)
    w = whole.width
    wedge = [(w * 0.55, w * 0.02), (w * 0.98, w * 0.02), (w * 0.98, w * 0.5), (w * 0.62, w * 0.36)]
    ImageDraw.Draw(torn).polygon(wedge, fill=0)
    piece = whole.crop((int(w * 0.6), 0, w, int(w * 0.45)))
    whole.putalpha(Image.composite(whole.getchannel("A"), Image.new("L", whole.size, 0), torn))
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    im.alpha_composite(whole, (int(size * 0.04), size - whole.height - int(size * 0.04)))
    frag = piece.rotate(38, expand=True, resample=Image.BICUBIC)
    frag.thumbnail((int(size * 0.3), int(size * 0.3)))
    im.alpha_composite(frag, (size - frag.width - 20, 20))
    small = frag.rotate(-70, expand=True).resize((frag.width // 2, frag.height // 2))
    im.alpha_composite(small, (size - small.width - 30, size - small.height - 30))
    return im


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
        elif ":" in kind:
            name, state = kind.split(":")
            im.alpha_composite(product(name, state, w, pid), (x, y))
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
