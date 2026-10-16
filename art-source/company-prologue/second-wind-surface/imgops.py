"""numpy/Pillow helpers shared by palette.py, register.py, grade.py and the review."""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import parts as P


def load_rgb(path, size=None):
    im = Image.open(path).convert('RGB')
    if size and im.size != tuple(size):
        im = im.resize(size, Image.LANCZOS)
    return np.asarray(im, np.float32)


def save_rgb(arr, path):
    Image.fromarray(np.clip(arr + .5, 0, 255).astype(np.uint8)).save(path)


# --- colour ------------------------------------------------------------------
_M = np.array([[0.4124564, 0.3575761, 0.1804375], [0.2126729, 0.7151522, 0.0721750], [0.0193339, 0.1191920, 0.9503041]])
_WHITE = np.array([0.95047, 1.0, 1.08883])


def rgb_to_lab(rgb):
    c = rgb / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    xyz = lin @ _M.T / _WHITE
    f = np.where(xyz > 216 / 24389, np.cbrt(xyz), (24389 / 27 * xyz + 16) / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)


def lab_to_rgb(lab):
    fy = (lab[..., 0] + 16) / 116
    fx, fz = fy + lab[..., 1] / 500, fy - lab[..., 2] / 200
    f = np.stack([fx, fy, fz], -1)
    xyz = np.where(f ** 3 > 216 / 24389, f ** 3, (116 * f - 16) / (24389 / 27)) * _WHITE
    lin = xyz @ np.linalg.inv(_M).T
    c = np.where(lin <= 0.0031308, 12.92 * lin, 1.055 * np.clip(lin, 0, None) ** (1 / 2.4) - 0.055)
    return np.clip(c * 255, 0, 255)


def class_masks(class_rgb):
    """{class: bool mask} from a flat CLASS_ID render (nearest id colour, black = empty)."""
    ids = np.array([P.CLASS_ID[c] for c in P.CLASS_ID], np.float32)
    d = np.linalg.norm(class_rgb[..., None, :] - ids[None, None], axis=-1)
    k = d.argmin(-1)
    filled = class_rgb.max(-1) > 60
    return {c: (k == i) & filled & (d.min(-1) < 60) for i, c in enumerate(P.CLASS_ID)}


def erode(mask, px):
    if px <= 0:
        return mask
    im = Image.fromarray(mask.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(2 * px + 1))
    return np.asarray(im) > 127


def dilate(mask, px):
    im = Image.fromarray(mask.astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(2 * px + 1))
    return np.asarray(im) > 127


# --- segmentation and registration (after the spike's measure_drift.py) -------
def segment(a, thr=22.0):
    H, W = a.shape[:2]
    border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    neutral = border[(border.max(1) - border.min(1) < 10) & (border.mean(1) > 160)]
    bg = np.median(neutral if len(neutral) > 50 else border, 0)
    near_bg = np.linalg.norm(a - bg, axis=2) < thr
    lab = Image.fromarray(np.where(near_bg, 128, 0).astype(np.uint8)).copy()
    # Flood the background from every border pixel that is background: a tile
    # can hold several pieces, and a hole open to the edge is still outside.
    for x in range(0, W, 8):
        for y in (0, H - 1):
            if lab.getpixel((x, y)) == 128:
                ImageDraw.floodfill(lab, (x, y), 255)
    for y in range(0, H, 8):
        for x in (0, W - 1):
            if lab.getpixel((x, y)) == 128:
                ImageDraw.floodfill(lab, (x, y), 255)
    # Enclosed background (a bay opening inside the part) is background too:
    # any near-background region larger than a speck.
    for y in range(4, H, 12):
        for x in range(4, W, 12):
            if lab.getpixel((x, y)) == 128:
                before = np.asarray(lab).copy()
                ImageDraw.floodfill(lab, (x, y), 200)
                region = (np.asarray(lab) == 200) & (before == 128)
                if region.sum() > 1500:
                    ImageDraw.floodfill(lab, (x, y), 255)
    hull = np.asarray(lab) != 255
    m = Image.fromarray(hull.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))
    return np.asarray(m) > 127, bg


def bounds(m):
    ys, xs = np.nonzero(m)
    return np.array([xs.min(), ys.min(), xs.max() + 1, ys.max() + 1], float)


def iou(a, b):
    return float((a & b).sum() / max((a | b).sum(), 1))


def warp_mask(m, T):
    H, W = m.shape
    sx, sy, tx, ty = T
    xs = np.arange(W) * sx + tx
    ys = np.arange(H) * sy + ty
    xi = np.clip(np.round(xs).astype(int), 0, W - 1)
    yi = np.clip(np.round(ys).astype(int), 0, H - 1)
    return m[yi][:, xi] & ((ys >= 0) & (ys < H))[:, None] & ((xs >= 0) & (xs < W))[None, :]


