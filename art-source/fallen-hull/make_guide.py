"""Geometry guides for the fallen hull's looks (`fallen-hull-nouns.md`; `long-survey-arc.md`, stories: Hull art).

Every sheet is 1536x1024, flat placeholder colours with a dark line round every silhouette, painted once by ImageGen
(`generate.py`) and cut mechanically by `export.py`. Pieces are drawn at a fixed density per sheet (CELL_PX below) and
every piece keeps its guide box, so the exporter cuts it at the guide's frame scaled to the raw.

  hull     the whole fallen hull from above, bow east, on a transparent ground, 24 px a cell: a 50 x 18-cell body with
           a snapped-off stern lying apart to the west. Painted three ways for pick `hull-lineage` (generate.py).
  forge    the autoforge housing, three designs (columns), lit and working on the top row, put out (cold, cracked,
           breached) on the bottom; 6 x 6 cells at 80 px a cell.
  stake    the survey stake, two designs (rows), its stages as columns: planted (the folded pylon), raised (the pylon
           opened into a node mast and a drone cradle) and the established stake's feedstock store, drawn 2, 3 and 2
           guide cells of 150 px and cut at STAKE_GAME_PX a game cell (3.2, 4.8 and 3.2 cells).
  debris   the debris field and hull plating sections, eleven pieces at 64 px a cell (DEBRIS below).
  trench   the impact trench's ground: a seamless 2 x 2-cell swatch of ploughed, scorched earth (192 px a cell), the
           trench's lip as a long strip (thrown-up berm, outside at the top), and three loose scorch and furrow patches
           with smooth lopsided outlines.
  stripper, hauler, rig
           the walker master layout of `art-source/derelict-walker/guide.py` (assembled walker on the left, torso, thigh
           and shin apart on the right, 256 px a cell, flat #b4b4b4 ground) with the stripper's, the hauler's and the
           one-unit stripper-hauler's (`rig`, pick `stripper-hauler` b) own torsos and legs at their size.

Usage: python art-source/fallen-hull/make_guide.py   (writes guides/<sheet>-guide.png and guides/layout.json)
"""
import importlib.util
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
GUIDES = HERE / "guides"
W, H = 1536, 1024

PLATE = (200, 204, 208, 255)
RAISED = (170, 176, 182, 255)
SEAM = (125, 133, 143, 255)
DARK = (43, 46, 51, 255)
VOID = (18, 18, 20, 255)
OUTLINE = (21, 23, 26, 255)
ACCENT = (224, 184, 48, 255)
LENS = (194, 58, 42, 255)
GLOW = (255, 140, 40, 255)
EARTH = (122, 104, 84, 255)
SCORCH = (52, 44, 38, 255)

_spec = importlib.util.spec_from_file_location("walker_guide", HERE.parent / "derelict-walker" / "guide.py")
walker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(walker)


def poly(d, pts, fill, width=4):
    d.polygon(pts, fill=fill, outline=OUTLINE, width=width)


def rect(d, x0, y0, x1, y1, fill, width=4, radius=0):
    if radius:
        d.rounded_rectangle((x0, y0, x1, y1), radius=radius, fill=fill, outline=OUTLINE, width=width)
    else:
        d.rectangle((x0, y0, x1, y1), fill=fill, outline=OUTLINE, width=width)


def disc(d, cx, cy, r, fill, width=3):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=OUTLINE, width=width)


def torn(x0, y0, x1, y1, steps, depth, seed):
    """A torn edge from (x0, y0) to (x1, y1), jagged up to depth either side."""
    rnd = random.Random(seed)
    pts = []
    for i in range(steps + 1):
        t = i / steps
        j = 0 if i in (0, steps) else (rnd.random() - 0.5) * 2 * depth
        dx, dy = x1 - x0, y1 - y0
        n = math.hypot(dx, dy)
        pts.append((x0 + dx * t - dy / n * j, y0 + dy * t + dx / n * j))
    return pts


def lopsided(cx, cy, rx, ry, seed, points=28, wobble=0.22, turn=0.0):
    """A smooth, lopsided blob: low-frequency radius noise only (sharp lobes come back as stars)."""
    rnd = random.Random(seed)
    a1, a2, p1, p2 = rnd.uniform(0.1, wobble), rnd.uniform(0.05, wobble / 2), rnd.random() * 6.3, rnd.random() * 6.3
    out = []
    for i in range(points):
        a = 2 * math.pi * i / points
        k = 1 + a1 * math.sin(a + p1) + a2 * math.sin(2 * a + p2)
        x, y = math.cos(a) * rx * k, math.sin(a) * ry * k
        out.append((cx + x * math.cos(turn) - y * math.sin(turn), cy + x * math.sin(turn) + y * math.cos(turn)))
    return out


# ---------------------------------------------------------------- hull

HULL_CELL = 24
HULL_BOX = (60, 296, 1500, 728)        # the body, bow east: 60 x 18 cells of canvas
STERN_BOX = (24, 330, 228, 690)        # the snapped stern, apart


