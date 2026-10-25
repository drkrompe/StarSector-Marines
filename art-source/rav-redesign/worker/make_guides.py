"""Draw the skitterer's three body-plan guides on the Runner body layer's canvas and rows.

  python make_guides.py

Writes guides/<option>-guide.png (1024x1536, flat grey #b4b4b4 ground, nose up). Every guide keeps the Runner body
layer's rows (`exploration/render_runner.py` BODY_ROWS: head 333, neck 420, shoulder 541, hip 958) so the export cuts
it exactly as `export_runner_rig.py` cuts the Runner, and the bone px the composer holds stay the same. ImageGen copies
guide shapes literally, so each guide is the option's silhouette and its colour blocking: lilac plates, crimson
sinew, ivory bone and amber, flat-shaded with one soft light from the upper left.

  hauler  squat broad flat back, a shallow cradle of ivory ribs along the spine, one amber node, hooked pincers
  tender  narrow front, a swollen translucent amber-lilac gut sac over the hindquarters, feeler mandibles
  mite    a round tick carapace, the head tucked under a shield-plate lip, pale plates with crimson seams
"""
import math

import numpy as np
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).parent
OUT = HERE / "guides"
W, H, SS = 1024, 1536, 2
GROUND = (180, 180, 180)
CX = 512

LILAC = (214, 180, 216)
LILAC_HI = (240, 222, 242)
LILAC_LO = (162, 124, 176)
WHELP = (232, 212, 236)
VIOLET = (112, 66, 118)
CRIMSON = (156, 28, 62)
CRIMSON_HI = (196, 58, 92)
IVORY = (238, 228, 208)
IVORY_LO = (196, 180, 156)
AMBER = (255, 170, 58)
AMBER_HI = (255, 220, 140)


class Canvas:
    def __init__(self):
        self.img = Image.new("RGBA", (W * SS, H * SS), GROUND + (255,))

    def layer(self):
        return Image.new("RGBA", self.img.size, (0, 0, 0, 0))

    def put(self, layer, blur=0):
        if blur:
            layer = layer.filter(ImageFilter.GaussianBlur(blur * SS))
        self.img.alpha_composite(layer)

    def ellipse(self, cx, cy, rx, ry, fill, blur=0):
        lay = self.layer()
        ImageDraw.Draw(lay).ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=fill)
        self.put(lay, blur)

    def poly(self, pts, fill, blur=0, mirror=False):
        lay = self.layer()
        d = ImageDraw.Draw(lay)
        d.polygon([(x * SS, y * SS) for x, y in pts], fill=fill)
        if mirror:
            d.polygon([((2 * CX - x) * SS, y * SS) for x, y in pts], fill=fill)
        self.put(lay, blur)

    def line(self, pts, fill, width, mirror=False, blur=0):
        lay = self.layer()
        d = ImageDraw.Draw(lay)
        d.line([(x * SS, y * SS) for x, y in pts], fill=fill, width=int(width * SS), joint="curve")
        if mirror:
            d.line([((2 * CX - x) * SS, y * SS) for x, y in pts], fill=fill, width=int(width * SS), joint="curve")
        self.put(lay, blur)

    def save(self, path):
        self.img.resize((W, H), Image.LANCZOS).convert("RGB").save(path)


def plate(c, cx, cy, rx, ry, base=LILAC, hi=LILAC_HI, lo=LILAC_LO, mirror=False):
    """A shaded chitin plate: shadow rim, body, and a highlight toward the upper left."""
    for x in ([cx, 2 * CX - cx] if mirror else [cx]):
        c.ellipse(x, cy, rx + 6, ry + 6, CRIMSON + (255,))
        c.ellipse(x, cy, rx, ry, lo + (255,))
        c.ellipse(x - rx * 0.12, cy - ry * 0.12, rx * 0.86, ry * 0.86, base + (255,))
        c.ellipse(x - rx * 0.30, cy - ry * 0.32, rx * 0.40, ry * 0.36, hi + (200,), blur=6)


def kite(c, cy, half_w, half_h, base=LILAC):
    """One diamond spine plate on the centre line, as the Runner's."""
    c.poly([(CX, cy - half_h - 6), (CX + half_w + 6, cy), (CX, cy + half_h + 6), (CX - half_w - 6, cy)],
           CRIMSON + (255,))
    c.poly([(CX, cy - half_h), (CX + half_w, cy), (CX, cy + half_h), (CX - half_w, cy)], LILAC_LO + (255,))
    c.poly([(CX, cy - half_h), (CX + half_w * 0.2, cy), (CX, cy + half_h * 0.8), (CX - half_w, cy)], base + (255,))
    c.poly([(CX - 4, cy - half_h + 10), (CX - half_w * 0.55, cy - 4), (CX - 6, cy + 8)], LILAC_HI + (190,), blur=3)


