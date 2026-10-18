"""Bridge: the long curved sand-tan bridge hull and its cream starboard crown.

`Bridge hull` runs X 3.8..34.8 over the central body, a superellipse in
section; `Bridge hull pale crown plating` is the cream plating over its
starboard crown (X 8..23.6). One sheet: crown from above (with the cream
plating), port flank, starboard flank, and the nose and chin from ahead.
"""
from parts_common import PORT, STARBOARD, UP_Z, BOW, DOWN_X

BRIDGE, PALE = 'Bridge hull', 'Bridge hull pale crown plating'

PARTS = {
    'bridge-crown': dict(members=[(BRIDGE, (15.0, -1.0, 17.0)), (PALE, (16.0, -7.5, 16.0))],
                         dir=UP_Z, up=DOWN_X, cone=.55, what='bridge hull crown with the cream starboard plating'),
    'bridge-side-port': dict(members=[(BRIDGE, (15.0, 5.5, 6.0))], dir=PORT, up=UP_Z, cone=.6,
                             what='bridge hull port flank'),
    'bridge-side-stbd': dict(members=[(BRIDGE, (15.0, -10.5, 6.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                             what='bridge hull starboard flank'),
    'bridge-nose': dict(members=[(BRIDGE, (33.0, -2.5, 8.0))], dir=BOW, up=UP_Z, cone=.6,
                        what='bridge nose and chin from ahead, below the windscreen'),
}

SHEETS = {
    'bridge': ['bridge-crown', 'bridge-side-port', 'bridge-side-stbd', 'bridge-nose'],
}
