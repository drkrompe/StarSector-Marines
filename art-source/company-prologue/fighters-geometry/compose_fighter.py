"""Top-view sprite overlay, silhouette IoU and the 1920x1080 review sheet for one fighter.

Run with ordinary Python (Pillow + numpy) after build_fighter.py:
  python compose_fighter.py --fighter ID [--dir DIR] [--sprite PATH]
The top render's alpha is the model silhouette. The vanilla sprite is laid
into the same frame through the top camera's projection (nose left, port
below) and the two masks are compared pixel for pixel. Writes
top-overlay.png, keeps the transparent render as top-alpha.png, records
the IoU in geometry.json and composes <id>-geometry.reference.png.
"""
from pathlib import Path
import argparse
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
GAME = Path(r'C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core')
BG = (222, 223, 223)
INK, SOFT = (39, 49, 55), (72, 81, 88)

parser = argparse.ArgumentParser()
parser.add_argument('--fighter', required=True)
parser.add_argument('--dir')
parser.add_argument('--sprite', help='override the sprite the IoU is measured against')
args = parser.parse_args()
src = Path(args.dir or HERE/args.fighter)
manifest = json.loads((src/'geometry.json').read_text(encoding='utf-8'))
font = lambda s: ImageFont.truetype('C:/Windows/Fonts/arial.ttf', s)
name = manifest['hull']['name']
cam = manifest['cameras']['top']
cx, cy = cam['position'][:2]
scale = cam['orthographic_scale']
sprite_path = Path(args.sprite) if args.sprite else GAME/manifest['hull']['source']['sprite']
sprite = Image.open(sprite_path).convert('RGBA')
sw, sh = sprite.size
ccol, crow_up = manifest['sprite']['center']
crow = sh - crow_up

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
d.text((28, 20), f'{name.upper()} BLOCKOUT / TOP OVER {sprite_path.name} (50%)', font=font(30), fill=INK)
d.text((28, 60), f'Silhouette IoU {iou:.3f}   red: sprite not covered ({missing:.1%})   '
                 f'cyan: model outside sprite ({extra:.1%})   nose left, port below', font=font(20), fill=SOFT)
over.convert('RGB').save(src/'top-overlay.png')
manifest['silhouette'] = {'iou': round(float(iou), 4), 'sprite_uncovered': round(float(missing), 4),
                          'model_outside_sprite': round(float(extra), 4), 'sprite': sprite_path.name,
                          'method': 'top render alpha vs sprite alpha (>127), nearest-sampled into the '
                                    f'top camera frame at {k:.2f} px per unit'}
(src/'geometry.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
print(f'{name} IoU {iou:.4f}  uncovered {missing:.3%}  outside {extra:.3%}')

panels = [('top-overlay', 'TOP OVER SPRITE / NOSE LEFT - PORT BELOW'), ('three-quarter', 'PORT BOW QUARTER'),
          ('port-side', 'PORT SIDE / NOSE LEFT, 1.8 m FIGURE'), ('front', 'NOSE ON')]
if not all((src/(n+'.png')).exists() for n, _ in panels):
    raise SystemExit('review sheet skipped: not every standard view was rendered')
sheet = Image.new('RGB', (1920, 1080), BG)
draw = ImageDraw.Draw(sheet)
m = manifest['metres_per_unit']
sz = manifest['scale']['size_m']
rng = manifest['scale'].get('range')
draw.text((28, 15), f'{name.upper()} / CINEMATIC GEOMETRY - BLOCKOUT', font=font(32), fill=INK)
draw.text((28, 55), f"{manifest['scale'].get('summary', '')}  |  {sz[0]} x {sz[1]} x {sz[2]} m AT {m} m/UNIT"
                    + (f' (RANGE {rng[0]}-{rng[1]})' if rng else ''), font=font(19), fill=SOFT)
def panel(n, label, x, y, w, h):
    im = Image.open(src/(n+'.png')).convert('RGB')
    s = max(w/im.width, (h+60)/im.height)
    im = im.resize((round(im.width*s), round(im.height*s)), Image.LANCZOS)
    ox, oy = (im.width-w)//2, (im.height-h)//2
    sheet.paste(im.crop((ox, oy, ox+w, oy+h)), (x, y))
    draw.text((x+20, y+h+6), label, font=font(19), fill=INK)


panel('top-overlay', panels[0][1], 0, 92, 960, 440)
panel('three-quarter', panels[1][1], 960, 92, 960, 440)
crew = (src/'cockpit-check.png').exists()
parked = manifest.get('parked_check') if (src/'parked.png').exists() else None
bw = (800, 560, 560) if crew or parked else (960, 960, 0)
panel('port-side', panels[2][1], 0, 580, bw[0], 430)
panel('front', panels[3][1], bw[0], 580, bw[1], 430)
if crew:
    fit = manifest['scale'].get('cockpit_fit') or []
    clear = ', '.join(f"{f['head_clearance_under_canopy_m']} m" for f in fit)
    panel('cockpit-check', f'CUTAWAY: 1.8 m PILOT CLEARS GLASS BY {clear}', bw[0]+bw[1], 580, bw[2], 430)
elif parked:
    panel('parked', f"PARKED ON {len(parked['feet'])} FEET: BODY CLEARS DECK BY {parked['body_clearance_m']:.2f} m",
          bw[0]+bw[1], 580, bw[2], 430)
source = manifest['hull']['source']
master = f"{Path(source['shape_reference']).parent.name}/{Path(source['shape_reference']).name}"
shape = (f"plan from {Path(source['plan_reference']).name}, sections from {master}" if source.get('plan_reference')
         else f'shape from {master}')
draw.text((28, 1050), f"Slots and engines from {source['ship']}; {shape}; heights proposed. "
                      f'Silhouette IoU {iou:.3f}.', font=font(17), fill=SOFT)
sheet.save(src/f'{args.fighter}-geometry.reference.png', optimize=True)