def bezier(p0, p1, p2, n=24):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in (i / n for i in range(n + 1))]


def taper(curve, w0, w1):
    """A closed outline around a curve, w0 wide at its root narrowing to w1 at its tip."""
    left, right = [], []
    for i, (x, y) in enumerate(curve):
        j0, j1 = max(0, i - 1), min(len(curve) - 1, i + 1)
        dx, dy = curve[j1][0] - curve[j0][0], curve[j1][1] - curve[j0][1]
        n = math.hypot(dx, dy) or 1
        w = (w0 + (w1 - w0) * i / (len(curve) - 1)) / 2
        left.append((x - dy / n * w, y + dx / n * w))
        right.append((x + dy / n * w, y - dx / n * w))
    return left + right[::-1]


def pincer(c, base, mid, tip, width, mirror=True):
    """A short hooked ivory pincer: a tapered curve from a thick root to a sharp tip hooking inward."""
    curve = bezier(base, mid, tip)
    c.poly(taper(curve, width + 12, 8), CRIMSON + (255,), mirror=mirror)
    c.poly(taper(curve, width, 3), IVORY_LO + (255,), mirror=mirror)
    c.poly(taper(curve[:18], width * 0.5, 2), IVORY + (255,), mirror=mirror)


def cubic(p0, p1, p2, p3, n=28):
    return [tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t * t * c_ + t ** 3 * d
                  for a, b, c_, d in zip(p0, p1, p2, p3)) for t in (i / n for i in range(n + 1))]


def hook(c, base, out, tip, width, tooth=True, mirror=True):
    """A stout hooked mandible: forward and out from a thick root, then curled hard inward to a sharp tip, with a
    tooth on its inner edge, so the pair reads as pincers closing in front of the head, never as horns."""
    curve = cubic(base, (out[0], base[1] - (base[1] - out[1]) * 0.4), (out[0], out[1]), tip)
    c.poly(taper(curve, width + 12, 9), CRIMSON + (255,), mirror=mirror)
    c.poly(taper(curve, width, 4), IVORY_LO + (255,), mirror=mirror)
    if tooth:
        mx, my = curve[int(len(curve) * 0.45)]
        t = [(mx - 4, my - 12), (mx - 4, my + 12), (mx + width * 0.95, my + 4)]
        c.poly([(t[0][0], t[0][1] - 5), (t[1][0], t[1][1] + 5), (t[2][0] + 7, t[2][1])], CRIMSON + (255,),
               mirror=mirror)
        c.poly(t, IVORY_LO + (255,), mirror=mirror)
    c.poly(taper(curve[:20], width * 0.5, 3), IVORY + (255,), mirror=mirror)


def repaint_ref(raw, erase_rows, draw):
    """An accepted raw with its mandibles redrawn: the rows above the head are cleared to the ground and `draw`
    paints the new flat mandibles, so a finish pass copies them as the raw's own parts."""
    c = Canvas()
    img = Image.open(raw).convert("RGBA").resize((W * SS, H * SS), Image.LANCZOS)
    top, bottom = erase_rows
    ground = img.getpixel((8, 8))
    ImageDraw.Draw(img).rectangle([0, 0, W * SS, bottom * SS], fill=ground)
    c.img = img
    draw(c)
    return c


def head(c, top, bottom, half_w, base=LILAC):
    """A rounded wedge head ending at the neck, eyes amber at its sides."""
    c.poly([(CX - half_w * 0.7, top + 10), (CX + half_w * 0.7, top + 10), (CX + half_w, (top + bottom) / 2),
            (CX + half_w * 0.55, bottom), (CX - half_w * 0.55, bottom), (CX - half_w, (top + bottom) / 2)],
           CRIMSON + (255,))
    c.ellipse(CX, (top + bottom) / 2 + 4, half_w - 8, (bottom - top) / 2 - 6, LILAC_LO + (255,))
    c.ellipse(CX - 8, (top + bottom) / 2 - 4, half_w - 22, (bottom - top) / 2 - 18, base + (255,))
    c.ellipse(CX - half_w * 0.3, top + (bottom - top) * 0.32, half_w * 0.32, (bottom - top) * 0.18,
              LILAC_HI + (190,), blur=5)
    eye = top + (bottom - top) * 0.36
    c.ellipse(CX - half_w * 0.78, eye, 9, 8, AMBER + (255,))
    c.ellipse(CX + half_w * 0.78, eye, 9, 8, AMBER + (255,))


def neck(c, top, bottom, half_w):
    c.poly([(CX - half_w, top), (CX + half_w, top), (CX + half_w * 1.1, bottom), (CX - half_w * 1.1, bottom)],
           CRIMSON + (255,))
    for y in range(int(top) + 12, int(bottom), 22):
        c.line([(CX - half_w + 6, y), (CX + half_w - 6, y + 6)], CRIMSON_HI + (255,), 6)


