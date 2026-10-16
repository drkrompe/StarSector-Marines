"""Port grapple arm: boom, forearm, pivots, slew gear and winch (sw_port_module.py).

The arm is long and thin, so each tile carries several parallel faces from one
direction: every top-facing arm face in one tile, every port-facing one in
another. Most of it is 'Grapple arm pale grey' (palette class 'pale'); the
pivots, ram and grapple head are gunmetal, the jaw dark.
The boom's 45-degree chamfers, its inboard side and underside, the forearm's
inboard side, and the small saddles, seams and rods are left to the swatch.
"""
from math import sqrt

from parts_common import PORT, UP_Z

_n = lambda v: tuple(c / sqrt(sum(x * x for x in v)) for c in v)
BOOM_TOP = _n((0.09, -0.02, 1.0))     # 'Grapple arm boom' top face normal
BOOM_PORT = _n((0.25, 0.97, 0.0))     # its outboard side
FOREARM_OUT = _n((0.93, 0.38, 0.0))   # 'Grapple arm forearm' outboard/forward side

PARTS = {
    'arm-tops': dict(members=[('Grapple arm boom', (40.0, 18.5, 17.9)),
                              ('Grapple arm forearm', (49.0, 12.6, 15.5)),
                              ('Arm elbow knuckle', (47.4, 16.6, 17.6)),
                              ('Arm shoulder pedestal', (32.5, 20.4, 16.7)),
                              ('Boom luff ram', (37.1, 21.9, 17.0)),
                              ('Grapple wrist yoke', (50.4, 9.2, 14.5)),
                              ('Grapple head body', (52.4, 9.0, 14.4)),
                              ('Grapple jaw.001', (54.7, 9.0, 14.1))],
                     dir=BOOM_TOP, up=PORT, cone=.9,
                     what='grapple arm from above: boom and forearm tops (pale), pivots, ram, wrist and head (gunmetal)'),
    'arm-boom-port': dict(members=[('Grapple arm boom', (40.4, 20.2, 16.6)),
                                   ('Boom luff ram', (37.2, 22.2, 16.6)),
                                   ('Arm shoulder pedestal', (33.3, 22.4, 15.4)),
                                   ('Arm elbow knuckle', (48.1, 18.4, 16.1)),
                                   ('Arm slew gear casing', (31.6, 26.7, 13.4)),
                                   ('Grapple winch drum', (41.6, 29.3, 14.1))],
                          dir=BOOM_PORT, up=UP_Z, cone=.9,
                          what='grapple arm from port: boom outboard side, ram, pivots, slew gear casing and winch drum'),
    'arm-forearm-out': dict(members=[('Grapple arm forearm', (50.3, 13.5, 14.4)),
                                     ('Arm elbow knuckle', (49.2, 17.3, 16.1)),
                                     ('Grapple wrist yoke', (51.8, 9.8, 13.2))],
                            dir=FOREARM_OUT, up=UP_Z, cone=.9,
                            what='forearm outboard/forward side with the elbow and wrist facets'),
    'arm-machinery-tops': dict(members=[('Arm slew gear casing', (31.6, 24.4, 15.4)),
                                        ('Grapple winch drum', (41.6, 27.4, 16.0))],
                               dir=UP_Z, up=PORT, cone=.9,
                               what='slew gear casing top (gunmetal) and winch drum crown (pale), from above'),
}

SHEETS = {
    'arm-a': ['arm-tops', 'arm-boom-port', 'arm-forearm-out', 'arm-machinery-tops'],
}
