"""C3 production plates: the land redrawn at plate resolution, de-misted, in registered tiles.

python c3_plates.py prep        # guides/c3-tile-<i>-target.png, the Codex edit targets
python c3_plates.py check       # registration of each assets/c3-tile-<i>-v1.png
python c3_plates.py assemble    # build/plates/: land, de-misted land, mist mattes (2x master)

The approved master is 1672x941; the frame shows it at 1.2x. Every row the shot ever
shows lies between master rows 176 and 800 (c3_bands.py replays the slide and lean),
so the land is redrawn as four overlapping tiles of 917x344 master px, each returned by
Codex at 2048x768 (2.23x). The valley mist is painted flat grey into each edit target,
so the region to repaint as canopy is drawn rather than described (c3-layout.md: the
lesson of the cabin head v1). The far and mid bands' mist goes; the near band's own
haze stays baked, since no mist card rides that layer.

Assembly, per tile:
1. resize to its exact master rectangle at PLATE (2x master);
2. register it to the upscaled master by gradient correlation (integer shift), then
   measure residual local drift on a grid of blocks; a tile whose blocks drift by more
   than 2 master px is rejected (c3-layout.md);
3. colour-match it at low frequency to the master outside the mist, so all four tiles
   and the approved still agree in tone (high frequencies from Codex, broad light from
   the approved master);
4. feather-blend tiles over their overlaps.

Mist mattes are a difference matte (c3-layout.md): alpha = (master - demisted) /
(mist white - demisted), at master resolution, so the drifting mist is the approved
mist and not an invention.
"""
import argparse
import json
import sys

import numpy as np
from PIL import Image, ImageFilter

from c3_core import BASE, MASTER

G = BASE / "guides"
A = BASE / "assets"
OUT = BASE / "build" / "plates"
PLATE = 2.0                         # plate px per master px
TW, TH = 917, 344                   # tile in master px
TILES = [(0, 176), (755, 176), (0, 456), (755, 456)]       # top-left in master px
GEN = (2048, 768)                   # the edit target's size
MIST_WHITE = np.array([0.90, 0.93, 0.95], np.float32)
GREY = np.array([0.5, 0.5, 0.5], np.float32)


def arr(im):
    return np.asarray(im, np.float32) / 255.0


def img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def _box(a, k, axis):
    """Running mean of width 2k+1 along axis, edges clamped."""
    pad = [(0, 0)] * a.ndim
    pad[axis] = (k + 1, k)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis, dtype=np.float64)
    hi = np.take(c, np.arange(2 * k + 1, c.shape[axis]), axis=axis)
    lo = np.take(c, np.arange(0, c.shape[axis] - 2 * k - 1), axis=axis)
    return ((hi - lo) / (2 * k + 1)).astype(np.float32)


def blur(a, r):
    """Near-Gaussian blur of sigma r (three box passes per axis), any channel count."""
    k = max(1, int(round(np.sqrt(12 * r * r / 3 + 1) / 2 - 0.5)))
    out = a.astype(np.float32)
    for axis in (0, 1):
        for _ in range(3):
            out = _box(out, k, axis)
    return out


def master():
    return arr(Image.open(MASTER).convert("RGB"))


def band(name):
    return arr(Image.open(G / ("c3-band-%s.png" % name)).convert("L")) > 0.5


def mist_mask():
    """Mist to remove: the far and mid bands' (and the range's) valley mist, dilated so
    its soft fringe goes too."""
    m = arr(Image.open(G / "c3-band-mist.png").convert("L")) > 0.5
    m &= ~band("near")
    m = np.asarray(img(m.astype(np.float32)).filter(ImageFilter.MaxFilter(7))) > 127
    return m


def prep():
    mst = master()
    mist = mist_mask()
    target = mst.copy()
    target[mist] = GREY
    for i, (x, y) in enumerate(TILES):
        crop = img(target[y:y + TH, x:x + TW]).resize(GEN, Image.LANCZOS)
        crop.save(G / ("c3-tile-%d-target.png" % i))
        img(mst[y:y + TH, x:x + TW]).resize(GEN, Image.LANCZOS).save(G / ("c3-tile-%d-master.png" % i))
    img(mist.astype(np.float32)).save(G / "c3-mist-removed.png")
    print("wrote", len(TILES), "edit targets; mist removed over", int(mist.sum()), "master px")


def grad(g):
    gy, gx = np.gradient(g)
    return np.hypot(gx, gy)


def gray(rgb):
    return rgb @ np.array([0.299, 0.587, 0.114], np.float32)


