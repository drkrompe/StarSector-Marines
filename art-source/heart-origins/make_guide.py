"""Geometry guides for the heart's origin props (`heart-origins.md`): one sheet per kit, code owns every silhouette.

Each prop is drawn straight overhead on its full placement frame (footprint x CELL px) in a slot with a transparent
gutter, once per state, a state to a row:

  clean      the thing before the bloom (shipped as `<id>-clean`, static)
  grown      the heart's state: what happened to it, and weeks of creep on it (shipped as `<id>`)
  swallowed  telling objects only: the same, with the growth thickest, the object half gone (`<id>-swallowed`)
  damaged    grown, then fought over: torn casing, scorched and torn growth (`<id>-damaged`)
  destroyed  low separated wreck pieces in a creep scab, transparent ground between (`<id>-destroyed`)

Flat keyed colours only; the prompt names the materials. The creep's extent is guide geometry too, so the growth
lands where the story needs it (out of the shaft's mouth, out of the split box's side) and not where the model
likes. Writes `templates/heart-<kit>-guide.png` and `heart-origins/geometry.json` (slot rects per id and state).

usage: python art-source/heart-origins/make_guide.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

CELL = 128
GUTTER = 48
HERE = Path(__file__).resolve().parent
TEMPLATES = HERE.parent / "templates"

CONCRETE, CONCRETE_DK, STEEL, STEEL_DK = "#8d8c84", "#5f5e58", "#6f7a7c", "#3f4749"
RUST, RUST_DK, BLUE, BLUE_DK = "#9a4a30", "#5e2c1e", "#4d6a80", "#2d4152"
HAZARD, HOLE, GLASS, TIMBER, TIMBER_DK = "#c9a43a", "#120c12", "#7fa6a8", "#8a6a48", "#56402c"
ROOF, ROOF_DK, IRON, RED, YELLOW, GREEN = "#7d8a7a", "#4f5a4e", "#34383a", "#b0453a", "#d0aa3a", "#4f8a5a"
WATER, WATER_DK, SHELL, ICE, REEFER = "#3f7480", "#1f3c44", "#7a3a34", "#cfe3e6", "#d9dcd6"
CREEP, CREEP_DK, CHITIN, SINEW, IVORY = "#6e6474", "#463e4c", "#4a3358", "#7a2a44", "#ddd2bc"
SCORCH = "#231d1c"


def rot(points, cx, cy, deg):
    a = math.radians(deg)
    return [(cx + (x - cx) * math.cos(a) - (y - cy) * math.sin(a),
             cy + (x - cx) * math.sin(a) + (y - cy) * math.cos(a)) for x, y in points]


def box(d, x0, y0, x1, y1, fill, outline=None, w=4, r=6):
    d.rounded_rectangle((x0, y0, x1, y1), r, fill=fill, outline=outline, width=w)


MAT = []
# Clear margin round every frame the creep mat may not enter, as a share of the frame's short side.
EDGE = 0.1


def creep(d, rng, ox, oy, fw, fh, anchors, reach, density=1.0, over=False):
    """Creep grown from anchor points. The mat (lobed blobs) goes on the layer UNDER the prop, so the thing stays
    legible and the growth reads as spreading from it; only raised veins, chitin plates and ivory spurs lie over
    it. With `over` the mat covers the west half of the prop too, rising over it: the swallowed state."""
    for ax, ay in anchors:
        for _ in range(int(14 * density)):
            r = rng.uniform(0.25, 0.6) * reach
            x, y = ax + rng.gauss(0, reach * 0.55), ay + rng.gauss(0, reach * 0.55)
            # The mat stays clear of the frame's edge, so no state's growth is cut square by its frame.
            m = EDGE * min(fw, fh)
            r = min(r, (fw - 2 * m) / 2, (fh - 2 * m) / 2)
            x = min(max(x, m + r), fw - m - r)
            y = min(max(y, m + r), fh - m - r)
            MAT.append((ox + x, oy + y, r))
            if over and x < fw * 0.5:
                d.ellipse((ox + x - r * 0.8, oy + y - r * 0.8, ox + x + r * 0.8, oy + y + r * 0.8), fill=CREEP)
    for ax, ay in anchors:
        for _ in range(int(4 * density)):
            pts = [(ox + ax, oy + ay)]
            a = rng.uniform(0, 2 * math.pi)
            x, y = ax, ay
            for _ in range(5):
                a += rng.uniform(-0.6, 0.6)
                x = min(max(x + math.cos(a) * reach * 0.22, EDGE * fw), fw - EDGE * fw)
                y = min(max(y + math.sin(a) * reach * 0.22, EDGE * fh), fh - EDGE * fh)
                pts.append((ox + x, oy + y))
            d.line(pts, fill=CREEP_DK, width=max(4, int(reach * 0.06)), joint="curve")
        for _ in range(int(2 * density)):
            px, py = ax + rng.gauss(0, reach * 0.3), ay + rng.gauss(0, reach * 0.3)
            px, py = min(max(px, 14), fw - 14), min(max(py, 14), fh - 14)
            s = reach * rng.uniform(0.08, 0.14)
            plate = rot([(px - s, py - s * 0.6), (px + s, py - s * 0.8), (px + s * 1.1, py + s * 0.7),
                         (px - s * 0.8, py + s * 0.8)], px, py, rng.uniform(0, 90))
            d.polygon([(ox + x, oy + y) for x, y in plate], fill=CHITIN, outline=SINEW)
            sp = rng.uniform(0, 2 * math.pi)
            d.polygon([(ox + px + math.cos(sp) * s, oy + py + math.sin(sp) * s),
                       (ox + px + math.cos(sp) * s * 2.2, oy + py + math.sin(sp) * s * 2.2),
                       (ox + px + math.cos(sp + 0.5) * s, oy + py + math.sin(sp + 0.5) * s)], fill=IVORY)


def tears(d, rng, ox, oy, fw, fh, n=5):
    """Battle damage over a grown prop: scorch blots and dark tears through casing and growth."""
    for _ in range(n):
        x, y = rng.uniform(0.15, 0.85) * fw, rng.uniform(0.15, 0.85) * fh
        r = rng.uniform(0.06, 0.12) * min(fw, fh)
        d.ellipse((ox + x - r, oy + y - r * 0.7, ox + x + r, oy + y + r * 0.7), fill=SCORCH)
    for _ in range(n):
        x, y = rng.uniform(0.1, 0.9) * fw, rng.uniform(0.1, 0.9) * fh
        pts = [(ox + x, oy + y)]
        for _ in range(3):
            x = min(max(x + rng.uniform(-0.12, 0.12) * fw, 4), fw - 4)
            y = min(max(y + rng.uniform(-0.12, 0.12) * fh, 4), fh - 4)
            pts.append((ox + x, oy + y))
        d.line(pts, fill=HOLE, width=6)


def wreck(d, rng, ox, oy, fw, fh, colours, n=9, scab=True):
    """Low separated fragments of the prop's own materials in a thin creep scab, wide transparent gaps between."""
    if scab:
        for _ in range(4):
            x, y = rng.uniform(0.25, 0.75) * fw, rng.uniform(0.25, 0.75) * fh
            r = rng.uniform(0.12, 0.2) * min(fw, fh)
            d.ellipse((ox + x - r, oy + y - r * 0.6, ox + x + r, oy + y + r * 0.6), fill=CREEP_DK)
    cols = max(2, round(math.sqrt(n * fw / fh)))
    rows = max(1, math.ceil(n / cols))
    for i in range(n):
        cx = (i % cols + 0.5 + rng.uniform(-0.2, 0.2)) * fw / cols
        cy = (i // cols + 0.5 + rng.uniform(-0.2, 0.2)) * fh / rows
        s = rng.uniform(0.25, 0.4) * min(fw / cols, fh / rows)
        pts = rot([(cx - s, cy - s * 0.5), (cx + s * 0.9, cy - s * 0.7), (cx + s, cy + s * 0.4),
                   (cx - s * 0.6, cy + s * 0.6)], cx, cy, rng.uniform(0, 180))
        d.polygon([(ox + x, oy + y) for x, y in pts], fill=rng.choice(colours), outline=IRON)


# ---- props: each draws (d, rng, ox, oy, fw, fh, state) ----

def shaft(d, rng, ox, oy, fw, fh, state):
    cx, cy = ox + fw / 2, oy + fh / 2
    if state == "destroyed":
        d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill=HOLE)
        wreck(d, rng, ox, oy, fw, fh, [CONCRETE, CONCRETE_DK, STEEL], n=10, scab=False)
        d.ellipse((cx - 60, cy - 60, cx + 60, cy + 60), fill=HOLE)
        return
    if state == "clean":
        box(d, ox + 20, oy + 20, ox + fw - 20, oy + fh - 20, CONCRETE, CONCRETE_DK, 8, 14)
        d.rectangle((ox + 36, oy + 36, ox + fw - 36, oy + 52), fill=HAZARD)
        d.ellipse((cx - 112, cy - 112, cx + 112, cy + 112), fill=STEEL, outline=STEEL_DK, width=10)
        for k in range(12):
            a = k * math.pi / 6
            bx, by = cx + math.cos(a) * 94, cy + math.sin(a) * 94
            d.ellipse((bx - 7, by - 7, bx + 7, by + 7), fill=STEEL_DK)
        d.rectangle((cx - 100, cy - 9, cx + 100, cy + 9), fill=STEEL_DK)
        d.rectangle((cx - 9, cy - 100, cx + 9, cy + 100), fill=STEEL_DK)
        d.rectangle((ox + fw - 70, oy + 70, ox + fw - 40, oy + fh - 70), fill=HAZARD, outline=IRON, width=4)
        return
    # burst from below: collar heaved outward in slabs, the cap torn off and flung aside, a dark mouth
    slabs = [[(20, 20), (150, 26), (128, 112), (40, 120)], [(170, 18), (fw - 22, 24), (fw - 40, 116), (190, 108)],
             [(fw - 24, 150), (fw - 18, fh - 26), (fw - 118, fh - 40), (fw - 110, 160)],
             [(26, 168), (112, 160), (128, fh - 30), (22, fh - 22)], [(150, fh - 22), (240, fh - 30), (226, fh - 100),
                                                                       (152, fh - 104)]]
    for s in slabs:
        p = rot([(ox + x, oy + y) for x, y in s], cx, cy, rng.uniform(-6, 6))
        d.polygon(p, fill=CONCRETE, outline=CONCRETE_DK)
    d.ellipse((cx - 92, cy - 86, cx + 92, cy + 86), fill=HOLE)
    cap = rot([(ox + fw - 150, oy + 18), (ox + fw - 26, oy + 30), (ox + fw - 40, oy + 118), (ox + fw - 160, oy + 100)],
              ox + fw - 90, oy + 70, 18)
    d.polygon(cap, fill=STEEL, outline=STEEL_DK)
    reach = 150 if state != "swallowed" else 230
    anchors = [(fw / 2 - 40, fh / 2 + 60), (fw / 2 + 50, fh / 2 + 40)]
    if state == "swallowed":
        anchors += [(fw / 2 - 90, fh / 2 - 60), (fw - 70, fh - 70)]
    creep(d, rng, ox, oy, fw, fh, anchors, reach, 1.0 if state != "swallowed" else 1.6, state == "swallowed")
    d.ellipse((cx - 60, cy - 56, cx + 60, cy + 56), fill=HOLE)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh)


