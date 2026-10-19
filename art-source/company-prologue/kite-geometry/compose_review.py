"""Lay the vanilla sprite over the top render, measure silhouette IoU, and
assemble the 1920x1080 review sheets. Ordinary Python + Pillow, run after
build_kite.py.

Top-view mapping (build_kite.py's top camera): 12 px per hull unit, bow
left, port down, hull point (5, 0) at the image centre. A sprite pixel
(c, r) covers hull X 37-r..38-r and Y 40-c..41-c (82x66 sprite, .ship centre
41,28 from bottom-left), so the sprite is turned 90 degrees and scaled 12x.
"""
from pathlib import Path
import argparse
import json
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent))
from review_font import review_font as font  # noqa: E402
from starsector_path import GAME_CORE  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--renders', help='render folder (default renders/); a scratch folder gets only the overlay')
ap.add_argument('--before', help='folder of close-up renders to compare against (default reference-history/v1-blockout)')
args = ap.parse_args()
R = Path(args.renders).resolve() if args.renders else HERE/'renders'
PACKAGE = R == HERE/'renders'
SPRITE = GAME_CORE/'graphics/ships/aeroshuttle/aeroshuttle_pirates.png'
PPU, CX, CY, W, H = 12, 5, 0, 1920, 1080
BG = (189, 193, 198)
INK = (30, 36, 40)

sprite = Image.open(SPRITE).convert('RGBA').rotate(90, expand=True)
sprite = sprite.resize((sprite.width*PPU, sprite.height*PPU), Image.NEAREST)
# Sprite left edge is hull X 38 and top edge hull Y -41 (port down).
ox, oy = round(W/2 + (CX-38)*PPU), round(H/2 + (-41-CY)*PPU)
sprite_layer = Image.new('RGBA', (W, H))
sprite_layer.paste(sprite, (ox, oy))

top = Image.open(R/'top.png').convert('RGBA')
model_mask = top.getchannel('A').point(lambda a: 255 if a >= 128 else 0)
sprite_mask = sprite_layer.getchannel('A').point(lambda a: 255 if a >= 128 else 0)
inter = ImageChops.multiply(model_mask, sprite_mask).histogram()[255]
union = ImageChops.lighter(model_mask, sprite_mask).histogram()[255]
model_only = ImageChops.subtract(model_mask, sprite_mask).histogram()[255]
sprite_only = ImageChops.subtract(sprite_mask, model_mask).histogram()[255]
iou = inter/union
stats = {'iou': round(iou, 4), 'pixels_per_unit': PPU, 'alpha_threshold': 128,
         'model_only_area_units2': round(model_only/PPU**2, 1),
         'sprite_only_area_units2': round(sprite_only/PPU**2, 1)}

plain = Image.new('RGBA', (W, H), BG + (255,))
plain.alpha_composite(top)
plain.convert('RGB').save(R/'top-composited.png')

# Overlay: model, sprite at half opacity, sprite outline magenta, model cyan.
over = plain.copy()
half = sprite_layer.copy()
half.putalpha(sprite_layer.getchannel('A').point(lambda a: a*115//255))
over.alpha_composite(half)
d = ImageDraw.Draw(over)
for mask, colour in ((sprite_mask, (255, 0, 200, 255)), (model_mask, (0, 220, 255, 255))):
    edge = ImageChops.subtract(mask.filter(ImageFilter.MaxFilter(3)), mask)
    over.paste(Image.new('RGBA', (W, H), colour), (0, 0), edge)
d.text((30, 24), 'KITE (P) / TOP, BOW LEFT, PORT DOWN, SPRITE 45% OVER MODEL', font=font(28), fill=INK)
d.text((30, 62), f'Silhouette IoU {iou:.3f} at {PPU} px/unit   magenta = sprite edge   cyan = model edge',
       font=font(22), fill=INK)
over.convert('RGB').save(R/'top-sprite-overlay.png')

# Sprite beside the model's top view, same scale, for reading detail.
side = Image.new('RGBA', (W, H), BG + (255,))
s2 = Image.open(SPRITE).convert('RGBA').rotate(90, expand=True)
s2 = s2.resize((s2.width*11, s2.height*11), Image.NEAREST)
side.alpha_composite(s2, (40, (H - s2.height)//2))
t2 = top.crop((ox, oy, ox + sprite.width, oy + sprite.height)).resize((s2.width, s2.height), Image.LANCZOS)
side.alpha_composite(t2, (W - 40 - s2.width, (H - s2.height)//2))
ImageDraw.Draw(side).text((30, 24), 'KITE (P) / SPRITE (LEFT) AND MODEL TOP VIEW (RIGHT), BOW LEFT',
                          font=font(28), fill=INK)
side.convert('RGB').save(R/'top-sprite-beside.png')

print(json.dumps(stats))
if not PACKAGE:
    raise SystemExit
gj = HERE/'geometry.json'
if gj.exists():
    manifest = json.loads(gj.read_text(encoding='utf-8'))
    manifest['silhouette'] = stats
    gj.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')

# Pass 2 before/after: the shared close-up cameras, blockout against now.
BEFORE = Path(args.before).resolve() if args.before else HERE/'reference-history'/'v1-blockout'
CLOSE = ['bow-detail', 'cockpit-detail', 'sponson-detail', 'turret-detail', 'stern-detail', 'underside-detail']
if all((BEFORE/(c+'.png')).exists() and (R/(c+'.png')).exists() for c in CLOSE):
    sheet = Image.new('RGB', (W, H), (222, 223, 223))
    sd = ImageDraw.Draw(sheet)
    for i, c in enumerate(CLOSE):
        row, pair = divmod(i, 2)
        for k, (src, tag) in enumerate(((BEFORE, 'before'), (R, 'now'))):
            x, y = (pair*2+k)*480, row*360
            sheet.paste(Image.open(src/(c+'.png')).convert('RGB').resize((480, 270), Image.LANCZOS), (x, y+45))
            sd.text((x+10, y+12), f'{c.upper()}  {tag}', font=font(18), fill=INK)
    sheet.save(R/'before-after.png', optimize=True)
