"""Shared registration: hull frames re-registered for the 4.6-cell dozer frame."""
import json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
REG = json.loads((HERE / 'registration.json').read_text())
F = REG['frameSize']


def hull_frame(index):
    """Registered 768x768 RGBA hull frame (no blade) for sheet slot 0 or 2."""
    fr = REG['frames'][index]
    src = Image.open(HERE / fr['source']).convert('RGBA')
    if list(src.size) != fr['sourceSize']:
        raise SystemExit('Re-measure registration when replacing ' + fr['source'])
    x0, y0, w, h = fr['sourceRect']
    s = fr['scale']
    crop = src.crop((x0, y0, x0 + w, y0 + h))
    # premultiplied resample keeps transparent edges clean
    crop = crop.convert('RGBa').resize((round(w * s), round(h * s)), Image.LANCZOS).convert('RGBA')
    tx = fr['targetCenter'][0] - index * F - (fr['sourceCenter'][0] - x0) * s
    ty = fr['targetCenter'][1] - (fr['sourceCenter'][1] - y0) * s
    out = Image.new('RGBA', (F, F), (0, 0, 0, 0))
    out.alpha_composite(crop, (round(tx), round(ty)))
    return out
