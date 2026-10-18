"""Compose the cross-state review: four states side by side, and scar continuity.

  python review_sheet.py [--renders DIR] [--views port-quarter,starboard-quarter,top,stern]

Reads each state's stills and `<view>.sites.json` (review_state.py --sites)
from <renders>/<state>/ (default ../../second-wind-surface/build/final/) and
each <state>/manifest.json. Writes to review/ beside this script, all 1920x1080:
  states-<view>.png             derelict | patched / refit | restored, one view
  scar-continuity-<view>.png    the same grid with every scar id marked where
                                this state shows it: a ring per recorded site the
                                camera sees, a square at the id's footprint
                                centre, numbered by scar-table row
  scar-continuity.json / .md    per id: status in each state; how far its
                                recorded sites move between states (3D, must be
                                0: they are cast once on the clean ship); how
                                far its footprint centre moves (what the scar
                                adds or hides differs by status, so this is a
                                spread, not an identity); marker spread on screen
"""
from pathlib import Path
import argparse
import json
import math
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
STATES = ('derelict', 'patched', 'refit', 'restored')
ap = argparse.ArgumentParser()
ap.add_argument('--renders', default=str(HERE.parent.parent / 'second-wind-surface' / 'build' / 'final'))
ap.add_argument('--views', default='port-quarter,starboard-quarter,top,stern')
args = ap.parse_args()
R = Path(args.renders)
OUT = HERE / 'review'
OUT.mkdir(exist_ok=True)
VIEWS = [v for v in args.views.split(',') if v]
try:
    FONT = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 22)
    SMALL = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 13)
except OSError:
    FONT = SMALL = ImageFont.load_default()
STATUS_COL = {'open': (235, 80, 60), 'dead': (235, 80, 60), 'patched': (245, 175, 40), 'capped': (245, 175, 40),
              'jury': (245, 175, 40), 'replaced': (90, 200, 255), 'faded': (190, 140, 255),
              'running': (110, 230, 120), 'gone': (150, 150, 150)}
man = {s: json.loads((HERE / s / 'manifest.json').read_text()) for s in STATES}
order = [r['id'] for r in man['patched']['scars']]
row = {sid: i + 1 for i, sid in enumerate(order)}
status = {s: {r['id']: r['status'] for r in man[s]['scars']} for s in STATES}


