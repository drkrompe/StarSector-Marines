"""Mechanical export of the marines' held weapons: raw ImageGen reskins -> mod/graphics/.

Reads the retained raws in art-source/infantry-weapons/raw/ and the code-owned guide written by
art-source/templates/infantry-weapons-guide.py. Nothing here repaints: each raw is resampled to its
guide sheet, its own alpha is kept and bounded by a slightly grown guide silhouette (so a drifted edge
cannot add material the geometry never had) and cleared inside the guide's enclosed openings (a
stock's cut-out, the gap between wire struts -- a reskin tends to fill those), then each frame is cut
at its recorded rectangle and downsampled to the shipped sprite's exact size, which is the pose
layouts' contract.

  infantry-weapons.raw.png         rifle, smg, dmr, anti-materiel-rifle, srm-1
                                   (its first pulse rifle is superseded by the laser sheet)
  infantry-weapons-grades.raw.png  the surplus rifle and masterwork DMR, reskins of the same frames
  infantry-weapons-laser.raw.png   the pulse rifle's second geometry: copper coils, contained glow
  infantry-weapons-packed.raw.png  the rocket launcher stowed on the back (its packedLayer)

Armory icons are the same guns in profile (art-source/templates/infantry-weapon-icons-guide.py),
cut from their frames, turned up to the right like the anti-materiel rifle's icon and fitted to a
512 px square:

  infantry-weapon-icons.raw.png         rifle, pulse rifle, SMG, railgun DMR, rocket launcher
  infantry-weapon-icons-grades.raw.png  the surplus rifle and masterwork DMR
  infantry-weapon-icons-amr.raw.png     the anti-materiel rifle (replacing its earlier hand-made icon)
"""
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'art-source/infantry-weapons/raw'
TPL = ROOT / 'art-source/templates'
WEAPONS = ROOT / 'mod/graphics/battle/marine-modular-topdown/variants/weapons'
ICONS = ROOT / 'mod/graphics/ui/armory'
ICON_SIZE = 512
ICON_TILT = 30          # degrees, muzzle up to the right

SHEETS = [
    ('infantry-weapons', 'infantry-weapons-guide.png', {
        'rifle': WEAPONS / 'rifle.png',
        'smg': WEAPONS / 'smg.png',
        'dmr': WEAPONS / 'dmr.png',
        'anti-materiel-rifle': WEAPONS / 'anti-materiel-rifle.png',
        'srm-1': WEAPONS / 'srm-1.png',
    }),
    ('infantry-weapons-grades', 'infantry-weapons-grades-guide.png', {
        'rifle': WEAPONS / 'grades/surplus/rifle.png',
        'dmr': WEAPONS / 'grades/masterwork/dmr.png',
    }),
    ('infantry-weapons-laser', 'infantry-weapons-laser-guide.png', {
        'laser-gun': WEAPONS / 'laser-gun.png',
    }),
    ('infantry-weapons-packed', 'infantry-weapons-packed-guide.png', {
        'srm-1-packed': WEAPONS / 'srm-1-packed.png',
    }),
]


def enclosed_openings(alpha):
    """Transparent guide pixels that no path of transparency joins to the sheet's edge."""
    empty = alpha.point(lambda v: 255 if v < 128 else 0)
    padded = Image.new('L', (empty.width + 2, empty.height + 2), 255)
    padded.paste(empty, (1, 1))
    ImageDraw.floodfill(padded, (0, 0), 0)
    return padded.crop((1, 1, empty.width + 1, empty.height + 1)).filter(ImageFilter.MinFilter(5))


def load(raw_name, guide_name):
    guide = Image.open(TPL / guide_name).convert('RGBA')
    raw = Image.open(RAW / f'{raw_name}.raw.png').convert('RGBA').resize(guide.size, Image.LANCZOS)
    alpha = guide.getchannel('A')
    bound = ImageChops.subtract(alpha.filter(ImageFilter.MaxFilter(9)), enclosed_openings(alpha))
    raw.putalpha(ImageChops.multiply(raw.getchannel('A'), bound))
    return raw


ICON_SHEETS = [
    ('infantry-weapon-icons', 'infantry-weapon-icons-guide.png', {
        'rifle': ICONS / 'weapon-rifle.png',
        'laser-gun': ICONS / 'weapon-laser-gun.png',
        'smg': ICONS / 'weapon-smg.png',
        'dmr': ICONS / 'weapon-dmr.png',
        'srm-1': ICONS / 'special-srm-1.png',
    }),
    ('infantry-weapon-icons-amr', 'infantry-weapon-icons-amr-guide.png', {
        'anti-materiel-rifle': ICONS / 'special-anti-materiel-rifle.png',
    }),
    ('infantry-weapon-icons-grades', 'infantry-weapon-icons-grades-guide.png', {
        'rifle': ICONS / 'weapon-rifle-surplus.png',
        'dmr': ICONS / 'weapon-dmr-masterwork.png',
    }),
]


def icon(frame):
    """One profile frame tilted up to the right, cropped about its alpha and centred in the square."""
    tilted = frame.rotate(ICON_TILT, Image.BICUBIC, expand=True)
    tilted = tilted.crop(tilted.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox())
    scale = (ICON_SIZE - 32) / max(tilted.size)
    tilted = tilted.resize((round(tilted.width * scale), round(tilted.height * scale)), Image.LANCZOS)
    out = Image.new('RGBA', (ICON_SIZE, ICON_SIZE))
    out.alpha_composite(tilted, ((ICON_SIZE - tilted.width) // 2, (ICON_SIZE - tilted.height) // 2))
    return out


def icons():
    frames = {f['name']: f for f in json.loads((TPL / 'infantry-weapon-icons-guide.json').read_text())['frames']}
    for raw_name, guide_name, outputs in ICON_SHEETS:
        if not (RAW / f'{raw_name}.raw.png').exists():
            print('skip', raw_name, '(no raw)')
            continue
        sheet = load(raw_name, guide_name)
        for name, out in outputs.items():
            image = icon(sheet.crop(tuple(frames[name]['rect'])))
            image.save(out, optimize=True)
            print(out.relative_to(ROOT), image.size)


def main():
    layout = json.loads((TPL / 'infantry-weapons-guide.json').read_text())
    frames = {f['name']: f for f in layout['frames'] + layout['packedFrames']}
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
    icons()