def amber_node(c, cx, cy, r):
    c.ellipse(cx, cy, r + 8, r + 8, CRIMSON + (255,))
    c.ellipse(cx, cy, r, r, AMBER + (255,))
    c.ellipse(cx - r * 0.3, cy - r * 0.3, r * 0.45, r * 0.45, AMBER_HI + (255,), blur=2)


def hauler():
    c = Canvas()
    # body: a broad flat oval shield from just behind the neck to a blunt rump
    c.ellipse(CX, 770, 222, 330, CRIMSON + (255,))
    c.ellipse(CX, 770, 210, 318, VIOLET + (255,))
    # rim plates down each flank, the creature's armour
    for cy, rx, ry in ((560, 70, 82), (680, 74, 92), (810, 74, 92), (940, 68, 86), (1040, 54, 64)):
        offs = 205 - rx * 0.75 - (40 if cy in (560, 1040) else 0)
        plate(c, CX - offs, cy, rx, ry, mirror=True)
    # blunt tail stub
    kite(c, 1110, 46, 40)
    # the cradle: a shallow dished bed between the rim plates, crossed by ivory ribs that curl up at the flanks
    c.ellipse(CX, 770, 118, 226, (88, 48, 96, 255))
    c.ellipse(CX + 10, 790, 96, 200, (70, 36, 78, 255), blur=10)
    for i, y in enumerate(range(612, 960, 68)):
        w = 128 - abs(i - 2.3) * 9
        rib = [(CX - w, y - 18), (CX - w * 0.6, y + 6), (CX, y + 12), (CX + w * 0.6, y + 6), (CX + w, y - 18)]
        c.line(rib, CRIMSON + (255,), 30)
        c.line(rib, IVORY_LO + (255,), 20)
        c.line([(p[0], p[1] - 4) for p in rib[:3]], IVORY + (255,), 10)
    # the spine keel down the cradle floor
    c.line([(CX, 590), (CX, 960)], IVORY_LO + (255,), 14)
    amber_node(c, CX, 545, 26)
    neck(c, 420, 500, 46)
    head(c, 236, 444, 92)
    # short hooked pincer mandibles, curling inward
    pincer(c, (CX - 62, 270), (CX - 112, 170), (CX - 18, 150), 30)
    return c


def tender():
    c = Canvas()
    # the gut sac: a swollen translucent amber-lilac bag over the hindquarters
    c.ellipse(CX, 960, 216, 236, CRIMSON + (255,))
    c.ellipse(CX, 960, 206, 226, (196, 150, 200, 255))
    c.ellipse(CX, 970, 176, 196, (226, 160, 120, 255), blur=18)
    c.ellipse(CX + 6, 980, 120, 140, (250, 190, 110, 255), blur=24)
    # what it carries: a few dark seed masses suspended in the sac
    for x, y, r in ((CX - 70, 930, 34), (CX + 58, 1000, 40), (CX - 18, 1070, 28), (CX + 30, 880, 24)):
        c.ellipse(x, y, r, r * 0.85, (150, 70, 70, 200), blur=6)
    # crimson veins over the membrane
    for sx in (-1, 1):
        c.line([(CX + sx * 30, 760), (CX + sx * 110, 860), (CX + sx * 150, 990), (CX + sx * 110, 1120)],
               CRIMSON + (220,), 10)
        c.line([(CX + sx * 110, 860), (CX + sx * 60, 960), (CX + sx * 70, 1060)], CRIMSON + (200,), 7)
    c.ellipse(CX - 90, 860, 70, 50, (255, 240, 230, 150), blur=12)   # wet sheen
    # narrow plated front body
    c.ellipse(CX, 640, 128, 186, CRIMSON + (255,))
    c.ellipse(CX, 640, 118, 176, VIOLET + (255,))
    plate(c, CX - 74, 590, 52, 84, mirror=True)
    plate(c, CX - 66, 718, 46, 60, mirror=True)
    for cy in (540, 620, 700):
        kite(c, cy, 40, 38)
    # bone straps that hold the sac to the frame
    for sx in (-1, 1):
        c.line([(CX + sx * 70, 780), (CX + sx * 160, 820), (CX + sx * 200, 900)], CRIMSON + (255,), 24)
        c.line([(CX + sx * 70, 780), (CX + sx * 160, 820), (CX + sx * 200, 900)], IVORY_LO + (255,), 14)
    plate(c, CX, 1196, 40, 26)                                          # a small plate closing the sac
    neck(c, 420, 480, 34)
    head(c, 262, 440, 70)
    # feeler mandibles: slender ivory palps, tips curled out
    c.line([(CX - 34, 280), (CX - 58, 210), (CX - 50, 150), (CX - 70, 112)], CRIMSON + (255,), 18, mirror=True)
    c.line([(CX - 34, 280), (CX - 58, 210), (CX - 50, 150), (CX - 70, 112)], IVORY + (255,), 10, mirror=True)
    for x, y in ((CX - 70, 112), (CX + 70, 112)):
        c.ellipse(x, y, 12, 12, IVORY + (255,))
    return c


