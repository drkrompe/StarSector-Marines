"""Mechanical export of the defence-structure art: raw ImageGen reskins -> mod/graphics/structures/.

Reads the retained raw PNGs in art-source/defences/raw/ and the code-owned guides and
masks written by defence-structures-guide.py, and writes the shipped sprites. Nothing
here repaints. It only (1) resamples a raw to the 1024 guide frame, (2) decides alpha:
the generator's own alpha where it returned a real one, the guide's where it did not,
always bounded by a slightly grown guide silhouette so a drifted edge cannot add
material the geometry never had, (3) splits the one reskinned gun turret into its
house and its barrel layer from the reskin's own alpha (re-centred on the house), and (4) downsamples to game scale.

Game scale is 96 px per cell. Sprites are square and pivot on the centre.

  fortress-battery-base.png     288  (3 cells)   static footprint plinth
  fortress-battery-turret.png   see output       rotating gun house (pivot = house centre)
  fortress-battery-barrels.png  same             recoil layer, drawn under the house
  shield-generator-base.png     288  (3 cells)   static footprint plinth
  shield-generator-ring.png     272  (2.8)       spinning emitter ring
  shield-generator-core.png     160  (1.6)       pulsing core, tinted by holder at runtime
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'art-source/defences/raw'
TPL = ROOT / 'art-source/templates'
OUT = ROOT / 'mod/graphics/structures'
S = 1024
C = S // 2


def has_real_alpha(im):
    lo, _ = im.getchannel('A').getextrema()
    return lo < 250


def load(name, bound=True):
    """Raw reskin resampled to the guide frame, alpha decided as described above."""
    guide = Image.open(TPL / f'{name}-guide.png').convert('RGBA')
    raw = Image.open(RAW / f'{name}.raw.png').convert('RGBA')
    if raw.width != raw.height:
        raise SystemExit(f'{name}: raw is {raw.size}, expected square')
    raw = raw.resize((S, S), Image.LANCZOS)
    guide_a = guide.getchannel('A')
    alpha = raw.getchannel('A') if has_real_alpha(raw) else guide_a
    if bound:
        alpha = ImageChops.multiply(alpha, guide_a.filter(ImageFilter.MaxFilter(9)))
    raw.putalpha(alpha)
    return raw


def save(im, size, path):
    OUT.mkdir(parents=True, exist_ok=True)
    out = im.resize((size, size), Image.LANCZOS)
    out.save(OUT / path)
    print(f'{path}: {size}x{size}')


def export_turret():
    """Split the one reskinned turret into house and barrels, and re-centre it on the house.

    ImageGen drifted this one off the guide's registration (it drew the house lower and
    larger), so the split reads the reskin's own alpha instead of the guide masks: the
    house is everything from the first row that is wide (the barrels are narrow), the
    pivot is the centre of the house's bounding box, and the canvas is padded so that
    pivot is the canvas centre. Barrels are held straight down under the house to the
    pivot so the layer can slide back on recoil without running out of barrel.
    """
    raw = Image.open(RAW / 'fortress-battery-turret.raw.png').convert('RGBA')
    w, h = raw.size
    a = raw.getchannel('A').point(lambda v: 255 if v > 24 else 0)
    ap = a.load()
    widths = [sum(1 for x in range(w) if ap[x, y]) for y in range(h)]
    top = next(y for y in range(h) if widths[y] > w * 0.35)
    house_box = a.crop((0, top, w, h)).getbbox()
    cx = (house_box[0] + house_box[2]) // 2
    cy = top + (house_box[1] + house_box[3]) // 2
    half = max(cx, w - cx, cy, h - cy)
    side = 2 * half
    px = raw.load()
    house = Image.new('RGBA', (side, side))
    barrels = Image.new('RGBA', (side, side))
    hp, bp = house.load(), barrels.load()
    ox, oy = half - cx, half - cy
    for y in range(h):
        for x in range(w):
            if y >= top:
                hp[x + ox, y + oy] = px[x, y]
            else:
                bp[x + ox, y + oy] = px[x, y]
    for x in range(w):
        # Barrel feet: rows just above the house top, held down to the pivot.
        foot = px[x, top - 2]
        for y in range(top - 1, cy + 1):
            if foot[3] > 24:
                bp[x + ox, y + oy] = foot
    size = round(side / 490 * 96)
    print(f'turret: pivot ({cx},{cy}) of {w}x{h}, house top row {top}, canvas {side}, visualCells {size / 96:.4f}')
    save(house, size, 'fortress-battery-turret.png')
    save(barrels, size, 'fortress-battery-barrels.png')


def main():
    save(load('fortress-battery-base', bound=False), 288, 'fortress-battery-base.png')
    export_turret()
    save(load('shield-generator-base', bound=False), 288, 'shield-generator-base.png')
    save(load('shield-generator-ring'), 272, 'shield-generator-ring.png')
    save(load('shield-generator-core'), 160, 'shield-generator-core.png')


if __name__ == '__main__':
    main()
