"""Compose the 1920x1080 review sheets (Pillow only).

Reads the generated images and the stills that measure_drift.py, project.py
and swatch_check.py write under build/.

  python compose_review.py

review-sheet.png          guide / generated / registered, then the one-view
                          projection from the guide angle, 30 and 60 degrees
review-sheet-scaling.png  second view, two-view blend, disagreement, swatches
Each panel is cropped to its content and fitted, never stretched.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
W, H, TITLE, LABEL = 1920, 1080, 56, 34
CELL_W, CELL_H = W // 3, (H - TITLE) // 2


def font(size, bold=False):
    for name in (('arialbd.ttf' if bold else 'arial.ttf'), 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def autocrop(img, pad=24, tol=18):
    a = np.asarray(img.convert('RGB'), np.float32)
    bg = np.median(np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]]), 0)
    ys, xs = np.nonzero(np.abs(a - bg).max(2) > tol)
    if len(xs) == 0:
        return img
    return img.crop((max(xs.min() - pad, 0), max(ys.min() - pad, 0),
                     min(xs.max() + pad, img.width), min(ys.max() + pad, img.height)))


def panel(path, label, crop=True):
    img = Image.open(HERE / path).convert('RGB')
    if crop:
        img = autocrop(img)
    box = (CELL_W - 12, CELL_H - LABEL - 8)
    img.thumbnail(box, Image.LANCZOS)
    cell = Image.new('RGB', (CELL_W, CELL_H), (28, 30, 33))
    cell.paste(img, ((CELL_W - img.width) // 2, LABEL + (box[1] - img.height) // 2 + 4))
    ImageDraw.Draw(cell).text((10, 7), label, fill=(232, 232, 228), font=font(19))
    return cell


def sheet(title, cells, out):
    s = Image.new('RGB', (W, H), (18, 19, 21))
    ImageDraw.Draw(s).text((14, 12), title, fill=(245, 240, 230), font=font(27, bold=True))
    for k, c in enumerate(cells):
        s.paste(c, ((k % 3) * CELL_W, TITLE + (k // 3) * CELL_H))
    d = ImageDraw.Draw(s)
    for k in range(1, 3):
        d.line([(k * CELL_W, TITLE), (k * CELL_W, H)], fill=(60, 62, 66), width=2)
    d.line([(0, TITLE + CELL_H), (W, TITLE + CELL_H)], fill=(60, 62, 66), width=2)
    s.save(HERE / out)
    print('wrote', out, s.size)


reg = json.loads((HERE / 'registration/reskin-v1.json').read_text())
coverage = lambda tag: {v: d['pixel_coverage_of_visible_hull'] for v, d in json.loads((HERE / f'review/{tag}-projection.json').read_text())['views'].items()}
one = coverage('one-view')
two = json.loads((HERE / 'review/two-view-projection.json').read_text())
fit = reg['fits']
pct = lambda v: f'{100 * v:.0f}%'

sheet('SECOND WIND - CODEX RESKIN -> MESH PROJECTION SPIKE (port-side guide, one generation)', [
    panel('guide/port-side-guide.png', '1  Guide: clean model, ortho port side, flat light'),
    panel('generated/reskin-v1.png', f"2  Codex reskin, raw (hull scaled x{reg['bounds_scale']['x']:.3f}, IoU {fit['identity']['iou']:.3f})"),
    panel('build/registration/reskin-v1-overlay-registered.png',
          f"3  Affine-registered, guide outline cyan: IoU {fit[reg['best_fit']]['iou']:.3f}, interior drift p90 "
          f"{reg['interior_drift_after_best_fit']['p90_px']:.0f}px"),
    panel('build/review/one-view-guide.png', f"4  Projected onto mesh, guide angle, new key light ({pct(one['guide'])} textured)"),
    panel('build/review/one-view-quarter30.png', f"5  Same, 30 deg toward bow (grey = unseen, {pct(one['quarter30'])} textured)"),
    panel('build/review/one-view-coverage60.png', f"6  60 deg port bow quarter, magenta = unseen ({pct(one['quarter60'])} textured)"),
], 'review-sheet.png')

dis = two['views']['guide']['pairs']['port-side vs quarter30']
sw = json.loads((HERE / 'review/swatches.json').read_text())
worst = max(max(v['wrap_seam_ratio_x'], v['wrap_seam_ratio_y']) for k, v in sw.items() if k != 'method')
cov2 = coverage('two-view')
sheet('SCALING TESTS - second projector at 30 deg, best-view blend, and tileable swatches instead', [
    panel('generated/reskin-quarter30.png', '7  Second Codex reskin at 30 deg (unscaled: IoU 0.995 raw)'),
    panel('build/review/two-view-quarter30.png', f"8  Two projectors blended, 30 deg ({pct(cov2['quarter30'])} textured)"),
    panel('build/review/two-view-quarter60.png', f"9  Two projectors blended, 60 deg ({pct(cov2['quarter60'])} textured)"),
    panel('build/review/two-view-disagreement-guide.png',
          f"10  Where both see it: colour disagreement (mean {dis['mean_abs_srgb_diff_0_255']:.0f}/255)"),
    panel('generated/swatches.png', '11  Codex tileable swatch sheet (one generation)', crop=False),
    panel('build/review/swatches-tiled.png', f'12  Each swatch repeated 2x2 (wrap seam up to {worst:.1f}x interior)', crop=False),
], 'review-sheet-scaling.png')

allp = json.loads((HERE / 'review/all-projection.json').read_text())['views']
twop = json.loads((HERE / 'review/two-view-projection.json').read_text())['views']
regs = {p: json.loads((HERE / f'registration/{p}.json').read_text()) for p in ('part-deck', 'part-turret', 'part-pod', 'part-bow')}
worst_scale = max(abs(v - 1) for r in regs.values() for v in r['bounds_scale'].values())
sheet('PART-BY-PART ARM - four parts rendered alone and square-on, one Codex generation', [
    panel('guide/parts-sheet-guide.png', '13  Part guides: deck, turret, engine pod, bow (rendered alone)', crop=False),
    panel('generated/parts-sheet.png', f'14  Codex parts sheet: no global scale (all within {100 * worst_scale:.1f}%); turret recoloured', crop=False),
    panel('build/review/two-view-high45.png', f"15  Two whole-ship views only, high 45 deg ({pct(twop['high45']['pixel_coverage_of_visible_hull'])} textured)"),
    panel('build/review/all-high45.png', f"16  Plus the four parts ({pct(allp['high45']['pixel_coverage_of_visible_hull'])}): deck and turret roof reached"),
    panel('build/review/all-quarter60.png', f"17  All six projectors, 60 deg ({pct(allp['quarter60']['pixel_coverage_of_visible_hull'])} textured)"),
    panel('build/review/all-seams-quarter30.png', '18  Hand-overs cyan, unseen magenta, 30 deg (all six)'),
], 'review-sheet-parts.png')
