"""Bridge: the buff-cream bridge cab on the starboard bow shoulder.

`Bridge cab` (X 101..120, Y -30..-10) carries `Bridge cab wheelhouse` on its
crown; the wheelhouse, windscreen and fittings stay on swatch.
"""
from parts_common import PORT, STARBOARD, UP_Z, BOW, DOWN_X

CAB = 'Bridge cab'

PARTS = {
    'cab-top': dict(members=[(CAB, None)], dir=UP_Z, up=DOWN_X, cone=.6, what='bridge cab roof from above'),
    'cab-front': dict(members=[(CAB, None)], dir=BOW, up=UP_Z, cone=.6, what='bridge cab forward face from ahead'),
    'cab-side-stbd': dict(members=[(CAB, None)], dir=STARBOARD, up=UP_Z, cone=.6, what='bridge cab starboard wall'),
    'cab-side-port': dict(members=[(CAB, None)], dir=PORT, up=UP_Z, cone=.6, what='bridge cab port wall'),
}

SHEETS = {
    'bridge': ['cab-top', 'cab-front', 'cab-side-stbd', 'cab-side-port'],
}
