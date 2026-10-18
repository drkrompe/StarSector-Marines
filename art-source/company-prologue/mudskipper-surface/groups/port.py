"""Port flank, square-on from +Y: the waist/fore flank wall (one hull plane from
the junction to the bow), the cargo bay doors on it, the aft flank with its
locker, and the forebody sponson's outer side.
Each source has a starboard reuse (skin_from + mirror_u); the starboard side is
built as the port side's mirror (y -> -y). The 'Junction ...' structure is
asymmetric and is left to swatches.
"""
from parts_common import HULL, PORT, STARBOARD, UP_Z


def _stbd(members):
    """Starboard twin of a port member list: 'Port X' -> 'Starboard X', y -> -y."""
    out = []
    for name, (x, y, z) in members:
        if name.startswith('Port '):
            name = 'Starboard ' + name[len('Port '):]
        out.append((name, (x, -y, z)))
    return out


WAIST = [
    (HULL, (8, 22.5, 0)),
    ('Port crew hatch', (0.2, 22.9, -3)),
    ('Port crew hatch frame', (0.2, 22.6, -4.5)),
    ('Port waist keel strake', (9, 22.0, -7.3)),
    ('Port forebody deck-edge lip', (28, 23.0, 9)),
]

DOORS = [
    ('Port cargo bay door aft leaf', (6, 22.9, 0)),
    ('Port cargo bay door forward leaf', (14, 22.9, 0)),
    ('Port cargo bay door frame', (10, 22.6, -4.6)),
    ('Port cargo bay door jamb', (2, 23.2, 0)),
    ('Port cargo bay door jamb.001', (18, 23.2, 0)),
    ('Port cargo bay door sill', (10, 23.2, -4.8)),
    ('Port cargo bay door track', (9, 23.2, 3.8)),
    ('Port cargo bay door stiffener', (6, 23.1, -2.4)),
    ('Port cargo bay door stiffener.001', (6, 23.1, 1.1)),
    ('Port cargo bay door stiffener.002', (14, 23.1, -2.4)),
    ('Port cargo bay door stiffener.003', (14, 23.1, 1.1)),
]

AFT = [
    (HULL, (-10, 28.5, 0)),
    ('Port aft flank armour module', (-12.6, 28.8, -4)),
    ('Port aft flank armour module.001', (-7, 28.8, -4)),
    ('Port aft flank seam backing', (-9.8, 28.6, -2.2)),
    ('Port aft keel strake', (-10, 28.0, -7.3)),
    ('Port flank locker', (-9, 29.0, 5.8)),
    ('Port flank locker door seam', (-9, 29.1, 5.6)),
    ('Port flank locker door', (-11, 29.2, 2)),
    ('Port flank locker door.001', (-7, 29.2, 2)),
]

SPONSON = [
    ('Port forebody sponson', (27, 28.4, 3)),
    ('Port sponson armour module', (23.4, 28.3, -2.3)),
    ('Port sponson armour module.001', (27.1, 28.6, -2.3)),
    ('Port sponson armour module.002', (30.7, 29.0, -2.3)),
    ('Port sponson seam backing', (27, 28.8, 0.3)),
    ('Port sponson belt', (27, 29.0, 0.8)),
    ('Port sponson keel strake', (27, 28.7, -6.3)),
    ('Port sponson passenger port frame', (23.6, 28.2, 2.4)),
    ('Port sponson passenger port frame.001', (26, 28.4, 2.4)),
    ('Port sponson passenger port frame.002', (28.4, 28.6, 2.4)),
    ('Port sponson passenger port frame.003', (30.8, 28.8, 2.4)),
]

PARTS = {
    'waist-port': dict(members=WAIST, dir=PORT, up=UP_Z, cone=.6,
                       what='main hull port waist and fore flank wall, one plane from the junction to the bow (the fore half sits behind the sponson; the strip above it shows), crew hatch, keel strake, deck-edge lip'),
    'aft-flank-port': dict(members=AFT, dir=PORT, up=UP_Z, cone=.6,
                           what='main hull port aft flank with the armour modules, keel strake and the tan flank locker'),
    'sponson-port': dict(members=SPONSON, dir=PORT, up=UP_Z, cone=.6,
                         what='port forebody sponson outer side (grey-green) with its armour modules, belt and keel strake'),
    'bay-doors-port': dict(members=DOORS, dir=PORT, up=UP_Z, cone=.6,
                           what='port cargo bay doors: two leaves with stiffeners, dark frame, jambs, sill and track'),
    'waist-stbd': dict(members=_stbd(WAIST), dir=STARBOARD, up=UP_Z, cone=.6, skin_from='waist-port', mirror_u=True,
                       what='starboard waist wall (mirror of waist-port)'),
    'aft-flank-stbd': dict(members=_stbd(AFT), dir=STARBOARD, up=UP_Z, cone=.6, skin_from='aft-flank-port', mirror_u=True,
                           what='starboard aft flank (mirror of aft-flank-port)'),
    'sponson-stbd': dict(members=_stbd(SPONSON), dir=STARBOARD, up=UP_Z, cone=.6, skin_from='sponson-port', mirror_u=True,
                         what='starboard sponson outer side (mirror of sponson-port)'),
    'bay-doors-stbd': dict(members=_stbd(DOORS), dir=STARBOARD, up=UP_Z, cone=.6, skin_from='bay-doors-port', mirror_u=True,
                           what='starboard cargo bay doors (mirror of bay-doors-port)'),
}

SHEETS = {
    'port-a': ['waist-port', 'aft-flank-port', 'sponson-port', 'bay-doors-port'],
}
