"""Deck: the lifting-body wing from above and the cabin roof.

`Hull wing body` is one object across both sides, and its deck is one
polygon from tip to tip, so it is one tile, not a port tile and its mirror. The cabin spine's roof and the
turret hump's deck share a tile seen from above.
"""
from parts_common import STARBOARD, UP_Z, DOWN_X

WING = 'Hull wing body'
SPINE = 'Hull dorsal spine'
HUMP = 'Hull aft hump'

PARTS = {
    'wing-top': dict(members=[(WING, (-10.0, 30.0, 0.8))], dir=UP_Z, up=DOWN_X, cone=.6,
                     what='the whole wing deck from above, both sides (its top is one polygon across the ship)'),
    'spine-top': dict(members=[(SPINE, (15.0, 0.0, 9.5)), (HUMP, (-15.0, 0.0, 7.5))], dir=UP_Z, up=DOWN_X, cone=.6,
                      what='cabin roof and turret hump deck from above'),
}

SHEETS = {
    'deck': ['wing-top', 'spine-top'],
}
