"""Top-view sprite overlay, silhouette IoU and the 1920x1080 review sheet.

Run with ordinary Python (Pillow + numpy) after build_legion.py:
  python compose_review.py [--dir DIR] [--sprite PATH]
The top render's alpha is the model silhouette. The vanilla sprite is laid
into the same frame through the top camera's projection (bow left, port
below) and the two masks are compared pixel for pixel. Writes
top-overlay.png, replaces top.png with an opaque copy for review, records
the IoU in geometry.json and composes legion-geometry.reference.png. C2 cabin views
(c2-*.png, transparent) are laid over a flat Jangala stand-in below the
horizon with the window aperture drawn, and the abeam frame's silhouette
extent is recorded against c2-layout.md's 1636x418 px target.
"""
from pathlib import Path
import argparse
import json
import math
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent/'second-wind-cinematic'/'patrol-abeam'))
import c2_core
GAME_SPRITE = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core'
                   r'\graphics\ships\legion\legion_xiv.png')
BG = (222, 223, 223)

parser = argparse.ArgumentParser()
parser.add_argument('--dir', default=str(HERE))
parser.add_argument('--sprite', default=str(GAME_SPRITE))
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
d.text((28, 20), 'LEGION (XIV) / TOP OVER legion_xiv.png (50%)', font=font(30), fill=(39, 49, 55))
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

# C2 cabin frames over a Jangala stand-in: black space above the horizon,
# flat daylit planet below it, the window aperture outlined.
def horizon_y(x):
    dip = math.radians(c2_core.horizon_dip_deg())
    return c2_core.project((0, 1e6*math.cos(dip), -1e6*math.sin(dip)))[1]


c2_frames = {}
for name in ('c2-entry', 'c2-abeam', 'c2-exit'):
    raw_path = src/(name+'-alpha.png')
    if (src/(name+'.png')).exists() and Image.open(src/(name+'.png')).mode == 'RGBA':
        Image.open(src/(name+'.png')).save(raw_path)
    if not raw_path.exists():
        continue
    im = Image.open(raw_path).convert('RGBA')
    w, h = im.size
    hy = horizon_y(w/2)*h/1080
    bgarr = np.zeros((h, w, 4), np.uint8)
    rows = np.arange(h)[:, None]
    t = np.clip((rows-hy)/(h-hy), 0, 1)
    planet = np.dstack([46+30*t, 64+34*t, 92+30*t])
    bgarr[..., :3] = np.where(rows >= hy, 1, 0)[..., None]*np.broadcast_to(planet, (h, w, 3))
    bgarr[..., 3] = 255
    frame = Image.alpha_composite(Image.fromarray(bgarr), im)
    d = ImageDraw.Draw(frame)
    ap = [(x*w/1920, y*h/1080) for x, y in c2_core.APERTURE]
    d.line(ap+[ap[0]], fill=(255, 210, 90), width=2)
    a = np.array(im)[..., 3] > 127
    if a.any():
        ys, xs = np.where(a)
        box = [int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())]
        c2_frames[name] = {'bbox_px': box, 'size_px': [box[2]-box[0]+1, box[3]-box[1]+1],
                           'touches_frame_edge': bool(xs.min() == 0 or xs.max() == w-1 or ys.min() == 0 or ys.max() == h-1)}
        d.rectangle(box, outline=(90, 220, 255), width=1)
    d.text((24, h-60), f'{name.upper()}  C2 cabin camera (26 mm, 18.6 deg down) / c2_core sun, planet-shine, no ambient',
           font=font(20), fill=(230, 230, 230))
    if name in c2_frames:
        d.text((24, h-32), f"silhouette {c2_frames[name]['size_px'][0]}x{c2_frames[name]['size_px'][1]} px "
                           f"(c2-layout target abeam 1636x418)   gold: window glass", font=font(18), fill=(200, 200, 200))
    frame.convert('RGB').save(src/(name+'.png'))
if c2_frames:
    manifest['c2_frame'] = c2_frames
    (src/'geometry.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    print('C2', json.dumps(c2_frames))

# Review sheet: four views at half size (skipped unless all four exist).
sheet = Image.new('RGB', (1920, 1080), BG)
draw = ImageDraw.Draw(sheet)
draw.text((28, 15), 'LEGION (XIV) BATTLECARRIER / GEOMETRY BLOCKOUT', font=font(32), fill=(39, 49, 55))
draw.text((28, 55), 'XIV BATTLEGROUP  |  DEEP TORSO, WING ARMS  |  FOUR UNDER-WING HANGARS  |  FIFTEEN TURRETS ON PIVOTS  |  TEN DRIVES',
          font=font(19), fill=(72, 81, 88))
panels = [('top-overlay', 'TOP OVER SPRITE / BOW LEFT - PORT BELOW'), ('three-quarter', 'STARBOARD BOW QUARTER'),
          ('starboard-side', 'STARBOARD SIDE / BOW RIGHT'), ('c2-abeam', 'C2 ABEAM: CABIN CAMERA, C2 SUN')]
if not all((src/(n+'.png')).exists() for n, _ in panels):
    raise SystemExit('review sheet skipped: not every standard view was rendered')
for (name, label), (x, y) in zip(panels, [(0, 92), (960, 92), (0, 580), (960, 580)]):
    im = Image.open(src/(name+'.png')).convert('RGB').resize((960, 540), Image.LANCZOS).crop((0, 50, 960, 490))
    sheet.paste(im, (x, y))
    draw.text((x+28, y+442), label, font=font(20), fill=(39, 49, 55))
draw.text((28, 1050), f'Slots, bays and engines from legion.ship; heights proposed. Silhouette IoU {iou:.3f}. Geometry review only.',
          font=font(17), fill=(72, 81, 88))
sheet.save(src/'legion-geometry.reference.png', optimize=True)

# Before/after close-ups against the accepted blockout, when both exist.
before = HERE/'reference-history'/'v2-blockout'
DETAILS = ['bow-detail', 'bridge-detail', 'turret-detail', 'flight-deck-detail', 'aft-deck-detail', 'flank-detail',
           'engine-detail', 'stern-detail']
if all((before/(n+'.png')).exists() and (src/(n+'.png')).exists() for n in DETAILS):
    cmp = Image.new('RGB', (1920, 1080), BG)
    d = ImageDraw.Draw(cmp)
    for i, n in enumerate(DETAILS[:6]):
        x, y = (i % 2)*960, 40+(i//2)*346
        for j, folder in enumerate((before, src)):
            im = Image.open(folder/(n+'.png')).convert('RGB').resize((480, 270), Image.LANCZOS)
            cmp.paste(im, (x+j*480, y))
        d.text((x+8, y+274), f'{n.upper()}   blockout | pass 2', font=font(18), fill=(39, 49, 55))
    d.text((12, 8), 'LEGION (XIV) / PASS 2 DETAILING - BEFORE | AFTER (same cameras)', font=font(24), fill=(39, 49, 55))
    cmp.save(src/'pass2-before-after.png', optimize=True)
