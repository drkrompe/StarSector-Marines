"""Midline and high-tech emplacement styles: guides, briefs and generations for six art-direction options.

Phase A drew every option as a draft (collar, autogun, cannon, plinth, heavy head). The CHOSEN options (phase B) add
the second point sheet (round collar, grenade-launcher and point-defence heads) and a stepped militia plinth
(hardpoint-v2), and export_ship.py cuts them into the shipped styles (modular, barrier, composite, deployed).

Each tech family (`tech-families.md`) gets a prepared and a militia variant, and each family is drawn three ways
(OPTIONS). A family owns its silhouette, so its heads and bases have their own guide geometry here; the three options
of one family share that geometry and differ in material, livery and dressing. Every head keeps the brutalist head's
barrel tips (make_guide.py), so one set of muzzles can serve every look in phase B.

Sheets follow make_guide.py's frames exactly (1536x1024):
  <family>-<variant>-points-guide.png      collar, autogun head, cannon head (400 px a cell, 500 px frames at y=512)
  <family>-<variant>-hardpoint-guide.png   3x3 plinth (256 px a cell, top at y=128), heavy head (frame at x=768)

Commands:
  python art-source/ground-turrets/family_drafts.py guides        writes guides/
  python art-source/ground-turrets/family_drafts.py prompts       writes prompts/<option>-<variant>-<sheet>.prompt.txt
  python art-source/ground-turrets/family_drafts.py generate [option ...]   one Codex generation per missing raw
  python art-source/ground-turrets/family_drafts.py export        cuts raws into build/ground-turret-art/<option>-<variant>/

The `export` cut is draft art for the `ground-turret-family-draft` snapshot suite only; the shipped sets come from
export_ship.py.
"""
import math
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from make_guide import Canvas, SS, g, W, H, PLINTH_OUT, PLINTH_DECK  # noqa: E402
from export import OUT as ART, PPC, contact_shadow, fitted, masked, plinth_with_shadow  # noqa: E402
from export_ship import fill_pit  # noqa: E402

ROOT = HERE.parent.parent
GUIDES = HERE / "guides"
PROMPTS = HERE / "prompts"
RAW = HERE / "raw"
FAMILIES = ("midline", "hightech")
VARIANTS = ("prepared", "militia")
STYLE_REF = ROOT / "mod" / "graphics" / "structures" / "missile-aa-base.png"


# ---- shared helpers ------------------------------------------------------------------------

def seam(c, x0, y0, x1, y1, ppc, v=70, w=0.012):
    """A thin panel seam between two points (cells relative to the caller's origin already applied)."""
    c.d.line([(x0 * SS, y0 * SS), (x1 * SS, y1 * SS)], fill=g(v), width=max(1, int(w * ppc * SS)))


def clip_round(c, x0, y0, x1, y1, radius):
    """Clears everything in the box outside a rounded rectangle of that radius (pixels)."""
    mask = Image.new("L", c.im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], radius * SS, fill=255)
    box = Image.new("L", c.im.size, 0)
    ImageDraw.Draw(box).rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], fill=255)
    a = c.im.getchannel("A")
    from PIL import ImageChops
    keep = ImageChops.lighter(ImageChops.multiply(a, mask), ImageChops.multiply(a, ImageChops.invert(box)))
    c.im.putalpha(keep)


def barrels_twin(c, cx, cy, ppc, shroud=True):
    """The brutalist autogun's two tubes, tips at y = -0.60 cell, x = +/-0.065."""
    for sx in (-0.065, 0.065):
        c.rect(cx + (sx - 0.022) * ppc, cy - 0.58 * ppc, cx + (sx + 0.022) * ppc, cy - 0.10 * ppc, g(70))
        if shroud:
            c.rect(cx + (sx - 0.035) * ppc, cy - 0.40 * ppc, cx + (sx + 0.035) * ppc, cy - 0.18 * ppc, g(105))
        c.rect(cx + (sx - 0.03) * ppc, cy - 0.60 * ppc, cx + (sx + 0.03) * ppc, cy - 0.55 * ppc, g(60))


def barrel_single(c, cx, cy, ppc, top=0.60, half=0.035, brake=0.055, root=0.15):
    c.rect(cx - half * ppc, cy - top * ppc, cx + half * ppc, cy - root * ppc, g(80))
    if brake:
        c.rect(cx - brake * ppc, cy - (top + 0.01) * ppc, cx + brake * ppc, cy - (top - 0.08) * ppc, g(60))


# ---- midline: pressed modular panels, sealed seams, standard fittings ------------------------

def ml_autogun(c, cx, cy, ppc):
    """Boxy panelled housing: a rounded rectangle with a flat lit front plate, a side feed box and panel seams."""
    c.ellipse(cx, cy, 0.25 * ppc, g(85))
    barrels_twin(c, cx, cy, ppc)
    c.rect(cx - 0.11 * ppc, cy - 0.26 * ppc, cx + 0.11 * ppc, cy - 0.18 * ppc, g(120))  # flat barrel cradle plate
    c.rect(cx - 0.22 * ppc, cy - 0.20 * ppc, cx + 0.22 * ppc, cy + 0.24 * ppc, g(150), radius=0.05 * ppc)
    c.rect(cx - 0.22 * ppc, cy - 0.20 * ppc, cx + 0.22 * ppc, cy - 0.11 * ppc, g(192), radius=0.04 * ppc)  # front plate
    c.rect(cx - 0.22 * ppc, cy - 0.11 * ppc, cx - 0.17 * ppc, cy + 0.20 * ppc, g(172))  # lit west flank
    seam(c, cx - 0.17 * ppc, cy + 0.04 * ppc, cx + 0.22 * ppc, cy + 0.04 * ppc, ppc)
    seam(c, cx + 0.02 * ppc, cy - 0.11 * ppc, cx + 0.02 * ppc, cy + 0.24 * ppc, ppc)
    c.rect(cx - 0.14 * ppc, cy + 0.08 * ppc, cx - 0.02 * ppc, cy + 0.19 * ppc, g(128), radius=0.02 * ppc)  # hatch
    for k in range(4):  # vent louvres
        y = cy + (0.08 + 0.03 * k) * ppc
        c.rect(cx + 0.06 * ppc, y, cx + 0.17 * ppc, y + 0.012 * ppc, g(95))
    c.rect(cx + 0.22 * ppc, cy - 0.08 * ppc, cx + 0.32 * ppc, cy + 0.16 * ppc, g(118), radius=0.02 * ppc)  # feed box
    c.rect(cx + 0.24 * ppc, cy - 0.06 * ppc, cx + 0.30 * ppc, cy - 0.03 * ppc, g(160))
    c.rect(cx - 0.20 * ppc, cy - 0.17 * ppc, cx - 0.12 * ppc, cy - 0.13 * ppc, g(40))  # sight window


def ml_cannon(c, cx, cy, ppc):
    """Flat-sided panelled turret with a square mantlet box, two hatches and a rear vent grille."""
    c.ellipse(cx, cy, 0.28 * ppc, g(85))
    barrel_single(c, cx, cy, ppc)
    c.rect(cx - 0.05 * ppc, cy - 0.32 * ppc, cx + 0.05 * ppc, cy - 0.22 * ppc, g(112))  # sleeve
    body = [(cx - 0.22 * ppc, cy - 0.22 * ppc), (cx + 0.22 * ppc, cy - 0.22 * ppc), (cx + 0.27 * ppc, cy - 0.15 * ppc),
            (cx + 0.27 * ppc, cy + 0.30 * ppc), (cx - 0.27 * ppc, cy + 0.30 * ppc), (cx - 0.27 * ppc, cy - 0.15 * ppc)]
    c.poly(body, g(145))
    c.rect(cx - 0.13 * ppc, cy - 0.27 * ppc, cx + 0.13 * ppc, cy - 0.13 * ppc, g(186))  # mantlet box
    c.rect(cx - 0.27 * ppc, cy - 0.15 * ppc, cx - 0.21 * ppc, cy + 0.30 * ppc, g(170))  # lit west skirt
    seam(c, cx - 0.21 * ppc, cy + 0.0 * ppc, cx + 0.27 * ppc, cy + 0.0 * ppc, ppc)
    seam(c, cx - 0.21 * ppc, cy + 0.20 * ppc, cx + 0.27 * ppc, cy + 0.20 * ppc, ppc)
    c.rect(cx + 0.05 * ppc, cy - 0.08 * ppc, cx + 0.19 * ppc, cy + 0.10 * ppc, g(125), radius=0.02 * ppc)  # hatch
    c.ellipse(cx - 0.09 * ppc, cy + 0.08 * ppc, 0.05 * ppc, g(118))  # periscope
    for k in range(5):
        x = cx + (-0.17 + 0.07 * k) * ppc
        c.rect(x, cy + 0.23 * ppc, x + 0.035 * ppc, cy + 0.28 * ppc, g(90))  # rear grille


