"""One 1920x1080 review sheet per turret: the game's sprite beside the model.

  blender --background --factory-startup --python catalog.py -- --out DIR --top [--only a,b]
  python review_sheet.py DIR [--only a,b] [--contact] [--game "C:/Program Files (x86)/Fractal Softworks/Starsector"]

Top row: the sprite composited the way the game draws it (gun below or above
the base, as its renderHints say), the model's orthographic top view at the
same scale, and its side view. Bottom row: the three-quarter render. Writes
DIR/<weapon>-sheet.png. This is the sheet the turret gauntlet judges (README).
"""
import argparse
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
WPN = {'hephaestus': 'hephag', 'needler': 'heavyneedler'}
RING = {'SMALL': 5.5, 'MEDIUM': 9.5, 'LARGE': 12.5}
FRAME = 6.4        # ring radii across the top and side renders (catalog.py --top)


def composite(core, wid):
    t = open(os.path.join(core, 'data', 'weapons', wid+'.wpn'), encoding='utf-8').read()

    def get(k):
        m = re.search(r'^\s*"%s"\s*:\s*"([^"]+)"' % k, t, re.M)
        return m and m.group(1)
    below = re.search(r'^\s*"renderHints"\s*:\s*\[[^\]]*RENDER_BARREL_BELOW', t, re.M)
    order = ['turretUnderSprite', 'turretGunSprite', 'turretSprite'] if below else \
        ['turretUnderSprite', 'turretSprite', 'turretGunSprite']
    base = Image.open(os.path.join(core, get('turretSprite'))).convert('RGBA')
    out = Image.new('RGBA', base.size)
    for key in order:
        if get(key):
            im = Image.open(os.path.join(core, get(key))).convert('RGBA')
            out.alpha_composite(im, ((base.width-im.width)//2, (base.height-im.height)//2))
    return out, re.search(r'"size"\s*:\s*"(\w+)"', t).group(1)


def silhouette_iou(spr, top, ring):
    """Overlap of the sprite's opaque pixels with the model's top-view alpha, at
    the top render's scale (FRAME ring radii across, centred on the pivot)."""
    n = top.width
    px = n/(FRAME*ring)
    big = Image.new('L', (n, n), 0)
    a = spr.split()[-1].point(lambda v: 255 if v > 40 else 0)
    a = a.resize((round(spr.width*px), round(spr.height*px)), Image.NEAREST)
    big.paste(a, ((n-a.width)//2, (n-a.height)//2))
    m = top.split()[-1].point(lambda v: 255 if v > 127 else 0)
    sa, ma = big.load(), m.load()
    inter = union = 0
    for y in range(0, n, 2):
        for x in range(0, n, 2):
            p, q = sa[x, y] > 0, ma[x, y] > 0
            inter += p and q
            union += p or q
    return inter/union if union else 0.0


def colour_match(spr, top):
    """Mean sRGB of the sprite's opaque pixels and of the model's top view (its
    own opaque pixels), and the model's mean luminance over the sprite's: the
    paint pass reads 'paler than the sprite' off this rather than by eye."""
    def mean(im):
        px = [p for p in im.getdata() if p[3] > 127]
        return [sum(p[i] for p in px)/len(px) for i in range(3)] if px else [0, 0, 0]
    a, b = mean(spr), mean(top.resize((top.width//4, top.height//4)))

    def lum(c):
        return .2126*c[0]+.7152*c[1]+.0722*c[2]
    return a, b, lum(b)/max(lum(a), 1e-6)


NAMES = {'vulcan': 'Vulcan Cannon', 'lightac': 'Light Autocannon', 'lightmg': 'Light Machine Gun',
         'heavyac': 'Heavy Autocannon', 'flak': 'Flak Cannon', 'dualflak': 'Dual Flak Cannon',
         'needler': 'Heavy Needler', 'squall': 'Squall MLRS', 'hurricane': 'Hurricane MIRV',
         'heavymauler': 'Heavy Mauler', 'hveldriver': 'Hypervelocity Driver', 'devastator': 'Devastator Cannon',
         'mark9': 'Mark IX Autocannon', 'hellbore': 'Hellbore Cannon', 'hephaestus': 'Hephaestus Assault Gun'}


def font(size):
    for f in ('C:/Windows/Fonts/arialbd.ttf', '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
              '/Library/Fonts/Arial Bold.ttf'):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def contact(core, d, names, scores):
    """DIR/contact.png: every turret's three-quarter render in one 1920x1080
    grid, numbered and named, its game sprite inset, for notes by number."""
    cols = 5
    rows = -(-len(names)//cols)
    tw, th = 1920//cols, 1080//rows
    sheet = Image.new('RGB', (1920, 1080), (30, 30, 34))
    big, small = font(22), font(16)
    for n, w in enumerate(names):
        x0, y0 = (n % cols)*tw, (n//cols)*th
        tq = Image.open(os.path.join(d, w+'.png')).convert('RGB')
        ch = round(tq.height*.86)
        cw = round(ch*tw/th)
        tile = tq.crop(((tq.width-cw)//2, (tq.height-ch)//2+40, (tq.width+cw)//2, (tq.height+ch)//2+40))
        sheet.paste(tile.resize((tw-4, th-4)), (x0+2, y0+2))
        spr, _ = composite(core, WPN.get(w, w))
        s = 84/max(spr.size)
        inset = Image.new('RGBA', (92, 92), (165, 167, 170, 255))
        im = spr.resize((max(1, round(spr.width*s)), max(1, round(spr.height*s))), Image.NEAREST)
        inset.alpha_composite(im, ((92-im.width)//2, (92-im.height)//2))
        sheet.paste(inset.convert('RGB'), (x0+tw-98, y0+th-98))
        dr = ImageDraw.Draw(sheet)
        dr.rectangle((x0+2, y0+2, x0+tw-3, y0+34), fill=(20, 20, 24))
        dr.text((x0+10, y0+6), f'{n+1}  {NAMES.get(w, w)}', font=big, fill=(255, 255, 255))
        if w in scores:
            dr.text((x0+10, y0+th-26), f'{w}   IoU {scores[w]:.2f}', font=small, fill=(255, 255, 255))
    sheet.save(os.path.join(d, 'contact.png'))
    print('wrote', os.path.join(d, 'contact.png'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('dir')
    ap.add_argument('--only', default='')
    ap.add_argument('--game', default='C:/Program Files (x86)/Fractal Softworks/Starsector')
    ap.add_argument('--contact', action='store_true', help='also DIR/contact.png: all turrets numbered and named')
    a = ap.parse_args()
    core = os.path.join(a.game, 'starsector-core')
    names = [n for n in a.only.split(',') if n] or \
        sorted(f[:-8] for f in os.listdir(a.dir) if f.endswith('-top.png'))
    scores, colours = {}, {}
    for w in names:
        spr, size = composite(core, WPN.get(w, w))
        S = 540
        px = S/(FRAME*RING[size])
        tile = Image.new('RGBA', (640, S), (165, 167, 170, 255))
        big = spr.resize((round(spr.width*px), round(spr.height*px)), Image.NEAREST)
        tile.alpha_composite(big, ((640-big.width)//2, (S-big.height)//2))
        sheet = Image.new('RGBA', (1920, 1080), (30, 30, 34, 255))
        sheet.alpha_composite(tile, (0, 0))
        top_full = Image.open(os.path.join(a.dir, w+'-top.png')).convert('RGBA')
        iou = silhouette_iou(spr, top_full, RING[size])
        scores[w] = iou
        ca, cb, lr = colour_match(spr, top_full)
        colours[w] = {'sprite': [round(c) for c in ca], 'model': [round(c) for c in cb], 'luminance': round(lr, 3)}
        bg = Image.new('RGBA', (S, S), (165, 167, 170, 255))
        bg.alpha_composite(top_full.resize((S, S)))
        sheet.alpha_composite(bg, (640+50, 0))
        side = Image.open(os.path.join(a.dir, w+'-side.png')).convert('RGBA').resize((S, S))
        bg = Image.new('RGBA', (S, S), (165, 167, 170, 255))
        bg.alpha_composite(side)
        sheet.alpha_composite(bg, (1280+50, 0))
        tq = Image.open(os.path.join(a.dir, w+'.png')).convert('RGBA')
        tq = tq.resize((1920, round(tq.height*1920/tq.width)))
        sheet.alpha_composite(tq.crop((0, (tq.height-540)//2, 1920, (tq.height-540)//2+540)), (0, 540))
        d = ImageDraw.Draw(sheet)
        cm = colours[w]
        for x, label in ((8, f'{w}: game sprite   mean colour {tuple(cm["sprite"])}'),
                         (698, f'model, top (same scale)   silhouette IoU {iou:.3f}   mean colour {tuple(cm["model"])}'
                               f'   luminance x{cm["luminance"]:.2f}'),
                         (1338, 'model, side')):
            d.text((x, 6), label, fill=(255, 255, 255, 255))
        d.text((8, 546), 'model, three-quarter', fill=(255, 255, 255, 255))
        sheet.convert('RGB').save(os.path.join(a.dir, w+'-sheet.png'))
        print(f'wrote {os.path.join(a.dir, w+"-sheet.png")}  IoU {iou:.3f}  colour {cm}')
    with open(os.path.join(a.dir, 'iou.json'), 'w') as fh:
        json.dump(scores, fh, indent=1)
    with open(os.path.join(a.dir, 'colour.json'), 'w') as fh:
        json.dump(colours, fh, indent=1)
    if a.contact:
        order = [w for w in NAMES if w in names]+[w for w in names if w not in NAMES]
        contact(core, a.dir, order, scores)


if __name__ == '__main__':
    main()
