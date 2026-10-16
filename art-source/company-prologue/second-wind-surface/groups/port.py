"""Port flank: the surfacing pilot's sixteen parts and two reuses (README, Verdict).

Owned by the pilot; the rollout groups sit beside it in groups/<group>.py.
"""
from parts_common import HULL, PORT, UP_Z, DOWN_X, BOW_QUARTER, CHEEK

# id: dict(members=[(object, seed)], dir, up, cone, material notes)
PARTS = {
    # Sheet A: the forebody and waist flank
    'hull-waist-port': dict(members=[(HULL, (8, 23.2, 7))], dir=PORT, up=UP_Z, cone=.6,
                            what='main hull port waist wall around the open bay'),
    'bow-port-flank': dict(members=[('Forebody armour module.002', (52, 20, 2))], dir=BOW_QUARTER, up=UP_Z, cone=.6,
                           what='bow shield module, port flank and forward rake'),
    'fore-module-1': dict(members=[('Forebody armour module', (32, 31.5, 2))], dir=PORT, up=UP_Z, cone=.6,
                          what='aft forebody armour module, port face (behind the grapple sponson)'),
    'fore-module-2': dict(members=[('Forebody armour module.001', (42, 31.5, 2))], dir=PORT, up=UP_Z, cone=.6,
                          what='forward forebody armour module, port face (behind the grapple sponson)'),
    # Sheet B: the port engine pod's major casing, from port
    'pod-cowl-port': dict(members=[(HULL, (-10, 38.5, 9))], dir=PORT, up=UP_Z, cone=.6,
                          what='port pod armoured cowl, port face (part of the main hull object)'),
    'pod-lower-casing-port': dict(members=[('Drive lower casing', (-10, 38, -2))], dir=PORT, up=UP_Z, cone=.6,
                                  what='port pod steel lower casing'),
    'pod-intake-cap-port': dict(members=[('Drive intake cap', (0, 36.5, 3))], dir=PORT, up=UP_Z, cone=.6,
                                what='port pod intake cap'),
    'pod-thrust-housing-port': dict(members=[('Thrust housing', (-30, 38, 2))], dir=PORT, up=UP_Z, cone=.6,
                                    what='port pod thrust housing'),
    # Sheet C: the grapple sponson boxes, the pod bands and the bow belt
    'sponson-lower': dict(members=[('Grapple sponson aft lower body', (33, 34.3, 1))], dir=PORT, up=UP_Z, cone=.6,
                          what='grapple sponson lower body; the forward body reuses it'),
    'sponson-lower-fwd': dict(members=[('Grapple sponson forward lower body', (43, 34.3, 1))], dir=PORT, up=UP_Z, cone=.6,
                              skin_from='sponson-lower', what='forward sponson lower body (shares sponson-lower)'),
    'sponson-cowl': dict(members=[('Grapple sponson aft cowl', (33, 34.3, 8))], dir=PORT, up=UP_Z, cone=.6,
                         what='grapple sponson cowl; the forward cowl reuses it'),
    'sponson-cowl-fwd': dict(members=[('Grapple sponson forward cowl', (43, 34.3, 8))], dir=PORT, up=UP_Z, cone=.6,
                             skin_from='sponson-cowl', what='forward sponson cowl (shares sponson-cowl)'),
    'pod-bands-port': dict(members=[('Drive reinforcing band', (-17.8, 39, 4)), ('Drive reinforcing band.001', (-26.2, 39, 4))],
                           dir=PORT, up=UP_Z, cone=.6, what='port pod barrel bands, both in one tile'),
    'belt-bow-port': dict(members=[('Forebody armour belt plate.002', (50, 24, -1))], dir=BOW_QUARTER, up=UP_Z, cone=.5,
                          what='steel belt plate on the bow module, port side'),
    # Sheet D: tops and strips
    'pod-cowl-top': dict(members=[(HULL, (-12, 33, 16))], dir=UP_Z, up=DOWN_X, cone=.6,
                         what='port pod cowl top and turret seat (main hull object)'),
    'sponson-cowl-tops': dict(members=[('Grapple sponson aft cowl', (33, 29, 12)), ('Grapple sponson forward cowl', (43, 29, 12))],
                              dir=UP_Z, up=DOWN_X, cone=.6, what='both sponson cowl roofs in one tile'),
    'bow-cheek-port': dict(members=[('Forebody armour module.002', (49, 18, 13))], dir=CHEEK, up=DOWN_X, cone=.85,
                           what='raked port bow cheek'),
    'deck-edge-lip-port': dict(members=[('Deck edge lip', (20, 28.5, 10)), ('Deck edge lip', (32, 32, 10)), ('Deck edge lip', (42, 30, 10))], dir=PORT, up=UP_Z, cone=.5,
                               what='overhanging deck-edge lip, port run'),
}

# One Codex generation per sheet; reading order = tile order (2x2).
SHEETS = {
    'flank': ['hull-waist-port', 'bow-port-flank', 'fore-module-1', 'fore-module-2'],
    'pod': ['pod-cowl-port', 'pod-lower-casing-port', 'pod-intake-cap-port', 'pod-thrust-housing-port'],
    'fittings': ['sponson-lower', 'sponson-cowl', 'pod-bands-port', 'belt-bow-port'],
    'tops': ['pod-cowl-top', 'sponson-cowl-tops', 'bow-cheek-port', 'deck-edge-lip-port'],
}