def hull(d):
    c = HULL_CELL
    x0, y0, x1, y1 = 260, 296, 1440, 728
    cy = (y0 + y1) / 2
    # Body: a long stepped block, the stern end torn where it snapped.
    tear = torn(x0, y1 - 20, x0 + 20, y0 + 20, 14, 26, 3)
    body = [(x0 + 30, y0 + 20), (x0 + 340, y0 + 20), (x0 + 380, y0), (x1 - 330, y0), (x1 - 290, y0 + 30),
            (x1 - 120, y0 + 30), (x1, cy - 70), (x1 + 40, cy), (x1, cy + 70), (x1 - 120, y1 - 30),
            (x1 - 290, y1 - 30), (x1 - 330, y1), (x0 + 380, y1), (x0 + 340, y1 - 20), (x0 + 30, y1 - 20)] + tear[1:-1]
    poly(d, body, PLATE, 6)
    # Dorsal spine down the middle, the forge stack block on it (the forge hall lies under it).
    rect(d, x0 + 120, cy - 46, x1 - 200, cy + 46, RAISED, 5)
    for k in range(9):
        xx = x0 + 150 + k * 82
        rect(d, xx, cy - 30, xx + 54, cy + 30, SEAM, 3)
    rect(d, x0 + 470, cy - 120, x0 + 650, cy + 120, RAISED, 5, radius=10)
    for k in range(3):
        disc(d, x0 + 515 + k * 45, cy - 70, 17, DARK)
        disc(d, x0 + 515 + k * 45, cy + 70, 17, DARK)
    rect(d, x0 + 500, cy - 28, x0 + 620, cy + 28, DARK, 3)
    # Plate bands across the body, every four cells.
    for xx in range(x0 + 60, x1 - 120, 4 * c):
        d.line([(xx, y0 + 34), (xx, cy - 60)], fill=SEAM, width=3)
        d.line([(xx, cy + 60), (xx, y1 - 34)], fill=SEAM, width=3)
    # Bow module: sensor blister and two forward launch bays, the port one blown open.
    poly(d, [(x1 - 110, cy - 60), (x1 - 20, cy - 30), (x1 - 20, cy + 30), (x1 - 110, cy + 60)], RAISED, 4)
    disc(d, x1 - 70, cy, 22, LENS)
    rect(d, x1 - 300, y0 + 50, x1 - 160, y0 + 120, DARK, 4)
    for k in range(5):
        d.line([(x1 - 290 + k * 28, y0 + 56), (x1 - 290 + k * 28, y0 + 114)], fill=SEAM, width=4)
    poly(d, [(x1 - 300, y1 - 120), (x1 - 160, y1 - 120), (x1 - 150, y1 - 60), (x1 - 310, y1 - 50)], VOID, 4)
    # Flank bays and radiator fins along both sides.
    for k in range(5):
        xx = x0 + 400 + k * 120
        if k == 2:
            continue
        rect(d, xx, y0 + 6, xx + 70, y0 + 40, DARK, 3)
        rect(d, xx, y1 - 40, xx + 70, y1 - 6, DARK, 3)
    for k in range(6):
        xx = x0 + 60 + k * 44
        rect(d, xx, y0 + 26, xx + 22, y0 + 90, SEAM, 3)
        rect(d, xx, y1 - 90, xx + 22, y1 - 26, SEAM, 3)
    # Hazard bands at the bow shoulders.
    for s in (-1, 1):
        yy = cy + s * 130
        poly(d, [(x1 - 140, yy - 14), (x1 - 100, yy - 14), (x1 - 120, yy + 14), (x1 - 160, yy + 14)], ACCENT, 3)
    # The sensor mast, snapped and laid flat along the starboard side.
    rect(d, x0 + 760, y1 - 90, x0 + 980, y1 - 64, SEAM, 3)
    disc(d, x0 + 990, y1 - 77, 26, RAISED)
    # A breach in the dorsal plate, open to the hull's interior.
    d.polygon(lopsided(x0 + 860, cy - 120, 60, 34, 9), fill=VOID, outline=OUTLINE)
    # The snapped stern: engine block, three engine bells, its own torn edge facing the body.
    sx0, sy0, sx1, sy1 = STERN_BOX
    stear = torn(sx1, sy0 + 10, sx1 - 10, sy1 - 10, 10, 18, 5)
    poly(d, [(sx0 + 60, sy0), (sx1 - 20, sy0 + 10)] + stear[1:-1] + [(sx1 - 20, sy1 - 10), (sx0 + 60, sy1),
                                                                     (sx0 + 40, sy1 - 40), (sx0 + 40, sy0 + 40)],
         PLATE, 6)
    for k in range(3):
        yy = sy0 + 70 + k * 110
        disc(d, sx0 + 46, yy, 40, DARK, 5)
        disc(d, sx0 + 46, yy, 22, SEAM, 3)
    rect(d, sx0 + 100, sy0 + 40, sx1 - 50, sy1 - 40, RAISED, 4)
    # Torn plates and girders thrown between the stern and the body.
    for (px, py, seed) in ((246, 420, 1), (240, 610, 2)):
        poly(d, lopsided(px, py, 18, 30, seed, 7, 0.4), PLATE, 3)


