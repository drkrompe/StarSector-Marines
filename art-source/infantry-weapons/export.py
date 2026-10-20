"""Mechanical export of the marines' held weapons: raw ImageGen reskins -> mod/graphics/.

Reads the retained raws in art-source/infantry-weapons/raw/ and the code-owned guide written by
art-source/templates/infantry-weapons-guide.py. Nothing here repaints: each raw is resampled to its
guide sheet, its own alpha is kept and bounded by a slightly grown guide silhouette (so a drifted edge
cannot add material the geometry never had), each frame is cut at its recorded rectangle and
downsampled to the shipped sprite's exact size, which is the pose layouts' contract.

  infantry-weapons.raw.png         rifle, laser-gun, smg, dmr, anti-materiel-rifle, rocket-launcher
  infantry-weapons-grades.raw.png  the surplus rifle and masterwork DMR, reskins of the same frames
"""
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'art-source/infantry-weapons/raw'
TPL = ROOT / 'art-source/templates'
WEAPONS = ROOT / 'mod/graphics/battle/marine-modular-topdown/variants/weapons'

SHEETS = [
    ('infantry-weapons', 'infantry-weapons-guide.png', {
        'rifle': WEAPONS / 'rifle.png',
        'laser-gun': WEAPONS / 'laser-gun.png',
        'smg': WEAPONS / 'smg.png',
        'dmr': WEAPONS / 'dmr.png',
        'anti-materiel-rifle': WEAPONS / 'anti-materiel-rifle.png',
        'rocket-launcher': WEAPONS / 'rocket-launcher.png',
    }),
    ('infantry-weapons-grades', 'infantry-weapons-grades-guide.png', {
        'rifle': WEAPONS / 'grades/surplus/rifle.png',
        'dmr': WEAPONS / 'grades/masterwork/dmr.png',
    }),
]


def load(raw_name, guide_name):
    guide = Image.open(TPL / guide_name).convert('RGBA')
    raw = Image.open(RAW / f'{raw_name}.raw.png').convert('RGBA').resize(guide.size, Image.LANCZOS)
    bound = guide.getchannel('A').filter(ImageFilter.MaxFilter(9))
    raw.putalpha(ImageChops.multiply(raw.getchannel('A'), bound))
    return raw


def main():
    frames = {f['name']: f for f in json.loads((TPL / 'infantry-weapons-guide.json').read_text())['frames']}
    for raw_name, guide_name, outputs in SHEETS:
        if not (RAW / f'{raw_name}.raw.png').exists():
            print('skip', raw_name, '(no raw)')
            continue
        sheet = load(raw_name, guide_name)
        for name, out in outputs.items():
            f = frames[name]
            sprite = sheet.crop(tuple(f['rect'])).resize(tuple(f['size']), Image.LANCZOS)
            out.parent.mkdir(parents=True, exist_ok=True)
            sprite.save(out, optimize=True)
            print(out.relative_to(ROOT), sprite.size)


if __name__ == '__main__':
    main()
