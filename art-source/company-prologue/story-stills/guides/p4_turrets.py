"""P4 (raising-her): put the two-Vulcan Second Wind (build dc62710dc) on the page.

    python p4_turrets.py guide   --master ../raising-her.master.png --render <p4-stern-quarter.png> --out p4-turrets-guide.png
    python p4_turrets.py compose --master ../raising-her.master.png --gen <generation.png> --out <candidate.png>
    python p4_turrets.py mask    --master ../raising-her.master.png --out <mask.png>

The 2026-10-19 page was painted from a guide of build 80c78dfac, which carried four gun turrets,
one of them a long-barrelled cannon. The owner adopted dc62710dc on 2026-10-20 to match the game
start (sm_second_wind_start): working Vulcans on turret 1 (WS 001, port, aft on this page) and
turret 2 (WS 002, starboard, the primer-green mount mid-deck), an empty housing with a holed
blanking cap on turret 3 (WS 003, behind the bridge dome, nearly hidden here) and a salvage cap on
turret 4 (WS 004, the big ring on the right).

`render` is `prologue_guides.py --views p4-stern-quarter` against the dc62710dc patched blend
(any size; it is resized to the master). P4 is the drive being lit before she leaves the Claim,
which is the patched state the earlier guide also used.

`guide` lays the new render's turrets over the current master inside four boxes, colour-matched
to the page, and where the old guns stood against the dock (now empty space in the render) it
copies the dock from beside the box, so the guide shows the new mounts and no old barrels. `compose`
lays a generation back over the master through those boxes, feathered inward only (after finding
the generation's small offset), so the dock, the figures and the rest of the ship stay
pixel-identical. It prints the largest change outside the boxes, which must be zero.

Run on 2026-10-20 against the 2026-10-19 master (`git show 968bb9112:<story-stills>/raising-her.master.png`):
three generations (see raising-her.master.png.provenance.json); gen3, a clean-up pass over gen2's
composite (guides/p4-turrets-gen2-composite.png), was composed and approved (2026-10-20), so the
current master is the result and rerunning against it repeats nothing useful.
"""
import argparse
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# Edit boxes in the 1536x1024 master, (x0, y0, x1, y1): turret 1 (aft, Vulcan), turret 3 (behind
# the bridge dome, holed cap), turret 2 (mid-deck, Vulcan), turret 4 (right, salvage cap; the old
# long barrel ran across the dock here).
BOXES = {
    "t1": (322, 256, 536, 354),
    "t3": (878, 234, 964, 300),
    "t2": (746, 296, 992, 374),
    "t4": (1046, 260, 1302, 347),
}
SPACE = 7.0      # render luminance below this is empty space
# Boxes that lie wholly on the hull in the render take it whole; the others show the dock where
# the render shows space.
ON_HULL = {"t2"}
FILL_FROM = {"t1": "near"}


def load(p, size=None):
    im = Image.open(p).convert("RGB")
    if size and im.size != size:
        im = im.resize(size, Image.LANCZOS)
    return np.asarray(im).astype(np.float64)


def box_mask(size, feather):
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    for b in BOXES.values():
        d.rectangle(b, fill=255)
    if feather:
        m = m.filter(ImageFilter.GaussianBlur(feather))
    return np.asarray(m).astype(np.float64) / 255.0


def guide(master_path, render_path, out):
    m = load(master_path)
    h, w, _ = m.shape
    r = load(render_path, (w, h))
    g = m.copy()
    for name, (x0, y0, x1, y1) in BOXES.items():
        mm, rr = m[y0:y1, x0:x1], r[y0:y1, x0:x1]
        lum = rr.mean(2)
        hull = np.ones_like(lum, bool) if name in ON_HULL else lum > SPACE
        # Bring the render's low-key hull up to the page's exposure: one gain per channel.
        src = rr[hull]
        dst = mm[(mm.mean(2) > np.percentile(mm.mean(2), 30))]
        rr = np.clip(rr * (dst.mean(0) / (src.mean(0) + 1e-6)), 0, 255)
        # Where the render is space, the page shows what lies behind: copy it from beside the box
        # (FILL_FROM). Turret 1 has a gantry a box-height above it, which the first try copied
        # down and the generation kept as a second gantry; it takes only the 48 rows of rock just
        # above the box, mirrored down.
        hgt = y1 - y0
        if FILL_FROM.get(name, "above") == "near":
            strip = m[y0 - 48:y0, x0:x1]
            above = np.vstack([strip, strip[::-1]] * (hgt // 96 + 1))[:hgt]
        else:
            above = m[max(0, y0 - hgt):y0, x0:x1]
            above = np.vstack([above] * (hgt // max(1, above.shape[0]) + 1))[:hgt]
        a = Image.fromarray((hull * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
        a = (np.asarray(a).astype(np.float64) / 255.0)[..., None]
        g[y0:y1, x0:x1] = rr * a + above * (1 - a)
    soft = box_mask((w, h), 3)[..., None]
    hard = box_mask((w, h), 0)[..., None]
    soft = soft * hard
    out_im = m * (1 - soft) + g * soft
    Image.fromarray(np.clip(np.rint(out_im), 0, 255).astype(np.uint8)).save(out)
    print("guide", out)


def best_offset(master, gen, mask):
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
    h, w, _ = m.shape
    g = load(gen_path, (w, h))
    hard = box_mask((w, h), 0)
    inner = Image.fromarray((hard * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(9))
    mask = np.asarray(inner.filter(ImageFilter.GaussianBlur(3))).astype(np.float64) / 255.0 * hard
    e, dx, dy = best_offset(m, g, np.asarray(Image.fromarray((hard * 255).astype(np.uint8))
                                               .filter(ImageFilter.GaussianBlur(12))).astype(np.float64) / 255.0)
    g = np.roll(np.roll(g, dy, 0), dx, 1)
    c = np.clip(np.rint(m * (1 - mask[..., None]) + g * mask[..., None]), 0, 255).astype(np.uint8)
    Image.fromarray(c).save(out)
    diff = np.abs(c.astype(int) - m.astype(int)).max(2)
    print("offset dx=%d dy=%d ring-error=%.2f; max change outside edit boxes: %d; changed px: %d"
          % (dx, dy, e, diff[hard < 0.5].max(), int((diff > 0).sum())))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["guide", "compose", "mask"])
    ap.add_argument("--master", required=True)
    ap.add_argument("--render")
    ap.add_argument("--gen")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    if a.mode == "guide":
        guide(a.master, a.render, a.out)
    elif a.mode == "compose":
        compose(a.master, a.gen, a.out)
    else:
        m = load(a.master)
        Image.fromarray((box_mask((m.shape[1], m.shape[0]), 0) * 255).astype(np.uint8)).save(a.out)


if __name__ == "__main__":
    sys.exit(main())
