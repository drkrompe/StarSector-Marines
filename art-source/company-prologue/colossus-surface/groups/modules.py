"""Modules: the four maroon side cargo modules (port sources, starboard mirrored).

Each module (rel Y 46..88 outboard of the hold, keel Z -46 to deck Z 14; fore
X 33..90.5, aft X -19..33) is one mesh whose outboard wall, deck and keel are
grids of flat plates parted by seam grooves. Three faces of each are skinned:

- the outboard wall (about 52 x 50 units), cut by box at its Z -11 seam row
  into an upper band (the upper cargo door row) and a lower band (the lower
  door row), so each tile keeps at least 15 texels per unit. The door leaves
  (separate maroon objects in the door recesses) ride on the same tile. The
  full-height end strips (fore X 79..86, aft X -12..-8 and 30..32), the
  vertical groove floors and the horizontal groove floors that run across
  those strips cross the cut, so `pad` leaves them to the swatch. The upper
  band holds 16 texels per unit, the lower band (27.9 units of plate between
  seams) 13.8;
- the deck (about 51 x 37 units), one tile per module: at 10.4 texels per
  unit, below the target, because two sheets cannot also split it;
- the keel's flat (about 43 x 28 units) round the lift-thruster well, 13.7.

Every starboard twin reuses its port source mirrored (mirror_u).

Left to the class swatches: the 45-degree chamfers round each module, the
outboard bow corner and the aft module's aft corner, the inboard wall (one
2,800-unit polygon facing the hold), the open bow/aft container bays (bow
armour, end caps, back walls; the containers keep their own materials), the
deck bands, turret rings, tie-downs, covers and vents, the door frames,
hazard stripes and stiffeners, personnel hatches, fender rails and brackets,
and every keel fitting (lift thrusters, clamps, tiles).
"""
import re

PORT = (0.0, 1.0, 0.0)
UP_Z = (0.0, 0.0, 1.0)
DOWN_Z = (0.0, 0.0, -1.0)
MIRROR_Y = 1.5          # the hull is symmetric about Y = +1.5


def _stbd(v):
    return (v[0], -v[1], v[2])


def _box(x, y, z, pad=None):
    s = {'box': ((x[0], y[0], z[0]), (x[1], y[1], z[1]))}
    if pad is not None:
        s['pad'] = pad
    return s


def _mirror_seed(seed):
    (x0, y0, z0), (x1, y1, z1) = seed['box']
    s = dict(seed, box=((x0, 2 * MIRROR_Y - y1, z0), (x1, 2 * MIRROR_Y - y0, z1)))
    return s


def _stbd_name(n):
    return re.sub(r'\bport\b', 'starboard', n)


def _twin(src, port):
    return dict(members=[(_stbd_name(o), _mirror_seed(s)) for o, s in port['members']],
                dir=_stbd(port['dir']), up=_stbd(port['up']), cone=port['cone'],
                skin_from=src, mirror_u=True, what=port['what'].replace('port', 'starboard') + ' (mirror of port)')


MOD = {'f': ('Cargo port fore module', (33.0, 91.0)), 'a': ('Cargo port aft module', (-21.0, 33.0))}
NAME = {'f': 'fore', 'a': 'aft', 'hi': 'upper', 'lo': 'lower'}
# The wall tiles stop short of the full-height end strips: their X boxes (with `pad`)
# also drop the horizontal groove floors that run on across those strips.
WALL_X = {'f': (36.0, 79.0), 'a': (-8.0, 29.5)}
WALL_Y, DECK_Y, KEEL_Y = (88.0, 90.0), (46.0, 90.0), (46.0, 90.0)
# The outboard wall's Z -11 seam row (grooves at -11.1..-10.6) parts the two door rows.
# Both bands leave the groove floor itself to the swatch (pad 0.2 excludes it), so the
# lower band is the plates' own 27.9 units (13.8 texels per unit, the most one tile holds).
BANDS = {'hi': (-10.75, 11.5), 'lo': (-39.3, -10.95)}
# Part names stay short: this copy's tile paths sit near Windows' 260-character limit.

PARTS = {}
for m, (obj, x) in MOD.items():
    for b, z in BANDS.items():
        seed = _box(WALL_X[m], WALL_Y, z, pad=0.2)
        doors = f'{obj} {"cargo" if b == "hi" else "lower cargo"} door leaves'
        PARTS[f'wall-{m}-{b}-port'] = dict(
            members=[(obj, seed), (doors, seed)], dir=PORT, up=UP_Z, cone=.8,
            what=f'{NAME[m]} cargo module port outboard wall, {NAME[b]} band (the {NAME[b]} cargo door row), bow to the left')
    PARTS[f'deck-{m}-port'] = dict(members=[(obj, _box(x, DECK_Y, (13.0, 15.0)))], dir=UP_Z, up=PORT, cone=.8,
                                   what=f'{NAME[m]} cargo module port deck from above, bow to the right, outboard up')
    PARTS[f'keel-{m}-port'] = dict(members=[(obj, _box(x, KEEL_Y, (-47.0, -44.0)))], dir=DOWN_Z, up=PORT, cone=.8,
                                   what=f'{NAME[m]} cargo module port keel flat from below, bow to the left, outboard up')

for p in [p for p in PARTS if p.endswith('-port')]:
    PARTS[p[:-5] + '-stbd'] = _twin(p, PARTS[p])

SHEETS = {
    'modules-wall': ['wall-f-hi-port', 'wall-f-lo-port', 'wall-a-hi-port', 'wall-a-lo-port'],
    'modules-deck': ['deck-f-port', 'deck-a-port', 'keel-f-port', 'keel-a-port'],
}
