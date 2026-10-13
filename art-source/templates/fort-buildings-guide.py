"""Geometry only: the battery fort's buildings, by function and by state, plus its animated and interior parts.

    python fort-buildings-guide.py guides     # writes the five guide PNGs beside this file
    python fort-buildings-guide.py banner     # writes the holder banner straight to mod/graphics/fort/

Every sheet is a 1536x1024 transparent canvas, overhead orthographic. Frames sit in fixed places so the exporter
(`art-source/fort-buildings/export.py`) can crop them mechanically; nothing is alpha-trimmed or recentred.

The three building sheets (bunker, magazine, power) hold four *panels* in a 2x2 grid of 768x512 quadrants, in slot
order  0 intact roof | 1 damaged roof | 2 destroyed (open-top wreck) | 3 lights overlay.
A panel is the building's own *interior floor rectangle* in cells (7x5 bunker, 8x6 magazine, 11x5 power plant) at one
integer pixel scale per sheet, centred in its quadrant. The runtime draws the panel over exactly those cells, turned
with the fort; the local frame has its door on the bottom edge (bunker, magazine) or the top edge (power plant).
Intact, damaged and destroyed share one registration, so a state change swaps the picture and moves nothing.

Parts sheet: six 512px squares in 3 columns x 2 rows, slot order
  0 radar dish (spins)   1 turbine fan (spins)   2 vent fan (spins)
  3 console bank         4 ordnance stacks       5 reactor core        (3x3-cell interior bases, 400px squares)
Gatehouse sheet: quadrants 0 tower roof, 1 gate leaves, 2 damaged tower roof, each 2x3 cells at 150px/cell.
"""
from pathlib import Path
import math
import sys
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

W, H = 1536, 1024
QW, QH = 768, 512

SLAB = '#6f757a'
SLAB_HI = '#8d9498'
RECESS = '#262a2e'
EDGE = '#a4a9ab'
AMBER = '#d9a21e'
RED = '#b8352a'
CYAN = '#3fb7c9'
EARTH = '#6b5a3d'
EARTH_DK = '#4c3f2a'
SCORCH = '#1f1c1a'
STEEL = '#7d858a'
HAZARD = '#c9a227'
SHELL = '#8f8a55'

# interior floor rectangles in cells, and the integer pixel scale each sheet is drawn at
BUILDINGS = {
    'bunker': (7, 5, 92),
    'magazine': (8, 6, 77),
    'power': (11, 5, 65),
}


