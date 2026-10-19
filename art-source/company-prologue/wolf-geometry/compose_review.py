"""Top-view sprite overlay, silhouette IoU, shot-check extents and the review set.

Run with ordinary Python (Pillow + numpy) after build_wolf.py:
  python3 compose_review.py [--dir DIR] [--review DIR] [--manifest]
The top render's alpha is the model silhouette. The vanilla sprite (read from
the install, never copied) is laid into the same frame through the top
camera's projection (bow left, port below) and the masks are compared pixel
for pixel. The shot checks' alpha gives the hull's frame extent in pixels.
Writes into --dir: top-overlay.png, an opaque top.png, review-sheet.png,
shot-checks.png; records silhouette and shot_extent_px in its geometry.json
(and with --manifest in the tracked copy here). With --review DIR it also
copies the numbered 1920x1080 review set there.
"""
from pathlib import Path
import argparse
import json
import shutil
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent))
from starsector_path import GAME_CORE

BG = (222, 223, 223)
parser = argparse.ArgumentParser()
parser.add_argument('--dir', default=str(HERE.parents[2]/'build'/'wolf-p'))
parser.add_argument('--sprite', default=str(GAME_CORE/'graphics/ships/wolf/wolf_pirates.png'))
parser.add_argument('--review')
parser.add_argument('--manifest', action='store_true')
parser.add_argument('--title')
args = parser.parse_args()
src = Path(args.dir)
manifest = json.loads((src/'geometry.json').read_text(encoding='utf-8'))
mpu = manifest['metres_per_unit']


