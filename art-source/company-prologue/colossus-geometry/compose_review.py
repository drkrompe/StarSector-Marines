"""Top-view sprite overlay, silhouette IoU and the 1920x1080 review sheet.

Run with ordinary Python (Pillow + numpy) after build_colossus.py:
  python compose_review.py [--dir DIR] [--sprite PATH]
The top render's alpha is the model silhouette. The vanilla sprite is laid
into the same frame through the top camera's projection (bow left, port
below) and the two masks are compared pixel for pixel. Writes
top-overlay.png, replaces top.png with an opaque copy for review, records
the IoU in geometry.json and composes colossus-geometry.reference.png.
"""
from pathlib import Path
import argparse
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
GAME_SPRITE = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core'
                   r'\graphics\ships\colossus\colossus.png')
BG = (222, 223, 223)

parser = argparse.ArgumentParser()
parser.add_argument('--dir', default=str(HERE))
parser.add_argument('--sprite', default=str(GAME_SPRITE))
parser.add_argument('--before', default='reference-history/v1-blockout',
                    help='snapshot folder (relative to this one) for the before/after sheet')
parser.add_argument('--compare', default='before-after.png')
parser.add_argument('--pairs', help='comma-separated views for the before/after sheet (default: the close-ups)')
parser.add_argument('--title', default='COLOSSUS / GEOMETRY PASS 1 - BLOCKOUT')
args = parser.parse_args()
src = Path(args.dir)
manifest = json.loads((src/'geometry.json').read_text(encoding='utf-8'))
cam = next(c for c in manifest['cameras'] if c['name'] == 'top')
cx, cy = cam['position'][:2]
scale = cam['orthographic_scale']
sprite = Image.open(args.sprite).convert('RGBA')
sw, sh = sprite.size
ccol, crow_up = manifest['sprite']['center']
crow = sh - crow_up  # .ship centre is measured from the sprite's bottom edge

top_path = src/'top.png'
raw = src/'top-alpha.png'
if Image.open(top_path).mode == 'RGBA':
    Image.open(top_path).save(raw)  # keep the transparent original for re-runs
render = Image.open(raw).convert('RGBA')
W, H = render.size
k = W/scale  # pixels per model unit

# Model coordinates of every output pixel centre, then the sprite pixel there.
u, v = np.meshgrid(np.arange(W)+.5, np.arange(H)+.5)
X = cx-(u-W/2)/k
Y = cy+(v-H/2)/k
col = np.floor(ccol-Y).astype(int)
row = np.floor(crow-X).astype(int)
inside = (col >= 0) & (col < sw) & (row >= 0) & (row < sh)
spr = np.array(sprite)
sprite_rgba = np.zeros((H, W, 4), np.uint8)
sprite_rgba[inside] = spr[row[inside], col[inside]]
sprite_mask = sprite_rgba[..., 3] > 127
model_mask = np.array(render)[..., 3] > 127
inter = (sprite_mask & model_mask).sum()
union = (sprite_mask | model_mask).sum()
iou = inter/union
missing = (sprite_mask & ~model_mask).sum()/sprite_mask.sum()
extra = (model_mask & ~sprite_mask).sum()/sprite_mask.sum()

base = Image.new('RGBA', (W, H), BG+(255,))
opaque = Image.alpha_composite(base, render)
opaque.convert('RGB').save(top_path)

# Overlay: the render, the sprite at half strength over it, and the
# disagreement drawn in colour: red = sprite not covered, cyan = model
# outside the sprite.
half = np.dstack([sprite_rgba[..., :3], (sprite_rgba[..., 3]*.5).astype(np.uint8)])
over = Image.alpha_composite(opaque, Image.fromarray(half))
diff = np.zeros((H, W, 4), np.uint8)
diff[sprite_mask & ~model_mask] = (220, 30, 30, 170)
diff[model_mask & ~sprite_mask] = (20, 190, 220, 170)
over = Image.alpha_composite(over, Image.fromarray(diff))
d = ImageDraw.Draw(over)
font = lambda s: ImageFont.truetype('C:/Windows/Fonts/arial.ttf', s)
d.text((28, 20), 'COLOSSUS / TOP OVER colossus.png (50%)', font=font(30), fill=(39, 49, 55))
d.text((28, 60), f'Silhouette IoU {iou:.3f}   red: sprite not covered ({missing:.1%})   '
                 f'cyan: model outside sprite ({extra:.1%})   bow left, port below, 1 px = 1 unit',
       font=font(20), fill=(72, 81, 88))