def shed(d, rng, ox, oy, fw, fh, state):
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [ROOF, ROOF_DK, STEEL, CONCRETE_DK])
        return
    box(d, ox + 12, oy + 12, ox + fw - 12, oy + fh - 12, ROOF, ROOF_DK, 6, 4)
    for x in range(ox + 30, ox + fw - 20, 22):
        d.line((x, oy + 16, x, oy + fh - 16), fill=ROOF_DK, width=4)
    d.rectangle((ox + 12, oy + fh / 2 - 4, ox + fw - 12, oy + fh / 2 + 4), fill=ROOF_DK)
    d.ellipse((ox + fw - 110, oy + 40, ox + fw - 50, oy + 100), fill=STEEL, outline=STEEL_DK, width=6)
    d.rectangle((ox + 40, oy + fh - 44, ox + 120, oy + fh - 12), fill=STEEL_DK)
    d.rectangle((ox - 0, oy + fh / 2 - 18, ox + 16, oy + fh / 2 + 18), fill=STEEL)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.25, fh * 0.75), (fw * 0.1, fh * 0.5)], 100)
    if state == "damaged":
        d.polygon([(ox + fw * 0.45, oy + 20), (ox + fw * 0.8, oy + 30), (ox + fw * 0.7, oy + fh * 0.6),
                   (ox + fw * 0.5, oy + fh * 0.5)], fill=HOLE)
        tears(d, rng, ox, oy, fw, fh, 3)


