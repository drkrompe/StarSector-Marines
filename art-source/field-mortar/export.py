"""Mechanical export of the field-mortar art: raw ImageGen reskins -> mod/graphics/.

Reads the retained raw PNGs in art-source/field-mortar/raw/ and the code-owned guides written
by art-source/templates/field-mortar-guide.py, and writes the shipped sprites. Nothing here
repaints. It (1) resamples each raw to the 1024 guide frame, (2) decides alpha -- the generator's
own where it returned a real one, the guide's where it did not, always bounded by a slightly grown
guide silhouette so a drifted edge cannot add material the geometry never had, (3) crops and
downsamples to game scale.

The three deployed layers share one frame and one pivot (the canvas centre). One cell is 128 px
here; the mount draws the 1024 frame across MOUNT_CELLS cells.

  graphics/structures/field-mortar-base.png     static ground plate, cropped square about the pivot
  graphics/structures/field-mortar-turret.png   rotating bipod collar (the mount's sprite)
  graphics/structures/field-mortar-tube.png     recoil layer, drawn under the collar (the mount's recoilSprite)
  graphics/ui/armory/special-field-mortar.png   the packed kit (armory icon and the gunner's carried layer), 512 px
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'art-source/field-mortar/raw'
TPL = ROOT / 'art-source/templates'
STRUCTURES = ROOT / 'mod/graphics/structures'
ARMORY = ROOT / 'mod/graphics/ui/armory'
S = 1024
C = S // 2
PX_PER_CELL = 128
MOUNT_CELLS = 1.3          # the 1024 frame spans this many cells (mount.render.visualCells)
BASE_HALF = 410            # guide pixels from the pivot to the farthest base handle / spade tooth


def has_real_alpha(im):
    lo, _ = im.getchannel('A').getextrema()
    return lo < 250


def load(name, bound=True):
    """Raw reskin resampled to the guide frame, alpha decided as described above."""
    guide = Image.open(TPL / f'field-mortar-{name}-guide.png').convert('RGBA')
    raw = Image.open(RAW / f'field-mortar-{name}.raw.png').convert('RGBA')
    if raw.width != raw.height:
        raise SystemExit(f'{name}: raw is {raw.size}, expected square')
    raw = raw.resize((S, S), Image.LANCZOS)
    guide_a = guide.getchannel('A')
    alpha = raw.getchannel('A') if has_real_alpha(raw) else guide_a
    if bound:
        alpha = ImageChops.multiply(alpha, guide_a.filter(ImageFilter.MaxFilter(21)))
    raw.putalpha(alpha)
    return raw


def save(im, size, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    out = im.resize((size, size), Image.LANCZOS)
    out.save(path)
    print(f'{path.relative_to(ROOT)}: {size}x{size}')


def main():
    layer = round(MOUNT_CELLS * PX_PER_CELL)
    save(load('turret'), layer, STRUCTURES / 'field-mortar-turret.png')
    save(load('tube'), layer, STRUCTURES / 'field-mortar-tube.png')
    base = load('base', bound=False).crop((C - BASE_HALF, C - BASE_HALF, C + BASE_HALF, C + BASE_HALF))
    save(base, PX_PER_CELL, STRUCTURES / 'field-mortar-base.png')
    packed = load('packed', bound=False)
    box = packed.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox()
    cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
    half = max(box[2] - box[0], box[3] - box[1]) // 2 + 24
    icon = packed.crop((cx - half, cy - half, cx + half, cy + half))
    save(icon, 512, ARMORY / 'special-field-mortar.png')
    print(f'muzzleOffsetCells {450 / S * MOUNT_CELLS:.3f}, visualCells {layer / PX_PER_CELL:.4f}')


if __name__ == '__main__':
    main()