# ---------------------------------------------------------------- forge

FORGE_CELL = 80
FORGE_CELLS = 6
FORGE_DESIGNS = ("crucible", "gantry", "ring")


def forge_panel(col, row):
    side = FORGE_CELLS * FORGE_CELL  # 480
    x = col * 512 + 16
    y = row * 512 + 16
    return x, y, x + side, y + side


def forge(d, design, x0, y0, x1, y1, lit):
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    core = GLOW if lit else VOID
    if design == "crucible":
        # A square press housing: a round crucible mouth under a press head, feed hoppers at two corners, a print bed.
        rect(d, x0 + 20, y0 + 20, x1 - 20, y1 - 20, PLATE, 6, radius=24)
        rect(d, x0 + 70, y1 - 150, x1 - 70, y1 - 60, SEAM, 4)
        for k in range(6):
            d.line([(x0 + 95 + k * 55, y1 - 140), (x0 + 95 + k * 55, y1 - 70)], fill=DARK, width=5)
        disc(d, cx, cy - 50, 110, RAISED, 5)
        disc(d, cx, cy - 50, 68, core, 4)
        for (hx, hy) in ((x0 + 70, y0 + 70), (x1 - 70, y0 + 70)):
            poly(d, [(hx - 40, hy - 40), (hx + 40, hy - 40), (hx + 22, hy + 40), (hx - 22, hy + 40)], DARK, 4)
        for (ax, ay) in ((x0 + 30, cy + 80), (x1 - 70, cy + 80)):
            poly(d, [(ax, ay - 14), (ax + 40, ay - 14), (ax + 30, ay + 14), (ax - 10, ay + 14)], ACCENT, 3)
    elif design == "gantry":
        # A gantry printer: two rails along a long build bed, a bridge carrying the print head across them.
        rect(d, x0 + 40, y0 + 40, x1 - 40, y1 - 40, SEAM, 5)
        rect(d, x0 + 90, y0 + 90, x1 - 90, y1 - 90, DARK, 4)
        poly(d, [(x0 + 150, cy - 70), (x1 - 150, cy - 70), (x1 - 170, cy + 70), (x0 + 170, cy + 70)], RAISED, 4)
        for s in (-1, 1):
            rect(d, x0 + 20, cy + s * 200 - 18, x1 - 20, cy + s * 200 + 18, PLATE, 5)
        rect(d, cx - 40, y0 + 30, cx + 40, y1 - 30, PLATE, 5)
        rect(d, cx - 56, cy - 56, cx + 56, cy + 56, RAISED, 5)
        disc(d, cx, cy, 26, core, 3)
        for (ax, ay) in ((x0 + 30, y0 + 30), (x1 - 70, y1 - 58)):
            poly(d, [(ax, ay), (ax + 40, ay), (ax + 30, ay + 28), (ax - 10, ay + 28)], ACCENT, 3)
    else:  # ring
        # A ring forge: an armoured ring of eight forming heads round a deep pit, a feed conveyor in from the south.
        disc(d, cx, cy, 225, PLATE, 6)
        disc(d, cx, cy, 150, SEAM, 5)
        disc(d, cx, cy, 90, core, 4)
        for k in range(8):
            a = k * math.pi / 4 + math.pi / 8
            hx, hy = cx + math.cos(a) * 185, cy + math.sin(a) * 185
            rect(d, hx - 26, hy - 26, hx + 26, hy + 26, RAISED, 4)
            disc(d, hx, hy, 10, DARK)
        rect(d, cx - 40, cy + 150, cx + 40, y1 - 4, DARK, 4)
        for s in (-1, 1):
            poly(d, [(cx + s * 225 - 20, cy - 16), (cx + s * 225 + 20, cy - 16), (cx + s * 225 + 10, cy + 16),
                     (cx + s * 225 - 30, cy + 16)], ACCENT, 3)
    if not lit:
        # Put out: a breach torn through the housing's top plate and cracks across it.
        d.polygon(lopsided(cx + 70, cy + 40, 60, 40, 21), fill=VOID, outline=OUTLINE)
        for k in range(4):
            a = k * 1.6 + 0.4
            d.line([(cx + 70 + math.cos(a) * 60, cy + 40 + math.sin(a) * 40),
                    (cx + 70 + math.cos(a) * 140, cy + 40 + math.sin(a) * 110)], fill=OUTLINE, width=4)


# ---------------------------------------------------------------- stake

STAKE_CELL = 150
# The stake guide is drawn at STAKE_CELL px a "guide cell" but cut at STAKE_GAME_PX px a game cell, so a stake stands
# 1.6 times its guide footprint: planted 3.2 cells, raised 4.8, the store 3.2 (carded beside a line frame, the guide's
# 2 / 3 / 2 read as toys).
STAKE_GAME_PX = 94
STAKE_DESIGNS = ("spire", "tripod")
STAKE_STAGES = ("planted", "raised", "store")
STAKE_CELLS = {"planted": 2, "raised": 3, "store": 2}


