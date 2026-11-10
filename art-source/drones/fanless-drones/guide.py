"""Deterministic geometry guides for the fanless battle drones (concepts in ../concepts-fanless/).

Writes beside this script, each a 1024x1024 sheet of four 512x512 panels, 256 px per cell, every drone facing LEFT
(nose to -x) in strict overhead orthographic, centred on its panel:

  tt-guide.png       Tri-Tachyon wing, vectored-thrust pods (concept A)
                     escort  a long gun-spine pod, the pulse emitter barrel along its centreline, two hip nozzle cowls
                     sensor  a teardrop pod whose front is one large aperture, swept antenna blades, twin tail nozzles
                     halo    a broad octagonal pod with a twin-emitter turret and a nozzle slot at each corner
                     strike  a loitering missile: brass nose, cyan band, swept tail fins, one rear nozzle
  family-guide.png   a hub's gun drone as each tech family builds it (concept D); the fourth panel is empty
                     low     a riveted box with a stubby barrel, two jet lift pods on outriggers
                     mid     an angular pod with a small turret forward and a belly thrust grille at each corner
                     high    a narrow lifting body, the emitter on its centreline, edge fins
  swarm-guide.png    the flock's swarm dart, once per livery side by side so both are one machine (top row only)
                     a small stamped dart with a short barrel, a sensor lens, two tail fins and one nozzle

Nothing round drives any of them: no fans, rotors or ducts. Flat colours say what each region is, not its material.
PANELS is shared with pack.py, which cuts the painted sheets into sprites.
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
PX = 256
PANEL = 512
HULL = "#d9dde2"
SEAM = "#7d858f"
DARK = "#2b2e33"
GLASS = "#1c2b3a"
ACCENT = "#36c6e8"
NOZZLE = "#4a5058"
WARHEAD = "#c9b26a"
HAZARD = "#d8b02a"
LENS = "#d23a2a"

# Sheet -> role -> (panel column, panel row). pack.py reads this.
PANELS = {
    "tt": {"escort": (0, 0), "sensor": (1, 0), "halo": (0, 1), "strike": (1, 1)},
    "family": {"low": (0, 0), "mid": (1, 0), "high": (0, 1)},
    "swarm": {"domain": (0, 0), "contingency": (1, 0)},
}
# Drawn this many times larger than its cell size, for detail; pack.py scales every sprite to its own pixel size.
SWARM_SCALE = 1.6


def c(v):
    return round(v * PX)


def poly(d, cx, cy, pts, fill):
    d.polygon([(cx + c(x), cy + c(y)) for x, y in pts], fill=fill)


def rect(d, cx, cy, x0, y0, x1, y1, fill):
    d.rectangle((cx + c(x0), cy + c(y0), cx + c(x1), cy + c(y1)), fill=fill)


def mirror(pts):
    """A half outline (y >= 0, nose first) closed into a symmetric polygon."""
    return pts + [(x, -y) for x, y in reversed(pts)]


def nozzle(d, cx, cy, x, y, w, h):
    """A thrust nozzle cowl seen from above: a dark block with a cyan exhaust slot at its aft end."""
    rect(d, cx, cy, x, y - h / 2, x + w, y + h / 2, NOZZLE)
    rect(d, cx, cy, x + w * .7, y - h * .35, x + w, y + h * .35, ACCENT)


# ---- Tri-Tachyon vectored-thrust pods -------------------------------------------------------------------------

def escort(d, cx, cy):
    poly(d, cx, cy, mirror([(-.36, .05), (-.14, .13), (.18, .15), (.3, .22), (.42, .22), (.46, .1)]), HULL)
    for side in (-1, 1):
        poly(d, cx, cy, [(.2, side * .16), (.3, side * .33), (.5, side * .33), (.48, side * .17)], NOZZLE)
        rect(d, cx, cy, .44, min(side * .2, side * .3), .5, max(side * .2, side * .3), ACCENT)
    rect(d, cx, cy, -.62, -.035, -.1, .035, DARK)  # emitter barrel along the spine
    rect(d, cx, cy, -.1, -.03, .3, .03, SEAM)
    rect(d, cx, cy, -.64, -.04, -.6, .04, ACCENT)
    for x in (-.02, .12):
        rect(d, cx, cy, x, -.02, x + .05, .02, ACCENT)


def sensor(d, cx, cy):
    poly(d, cx, cy, mirror([(-.48, .06), (-.3, .2), (0, .27), (.26, .24), (.36, .14)]), HULL)
    poly(d, cx, cy, mirror([(-.42, .05), (-.3, .14), (-.08, .14), (-.02, .05)]), GLASS)  # the aperture
    rect(d, cx, cy, -.24, -.025, -.19, .025, ACCENT)
    for side in (-1, 1):
        poly(d, cx, cy, [(.04, side * .2), (.5, side * .44), (.52, side * .4), (.1, side * .17)], SEAM)
        nozzle(d, cx, cy, .34, side * .07, .14, .09)


def halo(d, cx, cy):
    poly(d, cx, cy, [(-.3, -.18), (-.2, -.32), (.24, -.32), (.34, -.2), (.34, .2), (.24, .32), (-.2, .32),
                     (-.3, .18)], HULL)
    rect(d, cx, cy, -.16, -.15, .14, .15, SEAM)  # turret
    for y in (-.06, .06):
        rect(d, cx, cy, -.5, y - .03, -.14, y + .03, DARK)
        rect(d, cx, cy, -.52, y - .035, -.47, y + .035, ACCENT)
    for x, y in ((-.24, -.26), (-.24, .26), (.28, -.26), (.28, .26)):
        rect(d, cx, cy, x - .05, y - .04, x + .05, y + .04, ACCENT)


def strike(d, cx, cy):
    poly(d, cx, cy, mirror([(-.56, .0), (-.46, .06), (-.34, .09)]), WARHEAD)
    rect(d, cx, cy, -.34, -.09, .4, .09, HULL)
    rect(d, cx, cy, -.34, -.09, -.29, .09, ACCENT)
    for side in (-1, 1):
        poly(d, cx, cy, [(.1, side * .09), (.34, side * .28), (.42, side * .28), (.36, side * .09)], SEAM)
    nozzle(d, cx, cy, .4, 0, .12, .12)


# ---- a hub's drone in each tech family, and the swarm dart -----------------------------------------------------

def low(d, cx, cy):
    for side in (-1, 1):
        rect(d, cx, cy, -.04, min(side * .14, side * .24), .1, max(side * .14, side * .24), SEAM)  # outrigger
        rect(d, cx, cy, -.16, side * .34 - .08, .26, side * .34 + .08, NOZZLE)  # jet lift pod
        rect(d, cx, cy, -.12, side * .34 - .05, .2, side * .34 + .05, HAZARD)
    rect(d, cx, cy, -.3, -.18, .3, .18, HULL)
    rect(d, cx, cy, -.38, -.08, -.22, .08, SEAM)  # gun mount
    rect(d, cx, cy, -.56, -.03, -.36, .03, DARK)


def mid(d, cx, cy):
    poly(d, cx, cy, mirror([(-.36, .1), (-.26, .22), (.24, .22), (.34, .12)]), HULL)
    for x in (-.22, .18):
        for side in (-1, 1):
            rect(d, cx, cy, x - .06, side * .17 - .04, x + .06, side * .17 + .04, NOZZLE)
    rect(d, cx, cy, -.3, -.08, -.14, .08, SEAM)  # turret
    rect(d, cx, cy, -.54, -.025, -.28, .025, DARK)


def high(d, cx, cy):
    poly(d, cx, cy, mirror([(-.5, .03), (-.3, .1), (.1, .14), (.42, .12), (.46, .05)]), HULL)
    for side in (-1, 1):
        poly(d, cx, cy, [(-.12, side * .12), (.3, side * .22), (.44, side * .2), (.36, side * .12)], SEAM)
    rect(d, cx, cy, -.4, -.02, .1, .02, DARK)
    rect(d, cx, cy, -.3, -.04, -.22, .04, ACCENT)


def swarm(d, cx, cy):
    global PX
    base, PX = PX, round(PX * SWARM_SCALE)
    try:
        _swarm(d, cx, cy)
    finally:
        PX = base


def _swarm(d, cx, cy):
    poly(d, cx, cy, mirror([(-.24, .04), (-.12, .1), (.16, .1), (.24, .05)]), HULL)
    for side in (-1, 1):
        poly(d, cx, cy, [(.08, side * .1), (.26, side * .2), (.3, side * .17), (.22, side * .08)], SEAM)
    rect(d, cx, cy, -.4, -.018, -.22, .018, DARK)
    rect(d, cx, cy, -.12, -.03, -.06, .03, LENS)
    rect(d, cx, cy, -.02, -.07, .1, .07, HAZARD)
    nozzle(d, cx, cy, .22, 0, .08, .07)


DRAW = {"escort": escort, "sensor": sensor, "halo": halo, "strike": strike,
        "low": low, "mid": mid, "high": high, "domain": swarm, "contingency": swarm}


def main():
    for sheet, panels in PANELS.items():
        img = Image.new("RGBA", (PANEL * 2, PANEL * 2), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        for role, (col, row) in panels.items():
            DRAW[role](d, col * PANEL + PANEL // 2, row * PANEL + PANEL // 2)
        img.save(HERE / f"{sheet}-guide.png")


if __name__ == "__main__":
    main()
