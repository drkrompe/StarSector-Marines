"""Body: the central body, the aft body and castle, the ventral keel and the drive block.

`Hull central body` is one prism (keel chamfer, wall, shoulder chamfer) cut by
deck and wall seam grooves and recesses, so every member is a box. The port
and starboard walls carry different hatches and collars and get their own
tiles. `Hull aft body` and `Hull aft castle` are seen together from above;
`Hull drive block`'s stern face is the hero of S7 (from behind, about 650 m).

Left to the class swatches: the four drum castings (round: no square-on
view), the command module, the tanks, the drive cans and every detail part.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, STERN, DOWN_X


def _box(x0, x1, y0, y1, z0, z1):
    return {'box': ((x0, y0, z0), (x1, y1, z1))}


BODY, AFT, CASTLE = 'Hull central body', 'Hull aft body', 'Hull aft castle'
KEEL, DRIVE = 'Hull ventral keel', 'Hull drive block'

PARTS = {
    'body-deck': dict(members=[(BODY, _box(-25.0, 42.0, -43.0, 45.0, 6.0, 11.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                      what='central body deck from above, bow at the top'),
    'body-wall-port': dict(members=[(BODY, _box(-25.0, 42.0, 30.0, 45.0, -14.0, 10.0))], dir=PORT, up=UP_Z,
                           cone=.6, what='central body port wall, bow to the left'),
    'body-wall-stbd': dict(members=[(BODY, _box(-25.0, 42.0, -43.0, -28.0, -14.0, 10.0))], dir=STARBOARD,
                           up=UP_Z, cone=.6, what='central body starboard wall, bow to the right'),
    'stern-face': dict(members=[(DRIVE, _box(-75.0, -66.0, -40.0, 40.0, -12.0, 8.0))], dir=STERN, up=UP_Z,
                       cone=.6, what='drive block stern face seen from astern, round the two main drive mouths'),
    'castle-top': dict(members=[(AFT, _box(-57.0, -19.0, -46.0, 48.0, 8.0, 12.0)),
                                (CASTLE, _box(-56.0, -34.0, -22.0, 24.0, 12.0, 17.0))], dir=UP_Z, up=DOWN_X,
                       cone=.6, what='aft body deck and the raised castle from above'),
    'keel-bottom': dict(members=[(KEEL, _box(-51.0, 31.0, -20.0, 22.0, -21.0, -12.0)),
                                 (BODY, _box(-25.0, 42.0, -43.0, 45.0, -14.0, -10.0))], dir=DOWN_Z, up=DOWN_X,
                        cone=.6, what='the belly from below: ventral keel and central body keel'),
    'drive-top': dict(members=[(DRIVE, _box(-74.0, -51.0, -40.0, 40.0, 3.0, 8.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                      what='drive block deck from above, between and around the drive cans'),
    'aft-wall-port': dict(members=[(AFT, _box(-57.0, -19.0, 30.0, 47.0, -14.0, 11.0)),
                                   (CASTLE, _box(-56.0, -34.0, 14.0, 23.0, 8.0, 17.0))], dir=PORT, up=UP_Z,
                          cone=.6, what='aft body and castle port walls, between the drum towers'),
    'aft-wall-stbd': dict(members=[(AFT, _box(-57.0, -19.0, -45.0, -28.0, -14.0, 11.0)),
                                   (CASTLE, _box(-56.0, -34.0, -21.0, -12.0, 8.0, 17.0))], dir=STARBOARD,
                          up=UP_Z, cone=.6, skin_from='aft-wall-port', mirror_u=True,
                          what='aft body and castle starboard walls (mirror of port)'),
}

SHEETS = {
    'body': ['body-deck', 'body-wall-port', 'body-wall-stbd', 'stern-face'],
    'aft': ['castle-top', 'keel-bottom', 'drive-top', 'aft-wall-port'],
}
