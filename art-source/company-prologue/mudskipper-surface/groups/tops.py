"""Tops: the Mudskipper's dorsal surfaces seen from directly above.

Four sources, all UP_Z: the waist deck (main hull's one waist top plane with
its straight red stripe panels and the tan dorsal cargo hatch), the fore deck
(main hull's fore deck plane round the cockpit block, with the straight and
angled red chevron panels and the vent grille frames), the aft deck (main
hull's aft top plane with the two aft dorsal hatch panels and the tan turret
well coaming), and the dorsal cabin hood top (hood crown, its side and aft
slopes, the two roof plates and the arched brow rim's top).

Each deck is one polygon spanning both sides, so none splits into a mirrored
port/starboard pair. The bow rake, cockpit block and bridge belong to the bow
group; the drive pods to drives. The turret, skylights, hatch ports, hinges,
latches, winches, grab rails and other raised fittings stay to swatches.
"""
from parts_common import HULL, PORT, UP_Z, BOW

_WAIST = [(HULL, (2.0, 15.0, 8.5)),
          ('Red stripe panel', (11.0, 17.7, 8.8)), ('Red stripe panel.002', (11.0, -17.7, 8.8)),
          ('Dorsal cargo hatch', (0.5, 6.8, 9.0)),
          ('Dorsal cargo hatch leaf', (2.0, -4.3, 8.9)), ('Dorsal cargo hatch leaf.001', (2.0, 0.0, 8.9)),
          ('Dorsal cargo hatch leaf.002', (2.0, 4.3, 8.9)), ('Dorsal cargo hatch leaf.003', (4.2, -4.3, 8.9)),
          ('Dorsal cargo hatch leaf.004', (4.2, 0.0, 8.9)), ('Dorsal cargo hatch leaf.005', (4.2, 4.3, 8.9))]

_FORE = [(HULL, (22.0, 18.0, 9.0)),
         ('Red stripe panel.001', (23.5, 17.7, 9.3)), ('Red stripe panel.003', (23.5, -17.7, 9.3)),
         ('Red stripe panel angled', (31.5, 14.0, 9.3)), ('Red stripe panel angled.001', (31.5, -14.0, 9.3)),
         ('Fore deck vent grille frame', (30.3, 19.6, 9.3)), ('Fore deck vent grille frame.001', (30.3, -19.6, 9.3))]

_AFT = [(HULL, (-8.0, 22.0, 9.5)),
        ('Aft dorsal hatch panel', (-12.0, 15.0, 9.8)), ('Aft dorsal hatch panel.001', (-12.0, -15.0, 9.8)),
        ('Turret well coaming', (-8.5, 6.1, 10.0)), ('Turret well coaming.001', (-8.5, -6.1, 10.0)),
        ('Turret well coaming.002', (-4.4, 0.0, 10.0)), ('Turret well coaming.003', (-13.1, 0.0, 10.0))]

_HOOD = [('Dorsal cabin hood', (17.0, 0.0, 13.0)),
         ('Hood roof plate', (9.3, 0.0, 13.2)), ('Hood roof plate.001', (12.3, 0.0, 13.2)),
         ('Hood arched brow rim', (17.7, 0.0, 13.5))]

PARTS = {
    'tops-waist-deck': dict(members=_WAIST, dir=UP_Z, up=BOW, cone=.95,
                            what='waist deck top from above: off-white deck plate, two straight signal-red stripe '
                                 'panels along its edges, tan dorsal cargo hatch and its six leaves'),
    'tops-fore-deck': dict(members=_FORE, dir=UP_Z, up=BOW, cone=.95,
                           what='fore deck top from above: off-white deck plate round the cockpit block, '
                                'straight and angled signal-red chevron panels, off-white vent grille frames'),
    'tops-aft-deck': dict(members=_AFT, dir=UP_Z, up=BOW, cone=.95,
                          what='aft deck top from above: off-white deck plate, two off-white aft dorsal hatch '
                               'panels, tan turret well coaming round the (swatched) turret'),
    'tops-hood': dict(members=_HOOD, dir=UP_Z, up=PORT, cone=.78,
                      what='dorsal cabin hood from above: off-white crown with its side and aft slopes, two roof '
                           'plates and the arched brow rim top'),
}

SHEETS = {
    'tops-a': ['tops-waist-deck', 'tops-fore-deck', 'tops-aft-deck', 'tops-hood'],
}
