"""Tanks: the three spherical cargo domes standing half proud of the deck.

`Tank 1 aft sphere` (X 0, r 15), `Tank 2 mid sphere` (X 44, r 14.5) and
`Tank 3 fore sphere` (X 88, r 15.5). One sheet: each crown from above and the
mid sphere's port side; the other sides reuse that tile (the spheres differ
by a few percent), mirrored for starboard.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X

T1, T2, T3 = 'Tank 1 aft sphere', 'Tank 2 mid sphere', 'Tank 3 fore sphere'

PARTS = {
    'tank1-top': dict(members=[(T1, None)], dir=UP_Z, up=DOWN_X, cone=.5, what='aft cargo dome crown from above'),
    'tank2-top': dict(members=[(T2, None)], dir=UP_Z, up=DOWN_X, cone=.5, what='mid cargo dome crown from above'),
    'tank3-top': dict(members=[(T3, None)], dir=UP_Z, up=DOWN_X, cone=.5, what='fore cargo dome crown from above'),
    'tank2-side-port': dict(members=[(T2, None)], dir=PORT, up=UP_Z, cone=.55, what='mid cargo dome seen from port'),
    'tank1-side-port': dict(members=[(T1, None)], dir=PORT, up=UP_Z, cone=.55, skin_from='tank2-side-port',
                            what='aft dome port side (reuses the mid dome)'),
    'tank3-side-port': dict(members=[(T3, None)], dir=PORT, up=UP_Z, cone=.55, skin_from='tank2-side-port',
                            what='fore dome port side (reuses the mid dome)'),
    'tank1-side-stbd': dict(members=[(T1, None)], dir=STARBOARD, up=UP_Z, cone=.55, skin_from='tank2-side-port',
                            mirror_u=True, what='aft dome starboard side (mirror)'),
    'tank2-side-stbd': dict(members=[(T2, None)], dir=STARBOARD, up=UP_Z, cone=.55, skin_from='tank2-side-port',
                            mirror_u=True, what='mid dome starboard side (mirror)'),
    'tank3-side-stbd': dict(members=[(T3, None)], dir=STARBOARD, up=UP_Z, cone=.55, skin_from='tank2-side-port',
                            mirror_u=True, what='fore dome starboard side (mirror)'),
}

SHEETS = {
    'tanks': ['tank1-top', 'tank2-top', 'tank3-top', 'tank2-side-port'],
}