def font(s):
    for f in ('/System/Library/Fonts/Supplemental/Arial.ttf', 'C:/Windows/Fonts/arial.ttf',
              '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if Path(f).exists():
            return ImageFont.truetype(f, s)
    return ImageFont.load_default()


cam = next(c for c in manifest['cameras'] if c['name'] == 'top')
cx, cy = cam['position'][:2]
scale = cam['orthographic_scale']
sprite = Image.open(args.sprite).convert('RGBA')
sw, sh = sprite.size
ccol, crow_up = manifest['sprite']['center']
crow = sh-crow_up  # .ship centre is measured from the sprite's bottom edge

raw = src/'top-alpha.png'
if Image.open(src/'top.png').mode == 'RGBA':
    Image.open(src/'top.png').save(raw)
render = Image.open(raw).convert('RGBA')
W, H = render.size
k = W/scale
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
iou = (sprite_mask & model_mask).sum()/(sprite_mask | model_mask).sum()
missing = (sprite_mask & ~model_mask).sum()/sprite_mask.sum()
extra = (model_mask & ~sprite_mask).sum()/sprite_mask.sum()
opaque = Image.alpha_composite(Image.new('RGBA', (W, H), BG+(255,)), render)
opaque.convert('RGB').save(src/'top.png')
half = np.dstack([sprite_rgba[..., :3], (sprite_rgba[..., 3]*.5).astype(np.uint8)])
over = Image.alpha_composite(opaque, Image.fromarray(half))
diff = np.zeros((H, W, 4), np.uint8)
diff[sprite_mask & ~model_mask] = (220, 30, 30, 170)
diff[model_mask & ~sprite_mask] = (20, 190, 220, 170)
over = Image.alpha_composite(over, Image.fromarray(diff))
d = ImageDraw.Draw(over)
d.text((28, 20), 'WOLF (P) / TOP OVER wolf_pirates.png (50%)', font=font(30), fill=(39, 49, 55))
d.text((28, 60), f'Silhouette IoU {iou:.3f}   red: sprite not covered ({missing:.1%})   '
                 f'cyan: model outside sprite ({extra:.1%})   bow left, port below, 1 px = 1 unit',
       font=font(20), fill=(72, 81, 88))
over.convert('RGB').save(src/'top-overlay.png')
manifest['silhouette'] = {'iou': round(float(iou), 4), 'sprite_uncovered': round(float(missing), 4),
                          'model_outside_sprite': round(float(extra), 4),
                          'method': 'top render alpha vs sprite alpha (>127), nearest-sampled into the '
                                    f'top camera frame at {k:.2f} px per unit; the hardpoint guns (Weapon WS ...) '
                                    'are hidden in the top render: the hull sprite draws no weapons'}
print(f'IoU {iou:.4f}  uncovered {missing:.3%}  outside {extra:.3%}')

# Shot checks: frame extent from alpha, and a sheet with 8x nearest insets.
extent = {}
shots = [n for n in ('shot-670m', 'shot-1500m', 'shot-4000m') if (src/(n+'.png')).exists()]
if shots:
    sheet = Image.new('RGB', (1920, 1080), (0, 0, 0))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(shots):
        im = Image.open(src/(n+'.png')).convert('RGBA')
        a = np.array(im)[..., 3] > 20
        ys, xs = np.nonzero(a)
        x0, x1, y0, y1 = xs.min(), xs.max()+1, ys.min(), ys.max()+1
        extent[n] = {'width_px': int(x1-x0), 'height_px': int(y1-y0)}
        crop = im.crop((x0-6, y0-6, x1+6, y1+6))
        z = max(1, min(8, int(600/crop.size[0]), int(400/crop.size[1])))
        crop = Image.alpha_composite(Image.new('RGBA', crop.size, (0, 0, 0, 255)), crop)
        big = crop.resize((crop.size[0]*z, crop.size[1]*z), Image.NEAREST)
        x = 40+i*630
        sheet.paste(big.convert('RGB'), (x, 200))
        d.text((x, 120), f'{n.upper()}  {x1-x0} x {y1-y0} px', font=font(24),
               fill=(230, 230, 230))
        d.text((x, 155), f'shown at {z}x, nearest; low sun, no fill', font=font(18),
               fill=(160, 160, 160))
    d.text((40, 40), f'WOLF (P) / ASHARU SHOT CHECK (S9 28mm 670 m, S6 30mm 1.5 km, S4 35mm 4 km)  -  {mpu} m per unit, '
                     f'{manifest["scale"]["size_m"][0]} m long', font=font(30), fill=(240, 240, 240))
    sheet.save(src/'shot-checks.png', optimize=True)
    manifest['shot_extent_px'] = extent
    print('shot extents', extent)
(src/'geometry.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
if args.manifest:
    shutil.copy(src/'geometry.json', HERE/'geometry.json')

# Review sheet: four views at half size.
panels = [('top-overlay', 'TOP OVER SPRITE / BOW LEFT - PORT BELOW'), ('three-quarter', 'PORT BOW QUARTER'),
          ('port-side', 'PORT SIDE / BOW LEFT'), ('stern', 'STERN')]
if all((src/(n+'.png')).exists() for n, _ in panels):
    sheet = Image.new('RGB', (1920, 1080), BG)
    draw = ImageDraw.Draw(sheet)
    draw.text((28, 15), 'WOLF (P) / PIRATE FAST FRIGATE - ' + (args.title or 'BLOCKOUT'), font=font(32),
              fill=(39, 49, 55))
    draw.text((28, 55), f'{mpu} m per unit  |  {" x ".join(str(v) for v in manifest["scale"]["size_m"])} m  |  '
                        f'{manifest["asset"]["triangles"]} triangles, {manifest["asset"]["objects"]} objects, '
                        f'{len(manifest["asset"]["materials"])} materials  |  IoU {iou:.3f}',
              font=font(19), fill=(72, 81, 88))
    for (name, label), (x, y) in zip(panels, [(0, 92), (960, 92), (0, 580), (960, 580)]):
        im = Image.open(src/(name+'.png')).convert('RGB').resize((960, 540), Image.LANCZOS).crop((0, 50, 960, 490))
        sheet.paste(im, (x, y))
        draw.text((x+28, y+442), label, font=font(20), fill=(39, 49, 55))
    sheet.save(src/'review-sheet.png', optimize=True)

if args.review:
    order = ['review-sheet', 'top-overlay', 'three-quarter', 'port-side', 'front', 'stern', 'bow-detail',
             'turret-detail', 'engine-detail', 'stern-detail', 'shot-checks', 'shot-670m']
    dst = Path(args.review)
    dst.mkdir(parents=True, exist_ok=True)
    k = 1
    for n in order:
        p = src/(n+'.png')
        if p.exists():
            im = Image.open(p)
            assert im.size == (1920, 1080), (n, im.size)
            if im.mode == 'RGBA':
                im = Image.alpha_composite(Image.new('RGBA', im.size, (0, 0, 0, 255)), im)
            im.convert('RGB').save(dst/f'{k:02d}-{n}.png', optimize=True)
            k += 1
    print('review set', dst)
