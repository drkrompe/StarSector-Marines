"""Geometry guides for the ground-turret art-direction pass (phase 1).

Two sheets per direction, both 1536x1024, top-down orthographic, sun from the upper left
(the battle's default: azimuth 135, shadows fall down and to the right):

  <dir>-points-guide.png  three 500x500 frames at 400 px a cell (1.25 cells), centred on y=512:
                          0 the 1x1 sunken collar (exported as the middle 400x400, one cell),
                          1 the autogun head, 2 the light-cannon head (pivot = frame centre, muzzle up).
  <dir>-hardpoint-guide.png two 768x768 frames at 256 px a cell (3 cells), top at y=128:
                          0 the 3x3 raised plinth, 1 the heavy-cannon head (pivot = frame centre).

The plinth is the part that must read as height: battered (sloped) walls lit on the north and
west and in shade on the south and east, a stair cut into the south face, and a deck parapet.
Its cast shadow is not in the guide; export.py draws it in code so the generation cannot move it.

Directions: brutalist (poured concrete, olive armour), colony (riveted steel, hazard paint),
earthwork (sandbags, gabions, rammed earth). Heads share geometry across directions; collars
and plinths differ.

Usage: python art-source/ground-turrets/make_guide.py   (writes art-source/ground-turrets/guides/)
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent / "guides"
W, H = 1536, 1024
SS = 4  # supersampling

DIRECTIONS = ("brutalist", "colony", "earthwork")


class Canvas:
    def __init__(self):
        self.im = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def poly(self, pts, fill):
        self.d.polygon([(x * SS, y * SS) for x, y in pts], fill=fill)

    def ellipse(self, cx, cy, r, fill, ry=None):
        ry = r if ry is None else ry
        self.d.ellipse([(cx - r) * SS, (cy - ry) * SS, (cx + r) * SS, (cy + ry) * SS], fill=fill)

    def rect(self, x0, y0, x1, y1, fill, radius=0):
        if radius:
            self.d.rounded_rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], radius * SS, fill=fill)
        else:
            self.d.rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], fill=fill)

    def save(self, path):
        self.im.resize((W, H), Image.LANCZOS).save(path)


def g(v, a=255):
    return (v, v, v, a)


# ---- point emplacements -------------------------------------------------------------------

def collar(c, cx, cy, ppc, direction):
    """A one-cell collar sunk into the ground: a low rim around a dark turret-ring well."""
    r_out = 0.47 * ppc
    r_in = 0.30 * ppc
    if direction == "brutalist":
        # Chamfered octagonal poured collar, its sloped top lit NW and shaded SE.
        def octagon(r):
            return [(cx + r * math.cos(math.radians(22.5 + 45 * i)), cy + r * math.sin(math.radians(22.5 + 45 * i)))
                    for i in range(8)]
        c.poly(octagon(r_out / math.cos(math.radians(22.5)) * 0.98), g(110))
        # Lit north-west half of the bevel: the same octagon nudged up-left, clipped by the shaded one below it.
        c.poly([(x - 0.025 * ppc, y - 0.025 * ppc) for x, y in octagon(r_out / math.cos(math.radians(22.5)) * 0.93)], g(170))
        c.poly(octagon(r_out * 0.86), g(140))
        c.ellipse(cx, cy, r_in + 0.03 * ppc, g(95))
        c.ellipse(cx, cy, r_in, g(45))
        for i in range(4):  # drain notches
            a = math.radians(45 + 90 * i)
            c.ellipse(cx + 0.40 * ppc * math.cos(a), cy + 0.40 * ppc * math.sin(a), 0.035 * ppc, g(60))
    elif direction == "colony":
        # Square steel ring frame with chamfered corners and a hazard band round the well.
        s = 0.46 * ppc
        ch = 0.12 * ppc
        c.poly([(cx - s + ch, cy - s), (cx + s - ch, cy - s), (cx + s, cy - s + ch), (cx + s, cy + s - ch),
                (cx + s - ch, cy + s), (cx - s + ch, cy + s), (cx - s, cy + s - ch), (cx - s, cy - s + ch)], g(130))
        c.rect(cx - s, cy - s + ch, cx - s + 0.05 * ppc, cy + s - ch, g(170))
        c.rect(cx - s + ch, cy - s, cx + s - ch, cy - s + 0.05 * ppc, g(170))
        c.ellipse(cx, cy, 0.41 * ppc, g(200))  # hazard band (painted)
        c.ellipse(cx, cy, 0.35 * ppc, g(110))
        c.ellipse(cx, cy, r_in, g(45))
        for sx in (-1, 1):
            for sy in (-1, 1):
                c.ellipse(cx + sx * 0.38 * ppc, cy + sy * 0.38 * ppc, 0.03 * ppc, g(90))
    else:
        # Ring of sandbags around a dug pit.
        c.ellipse(cx, cy, r_out, g(70))
        n = 11
        for i in range(n):
            a = 2 * math.pi * i / n
            bx = cx + 0.39 * ppc * math.cos(a)
            by = cy + 0.39 * ppc * math.sin(a)
            lit = 175 if math.cos(a - math.radians(225)) > 0 else 135
            c.ellipse(bx, by, 0.11 * ppc, g(lit), ry=0.085 * ppc)
        c.ellipse(cx, cy, r_in, g(55))
        c.ellipse(cx, cy, r_in * 0.8, g(40))


def autogun_head(c, cx, cy, ppc):
    """Squat armoured twin-MG cupola, muzzles up. Ring radius 0.24 cell."""
    c.ellipse(cx, cy, 0.25 * ppc, g(85))  # bearing ring
    # Twin barrels with cooling jackets.
    for sx in (-0.065, 0.065):
        c.rect(cx + (sx - 0.022) * ppc, cy - 0.58 * ppc, cx + (sx + 0.022) * ppc, cy - 0.10 * ppc, g(70))
        c.rect(cx + (sx - 0.035) * ppc, cy - 0.40 * ppc, cx + (sx + 0.035) * ppc, cy - 0.18 * ppc, g(105))
        c.rect(cx + (sx - 0.03) * ppc, cy - 0.60 * ppc, cx + (sx + 0.03) * ppc, cy - 0.55 * ppc, g(60))
    # Faceted armoured body, lit NW.
    body = [(cx - 0.20 * ppc, cy - 0.20 * ppc), (cx + 0.20 * ppc, cy - 0.20 * ppc), (cx + 0.24 * ppc, cy + 0.04 * ppc),
            (cx + 0.18 * ppc, cy + 0.24 * ppc), (cx - 0.18 * ppc, cy + 0.24 * ppc), (cx - 0.24 * ppc, cy + 0.04 * ppc)]
    c.poly(body, g(150))
    c.poly([(cx - 0.20 * ppc, cy - 0.20 * ppc), (cx + 0.20 * ppc, cy - 0.20 * ppc), (cx + 0.12 * ppc, cy - 0.10 * ppc),
            (cx - 0.12 * ppc, cy - 0.10 * ppc)], g(190))  # sloped front glacis
    c.rect(cx - 0.11 * ppc, cy - 0.06 * ppc, cx + 0.11 * ppc, cy + 0.14 * ppc, g(130), radius=0.03 * ppc)  # roof hatch
    c.rect(cx + 0.24 * ppc, cy - 0.06 * ppc, cx + 0.31 * ppc, cy + 0.14 * ppc, g(110))  # ammo box
    c.rect(cx - 0.05 * ppc, cy - 0.17 * ppc, cx + 0.05 * ppc, cy - 0.12 * ppc, g(40))  # sight slit


def cannon_head(c, cx, cy, ppc):
    """Wedge-mantlet light anti-armour cannon, one long barrel with a muzzle brake. Ring radius 0.27 cell."""
    c.ellipse(cx, cy, 0.28 * ppc, g(85))
    c.rect(cx - 0.035 * ppc, cy - 0.60 * ppc, cx + 0.035 * ppc, cy - 0.15 * ppc, g(80))
    c.rect(cx - 0.055 * ppc, cy - 0.61 * ppc, cx + 0.055 * ppc, cy - 0.52 * ppc, g(60))  # muzzle brake
    c.rect(cx - 0.05 * ppc, cy - 0.30 * ppc, cx + 0.05 * ppc, cy - 0.18 * ppc, g(110))  # fume extractor
    body = [(cx - 0.14 * ppc, cy - 0.24 * ppc), (cx + 0.14 * ppc, cy - 0.24 * ppc), (cx + 0.27 * ppc, cy - 0.05 * ppc),
            (cx + 0.26 * ppc, cy + 0.22 * ppc), (cx + 0.16 * ppc, cy + 0.30 * ppc), (cx - 0.16 * ppc, cy + 0.30 * ppc),
            (cx - 0.26 * ppc, cy + 0.22 * ppc), (cx - 0.27 * ppc, cy - 0.05 * ppc)]
    c.poly(body, g(145))
    c.poly([(cx - 0.14 * ppc, cy - 0.24 * ppc), (cx + 0.14 * ppc, cy - 0.24 * ppc), (cx + 0.20 * ppc, cy - 0.12 * ppc),
            (cx - 0.20 * ppc, cy - 0.12 * ppc)], g(185))  # mantlet glacis
    c.poly([(cx - 0.27 * ppc, cy - 0.05 * ppc), (cx - 0.20 * ppc, cy - 0.12 * ppc), (cx - 0.20 * ppc, cy + 0.20 * ppc),
            (cx - 0.26 * ppc, cy + 0.22 * ppc)], g(170))  # lit west cheek
    c.ellipse(cx + 0.08 * ppc, cy + 0.10 * ppc, 0.06 * ppc, g(120))  # commander's sight
    c.rect(cx - 0.12 * ppc, cy + 0.20 * ppc, cx + 0.04 * ppc, cy + 0.28 * ppc, g(115))  # rear stowage


def points_sheet(direction):
    c = Canvas()
    ppc = 400
    for i in range(3):
        cx = 18 + 500 * i + 250
        cy = 512
        if i == 0:
            collar(c, cx, cy, ppc, direction)
        elif i == 1:
            autogun_head(c, cx, cy, ppc)
        else:
            cannon_head(c, cx, cy, ppc)
    return c


# ---- hardpoint -----------------------------------------------------------------------------

PLINTH_OUT = (0.12, 2.72)   # outer foot of the battered walls, in cells
PLINTH_DECK = (0.52, 2.32)  # deck edge


def plinth(c, ox, oy, ppc, direction):
    o0, o1 = PLINTH_OUT
    d0, d1 = PLINTH_DECK

    def P(x, y):
        return ox + x * ppc, oy + y * ppc

    lit, side_lit, side_dark, dark, deck = 205, 175, 105, 80, 150
    if direction == "earthwork":
        lit, side_lit, side_dark, dark, deck = 185, 160, 110, 85, 140
    # Four battered walls as trapezoids between the foot and the deck edge.
    c.poly([P(o0, o0), P(o1, o0), P(d1, d0), P(d0, d0)], g(side_lit))   # north, lit
    c.poly([P(o0, o0), P(d0, d0), P(d0, d1), P(o0, o1)], g(lit))        # west, lit
    c.poly([P(o1, o0), P(o1, o1), P(d1, d1), P(d1, d0)], g(side_dark))  # east, shade
    c.poly([P(o0, o1), P(d0, d1), P(d1, d1), P(o1, o1)], g(dark))       # south, shade
    if direction == "brutalist":
        # Chamfered outer corners: the foot is an octagon, the corner arrises run up to the deck corners.
        k = 0.34
        for (ax, ay, bx, by, cx2, cy2) in ((o0, o0, o0 + k, o0, o0, o0 + k), (o1, o0, o1 - k, o0, o1, o0 + k),
                                           (o0, o1, o0 + k, o1, o0, o1 - k), (o1, o1, o1 - k, o1, o1, o1 - k)):
            c.poly([P(ax, ay), P(bx, by), P(cx2, cy2)], (0, 0, 0, 0))
    if direction == "earthwork":
        # Sandbag courses along the wall faces.
        for t in (0.3, 0.65):
            y = o0 + (d0 - o0) * t
            for k in range(14):
                x = o0 + 0.15 + k * (o1 - o0 - 0.3) / 13
                c.ellipse(*P(x, y), 0.075 * ppc, g(side_lit - 15), ry=0.05 * ppc)
            y = o1 - (o1 - d1) * t
            for k in range(14):
                x = o0 + 0.15 + k * (o1 - o0 - 0.3) / 13
                c.ellipse(*P(x, y), 0.075 * ppc, g(dark + 15), ry=0.05 * ppc)
    # Deck and parapet.
    c.poly([P(d0, d0), P(d1, d0), P(d1, d1), P(d0, d1)], g(deck + 25))
    inset = 0.07
    c.poly([P(d0 + inset, d0 + inset), P(d1 - inset, d0 + inset), P(d1 - inset, d1 - inset), P(d0 + inset, d1 - inset)],
           g(deck))
    # Turret ring well at the structure centre.
    c.ellipse(*P(1.5, 1.5), 0.62 * ppc, g(110))
    c.ellipse(*P(1.5, 1.5), 0.55 * ppc, g(55))
    # Stair cut into the south wall, down to the ground, steps as bands.
    sx0, sx1 = 1.12, 1.72
    top, foot = d1 - inset, 2.96
    c.poly([P(sx0, top), P(sx1, top), P(sx1, foot), P(sx0, foot)], g(120))
    steps = 7
    for k in range(steps):
        y0 = top + (foot - top) * k / steps
        y1 = y0 + (foot - top) / steps * 0.45
        c.poly([P(sx0 + 0.04, y0), P(sx1 - 0.04, y0), P(sx1 - 0.04, y1), P(sx0 + 0.04, y1)], g(155 - k * 8))
    c.poly([P(sx0 - 0.05, top), P(sx0, top), P(sx0, foot), P(sx0 - 0.05, foot)], g(185))  # stair cheek walls
    c.poly([P(sx1, top), P(sx1 + 0.05, top), P(sx1 + 0.05, foot), P(sx1, foot)], g(70))
    # Corner fittings on the deck (ammunition lockers / vents).
    for x, y in ((0.75, 0.75), (2.1, 0.75), (0.75, 2.1)):
        c.poly([P(x - 0.12, y - 0.1), P(x + 0.12, y - 0.1), P(x + 0.12, y + 0.1), P(x - 0.12, y + 0.1)], g(125))


def heavy_head(c, cx, cy, ppc):
    """Heavy anti-armour gun house: long single barrel with a muzzle brake, rear bustle. Pivot = frame centre."""
    c.ellipse(cx, cy, 0.56 * ppc, g(85))
    c.rect(cx - 0.075 * ppc, cy - 1.42 * ppc, cx + 0.075 * ppc, cy - 0.40 * ppc, g(80))
    c.rect(cx - 0.12 * ppc, cy - 1.44 * ppc, cx + 0.12 * ppc, cy - 1.26 * ppc, g(60))
    c.rect(cx - 0.11 * ppc, cy - 0.85 * ppc, cx + 0.11 * ppc, cy - 0.62 * ppc, g(110))
    body = [(cx - 0.34 * ppc, cy - 0.55 * ppc), (cx + 0.34 * ppc, cy - 0.55 * ppc), (cx + 0.58 * ppc, cy - 0.20 * ppc),
            (cx + 0.58 * ppc, cy + 0.45 * ppc), (cx + 0.40 * ppc, cy + 0.72 * ppc), (cx - 0.40 * ppc, cy + 0.72 * ppc),
            (cx - 0.58 * ppc, cy + 0.45 * ppc), (cx - 0.58 * ppc, cy - 0.20 * ppc)]
    c.poly(body, g(145))
    c.poly([(cx - 0.34 * ppc, cy - 0.55 * ppc), (cx + 0.34 * ppc, cy - 0.55 * ppc), (cx + 0.44 * ppc, cy - 0.30 * ppc),
            (cx - 0.44 * ppc, cy - 0.30 * ppc)], g(190))
    c.poly([(cx - 0.58 * ppc, cy - 0.20 * ppc), (cx - 0.44 * ppc, cy - 0.30 * ppc), (cx - 0.44 * ppc, cy + 0.45 * ppc),
            (cx - 0.58 * ppc, cy + 0.45 * ppc)], g(170))
    c.ellipse(cx + 0.22 * ppc, cy + 0.12 * ppc, 0.11 * ppc, g(120))  # cupola
    c.rect(cx - 0.30 * ppc, cy + 0.05 * ppc, cx - 0.05 * ppc, cy + 0.30 * ppc, g(130), radius=0.03 * ppc)  # hatch
    c.rect(cx - 0.32 * ppc, cy + 0.50 * ppc, cx + 0.32 * ppc, cy + 0.68 * ppc, g(115))  # bustle stowage


def hardpoint_sheet(direction):
    c = Canvas()
    ppc = 256
    plinth(c, 0, 128, ppc, direction)
    heavy_head(c, 768 + 384, 128 + 384, ppc)
    return c


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for direction in DIRECTIONS:
        points_sheet(direction).save(OUT / f"{direction}-points-guide.png")
        hardpoint_sheet(direction).save(OUT / f"{direction}-hardpoint-guide.png")
    # The earthwork points sheet was refused by the image tool's safety filter four times running, so its collar is
    # generated on its own (frame 0 only) and its heads are the brutalist heads recoloured in export.py.
    collar_only = Canvas()
    collar(collar_only, 268, 512, 400, "earthwork")
    collar_only.save(OUT / "earthwork-collar-guide.png")
    print("wrote guides")


if __name__ == "__main__":
    main()
