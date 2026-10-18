"""Class swatches: the floor every unclaimed face renders with.

  python swatches.py guide   -> guide/sheet-swatch-guide.png
  python swatches.py [grade] [--sheet generated/sheet-swatch.png]
                             -> textures/swatch-<class>.png, grading/swatches.json

The guide is one 1920x1080 sheet on parts.BACKGROUND with four square plates,
one per tile box (parts.tile_box), each the largest centred square that fits
its tile with SQUARE_MARGIN px clear, flat-filled with the palette mean of
cream, oxide, gunmetal and dark in reading order. Codex paints each square as
a flat sample of that hull material (prompts/sheet-swatch.prompt.txt).

Grading, per square (pure numpy/Pillow; never imports bpy):
1. Crop. The square is located by its known guide position scaled to the
   generated sheet, then tightened to the painted pixels (rows and columns
   that differ from the background), and cut CROP_INSET px inside that, so the
   chipped outline Codex draws round every shape is dropped.
2. De-light. One robust linear L ramp (3 rounds of 2.5 sigma rejection) is
   fitted over the whole square and removed about its mean, as grade.py does
   per plane.
3. Grade. L matched to the class palette mean with its spread scaled toward
   the palette's, clamped 0.75-1.33x; a and b on gunmetal and dark (and pale)
   shifted to the palette with the spread clamped 0.6-1.5x; on cream and
   oxide chroma is scaled about neutral (clamped 0.4-2.5x) so bare-metal chips
   stay neutral. On the neutral classes chroma past CHROMA_KNEE from the
   palette mean is halved (rust flecks). 'pale' is the gunmetal square regraded to the pale class.
4. Tile. apply_skin samples swatches with extension MIRROR, which is
   continuous in value at every image edge by construction but shows a
   kaleidoscope fold where a feature meets the edge. Instead each square is
   made truly periodic by an offset cross-fade (the square rolled by half and
   blended into itself across a band at each wrap, then the wrap seam moved
   back to the middle); periodic content is also seamless under MIRROR,
   because the mirrored copy's edge texel equals its neighbour's. Measured:
   the edge-to-opposite-edge step against the interior's own neighbour step.
   The square is resized to 1024x1024 (Lanczos) before this step, so the wrap
   is made at the texel pitch Blender samples. Written to
   textures/swatch-<class>.png.
"""
from pathlib import Path
import json
import sys
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import imgops as I  # noqa: E402
import parts as P  # noqa: E402
import hull as H  # noqa: E402
DATA = H.DIR  # the hull's folder: its tables, art and outputs (hull.py)

ORDER = H.SWATCH_ORDER
SQUARE_MARGIN = 20      # px clear round each guide square inside its tile
CROP_INSET = 0.04       # share of the square cut away at each side (outline chipping)
SIZE = 1024
CHROMA_KNEE = 10.0      # neutral classes: chroma from the palette mean past this is halved
FADE = 0.18             # cross-fade band, share of the square, at each wrap
PAL = json.loads((DATA / 'palette.json').read_text())['classes']