def ml_heavy(c, cx, cy, ppc):
    """Modular heavy turret: a slab body with chamfered front, bolted side bins along both flanks and panel seams."""
    c.ellipse(cx, cy, 0.56 * ppc, g(85))
    c.rect(cx - 0.075 * ppc, cy - 1.42 * ppc, cx + 0.075 * ppc, cy - 0.40 * ppc, g(80))
    c.rect(cx - 0.12 * ppc, cy - 1.44 * ppc, cx + 0.12 * ppc, cy - 1.26 * ppc, g(60))
    c.rect(cx - 0.10 * ppc, cy - 0.90 * ppc, cx + 0.10 * ppc, cy - 0.70 * ppc, g(110))
    body = [(cx - 0.40 * ppc, cy - 0.55 * ppc), (cx + 0.40 * ppc, cy - 0.55 * ppc), (cx + 0.50 * ppc, cy - 0.40 * ppc),
            (cx + 0.50 * ppc, cy + 0.72 * ppc), (cx - 0.50 * ppc, cy + 0.72 * ppc), (cx - 0.50 * ppc, cy - 0.40 * ppc)]
    c.poly(body, g(145))
    c.poly([(cx - 0.40 * ppc, cy - 0.55 * ppc), (cx + 0.40 * ppc, cy - 0.55 * ppc), (cx + 0.46 * ppc, cy - 0.40 * ppc),
            (cx - 0.46 * ppc, cy - 0.40 * ppc)], g(192))
    c.rect(cx - 0.22 * ppc, cy - 0.62 * ppc, cx + 0.22 * ppc, cy - 0.44 * ppc, g(178))  # mantlet box
    for side in (-1, 1):  # side bins
        x0, x1 = (cx - 0.58 * ppc, cx - 0.50 * ppc) if side < 0 else (cx + 0.50 * ppc, cx + 0.58 * ppc)
        for k in range(3):
            y0 = cy + (-0.30 + 0.32 * k) * ppc
            c.rect(x0, y0, x1, y0 + 0.28 * ppc, g(172 if side < 0 else 110), radius=0.015 * ppc)
    for y in (-0.10, 0.25, 0.55):
        seam(c, cx - 0.50 * ppc, cy + y * ppc, cx + 0.50 * ppc, cy + y * ppc, ppc)
    seam(c, cx, cy - 0.40 * ppc, cx, cy + 0.72 * ppc, ppc)
    c.rect(cx + 0.12 * ppc, cy - 0.02 * ppc, cx + 0.36 * ppc, cy + 0.20 * ppc, g(125), radius=0.02 * ppc)  # cupola hatch
    c.ellipse(cx + 0.24 * ppc, cy + 0.09 * ppc, 0.07 * ppc, g(150))
    c.rect(cx - 0.34 * ppc, cy + 0.05 * ppc, cx - 0.10 * ppc, cy + 0.22 * ppc, g(128), radius=0.02 * ppc)
    for k in range(6):
        x = cx + (-0.32 + 0.11 * k) * ppc
        c.rect(x, cy + 0.60 * ppc, x + 0.06 * ppc, cy + 0.68 * ppc, g(95))  # rear grille


def ml_collar(c, cx, cy, ppc, variant):
    if variant == "prepared":
        # Square precast ring of eight panels with chamfered corners, recessed strip lamps on two faces, bolt heads.
        s, ch = 0.47 * ppc, 0.08 * ppc
        c.poly([(cx - s + ch, cy - s), (cx + s - ch, cy - s), (cx + s, cy - s + ch), (cx + s, cy + s - ch),
                (cx + s - ch, cy + s), (cx - s + ch, cy + s), (cx - s, cy + s - ch), (cx - s, cy - s + ch)], g(125))
        c.rect(cx - s + ch, cy - s, cx + s - ch, cy - s + 0.06 * ppc, g(178))
        c.rect(cx - s, cy - s + ch, cx - s + 0.06 * ppc, cy + s - ch, g(178))
        c.rect(cx - 0.38 * ppc, cy - 0.38 * ppc, cx + 0.38 * ppc, cy + 0.38 * ppc, g(148), radius=0.04 * ppc)
        for a in (-0.38, 0.38):  # panel joints: a 3x3 grid broken by the well
            seam(c, cx + a * 0.34 * ppc, cy - 0.38 * ppc, cx + a * 0.34 * ppc, cy - 0.30 * ppc, ppc)
        for t in (-0.13, 0.13):
            seam(c, cx + t * ppc, cy - 0.38 * ppc, cx + t * ppc, cy - 0.28 * ppc, ppc)
            seam(c, cx + t * ppc, cy + 0.28 * ppc, cx + t * ppc, cy + 0.38 * ppc, ppc)
            seam(c, cx - 0.38 * ppc, cy + t * ppc, cx - 0.28 * ppc, cy + t * ppc, ppc)
            seam(c, cx + 0.28 * ppc, cy + t * ppc, cx + 0.38 * ppc, cy + t * ppc, ppc)
        c.rect(cx - 0.10 * ppc, cy - 0.44 * ppc, cx + 0.10 * ppc, cy - 0.415 * ppc, g(235))  # strip lamps
        c.rect(cx - 0.44 * ppc, cy - 0.10 * ppc, cx - 0.415 * ppc, cy + 0.10 * ppc, g(235))
        c.ellipse(cx, cy, 0.33 * ppc, g(100))
        c.ellipse(cx, cy, 0.30 * ppc, g(45))
        for sx in (-1, 1):
            for sy in (-1, 1):
                c.ellipse(cx + sx * 0.33 * ppc, cy + sy * 0.33 * ppc, 0.025 * ppc, g(90))
    else:
        # Collapsible mesh barrier cells: eight square bins round a dug well, lit faces north-west.
        c.ellipse(cx, cy, 0.36 * ppc, g(70))
        cells = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
        for ix, iy in cells:
            bx, by = cx + ix * 0.32 * ppc, cy + iy * 0.32 * ppc
            r = 0.145 * ppc
            lit = 185 if (ix + iy) < 0 or (ix + iy == 0 and ix < 0) else 128
            c.rect(bx - r, by - r, bx + r, by + r, g(lit - 30))
            c.rect(bx - r + 0.02 * ppc, by - r + 0.02 * ppc, bx + r - 0.02 * ppc, by + r - 0.02 * ppc, g(lit))
            for k in (-1, 0, 1):  # mesh lines
                seam(c, bx + k * 0.06 * ppc, by - r, bx + k * 0.06 * ppc, by + r, ppc, v=lit - 45, w=0.006)
        c.ellipse(cx, cy, 0.30 * ppc, g(55))
        c.ellipse(cx, cy, 0.24 * ppc, g(40))


