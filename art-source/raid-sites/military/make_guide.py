"""Geometry guide for the military base's doodads (`military-base` sheet): the pieces ImageGen paints, each in its own
frame at 160 px a cell on a white canvas. The parade square's painted markings are drawn by `export.py` itself.

Light is from the north-west, so shadows fall to the south-east, inside each frame.

Usage: python art-source/raid-sites/military/make_guide.py   (writes guide.png beside it)
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
CELL = 160
W, H = 1536, 768
# (id, x, y, cells w, cells h)
FRAMES = (
    ("watchtower", 96, 144, 3, 3),
    ("saluting-base", 672, 224, 3, 2),
    ("flag-mast", 1248, 304, 1, 1),
)
BODY, EDGE, DARK, LIGHT = (128, 132, 120), (70, 74, 66), (40, 42, 40), (176, 180, 168)
SAND = (186, 166, 120)
SHADOW = (150, 150, 150)


def watchtower(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.polygon([f(.3, .3), f(.98, .62), f(.98, .98), f(.62, .98), f(.3, .7)], fill=SHADOW)
    for cx, cy in ((.14, .14), (.86, .14), (.14, .86), (.86, .86)):
        d.rectangle((*f(cx - .04, cy - .04), *f(cx + .04, cy + .04)), fill=DARK)
    d.rectangle((*f(.1, .1), *f(.9, .9)), fill=BODY, outline=EDGE, width=6)
    for k in range(12):
        t = .1 + k * .8 / 12
        for box in ((t, .1, t + .8 / 12, .18), (t, .82, t + .8 / 12, .9), (.1, t, .18, t + .8 / 12),
                    (.82, t, .9, t + .8 / 12)):
            d.ellipse((*f(box[0], box[1]), *f(box[2], box[3])), fill=SAND, outline=EDGE, width=2)
    d.rectangle((*f(.32, .32), *f(.68, .68)), fill=LIGHT, outline=EDGE, width=6)
    d.line((*f(.32, .32), *f(.68, .68)), fill=EDGE, width=3)
    d.line((*f(.68, .32), *f(.32, .68)), fill=EDGE, width=3)
    d.ellipse((*f(.2, .2), *f(.3, .3)), fill=(220, 220, 200), outline=DARK, width=4)
    for k in range(5):
        yy = .9 + k * .02
        d.line((*f(.44, yy), *f(.56, yy)), fill=DARK, width=4)
    d.line((*f(.44, .9), *f(.44, 1.0)), fill=DARK, width=4)
    d.line((*f(.56, .9), *f(.56, 1.0)), fill=DARK, width=4)


def saluting_base(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.polygon([f(.12, .2), f(.97, .3), f(.97, .97), f(.12, .9)], fill=SHADOW)
    d.rectangle((*f(.06, .12), *f(.9, .8)), fill=LIGHT, outline=EDGE, width=6)
    for k in range(3):
        d.rectangle((*f(.3 + k * .02, .8 + k * .05), *f(.66 - k * .02, .85 + k * .05)), fill=BODY, outline=EDGE,
                    width=3)
    d.line((*f(.1, .18), *f(.86, .18)), fill=DARK, width=6)
    d.rectangle((*f(.44, .3), *f(.54, .42)), fill=BODY, outline=EDGE, width=4)


def flag_mast(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.line((*f(.3, .36), *f(.94, .94)), fill=SHADOW, width=6)
    d.polygon([f(.8, .78), f(.95, .8), f(.9, .92)], fill=SHADOW)
    d.ellipse((*f(.16, .22), *f(.44, .5)), fill=LIGHT, outline=EDGE, width=4)
    d.ellipse((*f(.26, .32), *f(.34, .4)), fill=DARK)
    d.polygon([f(.32, .3), f(.82, .4), f(.34, .5)], fill=(170, 150, 70), outline=EDGE)


DRAW = {"watchtower": watchtower, "saluting-base": saluting_base, "flag-mast": flag_mast}

# Round 2 (`military-base-art-2`): the structures a working base needs, on a guide of their own (`guide-2.png`).
W2, H2 = 1696, 992
FRAMES2 = (
    ("satcom-dish", 64, 64, 3, 3),
    ("radar-tower", 608, 64, 3, 3),
    ("fuel-bladder", 1152, 64, 3, 3),
    ("generator-set", 64, 608, 3, 2),
    ("antenna-mast", 608, 608, 1, 1),
)


def satcom_dish(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.ellipse((*f(.38, .4), *f(.98, .9)), fill=SHADOW)                       # the dish's shadow, south-east
    d.rectangle((*f(.08, .08), *f(.92, .92)), fill=LIGHT, outline=EDGE, width=6)   # concrete pad
    d.rectangle((*f(.12, .12), *f(.88, .88)), outline=DARK, width=4)              # low railing
    d.ellipse((*f(.42, .42), *f(.58, .58)), fill=DARK)                            # pedestal
    d.ellipse((*f(.16, .2), *f(.7, .64)), fill=BODY, outline=EDGE, width=6)       # the dish, tilted up to the NW
    d.ellipse((*f(.26, .3), *f(.6, .54)), outline=EDGE, width=3)
    for a, b in ((.22, .26), (.64, .28), (.43, .6)):                              # feed struts
        d.line((*f(a, b), *f(.4, .4)), fill=DARK, width=4)
    d.ellipse((*f(.37, .37), *f(.45, .45)), fill=DARK)                            # feed horn


def radar_tower(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.polygon([f(.36, .36), f(.64, .36), f(.98, .82), f(.98, .98), f(.8, .98), f(.36, .64)], fill=SHADOW)
    d.polygon([f(.1, .5), f(.94, .44), f(.98, .58), f(.18, .62)], fill=SHADOW)    # the antenna bar's shadow
    for cx, cy in ((.32, .32), (.68, .32), (.32, .68), (.68, .68)):              # lattice legs, splayed
        d.rectangle((*f(cx - .03, cy - .03), *f(cx + .03, cy + .03)), fill=DARK)
    for a, b, c, e in ((.32, .32, .68, .68), (.68, .32, .32, .68), (.32, .32, .68, .32), (.68, .32, .68, .68),
                       (.68, .68, .32, .68), (.32, .68, .32, .32)):
        d.line((*f(a, b), *f(c, e)), fill=EDGE, width=5)                          # bracing
    d.rectangle((*f(.4, .4), *f(.6, .6)), fill=BODY, outline=EDGE, width=5)       # top platform
    d.polygon([f(.04, .42), f(.92, .36), f(.94, .46), f(.06, .52)], fill=LIGHT, outline=EDGE)  # the search antenna
    d.ellipse((*f(.46, .4), *f(.54, .48)), fill=DARK)                             # rotator


def fuel_bladder(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.rounded_rectangle((*f(.04, .04), *f(.96, .96)), radius=.1 * w, fill=SAND, outline=EDGE, width=4)  # earth bund
    d.rounded_rectangle((*f(.13, .13), *f(.87, .87)), radius=.06 * w, fill=(200, 196, 184))             # bund floor
    d.rounded_rectangle((*f(.2, .32), *f(.84, .78)), radius=.12 * w, fill=SHADOW)
    d.rounded_rectangle((*f(.17, .24), *f(.8, .72)), radius=.12 * w, fill=BODY, outline=EDGE, width=5)  # the bladder
    d.line((*f(.8, .48), *f(.88, .82)), fill=DARK, width=8)                                             # hose
    d.rectangle((*f(.82, .82), *f(.94, .94)), fill=DARK)                                                # pump


def generator_set(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.rectangle((*f(.1, .2), *f(.98, .98)), fill=SHADOW)
    d.rectangle((*f(.04, .1), *f(.92, .86)), fill=EDGE)                           # skid
    d.rectangle((*f(.08, .16), *f(.66, .8)), fill=BODY, outline=DARK, width=5)    # olive housing
    for k in range(5):
        yy = .26 + k * .1
        d.line((*f(.14, yy), *f(.34, yy)), fill=DARK, width=4)                    # louvres
    d.ellipse((*f(.5, .2), *f(.6, .35)), fill=DARK)                               # exhaust stack
    d.rounded_rectangle((*f(.7, .22), *f(.88, .74)), radius=.04 * w, fill=LIGHT, outline=DARK, width=4)  # fuel tank
    for yy in (.6, .68):
        d.line((*f(.66, yy), *f(.98, yy + .2)), fill=DARK, width=5)               # cable runs


def antenna_mast(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.line((*f(.42, .42), *f(.98, .98)), fill=SHADOW, width=5)                    # the mast's long shadow
    for a, b in ((.08, .1), (.9, .22), (.24, .92)):
        d.line((*f(.4, .4), *f(a, b)), fill=EDGE, width=2)                        # guy lines
        d.rectangle((*f(a - .04, b - .04), *f(a + .04, b + .04)), fill=DARK)      # anchors
    d.ellipse((*f(.33, .33), *f(.47, .47)), fill=BODY, outline=DARK, width=3)     # mast base and tip


W3, H3 = 2016, 640
FRAMES3 = (
    ("parked-ambulance", 64, 144, 3, 2),
    ("workshop-gantry", 608, 64, 4, 3),
    ("tent-khaki", 1312, 64, 4, 3),
)


def parked_ambulance(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.rounded_rectangle((*f(.1, .26), *f(.98, .94)), radius=.06 * h, fill=SHADOW)
    d.rounded_rectangle((*f(.04, .2), *f(.32, .8)), radius=.1 * h, fill=BODY, outline=EDGE, width=5)   # cab, nose west
    d.rectangle((*f(.06, .28), *f(.12, .72)), fill=DARK)                                                # windscreen
    d.rectangle((*f(.3, .12), *f(.94, .86)), fill=BODY, outline=EDGE, width=6)                          # box body
    d.rectangle((*f(.5, .24), *f(.78, .74)), fill=LIGHT, outline=EDGE, width=4)                         # roof panel
    d.rectangle((*f(.6, .3), *f(.68, .68)), fill=(235, 235, 235))                                       # its cross
    d.rectangle((*f(.53, .44), *f(.75, .54)), fill=(235, 235, 235))


def workshop_gantry(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.polygon([f(.08, .5), f(.92, .5), f(.98, .62), f(.14, .62)], fill=SHADOW)                       # beam shadow
    d.rectangle((*f(.08, .03), *f(.5, .15)), fill=BODY, outline=EDGE, width=4)                         # tool bench
    for ex in (.1, .9):                                                                                 # A-frame ends
        d.line((*f(ex, .2), *f(ex, .46)), fill=EDGE, width=10)
        d.line((*f(ex, .54), *f(ex, .8)), fill=EDGE, width=10)
        for ey in (.2, .8):
            d.rectangle((*f(ex - .025, ey - .03), *f(ex + .025, ey + .03)), fill=DARK)                 # feet
    d.rectangle((*f(.39, .34), *f(.61, .7)), fill=SHADOW)                                              # its shadow
    d.rectangle((*f(.37, .3), *f(.57, .64)), fill=BODY, outline=EDGE, width=5)    # engine block, hung under the hoist
    d.rectangle((*f(.08, .46), *f(.92, .54)), fill=LIGHT, outline=EDGE, width=4)                       # the I-beam
    d.rectangle((*f(.42, .43), *f(.52, .57)), fill=DARK)                                               # hoist trolley


def tent_khaki(d, x, y, w, h):
    f = lambda a, b: (x + a * w, y + b * h)  # noqa: E731
    d.polygon([f(.14, .3), f(.92, .3), f(.98, .9), f(.2, .9)], fill=SHADOW)
    d.rectangle((*f(.1, .2), *f(.86, .5)), fill=LIGHT, outline=EDGE, width=4)                          # sunlit slope
    d.rectangle((*f(.1, .5), *f(.86, .8)), fill=BODY, outline=EDGE, width=4)                           # shaded slope
    d.line((*f(.1, .5), *f(.86, .5)), fill=DARK, width=6)                                              # ridge
    d.polygon([f(.86, .38), f(.95, .5), f(.86, .62)], fill=DARK)                                       # door flap, east
    for a in (.2, .4, .6, .78):                                                                        # guy ropes, pegs
        for b0, b1 in ((.2, .05), (.8, .95)):
            d.line((*f(a, b0), *f(a, b1)), fill=EDGE, width=2)
            d.rectangle((*f(a - .01, b1 - .015), *f(a + .01, b1 + .015)), fill=DARK)


DRAW3 = {"parked-ambulance": parked_ambulance, "workshop-gantry": workshop_gantry, "tent-khaki": tent_khaki}

DRAW2 = {"satcom-dish": satcom_dish, "radar-tower": radar_tower, "fuel-bladder": fuel_bladder,
         "generator-set": generator_set, "antenna-mast": antenna_mast}


def main():
    im = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(im)
    for pid, x, y, cw, ch in FRAMES:
        DRAW[pid](d, x, y, cw * CELL, ch * CELL)
    im.save(HERE / "guide.png")
    im = Image.new("RGB", (W2, H2), (255, 255, 255))
    d = ImageDraw.Draw(im)
    for pid, x, y, cw, ch in FRAMES2:
        DRAW2[pid](d, x, y, cw * CELL, ch * CELL)
    im.save(HERE / "guide-2.png")
    im = Image.new("RGB", (W3, H3), (255, 255, 255))
    d = ImageDraw.Draw(im)
    for pid, x, y, cw, ch in FRAMES3:
        DRAW3[pid](d, x, y, cw * CELL, ch * CELL)
    im.save(HERE / "guide-3.png")


if __name__ == "__main__":
    main()