def stake_box(design, stage):
    col = STAKE_STAGES.index(stage)
    row = STAKE_DESIGNS.index(design)
    side = STAKE_CELLS[stage] * STAKE_CELL
    cx, cy = col * 512 + 256, row * 512 + 256
    return cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2


def stake(d, design, stage):
    x0, y0, x1, y1 = stake_box(design, stage)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    if stage == "store":
        # The feedstock store: an armoured hopper, open top, scrap metal heaped in it, a chute on one side.
        rect(d, x0 + 18, y0 + 18, x1 - 18, y1 - 18, PLATE, 5, radius=10 if design == "spire" else 0)
        rect(d, x0 + 54, y0 + 54, x1 - 54, y1 - 54, DARK, 4)
        rnd = random.Random(31 if design == "spire" else 37)
        for _ in range(9):
            px, py = rnd.uniform(x0 + 80, x1 - 80), rnd.uniform(y0 + 80, y1 - 80)
            poly(d, lopsided(px, py, rnd.uniform(14, 26), rnd.uniform(8, 16), rnd.randint(0, 99), 6, 0.4,
                             rnd.random() * 3), SEAM, 2)
        rect(d, x1 - 40, cy - 30, x1 - 4, cy + 30, SEAM, 4)
        poly(d, [(x0 + 18, y0 + 18), (x0 + 60, y0 + 18), (x0 + 18, y0 + 60)], ACCENT, 3)
        return
    if design == "spire":
        if stage == "planted":
            # A folded pylon: a square armoured spike seen from above, four folded petals round its tip.
            poly(d, [(cx, y0 + 20), (x1 - 20, cy), (cx, y1 - 20), (x0 + 20, cy)], PLATE, 6)
            for k in range(4):
                a = k * math.pi / 2
                px, py = cx + math.cos(a) * 70, cy + math.sin(a) * 70
                poly(d, [(cx + math.cos(a + 0.5) * 22, cy + math.sin(a + 0.5) * 22), (px, py),
                         (cx + math.cos(a - 0.5) * 22, cy + math.sin(a - 0.5) * 22)], RAISED, 3)
            disc(d, cx, cy, 16, DARK)
            for s in (-1, 1):
                poly(d, [(cx + s * 60, cy - 60 * s - 8), (cx + s * 80, cy - 40 * s - 8),
                         (cx + s * 70, cy - 30 * s + 8), (cx + s * 50, cy - 50 * s + 8)], ACCENT, 2)
        else:
            # Raised: the petals opened flat into a square pad, a node mast in the middle, a cradle on one side.
            for k in range(4):
                a = k * math.pi / 2
                px, py = cx + math.cos(a) * 190, cy + math.sin(a) * 190
                poly(d, [(cx + math.cos(a + 0.6) * 60, cy + math.sin(a + 0.6) * 60), (px, py),
                         (cx + math.cos(a - 0.6) * 60, cy + math.sin(a - 0.6) * 60)], RAISED, 4)
            rect(d, cx - 70, cy - 70, cx + 70, cy + 70, PLATE, 5)
            disc(d, cx, cy, 34, SEAM, 4)
            disc(d, cx, cy, 12, LENS)
            disc(d, x1 - 70, y1 - 70, 52, PLATE, 5)
            disc(d, x1 - 70, y1 - 70, 30, DARK, 4)
    else:  # tripod
        if stage == "planted":
            # A folded tripod spike: a round armoured core, three legs folded tight to it.
            for k in range(3):
                a = k * 2 * math.pi / 3 - math.pi / 2
                poly(d, [(cx + math.cos(a - 0.35) * 40, cy + math.sin(a - 0.35) * 40),
                         (cx + math.cos(a) * 125, cy + math.sin(a) * 125),
                         (cx + math.cos(a + 0.35) * 40, cy + math.sin(a + 0.35) * 40)], RAISED, 4)
            disc(d, cx, cy, 62, PLATE, 6)
            disc(d, cx, cy, 30, SEAM, 4)
            disc(d, cx, cy, 10, DARK)
            d.arc((cx - 62, cy - 62, cx + 62, cy + 62), 200, 250, fill=ACCENT, width=12)
        else:
            # Raised: three legs splayed and anchored, the core opened into a mast head, a cradle ring between legs.
            for k in range(3):
                a = k * 2 * math.pi / 3 - math.pi / 2
                ex, ey = cx + math.cos(a) * 200, cy + math.sin(a) * 200
                poly(d, [(cx + math.cos(a - 0.25) * 50, cy + math.sin(a - 0.25) * 50), (ex, ey),
                         (cx + math.cos(a + 0.25) * 50, cy + math.sin(a + 0.25) * 50)], RAISED, 4)
                disc(d, ex, ey, 18, DARK)
            disc(d, cx, cy, 64, PLATE, 5)
            disc(d, cx, cy, 26, SEAM, 3)
            disc(d, cx, cy, 10, LENS)
            a = math.pi / 6 + math.pi / 3
            disc(d, cx + math.cos(a) * 150, cy + math.sin(a) * 150, 50, PLATE, 5)
            disc(d, cx + math.cos(a) * 150, cy + math.sin(a) * 150, 28, DARK, 4)