def ml_plinth(c, ox, oy, ppc, variant):
    o0, o1 = PLINTH_OUT
    d0, d1 = PLINTH_DECK

    def P(x, y):
        return ox + x * ppc, oy + y * ppc

    if variant == "prepared":
        # Stepped modular block: a foot terrace, then panelled battered walls to a plated deck; square corners.
        m = 0.20
        c.poly([P(o0, o0), P(o1, o0), P(o1, o1), P(o0, o1)], g(118))  # foot terrace
        c.poly([P(o0, o0), P(o1, o0), P(o1 - 0.05, o0 + 0.05), P(o0 + 0.05, o0 + 0.05)], g(170))
        c.poly([P(o0, o0), P(o0 + 0.05, o0 + 0.05), P(o0 + 0.05, o1 - 0.05), P(o0, o1)], g(185))
        a0, a1 = o0 + m, o1 - m
        c.poly([P(a0, a0), P(a1, a0), P(d1, d0), P(d0, d0)], g(178))   # north wall, lit
        c.poly([P(a0, a0), P(d0, d0), P(d0, d1), P(a0, a1)], g(205))   # west, lit
        c.poly([P(a1, a0), P(a1, a1), P(d1, d1), P(d1, d0)], g(108))   # east, shade
        c.poly([P(a0, a1), P(d0, d1), P(d1, d1), P(a1, a1)], g(82))    # south, shade
        for k in range(1, 6):  # vertical panel joints across the walls
            t = k / 6
            x = a0 + (a1 - a0) * t
            xd = d0 + (d1 - d0) * t
            seam(c, *P(x, a0), *P(xd, d0), ppc / 2, v=120)
            seam(c, *P(x, a1), *P(xd, d1), ppc / 2, v=60)
            y = a0 + (a1 - a0) * t
            yd = d0 + (d1 - d0) * t
            seam(c, *P(a0, y), *P(d0, yd), ppc / 2, v=140)
            seam(c, *P(a1, y), *P(d1, yd), ppc / 2, v=70)
        c.poly([P(d0, d0), P(d1, d0), P(d1, d1), P(d0, d1)], g(172))
        inset = 0.07
        c.poly([P(d0 + inset, d0 + inset), P(d1 - inset, d0 + inset), P(d1 - inset, d1 - inset),
                P(d0 + inset, d1 - inset)], g(150))
        for t in (1.0, 2.0):  # deck plate joints
            seam(c, *P(d0 + inset, t), *P(d1 - inset, t), ppc / 2, v=110)
            seam(c, *P(t, d0 + inset), *P(t, d1 - inset), ppc / 2, v=110)
        for x0, y0, x1, y1 in ((d0 + 0.2, d0 + 0.03, d0 + 0.8, d0 + 0.06), (d0 + 0.03, d0 + 0.2, d0 + 0.06, d0 + 0.8)):
            c.poly([P(x0, y0), P(x1, y0), P(x1, y1), P(x0, y1)], g(238))  # recessed strip lamps on the parapet
    else:
        # Barrier-cell bastion, stepped so it reads as height: three courses of mesh bins, each set back from the one
        # below, every course showing a lit top on the north and west and a dark face on the south and east, over a
        # cast-shadow band at the foot (phase B: the flat grid of phase A read as a paved square, not a tower).
        courses = 3
        for k in range(courses):
            t0 = o0 + (d0 - o0) * k / courses
            t1 = o0 + (d0 - o0) * (k + 1) / courses
            u0, u1 = o1 - (o1 - d1) * k / courses, o1 - (o1 - d1) * (k + 1) / courses
            c.poly([P(t0, t0), P(u0, t0), P(u1, t1), P(t1, t1)], g(150 + 12 * k))  # north top
            c.poly([P(t0, t0), P(t1, t1), P(t1, u1), P(t0, u0)], g(170 + 12 * k))  # west top
            c.poly([P(u0, t0), P(u0, u0), P(u1, u1), P(u1, t1)], g(76 + 6 * k))    # east face, shade
            c.poly([P(t0, u0), P(t1, u1), P(u1, u1), P(u0, u0)], g(58 + 6 * k))    # south face, shade
            cells = 9 - 2 * k
            for side in range(4):
                for j in range(1, cells):
                    f = j / cells
                    if side == 0:
                        seam(c, *P(t0 + (u0 - t0) * f, t0), *P(t1 + (u1 - t1) * f, t1), ppc / 2, v=110, w=0.012)
                    elif side == 1:
                        seam(c, *P(t0, t0 + (u0 - t0) * f), *P(t1, t1 + (u1 - t1) * f), ppc / 2, v=130, w=0.012)
                    elif side == 2:
                        seam(c, *P(u0, t0 + (u0 - t0) * f), *P(u1, t1 + (u1 - t1) * f), ppc / 2, v=45, w=0.012)
                    else:
                        seam(c, *P(t0 + (u0 - t0) * f, u0), *P(t1 + (u1 - t1) * f, u1), ppc / 2, v=35, w=0.012)
        c.poly([P(d0, d0), P(d1, d0), P(d1, d1), P(d0, d1)], g(150))
        for t in (1.0, 2.0):
            seam(c, *P(d0, t), *P(d1, t), ppc / 2, v=110)
    c.ellipse(*P(1.5, 1.5), 0.62 * ppc, g(110))
    c.ellipse(*P(1.5, 1.5), 0.55 * ppc, g(55))
    sx0, sx1 = 1.12, 1.72
    top, foot = d1 - 0.07, 2.96
    c.poly([P(sx0, top), P(sx1, top), P(sx1, foot), P(sx0, foot)], g(120))
    for k in range(7):
        y0 = top + (foot - top) * k / 7
        y1 = y0 + (foot - top) / 7 * 0.45
        c.poly([P(sx0 + 0.04, y0), P(sx1 - 0.04, y0), P(sx1 - 0.04, y1), P(sx0 + 0.04, y1)], g(155 - k * 8))
    c.poly([P(sx0 - 0.05, top), P(sx0, top), P(sx0, foot), P(sx0 - 0.05, foot)], g(185))
    c.poly([P(sx1, top), P(sx1 + 0.05, top), P(sx1 + 0.05, foot), P(sx1, foot)], g(70))
    for x, y in ((0.75, 0.75), (2.1, 0.75), (0.75, 2.1)):
        c.poly([P(x - 0.13, y - 0.09), P(x + 0.13, y - 0.09), P(x + 0.13, y + 0.09), P(x - 0.13, y + 0.09)], g(128))
        seam(c, *P(x, y - 0.09), *P(x, y + 0.09), ppc / 2, v=95)


# ---- high-tech: seamless composite shells, smooth curves, low profile, emitter glow ---------

def ht_autogun(c, cx, cy, ppc):
    """Low smooth lozenge pod: the tubes leave a forward slot; a luminous spine strip and a front lens."""
    c.ellipse(cx, cy, 0.24 * ppc, g(85))
    for sx in (-0.065, 0.065):  # slim tubes, same tips
        c.rect(cx + (sx - 0.018) * ppc, cy - 0.60 * ppc, cx + (sx + 0.018) * ppc, cy - 0.12 * ppc, g(72))
        c.rect(cx + (sx - 0.026) * ppc, cy - 0.46 * ppc, cx + (sx + 0.026) * ppc, cy - 0.43 * ppc, g(150))
        c.rect(cx + (sx - 0.026) * ppc, cy - 0.32 * ppc, cx + (sx + 0.026) * ppc, cy - 0.29 * ppc, g(150))
    c.ellipse(cx, cy + 0.02 * ppc, 0.24 * ppc, g(150), ry=0.25 * ppc)
    c.poly([(cx - 0.14 * ppc, cy - 0.17 * ppc), (cx + 0.14 * ppc, cy - 0.17 * ppc), (cx + 0.07 * ppc, cy - 0.26 * ppc),
            (cx - 0.07 * ppc, cy - 0.26 * ppc)], g(150))  # forward taper
    c.ellipse(cx - 0.04 * ppc, cy - 0.02 * ppc, 0.17 * ppc, g(188), ry=0.18 * ppc)  # lit crown
    c.rect(cx - 0.012 * ppc, cy - 0.10 * ppc, cx + 0.012 * ppc, cy + 0.20 * ppc, g(240))  # luminous spine
    c.ellipse(cx, cy - 0.19 * ppc, 0.035 * ppc, g(30))  # lens
    c.ellipse(cx, cy - 0.19 * ppc, 0.015 * ppc, g(235))
    c.ellipse(cx + 0.18 * ppc, cy + 0.10 * ppc, 0.05 * ppc, g(118), ry=0.09 * ppc)  # flush feed blister


def ht_cannon(c, cx, cy, ppc):
    """Teardrop shell tapering into a long slim barrel with ring collars and no brake; two swept rear fins."""
    c.ellipse(cx, cy, 0.27 * ppc, g(85))
    barrel_single(c, cx, cy, ppc, half=0.03, brake=0.0)
    c.rect(cx - 0.045 * ppc, cy - 0.61 * ppc, cx + 0.045 * ppc, cy - 0.56 * ppc, g(205))  # emitter tip ring
    for y in (0.48, 0.38):
        c.rect(cx - 0.045 * ppc, cy - y * ppc, cx + 0.045 * ppc, cy - (y - 0.03) * ppc, g(150))
    for side in (-1, 1):  # swept fins
        c.poly([(cx + side * 0.16 * ppc, cy + 0.14 * ppc), (cx + side * 0.30 * ppc, cy + 0.30 * ppc),
                (cx + side * 0.25 * ppc, cy + 0.31 * ppc), (cx + side * 0.10 * ppc, cy + 0.24 * ppc)],
               g(170 if side < 0 else 110))
    c.ellipse(cx, cy + 0.05 * ppc, 0.24 * ppc, g(148), ry=0.25 * ppc)
    c.poly([(cx - 0.18 * ppc, cy - 0.12 * ppc), (cx + 0.18 * ppc, cy - 0.12 * ppc), (cx + 0.06 * ppc, cy - 0.30 * ppc),
            (cx - 0.06 * ppc, cy - 0.30 * ppc)], g(148))
    c.ellipse(cx - 0.05 * ppc, cy + 0.0 * ppc, 0.16 * ppc, g(190), ry=0.18 * ppc)
    c.d.arc([(cx - 0.20 * ppc) * SS, (cy - 0.15 * ppc) * SS, (cx + 0.20 * ppc) * SS, (cy + 0.25 * ppc) * SS],
            200, 340, fill=g(240), width=int(0.018 * ppc * SS))  # luminous seam
    c.ellipse(cx + 0.09 * ppc, cy + 0.13 * ppc, 0.045 * ppc, g(40))
    c.ellipse(cx + 0.09 * ppc, cy + 0.13 * ppc, 0.02 * ppc, g(230))


def ht_heavy(c, cx, cy, ppc):
    """Large smooth oval house, long barrel wound with field coils, a luminous ring and a flush sensor lens."""
    c.ellipse(cx, cy, 0.56 * ppc, g(85))
    c.rect(cx - 0.065 * ppc, cy - 1.42 * ppc, cx + 0.065 * ppc, cy - 0.40 * ppc, g(80))
    c.rect(cx - 0.10 * ppc, cy - 1.44 * ppc, cx + 0.10 * ppc, cy - 1.34 * ppc, g(205))  # emitter tip
    for k in range(5):
        y = 1.18 - 0.12 * k
        c.rect(cx - 0.10 * ppc, cy - y * ppc, cx + 0.10 * ppc, cy - (y - 0.06) * ppc, g(150))  # coils
    for side in (-1, 1):
        c.poly([(cx + side * 0.36 * ppc, cy + 0.30 * ppc), (cx + side * 0.58 * ppc, cy + 0.66 * ppc),
                (cx + side * 0.48 * ppc, cy + 0.72 * ppc), (cx + side * 0.22 * ppc, cy + 0.52 * ppc)],
               g(170 if side < 0 else 110))
    c.ellipse(cx, cy + 0.05 * ppc, 0.52 * ppc, g(146), ry=0.62 * ppc)
    c.poly([(cx - 0.32 * ppc, cy - 0.40 * ppc), (cx + 0.32 * ppc, cy - 0.40 * ppc), (cx + 0.12 * ppc, cy - 0.66 * ppc),
            (cx - 0.12 * ppc, cy - 0.66 * ppc)], g(146))
    c.ellipse(cx - 0.10 * ppc, cy - 0.04 * ppc, 0.38 * ppc, g(190), ry=0.46 * ppc)
    c.d.ellipse([(cx - 0.30 * ppc) * SS, (cy - 0.25 * ppc) * SS, (cx + 0.30 * ppc) * SS, (cy + 0.35 * ppc) * SS],
                outline=g(240), width=int(0.025 * ppc * SS))
    c.ellipse(cx, cy + 0.05 * ppc, 0.12 * ppc, g(130))
    c.ellipse(cx + 0.25 * ppc, cy - 0.30 * ppc, 0.06 * ppc, g(35))
    c.ellipse(cx + 0.25 * ppc, cy - 0.30 * ppc, 0.025 * ppc, g(235))


