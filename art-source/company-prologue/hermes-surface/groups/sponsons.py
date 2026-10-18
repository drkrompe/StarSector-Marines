"""Sponsons: the grey gun sponsons (`Wing body`, one object across both sides).

Flat-decked octagons over raked walls. One sheet of the port sponson: its
outboard wall, deck and raked bow face (the starboard sponson takes them as
mirrors), and the whole wing-body underside, which is one connected face group. Seen from above or below the camera's up is the
bow, so a starboard mirror is a horizontal flip.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, DOWN_X, BOW_QUARTER, BOW_QUARTER_STBD

WING = 'Wing body'

PARTS = {
    'sponson-wall-port': dict(members=[(WING, (14.0, 26.0, -3.0))], dir=PORT, up=UP_Z, cone=.6,
                              what='port sponson outboard wall'),
    'sponson-top-port': dict(members=[(WING, (19.0, 12.0, 5.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                             what='port sponson deck round the turret'),
    'sponson-front-port': dict(members=[(WING, (21.0, 21.0, -3.0))], dir=BOW_QUARTER, up=UP_Z, cone=.8,
                               what='port sponson raked bow face'),
    'sponson-under': dict(members=[(WING, (14.0, 18.0, -10.0)), ('Hull belly wing port service cover', (0.25, 21.0, -10.1)),
                                   ('Hull belly wing starboard service cover', (0.25, -21.0, -10.1))],
                          dir=DOWN_Z, up=DOWN_X, cone=.6, what='the wing body underside, both sponsons (one connected face group)'),
    'sponson-wall-stbd': dict(members=[(WING, (14.0, -26.0, -3.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                              skin_from='sponson-wall-port', mirror_u=True, what='starboard sponson wall (mirror)'),
    'sponson-top-stbd': dict(members=[(WING, (19.0, -12.0, 5.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                             skin_from='sponson-top-port', mirror_u=True, what='starboard sponson deck (mirror)'),
    'sponson-front-stbd': dict(members=[(WING, (21.0, -21.0, -3.0))], dir=BOW_QUARTER_STBD, up=UP_Z, cone=.8,
                               skin_from='sponson-front-port', mirror_u=True, what='starboard sponson bow face (mirror)'),
}

SHEETS = {
    'sponsons': ['sponson-wall-port', 'sponson-top-port', 'sponson-front-port', 'sponson-under'],
}
