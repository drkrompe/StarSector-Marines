"""Cut the approved forest still into C3's depth bands and map what must be filled.

python c3_bands.py

Inputs (read-only): realism-lookdev/landscape-realism-v1.png (the approved master) and
the three transparent extractions forest-reveal-realism made from it
(far/mid/near-realism-v1.png), whose content already continues *behind* the nearer
bands. Writes, all in master pixel coordinates (1672x941) unless noted:

  guides/c3-band-<name>.png      8-bit band mask (the band as the master shows it)
  guides/c3-bands-overlay.png    master with the band map tinted over it
  guides/c3-inpaint-<name>.png   master dimmed; green = behind a nearer band but
                                 continued by the salvaged extraction; magenta = needed
                                 and nothing exists (Codex fill); cyan = needed beyond
                                 the master's canvas (extension)
  guides/c3-bands.json           registration offsets, band rates, fill areas

"Needed" means: during C3's 12 s slide and lean, this band pixel lands inside the
window aperture with no nearer band in front of it.
"""
import json

import numpy as np
from PIL import Image, ImageFilter

from c3_core import (BASE, CIN, DURATION, LAYERS, LEAN_M, MASTER, PLATE_SCALE, PLATE_X0, PLATE_Y0,
                     H, W, rise_px, truck_px, lean_scale, CABIN_DEPTH_M)

G = BASE / "guides"
EXTRACT = CIN / "forest-reveal-realism" / "assets"
BAND_DISTANCE = {n: d for n, d, _ in LAYERS if n in ("far", "mid", "near")}
BAND_DISTANCE["mountains"] = dict((n, d) for n, d, _ in LAYERS)["horizon"]
ORDER = ["mountains", "far", "mid", "near"]                       # back to front


def load(path, mode):
    return np.asarray(Image.open(path).convert(mode), np.float32) / 255.0


def gray(rgb):
    return rgb @ np.array([0.299, 0.587, 0.114], np.float32)


def grad(img):
    gy, gx = np.gradient(img)
    return np.hypot(gx, gy)


def register(master_rgb, layer_rgba, region, search=24):
    """Integer (dx, dy) that best lays the extraction over the master inside `region`
    (where the master shows this band unoccluded). Normalised gradient correlation."""
    m = grad(gray(master_rgb))
    e = grad(gray(layer_rgba[..., :3]))
    best, best_score = (0, 0), -1.0
    ys, xs = np.nonzero(region[search:-search, search:-search])
    ys, xs = ys + search, xs + search
    if len(ys) > 60000:
        pick = np.random.default_rng(3).choice(len(ys), 60000, replace=False)
        ys, xs = ys[pick], xs[pick]
    mv = m[ys, xs] - m[ys, xs].mean()
    for dy in range(-search, search + 1, 2):
        for dx in range(-search, search + 1, 2):
            ev = e[ys - dy, xs - dx]
            ev = ev - ev.mean()
            score = float((mv * ev).sum() / (np.linalg.norm(mv) * np.linalg.norm(ev) + 1e-9))
            if score > best_score:
                best, best_score = (dx, dy), score
    # refine to 1 px
    cx, cy = best
    for dy in range(cy - 1, cy + 2):
        for dx in range(cx - 1, cx + 2):
            ev = e[ys - dy, xs - dx]
            ev = ev - ev.mean()
            score = float((mv * ev).sum() / (np.linalg.norm(mv) * np.linalg.norm(ev) + 1e-9))
            if score > best_score:
                best, best_score = (dx, dy), score
    return best, best_score


def shift(a, dx, dy):
    out = np.zeros_like(a)
    h, w = a.shape[:2]
    ys0, ys1 = max(0, dy), min(h, h + dy)
    xs0, xs1 = max(0, dx), min(w, w + dx)
    out[ys0:ys1, xs0:xs1] = a[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx]
    return out


def aperture():
    """Window opening in frame pixels from the C3 cabin card (alpha < 0.5 is glass)."""
    cab = load(G / "c3-cabin-projected-p09.png", "RGBA")
    return cab[..., 3] < 0.5


