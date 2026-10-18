"""The shape gauntlet: compare the model's top view with the sprite part by part, not just by outline.

  python3 gauntlet.py [--dir DIR] [--sprite PATH]      (Pillow + numpy)
Needs DIR/top-classes.png from build_paragon.py --views top-classes and
DIR/geometry.json. Silhouette IoU says the outline is right and nothing about
what lies inside it. The sprite separates cleanly into three tones, and each
tone is a kind of part: blue-grey skin (armour), white (the command deck, the
bow module, the reactor domes) and dark (the bore lining, turret wells,
trenches, machinery, and every steep face, which the sprite shades dark). The
build renders its top view with the same three classes (from each object's
material, and steep faces as dark), and this scores each class's IoU and draws:
  gauntlet.png        1920x1080: sprite classes | model classes | disagreement
  gauntlet-edges.png  1920x1080: the sprite at 2.9x with the model's class
                      boundaries over it, for lining up internal shapes
The scores go into geometry.json under 'gauntlet'. Run it every iteration; a
change that moves a part should move its score, and a part is placed when its
edges sit on the sprite's.
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
FONT = '/System/Library/Fonts/Supplemental/Arial.ttf' if sys.platform == 'darwin' else 'C:/Windows/Fonts/arial.ttf'
font = lambda s: ImageFont.truetype(FONT, s)
CLASSES = {1: 'skin', 2: 'white', 3: 'dark'}
PAL = np.array([[22, 22, 26], [120, 150, 175], [240, 240, 240], [48, 48, 54]], np.uint8)
# The build's class colours (emission, linear 0-1 -> sRGB in the PNG).
MODEL_RGB = {1: (0, 0, 255), 2: (255, 255, 255), 3: (255, 0, 0)}

parser = argparse.ArgumentParser()
parser.add_argument('--dir', default=str(HERE))
parser.add_argument('--sprite', default=str(GAME/'graphics'/'ships'/'paragon.png'))
args = parser.parse_args()
src = Path(args.dir)


def box(a, k):
    """k x k mean filter."""
    p = k//2
    a = np.pad(a, p, mode='edge')
    c = a.cumsum(0).cumsum(1)
    c = np.pad(c, ((1, 0), (1, 0)))
    return (c[k:, k:]-c[:-k, k:]-c[k:, :-k]+c[:-k, :-k])/(k*k)


def sprite_classes(path):
    a = np.array(Image.open(path).convert('RGBA')).astype(float)
    m = a[..., 3] > 127
    rgb = a[..., :3]
    lum = rgb.mean(2)
    br = rgb[..., 2]-rgb[..., 0]
    dark = m & (lum < 80)
    white = m & (lum >= 150) & (br < 12)
    skin = m & ~dark & ~white
    votes = np.stack([box(c.astype(float), 7) for c in (skin, white, dark)])
    lab = votes.argmax(0)+1
    lab[~m] = 0
    return lab, a


manifest = json.loads((src/'geometry.json').read_text(encoding='utf-8'))
cam = next(c for c in manifest['cameras'] if c['name'] == 'top')
cx, cy = cam['position'][:2]
scale = cam['orthographic_scale']
slab, sprite_rgba = sprite_classes(args.sprite)
sh, sw = slab.shape
ccol, crow_up = manifest['sprite']['center']
crow = sh-crow_up
render = np.array(Image.open(src/'top-classes.png').convert('RGBA')).astype(int)
H, W = render.shape[:2]
k = W/scale
# Sample the model's class render at every sprite pixel centre.
rows, cols = np.mgrid[0:sh, 0:sw]
X = crow-rows-.5
Y = ccol-cols-.5
u = np.clip(((cx-X)*k+W/2).astype(int), 0, W-1)
v = np.clip(((Y-cy)*k+H/2).astype(int), 0, H-1)
px = render[v, u]
mlab = np.zeros((sh, sw), int)
alpha = px[..., 3] > 127
for c, rgb in MODEL_RGB.items():
    d = np.abs(px[..., :3]-np.array(rgb)).sum(-1)
    mlab[alpha & (d < 200)] = c
mlab[alpha & (mlab == 0)] = 1
scores = {}
for c, name in CLASSES.items():
    a, b = slab == c, mlab == c
    scores[name] = round(float((a & b).sum()/max(1, (a | b).sum())), 3)
both = (slab > 0) | (mlab > 0)
scores['agreement'] = round(float(((slab == mlab) & both).sum()/both.sum()), 3)
scores['silhouette'] = round(float(((slab > 0) & (mlab > 0)).sum()/both.sum()), 3)
print('gauntlet', json.dumps(scores))

sheet = Image.new('RGB', (1920, 1080), (222, 223, 223))
d = ImageDraw.Draw(sheet)
d.text((24, 14), 'PARAGON / SHAPE GAUNTLET - sprite classes | model classes | disagreement', font=font(26), fill=(39, 49, 55))
d.text((24, 50), '   '.join(f'{n} IoU {s}' for n, s in scores.items()) +
       '    (skin = blue-grey armour, white = command deck / bow module / domes, dark = lining, wells, trenches, steep faces)',
       font=font(17), fill=(72, 81, 88))
tile = lambda lab: Image.fromarray(PAL[lab]).rotate(90, expand=True)  # bow left like the top render
dis = np.zeros((sh, sw, 3), np.uint8)
dis[(slab > 0) | (mlab > 0)] = (200, 200, 200)
dis[(slab != mlab) & (slab > 0) & (mlab > 0)] = (230, 160, 30)
dis[(slab > 0) & (mlab == 0)] = (220, 30, 30)
dis[(slab == 0) & (mlab > 0)] = (20, 190, 220)
for i, im in enumerate((tile(slab), tile(mlab), Image.fromarray(dis).rotate(90, expand=True))):
    im = im.resize((int(im.width*1.68), int(im.height*1.68)), Image.NEAREST)
    sheet.paste(im, (20+i*632, 110))
    d.text((24+i*632, 110+im.height+8), ('SPRITE', 'MODEL', 'DISAGREEMENT (orange: wrong class, red: missing, cyan: extra)')[i],
           font=font(18), fill=(39, 49, 55))
sheet.save(src/'gauntlet.png', optimize=True)
# Edge sheet: the sprite at 2.9x with the model's class boundaries drawn over it.
edge = np.zeros((sh, sw), bool)
edge[:-1] |= mlab[:-1] != mlab[1:]
edge[:, :-1] |= mlab[:, :-1] != mlab[:, 1:]
bg = Image.new('RGBA', (sw, sh), (25, 25, 30, 255))
bg.alpha_composite(Image.fromarray(sprite_rgba.astype(np.uint8)))
big = bg.convert('RGB').rotate(90, expand=True).resize((int(sh*2.9), int(sw*2.9)), Image.LANCZOS)
e = Image.fromarray((edge*255).astype(np.uint8)).rotate(90, expand=True).resize(big.size, Image.NEAREST)
big.paste(Image.new('RGB', big.size, (0, 255, 200)), (0, 0), e)
es = Image.new('RGB', (1920, 1080), (25, 25, 30))
es.paste(big, ((1920-big.width)//2, 1080-big.height-8))
de = ImageDraw.Draw(es)
de.text((24, 14), 'PARAGON / GAUNTLET EDGES - the model\'s part boundaries (cyan) over paragon.png, bow left',
        font=font(24), fill=(225, 225, 225))
es.save(src/'gauntlet-edges.png', optimize=True)
manifest['gauntlet'] = {**scores, 'method': 'per-class IoU on the sprite grid: sprite tones (skin / white / dark, '
                        '7 px majority) vs the build top-classes render (material class; faces steeper than '
                        '57 deg as dark)'}
(src/'geometry.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
