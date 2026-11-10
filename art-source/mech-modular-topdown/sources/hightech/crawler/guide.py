"""Geometry guide for Tri-Tachyon's light crawler (owner, from the high-tech frame concepts: "The far left crawler seems
like an interesting 'drone' concept ... light crawlers"): a small rounded carapace on four short legs.

Laid out as the derelict walker's guides are (art-source/derelict-walker/guide.py), so its parts are cut and drawn the
same way: one 1536 x 1024 canvas, 256 px a cell, flat grey ground, facing up;
  left  (0..1024)     the crawler assembled, four legs in an X, for proportion;
  right (1024..1536)  the parts the game draws, apart and upright: the carapace (top), the thigh (hip at its bottom,
                      knee at its top) and the shin (knee at its bottom, foot at its top).
The carapace is drawn bare (mech-lines.md, law 4): one empty round weapon socket at the front, where a gun mounts as
its own layer, and the shield emitter bulb behind it. export.py cuts the parts by the boxes in parts.json.

Run: python art-source/mech-modular-topdown/sources/hightech/crawler/guide.py
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
CELL = 256
W, H = 1536, 1024
GROUND = (180, 180, 180, 255)
SHELL = (150, 152, 156, 255)
MACHINE = (70, 72, 76, 255)
SOCKET = (40, 42, 46, 255)
EMITTER = (225, 228, 232, 255)
EDGE = (28, 29, 30, 255)

BODY_W, BODY_H = 1.25, 1.15          # carapace, cells
THIGH_CELLS, SHIN_CELLS = 0.42, 0.50
LEG_W = 0.30
HIP_CELLS = 0.42                     # hips on a ring this far from the centre

PARTS = {
    "torso": (1060, 40, 1500, 440),
    "thigh": (1100, 640, 1260, 1000),
    "shin": (1290, 600, 1450, 1000),
}


def c(v):
    return round(v * CELL)


def carapace(d, cx, cy):
    w, h = c(BODY_W), c(BODY_H)
    d.ellipse((cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2), fill=SHELL, outline=EDGE, width=3)
    # machinery showing between the two shell halves, down the spine
    d.rectangle((cx - c(0.08), cy - h // 2 + c(0.1), cx + c(0.08), cy + h // 2 - c(0.05)), fill=MACHINE)
    r = c(0.17)
    d.ellipse((cx - r, cy - h // 2 + c(0.12), cx + r, cy - h // 2 + c(0.12) + 2 * r), fill=SOCKET, outline=EDGE,
              width=2)
    e = c(0.12)
    ey = cy + c(0.12)
    d.ellipse((cx - e, ey - e, cx + e, ey + e), fill=EMITTER, outline=EDGE, width=2)


def segment(d, x0, y0, x1, y1, width, foot=False):
    """A leg segment from (x0, y0) to (x1, y1), drawn as a thick capsule."""
    d.line((x0, y0, x1, y1), fill=SHELL, width=c(width))
    d.ellipse((x0 - c(width) // 2, y0 - c(width) // 2, x0 + c(width) // 2, y0 + c(width) // 2), fill=MACHINE)
    if foot:
        f = c(width * 0.75)
        d.ellipse((x1 - f, y1 - f, x1 + f, y1 + f), fill=MACHINE, outline=EDGE, width=2)


def main():
    img = Image.new("RGBA", (W, H), GROUND)
    d = ImageDraw.Draw(img)
    cx, cy = 512, 512
    for angle in (45, 135, 225, 315):
        a = math.radians(angle)
        hx, hy = cx + c(HIP_CELLS) * math.cos(a), cy - c(HIP_CELLS) * math.sin(a)
        kx, ky = hx + c(THIGH_CELLS) * math.cos(a), hy - c(THIGH_CELLS) * math.sin(a)
        fx, fy = kx + c(SHIN_CELLS) * math.cos(a), ky - c(SHIN_CELLS) * math.sin(a)
        segment(d, hx, hy, kx, ky, LEG_W)
        segment(d, kx, ky, fx, fy, LEG_W * 0.85, foot=True)
    carapace(d, cx, cy)
    x0, y0, x1, y1 = PARTS["torso"]
    carapace(d, (x0 + x1) // 2, (y0 + y1) // 2)
    for name, length, width, foot in (("thigh", THIGH_CELLS, LEG_W, False), ("shin", SHIN_CELLS, LEG_W * .85, True)):
        x0, y0, x1, y1 = PARTS[name]
        mx = (x0 + x1) // 2
        bottom = y1 - c(width) // 2 - 8
        segment(d, mx, bottom, mx, bottom - c(length), width, foot)
    img.save(HERE / "guide.png")
    (HERE / "parts.json").write_text(json.dumps({
        "canvas": [W, H], "cell": CELL,
        "parts": {k: list(v) for k, v in PARTS.items()},
        "thighCells": THIGH_CELLS, "shinCells": SHIN_CELLS, "jointInsetPx": 20,
    }, indent=2))
    print("wrote guide.png and parts.json")


if __name__ == "__main__":
    main()
