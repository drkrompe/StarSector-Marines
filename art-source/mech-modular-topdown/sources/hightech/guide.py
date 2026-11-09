"""Geometry guides for the high-tech (Tri-Tachyon) mech row's look development: a shield skirmisher at the Hound's
weight, a drone controller at the Warden's and a lance platform at the Bulwark's. Each keeps its weight's mounts where
the composer puts them today (LayeredMechComposer, render_variants.py; hull widths H from the centre, +y forward), so
every weapon drawing fits unchanged while the line is a concept:

  light    nose gun under, rear pivot (0, -0.02), up an open nose channel; dorsal pod over (0, -0.30), on the deck;
           feet under at (+-0.22, -0.05), thigh links over from the waist
  medium   cannon under, rear pivot (0, -0.05), up the nose channel; two racks over at (+-0.40, -0.30) on sponsons;
           feet under at (+-0.22, -0.05)
  heavy    an arm gun under each shoulder at (+-0.37, -0.15); two shoulder mounts over at (+-0.40, -0.30) (racks or
           the LL-3 lance); feet under at (+-0.17, -0.28)

Each body adds what the row is for: a sealed sensor visor (no framed canopy), a shield emitter (the light grey disc),
and on the medium four drone cradles (dark rings). Two silhouette families per weight:

  a   kin to the Tri-Tachyon hover line: rounded composite slabs inside a dark bumper rim
  b   vanilla high-tech: swept, curved shells, crescent wings over the legs, no hard corners

Writes <weight>/guide-<a|b>.png, 1254 px square (H = 1254 px), and ref-hover-scout.png, the hover scout's intact hull
cropped from its shipped sheet as the material reference.

Run from anywhere: python art-source/mech-modular-topdown/sources/hightech/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[3]
S = 1254
CX = S / 2
CY = S / 2

BODY = (128, 128, 124, 255)
DECK = (150, 150, 146, 255)
HIP = (104, 104, 100, 255)
RIM = (70, 70, 70, 255)
GLASS = (176, 176, 176, 255)
EMITTER = (210, 210, 210, 255)
CRADLE = (88, 88, 88, 255)
LINE = (44, 44, 44, 255)


def px(x, y):
    return CX + x * S, CY - y * S


def smooth(points, rounds=3):
    """Chaikin corner cutting on a closed outline."""
    for _ in range(rounds):
        out = []
        for i, (x0, y0) in enumerate(points):
            x1, y1 = points[(i + 1) % len(points)]
            out += [(0.75 * x0 + 0.25 * x1, 0.75 * y0 + 0.25 * y1), (0.25 * x0 + 0.75 * x1, 0.25 * y0 + 0.75 * y1)]
        points = out
    return points


def poly(d, points, fill, curved=False):
    if curved:
        points = smooth(points)
    d.polygon([px(x, y) for x, y in points], fill=fill, outline=LINE, width=6)


def mirrored(d, half, fill, curved=False):
    poly(d, half + [(-x, y) for x, y in reversed(half)], fill, curved)


def rect(d, x0, y0, x1, y1, fill, radius=0.02):
    a, b = px(x0, y1), px(x1, y0)
    d.rounded_rectangle((a[0], a[1], b[0], b[1]), radius=radius * S, fill=fill, outline=LINE, width=6)


def circle(d, x, y, r, fill):
    cx, cy = px(x, y)
    d.ellipse((cx - r * S, cy - r * S, cx + r * S, cy + r * S), fill=fill, outline=LINE, width=6)


def visor(d, front, rear, half):
    """A sealed arrow-head sensor visor, the hover line's cockpit glass."""
    mirrored(d, [(0.0, front), (half, front - (front - rear) * 0.45), (half, rear)], GLASS)


def nose_prongs(d, root, tip, inner, outer, curved):
    for side in (-1, 1):
        poly(d, [(side * inner, root), (side * outer, root), (side * (outer * 0.8), tip - 0.05), (side * inner, tip)],
             BODY, curved)


def canvas():
    im = Image.new("RGBA", (S, S))
    return im, ImageDraw.Draw(im)


def light_a():
    im, d = canvas()
    rect(d, -0.17, -0.47, 0.17, 0.30, RIM, 0.08)
    rect(d, -0.14, -0.44, 0.14, 0.27, BODY, 0.06)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.15, side * 0.29))
        rect(d, x0, -0.22, x1, 0.12, RIM, 0.06)
        x0, x1 = sorted((side * 0.17, side * 0.27))
        rect(d, x0, -0.19, x1, 0.09, BODY, 0.045)
    nose_prongs(d, 0.24, 0.48, 0.04, 0.11, False)
    rect(d, -0.09, -0.30, 0.09, 0.12, DECK, 0.015)
    visor(d, 0.27, 0.14, 0.07)
    circle(d, 0.0, -0.37, 0.05, EMITTER)
    return im


