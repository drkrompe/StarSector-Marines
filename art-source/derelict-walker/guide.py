"""Geometry guides for the derelict security walker's art (derelict-security-network.md, The walker's legs).

One guide per design direction. Each 1536 x 1024 canvas, 256 px a cell, flat #b4b4b4 ground, facing up:
  left  (0..1024)     the walker assembled, four legs in an X, for proportion and the owner's read;
  right (1024..1536)  the parts the game draws, apart: the torso (top), the thigh (hip at its bottom, knee at its
                      top) and the shin (knee at its bottom, foot at its top), each upright, so DerelictWalkerComposer
                      turns each part's top toward where it reaches.
export.py cuts the parts from the right panel by the boxes below (PARTS), scaled to the raw's measured size.

Run: python art-source/derelict-walker/guide.py   (writes guides/<design>-guide.png)
"""
import json
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
CELL = 256
W, H = 1536, 1024
GROUND = (180, 180, 180, 255)
PLATE = (120, 122, 124, 255)
RAISED = (150, 152, 154, 255)
DARK = (62, 64, 66, 255)
EDGE = (28, 29, 30, 255)
LAMP = (210, 70, 50, 255)
HAZARD = (200, 165, 50, 255)

# The parts' boxes on the right panel, canvas px (x0, y0, x1, y1), and their joints.
PARTS = {
    "torso": (1040, 0, 1520, 464),
    "thigh": (1100, 480, 1240, 1012),
    "shin": (1290, 480, 1430, 1012),
}
THIGH_LEN = 1.30   # cells, hip to knee
SHIN_LEN = 1.60    # cells, knee to foot tip
LEG_W = 0.30       # cells


def poly(d, pts, fill, width=4):
    d.polygon(pts, fill=fill, outline=EDGE, width=width)


def octagon(cx, cy, w, h, cut):
    x0, x1, y0, y1 = cx - w / 2, cx + w / 2, cy - h / 2, cy + h / 2
    return [(x0 + cut, y0), (x1 - cut, y0), (x1, y0 + cut), (x1, y1 - cut), (x1 - cut, y1), (x0 + cut, y1),
            (x0, y1 - cut), (x0, y0 + cut)]


def torso(d, cx, cy, design, s):
    """The torso block-in, s px a cell."""
    if design == "crab":
        poly(d, octagon(cx, cy, 1.55 * s, 1.30 * s, 0.30 * s), PLATE)
        poly(d, octagon(cx, cy + 0.05 * s, 0.95 * s, 0.75 * s, 0.18 * s), RAISED)
        d.rectangle([cx - 0.35 * s, cy - 0.62 * s, cx + 0.35 * s, cy - 0.48 * s], fill=DARK, outline=EDGE, width=3)
        for sx in (-1, 1):
            d.rectangle([cx + sx * 0.78 * s - 0.09 * s, cy - 0.30 * s, cx + sx * 0.78 * s + 0.09 * s, cy + 0.10 * s],
                        fill=DARK, outline=EDGE, width=3)
        d.rectangle([cx - 0.32 * s, cy + 0.22 * s, cx + 0.32 * s, cy + 0.50 * s], fill=DARK, outline=EDGE, width=3)
    elif design == "dome":
        poly(d, octagon(cx, cy, 1.45 * s, 1.45 * s, 0.38 * s), PLATE)
        r = 0.48 * s
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=RAISED, outline=EDGE, width=4)
        d.rectangle([cx - 0.07 * s, cy - 0.95 * s, cx + 0.07 * s, cy - 0.40 * s], fill=DARK, outline=EDGE, width=3)
        d.rectangle([cx - 0.30 * s, cy + 0.50 * s, cx + 0.30 * s, cy + 0.66 * s], fill=DARK, outline=EDGE, width=3)
    elif design == "sentinel":
        # A hunched spine: a sensor head forward, a segmented back, a power pack behind.
        d.ellipse([cx - 0.30 * s, cy - 0.95 * s, cx + 0.30 * s, cy - 0.40 * s], fill=RAISED, outline=EDGE, width=4)
        for k, (w, y0, y1) in enumerate([(1.10, -0.45, -0.05), (1.25, -0.05, 0.35), (1.05, 0.35, 0.70)]):
            poly(d, octagon(cx, cy + (y0 + y1) / 2 * s, w * s, (y1 - y0) * s, 0.12 * s), PLATE if k != 1 else RAISED)
        d.rectangle([cx - 0.35 * s, cy + 0.70 * s, cx + 0.35 * s, cy + 0.92 * s], fill=DARK, outline=EDGE, width=3)
        d.rectangle([cx - 0.05 * s, cy - 1.10 * s, cx + 0.05 * s, cy - 0.80 * s], fill=DARK, outline=EDGE, width=3)
    else:  # prow
        pts = [(cx, cy - 0.95 * s), (cx + 0.62 * s, cy - 0.45 * s), (cx + 0.66 * s, cy + 0.55 * s),
               (cx + 0.40 * s, cy + 0.85 * s), (cx - 0.40 * s, cy + 0.85 * s), (cx - 0.66 * s, cy + 0.55 * s),
               (cx - 0.62 * s, cy - 0.45 * s)]
        poly(d, pts, PLATE)
        inner = [(cx, cy - 0.60 * s), (cx + 0.38 * s, cy - 0.28 * s), (cx + 0.40 * s, cy + 0.45 * s),
                 (cx - 0.40 * s, cy + 0.45 * s), (cx - 0.38 * s, cy - 0.28 * s)]
        poly(d, inner, RAISED)
        d.rectangle([cx - 0.30 * s, cy + 0.55 * s, cx + 0.30 * s, cy + 0.78 * s], fill=DARK, outline=EDGE, width=3)
    d.ellipse([cx - 0.06 * s, cy - 0.70 * s - 0.06 * s, cx + 0.06 * s, cy - 0.70 * s + 0.06 * s], fill=LAMP,
              outline=EDGE, width=2)