# ---------------------------------------------------------------- debris and plating sections

DEBRIS_CELL = 64
# name: (x, y, cells wide, cells high) on the sheet; every frame a whole number of cells.
DEBRIS = {
    "plating-flat": (24, 24, 4, 4),        # a 4x4 run of hull plating, its four edges torn: a section the ground lays
    "plating-vents": (300, 24, 4, 4),      # the same with a vent block and hatch
    "plating-bay": (576, 24, 6, 4),        # a breached bay: plating round a torn hole into the dark
    "plating-edge": (980, 24, 8, 2),       # a broken hull edge: plating with one long torn side (north)
    "plate-shard-a": (980, 176, 3, 2),     # a torn plate lying loose
    "plate-shard-b": (1200, 176, 2, 2),
    "girder": (24, 316, 6, 1),             # a snapped structural girder
    "pod": (420, 316, 3, 3),               # a torn-off equipment pod
    "bell": (640, 316, 3, 3),              # an engine bell lying on its side, mouth west
    "mast": (860, 316, 2, 6),              # a sensor mast section, dish at the top
    "scrap-heap": (1020, 316, 3, 2),       # a heap of smaller scrap
}


def debris(d, name, x0, y0, cw, ch):
    c = DEBRIS_CELL
    x1, y1 = x0 + cw * c, y0 + ch * c
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    seed = sum(map(ord, name))
    if name.startswith("plating"):
        m = 10
        top = torn(x0 + m, y0 + m, x1 - m, y0 + m, cw * 3, 9, seed) if name != "plating-edge" else \
            torn(x0 + 4, y0 + 30, x1 - 4, y0 + 30, cw * 4, 22, seed)
        right = torn(x1 - m, y0 + m, x1 - m, y1 - m, ch * 3, 9, seed + 1)
        bottom = torn(x1 - m, y1 - m, x0 + m, y1 - m, cw * 3, 9, seed + 2)
        left = torn(x0 + m, y1 - m, x0 + m, y0 + m, ch * 3, 9, seed + 3)
        if name == "plating-edge":
            right = [(x1 - 4, y0 + 30), (x1 - 4, y1 - 4)]
            bottom = [(x1 - 4, y1 - 4), (x0 + 4, y1 - 4)]
            left = [(x0 + 4, y1 - 4), (x0 + 4, y0 + 30)]
        poly(d, top + right[1:] + bottom[1:] + left[1:-1], PLATE, 5)
        for k in range(1, cw):
            d.line([(x0 + k * c, y0 + 26), (x0 + k * c, y1 - 20)], fill=SEAM, width=3)
        for k in range(1, ch):
            d.line([(x0 + 20, y0 + k * c), (x1 - 20, y0 + k * c)], fill=SEAM, width=3)
        if name == "plating-vents":
            rect(d, x0 + 40, y0 + 40, x0 + 150, y0 + 110, DARK, 3)
            for k in range(5):
                d.line([(x0 + 52 + k * 20, y0 + 46), (x0 + 52 + k * 20, y0 + 104)], fill=SEAM, width=4)
            rect(d, x1 - 110, y1 - 110, x1 - 40, y1 - 40, RAISED, 4)
            disc(d, x1 - 75, y1 - 75, 14, DARK)
        if name == "plating-bay":
            d.polygon(lopsided(cx, cy, 110, 66, seed), fill=VOID, outline=OUTLINE)
        if name == "plating-edge":
            poly(d, [(x0 + 60, y1 - 50), (x0 + 110, y1 - 50), (x0 + 90, y1 - 20), (x0 + 40, y1 - 20)], ACCENT, 3)
    elif name.startswith("plate-shard"):
        poly(d, lopsided(cx, cy, cw * c * 0.44, ch * c * 0.38, seed, 9, 0.35, 0.3), PLATE, 4)
        d.line([(x0 + 30, cy), (x1 - 30, cy - 10)], fill=SEAM, width=3)
    elif name == "girder":
        rect(d, x0 + 8, cy - 18, x1 - 40, cy + 18, SEAM, 4)
        for k in range(10):
            xx = x0 + 20 + k * 34
            d.line([(xx, cy - 14), (xx + 26, cy + 14)], fill=DARK, width=4)
        poly(d, torn(x1 - 40, cy - 18, x1 - 40, cy + 18, 4, 14, seed) + [(x1 - 40, cy + 18)], SEAM, 3)
    elif name == "pod":
        poly(d, lopsided(cx, cy, 82, 74, seed, 8, 0.12), PLATE, 5)
        rect(d, cx - 40, cy - 30, cx + 40, cy + 30, RAISED, 4)
        disc(d, cx + 50, cy + 44, 12, LENS)
    elif name == "bell":
        poly(d, [(x0 + 20, cy - 80), (x1 - 40, cy - 40), (x1 - 40, cy + 40), (x0 + 20, cy + 80)], PLATE, 5)
        d.ellipse((x0 + 4, cy - 80, x0 + 40, cy + 80), fill=VOID, outline=OUTLINE, width=4)
        rect(d, x1 - 50, cy - 30, x1 - 10, cy + 30, SEAM, 4)
    elif name == "mast":
        rect(d, cx - 20, y0 + 120, cx + 20, y1 - 30, SEAM, 4)
        for k in range(6):
            yy = y0 + 150 + k * 40
            d.line([(cx - 20, yy), (cx + 20, yy + 20)], fill=DARK, width=3)
        disc(d, cx, y0 + 64, 56, RAISED, 5)
        disc(d, cx, y0 + 64, 18, DARK)
        poly(d, torn(cx - 20, y1 - 30, cx + 20, y1 - 30, 3, 10, seed) + [(cx + 20, y1 - 30)], SEAM, 3)
    else:  # scrap-heap
        rnd = random.Random(seed)
        d.polygon(lopsided(cx, cy, cw * c * 0.42, ch * c * 0.36, seed), fill=DARK, outline=OUTLINE)
        for _ in range(12):
            px, py = rnd.uniform(x0 + 30, x1 - 30), rnd.uniform(y0 + 24, y1 - 24)
            poly(d, lopsided(px, py, rnd.uniform(10, 22), rnd.uniform(6, 12), rnd.randint(0, 99), 6, 0.4,
                             rnd.random() * 3), rnd.choice((PLATE, SEAM, RAISED)), 2)


