"""Decks: the shielded-hold deckhouse (bridge walls, raked front, roof), the
wheelhouse visor brow, and the stern skids.

One sheet of four sources: the deckhouse port wall (the starboard wall is its
mirror), the deckhouse front with the brow's forward face, the deckhouse roof
with the brow's top, and the port stern skid's outboard side (the starboard
skid is its mirror). The window recesses, the wheelhouse rails, mast, walkway
and glazing, the aft ventral ramp (seen only from below), the deckhouse aft
wall and the WS 002 collar and ring stay on the class swatches.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X, BOW

DECKHOUSE = 'Hull shielded hold deckhouse'
BROW = 'Wheelhouse visor brow'

PARTS = {
    'deckhouse-wall-port': dict(members=[(DECKHOUSE, (8, 11, 6))], dir=PORT, up=UP_Z, cone=.6,
                                what='taupe deckhouse port wall round its window recess'),
    'deckhouse-wall-stbd': dict(members=[(DECKHOUSE, (8, -10.5, 6))], dir=STARBOARD, up=UP_Z, cone=.6,
                                skin_from='deckhouse-wall-port', mirror_u=True,
                                what='taupe deckhouse starboard wall (mirror of the port wall)'),
    'deckhouse-front': dict(members=[(DECKHOUSE, (18.5, -5, 6)), (BROW, (19.24, 0, 9.38))], dir=BOW, up=UP_Z, cone=.6,
                            what='taupe deckhouse front: lower wall, raked window-band face and the visor brow edge'),
    'deckhouse-roof': dict(members=[(DECKHOUSE, (12, 0, 9.7)), (BROW, (18.3, 0, 9.67))], dir=UP_Z, up=DOWN_X, cone=.6,
                           what='taupe deckhouse roof with the visor brow along its forward edge'),
    'skid-side-port': dict(members=[('Hull stern skid port', (-21.7, 14, -1)), ('Hull stern skid port', (-17.95, 14, -0.9))],
                           dir=PORT, up=UP_Z, cone=.6, what='taupe port stern skid, outboard side'),
    'skid-side-stbd': dict(members=[('Hull stern skid starboard', (-21.7, -14, -1)),
                                    ('Hull stern skid starboard', (-17.95, -14, -0.9))],
                           dir=STARBOARD, up=UP_Z, cone=.6, skin_from='skid-side-port', mirror_u=True,
                           what='taupe starboard stern skid, outboard side (mirror of port)'),
}

SHEETS = {
    'decks': ['deckhouse-wall-port', 'deckhouse-front', 'deckhouse-roof', 'skid-side-port'],
}
