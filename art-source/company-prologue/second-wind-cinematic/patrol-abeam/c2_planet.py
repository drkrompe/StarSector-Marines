"""Jangala below C2's window: the layout guide for the painted plate, and the plate in motion.

    python c2_planet.py            # writes guides/c2-planet-guide.png (+ .json)

Geometry is ray-traced through C2's cabin camera (c2_core.CABIN_PITCH_DEG) against a
6000 km sphere 200 km below: the horizon, the limb and every row's slant distance are
exact. The guide colours that geometry with C1's own maps (corvus-approach/assets
planet-albedo-detail-v1 and planet-clouds-detail-v1, the same 100 x 50 degree window
compose_blockout used) under C1's sun carried into Second Wind's frame, so the painted
plate keeps C1's coastlines, cloud masses and light. Those maps are about 18 texels per
degree, far too coarse for 200 km; the plate (assets/c2-planet-v<N>.png, ImageGen from
this guide) supplies the detail.

The plate covers frame x PLATE_BOX[0]..[2] and y [1]..[3]: the glass plus the aft drift's
overscan to the right. plate_layer(t) registers it and slides every row aft (right to
left) at that row's own ground rate, F * v_orbit / slant distance: about 6 px/s at the
horizon and 20 at the sill. The limb above the horizon holds still. This is the true
screen flow of a surface passing beneath a fixed camera, applied row by row.
"""
import json
import math
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c2_core as C  # noqa: E402

C1_ASSETS = BASE.parent / "corvus-approach" / "assets"
ALBEDO = C1_ASSETS / "planet-albedo-detail-v1.png"
CLOUDS = C1_ASSETS / "planet-clouds-detail-v1.png"
PLATE_BOX = (100, 0, 2140, 860)          # frame px: x0, y0, x1, y1 (2040 x 860)
GUIDE = BASE / "guides" / "c2-planet-guide.png"


def latest_plate():
    found = sorted((p for p in (BASE / "assets").glob("c2-planet-v*.png") if p.stem.rsplit("-v", 1)[1].isdigit()),
                   key=lambda p: int(p.stem.rsplit("-v", 1)[1]))
    return found[-1] if found else None


def trace(xs, ys):
    """Ray-trace frame pixel centres. Returns (hit, slant distance m, unit normal, ray dir)."""
    fwd, right, up = (np.array(v) for v in C.basis(C.CABIN_PITCH_DEG))
    a = (xs + 0.5 - C.W / 2) / C.F_PX
    b = (C.H / 2 - ys - 0.5) / C.F_PX
    d = fwd + a[..., None] * right + b[..., None] * up
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    R = C.PLANET_RADIUS_M
    centre = np.array([0.0, 0.0, -(R + C.ALTITUDE_M)])
    oc = -centre
    bq = d @ oc
    disc = bq * bq - (oc @ oc - R * R)
    hit = disc > 0
    tt = np.where(hit, -bq - np.sqrt(np.maximum(disc, 0)), np.inf)
    hit &= tt > 0
    n = (d * np.where(hit, tt, 0)[..., None] - centre) / R
    return hit, tt, n, d, bq, oc


def horizon_y(x=C.W / 2):
    ys = np.arange(0, C.H, 0.25)
    hit = trace(np.full_like(ys, x), ys)[0]
    return float(ys[np.argmax(hit)])


@lru_cache(maxsize=1)
def row_rates():
    """Aft screen speed (px/s) of the ground in each plate row, read on the frame's centre column."""
    y0, y1 = PLATE_BOX[1], PLATE_BOX[3]
    ys = np.arange(y0, y1, dtype=np.float64)
    hit, dist, *_ = trace(np.full_like(ys, C.W / 2), ys)
    hz = np.argmax(hit)
    rate = np.where(hit, C.F_PX * C.ORBITAL_SPEED / np.where(hit, dist, 1.0), 0.0)
    rate[:hz] = rate[hz]                   # the limb: a uniform shell, so its slide cannot show
    return rate


def _bilerp(img, u, v):
    u0, v0 = np.floor(u).astype(int), np.floor(v).astype(int)
    fu, fv = (u - u0)[..., None], (v - v0)[..., None]
    return ((img[v0, u0] * (1 - fu) + img[v0, u0 + 1] * fu) * (1 - fv)
            + (img[v0 + 1, u0] * (1 - fu) + img[v0 + 1, u0 + 1] * fu) * fv)


