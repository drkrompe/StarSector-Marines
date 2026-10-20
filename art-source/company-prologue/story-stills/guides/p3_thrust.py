"""P3 (last-carrier): put the freighter's thrust back in its own six drive bells.

    python p3_thrust.py guide   --master ../last-carrier.master.png --out p3-thrust-guide.png
    python p3_thrust.py compose --master ../last-carrier.master.png --gen <generation.png> --out <candidate.png>

The 2026-10-19 page painted the real Colossus over the old freighter, but the guide it was painted
from still carried the old ship's two plumes and a lit bank under the right pod, and ImageGen kept
them: the thrust came from nowhere near the drives (owner, 2026-10-20). The Colossus's drive bells
are the two three-bell clusters on the rear faces of its outboard pods, facing the camera.

`guide` takes the current master, blanks the old plumes and the extra module and its light bank
back to space, and paints the six bells hot with short exhaust foreshortened toward the camera.
`compose` lays a generation back over the master through a feathered mask over the pods' rear and
the exhaust only (after finding the generation's small offset against the master), so Castle, the
crowd, the children, the window and the rest of the ship stay pixel-identical. It prints the
largest change outside the mask, which must be zero.

Both modes were run against the 2026-10-19 master (`git show b7b252566:<story-stills>/last-carrier.master.png`);
the current master is the result (approved 2026-10-20), so rerunning against it repeats nothing useful.
"""
import argparse
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# Bell throats in the 1536x1024 master: (x, y, radius). Measured on the master; the left pod's
# cluster is lower-centre on the pod, the right pod's upper-right.
BELLS = [(560.6, 236.9, 4.6), (570.3, 233.8, 4.6), (579.4, 229.4, 4.6),
         (683.1, 191.3, 3.4), (690.6, 188.1, 3.4), (697.3, 185.6, 3.4)]
# Screen direction of the thrust axis: the bell faces are seen from above and to the right, so
# exhaust coming toward the camera runs down and a little right on the page.
AXIS = np.array([0.50, 0.866])

# What the old freighter left: its plumes and the lit module under the right pod (outside the hull).
OLD = [
    [(600, 212), (708, 204), (724, 226), (800, 252), (858, 282), (858, 322), (790, 318),
     (700, 292), (618, 262), (598, 246)],
    [(488, 258), (545, 244), (600, 248), (700, 290), (740, 312), (720, 328), (650, 318),
     (580, 300), (540, 286), (500, 280)],
]
# The region a generation may replace: the pods' rear, the cleared ground and the new exhaust.
# It stops above the children's heads and short of the hull's far end by the rocks.
EDIT = [(522, 150), (640, 140), (730, 150), (870, 268), (870, 336), (760, 338), (640, 330),
        (560, 304), (530, 288), (515, 250)]


def load(p):
    return np.asarray(Image.open(p).convert("RGB")).astype(np.float64)


def poly_mask(size, polys, feather):
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    for p in polys:
        d.polygon(p, fill=255)
    if feather:
        m = m.filter(ImageFilter.GaussianBlur(feather))
    return np.asarray(m).astype(np.float64) / 255.0


