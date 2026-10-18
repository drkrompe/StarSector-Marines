"""Hull: the Longbow's armour and structure (pass 6, one group).

The Longbow (ft_longbow.py) is light blue-grey armour (fuselage, four swept
arms, two drive pods, the forward prongs, the WS 001 emitter housing and fin to
port, the WS 002 rack plates to starboard) over dark steel webs. Arms, pods and
the fuselage flanks are symmetric about Y = 0, so their starboard twins reuse
the port tiles mirrored.

  hull-top   fuselage with prongs, port fore and aft arms, port pod, from above
  hull-low   emitter housing and fin, rack plates, the fuselage port flank, the belly
Pass 2 engraved panel lines into the armour as constant-depth grooves, and a
groove's walls turn out of any part's cone, so one seed would claim only the
panel it lands in. Each member list is therefore a grid of seeds over the
object's face toward the view (`_grid`); a seed that lands nowhere useful
only re-claims a face another seed already holds. Groove floors are claimed
with their panels. Pass-2 fittings (vents, grilles, strips, rings, the
canopy frame) and the drive bells keep their swatches.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z

# World bounding boxes of the clean build (x0, x1, y0, y1, z0, z1).
BOX = {
    'Hull fuselage': (-6.9, 13.3, -3.6, 3.6, -2.0, 2.4),
    'Hull port prong': (10.5, 18.24, 3.95, 7.1, -0.25, 0.55),
    'Hull starboard prong': (10.5, 17.35, -5.99, -3.95, -0.25, 0.55),
    'Wing port fore arm': (-0.41, 13.51, 0.1, 13.5, -0.95, 0.9),
    'Wing starboard fore arm': (-0.41, 13.51, -13.5, -0.1, -0.95, 0.9),
    'Wing port aft arm': (-10.29, 0.89, -0.26, 10.76, -0.95, 0.9),
    'Wing starboard aft arm': (-10.29, 0.89, -10.76, 0.26, -0.95, 0.9),
    'Engine 1 port drive pod': (-13.0, -6.4, 5.0, 13.0, -2.1, 2.0),
    'Engine 2 starboard drive pod': (-13.0, -6.4, -13.0, -5.0, -2.1, 2.0),
    'Weapon WS 001 housing': (6.0, 16.0, 8.4, 16.0, -1.2, 1.35),
    'Weapon WS 001 ventral fin': (0.0, 6.0, 12.01, 15.05, -0.9, 0.4),
    'Weapon WS 002 inner plate': (5.0, 15.1, -9.4, -4.4, -1.45, 1.55),
    'Weapon WS 002 outer plate': (5.0, 15.1, -16.0, -12.6, -1.45, 1.55),
}
STEP = 0.6


def _grid(name, face):
    """Seeds on the side of `name`'s box that faces the view: '+z', '-z', '+y', '-y'."""
    x0, x1, y0, y1, z0, z1 = BOX[name]

    def n(a, b):
        k = max(1, round((b - a) / STEP))
        return [a + (b - a) * (i + .5) / k for i in range(k)]
    if face in ('+z', '-z'):
        z = z1 if face == '+z' else z0
        return [(name, (x, y, z)) for x in n(x0, x1) for y in n(y0, y1)]
    y = y1 if face == '+y' else y0
    return [(name, (x, y, z)) for x in n(x0, x1) for z in n(z0, z1)]


def _members(names, face):
    return [m for name in names for m in _grid(name, face)]


PARTS = {
    'fuselage-top': dict(members=_members(['Hull fuselage', 'Hull port prong', 'Hull starboard prong'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='fuselage and its two forward prongs from above: light blue-grey armour, nose to the right'),
    'arm-fore-top-port': dict(members=_members(['Wing port fore arm'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port fore arm from above: swept light blue-grey armour'),
    'arm-fore-top-stbd': dict(members=_members(['Wing starboard fore arm'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='arm-fore-top-port', mirror_u=True,
                what='starboard fore arm (mirror)'),
    'arm-aft-top-port': dict(members=_members(['Wing port aft arm'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port aft arm from above: swept light blue-grey armour'),
    'arm-aft-top-stbd': dict(members=_members(['Wing starboard aft arm'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='arm-aft-top-port', mirror_u=True,
                what='starboard aft arm (mirror)'),
    'pod-top-port': dict(members=_members(['Engine 1 port drive pod'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port drive pod from above: light blue-grey armour'),
    'pod-top-stbd': dict(members=_members(['Engine 2 starboard drive pod'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='pod-top-port', mirror_u=True,
                what='starboard drive pod (mirror)'),
    'housing-top': dict(members=_members(['Weapon WS 001 housing', 'Weapon WS 001 ventral fin'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='emitter housing and its fin from above'),
    'rack-top': dict(members=_members(['Weapon WS 002 inner plate', 'Weapon WS 002 outer plate'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='the missile rack side plates from above'),
    'fuselage-side-port': dict(members=_members(['Hull fuselage'], '+y'), dir=PORT, up=UP_Z, cone=.85,
                what='fuselage port flank'),
    'fuselage-side-stbd': dict(members=_members(['Hull fuselage'], '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                skin_from='fuselage-side-port', mirror_u=True,
                what='fuselage starboard flank (mirror)'),
    'underside': dict(members=_members(['Hull fuselage', 'Wing port fore arm', 'Wing starboard fore arm', 'Wing port aft arm', 'Wing starboard aft arm', 'Engine 1 port drive pod', 'Engine 2 starboard drive pod', 'Weapon WS 001 housing', 'Weapon WS 001 ventral fin', 'Weapon WS 002 inner plate', 'Weapon WS 002 outer plate'], '-z'), dir=DOWN_Z, up=PORT, cone=.85,
                what='the whole belly from below'),
}

SHEETS = {
    'hull-top': ['fuselage-top', 'arm-fore-top-port', 'arm-aft-top-port', 'pod-top-port'],
    'hull-low': ['housing-top', 'rack-top', 'fuselage-side-port', 'underside'],
}
