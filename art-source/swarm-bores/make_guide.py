"""Geometry guides for the swarm bore bodies and the three spore-colony grades.

Every part is one centre-pivoted, upright structure-art layer (``StructureArt``):
square, drawn in the guide's own 1024 px canvas about its centre, and shipped
whole, so the canvas is the registration and the layer's ``cells`` in
``turret-emplacements.turret.json`` sets its size on the map. The silhouettes
are deliberately not all the same reach: what makes a maw colony read bigger
than a fissure colony is both its ``cells`` and its shape (more sacs, more
spines), never only a scale.

- Bore: a ring of hooked chitin plates round an open pit. The pit is part of
  the sprite (opaque, dark), because a bore's mouth is a hole the swarm climbs
  out of, not ground.
- Mother bore: the same organism grown deep, two rings of plates and inward
  tusks round a wider pit.
- Spore colonies: spore sacs cupped by plates, one sac at fissure grade, a
  triad at bore grade, a crowned cluster at maw grade.

Pale yellow (``ACCENT``) marks glow: amber nodes on plates, the sacs' cores,
the heat deep in a pit. ``export.py`` lifts the glow back out of the reskin as
the pulsing layer.

Run from the repository root: ``python art-source/swarm-bores/make_guide.py``.
Writes ``art-source/templates/swarm-<part>-guide.png``.
"""

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

CANVAS = 1024
SUPERSAMPLE = 2

FILL = (132, 132, 136, 255)
SHADE = (98, 98, 104, 255)
LINE = (52, 52, 58, 255)
LIGHT = (176, 176, 182, 255)
PIT = (22, 20, 26, 255)
PIT_RING = (40, 38, 46, 255)
ACCENT = (210, 210, 120, 255)

PARTS = ("bore", "mother-bore", "spore-colony-fissure", "spore-colony-bore", "spore-colony-maw")


class Pen:
    """Draws in guide pixels about the canvas centre."""

    def __init__(self) -> None:
        size = CANVAS * SUPERSAMPLE
        self.image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.image)
        self.k = SUPERSAMPLE
        self.c = size / 2

    def p(self, x: float, y: float) -> tuple[float, float]:
        return self.c + x * self.k, self.c + y * self.k

    def disc(self, cx, cy, r, fill, outline=LINE, width=6):
        x0, y0 = self.p(cx - r, cy - r)
        x1, y1 = self.p(cx + r, cy + r)
        self.draw.ellipse((x0, y0, x1, y1), fill=fill, outline=outline, width=int(width * self.k))

    def poly(self, points, fill, outline=LINE, width=6):
        self.draw.polygon([self.p(x, y) for x, y in points], fill=fill)
        if outline is not None:
            pts = [self.p(x, y) for x, y in points]
            self.draw.line(pts + [pts[0]], fill=outline, width=int(width * self.k), joint="curve")

    def line(self, points, width, fill=LINE):
        self.draw.line([self.p(x, y) for x, y in points], fill=fill, width=max(1, int(width * self.k)),
                       joint="curve")

    def finish(self) -> Image.Image:
        return self.image.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)


def polar(r: float, a: float) -> tuple[float, float]:
    return r * math.cos(a), r * math.sin(a)


def plate(pen: Pen, a: float, span: float, r0: float, r1: float, hook: float, fill=FILL, seams=2):
    """One petal-shaped chitin plate from radius r0 to r1, bulging at its outer edge and leaning by ``hook``."""
    steps = 16
    left, right = [], []
    for i in range(steps + 1):
        t = i / steps
        r = r0 + (r1 - r0) * t
        # Narrow at the lip, widest two thirds out, rounded off at the rim.
        half = span / 2 * (0.35 + 0.75 * math.sin(math.pi * min(1.0, 0.25 + 0.75 * t)) ** 0.6)
        lean = hook * (1 - t) ** 2
        left.append(polar(r, a + lean - half))
        right.append(polar(r, a + lean + half))
    rim = [polar(r1 + 18 * math.sin(math.pi * i / 8), a - span * 0.32 + span * 0.64 * i / 8) for i in range(1, 8)]
    pen.poly(left + rim + right[::-1], fill)
    for s in range(1, seams + 1):
        t = s / (seams + 1)
        r = r0 + (r1 - r0) * (0.25 + 0.7 * t)
        w = span * (0.35 + 0.35 * t)
        lean = hook * (1 - t) ** 2
        pen.line([polar(r - 20 * math.sin(math.pi * i / 6), a + lean - w / 2 + w * i / 6) for i in range(7)], 4)


def tendon(pen: Pen, a: float, r0: float, r1: float, width: float):
    """A fleshy root splaying outward, tapering as it goes."""
    steps = 10
    pts = [polar(r0 + (r1 - r0) * i / steps, a + 0.025 * math.sin(i * 1.3)) for i in range(steps + 1)]
    for i in range(steps):
        w = width * (1 - 0.7 * i / steps)
        pen.line(pts[i:i + 2], w + 8)
    for i in range(steps):
        w = width * (1 - 0.7 * i / steps)
        pen.line(pts[i:i + 2], w, SHADE)


def node(pen: Pen, x, y, r):
    pen.disc(x, y, r, ACCENT, outline=LINE, width=4)