def light_b():
    im, d = canvas()
    mirrored(d, [(0.05, 0.30), (0.13, 0.22), (0.16, 0.10), (0.30, -0.05), (0.33, -0.20), (0.24, -0.20),
                 (0.16, -0.30), (0.13, -0.46), (0.0, -0.48)], BODY, True)
    nose_prongs(d, 0.20, 0.48, 0.04, 0.10, True)
    rect(d, -0.09, -0.30, 0.09, 0.12, DECK, 0.015)
    visor(d, 0.27, 0.13, 0.065)
    circle(d, 0.0, -0.37, 0.05, EMITTER)
    return im


def medium_a():
    im, d = canvas()
    rect(d, -0.30, -0.47, 0.30, 0.30, RIM, 0.10)
    rect(d, -0.27, -0.44, 0.27, 0.27, BODY, 0.08)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.26, side * 0.38))
        rect(d, x0, -0.38, x1, -0.16, HIP, 0.04)
    nose_prongs(d, 0.24, 0.48, 0.045, 0.12, False)
    rect(d, -0.08, -0.36, 0.08, 0.10, DECK, 0.012)
    for x in (-0.18, 0.18):
        for y in (0.05, -0.20):
            circle(d, x, y, 0.06, CRADLE)
    visor(d, 0.27, 0.14, 0.075)
    circle(d, 0.0, -0.41, 0.045, EMITTER)
    return im


def medium_b():
    im, d = canvas()
    mirrored(d, [(0.06, 0.30), (0.16, 0.24), (0.24, 0.08), (0.34, -0.12), (0.40, -0.34), (0.30, -0.36),
                 (0.20, -0.46), (0.0, -0.48)], BODY, True)
    nose_prongs(d, 0.20, 0.48, 0.045, 0.11, True)
    rect(d, -0.08, -0.36, 0.08, 0.10, DECK, 0.012)
    for x in (-0.18, 0.18):
        for y in (0.02, -0.22):
            circle(d, x, y, 0.06, CRADLE)
    visor(d, 0.27, 0.13, 0.07)
    circle(d, 0.0, -0.41, 0.045, EMITTER)
    return im


def heavy_a():
    """The shoulders stop at y 0.06, as the Bulwark's do, so the arm guns' barrels show either side of the head."""
    im, d = canvas()
    rect(d, -0.23, -0.10, 0.23, 0.44, RIM, 0.08)
    rect(d, -0.36, -0.47, 0.36, 0.09, RIM, 0.10)
    rect(d, -0.20, -0.10, 0.20, 0.41, BODY, 0.06)
    rect(d, -0.33, -0.44, 0.33, 0.06, BODY, 0.08)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.30, side * 0.44))
        rect(d, x0, -0.36, x1, 0.04, HIP, 0.05)
    rect(d, -0.10, -0.40, 0.10, 0.10, DECK, 0.015)
    visor(d, 0.36, 0.18, 0.09)
    circle(d, 0.0, -0.10, 0.09, EMITTER)
    return im


def heavy_b():
    im, d = canvas()
    mirrored(d, [(0.0, 0.47), (0.12, 0.42), (0.20, 0.26), (0.24, 0.10), (0.42, 0.04), (0.46, -0.10), (0.40, -0.36),
                 (0.24, -0.40), (0.16, -0.48), (0.0, -0.46)], BODY, True)
    rect(d, -0.10, -0.40, 0.10, 0.10, DECK, 0.015)
    visor(d, 0.38, 0.18, 0.085)
    circle(d, 0.0, -0.10, 0.09, EMITTER)
    return im


def hover_reference():
    sheet = Image.open(REPOSITORY / "mod" / "graphics" / "battle" / "vehicles" / "tt-hover-scout.png").convert("RGBA")
    return sheet.crop((0, 80, 680, 500))


def main():
    for weight, a, b in (("light", light_a, light_b), ("medium", medium_a, medium_b), ("heavy", heavy_a, heavy_b)):
        (HERE / weight).mkdir(exist_ok=True)
        a().save(HERE / weight / "guide-a.png")
        b().save(HERE / weight / "guide-b.png")
    hover_reference().save(HERE / "ref-hover-scout.png")
    print("wrote light, medium and heavy guide-a/b.png and ref-hover-scout.png")


if __name__ == "__main__":
    main()
