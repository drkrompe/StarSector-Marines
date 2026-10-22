"""The swap-in contract between the Asharu wreck kit and the battle builder. Plain Python, no bpy.

The debris field (asharu-battle/debris_field) stays the truth about where every wreck is and how
big: --check measures the hiding against its boxes. The kit only decides what is drawn in a box:

    pick(half, seed) -> Pick(name, object, family, local, fill)

- `object` is the kit object to instance (collection `ASHARU WRECKS` in asharu-wrecks.blend);
- `local` is the 3x3 that maps the kit mesh (metres, centred) into the box's own frame. It
  replaces `Diagonal(half)` in the builder's matrix: world = T(centre) @ R(box) @ local. The kit
  mesh then fills the box exactly on every axis, so the box --check measures is the wreck's own
  bounds;
- `fill` is the stretch per axis (box over native size). Procedural wrecks are chosen nearest
  the box in size and proportion, so it stays near one; a hulk derived from an accepted hull is
  chosen only within catalogue HULK_FIT, so a recognisable class is never drawn at a size it
  does not have.

The pick is deterministic in (half, seed): the same box gets the same wreck on every frame and
every machine. `local` is a proper rotation times a scale (determinant > 0), so normals stay
outward, and it may turn the wreck end for end (seeded) so two boxes alike are not twins.

    python wreck_kit.py            the swap table for the current field (imports battle_blockout)
"""
import json
import math
import random
from collections import namedtuple
from pathlib import Path

HERE = Path(__file__).resolve().parent
CATALOGUE = HERE / 'catalogue.json'
HULK_FIT = (.86, 1.16)

Pick = namedtuple('Pick', 'name object family local fill')
_cat = None


def catalogue():
    global _cat
    if _cat is None:
        _cat = json.loads(CATALOGUE.read_text())['variants']
    return _cat


def family_of(a, b, c):
    """The wreck family a box of sorted half extents a >= b >= c calls for."""
    if b / a < .2:
        return 'spar'
    if c / a < .17 and b / a >= .3:
        return 'plate'
    if b / a < .5 and c / b > .55:
        return 'spar'
    if a / c < 1.45:
        return 'chunk'
    if b / a < .66:
        return 'hullfrag'
    return 'section'


def _score(native, box):
    return sum(abs(math.log(box[k] / native[k])) for k in range(3))


def pick(half, seed):
    half = tuple(float(h) for h in half)
    order = sorted(range(3), key=lambda k: -half[k])          # box axes, longest first
    box = tuple(max(half[k], 1e-3) for k in order)
    fam = family_of(*box)
    rnd = random.Random(int(seed) * 7919 + 17)
    choice = None
    if fam in ('section', 'hullfrag', 'chunk'):
        fits = [v for v in catalogue() if v['family'] == 'hulk'
                and all(HULK_FIT[0] <= box[k] / v['half'][k] <= HULK_FIT[1] for k in range(3))]
        if fits:
            fits.sort(key=lambda v: _score(v['half'], box))
            choice = fits[0] if len(fits) == 1 or rnd.random() < .6 else rnd.choice(fits[:3])
    if choice is None:
        pool = [v for v in catalogue() if v['family'] == fam]
        pool.sort(key=lambda v: _score(v['half'], box))
        best = _score(pool[0]['half'], box)
        near = [v for v in pool if _score(v['half'], box) <= best + .25][:3]
        choice = rnd.choice(near)
    nat = choice['half']
    fill = tuple(box[k] / nat[k] for k in range(3))
    # kit axis k (X, Y, Z: long, middle, short) -> box axis order[k], with a seeded end-for-end turn
    signs = [rnd.choice((-1, 1)) for _ in range(3)]
    local = [[0.0] * 3 for _ in range(3)]
    for k in range(3):                     # box metres along order[k] = sign * fill * kit metres along k
        local[order[k]][k] = signs[k] * fill[k]
    if _det(local) < 0:                    # a proper rotation: flip the short axis instead of mirroring
        local[order[2]][2] = -local[order[2]][2]
    return Pick(choice['name'], choice['object'], choice['family'], tuple(tuple(r) for r in local),
                tuple(round(f, 3) for f in fill))


def hulks():
    """{name: full (L, W, H) m} of the hulks derived from accepted hulls. A box sized to one of these
    (within HULK_FIT, in any axis order) gets that hulk: the way to put a recognisable class in the
    field is to size a cover or field box to it."""
    return {v['name']: tuple(round(2 * h, 1) for h in v['half']) for v in catalogue() if v['family'] == 'hulk'}


def _det(m):
    return (m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
            + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]))


def table():
    """The swap for every box of the current field: battle_blockout.FIELD, read only."""
    import sys
    sys.path.insert(0, str(HERE.parent / 'asharu-battle'))
    argv, sys.argv = sys.argv, ['wreck_kit']
    try:
        import battle_blockout as b
    finally:
        sys.argv = argv
    rows = []
    for i, piece in enumerate(b.FIELD):
        for j, (_, half, _) in enumerate(piece.boxes):
            p = pick(half, i * 31 + j)
            rows.append((piece.name, j, tuple(round(2 * h, 1) for h in sorted(half, reverse=True)), p))
    return rows


if __name__ == '__main__':
    from collections import Counter
    rows = table()
    for name, j, size, p in rows:
        print('%-20s %d  %-22s -> %-22s fill %s' % (name, j, size, p.name, p.fill))
    print(Counter(p.family for *_, p in rows))
    print('worst stretch', max(max(abs(math.log(f)) for f in p.fill) for *_, p in rows))
