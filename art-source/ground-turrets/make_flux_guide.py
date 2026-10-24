"""Geometry guide for a hardpoint's flux hardware: the capacitor banks and radiators that show its shield's charge.

One sheet, 1536x1024, the same frame as the hardpoint guide (768x768 frames at 256 px a cell, 3 cells, top at
y=128), sun from the upper left:

  frame 0  the hardware cold: a radiator bank bolted along the west and the east battered wall of the 3x3 plinth,
           each with a capacitor can at its north end and a conduit running up onto the deck edge;
  frame 1  the same hardware at full charge: the radiator fins and the cans' charge windows glowing.

The plinth itself is not drawn; the banks are a separate layer laid over it. Also writes
flux-bank-emissive.png, the 768x768 mask (one frame, white where the hot frame's glow belongs) the exporter
cuts the hot layer with, so a generation that drifts a little cannot light the frame or the cans.

Usage: python art-source/ground-turrets/make_flux_guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

from make_guide import Canvas, OUT, g

PPC = 256
TOP = 128
FINS = 9


def bank(c, ox, oy, side, hot, mask=None):
    """One bank: x0..x1 the mounting rails across the wall band, the can at the north end. side -1 west, +1 east."""
    def X(x):
        return ox + x * PPC

    def Y(y):
        return oy + y * PPC

    x0, x1 = (0.07, 0.47) if side < 0 else (2.53, 2.93)
    lit = side < 0
    rail = 168 if lit else 92
    gap = 52 if lit else 34
    fin = 200 if lit else 120
    y0, y1 = 0.78, 2.30
    # Mounting frame: two long rails and the dark well between them the fins stand in.
    c.rect(X(x0), Y(y0), X(x1), Y(y1), g(rail), radius=0.03 * PPC)
    c.rect(X(x0 + 0.04), Y(y0 + 0.05), X(x1 - 0.04), Y(y1 - 0.05), g(gap))
    pitch = (y1 - y0 - 0.14) / FINS
    for k in range(FINS):
        fy0 = y0 + 0.08 + k * pitch
        fy1 = fy0 + pitch * 0.55
        c.rect(X(x0 + 0.06), Y(fy0), X(x1 - 0.06), Y(fy1), g(250 if hot else fin))
        if mask is not None:
            mask.rectangle([X(x0 + 0.06) - ox, Y(fy0) - oy, X(x1 - 0.06) - ox, Y(fy1) - oy], fill=255)
    # Capacitor can: a squat cylinder seen end-on, a bolted rim and a round charge window in its cap.
    cx, cy = (x0 + x1) / 2, 0.50
    c.ellipse(X(cx), Y(cy), 0.19 * PPC, g(rail - 30))
    c.ellipse(X(cx), Y(cy), 0.16 * PPC, g(rail))
    for bx, by in ((-0.11, -0.11), (0.11, -0.11), (-0.11, 0.11), (0.11, 0.11)):
        c.ellipse(X(cx + bx), Y(cy + by), 0.022 * PPC, g(rail - 50))
    c.ellipse(X(cx), Y(cy), 0.075 * PPC, g(250 if hot else gap + 20))
    if mask is not None:
        r = 0.075 * PPC
        mask.ellipse([X(cx) - ox - r, Y(cy) - oy - r, X(cx) - ox + r, Y(cy) - oy + r], fill=255)
    # Strap from the can down onto the bank, and the conduit up onto the deck edge.
    c.rect(X(cx - 0.05), Y(cy + 0.15), X(cx + 0.05), Y(y0 + 0.02), g(rail - 20))
    dx0, dx1 = (x1 - 0.02, 0.62) if side < 0 else (2.38, x0 + 0.02)
    c.rect(X(dx0), Y(cy - 0.04), X(dx1), Y(cy + 0.04), g(rail - 40))


def main():
    c = Canvas()
    mask_im = Image.new("L", (768, 768), 0)
    mask = ImageDraw.Draw(mask_im)
    for side in (-1, 1):
        bank(c, 0, TOP, side, hot=False, mask=mask)
        bank(c, 768, TOP, side, hot=True)
    OUT.mkdir(parents=True, exist_ok=True)
    c.save(OUT / "flux-bank-guide.png")
    mask_im.filter(ImageFilter.GaussianBlur(2)).save(OUT / "flux-bank-emissive.png")


if __name__ == "__main__":
    main()