# ---------------------------------------------------------------- trench

TRENCH_CELL = 192
TRENCH_SWATCH = (0, 128)                 # 2 x 2 cells at 192 px a cell: 384 x 384 px
TRENCH_STRIP = (440, 40, 1056, 96)       # the lip: x, y, w, h, outside at the top
TRENCH_PATCHES = {                       # name: centre x, centre y, half-width, half-height (px)
    "scorch": (600, 700, 160, 140),
    "furrow": (1140, 330, 280, 80),
    "glassed": (1180, 760, 190, 150),
}


def trench(d):
    sx, sy = TRENCH_SWATCH
    side = TRENCH_CELL * 2
    d.rectangle((sx, sy, sx + side - 1, sy + side - 1), fill=EARTH)
    # Ploughed furrows along the grain (west-east), every quarter cell, so the swatch wraps.
    for k in range(8):
        yy = sy + k * side / 8
        d.rectangle((sx, yy + side / 16, sx + side - 1, yy + side / 16 + 10), fill=SCORCH)
        d.rectangle((sx, yy + 4, sx + side - 1, yy + 12), fill=(150, 130, 108, 255))
    rnd = random.Random(4)
    for _ in range(40):
        px, py = sx + rnd.random() * side, sy + rnd.random() * side
        d.ellipse((px - 5, py - 3, px + 5, py + 3), fill=(90, 80, 70, 255))
    x, y, w, h = TRENCH_STRIP
    # The lip: earth thrown up and out of the trench, its crest along the middle, outside (the field) at the top.
    d.rectangle((x, y, x + w - 1, y + h - 1), fill=EARTH)
    d.rectangle((x, y, x + w - 1, y + h * 0.3), fill=(150, 130, 108, 255))
    d.rectangle((x, y + h * 0.7, x + w - 1, y + h - 1), fill=SCORCH)
    for k in range(int(w / 48)):
        d.ellipse((x + k * 48 + 8, y + h * 0.35, x + k * 48 + 40, y + h * 0.6), fill=(100, 88, 74, 255))
    for name, (cx, cy, rx, ry) in TRENCH_PATCHES.items():
        seed = sum(map(ord, name))
        if name == "scorch":
            d.polygon(lopsided(cx, cy, rx, ry, seed), fill=SCORCH, outline=OUTLINE)
            d.polygon(lopsided(cx, cy, rx * 0.5, ry * 0.5, seed + 1), fill=(30, 26, 24, 255))
        elif name == "furrow":
            d.polygon(lopsided(cx, cy, rx, ry, seed, 32, 0.12), fill=EARTH, outline=OUTLINE)
            for k in (-1, 0, 1):
                d.polygon(lopsided(cx, cy + k * 34, rx * 0.85, 9, seed + k + 3, 24, 0.08), fill=SCORCH)
        else:
            d.polygon(lopsided(cx, cy, rx, ry, seed), fill=SCORCH, outline=OUTLINE)
            d.polygon(lopsided(cx, cy, rx * 0.6, ry * 0.55, seed + 2), fill=(70, 74, 72, 255))


# ---------------------------------------------------------------- walkers

