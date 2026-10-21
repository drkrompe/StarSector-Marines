"""Mechanical export of the armory armor images: retained raw masters -> mod/graphics/ui/armory/.

  python art-source/armor-icons/export.py [FAMILY ...]

Nothing here repaints. Each family's accepted raw (raw/<slug>.raw.png, or the version named in
ACCEPTED) keeps the alpha the generator returned, with faint haze below a small threshold cleared,
is cropped square about its opaque bounds with a margin matching the former tier icons, and is
downsampled to the armory's 512 px. Variant patterns (variants.py) export the same way to
armor-<pattern>.png. Anything without a raw is skipped and keeps whatever the catalog points at.
"""
import sys

from PIL import Image

from families import FAMILIES, ROOT, icon_path, slug
from variants import VARIANTS, variant_icon_path

RAW = ROOT / 'art-source/armor-icons/raw'
SIZE = 512
MARGIN = 0.03  # of the square side; the tier icons sat 5-20 px inside 512
HAZE = 10

# family -> raw version suffix, when the accepted master is not the first generation
ACCEPTED = {'orderly': 'v2'}  # also keyed by variant pattern slug; v1 read as a bundle of barrels


def raw_path(family):
    version = ACCEPTED.get(family)
    name = family if family in VARIANTS else slug(family)
    return RAW / f'{name}{"-" + version if version else ""}.raw.png'


def export(family):
    raw = Image.open(raw_path(family)).convert('RGBA')
    alpha = raw.getchannel('A')
    if alpha.getextrema()[0] > 200:
        raise SystemExit(f'{raw_path(family).name} has no transparency; it needs a regeneration, not a key')
    raw.putalpha(alpha.point(lambda v: 0 if v < HAZE else v))
    box = raw.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox()
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    side = max(box[2] - box[0], box[3] - box[1]) / (1 - 2 * MARGIN)
    half = side / 2
    crop = raw.crop((round(cx - half), round(cy - half), round(cx + half), round(cy + half)))
    icon = crop.resize((SIZE, SIZE), Image.LANCZOS)
    out = ROOT / 'mod' / (variant_icon_path(family) if family in VARIANTS else icon_path(family))
    icon.save(out, optimize=True)
    print(out.relative_to(ROOT), icon.size)


def main():
    families = sys.argv[1:] or list(FAMILIES) + list(VARIANTS)
    for family in families:
        if raw_path(family).exists():
            export(family)
        else:
            print('skip', family, '(no raw)')


if __name__ == '__main__':
    main()