def grid(view, marks):
    W = Image.new('RGB', (1920, 1080), (12, 12, 14))
    d = ImageDraw.Draw(W)
    for k, s in enumerate(STATES):
        ox, oy = (k % 2) * 960, (k // 2) * 540
        im = Image.open(R / s / f'{view}.png').convert('RGB').resize((960, 540), Image.LANCZOS)
        W.paste(im, (ox, oy))
        if marks:
            sj = json.loads((R / s / f'{view}.sites.json').read_text())
            for sid, pts in sj['sites'].items():
                col = STATUS_COL.get(status[s].get(sid), (255, 255, 255))
                for x, y, seen in pts:
                    if seen:
                        x, y = ox + x / 2, oy + y / 2
                        d.ellipse((x - 4, y - 4, x + 4, y + 4), outline=col, width=2)
            for sid, (x, y) in sj['footprints'].items():
                if sid not in row:
                    continue
                col = STATUS_COL.get(status[s].get(sid), (255, 255, 255))
                x, y = ox + x / 2, oy + y / 2
                if ox <= x < ox + 960 and oy <= y < oy + 540:
                    d.rectangle((x - 3, y - 3, x + 3, y + 3), fill=col)
                    d.text((x + 5, y - 7), str(row[sid]), font=SMALL, fill=col, stroke_width=2,
                           stroke_fill=(0, 0, 0))
        d.rectangle((ox, oy, ox + 960, oy + 34), fill=(0, 0, 0))
        d.text((ox + 12, oy + 5), f'{s}  ·  {view}', font=FONT, fill=(235, 235, 235))
    if marks:
        x = 1920 - 12
        for st, col in (('open/dead', STATUS_COL['open']), ('patched/capped/jury', STATUS_COL['patched']),
                        ('replaced', STATUS_COL['replaced']), ('faded', STATUS_COL['faded']),
                        ('running', STATUS_COL['running'])):
            tw = d.textlength(st, font=SMALL)
            x -= tw + 26
            d.rectangle((x, 1080 - 22, x + 10, 1080 - 12), fill=col)
            d.text((x + 14, 1080 - 25), st, font=SMALL, fill=(230, 230, 230))
        d.text((12, 1080 - 25), 'ring: recorded site seen by this camera   square + number: footprint centre, scar-table row',
               font=SMALL, fill=(230, 230, 230))
    return W


for v in VIEWS:
    grid(v, False).save(OUT / f'states-{v}.png')
    if all((R / s / f'{v}.sites.json').exists() for s in STATES):
        grid(v, True).save(OUT / f'scar-continuity-{v}.png')

# Continuity numbers.
rows, worst_site, worst_screen = [], 0.0, 0.0
screens = {v: {s: json.loads((R / s / f'{v}.sites.json').read_text()) for s in STATES}
           for v in VIEWS if all((R / s / f'{v}.sites.json').exists() for s in STATES)}
for sid in order:
    sites = {s: man[s]['scar_sites'].get(sid) for s in STATES if man[s]['scar_sites'].get(sid)}
    dev = 0.0
    ref = next(iter(sites.values()), None)
    same_count = all(len(p) == len(ref) for p in sites.values()) if ref else True
    if ref:
        for pts in sites.values():
            for a, b in zip(pts, ref):
                dev = max(dev, math.dist(a['p'], b['p']))
    foot = {s: man[s]['scar_footprints'][sid]['centre'] for s in STATES if sid in man[s].get('scar_footprints', {})}
    spread = max((math.dist(a, b) for a in foot.values() for b in foot.values()), default=0.0)
    scr = 0.0
    for v, per in screens.items():
        pts = [per[s]['sites'].get(sid) for s in STATES if per[s]['sites'].get(sid)]
        for a in pts:
            for b in pts:
                for pa, pb in zip(a, b):
                    scr = max(scr, math.dist(pa[:2], pb[:2]))
    worst_site, worst_screen = max(worst_site, dev), max(worst_screen, scr)
    rows.append({'row': row[sid], 'id': sid, 'status': {s: status[s].get(sid) for s in STATES},
                 'site_states': sorted(sites), 'site_points': len(ref) if ref else 0,
                 'site_count_same': same_count, 'site_max_move_units': round(dev, 4),
                 'site_max_move_px': round(scr, 2), 'footprint_states': sorted(foot),
                 'footprint_spread_units': round(spread, 2)})
summary = {'ids': len(rows), 'ids_with_sites': sum(1 for r in rows if r['site_points']),
           'ids_with_footprint': sum(1 for r in rows if r['footprint_states']),
           'site_max_move_units': round(worst_site, 4), 'site_max_move_px': round(worst_screen, 2),
           'site_counts_consistent': all(r['site_count_same'] for r in rows),
           'ids_without_any_location': [r['id'] for r in rows if not r['site_points'] and not r['footprint_states']]}
(OUT / 'scar-continuity.json').write_text(json.dumps({'summary': summary, 'rows': rows}, indent=1) + '\n')
md = ['# Scar continuity across states', '',
      f"{summary['ids']} ids; {summary['ids_with_sites']} with cast sites, {summary['ids_with_footprint']} with a footprint in at least one state.",
      f"Recorded sites move at most {summary['site_max_move_units']} units between states "
      f"({summary['site_max_move_px']} px on screen); site counts consistent: {summary['site_counts_consistent']}.",
      '', '| # | id | derelict | patched | refit | restored | sites | site move | footprint spread (units) |',
      '|---|---|---|---|---|---|---|---|---|']
for r in rows:
    st = r['status']
    md.append(f"| {r['row']} | {r['id']} | {st['derelict']} | {st['patched']} | {st['refit']} | {st['restored']} | "
              f"{r['site_points']} | {r['site_max_move_units']} | {r['footprint_spread_units']} ({len(r['footprint_states'])} states) |")
(OUT / 'scar-continuity.md').write_text('\n'.join(md) + '\n')
print('SHEET', summary)
