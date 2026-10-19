"""Compose the Paragon cinematic review: 1920x1080 sheets and the formation frame.

  python3 compose_review.py [--renders DIR] [--fps 24]

Reads review_ship.py's renders (default build/review/) and writes review/
beside this script (ignored; renders are never committed):
  whole.png            beauty three-quarter and stern quarter, without and with effects
  effects.png          the ten drives close (studio, and studio off), the stern quarter
                       with the studio off (the ship's glow and the drives only), and a
                       2x crop of the formation frame around the hull
  formation-1.5km.png  the space-lit formation frame with effects, as rendered
  beams.png            the beams that can bear (arc, elevation, clear line; aim.py) on a
                       point ahead (the whole line, and the bow close) and on a point
                       high on the port bow
  beam-broadside.png, beam-bow.png, beam-port.png   the same frames full size
  lance-sequence.png   the bow tachyon lances through one shot, six stills
  shield.png           the Fortress Shield half raised, raised with two hits, and in
                       fortress mode with one
  shield-half.png, shield-raised.png, shield-fortress.png   the same frames full size
There is no turn movie: the Onslaught's turntable is the model for one if a shot wants it.
"""
from pathlib import Path
import argparse
import shutil
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--renders', default=str(HERE / 'build' / 'review'))
ap.add_argument('--fps', type=int, default=24)
a = ap.parse_args()
src, out = Path(a.renders), HERE / 'review'
out.mkdir(parents=True, exist_ok=True)
font = None
for f in ('/System/Library/Fonts/Supplemental/Arial Bold.ttf', 'C:/Windows/Fonts/arialbd.ttf'):
    try:
        font = ImageFont.truetype(f, 22)
        break
    except OSError:
        pass
font = font or ImageFont.load_default()
PAGES = {
    'whole': ['three-quarter', 'stern-quarter', 'three-quarter-fx', 'stern-quarter-fx'],
    'effects': ['engine-detail-fx', 'engine-detail-fx-dark', 'stern-quarter-fx-dark', 'formation-1.5km-fx@crop'],
    'beams': ['beam-broadside-fx', 'beam-bow-fx', 'beam-port-fx'],
    'shield': ['shield-half', 'shield-raised', 'shield-fortress'],
}
NOTES = {'-dark': '  (studio off: the ship glow and the drives)', '-fx': '  (SWFX effects)',
         '@crop': '  (2x crop, effects)'}


def load(v):
    name, _, mode = v.partition('@')
    f = src / f'{name}.png'
    if not f.exists():
        return None
    im = Image.open(f).convert('RGB')
    assert im.size == (1920, 1080), (f, im.size)
    if mode == 'crop':          # 960x540 about the frame centre, shown at 2x (pixel-doubled)
        return im.crop((720, 405, 1200, 675)).resize((960, 540), Image.NEAREST)
    return im.resize((960, 540), Image.LANCZOS)


for name, views in PAGES.items():
    W = Image.new('RGB', (1920, 1080))
    d = ImageDraw.Draw(W)
    for k, v in enumerate(views):
        x, y = (k % 2) * 960, (k // 2) * 540
        im = load(v)
        if im is not None:
            W.paste(im, (x, y))
        d.rectangle((x, y, x + 960, y + 34), fill=(0, 0, 0))
        note = ''.join(t for s, t in NOTES.items() if s in v and not (s == '-fx' and ('-dark' in v or '@' in v)))
        d.text((x + 12, y + 5), f"Paragon  ·  {v.split('@')[0].replace('-fx', '').replace('-dark', '')}{note}",
               font=font, fill=(235, 235, 235))
    W.save(out / f'{name}.png')
for n in ('shield-half', 'shield-raised', 'shield-fortress'):
    if (src / f'{n}.png').exists():
        shutil.copyfile(src / f'{n}.png', out / f'{n}.png')
for n in ('formation-1.5km', 'beam-broadside', 'beam-bow', 'beam-port'):
    f = src / f'{n}-fx.png' if n != 'formation-1.5km' else src / 'formation-1.5km-fx.png'
    if f.exists():
        shutil.copyfile(f, out / f'{n}.png')
LANCE = ['charge building', 'full charge', 'beam on', 'beam held', 'charge-down', 'dark again']
frames = [src / f'lance-{k}.png' for k in range(1, 7)]
if all(f.exists() for f in frames):
    sheet = Image.new('RGB', (1920, 1080))
    d = ImageDraw.Draw(sheet)
    for k, f in enumerate(frames):
        x, y = (k % 3) * 640, 180 + (k // 3) * 360
        sheet.paste(Image.open(f).convert('RGB').resize((640, 360), Image.LANCZOS), (x, y))
        d.rectangle((x, y, x + 640, y + 30), fill=(0, 0, 0))
        d.text((x + 10, y + 4), f'{k + 1}. {LANCE[k]}', font=font, fill=(235, 235, 235))
    d.text((24, 70), 'Paragon  ·  bow tachyon lances, one shot (0.5 s charge, 1 s burst, 1 s charge-down)',
           font=font, fill=(235, 235, 235))
    sheet.save(out / 'lance-sequence.png')
print('COMPOSED', sorted(p.name for p in out.iterdir()))