def main():
    master = load(MASTER, "RGB")
    mh, mw = master.shape[:2]
    lay = {n: load(EXTRACT / ("%s-realism-v1.png" % n), "RGBA") for n in ("far", "mid", "near")}

    # 1. register each extraction against the master where the master shows it unoccluded
    rough = {n: lay[n][..., 3] > 0.5 for n in lay}
    regions = {
        "near": rough["near"],
        "mid": rough["mid"] & ~rough["near"],
        "far": rough["far"] & ~rough["mid"] & ~rough["near"],
    }
    offsets = {}
    for n in ("near", "mid", "far"):
        (dx, dy), score = register(master, lay[n], regions[n] & (master.std(-1) >= 0))
        offsets[n] = {"dx": dx, "dy": dy, "score": round(score, 3)}
        lay[n] = shift(lay[n], dx, dy)

    a_near = lay["near"][..., 3]
    a_mid = lay["mid"][..., 3]
    a_far = lay["far"][..., 3]

    # 2. visible band labels in the master (front-most band wins)
    land = a_far > 0.5
    near = a_near > 0.5
    mid = (a_mid > 0.5) & ~near
    far_all = land & ~mid & ~near
    # Horizon mountains: the blue, low-contrast range sitting on the skyline. Blue
    # exceeds green there, while every wooded ridge in front is green-dominant.
    r, g, b = master[..., 0], master[..., 1], master[..., 2]
    # Smooth the per-pixel test, then take, column by column, the contiguous blue run
    # that starts at the skyline: the range is the first thing under the sky, and the
    # first green-dominant row below it is the nearest wooded ridge's crest. The run is
    # capped at 45 px and its foot smoothed along x, so it reads as one range.
    blue = np.asarray(Image.fromarray(((b - g) * 4 + 0.5).clip(0, 1).__mul__(255).astype(np.uint8))
                      .filter(ImageFilter.GaussianBlur(3)), np.float32) / 255 > 0.5
    skyline = np.argmax(land, axis=0)
    foot = np.zeros(mw, np.float32)
    for x in range(mw):
        y = skyline[x]
        while y < min(mh, skyline[x] + 45) and blue[y, x]:
            y += 1
        foot[x] = y
    k = np.ones(31, np.float32) / 31
    foot = np.convolve(np.pad(foot, 15, mode="edge"), k, mode="valid")
    rows = np.arange(mh)[:, None]
    mount = far_all & (rows < foot[None, :])
    far = far_all & ~mount
    sky = ~land & ~mid & ~near
    bands = {"sky": sky, "mountains": mount, "far": far, "mid": mid, "near": near}
    for n, m in bands.items():
        Image.fromarray((m * 255).astype(np.uint8)).save(G / ("c3-band-%s.png" % n))

    # mist inside the land bands: bright, desaturated wisps
    sat = master.max(-1) - master.min(-1)
    # neutral white; the river is as bright but reflects sky, so its blue leads its red
    mist = land & (gray(master) > 0.62) & (sat < 0.16) & (master[..., 2] - master[..., 0] < 0.05)
    mist = np.asarray(Image.fromarray((mist * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2))) > 60
    Image.fromarray((mist * 255).astype(np.uint8)).save(G / "c3-band-mist.png")

    tint = {"sky": (90, 150, 255), "mountains": (150, 110, 255), "far": (255, 200, 60),
            "mid": (60, 220, 120), "near": (255, 80, 60)}
    over = master.copy()
    for n, m in bands.items():
        over[m] = over[m] * 0.55 + np.array(tint[n], np.float32) / 255 * 0.45
    over[mist] = over[mist] * 0.5 + 0.5
    Image.fromarray((over * 255).astype(np.uint8)).save(G / "c3-bands-overlay.png")

    # 3. what each band must supply during the shot. Work in frame space at half
    #    resolution, sampling the shot every 0.25 s; the cabin lean grows the aperture.
    ap_full = aperture()
    step = 2
    fy, fx = np.mgrid[0:H:step, 0:W:step].astype(np.float32)
    times = np.linspace(0, DURATION, int(DURATION * 4) + 1)
    # per band, accumulate needed master pixels (may lie outside the canvas: pad)
    pad = 400
    need = {n: np.zeros((mh + 2 * pad, mw + 2 * pad), bool) for n in ORDER}
    masks = {"mountains": mount, "far": far, "mid": mid, "near": near}
    # A band is responsible for a pixel where the master shows it, or where the master
    # shows a nearer band (so the band continues behind it). Its completed layer is
    # opaque over that whole region, so whatever it is responsible for, it covers.
    in_front = {"mountains": far | mid | near, "far": mid | near, "mid": near, "near": np.zeros_like(near)}
    resp_map = {n: masks[n] | in_front[n] for n in ORDER}
    for t in times:
        u = t / DURATION
        s_cab = lean_scale(t, CABIN_DEPTH_M)
        # aperture at time t: the cabin card scaled about the principal point
        ax = ((fx - W / 2) / s_cab + W / 2).astype(int).clip(0, W - 1)
        ay = ((fy - H / 2) / s_cab + H / 2).astype(int).clip(0, H - 1)
        ap = ap_full[ay, ax]
        covered = np.zeros_like(ap)
        for n in reversed(ORDER):                      # front to back
            d = BAND_DISTANCE[n]
            ox = PLATE_X0 - truck_px(d) * u
            oy = PLATE_Y0 - rise_px(d) * u
            mx = np.floor((fx - ox) / PLATE_SCALE).astype(int)
            my = np.floor((fy - oy) / PLATE_SCALE).astype(int)
            want = ap & ~covered
            # beyond the canvas, responsibility follows the nearest canvas pixel
            cx, cy = mx.clip(0, mw - 1), my.clip(0, mh - 1)
            resp = want & resp_map[n][cy, cx]
            ok = (mx > -pad) & (mx < mw + pad) & (my > -pad) & (my < mh + pad)
            sel = resp & ok
            need[n][my[sel] + pad, mx[sel] + pad] = True
            covered |= resp
    stats = {}
    for n in ORDER:
        # frame samples every 2 px land up to 2 master px apart; close the gaps
        nd = np.asarray(Image.fromarray(need[n].astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(3))) > 0
        core = nd[pad:pad + mh, pad:pad + mw]
        shown = masks[n]
        # needed where the master shows something nearer; a 2 px rim along the band's own
        # silhouette is the edge feather, not a fill, and is left out
        rim = np.asarray(Image.fromarray(shown.astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(5))) > 0
        behind = core & ~rim
        if n == "mountains":
            # The far extraction is the range and the ridges flattened together: nothing
            # of the range survives behind a ridge crest, so all of it is a hole.
            have = np.zeros_like(mount)
        elif n == "far":
            have = a_far > 0.5
        elif n == "mid":
            have = a_mid > 0.5
        else:
            have = a_near > 0.5
        salvaged = behind & have
        hole = behind & ~have
        ext = nd.copy()
        ext[pad:pad + mh, pad:pad + mw] = False
        stats[n] = {
            "distance_m": BAND_DISTANCE[n],
            "truck_px_over_shot": round(truck_px(BAND_DISTANCE[n]), 1),
            "rise_px_over_shot": round(rise_px(BAND_DISTANCE[n]), 1),
            "needed_master_px": int(core.sum()),
            "behind_nearer_px": int(behind.sum()),
            "continued_by_salvage_px": int(salvaged.sum()),
            "hole_px": int(hole.sum()),
            "beyond_canvas_px": int(ext.sum()),
            "beyond_canvas_bbox": list(map(int, bbox(ext, pad))) if ext.any() else None,
        }
        vis = np.zeros((mh + 2 * pad, mw + 2 * pad, 3), np.float32) + 0.08
        vis[pad:pad + mh, pad:pad + mw] = master * 0.35
        vis[pad:pad + mh, pad:pad + mw][core & shown] = master[core & shown] * 0.9
        v = vis[pad:pad + mh, pad:pad + mw]
        v[salvaged] = v[salvaged] * 0.3 + np.array([0.1, 0.9, 0.2]) * 0.7
        v[hole] = np.array([1.0, 0.0, 1.0])
        vis[ext] = np.array([0.0, 0.9, 1.0])
        Image.fromarray((vis.clip(0, 1) * 255).astype(np.uint8)).save(G / ("c3-inpaint-%s.png" % n))

    report = {
        "master": "../realism-lookdev/landscape-realism-v1.png",
        "extractions": "../forest-reveal-realism/assets/{far,mid,near}-realism-v1.png",
        "registration_px": offsets,
        "band_area_px": {n: int(m.sum()) for n, m in bands.items()},
        "mist_px": int(mist.sum()),
        "plate": {"scale": PLATE_SCALE, "x0": PLATE_X0, "y0": round(PLATE_Y0, 1)},
        "lean_m": LEAN_M,
        "bands": stats,
        "inpaint_legend": "green: needed behind a nearer band, continued by the salvaged extraction; "
                          "magenta: needed, nothing exists (Codex fill); cyan: needed beyond the master canvas "
                          "(pad 400 px each side shown)",
    }
    (G / "c3-bands.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


def bbox(mask, pad):
    ys, xs = np.nonzero(mask)
    return xs.min() - pad, ys.min() - pad, xs.max() - pad, ys.max() - pad


if __name__ == "__main__":
    main()
