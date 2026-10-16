"""Coverage and seam measurements, and the 1920x1080 review sheets.

  python compose_review.py [--patched build/review-patched]
      -> review/*.png, review/review.json

Reads review.py's renders under build/review/ (and, with --patched, the same
renders of the skinned patched build). Per view:
- coverage: share of the ship's pixels (beauty differs from the flat world
  background) that render a part skin, a class swatch, or neither (lamps,
  glazing, scar and repair materials). Skin + swatch is the textured share;
  skin alone is the share carrying generated part art.
- seams: wherever two different parts meet on screen (part-id pass), the
  albedo is compared ACROSS the boundary, sampling 2 px either side so the
  antialiased boundary pixel itself is skipped. Two figures per part pair:
    step   mean per-pixel CIELAB dE across the boundary, against the mean dE
           between same-part pixels the same 4 px apart (the texture's own
           variation): a seam that merely reads as another plate line has
           step close to that;
    tone   |mean L one side - mean L other side| over 3..8 px bands. Since
           the variation table offsets each part on purpose, a same-material
           tone step of a few L is now intended, not a grading failure.
  Pairs whose palette classes differ are a material change the geometry asks
  for and are listed but not judged.
"""
from pathlib import Path
import json
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import imgops as I
import parts as P

HERE = Path(__file__).resolve().parent
SRC = HERE / 'build' / 'review'
OUT = HERE / 'review'
OUT.mkdir(exist_ok=True)
PATCHED = Path(sys.argv[sys.argv.index('--patched') + 1]) if '--patched' in sys.argv else None
if PATCHED is not None and not PATCHED.is_absolute():
    PATCHED = HERE / PATCHED
WHOLE = ['port-side', 'port-quarter', 'high-quarter', 'top', 'starboard-side', 'starboard-quarter',
         'high-quarter-starboard', 'bow-quarter', 'stern', 'under-quarter']
CLOSE = ['port-flank-detail', 'port-aft-detail', 'bow-detail', 'bridge-detail', 'turret-detail',
         'starboard-aft-detail', 'stern-detail', 'bay-detail']
PATCHED_VIEWS = ['port-quarter', 'starboard-quarter', 'port-flank-detail', 'stern-detail']
PART_ORDER = list(P.PARTS)
CLASS_OF = {}
for part in P.PARTS:
    path = HERE / 'guide' / f'{P.source_part(part)}-class.png'
    if path.exists():
        cls = I.class_masks(I.load_rgb(path))
        CLASS_OF[part] = max(cls, key=lambda c: cls[c].sum())