def pipe_run(d, rng, ox, oy, fw, fh, state):
    cy = oy + fh / 2
    if state == "destroyed":
        for x0, x1, dy in ((14, 120, -6), (150, 250, 8), (276, fw - 12, -2)):
            d.rounded_rectangle((ox + x0, cy - 26 + dy, ox + x1, cy + 26 + dy), 20, fill=STEEL, outline=STEEL_DK,
                                width=6)
        return
    d.rounded_rectangle((ox + 4, cy - 30, ox + fw - 4, cy + 30), 26, fill=STEEL, outline=STEEL_DK, width=6)
    for x in (ox + 40, ox + fw / 2, ox + fw - 40):
        d.rectangle((x - 9, cy - 38, x + 9, cy + 38), fill=STEEL_DK)
        d.rectangle((x - 22, cy + 38, x + 22, cy + 52), fill=CONCRETE_DK)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.85, fh / 2), (fw * 0.5, fh / 2)], 60, 0.8)
    if state == "damaged":
        d.rectangle((ox + fw * 0.35, cy - 34, ox + fw * 0.42, cy + 34), fill=HOLE)
        tears(d, rng, ox, oy, fw, fh, 2)


def pipe_elbow(d, rng, ox, oy, fw, fh, state):
    if state == "destroyed":
        d.rounded_rectangle((ox + 24, oy + fh - 90, ox + 120, oy + fh - 40), 20, fill=STEEL, outline=STEEL_DK, width=6)
        d.rounded_rectangle((ox + fw - 90, oy + 20, ox + fw - 40, oy + 130), 20, fill=STEEL, outline=STEEL_DK, width=6)
        return
    d.arc((ox - fw / 2 + 0, oy + fh / 2 - 0, ox + fw / 2 + 0, oy + fh * 1.5), 270, 360, fill=STEEL_DK, width=72)
    d.arc((ox - fw / 2 + 6, oy + fh / 2 + 6, ox + fw / 2 - 6, oy + fh * 1.5 - 6), 270, 360, fill=STEEL, width=60)
    d.rectangle((ox, oy + fh / 2 - 30, ox + 12, oy + fh / 2 + 30), fill=STEEL_DK)
    d.rectangle((ox + fw / 2 - 30, oy + fh - 12, ox + fw / 2 + 30, oy + fh), fill=STEEL_DK)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.3, fh * 0.7)], 70, 0.7)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 2)


def pipe_mouth(d, rng, ox, oy, fw, fh, state):
    """A pipe running into the ground: the run ends in a ragged collar and a dark hole in the earth."""
    cy = oy + fh / 2
    d.ellipse((ox + fw - 120, cy - 48, ox + fw - 16, cy + 48), fill=CONCRETE_DK)
    d.ellipse((ox + fw - 104, cy - 36, ox + fw - 32, cy + 36), fill=HOLE)
    if state == "destroyed":
        d.rounded_rectangle((ox + 12, cy - 22, ox + 90, cy + 26), 18, fill=STEEL, outline=STEEL_DK, width=6)
        return
    d.rounded_rectangle((ox + 4, cy - 30, ox + fw - 70, cy + 30), 26, fill=STEEL, outline=STEEL_DK, width=6)
    d.rectangle((ox + 60, cy - 38, ox + 78, cy + 38), fill=STEEL_DK)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw - 68, fh / 2)], 80, 0.9)
    d.ellipse((ox + fw - 92, cy - 24, ox + fw - 44, cy + 24), fill=HOLE)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 2)


def container(colour, dark):
    def draw(d, rng, ox, oy, fw, fh, state):
        if state == "destroyed":
            wreck(d, rng, ox, oy, fw, fh, [colour, dark, STEEL_DK], n=8)
            return
        x0, y0, x1, y1 = ox + 18, oy + 50, ox + fw - 18, oy + fh - 50
        box(d, x0, y0, x1, y1, colour, dark, 8, 4)
        for x in range(int(x0) + 24, int(x1) - 10, 20):
            d.line((x, y0 + 8, x, y1 - 8), fill=dark, width=5)
        d.rectangle((x0, y0, x0 + 14, y1), fill=dark)
        d.rectangle((x1 - 14, y0, x1, y1), fill=dark)
        for cx_, cy_ in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            d.rectangle((cx_ - 10, cy_ - 10, cx_ + 10, cy_ + 10), fill=IRON)
        if state == "clean":
            return
        creep(d, rng, ox, oy, fw, fh, [(fw * 0.2, fh * 0.75)], 80, 0.7)
        if state == "damaged":
            tears(d, rng, ox, oy, fw, fh, 4)
    return draw


