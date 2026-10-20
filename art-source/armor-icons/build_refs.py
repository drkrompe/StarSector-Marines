"""Identity references for the armory-image generations: each family's shipped battle layers.

  python art-source/armor-icons/build_refs.py

Writes refs/<family-slug>-identity.png: the torso layer (with the shoulder layers composited at
their shared registration, for articulated shells) and the helmet layer, cropped to their alpha,
enlarged and set side by side on a neutral grey. These are the battle sprites the owner accepted;
a generation reads palette, plate shapes and markings off them. They are the references the
retained raws were generated from, so they stay tracked beside them.
"""
from PIL import Image

from families import FAMILIES, ROOT, layer_files, slug

OUT = ROOT / 'art-source/armor-icons/refs'
GREY = (128, 128, 128, 255)
TALL = 560


def assembled(family):
    files = dict(layer_files(family))
    body = Image.open(files['body']).convert('RGBA')
    for part in ('shoulder-left', 'shoulder-right'):
        if part in files:
            body.alpha_composite(Image.open(files[part]).convert('RGBA'))
    head = Image.open(files['head']).convert('RGBA')
    return [im.crop(im.getchannel('A').point(lambda v: 255 if v > 8 else 0).getbbox()) for im in (body, head)]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for family in FAMILIES:
        body, head = assembled(family)
        k = TALL / body.height
        k = min(k, 900 / body.width)
        body = body.resize((round(body.width * k), round(body.height * k)), Image.LANCZOS)
        head = head.resize((round(head.width * k), round(head.height * k)), Image.LANCZOS)
        w = body.width + head.width + 3 * 60
        sheet = Image.new('RGBA', (w, max(body.height, head.height) + 120), GREY)
        sheet.alpha_composite(body, (60, (sheet.height - body.height) // 2))
        sheet.alpha_composite(head, (120 + body.width, (sheet.height - head.height) // 2))
        path = OUT / f'{slug(family)}-identity.png'
        sheet.convert('RGB').save(path)
        print(path.relative_to(ROOT), sheet.size)


if __name__ == '__main__':
    main()
