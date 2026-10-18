"""Lay the vanilla sprite over the top render, measure silhouette IoU, and
assemble the 1920x1080 review sheet. Ordinary Python + Pillow, run after
build_hermes.py.

Top-view mapping (build_hermes.py's top camera): 16 px per hull unit, bow
left, port down, hull point (1, 0) at the image centre. A sprite pixel
(c, r) covers hull X 42-r..43-r and Y 27-c..28-c (56x82 sprite, .ship centre
28,39 from bottom-left), so the sprite is turned 90 degrees and scaled 16x.
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
SPRITE = Path(r'C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/graphics/ships/hermes/hermes_base.png')
PPU, CX, CY, W, H = 16, 1, 0, 1920, 1080
BG = (189, 193, 198)
font = lambda n: ImageFont.truetype('C:/Windows/Fonts/arial.ttf', n)

sprite = Image.open(SPRITE).convert('RGBA').rotate(90, expand=True)
sprite = sprite.resize((sprite.width*PPU, sprite.height*PPU), Image.NEAREST)
# Sprite left edge is hull X 43 and top edge hull Y -28 (port down).
ox, oy = round(W/2 + (CX-43)*PPU), round(H/2 + (-28-CY)*PPU)
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
d.text((30, 24), 'HERMES / TOP, BOW LEFT, PORT DOWN, SPRITE 45% OVER MODEL', font=font(28), fill=(30, 36, 40))
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
sheet.save(HERE/'hermes-geometry.reference.png', optimize=True)

gj = HERE/'geometry.json'
if gj.exists():
    manifest = json.loads(gj.read_text(encoding='utf-8'))
    manifest['silhouette'] = stats
    gj.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')

# Second Wind bay fit sheet (bay_fit.py writes renders/bay-fit-*.png and bay-fit.json).
fitj = R/'bay-fit.json'
if fitj.exists():
    fit = json.loads(fitj.read_text(encoding='utf-8'))
    sheet = Image.new('RGB', (W, H), (222, 223, 223))
    sd = ImageDraw.Draw(sheet)
    panels = [('bay-fit-port', 'PORT BAY FROM OUTBOARD (TRUE RELATIVE SCALE)'), ('bay-fit-quarter', 'SECOND WIND, PORT QUARTER'),
              ('bay-fit-plan', 'PLAN SECTION UNDER THE BAY CEILING (BOW RIGHT, MOUTH UP)'), ('bay-fit-section', 'SECTION AT THE FORWARD JAMB, LOOKING AFT')]
    for (name, label), (x, y) in zip(panels, [(0, 0), (960, 0), (0, 540), (960, 540)]):
        sheet.paste(Image.open(R/(name+'.png')).convert('RGB').resize((960, 540), Image.LANCZOS), (x, y))
        sd.rectangle((x, y+500, x+960, y+540), fill=(222, 223, 223))
        sd.text((x+16, y+508), label, font=font(20), fill=(30, 36, 40))
    b, h, m = fit['bay_clear_m'], fit['hermes_m'], fit['margins_m']
    hd = next(v for k, v in h.items() if k.startswith('height'))
    lines = [f"Hermes {fit['metres_per_unit']['hermes']} m/u in Second Wind's port bay ({fit['metres_per_unit']['second_wind']} m/u)",
             f"bay clear {b['length']} x {b['depth']} x {b['clear_height']} m (L x depth x H)",
             f"Hermes {h['length']} x {h['beam']} x {hd} m (gear down)",
             f"margins: {m['fore_and_aft_each_end']} m each end, {m['inboard_wall']} m wall, "
             f"{m['outboard_mouth']} m mouth, {m['overhead']} m overhead",
             f"door closed: depth {fit.get('depth_with_door_closed_m')} m, "
             f"overhang {fit.get('overhang_past_door_line_m')} m -> {'fits' if fit.get('fits_with_door_closed') else 'DOES NOT fit'}"]
    box_w = 700
    sd.rectangle((W-box_w-20, 20, W-20, 20+30*len(lines)+20), fill=(245, 245, 240), outline=(30, 36, 40))
    for i, t in enumerate(lines):
        sd.text((W-box_w-8, 30+30*i), t, font=font(19), fill=(30, 36, 40))
    sheet.save(HERE/'hermes-bay-fit.reference.png', optimize=True)
    if gj.exists():
        manifest = json.loads(gj.read_text(encoding='utf-8'))
        manifest['second_wind_bay_fit'] = fit
        gj.write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')

# Pass 2 before/after: the shared close-up cameras, blockout against now.
BEFORE = HERE/'reference-history'/'v1-blockout'
CLOSE = ['bow-detail', 'cockpit-detail', 'turret-detail', 'aft-turret-detail', 'stern-detail', 'underside-detail']
if all((BEFORE/(c+'.png')).exists() and (R/(c+'.png')).exists() for c in CLOSE):
    sheet = Image.new('RGB', (W, H), (222, 223, 223))
    sd = ImageDraw.Draw(sheet)
    for i, c in enumerate(CLOSE):
        row, pair = divmod(i, 2)
        for k, (src, tag) in enumerate(((BEFORE, 'blockout'), (R, 'pass 2'))):
            x, y = (pair*2+k)*480, row*360
            sheet.paste(Image.open(src/(c+'.png')).convert('RGB').resize((480, 270), Image.LANCZOS), (x, y+45))
            sd.text((x+10, y+12), f'{c.upper()}  {tag}', font=font(18), fill=(30, 36, 40))
    sheet.save(HERE/'pass2-before-after.png', optimize=True)
