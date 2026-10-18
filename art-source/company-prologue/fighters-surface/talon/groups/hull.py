"""Hull: the Talon's armour and stern structure (pass 6, one group).

The Talon (ft_talon.py) is four red armour bodies (cockpit pod to starboard,
gun nacelle to port, the aft body that joins them, two wing plates) over a
grey steel stern (two drive drums and the centre spine). Pass 2 engraved
panel lines into the armour as constant-depth grooves, and a groove's walls
turn out of any part's cone, so one seed would claim only the panel it lands
in. Each member list is therefore a grid of seeds over the object's face
toward the view (`_grid`); a seed that lands nowhere useful only re-claims a
face another seed already holds. Groove floors are claimed with their panels.

  hull-top   pod, nacelle, aft body and port wing from above
  hull-low   the stern steel from above, both flanks, the whole underside

The starboard wing reuses the port wing's tile mirrored: the sprite, and
the model, are symmetric about Y = 1 there. Pass-2 fittings (vents, grilles,
strips, the vulcan, canopy frame) and the drive bells keep their swatches.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z

# World bounding boxes of the clean build (x0, x1, y0, y1, z0, z1).
BOX = {
    'Hull cockpit pod': (-5.4, 10.25, -6.0, 0.0, -1.35, 1.25),
    'Hull gun nacelle': (-5.4, 12.0, 1.1, 7.0, -1.1, 1.4),
    'Hull aft body': (-8.0, 2.0, -6.6, 8.6, -1.5, 0.9),
    'Wing port plate': (-8.0, 1.6, 7.0, 12.0, -0.7, 0.35),
    'Wing starboard plate': (-8.0, 1.6, -10.0, -5.0, -0.7, 0.35),
    'Hull centre spine': (-9.0, 1.9, -0.9, 2.9, -1.2, 1.2),
    'Engine 1 port drive drum': (-8.95, -3.0, 4.0, 9.0, -2.3, 1.15),
    'Engine 2 starboard drive drum': (-8.95, -3.0, -7.0, -2.0, -2.3, 1.15),
}
STEP = 0.6


def _grid(name, face):
    """Seeds on the side of `name`'s box that faces the view: '+z', '-z', '+y', '-y'."""
    x0, x1, y0, y1, z0, z1 = BOX[name]
    n = lambda a, b: [a + (b - a) * (i + .5) / max(1, round((b - a) / STEP)) for i in range(max(1, round((b - a) / STEP)))]
    if face in ('+z', '-z'):
        z = z1 if face == '+z' else z0
        return [(name, (x, y, z)) for x in n(x0, x1) for y in n(y0, y1)]
    y = y1 if face == '+y' else y0
    return [(name, (x, y, z)) for x in n(x0, x1) for z in n(z0, z1)]


def _members(names, face):
    return [m for name in names for m in _grid(name, face)]


PARTS = {
    'pod-top': dict(members=_grid('Hull cockpit pod', '+z'), dir=UP_Z, up=PORT, cone=.85,
                    what='cockpit pod from above: long red armour body, rounded nose to the right, '
                         'the canopy area and the dorsal strip channel stay red plate'),
    'nacelle-top': dict(members=_grid('Hull gun nacelle', '+z'), dir=UP_Z, up=PORT, cone=.85,
                        what='gun nacelle from above: long red armour body, tapered gun end to the right'),
    'aft-top': dict(members=_grid('Hull aft body', '+z'), dir=UP_Z, up=PORT, cone=.85,
                    what='aft body from above: the broad red armour block joining both pods and the wing roots'),
    'wing-top-port': dict(members=_grid('Wing port plate', '+z'), dir=UP_Z, up=PORT, cone=.85,
                          what='port wing plate from above: a swept red armour plate, leading edge to the right'),
    'wing-top-stbd': dict(members=_grid('Wing starboard plate', '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                          skin_from='wing-top-port', mirror_u=True,
                          what='starboard wing plate from above (mirror of the port wing)'),
    'stern-top': dict(members=_members(['Engine 1 port drive drum', 'Hull centre spine',
                                        'Engine 2 starboard drive drum'], '+z'),
                      dir=UP_Z, up=PORT, cone=.85,
                      what='stern steel from above: the two drive drums and the centre spine between them, grey worn steel'),
    'side-stbd': dict(members=_members(['Hull cockpit pod', 'Hull aft body', 'Wing starboard plate'], '-y'),
                      dir=STARBOARD, up=UP_Z, cone=.85,
                      what='starboard flank: the cockpit pod and aft body side walls and the wing edge, red armour'),
    'side-port': dict(members=_members(['Hull gun nacelle', 'Hull aft body', 'Wing port plate'], '+y'),
                      dir=PORT, up=UP_Z, cone=.85,
                      what='port flank: the gun nacelle and aft body side walls and the wing edge, red armour'),
    'underside': dict(members=_members(['Hull cockpit pod', 'Hull gun nacelle', 'Hull aft body',
                                        'Wing port plate', 'Wing starboard plate'], '-z'),
                      dir=DOWN_Z, up=PORT, cone=.85,
                      what='the whole belly from below: pod, nacelle, aft body and both wings, red armour'),
}

SHEETS = {
    'hull-top': ['pod-top', 'nacelle-top', 'aft-top', 'wing-top-port'],
    'hull-low': ['stern-top', 'side-stbd', 'side-port', 'underside'],
}