def segment(d, x0, y0, x1, y1, width, foot=False):
    """A leg segment from (x0, y0) to (x1, y1): a tapered armoured strut with a joint at its root."""
    ang = math.atan2(y1 - y0, x1 - x0)
    nx, ny = -math.sin(ang), math.cos(ang)
    w0, w1 = width / 2, width * 0.36
    pts = [(x0 + nx * w0, y0 + ny * w0), (x1 + nx * w1, y1 + ny * w1), (x1 - nx * w1, y1 - ny * w1),
           (x0 - nx * w0, y0 - ny * w0)]
    poly(d, pts, PLATE, 3)
    mx, my = x0 + (x1 - x0) * 0.25, y0 + (y1 - y0) * 0.25
    d.line([(mx, my), (x0 + (x1 - x0) * 0.8, y0 + (y1 - y0) * 0.8)], fill=DARK, width=max(2, int(width * 0.18)))
    r = width * 0.55
    d.ellipse([x0 - r, y0 - r, x0 + r, y0 + r], fill=DARK, outline=EDGE, width=3)
    if foot:
        # A three-toed clamp at the tip.
        for da in (-0.55, 0, 0.55):
            tx = x1 + math.cos(ang + da) * width * 0.9
            ty = y1 + math.sin(ang + da) * width * 0.9
            d.line([(x1, y1), (tx, ty)], fill=EDGE, width=int(width * 0.32))
            d.line([(x1, y1), (tx, ty)], fill=DARK, width=int(width * 0.18))


def guide(design):
    img = Image.new("RGBA", (W, H), GROUND)
    d = ImageDraw.Draw(img)
    s = 220  # the assembled walker a little smaller than the parts, to fit the left panel
    cx, cy = 512, 512
    hip = 0.55 * s
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        dx, dy = math.sin(r), -math.cos(r)
        hx, hy = cx + dx * hip, cy + dy * hip
        kx, ky = cx + dx * (hip + 0.85 * s), cy + dy * (hip + 0.85 * s)
        fx, fy = cx + dx * (hip + 1.75 * s), cy + dy * (hip + 1.75 * s)
        segment(d, kx, ky, fx, fy, LEG_W * s * 0.9, foot=True)
        segment(d, hx, hy, kx, ky, LEG_W * s)
    torso(d, cx, cy, design, s)
    # The parts, upright.
    x0, y0, x1, y1 = PARTS["torso"]
    torso(d, (x0 + x1) / 2, (y0 + y1) / 2, design, CELL)
    tx0, ty0, tx1, ty1 = PARTS["thigh"]
    tc = (tx0 + tx1) / 2
    segment(d, tc, ty1 - 40, tc, ty1 - 40 - THIGH_LEN * CELL, LEG_W * CELL)
    sx0, sy0, sx1, sy1 = PARTS["shin"]
    sc = (sx0 + sx1) / 2
    segment(d, sc, sy1 - 40, sc, sy1 - 40 - (SHIN_LEN - 0.25) * CELL, LEG_W * CELL * 0.9, foot=True)
    return img


def main():
    out = os.path.join(HERE, "guides")
    os.makedirs(out, exist_ok=True)
    for design in ("crab", "dome", "prow", "sentinel"):
        guide(design).save(os.path.join(out, design + "-guide.png"))
    with open(os.path.join(out, "parts.json"), "w") as f:
        json.dump({"canvas": [W, H], "cell": CELL, "parts": PARTS, "thighCells": THIGH_LEN, "shinCells": SHIN_LEN,
                   "jointInsetPx": 40}, f, indent=2)
    print("wrote", out)


if __name__ == "__main__":
    main()
