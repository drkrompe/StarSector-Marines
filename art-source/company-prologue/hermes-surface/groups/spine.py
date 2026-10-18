"""Spine: the cream main hull (cockpit, troop bay and the fairing it flares into).

`Main hull - spine` is one object from the nose to X -24, including the
fairing aft of X 10 that rolls down to the wing edge. One sheet: its port
flank (the starboard flank is the mirror), its dorsal deck with the ridge,
spine and well plates bolted to it and the cheek fairings, the glazed nose
seen from ahead, and its belly with the service covers.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW, DOWN_X

SPINE = 'Main hull - spine'
RIDGE = [(f'Hull dorsal ridge plate {i}', (x, 0.0, 10.5)) for i, x in
         ((1, 2.0), (2, 6.1), (3, 10.5), (4, 15.0), (5, 19.7), (6, 24.1), (7, 28.0))]
PLATES = [(f'Hull spine plate {i} {s}', (x, y, 10.3)) for i, x in ((2, 6.1), (4, 15.0))
          for s, y in (('port', 5.3), ('starboard', -5.3))]
WELL = [(f'Hull well plate {i} {s}', (x, y, 10.3)) for i, x in ((1, -8.0), (2, -2.8))
        for s, y in (('port', 5.3), ('centre', 0.0), ('starboard', -5.3))]

PARTS = {
    'spine-side-port': dict(members=[(SPINE, (25.0, 12.0, 0.0)), (SPINE, (-5.0, 24.0, -4.0))],
                            dir=PORT, up=UP_Z, cone=.6, what='spine port flank, nose cheek to the fairing flank'),
    'spine-side-stbd': dict(members=[(SPINE, (25.0, -12.0, 0.0)), (SPINE, (-5.0, -24.0, -4.0))],
                            dir=STARBOARD, up=UP_Z, cone=.6, skin_from='spine-side-port', mirror_u=True,
                            what='spine starboard flank (mirror of port)'),
    'spine-top': dict(members=[(SPINE, (20.0, 0.0, 10.0)), (SPINE, (-5.0, 15.0, 8.0)),
                               ('Hull port cheek fairing', (6.6, 11.5, 7.9)),
                               ('Hull starboard cheek fairing', (6.6, -11.5, 7.9))] + RIDGE + PLATES + WELL,
                      dir=UP_Z, up=DOWN_X, cone=.6, what='spine dorsal deck with its plates and the fairing deck'),
    'spine-nose': dict(members=[(SPINE, (42.0, 0.0, -4.0)), ('Nose cap plate', (42.1, 0.0, -1.0))],
                       dir=BOW, up=UP_Z, cone=.6, what='the nose seen from ahead, glazing frame and chin'),
    'spine-under': dict(members=[(SPINE, (20.0, 0.0, -10.0)), ('Hull belly service covers', (5.0, 0.0, -10.1))],
                        dir=DOWN_Z, up=DOWN_X, cone=.6, what='spine belly with its service covers'),
}

SHEETS = {
    'spine': ['spine-side-port', 'spine-top', 'spine-nose', 'spine-under'],
}
