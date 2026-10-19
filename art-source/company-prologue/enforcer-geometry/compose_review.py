"""Top-view sprite overlay and silhouette IoU for the Enforcer build.

  python3 compose_review.py [--variant merchant|pirate] [--manifest]
Reads build/enforcer/<variant>/top.png (rendered by build_enforcer.py with a
transparent film, bow right, port up, ortho scale 250 across 1920 px) and the
variant's sprite from the install, lays the sprite into the same frame and
compares alpha masks. Writes overlay.png (sprite under a cyan model outline)
beside the render, and with --manifest stores the IoU under 'silhouette' in
the tracked geometry json. Needs Pillow + numpy.
"""
from pathlib import Path
import argparse
import json
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent))
from starsector_path import GAME_CORE as GAME  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--variant', default='merchant', choices=('merchant', 'pirate'))
ap.add_argument('--manifest', action='store_true')
args = ap.parse_args()
OUT = HERE.parents[2]/'build'/'enforcer'/args.variant
SPRITE = {'merchant': 'graphics/ships/enforcer/enforcer_base.png',
          'pirate': 'graphics/ships/enforcer/enforcer_pirates.png'}[args.variant]
W, H, ORTHO = 1920, 1080, 250.0
PPU = W/ORTHO

top = Image.open(OUT/'top.png').convert('RGBA')
assert top.size == (W, H), top.size
model = np.array(top)[..., 3] > 128

sp = Image.open(GAME/SPRITE).convert('RGBA').rotate(-90, expand=True)   # bow right, port up
n = sp.size[0]
big = sp.resize((round(n*PPU), round(n*PPU)), Image.NEAREST)
frame = Image.new('RGBA', (W, H), (0, 0, 0, 0))
# Sprite column (after rotation) k covers X k-67..k-66; row j covers Y 68-j..67-j.
frame.alpha_composite(big, (round(W/2-67*PPU), round(H/2-68*PPU)))
sprite = np.array(frame)[..., 3] > 128

inter, union = (model & sprite).sum(), (model | sprite).sum()
iou = inter/union
miss = (sprite & ~model).sum()/sprite.sum()
extra = (model & ~sprite).sum()/sprite.sum()
print(f'IoU {iou:.3f}  sprite uncovered {miss:.1%}  model outside sprite {extra:.1%}')

bg = Image.new('RGBA', (W, H), (34, 36, 42, 255))
bg.alpha_composite(frame)
edge = Image.fromarray((model*255).astype(np.uint8)).filter(ImageFilter.FIND_EDGES)
cyan = Image.new('RGBA', (W, H), (0, 255, 255, 255))
bg.paste(cyan, (0, 0), edge)
diff = np.zeros((H, W, 4), np.uint8)
diff[sprite & ~model] = (255, 60, 60, 200)
diff[model & ~sprite] = (255, 220, 0, 140)
bg2 = Image.new('RGBA', (W, H), (34, 36, 42, 255))
bg2.alpha_composite(Image.fromarray(np.where(model[..., None], (90, 96, 110, 255), (0, 0, 0, 0)).astype(np.uint8)))
bg2.alpha_composite(Image.fromarray(diff))
for im, name, note in ((bg, 'overlay.png', 'sprite under model outline (cyan)'),
                       (bg2, 'overlay-diff.png', 'red: sprite not covered; yellow: model outside sprite')):
    d = ImageDraw.Draw(im)
    d.text((24, 20), f'Enforcer ({args.variant}) top view vs {Path(SPRITE).name}: IoU {iou:.3f}  '
                     f'uncovered {miss:.1%}  outside {extra:.1%}   [{note}]', fill=(255, 255, 255, 255))
    im.convert('RGB').save(OUT/name)

if args.manifest:
    tracked = HERE/('geometry-p.json' if args.variant == 'pirate' else 'geometry.json')
    g = json.loads(tracked.read_text(encoding='utf-8'))
    g['silhouette'] = {'iou': round(float(iou), 3), 'sprite_uncovered': round(float(miss), 3),
                       'model_outside_sprite': round(float(extra), 3), 'sprite': SPRITE,
                       'method': 'compose_review.py: top.png alpha vs sprite alpha at 7.68 px/unit'}
    tracked.write_text(json.dumps(g, indent=2)+'\n', encoding='utf-8')