def tile_box(k):
    """Up to four squares sit in the part sheets' 2x2 tiles; more take a 3x2 grid."""
    if len(ORDER) <= 4:
        return P.tile_box(k)
    w, h = (P.SHEET[0] - 4 * P.GX) // 3, (P.SHEET[1] - 3 * P.GY) // 2
    x, y = P.GX + (k % 3) * (w + P.GX), P.GY + (k // 3) * (h + P.GY)
    return x, y, x + w, y + h


def square_box(k):
    x0, y0, x1, y1 = tile_box(k)
    side = min(x1 - x0, y1 - y0) - 2 * SQUARE_MARGIN
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    return cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side


def make_guide():
    a = np.zeros((P.SHEET[1], P.SHEET[0], 3), np.float32) + np.array(P.BACKGROUND, np.float32)
    for k, c in enumerate(ORDER):
        x0, y0, x1, y1 = square_box(k)
        a[y0:y1, x0:x1] = I.lab_to_rgb(np.array(PAL[c]['mean'], np.float32)[None, None])[0, 0]
    out = DATA / 'guide' / 'sheet-swatch-guide.png'
    I.save_rgb(a, out)
    print('guide ->', out, [square_box(k) for k in range(len(ORDER))])


def crop_square(sheet, k):
    H, W = sheet.shape[:2]
    sx, sy = W / P.SHEET[0], H / P.SHEET[1]
    x0, y0, x1, y1 = square_box(k)
    gx0, gy0, gx1, gy1 = x0 * sx, y0 * sy, x1 * sx, y1 * sy
    # Search a window a little larger than the expected square for painted pixels.
    pad = 0.08 * (gx1 - gx0)
    wx0, wy0 = int(max(0, gx0 - pad)), int(max(0, gy0 - pad))
    wx1, wy1 = int(min(W, gx1 + pad)), int(min(H, gy1 + pad))
    win = sheet[wy0:wy1, wx0:wx1]
    bg = np.median(np.concatenate([win[:3].reshape(-1, 3), win[-3:].reshape(-1, 3),
                                   win[:, :3].reshape(-1, 3), win[:, -3:].reshape(-1, 3)]), 0)
    painted = np.linalg.norm(win - bg, axis=-1) > 18
    rows = np.nonzero(painted.mean(1) > 0.5)[0]
    cols = np.nonzero(painted.mean(0) > 0.5)[0]
    bx0, bx1, by0, by1 = cols.min(), cols.max() + 1, rows.min(), rows.max() + 1
    found = [wx0 + bx0, wy0 + by0, wx0 + bx1, wy0 + by1]
    side = min(bx1 - bx0, by1 - by0)
    inset = int(round(CROP_INSET * side))
    cx, cy = (bx0 + bx1) // 2, (by0 + by1) // 2
    s = side - 2 * inset
    sq = win[cy - s // 2:cy - s // 2 + s, cx - s // 2:cx - s // 2 + s]
    return sq, {'expected_box': [round(v, 1) for v in (gx0, gy0, gx1, gy1)], 'found_box': [int(v) for v in found],
                'crop_px': int(s)}


def delight(lab):
    H, W = lab.shape[:2]
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    X = np.stack([np.ones(H * W), (xx - xx.mean()).ravel(), (yy - yy.mean()).ravel()], 1)
    L = lab[..., 0].ravel()
    keep = np.ones(len(L), bool)
    for _ in range(3):
        coef, *_ = np.linalg.lstsq(X[keep], L[keep], rcond=None)
        res = L - X @ coef
        keep = np.abs(res) < 2.5 * res[keep].std() + 1e-6
    ramp = (coef[1] * (xx - xx.mean()) + coef[2] * (yy - yy.mean()))
    out = lab.copy()
    out[..., 0] -= ramp
    return out, {'ramp_span_L_removed': round(float(ramp.max() - ramp.min()), 2),
                 'ramp_dL_per_100px': [round(float(coef[1] * 100), 2), round(float(coef[2] * 100), 2)]}


def grade(lab, cls):
    mu, sd = lab.reshape(-1, 3).mean(0), lab.reshape(-1, 3).std(0) + 1e-3
    tm, ts = np.array(PAL[cls]['mean']), np.array(PAL[cls]['std'])
    k = np.clip(ts / sd, [0.75, 0.6, 0.6], [1.33, 1.5, 1.5])
    out = (lab - mu) * k + tm
    if np.hypot(tm[1], tm[2]) > 8:  # cream, oxide: scale chroma about neutral (grade.py)
        k[1:] = np.clip(tm[1:] / np.where(np.abs(mu[1:]) > 1, mu[1:], 1), 0.4, 2.5)
        out[..., 1:] = lab[..., 1:] * k[1:]
    else:
        # Neutral classes: Codex's rust flecks come back redder than the part
        # skins' (gunmetal p99 chroma 19 against 15); halve chroma past a knee.
        d = out[..., 1:] - tm[1:]
        c = np.hypot(d[..., 0], d[..., 1])[..., None]
        out[..., 1:] = tm[1:] + d * np.where(c > CHROMA_KNEE, (CHROMA_KNEE + (c - CHROMA_KNEE) * 0.5) / np.maximum(c, 1e-6), 1)
    return out, k


def make_periodic(rgb):
    """Offset cross-fade: blend the square with itself rolled by half, the
    rolled copy weighted up only near its own wrap seams, so the result wraps."""
    n = rgb.shape[0]
    band = max(4, int(FADE * n))
    t = np.arange(n, dtype=np.float32)
    # distance from the image edge (where the wrap of the ORIGINAL lies)
    d = np.minimum(t, n - 1 - t)
    w1 = np.clip(d / band, 0, 1)  # 0 at the edges, 1 inside
    w1 = w1 * w1 * (3 - 2 * w1)
    wy, wx = w1[:, None], w1[None, :]
    w = wy * wx  # original's weight
    rolled = np.roll(np.roll(rgb, n // 2, 0), n // 2, 1)
    # The rolled copy's own seams sit in the middle, where w = 1, so they are hidden.
    out = rgb * w[..., None] + rolled * (1 - w[..., None])
    # Restore contrast lost where two uncorrelated textures average.
    mix_var = w ** 2 + (1 - w) ** 2
    mean = rgb.reshape(-1, 3).mean(0)
    out = mean + (out - mean) / np.sqrt(mix_var)[..., None]
    return out


def seam_ratio(rgb):
    """Wrap step (last col -> first col, last row -> first row) over the mean
    interior neighbour step: about 1 means the wrap is invisible."""
    g = rgb.mean(-1)
    inner = np.concatenate([np.abs(np.diff(g, axis=1)).ravel(), np.abs(np.diff(g, axis=0)).ravel()]).mean()
    wrap = np.concatenate([np.abs(g[:, 0] - g[:, -1]), np.abs(g[0] - g[-1])]).mean()
    return round(float(wrap / inner), 3)


def run(sheet_path):
    sheet = I.load_rgb(sheet_path)
    (DATA / 'textures').mkdir(exist_ok=True)
    (DATA / 'grading').mkdir(exist_ok=True)
    rep = {'sheet': str(Path(sheet_path).relative_to(DATA)).replace('\\', '/'), 'sheet_size': list(sheet.shape[1::-1]),
           'size': SIZE, 'tiling': f'offset cross-fade, band {FADE} of the square; periodic, so seamless under MIRROR too',
           'classes': {}}
    lab_squares = {}
    for k, c in enumerate(ORDER):
        sq, box = crop_square(sheet, k)
        lab_squares[c] = (I.rgb_to_lab(sq), box)
    jobs = [(c, c) for c in ORDER] + list(H.SWATCH_DERIVED)
    for cls, src in jobs:
        lab, box = lab_squares[src]
        before = lab.reshape(-1, 3).mean(0)
        flat, ramp = delight(lab)
        graded, k = grade(flat, cls)
        rgb = I.lab_to_rgb(graded)
        # Resize first, so the wrap is made at the texel pitch Blender samples.
        im = Image.fromarray(np.clip(rgb + .5, 0, 255).astype(np.uint8)).resize((SIZE, SIZE), Image.LANCZOS)
        big = np.asarray(im, np.float32)
        raw_seam = seam_ratio(big)
        out = np.clip(make_periodic(big), 0, 255)
        after = I.rgb_to_lab(out).reshape(-1, 3)
        I.save_rgb(out, DATA / 'textures' / f'swatch-{cls}.png')
        rep['classes'][cls] = {'source_square': src, **box,
                               'before_mean_lab': before.round(2).tolist(), 'before_std_lab': lab.reshape(-1, 3).std(0).round(2).tolist(),
                               **ramp, 'gain_L_a_b': k.round(3).tolist(),
                               'after_mean_lab': after.mean(0).round(2).tolist(), 'after_std_lab': after.std(0).round(2).tolist(),
                               'palette_mean_lab': PAL[cls]['mean'],
                               'dE_to_palette_before': round(float(np.linalg.norm(before - np.array(PAL[cls]['mean']))), 2),
                               'dE_to_palette_after': round(float(np.linalg.norm(after.mean(0) - np.array(PAL[cls]['mean']))), 2),
                               'wrap_step_over_interior_step': {'before_tiling': raw_seam, 'after': seam_ratio(out)}}
        print(cls, rep['classes'][cls]['before_mean_lab'], '->', rep['classes'][cls]['after_mean_lab'],
              'ramp', ramp['ramp_span_L_removed'], 'seam', raw_seam, '->', seam_ratio(out))
    (DATA / 'grading' / 'swatches.json').write_text(json.dumps(rep, indent=1) + '\n')


if __name__ == '__main__':
    args = sys.argv[1:]
    if args and args[0] == 'guide':
        make_guide()
    else:
        run(DATA / (args[args.index('--sheet') + 1] if '--sheet' in args else 'generated/sheet-swatch.png'))