def ht_collar(c, cx, cy, ppc, variant):
    if variant == "prepared":
        # Flush seamless disc with a smooth bevel and a luminous emitter ring round the well, three glowing nodes.
        c.ellipse(cx, cy, 0.47 * ppc, g(118))
        c.ellipse(cx - 0.015 * ppc, cy - 0.015 * ppc, 0.45 * ppc, g(182))
        c.ellipse(cx, cy, 0.42 * ppc, g(160))
        c.ellipse(cx, cy, 0.355 * ppc, g(242))
        c.ellipse(cx, cy, 0.335 * ppc, g(100))
        c.ellipse(cx, cy, 0.30 * ppc, g(45))
        for i in range(3):
            a = math.radians(-90 + 120 * i + 60)
            c.ellipse(cx + 0.39 * ppc * math.cos(a), cy + 0.39 * ppc * math.sin(a), 0.025 * ppc, g(245))
    else:
        # Drop-deployed hexagonal pad with three folding outrigger feet and a glowing beacon.
        r = 0.36 * ppc
        for i in range(3):
            a = math.radians(-90 + 120 * i)
            ex, ey = cx + 0.47 * ppc * math.cos(a), cy + 0.47 * ppc * math.sin(a)
            px, py = -math.sin(a) * 0.04 * ppc, math.cos(a) * 0.04 * ppc
            c.poly([(cx + px, cy + py), (ex + px, ey + py), (ex - px, ey - py), (cx - px, cy - py)], g(110))
            c.ellipse(ex, ey, 0.06 * ppc, g(150))
        hexa = [(cx + r * math.cos(math.radians(60 * i)), cy + r * math.sin(math.radians(60 * i))) for i in range(6)]
        c.poly(hexa, g(130))
        c.poly([(x - 0.015 * ppc, y - 0.015 * ppc) for x, y in
                [(cx + r * 0.93 * math.cos(math.radians(60 * i)), cy + r * 0.93 * math.sin(math.radians(60 * i)))
                 for i in range(6)]], g(175))
        c.ellipse(cx, cy, 0.31 * ppc, g(100))
        c.ellipse(cx, cy, 0.28 * ppc, g(45))
        c.ellipse(cx + 0.30 * ppc, cy - 0.17 * ppc, 0.03 * ppc, g(245))


def ht_plinth(c, ox, oy, ppc, variant):
    o0, o1 = PLINTH_OUT
    d0, d1 = PLINTH_DECK

    def P(x, y):
        return ox + x * ppc, oy + y * ppc

    if variant == "prepared":
        # Smooth tapering pedestal: battered walls with rounded corners, a rounded deck rimmed by a luminous strip,
        # and a smooth ramp in place of steps.
        c.poly([P(o0, o0), P(o1, o0), P(d1, d0), P(d0, d0)], g(178))
        c.poly([P(o0, o0), P(d0, d0), P(d0, d1), P(o0, o1)], g(206))
        c.poly([P(o1, o0), P(o1, o1), P(d1, d1), P(d1, d0)], g(108))
        c.poly([P(o0, o1), P(d0, d1), P(d1, d1), P(o1, o1)], g(84))
        x0, y0 = P(o0, o0)
        x1, y1 = P(o1, o1)
        clip_round(c, x0, y0, x1, y1, 0.55 * ppc)
        dx0, dy0 = P(d0, d0)
        dx1, dy1 = P(d1, d1)
        c.rect(dx0, dy0, dx1, dy1, g(240), radius=0.30 * ppc)  # luminous rim
        c.rect(dx0 + 0.04 * ppc, dy0 + 0.04 * ppc, dx1 - 0.04 * ppc, dy1 - 0.04 * ppc, g(158), radius=0.27 * ppc)
        c.rect(dx0 + 0.10 * ppc, dy0 + 0.10 * ppc, dx1 - 0.10 * ppc, dy1 - 0.10 * ppc, g(148), radius=0.22 * ppc)
        sx0, sx1 = 1.15, 1.85
        top, foot = d1 - 0.05, 2.90
        c.poly([P(sx0, top), P(sx1, top), P(sx1, foot), P(sx0, foot)], g(135))
        for k in range(10):  # smooth ramp: a soft gradient, no step edges
            y0r = top + (foot - top) * k / 10
            y1r = top + (foot - top) * (k + 1) / 10
            c.poly([P(sx0 + 0.03, y0r), P(sx1 - 0.03, y0r), P(sx1 - 0.03, y1r), P(sx0 + 0.03, y1r)], g(160 - k * 5))
        c.poly([P(sx0 - 0.04, top), P(sx0, top), P(sx0, foot), P(sx0 - 0.04, foot)], g(240))  # ramp light strips
        c.poly([P(sx1, top), P(sx1 + 0.04, top), P(sx1 + 0.04, foot), P(sx1, foot)], g(240))
        for x, y in ((0.80, 0.80), (2.2, 0.80), (0.80, 2.2)):
            c.ellipse(*P(x, y), 0.10 * ppc, g(125))
            c.ellipse(*P(x, y), 0.04 * ppc, g(240))
    else:
        # Deployable platform on four splayed struts: a rounded deck with a thin edge band, strut feet on pads,
        # curved barrier shells round the foot on three sides and a folding ladder down the south.
        for side in ("n", "w", "e"):
            if side == "n":
                box, start, end, lit = (o0 + 0.1, o0 - 0.05, o1 - 0.1, o0 + 0.75), 200, 340, 175
            elif side == "w":
                box, start, end, lit = (o0 - 0.05, o0 + 0.1, o0 + 0.75, o1 - 0.1), 110, 250, 200
            else:
                box, start, end, lit = (o1 - 0.75, o0 + 0.1, o1 + 0.05, o1 - 0.1), 290, 430, 105
            bx0, by0 = P(box[0], box[1])
            bx1, by1 = P(box[2], box[3])
            c.d.arc([bx0 * SS, by0 * SS, bx1 * SS, by1 * SS], start, end, fill=g(lit), width=int(0.12 * ppc * SS))
        for fx, fy in ((o0 + 0.15, o0 + 0.15), (o1 - 0.15, o0 + 0.15), (o0 + 0.15, o1 - 0.15), (o1 - 0.15, o1 - 0.15)):
            kx = d0 + 0.2 if fx < 1.5 else d1 - 0.2
            ky = d0 + 0.2 if fy < 1.5 else d1 - 0.2
            lit = 185 if (fx < 1.5 or fy < 1.5) and not (fx > 1.5 and fy > 1.5) else 95
            ax, ay = P(fx, fy)
            bx, by = P(kx, ky)
            c.d.line([(ax * SS, ay * SS), (bx * SS, by * SS)], fill=g(lit), width=int(0.13 * ppc * SS))
            c.ellipse(ax, ay, 0.12 * ppc, g(130))
            c.ellipse(ax, ay, 0.05 * ppc, g(240))
        dx0, dy0 = P(d0 - 0.10, d0 - 0.10)
        dx1, dy1 = P(d1 + 0.10, d1 + 0.10)
        # The deck's thickness as a deep skirt below its south and east edges, and the dark of the space under it,
        # so the platform reads as standing a storey up rather than lying on the ground (phase B height cue).
        c.rect(dx0 + 0.22 * ppc, dy0 + 0.22 * ppc, dx1 + 0.10 * ppc, dy1 + 0.10 * ppc, g(40), radius=0.32 * ppc)
        c.rect(dx0, dy0, dx1 + 0.06 * ppc, dy1 + 0.06 * ppc, g(78), radius=0.32 * ppc)
        c.rect(dx0, dy0, dx1 - 0.08 * ppc, dy1 - 0.08 * ppc, g(176), radius=0.30 * ppc)
        c.rect(dx0 + 0.07 * ppc, dy0 + 0.07 * ppc, dx1 - 0.14 * ppc, dy1 - 0.14 * ppc, g(150), radius=0.26 * ppc)
        sx0, sx1 = 1.22, 1.68
        top, foot = d1 + 0.02, 2.96
        c.poly([P(sx0, top), P(sx1, top), P(sx1, foot), P(sx0, foot)], g(100))
        for k in range(8):
            y = top + (foot - top) * (k + 0.5) / 8
            c.poly([P(sx0, y), P(sx1, y), P(sx1, y + 0.04), P(sx0, y + 0.04)], g(175))
        for x, y in ((0.85, 0.85), (2.1, 0.85), (0.85, 2.1)):
            c.rect(*P(x - 0.12, y - 0.09), *P(x + 0.12, y + 0.09), g(128), radius=0.04 * ppc)
            c.rect(*P(x - 0.06, y - 0.015), *P(x + 0.06, y + 0.015), g(240))
    c.ellipse(*P(1.5, 1.5), 0.62 * ppc, g(110))
    c.ellipse(*P(1.5, 1.5), 0.55 * ppc, g(55))


