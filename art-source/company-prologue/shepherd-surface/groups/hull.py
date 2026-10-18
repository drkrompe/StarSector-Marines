"""Hull: the sand-tan central crew body, the port spine and the aft crossbeam.

`Hull central body` runs X -31..35.5 under the bridge hull; its open deck is
the aft half (the bridge covers the forward half) and its starboard flank shows
above the gantry and turret deck. `Hull port spine` is the raised strip between
the body and the drone bay. `Hull aft crossbeam` spans the full beam at X -29..-9.
Two sheets: body deck, starboard flank, bow and spine top; crossbeam top, its
forward faces beside the bay and turret deck, its outboard ends, the spine's
bay-facing flank. The starboard outboard end reuses the port tile mirrored.
"""
from parts_common import PORT, STARBOARD, UP_Z, BOW, DOWN_X

BODY, SPINE, BEAM = 'Hull central body', 'Hull port spine', 'Hull aft crossbeam'

PARTS = {
    'body-top': dict(members=[(BODY, (-2.0, -3.0, 11.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                     what='central body open dorsal deck aft of the bridge hull'),
    'body-side-stbd': dict(members=[(BODY, (10.0, -11.0, 6.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                           what='central body starboard flank above the gantry and turret deck'),
    'body-bow': dict(members=[(BODY, (35.0, -2.5, -4.0))], dir=BOW, up=UP_Z, cone=.6,
                     what='the rounded bow nose seen from ahead, under the bridge chin'),
    'spine-top': dict(members=[(SPINE, (10.0, 8.0, 13.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                      what='port spine top beside the walkway'),
    'beam-top': dict(members=[(BEAM, (-19.0, 20.0, 7.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                     what='aft crossbeam deck across the full beam'),
    'beam-front': dict(members=[(BEAM, (-9.0, 28.0, 0.0))], dir=BOW, up=UP_Z, cone=.6,
                       what='crossbeam forward face across the beam (one face; the body hides its middle)'),
    'beam-side-port': dict(members=[(BEAM, (-19.0, 31.5, 0.0))], dir=PORT, up=UP_Z, cone=.6,
                           what='crossbeam port outboard end face'),
    'beam-side-stbd': dict(members=[(BEAM, (-19.0, -31.5, 0.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                           skin_from='beam-side-port', mirror_u=True,
                           what='crossbeam starboard outboard end face (mirror of port)'),
    'spine-side-port': dict(members=[(SPINE, (10.0, 11.0, 10.5))], dir=PORT, up=UP_Z, cone=.6, ray=40,
                            what='port spine flank above the drone bay back wall'),
}

SHEETS = {
    'hull-body': ['body-top', 'body-side-stbd', 'body-bow', 'spine-top'],
    'hull-beam': ['beam-top', 'beam-front', 'beam-side-port', 'spine-side-port'],
}
