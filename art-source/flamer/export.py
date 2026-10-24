"""Mechanical export of the flamer art: raw ImageGen reskins -> mod/graphics/.

Reads the retained raws in art-source/flamer/raw/ and the code-owned guides written by
art-source/templates/flamer-guide.py. Nothing here repaints: each raw is resampled to its guide
frame, its own alpha is kept and bounded by a slightly grown guide silhouette so a drifted edge
cannot add material the geometry never had, then downsampled to game scale.

  graphics/battle/fx/flamer-pack.png   the fuel pack as worn from above, 448x320 = the carrier layer's 0.70 x 0.50 aspect
  graphics/battle/fx/flamer-wand.png   the wand, 128x496 = the handset layer's 0.16 x 0.62 aspect
  (graphics/battle/fx/flamer-hose.png is drawn finished by templates/flamer-guide.py)
  graphics/ui/armory/special-flamer.png  the kit as an icon, cropped square about its alpha, 512 px

`--finish olive` exports the olive-drab pack and wand raws (`*-olive.raw.png`) instead of the
hazard-orange gunmetal ones; the icon has one finish.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'art-source/flamer/raw'
TPL = ROOT / 'art-source/templates'


def load(name, finish=''):
    guide = Image.open(TPL / f'{name}-guide.png').convert('RGBA')
    raw = Image.open(RAW / f'{name}{finish}.raw.png').convert('RGBA').resize(guide.size, Image.LANCZOS)
    alpha = ImageChops.multiply(raw.getchannel('A'), guide.getchannel('A').filter(ImageFilter.MaxFilter(21)))
    raw.putalpha(alpha)
    return raw


def save(image, path):
    out = ROOT / path
    image.save(out)
    print(out.relative_to(ROOT), image.size)


def main():
    finish = '-' + sys.argv[sys.argv.index('--finish') + 1] if '--finish' in sys.argv else ''
    save(load('flamer-pack', finish).resize((448, 320), Image.LANCZOS), 'mod/graphics/battle/fx/flamer-pack.png')
    save(load('flamer-wand', finish).resize((128, 496), Image.LANCZOS), 'mod/graphics/battle/fx/flamer-wand.png')
    # The icon's guide reaches the canvas edge at the nozzle, so it keeps its raw alpha unbounded.
    icon = Image.open(RAW / 'flamer-icon.raw.png').convert('RGBA')
    box = icon.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox()
    cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
    half = max(box[2] - box[0], box[3] - box[1]) // 2 + 24
    canvas = Image.new('RGBA', (half * 2, half * 2))
    canvas.alpha_composite(icon, (half - cx, half - cy))
    save(canvas.resize((512, 512), Image.LANCZOS), 'mod/graphics/ui/armory/special-flamer.png')


if __name__ == '__main__':
    main()
