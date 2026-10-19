"""C3's thruster markers: the Atlas lifter is a painted layer in a 2D composite, so its markers
live on that layer rather than on a 3D craft.

    python c3_atlas_markers.py [--out DIR] [--sheet]

Finds the lit bells painted into first-open-sky/assets/c3-atlas-v1.png (card_bells), and writes
<out>/c3.json in the same per-marker form as check_thrust_markers.py: craft, kind, index,
radius_m, throttle keys, plus each nozzle's place on the `atlas` layer as c3_layers.py bakes it
(pixels of the drawn layer, top-left origin) and its exhaust direction in that picture. The
layer's position on screen is compose_c3's to give. Fails if the card shows no lit bell.

--sheet also writes <out>/c3-card-plain.png and c3-card-glyphs.json (the card at 4x its drawn size,
centred on a 1920x1080 canvas over C3's sky colour) for draw_glyphs.py, since the composite's
own frames need its full plate build. Default out: build/thrust-markers/ (ignored).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
CINE = HERE.parent
REPO = CINE.parents[2]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(CINE / 'first-open-sky'))
from card_bells import painted_bells  # noqa: E402
from c3_core import DURATION, F_PX  # noqa: E402
from c3_layers import ATLAS_DISTANCE_M, ATLAS_LENGTH_M  # noqa: E402

CARD = CINE / 'first-open-sky' / 'assets' / 'c3-atlas-v1.png'


def main():
    argv = sys.argv[1:]
    out = Path(argv[argv.index('--out') + 1]) if '--out' in argv else REPO / 'build' / 'thrust-markers'
    out.mkdir(parents=True, exist_ok=True)
    im = Image.open(CARD).convert('RGBA')
    a = np.asarray(im, np.float32) / 255
    # c3_layers.atlas: hardened alpha, cropped to it, drawn `length` px long.
    alpha = np.clip((a[..., 3] - 0.25) / 0.5, 0, 1)
    ys, xs = np.nonzero(alpha > 0)
    x0, y0, x1, y1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
    length = F_PX * ATLAS_LENGTH_M / ATLAS_DISTANCE_M
    k = length / (x1 - x0)                  # card px -> drawn layer px
    m_per_px = ATLAS_LENGTH_M / (x1 - x0)   # card px -> metres along the hull
    bells, (cx, cy) = painted_bells(a)
    if not bells:
        raise SystemExit('THRUST c3 FAIL: no lit bell found on %s' % CARD.name)
    markers = []
    for i, (bx, by, r) in enumerate(sorted(bells, key=lambda b: b[1]), 1):
        d = np.array((bx - cx, by - cy))
        d /= np.linalg.norm(d)
        markers.append({
            'name': 'THRUST | Atlas lifter | main %02d' % i, 'kind': 'main', 'index': i,
            'radius_m': round(r * m_per_px, 3), 'working': True,
            'source': '%s painted bell at px (%d, %d)' % (CARD.name, bx, by),
            'throttle': [(0.0, 1.0), (DURATION, 1.0)],
            'layer_px': [round((bx - x0) * k, 2), round((by - y0) * k, 2)],
            'layer_exhaust_dir': [round(float(d[0]), 4), round(float(d[1]), 4)],
        })
    data = {'shot': 'c3', 'fps': 24, 'convention': 'thrust-markers/README.md',
            'craft': {'atlas layer': {'label': 'Atlas lifter', 'activity': 'climbing out under its drives',
                                      'layer': 'atlas (compose_c3)', 'markers': markers}},
            'problems': []}
    (out / 'c3.json').write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    print('THRUST c3: Atlas lifter, %d markers' % len(markers))
    if '--sheet' in argv:
        s = 4.0 * k
        card = im.crop((x0, y0, x1, y1))
        card = card.resize((round(card.width * s), round(card.height * s)), Image.LANCZOS)
        canvas = Image.new('RGBA', (1920, 1080), (178, 200, 222, 255))
        ox, oy = (1920 - card.width) // 2, (1080 - card.height) // 2
        canvas.alpha_composite(card, (ox, oy))
        canvas.convert('RGB').save(out / 'c3-card-plain.png')
        g = []
        for mk, (bx, by, r) in zip(markers, sorted(bells, key=lambda b: b[1])):
            x, y = ox + (bx - x0) * s, oy + (by - y0) * s
            dx, dy = mk['layer_exhaust_dir']
            g.append({'craft': 'Atlas lifter', 'kind': 'main', 'name': mk['name'], 'throttle': 1.0,
                      'working': True, 'xy': [x, y], 'tip': [x + dx * 120, y + dy * 120], 'r_px': r * s})
        (out / 'c3-card-glyphs.json').write_text(json.dumps({'shot': 'c3', 't': 0.0, 'frame': 0, 'markers': g}, indent=1),
                                            encoding='utf-8')
    print('THRUST c3 PASS')


if __name__ == '__main__':
    main()
