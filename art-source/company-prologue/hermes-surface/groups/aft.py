"""Aft: the grey drive block (`Aft drive block`) and its cream horseshoe arms.

One sheet: the block's port side (starboard is the mirror), its deck with the
turret-well deck plates, its stern face round the three drives, and the
port horseshoe arm seen from above (the starboard arm is its mirror).
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X, STERN

AFT = 'Aft drive block'
ARM = 'Hull aft arm {} segment {}'

PARTS = {
    'aft-side-port': dict(members=[(AFT, (-27.0, 21.0, -1.0))], dir=PORT, up=UP_Z, cone=.6,
                          what='drive block port side'),
    'aft-side-stbd': dict(members=[(AFT, (-27.0, -21.0, -1.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                          skin_from='aft-side-port', mirror_u=True, what='drive block starboard side (mirror)'),
    'aft-top': dict(members=[(AFT, (-30.0, 10.0, 8.0)), ('Hull aft turret well deck port', (-15.0, 4.0, 10.3)),
                             ('Hull aft turret well deck starboard', (-15.0, -4.0, 10.3))],
                    dir=UP_Z, up=DOWN_X, cone=.6, what='drive block deck and the turret-well deck plates'),
    'aft-stern': dict(members=[(AFT, (-37.0, 16.0, -2.0))], dir=STERN, up=UP_Z, cone=.6,
                      what='drive block stern face round the drives'),
    'aft-arm-port': dict(members=[(ARM.format('port', i), c) for i, c in
                                  ((1, (-3.5, 9.0, 11.0)), (2, (-12.0, 10.6, 11.0)), (3, (-21.0, 13.3, 11.0)), (4, (-30.0, 15.7, 10.6)))],
                         dir=UP_Z, up=DOWN_X, cone=.5, what='port horseshoe arm from above'),
    'aft-arm-stbd': dict(members=[(ARM.format('starboard', i), c) for i, c in
                                  ((1, (-3.5, -9.0, 11.0)), (2, (-12.0, -10.6, 11.0)), (3, (-21.0, -13.3, 11.0)), (4, (-30.0, -15.7, 10.6)))],
                         dir=UP_Z, up=DOWN_X, cone=.5, skin_from='aft-arm-port', mirror_u=True,
                         what='starboard horseshoe arm (mirror)'),
}

SHEETS = {
    'aft': ['aft-side-port', 'aft-top', 'aft-stern', 'aft-arm-port'],
}
