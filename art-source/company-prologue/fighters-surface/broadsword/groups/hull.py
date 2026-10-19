"""Hull: the Broadsword's armour and structure (pass 6, one group).

The Broadsword (../../broadsword-geometry/) is yellowed khaki armour plates
laid over dark gunmetal structure: the forward fuselage and aft spine, two
drive nacelles, and on each side a gun pod housing carrying a blade and an
aft strake. Plates are their own objects (nose, shoulder, dorsal, spine,
nacelle inboard, gun pod, blade, strake), so a part is the structure and the
plates on it seen from one side. The model is symmetric about Y = 0, so every
starboard part reuses its port tile mirrored.

  hull-top   forward fuselage and its plates, aft spine, port nacelle, port wing (pod, blade, strake), from above
  hull-low   fuselage port side, port wing outboard side, the nose from ahead, the whole belly from below

Members are a grid of seeds over each object's face toward the view
(`_grid`): a seed that lands nowhere useful only re-claims a face another
seed already holds. Pass-2 fittings (grilles, slats, vents, rails, canopy
frame, guns, bells, collar ribs) and the landing gear keep their swatches;
the gear doors are claimed with the belly.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW

# World bounding boxes of the clean build, gear up (x0, x1, y0, y1, z0, z1).
BOX = {
    'Hull forward fuselage': (-1.8, 19.0, -6.9, 6.9, -2.4, 2.0),
    'Hull nose armour plate port': (10.7, 18.5, 0.9, 3.9, -0.27, 2.01),
    'Hull nose armour plate starboard': (10.7, 18.5, -3.9, -0.9, -0.27, 2.01),
    'Hull shoulder armour plate port': (-1.0, 9.4, 2.2, 7.0, -0.11, 2.14),
    'Hull shoulder armour plate starboard': (-1.0, 9.4, -7.0, -2.2, -0.11, 2.14),
    'Hull dorsal plate': (0.6, 3.9, -1.2, 1.2, 1.84, 2.10),
    'Hull aft spine': (-7.0, 0.5, -2.6, 2.6, -2.0, 1.55),
    'Hull spine armour plate port': (-5.6, -0.3, 0.0, 1.3, 1.29, 1.66),
    'Hull spine armour plate starboard': (-5.6, -0.3, -1.3, 0.0, 1.29, 1.66),
    'Engine 1 port nacelle': (-10.6, -0.6, 2.5, 8.5, -3.2, 1.8),
    'Engine 2 starboard nacelle': (-10.6, -0.6, -8.5, -2.5, -3.2, 1.8),
    'Engine 1 inboard armour plate': (-10.2, -4.6, 2.4, 5.5, -0.12, 1.94),
    'Wing port gun pod housing': (-7.0, 5.1, 7.5, 10.9, -1.4, 1.3),
    'Wing port gun pod armour plate': (-6.4, 4.5, 9.2, 11.0, 0.18, 1.44),
    'Wing port gun pod armour plate inboard': (-6.4, 4.5, 7.4, 9.2, 0.18, 1.44),
    'Wing port blade': (-6.9, 3.0, 10.8, 14.0, -0.6, 0.6),
    'Wing port blade armour plate': (-5.5, 2.7, 11.2, 13.6, 0.5, 0.78),
    'Wing port aft strake': (-10.9, -0.5, 7.5, 12.1, -1.0, 0.85),
    'Wing port aft strake armour plate': (-10.2, -1.0, 8.0, 11.6, 0.75, 1.02),
    'Wing starboard gun pod housing': (-7.0, 5.1, -10.9, -7.5, -1.4, 1.3),
    'Wing starboard blade': (-6.9, 3.0, -14.0, -10.8, -0.6, 0.6),
    'Wing starboard aft strake': (-10.9, -0.5, -12.1, -7.5, -1.0, 0.85),
}
DOORS = {f'Hull {leg} door {side}': box
         for leg, side, box in [('nose gear', 'port', (5.2, 10.1, 0.0, 0.68, -2.5, -2.1)),
                                ('nose gear', 'starboard', (5.2, 10.1, -0.68, 0.0, -2.5, -2.1)),
                                ('port main gear', 'port', (-9.3, -5.1, 5.5, 6.3, -3.3, -3.2)),
                                ('port main gear', 'starboard', (-9.3, -5.1, 4.7, 5.5, -3.3, -3.2)),
                                ('starboard main gear', 'port', (-9.3, -5.1, -5.5, -4.7, -3.3, -3.2)),
                                ('starboard main gear', 'starboard', (-9.3, -5.1, -6.3, -5.5, -3.3, -3.2))]}
BOX.update(DOORS)
STEP = 0.6


def _grid(name, face):
    """Seeds on the side of `name`'s box that faces the view: '+z', '-z', '+y', '-y', '+x'."""
    x0, x1, y0, y1, z0, z1 = BOX[name]

    def n(a, b):
        k = max(1, round((b - a) / STEP))
        return [a + (b - a) * (i + .5) / k for i in range(k)]
    if face in ('+z', '-z'):
        z = z1 if face == '+z' else z0
        return [(name, (x, y, z)) for x in n(x0, x1) for y in n(y0, y1)]
    if face == '+x':
        return [(name, (x1, y, z)) for y in n(y0, y1) for z in n(z0, z1)]
    y = y1 if face == '+y' else y0
    return [(name, (x, y, z)) for x in n(x0, x1) for z in n(z0, z1)]


