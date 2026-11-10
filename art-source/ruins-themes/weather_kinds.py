"""Block-out kinds for the delve's weather decals (``delve-weather``): what the world and the years leave on a dead
site's floors, laid by ``RuinsWeather`` over the dead town and through the megastructure's corridors.

Every decal is flat and translucent, drawn as a pale grey shape ImageGen keeps the outline of:

- ``w/fan``: a tongue of the world's ground blown in through a mouth, widest along its south edge (the edge it is laid
  against: the doorway or the drift's source) and thinning in lobes to the north.
- ``w/debris``: fallen ceiling: a dust fan with chunks lying in it.
- ``w/crack``: a crack network spreading from one point, a few branching lines.
- ``w/roots``: roots reaching in from the south edge, branching as they go.
"""
import math
import random

TINT = (150, 154, 160, 140)
CHUNK = (128, 132, 138, 255)


def _lobed(d, cx, cy, rx, ry, rng, fill):
    harm = [(k, rng.uniform(0.06, 0.14) / k ** 0.5, rng.uniform(0, 2 * math.pi)) for k in (2, 3, 5)]
    pts = []
    for i in range(120):
        a = i / 120 * 2 * math.pi
        rr = 1 + sum(amp * math.sin(k * a + ph) for k, amp, ph in harm)
        pts.append((cx + rx * rr * math.cos(a), cy + ry * rr * math.sin(a)))
    d.polygon(pts, fill=fill)


def _branch(d, x, y, angle, length, width, depth, rng, fill):
    """A line wandering from (x, y) at ``angle``, splitting ``depth`` more times."""
    pts = [(x, y)]
    steps = 8
    for _ in range(steps):
        angle += rng.uniform(-0.18, 0.18)
        x += math.cos(angle) * length / steps
        y += math.sin(angle) * length / steps
        pts.append((x, y))
    d.line(pts, fill=fill, width=max(4, int(width)), joint="curve")
    if depth > 0:
        for side in (-1, 1):
            k = rng.randrange(3, steps)
            bx, by = pts[k]
            _branch(d, bx, by, angle + side * rng.uniform(0.5, 0.9), length * 0.55, width * 0.6, depth - 1, rng, fill)


def draw_kind(im, d, kind, x, y, w, h):
    if not kind.startswith("w/"):
        return False
    rng = random.Random(f"{kind}{x}{y}")
    m = 30
    if kind == "w/fan":
        # Widest at the south edge, thinning north in a few slow lobes: a half outline from the edge's two corners.
        harm = [(k, rng.uniform(0.04, 0.09), rng.uniform(0, 2 * math.pi)) for k in (2, 3)]
        pts = [(x + m, y + h - m)]
        for i in range(1, 60):
            a = math.pi * i / 60
            rr = 1 + sum(amp * math.sin(k * a + ph) for k, amp, ph in harm)
            pts.append((x + w / 2 - (w / 2 - m) * math.cos(a) * rr, y + h - m - (h - 2 * m) * 0.92 * math.sin(a) * rr))
        pts.append((x + w - m, y + h - m))
        d.polygon(pts, fill=TINT)
    elif kind == "w/debris":
        _lobed(d, x + w / 2, y + h / 2, w * 0.38, h * 0.38, rng, TINT)
        for _ in range(int(7 * w * h / 320 ** 2) + 3):
            cx = x + w / 2 + max(-w / 4, min(w / 4, rng.gauss(0, w / 8)))
            cy = y + h / 2 + max(-h / 4, min(h / 4, rng.gauss(0, h / 8)))
            r = rng.uniform(0.07, 0.13) * min(w, h)
            pts = [(cx + r * rng.uniform(0.6, 1) * math.cos(a), cy + r * rng.uniform(0.6, 1) * math.sin(a))
                   for a in sorted(rng.uniform(0, 2 * math.pi) for _ in range(6))]
            d.polygon(pts, fill=CHUNK, outline=(70, 74, 80, 255))
    elif kind == "w/crack":
        cx, cy = x + w / 2 + rng.uniform(-20, 20), y + h / 2 + rng.uniform(-20, 20)
        arms = 4 if w >= h else 2
        for k in range(arms):
            a = (k / arms) * 2 * math.pi + rng.uniform(-0.3, 0.3) if w >= h else (-math.pi / 2 if k == 0 else math.pi / 2)
            _branch(d, cx, cy, a, min(w, h) * 0.3 if w >= h else h * 0.36, 12, 2, rng, (60, 62, 66, 220))
    elif kind == "w/roots":
        for k in range(3):
            sx = x + w * (0.3 + 0.2 * k) + rng.uniform(-10, 10)
            _branch(d, sx, y + h - m, -math.pi / 2 + rng.uniform(-0.3, 0.3), (h - 2 * m) * 0.75, 20, 1, rng,
                    (110, 104, 92, 230))
    return True
