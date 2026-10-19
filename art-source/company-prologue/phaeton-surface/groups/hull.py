"""Hull: the oxide-red tank hull, its belly plates, the port crew gallery and the aft frame.

`Hull main body` runs X -36..132 with the open cargo deck at Z 6 between the
tank skirts; its flanks show above the machinery strakes and its bow is raked
by two facets. Its plating is seamed, so the deck and flanks are taken by box
(every qualifying face) rather than grown from one seed. `Hull ventral plates`
is the flat belly, `Hull port gallery block` the raised crew gallery amidships,
`Hull aft transverse frame` the gunmetal frame across the beam aft of tank 1.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW, DOWN_X

BODY, BELLY = 'Hull main body', 'Hull ventral plates'
GALLERY, FRAME = 'Hull port gallery block', 'Hull aft transverse frame'

PARTS = {
    'deck-top': dict(members=[(BODY, {'box': ((-40.0, -40.0, 3.0), (136.0, 40.0, 9.0))})], dir=UP_Z, up=DOWN_X, cone=.6,
                     what='the long open cargo deck from above, bow at the top, with three round tank wells'),
    'side-stbd': dict(members=[(BODY, {'box': ((-40.0, -40.0, -25.0), (136.0, -20.0, 9.0))})], dir=STARBOARD, up=UP_Z,
                      cone=.6, what='tank hull starboard flank above the machinery strake'),
    'side-port': dict(members=[(BODY, {'box': ((-40.0, 20.0, -25.0), (136.0, 40.0, 9.0))})], dir=PORT, up=UP_Z,
                      cone=.6, what='tank hull port flank above the machinery strake'),
    'bow': dict(members=[(BODY, {'box': ((105.0, -40.0, -25.0), (136.0, 40.0, 9.0))})], dir=BOW, up=UP_Z, cone=.6,
                what='the raked bow from ahead'),
    'belly': dict(members=[(BELLY, None)], dir=DOWN_Z, up=DOWN_X, cone=.6,
                  what='the flat ventral plating from below, bow at the top'),
    'gallery-top': dict(members=[(GALLERY, {'box': ((50.0, 20.0, 8.0), (88.0, 40.0, 14.0))})], dir=UP_Z, up=DOWN_X,
                        cone=.6, what='crew gallery roof from above'),
    'gallery-side-port': dict(members=[(GALLERY, None)], dir=PORT, up=UP_Z, cone=.6,
                              what='crew gallery port wall with its window row'),
    'frame-top': dict(members=[(FRAME, None)], dir=UP_Z, up=DOWN_X, cone=.6,
                      what='aft transverse frame top across the beam'),
}

SHEETS = {
    'hull-deck': ['deck-top', 'side-stbd', 'side-port', 'bow'],
    'hull-under': ['belly', 'gallery-top', 'gallery-side-port', 'frame-top'],
}
