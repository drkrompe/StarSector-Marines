"""Hull: the Wasp's armour and structure (pass 6, one group).

The Wasp (ft_wasp.py) is a grey-blue armour spine over a dark steel hub, four
grey-blue arm blades on dark steel frames, and two dark steel drive cans. It is
symmetric about Y = 0, so every starboard arm, can and flank reuses its port
twin's tile mirrored.

  hull-top   the spine, the hub, the port fore and aft arm blades, from above
  hull-low   the port can from above and outboard, the spine's port flank, the belly
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
    'Hull spine': (-5.0, 7.0, -2.1, 2.1, -1.1, 1.5),
    'Hull hub': (-4.45, 1.0, -4.95, 4.95, -0.8, 0.75),
    'Wing port fore arm blade': (0.3, 8.85, 3.26, 7.88, -0.61, 0.55),
    'Wing starboard fore arm blade': (0.3, 8.85, -7.88, -3.26, -0.61, 0.55),
    'Wing port aft arm blade': (-9.82, -2.75, 4.12, 7.88, -2.2, 0.54),
    'Wing starboard aft arm blade': (-9.82, -2.75, -7.88, -4.12, -2.2, 0.54),
    'Engine 1 port drive can': (-7.95, -1.8, 1.2, 4.0, -1.55, 1.05),
    'Engine 2 starboard drive can': (-7.95, -1.8, -4.0, -1.2, -1.55, 1.05),
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
    'spine-top': dict(members=_members(['Hull spine'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='the central spine from above: a long grey-blue armour body, nose to the right'),
    'hub-top': dict(members=_members(['Hull hub'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='the hub from above: the dark steel cross-member the four arms pivot on'),
    'arm-fore-top-port': dict(members=_members(['Wing port fore arm blade'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port fore arm blade from above: a long grey-blue armour blade'),
    'arm-aft-top-port': dict(members=_members(['Wing port aft arm blade'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port aft arm blade from above: a long grey-blue armour blade'),
    'arm-fore-top-stbd': dict(members=_members(['Wing starboard fore arm blade'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='arm-fore-top-port', mirror_u=True,
                what='starboard fore arm blade (mirror)'),
    'arm-aft-top-stbd': dict(members=_members(['Wing starboard aft arm blade'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='arm-aft-top-port', mirror_u=True,
                what='starboard aft arm blade (mirror)'),
    'can-top-port': dict(members=_members(['Engine 1 port drive can'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port drive can from above: dark steel'),
    'can-top-stbd': dict(members=_members(['Engine 2 starboard drive can'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='can-top-port', mirror_u=True,
                what='starboard drive can from above (mirror)'),
    'spine-side-port': dict(members=_members(['Hull spine'], '+y'), dir=PORT, up=UP_Z, cone=.85,
                what='the spine port flank: grey-blue armour side wall'),
    'spine-side-stbd': dict(members=_members(['Hull spine'], '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                skin_from='spine-side-port', mirror_u=True,
                what='the spine starboard flank (mirror)'),
    'can-side-port': dict(members=_members(['Engine 1 port drive can'], '+y'), dir=PORT, up=UP_Z, cone=.85,
                what='port drive can outboard side: dark steel'),
    'can-side-stbd': dict(members=_members(['Engine 2 starboard drive can'], '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                skin_from='can-side-port', mirror_u=True,
                what='starboard drive can outboard side (mirror)'),
    'underside': dict(members=_members(['Hull spine', 'Hull hub', 'Wing port fore arm blade', 'Wing starboard fore arm blade', 'Wing port aft arm blade', 'Wing starboard aft arm blade', 'Engine 1 port drive can', 'Engine 2 starboard drive can'], '-z'), dir=DOWN_Z, up=PORT, cone=.85,
                what='the whole belly from below: spine, hub, four blades, two cans'),
}

SHEETS = {
    'hull-top': ['spine-top', 'hub-top', 'arm-fore-top-port', 'arm-aft-top-port'],
    'hull-low': ['can-top-port', 'spine-side-port', 'can-side-port', 'underside'],
}
