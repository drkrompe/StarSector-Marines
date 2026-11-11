"""The tank farm's earth bund (`military-set-pieces`, raid-redesign): a knee-high bank of packed earth in the fort
earthworks' colours, as one-cell pieces that join into a ring: a straight run each way and the four outer corners.

Painted from a height field rather than generated, so every piece meets its neighbour exactly: the bank's crest runs
along the middle of the cell, its height falls off with distance from that line, and the light (from the north-west,
as on the rest of the military-base sheet) shades its slopes. Its surface noise is tiled with the cell's period, so
two pieces side by side continue the same earth. The bank fades out at its toe, so it settles onto whatever ground
the map lays under it, and its south-east toe carries a short contact shadow.
"""
import sys

import numpy as np
from PIL import Image

CELL = 128
HALF_WIDTH = 0.36          # the bank's half-width at its toe, in cells
LIGHT = np.array([-0.55, -0.55, 0.63])  # from the north-west and above (x east, y south, z up)
LIGHT = LIGHT / np.linalg.norm(LIGHT)
# Fort earthworks colours: sunlit tan earth and its dark worked soil.
TAN, SOIL = np.array([196, 172, 124], np.float32), np.array([92, 74, 52], np.float32)
# Which arms each piece runs out to, from the cell's middle: N, E, S, W.
PIECES = {
    "earth-bund-h": ("E", "W"),
    "earth-bund-v": ("N", "S"),
    "earth-bund-corner-nw": ("E", "S"),   # the ring's north-west corner: the bank turns from east to south
    "earth-bund-corner-ne": ("W", "S"),
    "earth-bund-corner-sw": ("E", "N"),
    "earth-bund-corner-se": ("W", "N"),
}


def tile_noise(seed, octaves=((8, 1.0), (16, 0.5), (32, 0.25))):
    """Smooth noise exactly periodic with the cell (random waves of whole-number frequency per cell), so
    neighbouring pieces continue one surface with no crease at the join. ``octaves`` are (top frequency, weight)."""
    rnd = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:CELL, 0:CELL].astype(np.float32) / CELL * 2 * np.pi
    out = np.zeros((CELL, CELL), np.float32)
    for top, amp in octaves:
        for _ in range(24):
            fx, fy = rnd.integers(-top, top + 1, 2)
            if fx == 0 and fy == 0:
                continue
            out += amp / np.hypot(fx, fy) * np.cos(fx * xs + fy * ys + rnd.uniform(0, 2 * np.pi))
    return (out - out.min()) / (out.max() - out.min())


PAD = 16  # px computed past each edge, so slopes, normals and the shadow continue into the neighbour
ARM_DIR = {"N": (0, -1), "E": (1, 0), "S": (0, 1), "W": (-1, 0)}


def distance_to_arms(arms):
    """Each pixel's distance, in cells, to the crest: segments from the middle out through each arm's edge and on
    past it, over the padded field."""
    n = CELL + 2 * PAD
    ys, xs = np.mgrid[0:n, 0:n].astype(np.float32)
    px, py = (xs - PAD + 0.5) / CELL, (ys - PAD + 0.5) / CELL
    d = np.full((n, n), 9.0, np.float32)
    reach = 0.5 + PAD / CELL + 0.5
    for arm in arms:
        ax, ay = 0.5, 0.5
        vx, vy = ARM_DIR[arm][0] * reach, ARM_DIR[arm][1] * reach
        t = np.clip(((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy), 0, 1)
        d = np.minimum(d, np.hypot(px - (ax + t * vx), py - (ay + t * vy)))
    return d


def shifted(a, dx, dy):
    """``a`` moved ``dx`` east and ``dy`` south, zero-filled (no wrap)."""
    out = np.zeros_like(a)
    out[dy:, dx:] = a[:a.shape[0] - dy, :a.shape[1] - dx]
    return out


def piece(arms, seed=7):
    d = distance_to_arms(arms) / HALF_WIDTH
    height = np.where(d < 1, np.clip(np.cos(np.clip(d, 0, 1) * np.pi / 2), 0, 1) ** 1.4, 0).astype(np.float32)
    noise = np.pad(tile_noise(seed), PAD, mode="wrap")
    height = height * (0.9 + 0.2 * noise)
    gy, gx = np.gradient(height * 22.0)
    normal = np.dstack((-gx, -gy, np.ones_like(gx)))
    normal /= np.linalg.norm(normal, axis=2, keepdims=True)
    lit = np.clip((normal @ LIGHT - 0.35) / 0.65, 0, 1)
    grit = np.pad(tile_noise(seed + 1, ((32, 1.0), (64, 0.6))), PAD, mode="wrap")
    shade = np.clip(0.25 + 0.75 * lit + (grit - 0.5) * 0.25, 0, 1)
    rgb = SOIL[None, None, :] + (TAN - SOIL)[None, None, :] * shade[..., None]
    # The toe fades into the ground; the crest is fully opaque.
    alpha = np.clip((1 - d) / 0.35, 0, 1)
    # A short contact shadow off the south-east toe, laid under the bank.
    shadow = shifted(alpha, 5, 5) * 0.35
    out_a = alpha + shadow * (1 - alpha)
    dark = np.array([28, 32, 44], np.float32)  # the cool shadow the map casts
    rgb = (rgb * alpha[..., None] + dark * (shadow * (1 - alpha))[..., None]) / np.maximum(out_a, 1e-6)[..., None]
    img = np.dstack((np.clip(rgb, 0, 255), out_a * 255)).astype(np.uint8)[PAD:PAD + CELL, PAD:PAD + CELL]
    return Image.fromarray(img, "RGBA")


def pieces():
    for pid, arms in PIECES.items():
        yield pid, piece(arms)


if __name__ == "__main__":
    out = Image.new("RGBA", (CELL * 4, CELL * 4), (150, 140, 110, 255))
    ring = [["earth-bund-corner-nw", "earth-bund-h", "earth-bund-h", "earth-bund-corner-ne"],
            ["earth-bund-v", None, None, "earth-bund-v"],
            ["earth-bund-v", None, None, "earth-bund-v"],
            ["earth-bund-corner-sw", "earth-bund-h", "earth-bund-h", "earth-bund-corner-se"]]
    made = dict(pieces())
    for r, row in enumerate(ring):
        for c, pid in enumerate(row):
            if pid:
                out.alpha_composite(made[pid], (c * CELL, r * CELL))
    out.save(sys.argv[1] if len(sys.argv) > 1 else "bund-ring.png")
