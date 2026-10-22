"""The S12 wreckfall paintover guide: the k6 planet plate at the key framing, with the Colossus and
Morrow projected onto it from their vanilla sprites at their staged poses (battle_blockout.K6_SHIPS,
the key camera), flat grey. Geometry and layout authority for the paintover options.

    python paintover/s12-wreckfall/make_guide.py <k6 plate png> <out png>
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[1]))
sys.argv, args = ['x'], sys.argv[1:]
import battle_blockout as b  # noqa: E402
from starsector_path import GAME_CORE  # noqa: E402

SPRITES = {'colossus': 'graphics/ships/colossus/colossus.png', 'morrow': 'graphics/ships/drone_tender.png'}
LENGTH = {'colossus': 108.5, 'morrow': 67.1}
W, H = b.W, b.H


def basis(c):
    loc = c['loc']
    f = [y - x for x, y in zip(loc, c['target'])]
    n = math.sqrt(sum(x * x for x in f))
    f = [x / n for x in f]
    r = [f[1], -f[0], 0.0]
    n = math.sqrt(sum(x * x for x in r))
    r = [x / n for x in r]
    u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
    return loc, f, r, u, c['lens'] / 36.0 * W


def rows(hd, pt, rl):
    h, p, q = math.radians(hd), math.radians(-pt), math.radians(rl)
    ch, sh, cp, sp, cr, sr = math.cos(h), math.sin(h), math.cos(p), math.sin(p), math.cos(q), math.sin(q)
    return [[ch * cp, ch * sp * sr - sh * cr, ch * sp * cr + sh * sr],
            [sh * cp, sh * sp * sr + ch * cr, sh * sp * cr - ch * sr],
            [-sp, cp * sr, cp * cr]]


def main(plate, out):
    cam = b.SHOTS['k6']['camera']
    loc, f, r, u, fpx = basis(cam)
    im = Image.new('RGB', (W, H), (0, 0, 0))
    pl = Image.open(plate).convert('RGBA').resize((W, H))
    im.paste(pl, (0, 0), pl)
    dr = ImageDraw.Draw(im)
    for key, sp in b.SHOTS['k6']['ships'].items():
        spr = Image.open(GAME_CORE / SPRITES[key]).convert('RGBA')
        sw, sh = spr.size
        m = LENGTH[key] / sh                     # metres per sprite pixel; the nose is up
        R = rows(*sp[3:6])
        step = max(1, sh // 260)
        for py in range(0, sh, step):
            for px in range(0, sw, step):
                a = spr.getpixel((px, py))
                if a[3] < 128:
                    continue
                bx, by = (sh / 2 - py) * m, (sw / 2 - px) * m        # fore, port
                w = [sp[i] + R[i][0] * bx + R[i][1] * by for i in range(3)]
                d = [w[i] - loc[i] for i in range(3)]
                z = sum(d[i] * f[i] for i in range(3))
                x = W / 2 + fpx * sum(d[i] * r[i] for i in range(3)) / z
                y = H / 2 - fpx * sum(d[i] * u[i] for i in range(3)) / z
                g = int(110 + .5 * (a[0] + a[1] + a[2]) / 3)
                rad = max(1.0, fpx * step * m / z * .7)
                dr.rectangle((x - rad, y - rad, x + rad, y + rad), fill=(g, g, g))
    im.save(out)
    print(out)


if __name__ == '__main__':
    main(*args)