def best_shift(ref, mov, valid, search):
    """Integer (dx, dy) of mov that best matches ref (gradient correlation) over valid."""
    r, m = grad(gray(ref)), grad(gray(mov))
    ys, xs = np.nonzero(valid[search:-search, search:-search])
    ys, xs = ys + search, xs + search
    if len(ys) > 40000:
        pick = np.random.default_rng(5).choice(len(ys), 40000, replace=False)
        ys, xs = ys[pick], xs[pick]
    rv = r[ys, xs] - r[ys, xs].mean()
    best, score = (0, 0), -2.0
    for dy in range(-search, search + 1):
        for dx in range(-search, search + 1):
            mv = m[ys - dy, xs - dx]
            mv = mv - mv.mean()
            s = float((rv * mv).sum() / (np.linalg.norm(rv) * np.linalg.norm(mv) + 1e-9))
            if s > score:
                best, score = (dx, dy), s
    return best, score


def shift(a, dx, dy):
    out = np.zeros_like(a)
    h, w = a.shape[:2]
    ys0, ys1 = max(0, dy), min(h, h + dy)
    xs0, xs1 = max(0, dx), min(w, w + dx)
    out[ys0:ys1, xs0:xs1] = a[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx]
    return out


def load_tile(i, version="v1"):
    """A completion resized to its master rectangle at PLATE, plus the master there."""
    p = A / ("c3-tile-%d-%s.png" % (i, version))
    size = (round(TW * PLATE), round(TH * PLATE))
    gen = arr(Image.open(p).convert("RGB").resize(size, Image.LANCZOS))
    x, y = TILES[i]
    ref = arr(img(master()[y:y + TH, x:x + TW]).resize(size, Image.LANCZOS))
    return gen, ref


