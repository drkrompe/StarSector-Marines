"""Hull: the Thunder's armour and structure (pass 6, one group).

The Thunder (ft_thunder.py) is khaki armour (central fuselage, the delta wing,
two drive pods, the port gun block, the starboard gun housing and lance) over
dark steel prongs, shroud and tip plates. The pods are symmetric about Y = 0,
so the starboard pod reuses the port tiles mirrored; the guns are not.

  hull-top   fuselage, delta wing, port pod and the gun block, housing and lance, from above
  hull-low   the port pod's outboard side, the gun block's port side, the lance's starboard side, the belly
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
    'Hull central fuselage': (-5.2, 17.6, -2.7, 2.7, -0.9, 1.9),
    'Hull delta wing': (-3.9, 20.0, -12.0, 12.0, -0.35, 0.85),
    'Engine 1 port pod': (-11.9, 2.4, 3.6, 9.9, -2.1, 1.4),
    'Engine 2 starboard pod': (-11.9, 2.4, -9.9, -3.6, -2.1, 1.4),
    'Hull port gun block': (10.5, 20.1, 0.1, 7.9, -0.4, 1.3),
    'Hull starboard gun housing': (13.0, 20.4, -5.0, -3.3, -0.3, 1.1),
    'Hull starboard lance': (12.0, 29.2, -4.0, -1.0, -0.1, 1.85),
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
    'fuselage-top': dict(members=_members(['Hull central fuselage'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='central fuselage from above: long khaki armour, nose to the right'),
    'wing-top': dict(members=_members(['Hull delta wing'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='the delta wing from above: broad khaki armour, apex to the right'),
    'pod-top-port': dict(members=_members(['Engine 1 port pod'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port drive pod from above: khaki armour'),
    'pod-top-stbd': dict(members=_members(['Engine 2 starboard pod'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='pod-top-port', mirror_u=True,
                what='starboard drive pod (mirror)'),
    'guns-top': dict(members=_members(['Hull port gun block', 'Hull starboard gun housing', 'Hull starboard lance'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='the port gun block, starboard gun housing and the long starboard lance from above: khaki armour'),
    'pod-side-port': dict(members=_members(['Engine 1 port pod'], '+y'), dir=PORT, up=UP_Z, cone=.85,
                what='port drive pod outboard side'),
    'pod-side-stbd': dict(members=_members(['Engine 2 starboard pod'], '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                skin_from='pod-side-port', mirror_u=True,
                what='starboard drive pod outboard side (mirror)'),
    'gunblock-side-port': dict(members=_members(['Hull port gun block'], '+y'), dir=PORT, up=UP_Z, cone=.85,
                what='the gun block port side'),
    'lance-side-stbd': dict(members=_members(['Hull starboard lance', 'Hull starboard gun housing'], '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                what='the lance and gun housing starboard side'),
    'underside': dict(members=_members(['Hull central fuselage', 'Hull delta wing', 'Engine 1 port pod', 'Engine 2 starboard pod', 'Hull port gun block', 'Hull starboard gun housing', 'Hull starboard lance'], '-z'), dir=DOWN_Z, up=PORT, cone=.85,
                what='the whole belly from below'),
}

SHEETS = {
    'hull-top': ['fuselage-top', 'wing-top', 'pod-top-port', 'guns-top'],
    'hull-low': ['pod-side-port', 'gunblock-side-port', 'lance-side-stbd', 'underside'],
}