# ---- second point sheet (phase B): the round collar, grenade-launcher and point-defence heads ---------------

def ml_round_collar(c, cx, cy, ppc, variant):
    if variant == "prepared":
        # Round precast ring of eight panels, a recessed lamp arc on the north-west, four bolted lifting plates.
        c.ellipse(cx, cy, 0.47 * ppc, g(118))
        c.ellipse(cx - 0.015 * ppc, cy - 0.015 * ppc, 0.45 * ppc, g(176))
        c.ellipse(cx, cy, 0.41 * ppc, g(148))
        for i in range(8):
            a = math.radians(22.5 + 45 * i)
            seam(c, cx + 0.33 * ppc * math.cos(a), cy + 0.33 * ppc * math.sin(a),
                 cx + 0.45 * ppc * math.cos(a), cy + 0.45 * ppc * math.sin(a), ppc)
        c.d.arc([(cx - 0.43 * ppc) * SS, (cy - 0.43 * ppc) * SS, (cx + 0.43 * ppc) * SS, (cy + 0.43 * ppc) * SS],
                200, 250, fill=g(238), width=int(0.025 * ppc * SS))
        for i in range(4):
            a = math.radians(90 * i)
            x, y = cx + 0.375 * ppc * math.cos(a), cy + 0.375 * ppc * math.sin(a)
            c.rect(x - 0.035 * ppc, y - 0.035 * ppc, x + 0.035 * ppc, y + 0.035 * ppc, g(105))
        c.ellipse(cx, cy, 0.33 * ppc, g(98))
        c.ellipse(cx, cy, 0.30 * ppc, g(45))
    else:
        # A ring of ten barrier bins set round a dug well, each turned to the ring.
        c.ellipse(cx, cy, 0.36 * ppc, g(70))
        for i in range(10):
            a = 2 * math.pi * i / 10
            bx, by = cx + 0.375 * ppc * math.cos(a), cy + 0.375 * ppc * math.sin(a)
            lit = 182 if math.cos(a - math.radians(225)) > 0 else 120
            ca, sa = math.cos(a), math.sin(a)
            for r, v in ((0.10 * ppc, lit - 35), (0.08 * ppc, lit)):
                c.poly([(bx + x * ca - y * sa, by + x * sa + y * ca) for x, y in ((-r, -r), (r, -r), (r, r), (-r, r))],
                       g(v))
        c.ellipse(cx, cy, 0.29 * ppc, g(55))
        c.ellipse(cx, cy, 0.23 * ppc, g(40))


def ml_grenade(c, cx, cy, ppc):
    """Panelled box housing with two short wide tubes in a flat cradle, a square side magazine and a roof hatch."""
    c.ellipse(cx, cy, 0.25 * ppc, g(85))
    for sx in (-0.08, 0.08):
        c.rect(cx + (sx - 0.045) * ppc, cy - 0.44 * ppc, cx + (sx + 0.045) * ppc, cy - 0.12 * ppc, g(95))
        c.rect(cx + (sx - 0.058) * ppc, cy - 0.47 * ppc, cx + (sx + 0.058) * ppc, cy - 0.40 * ppc, g(70))
        c.ellipse(cx + sx * ppc, cy - 0.435 * ppc, 0.03 * ppc, g(30))
    c.rect(cx - 0.15 * ppc, cy - 0.27 * ppc, cx + 0.15 * ppc, cy - 0.20 * ppc, g(125))  # cradle band
    c.rect(cx - 0.22 * ppc, cy - 0.19 * ppc, cx + 0.20 * ppc, cy + 0.24 * ppc, g(150), radius=0.04 * ppc)
    c.rect(cx - 0.22 * ppc, cy - 0.19 * ppc, cx + 0.20 * ppc, cy - 0.11 * ppc, g(192), radius=0.03 * ppc)
    c.rect(cx - 0.22 * ppc, cy - 0.11 * ppc, cx - 0.17 * ppc, cy + 0.20 * ppc, g(172))
    seam(c, cx - 0.17 * ppc, cy + 0.06 * ppc, cx + 0.20 * ppc, cy + 0.06 * ppc, ppc)
    c.rect(cx - 0.12 * ppc, cy + 0.10 * ppc, cx + 0.02 * ppc, cy + 0.20 * ppc, g(128), radius=0.02 * ppc)
    c.rect(cx + 0.20 * ppc, cy - 0.06 * ppc, cx + 0.34 * ppc, cy + 0.16 * ppc, g(120), radius=0.02 * ppc)  # magazine
    seam(c, cx + 0.20 * ppc, cy + 0.05 * ppc, cx + 0.34 * ppc, cy + 0.05 * ppc, ppc)


def ml_pd(c, cx, cy, ppc):
    """Small square housing with a sensor box on its flank and a cluster of three thin tubes in a clamp."""
    c.ellipse(cx, cy, 0.21 * ppc, g(85))
    for sx in (-0.035, 0.0, 0.035):
        c.rect(cx + (sx - 0.012) * ppc, cy - 0.40 * ppc, cx + (sx + 0.012) * ppc, cy - 0.12 * ppc, g(75))
    c.rect(cx - 0.06 * ppc, cy - 0.30 * ppc, cx + 0.06 * ppc, cy - 0.24 * ppc, g(110))
    c.rect(cx - 0.18 * ppc, cy - 0.16 * ppc, cx + 0.16 * ppc, cy + 0.20 * ppc, g(150), radius=0.04 * ppc)
    c.rect(cx - 0.18 * ppc, cy - 0.16 * ppc, cx + 0.16 * ppc, cy - 0.09 * ppc, g(190), radius=0.03 * ppc)
    seam(c, cx - 0.01 * ppc, cy - 0.09 * ppc, cx - 0.01 * ppc, cy + 0.20 * ppc, ppc)
    c.rect(cx + 0.06 * ppc, cy + 0.0 * ppc, cx + 0.20 * ppc, cy + 0.12 * ppc, g(60), radius=0.02 * ppc)  # sensor box
    c.ellipse(cx + 0.13 * ppc, cy + 0.06 * ppc, 0.03 * ppc, g(215))


def ht_round_collar(c, cx, cy, ppc, variant):
    if variant == "prepared":
        # A seamless disc cut into three arcs by glowing gaps, with a thin emitter ring at the well.
        c.ellipse(cx, cy, 0.47 * ppc, g(120))
        c.ellipse(cx - 0.015 * ppc, cy - 0.015 * ppc, 0.45 * ppc, g(184))
        c.ellipse(cx, cy, 0.41 * ppc, g(162))
        for i in range(3):
            a = math.radians(-90 + 120 * i)
            c.d.line([((cx + 0.32 * ppc * math.cos(a)) * SS, (cy + 0.32 * ppc * math.sin(a)) * SS),
                      ((cx + 0.46 * ppc * math.cos(a)) * SS, (cy + 0.46 * ppc * math.sin(a)) * SS)],
                     fill=g(242), width=int(0.03 * ppc * SS))
        c.ellipse(cx, cy, 0.335 * ppc, g(236))
        c.ellipse(cx, cy, 0.315 * ppc, g(100))
        c.ellipse(cx, cy, 0.29 * ppc, g(45))
    else:
        # A square drop pad on four splayed outrigger feet, a beacon on one corner.
        for i in range(4):
            a = math.radians(45 + 90 * i)
            ex, ey = cx + 0.46 * ppc * math.cos(a), cy + 0.46 * ppc * math.sin(a)
            px, py = -math.sin(a) * 0.035 * ppc, math.cos(a) * 0.035 * ppc
            c.poly([(cx + px, cy + py), (ex + px, ey + py), (ex - px, ey - py), (cx - px, cy - py)], g(110))
            c.ellipse(ex, ey, 0.05 * ppc, g(150))
        c.rect(cx - 0.34 * ppc, cy - 0.34 * ppc, cx + 0.34 * ppc, cy + 0.34 * ppc, g(128), radius=0.09 * ppc)
        c.rect(cx - 0.34 * ppc, cy - 0.34 * ppc, cx + 0.31 * ppc, cy + 0.31 * ppc, g(175), radius=0.09 * ppc)
        c.rect(cx - 0.31 * ppc, cy - 0.31 * ppc, cx + 0.31 * ppc, cy + 0.31 * ppc, g(150), radius=0.08 * ppc)
        c.ellipse(cx, cy, 0.29 * ppc, g(100))
        c.ellipse(cx, cy, 0.26 * ppc, g(45))
        c.ellipse(cx + 0.27 * ppc, cy - 0.27 * ppc, 0.03 * ppc, g(245))


