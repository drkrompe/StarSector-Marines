"""Spine: the pale longerons, the oxide-red aft machinery body and the keel spine.

The longerons are pale box girders between the hold and the side cargo
modules, X -20..91. Each is a handful of single polygons the whole girder long
(the outboard and inboard webs are one n-gon each, 111 x 64 units, the top
flange one 108 x 11.4 polygon), so no box can cut them: their tiles are capped
at about 7 texels per unit by that length. Only the top of each web shows (the
modules cover the outboard web below Z 14, the hold the inboard web below
Z 18). The port girder is the source; the starboard girder reuses it mirrored.

The aft machinery body (X -92..-30) carries the bridge: its deck is cut at the
plate seam X -58 into an aft and a forward tile, each one single deck plate
wide (62 units, about 12.6 texels per unit). The stern is the faceted aft
face between the drive pods; the underside the plated bottom between the
pods. The keel spine's top shows in the waist between the aft body and the
hold (X -30..-19); its tile is the whole top plate with its ladder well.

Left to the class swatches: the aft body's flanks (each sits 5 units inboard
of a drive pod casing that covers its full height), its forward face (behind
the hold), every 45-degree chamfer, the longeron end faces, the aft knees and
their flanges, web stiffeners, splice plates and bolts, deck fittings, the
keel spine's sides and underside (inside the aft body and hold but for an
11-unit slot), its ladder rails, well floor and cross struts.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW, STERN, DOWN_X

LON, FLG = 'Hull port longeron', 'Hull port longeron top flange'
SLON, SFLG = 'Hull starboard longeron', 'Hull starboard longeron top flange'
AFT = 'Hull aft machinery body'
KEEL = 'Hull keel spine'


def _box(x, y=(-60.0, 60.0), z=(-60.0, 60.0), **kw):
    return dict(box=((x[0], y[0], z[0]), (x[1], y[1], z[1])), **kw)


PARTS = {
    'longeron-top-port': dict(members=[(FLG, None)], dir=UP_Z, up=PORT, cone=.8,
                              what='port longeron top flange from above, bow to the right: one long pale strip'),
    'longeron-out-port': dict(members=[(LON, None)], dir=PORT, up=UP_Z, cone=.8,
                              what='port longeron outboard web, row of lightening holes along its top'),
    'longeron-in-port': dict(members=[(LON, None)], dir=STARBOARD, up=UP_Z, cone=.8,
                             what='port longeron inboard web (faces the hold)'),
    'keel-top': dict(members=[(KEEL, _box((-50.0, -8.0), z=(9.0, 13.0)))], dir=UP_Z, up=DOWN_X, cone=.8,
                     what='keel spine top plate with its ladder well, from above, bow at the top'),
    'aft-deck-aft': dict(members=[(AFT, _box((-93.0, -58.05), z=(10.0, 14.0), pad=1.0))], dir=UP_Z, up=DOWN_X, cone=.8,
                         what='aft body deck aft of the X -58 seam: stern deck and the plate under the bridge'),
    'aft-deck-fwd': dict(members=[(AFT, _box((-58.05, -33.0), z=(10.0, 14.0), pad=1.0))], dir=UP_Z, up=DOWN_X, cone=.8,
                         what='aft body deck forward of the X -58 seam: plates beside and ahead of the bridge'),
    'aft-stern': dict(members=[(AFT, _box((-93.0, -75.0), z=(-17.0, 9.5)))], dir=STERN, up=UP_Z, cone=.8,
                      what='aft body faceted stern face between the drive pods, from astern'),
    'aft-under': dict(members=[(AFT, _box((-93.0, -33.0), z=(-17.0, -14.0)))], dir=DOWN_Z, up=BOW, cone=.8,
                      what='aft body plated underside between the drive pods, from below, bow up the tile'),
}

for src, port_objs, stbd_objs, d in [('longeron-top-port', [FLG], [SFLG], UP_Z),
                                     ('longeron-out-port', [LON], [SLON], STARBOARD),
                                     ('longeron-in-port', [LON], [SLON], PORT)]:
    p = PARTS[src]
    PARTS[src.replace('-port', '-stbd')] = dict(
        members=[(o, None) for o in stbd_objs], dir=d, up=(0.0, -1.0, 0.0) if d == UP_Z else p['up'], cone=p['cone'],
        skin_from=src, mirror_u=True, what=p['what'].replace('port', 'starboard') + ' (mirror of port)')

SHEETS = {
    'spine-longeron': ['longeron-top-port', 'longeron-out-port', 'longeron-in-port', 'keel-top'],
    'spine-aft': ['aft-deck-aft', 'aft-deck-fwd', 'aft-stern', 'aft-under'],
}
