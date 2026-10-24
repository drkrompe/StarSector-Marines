"""Geometry guide for a hardpoint's flux device: one compact capacitor and radiator that show its shield's charge.

One sheet per tech family, 1536x1024, the same frame as the hardpoint guide (768x768 frames at 256 px a cell, 3
cells, top at y=128), sun from the upper left. The device is a single unit about one cell by two bolted on the west
wall of the 3x3 plinth, clear of the gun head:

  north  the capacitor, with six charge-lamp sockets in a ring round it (UnitRenderService lights them one by one
         as the shield charges, so they are left dark in both frames);
  south  the radiator, whose fins glow with the heat and whose vents give off the flux haze.

  frame 0  the device cold;
  frame 1  the same device at full charge: the capacitor's core window and the radiator fins glowing.

The plinth itself is not drawn; the device is a separate layer laid over it. Also writes
flux-device-<family>-emissive.png, the 768x768 mask (white where the hot frame's glow belongs) the exporter cuts the
hot layer with, so a generation that drifts a little cannot light the housing or the lamp sockets.

The capacitor centre, the lamp ring and the radiator centre are shared by every family and mirrored in
TurretFluxDevice; move them together.

Usage: python art-source/ground-turrets/make_flux_device_guide.py [<family> ...]   (brutalist, modular, composite)
"""
import math
import sys

from PIL import Image, ImageDraw, ImageFilter

from make_guide import Canvas, OUT, g

PPC = 256
TOP = 128
FAMILIES = ("brutalist", "modular", "composite")

# Shared layout, in cells of the 3x3 frame (x east, y south).
BODY = (0.05, 0.80, 0.67, 2.20)
CAP = (0.36, 1.14)
LAMP_RING = 0.215
LAMPS = 6
RADIATOR = (0.10, 1.48, 0.62, 2.13)


def lamp_points():
    """The lamp sockets, first at the top and going clockwise."""
    return [(CAP[0] + LAMP_RING * math.sin(2 * math.pi * k / LAMPS),
             CAP[1] - LAMP_RING * math.cos(2 * math.pi * k / LAMPS)) for k in range(LAMPS)]