COLOURS = np.array([[30 + 15 * (k % 14), 30 + 15 * (k // 14), 128] for k in range(len(PART_ORDER))], np.float32)  # review.part_rgb
SWATCH = np.array([40, 40, 255], np.float32)  # review.SWATCH_COLOUR
try:
    FONT = ImageFont.truetype('arial.ttf', 22)
    SMALL = ImageFont.truetype('arial.ttf', 16)
except OSError:
    FONT = SMALL = ImageFont.load_default()


def part_ids(rgb):
    """Part index per pixel, -2 for swatch, -1 for anything else (decodes review.part_rgb)."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    i, j = np.round((r - 30) / 15), np.round((g - 30) / 15)
    ok = (np.abs(r - 30 - 15 * i) < 5) & (np.abs(g - 30 - 15 * j) < 5) & (np.abs(b - 128) < 6) & (i >= 0) & (i < 14) & (j >= 0)
    k = (i + 14 * j).astype(int)
    ids = np.where(ok & (k < len(PART_ORDER)), k, -1)
    ids[np.linalg.norm(rgb - SWATCH, axis=-1) < 10] = -2
    return ids


_BG = {}


def world_colour(src):
    """The flat world colour, read off a whole-ship view's corners (a close-up's
    border is ship, so it cannot supply its own background)."""
    if src not in _BG:
        ref = I.load_rgb(next(src / f'{v}.png' for v in WHOLE + PATCHED_VIEWS if (src / f'{v}.png').exists()))
        _BG[src] = np.median(np.concatenate([ref[:40, :40].reshape(-1, 3), ref[:40, -40:].reshape(-1, 3)]), 0)
    return _BG[src]


def coverage(src, view):
    beauty = I.load_rgb(src / f'{view}.png')
    ids = part_ids(I.load_rgb(src / f'{view}-part.png'))
    ship = np.linalg.norm(beauty - world_colour(src), axis=-1) > 4
    n = max(ship.sum(), 1)
    return {'skin': round(float(((ids >= 0) & ship).sum() / n), 4),
            'swatch': round(float(((ids == -2) & ship).sum() / n), 4),
            'other': round(float(((ids == -1) & ship).sum() / n), 4),
            'ship_pixels': int(ship.sum())}, ids


def measure(view):
    cov, ids = coverage(SRC, view)
    albedo = I.rgb_to_lab(I.load_rgb(SRC / f'{view}-albedo.png'))
    rep = {'coverage': cov, 'parts_visible': sorted(PART_ORDER[k] for k in np.unique(ids[ids >= 0]))}
    pairs, within = {}, []
    for axis in (0, 1):
        a = np.s_[:-4, :] if axis == 0 else np.s_[:, :-4]
        b = np.s_[4:, :] if axis == 0 else np.s_[:, 4:]
        mid1 = np.s_[1:-3, :] if axis == 0 else np.s_[:, 1:-3]
        mid2 = np.s_[3:-1, :] if axis == 0 else np.s_[:, 3:-1]
        ia, ib = ids[a], ids[b]
        clean = (ia >= 0) & (ib >= 0) & (ia != ib) & (ids[mid1] == ia) & (ids[mid2] == ib)
        de = np.linalg.norm(albedo[a] - albedo[b], axis=-1)
        same = (ia >= 0) & (ia == ib) & (ids[mid1] == ia) & (ids[mid2] == ia)
        within.append(de[same])
        lo, hi = np.minimum(ia[clean], ib[clean]), np.maximum(ia[clean], ib[clean])
        for key in np.unique(lo * 1000 + hi):
            sel = (lo * 1000 + hi) == key
            pair = (PART_ORDER[key // 1000], PART_ORDER[key % 1000])
            pairs.setdefault(pair, []).append(de[clean][sel])
    within = np.concatenate(within)
    rep['within_part_dE_4px'] = round(float(within.mean()), 2) if len(within) else None
    rep['seams'] = []
    L = albedo[..., 0]
    for (pa, pb), vals in pairs.items():
        vals = np.concatenate(vals)
        if len(vals) < 40:
            continue
        ma, mb = ids == PART_ORDER.index(pa), ids == PART_ORDER.index(pb)
        ys, xs = np.nonzero(ma | mb)  # work in the pair's own box: full-frame dilation per pair is minutes per view
        box = np.s_[max(ys.min() - 10, 0):ys.max() + 11, max(xs.min() - 10, 0):xs.max() + 11]
        ma, mb, L_ = ma[box], mb[box], L[box]
        near_a = ma & I.dilate(mb, 8) & ~I.dilate(mb, 3)
        near_b = mb & I.dilate(ma, 8) & ~I.dilate(ma, 3)
        tone = abs(float(L_[near_a].mean() - L_[near_b].mean())) if near_a.sum() > 20 and near_b.sum() > 20 else None
        ca, cb = CLASS_OF.get(pa), CLASS_OF.get(pb)
        rep['seams'].append({'parts': [pa, pb], 'classes': [ca, cb], 'same_material': ca == cb, 'boundary_px': int(len(vals)),
                             'step_dE': round(float(vals.mean()), 2), 'tone_dL': None if tone is None else round(tone, 2)})
    rep['seams'].sort(key=lambda s: -s['boundary_px'])
    return rep, ids


def label(draw, xy, text, font=FONT):
    x, y = xy
    w = draw.textlength(text, font=font)
    draw.rectangle([x - 4, y - 2, x + w + 4, y + font.size + 4], fill=(20, 20, 20))
    draw.text((x, y), text, fill=(255, 235, 120), font=font)


def cov_text(c):
    return f"skin {c['skin'] * 100:.0f}% + swatch {c['swatch'] * 100:.0f}% = {(c['skin'] + c['swatch']) * 100:.0f}% textured"


def view_sheet(src, views, results, name, caption):
    sheet = Image.new('RGB', (1920, 1080), (30, 30, 30))
    d = ImageDraw.Draw(sheet)
    for k, v in enumerate(views):
        im = Image.open(src / f'{v}.png').convert('RGB').resize((960, 540), Image.LANCZOS)
        sheet.paste(im, ((k % 2) * 960, (k // 2) * 540))
        label(d, ((k % 2) * 960 + 12, (k // 2) * 540 + 10), f'{v}   {cov_text(results[v])}', SMALL)
    label(d, (12, 1080 - 30), caption, SMALL)
    sheet.save(OUT / f'{name}.png')


results, idmaps = {}, {}
for v in WHOLE + CLOSE:
    if not (SRC / f'{v}.png').exists():
        continue
    results[v], idmaps[v] = measure(v)
    print(v, cov_text(results[v]['coverage']), 'within', results[v]['within_part_dE_4px'])

seam_table = {}
for v, r in results.items():
    for s in r['seams']:
        key = ' / '.join(s['parts'])
        cur = seam_table.get(key)
        if cur is None or s['boundary_px'] > cur['boundary_px']:
            seam_table[key] = dict(s, view=v, within_part_dE_4px=r['within_part_dE_4px'])

cov = {v: r['coverage'] for v, r in results.items()}
caption = 'Second Wind whole-hull surfacing, clean build: graded Codex part skins + graded class swatches on every other base face.'
pages = [('views-port', ['port-side', 'port-quarter', 'high-quarter', 'top']),
         ('views-starboard', ['starboard-side', 'starboard-quarter', 'high-quarter-starboard', 'bow-quarter']),
         ('views-ends', ['stern', 'under-quarter', 'bow-detail', 'bridge-detail']),
         ('views-close', ['port-flank-detail', 'port-aft-detail', 'turret-detail', 'starboard-aft-detail']),
         ('views-close-2', ['stern-detail', 'bay-detail'])]
for name, views in pages:
    views = [v for v in views if v in cov]
    if views:
        view_sheet(SRC, views, cov, name, caption)

# Coverage-only sheet: the part-id pass of every whole view, swatch blue, part colours.
sheet = Image.new('RGB', (1920, 1080), (30, 30, 30))
d = ImageDraw.Draw(sheet)
for k, v in enumerate([v for v in WHOLE if v in cov][:10]):
    ids = idmaps[v]
    beauty = np.asarray(Image.open(SRC / f'{v}.png').convert('RGB'), np.float32)
    over = beauty * .35
    over[ids == -2] = beauty[ids == -2] * .4 + np.array([40, 90, 255]) * .6
    over[ids >= 0] = beauty[ids >= 0] * .4 + np.array([255, 170, 40]) * .6
    x, y = (k % 4) * 480, (k // 4) * 300
    sheet.paste(Image.fromarray(over.astype(np.uint8)).resize((480, 270), Image.LANCZOS), (x, y))
    label(d, (x + 6, y + 4), f"{v}: skin {cov[v]['skin'] * 100:.0f}% / swatch {cov[v]['swatch'] * 100:.0f}%", SMALL)
d.text((16, 930), 'Coverage per view: orange = generated part skin, blue = graded class swatch (small fittings, ends, chamfers left to swatch),',
       fill=(230, 230, 230), font=SMALL)
d.text((16, 952), 'dark = neither (lamps, glazing). Shares are of the ship\'s pixels in that 1920x1080 view.', fill=(230, 230, 230), font=SMALL)
y = 985
line = '   '.join(f"{v} {(c['skin'] + c['swatch']) * 100:.0f}%" for v, c in cov.items() if v in CLOSE)
d.text((16, y), 'Close-ups textured: ' + line, fill=(230, 230, 230), font=SMALL)
sheet.save(OUT / 'coverage.png')

# --- seams -----------------------------------------------------------------------
sheet = Image.new('RGB', (1920, 1080), (30, 30, 30))
d = ImageDraw.Draw(sheet)
pv = 'port-quarter' if 'port-quarter' in idmaps else next(iter(idmaps))
ids = idmaps[pv]
over = np.asarray(Image.open(SRC / f'{pv}.png').convert('RGB'), np.float32).copy()
col = np.where(ids[..., None] >= 0, COLOURS[np.clip(ids, 0, None)] * [1, 1, 0] + [0, 0, 180], over)
over = over * .45 + col * .55
edge = np.zeros(ids.shape, bool)
edge[:-1] |= (ids[:-1] != ids[1:]) & (ids[:-1] >= 0) & (ids[1:] >= 0)
edge[:, :-1] |= (ids[:, :-1] != ids[:, 1:]) & (ids[:, :-1] >= 0) & (ids[:, 1:] >= 0)
over[I.dilate(edge, 1)] = (255, 255, 255)
sheet.paste(Image.fromarray(over.astype(np.uint8)).resize((960, 540), Image.LANCZOS), (0, 0))
label(d, (12, 10), f'{pv}: parts (colour) and part boundaries (white)')
sv = 'starboard-quarter' if 'starboard-quarter' in idmaps else pv
ids = idmaps[sv]
over = np.asarray(Image.open(SRC / f'{sv}.png').convert('RGB'), np.float32).copy()
col = np.where(ids[..., None] >= 0, COLOURS[np.clip(ids, 0, None)] * [1, 1, 0] + [0, 0, 180], over)
over = over * .45 + col * .55
edge = np.zeros(ids.shape, bool)
edge[:-1] |= (ids[:-1] != ids[1:]) & (ids[:-1] >= 0) & (ids[1:] >= 0)
edge[:, :-1] |= (ids[:, :-1] != ids[:, 1:]) & (ids[:, :-1] >= 0) & (ids[:, 1:] >= 0)
over[I.dilate(edge, 1)] = (255, 255, 255)
sheet.paste(Image.fromarray(over.astype(np.uint8)).resize((960, 540), Image.LANCZOS), (960, 0))
label(d, (972, 10), f'{sv}: parts and boundaries')
rows = sorted(seam_table.values(), key=lambda s: (not s['same_material'], -s['boundary_px']))
y = 556
d.text((16, y), 'Seams between adjacent parts (albedo CIELAB; same-part texture variation at the same 4 px spacing in brackets; tone includes the owned variation offsets)',
       fill=(255, 255, 255), font=SMALL)
y += 24
for x, h in ((16, 'parts'), (760, 'materials'), (960, 'boundary px'), (1100, 'step dE'), (1260, 'tone dL'), (1400, 'view')):
    d.text((x, y), h, fill=(200, 200, 200), font=SMALL)
y += 21
for s in rows[:21]:
    c = (255, 255, 255) if s['same_material'] else (150, 150, 150)
    d.text((16, y), ' / '.join(s['parts']), fill=c, font=SMALL)
    d.text((760, y), '/'.join(str(x) for x in s['classes']), fill=c, font=SMALL)
    d.text((960, y), str(s['boundary_px']), fill=c, font=SMALL)
    d.text((1100, y), f"{s['step_dE']:.1f} ({s['within_part_dE_4px']:.1f})", fill=c, font=SMALL)
    d.text((1260, y), '-' if s['tone_dL'] is None else f"{s['tone_dL']:.1f}", fill=c, font=SMALL)
    d.text((1400, y), s['view'], fill=c, font=SMALL)
    y += 21
d.text((16, 1056), 'Grey rows change material at the crease: a step the geometry asks for, not a seam defect.', fill=(170, 170, 170), font=SMALL)
sheet.save(OUT / 'seams.png')

# --- process: guide -> Codex -> graded, six sheets per page -------------------------
reg, grade = {}, {}
for f in sorted((HERE / 'registration').glob('summary-*.json')):
    reg.update(json.loads(f.read_text()))
for f in sorted((HERE / 'grading').glob('summary-*.json')):
    grade.update(json.loads(f.read_text()).get('parts', {}))
names = [n for n in P.SHEETS if (HERE / 'generated' / f'sheet-{n}.png').exists()]
spread = {}
for c in ('cream', 'oxide', 'gunmetal', 'dark', 'pale'):
    before = [g[c]['before_mean_lab'] for g in grade.values() if c in g and 'after_mean_lab' in g[c]]
    after = [g[c]['after_mean_lab'] for g in grade.values() if c in g and 'after_mean_lab' in g[c]]
    if len(before) >= 2:
        def worst(ms):
            ms = np.array(ms)
            return round(float(max(np.linalg.norm(a - b) for a in ms for b in ms)), 2)
        spread[c] = {'parts': len(before), 'max_pair_dE_before': worst(before), 'max_pair_dE_after': worst(after),
                     'L_range_before': [round(min(b[0] for b in before), 1), round(max(b[0] for b in before), 1)]}
for page in range(0, len(names), 6):
    sheet = Image.new('RGB', (1920, 1080), (30, 30, 30))
    d = ImageDraw.Draw(sheet)
    for k, name in enumerate(names[page:page + 6]):
        x0, y0 = (k % 2) * 960, (k // 2) * 330
        graded = Image.new('RGB', P.SHEET, P.BACKGROUND)
        for t, part in enumerate(P.SHEETS[name]):
            tex = HERE / 'textures' / f'{part}.png'
            if not tex.exists():
                continue
            tex = np.asarray(Image.open(tex).convert('RGB'), np.float32)
            m = I.load_rgb(HERE / 'guide' / f'{part}-mask.png')[..., :1] / 255
            graded.paste(Image.fromarray((tex * m + np.array(P.BACKGROUND) * (1 - m)).astype(np.uint8)), P.tile_box(t)[:2])
        for j, im in enumerate([Image.open(HERE / 'guide' / f'sheet-{name}-guide.png'), Image.open(HERE / 'generated' / f'sheet-{name}.png'), graded]):
            sheet.paste(im.convert('RGB').resize((316, 178), Image.LANCZOS), (x0 + 4 + j * 318, y0 + 28))
        label(d, (x0 + 8, y0 + 4), f'sheet {name} ({P.SHEET_GROUP[name]}): guide / Codex / registered + graded', SMALL)
        yy = y0 + 210
        for part in P.SHEETS[name]:
            r, g = reg.get(part), grade.get(part)
            if r is None or g is None:
                d.text((x0 + 8, yy), f'{part}: not registered', fill=(230, 120, 120), font=SMALL)
                yy += 19
                continue
            held = ', '.join(f"{c} {g[c]['share_in_declared_family'] * 100:.0f}%" for c in ('cream', 'oxide', 'gunmetal', 'dark', 'pale') if c in g)
            d.text((x0 + 8, yy), f"{part}: IoU {r['fitted_iou']:.3f}, scale {r['scale'][0]:.3f}/{r['scale'][1]:.3f}, held {held}, ramp {g['ramp_span_L_removed_max'] or 0:.1f}L",
                   fill=(230, 230, 230), font=SMALL)
            yy += 19
    if page + 6 >= len(names):
        y = 1000
        d.text((16, y), 'Tone across generations before grading (worst pair of part means) -> after; grading puts every class on the palette, the variation table then offsets each part:',
               fill=(255, 255, 255), font=SMALL)
        d.text((16, y + 24), '   '.join(f"{c}: {s['parts']} parts, dE {s['max_pair_dE_before']:.1f} -> {s['max_pair_dE_after']:.1f}" for c, s in spread.items()),
               fill=(230, 230, 230), font=SMALL)
    sheet.save(OUT / f'process-{page // 6 + 1}.png')

# --- patched state ----------------------------------------------------------------------
patched = {}
if PATCHED is not None:
    for v in PATCHED_VIEWS:
        if (PATCHED / f'{v}.png').exists():
            patched[v] = coverage(PATCHED, v)[0]
    view_sheet(PATCHED, list(patched), patched, 'patched-views',
               'The same base skin on the patched damage build: craters cut through it, salvage plates and welds keep their own materials.')
    print('patched', {v: cov_text(c) for v, c in patched.items()})

(OUT / 'review.json').write_text(json.dumps({'views': results, 'patched_coverage': patched, 'seams_by_pair': seam_table,
                                             'tone_spread_before_grading': spread}, indent=1) + '\n')
print('sheets written')