def crustacean(d, cx, cy, size, colour):
    """A small overhead crustacean silhouette: tail, body and two claws, pointing east."""
    d.ellipse((cx - size, cy - size * 0.4, cx + size * 0.5, cy + size * 0.4), fill=colour)
    d.polygon([(cx - size, cy), (cx - size * 1.6, cy - size * 0.35), (cx - size * 1.6, cy + size * 0.35)], fill=colour)
    for side in (-1, 1):
        d.line((cx + size * 0.3, cy + side * size * 0.25, cx + size * 1.0, cy + side * size * 0.6), fill=colour,
               width=max(2, int(size * 0.2)))
        d.ellipse((cx + size * 0.9, cy + side * size * 0.6 - size * 0.25, cx + size * 1.5,
                   cy + side * size * 0.6 + size * 0.25), fill=colour)


def reefer(d, x0, y0, x1, y1):
    """A refrigerated container's body: insulated white ribbed shell, the cooling unit's grille at its east end."""
    box(d, x0, y0, x1, y1, REEFER, STEEL_DK, 8, 4)
    for x in range(int(x0) + 24, int(x1) - 70, 26):
        d.line((x, y0 + 8, x, y1 - 8), fill="#aeb3ad", width=4)
    d.rectangle((x1 - 64, y0 + 6, x1 - 8, y1 - 6), fill=STEEL_DK)
    for y in range(int(y0) + 14, int(y1) - 10, 12):
        d.line((x1 - 58, y, x1 - 14, y), fill=IRON, width=4)
    d.ellipse((x1 - 52, (y0 + y1) / 2 - 18, x1 - 18, (y0 + y1) / 2 + 18), fill=IRON, outline=STEEL, width=3)
    for cx_, cy_ in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        d.rectangle((cx_ - 10, cy_ - 10, cx_ + 10, cy_ + 10), fill=IRON)


def split_container(d, rng, ox, oy, fw, fh, state):
    """The box it came in: a refrigerated container, its side burst outward from inside, sheet peeled back in petals,
    its seafood crates thrown about inside the split."""
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [REEFER, STEEL_DK, "#5d7f8a"], n=9)
        return
    x0, y0, x1, y1 = ox + 18, oy + 50, ox + fw - 18, oy + fh - 50
    reefer(d, x0, y0, x1, y1)
    if state == "clean":
        return
    mid = (x0 + x1 - 60) / 2
    d.rectangle((mid - 110, y0 + 10, mid + 110, y1 - 10), fill=HOLE)
    for i in range(3):
        cx_ = mid - 70 + i * 70
        d.rectangle((cx_ - 26, y0 + 30 + (i % 2) * 20, cx_ + 26, y0 + 70 + (i % 2) * 20), fill="#5d7f8a",
                    outline=IRON, width=3)
    petals = [[(mid - 110, y1 - 4), (mid - 40, y1 - 4), (mid - 70, oy + fh - 6)],
              [(mid - 30, y1 - 4), (mid + 50, y1 - 4), (mid + 20, oy + fh - 4)],
              [(mid + 60, y1 - 4), (mid + 112, y1 - 4), (mid + 120, oy + fh - 10)],
              [(mid - 100, y0 + 4), (mid - 20, y0 + 4), (mid - 64, oy + 6)],
              [(mid + 10, y0 + 4), (mid + 100, y0 + 4), (mid + 70, oy + 4)]]
    for pt in petals:
        d.polygon(pt, fill=REEFER, outline=STEEL_DK)
    reach = 130 if state != "swallowed" else 200
    anchors = [(mid - ox, fh / 2), (mid - ox + 30, fh - 20)]
    if state == "swallowed":
        anchors += [(fw * 0.15, fh * 0.6), (fw * 0.85, fh * 0.4)]
    creep(d, rng, ox, oy, fw, fh, anchors, reach, 1.0 if state != "swallowed" else 1.5, state == "swallowed")
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 4)


def seafood_crates(d, rng, ox, oy, fw, fh, state):
    """A pallet of shallow seafood crates from above: ice, and the catch packed in rows. Grown, the crates are burst
    and empty, only cast shell left in them."""
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, ["#5d7f8a", ICE, TIMBER], n=8)
        return
    box(d, ox + 10, oy + 10, ox + fw - 10, oy + fh - 10, TIMBER, TIMBER_DK, 4, 3)
    for cy_ in range(2):
        for cx_ in range(2):
            x0, y0 = ox + 18 + cx_ * (fw - 36) / 2, oy + 18 + cy_ * (fh - 36) / 2
            x1, y1 = x0 + (fw - 36) / 2 - 6, y0 + (fh - 36) / 2 - 6
            d.rectangle((x0, y0, x1, y1), fill="#5d7f8a", outline=IRON, width=4)
            d.rectangle((x0 + 8, y0 + 8, x1 - 8, y1 - 8), fill=ICE if state == "clean" else HOLE)
            for k in range(2):
                crustacean(d, x0 + (x1 - x0) * 0.5, y0 + (y1 - y0) * (0.3 + 0.4 * k), (x1 - x0) * 0.16,
                           SHELL if state == "clean" else "#8a7a6a")
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.3, fh * 0.7)], 70, 0.8)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 3)


