"""Measure how far a generated reskin moved the hull, and fit a registration.

  python measure_drift.py generated/reskin-v1.png [--view port-side] [--tag reskin-v1]

Needs numpy and Pillow only. Works in guide pixel space (1920x1080): the
generated image is first resized to the guide canvas (same 16:9 aspect).

1. Segment the hull from its flat background (border colour + flood fill),
   sanity-checked by running the same segmenter on the guide itself.
2. Silhouette metrics against guide/<view>-mask.png: IoU, bounds offset and
   scale, boundary distance (mean / p95 / max).
3. Fits, each a map T from guide pixels to generated pixels:
     identity, moments similarity (centroid + sqrt area), bounds affine
     (independent x/y scale + offset), and an IoU-refined axis-aligned affine.
4. Interior drift after the best fit: patch-wise phase correlation of gradient
   magnitude between the guide and the registered image. The silhouette says
   nothing about whether a hatch or seam moved inside it; this does.

Writes registration/<tag>.json (consumed by project.py) and review images under
build/registration/.
"""
from pathlib import Path
import argparse
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
OUT = HERE / 'registration'
OUT.mkdir(exist_ok=True)
IMAGES = HERE / 'build' / 'registration'  # reproducible review images stay untracked
IMAGES.mkdir(parents=True, exist_ok=True)

ap = argparse.ArgumentParser()
ap.add_argument('image')
ap.add_argument('--tag')
ap.add_argument('--view', default='port-side')
args = ap.parse_args()
src = Path(args.image).resolve()
tag = args.tag or src.stem

guide_rgb = Image.open(HERE / 'guide' / f'{args.view}-guide.png').convert('RGB')
W, H = guide_rgb.size
guide_mask = np.asarray(Image.open(HERE / 'guide' / f'{args.view}-mask.png').convert('L')) > 127
gen_native = Image.open(src).convert('RGB')
gen_rgb = gen_native.resize((W, H), Image.LANCZOS)


def segment(img, thr=22.0):
    a = np.asarray(img, dtype=np.float32)
    border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    # The background is neutral light grey. A part tile's border can be mostly
    # hull, so take the median of neutral bright border pixels when there are
    # enough of them rather than of the whole border.
    neutral = border[(border.max(1) - border.min(1) < 10) & (border.mean(1) > 160)]
    bg = np.median(neutral if len(neutral) > 50 else border, 0)
    near_bg = np.linalg.norm(a - bg, axis=2) < thr
    lab = Image.fromarray(np.where(near_bg, 128, 0).astype(np.uint8)).copy()  # copy: floodfill on an array-backed image is a silent no-op
    for seed in [(0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)]:
        if lab.getpixel(seed) == 128:
            ImageDraw.floodfill(lab, seed, 255)
    hull = np.asarray(lab) != 255
    # Drop specks: a 5px opening via min/max filters.
    m = Image.fromarray(hull.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))
    return np.asarray(m) > 127, bg


def bounds(m):
    ys, xs = np.nonzero(m)
    return np.array([xs.min(), ys.min(), xs.max() + 1, ys.max() + 1], float)


def iou(a, b):
    return float((a & b).sum() / max((a | b).sum(), 1))


def boundary(m):
    er = np.asarray(Image.fromarray(m.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(3))) > 127
    ys, xs = np.nonzero(m & ~er)
    return np.stack([xs, ys], 1).astype(np.float32)


def boundary_distance(a, b, step=3):
    pa, pb = boundary(a)[::step], boundary(b)[::step]
    if len(pa) == 0 or len(pb) == 0:  # a fit that leaves no hull in frame
        return {'mean_px': float('nan'), 'p95_px': float('nan'), 'max_px': float('nan')}
    def nearest(p, q):
        out = np.empty(len(p), np.float32)
        for i in range(0, len(p), 512):
            d = ((p[i:i + 512, None, :] - q[None, :, :]) ** 2).sum(2)
            out[i:i + 512] = np.sqrt(d.min(1))
        return out
    d = np.concatenate([nearest(pa, pb), nearest(pb, pa)])
    return {'mean_px': float(d.mean()), 'p95_px': float(np.percentile(d, 95)), 'max_px': float(d.max())}