def device(c, ox, oy, family, hot, mask=None):
    def X(x):
        return ox + x * PPC

    def Y(y):
        return oy + y * PPC

    def lit(box):
        if mask is not None:
            mask.rectangle([X(box[0]) - ox, Y(box[1]) - oy, X(box[2]) - ox, Y(box[3]) - oy], fill=255)

    def lit_disc(cx, cy, r):
        if mask is not None:
            mask.ellipse([X(cx - r) - ox, Y(cy - r) - oy, X(cx + r) - ox, Y(cy + r) - oy], fill=255)

    x0, y0, x1, y1 = BODY
    rx0, ry0, rx1, ry1 = RADIATOR
    cx, cy = CAP
    glow = 250

    if family == "brutalist":
        # A cast slab, square-cornered; a bolted steel box for the capacitor; three heavy fin bars in a deep well.
        c.rect(X(x0), Y(y0), X(x1), Y(y1), g(150))
        c.rect(X(x0 + 0.03), Y(y0 + 0.03), X(x1 - 0.03), Y(y1 - 0.03), g(128))
        h = 0.20
        c.poly([(X(cx - h + 0.05), Y(cy - h)), (X(cx + h - 0.05), Y(cy - h)), (X(cx + h), Y(cy - h + 0.05)),
                (X(cx + h), Y(cy + h - 0.05)), (X(cx + h - 0.05), Y(cy + h)), (X(cx - h + 0.05), Y(cy + h)),
                (X(cx - h), Y(cy + h - 0.05)), (X(cx - h), Y(cy - h + 0.05))], g(176))
        c.rect(X(cx - 0.13), Y(cy - 0.13), X(cx + 0.13), Y(cy + 0.13), g(150))
        core = 0.06
        c.rect(X(cx - core), Y(cy - core), X(cx + core), Y(cy + core), g(glow if hot else 60))
        lit((cx - core, cy - core, cx + core, cy + core))
        c.rect(X(rx0), Y(ry0), X(rx1), Y(ry1), g(70))
        fins, fh = 3, 0.11
        pitch = (ry1 - ry0 - 0.08) / fins
        for k in range(fins):
            fy = ry0 + 0.06 + k * pitch
            c.rect(X(rx0 + 0.04), Y(fy), X(rx1 - 0.04), Y(fy + fh), g(glow if hot else 140))
            lit((rx0 + 0.04, fy, rx1 - 0.04, fy + fh))
        for bx, by in ((x0 + 0.06, y0 + 0.06), (x1 - 0.06, y0 + 0.06), (x0 + 0.06, y1 - 0.06), (x1 - 0.06, y1 - 0.06)):
            c.ellipse(X(bx), Y(by), 0.025 * PPC, g(90))
    elif family == "modular":
        # A bolted steel skid; a round capacitor can with a bolt ring; five fins in a rail frame.
        c.rect(X(x0), Y(y0), X(x1), Y(y1), g(150), radius=0.05 * PPC)
        c.rect(X(x0 + 0.03), Y(y0 + 0.03), X(x1 - 0.03), Y(y1 - 0.03), g(120), radius=0.04 * PPC)
        c.ellipse(X(cx), Y(cy), 0.19 * PPC, g(170))
        c.ellipse(X(cx), Y(cy), 0.15 * PPC, g(140))
        for k in range(8):
            a = 2 * math.pi * (k + 0.5) / 8
            c.ellipse(X(cx + 0.125 * math.sin(a)), Y(cy - 0.125 * math.cos(a)), 0.016 * PPC, g(95))
        core = 0.07
        c.ellipse(X(cx), Y(cy), core * PPC, g(glow if hot else 64))
        lit_disc(cx, cy, core)
        c.rect(X(rx0), Y(ry0), X(rx1), Y(ry1), g(160), radius=0.02 * PPC)
        c.rect(X(rx0 + 0.03), Y(ry0 + 0.03), X(rx1 - 0.03), Y(ry1 - 0.03), g(48))
        fins = 5
        pitch = (ry1 - ry0 - 0.08) / fins
        for k in range(fins):
            fy = ry0 + 0.05 + k * pitch
            c.rect(X(rx0 + 0.05), Y(fy), X(rx1 - 0.05), Y(fy + pitch * 0.55), g(glow if hot else 132))
            lit((rx0 + 0.05, fy, rx1 - 0.05, fy + pitch * 0.55))
    else:
        # A smooth capsule; a round pod with a glass core; seven slim glass fins.
        c.rect(X(x0), Y(y0), X(x1), Y(y1), g(170), radius=0.2 * PPC)
        c.rect(X(x0 + 0.03), Y(y0 + 0.03), X(x1 - 0.03), Y(y1 - 0.03), g(150), radius=0.17 * PPC)
        c.ellipse(X(cx), Y(cy), 0.17 * PPC, g(196))
        c.ellipse(X(cx), Y(cy), 0.12 * PPC, g(90))
        core = 0.085
        c.ellipse(X(cx), Y(cy), core * PPC, g(glow if hot else 70))
        lit_disc(cx, cy, core)
        c.rect(X(rx0), Y(ry0), X(rx1), Y(ry1), g(84), radius=0.08 * PPC)
        fins = 7
        pitch = (ry1 - ry0 - 0.10) / fins
        for k in range(fins):
            fy = ry0 + 0.06 + k * pitch
            c.rect(X(rx0 + 0.06), Y(fy), X(rx1 - 0.06), Y(fy + pitch * 0.5), g(glow if hot else 150),
                   radius=0.01 * PPC)
            lit((rx0 + 0.06, fy, rx1 - 0.06, fy + pitch * 0.5))

    # The charge lamps: dark sockets in both frames, lit at run time.
    for lx, ly in lamp_points():
        c.ellipse(X(lx), Y(ly), 0.034 * PPC, g(40))
        c.ellipse(X(lx), Y(ly), 0.022 * PPC, g(24))


def main(families):
    OUT.mkdir(parents=True, exist_ok=True)
    for family in families:
        c = Canvas()
        mask_im = Image.new("L", (768, 768), 0)
        device(c, 0, TOP, family, hot=False, mask=ImageDraw.Draw(mask_im))
        device(c, 768, TOP, family, hot=True)
        c.save(OUT / f"flux-device-{family}-guide.png")
        mask_im.filter(ImageFilter.GaussianBlur(2)).save(OUT / f"flux-device-{family}-emissive.png")


if __name__ == "__main__":
    main(sys.argv[1:] or FAMILIES)
