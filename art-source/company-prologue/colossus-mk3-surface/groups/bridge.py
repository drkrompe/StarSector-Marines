"""Bridge: the pale deckhouse on the aft machinery body (X -42..-71, Y 1.5 +- 24).

`Bridge deckhouse` is one shell: a low forward annex (roof under the red
`Bridge annex roof plating`), the raked windscreen wall above it, and the cab
behind with 45-degree roof chamfers. The red coaming frame runs round its base.
One sheet:

- roof from above: the flat cab roof (cone .8: its 45-degree chamfers tie
  with the walls' view and go to the wall and aft tiles), the red annex roof deck and the
  pale windscreen visor's top, bow at the top;
- port wall (deckhouse side, its roof chamfer and the red frame's outer face);
  the starboard wall is its mirror (the deckhouse is built Y-symmetric);
- forward face: annex front and corners, the raked windscreen wall round its
  recess, the pale windscreen surround and visor face, the frame's front;
- aft face: aft wall, its roof chamfer and corners, the frame's aft run.

Left to the class swatches: the windscreen frame, sill, shutter box and rails,
wing-port surrounds and eyebrows, vents, service covers, conduits, ladders,
handrails, the roof hatch, skylights, mushroom vents, sensor ring, dish, mast,
scanner and dome, life-raft cradles, wing platforms, the door, landing and
stern posts. Glazing, port glass and lamps keep their own materials.
"""
from parts_common import PORT, STARBOARD, UP_Z, BOW, STERN, DOWN_X

HOUSE, ANNEX, FRAME = 'Bridge deckhouse', 'Bridge annex roof plating', 'Bridge red frame'
VISOR, SURROUND = 'Bridge windscreen visor', 'Bridge windscreen surround'


def _box(x0, x1, y0, y1, z0=11.0, z1=26.0):
    # The shell is cut by skin-deep seam and strata grooves, so one seed would
    # claim a single plate between grooves: take every qualifying face in a box.
    return {'box': ((x0, y0, z0), (x1, y1, z1))}


PARTS = {
    'bridge-roof': dict(members=[(HOUSE, _box(-75.0, -50.0, -30.0, 33.0, 20.0, 26.0)), (ANNEX, (-47.0, 18.0, 18.8)),
                                 (VISOR, (-53.0, 1.5, 24.4))],
                        dir=UP_Z, up=DOWN_X, cone=.8,
                        what='bridge roof from above: pale cab roof, red annex roof deck forward, pale visor top'),
    'bridge-wall-port': dict(members=[(HOUSE, _box(-75.0, -38.0, 1.5, 33.0)), (FRAME, (-60.0, 27.4, 13.6))],
                             dir=PORT, up=UP_Z, cone=.6,
                             what='bridge deckhouse port wall with its roof chamfer, red coaming frame along its foot'),
    'bridge-wall-stbd': dict(members=[(HOUSE, _box(-75.0, -38.0, -30.0, 1.5)), (FRAME, (-60.0, -24.4, 13.6))],
                             dir=STARBOARD, up=UP_Z, cone=.6, skin_from='bridge-wall-port', mirror_u=True,
                             what='bridge deckhouse starboard wall (mirror of port)'),
    'bridge-front': dict(members=[(HOUSE, _box(-57.0, -38.0, -30.0, 33.0)), (SURROUND, (-51.5, 12.5, 21.0)), (VISOR, (-51.3, 1.5, 23.7)),
                                  (FRAME, (-40.1, 1.5, 13.6))],
                         dir=BOW, up=UP_Z, cone=.6,
                         what='bridge forward face: annex front, raked windscreen wall round the recess, surround and visor'),
    'bridge-aft': dict(members=[(HOUSE, _box(-75.0, -62.0, -30.0, 33.0)), (FRAME, _box(-75.0, -71.5, -30.0, 33.0, 11.0, 16.0))],
                       dir=STERN, up=UP_Z, cone=.6,
                       what='bridge aft face with its roof chamfer, red frame foot'),
}

SHEETS = {
    'bridge': ['bridge-roof', 'bridge-wall-port', 'bridge-front', 'bridge-aft'],
}
