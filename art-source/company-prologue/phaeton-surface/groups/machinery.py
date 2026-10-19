"""Machinery: the gunmetal strakes along both flanks, the aft spine and the drive block top.

`Hull starboard machinery strake` (X -38..124) and `Hull port machinery
strake` (X -38..104, broken by the gallery) are the pump rooms and pipe
galleries below the deck line; `Hull aft machinery spine` joins the hull to
`Drive block` across the stern.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X

SSTRAKE, PSTRAKE = 'Hull starboard machinery strake', 'Hull port machinery strake'
SPINE, BLOCK = 'Hull aft machinery spine', 'Drive block'

PARTS = {
    'strake-stbd': dict(members=[(SSTRAKE, None)], dir=STARBOARD, up=UP_Z, cone=.6,
                        what='starboard machinery strake outboard face, bow to the right'),
    'strake-port': dict(members=[(PSTRAKE, None)], dir=PORT, up=UP_Z, cone=.6,
                        what='port machinery strake outboard face, bow to the left'),
    'spine-top': dict(members=[(SPINE, None)], dir=UP_Z, up=DOWN_X, cone=.6,
                      what='aft machinery spine top around the reactor dome'),
    'block-top': dict(members=[(BLOCK, None)], dir=UP_Z, up=DOWN_X, cone=.6,
                      what='stern drive block top across the full beam'),
}

SHEETS = {
    'machinery': ['strake-stbd', 'strake-port', 'spine-top', 'block-top'],
}