def warp_mask(m, T):
    """Sample m (generated space) at T(p) for every guide pixel p; nearest."""
    sx, sy, tx, ty = T
    xs = np.clip(np.round(np.arange(W) * sx + tx).astype(int), 0, W - 1)
    ys = np.clip(np.round(np.arange(H) * sy + ty).astype(int), 0, H - 1)
    inside_x = (np.arange(W) * sx + tx >= 0) & (np.arange(W) * sx + tx < W)
    inside_y = (np.arange(H) * sy + ty >= 0) & (np.arange(H) * sy + ty < H)
    return m[ys][:, xs] & inside_y[:, None] & inside_x[None, :]


def warp_rgb(img, T):
    sx, sy, tx, ty = T
    return img.transform((W, H), Image.AFFINE, (sx, 0, tx, 0, sy, ty), Image.BICUBIC, fillcolor=FILL)


# --- segmentation -----------------------------------------------------------
guide_seg, _ = segment(guide_rgb)
gen_mask, gen_bg = segment(gen_rgb)
FILL = tuple(int(round(v)) for v in gen_bg)
seg_check = iou(guide_seg, guide_mask)

gb, nb = bounds(guide_mask), bounds(gen_mask)
fits = {}
fits['identity'] = (1.0, 1.0, 0.0, 0.0)
# Moments similarity: uniform scale from area, translation from centroids.
def centroid(m):
    ys, xs = np.nonzero(m)
    return xs.mean(), ys.mean()
s = float(np.sqrt(gen_mask.sum() / guide_mask.sum()))
(gcx, gcy), (ncx, ncy) = centroid(guide_mask), centroid(gen_mask)
fits['moments_similarity'] = (s, s, ncx - s * gcx, ncy - s * gcy)
# Bounds affine.
sx = (nb[2] - nb[0]) / (gb[2] - gb[0]); sy = (nb[3] - nb[1]) / (gb[3] - gb[1])
fits['bounds_affine'] = (sx, sy, nb[0] - sx * gb[0], nb[1] - sy * gb[1])
# IoU-refined axis-aligned affine: coordinate descent from the bounds fit,
# done at quarter resolution then checked at full resolution.
def refine(T0):
    T = list(T0)
    steps = [0.004, 0.004, 2.0, 2.0]
    best = iou(guide_mask, warp_mask(gen_mask, T))
    for _ in range(6):
        improved = False
        for k in range(4):
            for sign in (1, -1):
                while True:
                    trial = list(T); trial[k] += sign * steps[k]
                    v = iou(guide_mask, warp_mask(gen_mask, trial))
                    if v > best + 1e-5:
                        T, best, improved = trial, v, True
                    else:
                        break
        if not improved:
            steps = [x / 2 for x in steps]
    return tuple(T)
fits['iou_refined_affine'] = refine(fits['bounds_affine'])

results = {}
for name, T in fits.items():
    wm = warp_mask(gen_mask, T)
    results[name] = {'T_guide_to_generated': {'scale': [T[0], T[1]], 'offset_px': [T[2], T[3]]},
                     'iou': iou(guide_mask, wm), **{'boundary_' + k: v for k, v in boundary_distance(guide_mask, wm).items()}}
best_name = max(results, key=lambda k: results[k]['iou'])
Tb = fits[best_name]
registered = warp_rgb(gen_rgb, Tb)
registered.save(IMAGES / f'{tag}-registered.png')


# --- interior drift: patch phase correlation on gradient magnitude ----------
def grad(img):
    g = np.asarray(img.convert('L').filter(ImageFilter.GaussianBlur(1.5)), np.float32)
    gx = np.zeros_like(g); gy = np.zeros_like(g)
    gx[:, 1:-1] = g[:, 2:] - g[:, :-2]; gy[1:-1] = g[2:] - g[:-2]
    return np.hypot(gx, gy)

