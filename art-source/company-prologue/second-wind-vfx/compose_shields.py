"""Compose review_shields.py's stills into labelled 1920x1080 sheets.

  python compose_shields.py --renders DIR --out DIR

DIR holds one folder per hull (review_shields.py's --out, default
build/review/shields/<hull>). Writes front-<hull>.png for every hull with
front stills, and schemes.png, strength.png and flicker.png from whichever
hull rendered those sets. Panels keep the renders' 16:9 and are scaled down,
never up; the sheets themselves are 1920x1080.
"""
import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1920, 1080


def font(size):
    for name in ('arial.ttf', 'DejaVuSans.ttf', '/System/Library/Fonts/Helvetica.ttc'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def label(draw, xy, text, size=24):
    f = font(size)
    x, y = xy
    box = draw.textbbox((x, y), text, font=f)
    draw.rectangle((box[0] - 8, box[1] - 6, box[2] + 8, box[3] + 6), fill=(0, 0, 0))
    draw.text((x, y), text, font=f, fill=(235, 235, 235))


def grid(panels, cols, title, out, footer=None):
    """panels: [(png path, caption)], laid out cols wide at 16:9 under a title band."""
    rows = -(-len(panels) // cols)
    band = 56
    pw = min(W // cols, int((H - band - (40 if footer else 0)) / rows * 16 / 9))
    ph = pw * 9 // 16
    sheet = Image.new('RGB', (W, H), (0, 0, 0))
    d = ImageDraw.Draw(sheet)
    x0 = (W - pw * cols) // 2
    y0 = band + (H - band - (40 if footer else 0) - ph * rows) // 2
    for i, (path, caption) in enumerate(panels):
        r, c = divmod(i, cols)
        im = Image.open(path).convert('RGB').resize((pw, ph), Image.LANCZOS)
        sheet.paste(im, (x0 + c * pw, y0 + r * ph))
        label(d, (x0 + c * pw + 14, y0 + r * ph + 12), caption, 22 if cols > 2 else 26)
    label(d, (20, 12), title, 28)
    if footer:
        label(d, (20, H - 34), footer, 20)
    sheet.save(out)
    print('SHEET', out)


def num(p):
    return int(re.match(r'\D*-(\d+)-', p.name).group(1))


ap = argparse.ArgumentParser()
ap.add_argument('--renders', required=True)
ap.add_argument('--out', required=True)
a = ap.parse_args()
R, OUT = Path(a.renders), Path(a.out)
OUT.mkdir(parents=True, exist_ok=True)
FRONT_CAPTION = {'closed': 'closed (shield_t .06: just opening at the facing)', 'half': 'half (shield_t .5)',
                 'full': 'full arc, two hits', 'turned': 'full arc turned 40 deg to port, two hits'}
for hull in sorted(p for p in R.iterdir() if p.is_dir()):
    front = sorted(hull.glob('front-*.png'), key=num)
    if front:
        grid([(p, FRONT_CAPTION[p.stem.split('-', 2)[2]]) for p in front], 2,
             f'{hull.name.split("-")[0].capitalize()}: FRONT shield, vanilla arc, fitted ~10 m clear',
             OUT / f'front-{hull.name.split("-")[0]}.png')
    schemes = sorted(hull.glob('scheme-*.png'), key=num)
    if schemes:
        grid([(p, p.stem.split('-', 2)[2].replace('_', ' ')) for p in schemes], 3,
             'Colour schemes (top row: vanilla hull styles; below: factions, the style leaned to the faction colour)',
             OUT / 'schemes.png')
    tech = sorted(hull.glob('tech-*.png'), key=num)
    if tech:
        cap = {'low-tech': 'low tech: vanilla pink-red, older emitter (busier, dimmer rim)',
               'midline': 'midline: vanilla blue, the same busier finish',
               'high-tech': 'high tech: vanilla blue, the Paragon finish (steady, even, bright rim)'}
        panels = [(p, cap[p.stem.split('-', 2)[2]]) for p in tech]
        ref = R / 'paragon-reference.png'
        if ref.exists():
            panels.append((ref, 'the Paragon as accepted: the high-tech reference'))
        grid(panels, 2, f'Shield technology on the {hull.name.split("-")[0].capitalize()}, all at strength 1.0',
             OUT / 'tech.png')
    strength = sorted(hull.glob('strength-*.png'), key=num)
    if strength:
        cap = {.3: 'very weak', .6: 'Conquest (eff 1.4)', .8: 'Onslaught/Legion (eff 1.0)',
               1.0: 'Paragon (eff 0.6), as accepted', 1.25: 'denser', 1.5: 'densest'}
        grid([(p, f'strength {float(p.stem.split("-")[2]):.2f}: {cap.get(float(p.stem.split("-")[2]), "")}')
              for p in strength], 3, f'shield_strength on the {hull.name.split("-")[0].capitalize()}',
             OUT / 'strength.png', 'one knob: lower is thinner and flickers in patches, higher is denser '
                                  'and filters what is behind it; 1.0 is the Paragon unchanged')
    flicker = sorted(hull.glob('flicker-*.png'), key=num)
    if flicker:
        grid([(p, f'frame {p.stem.split("-f")[1]} (strength .3)') for p in flicker], 2,
             'A weak shield moves: four frames 0.1 s apart', OUT / 'flicker.png')
