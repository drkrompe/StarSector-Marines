"""Deterministic geometry guides for Tri-Tachyon's hover line (hover scout, drone skimmer).

Writes, beside this script, three 1792x896 guides of two 896x896 panels each, 256 px per cell, every hull facing
LEFT (nose to -x) in strict overhead orthographic, centred on its panel:

  scout-hull-guide.png    live hover scout hull | wrecked hover scout hull
  skimmer-hull-guide.png  live drone skimmer hull | wrecked drone skimmer hull
  scout-turret-guide.png  live twin pulse emitter | wrecked emitter

Flat colours are placeholders that say what each region is, not material. The hull guides leave the turret ring
empty; the turret is its own component. Registration constants are shared with pack.py.
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
PX = 256          # pixels per cell
PANEL = 896
SKIRT = "#2b2e33"
SHELL = "#d9dde2"
SEAM = "#7d858f"
GLASS = "#1c2b3a"
FAN = "#4a5058"
ACCENT = "#36c6e8"
SCORCH = "#3a3633"

SCOUT = dict(length=2.5, width=1.45)       # skirt outline, cells
SKIMMER = dict(length=3.1, width=1.85)
# The scout's turret bearing, chassis-local: 0.15 cells aft of centre (VehicleType.HOVER_SCOUT turretMountY).
SCOUT_MOUNT_AFT = 0.15


def c(v):
    return round(v * PX)


def rounded(d, cx, cy, length, width, radius, fill):
    d.rounded_rectangle((cx - c(length / 2), cy - c(width / 2), cx + c(length / 2), cy + c(width / 2)),
                        radius=c(radius), fill=fill)


def hull_plan(d, cx, cy, length, width, wreck=False, bays=0):
    """Skirt band, lozenge composite shell, canopy, two rear lift fans, sensor cluster and optional drone bays."""
    rounded(d, cx, cy, length, width, .32, SKIRT if not wreck else "#24221f")
    if wreck:
        # A torn, deflated stretch of skirt along the port side.
        d.polygon([(cx - c(.4), cy - c(width / 2)), (cx + c(.5), cy - c(width / 2)),
                   (cx + c(.35), cy - c(width / 2) + c(.12)), (cx - c(.25), cy - c(width / 2) + c(.1))],
                  fill=(0, 0, 0, 0))
    inner_l, inner_w = length - .2, width - .22
    nose = cx - c(inner_l / 2)
    tail = cx + c(inner_l / 2)
    shell = [(nose, cy), (nose + c(.42), cy - c(inner_w / 2)), (tail - c(.12), cy - c(inner_w / 2)),
             (tail, cy - c(inner_w / 2) + c(.12)), (tail, cy + c(inner_w / 2) - c(.12)),
             (tail - c(.12), cy + c(inner_w / 2)), (nose + c(.42), cy + c(inner_w / 2))]
    d.polygon(shell, fill=SHELL if not wreck else SCORCH)
    # Centre spine seam and panel lines.
    d.line((nose + c(.5), cy, tail - c(.1), cy), fill=SEAM, width=4)
    for fx in (.35, .62):
        x = nose + c(inner_l * fx)
        d.line((x, cy - c(inner_w / 2) + 8, x, cy + c(inner_w / 2) - 8), fill=SEAM, width=3)
    # Canopy wedge at the nose.
    d.polygon([(nose + c(.22), cy), (nose + c(.5), cy - c(.24)), (nose + c(.78), cy - c(.24)),
               (nose + c(.78), cy + c(.24)), (nose + c(.5), cy + c(.24))], fill=GLASS)
    # Two shrouded lift fans in the rear quarters.
    for side in (-1, 1):
        fx, fy = tail - c(.42), cy + side * c(inner_w / 2 - .3)
        r = c(.24)
        d.ellipse((fx - r, fy - r, fx + r, fy + r), fill=FAN)
        d.ellipse((fx - r + 10, fy - r + 10, fx + r - 10, fy + r - 10), fill="#262a2f")
        for k in range(6):
            d.line((fx, fy, fx + (r - 12) * [1, .5, -.5, -1, -.5, .5][k], fy + (r - 12) * [0, .87, .87, 0, -.87, -.87][k]),
                   fill="#5c636c", width=5)
    # Accent light strips along both flanks.
    if not wreck:
        for side in (-1, 1):
            y = cy + side * c(inner_w / 2 - .07)
            d.line((nose + c(.6), y, nose + c(inner_l * .55), y), fill=ACCENT, width=5)
    # Sensor cluster: a low dome forward of the turret ring, starboard of the spine.
    sx, sy = nose + c(.98), cy + c(.2)
    d.ellipse((sx - c(.13), sy - c(.13), sx + c(.13), sy + c(.13)), fill="#9aa3ad" if not wreck else "#2a2826")
    d.ellipse((sx - c(.05), sy - c(.05), sx + c(.05), sy + c(.05)), fill=ACCENT if not wreck else "#151413")
    # Drone bays: square recessed hatches across the after deck.
    for i in range(bays):
        bx = cx + c(-.25 + (i // 2) * .5)
        by = cy + (-1 if i % 2 == 0 else 1) * c(.3)
        h = c(.17)
        d.rectangle((bx - h, by - h, bx + h, by + h), fill="#5e6670" if not wreck else "#1b1a19")
        d.rectangle((bx - h + 8, by - h + 8, bx + h - 8, by + h - 8), fill="#454c55" if not wreck else "#0f0e0e")
        if not wreck:
            d.line((bx - h + 12, by, bx + h - 12, by), fill=ACCENT, width=3)
    if wreck:
        # Burn-through and a buckled panel.
        d.ellipse((cx - c(.35), cy - c(.3), cx + c(.25), cy + c(.25)), fill="#141211")
        d.polygon([(nose + c(.3), cy - c(.05)), (nose + c(.62), cy - c(.3)), (nose + c(.7), cy + c(.1))],
                  fill="#0e0d0c")


def turret(d, cx, cy, wreck=False):
    """A low rounded housing on the bearing with twin slim emitters pointing LEFT."""
    for side in (-1, 1):
        y = cy + side * c(.075)
        length = .52 if not wreck else .34
        d.rectangle((cx - c(length), y - c(.035), cx - c(.1), y + c(.035)), fill="#8c949e" if not wreck else "#2c2a28")
        d.rectangle((cx - c(length) - c(.05), y - c(.045), cx - c(length), y + c(.045)),
                    fill=ACCENT if not wreck else "#161514")
    r = c(.24)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=SHELL if not wreck else SCORCH)
    d.ellipse((cx - r + 14, cy - r + 14, cx + r - 14, cy + r - 14), fill="#b9c0c8" if not wreck else "#24211f")
    d.line((cx - c(.05), cy, cx + c(.16), cy), fill=SEAM, width=4)


def sheet(draw_panel):
    img = Image.new("RGBA", (PANEL * 2, PANEL), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i, wreck in enumerate((False, True)):
        draw_panel(d, PANEL * i + PANEL // 2, PANEL // 2, wreck)
    return img


def main():
    sheet(lambda d, x, y, w: hull_plan(d, x, y, wreck=w, **SCOUT)).save(HERE / "scout-hull-guide.png")
    sheet(lambda d, x, y, w: hull_plan(d, x, y, wreck=w, bays=4, **SKIMMER)).save(HERE / "skimmer-hull-guide.png")
    sheet(lambda d, x, y, w: turret(d, x, y, wreck=w)).save(HERE / "scout-turret-guide.png")


if __name__ == "__main__":
    main()