def ht_grenade(c, cx, cy, ppc):
    """Smooth capsule housing with two short wide tubes in a faired nose, a glowing strip and a flush side pod."""
    c.ellipse(cx, cy, 0.25 * ppc, g(85))
    for sx in (-0.08, 0.08):
        c.rect(cx + (sx - 0.042) * ppc, cy - 0.44 * ppc, cx + (sx + 0.042) * ppc, cy - 0.14 * ppc, g(95))
        c.rect(cx + (sx - 0.052) * ppc, cy - 0.47 * ppc, cx + (sx + 0.052) * ppc, cy - 0.43 * ppc, g(205))
        c.ellipse(cx + sx * ppc, cy - 0.445 * ppc, 0.028 * ppc, g(30))
    c.poly([(cx - 0.17 * ppc, cy - 0.14 * ppc), (cx + 0.17 * ppc, cy - 0.14 * ppc), (cx + 0.10 * ppc, cy - 0.28 * ppc),
            (cx - 0.10 * ppc, cy - 0.28 * ppc)], g(148))
    c.rect(cx - 0.21 * ppc, cy - 0.18 * ppc, cx + 0.21 * ppc, cy + 0.25 * ppc, g(150), radius=0.18 * ppc)
    c.rect(cx - 0.19 * ppc, cy - 0.17 * ppc, cx + 0.12 * ppc, cy + 0.18 * ppc, g(190), radius=0.15 * ppc)
    c.rect(cx - 0.012 * ppc, cy - 0.08 * ppc, cx + 0.012 * ppc, cy + 0.18 * ppc, g(240))
    c.ellipse(cx + 0.25 * ppc, cy + 0.04 * ppc, 0.07 * ppc, g(125), ry=0.12 * ppc)
    c.ellipse(cx + 0.25 * ppc, cy + 0.04 * ppc, 0.02 * ppc, g(235))


def ht_pd(c, cx, cy, ppc):
    """A small smooth dome with a glowing ring, a dark sensor lens and three thin tubes in a faired clamp."""
    c.ellipse(cx, cy, 0.21 * ppc, g(85))
    for sx in (-0.035, 0.0, 0.035):
        c.rect(cx + (sx - 0.011) * ppc, cy - 0.40 * ppc, cx + (sx + 0.011) * ppc, cy - 0.12 * ppc, g(75))
    c.rect(cx - 0.055 * ppc, cy - 0.30 * ppc, cx + 0.055 * ppc, cy - 0.25 * ppc, g(200))
    c.ellipse(cx, cy + 0.02 * ppc, 0.19 * ppc, g(150))
    c.ellipse(cx - 0.04 * ppc, cy - 0.02 * ppc, 0.13 * ppc, g(188))
    c.d.ellipse([(cx - 0.15 * ppc) * SS, (cy - 0.13 * ppc) * SS, (cx + 0.15 * ppc) * SS, (cy + 0.17 * ppc) * SS],
                outline=g(240), width=int(0.018 * ppc * SS))
    c.ellipse(cx + 0.09 * ppc, cy + 0.06 * ppc, 0.05 * ppc, g(35))
    c.ellipse(cx + 0.09 * ppc, cy + 0.06 * ppc, 0.02 * ppc, g(235))


POINTS2 = {
    "midline": (ml_round_collar, ml_grenade, ml_pd),
    "hightech": (ht_round_collar, ht_grenade, ht_pd),
}


GEOMETRY = {
    "midline": (ml_collar, ml_autogun, ml_cannon, ml_plinth, ml_heavy),
    "hightech": (ht_collar, ht_autogun, ht_cannon, ht_plinth, ht_heavy),
}


def points_sheet(family, variant):
    collar, autogun, cannon, _, _ = GEOMETRY[family]
    c = Canvas()
    ppc = 400
    cxs = [18 + 500 * i + 250 for i in range(3)]
    collar(c, cxs[0], 512, ppc, variant)
    autogun(c, cxs[1], 512, ppc)
    cannon(c, cxs[2], 512, ppc)
    return c


def hardpoint_sheet(family, variant):
    _, _, _, plinth, heavy = GEOMETRY[family]
    c = Canvas()
    plinth(c, 0, 128, 256, variant)
    heavy(c, 768 + 384, 128 + 384, 256)
    return c


def points2_sheet(family, variant):
    collar, grenade, pd = POINTS2[family]
    c = Canvas()
    cxs = [18 + 500 * i + 250 for i in range(3)]
    collar(c, cxs[0], 512, 400, variant)
    grenade(c, cxs[1], 512, 400)
    pd(c, cxs[2], 512, 400)
    return c


def collar2_sheet(family, variant):
    c = Canvas()
    POINTS2[family][0](c, 18 + 250, 512, 400, variant)
    return c


def guide_path(family, variant, sheet):
    return GUIDES / f"{family}-{variant}-{sheet}-guide.png"


# The directions taken on to full sets (phase B). Their militia hardpoint is the stepped 'hardpoint-v2' geometry
# (the phase-A militia plinths read flat at play zoom), and they carry the second point sheet.
CHOSEN = ("league-standard", "diktat-export", "field-emitter")
# Options whose shells carry the Paragon's hex ablative armour (owner, phase C): their sheets are generated again with
# the hex reference, as '<sheet>-hex' on the same guides.
HEX = ("field-emitter",)
HEX_REF = GUIDES / "paragon-hex-reference.png"
PARAGON_REFERENCE = ROOT / "art-source" / "company-prologue" / "paragon-surface" / "guide" / "reference-guide.png"
# A strip of the Paragon's clean review render that holds hex plating and nothing else, enlarged threefold.
HEX_CROP = (640, 668, 1040, 760)


def guide_sheet(sheet):
    """The guide a sheet is drawn over: a '-hex' sheet reuses its plain sheet's guide."""
    return sheet[:-4] if sheet.endswith("-hex") else sheet


# The image tool refused the League militia's second point sheet three times running (violence filter), so that
# variant generates its round collar alone ('collar2') and its grenade and point-defence heads are the prepared
# variant's, dressed in the militia heads' paint by export_ship (field_dress.colour_transfer), as earthwork's were.
COLLAR_ONLY = {("league-standard", "militia")}


def sheets(option, variant):
    if option not in CHOSEN:
        return ("points", "hardpoint")
    second = "collar2" if (option, variant) in COLLAR_ONLY else "points2"
    names = ("points", "hardpoint-v2" if variant == "militia" else "hardpoint", second)
    return tuple(n + "-hex" for n in names) if option in HEX else names


def hardpoint_sheet_name(option, variant):
    return [s for s in sheets(option, variant) if s.startswith("hardpoint")][0]


# ---- options and briefs ----------------------------------------------------------------------

# option id -> (family, prepared material brief, militia material brief). The three options of a family share its
# geometry; what changes is material, livery and dressing.
OPTIONS = {
    "league-standard": ("midline",
        "Persean League coalition standard: factory-made pressed modular composite-steel panels with sealed caulked "
        "seams, flush standard bolt fittings and access hatches, painted in a clean slate blue-grey standard scheme "
        "with pale grey trim and small abstract white-and-yellow unit chevrons and stencil bars (no letters or numbers), "
        "recessed white strip lamps where the guide shows the brightest thin bars; housings in the same painted slate "
        "blue-grey with grey hatches and darker charcoal pipes; scuffed paint at edges, clean repairs, reads serviced "
        "not old",
        "Persean League civil-engineering kit assembled on site: the same slate blue-grey standard-pattern machinery housings, scuffed and "
        "patched, sitting on collapsible wire-mesh bins lined with grey geotextile and filled with earth, "
        "ratchet straps, stacked grey storage cases, a sun-faded blue tarp lashed over part of the deck; "
        "bins and mesh where the guide shows square cells; painted markings faded"),
    "diktat-export": ("midline",
        "Sindrian Diktat export grade: pressed ceramic-steel modular panels with rounded chamfers and sealed seams, "
        "painted warm sand-ochre with bold rust-red and dark brown stripe bands as unit markings (no letters), polished "
        "steel standard fittings and grilles, recessed warm-white strip lamps where the guide shows the brightest thin "
        "bars; housings painted sand-ochre with a red unit band across the roof and dark steel pipes; clean, "
        "petro-wealthy, well kept",
        "Sindrian field-built works: the same sand-ochre export housings, dusty and dented, set in interlocking "
        "moulded plastic-composite barrier blocks in faded orange-red and off-white where the guide shows square "
        "cells, filled with sand, with oil drums and corrugated sheet patches; red unit bands sun-faded"),
    "commercial-grade": ("midline",
        "Independent licensed commercial grade: standard pressed steel panels in dark charcoal-grey steel with safety-green "
        "painted panels and white unit codes as plain bars (no letters), hex-bolted standard flanges, steel deck "
        "grating, recessed cool-white strip lamps where the guide shows the brightest thin bars; housings in "
        "charcoal-grey with safety-green hatches and dark steel pipes; scuffed industrial, serviced, oil-stained",
        "Frontier colony industrial equipment: weathered charcoal-grey machinery housings with pale lime-green service "
        "hatches and hand-painted touch-ups, set among ribbed steel shipping-crate sections and corrugated sheet "
        "panels on scaffold tube where the guide shows square cells, like a mining-camp yard"),
    "tritach-white": ("hightech",
        "Tri-Tachyon corporate high-tech: seamless white ceramic composite shells with smooth curves and no visible "
        "bolts, graphite-black underside bands and panel gaps, thin glowing cyan emitter seams and lenses where the "
        "guide shows the brightest lines and dots; pristine, precise, smooth satin finish; housings glossy white with "
        "graphite pipes and cyan light accents",
        "Tri-Tachyon rapid-deployment kit: the same smooth white composite housings with graphite pipes and cyan "
        "lights, slightly scorched, on drop-deployed graphite-black folding pads, struts and cast-foam barrier "
        "shells in pale grey, cyan beacon lights blinking, everything snap-locked and seamless, nothing improvised "
        "by hand"),
    "graphite-stealth": ("hightech",
        "Graphite low-observable high-tech: matte dark graphite composite with smooth curved planes and tight "
        "seamless joints, a single thin white corporate edge stripe, tiny cyan sensor lenses and faint cyan emitter "
        "lines only where the guide shows the brightest lines and dots; housings in matte graphite with darker pipes; "
        "quiet, sleek, near-black but readable with soft highlights on lit faces",
        "Graphite field deployment: the same matte graphite housings with white edge stripes and cyan pinpricks, on "
        "folding graphite struts and pads, wrapped in dark grey adaptive camouflage mesh panels and sealed hard "
        "cases, a few cyan status lights"),
    "field-emitter": ("hightech",
        "Hard-light field high-tech: pale pearl-grey composite with smooth curves, translucent blue-tinted glass "
        "canopies on the housings, and bright pale-blue glowing field-projector rings and emitter nodes where the "
        "guide shows the brightest lines and dots, with a faint luminous haze over them; graphite pipes with "
        "glowing blue coil rings; luminous, clean, otherworldly",
        "Portable field-emitter kit: the same pearl-grey housings with glass canopies, on light folding pylons and "
        "pads that project translucent pale-blue hard-light barrier panes and arcs where the guide shows curved "
        "shells and struts, blue emitter glow, compact power cells on the deck"),
}

