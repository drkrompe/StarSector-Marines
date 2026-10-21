"""Geometry guides for the Brute and Spitter swarm strains.

Each live layer is drawn in runtime pixels (the infantry compositor's 150 px
shoulder registration, see ``LayeredUnitComposer.SOURCE_SHOULDER_PX``) and
scaled by ``SCALE`` onto a square 1024 canvas, centred. The export window for
every part is recorded in ``geometry.json`` so ``export.py`` can cut the
reskinned raw back to the runtime canvas mechanically: the window, not the
alpha extent, is the registration, so a spitter's acid sac can sit behind the
shoulders without moving them.

The spitter reuses the Runner's foot and fore-claw (same lineage, same gait);
the Brute gets its own heavier foot and crushing fore-claw.

Run from the repository root: ``python art-source/swarm-strains/make_guide.py``.
Writes ``art-source/templates/swarm-<strain>-<part>-guide.png``.
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

CANVAS = 1024
SCALE = 6
SUPERSAMPLE = 2

FILL = (132, 132, 136, 255)
SHADE = (98, 98, 104, 255)
LINE = (52, 52, 58, 255)
LIGHT = (176, 176, 182, 255)
ACCENT = (210, 210, 120, 255)  # marks where the strain's glow belongs

# Runtime export windows in pixels, width x height. Body widths are the
# 150 px registration; heads keep the Runner's 72 px width.
WINDOWS = {
    "brute-body": (150, 110),
    "brute-head": (72, 92),
    "brute-foot": (38, 60),
    "brute-fore-claw": (64, 96),
    "spitter-body": (150, 150),
    "spitter-head": (72, 116),
}


class Pen:
    """Draws in runtime pixels about the window centre, at guide resolution."""

    def __init__(self) -> None:
        size = CANVAS * SUPERSAMPLE
        self.image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.image)
        self.k = SCALE * SUPERSAMPLE
        self.c = size / 2

    def p(self, x: float, y: float) -> tuple[float, float]:
        return self.c + x * self.k, self.c + y * self.k

    def ellipse(self, cx, cy, w, h, fill, outline=None, width=0):
        x0, y0 = self.p(cx - w / 2, cy - h / 2)
        x1, y1 = self.p(cx + w / 2, cy + h / 2)
        self.draw.ellipse((x0, y0, x1, y1), fill=fill, outline=outline,
                          width=int(width * self.k))

    def poly(self, points, fill, outline=None, width=0):
        self.draw.polygon([self.p(x, y) for x, y in points], fill=fill,
                          outline=outline, width=int(width * self.k) or 1)

    def line(self, points, width, fill=LINE):
        self.draw.line([self.p(x, y) for x, y in points], fill=fill,
                       width=max(1, int(width * self.k)), joint="curve")

    def arc(self, cx, cy, w, h, start, end, width, fill=LINE):
        x0, y0 = self.p(cx - w / 2, cy - h / 2)
        x1, y1 = self.p(cx + w / 2, cy + h / 2)
        self.draw.arc((x0, y0, x1, y1), start, end, fill=fill,
                      width=max(1, int(width * self.k)))

    def finish(self) -> Image.Image:
        return self.image.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)


def hook(pen: Pen, base_x, base_y, tip_x, tip_y, bend, width, fill=LIGHT):
    """A tapering curved talon from base to tip, bowed sideways by ``bend``."""
    steps = 12
    left, right = [], []
    for i in range(steps + 1):
        t = i / steps
        x = base_x + (tip_x - base_x) * t + bend * math.sin(math.pi * t)
        y = base_y + (tip_y - base_y) * t
        half = width * (1 - t) / 2 + 0.3
        dx, dy = tip_x - base_x, tip_y - base_y
        n = math.hypot(dx, dy) or 1
        nx, ny = -dy / n, dx / n
        left.append((x + nx * half, y + ny * half))
        right.append((x - nx * half, y - ny * half))
    pen.poly(left + right[::-1], fill, outline=LINE, width=0.6)


def brute_body() -> Image.Image:
    pen = Pen()
    # Pauldrons first so the dorsal shell overlaps them.
    for side in (-1, 1):
        pen.ellipse(side * 47, 4, 56, 80, SHADE, LINE, 1.2)
        for i in range(3):
            pen.arc(side * 47, 4 + i * 12, 50, 52, 200, 340, 1.1)
    pen.ellipse(0, 8, 104, 100, FILL, LINE, 1.4)
    # Overlapping dorsal plates, front to back.
    for i, y in enumerate((-18, 2, 22, 40)):
        pen.arc(0, y, 96 - i * 8, 46, 200, 340, 1.4)
    pen.line([(0, -30), (0, 54)], 1.2)
    # Low head socket: the head sits forward between the pauldrons.
    pen.ellipse(0, -36, 42, 32, (70, 70, 76, 255), LINE, 1.2)
    for side in (-1, 1):
        pen.ellipse(side * 22, -10, 7, 7, ACCENT)
        pen.ellipse(side * 52, -14, 6, 6, ACCENT)
    return pen.finish()


def brute_head() -> Image.Image:
    pen = Pen()
    pen.poly([(-34, 34), (-35, -6), (-24, -34), (-10, -44), (10, -44),
              (24, -34), (35, -6), (34, 34), (0, 44)], FILL, LINE, 1.4)
    # Broad battering crest plates.
    pen.poly([(-24, -30), (-10, -42), (10, -42), (24, -30), (14, -12),
              (-14, -12)], LIGHT, LINE, 1.0)
    for y in (-2, 12, 26):
        pen.arc(0, y, 60, 26, 200, 340, 1.2)
    pen.line([(0, -42), (0, 40)], 1.0)
    for side in (-1, 1):
        pen.ellipse(side * 26, -38, 10, 10, LIGHT, LINE, 0.8)
        pen.ellipse(side * 18, -2, 5, 5, ACCENT)
    return pen.finish()


def brute_foot() -> Image.Image:
    pen = Pen()
    pen.ellipse(0, 10, 32, 42, FILL, LINE, 1.2)
    for i in range(3):
        pen.arc(0, 4 + i * 9, 28, 16, 200, 340, 1.0)
    for x in (-10, 0, 10):
        hook(pen, x, -6, x * 1.3, -26, 0, 9)
    return pen.finish()


def brute_claw() -> Image.Image:
    pen = Pen()
    # Forearm from the shoulder socket (south) into a heavy club hand.
    pen.poly([(-9, 46), (9, 46), (16, 8), (-16, 8)], SHADE, LINE, 1.2)
    for y in (36, 26, 16):
        pen.arc(0, y, 26, 10, 200, 340, 1.0)
    pen.ellipse(0, -8, 52, 40, FILL, LINE, 1.4)
    pen.arc(0, -4, 42, 22, 200, 340, 1.2)
    # Two thick crushing hooks and a short thumb spur.
    hook(pen, -14, -18, -22, -46, -4, 13)
    hook(pen, 14, -18, 22, -46, 4, 13)
    hook(pen, 0, -24, 0, -38, 0, 8)
    pen.ellipse(0, 2, 6, 6, ACCENT)
    return pen.finish()


def spitter_body() -> Image.Image:
    pen = Pen()
    # Acid sac behind the shoulders; the window is square so the shoulders
    # stay at the canvas centre where the compositor pivots the body.
    pen.ellipse(0, 40, 82, 70, (150, 150, 120, 255), LINE, 1.4)
    for x, w in ((-18, 1.0), (0, 1.0), (18, 1.0)):
        pen.line([(x * 0.6, 12), (x, 40), (x * 0.7, 70)], w, SHADE)
    pen.ellipse(0, 38, 40, 30, ACCENT)
    for side in (-1, 1):
        pen.ellipse(side * 44, -2, 40, 46, SHADE, LINE, 1.2)
        pen.arc(side * 44, 2, 34, 30, 200, 340, 1.0)
    pen.ellipse(0, -2, 92, 50, FILL, LINE, 1.3)
    pen.arc(0, 6, 76, 26, 200, 340, 1.1)
    pen.ellipse(0, -18, 30, 22, (70, 70, 76, 255), LINE, 1.0)
    for side in (-1, 1):
        pen.ellipse(side * 30, 2, 5, 5, ACCENT)
    return pen.finish()


def spitter_head() -> Image.Image:
    pen = Pen()
    # Narrow skull with a flared spitting spout out front.
    pen.ellipse(0, 18, 50, 72, FILL, LINE, 1.3)
    for y in (6, 20, 34):
        pen.arc(0, y, 40, 16, 200, 340, 1.0)
    pen.poly([(-10, -12), (10, -12), (13, -44), (-13, -44)], SHADE, LINE, 1.1)
    for y in (-20, -30, -38):
        pen.line([(-11, y), (11, y)], 0.8)
    pen.ellipse(0, -48, 32, 18, LIGHT, LINE, 1.2)
    pen.ellipse(0, -48, 16, 9, (60, 60, 40, 255))
    pen.ellipse(0, -48, 9, 5, ACCENT)
    for side in (-1, 1):
        pen.ellipse(side * 16, 6, 5, 5, ACCENT)
        pen.poly([(side * 22, 30), (side * 33, 46), (side * 18, 44)], SHADE,
                 LINE, 0.8)
    return pen.finish()


GUIDES = {
    "brute-body": brute_body,
    "brute-head": brute_head,
    "brute-foot": brute_foot,
    "brute-fore-claw": brute_claw,
    "spitter-body": spitter_body,
    "spitter-head": spitter_head,
}


def main() -> None:
    here = Path(__file__).resolve().parent
    templates = here.parent / "templates"
    templates.mkdir(exist_ok=True)
    geometry = {"canvas": CANVAS, "scale": SCALE, "windows": {}}
    for name, draw in GUIDES.items():
        draw().save(templates / f"swarm-{name}-guide.png", optimize=True)
        w, h = WINDOWS[name]
        geometry["windows"][name] = {
            "runtime": [w, h],
            "guide": [CANVAS / 2 - w * SCALE / 2, CANVAS / 2 - h * SCALE / 2,
                      CANVAS / 2 + w * SCALE / 2, CANVAS / 2 + h * SCALE / 2],
        }
    (here / "geometry.json").write_text(json.dumps(geometry, indent=2) + "\n")


if __name__ == "__main__":
    main()