def guide(master_path, out):
    m = load(master_path)
    h, w, _ = m.shape
    # Clear the old plumes and module to deep space. Keep the hull itself, which lies above and
    # left of the old module: only the region below the right pod's outline is cleared there.
    clear = poly_mask((w, h), OLD, 0)
    pods = poly_mask((w, h), [[(520, 150), (712, 150), (712, 212), (640, 206), (600, 214),
                               (588, 246), (520, 262)]], 0)
    clear = clear * (1 - pods)
    clear = np.asarray(Image.fromarray((clear * 255).astype(np.uint8))
                       .filter(ImageFilter.GaussianBlur(5))).astype(np.float64) / 255.0
    space = np.median(m[190:235, 740:840].reshape(-1, 3), axis=0)
    rng = np.random.default_rng(3)
    fill = np.empty_like(m)
    fill[:] = space
    fill += rng.normal(0, 2.0, fill.shape)
    for _ in range(70):                                   # a few stars where the plumes were
        x, y = rng.integers(600, 860), rng.integers(205, 330)
        fill[y, x] = rng.uniform(120, 220)
    g = m * (1 - clear[..., None]) + fill * clear[..., None]

    # Exhaust: light added in linear-ish space; a white-hot throat, an orange body, a soft spread.
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    add = np.zeros_like(m)
    for (bx, by, r) in BELLS:
        scale = r / 4.6
        length = 70 * scale
        for t in np.linspace(0, 1, 28):
            cx, cy = np.array([bx, by]) + AXIS * length * t
            sig = r * (0.9 + 2.6 * t)
            amp = (1 - t) ** 1.6 * 0.55
            k = np.exp(-((xx - cx) ** 2 + (yy - cy) ** 2) / (2 * sig * sig)) * amp
            add += k[..., None] * np.array([255, 140, 55])
        core = np.exp(-((xx - bx) ** 2 + (yy - by) ** 2) / (2 * (r * 0.75) ** 2))
        add += core[..., None] * np.array([255, 245, 225]) * 1.3
        halo = np.exp(-((xx - bx) ** 2 + (yy - by) ** 2) / (2 * (r * 3.2) ** 2))
        add += halo[..., None] * np.array([255, 150, 70]) * 0.35
    # Throats: the dark bell interiors become hot.
    for (bx, by, r) in BELLS:
        d = np.hypot(xx - bx, yy - by)
        throat = np.clip((r - d) / 1.2, 0, 1)
        g = g * (1 - throat[..., None]) + np.array([255, 214, 150]) * throat[..., None]
    g = 255 - (255 - g) * np.exp(-add / 255.0)           # screen-like, never clips hard
    Image.fromarray(np.clip(g, 0, 255).astype(np.uint8)).save(out)
    print("guide", out)


def best_offset(master, gen, mask):
    """Integer shift of the generation that best matches the master just outside the edit region."""
    ring = (mask > 0.02) & (mask < 0.6)
    best = None
    for dy in range(-6, 7):
        for dx in range(-6, 7):
            s = np.roll(np.roll(gen, dy, 0), dx, 1)
            e = np.abs(s - master)[ring].mean()
            if best is None or e < best[0]:
                best = (e, dx, dy)
    return best


def compose(master_path, gen_path, out):
    m = load(master_path)
    g = Image.open(gen_path).convert("RGB")
    if g.size != (m.shape[1], m.shape[0]):
        g = g.resize((m.shape[1], m.shape[0]), Image.LANCZOS)
    g = np.asarray(g).astype(np.float64)
    hard = poly_mask((m.shape[1], m.shape[0]), [EDIT], 0)
    # Feather inward only: erode, soften, then clip to the polygon so nothing outside it changes.
    inner = Image.fromarray((hard * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(25))
    mask = np.asarray(inner.filter(ImageFilter.GaussianBlur(4))).astype(np.float64) / 255.0 * hard
    e, dx, dy = best_offset(m, g, mask)
    g = np.roll(np.roll(g, dy, 0), dx, 1)
    c = m * (1 - mask[..., None]) + g * mask[..., None]
    c = np.clip(np.rint(c), 0, 255).astype(np.uint8)
    Image.fromarray(c).save(out)
    outside = hard < 0.5
    diff = np.abs(c.astype(int) - m.astype(np.uint8).astype(int)).max(2)
    print("offset dx=%d dy=%d ring-error=%.2f; max change outside edit region: %d; changed px: %d"
          % (dx, dy, e, diff[outside].max(), int((diff > 0).sum())))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["guide", "compose", "mask"])
    ap.add_argument("--master", required=True)
    ap.add_argument("--gen")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    if a.mode == "guide":
        guide(a.master, a.out)
    elif a.mode == "compose":
        compose(a.master, a.gen, a.out)
    else:
        m = load(a.master)
        hard = poly_mask((m.shape[1], m.shape[0]), [EDIT], 0)
        Image.fromarray((hard * 255).astype(np.uint8)).save(a.out)


if __name__ == "__main__":
    sys.exit(main())
