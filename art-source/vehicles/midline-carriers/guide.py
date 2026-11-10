"""Geometry guides for the midline row's articulated carriers, light, medium and heavy (owner's pick E,
art-source/mech-modular-topdown/sources/midline/frames/e-sheet.png; mech-lines.md, Midline).

Each carrier is a drive cab and mission modules on wheels, joined by hinges that bend to steer. The guide lays the
carrier out straight, facing LEFT (the cab's nose at the left), on a square canvas of two rows: the live machine
above, the wreck below, each centred on its row. Every segment is its own flat block so pack.py can cut it as a layer
that swings at its hinge. Flat tones say what is what:

  light grey   armour plate (a segment's body)
  mid grey     the hinge: pistons, cables and a pivot drum between two segments
  near black   a wheel
  dark disc    an EMPTY weapon socket (mount ring), always on the spine, never in pairs: the concept's two sockets
               over a hatch read as a face (owner), so the mounts run in a line nose to tail

The layout is data (HULLS below) shared with pack.py, which cuts at the hinge centres. All sizes are cells.

Run: python art-source/vehicles/midline-carriers/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
CONCEPT = HERE.parents[1] / "mech-modular-topdown" / "sources" / "midline" / "frames" / "e-sheet.png"
CANVAS = 1024
ROW = CANVAS // 2

PLATE = (168, 168, 164, 255)
HINGE = (92, 94, 96, 255)
WHEEL = (34, 34, 36, 255)
SOCKET = (52, 52, 54, 255)
SOCKET_RIM = (120, 120, 118, 255)

# Segments nose to tail: (name, length, width, axles as offsets from the segment's front edge).
# Hinge: the gap between two segments. Mounts: (segment, offset from its front edge, radius, kind).
# Wheel: length along the hull, how far it juts past the segment's side, and its depth under the plate.
HULLS = {
    "light": dict(
        segments=[("cab", 1.05, 1.10, [0.52]), ("module", 1.45, 1.18, [0.85])],
        hinge=0.26, wheel=(0.56, 0.16, 0.10),
        mounts=[("module", 0.62, 0.28, "main")]),
    "medium": dict(
        segments=[("cab", 1.15, 1.30, [0.56]), ("module", 2.35, 1.42, [0.70, 1.75])],
        hinge=0.28, wheel=(0.62, 0.18, 0.12),
        mounts=[("module", 0.80, 0.38, "main"), ("module", 1.80, 0.20, "secondary")]),
    "heavy": dict(
        segments=[("cab", 1.20, 1.55, [0.58]), ("power", 0.95, 1.55, [0.48]),
                  ("module", 2.30, 1.70, [0.62, 1.70])],
        hinge=0.26, wheel=(0.66, 0.20, 0.14),
        mounts=[("module", 0.42, 0.20, "secondary"), ("module", 1.18, 0.45, "main"),
                ("module", 1.98, 0.20, "secondary")]),
}
# How much of the canvas width the straight machine fills.
FILL = 0.86


def length(hull):
    """Nose to tail, cells: every segment and every hinge gap."""
    return sum(s[1] for s in hull["segments"]) + hull["hinge"] * (len(hull["segments"]) - 1)


def px_per_cell(hull):
    return CANVAS * FILL / length(hull)


def layout(hull):
    """Each segment's front and rear edge, cells from the nose; each hinge's centre, cells from the nose."""
    x, edges, hinges = 0.0, [], []
    for i, (_, seg_len, _, _) in enumerate(hull["segments"]):
        if i:
            hinges.append(x + hull["hinge"] / 2)
            x += hull["hinge"]
        edges.append((x, x + seg_len))
        x += seg_len
    return edges, hinges


def draw(hull, d, x0, cy, ppc):
    """The straight machine at nose x0 (canvas px), centred on row centre cy."""
    edges, hinges = layout(hull)
    wl, jut, depth = hull["wheel"]
    segs = hull["segments"]
    # Hinges first, so plate overlaps their ends: pistons as two bars, a pivot drum on the spine.
    for i, hx in enumerate(hinges):
        half_w = min(segs[i][2], segs[i + 1][2]) * 0.30
        a, b = edges[i][1] - 0.06, edges[i + 1][0] + 0.06
        for side in (-1, 1):
            yc = cy + side * half_w * ppc
            d.rectangle((x0 + a * ppc, yc - 0.07 * ppc, x0 + b * ppc, yc + 0.07 * ppc), fill=HINGE)
        r = 0.16 * ppc
        d.ellipse((x0 + hx * ppc - r, cy - r, x0 + hx * ppc + r, cy + r), fill=HINGE)
    for (name, _, width, axles), (front, rear) in zip(segs, edges):
        for ax in axles:
            wx = front + ax
            for side in (-1, 1):
                inner = cy + side * (width / 2 - depth) * ppc
                outer = cy + side * (width / 2 + jut) * ppc
                d.rounded_rectangle((x0 + (wx - wl / 2) * ppc, min(inner, outer),
                                     x0 + (wx + wl / 2) * ppc, max(inner, outer)), radius=0.08 * ppc, fill=WHEEL)
        chamfer = 0.16 * ppc
        l, r = x0 + front * ppc, x0 + rear * ppc
        t, b = cy - width / 2 * ppc, cy + width / 2 * ppc
        d.polygon([(l + chamfer, t), (r - chamfer * .6, t), (r, t + chamfer * .6), (r, b - chamfer * .6),
                   (r - chamfer * .6, b), (l + chamfer, b), (l, b - chamfer), (l, t + chamfer)], fill=PLATE)
    by_name = {s[0]: e for s, e in zip(segs, edges)}
    for seg, off, radius, _ in hull["mounts"]:
        mx = x0 + (by_name[seg][0] + off) * ppc
        rr = radius * ppc
        d.ellipse((mx - rr, cy - rr, mx + rr, cy + rr), fill=SOCKET_RIM)
        rr *= 0.78
        d.ellipse((mx - rr, cy - rr, mx + rr, cy + rr), fill=SOCKET)


def guide(weight):
    hull = HULLS[weight]
    ppc = px_per_cell(hull)
    canvas = Image.new("RGBA", (CANVAS, CANVAS))
    d = ImageDraw.Draw(canvas)
    x0 = (CANVAS - length(hull) * ppc) / 2
    for row in range(2):
        draw(hull, d, x0, ROW * row + ROW / 2, ppc)
    canvas.save(HERE / f"{weight}-guide.png")
    return ppc


def concepts():
    """The concept sheet's three machines, turned to face left: identity and material reference only."""
    sheet = Image.open(CONCEPT).convert("RGBA")
    alpha = sheet.getchannel("A").point(lambda v: 255 if v > 24 else 0)
    cols = [any(alpha.getpixel((x, y)) for y in range(0, sheet.height, 4)) for x in range(sheet.width)]
    spans, start = [], None
    for x, on in enumerate(cols + [False]):
        if on and start is None:
            start = x
        if not on and start is not None:
            if x - start > 40:
                spans.append((start, x))
            start = None
    for weight, (a, b) in zip(("light", "medium", "heavy"), spans[:3]):
        part = sheet.crop((a, 0, b, sheet.height))
        part = part.crop(part.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox())
        part.rotate(90, expand=True).save(HERE / f"{weight}-concept.png")


def main():
    concepts()
    for weight in HULLS:
        ppc = guide(weight)
        hull = HULLS[weight]
        print(f"{weight}: {length(hull):.2f} cells long at {ppc:.1f} px a cell")


if __name__ == "__main__":
    main()