def walker_torso(d, cx, cy, design, s):
    """The stripper's, hauler's and rig's torsos, s px a cell, facing up; sentinel language: rounded sensor head,
    overlapping carapace sections, a squared pack at the rear."""
    E = walker
    if design == "stripper":
        # Low and long: a small sensor head between two cutting arms that reach forward, saw discs at their tips,
        # a two-section back and a coolant pack behind.
        for sx in (-1, 1):
            E.poly(d, [(cx + sx * 0.30 * s, cy - 0.25 * s), (cx + sx * 0.52 * s, cy - 0.30 * s),
                       (cx + sx * 0.46 * s, cy - 1.05 * s), (cx + sx * 0.34 * s, cy - 1.05 * s)], E.RAISED)
            r = 0.20 * s
            d.ellipse([cx + sx * 0.40 * s - r, cy - 1.18 * s - r, cx + sx * 0.40 * s + r, cy - 1.18 * s + r],
                      fill=E.DARK, outline=E.EDGE, width=4)
            r2 = 0.07 * s
            d.ellipse([cx + sx * 0.40 * s - r2, cy - 1.18 * s - r2, cx + sx * 0.40 * s + r2, cy - 1.18 * s + r2],
                      fill=E.PLATE, outline=E.EDGE, width=2)
        d.ellipse([cx - 0.20 * s, cy - 0.72 * s, cx + 0.20 * s, cy - 0.34 * s], fill=E.RAISED, outline=E.EDGE,
                  width=4)
        for k, (w, y0, y1) in enumerate([(0.95, -0.38, 0.05), (1.00, 0.05, 0.45)]):
            E.poly(d, E.octagon(cx, cy + (y0 + y1) / 2 * s, w * s, (y1 - y0) * s, 0.12 * s),
                   E.PLATE if k == 0 else E.RAISED)
        d.rectangle([cx - 0.30 * s, cy + 0.45 * s, cx + 0.30 * s, cy + 0.66 * s], fill=E.DARK, outline=E.EDGE,
                    width=3)
        d.ellipse([cx - 0.05 * s, cy - 0.58 * s, cx + 0.05 * s, cy - 0.48 * s], fill=E.LAMP, outline=E.EDGE, width=2)
    elif design == "hauler":
        # Broad: a small sensor head forward, a deep open load bed (the hopper) taking most of the back, its sides
        # plated, a power pack behind it.
        d.ellipse([cx - 0.24 * s, cy - 0.98 * s, cx + 0.24 * s, cy - 0.56 * s], fill=E.RAISED, outline=E.EDGE,
                  width=4)
        E.poly(d, E.octagon(cx, cy - 0.38 * s, 1.15 * s, 0.40 * s, 0.12 * s), E.PLATE)
        E.poly(d, E.octagon(cx, cy + 0.22 * s, 1.40 * s, 0.86 * s, 0.14 * s), E.PLATE)
        d.rectangle([cx - 0.52 * s, cy - 0.10 * s, cx + 0.52 * s, cy + 0.56 * s], fill=E.DARK, outline=E.EDGE,
                    width=4)
        rnd = random.Random(7)
        for _ in range(7):
            px, py = rnd.uniform(cx - 0.42 * s, cx + 0.42 * s), rnd.uniform(cy - 0.02 * s, cy + 0.46 * s)
            E.poly(d, lopsided(px, py, 0.09 * s, 0.05 * s, rnd.randint(0, 99), 6, 0.4, rnd.random() * 3),
                   E.RAISED, 2)
        d.rectangle([cx - 0.32 * s, cy + 0.66 * s, cx + 0.32 * s, cy + 0.86 * s], fill=E.DARK, outline=E.EDGE,
                    width=3)
        d.ellipse([cx - 0.05 * s, cy - 0.84 * s, cx + 0.05 * s, cy - 0.74 * s], fill=E.LAMP, outline=E.EDGE, width=2)
    else:  # rig: one unit that cuts and carries
        for sx in (-1, 1):
            E.poly(d, [(cx + sx * 0.36 * s, cy - 0.45 * s), (cx + sx * 0.56 * s, cy - 0.50 * s),
                       (cx + sx * 0.50 * s, cy - 1.10 * s), (cx + sx * 0.38 * s, cy - 1.10 * s)], E.RAISED)
            r = 0.18 * s
            d.ellipse([cx + sx * 0.44 * s - r, cy - 1.22 * s - r, cx + sx * 0.44 * s + r, cy - 1.22 * s + r],
                      fill=E.DARK, outline=E.EDGE, width=4)
        d.ellipse([cx - 0.20 * s, cy - 0.86 * s, cx + 0.20 * s, cy - 0.48 * s], fill=E.RAISED, outline=E.EDGE,
                  width=4)
        E.poly(d, E.octagon(cx, cy + 0.10 * s, 1.30 * s, 1.10 * s, 0.14 * s), E.PLATE)
        d.rectangle([cx - 0.44 * s, cy - 0.20 * s, cx + 0.44 * s, cy + 0.40 * s], fill=E.DARK, outline=E.EDGE,
                    width=4)
        d.rectangle([cx - 0.30 * s, cy + 0.66 * s, cx + 0.30 * s, cy + 0.84 * s], fill=E.DARK, outline=E.EDGE,
                    width=3)
        d.ellipse([cx - 0.05 * s, cy - 0.72 * s, cx + 0.05 * s, cy - 0.62 * s], fill=E.LAMP, outline=E.EDGE, width=2)


# The torso part's px a cell and its centre's shift down its box (in cells), so each design's reach (the stripper's
# arms, the hauler's head) fits the torso box; export.py scales each by its own px a cell.
TORSO_FIT = {"stripper": (200, 0.36), "hauler": (235, 0.06), "rig": (200, 0.28)}