def interior(img, mask_overlap, P=128, maxshift=40):
    ga, gb2 = grad(guide_rgb), grad(img)
    x0, y0, x1, y1 = bounds(guide_mask).astype(int)
    win = np.outer(np.hanning(P), np.hanning(P))
    patches = []
    for y in range(y0, y1 - P + 1, P // 2):
        for x in range(x0, x1 - P + 1, P // 2):
            if mask_overlap[y:y + P, x:x + P].mean() < 0.9:
                continue
            a = ga[y:y + P, x:x + P]; b = gb2[y:y + P, x:x + P]
            if a.std() < 2 or b.std() < 2:
                continue
            A = np.fft.fft2((a - a.mean()) * win); B = np.fft.fft2((b - b.mean()) * win)
            R = A * np.conj(B); R /= np.abs(R) + 1e-6
            r = np.fft.fftshift(np.real(np.fft.ifft2(R)))
            c = P // 2
            sub = r[c - maxshift:c + maxshift + 1, c - maxshift:c + maxshift + 1]
            iy, ix = np.unravel_index(sub.argmax(), sub.shape)
            peak = float(sub.max()); noise = float(np.median(np.abs(sub)) + 1e-6)
            # Shift of the generated feature relative to the guide feature.
            dx, dy = -(ix - maxshift), -(iy - maxshift)
            patches.append({'x': x + c, 'y': y + c, 'dx': int(dx), 'dy': int(dy), 'peak': peak, 'peak_over_median': peak / noise})
    return patches

overlap = guide_mask & warp_mask(gen_mask, Tb)
patches = interior(registered, overlap)
confident = [p for p in patches if p['peak'] >= 0.08]
mag = np.array([np.hypot(p['dx'], p['dy']) for p in confident]) if confident else np.zeros(1)
patches_identity = interior(gen_rgb, guide_mask & gen_mask)
conf_id = [p for p in patches_identity if p['peak'] >= 0.08]
mag_id = np.array([np.hypot(p['dx'], p['dy']) for p in conf_id]) if conf_id else np.zeros(1)

def summary(m, n_all, n_conf):
    return {'patches': n_all, 'confident_patches': n_conf, 'median_px': float(np.median(m)),
            'p90_px': float(np.percentile(m, 90)), 'max_px': float(m.max()),
            'share_within_4px': float((m <= 4).mean()), 'share_over_12px': float((m > 12).mean())}

report = {
    'source': src.relative_to(HERE).as_posix() if src.is_relative_to(HERE) else str(src),
    'view': args.view, 'native_size': list(gen_native.size), 'guide_size': [W, H],
    'segmentation_selfcheck_iou_on_guide': seg_check,
    'generated_background_rgb': [float(v) for v in gen_bg],
    'guide_bounds_px': gb.tolist(), 'generated_bounds_px': nb.tolist(),
    'bounds_offset_px': {'left': nb[0] - gb[0], 'top': nb[1] - gb[1], 'right': nb[2] - gb[2], 'bottom': nb[3] - gb[3]},
    'bounds_scale': {'x': sx, 'y': sy},
    'fits': results, 'best_fit': best_name,
    'interior_drift_after_best_fit': summary(mag, len(patches), len(confident)),
    'interior_drift_without_fit': summary(mag_id, len(patches_identity), len(conf_id)),
    'interior_method': '128px patches at 64px stride, >=90% hull, phase correlation of blurred gradient magnitude, +-40px search, peak>=0.08 counted as confident',
    'uv_mapping': 'For normalized guide UV (u,v) with v up: u_g = sx*u + tx/W ; v_g = 1 - (sy*(1-v) + ty/H)',
    'patches_after_best_fit': confident,
}
(OUT / f'{tag}.json').write_text(json.dumps(report, indent=2) + '\n')

# Review: guide silhouette outline over generated, before and after the fit,
# with interior drift arrows (scaled x3) after the fit.
def outline_overlay(img, mask_other, arrows=None):
    base = img.copy().convert('RGB')
    ed = Image.fromarray(((mask_other & ~(np.asarray(Image.fromarray(mask_other.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(5))) > 127)) * 255).astype(np.uint8))
    base.paste((0, 230, 255), mask=ed)
    if arrows:
        d = ImageDraw.Draw(base)
        for p in arrows:
            col = (60, 255, 80) if np.hypot(p['dx'], p['dy']) <= 4 else (255, 210, 0) if np.hypot(p['dx'], p['dy']) <= 12 else (255, 40, 200)
            d.line([(p['x'], p['y']), (p['x'] + 3 * p['dx'], p['y'] + 3 * p['dy'])], fill=col, width=3)
            d.ellipse([p['x'] - 4, p['y'] - 4, p['x'] + 4, p['y'] + 4], fill=col)
    return base
outline_overlay(gen_rgb, guide_mask).save(IMAGES / f'{tag}-overlay-raw.png')
outline_overlay(registered, guide_mask, confident).save(IMAGES / f'{tag}-overlay-registered.png')
print(json.dumps({k: report[k] for k in ('segmentation_selfcheck_iou_on_guide', 'bounds_offset_px', 'bounds_scale', 'best_fit', 'interior_drift_after_best_fit', 'interior_drift_without_fit')}, indent=1))
for k, v in results.items():
    print(k, {kk: (round(vv, 4) if isinstance(vv, float) else vv) for kk, vv in v.items()})
