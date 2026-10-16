"""Coverage and seam measurements, and the 1920x1080 review sheets.

  python compose_review.py   -> review/*.png, review/review.json

Reads review.py's renders under build/review/. Per view:
- coverage: skinned share of the ship's pixels (ship = beauty differs from the
  flat world background);
- seams: wherever two different parts meet on screen (part-id pass), the
  albedo is compared ACROSS the boundary, sampling 2 px either side so the
  antialiased boundary pixel itself is skipped. Two figures per part pair:
    step   mean per-pixel CIELAB dE across the boundary, against the mean dE
           between same-part pixels the same 4 px apart (the texture's own
           variation): a seam that merely reads as another plate line has
           step close to that;
    tone   |mean L one side - mean L other side| over 3..8 px bands: the
           generation-to-generation tone mismatch grading exists to remove.
  Pairs whose palette classes differ (oxide against gunmetal) are a material
  change the geometry asks for and are listed but not judged.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import imgops as I
import parts as P

HERE = Path(__file__).resolve().parent
SRC = HERE / 'build' / 'review'
OUT = HERE / 'review'
OUT.mkdir(exist_ok=True)
VIEWS = ['port-side', 'port-quarter', 'high-quarter', 'port-flank-detail', 'port-aft-detail']
PART_ORDER = list(P.PARTS)
CLASS_OF = {}
for part in P.PARTS:
    src = P.source_part(part)
    cls = I.class_masks(I.load_rgb(HERE / 'guide' / f'{src}-class.png'))
    CLASS_OF[part] = max(cls, key=lambda c: cls[c].sum())
COLOURS = np.array([[20 + 12 * k, 235 - 12 * k, 128] for k in range(len(PART_ORDER))], np.float32)  # review.part_colour, as displayed
try:
    FONT = ImageFont.truetype('arial.ttf', 22)
    SMALL = ImageFont.truetype('arial.ttf', 16)
except OSError:
    FONT = SMALL = ImageFont.load_default()


def part_ids(rgb):
    filled = rgb.max(-1) > 40
    d = np.linalg.norm(rgb[..., None, :] - COLOURS[None, None], axis=-1)
    ids = d.argmin(-1)
    ids[~filled | (d.min(-1) > 12)] = -1
    return ids


def measure(view):
    beauty = I.load_rgb(SRC / f'{view}.png')
    albedo = I.rgb_to_lab(I.load_rgb(SRC / f'{view}-albedo.png'))
    ids = part_ids(I.load_rgb(SRC / f'{view}-part.png'))
    bg = np.median(np.concatenate([beauty[0], beauty[-1]]), 0)
    ship = np.linalg.norm(beauty - bg, axis=-1) > 4
    rep = {'coverage_skinned_share_of_ship': round(float(((ids >= 0) & ship).sum() / max(ship.sum(), 1)), 4),
           'parts_visible': sorted(PART_ORDER[k] for k in np.unique(ids[ids >= 0]))}
    pairs, within = {}, []
    for axis in (0, 1):
        a = np.s_[:-4, :] if axis == 0 else np.s_[:, :-4]
        b = np.s_[4:, :] if axis == 0 else np.s_[:, 4:]
        mid1 = np.s_[1:-3, :] if axis == 0 else np.s_[:, 1:-3]
        mid2 = np.s_[3:-1, :] if axis == 0 else np.s_[:, 3:-1]
        ia, ib = ids[a], ids[b]
        # A clean crossing: A on both pixels on its side, B on both on its side.
        clean = (ia >= 0) & (ib >= 0) & (ia != ib) & (ids[mid1] == ia) & (ids[mid2] == ib)
        de = np.linalg.norm(albedo[a] - albedo[b], axis=-1)
        same = (ia >= 0) & (ia == ib) & (ids[mid1] == ia) & (ids[mid2] == ia)
        within.append(de[same])
        for pa, pb, v in zip(ia[clean], ib[clean], de[clean]):
            key = tuple(sorted((PART_ORDER[pa], PART_ORDER[pb])))
            pairs.setdefault(key, []).append(v)
    within = np.concatenate(within)
    rep['within_part_dE_4px'] = round(float(within.mean()), 2) if len(within) else None
    rep['seams'] = []
    L = albedo[..., 0]
    for (pa, pb), vals in sorted(pairs.items(), key=lambda kv: -len(kv[1])):
        if len(vals) < 40:
            continue
        ma, mb = ids == PART_ORDER.index(pa), ids == PART_ORDER.index(pb)
        near_a = ma & I.dilate(mb, 8) & ~I.dilate(mb, 3)
        near_b = mb & I.dilate(ma, 8) & ~I.dilate(ma, 3)
        tone = abs(float(L[near_a].mean() - L[near_b].mean())) if near_a.sum() > 20 and near_b.sum() > 20 else None
        rep['seams'].append({'parts': [pa, pb], 'classes': [CLASS_OF[pa], CLASS_OF[pb]],
                             'same_material': CLASS_OF[pa] == CLASS_OF[pb], 'boundary_px': len(vals),
                             'step_dE': round(float(np.mean(vals)), 2), 'tone_dL': None if tone is None else round(tone, 2)})
    return rep, ids


def label(draw, xy, text, font=FONT):
    x, y = xy
    w = draw.textlength(text, font=font)
    draw.rectangle([x - 4, y - 2, x + w + 4, y + font.size + 4], fill=(20, 20, 20))
    draw.text((x, y), text, fill=(255, 235, 120), font=font)


results, idmaps = {}, {}
for v in VIEWS:
    results[v], idmaps[v] = measure(v)
    print(v, results[v]['coverage_skinned_share_of_ship'], results[v]['within_part_dE_4px'],
          [(s['parts'], s['step_dE'], s['tone_dL']) for s in results[v]['seams'] if s['same_material']])

# Seam summary across views: same-material pairs only, worst view per pair.
seam_table = {}
for v, r in results.items():
    for s in r['seams']:
        key = ' / '.join(s['parts'])
        cur = seam_table.get(key)
        if cur is None or s['boundary_px'] > cur['boundary_px']:
            seam_table[key] = dict(s, view=v, within_part_dE_4px=r['within_part_dE_4px'])
(OUT / 'review.json').write_text(json.dumps({'views': results, 'seams_by_pair': seam_table}, indent=1) + '\n')

# --- sheet 1: the four review views ------------------------------------------
sheet = Image.new('RGB', (1920, 1080), (30, 30, 30))
d = ImageDraw.Draw(sheet)
for k, v in enumerate(['port-side', 'port-quarter', 'high-quarter', 'port-flank-detail']):
    im = Image.open(SRC / f'{v}.png').convert('RGB').resize((960, 540), Image.LANCZOS)
    sheet.paste(im, ((k % 2) * 960, (k // 2) * 540))
    label(d, ((k % 2) * 960 + 12, (k // 2) * 540 + 10),
          f"{v}   skinned {results[v]['coverage_skinned_share_of_ship'] * 100:.0f}% of ship pixels")
label(d, (12, 1080 - 34), 'Port-flank surfacing pilot: graded Codex part skins on the clean build. Flat dark grey = not yet skinned.', SMALL)
sheet.save(OUT / 'views.png')

# --- sheet 2: seams ------------------------------------------------------------
sheet = Image.new('RGB', (1920, 1080), (30, 30, 30))
d = ImageDraw.Draw(sheet)
pq = Image.open(SRC / 'port-quarter.png').convert('RGB')
ids = idmaps['port-quarter']
over = np.asarray(pq, np.float32).copy()
col = np.where(ids[..., None] >= 0, COLOURS[np.clip(ids, 0, None)], over)
over = over * .45 + col * .55
edge = np.zeros(ids.shape, bool)
edge[:-1] |= (ids[:-1] != ids[1:]) & (ids[:-1] >= 0) & (ids[1:] >= 0)
edge[:, :-1] |= (ids[:, :-1] != ids[:, 1:]) & (ids[:, :-1] >= 0) & (ids[:, 1:] >= 0)
over[I.dilate(edge, 1)] = (255, 255, 255)
sheet.paste(Image.fromarray(over.astype(np.uint8)).crop((300, 250, 1700, 1000)).resize((960, 515), Image.LANCZOS), (0, 0))
label(d, (12, 10), 'port-quarter: parts (colour) and part boundaries (white)')
# 1:1 crops at seam sites from the close-ups.
crops = [('port-flank-detail', (520, 380, 1000, 650), 'sponson cowl roof / side / lower body'),
         ('port-flank-detail', (0, 560, 480, 830), 'belt / bow flank / module'),
         ('port-aft-detail', (380, 330, 860, 600), 'pod cowl roof / cowl side'),
         ('port-aft-detail', (900, 620, 1380, 890), 'cowl side / lower casing / band')]
for k, (v, box, text) in enumerate(crops):
    x, y = 960 + (k % 2) * 480, (k // 2) * 270
    sheet.paste(Image.open(SRC / f'{v}.png').convert('RGB').crop(box), (x, y))
    label(d, (x + 8, y + 6), f'{v} 1:1: {text}', SMALL)
# Table.
rows = sorted(seam_table.values(), key=lambda s: (not s['same_material'], -s['boundary_px']))
y = 560
d.text((16, y), 'Seams between adjacent parts (albedo, CIELAB; same-part texture variation at the same 4 px spacing in brackets)', fill=(255, 255, 255), font=SMALL)
y += 26
d.text((16, y), 'parts', fill=(200, 200, 200), font=SMALL)
for x, h in ((700, 'materials'), (900, 'boundary px'), (1040, 'step dE'), (1200, 'tone dL'), (1340, 'view')):
    d.text((x, y), h, fill=(200, 200, 200), font=SMALL)
y += 22
for s in rows[:20]:
    c = (255, 255, 255) if s['same_material'] else (150, 150, 150)
    d.text((16, y), ' / '.join(s['parts']), fill=c, font=SMALL)
    d.text((700, y), '/'.join(s['classes']), fill=c, font=SMALL)
    d.text((900, y), str(s['boundary_px']), fill=c, font=SMALL)
    d.text((1040, y), f"{s['step_dE']:.1f} ({s['within_part_dE_4px']:.1f})", fill=c, font=SMALL)
    d.text((1200, y), '-' if s['tone_dL'] is None else f"{s['tone_dL']:.1f}", fill=c, font=SMALL)
    d.text((1340, y), s['view'], fill=c, font=SMALL)
    y += 21
d.text((16, 1056), 'Grey rows change material at the crease (oxide against gunmetal): a step the geometry asks for, not a seam defect.', fill=(170, 170, 170), font=SMALL)
sheet.save(OUT / 'seams.png')

# --- sheet 3: process (guide -> generated -> graded) ---------------------------
sheet = Image.new('RGB', (1920, 1080), (30, 30, 30))
d = ImageDraw.Draw(sheet)
reg = json.loads((HERE / 'registration' / 'summary.json').read_text())
grade = json.loads((HERE / 'grading' / 'summary.json').read_text())
for k, name in enumerate(P.SHEETS):
    x0, y0 = (k % 2) * 960, (k // 2) * 300
    graded = Image.new('RGB', P.SHEET, P.BACKGROUND)
    for t, part in enumerate(P.SHEETS[name]):
        tex = np.asarray(Image.open(HERE / 'textures' / f'{part}.png').convert('RGB'), np.float32)
        m = I.load_rgb(HERE / 'guide' / f'{part}-mask.png')[..., :1] / 255
        graded.paste(Image.fromarray((tex * m + np.array(P.BACKGROUND) * (1 - m)).astype(np.uint8)), P.tile_box(t)[:2])
    for j, im in enumerate([Image.open(HERE / 'guide' / f'sheet-{name}-guide.png'), Image.open(HERE / 'generated' / f'sheet-{name}.png'), graded]):
        sheet.paste(im.convert('RGB').resize((316, 178), Image.LANCZOS), (x0 + 4 + j * 318, y0 + 30))
    label(d, (x0 + 8, y0 + 4), f'sheet {name}: guide / Codex / registered + graded', SMALL)
    yy = y0 + 214
    for part in P.SHEETS[name]:
        r, g = reg[part], grade['parts'][part]
        cls = next(c for c in ('oxide', 'gunmetal', 'cream', 'dark') if c in g)
        d.text((x0 + 8, yy), f"{part}: IoU {r['raw_iou']:.3f}->{r['fitted_iou']:.3f}, scale {r['scale'][0]:.3f}/{r['scale'][1]:.3f}, "
               f"{cls} {g[cls]['share_in_declared_family'] * 100:.0f}% held, dE {g[cls]['dE_to_palette_before']:.1f}->0, ramp {g['ramp_span_L_removed_max']:.1f}L",
               fill=(230, 230, 230), font=SMALL)
        yy += 20
spread = grade['tone_spread_across_parts']
y = 620
d.text((16, y), 'Tone across generations (mean CIELAB per part, worst pair):', fill=(255, 255, 255), font=FONT)
for c, s in spread.items():
    y += 30
    d.text((16, y), f"{c}: {s['parts']} parts, worst pair dE {s['max_pair_dE_before']:.1f} before grading -> {s['max_pair_dE_after']:.1f} after; "
           f"mean L {s['L_range_before'][0]:.0f}..{s['L_range_before'][1]:.0f} -> {s['L_range_after'][0]:.0f}", fill=(230, 230, 230), font=SMALL)
sheet.save(OUT / 'process.png')
for v in ('port-flank-detail', 'port-aft-detail'):
    Image.open(SRC / f'{v}.png').convert('RGB').save(OUT / f'closeup-{v}.png')
print('sheets written')