def pit(pen: Pen, r: float, rng: random.Random, glows: int):
    pen.disc(0, 0, r, PIT, outline=LINE, width=8)
    for k in (0.78, 0.55, 0.32):
        pen.disc(0, 0, r * k, None, outline=PIT_RING, width=10)
    for i in range(glows):
        a = 2 * math.pi * i / glows + rng.uniform(-0.3, 0.3)
        x, y = polar(r * rng.uniform(0.12, 0.42), a)
        node(pen, x, y, rng.uniform(9, 14))


def bore(pen: Pen):
    rng = random.Random(11)
    count = 8
    for i in range(count):
        tendon(pen, 2 * math.pi * (i + 0.5) / count, 220, 470, 46)
    pit(pen, 205, rng, 4)
    for i in range(count):
        a = 2 * math.pi * i / count + rng.uniform(-0.06, 0.06)
        plate(pen, a, 0.62, 175, rng.uniform(395, 430), 0.22, seams=2)
        x, y = polar(330, a)
        node(pen, x, y, 11)


def mother_bore(pen: Pen):
    rng = random.Random(23)
    outer = 12
    for i in range(outer):
        tendon(pen, 2 * math.pi * (i + 0.5) / outer, 300, 495, 40)
    for i in range(outer):
        a = 2 * math.pi * i / outer + rng.uniform(-0.04, 0.04)
        plate(pen, a, 0.50, 300, rng.uniform(440, 470), 0.12, fill=SHADE, seams=2)
        x, y = polar(400, a)
        node(pen, x, y, 10)
    pit(pen, 250, rng, 6)
    inner = 10
    for i in range(inner):
        a = 2 * math.pi * (i + 0.5) / inner
        plate(pen, a, 0.58, 205, 320, 0.30, seams=1)
    for i in range(inner):
        # Tusks: long hooked spikes leaning in over the pit.
        a = 2 * math.pi * i / inner
        base_l = polar(300, a - 0.10)
        base_r = polar(300, a + 0.10)
        tip = polar(165, a + 0.30)
        pen.poly([base_l, polar(255, a - 0.02), polar(205, a + 0.14), tip, polar(215, a + 0.22),
                  polar(265, a + 0.16), base_r], LIGHT, width=5)


def sac(pen: Pen, x, y, r, rng: random.Random):
    """A spore sac seen from above: a swollen, slightly lumpy dome with veins and a glowing pore."""
    pts = []
    for i in range(24):
        a = 2 * math.pi * i / 24
        k = 1 + 0.06 * math.sin(3 * a + rng.uniform(0, 6)) + rng.uniform(-0.02, 0.02)
        pts.append((x + r * k * math.cos(a), y + r * k * 0.95 * math.sin(a)))
    pen.poly(pts, FILL, width=7)
    for i in range(5):
        a = 2 * math.pi * i / 5 + rng.uniform(-0.3, 0.3)
        bend = rng.uniform(-0.5, 0.5)
        pen.line([(x + r * q * math.cos(a + bend * q), y + r * q * math.sin(a + bend * q))
                  for q in (0.32, 0.5, 0.68, 0.85)], 4, SHADE)
    pen.disc(x, y, r * 0.30, PIT_RING, outline=LINE, width=5)
    node(pen, x, y, r * 0.20)


def cup(pen: Pen, count: int, r0: float, r1: float, rng: random.Random, spike: float = 0.0):
    for i in range(count):
        a = 2 * math.pi * i / count + rng.uniform(-0.08, 0.08)
        plate(pen, a, 2 * math.pi / count * 0.72, r0, r1 * rng.uniform(0.95, 1.03), 0.10, seams=1)
        if spike:
            pen.poly([polar(r1 - 10, a - 0.07), polar(r1 + spike, a), polar(r1 - 10, a + 0.07)], LIGHT, width=5)


def roots(pen: Pen, count: int, r0: float, r1: float, rng: random.Random):
    for i in range(count):
        a = 2 * math.pi * (i + 0.5) / count + rng.uniform(-0.2, 0.2)
        tendon(pen, a, r0, r1 * rng.uniform(0.85, 1.0), 34)


def spore_colony_fissure(pen: Pen):
    rng = random.Random(31)
    roots(pen, 5, 160, 400, rng)
    cup(pen, 3, 150, 330, rng)
    sac(pen, 0, 0, 215, rng)


def spore_colony_bore(pen: Pen):
    rng = random.Random(37)
    roots(pen, 7, 220, 440, rng)
    cup(pen, 6, 230, 400, rng)
    for i in range(3):
        x, y = polar(130, 2 * math.pi * i / 3 - math.pi / 2)
        sac(pen, x, y, 150, rng)
    node(pen, 0, 0, 22)


def spore_colony_maw(pen: Pen):
    rng = random.Random(41)
    roots(pen, 9, 280, 470, rng)
    cup(pen, 9, 270, 410, rng, spike=55)
    for i in range(5):
        x, y = polar(255, 2 * math.pi * i / 5 - math.pi / 2 + math.pi / 5)
        sac(pen, x, y, 105, rng)
    sac(pen, 0, 0, 185, rng)


DRAWERS = {
    "bore": bore,
    "mother-bore": mother_bore,
    "spore-colony-fissure": spore_colony_fissure,
    "spore-colony-bore": spore_colony_bore,
    "spore-colony-maw": spore_colony_maw,
}


def main() -> None:
    templates = Path(__file__).resolve().parent.parent / "templates"
    for part in PARTS:
        pen = Pen()
        DRAWERS[part](pen)
        out = templates / f"swarm-{part}-guide.png"
        pen.finish().save(out, optimize=True)
        print(out)


if __name__ == "__main__":
    main()