def _members(names, face):
    return [m for name in names for m in _grid(name, face)]


FORWARD = ['Hull forward fuselage', 'Hull nose armour plate port', 'Hull nose armour plate starboard',
           'Hull shoulder armour plate port', 'Hull shoulder armour plate starboard', 'Hull dorsal plate']
SPINE = ['Hull aft spine', 'Hull spine armour plate port', 'Hull spine armour plate starboard']
WING_PORT = ['Wing port gun pod housing', 'Wing port gun pod armour plate', 'Wing port gun pod armour plate inboard',
             'Wing port blade', 'Wing port blade armour plate', 'Wing port aft strake', 'Wing port aft strake armour plate']
WING_STBD = [n.replace('port', 'starboard') for n in WING_PORT]
# The starboard pod's plates are named the other way round (its "armour plate" is the inboard one).
BOX['Wing starboard gun pod armour plate'] = (-6.4, 4.5, -9.2, -7.4, 0.18, 1.44)
BOX['Wing starboard gun pod armour plate inboard'] = (-6.4, 4.5, -11.0, -9.2, 0.18, 1.44)
BOX['Wing starboard blade armour plate'] = (-5.5, 2.7, -13.6, -11.2, 0.5, 0.78)
BOX['Wing starboard aft strake armour plate'] = (-10.2, -1.0, -11.6, -8.0, 0.75, 1.02)
BOX['Engine 2 inboard armour plate'] = (-10.2, -4.6, -5.5, -2.4, -0.12, 1.94)
NACELLE_PORT = ['Engine 1 port nacelle', 'Engine 1 inboard armour plate']
NACELLE_STBD = ['Engine 2 starboard nacelle', 'Engine 2 inboard armour plate']
BELLY = FORWARD[:1] + ['Hull aft spine'] + NACELLE_PORT[:1] + NACELLE_STBD[:1] + \
    ['Wing port gun pod housing', 'Wing port blade', 'Wing port aft strake',
     'Wing starboard gun pod housing', 'Wing starboard blade', 'Wing starboard aft strake'] + list(DOORS)

PARTS = {
    'forward-top': dict(members=_members(FORWARD, '+z'), dir=UP_Z, up=PORT, cone=.85,
                        what='forward fuselage from above: dark steel body with long khaki nose plates either side of '
                             'a centre channel, khaki shoulder plates and a small dorsal plate, nose to the right'),
    'spine-top': dict(members=_members(SPINE, '+z'), dir=UP_Z, up=PORT, cone=.85,
                      what='aft spine from above: dark steel with two narrow khaki plates along its centre'),
    'nacelle-top-port': dict(members=_members(NACELLE_PORT, '+z'), dir=UP_Z, up=PORT, cone=.85,
                             what='port drive nacelle from above: dark steel with a khaki plate along its inboard edge'),
    'nacelle-top-stbd': dict(members=_members(NACELLE_STBD, '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                             skin_from='nacelle-top-port', mirror_u=True,
                             what='starboard drive nacelle (mirror)'),
    'wing-top-port': dict(members=_members(WING_PORT, '+z'), dir=UP_Z, up=PORT, cone=.85,
                          what='port wing from above: the gun pod, blade and aft strake in dark steel, each with a '
                               'khaki armour plate on top'),
    'wing-top-stbd': dict(members=_members(WING_STBD, '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                          skin_from='wing-top-port', mirror_u=True,
                          what='starboard wing (mirror)'),
    'fuselage-side-port': dict(members=_members(FORWARD[:1] + ['Hull nose armour plate port',
                                                              'Hull shoulder armour plate port', 'Hull aft spine'], '+y'),
                               dir=PORT, up=UP_Z, cone=.85,
                               what='the fuselage port side: dark steel flank under khaki nose and shoulder plates'),
    'fuselage-side-stbd': dict(members=_members(FORWARD[:1] + ['Hull nose armour plate starboard',
                                                              'Hull shoulder armour plate starboard', 'Hull aft spine'], '-y'),
                               dir=STARBOARD, up=UP_Z, cone=.85,
                               skin_from='fuselage-side-port', mirror_u=True,
                               what='the fuselage starboard side (mirror)'),
    'wing-side-port': dict(members=_members(WING_PORT, '+y'), dir=PORT, up=UP_Z, cone=.85,
                           what='the port wing outboard side: gun pod housing, blade and strake edges'),
    'wing-side-stbd': dict(members=_members(WING_STBD, '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                           skin_from='wing-side-port', mirror_u=True,
                           what='the starboard wing outboard side (mirror)'),
    'nose-front': dict(members=_members(FORWARD[:3], '+x'), dir=BOW, up=UP_Z, cone=.6,
                       what='the nose from dead ahead: dark steel with the two khaki nose plates'),
    'underside': dict(members=_members(BELLY, '-z'), dir=DOWN_Z, up=PORT, cone=.85,
                      what='the whole belly from below, dark steel, with the closed landing gear doors'),
}

SHEETS = {
    'hull-top': ['forward-top', 'spine-top', 'nacelle-top-port', 'wing-top-port'],
    'hull-low': ['fuselage-side-port', 'wing-side-port', 'nose-front', 'underside'],
}