LABELS = {
    "league-standard": "League standard: slate-blue pressed panels / mesh barrier cells",
    "diktat-export": "Diktat export: sand-ochre and red bands / plastic barrier blocks",
    "commercial-grade": "Commercial grade: charcoal and safety green / container revetment",
    "tritach-white": "Tri-Tachyon white: white composite, cyan seams / drop pads and foam shells",
    "graphite-stealth": "Graphite stealth: matte graphite, white stripe / struts and camo mesh",
    "field-emitter": "Field emitter: pearl composite, glass, blue fields / emitter pylons",
}

POINTS_OBJECTS = {
    "midline": ("left, a {collar}, sunk flush into the ground, with an empty dark circular socket in the middle "
                "(leave the well interior dark and plain: a separate housing sits over it); middle, a boxy panelled "
                "rotating machinery housing with a flat front plate, a side feed box and two slim parallel pipes "
                "sticking straight up the image; right, a flat-sided panelled rotating machinery housing with a "
                "square front box and one long pipe ending in a slotted cap, sticking straight up the image"),
    "hightech": ("left, a {collar}, sunk flush into the ground, with an empty dark circular socket in the middle "
                 "(leave the well interior dark and plain: a separate housing sits over it); middle, a low smooth "
                 "lozenge-shaped rotating machinery pod with a glowing spine strip, a small lens and two slim parallel "
                 "pipes sticking straight up the image; right, a smooth teardrop-shaped rotating machinery pod with two "
                 "swept rear fins and one long slim pipe with ring collars, sticking straight up the image"),
}
COLLARS = {
    ("midline", "prepared"): "square precast modular collar of joined panels with chamfered corners and two recessed "
                             "strip lamps",
    ("midline", "militia"): "ring of eight square collapsible barrier cells round a dug pit",
    ("hightech", "prepared"): "flush seamless round disc collar with a glowing emitter ring round the well and three "
                              "glowing nodes",
    ("hightech", "militia"): "hexagonal drop-deployed pad with three folding outrigger feet and a beacon light",
}
PLINTHS = {
    ("midline", "prepared"): "a raised modular plinth about one storey (four metres) tall on a low foot terrace, "
                             "seen from straight above, so its panelled battered side walls show as the four bands "
                             "round the flat plated top deck: the north and west walls catch the sun and are light, "
                             "the south and east walls are in shade and dark; it must read clearly as a tall solid "
                             "block standing above the ground. A narrow staircase is cut into the south wall (keep "
                             "the steps as drawn), recessed strip lamps on the parapet, lockers on three deck corners",
    ("midline", "militia"): "a raised bastion about one storey (four metres) tall built of stacked square barrier "
                            "cells, seen from straight above: the cells on the north and west sides catch the sun and "
                            "are light, those on the south and east are in shade and dark; it must read clearly as a "
                            "tall solid block standing above the ground. A narrow staircase runs down the south side "
                            "(keep the steps as drawn), crates on three deck corners",
    ("hightech", "prepared"): "a raised smooth pedestal about one storey (four metres) tall with rounded corners, "
                              "seen from straight above, so its smooth sloped side walls show as the four bands round "
                              "the rounded top deck: north and west faces lit, south and east faces in shade; it must "
                              "read clearly as a tall solid form standing above the ground. A glowing light strip rims "
                              "the deck, a smooth ramp with light strips runs down the south face (keep it as drawn), "
                              "round fittings with glowing centres on three deck corners",
    ("hightech", "militia"): "a raised deployable platform about one storey (four metres) tall: a rounded deck "
                             "with a thick edge band, held up by four splayed struts to round foot pads at the "
                             "corners, curved barrier shells round its foot on the north, west and east, and a "
                             "folding ladder down the south (keep everything as drawn); seen from straight above, "
                             "lit from the upper left; it must read as a platform standing high above the ground",
}
# The stepped militia plinths of phase B (hardpoint-v2), described as built in courses so the height reads.
PLINTHS_V2 = {
    ("midline", "militia"): "a raised platform about one storey (four metres) tall built up in three stepped courses "
                            "of square earth-filled mesh bins, each course set back from the one below: every course shows "
                            "a lit top on the north and west and a dark shaded face on the south and east, darkest at "
                            "the foot; it must read clearly as a tall stepped tower standing above the ground, not a "
                            "flat floor. A plated deck on top, a narrow staircase down the south side (keep the steps "
                            "as drawn), crates on three deck corners",
    ("hightech", "militia"): "a raised deployable platform about one storey (four metres) tall: a rounded deck with a "
                             "deep dark skirt of its own thickness showing below its south and east edges and deep "
                             "shadow underneath, held up by four splayed struts to round foot pads at the corners, "
                             "curved barrier shells round its foot on the north, west and east, and a folding ladder "
                             "down the south (keep everything as drawn); seen from straight above, lit from the "
                             "upper left; it must read as a platform standing high above the ground",
}
HEAVY = {
    "midline": "the heavy rotating machinery house that sits on it: a slab-sided panelled housing with a chamfered front, "
               "a square front box, bolted side bins along both flanks, a hatch, a cupola, a rear grille, and one long "
               "heavy pipe with a slotted end cap pointing straight up the image",
    "hightech": "the heavy rotating machinery house that sits on it: a large smooth oval shell with two swept rear fins, a "
                "glowing ring on its crown, a flush lens, and one long pipe wound with ring coils and ending in a "
                "glowing tip, pointing straight up the image",
}

POINTS2_OBJECTS = {
    "midline": ("left, a {collar}, sunk flush into the ground, with an empty dark circular socket in the middle "
                "(leave the well interior dark and plain: a separate housing sits over it); middle, a panelled box "
                "machinery housing with a square side box and two short wide pipes side by side in a flat cradle, "
                "sticking straight up the image; right, a small square machinery housing with a dark sensor box on "
                "its side and a cluster of three thin pipes sticking straight up the image"),
    "hightech": ("left, a {collar}, sunk flush into the ground, with an empty dark circular socket in the middle "
                 "(leave the well interior dark and plain: a separate housing sits over it); middle, a smooth capsule "
                 "machinery pod with a glowing spine strip, a flush side pod and two short wide pipes with glowing "
                 "rims sticking straight up the image; right, a small smooth dome with a glowing ring, a dark lens "
                 "and a cluster of three thin pipes sticking straight up the image"),
}
ROUND_COLLARS = {
    ("midline", "prepared"): "round precast collar of joined panels with a recessed lamp arc and four bolted plates",
    ("midline", "militia"): "ring of ten square earth-filled mesh bins round a sunken pit",
    ("hightech", "prepared"): "flush seamless round disc collar cut into three arcs by glowing gaps, with a glowing "
                              "ring round the well",
    ("hightech", "militia"): "square drop-deployed pad on four splayed outrigger feet with a beacon light",
}