def measure(i, version="v1"):
    """Global shift and block drift of tile i, in master px."""
    gen, ref = load_tile(i, version)
    mist = mist_mask()
    x, y = TILES[i]
    size = (gen.shape[1], gen.shape[0])
    keep = arr(img((~mist[y:y + TH, x:x + TW]).astype(np.float32)).resize(size, Image.NEAREST)) > 0.5
    # work at master resolution for the shifts (the 2 px rule is in master px)
    g1 = arr(img(gen).resize((TW, TH), Image.LANCZOS))
    r1 = arr(img(ref).resize((TW, TH), Image.LANCZOS))
    k1 = np.asarray(img(keep.astype(np.float32)).resize((TW, TH), Image.NEAREST)) > 127
    (dx, dy), score = best_shift(r1, g1, k1, 8)
    g1s = shift(g1, dx, dy)
    blocks = []
    B = 96
    for by in range(8, TH - B - 8, B // 2):
        for bx in range(8, TW - B - 8, B // 2):
            sl = (slice(by, by + B), slice(bx, bx + B))
            if k1[sl].mean() < 0.6:
                continue
            (ddx, ddy), s = best_shift(r1[sl], g1s[sl], k1[sl], 4)
            if s > 0.3:
                blocks.append((bx + B // 2, by + B // 2, ddx, ddy, round(s, 2)))
    drift = max((abs(b[2]) + abs(b[3]) for b in blocks), default=0)
    return {"tile": i, "dx": dx, "dy": dy, "score": round(score, 3), "blocks": len(blocks),
            "max_block_drift_px": drift, "block_list": blocks}


def check(version):
    rep = [measure(i, version) for i in range(len(TILES)) if (A / ("c3-tile-%d-%s.png" % (i, version))).exists()]
    for r in rep:
        bad = [b for b in r["block_list"] if abs(b[2]) + abs(b[3]) > 2]
        print("tile %d: shift (%d, %d) score %.3f; %d blocks, max drift %d px; over 2 px: %s"
              % (r["tile"], r["dx"], r["dy"], r["score"], r["blocks"], r["max_block_drift_px"], bad))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / ("registration-%s.json" % version)).write_text(json.dumps(rep, indent=1), encoding="utf-8")
    return rep


def assemble(version):
    """Full land at PLATE: `land` (as approved, mist and all, at plate resolution) and
    `demist` (the same without valley mist); plus the mist matte at master resolution."""
    mst = master()
    mh, mw = mst.shape[:2]
    PW, PH = round(mw * PLATE), round(mh * PLATE)
    up = arr(img(mst).resize((PW, PH), Image.LANCZOS))
    mist = mist_mask()
    mist_up = arr(img(mist.astype(np.float32)).resize((PW, PH), Image.BILINEAR)) > 0.02
    acc = np.zeros((PH, PW, 3), np.float32)
    wsum = np.zeros((PH, PW), np.float32)
    reg = json.loads((OUT / ("registration-%s.json" % version)).read_text(encoding="utf-8"))
    for r in reg:
        i = r["tile"]
        gen, _ = load_tile(i, version)
        gen = shift(gen, round(r["dx"] * PLATE), round(r["dy"] * PLATE))
        x, y = TILES[i]
        X, Y = round(x * PLATE), round(y * PLATE)
        h, w = gen.shape[:2]
        ref = up[Y:Y + h, X:X + w]
        valid = (~mist_up[Y:Y + h, X:X + w]) & (gen.sum(-1) > 0)
        # low-frequency colour match by normalised convolution outside the mist
        R = 24 * PLATE
        vw = blur(valid.astype(np.float32), R)
        diff = blur((ref - gen) * valid[..., None], R) / np.maximum(vw, 1e-3)[..., None]
        # inside the mist (no valid reference) fall back to the widely smoothed offset
        wide = blur((ref - gen) * valid[..., None], R * 4) / np.maximum(blur(valid.astype(np.float32), R * 4), 1e-3)[..., None]
        t = np.clip(vw * 3, 0, 1)[..., None]
        gen = gen + diff * t + wide * (1 - t)
        # feather: ramps over the overlaps, to zero at the tile edges (bar the plate's own edges)
        yy = np.arange(h, dtype=np.float32)[:, None]
        xx = np.arange(w, dtype=np.float32)[None, :]
        F = 70 * PLATE
        fx = np.minimum(np.clip(xx / F, 0, 1) if X > 0 else 1, np.clip((w - 1 - xx) / F, 0, 1) if X + w < PW else 1)
        fy = np.minimum(np.clip(yy / F, 0, 1) if y > TILES[0][1] else 1, np.clip((h - 1 - yy) / F, 0, 1))
        wt = (fx * fy).astype(np.float32) * (gen.sum(-1) > 0)
        acc[Y:Y + h, X:X + w] += gen * wt[..., None]
        wsum[Y:Y + h, X:X + w] += wt
    have = wsum > 1e-3
    demist = up.copy()
    demist[have] = acc[have] / wsum[have][..., None]
    # Outside the tiles (the sky above row 176 and rows the shot never shows) keep the
    # upscaled master, blended over 24 px.
    edge = blur(have.astype(np.float32), 12 * PLATE)[..., None]
    demist = up * (1 - edge) + demist * edge
    # `land` is the de-misted plate with the approved mist put back by the matte: the
    # still as approved, at plate resolution, wherever the mist cards are not drifting.
    dm1 = arr(img(demist).resize((mw, mh), Image.LANCZOS))
    # Measured over all far and mid land (and the range), not only the removed patches:
    # the completion also cleared the thin wisps between ridges, and those are part of
    # the approved mist. Both images are softened first so that detail Codex added does
    # not read as mist; small values are noise and are dropped.
    soft_m, soft_d = blur(mst, 1.5), blur(dm1, 1.5)
    num = (soft_m - soft_d).sum(-1)
    den = (MIST_WHITE - soft_d).sum(-1)
    alpha = np.clip(num / np.maximum(den, 1e-3), 0, 1)
    sat_m = soft_m.max(-1) - soft_m.min(-1)
    neutral = np.clip((0.22 - sat_m) / 0.08, 0, 1)            # mist is whiter than canopy
    land = (band("far") | band("mid") | band("mountains")) & ~band("near")
    region = blur(land.astype(np.float32), 2) * neutral
    alpha = np.clip((alpha * region - 0.06) / 0.94, 0, 1)
    alpha = blur(alpha, 1.0).clip(0, 1)
    OUT.mkdir(parents=True, exist_ok=True)
    img(demist).save(OUT / "land-demist.png")
    img(alpha).save(OUT / "mist-alpha.png")
    a_up = arr(img(alpha).resize((PW, PH), Image.BILINEAR))[..., None]
    img(demist * (1 - a_up) + MIST_WHITE * a_up).save(OUT / "land-misted.png")
    print("wrote", OUT, "plate", PW, "x", PH)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("step", choices=["prep", "check", "assemble"])
    ap.add_argument("--version", default="v1")
    a = ap.parse_args()
    if a.step == "prep":
        prep()
    elif a.step == "check":
        check(a.version)
    else:
        assemble(a.version)


if __name__ == "__main__":
    sys.exit(main())
