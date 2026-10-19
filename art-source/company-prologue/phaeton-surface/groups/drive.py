"""Drive: the drive housings, the drive block stern face, the reaction drums and the guard arcs.

`Drive port housing` / `Drive starboard housing` flank the spine (gunmetal,
glowing crown ports); `Drive port reaction drum` / starboard sit on the block's
corners (slate blue; their red bands are separate parts on swatch); `Frame port
guard arc` / starboard are the buff guard arcs outboard. Starboard parts reuse
the port tiles mirrored.
"""
from parts_common import PORT, STARBOARD, UP_Z, STERN, DOWN_X

PH, SH = 'Drive port housing', 'Drive starboard housing'
BLOCK = 'Drive block'
PD, SD = 'Drive port reaction drum', 'Drive starboard reaction drum'
PA, SA = 'Frame port guard arc', 'Frame starboard guard arc'

PARTS = {
    'housing-top-port': dict(members=[(PH, None)], dir=UP_Z, up=DOWN_X, cone=.6, what='port drive housing top'),
    'housing-top-stbd': dict(members=[(SH, None)], dir=UP_Z, up=DOWN_X, cone=.6, skin_from='housing-top-port',
                             mirror_u=True, what='starboard drive housing top (mirror)'),
    'block-stern': dict(members=[(BLOCK, None)], dir=STERN, up=UP_Z, cone=.6,
                        what='drive block stern face around the six bell mouths'),
    'drum-side-port': dict(members=[(PD, None)], dir=PORT, up=UP_Z, cone=.6, what='port reaction drum outboard side'),
    'drum-side-stbd': dict(members=[(SD, None)], dir=STARBOARD, up=UP_Z, cone=.6, skin_from='drum-side-port',
                           mirror_u=True, what='starboard reaction drum outboard side (mirror)'),
    'arc-side-port': dict(members=[(PA, None)], dir=PORT, up=UP_Z, cone=.6, what='port guard arc outboard face'),
    'arc-side-stbd': dict(members=[(SA, None)], dir=STARBOARD, up=UP_Z, cone=.6, skin_from='arc-side-port',
                          mirror_u=True, what='starboard guard arc outboard face (mirror)'),
}

SHEETS = {
    'drive': ['housing-top-port', 'block-stern', 'drum-side-port', 'arc-side-port'],
}
