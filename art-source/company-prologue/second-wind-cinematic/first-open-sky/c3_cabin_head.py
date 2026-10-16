"""Cabin head v2: the window rim drawn into the edit image, so Codex registers it.

python c3_cabin_head.py            # writes guides/c3-cabin-edit-target-v2.png
python c3_cabin_head.py --check    # measures assets/c3-cabin-top-v2.png against the aperture

The v1 completion (assets/c3-cabin-top-v1.png) was briefed with the head's position as
words and drew its glass edge 92 px too deep (c3-layout.md, "Cabin head v2"). Here the
head is drawn: the painted cabin's own sill rim, flipped about the glass edge and
widened to the aperture's top width, lands its glass edge exactly on y = 77; the side
jambs continue upward as their own paint mirrored about the y = 213 seam. The two meet
in a mitre at each upper corner. That is a correct-geometry, wrong-detail draft (a
flipped sill is lit from the wrong side, and a mirror repeats bolts); Codex repaints
its detail and light without moving its lines.
"""
import argparse
import sys

import numpy as np
from PIL import Image, ImageDraw

from c3_core import BASE, H, W

G = BASE / "guides"
GLASS = np.array([149, 168, 182], np.float32) / 255
SEAM = 213          # first projected row (c3-cabin-unpainted-p09.png)
HEAD = 77           # aperture top at the frame centre
SILL = 967          # aperture bottom at the frame centre


def load():
    cab = np.asarray(Image.open(G / "c3-cabin-projected-p09.png").convert("RGBA"), np.float32) / 255
    return cab


def aperture_edges(alpha):
    """Per row, the glass's left and right x (alpha < 0.5), fitted as straight jambs."""
    rows, lefts, rights = [], [], []
    for y in range(SEAM, SILL - 5):
        xs = np.nonzero(alpha[y] < 0.5)[0]
        if len(xs):
            rows.append(y), lefts.append(xs.min()), rights.append(xs.max())
    rows = np.array(rows, np.float32)
    lf, rf = np.polyfit(rows, lefts, 1), np.polyfit(rows, rights, 1)
    return lf, rf


def draft():
    cab = load()
    rgb, a = cab[..., :3], cab[..., 3]
    lf, rf = aperture_edges(a)
    xl = lambda y: np.polyval(lf, y)            # noqa: E731
    xr = lambda y: np.polyval(rf, y)            # noqa: E731
    yy, xx = np.mgrid[0:SEAM, 0:W].astype(np.float32)
    # 1. head: the sill rim flipped about the glass edge, x mapped by the aperture widths
    top_l, top_r, bot_l, bot_r = xl(HEAD), xr(HEAD), xl(SILL), xr(SILL)
    cx_top, cx_bot = (top_l + top_r) / 2, (bot_l + bot_r) / 2
    k = (bot_r - bot_l) / (top_r - top_l)
    sy = SILL + (HEAD - yy)
    sx = cx_bot + (xx - cx_top) * k
    head = rgb[np.clip(sy, 0, H - 1).astype(int), np.clip(sx, 0, W - 1).astype(int)]
    # The flipped sill is lit from above; the real head's underside is in shade.
    head = head * 0.62
    # 2. jambs: each continues upward as its own paint, mirrored about the seam, following
    # the jamb's lean (x measured from the glass edge at each row)
    my = 2 * SEAM - yy
    left_off = xx - xl(yy)
    right_off = xx - xr(yy)
    jx = np.where(xx < W / 2, xl(my) + left_off, xr(my) + right_off)
    jamb = rgb[np.clip(my, 0, H - 1).astype(int), np.clip(jx, 0, W - 1).astype(int)]
    # 3. mitre: above the head by more than outside the jamb -> head
    above = HEAD - yy
    outside = np.maximum(xl(yy) - xx, xx - xr(yy))
    w = np.clip((above - outside) / 12 + 0.5, 0, 1)[..., None]
    band = jamb * (1 - w) + head * w
    glass = (yy >= HEAD) & (xx > xl(yy)) & (xx < xr(yy))
    band[glass] = GLASS
    out = rgb.copy()
    out[:SEAM] = band
    out[a < 0.5] = GLASS
    return out, (lf, rf)


def check(path):
    """Measure a completion's glass opening against the geometry, row by row."""
    gen = np.asarray(Image.open(path).convert("RGB").resize((W, H), Image.LANCZOS), np.float32) / 255
    glass = np.abs(gen - GLASS).sum(-1) < 0.09
    col = glass[:, W // 2]
    top = int(np.argmax(col))
    print("glass top at centre: y=%d (geometry %d)" % (top, HEAD))
    cab = load()
    lf, rf = aperture_edges(cab[..., 3])
    for y in (90, 130, 180, 212, 260):
        xs = np.nonzero(glass[y])[0]
        if len(xs):
            print("row %d: glass %d..%d, geometry %.0f..%.0f" % (y, xs.min(), xs.max(), np.polyval(lf, y),
                                                                  np.polyval(rf, y)))
    below = np.abs(gen[SEAM + 4:] - cab[SEAM + 4:, :, :3]).mean()
    print("mean abs change below the seam: %.4f" % below)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", help="completion to measure")
    args = ap.parse_args()
    if args.check:
        check(args.check)
        return
    out, _ = draft()
    im = Image.fromarray((out.clip(0, 1) * 255).astype(np.uint8))
    im.save(G / "c3-cabin-edit-target-v2.png")
    # the same, with the line the completion must keep, for review only
    d = ImageDraw.Draw(im)
    d.line([(0, SEAM), (W, SEAM)], fill=(255, 0, 255), width=1)
    im.save(BASE / "review" / "c3-cabin-edit-target-v2-marked.png")
    print("wrote", G / "c3-cabin-edit-target-v2.png")


if __name__ == "__main__":
    sys.exit(main())
