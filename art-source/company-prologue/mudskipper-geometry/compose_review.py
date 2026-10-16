"""Lay the vanilla sprite over the top render, measure silhouette IoU, and
assemble a 1920x1080 review sheet. Ordinary Python + Pillow, run after
build_reference.py.

Top-view mapping (build_reference.py's top camera): 16 px per hull unit,
bow left, port down, hull point (3, 0) at the image centre. A sprite pixel
(c, r) covers hull X 44-r..45-r and Y 31-c..32-c (64x84 sprite, .ship centre
32,39 from bottom-left), so the sprite is turned 90 degrees and scaled 16x.
"""
from pathlib import Path
import argparse
import json
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--renders', help='render folder (default renders/); a scratch folder gets only the overlay')
args = ap.parse_args()
R = Path(args.renders).resolve() if args.renders else HERE/'renders'
PACKAGE = R == HERE/'renders'
SPRITE = Path(r'C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/graphics/ships/mudskipper/mudskipper.png')
PPU, CX, CY, W, H = 16, 3, 0, 1920, 1080
BG = (189, 193, 198)
font = lambda n: ImageFont.truetype('C:/Windows/Fonts/arial.ttf', n)

sprite = Image.open(SPRITE).convert('RGBA').rotate(90, expand=True)
sprite = sprite.resize((sprite.width*PPU, sprite.height*PPU), Image.NEAREST)
# Sprite left edge is hull X 45 and top edge hull Y -32 (port down).
ox, oy = round(W/2 + (CX-45)*PPU), round(H/2 + (-32-CY)*PPU)
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

# top.png on the review background (the render itself keeps its alpha).
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
d.text((30, 24), 'MUDSKIPPER / TOP, BOW LEFT, PORT DOWN, SPRITE 50% OVER MODEL', font=font(28), fill=(30, 36, 40))
d.text((30, 62), f'Silhouette IoU {iou:.3f} at 16 px/unit   magenta = sprite edge   cyan = model edge', font=font(22), fill=(30, 36, 40))
over.convert('RGB').save(R/'top-sprite-overlay.png')

print(json.dumps(stats))
if not PACKAGE:
    raise SystemExit
# Review sheet: four views at half size.
sheet = Image.new('RGB', (W, H), (222, 223, 223))
sd = ImageDraw.Draw(sheet)
for (name, label), (x, y) in zip([('top-sprite-overlay', f'TOP + SPRITE (IoU {iou:.3f})'), ('port-side', 'PORT SIDE'),
                                  ('bow', 'BOW'), ('port-quarter', 'PORT BOW QUARTER')],
                                 [(0, 0), (960, 0), (0, 540), (960, 540)]):
    sheet.paste(Image.open(R/(name+'.png')).convert('RGB').resize((960, 540), Image.LANCZOS), (x, y))
    sd.text((x+16, y+506), label, font=font(20), fill=(30, 36, 40))
sheet.save(HERE/'mudskipper-geometry.reference.png', optimize=True)

gj = HERE/'geometry.json'
if gj.exists():
    manifest = json.loads(gj.read_text(encoding='utf-8'))
    manifest['silhouette'] = stats
    gj.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
