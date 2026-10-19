"""Body: the fuselage's top, port neck flank and nose, and the cabin spine's port wall.

`Hull fuselage` is one object from the nose to the tail: a flat deck rolling
off at the shoulders into the wing roots, raked glacis and cheek facets at
the nose, the neck's vertical walls forward of the wing. `Hull dorsal spine`
and `Hull aft hump` are the bone cabin roof and the turret hump; their port
walls carry the cabin windows. Starboard walls take the port tiles mirrored.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X, BOW

FUSE = 'Hull fuselage'
SPINE = 'Hull dorsal spine'
HUMP = 'Hull aft hump'

PARTS = {
    'fuse-top': dict(members=[(FUSE, (5.0, 15.0, 5.0)), (FUSE, (20.0, 0.0, 6.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                     what='fuselage deck, shoulder rolls and glacis from above'),
    'fuse-side-port': dict(members=[(FUSE, (22.0, 13.0, -1.0))], dir=PORT, up=UP_Z, cone=.6,
                           what='fuselage port neck flank with the crew door'),
    'fuse-side-stbd': dict(members=[(FUSE, (22.0, -13.0, -1.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                           skin_from='fuse-side-port', mirror_u=True, what='fuselage starboard neck flank (mirror)'),
    'fuse-nose': dict(members=[(FUSE, (37.5, 0.0, 0.5))], dir=BOW, up=UP_Z, cone=.5,
                      what='the nose seen from ahead, cheeks and chin'),
    'spine-side-port': dict(members=[(SPINE, (15.0, 5.6, 7.0)), (HUMP, (-10.0, 10.5, 6.2))], dir=PORT, up=UP_Z,
                            cone=.6, what='cabin spine and hump port walls with the cabin windows'),
    'spine-side-stbd': dict(members=[(SPINE, (15.0, -5.6, 7.0)), (HUMP, (-10.0, -10.5, 6.2))], dir=STARBOARD,
                            up=UP_Z, cone=.6, skin_from='spine-side-port', mirror_u=True,
                            what='cabin spine and hump starboard walls (mirror)'),
}

SHEETS = {
    'body': ['fuse-top', 'fuse-side-port', 'fuse-nose', 'spine-side-port'],
}
