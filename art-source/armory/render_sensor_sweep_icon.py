"""Procedural placeholder for the sensor-sweep family icon.

The other two family icons are ImageGen renders; this one is drawn and should be
replaced by a master when one is generated. It reads as a hooded sensor head
throwing a swept active return, in the cyan accent the armory icons share.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parent.parent
OUT_PATH = REPOSITORY / "mod" / "graphics" / "ui" / "armory" / "system-sensor-sweep.png"

S = 2048          # supersampled, downscaled to 512 at the end
OUT = 512
C = S // 2

STEEL_HI = (222, 227, 234)
STEEL_MID = (128, 137, 150)
STEEL_LO = (48, 54, 66)
STEEL_DK = (22, 26, 34)
CYAN = (64, 209, 255)
CYAN_DIM = (30, 132, 172)

img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# Emitter sits at the crown of the hood; everything sweeps upward from it.
EX, EY = C, int(S * 0.615)
# PIL angles run clockwise from +x with y downward, so "up" is 180..360.
A0, A1 = 202, 338


def arc_band(r_out, r_in, a0, a1, fill, steps=220):
    pts = []
    for i in range(steps + 1):
        a = math.radians(a0 + (a1 - a0) * i / steps)
        pts.append((EX + r_out * math.cos(a), EY + r_out * math.sin(a)))
    for i in range(steps, -1, -1):
        a = math.radians(a0 + (a1 - a0) * i / steps)
        pts.append((EX + r_in * math.cos(a), EY + r_in * math.sin(a)))
    d.polygon(pts, fill=fill)


# --- the return wedge: light fading outward ---------------------------------
glow = Image.new('RGBA', (S, S), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
for i in range(56):
    t = i / 55.0
    r = int(S * 0.10 + t * S * 0.50)
    a = int(72 * (1.0 - t) ** 1.6)
    gd.pieslice([EX - r, EY - r, EX + r, EY + r], start=A0, end=A1, fill=CYAN + (a,))
glow = glow.filter(ImageFilter.GaussianBlur(S // 80))
img.alpha_composite(glow)

# --- three return arcs, brightest nearest the emitter ------------------------
for rad, alpha, w in [(0.24, 240, 0.019), (0.38, 170, 0.015), (0.52, 105, 0.012)]:
    r_out = S * rad
    arc_band(r_out, r_out - S * w, A0 + 6, A1 - 6, CYAN + (alpha,))

# --- the hood: a rounded sensor head, plated, top-lit ------------------------
hood_w, hood_h = int(S * 0.46), int(S * 0.34)
hx0, hy0 = C - hood_w // 2, int(S * 0.60)
hx1, hy1 = hx0 + hood_w, hy0 + hood_h
radius = int(S * 0.11)

body = Image.new('RGBA', (S, S), (0, 0, 0, 0))
bd = ImageDraw.Draw(body)
for i in range(hood_h):
    t = i / float(hood_h)
    k = (1 - t) ** 1.4
    shade = tuple(int(STEEL_HI[c] * k + STEEL_LO[c] * (1 - k)) for c in range(3))
    bd.line([(hx0, hy0 + i), (hx1, hy0 + i)], fill=shade + (255,))
mask = Image.new('L', (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([hx0, hy0, hx1, hy1], radius=radius, fill=255)
img.paste(body, (0, 0), mask)

d.rounded_rectangle([hx0, hy0, hx1, hy1], radius=radius,
                    outline=STEEL_DK + (255,), width=int(S * 0.011))
d.rounded_rectangle([hx0 + int(S * 0.024), hy0 + int(S * 0.024),
                     hx1 - int(S * 0.024), hy1 - int(S * 0.024)],
                    radius=int(S * 0.085), outline=STEEL_MID + (140,),
                    width=int(S * 0.004))

# --- the visor: a dark inset band with a live cyan line ----------------------
vx0, vx1 = hx0 + int(S * 0.060), hx1 - int(S * 0.060)
vy0 = hy0 + int(S * 0.135)
vy1 = vy0 + int(S * 0.100)
d.rounded_rectangle([vx0, vy0, vx1, vy1], radius=int(S * 0.034),
                    fill=(11, 14, 19, 255), outline=STEEL_DK + (255,),
                    width=int(S * 0.006))
visor = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(visor).rounded_rectangle(
    [vx0 + int(S * 0.015), vy0 + int(S * 0.029),
     vx1 - int(S * 0.015), vy1 - int(S * 0.029)],
    radius=int(S * 0.015), fill=CYAN + (220,))
visor = visor.filter(ImageFilter.GaussianBlur(S // 200))
img.alpha_composite(visor)

# --- chin block, so the shape reads as worn kit rather than a screen ---------
cw = int(S * 0.20)
d.rounded_rectangle([C - cw // 2, hy1 - int(S * 0.010),
                     C + cw // 2, hy1 + int(S * 0.045)],
                    radius=int(S * 0.018), fill=STEEL_LO + (255,),
                    outline=STEEL_DK + (255,), width=int(S * 0.007))

# --- emitter node at the crown, where the sweep originates -------------------
er = int(S * 0.050)
d.ellipse([EX - er, EY - er, EX + er, EY + er],
          fill=STEEL_MID + (255,), outline=STEEL_DK + (255,), width=int(S * 0.009))
ir = int(er * 0.50)
node = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(node).ellipse([EX - ir, EY - ir, EX + ir, EY + ir], fill=CYAN + (255,))
node = node.filter(ImageFilter.GaussianBlur(S // 240))
img.alpha_composite(node)

# --- two antenna stubs flanking the crown ------------------------------------
for sign in (-1, 1):
    ax = C + sign * int(S * 0.180)
    top = hy0 - int(S * 0.070)
    d.polygon([(ax - int(S * 0.015), hy0 + int(S * 0.020)),
               (ax + int(S * 0.015), hy0 + int(S * 0.020)),
               (ax + int(S * 0.006), top),
               (ax - int(S * 0.006), top)],
              fill=STEEL_MID + (255,), outline=STEEL_DK + (255,), width=int(S * 0.005))
    d.ellipse([ax - int(S * 0.014), top - int(S * 0.026),
               ax + int(S * 0.014), top + int(S * 0.002)], fill=CYAN_DIM + (255,))

img.resize((OUT, OUT), Image.LANCZOS).save(OUT_PATH)
print('wrote', OUT_PATH)
