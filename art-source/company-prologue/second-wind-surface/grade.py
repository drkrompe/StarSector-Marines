"""Check, de-light and grade every registered part tile to the shared palette.

  python grade.py [--group g[,g]]  -> textures/<part>.png, grading/<part>.json, grading/summary-<group>.json

Per part, in guide tile pixel space (the texture space the UVs address):
1. Colour-layout check. Every pixel is classified to the nearest palette class
   (CIELAB); each material region the guide declares (guide/<part>-class.png)
   reports the share that came back as its own class. A region below
   SWAP_SHARE is a colour-layout swap (the spike's grey turret painted orange):
   it is flagged, and the grade below forces it back to its declared class.
2. De-light. Pixels are grouped into planes by their flat world normal
   (build/<group>/tiles/<part>-normal.png). On each plane a least-squares linear ramp
   of L is fitted (robustly, with one intercept per declared material class on
   the plane, so a two-material plane's colour split is not taken for light)
   and removed: the top-light
   gradient Codex bakes in. Recorded: the ramp's span before. What a linear
   ramp cannot remove is measured and left: the bright/dark band Codex paints
   along plate outlines (edge_band_dL), and plate-to-plate brightness steps.
3. Grade. Per declared material region: L is matched to the palette mean with
   its spread scaled toward the palette's (clamped, so the texture's own detail
   survives); a and b are shifted to the palette mean with a clamped chroma
   spread. Stats are taken 2 px inside the outline.
4. Pad. Colour is pushed 24 px out past the outline so bevels and
   antialiased edges sampling just outside it never pick up background grey.
   The fill starts EDGE_TRIM px inside the outline: Codex draws a light
   chipped-edge line round every shape, and padding from the outline itself
   smeared that line onto each bevel strip as a white stripe.
"""
from pathlib import Path
import json
import sys
import numpy as np
from PIL import Image, ImageFilter
import imgops as I
import parts as P
import hull as H

HERE = Path(__file__).resolve().parent
DATA = H.DIR  # the hull's folder: its tables, art and outputs (hull.py)
GROUPS = P.group_arg(sys.argv)
(DATA / 'textures').mkdir(exist_ok=True)
(DATA / 'grading').mkdir(exist_ok=True)
PAL = json.loads((DATA / 'palette.json').read_text())['classes']
CLASSES = H.CLASSES
SWAP_SHARE = 0.6
EDGE_TRIM = 3  # px of the outline refilled from inside (see 4. Pad)
# A swap is a change of paint family (grey painted orange), not a lighter or
# darker grey: gunmetal and dark recess are one neutral family for the check,
# and the L drift between them is reported as tone instead.
FAMILY = H.FAMILY
pal_mean = np.array([PAL[c]['mean'] for c in CLASSES])


def classify(lab):
    # Chroma weighs double: oxide vs gunmetal vs cream is mostly a hue question.
    w = np.array([1.0, 2.0, 2.0])
    d = (((lab[..., None, :] - pal_mean) * w) ** 2).sum(-1)
    return d.argmin(-1)


def plane_labels(normal_rgb, mask):
    q = np.round(normal_rgb / 16).astype(int)
    key = q[..., 0] * 10000 + q[..., 1] * 100 + q[..., 2]
    key[~mask] = -1
    return key


