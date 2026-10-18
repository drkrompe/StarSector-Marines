"""Deterministic geometry guide for Tri-Tachyon's ground drones (escort, sensor, point-defence halo, strike).

Writes drones-guide.png beside this script: a 1024x1024 sheet of four 512x512 panels, 256 px per cell, every drone
facing LEFT (nose to -x) in strict overhead orthographic, centred on its panel:

  top-left  escort   twin side-by-side ducted fans either side of a body, a pulse emitter nose
  top-right sensor   one large ducted fan, a forward sensor boom and a large eye dome
  bottom-left halo   one ducted fan ringed by three small point-defence laser nubs on the shroud rim
  bottom-right strike a dart: long warhead body, a rear ducted fan, short canards

Flat colours are placeholders that say what each region is, not material. Panel order and sizes are shared with
pack.py, which cuts the painted sheet into one sprite per role.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
PX = 256
PANEL = 512
SHROUD = "#d9dde2"
SEAM = "#7d858f"
FAN = "#3a4048"
HUB = "#9aa2ab"
GLASS = "#1c2b3a"
ACCENT = "#36c6e8"
DARK = "#2b2e33"
WARHEAD = "#c9b26a"

# Role -> (panel column, panel row, long-axis length in cells). pack.py reads this.
PANELS = {"escort": (0, 0, 1.15), "sensor": (1, 0, 1.0), "halo": (0, 1, 0.9), "strike": (1, 1, 1.05)}


def c(v):
    return round(v * PX)


def fan(d, cx, cy, r, blades=7):
    """A shrouded lift fan: pale shroud ring, dark duct, blades, hub."""
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=SHROUD)
    ri = r * .78
    d.ellipse((cx - ri, cy - ri, cx + ri, cy + ri), fill=FAN)
    for k in range(blades):
        a = 2 * math.pi * k / blades
        d.line((cx, cy, cx + ri * .95 * math.cos(a), cy + ri * .95 * math.sin(a)), fill=SEAM, width=5)
    rh = r * .22
    d.ellipse((cx - rh, cy - rh, cx + rh, cy + rh), fill=HUB)


def escort(d, cx, cy):
    # Central body, a twin fan pair either side, a pulse emitter nose to the left.
    d.rounded_rectangle((cx - c(.42), cy - c(.16), cx + c(.36), cy + c(.16)), radius=c(.1), fill=SHROUD)
    for side in (-1, 1):
        y0, y1 = sorted((cy + side * c(.12), cy + side * c(.3)))
        d.rectangle((cx - c(.08), y0, cx + c(.12), y1), fill=SEAM)
        fan(d, cx + c(.02), cy + side * c(.36), c(.24))
    d.rectangle((cx - c(.58), cy - c(.035), cx - c(.4), cy + c(.035)), fill=DARK)
    d.ellipse((cx - c(.62), cy - c(.04), cx - c(.54), cy + c(.04)), fill=ACCENT)
    d.ellipse((cx - c(.3), cy - c(.07), cx - c(.16), cy + c(.07)), fill=GLASS)
    d.ellipse((cx - c(.255), cy - c(.025), cx - c(.205), cy + c(.025)), fill=ACCENT)
    d.line((cx + c(.1), cy, cx + c(.32), cy), fill=ACCENT, width=4)


def sensor(d, cx, cy):
    fan(d, cx + c(.1), cy, c(.36), blades=9)
    # Forward boom carrying the sensor head.
    d.rectangle((cx - c(.36), cy - c(.045), cx - c(.12), cy + c(.045)), fill=SEAM)
    d.ellipse((cx - c(.52), cy - c(.14), cx - c(.26), cy + c(.14)), fill=SHROUD)
    d.ellipse((cx - c(.47), cy - c(.09), cx - c(.31), cy + c(.09)), fill=GLASS)
    d.ellipse((cx - c(.43), cy - c(.04), cx - c(.35), cy + c(.04)), fill=ACCENT)
    # Two short antenna whiskers aft.
    for side in (-1, 1):
        d.line((cx + c(.38), cy + side * c(.12), cx + c(.5), cy + side * c(.2)), fill=SEAM, width=5)


def halo(d, cx, cy):
    fan(d, cx, cy, c(.34), blades=8)
    rim = c(.36)
    for k in range(3):
        a = math.pi + 2 * math.pi * k / 3
        x, y = cx + rim * math.cos(a), cy + rim * math.sin(a)
        d.ellipse((x - c(.09), y - c(.09), x + c(.09), y + c(.09)), fill=SHROUD)
        d.ellipse((x - c(.045), y - c(.045), x + c(.045), y + c(.045)), fill=ACCENT)
    d.ellipse((cx - c(.08), cy - c(.08), cx + c(.08), cy + c(.08)), fill=GLASS)


def strike(d, cx, cy):
    # Dart body, warhead nose to the left, canards, a rear ducted fan.
    nose = cx - c(.52)
    d.polygon([(nose, cy), (cx - c(.3), cy - c(.11)), (cx + c(.22), cy - c(.11)),
               (cx + c(.22), cy + c(.11)), (cx - c(.3), cy + c(.11))], fill=SHROUD)
    d.polygon([(nose, cy), (cx - c(.36), cy - c(.08)), (cx - c(.36), cy + c(.08))], fill=WARHEAD)
    d.rectangle((cx - c(.3), cy - c(.11), cx - c(.24), cy + c(.11)), fill=ACCENT)
    for side in (-1, 1):
        d.polygon([(cx - c(.16), cy + side * c(.1)), (cx - c(.02), cy + side * c(.26)),
                   (cx + c(.06), cy + side * c(.26)), (cx + c(.02), cy + side * c(.1))], fill=SEAM)
    fan(d, cx + c(.32), cy, c(.21), blades=6)


def main():
    img = Image.new("RGBA", (PANEL * 2, PANEL * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw = {"escort": escort, "sensor": sensor, "halo": halo, "strike": strike}
    for role, (col, row, _) in PANELS.items():
        draw[role](d, col * PANEL + PANEL // 2, row * PANEL + PANEL // 2)
    img.save(HERE / "drones-guide.png")


if __name__ == "__main__":
    main()
