"""Arms: the twin forward cargo arms, port tiles and their starboard mirrors.

Each `Hull <side> arm` is a faceted loft (ts_hull.ARM_SECTION): a canted keel
facet, a bulged outboard pod wall that cants back in, a raised pod deck that
steps down to a lower inboard strip, and a bow tapering in plan and height.
Its facets are cut by ring and deck seam grooves, so every member is a box
claiming each qualifying face. The arms are mirror images about Y = 1 (the
detail differs, not the base), so the starboard arm takes the port tiles
mirrored.

Left to the class swatches: the inboard walls in the gap, the bow drums, and
everything the detail modules lay on top (plates, strakes, doors, masts).
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW, DOWN_X


def _box(x0, x1, y0, y1, z0, z1):
    return {'box': ((x0, y0, z0), (x1, y1, z1))}


def _arm(side):
    return f'Hull {side} arm'


PARTS = {
    'arm-top-port': dict(members=[(_arm('port'), _box(40.0, 111.0, 9.0, 46.0, 2.0, 11.0))], dir=UP_Z, up=DOWN_X,
                         cone=.6, what='port arm from above: lower inboard strip, angled step, raised pod deck, '
                                       'raked bow, bow at the top'),
    'arm-wall-port': dict(members=[(_arm('port'), _box(40.0, 111.0, 25.0, 46.0, -10.0, 9.0))], dir=PORT, up=UP_Z,
                          cone=.6, what='port arm outboard pod wall from port: the bulged lower and upper wall '
                                        'facets, bow to the left'),
    'arm-bow-port': dict(members=[(_arm('port'), _box(86.0, 112.0, 9.0, 46.0, -9.0, 10.0))], dir=BOW, up=UP_Z,
                         cone=.55, what='port arm bow facets seen from ahead'),
    'arm-keel-port': dict(members=[(_arm('port'), _box(40.0, 111.0, 9.0, 46.0, -10.0, -3.0))], dir=DOWN_Z,
                          up=DOWN_X, cone=.6, what='port arm keel from below: flat keel and canted keel facet'),
}
for _p in ('top', 'wall', 'bow', 'keel'):
    _src = PARTS[f'arm-{_p}-port']
    _m = [(_arm('starboard'), {'box': ((b[0][0], 2.0-b[1][1], b[0][2]), (b[1][0], 2.0-b[0][1], b[1][2]))})
          for _, s in _src['members'] for b in [s['box']]]
    PARTS[f'arm-{_p}-stbd'] = dict(members=_m, dir=STARBOARD if _p == 'wall' else _src['dir'], up=_src['up'],
                                   cone=_src['cone'], skin_from=f'arm-{_p}-port', mirror_u=True,
                                   what=_src['what'].replace('port', 'starboard') + ' (mirror of port)')

SHEETS = {
    'arms': ['arm-top-port', 'arm-wall-port', 'arm-bow-port', 'arm-keel-port'],
}
