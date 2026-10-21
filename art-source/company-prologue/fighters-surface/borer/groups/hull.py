"""Hull: the Borer's armour and drive pods (pass 6, one group).

The Borer (ft_borer.py) is an orange armour spine and two orange armour pods ahead of two grey
drive pods, with grey cheeks, a grey cutting head and grey drums. It is symmetric about Y = 0,
so every starboard pod, cheek and flank reuses its port twin's tile mirrored. As on the other
fighters, each member list is a grid of seeds over the object's face toward the view (`_grid`),
because the engraved panel lines' groove walls turn out of any part's cone.

  hull-top   the spine, the port armour pod and the port drive pod from above, and the belly
  hull-low   the port drive pod's outboard side, the port cheek, the bow faces, the stern faces
Pass-2 fittings (vents, tines, drums, struts, pads, lamps) and the drive bells keep their swatches.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW, STERN

# World bounding boxes of the clean build (x0, x1, y0, y1, z0, z1), measured on the build of
# 2026-10-21 (fighters-geometry/borer).
BOX = {
    'Hull spine': (-6.45, 4.6, -2.4, 2.4, -1.8, 2.0),
    'Pod port armour shell': (-0.75, 4.9, 1.9, 7.15, -1.3, 1.65),
    'Pod starboard armour shell': (-0.75, 4.9, -7.15, -1.9, -1.3, 1.65),
    'Pod port outer cheek': (1.05, 5.95, 7.1, 8.0, -1.05, 1.3),
    'Pod starboard outer cheek': (1.05, 5.95, -8.0, -7.1, -1.05, 1.3),
    'Drive port pod housing': (-7.0, -1.0, 2.35, 7.9, -2.3, 0.85),
    'Drive starboard pod housing': (-7.0, -1.0, -7.9, -2.35, -2.3, 0.85),
}
STEP = 0.4


def _grid(name, face):
    """Seeds on the side of `name`'s box that faces the view: '+z', '-z', '+y', '-y', '+x', '-x'."""
    x0, x1, y0, y1, z0, z1 = BOX[name]
    n = lambda a, b: [a + (b - a) * (i + .5) / max(1, round((b - a) / STEP)) for i in range(max(1, round((b - a) / STEP)))]
    if face in ('+z', '-z'):
        z = z1 if face == '+z' else z0
        return [(name, (x, y, z)) for x in n(x0, x1) for y in n(y0, y1)]
    if face in ('+x', '-x'):
        x = x1 if face == '+x' else x0
        return [(name, (x, y, z)) for y in n(y0, y1) for z in n(z0, z1)]
    y = y1 if face == '+y' else y0
    return [(name, (x, y, z)) for x in n(x0, x1) for z in n(z0, z1)]


def _members(names, face):
    return [m for name in names for m in _grid(name, face)]


PODS = ['Pod port armour shell', 'Pod starboard armour shell']
DRIVES = ['Drive port pod housing', 'Drive starboard pod housing']
PARTS = {
    'spine-top': dict(members=_grid('Hull spine', '+z'), dir=UP_Z, up=PORT, cone=.85,
                      what='the spine from above: a long orange armour body, narrow neck to the right widening to '
                           'broad shoulders and a flared tail on the left, with engraved plate breaks and a hatch '
                           'outline near the tail'),
    'pod-top-port': dict(members=_grid('Pod port armour shell', '+z'), dir=UP_Z, up=PORT, cone=.85,
                         what='the port armour pod from above: a broad orange armour block with chamfered corners, '
                              'front edge to the right, with an engraved step line and plate break'),
    'pod-top-stbd': dict(members=_grid('Pod starboard armour shell', '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                         skin_from='pod-top-port', mirror_u=True, what='the starboard armour pod from above (mirror)'),
    'drive-top-port': dict(members=_grid('Drive port pod housing', '+z'), dir=UP_Z, up=PORT, cone=.85,
                           what='the port drive pod from above: a light grey machinery housing, front to the right, '
                                'with a collar groove and an access panel outline'),
    'drive-top-stbd': dict(members=_grid('Drive starboard pod housing', '+z'), dir=UP_Z, up=STARBOARD, cone=.85,
                           skin_from='drive-top-port', mirror_u=True, what='the starboard drive pod from above (mirror)'),
    'underside': dict(members=_members(['Hull spine'] + PODS + DRIVES, '-z'), dir=DOWN_Z, up=PORT, cone=.85,
                      what='the whole belly from below: the orange spine and armour pods, the grey drive pods'),
    'drive-side-port': dict(members=_grid('Drive port pod housing', '+y'), dir=PORT, up=UP_Z, cone=.85,
                            what="the port drive pod's outboard side: a light grey machinery housing wall"),
    'drive-side-stbd': dict(members=_grid('Drive starboard pod housing', '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                            skin_from='drive-side-port', mirror_u=True,
                            what="the starboard drive pod's outboard side (mirror)"),
    'cheek-port': dict(members=_grid('Pod port outer cheek', '+y'), dir=PORT, up=UP_Z, cone=.85,
                       what="the port armour pod's grey outer cheek from outboard, with a three-slot louvre"),
    'cheek-stbd': dict(members=_grid('Pod starboard outer cheek', '-y'), dir=STARBOARD, up=UP_Z, cone=.85,
                       skin_from='cheek-port', mirror_u=True, what='the starboard cheek (mirror)'),
    'bow': dict(members=_members(['Hull spine'] + PODS, '+x'), dir=BOW, up=UP_Z, cone=.6,
                what="the bow faces from dead ahead: the two orange armour pods' front faces either side of the spine"),
    'stern': dict(members=_members(['Hull spine'] + DRIVES, '-x'), dir=STERN, up=UP_Z, cone=.6,
                  what='the stern faces from dead astern: the orange spine tail between the two grey drive pod tails'),
}

SHEETS = {
    'hull-top': ['spine-top', 'pod-top-port', 'drive-top-port', 'underside'],
    'hull-low': ['drive-side-port', 'cheek-port', 'bow', 'stern'],
}
