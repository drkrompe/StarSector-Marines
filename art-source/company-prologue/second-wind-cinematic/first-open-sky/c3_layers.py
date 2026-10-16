"""Bake C3's production layers to build/layers/ for compose_c3.py.

python c3_layers.py

Run c3_bands.py (masks, registration) and c3_plates.py assemble (the de-misted land at
2x master and the mist matte) first. Writes RGBA PNGs plus layers.json giving each
layer's scale in frame px per layer px:

  range, far, mid, near   the land bands, from the de-misted plate where the master
                          shows the band, and from the registered salvage extraction
                          where it continues behind a nearer band
  mist-high, mist-low     the approved mist as translucent cards (difference matte),
                          split by the band each lies over
  atlas                   the realism Atlas card at its true size, hazed for 2.2 km
  cabin                   the painted cabin at C3's camera, head band from v2

The range (band `mountains`) has no paint behind the far crests; the parallax exposes
up to ~16 master px of it (c3-inpaint-mountains.png). It is filled procedurally rather
than by a generation: below the range's foot each column mirrors the range's own lower
slope about the foot and fades it into the range's haze colour. At 40 km the range is
about 70% sky colour and almost featureless, so a mirrored slope is indistinguishable
from paint, and the slivers are never more than a few frame pixels tall.
"""
import json

import numpy as np
from PIL import Image

from c3_core import BASE, CIN, F_PX, H, PLATE_SCALE, W
from c3_plates import MIST_WHITE, PLATE, arr, band, blur, img, shift

G = BASE / "guides"
P = BASE / "build" / "plates"
OUT = BASE / "build" / "layers"
EXTRACT = CIN / "forest-reveal-realism" / "assets"
ATLAS_LENGTH_M, ATLAS_DISTANCE_M = 300.0, 2200.0
HAZE = np.array([0.70, 0.80, 0.90], np.float32)


def up(a, size, resample=Image.BILINEAR):
    """Resize a float array (H, W[, C]) to `size` (w, h)."""
    if a.ndim == 2:
        return np.asarray(Image.fromarray(a.astype(np.float32), "F").resize(size, resample), np.float32)
    return np.dstack([up(a[..., c], size, resample) for c in range(a.shape[2])])


def save(name, rgba, scale, meta):
    img(rgba).save(OUT / (name + ".png"))
    meta[name] = {"scale": scale, "size": [rgba.shape[1], rgba.shape[0]]}
    print("layer", name, rgba.shape[1], "x", rgba.shape[0], "scale %.4f" % scale)


def bands(meta):
    reg = json.loads((G / "c3-bands.json").read_text(encoding="utf-8"))["registration_px"]
    demist = arr(Image.open(P / "land-demist.png").convert("RGB"))
    PH, PW = demist.shape[:2]
    mh, mw = round(PH / PLATE), round(PW / PLATE)
    size = (PW, PH)
    mask = {n: up(band(n).astype(np.float32), size) > 0.5 for n in ("mountains", "far", "mid", "near")}
    ex = {}
    for n in ("far", "mid", "near"):
        e = arr(Image.open(EXTRACT / ("%s-realism-v1.png" % n)).convert("RGBA"))
        e = shift(e, reg[n]["dx"], reg[n]["dy"])
        ex[n] = np.clip(up(e, size, Image.BICUBIC), 0, 1)
    s = PLATE_SCALE / PLATE

    # --- the range, with its slivers filled
    m1 = band("mountains")
    foot1 = np.where(m1.any(0), mh - 1 - np.argmax(m1[::-1], axis=0), 0).astype(np.float32)
    top1 = np.where(m1.any(0), np.argmax(m1, axis=0), 0).astype(np.float32)
    foot = up(foot1[None, :], (PW, 1))[0] * PLATE
    top = up(top1[None, :], (PW, 1))[0] * PLATE
    rows = np.arange(PH, dtype=np.float32)[:, None]
    depth = rows - foot[None, :]                                   # below the foot (plate px)
    span = np.maximum(foot - top, 4)[None, :]
    src_y = np.clip(foot[None, :] - np.minimum(depth, span - 1), 0, PH - 1).astype(int)
    cols = np.broadcast_to(np.arange(PW)[None, :], (PH, PW))
    mirrored = demist[src_y, cols]
    haze = blur(np.where(mask["mountains"][..., None], demist, 0), 12) / np.maximum(
        blur(mask["mountains"].astype(np.float32), 12), 1e-3)[..., None]
    fade = np.clip(depth / (16 * PLATE), 0, 1)[..., None]
    fill = mirrored * (1 - fade * 0.6) + haze * (fade * 0.6)
    FILL = 18 * PLATE                                              # deepest sliver ~16 master px
    below = (depth > 0) & (depth < FILL) & (foot[None, :] > 0)
    rgb = np.where(mask["mountains"][..., None], demist, fill)
    # The range's skyline pixels are blended with the master's own sky, which is paler
    # than the sky plate behind this layer; pull the top edge in by 1.5 master px so no
    # pale fringe rides along the skyline.
    rows_from_top = rows - top[None, :]
    a = (mask["mountains"] | below).astype(np.float32) * (rows_from_top > 1.5 * PLATE)
    a = blur(a, 1.0) * (1 - np.clip((depth - FILL + 6) / 6, 0, 1))
    save("range", np.dstack([rgb, a.clip(0, 1)]), s, meta)

    # --- far ridges: plate where shown, extraction behind; the range cut away
    rgb = np.where(mask["far"][..., None], demist, ex["far"][..., :3])
    soft_range = blur(mask["mountains"].astype(np.float32), PLATE)
    a = ex["far"][..., 3] * (1 - soft_range) * (rows > foot[None, :])
    save("far", np.dstack([rgb, a]), s, meta)
    for n in ("mid", "near"):
        # the near band's own haze was never painted out (c3_plates.mist_mask), so the
        # plate there is the master at plate resolution
        rgb = np.where(mask[n][..., None], demist, ex[n][..., :3])
        save(n, np.dstack([rgb, ex[n][..., 3]]), s, meta)