def warp_rgb(a, T, fill):
    H, W = a.shape[:2]
    im = Image.fromarray(np.clip(a + .5, 0, 255).astype(np.uint8))
    sx, sy, tx, ty = T
    return np.asarray(im.transform((W, H), Image.AFFINE, (sx, 0, tx, 0, sy, ty), Image.BICUBIC,
                                   fillcolor=tuple(int(v) for v in fill)), np.float32)


def boundary_distance(a, b, step=1):
    def edge(m):
        ys, xs = np.nonzero(m & ~erode(m, 1))
        return np.stack([xs, ys], 1).astype(np.float32)[::step]
    pa, pb = edge(a), edge(b)
    if not len(pa) or not len(pb):
        return {'mean_px': float('nan'), 'p95_px': float('nan'), 'max_px': float('nan')}

    def nearest(p, q):
        out = np.empty(len(p), np.float32)
        for i in range(0, len(p), 512):
            out[i:i + 512] = np.sqrt(((p[i:i + 512, None] - q[None]) ** 2).sum(2).min(1))
        return out
    d = np.concatenate([nearest(pa, pb), nearest(pb, pa)])
    return {'mean_px': float(d.mean()), 'p95_px': float(np.percentile(d, 95)), 'max_px': float(d.max())}


def fit(guide_mask, gen_mask):
    """Fits mapping guide pixels to generated pixels; returns {name: T}, best name."""
    gb, nb = bounds(guide_mask), bounds(gen_mask)
    fits = {'identity': (1.0, 1.0, 0.0, 0.0)}
    sx = (nb[2] - nb[0]) / (gb[2] - gb[0])
    sy = (nb[3] - nb[1]) / (gb[3] - gb[1])
    fits['bounds_affine'] = (sx, sy, nb[0] - sx * gb[0], nb[1] - sy * gb[1])

    def refine(T0):
        T, steps = list(T0), [0.004, 0.004, 2.0, 2.0]
        best = iou(guide_mask, warp_mask(gen_mask, T))
        for _ in range(6):
            improved = False
            for k in range(4):
                for sign in (1, -1):
                    while True:
                        trial = list(T)
                        trial[k] += sign * steps[k]
                        v = iou(guide_mask, warp_mask(gen_mask, trial))
                        if v > best + 1e-5:
                            T, best, improved = trial, v, True
                        else:
                            break
            if not improved:
                steps = [x / 2 for x in steps]
        return tuple(T)
    fits['iou_refined_affine'] = refine(fits['bounds_affine'])
    scores = {k: iou(guide_mask, warp_mask(gen_mask, T)) for k, T in fits.items()}
    return fits, scores, max(scores, key=scores.get)


def interior_drift(guide_rgb, reg_rgb, mask, P_=96, maxshift=24):
    """Patch phase correlation of gradient magnitude; flat guides yield few confident patches."""
    def grad(a):
        g = np.asarray(Image.fromarray(np.clip(a.mean(-1), 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5)), np.float32)
        gx = np.zeros_like(g); gy = np.zeros_like(g)
        gx[:, 1:-1] = g[:, 2:] - g[:, :-2]; gy[1:-1] = g[2:] - g[:-2]
        return np.hypot(gx, gy)
    ga, gb = grad(guide_rgb), grad(reg_rgb)
    H, W = mask.shape
    win = np.outer(np.hanning(P_), np.hanning(P_))
    out = []
    for y in range(0, H - P_ + 1, P_ // 2):
        for x in range(0, W - P_ + 1, P_ // 2):
            m = mask[y:y + P_, x:x + P_]
            if m.mean() < 0.3 or m.mean() > 0.97:  # edge-bearing patches: outline features
                continue
            a = ga[y:y + P_, x:x + P_]; b = gb[y:y + P_, x:x + P_]
            if a.std() < 2 or b.std() < 2:
                continue
            A = np.fft.fft2((a - a.mean()) * win); B = np.fft.fft2((b - b.mean()) * win)
            R = A * np.conj(B); R /= np.abs(R) + 1e-6
            r = np.fft.fftshift(np.real(np.fft.ifft2(R)))
            c = P_ // 2
            sub = r[c - maxshift:c + maxshift + 1, c - maxshift:c + maxshift + 1]
            iy, ix = np.unravel_index(sub.argmax(), sub.shape)
            if sub.max() >= 0.08:
                out.append(float(np.hypot(ix - maxshift, iy - maxshift)))
    return out