def pond(d, rng, ox, oy, fw, fh, state):
    """The park's pond: a kidney of water in a stone rim, lily pads. Grown, it is drained and its bed torn open in
    the middle, the growth welling up out of it over the rim."""
    cx, cy = ox + fw / 2, oy + fh / 2
    rim = (ox + 24, oy + 24, ox + fw - 24, oy + fh - 24)
    if state == "destroyed":
        d.ellipse((rim[0] + 30, rim[1] + 30, rim[2] - 30, rim[3] - 30), fill="#4a3c30")
        d.ellipse((cx - 50, cy - 40, cx + 50, cy + 40), fill=HOLE)
        wreck(d, rng, ox, oy, fw, fh, ["#b9b2a2", "#a7a08f"], n=10, scab=False)
        return
    d.ellipse(rim, fill="#b9b2a2", outline=CONCRETE_DK, width=6)
    inner = (rim[0] + 26, rim[1] + 26, rim[2] - 26, rim[3] - 26)
    if state == "clean":
        d.ellipse(inner, fill=WATER)
        for px, py, r in ((0.3, 0.4, 22), (0.62, 0.62, 18), (0.7, 0.32, 15)):
            d.pieslice((ox + fw * px - r, oy + fh * py - r, ox + fw * px + r, oy + fh * py + r), 30, 360, fill=GREEN)
        return
    d.ellipse(inner, fill="#4a3c30")
    d.ellipse((cx - 70, cy - 54, cx + 70, cy + 54), fill=HOLE)
    reach = 150 if state != "swallowed" else 220
    anchors = [(fw / 2, fh / 2 + 30), (fw / 2 + 60, fh / 2)]
    if state == "swallowed":
        anchors += [(fw * 0.25, fh * 0.35), (fw * 0.25, fh * 0.7)]
    creep(d, rng, ox, oy, fw, fh, anchors, reach, 1.0 if state != "swallowed" else 1.5, state == "swallowed")
    d.ellipse((cx - 44, cy - 34, cx + 44, cy + 34), fill=HOLE)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 4)


def gantry_leg(d, rng, ox, oy, fw, fh, state):
    """One end of the yard's gantry crane: a rail bogie under a braced A-frame leg."""
    cx = ox + fw / 2
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [HAZARD, STEEL, STEEL_DK], n=5)
        return
    d.rectangle((cx - 40, oy + 6, cx + 40, oy + fh - 6), fill=STEEL_DK)
    for y in (oy + 30, oy + fh - 70):
        d.rectangle((cx - 50, y, cx + 50, y + 40), fill=IRON)
    d.polygon([(cx - 34, oy + 20), (cx + 34, oy + 20), (cx + 18, oy + fh / 2), (cx - 18, oy + fh / 2)], fill=HAZARD,
              outline=IRON)
    d.polygon([(cx - 18, oy + fh / 2), (cx + 18, oy + fh / 2), (cx + 34, oy + fh - 20), (cx - 34, oy + fh - 20)],
              fill=HAZARD, outline=IRON)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw / 2, fh * 0.8)], 70, 0.8)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 2)


def gantry_span(d, rng, ox, oy, fw, fh, state):
    """The crane's overhead bridge: two box girders between the legs and the trolley with its spreader hanging."""
    if state == "destroyed":
        d.polygon([(ox + 20, oy + 40), (ox + fw * 0.45, oy + 70), (ox + fw * 0.44, oy + 110), (ox + 16, oy + 84)],
                  fill=HAZARD, outline=IRON)
        d.polygon([(ox + fw * 0.55, oy + fh - 60), (ox + fw - 20, oy + fh - 100), (ox + fw - 16, oy + fh - 56),
                   (ox + fw * 0.56, oy + fh - 20)], fill=HAZARD, outline=IRON)
        wreck(d, rng, ox, oy + fh * 0.3, fw, fh * 0.4, [STEEL, STEEL_DK], n=5, scab=False)
        return
    for y in (oy + 40, oy + fh - 88):
        d.rectangle((ox + 8, y, ox + fw - 8, y + 48), fill=HAZARD, outline=IRON, width=5)
        for x in range(ox + 30, ox + fw - 20, 40):
            d.line((x, y + 6, x + 20, y + 42), fill=IRON, width=4)
    tx = ox + fw * 0.55
    d.rectangle((tx - 70, oy + 30, tx + 70, oy + fh - 30), fill=STEEL, outline=STEEL_DK, width=6)
    d.rectangle((tx - 40, oy + fh / 2 - 30, tx + 40, oy + fh / 2 + 30), fill=IRON)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.55, fh / 2)], 70, 0.6)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 4)


def truck_unloading(d, rng, ox, oy, fw, fh, state):
    """A flatbed stopped mid-unload: cab east, deck tailgate down, its load half off the back."""
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [YELLOW, STEEL_DK, RUST, IRON], n=8)
        return
    for x in (ox + 50, ox + 120, ox + fw - 80):
        d.rectangle((x - 22, oy + 22, x + 22, oy + 46), fill=IRON)
        d.rectangle((x - 22, oy + fh - 46, x + 22, oy + fh - 22), fill=IRON)
    box(d, ox + fw - 120, oy + 44, ox + fw - 18, oy + fh - 44, YELLOW, STEEL_DK, 6, 8)
    d.rectangle((ox + fw - 46, oy + 60, ox + fw - 28, oy + fh - 60), fill=GLASS)
    box(d, ox + 20, oy + 40, ox + fw - 128, oy + fh - 40, STEEL, STEEL_DK, 6, 4)
    box(d, ox + 4, oy + 70, ox + 130, oy + fh - 70, RUST, RUST_DK, 5, 3)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.15, fh * 0.5)], 70, 0.7)
    if state == "damaged":
        d.polygon([(ox + fw - 110, oy + 50), (ox + fw - 60, oy + 56), (ox + fw - 80, oy + fh * 0.5)], fill=HOLE)
        tears(d, rng, ox, oy, fw, fh, 3)