def pad(rgb, mask, px=24):
    out, filled = rgb.copy(), mask.copy()
    for _ in range(px // 3):
        acc = np.asarray(Image.fromarray((filled * 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(2)), np.float32) / 255
        col = np.stack([np.asarray(Image.fromarray(np.clip(out[..., k] * filled, 0, 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(2)), np.float32)
                        for k in range(3)], -1)
        grow = (~filled) & (acc > 0.05)
        out[grow] = col[grow] / acc[grow, None]
        filled = filled | grow
    out[~filled] = out[filled].mean(0)
    return out


summary = {}
for sheet in P.sheets_in(GROUPS):
    for part in P.SHEETS[sheet]:
        TILES = P.tiles_dir(part)
        reg = I.load_rgb(TILES / f'{part}-reg.png')
        mask = I.load_rgb(DATA / 'guide' / f'{part}-mask.png')[..., 0] > 127
        inner = I.erode(mask, 2)
        regions = {c: m & mask for c, m in I.class_masks(I.load_rgb(DATA / 'guide' / f'{part}-class.png')).items()}
        lab = I.rgb_to_lab(reg)
        rep = {'part': part, 'regions': {}, 'planes': []}

        # 1. colour-layout check
        cls = classify(lab)
        for c in CLASSES:
            r = regions[c] & inner
            if r.sum() < 200:
                continue
            got = np.bincount(cls[r], minlength=len(CLASSES)) / r.sum()
            family = sum(v for k, v in enumerate(got) if FAMILY[CLASSES[k]] == FAMILY[c])
            rep['regions'][c] = {'pixels': int(r.sum()), 'share_as_declared': round(float(got[CLASSES.index(c)]), 3),
                                 'share_in_declared_family': round(float(family), 3),
                                 'came_back_as': {CLASSES[k]: round(float(v), 3) for k, v in enumerate(got) if v >= 0.02},
                                 'swap': bool(family < SWAP_SHARE),
                                 'before_mean_lab': lab[r].mean(0).round(2).tolist()}

        # 2. de-light: remove a robust linear L ramp per plane
        planes = plane_labels(I.load_rgb(TILES / f'{part}-normal.png'), mask)
        H, W = mask.shape
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        for key in np.unique(planes[mask]):
            region = planes == key
            core = region & inner
            if core.sum() < 1500:
                continue
            # One light ramp per plane, but one intercept per material class on
            # it: a plane holding cream and oxide must not read their colour
            # split as a top-to-bottom light gradient (the bow rake did, +18 L).
            onehot = [regions[c][core] for c in regions if regions[c][core].sum() >= 200]
            onehot = onehot or [np.ones(core.sum(), bool)]
            X = np.stack([*[o.astype(np.float64) for o in onehot], xx[core] - xx[core].mean(), yy[core] - yy[core].mean()], 1)
            L = lab[..., 0][core]
            keep = np.ones(len(L), bool)
            for _ in range(3):
                coef, *_ = np.linalg.lstsq(X[keep], L[keep], rcond=None)
                res = L - X @ coef
                keep = np.abs(res) < 2.5 * res[keep].std() + 1e-6
            ramp = coef[-2] * (xx - xx[core].mean()) + coef[-1] * (yy - yy[core].mean())
            span = float(ramp[core].max() - ramp[core].min())
            lab[..., 0][region] -= ramp[region]
            band = region & ~I.erode(region, 4) & mask & I.erode(mask, 1)
            mid = I.erode(region, 12)
            edge_dl = float(lab[..., 0][band].mean() - lab[..., 0][mid].mean()) if band.sum() and mid.sum() > 200 else None
            rep['planes'].append({'pixels': int(region.sum()), 'ramp_span_L_removed': round(span, 2),
                                  'ramp_dL_per_100px': [round(float(coef[-2] * 100), 2), round(float(coef[-1] * 100), 2)],
                                  'edge_band_dL_after': None if edge_dl is None else round(edge_dl, 2)})

        # 3. grade per declared material region
        out = lab.copy()
        for c, r in regions.items():
            if c not in PAL or r.sum() < 50:
                continue
            s = r & inner if (r & inner).sum() > 200 else r
            mu, sd = lab[s].mean(0), lab[s].std(0) + 1e-3
            tm, ts = np.array(PAL[c]['mean']), np.array(PAL[c]['std'])
            k = np.clip(ts / sd, [0.75, 0.6, 0.6], [1.33, 1.5, 1.5])
            out[r] = (lab[r] - mu) * k + tm
            if np.hypot(tm[1], tm[2]) > 8:
                # A chromatic paint holds bare-metal chips: a shift would push
                # those neutral pixels past grey into blue. Scale chroma about
                # neutral instead, so a chip stays a chip.
                k[1:] = np.clip(tm[1:] / np.where(np.abs(mu[1:]) > 1, mu[1:], 1), 0.4, 2.5)
                o = out[r]
                o[:, 1:] = lab[r][:, 1:] * k[1:]
                out[r] = o
            if c in rep['regions']:
                rep['regions'][c].update({'after_mean_lab': out[s].mean(0).round(2).tolist(),
                                          'gain_L_a_b': k.round(3).tolist(),
                                          'dE_to_palette_before': round(float(np.linalg.norm(lab[s].mean(0) - tm)), 2),
                                          'forced_back_to_declared': rep['regions'][c]['swap']})
        rgb = I.lab_to_rgb(out)
        rgb[~mask] = reg[~mask]
        I.save_rgb(pad(rgb, I.erode(mask, EDGE_TRIM)), DATA / 'textures' / f'{part}.png')
        I.save_rgb(np.concatenate([reg, rgb], 1), TILES / f'{part}-graded-compare.png')
        spans = [p['ramp_span_L_removed'] for p in rep['planes']]
        edges = [p['edge_band_dL_after'] for p in rep['planes'] if p['edge_band_dL_after'] is not None]
        rep['ramp_span_L_removed_max'] = max(spans) if spans else None
        rep['edge_band_dL_after_mean'] = round(float(np.mean(edges)), 2) if edges else None
        (DATA / 'grading' / f'{part}.json').write_text(json.dumps(rep, indent=1) + '\n')
        summary[part] = {c: {k: v for k, v in r.items() if k in ('share_as_declared', 'share_in_declared_family', 'swap', 'before_mean_lab', 'after_mean_lab', 'dE_to_palette_before')}
                         for c, r in rep['regions'].items()} | {'ramp_span_L_removed_max': rep['ramp_span_L_removed_max'],
                                                                 'edge_band_dL_after_mean': rep['edge_band_dL_after_mean']}
        print(part, {c: (r['share_in_declared_family'], r['share_as_declared'], r['dE_to_palette_before']) for c, r in rep['regions'].items()},
              'ramp', rep['ramp_span_L_removed_max'], 'edge', rep['edge_band_dL_after_mean'])

# Tone spread across generations, per class: before and after grading.
spread = {}
for c in CLASSES:
    before = [v[c]['before_mean_lab'] for v in summary.values() if c in v]
    after = [v[c]['after_mean_lab'] for v in summary.values() if c in v]
    if len(before) < 2:
        continue
    def worst(ms):
        ms = np.array(ms)
        return round(float(max(np.linalg.norm(a - b) for a in ms for b in ms)), 2)
    spread[c] = {'parts': len(before), 'max_pair_dE_before': worst(before), 'max_pair_dE_after': worst(after),
                 'L_range_before': [round(min(b[0] for b in before), 1), round(max(b[0] for b in before), 1)],
                 'L_range_after': [round(min(a[0] for a in after), 1), round(max(a[0] for a in after), 1)]}
print(json.dumps(spread, indent=1))
for g in sorted({P.PART_GROUP[p] for p in summary}):
    rows = {p: v for p, v in summary.items() if P.PART_GROUP[p] == g}
    (DATA / 'grading' / f'summary-{g}.json').write_text(json.dumps({'parts': rows}, indent=1) + '\n')
if GROUPS is None:
    (DATA / 'grading' / 'tone-spread.json').write_text(json.dumps(spread, indent=1) + '\n')
