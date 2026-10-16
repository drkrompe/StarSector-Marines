"""Decks and spine (dorsal) plus the keel (ventral): one 2x2 sheet, one mirrored reuse.

The forward deck top, the spine roofs as one multi-member tile, the spine's
port side (mirrored onto the starboard side for free) and the keel plate's
underside. The waist deck, the aft deck and the module tops are left to the
class swatch: they are either mostly covered by fittings other groups own or
too large a single face to reach a useful texel density.
"""
from parts_common import HULL, PORT, STARBOARD, UP_Z, DOWN_Z, DOWN_X

_SPINE_TOP = [
    ('Command deck armoured roof', (13, 0, 20.6)),
    ('Command deck visor brow', (13.2, 0, 20.1)),
    ('Command deck housing', (12.2, 0, 17.5)),
    ('Spine waist housing', (3.5, 0, 17.5)),
    ('Spine roof walkway', (2.6, 0, 17.7)),
    ('Spine aft cap', (-2.1, 0, 17.5)),
    ('Spine trunk segment', (20.4, 0, 17.2)),
    ('Spine trunk segment.001', (23.8, 0, 17.2)),
    ('Spine trunk segment.002', (27.1, 0, 17.2)),
    ('Spine trunk nose', (29.5, 0, 17.2)),
    ('Spine dark core', (13.1, 0, 16.7)),
]


def _side(sign):
    return [
        ('Command deck housing', (12.4, 8.0 * sign, 12.5)),
        ('Command deck lower bulkhead', (12.8, 5.1 * sign, 17.9)),
        ('Command deck visor brow', (12.9, 5.3 * sign, 19.8)),
        ('Spine waist housing', (3.5, 8.0 * sign, 12.5)),
        ('Spine aft cap', (-2.0, 8.0 * sign, 12.5)),
        ('Spine dark core', (12.9, 6.5 * sign, 12.3)),
        ('Spine trunk segment', (20.4, 6.9 * sign, 12.5)),
        ('Spine trunk segment.001', (23.8, 6.9 * sign, 12.5)),
        ('Spine trunk segment.002', (27.1, 6.9 * sign, 12.5)),
    ]


PARTS = {
    'deck-fore-top': dict(members=[(HULL, (34.6, 0, 14.5))], dir=UP_Z, up=DOWN_X, cone=.95,
                          what='forward deck top (main hull, z 14.5), oxide; raked bow glacis left to the bow group'),
    'spine-top': dict(members=_SPINE_TOP, dir=UP_Z, up=PORT, cone=.9,
                      what='spine roofs seen from above: command deck roof, cream housings, gunmetal trunk segments, dark core'),
    'spine-side-port': dict(members=_side(1), dir=PORT, up=UP_Z, cone=.6,
                            what='spine and command deck housings, port faces'),
    'spine-side-stbd': dict(members=_side(-1), dir=STARBOARD, up=UP_Z, cone=.6,
                            skin_from='spine-side-port', mirror_u=True,
                            what='spine and command deck housings, starboard faces (mirror of the port side)'),
    'keel-plate': dict(members=[('Keel plate', (26.7, 0, -9.2))], dir=DOWN_Z, up=DOWN_X, cone=.75,
                       what='keel plate underside, gunmetal'),
}

SHEETS = {
    'decks-a': ['deck-fore-top', 'spine-top', 'spine-side-port', 'keel-plate'],
}