def annex(d, rng, ox, oy, fw, fh, state):
    """The sealed annex: thick concrete walls, one airlock, roof plant. Blown outward from inside."""
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [CONCRETE, CONCRETE_DK, STEEL, "#d8d8d0"], n=14)
        return
    x0, y0, x1, y1 = ox + 24, oy + 24, ox + fw - 24, oy + fh - 24
    if state == "clean":
        box(d, x0, y0, x1, y1, CONCRETE, CONCRETE_DK, 14, 6)
        box(d, x0 + 40, y0 + 40, x1 - 40, y1 - 40, "#a7a69c", CONCRETE_DK, 6, 4)
        for i in range(3):
            d.ellipse((x0 + 80 + i * 110, y0 + 70, x0 + 150 + i * 110, y0 + 140), fill=STEEL, outline=STEEL_DK,
                      width=6)
        d.rectangle((x0 + 70, y1 - 150, x1 - 70, y1 - 110), fill=STEEL_DK)
        d.rectangle(((x0 + x1) / 2 - 50, y1 - 14, (x0 + x1) / 2 + 50, y1 + 18), fill=HAZARD, outline=IRON, width=4)
        for x in range(int(x0) + 10, int(x1) - 10, 36):
            d.rectangle((x, y0 - 4, x + 18, y0 + 10), fill=HAZARD)
        return
    # blown outward: the roof gone, the walls lying as slabs thrown well clear of the floor on every side, the floor
    # and its burst containment wells open to the sky
    fx0, fy0, fx1, fy1 = ox + 110, oy + 100, ox + fw - 110, oy + fh - 100
    d.rectangle((fx0, fy0, fx1, fy1), fill="#cfcfc6")
    for i in range(3):
        cx_, cy_ = fx0 + 70 + i * (fx1 - fx0 - 140) / 2, fy0 + 70
        d.ellipse((cx_ - 34, cy_ - 34, cx_ + 34, cy_ + 34), fill=WATER_DK, outline=STEEL, width=8)
        d.ellipse((cx_ - 22, cy_ - 22, cx_ + 22, cy_ + 22), fill=HOLE)
        for k in range(5):
            a = k * 1.3
            d.polygon([(cx_ + math.cos(a) * 26, cy_ + math.sin(a) * 26),
                       (cx_ + math.cos(a + 0.3) * 40, cy_ + math.sin(a + 0.3) * 40),
                       (cx_ + math.cos(a + 0.5) * 28, cy_ + math.sin(a + 0.5) * 28)], fill=GLASS)
    cx, cy = ox + fw / 2, oy + fh / 2
    walls = [(ox + fw * 0.27, oy + 34, 230, 40, -9), (ox + fw * 0.72, oy + 30, 230, 40, 12),
             (ox + fw * 0.26, oy + fh - 34, 230, 40, 8), (ox + fw * 0.74, oy + fh - 38, 220, 40, -14),
             (ox + 34, oy + fh * 0.32, 40, 170, 10), (ox + 30, oy + fh * 0.72, 40, 150, -8),
             (ox + fw - 34, oy + fh * 0.3, 40, 160, -12), (ox + fw - 30, oy + fh * 0.7, 40, 160, 9)]
    for wx, wy, ww, wh, deg in walls:
        d.polygon(rot([(wx - ww / 2, wy - wh / 2), (wx + ww / 2, wy - wh / 2), (wx + ww / 2, wy + wh / 2),
                       (wx - ww / 2, wy + wh / 2)], wx, wy, deg), fill=CONCRETE, outline=CONCRETE_DK)
    for _ in range(22):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(0.62, 0.8)
        px, py = cx + math.cos(a) * r * fw / 2, cy + math.sin(a) * r * fh / 2
        s = rng.uniform(6, 13)
        d.rectangle((px - s, py - s, px + s, py + s), fill=CONCRETE_DK)
    reach = 170 if state != "swallowed" else 250
    anchors = [(fw / 2, fh * 0.6), (fw * 0.35, fh * 0.35)]
    if state == "swallowed":
        anchors += [(fw * 0.75, fh * 0.3), (fw * 0.2, fh * 0.75), (fw * 0.8, fh * 0.75)]
    creep(d, rng, ox, oy, fw, fh, anchors, reach, 1.1 if state != "swallowed" else 1.6, state == "swallowed")
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 6)


def tank_rack(d, rng, ox, oy, fw, fh, state):
    """A rack of three water-filled specimen tanks on a frame, a crustacean in each; grown, their glass is broken
    from inside and they are empty."""
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [STEEL, STEEL_DK, GLASS], n=6)
        return
    box(d, ox + 10, oy + 16, ox + fw - 10, oy + fh - 16, STEEL_DK, IRON, 5, 4)
    for i in range(3):
        cx_ = ox + 46 + i * (fw - 92) / 2
        d.ellipse((cx_ - 34, oy + fh / 2 - 34, cx_ + 34, oy + fh / 2 + 34), fill=WATER, outline=STEEL, width=8)
        if state == "clean":
            crustacean(d, cx_ + 4, oy + fh / 2, 14, SHELL)
        else:
            d.ellipse((cx_ - 18, oy + fh / 2 - 18, cx_ + 18, oy + fh / 2 + 18), fill=HOLE)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.3, fh / 2)], 60, 0.7)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 3)


def hazard_locker(d, rng, ox, oy, fw, fh, state):
    """A sealed sample freezer chest, 2x1: lid with locking bars and a hazard band."""
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, ["#d8d8d0", STEEL_DK, HAZARD], n=6)
        return
    box(d, ox + 12, oy + 20, ox + fw - 12, oy + fh - 20, "#d8d8d0", STEEL_DK, 6, 8)
    d.rectangle((ox + 12, oy + 20, ox + 36, oy + fh - 20), fill=HAZARD)
    for x in (ox + 90, ox + 160):
        d.rectangle((x, oy + 26, x + 14, oy + fh - 26), fill=STEEL_DK)
    if state == "clean":
        return
    d.polygon([(ox + 120, oy + 28), (ox + fw - 20, oy + 22), (ox + fw - 40, oy + fh - 30), (ox + 150, oy + fh - 24)],
              fill=HOLE)
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.7, fh / 2)], 60, 0.7)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 3)


