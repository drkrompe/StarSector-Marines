"""Top-view sprite overlay, silhouette IoU and the 1920x1080 review sheets.

Run with ordinary Python (Pillow + numpy) after build_onslaught.py:
  python3 compose_review.py [--dir DIR] [--sprite PATH] [--title TEXT] [--before DIR]
Writes
  top-overlay.png        the top render with the sprite over it at 50% and the
                         disagreement in colour; the IoU goes into geometry.json
  onslaught-geometry.reference.png   four views on one sheet
  formation-sheet.png    the C1 v2 distance views (formation-*.png), each
                         cropped round the hull at 1:1, never rescaled, so the
                         sheet shows the hull at the size the shot sees it; the
                         measured span in pixels goes into geometry.json
  before-after.png       close-ups against --before (same cameras), when given
"""
from pathlib import Path
import argparse
import json
import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
GAME = Path(os.environ.get('STARSECTOR_CORE') or (
    '/Applications/Starsector.app/Contents/Resources/Java' if sys.platform == 'darwin'
    else r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core'))
GAME_SPRITE = GAME/'graphics'/'ships'/'onslaught'/'onslaught_hegemony.png'
BG = (222, 223, 223)
FONT = '/System/Library/Fonts/Supplemental/Arial.ttf' if sys.platform == 'darwin' else 'C:/Windows/Fonts/arial.ttf'
font = lambda s: ImageFont.truetype(FONT, s)

parser = argparse.ArgumentParser()
parser.add_argument('--dir', default=str(HERE))
parser.add_argument('--sprite', default=str(GAME_SPRITE))
parser.add_argument('--title', default='ONSLAUGHT (XIV) BATTLESHIP / PASS 1 BLOCKOUT')
parser.add_argument('--before')
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

iou = None
top_path = src/'top.png'
raw = src/'top-alpha.png'
if top_path.exists() and Image.open(top_path).mode == 'RGBA':
    Image.open(top_path).save(raw)  # keep the transparent original for re-runs
if raw.exists():
    render = Image.open(raw).convert('RGBA')
    W, H = render.size
    k = W/scale  # pixels per model unit
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
    opaque.convert('RGB').save(top_path)
    half = np.dstack([sprite_rgba[..., :3], (sprite_rgba[..., 3]*.5).astype(np.uint8)])
    over = Image.alpha_composite(opaque, Image.fromarray(half))
    diff = np.zeros((H, W, 4), np.uint8)
    diff[sprite_mask & ~model_mask] = (220, 30, 30, 170)
    diff[model_mask & ~sprite_mask] = (20, 190, 220, 170)
    over = Image.alpha_composite(over, Image.fromarray(diff))
    d = ImageDraw.Draw(over)
    d.text((28, 20), 'ONSLAUGHT (XIV) / TOP OVER onslaught_hegemony.png (50%)', font=font(30), fill=(39, 49, 55))
    d.text((28, 60), f'Silhouette IoU {iou:.3f}   red: sprite not covered ({missing:.1%})   '
                     f'cyan: model outside sprite ({extra:.1%})   bow left, port below', font=font(20), fill=(72, 81, 88))
    over.convert('RGB').save(src/'top-overlay.png')
    manifest['silhouette'] = {'iou': round(float(iou), 4), 'sprite_uncovered': round(float(missing), 4),
                              'model_outside_sprite': round(float(extra), 4),
                              'method': 'top render alpha vs sprite alpha (>127), nearest-sampled into the '
                                        f'top camera frame at {k:.2f} px per unit'}
    print(f'IoU {iou:.4f}  uncovered {missing:.3%}  outside {extra:.3%}')

# Formation views: crop each 1080p render round the hull at 1:1.
formation = sorted(src.glob('formation-*.png'))
extent = {}
if formation:
    sheet = Image.new('RGB', (1920, 1080), (8, 9, 12))
    d = ImageDraw.Draw(sheet)
    d.text((28, 18), 'ONSLAUGHT (XIV) / C1 v2 FORMATION DISTANCE - every tile 1:1 from a 1920x1080 render, never rescaled',
           font=font(24), fill=(225, 225, 225))
    d.text((28, 52), f"24 mm stand-in lens, hard sun, no fill; {manifest['metres_per_unit']} m per unit. "
                     'Top row 1.5 km, bottom row 3 km.', font=font(18), fill=(170, 170, 170))
    order = sorted(formation, key=lambda p: ('3km' in p.name, p.name))
    tw, th = 470, 440
    for i, p in enumerate(order):
        im = Image.open(p).convert('RGB')
        a = np.asarray(im).max(axis=2) > 10
        ys, xs = np.where(a)
        if xs.size:
            extent[p.stem] = {'size_px': [int(xs.max()-xs.min()+1), int(ys.max()-ys.min()+1)]}
            mx, my = int((xs.min()+xs.max())/2), int((ys.min()+ys.max())/2)
        else:
            mx, my = 960, 540
        tile = im.crop((mx-tw//2, my-th//2, mx+tw//2, my+th//2))
        x, y = 10+(i % 4)*(tw+8), 100+(i//4)*(th+38)
        sheet.paste(tile, (x, y))
        size = extent.get(p.stem, {}).get('size_px', ['?', '?'])
        d.text((x+6, y+th+6), f"{p.stem.replace('formation-', '')}   {size[0]}x{size[1]} px", font=font(17),
               fill=(200, 200, 200))
    sheet.save(src/'formation-sheet.png', optimize=True)
    manifest['formation_extent'] = extent
    print('formation', json.dumps(extent))

(src/'geometry.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')

panels = [('top-overlay', 'TOP OVER SPRITE / BOW LEFT - PORT BELOW'), ('three-quarter', 'STARBOARD BOW QUARTER'),
          ('starboard-side', 'STARBOARD SIDE / BOW RIGHT'), ('stern', 'STERN')]
if all((src/(n+'.png')).exists() for n, _ in panels):
    sheet = Image.new('RGB', (1920, 1080), BG)
    draw = ImageDraw.Draw(sheet)
    draw.text((28, 15), args.title, font=font(32), fill=(39, 49, 55))
    draw.text((28, 55), 'HEGEMONY XIV  |  DEEP CURVED HULL  |  TWO GUN ARMS, TPC HARDPOINTS  |  24 TURRETS ON PIVOTS  |  '
                        'STERN MACHINERY WELLS  |  SIX DRIVES', font=font(19), fill=(72, 81, 88))
    for (name, label), (x, y) in zip(panels, [(0, 92), (960, 92), (0, 580), (960, 580)]):
        im = Image.open(src/(name+'.png')).convert('RGB').resize((960, 540), Image.LANCZOS).crop((0, 50, 960, 490))
        sheet.paste(im, (x, y))
        draw.text((x+28, y+442), label, font=font(20), fill=(39, 49, 55))
    draw.text((28, 1050), f'Slots and engines from onslaught.ship; heights proposed. '
                          f'Silhouette IoU {iou if iou is None else round(float(iou), 3)}. Geometry review only.',
              font=font(17), fill=(72, 81, 88))
    sheet.save(src/'onslaught-geometry.reference.png', optimize=True)

DETAILS = ['bow-detail', 'arm-detail', 'bridge-detail', 'turret-detail', 'machinery-detail', 'engine-detail']
if args.before:
    before = Path(args.before)
    if all((before/(n+'.png')).exists() and (src/(n+'.png')).exists() for n in DETAILS):
        cmp = Image.new('RGB', (1920, 1080), BG)
        d = ImageDraw.Draw(cmp)
        for i, n in enumerate(DETAILS):
            x, y = (i % 2)*960, 40+(i//2)*346
            for j, folder in enumerate((before, src)):
                im = Image.open(folder/(n+'.png')).convert('RGB').resize((480, 270), Image.LANCZOS)
                cmp.paste(im, (x+j*480, y))
            d.text((x+8, y+274), f'{n.upper()}   before | after', font=font(18), fill=(39, 49, 55))
        d.text((12, 8), 'ONSLAUGHT (XIV) / BEFORE | AFTER (same cameras)', font=font(24), fill=(39, 49, 55))
        cmp.save(src/'before-after.png', optimize=True)
