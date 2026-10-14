"""Mechanical export of the comms-radio art: raw ImageGen reskins -> mod/graphics/.

Reads the retained raws in art-source/comms-radio/raw/ and the code-owned guides written by
art-source/templates/comms-radio-guide.py. Nothing here repaints: each raw is resampled to its
guide frame, its own alpha is kept (the generator returned a real one) and bounded by a slightly
grown guide silhouette so a drifted edge cannot add material the geometry never had, then
downsampled to game scale.

  graphics/battle/fx/comms-radio-pack.png     the pack as worn from above, 320x336 = the carrier layer's 0.40 x 0.42 aspect
  graphics/battle/fx/comms-radio-handset.png  the hand set, 200x500 = the handset layer's 0.20 x 0.50 aspect
  (graphics/battle/fx/comms-radio-cord.png is drawn finished by templates/comms-radio-guide.py)
  graphics/ui/armory/special-comms-radio.png  the set as an icon, cropped square about its alpha, 512 px
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'art-source/comms-radio/raw'
TPL = ROOT / 'art-source/templates'


def load(name):
    guide = Image.open(TPL / f'{name}-guide.png').convert('RGBA')
    raw = Image.open(RAW / f'{name}.raw.png').convert('RGBA').resize(guide.size, Image.LANCZOS)
    alpha = ImageChops.multiply(raw.getchannel('A'), guide.getchannel('A').filter(ImageFilter.MaxFilter(21)))
    raw.putalpha(alpha)
    return raw


def main():
    pack = load('comms-radio-pack').resize((320, 336), Image.LANCZOS)
    out = ROOT / 'mod/graphics/battle/fx/comms-radio-pack.png'
    pack.save(out)
    print(out.relative_to(ROOT), pack.size)
    handset = load('comms-radio-handset').resize((200, 500), Image.LANCZOS)
    out = ROOT / 'mod/graphics/battle/fx/comms-radio-handset.png'
    handset.save(out)
    print(out.relative_to(ROOT), handset.size)
    icon = load('comms-radio-icon')
    box = icon.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox()
    cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
    half = max(box[2] - box[0], box[3] - box[1]) // 2 + 24
    icon = icon.crop((cx - half, cy - half, cx + half, cy + half)).resize((512, 512), Image.LANCZOS)
    out = ROOT / 'mod/graphics/ui/armory/special-comms-radio.png'
    icon.save(out)
    print(out.relative_to(ROOT), icon.size)


if __name__ == '__main__':
    main()