def guide():
    """Layout and light guide on the plate canvas (RGB)."""
    x0, y0, x1, y1 = PLATE_BOX
    ys, xs = np.mgrid[y0:y1, x0:x1].astype(np.float64)
    hit, dist, n, d, bq, oc = trace(xs, ys)
    lon = np.degrees(np.arctan2(n[..., 0], n[..., 2]))
    lat = np.degrees(np.arcsin(np.clip(n[..., 1], -1, 1)))
    alb = np.asarray(Image.open(ALBEDO).convert("RGB"), np.float64) / 255
    cld = np.asarray(Image.open(CLOUDS).convert("L"), np.float64) / 255
    mh, mw = alb.shape[:2]
    u = np.clip((lon + 50.0) / 100.0 * (mw - 1), 0, mw - 1.001)
    v = np.clip((25.0 - lat) / 50.0 * (mh - 1), 0, mh - 1.001)
    ground = _bilerp(alb, u, v) ** 1.25 * 0.9
    cloud = np.clip((_bilerp(cld[..., None], u, v)[..., 0] - 0.25) / 0.6, 0, 1)[..., None]
    surf = ground * (1 - cloud) + 0.95 * cloud
    lit = np.clip(n @ np.array(C.SUN), 0, 1)[..., None]
    rgb = surf * (0.02 + 1.0 * lit)
    graze = np.clip(1 + (d * n).sum(-1), 0, 1)[..., None]
    rgb = rgb * (1 - 0.6 * graze ** 3) + np.array([0.42, 0.6, 0.95]) * 0.6 * graze ** 3 * lit
    closest = np.sqrt(np.maximum(oc @ oc - bq * bq, 0)) - C.PLANET_RADIUS_M
    shell = np.exp(-np.maximum(closest, 0) / 18000.0) * (~hit) * (bq < 0)
    rgb = np.where(hit[..., None], rgb, 0) + shell[..., None] * np.array([0.3, 0.5, 1.0]) * 0.9
    return Image.fromarray((np.clip(rgb, 0, 1) ** (1 / 1.1) * 255).astype(np.uint8))


@lru_cache(maxsize=1)
def _plate():
    path = latest_plate()
    if path is None:
        img = guide()
    else:
        img = Image.open(path).convert("RGB").resize((PLATE_BOX[2] - PLATE_BOX[0], PLATE_BOX[3] - PLATE_BOX[1]),
                                                     Image.LANCZOS)
    arr = np.asarray(img, np.float32) / 255
    return arr


def plate_layer(t):
    """Jangala at C2 time t as a 1920x1080 float RGB frame (space black). Every row slides aft
    at its own ground rate, with linear interpolation for sub-pixel motion."""
    arr = _plate()
    ph, pw = arr.shape[:2]
    out = np.zeros((C.H, C.W, 3), np.float32)
    shift = row_rates() * t                          # px moved aft (leftward) by time t
    x0 = PLATE_BOX[0]
    cols = np.arange(C.W, dtype=np.float64)
    for r in range(ph):
        src = cols - x0 + shift[r]
        i0 = np.clip(np.floor(src).astype(int), 0, pw - 2)
        f = np.clip(src - i0, 0, 1)[:, None]
        out[PLATE_BOX[1] + r] = arr[r, i0] * (1 - f) + arr[r, i0 + 1] * f
    return out


def main():
    GUIDE.parent.mkdir(exist_ok=True)
    guide().save(GUIDE)
    info = {"plate_box_frame_px": PLATE_BOX, "pitch_deg": C.CABIN_PITCH_DEG, "horizon_y_centre": horizon_y(),
            "horizon_y_left": horizon_y(157), "horizon_y_right": horizon_y(1762),
            "rate_px_s_horizon": float(row_rates()[int(horizon_y()) + 1]),
            "rate_px_s_sill": float(row_rates()[707 - PLATE_BOX[1]]),
            "drift_overscan_px": float(row_rates().max() * (C.C2_LEN + C.ENTRY_LEN))}
    GUIDE.with_suffix(".json").write_text(json.dumps(info, indent=2), encoding="utf-8")
    print(json.dumps(info))


if __name__ == "__main__":
    main()