def mist(meta):
    a = arr(Image.open(P / "mist-alpha.png").convert("L"))
    hi = blur((band("far") | band("mountains")).astype(np.float32), 2)
    lo = blur(band("mid").astype(np.float32), 2)
    tot = np.maximum(hi + lo, 1e-3)
    col = np.ones(a.shape + (3,), np.float32) * MIST_WHITE
    save("mist-high", np.dstack([col, a * hi / tot]), PLATE_SCALE, meta)
    save("mist-low", np.dstack([col, a * lo / tot]), PLATE_SCALE, meta)


def atlas(meta):
    im = Image.open(BASE / "assets" / "c3-atlas-v1.png").convert("RGBA")
    a = np.asarray(im, np.float32) / 255
    # the completion's alpha is soft where it painted faint edge haze; harden it
    alpha = np.clip((a[..., 3] - 0.25) / 0.5, 0, 1)
    im = img(np.dstack([a[..., :3], alpha]))
    im = im.crop(im.getchannel("A").getbbox())
    length = F_PX * ATLAS_LENGTH_M / ATLAS_DISTANCE_M              # 189 px
    k = 3                                                          # baked at 3x, drawn at 1/3
    im = im.resize((round(length * k), round(length * k * im.height / im.width)), Image.LANCZOS)
    c = np.asarray(im, np.float32) / 255
    # 2.2 km of humid air: about 15% haze, more in the shade
    c[..., :3] = c[..., :3] * 0.85 + HAZE * 0.15
    save("atlas", c, 1.0 / k, meta)


def cabin(meta):
    """The painted cabin re-viewed at C3's camera (c3_cabin_render.py); above the
    projector's coverage (y < 213) the v2 head, which is registered to the geometry
    (c3_cabin_head.py --check: glass top at y = 76 against 77), so nothing is lifted."""
    cab = arr(Image.open(G / "c3-cabin-projected-p09.png").convert("RGBA"))
    un = arr(Image.open(G / "c3-cabin-unpainted-p09.png").convert("L"))
    seam = int(np.argmin(un[:, W // 2] > 0.5))
    top = arr(Image.open(BASE / "assets" / "c3-cabin-top-v2.png").convert("RGB").resize((W, H), Image.LANCZOS))
    yy = np.arange(H, dtype=np.float32)[:, None]
    w = ((seam + 4 - yy) / 8).clip(0, 1) * np.ones((1, W), np.float32)
    w = np.maximum(w, un)
    rgb = cab[..., :3] * (1 - w[..., None]) + top * w[..., None]
    save("cabin", np.dstack([rgb, cab[..., 3]]), 1.0, meta)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    meta = {}
    bands(meta)
    mist(meta)
    atlas(meta)
    cabin(meta)
    (OUT / "layers.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
