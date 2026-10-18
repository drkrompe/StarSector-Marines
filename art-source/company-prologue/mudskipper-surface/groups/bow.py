"""Bow: the forward end of the Mudskipper.

Four sources: the whole bow face seen from dead ahead (the main hull's one
big bow plane with the cockpit block's stem standing proud of it, the tan
fender blocks on their steel backing plates, the white bow face armour
modules and both sponsons' forward faces), the bow top rake seen along its
own normal (main hull rake, cockpit block rake, bridge cab windscreen rake,
visor brow and windscreen sill), the cockpit block and bridge cab from
directly above, and the port sponson's rounded bow-quarter face, whose
starboard twin is a mirror reuse.

Glazing, lamps, the docking collar, antennae and the cab's narrow side faces
stay to swatches.
"""
from math import sqrt
from parts_common import HULL, BOW, UP_Z, DOWN_X

COCKPIT = 'Bow cockpit block'
CAB = ('Bridge cab centre section', 'Bridge cab port section', 'Bridge cab starboard section')
RAKE = (0.5, 0.0, sqrt(0.75))           # main hull bow top rake, n (0.50, 0, 0.87)
SPONSON_Q = (0.6, 0.8, 0.0)             # port sponson bow-quarter face
SPONSON_Q_STBD = (0.6, -0.8, 0.0)

_fenders = [('Bow fender block', (40.8, 6.3, 3.0)), ('Bow fender block.001', (40.8, -6.3, 3.0)),
            ('Bow fender block.002', (41.8, 12.2, 0.0)), ('Bow fender block.003', (41.8, -12.2, 0.0)),
            ('Bow fender block.004', (41.8, 19.2, 0.0)), ('Bow fender block.005', (41.8, -19.2, 0.0)),
            ('Bow fender backing plate', (40.1, 12.2, 2.8)), ('Bow fender backing plate.001', (40.1, -12.2, 2.8)),
            ('Bow fender backing plate.002', (40.1, 19.2, 2.8)),
            ('Bow fender backing plate.003', (40.1, -19.2, 2.8))]
_bow_modules = [('Port bow face armour module', (39.7, 17.4, 5.1)),
                ('Port bow face armour module.001', (39.7, 10.9, 5.1)),
                ('Starboard bow face armour module', (39.7, -17.4, 5.1)),
                ('Starboard bow face armour module.001', (39.7, -10.9, 5.1)),
                ('Port bow face seam backing', (39.5, 14.0, 3.3)),
                ('Starboard bow face seam backing', (39.5, -14.0, 3.3))]

PARTS = {
    'bow-face': dict(members=[(HULL, (39.5, 15.0, -3.0)), (COCKPIT, (42.5, 0.8, 3.2)), (COCKPIT, (42.5, 0.0, 7.0)),
                              ('Port forebody sponson', (39.6, 22.7, 0.2)),
                              ('Starboard forebody sponson', (39.6, -22.7, 0.2))] + _fenders + _bow_modules,
                     dir=BOW, up=UP_Z, cone=.7,
                     what='whole bow face from dead ahead: white bow plate, cockpit block stem, tan fender blocks on '
                          'steel backing plates, white armour modules, grey-green sponson fronts'),
    'bow-rake': dict(members=[(HULL, (38.6, 12.0, 8.1)), (COCKPIT, (40.2, 0.0, 8.9)),
                              (CAB[0], (34.9, 0.0, 11.5)), (CAB[1], (34.9, 6.3, 11.5)), (CAB[2], (34.9, -6.3, 11.5)),
                              ('Bridge visor brow', (34.1, 0.0, 12.7)), ('Bridge windscreen sill', (37.4, 0.0, 10.2))],
                     dir=RAKE, up=UP_Z, cone=.9,
                     what='bow top rake: main hull rake, cockpit block rake, bridge cab windscreen rake, visor and sill'),
    'bow-cockpit-top': dict(members=[(COCKPIT, (34.8, 0.0, 9.6)),
                                     (CAB[0], (31.8, 0.0, 12.6)), (CAB[1], (31.8, 6.3, 12.6)),
                                     (CAB[2], (31.8, -6.3, 12.6))],
                            dir=UP_Z, up=DOWN_X, cone=.8,
                            what='cockpit block crown and bridge cab roof from above'),
    'bow-sponson-port': dict(members=[('Port forebody sponson', (36.6, 26.2, -1.1))], dir=SPONSON_Q, up=UP_Z, cone=.78,
                             what='port forebody sponson, rounded bow-quarter face'),
    'bow-sponson-stbd': dict(members=[('Starboard forebody sponson', (36.6, -26.2, -1.1))], dir=SPONSON_Q_STBD,
                             up=UP_Z, cone=.78, skin_from='bow-sponson-port', mirror_u=True,
                             what='starboard forebody sponson bow-quarter face (mirror of port)'),
}

SHEETS = {
    'bow-a': ['bow-face', 'bow-rake', 'bow-cockpit-top', 'bow-sponson-port'],
}