COMMON_TAIL = ("Straight 90-degree overhead orthographic camera, top-down like a strategy-game map sprite, no "
               "perspective tilt. Sun from the upper left: keep the guide's lit and shaded faces exactly where they "
               "are. Hand-painted gritty science-fiction game-sprite look like Starsector, restrained palette, coarse "
               "readable detail that survives being shrunk to about 30 to 40 pixels per cell. No text, no letters, no "
               "numbers, no labels, no watermark, no UI, no ground or terrain, no grass, no cast shadow outside the "
               "objects, no extra slab, apron or border. Transparent background outside every silhouette (if "
               "transparency is impossible, a flat pure white background). Change only material, surface detail and "
               "subtle shading, never placement, size or silhouette.")


HEX_BRIEF = ("Surface: every composite shell, housing and deck is armoured in a fine regular pattern of small "
             "hexagonal ceramic plates with thin dark seams, as on the hex-armoured warship hull in the hex reference "
             "image: one continuous honeycomb across each surface, plates a few pixels across at sprite size, "
             "slightly crowned, a little paler on lit faces; glass, glowing rings and pipes stay smooth. ")


# Where an option's militia works are not the family's default material, the geometry wording follows its material.
BASE_WORDS = {
    "diktat-export": (("earth-filled mesh bins", "interlocking moulded plastic blocks"),),
}


def brief(option, variant, sheet):
    family, prepared, militia = OPTIONS[option]
    material = prepared if variant == "prepared" else militia
    hex_armour = sheet.endswith("-hex")
    sheet = guide_sheet(sheet)
    head = ("Use case: sketch-to-render, game sprite sheet. Image 1 is the authoritative geometry guide and edit "
            "target: reskin it, keeping every shape exactly where and how big it is on the 1536x1024 canvas. ")
    if sheet == "points":
        body = ("It holds three separate objects for small sci-fi strategy-game building props (like a tabletop "
                "miniature terrain set), each seen from directly above: "
                + POINTS_OBJECTS[family].format(collar=COLLARS[(family, variant)]) + ". ")
    elif sheet == "collar2":
        body = ("It holds one object for a small sci-fi strategy-game building prop (like a tabletop miniature "
                "terrain set), seen from directly above, at the left: a " + ROUND_COLLARS[(family, variant)]
                + ", sunk flush into the ground, with an empty dark circular socket in the middle (leave it dark and "
                "plain). ")
    elif sheet == "points2":
        body = ("It holds three separate objects for small sci-fi strategy-game building props (like a tabletop "
                "miniature terrain set), each seen from directly above: "
                + POINTS2_OBJECTS[family].format(collar=ROUND_COLLARS[(family, variant)]) + ". ")
    else:
        body = ("It holds two separate objects for a sci-fi strategy-game building prop (like a tabletop miniature "
                "terrain set), each seen from directly above. Left: "
                + (PLINTHS_V2 if sheet == "hardpoint-v2" else PLINTHS)[(family, variant)]
                + "; the deck holds an empty dark circular ring well (leave the well dark and plain: a separate "
                "rotating house sits over it). Right: " + HEAVY[family] + ". ")
    for words, instead in BASE_WORDS.get(option, ()):
        body = body.replace(words, instead)
    return head + body + "Material and style: " + material + ". " + (HEX_BRIEF if hex_armour else "") + COMMON_TAIL


def prompt_path(option, variant, sheet):
    return PROMPTS / f"{option}-{variant}-{sheet}.prompt.txt"


def raw_path(option, variant, sheet):
    return RAW / f"{option}-{variant}-{sheet}.raw.png"


# ---- commands ----------------------------------------------------------------------------------

def cmd_guides():
    GUIDES.mkdir(parents=True, exist_ok=True)
    for family in FAMILIES:
        for variant in VARIANTS:
            points_sheet(family, variant).save(guide_path(family, variant, "points"))
            points2_sheet(family, variant).save(guide_path(family, variant, "points2"))
            if any(OPTIONS[o][0] == family and v == variant for o, v in COLLAR_ONLY):
                collar2_sheet(family, variant).save(guide_path(family, variant, "collar2"))
            # The phase-A militia hardpoint guides stay as generated (their raws' provenance hashes them); the
            # stepped militia geometry is written as hardpoint-v2.
            name = "hardpoint" if variant == "prepared" else "hardpoint-v2"
            hardpoint_sheet(family, variant).save(guide_path(family, variant, name))
    strip = Image.open(PARAGON_REFERENCE).convert("RGB").crop(HEX_CROP)
    strip.resize((strip.width * 3, strip.height * 3), Image.LANCZOS).save(HEX_REF)
    print("wrote guides")


def cmd_prompts():
    PROMPTS.mkdir(parents=True, exist_ok=True)
    for option in OPTIONS:
        for variant in VARIANTS:
            for sheet in sheets(option, variant):
                prompt_path(option, variant, sheet).write_text(brief(option, variant, sheet) + "\n", encoding="utf-8")
    print("wrote prompts")


def jobs(options):
    for option in options or OPTIONS:
        family = OPTIONS[option][0]
        for variant in VARIANTS:
            for sheet in sheets(option, variant):
                yield option, family, variant, sheet


def cmd_generate(options):
    """One generation per missing raw, four at a time (each run is found by its own Codex thread)."""
    procs = []
    for option, family, variant, sheet in jobs(options):
        out = raw_path(option, variant, sheet)
        if out.exists():
            continue
        cmd = [sys.executable, str(ROOT / "tools" / "codex_imagegen.py"),
               "--prompt", str(prompt_path(option, variant, sheet)), "--out", str(out),
               "--ref", f"{guide_path(family, variant, guide_sheet(sheet))}=Geometry/layout authority and edit "
                        "target: keep every silhouette, position and size",
               "--ref", f"{STYLE_REF}=Painting style and rendering finish only; not layout, not material colour"]
        if sheet.endswith("-hex"):
            cmd += ["--ref", f"{HEX_REF}=Hex reference: the hexagonal armour-plate surface pattern only; not layout, "
                             "not shapes, not colour"]
        procs.append((out.name, subprocess.Popen(cmd, cwd=ROOT)))
        while sum(1 for _, p in procs if p.poll() is None) >= 4:
            procs[[i for i, (_, p) in enumerate(procs) if p.poll() is None][0]][1].wait()
    for name, p in procs:
        p.wait()
        print(name, "ok" if p.returncode == 0 else f"FAILED ({p.returncode})")


SHIPPED = ROOT / "mod" / "graphics" / "structures" / "ground-turrets" / "brutalist"


def shipped_head(role):
    """The shipped brutalist head with its barrel layer composed back on: the extent every look's head is fitted to."""
    out = Image.open(SHIPPED / f"{role}-barrel.png").convert("RGBA")
    out.alpha_composite(Image.open(SHIPPED / f"{role}-head.png").convert("RGBA"))
    return out


def head_piece(sheet, guide, box, size, shadow_frac, role):
    """A head cut to its guide box, so its turret ring sits on the pivot as the guide draws it. (Phase A also
    stretched it per axis onto the shipped brutalist head's box, which moved every family's ring off the pivot.)"""
    del role
    piece = fitted(sheet, guide, box).resize((size, size), Image.LANCZOS)
    out = contact_shadow(size, shadow_frac)
    out.alpha_composite(piece)
    return out


def cmd_export():
    cx = [18 + 500 * i + 250 for i in range(3)]
    for option, family, variant, sheet in jobs(None):
        if guide_sheet(sheet) != "points":
            continue
        hp_sheet = hardpoint_sheet_name(option, variant)
        pts_raw, hp_raw = raw_path(option, variant, sheet), raw_path(option, variant, hp_sheet)
        if not (pts_raw.exists() and hp_raw.exists()):
            print("skip", option, variant, "(raw missing)")
            continue
        target = ART / f"{option}-{variant}"
        target.mkdir(parents=True, exist_ok=True)
        guide = Image.open(guide_path(family, variant, "points"))
        pts = masked(Image.open(pts_raw), guide)
        fill_pit(fitted(pts, guide, (cx[0] - 200, 312, cx[0] + 200, 712)).resize((PPC, PPC), Image.LANCZOS)) \
            .save(target / "collar.png")
        head_piece(pts, guide, (cx[1] - 250, 262, cx[1] + 250, 762), 160, 0.22, "autogun") \
            .save(target / "autogun.png")
        head_piece(pts, guide, (cx[2] - 250, 262, cx[2] + 250, 762), 160, 0.24, "cannon").save(target / "cannon.png")
        guide = Image.open(guide_path(family, variant, guide_sheet(hp_sheet)))
        hp = masked(Image.open(hp_raw), guide)
        plinth_with_shadow(fitted(hp, guide, (0, 128, 768, 896)), 3 * PPC).save(target / "plinth.png")
        head_piece(hp, guide, (768, 128, 1536, 896), 3 * PPC, 0.2, "heavy").save(target / "heavy.png")
        print("exported", option, variant, "->", target)


def main(argv):
    cmd, rest = (argv[0], argv[1:]) if argv else ("", [])
    if cmd == "guides":
        cmd_guides()
    elif cmd == "prompts":
        cmd_prompts()
    elif cmd == "generate":
        cmd_generate(rest)
    elif cmd == "export":
        cmd_export()
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