class Panel:
    """Draws in cell units inside one building panel, centred in its quadrant."""

    def __init__(self, image, quadrant, cells_w, cells_h, scale):
        self.d = ImageDraw.Draw(image)
        self.s = scale
        pw, ph = cells_w * scale, cells_h * scale
        self.x0 = (quadrant % 2) * QW + (QW - pw) // 2
        self.y0 = (quadrant // 2) * QH + (QH - ph) // 2
        self.w, self.h = cells_w, cells_h

    def p(self, x, y):
        return (self.x0 + x * self.s, self.y0 + y * self.s)

    def rect(self, x0, y0, x1, y1, fill, outline=None, width=2):
        self.d.rectangle((*self.p(x0, y0), *self.p(x1, y1)), fill=fill, outline=outline, width=width)

    def rrect(self, x0, y0, x1, y1, r, fill, outline=None, width=2):
        self.d.rounded_rectangle((*self.p(x0, y0), *self.p(x1, y1)), r * self.s, fill=fill, outline=outline, width=width)

    def circle(self, cx, cy, r, fill, outline=None, width=2):
        a, b = self.p(cx - r, cy - r), self.p(cx + r, cy + r)
        self.d.ellipse((*a, *b), fill=fill, outline=outline, width=width)

    def poly(self, pts, fill, outline=None):
        self.d.polygon([self.p(x, y) for x, y in pts], fill=fill, outline=outline)

    def line(self, pts, fill, width=3):
        self.d.line([self.p(x, y) for x, y in pts], fill=fill, width=width)

    def chamfer(self, x0, y0, x1, y1, c, fill, outline=None):
        self.poly([(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1),
                   (x0, y1 - c), (x0, y0 + c)], fill, outline)


def shards(panel, boxes, fill, outline=EDGE):
    """Broken slab pieces: irregular quadrilaterals inside each (x0, y0, x1, y1) box."""
    for i, (x0, y0, x1, y1) in enumerate(boxes):
        dx, dy = (x1 - x0) * 0.22, (y1 - y0) * 0.22
        j = 1 if i % 2 else -1
        panel.poly([(x0 + dx, y0), (x1, y0 + dy * (1 + j * .4)), (x1 - dx, y1), (x0, y1 - dy * (1 - j * .4))],
                   fill, outline)


# ------------------------------------------------------------------------------------------------ bunker (7 x 5)

def bunker(image):
    cw, ch, s = BUILDINGS['bunker']
    # 0 intact: low hardened slab, slit apertures, hatch, vent, mast base top-right, armoured door shutter at the bottom
    p = Panel(image, 0, cw, ch, s)
    p.chamfer(0.08, 0.08, 6.92, 4.92, 0.4, SLAB, EDGE)
    p.chamfer(0.55, 0.55, 6.45, 4.1, 0.25, SLAB_HI, EDGE)
    for x0, x1 in ((0.9, 2.2), (2.9, 4.1), (4.8, 6.1)):
        p.rect(x0, 4.3, x1, 4.55, RECESS)
    p.rect(0.18, 1.4, 0.45, 2.7, RECESS)
    p.rect(6.55, 1.4, 6.82, 2.7, RECESS)
    p.rect(2.85, 4.55, 4.15, 4.95, STEEL, EDGE)
    for x in (3.1, 3.5, 3.9):
        p.rect(x, 4.62, x + 0.1, 4.9, HAZARD)
    p.rect(1.1, 1.0, 2.1, 2.0, RECESS, EDGE)
    p.rect(2.7, 1.3, 3.9, 2.4, RECESS, EDGE)
    for x in (3.0, 3.3, 3.6):
        p.line([(x, 1.4), (x, 2.3)], SLAB, 3)
    p.circle(5.4, 1.35, 0.62, RECESS, EDGE)
    p.circle(5.4, 1.35, 0.3, STEEL)
    for bx, by in ((0.35, 0.35), (6.65, 0.35), (0.35, 4.65), (6.65, 4.65)):
        p.circle(bx, by, 0.12, EDGE)
    # 1 damaged: same plate with a corner bitten out, cracks, scorch and spall
    p = Panel(image, 1, cw, ch, s)
    p.poly([(1.5, 0.08), (6.52, 0.08), (6.92, 0.48), (6.92, 4.52), (6.52, 4.92), (0.48, 4.92), (0.08, 4.52),
            (0.08, 1.3), (0.9, 1.3), (1.5, 0.7)], SLAB, EDGE)
    p.poly([(1.6, 0.9), (6.45, 0.9), (6.45, 4.1), (0.55, 4.1), (0.55, 1.8), (1.2, 1.8)], SLAB_HI, EDGE)
    for x0, x1 in ((0.9, 2.2), (2.9, 4.1), (4.8, 6.1)):
        p.rect(x0, 4.3, x1, 4.55, RECESS)
    p.rect(2.85, 4.55, 4.15, 4.95, STEEL, EDGE)
    p.circle(3.3, 2.4, 0.9, SCORCH)
    p.circle(5.4, 1.35, 0.62, RECESS, EDGE)
    p.line([(4.8, 0.9), (5.6, 1.7), (6.3, 1.5), (6.9, 2.3)], SCORCH, 4)
    p.line([(1.2, 4.1), (2.0, 3.2), (2.6, 3.4), (3.4, 2.5)], SCORCH, 4)
    p.line([(5.6, 4.1), (5.2, 3.3), (5.8, 2.8)], SCORCH, 4)
    shards(p, [(0.6, 0.6, 1.3, 1.2), (5.8, 3.5, 6.6, 4.4)], '#585d61')
    # 2 destroyed: collapsed slab, shards, rebar, a fallen mast; lots of open space
    p = Panel(image, 2, cw, ch, s)
    p.poly([(0.3, 0.6), (2.6, 0.3), (3.4, 1.1), (3.0, 2.3), (1.2, 2.5), (0.2, 1.7)], SCORCH)
    p.poly([(3.6, 2.4), (6.4, 2.0), (6.8, 3.5), (5.2, 4.6), (3.5, 4.2)], SCORCH)
    shards(p, [(0.4, 0.5, 1.9, 1.7), (2.5, 2.7, 4.0, 3.9), (4.3, 2.3, 6.0, 3.7), (0.7, 3.2, 2.1, 4.5),
               (4.9, 0.4, 6.2, 1.4), (2.0, 1.4, 3.2, 2.5)], SLAB)
    for pts in (((0.8, 4.4), (2.5, 3.2)), ((4.2, 1.0), (5.0, 2.8)), ((3.3, 4.6), (4.9, 4.2)), ((1.0, 2.3), (2.2, 1.0))):
        p.line(list(pts), STEEL, 3)
    p.line([(4.7, 0.9), (6.7, 1.9)], EDGE, 6)
    p.circle(6.7, 1.9, 0.2, STEEL)
    # 3 lights: amber slit glows and lamps on the same registration
    p = Panel(image, 3, cw, ch, s)
    for x0, x1 in ((0.9, 2.2), (2.9, 4.1), (4.8, 6.1)):
        p.rect(x0, 4.3, x1, 4.55, AMBER)
    p.rect(0.18, 1.4, 0.45, 2.7, AMBER)
    p.rect(6.55, 1.4, 6.82, 2.7, AMBER)
    p.rect(2.7, 1.3, 3.9, 2.4, '#40342a')
    for x in (3.0, 3.3, 3.6):
        p.line([(x, 1.4), (x, 2.3)], AMBER, 3)
    p.circle(5.4, 1.35, 0.14, RED)


# ---------------------------------------------------------------------------------------------- magazine (8 x 6)

def magazine(image):
    cw, ch, s = BUILDINGS['magazine']
    # 0 intact: earth berm, concrete crown, skylight grating over racked ordnance, headwall and two blast doors
    p = Panel(image, 0, cw, ch, s)
    p.rrect(0.05, 0.05, 7.95, 5.95, 1.2, EARTH, EARTH_DK)
    p.rrect(0.9, 0.8, 7.1, 4.5, 0.3, SLAB, EDGE)
    p.rect(2.3, 1.3, 5.7, 3.6, RECESS, EDGE)
    for i in range(5):
        y = 1.5 + i * 0.4
        p.rrect(2.45, y, 5.55, y + 0.28, 0.12, SHELL)
    p.rect(1.5, 4.55, 6.5, 5.95, SLAB_HI, EDGE)
    p.rect(2.1, 4.75, 3.9, 5.85, STEEL, EDGE)
    p.rect(4.1, 4.75, 5.9, 5.85, STEEL, EDGE)
    for x0 in (2.2, 4.2):
        for i in range(4):
            p.rect(x0 + i * 0.4, 4.85, x0 + i * 0.4 + 0.16, 5.75, HAZARD)
    for vx, vy in ((1.0, 1.0), (7.0, 1.0)):
        p.circle(vx, vy, 0.38, RECESS, EDGE)
        p.circle(vx, vy, 0.18, STEEL)
    # 1 damaged: slumped berm, torn grating with shells jumbled out, scorch, one door buckled
    p = Panel(image, 1, cw, ch, s)
    p.poly([(0.6, 0.2), (7.3, 0.1), (7.95, 1.0), (7.8, 5.2), (6.9, 5.95), (1.0, 5.9), (0.1, 5.1), (0.0, 1.0)],
           EARTH, EARTH_DK)
    p.poly([(1.0, 0.9), (7.0, 0.8), (7.1, 4.4), (0.9, 4.5)], SLAB, EDGE)
    p.poly([(2.3, 1.3), (4.6, 1.6), (5.7, 1.3), (5.5, 3.6), (2.6, 3.4)], RECESS, EDGE)
    for x, y, a in ((2.6, 1.8, 0), (3.5, 2.6, 1), (4.6, 2.0, 0), (3.2, 3.0, 1), (5.0, 3.0, 0)):
        p.rrect(x, y, x + 1.0, y + 0.26, 0.1, SHELL)
    p.circle(4.0, 2.4, 1.2, SCORCH)
    p.rect(1.5, 4.55, 6.5, 5.95, SLAB_HI, EDGE)
    p.rect(2.1, 4.75, 3.9, 5.85, STEEL, EDGE)
    p.poly([(4.1, 4.75), (5.9, 4.95), (5.7, 5.85), (4.2, 5.7)], STEEL, EDGE)
    p.line([(1.4, 1.3), (2.2, 2.2), (1.9, 3.2)], SCORCH, 5)
    p.line([(6.3, 1.5), (5.8, 2.6), (6.4, 3.6)], SCORCH, 5)
    # 2 destroyed: blast crater with scorched lip, scattered casings, door leaves flung out
    p = Panel(image, 2, cw, ch, s)
    pts = []
    for i in range(18):
        a = i / 18 * 2 * math.pi
        r = 3.0 + 0.5 * math.sin(i * 2.3)
        pts.append((4 + math.cos(a) * r * 1.2, 3 + math.sin(a) * r * 0.9))
    p.poly(pts, EARTH_DK)
    pts = [(4 + (x - 4) * 0.62, 3 + (y - 3) * 0.62) for x, y in pts]
    p.poly(pts, SCORCH)
    for i in range(12):
        a = i * 0.9
        rx, ry = 4 + math.cos(a) * (1.6 + (i % 3) * 0.8), 3 + math.sin(a) * (1.1 + (i % 4) * 0.5)
        p.rrect(rx, ry, rx + 0.5, ry + 0.16, 0.06, SHELL)
    shards(p, [(0.5, 4.6, 1.8, 5.7), (6.1, 4.4, 7.5, 5.7), (1.0, 0.4, 2.2, 1.3), (6.0, 0.4, 7.3, 1.4)], STEEL)
    # 3 lights: red warning lamps at the doors and stacks
    p = Panel(image, 3, cw, ch, s)
    for lx, ly in ((1.7, 4.65), (6.3, 4.65), (1.0, 1.0), (7.0, 1.0)):
        p.circle(lx, ly, 0.2, RED)
    p.rect(2.45, 3.7, 5.55, 3.8, RED)


# ------------------------------------------------------------------------------------------------ power (11 x 5)

def power(image):
    cw, ch, s = BUILDINGS['power']
    # 0 intact: turbine wells either side of a ringed reactor housing, conduits, a door canopy on the top edge
    p = Panel(image, 0, cw, ch, s)
    p.chamfer(0.05, 0.05, 10.95, 4.95, 0.3, SLAB, EDGE)
    for cx in (2.2, 8.8):
        p.circle(cx, 2.6, 1.4, SLAB_HI, EDGE)
        p.circle(cx, 2.6, 1.15, RECESS, EDGE)
    p.rrect(4.0, 0.6, 7.0, 4.4, 0.4, SLAB_HI, EDGE)
    p.circle(5.5, 2.5, 1.05, RECESS, EDGE)
    p.circle(5.5, 2.5, 0.65, STEEL)
    for x in (4.25, 4.55, 6.45, 6.75):
        p.line([(x, 0.9), (x, 4.1)], SLAB, 3)
    p.rect(3.6, 2.25, 4.0, 2.85, STEEL, EDGE)
    p.rect(7.0, 2.25, 7.4, 2.85, STEEL, EDGE)
    p.rect(0.5, 4.35, 10.5, 4.75, STEEL, EDGE)
    p.rect(2.9, 0.05, 4.1, 0.45, STEEL, EDGE)
    for x in (3.1, 3.5, 3.9):
        p.rect(x - 0.05, 0.1, x + 0.05, 0.4, HAZARD)
    # 1 damaged: split housing, one well uncovered and scorched, a ruptured conduit
    p = Panel(image, 1, cw, ch, s)
    p.chamfer(0.05, 0.05, 10.95, 4.95, 0.3, SLAB, EDGE)
    for cx in (2.2, 8.8):
        p.circle(cx, 2.6, 1.4, SLAB_HI, EDGE)
        p.circle(cx, 2.6, 1.15, RECESS, EDGE)
    p.circle(8.8, 2.6, 1.3, SCORCH)
    p.poly([(4.0, 0.6), (5.4, 0.6), (5.2, 2.4), (5.6, 4.4), (4.0, 4.4)], SLAB_HI, EDGE)
    p.poly([(5.7, 0.7), (7.0, 0.6), (7.0, 4.4), (5.8, 4.3), (5.9, 2.4)], SLAB_HI, EDGE)
    p.circle(5.5, 2.5, 0.6, SCORCH)
    p.rect(0.5, 4.35, 4.6, 4.75, STEEL, EDGE)
    p.poly([(6.2, 4.35), (10.5, 4.35), (10.5, 4.75), (6.6, 4.75)], STEEL, EDGE)
    p.line([(4.6, 4.5), (5.3, 3.9), (5.9, 4.6), (6.4, 4.2)], SCORCH, 5)
    p.rect(2.9, 0.05, 4.1, 0.45, STEEL, EDGE)
    p.line([(8.0, 0.8), (8.6, 1.5), (8.3, 2.3)], SCORCH, 4)
    # 2 destroyed: open well pits, a slumped housing, snapped conduits, broad scorch
    p = Panel(image, 2, cw, ch, s)
    for cx in (2.2, 8.8):
        p.circle(cx, 2.6, 1.3, SCORCH)
        p.circle(cx, 2.6, 0.7, '#14120f')
        p.line([(cx - 1.2, 2.2), (cx - 0.3, 2.8)], STEEL, 4)
        p.line([(cx + 0.2, 3.4), (cx + 1.1, 2.9)], STEEL, 4)
    p.poly([(4.2, 1.0), (5.4, 0.8), (6.8, 1.4), (6.9, 3.5), (5.6, 4.2), (4.3, 3.6)], SCORCH)
    shards(p, [(4.2, 1.0, 5.6, 2.4), (5.2, 2.4, 6.8, 3.9), (0.4, 0.3, 1.5, 1.1), (9.3, 3.8, 10.6, 4.8)], SLAB)
    for pts in (((0.5, 4.6), (2.4, 4.3)), ((3.0, 4.4), (3.9, 3.6)), ((7.2, 4.5), (9.0, 4.6)), ((9.4, 1.2), (10.6, 0.5))):
        p.line(list(pts), STEEL, 6)
    # 3 lights: cyan glow along the conduits, the reactor lens and the well rims
    p = Panel(image, 3, cw, ch, s)
    for cx in (2.2, 8.8):
        p.d.ellipse((*p.p(cx - 1.15, 2.6 - 1.15), *p.p(cx + 1.15, 2.6 + 1.15)), outline=CYAN, width=5)
    p.circle(5.5, 2.5, 0.65, CYAN)
    p.rect(3.6, 2.4, 4.0, 2.7, CYAN)
    p.rect(7.0, 2.4, 7.4, 2.7, CYAN)
    p.rect(0.6, 4.5, 10.4, 4.6, CYAN)
    for x in (4.4, 6.6):
        p.line([(x, 1.0), (x, 4.0)], CYAN, 3)


# ------------------------------------------------------------------------------------------------------- parts

def parts(image):
    d = ImageDraw.Draw(image)

    def at(slot):
        return (slot % 3) * 512 + 256, (slot // 3) * 512 + 256

    # 0 radar dish: offset parabolic dish on a hub with a feed arm and counterweight so a turn is visible
    cx, cy = at(0)
    d.ellipse((cx - 40, cy - 40, cx + 40, cy + 40), fill=STEEL, outline=EDGE, width=3)
    d.ellipse((cx - 190, cy - 130, cx + 40, cy + 130), fill=SLAB_HI, outline=EDGE, width=4)
    d.ellipse((cx - 150, cy - 95, cx - 10, cy + 95), fill=RECESS)
    d.line((cx - 70, cy, cx + 175, cy), fill=EDGE, width=10)
    d.ellipse((cx + 150, cy - 24, cx + 200, cy + 24), fill=AMBER)
    d.rectangle((cx + 40, cy - 30, cx + 110, cy + 30), fill=STEEL, outline=EDGE, width=3)
    # 1 turbine fan: hub, eight swept blades inside a rim
    cx, cy = at(1)
    d.ellipse((cx - 190, cy - 190, cx + 190, cy + 190), fill=RECESS, outline=EDGE, width=6)
    for i in range(8):
        a = i * math.tau / 8
        pts = []
        for r, da in ((40, 0.0), (170, 0.22), (175, 0.42), (50, 0.35)):
            pts.append((cx + math.cos(a + da) * r, cy + math.sin(a + da) * r))
        d.polygon(pts, fill=SLAB_HI, outline=EDGE)
    d.ellipse((cx - 45, cy - 45, cx + 45, cy + 45), fill=STEEL, outline=EDGE, width=4)
    # 2 vent fan: ring and six broad blades
    cx, cy = at(2)
    d.ellipse((cx - 150, cy - 150, cx + 150, cy + 150), fill=RECESS, outline=EDGE, width=6)
    for i in range(6):
        a = i * math.tau / 6
        pts = [(cx + math.cos(a) * 30, cy + math.sin(a) * 30), (cx + math.cos(a - .3) * 135, cy + math.sin(a - .3) * 135),
               (cx + math.cos(a + .35) * 135, cy + math.sin(a + .35) * 135)]
        d.polygon(pts, fill=SLAB_HI, outline=EDGE)
    d.ellipse((cx - 28, cy - 28, cx + 28, cy + 28), fill=STEEL)
    # 3 console bank: 400px square (3x3 cells at 133px), consoles on three sides round a map table
    cx, cy = at(3)
    d.rectangle((cx - 200, cy - 200, cx + 200, cy + 200), fill=SLAB, outline=EDGE, width=4)
    d.rectangle((cx - 190, cy - 190, cx + 190, cy - 110), fill=STEEL, outline=EDGE, width=3)
    d.rectangle((cx - 190, cy - 110, cx - 110, cy + 190), fill=STEEL, outline=EDGE, width=3)
    d.rectangle((cx + 110, cy - 110, cx + 190, cy + 190), fill=STEEL, outline=EDGE, width=3)
    for x in (-150, -60, 30, 120):
        d.rectangle((cx + x, cy - 180, cx + x + 60, cy - 130), fill=AMBER)
    d.rectangle((cx - 180, cy - 40, cx - 120, cy + 120), fill=CYAN)
    d.rectangle((cx + 120, cy - 40, cx + 180, cy + 120), fill=CYAN)
    d.rectangle((cx - 60, cy - 30, cx + 60, cy + 60), fill=RECESS, outline=EDGE, width=3)
    d.ellipse((cx - 25, cy + 100, cx + 25, cy + 150), fill=RECESS, outline=EDGE, width=3)
    # 4 ordnance stacks: three palletised racks of shells
    cx, cy = at(4)
    d.rectangle((cx - 200, cy - 200, cx + 200, cy + 200), fill=SLAB, outline=EDGE, width=4)
    for ry in (-170, -50, 70):
        d.rectangle((cx - 180, cy + ry, cx + 180, cy + ry + 110), fill=RECESS, outline=EDGE, width=3)
        for i in range(8):
            d.rounded_rectangle((cx - 170 + i * 44, cy + ry + 10, cx - 138 + i * 44, cy + ry + 100), 10, fill=SHELL)
    # 5 reactor core: concentric housing with four conduit stubs
    cx, cy = at(5)
    d.rectangle((cx - 200, cy - 200, cx + 200, cy + 200), fill=SLAB, outline=EDGE, width=4)
    d.ellipse((cx - 170, cy - 170, cx + 170, cy + 170), fill=SLAB_HI, outline=EDGE, width=5)
    d.ellipse((cx - 120, cy - 120, cx + 120, cy + 120), fill=RECESS, outline=EDGE, width=5)
    d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill=STEEL, outline=EDGE, width=4)
    d.ellipse((cx - 28, cy - 28, cx + 28, cy + 28), fill=CYAN)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        d.rectangle((cx + dx * 185 - 18 + abs(dy) * 0, cy + dy * 185 - 18, cx + dx * 185 + 18, cy + dy * 185 + 18), fill=STEEL)


# ---------------------------------------------------------------------------------------------------- gatehouse

def gatehouse(image):
    s = 150
    # 0 tower roof (2x3): hardened cap with embrasure slits on the long outer edge and both flanks of the lane
    p = Panel(image, 0, 2, 3, s)
    p.chamfer(0.04, 0.04, 1.96, 2.96, 0.2, SLAB, EDGE)
    p.rect(0.3, 0.3, 1.7, 2.7, SLAB_HI, EDGE)
    p.rect(0.04, 0.8, 0.3, 2.2, RECESS)
    p.rect(0.5, 0.06, 1.5, 0.28, RECESS)
    p.rect(0.5, 2.72, 1.5, 2.94, RECESS)
    p.rect(0.8, 1.0, 1.5, 2.0, RECESS, EDGE)
    p.circle(1.15, 1.5, 0.22, STEEL)
    # 1 gate leaves (2x3): the passage stays open; two heavy leaves stand folded back against the jambs, a threshold sill
    p = Panel(image, 1, 2, 3, s)
    p.rect(0.0, 0.0, 2.0, 0.5, STEEL, EDGE)
    p.rect(0.0, 2.5, 2.0, 3.0, STEEL, EDGE)
    for x in (0.2, 0.6, 1.0, 1.4):
        p.rect(x, 0.06, x + 0.18, 0.44, HAZARD)
        p.rect(x, 2.56, x + 0.18, 2.94, HAZARD)
    p.circle(0.12, 0.25, 0.14, RECESS, EDGE)
    p.circle(0.12, 2.75, 0.14, RECESS, EDGE)
    p.rect(0.9, 0.55, 1.05, 2.45, SLAB_HI)
    # 2 damaged tower roof: the same cap, cracked and scorched, an embrasure blown wide
    p = Panel(image, 2, 2, 3, s)
    p.chamfer(0.04, 0.04, 1.96, 2.96, 0.2, SLAB, EDGE)
    p.rect(0.3, 0.3, 1.7, 2.7, SLAB_HI, EDGE)
    p.circle(1.0, 1.4, 0.7, SCORCH)
    p.poly([(0.04, 0.6), (0.5, 0.8), (0.4, 1.5), (0.04, 2.2)], RECESS)
    p.line([(0.6, 0.4), (1.1, 1.2), (0.8, 1.9), (1.4, 2.6)], SCORCH, 5)
    p.rect(0.5, 2.72, 1.5, 2.94, RECESS)


def save(name, fn):
    image = Image.new('RGBA', (W, H))
    fn(image)
    image.save(HERE / f'fort-buildings-{name}-guide.png')


def banner():
    """The holder banner: a pole, a swallow-tail pennant and a base plate, neutral light grey so a holder tint multiplies
    it cleanly. 128x256, pivot at the base plate's centre (the runtime offsets it)."""
    img = Image.new('RGBA', (128, 256))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((44, 196, 84, 236), 8, fill='#8e9498', outline='#c6cbcd', width=3)
    d.ellipse((58, 210, 70, 222), fill='#5b6165')
    d.rectangle((60, 20, 68, 214), fill='#b9bfc2', outline='#8e9498')
    d.polygon([(68, 28), (124, 36), (108, 60), (124, 84), (68, 92)], fill='#f2f4f4', outline='#c9cdd0')
    d.line((68, 44, 112, 48), fill='#d9dcdd', width=3)
    d.line((68, 62, 104, 62), fill='#d9dcdd', width=3)
    d.line((68, 78, 112, 76), fill='#d9dcdd', width=3)
    out = ROOT / 'mod/graphics/fort'
    out.mkdir(parents=True, exist_ok=True)
    img.save(out / 'fort-banner.png')


if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'guides'
    if mode == 'guides':
        save('bunker', bunker)
        save('magazine', magazine)
        save('power', power)
        save('parts', parts)
        save('gatehouse', gatehouse)
    elif mode == 'banner':
        banner()
