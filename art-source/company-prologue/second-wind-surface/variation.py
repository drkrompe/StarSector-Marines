"""Per-plate tone offsets: the variety grading removed, restored on purpose.

  python variation.py      -> variation.json (fills rows for new parts only)

Grading puts every part exactly on its class's palette mean, which reads as
one flat plate rolled over the whole hull (pilot verdict, change 2). The
offsets here are the deliberate replacement for the accidental drift between
generations: one CIELAB offset per part, applied in the part's material as a
linear-light gain (apply_skin.py), so two parts sharing one generation
(skin_from, mirrored starboard reuses) still differ.

variation.json is OWNED: edit any row by hand to make a plate read older,
newer or a replacement panel. This script only adds rows for parts that have
none yet, from a hash of the part id, so a row never changes under you and a
new part's offset does not depend on which order parts were added in.
Pure Python (no numpy) so apply_skin.py can use `gain` inside Blender.
"""
from pathlib import Path
import hashlib
import json

import hull as H

HERE = Path(__file__).resolve().parent
DATA = H.DIR  # the hull's folder: its tables, art and outputs (hull.py)
PATH = DATA / 'variation.json'
# Painted classes may wander in hue a little; bare metal and recess only in
# lightness. Spans are +/- from the palette mean.
SPAN = H.VARIATION_SPAN  # per hull (surface_hull.py)
_M = ((0.4124564, 0.3575761, 0.1804375), (0.2126729, 0.7151522, 0.0721750), (0.0193339, 0.1191920, 0.9503041))
_MI = ((3.2404542, -1.5371385, -0.4985314), (-0.9692660, 1.8760108, 0.0415560), (0.0556434, -0.2040259, 1.0572252))
_WHITE = (0.95047, 1.0, 1.08883)


def offset(part):
    """Unit offsets in [-1, 1]^3 from the part id (stable across runs and machines)."""
    h = hashlib.sha256(part.encode()).digest()
    return [round((h[k] / 255) * 2 - 1, 3) for k in range(3)]


def lab_to_linear(lab):
    L, a, b = lab
    fy = (L + 16) / 116
    f = (fy + a / 500, fy, fy - b / 200)
    xyz = [(v ** 3 if v ** 3 > 216 / 24389 else (116 * v - 16) / (24389 / 27)) * w for v, w in zip(f, _WHITE)]
    return [max(sum(m * x for m, x in zip(row, xyz)), 1e-5) for row in _MI]


def gain(cls_mean, unit, cls):
    """Linear RGB multiplier taking the class mean to mean + unit * SPAN[cls]."""
    span = SPAN.get(cls, (2.0, 0.3, 0.5))
    lab = [m + u * s for m, u, s in zip(cls_mean, unit, span)]
    a, b = lab_to_linear(cls_mean), lab_to_linear(lab)
    return [round(y / x, 4) for x, y in zip(a, b)]


def load():
    return json.loads(PATH.read_text()) if PATH.exists() else {}


if __name__ == '__main__':
    import parts as P
    table = load()
    added = [p for p in P.PARTS if p not in table]
    for p in added:
        table[p] = {'unit_offset_L_a_b': offset(p), 'note': 'hash default; edit freely'}
    PATH.write_text(json.dumps(dict(sorted(table.items())), indent=1) + '\n')
    print('variation rows', len(table), 'added', len(added))
