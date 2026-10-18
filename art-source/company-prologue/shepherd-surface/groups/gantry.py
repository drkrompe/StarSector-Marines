"""Gantry: the light grey machinery drum on the starboard bow, and the turret deck.

`Gantry machinery core` is the drum (X 8.6..34, centred Y -18.5); its frame
bands, crown plate and covers are separate gunmetal and dark parts. `Frame
starboard turret deck` is the gunmetal deck aft of it, carrying the light grey
`Frame starboard turret deck plating`. One sheet: drum crown from above, drum
outboard flank, drum nose from ahead, turret deck with its plating from above.
"""
from parts_common import STARBOARD, UP_Z, BOW, DOWN_X

DRUM, DECK, PLATE = 'Gantry machinery core', 'Frame starboard turret deck', 'Frame starboard turret deck plating'

PARTS = {
    'drum-top': dict(members=[(DRUM, (18.0, -18.5, 6.0))], dir=UP_Z, up=DOWN_X, cone=.55,
                     what='machinery drum crown'),
    'drum-side-stbd': dict(members=[(DRUM, (18.0, -24.5, -1.5))], dir=STARBOARD, up=UP_Z, cone=.55,
                           what='machinery drum outboard flank'),
    'drum-nose': dict(members=[(DRUM, (33.8, -18.5, -1.6))], dir=BOW, up=UP_Z, cone=.6,
                      what='machinery drum nose from ahead'),
    'turret-deck-top': dict(members=[(DECK, (-11.0, -16.0, 3.0)), (PLATE, (-2.0, -16.0, 3.1))],
                            dir=UP_Z, up=DOWN_X, cone=.6, what='turret deck with its light grey plating'),
}

SHEETS = {
    'gantry': ['drum-top', 'drum-side-stbd', 'drum-nose', 'turret-deck-top'],
}
