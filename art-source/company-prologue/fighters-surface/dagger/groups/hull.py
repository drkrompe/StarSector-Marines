"""Hull: the Dagger's armour and structure (pass 6, one group).

The Dagger (ft_dagger.py) is pale blue-grey armour throughout: two forward
prongs, the cockpit body, the broad drive block at the stern, the port torpedo
pod on its pylon and strut, the starboard stub and two tip fins. The prongs and
tip fins are symmetric about Y = 0, so the starboard ones reuse the port tiles
mirrored; the torpedo pod and stub are not.

  hull-top   cockpit body, port prong, drive block, torpedo pod from above
  hull-low   both flanks, the port tip fin's outboard face, the belly
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
    'Hull cockpit body': (-7.6, 2.0, -6.8, 6.8, -1.35, 1.6),
    'Engine 1 drive block': (-15.0, -7.1, -6.0, 6.0, -1.6, 1.85),
    'Hull port prong': (-0.9, 17.7, 1.0, 7.0, -0.85, 1.5),
    'Hull starboard prong': (-0.9, 17.7, -7.0, -1.0, -0.85, 1.5),
    'Wing port torpedo pod': (-2.85, 11.2, 8.0, 12.0, -1.25, 0.9),
    'Wing port pylon': (0.0, 7.0, 6.6, 8.4, -0.35, 0.55),
    'Wing port strut': (2.0, 7.0, 11.7, 13.2, -0.25, 0.45),
    'Wing starboard stub': (0.0, 6.9, -9.1, -6.6, -0.5, 0.9),
    'Wing port tip fin': (-0.98, 7.98, 12.95, 14.05, -1.0, 1.1),
    'Wing starboard tip fin': (-0.98, 7.98, -10.05, -8.95, -1.0, 1.1),
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
    'body-top': dict(members=_members(['Hull cockpit body'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='cockpit body from above: broad pale blue-grey armour'),
    'prong-top-port': dict(members=_members(['Hull port prong'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port prong from above: long pale blue-grey armour, tip to the right'),
    'prong-top-stbd': dict(members=_members(['Hull starboard prong'], '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                skin_from='prong-top-port', mirror_u=True,
                what='starboard prong from above (mirror)'),
    'drive-top': dict(members=_members(['Engine 1 drive block'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='drive block from above: broad pale blue-grey armour at the stern'),
    'pod-top': dict(members=_members(['Wing port torpedo pod', 'Wing port pylon', 'Wing port strut', 'Wing starboard stub'], '+z'), dir=UP_Z, up=PORT, cone=.85,
                what='port torpedo pod with its pylon and strut, and the starboard stub, from above: pale blue-grey armour'),
    'side-port': dict(members=_members(['Hull port prong', 'Hull cockpit body', 'Engine 1 drive block'], '+y'), dir=PORT, up=UP_Z, cone=.85,
                what='port flank: prong, body and drive block side walls'),
    'side-stbd': dict(members=_members(['Hull starboard prong', 'Hull cockpit body', 'Engine 1 drive block'], '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                what='starboard flank: prong, body and drive block side walls'),
    'fin-side-port': dict(members=_members(['Wing port tip fin'], '+y'), dir=PORT, up=UP_Z, cone=.85,
                what='port tip fin outboard face: pale blue-grey armour'),
    'fin-side-stbd': dict(members=_members(['Wing starboard tip fin'], '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                skin_from='fin-side-port', mirror_u=True,
                what='starboard tip fin outboard face (mirror)'),
    'underside': dict(members=_members(['Hull cockpit body', 'Engine 1 drive block', 'Hull port prong', 'Hull starboard prong', 'Wing port torpedo pod', 'Wing port pylon', 'Wing port strut', 'Wing starboard stub', 'Wing port tip fin', 'Wing starboard tip fin'], '-z'), dir=DOWN_Z, up=PORT, cone=.85,
                what='the whole belly from below'),
}

SHEETS = {
    'hull-top': ['body-top', 'prong-top-port', 'drive-top', 'pod-top'],
    'hull-low': ['side-port', 'side-stbd', 'fin-side-port', 'underside'],
}