def mite():
    c = Canvas()
    # a round tick carapace, pale whelp-lilac plates parted by crimson sinew seams
    c.ellipse(CX, 740, 230, 312, CRIMSON + (255,))
    c.ellipse(CX, 740, 216, 298, LILAC_LO + (255,))
    # radial plates: a central keel row and two rings of side plates
    c.ellipse(CX - 16, 720, 196, 276, WHELP + (255,))
    # crimson seams: two plate rings either side of the keel
    for a0 in (-55, -15, 25, 65):
        a = math.radians(a0)
        c.line([(CX - 60, 740 + math.sin(a) * 120), (CX - 150, 740 + math.sin(a) * 200),
                (CX - 214, 740 + math.sin(a) * 270)], CRIMSON + (255,), 14, mirror=True)
    c.line(bezier((CX - 70, 480), (CX - 120, 740), (CX - 70, 1010)), CRIMSON + (255,), 14, mirror=True)
    for cy in (600, 720, 840, 960):
        kite(c, cy, 46, 52, base=WHELP)
    shine = Canvas()
    shine.img = c.layer()
    shine.ellipse(CX - 110, 600, 80, 120, (255, 248, 255, 150), blur=22)   # domed highlight
    shine.ellipse(CX + 120, 900, 110, 150, (120, 80, 140, 120), blur=30)   # dome shade
    mask = c.layer()
    ImageDraw.Draw(mask).ellipse([(CX - 216) * SS, (740 - 298) * SS, (CX + 216) * SS, (740 + 298) * SS],
                                 fill=(0, 0, 0, 255))
    shine.img.putalpha(Image.fromarray((np.asarray(shine.img.getchannel("A")).astype(float)
                                        * (np.asarray(mask.getchannel("A")) / 255)).astype(np.uint8)))
    c.put(shine.img)
    # nothing of the carapace's detail strays past its rim
    outside = Image.new("RGBA", c.img.size, GROUND + (255,))
    hole = Image.new("L", c.img.size, 255)
    ImageDraw.Draw(hole).ellipse([(CX - 230) * SS, (740 - 312) * SS, (CX + 230) * SS, (740 + 312) * SS], fill=0)
    outside.putalpha(hole)
    c.put(outside)
    # the shield-plate lip over the head: a pale crescent overhanging the front edge
    c.poly([(CX - 190, 520), (CX - 120, 452), (CX, 428), (CX + 120, 452), (CX + 190, 520), (CX + 120, 488),
            (CX, 470), (CX - 120, 488)], CRIMSON + (255,))
    c.poly([(CX - 176, 512), (CX - 116, 460), (CX, 438), (CX + 116, 460), (CX + 176, 512), (CX + 116, 484),
            (CX, 466), (CX - 116, 484)], IVORY + (255,))
    # the tucked head: only its front shows under the lip
    head(c, 320, 448, 70, base=WHELP)
    c.poly([(CX - 120, 452), (CX, 428), (CX + 120, 452), (CX + 70, 470), (CX - 70, 470)], IVORY + (255,))
    pincer(c, (CX - 46, 346), (CX - 84, 270), (CX - 14, 262), 24)
    # a small tail nub
    kite(c, 1062, 30, 26, base=WHELP)
    return c


# Round 2: the v1 raws read their pincers as horns. The finish pass paints over each v1 raw with the head's front
# cleared and stout hooked pincers drawn in their place.
REPAINT = {
    "hauler": (232, lambda c: hook(c, (CX - 56, 262), (CX - 98, 196), (CX - 16, 176), 38)),
    "mite": (300, lambda c: hook(c, (CX - 42, 330), (CX - 78, 278), (CX - 12, 262), 30)),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("hauler", hauler), ("tender", tender), ("mite", mite)):
        fn().save(OUT / f"{name}-guide.png")
        print(OUT / f"{name}-guide.png")
    for name, (rows, draw) in REPAINT.items():
        raw = HERE / "raw" / "options" / f"{name}-body-v1.png"
        if raw.exists():
            repaint_ref(raw, (0, rows), draw).save(OUT / f"{name}-v2-ref.png")
            print(OUT / f"{name}-v2-ref.png")


if __name__ == "__main__":
    main()
