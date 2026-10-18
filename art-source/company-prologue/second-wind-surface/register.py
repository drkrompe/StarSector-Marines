"""Register every generated part tile to its guide by silhouette fit.

  python register.py [--group g[,g]]
      -> registration/<part>.json, registration/summary-<group>.json, build/<group>/tiles/<part>-reg.png

Splits each generated sheet into tiles (sheets.split), segments the part from
the background, and fits identity, a bounds affine and an IoU-refined
axis-aligned affine (the spike's method). The best fit warps the tile into
guide pixel space, which is the texture space the apply phase's UVs address.
Measured per part: raw and fitted IoU, scale and offset, boundary distance,
after the fit. The guides are flat faces, so the outline is the only feature
that can drift: the panel layout inside is Codex's own invention and has
nothing to register against (the spike's interior phase correlation has no
signal here and is not run).
"""
from pathlib import Path
import json
import sys
import numpy as np
import imgops as I
import parts as P
import sheets
import hull as H

HERE = Path(__file__).resolve().parent
DATA = H.DIR  # the hull's folder: its tables, art and outputs (hull.py)
OUT = DATA / 'registration'
OUT.mkdir(exist_ok=True)
GROUPS = P.group_arg(sys.argv)
NAMES = P.sheets_in(GROUPS)

for name in NAMES:
    sheets.split(name, DATA / 'generated' / f'sheet-{name}.png')

summary = {}
for name in NAMES:
    members = P.SHEETS[name]
    for part in members:
        TILES = P.tiles_dir(part)
        guide = I.load_rgb(DATA / 'guide' / f'{part}-guide.png')
        gmask = I.load_rgb(DATA / 'guide' / f'{part}-mask.png')[..., 0] > 127
        gen = I.load_rgb(TILES / f'{part}-gen.png')
        seg_check = I.iou(I.segment(guide)[0], gmask)
        nmask, bg = I.segment(gen)
        # Drop anything far outside the part (a neighbour tile's edge cut in at
        # the crop): drift here is a few pixels, so 40 px is generous.
        nmask &= I.dilate(gmask, 40)
        fits, scores, best = I.fit(gmask, nmask)
        T = fits[best]
        reg = I.warp_rgb(gen, T, bg)
        I.save_rgb(reg, TILES / f'{part}-reg.png')
        wm = I.warp_mask(nmask, T)
        raw = I.boundary_distance(gmask, nmask)
        after = I.boundary_distance(gmask, wm)
        rep = {
            'part': part, 'sheet': name, 'tile': members.index(part), 'tile_size': list(P.TILE),
            'segmentation_selfcheck_iou_on_guide': round(seg_check, 4),
            'raw_iou': round(scores['identity'], 4), 'best_fit': best, 'fitted_iou': round(scores[best], 4),
            'fits': {k: {'scale': [round(T_[0], 5), round(T_[1], 5)], 'offset_px': [round(T_[2], 2), round(T_[3], 2)],
                         'iou': round(scores[k], 4)} for k, T_ in fits.items()},
            'boundary_raw_px': {k: round(v, 2) for k, v in raw.items()},
            'boundary_fitted_px': {k: round(v, 2) for k, v in after.items()},
            'mapping': 'T maps guide tile pixels to generated tile pixels: gen = (sx*x + tx, sy*y + ty)',
        }
        (OUT / f'{part}.json').write_text(json.dumps(rep, indent=1) + '\n')
        summary[part] = {k: rep[k] for k in ('raw_iou', 'best_fit', 'fitted_iou')} | {
            'scale': rep['fits'][best]['scale'], 'boundary_p95_px': rep['boundary_fitted_px']['p95_px']}
        print(f"{part:26s} raw IoU {rep['raw_iou']:.3f} -> {best} {rep['fitted_iou']:.3f} scale {rep['fits'][best]['scale']} "
              f"boundary mean/p95 {after['mean_px']:.1f}/{after['p95_px']:.1f}px")
for g in sorted({P.PART_GROUP[p] for p in summary}):
    rows = {p: v for p, v in summary.items() if P.PART_GROUP[p] == g}
    (OUT / f'summary-{g}.json').write_text(json.dumps(rows, indent=1) + '\n')
