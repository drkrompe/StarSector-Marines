"""Hull: the central body's flanks and underside.

The prow's port flank (X 104..225: the plated band of orange, bone-brown, dark
brown and blue-steel plates over the brown-grey prow, seen square-on to its
(0.16, 0.95, -0.27) slope), the forefoot (the prow chin under the bow, port
half), the bilge (the plated lower body between the keel band and the gun arms,
'Hull belly' and the 'Detail hull port ...' plate families on it) and the keel
band (the dark grey centre strake with its steel and dark brown keel plates).

Every port part is the source and its starboard twin reuses it mirrored
(mirror_u); the keel tiles are centred and need no twin. A tile spans at most
about 45 x 25.6 units at 15 texels per unit, so the prow flank is cut by box
along X into three, the bilge (about 40 units across its slope) into an inner
and an outer band per X segment, and the keel into X segments of 41 units.

Left to the class swatches: the side keel strakes (strakes 2 and 3), the aft
bilge beyond the plating, the waist undersides with their louvres, the conduit
bundles and brackets, radiator fins, tanks, fittings, the fin under the bow
(the chin hides all but its lower edge) and every chamfer.
"""
import re
from math import sqrt


def _n(v):
    s = sqrt(sum(c * c for c in v))
    return tuple(c / s for c in v)


def _stbd(v):
    return (v[0], -v[1], v[2])


def _box(x, y=(0.0, 200.0), z=(-100.0, 200.0)):
    return {'box': ((x[0], y[0], z[0]), (x[1], y[1], z[1]))}


def _mirror_box(seed):
    (x0, y0, z0), (x1, y1, z1) = seed['box']
    return {'box': ((x0, -y1, z0), (x1, -y0, z1))}


def _stbd_name(n):
    return re.sub(r'\bport\b', 'starboard', n)


UP_Z = (0.0, 0.0, 1.0)
PORT = (0.0, 1.0, 0.0)
DOWN_Z = (0.0, 0.0, -1.0)

# The plate families laid over the prow flank and the bilge (port; starboard by name).
PLATES = ['Detail hull port base plates', 'Detail hull port bone plates', 'Detail hull port dkbrown plates',
          'Detail hull port steel plates', 'Detail hull port orange plates', 'Detail hull port seam backing',
          'Detail hull port hatch frames', 'Detail hull port hatch leaves', 'Detail hull port bay coamings',
          'Detail hull port vent throats']

FLANK = _n((0.16, 0.95, -0.27))
CHIN = _n((0.20, 0.40, -0.89))
BOW = (1.0, 0.0, 0.0)
# Bilge: each tile holds the whole slope (keel strake to the bilge's outer edge,
# 37-45 units in plane) with the hull's X running up the tile, cut at the
# 'Hull belly' slices so no slice is split; each seen along its own slices' normal.
BILGE = {1: ((148.0, 168.0), (0.33, 0.62, -0.72)), 2: ((128.0, 148.0), (0.27, 0.78, -0.57)),
         3: ((106.0, 127.0), (0.10, 0.86, -0.50)), 4: ((88.0, 104.0), (0.14, 0.83, -0.55)),
         5: ((74.0, 88.0), (0.10, 0.77, -0.63)), 6: ((60.0, 74.0), (0.09, 0.70, -0.71))}
KEEL_X = {1: (124.0, 166.0), 2: (83.0, 124.0), 3: (42.0, 83.0), 4: (1.0, 42.0), 5: (-40.0, 1.0)}
KEEL = ['Armour core keel strake 1', 'Armour core keel strake 1 aft', 'Detail hull keel steel plates',
        'Detail hull keel brown plates', 'Detail hull keel frames', 'Detail hull keel hatch leaves',
        'Detail hull keel throats']


def _part(objs, seed, d, up, what, **kw):
    return dict(members=[(o, seed) for o in objs], dir=d, up=up, cone=kw.pop('cone', .6), what=what, **kw)


def _twin(src, port):
    """The starboard mirror of a port part."""
    return dict(members=[(_stbd_name(o), _mirror_box(s)) for o, s in port['members']],
                dir=_stbd(port['dir']), up=_stbd(port['up']), cone=port['cone'],
                skin_from=src, mirror_u=True, what=port['what'].replace('port', 'starboard') + ' (mirror of port)')


PARTS = {}
# Prow flank: the plated band over the prow's port side (the bilge and chin start below Z -15).
for x, name in [((200.0, 227.0), 'nose'), ((172.0, 200.0), 'fwd'), ((140.0, 172.0), 'mid'), ((104.0, 140.0), 'aft')]:
    PARTS[f'prow-flank-{name}-port'] = _part(['Hull prow'] + PLATES, _box(x, z=(-15.0, 30.0)), FLANK, UP_Z,
                                             f'prow port flank X {x[0]:.0f}..{x[1]:.0f}: plated band over the brown-grey prow side')
# Forefoot: the prow chin under the bow, port half.
PARTS['forefoot-port'] = _part(['Hull prow'] + PLATES, _box((188.0, 226.0), y=(0.5, 200.0), z=(-40.0, -14.0)), CHIN, UP_Z,
                               'prow chin (forefoot) under the bow, port half')
# On slices 1, 2 and 5 a plate counts only when its centre lies 2 units inside the
# slice ends: the few plates straddling those ends differ port to starboard and broke
# the mirror (flipped-mask IoU 0.91-0.94; 0.997-1.000 inset). They keep their swatch.
BILGE_INSET = {1: 2.0, 2: 2.0, 5: 2.0}
for s_, (x, d) in BILGE.items():
    inner = (x[0] + BILGE_INSET.get(s_, 0.0), x[1] - BILGE_INSET.get(s_, 0.0))
    PARTS[f'bilge-{s_}-port'] = dict(
        members=[('Hull belly', _box(x, y=(4.0, 46.0), z=(-60.0, -15.0)))] + [(o, _box(inner, y=(4.0, 46.0), z=(-60.0, -15.0))) for o in PLATES],
        dir=_n(d), up=BOW, cone=.6, what=f'port bilge X {x[0]:.0f}..{x[1]:.0f}: the plated slope from the keel strake out, bow up the tile')
# Keel band: centre strake and keel plates, from below (bow to the left of the tile).
for s_, x in KEEL_X.items():
    PARTS[f'keel-{s_}'] = _part(KEEL, _box(x, y=(-11.0, 11.0), z=(-60.0, -30.0)), DOWN_Z, PORT,
                                f'keel band X {x[0]:.0f}..{x[1]:.0f} from below')
# The centre strake's slices run 40-60-74-88: the 74..88 slice would stretch keel-3
# to 48 units (14.2 texels per unit), so it is left to the grey swatch.
PARTS['keel-3']['members'] = [(o, _box((40.0, 74.0) if o == 'Armour core keel strake 1 aft' else KEEL_X[3], y=(-11.0, 11.0), z=(-60.0, -30.0)))
                              for o in KEEL]

for p in [p for p in PARTS if p.endswith('-port')]:
    PARTS[p[:-5] + '-stbd'] = _twin(p, PARTS[p])

SHEETS = {
    'hull-prow': ['prow-flank-nose-port', 'prow-flank-fwd-port', 'prow-flank-mid-port', 'prow-flank-aft-port'],
    'hull-bilge-a': ['forefoot-port', 'bilge-1-port', 'bilge-2-port', 'bilge-3-port'],
    'hull-bilge-b': ['bilge-4-port', 'bilge-5-port', 'bilge-6-port', 'keel-5'],
    'hull-keel': ['keel-1', 'keel-2', 'keel-3', 'keel-4'],
}