def walker_guide(design):
    E = walker
    img = Image.new("RGBA", (W, H), E.GROUND)
    d = ImageDraw.Draw(img)
    s = 220
    cx, cy = 512, 512
    hip = 0.55 * s
    # The stripper's legs sit wide and low, the hauler's long and planted; both on the sentinel's segment drawing.
    lw = {"stripper": 0.26, "hauler": 0.34, "rig": 0.32}[design]
    for a in (45, 135, 225, 315):
        r = math.radians(a)
        dx, dy = math.sin(r), -math.cos(r)
        hx, hy = cx + dx * hip, cy + dy * hip
        kx, ky = cx + dx * (hip + 0.85 * s), cy + dy * (hip + 0.85 * s)
        fx, fy = cx + dx * (hip + 1.75 * s), cy + dy * (hip + 1.75 * s)
        E.segment(d, kx, ky, fx, fy, lw * s * 0.9, foot=True)
        E.segment(d, hx, hy, kx, ky, lw * s)
    walker_torso(d, cx, cy, design, s)
    x0, y0, x1, y1 = E.PARTS["torso"]
    ts, shift = TORSO_FIT[design]
    walker_torso(d, (x0 + x1) / 2, (y0 + y1) / 2 + shift * ts, design, ts)
    tx0, ty0, tx1, ty1 = E.PARTS["thigh"]
    tc = (tx0 + tx1) / 2
    E.segment(d, tc, ty1 - 40, tc, ty1 - 40 - E.THIGH_LEN * E.CELL, lw * E.CELL)
    sx0, sy0, sx1, sy1 = E.PARTS["shin"]
    sc = (sx0 + sx1) / 2
    E.segment(d, sc, sy1 - 40, sc, sy1 - 40 - (E.SHIN_LEN - 0.25) * E.CELL, lw * E.CELL * 0.9, foot=True)
    return img


# ---------------------------------------------------------------- sheets

def sheet():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def main():
    GUIDES.mkdir(parents=True, exist_ok=True)
    img = sheet()
    hull(ImageDraw.Draw(img))
    img.save(GUIDES / "hull-guide.png")

    img = sheet()
    d = ImageDraw.Draw(img)
    for col, design in enumerate(FORGE_DESIGNS):
        for row in (0, 1):
            forge(d, design, *forge_panel(col, row), lit=row == 0)
    img.save(GUIDES / "forge-guide.png")

    img = sheet()
    d = ImageDraw.Draw(img)
    for design in STAKE_DESIGNS:
        for stage in STAKE_STAGES:
            stake(d, design, stage)
    img.save(GUIDES / "stake-guide.png")

    img = sheet()
    d = ImageDraw.Draw(img)
    for name, (x, y, cw, ch) in DEBRIS.items():
        debris(d, name, x, y, cw, ch)
    img.save(GUIDES / "debris-guide.png")

    img = sheet()
    trench(ImageDraw.Draw(img))
    img.save(GUIDES / "trench-guide.png")

    for design in ("stripper", "hauler", "rig"):
        walker_guide(design).save(GUIDES / f"{design}-guide.png")

    layout = {
        "canvas": [W, H],
        "hull": {"cellPx": HULL_CELL, "body": HULL_BOX, "stern": STERN_BOX},
        "forge": {"cellPx": FORGE_CELL, "cells": FORGE_CELLS,
                  "frames": {f"{dz}-{'lit' if row == 0 else 'out'}": forge_panel(c, row)
                             for c, dz in enumerate(FORGE_DESIGNS) for row in (0, 1)}},
        "stake": {"cellPx": STAKE_GAME_PX,
                  "frames": {f"{dz}-{st}": [round(v) for v in stake_box(dz, st)]
                             for dz in STAKE_DESIGNS for st in STAKE_STAGES}},
        "debris": {"cellPx": DEBRIS_CELL,
                   "frames": {n: [x, y, x + cw * DEBRIS_CELL, y + ch * DEBRIS_CELL]
                              for n, (x, y, cw, ch) in DEBRIS.items()}},
        "trench": {"cellPx": TRENCH_CELL, "swatch": [*TRENCH_SWATCH, TRENCH_CELL * 2, TRENCH_CELL * 2],
                   "strip": TRENCH_STRIP,
                   "patches": {n: [round(cx - rx * 1.4), round(cy - ry * 1.4), round(cx + rx * 1.4), round(cy + ry * 1.4)]
                               for n, (cx, cy, rx, ry) in TRENCH_PATCHES.items()}},
    }
    layout["walkers"] = {dz: {"torsoCellPx": ts, "legCellPx": walker.CELL, "parts": walker.PARTS,
                              "thighCells": walker.THIGH_LEN, "shinCells": walker.SHIN_LEN}
                         for dz, (ts, _) in TORSO_FIT.items()}
    (GUIDES / "layout.json").write_text(json.dumps(layout, indent=2))
    print("wrote", GUIDES)


if __name__ == "__main__":
    main()