def bandstand(d, rng, ox, oy, fw, fh, state):
    """An octagonal park bandstand seen from above: a pitched octagon roof with a finial, a ring of posts, steps."""
    cx, cy = ox + fw / 2, oy + fh / 2
    if state == "destroyed":
        oct_ = [(cx + math.cos(k * math.pi / 4 + math.pi / 8) * 200, cy + math.sin(k * math.pi / 4 + math.pi / 8) * 200)
                for k in range(8)]
        d.polygon(oct_, fill="#b9b2a2", outline=CONCRETE_DK)
        wreck(d, rng, ox, oy, fw, fh, [ROOF, ROOF_DK, "#e8e2d2", TIMBER], n=12, scab=True)
        return
    d.polygon([(cx - 60, cy + 196), (cx + 60, cy + 196), (cx + 70, oy + fh - 6), (cx - 70, oy + fh - 6)],
              fill="#b9b2a2", outline=CONCRETE_DK)
    oct_ = [(cx + math.cos(k * math.pi / 4 + math.pi / 8) * 210, cy + math.sin(k * math.pi / 4 + math.pi / 8) * 210)
            for k in range(8)]
    d.polygon(oct_, fill=ROOF, outline="#e8e2d2")
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        d.line((cx, cy, cx + math.cos(a) * 210, cy + math.sin(a) * 210), fill="#e8e2d2", width=8)
        px, py = cx + math.cos(a) * 196, cy + math.sin(a) * 196
        d.ellipse((px - 12, py - 12, px + 12, py + 12), fill="#e8e2d2")
    d.ellipse((cx - 22, cy - 22, cx + 22, cy + 22), fill=HAZARD, outline=IRON, width=4)
    if state == "clean":
        return
    # half swallowed: the growth rises over one side of the roof, which sags into it
    reach = 190 if state != "swallowed" else 260
    anchors = [(fw * 0.25, fh * 0.35), (fw * 0.3, fh * 0.7)]
    if state == "swallowed":
        anchors += [(fw * 0.5, fh * 0.5), (fw * 0.65, fh * 0.25)]
    creep(d, rng, ox, oy, fw, fh, anchors, reach, 1.2 if state != "swallowed" else 1.6, state == "swallowed")
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 5)


def swings(d, rng, ox, oy, fw, fh, state):
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [RED, IRON, TIMBER], n=5, scab=True)
        return
    d.rectangle((ox + 14, oy + fh / 2 - 8, ox + fw - 14, oy + fh / 2 + 8), fill=RED, outline=IRON, width=3)
    for x in (ox + 20, ox + fw - 20):
        d.line((x - 18, oy + 30, x + 18, oy + fh - 30), fill=RED, width=12)
        d.line((x + 18, oy + 30, x - 18, oy + fh - 30), fill=RED, width=12)
    for x in (ox + fw * 0.36, ox + fw * 0.64):
        d.line((x - 22, oy + fh / 2, x - 22, oy + fh / 2 + 70), fill=IRON, width=4)
        d.line((x + 22, oy + fh / 2, x + 22, oy + fh / 2 + 70), fill=IRON, width=4)
        d.rectangle((x - 30, oy + fh / 2 + 62, x + 30, oy + fh / 2 + 80), fill=TIMBER, outline=TIMBER_DK)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.2, fh * 0.6)], 70, 0.7)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 2)


def slide(d, rng, ox, oy, fw, fh, state):
    cy = oy + fh / 2
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [YELLOW, IRON, BLUE], n=5)
        return
    d.rectangle((ox + 16, cy - 40, ox + 100, cy + 40), fill=BLUE, outline=IRON, width=5)
    for x in range(ox + 26, ox + 96, 16):
        d.line((x, cy - 34, x, cy + 34), fill=IRON, width=4)
    d.polygon([(ox + 100, cy - 30), (ox + fw - 30, cy - 34), (ox + fw - 14, cy), (ox + fw - 30, cy + 34),
               (ox + 100, cy + 30)], fill=YELLOW, outline=IRON)
    d.line((ox + 110, cy, ox + fw - 40, cy), fill="#e8c860", width=10)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.85, fh / 2)], 60, 0.7)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 2)


def climber(d, rng, ox, oy, fw, fh, state):
    """A climbing frame: a domed lattice of bars seen from above, a platform at its crown."""
    cx, cy = ox + fw / 2, oy + fh / 2
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [GREEN, IRON, TIMBER], n=6)
        return
    for r in (110, 80, 50):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=GREEN, width=10)
    for k in range(8):
        a = k * math.pi / 4
        d.line((cx + math.cos(a) * 30, cy + math.sin(a) * 30, cx + math.cos(a) * 112, cy + math.sin(a) * 112),
               fill=GREEN, width=10)
    d.rectangle((cx - 26, cy - 26, cx + 26, cy + 26), fill=TIMBER, outline=TIMBER_DK, width=4)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.3, fh * 0.65)], 70, 0.8)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 3)


def roundabout(d, rng, ox, oy, fw, fh, state):
    cx, cy = ox + fw / 2, oy + fh / 2
    if state == "destroyed":
        d.ellipse((cx - 80, cy - 70, cx + 70, cy + 80), fill=STEEL_DK)
        wreck(d, rng, ox, oy, fw, fh, [RED, YELLOW, IRON], n=5, scab=False)
        return
    d.ellipse((cx - 108, cy - 108, cx + 108, cy + 108), fill=RED, outline=IRON, width=6)
    for k in range(4):
        a = k * math.pi / 2 + (math.radians(25) if state != "clean" else 0)
        d.pieslice((cx - 108, cy - 108, cx + 108, cy + 108), math.degrees(a), math.degrees(a) + 45, fill=YELLOW)
        d.line((cx, cy, cx + math.cos(a + 0.4) * 96, cy + math.sin(a + 0.4) * 96), fill=IRON, width=10)
    d.ellipse((cx - 22, cy - 22, cx + 22, cy + 22), fill=IRON)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.7, fh * 0.65)], 80, 0.8)
    if state == "damaged":
        tears(d, rng, ox, oy, fw, fh, 2)


