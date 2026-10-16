"""Wear review sheets at 1920x1080 from review.py's renders of the worn builds.

  python compose_wear.py   -> review/wear-c1.png, review/wear-close.png

Reads build/wear-nowear (the patched build with the bare base skin),
build/wear-patched and build/wear-derelict (apply_skin.py --wear <state>), at
the cinematics' framings: C1's close starboard quarter and C5's starboard stern
quarter (review.EXTRA), plus patched close-ups.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
B = HERE / 'build'
OUT = HERE / 'review'
try:
    FONT = ImageFont.truetype('arial.ttf', 20)
except OSError:
    FONT = ImageFont.load_default()


def label(d, xy, text):
    x, y = xy
    w = d.textlength(text, font=FONT)
    d.rectangle([x - 4, y - 2, x + w + 4, y + 26], fill=(20, 20, 20))
    d.text((x, y), text, fill=(255, 235, 120), font=FONT)


def sheet(tiles, name, caption):
    s = Image.new('RGB', (1920, 1080), (30, 30, 30))
    d = ImageDraw.Draw(s)
    for k, (path, text, crop) in enumerate(tiles):
        im = Image.open(path).convert('RGB')
        if crop:
            im = im.crop(crop)
        s.paste(im.resize((960, 540), Image.LANCZOS), ((k % 2) * 960, (k // 2) * 540))
        label(d, ((k % 2) * 960 + 10, (k // 2) * 540 + 8), text)
    label(d, (10, 1048), caption)
    s.save(OUT / name)


C1_CROP = (180, 60, 1740, 938)  # the hull at C1's framing, filling the tile
sheet([(B / 'wear-nowear' / 'c1-starboard-quarter.png', 'patched, base skin only (before)', C1_CROP),
       (B / 'wear-patched' / 'c1-starboard-quarter.png', 'patched + wear  (C1 framing, hull ~1,340 px)', C1_CROP),
       (B / 'wear-derelict' / 'c1-starboard-quarter.png', 'derelict + wear (heavier)', C1_CROP),
       (B / 'wear-patched' / 'c5-starboard-stern.png', 'patched + wear  (C5 framing, hull ~780 px)', (480, 240, 1440, 780))],
      'wear-c1.png', 'Eight years in the belt: abrasion to primer and bare steel, pits and dings, chalking, ore dust, soot, neglect. Scars and repairs keep their own materials.')
sheet([(B / 'wear-patched' / 'port-flank-detail.png', 'patched + wear: port flank', None),
       (B / 'wear-patched' / 'bow-detail.png', 'patched + wear: bow (craters keep their shape)', None),
       (B / 'wear-patched' / 'stern-detail.png', 'patched + wear: stern soot', None),
       (B / 'wear-derelict' / 'c5-starboard-stern.png', 'derelict + wear: C5 framing', (480, 240, 1440, 780))],
      'wear-close.png', 'Close-ups at 1920x1080: abrasion bands densest on bow-facing and dorsal plates, plate-to-plate fade and soiling, dust packed in recesses.')
print('wear sheets written')
