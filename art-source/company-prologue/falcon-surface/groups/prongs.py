"""Prongs: the two long armoured prongs that are most of the Falcon's plan.

Each prong is a loft (fc_hull.prong_section): inner wall, a small chamfer, the
dorsal deck out to the turret ridge, the outboard glacis, a short belt, the
chine and the keel. S10 looks down the port prong's deck and glacis from behind
and above, and across the waist at the starboard prong's inner wall; S4-S6 see
the prongs' tops whole.

Deck and glacis share one tile per X segment, seen from their bisector (cone
0.85 takes both and leaves the belt, 25 deg further round, to its own tile).
The deck and glacis plates carry the sprite's pirate livery per plate
(fc_detail.Livery): khaki, with plates repainted red or sand where falcon_p.png
paints its slashes. The two prongs' liveries differ, so each prong's top tiles
are their own generations. Belts and inner walls are plain khaki and grey and
built Y-symmetric: starboard reuses port mirrored.

Left to the class swatches: the inner chamfer, the chine and keel, the bow
tips forward of X 110, and every chamfer more than ~30 deg off its tile.
"""
from math import sqrt

from parts_common import PORT, STARBOARD, UP_Z


def _n(v):
    s = sqrt(sum(c * c for c in v))
    return tuple(c / s for c in v)


def _stbd(v):
    return (v[0], -v[1], v[2])


TOP_DIR = _n((0.0, 0.21, 0.98))     # bisector of the deck (0,-.08,1) and glacis (0,.49,.87)
BELT_DIR = _n((0.0, 0.81, 0.58))
WALL_DIR = STARBOARD                 # the port prong's inner wall faces the waist
TOP_X = [(-48.0, -10.0), (-10.0, 28.0), (28.0, 66.0), (66.0, 110.0)]
SIDE_X = [(-48.0, -9.0), (-9.0, 30.0), (30.0, 69.0), (69.0, 110.0)]
TOP_OBJECTS = ['Hull {s} prong', 'Hull {s} prong deck plating', 'Hull {s} prong glacis plating',
               'Hull {s} prong ridge cap', 'Hull {s} prong patch plates', 'Hull {s} prong patch bolts',
               'Hull {s} prong hatches', 'Hull {s} prong fittings']


def _box(x, side, z=(-40.0, 60.0), y=(0.0, 100.0)):
    y = y if side == 'port' else (-y[1], -y[0])
    return {'box': ((x[0], y[0], z[0]), (x[1], y[1], z[1]))}


def _up(d, side):
    """Screen-up so the tile's u runs bow-ward along X."""
    # u = up x dir must be +X: up = dir x X
    return _n((0.0, d[2], -d[1])) if side == 'port' else _n((0.0, -d[2], d[1]))


PARTS = {}
for _s in ('port', 'starboard'):
    _d = TOP_DIR if _s == 'port' else _stbd(TOP_DIR)
    for _k, _x in enumerate(TOP_X, 1):
        PARTS[f'top-{_k}-{_s}'] = dict(
            members=[(o.format(s=_s), _box(_x, _s)) for o in TOP_OBJECTS], dir=_d, up=_up(_d, _s), cone=.85,
            what=f'{_s} prong deck and glacis, X {_x[0]:g} to {_x[1]:g}: khaki armour plates with pirate red and sand repaint '
                 'plates, the grey ridge cap along the turret line, red patch slabs and hatches')
for _k, _x in enumerate(SIDE_X, 1):
    PARTS[f'belt-{_k}-port'] = dict(
        members=[('Hull port prong belt plating', _box(_x, 'port')), ('Hull port prong', _box(_x, 'port', z=(-40.0, 3.2)))],
        dir=BELT_DIR, up=_up(BELT_DIR, 'port'), cone=.8,
        what=f'port prong outboard belt, X {_x[0]:g} to {_x[1]:g}: grey belt armour plates over khaki')
    PARTS[f'belt-{_k}-starboard'] = dict(
        members=[('Hull starboard prong belt plating', _box(_x, 'starboard')),
                 ('Hull starboard prong', _box(_x, 'starboard', z=(-40.0, 3.2)))],
        dir=_stbd(BELT_DIR), up=_up(_stbd(BELT_DIR), 'starboard'), cone=.8, skin_from=f'belt-{_k}-port', mirror_u=True,
        what=f'starboard prong outboard belt, X {_x[0]:g} to {_x[1]:g} (mirror of port)')
    PARTS[f'wall-{_k}-port'] = dict(
        members=[('Hull port prong', _box(_x, 'port', y=(0.0, 45.0))), ('Hull port prong fittings', _box(_x, 'port', y=(0.0, 45.0)))],
        dir=WALL_DIR, up=UP_Z, cone=.8,
        what=f'port prong inner wall facing the waist, X {_x[0]:g} to {_x[1]:g}: khaki armour with rows of small viewports')
    PARTS[f'wall-{_k}-starboard'] = dict(
        members=[('Hull starboard prong', _box(_x, 'starboard', y=(0.0, 45.0))),
                 ('Hull starboard prong fittings', _box(_x, 'starboard', y=(0.0, 45.0)))],
        dir=PORT, up=UP_Z, cone=.8, skin_from=f'wall-{_k}-port', mirror_u=True,
        what=f'starboard prong inner wall, X {_x[0]:g} to {_x[1]:g} (mirror of port)')

SHEETS = {
    'prongs-top-port': [f'top-{k}-port' for k in range(1, 5)],
    'prongs-top-starboard': [f'top-{k}-starboard' for k in range(1, 5)],
    'prongs-belt': [f'belt-{k}-port' for k in range(1, 5)],
    'prongs-wall': [f'wall-{k}-port' for k in range(1, 5)],
}
