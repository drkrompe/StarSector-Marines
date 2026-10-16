"""Starboard flank: the mirrored twins of the port pilot's parts, plus the
starboard-only fittings (the sealed bay door, the forebody belt plates).

Mirror reuse is the default: a starboard part takes its port twin's
generation with `mirror_u`, checked by rendering it as its own source and
flipping its mask against the twin's (IoU 0.997-1.000 for every row kept as a
mirror). The grapple sponson is port-only, so its rows have no twin here.

Not mirrors: the port bow flank, bow belt and bow cheek parts also own the
bow module's centreline faces (front face, cheek top; ties go to port), so
the starboard flank and belt are a subset framed differently (IoU ~0.58) and
get their own tiles. The starboard cheek is one 8.7 unit^2 strip and is left
to the swatch. The sealed bay door and the two forebody belt plates exist
only on this side.
"""
from parts_common import HULL, STARBOARD, UP_Z, DOWN_X, BOW_QUARTER_STBD

VERIFY = False   # True: mirrored parts render as their own source (mirror check)


def _twin(port_part):
    return {} if VERIFY else dict(skin_from=port_part, mirror_u=True)


PARTS = {
    # Mirrors of the port flank sheet
    'hull-waist-stbd': dict(members=[(HULL, (8, -23.2, 7))], dir=STARBOARD, up=UP_Z, cone=.6,
                            what='main hull starboard waist wall around the sealed bay', **_twin('hull-waist-port')),
    'bow-stbd-flank': dict(members=[('Forebody armour module.002', (52, -20, 2))], dir=BOW_QUARTER_STBD, up=UP_Z, cone=.6,
                           what='bow shield module, starboard flank and lower rake (the centreline front face belongs to the port part)'),
    # Mirrors of the port pod sheet
    'pod-cowl-stbd': dict(members=[(HULL, (-10, -38.5, 9))], dir=STARBOARD, up=UP_Z, cone=.6,
                          what='starboard pod armoured cowl, starboard face', **_twin('pod-cowl-port')),
    'pod-lower-casing-stbd': dict(members=[('Drive lower casing.001', (-10, -38, -2))], dir=STARBOARD, up=UP_Z, cone=.6,
                                  what='starboard pod steel lower casing', **_twin('pod-lower-casing-port')),
    'pod-intake-cap-stbd': dict(members=[('Drive intake cap.001', (0, -36.5, 3))], dir=STARBOARD, up=UP_Z, cone=.6,
                                what='starboard pod intake cap', **_twin('pod-intake-cap-port')),
    'pod-thrust-housing-stbd': dict(members=[('Thrust housing.001', (-30, -38, 2))], dir=STARBOARD, up=UP_Z, cone=.6,
                                    what='starboard pod thrust housing', **_twin('pod-thrust-housing-port')),
    # Mirrors of the port fittings and tops sheets
    'pod-bands-stbd': dict(members=[('Drive reinforcing band.002', (-17.8, -39, 4)), ('Drive reinforcing band.003', (-26.2, -39, 4))],
                           dir=STARBOARD, up=UP_Z, cone=.6, what='starboard pod barrel bands', **_twin('pod-bands-port')),
    'belt-bow-stbd': dict(members=[('Forebody armour belt plate.002', (50, -24, -1))], dir=BOW_QUARTER_STBD, up=UP_Z, cone=.5,
                          what='steel belt plate on the bow module, starboard side (front face belongs to the port part)'),
    'pod-cowl-top-stbd': dict(members=[(HULL, (-12, -33, 16))], dir=UP_Z, up=DOWN_X, cone=.6,
                              what='starboard pod cowl top and turret seat', **_twin('pod-cowl-top')),
    'deck-edge-lip-stbd': dict(members=[('Deck edge lip', (20, -28.5, 10)), ('Deck edge lip', (32, -32, 10)), ('Deck edge lip', (42, -30, 10))],
                               dir=STARBOARD, up=UP_Z, cone=.5, what='overhanging deck-edge lip, starboard run',
                               **_twin('deck-edge-lip-port')),
    # Starboard-only / non-mirror faces: one generated sheet
    'bay-door-stbd': dict(members=[('Starboard bay sealed sectional door', (14, -23.4, 0))], dir=STARBOARD, up=UP_Z, cone=.6,
                          what='starboard bay sealed sectional door, outer face'),
    'belt-fore-stbd': dict(members=[('Forebody armour belt plate', (32, -32.7, -1)), ('Forebody armour belt plate.001', (42, -32.7, -1))],
                           dir=STARBOARD, up=UP_Z, cone=.6, what='both forebody belt plates, starboard face, in one tile'),
    'fore-module-1-stbd': dict(members=[('Forebody armour module', (32, -31.5, 2))], dir=STARBOARD, up=UP_Z, cone=.6,
                               what='aft forebody armour module, starboard face', **_twin('fore-module-1')),
    'fore-module-2-stbd': dict(members=[('Forebody armour module.001', (42, -31.5, 2))], dir=STARBOARD, up=UP_Z, cone=.6,
                               what='forward forebody armour module, starboard face', **_twin('fore-module-2')),
}

SHEETS = {
    'starboard-flank': ['bay-door-stbd', 'belt-fore-stbd', 'bow-stbd-flank', 'belt-bow-stbd'],
}