over.convert('RGB').save(src/'top-overlay.png')

manifest['silhouette'] = {'iou': round(float(iou), 4), 'sprite_uncovered': round(float(missing), 4),
                          'model_outside_sprite': round(float(extra), 4),
                          'method': 'top render alpha vs sprite alpha (>127), nearest-sampled into the '
                                    f'top camera frame at {k:.2f} px per unit'}
(src/'geometry.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')

print(f'IoU {iou:.4f}  uncovered {missing:.3%}  outside {extra:.3%}')

# Review sheet: four views at half size (skipped unless all four exist).
sheet = Image.new('RGB', (1920, 1080), BG)
draw = ImageDraw.Draw(sheet)
draw.text((28, 15), args.title, font=font(32), fill=(39, 49, 55))
draw.text((28, 55), 'LOW TECH HEAVY FREIGHTER (DOMAIN FRONTIER SUPPLY)  |  CARGO BODY: CENTRAL HOLD + FOUR SIDE MODULES  |  AFT BRIDGE  |  TWO DRIVE PODS, SIX DRIVES  |  ' + f"{manifest['metres_per_unit']} M PER UNIT",
          font=font(19), fill=(72, 81, 88))
panels = [('top-overlay', 'TOP OVER SPRITE / BOW LEFT - PORT BELOW'), ('three-quarter', 'PORT BOW QUARTER'),
          ('port-side', 'PORT SIDE / BOW LEFT'), ('front', 'BOW')]
if not all((src/(n+'.png')).exists() for n, _ in panels):
    raise SystemExit('review sheet skipped: not every standard view was rendered')
for (name, label), (x, y) in zip(panels, [(0, 92), (960, 92), (0, 580), (960, 580)]):
    im = Image.open(src/(name+'.png')).convert('RGB').resize((960, 540), Image.LANCZOS).crop((0, 50, 960, 490))
    sheet.paste(im, (x, y))
    draw.text((x+28, y+442), label, font=font(20), fill=(39, 49, 55))
draw.text((28, 1050), f'Slots and engines from colossus.ship; heights proposed. Silhouette IoU {iou:.3f}. Geometry review only.',
          font=font(17), fill=(72, 81, 88))
sheet.save(src/'colossus-geometry.reference.png', optimize=True)

# Before/after close-ups against a reference-history snapshot, for every
# close-up both sides have.
before = HERE/args.before
DETAILS = ['bow-detail', 'cargo-detail', 'bridge-detail', 'turret-detail', 'engine-detail', 'stern-detail',
           'underside']
if args.pairs:
    DETAILS = args.pairs.split(',')
pairs = [n for n in DETAILS if (before/(n+'.png')).exists() and (src/(n+'.png')).exists()]
if pairs:
    cmp = Image.new('RGB', (1920, 1080), BG)
    d = ImageDraw.Draw(cmp)
    rows = (len(pairs)+1)//2
    ch = min(270, (1080-40)//rows-28)
    cw = round(ch*16/9)
    for i, n in enumerate(pairs):
        x, y = (i % 2)*960, 40+(i//2)*(ch+28)
        for j, folder in enumerate((before, src)):
            im = Image.open(folder/(n+'.png')).convert('RGB').resize((cw, ch), Image.LANCZOS)
            cmp.paste(im, (x+j*cw, y))
        d.text((x+8, y+ch+3), f'{n.upper()}   before | after', font=font(18), fill=(39, 49, 55))
    d.text((12, 8), f'COLOSSUS / BEFORE ({before.name}) | AFTER - same cameras', font=font(24), fill=(39, 49, 55))
    cmp.save(src/args.compare, optimize=True)
