"""Trace the deck schematic's ship from the vanilla Cerberus sprite: its outline, and its hull cut into compartments.

Run from the repository root:  python -I art-source/hq-command-deck/trace_ship.py

The outline is the sprite's alpha, enlarged, smoothed and traced. Compartments grow outwards from a hand-placed seed
per section of the hull; growth is slow through the sprite's dark seams, so neighbouring compartments meet along the
seams the sprite paints. The ship lies bow to the left, as the bridge does. One compartment per seed.

Writes, in units of sprite pixels:
  mod/graphics/ui/hq-command-deck/deck-ship.svg           the plan: outline, compartment lines and the round bays
  mod/graphics/ui/hq-command-deck/deck-ship-sections.svg  one filled shape per compartment, a colour each so each
                                                          compiles to its own layer, for lighting one at a time
  deck-ship-review.png                                    the plan drawn over the sprite, for checking the trace
"""
import heapq
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
OUT = HERE.parent.parent / "mod/graphics/ui/hq-command-deck"
SPRITE = Path(r"C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core\graphics\ships\cerberus\cerberus_base.png")
UP = 6  # the trace's working pixels per sprite pixel
SEAM = 10.0  # how much slower growth is through a black pixel than a white one

# Compartment seeds and round bays, read off the turned sprite in its own pixels (x towards the stern, y down).
SEEDS = {
    "bow-tank": (24, 24), "bow-bay": (19, 51), "nose": (11, 40), "forward-core": (37, 40), "citadel": (53, 40),
    "upper-shoulder": (42, 27), "lower-shoulder": (40, 54), "upper-wing": (71, 20), "lower-wing": (71, 60),
    "upper-armour": (70, 9), "lower-armour": (70, 71), "upper-engines": (89, 12), "lower-engines": (89, 69),
    "stern": (81, 40), "upper-quarter": (79, 27), "lower-quarter": (79, 53), "forward-mount": (24, 68),
    "upper-gear": (41, 15), "upper-waist": (54, 22), "lower-waist": (54, 58),
}
BAYS = [(24.0, 24.0, 9.5), (19.0, 51.0, 5.8), (71.0, 20.0, 6.2), (71.0, 60.0, 6.2)]
MOUNTS = [(42.0, 28.0, 2.4), (42.0, 52.0, 2.4)]


def turned(path):
    """The sprite as RGBA in 0..1, bow to the left."""
    return np.rot90(np.asarray(Image.open(path).convert("RGBA")).astype(float) / 255, 1)


def loops(mask):
    """Closed outlines of a boolean mask, walked along pixel edges, as lists of (x, y); the mask's outside on the left."""
    h, w = mask.shape
    m = np.zeros((h + 2, w + 2), bool)
    m[1:-1, 1:-1] = mask
    next_of = {}
    for y, x in zip(*np.nonzero(m)):
        x, y = int(x), int(y)
        if not m[y - 1, x]:
            next_of.setdefault((x, y), []).append((x + 1, y))
        if not m[y, x + 1]:
            next_of.setdefault((x + 1, y), []).append((x + 1, y + 1))
        if not m[y + 1, x]:
            next_of.setdefault((x + 1, y + 1), []).append((x, y + 1))
        if not m[y, x - 1]:
            next_of.setdefault((x, y + 1), []).append((x, y))
    out = []
    while next_of:
        start = next(iter(next_of))
        loop, here = [], start
        while True:
            loop.append((here[0] - 1, here[1] - 1))
            options = next_of[here]
            step = options.pop()
            if not options:
                del next_of[here]
            here = step
            if here == start:
                break
        out.append(loop)
    return sorted(out, key=len, reverse=True)


def simplify(points, tolerance):
    """Ramer-Douglas-Peucker over a closed loop, cut at its two furthest-apart ends."""
    def rdp(p):
        if len(p) < 3:
            return p
        a, b = np.array(p[0], float), np.array(p[-1], float)
        d = b - a
        n = max(1e-6, np.hypot(*d))
        q = np.array(p[1:-1], float) - a
        off = np.abs(q[:, 0] * d[1] - q[:, 1] * d[0]) / n
        i = int(off.argmax())
        if off[i] <= tolerance:
            return [p[0], p[-1]]
        return rdp(p[:i + 2])[:-1] + rdp(p[i + 1:])
    half = len(points) // 2
    return rdp(points[:half + 1])[:-1] + rdp(points[half:] + [points[0]])[:-1]


def smoothed(mask, radius):
    image = Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    return np.asarray(image) > 127