def bench(d, rng, ox, oy, fw, fh, state):
    cy = oy + fh / 2
    if state == "destroyed":
        wreck(d, rng, ox, oy, fw, fh, [TIMBER, IRON], n=5, scab=False)
        return
    for x in (ox + 30, ox + fw - 30):
        d.rectangle((x - 9, cy - 44, x + 9, cy + 44), fill=IRON)
    for k in range(4):
        y = cy - 38 + k * 21
        d.rectangle((ox + 14, y, ox + fw - 14, y + 14), fill=TIMBER, outline=TIMBER_DK, width=3)
    if state == "clean":
        return
    creep(d, rng, ox, oy, fw, fh, [(fw * 0.25, fh * 0.6)], 50, 0.6)
    if state == "damaged":
        d.rectangle((ox + fw * 0.45, cy - 40, ox + fw * 0.55, cy + 40), fill=HOLE)


# ---- kits ----
BASE = ["clean", "grown", "damaged", "destroyed"]
TELL = ["clean", "grown", "swallowed", "damaged", "destroyed"]

KITS = {
    # name: (columns of (id, footprint w, h, draw, states, telling))
    "workings-shaft": [("origin-workings.shaft", 3, 3, shaft, TELL, True)],
    "workings-yard": [("origin-workings.pump-shed", 3, 2, shed, BASE, False),
                      ("origin-workings.pipe-run", 3, 1, pipe_run, BASE, False),
                      ("origin-workings.pipe-elbow", 2, 2, pipe_elbow, BASE, False),
                      ("origin-workings.pipe-mouth", 2, 1, pipe_mouth, BASE, False)],
    "freight-split": [("origin-freight.split-container", 4, 2, split_container, TELL, True)],
    "freight-boxes": [("origin-freight.container-rust", 4, 2, container(RUST, RUST_DK), BASE, False),
                      ("origin-freight.container-blue", 4, 2, container(BLUE, BLUE_DK), BASE, False),
                      ("origin-freight.seafood-crates", 2, 2, seafood_crates, BASE, False)],
    "freight-crane": [("origin-freight.gantry-span", 7, 3, gantry_span, BASE, False),
                      ("origin-freight.gantry-leg", 1, 3, gantry_leg, BASE, False),
                      ("origin-freight.unloading-truck", 3, 2, truck_unloading, BASE, False)],
    "lab-annex": [("origin-lab.annex", 5, 4, annex, TELL, True)],
    "lab-kit": [("origin-lab.tank-rack", 3, 1, tank_rack, BASE, False),
                ("origin-lab.sample-freezer", 2, 1, hazard_locker, BASE, False)],
    "park-bandstand": [("origin-park.bandstand", 4, 4, bandstand, TELL, True)],
    "park-pond": [("origin-park.pond", 4, 3, pond, TELL, True)],
    "park-play": [("origin-park.swings", 3, 2, swings, BASE, False),
                  ("origin-park.slide", 3, 1, slide, BASE, False),
                  ("origin-park.climber", 2, 2, climber, BASE, False),
                  ("origin-park.roundabout", 2, 2, roundabout, BASE, False),
                  ("origin-park.bench", 2, 1, bench, BASE, False)],
}


def layout(columns):
    """Single-prop kits lay their states out as a grid; multi-prop kits a column per prop, a row per state."""
    slots = []
    if len(columns) == 1:
        pid, w, h, draw, states, _ = columns[0]
        per_row = 2 if len(states) <= 4 else 3
        sw, sh = w * CELL + 2 * GUTTER, h * CELL + 2 * GUTTER
        for i, st in enumerate(states):
            slots.append((pid, st, (i % per_row) * sw + GUTTER, (i // per_row) * sh + GUTTER, w, h, draw))
        rows = math.ceil(len(states) / per_row)
        return slots, per_row * sw, rows * sh
    x = 0
    row_h = max(h for _, _, h, _, _, _ in columns) * CELL + 2 * GUTTER
    n_rows = max(len(s) for *_, s, _ in columns)
    for pid, w, h, draw, states, _ in columns:
        sw = w * CELL + 2 * GUTTER
        for r, st in enumerate(states):
            slots.append((pid, st, x + GUTTER, r * row_h + GUTTER + (row_h - 2 * GUTTER - h * CELL) // 2, w, h, draw))
        x += sw
    return slots, x, n_rows * row_h


def main():
    geometry = {"cellPx": CELL, "kits": {}}
    for kit, columns in KITS.items():
        slots, width, height = layout(columns)
        img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        entries = []
        for pid, st, ox, oy, w, h, draw in slots:
            rng = random.Random(f"{pid}:{st if st != 'damaged' else 'grown'}")
            fw, fh = w * CELL, h * CELL
            MAT.clear()
            layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
            draw(ImageDraw.Draw(layer), rng, ox, oy, fw, fh, st)
            under = ImageDraw.Draw(img)
            for x, y, r in MAT:
                under.ellipse((x - r, y - r, x + r, y + r), fill=CREEP)
            img.alpha_composite(layer)
            entries.append({"id": pid, "state": st, "rect": [ox, oy, fw, fh], "footprintCells": [w, h]})
        out = TEMPLATES / f"heart-{kit}-guide.png"
        img.save(out)
        geometry["kits"][kit] = {"guide": f"art-source/templates/{out.name}", "size": [width, height],
                                 "entries": entries}
        print(out.name, img.size)
    (HERE / "geometry.json").write_text(json.dumps(geometry, indent=1) + "\n")


if __name__ == "__main__":
    main()
