"""The derelict Asharu platform as a kit piece, in plain Python (wk_geom, wk_families).

The game calls it "castoff bulk-carrier segments welded between a honeycomb of emptied fuel tanks";
the shot doc adds empty hangars with a few slumped, stripped hulks and a broken mast. The Asharu
builder owns its footprint (`battle_blockout.platform_parts`), and the hiding is measured against
it, so this builds every part of that list in place, at exactly its size, as the thing it stands
for:

- **girder**: the sagging double spine, whole trusses of angle iron with battens and diagonals;
- **tie**: the cross ties between them, lighter trusses;
- **bulk segment**: a castoff bulk-carrier hull segment, plated over ring frames, both ends closed
  by bulkheads with plates gone, holed and gutted, in mismatched old liveries;
- **tank**: an emptied fuel drum, a true circle of plates over ring frames, ends closed, some
  holed through so the drum shows hollow;
- **hangar**: the bays' floors and walls, plated over ribs, eroded at the edges, a few holes;
- **mast**: the broken mast, a truss torn off at the top, and its stub arms.

The result is one mesh in the platform's own metres (+X its length, +Y its north side, +Z up),
placed by the builder with the platform's own frame: never centred on its bounds. Its parts list
is hashed (`parts_sha`) so a builder can tell when the footprint has moved on from the kit."""
import hashlib
import json
import math
import random

import wk_families as F
from wk_geom import Mesh

PART_LIVERIES = ('oxide', 'grey', 'umber', 'bluegrey', 'olive')


def rot(hd, pt, rl):
    """battle_blockout.frame_of's convention: Rz(hd) Ry(-pt) Rx(rl)."""
    ch, sh = math.cos(math.radians(hd)), math.sin(math.radians(hd))
    cp, sp = math.cos(math.radians(pt)), math.sin(math.radians(pt))
    cr, sr = math.cos(math.radians(rl)), math.sin(math.radians(rl))
    rz = ((ch, -sh, 0), (sh, ch, 0), (0, 0, 1))
    ry = ((cp, 0, -sp), (0, 1, 0), (sp, 0, cp))
    rx = ((1, 0, 0), (0, cr, -sr), (0, sr, cr))

    def mm(a, b):
        return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))
    return mm(mm(rz, ry), rx)


def parts_sha(parts):
    return hashlib.sha256(json.dumps(parts, sort_keys=True).encode()).hexdigest()[:16]


def _fit(m, full):
    """Recentres a long-X mesh and scales it to exactly full (L, W, H) on its sorted axes."""
    half = m.recentre()
    sc = [full[k] / 2 / max(half[k], 1e-6) for k in range(3)]
    m.transform(lambda v: (v[0] * sc[0], v[1] * sc[1], v[2] * sc[2]))
    return m


def _place(out, m, axes_order, r, c):
    """Adds m (long X, middle Y, short Z) to out with its axes on the part's local axes_order
    (a proper rotation), then the part's rotation r and centre c."""
    perm = [[0.0] * 3 for _ in range(3)]
    for k in range(3):
        perm[axes_order[k]][k] = 1.0
    det = (perm[0][0] * (perm[1][1] * perm[2][2] - perm[1][2] * perm[2][1])
           - perm[0][1] * (perm[1][0] * perm[2][2] - perm[1][2] * perm[2][0])
           + perm[0][2] * (perm[1][0] * perm[2][1] - perm[1][1] * perm[2][0]))
    if det < 0:
        perm[axes_order[2]][2] = -1.0

    def tf(v):
        loc = [sum(perm[i][k] * v[k] for k in range(3)) for i in range(3)]
        w = [sum(r[i][k] * loc[k] for k in range(3)) for i in range(3)]
        return (w[0] + c[0], w[1] + c[1], w[2] + c[2])
    out.extend(m, tf)


def build(parts, seed=7):
    """One Mesh of the whole platform from battle_blockout.platform_parts()."""
    rnd = random.Random(seed)
    out = Mesh()
    counts = {}
    for n, (kind, name, c, size, (hd, pt, rl)) in enumerate(parts):
        r = rot(hd, pt, rl)
        liv = F.LIVERIES[PART_LIVERIES[n % len(PART_LIVERIES)]]
        s = seed * 1000 + n
        if kind == 'drum':
            radius, half_len = size
            full = (2 * half_len, 2 * radius, 2 * radius)
            m = _tank(full, s, liv)
            order = (0, 1, 2)                       # a drum lies along its own X
            label = 'tank'
        else:
            half = size
            order = tuple(sorted(range(3), key=lambda k: -half[k]))
            full = tuple(2 * half[k] for k in order)
            if 'girder' in name:
                m, label = F.spar(full, s, F.LIVERIES['grey'], torn=False, kink=False), 'girder'
            elif 'tie' in name:
                m, label = F.spar(full, s, F.LIVERIES['grey'], torn=False, kink=False), 'tie'
            elif 'mast' in name:
                m, label = F.spar(full, s, F.LIVERIES['grey'], torn=full[0] > 60, kink=False), 'mast'
            elif 'bulk' in name:
                m, label = _segment(full, s, liv), 'bulk segment'
            elif 'hangar' in name:
                m, label = F.plate(full, s, F.LIVERIES['umber'], erode=.25, holes_n=rnd.randint(0, 2), bend=False), 'hangar'
            else:
                m, label = F.chunk(full, s, liv), 'module'
        _fit(m, full)
        _place(out, m, order, r, c)
        counts[label] = counts.get(label, 0) + 1
    return out, counts


def _segment(full, seed, livery):
    """A castoff bulk-carrier segment: plated hull length, both ends closed by gappy bulkheads,
    holed and gutted, welded into the station."""
    rnd = random.Random(seed)
    L, W, H = full
    m = Mesh(livery[0])
    body = F.Body(m, rnd, L, W / 2, H / 2, livery=livery, chamfer=rnd.uniform(.3, .5), dent=.04)
    for _ in range(rnd.randint(1, 3)):
        body.gash(rnd.randint(0, max(0, body.nst - 2)), rnd.randrange(body.n), rnd.randint(1, 2), rnd.randint(2, 4))
    body.scatter(.04)
    accent = rnd.randrange(body.n)
    body.plates(band=lambda j: j in (accent, (accent + 1) % body.n))
    body.frames(every=1)
    body.deck(-H / 2 * .3)
    body.bulkhead(0)
    body.bulkhead(body.nst)
    return m


def _tank(full, seed, livery):
    """An emptied fuel drum: a true circle of plates over ring frames, closed ends, holed."""
    rnd = random.Random(seed)
    L, D, _ = full
    m = Mesh(livery[1])
    body = F.Body(m, rnd, L, D / 2, D / 2, livery=(livery[1], livery[0]), dent=.03, round_section=True)
    if rnd.random() < .6:
        body.gash(rnd.randint(0, max(0, body.nst - 2)), rnd.randrange(body.n), rnd.randint(1, 2), rnd.randint(3, 8))
    body.plates(band=lambda j: False)
    body.frames(every=1, depth=.7, flange=.35)
    body.bulkhead(0)
    body.bulkhead(body.nst)
    return m
