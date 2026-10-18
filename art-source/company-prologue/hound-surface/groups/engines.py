"""Engines: the two drive pods' casing armour (Hound (A) pass 6 rollout group).

Each pod is a run of octagonal sections along X (hd_engines.py): intake cap,
forward casing, orange armour collar (outboard skirt plates set into its
flank), aft casing with its reinforcing band, the orange thrust housing with
two shroud bands. Engine 2 starboard is built as the exact Y mirror of
Engine 1 port, so the port pod carries the four sources and the starboard pod
reuses every one of them through `mirror_u`:

  side     the outboard flats of every section, seen from outboard
  top      the dorsal flats, the collar's top plate and vent ring, the cowl plate
  chamfer  the 45 deg upper-outboard chamfers (the sections' corner bevel is a
           45 deg face, square to neither the side nor the top camera)
  front    the intake dome from ahead, with the clamp band, forward casing and
           collar end rings round it

The collar top plate and cowl top plate ('Engine worn top plate', class gunmetal
in surface_hull.py, though the finish reference paints them orange) are left out
of the top part and take the gunmetal swatch: Codex followed the reference and
the mixed colour polluted the tile's grading. The collar and casing faces under
them are still in the part (per-object visibility), so that art is hidden.

The drive bells, nozzle petals, exhaust/core and anchors stay swatch (or VFX);
the inboard flats, keel faces and lower chamfers are left to the class
swatches (mostly hidden by the hull, seen only from below).
"""
from math import sqrt
from parts_common import PORT, STARBOARD, UP_Z

P1, P2 = 'Engine 1 port', 'Engine 2 starboard'
_S = sqrt(.5)
CHAMFER = (0.0, _S, _S)
CHAMFER_STBD = (0.0, -_S, _S)
FRONT = (1.0, 0.0, 0.0)

# Port seeds: (object suffix, point on the face meant).
# The collar's flank is three frames round the skirt recess, split by the two
# armour seams: C-shaped end frames and the middle frame's top and bottom strips
# (seeds at the face centres; the seam grooves' tiny floor bits lie nearer a strip seed).
_SIDE = [('armour collar', (-6.11, 30.0, -0.07)), ('armour collar', (-0.4, 30.0, 3.7)), ('armour collar', (-0.4, 30.0, -3.85)),
         ('armour collar', (5.34, 30.0, -0.08)),
         ('skirt backing', (-0.4, 29.51, -0.1)),   # dark recess between the plates: the seams Codex paints dark
         ('side skirt plate 1', (-6.05, 29.97, 0)), ('side skirt plate 2', (-2.7, 29.97, 0)),
         ('side skirt plate 3', (1.8, 29.97, 0)), ('side skirt plate 4', (5.25, 29.97, 0)),
         ('forward casing', (9.5, 28.3, -3.6)), ('intake clamp band', (11.4, 27.58, -3.5)),
         ('intake cap', (13.0, 27.4, 0)),
         ('aft casing', (-9.9, 27.4, 0)), ('aft casing', (-14.3, 27.4, 0)),
         ('reinforcing band', (-12.5, 28.2, 0)), ('thrust housing', (-19.3, 29.6, 0))]
_TOP = [('armour collar', (-6.9, 22, 7.6)), ('armour collar', (0, 26.2, 7.6)), ('armour collar', (5.0, 26.2, 7.6)),
        ('dorsal vent housing', (-0.5, 25.6, 8.4)),
        ('forward casing', (9.5, 17.8, 6.9)), ('forward casing', (9.5, 25.2, 6.9)),
        ('intake clamp band', (11.4, 20.0, 6.48)), ('intake cap', (14.0, 19.3, 6.3)),
        ('aft casing', (-9.8, 23.0, 6.0)), ('aft casing', (-14.3, 22.0, 6.0)),
        ('reinforcing band', (-12.5, 20.0, 6.6)),
        ('shroud band 1', (-21.4, 21.0, 7.4)), ('shroud band 2', (-20.0, 21.0, 7.4)),
        ('thrust housing', (-22.7, 21.0, 7.1)), ('thrust housing', (-20.7, 21.0, 7.1)), ('thrust housing', (-19.3, 21.0, 7.1))]
_CHAMFER = [('armour collar', (-6.2, 28.2, 5.8)), ('armour collar', (0.0, 28.2, 5.8)), ('armour collar', (5.3, 28.2, 5.8)),
            ('forward casing', (9.5, 26.8, 5.4)), ('intake clamp band', (11.4, 26.0, 4.9)), ('intake cap', (13.0, 25.9, 4.8)),
            ('aft casing', (-9.9, 26.1, 4.7)), ('aft casing', (-14.3, 26.1, 4.7)), ('reinforcing band', (-12.5, 26.75, 5.15)),
            ('shroud band 1', (-21.4, 27.9, 5.7)), ('shroud band 2', (-20.0, 27.9, 5.7)),
            ('thrust housing', (-22.7, 27.95, 5.45)), ('thrust housing', (-20.7, 27.95, 5.45)), ('thrust housing', (-19.3, 27.95, 5.45))]
_FRONT = [('intake cap', (17.0, 23.9, -1.5)), ('intake clamp band', (11.75, 27.0, 0)),
          ('forward casing', (10.8, 27.9, 0)), ('armour collar', (7.8, 28.9, 0))]


def _port(rows):
    return [(f'{P1} {o}', p) for o, p in rows]


def _stbd(rows):
    return [(f'{P2} {o}', (p[0], -p[1], p[2])) for o, p in rows]


PARTS = {
    'engine-side-port': dict(members=_port(_SIDE), dir=PORT, up=UP_Z, cone=.85,
                             what='port engine outboard flank: orange collar frame with taupe skirt plates, taupe casings, '
                                  'gunmetal band and intake cap side, orange thrust housing'),
    'engine-top-port': dict(members=_port(_TOP), dir=UP_Z, up=PORT, cone=.85,
                            what='port engine from above: collar top plate and vent ring, cowl plate, casings, bands, housing tops'),
    'engine-chamfer-port': dict(members=_port(_CHAMFER), dir=CHAMFER, up=UP_Z, cone=.85,
                                what='port engine upper outboard 45 deg chamfers along the whole pod'),
    'engine-front-port': dict(members=_port(_FRONT), dir=FRONT, up=UP_Z, cone=.6, square=.62,
                              what='port engine from ahead: gunmetal intake dome with its clamp band, forward casing and collar end rings'),
    'engine-side-stbd': dict(members=_stbd(_SIDE), dir=STARBOARD, up=UP_Z, cone=.85, skin_from='engine-side-port', mirror_u=True,
                             what='starboard engine outboard flank (mirror of the port flank)'),
    'engine-top-stbd': dict(members=_stbd(_TOP), dir=UP_Z, up=STARBOARD, cone=.85, skin_from='engine-top-port', mirror_u=True,
                            what='starboard engine from above (mirror; screen-up outboard as on port)'),
    'engine-chamfer-stbd': dict(members=_stbd(_CHAMFER), dir=CHAMFER_STBD, up=UP_Z, cone=.85, skin_from='engine-chamfer-port',
                                mirror_u=True, what='starboard engine upper outboard chamfers (mirror)'),
    'engine-front-stbd': dict(members=_stbd(_FRONT), dir=FRONT, up=UP_Z, cone=.6, square=.62, skin_from='engine-front-port',
                              mirror_u=True, what='starboard engine from ahead (mirror)'),
}

SHEETS = {
    'engines': ['engine-side-port', 'engine-top-port', 'engine-chamfer-port', 'engine-front-port'],
}