def outline_paths(mask, smooth, tolerance, keep=60):
    """SVG path data of a mask's outlines, in sprite pixels; slivers shorter than `keep` working pixels are dropped."""
    parts = []
    for loop in loops(smoothed(mask, smooth)):
        if len(loop) < keep:
            continue
        pts = simplify(loop, tolerance)
        parts.append("M" + " L".join(f"{x / UP:.1f},{y / UP:.1f}" for x, y in pts) + " Z")
    return " ".join(parts)


def grow(hull, luma, seeds):
    """Each hull pixel's nearest seed, where distance is slower through dark pixels."""
    h, w = hull.shape
    label = np.full((h, w), -1, int)
    best = np.full((h, w), np.inf)
    queue = []
    for i, (sx, sy) in enumerate(seeds):
        x, y = int(sx * UP), int(sy * UP)
        if not hull[y, x]:
            raise SystemExit(f"seed {i} at {sx},{sy} is off the hull")
        best[y, x] = 0.0
        label[y, x] = i
        heapq.heappush(queue, (0.0, y, x, i))
    cost = 1.0 + SEAM * (1.0 - luma)
    while queue:
        d, y, x, i = heapq.heappop(queue)
        if d > best[y, x]:
            continue
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and hull[ny, nx]:
                nd = d + cost[ny, nx]
                if nd < best[ny, nx]:
                    best[ny, nx] = nd
                    label[ny, nx] = i
                    heapq.heappush(queue, (nd, ny, nx, i))
    return label


def main():
    sprite = turned(SPRITE)
    h, w = sprite.shape[:2]
    big = Image.fromarray((sprite * 255).astype(np.uint8), "RGBA").resize((w * UP, h * UP), Image.BICUBIC)
    rgba = np.asarray(big).astype(float) / 255
    hull = smoothed(rgba[..., 3] > 0.45, UP * 0.35)
    luma = np.asarray(big.convert("L").filter(ImageFilter.GaussianBlur(UP * 0.4))).astype(float) / 255
    names = list(SEEDS)
    label = grow(hull, luma, [SEEDS[n] for n in names])

    outline = outline_paths(hull, UP * 0.55, UP * 0.5)
    plan = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">',
            "  <!-- Deck display: the Cerberus's plan, traced from its sprite by art-source/hq-command-deck/trace_ship.py. -->",
            "  <!-- Tinted by the display; the scan line and lit compartments are drawn over it live. -->",
            f'  <path d="{outline}" fill="#ffffff" fill-opacity="0.12" fill-rule="evenodd"/>']
    sections = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">',
                "  <!-- Deck display: one shape per compartment of deck-ship.svg, each its own colour so it is its own layer. -->"]
    for i, name in enumerate(names):
        shape = outline_paths(label == i, UP * 0.7, UP * 0.5)
        if not shape:
            raise SystemExit(f"compartment {name} came out empty")
        plan.append(f'  <path d="{shape}" fill="none" stroke="#ffffff" stroke-opacity="0.5" stroke-width="0.45" stroke-linejoin="round"/>')
        colour = f"#{(i + 1) * 12:02x}ff{255 - (i + 1) * 12:02x}"
        sections.append(f'  <path d="{shape}" fill="{colour}" fill-rule="evenodd"/>')
    for cx, cy, r in BAYS + MOUNTS:
        plan.append(f'  <circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="#ffffff" stroke-opacity="0.85" stroke-width="0.6"/>')
    plan.append(f'  <path d="{outline}" fill="none" stroke="#ffffff" stroke-width="1.1" stroke-linejoin="round"/>')
    (OUT / "deck-ship.svg").write_text("\n".join(plan + ["</svg>", ""]), encoding="utf-8")
    (OUT / "deck-ship-sections.svg").write_text("\n".join(sections + ["</svg>", ""]), encoding="utf-8")

    # review: the sprite with the compartments' edges and seeds on it
    review = Image.new("RGB", (1920, 1080), (24, 26, 30))
    k = min(1920 / w, 1080 / h)
    shown = big.resize((round(w * k), round(h * k)), Image.BICUBIC)
    ox, oy = (1920 - shown.width) // 2, (1080 - shown.height) // 2
    review.paste(shown, (ox, oy), shown)
    edge = (label != np.roll(label, 1, 0)) | (label != np.roll(label, 1, 1))
    edge = Image.fromarray((edge * 255).astype(np.uint8)).resize(shown.size, Image.NEAREST)
    review.paste((120, 255, 200), (ox, oy), edge)
    draw = ImageDraw.Draw(review)
    for name, (x, y) in SEEDS.items():
        draw.text((ox + x * k - 20, oy + y * k), name, fill=(255, 255, 120))
    review.save(HERE / "deck-ship-review.png")
    print("outline bytes", len(outline), "svg bytes", sum(len(s) for s in plan), "compartments", len(names))


if __name__ == "__main__":
    sys.setrecursionlimit(20000)
    main()
