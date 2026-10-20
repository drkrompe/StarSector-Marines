"""Review contact sheet: each armor family's battle layers beside its armory image.

  python art-source/armor-icons/contact_sheet.py [--new | --compare REV] [--out build/armor-icons/sheets]

Writes 1920x1080 pages, nine families each. Left of each tile: the family's body, head and (for
articulated shells) shoulder layers enlarged on a neutral ground; right: the armory image the
catalog currently points its patterns at. With --new the right side is the family's own image
(art-source/armor-icons export) when it exists. With --compare REV each tile shows the layers, the
image the catalog pointed at in REV (small, labelled) and the family's own image. Output is a render: it goes under build/, never git.
"""
import argparse
import json
import subprocess
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from build_refs import assembled
from families import CATALOG, FAMILIES, ROOT, icon_path

W, H = 1920, 1080
COLS, ROWS = 3, 3
TW, TH = W // COLS, H // ROWS
BG = (24, 27, 31)
TILE = (36, 40, 46)
GROUND = (88, 92, 96)


def font(size):
    for name in ('segoeui.ttf', 'arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def patterns_by_family(rev=None):
    if rev:
        text = subprocess.run(['git', 'show', f'{rev}:{CATALOG.relative_to(ROOT).as_posix()}'], cwd=ROOT,
                              capture_output=True, text=True, check=True).stdout
    else:
        text = CATALOG.read_text(encoding='utf-8')
    out = defaultdict(list)
    for p in json.loads(text)['armor']:
        out[p['battle']['appearanceFamily']].append((p['id'].removeprefix('armor.'), p['catalog']['iconPath']))
    return out


def layer_strip(family, box_w, box_h):
    ims = assembled(family)
    gap = 6
    total_w = sum(i.width for i in ims) + gap * (len(ims) - 1)
    tall = max(i.height for i in ims)
    scale = min(box_w / total_w, box_h / tall, 3.0)
    strip = Image.new('RGBA', (box_w, box_h), GROUND + (255,))
    x = (box_w - int(total_w * scale)) // 2
    for im in ims:
        im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.NEAREST)
        strip.alpha_composite(im, (x, (box_h - im.height) // 2))
        x += im.width + int(gap * scale)
    return strip


def old_icon_file(path):
    """A catalog path from an older revision; the retired tier icons now live in composition/."""
    shipped = ROOT / 'mod' / path
    return shipped if shipped.exists() else ROOT / 'art-source/armor-icons/composition' / Path(path).name


def compare_tile(sheet, d, fam, old_path, x0, y0, box, f_small):
    left_w = TW - box - 44
    strip = layer_strip(fam, left_w, box // 2 - 10)
    sheet.alpha_composite(strip, (x0 + 16, y0 + 64))
    d.text((x0 + 16, y0 + 64 + box // 2 - 8), 'in battle', font=f_small, fill=(140, 150, 160))
    small = box // 2 - 24
    old_file = old_icon_file(old_path)
    icon = Image.open(old_file).convert('RGBA').resize((small, small), Image.LANCZOS)
    sheet.alpha_composite(icon, (x0 + 16, y0 + 64 + box // 2 + 12))
    d.text((x0 + 24 + small, y0 + TH - 40), 'old: ' + old_file.stem, font=f_small, fill=(140, 150, 160))
    new = Image.open(ROOT / 'mod' / icon_path(fam)).convert('RGBA').resize((box, box), Image.LANCZOS)
    sheet.alpha_composite(new, (x0 + TW - box - 16, y0 + 64))
    d.text((x0 + TW - box - 16, y0 + TH - 22), 'new: ' + Path(icon_path(fam)).name, font=f_small, fill=(200, 210, 220))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--new', action='store_true')
    ap.add_argument('--compare', metavar='REV')
    ap.add_argument('--out', default=str(ROOT / 'build/armor-icons/sheets'))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    pats = patterns_by_family()
    old = patterns_by_family(args.compare) if args.compare else None
    fams = list(FAMILIES)
    f_title, f_small = font(20), font(14)
    for page in range((len(fams) + COLS * ROWS - 1) // (COLS * ROWS)):
        sheet = Image.new('RGBA', (W, H), BG + (255,))
        d = ImageDraw.Draw(sheet)
        for i, fam in enumerate(fams[page * 9:(page + 1) * 9]):
            x0, y0 = (i % COLS) * TW, (i // COLS) * TH
            d.rectangle((x0 + 6, y0 + 6, x0 + TW - 6, y0 + TH - 6), fill=TILE)
            d.text((x0 + 16, y0 + 12), fam, font=f_title, fill=(230, 230, 230))
            ids = ', '.join(p for p, _ in pats[fam])
            d.text((x0 + 16, y0 + 38), ids[:84], font=f_small, fill=(160, 170, 180))
            box = TH - 80
            if old:
                compare_tile(sheet, d, fam, old[fam][0][1], x0, y0, box, f_small)
                continue
            strip = layer_strip(fam, TW - box - 44, box)
            sheet.alpha_composite(strip, (x0 + 16, y0 + 64))
            if args.new and (ROOT / 'mod' / icon_path(fam)).exists():
                icon_file = ROOT / 'mod' / icon_path(fam)
            else:
                icon_file = ROOT / 'mod' / pats[fam][0][1]
            icon = Image.open(icon_file).convert('RGBA').resize((box, box), Image.LANCZOS)
            sheet.alpha_composite(icon, (x0 + TW - box - 16, y0 + 64))
            d.text((x0 + TW - box - 16, y0 + TH - 22), icon_file.name, font=f_small, fill=(140, 150, 160))
        kind = 'compare' if old else 'new' if args.new else 'current'
        path = out / f'{kind}-{page + 1:02d}.png'
        sheet.convert('RGB').save(path)
        print(path)


if __name__ == '__main__':
    main()
