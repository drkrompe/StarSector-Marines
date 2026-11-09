"""Deterministic geometry guide for the derelict swarm drone's look candidates (`derelict-drone-flocks.md`, slice 7).

Writes swarm-guide.png beside this script: a 1024x1024 sheet of four 512x512 panels, each one candidate design of the
same small expendable flier, every drone facing LEFT (nose to -x) in strict overhead orthographic, centred on its
panel. The same guide is painted twice, once per lineage livery (Domain security, contingency security), so a design
reads the same in both.

  top-left     disc    one ducted lift fan in a round shroud, a chin gun pod forward
  top-right    quad    an X frame with four small rotors and a gun pod under the hub
  bottom-left  tandem  two lift fans fore and aft on a slim spine, a gun on the nose
  bottom-right dart    a lozenge lifting body with a tail fan and a nose gun

Flat colours are placeholders that say what each region is, not material. Panel order is shared with pack.py.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
PX = 300
PANEL = 512
BODY = "#c8ccd0"
SEAM = "#7d858f"
FAN = "#3a4048"
HUB = "#9aa2ab"
GUN = "#2b2e33"
ACCENT = "#e0b830"
LENS = "#c23a2a"

# Design -> (panel column, panel row). pack.py reads this.
PANELS = {"disc": (0, 0), "quad": (1, 0), "tandem": (0, 1), "dart": (1, 1)}


def c(v):
    return round(v * PX)


def fan(d, cx, cy, r, blades=6):
    """A shrouded lift fan: shroud ring, dark duct, blades, hub."""
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=BODY)
    ri = r * .76
    d.ellipse((cx - ri, cy - ri, cx + ri, cy + ri), fill=FAN)
    for k in range(blades):
        a = 2 * math.pi * k / blades + .3
        d.line((cx, cy, cx + ri * .95 * math.cos(a), cy + ri * .95 * math.sin(a)), fill=SEAM, width=5)
    rh = r * .24
    d.ellipse((cx - rh, cy - rh, cx + rh, cy + rh), fill=HUB)


def rotor(d, cx, cy, r):
    """An open rotor: a faint disc of blur with two blades and a motor hub."""
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=SEAM, width=3)
    for a in (.4, .4 + math.pi):
        d.line((cx, cy, cx + r * .95 * math.cos(a), cy + r * .95 * math.sin(a)), fill=GUN, width=7)
    d.ellipse((cx - r * .22, cy - r * .22, cx + r * .22, cy + r * .22), fill=HUB)


def gun(d, x0, cy, length, width=.05):
    d.rectangle((x0 - c(length), cy - c(width / 2), x0, cy + c(width / 2)), fill=GUN)


def disc(d, cx, cy):
    gun(d, cx - c(.3), cy, .22)
    d.rounded_rectangle((cx - c(.42), cy - c(.09), cx - c(.22), cy + c(.09)), radius=c(.04), fill=SEAM)
    fan(d, cx + c(.04), cy, c(.36), blades=7)
    d.ellipse((cx - c(.36), cy - c(.035), cx - c(.29), cy + c(.035)), fill=LENS)
    d.arc((cx + c(.04) - c(.36), cy - c(.36), cx + c(.04) + c(.36), cy + c(.36)), 200, 340, fill=ACCENT, width=9)


def quad(d, cx, cy):
    arm = c(.32)
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        x, y = cx + arm * math.cos(r), cy + arm * math.sin(r)
        d.line((cx, cy, x, y), fill=SEAM, width=c(.06))
        rotor(d, x, y, c(.17))
    d.rounded_rectangle((cx - c(.14), cy - c(.11), cx + c(.14), cy + c(.11)), radius=c(.05), fill=BODY)
    gun(d, cx - c(.12), cy, .26)
    d.ellipse((cx - c(.1), cy - c(.04), cx - c(.02), cy + c(.04)), fill=LENS)
    d.rectangle((cx + c(.02), cy - c(.11), cx + c(.06), cy + c(.11)), fill=ACCENT)


def tandem(d, cx, cy):
    d.rounded_rectangle((cx - c(.5), cy - c(.06), cx + c(.5), cy + c(.06)), radius=c(.03), fill=SEAM)
    fan(d, cx - c(.24), cy, c(.22))
    fan(d, cx + c(.28), cy, c(.22))
    gun(d, cx - c(.48), cy, .14)
    d.ellipse((cx - c(.52), cy - c(.04), cx - c(.44), cy + c(.04)), fill=LENS)
    d.rectangle((cx + c(.0), cy - c(.06), cx + c(.04), cy + c(.06)), fill=ACCENT)


def dart(d, cx, cy):
    d.polygon([(cx - c(.55), cy), (cx - c(.05), cy - c(.26)), (cx + c(.38), cy - c(.2)),
               (cx + c(.38), cy + c(.2)), (cx - c(.05), cy + c(.26))], fill=BODY)
    fan(d, cx + c(.3), cy, c(.17))
    gun(d, cx - c(.5), cy, .12)
    d.ellipse((cx - c(.36), cy - c(.05), cx - c(.26), cy + c(.05)), fill=LENS)
    for side in (-1, 1):
        d.line((cx - c(.2), cy + side * c(.12), cx + c(.08), cy + side * c(.2)), fill=ACCENT, width=8)


DRAW = {"disc": disc, "quad": quad, "tandem": tandem, "dart": dart}


def main():
    img = Image.new("RGBA", (PANEL * 2, PANEL * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for name, (col, row) in PANELS.items():
        DRAW[name](d, col * PANEL + PANEL // 2, row * PANEL + PANEL // 2)
    img.save(HERE / "swarm-guide.png")


if __name__ == "__main__":
    main()
